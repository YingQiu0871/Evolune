# V17-A-04 — Phase-A Hardening & Gate Closure（工作记录 + gate matrix）

> 状态：`PHASE A CANDIDATE CLOSED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-A / **A-04**（Phase A hardening；**不含 Phase B / Insights**）
> 起始 HEAD：`bad2ab3ae48cbf2ee3151947c5cde059c8980d83`（A-03-UI-R1 APPROVE，A-03 正式关闭）
> 依据（§0 要求逐字重读，不按任务书假设）：
> [`V17_ACCEPTANCE.md`](V17_ACCEPTANCE.md) §1（Phase A 验收表）、§0（G1–G7）、§8（v1.6 对照锚点）、§9（A mandatory backlog M1–M8）；
> [`V17_SPEC.md`](V17_SPEC.md)、[`V17_PLAN.md`](V17_PLAN.md)；A-01/A-02/A-03 证据文档现状。
> 配套：[`V17_A_01_HISTORICAL_PROJECTION.md`](V17_A_01_HISTORICAL_PROJECTION.md) · [`V17_A_02_HISTORY_READ_MODEL.md`](V17_A_02_HISTORY_READ_MODEL.md) · [`V17_A_03_HISTORY_UI.md`](V17_A_03_HISTORY_UI.md)

---

## 0. Acceptance 原文复核（A6 / A7 / A10）

本轮**先**重读最新版 acceptance，再定义工作内容。原文（`V17_ACCEPTANCE.md` §1）：

| # | acceptance 原文（判定列） |
|---|---|
| A6 | 撤销语义正确：撤销后行不存在于任何历史投影；长期历史**只**表达 `Recorded` / `No recorded intake`，不得虚构 `Skipped` / `Missed`——**Decision C** ｜ 判定：复用物理删除机制；补测试（**Phone delete + Wear latest-delete 各自的结果**）+ **文案审查** |
| A7 | 读路径无写入：打开 History 不产生任何 `dose_events`/`plans` 变更（Invariant 5） ｜ 判定：测试用**计数型 repository 替身**断言零写入 |
| A10 | fresh 验证 ｜ 判定：JVM 全量 + app instrumentation（**受影响面**）；`BUILD SUCCESSFUL` 原文 |

**与任务书描述的差异（以 acceptance 为准）**：

1. A6 的判定明确要求**两条撤销路径各自的结果**（Phone `delete` 与 Wear `latest-delete`），并要求**文案审查**作为验收的一部分；任务书的 Case A–D 只是其细化，本轮的 A6 关闭同时包含任务书 Case A–D **与** acceptance 的"两路径 + 文案审查"三重条件。
2. A7 的判定原文是"计数型 repository 替身"（测试替身即可）。任务书额外要求 instrumented/counting repositories；本轮**两者都做**（替身计数 + 真 Room 上的 counting decorator），因此强于 acceptance 最低要求。
3. A10 的判定原文是"JVM 全量 + app instrumentation（**受影响面**）"，并非某一固定测试清单；因此本轮建立**受影响面矩阵**并逐面给出 fresh 证据，`BUILD SUCCESSFUL` **原文**入证据。
4. 另注：acceptance §1 的 Phase A 门禁是 `APPROVE V1.7-A CANDIDATE IMPLEMENTATION`，§9 另有 **M1–M8 mandatory backlog**（"A 门禁前必须具备确定性覆盖"），故本 matrix 同时跟踪 A1–A10 与 M1–M8。

---

## 1. Gate matrix（Acceptance item / Current status / Existing evidence / Missing evidence or code / Planned closure）

### 1.1 A1–A10

| # | Acceptance item | Current status | Existing evidence | Missing evidence/code | Planned closure (A-04) |
|---|---|---|---|---|---|
| A1 | 历史投影来自既有权威事实，无新表/新事实列 | **COVERED** | `app/schemas/**` 无 diff（v3 = 2.json/3.json 未变）；A-01/A-02/A-03 三轮 `git diff --name-only` 均含 schema 0 改动 | — | 复审时以 `git diff --name-only bad2ab3..HEAD -- app/schemas` = 0 复核 |
| A2 | 复用共享派生层，无第二套匹配实现 | **COVERED** | `PureDependencyBoundaryTest`（2）；`MedicationOccurrencePresentation` 为唯一 matcher；`HistoryReadService`/UI 均无 matcher 引用（`HistoryPresentationWordingTest` 源码护栏） | — | 复审复核；无新改动 |
| A3 | 四阶段匹配语义不变 | **COVERED** | `MedicationOccurrencePresentationTest`（23）全绿且期望未被削弱 | — | fresh JVM 复核 |
| A4 | 日视图四类区分 + 孤儿按真实 source 呈现 | **COVERED** | `HistoricalProjectionIntakeTest`（9，含 only-MANUAL 可标 Manual）；A-03 UI 的 source label 映射 + `HistoryPresentationTest` | — | fresh JVM 复核 |
| A4b | Match provenance 可携带（exact / bounded inferred / legacy inferred / unmatched） | **COVERED** | `MedicationMatchProvenance` 四阶段 + `HistoricalProjectionProvenanceTest`（10） | — | fresh JVM 复核 |
| A5 | 跨日 legacy 呈现为 inferred 而非 exact | **COVERED** | `MedicationOccurrencePresentationTest` 跨日用例 + `HistoricalProjectionProvenanceTest` | — | fresh JVM 复核（A-04 另加 undo×cross-date 场景，见 §3 Case D） |
| A6 | 撤销语义正确（行不再出现在任何历史投影；不得虚构 Skipped/Missed） | **CLOSED（本轮）** | 既有：`WearAppUndoHandlerTest`（delete 路径本身）、`HistoryReadServiceTest` 等 | **缺**：撤销 **→ 历史投影** 的端到端断言（Phone delete 与 Wear latest-delete 各自结果）；缺 undo 后的四类形态（matched→unrecorded / unmatched 消失 / early intake→future context 消失 / delayed cross-date 无 ghost） | §3：JVM Cases A–D（production mutation path）+ 真 Room instrumented 撤销回环 + 文案审查（Decision C） |
| A7 | 读路径无写入 | **CLOSED（本轮）** | 既有：读路径结构上只调用 find/observe（代码审查） | **缺**：计数型 repository 替身的运行时零写入断言（服务层 + ViewModel 层）；缺真 Room 上的 counting decorator 证据 | §4：JVM 全 mutation 方法计数 + instrumented `HistoryReadZeroWriteDeviceTest` |
| A8 | 时间语义 T1–T6（provenance / wall-clock vs instant） | **COVERED** | `HistoryReadServiceExtremeZoneTest`（7）、`HistoricalProjectionIntakeTest` DST 用例、A-03-PRE-01 DST gap/overlap 回归 | — | fresh JVM 复核 |
| A9 | 五类新回归（删除计划存活 / 计划编辑归属 / 时区 A→B / DST×匹配 / occurrence identity golden vector） | **COVERED** | `HistoricalProjectionIntakeTest`、`HistoryReadServiceExtremeZoneTest`、`MedicationOccurrenceIdentityGoldenVectorTest`（6） | — | fresh JVM 复核 |
| A10 | fresh 验证（JVM 全量 + app instrumentation 受影响面 + `BUILD SUCCESSFUL` 原文） | **CLOSED（本轮）** | 既有：A-03-UI 轮 full Phone 235/0/5-skip、R1 238/0/5-skip；fresh JVM 1007 | **缺**：A-04 变更面（undo/zero-write/refresh/SavedState/index EXPLAIN）的 targeted instrumentation 与 full suite 重跑 | §5：受影响面矩阵 + targeted + full + assemble 原文 |

### 1.2 M1–M8（acceptance §9 mandatory backlog）

| # | 项目 | Current status | Existing evidence | Missing | Planned closure (A-04) |
|---|---|---|---|---|---|
| M1 | occurrence identity fixed golden vector | **COVERED** | `MedicationOccurrenceIdentityGoldenVectorTest`（6） | — | fresh JVM |
| M2 | 删除计划后事件存活 | **COVERED** | `HistoricalProjectionIntakeTest`（deleted plan survival） | — | fresh JVM |
| M3 | 计划编辑后历史归属（改时间/剂量/slot 三态） | **COVERED** | `HistoricalProjectionIntakeTest` / `MedicationOccurrencePresentationTest` | — | fresh JVM |
| M4 | 精确跨日边界（昨日 23:00 legacy × 今日 00:00，±1h 闭区间） | **COVERED** | `MedicationOccurrencePresentationTest` 边界用例 | — | fresh JVM |
| M5 | 时区 A 记录 → B 读取/确认/撤销 | **CLOSED（本轮补撤销侧）** | 读取/呈现：`HistoryReadServiceExtremeZoneTest`；确认：`WearAppConfirmationHandlerTest` | **撤销 × 时区**：无 —— 本轮在 A6 Case D 补时区/DST 场景撤销 | §3 Case D |
| M6 | DST × 匹配/去重/撤销 | **CLOSED（本轮补撤销侧）** | 匹配/生成/DST：`HistoricalProjectionIntakeTest`、A-03-PRE-01 DST 用例 | **DST × 撤销**：无 —— 本轮补一条 DST 日撤销回环 | §3 Case D |
| M7 | provenance 逐阶段断言 | **COVERED** | `HistoricalProjectionProvenanceTest`（10）+ A-03 呈现映射测试 | — | fresh JVM |
| M8 | 孤儿事件按真实 source 呈现 | **COVERED** | `HistoricalProjectionIntakeTest` + `HistoryPresentationTest`（非 MANUAL 不得标 Manual，源码护栏 + 渲染断言） | — | fresh JVM |

---

## 2. A-03/R1 剩余 precision cleanup（本轮先做）

| # | 问题 | 处理 | 状态 |
|---|---|---|---|
| 1.1 | `V17_A_03_HISTORY_UI.md` §18 仍引用已被删除的 key `history_note_legacy_context` | 改为当前真实 key `history_note_inferred_match`（文案"根据记录上下文推断匹配"） | 本轮 |
| 1.2 | `evidence/a-03-ui-r1/source-diff-stat.txt` 的 header 把 `d1889bf` 写成最终 R1 HEAD（生成于 guard commit 之前；真实 R1 HEAD = `bad2ab3`） | **不改冻结 evidence**；在本文件 §11 记录该历史 precision issue，并在 §2 说明后续 evidence 的生成方式（先完成全部提交再生成 diff 统计） | 本轮（仅记录） |
| 1.3 | R1 唯一 P2：modern quick-record 的 inferred 标注测试是手塞 `NULL_SLOT_TIME_WINDOW` provenance | 用**真实 production helper** `DoseEventEditSessionFactory.createQuickEvent(plan)`（`slotId = null`、`source = MANUAL`、`localDate = occurredAt` 当天）经 `HistoryReadService → matcher → projection → presentation` 断言中性文案，且绝不出现"旧版" | 本轮 |

---

## 3. A6 — 撤销 → 历史投影（Cases A–D + acceptance 两路径 + 文案审查）

**真实 mutation path（不使用 fake list 手工删除）**：

| 路径 | 生产入口 | 底层 mutation |
|---|---|---|
| Phone delete | `HRTViewModel.deleteEvent(id)`（`viewmodel/HRTViewModel.kt`） | `DoseEventRepository.delete(id)` / `deleteIfRevisionMatches` |
| Wear latest-delete | `WearAppUndoHandler.handle(WearAppUndoCommand)`（`application/WearAppUndoHandler.kt`） | `DoseEventRepository.deleteLatestRecordedIfRevisionMatches(id, revision)` |

| Case | 初始投影 | 撤销后期望 | 不得出现 |
|---|---|---|---|
| A matched intake | `MatchedHistoricalOccurrence` | 同一 occurrence → `UnrecordedHistoricalOccurrence`；event 不再出现在 unmatched | ghost matched entry、tombstone history fact、缓存旧结果 |
| B unmatched actual | `UnmatchedHistoricalIntake` | entry **完全消失** | 凭空生成 occurrence |
| C future occurrence + early intake（`scheduledAt > now`） | `MatchedHistoricalOccurrence` | occurrence 回到 `FutureOccurrenceContext`（**History 中完全消失**） | 变成 `UnrecordedHistoricalOccurrence` |
| D delayed/cross-date（Reminder/Wear 形状，含时区/DST） | `MatchedHistoricalOccurrence`（完整日期） | 撤销后 → `UnrecordedHistoricalOccurrence`，显示日期归因不变、无 ghost | ghost、日期漂移 |

**文案审查（Decision C）**：撤销后的历史呈现**只有** `Recorded` / `No recorded intake`（+ 未匹配实际事件）；
`Skipped` / `Missed` 不得出现。本轮以现有护栏（`HistoryPresentationWordingTest`：`history_*` 标签禁用词扫描）
+ `strings.xml` 复核作为文案审查证据，并在 A-04 文档登记审查结论。

---

## 4. A7 — 读路径零写入

**计数面（所有 mutation 方法）**：

| 仓库 | 方法 |
|---|---|
| `DoseEventRepository` | `insert` / `update` / `delete` / `deleteIfRevisionMatches` / `deleteLatestRecordedIfRevisionMatches` / `deleteAll` |
| `MedicationPlanRepository` | `save` / `setEnabled` / `delete` / `deleteAll` |

**三条被测链路**：

1. `HistoryReadService.readRange(...)` 直接调用 → `totalWrites == 0`（JVM 计数替身）；
2. `HistoryViewModel` 初始月加载 → `totalWrites == 0`（JVM 计数替身，走 `HistoryRangeSource`）；
3. 同月 `selectDate` → 额外 **reads == 0** 且 `writes == 0`；切月 / `retry` → reads > 0 但 `writes == 0`。

**真 Room 证据（强于 acceptance 最低要求）**：instrumented `HistoryReadZeroWriteDeviceTest` 用
counting decorator 包裹真实 Room repositories（`ProductionRepositoryProvider`），执行读链路后断言
`totalWrites == 0` 且 `dose_events`/`medication_plans`/`scheduled_dose_slots` 行数不变。

---

## 5. A10 — 受影响面矩阵

| Affected surface | 变更来源 | 需要的证据类型 | Existing | A-04 fresh |
|---|---|---|---|---|
| Historical projection | A-01/A-02（本轮无改动） | JVM | ✔ | fresh JVM 全量 |
| `HistoryReadService` 读路径 | A-02（本轮无生产改动） | JVM + instrumented 零写入 | 部分 | + `HistoryReadZeroWriteDeviceTest` |
| persisted `localDate` DAO 查询 | A-02 R2（本轮无 schema 改动） | Room instrumented | ✔（`HistoryReadServicePersistedDateTest` JVM + 既有 Room 测试） | + EXPLAIN QUERY PLAN 设备证据（§7） |
| History 日历/列表 UI | A-03/A-03-R1（本轮 refresh 已改） | Phone instrumentation | ✔ | + tab-return refresh 回归 |
| primary navigation | A-03（本轮不改导航结构） | Phone instrumentation | ✔ | full suite |
| undo → History 交互 | **A-04 新增** | JVM + Room instrumented | ✘ | §3 两路径 |
| ViewModel 状态恢复（SavedState factory） | **A-04 新增** | Android instrumentation（真实 `CreationExtras`） | ✘ | §8 |
| Wear 主源码 / Widget / PK | 本轮无改动 | — | — | **不为其新增证据**（avoid padding） |

A10 只有在上述"需要 A-04 fresh"的每一面都有对应证据后才标 CLOSED。

---

## 6. History refresh（P2 用户体验缺口）

**审计结果**：仓库**没有**既有 tab/resume refresh idiom（`LifecycleEventObserver`/`repeatOnLifecycle`/
`onResume` 主源码 0 命中），因此新增一个**局部**契约：

| 事件 | 契约 |
|---|---|
| 首次进入 | 正常 load（现有 init load） |
| 从其它 tab 返回 History | 当前 `visibleMonth` 重新读取一次 |
| app 从后台回前台且 History 处于前台 | 重新读取一次（同一入口，去重） |
| 跨日 | 重新取 `now`/`displayZone`/`today`；若可见月**曾是**当前月而今日已进入新月份 → 可见月/选中日前进到新当前月/today |
| 同一次 composition/recomposition | 不重复 query |
| 同月选日期 | 仍 0 query |

实现方式（不改全局导航/lifecycle 架构）：`HistoryViewModel.onActiveSurfaceEntered()`（幂等入口，
在 load 进行中或尚未完成首次 load 时不重复触发）+ `HistoryScreen` 内的
`LaunchedEffect(Unit)`（目的地重新进入 composition 时触发）与 `LifecycleEventObserver`
（`ON_START`，app 回前台时触发）。若实现被迫需要全局 nav/lifecycle 重构 → 停止并返回
`A-04 REFRESH ARCHITECTURE DECISION REQUIRED`。

---

## 7. `dose_events.localDate` index — 只读评估（先评估，不盲目迁移）

审计输入：当前 Room schema（v3：`dose_events` **无任何 index**；`medication_plans` 无 index；
`scheduled_dose_slots` 有 `planId` 与 `(planId, position)` unique）、Channel A 查询形态
（`WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ?`，按月一次）、
`EXPLAIN QUERY PLAN` 原始输出、可能的行数量级。

结论只能二选一：`INDEX REQUIRED FOR v1.7`（需同时满足 acceptance/performance gate 要求，或有可复现实测表明
scan 成本不可接受）或 `INDEX DEFERRED`（有证据说明不阻塞 v1.7）。**任何 index 实现都会先返回
`A-04 SCHEMA DECISION REQUIRED`**，不得混入 hardening commit。

---

## 8. Anti-androgen placeholder 审计（只读）

reviewer 指出 anti-androgen 计划/事件可能显示成 `E2 · 50.0 mg`。本轮只审计：
**production anti-androgen 事件是否真的会走到该呈现**，以及 UI model 是否已有**无需推测**的 identity
可用于修正。若必须由 route/ester 推测药物身份 → **不修**，登记 `A-04/B presentation model debt`。

---

## 9. P3 低风险清理（仅在有证据证明无 consumer 时）

- 删除 `HistoryPresentation.kt` 中未被 production/测试使用的 helpers（`selectedDayOrNull`、`presentedDay`）；
- `HistoryScreenContent` 可见性：Preview 与 androidTest（视觉证据）都使用它，**保持 public** 并在文档说明原因；
- 不改其它 API。

---

## 10. 验证与证据计划

- Fresh JVM：`:experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks`（计数取 XML）；
- Targeted Android：zero-write（真 Room）、undo 回环、SavedState factory、EXPLAIN QUERY PLAN、refresh tab-return；
- Full Phone：`:app:connectedDebugAndroidTest`（authoritative phone result）；
- `:app:assembleDebug` + `git diff --check`（assemble 若为 UP-TO-DATE → 如实记录 build-green）；
- 证据目录 `docs/evolune/v1.7/evidence/a-04/`：gate matrix、fresh JVM XML/log、targeted XML/log、
  full XML/log、zero-write、undo-projection、refresh、index-EXPLAIN、聚合 TSV、assemble log、`MANIFEST.sha256`
  （coverage 100% + `sha256sum -c` + self hash + HEAD blob == worktree）；
- 旧 evidence（a-01/a-02/a-03/a-03-pre-01/a-03-ui/a-03-ui-r1）**全部 frozen**。

---

## 11. 历史 precision issues（只记录，不改冻结 evidence）

| # | 位置 | 问题 | 处理 |
|---|---|---|---|
| 1 | `evidence/a-03-ui-r1/source-diff-stat.txt` | header 记录 `# head (R1 HEAD) = d1889bf…`；该文件生成于 guard commit（`69dc99c`）之前，真实 R1 最终 HEAD 是 `bad2ab3` | 冻结不动；本文件登记。A-04 的 `source-diff-stat.txt` 改为**在所有提交完成后**生成 |
| 2 | `evidence/a-03-ui/a03ui-assemble-debug.log` + `V17_A_03_HISTORY_UI.md` §22.4 | 原文档曾声称"APK 正是 instrumentation 安装的那一份"（无 APK SHA 证据） | 已在 R1 轮改为 build-green（UP-TO-DATE）表述；本轮沿用该口径 |

---

## 12. Stop conditions（本轮）

| 停止条件 | 触发判据 |
|---|---|
| `A-04 REFRESH ARCHITECTURE DECISION REQUIRED` | refresh 必须改全局导航/lifecycle 架构 |
| `A-04 SCHEMA DECISION REQUIRED` | `localDate` index 必须进入 v1.7 |
| `A-04 ACCEPTANCE DECISION REQUIRED` | acceptance 的 A6/A7/A10 与当前设计存在无法局部满足的冲突 |

---

## 13. 实施结果（本轮实际完成）

### 13.1 A6 — 撤销 → 历史投影：**CLOSED**

`app/src/test/java/.../history/HistoryUndoProjectionTest.kt`（5 tests），全部经**真实 mutation path**：

| Case | mutation path | 断言 |
|---|---|---|
| A matched intake | `HRTViewModel.deleteEvent(id)`（Phone delete → `DoseEventRepository.delete`） | entry 从 `MatchedHistoricalOccurrence` 变为 `UnrecordedHistoricalOccurrence`；`recordedCount 1→0`、`unrecordedCount 0→1`、`unmatchedActualCount == 0`（无 ghost/tombstone） |
| B unmatched actual | 同上 | 该 `UnmatchedHistoricalIntake` **完全消失**；occurrence id 集合前后相同（未凭空生成 occurrence） |
| C future occurrence + early intake | 同上 | 撤销后该日的 History **为空**；domain 层复核 occurrence 落在 `futureOccurrences`（`FutureOccurrenceContext`），**不是** unrecorded |
| D1 delayed/cross-date | `WearAppUndoHandler.handle(WearAppUndoCommand)`（Wear latest-delete） | `UNDONE`、`latestDoseDeleteCalls == 1`、`conditionalDeleteCalls == 0`；撤销后 → unrecorded，`displayDate` 仍为计划日（不漂移） |
| D2 DST 日（Paris 2025-03-30，gap 日）| Phone delete | 撤销后 attribution 不变、无 ghost |

**测试替身保真度修复（本轮，测试专用）**：`FakeDoseEventRepository.delete(id)` 原为返回 `NotFound` 的 stub，
无法表达真实删除；现改为与 `RoomDoseEventRepository.delete` 等价（存在则删除并返回 `Deleted`，否则 `NotFound`），
并新增 `deleteCalls` / `deleteFailure`。同时把 `rangeEvents` / `localDateRangeEvents` / `pkEvents` 从"启动快照"
改为**默认 null + 回落到 live event map**，使"撤销后再读取"能观察到权威状态（既有显式赋值用法不受影响）。

**文案审查（Decision C）**：撤销后的呈现只有 `已记录` / `无记录`（+ 已记录用药）；
`HistoryPresentationWordingTest` 的 `history_*` 标签禁用词扫描 + `strings.xml` 复核未发现 `Skipped`/`Missed`
或对应中文（`漏服` 仅出现在**必须的**中性免责声明 `history_note_not_necessarily_missed` 中，且该声明显式否定该解读）。

### 13.2 A7 — 读路径零写入：**CLOSED**

**JVM（计数型替身，acceptance 判定方式）** —— `HistoryReadZeroWriteTest`（4 tests）：
`CountingDoseEvents` / `CountingPlans` 覆盖两个仓库的**全部 mutation 方法**
（`insert`/`update`/`delete`/`deleteIfRevisionMatches`/`deleteLatestRecordedIfRevisionMatches`/`deleteAll`、
`save`/`setEnabled`/`delete`/`deleteAll`），并同时统计读调用以证明读路径**真的执行**：

| 链路 | 断言 |
|---|---|
| `HistoryReadService.readRange(...)` | `writes == 0`，`reads > 0`（含 plans 读） |
| `HistoryViewModel` 初始月加载 | `writes == 0`，`reads > 0` |
| 同月 `selectDate` | reads 不变、`writes == 0` |
| refresh（`onSurfaceShown`）/ 切月 / `retry` | reads 增加、`writes == 0` |

**真 Room（强于 acceptance 最低要求）** —— `HistoryReadZeroWriteDeviceTest`（instrumentation，1 test）：
counting decorator 包裹**生产** Room 仓库，读一次 History 前后比较
`dose_events`（id → revision）与 plans（id → 剂量/启用/slot 数）快照完全一致，
且 `dose_events` / `plans` 的写入计数均为 0。

### 13.3 A10 — fresh 验证 + 受影响面矩阵：**CLOSED**

| Affected surface | 证据（本轮 fresh） |
|---|---|
| Historical projection | fresh JVM 全量（`MedicationOccurrencePresentationTest` / `HistoricalProjection*Test` / identity golden vector） |
| `HistoryReadService` 读路径 | fresh JVM + `HistoryReadZeroWriteDeviceTest`（Room instrumented） |
| persisted `localDate` DAO 查询 | `HistoryIndexQueryPlanTest`（真 Room + 20k 行 EXPLAIN QUERY PLAN，见 §14） |
| History 日历/列表 UI | full Phone instrumentation（含 `HistoryScreenTest` / `HistoryVisualEvidenceTest`） |
| primary navigation | full Phone instrumentation（含 `HistoryNavigationTest` + 既有导航测试） |
| undo → History 交互 | `HistoryUndoProjectionTest`（JVM，两条生产路径） |
| ViewModel 状态恢复（factory + 真实 extras） | `HistorySavedStateFactoryTest`（instrumentation） |
| History refresh（tab 返回 / 前台返回） | `HistoryRefreshDeviceTest`（instrumentation）+ `HistoryRefreshTest`（JVM 9 例） |
| Wear 主源码 / Widget / PK | **本轮无改动、不为其补跑**（避免为凑数字而增加无关 instrumented 面） |

计数（XML 为准）见 §15。

### 13.4 History refresh：**CLOSED（局部实现，无架构变更）**

审计确认仓库**没有**既有 tab/resume refresh idiom，因此新增**局部**契约（未改导航/lifecycle 架构）：

- `HistoryViewModel.onSurfaceShown()`：目的地重新进入 composition 时调用（切换 tab 返回）。
  **首次**进入由构造时的初始加载负责（`surfaceShownOnce` 位），因此冷启动恰好 1 次读、recomposition 0 次读；
- `HistoryViewModel.onAppForegrounded()`：屏幕只在**真实** `ON_STOP → ON_START` 之后调用（`wentToBackground` 位），
  因此冷启动的 `ON_START` 不会造成第二次加载；
- 两者共用 `refreshVisibleMonth()`：以 in-flight 守卫合并；重新取 `now` / `displayZone` / `today`；
  跨日时若可见月**曾是**当前月则前进到新的当前月/today（用户正在浏览的历史月不受打扰）；
- 同月选日期仍然 **0 次读**；
- 生成世代改为 **先 `++loadToken` 再 `loadJob?.cancel()`**。

**测试**：`HistoryRefreshTest`（9）+ `HistoryRefreshDeviceTest`（机测 tab 返回且数据在离开期间被写入 → 返回后可见）。

### 13.5 SavedState factory production coverage：**CLOSED（含诚实覆盖边界）**

`HistorySavedStateFactoryTest`（instrumentation）用**真实 `ViewModelProvider` + Activity 的真实
`CreationExtras`** 创建 ViewModel（这是 `extras.createSavedStateHandle()` 真正拿到 `VIEW_MODEL_KEY` 的路径），
断言：月/日写入后通过 `Instrumentation.callActivityOnSaveInstanceState` 确实出现在 Activity saved state 中，
并经真实 save/restore 周期保留。

**覆盖边界（如实声明）**：真正的"进程死亡"（`SavedStateHandlesVM` 被丢弃）无法在 instrumentation 中模拟
（`SavedStateHandleSupport` 的 key 在 lifecycle 2.10 为 internal）；因此**恢复读取规则**
（畸形值回落、恢复出的未来月被夹回）由 `HistoryViewModelTest`（JVM，同一份 parse/clamp 代码）覆盖，
本机测覆盖 factory/extras 路径与持久化。**不以 `HistoryViewModel(savedStateHandle = ...)` 冒充 production factory 路径。**

### 13.6 §1 precision cleanup：**CLOSED**

| # | 结果 |
|---|---|
| 1.1 | `V17_A_03_HISTORY_UI.md` §18 的 key 已改为 `history_note_inferred_match`（并注明更名来源；§28 保留更名历史说明） |
| 1.2 | 冻结 evidence 不改；`source-diff-stat.txt` 的 `d1889bf` header 在 §11 登记为历史 precision issue；本轮 evidence 的 diff 统计在**全部提交完成后**生成 |
| 1.3 | `HistoryQuickRecordInferredTest`（2 tests）：用生产 `DoseEventEditSessionFactory.createQuickEvent(plan)`（`slotId == null`、`source == MANUAL`、`localDate != null`）经 `HistoryReadService → matcher → projection → presentation` 断言 `NULL_SLOT_TIME_WINDOW` + 中性文案；instrumentation 的同名用例也改为生产形状（真实 quick-record writer + 真实 `HistoricalProjectionBuilder`），不再手塞 provenance |

### 13.7 §10 anti-androgen 审计（presentation-only 修复 + 债务登记）

审计结论：**确实可能发生**。反雄计划/事件的 `matchKey.medicationKey` 携带的是**酯类槽位**（如 `E2`），
而权威药物是 anti-androgen；投影类型（`RecordedMedicationEvent` / `MedicationOccurrence.presentation`）
**不携带** anti-androgen identity（它在 `extras[ExtraKey.ANTI_ANDROGEN_TYPE]`，投影层不读取）。
旧实现会因此显示成 `E2 · 50.0 mg`。

处理（**不推测药物**）：anti-androgen 路由下**不再回落到 raw key**（`medicationFallback` 返回 null），
卡片只显示方案名 + 剂量 + 路由（`抗雄口服`），因此不会出现错误的酯类占位符。
真实药物名的正确做法需要投影携带 anti-androgen identity → 登记为
**`A-04/B presentation model debt`**（不修，需 domain 字段）。

### 13.8 §12 P3 清理

- 删除无 consumer 的死代码 `HistoryMonthUiModel.selectedDayOrNull()` 与 `HistoryUiState.presentedDay()`
  （清理前 `rg` 证明仅定义、无引用）；
- `HistoryScreenContent` **保持 public**：Preview（main）与 `HistoryVisualEvidenceTest`（androidTest 视觉证据）
  都消费它；改为 internal 会破坏 androidTest 视觉证据，收益不足，故在文档记录不改。

---

## 14. `dose_events.localDate` index 评估结论

**结论：`INDEX DEFERRED`**（有实测证据，不阻塞 v1.7）。

| 输入 | 事实 |
|---|---|
| 当前 schema | v3（`app/schemas/.../3.json`）：`dose_events` **无任何 index**；`medication_plans` 无 index；`scheduled_dose_slots` 有 `planId` 与 `(planId, position)` unique |
| 查询形态 | Channel A（`WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ? ORDER BY localDate ASC, occurredAtEpochMillis ASC, id ASC`）与 Channel B（`occurredAtEpochMillis` 半开区间）**均与 `DoseEventDao` 逐字一致** |
| `EXPLAIN QUERY PLAN`（20,000 行，Pixel_7 API 35，真 Room） | Channel A：`SCAN dose_events` + `USE TEMP B-TREE FOR ORDER BY`；Channel B 相同 |
| 实测成本（20 次取中位数，行被完全消费） | Channel A **median 2.02 ms**（max 3.13 ms）；Channel B **median 1.55 ms**（max 1.97 ms） |
| 查询频率 | 一次 History 月份加载 = Channel A 一次 + Channel B 一次；同月选日期 0 次；refresh / 切月 / retry 各 1 次 |
| 数据量与余量 | 20,000 行 ≈ 每日多次记录下 5–10 年的量级；v1.7 目标量级远低于此 |
| acceptance / performance gate | `V17_ACCEPTANCE.md` A1–A10 / G1–G7 **无性能门**要求该 index |

判定依据：两个允许条件（"acceptance/performance gate 要求" 或 "可复现实测表明不可接受成本"）**均不满足**，
因此**不进入** schema 变更；`A-04 SCHEMA DECISION REQUIRED` **未触发**。原始输出见
`evidence/a-04/explain-query-plan.txt`（由 `HistoryIndexQueryPlanTest` 生成并拉取）。

若未来数据量或查询频率显著上升（例如 Timeline/Insights 引入按日查询），应以新的实测重开该议题。

---

## 15. 验证结果（A-04，计数取 XML）

### 15.1 Fresh JVM

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

| 模块 | XML | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 93 | 797 | 0 | 0 | 0 | 797 |
| experience-core | 15 | 140 | 0 | 0 | 0 | 140 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **119** | **1027** | **0** | **0** | **0** | **1027** |

`54 actionable tasks: 54 executed`；相对 A-03-UI-R1 基线 1007 → **+20**
（`HistoryRefreshTest` 9 / `HistoryUndoProjectionTest` 5 / `HistoryReadZeroWriteTest` 4 /
`HistoryQuickRecordInferredTest` 2）。

### 15.2 Targeted instrumentation（A-04 新增面）

`HistoryReadZeroWriteDeviceTest` / `HistorySavedStateFactoryTest` / `HistoryIndexQueryPlanTest` /
`HistoryRefreshDeviceTest` → **5 / 5 passed**（XML：`evidence/a-04/androidtest-xml/targeted-a04-*.xml`）。

### 15.3 Full Phone instrumentation（authoritative）

见 `evidence/a-04/androidtest-xml/final-a04-*.xml`；对照基线：
A-03 UI 235 / R1 238 → A-04 **243**，要求"不新增 unexpected failure / skip"。

### 15.4 Assemble / hygiene

`./gradlew :app:assembleDebug` + `git diff --check`：结果与 UP-TO-DATE 状态见 §16 与 closure report。

---

## 16. 证据与文档（A-04）

- 证据目录：`docs/evolune/v1.7/evidence/a-04/`（**130 条目，coverage 130=130，`sha256sum -c` 130 OK / 0 FAILED**，
  manifest 自哈希 `658603de2458d6e8c3639a3162e2ee21aa040fddd30a153459b25010ad5f2fe3`）：
  `gate-matrix-V17_A_04_HARDENING.md`（本文件快照）、`jvm-{app,experience-core,wear}/`（119 XML）、
  `a04-jvm-run.log`、`jvm-aggregate.tsv`、`androidtest-xml/{targeted-a04,final-a04}-TEST-Pixel_7-AVD-15-app.xml`、
  `a04-targeted-instrumentation.log`、`a04-full-instrumentation.log`、`androidtest-aggregate.tsv`、
  `explain-query-plan.txt`（EXPLAIN + 实测耗时）、`a04-assemble-debug.log`、`source-diff-stat.txt`（改动面 + 13 项边界 0 改动证明）；
  提交后校验：coverage 100% / `sha256sum -c` / self hash / HEAD blob 0 mismatch；
- 旧 evidence（a-01 / a-02 / a-03 / a-03-pre-01 / a-03-ui / a-03-ui-r1）保持 **frozen**；
- 文档更新：本文件、`V17_PLAN.md`、`V17_ACCEPTANCE.md`（Phase A 状态与证据段）、
  `V17_A_03_HISTORY_UI.md`（§1.1 精度修正）。

## 17. Phase-A 关闭判定

| 条件 | 状态 |
|---|---|
| A6 CLOSED | ✅（§13.1） |
| A7 CLOSED | ✅（§13.2） |
| A10 CLOSED | ✅（§13.3 + §15） |
| A-03 R1 P2 quick-record test-shape closed | ✅（§13.6） |
| History refresh P2 closed | ✅（§13.4） |
| SavedState production path coverage closed | ✅（§13.5，含明确覆盖边界） |
| 无新 P0/P1 | ✅（见 closure report P0–P3） |
| localDate index | ✅ `INDEX DEFERRED`（实测证据，不阻塞 v1.7，§14） |

→ **PHASE A — CLOSED**（候选实现，待独立复审）。

---

## 18. A-04 复审后续（非阻塞 P2，**不重开 Phase A**）

A-04 独立复审 APPROVE（PHASE A CLOSED）另登记两个非阻塞 P2，转入后续 backlog：

| # | 发现 | 影响 | 处理计划 |
|---|---|---|---|
| R1 | History 处于 active 时若发生**后台** authoritative write（例如 Wear/Widget 在 History 打开期间写入），界面**不会实时刷新**，要等到下一次 activation（tab 返回 / 前台返回）才更新 | 用户体验：静态屏幕可能短暂陈旧 | 需一个 realtime/observation-based refresh 设计（例如对权威事件流做去抖订阅），属于 Phase B 之后的刷新策略细化；A-04 的 refresh 契约（activation-based）已如实覆盖其边界，不因此重开 Phase A |
| R2 | `foreground ON_STOP→ON_START` 路径**缺真实 instrumentation**；`same-month rollover`（可见月=当前月且跨日后前进）**缺单独 regression** | 覆盖面：JVM 已覆盖逻辑（`HistoryRefreshTest` 9 例含 rollover 与前台刷新），但设备侧只覆盖 tab 返回 | 在后续 hardening 轮补：（a）设备级 ON_STOP→ON_START 刷新回归；（b）same-month rollover 的独立 JVM regression（与"浏览历史月不受打扰"分开断言） |

> 两项均为 P2、非阻塞，已按复审结论登记；Phase A 状态保持 **CLOSED**。
