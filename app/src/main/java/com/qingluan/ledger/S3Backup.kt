package com.qingluan.ledger

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Path-style HTTPS endpoints; no redirects, provider SDK or credentials in exports. */
data class S3Config(
    val endpoint: String,
    val bucket: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val prefix: String = "qingluan",
    val sessionToken: String = "",
) {
    fun validate(): URI {
        val uri = runCatching { URI(endpoint.trim()) }.getOrNull()
        require(
            uri != null &&
                uri.scheme == "https" &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                uri.rawPath in listOf("", "/") &&
                uri.port in -1..65535 &&
                uri.port != 0 &&
                endpoint.length <= 2048
        ) {
            "请填写不含路径、账号或参数的 HTTPS Endpoint"
        }
        require(
            bucket.matches(Regex("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")) &&
                ".." !in bucket &&
                ".-" !in bucket &&
                "-." !in bucket
        ) {
            "存储桶名称无效"
        }
        require(region.matches(Regex("[a-z0-9-]{1,64}"))) { "区域无效，请使用服务商提供的 Region" }
        require(
            accessKey.length in 1..256 &&
                secretKey.length in 1..2048 &&
                (accessKey + secretKey + sessionToken).all { it.code in 0x21..0x7e } &&
                sessionToken.length <= 8192
        ) {
            "访问密钥或会话令牌无效"
        }
        require(
            prefix.length <= 128 &&
                (prefix.isEmpty() ||
                    prefix.split('/').all {
                        it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9._-]*")) && it !in setOf(".", "..")
                    })
        ) {
            "目录前缀仅支持英文字母、数字、横线、下划线和分隔斜线"
        }
        return uri
    }

    val objectPrefix
        get() = if (prefix.isEmpty()) "" else "$prefix/"

    override fun toString() = "S3Config(credentials=redacted)"
}

/** AWS Signature V4. Query encoding must be RFC3986, not form encoding. */
internal object S3Signing {
    fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hmac(key: ByteArray, text: String) =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(text.toByteArray(Charsets.UTF_8))
        }

    fun encode(text: String): String = buildString {
        text.toByteArray(Charsets.UTF_8).forEach {
            val b = it.toInt() and 255
            if (b in 65..90 || b in 97..122 || b in 48..57 || b in listOf(45, 46, 95, 126))
                append(b.toChar())
            else
                append('%').append("0123456789ABCDEF"[b shr 4]).append("0123456789ABCDEF"[b and 15])
        }
    }

    fun request(
        config: S3Config,
        method: String,
        key: String? = null,
        query: Map<String, String> = emptyMap(),
        body: ByteArray = byteArrayOf(),
        extra: Map<String, String> = emptyMap(),
        now: Instant = Instant.now(),
    ): Request {
        val endpoint = config.validate()
        val stamp =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now)
        val day = stamp.take(8)
        val path =
            "/" +
                encode(config.bucket) +
                if (key == null) "" else "/" + key.split('/').joinToString("/") { encode(it) }
        val canonicalQuery =
            query.entries
                .map { encode(it.key) to encode(it.value) }
                .sortedWith(compareBy({ it.first }, { it.second }))
                .joinToString("&") { "${it.first}=${it.second}" }
        val url =
            "${endpoint.scheme}://${endpoint.rawAuthority}$path" +
                if (canonicalQuery.isEmpty()) "" else "?$canonicalQuery"
        val builder = Request.Builder().url(url)
        val parsed = builder.build().url
        val host =
            parsed.host.let { if (':' in it) "[$it]" else it } +
                if (parsed.port == 443) "" else ":${parsed.port}"
        val headers =
            sortedMapOf("host" to host, "x-amz-content-sha256" to hash(body), "x-amz-date" to stamp)
        if (config.sessionToken.isNotEmpty()) headers["x-amz-security-token"] = config.sessionToken
        headers.putAll(extra)
        val signed = headers.keys.joinToString(";")
        val canonicalHeaders =
            headers.entries.joinToString("") {
                "${it.key}:${it.value.trim().replace(Regex("\\s+"), " ")}\n"
            }
        val canonical = "$method\n$path\n$canonicalQuery\n$canonicalHeaders\n$signed\n${hash(body)}"
        val scope = "$day/${config.region}/s3/aws4_request"
        val stringToSign =
            "AWS4-HMAC-SHA256\n$stamp\n$scope\n${hash(canonical.toByteArray(Charsets.UTF_8))}"
        val dateKey = hmac(("AWS4" + config.secretKey).toByteArray(Charsets.UTF_8), day)
        val signingKey = hmac(hmac(hmac(dateKey, config.region), "s3"), "aws4_request")
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.header(
            "Authorization",
            "AWS4-HMAC-SHA256 Credential=${config.accessKey}/$scope, SignedHeaders=$signed, Signature=${hex(hmac(signingKey,stringToSign))}",
        )
        builder.header("Accept-Encoding", "identity").header("Cache-Control", "no-cache")
        return builder
            .method(
                method,
                if (method == "PUT") body.toRequestBody("application/octet-stream".toMediaType())
                else null,
            )
            .build()
    }
}

class S3Client(private val config: S3Config) {
    companion object {
        private val http =
            OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
    }

    private suspend fun execute(request: Request, maxBytes: Int): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                http.newCall(request).execute().use { response ->
                    require(response.isSuccessful) {
                        when (response.code) {
                            301,
                            302,
                            307,
                            308 -> "Endpoint 或 Region 不匹配，请填写最终 HTTPS 地址"
                            400 -> "S3 请求无效，请核对 Endpoint、Region 和系统时间"
                            401,
                            403 -> "S3 认证或权限不足，请核对密钥、Region、系统时间和存储桶权限"
                            404 -> "存储桶或备份不存在"
                            412 -> "远端备份已变化，请刷新列表后重新操作"
                            else -> "S3 请求失败（HTTP ${response.code}）"
                        }
                    }
                    val body = response.body ?: error("服务器返回空响应")
                    require(body.contentLength() <= maxBytes) { "远端内容超过允许大小" }
                    body.byteStream().use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= maxBytes) { "远端内容超过允许大小" }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                error("S3 连接超时，请稍后重试")
            } catch (_: javax.net.ssl.SSLException) {
                error("HTTPS 证书验证失败")
            } catch (_: java.io.IOException) {
                error("S3 网络连接失败，请检查服务器地址和网络")
            }
        }

    suspend fun history(): List<WebDavHistoryEntry> {
        val raw =
            execute(
                S3Signing.request(
                    config,
                    "GET",
                    query =
                        mapOf(
                            "list-type" to "2",
                            "max-keys" to "1000",
                            "prefix" to config.objectPrefix,
                        ),
                ),
                1024 * 1024,
            )
        return parseHistory(raw, config.objectPrefix)
    }

    suspend fun upload(encrypted: String): String {
        val body = encrypted.toByteArray(Charsets.UTF_8)
        require(body.size <= CompleteBackup.MAX_BYTES) { "备份超过16MB" }
        val name = WebDavHistory.filename()
        execute(
            S3Signing.request(
                config,
                "PUT",
                config.objectPrefix + name,
                body = body,
                extra = mapOf("if-none-match" to "*"),
            ),
            64 * 1024,
        )
        return name
    }

    suspend fun download(entry: WebDavHistoryEntry): String {
        require(WebDavHistory.owned(entry.filename) && WebDavHistory.strongEtag(entry.etag)) {
            "备份文件或 ETag 无效，请刷新列表"
        }
        return execute(
                S3Signing.request(
                    config,
                    "GET",
                    config.objectPrefix + entry.filename,
                    extra = mapOf("if-match" to entry.etag!!),
                ),
                CompleteBackup.MAX_BYTES,
            )
            .toString(Charsets.UTF_8)
    }
}

internal fun parseHistory(raw: ByteArray, prefix: String): List<WebDavHistoryEntry> {
    val parser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
    parser.setInput(raw.inputStream(), "UTF-8")
    val entries = mutableListOf<WebDavHistoryEntry>()
    var root = false
    var truncated = false
    var count = 0
    var fields: MutableMap<String, String>? = null
    var field: String? = null
    var contentDepth = -1
    var fieldDepth = -1
    val value = StringBuilder()
    while (true) {
        val event = parser.nextToken()
        require(
            parser.depth <= 16 &&
                event != XmlPullParser.DOCDECL &&
                (event != XmlPullParser.ENTITY_REF ||
                    parser.name in setOf("amp", "lt", "gt", "apos", "quot"))
        ) {
            "S3 列表格式不安全"
        }
        if (event == XmlPullParser.END_DOCUMENT) break
        if (event == XmlPullParser.START_TAG) {
            if (parser.depth == 1) {
                require(parser.name == "ListBucketResult") { "服务器未返回有效 S3 列表" }
                root = true
            }
            if (parser.name == "Contents" && parser.depth == 2) {
                require(++count <= 1000) { "备份列表过大" }
                fields = mutableMapOf()
                contentDepth = parser.depth
            }
            if (
                (fields != null &&
                    parser.depth == contentDepth + 1 &&
                    parser.name in setOf("Key", "Size", "ETag", "LastModified")) ||
                    (parser.name == "IsTruncated" && parser.depth == 2)
            ) {
                field = parser.name
                fieldDepth = parser.depth
                value.clear()
            }
        } else if (
            event in setOf(XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF) &&
                field != null
        ) {
            value.append(parser.text.orEmpty())
            require(value.length <= 4096) { "S3 列表字段过长" }
        } else if (event == XmlPullParser.END_TAG) {
            if (field != null && parser.depth == fieldDepth) {
                if (field == "IsTruncated") truncated = value.toString().trim() == "true"
                else fields?.put(field!!, value.toString())
                field = null
            }
            if (parser.name == "Contents" && parser.depth == contentDepth) {
                val f = fields.orEmpty()
                val key = f["Key"].orEmpty()
                val name = key.removePrefix(prefix)
                if (key.startsWith(prefix) && WebDavHistory.owned(name)) {
                    entries +=
                        WebDavHistoryEntry(
                            name,
                            f["LastModified"],
                            f["Size"]?.toLongOrNull(),
                            f["ETag"],
                        )
                }
                fields = null
                contentDepth = -1
            }
        }
    }
    require(root && !truncated) { "S3 对象超过1000个，请使用独立目录或整理存储桶后重试" }
    return entries.distinctBy { it.filename }.sortedByDescending { it.timestamp }
}

/** Device-bound AES-GCM credentials, kept outside exports and Android backup. */
class S3Store(private val context: Context) {
    private val file
        get() = AtomicFile(File(context.noBackupFilesDir, "s3-account.bin"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("qingluan-s3-account", null) as? SecretKey)
            ?: KeyGenerator.getInstance("AES", "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                "qingluan-s3-account",
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    suspend fun load(): S3Config? =
        withContext(Dispatchers.IO) {
            if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
                return@withContext null
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size in 29..32768) { "S3 配置损坏，请清除后重新填写" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val j =
                JSONObject(
                    cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
                )
            S3Config(
                    j.getString("endpoint"),
                    j.getString("bucket"),
                    j.getString("region"),
                    j.getString("accessKey"),
                    j.getString("secretKey"),
                    j.optString("prefix", "qingluan"),
                    j.optString("sessionToken"),
                )
                .also { it.validate() }
        }

    suspend fun save(config: S3Config) =
        withContext(Dispatchers.IO) {
            config.validate()
            val raw =
                JSONObject()
                    .put("endpoint", config.endpoint.trim())
                    .put("bucket", config.bucket)
                    .put("region", config.region)
                    .put("accessKey", config.accessKey)
                    .put("secretKey", config.secretKey)
                    .put("prefix", config.prefix)
                    .put("sessionToken", config.sessionToken)
                    .toString()
                    .toByteArray(Charsets.UTF_8)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val stream = file.startWrite()
            try {
                stream.write(cipher.iv + cipher.doFinal(raw))
                file.finishWrite(stream)
            } catch (e: Throwable) {
                file.failWrite(stream)
                throw e
            }
        }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
