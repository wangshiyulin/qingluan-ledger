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

private data class PendingUpload(
    val config: WebDavConfig,
    val encrypted: String,
    val remote: RemoteBackup,
)

private data class PendingRestore(val raw: String, val password: CharArray, val count: Int)

@Composable
fun SyncPage(vm: LedgerViewModel, back: () -> Unit) {
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
    var transfer by remember { mutableStateOf<Pair<String, WebDavConfig>?>(null) }
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
    fun requestTransfer(mode: String) {
        try {
            transfer = mode to config()
            backupPassword = ""
        } catch (e: Exception) {
            status = e.message ?: "连接配置无效"
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(onClick = { if (!busy) back() else vm.message.value = "操作进行中，请稍候" })
            Text("同步", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        onClick = { requestTransfer("download") },
                    ) {
                        Text("下载恢复")
                    }
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
    transfer?.let { (mode, target) ->
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
                                    val remote = WebDavClient(target).stat()
                                    pendingUpload = PendingUpload(target, encrypted, remote)
                                    status = "备份已准备，等待确认上传"
                                } else {
                                    val downloaded = WebDavClient(target).download()
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
            title = { Text(if (pending.remote.exists) "覆盖远端备份？" else "上传完整备份？") },
            text = {
                Text(
                    if (pending.remote.exists)
                        "远端已有备份，本次上传将替换它。${pending.remote.modified?.let { "\n远端时间：$it" } ?: ""}"
                    else "将在配置的文件夹中创建加密备份。"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        pendingUpload = null
                        run("上传备份") {
                            WebDavClient(pending.config).upload(pending.encrypted, pending.remote)
                            vm.markBackup()
                            status = "备份上传完成"
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
                                vm.undo.value = emptyList()
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
private fun SyncCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                    "文件名为 ledger-backup.qlb，最大16MiB。覆盖上传使用 ETag 条件校验，服务端需支持 HEAD、GET、PUT 和条件写入。远端已变化时请重新操作；已有文件缺少有效 ETag 时拒绝覆盖。仅支持有效证书的 HTTPS，不跟随重定向。"
                )
            }
            SyncCard("同步方式") { Text("仅在你主动操作时上传或下载，不进行定时或后台同步。") }
        }
    }
}
