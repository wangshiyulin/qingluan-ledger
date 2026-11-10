package com.qingluan.ledger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Fixed file beneath a user-selected existing WebDAV collection. No redirects or cleartext. */
data class WebDavConfig(val address: String, val username: String, val password: String) {
    fun collection(): URL {
        val uri =
            try {
                URI(address.trim())
            } catch (_: Exception) {
                error("服务器地址无效")
            }
        require(
            uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.port in -1..65535 &&
                uri.port != 0 &&
                address.length <= 2048
        ) {
            "请填写不含账号、查询参数的 HTTPS 文件夹地址"
        }
        require(
            username.isNotBlank() &&
                ':' !in username &&
                username.length <= 512 &&
                password.length in 1..2048 &&
                (username + password).none { it == '\r' || it == '\n' }
        ) {
            "用户名或密码无效"
        }
        return URL(uri.toASCIIString().trimEnd('/') + "/")
    }

    fun file(): URL = URL(collection(), "ledger-backup.qlb")
}

data class RemoteBackup(
    val exists: Boolean,
    val etag: String? = null,
    val modified: String? = null,
)

data class RemotePayload(val encrypted: String, val metadata: RemoteBackup)

class WebDavClient(
    private val config: WebDavConfig,
    private val connection: (URL) -> HttpURLConnection = {
        it.openConnection() as HttpURLConnection
    },
) {
    private suspend fun <T> request(url: URL, method: String, block: (HttpURLConnection) -> T): T =
        withContext(Dispatchers.IO) {
            config.collection()
            val c = connection(url)
            try {
                c.instanceFollowRedirects = false
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.requestMethod = method
                c.setRequestProperty(
                    "Authorization",
                    "Basic " +
                        Base64.encodeToString(
                            "${config.username}:${config.password}".toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        ),
                )
                c.setRequestProperty("Accept-Encoding", "identity")
                c.setRequestProperty("Cache-Control", "no-cache")
                c.useCaches = false
                block(c)
            } catch (e: java.net.SocketTimeoutException) {
                error("连接超时，请检查服务器后重试")
            } catch (e: javax.net.ssl.SSLException) {
                error("HTTPS 证书验证失败")
            } catch (e: java.io.IOException) {
                error("网络连接失败，请检查地址和网络")
            } finally {
                c.disconnect()
            }
        }

    private fun requireStatus(code: Int, accepted: Set<Int>) {
        if (code in accepted) return
        error(
            when (code) {
                401 -> "认证失败，请检查用户名和密码"
                403 -> "服务器拒绝访问，请检查读写权限"
                404 -> "服务器路径或备份不存在"
                409 -> "服务器文件夹不存在，请先创建文件夹"
                412 -> "远端备份已变化，请重新操作"
                413,
                507 -> "服务器空间不足或文件过大"
                in 300..399 -> "服务器发生重定向，请填写最终 HTTPS 地址"
                else -> "WebDAV 请求失败（HTTP $code）"
            }
        )
    }

    private fun metadata(c: HttpURLConnection) =
        RemoteBackup(
            true,
            c.getHeaderField("ETag")?.takeIf {
                it.length <= 1024 && it.none { ch -> ch == '\r' || ch == '\n' }
            },
            c.getHeaderField("Last-Modified")?.take(120),
        )

    suspend fun test(): RemoteBackup {
        request(config.collection(), "HEAD") { c ->
            requireStatus(c.responseCode, setOf(200, 204, 207))
        }
        return stat()
    }

    suspend fun stat(): RemoteBackup =
        request(config.file(), "HEAD") { c ->
            if (c.responseCode == 404) RemoteBackup(false)
            else {
                requireStatus(c.responseCode, setOf(200, 204))
                metadata(c)
            }
        }

    suspend fun upload(encrypted: String, expected: RemoteBackup) {
        val bytes = encrypted.toByteArray(Charsets.UTF_8)
        require(bytes.size <= CompleteBackup.MAX_BYTES) { "备份超过16MB" }
        require(
            !expected.exists || (!expected.etag.isNullOrBlank() && !expected.etag.startsWith("W/"))
        ) {
            "服务器未提供有效 ETag，无法安全覆盖；请更换空文件夹"
        }
        request(config.file(), "PUT") { c ->
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            if (expected.exists) c.setRequestProperty("If-Match", expected.etag)
            else c.setRequestProperty("If-None-Match", "*")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            requireStatus(c.responseCode, setOf(200, 201, 204))
        }
    }

    suspend fun download(): RemotePayload =
        request(config.file(), "GET") { c ->
            requireStatus(c.responseCode, setOf(200))
            require(c.contentLengthLong <= CompleteBackup.MAX_BYTES) { "远端文件超过16MB" }
            val bytes =
                c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(out.size() + count <= CompleteBackup.MAX_BYTES) { "远端文件超过16MB" }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
            val raw = bytes.toString(Charsets.UTF_8)
            require(
                runCatching { JSONObject(raw).optString("encrypted") == "qingluan-v1" }
                    .getOrDefault(false)
            ) {
                "远端文件不是青鸾加密备份"
            }
            RemotePayload(raw, metadata(c))
        }
}

/** Credentials are device-bound, outside Android backups and complete ledger exports. */
class WebDavStore(private val context: Context) {
    private val file
        get() = AtomicFile(File(context.noBackupFilesDir, "webdav-account.bin"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("qingluan-webdav-account", null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                "qingluan-webdav-account",
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    suspend fun load(): WebDavConfig? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
                return@withContext null
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size in 29..32768) { "连接配置损坏，请清除后重新填写" }
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val j =
                JSONObject(c.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
            WebDavConfig(j.getString("address"), j.getString("username"), j.getString("password"))
                .also { it.collection() }
        }

    suspend fun save(config: WebDavConfig) =
        withContext(Dispatchers.IO) {
            config.collection()
            val raw =
                JSONObject()
                    .put("address", config.address.trim())
                    .put("username", config.username)
                    .put("password", config.password)
                    .toString()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val stream = file.startWrite()
            try {
                stream.write(c.iv + c.doFinal(raw.toByteArray(Charsets.UTF_8)))
                file.finishWrite(stream)
            } catch (e: Throwable) {
                file.failWrite(stream)
                throw e
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    private val recovery
        get() = AtomicFile(File(context.noBackupFilesDir, "webdav-before-restore.qlb"))

    suspend fun preserve(encrypted: String) =
        withContext(Dispatchers.IO) {
            val stream = recovery.startWrite()
            try {
                stream.write(encrypted.toByteArray(Charsets.UTF_8))
                recovery.finishWrite(stream)
            } catch (e: Throwable) {
                recovery.failWrite(stream)
                throw e
            }
        }

    suspend fun recoveryBytes(): ByteArray =
        withContext(Dispatchers.IO) { recovery.openRead().use { it.readBytes() } }

    suspend fun hasRecovery(): Boolean =
        withContext(Dispatchers.IO) {
            recovery.baseFile.exists() || File(recovery.baseFile.path + ".bak").exists()
        }
}
