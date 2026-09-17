# Evolune（月序）

Evolune 是面向 Android 与 Wear OS 的本地优先用药记录、提醒和药代动力学趋势工具。

Evolune is a local-first medication logging, reminder, and pharmacokinetic trend app for Android and Wear OS.

> Evolune 仅用于学习、研究和个人记录，不构成诊断、处方或治疗建议。
>
> Evolune is intended only for learning, research, and personal record-keeping. It does not provide diagnoses, prescriptions, or treatment advice.

[![Build Debug APK](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml/badge.svg?branch=main)](https://github.com/YingQiu0871/Evolune/actions/workflows/apkdebug.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 发布与开发基线 / Release and Development Baseline

当前公开稳定版本是 **v1.6.0**，发布于 2026-09-10；`v1.5.0` 是上一版已封存的稳定发布。

The current public stable release is **v1.6.0**, published on September 10, 2026. `v1.5.0` is the previous finalized stable release.

[下载 Evolune v1.6.0 / Download Evolune v1.6.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0)

Release 提供经过签名的 Phone APK 与 Wear APK。GitHub Actions 中的 Debug APK 仅供开发和测试，不是正式版本的主要下载渠道；Debug 与 Release 的应用 ID、签名和本地数据相互独立。

The release includes signed Phone and Wear APKs. Debug APKs from GitHub Actions are intended for development and testing, rather than stable distribution. Debug and Release builds use separate application IDs, signatures, and local data.

当前 `main` 对应已发布的 **v1.6.0**。v1.6 Widget Gallery 已完成独立复审、真实 Phone/Wear 覆盖安装与项目负责人真实手表验收。具体证据范围与局限见[最终发布门禁](docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md)。

The current `main` reflects the released **v1.6.0**. The v1.6 Widget Gallery completed independent review, in-place installation on physical Phone/Wear devices, and the project owner's acceptance on a real watch. See the [final release gate](docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md) for the evidence scope and limitations.

## 已发布能力 / Shipped Features

| 中文 | English |
| --- | --- |
| 用药方案、用药记录、历史、提醒与通知签到 | Medication plans, dose records, history, reminders, and dose confirmation from notifications |
| JSON 导入导出与 PK 浓度趋势展示 | JSON import/export and PK concentration trends |
| 可选的 Health Connect 前台体重读取，以及用户主动授权的 Google Drive 加密备份/恢复（v1.2 起） | Optional foreground weight reads from Health Connect and explicitly authorized encrypted backup/restore through Google Drive, available since v1.2 |
| Room v3、稳定计划槽位及 Repository 数据边界 | Room v3, stable schedule slots, and Repository data boundaries |
| 四个独立 Phone Widget：今日计划、下一次服药、当前 E2、E2 趋势；支持响应式尺寸、滚动计划、Material You 与预制配色 | Four separate Phone widgets: Today's Plan, Next Dose, Current E2, and E2 Trend; responsive sizing, a scrollable plan list, Material You, and preset palettes |
| Wear App、下一次服药/今日计划/当前 E2 三个新 Tile、兼容 E2 曲线 Tile 及三个 Complication | A Wear App, three new Tiles for Next Dose / Today's Plan / Current E2, the compatible legacy E2 curve Tile, and three Complications |
| Phone/Wear Data Layer 同步、幂等确认、精确撤销及 occurrence 级“跳过本次”提醒抑制 | Phone/Wear Data Layer synchronization, idempotent confirmation, precise undo, and “Skip this dose” reminder suppression for a specific scheduled occurrence |
| 自动更新检查 | Automatic update checks |

当前 Phone Room/domain/repository 仍是用药事实来源；Widget 和 Wear 只使用派生状态或缓存。Health Connect 仅在前台按授权读取体重，不写入用药数据；Google Drive 提供手动加密备份/恢复，不提供后台或实时云同步。Android Auto Backup/设备迁移的私有数据排除规则与应用内主动备份是不同机制。

Phone Room/domain/repository remains the source of truth for medication data; widgets and Wear use only derived state or caches. Health Connect reads authorized weight data in the foreground and does not write medication data. Google Drive provides manual encrypted backup/restore, without background or real-time cloud synchronization. Excluding private data from Android Auto Backup/device transfer is separate from user-initiated in-app backups.

已安装 v1.0 Wear APK 的用户需要按 [Wear v1.1 身份迁移说明](docs/evolune/WEAR_V11_MIGRATION.md) 卸载旧 Wear 包并安装 v1.1+ 主线身份；Phone 应用及其 Room 数据不受影响。

Users with the v1.0 Wear APK need to follow the [Wear v1.1 identity migration guide](docs/evolune/WEAR_V11_MIGRATION.md), uninstall the old Wear package, and install the shared application identity used by v1.1 and later. The Phone app and its Room data are unaffected.

v1.7（History & Insights：History / Timeline / Insights / 回顾性 PK / 数据可携）的 release candidate 门禁已批准（`APPROVE V1.7.0 RELEASE CANDIDATE`）；版本打包（版本号、签名与正式发布产物）尚未执行，版本元数据在授权发布打包步骤之前仍为 1.6.0。

The v1.7 milestone (History & Insights: History, Timeline, Insights, retrospective PK, and data portability) has passed its release-candidate gate (`APPROVE V1.7.0 RELEASE CANDIDATE`). Release packaging (version metadata, signing, and distribution artifacts) has not been executed; version metadata still reflects 1.6.0 until the authorized release-packaging step.

完整的产品说明、构建步骤、隐私边界和致谢见[项目详细说明](docs/evolune/README.md)。当前发布与实现事实以[当前状态](docs/evolune/CURRENT_STATUS.md)为准。

See the [detailed project README](docs/evolune/README.md) for the product description, build instructions, privacy boundaries, and acknowledgments. [Current Status](docs/evolune/CURRENT_STATUS.md) is the canonical reference for the current release and implementation.

## 文档 / Documentation

### 现行文档 / Current Documentation

- [当前状态 / Current Status](docs/evolune/CURRENT_STATUS.md)
- [截至 v1.6 的版本回顾与文档盘点 / Version Review and Documentation Audit through v1.6](docs/evolune/DOCUMENTATION_REVIEW_V16_2026-09-12.md)
- [完整文档索引 / Complete Documentation Index](docs/evolune/DOCUMENTATION_INDEX.md)
- [快速开始 / Quick Start](QUICK_START_GUIDE.md)
- [项目详细说明 / Detailed Project README](docs/evolune/README.md)
- [产品概览 / Product Overview](docs/evolune/PRODUCT_OVERVIEW.md)
- [架构 / Architecture](docs/evolune/ARCHITECTURE.md)
- [功能矩阵 / Feature Matrix](docs/evolune/FEATURE_MATRIX.md)
- [路线图 / Roadmap](docs/evolune/ROADMAP.md)
- [架构决策记录 / Architecture Decision Records](docs/evolune/DECISIONS.md)

### 来源与许可记录 / Provenance and Licensing Records

- [来源追踪记录 / Source Provenance](docs/SOURCE_PROVENANCE.md)
- [版权与归属通知 / NOTICE](NOTICE)
- [第三方许可证与通知 / Third-Party Licenses and Notices](THIRD_PARTY_NOTICES.md)

### 历史设计与证据 / Historical Design and Evidence

- [v1 之前的迁移计划 / Pre-v1 Migration Plan](docs/evolune/MIGRATION_PLAN.md)
- [第 0 阶段报告 / Phase 0 Report](docs/PHASE_0_REPORT.md)
- [第 1 阶段报告 / Phase 1 Reports](docs/phase-reports/)
- [外部审阅记录 / External Review Records](reviews/)

## 来源与许可证 / Provenance and License

Evolune 是 [NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker) 的独立延续与大规模重构；直接上游的 MIT 许可和版权声明保留在 [LICENSE](LICENSE) 中。

Evolune is an independent continuation and extensive refactoring of [NaiveTomcat/HRTTracker](https://github.com/NaiveTomcat/HRTTracker). The direct upstream's MIT license and copyright notice are preserved in [LICENSE](LICENSE).

**PK 实现来源：** 当前雌二醇药代动力学实现实质上派生自 LaoZhong-Mihari 发布的 [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test)。2026-08-14，原作者明确授权 Evolune 使用、复制、修改、移植、二次开发、分发修改后的源代码和编译后的应用，并将相应衍生代码按 MIT License 开源发布；授权仅覆盖作者本人拥有相关权利或有权授权的内容。项目继续保留来源及相关贡献者的归属、版权和许可说明。该授权不表示整个上游仓库自动变为 MIT，不表示作者代表第三方贡献者授予权利，也不表示上游仓库已经新增正式 `LICENSE` 文件。

**PK implementation provenance:** The current estradiol pharmacokinetic implementation is substantially derived from [HRT-Recorder-PKcomponent-Test](https://github.com/LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test), published by LaoZhong-Mihari. On August 14, 2026, the original author explicitly authorized Evolune to use, copy, modify, port, further develop, and distribute the modified source code and compiled applications, and to release the corresponding derived code as open source under the MIT License. This authorization covers only content for which the author owns the relevant rights or is entitled to grant permission. The project continues to preserve provenance, contributor attribution, copyright, and licensing notices. This permission does not automatically place the entire upstream repository under MIT, grant rights on behalf of third-party contributors, or imply that a formal `LICENSE` file has been added upstream.

根 [MIT License](LICENSE) 适用于 Evolune 自有工作、按兼容条款继承的内容，以及上述明确授权范围内按 MIT 发布的相应衍生代码；它不将整个上游仓库或第三方贡献自动重新许可为 MIT。

The root [MIT License](LICENSE) applies to Evolune's own work, content inherited under compatible terms, and the corresponding derived code released under MIT within the explicit authorization described above. It does not automatically relicense the entire upstream repository or third-party contributions under MIT.
