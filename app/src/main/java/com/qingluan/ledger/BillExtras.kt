package com.qingluan.ledger

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

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
            val scale = (512f / maxOf(image.width, image.height)).coerceAtMost(1f)
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
