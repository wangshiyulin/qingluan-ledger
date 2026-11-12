package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val settingsSectionPages = listOf("账单管理")

// Accept saved navigation destinations from earlier builds, then redirect to current pages.
internal val legacySettingsRoutes =
    mapOf("分类与标签" to "数据管理", "本地数据" to "数据管理", "账单计划" to "账单管理", "统计与预算" to "账单管理")

@Composable
fun SettingsSectionPage(page: String, back: () -> Unit, open: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        FeatureHeader(page, back)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (page) {
                "账单管理" -> {
                    SettingsActionCard("周期账单", Icons.Outlined.EventRepeat, { open("周期账单") })
                    SettingsActionCard("分期账单", Icons.Outlined.Payments, { open("分期账单") })
                    SettingsActionCard(
                        "预算管理",
                        Icons.Outlined.AccountBalanceWallet,
                        { open("预算管理") },
                    )
                    SettingsActionCard("标签统计", Icons.Outlined.QueryStats, { open("标签统计") })
                    SettingsActionCard("列表显示", Icons.Outlined.ViewAgenda, { open("列表显示") })
                }
            }
        }
    }
}
