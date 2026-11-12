package com.qingluan.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val context = LocalContext.current.applicationContext
    val initial = remember(style) { normalizeFontSize(style.fontSize, style.customFontPercent) }
    var custom by remember { mutableStateOf(initial.first == "custom") }
    var percent by remember { mutableIntStateOf(initial.second) }
    var text by remember { mutableStateOf(initial.second.toString()) }
    var family by remember { mutableStateOf(style.fontFamily) }
    var fontId by remember { mutableStateOf(style.customFontId) }
    var importing by remember { mutableStateOf(false) }
    var fontError by remember { mutableStateOf("") }
    val staged = remember { mutableListOf<String>() }
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    DisposableEffect(Unit) {
        onDispose {
            alive.set(false)
            val files = staged.toList()
            CoroutineScope(Dispatchers.IO).launch {
                val keep =
                    runCatching {
                            context.preferences.data
                                .first()[
                                    androidx.datastore.preferences.core.stringPreferencesKey(
                                        "customFontId"
                                    )] ?: ""
                        }
                        .getOrElse {
                            return@launch
                        }
                FontFiles.discard(context, files, keep)
            }
        }
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !importing) {
                importing = true
                vm.action {
                    try {
                        val id = FontFiles.stage(context, uri)
                        if (alive.get()) {
                            staged += id
                            fontId = id
                            family = "custom"
                            fontError = ""
                        } else FontFiles.discard(context, listOf(id), "")
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (alive.get()) fontError = "无法导入，请选择有效的TTF/OTF字体（最多32MiB）"
                        throw e
                    } finally {
                        importing = false
                    }
                }
            }
        }
    val value = text.toIntOrNull()
    val valid = value != null && value in 75..160
    val previewFamily = rememberAppFont(style.copy(fontFamily = family, customFontId = fontId))
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("字体") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("字号", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().testTag("font-size-modes"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    fontSizes.forEach { (key, label) ->
                        Row(
                            Modifier.weight(1f)
                                .heightIn(min = 48.dp)
                                .selectable(
                                    if (key == "custom") custom else !custom,
                                    enabled = !importing,
                                    role = Role.RadioButton,
                                    onClick = { custom = key == "custom" },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                if (key == "custom") custom else !custom,
                                null,
                                Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (custom) {
                    OutlinedTextField(
                        text,
                        { input ->
                            text = input.take(3)
                            text.toIntOrNull()?.takeIf { it in 75..160 }?.let { percent = it }
                        },
                        Modifier.fillMaxWidth().testTag("font-custom-percent"),
                        enabled = !importing,
                        label = { Text("字号比例（%）") },
                        singleLine = true,
                        isError = !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            enabled = !importing,
                            onClick = {
                                percent = (percent - 1).coerceAtLeast(75)
                                text = percent.toString()
                            },
                        ) {
                            Text("−")
                        }
                        Slider(
                            percent.toFloat(),
                            {
                                percent = it.roundToInt()
                                text = percent.toString()
                            },
                            Modifier.weight(1f).testTag("font-custom-slider"),
                            enabled = !importing,
                            valueRange = 75f..160f,
                            steps = 84,
                        )
                        IconButton(
                            enabled = !importing,
                            onClick = {
                                percent = (percent + 1).coerceAtMost(160)
                                text = percent.toString()
                            },
                        ) {
                            Text("+")
                        }
                    }
                    TextButton(
                        enabled = !importing,
                        onClick = {
                            percent = 100
                            text = "100"
                        },
                    ) {
                        Text("恢复100%")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                ConfigChoice("字形", family, fontFamilies) { if (!importing) family = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !importing,
                        onClick = {
                            try {
                                picker.launch(
                                    arrayOf(
                                        "font/ttf",
                                        "font/otf",
                                        "application/x-font-ttf",
                                        "application/vnd.ms-opentype",
                                        "application/octet-stream",
                                        "*/*",
                                    )
                                )
                            } catch (_: Exception) {
                                fontError = "无法打开系统文件选择器"
                            }
                        },
                    ) {
                        Text(if (importing) "导入中…" else "导入字体")
                    }
                    if (fontId.isNotBlank())
                        TextButton(
                            enabled = !importing,
                            onClick = {
                                fontId = ""
                                if (family == "custom") family = "system"
                            },
                        ) {
                            Text("移除字体")
                        }
                }
                if (family == "custom")
                    Text(
                        "TTF / OTF · 最多32MiB · 字体文件仅保存在本机",
                        style = MaterialTheme.typography.labelSmall,
                    )
                if (fontError.isNotBlank())
                    Text(
                        fontError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                val scale = if (custom) percent / 100f else 1f
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(
                            "字体预览",
                            fontFamily = previewFamily,
                            fontSize = (16 * scale).sp,
                            lineHeight = (24 * scale).sp,
                        )
                        Text(
                            "青鸾记账 123.45",
                            fontFamily = previewFamily,
                            fontSize = (14 * scale).sp,
                            lineHeight = (20 * scale).sp,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    !importing && (!custom || valid) && (family != "custom" || fontId.isNotBlank()),
                onClick = {
                    vm.applyFont(
                        if (custom) "custom" else "system",
                        if (custom) value!! else percent,
                        family,
                        fontId,
                        dismiss,
                    )
                },
            ) {
                Text("完成")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
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
