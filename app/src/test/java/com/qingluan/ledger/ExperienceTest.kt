package com.qingluan.ledger

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExperienceTest {
    @Test
    fun preferencesRoundTripAndCropMoves() {
        val e =
            Experience(
                density = "comfortable",
                showTime = false,
                hideAmounts = true,
                hapticStrength = "firm",
            )
        assertEquals(e, Experience.decode(e.json()))
        assertEquals("••••", visibleMoney(200, "CNY", true))
        val image = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.RED)
        for (x in 512 until 1024) for (y in 0 until 512) image.setPixel(x, y, Color.BLUE)
        val left = avatarCrop(image, 1f, .5f, 0f)
        val right = avatarCrop(image, 1f, -.5f, 0f)
        assertEquals(Color.RED, left.getPixel(256, 256))
        assertEquals(Color.BLUE, right.getPixel(256, 256))
        left.recycle()
        right.recycle()
        image.recycle()
    }
}
