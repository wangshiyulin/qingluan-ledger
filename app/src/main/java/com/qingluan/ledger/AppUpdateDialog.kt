package com.qingluan.ledger

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun AppUpdateDialog(model: AppUpdateViewModel, dismiss: () -> Unit) {
    val context = LocalContext.current
    val state by model.state.collectAsState()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (context.packageManager.canRequestPackageInstalls()) model.install()
            else model.permissionHint("未允许安装此来源，可稍后重新点击安装")
        }
    GlassAlertDialog(
        onDismissRequest = dismiss,
        title = { Text("检测更新") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(state.message)
                state.release?.let { release ->
                    Text(
                        "${release.tag} · ${String.format(java.util.Locale.ROOT,"%.1f",release.size/1048576.0)} MiB",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (state.phase == "downloading") {
                        LinearProgressIndicator(
                            progress = {
                                (state.downloaded.toFloat() / release.size).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${(state.downloaded*100/release.size).coerceIn(0,100)}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (
                        state.phase == "available" ||
                            state.phase == "ready" ||
                            state.phase == "error"
                    ) {
                        if (release.note.isNotBlank())
                            Text(release.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (state.phase in setOf("checking", "verifying", "installing"))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            when (state.phase) {
                "available" -> TextButton(onClick = model::download) { Text("下载并核验") }
                "ready" ->
                    TextButton(
                        onClick = {
                            if (context.packageManager.canRequestPackageInstalls()) model.install()
                            else {
                                model.permissionHint("请允许安装此来源，返回后将重新核验并打开系统安装页面")
                                try {
                                    permission.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                                } catch (_: Exception) {
                                    model.permissionHint("无法打开安装权限设置，请在系统设置中允许此应用安装软件")
                                }
                            }
                        }
                    ) {
                        Text("安装")
                    }
                "error" ->
                    TextButton(
                        onClick = { if (state.release == null) model.check() else model.download() }
                    ) {
                        Text("重试")
                    }
                "current" -> TextButton(onClick = dismiss) { Text("完成") }
                else -> Unit
            }
        },
        dismissButton = {
            if (state.phase != "current")
                TextButton(onClick = dismiss) { Text(if (state.busy) "取消" else "关闭") }
        },
    )
}
