package com.qingluan.ledger

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.withTransaction

suspend fun editBills(
    repository: Repository,
    ids: Set<Long>,
    category: Long?,
    note: String?,
    tags: String?,
) =
    repository.db.withTransaction {
        val entries = repository.dao.allEntries().filter { it.id in ids }
        require(entries.size == ids.size) { "账单已变化，请重新选择" }
        val target =
            category?.let { id ->
                repository.dao.allCategories().find { it.id == id } ?: error("分类不存在")
            }
        require(target == null || entries.all { it.type == target.type }) { "批量分类只支持相同收支类型" }
        entries.forEach { e ->
            val next =
                e.copy(
                    categoryId = target?.id ?: e.categoryId,
                    note = note ?: e.note,
                    tags = tags ?: e.tags,
                    updatedAt = System.currentTimeMillis(),
                )
            validateBillExtras(next.tags, next.receipt)
            repository.dao.update(next)
        }
    }

@Composable
fun BulkBills(vm: LedgerViewModel, scopeIds: Set<Long>, initialId: Long, dismiss: () -> Unit) {
    val all by vm.entries.collectAsState()
    val cats by vm.categories.collectAsState()
    val rows = all.filter { it.id in scopeIds }
    var selected by rememberSaveable { mutableStateOf(setOf(initialId)) }
    var export by rememberSaveable { mutableStateOf(false) }
    var edit by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val valid = selected.intersect(rows.map { it.id }.toSet())
    val feedback = rememberAppFeedback()
    BackHandler(onBack = dismiss)
    Dialog(
        onDismissRequest = dismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ApplyDialogGlass()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.ArrowBack, "退出多选") }
                    Text("已选 ${valid.size} 笔", Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            selected =
                                if (valid.size == rows.size) emptySet()
                                else rows.map { it.id }.toSet()
                        }
                    ) {
                        Text(if (valid.size == rows.size) "取消全选" else "全选")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = valid.isNotEmpty(),
                        onClick = { edit = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("编辑")
                    }
                    TextButton(
                        enabled = valid.isNotEmpty(),
                        onClick = { export = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("导出所选")
                    }
                    TextButton(
                        enabled = valid.isNotEmpty(),
                        onClick = { deleting = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("删除")
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(rows, key = { it.id }) { e ->
                        EntryRow(
                            e,
                            cats,
                            onClick = {
                                selected = if (e.id in valid) selected - e.id else selected + e.id
                                feedback()
                            },
                            onLongClick = null,
                            trailing = {
                                Checkbox(
                                    e.id in valid,
                                    {
                                        selected =
                                            if (e.id in valid) selected - e.id else selected + e.id
                                        feedback()
                                    },
                                    modifier = Modifier.testTag("bill-checkbox-${e.id}"),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
    if (export) ExportDialog(vm, valid) { export = false }
    if (edit) {
        var category by remember { mutableStateOf("keep") }
        var note by remember { mutableStateOf("") }
        var tags by remember { mutableStateOf("") }
        var changeNote by remember { mutableStateOf(false) }
        var changeTags by remember { mutableStateOf(false) }
        val types = rows.filter { it.id in valid }.map { it.type }.distinct()
        GlassAlertDialog(
            onDismissRequest = { edit = false },
            title = { Text("批量编辑") },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConfigChoice(
                        "分类",
                        category,
                        linkedMapOf("keep" to "不修改").apply {
                            if (types.size == 1)
                                cats
                                    .filter { it.type == types.first() }
                                    .forEach { put(it.id.toString(), it.name) }
                        },
                    ) {
                        category = it
                    }
                    ConfigSwitch("修改备注", changeNote) { changeNote = it }
                    if (changeNote)
                        OutlinedTextField(note, { note = it }, label = { Text("备注（留空清除）") })
                    ConfigSwitch("修改标签", changeTags) { changeTags = it }
                    if (changeTags)
                        OutlinedTextField(
                            tags,
                            { tags = it.take(300) },
                            label = { Text("标签（留空清除）") },
                        )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ids = valid.toSet()
                        vm.action {
                            editBills(
                                vm.repository,
                                ids,
                                category.toLongOrNull(),
                                if (changeNote) note else null,
                                if (changeTags) tags else null,
                            )
                            edit = false
                            vm.message.value = "已修改 ${ids.size} 笔"
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { edit = false }) { Text("取消") } },
        )
    }
    if (deleting)
        GlassAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除所选 ${valid.size} 笔账单？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteEntries(rows.filter { it.id in valid })
                        deleting = false
                        dismiss()
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
}
