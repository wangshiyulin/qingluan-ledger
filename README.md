<div align="center">
  <img src="app/src/main/res/drawable-nodpi/brand_logo.png" width="96" height="96" alt="青鸾记账图标" />
  <h1>青鸾记账</h1>
  <p>一款数据保存在本机的原生 Android 记账应用。</p>
  <p>Android 8.0+ · Kotlin · Jetpack Compose · Material 3</p>
  <p>
    <a href="https://github.com/wangshiyulin/qingluan-ledger/releases">下载版本</a> ·
    <a href="https://github.com/wangshiyulin/qingluan-ledger/issues">问题反馈</a> ·
    <a href="https://ledger.qingluanx.com">官方网站</a> ·
    <a href="https://qingluanx.com">博客</a>
  </p>
</div>

## 简介

青鸾记账支持日常收支记录、分类统计、多币种和本地备份。无需注册账号，无广告；账单、票据和个人资料默认保存在应用的本地私有目录。联网权限用于用户主动发起的 WebDAV 备份同步。

当前版本：**v1.0.5**。版本说明见 [更新日志](CHANGELOG.md)。

## 功能

### 记账与管理

- 记录收入、支出、日期、时间、备注及票据图片。
- 自定义收支分类与图标，长按拖动调整分类顺序；支持保留分类的独立账单迁移。
- 点击账单直接编辑，左滑确认删除，并可撤销删除操作。
- 长按账单直接在明细页勾选，顶部悬浮操作栏提供编辑、导出、删除与全选，不推动列表或改变卡片尺寸，批量修改分类、备注和标签，或删除、导出所选账单。
- 按月份、日期范围、收支类型、分类及关键词筛选账单。
- 支持滚动渐进显示。

### 统计与多币种

- 提供月度、年度和全部记录的收入、支出、结余统计。
- 展示收支趋势、分类排行、圆环占比、日历及年度报告。
- 支持 178 种内置 ISO 4217 币种及对应金额精度，优先显示实际常用币种。
- “全部”视图的平均值按总金额除以有账单的不同日期数计算。
- 不同币种分别统计，不提供汇率换算，也不直接合计不同币种。
- 可设置默认统计周期、类型、图表，并记住统计筛选。

### 外观与操作

- 默认跟随系统外观，支持浅色、深色及纯黑深色模式；深色图标容器保留清晰的主题底色。
- 提供多种主题配色；Android 12 及以上支持系统动态配色。
- 可调整字号、列表密度，以及时间、备注和金额的显示方式；自定义字号提供75%–160%滑块、精确输入与预览。
- 支持悬浮记账按钮、轻量页面转场和透明模糊底栏；启动交接采用约180ms淡出与轻微缩放，关闭灵动效果或系统动画后即时切换。
- 可配置局部动画、触觉反馈的强度及触发场景；关闭灵动效果时页面即时切换。
- 明细条目的时间显示在备注之前。
- 支持本地昵称、头像及圆形裁剪。

### 导入、导出与保护

- **JSON**：完整备份与恢复，也支持追加导入或替换指定范围的账单。
- **CSV**：导入、导出本应用的账单表格，可选择备注、标签和票据列。
- **加密备份（`.qlb`）**：使用密码保护 JSON 备份，采用 AES-256-GCM。
- 导入前预览记录数量、日期和币种，选择范围与重复记录处理方式。
- 导出支持全部、当前明细筛选、指定日期或多选账单，可设置文件名并选择保存位置。
- 提供系统身份验证应用锁、后台预览隐藏和打开应用时的备份提醒。

## 下载与使用

前往 [GitHub Releases](https://github.com/wangshiyulin/qingluan-ledger/releases) 查看可用的安装包。

- **明细**：查看、搜索和筛选账单；点击右下角按钮开始记账。
- **洞察**：查看收入、支出和结余趋势及分类统计。
- **我的**：管理分类、外观、列表显示和本地数据。

## 数据说明

- 应用不自动上传数据，不提供托管账号或云服务。可手动将加密备份同步到用户自己的 WebDAV 服务器；关于页面中的链接由系统浏览器打开。
- JSON 与 CSV 为明文文件；需要密码保护时请选择加密备份。备份密码不保存，忘记后无法恢复加密文件。
- 完整备份包含账单、分类、头像、昵称及外观和体验配置；应用锁等设备安全设置保留在本机。
- 子集导出只包含所选账单及分类，不等同于完整备份。
- 单次导入文件上限为 **16 MiB**，最多 **100,000 笔账单、5,000 个分类**。含大量票据时可缩小导出范围，或在 CSV 中排除票据列。
- 开启后台预览隐藏会同时禁止系统截图；身份验证、模糊效果和触觉表现取决于系统与设备支持。
- 运行日志仅保留最近 100 条操作结果，不记录账单内容，不自动上传。

## WebDAV 同步与系统兼容

“我的 → 同步”支持手动 WebDAV 加密备份：

1. 填写已存在的 **HTTPS 文件夹地址**、用户名与密码（可用服务商的应用密码），可测试连接，也可直接点击上传或下载，连接配置会自动加密保存。
2. 点击上传后，在弹窗输入独立的备份密码（8–128 位），上传完整备份至该文件夹中的 `ledger-backup.qlb`；已有文件时先确认覆盖。
3. 点击下载后输入对应的备份密码，校验并预览账单数量后确认恢复。恢复将覆盖本地账单、分类、票据和个人设置，不进行自动合并。

右上角“？”可查看同步说明。账号通过 Android Keystore 加密保存在本机，不进入导出或日志；备份密码不保存。恢复前保留本地数据的加密副本，可在同步页导出，再通过本地数据导入。上传使用 ETag 条件写入避免覆盖已变化的远端文件；服务器需支持 HEAD、GET、PUT 和条件请求。已有文件没有有效 ETag 时拒绝覆盖。仅支持 HTTPS，不跟随重定向，不接受无效证书。没有后台同步或定时上传。

图标同时提供带透明圆角的兼容资源与自适应资源；启动页使用独立圆角图标。选图、身份验证与窗口模糊提供系统能力回退。APK兼容容器的具体行为仍取决于系统实现，本项目未声称完成鸿蒙真机验证。

## 从源码构建

### 环境要求

- JDK 17
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- Android Studio，或可运行 Gradle Wrapper 的命令行环境

### 构建步骤

1. 克隆仓库：

   ```bash
   git clone https://github.com/wangshiyulin/qingluan-ledger.git
   cd qingluan-ledger
   ```

2. 使用 Android Studio 打开项目并完成 Gradle Sync。Android Studio 会生成本机的 `local.properties`；命令行构建也可通过 `ANDROID_HOME` 指定 SDK 路径。

3. 构建 Debug APK。

   **macOS / Linux：**

   ```bash
   chmod +x gradlew
   ./gradlew assembleDebug
   ```

   **Windows PowerShell：**

   ```powershell
   .\gradlew.bat assembleDebug
   ```

输出文件：`app/build/outputs/apk/debug/ledger-v1.0.5.apk`。

发行版启用 R8 代码优化与资源裁剪，可使用 `./gradlew assembleRelease` 构建并配置自己的发布签名。

首次构建需要联网下载构建工具及依赖；应用联网权限用于手动 WebDAV 同步。正式发布请使用自己的发布签名，并妥善保存签名文件；不同签名的安装包无法直接覆盖安装。

## 项目结构

```text
app/
├── src/main/          应用代码、资源与第三方许可
├── src/test/          单元测试与 JVM 界面测试
├── src/androidTest/   设备测试
└── schemas/           Room 数据库结构记录
gradle/wrapper/        Gradle Wrapper
gradlew                macOS / Linux 构建入口
gradlew.bat            Windows 构建入口
```

主要技术：Kotlin、Jetpack Compose、Material 3、Room、DataStore、Navigation Compose、Haze。

## 反馈与参与

欢迎通过 [Issues](https://github.com/wangshiyulin/qingluan-ledger/issues) 提交问题或建议。反馈时请说明应用版本、Android 版本、复现步骤及预期结果；提交截图或日志前，请移除个人信息。

官方网站：[ledger.qingluanx.com](https://ledger.qingluanx.com)（建设中）。

博客：[qingluanx.com](https://qingluanx.com)。

## 第三方说明

功能图标采用 AndroidX Compose Material Icons，遵循 Apache License 2.0。相关说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)，许可文本位于 [app/src/main/assets/licenses/](app/src/main/assets/licenses/)。应用品牌图案独立于第三方图标许可。
