package com.qingluan.ledger

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Launcher shortcuts only open a draft after the app unlocks. */
object DesktopEntry {
    const val expenseAction = "com.qingluan.ledger.action.ADD_EXPENSE"
    const val incomeAction = "com.qingluan.ledger.action.ADD_INCOME"
    val enabledKey = booleanPreferencesKey("desktopQuickEntry")
    private val ids = listOf("quick_expense", "quick_income")

    fun parse(intent: Intent?): String? =
        when (intent?.action) {
            expenseAction -> "EXPENSE"
            incomeAction -> "INCOME"
            else -> null
        }

    fun configure(context: Context, enabled: Boolean) {
        val manager =
            context.getSystemService(ShortcutManager::class.java) ?: error("当前系统不支持桌面快捷记账")
        if (enabled) {
            if (manager.dynamicShortcuts.map { it.id }.toSet() != ids.toSet()) {
                val shortcuts =
                    ids.mapIndexed { rank, id ->
                        val income = rank == 1
                        ShortcutInfo.Builder(context, id)
                            .setShortLabel(
                                context.getString(
                                    if (income) R.string.desktop_entry_income
                                    else R.string.desktop_entry_expense
                                )
                            )
                            .setIcon(
                                Icon.createWithResource(
                                    context,
                                    if (income) R.drawable.shortcut_income
                                    else R.drawable.shortcut_expense,
                                )
                            )
                            .setIntent(
                                Intent(context, MainActivity::class.java)
                                    .setAction(if (income) incomeAction else expenseAction)
                            )
                            .setRank(rank)
                            .build()
                    }
                check(manager.setDynamicShortcuts(shortcuts)) { "系统暂时无法更新快捷方式，请稍后再试" }
            }
            manager.enableShortcuts(ids)
        } else {
            manager.removeDynamicShortcuts(ids)
            manager.disableShortcuts(ids, "已关闭桌面快捷记账")
        }
    }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        val previous = context.preferences.data.first()[enabledKey] ?: false
        withContext(Dispatchers.IO) { configure(context, enabled) }
        try {
            context.preferences.edit { it[enabledKey] = enabled }
        } catch (error: Exception) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) {
                runCatching { configure(context, previous) }
            }
            throw error
        }
    }
}
