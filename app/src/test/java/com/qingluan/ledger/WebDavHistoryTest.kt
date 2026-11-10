package com.qingluan.ledger

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebDavHistoryTest {
    private val config = WebDavConfig("https://dav.example/ledger/", "user", "pass")
    private val older = "ledger-backup-20260102030405006-0123456789abcdef0123456789abcdef.qlb"
    private val newer = "ledger-backup-20261110030405006-fedcba9876543210fedcba9876543210.qlb"

    private fun response(href: String, extra: String = "", status: String = "HTTP/1.1 200 OK") =
        """<d:response><d:href>$href</d:href><d:propstat><d:prop><d:getetag>&quot;v1&quot;</d:getetag><d:getcontentlength>123</d:getcontentlength><d:resourcetype/>$extra</d:prop><d:status>$status</d:status></d:propstat></d:response>"""
    private fun xml(body: String) = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">$body</d:multistatus>""".toByteArray()

    @Test fun parsesBoundedImmediateFilesAndSortsNewest() {
        val rows = WebDavHistory.parse(config.collection(), xml(
            response("/ledger/$older") + response("https://dav.example/ledger/$newer") +
                response("/ledger/ledger-backup.qlb") + response("/ledger/unrelated.qlb") +
                response("https://evil.example/ledger/$newer") + response("/ledger/nested/$newer") +
                response("/other/$newer") + response("/ledger/%2e%2e/$newer") +
                response("/ledger/%6cedger-backup.qlb") + response("/ledger/$newer?secret=x") +
                response("/ledger/folder/", "<d:resourcetype><d:collection/></d:resourcetype>")))
        assertEquals(listOf(newer, older, WebDavHistory.LEGACY), rows.map { it.filename })
        assertEquals(123L, rows.first().size)
        assertEquals("\"v1\"", rows.first().etag)
        assertTrue(rows.first().deletable)
        assertFalse(rows.last().deletable)
        assertEquals(Instant.parse("2026-11-10T03:04:05.006Z").toEpochMilli(), rows.first().timestamp)
    }

    @Test fun rejectsDoctypeOversizedDuplicateAndExcessiveLists() {
        val inputs = listOf(
            """<!DOCTYPE d:multistatus [<!ENTITY x SYSTEM "file:///etc/passwd">]><d:multistatus xmlns:d="DAV:">&x;</d:multistatus>""".toByteArray(),
            ByteArray(WebDavHistory.MAX_LIST_BYTES + 1),
            xml(response("/ledger/$newer") + response("/ledger/$newer")),
            xml((0..WebDavHistory.MAX_ITEMS).joinToString("") { response("/ledger/ignored-$it") }),
            "<html/>".toByteArray(),
        )
        inputs.forEach { assertTrue(runCatching { WebDavHistory.parse(config.collection(), it) }.isFailure) }
    }

    @Test fun failedPropertiesAndCollectionsCannotBeUsedForDeletion() {
        val rows = WebDavHistory.parse(config.collection(), xml(
            """<d:response><d:href>/ledger/$older</d:href><d:propstat><d:prop><d:resourcetype/></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat><d:propstat><d:prop><d:getetag>&quot;v1&quot;</d:getetag></d:prop><d:status>HTTP/1.1 404 Not Found</d:status></d:propstat></d:response>""" +
                response("/ledger/$newer", "<d:resourcetype><d:collection/></d:resourcetype>")))
        assertEquals(1, rows.size)
        assertNull(rows.first().etag)
        assertFalse(rows.first().deletable)
        assertFalse(WebDavHistory.strongEtag("W/\"v1\""))
        assertFalse(WebDavHistory.strongEtag("\"v1\"\r\nInjected: yes"))
    }

    @Test fun createsUniqueNamesAndRefusesForeignOrLegacyDeletion() = runBlocking {
        val names = (1..100).map { WebDavHistory.filename(Instant.parse("2026-11-10T00:00:00Z")) }
        assertEquals(100, names.toSet().size)
        assertTrue(names.all { WebDavHistory.owned(it) })
        val client = WebDavClient(config) { error("Must reject before opening connection") }
        for (name in listOf(WebDavHistory.LEGACY, "unrelated.qlb", "../$newer", "$newer/child")) {
            assertTrue(runCatching { client.delete(WebDavHistoryEntry(name, etag = "\"v1\"")) }.isFailure)
        }
        assertTrue(runCatching { client.delete(WebDavHistoryEntry(newer, etag = "W/\"v1\"")) }.isFailure)
    }

    @Test fun newUploadIsCreateOnlyAndDeleteUsesSelectedEtag() = runBlocking {
        class Stub(url: URL) : HttpURLConnection(url) {
            val data = ByteArrayOutputStream()
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = if (requestMethod == "DELETE") 204 else 201
            override fun getOutputStream() = data
        }
        var last: Stub? = null
        val client = WebDavClient(config) { Stub(it).also { last = it } }
        val name = client.uploadHistory("encrypted-data")
        assertTrue(WebDavHistory.owned(name))
        assertEquals("PUT", last!!.requestMethod)
        assertEquals("*", last!!.getRequestProperty("If-None-Match"))
        assertEquals("encrypted-data", last!!.data.toString("UTF-8"))
        client.delete(WebDavHistoryEntry(newer, etag = "\"selected\""))
        assertEquals("DELETE", last!!.requestMethod)
        assertEquals("\"selected\"", last!!.getRequestProperty("If-Match"))
    }

    @Test fun retentionProtectsLegacyAndUnrelatedFilesAndRefusesMissingEtags() {
        val entries = (1..8).map { day ->
            WebDavHistoryEntry("ledger-backup-202611${day.toString().padStart(2, '0')}000000000-0123456789abcdef0123456789abcdef.qlb", etag = "\"v1\"")
        }
        val old = WebDavHistory.retentionCandidates(entries + listOf(
            WebDavHistoryEntry(WebDavHistory.LEGACY, etag = "\"legacy\""),
            WebDavHistoryEntry("unrelated.qlb", etag = "\"other\"")), 5)
        assertEquals(entries.take(3).reversed(), old)
        assertTrue(runCatching { WebDavHistory.retentionCandidates(entries.map { it.copy(etag = null) }, 5) }.isFailure)
        assertTrue(runCatching { WebDavHistory.retentionCandidates(entries, 0) }.isFailure)
    }

    @Test fun staleSelectionCannotDownloadReplacement() = runBlocking {
        class Stub(url: URL) : HttpURLConnection(url) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getHeaderField(name: String?) = if (name == "ETag") "\"replacement\"" else null
            override fun getInputStream() = error("Must reject before reading body")
        }
        var last: Stub? = null
        val client = WebDavClient(config) { Stub(it).also { last = it } }
        assertTrue(runCatching { client.download(WebDavHistoryEntry(newer, etag = "\"selected\"")) }.isFailure)
        assertEquals("\"selected\"", last!!.getRequestProperty("If-Match"))
    }
}
