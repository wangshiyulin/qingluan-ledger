package com.qingluan.ledger

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

/** Commit only on a completed gesture; cancellation leaves the current selection intact. */
@Composable
fun rememberPredictiveDismiss(enabled: Boolean, dismiss: () -> Unit): Float {
    var progress by remember { mutableFloatStateOf(0f) }
    val action by rememberUpdatedState(dismiss)
    val motion = motionAllowed(LocalEffects.current)
    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { progress = if (motion) it.progress else 0f }
            action()
        } catch (_: CancellationException) {
            // A cancelled back gesture must not dismiss the current state.
        } finally {
            progress = 0f
        }
    }
    return progress
}
