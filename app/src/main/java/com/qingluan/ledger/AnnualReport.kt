package com.qingluan.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val ANNUAL_REPORT_ROUTE = "年度报告/{year}"

internal data class AnnualMonth(val month: Int, val income: Long, val expense: Long)

internal data class AnnualReportData(
    val year: Int,
    val currency: String,
    val total: Totals,
    val count: Int,
    val days: Int,
    val months: List<AnnualMonth>,
    val categories: List<Pair<Long, Long>>,
    val largest: List<Entry>,
) {
    val peakMonth
        get() = months.maxByOrNull { it.expense }?.takeIf { it.expense > 0 }
}

/** Filter year and currency before aggregation; one date conversion per included currency row. */
internal fun annualReport(
    rows: List<Entry>,
    year: Int,
    code: String,
    zone: ZoneId = ZoneId.systemDefault(),
): AnnualReportData {
    require(year in 1900..2200 && Currencies.contains(code))
    val income = LongArray(12)
    val expense = LongArray(12)
    val categories = mutableMapOf<Long, Long>()
    val days = mutableSetOf<LocalDate>()
    val entries = mutableListOf<Entry>()
    for (entry in rows) {
        if (entry.currencyCode != code) continue
        val date = java.time.Instant.ofEpochMilli(entry.occurredAt).atZone(zone).toLocalDate()
        if (date.year != year) continue
        entries += entry
        days += date
        val month = date.monthValue - 1
        if (entry.type == "INCOME") income[month] = Math.addExact(income[month], entry.amount)
        else if (entry.type == "EXPENSE") {
            expense[month] = Math.addExact(expense[month], entry.amount)
            categories[entry.categoryId] =
                Math.addExact(categories[entry.categoryId] ?: 0, entry.amount)
        }
    }
    return AnnualReportData(
        year,
        code,
        totals(entries),
        entries.size,
        days.size,
        (0..11).map { AnnualMonth(it + 1, income[it], expense[it]) },
        categories.entries.sortedByDescending { it.value }.map { it.key to it.value },
        entries.filter { it.type == "EXPENSE" }.sortedByDescending { it.amount }.take(5),
    )
}

@Composable
fun AnnualReportPage(rows: List<Entry>, cats: List<Category>, initialYear: Int, back: () -> Unit) {
    var year by rememberSaveable { mutableIntStateOf(initialYear.coerceIn(1900, 2200)) }
    val default = LocalMainCurrency.current
    var currency by rememberSaveable { mutableStateOf(default) }
    val code = currency.takeIf(Currencies::contains) ?: default
    var years by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val report by
        produceState<AnnualReportData?>(null, rows, year, code, zone) {
            value = null
            value =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    annualReport(rows, year, code, zone)
                }
        }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var shareOptions by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    var showAmounts by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(true) }
    val hideAmounts = LocalExperience.current.hideAmounts
    val accent = MaterialTheme.colorScheme.primary.toArgb()
    val categories = remember(cats) { cats.associateBy { it.id } }
    Column(Modifier.fillMaxSize().testTag("annual-report-page")) {
        FeatureHeader("年度报告", back) {
            IconButton(
                enabled =
                    !sharing && report?.let { it.year == year && it.currency == code } == true,
                onClick = {
                    showAmounts = !hideAmounts
                    shareOptions = true
                },
            ) {
                Icon(Icons.Outlined.Share, if (sharing) "正在生成分享图片" else "分享年度报告")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(enabled = year > 1900, onClick = { year-- }) {
                Icon(Icons.Outlined.ChevronLeft, "上一年")
            }
            TextButton(onClick = { years = true }, modifier = Modifier.weight(1f)) {
                Text("${year}年")
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
            IconButton(enabled = year < 2200, onClick = { year++ }) {
                Icon(Icons.Outlined.ChevronRight, "下一年")
            }
            TextButton(onClick = { picker = true }) {
                Text(code)
                Icon(Icons.Outlined.ArrowDropDown, null)
            }
        }
        val data = report?.takeIf { it.year == year && it.currency == code }
        if (data == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else
            CompositionLocalProvider(LocalCurrency provides code) {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text(
                                    "${year}年 · $code",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "年度结余",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    displayMoney(data.total.balance, code),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    AnnualValue(
                                        "全年收入",
                                        displayMoney(data.total.income, code),
                                        Modifier.weight(1f),
                                    )
                                    AnnualValue(
                                        "全年支出",
                                        displayMoney(data.total.expense, code),
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ReportMetric("记账天数", data.days.toString(), Modifier.weight(1f))
                            ReportMetric("账单笔数", data.count.toString(), Modifier.weight(1f))
                            ReportMetric(
                                "支出峰值月",
                                data.peakMonth?.let { "${it.month}月" } ?: "—",
                                Modifier.weight(1f),
                            )
                        }
                    }
                    if (data.count == 0)
                        item {
                            Text(
                                "本年度暂无账单",
                                Modifier.padding(vertical = 20.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    else {
                        item {
                            ReportCard("月度收支") {
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Text(
                                        "收入",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        "支出",
                                        color = spendingColor(),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                val max =
                                    data.months
                                        .maxOf { maxOf(it.income, it.expense) }
                                        .coerceAtLeast(1L)
                                        .toDouble()
                                data.months.forEach { month ->
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                "${month.month}月",
                                                Modifier.width(32.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                            Text(
                                                displayMoney(month.income, code),
                                                Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                displayMoney(month.expense, code),
                                                Modifier.weight(1f),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = spendingColor(),
                                            )
                                        }
                                        if (!LocalExperience.current.hideAmounts) {
                                            ReportBar(
                                                (month.income / max).toFloat(),
                                                MaterialTheme.colorScheme.primary,
                                            )
                                            ReportBar(
                                                (month.expense / max).toFloat(),
                                                spendingColor(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (data.categories.isNotEmpty())
                            item {
                                ReportCard("支出分类 · 前五") {
                                    data.categories.take(5).forEach { (id, amount) ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Icon(
                                                icon(categories[id]?.iconKey ?: "circle"),
                                                null,
                                                Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                categories[id]?.name ?: "分类",
                                                Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                displayMoney(amount, code),
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        if (!LocalExperience.current.hideAmounts)
                                            ReportBar(
                                                (amount.toDouble() /
                                                        data.total.expense.coerceAtLeast(1L))
                                                    .toFloat(),
                                                MaterialTheme.colorScheme.primary,
                                            )
                                    }
                                }
                            }
                        if (data.largest.isNotEmpty()) {
                            item { Text("大额支出 · 前五", style = MaterialTheme.typography.titleMedium) }
                            items(data.largest, key = { it.id }) { entry ->
                                EntryRow(entry, cats, null)
                            }
                        }
                    }
                }
            }
    }
    if (shareOptions)
        GlassAlertDialog(
            onDismissRequest = { shareOptions = false },
            title = { Text("分享年度报告") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfigSwitch("显示金额", showAmounts) { showAmounts = it }
                    ConfigSwitch("显示分类排行", showCategories) { showCategories = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val snapshot = report?.takeIf { it.year == year && it.currency == code }
                        if (snapshot != null) {
                            shareOptions = false
                            sharing = true
                            val amounts = showAmounts
                            val ranking = showCategories
                            shareScope.launch {
                                try {
                                    val uri =
                                        annualReportImage(
                                            context,
                                            snapshot,
                                            categories,
                                            amounts,
                                            ranking,
                                            accent,
                                        )
                                    shareAnnualReport(context, uri)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    shareError = "无法分享年度报告，请稍后重试"
                                } finally {
                                    sharing = false
                                }
                            }
                        }
                    }
                ) {
                    Text("生成并分享")
                }
            },
            dismissButton = { TextButton(onClick = { shareOptions = false }) { Text("取消") } },
        )
    shareError?.let { message ->
        GlassAlertDialog(
            onDismissRequest = { shareError = null },
            title = { Text("分享失败") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text("确定") } },
        )
    }
    if (years)
        PeriodPicker(LocalDate.of(year, 1, 1), "year", { years = false }) {
            year = it.year
            years = false
        }
    if (picker)
        CurrencyPicker(code, { picker = false }) {
            currency = it
            picker = false
        }
}

@Composable
private fun AnnualValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ReportMetric(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReportCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ReportBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier.fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp))
    ) {
        if (fraction > 0)
            Box(
                Modifier.fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color, RoundedCornerShape(2.dp))
            )
    }
}
