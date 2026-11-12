package com.qingluan.ledger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class PendingUpload(val config: WebDavConfig, val encrypted: String, val keep: Int)

private data class WebDavTransfer(
    val mode: String,
    val config: WebDavConfig,
    val entry: WebDavHistoryEntry? = null,
)

private data class PendingRestore(val raw: String, val password: CharArray, val count: Int)

@Composable
fun WebDavSyncPage(vm: LedgerViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { WebDavStore(context.applicationContext) }
    var address by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var backupPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf(false) }
    var pendingUpload by remember { mutableStateOf<PendingUpload?>(null) }
    var pendingRestore by remember { mutableStateOf<PendingRestore?>(null) }
    var clear by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<WebDavTransfer?>(null) }
    var historyOpen by remember { mutableStateOf(false) }
    var historyConfig by remember { mutableStateOf<WebDavConfig?>(null) }
    var history by remember { mutableStateOf<List<WebDavHistoryEntry>>(emptyList()) }
    var pendingDelete by remember { mutableStateOf<WebDavHistoryEntry?>(null) }
    val preferences = remember {
        context.getSharedPreferences("webdav-history", android.content.Context.MODE_PRIVATE)
    }
    var retention by remember {
        mutableStateOf(preferences.getInt("keep", 0).takeIf { it in setOf(0, 5, 10, 20) } ?: 0)
    }
    var retentionPicker by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { pendingRestore?.password?.fill('\u0000') } }
    LaunchedEffect(Unit) {
        try {
            store.load()?.let {
                address = it.address
                username = it.username
                password = it.password
            }
            recovery = store.hasRecovery()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "连接配置无法读取，请清除后重新填写"
        } finally {
            busy = false
        }
    }
    fun run(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = label + "…"
        vm.action(label = "WebDAV") {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status =
                    when (e) {
                        is IllegalArgumentException,
                        is IllegalStateException -> e.message ?: "操作失败"
                        else -> "操作失败，请检查配置或稍后重试"
                    }
                throw e
            } finally {
                busy = false
            }
        }
    }
    fun config() = WebDavConfig(address.trim(), username, password).also { it.collection() }
    val exportRecovery =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null)
                run("导出恢复副本") {
                    val bytes = store.recoveryBytes()
                    withContext(Dispatchers.IO) {
                        (context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件"))
                            .use { it.write(bytes) }
                    }
                    status = "恢复副本已导出，使用恢复前填写的备份密码解密"
                }
        }
    BackHandler(enabled = busy && LocalRouteActive.current) { vm.message.value = "操作进行中，请稍候" }
    BackHandler(enabled = help && LocalRouteActive.current) { help = false }
    if (help) {
        SyncHelpPage { help = false }
        return
    }
    fun requestTransfer(
        mode: String,
        target: WebDavConfig? = null,
        entry: WebDavHistoryEntry? = null,
    ) {
        try {
            transfer = WebDavTransfer(mode, target ?: config(), entry)
            backupPassword = ""
        } catch (e: Exception) {
            status = e.message ?: "连接配置无效"
        }
    }
    if (!historyOpen)
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageBackButton(onClick = { if (!busy) back() else vm.message.value = "操作进行中，请稍候" })
                Text("WebDAV", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = !busy, onClick = { help = true }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, "同步说明")
                }
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncCard("WebDAV 连接") {
                    OutlinedTextField(
                        address,
                        { address = it },
                        Modifier.fillMaxWidth().testTag("webdav-address"),
                        enabled = !busy,
                        label = { Text("HTTPS 文件夹地址") },
                        placeholder = { Text("https://dav.example.com/ledger/") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        username,
                        { username = it },
                        Modifier.fillMaxWidth().testTag("webdav-username"),
                        enabled = !busy,
                        label = { Text("用户名") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        password,
                        { password = it },
                        Modifier.fillMaxWidth().testTag("webdav-password"),
                        enabled = !busy,
                        label = { Text("密码或应用密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                            ),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                run("保存连接") {
                                    store.save(config())
                                    status = "连接已加密保存在本机"
                                }
                            },
                        ) {
                            Text("保存连接")
                        }
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                run("测试连接") {
                                    val remote = WebDavClient(config()).test()
                                    status = if (remote.exists) "连接成功 · 远端已有备份" else "连接成功 · 远端暂无备份"
                                }
                            },
                        ) {
                            Text("测试连接")
                        }
                        TextButton(enabled = !busy, onClick = { clear = true }) { Text("清除") }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = { requestTransfer("upload") },
                        ) {
                            Text("上传备份")
                        }
                        OutlinedButton(
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                run("读取备份历史") {
                                    val target = config()
                                    store.save(target)
                                    historyConfig = target
                                    history = emptyList()
                                    historyOpen = true
                                    history = WebDavClient(target).history()
                                    status = if (history.isEmpty()) "暂无远端备份" else ""
                                }
                            },
                        ) {
                            Text("下载恢复")
                        }
                    }
                    TextButton(enabled = !busy, onClick = { retentionPicker = true }) {
                        Text("历史保留：${if (retention == 0) "不限" else "最近 $retention 份"}")
                    }
                    if (recovery)
                        OutlinedButton(
                            enabled = !busy,
                            onClick = { exportRecovery.launch("ledger-before-webdav-restore.qlb") },
                        ) {
                            Text("导出恢复前副本")
                        }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (status.isNotBlank())
                    Text(
                        status,
                        Modifier.testTag("webdav-status"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                Spacer(Modifier.height(24.dp))
            }
        }
    if (historyOpen) {
        WebDavHistoryPage(
            entries = history,
            busy = busy,
            status = status,
            back = { if (!busy) historyOpen = false },
            refresh = {
                historyConfig?.let { target ->
                    run("读取备份历史") {
                        history = WebDavClient(target).history()
                        status = if (history.isEmpty()) "暂无远端备份" else ""
                    }
                }
            },
            restore = { entry -> historyConfig?.let { requestTransfer("download", it, entry) } },
            legacy = { historyConfig?.let { requestTransfer("download", it) } },
            delete = { pendingDelete = it },
        )
    }
    BackHandler(enabled = historyOpen && !busy && LocalRouteActive.current) { historyOpen = false }
    if (retentionPicker)
        GlassAlertDialog(
            onDismissRequest = { retentionPicker = false },
            title = { Text("历史保留数量") },
            text = {
                Column {
                    listOf(0, 5, 10, 20).forEach { keep ->
                        TextButton(
                            onClick = {
                                retention = keep
                                preferences.edit().putInt("keep", keep).apply()
                                retentionPicker = false
                            }
                        ) {
                            Text(if (keep == 0) "不限" else "最近 $keep 份")
                        }
                    }
                }
            },
            confirmButton = {},
        )
    pendingDelete?.let { entry ->
        GlassAlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("删除远端备份？") },
            text = { Text(entry.filename + "\n删除后无法撤回。") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingDelete = null
                        historyConfig?.let { target ->
                            run("删除远端备份") {
                                val client = WebDavClient(target)
                                client.delete(entry)
                                history = history.filterNot { it.filename == entry.filename }
                                status = "远端备份已删除"
                            }
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
    transfer?.let { request ->
        val mode = request.mode
        val target = request.config
        GlassAlertDialog(
            onDismissRequest = {
                transfer = null
                backupPassword = ""
            },
            title = { Text(if (mode == "upload") "上传备份" else "下载恢复") },
            text = {
                OutlinedTextField(
                    backupPassword,
                    { backupPassword = it },
                    Modifier.fillMaxWidth().testTag("webdav-backup-password"),
                    label = { Text("备份密码（8–128 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                        ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = backupPassword.length in 8..128,
                    onClick = {
                        val secret = backupPassword.toCharArray()
                        backupPassword = ""
                        transfer = null
                        run(if (mode == "upload") "准备上传" else "下载备份") {
                            try {
                                store.save(target)
                                if (mode == "upload") {
                                    val raw = vm.localData.export()
                                    val encrypted =
                                        withContext(Dispatchers.Default) {
                                            BackupCipher.encrypt(raw, secret)
                                        }
                                    pendingUpload = PendingUpload(target, encrypted, retention)
                                    status = "备份已准备，等待确认上传"
                                } else {
                                    val client = WebDavClient(target)
                                    val downloaded =
                                        request.entry?.let { client.download(it) }
                                            ?: client.download()
                                    val raw =
                                        withContext(Dispatchers.Default) {
                                            BackupCipher.decrypt(downloaded.encrypted, secret)
                                        }
                                    val data =
                                        withContext(Dispatchers.Default) {
                                            CompleteBackup.decode(raw)
                                        }
                                    require(data.personal != null) { "需要完整备份文件" }
                                    pendingRestore =
                                        PendingRestore(raw, secret.copyOf(), data.entries.size)
                                    status = "下载并校验完成，等待确认恢复"
                                }
                            } finally {
                                secret.fill('\u0000')
                            }
                        }
                    },
                ) {
                    Text("继续")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        transfer = null
                        backupPassword = ""
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    pendingUpload?.let { pending ->
        GlassAlertDialog(
            onDismissRequest = {
                pendingUpload = null
                status = "已取消上传"
            },
            title = { Text("上传完整备份？") },
            text = {
                Text(
                    "将在配置的文件夹中创建新的加密备份。" +
                        if (pending.keep == 0) ""
                        else "\n上传成功后保留最近 ${pending.keep} 份历史备份，并删除更早的青鸾历史备份。"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingUpload = null
                        run("上传备份") {
                            val client = WebDavClient(pending.config)
                            client.uploadHistory(pending.encrypted)
                            vm.markBackup()
                            status = "备份上传完成"
                            if (pending.keep != 0) {
                                try {
                                    val removed = client.retainHistory(pending.keep)
                                    status = "备份上传完成 · 已清理 $removed 份历史备份"
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    status = "备份上传完成，历史清理未完成；请刷新历史后检查"
                                }
                            }
                        }
                    },
                ) {
                    Text("上传")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingUpload = null
                        status = "已取消上传"
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    pendingRestore?.let { pending ->
        GlassAlertDialog(
            onDismissRequest = {
                if (!busy) {
                    pendingRestore?.password?.fill('\u0000')
                    pendingRestore = null
                    status = "已取消恢复"
                }
            },
            title = { Text("恢复远端 ${pending.count} 笔账单？") },
            text = { Text("将覆盖本地账单、分类、票据和个人设置。恢复前的加密副本会保留在本机，可在本页导出。应用锁和连接配置不变。") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingRestore = null
                        run("恢复备份") {
                            try {
                                val before = vm.localData.export()
                                val copy =
                                    withContext(Dispatchers.Default) {
                                        BackupCipher.encrypt(before, pending.password)
                                    }
                                store.preserve(copy)
                                recovery = true
                                vm.localData.restore(pending.raw)
                                vm.clearUndo()
                                vm.currentExportIds.value = emptySet()
                                status = "远端备份已恢复，本机保留恢复前副本"
                            } finally {
                                pending.password.fill('\u0000')
                            }
                        }
                    },
                ) {
                    Text("覆盖恢复")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingRestore?.password?.fill('\u0000')
                        pendingRestore = null
                        status = "已取消恢复"
                    },
                ) {
                    Text("取消")
                }
            },
        )
    }
    if (clear)
        GlassAlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("清除连接配置？") },
            text = { Text("不会删除远端备份和本机恢复副本。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clear = false
                        run("清除连接") {
                            store.clear()
                            address = ""
                            username = ""
                            password = ""
                            backupPassword = ""
                            status = "连接配置已清除"
                        }
                    }
                ) {
                    Text("清除")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } },
        )
}

@Composable
internal fun SyncCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SyncHelpPage(back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onClick = back)
            Text("同步说明", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SyncCard("连接与使用") {
                Text("填写已存在的 HTTPS 文件夹地址、用户名与密码。可直接点击上传备份或下载恢复，连接配置会自动加密保存在本机；也可单独保存或测试连接。")
            }
            SyncCard("备份密码") {
                Text("操作时输入独立备份密码（8–128 位），不保存在设备上。下载需要上传时使用的同一密码，忘记后无法解密。账号和服务器密码不会进入导出文件或日志。")
            }
            SyncCard("上传与恢复范围") {
                Text(
                    "上传包含全部账单、分类、票据、头像和个人设置。下载会先校验并预览数量，确认后覆盖本地数据，不自动合并；应用锁和连接配置保持不变。恢复前保留加密副本，可在同步页导出。"
                )
            }
            SyncCard("远端文件与冲突") {
                Text(
                    "每次上传创建带 UTC 时间和随机编号的 .qlb 文件，最大16MiB。历史列表需要服务器支持 PROPFIND；不支持时可使用旧版备份恢复。选中历史备份恢复、删除均使用 ETag 校验，缺少有效 ETag 时无法操作；旧版 ledger-backup.qlb 始终保留。历史保留可设为不限、5、10、20 份，清理仅在上传成功后执行。仅支持有效证书的 HTTPS，不跟随重定向。"
                )
            }
            SyncCard("同步方式") { Text("仅在你主动操作时上传或下载，不进行定时或后台同步。") }
        }
    }
}

@Composable
private fun WebDavHistoryPage(
    entries: List<WebDavHistoryEntry>,
    busy: Boolean,
    status: String,
    back: () -> Unit,
    refresh: () -> Unit,
    restore: (WebDavHistoryEntry) -> Unit,
    legacy: () -> Unit,
    delete: (WebDavHistoryEntry) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onClick = back)
            Text("远端备份历史", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(enabled = !busy, onClick = refresh) { Text("刷新") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.weight(1f).padding(horizontal = 22.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedButton(enabled = !busy, onClick = legacy) { Text("恢复旧版 ledger-backup.qlb") }
            }
            if (status.isNotBlank())
                item { Text(status, Modifier.testTag("webdav-history-status")) }
            if (entries.isEmpty() && !busy && status.isBlank()) item { Text("暂无远端备份") }
            items(entries.size, key = { entries[it].filename }) { index ->
                val entry = entries[index]
                SyncCard(if (entry.legacy) "旧版备份" else "历史备份") {
                    Text(entry.filename, style = MaterialTheme.typography.bodyMedium)
                    val date =
                        if (entry.timestamp > 0)
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(java.time.Instant.ofEpochMilli(entry.timestamp))
                        else entry.modified ?: "时间未知"
                    Text(
                        "$date · ${entry.size?.let { "$it 字节" } ?: "大小未知"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !busy && WebDavHistory.strongEtag(entry.etag),
                            onClick = { restore(entry) },
                        ) {
                            Text("下载恢复")
                        }
                        if (!entry.legacy)
                            TextButton(
                                enabled = !busy && entry.deletable,
                                onClick = { delete(entry) },
                            ) {
                                Text("删除")
                            }
                    }
                    if (!WebDavHistory.strongEtag(entry.etag))
                        Text("服务器未提供有效 ETag", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
