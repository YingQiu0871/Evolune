# Evolune（月序）

> 面向 Android 与 Wear OS 的激素用药记录、提醒和浓度趋势工具。

[![Build Debug APK](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml)
[![GitHub Downloads](https://img.shields.io/github/downloads/Yuning-Gu/Evolune/total?style=flat&logo=github&label=Downloads)](https://github.com/Yuning-Gu/Evolune/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Evolune 用于记录日常用药、管理周期方案和提醒，并通过药代动力学模型展示雌二醇浓度的历史与预测趋势。所有个人记录默认保存在本机。

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
wear/  Wear OS 磁贴、方案存储与手机数据同步
```

项目使用 Kotlin、Jetpack Compose、Room、DataStore、Kotlin Serialization、Android Widgets、Wearable Data Layer 和 Wear Tiles。

## 数据与隐私

- 用药记录、方案和设置保存在设备本地。
- 应用不会自动将个人记录上传到云端。
- 网络权限用于检查 GitHub 上的新版本。
- 导出的数据文件由用户自行保存和管理。

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

Evolune 基于上游项目继续维护和扩展。相关项目名称、代码和素材的权利归各自权利人所有。

## 许可证

本项目采用 [MIT License](LICENSE)。
