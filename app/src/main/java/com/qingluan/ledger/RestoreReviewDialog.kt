package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/** All full backup sources use this preview; no mutation takes place until confirmation. */
@Composable
internal fun RestoreReviewDialog(
    vm: LedgerViewModel,
    raw: String,
    dismiss: () -> Unit,
    beforeRestore: suspend () -> Unit = {},
    restored: () -> Unit,
) {
    var comparison by remember(raw) { mutableStateOf<RestoreComparison?>(null) }
    var error by remember(raw) { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var choices by remember(raw) { mutableStateOf(emptySet<String>()) }
    var mode by remember { mutableStateOf("merge") }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(raw) {
        try {
            comparison = vm.compareRestore(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "无法比较备份"
        }
    }
    val data = comparison
    GlassAlertDialog(
        onDismissRequest = { if (!working) dismiss() },
        title = { Text("恢复前比较") },
        text = {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = dialogContentMaxHeight()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    ConfigChoice(
                        "恢复方式",
                        mode,
                        linkedMapOf("merge" to "合并并处理冲突", "overwrite" to "完整覆盖"),
                    ) {
                        mode = it
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (data == null && error == null)
                    item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (data != null) {
                    item {
                        Text("本机 ${data.local.entries.size} 笔 · 备份 ${data.incoming.entries.size} 笔")
                    }
                    if (mode == "merge") {
                        item {
                            Text(
                                "新增账单 ${data.preview.addedBills} 笔 · 新增回收站 ${data.preview.addedTrash} 笔"
                            )
                            if (data.preview.skippedConfigs > 0)
                                Text(
                                    "备份有 ${data.preview.skippedConfigs} 项预算或标签对应的分类已不存在，合并时将跳过这些无效配置。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            Text(
                                "合并分类、预算、标签、周期及分期计划；外观、个人资料和已保存搜索保留本机设置。备份中缺少的记录不会删除本机记录。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "冲突 ${data.preview.conflicts.size} 项 · 默认保留本机",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (data.preview.conflicts.isNotEmpty())
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    TextButton(onClick = { choices = emptySet() }) {
                                        Text("全部保留本机")
                                    }
                                    TextButton(
                                        onClick = {
                                            choices =
                                                data.preview.conflicts.mapTo(hashSetOf()) { it.key }
                                        }
                                    ) {
                                        Text("全部使用备份")
                                    }
                                }
                            }
                        items(data.preview.conflicts, key = { it.key }) { conflict ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(
                                        conflict.title,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    listOf(false to conflict.local, true to conflict.backup)
                                        .forEach { (backup, description) ->
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .selectable(
                                                        (conflict.key in choices) == backup,
                                                        role = Role.RadioButton,
                                                    ) {
                                                        choices =
                                                            if (backup) choices + conflict.key
                                                            else choices - conflict.key
                                                    }
                                            ) {
                                                RadioButton(
                                                    (conflict.key in choices) == backup,
                                                    null,
                                                )
                                                Column(
                                                    Modifier.weight(1f).padding(vertical = 8.dp)
                                                ) {
                                                    Text(
                                                        if (backup) "备份版本" else "本机版本",
                                                        style = MaterialTheme.typography.labelMedium,
                                                    )
                                                    Text(
                                                        description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                    )
                                                }
                                            }
                                        }
                                }
                            }
                        }
                    } else
                        item {
                            Text(
                                "本机账单、分类、回收站、账单计划、预算、标签和备份包含的个人设置将被替换。应用锁及连接配置保留。",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = data != null && !working && error == null,
                onClick = { confirm = true },
            ) {
                Text(if (working) "恢复中" else "继续")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text("取消") } },
    )
    if (confirm && data != null)
        GlassAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(if (mode == "merge") "确认合并恢复？" else "确认覆盖本机数据？") },
            text = {
                Text(
                    if (mode == "merge") "${choices.size} 项使用备份版本，其余冲突保留本机；未选择前不会修改数据。"
                    else "完整覆盖会替换当前数据。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        working = true
                        val selected = choices.toSet()
                        val overwrite = mode == "overwrite"
                        vm.action(label = "恢复备份") {
                            try {
                                beforeRestore()
                                if (overwrite) vm.restoreData(raw)
                                else vm.mergeRestore(data, selected)
                                vm.currentExportIds.value = emptySet()
                                restored()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "恢复失败，本机数据已保留"
                                throw e
                            } finally {
                                working = false
                            }
                        }
                    }
                ) {
                    Text(if (mode == "merge") "合并恢复" else "覆盖恢复")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
}
