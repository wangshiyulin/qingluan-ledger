package com.qingluan.ledger

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Text-only OOXML interchange. Formula cells are rejected, never evaluated. */
object XlsxTransfer {
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_ROWS = 100001
    private const val CHUNKS = "__qingluan_receipt_chunks_v1__"
    private const val AUX = "票据附件"
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    fun export(
        rows: List<Entry>,
        cats: List<Category>,
        includeNote: Boolean = true,
        includeTags: Boolean = true,
        includeReceipt: Boolean = true,
    ): ByteArray {
        require(rows.size <= 100000) { "XLSX账单数量超过限制" }
        val names = cats.associate { it.id to it.name }
        val header =
            listOf("日期", "时间", "类型", "分类", "币种", "金额") +
                (if (includeNote) listOf("备注") else emptyList()) +
                (if (includeTags) listOf("标签") else emptyList()) +
                (if (includeReceipt) listOf("票据") else emptyList())
        val attachments = mutableListOf<List<String>>()
        val zone = ZoneId.systemDefault()
        var sourceChars = 0L
        val data =
            rows
                .sortedWith(compareByDescending<Entry> { it.occurredAt }.thenByDescending { it.id })
                .mapIndexed { index, e ->
                    sourceChars +=
                        (if (includeNote) e.note.length.toLong() else 0L) +
                            (if (includeTags) e.tags.length.toLong() else 0L) +
                            (if (includeReceipt) e.receipt.length.toLong() else 0L) +
                            (names[e.categoryId]?.length ?: 0) +
                            100L
                    require(sourceChars <= MAX_BYTES) { "XLSX内容超过16MB限制" }
                    val local = Instant.ofEpochMilli(e.occurredAt).atZone(zone)
                    val receipt =
                        if (includeReceipt && e.receipt.length > 32767) {
                            e.receipt.chunked(30000).forEachIndexed { part, chunk ->
                                attachments.add(
                                    listOf((index + 2).toString(), part.toString(), chunk)
                                )
                            }
                            CHUNKS
                        } else e.receipt
                    listOf(
                        local.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        local.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        if (e.type == "INCOME") "收入" else "支出",
                        names[e.categoryId] ?: "",
                        e.currencyCode,
                        Money.text(e.amount, e.currencyCode),
                    ) +
                        (if (includeNote) listOf(e.note) else emptyList()) +
                        (if (includeTags) listOf(e.tags) else emptyList()) +
                        (if (includeReceipt) listOf(receipt) else emptyList())
                }
        val hasAux = attachments.isNotEmpty()
        val files = linkedMapOf<String, String>()
        files["[Content_Types].xml"] =
            """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>${if (hasAux) "<Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" else ""}</Types>"""
        files["_rels/.rels"] =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
        files["xl/workbook.xml"] =
            """<workbook xmlns="$NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="账单" sheetId="1" r:id="rId1"/>${if (hasAux) "<sheet name=\"$AUX\" sheetId=\"2\" r:id=\"rId2\"/>" else ""}</sheets></workbook>"""
        files["xl/_rels/workbook.xml.rels"] =
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>${if (hasAux) "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/>" else ""}</Relationships>"""
        files["xl/worksheets/sheet1.xml"] = sheet(listOf(header) + data, numericAmountColumn = 5)
        if (hasAux)
            files["xl/worksheets/sheet2.xml"] =
                sheet(listOf(listOf("账单行", "分片", "内容")) + attachments)
        val out = ByteArrayOutputStream()
        var total = 0
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, xml) ->
                val bytes = xml.toByteArray(Charsets.UTF_8)
                total += bytes.size
                require(total <= MAX_BYTES) { "XLSX解压内容超过16MB限制" }
                // Stored entries also keep our own exports within the import compression ratio
                // limit.
                val entry =
                    ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        compressedSize = size
                        crc = CRC32().apply { update(bytes) }.value
                    }
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        require(out.size() <= MAX_BYTES) { "XLSX超过16MB限制" }
        return out.toByteArray()
    }

    private fun sheet(rows: List<List<String>>, numericAmountColumn: Int? = null) = buildString {
        require(rows.size <= MAX_ROWS) { "XLSX记录数超过限制" }
        append(
            "<worksheet xmlns=\"$NS\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"9\" width=\"22\" customWidth=\"1\"/></cols><sheetData>"
        )
        rows.forEachIndexed { r, cells ->
            append("<row r=\"${r + 1}\">")
            cells.forEachIndexed { c, value ->
                require(value.length <= 32767) { "XLSX单元格超过32767字限制，请缩短备注或分类" }
                val numeric =
                    r > 0 &&
                        c == numericAmountColumn &&
                        value.toBigDecimalOrNull()?.stripTrailingZeros()?.precision()?.let {
                            it <= 15
                        } == true
                if (numeric) {
                    append("<c r=\"${column(c)}${r + 1}\" t=\"n\"><v>")
                    append(value)
                    append("</v></c>")
                } else {
                    append(
                        "<c r=\"${column(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                    )
                    append(escape(value))
                    append("</t></is></c>")
                }
            }
            append("</row>")
            require(length <= MAX_BYTES) { "XLSX解压内容超过16MB限制" }
        }
        append("</sheetData></worksheet>")
    }

    private fun column(index: Int): String =
        if (index < 26) ('A' + index).toString() else column(index / 26 - 1) + ('A' + index % 26)

    private fun escape(value: String): String = buildString {
        value.forEachIndexed { index, ch ->
            when {
                ch == '_' && Regex("_x[0-9a-fA-F]{4}_").find(value, index)?.range?.first == index ->
                    append("_x005F_")
                ch == '\r' ||
                    ch.code < 32 && ch != '\n' && ch != '\t' ||
                    ch == '\uFFFE' ||
                    ch == '\uFFFF' -> append("_x%04X_".format(ch.code))
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                else -> append(ch)
            }
        }
    }

    private fun unescape(value: String) =
        Regex("_x([0-9a-fA-F]{4})_").replace(value) {
            it.groupValues[1].toInt(16).toChar().toString()
        }

    fun preview(bytes: ByteArray): CsvPreview {
        val files = unzip(bytes)
        require(files.containsKey("xl/workbook.xml")) { "XLSX工作簿缺失" }
        // Check every relationship, including those not needed to import ledger data.
        files
            .filterKeys { it.endsWith(".rels") }
            .forEach { (path, content) ->
                parse(content) { p ->
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                        require(p.getAttributeValue(null, "TargetMode") != "External") {
                            "XLSX不支持外部链接"
                        }
                        resolve(
                            relationshipBase(path),
                            p.getAttributeValue(null, "Target") ?: error("XLSX关系无效"),
                        )
                    }
                }
            }
        val relations = mutableMapOf<String, String>()
        parse(files["xl/_rels/workbook.xml.rels"] ?: error("XLSX工作簿关系缺失")) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                val type = p.getAttributeValue(null, "Type").orEmpty()
                if (type.endsWith("/worksheet"))
                    relations[p.getAttributeValue(null, "Id")] =
                        resolve("xl", p.getAttributeValue(null, "Target"))
            }
        }
        val sheets = mutableListOf<Pair<String, String>>()
        var date1904 = false
        parse(files.getValue("xl/workbook.xml")) { p ->
            if (p.eventType == XmlPullParser.START_TAG && p.name == "workbookPr") {
                date1904 = p.getAttributeValue(null, "date1904") in listOf("1", "true")
            }
            if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                val id =
                    p.getAttributeValue(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id",
                    )
                sheets.add(
                    p.getAttributeValue(null, "name").orEmpty() to
                        (relations[id] ?: error("XLSX工作表关系无效"))
                )
            }
        }
        require(sheets.isNotEmpty()) { "XLSX没有工作表" }
        val strings = mutableListOf<String>()
        files["xl/sharedStrings.xml"]?.let { xml ->
            var text: StringBuilder? = null
            var inText = false
            parse(xml) { p ->
                when (p.eventType) {
                    XmlPullParser.START_TAG ->
                        when (p.name) {
                            "si" -> text = StringBuilder()
                            "t" -> inText = true
                        }
                    XmlPullParser.TEXT,
                    XmlPullParser.ENTITY_REF -> if (inText) text?.append(p.text)
                    XmlPullParser.END_TAG ->
                        when (p.name) {
                            "t" -> inText = false
                            "si" -> {
                                strings.add(unescape(text.toString()))
                                require(strings.size <= 900000) { "XLSX字符串数量超过限制" }
                                text = null
                            }
                        }
                }
            }
        }
        val main = readSheet(files[sheets.first().second] ?: error("XLSX账单表缺失"), strings, date1904)
        require(main.isNotEmpty()) { "XLSX为空" }
        val receiptIndex = main.first().indexOf("票据")
        val chunks = mutableMapOf<Int, java.util.SortedMap<Int, String>>()
        sheets
            .drop(1)
            .firstOrNull { it.first == AUX }
            ?.let { aux ->
                val content = readSheet(files[aux.second] ?: error("XLSX票据附件表缺失"), strings)
                require(content.firstOrNull() == listOf("账单行", "分片", "内容")) { "XLSX票据附件表格式无效" }
                content.drop(1).forEach { cells ->
                    require(cells.size == 3) { "XLSX票据分片无效" }
                    val row = cells[0].toIntOrNull() ?: error("XLSX票据行号无效")
                    val part = cells[1].toIntOrNull() ?: error("XLSX票据分片编号无效")
                    require(row in 2..main.size && part in 0..100 && cells[2].length <= 30000) {
                        "XLSX票据分片超出限制"
                    }
                    require(chunks.getOrPut(row) { sortedMapOf() }.put(part, cells[2]) == null) {
                        "XLSX票据分片重复"
                    }
                }
            }
        val csv = buildString {
            main.forEachIndexed { index, source ->
                val row = source.toMutableList()
                if (index > 0 && receiptIndex in row.indices && row[receiptIndex] == CHUNKS) {
                    val parts = chunks.remove(index + 1) ?: error("XLSX票据分片缺失")
                    require(parts.keys.toList() == (0 until parts.size).toList()) { "XLSX票据分片不连续" }
                    row[receiptIndex] = parts.values.joinToString("")
                }
                row.forEachIndexed { c, raw ->
                    if (c > 0) append(',')
                    // CSV's modern escaping is applied exactly once, only at this bridge.
                    val value =
                        if (index == 0 && raw == "日期") "日期（文本）"
                        else if (
                            index > 0 &&
                                c != receiptIndex &&
                                (raw.startsWith("'") ||
                                    raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                                    raw.startsWith('\t') ||
                                    raw.startsWith('\r'))
                        )
                            "'$raw"
                        else raw
                    append('"')
                    append(value.replace("\"", "\"\""))
                    append('"')
                }
                append("\r\n")
            }
        }
        require(chunks.isEmpty()) { "XLSX存在未关联的票据分片" }
        return CsvImport.preview(csv)
    }

    private fun readSheet(
        xml: ByteArray,
        strings: List<String>,
        date1904: Boolean = false,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row: MutableList<String>? = null
        var column = -1
        var type = ""
        var value = StringBuilder()
        var capture = false
        var hasValue = false
        parse(xml) { p ->
            when (p.eventType) {
                XmlPullParser.START_TAG ->
                    when (p.name) {
                        "row" -> {
                            require(row == null && rows.size < MAX_ROWS) { "XLSX记录数超过限制" }
                            val number =
                                p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rows.size + 1)
                            require(number == rows.size + 1) { "XLSX行号不连续，请删除空行后导入" }
                            row = mutableListOf()
                            column = -1
                        }
                        "c" -> {
                            require(row != null) { "XLSX单元格不在行内" }
                            val reference = p.getAttributeValue(null, "r")
                            val next =
                                if (reference == null) column + 1
                                else {
                                    require(reference.matches(Regex("[A-Z]{1,3}[1-9][0-9]*"))) {
                                        "XLSX单元格坐标无效"
                                    }
                                    reference
                                        .takeWhile { it in 'A'..'Z' }
                                        .fold(0) { n, ch -> n * 26 + (ch - 'A') + 1 } - 1
                                }
                            require(next > column && next < 128) { "XLSX列数或顺序无效" }
                            column = next
                            type = p.getAttributeValue(null, "t").orEmpty()
                            value = StringBuilder()
                            hasValue = false
                        }
                        "f" -> error("XLSX含公式，请在表格软件中粘贴为值后导入")
                        "v",
                        "t" -> {
                            capture = true
                            hasValue = true
                        }
                    }
                XmlPullParser.TEXT,
                XmlPullParser.ENTITY_REF ->
                    if (capture) {
                        value.append(p.text)
                        require(value.length <= 32767) { "XLSX单元格超过32767字限制" }
                    }
                XmlPullParser.END_TAG ->
                    when (p.name) {
                        "v",
                        "t" -> capture = false
                        "c" -> {
                            val text =
                                when (type) {
                                    "s" ->
                                        strings.getOrNull(value.toString().toIntOrNull() ?: -1)
                                            ?: error("XLSX共享字符串索引无效")
                                    "inlineStr",
                                    "str",
                                    "d" -> unescape(value.toString())
                                    "",
                                    "n" -> {
                                        val header = rows.firstOrNull()?.getOrNull(column)
                                        if (hasValue && header in listOf("日期", "日期（文本）", "时间")) {
                                            excelDate(value.toString(), header == "时间", date1904)
                                        } else value.toString()
                                    }
                                    else -> error("XLSX单元格类型不支持")
                                }
                            while (row!!.size <= column) row!!.add("")
                            row!![column] = text
                        }
                        "row" -> {
                            rows.add(row!!.toList())
                            row = null
                        }
                    }
            }
        }
        if (rows.isNotEmpty()) {
            val width = rows.first().size
            return rows.map { if (it.size < width) it + List(width - it.size) { "" } else it }
        }
        return rows
    }

    private fun excelDate(raw: String, time: Boolean, date1904: Boolean): String {
        val serial = raw.toBigDecimalOrNull() ?: error("XLSX日期或时间数值无效")
        require(serial >= BigDecimal.ZERO && serial <= BigDecimal(150000)) { "XLSX日期或时间数值超出范围" }
        val whole = serial.setScale(0, RoundingMode.DOWN).toLong()
        if (time) {
            require(serial < BigDecimal.ONE) { "XLSX时间必须为一天内的时间数值" }
            val nanos =
                serial
                    .multiply(BigDecimal(86400000000000L))
                    .setScale(0, RoundingMode.HALF_UP)
                    .toLong()
            require(nanos < 86400000000000L) { "XLSX时间数值无效" }
            return LocalTime.ofNanoOfDay(nanos).toString()
        }
        require(serial.compareTo(BigDecimal(whole)) == 0) { "XLSX日期含有时间，请拆分日期和时间列" }
        val day =
            if (date1904) LocalDate.of(1904, 1, 1).plusDays(whole)
            else LocalDate.of(1899, 12, 30).plusDays(whole)
        require(day.year in 1970..2200) { "XLSX日期超出1970至2200范围" }
        return day.toString()
    }

    private fun relationshipBase(path: String): String =
        path.substringBeforeLast("/_rels/", "").let { if (path == "_rels/.rels") "" else it }

    private fun resolve(base: String, target: String?): String {
        require(
            !target.isNullOrBlank() &&
                !target.contains('\\') &&
                !target.contains(':') &&
                !target.contains('?') &&
                !target.contains('#') &&
                !target.contains('%')
        ) {
            "XLSX关系路径无效"
        }
        val parts = mutableListOf<String>()
        val path =
            if (target.startsWith('/')) target.drop(1)
            else if (base.isEmpty()) target else "$base/$target"
        path.split('/').forEach { part ->
            when (part) {
                "",
                "." -> Unit
                ".." -> {
                    require(parts.isNotEmpty()) { "XLSX关系路径越界" }
                    parts.removeAt(parts.lastIndex)
                }
                else -> parts.add(part)
            }
        }
        require(parts.isNotEmpty()) { "XLSX关系路径无效" }
        return parts.joinToString("/")
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        require(bytes.size <= MAX_BYTES) { "XLSX超过16MB限制" }
        val files = linkedMapOf<String, ByteArray>()
        var total = 0
        var entries = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 256) { "XLSX压缩包文件数量超过限制" }
                val name = entry.name
                require(
                    !name.startsWith('/') &&
                        !name.contains('\\') &&
                        name.split('/').none { it == ".." || it == "." } &&
                        !name.contains(':')
                ) {
                    "XLSX压缩包路径无效"
                }
                if (!entry.isDirectory) {
                    require(!files.containsKey(name)) { "XLSX压缩包文件重复" }
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_BYTES) { "XLSX解压内容超过16MB限制" }
                        out.write(buffer, 0, count)
                    }
                    require(
                        entry.compressedSize <= 0 ||
                            out.size().toLong() <= entry.compressedSize * 2000L
                    ) {
                        "XLSX压缩比例超过限制"
                    }
                    files[name] = out.toByteArray()
                }
                zip.closeEntry()
            }
        }
        return files
    }

    private fun parse(bytes: ByteArray, visit: (XmlPullParser) -> Unit) {
        val parser =
            XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        parser.setInput(ByteArrayInputStream(bytes), null)
        var depth = 0
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            require(
                parser.eventType != XmlPullParser.DOCDECL &&
                    (parser.eventType != XmlPullParser.ENTITY_REF ||
                        (parser.name in listOf("amp", "lt", "gt", "quot", "apos") ||
                            parser.name?.startsWith("#") == true))
            ) {
                "XLSX不允许DTD或实体引用"
            }
            if (parser.eventType == XmlPullParser.START_TAG)
                require(++depth <= 32) { "XLSX XML嵌套过深" }
            visit(parser)
            if (parser.eventType == XmlPullParser.END_TAG) depth--
            parser.nextToken()
        }
    }
}
