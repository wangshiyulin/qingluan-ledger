package com.qingluan.ledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens

/** Uses the navigation bar's same sampled glass and pressure lens, with a single circular lens. */
@Composable
internal fun LiquidEntryButton(backdrop: Backdrop, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = LocalDarkAppearance.current
    val hardware = LocalView.current.isHardwareAccelerated
    val motion = motionAllowed(LocalEffects.current)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val progress by
        animateFloatAsState(
            if (pressed) 1f else 0f,
            if (motion) spring(.8f, 700f) else snap(),
            label = "entry-glass-pressure",
        )
    Box(
        Modifier.size(68.dp)
            .testTag("liquid-entry-button")
            .clickable(
                interactionSource = interactions,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer {
                    scaleX = 1f + .08f * progress
                    scaleY = 1f + .13f * progress
                }
                .then(
                    if (hardware)
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { CircleShape },
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
                    else Modifier.background(colors.tertiary.copy(alpha = .13f), CircleShape)
                )
                .border(.5.dp, Color.White.copy(alpha = if (dark) .26f else .65f), CircleShape)
        )
        Icon(
            Icons.Outlined.Add,
            "记账",
            Modifier.size(30.dp).graphicsLayer {
                scaleX = 1f + progress * .08f
                scaleY = scaleX
            },
            tint = colors.tertiary,
        )
    }
}
