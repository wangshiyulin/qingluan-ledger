package com.qingluan.ledger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate

data class TagTotal(val name: String, val count: Int, val amount: Long)

/** One bill contributes once to each of its distinct historical tag names. */
fun tagTotals(rows: List<Entry>): List<TagTotal> {
    require(rows.map { it.currencyCode }.distinct().size <= 1) { "不同币种不能合计" }
    require(rows.map { it.type }.distinct().size <= 1) { "收入与支出须分别统计" }
    val grouped = linkedMapOf<String, MutableList<Entry>>()
    rows.forEach { entry ->
        TagCatalog.names(entry.tags).forEach { name ->
            grouped.getOrPut(name) { mutableListOf() }.add(entry)
        }
    }
    return grouped
        .map { (name, entries) ->
            TagTotal(
                name,
                entries.size,
                entries.fold(0L) { sum, entry -> Math.addExact(sum, entry.amount) },
            )
        }
        .sortedWith(compareByDescending<TagTotal> { it.amount }.thenBy { it.name })
}

@Composable
fun TagInsightsPage(rows: List<Entry>, cats: List<Category>, back: () -> Unit) {
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var type by remember { mutableStateOf("EXPENSE") }
    var categoryId by remember { mutableLongStateOf(0) }
    var currency by remember { mutableStateOf("CNY") }
    var periodOpen by remember { mutableStateOf(false) }
    val monthRows =
        remember(rows, month, type, categoryId) {
            val span = DateSpan.month(month)
            rows.filter {
                it.type == type &&
                    span.contains(it) &&
                    (categoryId == 0L || it.categoryId == categoryId)
            }
        }
    val codes = remember(monthRows) { monthRows.map { it.currencyCode }.distinct().sorted() }
    LaunchedEffect(codes) { if (currency !in codes && codes.isNotEmpty()) currency = codes.first() }
    val filtered =
        remember(monthRows, currency) { monthRows.filter { it.currencyCode == currency } }
    val tagged = remember(filtered) { filtered.filter { TagCatalog.names(it.tags).isNotEmpty() } }
    val summary =
        remember(tagged) { tagged.fold(0L) { sum, entry -> Math.addExact(sum, entry.amount) } }
    val totals = remember(filtered) { tagTotals(filtered) }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("标签统计", back)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { month = month.minusMonths(1) }) { Text("上月") }
                    TextButton(onClick = { periodOpen = true }, modifier = Modifier.weight(1f)) {
                        Text("${month.year}年${month.monthValue}月")
                    }
                    TextButton(onClick = { month = month.plusMonths(1) }) { Text("下月") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("EXPENSE" to "支出", "INCOME" to "收入").forEach { (key, label) ->
                        FilterChip(
                            type == key,
                            {
                                type = key
                                categoryId = 0
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(categoryId == 0L, { categoryId = 0 }, label = { Text("所有分类") })
                    cats
                        .filter { it.type == type }
                        .forEach { category ->
                            FilterChip(
                                categoryId == category.id,
                                { categoryId = category.id },
                                label = { Text(category.name) },
                            )
                        }
                }
                if (codes.isNotEmpty())
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        codes.forEach { code ->
                            FilterChip(
                                currency == code,
                                { currency = code },
                                label = { Text(code) },
                            )
                        }
                    }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "已标记账单 · ${tagged.size}笔",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "$currency ${displayMoney(summary, currency)}",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text("汇总中每笔账单只计算一次。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text(
                    "每个标签计入整笔账单金额；一笔账单可属于多个标签，各标签金额不能相加。收入、支出和币种分别统计。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (totals.isEmpty()) item { Text("本月暂无带标签的账单", Modifier.padding(vertical = 24.dp)) }
            items(totals, key = { it.name }) { tag ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tag.name,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text("${tag.count}笔", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "$currency ${displayMoney(tag.amount, currency)}",
                            Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }
    }
    if (periodOpen)
        PeriodPicker(month, "month", { periodOpen = false }) {
            month = it.withDayOfMonth(1)
            periodOpen = false
        }
}
