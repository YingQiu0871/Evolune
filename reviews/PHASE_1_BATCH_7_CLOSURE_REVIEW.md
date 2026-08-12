# Phase 1 Batch 7 Final Closure Independent Review

Date: 2026-08-11
Reviewer: DeepSeek (independent read-only final closure review)
Worktree: `D:\Evolune-phase1`
Branch: `phase1/batch7-design`
Integrated HEAD: `064d164d432c6b6c598b0c42dd030cc20a166384`

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 8**（8 项全部为非阻断 surviving caveat / 工程优化 / 测试基建维护）
- Ancestry: **PASS** / JSON: **PASS** / Import: **PASS** / Export: **PASS** / Repository semantics: **PASS**
- PK architecture: **PASS** / Structural parity: **PASS** / Numerical parity: **PASS**
- Max concentration delta: **0.0** / AUC delta: **0.0**
- Production legacy reachability: **ZERO** / Duplicate Domain→PK mapping: **ZERO**
- Remediation integration: **PASS**（production diff ZERO）
- API33 final connected: **PASS**（独立复现 115/0/0/2）/ API35 final connected: **PASS**（独立复现 115/0/0/2）
- Foldable regression: **PASS**（24/24 XML） / Full App JVM: **PASS**（49/406） / Wear: **PASS**（1/1）
- Build gates: **PASS** / 16KB alignment: **PASS** / Schema: **PASS** / Release boundary: **PASS**
- Deferred-feature boundary: **PASS**
- **Batch 7 may be closed: YES**
- **Batch 8 DESIGN may begin after final tag: YES**
- **Batch 8 implementation: NO** / **Release: FORBIDDEN** / **Room v3 release: FORBIDDEN**

## Git / ancestry verdict

- 分支 `phase1/batch7-design`；HEAD = `064d164d...` ✓
- 独立 `merge-base --is-ancestor` 逐一确认全部 5 个 tag 为 HEAD 祖先（exit 0）：`phase-1-batch-7-design-v1`、`-7a`、`-7b`、`-7c`、`-7-closure-remediation` ✓
- 唯一未跟踪 = closure report；暂存空；`git diff --check` 通过；无 keystore/artifact ✓
- closure report §3 SHA 表与 git graph 一致 ✓

## Final architecture verdict

- JSON import 单路径：`JSON → DTO → Codec → DomainAdapter → ImportService → DoseEventRepository contract → Room` ✓
- PK 单路径：`Domain DoseEvent → selection/filter/order → DomainDoseEventToPkAdapter → unchanged PK engine` ✓
- `MedicationPlanPredictor` 为独立 plan→future 路径（非 duplicate persisted 投影）✓
- 无 DAO/Entity bypass、无第二 import 语义、无第二 persisted→PK 映射、Room = source of truth ✓

## JSON verdict

- 锁定语义保持（§6）：valid UUID 保留；missing/blank/malformed → random；numeric id → invalid/skip；LegacyTimeAdapter 唯一时间源；zoneId/localDate 不重建；不可表示 export 显式失败；六 extras 精确；unknown fields 按设计 ✓
- 无 MedicationPlan JSON ✓；独立复验 Codec 12 + DomainAdapter 13 = 25 ✓

## Import / Repository verdict

- stable valid ID same content → idempotent；same ID different content → conflict；conflict/idempotent continue；storage failure → stop + accurate partial（import 12 复验）✓
- ImportService 无第二套 conflict logic；Random-ID replay caveat 诚实保留 ✓

## Export verdict

- `MahiroJsonV1ExportService` 仅复用 7A sealed 边界；保留 v1 字段/顺序；不可表示时间显式失败；`MahiroJsonFormat` 零 production caller（compat oracle）✓；独立复验 Export 2 ✓

## Domain-to-PK verdict

- `DomainDoseEventToPkAdapter` 纯/deterministic/无 Repository/Room/Android/clock/timezone/filter/selection/sort/random；one→one；list 保序（Adapter 5 复验）✓；无 policy leakage ✓

## Structural / numerical parity verdict

- 独立复验 Parity：结构全等 + SimulationEngine 逐样本比较；**max concentration delta = 0.0（断言绝对 0.0）**、**AUC delta = 0.0**——均 ≤1e-6 ✓
- PK engine/source 未修改（`pk` 包零 diff）✓

## Production reachability verdict

独立 grep 生产 source：`Batch6HrtPkProjection` / `Batch6MahiroJsonBridge` / `toWidgetPkEvent` / `toWidgetPkExtraKey` **零命中**；`PkDoseEvent(` 唯一命中 = `core/adapter/DomainDoseEventToPkAdapter.kt:17`；`MahiroJsonFormat` 仅定义处（零 caller）✓；duplicate persisted→PK **ZERO** ✓

## Batch6 retirement verdict

- 删除 bridge 职责全部有正式 owner（import→7A+7B、export→ExportService、PK→adapter、ExtraKey→共享映射）✓；无 orphaned responsibility ✓

## Remediation verdict

- `git show 605ba55d`：仅改 `DoseEventProductionCutoverTest.kt`（+29）+ report——**production diff ZERO**（无 app/src/main、wear、schema、Gradle、Manifest）✓
- timeout 10_000L 不变；无 retry/sleep/delay；assertions 保留；lifecycle 同步正确 ✓

## Connected-device verdict

- **模拟器重启导致端口身份漂移**（历史 final gate 时 5558=API33；重启后 5558 变 API37 Fold、API33 现为 5554）——动态核实后独立复现：
  - API33（`emulator-5554` Evolune_API33_Migration SDK33/gphone64/无 watch）：全量 **115/0/0/2 BUILD SUCCESSFUL** ✓
  - API35（`emulator-5560` Pixel_7 SDK35）：全量 **115/0/0/2 BUILD SUCCESSFUL** ✓
- 报告 §15 的 5558/5560 为其运行时正确身份；漂移为环境事实非缺陷（P2 记录）；2 skipped = foldable-only on phone ✓

## Foldable/UI regression verdict

- build XML：`FoldableNavigationLayoutTest` **24/24**（0/0/0）——与报告一致 ✓；为既有 regression 保留证明，非重新授权 UI ✓

## JVM/Wear verdict

- 独立复验：Full App JVM **49/406/0/0/0**；Wear 1/1；PK 49；7A 25、7B import 12、7C adapter+parity+boundary+export ✓
- 报告 §16 focused 分组（JSON 5/53、import 3/29、PK 5/49）口径清楚（与全量 overlap 合理）✓

## Build/alignment verdict

- ksp/assemble/compile androidTest/wear assemble/lint 全 PASS；lint 0 errors（83 warnings/1 hint）✓
- 16KB alignment：此前独立 zipalign `-c -P 16 -v 4` Verification successful ✓

## Schema verdict

- 独立计算 git blob SHA-256：schema3 = **044013C0...**（匹配）；schema2 = B8DA54ED... ✓；无 migration/AppDatabase/Entity/DAO/schema 变化 ✓

## Historical-evidence verdict

- §13 历史 API33 失败（115/1/0/2, TimeoutCancellationException, 10.163s）**保留未抹掉** ✓
- §14 diagnosis（TEST DEFECT/HIGH/P2）+ remediation（test-only）✓；§19 五项历史澄清如实保留（39 vs 42、API35 瞬态、7C 43 vs 51、UTP 117 vs 115）✓；未写成"从未出现 connected failure" ✓

## Surviving-risk verdict

Closure report §20 的 8 项 P2 独立逐项评估——**全部非阻断**：

1. Random-ID replay caveat（missing/blank/malformed → random 非幂等）——真实 JSON v1 语义 caveat，文档明确
2. Numeric-ID stricter（invalid/skip vs legacy random）——intentional formal behavior，compat caveat + stop condition
3. JSON v1 metadata projection limits——protocol 设计限制
4. ADR-015 Route/Ester ownership——transitional 依赖（既定决策）
5. HRT failure folding（Document/Storage 共用 StorageFailure）——presentation 优化项
6. ImportService broad RuntimeException catch——可收窄
7. Wear replay 不能 reverify plan_id——继承 Batch6（Batch8 protocol review 项）
8. Wrapper 39 vs 42——测试基建计数维护（非 product risk）

报告把 API35 瞬态与 7C 43/51 明确不计入当前 product P2（作为 historical evidence）——合理。**最终 P2 = 8 项，全部非阻断**。

## Release-boundary verdict

- §22 明确 "Room v3 remains internal and unreleasable"；无 real-user migration/production release/repair migration/legacy 删除/real DB validation/RC 授权 ✓；未写成 "Room v3 ready for release" ✓

## Closure-report accuracy verdict

`PHASE_1_BATCH_7_CLOSURE_REPORT.md`（355 行）逐节核实——与 sealed evidence / 集成代码 / 独立验证一致：ancestry/架构/JSON/import/export/PK/parity/reachability/历史与 remediation/connected/JVM/Wear/build/schema/P2/deferred/release 全部准确；无事实错误；focused/全量口径清楚 ✓

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 设备端口身份漂移（环境事实）**
- Severity: P2
- Source: 验证环境
- Problem: 模拟器重启后 API33 phone 从 5558 变 5554（5558 变 API37 Fold）；报告 §15 身份为其 final gate 运行时正确身份
- Evidence: 动态 getprop/pm 核实；重跑正确端口（5554/5560）均 115/0/0/2 BUILD SUCCESSFUL
- Impact: 无（证据经正确端口复现；漂移非缺陷）
- Blocks closure? NO

**F2 — Surviving P2 8 项（全部非阻断）**
- Severity: P2
- Source: closure report §20
- Problem: 8 项 surviving P2（random replay、numeric-ID、v1 投影、ADR-015、failure folding、broad catch、Wear plan_id、wrapper 计数）
- Evidence: 逐项独立评估，1-7 为 bounded caveat/优化、8 为测试基建维护；全部文档化
- Blocks closure? NO

## Independent validation

- Git: branch/HEAD/5-tag ancestry（is-ancestor exit 0 逐一）/status/diff/--check/stash ✓
- 完整读取: closure report（355 行）
- 生产 reachability grep: Batch6 三符号零命中、PkDoseEvent( 唯一=formal adapter、MahiroJsonFormat 定义处 ✓
- remediation diff: `git show 605ba55d` 仅测试+报告（production zero）✓
- Full App JVM: **49/406/0/0/0**（XML 累加）✓；PK 49；7A 25；7B import 12；7C parity 1+adapter 5；Export 2；Wear 1/1 ✓
- Core parity/JSON focused: 全绿（Parity delta 0.0）✓
- **API33 full（5554 动态核实为 API33 phone）: 115/0/0/2 BUILD SUCCESSFUL** ✓
- **API35 full（5560 动态核实为 API35 phone）: 115/0/0/2 BUILD SUCCESSFUL** ✓
- Foldable XML: 24/24 ✓
- Schema: schema3 blob SHA-256 = 044013C0...（独立计算）✓
- 设备动态核实: 5554=API33、5560=API35、5556=Wear、5558=API37 Fold（重启后漂移已处理）
- 未重跑: lint 全量（此前独立跑过 0 errors）、zipalign（此前独立 Verification successful）、ksp（JVM/assemble 链已含）——均此前独立验证

## Final decision

**Batch 7 may be closed.**

The closure report and this independent review may be sealed.

After sealing both documents, an annotated `phase-1-batch-7` tag may be created.

Only Batch 8 DESIGN may begin after that tag.

Batch 8 implementation remains unauthorized.

Release remains forbidden.

Room v3 remains internal and unreleasable.
