package com.qingluan.ledger

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExport {
    private fun cell(value: String, protect: Boolean = true): String {
        // Quoting alone does not stop spreadsheet formula execution.
        val text =
            if (
                protect &&
                    (value.startsWith("'") ||
                        value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
                        value.startsWith('\t') ||
                        value.startsWith('\r'))
            )
                "'" + value
            else value
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    fun encode(
        categories: List<Category>,
        entries: List<Entry>,
        includeNote: Boolean = true,
        includeTags: Boolean = false,
        includeReceipt: Boolean = false,
    ): String {
        val names = categories.associate { it.id to it.name }
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val time = DateTimeFormatter.ofPattern("HH:mm:ss")
        val zone = ZoneId.systemDefault()
        return buildString {
            append('\uFEFF')
            append(
                    (listOf("日期（文本）", "时间", "类型", "分类", "币种", "金额") +
                            (if (includeNote) listOf("备注") else emptyList()) +
                            (if (includeTags) listOf("标签") else emptyList()) +
                            (if (includeReceipt) listOf("票据") else emptyList()))
                        .joinToString(",", transform = { cell(it) })
                )
                .append("\r\n")
            entries
                .sortedWith(compareByDescending<Entry> { it.occurredAt }.thenByDescending { it.id })
                .forEach { e ->
                    val local = Instant.ofEpochMilli(e.occurredAt).atZone(zone)
                    append(
                            listOf(
                                    "'" + local.format(date),
                                    local.format(time),
                                    if (e.type == "INCOME") "收入" else "支出",
                                    names[e.categoryId] ?: "",
                                    e.currencyCode,
                                    Money.text(e.amount, e.currencyCode),
                                )
                                .let {
                                    it +
                                        (if (includeNote) listOf(e.note) else emptyList()) +
                                        (if (includeTags) listOf(e.tags) else emptyList()) +
                                        (if (includeReceipt) listOf(e.receipt) else emptyList())
                                }
                                .mapIndexed { index, value -> cell(value, protect = index != 0) }
                                .joinToString(",")
                        )
                        .append("\r\n")
                }
        }
    }
}
