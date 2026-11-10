package com.qingluan.ledger

import android.graphics.Bitmap
import android.util.Base64
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureBatchTest {
    private lateinit var repository: Repository
    private lateinit var name: String

    @Before
    fun setup() = runBlocking {
        name = "feature-${UUID.randomUUID()}.db"
        repository = Repository(RuntimeEnvironment.getApplication(), name)
        repository.seed()
    }

    @After
    fun close() {
        repository.db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(name)
    }

    @Test
    fun recycleRestoreRecreatesCategoryAndPreservesReceipt() = runBlocking {
        val category = repository.dao.allCategories().first { it.type == "EXPENSE" }
        val out = java.io.ByteArrayOutputStream()
        Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            compress(Bitmap.CompressFormat.PNG, 100, out)
            recycle()
        }
        val receipt = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        val original =
            Entry(
                amount = 1234,
                categoryId = category.id,
                note = "备注",
                tags = "历史标签",
                receipt = receipt,
            )
        repository.save(original)
        val id = repository.dao.allEntries().single().id
        val removed = repository.recycle(setOf(id))
        assertEquals(1, removed.size)
        assertTrue(repository.dao.allEntries().isEmpty())
        repository.dao.deleteCategory(category.id)
        repository.restoreTrash(removed.toSet())
        val restored = repository.dao.allEntries().single()
        assertEquals(receipt, restored.receipt)
        assertEquals("备注", restored.note)
        assertEquals("历史标签", restored.tags)
        assertEquals(1234, restored.amount)
        assertTrue(repository.dao.allTrash().isEmpty())
        assertEquals(
            category.name,
            repository.dao.allCategories().first { it.id == restored.categoryId }.name,
        )
    }

    @Test
    fun recurringMonthEndAndDuplicateConfirmationAreSafe() = runBlocking {
        val category = repository.dao.allCategories().first { it.type == "EXPENSE" }
        val rule =
            RecurringBill(
                name = "房租",
                amount = 100,
                categoryId = category.id,
                nextDate = "2024-01-31",
                frequency = "MONTH",
            )
        assertEquals(LocalDate.of(2024, 2, 29), nextRecurringDate(rule))
        assertEquals(
            LocalDate.of(2024, 3, 31),
            nextRecurringDate(rule.copy(nextDate = "2024-02-29")),
        )
        val yearly =
            rule.copy(nextDate = "2024-02-29", frequency = "YEAR", anchorDay = 29, anchorMonth = 2)
        assertEquals(LocalDate.of(2025, 2, 28), nextRecurringDate(yearly))
        repository.saveRecurring(rule)
        val stored = repository.dao.allRecurring().single()
        repository.advanceRecurring(stored.id, stored.nextDate, true)
        assertEquals(1, repository.dao.allEntries().size)
        assertTrue(
            runCatching { repository.advanceRecurring(stored.id, stored.nextDate, true) }.isFailure
        )
        val updated = repository.dao.allRecurring().single()
        repository.advanceRecurring(updated.id, updated.nextDate, false)
        assertEquals(1, repository.dao.allEntries().size)
        assertEquals("2024-03-31", repository.dao.allRecurring().single().nextDate)
    }

    @Test
    fun completeBackupRoundTripsArchiveRulesAndLegacy() = runBlocking {
        val category = repository.dao.allCategories().first { it.type == "EXPENSE" }
        repository.save(Entry(amount = 100, categoryId = category.id))
        repository.recycle(repository.dao.allEntries().map { it.id }.toSet())
        repository.saveRecurring(RecurringBill(name = "订阅", amount = 700, categoryId = category.id))
        val personal =
            PersonalBackup(
                searchPresets =
                    JSONObject()
                        .put("固定筛选", BillSearch(currency = "JPY", minimum = "1").json())
                        .toString()
            )
        val raw =
            CompleteBackup.encode(
                repository.dao.allCategories(),
                repository.dao.allEntries(),
                personal,
                repository.dao.allTrash(),
                repository.dao.allRecurring(),
            )
        val decoded = CompleteBackup.decode(raw)
        assertEquals(1, decoded.trash.size)
        assertEquals(1, decoded.recurring.size)
        assertEquals(1, searchPresets(decoded.personal!!.searchPresets).size)
        val manager = LocalDataManager(RuntimeEnvironment.getApplication(), repository)
        manager.restore(raw)
        val again = CompleteBackup.decode(manager.export())
        assertEquals(1, again.trash.size)
        assertEquals(1, again.recurring.size)
        val legacy =
            JSONObject(raw).put("formatVersion", 6).apply {
                remove("trash")
                remove("recurring")
            }
        val old = CompleteBackup.decode(legacy.toString())
        assertTrue(old.trash.isEmpty())
        assertTrue(old.recurring.isEmpty())
        assertTrue(
            runCatching { CompleteBackup.decode(raw.dropLast(1) + ",\"extra\":1} trailing") }
                .isFailure
        )
    }

    @Test
    fun advancedSearchUsesOriginalCurrencyUnitsAndPresets() {
        val yen = Entry(amount = 200, categoryId = 1, currencyCode = "JPY", note = "午饭")
        val cny = Entry(amount = 200, categoryId = 1, currencyCode = "CNY", note = "午饭")
        val filter = BillSearch(minimum = "100", maximum = "300")
        filter.validate()
        assertTrue(filter.matches(yen, null))
        assertFalse(filter.matches(cny, null))
        assertFalse(filter.copy(receipt = "yes").matches(yen, null))
        assertTrue(filter.copy(keyword = "午饭").matches(yen, null))
        assertFalse(filter.copy(currency = "CNY").matches(yen, null))
        assertEquals(filter, BillSearch.decode(filter.json()))
        assertTrue(runCatching { BillSearch(minimum = "20", maximum = "1").validate() }.isFailure)
    }
}
