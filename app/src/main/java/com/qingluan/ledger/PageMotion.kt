package com.qingluan.ledger

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation.NavBackStackEntry

val LocalRouteActive = staticCompositionLocalOf { true }
private val mainTabs = listOf("明细", "洞察", "我的")

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabChange() =
    initialState.destination.route in mainTabs && targetState.destination.route in mainTabs

fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(
    enabled: Boolean,
    pop: Boolean = false,
): EnterTransition {
    if (!enabled || initialState.destination.route == targetState.destination.route)
        return EnterTransition.None
    val tab = tabChange()
    val direction =
        if (
            tab &&
                mainTabs.indexOf(targetState.destination.route.orEmpty()) <
                    mainTabs.indexOf(initialState.destination.route.orEmpty())
        )
            -1
        else 1
    return if (tab)
        slideInHorizontally(tween(160, easing = FastOutSlowInEasing)) { it / 12 * direction } +
            fadeIn(tween(140))
    else slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { if (pop) -it / 4 else it }
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.pageExit(
    enabled: Boolean,
    pop: Boolean = false,
): ExitTransition {
    if (!enabled || initialState.destination.route == targetState.destination.route)
        return ExitTransition.None
    val tab = tabChange()
    val direction =
        if (
            tab &&
                mainTabs.indexOf(targetState.destination.route.orEmpty()) <
                    mainTabs.indexOf(initialState.destination.route.orEmpty())
        )
            -1
        else 1
    return if (tab)
        slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -it / 12 * direction } +
            fadeOut(tween(120))
    else slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { if (pop) it else -it / 4 }
}

/** Keep outgoing panes opaque and inert while new pages can already receive input. */
@Composable
fun RouteSurface(active: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRouteActive provides active) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .then(if (!active) Modifier.clearAndSetSemantics {} else Modifier)
                .pointerInput(active) {
                    if (!active)
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial)
                                .changes
                                .forEach { it.consume() }
                        }
                }
        ) {
            content()
        }
    }
}
