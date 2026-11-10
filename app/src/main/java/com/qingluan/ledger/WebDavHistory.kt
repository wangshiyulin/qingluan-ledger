package com.qingluan.ledger

import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Only immediate children with names owned by this app are eligible for remote operations. */
data class WebDavHistoryEntry(
    val filename: String,
    val modified: String? = null,
    val size: Long? = null,
    val etag: String? = null,
) {
    val legacy: Boolean
        get() = filename == WebDavHistory.LEGACY

    val timestamp: Long
        get() = WebDavHistory.timestamp(filename)

    val deletable: Boolean
        get() = !legacy && WebDavHistory.strongEtag(etag)
}

object WebDavHistory {
    const val LEGACY = "ledger-backup.qlb"
    const val MAX_LIST_BYTES = 1024 * 1024
    const val MAX_ITEMS = 1000
    private val pattern = Regex("ledger-backup-([0-9]{17})-([a-f0-9]{32})\\.qlb")
    private val format =
        DateTimeFormatter.ofPattern("uuuuMMddHHmmssSSS")
            .withResolverStyle(ResolverStyle.STRICT)
            .withZone(ZoneOffset.UTC)

    fun filename(now: Instant = Instant.now()): String =
        "ledger-backup-${format.format(now)}-${UUID.randomUUID().toString().replace("-", "")}.qlb"

    fun owned(name: String): Boolean = pattern.matches(name) && timestamp(name) > 0L

    fun allowed(name: String): Boolean = name == LEGACY || owned(name)

    fun timestamp(name: String): Long =
        runCatching {
                Instant.from(
                        format.parse(pattern.matchEntire(name)?.groupValues?.get(1) ?: return 0L)
                    )
                    .toEpochMilli()
            }
            .getOrDefault(0L)

    fun strongEtag(value: String?): Boolean =
        value != null &&
            value.length in 2..1024 &&
            value.first() == '"' &&
            value.last() == '"' &&
            value.substring(1, value.lastIndex).all {
                it.code == 0x21 || it.code in 0x23..0x7e || it.code in 0x80..0xff
            }

    fun url(config: WebDavConfig, filename: String): URL {
        require(allowed(filename)) { "远端备份文件名无效" }
        return URL(config.collection(), filename)
    }

    internal fun retentionCandidates(
        entries: List<WebDavHistoryEntry>,
        keep: Int,
    ): List<WebDavHistoryEntry> {
        require(keep in setOf(5, 10, 20)) { "保留数量无效" }
        val old =
            entries.filter { owned(it.filename) }.sortedByDescending { it.timestamp }.drop(keep)
        require(old.all { it.deletable }) { "部分备份缺少有效 ETag，未执行自动清理" }
        return old
    }

    private fun child(collection: URL, href: String): String? =
        runCatching {
                val base = collection.toURI()
                val raw = URI(href.trim())
                if (raw.rawUserInfo != null || raw.rawQuery != null || raw.rawFragment != null)
                    return null
                val resolved = base.resolve(raw)
                if (
                    resolved.scheme != base.scheme ||
                        resolved.host != base.host ||
                        resolved.port != base.port
                )
                    return null
                val parent = base.rawPath
                val path = resolved.rawPath ?: return null
                if (!path.startsWith(parent)) return null
                val name = path.removePrefix(parent)
                // Do not decode: encoded separators, dots, aliases and descendants are not
                // accepted.
                name.takeIf { allowed(it) }
            }
            .getOrNull()

    fun parse(collection: URL, bytes: ByteArray): List<WebDavHistoryEntry> {
        require(bytes.size <= MAX_LIST_BYTES) { "远端备份列表过大" }
        val parser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(bytes.inputStream(), "UTF-8")
        val entries = linkedMapOf<String, WebDavHistoryEntry>()
        var responseCount = 0
        var responseDepth = -1
        var propstatDepth = -1
        var href: String? = null
        var props = mutableMapOf<String, String>()
        var accepted = mutableMapOf<String, String>()
        var propStatus = ""
        var responseStatus = ""
        var field: String? = null
        var fieldDepth = -1
        val value = StringBuilder()
        while (true) {
            val event = parser.nextToken()
            require(event != XmlPullParser.DOCDECL) { "远端列表 XML 不安全" }
            require(
                event != XmlPullParser.ENTITY_REF ||
                    parser.name in setOf("amp", "lt", "gt", "apos", "quot")
            ) {
                "远端列表 XML 不安全"
            }
            require(parser.depth <= 32) { "远端列表 XML 层级过深" }
            if (event == XmlPullParser.END_DOCUMENT) break
            val dav = parser.namespace == "DAV:"
            if (event == XmlPullParser.START_TAG && dav) {
                when (parser.name) {
                    "response" -> {
                        require(responseDepth < 0 && ++responseCount <= MAX_ITEMS) { "远端备份列表项目过多" }
                        responseDepth = parser.depth
                        href = null
                        accepted = mutableMapOf()
                        responseStatus = ""
                    }
                    "resourcetype" -> if (propstatDepth >= 0) props["resourcetype"] = "file"
                    "propstat" ->
                        if (responseDepth >= 0) {
                            propstatDepth = parser.depth
                            props = mutableMapOf()
                            propStatus = ""
                        }
                }
                if (
                    responseDepth >= 0 &&
                        parser.name in
                            setOf(
                                "href",
                                "getetag",
                                "getlastmodified",
                                "getcontentlength",
                                "status",
                                "collection",
                            )
                ) {
                    field = parser.name
                    fieldDepth = parser.depth
                    value.setLength(0)
                    if (field == "collection") props["collection"] = "true"
                }
            } else if (
                event == XmlPullParser.TEXT ||
                    event == XmlPullParser.CDSECT ||
                    event == XmlPullParser.ENTITY_REF
            ) {
                if (field != null) {
                    require(value.length + parser.text.length <= 4096) { "远端列表属性过长" }
                    value.append(parser.text)
                }
            } else if (event == XmlPullParser.END_TAG && dav) {
                if (parser.depth == fieldDepth && parser.name == field) {
                    val text = value.toString().trim()
                    when (field) {
                        "href" -> if (propstatDepth < 0) href = text
                        "status" ->
                            if (propstatDepth >= 0) propStatus = text else responseStatus = text
                        else ->
                            if (propstatDepth >= 0 && field != "collection") props[field!!] = text
                    }
                    field = null
                }
                if (parser.name == "propstat" && parser.depth == propstatDepth) {
                    if (Regex("HTTP/\\S+ 200(?: .*)?").matches(propStatus)) accepted.putAll(props)
                    propstatDepth = -1
                }
                if (parser.name == "response" && parser.depth == responseDepth) {
                    val name = href?.let { child(collection, it) }
                    if (
                        name != null &&
                            accepted["resourcetype"] == "file" &&
                            accepted["collection"] == null &&
                            (responseStatus.isEmpty() ||
                                Regex("HTTP/\\S+ 200(?: .*)?").matches(responseStatus))
                    ) {
                        require(!entries.containsKey(name)) { "远端列表包含重复备份" }
                        entries[name] =
                            WebDavHistoryEntry(
                                name,
                                accepted["getlastmodified"]?.take(120),
                                accepted["getcontentlength"]?.toLongOrNull()?.takeIf { it >= 0 },
                                accepted["getetag"]?.takeIf {
                                    it.length <= 1024 && it.none { c -> c == '\r' || c == '\n' }
                                },
                            )
                    }
                    responseDepth = -1
                }
            }
        }
        require(parser.rootValid(bytes)) { "远端未返回有效 WebDAV 列表" }
        return entries.values.sortedWith(
            compareByDescending<WebDavHistoryEntry> { it.timestamp }
                .thenByDescending { it.filename }
        )
    }

    // A multistatus root is required even when the collection is empty.
    private fun XmlPullParser.rootValid(bytes: ByteArray): Boolean {
        val p = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        p.setInput(bytes.inputStream(), "UTF-8")
        while (p.nextToken() != XmlPullParser.START_TAG) {
            if (p.eventType == XmlPullParser.END_DOCUMENT) return false
        }
        return p.name == "multistatus" && p.namespace == "DAV:"
    }
}
