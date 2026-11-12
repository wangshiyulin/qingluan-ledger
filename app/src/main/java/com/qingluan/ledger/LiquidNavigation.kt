package com.qingluan.ledger

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
    val motion = motionAllowed(LocalEffects.current)
    val feedback = rememberAppFeedback("navigation")
    val shape = RoundedCornerShape(29.dp)
    val colors = MaterialTheme.colorScheme
    val surface = colors.surface
    val dark = surface.luminance() < .5f
    val hardwareBlur =
        android.os.Build.VERSION.SDK_INT >= 31 &&
            androidx.compose.ui.platform.LocalView.current.isHardwareAccelerated
    val tabs =
        listOf(
            "明细" to Icons.Outlined.ReceiptLong,
            "洞察" to Icons.Outlined.Insights,
            "我的" to Icons.Outlined.Person,
        )
    val index = tabs.indexOfFirst { it.first == route }.coerceAtLeast(0)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            Modifier.navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 6.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .height(58.dp)
                .shadow(5.dp, shape)
                .clip(shape)
                .hazeChild(
                    haze,
                    HazeStyle(
                        backgroundColor = surface.copy(alpha = .03f),
                        tints = listOf(HazeTint(surface.copy(alpha = if (dark) .20f else .13f))),
                        blurRadius = 20.dp,
                        noiseFactor = .01f,
                        fallbackTint = HazeTint(surface.copy(alpha = .88f)),
                    ),
                ) {
                    blurEnabled = hardwareBlur
                }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) .07f else .14f),
                            Color.White.copy(alpha = .01f),
                            Color.Black.copy(alpha = if (dark) .06f else .035f),
                        )
                    )
                )
                .border(
                    .8.dp,
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) .38f else .72f),
                            colors.outline.copy(alpha = .12f),
                            Color.White.copy(alpha = .22f),
                        )
                    ),
                    shape,
                )
                .padding(4.dp)
        ) {
            val tabWidth = (maxWidth - 8.dp) / 3
            val position by
                animateDpAsState(
                    (tabWidth + 4.dp) * index,
                    if (motion) spring(dampingRatio = .86f, stiffness = 520f) else snap(),
                    label = "玻璃选中胶囊位置",
                )
            val lensShape = RoundedCornerShape(25.dp)
            Box(
                Modifier.offset(x = position)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .shadow(2.dp, lensShape)
                    .clip(lensShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = if (dark) .19f else .50f),
                                surface.copy(alpha = .10f),
                                Color.White.copy(alpha = if (dark) .07f else .24f),
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                colors.outline.copy(alpha = .28f),
                                Color.White.copy(alpha = if (dark) .60f else .95f),
                                Color.White.copy(alpha = .10f),
                                colors.outline.copy(alpha = .22f),
                            )
                        ),
                        lensShape,
                    )
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { (label, image) ->
                    val selected = route == label
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clip(lensShape)
                            .clickable {
                                if (!selected) {
                                    feedback()
                                    select(label)
                                }
                            }
                            .semantics {
                                this.selected = selected
                                role = Role.Tab
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val tint = if (selected) colors.primary else colors.onSurfaceVariant
                        Icon(image, null, Modifier.size(20.dp), tint = tint)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.elasticClickable(onClick: () -> Unit): Modifier {
    val effects = LocalEffects.current
    val motion = motionAllowed(effects)
    val strength = LocalExperience.current.motionStrength
    val feedback = rememberAppFeedback()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed && motion)
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
