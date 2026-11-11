package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryTagSelector(
    categoryId: Long,
    tags: String,
    change: (String) -> Unit,
    manage: (() -> Unit)?,
) {
    val catalog = LocalTagCatalog.current
    val available =
        remember(catalog, categoryId) {
            catalog.filter { it.categoryId == categoryId }.map { it.name }
        }
    val choices = remember(available, tags) { (available + TagCatalog.names(tags)).distinct() }
    var error by remember { mutableStateOf("") }
    var allTags by remember { mutableStateOf(false) }
    val selected = remember(tags) { TagCatalog.names(tags).toSet() }
    val inline = remember(choices, selected) { (selected.toList() + choices).distinct().take(8) }
    val feedback = rememberAppFeedback()
    fun toggle(name: String) {
        feedback()
        runCatching { TagCatalog.toggle(tags, name) }
            .onSuccess {
                change(it)
                error = ""
            }
            .onFailure { error = it.message ?: "无法选择标签" }
    }
    if (choices.isNotEmpty() || manage != null)
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.isEmpty()) "标签" else "标签（${selected.size}）",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (manage != null) TextButton(onClick = manage) { Text("管理标签") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                inline.forEach { name ->
                    FilterChip(
                        name in selected,
                        onClick = { toggle(name) },
                        label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            if (choices.size > 8)
                TextButton(onClick = { allTags = true }) { Text("全部标签（${choices.size}）") }
            if (error.isNotEmpty())
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    if (allTags)
        GlassAlertDialog(
            onDismissRequest = { allTags = false },
            title = { Text("选择标签") },
            text = {
                Column {
                    LazyVerticalGrid(
                        GridCells.Adaptive(88.dp),
                        Modifier.heightIn(max = minOf(320.dp, dialogContentMaxHeight())),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(choices, key = { it }) { name ->
                            FilterChip(
                                name in selected,
                                { toggle(name) },
                                label = {
                                    Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { allTags = false }) { Text("完成") } },
        )
}

@Composable
fun TagManagementPage(vm: LedgerViewModel, cats: List<Category>, back: () -> Unit) {
    val tags by vm.managedTags.collectAsState()
    var selectedCategory by remember { mutableLongStateOf(0) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ManagedTag?>(null) }
    var adding by remember { mutableStateOf(false) }
    val index = remember(cats) { cats.associateBy { it.id } }
    val visible =
        remember(tags, selectedCategory) {
            tags.filter { selectedCategory == 0L || it.categoryId == selectedCategory }
        }
    Column(Modifier.fillMaxSize()) {
        FeatureHeader("标签管理", back) {
            TextButton(onClick = { adding = true }, enabled = cats.isNotEmpty()) { Text("新增") }
        }
        TextButton(onClick = { picker = true }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(if (selectedCategory == 0L) "所有分类" else index[selectedCategory]?.name ?: "已删除分类")
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        if (visible.isEmpty())
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无标签")
            }
        else
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { tag ->
                    Card(onClick = { editing = tag }, modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text(tag.name) },
                            supportingContent = {
                                Text(
                                    index[tag.categoryId]?.let {
                                        "${if (it.type == "EXPENSE") "支出" else "收入"} · ${it.name}"
                                    } ?: "原分类已删除，可重新归属"
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Label,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            trailingContent = { Icon(Icons.Outlined.ChevronRight, "编辑标签") },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                ),
                        )
                    }
                }
            }
    }
    if (picker)
        TagCategoryPicker(cats, selectedCategory, true, { picker = false }) {
            selectedCategory = it
            picker = false
        }
    if (adding || editing != null)
        TagEditor(vm, cats, editing, selectedCategory) {
            adding = false
            editing = null
        }
}

@Composable
private fun TagEditor(
    vm: LedgerViewModel,
    cats: List<Category>,
    original: ManagedTag?,
    preferred: Long,
    dismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(original?.name ?: "") }
    var categoryId by remember {
        mutableLongStateOf(
            original?.categoryId
                ?: cats.find { it.id == preferred }?.id
                ?: cats.firstOrNull()?.id
                ?: 0L
        )
    }
    var categoryOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    GlassAlertDialog(
        onDismissRequest = { if (!submitting) dismiss() },
        title = { Text(if (original == null) "新增标签" else "编辑标签") },
        text = {
            Column(
                Modifier.heightIn(max = dialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    name,
                    { name = it.take(24) },
                    Modifier.fillMaxWidth(),
                    label = { Text("标签名称") },
                    singleLine = true,
                )
                TextButton(onClick = { categoryOpen = true }) {
                    Text(
                        cats
                            .find { it.id == categoryId }
                            ?.let { "${if (it.type == "EXPENSE") "支出" else "收入"} · ${it.name}" }
                            ?: "选择所属分类"
                    )
                    Icon(Icons.Outlined.ArrowDropDown, null)
                }
                Text("变更模板不会改写历史账单中的标签。", style = MaterialTheme.typography.bodySmall)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val next =
                        original?.copy(categoryId = categoryId, name = name.trim())
                            ?: ManagedTag(categoryId = categoryId, name = name.trim())
                    try {
                        TagCatalog.validate(next)
                        require(cats.any { it.id == categoryId }) { "请选择有效分类" }
                        submitting = true
                        vm.action {
                            try {
                                vm.saveManagedTag(original, next)
                                dismiss()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "无法保存标签"
                                throw e
                            } finally {
                                submitting = false
                            }
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "标签无效"
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (original != null)
                    TextButton(enabled = !submitting, onClick = { deleting = true }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                TextButton(enabled = !submitting, onClick = dismiss) { Text("取消") }
            }
        },
    )
    if (categoryOpen)
        TagCategoryPicker(cats, categoryId, false, { categoryOpen = false }) {
            categoryId = it
            categoryOpen = false
        }
    if (deleting && original != null)
        GlassAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("删除标签模板？") },
            text = { Text("历史账单中的标签文字会保留。") },
            confirmButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        submitting = true
                        vm.action {
                            try {
                                vm.deleteManagedTag(original)
                                dismiss()
                            } finally {
                                submitting = false
                            }
                        }
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
        )
}

@Composable
private fun TagCategoryPicker(
    cats: List<Category>,
    selected: Long,
    allowAll: Boolean,
    dismiss: () -> Unit,
    choose: (Long) -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("所属分类") },
        text = {
            LazyVerticalGrid(
                GridCells.Fixed(3),
                Modifier.heightIn(max = minOf(360.dp, dialogContentMaxHeight())),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (allowAll)
                    item { FilterChip(selected == 0L, { choose(0L) }, label = { Text("所有分类") }) }
                items(cats, key = { it.id }) { category ->
                    FilterChip(
                        selected == category.id,
                        { choose(category.id) },
                        label = {
                            Column {
                                Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (category.type == "EXPENSE") "支出" else "收入",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
