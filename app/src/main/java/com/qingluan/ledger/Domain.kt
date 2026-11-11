package com.qingluan.ledger

import androidx.room.*
import java.math.BigDecimal
import java.time.*

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val iconKey: String = "circle",
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
)

@Entity(
    tableName = "transactions",
    foreignKeys =
        [
            ForeignKey(
                entity = Category::class,
                parentColumns = ["id"],
                childColumns = ["categoryId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index("categoryId"), Index("occurredAt")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = "EXPENSE",
    val amount: Long,
    val categoryId: Long,
    val occurredAt: Long = System.currentTimeMillis(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'CNY'") val currencyCode: String = "CNY",
    @ColumnInfo(defaultValue = "''") val tags: String = "",
    @ColumnInfo(defaultValue = "''") val receipt: String = "",
)

object Money {
    fun parse(s: String, currencyCode: String = "CNY"): Long? =
        try {
            val digits = Currencies.get(currencyCode).digits
            val pattern = if (digits == 0) "[0-9]+" else "[0-9]+(\\.[0-9]{0,$digits})?"
            if (!s.matches(Regex(pattern))) null
            else BigDecimal(s).movePointRight(digits).longValueExact().takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }

    fun text(amount: Long, currencyCode: String = "CNY"): String =
        BigDecimal.valueOf(amount, Currencies.get(currencyCode).digits).toPlainString()
}

data class Totals(val income: Long, val expense: Long) {
    val balance
        get() = income - expense
}

fun totals(rows: List<Entry>): Totals {
    var code: String? = null
    var income = 0L
    var expense = 0L
    for (row in rows) {
        require(code == null || code == row.currencyCode) { "不同币种不能合计" }
        code = row.currencyCode
        if (row.type == "INCOME") income = Math.addExact(income, row.amount)
        else if (row.type == "EXPENSE") expense = Math.addExact(expense, row.amount)
    }
    return Totals(income, expense)
}

fun Entry.date(): LocalDate =
    Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()

data class DateSpan(val start: LocalDate, val end: LocalDate, val unit: String = "custom") {
    val days
        get() = java.time.temporal.ChronoUnit.DAYS.between(start, end)

    fun contains(e: Entry): Boolean {
        val date = e.date()
        return !date.isBefore(start) && date.isBefore(end)
    }

    companion object {
        fun month(d: LocalDate) =
            DateSpan(d.withDayOfMonth(1), d.withDayOfMonth(1).plusMonths(1), "month")

        fun year(d: LocalDate) =
            DateSpan(d.withDayOfYear(1), d.withDayOfYear(1).plusYears(1), "year")
    }
}

fun dailyMean(cents: Long, days: Long): Long =
    BigDecimal.valueOf(cents)
        .divide(BigDecimal.valueOf(days.coerceAtLeast(1)), 0, java.math.RoundingMode.HALF_UP)
        .longValueExact()

fun trendBuckets(
    rows: List<Entry>,
    span: DateSpan,
    balance: Boolean = false,
): List<Pair<LocalDate, Long>> {
    fun bucketTotal(entries: List<Entry>) =
        if (balance) totals(entries).balance
        else entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.amount) }
    val filtered = rows.filter { span.contains(it) }
    require(filtered.map { it.currencyCode }.distinct().size <= 1) { "不同币种不能合计" }
    if (span.days > 36600) {
        val step = (span.days + 119) / 120
        val sums =
            filtered
                .groupBy {
                    java.time.temporal.ChronoUnit.DAYS.between(span.start, it.date()) / step
                }
                .mapValues { (_, list) -> bucketTotal(list) }
        return generateSequence(0L) { it + 1 }
            .takeWhile { it * step < span.days }
            .map { span.start.plusDays(it * step) to (sums[it] ?: 0L) }
            .toList()
    }
    val monthly = span.unit == "year" || span.days > 90
    val sums =
        filtered
            .groupBy { if (monthly) it.date().withDayOfMonth(1) else it.date() }
            .mapValues { (_, list) -> bucketTotal(list) }
    val start = if (monthly) span.start.withDayOfMonth(1) else span.start
    return generateSequence(start) { if (monthly) it.plusMonths(1) else it.plusDays(1) }
        .takeWhile { it < span.end }
        .map { it to (sums[it] ?: 0L) }
        .toList()
}

fun visibleTrendSpan(span: DateSpan, today: LocalDate): DateSpan {
    val end = minOf(span.end, today.plusDays(1))
    return DateSpan(span.start, maxOf(span.start, end), span.unit)
}

/** Empty periods still have a CNY zero summary. Never add different currencies together. */
fun currencyGroups(rows: List<Entry>, emptyCurrency: String = "CNY"): Map<String, List<Entry>> =
    if (rows.isEmpty()) linkedMapOf(emptyCurrency to emptyList())
    else rows.groupBy { it.currencyCode }.toSortedMap()

data class ProfileStats(val days: Int, val count: Int, val balances: Map<String, Long>)

fun profileStats(rows: List<Entry>) =
    ProfileStats(
        rows.map { it.date() }.distinct().size,
        rows.size,
        currencyGroups(rows).mapValues { (_, entries) -> totals(entries).balance },
    )

fun resolveEntryTime(
    original: Long?,
    date: LocalDate,
    time: LocalTime,
    manual: Boolean,
    changed: Boolean,
    now: Long = System.currentTimeMillis(),
): Long =
    when {
        original != null && !changed -> original
        !manual -> now
        else -> date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

/** A recorded day is a local date with at least one bill; multiple bills count once. */
fun recordedDayMean(amount: Long, rows: List<Entry>): Long =
    dailyMean(amount, rows.map { it.date() }.distinct().size.toLong())

fun entrySubtitle(entry: Entry, display: Experience): String =
    buildList {
            if (display.showTime)
                add(
                    java.time.Instant.ofEpochMilli(entry.occurredAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                )
            if (display.showNote && entry.note.isNotBlank()) add(entry.note)
            if (display.showTags && entry.tags.isNotBlank()) add(entry.tags)
            if (entry.receipt.isNotBlank()) add("票据")
        }
        .joinToString(" · ")
