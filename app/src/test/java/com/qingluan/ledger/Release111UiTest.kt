package com.qingluan.ledger

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release111UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        compose
            .onNode(
                hasClickAction() and (hasText(label) or hasAnyDescendant(hasText(label))),
                useUnmergedTree = true,
            )
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    @Test
    fun fontChoicesProfileBadgeAndAnnualReportNavigation() {
        val vm = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        click("我的")
        compose.onNodeWithTag("profile-edit-badge", useUnmergedTree = true).assertExists()
        click("个性化")
        click("字体")
        compose.onNodeWithText("小").assertDoesNotExist()
        compose.onNodeWithText("大").assertDoesNotExist()
        compose.onNodeWithText("特大").assertDoesNotExist()
        val system =
            compose
                .onNode(
                    hasText("跟随系统") and hasAnyAncestor(hasTestTag("font-size-modes")),
                    useUnmergedTree = true,
                )
                .fetchSemanticsNode()
                .boundsInRoot
        val custom = compose.onNodeWithText("自定义").fetchSemanticsNode().boundsInRoot
        assertEquals(system.center.y, custom.center.y, 1f)
        click("自定义")
        compose.onNodeWithTag("font-custom-percent").performScrollTo().performTextReplacement("125")
        click("字形")
        click("衬线")
        compose.onNode(hasText("完成") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.appearance.value.fontSize == "custom" &&
                vm.appearance.value.customFontPercent == 125 &&
                vm.appearance.value.fontFamily == "serif"
        }
        compose.onNodeWithContentDescription("返回").performClick()
        click("洞察")
        compose.onNodeWithContentDescription("更多").performClick()
        click("年度报告")
        compose.onNodeWithTag("annual-report-page").assertExists()
        compose.waitUntil(10000) {
            compose.onAllNodesWithText("年度结余").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("我的").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("洞察").assertExists()
    }

    @Test
    fun calendarDatesHaveCompactSquareSpacing() {
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    androidx.compose.foundation.layout.Box(Modifier.width(350.dp)) {
                        CalendarMonth(LocalDate.of(2026, 11, 1), emptyList()) {}
                    }
                }
            }
        }
        val two = compose.onNodeWithTag("calendar-day-2").fetchSemanticsNode().boundsInRoot
        val three = compose.onNodeWithTag("calendar-day-3").fetchSemanticsNode().boundsInRoot
        val nine = compose.onNodeWithTag("calendar-day-9").fetchSemanticsNode().boundsInRoot
        assertEquals(three.center.x - two.center.x, nine.center.y - two.center.y, 1f)
        assertTrue(two.height <= two.width + 1f)
    }
}
