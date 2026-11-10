package com.qingluan.ledger

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DataTest {
    @Test
    fun migrationAndFailedRestorePreserveRecords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "test-${System.nanoTime()}.db"
        val repo = Repository(context, name)
        repo.seed()
        val cs = repo.dao.allCategories()
        val expense = cs.filter { it.type == "EXPENSE" }
        repo.save(Entry(amount = 123, categoryId = expense[0].id))
        repo.remove(expense[0], expense[1].id)
        assertTrue(repo.dao.allEntries().all { it.categoryId != expense[0].id })
        val before = repo.backup()
        try {
            repo.restore("{broken")
        } catch (_: Exception) {}
        assertEquals(before, repo.backup())
        repo.dao.clear()
        repo.db.close()
        context.deleteDatabase(name)
        Unit
    }
}
