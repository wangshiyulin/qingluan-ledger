package com.qingluan.ledger

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class AppearancePolishTest {
    @Test
    fun lightTintsAreDeeperAndRetainTextContrast() {
        for (key in paletteNames.keys) {
            val seed = paletteColor(key)
            val container = androidx.compose.ui.graphics.lerp(seed, Color.White, .85f)
            val base =
                lightColorScheme(
                    primary = seed,
                    secondary = seed,
                    primaryContainer = container,
                    secondaryContainer = container,
                    onPrimaryContainer = Color.Black,
                    onSecondaryContainer = Color.Black,
                )
            val next = richerLightColors(base)
            assertTrue(key, next.primaryContainer.luminance() < base.primaryContainer.luminance())
            assertTrue(
                key,
                next.secondaryContainer.luminance() < base.secondaryContainer.luminance(),
            )
            assertTrue(key, (next.secondaryContainer.luminance() + .05f) / .05f >= 4.5f)
            val dark = richerDarkColors(readableDarkContainers(darkColorScheme()))
            val contrast =
                (dark.onSecondaryContainer.luminance() + .05f) /
                    (dark.secondaryContainer.luminance() + .05f)
            assertTrue(contrast >= 4.5f)
        }
    }

    @Test
    fun liftedContainersRemainReadableAcrossPalettes() {
        for (key in paletteNames.keys) {
            val primary = androidx.compose.ui.graphics.lerp(paletteColor(key), Color.White, .5f)
            val base = darkColorScheme(primary = primary, secondary = primary)
            val adjusted = readableDarkContainers(base)
            assertTrue(
                key,
                adjusted.secondaryContainer.luminance() > base.surfaceContainerLow.luminance(),
            )
            val text = adjusted.onSecondaryContainer.luminance()
            val background = adjusted.secondaryContainer.luminance()
            assertTrue(
                key,
                (maxOf(text, background) + .05f) / (minOf(text, background) + .05f) >= 4.5f,
            )
        }
    }
}
