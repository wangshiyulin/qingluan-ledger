package com.qingluan.ledger

import android.graphics.BitmapFactory
import android.graphics.Color
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompatibilityTest {
    @Test
    fun recordedDaysAndCurrencyOrder() {
        val d = LocalDate.of(2026, 11, 1)
        fun at(n: Long) =
            d.plusDays(n).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val rows =
            listOf(
                Entry(amount = 100, categoryId = 1, occurredAt = at(0)),
                Entry(amount = 200, categoryId = 1, occurredAt = at(0)),
                Entry(type = "INCOME", amount = 500, categoryId = 2, occurredAt = at(3)),
            )
        assertEquals(150L, recordedDayMean(300, rows))
        assertEquals(0L, recordedDayMean(0, emptyList()))
        assertEquals(
            listOf("CNY", "USD", "HKD", "EUR", "JPY", "GBP"),
            currencyDisplayOrder(emptyMap()).take(6).map { it.code },
        )
        assertEquals(
            listOf("EUR", "CNY", "USD"),
            currencyDisplayOrder(mapOf("EUR" to 9, "CNY" to 3, "USD" to 1)).take(3).map { it.code },
        )
        assertEquals(178, currencyDisplayOrder(emptyMap()).map { it.code }.distinct().size)
    }

    @Test
    fun physicalRoundedIconHasTransparentCorners() {
        val resources = RuntimeEnvironment.getApplication().resources
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.brand_rounded)
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertEquals(0, Color.alpha(bitmap.getPixel(bitmap.width - 1, bitmap.height - 1)))
        assertEquals(255, Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)))
        bitmap.recycle()
    }
}
