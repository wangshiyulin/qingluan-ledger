package com.qingluan.ledger

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    @Test
    fun balanceTrendUsesSignedNetAmountsAndKeepsEmptyDays() {
        val d = LocalDate.of(2026, 11, 1)
        fun at(day: Int) =
            d.plusDays(day.toLong())
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val rows =
            listOf(
                Entry(type = "INCOME", amount = 10000, categoryId = 1, occurredAt = at(0)),
                Entry(amount = 3000, categoryId = 2, occurredAt = at(0)),
                Entry(amount = 9000, categoryId = 2, occurredAt = at(1)),
            )
        val buckets = trendBuckets(rows, DateSpan(d, d.plusDays(3)), balance = true)
        assertEquals(listOf(7000L, -9000L, 0L), buckets.map { it.second })
        assertEquals(totals(rows).balance, buckets.sumOf { it.second })
        val months = trendBuckets(rows, DateSpan.year(d), balance = true)
        assertEquals(-2000L, months.first { it.first.monthValue == 11 }.second)
        assertEquals(-2000L, dailyMean(-6000L, 3))
        assertEquals(
            listOf(13000L, 9000L, 0L),
            trendBuckets(rows, DateSpan(d, d.plusDays(3))).map { it.second },
        )
    }

    @Test
    fun moneyIsExact() {
        assertEquals(123L, Money.parse("1.23"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1.234"))
    }

    @Test
    fun currencyPrecisionAndBoundsAreExact() {
        assertEquals(178, Currencies.all.size)
        assertEquals(Currencies.all.size, Currencies.all.map { it.code }.toSet().size)
        assertEquals(123L, Money.parse("123", "JPY"))
        assertEquals("123", Money.text(123, "JPY"))
        assertNull(Money.parse("123.0", "JPY"))
        assertEquals(1234L, Money.parse("1.234", "KWD"))
        assertEquals("1.234", Money.text(1234, "KWD"))
        assertNull(Money.parse("1.2345", "KWD"))
        assertEquals(12345L, Money.parse("1.2345", "CLF"))
        assertEquals("1.2345", Money.text(12345, "CLF"))
        assertNull(Money.parse("1.23456", "CLF"))
        assertEquals(Long.MAX_VALUE, Money.parse("9223372036854775.807", "KWD"))
        assertNull(Money.parse("9223372036854775.808", "KWD"))
        assertNull(Money.parse("1", "INVALID"))
        assertEquals(2, Currencies.get("XAU").digits)
    }

    @Test(expected = IllegalArgumentException::class)
    fun totalsRejectMixedCurrencies() {
        totals(
            listOf(
                Entry(amount = 100, categoryId = 1),
                Entry(amount = 100, categoryId = 1, currencyCode = "JPY"),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun catalogRejectsUnknownCurrency() {
        Currencies.get("INVALID")
    }

    @Test
    fun leapMonthBounds() {
        val r = DateSpan.month(LocalDate.of(2024, 2, 15))
        assertEquals(29L, r.days)
        assertEquals(LocalDate.of(2024, 3, 1), r.end)
    }

    @Test
    fun profileCountsAndBalancesStaySeparated() {
        val now = System.currentTimeMillis()
        val entries =
            listOf(
                Entry(amount = 100, categoryId = 1, occurredAt = now),
                Entry(amount = 200, categoryId = 1, occurredAt = now, currencyCode = "USD"),
            )
        val stats = profileStats(entries)
        assertEquals(1, stats.days)
        assertEquals(2, stats.count)
        assertEquals(mapOf("CNY" to -100L, "USD" to -200L), stats.balances)
        assertEquals(0, profileStats(emptyList()).days)
    }

    @Test
    fun totalsAreExact() {
        val ts =
            listOf(
                Entry(type = "EXPENSE", amount = 123, categoryId = 1),
                Entry(type = "INCOME", amount = 500, categoryId = 2),
            )
        assertEquals(377L, totals(ts).balance)
    }

    @Test
    fun dailyMeanAndBucketsStayExact() {
        assertEquals(34L, dailyMean(101, 3))
        val span = DateSpan(LocalDate.of(2024, 12, 1), LocalDate.of(2025, 4, 1))
        val stamp =
            LocalDate.of(2025, 1, 10)
                .atStartOfDay(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val points =
            trendBuckets(listOf(Entry(amount = 123, categoryId = 1, occurredAt = stamp)), span)
        assertEquals(4, points.size)
        assertEquals(123L, points.sumOf { it.second })
        assertEquals(123L, points[1].second)
    }

    @Test
    fun largeRangesHaveBoundedTrendBuckets() {
        val span = DateSpan(LocalDate.of(1000, 1, 1), LocalDate.of(3000, 1, 1))
        assertTrue(trendBuckets(emptyList(), span).size <= 120)
    }

    @Test
    fun currentMonthTrendStopsAtToday() {
        val span = DateSpan.month(LocalDate.of(2026, 11, 9))
        val shown = visibleTrendSpan(span, LocalDate.of(2026, 11, 9))
        val points = trendBuckets(emptyList(), shown)
        assertEquals(9, points.size)
        assertEquals(LocalDate.of(2026, 11, 1), points.first().first)
        assertEquals(LocalDate.of(2026, 11, 9), points.last().first)
        assertTrue(
            trendBuckets(
                    emptyList(),
                    visibleTrendSpan(
                        DateSpan.month(LocalDate.of(2026, 12, 1)),
                        LocalDate.of(2026, 11, 9),
                    ),
                )
                .isEmpty()
        )
    }

    @Test
    fun pastMonthAndCurrentYearKeepExpectedBuckets() {
        assertEquals(
            31,
            trendBuckets(
                    emptyList(),
                    visibleTrendSpan(
                        DateSpan.month(LocalDate.of(2026, 10, 1)),
                        LocalDate.of(2026, 11, 9),
                    ),
                )
                .size,
        )
        assertEquals(
            2,
            trendBuckets(
                    emptyList(),
                    visibleTrendSpan(
                        DateSpan.year(LocalDate.of(2026, 2, 9)),
                        LocalDate.of(2026, 2, 9),
                    ),
                )
                .size,
        )
        assertNotEquals(categoryColor(0), categoryColor(1))
    }
}
