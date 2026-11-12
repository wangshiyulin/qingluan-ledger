package com.qingluan.ledger

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun StoragePage(vm: LedgerViewModel, back: () -> Unit) {
    val context = LocalContext.current
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf("") }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
    fun run(message: String = "", block: suspend () -> Unit = {}) {
        if (busy) return
        busy = true
        vm.action(record = false) {
            try {
                block()
                if (message.isNotEmpty()) vm.message.value = message
            } finally {
                try {
                    usage = vm.storage.inspect()
                } finally {
                    busy = false
                }
            }
        }
    }
    LaunchedEffect(Unit) { run() }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("存储空间管理", back) {
            IconButton(enabled = !busy, onClick = { run() }) {
                Icon(Icons.Outlined.Refresh, "刷新占用")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "本地数据占用 ${usage?.let { size(it.total) } ?: "读取中"}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "不含安装包。票据与回收站已包含在数据库占用中。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            usage?.let { value ->
                StorageCard(
                    "数据库",
                    size(value.database),
                    "票据内容约 ${size(value.receiptContent)} · 回收站内容约 ${size(value.trashContent)}",
                    "整理",
                    !busy,
                ) {
                    confirmation = "database"
                }
                StorageCard("缓存", size(value.cache), "清理后可重新生成", "清理", !busy) {
                    run("缓存已清理") { vm.storage.clearCache() }
                }
                StorageCard("运行日志", size(value.logs), "仅本机运行记录", "清空", !busy) {
                    confirmation = "logs"
                }
                StorageCard("回收站", size(value.trashContent), "此数值已包含在数据库中", "清空", !busy) {
                    confirmation = "trash"
                }
                StorageCard("恢复前副本", size(value.recovery), "同步恢复前保留的加密副本", "删除", !busy) {
                    confirmation = "recovery"
                }
                StorageCard("资料与系统数据", size(value.other), "头像、标签模板、设置、代码缓存及本机连接配置")
            }
        }
    }
    if (confirmation.isNotEmpty())
        GlassAlertDialog(
            onDismissRequest = { confirmation = "" },
            title = {
                Text(
                    when (confirmation) {
                        "logs" -> "清空运行日志？"
                        "trash" -> "永久清空回收站？"
                        "recovery" -> "删除恢复前副本？"
                        else -> "整理数据库？"
                    }
                )
            },
            text = {
                Text(
                    when (confirmation) {
                        "logs" -> "清空后无法恢复日志，账单和设置会保留。"
                        "trash" -> "回收站中的账单和票据将永久删除，无法撤销。正常账单会保留。"
                        "recovery" -> "删除后无法再使用此副本回到同步恢复前的数据。如需保留，请先到同步页面导出副本。"
                        else -> "合并空闲空间，保留所有账单和资料。数据较多时可能需要一些时间。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = confirmation
                        confirmation = ""
                        run("操作完成") {
                            when (action) {
                                "logs" -> AppLog.clear(context)
                                "trash" -> {
                                    vm.repository.dao.clearTrash()
                                    vm.clearUndo()
                                }
                                "recovery" -> WebDavStore(context).clearRecovery()
                                "database" -> vm.storage.compactDatabase()
                            }
                        }
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = "" }) { Text("取消") } },
        )
}

@Composable
private fun StorageCard(
    title: String,
    size: String,
    note: String,
    action: String? = null,
    enabled: Boolean = true,
    click: () -> Unit = {},
) {
    Card(
        Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(
                    size,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (action != null) TextButton(enabled = enabled, onClick = click) { Text(action) }
            }
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
