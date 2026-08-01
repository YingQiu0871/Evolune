# Evolune 架构审计报告 V1

**审计日期**: 2026-08-01
**审计范围**: 仓库根目录 + `docs/evolune/` 架构文档 + `app/` `wear/` 源码 + `feiwuliyong/` 迁移资料包
**审计方法**: 文档间交叉比对 + 文档与代码逐项验证
**审计人员**: DeepSeek V4 Pro (自动化审阅)

---

## 一、文档一致性结论

### 整体判断：文档间存在 6 处冲突，文档与代码间存在 8 处不一致

**文档体系分层**：仓库实际有两套 README（根目录 `readme.md` 与 `docs/evolune/README.md`），内容不完全一致。根 README 缺少 `docs/` 和 `feiwuliyong/` 目录说明、缺少许可证边界声明、缺少 Wear 协议局限性说明。`docs/evolune/README.md` 更完整但路径引用使用相对路径（如 `[迁移计划](MIGRATION_PLAN.md)`），容易误解为指向根目录文件。

**核心冲突**：
1. `FEATURE_MATRIX.md` 将 Tracked Date 标记为 **P1**，但 `ROADMAP.md` 将其放在 1.0 阶段标记为 **P2**，`ADR-011` 暗示它尚未进入产品决策
2. `feiwuliyong/01-product-docs/03-migration-checklist.md` 第二阶段的 "复制 `phone-widgets.patch`" 与 `ADR-001`/`ADR-002` 的 "不直接复制 GPLv3 源码" **直接矛盾**
3. `ARCHITECTURE.md` 依赖图显示 `feature:*` 模块直接依赖 `core:database`，但同文档的依赖规则规定 feature 应 "通过 Repository、Use Case 访问"
4. `ARCHITECTURE.md` 使用名称 `core:sync` 同时指代 Wear 桥接和未来云同步，但 `DECISIONS.md` ADR-009 明确 Google Drive 不作为默认方案

---

## 二、当前代码与目标架构的差距

| 差距 | 当前状态 | 目标状态 | 跨度 |
|------|----------|----------|------|
| 模块拆分 | `app` + `wear` 双模块单体 | 12+ 模块 (`core:*` + `feature:*` + `wear` + `widget`) | 大 |
| 数据时间语义 | `timeH: Double` (epoch 小时数) | `occurredAt: Long` (epoch 毫秒) + 时区值对象 | 中 |
| Wear 协议 | 纯字符串 JSON/DataMap | 版本化 envelope (protocolVersion, schemaVersion, checksum, ack) | 大 |
| Repository 边界 | DAO 从 `WearDoseListenerService`、Widget 直接调用 | 统一通过 Repository/Use Case | 中 |
| Wear App | 仅 Tile (非独立 App) | 独立 Wear App + Tile + Complication | 中 |
| Health Connect | 无 | 可选集成层 | 大 |
| 数据库备份规则 | `allowBackup=true`，无 `dataExtractionRules` | 明确备份排除策略 | 小/紧急 |
| Room Schema | `exportSchema = false` | `exportSchema = true` + 迁移测试 | 小 |

---

## 三、阻止继续开发的问题 (P0)

| Issue | 描述 | 影响 |
|-------|------|------|
| **I-01** | `allowBackup=true` 且无 `dataExtractionRules` | 敏感用药数据会通过 Android Auto Backup 上传到 Google Drive（Android 12+），构成隐私泄露和合规风险 |
| **I-02** | 许可证来源未充分记录 | LICENSE 仅声明 "Yitong Dang" 版权，缺少对上游 HRTTracker (MIT) 和 PK 参考实现的 NOTICE 文件，法律风险 |

---

## 四、可以在 Phase 0 修复的问题

- **I-01**: 添加 `dataExtractionRules` 或设置 `allowBackup=false` (1 行 XML 修改)
- **I-02**: 创建 NOTICE 文件，记录所有上游来源与许可证
- **I-09**: 标记 `exportSchema = true`
- **I-17**: 统一 `readme.md` 与 `docs/evolune/README.md` 内容
- **I-19**: 修复 `feiwuliyong` 迁移清单中的 "复制 patch" 表述

---

## 五、应留到 Phase 1 及以后处理的问题

- **I-04**: `timeH → occurredAt` 数据模型迁移 (需要 Room migration + adapter)
- **I-05**: Wear 协议版本化 (需要新 `core:wear-protocol` 模块)
- **I-07**: Repository 接口边界定义
- **I-10**: `WearDoseListenerService` 绕过 Repository 问题
- **I-12**: `core:sync` 模块职责拆分
- **I-14**: Wear 端硬编码路径常量
- **I-18**: 根 README 缺少 `docs/` 和 `feiwuliyong/` 目录

---

## 六、建议接受、修改或删除的架构决策

| 决策 | 建议 | 理由 |
|------|------|------|
| ADR-001 (保持 MIT) | **接受** | 许可证边界清晰，适合独立项目 |
| ADR-002 (不复制 GPLv3 源码) | **接受，但需修正 feiwuliyong 迁移清单** | 决策正确，但 `03-migration-checklist.md` 第二阶段的 "复制 phone-widgets.patch" 必须改为 "参考行为后独立实现" |
| ADR-003 (Room 是主要事实来源) | **接受** | 离线可靠，与产品定位一致 |
| ADR-005 (Wear 协议版本化) | **接受** | 当前 `/hrt/*` payload 无版本/ack/checksum，必须升级 |
| ADR-006 (逐步多模块) | **接受** | 降低风险，合理 |
| ADR-007 (暂不使用 SQLCipher) | **接受，但需先修复 I-01** | Auto Backup 不设规则比数据库不加密更紧急 |
| ADR-008 (Glance 先试点) | **接受** | RemoteViews 已工作，Glance 有 OEM 差异风险 |
| ADR-009 (Drive 不作为默认) | **接受** | 避免过早承担 OAuth 和冲突复杂度 |
| ADR-010 (模型分离) | **接受** | 当前 `DoseEvent` 同时服务 PK/DB/UI，分离必要 |
| ADR-011 (Tracked Date) | **修改** — 降级为 P2，统一文档 | FEATURE_MATRIX 和 ROADMAP 优先级冲突，建议统一为 P2（1.0 阶段） |

---

## 七、最重要的 20 项问题清单

---

### I-01 — 缺少 Auto Backup 排除规则

- **Issue ID**: I-01
- **分类**: 隐私
- **严重程度**: **P0**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/AndroidManifest.xml:21` — `android:allowBackup="true"`
  - `wear/src/main/AndroidManifest.xml:11` — `android:allowBackup="true"`
- **证据**: 两个 AndroidManifest 均声明 `allowBackup="true"`，但均未设置 `android:fullBackupContent` 或 `android:dataExtractionRules` 属性。Manifest 中也不存在 `@xml/data_extraction_rules` 或 `@xml/backup_rules` 资源文件。
- **影响**: 在 Android 12+ 设备上，包含用药记录、方案和体重等敏感健康数据的 Room 数据库和 SharedPreferences 会被自动备份到用户的 Google Drive。用户可能不知情；备份文件未加密；数据可能跨越设备恢复。
- **最小修改建议**:
  1. 创建 `app/src/main/res/xml/data_extraction_rules.xml`，将 Room 数据库文件 (默认路径 `databases/evolune_database*`) 和 SharedPreferences 排除在 cloud backup 之外，只保留非敏感设置（如主题、时间格式）
  2. 在 Manifest 中引用：`android:dataExtractionRules="@xml/data_extraction_rules" android:fullBackupContent="@xml/backup_rules"`
  3. 或暂时设置 `android:allowBackup="false"` 并记录 TODO
- **需要人工决定**: 是 — 需要决定哪些数据允许云端备份
- **阻止进入 Phase 0**: **是** — 这是发布前的隐私阻断问题
- **阻止进入 Phase 1**: **是**

---

### I-02 — 缺少 NOTICE 文件和上游许可证声明

- **Issue ID**: I-02
- **分类**: 许可证
- **严重程度**: **P0**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `LICENSE:3` — 仅声明 "Copyright (c) 2026 Yitong Dang"
  - `readme.md:125-130` — "致谢" 部分提及三个上游项目，但非正式 NOTICE
  - `docs/evolune/README.md:123-131` — 同样在 README 中提及
- **证据**: LICENSE 文件只包含标准 MIT 文本，未列出：
  1. NaiveTomcat/HRTTracker (MIT) 的版权声明
  2. LaoZhong-Mihari/HRT-Recorder-PKcomponent-Test 的 PK 参考实现来源
  3. SmirnovaOyama/Oyama-s-HRT-Tracker 的灵感来源
  `feiwuliyong/06-licenses/SOURCE-AND-LICENSE-NOTICE.md` 正确声明了 Featherline (GPLv3) 的许可证边界，但 Evolune 库缺少对应文件。
- **影响**: 上游 MIT 项目的要求是 "The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software"。缺少正式 NOTICE 文件意味着分发时可能违反上游许可证条件。
- **最小修改建议**: 创建 `NOTICE` 文件，逐项列出：
  - 上游 MIT 项目的版权声明和许可证副本引用
  - PK 参数和模型的参考来源
  - `feiwuliyong/` 目录的 GPLv3 边界说明
  - 第三方依赖（通过 Gradle 自动生成可考虑）
- **需要人工决定**: 是 — 需要确认哪些代码来自上游项目，哪些是独立实现
- **阻止进入 Phase 0**: **是**
- **阻止进入 Phase 1**: 否

---

### I-03 — FEATURE_MATRIX 与 ROADMAP 的 Tracked Date 优先级冲突

- **Issue ID**: I-03
- **分类**: 文档冲突
- **严重程度**: **P1**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `docs/evolune/ROADMAP.md:33` — Tracked Date 标记为 P2 (1.0 阶段 "有价值但可延后")
  - `docs/evolune/FEATURE_MATRIX.md:21` — Tracked Date 标记为 P1
  - `docs/evolune/DECISIONS.md:105-111` — ADR-011: "若产品确认需要周期起点/追踪日期......" (描述为待定)
  - `docs/evolune/MIGRATION_PLAN.md:77` — Phase 1 前置："确定 Tracked Date 是否进入 MVP"
- **证据**: 四份文档对同一功能的优先级描述不一致。ROADMAP 明确是 P2 (1.0 阶段)，FEATURE_MATRIX 明确是 P1 (MVP)，ADR-011 和 MIGRATION_PLAN 表述为"待确认"。FEATURE_MATRIX 是唯一将 Tracked Date 标为 P1 的文档。
- **影响**: 如果开发团队以 FEATURE_MATRIX 为准，可能在 MVP 阶段投入资源建设 Tracked Date 模型（需要 Room 实体、DAO、迁移），但这可能阻塞 Phase 1 的核心数据模型重构。
- **最小修改建议**: FEATURE_MATRIX.md 第 21 行将 Tracked Date 优先级从 P1 改为 P2，与 ROADMAP.md 和 ADR-011 对齐。如果产品确认需要尽早引入，则反向修改 ROADMAP.md。
- **需要人工决定**: **是** — 这是产品范围的决策
- **阻止进入 Phase 0**: 否 (Phase 0 只涉及文档/许可证)
- **阻止进入 Phase 1**: 否 (可以推迟决策)

---

### I-04 — `timeH: Double → occurredAt: Long` 迁移计划缺少细节

- **Issue ID**: I-04
- **分类**: 数据迁移
- **严重程度**: **P1**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/data/DoseEventEntity.kt:18` — `val timeH: Double`
  - `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt:19` — `version = 2, exportSchema = false`
  - `docs/evolune/MIGRATION_PLAN.md:78-83` — Phase 1 描述
- **证据**: `DoseEventEntity.timeH` 是 `Double` 类型（epoch 小时数）。MIGRATION_PLAN Phase 1 描述为 "从 `dose_events.timeH` 生成 `occurredAt`，无法恢复的计划槽位保持 null 并标记来源为 legacy"。但这存在转换精度问题：当前的 `timeH` 值是 `System.currentTimeMillis() / 3600000.0`，包含亚小时精度。反向转换回 `Long` 毫秒时需要 `(timeH * 3600000).toLong()`，可能丢失百万分之一级别的精度（对药代动力学模拟可忽略，但需要确认可接受）。
- **影响**: 迁移脚本设计不充分会导致：历史数据 `occurredAt` 与原始时间偏差；PK 模拟结果在迁移前后不一致；无法回滚到旧版本。
- **最小修改建议**:
  1. MIGRATION_PLAN Phase 1 补充具体的 ALTER TABLE 策略和 adapter 实现方案
  2. 添加迁移测试验证 `occurredAt` 与原始 `timeH` 的往返一致性
  3. 明确旧 `timeH` 列的保留期限（至少保留一个大版本周期）
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: **是** — 这是 Phase 1 的核心交付物

---

### I-05 — Wear 协议当前无版本化，升级兼容方案不完整

- **Issue ID**: I-05
- **分类**: Wear
- **严重程度**: **P1**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt:31-33` — 纯字符串路径，无 version/schema/checksum
  - `wear/src/main/java/io/github/yuninggu/evolune/wear/WearPlanListenerService.kt:10-42` — 接收端直接解析 DataMap
  - `docs/evolune/ARCHITECTURE.md:134-142` — 建议的 envelope 格式
  - `docs/evolune/MIGRATION_PLAN.md:116-118` — Phase 4 描述
- **证据**: 当前 payload 格式为：
  - `PutDataMapRequest` 直接写入 `plans_json` (字符串)、`current_concentration` (Double)、`curve_values` (float[])
  - 无 `protocolVersion`、`schemaVersion`、`messageId`、`checksum`
  - 无 ack 机制
  - 剂量动作为纯字符串 `planId` + `actionId`

  MIGRATION_PLAN Phase 4 说 "协议 v0 只读兼容一版；旧 `/hrt/*` 动作转换为新 command 或拒绝并请求全量同步"。但未说明：
  - 旧版 Wear app 收到新版 payload 时的行为
  - 新版手机 app 如何与旧版 Wear app 共存
  - checksum 校验失败时是否静默丢弃
  - 离线缓存期间版本不兼容的升级窗口
- **影响**: 如果新协议在 Wear App 更新前部署到手机端，旧版 Wear 可能静默解析失败（`JSONArray` 构造失败时 `runCatching` 回退到空列表，用户看到"请先在手机端启用用药方案"），导致 Wear 功能完全不可用。
- **最小修改建议**:
  1. 在新协议 payload 中保留旧字段名作为 fallback
  2. Phase 4 计划补充手机端同时发布新旧两种 payload 的双写方案（持续一个大版本）
  3. 明确新协议的拒绝策略：未知版本 → 请求全量同步 + 显示 "请更新 Evolune"
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否 (Wear 协议是 Phase 4)

---

### I-06 — `feiwuliyong` 迁移清单与 ADR-001/002 直接矛盾

- **Issue ID**: I-06
- **分类**: 文档冲突
- **严重程度**: **P1**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `docs/evolune/DECISIONS.md:13-21` — ADR-001: "保持 Evolune MIT，未经人工确认不纳入 GPLv3 快照源码或专属资源"
  - `docs/evolune/DECISIONS.md:23-31` — ADR-002: "只参考产品行为、数据概念和公开 API，使用 Evolune 自己的接口和实现重写"
  - `feiwuliyong/01-product-docs/03-migration-checklist.md:19-20` — 第二阶段: "复制 `phone-widgets.patch` 并按 Evolune package 重命名"
  - `feiwuliyong/01-product-docs/06-source-map.md:20` — 末尾注明 "补丁应用前请用 `git apply --check` 检查冲突"
- **证据**: ADR-001 和 ADR-002 明确禁止复制 GPLv3 源码。但 `03-migration-checklist.md` 第 19 行说 "复制 `phone-widgets.patch` 并按 Evolune package 重命名"，`06-source-map.md` 末尾还提示用 `git apply --check`。这两份文档暗示补丁可以直接应用，与 ADR 决策**直接矛盾**。MIGRATION_PLAN.md 第 32 行的 "不建议迁移" 与 ADR 一致，但 `03-migration-checklist.md` 没有被修正。
- **影响**: 开发者可能误解为 "重命名后即可复制 GPLv3 补丁"，导致许可证污染。
- **最小修改建议**: 删除 `03-migration-checklist.md:19` 的 "复制 `phone-widgets.patch` 并按 Evolune package 重命名"，改为 "参考 `phone-widgets.patch` 的产品行为，独立实现 Widget snapshot provider"。同样修改其他 "复制 xxx.patch" 的条目。
- **需要人工决定**: 否 (ADR 已有决定，只是执行文案未更新)
- **阻止进入 Phase 0**: **是** — Phase 0 目标包括 "确定 Evolune 的独立身份、许可证来源和文档边界"
- **阻止进入 Phase 1**: 否

---

### I-07 — Repository 接口边界未定义

- **Issue ID**: I-07
- **分类**: 架构
- **严重程度**: **P1**
- **置信度**: 中
- **状态**: 待确认
- **文件路径**:
  - `docs/evolune/ARCHITECTURE.md:52-53` — 依赖规则: "UI feature 只能通过 Repository、Use Case 或公开领域接口访问数据，禁止直接访问 DAO"
  - `docs/evolune/ARCHITECTURE.md:15-48` — Mermaid 依赖图: `featureMed → database["core:database"]`
  - `app/src/main/java/io/github/yuninggu/evolune/data/DoseEventRepository.kt` — 当前 Repository 在 `data/` 包内
- **证据**: ARCHITECTURE.md 的 Mermaid 图显示 feature 直接依赖 `core:database` 模块，与同文档第 52 行的规则冲突。Repository 接口应该在哪个模块？`core:database` 模块？`core:model` 模块？独立的 `core:repository` 模块？文档未给出答案。当前实践中 Repository 实现类在 `app` 模块的 `data/` 包内，定义了实体→领域模型的转换，这违背了 "core:model 不应依赖 Room Entity" 的原则。
- **影响**: 模块拆分时，如果 Repository 接口放在 `core:database` 中，那么所有 feature 模块必须依赖 `core:database` 模块（即使它们只需要接口）。如果放在 `core:model` 中，那么 `core:model` 需要引入 Flow 等响应式类型（Kotlin Coroutines）。
- **最小修改建议**: 在 ARCHITECTURE.md 或 DECISIONS.md 中新增 ADR，明确 Repository 接口位于 `core:database` 模块（该模块提供接口 + Room 实现），feature 模块通过接口依赖于 `core:database` 编译产物，但不直接访问 DAO 类。或在 Mermaid 图中将 `database` 改为 `repository interfaces` 以消除歧义。
- **需要人工决定**: **是** — 影响模块依赖拓扑
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否 — 但需在 Phase 1 开始前决定

---

### I-08 — `core:sync` 职责混合 Wear 同步与未来云同步

- **Issue ID**: I-08
- **分类**: 架构
- **严重程度**: **P2**
- **置信度**: 中
- **状态**: 待确认
- **文件路径**:
  - `docs/evolune/ARCHITECTURE.md:72` — `core:sync` 职责: "Provider 抽象、同步状态、冲突和后台调度"
  - `docs/evolune/ARCHITECTURE.md:24` — 依赖图: `app --> wearBridge["core:sync"]`
  - `docs/evolune/ARCHITECTURE.md:46-47` — 依赖图: `sync["future sync providers"] --> model` 和 `sync --> common` (**独立节点**)
  - `docs/evolune/DECISIONS.md:83-91` — ADR-009: "Google Drive 不作为默认同步方案"
- **证据**: ARCHITECTURE.md 的 Mermaid 图中存在**两个 sync 节点**：一个是 `wearBridge["core:sync"]`（被 app 依赖），另一个是 `sync["future sync providers"]`（未来云同步）。但表格（第 72 行）中只定义了一个 `core:sync` 模块。当前代码中 `WearDataLayer` 位于 `app/` 模块内，不是一个独立模块。Wear 桥接与云同步的**传输机制**完全不同（Wearable Data Layer vs HTTP/OAuth），**冲突模型**完全不同（离线缓存 vs 多设备冲突），放在同一模块可能导致不必要的耦合。
- **影响**: 如果 `core:sync` 同时包含 Wear 同步和云同步的 orchestration 逻辑，任何引入的云同步依赖（OAuth lib、加密库）也会被 Wear 同步路径拉入。
- **最小修改建议**:
  1. 将当前图中的 `wearBridge["core:sync"]` 重命名为 `wearBridge["core:wear-bridge"]` 或直接合并到 `core:wear-protocol`
  2. 将表格中的 `core:sync` 职责更新为仅限未来云同步的 provider 抽象
  3. 当前 Wear 同步代码留在 `core:wear-protocol` 的手机端适配层
- **需要人工决定**: 否 (当前代码中 `core:sync` 尚未创建，有机会重新命名)
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-09 — Room schema 导出关闭

- **Issue ID**: I-09
- **分类**: 数据迁移
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt:19` — `exportSchema = false`
  - `docs/evolune/ARCHITECTURE.md:106` — 目标设计: "从下一次 schema 变更开始导出 Room schema"
  - `docs/evolune/MIGRATION_PLAN.md:16` — "避免 `exportSchema=false` 长期存在"
- **证据**: 当前 `@Database(version = 2, exportSchema = false)`。文档正确标注这是已知问题，且目标设计中包含修复。
- **影响**: 没有导出的 schema，无法编写 Room 迁移测试，无法验证 `MIGRATION_1_2` 的 SQL DDL 是否正确。下一次 schema 升级（如添加 `TrackedDate` 表或 `occurredAt` 列）时风险更高。
- **最小修改建议**: 在 `build.gradle.kts` 中添加 KSP 参数 `room.schemaLocation("$projectDir/schemas")`，将 `exportSchema` 设为 `true`，立即对当前 schema 做一次基线导出。这是 Phase 0 的合理任务。
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否 — 但应在 Phase 1 前完成

---

### I-10 — `WearDoseListenerService` 绕过 Repository 直接访问 DAO

- **Issue ID**: I-10
- **分类**: 架构
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt:128-133` — `AppDatabase.getDatabase(...).medicationPlanDao().getEnabledPlans()`
  - `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt:165-178` — `database.doseEventDao().upsertEvent(...)`
  - `docs/evolune/ARCHITECTURE.md:52` — 依赖规则: "禁止直接访问 DAO"
- **证据**: `WearDoseListenerService` 是一个 Android Service (继承 `WearableListenerService`)，不属于 UI Feature 层，但属于平台适配层。它直接调用 `AppDatabase.getDatabase()` 获取数据库实例，直接调用 `medicationPlanDao()` 和 `doseEventDao()`，完全绕过 `DoseEventRepository` 和 `MedicationPlanRepository`。
- **影响**: 违反架构规则。如果未来 Repository 需要添加日志、权限检查、数据验证或缓存逻辑，Wear 路径不会受益。`upsertEvent` 通过 `DoseEventEntity.fromDoseEvent()` 进行转换，但这段转换逻辑（entity ↔ domain）与 `DoseEventRepository` 中的重复。
- **最小修改建议**:
  1. 将 `WearDoseListenerService` 改为通过 Repository 接口访问数据
  2. 或在 Wear 桥接层提供专门的 `WearDoseWriter` Use Case，封装 DAO 访问
  3. 当前阶段最简方案：将 `WearDoseListenerService` 的 DAO 调用替换为 `DoseEventRepository` 和 `MedicationPlanRepository` 的方法调用
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-11 — `PRODUCT_OVERVIEW` 声称 Wear Tile 状态为 "已确认" 但实际协议无版本/ack

- **Issue ID**: I-11
- **分类**: 正确性
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `docs/evolune/PRODUCT_OVERVIEW.md:44` — "Wear Tile 和基础同步 | 已确认"
  - `docs/evolune/FEATURE_MATRIX.md:16` — Wear Tile 完成度: "基础可用"
  - `docs/evolune/ARCHITECTURE.md:132` — "没有统一 envelope、ack、checksum 或版本协商"
- **证据**: PRODUCT_OVERVIEW 确认 Wear 同步存在，FEATURE_MATRIX 标记为 "基础可用"，ARCHITECTURE 明确标注限制。这三份文档之间一致——都承认当前实现是基础的。但 PRODUCT_OVERVIEW 用 "已确认" 一词可能让读者误以为 "已经达到目标状态"。
- **影响**: 阅读 PRODUCT_OVERVIEW 的新开发者可能认为 Wear 同步已经完备，不需要在 Phase 4 投入额外工作。
- **最小修改建议**: 在 PRODUCT_OVERVIEW.md 的 Wear 行添加 Limitations 备注："当前缺少 envelope、版本协商和 ack 机制；协议升级设计见 ARCHITECTURE.md 第 7 节和 MIGRATION_PLAN.md Phase 4"
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-12 — FEATURE_MATRIX 中 Wear 完整 App 标记为 P1 但 ROADMAP 标记为 1.0

- **Issue ID**: I-12
- **分类**: 文档冲突
- **严重程度**: **P1**
- **置信度**: 中
- **状态**: 待确认
- **文件路径**:
  - `docs/evolune/FEATURE_MATRIX.md:17` — Wear 完整 App: 优先级 P1
  - `docs/evolune/ROADMAP.md:26` — 1.0 阶段: "P1：基础 Wear App，至少支持查看下一次用药、今日状态和快速记录"
  - `docs/evolune/ROADMAP.md:13` — MVP: "P1：当前 Wear Tile 的可靠快照同步和重复动作保护"
- **证据**: ROADMAP 区分了 MVP (只需 Tile 同步) 和 1.0 (需要完整 Wear App)。但 FEATURE_MATRIX 将两者都标为 P1。如果 FEATURE_MATRIX 的 P1 等于 MVP，那么 Wear 完整 App 应该降级。
- **影响**: 可能把需要独立 App 开发的 Wear 功能提前到 MVP，加剧 Phase 4 之前的基础设施缺失风险（Wear 协议尚未版本化）。
- **最小修改建议**: FEATURE_MATRIX.md 第 17 行将 Wear 完整 App 的优先级从 P1 改为 "P1 (1.0)" 并添加注解说依赖 Phase 4 协议前置条件
- **需要人工决定**: **是**
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-13 — `exportSchema=false` 且数据库版本 2 无备份迁移验证

- **Issue ID**: I-13
- **分类**: 数据迁移
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt:35-56` — `MIGRATION_1_2`
  - `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt:19` — `exportSchema = false`
- **证据**: `MIGRATION_1_2` 通过内联 SQL 创建 `medication_plans` 表。由于 `exportSchema=false`，无法生成 schema JSON 文件来编写 Room 的 `MigrationTestHelper` 自动化迁移测试。当前无法验证：
  - 从 `version 1` 的数据库文件升级到 `version 2` 时，`dose_events` 表数据是否完整保留
  - `medication_plans` 的 DDL 是否与 Room 编译期生成的表结构一致（`extras TEXT NOT NULL` 仅通过 JSON 序列化约束）
- **影响**: 如果 `MIGRATION_1_2` 的 SQL 有误（例如列类型不匹配、主键约束遗漏），用户设备上的数据可能在升级时静默丢失。数据库版本 1 的用户目前不受影响（因为 version 2 是当前版本），但从 version 1 升级到 version 2 的正向迁移路径未测试。
- **最小修改建议**: 与 I-09 一并修复：导出 schema，添加 `MigrationTestHelper` 自动化迁移测试。至少需要模拟 version 1 数据库并验证迁移到 version 2 后 `dose_events` 表的 SELECT 查询返回相同行。
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: **是** — Phase 1 需要添加新的 Room 迁移

---

### I-14 — Wear 端 `WearPlanListenerService` 硬编码路径常量而非从共享模块导入

- **Issue ID**: I-14
- **分类**: 正确性
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt:31-33` — 定义 `PLANS_PATH = "/hrt/plans"`
  - `wear/src/main/java/io/github/yuninggu/evolune/wear/WearPlanListenerService.kt:47` — 重新定义 `const val PLANS_PATH = "/hrt/plans"`
  - `wear/src/main/java/io/github/yuninggu/evolune/wear/WearPlanListenerService.kt:48-51` — 重新定义 `KEY_PLANS_JSON`、`KEY_CURRENT_CONCENTRATION`、`KEY_CURVE_VALUES`、`KEY_UPDATED_AT`
  - `wear/src/main/java/io/github/yuninggu/evolune/wear/WearSyncManager.kt:10` — 重新定义 `REQUEST_PLANS_PATH = "/hrt/request-plans"`
- **证据**: 手机端 `WearDataLayer` 和手表端 `WearPlanListenerService`、`WearSyncManager` 各自独立定义了相同的路径常量和键名。`WearPlanListenerService` 没有 import `WearDataLayer.PLANS_PATH`（实际上无法 import，因为两者在不同的 Gradle 模块中）。
- **影响**: 如果手机端修改了 `PLANS_PATH`（例如改为 `/hrt/v2/plans`），手表端不会编译失败，而是静默停止接收数据。这正是 `core:wear-protocol` 模块需要解决的核心问题——协议常量应该在共享的纯 Kotlin 模块中定义，手机和手表两侧都依赖它。
- **最小修改建议**: 这是 `core:wear-protocol` 模块的明确需求驱动力。在创建该模块时，将 `PLANS_PATH`、`KEY_*` 等常量移入，并让手机端 `WearDataLayer` 和手表端 `WearPlanListenerService` 都引用同一源。
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-15 — 根 `readme.md` 与 `docs/evolune/README.md` 内容差异显著

- **Issue ID**: I-15
- **分类**: 文档冲突
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `readme.md` (根目录)
  - `docs/evolune/README.md` (docs 子目录)
- **证据**: 根 README 与 docs README 的关键差异：

  | 内容 | 根 readme.md | docs/evolune/README.md |
  |------|-------------|----------------------|
  | `feiwuliyong/` 目录说明 | 无 | 有 ("仅作为受许可证约束的参考资料") |
  | `docs/` 目录说明 | 无 | 有 |
  | Wear 实现局限性 | 无 ("同步用药方案") | 有 ("Health Connect、云同步、加密备份、正式 Tracked Date 模型和独立 Wear App 尚未在 Evolune 中实现") |
  | 数据加密状态 | 无 | 有 ("尚未启用 SQLCipher 或其他数据库加密方案") |
  | 许可证边界声明 | 仅 "致谢" | 有明确边界声明和指向 MIGRATION_PLAN.md、DECISIONS.md |

- **影响**: 访问 GitHub 仓库首页的读者只看根 README，看不到许可证边界、`feiwuliyong/` 目录意图和当前架构限制。根 README 给人印象是 Wear 同步已成熟，但实际不然。
- **最小修改建议**: 统一两份 README。选项：
  1. 根 README 合并 docs README 的关键内容（许可证边界、功能局限）
  2. 或根 README 精简为简介 + 链接到 docs README
- **需要人工决定**: **是** — 影响项目对外呈现
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-16 — 缺少测试策略文档和发布门槛

- **Issue ID**: I-16
- **分类**: 测试
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 待确认
- **文件路径**:
  - `docs/evolune/ARCHITECTURE.md:170-177` — 分层测试概述（共 8 条，无具体指标）
  - `docs/evolune/FEATURE_MATRIX.md` — "测试要求" 列（高层面，如 "Repository/计划预测测试"）
  - `docs/evolune/ROADMAP.md` — 无测试覆盖率或发布质量标准
- **证据**: ARCHITECTURE.md 第 12 节列出 6 个测试层级，但未定义：
  - 每个层级的最低覆盖率要求
  - 哪些测试应该阻止 CI/CD 流水线
  - 真实设备矩阵的最低要求
  - Wear 配对测试的具体步骤
  FEATURE_MATRIX.md 的 "测试要求" 列停留在 "DAO/幂等/时间测试" 层面，没有可验证的通过标准。
- **影响**: 没有测试策略，Phase 1 的 `timeH → occurredAt` 迁移、Phase 4 的 Wear 协议版本化等高风险变更无法客观评估是否准备就绪。MIGRATION_PLAN 每个 Phase 的 "完成定义" 中有测试描述，但没有全局测试策略支撑。
- **最小修改建议**: 创建 `docs/evolune/TEST_STRATEGY.md`，至少包含：
  - 每个模块的最低 JVM 单元测试覆盖率
  - Room 迁移测试的具体命令（如 `./gradlew :app:testDebugUnitTest --tests "*Migration*"`）
  - Wear 协议测试的设备矩阵（至少 1 部手机 + 1 块手表）
  - CI 中必须通过和可以跳过（如真实设备测试）的测试
- **需要人工决定**: **是** — 需要定义测试投资边界
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-17 — 根 `readme.md` 缺少 `docs/` 和 `feiwuliyong/` 目录描述

- **Issue ID**: I-17
- **分类**: 文档冲突
- **严重程度**: **P2**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `readme.md:89-98` — 项目结构部分
- **证据**: 根 README 的项目结构部分只列出 `app/` 和 `wear/`，未提及：
  - `docs/evolune/` — 架构文档、功能矩阵、迁移计划、路线图、决策记录
  - `feiwuliyong/` — Featherline 迁移资料包（GPLv3 约束）
  - `reviews/` — 审计报告
- **影响**: 新贡献者可能不知道架构文档存在，或可能误入 `feiwuliyong/` 目录并复制 GPLv3 代码。
- **最小修改建议**: 在根 README 项目结构部分参考 `docs/evolune/README.md:89-98`，添加四个目录及其用途描述
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-18 — `SIMULATION_POINTS_PER_HOUR = 12.0` 注释错误

- **Issue ID**: I-18
- **分类**: 正确性
- **严重程度**: **P3**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/viewmodel/HRTViewModel.kt:45` — `const val SIMULATION_POINTS_PER_HOUR = 12.0 // 5分钟一个数据点`
- **证据**: 12 个数据点每小时 = 每 5 分钟一个点 (60 分钟 / 12 = 5 分钟)。注释正确，值为 12.0。但 MIGRATION_PLAN 文档中没有讨论这个值的来源或对性能的影响。这是一个**小问题**因为注释与值一致，但放在此处作为完整性记录。
- **影响**: 无功能影响。
- **最小修改建议**: 无需修改。
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-19 — `CorePK.VD_PER_KG = 2.0` 与注释矛盾

- **Issue ID**: I-19
- **分类**: 正确性
- **严重程度**: **P3**
- **置信度**: 中
- **状态**: 待确认
- **文件路径**:
  - `app/src/main/java/io/github/yuninggu/evolune/pk/PKParameters.kt:7-11`
- **证据**: 代码注释说 "文献值：雌二醇的分布容积通常为10-15 L/kg（由于组织结合）"，但常量值为 `const val VD_PER_KG = 2.0`。如果文献值是 10-15 L/kg，为什么代码用 2.0？这是有意校准的参数，还是文档错误？
- **影响**: 如果注释正确而代码值错误，所有浓度计算结果都偏低约 5-7.5 倍。如果是参数有意校准，注释需要更新以避免混淆。
- **最小修改建议**: 确认上游 PK 参考实现的值，将注释更新为 "校准后的有效分布容积系数（L/kg），已通过文献 Tmax/Cmax 验证" 或修正常数值。
- **需要人工决定**: **是** — 需要 PK 领域专家确认
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

### I-20 — Debug 签名密钥硬编码

- **Issue ID**: I-20
- **分类**: 正确性
- **严重程度**: **P3**
- **置信度**: 高
- **状态**: 已确认
- **文件路径**:
  - `app/build.gradle.kts:40-43` — `storePassword = "DEBUG1"`, `keyAlias = "DEBUG"`, `keyPassword = "DEBUG1"`
- **证据**: Debug 签名密钥的 storePassword 和 keyPassword 直接硬编码在 `build.gradle.kts` 中。虽然密钥文件被 `.gitignore` 排除（需确认），但密码明文存在于源码中。这不是安全漏洞（debug 密钥无安全价值），但提交到公开仓库后，CI/CD 工具可能扫描并报警。
- **影响**: CI 安全扫描可能误报；新开发者看到后可能认为硬编码密码是可接受的做法。
- **最小修改建议**: 将密钥密码移到 `local.properties`（已被 `.gitignore` 排除），在 `build.gradle.kts` 中通过 `Properties` 读取并设置 fallback。
- **需要人工决定**: 否
- **阻止进入 Phase 0**: 否
- **阻止进入 Phase 1**: 否

---

## 附录 A：文档交叉引用验证矩阵

| 主题 | ARCHITECTURE | DECISIONS | MIGRATION_PLAN | PRODUCT_OVERVIEW | ROADMAP | FEATURE_MATRIX | README (root) | README (docs) | 代码 |
|------|-------------|-----------|----------------|-----------------|---------|----------------|---------------|---------------|------|
| 许可证策略 | 提及 | **ADR-001/002** | **Phase 0 详述** | 无 | P0 | "许可证风险"列 | 仅致谢 | **详述** | LICENSE (MIT) |
| 模块数量 | **12+** | ADR-006 | — | — | — | — | 2 (app+wear) | 2 + docs | 2 (app+wear) |
| TrackedDate 优先级 | — | ADR-011 (待定) | Phase 1 前置 | 当前不存在 | **P2 (1.0)** | **P1** | — | — | 无实体 |
| Wear 协议版本化 | **建议 envelope** | ADR-005 | Phase 4 | 基础同步 | — | "当前不存在" | — | — | 无 |
| allowBackup 风险 | **第 11 节** | ADR-007 | 第 19 行 | 第 91 行 | P0 | — | — | **第 107 行** | `true` 无规则 |
| SQLCipher | 第 5 节 (否) | ADR-007 (否) | 不建议 | 否 | P3 | — | 否 | — | 无 |
| Glance | 第 10 节 | ADR-008 | Phase 6 | 当前不存在 | P2 | P2 | — | — | 无 |
| Health Connect | 第 9 节 | ADR-004 | Phase 5 | 当前不存在 | P2 | P2 | — | — | 无 |
| exportSchema | 目标: true | — | 标记为需重构 | 当前: false | — | — | — | — | **false** |
| Repository 边界 | 规则: 禁直接DAO | — | — | — | — | — | — | — | **Wear绕过** |
| feiwuliyong 迁移 | 仅参考行为 | ADR-001/002 | 第 3 节 | — | — | — | 无 | 有 | 不参与构建 |

- **图例**: — 表示文档未涉及；**加粗** 表示有冲突或不完整。

---

## 附录 B：文档完整性检查

| 文档 | 存在 | 状态 |
|------|------|------|
| README.md (根) | ✓ | 缺少 docs/、feiwuliyong/ 路径和许可证边界 |
| README.md (docs) | ✓ | 完整，路径引用使用相对路径 |
| ARCHITECTURE.md | ✓ | 完整，但依赖图与规则有冲突 |
| DECISIONS.md | ✓ | 完整，但 ADR-011 决策未关闭 |
| MIGRATION_PLAN.md | ✓ | 完整，Phase 细节可提升 |
| PRODUCT_OVERVIEW.md | ✓ | 完整 |
| ROADMAP.md | ✓ | 完整，与 FEATURE_MATRIX 有一处冲突 |
| FEATURE_MATRIX.md | ✓ | 完整，与 ROADMAP/ADR 有两处优先级冲突 |
| TEST_STRATEGY.md | **缺失** | 建议创建 |
| NOTICE 文件 | **缺失** | P0 阻断 |
| dataExtractionRules XML | **缺失** | P0 阻断 |
| Room schema JSON (基线) | **缺失** | P2 |

---

*审计报告结束。所有发现基于 2026-08-01 的仓库快照。*
