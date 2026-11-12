package com.qingluan.ledger

import androidx.datastore.preferences.core.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release110IntegrationTest {
    @Test
    fun monthlyEditsCanMoveScopeAndReAddDeletedMonthWithoutChangingDefaults() {
        val legacy = BudgetRule("CNY", 0, 10000)
        val next = BudgetRule("USD", 0, 30000, "2026-11")
        val moved = updatedMonthlyBudgets(listOf(legacy), legacy, next)
        assertEquals(listOf(next), effectiveBudgetRules(moved, LocalDate.parse("2026-11-01")))
        assertEquals(listOf(legacy), effectiveBudgetRules(moved, LocalDate.parse("2026-12-01")))
        val restored =
            updatedMonthlyBudgets(moved, null, legacy.copy(month = "2026-11", limit = 5000))
        assertEquals(2, effectiveBudgetRules(restored, LocalDate.parse("2026-11-01")).size)
        assertTrue(
            runCatching { updatedMonthlyBudgets(restored, null, next.copy(limit = 40000)) }
                .isFailure
        )
    }

    @Test
    fun completeRestoreKeepsInstallmentProgressMonthlyBudgetAndListPreference() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val repo = Repository(context, "release110-test.db")
        try {
            context.preferences.updateData { emptyPreferences() }
            repo.seed()
            val category = repo.dao.allCategories().first { it.type == "EXPENSE" }
            val plan =
                InstallmentPlan(
                    name = "电脑",
                    categoryId = category.id,
                    principal = 101,
                    fee = 2,
                    terms = 3,
                )
            repo.saveInstallment(plan)
            val saved = repo.dao.allInstallments().single()
            repo.confirmInstallment(saved.id, 0)
            val budgets = BudgetRules.encode(listOf(BudgetRule("CNY", 0, 10000, "2026-11")))
            context.preferences.edit {
                it[BudgetRules.key] = budgets
                it[stringPreferencesKey("experience")] =
                    Experience(showTags = false).json().toString()
            }
            val manager = LocalDataManager(context, repo)
            val raw = manager.export()
            assertEquals(11, JSONObject(raw).getInt("formatVersion"))
            manager.restore(raw)
            assertEquals(1, repo.dao.allInstallments().single().paidTerms)
            assertEquals(35L, repo.dao.allEntries().single().amount)
            val prefs = context.preferences.data.first()
            assertEquals(BudgetRules.decode(budgets), BudgetRules.decode(prefs[BudgetRules.key]!!))
            assertFalse(
                Experience.decode(JSONObject(prefs[stringPreferencesKey("experience")]!!)).showTags
            )
        } finally {
            repo.db.close()
            context.deleteDatabase("release110-test.db")
        }
    }

    @Test
    fun binaryTransferKeepsXlsxOutOfFullRestoreMode() {
        val categories = listOf(Category(1, "餐饮", "EXPENSE"))
        val entry = Entry(amount = 123, categoryId = 1, occurredAt = 1700000000000, tags = "午餐")
        val data =
            decodeTransfer(XlsxTransfer.export(listOf(entry), categories), "xlsx", charArrayOf())
        assertEquals("xlsx", data.kind)
        assertNull(data.fullJson)
        assertEquals("午餐", data.bills.single().tags)
        assertEquals(123L, data.bills.single().amount)
    }

    @Test
    fun hidingTagsDoesNotHideNotesOrChangeStoredTags() {
        val entry = Entry(amount = 100, categoryId = 1, note = "备注", tags = "午餐")
        assertEquals("备注", entrySubtitle(entry, Experience(showTime = false, showTags = false)))
        assertEquals("午餐", entry.tags)
    }
}
