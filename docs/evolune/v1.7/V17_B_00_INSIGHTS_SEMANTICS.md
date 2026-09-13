# V17-B-00 — Adherence & Medication Insights — Semantics Freeze

> 状态：`SEMANTICS FROZEN / DESIGN GATE`（**无生产代码**）
> Round：v1.7-B / **B-00**（语义冻结、数据资格审计、指标定义、子阶段计划）
> 起始 HEAD：`40eb9f230536e600843fde6bb5896cdb26128973`（A-04 APPROVE，**PHASE A CLOSED**）
> 依据（§1 要求逐字重读）：[`V17_SPEC.md`](V17_SPEC.md)（§2 核心架构规则、§3 v1.7-B 候选指标、§8 边界、§10 UI 原则、
> §12 Invariants，以及 Phase-0 **Decision A/B/C/D/E/F**、v1.7-A **Decision G/H**）·
> [`V17_PLAN.md`](V17_PLAN.md) §5 · [`V17_ACCEPTANCE.md`](V17_ACCEPTANCE.md) §2（B1–B7、B2a/B2b/B2c）·
> [`V17_DATA_SEMANTICS.md`](V17_DATA_SEMANTICS.md)（§0 四阶段匹配、§4/§5/§9/§10/§11/§12、§13 未决清单）·
> [`V17_A_02_HISTORY_READ_MODEL.md`](V17_A_02_HISTORY_READ_MODEL.md) · [`V17_A_03_HISTORY_UI.md`](V17_A_03_HISTORY_UI.md) ·
> [`V17_A_04_HARDENING.md`](V17_A_04_HARDENING.md)（gate matrix、anti-androgen 身份债务）
> 配套证据：[`evidence/b-00/`](evidence/b-00/)

---

## 0. 范围与首要规则（Phase-B cardinal rule）

**范围**：本轮只做语义冻结/审计/定义/计划。**不写任何 Insights 生产代码**
（无 Insights class、无 ViewModel、无 Compose、无 Room/DAO/schema、无 chart 依赖）。

**首要规则（冻结）**：Insights 只能从 Phase A 已冻结的历史事实派生，**不得建立第二套 medication truth**：

```
authoritative Room facts (dose_events / medication_plans / scheduled_dose_slots)
        ↓  （唯一匹配实现：MedicationOccurrenceMatcher 四阶段）
HistoricalProjection            ← Phase A 冻结
        ↓
HistoricalReadModel (day / range)
        ↓
Insights derivation             ← Phase B 只在这里新增纯聚合
        ↓
Insights UI
```

禁止（Invariant 1/2/5 + 规格 §2）：
Insights → DAO/Room 直连；Insights → 原始 `DoseEvent` 自行匹配；Insights → 重新生成另一套 history truth；
Insights 读取路径产生任何写入；Insights 重新计算 display date / provenance。

---

## 1. Data sources（只读审计）

| 来源 | 内容 | 是否可进入 Insights | 说明 |
|---|---|---|---|
| `dose_events` | 权威实际摄入（`DoseEventSource`：`MANUAL`/`REMINDER`/`WEAR`/`WIDGET`/`JSON_V1`/`LEGACY`；`DoseEventStatus` 只有 `RECORDED`） | **间接**（经投影） | 不得被 Insights 直接查询 |
| `medication_plans` + `scheduled_dose_slots` | **当前**计划（无版本历史） | **间接**（经投影） | 只能表达 `Current schedule context`（Decision A） |
| `HistoricalProjection` | `entries`（三类 `HistoricalEntry`）+ `futureOccurrences` | **是**（唯一入口） | Phase A 冻结；三分支穷尽性由 builder runtime invariant 保证 |
| `HistoricalReadModel` | `HistoricalDay`（recorded/unrecorded/unmatchedActual 计数 + entries）、`HistoricalRange` | **是** | 区间/日期归属的冻结语义 |
| `ReminderSkipStore` | 48 小时过期的非权威 skip 状态（SharedPreferences） | **否** | Decision C：不得成为长期历史事实 |
| `wear_app_confirmation_operations` journal | 撤销幂等日志 | **否** | 不是用药事实（DATA_SEMANTICS §9） |
| Widget / Wear 协议 | 只经同一条权威写入路径落库 | 经 `dose_events` 间接 | Invariant 6 |

**审计结论**：Phase B 所需的全部事实都能从 Phase A 投影取得；**不需要**任何新表、新列或第二套派生层。

---

## 2. Historical fact taxonomy（字段级，非按名称猜测）

| 类型 | 定义 | 关键字段 | 是否 `HistoricalEntry` |
|---|---|---|---|
| `MatchedHistoricalOccurrence` | 权威事件与某个生成 occurrence 绑定 | `occurrence`（`scheduledAt`/`scheduledLocalDateTime`/`zoneId`/`presentation.planName`/`matchKey`）、`event`（`occurredAt`/`slotId`/`matchKey`/`source`/`localDate`/`zoneId`）、`matchProvenance`、`status`、`actionAvailability`、`crossesLocalDateBoundary`、`displayDate`、`displayDateProvenance` | 是 |
| `UnrecordedHistoricalOccurrence` | 生成的 occurrence 在可用数据中**没有**匹配记录 | `occurrence`、`status`、`actionAvailability`、`scheduleTimeContext`（仅 `CURRENT_SCHEDULE_CONTEXT`）、`displayDate`、`displayDateProvenance` | 是 |
| `UnmatchedHistoricalIntake` | 权威事件未被任何 occurrence 认领 | `event`、`source`、`isManualIntake`（仅 `MANUAL` 为 true）、`displayDate`、`displayDateProvenance` | 是 |
| `FutureOccurrenceContext` | 未到达（`scheduledAt > now`）且无匹配记录的 occurrence | `occurrence`、`status`、`actionAvailability`、`scheduleTimeContext` | **否**（不在 `entries`） |

**匹配实现（唯一）**：`MedicationOccurrenceMatcher` 四阶段 → `MedicationMatchProvenance`：

| 阶段 | 事件形态 | 归属条件 | provenance |
|---|---|---|---|
| 1 | `slotId != null && localDate != null` | slot 相同 **且** persisted `localDate` == occurrence 本地日期（**不看时间距离**） | `EXACT_SLOT_AND_LOCAL_DATE` |
| 2 | `slotId != null && localDate == null` | slot 相同 且 时间差 ∈ `[-1h, +1h]` 闭区间 | `SLOT_WINDOW_WITHOUT_LOCAL_DATE` |
| 3 | `slotId == null` | 时间差 ∈ `[-1h, +1h]` 且 route/ester/剂量匹配，候选 occurrence **唯一** | `NULL_SLOT_TIME_WINDOW` |
| 4 | `slotId == null && localDate != null` | 同一本地日期 且 route/ester/剂量匹配、候选唯一，且该事件**从未有过窗口证据** | `NULL_SLOT_SAME_DAY` |

**其它冻结维度**：
`HistoricalDisplayDateProvenance` = `INTENDED_LOCAL_DATE` / `PERSISTED_RECORDING_DATE` / `CURRENT_DISPLAY_TIMEZONE_DERIVED`；
`MedicationOccurrenceStatus` = `UPCOMING`/`DUE`/`RECORDED`/`PAST_UNRECORDED`（**仅作字段携带，不参与历史分类**）；
`HistoricalScheduleTimeContext` 只有 `CURRENT_SCHEDULE_CONTEXT`。

---

## 3. Eligibility matrix（§4 要求，逐项由 contract 推导）

| Historical fact | Count as recorded intake? | Eligible for denominator? | Eligible for timing metric? | Confidence | Notes |
|---|---|---|---|---|---|
| Exact matched（`EXACT_SLOT_AND_LOCAL_DATE`） | **是**（该 event 计一次） | **是**（作为"有匹配记录"的 occurrence） | **仅限** Option-2（未在本轮 ship，见 §10） | **HIGH**（binding） | binding 高置信 ≠ 历史处方真实性（§9） |
| Slot-window inferred（`SLOT_WINDOW_WITHOUT_LOCAL_DATE`） | **是** | **是**（同上，且必须标记 inferred 分层） | 否（本轮不 ship；若 ship 仅 Option-2 且单独资格规则） | **MEDIUM**（binding） | 无 persisted localDate，靠 ±1h 窗口 |
| Null-slot time-window（`NULL_SLOT_TIME_WINDOW`） | **是** | **是**（标记 inferred） | 否（同上） | **MEDIUM**（binding） | 现代 quick-record 亦走此阶段（A-04 已证） |
| Null-slot same-day（`NULL_SLOT_SAME_DAY`） | **是** | **是**（标记 inferred） | **否** | **LOW**（binding） | 仅同日 + 唯一候选；不得作为高置信输入 |
| Unrecorded historical occurrence | **否**（没有发生过的摄入事实） | **是**（作为"无匹配记录"的 occurrence） | 否 | N/A | **不得**解释为 missed/skipped/forgot（§6） |
| Unmatched actual intake | **是**（真实发生） | **否**（无 occurrence binding，不进 plan-based 分母） | 否（无 `scheduledAt` 可比） | **NONE**（binding） | 可进 recorded totals / source 分布 / 剂量总量 |
| `FutureOccurrenceContext` | **否** | **否** | **否** | N/A | 不是 `HistoricalEntry`，完全不进任何历史指标（§8） |
| Legacy orphan（`localDate == null && zoneId == null` 的未匹配事件） | **是**（是权威事件） | **否** | 否 | **NONE**（binding）+ 日期归属低置信 | `displayDateProvenance == CURRENT_DISPLAY_TIMEZONE_DERIVED`；不得当原始日期（Decision G） |

> 表中"是/否"全部由 Phase A 契约推导，未引入任何新语义。

---

## 4. Match confidence（语义映射，不新增 domain enum）

| Confidence | Provenance | Recorded count | Occurrence-based insights | Timing | 展示要求 |
|---|---|---|---|---|---|
| `HIGH` | `EXACT_SLOT_AND_LOCAL_DATE` | 计入 | 计入 | 仅 Option-2 | 无需 inferred 标注 |
| `MEDIUM` | `SLOT_WINDOW_WITHOUT_LOCAL_DATE`、`NULL_SLOT_TIME_WINDOW` | 计入 | 计入（**必须可分层**） | 仅 Option-2 | 必须携带 inferred 标注（沿用 A-03-R1 中性文案） |
| `LOW` | `NULL_SLOT_SAME_DAY` | 计入 | 计入但**必须可分层**，且不得作为高置信输入 | 否 | 必须携带 inferred 标注 |
| `NONE` | `UnmatchedHistoricalIntake`（无 binding） | 计入（作为实际摄入） | 不进入 plan-based 指标 | 否 | 按真实 source 呈现；仅 `MANUAL` 可称 Manual |

**冻结要点**：confidence 是 **binding 置信度**，不是"历史处方真实性"（§9）；
confidence 不得被用来改写"这是否是一次已发生的权威摄入"这一事实（§5）。

---

## 5. Recorded intake semantics（冻结）

`Recorded intake count` 是**事实型**指标：

- 计数对象 = **权威实际摄入事件**，每个 `dose_events` 行**恰好一次**；
- 包含：matched authoritative intake（四个 provenance 全部）+ unmatched authoritative intake；
- **不因** exact / inferred / manual / reminder / wear / widget / legacy 而改变；
- **不因** plan 是否仍存在、slot 是否被替换、计划是否被编辑而改变；
- 撤销（physical delete）后该行**不存在** → 不计入（§15）。

派生：`days with ≥1 recorded intake` = 具有至少一个 matched 或 unmatched entry 的 `displayDate` 去重计数。
（unrecorded-only 的日期**不计入**。）

---

## 6. Unrecorded occurrence 语义（冻结，且不得直接推出 adherence）

`UnrecordedHistoricalOccurrence` 只代表：

> **No matching intake record exists in the available data.**

不代表：`Missed` / `Skipped` / `Forgot` / `Non-adherent` / `Not taken`。

因此**明确禁止**在当前证据下直接定义：

- `missedCount = unrecordedCount`；
- `adherence = recorded / (recorded + unrecorded)`。

**默认视为不合法**（与规格 §3 Decision A 约束一致）；任何此类 ratio 只有在 §12 的全部前置条件被单独批准后才可讨论。
UI 必须使用中性表述（`无记录` / `No recorded intake` 及其既有披露文案）。

---

## 7. Unmatched actual intake 语义（冻结）

`UnmatchedHistoricalIntake` 是**真实权威摄入**，因此：

- **可以**进入：`recorded intake total`、`source distribution`、`dose totals`（当 identity 足够，见 §13）；
- **默认不得**进入：plan-based 分母、on-time/延迟、任何需要 occurrence binding 的指标；
- 呈现必须按**真实 source**（Decision B）：仅 `source == MANUAL` 可称 Manual / 手动；其余保持自身来源。

---

## 8. FutureOccurrenceContext 排除（冻结）

`FutureOccurrenceContext` **不是** `HistoricalEntry`，因此：

- 不进 `HistoricalProjection.entries`、不进 `HistoricalDay`/`HistoricalRange` 的任何计数；
- 不进 denominator、不进 no-record count、不进任何 adherence/coverage 讨论；
- Phase B **不得**为了"预测/计划"便利把它重新塞进历史指标（Invariant 1/2 + A-03-PRE-01 冻结契约）。

---

## 9. Schedule-authenticity limitation（Phase B 最重要的防误用边界）

Phase A 已冻结（Decision A）：

- **没有** plan revision history、**没有** slot revision history、**没有** historical schedule snapshot；
- 过去日期的 scheduled time 由**当前** plan 重新生成 → 只能表示 `Current schedule context`；
- slot UUID 可跨时间被编辑后继续保留，因此**无法检测**"某次 intake 之后 slot 被改过"。

因此必须区分两个独立维度：

| 维度 | 含义 | 能否证明 |
|---|---|---|
| **Occurrence binding confidence** | event ↔ **生成出来**的 occurrence 的绑定可靠性（§4） | **能**（四阶段 provenance + 确定性测试） |
| **Historical schedule authenticity** | 该生成的 occurrence **就是**当时真实的处方计划 | **不能**（无版本/无快照） |

**冻结推论**：
1. 即使 `EXACT_SLOT_AND_LOCAL_DATE`，也只证明"event ↔ generated occurrence"，**不**证明"generated occurrence == 历史处方 occurrence"；
2. 因此 `scheduled occurrence` 类计数必须被描述为**当前计划上下文中的生成 occurrence**，而不是"当时应服用的计划"；
3. 任何"历史符合度/准时率"的强语义都缺少可证前提。

---

## 10. Timing metric decision（**Option 1 — 本轮 ship 版**）

**决策：v1.7-B（B-01..B-04）不提供任何 historical punctuality / on-time / delay 指标。**

理由：`observedDelta = actual occurredAt − generated occurrence scheduledAt` 在数学上可算，
但在 §9 前提下，它只能表示"与**当前**计划上下文的差"，而不是历史服药延迟；Decision A 明确禁止
在无法证明历史 planned snapshot 时把它展示成确定的历史延迟。

**Option 2（探索性 timing）不在本轮 ship，且被精确定义为需单独批准**：

- 名称/文案**只能**是 `Compared with current schedule context`（中文等价："与当前方案时间相比"）；
- 资格：仅 `HIGH`/`MEDIUM` binding（`NULL_SLOT_SAME_DAY` 与 unmatched 一律排除）；
- 展示：必须同时展示 generated scheduled time + 其 `Current schedule context` 标注，不得出现 `On time`/`Late`/`准时`/`延迟`；
- 前置：单独的产品警示（说明计划可被追溯改写）+ 独立复审批准。

→ 结论**不含糊**：本轮 ship Option 1；Option 2 是注册在案、条件完备但**未启用**的候选。

---

## 11. Adherence 术语裁决（**B — 不对用户展示 clinical adherence 百分比**）

| 选项 | 裁决 |
|---|---|
| A 允许 `adherence` | **不采用**（缺少可证 denominator 与历史处方真实性，§9） |
| **B 采用** | v1.7 不展示临床含义的 adherence percentage；改用中性术语 |

**允许的用户可见术语（冻结）**：`Recorded` / `No recorded intake` / `Record coverage` / `Intake history` /
`Recording consistency` / `Intake activity` / `Recorded intakes` / `Days with recorded intake` / `By source`（来源分布）。

**禁止的用户可见术语（冻结）**：`adherence` / `adherence rate` / `compliance` / `missed` / `skipped` /
`forgot` / `non-adherent` / `on time` / `late` / `overdue` / `failed`，以及中文 `依从`/`依从率`/`漏服`/`漏吃`/
`跳过`/`忘记`/`未依从`/`准时`/`延迟`/`逾期`。

> 唯一例外（沿用 Phase A 冻结文案）：`无记录` 的**中性免责声明**必须保留（A-03 `history_note_not_necessarily_missed`），
> 它显式否定"没记录=漏服"的解读。路线图阶段名为 *Adherence Insights* **不构成** UI 必须输出 adherence % 的义务。

---

## 12. Percentage denominator definitions（冻结数学定义；本轮**不 ship 任何 percentage**）

**B-00 决定**：v1.7-B 的 B-01..B-04 **不输出任何百分比**，只输出计数与分布。
原因：ratio 的分母必然来自**当前计划重新生成**的 occurrence（§9），其历史真实性不可证；
展示百分比会被读成"应服用的比例"。为保证"不含糊"，此处仍冻结其**完整数学定义**，供未来单独批准后直接实现：

```
numerator   = |{ e ∈ HistoricalEntry : e is MatchedHistoricalOccurrence
                  ∧ e.displayDate ∈ [startDate, endDate]
                  ∧ bindingConfidence(e.matchProvenance) ∈ approvedTiers }|
denominator = numerator
            + |{ e ∈ HistoricalEntry : e is UnrecordedHistoricalOccurrence
                  ∧ e.displayDate ∈ [startDate, endDate]
                  ∧ identityEligible(e.occurrence) }|
excluded    = FutureOccurrenceContext（非 HistoricalEntry，构造上不出现）
            ∪ UnmatchedHistoricalIntake（无 occurrence binding；单独作为 recorded intake 计数）
            ∪ bindingConfidence ∉ approvedTiers 的 matched entries
            ∪ identity 不可用的 occurrence（仅身份相关指标）
            ∪ 区间外 displayDate
confidence requirement = approvedTiers ⊆ {HIGH, MEDIUM}（默认仅 HIGH；LOW 永不进入）
time range  = LocalDate inclusive [startDate, endDate]，与 HistoryReadService 一致（§14）
timezone attribution = 一律使用 entry.displayDate（不得由 Insights 重新按 zone 推导）
label       = 若未来获批，只能使用 "Record coverage"，且必须携带
              "generated from the current plan; no recorded intake does not prove the dose was not taken" 披露
```

**明确禁止**的写法：`denominator = all generated occurrences`（不解释当前计划重建的局限）——违反 Decision A。

---

## 13. Medication identity eligibility（身份三态 + anti-androgen 债务）

**来源事实**：`matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount)`；
生产映射 `MedicationPlan.toMedicationSchedule()` / `DoseEvent.toRecordedMedicationEvent()` 一律设
`medicationKey = ester.name`（**酯类槽位**），而 anti-androgen 的真实药物在 `extras[ExtraKey.ANTI_ANDROGEN_TYPE]`，
**投影不携带 extras**（A-04 已登记为 presentation-model debt）。

| 身份状态 | 判据 | 指标处理 |
|---|---|---|
| **identity known** | `routeKey != ANTIANDROGEN` 且 `medicationKey ∈ {E2,EB,EV,EC,EN}` | 可进入按药物的剂量总量/分布；标签用既有 `ester_*` 字符串 |
| **identity partial** | `medicationKey` 非上述集合（未知/外来值）或 `routeKey` 无法映射 | 只以**原始 key** 显示或归入 `Unknown`；不得映射成某个具体药物 |
| **identity unavailable** | `routeKey == ANTIANDROGEN` | **不得**显示 `E2`/任何酯类名（A-04 已取消 raw-key 回落）；仅按 route + dose 呈现，或整体归入 `Unknown (anti-androgen)` |

冻结：per-medication chart / per-drug dose total / medication comparison **只有在 identity known 时**才可出现；
`Ester.E2` 是**槽位占位符**，绝不能据以推断抗雄药身份。真实修法需投影携带 anti-androgen identity（`A-04/B` 债务）。

---

## 14. Date / timezone aggregation（冻结）

- 历史日归属**唯一**来源：`HistoricalEntry.displayDate`；
- Insights **不得**执行 `occurredAt.atZone(systemDefault()).toLocalDate()` 或任何等价重算；
- `displayDateProvenance == CURRENT_DISPLAY_TIMEZONE_DERIVED` 必须被标记为**低置信时区归属**
  （Decision G：不是原始当地日期；不得作为高置信输入，也不得用于责任性指标）；
- "intakes per day" 一律按 `displayDate` 聚合（与 History 一致）；
- actual 时间戳的**渲染**沿用 A-03-UI-R1 冻结规则（persisted `zoneId` 优先，缺失时用当前 displayZone；
  实际当地日期 ≠ entry displayDate 时显示完整日期）——Insights 不得另立一套时间格式化。

---

## 15. Undo / delete 语义（冻结）

- 撤销 = **物理删除行**（A6 冻结）：删除后的 event **不进入**任何 Insights；
- **不存在** historical tombstone；仓库**无法表达**"曾记录后撤销"（DATA_SEMANTICS §9）；
- 因此 Phase B **禁止**任何 `undo count` / `retracted intake` / `was recorded then removed` 类指标；
- Insights 的读取路径零写入（Invariant 5 / A7 冻结），且撤销后重算必须反映新事实；
- 缓存只允许"完全可由权威数据重建"（规格 §2.1）。

---

## 16. Source × match provenance 分离（二维，冻结）

`MedicationIntakeSource`（事件来源，权威）：`MANUAL` / `REMINDER` / `WEAR` / `WIDGET` / `JSON_V1` / `LEGACY`。
`MedicationMatchProvenance`（绑定方式）：`EXACT_SLOT_AND_LOCAL_DATE` / `SLOT_WINDOW_WITHOUT_LOCAL_DATE` /
`NULL_SLOT_TIME_WINDOW` / `NULL_SLOT_SAME_DAY`（unmatched = 无绑定）。

| source ＼ provenance | exact | slot-window | null-slot window | null-slot same-day | unmatched |
|---|---|---|---|---|---|
| `MANUAL` | 可能 | 可能 | 可能（quick-record） | 可能 | 可能（仅此处可称 Manual） |
| `REMINDER` | 可能（planned day persisted） | 可能 | — | — | 可能 |
| `WEAR` | 可能 | 可能 | — | — | 可能 |
| `WIDGET` | 可能 | — | — | — | 可能 |
| `JSON_V1` | 可能 | 可能 | — | — | 可能 |
| `LEGACY` | 仅当有两列时 | 可能 | 可能 | 可能 | 可能（legacy orphan） |

冻结：**禁止**把非 `MANUAL` 统一写成 Manual；**禁止**把 `LEGACY`（source）与 inferred（provenance）混为一谈——
两个维度必须分别可断言、可分层展示。

---

## 17. Initial metric set（Phase-B 第一版，全部为事实型）

| # | Metric | 定义 | 资格/排除 |
|---|---|---|---|
| A | `Recorded intakes` | 区间内权威实际摄入事件计数（matched + unmatched，按 event id 去重） | 区间按 `displayDate`；撤销后不计 |
| B | `Days with recorded intake` | 具有 ≥1 个 matched 或 unmatched entry 的 `displayDate` 去重计数 | unrecorded-only 日不计 |
| C | `By source` | 按 `MedicationIntakeSource` 分组计数（matched + unmatched 均计入） | 六类真实来源，不合并、不重命名 |
| D | `Dose totals by medication` | 按身份分组累计 `matchKey.doseAmount`（mg） | **仅** identity known；partial → `Unknown`；anti-androgen → 不显示酯类名 |
| E | `Record coverage（计数形式）` | `occurrences with a matching record` 与 `occurrences with no matching record` 两个**计数** | 只计数不输出百分比（§12）；必须携带"生成自当前计划"披露；`CURRENT_DISPLAY_TIMEZONE_DERIVED` 单独标注 |
| F | `Binding confidence breakdown`（可选，分层用） | 按 `HIGH`/`MEDIUM`/`LOW`/`NONE` 分组计数 | 纯分组，不产生评分 |

上述六项的计数在区间上必须遵守 §19 的范围语义，并复用 `HistoricalReadModel.range(...)` 的 day/entry 结构。

---

## 18. Explicitly forbidden metrics / wording（冻结）

**指标层面禁止**：`adherence rate`/`compliance`/`missed count`/`skipped count`/`forgot count`/
`on-time percentage`/`late count`/`delay from plan`/`uncompleted`（作为"未完成"语义）/
`undo count`/`retracted intake`/依从性评分或百分比/任何 red-green 评分。

**措辞层面禁止**：`Missed`/`Skipped`/`Forgot`/`Non-adherent`/`Late`/`Overdue`/`On time`/`Failed`/
`依从`/`漏服`/`漏吃`/`跳过`/`忘记`/`未依从`/`准时`/`延迟`/`逾期`。

**同时禁止**："没记录"被渲染为沉默的负向信号（颜色/图标/排序暗示"未完成"）——Ambiguity must stay ambiguous
（Invariant 4）。

---

## 19. Range semantics（冻结）

- 全部基于 **`LocalDate` inclusive `[startDate, endDate]`**，与 `HistoryReadService`/`HistoricalRange` 一致；
- 预设：`7 days` / `30 days` / `90 days` / `current month`（= `[月初, min(月末, today)]`）/ custom（同规则）；
- `endDate` 不得晚于 `today`（未来日期不是历史）；
- Insights **不得**自行发明 instant 区间；跨时区/跨 DST 一律由 `displayDate` 归属（§14）；
- 区间端点、空日、DST 日的行为必须与 History 的既有 range 语义**逐项一致**（B-02 需交叉断言）。

---

## 20. Open decisions（B-00 结束时仍开放，需后续批准）

| # | 开放项 | 现状 | 需要的批准 |
|---|---|---|---|
| 1 | Option-2 timing metric（"与当前方案时间相比"） | 已定义资格/文案/前置，**未 ship** | 单独产品警示 + 独立复审 |
| 2 | `Record coverage` 的百分比形式 | 数学定义已冻结（§12），**未 ship** | 需批准 denominator 披露方式与 approvedTiers |
| 3 | anti-androgen 真实身份 | 身份 unavailable（投影不携带） | 需 domain 字段（`A-04/B presentation model debt`） |
| 4 | Insights 的日/周粒度与图表形式（B-03） | 未决 | B-03 设计轮 |
| 5 | 缓存策略（若引入） | 未决 | 必须满足"完全可由权威数据重建" |

---

## 21. B 阶段子阶段计划（B-01…B-04，含与 V17_PLAN §5 的映射）

| 子阶段 | 内容 | 覆盖 PLAN §5 的 | 出口判据 |
|---|---|---|---|
| **B-01 Insights Domain**（read-only aggregate） | 纯 Kotlin 聚合：区间 → 计数/分布/剂量/coverage 计数；只消费 `HistoricalProjection`/`HistoricalReadModel`；零写入 | B-01（语义，已由本文件完成）+ B-02 + B-03 + B-04（medication breakdown） | 确定性单测：区间端点、DST、跨时区、legacy/孤儿/手动/歧义数据集逐项断言；无 DAO 依赖（边界测试） |
| **B-02 Insights ViewModel** | range 选择/加载/状态；复用 `HistoryRangeSource` 读缝与 A-04 refresh/cancellation 语义 | B-03（ranges 的交互面） | 查询计数测试（切范围一次加载、无 per-item 查询）、错误/重试、状态恢复 |
| **B-03 Insights UI** | cards + charts；术语白名单；无百分比（本轮）；无障碍与本地化 | B-05 | Compose 测试 + 文案审查（禁用词扫描）+ Preview |
| **B-04 Phase-B hardening** | 回归/instrumentation/accessibility/evidence | B-06 + B-07 + B-08 | fresh JVM + affected-surface instrumentation + full Phone + 证据 manifest；独立复审包 |

> B-00 自身对应 PLAN §5 的 **B-01 metric semantics**（本文件即其产出）。
> 若 B-01 实施时发现某个指标仍不可证，应**删除或降级该指标**，不得回头修改 Phase A domain（§25 停止条件）。

---

## 22. 结论摘要（给复审）

1. Phase A 投影**足以**支撑一组完全事实型的 Insights；**不需要**新表/新派生层；
2. 只有 **binding confidence** 可证；**historical schedule authenticity 不可证** → 不做历史准时率；
3. 因此 v1.7-B 第一版：**无 adherence 百分比、无 punctuality**，只有计数/分布/剂量/coverage 计数；
4. `UnrecordedHistoricalOccurrence` 保持中性语义，**禁止** `missed = unrecorded` 与 `recorded/(recorded+unrecorded)`；
5. `FutureOccurrenceContext` 完全不进历史指标；unmatched intake 进事实计数但不进 plan-based 分母；
6. source 与 provenance 两个维度分离且都必须可断言；anti-androgen identity 不可用即不猜。
