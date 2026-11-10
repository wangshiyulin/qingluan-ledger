package com.qingluan.ledger

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/** A shared width keeps the calendar aligned with the filter's three-column content. */
@Composable
fun billFilterDialogWidth() = minOf(LocalConfiguration.current.screenWidthDp.dp - 32.dp, 360.dp).coerceAtLeast(1.dp)

@Composable fun calendarDialogWidth() = (billFilterDialogWidth() - 48.dp).coerceAtLeast(1.dp)

/** Leave room for dialog insets and the keyboard in compact landscape windows. */
@Composable
fun dialogContentMaxHeight() =
    (LocalConfiguration.current.screenHeightDp.dp - 160.dp).coerceAtLeast(96.dp)
