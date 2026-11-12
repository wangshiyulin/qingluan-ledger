package com.qingluan.ledger

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val fontFamilies =
    linkedMapOf(
        "system" to "跟随系统",
        "sans" to "无衬线",
        "serif" to "衬线",
        "mono" to "等宽",
        "custom" to "导入字体",
    )
internal val legacyFontPercents = mapOf("small" to 90, "large" to 115, "extra" to 130)

internal fun normalizeFontSize(mode: String, percent: Int): Pair<String, Int> =
    if (mode in legacyFontPercents) "custom" to legacyFontPercents.getValue(mode)
    else (if (mode == "custom") "custom" else "system") to percent.coerceIn(75, 160)

fun systemFontFamily(key: String): FontFamily =
    when (key) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }

internal object FontFiles {
    const val MAX_BYTES = 32L * 1024 * 1024
    private val idPattern = Regex("[a-f0-9]{32}")

    fun file(context: Context, id: String): File? =
        id.takeIf(idPattern::matches)?.let { File(File(context.filesDir, "fonts"), "$it.font") }

    suspend fun stage(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString().replace("-", "")
            val file = file(context, id)!!
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
            var success = false
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("无法读取字体")
                input.use { source ->
                    file.outputStream().use { output ->
                        var count = 0L
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            count += n
                            require(count <= MAX_BYTES) { "字体文件超过32MiB" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val magic =
                    file.inputStream().use {
                        val bytes = ByteArray(4)
                        require(it.read(bytes) == 4) { "字体文件无效" }
                        bytes
                    }
                require(
                    magic.contentEquals(byteArrayOf(0, 1, 0, 0)) ||
                        magic.toString(Charsets.US_ASCII) in setOf("OTTO", "true")
                ) {
                    "仅支持TTF或OTF字体"
                }
                try {
                    Typeface.createFromFile(file)
                } catch (_: Exception) {
                    error("无法解析字体文件")
                }
                success = true
                id
            } finally {
                if (!success) file.delete()
            }
        }

    suspend fun discard(context: Context, ids: List<String>, keep: String) =
        withContext(Dispatchers.IO) {
            ids.filter { it != keep }.forEach { file(context, it)?.delete() }
        }

    suspend fun prune(context: Context, keep: String) =
        withContext(Dispatchers.IO) {
            File(context.filesDir, "fonts")
                .listFiles()
                ?.filter {
                    it.isFile &&
                        it.extension == "font" &&
                        idPattern.matches(it.nameWithoutExtension) &&
                        it.nameWithoutExtension != keep
                }
                ?.forEach { it.delete() }
        }
}

@Composable
fun rememberAppFont(style: AppAppearance): FontFamily {
    if (style.fontFamily != "custom") return systemFontFamily(style.fontFamily)
    val context = LocalContext.current.applicationContext
    val family by
        produceState<FontFamily>(FontFamily.Default, style.customFontId) {
            value =
                withContext(Dispatchers.IO) {
                    runCatching {
                            FontFamily(
                                Typeface.createFromFile(
                                    FontFiles.file(context, style.customFontId) ?: error("字体不存在")
                                )
                            )
                        }
                        .getOrDefault(FontFamily.Default)
                }
        }
    return family
}
