package com.qingluan.ledger

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fill these with the owner's HTTPS addresses when supplied. No guessed or third-party URLs. */
object AboutLinks {
    const val website = "https://ledger.qingluanx.com"
    const val github = "https://github.com/wangshiyulin/qingluan-ledger"
    const val blog = "https://qingluanx.com"
    const val updates = "$github/releases"
    const val feedback = "$github/issues"
}

val aboutPages = listOf("关于", "更新日志", "运行日志", "开源许可")

fun openAboutLink(context: android.content.Context, url: String): Boolean {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" || uri.host.isNullOrBlank()) return false
    return runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            true
        }
        .getOrDefault(false)
}

@Composable
fun AboutPage(vm: LedgerViewModel, page: String, back: () -> Unit, open: (String) -> Unit) {
    val context = LocalContext.current
    val version = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
    }
    var events by remember { mutableStateOf<List<LogEvent>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var clear by remember { mutableStateOf(false) }
    var updateOpen by rememberSaveable { mutableStateOf(false) }
    val updater: AppUpdateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    LaunchedEffect(updateOpen) {
        if (updateOpen && updater.state.value.phase == "idle") updater.check()
    }
    var license by remember { mutableStateOf("") }
    LaunchedEffect(page, refresh) {
        if (page == "运行日志") events = AppLog.read(context).reversed()
        if (page == "开源许可")
            license =
                withContext(Dispatchers.IO) {
                    context.assets.open("licenses/THIRD_PARTY_NOTICES.md").bufferedReader().use {
                        it.readText()
                    } +
                        "\n\n" +
                        context.assets.open("licenses/Apache-2.0.txt").bufferedReader().use {
                            it.readText()
                        }
                }
    }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) {
            uri ->
            if (uri != null) {
                val text = AppLog.text(events.reversed())
                vm.action {
                    withContext(Dispatchers.IO) {
                        (context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件"))
                            .bufferedWriter()
                            .use { it.write(text) }
                    }
                    vm.message.value = "日志已导出"
                }
            }
        }
    fun link(url: String, pending: String) {
        vm.message.value =
            if (url.isBlank()) pending else if (openAboutLink(context, url)) null else "无法打开链接"
    }
    if (updateOpen)
        AppUpdateDialog(updater) {
            updater.cancel()
            updateOpen = false
        }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageBackButton(back)
            Text(page, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            if (page == "运行日志")
                IconButton(onClick = { refresh++ }) { Icon(Icons.Outlined.Refresh, "刷新日志") }
        }
        Column(
            Modifier.weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                "关于" -> {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(
                            painterResource(R.drawable.brand_logo),
                            "青鸾记账图标",
                            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("青鸾记账", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "版本 $version",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(
                                Triple("官网", Icons.Outlined.Language, AboutLinks.website),
                                Triple("GitHub", Icons.Outlined.Code, AboutLinks.github),
                                Triple("博客", Icons.AutoMirrored.Outlined.Article, AboutLinks.blog),
                            )
                            .forEach { (label, image, url) ->
                                Surface(
                                    onClick = { link(url, "${label}地址待配置") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                ) {
                                    Column(
                                        Modifier.padding(vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            image,
                                            null,
                                            Modifier.size(25.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(label, style = MaterialTheme.typography.labelLarge)
                                        if (url.isBlank())
                                            Text(
                                                "待配置",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                    }
                                }
                            }
                    }
                    Spacer(Modifier.height(6.dp))
                    AboutRow("检测更新", Icons.Outlined.SystemUpdate, null) {
                        updateOpen = true
                        updater.check()
                    }
                    AboutRow(
                        "问题反馈",
                        Icons.Outlined.Feedback,
                        "反馈地址待配置".takeIf { AboutLinks.feedback.isBlank() },
                    ) {
                        link(AboutLinks.feedback, "反馈地址待配置")
                    }
                    AboutRow("更新日志", Icons.Outlined.History) { open("更新日志") }
                    AboutRow("运行日志", Icons.Outlined.BugReport) { open("运行日志") }
                    AboutRow("开源许可", Icons.Outlined.Description) { open("开源许可") }
                }
                "更新日志" -> {
                    Changelog(
                        "1.1.1",
                        "字体精简为跟随系统和自定义，新增字形选择与本地字体导入；年度报告独立页面与月度对比、分类排行；缩小日历行距；提亮深色主题，头像增加编辑标记。",
                    )
                    Changelog(
                        "1.1.0",
                        "新增 S3 兼容加密备份与恢复；回收站紧凑卡片；液态透明底栏；标签筛选统计、独立月预算、分期账单、多张票据和XLSX导入导出；整理功能分级。",
                    )
                    Changelog("1.0.9", "新增存储空间管理和分类标签管理，选完分类可多选标签；完整备份包含标签模板；优化汇总、日期计算、时钟刷新和小票据处理。")
                    Changelog(
                        "1.0.8",
                        "增强主题图标和文字色彩；新增月度总预算及分类预算、主币种与临时金额隐藏；列表显示移入个性化；紧凑搜索与底栏，更新我的图标。",
                    )
                    Changelog("1.0.7", "桌面快捷记账支持开关并保存状态；调整我的页面顺序；高级搜索按选项分组显示浅色卡片。")
                    Changelog(
                        "1.0.6",
                        "高级搜索、回收站、周期账单、WebDAV历史与导入预览；保留桌面快捷记账，移除组件；新增币种地区旗帜，调整周期空状态；缓存统计结果与配色，优化趋势标注和列表复用。",
                    )
                    Changelog("1.0.5", "简化同步与独立说明；透明启动标识及淡出过渡；默认跟随系统，改善主题浓度、图标与日历间距；优化账单保存和发行包体积。")
                    Changelog("1.0.4", "多选栏顶部悬浮、卡片尺寸保持不变；实现 HTTPS WebDAV 手动加密备份与恢复，新增冲突校验和恢复副本。")
                    Changelog("1.0.3", "自定义字号、独立分类迁移；时间先于备注；新增可关闭的水平转场与退出页面交互保护。")
                    Changelog("1.0.2", "明细原位多选、按记账天数计算平均值、编辑金额直接替换、常用币种前置；新增同步方案，优化兼容图标和系统能力回退。")
                    Changelog("1.0.1", "统一返回按钮位置；简化票据选择与设置入口；调整本地数据卡片；配置官方网站。")
                    Changelog("1.0.0", "初始版本。")
                }
                "运行日志" -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            enabled = events.isNotEmpty(),
                            onClick = {
                                export.launch("青鸾记账-运行日志-${java.time.LocalDate.now()}.txt")
                            },
                        ) {
                            Text("导出日志")
                        }
                        TextButton(enabled = events.isNotEmpty(), onClick = { clear = true }) {
                            Text("清空日志")
                        }
                    }
                    Text(
                        "仅保留最近100条操作结果，不包含账单内容。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (events.isEmpty()) Text("暂无日志")
                    events.forEach { event ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                AppLog.text(listOf(event)),
                                Modifier.fillMaxWidth().padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                "开源许可" -> Text(license, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (clear)
        GlassAlertDialog(
            onDismissRequest = { clear = false },
            title = { Text("清空运行日志？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.action(record = false) {
                            AppLog.clear(context)
                            events = emptyList()
                        }
                        clear = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = { TextButton(onClick = { clear = false }) { Text("取消") } },
        )
}

@Composable
private fun AboutRow(
    label: String,
    image: ImageVector,
    subtitle: String? = null,
    click: () -> Unit,
) {
    Surface(
        onClick = click,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ListItem(
            headlineContent = { Text(label) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { Icon(image, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            colors =
                ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent
                ),
        )
    }
}

@Composable
private fun Changelog(version: String, changes: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                version,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(changes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
