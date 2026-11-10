package com.qingluan.ledger

import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RepositoryTest {
    private lateinit var repo: Repository
    private lateinit var name: String

    @Before
    fun setup() = runBlocking {
        name = "test-${System.nanoTime()}.db"
        repo = Repository(RuntimeEnvironment.getApplication(), name)
        repo.seed()
    }

    @After
    fun close() {
        repo.db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(name)
    }

    @Test
    fun missingEditsAreRejectedAndIncomeDefaultsUseRelevantIcons() = runBlocking {
        val categories = repo.dao.allCategories()
        assertEquals("salary", categories.first { it.name == "工资" }.iconKey)
        assertEquals("refund", categories.first { it.name == "退款" }.iconKey)
        val category = categories.first { it.type == "EXPENSE" }
        assertTrue(
            runCatching { repo.save(Entry(id = 9999, amount = 123, categoryId = category.id)) }
                .isFailure
        )
        assertTrue(repo.dao.allEntries().isEmpty())
    }

    @Test
    fun referencedCategoryCannotChangeType() = runBlocking {
        val c = repo.dao.allCategories().first { it.type == "EXPENSE" }
        repo.save(Entry(amount = 123, categoryId = c.id))
        try {
            repo.category(c.copy(type = "INCOME"))
            fail("不能改变历史账单分类类型")
        } catch (_: IllegalArgumentException) {}
        assertEquals("EXPENSE", repo.dao.allCategories().first { it.id == c.id }.type)
    }

    @Test
    fun migrationAndInvalidRestoreKeepData() = runBlocking {
        val cs = repo.dao.allCategories().filter { it.type == "EXPENSE" }
        repo.save(Entry(amount = 123, categoryId = cs[0].id))
        try {
            repo.remove(cs[0], null)
            fail("迁移目标必填")
        } catch (_: IllegalArgumentException) {}
        assertEquals(cs[0].id, repo.dao.allEntries().single().categoryId)
        repo.remove(cs[0], cs[1].id)
        assertEquals(cs[1].id, repo.dao.allEntries().single().categoryId)
        val backup = repo.backup()
        try {
            repo.restore("{broken")
            fail("损坏备份必须拒绝")
        } catch (_: Exception) {}
        assertEquals(backup, repo.backup())
    }

    @Test
    fun saveChecksOverflowWithinEachCurrency() = runBlocking {
        val categoryId = repo.dao.allCategories().first { it.type == "EXPENSE" }.id
        repo.save(Entry(amount = Long.MAX_VALUE, categoryId = categoryId))
        repo.save(Entry(amount = 1, categoryId = categoryId, currencyCode = "JPY"))
        try {
            repo.save(Entry(amount = 1, categoryId = categoryId))
            fail("同币种合计溢出必须拒绝")
        } catch (_: ArithmeticException) {}
        try {
            repo.save(Entry(amount = 1, categoryId = categoryId, currencyCode = "INVALID"))
            fail("无效币种必须拒绝")
        } catch (_: IllegalArgumentException) {}
        assertEquals(2, repo.dao.allEntries().size)
    }

    @Test
    fun invalidMultiCurrencyRestorePreservesExistingRows() = runBlocking {
        val categories = repo.dao.allCategories()
        val categoryId = categories.first { it.type == "EXPENSE" }.id
        repo.save(Entry(amount = 123, categoryId = categoryId))
        val before = repo.backup()
        val invalid =
            listOf(
                    listOf(
                        Entry(id = 1, amount = 1, categoryId = categoryId, currencyCode = "INVALID")
                    ),
                    listOf(
                        Entry(id = 1, amount = 1, categoryId = Long.MAX_VALUE, currencyCode = "JPY")
                    ),
                    listOf(
                        Entry(
                            id = 1,
                            amount = Long.MAX_VALUE,
                            categoryId = categoryId,
                            currencyCode = "KWD",
                        ),
                        Entry(id = 2, amount = 1, categoryId = categoryId, currencyCode = "KWD"),
                    ),
                )
                .map { Backup.encode(categories, it) } +
                before.replace("\"amount\": 123", "\"amount\": 1.9")
        invalid.forEach { raw ->
            try {
                repo.restore(raw)
                fail("无效备份必须拒绝")
            } catch (_: Exception) {}
            assertEquals(before, repo.backup())
        }
    }

    @Test
    fun categoryOrderPersistsAndRejectsInvalidIds() = runBlocking {
        val expense = repo.dao.allCategories().filter { it.type == "EXPENSE" }
        val reverse = expense.map { it.id }.reversed()
        repo.reorderCategories("EXPENSE", reverse)
        assertEquals(
            reverse,
            repo.dao.allCategories().filter { it.type == "EXPENSE" }.map { it.id },
        )
        try {
            repo.reorderCategories("EXPENSE", reverse.dropLast(1) + reverse.first())
            fail("拒绝重复ID")
        } catch (_: IllegalArgumentException) {}
        assertEquals(
            reverse,
            repo.dao.allCategories().filter { it.type == "EXPENSE" }.map { it.id },
        )
    }
}
