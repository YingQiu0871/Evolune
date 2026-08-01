# Evolune（月序）

Evolune 是面向 Android 与 Wear OS 的本地优先用药记录、提醒和药代动力学趋势工具。

> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。

[![Build Debug APK](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/Yuning-Gu/Evolune/actions/workflows/apkdebug.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

完整的产品说明、构建步骤、隐私边界和致谢见 [项目详细说明](docs/evolune/README.md)。

## 文档

- [产品概览](docs/evolune/PRODUCT_OVERVIEW.md)
- [架构设计](docs/evolune/ARCHITECTURE.md)
- [功能矩阵](docs/evolune/FEATURE_MATRIX.md)
- [迁移计划](docs/evolune/MIGRATION_PLAN.md)
- [路线图](docs/evolune/ROADMAP.md)
- [架构决策记录](docs/evolune/DECISIONS.md)
- [来源追踪模板](docs/SOURCE_PROVENANCE.md)
- [DeepSeek 审阅处置记录](reviews/REVIEW_DISPOSITION_V1.md)

## 当前边界

- 当前构建模块为 `app` 和 `wear`；Wear 端已有 Tile 和基础设备传输，但尚无目标版本协议或完整 Wear App。
- Health Connect、云同步、加密备份和正式 Tracked Date 模型尚未实现。
- `feiwuliyong/` 是受其来源许可证约束的迁移参考资料，不是 Evolune 构建模块，也不得作为可直接应用的 patch 集。
- Android Auto Backup 和设备迁移规则排除手机与 Wear 的应用私有数据；用户应通过 JSON 导出/导入完成主动迁移。

本项目采用 [MIT License](LICENSE)，附加说明见 [NOTICE](NOTICE)。第三方与上游来源以 [来源追踪模板](docs/SOURCE_PROVENANCE.md) 的核验结果为准。
