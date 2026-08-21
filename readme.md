# Evolune（月序）

Evolune 是面向 Android 与 Wear OS 的本地优先用药记录、提醒和药代动力学趋势工具。

> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。

[![Build Debug APK](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 发布与开发基线

当前公开稳定版本仍是 **v1.0.0**，发布于 2026-08-15。

[前往 Evolune v1.0.0 Release 下载](https://github.com/YingQiu0871/Evolune/releases/tag/v1.0.0)

Release 提供经过签名的 Phone APK 与 Wear APK。GitHub Actions 中的 Debug APK 仅供开发和测试，不是正式版本的主要下载渠道；Debug 与 Release 的应用 ID、签名和本地数据相互独立。

`main` 已包含于 2026-08-21 完成的 v1.1 Phone Widget Completion 开发里程碑。v1.1 是已完成的开发里程碑，不代表创建了新的 GitHub Release 或 tag。

## 已发布能力

- 用药方案、用药记录、历史、提醒与通知签到
- JSON 导入导出与 PK 浓度趋势展示
- Room v3、稳定计划槽位及 Repository 数据边界
- occurrence-driven RemoteViews 手机桌面小组件：多时间槽独立行、响应式尺寸、超出容量时纵向滚动、实际记录时间和 occurrence-scoped 快速记录
- Wear Tile、手机/手表 Data Layer 同步及幂等剂量动作
- 自动更新检查

当前 Phone Room/domain/repository 仍是唯一事实来源；Widget 和 Wear 只使用派生状态或缓存。Health Connect 与 Google backup/restore 属于尚未开始的 v1.2，完整 Wear OS Companion App 规划在 v1.3，Widget Gallery 规划在 v1.6。当前 Phone/Wear 私有数据明确排除于 Android Auto Backup 和设备迁移；跨设备迁移使用用户主动控制的 JSON 导出/导入。

已安装 v1.0 Wear APK 的用户需要按 [Wear v1.1 身份迁移说明](docs/evolune/WEAR_V11_MIGRATION.md) 卸载旧 Wear 包并安装 v1.1+ 主线身份；Phone 应用及其 Room 数据不受影响。

下一开发里程碑是 **v1.2 — Google Integration & Data Continuity**，目前为规划中、尚未开始；Health Connect 与 Google backup/restore 将分别验收，Room 继续保持权威。

完整的产品说明、构建步骤、隐私边界和致谢见 [项目详细说明](docs/evolune/README.md)。当前发布与实现事实以 [Current Status](docs/evolune/CURRENT_STATUS.md) 为准。

## 文档

### Current documentation

- [Current Status](docs/evolune/CURRENT_STATUS.md)
- [项目详细说明](docs/evolune/README.md)
- [产品概览](docs/evolune/PRODUCT_OVERVIEW.md)
- [架构](docs/evolune/ARCHITECTURE.md)
- [功能矩阵](docs/evolune/FEATURE_MATRIX.md)
- [路线图](docs/evolune/ROADMAP.md)
- [架构决策记录](docs/evolune/DECISIONS.md)

### Provenance / licensing

- [来源追踪记录](docs/SOURCE_PROVENANCE.md)
- [NOTICE](NOTICE)
- [第三方许可证与通知](THIRD_PARTY_NOTICES.md)

### Historical design / evidence

- [Pre-v1 Migration Plan](docs/evolune/MIGRATION_PLAN.md)
- [Phase 0 Report](docs/PHASE_0_REPORT.md)
- [Phase 1 reports](docs/phase-reports/)
- [External review records](reviews/)

## 来源与许可证

Evolune 是 [NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker) 的独立延续与大规模重构；直接上游的 MIT 许可和版权声明保留在 [LICENSE](LICENSE) 中。

**PK 实现来源：** 当前雌二醇药代动力学实现实质上派生自 LaoZhong-Mihari 发布的 [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test)。2026-08-14，原作者明确授权 Evolune 使用、复制、修改、移植、二次开发、分发修改后的源代码和编译后的应用，并将相应衍生代码按 MIT License 开源发布；授权仅覆盖作者本人拥有相关权利或有权授权的内容。项目继续保留来源及相关贡献者的归属、版权和许可说明。该授权不表示整个上游仓库自动变为 MIT，不表示作者代表第三方贡献者授予权利，也不表示上游仓库已经新增正式 `LICENSE` 文件。

根 [MIT License](LICENSE) 适用于 Evolune 自有工作、按兼容条款继承的内容，以及上述明确授权范围内按 MIT 发布的相应衍生代码；它不将整个上游仓库或第三方贡献自动重新许可为 MIT。
