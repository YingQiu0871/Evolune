# DeepSeek 架构审阅处置记录 V1

**核验日期**：2026-08-01

**核验范围**：当前 Evolune 工作树、`docs/evolune/`、`feiwuliyong/`、`reviews/DEEPSEEK_ARCH_AUDIT_V1.md`

**变更边界**：本轮只核验事实并修订 Markdown 文档，不修改 Android、Kotlin、Gradle、资源或业务逻辑。

## 结论口径

- `Accepted`：问题和主要修复方向均成立。
- `Accepted with Changes`：问题成立，但证据、范围或建议方案需要调整。
- `Rejected`：当前代码或文档证据不支持该问题。
- `Deferred`：问题成立，但不是当前阶段的阻断项。
- `Needs Product Decision`：必须由项目所有者决定产品或架构政策。
- `Duplicate`：与另一项具有相同根因和修复路径。

优先级以本次核验后的“最终优先级”为准。`P0` 是进入后续开发前的阻断项，`P1` 是目标版本必需项，`P2` 是建议项，`P3` 是后续清理或验证项。

## Issue 处置表

| Issue ID | DeepSeek 严重程度 | Codex 处理结论 | 实际证据 | 最终优先级 | 所属阶段 | 拟修改文件 | 所需测试 | 是否涉及许可证风险 |
|---|---|---|---|---|---|---|---|---|
| I-01 | P0 | Accepted with Changes | 手机与 Wear Manifest 均为 `allowBackup=true`，且当前没有 `dataExtractionRules`/`fullBackupContent` 引用；`app/src/main/res/xml/` 中对应规则已不存在。风险成立，但具体备份范围和 OEM/系统行为不能仅凭 Manifest 断言，必须先决定允许备份的数据范围。 | P0 | Phase 0 决策门槛 | 后续：两个 Manifest、`app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml`、隐私文档 | Manifest 合并结果、备份排除规则、备份/恢复设备验证 | 否 |
| I-02 | P0 | Accepted with Changes | 根 `LICENSE` 仅声明 Evolune 的 MIT 版权；当前无 `NOTICE` 或统一来源台账。`upstream/master` 的许可证证据为 MIT，而 `feiwuliyong` 包内声明快照和补丁为 Featherline GPLv3；现有文件的逐文件血缘尚未核实。应先建立来源台账，再决定 NOTICE 内容，不能直接把 README 致谢等同于代码复用。 | P0 | Phase 0 | `docs/SOURCE_PROVENANCE.md`；人工确认后可能新增 `NOTICE` | 来源台账逐项复核、依赖许可证清单、发布前人工许可证审查 | 是 |
| I-03 | P1 | Needs Product Decision | `FEATURE_MATRIX.md` 将 Tracked Date 标为 P1；`ROADMAP.md` 标为 1.0/P2；ADR-011 仍以“若产品确认需要”为前提。当前代码无该模型。 | P2（暂定 1.0，非 MVP） | Phase 0 决策门槛；实现最早 Phase 1 | `FEATURE_MATRIX.md`、`ROADMAP.md`、`DECISIONS.md`、`MIGRATION_PLAN.md` | 文档一致性检查；若进入实现则补时区、DST、迁移与审计测试 | 否 |
| I-04 | P1 | Accepted with Changes | `DoseEvent.timeH` 是 Unix epoch 小时的 `Double`，迁移计划只描述生成 `occurredAt`，未定义舍入、容差、回滚和旧 JSON 语义。DeepSeek 对精度损失的表述过强；真实风险是迁移规则不明确，而非已证实的大量精度丢失。 | P1 | Phase 1 | 后续：领域模型、Room migration、外部 DTO、`MIGRATION_PLAN.md` | `timeH` 往返容差、旧数据库、旧 JSON、PK 结果等价、时区/DST | 否 |
| I-05 | P1 | Accepted with Changes | 当前 `/hrt/plans`、`/hrt/request-plans`、`/hrt/dose-actions` 使用 DataMap/JSON，无统一协议版本、checksum、ack 或兼容矩阵。建议采用共享纯 Kotlin 协议、显式能力/版本策略和只读 legacy fallback；不预设长期双写所有 payload。 | P1 | Phase 4 | 后续：`core:wear-protocol`、手机/Wear bridge、协议文档 | Codec、未知版本、重复命令、checksum、断连重连、跨版本设备 | 否；若参考迁移包代码则是 |
| I-06 | P1 | Accepted | `feiwuliyong/01-product-docs/03-migration-checklist.md` 要求复制 patch，`06-source-map.md` 要求按顺序应用并运行 `git apply --check`，与 ADR-002 和包内 GPLv3 说明直接冲突。 | P0（迁移前阻断） | Phase 0 | `feiwuliyong/01-product-docs/03-migration-checklist.md`、`06-source-map.md` | 搜索并人工确认不再指导 Evolune 直接复制或应用 GPLv3 patch | 是 |
| I-07 | P1 | Needs Product Decision | 架构图让 feature 依赖 `core:database`，规则又只允许 Repository/Use Case；模块表仅写“Repository 实现”，没有明确接口归属。推荐建立 `core:data-api` 逻辑边界并隐藏 DAO/Entity，过渡期先用独立 package，最终 Gradle module 创建时机需所有者确认。 | P1 | Phase 0 记录；Phase 1 落地 | `ARCHITECTURE.md`、`DECISIONS.md`；后续 Gradle/module 边界 | 依赖图检查、feature 编译依赖、禁止 DAO/Entity 泄漏的架构测试 | 否 |
| I-08 | P2 | Accepted with Changes | `ARCHITECTURE.md` 将手机 Wear bridge 标成 `core:sync`，同一名称又指未来 provider；Phase 4/7 也复用该名称。Wear Data Layer 是配对设备传输，云同步是账户、加密备份、冲突与 provider 编排，职责不同。 | P1 | Phase 0 文档；Phase 4 与 Phase 7 实现 | `ARCHITECTURE.md`、`DECISIONS.md`、`MIGRATION_PLAN.md` | 模块依赖检查；Wear bridge 不依赖 OAuth/cloud；cloud sync 不依赖 Wearable SDK | 否 |
| I-09 | P2 | Accepted with Changes | `AppDatabase` 当前 `version=2` 且 `exportSchema=false`。问题成立；但仅把开关改为 true 不足以形成历史基线，需同时确定 schema 目录、版本基线和 migration test。 | P1 | Phase 1 | 后续：`AppDatabase.kt`、Gradle schema 配置、schema 文件、migration tests | Room schema 导出、1→2 和后续 migration、旧数据样本 | 否 |
| I-10 | P2 | Accepted with Changes | `WearDoseListenerService` 直接获取 `AppDatabase` 并调用 `doseEventDao().upsertEvent`；Widget 也存在同类路径。平台 service/receiver 可以作为 adapter，但写操作应进入幂等 Use Case/应用服务，而不是只把 DAO 包一层 Repository。 | P2 | Phase 2/4 | 后续：Wear/Widget adapter、Use Case、Repository contract | 重复动作幂等、service 重启、失败重试、Widget/Wear 写入一致性 | 否 |
| I-11 | P2 | Accepted with Changes | Wear Tile 的类、缓存和 Data Layer 基础路径确实存在，因此“已确认”并非错误；问题在于该状态没有注明“基础实现，协议未版本化”。 | P2 | Phase 0 文档 | `PRODUCT_OVERVIEW.md`、`FEATURE_MATRIX.md` | 文档状态定义检查；协议测试归入 I-05 | 否 |
| I-12 | P1 | Accepted with Changes | 功能矩阵的 P1 未携带发布阶段，路线图把完整 Wear App 放在 1.0/P1。两者不是实质优先级冲突，但表达含混，应统一为 `P1（1.0）`，并与当前仅 Tile 的事实分开。 | P1（1.0） | Phase 0 文档；Phase 4 实现 | `FEATURE_MATRIX.md`、`ROADMAP.md`、`readme.md` | 文档一致性；未来 Wear Activity、安装、断连和快速记录设备测试 | 否；若复制 Featherline UI/资源则是 |
| I-13 | P2 | Duplicate | 与 I-09 同一根因：未导出 Room schema，因此缺少可复现的 schema 基线和迁移验证。数据库已存在显式 `MIGRATION_1_2`，不能表述为“完全无迁移”，但测试基线确实不足。 | P1（随 I-09） | Phase 1 | 同 I-09 | 同 I-09 | 否 |
| I-14 | P2 | Accepted | 手机端和 Wear 端分别硬编码 `/hrt/*` 路径与 DataMap key，`WearSyncManager`、`WearPlanListenerService` 和 `WearDataLayer` 之间没有共享编译期契约。 | P1 | Phase 4 | 后续：`core:wear-protocol`、三处 Data Layer adapter | 手机/Wear 共用常量、codec contract、路径兼容和 legacy fallback | 否；若复制 GPLv3 协议实现则是 |
| I-15 | P2 | Accepted with Changes | DeepSeek 审计时记录了两个完整 README；核验开始时根 README 已被移走，只剩 `docs/evolune/README.md`，且其中 `LICENSE` 相对链接错误。修复应是恢复简洁根入口并指向单一详细文档，而不是长期维护两份完整正文。 | P1 | Phase 0 | 根 `readme.md`、`docs/evolune/README.md` | Markdown 相对链接检查、GitHub 根页面人工预览 | 否 |
| I-16 | P2 | Accepted with Changes | `ARCHITECTURE.md` 已有分层测试清单，`MIGRATION_PLAN.md` 各阶段也有测试和验收，但缺少统一的发布门槛、设备矩阵所有者和失败处置。不是“完全无测试策略”，而是策略未形成可执行基线。 | P2 | Phase 0 记录；Phase 8 完成 | 本处置记录；后续可新增 `docs/evolune/TEST_STRATEGY.md` | CI 门槛、Room migration、手机/Wear 设备矩阵、备份恢复演练 | 否 |
| I-17 | P2 | Accepted with Changes | 审计所指根 `readme.md` 在核验开始时已被移走，因此“根 README 只列 app/wear”的当前证据已过时；但根入口缺失使 `docs/`、`reviews/` 和迁移资料更难发现，问题结果仍成立。 | P1 | Phase 0 | 根 `readme.md`、`docs/evolune/README.md` | 根目录链接和大小写敏感环境验证 | 否 |
| I-18 | P3 | Rejected | `SIMULATION_POINTS_PER_HOUR = 12.0` 的注释是“5 分钟一个数据点”；一小时 12 个点正好是每 5 分钟一个点，代码与注释一致。 | 无需处理 | 无 | 无 | 现有 simulation sampling 测试即可 | 否 |
| I-19 | P3 | Needs Product Decision | `CorePK.VD_PER_KG = 2.0`，相邻注释称文献值通常为 10–15 L/kg；常量测试只锁定 2.0，没有解释这是校准值、经验值还是文献值。不能只改注释或常量，需确认参数来源和产品期望。 | P3（参数审查） | Phase 1 前确认；最晚 Phase 8 | 后续：`PKParameters.kt`、`SimulationEngine.kt`、`PK_IMPLEMENTATION.md`、来源台账 | 参数来源复核、数值回归、真实样本校准、医疗免责声明 | 是（来源可追溯性） |
| I-20 | P3 | Deferred | Debug signing config 使用固定密码 `DEBUG1`，CI 会生成 debug keystore；该密钥不用于 release 身份，当前也未发现 debug keystore 被跟踪。属于构建卫生问题，不是发布密钥泄露。 | P3 | Phase 8/CI 清理 | 后续：`app/build.gradle.kts`、CI 文档 | 确认 release signing 与 debug 分离、秘密扫描、keystore 跟踪检查 | 否 |

## 关键决策与阶段门槛

1. **Phase 0 阻断项**：备份政策必须由项目所有者确认；来源台账必须建立；迁移资料不得继续指导直接复制或应用 GPLv3 patch；根 README 和文档链接必须可达。
2. **Phase 1 进入条件**：不得存在未处置的 P0；Repository contract 的归属必须记录；Tracked Date 保持 `P2（1.0，待产品确认，非 MVP）` 或由所有者显式改为 MVP；`timeH` 迁移不变量、Room schema 基线和 migration test 方案必须先确定。
3. **Wear 与云边界**：Wear 同步只指配对手机和手表之间的快照/命令传输；本地备份负责导出恢复；`core:sync` 仅保留给未来云 provider 和多设备冲突编排。
4. **许可证边界**：`feiwuliyong` 中的源码快照、补丁和专属资源继续留在资料区，不进入 Evolune 构建；任何复用都必须先完成逐文件来源确认。

## 本轮不实施的事项

- 不修改 Manifest 或补回备份 XML；I-01 需要产品所有者先决定备份政策。
- 不修改 `AppDatabase`、Room schema、`timeH`、Repository、Wear 协议、PK 参数或 debug signing。
- 不创建新 Gradle module，不运行迁移，不导入 GPLv3 patch。
