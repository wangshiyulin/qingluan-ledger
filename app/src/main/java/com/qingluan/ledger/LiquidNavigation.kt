package com.qingluan.ledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

/** A sampled background lens; gestures move the lens before committing a destination. */
@Composable
fun LiquidNavigation(tab: Int, backdrop: Backdrop, select: (Int) -> Unit) {
    val animationsEnabled = motionAllowed(LocalEffects.current)
    val hapticsEnabled = LocalEffects.current.haptics
    val dark = LocalDarkAppearance.current
    val colors = MaterialTheme.colorScheme
    val hardware = LocalView.current.isHardwareAccelerated
    val feedback = rememberAppFeedback("navigation")
    val latestSelect by rememberUpdatedState(select)
    val latestTab by rememberUpdatedState(tab)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableFloatStateOf(tab.toFloat()) }
    val progress by
        animateFloatAsState(
            if (pressed || dragging) 1f else 0f,
            if (animationsEnabled) spring(.8f, 700f) else snap(),
            label = "glass-pressure",
        )
    val position by
        animateFloatAsState(
            if (dragging) dragIndex else tab.toFloat(),
            if (!animationsEnabled || dragging) snap() else spring(.78f, 550f),
            label = "glass-position",
        )
    fun choose(index: Int) {
        if (index != latestTab) {
            if (hapticsEnabled) feedback()
            latestSelect(index)
        }
    }
    val labels = listOf("明细", "洞察", "我的")
    val icons = listOf(Icons.AutoMirrored.Outlined.ReceiptLong, Icons.Outlined.Insights, Icons.Outlined.Person)
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier.widthIn(max = 352.dp)
                .fillMaxWidth()
                .height(60.dp)
                .testTag("transparent-navigation")
                .pointerInput(animationsEnabled, hapticsEnabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragIndex = (offset.x / (size.width / 3f) - .5f).coerceIn(0f, 2f)
                            dragging = true
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragIndex =
                                (change.position.x / (size.width / 3f) - .5f).coerceIn(0f, 2f)
                        },
                        onDragEnd = {
                            choose(dragIndex.roundToInt())
                            dragging = false
                        },
                        onDragCancel = { dragging = false },
                    )
                }
        ) {
            val width = maxWidth / 3
            Box(
                Modifier.fillMaxSize()
                    .then(
                        if (hardware)
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { shape },
                                effects = {
                                    vibrancy()
                                    blur(8.dp.toPx())
                                    lens(25.dp.toPx(), 27.dp.toPx(), chromaticAberration = true)
                                },
                                onDrawSurface = {
                                    drawRect(
                                        if (dark) Color(0xFF171B20).copy(alpha = .35f)
                                        else Color.White.copy(alpha = .35f)
                                    )
                                },
                            )
                        else Modifier.background(colors.surfaceContainer.copy(alpha = .92f), shape)
                    )
                    .border(
                        .65.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = .48f),
                                Color.White.copy(alpha = .06f),
                                Color.White.copy(alpha = .20f),
                            )
                        ),
                        shape,
                    )
            )
            val lensShape = RoundedCornerShape(26.dp)
            Box(
                Modifier.offset {
                        IntOffset((width * position + 4.dp).roundToPx(), 4.dp.roundToPx())
                    }
                    .width(width - 8.dp)
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = 1f + .08f * progress
                        scaleY = 1f + .13f * progress
                    }
                    .then(
                        if (hardware)
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { lensShape },
                                effects = {
                                    lens(
                                        (10f + progress * 4f).dp.toPx(),
                                        14.dp.toPx(),
                                        chromaticAberration = true,
                                    )
                                },
                                onDrawSurface = {
                                    drawRect(Color.White.copy(alpha = if (dark) .10f else .28f))
                                },
                            )
                        else Modifier.background(colors.tertiary.copy(alpha = .13f), lensShape)
                    )
                    .border(.5.dp, Color.White.copy(alpha = if (dark) .26f else .65f), lensShape)
            )
            Row(Modifier.fillMaxSize()) {
                labels.forEachIndexed { i, label ->
                    val active = if (dragging) dragIndex.roundToInt() == i else i == tab
                    val color = if (active) colors.tertiary else colors.onSurfaceVariant
                    Column(
                        Modifier.weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactions,
                                indication = null,
                                role = Role.Tab,
                            ) {
                                choose(i)
                            }
                            .semantics { selected = i == tab },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement =
                            Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            icons[i],
                            null,
                            Modifier.size(23.dp).graphicsLayer {
                                val grow = if (active) progress * .08f else 0f
                                scaleX = 1f + grow
                                scaleY = scaleX
                            },
                            tint = color,
                        )
                        Text(
                            label,
                            fontSize = 11.sp,
                            lineHeight = 13.sp,
                            color = if (active) colors.onSurface else color,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
