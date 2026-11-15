package com.qingluan.ledger

import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h900dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release115UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun monthSelectionAndCurrencyDisplayStayReadable() {
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    PeriodPicker(LocalDate.of(2026, 11, 1), "month", {}, {})
                }
            }
        }
        compose.onNodeWithTag("period-month-11").assertIsSelected()
        compose.onNodeWithTag("period-month-10").assertIsNotSelected()
        compose.onNodeWithTag("period-month-10").performClick()
        assertEquals("🇨🇳 CNY", currencyLabel("CNY"))
        assertEquals("🇺🇸 USD", currencyLabel("USD"))
        assertEquals("🌐 XAU", currencyLabel("XAU"))
        assertEquals("¥", currencyPrefix("CNY"))
        assertEquals("🇺🇸 USD", currencyPrefix("USD"))
    }

    @Test
    fun categoryPickerKeepsItsBoundsAcrossUnequalTypesAndScrollsBeyondTwelve() {
        val cats =
            (1..18).map { Category(it.toLong(), "支出$it", "EXPENSE", "food") } +
                (101..105).map { Category(it.toLong(), "收入$it", "INCOME", "salary") }
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalEffects provides UiEffects(motion = false)) {
                        EntrySheet({}) {
                            EntryPanel(
                                null,
                                LocalDate.now(),
                                cats,
                                false,
                                onCancel = {},
                                onManage = {},
                                save = {},
                            )
                        }
                    }
                }
            }
        }
        val before = compose.onNodeWithTag("entry-panel").fetchSemanticsNode().boundsInRoot
        val grid = compose.onNodeWithTag("entry-category-grid").fetchSemanticsNode().boundsInRoot
        for (id in 1..12) compose.onNodeWithTag("entry-category-$id").assertIsDisplayed()
        compose.onNodeWithText("收入").performClick()
        val after = compose.onNodeWithTag("entry-panel").fetchSemanticsNode().boundsInRoot
        val incomeGrid =
            compose.onNodeWithTag("entry-category-grid").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.height, after.height, 1f)
        assertEquals(grid.height, incomeGrid.height, 1f)
        for (id in 101..105) compose.onNodeWithTag("entry-category-$id").assertIsDisplayed()
        compose.onNodeWithText("支出").performClick()
        compose.onNodeWithTag("entry-category-grid").performScrollToIndex(17)
        compose.onNodeWithTag("entry-category-18").assertIsDisplayed()
        compose.onNodeWithTag("entry-category-18").performClick()
        compose.onNodeWithText("完成").assertExists()
    }

    @Test
    fun liquidButtonIsSmallerAndOpensEntryWhilePeriodsRemainSelectable() {
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            compose.onAllNodesWithTag("liquid-entry-button").fetchSemanticsNodes().isNotEmpty()
        }
        val bounds = compose.onNodeWithTag("liquid-entry-button").fetchSemanticsNode().boundsInRoot
        assertEquals(68f * compose.activity.resources.displayMetrics.density, bounds.width, 1f)
        compose.onNodeWithTag("liquid-entry-button").performClick()
        compose.waitUntil(10000) { compose.onNodeWithTag("entry-category-grid").isDisplayed() }
        compose.onNodeWithTag("entry-category-grid").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithTag("insight-period-year").performClick().assertIsSelected()
        compose.onNodeWithTag("insight-period-all").performClick().assertIsSelected()
        compose.onNodeWithTag("insight-period-month").performClick().assertIsSelected()
    }
}
