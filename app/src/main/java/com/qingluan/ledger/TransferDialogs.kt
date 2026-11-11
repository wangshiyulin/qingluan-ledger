package com.qingluan.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val formats = linkedMapOf("json" to "JSON", "csv" to "CSV", "encrypted" to "加密 JSON")

@Composable
fun TransferTools(vm: LedgerViewModel) {
    var export by rememberSaveable { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf("json") }
    var password by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val format = kind
                val secret = password.toCharArray()
                password = ""
                importing = false
                vm.action {
                    try {
                        val raw =
                            withContext(Dispatchers.IO) {
                                (context.contentResolver.openInputStream(uri) ?: error("无法读取文件"))
                                    .use { Backup.read(it) }
                            }
                        preview =
                            withContext(Dispatchers.Default) { decodeTransfer(raw, format, secret) }
                    } finally {
                        secret.fill('\u0000')
                    }
                }
            }
        }
    SettingsActionCard("导出", Icons.Outlined.UploadFile, { export = true })
    SettingsActionCard("导入", Icons.Outlined.FileDownload, { importing = true })
    if (export) ExportDialog(vm, dismiss = { export = false })
    if (importing)
        GlassAlertDialog(
            onDismissRequest = {
                importing = false
                password = ""
            },
            title = { Text("导入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConfigChoice("文件格式", kind, formats) { kind = it }
                    if (kind == "encrypted")
                        OutlinedTextField(
                            password,
                            { password = it.take(128) },
                            label = { Text("备份密码") },
                            visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
                        )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = kind != "encrypted" || password.length >= 8,
                    onClick = {
                        picker.launch(
                            if (kind == "csv")
                                arrayOf(
                                    "text/*",
                                    "application/csv",
                                    "application/vnd.ms-excel",
                                    "application/octet-stream",
                                )
                            else
                                arrayOf(
                                    "application/json",
                                    "application/octet-stream",
                                    "text/plain",
                                )
                        )
                    },
                ) {
                    Text("选择文件")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        importing = false
                        password = ""
                    }
                ) {
                    Text("取消")
                }
            },
        )
    preview?.let { data -> ImportDialog(vm, data) { preview = null } }
}

@Composable
private fun RangeControls(
    scope: String,
    changeScope: (String) -> Unit,
    from: LocalDate,
    to: LocalDate,
    changeFrom: (LocalDate) -> Unit,
    changeTo: (LocalDate) -> Unit,
    type: String,
    changeType: (String) -> Unit,
    currency: String,
    changeCurrency: (String) -> Unit,
    codes: List<String>,
    allowCurrent: Boolean,
) {
    val choices = linkedMapOf("all" to "全部", "dates" to "指定日期")
    if (allowCurrent) choices["current"] = "当前明细筛选"
    ConfigChoice("范围", scope, choices, changeScope)
    if (scope == "dates")
        Row(Modifier.fillMaxWidth()) {
            DateButton(from, changeFrom)
            DateButton(to, changeTo)
        }
    ConfigChoice(
        "收支",
        type,
        linkedMapOf("all" to "收入和支出", "EXPENSE" to "支出", "INCOME" to "收入"),
        changeType,
    )
    ConfigChoice(
        "币种",
        currency,
        linkedMapOf("all" to "全部币种").apply { codes.forEach { put(it, it) } },
        changeCurrency,
    )
}

@Composable
fun ExportDialog(vm: LedgerViewModel, fixedIds: Set<Long>? = null, dismiss: () -> Unit) {
    val rows by vm.entries.collectAsState()
    val cats by vm.categories.collectAsState()
    val current by vm.currentExportIds.collectAsState()
    val context = LocalContext.current
    var scope by remember { mutableStateOf("all") }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    var type by remember { mutableStateOf("all") }
    var currency by remember { mutableStateOf("all") }
    var kind by rememberSaveable { mutableStateOf("json") }
    var name by remember { mutableStateOf("青鸾记账-${LocalDate.now()}") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var includePersonal by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf(true) }
    var tags by remember { mutableStateOf(true) }
    var receipt by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    val valid = from <= to
    val range =
        if (valid)
            TransferRange(
                if (scope == "dates") from else null,
                if (scope == "dates") to else null,
                type,
                currency,
                fixedIds ?: if (scope == "current") current else null,
            )
        else null
    val selected =
        remember(rows, range) {
            if (range == null) emptyList() else rows.filter { range.contains(it) }
        }
    val complete = fixedIds == null && scope == "all" && type == "all" && currency == "all"
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri == null) {
                vm.pendingExport = null
                working = false
            } else
                vm.action {
                    try {
                        val raw = vm.pendingExport ?: error("导出已失效，请重新选择")
                        withContext(Dispatchers.IO) {
                            (context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件"))
                                .bufferedWriter(Charsets.UTF_8)
                                .use { it.write(raw) }
                        }
                        if (vm.pendingFullBackup) vm.markBackup()
                        vm.message.value = "已导出 ${vm.pendingExportCount} 笔"
                        dismiss()
                    } finally {
                        vm.pendingExport = null
                        working = false
                    }
                }
        }
    GlassAlertDialog(
        onDismissRequest = { if (!working) dismiss() },
        title = { Text(if (fixedIds == null) "导出" else "导出所选账单") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConfigChoice("文件格式", kind, formats) { kind = it }
                if (fixedIds == null)
                    RangeControls(
                        scope,
                        { scope = it },
                        from,
                        to,
                        { from = it },
                        { to = it },
                        type,
                        { type = it },
                        currency,
                        { currency = it },
                        rows.map { it.currencyCode }.distinct(),
                        true,
                    )
                Text(
                    "${selected.size} 笔 · ${selected.map{it.currencyCode}.distinct().joinToString(" / ")}"
                )
                if (!valid) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
                if (kind != "csv" && complete)
                    ConfigSwitch("完整备份（资料、回收站、周期规则、预算、标签模板）", includePersonal) { includePersonal = it }
                if (kind != "csv" && (!complete || !includePersonal))
                    Text("仅导出所选账单及分类，不包含回收站、周期规则、预算和标签模板。", style = MaterialTheme.typography.bodySmall)
                if (kind == "csv") {
                    Text("日期、时间、收支、分类、币种、金额为必需列", style = MaterialTheme.typography.labelSmall)
                    ConfigSwitch("备注列", notes) { notes = it }
                    ConfigSwitch("标签列", tags) { tags = it }
                    ConfigSwitch("票据列", receipt) { receipt = it }
                }
                OutlinedTextField(
                    name,
                    { name = it.take(80).replace('/', '-').replace('\\', '-') },
                    label = { Text("文件名") },
                    singleLine = true,
                )
                if (kind == "encrypted") {
                    OutlinedTextField(
                        password,
                        { password = it.take(128) },
                        label = { Text("密码（至少8位）") },
                        visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                    OutlinedTextField(
                        confirmPassword,
                        { confirmPassword = it.take(128) },
                        label = { Text("确认密码") },
                        visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Password),
                    )
                    Text("密码无法找回，请妥善保存。", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    valid &&
                        name.isNotBlank() &&
                        !working &&
                        (kind != "encrypted" ||
                            password.length >= 8 && password == confirmPassword),
                onClick = {
                    working = true
                    val secret = password.toCharArray()
                    password = ""
                    confirmPassword = ""
                    val chosen = selected.toList()
                    val format = kind
                    val personal = complete && includePersonal
                    val includeNotes = notes
                    val includeTags = tags
                    val includeReceipt = receipt
                    val filename = name
                    val categories = cats.toList()
                    vm.action {
                        try {
                            val raw =
                                if (format == "csv")
                                    withContext(Dispatchers.Default) {
                                        CsvExport.encode(
                                            categories,
                                            chosen,
                                            includeNotes,
                                            includeTags,
                                            includeReceipt,
                                        )
                                    }
                                else if (personal) vm.localData.export()
                                else
                                    withContext(Dispatchers.Default) {
                                        Backup.encode(categories, chosen)
                                    }
                            vm.pendingExport =
                                if (format == "encrypted")
                                    withContext(Dispatchers.Default) {
                                        BackupCipher.encrypt(raw, secret)
                                    }
                                else raw
                            require(
                                vm.pendingExport!!.toByteArray(Charsets.UTF_8).size <=
                                    CompleteBackup.MAX_BYTES
                            ) {
                                "文件超过16MB，请缩小范围"
                            }
                            vm.pendingFullBackup = personal && format != "csv"
                            vm.pendingExportCount = chosen.size
                            picker.launch(
                                filename +
                                    when (format) {
                                        "csv" -> ".csv"
                                        "encrypted" -> ".qlb"
                                        else -> ".json"
                                    }
                            )
                        } catch (e: Exception) {
                            working = false
                            throw e
                        } finally {
                            secret.fill('\u0000')
                        }
                    }
                },
            ) {
                Text(if (working) "处理中" else "选择保存位置")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text("取消") } },
    )
}

@Composable
internal fun ImportDialog(vm: LedgerViewModel, data: ImportPreview, dismiss: () -> Unit) {
    val rows by vm.entries.collectAsState()
    val categories by vm.categories.collectAsState()
    val restoreDetails by
        produceState<Triple<Int, Int, Boolean>?>(null, data.fullJson) {
            val raw = data.fullJson
            if (raw != null) {
                value =
                    withContext(Dispatchers.Default) {
                        // decodeTransfer already validated the complete document; inspect metadata
                        // only.
                        val json = org.json.JSONObject(raw)
                        Triple(
                            json.optJSONArray("trash")?.length() ?: 0,
                            json.optJSONArray("recurring")?.length() ?: 0,
                            !json.has("trash") || !json.has("recurring"),
                        )
                    }
            }
        }
    var acceptValidRows by remember(data) { mutableStateOf(false) }
    var showSamples by remember(data) { mutableStateOf(false) }
    var showErrors by remember(data) { mutableStateOf(false) }
    var scope by remember { mutableStateOf("all") }
    var from by remember { mutableStateOf(data.from ?: LocalDate.now()) }
    var to by remember { mutableStateOf(data.to ?: LocalDate.now()) }
    var type by remember { mutableStateOf("all") }
    var currency by remember { mutableStateOf("all") }
    var mode by remember { mutableStateOf("append") }
    var skip by remember { mutableStateOf(true) }
    var confirm by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val valid = from <= to
    val range =
        if (valid)
            TransferRange(
                if (scope == "dates") from else null,
                if (scope == "dates") to else null,
                type,
                currency,
            )
        else null
    val bills =
        remember(range, data) {
            if (range == null) emptyList() else data.bills.filter { range.contains(it) }
        }
    val counts =
        remember(data, categories, rows, range, mode) {
            range?.let { importCounts(data, categories, rows, it, mode == "replace") }
        }
    val modes = linkedMapOf("append" to "追加账单", "replace" to "替换所选范围")
    if (data.fullJson != null && scope == "all" && type == "all" && currency == "all")
        modes["full"] = "完整恢复（覆盖本地数据）"
    LaunchedEffect(modes.keys) { if (mode !in modes) mode = "append" }
    fun runImport() {
        working = true
        val chosenMode = mode
        val chosenRange = range!!
        val chosenBills = bills.toList()
        val skipExisting = skip
        vm.action {
            try {
                vm.clearUndo()
                if (chosenMode == "full") {
                    vm.restoreData(data.fullJson!!)
                    vm.message.value = "完整恢复完成"
                } else {
                    val result =
                        vm.localData.importBills(
                            chosenBills,
                            chosenRange,
                            chosenMode == "replace",
                            skipExisting,
                        )
                    vm.message.value = "已导入 ${result.added} 笔，跳过 ${result.skipped} 笔"
                }
                dismiss()
            } finally {
                working = false
            }
        }
    }
    GlassAlertDialog(
        onDismissRequest = { if (!working) dismiss() },
        title = { Text("导入预览") },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("文件 ${data.totalRows} 笔 · 有效 ${data.bills.size} · 无效 ${data.errors.size}")
                Text(
                    "${data.from ?: "—"} 至 ${data.to ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (data.errors.isNotEmpty()) {
                    TextButton(onClick = { showErrors = !showErrors }) {
                        Text(if (showErrors) "收起无效行" else "查看无效行")
                    }
                    if (showErrors) {
                        data.errors.take(20).forEach {
                            Text(
                                "第${it.line}行：${it.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        if (data.errors.size > 20)
                            Text("仅显示前20条错误", style = MaterialTheme.typography.labelSmall)
                    }
                    ConfigSwitch("仅导入有效行（${data.bills.size}笔）", acceptValidRows) {
                        acceptValidRows = it
                    }
                }
                RangeControls(
                    scope,
                    { scope = it },
                    from,
                    to,
                    { from = it },
                    { to = it },
                    type,
                    { type = it },
                    currency,
                    { currency = it },
                    data.bills.map { it.currency }.distinct(),
                    false,
                )
                counts?.let {
                    if (mode == "full") {
                        Text("完整恢复 ${data.bills.size} 笔")
                        restoreDetails?.let { details ->
                            Text(
                                "回收站 ${details.first} 笔 · 周期记账 ${details.second} 项",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (details.third)
                                Text(
                                    "旧备份未包含的回收站或周期记账将清空本地对应数据。",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                        }
                    } else {
                        Text("新增 ${it.new} · 重复 ${it.duplicates} · 筛选排除 ${it.excluded}")
                        Text("将导入 ${it.importing(skip)} 笔")
                        if (mode == "replace")
                            Text(
                                "将替换本地 ${rows.count { row -> range?.contains(row) == true }} 笔",
                                style = MaterialTheme.typography.bodySmall,
                            )
                    }
                }
                if (bills.isNotEmpty()) {
                    TextButton(onClick = { showSamples = !showSamples }) {
                        Text(if (showSamples) "收起账单预览" else "查看账单预览")
                    }
                    if (showSamples) {
                        bills.take(5).forEach { bill ->
                            HorizontalDivider()
                            Text(
                                "${java.time.Instant.ofEpochMilli(bill.occurredAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} · ${if (bill.type == "INCOME") "收入" else "支出"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "${bill.category.take(40)} · ${bill.currency} ${Money.text(bill.amount, bill.currency)}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                            if (bill.note.isNotBlank())
                                Text(
                                    bill.note.take(80),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                )
                        }
                        if (bills.size > 5)
                            Text("仅显示前5笔", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (!valid) Text("开始日期不能晚于结束日期", color = MaterialTheme.colorScheme.error)
                ConfigChoice("导入方式", mode, modes) { mode = it }
                if (mode != "full")
                    ConfigChoice(
                        "重复记录",
                        if (skip) "skip" else "keep",
                        linkedMapOf("skip" to "跳过已有", "keep" to "保留全部"),
                    ) {
                        skip = it == "skip"
                    }
            }
        },
        confirmButton = {
            TextButton(
                enabled =
                    valid &&
                        !working &&
                        (data.errors.isEmpty() || acceptValidRows) &&
                        (mode == "full" || bills.isNotEmpty()),
                onClick = { if (mode == "append") runImport() else confirm = true },
            ) {
                Text(if (working) "处理中" else "导入")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text("取消") } },
    )
    if (confirm)
        GlassAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (mode == "full") "覆盖全部本地数据？" else "替换所选范围的账单？") },
            text = {
                Text(
                    if (mode == "full") "当前账单、分类、回收站、周期记账、预算、标签模板及备份包含的资料设置将被替换。旧备份未包含的回收站、周期记账、预算或标签模板会清空对应数据，未包含主币种时恢复为CNY。"
                    else "范围外账单将保留。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        runImport()
                    }
                ) {
                    Text("确认替换")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
}
