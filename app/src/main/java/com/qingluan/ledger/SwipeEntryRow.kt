package com.qingluan.ledger

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlin.math.roundToInt

@Composable
fun SwipeEntryRow(
    entry: Entry,
    categories: List<Category>,
    open: Boolean,
    setOpen: (Boolean) -> Unit,
    edit: () -> Unit,
    delete: () -> Unit,
    longPress: (() -> Unit)? = null,
) {
    val motion = LocalEffects.current.motion
    val feedback = rememberAppFeedback()
    val reveal = with(LocalDensity.current) { 76.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(0f) }
    val position by
        animateFloatAsState(
            if (dragging) offset else if (open) -reveal else 0f,
            if (dragging || !motion) snap() else spring(stiffness = 1200f),
            label = "左滑删除",
        )
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction("删除账单") {
                            delete()
                            true
                        }
                    )
            }
            .pointerInput(reveal, open) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        offset = position
                        dragging = true
                    },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        offset = (offset + delta).coerceIn(-reveal, 0f)
                    },
                    onDragEnd = {
                        val next = offset < -reveal / 2
                        if (next && !open) feedback()
                        setOpen(next)
                        dragging = false
                        if (next) delete()
                    },
                    onDragCancel = { dragging = false },
                )
            }
    ) {
        if (open || position < -1f) {
            Surface(
                Modifier.matchParentSize().padding(vertical = 3.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
            ) {}
            IconButton(
                onClick = delete,
                modifier = Modifier.align(Alignment.CenterEnd).width(76.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    "删除账单",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        Box(Modifier.offset { IntOffset(position.roundToInt(), 0) }) {
            EntryRow(
                entry,
                categories,
                onClick = {
                    setOpen(false)
                    edit()
                },
                onLongClick = longPress,
            )
        }
    }
}
