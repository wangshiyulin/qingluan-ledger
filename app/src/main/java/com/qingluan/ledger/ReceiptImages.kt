package com.qingluan.ledger

import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONTokener

/** Entry.receipt keeps its legacy single-image form; multiple images use a JSON array. */
object ReceiptImages {
    const val MAX_IMAGES = 8
    const val MAX_IMAGE_CHARS = 400000
    const val MAX_TOTAL_CHARS = 2400000
    private const val MAX_IMAGE_BYTES = 300000

    fun hasImages(raw: String): Boolean = decode(raw).isNotEmpty()

    fun validate(raw: String) {
        decode(raw)
    }

    fun decode(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        require(raw.length <= MAX_TOTAL_CHARS) { "票据图片总大小超过限制" }
        val images =
            if (raw.trimStart().startsWith("[")) {
                val array =
                    try {
                        val tokener = JSONTokener(raw)
                        val parsed = tokener.nextValue()
                        require(parsed is JSONArray && tokener.nextClean() == '\u0000')
                        parsed
                    } catch (_: Exception) {
                        throw IllegalArgumentException("票据图片格式无效")
                    }
                require(array.length() in 1..MAX_IMAGES) { "每笔账单最多添加8张票据" }
                List(array.length()) { index ->
                    (array.get(index) as? String) ?: throw IllegalArgumentException("票据图片格式无效")
                }
            } else listOf(raw)
        images.forEach(::validateImage)
        return images
    }

    fun encode(images: List<String>): String {
        require(images.size <= MAX_IMAGES) { "每笔账单最多添加8张票据" }
        require(images.sumOf { it.length.toLong() } <= MAX_TOTAL_CHARS) { "票据图片总大小超过限制" }
        images.forEach(::validateImage)
        val raw =
            when (images.size) {
                0 -> ""
                1 -> images.single()
                else -> JSONArray(images).toString()
            }
        require(raw.length <= MAX_TOTAL_CHARS) { "票据图片总大小超过限制" }
        return raw
    }

    private fun validateImage(image: String) {
        require(image.isNotEmpty() && image.length <= MAX_IMAGE_CHARS) { "票据图片过大或为空" }
        // Bound the encoded input before allocating the decoded bytes. Preserve whitespace in
        // old Base64 receipts, but reject arbitrary characters skipped by Android's decoder.
        require(
            image.all {
                it in 'A'..'Z' ||
                    it in 'a'..'z' ||
                    it in '0'..'9' ||
                    it == '+' ||
                    it == '/' ||
                    it == '=' ||
                    it == '\r' ||
                    it == '\n' ||
                    it == ' ' ||
                    it == '\t'
            }
        ) {
            "票据图片编码无效"
        }
        val bytes =
            try {
                Base64.decode(image, Base64.NO_WRAP)
            } catch (_: Exception) {
                throw IllegalArgumentException("票据图片编码无效")
            }
        require(bytes.size in 1..MAX_IMAGE_BYTES) { "票据图片过大" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024) { "票据图片无效" }
    }
}
