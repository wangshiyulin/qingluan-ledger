package com.qingluan.ledger

import androidx.compose.runtime.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

data class UiEffects(val motion: Boolean = true, val haptics: Boolean = true)

val LocalEffects = staticCompositionLocalOf { UiEffects() }
val LocalCurrency = staticCompositionLocalOf { "CNY" }

@Composable
fun rememberAppFeedback(scene: String = "actions"): () -> Unit {
    val e = LocalExperience.current
    val sceneEnabled =
        when (scene) {
            "navigation" -> e.feedbackNavigation
            "input" -> e.feedbackInput
            else -> e.feedbackActions
        }
    val enabled = LocalEffects.current.haptics && sceneEnabled
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic, e.hapticStrength) {
        {
            if (enabled)
                haptic.performHapticFeedback(
                    if (e.hapticStrength == "firm") HapticFeedbackType.LongPress
                    else HapticFeedbackType.TextHandleMove
                )
        }
    }
}

fun currencyPrefix(code: String) = if (code == "CNY") "¥" else code

val LocalFloatingBottomInset = staticCompositionLocalOf { 0.dp }
