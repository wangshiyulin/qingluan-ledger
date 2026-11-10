package com.qingluan.ledger

import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        modifier = modifier,
        properties = properties,
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = {
            ApplyDialogGlass()
            title()
        },
        text = text,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f),
    )
}

@Composable
fun ApplyDialogGlass() {
    val view = LocalView.current
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= 31) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val manager = view.context.getSystemService(WindowManager::class.java)
            if (runCatching { manager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)) {
                runCatching {
                        window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        window?.attributes = window?.attributes?.apply { blurBehindRadius = 24 }
                        window?.setBackgroundBlurRadius(24)
                    }
                    .onFailure { window?.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND) }
            }
        }
        onDispose {}
    }
}
