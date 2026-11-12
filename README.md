<img src="app/src/main/res/drawable-nodpi/brand_logo.png" width="100" height="100" alt="青鸾记账图标" />

# 青鸾记账

记录日常收支，管理预算与账单。

**本地存储 · 无需注册 · 无广告**

[![最新版本](https://img.shields.io/github/v/release/wangshiyulin/qingluan-ledger?label=Release&color=379D98)](https://github.com/wangshiyulin/qingluan-ledger/releases/latest)
![Android 8.0 及以上](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)

[下载应用](https://github.com/wangshiyulin/qingluan-ledger/releases/latest) · [官网](https://ledger.qingluanx.com) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/wangshiyulin/qingluan-ledger/issues)

## 简介

青鸾记账是一款 Android 记账应用，支持收支记录、预算与账单计划、统计分析和备份恢复。账本默认保存在自己的设备上，无需注册应用账号。

你可以自定义分类和标签，为账单添加备注与票据，也可以调整主题、字体和列表样式。

## 界面预览

<table>
  <tr>
    <td align="center"><img src="https://ledger.qingluanx.com/assets/previews/ledger.jpg" width="240" alt="明细页面" /><br />明细</td>
    <td align="center"><img src="https://ledger.qingluanx.com/assets/previews/insights.jpg" width="240" alt="洞察页面" /><br />洞察</td>
    <td align="center"><img src="https://ledger.qingluanx.com/assets/previews/settings.jpg" width="240" alt="我的页面" /><br />我的</td>
  </tr>
</table>

## 功能特性

- **收支记录**：记录收入、支出、日期、备注和标签，每笔账单最多添加 8 张票据。
- **分类与标签**：自定义分类、图标与排序，设置标签所属分类，支持分类迁移。
- **搜索与批量管理**：按日期、金额、币种、分类、标签等条件筛选，保存常用搜索；支持多选、批量编辑、导出和删除。
- **预算与账单计划**：按月独立设置总预算和分类预算，管理周期账单与分期账单。
- **统计与年报**：查看收支趋势、分类排行、圆环占比、账单日历和独立年度报告，可生成图片分享并选择隐藏金额。
- **多币种**：支持 178 种币种及主币种设置，不同币种分别记录和统计。
- **导入与导出**：支持 JSON、CSV、Excel（XLSX）和加密备份，导入前可预览内容。
- **云端备份**：支持 WebDAV 和 S3 兼容对象存储，上传加密备份、查看历史并选择恢复；支持恢复前比较和逐项冲突处理。
- **数据保护**：应用锁、金额隐藏、后台预览隐藏、回收站与备份提醒。
- **外观与字体**：多种主题、浅色与深色模式、纯黑深色、自定义字号、字形及本地字体导入。
- **操作体验**：透明玻璃底栏、页面转场、触觉反馈和桌面图标快捷记账入口。
- **应用内更新**：检查新版本、查看更新说明、下载安装包并核验签名。

## 下载

支持 **Android 8.0 及以上**。

前往 [GitHub Releases](https://github.com/wangshiyulin/qingluan-ledger/releases/latest) 下载 `.apk` 安装包。后续也可以通过应用内的 **关于 → 检测更新** 获取新版本。

## 数据与备份

账单默认在本机保存，不会自动上传。使用云端备份时，需要配置自己的 WebDAV 或 S3 存储服务，并手动上传、恢复。

<details>
<summary>备份与使用说明</summary>

- 换设备、卸载或清除应用数据前，请导出完整 JSON 或加密备份，并保存到应用之外的位置。
- CSV 和 XLSX 适合用表格整理账单，不包含全部个人设置与账单计划。
- 完整覆盖会替换相应的本地数据；也可选择合并恢复，预览差异并决定冲突记录保留哪一份。加密备份需要使用原密码解密。
- 多币种不会自动换算；周期和分期账单到期后需要确认入账，不会自动扣款。
- 导入的字体文件仅保存在当前设备，换设备后需要重新导入。

</details>

## 反馈与参与

欢迎通过 [Issues](https://github.com/wangshiyulin/qingluan-ledger/issues) 提交问题和建议。反馈问题时，请附上应用版本、系统版本和复现步骤。

- 官网：[ledger.qingluanx.com](https://ledger.qingluanx.com)
- 作者博客：[qingluanx.com](https://qingluanx.com)
- 更新记录：[CHANGELOG.md](CHANGELOG.md)
- 开源组件说明：[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
