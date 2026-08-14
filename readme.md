# Evolune（月序）

Evolune 是面向 Android 与 Wear OS 的本地优先用药记录、提醒和药代动力学趋势工具。

> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。

[![Build Debug APK](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

完整的产品说明、构建步骤、隐私边界和致谢见 [项目详细说明](docs/evolune/README.md)。

## 文档

- [产品概览](docs/evolune/PRODUCT_OVERVIEW.md)
- [架构设计](docs/evolune/ARCHITECTURE.md)
- [功能矩阵](docs/evolune/FEATURE_MATRIX.md)
- [迁移计划](docs/evolune/MIGRATION_PLAN.md)
- [路线图](docs/evolune/ROADMAP.md)
- [架构决策记录](docs/evolune/DECISIONS.md)
- [来源追踪记录](docs/SOURCE_PROVENANCE.md)
- [第三方许可证与通知](THIRD_PARTY_NOTICES.md)
- [DeepSeek 审阅处置记录](reviews/REVIEW_DISPOSITION_V1.md)

## 当前边界

- 当前构建模块为 `app` 和 `wear`；Wear 端已有 Tile 和基础设备传输，但尚无目标版本协议或完整 Wear App。
- Health Connect、云同步、加密备份和正式 Tracked Date 模型尚未实现。
- 当前发布树不包含 Featherline/`feiwuliyong` 源码、补丁或专属资源；相关历史材料仅保存在受保护的本地证据和内部 ref 中，不得随发布分发。
- Android Auto Backup 和设备迁移规则排除手机与 Wear 的应用私有数据；用户应通过 JSON 导出/导入完成主动迁移。

## 来源与许可证

Evolune 是 [NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker) 的独立延续与大规模重构；直接上游的 MIT 许可和版权声明保留在 [LICENSE](LICENSE) 中。

**PK 实现来源：** 当前雌二醇药代动力学实现实质上派生自 LaoZhong-Mihari 发布的 [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test)。项目已保留归属说明，但在来源审查中未找到明确的仓库许可证或许可授权；项目已向原作者请求明确许可，回复仍在等待中。本说明仅记录来源，不构成许可授权。项目所有者已决定在准确披露这一待定风险的前提下继续准备 v1.0 发布。

根 [MIT License](LICENSE) 适用于 Evolune 自有工作及按兼容条款继承的内容；另行引入的第三方组件和待定来源项目不因此自动成为 MIT。详见 [NOTICE](NOTICE)、[来源追踪记录](docs/SOURCE_PROVENANCE.md) 与 [第三方许可证与通知](THIRD_PARTY_NOTICES.md)。
