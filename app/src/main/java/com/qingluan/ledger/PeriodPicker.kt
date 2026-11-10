package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun PeriodPicker(date: LocalDate, mode: String, dismiss: () -> Unit, choose: (LocalDate) -> Unit) {
    var year by remember { mutableIntStateOf(date.year) }
    var years by remember { mutableStateOf(mode == "year") }
    val feedback = rememberAppFeedback()
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!years)
                    IconButton(enabled = year > 1900, onClick = { year-- }) {
                        Icon(Icons.Outlined.ChevronLeft, "上一年")
                    }
                TextButton(onClick = { years = true }, modifier = Modifier.weight(1f)) {
                    Text(if (years) "年份" else "${year}年")
                }
                if (!years)
                    IconButton(enabled = year < 2100, onClick = { year++ }) {
                        Icon(Icons.Outlined.ChevronRight, "下一年")
                    }
            }
        },
        text = {
            if (years)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state =
                        rememberLazyGridState(
                            initialFirstVisibleItemIndex =
                                ((year.coerceIn(1900, 2100) - 1900) / 3 * 3).coerceAtLeast(0)
                        ),
                    modifier = Modifier.heightIn(max = minOf(320.dp, dialogContentMaxHeight())).testTag("year-grid"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items((1900..2100).toList()) { y ->
                        FilterChip(
                            year == y,
                            {
                                feedback()
                                year = y
                                if (mode == "year") choose(date.withYear(y).withDayOfYear(1))
                                else years = false
                            },
                            label = { Text(y.toString(), maxLines = 1) },
                        )
                    }
                }
            else
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.heightIn(max = minOf(280.dp, dialogContentMaxHeight())).testTag("month-grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items((1..12).toList()) { m ->
                        FilterChip(
                            year == date.year && m == date.monthValue,
                            {
                                feedback()
                                choose(LocalDate.of(year, m, 1))
                            },
                            label = { Text("${m}月", maxLines = 1) },
                        )
                    }
                }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
