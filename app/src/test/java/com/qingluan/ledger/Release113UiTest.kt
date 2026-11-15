package com.qingluan.ledger

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
class Release113UiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (text in listOf("明细", "洞察", "我的")) node.performClick()
        else node.performScrollTo().performClick()
    }

    @Test
    fun groupedMenusPaletteMigrationAndNavigationDragWork() {
        val vm = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        click("我的")
        val personal = compose.onNodeWithText("个性化").fetchSemanticsNode().boundsInRoot
        val categories = compose.onNodeWithText("分类管理").fetchSemanticsNode().boundsInRoot
        val tags = compose.onNodeWithText("标签管理").fetchSemanticsNode().boundsInRoot
        assertTrue(personal.top < categories.top && categories.top < tags.top)
        assertTrue(compose.onAllNodesWithTag("settings-group").fetchSemanticsNodes().size >= 3)
        click("数据管理")
        compose.onNodeWithText("分类管理").assertDoesNotExist()
        compose.onNodeWithText("标签管理").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        click("个性化")
        click("配色")
        compose.onNodeWithTag("palette-system").assertExists()
        compose.onNodeWithTag("palette-slate").performScrollTo().performClick()
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.appearance.value.palette == "slate"
        }
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("transparent-navigation").performTouchInput {
            swipe(
                Offset(width.toFloat() * 5f / 6f, height.toFloat() / 2),
                Offset(width.toFloat() / 6f, height.toFloat() / 2),
            )
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithContentDescription("搜索").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("明细") and isSelected()).assertExists()
        // The same tab remains clickable after a drag or cancellation.
        click("洞察")
        compose.onNode(hasText("洞察") and isSelected()).assertExists()
    }
}
