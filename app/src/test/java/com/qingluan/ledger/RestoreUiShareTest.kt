package com.qingluan.ledger

import android.graphics.BitmapFactory
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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
class RestoreUiShareTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun categoryPickerUsesThreeEqualColumnsAndHighlightsSelection() {
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    ConfigChoice(
                        "来源分类",
                        "1",
                        linkedMapOf("1" to "餐饮", "2" to "购物", "3" to "交通", "4" to "居住"),
                        columns = 3,
                    ) {}
                }
            }
        }
        compose.onNodeWithText("来源分类").performClick()
        val food =
            compose
                .onNode(hasText("餐饮") and hasAnyAncestor(isDialog()))
                .fetchSemanticsNode()
                .boundsInRoot
        val shopping =
            compose
                .onNode(hasText("购物") and hasAnyAncestor(isDialog()))
                .fetchSemanticsNode()
                .boundsInRoot
        val transport =
            compose
                .onNode(hasText("交通") and hasAnyAncestor(isDialog()))
                .fetchSemanticsNode()
                .boundsInRoot
        val home =
            compose
                .onNode(hasText("居住") and hasAnyAncestor(isDialog()))
                .fetchSemanticsNode()
                .boundsInRoot
        assertEquals(food.center.y, shopping.center.y, 1f)
        assertEquals(food.center.y, transport.center.y, 1f)
        assertTrue(home.center.y > food.center.y)
        compose.onNode(hasText("餐饮") and isSelected()).assertExists()
    }

    @Test
    fun restorePreviewRendersAndOffersExplicitMergeConfirmation() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(10000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val categories = vm.categories.value
        val entry =
            Entry(id = 101, amount = 100, categoryId = categories.first { it.type == "EXPENSE" }.id)
        val raw = CompleteBackup.encode(categories, listOf(entry), PersonalBackup())
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme { RestoreReviewDialog(vm, raw, dismiss = {}, restored = {}) }
            }
        }
        compose.waitUntil(10000) {
            compose
                .onAllNodesWithText("新增账单 1 笔", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("继续").assertIsEnabled().performClick()
        compose.onNodeWithText("确认合并恢复？").assertExists()
        compose.onNodeWithText("合并恢复").assertIsEnabled()
    }

    @Test
    fun shareCreatesReadablePngInGrantedProviderDirectory() = runBlocking {
        val context = compose.activity
        val report = annualReport(emptyList(), LocalDate.now().year, "CNY")
        val uri = annualReportImage(context, report, emptyMap(), false, true, 0xFF379D98.toInt())
        assertEquals("content", uri.scheme)
        val bitmap =
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1900, bitmap.height)
        java.io
            .File("build/reports/annual-share-preview.png")
            .apply { parentFile!!.mkdirs() }
            .outputStream()
            .use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
