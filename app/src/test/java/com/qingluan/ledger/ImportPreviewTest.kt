package com.qingluan.ledger

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportPreviewTest {
    private val header = "日期,时间,类型,分类,币种,金额,备注\r\n"

    @Test
    fun rowErrorsKeepPhysicalLineAndStrictDecodeRefusesPartialData() {
        val raw = header + "2026-11-10,12:00:00,支出,餐饮,CNY,1.00,\"跨行\r\n备注\"\r\n" +
            "2026-02-30,12:00:00,支出,餐饮,CNY,2.00,无效\r\n" +
            "2026-11-10,12:00:00,支出,餐饮,CNY,3.00,有效"
        val preview = decodeTransfer(raw, "csv", charArrayOf())
        assertEquals(3, preview.totalRows)
        assertEquals(2, preview.bills.size)
        assertEquals(listOf(CsvRowError(4, "日期或时间无效")), preview.errors)
        assertTrue(runCatching { CsvImport.decode(raw) }.isFailure)
    }

    @Test
    fun structuralCsvFailuresRejectWholePreview() {
        listOf(
            header + "2026-11-10,12:00:00,支出,餐饮,CNY,1.00,\"未闭合",
            header + "2026-11-10,12:00:00,支出,餐饮,CNY,1.00,ab\"cd",
            header.replace("备注", "币种"),
            header.replace("金额", "未知"),
        ).forEach { assertTrue(runCatching { CsvImport.preview(it) }.isFailure) }
    }

    @Test
    fun errorReasonsDoNotEchoReceiptOrRawValues() {
        val raw = header.replace("备注", "票据") +
            "2026-11-10,12:00:00,支出,餐饮,CNY,1.00,PRIVATE_RECEIPT_PAYLOAD"
        val preview = CsvImport.preview(raw)
        assertEquals("票据图片无效", preview.errors.single().reason)
        assertFalse(preview.errors.toString().contains("PRIVATE_RECEIPT"))
    }

    @Test
    fun countsMatchApplyWithMultiplicityFiltersAndRangeReplacement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "preview-${System.nanoTime()}.db"
        val repo = Repository(context, name)
        try {
            repo.seed()
            val category = repo.dao.allCategories().first { it.type == "EXPENSE" }
            val day = LocalDate.of(2026, 11, 10)
            val stamp = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val original = Entry(amount = 100, categoryId = category.id, occurredAt = stamp)
            repo.save(original)
            val bill = CsvBill("EXPENSE", 100, category.name, "CNY", stamp, "")
            val outside = bill.copy(occurredAt = stamp - 86400000L)
            val data = ImportPreview(listOf(bill, bill, outside), null, "csv", listOf(CsvRowError(5, "币种无效")))
            val range = TransferRange(day, day)
            val cats = repo.dao.allCategories()
            val local = repo.dao.allEntries()
            val counts = importCounts(data, cats, local, range, false)
            assertEquals(ImportCounts(4, 3, 1, 1, 1, 1), counts)
            assertEquals(ImportCounts(4, 3, 2, 0, 1, 1), importCounts(data, cats, local, range, true))
            val result = CsvImport.apply(repo, listOf(bill, bill), true)
            assertEquals(counts.new, result.added)
            assertEquals(counts.duplicates, result.skipped)
            assertEquals(listOf(true, true, false), CsvImport.duplicateFlags(cats, repo.dao.allEntries(), listOf(bill, bill, bill)))
        } finally {
            repo.db.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun strictJsonRejectsExtensionsDuplicateKeysAndNesting() {
        validateTransferJson("{\"a\":[1,true,null,\"文本\"]}")
        listOf(
            "{} trailing", "{'a':1}", "{\"a\":1,}", "{\"a\":1,\"a\":2}",
            "{\"a\":NaN}", "{\"a\":/*comment*/1}", "[1]",
            "{\"a\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}",
        ).forEach { assertTrue(it, runCatching { validateTransferJson(it) }.isFailure) }
    }
}
