package com.qingluan.ledger

import androidx.room.withTransaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

data class CsvBill(
    val type: String,
    val amount: Long,
    val category: String,
    val currency: String,
    val occurredAt: Long,
    val note: String,
    val tags: String = "",
    val receipt: String = "",
)

data class CsvImportResult(val added: Int, val skipped: Int)

object CsvImport {
    private fun dangerous(value: String) =
        value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
            value.startsWith('\t') ||
            value.startsWith('\r')

    private fun original(value: String, modern: Boolean): String =
        if (
            value.startsWith("'") &&
                (dangerous(value.drop(1)) || (modern && value.startsWith("''")))
        )
            value.drop(1)
        else value

    private fun parse(raw: String): List<List<String>> {
        require(raw.toByteArray(Charsets.UTF_8).size <= CompleteBackup.MAX_BYTES) { "CSV超过16MB限制" }
        val source = raw.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun fieldEnd() {
            row.add(field.toString())
            field.setLength(0)
            closed = false
        }
        fun rowEnd() {
            fieldEnd()
            if (row.any { it.isNotEmpty() }) {
                rows.add(row.toList())
                require(rows.size <= 100001) { "CSV记录数超过限制" }
            }
            row.clear()
        }
        while (i < source.length) {
            val c = source[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < source.length && source[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                        closed = true
                    }
                } else field.append(c)
            } else {
                when (c) {
                    ',' -> fieldEnd()
                    '\r',
                    '\n' -> {
                        rowEnd()
                        if (c == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                    }
                    '"' -> {
                        require(field.isEmpty() && !closed) { "CSV引号格式无效" }
                        quoted = true
                    }
                    else -> {
                        if (closed) require(c == ' ' || c == '\t') { "CSV引号后存在无效内容" }
                        else field.append(c)
                    }
                }
            }
            i++
        }
        require(!quoted) { "CSV引号未闭合" }
        if (field.isNotEmpty() || row.isNotEmpty() || closed) rowEnd()
        return rows
    }

    fun decode(raw: String): List<CsvBill> {
        val data = parse(raw)
        require(data.isNotEmpty()) { "CSV为空" }
        val modern = data.first().any { it.trim() == "日期（文本）" }
        val header = data.first().map { if (it.trim() == "日期（文本）") "日期" else it.trim() }
        val required = listOf("日期", "时间", "类型", "分类", "币种", "金额")
        val optional = listOf("备注", "标签", "票据")
        require(required.all { name -> header.count { it == name } == 1 }) { "CSV列名不匹配" }
        val indices = (required + optional).associateWith { header.indexOf(it) }
        val zone = ZoneId.systemDefault()
        val dateFormat =
            DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT)
        return data.drop(1).mapIndexed { index, cells ->
            try {
                require(cells.size == header.size) { "列数不匹配" }
                fun get(name: String) =
                    indices.getValue(name).takeIf { it >= 0 }?.let { cells[it] } ?: ""
                val type =
                    when (get("类型").trim().uppercase(Locale.ROOT)) {
                        "支出",
                        "EXPENSE" -> "EXPENSE"
                        "收入",
                        "INCOME" -> "INCOME"
                        else -> error("收支类型无效")
                    }
                val currency = get("币种").trim().uppercase(Locale.ROOT)
                require(Currencies.contains(currency)) { "币种无效" }
                val amount = Money.parse(get("金额").trim(), currency) ?: error("金额或精度无效")
                var date = get("日期").trim().removePrefix("'")
                if (date.startsWith("=\"") && date.endsWith("\""))
                    date = date.substring(2, date.length - 1)
                val day = LocalDate.parse(date.replace('/', '-'), dateFormat)
                val time = LocalTime.parse(get("时间").trim().removePrefix("'"))
                val category = original(get("分类"), modern)
                require(category.isNotBlank()) { "分类不能为空" }
                validateBillExtras(original(get("标签"), modern), get("票据"))
                CsvBill(
                    type,
                    amount,
                    category,
                    currency,
                    day.atTime(time).atZone(zone).toInstant().toEpochMilli(),
                    original(get("备注"), modern),
                    original(get("标签"), modern),
                    get("票据"),
                )
            } catch (e: Exception) {
                throw IllegalArgumentException("CSV第${index+2}行：${e.message ?: "数据无效"}", e)
            }
        }
    }

    private data class Key(
        val type: String,
        val amount: Long,
        val category: Long,
        val currency: String,
        val second: Long,
        val note: String,
        val tags: String,
        val receipt: String,
    )

    suspend fun apply(
        repository: Repository,
        bills: List<CsvBill>,
        skipDuplicates: Boolean = true,
    ): CsvImportResult =
        repository.db.withTransaction {
            val categories = repository.dao.allCategories().toMutableList()
            val existing = repository.dao.allEntries()
            val counts =
                existing
                    .groupingBy {
                        Key(
                            it.type,
                            it.amount,
                            it.categoryId,
                            it.currencyCode,
                            Math.floorDiv(it.occurredAt, 1000L),
                            it.note,
                            it.tags,
                            it.receipt,
                        )
                    }
                    .eachCount()
            val incoming = mutableMapOf<Key, Int>()
            val sums =
                existing.groupBy { it.currencyCode }.mapValues { totals(it.value) }.toMutableMap()
            var added = 0
            var skipped = 0
            bills.forEach { bill ->
                var category = categories.find { it.type == bill.type && it.name == bill.category }
                if (category == null) {
                    require(categories.size < 5000) { "分类数量超过限制" }
                    val next =
                        Category(
                            name = bill.category,
                            type = bill.type,
                            sortOrder =
                                (categories
                                    .filter { it.type == bill.type }
                                    .maxOfOrNull { it.sortOrder } ?: -1) + 1,
                        )
                    category = next.copy(id = repository.dao.add(next))
                    categories.add(category)
                }
                val key =
                    Key(
                        bill.type,
                        bill.amount,
                        category.id,
                        bill.currency,
                        Math.floorDiv(bill.occurredAt, 1000L),
                        bill.note,
                        bill.tags,
                        bill.receipt,
                    )
                val ordinal = (incoming[key] ?: 0) + 1
                incoming[key] = ordinal
                if (skipDuplicates && (counts[key] ?: 0) >= ordinal) skipped++
                else {
                    val total = sums[bill.currency] ?: Totals(0, 0)
                    sums[bill.currency] =
                        if (bill.type == "INCOME")
                            total.copy(income = Math.addExact(total.income, bill.amount))
                        else total.copy(expense = Math.addExact(total.expense, bill.amount))
                    repository.dao.insert(
                        Entry(
                            type = bill.type,
                            amount = bill.amount,
                            categoryId = category.id,
                            occurredAt = bill.occurredAt,
                            note = bill.note,
                            tags = bill.tags,
                            receipt = bill.receipt,
                            currencyCode = bill.currency,
                        )
                    )
                    require(existing.size + added + 1 <= 100000) { "账单数量超过限制" }
                    added++
                }
            }
            CsvImportResult(added, skipped)
        }
}
