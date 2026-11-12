package com.qingluan.ledger

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SyncPage(vm: LedgerViewModel, back: () -> Unit) {
    var method by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = method.isNotEmpty() && LocalRouteActive.current) { method = "" }
    when (method) {
        "webdav" -> WebDavSyncPage(vm) { method = "" }
        "s3" -> S3SyncPage(vm) { method = "" }
        else ->
            Column(Modifier.fillMaxSize()) {
                FeatureHeader("同步", back)
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsActionCard("WebDAV", Icons.Outlined.CloudQueue, { method = "webdav" })
                    SettingsActionCard("S3 兼容对象存储", Icons.Outlined.Storage, { method = "s3" })
                }
            }
    }
}

private data class S3Transfer(val config: S3Config, val entry: WebDavHistoryEntry? = null)

private data class S3Restore(val raw: String, val password: CharArray)

@Composable
private fun S3SyncPage(vm: LedgerViewModel, back: () -> Unit) {
    val context = LocalContext.current
    val store = remember { S3Store(context.applicationContext) }
    val recoveryStore = remember { WebDavStore(context.applicationContext) }
    var endpoint by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var accessKey by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var prefix by remember { mutableStateOf("qingluan") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    var help by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var recovery by remember { mutableStateOf(false) }
    var transfer by remember { mutableStateOf<S3Transfer?>(null) }
    var password by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<S3Restore?>(null) }
    var history by remember { mutableStateOf<List<WebDavHistoryEntry>>(emptyList()) }
    var historyConfig by remember { mutableStateOf<S3Config?>(null) }
    DisposableEffect(Unit) { onDispose { pending?.password?.fill('\u0000') } }
    fun config() =
        S3Config(
                endpoint.trim(),
                bucket.trim(),
                region.trim(),
                accessKey.trim(),
                secretKey,
                prefix.trim().trim('/'),
                token,
            )
            .also { it.validate() }
    fun run(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        status = "$label…"
        vm.action(label = "S3") {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status =
                    if (e is IllegalArgumentException || e is IllegalStateException)
                        e.message ?: "操作失败"
                    else "操作失败，请检查配置或稍后重试"
                throw e
            } finally {
                busy = false
            }
        }
    }
    fun withConfig(action: (S3Config) -> Unit) {
        try {
            action(config())
        } catch (e: IllegalArgumentException) {
            status = e.message ?: "配置无效"
        }
    }
    LaunchedEffect(Unit) {
        try {
            store.load()?.let {
                endpoint = it.endpoint
                bucket = it.bucket
                region = it.region
                accessKey = it.accessKey
                secretKey = it.secretKey
                prefix = it.prefix
                token = it.sessionToken
            }
            recovery = recoveryStore.hasRecovery()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            status = "连接配置无法读取，请清除后重新填写"
        } finally {
            busy = false
        }
    }
    val exportRecovery =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri ->
            if (uri != null)
                run("导出恢复副本") {
                    val bytes = recoveryStore.recoveryBytes()
                    withContext(Dispatchers.IO) {
                        (context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件"))
                            .use { it.write(bytes) }
                    }
                    status = "恢复副本已导出，请使用恢复时填写的备份密码导入"
                }
        }
    BackHandler(enabled = (busy || help || historyConfig != null) && LocalRouteActive.current) {
        if (busy) vm.message.value = "操作进行中，请稍候"
        else if (help) help = false else historyConfig = null
    }
    if (help) {
        Column(Modifier.fillMaxSize()) {
            FeatureHeader("S3 同步说明", { help = false })
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("连接", style = MaterialTheme.typography.titleSmall)
                Text(
                    "使用已存在且支持路径式寻址（path-style）的 S3 存储桶。填写服务商提供的 HTTPS Endpoint、存储桶和 Region；地址不包含存储桶路径。部分服务使用 auto 区域，请以服务商文档为准。"
                )
                Text("权限", style = MaterialTheme.typography.titleSmall)
                Text("访问密钥需要列出指定目录、读取和写入对象的权限。测试连接仅验证列表读取，不写入测试文件。临时密钥还需填写会话令牌。")
                Text("备份与恢复", style = MaterialTheme.typography.titleSmall)
                Text(
                    "每次上传创建独立的加密完整备份，不覆盖旧文件。恢复前输入对应的备份密码、查看账单数量并确认覆盖；本机保留恢复前加密副本，可在同步页导出后通过数据管理导入。只列出当前目录内青鸾生成的备份。"
                )
                Text("密钥保护", style = MaterialTheme.typography.titleSmall)
                Text(
                    "访问密钥由 Android Keystore 加密保存在本机，不进入导出或日志。备份密码不保存，忘记后无法解密。修改配置后可直接上传或读取历史，配置会自动保存。"
                )
                Text("限制", style = MaterialTheme.typography.titleSmall)
                Text(
                    "仅手动上传与覆盖恢复，不后台同步、不自动合并。单份备份最多16MiB，列表最多1000个对象；超过时请整理独立目录。不支持自定义路径 Endpoint、HTTP、重定向或无效证书。请保证设备时间正确。"
                )
            }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader(
            if (historyConfig == null) "S3 兼容对象存储" else "S3 备份历史",
            {
                if (busy) vm.message.value = "操作进行中，请稍候"
                else if (historyConfig != null) historyConfig = null else back()
            },
        ) {
            IconButton(enabled = !busy, onClick = { help = true }) {
                Icon(Icons.AutoMirrored.Outlined.HelpOutline, "S3 同步说明")
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (historyConfig == null) {
                item {
                    SyncCard("S3 连接") {
                        S3Field(
                            "HTTPS Endpoint",
                            endpoint,
                            !busy,
                            { endpoint = it },
                            KeyboardType.Uri,
                        )
                        S3Field("存储桶", bucket, !busy, { bucket = it })
                        S3Field("区域 Region", region, !busy, { region = it })
                        S3Field(
                            "Access Key ID",
                            accessKey,
                            !busy,
                            { accessKey = it },
                            secret = true,
                        )
                        S3Field(
                            "Secret Access Key",
                            secretKey,
                            !busy,
                            { secretKey = it },
                            secret = true,
                        )
                        S3Field("目录前缀", prefix, !busy, { prefix = it })
                        S3Field("会话令牌（可选）", token, !busy, { token = it }, secret = true)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    withConfig { c ->
                                        run("保存连接") {
                                            store.save(c)
                                            status = "连接已保存"
                                        }
                                    }
                                },
                            ) {
                                Text("保存连接")
                            }
                            TextButton(
                                enabled = !busy,
                                onClick = {
                                    withConfig { c ->
                                        run("测试连接") {
                                            val entries = S3Client(c).history()
                                            status = "连接成功，发现 ${entries.size} 份备份"
                                        }
                                    }
                                },
                            ) {
                                Text("测试连接")
                            }
                            TextButton(enabled = !busy, onClick = { clear = true }) { Text("清除") }
                        }
                    }
                }
                item {
                    SyncCard("加密备份") {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                enabled = !busy,
                                onClick = {
                                    withConfig {
                                        transfer = S3Transfer(it)
                                        password = ""
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("上传备份")
                            }
                            OutlinedButton(
                                enabled = !busy,
                                onClick = {
                                    withConfig { c ->
                                        run("读取历史") {
                                            store.save(c)
                                            history = S3Client(c).history()
                                            historyConfig = c
                                            status = "已读取 ${history.size} 份备份"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("下载恢复")
                            }
                        }
                        if (recovery)
                            TextButton(
                                enabled = !busy,
                                onClick = { exportRecovery.launch("ledger-before-restore.qlb") },
                            ) {
                                Text("导出恢复前副本")
                            }
                    }
                }
            } else {
                item {
                    TextButton(
                        enabled = !busy,
                        onClick = {
                            val c = historyConfig!!
                            run("刷新历史") {
                                history = S3Client(c).history()
                                status = "已读取 ${history.size} 份备份"
                            }
                        },
                    ) {
                        Text("刷新")
                    }
                }
                if (history.isEmpty()) item { Text("暂无远端备份") }
                items(history, key = { it.filename }) { entry ->
                    SyncCard(
                        java.time.Instant.ofEpochMilli(entry.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            )
                    ) {
                        Text(entry.filename, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${(entry.size ?: 0)/1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(
                            enabled = !busy && WebDavHistory.strongEtag(entry.etag),
                            onClick = {
                                transfer = S3Transfer(historyConfig!!, entry)
                                password = ""
                            },
                        ) {
                            Text("下载恢复")
                        }
                        if (!WebDavHistory.strongEtag(entry.etag))
                            Text("服务器未提供有效 ETag", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (status.isNotBlank())
                item { Text(status, style = MaterialTheme.typography.bodySmall) }
        }
    }
    transfer?.let { request ->
        val upload = request.entry == null
        GlassAlertDialog(
            onDismissRequest = {
                transfer = null
                password = ""
            },
            title = { Text(if (upload) "上传加密完整备份" else "解密备份") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (upload) Text("将账单、分类、票据和个人设置加密上传至所选存储桶，不包含访问密钥和应用锁配置。")
                    OutlinedTextField(
                        password,
                        { password = it.take(128) },
                        Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("备份密码（8–128 位）") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = password.length in 8..128,
                    onClick = {
                        val secret = password.toCharArray()
                        password = ""
                        transfer = null
                        run(if (upload) "上传备份" else "校验备份") {
                            try {
                                store.save(request.config)
                                if (upload) {
                                    val raw = vm.localData.export()
                                    val encrypted =
                                        withContext(Dispatchers.Default) {
                                            BackupCipher.encrypt(raw, secret)
                                        }
                                    S3Client(request.config).upload(encrypted)
                                    status = "加密备份已上传"
                                } else {
                                    val encrypted =
                                        S3Client(request.config).download(request.entry!!)
                                    val raw =
                                        withContext(Dispatchers.Default) {
                                            BackupCipher.decrypt(encrypted, secret)
                                        }
                                    val snapshot =
                                        withContext(Dispatchers.Default) {
                                            CompleteBackup.decode(raw)
                                        }
                                    require(snapshot.personal != null) { "需要完整备份文件" }
                                    pending = S3Restore(raw, secret.copyOf())
                                    status = "备份已校验，等待确认恢复"
                                }
                            } finally {
                                secret.fill('\u0000')
                            }
                        }
                    },
                ) {
                    Text(if (upload) "上传" else "继续")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        transfer = null
                        password = ""
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
    pending?.let { snapshot ->
        RestoreReviewDialog(
            vm,
            snapshot.raw,
            dismiss = {
                snapshot.password.fill('\u0000')
                pending = null
            },
            beforeRestore = {
                val before = vm.localData.export()
                val secret = snapshot.password.copyOf()
                val copy =
                    try {
                        withContext(Dispatchers.Default) { BackupCipher.encrypt(before, secret) }
                    } finally {
                        secret.fill('\u0000')
                    }
                recoveryStore.preserve(copy)
                recovery = true
            },
            restored = {
                snapshot.password.fill('\u0000')
                pending = null
                status = "S3 备份已恢复，本机保留恢复前副本"
            },
        )
    }
    if (clear)
        GlassAlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("清除 S3 连接？") },
            text = { Text("不会删除存储桶内的备份或本机恢复副本。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        clear = false
                        run("清除连接") {
                            store.clear()
                            endpoint = ""
                            bucket = ""
                            region = "us-east-1"
                            accessKey = ""
                            secretKey = ""
                            prefix = "qingluan"
                            token = ""
                            status = "连接已清除"
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
private fun S3Field(
    label: String,
    value: String,
    enabled: Boolean,
    change: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
) {
    OutlinedTextField(
        value,
        { change(it.take(if (label.startsWith("会话令牌")) 8192 else 2048)) },
        Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        visualTransformation =
            if (secret) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions =
            KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
    )
}
