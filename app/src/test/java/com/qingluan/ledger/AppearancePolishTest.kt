package com.qingluan.ledger

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AppearancePolishTest {
    @Test
    fun palettesKeepReadableTextInBothModes() {
        for (key in paletteNames.keys - "system") for (dark in listOf(false, true)) {
            val colors = ledgerColorScheme(key, dark)
            fun contrast(
                a: androidx.compose.ui.graphics.Color,
                b: androidx.compose.ui.graphics.Color,
            ): Float {
                val x = a.luminance()
                val y = b.luminance()
                return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
            }
            assertTrue("$key/$dark text", contrast(colors.onSurface, colors.surface) >= 4.5f)
            assertTrue(
                "$key/$dark primary container",
                contrast(colors.onPrimaryContainer, colors.primaryContainer) >= 4.5f,
            )
            assertTrue(
                "$key/$dark secondary container",
                contrast(colors.onSecondaryContainer, colors.secondaryContainer) >= 4.5f,
            )
        }
    }

    @Test
    fun oldSelectionsMapToVisibleChoicesWithoutLosingSystemDefault() {
        assertEquals("system", normalizePalette("system"))
        assertEquals("ocean", normalizePalette("blue"))
        assertEquals("iris", normalizePalette("violet"))
        for (old in legacyPalettes.keys) assertTrue(normalizePalette(old) in paletteNames)
        assertEquals("system", normalizePalette("unknown"))
    }
}
