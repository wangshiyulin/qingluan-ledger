package com.qingluan.ledger

import androidx.datastore.preferences.core.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CsvImportTest {
    private lateinit var repo: Repository
    private lateinit var manager: LocalDataManager
    private lateinit var name: String
    private val context
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setup() = runBlocking {
        File(context.filesDir, "restore-journal.json").delete()
        context.preferences.edit { it[stringPreferencesKey("nickname")] = "保留资料" }
        name = "csv-${System.nanoTime()}.db"
        repo = Repository(context, name)
        repo.seed()
        manager = LocalDataManager(context, repo)
    }

    @After
    fun close() {
        repo.db.close()
        context.deleteDatabase(name)
    }

    @Test
    fun ownExportRoundTripsAndRepeatedImportUsesMultiplicity() = runBlocking {
        val categories = repo.dao.allCategories()
        val food = categories.first { it.type == "EXPENSE" }
        val income = categories.first { it.type == "INCOME" }
        val extra = Category(id = 999, name = "=自定义", type = "EXPENSE")
        val stamp = 1700000000123L
        val first =
            Entry(amount = 8000, categoryId = food.id, occurredAt = stamp, note = "备注,\"引号\"\n下一行")
        val rows =
            listOf(
                first,
                first.copy(id = 2),
                Entry(
                    amount = 1234,
                    categoryId = extra.id,
                    currencyCode = "KWD",
                    occurredAt = stamp,
                    note = "'=字面",
                ),
                Entry(
                    type = "INCOME",
                    amount = 123,
                    categoryId = income.id,
                    currencyCode = "JPY",
                    occurredAt = stamp,
                    note = "=1+1",
                ),
            )
        val csv = CsvExport.encode(categories + extra, rows)
        assertTrue(csv.contains("日期（文本）"))
        assertTrue(csv.contains("\"'2023-"))
        val result = manager.importCsv(csv)
        assertEquals(CsvImportResult(4, 0), result)
        assertEquals(CsvImportResult(0, 4), manager.importCsv(csv))
        val imported = repo.dao.allEntries()
        assertEquals(2, imported.count { it.note == first.note })
        assertEquals(1234L, imported.single { it.currencyCode == "KWD" }.amount)
        assertEquals("'=字面", imported.single { it.currencyCode == "KWD" }.note)
        assertEquals("=1+1", imported.single { it.currencyCode == "JPY" }.note)
        assertTrue(repo.dao.allCategories().any { it.name == "=自定义" })
        assertEquals("保留资料", context.preferences.data.first()[stringPreferencesKey("nickname")])
    }

    @Test
    fun legacyDatesAndInvalidRowsAreValidatedBeforeWriting() = runBlocking {
        val header = "日期,时间,类型,分类,币种,金额,备注\r\n"
        val row = "2026-11-10,12:13:14,支出,旧分类,CNY,1.23,'=2+2\r\n"
        assertEquals("=2+2", CsvImport.decode(header + row).single().note)
        val initial = repo.dao.allCategories().size
        val bad = header + row + "2026-02-30,12:00:00,支出,不应创建,CNY,1.00,无效\r\n"
        assertTrue(runCatching { manager.importCsv(bad) }.isFailure)
        assertTrue(repo.dao.allEntries().isEmpty())
        assertEquals(initial, repo.dao.allCategories().size)
        assertEquals(CsvImportResult(1, 0), manager.importCsv(header + row))
    }

    @Test
    fun overflowRollsBackNewCategoriesAndBills() = runBlocking {
        val category = repo.dao.allCategories().first { it.type == "EXPENSE" }
        repo.save(Entry(amount = Long.MAX_VALUE, categoryId = category.id, currencyCode = "JPY"))
        val initial = repo.dao.allCategories().size
        val extra = Category(id = 999, name = "回滚分类", type = "EXPENSE")
        val csv =
            CsvExport.encode(
                listOf(extra),
                listOf(Entry(amount = 1, categoryId = 999, currencyCode = "JPY", note = "额外")),
            )
        assertTrue(runCatching { manager.importCsv(csv) }.isFailure)
        assertEquals(Long.MAX_VALUE, repo.dao.allEntries().single().amount)
        assertEquals(initial, repo.dao.allCategories().size)
    }
}
