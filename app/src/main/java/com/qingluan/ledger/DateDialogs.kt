package com.qingluan.ledger

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateDialog(date: LocalDate, dismiss: () -> Unit, select: (LocalDate) -> Unit) {
    val feedback = rememberAppFeedback()
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis =
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1900..2100,
        )
    val width = calendarDialogWidth()
    androidx.compose.ui.window.Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(
                    density.density * (width.value / 360f),
                    density.fontScale,
                )
        ) {
            ApplyDialogGlass()
            Surface(
                modifier = Modifier.width(360.dp).testTag("app-date-dialog"),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                androidx.compose.foundation.layout.Column {
                    DatePicker(
                        state,
                        Modifier.testTag("app-date-picker"),
                        title = null,
                        headline = null,
                        showModeToggle = false,
                        colors =
                            DatePickerDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                    )
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    ) {
                        TextButton(onClick = dismiss) { Text("取消") }
                        TextButton(
                            enabled = state.selectedDateMillis != null,
                            onClick = {
                                feedback()
                                state.selectedDateMillis?.let {
                                    select(
                                        Instant.ofEpochMilli(it)
                                            .atZone(ZoneOffset.UTC)
                                            .toLocalDate()
                                    )
                                }
                                dismiss()
                            },
                        ) {
                            Text("确定")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimeDialog(time: LocalTime, dismiss: () -> Unit, select: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(time.hour, time.minute, true)
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("时间") },
        text = { TimeInput(state, Modifier.testTag("app-time-picker")) },
        confirmButton = {
            TextButton(
                onClick = {
                    select(LocalTime.of(state.hour, state.minute))
                    dismiss()
                }
            ) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
