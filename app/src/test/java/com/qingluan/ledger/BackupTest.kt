package com.qingluan.ledger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val categories = listOf(Category(1, "餐饮", "EXPENSE"), Category(2, "工资", "INCOME"))

    @Test
    fun expandedIconsKeepAllLegacyKeysAndRoundTrip() {
        val old =
            listOf(
                "food",
                "bag",
                "car",
                "home",
                "circle",
                "health",
                "fun",
                "travel",
                "book",
                "gift",
                "work",
                "money",
            )
        assertEquals(old, Repository.icons.take(old.size))
        assertEquals(40, Repository.icons.toSet().size)
        val updated = categories.map { if (it.id == 1L) it.copy(iconKey = "delivery") else it }
        assertEquals(updated, Backup.decode(Backup.encode(updated, emptyList())).first)
    }

    @Test
    fun roundTripPreservesCentsAndIds() {
        val entries = listOf(Entry(5, "EXPENSE", 123, 1, note = "午饭\n备注"))
        val (cs, es) = Backup.decode(Backup.encode(categories, entries))
        assertEquals(categories, cs)
        assertEquals(entries, es)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenReference() {
        Backup.decode(
            Backup.encode(categories, listOf(Entry(amount = 10, categoryId = 99, id = 1)))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveAmount() {
        Backup.decode(Backup.encode(categories, listOf(Entry(amount = 0, categoryId = 1, id = 1))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownVersion() {
        Backup.decode(
            Backup.encode(categories, emptyList())
                .replace("\"formatVersion\": 2", "\"formatVersion\": 99")
        )
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsFractionalCents() {
        val raw = Backup.encode(categories, listOf(Entry(id = 1, amount = 123, categoryId = 1)))
        Backup.decode(raw.replace("\"amount\": 123", "\"amount\": 1.9"))
    }

    @Test
    fun mixedCurrencyRoundTripPreservesMinorUnits() {
        val entries =
            listOf(
                Entry(id = 1, amount = Long.MAX_VALUE, categoryId = 1, currencyCode = "JPY"),
                Entry(id = 2, amount = 1234, categoryId = 1, currencyCode = "KWD"),
                Entry(id = 3, amount = 12345, categoryId = 1, currencyCode = "CLF"),
            )
        val raw = Backup.encode(categories, entries)
        assertEquals(2, JSONObject(raw).getInt("formatVersion"))
        assertEquals("MULTI", JSONObject(raw).getString("currency"))
        assertEquals(entries, Backup.decode(raw).second)
    }

    @Test
    fun legacyCnyBackupImportsUnchanged() {
        val entry = Entry(id = 1, amount = 123, categoryId = 1)
        val legacy =
            JSONObject(Backup.encode(categories, listOf(entry)))
                .put("formatVersion", 1)
                .put("currency", "CNY")
        legacy.getJSONArray("transactions").getJSONObject(0).remove("currencyCode")
        assertEquals(entry, Backup.decode(legacy.toString()).second.single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownCurrency() {
        Backup.decode(
            Backup.encode(
                categories,
                listOf(Entry(id = 1, amount = 123, categoryId = 1, currencyCode = "INVALID")),
            )
        )
    }

    @Test(expected = ArithmeticException::class)
    fun rejectsPerCurrencyOverflow() {
        Backup.decode(
            Backup.encode(
                categories,
                listOf(
                    Entry(id = 1, amount = Long.MAX_VALUE, categoryId = 1, currencyCode = "KWD"),
                    Entry(id = 2, amount = 1, categoryId = 1, currencyCode = "KWD"),
                ),
            )
        )
    }

    @Test
    fun emptyStatsAreZero() {
        assertEquals(Totals(0, 0), totals(emptyList()))
    }

    @Test
    fun zeroAndOverflowRejected() {
        assertNull(Money.parse("0.00"))
        assertNull(Money.parse("92233720368547758.08"))
        assertEquals(1L, Money.parse("0.01"))
    }
}
