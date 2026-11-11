package com.qingluan.ledger

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class XlsxTransferTest {
    private val category = Category(id = 1, name = "'=分类<&", type = "EXPENSE")
    private val entry = Entry(amount = 123, categoryId = 1, occurredAt = 1700000000000,
        note = "'=literal & < >\r\n_x0041_\u0001", tags = "'=标签")

    @Test fun textRoundTripPreservesLiteralFormulaPrefixesAndXmlEscapes() {
        val bytes = XlsxTransfer.export(listOf(entry), listOf(category))
        assertEquals('P'.code.toByte(), bytes[0])
        val result = XlsxTransfer.preview(bytes)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(entry.note, result.bills.single().note)
        assertEquals(entry.tags, result.bills.single().tags)
        assertEquals(category.name, result.bills.single().category)
        assertEquals(entry.occurredAt, result.bills.single().occurredAt)
    }

    @Test fun ordinaryAmountsAreNumericAndLargePreciseAmountsRemainText() {
        val ordinary = entry.copy(id = 2)
        val large = entry.copy(id = 1, amount = Long.MAX_VALUE)
        val bytes = XlsxTransfer.export(listOf(ordinary, large), listOf(category))
        var worksheet = ""
        rewrite(bytes) { name, text ->
            if (name == "xl/worksheets/sheet1.xml") worksheet = text
            text
        }
        assertTrue(worksheet.contains("<c r=\"F2\" t=\"n\"><v>1.23</v></c>"))
        assertTrue(worksheet.contains("<c r=\"F3\" t=\"inlineStr\">"))
        val result = XlsxTransfer.preview(bytes)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(listOf(123L, Long.MAX_VALUE), result.bills.map { it.amount })
    }

    @Test fun receiptsLargerThanAnExcelCellRoundTripThroughChunks() {
        val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(42)
        bitmap.setPixels(IntArray(128 * 128) { random.nextInt() or 0xff000000.toInt() }, 0, 128, 0, 0, 128, 128)
        val image = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, image))
        bitmap.recycle()
        val encoded = Base64.encodeToString(image.toByteArray(), Base64.NO_WRAP)
        val raw = ReceiptImages.encode(listOf(encoded, encoded))
        assertTrue(raw.length > 32767)
        val bytes = XlsxTransfer.export(listOf(entry.copy(receipt = raw)), listOf(category))
        val result = XlsxTransfer.preview(bytes)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
        assertEquals(raw, result.bills.single().receipt)
        val broken = rewrite(bytes) { name, text -> if (name == "xl/worksheets/sheet2.xml") text.replace("<row r=\"2\">", "<row r=\"3\">") else text }
        assertTrue(runCatching { XlsxTransfer.preview(broken) }.isFailure)
    }

    @Test fun numericExcelDateAndTimeAreConvertedForBothEpochs() {
        for (epoch1904 in listOf(false, true)) {
            val day = LocalDate.of(2024, 1, 2)
            val epoch = if (epoch1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 30)
            val serial = java.time.temporal.ChronoUnit.DAYS.between(epoch, day)
            val bytes = rewrite(XlsxTransfer.export(listOf(entry), listOf(category))) { name, text -> when (name) {
                "xl/workbook.xml" -> if (epoch1904) text.replace("<sheets>", "<workbookPr date1904=\"1\"/><sheets>") else text
                "xl/worksheets/sheet1.xml" -> text.replace(Regex("<c r=\"A2\".*?</c>"), "<c r=\"A2\"><v>$serial</v></c>")
                    .replace(Regex("<c r=\"B2\".*?</c>"), "<c r=\"B2\"><v>0.5</v></c>")
                else -> text
            } }
            val result = XlsxTransfer.preview(bytes)
            assertTrue(result.errors.toString(), result.errors.isEmpty())
            assertEquals(day.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), result.bills.single().occurredAt)
        }
    }

    @Test fun rejectsFormulaExternalRelationshipAndDtd() {
        val original = XlsxTransfer.export(listOf(entry), listOf(category))
        val formula = rewrite(original) { name, text -> if (name.endsWith("sheet1.xml")) text.replace("<is>", "<f>1+1</f><is>") else text }
        assertTrue(runCatching { XlsxTransfer.preview(formula) }.isFailure)
        val external = rewrite(original) { name, text -> if (name.endsWith("workbook.xml.rels")) text.replace("Target=", "TargetMode=\"External\" Target=") else text }
        assertTrue(runCatching { XlsxTransfer.preview(external) }.isFailure)
        val dtd = rewrite(original) { name, text -> if (name.endsWith("sheet1.xml")) "<!DOCTYPE worksheet [<!ENTITY unsafe SYSTEM 'file:///etc/passwd'>]>$text" else text }
        assertTrue(runCatching { XlsxTransfer.preview(dtd) }.isFailure)
    }

    @Test fun omittedOptionalColumnsStayEmpty() {
        val preview = XlsxTransfer.preview(XlsxTransfer.export(listOf(entry), listOf(category), false, false, false))
        assertTrue(preview.errors.isEmpty())
        assertEquals("", preview.bills.single().note)
        assertEquals("", preview.bills.single().tags)
        assertEquals("", preview.bills.single().receipt)
    }

    private fun rewrite(bytes: ByteArray, transform: (String, String) -> String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { output ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val text = input.readBytes().toString(Charsets.UTF_8)
                    output.putNextEntry(ZipEntry(entry.name))
                    output.write(transform(entry.name, text).toByteArray(Charsets.UTF_8))
                    output.closeEntry()
                }
            }
        }
        return out.toByteArray()
    }
}
