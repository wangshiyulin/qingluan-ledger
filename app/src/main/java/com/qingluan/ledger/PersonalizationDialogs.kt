package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    var custom by remember { mutableStateOf(style.fontSize == "custom") }
    var percent by remember { mutableIntStateOf(style.customFontPercent) }
    var text by remember { mutableStateOf(style.customFontPercent.toString()) }
    val value = text.toIntOrNull()
    val valid = value != null && value in 75..160
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("字体") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                fontSizes
                    .filterKeys { it != "custom" }
                    .forEach { (key, label) ->
                        Choice(
                            label,
                            !custom && style.fontSize == key,
                            {
                                custom = false
                                vm.preference("fontSize", key)
                            },
                        )
                    }
                Choice("自定义", custom, { custom = true })
                if (custom) {
                    OutlinedTextField(
                        text,
                        { input ->
                            text = input.take(3)
                            input.toIntOrNull()?.takeIf { it in 75..160 }?.let { percent = it }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("font-custom-percent"),
                        label = { Text("字号比例（%）") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                percent = (percent - 1).coerceAtLeast(75)
                                text = percent.toString()
                            }
                        ) {
                            Text("−")
                        }
                        Slider(
                            percent.toFloat(),
                            {
                                percent = it.roundToInt()
                                text = percent.toString()
                            },
                            modifier = Modifier.weight(1f).testTag("font-custom-slider"),
                            valueRange = 75f..160f,
                            steps = 84,
                        )
                        IconButton(
                            onClick = {
                                percent = (percent + 1).coerceAtMost(160)
                                text = percent.toString()
                            }
                        ) {
                            Text("+")
                        }
                    }
                    Text("75%–160% · 在系统字号基础上调整", style = MaterialTheme.typography.labelSmall)
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("字体预览", fontSize = (16 * percent / 100f).sp, lineHeight=(24*percent/100f).sp)
                            Text("青鸾记账 123.45", fontSize = (14 * percent / 100f).sp, lineHeight=(20*percent/100f).sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            percent = 100
                            text = "100"
                        }
                    ) {
                        Text("恢复100%")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !custom || valid,
                onClick = { if (custom) vm.customFontPercent(value!!, dismiss) else dismiss() },
            ) {
                Text(if (custom) "应用" else "完成")
            }
        },
        dismissButton = if (custom) ({ TextButton(onClick = dismiss) { Text("取消") } }) else null,
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
