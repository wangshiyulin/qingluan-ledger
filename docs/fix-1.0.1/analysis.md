# 1.0.1 闪退修复与代码检查

用户设备：一加 Ace 6T，Android 16。包名不变，versionCode 2，versionName 1.0.1，数据库 schema 仍为 1，签名与 1.0 一致，可覆盖安装。

## 发现与修复

1. 启动绘制缺少 Haze 必填背景色。Haze 1.1.1 的 HazeChildNode.drawEffectWithGraphicsLayer 会执行 `require(bg.isSpecified)`，缺失时抛出 `IllegalArgumentException: backgroundColor not specified. Please provide a color.`。旧底栏使用默认未指定背景色的 style；现在显式传入当前主题 surface 和 20dp blurRadius。此为源码确认的异常路径；没有取得用户手机的崩溃堆栈，不能将其描述成已抓取的一加设备日志。
2. 底栏遮挡内容。触摸回归中账单行坐标为 y=398..470，而导航栏 y=390..470；点击被底栏截获。现在把完整 Scaffold 安全边距应用到导航内容，Haze 位于外层。
3. 分类更新接口允许改变分类类型，可能令历史交易与分类类型不一致。新增 Room 测试先复现失败，再加入已有分类类型不可更改的校验，同时验证合法分类类型和图标。
4. 分类迁移弹窗的分类列表不能滚动，分类较多时下方选项不可操作；加入限高滚动容器。
5. 修正 UI 测试中的滚动选择器；横向周期选择和纵向列表都具有滚动语义，测试现明确选择 LazyColumn 的索引滚动动作，并等待数据加载、处理保存提示。

## 当前验证结果

`./gradlew testDebugUnitTest assembleDebug lintDebug -PtestJavaExecutable=/usr/bin/java --max-workers=2`：BUILD SUCCESSFUL。

19 项测试全部通过：业务金额/日期 4，备份校验 7，Room 数据完整性 2，Android 9 / 14 / 16 各两项页面导航与账单新增编辑删除测试，共 6。

Android 16 测试由 Robolectric 4.17 + Java 21 执行。测试配置中启用其 FileDescriptor 拦截器所需的 jdk.internal.access 导出；这是 JVM 测试设置，不进入 APK。

Lint：0 Error、13 Warning、2 Information。警告主要是稳定依赖存在更新版本及 dataExtractionRules 配置建议，信息项为 Long 状态装箱建议；本次未整体升级生产依赖。

APK 签名验证通过，SHA256 签名证书与原版一致，无 INTERNET 权限。

## 验证边界

上述为 JVM Android 运行时与 Compose 交互回归，并非一加实机验收；硬件 GPU 毛玻璃效果、厂商系统行为和真实帧率尚未实测。Robolectric 截图接口在此环境超时，未将截图检查标记为通过。
