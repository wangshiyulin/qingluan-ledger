package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun CurrencyButton(code: String, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val feedback = rememberAppFeedback()
    TextButton(
        onClick = {
            feedback()
            open = true
        },
        contentPadding = PaddingValues(4.dp),
    ) {
        Text(code, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp))
    }
    if (open)
        CurrencyPicker(code, { open = false }) {
            change(it)
            open = false
        }
}

@Composable
fun CurrencyPicker(code: String, dismiss: () -> Unit, choose: (String) -> Unit) {
    val feedback = rememberAppFeedback()
    var query by remember { mutableStateOf("") }
    val frequency = LocalCurrencyFrequency.current
    val list =
        remember(query, frequency) {
            currencyDisplayOrder(frequency).filter {
                it.code.contains(query, true) || it.name.contains(query, true)
            }
        }
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("币种") },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    Modifier.fillMaxWidth().testTag("currency-search"),
                    singleLine = true,
                    placeholder = { Text("搜索币种") },
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.code }) { c ->
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    feedback()
                                    choose(c.code)
                                },
                            headlineContent = { Text(c.code) },
                            supportingContent = { Text(c.name) },
                            trailingContent = {
                                if (code == c.code) Icon(Icons.Outlined.Check, null)
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}

val LocalCurrencyFrequency = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }
private val commonCurrencyOrder =
    listOf(
        "CNY",
        "USD",
        "HKD",
        "EUR",
        "JPY",
        "GBP",
        "MOP",
        "TWD",
        "KRW",
        "SGD",
        "AUD",
        "CAD",
        "CHF",
        "MYR",
        "THB",
        "RUB",
    )

fun currencyDisplayOrder(frequency: Map<String, Int>): List<CurrencyUnit> =
    Currencies.all.sortedWith(
        compareByDescending<CurrencyUnit> { frequency[it.code] ?: 0 }
            .thenBy {
                commonCurrencyOrder.indexOf(it.code).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }
            .thenBy { it.code }
    )
