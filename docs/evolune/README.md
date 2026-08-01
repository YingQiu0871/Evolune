# Evolune（月序）

> 面向 Android 与 Wear OS 的激素用药记录、提醒和浓度趋势工具。

[![Build Debug APK](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml)
[![GitHub Downloads](https://img.shields.io/github/downloads/Yuning-Gu/Evolune/total?style=flat&logo=github&label=Downloads)](https://github.com/Yuning-Gu/Evolune/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](../../LICENSE)

Evolune 是一个以本地数据为中心的 Android/Wear OS 用药记录工具。它用于记录日常用药、管理周期方案、安排提醒，并通过药代动力学模型展示雌二醇浓度的历史与预测趋势。所有个人记录默认保存在本机。

> [!IMPORTANT]
> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。医疗相关决策请咨询具备资质的专业人士，并以实际检验结果为准。

## 主要功能

- **用药记录**：添加、编辑和删除用药事件。
- **多种给药途径**：支持肌肉注射、口服、舌下含服、透皮凝胶及贴片应用或移除。
- **用药方案**：创建每日、每周或自定义间隔方案，并配置多个用药时间。
- **提醒与签到**：根据启用的方案安排系统通知，并可从通知快速确认本次用药。
- **浓度趋势**：根据药物、剂量、途径、体重和历史记录计算当前浓度及未来趋势。
- **桌面小组件**：查看近期用药并快速添加或确认记录。
- **Wear OS 支持**：同步用药方案，并通过手表磁贴查看和处理近期剂量。
- **数据导入导出**：通过文件或剪贴板导入、导出 JSON，兼容 `hrt.mahiro.uk` 数据格式。
- **个性化设置**：支持深浅色主题、动态取色、12/24 小时制和自动检查更新。

当前代码仍处于单体应用演进阶段。已实现能力集中在 `app` 和 `wear` 两个 Android 模块；Wear 端当前是 Tile 与基础设备传输，不是完整 Wear App，协议也尚未版本化。Health Connect、云同步、加密备份和正式 Tracked Date 模型尚未在 Evolune 中实现。

## 系统要求

- 手机端：Android 12 及以上（`minSdk = 31`）
- 手表端：Wear OS / Android API 30 及以上（`minSdk = 30`）

## 获取应用

### GitHub Actions 构建

当前可以从 [Actions 页面](https://github.com/Yuning-Gu/Evolune/actions) 获取最新 Debug APK：

1. 打开最近一次成功的 `Build Debug APK` 工作流。
2. 在页面底部的 Artifacts 区域下载 APK 压缩包。
3. 解压并安装 `app-debug.apk`。

### 正式版本

正式发布版本会出现在 [Releases 页面](https://github.com/Yuning-Gu/Evolune/releases)。如果页面暂时没有版本，请使用 Actions 构建产物。

> Debug 与 Release 使用不同的应用 ID 和签名，可以同时安装，但数据不会自动互通。

## 快速上手

1. 在“设置”中填写体重，用于浓度模型计算。
2. 在“记录”中添加已有的用药记录。
3. 在“方案”中创建未来计划，并按需启用提醒。
4. 返回“主页”查看当前浓度、历史曲线和未来预测。
5. 如需备份，在“设置”中导出 JSON 数据。

## 本地构建

构建环境：

- JDK 17
- Android SDK 36
- Git（版本名通过 `git describe` 生成）

Windows：

```powershell
.\gradlew.bat test assembleDebug
```

macOS / Linux：

```bash
./gradlew test assembleDebug
```

手机端 Debug APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

手表端 Debug APK 通常位于：

```text
wear/build/outputs/apk/debug/wear-debug.apk
```

## 项目结构

```text
app/   Android 手机端、桌面小组件、提醒、数据存储与药代动力学模型
wear/  Wear OS 磁贴、方案缓存与手机数据同步
docs/  产品、架构、功能矩阵、迁移计划、路线图和决策记录
feiwuliyong/  Featherline 迁移资料包，仅作为受许可证约束的参考资料
reviews/  外部审阅报告和逐项处置记录
```

项目使用 Kotlin、Jetpack Compose、Room、DataStore、Kotlin Serialization、Android Widgets、Wearable Data Layer 和 Wear Tiles。

## 数据与隐私

- 用药记录、方案和设置保存在设备本地。
- 应用本身尚未实现主动云同步，但当前 Android Auto Backup 范围仍未确定；这是进入下一开发阶段前必须解决的隐私阻断项。
- 网络权限用于检查 GitHub 上的新版本。
- 导出的数据文件由用户自行保存和管理。
- 当前数据库使用 Room 默认存储，尚未启用 SQLCipher 或其他数据库加密方案。
- Android Auto Backup 和设备迁移规则当前排除手机与 Wear 的应用私有数据；用户应通过设置中的 JSON 导出/导入完成主动迁移。
- Health Connect、Google Drive 和其他云端同步当前不存在；未来接入必须采用主动授权和加密备份策略。

## 常见问题

### 为什么曲线与实际化验结果不同？

模型使用通用参数进行估算，个体吸收、代谢、给药误差和检测时间都会造成偏差。请以实际检验结果和专业医疗建议为准。

### 为什么没有收到提醒？

请确认方案已经启用，并检查通知权限、精确闹钟权限及设备的后台省电限制。

### 如何迁移或备份数据？

在设置页导出 JSON 文件或复制到剪贴板；在新设备上使用对应的导入功能恢复。

## 致谢

- 上游项目：[NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker)
- 灵感来源：[SmirnovaOyama/Oyama-s-HRT-Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker)
- PK 参考实现：[LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test)

Evolune 参考了上游 HRTTracker 的公开产品需求和代码演进背景。当前工作区中的 `upstream/master` 实际为单一 `app` 模块，包名为 `cn.naivetomcat.hrt_tracker`；Evolune 使用独立包名 `io.github.yuninggu.evolune`，不应把迁移资料中的 GPLv3 Featherline 源码直接复制到本项目。

`feiwuliyong/` 是来自 Featherline 的迁移资料包，其中的源码快照和补丁按包内来源说明受 GPLv3 约束。该资料包不是 Evolune 的构建模块，也不是可直接应用的补丁集合。

当前致谢只表示候选来源或产品参考关系，不证明具体代码血缘。逐文件结论以 [来源追踪模板](../SOURCE_PROVENANCE.md) 的人工核验为准。

## 许可证

本项目采用 [MIT License](../../LICENSE)。项目通知见 [NOTICE](../../NOTICE)。

## 文档索引

- [GitHub 根说明](../../readme.md)
- [产品概览](PRODUCT_OVERVIEW.md)
- [架构设计](ARCHITECTURE.md)
- [功能矩阵](FEATURE_MATRIX.md)
- [迁移计划](MIGRATION_PLAN.md)
- [路线图](ROADMAP.md)
- [架构决策记录](DECISIONS.md)
- [来源追踪模板](../SOURCE_PROVENANCE.md)
- [DeepSeek 审阅处置记录](../../reviews/REVIEW_DISPOSITION_V1.md)
