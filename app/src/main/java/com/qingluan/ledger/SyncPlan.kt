package com.qingluan.ledger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp

/** A visible proposal only: no server connection or credential collection in this version. */
@Composable
fun SyncPlanPage(back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(back)
            Text("同步", style = MaterialTheme.typography.titleMedium)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SyncCard("WebDAV 备份同步", "方案预览 · 尚未启用联网同步")
            SyncCard("连接配置", "使用你自己的 WebDAV 服务，配置 HTTPS 地址、用户名与密码。账号信息仅保存在本机，不写入备份或日志。")
            SyncCard("手动上传", "生成加密备份，再手动上传到 ledger/ledger-backup.qlb。使用独立备份密码，服务端账号密码与备份密码分开。")
            SyncCard("下载恢复", "先下载并校验备份，输入备份密码后预览。恢复前备份本地数据，确认覆盖后再执行恢复。")
            SyncCard("冲突处理", "不做后台实时合并。两端都有修改时，先比较远端文件时间或 ETag，由你选择上传本地或恢复远端；不静默覆盖。")
            Text(
                "本版本仅展示方案，不连接服务器，不上传或下载账单。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncCard(title: String, body: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
