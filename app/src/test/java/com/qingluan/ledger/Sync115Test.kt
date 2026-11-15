package com.qingluan.ledger

import java.net.Proxy
import java.nio.file.Files
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Local HTTPS S3 service. The test CA is generated at runtime and never used by the app. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Sync115Test {
    @Test
    fun s3HttpsSignedUploadListDownloadAndRestoreProtectAgainstConflicts() = runBlocking {
        val directory = Files.createTempDirectory("ledger-test-tls").toFile()
        val password = java.util.UUID.randomUUID().toString()
        val storeFile = directory.resolve("test.p12")
        val keytool = java.io.File(System.getProperty("java.home"), "bin/keytool")
        val process =
            ProcessBuilder(
                    keytool.path,
                    "-genkeypair",
                    "-alias",
                    "local-test",
                    "-keyalg",
                    "RSA",
                    "-keysize",
                    "2048",
                    "-storetype",
                    "PKCS12",
                    "-keystore",
                    storeFile.path,
                    "-storepass",
                    password,
                    "-dname",
                    "CN=localhost",
                    "-validity",
                    "1",
                    "-ext",
                    "SAN=ip:127.0.0.1,dns:localhost",
                    "-noprompt",
                )
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        val keyStore =
            KeyStore.getInstance("PKCS12").apply {
                storeFile.inputStream().use { load(it, password.toCharArray()) }
            }
        val keys =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password.toCharArray())
            }
        val trusts =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore)
            }
        val ssl =
            SSLContext.getInstance("TLS").apply {
                init(keys.keyManagers, trusts.trustManagers, null)
            }
        val trust = trusts.trustManagers.filterIsInstance<X509TrustManager>().single()
        val http =
            OkHttpClient.Builder()
                .sslSocketFactory(ssl.socketFactory, trust)
                .proxy(Proxy.NO_PROXY)
                .build()
        val pool = Executors.newCachedThreadPool()
        val server =
            ssl.serverSocketFactory.createServerSocket(
                0,
                8,
                java.net.InetAddress.getByName("127.0.0.1"),
            )
        val objects = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
        val currentEtag = java.util.concurrent.atomic.AtomicReference("\"revision-1\"")
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        val worker =
            Thread {
                    while (!server.isClosed) {
                        try {
                            val socket = server.accept()
                            pool.execute {
                                socket.use {
                                    try {
                                        socket.soTimeout = 10000
                                        val input = socket.getInputStream().buffered()
                                        val request = readLine(input).split(' ')
                                        val method = request[0]
                                        val uri = java.net.URI(request[1])
                                        val headers = mutableMapOf<String, String>()
                                        while (true) {
                                            val line = readLine(input)
                                            if (line.isEmpty()) break
                                            headers[line.substringBefore(':').lowercase()] =
                                                line.substringAfter(':').trim()
                                        }
                                        val bytes =
                                            input.readNBytes(
                                                headers["content-length"]?.toInt() ?: 0
                                            )
                                        val auth = headers["authorization"].orEmpty()
                                        val access =
                                            auth.substringAfter("Credential=").substringBefore('/')
                                        val allowed =
                                            access == "local-access" &&
                                                validSignature(
                                                    method,
                                                    uri,
                                                    headers,
                                                    bytes,
                                                    "local-secret",
                                                )
                                        val path = uri.path
                                        var status = 200
                                        val response =
                                            if (!allowed) {
                                                status = 403
                                                byteArrayOf()
                                            } else if (uri.rawQuery != null) {
                                                val contents =
                                                    objects.entries.joinToString("") { (key, value)
                                                        ->
                                                        "<Contents><Key>${key.removePrefix("/bucket/")}</Key>" +
                                                            "<Size>${value.size}</Size><ETag>&quot;revision-1&quot;</ETag>" +
                                                            "<LastModified>2026-11-15T00:00:00Z</LastModified></Contents>"
                                                    }
                                                ("<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">" +
                                                        "<IsTruncated>false</IsTruncated>$contents</ListBucketResult>")
                                                    .toByteArray()
                                            } else if (method == "PUT") {
                                                if (
                                                    headers["if-none-match"] != "*" ||
                                                        objects.putIfAbsent(path, bytes) != null
                                                )
                                                    status = 412
                                                else status = 201
                                                byteArrayOf()
                                            } else if (headers["if-match"] != currentEtag.get()) {
                                                status = 412
                                                byteArrayOf()
                                            } else
                                                objects[path] ?: byteArrayOf().also { status = 404 }
                                        socket.getOutputStream().apply {
                                            write(
                                                "HTTP/1.1 $status Response\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n"
                                                    .toByteArray()
                                            )
                                            write(response)
                                            flush()
                                        }
                                    } catch (_: javax.net.ssl.SSLException) {
                                        // The default production client intentionally rejects this
                                        // test CA.
                                    } catch (e: Throwable) {
                                        failures += e
                                    }
                                }
                            }
                        } catch (e: java.net.SocketException) {
                            if (!server.isClosed) failures += e
                        }
                    }
                }
                .apply {
                    isDaemon = true
                    start()
                }
        try {
            val cfg =
                S3Config(
                    "https://127.0.0.1:${server.localPort}",
                    "bucket",
                    "test-region",
                    "local-access",
                    "local-secret",
                )
            val client = S3Client(cfg, http)
            assertTrue(client.history().isEmpty())
            val raw =
                CompleteBackup.encode(
                    listOf(Category(1, "餐饮", "EXPENSE"), Category(2, "工资", "INCOME")),
                    listOf(
                        Entry(
                            id = 1,
                            amount = 12345,
                            categoryId = 1,
                            occurredAt = 123456789,
                            note = "跨设备同步中文",
                        )
                    ),
                    PersonalBackup(nickname = "本地TLS测试"),
                )
            val encrypted = BackupCipher.encrypt(raw, "test-backup-password".toCharArray())
            assertFalse(encrypted.contains("跨设备同步中文"))
            val name = client.upload(encrypted)
            objects["/bucket/qingluan/unrelated.txt"] = "ignore".toByteArray()
            val list = client.history()
            assertEquals(listOf(name), list.map { it.filename })
            val downloaded = client.download(list.single())
            assertEquals(encrypted, downloaded)
            val restored = BackupCipher.decrypt(downloaded, "test-backup-password".toCharArray())
            assertEquals(raw, restored)
            assertTrue(
                runCatching { BackupCipher.decrypt(downloaded, "wrong-password".toCharArray()) }
                    .isFailure
            )
            val repo = Repository(RuntimeEnvironment.getApplication())
            try {
                LocalDataManager(RuntimeEnvironment.getApplication(), repo).restore(restored)
                assertEquals("跨设备同步中文", repo.dao.allEntries().single().note)
                assertEquals(12345L, repo.dao.allEntries().single().amount)
            } finally {
                repo.db.close()
            }
            currentEtag.set("\"revision-2\"")
            assertTrue(
                runCatching { client.download(list.single()) }
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("远端备份已变化")
            )
            assertTrue(
                runCatching { S3Client(cfg.copy(secretKey = "wrong"), http).history() }
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("认证")
            )
            assertTrue(
                runCatching { S3Client(cfg).history() }
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("证书")
            )
            assertTrue(failures.toString(), failures.isEmpty())
        } finally {
            server.close()
            worker.join(1000)
            pool.shutdownNow()
            http.connectionPool.evictAll()
            http.dispatcher.executorService.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun s3RejectsUnsafeEndpointsAndUnboundedOrMalformedHistory() {
        val config = S3Config("https://s3.example", "bucket", "auto", "test", "secret")
        for (endpoint in
            listOf(
                "http://s3.example",
                "https://user:pass@s3.example",
                "https://s3.example/path",
                "https://s3.example/?secret=x",
            )) {
            assertTrue(runCatching { config.copy(endpoint = endpoint).validate() }.isFailure)
        }
        for (xml in
            listOf(
                "<html/>",
                "<ListBucketResult><IsTruncated>true</IsTruncated></ListBucketResult>",
                "<!DOCTYPE root [<!ENTITY ext SYSTEM 'file:///not-used'>]><ListBucketResult>&ext;</ListBucketResult>",
            )) {
            assertTrue(xml, runCatching { parseHistory(xml.toByteArray(), "qingluan/") }.isFailure)
        }
    }

    private fun readLine(input: java.io.InputStream): String {
        val buffer = java.io.ByteArrayOutputStream()
        while (true) {
            val ch = input.read()
            check(ch >= 0) { "Unexpected EOF" }
            if (ch == 10) return buffer.toString("US-ASCII").removeSuffix("\r")
            buffer.write(ch)
            check(buffer.size() <= 16384)
        }
    }

    // Verify what arrived at the service, including actual HTTP headers, encoded path and payload.
    private fun validSignature(
        method: String,
        uri: java.net.URI,
        headers: Map<String, String>,
        body: ByteArray,
        secret: String,
    ): Boolean {
        val auth = headers["authorization"] ?: return false
        val scope = auth.substringAfter("Credential=").substringBefore(',').substringAfter('/')
        val signed = auth.substringAfter("SignedHeaders=").substringBefore(',')
        val names = signed.split(';')
        val hash = hex(MessageDigest.getInstance("SHA-256").digest(body))
        if (hash != headers["x-amz-content-sha256"]) return false
        if (names.any { headers[it] == null }) return false
        val canonicalHeaders =
            names.joinToString("") {
                "$it:${headers[it].orEmpty().trim().replace(Regex("\\s+"), " ")}\n"
            }
        val canonical =
            listOf(method, uri.rawPath, uri.rawQuery.orEmpty(), canonicalHeaders, signed, hash)
                .joinToString("\n")
        val message =
            "AWS4-HMAC-SHA256\n${headers["x-amz-date"]}\n$scope\n${hex(MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()))}"
        val parts = scope.split('/')
        var key = ("AWS4$secret").toByteArray()
        for (part in parts) key =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                doFinal(part.toByteArray())
            }
        val expected =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(key, "HmacSHA256"))
                hex(doFinal(message.toByteArray()))
            }
        return MessageDigest.isEqual(
            expected.toByteArray(),
            auth.substringAfter("Signature=").toByteArray(),
        )
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
