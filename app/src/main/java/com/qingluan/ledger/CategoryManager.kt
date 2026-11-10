package com.qingluan.ledger

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CategoryManager(
    cats: List<Category>,
    back: () -> Unit,
    edit: (Category) -> Unit,
    delete: (Category) -> Unit,
    reorder: (String, List<Long>) -> Unit,
) {
    val effects = LocalEffects.current
    val feedback = rememberAppFeedback()
    var type by rememberSaveable { mutableStateOf("EXPENSE") }
    var order by remember(type) { mutableStateOf(cats.filter { it.type == type }) }
    var selected by remember { mutableStateOf<Category?>(null) }
    var dragging by remember { mutableStateOf<Long?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var original by remember { mutableStateOf(emptyList<Category>()) }
    val grid = rememberLazyGridState()
    var grab by remember { mutableStateOf(Offset.Zero) }
    var draggedSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val edge = with(density) { 48.dp.toPx() }
    LaunchedEffect(cats, type) { if (dragging == null) order = cats.filter { it.type == type } }
    fun moveUnderFinger() {
        val id = dragging ?: return
        val target =
            grid.layoutInfo.visibleItemsInfo
                .minByOrNull {
                    val x = pointer.x - (it.offset.x + it.size.width / 2f)
                    val y =
                        pointer.y -
                            (it.offset.y +
                                grid.layoutInfo.beforeContentPadding +
                                it.size.height / 2f)
                    x * x + y * y
                }
                ?.index ?: return
        val from = order.indexOfFirst { it.id == id }
        if (from >= 0 && target in order.indices && from != target) {
            order = order.toMutableList().apply { add(target, removeAt(from)) }
        }
    }
    LaunchedEffect(dragging) {
        while (isActive && dragging != null) {
            val end = grid.layoutInfo.viewportEndOffset + grid.layoutInfo.beforeContentPadding
            val delta =
                when {
                    pointer.y < edge -> -12f * density.density
                    pointer.y > end - edge -> 12f * density.density
                    else -> 0f
                }
            if (delta != 0f && grid.scrollBy(delta) != 0f) moveUnderFinger()
            delay(16)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "返回") }
            listOf("EXPENSE" to "支出", "INCOME" to "收入").forEach { (v, n) ->
                FilterChip(
                    type == v,
                    { type = v },
                    label = { Text(n) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(type) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val padding = grid.layoutInfo.beforeContentPadding
                    val item =
                        grid.layoutInfo.visibleItemsInfo.firstOrNull {
                            held.position.x >= it.offset.x &&
                                held.position.x < it.offset.x + it.size.width &&
                                held.position.y >= it.offset.y + padding &&
                                held.position.y < it.offset.y + padding + it.size.height
                        } ?: return@awaitEachGesture
                    dragging = item.key as Long
                    pointer = held.position
                    grab =
                        pointer -
                            Offset(item.offset.x.toFloat(), (item.offset.y + padding).toFloat())
                    draggedSize = item.size
                    original = order
                    feedback()
                    try {
                        while (true) {
                            // Claim motion before LazyGrid's scroll handler, after the long press.
                            val change =
                                awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull {
                                    it.id == down.id
                                } ?: break
                            change.consume()
                            if (!change.pressed) {
                                reorder(type, order.map { it.id })
                                dragging = null
                                feedback()
                                break
                            }
                            pointer = change.position
                            moveUnderFinger()
                        }
                    } finally {
                        if (dragging != null) {
                            order = original
                            dragging = null
                        }
                    }
                }
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                state = grid,
                userScrollEnabled = dragging == null,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(order, key = { it.id }) { c ->
                    CategoryTile(
                        c,
                        Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec =
                                    if (effects.motion)
                                        androidx.compose.animation.core.spring(stiffness = 900f)
                                    else null,
                            )
                            .graphicsLayer { alpha = if (dragging == c.id) .18f else 1f }
                            .clickable { if (dragging == null) selected = c }
                            .semantics {
                                contentDescription = c.name
                                customActions =
                                    listOf(-1 to "前移", 1 to "后移").map { (step, label) ->
                                        CustomAccessibilityAction(label) {
                                            val from = order.indexOfFirst { it.id == c.id }
                                            val to = from + step
                                            if (from < 0 || to !in order.indices) false
                                            else {
                                                order =
                                                    order.toMutableList().apply {
                                                        add(to, removeAt(from))
                                                    }
                                                reorder(type, order.map { it.id })
                                                true
                                            }
                                        }
                                    }
                            },
                    )
                }
            }
            order
                .find { it.id == dragging }
                ?.let { c ->
                    CategoryTile(
                        c,
                        Modifier.offset {
                                IntOffset(
                                    (pointer.x - grab.x).toInt(),
                                    (pointer.y - grab.y).toInt(),
                                )
                            }
                            .width(with(density) { draggedSize.width.toDp() })
                            .testTag("dragging-category")
                            .clearAndSetSemantics {},
                        true,
                    )
                }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf("EXPENSE" to "新增支出分类", "INCOME" to "新增收入分类").forEach { (v, n) ->
                FilledTonalButton(
                    onClick = {
                        edit(Category(name = "", type = v, sortOrder = cats.count { it.type == v }))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    Text(n, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    selected?.let { c ->
        GlassAlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(c.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            selected = null
                            edit(c)
                        }
                    ) {
                        Icon(Icons.Outlined.Edit, null)
                        Text("编辑")
                    }
                    TextButton(
                        onClick = {
                            selected = null
                            delete(c)
                        }
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                        Text("删除")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = if (lifted) Modifier.shadow(8.dp, RoundedCornerShape(20.dp)) else Modifier,
            shape = RoundedCornerShape(20.dp),
            color =
                if (lifted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Icon(icon(category.iconKey), null, Modifier.padding(14.dp).size(24.dp))
        }
        Text(
            category.name,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}
