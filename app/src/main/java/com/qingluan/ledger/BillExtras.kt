package com.qingluan.ledger

import android.graphics.Bitmap
import android.util.Base64

fun validateBillExtras(tags: String, receipt: String) {
    require(tags.length <= 300) { "标签超过300字" }
    ReceiptImages.validate(receipt)
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
