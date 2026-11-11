package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import java.math.BigDecimal
import java.time.LocalDate
import org.json.*

data class BillSearch(
    val keyword: String = "",
    val type: String = "EXPENSE",
    val categoryId: Long = 0,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val currency: String = "all",
    val minimum: String = "",
    val maximum: String = "",
    val receipt: String = "any",
    val tagNames: List<String> = emptyList(),
    val tagMode: String = "any",
) {
    fun validate() {
        require(keyword.length <= 200 && type in listOf("INCOME", "EXPENSE") && categoryId >= 0) {
            "搜索条件无效"
        }
        require(from == null || to == null || !from.isAfter(to)) { "开始日期不能晚于结束日期" }
        require(currency == "all" || Currencies.contains(currency)) { "币种无效" }
        require(receipt in listOf("any", "yes", "no")) { "票据条件无效" }
        require(tagMode in listOf("any", "all")) { "标签匹配方式无效" }
        require(
            tagNames.size <= 30 &&
                tagNames.distinct().size == tagNames.size &&
                tagNames.all { name ->
                    name == name.trim() &&
                        name.length in 1..300 &&
                        name.none { it.isISOControl() || it in ",，、;；" }
                }
        ) {
            "标签筛选无效"
        }
        listOf(minimum, maximum).forEach {
            require(it.isEmpty() || it.matches(Regex("[0-9]{1,18}(\\.[0-9]{1,8})?"))) { "金额格式无效" }
        }
        require(
            minimum.isEmpty() || maximum.isEmpty() || BigDecimal(minimum) <= BigDecimal(maximum)
        ) {
            "最小金额不能大于最大金额"
        }
    }

    fun predicate(categories: Map<Long, Category> = emptyMap()): (Entry) -> Boolean {
        val min = minimum.takeIf { it.isNotEmpty() }?.let(::BigDecimal)
        val max = maximum.takeIf { it.isNotEmpty() }?.let(::BigDecimal)
        return { entry ->
            val priceMatches =
                if (min == null && max == null) true
                else {
                    val amount =
                        BigDecimal.valueOf(entry.amount, Currencies.get(entry.currencyCode).digits)
                    (min == null || amount >= min) && (max == null || amount <= max)
                }
            val entryTags = TagCatalog.names(entry.tags).toSet()
            val tagsMatch =
                tagNames.isEmpty() ||
                    if (tagMode == "all") tagNames.all { it in entryTags }
                    else tagNames.any { it in entryTags }
            priceMatches &&
                tagsMatch &&
                (from == null || !entry.date().isBefore(from)) &&
                (to == null || !entry.date().isAfter(to)) &&
                (categoryId == 0L || entry.categoryId == categoryId) &&
                (currency == "all" || entry.currencyCode == currency) &&
                (receipt == "any" || entry.receipt.isNotEmpty() == (receipt == "yes")) &&
                (keyword.isBlank() ||
                    entry.note.contains(keyword, true) ||
                    entry.tags.contains(keyword, true) ||
                    categories[entry.categoryId]?.name?.contains(keyword, true) == true)
        }
    }

    fun matches(entry: Entry, category: Category?): Boolean =
        predicate(category?.let { mapOf(it.id to it) } ?: emptyMap())(entry)

    fun json() =
        JSONObject()
            .put("keyword", keyword)
            .put("type", type)
            .put("categoryId", categoryId)
            .put("from", from?.toString() ?: "")
            .put("to", to?.toString() ?: "")
            .put("currency", currency)
            .put("minimum", minimum)
            .put("maximum", maximum)
            .put("receipt", receipt)
            .put("tagNames", JSONArray(tagNames))
            .put("tagMode", tagMode)

    companion object {
        fun decode(j: JSONObject) =
            BillSearch(
                    j.getString("keyword"),
                    j.getString("type"),
                    ArchiveJson.long(j, "categoryId"),
                    j.getString("from").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                    j.getString("to").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
                    j.getString("currency"),
                    j.getString("minimum"),
                    j.getString("maximum"),
                    j.getString("receipt"),
                    if (j.has("tagNames"))
                        j.getJSONArray("tagNames").let { tags ->
                            require(tags.length() <= 30) { "标签筛选过多" }
                            (0 until tags.length()).map { tags.getString(it) }
                        }
                    else emptyList(),
                    j.optString("tagMode", "any"),
                )
                .also { it.validate() }
    }
}

fun searchPresets(raw: String): Map<String, BillSearch> =
    runCatching {
            require(raw.length <= 32000)
            val j = JSONObject(raw)
            require(j.length() <= 10)
            j.keys().asSequence().associateWith { key ->
                require(key.length in 1..24)
                BillSearch.decode(j.getJSONObject(key))
            }
        }
        .getOrDefault(emptyMap())

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedSearchDialog(
    vm: LedgerViewModel,
    cats: List<Category>,
    initial: BillSearch,
    dismiss: () -> Unit,
    apply: (BillSearch) -> Unit,
) {
    var state by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf("") }
    var save by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var currencies by remember { mutableStateOf(false) }
    val catalog = LocalTagCatalog.current
    val entries by vm.entries.collectAsState()
    val tagChoices =
        remember(catalog, entries, cats, state.type, state.categoryId, state.tagNames) {
            val ids =
                cats
                    .filter {
                        it.type == state.type &&
                            (state.categoryId == 0L || it.id == state.categoryId)
                    }
                    .map { it.id }
                    .toSet()
            (state.tagNames +
                    catalog.filter { it.categoryId in ids }.map { it.name } +
                    entries
                        .filter {
                            it.type == state.type &&
                                (state.categoryId == 0L || it.categoryId == state.categoryId)
                        }
                        .flatMap { TagCatalog.names(it.tags) })
                .distinct()
        }
    val context = LocalContext.current
    val prefs by context.preferences.data.collectAsState(initial = emptyPreferences())
    val presets =
        remember(prefs) { searchPresets(prefs[stringPreferencesKey("searchPresets")] ?: "{}") }
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("高级搜索") },
        text = {
            Column(
                Modifier.heightIn(max = dialogContentMaxHeight())
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchSection {
                    OutlinedTextField(
                        state.keyword,
                        { state = state.copy(keyword = it.take(200)) },
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
                        label = { Text("分类、备注、标签关键词", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                }
                SearchSection {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("EXPENSE" to "支出", "INCOME" to "收入").forEach { (key, label) ->
                            FilterChip(
                                state.type == key,
                                {
                                    state =
                                        state.copy(
                                            type = key,
                                            categoryId = 0,
                                            tagNames = emptyList(),
                                        )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            )
                        }
                    }
                }
                SearchSection {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "限制日期范围",
                            Modifier.weight(1f).padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            state.from != null || state.to != null,
                            onCheckedChange = { limited ->
                                state =
                                    if (limited)
                                        state.copy(
                                            from = LocalDate.now().withDayOfMonth(1),
                                            to = LocalDate.now(),
                                        )
                                    else state.copy(from = null, to = null)
                            },
                        )
                    }
                    if (state.from != null || state.to != null)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DateButton(state.from ?: LocalDate.now().withDayOfMonth(1)) {
                                state = state.copy(from = it)
                            }
                            DateButton(state.to ?: LocalDate.now()) { state = state.copy(to = it) }
                        }
                }
                SearchSection {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            state.minimum,
                            { state = state.copy(minimum = it.take(27)) },
                            Modifier.weight(1f).defaultMinSize(minHeight = 52.dp),
                            label = { Text("最小金额", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        OutlinedTextField(
                            state.maximum,
                            { state = state.copy(maximum = it.take(27)) },
                            Modifier.weight(1f).defaultMinSize(minHeight = 52.dp),
                            label = { Text("最大金额", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { currencies = true }) {
                            Text(if (state.currency == "all") "所有币种" else state.currency)
                        }
                        if (state.currency != "all")
                            TextButton(onClick = { state = state.copy(currency = "all") }) {
                                Text("取消币种限制")
                            }
                    }
                    Text(
                        "金额按原币数值筛选，不进行换算。",
                        Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                SearchSection {
                    ConfigChoice(
                        "票据",
                        state.receipt,
                        linkedMapOf("any" to "不限", "yes" to "有票据", "no" to "无票据"),
                    ) {
                        state = state.copy(receipt = it)
                    }
                }
                SearchSection {
                    Text("分类", style = MaterialTheme.typography.labelMedium)
                    LazyVerticalGrid(
                        GridCells.Adaptive(84.dp),
                        Modifier.heightIn(max = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(cats.filter { it.type == state.type }, key = { it.id }) { c ->
                            FilterChip(
                                state.categoryId == c.id,
                                {
                                    state =
                                        state.copy(
                                            categoryId = if (state.categoryId == c.id) 0 else c.id,
                                            tagNames = emptyList(),
                                        )
                                },
                                label = {
                                    Text(
                                        c.name,
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge,
                                        overflow =
                                            androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
                SearchSection {
                    Text("标签", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("any" to "任一标签", "all" to "全部标签").forEach { (mode, label) ->
                            FilterChip(
                                state.tagMode == mode,
                                { state = state.copy(tagMode = mode) },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (tagChoices.isEmpty())
                        Text("当前分类暂无标签", style = MaterialTheme.typography.bodySmall)
                    else
                        LazyVerticalGrid(
                            GridCells.Adaptive(84.dp),
                            Modifier.heightIn(max = 160.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(tagChoices, key = { it }) { tag ->
                                val selected = tag in state.tagNames
                                FilterChip(
                                    selected,
                                    {
                                        if (selected || state.tagNames.size < 30) {
                                            state =
                                                state.copy(
                                                    tagNames =
                                                        if (selected) state.tagNames - tag
                                                        else state.tagNames + tag
                                                )
                                        } else error = "最多选择30个标签"
                                    },
                                    label = {
                                        Text(
                                            tag,
                                            maxLines = 2,
                                            overflow =
                                                androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    if (state.tagNames.isNotEmpty())
                        TextButton(onClick = { state = state.copy(tagNames = emptyList()) }) {
                            Text("清除标签筛选")
                        }
                }
                SearchSection {
                    TextButton(
                        onClick = {
                            try {
                                state.validate()
                                save = true
                            } catch (e: Exception) {
                                error = e.message ?: "条件无效"
                            }
                        }
                    ) {
                        Text("保存当前筛选")
                    }
                    presets.forEach { (label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    state =
                                        value.copy(
                                            categoryId =
                                                if (
                                                    cats.any {
                                                        it.id == value.categoryId &&
                                                            it.type == value.type
                                                    }
                                                )
                                                    value.categoryId
                                                else 0
                                        )
                                },
                                Modifier.weight(1f),
                            ) {
                                Text(label)
                            }
                            IconButton(
                                onClick = {
                                    vm.action {
                                        context.preferences.edit {
                                            it[stringPreferencesKey("searchPresets")] =
                                                JSONObject()
                                                    .apply {
                                                        presets
                                                            .filterKeys { it != label }
                                                            .forEach { (k, v) -> put(k, v.json()) }
                                                    }
                                                    .toString()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.DeleteOutline, "删除筛选$label")
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        state.validate()
                        apply(state)
                        dismiss()
                    } catch (e: Exception) {
                        error = e.message ?: "条件无效"
                    }
                }
            ) {
                Text("应用")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        state = BillSearch()
                        error = ""
                    }
                ) {
                    Text("重置")
                }
                TextButton(onClick = dismiss) { Text("取消") }
            }
        },
    )
    if (currencies)
        CurrencyPicker(state.currency, { currencies = false }) {
            state = state.copy(currency = it)
            currencies = false
        }
    if (save)
        GlassAlertDialog(
            onDismissRequest = { save = false },
            title = { Text("保存筛选") },
            text = {
                OutlinedTextField(
                    name,
                    { name = it.take(24) },
                    label = { Text("名称（同名会替换）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val label = name.trim()
                        val value = state
                        vm.action {
                            context.preferences.edit { p ->
                                val existing =
                                    searchPresets(p[stringPreferencesKey("searchPresets")] ?: "{}")
                                        .toMutableMap()
                                require(existing.size < 10 || label in existing) { "最多保存10个筛选" }
                                existing[label] = value
                                p[stringPreferencesKey("searchPresets")] =
                                    JSONObject()
                                        .apply { existing.forEach { (k, v) -> put(k, v.json()) } }
                                        .toString()
                            }
                            save = false
                            vm.message.value = "筛选已保存"
                        }
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { save = false }) { Text("取消") } },
        )
}

@Composable
private fun SearchSection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .18f)),
    ) {
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            Column(
                Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }
}
