package com.qingluan.ledger

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/** Observe blank taps without consuming clicks, text selection or list scrolling. */
@Composable
fun Modifier.dismissSearchOnBlankTap(
    enabled: Boolean,
    searchBounds: Rect,
    dismiss: () -> Unit,
): Modifier {
    val active by rememberUpdatedState(enabled)
    val bounds by rememberUpdatedState(searchBounds)
    val close by rememberUpdatedState(dismiss)
    val origin = remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { origin.value = it.positionInWindow() }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val outside = active && !bounds.contains(down.position + origin.value)
                var moved = false
                var consumed = false
                while (true) {
                    val change =
                        awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull {
                            it.id == down.id
                        } ?: break
                    moved =
                        moved ||
                            (change.position - down.position).getDistance() >
                                viewConfiguration.touchSlop
                    consumed = consumed || change.isConsumed
                    if (!change.pressed) {
                        if (outside && !moved && !consumed) close()
                        break
                    }
                }
            }
        }
}
