# 青鸾记账

当前版本：**1.0.0**（初始版本）。Android 内部升级编号为 108，保留对已安装开发版的覆盖升级能力。

原生 Android 个人记账应用。Kotlin / Compose / Material 3 / Room，API 26+，默认人民币，支持 178 种 ISO 4217 币种，纯本地，无账号和网络权限。

## 使用

底部三个入口：明细、洞察、设置。右下角按钮打开记账。设置含本地昵称头像、天数/笔数/分币种结余，以及分类管理、个性化和本地数据二级页，外观模式在个性化中通过弹窗切换。点击明细账单直接编辑，左滑达到阈值松手后弹出删除确认；日历可快捷记账。

分类被引用时需迁移到同收支类型的另一分类；每种类型保留至少一个分类。金额按各币种最小单位以 Long 存储；人民币两位、日元零位、KWD 三位、CLF 四位小数。记账页选择币种，账单和统计自动按币种分别展示，不换算或合计不同币种。周一开周，所有日期按设备当前本地时区解释。

## 构建

安装 JDK 17+、Android SDK 35 与 Build Tools 35.0.0；在 local.properties 中设置 sdk.dir，或设置 ANDROID_HOME。

```sh
./gradlew test
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest # 需要已连接的设备或模拟器
```

APK：app/build/outputs/apk/debug/app-debug.apk。依赖版本均锁定。

## 数据与备份

数据库保存在 Android 应用私有目录，关闭系统自动备份。设置中通过系统文件选择器导出/恢复 JSON。完整备份 formatVersion=5，包含完整分类、交易及逐笔 currencyCode、头像、昵称、主题、字体和个性化设置；金额为整数最小货币单位、时间为 epoch milliseconds。兼容 v1/v2 账单备份和 v3/v4 完整备份，导入旧备份时保留当前个人资料和设置。导出和导入均可选择 CSV 表格；CSV 追加账单、补充缺失分类，并按重复记录数量跳过已有账单，不覆盖个人资料和设置。JSON 支持追加、范围替换与完整恢复；导入前预览，可筛选日期、收支及币种。导出支持全部、当前明细筛选、指定日期、多选账单及 CSV 可选列，文件名和系统保存位置可选。JSON/CSV 为明文，也可导出密码加密的 .qlb（AES-256-GCM）。恢复先校验，再事务替换；不合规文件保留现有数据库。清空账单保留分类。

数据库版本 3，导出 Room schema；1→2 添加币种字段并保留旧账单为 CNY，2→3 增加标签与票据，未使用破坏性升级。

## 界面

浅色/深色/跟随系统，Android 12+ 动态色。底栏使用 Haze 背景模糊，旧版本使用半透明降级；图表 Compose Canvas 绘制，系统组件提供按压和弹窗反馈。个性化二级页可分别关闭灵动效果、触觉反馈，并设置字号与主题；材质始终启用。底栏后可滚动经过账单，展示半透明背景模糊与边缘高光；不是光学折射。弹窗背景模糊取决于系统支持。页面导航始终即时切换，Compose 动效遵循系统动画时长设置。

## 验证与限制

具体执行结果见 docs/verification.md。尚未完成设备验证的功能不视为已验证。没有自动上传、账户、预算或云同步功能。系统文件保存由用户选择目标；备份提醒在打开应用时触发，不申请通知权限。

单次备份恢复最多 16MiB、100000 笔交易和 5000 个分类；超过限制会拒绝导入并保留当前数据。

## 链接

- GitHub：https://github.com/wangshiyulin/qingluan-ledger
- 博客：https://qingluanx.com
- 更新发布：https://github.com/wangshiyulin/qingluan-ledger/releases
- 问题反馈：https://github.com/wangshiyulin/qingluan-ledger/issues

更新说明见 [CHANGELOG.md](CHANGELOG.md)，继续开发见 [开发指南](docs/development.md)。官方网站地址尚未提供，应用内保留待配置。更新入口打开 Releases 页面，不自动联网比较版本。
