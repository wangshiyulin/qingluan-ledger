package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun Settings(
    vm: LedgerViewModel,
    cats: List<Category>,
    page: String,
    back: () -> Unit,
    open: (String) -> Unit = {},
) {
    val theme by vm.theme.collectAsState()
    val effects by vm.effects.collectAsState()
    val style by vm.appearance.collectAsState()
    val mainCurrency by vm.mainCurrency.collectAsState()
    var currencyPicker by remember { mutableStateOf(false) }

    var font by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var edit by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var migrating by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Long?>(null) }
    if (page in aboutPages) AboutPage(vm, page, back, open)
    else if (page == "同步") SyncPage(vm, back)
    else if (page == "回收站") TrashPage(vm, back)
    else if (page == "存储空间管理") StoragePage(vm, back)
    else if (page == "标签管理") TagManagementPage(vm, cats, back)
    else if (page in settingsSectionPages) SettingsSectionPage(page, back, open)
    else if (page == "标签统计") TagInsightsPage(vm.entries.collectAsState().value, cats, back)
    else if (page == "分期账单") InstallmentPage(vm, cats, back)
    else if (page == "周期账单") RecurringPage(vm, cats, back)
    else if (page == "预算管理") BudgetPage(vm, cats, back)
    else if (page == "其他设置") OtherSettingsPage(vm, back)
    else if (page == "分类管理")
        CategoryManager(
            cats,
            back,
            { edit = it },
            {
                deleting = it
                target = null
            },
            onMigrate = { migrating = true },
        ) { type, ids ->
            vm.action { vm.repository.reorderCategories(type, ids) }
        }
    else
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PageBackButton(back)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding =
                    PaddingValues(start = 22.dp, end = 22.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (page == "个性化") {
                    item {
                        PreferenceCard("外观模式", Icons.Outlined.Palette, themeLabel(theme)) {
                            appearance = true
                        }
                    }
                    item {
                        PreferenceCard("字体", Icons.Outlined.TextFields, fontSizeLabel(style)) {
                            font = true
                        }
                    }
                    item {
                        PreferenceCard(
                            "主题",
                            Icons.Outlined.ColorLens,
                            paletteNames.getValue(style.palette),
                        ) {
                            palette = true
                        }
                    }
                    item {
                        PreferenceCard("主币种", Icons.Outlined.CurrencyExchange, mainCurrency) {
                            currencyPicker = true
                        }
                    }
                    item {
                        PreferenceSwitch("灵动效果", Icons.Outlined.AutoAwesome, effects.motion) {
                            vm.effect("motion", it)
                        }
                    }
                    item {
                        PreferenceSwitch("触觉反馈", Icons.Outlined.Vibration, effects.haptics) {
                            vm.effect("haptics", it)
                        }
                    }
                }
                if (page == "个性化") {
                    item { PreferenceCard("操作反馈", Icons.Outlined.TouchApp, "") { open("操作反馈") } }
                    item { PreferenceCard("统计偏好", Icons.Outlined.QueryStats, "") { open("统计偏好") } }
                }
                if (page in listOf("操作反馈", "列表显示", "统计偏好"))
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExperienceControls(vm, page)
                        }
                    }
                if (page == "数据保护")
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProtectionControls(vm)
                        }
                    }
                if (page == "数据管理")
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TransferTools(vm)
                            SettingsActionCard("同步", Icons.Outlined.CloudSync, { open("同步") })
                            SettingsActionCard(
                                "数据保护",
                                Icons.Outlined.VerifiedUser,
                                { open("数据保护") },
                            )
                            SettingsActionCard("存储空间管理", Icons.Outlined.Storage, { open("存储空间管理") })
                            SettingsActionCard("回收站", Icons.Outlined.DeleteOutline, { open("回收站") })
                            SettingsActionCard(
                                "清空账单",
                                Icons.Outlined.DeleteOutline,
                                { clear = true },
                                danger = true,
                            )
                        }
                    }
            }
        }
    if (currencyPicker)
        CurrencyPicker(mainCurrency, { currencyPicker = false }) {
            vm.mainCurrency(it)
            currencyPicker = false
        }
    if (migrating) CategoryMigrationDialog(vm, cats) { migrating = false }
    if (font) FontDialog(vm, style) { font = false }
    if (palette) PaletteDialog(vm, style) { palette = false }
    if (appearance)
        GlassAlertDialog(
            onDismissRequest = { appearance = false },
            title = { Text("外观模式") },
            text = {
                Column {
                    listOf("system", "light", "dark").forEach { value ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected = theme == value,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                    onClick = {
                                        vm.theme(value)
                                        appearance = false
                                    },
                                )
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(theme == value, onClick = null)
                            Text(themeLabel(value))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { appearance = false }) { Text("取消") } },
        )
    if (clear)
        GlassAlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("清空全部账单？") },
            text = { Text("全部账单移入回收站，保留分类与周期规则。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.action {
                            vm.repository.recycle(
                                vm.repository.dao.allEntries().map { it.id }.toSet()
                            )
                            vm.clearUndo()
                            vm.message.value = "全部账单已移入回收站"
                        }
                        clear = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } },
        )
    edit?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var key by remember(c) { mutableStateOf(c.iconKey) }
        GlassAlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("分类") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("名称") })
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.heightIn(max = 300.dp).testTag("category-icon-grid"),
                    ) {
                        items(Repository.icons, key = { it }) { k ->
                            IconToggleButton(checked = k == key, onCheckedChange = { key = k }) {
                                Icon(
                                    icon(k),
                                    CategoryIcons.labels.getValue(k),
                                    tint =
                                        if (k == key) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.action {
                            vm.repository.category(c.copy(name = name, iconKey = key))
                            edit = null
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("取消") } },
        )
    }
    deleting?.let { c ->
        GlassAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除 ${c.name}？") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = 320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(cats.filter { it.type == c.type && it.id != c.id }, key = { it.id }) {
                        other ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { target = other.id }
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(target == other.id, { target = other.id })
                            Text(
                                other.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.action {
                            vm.repository.remove(c, target)
                            deleting = null
                        }
                    }
                ) {
                    Text("迁移并删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

private fun themeLabel(value: String) =
    when (value) {
        "light" -> "浅色模式"
        "dark" -> "深色模式"
        else -> "跟随系统"
    }

@Composable
fun SettingsHome(vm: LedgerViewModel, rows: List<Entry>, open: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = LocalFloatingBottomInset.current + 72.dp)
    ) {
        ProfileHeader(vm, rows)
        listOf(
                "个性化" to Icons.Outlined.Palette,
                "分类与标签" to Icons.Outlined.Category,
                "账单管理" to Icons.Outlined.EventNote,
                "数据管理" to Icons.Outlined.FolderOpen,
                "其他设置" to Icons.Outlined.MoreHoriz,
                "关于" to Icons.Outlined.Info,
            )
            .forEach { (label, image) ->
                ListItem(
                    modifier =
                        Modifier.padding(vertical = 4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                            .elasticClickable { open(label) },
                    headlineContent = { Text(label) },
                    leadingContent = {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Icon(
                                image,
                                null,
                                Modifier.padding(9.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    },
                    trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                    colors =
                        ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                )
            }
    }
}

@Composable
private fun PreferenceCard(label: String, image: ImageVector, value: String, click: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().elasticClickable(click),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = { Text(label) },
            leadingContent = { Icon(image, null) },
            trailingContent = {
                Text(
                    value,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
}

@Composable
private fun PreferenceSwitch(
    label: String,
    image: ImageVector,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.toggleable(
                    checked,
                    role = androidx.compose.ui.semantics.Role.Switch,
                    onValueChange = change,
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(image, null)
            Spacer(Modifier.width(16.dp))
            Text(label, Modifier.weight(1f))
            Switch(checked, onCheckedChange = null)
        }
    }
}

@Composable
fun SettingsActionCard(
    label: String,
    image: ImageVector,
    click: () -> Unit,
    danger: Boolean = false,
) {
    Surface(
        Modifier.fillMaxWidth().elasticClickable(click),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = {
                Text(
                    label,
                    color =
                        if (danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                )
            },
            leadingContent = {
                Icon(
                    image,
                    null,
                    tint =
                        if (danger) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
}
