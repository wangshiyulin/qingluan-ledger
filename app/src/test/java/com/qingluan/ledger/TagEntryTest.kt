package com.qingluan.ledger

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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
class TagEntryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun categoryScopedTagIsOnlySavedWithCompletedBill() {
        val vm = ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        var saved: Entry? = null
        val cats = listOf(Category(1, "测试分类", "EXPENSE"), Category(2, "另一分类", "EXPENSE"))
        val catalog =
            listOf(
                ManagedTag(categoryId = 1, name = "午餐"),
                ManagedTag(categoryId = 2, name = "其他标签"),
            )
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalTagCatalog provides catalog) {
                        EntryPanel(
                            null,
                            LocalDate.now(),
                            cats,
                            false,
                            onCancel = {},
                            onManage = {},
                            save = { saved = it },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("测试分类").performClick()
        compose.onNodeWithText("午餐").assertExists().performClick()
        compose.onNodeWithText("其他标签").assertDoesNotExist()
        assertNull(saved)
        compose.onNodeWithText("5").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.runOnIdle {
            assertEquals("午餐", saved!!.tags)
            assertEquals(500L, saved!!.amount)
        }
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }
}
