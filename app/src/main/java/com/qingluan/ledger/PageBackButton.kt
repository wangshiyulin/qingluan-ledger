package com.qingluan.ledger

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Hosts use a 16dp horizontal inset and no extra top inset, matching category management. */
@Composable
fun PageBackButton(onClick: () -> Unit, description: String = "返回") {
    IconButton(onClick = onClick, modifier = Modifier.testTag("page-back")) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, description)
    }
}
