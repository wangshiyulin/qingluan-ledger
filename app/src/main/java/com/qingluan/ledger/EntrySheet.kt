package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntrySheet(dismiss: () -> Unit, content: @Composable () -> Unit) {
    if (LocalEffects.current.motion)
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = null,
        ) {
            content()
        }
    else
        Dialog(
            onDismissRequest = dismiss,
            properties =
                DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.matchParentSize()
                        .background(Color.Black.copy(alpha = .3f))
                        .clickable(onClick = dismiss)
                )
                Surface(
                    Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures {} },
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    content()
                }
            }
        }
}
