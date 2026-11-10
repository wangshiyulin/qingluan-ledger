package com.qingluan.ledger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

fun validateBillExtras(tags: String, receipt: String) {
    require(tags.length <= 300) { "标签超过300字" }
    require(receipt.length <= 400000) { "票据图片过大" }
    if (receipt.isNotEmpty()) {
        val bytes = Base64.decode(receipt, Base64.NO_WRAP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) { "票据图片无效" }
    }
}

suspend fun encodeReceipt(context: android.content.Context, uri: android.net.Uri): String =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val image = decodeAvatar(context, uri)
        try {
            val scale = 512f / maxOf(image.width, image.height)
            val resized =
                Bitmap.createScaledBitmap(
                    image,
                    (image.width * scale).toInt().coerceAtLeast(1),
                    (image.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            try {
                val out = java.io.ByteArrayOutputStream()
                check(resized.compress(Bitmap.CompressFormat.JPEG, 78, out))
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP).also {
                    validateBillExtras("", it)
                }
            } finally {
                if (resized !== image) resized.recycle()
            }
        } finally {
            image.recycle()
        }
    }

@Composable
fun BillExtrasDialog(
    tags: String,
    receipt: String,
    note: String,
    common: String,
    save: (String, String, String) -> Unit,
    dismiss: () -> Unit,
) {
    var tag by remember { mutableStateOf(tags) }
    var image by remember { mutableStateOf(receipt) }
    var memo by remember { mutableStateOf(note) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null)
                scope.launch {
                    loading = true
                    try {
                        image = encodeReceipt(context, uri)
                    } catch (e: Exception) {
                        error = e.message ?: "无法读取票据"
                    } finally {
                        loading = false
                    }
                }
        }
    val bitmap =
        remember(image) {
            if (image.isBlank()) null
            else
                runCatching {
                        val bytes = Base64.decode(image, Base64.NO_WRAP)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    }
                    .getOrNull()
        }
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("标签与票据") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(tag, { tag = it.take(300) }, label = { Text("标签（逗号分隔）") })
                OutlinedTextField(memo, { memo = it }, label = { Text("备注") })
                common
                    .lines()
                    .filter { it.isNotBlank() }
                    .take(20)
                    .forEach { value -> TextButton(onClick = { memo = value }) { Text(value) } }
                if (bitmap != null)
                    Image(bitmap.asImageBitmap(), "票据", Modifier.fillMaxWidth().height(160.dp))
                Row {
                    TextButton(
                        enabled = !loading,
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    ) {
                        Text(if (loading) "读取中" else "选择票据")
                    }
                    if (image.isNotEmpty()) TextButton(onClick = { image = "" }) { Text("移除票据") }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !loading,
                onClick = {
                    save(tag, image, memo)
                    dismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
