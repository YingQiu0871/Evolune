# Evolune（月序）

> 面向 Android 与 Wear OS 的激素用药记录、提醒和浓度趋势工具。

[![Build Debug APK](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml)
[![GitHub Downloads](https://img.shields.io/github/downloads/YingQiu0871/Evolune/total?style=flat&logo=github&label=Downloads)](https://github.com/YingQiu0871/Evolune/releases)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](../../LICENSE)

Evolune 是一个以本地数据为中心的 Android/Wear OS 用药记录工具。它用于记录日常用药、管理周期方案、安排提醒，并通过药代动力学模型展示雌二醇浓度的历史与预测趋势。所有个人记录默认保存在本机。

> [!IMPORTANT]
> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。医疗相关决策请咨询具备资质的专业人士，并以实际检验结果为准。

当前发布与实现边界以 [Current Status](CURRENT_STATUS.md) 为准。

## 当前稳定版本

**Evolune v1.0.0** 已于 2026-08-15 发布。请从 [Evolune v1.0.0 GitHub Release](https://github.com/YingQiu0871/Evolune/releases/tag/v1.0.0) 下载经过签名的 Phone APK 与 Wear APK。

GitHub Actions 的 `Build Debug APK` 产物只用于开发和测试。Debug 与 Release 使用不同的应用 ID 和签名，可同时安装，但数据不会自动互通。

## 主要功能

- **用药记录**：添加、编辑和删除用药事件，并查看历史。
- **多种给药途径**：支持肌肉注射、口服、舌下含服、透皮凝胶及贴片应用或移除。
- **用药方案**：创建每日、每周或自定义间隔方案，并配置稳定、有序的用药时间槽。
- **提醒与签到**：根据启用的方案安排系统通知，并可从通知快速确认本次用药。
- **浓度趋势**：根据药物、剂量、途径、体重和历史记录计算当前浓度及未来趋势。
- **桌面小组件**：显示浓度和近期方案，并支持快速记录。
- **Wear OS 支持**：通过 Tile 查看浓度、方案并提交剂量动作；手机端提供重放、幂等和冲突处理。
- **数据导入导出**：通过文件或剪贴板导入、导出 JSON，兼容 `hrt.mahiro.uk` 数据格式。
- **个性化设置**：支持深浅色主题、动态取色、12/24 小时制和自动检查更新。

v1.0 的生产代码仍由 `app` 和 `wear` 两个 Android application 模块组成。领域模型、Repository contract 与 Room 实现已在 `app` 内形成明确 package 边界，但尚未拆为多个 Gradle 模块。Wear 当前以 Tile/Data Layer 为主要交互，不是完整的未来 Wear App；其 `/hrt/*` payload 也尚未形成通用版本化协议。

## 系统要求与身份

- 手机端：Android 12 及以上（`minSdk = 31`），应用 ID `io.github.yingqiu0871.evolune`
- 手表端：Wear OS / Android API 30 及以上（`minSdk = 30`），应用 ID `io.github.yingqiu0871.evolune.wear`
- v1.0.0：`versionCode = 10060`

## 快速上手

1. 在“设置”中填写体重，用于浓度模型计算。
2. 在“记录”中添加已有的用药记录。
3. 在“方案”中创建未来计划，并按需启用提醒。
4. 返回“主页”查看当前浓度、历史曲线和未来预测。
5. 如需迁移，在“设置”中导出 JSON 数据，并在目标设备上导入。

## 本地开发构建

构建环境：JDK 17、Android SDK 36 和 Git。

```powershell
.\gradlew.bat test assembleDebug
```

macOS / Linux 使用 `./gradlew test assembleDebug`。Debug APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
wear/build/outputs/apk/debug/wear-debug.apk
```

Release signing 需要项目维护者控制的持久外部签名身份；开发者不应以 Debug 签名或临时密钥替代正式发布签名。

## 项目结构

```text
app/      Android 手机端、桌面小组件、提醒、Room、Repository 与 PK 模型
wear/     Wear OS Tile、方案/浓度缓存与手机 Data Layer
docs/     当前文档、历史设计、工程报告与来源记录
reviews/  外部审阅报告和逐项处置记录
```

项目使用 Kotlin、Jetpack Compose、Room、DataStore、Kotlin Serialization、Android RemoteViews、Wearable Data Layer 和 Wear Tiles。

## 数据与隐私

- 用药记录、方案和设置保存在设备本地；Room v3 是核心事实来源。
- 应用本身不提供云同步、Health Connect 或 Google Drive 集成。
- 网络权限用于检查 GitHub 上的新版本。
- 导出的 JSON 文件由用户自行保存和管理，内容可能包含敏感健康数据。
- 当前数据库使用 Room 默认存储，未启用 SQLCipher 或其他数据库透明加密。
- Phone 与 Wear 的 Android Auto Backup 和设备迁移规则排除全部应用私有数据；用户应通过 JSON 导出/导入主动迁移。

## 当前限制

- Health Connect 与 Google 云备份计划用于 v1.2，尚未实现。
- Wear 与 Phone Widget 的下一轮增强先由 v1.1 Gap Audit 锁定范围。
- Tracked Date 仍为 deferred，没有实体或产品入口。
- 个性化 calibration/PK 2.0 不属于 v1.0。

## 常见问题

### 为什么曲线与实际化验结果不同？

模型使用通用参数进行估算，个体吸收、代谢、给药误差和检测时间都会造成偏差。请以实际检验结果和专业医疗建议为准。

### 为什么没有收到提醒？

请确认方案已经启用，并检查通知权限、精确闹钟权限及设备的后台省电限制。

### 如何迁移或备份数据？

在设置页导出 JSON 文件或复制到剪贴板；在新设备上使用对应的导入功能恢复。Android Auto Backup 和设备迁移不会复制 Evolune 私有数据。

## 致谢与许可证

- 上游项目：[NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker)
- 灵感来源：[SmirnovaOyama/Oyama-s-HRT-Tracker](https://github.com/SmirnovaOyama/Oyama-s-HRT-Tracker)
- PK 参考实现：[LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test)

Evolune 是 HRTTracker 的独立延续与大规模重构。当前由盈秋（[`YingQiu0871`](https://github.com/YingQiu0871)）维护，公共仓库为 [`YingQiu0871/Evolune`](https://github.com/YingQiu0871/Evolune)。Git 历史直接继承 `upstream/master` 的 MIT 基线，公开包名为 `io.github.yingqiu0871.evolune`。

当前发布树不包含 Featherline/`feiwuliyong` 源码、补丁或专属资源。相关 GPLv3 历史材料仅存在于受保护的本地证据和内部 checkpoint ref，不得发布所有本地 ref、完整对象库或未过滤的全仓库 bundle。

当前 PK 实现实质上派生自 HRT-Recorder-PKcomponent-Test。2026-08-14，原作者明确授权 Evolune 使用、复制、修改、移植、二次开发、分发修改后的源代码和编译后的应用，并将相应衍生代码按 MIT License 开源发布；授权仅覆盖作者本人拥有相关权利或有权授权的内容。项目保留来源及相关贡献者的归属、版权和许可说明。该授权不表示整个上游仓库自动变为 MIT，也不代表第三方贡献者授予权利。

根 [MIT License](../../LICENSE) 适用于 Evolune 自有工作、兼容继承内容及上述明确授权范围内的相应衍生代码。详见 [NOTICE](../../NOTICE)、[来源追踪记录](../SOURCE_PROVENANCE.md) 与 [第三方许可证与通知](../../THIRD_PARTY_NOTICES.md)。

## 文档索引

### Current documentation

- [Current Status](CURRENT_STATUS.md)
- [产品概览](PRODUCT_OVERVIEW.md)
- [架构](ARCHITECTURE.md)
- [功能矩阵](FEATURE_MATRIX.md)
- [路线图](ROADMAP.md)
- [架构决策记录](DECISIONS.md)

### Provenance / licensing

- [来源追踪记录](../SOURCE_PROVENANCE.md)
- [NOTICE](../../NOTICE)
- [第三方许可证与通知](../../THIRD_PARTY_NOTICES.md)

### Historical design / evidence

- [Pre-v1 Migration Plan](MIGRATION_PLAN.md)
- [Phase 0 Report](../PHASE_0_REPORT.md)
- [Phase 1 reports](../phase-reports/)
- [External review records](../../reviews/)
