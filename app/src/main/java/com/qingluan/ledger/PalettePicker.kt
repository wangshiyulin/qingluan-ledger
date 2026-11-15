package com.qingluan.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.*

private val PetalShape = GenericShape { size, _ ->
    for (i in 0..128) {
        val angle = i / 128f * (2 * PI).toFloat() - PI.toFloat() / 2
        val r = .45f + .035f * cos(angle * 8)
        val x = size.width * (.5f + cos(angle) * r)
        val y = size.height * (.5f + sin(angle) * r)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
internal fun PalettePreview(color: Color, modifier: Modifier = Modifier, checked: Boolean = false) {
    Box(modifier.size(32.dp).background(color, PetalShape), contentAlignment = Alignment.Center) {
        if (checked)
            Icon(
                Icons.Outlined.Check,
                null,
                Modifier.size(18.dp),
                tint = if (color.luminance() > .4f) Color(0xFF18222C) else Color.White,
            )
    }
}

@Composable
fun PaletteDialog(vm: LedgerViewModel, style: AppAppearance, dismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val selected = normalizePalette(style.palette)
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("配色") },
        text = {
            Column(
                Modifier.heightIn(max = dialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val choices =
                    listOf(Triple("system", "系统动态", colors.primary)) +
                        LedgerPalette.entries.map { Triple(it.key, it.title, it.seed) }
                choices.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (key, title, seed) ->
                            val active = selected == key
                            Row(
                                Modifier.weight(1f)
                                    .heightIn(min = 64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (active) colors.primaryContainer
                                        else colors.surfaceContainer
                                    )
                                    .border(
                                        if (active) .65.dp else .5.dp,
                                        if (active) colors.primary.copy(alpha = .5f)
                                        else colors.outlineVariant.copy(alpha = .65f),
                                        RoundedCornerShape(18.dp),
                                    )
                                    .clickable(role = Role.RadioButton) {
                                        vm.preference("palette", key)
                                    }
                                    .semantics { this.selected = active }
                                    .testTag("palette-$key")
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PalettePreview(seed, checked = active)
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color =
                                        if (active) colors.onPrimaryContainer else colors.onSurface,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("纯黑深色", Modifier.weight(1f))
                    Switch(style.pureBlack, { vm.pureBlack(it) }, Modifier.testTag("pure-black"))
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("完成") } },
    )
}
