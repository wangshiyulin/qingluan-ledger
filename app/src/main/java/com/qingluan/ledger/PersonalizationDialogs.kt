package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    choose: () -> Unit,
    color: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, role = Role.RadioButton, onClick = choose)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null)
        if (color != null) {
            Surface(Modifier.size(20.dp), shape = CircleShape, color = color) {}
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun FontDialog(vm: LedgerViewModel, style: AppAppearance, dismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("字体") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                fontSizes.forEach { (key, label) ->
                    Choice(label, style.fontSize == key, { vm.preference("fontSize", key) })
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("完成") } },
    )
}

@Composable
fun PaletteDialog(vm: LedgerViewModel, style: AppAppearance, dismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("主题") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                paletteNames.entries.toList().chunked(2).forEach { options ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        options.forEach { (key, label) ->
                            Box(Modifier.weight(1f).testTag("palette-$key")) {
                                Choice(
                                    label,
                                    style.palette == key,
                                    { vm.preference("palette", key) },
                                    if (key == "system") MaterialTheme.colorScheme.primary
                                    else palettePreviewColor(key),
                                )
                            }
                        }
                        if (options.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("纯黑深色", Modifier.weight(1f))
                    Switch(
                        style.pureBlack,
                        { vm.pureBlack(it) },
                        modifier = Modifier.testTag("pure-black"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("完成") } },
    )
}
