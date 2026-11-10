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

data class ImportPreview(val bills: List<CsvBill>, val fullJson: String?, val kind: String) {
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
    val plaintext = if (kind == "encrypted") BackupCipher.decrypt(raw, password) else raw
    return if (kind == "csv") ImportPreview(CsvImport.decode(plaintext), null, kind)
    else {
        val snapshot = CompleteBackup.decode(plaintext)
        ImportPreview(snapshotBills(snapshot), plaintext, kind)
    }
}
