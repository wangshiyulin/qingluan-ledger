package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.*
import org.json.JSONObject

@Composable
fun FeatureHeader(
    title: String,
    back: () -> Unit,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageBackButton(onClick = back)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        action?.invoke(this)
    }
}

@Composable
fun TrashPage(vm: LedgerViewModel, back: () -> Unit) {
    val feedback = rememberAppFeedback()
    val records by vm.trash.collectAsState()
    val retention by vm.trashRetentionDays.collectAsState()
    var deleting by remember { mutableStateOf<Set<Long>?>(null) }
    var restoring by remember { mutableStateOf<Set<Long>?>(null) }
    var keep by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("回收站", back)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ConfigChoice(
                    "保留期限",
                    retention.toString(),
                    linkedMapOf("7" to "7天", "30" to "30天", "90" to "90天", "0" to "永久保留"),
                ) { days ->
                    val value = days.toInt()
                    if (
                        value > 0 &&
                            records.any {
                                it.deletedAt < System.currentTimeMillis() - value * 86400000L
                            }
                    )
                        keep = value
                    else vm.retention(value)
                }
            }
            if (records.isNotEmpty())
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            onClick = {
                                feedback()
                                restoring = records.map { it.id }.toSet()
                            }
                        ) {
                            Text("恢复全部")
                        }
                        TextButton(
                            onClick = {
                                feedback()
                                deleting = records.map { it.id }.toSet()
                            }
                        ) {
                            Text("清空回收站")
                        }
                    }
                }
            if (records.isEmpty())
                item {
                    Text(
                        "回收站为空",
                        Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            items(records, key = { it.id }) { record ->
                val entry = remember(record) { ArchiveJson.entry(JSONObject(record.entryJson)) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(8.dp)) {
                        CompositionLocalProvider(LocalCategoryIndex provides emptyMap()) {
                            EntryRow(
                                entry,
                                listOf(
                                    Category(
                                        id = entry.categoryId,
                                        name = record.categoryName,
                                        type = entry.type,
                                        iconKey = record.iconKey,
                                    )
                                ),
                                null,
                            )
                        }
                        Text(
                            "删除于 ${Instant.ofEpochMilli(record.deletedAt).atZone(ZoneId.systemDefault()).toLocalDate()}",
                            Modifier.padding(horizontal = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                onClick = {
                                    feedback()
                                    vm.restoreArchived(setOf(record.id))
                                }
                            ) {
                                Text("恢复")
                            }
                            TextButton(
                                onClick = {
                                    feedback()
                                    deleting = setOf(record.id)
                                }
                            ) {
                                Text("彻底删除")
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { ids ->
        GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("彻底删除 ${ids.size} 笔账单？") },
            text = { Text("此操作无法从回收站恢复，票据也将删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.permanentlyDelete(ids)
                        deleting = null
                    }
                ) {
                    Text("彻底删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        deleting = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    restoring?.let { ids ->
        GlassAlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("恢复 ${ids.size} 笔账单？") },
            text = { Text("原分类已删除时会重新创建相应分类。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.restoreArchived(ids)
                        restoring = null
                    }
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        restoring = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    keep?.let { days ->
        GlassAlertDialog(
            onDismissRequest = { keep = null },
            title = { Text("保留 $days 天？") },
            text = { Text("超过期限的回收站记录将被彻底删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.retention(days)
                        keep = null
                    }
                ) {
                    Text("应用")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        keep = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun RecurringPage(vm: LedgerViewModel, cats: List<Category>, back: () -> Unit) {
    val feedback = rememberAppFeedback()
    val rules by vm.recurring.collectAsState()
    val today by vm.today.collectAsState()
    var edit by remember { mutableStateOf<RecurringBill?>(null) }
    var create by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RecurringBill?>(null) }
    var confirming by remember { mutableStateOf<RecurringBill?>(null) }
    var skipping by remember { mutableStateOf<RecurringBill?>(null) }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("周期账单", back) {
            TextButton(
                onClick = {
                    feedback()
                    create = true
                }
            ) {
                Text("新增")
            }
        }
        if (rules.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rules, key = { it.id }) { rule ->
                    val due = rule.enabled && !LocalDate.parse(rule.nextDate).isAfter(today)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${rule.currencyCode} ${visibleMoney(rule.amount,rule.currencyCode,LocalExperience.current.hideAmounts)} · ${if(rule.type=="EXPENSE")"支出" else "收入"}"
                            )
                            Text(
                                "每${rule.interval}${frequencyNames[rule.frequency]} · ${rule.nextDate} · ${if(!rule.enabled)"已暂停" else if(due)"待确认" else "未到期"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(
                                    onClick = {
                                        feedback()
                                        edit = rule
                                    }
                                ) {
                                    Text("编辑")
                                }
                                TextButton(
                                    onClick = {
                                        feedback()
                                        vm.action {
                                            vm.repository.saveRecurring(
                                                rule.copy(enabled = !rule.enabled)
                                            )
                                        }
                                    }
                                ) {
                                    Text(if (rule.enabled) "暂停" else "启用")
                                }
                                TextButton(
                                    onClick = {
                                        feedback()
                                        deleting = rule
                                    }
                                ) {
                                    Text("删除")
                                }
                            }
                            if (due)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            feedback()
                                            confirming = rule
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("确认入账")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            feedback()
                                            skipping = rule
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("跳过本期")
                                    }
                                }
                        }
                    }
                }
            }
    }
    if (create || edit != null)
        RecurringEditor(
            vm,
            cats,
            edit,
            {
                create = false
                edit = null
            },
        )
    deleting?.let { rule ->
        GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除周期规则？") },
            text = { Text("已生成的账单不会删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.action { vm.repository.dao.deleteRecurring(rule.id) }
                        deleting = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        deleting = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    confirming?.let { rule ->
        GlassAlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("确认 ${rule.nextDate} 的账单？") },
            text = { Text("仅生成本期账单；若仍有逾期项目，可继续确认。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.action {
                            vm.repository.advanceRecurring(rule.id, rule.nextDate, true)
                            vm.message.value = "周期账单已入账"
                        }
                        confirming = null
                    }
                ) {
                    Text("入账")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        confirming = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    skipping?.let { rule ->
        GlassAlertDialog(
            onDismissRequest = { skipping = null },
            title = { Text("跳过本期？") },
            text = { Text("本期不生成账单，下次日期将顺延一个周期。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        feedback()
                        vm.action { vm.repository.advanceRecurring(rule.id, rule.nextDate, false) }
                        skipping = null
                    }
                ) {
                    Text("跳过")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        feedback()
                        skipping = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun RecurringEditor(
    vm: LedgerViewModel,
    cats: List<Category>,
    original: RecurringBill?,
    dismiss: () -> Unit,
) {
    val feedback = rememberAppFeedback()
    var name by remember { mutableStateOf(original?.name ?: "") }
    var type by remember { mutableStateOf(original?.type ?: "EXPENSE") }
    var amount by remember {
        mutableStateOf(original?.let { Money.text(it.amount, it.currencyCode) } ?: "")
    }
    val mainCurrency = LocalMainCurrency.current
    var currency by remember { mutableStateOf(original?.currencyCode ?: mainCurrency) }
    var category by remember {
        mutableStateOf(
            original?.categoryId?.toString()
                ?: cats.firstOrNull { it.type == type }?.id?.toString()
                ?: ""
        )
    }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var date by remember {
        mutableStateOf(original?.nextDate?.let(LocalDate::parse) ?: LocalDate.now())
    }
    var frequency by remember { mutableStateOf(original?.frequency ?: "MONTH") }
    var interval by remember { mutableStateOf(original?.interval?.toString() ?: "1") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(if (original == null) "新增周期账单" else "编辑周期账单") },
        text = {
            Column(
                Modifier.heightIn(max = dialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it.take(50) },
                    Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                )
                ConfigChoice("类型", type, linkedMapOf("EXPENSE" to "支出", "INCOME" to "收入")) {
                    type = it
                    category = cats.firstOrNull { c -> c.type == it }?.id?.toString() ?: ""
                }
                CurrencyButton(currency) {
                    currency = it
                    amount = ""
                }
                OutlinedTextField(
                    amount,
                    { amount = it.take(24) },
                    Modifier.fillMaxWidth(),
                    label = { Text("金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                ConfigChoice(
                    "分类",
                    category,
                    linkedMapOf<String, String>().apply {
                        cats.filter { it.type == type }.forEach { put(it.id.toString(), it.name) }
                    },
                ) {
                    category = it
                }
                DateButton(date) { date = it }
                ConfigChoice("周期单位", frequency, frequencyNames) { frequency = it }
                OutlinedTextField(
                    interval,
                    { interval = it.take(3) },
                    Modifier.fillMaxWidth(),
                    label = { Text("每隔几个周期（1–365）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    note,
                    { note = it.take(1000) },
                    Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                )
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    feedback()
                    try {
                        val rule =
                            RecurringBill(
                                id = original?.id ?: 0,
                                name = name.trim(),
                                type = type,
                                amount = Money.parse(amount, currency) ?: kotlin.error("金额无效"),
                                currencyCode = currency,
                                categoryId = category.toLongOrNull() ?: kotlin.error("请选择分类"),
                                note = note,
                                nextDate = date.toString(),
                                frequency = frequency,
                                interval = interval.toIntOrNull() ?: kotlin.error("周期无效"),
                                anchorDay =
                                    if (
                                        original != null &&
                                            original.nextDate == date.toString() &&
                                            original.frequency == frequency
                                    )
                                        original.anchorDay
                                    else date.dayOfMonth,
                                anchorMonth =
                                    if (
                                        original != null &&
                                            original.nextDate == date.toString() &&
                                            original.frequency == frequency
                                    )
                                        original.anchorMonth
                                    else date.monthValue,
                                enabled = original?.enabled ?: true,
                            )
                        validateRecurring(rule, cats)
                        saving = true
                        vm.action {
                            try {
                                vm.repository.saveRecurring(rule)
                                dismiss()
                            } catch (e: Exception) {
                                error = e.message ?: "保存失败"
                                throw e
                            } finally {
                                saving = false
                            }
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "输入无效"
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("取消") } },
    )
}
