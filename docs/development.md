# 继续开发青鸾记账

## 1. 打开项目

1. 安装官方 Android Studio： https://developer.android.com/studio 。
2. 解压源码 ZIP，选择 Open，打开包含 `settings.gradle.kts` 的 解压后的项目 文件夹。
3. 安装 Android SDK Platform 35、Build Tools 35.0.0。在 Settings → Build Tools → Gradle 中将 Gradle JDK 设为 JDK 17（构建也可用兼容的更高版本）。
4. 等待 Gradle Sync。首次同步需要联网下载 Gradle 和依赖；应用本身没有网络权限。
5. 手机开启开发者选项、USB 调试，连接电脑并允许调试，然后选中设备按 Run。

源码包不包含本机 `local.properties`；Android Studio 会生成它。也可自行填写 `sdk.dir=你的SDK路径`。

## 2. 构建 APK

macOS / Linux：

```sh
chmod +x gradlew
./gradlew assembleDebug
```

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 3. 修改代码的位置

| 功能 | 文件（均在 app/src/main/java/com/qingluan/ledger/） |
| --- | --- |
| 主导航、明细、金额概览 | MainActivity.kt、HomeToolbar.kt、SearchDismiss.kt、SwipeEntryRow.kt、LiquidNavigation.kt |
| 记账表单、币种 | EntryPanel.kt、CurrencyPicker.kt、Currencies.kt |
| 洞察与图表 | Insights.kt |
| 分类、设置与个性化 | CategoryManager.kt、CategoryIcons.kt、Settings.kt、UiPreferences.kt、Appearance.kt、PersonalizationDialogs.kt |
| 昵称、头像、三项统计 | Profile.kt、LedgerViewModel.kt、Domain.kt |
| 日期、时间弹窗 | DateDialogs.kt、PeriodPicker.kt |
| 数据库、备份、业务校验 | Data.kt、Domain.kt、CompleteBackup.kt、CsvExport.kt、CsvImport.kt |

应用图标资源位于 `app/src/main/res/drawable-nodpi/brand_logo.png`，桌面图标还有 `mipmap-anydpi/ic_launcher.xml` 与 `drawable/brand_foreground.xml`，启动页主题在 `values/styles.xml`、`values-v31/styles.xml`。

## 4. 每次修改的建议顺序

- 先明确一个小改动，再改相关页面；遵守 AGENTS.md 中已确定的界面约定。
- 数据结构变化必须添加 Room Migration，并保留旧 schema；不要使用 destructive migration。当前数据库为 v3。
- 备份格式变化需要兼容旧版。当前完整导出 v5，兼容 v1/v2 账单数据与 v3/v4 完整备份。
- 金额始终按币种最小单位存 Long，不用浮点数存储；不同币种不能直接合计。
- 构建后在自己的手机上走一遍受影响的流程，再发布新版本；更新 app/build.gradle.kts 中 versionCode 和 versionName。

## 5. 测试按改动选用

金额和统计：

```sh
./gradlew testDebugUnitTest --tests '*LedgerTest'
```

完整单元与 JVM 界面回归：

```sh
./gradlew testDebugUnitTest
```

Android 16 的 Robolectric 测试需要 Java 21。若用 JDK 17 构建，可额外传 `-PtestJavaExecutable=/path/to/java21`；Windows 路径带空格时使用引号。SDK 36 的 Robolectric Android 模型由测试依赖下载，不要求将应用 targetSdk 升到 36。

实体设备测试：

```sh
./gradlew connectedDebugAndroidTest
```

相册、外部相机、系统返回、深浅色、模糊和触觉需要真机检查。模拟环境不会证明这些硬件或厂商行为正常。

## 6. 签名与已有账单

本次交付的 APK 与上一版使用同一 Debug 签名，可覆盖安装。源码包不包含签名私钥；你在新电脑上构建的 Debug 签名通常不同，不能直接覆盖现在的安装包。先在应用内备份账单，再卸载旧安装、安装自己构建的版本并恢复备份。

正式公开发布前，请生成自己的发布 keystore，妥善保存并在之后所有版本中继续使用同一签名。不要将 keystore、密码或签名配置提交到 GitHub。签名丢失会影响覆盖升级。

## 7. 发布到 GitHub

在源码目录初始化 Git，提交源码、Gradle wrapper、Room schema 与文档，创建仓库后推送。提交前确认未包含 `local.properties`、`.gradle/`、`build/`、签名私钥或任何个人数据；这些项目已加入 .gitignore。首次公开时自行选择开源许可证。

APK 可作为 GitHub Releases 的附件，获得较稳定的下载地址；本次 tmpfiles 临时直链有有效期，不能作为长期发布渠道。

## 当前资料与备份范围

昵称和个性化偏好在 DataStore，头像为应用私有目录中的压缩图片；没有账号和上传服务。天数为实际有记账的不同本地日期数，笔数为所有账单数量，结余按币种分别计算。完整 JSON v5 备份包含账单、分类、头像、昵称与全部现有外观/个性化偏好。旧 v1/v2 备份不包含个人资料与偏好，恢复时保留现有设置。CSV 支持追加导入，复用或补充分类、按相同账单的重复次数跳过已有记录；导入前完整校验，事务提交。新 CSV 日期使用文本避免表格自动转为日期数字，但 CSV 不能设置列宽。

## 界面约定

点击账单直接编辑，左滑超过阈值并松手后直接弹出删除确认；外观模式在个性化中用弹窗选择。分类图标键集中在 CategoryIcons.kt，并用于数据库及备份校验，扩充时保留旧键。Material 功能图标为 Apache-2.0 开源资源，发布时保留 THIRD_PARTY_NOTICES.md 和 APK 中的许可证；品牌图案独立于该许可。

## 初始发布版本编号

当前对外 versionName 为 1.0.0，内部 versionCode 为整数 108，对应这一版本并支持覆盖此前安装。Android 内部版本只接受整数；下一次更新的内部编号必须大于 108，显示版本按正式发布序列递增。不要把 Room 数据库版本重置为 1。应用升级编号、显示版本号、数据库版本和备份格式版本分别维护。

后续需求可参考 docs/recommendations.md；必要检测记录见 docs/verification.md。

## 数据维护

完整备份与 CSV 入口集中在 LocalDataManager，写操作由 ViewModel 的同一互斥锁串行执行。修改恢复流程时保留私有日志、失败补偿和启动恢复；不要将恢复日志作为垃圾文件清除。新增设置时同步维护导出字段、解码校验和恢复赋值。Appearance.kt 管理配色和字号，PersonalizationDialogs.kt 提供设置弹窗。列表逐步显示由 UI 控制，Room 不做分页查询。

## 界面与导入维护

字体只保留字号；删除的字形、材质开关偏好在启动和恢复时清理，旧 v3 备份的对应字段忽略。个性化使用独立圆角卡片。LocalFloatingBottomInset 将悬浮底栏安全留白交给各主页面列表，让内容能从底栏后经过；二级页面仍使用正常 Scaffold 留白。Haze 底栏模糊始终启用；系统弹窗背景模糊由 Android/厂商能力决定，不模拟光学折射。

## 洞察与主题

洞察的 BALANCE 仅为显示模式，不是数据库交易类型。结余趋势通过 trendBuckets 的 balance 参数计算，保留各周期正负净额；不得将不同币种相加。结余分类区分收入/支出，分别计算占比，禁止负数饼图。配色键集中在 Appearance.kt，完整备份校验复用 paletteNames，新旧主题键保持稳定；浅色系在浅色模式使用柔和背景，深色模式仍有对应暗色显示。

## 功能位置与维护

- Experience.kt / ExperienceSettings.kt：反馈、列表和统计配置；体验偏好以 JSON 存在 DataStore 并纳入完整备份。
- AvatarCropDialog.kt / Profile.kt：有界解码、EXIF、圆形预览、拖动缩放与 AtomicFile 保存；预览与写入使用同一裁剪窗口。
- Transfer.kt / TransferDialogs.kt：日期闭区间/币种/收支/捕获的当前筛选 ID、导入预览、重复策略与范围替换。全部范围的 JSON 可含资料；子集 JSON 仅含账单及分类，不将局部导出冒充完整备份。
- BackupCipher.kt：随机16字节盐、12字节IV、PBKDF2-HMAC-SHA256 210000次、AES-256-GCM和认证附加数据；密码不持久化。密码不可找回。
- Protection.kt：系统生物识别（Android11+）或锁屏凭据、可配离开超时、FLAG_SECURE、打开应用时备份提醒。保护开关和最后备份时间仅保留本机，避免恢复备份时意外改变锁状态。
- BulkBills.kt / BillExtras.kt：多选批量事务、同类型分类校验、标签、压缩票据、常用备注。票据以有界JPEG的Base64存在Room并随备份，修改模型必须同步 CSV/JSON校验与迁移。

配置路径：设置→常用备注/列表显示；设置→个性化→操作反馈/统计偏好；设置→本地数据→数据保护。记住统计开启时会记录上次周期、类型、日期与图表。长按明细进入多选，选择范围以当时筛选结果为准。当前明细筛选导出指最近明细页筛选（不把不同币种相加）。CSV六个基础列固定保证可再导入，备注/标签/票据列可选。

系统认证、SAF文件保存、厂商触觉和后台预览需在目标手机确认；本次没有声称已做真机验证。隐藏后台预览同时禁用截图。备份文件上限16MiB，含大量票据时可缩小导出范围或CSV不含票据列。

## 关于与运行日志

About.kt 的 AboutLinks 集中维护 website/github/blog/updates/feedback。GitHub为 https://github.com/wangshiyulin/qingluan-ledger ，博客为 https://qingluanx.com ，更新和反馈分别指向仓库的 /releases 和 /issues。官方网站仍留空显示待配置。通过系统浏览器打开用户提供的HTTPS地址，不自动联网，也不声称已是最新版，更新入口暂为更新发布页面入口，尚无联网比较版本或下载安装逻辑。关于下的更新日志为本地真实版本说明。

AppLog.kt 仅保留最近100条匿名操作结果，AtomicFile 写入私有文件；ViewModel 记录启动/保存/本地操作及异常类名，严禁记录异常消息、堆栈、账单、昵称、路径、密码或URL。用户可自行导出文本或清空，不上传，也不进入完整备份。关于页的开源许可只展示已随应用附带的图标许可和第三方说明，不将品牌图案冒充Apache许可。

多选复用 EntryRow 的尾部可组合槽，勾选框置于右侧；全屏 Dialog 使用系统栏留白和与明细相同的16dp水平、8dp顶部边距。个性化保留外观/字体/主题及效果总开关，并包含操作反馈和统计偏好；常用备注与列表显示入口在设置首页。
