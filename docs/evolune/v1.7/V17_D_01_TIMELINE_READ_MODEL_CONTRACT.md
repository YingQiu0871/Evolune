# V17-D-01 — Timeline Read Model / Row Projection — Contract

> 状态：`CONTRACT — APPROVED / FROZEN`（**D-01 APPROVED / CLOSED**）
> Approved contract HEAD：`a245a5ec7a2dcd977ff0de3b3a79c8b129f67e34`
> Approved implementation HEAD：`97838fbf7c8692ada9d44d8401a6008283fac178`
> evidence：`docs/evolune/v1.7/evidence/d-01/`
> **No further D-01 production changes are authorized without reopening review.**
> Round：v1.7-D / **D-01**（Timeline Read Model / Row Projection；semantics/read-model only，无 UI）
> Base HEAD：`cfbf545dd81105367e304814c7ece9d7d79292c1`（Phase C APPROVED / CLOSED）
> 上游（约束性输入，**优先于本文件**）：
> - `V17_SPEC.md` §5（Timeline）、§2（单一事实源/共享派生）、§7、§12 Invariant 2、§13
> - `V17_PLAN.md` §7（D-01…D-07；gate `APPROVE V1.7-D CANDIDATE IMPLEMENTATION`）
> - `V17_ACCEPTANCE.md` §5（D1–D5）、§0（G1–G7）
> - `V17_C_PHASE_CLOSURE.md`（Phase C APPROVED / CLOSED；delta gated）
> - planning audit：`review-packets/v17-phase-d-baseline-scope-audit.txt`
> 本文件只定义 **D-01 read-model / semantics contract**；不实现 UI、不做 range loading、
> 不新增 read seam、不触碰 Phase C / C-01 / C-04。

---

## 0. 源核对清单（source-verified，写死前已逐项核对）

| 事实 | 位置 | 冻结引用 |
|---|---|---|
| `HistoryRangeSource.read(startDate, endDate, displayZone, now): HistoricalRange`；唯一生产实现 `HistoryReadService::readRange` | `app/.../history/HistoryRangeSource.kt:22-29` | D-01 的**唯一**输入 seam（eventual range-read） |
| `HistoricalRange(startDate, endDate, days)`；`HistoricalDay(date, entries)`；只返回有 entries 的日期 | `experience-core/.../HistoricalReadModel.kt:50-80` | sections 来源；不合成空日期 |
| `HistoricalEntry`：`displayDate: LocalDate`、`displayDateProvenance`、`sortInstant: Instant`、`sortKey: String` | `experience-core/.../HistoricalProjection.kt:42-59` | displayDate 归因、sortInstant、tie-break 输入 |
| `MatchedHistoricalOccurrence(occurrence, event, matchProvenance, status, actionAvailability, scheduleTimeContext, crossesLocalDateBoundary, displayDate, displayDateProvenance)`；`sortInstant = occurrence.scheduledAt`；`sortKey = "0:"+occurrence.id.value+":"+event.eventId` | 同上 `:62-86` | MATCHED row 双面事实 |
| `UnrecordedHistoricalOccurrence(occurrence, status, actionAvailability, scheduleTimeContext, displayDate, displayDateProvenance)`；`sortInstant = scheduledAt`；`sortKey = "1:"+occurrence.id.value` | 同上 `:100-112` | UNRECORDED_SCHEDULE row |
| `UnmatchedHistoricalIntake(event, source, displayDate, displayDateProvenance)`；`sortInstant = event.occurredAt`；`sortKey = "2:"+event.eventId` | 同上 `:121-132` | UNMATCHED_INTAKE row |
| `HistoricalProjection.entries` 与 `futureOccurrences` 分离；`futureOccurrences` 刻意不是 entries | 同上 `:151-169` | future 非 D-01 行 |
| `MedicationOccurrence(id: MedicationOccurrenceId, planId, slotId, slotPosition, presentation: MedicationPresentation, scheduledAt, scheduledLocalDateTime, zoneId)` | `experience-core/.../MedicationOccurrence.kt:79-88` | scheduleContext 事实 |
| `MedicationPresentation(planName, matchKey: MedicationMatchKey, doseUnit)`；`MedicationMatchKey(routeKey, medicationKey, doseAmount)`；`MedicationOccurrenceId(value: UUID)` | 同上 `:22-42,77` | match key / identity 输入；`planName` **不是**身份证据 |
| `RecordedMedicationEvent(eventId: UUID, occurredAt, slotId, matchKey, source, localDate, zoneId, extras)` | `experience-core/.../MedicationTimeline.kt:18-34` | recordedIntake 事实 |
| `MedicationIdentityClassifier.classify(matchKey): MedicationIdentity(status: KNOWN/PARTIAL/UNAVAILABLE, key)` | `experience-core/.../insights/MedicationIdentityClassifier.kt:16-31`；`InsightsModels.kt:14-47` | 唯一身份分类器 |
| `OCCURRENCE_ORDER = compareBy(scheduledAt, planId.toString(), slotPosition, slotId.toString(), id.value.toString())`，**`internal`（experience-core 模块内可见）** | `experience-core/.../MedicationOccurrenceGenerator.kt:126-132` | 同 instant occurrence tie-break 的字段序列证明；app 模块**不能直接 import**，按字段序列镜像 |
| `HISTORICAL_ENTRY_ORDER = compareBy(displayDate, sortInstant, sortKey)` | `experience-core/.../HistoricalProjection.kt:334-335` | 现存投影顺序（参考） |
| `MedicationTimelineSelector` 是 live "previous/current/upcoming" 选择器，**不是**历史 Timeline | `experience-core/.../MedicationTimeline.kt:149-205` | 不得混用 |

---

## 1. D-01 authoritative goal（冻结）

D-01 derives a **read-only Medication Timeline model** from the **SAME historical projection already
used by History**（`HistoricalRange` ← `HistoryRangeSource`），satisfying the semantic foundation for：

- **D1** shared projection / no duplicated matcher；
- **D2** deterministic chronological ordering；
- **D3** date attribution consistency with History。

D4（UI a11y/localization）与 D5（fresh verification + independent review）是**后续 Phase-D gates**，
不在 D-01。

---

## 2. Source boundary（冻结；违反即 STOP）

D-01 生产必须是纯派生投影：

```text
HistoricalRange (from HistoryRangeSource)
    -> TimelineProjectionBuilder / TimelineReadModelBuilder
    -> TimelineReadModel
```

D-01 **不拥有**：date-range loading、month selection、navigation、ViewModel、Compose、
resource strings、direct `HistoryReadService` calls —— 全部属于 D-02/D-03/D-04/D-05。

**不授权新 read seam。** Forbidden：DAO、repository、Room、第二 matcher、第二 occurrence
generator、第二 truth store、`AllAvailableHistorySource`、直接 event 查询。

`HistoryRangeSource` 保持为**唯一**的 eventual range-read seam（D1）。

---

## 3. Row families（冻结：恰好三个）

```text
MATCHED              <- MatchedHistoricalOccurrence
UNRECORDED_SCHEDULE  <- UnrecordedHistoricalOccurrence
UNMATCHED_INTAKE     <- UnmatchedHistoricalIntake
```

**MATCHED**：一条 schedule-context occurrence + 一条权威 recorded intake（两个概念上独立的侧面，§6）。

**UNRECORDED_SCHEDULE**：包含进 Timeline。含义**严格**为：

> 存在 schedule-context occurrence，且没有权威 recorded intake 与之关联。

它**不得**表示：missed / skipped / overdue / failed adherence / not taken。

Canonical future presentation semantics（后续 UI 用，D-01 不产出本地化文案）：

```text
No recorded intake / 未记录摄入
```

D-01 自身只暴露结构化 row kind，不产出 localized copy。

**UNMATCHED_INTAKE**：权威 recorded fact，必须保持可见；**不得**为其合成 schedule occurrence。

**futureOccurrences 不是 D-01 行**（§20）。

---

## 4. Row identity（冻结；不用 sortKey 作为主身份）

```text
sealed interface TimelineRowId
    data class Occurrence(val occurrenceId: MedicationOccurrenceId) : TimelineRowId
    data class Event(val eventId: UUID) : TimelineRowId
```

规则：

| row kind | rowId |
|---|---|
| MATCHED | `Occurrence(occurrence.id)` |
| UNRECORDED_SCHEDULE | `Occurrence(occurrence.id)` |
| UNMATCHED_INTAKE | `Event(event.eventId)` |

因此：

- `UNRECORDED → MATCHED` 与 `MATCHED → UNRECORDED` **保持** occurrence-backed identity；
- 真实的 `UNMATCHED_INTAKE → MATCHED` reconciliation 可以合法地把 `Event(eventId)` 变为
  `Occurrence(occurrenceId)`（权威投影 family 已改变）；
- **不授权**持久化 Timeline ID 列/存储；
- 现存 `HistoricalEntry.sortKey` 只作为 ordering/tie-break 输入保留，**不是** row identity（§15）。

---

## 5. Row data（最小结构化字段；无本地化字符串）

源核对后的字段名以 §0 为准。概念结构：

```text
common:
  rowId: TimelineRowId
  rowKind: MATCHED | UNRECORDED_SCHEDULE | UNMATCHED_INTAKE
  displayDate: LocalDate            // 直接来自投影 entry（§14）
  sortInstant: Instant              // 有效 instant（§15）

occurrence-backed（MATCHED / UNRECORDED_SCHEDULE）:
  occurrenceId: MedicationOccurrenceId
  scheduledAt: Instant
  scheduledMatchKey: MedicationMatchKey          // schedule-context 事实
  scheduledIdentity: MedicationIdentity          // classify(scheduledMatchKey)

recorded-backed（MATCHED / UNMATCHED_INTAKE）:
  eventId: UUID
  occurredAt: Instant
  recordedMatchKey: MedicationMatchKey           // recorded 事实
  recordedIdentity: MedicationIdentity           // classify(recordedMatchKey)
```

规则：

- Dose **必须**来自各自侧面的权威 `MedicationMatchKey.doseAmount`（§7）；
- 不放入 localized/formatted strings；read-model 层**不依赖** `HistoryFormatting`；
- 可选 source/provenance 字段**不加入**，除非未来权威 Phase-D 需求要求（后续 contract 显式扩展）；
- `planName` 不进入 D-01 模型（§8：不得作为身份证据；需要时由显式后续 contract 添加）。

---

## 6. Schedule context 与 recorded fact 分离（truthfulness invariant）

一条 MATCHED row **必须**保留两个概念上独立的侧面：

**scheduleContext**（来自 `MedicationOccurrence`）：current schedule context materialized for this
historical occurrence。**不得**称为 historical prescription snapshot / historical prescribed dose /
historical medication plan at that time —— Phase A 没有历史 plan 快照（Decision A）。

**recordedIntake**（来自 `RecordedMedicationEvent`）：权威 actual medication fact。对 MATCHED row，
UI 之后描述"实际服用了什么"时**必须**使用 recorded 侧；**不得**用 schedule-context 的 dose/name
替代 recorded actual。

该分离在 occurrence/event match keys 不同时**仍然必须**成立。

---

## 7. Dose truthfulness（冻结）

- MATCHED：scheduled-context dose 与 recorded-intake dose 保持**两个独立事实**；
- UNRECORDED_SCHEDULE：dose 仅为 schedule-context；
- UNMATCHED_INTAKE：dose 仅为 recorded-event fact。

后续 UI **不得**把 schedule-context dose 呈现为 actual recorded dose。
D-01 从 dose 差异**不计算**任何 adherence/timing 解释。

---

## 8. Medication identity truthfulness（冻结）

复用**唯一**的 `MedicationIdentityClassifier`（无第二 classifier）：

- `KNOWN` → 后续 UI 可呈现已批准的 canonical medication identity；
- `PARTIAL` → 不得发明具体 medication 名称；
- `UNAVAILABLE` → 不得发明 medication identity（anti-androgen real identity 保持 unavailable，
  除非未来单独批准的契约改变 source model）。

D-01 **不得**从以下推断身份：plan name、route alone、historical context、user-facing strings。
`planName` 不得用作 historical medication identity 的证明。

---

## 9. Time fields（冻结）

| row kind | scheduledAt | occurredAt |
|---|---|---|
| MATCHED | 有 | 有 |
| UNRECORDED_SCHEDULE | 有 | 无 |
| UNMATCHED_INTAKE | 无 | 有 |

这支持 SPEC §5 的 scheduled + actual 时间呈现，**不**计算 timing metric。

---

## 10. Delta decision（显式排除）

D-01 **排除**：

- signed actual-vs-planned delta；
- `Duration.between(scheduledAt, occurredAt)` presentation metric；
- `+2h41m / -7m` 字段；
- early / late / on-time / overdue；
- adherence / compliance interpretation。

SPEC §5 的 `+2h41m` 示例**不**静默解冻此前冻结的 Option-2 semantic。
两个 raw timestamp 可以共存于同一 row；**没有任何 derived timing-difference 字段**属于 D-01。
未来 delta 功能需要单独显式语义决策。

---

## 11. Date attribution（冻结）

D-01 必须保留权威投影的 `displayDate`（`HistoricalEntry.displayDate`，含其
`displayDateProvenance` 语义）。Timeline projector **不得**用设备当前 zone 重算日期归因。
Timeline section attribution = `HistoricalEntry.displayDate`（D3 consistency with History）。
不做超出既有投影的历史时区重建。

---

## 12. Ordering（D2 显式解决；冻结）

**Canonical Timeline ordering（ascending）：**

1. `displayDate` 升序；
2. 有效 `sortInstant` 升序，其中 MATCHED / UNRECORDED_SCHEDULE → `scheduledAt`，
   UNMATCHED_INTAKE → `occurredAt`。

对相同 `displayDate` + `sortInstant`：

- **A.** 两行都是 occurrence-backed：按现存 `OCCURRENCE_ORDER` 的**字段序列**比较
  —— `scheduledAt`, `planId.toString()`, `slotPosition`, `slotId.toString()`,
  `id.value.toString()`（source-verified：experience-core `OCCURRENCE_ORDER` 为 `internal`，
  app 模块不能直接 import；字段序列与字符串比较约定按此镜像，UUID 用其 `toString()`
  词法序）；
- **B.** occurrence-backed vs unmatched intake：occurrence-backed 在前；
- **C.** 两行都是 unmatched intakes：`eventId` 的确定性词法序（`UUID.toString()`）。

Canonical data ordering 为**升序**（后续 UI 若要反转仅作视觉方向，且需后续 contract 显式允许；
D-01 只暴露一个确定性 canonical order）。

`HistoricalEntry.sortKey` **不**替代 occurrence-backed 同 instant 行的 `OCCURRENCE_ORDER` 字段序列；
source proof（§0）表明在 A/B/C 规则下同 instant 行已被唯一区分（每 occurrence/event 至多出现一次），
因此 sortKey **不是**排序必需，仅可作为实现层防御性断言/调试用途，不构成契约顺序的一部分。

---

## 13. Range / grouping boundary（冻结）

D-01 **不选择**：month selector、pagination、infinite scroll、load more、date picker、
History entry-card navigation、bottom tab。

D-01 接收已权威的 `HistoricalRange` 并投影；它必须保留 **date sections by displayDate**，
且**不合成** future sections。D-03 之后用 one month / multiple months / selected day /
其它显式 range，均属于 **D-03 contract decision**（刻意不预先冻结 History 的 month-at-a-time UX）。

## 14. Empty-date behavior（冻结）

`HistoricalRange` 可以没有某日期的 row。D-01 **不得**合成 fake empty rows/sections；
只暴露由 supplied historical entries 派生的**非空** date sections。
Selected-date empty-state 行为属于 D-03/D-04。

---

## 15. Read-only（冻结）

D-01 不含任何：edit、delete、undo、record、quick record、navigation action、repository write。
Phase-D row actions 未被 D1–D5 要求，out of D-01；既有 History/Record 写入路径保持权威。

---

## 16. No C-04 coupling（冻结）

Timeline **不得**消费：`history/retrospective/**`、`RetrospectivePkResult`、
`ScheduleContextMarker`、`RecordedIntakeMarker`、C-04 marker membership、C-04 720h window、
cursor、PK series。Phase D 共享的是 **Phase-A historical facts**，不是 Phase-C presentation logic。

---

## 17. Proposed production package（规划值；实现评审最终确认）

```text
app/src/main/java/.../history/timeline/
    TimelineReadModel.kt        (TimelineReadModel, TimelineDay, TimelineRow, TimelineRowId)
    TimelineProjectionBuilder.kt
```

最小纯类型等价于：`TimelineReadModel`、`TimelineDay`、`TimelineRow`、`TimelineRowId`、
`TimelineProjectionBuilder`。**无新依赖。**

---

## 18. Required D-01 acceptance tests（冻结；后续实现轮必须实现）

| # | requirement |
|---|---|
| TLM1 | matched entry -> 恰好一条 MATCHED row |
| TLM2 | unrecorded entry -> 恰好一条中性 UNRECORDED_SCHEDULE row |
| TLM3 | unmatched event -> 恰好一条 UNMATCHED_INTAKE row |
| TLM4 | `futureOccurrences` -> 零 Timeline rows |
| TLM5 | matched / unrecorded 的 rowId = `Occurrence(occurrenceId)` |
| TLM6 | unmatched 的 rowId = `Event(eventId)` |
| TLM7 | `UNRECORDED → MATCHED` 保持 occurrence-backed rowId |
| TLM8 | `MATCHED → UNRECORDED` 保持 occurrence-backed rowId |
| TLM9 | matched 保持 scheduled-context 与 recorded-intake 事实分离 |
| TLM10 | matched 的 actual dose/identity 来自 event 侧，而非 schedule 侧 |
| TLM11 | unrecorded 只携带 schedule-context 事实 |
| TLM12 | unmatched 只携带 recorded 事实 |
| TLM13 | KNOWN identity 经既有 classifier 保留 |
| TLM14 | PARTIAL 不变成具体 medication 名称 |
| TLM15 | UNAVAILABLE/ANTIANDROGEN 不获得发明的 identity |
| TLM16 | displayDate 与历史投影逐日完全一致 |
| TLM17 | canonical 升序（date/instant）ordering |
| TLM18 | 同 instant occurrence-backed 行遵循 `OCCURRENCE_ORDER` 字段序列 |
| TLM19 | occurrence-backed vs unmatched 同 instant 的确定性（occurrence 在前） |
| TLM20 | unmatched 同 instant 行按 eventId 确定性 |
| TLM21 | 无 delta 字段 / 无 timing classification |
| TLM22 | 无合成空 row/section |
| TLM23 | 无 future rows |
| TLM24 | 无 write 行为 |
| TLM25 | 相同输入重复投影输出确定（逐行、逐序） |

加上架构/静态 guards，证明不存在：DAO/repository/Room、`HistoryReadService`、matcher/generator、
Retrospective/C-04、PK、Compose/navigation/resources、`Duration.between` timing metric、writes。

---

## 19. Acceptance mapping（不夸大）

| acceptance | D-01 状态 |
|---|---|
| D1 | **direct foundation / targeted by D-01** |
| D2 | **deterministic ordering foundation / targeted by D-01** |
| D3 | **displayDate/date-section consistency foundation / targeted partially**；loading/range UX 仍属 D-03 |
| D4 | **NOT completed by D-01** |
| D5 | **NOT completed by D-01** |

**Phase D 与 D1–D5 不因本契约而完成。**

---

## 20. Explicit D-01 non-goals（冻结 OUT）

Compose UI · navigation · ViewModel · date-range loading policy · pagination · future occurrences ·
row actions · localized strings · 12/24h formatting · font-scale work · signed timing delta ·
early/late/on-time · adherence/compliance · coverage percentage · historical prescription
reconstruction · anti-androgen identity expansion · CPA PK · 任何 PK calculation。

---

## 21. Contract guards（违反 ⇒ STOP）

| # | forbidden |
|---|---|
| F1 | 第二 matcher |
| F2 | 第二 occurrence generator |
| F3 | repository / DAO / Room |
| F4 | 新的持久化 Timeline truth/identity store |
| F5 | C-01 / C-04 依赖 |
| F6 | PK 逻辑 |
| F7 | future rows |
| F8 | timing delta |
| F9 | early/late/on-time/adherence |
| F10 | historical prescription claim |
| F11 | identity guessing |
| F12 | write path |
| F13 | D-01 内 UI/navigation/range-loading policy |
| F14 | D-01 read model 内的 formatted/localized strings |
| F15 | 新依赖 |

---

## 22. Frozen cross-phase boundaries

- Phase A historical projection = authoritative source；
- Phase C = **CLOSED**；C-01 / C-04 不被触碰、不被依赖（§16）；
- v1.7 final T*/DST release gates 保持独立（Phase C closure 结论延续）；
- D-01 不改变任何既有语义（SPEC §2.2）；无 schema 变更。

---

## 23. Documentation status

- 本契约状态：`CONTRACT — APPROVED / FROZEN`；
- D-01 production：**APPROVED / CLOSED** @ `97838fbf7c8692ada9d44d8401a6008283fac178`
  （approved contract HEAD `a245a5ec7a2dcd977ff0de3b3a79c8b129f67e34`；evidence
  `docs/evolune/v1.7/evidence/d-01/`）；**No further D-01 production changes are authorized
  without reopening review.**
- Phase D：IN PROGRESS；**D-02…D-07 NOT STARTED / unreconciled after D-01**（下一步为
  Phase-D post-D01 remaining-scope audit / D-02…D-03 reconciliation）。
- 指针更新仅限 `TODO.MD` / `CURRENT_STATUS.md` / `ROADMAP.md` / `V17_PLAN.md` 的最小状态行。

### 23.1 Implementation-review P3 dispositions（recorded only；不修改生产）

- D-01 的 Gradle 证据日志恰好以 UTF-16-LE 存储；历史文件保持原样不动，未来 evidence logs
  宜优先使用 UTF-8。
- `compareAtSameInstant` 的防御性非空断言由 Timeline row-construction invariant 支撑；
  非阻塞，不为此改动生产。
