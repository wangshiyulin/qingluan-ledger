package com.qingluan.ledger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReceiptSelector(receipt: String, change: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var preview by remember(receipt) { mutableStateOf<Int?>(null) }
    var managing by remember { mutableStateOf(false) }
    val feedback = rememberAppFeedback()
    val currentChange by rememberUpdatedState(change)
    val currentReceipt by rememberUpdatedState(receipt)
    val images by
        produceState<List<String>?>(null, receipt) {
            value = null
            try {
                value = withContext(Dispatchers.IO) { ReceiptImages.decode(receipt) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "无法读取票据"
                value = emptyList()
            }
        }
    val picker =
        rememberImagePicker(
            onImage = { uri ->
                scope.launch {
                    loading = true
                    error = ""
                    try {
                        val image = encodeReceipt(context, uri)
                        val updated =
                            withContext(Dispatchers.IO) {
                                ReceiptImages.encode(ReceiptImages.decode(currentReceipt) + image)
                            }
                        currentChange(updated)
                        managing = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = e.message ?: "无法读取票据"
                        managing = true
                    } finally {
                        loading = false
                    }
                }
            },
            onError = { error = "无法打开系统图片选择器" },
        )
    fun remove(index: Int) {
        scope.launch {
            loading = true
            try {
                val updated =
                    withContext(Dispatchers.IO) {
                        ReceiptImages.encode(
                            ReceiptImages.decode(currentReceipt).filterIndexed { i, _ ->
                                i != index
                            }
                        )
                    }
                currentChange(updated)
                preview = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "无法移除票据"
            } finally {
                loading = false
            }
        }
    }
    fun addImage() {
        feedback()
        try {
            picker()
        } catch (_: Exception) {
            error = "无法打开图片选择器"
            managing = true
        }
    }
    IconButton(
        enabled = !loading && images != null,
        onClick = {
            if (images.orEmpty().isEmpty() && error.isEmpty()) addImage() else managing = true
        },
    ) {
        BadgedBox(
            badge = {
                if (images.orEmpty().isNotEmpty()) Badge { Text(images.orEmpty().size.toString()) }
            }
        ) {
            Icon(
                Icons.Outlined.Image,
                if (images.orEmpty().isEmpty()) "选择票据" else "管理票据，${images.orEmpty().size}张",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    if (managing)
        GlassAlertDialog(
            onDismissRequest = { if (!loading) managing = false },
            title = { Text("票据 ${images.orEmpty().size}/${ReceiptImages.MAX_IMAGES}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(images.orEmpty()) { index, image ->
                            Column {
                                ReceiptImage(
                                    image,
                                    "票据${index + 1}",
                                    Modifier.size(88.dp).clickable(enabled = !loading) {
                                        preview = index
                                    },
                                    thumbnail = true,
                                )
                                TextButton(enabled = !loading, onClick = { remove(index) }) {
                                    Text("移除")
                                }
                            }
                        }
                    }
                    if (error.isNotEmpty())
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    TextButton(
                        enabled = !loading && images.orEmpty().size < ReceiptImages.MAX_IMAGES,
                        onClick = ::addImage,
                    ) {
                        Icon(Icons.Outlined.Image, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("添加票据")
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !loading, onClick = { managing = false }) { Text("完成") }
            },
        )
    val index = preview
    val image = index?.let { images.orEmpty().getOrNull(it) }
    if (index != null && image != null) {
        GlassAlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("票据 ${index + 1}/${images.orEmpty().size}") },
            text = { ReceiptImage(image, "票据图片", Modifier.fillMaxWidth().heightIn(max = 320.dp)) },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("关闭") } },
            dismissButton = {
                TextButton(enabled = !loading, onClick = { remove(index) }) { Text("移除票据") }
            },
        )
    }
}

@Composable
private fun ReceiptImage(
    raw: String,
    description: String,
    modifier: Modifier,
    thumbnail: Boolean = false,
) {
    val bitmap by
        produceState<Bitmap?>(null, raw, thumbnail) {
            var decoded: Bitmap? = null
            value = null
            try {
                withContext(Dispatchers.IO) {
                    val bytes = Base64.decode(raw, Base64.NO_WRAP)
                    val options = BitmapFactory.Options()
                    if (thumbnail) {
                        options.inJustDecodeBounds = true
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        while (
                            maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 192
                        ) {
                            options.inSampleSize *= 2
                        }
                        options.inJustDecodeBounds = false
                    }
                    decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
                value = decoded
                awaitCancellation()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                value = null
            } finally {
                decoded?.recycle()
            }
        }
    val ready = bitmap
    if (ready != null)
        Image(ready.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit)
    else Box(modifier)
}
