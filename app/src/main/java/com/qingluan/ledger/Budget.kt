package com.qingluan.ledger

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

data class BudgetRule(val currency: String, val categoryId: Long = 0, val limit: Long) {
    val key
        get() = currency to categoryId
}

object BudgetRules {
    val key = stringPreferencesKey("monthlyBudgets")
    val mainCurrencyKey = stringPreferencesKey("mainCurrency")

    fun decode(raw: String): List<BudgetRule> {
        require(raw.length <= 100000) { "预算配置过大" }
        val array = JSONArray(raw)
        require(array.length() <= 500) { "最多设置500项预算" }
        val rules =
            (0 until array.length()).map {
                val j = array.getJSONObject(it)
                BudgetRule(
                        j.getString("currency"),
                        ArchiveJson.long(j, "categoryId"),
                        ArchiveJson.long(j, "limit"),
                    )
                    .also { rule ->
                        require(
                            Currencies.contains(rule.currency) &&
                                rule.categoryId >= 0 &&
                                rule.limit > 0
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
                    )
                }
            }
            .toString()
            .also { decode(it) }
}

val LocalMainCurrency = staticCompositionLocalOf { "CNY" }
val LocalToggleAmounts = staticCompositionLocalOf<(() -> Unit)?> { null }
