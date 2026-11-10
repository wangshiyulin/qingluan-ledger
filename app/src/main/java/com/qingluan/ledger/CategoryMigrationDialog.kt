package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CategoryMigrationDialog(vm: LedgerViewModel, categories: List<Category>, dismiss: () -> Unit) {
    val entries by vm.entries.collectAsState()
    var type by remember { mutableStateOf("EXPENSE") }
    var source by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val same = categories.filter { it.type == type }
    val sourceId = source.toLongOrNull()
    val targetId = target.toLongOrNull()
    val count = entries.count { it.categoryId == sourceId }
    val sourceCategory = same.find { it.id == sourceId }
    val targetCategory = same.find { it.id == targetId && it.id != sourceId }
    GlassAlertDialog(
        onDismissRequest = { if (!working) dismiss() },
        title = { Text("分类迁移") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ConfigChoice("收支类型", type, linkedMapOf("EXPENSE" to "支出", "INCOME" to "收入")) {
                    type = it
                    source = ""
                    target = ""
                }
                ConfigChoice(
                    "来源分类",
                    source,
                    linkedMapOf("" to "请选择").apply {
                        same.forEach { put(it.id.toString(), it.name) }
                    },
                ) {
                    source = it
                    target = ""
                }
                ConfigChoice(
                    "目标分类",
                    target,
                    linkedMapOf("" to "请选择").apply {
                        same.filter { it.id != sourceId }.forEach { put(it.id.toString(), it.name) }
                    },
                ) {
                    target = it
                }
                Text("共 $count 笔账单")
                Text("只迁移账单，保留来源和目标分类。", style = MaterialTheme.typography.labelMedium)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working && count > 0 && sourceCategory != null && targetCategory != null,
                onClick = { confirm = true },
            ) {
                Text("迁移")
            }
        },
        dismissButton = { TextButton(enabled = !working, onClick = dismiss) { Text("取消") } },
    )
    if (confirm)
        GlassAlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("迁移 $count 笔账单？") },
            text = { Text("${sourceCategory?.name} → ${targetCategory?.name}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val from = sourceId!!
                        val to = targetId!!
                        confirm = false
                        working = true
                        vm.action {
                            try {
                                val moved = vm.repository.migrateCategory(from, to)
                                vm.message.value = "已迁移 $moved 笔账单"
                                dismiss()
                            } finally {
                                working = false
                            }
                        }
                    }
                ) {
                    Text("确认迁移")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } },
        )
}
