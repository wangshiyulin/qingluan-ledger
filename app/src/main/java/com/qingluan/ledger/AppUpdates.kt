package com.qingluan.ledger

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

internal class UpdateFailure(message: String) : IllegalStateException(message)

private fun updateRequire(condition: Boolean, message: String) {
    if (!condition) throw UpdateFailure(message)
}

internal data class AppRelease(
    val tag: String,
    val version: List<Int>,
    val note: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

internal object ReleaseSource {
    const val API = "https://api.github.com/repos/wangshiyulin/qingluan-ledger/releases/latest"
    const val DOWNLOAD_PREFIX = "https://github.com/wangshiyulin/qingluan-ledger/releases/download/"
    const val MAX_APK = 100L * 1024 * 1024

    fun version(value: String): List<Int>? {
        val match =
            Regex("^v?([0-9]{1,4})\\.([0-9]{1,4})\\.([0-9]{1,4})$").matchEntire(value)
                ?: return null
        return match.groupValues.drop(1).map { it.toInt() }
    }

    fun newer(remote: List<Int>, local: List<Int>): Boolean {
        for (i in 0..2) if (remote[i] != local[i]) return remote[i] > local[i]
        return false
    }

    fun parse(raw: String): AppRelease {
        val json = JSONObject(raw)
        updateRequire(!json.optBoolean("draft") && !json.optBoolean("prerelease"), "暂不支持预发布更新")
        val tag = json.getString("tag_name")
        val version = version(tag) ?: throw UpdateFailure("发布版本格式无效")
        val filename = "ledger-v${version.joinToString(".")}.apk"
        val assets = json.getJSONArray("assets")
        updateRequire(assets.length() <= 100, "发布附件数量异常")
        val asset =
            (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == filename }
                ?: throw UpdateFailure("最新版本未提供 $filename")
        val url = asset.getString("browser_download_url")
        updateRequire(url == "$DOWNLOAD_PREFIX$tag/$filename", "安装包不是指定仓库的发布附件")
        val size = asset.optLong("size", -1)
        updateRequire(size in 1..MAX_APK, "安装包大小无效或超过100MiB")
        val digest = asset.optString("digest").takeIf { it.isNotBlank() && it != "null" }
        updateRequire(
            digest == null || digest.matches(Regex("sha256:[0-9a-fA-F]{64}")),
            "安装包摘要格式无效",
        )
        return AppRelease(
            tag,
            version,
            json.optString("body").take(6000),
            url,
            size,
            digest?.substringAfter(':')?.lowercase(java.util.Locale.ROOT),
        )
    }
}

/** Public GitHub requests only. Every redirect hop must remain HTTPS on GitHub's asset hosts. */
internal class UpdateNetwork {
    private val allowedHosts =
        setOf(
            "api.github.com",
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com",
            "github-releases.githubusercontent.com",
        )
    private val http =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .addNetworkInterceptor { chain ->
                val url = chain.request().url
                if (
                    url.scheme != "https" ||
                        url.host !in allowedHosts ||
                        url.username.isNotEmpty() ||
                        url.password.isNotEmpty() ||
                        url.port != 443
                )
                    throw IOException("Untrusted update destination")
                chain.proceed(chain.request())
            }
            .build()

    private suspend fun <T> receive(url: String, block: (Response, () -> Boolean) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "qingluan-ledger-android")
                    .header("Accept-Encoding", "identity")
                    .header("Cache-Control", "no-cache")
                    .apply {
                        if (url == ReleaseSource.API) {
                            header("Accept", "application/vnd.github+json")
                            header("X-GitHub-Api-Version", "2022-11-28")
                        }
                    }
                    .build()
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive)
                            continuation.resumeWithException(UpdateFailure("网络连接失败，请稍后重试"))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val value =
                                response.use {
                                    updateRequire(
                                        it.isSuccessful,
                                        when (it.code) {
                                            403,
                                            429 -> "GitHub 限制了请求，请稍后重试"
                                            404 -> "尚无正式发布版本，或安装包已移除"
                                            else -> "更新服务暂不可用（HTTP ${it.code}）"
                                        },
                                    )
                                    block(it) { continuation.isActive }
                                }
                            if (continuation.isActive) continuation.resume(value)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }
            )
        }

    fun close() {
        // Clearing a navigation ViewModel happens on main. Closing an idle TLS socket may
        // send close_notify, so cleanup must outlive the cancelled ViewModel scope on IO.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { http.dispatcher.cancelAll() }
            runCatching { http.connectionPool.evictAll() }
            runCatching { http.dispatcher.executorService.shutdown() }
        }
    }

    suspend fun latest(): AppRelease {
        val bytes =
            receive(ReleaseSource.API) { response, active ->
                val body = response.body ?: throw UpdateFailure("更新服务返回空内容")
                updateRequire(body.contentLength() <= 1024 * 1024, "更新信息过大")
                body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        if (!active()) throw CancellationException()
                        val n = input.read(buffer)
                        if (n < 0) break
                        updateRequire(out.size() + n <= 1024 * 1024, "更新信息过大")
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            }
        return withContext(Dispatchers.Default) {
            ReleaseSource.parse(bytes.toString(Charsets.UTF_8))
        }
    }

    suspend fun download(release: AppRelease, file: File, progress: (Long) -> Unit): File =
        receive(release.url) { response, active ->
            val body = response.body ?: throw UpdateFailure("下载内容为空")
            updateRequire(
                body.contentLength() < 0 || body.contentLength() == release.size,
                "安装包大小已变化，请重新检查更新",
            )
            var complete = false
            try {
                var count = 0L
                var last = 0L
                file.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (!active()) throw CancellationException()
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            updateRequire(
                                count <= release.size && count <= ReleaseSource.MAX_APK,
                                "下载内容超过允许大小",
                            )
                            output.write(buffer, 0, n)
                            if (count - last >= 128 * 1024) {
                                progress(count)
                                last = count
                            }
                        }
                    }
                }
                updateRequire(count == release.size, "下载不完整，请重试")
                if (!active()) throw CancellationException()
                progress(count)
                complete = true
                file
            } finally {
                if (!complete) file.delete()
            }
        }
}

@Suppress("DEPRECATION")
private fun PackageInfo.code(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

/** Cryptographic APK verification first; matching a parsed certificate alone is insufficient. */
internal object UpdateVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return hex(digest.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun fingerprint(bytes: ByteArray) =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    @Suppress("DEPRECATION")
    fun verify(app: Application, file: File, release: AppRelease) {
        updateRequire(file.isFile && file.length() == release.size, "安装包不存在或下载不完整，请重新下载")
        if (release.sha256 != null)
            updateRequire(sha256(file) == release.sha256, "安装包 SHA-256 不匹配，已停止安装")
        val verified =
            try {
                ApkVerifier.Builder(file)
                    .setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
                    .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                    .build()
                    .verify()
            } catch (_: Exception) {
                throw UpdateFailure("安装包签名校验失败，请重新下载")
            }
        updateRequire(
            verified.isVerified &&
                (verified.isVerifiedUsingV2Scheme ||
                    verified.isVerifiedUsingV3Scheme ||
                    verified.isVerifiedUsingV31Scheme),
            "安装包签名或内容完整性验证失败，已停止安装",
        )
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val installed = app.packageManager.getPackageInfo(app.packageName, flags)
        val archive =
            app.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
                ?: throw UpdateFailure("安装包无法解析")
        updateRequire(archive.packageName == app.packageName, "安装包包名不匹配，已停止安装")
        updateRequire(archive.code() > installed.code(), "安装包内部版本号未递增，无法作为更新安装")
        updateRequire(
            ReleaseSource.version(archive.versionName.orEmpty()) == release.version,
            "安装包版本与发布信息不一致",
        )
        updateRequire(
            (archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT,
            "新版本不支持当前 Android 系统",
        )
        val current =
            if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners
            else installed.signatures
        val expected = current.orEmpty().map { fingerprint(it.toByteArray()) }.toSet()
        val actual = verified.signerCertificates.map { fingerprint(it.encoded) }.toSet()
        updateRequire(expected.isNotEmpty() && actual == expected, "安装包签名与当前安装不一致，已停止安装；请使用同一发布密钥")
    }
}

internal data class AppUpdateState(
    val phase: String = "idle",
    val message: String = "",
    val release: AppRelease? = null,
    val downloaded: Long = 0,
    val file: File? = null,
) {
    val busy
        get() = phase in setOf("checking", "downloading", "verifying", "installing")
}

internal class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val network = UpdateNetwork()
    private val mutable = MutableStateFlow(AppUpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var generation = 0
    private val app
        get() = getApplication<Application>()

    fun check() {
        if (state.value.busy) return
        val id = ++generation
        mutable.value = AppUpdateState("checking", "正在检查更新")
        job =
            viewModelScope.launch {
                try {
                    val release = network.latest()
                    val local =
                        ReleaseSource.version(
                            app.packageManager
                                .getPackageInfo(app.packageName, 0)
                                .versionName
                                .orEmpty()
                        ) ?: throw UpdateFailure("当前应用版本格式无效")
                    mutable.value =
                        if (ReleaseSource.newer(release.version, local))
                            AppUpdateState("available", "发现新版本", release)
                        else AppUpdateState("current", "当前已是最新正式版本")
                    AppLog.record(app, "应用更新")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(id, e)
                }
            }
    }

    fun download() {
        if (state.value.busy) return
        val release = state.value.release ?: return
        val id = ++generation
        mutable.value = AppUpdateState("downloading", "正在下载", release)
        job =
            viewModelScope.launch {
                var file: File? = null
                try {
                    val target =
                        withContext(Dispatchers.IO) {
                            val folder = File(app.cacheDir, "updates")
                            check(folder.mkdirs() || folder.isDirectory)
                            folder
                                .listFiles()
                                ?.filter { it.isFile && it.name.startsWith("ledger-update-") }
                                ?.forEach { it.delete() }
                            File.createTempFile("ledger-update-", ".apk", folder)
                        }
                    file = target
                    network.download(release, target) { count ->
                        viewModelScope.launch {
                            if (id == generation && mutable.value.phase == "downloading")
                                mutable.value =
                                    AppUpdateState("downloading", "正在下载", release, count)
                        }
                    }
                    mutable.value =
                        AppUpdateState("verifying", "正在核验包名、版本与签名", release, release.size)
                    withContext(Dispatchers.IO) { UpdateVerifier.verify(app, target, release) }
                    mutable.value =
                        AppUpdateState("ready", "包名、版本和签名核验通过", release, release.size, target)
                    AppLog.record(app, "应用更新")
                } catch (e: CancellationException) {
                    file?.delete()
                    throw e
                } catch (e: Exception) {
                    file?.delete()
                    fail(id, e, release)
                }
            }
    }

    fun install() {
        val current = state.value
        if (current.busy || current.phase != "ready") return
        val file = current.file ?: return
        val release = current.release ?: return
        if (!app.packageManager.canRequestPackageInstalls()) {
            mutable.value = current.copy(message = "请先允许安装此来源的应用，再点击安装")
            return
        }
        val id = ++generation
        mutable.value = current.copy(phase = "installing", message = "正在重新核验安装包")
        job =
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.IO) { UpdateVerifier.verify(app, file, release) }
                    val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
                    app.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                            )
                    )
                    mutable.value = current.copy(message = "已打开系统安装页面")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fail(id, e, release)
                }
            }
    }

    private suspend fun fail(id: Int, error: Exception, release: AppRelease? = null) {
        if (id != generation) return
        mutable.value =
            AppUpdateState(
                "error",
                if (error is UpdateFailure) error.message.orEmpty() else "更新失败，请检查网络或稍后重试",
                release,
            )
        AppLog.record(app, "应用更新", error)
    }

    fun permissionHint(message: String) {
        if (!state.value.busy) mutable.value = state.value.copy(message = message)
    }

    override fun onCleared() {
        cancel()
        network.close()
        super.onCleared()
    }

    fun cancel() {
        ++generation
        job?.cancel()
        mutable.value = AppUpdateState()
    }
}
