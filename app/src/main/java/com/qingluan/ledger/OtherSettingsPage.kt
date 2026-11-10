package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.emptyPreferences

@Composable
fun OtherSettingsPage(vm: LedgerViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val prefs by context.preferences.data.collectAsState(initial = emptyPreferences())
    val enabled = prefs[DesktopEntry.enabledKey] ?: false
    val busy by vm.saving.collectAsState()

    Column(Modifier.fillMaxSize()) {
        FeatureHeader("其他设置", back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            ConfigSwitch("桌面快捷记账", enabled) { value ->
                if (!busy) vm.action { DesktopEntry.setEnabled(context, value) }
            }
            Text(
                "长按桌面应用图标，选择记支出或记收入。",
                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
