package com.qingluan.ledger

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ExportTimeTest {
    @Test
    fun csvKeepsPrecisionAndEscapesSpreadsheetCells() {
        val categories = listOf(Category(1, "=SUM(1,2)", "EXPENSE"))
        val csv =
            CsvExport.encode(
                categories,
                listOf(
                    Entry(
                        id = 1,
                        amount = 1234,
                        categoryId = 1,
                        currencyCode = "KWD",
                        note = "备注,\"引号\"\n下一行",
                    ),
                    Entry(id = 2, amount = 123, categoryId = 1, currencyCode = "JPY", note = "=1+1"),
                ),
            )
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"KWD\",\"1.234\""))
        assertTrue(csv.contains("\"JPY\",\"123\""))
        assertTrue(csv.contains("\"'=SUM(1,2)\""))
        assertTrue(csv.contains("\"'=1+1\""))
        assertTrue(csv.contains("\"备注,\"\"引号\"\"\n下一行\""))
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun systemTimeIsReadAtSaveAndEditingPreservesSeconds() {
        val date = LocalDate.of(2024, 2, 29)
        val time = LocalTime.of(23, 59)
        val now = 1700000000123L
        assertEquals(now, resolveEntryTime(null, date, time, false, false, now))
        assertEquals(123456789L, resolveEntryTime(123456789L, date, time, true, false, now))
        assertEquals(
            date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            resolveEntryTime(null, date, time, true, true, now),
        )
    }
}
