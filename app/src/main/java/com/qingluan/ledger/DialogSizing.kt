package com.qingluan.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** A shared width keeps the calendar aligned with the filter's three-column content. */
@Composable
fun billFilterDialogWidth() = minOf(LocalConfiguration.current.screenWidthDp.dp - 32.dp, 360.dp)

@Composable fun calendarDialogWidth() = billFilterDialogWidth() - 48.dp
