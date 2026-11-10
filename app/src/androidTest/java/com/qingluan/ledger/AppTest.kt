package com.qingluan.ledger

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.*

class AppTest {
    @get:Rule(order = 0)
    val cleanState =
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                val context =
                    androidx.test.core.app.ApplicationProvider.getApplicationContext<
                        android.content.Context
                    >()
                kotlinx.coroutines.runBlocking {
                    val repository = Repository(context)
                    repository.dao.clear()
                    repository.db.close()
                }
                kotlinx.coroutines.runBlocking {
                    context.preferences.updateData {
                        androidx.datastore.preferences.core.emptyPreferences()
                    }
                }
            }
        }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun compactPagesAndSettingsNavigation() {
        compose.onNodeWithText("青鸾记账").assertExists()
        compose.onNodeWithText("暂无账单").assertExists()
        compose.onNodeWithText("把日子，记得清楚").assertDoesNotExist()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("分类管理").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("餐饮").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithTag("entry-note").performTextInput("午饭")
        compose.onNode(hasText("支出") and hasClickAction()).performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("餐饮").performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("2")
        compose.onNodeWithText("午饭").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("每一笔，都有迹可循").assertDoesNotExist()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("浅色模式").assertDoesNotExist()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("外观模式").performClick()
        compose.onNodeWithText("浅色模式").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导出").assertExists()
    }

    @Test
    fun popupEntryCrud() {
        compose.onNodeWithContentDescription("记账").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("餐饮").performClick()
        listOf("1", "2", ".", "3", "4").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("−12.34").fetchSemanticsNodes().isNotEmpty()
        }
        dismissSnackbar()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("总支出").assertExists()
        compose.onNodeWithText("对比").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("分类排行"))
        compose.onNodeWithText("分类排行").assertExists()
        compose.onNodeWithContentDescription("分类占比").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("总计"))
        compose.onNodeWithText("总计").assertExists()
        compose.onNodeWithContentDescription("分类列表").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("100.0%"))
        compose.onNodeWithText("100.0%").assertExists()
        compose.onNodeWithText("明细").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("−12.34"))
        compose.onNodeWithText("−12.34").performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("12.34")
        compose.onNodeWithContentDescription("删除一位").performTouchInput { longClick() }
        listOf("5", "6", ".", "7", "8").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("−56.78").fetchSemanticsNodes().isNotEmpty()
        }
        dismissSnackbar()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("−56.78"))
        compose.onNodeWithText("−56.78").performTouchInput { swipeLeft() }
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("暂无账单").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun categoryGridDragPersistsAndMenuOpens() {
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("餐饮").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(width.toFloat(), 0f), 100)
            up()
        }
        compose.waitUntil(5000) {
            compose.onNodeWithContentDescription("餐饮").fetchSemanticsNode().boundsInRoot.left >
                compose.onNodeWithContentDescription("购物").fetchSemanticsNode().boundsInRoot.left
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.waitUntil(5000) {
            compose.onNodeWithContentDescription("餐饮").fetchSemanticsNode().boundsInRoot.left >
                compose.onNodeWithContentDescription("购物").fetchSemanticsNode().boundsInRoot.left
        }
        compose.onNodeWithContentDescription("餐饮").performClick()
        compose.onNodeWithText("编辑").assertExists()
        compose.onNodeWithText("删除").assertExists()
    }

    @Test
    fun filterAndMigrationOptionsUseGrids() {
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNode(hasText("支出") and hasClickAction()).performClick()
        compose.onNodeWithText("购物").assertExists()
        val a = compose.onNodeWithText("餐饮").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithText("购物").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(a.top - b.top) < 4f)
        compose.onNodeWithText("重置").performClick()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("餐饮").performClick()
        compose.onNodeWithText("删除").performClick()
        val shopping = compose.onAllNodesWithText("购物").onLast().fetchSemanticsNode().boundsInRoot
        val transport = compose.onAllNodesWithText("交通").onLast().fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(shopping.top - transport.top) < 4f)
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun immediateNavigationFabAndThemedDates() {
        compose.onNodeWithText("记账").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("我的").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("明细").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("青鸾记账").assertExists()
        compose.onNodeWithText("分类管理").assertDoesNotExist()
        compose.onNodeWithText("我的").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("分类管理").assertExists()
        compose.onNodeWithText("洞察").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("分类管理").assertDoesNotExist()
        compose.onNodeWithText("总支出").assertExists()
        compose.mainClock.autoAdvance = true

        repeat(3) {
            compose.onNodeWithText("我的").performClick()
            compose.onNodeWithText("分类管理").assertExists()
            compose.onNodeWithText("洞察").performClick()
            compose.onNodeWithText("总支出").assertExists()
            compose.onNodeWithText("明细").performClick()
            compose.onNodeWithText("青鸾记账").assertExists()
        }
        compose.onNodeWithContentDescription("记账").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("餐饮").performClick()
        compose.onNodeWithTag("entry-date").performClick()
        compose.onNodeWithTag("app-date-picker").assertExists()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun monthYearSearchAndPersonalization() {
        compose.onNodeWithTag("detail-period").performClick()
        compose.onNodeWithTag("month-grid").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNodeWithTag("search-island").assertExists()
        compose.onNodeWithContentDescription("关闭搜索").performClick()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("年").performClick()
        compose.onNodeWithText("月均").assertExists()
        compose
            .onNode(hasText(java.time.LocalDate.now().year.toString()) and hasClickAction())
            .performClick()
        compose.onNodeWithTag("year-grid").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("更多", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("灵动效果").performClick()
        waitForOff("灵动效果")
        compose.onNodeWithText("触觉反馈").performClick()
        waitForOff("触觉反馈")
        compose.onNodeWithText("液态材质").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        waitForOff("灵动效果")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.onNodeWithText("明细").performClick()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("餐饮").performClick()
        compose.onNodeWithTag("entry-amount").assertExists()
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun nonCnyEntryKeepsItsCurrencyAndPrecision() {
        compose.onNodeWithContentDescription("记账").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("餐饮").performClick()
        compose.onAllNodesWithText("CNY").onLast().performClick()
        compose.onNodeWithTag("currency-search").performTextInput("KWD")
        compose.onAllNodesWithText("KWD").onLast().performClick()
        listOf("1", ".", "2", "3", "4").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("KWD −1.234").fetchSemanticsNodes().isNotEmpty()
        }
        dismissSnackbar()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("KWD −1.234"))
        compose.onNodeWithText("KWD −1.234").assertExists()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("KWD 1.234").assertExists()
    }

    @Test
    fun requestedLayoutProfileAndMixedCurrency() {
        compose.onNodeWithText("CNY").assertDoesNotExist()
        compose.waitUntil(5000) {
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
                .categories
                .value
                .isNotEmpty()
        }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        val category = vm.categories.value.first { it.type == "EXPENSE" }
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(amount = 100, categoryId = category.id))
            vm.repository.save(Entry(amount = 200, categoryId = category.id, currencyCode = "USD"))
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("USD −2.00").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("入 0.00 · 出 1.00").assertDoesNotExist()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("天数").assertExists()
        compose.onNodeWithText("笔数").assertExists()
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithText("相册").assertExists()
        compose.onNodeWithText("拍照").assertExists()
        compose.onNodeWithTag("profile-nickname").performTextInput("小青")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("小青").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("CNY").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        listOf("日", "周", "自选").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithText("日历").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("USD 2.00"))
        compose.onNodeWithText("USD 2.00").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("年").performClick()
        compose
            .onNode(hasText(java.time.LocalDate.now().year.toString()) and hasClickAction())
            .performClick()
        compose.onNodeWithTag("year-grid").assertExists()
        val a =
            compose
                .onNode(hasText("2026") and hasAnyAncestor(hasTestTag("year-grid")))
                .fetchSemanticsNode()
                .boundsInRoot
        val b = compose.onNodeWithText("2028").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(a.top - b.top) < 4f)
        assert(a.height < 55f)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("小青").assertExists()
    }

    @Test
    fun swipeEditAndThemeDialog() {
        compose.onNodeWithContentDescription("记账").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("餐饮").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("餐饮").performClick()
        listOf("1", "2", ".", "3", "4").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("−12.34").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("−12.34").performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("12.34")
        compose.onNodeWithText("编辑").assertDoesNotExist()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("−12.34").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(-32f, 0f), 200)
        }
        compose.onNodeWithText("删除这笔账单？").assertDoesNotExist()
        compose.onNodeWithText("−12.34").performTouchInput { swipeLeft() }
        compose.onNodeWithText("−12.34").assertExists()
        compose.onNodeWithText("删除这笔账单？").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("−12.34").assertExists()
        compose.onNodeWithContentDescription("删除账单").assertDoesNotExist()
        compose.onNodeWithText("−12.34").performTouchInput { swipeLeft() }
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("暂无账单").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("外观设置").assertDoesNotExist()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("外观模式").performClick()
        compose.onNode(hasText("深色模式") and isSelectable()).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("深色模式").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("深色模式") and isSelectable()).assertDoesNotExist()
        compose.onNodeWithText("外观模式").performClick()
        compose.onNode(hasText("深色模式") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导出").assertExists()
    }

    @Test
    fun freeDragAcrossRowsAndExpandedIcons() {
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("旅行").fetchSemanticsNodes().isNotEmpty()
        }
        val origin =
            compose.onNodeWithContentDescription("餐饮").fetchSemanticsNode().boundsInRoot.center
        val target =
            compose.onNodeWithContentDescription("旅行").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithContentDescription("餐饮").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(target - origin, 350)
            up()
        }
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("餐饮").fetchSemanticsNodes()
            vm.categories.value.filter { it.type == "EXPENSE" }.indexOfFirst { it.name == "餐饮" } ==
                7
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("分类管理").performClick()
        compose.onNodeWithContentDescription("餐饮").performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("category-icon-grid").performScrollToNode(hasContentDescription("快递"))
        compose.onNodeWithContentDescription("快递").performClick()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription("餐饮").fetchSemanticsNodes()
            vm.categories.value.any { it.name == "餐饮" && it.iconKey == "delivery" }
        }
    }

    @Test
    fun calendarMatchesFilterGridWidth() {
        compose.onNodeWithContentDescription("筛选").performClick()
        val width =
            compose.onNodeWithTag("bill-filter-categories").fetchSemanticsNode().boundsInRoot.width
        compose
            .onNodeWithText(java.time.LocalDate.now().withDayOfMonth(1).toString())
            .performClick()
        val calendar = compose.onNodeWithTag("app-date-dialog").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(calendar.width - width) < 2f) { "filter=$width calendar=$calendar" }
        assert(calendar.height < calendar.width * 2f)
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("应用").performClick()
        compose.onNodeWithText("青鸾记账").assertExists()
    }

    @Test
    fun outsideSearchAndTwoTypeFilters() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        kotlinx.coroutines.runBlocking {
            vm.repository.save(
                Entry(
                    amount = 1000,
                    categoryId = vm.categories.value.first { it.type == "EXPENSE" }.id,
                )
            )
            vm.repository.save(
                Entry(
                    type = "INCOME",
                    amount = 2000,
                    categoryId = vm.categories.value.first { it.type == "INCOME" }.id,
                )
            )
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("−10.00").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("20.00").assertExists()
        compose.onNodeWithText("+20.00").assertDoesNotExist()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("全部").assertDoesNotExist()
        compose.onNodeWithText("全部分类").assertDoesNotExist()
        compose.onNode(hasText("收入") and hasClickAction()).performClick()
        compose
            .onNode(hasText("餐饮") and hasAnyAncestor(hasTestTag("bill-filter-categories")))
            .assertDoesNotExist()
        compose.onNodeWithText("应用").performClick()
        compose.onNodeWithText("+20.00").assertExists()
        compose.onNodeWithText("−10.00").assertDoesNotExist()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("重置").performClick()
        compose.onNodeWithText("−10.00").assertExists()
        compose.onNodeWithContentDescription("搜索").performClick()
        compose.onNodeWithTag("search-island").performTouchInput { click(center) }
        compose.onNodeWithTag("search-island").performTextInput("不存在的备注")
        compose.onNodeWithText("暂无账单").assertExists()
        compose.onNodeWithTag("details-page").performTouchInput { swipeUp() }
        compose.onNodeWithTag("search-island").assertExists()
        compose.onNodeWithTag("details-page").performTouchInput {
            click(androidx.compose.ui.geometry.Offset(width / 2f, height - 20f))
        }
        compose.onNodeWithTag("search-island").assertDoesNotExist()
        compose.onNodeWithText("青鸾记账").assertExists()
        compose.onNodeWithText("−10.00").assertExists()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("全部").assertDoesNotExist()
        compose.onNodeWithText("应用").performClick()
    }

    @Test
    fun screenshotCategorySectionsAndDataLabels() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val food = vm.categories.value.first { it.name == "餐饮" }
        val shopping = vm.categories.value.first { it.name == "购物" }
        val salary = vm.categories.value.first { it.name == "工资" }
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(amount = 8000, categoryId = food.id))
            vm.repository.save(Entry(amount = 1000, categoryId = shopping.id))
            vm.repository.save(Entry(type = "INCOME", amount = 20000, categoryId = salary.id))
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("−80.00").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("全部分类").assertDoesNotExist()
        compose
            .onNode(hasText("餐饮") and hasAnyAncestor(hasTestTag("bill-filter-categories")))
            .performClick()
        compose.onNodeWithText("应用").performClick()
        compose.onNodeWithText("−80.00").assertExists()
        compose.onNodeWithText("−10.00").assertDoesNotExist()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose
            .onNode(hasText("餐饮") and hasAnyAncestor(hasTestTag("bill-filter-categories")))
            .performClick()
        compose.onNodeWithText("应用").performClick()
        compose.onNodeWithText("−10.00").assertExists()
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNode(hasText("收入") and hasClickAction()).performClick()
        compose.onNodeWithText("全部分类").assertDoesNotExist()
        compose.onNodeWithText("重置").performClick()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("日历").performClick()
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("insight-calendar-CNY"))
        compose
            .onNode(hasText("日历") and hasAnyAncestor(hasTestTag("insight-calendar-CNY")))
            .assertExists()
        compose
            .onNode(hasText("90.00") and hasAnyAncestor(hasTestTag("insight-calendar-CNY")))
            .assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("年度报告").performClick()
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("insight-report-CNY"))
        compose
            .onNode(hasText("年度报告") and hasAnyAncestor(hasTestTag("insight-report-CNY")))
            .assertExists()
        compose.onNodeWithText("入 200.00 · 出 90.00").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNode(hasText("支出") and hasClickAction()).performClick()
        compose.onNode(hasText("收入") and hasClickAction()).performClick()
        compose
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasTestTag("insight-calendar-CNY"))
        compose
            .onNode(hasText("200.00") and hasAnyAncestor(hasTestTag("insight-calendar-CNY")))
            .assertExists()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导出").assertExists()
        compose.onNodeWithText("导入").assertExists()
        compose.onNodeWithText("备份数据").assertDoesNotExist()
        compose.onNodeWithText("从备份恢复").assertDoesNotExist()
    }

    @Test
    fun fontThemeExportAndProgressiveRows() {
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("字体").performClick()
        compose.onNode(hasText("大") and isSelectable()).performClick()
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("大").fetchSemanticsNodes()
            vm.appearance.value.fontSize == "large"
        }
        compose.onNodeWithText("字形").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("主题").performClick()
        compose.onNode(hasText("海蓝") and isSelectable()).performClick()
        compose.onNodeWithTag("pure-black").performScrollTo().performClick()
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("主题").fetchSemanticsNodes()
            vm.appearance.value.palette == "blue" && vm.appearance.value.pureBlack
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导出").performClick()
        compose.onNodeWithText("文件格式").assertExists()
        compose.onNodeWithText("范围").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("明细").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val category = vm.categories.value.first { it.type == "EXPENSE" }.id
        val now = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking {
            repeat(70) {
                vm.repository.save(
                    Entry(
                        amount = 100,
                        categoryId = category,
                        occurredAt = now + it,
                        note = "渐进-$it",
                    )
                )
            }
        }
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("渐进-69").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(51)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("渐进-0"))
        compose.onNodeWithText("渐进-0").assertExists()
    }

    @Test
    fun compactPreferencesImportFormatsAndLargerButton() {
        assert(
            compose.onNodeWithContentDescription("记账").fetchSemanticsNode().boundsInRoot.width >=
                64f
        )
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("液态材质").assertDoesNotExist()
        compose.onNodeWithText("字体").performClick()
        compose.onNodeWithText("字形").assertDoesNotExist()
        compose.onNodeWithText("等宽").assertDoesNotExist()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("触觉反馈").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导入").performClick()
        compose.onNodeWithText("文件格式").performClick()
        compose.onNode(hasText("JSON") and isSelectable()).assertExists()
        compose.onNode(hasText("CSV") and isSelectable()).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("导出").performClick()
        compose.onNodeWithText("文件格式").assertExists()
        compose.onNodeWithText("范围").assertExists()
        compose.onNodeWithText("取消").performClick()
    }

    @Test
    fun balanceDialogPastelGridAndLargerFab() {
        assert(
            compose.onNodeWithContentDescription("记账").fetchSemanticsNode().boundsInRoot.width >=
                72f
        )
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.any { it.type == "INCOME" }
        }
        val income = vm.categories.value.first { it.type == "INCOME" }.id
        val expense = vm.categories.value.first { it.type == "EXPENSE" }.id
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(type = "INCOME", amount = 10000, categoryId = income))
            vm.repository.save(Entry(amount = 15000, categoryId = expense))
        }
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithTag("insight-type").performClick()
        compose.onNodeWithText("统计类型").assertExists()
        compose.onNode(hasText("结余") and isSelectable()).performClick()
        compose.onNodeWithText("总结余").assertExists()
        compose.onNodeWithText("¥ -50.00").assertExists()
        compose.onNodeWithText("收入分类").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("支出分类"))
        compose.onNodeWithText("支出分类").assertExists()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("主题").performClick()
        compose.onNode(hasText("默认") and isSelectable()).assertExists()
        compose.onNodeWithText("系统配色").assertDoesNotExist()
        val left = compose.onNodeWithTag("palette-jade").fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithTag("palette-system").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(left.top - right.top) < 1f)
        assert(left.left > right.left)
        compose.onNode(hasText("薄荷") and isSelectable()).performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("主题").fetchSemanticsNodes()
            vm.appearance.value.palette == "mint"
        }
        compose.onNodeWithText("完成").performClick()
    }

    @Test
    fun experienceBatchManagementAndTransferUi() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val category = vm.categories.value.first { it.type == "EXPENSE" }.id
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(amount = 100, categoryId = category, note = "多选测试"))
        }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("多选测试", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("多选测试", substring = true)).performTouchInput { longClick() }
        compose.onNodeWithText("已选 1 笔").assertExists()
        compose.onNodeWithText("导出").performClick()
        compose.onNodeWithText("导出所选账单").assertExists()
        compose.onNodeWithText("选择保存位置").assertExists()
        compose.onNodeWithText("文件格式").performClick()
        compose.onNode(hasText("CSV") and isSelectable()).performClick()
        compose.onNodeWithText("备注列").performScrollTo().assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("退出多选").performClick()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithTag("profile-avatar").performClick()
        compose.onNodeWithContentDescription("重置个人资料").assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("列表显示").performScrollTo().performClick()
        compose.onNodeWithText("隐藏金额").assertExists()
        compose.onNodeWithText("显示时间").assertExists()
        compose.onNodeWithText("隐藏金额").performClick()
        compose.onNodeWithText("列表密度").performClick()
        compose.onNode(hasText("舒适") and isSelectable()).performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("列表密度").fetchSemanticsNodes()
            vm.experience.value.density == "comfortable"
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("统计偏好"))
        compose.onNodeWithText("统计偏好").performClick()
        compose.onNodeWithText("默认统计类型").performClick()
        compose.onNode(hasText("结余") and isSelectable()).performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("洞察").performClick()
        compose.onNodeWithText("总结余").assertExists()
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("本地数据").performScrollTo().performClick()
        compose.onNodeWithText("导出").performClick()
        compose.onNodeWithText("范围").performClick()
        compose.onNode(hasText("指定日期") and isSelectable()).assertExists()
        compose.onNode(hasText("当前明细筛选") and isSelectable()).assertExists()
        compose.onNode(hasText("指定日期") and isSelectable()).performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("数据保护").performClick()
        compose.onNodeWithText("应用锁").assertExists()
        compose.onNodeWithText("隐藏后台预览").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("应用锁").fetchSemanticsNodes()
            compose.activity.window.attributes.flags and
                android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
        }
    }

    @Test
    fun aboutRootSettingsAndRightSideSelection() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val category = vm.categories.value.first { it.type == "EXPENSE" }.id
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(amount = 100, categoryId = category, note = "多选布局"))
        }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("多选布局", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        val before =
            compose.onNode(hasText("多选布局", substring = true)).fetchSemanticsNode().boundsInRoot
        val id = kotlinx.coroutines.runBlocking { vm.repository.dao.allEntries().first().id }
        compose.onNode(hasText("多选布局", substring = true)).performTouchInput { longClick() }
        val after =
            compose
                .onAllNodes(hasText("多选布局", substring = true))
                .onLast()
                .fetchSemanticsNode()
                .boundsInRoot
        val check = compose.onNodeWithTag("bill-checkbox-$id").fetchSemanticsNode().boundsInRoot
        assert(kotlin.math.abs(before.left - after.left) < 2f)
        assert(check.left > after.center.x && check.right <= after.right)
        compose.onNodeWithTag("bill-checkbox-$id").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithContentDescription("退出多选").performClick()
        compose.onNodeWithText("我的").performClick()
        listOf("列表显示").forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        listOf("常用备注", "列表显示").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("操作反馈"))
        compose.onNodeWithText("操作反馈").assertExists()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("统计偏好"))
        compose.onNodeWithText("统计偏好").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("关于").performScrollTo()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 200f)
        }
        compose.onNodeWithText("关于").performClick()
        val installedVersion =
            compose.activity.packageManager
                .getPackageInfo(compose.activity.packageName, 0)
                .versionName ?: "—"
        compose.onNodeWithText("版本 $installedVersion").assertExists()
        listOf("官方网站", "GitHub", "博客").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("检测更新").performScrollTo().assertExists()
        compose.onNodeWithText("问题反馈").performScrollTo().assertExists()
        compose.onNodeWithText("更新日志").performScrollTo().performClick()
        compose.onNodeWithText("1.0.0").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("运行日志").performScrollTo().performClick()
        compose.onNodeWithText("仅保留最近100条操作结果，不包含账单内容。").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("开源许可").performScrollTo().performClick()
        compose.waitUntil(5000) {
            compose
                .onAllNodes(hasText("Apache", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNode(hasText("Apache", substring = true)).assertExists()
    }

    @Test
    fun releaseLayoutReceiptAndMenuCleanup() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val category = vm.categories.value.first { it.type == "EXPENSE" }.name
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("常用备注").assertDoesNotExist()
        compose.onNodeWithText("分类管理").performScrollTo().performClick()
        val baseline = compose.onNodeWithTag("page-back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("返回").performClick()
        listOf("个性化", "列表显示", "本地数据", "关于").forEach { page ->
            compose.onNodeWithText(page).performScrollTo()
            val target = compose.onNodeWithText(page).fetchSemanticsNode().boundsInRoot
            val safeBottom =
                compose.activity.resources.displayMetrics.heightPixels -
                    150f * compose.activity.resources.displayMetrics.density
            if (target.bottom > safeBottom)
                compose.onNode(hasScrollAction()).performSemanticsAction(
                    SemanticsActions.ScrollBy
                ) {
                    it(0f, target.bottom - safeBottom)
                }
            compose.onNodeWithText(page).performClick()
            val bounds = compose.onNodeWithTag("page-back").fetchSemanticsNode().boundsInRoot
            assert(
                kotlin.math.abs(bounds.left - baseline.left) < 1f &&
                    kotlin.math.abs(bounds.top - baseline.top) < 1f
            )
            if (page == "本地数据")
                listOf("导入", "导出", "数据保护", "清空账单").forEach {
                    compose.onNodeWithText(it).assertExists()
                }
            if (page == "关于") {
                compose.onNodeWithText("官方网站").assertExists()
                compose.onNodeWithText("待配置").assertDoesNotExist()
            }
            compose.onNodeWithContentDescription("返回").performClick()
        }
        compose.onNodeWithText("明细").performClick()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText(category).performClick()
        compose.onNodeWithText("标签与票据").assertDoesNotExist()
        compose.onNodeWithText("选择票据").performClick()
        assert(AboutLinks.website == "https://ledger.qingluanx.com")
    }

    @Test
    fun inlineSelectionReplaceAmountAndSyncProposal() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        val cat = vm.categories.value.first { it.type == "EXPENSE" }.id
        kotlinx.coroutines.runBlocking {
            vm.repository.save(Entry(amount = 123456, categoryId = cat, note = "待编辑"))
            vm.repository.save(Entry(amount = 200, categoryId = cat, note = "另一笔"))
        }
        compose.waitUntil(5000) {
            compose.onAllNodes(hasText("待编辑", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("待编辑", substring = true)).performTouchInput { longClick() }
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        listOf("编辑", "导出", "删除", "全选").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选 2 笔").assertExists()
        compose.onNodeWithContentDescription("退出多选").performClick()
        compose.onNode(hasText("待编辑", substring = true)).performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("1234.56")
        compose.onNodeWithText("7").performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("7")
        assert(
            kotlinx.coroutines.runBlocking {
                vm.repository.dao.allEntries().first { it.note == "待编辑" }.amount
            } == 123456L
        )
        compose.onNodeWithText("取消").performClick()
        compose.onNode(hasText("待编辑", substring = true)).performClick()
        compose.onNodeWithTag("entry-amount").assertTextEquals("1234.56")
        compose.onNodeWithText("8").performClick()
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.entries.value.any { it.note == "待编辑" && it.amount == 800L }
        }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("同步").performScrollTo()
        val bottom = compose.onNodeWithText("同步").fetchSemanticsNode().boundsInRoot.bottom
        val safe =
            compose.activity.resources.displayMetrics.heightPixels -
                150f * compose.activity.resources.displayMetrics.density
        if (bottom > safe)
            compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, bottom - safe)
            }
        compose.onNodeWithText("同步").performClick()
        compose.onNodeWithText("WebDAV 备份同步").assertExists()
        compose.onNodeWithText("本版本仅展示方案，不连接服务器，不上传或下载账单。").performScrollTo().assertExists()
    }

    @Test
    fun customFontsMigrationEntryAndPageMotion() {
        val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[LedgerViewModel::class.java]
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("青鸾记账").fetchSemanticsNodes()
            vm.categories.value.isNotEmpty()
        }
        compose.onNodeWithText("我的").performClick()
        compose.onNodeWithText("个性化").performScrollTo().performClick()
        compose.onNodeWithText("字体").performClick()
        compose.onNode(hasText("自定义") and isSelectable()).performScrollTo().performClick()
        compose.onNodeWithTag("font-custom-percent").performScrollTo().performTextReplacement("125")
        compose.onNodeWithText("应用").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("字体").fetchSemanticsNodes()
            vm.appearance.value.fontSize == "custom" && vm.appearance.value.customFontPercent == 125
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("分类管理").performScrollTo().performClick()
        compose.onNodeWithContentDescription("分类迁移").performClick()
        compose.onNodeWithText("来源分类").assertExists()
        compose.onNodeWithText("目标分类").assertExists()
        compose.onNodeWithText("只迁移账单，保留来源和目标分类。").assertExists()
        compose.onAllNodesWithText("取消").onLast().performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("明细").assertExists()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("明细").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("洞察").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("我的").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("分类管理").performScrollTo().assertExists()
        compose.onNodeWithText("暂无账单").assertDoesNotExist()
    }

    private fun waitForOff(label: String) {
        compose.waitUntil(5000) {
            compose.onNodeWithText(label).fetchSemanticsNode().config.getOrElse(
                androidx.compose.ui.semantics.SemanticsProperties.ToggleableState
            ) {
                androidx.compose.ui.state.ToggleableState.On
            } == androidx.compose.ui.state.ToggleableState.Off
        }
        compose.onNodeWithText(label).assertIsOff()
    }

    private fun dismissSnackbar() {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty())
            compose.onAllNodes(matcher).onFirst().performSemanticsAction(SemanticsActions.Dismiss) {
                it()
            }
    }
}
