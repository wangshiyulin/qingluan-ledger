package com.qingluan.ledger

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthlyBudgetTest {
    @Test
    fun monthlyOverrideIsIndependentAndKeepsLegacyDefaults() {
        val recurring = BudgetRule("CNY", 0, 10000)
        val june = recurring.copy(limit = 20000, month = "2026-06")
        val julyCategory = BudgetRule("CNY", 3, 5000, "2026-07")
        val dollars = BudgetRule("USD", 0, 3000)
        val rules = listOf(recurring, june, julyCategory, dollars)
        assertEquals(
            setOf(june, dollars),
            effectiveBudgetRules(rules, LocalDate.of(2026, 6, 15)).toSet(),
        )
        assertEquals(
            setOf(recurring, julyCategory, dollars),
            effectiveBudgetRules(rules, LocalDate.of(2026, 7, 1)).toSet(),
        )
    }

    @Test
    fun monthlyDeletionSuppressesLegacyWithoutAffectingOtherMonths() {
        val recurring = BudgetRule("CNY", 0, 10000)
        val disabled = recurring.copy(limit = 0, month = "2026-06")
        val rules = BudgetRules.decode(BudgetRules.encode(listOf(recurring, disabled)))
        assertTrue(effectiveBudgetRules(rules, LocalDate.of(2026, 6, 1)).isEmpty())
        assertEquals(listOf(recurring), effectiveBudgetRules(rules, LocalDate.of(2026, 7, 1)))
    }

    @Test
    fun legacyJsonMigratesAndInvalidMonthScopesAreRejected() {
        assertEquals(
            listOf(BudgetRule("CNY", 0, 10000)),
            BudgetRules.decode("""[{"currency":"CNY","categoryId":0,"limit":10000}]"""),
        )
        for (month in listOf("2026-6", "2026-13", "2026-06-01")) {
            assertTrue(
                runCatching { BudgetRules.encode(listOf(BudgetRule("CNY", 0, 10000, month))) }
                    .isFailure
            )
        }
        assertTrue(runCatching { BudgetRules.encode(listOf(BudgetRule("CNY", 0, 0))) }.isFailure)
        assertTrue(
            runCatching { BudgetRules.encode(listOf(BudgetRule("CNY", 0, -1, "2026-06"))) }
                .isFailure
        )
    }
}
