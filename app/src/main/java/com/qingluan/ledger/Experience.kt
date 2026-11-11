package com.qingluan.ledger

import androidx.compose.runtime.staticCompositionLocalOf
import org.json.JSONObject

data class Experience(
    val motionStrength: String = "normal",
    val hapticStrength: String = "light",
    val feedbackNavigation: Boolean = true,
    val feedbackInput: Boolean = true,
    val feedbackActions: Boolean = true,
    val undoDelete: Boolean = true,
    val density: String = "compact",
    val showTime: Boolean = true,
    val showNote: Boolean = true,
    val hideAmounts: Boolean = false,
    val statUnit: String = "month",
    val statType: String = "EXPENSE",
    val statPie: Boolean = false,
    val rememberStats: Boolean = true,
    val statDate: String = "",
    val backupDays: String = "off",
    val showTags: Boolean = true,
) {
    fun json(): JSONObject =
        JSONObject().apply {
            put("motionStrength", motionStrength)
            put("hapticStrength", hapticStrength)
            put("feedbackNavigation", feedbackNavigation)
            put("feedbackInput", feedbackInput)
            put("feedbackActions", feedbackActions)
            put("undoDelete", undoDelete)
            put("density", density)
            put("showTime", showTime)
            put("showNote", showNote)
            put("showTags", showTags)
            put("hideAmounts", hideAmounts)
            put("statUnit", statUnit)
            put("statType", statType)
            put("statPie", statPie)
            put("rememberStats", rememberStats)
            put("statDate", statDate)
            put("backupDays", backupDays)
        }

    companion object {
        fun decode(j: JSONObject): Experience {
            fun choice(key: String, default: String, values: Set<String>): String =
                j.optString(key, default).also { require(it in values) { "无效配置：$key" } }
            val date = j.optString("statDate", "")
            if (date.isNotBlank()) java.time.LocalDate.parse(date)
            return Experience(
                choice("motionStrength", "normal", setOf("subtle", "normal", "lively")),
                choice("hapticStrength", "light", setOf("light", "firm")),
                j.optBoolean("feedbackNavigation", true),
                j.optBoolean("feedbackInput", true),
                j.optBoolean("feedbackActions", true),
                j.optBoolean("undoDelete", true),
                choice("density", "compact", setOf("compact", "comfortable")),
                j.optBoolean("showTime", true),
                j.optBoolean("showNote", true),
                j.optBoolean("hideAmounts", false),
                choice("statUnit", "month", setOf("month", "year", "all")),
                choice("statType", "EXPENSE", setOf("EXPENSE", "INCOME", "BALANCE")),
                j.optBoolean("statPie", false),
                j.optBoolean("rememberStats", true),
                date,
                choice("backupDays", "off", setOf("off", "7", "30")),
                j.optBoolean("showTags", true),
            )
        }
    }
}

val LocalExperience = staticCompositionLocalOf { Experience() }

fun visibleMoney(amount: Long, code: String = "CNY", hidden: Boolean) =
    if (hidden) "••••" else Money.text(amount, code)

@androidx.compose.runtime.Composable
fun displayMoney(amount: Long, code: String = "CNY") =
    visibleMoney(amount, code, LocalExperience.current.hideAmounts)
