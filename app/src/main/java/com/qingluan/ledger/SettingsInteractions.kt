package com.qingluan.ledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun Modifier.elasticClickable(onClick: () -> Unit): Modifier {
    val effects = LocalEffects.current
    val motion = motionAllowed(effects)
    val grouped = LocalSettingsGroup.current
    val strength = LocalExperience.current.motionStrength
    val feedback = rememberAppFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && motion && !grouped)
                when (strength) {
                    "subtle" -> .99f
                    "lively" -> .94f
                    else -> .97f
                }
            else 1f,
            if (motion) spring(dampingRatio = .65f, stiffness = 1000f) else snap(),
            label = "轻触回弹",
        )
    return this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            onClick = {
                feedback()
                onClick()
            },
        )
}
