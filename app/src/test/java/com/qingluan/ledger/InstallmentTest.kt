package com.qingluan.ledger

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InstallmentTest {
    private lateinit var repo: Repository
    private lateinit var databaseName: String

    @Before fun setup() = runBlocking {
        databaseName = "installment-${System.nanoTime()}.db"
        repo = Repository(RuntimeEnvironment.getApplication(), databaseName)
        repo.seed()
    }

    @After fun close() {
        repo.db.close()
        RuntimeEnvironment.getApplication().deleteDatabase(databaseName)
    }

    private suspend fun plan() = InstallmentPlan(name = "电脑", categoryId = repo.dao.allCategories().first { it.type == "EXPENSE" }.id,
        principal = 100, fee = 1, terms = 3, nextDate = "2024-01-31")

    @Test fun exactAmountsAndMonthEndAnchors() = runBlocking {
        val plan = plan()
        assertEquals(listOf(34L, 34L, 33L), (0 until 3).map { installmentTermAmount(plan, it) })
        assertEquals(LocalDate.of(2024, 2, 29), nextInstallmentDate(plan))
        assertEquals(LocalDate.of(2024, 3, 31), nextInstallmentDate(plan.copy(nextDate = "2024-02-29")))
        assertEquals(plan, Installments.decode(Installments.encode(plan)))
        val jpy = plan.copy(currencyCode = "JPY", principal = 120, fee = 0, terms = 120)
        assertEquals(120L, (0 until 120).sumOf { installmentTermAmount(jpy, it) })
    }

    @Test fun invalidAmountsDatesAndIncomeCategoriesAreRejected() = runBlocking {
        val p = plan()
        val cats = repo.dao.allCategories()
        listOf(p.copy(principal = Long.MAX_VALUE, fee = 1), p.copy(terms = 121), p.copy(terms = 102),
            p.copy(principal = 0), p.copy(fee = -1), p.copy(paidTerms = 4), p.copy(note = "注".repeat(901)),
            p.copy(nextDate = "2024-02-20"), p.copy(categoryId = cats.first { it.type == "INCOME" }.id)).forEach {
            assertTrue(runCatching { Installments.validate(it, cats) }.isFailure)
        }
    }

    @Test fun confirmationIsExactlyOnceAndCompletionRetainsBills() = runBlocking {
        repo.saveInstallment(plan().copy(name = "名".repeat(50), note = "注".repeat(900)))
        val id = repo.dao.allInstallments().single().id
        repo.confirmInstallment(id, 0)
        assertTrue(runCatching { repo.confirmInstallment(id, 0) }.isFailure)
        assertEquals(1, repo.dao.allEntries().size)
        assertTrue(repo.dao.allEntries().single().note.endsWith("注".repeat(900)))
        assertTrue(repo.dao.allEntries().single().note.length <= 1000)
        val after = repo.dao.installment(id)!!
        assertEquals(1, after.paidTerms)
        assertEquals("2024-02-29", after.nextDate)
        assertTrue(runCatching { repo.saveInstallment(after.copy(principal = 500)) }.isFailure)
        assertTrue(runCatching { repo.saveInstallment(after.copy(nextDate = "2024-03-31")) }.isFailure)
        repo.saveInstallment(after.copy(name = "新名称"))
        repo.confirmInstallment(id, 1)
        repo.confirmInstallment(id, 2)
        assertEquals(101L, repo.dao.allEntries().sumOf { it.amount })
        assertFalse(repo.dao.installment(id)!!.enabled)
        assertTrue(runCatching { repo.confirmInstallment(id, 3) }.isFailure)
        repo.deleteInstallment(id, 3)
        assertEquals(3, repo.dao.allEntries().size)
    }

    @Test fun pauseFutureAndOverflowNeverAdvanceProgress() = runBlocking {
        repo.saveInstallment(plan())
        val id = repo.dao.allInstallments().single().id
        repo.setInstallmentEnabled(id, 0, false)
        assertTrue(runCatching { repo.confirmInstallment(id, 0) }.isFailure)
        repo.setInstallmentEnabled(id, 0, true)
        val p = repo.dao.installment(id)!!
        repo.save(Entry(amount = Long.MAX_VALUE, categoryId = p.categoryId))
        assertTrue(runCatching { repo.confirmInstallment(id, 0) }.isFailure)
        assertEquals(0, repo.dao.installment(id)!!.paidTerms)
        assertEquals(1, repo.dao.allEntries().size)
        repo.saveInstallment(p.copy(nextDate = "2190-01-31"))
        assertTrue(runCatching { repo.confirmInstallment(id, 0) }.isFailure)
    }

    @Test fun deletingBillDoesNotRewindAndCategoryRemovalMigratesPlans() = runBlocking {
        val p = plan()
        repo.saveInstallment(p)
        val id = repo.dao.allInstallments().single().id
        repo.confirmInstallment(id, 0)
        repo.dao.delete(repo.dao.allEntries().single().id)
        assertEquals(1, repo.dao.installment(id)!!.paidTerms)
        val cats = repo.dao.allCategories()
        val source = cats.first { it.id == p.categoryId }
        val target = cats.first { it.type == "EXPENSE" && it.id != source.id }
        assertTrue(runCatching { repo.remove(source, null) }.isFailure)
        repo.remove(source, target.id)
        assertEquals(target.id, repo.dao.installment(id)!!.categoryId)
        val backup = repo.backup()
        repo.restore(backup)
        assertTrue(repo.dao.allInstallments().isEmpty())
    }
}
