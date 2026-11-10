package com.qingluan.ledger

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

const val STARTUP_REVEAL_MILLIS = 180

/** Native splash fades over the already drawn destination. Never delays app initialization. */
fun animateStartupExit(view: View, motion: Boolean, remove: () -> Unit): Animator? {
    if (!motion || !ValueAnimator.areAnimatorsEnabled()) {
        remove()
        return null
    }
    var removed = false
    fun finish() {
        if (!removed) {
            removed = true
            remove()
        }
    }
    return AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f),
            ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.025f),
            ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.025f),
        )
        duration = STARTUP_REVEAL_MILLIS.toLong()
        interpolator = DecelerateInterpolator()
        addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()

                override fun onAnimationCancel(animation: Animator) = finish()
            }
        )
        start()
    }
}

/** Pre-Android 12 handoff matches the window splash, then reveals content without a timer. */
@Composable
fun LegacyStartupReveal(ready: Boolean, motion: Boolean, content: @Composable () -> Unit) {
    var covered by remember { mutableStateOf(true) }
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(ready, motion) {
        if (!ready || !covered) return@LaunchedEffect
        if (motion && ValueAnimator.areAnimatorsEnabled())
            coroutineScope {
                launch { alpha.animateTo(0f, tween(STARTUP_REVEAL_MILLIS)) }
                launch { scale.animateTo(1.025f, tween(STARTUP_REVEAL_MILLIS)) }
            }
        covered = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (covered)
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .background(colorResource(R.color.startup_background))
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .forEach { it.consume() }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.splash_logo),
                    null,
                    Modifier.size(120.dp),
                    colorFilter = ColorFilter.tint(colorResource(R.color.startup_logo_tint)),
                )
            }
    }
}
