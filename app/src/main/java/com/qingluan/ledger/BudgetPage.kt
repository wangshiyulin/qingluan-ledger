package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BudgetPage(vm: LedgerViewModel, cats: List<Category>, back: () -> Unit) {
    val rules by vm.budgets.collectAsState()
    val rows by vm.entries.collectAsState()
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var period by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BudgetRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    val feedback = rememberAppFeedback()
    val zone = ZoneId.systemDefault()
    val spent =
        remember(rows, month, zone) {
            val span = DateSpan.month(month)
            val result = mutableMapOf<Pair<String, Long>, Long>()
            rows
                .asSequence()
                .filter { it.type == "EXPENSE" && span.contains(it) }
                .forEach { row ->
                    for (key in
                        listOf(row.currencyCode to 0L, row.currencyCode to row.categoryId)) result[
                        key] = Math.addExact(result[key] ?: 0L, row.amount)
                }
            result
        }
    val index = remember(cats) { cats.associateBy { it.id } }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("预算管理", back) {
            TextButton(
                onClick = {
                    feedback()
                    adding = true
                }
            ) {
                Text("新增")
            }
        }
        TextButton(onClick = { period = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("${month.year}年${month.monthValue}月")
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        if (rules.isEmpty())
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无预算")
            }
        else
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "每月预算 · 分类预算独立统计",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(rules, key = { "${it.currency}:${it.categoryId}" }) { rule ->
                    val used = spent[rule.key] ?: 0L
                    val remaining = rule.limit - used
                    val tint =
                        if (remaining < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    Card(
                        onClick = {
                            feedback()
                            editing = rule
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${rule.currency} · ${if (rule.categoryId == 0L) "总预算" else index[rule.categoryId]?.name ?: "已删除分类"}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "预算 ${visibleMoney(rule.limit, rule.currency, LocalExperience.current.hideAmounts)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "已用 ${visibleMoney(used, rule.currency, LocalExperience.current.hideAmounts)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (used.toDouble() / rule.limit).toFloat().coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = tint,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Text(
                                "${if (remaining < 0) "超支" else "剩余"} ${visibleMoney(if (remaining < 0) -remaining else remaining, rule.currency, LocalExperience.current.hideAmounts)}",
                                color = tint,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            }
    }
    if (period)
        PeriodPicker(month, "month", { period = false }) {
            month = it.withDayOfMonth(1)
            period = false
        }
    if (adding || editing != null)
        BudgetEditor(vm, cats, editing) {
            adding = false
            editing = null
        }
}

@Composable
private fun BudgetEditor(
    vm: LedgerViewModel,
    cats: List<Category>,
    original: BudgetRule?,
    dismiss: () -> Unit,
) {
    val defaultCurrency = LocalMainCurrency.current
    var currency by remember { mutableStateOf(original?.currency ?: defaultCurrency) }
    var categoryId by remember { mutableLongStateOf(original?.categoryId ?: 0L) }
    var amount by remember {
        mutableStateOf(original?.let { Money.text(it.limit, it.currency) } ?: "")
    }
    var categoryOpen by remember { mutableStateOf(false) }
    var currencyOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    GlassAlertDialog(
        onDismissRequest = { if (!submitting) dismiss() },
        title = { Text(if (original == null) "新增预算" else "编辑预算") },
        text = {
            Column(
                Modifier.heightIn(max = dialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("每月使用此预算", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { currencyOpen = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            currency,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                    OutlinedButton(
                        onClick = { categoryOpen = true },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            if (categoryId == 0L) "总预算"
                            else cats.find { it.id == categoryId }?.name ?: "已删除分类",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp))
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it.take(30) },
                    Modifier.fillMaxWidth(),
                    label = { Text("预算金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val limit = Money.parse(amount, currency)
                    if (limit == null || limit <= 0) error = "请输入有效的正数预算"
                    else if (
                        categoryId != 0L &&
                            cats.none { it.id == categoryId && it.type == "EXPENSE" }
                    )
                        error = "请选择有效的支出分类"
                    else {
                        submitting = true
                        vm.action {
                            try {
                                vm.saveBudget(original, BudgetRule(currency, categoryId, limit))
                                dismiss()
                            } finally {
                                submitting = false
                            }
                        }
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (original != null)
                    TextButton(enabled = !submitting, onClick = { deleting = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                TextButton(enabled = !submitting, onClick = dismiss) { Text("取消") }
            }
        },
    )
    if (currencyOpen)
        CurrencyPicker(currency, { currencyOpen = false }) {
            if (it != currency) {
                currency = it
                amount = ""
            }
            currencyOpen = false
        }
    if (categoryOpen)
        GlassAlertDialog(
            onDismissRequest = { categoryOpen = false },
            title = { Text("预算分类") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier =
                        Modifier.fillMaxWidth()
                            .heightIn(max = minOf(360.dp, dialogContentMaxHeight())),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "total") {
                        BudgetCategoryOption("总预算", categoryId == 0L) {
                            categoryId = 0L
                            categoryOpen = false
                        }
                    }
                    items(cats.filter { it.type == "EXPENSE" }, key = { it.id }) { category ->
                        BudgetCategoryOption(category.name, categoryId == category.id) {
                            categoryId = category.id
                            categoryOpen = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { categoryOpen = false }) { Text("取消") } },
        )
    if (deleting && original != null)
        GlassAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除预算？") },
            text = { Text("账单不会被删除。") },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        vm.action {
                            try {
                                vm.deleteBudget(original)
                                deleting = false
                                dismiss()
                            } finally {
                                submitting = false
                            }
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(enabled = !submitting, onClick = { deleting = false }) { Text("取消") }
            },
        )
}

@Composable
private fun BudgetCategoryOption(name: String, selected: Boolean, choose: () -> Unit) {
    val feedback = rememberAppFeedback()
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 52.dp)
                .selectable(
                    selected,
                    role = Role.RadioButton,
                    onClick = {
                        feedback()
                        choose()
                    },
                ),
        shape = RoundedCornerShape(12.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor =
            if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f),
            ),
    ) {
        Box(
            Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
