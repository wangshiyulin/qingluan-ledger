package com.qingluan.ledger

import java.time.LocalDate

/** Inclusive local date boundaries. IDs refer to the captured current/selected bills. */
data class TransferRange(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val type: String = "all",
    val currency: String = "all",
    val ids: Set<Long>? = null,
) {
    init {
        require(from == null || to == null || !from.isAfter(to)) { "开始日期不能晚于结束日期" }
    }

    fun contains(e: Entry) =
        (from == null || !e.date().isBefore(from)) &&
            (to == null || !e.date().isAfter(to)) &&
            (type == "all" || e.type == type) &&
            (currency == "all" || e.currencyCode == currency) &&
            (ids == null || e.id in ids)

    fun contains(b: CsvBill): Boolean =
        contains(
            Entry(
                type = b.type,
                amount = b.amount,
                categoryId = 0,
                occurredAt = b.occurredAt,
                currencyCode = b.currency,
            )
        )
}

fun snapshotBills(data: CompleteSnapshot): List<CsvBill> {
    val names = data.categories.associateBy { it.id }
    return data.entries.map {
        CsvBill(
            it.type,
            it.amount,
            names.getValue(it.categoryId).name,
            it.currencyCode,
            it.occurredAt,
            it.note,
            it.tags,
            it.receipt,
        )
    }
}

data class ImportPreview(
    val bills: List<CsvBill>,
    val fullJson: String?,
    val kind: String,
    val errors: List<CsvRowError> = emptyList(),
    val totalRows: Int = bills.size + errors.size,
) {
    val from
        get() =
            bills.minOfOrNull {
                java.time.Instant.ofEpochMilli(it.occurredAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }

    val to
        get() =
            bills.maxOfOrNull {
                java.time.Instant.ofEpochMilli(it.occurredAt)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            }
}

fun decodeTransfer(raw: String, kind: String, password: CharArray): ImportPreview {
    require(kind in setOf("csv", "json", "encrypted")) { "不支持的文件格式" }
    require(raw.toByteArray(Charsets.UTF_8).size <= CompleteBackup.MAX_BYTES) { "文件超过16MB限制" }
    val plaintext = if (kind == "encrypted") BackupCipher.decrypt(raw, password) else raw
    return if (kind == "csv") {
        val csv = CsvImport.preview(plaintext)
        ImportPreview(csv.bills, null, kind, csv.errors, csv.totalRows)
    } else {
        validateTransferJson(plaintext)
        val snapshot = CompleteBackup.decode(plaintext)
        ImportPreview(snapshotBills(snapshot), plaintext, kind)
    }
}

/** Counts reflect the same ordered occurrence matching used by CsvImport.apply. */
data class ImportCounts(
    val total: Int,
    val valid: Int,
    val new: Int,
    val duplicates: Int,
    val invalid: Int,
    val excluded: Int,
) {
    fun importing(skipDuplicates: Boolean) = if (skipDuplicates) new else new + duplicates
}

fun importCounts(
    data: ImportPreview,
    categories: List<Category>,
    existing: List<Entry>,
    range: TransferRange,
    replace: Boolean,
): ImportCounts {
    val selected = data.bills.filter { range.contains(it) }
    val remaining = if (replace) existing.filterNot { range.contains(it) } else existing
    val duplicates = CsvImport.duplicateFlags(categories, remaining, selected).count { it }
    return ImportCounts(data.totalRows, data.bills.size, selected.size - duplicates, duplicates,
        data.errors.size, data.bills.size - selected.size)
}
