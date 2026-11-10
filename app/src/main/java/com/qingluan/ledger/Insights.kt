package com.qingluan.ledger

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*

val insightTypeNames = linkedMapOf("EXPENSE" to "支出", "INCOME" to "收入", "BALANCE" to "结余")

@Composable
fun Insights(
    rows: List<Entry>,
    cats: List<Category>,
    persist: (String, Any) -> Unit = { _, _ -> },
    add: (LocalDate) -> Unit,
) {
    val prefs = LocalExperience.current
    fun savePref(key: String, value: Any) {
        if (prefs.rememberStats) persist(key, value)
    }
    val effects = LocalEffects.current
    val feedback = rememberAppFeedback()
    var date by remember {
        mutableStateOf(
            if (prefs.rememberStats && prefs.statDate.isNotBlank()) LocalDate.parse(prefs.statDate)
            else LocalDate.now()
        )
    }
    var unit by remember { mutableStateOf(prefs.statUnit) }
    var type by remember { mutableStateOf(prefs.statType) }
    var detail by remember { mutableStateOf<Pair<Category, String>?>(null) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    var calendar by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var pie by remember { mutableStateOf(prefs.statPie) }
    var typeMenu by remember { mutableStateOf(false) }
    var more by remember { mutableStateOf(false) }
    var dateOpen by remember { mutableStateOf(false) }
    val range =
        when (unit) {
            "all" ->
                if (rows.isEmpty()) DateSpan.month(date)
                else DateSpan(rows.minOf { it.date() }, rows.maxOf { it.date() }.plusDays(1))
            "year" -> DateSpan.year(date)
            else -> DateSpan.month(date)
        }
    val current = remember(rows, range) { rows.filter { range.contains(it) } }
    val groupedRows = remember(rows) { currencyGroups(rows) }
    val groupedCurrent = remember(current) { current.groupBy { it.currencyCode } }
    val today = LocalDate.now()
    val elapsed =
        if (!today.isBefore(range.start) && today < range.end)
            java.time.temporal.ChronoUnit.DAYS.between(range.start, today) + 1
        else range.days
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = LocalFloatingBottomInset.current + 88.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(
                        onClick = {
                            feedback()
                            dateOpen = true
                        },
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        Icon(Icons.Outlined.BarChart, null, Modifier.size(20.dp))
                        Text(
                            when (unit) {
                                "year" -> date.year.toString()
                                "all" -> "全部"
                                else ->
                                    date.format(
                                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")
                                    )
                            }
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null)
                    }
                }
                TextButton(
                    onClick = {
                        feedback()
                        typeMenu = true
                    },
                    modifier = Modifier.testTag("insight-type"),
                ) {
                    Text(insightTypeNames.getValue(type))
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreHoriz, "更多") }
                    DropdownMenu(more, { more = false }) {
                        listOf("日历", "年度报告").forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    if (label == "日历") calendar = !calendar else report = !report
                                    more = false
                                },
                            )
                        }
                    }
                }
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.fillMaxWidth().padding(4.dp)) {
                    listOf("month" to "月", "year" to "年", "all" to "全部").forEach { (v, n) ->
                        Surface(
                            onClick = {
                                feedback()
                                unit = v
                                savePref("statUnit", v)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(22.dp),
                            color =
                                if (unit == v) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                        ) {
                            Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    n,
                                    color =
                                        if (unit == v) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
        groupedRows.forEach { (currency, currencyRows) ->
            val periodRows = groupedCurrent[currency].orEmpty()
            val typed = periodRows.filter { type == "BALANCE" || it.type == type }
            val total = if (type == "BALANCE") totals(typed).balance else typed.sumOf { it.amount }
            val points =
                trendBuckets(typed, visibleTrendSpan(range, today), balance = type == "BALANCE")
            currencyItem(currency) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (type == "BALANCE") "总结余"
                                else if (type == "EXPENSE") "总支出" else "总收入",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "${currencyPrefix(currency)} ${displayMoney(total,currency)}",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            Text(
                                if (unit == "year") "月均" else if (unit == "all") "平均" else "日均",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                "${currencyPrefix(currency)} ${displayMoney(if(unit=="all")recordedDayMean(total,currencyRows)else dailyMean(total,if(unit=="year")if(date.year==today.year)today.monthValue.toLong()else 12L else elapsed),currency)}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
            if (typed.isEmpty()) currencyItem(currency) { Empty("暂无数据") }
            else {
                currencyItem(currency) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Crossfade(
                            points,
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    if (effects.motion) 100 else 0
                                ),
                            label = "趋势切换",
                        ) { series ->
                            Trend(
                                series.map { it.second },
                                series.map {
                                    if (range.days > 90)
                                        it.first.format(
                                            java.time.format.DateTimeFormatter.ofPattern("yy-MM")
                                        )
                                    else it.first.dayOfMonth.toString()
                                },
                            )
                        }
                    }
                }
                val rankTypes = if (type == "BALANCE") listOf("INCOME", "EXPENSE") else listOf(type)
                rankTypes.forEach { rankType ->
                    val rankRows = typed.filter { it.type == rankType }
                    val rankTotal = rankRows.sumOf { it.amount }
                    val ranked =
                        rankRows
                            .groupBy { it.categoryId }
                            .map { (id, list) -> id to list.sumOf { it.amount } }
                            .sortedByDescending { it.second }
                    if (ranked.isNotEmpty()) {
                        currencyItem(currency) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (type == "BALANCE")
                                        "${insightTypeNames.getValue(rankType)}分类"
                                    else "分类排行",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                IconButton(
                                    onClick = {
                                        pie = !pie
                                        savePref("statPie", pie)
                                    }
                                ) {
                                    Icon(
                                        if (pie) Icons.AutoMirrored.Outlined.FormatListBulleted
                                        else Icons.Outlined.PieChart,
                                        if (pie) "分类列表" else "分类占比",
                                    )
                                }
                            }
                            if (pie)
                                Pie(
                                    ranked.map { it.second },
                                    ranked.map { id ->
                                        cats.find { it.id == id.first }?.name ?: "分类"
                                    },
                                ) { index ->
                                    detail =
                                        cats
                                            .find { it.id == ranked[index].first }
                                            ?.let { it to currency }
                                }
                        }
                        if (!pie)
                            ranked.forEach { (id, value) ->
                                currencyItem(currency) {
                                    val c = cats.find { it.id == id }
                                    Surface(
                                        onClick = { detail = c?.let { it to currency } },
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    ) {
                                        Row(
                                            Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                            ) {
                                                Icon(
                                                    icon(c?.iconKey ?: "circle"),
                                                    null,
                                                    Modifier.padding(10.dp).size(20.dp),
                                                )
                                            }
                                            Column(Modifier.weight(1f)) {
                                                Row(Modifier.fillMaxWidth()) {
                                                    Text(
                                                        c?.name ?: "分类",
                                                        Modifier.weight(1f),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                    )
                                                    Text(
                                                        displayMoney(value, currency),
                                                        style = MaterialTheme.typography.titleSmall,
                                                    )
                                                }
                                                Text(
                                                    "%.1f%%"
                                                        .format(value.toDouble() / rankTotal * 100),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                LinearProgressIndicator(
                                                    progress = { value.toFloat() / rankTotal },
                                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    trackColor =
                                                        MaterialTheme.colorScheme
                                                            .surfaceContainerHighest,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
            if (calendar)
                currencyItem(currency) {
                    InsightSection(
                        "日历",
                        "${date.year}年${date.monthValue}月 · $currency",
                        "insight-calendar-$currency",
                    ) {
                        CalendarMonth(date, currencyRows, type) { day = it }
                    }
                }
            if (report)
                currencyItem(currency) {
                    val yearly = currencyRows.filter { DateSpan.year(date).contains(it) }
                    val months = yearly.groupBy { it.date().monthValue }
                    InsightSection(
                        "年度报告",
                        "${date.year} 年 · $currency",
                        "insight-report-$currency",
                    ) {
                        Summary(yearly)
                        (1..12).forEach { m ->
                            val mt = totals(months[m].orEmpty())
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("${m}月", Modifier.weight(1f))
                                Text(
                                    "入 ${displayMoney(mt.income,currency)} · 出 ${displayMoney(mt.expense,currency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Text("消费排行", style = MaterialTheme.typography.titleMedium)
                        yearly
                            .filter { it.type == "EXPENSE" }
                            .sortedByDescending { it.amount }
                            .take(10)
                            .forEach { e -> EntryRow(e, cats) { day = e.date() } }
                    }
                }
        }
    }
    if (typeMenu) {
        GlassAlertDialog(
            onDismissRequest = { typeMenu = false },
            title = { Text("统计类型") },
            text = {
                Column {
                    insightTypeNames.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    type == key,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                ) {
                                    feedback()
                                    type = key
                                    savePref("statType", key)
                                    typeMenu = false
                                }
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(type == key, onClick = null)
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { typeMenu = false }) { Text("取消") } },
        )
    }
    if (dateOpen) {
        PeriodPicker(date, if (unit == "year") "year" else "month", { dateOpen = false }) {
            date = it
            savePref("statDate", it.toString())
            if (unit == "all") {
                unit = "month"
                savePref("statUnit", unit)
            }
            dateOpen = false
        }
    }
    detail?.let { (c, code) ->
        GlassAlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(c.name) },
            text = {
                LazyColumn {
                    items(current.filter { it.currencyCode == code && it.categoryId == c.id }) {
                        EntryRow(it, cats) {
                            day = it.date()
                            detail = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("关闭") } },
        )
    }
    day?.let { d ->
        GlassAlertDialog(
            onDismissRequest = { day = null },
            title = { Text(d.toString()) },
            text = {
                LazyColumn {
                    val daily = rows.filter { it.date() == d }
                    item { Summary(daily) }
                    items(daily) { EntryRow(it, cats, null) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        day = null
                        add(d)
                    }
                ) {
                    Text("记一笔")
                }
            },
            dismissButton = { TextButton(onClick = { day = null }) { Text("关闭") } },
        )
    }
}

@Composable
private fun InsightSection(
    title: String,
    period: String,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(tag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                title,
                Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                period,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
            )
            content()
        }
    }
}

private fun LazyListScope.currencyItem(code: String, content: @Composable () -> Unit) {
    item { CompositionLocalProvider(LocalCurrency provides code, content = content) }
}

@Composable
fun Trend(values: List<Long>, labels: List<String> = values.indices.map { (it + 1).toString() }) {
    val currency = LocalCurrency.current
    val appearance = LocalAppearance.current
    val color = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val hideAmounts = LocalExperience.current.hideAmounts
    Canvas(Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 12.dp, vertical = 16.dp)) {
        if (values.isNotEmpty()) {
            val max = maxOf(0.0, values.maxOrNull()?.toDouble() ?: 0.0)
            val min = minOf(0.0, values.minOrNull()?.toDouble() ?: 0.0)
            val extent = (max - min).coerceAtLeast(1.0)
            fun fraction(value: Double) = ((value - min) / extent).toFloat()
            val top = 20.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val plot = (bottom - top).coerceAtLeast(1f)
            val inset = 12.dp.toPx()
            val width = (size.width - inset * 2).coerceAtLeast(1f)
            fun point(i: Int) =
                Offset(
                    if (values.size == 1) size.width / 2 else inset + width * i / (values.size - 1),
                    bottom - plot * fraction(values[i].toDouble()),
                )
            drawLine(
                foreground.copy(alpha = .2f),
                Offset(inset, bottom - plot * fraction(0.0)),
                Offset(size.width - inset, bottom - plot * fraction(0.0)),
                1.dp.toPx(),
            )
            val mean = values.map { it.toDouble() }.average()
            val avgY = bottom - plot * fraction(mean)
            drawLine(
                color.copy(alpha = .25f),
                Offset(inset, avgY),
                Offset(size.width - inset, avgY),
                1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            values.indices.zipWithNext().forEach { (a, b) ->
                drawLine(color, point(a), point(b), 2.dp.toPx(), StrokeCap.Round)
            }
            val paint =
                android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = foreground.toArgb()
                    textSize = 10.sp.toPx() * appearance.scale
                    typeface = android.graphics.Typeface.DEFAULT
                }
            val selected =
                if (values.size <= 10) values.indices.toSet()
                else
                    ((0 until 6).map { it * (values.size - 1) / 5 } +
                            values.indices.maxBy { values[it] })
                        .toSet()
            selected.forEach { i ->
                val p = point(i)
                drawCircle(color, 3.dp.toPx(), p)
                drawContext.canvas.nativeCanvas.drawText(
                    visibleMoney(values[i], currency, hideAmounts),
                    p.x.coerceIn(
                        22.dp.toPx(),
                        (size.width - 22.dp.toPx()).coerceAtLeast(22.dp.toPx()),
                    ),
                    p.y - 8.dp.toPx(),
                    paint,
                )
            }
            val ticks =
                if (values.size <= 10) values.indices.toList()
                else values.indices.filter { it % kotlin.math.ceil(values.size / 6.0).toInt() == 0 }
            ticks.forEach { index ->
                // Tick slots are evenly spaced; sample indices match the nearest plotted date.
                val x = point(index).x
                drawContext.canvas.nativeCanvas.drawText(
                    labels.getOrElse(index) { "" },
                    x,
                    bottom + 17.dp.toPx(),
                    paint,
                )
            }
        }
    }
}

fun categoryColor(index: Int): Color {
    val palette =
        listOf(
            Color(0xFF5B8FF9),
            Color(0xFF61C9A8),
            Color(0xFFF6BD62),
            Color(0xFFEF8298),
            Color(0xFF9275DD),
            Color(0xFF52B8CC),
            Color(0xFFF08C56),
            Color(0xFF91B85A),
        )
    return if (index < palette.size) palette[index]
    else Color.hsl((index * 137.5f) % 360f, .55f, .6f)
}

@Composable
fun Pie(
    values: List<Long>,
    labels: List<String> = values.indices.map { (it + 1).toString() },
    select: ((Int) -> Unit)? = null,
) {
    val currency = LocalCurrency.current
    val total = values.sum()
    Column {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(200.dp).padding(16.dp)) {
                var start = -90f
                val width = 28.dp.toPx()
                val box = androidx.compose.ui.geometry.Size(size.width - width, size.height - width)
                val origin = Offset(width / 2, width / 2)
                if (total > 0)
                    values.forEachIndexed { i, v ->
                        val sweep = (v.toDouble() / total * 360).toFloat()
                        val gap = minOf(2f, sweep * .08f)
                        drawArc(
                            categoryColor(i),
                            start + gap / 2,
                            sweep - gap,
                            false,
                            topLeft = origin,
                            size = box,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width),
                        )
                        start += sweep
                    }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "总计",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${currencyPrefix(currency)} ${displayMoney(total,currency)}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        values.indices.chunked(2).forEach { line ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                line.forEach { i ->
                    Row(
                        Modifier.weight(1f)
                            .then(
                                if (select != null) Modifier.clickable { select(i) } else Modifier
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = categoryColor(i),
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp),
                        ) {}
                        Text(
                            labels.getOrElse(i) { "分类" },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                        Text(
                            if (total == 0L) "0.0%"
                            else "%.1f%%".format(values[i].toDouble() / total * 100),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (line.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun CalendarMonth(
    date: LocalDate,
    rows: List<Entry>,
    type: String = "EXPENSE",
    select: (LocalDate) -> Unit,
) {
    val currency = LocalCurrency.current
    val start = date.withDayOfMonth(1)
    val dailyAmounts =
        remember(rows, type) {
            rows
                .filter { type == "BALANCE" || it.type == type }
                .groupBy { it.date() }
                .mapValues { (_, list) ->
                    if (type == "BALANCE") totals(list).balance else list.sumOf { it.amount }
                }
        }
    Column {
        Row {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(
                    it,
                    Modifier.weight(1f).padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        val offset = start.dayOfWeek.value - 1
        (0 until (offset + date.lengthOfMonth() + 6) / 7).forEach { week ->
            Row {
                (0..6).forEach { col ->
                    val n = week * 7 + col - offset + 1
                    Column(
                        Modifier.weight(1f)
                            .heightIn(min = 52.dp)
                            .then(
                                if (n in 1..date.lengthOfMonth())
                                    Modifier.clickable { select(start.withDayOfMonth(n)) }
                                else Modifier
                            )
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (n in 1..date.lengthOfMonth()) {
                            Text(n.toString(), style = MaterialTheme.typography.bodyMedium)
                            val amount = dailyAmounts[start.withDayOfMonth(n)] ?: 0L
                            if (amount != 0L)
                                Text(
                                    displayMoney(amount, currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (type == "EXPENSE" || amount < 0) spendingColor()
                                        else MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                )
                        }
                    }
                }
            }
        }
    }
}
