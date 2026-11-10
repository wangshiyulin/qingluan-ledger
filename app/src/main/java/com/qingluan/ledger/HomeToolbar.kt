package com.qingluan.ledger

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun HomeToolbar(
    query: String,
    change: (String) -> Unit,
    search: Boolean,
    openSearch: () -> Unit,
    closeSearch: () -> Unit,
    searchBounds: (Rect) -> Unit,
    filter: () -> Unit,
) {
    val effects = LocalEffects.current
    val feedback = rememberAppFeedback()
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            search,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(if (effects.motion) 100 else 0)) +
                    scaleIn(
                        tween(if (effects.motion) 120 else 0),
                        initialScale = .88f,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f),
                    )) togetherWith
                    (fadeOut(tween(if (effects.motion) 60 else 0)) +
                        scaleOut(tween(if (effects.motion) 60 else 0), targetScale = .96f))
            },
            label = "搜索岛",
        ) { expanded ->
            if (expanded) {
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    query,
                    change,
                    Modifier.fillMaxWidth()
                        .height(52.dp)
                        .focusRequester(focus)
                        .testTag("search-island")
                        .onGloballyPositioned { searchBounds(it.boundsInWindow()) },
                    singleLine = true,
                    placeholder = { Text("搜索分类、备注", style = MaterialTheme.typography.bodyMedium) },
                    shape = RoundedCornerShape(26.dp),
                    trailingIcon = {
                        IconButton(onClick = { closeSearch() }) {
                            Icon(Icons.Outlined.Close, "关闭搜索")
                        }
                    },
                )
                LaunchedEffect(Unit) {
                    focus.requestFocus()
                    keyboard?.show()
                }
            } else
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.brand_logo),
                        "青鸾记账图标",
                        Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("青鸾记账", style = MaterialTheme.typography.titleMedium)
                }
        }
        if (!search) {
            IconButton(
                onClick = {
                    feedback()
                    openSearch()
                }
            ) {
                Icon(Icons.Outlined.Search, "搜索")
            }
        }
        IconButton(
            onClick = {
                feedback()
                closeSearch()
                filter()
            }
        ) {
            Icon(Icons.Outlined.Tune, "筛选")
        }
    }
}
