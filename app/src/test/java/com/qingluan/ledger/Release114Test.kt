package com.qingluan.ledger

import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Release114Test {
    @Test
    fun periodSelectionIsDistinctAndReadableInAllPalettesAndModes() {
        fun contrast(a: Color, b: Color): Float {
            val x = a.luminance()
            val y = b.luminance()
            return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
        }
        val context = RuntimeEnvironment.getApplication()
        for (dark in listOf(false, true)) for (key in paletteNames.keys) {
            val scheme =
                if (key == "system")
                    dynamicBackdrop(
                        if (dark) dynamicDarkColorScheme(context)
                        else dynamicLightColorScheme(context),
                        dark,
                    )
                else ledgerColorScheme(key, dark)
            assertTrue(
                "$key/$dark selected fill",
                contrast(scheme.primary, scheme.surfaceContainer) >= 3f,
            )
            assertTrue(
                "$key/$dark selected text",
                contrast(scheme.onPrimary, scheme.primary) >= 4.5f,
            )
            assertTrue(
                "$key/$dark unselected text",
                contrast(scheme.onSurface, scheme.surfaceContainer) >= 4.5f,
            )
        }
    }
}
