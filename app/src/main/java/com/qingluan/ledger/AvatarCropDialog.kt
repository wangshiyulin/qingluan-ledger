package com.qingluan.ledger

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AvatarCropDialog(uri: Uri, dismiss: () -> Unit, save: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf("") }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableFloatStateOf(1f) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        try {
            image = decodeAvatar(context, uri)
        } catch (e: Exception) {
            error = e.message ?: "无法读取图片"
        }
    }
    DisposableEffect(image) {
        val old = image
        onDispose { old?.recycle() }
    }
    fun clamp(value: Offset, z: Float): Offset {
        val b = image ?: return Offset.Zero
        val side = minOf(b.width, b.height).toFloat()
        val x = (b.width / side * z - 1) / 2
        val y = (b.height / side * z - 1) / 2
        return Offset(value.x.coerceIn(-x, x), value.y.coerceIn(-y, y))
    }
    GlassAlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text("裁剪头像") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (error.isNotBlank()) Text(error)
                val bitmap = image
                if (bitmap != null) {
                    Box(
                        Modifier.fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .onSizeChanged { size = it.width.toFloat() }
                            .pointerInput(bitmap) {
                                detectTransformGestures { _, delta, scale, _ ->
                                    zoom = (zoom * scale).coerceIn(1f, 4f)
                                    pan = clamp(pan + delta / size, zoom)
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val rect = avatarCropRect(bitmap, zoom, pan.x, pan.y)
                            drawImage(
                                bitmap.asImageBitmap(),
                                srcOffset = androidx.compose.ui.unit.IntOffset(rect.left, rect.top),
                                srcSize =
                                    androidx.compose.ui.unit.IntSize(rect.width(), rect.height()),
                                dstSize =
                                    androidx.compose.ui.unit.IntSize(
                                        this.size.width.toInt(),
                                        this.size.height.toInt(),
                                    ),
                            )
                        }
                    }
                    Slider(
                        zoom,
                        {
                            zoom = it
                            pan = clamp(pan, zoom)
                        },
                        valueRange = 1f..4f,
                    )
                } else if (error.isEmpty()) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(
                enabled = image != null && !saving,
                onClick = {
                    saving = true
                    save(avatarCrop(image!!, zoom, pan.x, pan.y))
                },
            ) {
                Text("使用")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("取消") } },
    )
}
