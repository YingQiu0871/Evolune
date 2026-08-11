# Phase 1 Batch 7C Independent Review

Date: 2026-08-11
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-batch7c`
Branch: `phase1/batch7c-domain-pk`

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 2**（一项报告计数口径 + 一项继承 P2；无阻断项）
- Adapter architecture: **PASS**
- Selection parity: **PASS**
- Filtering parity: **PASS**
- Ordering parity: **PASS**
- Structural parity: **PASS**
- Numerical parity: **PASS**
- **Max observed delta: 0.0**（独立复验，断言 0.0）
- PK engine integrity: **PASS**（零 diff）
- HRT cutover: **PASS**
- Widget cutover: **PASS**（仅投影替换）
- JSON export compatibility: **PASS**
- Batch6 bridge removal: **PASS**（职责转移完整）
- Duplicate production mapping: **ZERO**（PkDoseEvent( 唯一命中 = formal adapter）
- Schema integrity: **PASS**
- Forbidden boundaries: **PASS**
- **7C may be sealed: YES**
- **Batch 7 may close after sealing: YES**（7A/7B/7C 全部封存后）
- **Batch 8 may begin after sealing/integration: YES**（仅 7C 封存 + Batch 7 整合/最终门后；本 review 不授权直接开始）
- **Release: forbidden**

## Git / baseline verdict

- 分支 `phase1/batch7c-domain-pk`（= batch7-design HEAD `44d4dfd` = 7B merge）✓
- **7B sealed 经 git graph 独立确认**：tag `phase-1-batch-7b` → `a2dd225`（7B review）→ `c6fc7bc`（7B impl）→ merge `44d4dfd` ✓ 报告 §1 SHAs 与 graph 一致
- 暂存区空；change set = 8 modified/deleted + 6 new（与报告一致）；无 APK/DB/keystore/真实数据（keystore 临时处理已披露并移除）✓
- 无 Batch 8 / Widget 产品功能 / Wear 扩展 / Custom / Plan JSON 实现 ✓

## Adapter verdict

`DomainDoseEventToPkAdapter.kt`（38 行）：

- 纯 Kotlin object：只 import Domain、`LegacyTimeAdapter`、PK 模型——无 Android/Room/Repository/DAO/Entity/Context/clock/timezone/random/simulation ✓
- 单事件映射：id 不变、route/ester 直传（ADR-015 共享枚举）、`occurredAt → LegacyTimeAdapter.instantToTimeH`（唯一时间源）、doseMG 原值、extras mapKeys 六键穷举 ✓
- list = `events.map`（保序、无 filter/sort/dedup/canonicalize/mutate）✓
- 不可表示 Instant → 显式 `IllegalArgumentException`（与 WidgetWork 旧 `toWidgetPkTimeH` 异常行为一致；设计 §10 "typed failure or explicit compatibility exception"）✓
- **无 policy leakage**（selection/filter/order 全在调用方）✓

## Selection verdict

- 30 天窗口 / 20 事件 fallback：`RoomDoseEventRepository.getEventsForPk` 零 diff ✓
- HRT 在捕获的 now 请求 Repository selection；Widget 同理——不变 ✓
- adapter 无查询/窗口/计数/计划选择 ✓

## Filtering verdict

- HRT：`filter { RECORDED && route != ANTIANDROGEN }` **在 adapt 前**（diff 证实——旧实现是 project 后 filter antiandrogen；新实现先 filter 再 adapt——**结果集合完全相同**，等价重构）✓
- Widget：antiandrogen + future 过滤在 loader 内、adapt 前 ✓
- Predictor：enabled/schedule/future/conflict-window 过滤保留于自身 ✓
- adapter 无任何 status/source/route/ester/date 过滤 ✓

## Ordering verdict

- Repository 分支序（ascending/descending）与 fallback 序零变化 ✓
- HRT historical+predicted append、Widget 序、Predictor 排序不变 ✓
- adapter 无 sortedBy/tie-breaker/set 转换 ✓
- **same timestamp + distinct IDs** 保序测试（AdapterTest `preserves empty input input order and duplicate timestamps`）✓

## Mapping verdict

- 逐字段：id→UUID 同值；route/ester→直传（无 ordinal/name heuristic）；doseMG→同 Double（无转换/无 clamp/无 abs）；occurredAt→LegacyTimeAdapter（无 unit 转换、无 silent rounding 超既有行为、无时区重建）✓
- extras 六键精确 1:1（无标准化/无单位转换/无新键/无 custom hack）✓
- 旧 duplicate 映射清除：ExtraKeyMapper 的 `toPkExtraKey` 移除（移至 core.adapter 共享）、Widget 私有对移除、Predictor 私有对移除 ✓

## Structural parity verdict

- ParityTest：4 corpus（representative 7 routes / same-timestamp repeated / long 30 / sparse）——`assertEquals(oldProjection, newProjection)` **PkDoseEvent data class 全字段 + list 顺序** ✓
- legacyProjection oracle 为独立 inline 映射（非复用 adapter 代码）——真对照 ✓
- AdapterTest：全字段（含负时间、零剂量、Domain-only 元数据省略+输入不变）、空列表、顺序、重复时间戳、全 route×ester 组合、边缘 dose（-0.25）、毫秒精确、Instant.MAX 显式失败 ✓
- **无 assertNotNull/noException 空泛断言** ✓

## Numerical parity verdict

- 独立复验：parity 测试实际运行 PASS——每 corpus：SimulationEngine（相同 bodyWeight 60/start-end range/numberOfSteps 257）→ concPGmL **逐样本 ≤1e-6** + auc ≤1e-6 + **maxObservedDelta 断言 == 0.0（绝对）** ✓
- delta=0.0 合理（old/new projection 全等 → 确定性引擎 → 输出全等）；测试仍独立跑引擎比较（防未来引擎/参数改动）✓
- 未放宽 tolerance、未改 PK engine、未 round 输出 ✓
- corpus 覆盖代表场景（oral/sublingual/injection/gel/patch apply-remove/antiandrogen/mixed/repeated/same-ts/long/sparse）——**无不存在 route** ✓

## HRT cutover verdict

- 仅替换：`Batch6HrtPkProjection`/`jsonBridge` → formal adapter/export service（diff 24 行，无无关修改）✓
- selection/filter/simulation range/body weight/future merge/state/UI 不变 ✓
- 7B 既有 P2（failure folding/catch width）**未搭车修**（diff 无相关变化）——报告 §18 如实 ✓

## Widget cutover verdict

- 仅替换 `toWidgetPkEvent` → formal adapter（diff 23 行）✓
- 无 protocol/rendering/quick action/side effects/refresh/body-weight/simulation-range/current-time/Material You/transparency 变化 ✓

## Predictor verdict

- 仅共享 `toPkExtraKey`（删除私有映射 + import 共享）——schedule/DST/selection/sort/conflict-filter/synthetic IDs 零变化（diff 11 行仅映射）✓
- Predictor 未被并入 formal adapter（plan→future 边界保持）✓

## JSON export verdict

`MahiroJsonV1ExportService`（32 行）：

- 仅复用 7A sealed 边界（`MahiroV1DoseEventAdapter.fromDomain` → `MahiroV1Codec.encode`）✓
- 无新 JSON schema/字段/enum 拼写/事件顺序/extras 语义/metadata 变化；clock 注入（VM 复用既有 clock）✓
- 不可表示 → 显式 IllegalArgumentException（无 silent 近似——旧 bridge 直接除法会产生近似 timeH；生产 Domain 事件 always representable，无实际触发）✓
- 无 Repository/DB 访问 ✓
- ExportServiceTest：legacy oracle（`MahiroJsonFormat.generateExport`）vs formal export——jsonObject 语义比较（固定 clock、顺序、字段）+ 不可表示失败 ✓

## Bridge removal verdict

- `Batch6DoseEventCompatibility.kt` 删除（136 行：import writer/HRT PK projection/export projection/duplicate maps）——**删除前职责全部有正式 owner**：import→7A+7B、export→ExportService、PK→formal adapter、ExtraKey→共享映射 ✓
- `Batch6DoseEventCompatibilityTest` 删除（244 行）——**contract 未消失**：import/export/PK projection/reachability 由 7A/7B/7C 新测试覆盖（报告 §13 职责转移表 + 本审阅逐项确认）✓
- **reachability 测试真实扫描**（BoundaryTest walk 全生产目录 + HRT/Widget 无 `PkDoseEvent(` 断言 + 文件不存在断言）——非仅"类不存在" ✓

## Production reachability verdict

- 独立 grep：`Batch6HrtPkProjection/Batch6MahiroJsonBridge/toWidgetPkEvent/toWidgetPkExtraKey` 生产**零命中** ✓
- `PkDoseEvent(` 生产唯一命中 = **formal adapter 自身**——persisted Domain→PK 生产只剩一套 ✓✓
- `MahiroJsonFormat` production callers = 0（compat oracle 保留，设计允许）✓

## Test quality verdict

- 断言全部为精确字段/数值比较（无 assertNotNull 空泛）✓
- parity 为结构+数值双 oracle（非仅终值）✓
- fixtures 全合成（UUID(0L, n)/2026 时间戳）✓；无真实数据 ✓
- 无 @Ignore ✓

## Connected-test evidence verdict

- 报告明确：connected NOT RUN、androidTest compile only——**如实披露** ✓
- 7C 核心（adapter/parity/export）= pure JVM；Widget/HRT cutover 为映射替换；无 Room/schema/permission 变化——**JVM + androidTest 编译 + 既有回归足够**；connected 未跑 = 可接受证据边界（非 P1）✓

## Schema verdict

- 独立计算：schema3 blob SHA-256 = **044013C0...** ✓（与报告/锁定值一致）；schema2 此前批次已验证（本批零 diff）✓
- KSP 重跑无 diff；越界 diff（schemas/migration/core.model/core.dataapi/data.repository/pk/Gradle）**全空** ✓
- Room v3 internal/unreleasable ✓

## Forbidden-boundary verdict

- 无 Widget 产品功能（Material You/transparency）、Wear 扩展、Custom medication、Plan JSON、Health Connect、cloud、onboarding、Batch 8、release ✓
- WidgetWork diff 仅投影替换（允许范围）✓

## Report-accuracy verdict

- baseline SHAs/graph 一致 ✓；scope/old-new paths/selection/filter/order ownership/mappings/delta=0.0/consumer cutover/bridge removal/JSON export/schema/deferred 全部准确 ✓
- connected 免责声明诚实 ✓；keystore 处理披露 ✓；7B P2 未搭车修 ✓
- **P2-F1**：报告 §14 "8 suites / 43 tests" 与我的独立 focused 运行（**8 suites / 51**）不一致——**全量 49/406 与报告一致**（权威）；43 疑为部分子集口径——报告 focused 计数表述模糊（不阻止；建议封存时注明口径或改 51）

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 报告 focused 测试计数口径（8 suites 43 vs 实测 51）**
- Severity: P2
- File: `PHASE_1_BATCH_7C_REPORT.md` §14
- Problem: 报告的 "Batch 7C focused ... 8 suites 43" 与独立运行（8 suites 51：AdapterTest 5 + ParityTest 1 + BoundaryTest 2 + ExportServiceTest 2 + HRTViewModelTest 14 + WidgetWorkTest 9 + ExtraKeyMapperTest 7 + MedicationPlanPredictorTest 11）不符
- Evidence: 独立 XML 逐 suite 累加 = 51；**全量 49 suites / 406 与报告一致**
- Impact: 仅 focused 行计数口径模糊；全量权威计数正确
- Required fix: 封存时注明 focused 为部分子集或更新为 51
- Blocks sealing? NO

**F2 — 继承 P2（非本批新增）**
- Severity: P2
- File: 报告 §18
- Problem: 7B 三项 P2（failure folding / catch width / 设计组）如实继承且未搭车修
- Blocks sealing? NO

## Independent validation

- Git: branch/HEAD/graph/tag（`phase-1-batch-7b`→a2dd225 确认）/status/diff/--check/ls-files/stash 全部核实
- 完整读取: DomainDoseEventToPkAdapter、MahiroJsonV1ExportService、4 个新测试、ExtraKeyMapper/Predictor/HRTViewModel/WidgetWork diffs、Batch6 bridge 删除、7C 报告（334 行）
- 独立运行:
  - 7C focused（8 suites）→ **51 tests / 0 failures / 0 skipped**（XML 逐 suite：2+5+2+1+7+11+14+9）
  - 7A 回归（Codec 12 + Adapter 13）→ **25/25**
  - 7B import（12）+ HRT → 计入 focused
  - legacy `MahiroJsonFormatTest` → **14/14**
  - `:app:testDebugUnitTest --rerun-tasks` → **49 suites / 406 / 0 / 0 / 0**（XML 累加）
  - PK（`pk.*`）→ **5 suites / 49 / 0**
  - `:app:assembleDebug` PASS；`:app:lintDebug` PASS（0 errors）；`:app:kspDebugKotlin --rerun-tasks` PASS；`:app:compileDebugAndroidTestKotlin --rerun-tasks` PASS；`git diff --check` PASS
- Schema: schema3 blob SHA-256 独立计算 = 044013C0...（匹配）
- 越界 diff: schemas/migration/core.model/core.dataapi/data.repository/pk/Gradle 全空
- 重复路径: grep `PkDoseEvent(` 生产唯一命中 = formal adapter；Batch6 三符号零命中
- 未运行: connected tests（报告如实声明 compile only；7C 核心 pure JVM——可接受证据边界）

## Final decision

**APPROVE WITH P2**

批准标准逐项：P0=0 ✓；P1=0 ✓；纯 adapter 边界 PASS ✓；selection/filtering/ordering parity PASS ✓；structural parity PASS ✓；**numerical parity ≤1e-6（实测 delta=0.0）** ✓；PK algorithm 未变 ✓；HRT cutover PASS ✓；Widget cutover 仅 adapter PASS ✓；JSON export compatibility PASS ✓；Batch6 bridge production reachability zero ✓；duplicate Domain→PK production path zero ✓；schema 未变 ✓；无 forbidden features ✓。

- **Batch 7C may be sealed: YES**
- **Batch 7 may close after 7C sealing: YES**（7A/7B/7C 均已封存；关闭需 Batch 7 整合/最终门确认）
- **Batch 8 may begin only after 7C sealing + Batch 7 integration/final gate: YES**
- 本 review 不授权直接开始 Batch 8；Widget/Wear 扩展/Custom/Plan JSON/Health Connect/cloud/onboarding/release 继续禁止；Room v3 仍 internal/unreleasable
