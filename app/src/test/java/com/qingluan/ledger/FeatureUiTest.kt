package com.qingluan.ledger

import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureUiTest {
    @get:Rule(order = 0)
    val clean =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                val context =
                    androidx.test.core.app.ApplicationProvider.getApplicationContext<
                        android.content.Context
                    >()
                runBlocking {
                    val repo = Repository(context)
                    repo.dao.clearRecurring()
                    repo.dao.clearTrash()
                    repo.dao.clear()
                    repo.dao.clearCategories()
                    repo.db.close()
                    context.preferences.updateData {
                        androidx.datastore.preferences.core.emptyPreferences()
                    }
                }
            }
        }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @After
    fun disposeDialogs() {
        compose.runOnUiThread { compose.activity.setContent {} }
        compose.waitForIdle()
    }

    private fun ready(): LedgerViewModel {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(8000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        return vm
    }

    private fun click(label: String) {
        compose
            .onNode(hasClickAction() and (hasText(label) or hasAnyDescendant(hasText(label))))
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
    }

    private fun until(condition: () -> Boolean) {
        compose.waitUntil(5000) {
            compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            condition()
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun archiveRecurringAndQuickIncomeWorkThroughUi() {
        val vm = ready()
        val category = vm.categories.value.first { it.type == "EXPENSE" }
        val id = runBlocking {
            vm.repository.dao.insert(Entry(amount = 999, categoryId = category.id, note = "回收测试"))
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("bill-row-$id").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("bill-row-$id").performTouchInput { longClick() }
        compose.onNodeWithText("删除").performClick()
        compose.onNode(hasText("删除") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("已删除", substring = true)).fetchSemanticsNodes()
            vm.entries.value.none { it.id == id }
        }
        compose.onNodeWithText("我的").performClick()
        click("本地数据")
        click("回收站")
        compose.onNodeWithText("恢复").performClick()
        until { vm.entries.value.any { it.note == "回收测试" } }
        compose.onNodeWithText("回收站为空").assertExists()
        back()
        back()
        runBlocking {
            vm.repository.saveRecurring(
                RecurringBill(name = "周期测试", amount = 500, categoryId = category.id, note = "周期已入账")
            )
        }
        click("周期账单")
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("周期测试").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("确认入账").performClick()
        compose.onNodeWithText("入账").performClick()
        until { vm.entries.value.any { it.note == "周期已入账" } }
        assertTrue(
            runBlocking { vm.repository.dao.allRecurring().single().nextDate } >
                java.time.LocalDate.now().toString()
        )
        runBlocking {
            compose.activity.preferences.edit { it[DesktopEntry.enabledKey] = true }
        }
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    compose.activity,
                    Intent(compose.activity.intent).setAction(DesktopEntry.incomeAction),
                )
        }
        compose.onNode(hasText("收入") and isSelected()).assertExists()
        compose.onNodeWithText("工资").assertExists()
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun advancedSearchAndCsvConsentAreAccessible() {
        val vm = ready()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("高级搜索").assertExists()
        compose.onNode(hasText("最小金额") and hasSetTextAction()).performTextReplacement("100")
        compose.onNodeWithText("应用").performClick()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNode(hasText("最小金额") and hasSetTextAction()).assertTextContains("100")
        compose.onNodeWithText("取消").performClick()
        compose.runOnUiThread {
            compose.activity.setContent {
                androidx.compose.material3.MaterialTheme {
                    ImportDialog(
                        vm,
                        ImportPreview(emptyList(), null, "csv", listOf(CsvRowError(2, "金额无效")), 1),
                    ) {}
                }
            }
        }
        compose.onNode(hasText("仅导入有效行", substring = true)).performScrollTo().assertExists()
    }

    @Test
    fun consecutiveDeletesUndoTheMatchingBatch() {
        val vm = ready()
        val category = vm.categories.value.first { it.type == "EXPENSE" }
        val entries = runBlocking {
            val first =
                vm.repository.dao.insert(
                    Entry(amount = 100, categoryId = category.id, note = "批次甲")
                )
            val second =
                vm.repository.dao.insert(
                    Entry(amount = 200, categoryId = category.id, note = "批次乙")
                )
            listOf(vm.repository.dao.entry(first)!!, vm.repository.dao.entry(second)!!)
        }
        compose.runOnUiThread { vm.deleteEntries(listOf(entries[0])) }
        until { vm.undoBatch.value != null }
        val token = vm.undoBatch.value!!.token
        compose.runOnUiThread { vm.deleteEntries(listOf(entries[1])) }
        until { vm.undoBatch.value?.token != token }
        compose.onNodeWithText("撤销").performClick()
        until { vm.entries.value.any { it.note == "批次乙" } }
        assertTrue(vm.entries.value.none { it.note == "批次甲" })
    }

    @Test
    fun cancelledPredictiveBackKeepsSelection() {
        val vm = ready()
        val category = vm.categories.value.first { it.type == "EXPENSE" }
        val id = runBlocking {
            vm.repository.dao.insert(Entry(amount = 100, categoryId = category.id))
        }
        until { compose.onAllNodesWithTag("bill-row-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("bill-row-$id").performTouchInput { longClick() }
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            dispatcher.dispatchOnBackStarted(
                androidx.activity.BackEventCompat(
                    0f,
                    200f,
                    0f,
                    androidx.activity.BackEventCompat.EDGE_LEFT,
                )
            )
            dispatcher.dispatchOnBackProgressed(
                androidx.activity.BackEventCompat(
                    50f,
                    200f,
                    .5f,
                    androidx.activity.BackEventCompat.EDGE_LEFT,
                )
            )
            dispatcher.dispatchOnBackCancelled()
        }
        compose.onNodeWithContentDescription("退出多选").assertExists()
        compose.runOnUiThread { dispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("退出多选").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w960dp-h600dp-land")
    fun landscapeLargeTextEntryAndDateRemainUsable() {
        val vm = ready()
        vm.customFontPercent(160)
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.appearance.value.customFontPercent == 160
        }
        runBlocking {
            compose.activity.preferences.edit { it[DesktopEntry.enabledKey] = true }
        }
        compose.runOnUiThread {
            InstrumentationRegistry.getInstrumentation()
                .callActivityOnNewIntent(
                    compose.activity,
                    Intent(compose.activity.intent).setAction(DesktopEntry.incomeAction),
                )
        }
        compose.onNodeWithText("工资").performScrollTo().performClick()
        compose.onNodeWithText("7").performScrollTo().performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("7")
        compose.onNodeWithTag("entry-date").performScrollTo().performClick()
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNodeWithText("完成").performScrollTo().performClick()
        until { vm.entries.value.any { it.type == "INCOME" && it.amount == 700L } }
    }
}
