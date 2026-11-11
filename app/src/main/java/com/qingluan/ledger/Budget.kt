package com.qingluan.ledger

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/** Blank month preserves legacy repeating budgets. A monthly zero limit disables that scope. */
data class BudgetRule(
    val currency: String,
    val categoryId: Long = 0,
    val limit: Long,
    val month: String = "",
) {
    val key
        get() = Triple(currency, categoryId, month)

    val scopeKey
        get() = currency to categoryId
}

/** Monthly overrides, including disabled rules, take precedence over repeating defaults. */
fun effectiveBudgetRules(rules: List<BudgetRule>, month: LocalDate): List<BudgetRule> {
    val selectedMonth = YearMonth.from(month).toString()
    val overrides = rules.filter { it.month == selectedMonth }.associateBy { it.scopeKey }
    return (rules.filter { it.month.isEmpty() && it.scopeKey !in overrides } + overrides.values)
        .filter { it.limit > 0 }
}

object BudgetRules {
    val key = stringPreferencesKey("monthlyBudgets")
    val mainCurrencyKey = stringPreferencesKey("mainCurrency")

    fun decode(raw: String): List<BudgetRule> {
        require(raw.length <= 500000) { "预算配置过大" }
        val array = JSONArray(raw)
        require(array.length() <= 2000) { "最多设置2000项预算" }
        val rules =
            (0 until array.length()).map {
                val j = array.getJSONObject(it)
                BudgetRule(
                        j.getString("currency"),
                        ArchiveJson.long(j, "categoryId"),
                        ArchiveJson.long(j, "limit"),
                        if (j.has("month")) j.getString("month") else "",
                    )
                    .also { rule ->
                        require(
                            Currencies.contains(rule.currency) &&
                                rule.categoryId >= 0 &&
                                (rule.limit > 0 || (rule.month.isNotEmpty() && rule.limit == 0L)) &&
                                (rule.month.isEmpty() ||
                                    (rule.month.matches(Regex("[0-9]{4}-[0-9]{2}")) &&
                                        YearMonth.parse(rule.month).toString() == rule.month))
                        ) {
                            "预算配置无效"
                        }
                    }
            }
        require(rules.map { it.key }.distinct().size == rules.size) { "预算重复" }
        return rules
    }

    fun encode(rules: List<BudgetRule>): String =
        JSONArray()
            .apply {
                rules.forEach {
                    put(
                        JSONObject()
                            .put("currency", it.currency)
                            .put("categoryId", it.categoryId)
                            .put("limit", it.limit)
                            .put("month", it.month)
                    )
                }
            }
            .toString()
            .also { decode(it) }
}

val LocalMainCurrency = staticCompositionLocalOf { "CNY" }
val LocalToggleAmounts = staticCompositionLocalOf<(() -> Unit)?> { null }
