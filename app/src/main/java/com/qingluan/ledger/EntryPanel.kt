package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryPanel(
    original: Entry?,
    initial: LocalDate,
    cats: List<Category>,
    busy: Boolean,
    initialCurrency: String = "CNY",
    onCancel: () -> Unit,
    onManage: () -> Unit,
    save: (Entry) -> Unit,
) {
    val feedback = rememberAppFeedback("input")
    var currency by rememberSaveable { mutableStateOf(original?.currencyCode ?: initialCurrency) }
    var type by rememberSaveable { mutableStateOf(original?.type ?: "EXPENSE") }
    var category by rememberSaveable { mutableLongStateOf(original?.categoryId ?: 0L) }
    var amount by rememberSaveable {
        mutableStateOf(original?.let { Money.text(it.amount, it.currencyCode) } ?: "")
    }
    var receipt by remember { mutableStateOf(original?.receipt ?: "") }
    var note by rememberSaveable { mutableStateOf(original?.note ?: "") }
    var dateText by rememberSaveable { mutableStateOf((original?.date() ?: initial).toString()) }
    var timeText by rememberSaveable {
        mutableStateOf(
            (original?.let {
                    Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalTime()
                } ?: LocalTime.now())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        )
    }
    var manualDateTime by rememberSaveable {
        mutableStateOf(original != null || initial != LocalDate.now())
    }
    var dateTimeChanged by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(manualDateTime) {
        while (!manualDateTime) {
            val now = LocalDateTime.now()
            dateText = now.toLocalDate().toString()
            timeText = now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            kotlinx.coroutines.delay(1000)
        }
    }
    var picking by rememberSaveable { mutableStateOf(original == null) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val selected = cats.find { it.id == category && it.type == type }
    LaunchedEffect(cats, category) {
        if (category != 0L && selected == null) {
            category = 0L
            picking = true
        }
    }
    fun key(value: String) {
        feedback()
        if (value == "⌫") {
            amount = amount.dropLast(1)
            return
        }
        val next = if (value == "." && amount.isEmpty()) "0." else amount + value
        if (
            next.length <= 22 &&
                next.matches(
                    Regex(
                        if (Currencies.get(currency).digits == 0) "[0-9]+"
                        else "[0-9]+(\\.[0-9]{0,${Currencies.get(currency).digits}})?"
                    )
                )
        )
            amount = next
    }
    Column(
        Modifier.fillMaxWidth()
            .heightIn(
                max =
                    androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .94f
            )
            .padding(horizontal = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            listOf("EXPENSE" to "支出", "INCOME" to "收入").forEach { (value, label) ->
                TextButton(
                    onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        if (type != value) {
                            type = value
                            category = 0
                        }
                        picking = true
                    }
                ) {
                    Text(
                        label,
                        fontWeight = if (type == value) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (type == value) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("取消") }
        }
        if (picking) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(cats.filter { it.type == type }, key = { it.id }) { c ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                feedback()
                                category = c.id
                                picking = false
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color =
                                if (category == c.id) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Icon(icon(c.iconKey), null, Modifier.padding(16.dp).size(24.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
            TextButton(
                onClick = onManage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("分类管理")
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CurrencyButton(currency) {
                    if (amount.isNotEmpty() && Money.parse(amount, it) == null) amount = ""
                    currency = it
                }
                Text(
                    amount.ifEmpty { Money.text(0, currency) },
                    Modifier.weight(1f).padding(vertical = 8.dp).testTag("entry-amount"),
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                )
            }
            OutlinedTextField(
                note,
                { note = it },
                Modifier.fillMaxWidth().testTag("entry-note"),
                placeholder = { Text("备注") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            focus.clearFocus()
                            keyboard?.hide()
                        }
                    ),
            )
            ReceiptSelector(receipt) { receipt = it }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                            listOf("7", "8", "9"),
                            listOf("4", "5", "6"),
                            listOf("1", "2", "3"),
                            listOf(".", "0", "⌫"),
                        )
                        .forEach { line ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                line.forEach { value ->
                                    Box(
                                        Modifier.weight(1f)
                                            .height(48.dp)
                                            .then(
                                                if (value == "⌫")
                                                    Modifier.semantics {
                                                        contentDescription = "删除一位"
                                                        customActions =
                                                            listOf(
                                                                CustomAccessibilityAction("清空金额") {
                                                                    amount = ""
                                                                    true
                                                                }
                                                            )
                                                    }
                                                else Modifier
                                            )
                                            .combinedClickable(
                                                enabled =
                                                    value != "." ||
                                                        Currencies.get(currency).digits > 0,
                                                onClick = {
                                                    focus.clearFocus()
                                                    keyboard?.hide()
                                                    key(value)
                                                },
                                                onLongClick =
                                                    if (value == "⌫") ({ amount = "" }) else null,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (value == "⌫") Icon(Icons.Outlined.Backspace, null)
                                        else
                                            Text(
                                                value,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                    }
                                }
                            }
                        }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            focus.clearFocus()
                            keyboard?.hide()
                            dateOpen = true
                        },
                        Modifier.fillMaxWidth().height(48.dp).testTag("entry-date"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text(
                            LocalDate.parse(dateText)
                                .format(DateTimeFormatter.ofPattern("yyyy\nMM/dd")),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    FilledTonalButton(
                        onClick = {
                            focus.clearFocus()
                            keyboard?.hide()
                            timeOpen = true
                        },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(2.dp),
                    ) {
                        Text(timeText, style = MaterialTheme.typography.labelMedium)
                    }
                    FilledTonalButton(
                        enabled =
                            !busy && Money.parse(amount, currency) != null && selected != null,
                        onClick = {
                            save(
                                Entry(
                                    original?.id ?: 0L,
                                    type,
                                    Money.parse(amount, currency)!!,
                                    category,
                                    resolveEntryTime(
                                        original?.occurredAt,
                                        LocalDate.parse(dateText),
                                        LocalTime.parse(timeText),
                                        manualDateTime,
                                        dateTimeChanged,
                                    ),
                                    note,
                                    original?.createdAt ?: System.currentTimeMillis(),
                                    System.currentTimeMillis(),
                                    currencyCode = currency,
                                    tags = original?.tags ?: "",
                                    receipt = receipt,
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(104.dp),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        Text("完成")
                    }
                }
            }
        }
    }
    if (dateOpen)
        AppDateDialog(LocalDate.parse(dateText), { dateOpen = false }) {
            dateText = it.toString()
            manualDateTime = true
            dateTimeChanged = true
        }
    if (timeOpen)
        AppTimeDialog(LocalTime.parse(timeText), { timeOpen = false }) {
            timeText = it.toString()
            manualDateTime = true
            dateTimeChanged = true
        }
}
