package com.qingluan.ledger

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ReceiptSelector(receipt: String, change: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf(false) }
    val feedback = rememberAppFeedback()
    val currentChange by rememberUpdatedState(change)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    loading = true
                    error = ""
                    try {
                        currentChange(encodeReceipt(context, uri))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "无法读取票据"
                    } finally {
                        loading = false
                    }
                }
        }
    Column {
        Row {
            TextButton(
                enabled = !loading,
                onClick = {
                    feedback()
                    try {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    } catch (_: Exception) {
                        error = "无法打开图片选择器"
                    }
                },
            ) {
                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (loading) "读取中" else "选择票据")
            }
            if (receipt.isNotEmpty()) TextButton(onClick = { preview = true }) { Text("查看票据") }
        }
        if (error.isNotEmpty())
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
    if (preview) {
        val bitmap =
            remember(receipt) {
                runCatching {
                        val bytes = Base64.decode(receipt, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    .getOrNull()
            }
        DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
        GlassAlertDialog(
            onDismissRequest = { preview = false },
            title = { Text("票据") },
            text = {
                if (bitmap != null)
                    Image(
                        bitmap.asImageBitmap(),
                        "票据图片",
                        Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    )
            },
            confirmButton = { TextButton(onClick = { preview = false }) { Text("关闭") } },
            dismissButton = {
                TextButton(
                    onClick = {
                        currentChange("")
                        preview = false
                    }
                ) {
                    Text("移除票据")
                }
            },
        )
    }
}
