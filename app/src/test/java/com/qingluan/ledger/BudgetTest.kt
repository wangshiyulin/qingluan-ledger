package com.qingluan.ledger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetTest {
    private val categories = listOf(
        Category(id = 1, name = "日用", type = "EXPENSE"),
        Category(id = 2, name = "工资", type = "INCOME"),
    )

    @Test
    fun rulesKeepCurrencyScopeAndExactAmounts() {
        val rules =
            listOf(
                BudgetRule("CNY", 0, 500000),
                BudgetRule("USD", 0, 50000),
                BudgetRule("CNY", 12, 120000),
            )
        assertEquals(rules, BudgetRules.decode(BudgetRules.encode(rules)))
    }

    @Test
    fun invalidAndDuplicateRulesAreRejected() {
        for (raw in
            listOf(
                "[{\"currency\":\"CNY\",\"categoryId\":0,\"limit\":0}]",
                "[{\"currency\":\"ZZZ\",\"categoryId\":0,\"limit\":1}]",
                "[{\"currency\":\"CNY\",\"categoryId\":-1,\"limit\":1}]",
                "[{\"currency\":\"CNY\",\"categoryId\":0,\"limit\":1.2}]",
            )) assertTrue(runCatching { BudgetRules.decode(raw) }.isFailure)
        val rule = BudgetRule("JPY", 0, 1000)
        assertTrue(runCatching { BudgetRules.encode(listOf(rule, rule)) }.isFailure)
    }

    @Test
    fun completeBackupIncludesBudgetsAndMainCurrency() {
        val budgets = BudgetRules.encode(listOf(BudgetRule("USD", 0, 123450)))
        val personal = PersonalBackup(mainCurrency = "USD", budgets = budgets)
        val raw = CompleteBackup.encode(categories, emptyList(), personal)
        assertEquals(10, JSONObject(raw).getInt("formatVersion"))
        assertEquals(personal, CompleteBackup.decode(raw).personal)
    }

    @Test
    fun legacyBackupDefaultsToCnyAndEmptyBudgets() {
        val j =
            JSONObject(
                    CompleteBackup.encode(
                        categories,
                        emptyList(),
                        PersonalBackup(mainCurrency = "JPY"),
                    )
                )
                .put("formatVersion", 7)
        j.getJSONObject("personal").remove("mainCurrency")
        j.getJSONObject("personal").remove("budgets")
        val restored = CompleteBackup.decode(j.toString()).personal!!
        assertEquals("CNY", restored.mainCurrency)
        assertEquals("[]", restored.budgets)
    }

    @Test
    fun invalidMainCurrencyDoesNotProduceOrRestoreBackup() {
        assertTrue(
            runCatching {
                    CompleteBackup.encode(
                        categories,
                        emptyList(),
                        PersonalBackup(mainCurrency = "ZZZ"),
                    )
                }
                .isFailure
        )
        val j = JSONObject(CompleteBackup.encode(categories, emptyList(), PersonalBackup()))
        j.getJSONObject("personal").put("mainCurrency", "ZZZ")
        assertTrue(runCatching { CompleteBackup.decode(j.toString()) }.isFailure)
    }
}
