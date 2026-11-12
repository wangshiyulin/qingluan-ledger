package com.qingluan.ledger

import android.net.Uri
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.datastore.preferences.core.*
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Release111LogicTest {
    private fun at(year: Int, month: Int, day: Int) =
        LocalDate.of(year, month, day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun annualReportSeparatesYearCurrencyAndMonthlyTotals() {
        val rows =
            listOf(
                Entry(id = 1, amount = 100, categoryId = 1, occurredAt = at(2026, 1, 1)),
                Entry(id = 2, amount = 200, categoryId = 1, occurredAt = at(2026, 1, 1)),
                Entry(
                    id = 3,
                    type = "INCOME",
                    amount = 1000,
                    categoryId = 2,
                    occurredAt = at(2026, 1, 2),
                ),
                Entry(id = 4, amount = 400, categoryId = 1, occurredAt = at(2026, 2, 3)),
                Entry(id = 5, amount = 9000, categoryId = 1, occurredAt = at(2025, 1, 1)),
                Entry(
                    id = 6,
                    amount = 8000,
                    categoryId = 1,
                    currencyCode = "USD",
                    occurredAt = at(2026, 1, 1),
                ),
            )
        val report = annualReport(rows, 2026, "CNY", ZoneOffset.UTC)
        assertEquals(Totals(1000, 700), report.total)
        assertEquals(4, report.count)
        assertEquals(3, report.days)
        assertEquals(12, report.months.size)
        assertEquals(AnnualMonth(1, 1000, 300), report.months[0])
        assertEquals(2, report.peakMonth!!.month)
        assertEquals(listOf(1L to 700L), report.categories)
        assertEquals(listOf(4L, 2L, 1L), report.largest.map { it.id })
        val empty = annualReport(rows, 2027, "CNY", ZoneOffset.UTC)
        assertEquals(Totals(0, 0), empty.total)
        assertNull(empty.peakMonth)
    }

    @Test
    fun fontPresetsMigrateAndDeviceFontFilesAreNotExported() = runBlocking {
        assertEquals(setOf("system", "custom"), fontSizes.keys)
        for ((old, percent) in legacyFontPercents) {
            val prefs = mutablePreferencesOf(stringPreferencesKey("fontSize") to old)
            val style = appearanceFromPreferences(prefs)
            assertEquals("custom", style.fontSize)
            assertEquals(percent, style.customFontPercent)
        }
        val cats = listOf(Category(1, "餐饮", "EXPENSE"), Category(2, "工资", "INCOME"))
        val serif =
            AppAppearance(fontSize = "custom", customFontPercent = 125, fontFamily = "serif")
        val raw = CompleteBackup.encode(cats, emptyList(), PersonalBackup(appearance = serif))
        assertEquals(serif, CompleteBackup.decode(raw).personal!!.appearance)
        val legacy = JSONObject(raw)
        legacy.getJSONObject("personal").remove("fontFamily")
        legacy.getJSONObject("personal").put("fontSize", "extra")
        val migrated = CompleteBackup.decode(legacy.toString()).personal!!.appearance
        assertEquals("system", migrated.fontFamily)
        assertEquals(130, migrated.customFontPercent)
        val local = serif.copy(fontFamily = "custom", customFontId = "a".repeat(32))
        val portable = CompleteBackup.encode(cats, emptyList(), PersonalBackup(appearance = local))
        assertEquals("system", CompleteBackup.decode(portable).personal!!.appearance.fontFamily)
        assertFalse(portable.contains("customFontId"))
        val context = RuntimeEnvironment.getApplication()
        val bad =
            java.io.File(context.cacheDir, "invalid-font.txt").apply { writeText("not a font") }
        assertTrue(runCatching { FontFiles.stage(context, Uri.fromFile(bad)) }.isFailure)
        assertTrue(java.io.File(context.filesDir, "fonts").listFiles().orEmpty().isEmpty())
        assertNull(FontFiles.file(context, "../../avatar.jpg"))
    }

    @Test
    fun darkThemesLiftCardsWhileKeepingContrastAndBlackBackground() {
        for (key in paletteNames.keys) {
            val primary = lerp(paletteColor(key), Color.White, .5f)
            val base =
                darkColorScheme(
                    primary = primary,
                    secondary = primary,
                    background = Color.Black,
                    surface = Color.Black,
                )
            val next = richerDarkColors(base)
            assertTrue(
                key,
                next.surfaceContainerLow.luminance() > base.surfaceContainerLow.luminance(),
            )
            assertEquals(Color.Black, next.background)
            val fg = next.onSecondaryContainer.luminance()
            val bg = next.secondaryContainer.luminance()
            assertTrue(key, (maxOf(fg, bg) + .05f) / (minOf(fg, bg) + .05f) >= 4.5f)
        }
    }
}
