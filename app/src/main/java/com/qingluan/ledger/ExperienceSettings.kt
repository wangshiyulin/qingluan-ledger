package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun ConfigChoice(
    label: String,
    value: String,
    choices: Map<String, String>,
    columns: Int = 1,
    update: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val feedback = rememberAppFeedback()
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            modifier =
                Modifier.clickable {
                    feedback()
                    open = true
                },
            headlineContent = { Text(label) },
            trailingContent = {
                Text(choices[value] ?: value, style = MaterialTheme.typography.labelMedium)
            },
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
    if (open)
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(
                    Modifier.heightIn(max = minOf(380.dp, dialogContentMaxHeight()))
                        .verticalScroll(rememberScrollState())
                ) {
                    choices.entries.toList().chunked(columns.coerceAtLeast(1)).forEach { group ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            group.forEach { (key, name) ->
                                val selected = key == value
                                Surface(
                                    modifier =
                                        Modifier.weight(1f).padding(vertical = 3.dp).selectable(
                                            selected,
                                            role = Role.RadioButton,
                                        ) {
                                            if (!selected) feedback()
                                            update(key)
                                            open = false
                                        },
                                    shape =
                                        androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                    color =
                                        if (selected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerLow,
                                    border =
                                        if (columns > 1)
                                            BorderStroke(
                                                1.dp,
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                            )
                                        else null,
                                ) {
                                    Row(
                                        Modifier.heightIn(min = 48.dp)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement =
                                            if (columns > 1) Arrangement.Center
                                            else Arrangement.Start,
                                    ) {
                                        if (columns == 1) RadioButton(selected, null)
                                        Text(name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("取消") } },
        )
}

@Composable
fun ConfigSwitch(label: String, value: Boolean, update: (Boolean) -> Unit) {
    val feedback = rememberAppFeedback()
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            modifier =
                Modifier.toggleable(
                    value,
                    role = Role.Switch,
                    onValueChange = {
                        feedback()
                        update(it)
                    },
                ),
            headlineContent = { Text(label) },
            trailingContent = { Switch(value, null) },
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
}

@Composable
fun ExperienceControls(vm: LedgerViewModel, page: String) {
    val e by vm.experience.collectAsState()
    val effects by vm.effects.collectAsState()
    fun set(key: String, value: Any) = vm.experience(key, value)
    when (page) {
        "操作反馈" -> {
            ConfigSwitch("触觉反馈", effects.haptics) { vm.effect("haptics", it) }
            ConfigChoice(
                "动画强度",
                e.motionStrength,
                linkedMapOf("subtle" to "轻微", "normal" to "标准", "lively" to "灵动"),
            ) {
                set("motionStrength", it)
            }
            ConfigChoice("触觉强度", e.hapticStrength, linkedMapOf("light" to "轻", "firm" to "强")) {
                set("hapticStrength", it)
            }
            ConfigSwitch("导航反馈", e.feedbackNavigation) { set("feedbackNavigation", it) }
            ConfigSwitch("输入反馈", e.feedbackInput) { set("feedbackInput", it) }
            ConfigSwitch("操作反馈", e.feedbackActions) { set("feedbackActions", it) }
            ConfigSwitch("删除后可撤销", e.undoDelete) { set("undoDelete", it) }
        }
        "列表显示" -> {
            ConfigChoice("列表密度", e.density, linkedMapOf("compact" to "紧凑", "comfortable" to "舒适")) {
                set("density", it)
            }
            ConfigSwitch("显示时间", e.showTime) { set("showTime", it) }
            ConfigSwitch("显示备注", e.showNote) { set("showNote", it) }
            ConfigSwitch("显示标签", e.showTags) { set("showTags", it) }
            ConfigSwitch("隐藏金额", e.hideAmounts) { set("hideAmounts", it) }
        }
        "统计偏好" -> {
            ConfigChoice(
                "默认周期",
                e.statUnit,
                linkedMapOf("month" to "月", "year" to "年", "all" to "全部"),
            ) {
                set("statUnit", it)
                set("statDate", "")
            }
            ConfigChoice("默认统计类型", e.statType, insightTypeNames) { set("statType", it) }
            ConfigChoice(
                "默认图表",
                if (e.statPie) "pie" else "list",
                linkedMapOf("list" to "列表", "pie" to "圆环"),
            ) {
                set("statPie", it == "pie")
            }
            ConfigSwitch("记住统计筛选", e.rememberStats) { set("rememberStats", it) }
        }
    }
}

val secondaryPages =
    listOf(
        "分类管理",
        "标签管理",
        "存储空间管理",
        "个性化",
        "数据管理",
        "操作反馈",
        "列表显示",
        "统计偏好",
        "数据保护",
        "同步",
        "回收站",
        "周期账单",
        "预算管理",
        "分期账单",
        "标签统计",
        "账单管理",
        "其他设置",
    ) + aboutPages
