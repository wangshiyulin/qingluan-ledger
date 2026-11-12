package com.qingluan.ledger

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Release110NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun click(label: String) {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        compose
            .onNode(
                hasClickAction() and (hasText(label) or hasAnyDescendant(hasText(label))),
                useUnmergedTree = true,
            )
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    @Test
    fun groupedSettingsOpenTagOwnerPickerWithAllIncomeAndExpense() {
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            compose.onAllNodesWithText("暂无账单").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("我的").performClick()
        click("分类与标签")
        click("标签管理")
        compose.onNodeWithText("所有分类").performClick()
        compose.onNode(hasText("所有") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasTextExactly("支出") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasTextExactly("收入") and hasAnyAncestor(isDialog())).performClick()
        compose.onNode(hasText("工资") and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText("餐饮") and hasAnyAncestor(isDialog())).assertDoesNotExist()
        compose.onNode(hasText("取消") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        click("分类管理")
        compose.onNodeWithContentDescription("标签管理").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("统计与预算").assertDoesNotExist()
        compose.onNodeWithText("本地数据").assertDoesNotExist()
        click("账单管理")
        compose.onNodeWithText("周期账单").assertExists()
        compose.onNodeWithText("分期账单").assertExists()
        compose.onNodeWithText("预算管理").assertExists()
        compose.onNodeWithText("标签统计").assertExists()
        click("列表显示")
        compose.onNodeWithText("显示标签").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        click("个性化")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("统计偏好"))
        compose.onNodeWithText("统计偏好").assertExists()
        compose.onNodeWithText("列表显示").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        click("数据管理")
        click("同步")
        click("WebDAV")
        compose.onNodeWithText("WebDAV 连接").assertExists()
    }
}
