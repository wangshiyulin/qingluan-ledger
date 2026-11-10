package com.qingluan.ledger

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.*

@Composable
fun LiquidNavigation(route: String, haze: HazeState, select: (String) -> Unit) {
    val effects = LocalEffects.current
    val strength = LocalExperience.current.motionStrength
    val feedback = rememberAppFeedback("navigation")
    val shape = RoundedCornerShape(28.dp)
    val surface = MaterialTheme.colorScheme.surface
    Row(
        Modifier.navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 7.dp)
            .fillMaxWidth()
            .height(58.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .hazeChild(
                haze,
                HazeStyle(
                    backgroundColor = surface,
                    tints = listOf(HazeTint(surface.copy(alpha = .42f))),
                    blurRadius = 24.dp,
                ),
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = .35f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .3f),
                        Color.White.copy(alpha = .12f),
                    )
                ),
                shape,
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
                "明细" to Icons.Outlined.ReceiptLong,
                "洞察" to Icons.Outlined.Insights,
                "我的" to Icons.Outlined.Settings,
            )
            .forEach { (label, image) ->
                val selected = route == label
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by
                    animateFloatAsState(
                        if (pressed && effects.motion)
                            when (strength) {
                                "subtle" -> .98f
                                "lively" -> .89f
                                else -> .93f
                            }
                        else 1f,
                        if (effects.motion) spring(dampingRatio = .7f, stiffness = 900f)
                        else snap(),
                        label = "导航按压",
                    )
                val color by
                    animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .75f)
                        else Color.Transparent,
                        tween(if (effects.motion) 100 else 0),
                        label = "导航材质",
                    )
                Column(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(26.dp))
                        .background(color)
                        .clickable(interactionSource = interaction, indication = ripple()) {
                            feedback()
                            select(label)
                        }
                        .semantics {
                            this.selected = selected
                            role = Role.Tab
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        image,
                        null,
                        Modifier.size(20.dp),
                        tint =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
fun Modifier.elasticClickable(onClick: () -> Unit): Modifier {
    val effects = LocalEffects.current
    val strength = LocalExperience.current.motionStrength
    val feedback = rememberAppFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && effects.motion)
                when (strength) {
                    "subtle" -> .99f
                    "lively" -> .94f
                    else -> .97f
                }
            else 1f,
            if (effects.motion) spring(dampingRatio = .65f, stiffness = 1000f) else snap(),
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
