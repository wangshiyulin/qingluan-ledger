package com.qingluan.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal val LocalSettingsGroup = staticCompositionLocalOf { false }

/** The outside corners belong to the group; rows inside stay straight. */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalSettingsGroup provides true) {
        Column(
            modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).testTag("settings-group"),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content,
        )
    }
}

@Composable
internal fun Modifier.settingsRow(): Modifier =
    if (LocalSettingsGroup.current) this.background(MaterialTheme.colorScheme.surfaceContainerLow)
    else this

@Composable
internal fun SettingsHeading(title: String) {
    androidx.compose.material3.Text(
        title,
        Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
    )
}

@Composable
internal fun settingsItemShape(): Shape =
    if (LocalSettingsGroup.current) androidx.compose.ui.graphics.RectangleShape
    else RoundedCornerShape(24.dp)
