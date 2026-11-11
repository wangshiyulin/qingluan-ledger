package com.qingluan.ledger

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptImagesTest {
    private fun image(width: Int = 2, height: Int = 2): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            val output = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally { bitmap.recycle() }
    }

    @Test fun emptyAndLegacySingleImageRemainCompatible() {
        assertEquals(emptyList<String>(), ReceiptImages.decode(""))
        assertEquals("", ReceiptImages.encode(emptyList()))
        val single = image()
        assertEquals(listOf(single), ReceiptImages.decode(single))
        assertEquals(single, ReceiptImages.encode(listOf(single)))
        validateBillExtras("旧票据", single)
    }

    @Test fun multipleImagesPreserveOrderAndCanRemoveIndividually() {
        val images = listOf(image(), image(3, 4), image(4, 3))
        val encoded = ReceiptImages.encode(images)
        assertTrue(encoded.startsWith("["))
        assertEquals(images, ReceiptImages.decode(encoded))
        assertEquals(listOf(images[0], images[2]), ReceiptImages.decode(ReceiptImages.encode(images.filterIndexed { i, _ -> i != 1 })))
        validateBillExtras("标签", encoded)
    }

    @Test fun rejectsTooManyImagesAndInvalidImages() {
        val valid = image()
        assertEquals(8, ReceiptImages.decode(ReceiptImages.encode(List(8) { valid })).size)
        assertThrows(IllegalArgumentException::class.java) { ReceiptImages.encode(List(9) { valid }) }
        listOf("[]", "[1]", "[null]", "[\"\"]", "[", "not an image", "A".repeat(400001)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { validateBillExtras("", bad) }
        }
        assertThrows(IllegalArgumentException::class.java) { validateBillExtras("", image(1025, 1)) }
        assertThrows(IllegalArgumentException::class.java) { validateBillExtras("x".repeat(301), valid) }
    }

    @Test fun rejectsAggregateLimitBeforeDecodingImages() {
        val tooLarge = "[" + List(7) { "\"${"A".repeat(350000)}\"" }.joinToString(",") + "]"
        assertThrows(IllegalArgumentException::class.java) { ReceiptImages.decode(tooLarge) }
    }
}
