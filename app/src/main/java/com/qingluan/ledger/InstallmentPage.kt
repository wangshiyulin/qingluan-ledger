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
import java.time.LocalDate

@Composable
fun InstallmentPage(vm: LedgerViewModel, cats: List<Category>, back: () -> Unit) {
    val plans by vm.installments.collectAsState()
    val today by vm.today.collectAsState()
    var editing by remember { mutableStateOf<InstallmentPlan?>(null) }
    var creating by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<InstallmentPlan?>(null) }
    var deleting by remember { mutableStateOf<InstallmentPlan?>(null) }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("分期账单", back) { TextButton(onClick = { creating = true }) { Text("新增") } }
        Text(
            "每月手动确认一期支出；删除已生成账单不会回退分期进度。",
            Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        if (plans.isEmpty())
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无分期账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        else
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    val complete = plan.paidTerms == plan.terms
                    val due =
                        !complete && plan.enabled && !LocalDate.parse(plan.nextDate).isAfter(today)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "已入账 ${plan.paidTerms}/${plan.terms} 期 · ${if (complete) "已完成" else if (!plan.enabled) "已暂停" else if (due) "待确认" else "未到期"}"
                            )
                            if (!complete)
                                Text(
                                    "${plan.nextDate} · ${plan.currencyCode} ${visibleMoney(installmentTermAmount(plan), plan.currencyCode, LocalExperience.current.hideAmounts)}"
                                )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = { editing = plan }) { Text("编辑") }
                                if (!complete)
                                    TextButton(
                                        onClick = {
                                            vm.action {
                                                vm.repository.setInstallmentEnabled(
                                                    plan.id,
                                                    plan.paidTerms,
                                                    !plan.enabled,
                                                )
                                            }
                                        }
                                    ) {
                                        Text(if (plan.enabled) "暂停" else "启用")
                                    }
                                TextButton(onClick = { deleting = plan }) { Text("删除") }
                            }
                            if (due)
                                Button(
                                    onClick = { confirming = plan },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("确认本期入账")
                                }
                        }
                    }
                }
            }
    }
    if (creating || editing != null)
        InstallmentEditor(vm, cats, editing) {
            creating = false
            editing = null
        }
    confirming?.let { plan ->
        GlassAlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("确认本期入账？") },
            text = {
                Text("${plan.name} 第${plan.paidTerms + 1}/${plan.terms}期将生成一笔支出。删除该账单不会回退分期进度。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = null
                        vm.action { vm.repository.confirmInstallment(plan.id, plan.paidTerms) }
                    }
                ) {
                    Text("确认入账")
                }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("取消") } },
        )
    }
    deleting?.let { plan ->
        GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除分期计划？") },
            text = { Text("仅删除计划，不会撤销或删除已经确认生成的账单。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        vm.action { vm.repository.deleteInstallment(plan.id, plan.paidTerms) }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun InstallmentEditor(
    vm: LedgerViewModel,
    cats: List<Category>,
    original: InstallmentPlan?,
    dismiss: () -> Unit,
) {
    val mainCurrency = LocalMainCurrency.current
    var name by remember { mutableStateOf(original?.name ?: "") }
    var currency by remember { mutableStateOf(original?.currencyCode ?: mainCurrency) }
    var principal by remember {
        mutableStateOf(original?.let { Money.text(it.principal, it.currencyCode) } ?: "")
    }
    var fee by remember {
        mutableStateOf(original?.let { Money.text(it.fee, it.currencyCode) } ?: "0")
    }
    var terms by remember { mutableStateOf(original?.terms?.toString() ?: "12") }
    var category by remember {
        mutableStateOf(
            (original?.categoryId ?: cats.firstOrNull { it.type == "EXPENSE" }?.id)?.toString()
                ?: ""
        )
    }
    var date by remember {
        mutableStateOf(original?.nextDate?.let(LocalDate::parse) ?: LocalDate.now())
    }
    var note by remember { mutableStateOf(original?.note ?: "") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val locked = (original?.paidTerms ?: 0) > 0
    GlassAlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(if (original == null) "新增分期账单" else "编辑分期账单") },
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
                if (locked) Text("已入账后，币种、金额、期数和日期不可修改。")
                if (locked) Text("币种：$currency")
                else
                    CurrencyButton(currency) {
                        currency = it
                        principal = ""
                        fee = "0"
                    }
                OutlinedTextField(
                    principal,
                    { principal = it.take(24) },
                    Modifier.fillMaxWidth(),
                    enabled = !locked,
                    label = { Text("本金") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    fee,
                    { fee = it.take(24) },
                    Modifier.fillMaxWidth(),
                    enabled = !locked,
                    label = { Text("总手续费（可为0）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    terms,
                    { terms = it.take(3) },
                    Modifier.fillMaxWidth(),
                    enabled = !locked,
                    label = { Text("每月一期，共几期（1–120）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                ConfigChoice(
                    "支出分类",
                    category,
                    linkedMapOf<String, String>().apply {
                        cats
                            .filter { it.type == "EXPENSE" }
                            .forEach { put(it.id.toString(), it.name) }
                    },
                ) {
                    category = it
                }
                if (locked) Text("下期日期：${original?.nextDate}")
                else {
                    Text("首期日期（之后每月按此日还款，月末自动适配）", style = MaterialTheme.typography.bodySmall)
                    DateButton(date) { date = it }
                }
                OutlinedTextField(
                    note,
                    { note = it.take(900) },
                    Modifier.fillMaxWidth(),
                    label = { Text("备注（最多900字）") },
                )
                Text("本金与总手续费按最小货币单位均分，余数分配到前几期。", style = MaterialTheme.typography.bodySmall)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    try {
                        val feeAmount =
                            if (fee.matches(Regex("0+(\\.0*)?"))) 0L
                            else Money.parse(fee, currency) ?: kotlin.error("手续费无效")
                        val plan =
                            InstallmentPlan(
                                id = original?.id ?: 0,
                                name = name.trim(),
                                categoryId = category.toLongOrNull() ?: kotlin.error("请选择分类"),
                                currencyCode = currency,
                                principal =
                                    Money.parse(principal, currency) ?: kotlin.error("本金无效"),
                                fee = feeAmount,
                                terms = terms.toIntOrNull() ?: kotlin.error("期数无效"),
                                paidTerms = original?.paidTerms ?: 0,
                                nextDate = date.toString(),
                                anchorDay =
                                    if (original != null && original.nextDate == date.toString())
                                        original.anchorDay
                                    else date.dayOfMonth,
                                enabled = original?.enabled ?: true,
                                note = note,
                            )
                        Installments.validate(plan, cats)
                        saving = true
                        vm.action {
                            try {
                                vm.repository.saveInstallment(plan)
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
                Text(if (saving) "保存中" else "保存")
            }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("取消") } },
    )
}
