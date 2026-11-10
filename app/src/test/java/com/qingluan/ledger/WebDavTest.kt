package com.qingluan.ledger

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDavTest {
    @Test
    fun encryptedRoundTripAndConditionalConflict() = runBlocking {
        val payload = AtomicReference<String?>(null)
        val etag = AtomicReference("\"1\"")
        val server = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val worker =
            Thread {
                    while (!server.isClosed) {
                        try {
                            server.accept().use { socket ->
                                socket.soTimeout = 10000
                                val reader =
                                    socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                                val request = reader.readLine().split(" ")
                                val method = request[0]
                                val path = request[1]
                                val headers = mutableMapOf<String, String>()
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isEmpty()) break
                                    headers[line.substringBefore(':').lowercase()] =
                                        line.substringAfter(':').trim()
                                }
                                val extra = mutableMapOf<String, String>()
                                var body = ""
                                val code =
                                    if (headers["authorization"] != "Basic dXNlcjpwYXNz") 401
                                    else if (path == "/ledger/") 200
                                    else
                                        when (method) {
                                            "HEAD",
                                            "GET" ->
                                                if (payload.get() == null) 404
                                                else {
                                                    extra["ETag"] = etag.get()
                                                    if (method == "GET") body = payload.get()!!
                                                    200
                                                }
                                            "PUT" -> {
                                                val create = headers["if-none-match"] == "*"
                                                val matches = headers["if-match"] == etag.get()
                                                val size = headers["content-length"]!!.toInt()
                                                val chars = CharArray(size)
                                                var offset = 0
                                                while (offset < size) {
                                                    val n =
                                                        reader.read(chars, offset, size - offset)
                                                    check(n > 0)
                                                    offset += n
                                                }
                                                if (
                                                    (create && payload.get() != null) ||
                                                        (!create && !matches)
                                                )
                                                    412
                                                else {
                                                    payload.set(String(chars))
                                                    201
                                                }
                                            }
                                            else -> 405
                                        }
                                val bytes = body.toByteArray(Charsets.UTF_8)
                                val response =
                                    "HTTP/1.1 $code Response\r\nConnection: close\r\nContent-Length: ${bytes.size}\r\n" +
                                        extra.entries.joinToString("") {
                                            "${it.key}: ${it.value}\r\n"
                                        } +
                                        "\r\n"
                                socket.getOutputStream().apply {
                                    write(response.toByteArray(Charsets.US_ASCII))
                                    if (method != "HEAD") write(bytes)
                                    flush()
                                }
                            }
                        } catch (e: java.net.SocketException) {
                            if (!server.isClosed) throw e
                        }
                    }
                }
                .apply {
                    isDaemon = true
                    start()
                }
        try {
            val cfg = WebDavConfig("https://dav.example/ledger/", "user", "pass")
            val factory: (URL) -> HttpURLConnection = { url ->
                URL("http://127.0.0.1:${server.localPort}${url.path}").openConnection()
                    as HttpURLConnection
            }
            val client = WebDavClient(cfg, factory)
            assertFalse(client.test().exists)
            val raw =
                CompleteBackup.encode(
                    listOf(Category(id = 1, name = "餐饮", type = "EXPENSE"), Category(id = 2, name = "工资", type = "INCOME")),
                    listOf(
                        Entry(
                            id = 1,
                            amount = 700,
                            categoryId = 1,
                            note = "备注",
                            occurredAt = 123456,
                        )
                    ),
                    PersonalBackup(nickname = "测试"),
                )
            val encrypted = BackupCipher.encrypt(raw, "backup-secret".toCharArray())
            assertFalse(encrypted.contains("备注"))
            client.upload(encrypted, RemoteBackup(false))
            val downloaded = client.download()
            assertEquals(
                raw,
                BackupCipher.decrypt(downloaded.encrypted, "backup-secret".toCharArray()),
            )
            assertTrue(
                runCatching {
                        BackupCipher.decrypt(downloaded.encrypted, "wrong-password".toCharArray())
                    }
                    .isFailure
            )
            val context = RuntimeEnvironment.getApplication()
            val repo = Repository(context)
            try {
                repo.dao.clear()
                repo.dao.clearCategories()
                LocalDataManager(context, repo).restore(raw)
                assertEquals("备注", repo.dao.allEntries().single().note)
                val localStore = WebDavStore(context)
                localStore.preserve(encrypted)
                assertTrue(localStore.hasRecovery())
                assertEquals(encrypted, localStore.recoveryBytes().toString(Charsets.UTF_8))
            } finally {
                repo.db.close()
            }
            etag.set("\"2\"")
            val conflict =
                runCatching { client.upload(encrypted, downloaded.metadata) }.exceptionOrNull()
            assertTrue(conflict?.message.orEmpty().contains("远端备份已变化"))
            assertEquals(encrypted, payload.get())
            assertTrue(runCatching { client.upload(encrypted, RemoteBackup(true)) }.isFailure)
            assertTrue(
                runCatching { WebDavClient(cfg.copy(password = "wrong"), factory).test() }
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("认证失败")
            )
        } finally {
            server.close()
            worker.join(1000)
        }
    }

    @Test
    fun validatesEndpointsRedirectsAndDownloadBounds() = runBlocking {
        for (address in
            listOf(
                "http://dav.example/",
                "https://user:pass@dav.example/",
                "https://dav.example/?q=x",
                "https://dav.example/#x",
                "file:///tmp/",
            )) assertTrue(
            address,
            runCatching { WebDavConfig(address, "user", "pass").collection() }.isFailure,
        )
        assertEquals(
            "https://dav.example/folder/ledger-backup.qlb",
            WebDavConfig("https://dav.example/folder", "user", "pass").file().toString(),
        )
        val cfg = WebDavConfig("https://dav.example/", "user", "pass")
        class Stub(url: URL, private val code: Int, private val length: Long = 0) :
            HttpURLConnection(url) {
            override fun connect() {}

            override fun disconnect() {}

            override fun usingProxy() = false

            override fun getResponseCode() = code

            override fun getContentLengthLong() = length

            override fun getInputStream() = "not-a-backup".byteInputStream()
        }
        assertTrue(
            runCatching { WebDavClient(cfg) { Stub(it, 302) }.test() }
                .exceptionOrNull()
                ?.message
                .orEmpty()
                .contains("重定向")
        )
        assertTrue(
            runCatching {
                    WebDavClient(cfg) { Stub(it, 200, CompleteBackup.MAX_BYTES.toLong() + 1) }
                        .download()
                }
                .exceptionOrNull()
                ?.message
                .orEmpty()
                .contains("16MB")
        )
        assertTrue(
            runCatching { WebDavClient(cfg) { Stub(it, 200) }.download() }
                .exceptionOrNull()
                ?.message
                .orEmpty()
                .contains("不是青鸾加密备份")
        )
    }
}
