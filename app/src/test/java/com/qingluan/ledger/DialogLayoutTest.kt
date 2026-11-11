package com.qingluan.ledger

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class DialogLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun budgetSelectorsAlignAndCategoryGridHasThreeColumns() {
        val vm = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.categories.value.any { it.type == "EXPENSE" }
        }
        val cats = vm.categories.value
        compose.runOnUiThread {
            compose.activity.setContent { MaterialTheme { BudgetPage(vm, cats) {} } }
        }
        compose.onNodeWithText("新增").performClick()
        val currency = compose.onNodeWithText("CNY").fetchSemanticsNode().boundsInRoot
        val total = compose.onNodeWithText("总预算").fetchSemanticsNode().boundsInRoot
        assertEquals(currency.center.y, total.center.y, 1f)
        assertTrue(total.left > currency.left)
        compose.onNodeWithText("总预算").performClick()
        compose.onNode(hasText("总预算") and isSelected()).assertExists()
        val first =
            compose.onNode(hasText("总预算") and isSelected()).fetchSemanticsNode().boundsInRoot
        val expenses = cats.filter { it.type == "EXPENSE" }
        val second = compose.onNodeWithText(expenses[0].name).fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText(expenses[1].name).fetchSemanticsNode().boundsInRoot
        assertEquals(first.center.y, second.center.y, 1f)
        assertEquals(second.center.y, third.center.y, 1f)
        assertTrue(first.left < second.left && second.left < third.left)
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    AdvancedSearchDialog(
                        vm,
                        cats,
                        BillSearch(from = LocalDate.now(), to = LocalDate.now()),
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithText("限制日期范围").assertExists()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasText("票据")), useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("有票据").assertExists()
        compose.onNodeWithText("无票据").assertExists()
        // Dispose modal roots before ActivityScenario tears down the synthetic window.
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }
}
