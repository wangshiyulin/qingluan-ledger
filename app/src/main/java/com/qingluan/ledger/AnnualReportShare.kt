package com.qingluan.ledger

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A separate share card deliberately excludes individual bills, notes and receipts. */
internal suspend fun annualReportImage(
    context: Context,
    data: AnnualReportData,
    categories: Map<Long, Category>,
    showAmounts: Boolean,
    showCategories: Boolean,
    accent: Int,
): android.net.Uri =
    withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "reports").apply { check(mkdirs() || isDirectory) }
        // Do not remove an image a receiving application may still be opening.
        directory
            .listFiles()
            ?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86400000L }
            ?.forEach { it.delete() }
        val bitmap =
            Bitmap.createBitmap(1080, if (showCategories) 1900 else 1540, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("annual-${data.year}-", ".png", directory)
        try {
            val ink =
                if (androidx.core.graphics.ColorUtils.calculateLuminance(accent) > .45)
                    androidx.core.graphics.ColorUtils.blendARGB(accent, 0xFF1F2937.toInt(), .45f)
                else accent
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFF8FAFC.toInt())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            fun text(
                value: String,
                x: Float,
                y: Float,
                size: Float = 30f,
                color: Int = 0xFF1F2937.toInt(),
                width: Float = 900f,
                bold: Boolean = false,
            ) {
                paint.color = color
                paint.textSize = size
                paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val count = paint.breakText(value, true, width, null)
                val label =
                    if (count < value.length) value.take((count - 1).coerceAtLeast(0)) + "…"
                    else value
                canvas.drawText(label, x, y, paint)
            }
            fun money(value: Long) = if (showAmounts) Money.text(value, data.currency) else "••••"
            fun card(top: Float, bottom: Float) {
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawRoundRect(48f, top, 1032f, bottom, 28f, 28f, paint)
            }
            text("青鸾记账 · 年度报告", 72f, 98f, 32f, ink)
            text("${data.year} 年", 72f, 188f, 66f, bold = true)
            text(data.currency, 820f, 180f, 30f, ink, 190f)
            card(240f, 550f)
            text("年度结余", 80f, 300f, 30f)
            text(money(data.total.balance), 80f, 382f, 58f, ink, bold = true)
            text("全年收入", 80f, 452f, 26f)
            text("全年支出", 566f, 452f, 26f)
            text(money(data.total.income), 80f, 505f, 36f, width = 440f)
            text(money(data.total.expense), 566f, 505f, 36f, width = 430f)
            text("记账 ${data.days} 天  ·  共 ${data.count} 笔", 72f, 620f, 30f)
            card(665f, 1430f)
            text("月度收支", 80f, 726f, 34f, bold = true)
            text("收入", 380f, 726f, 26f)
            text("支出", 710f, 726f, 26f)
            data.months.forEachIndexed { index, month ->
                val y = 790f + index * 53f
                text("${month.month}月", 80f, y, 27f)
                text(money(month.income), 380f, y, 27f, width = 300f)
                text(money(month.expense), 710f, y, 27f, width = 290f)
            }
            if (showCategories) {
                card(1470f, 1790f)
                text("支出分类 · 前五", 80f, 1525f, 32f, bold = true)
                if (data.categories.isEmpty()) text("暂无", 80f, 1580f, 28f)
                data.categories.take(5).forEachIndexed { index, (id, amount) ->
                    val y = 1576f + index * 44f
                    text(categories[id]?.name ?: "分类", 80f, y, 27f, width = 540f)
                    text(money(amount), 710f, y, 27f, width = 290f)
                }
            }
            text("ledger.qingluanx.com", 72f, bitmap.height - 44f, 24f, 0xFF64748B.toInt())
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        } catch (e: Throwable) {
            file.delete()
            throw e
        } finally {
            bitmap.recycle()
        }
    }

internal fun shareAnnualReport(context: Context, uri: android.net.Uri) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("年度报告", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(intent, "分享年度报告"))
}
