package com.qingluan.ledger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
    ListItem(modifier = Modifier.clickable { export = true }, headlineContent = { Text("导出") })
    ListItem(modifier = Modifier.clickable { importing = true }, headlineContent = { Text("导入") })
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
                    ConfigSwitch("包含个人资料与设置", includePersonal) { includePersonal = it }
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
                    )
                    OutlinedTextField(
                        confirmPassword,
                        { confirmPassword = it.take(128) },
                        label = { Text("确认密码") },
                        visualTransformation = PasswordVisualTransformation(),
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
private fun ImportDialog(vm: LedgerViewModel, data: ImportPreview, dismiss: () -> Unit) {
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
                vm.undo.value = emptyList()
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
                Text("文件 ${data.bills.size} 笔 · ${data.from ?: "—"} 至 ${data.to ?: "—"}")
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
                Text(
                    "将导入 ${bills.size} 笔 · ${bills.map{it.currency}.distinct().joinToString(" / ")}"
                )
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
                enabled = valid && !working && (mode == "full" || bills.isNotEmpty()),
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
            text = { Text(if (mode == "full") "当前账单、分类及备份包含的资料设置将被替换。" else "范围外账单将保留。") },
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
