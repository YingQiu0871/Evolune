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
`MatchedHistoricalOccurrence.crossesLocalDateBoundary`（**presentation/context 字段**）：表示 event 的本地日期 ≠ occurrence 的
display date；它 **不改变 B-00 eligibility**、**不能**用于 historical schedule authenticity 判断，
也不决定 actual timestamp 的格式（后者由 A-03-UI-R1 的 `actualTimestampPresentation` 冻结）。
`HistoricalDisplayDateProvenance` = `INTENDED_LOCAL_DATE` / `PERSISTED_RECORDING_DATE` / `CURRENT_DISPLAY_TIMEZONE_DERIVED`；
`MedicationOccurrenceStatus` = `UPCOMING`/`DUE`/`RECORDED`/`PAST_UNRECORDED`（**仅作字段携带，不参与历史分类**）；
`HistoricalScheduleTimeContext` 只有 `CURRENT_SCHEDULE_CONTEXT`。

---

## 3. Eligibility matrix（§4 要求，逐项由 contract 推导；列已拆分以便实现）

> **P2-4 修正（B-00-R1）**：原 "Eligible for denominator?" 一列语义过载，现拆为
> **Coverage-count eligible?**（第一版 shipped 的计数）与 **Future-ratio eligibility**（未 ship 的比例定义，
> 见 §12）。两件事不同：计数描述 **projection linking**，比例才需要 `approvedTiers` 过滤。

| Fact | Recorded intake? | Coverage-count eligible? | Future-ratio eligibility | Timing eligibility | Confidence |
|---|---|---|---|---|---|
| Exact matched（`EXACT_SLOT_AND_LOCAL_DATE`） | **yes**（该 event 计一次） | **yes**（进 matched count） | **yes** when `HIGH ∈ approvedTiers` | 仅 Option-2（未 ship） | `HIGH`（binding） |
| Slot-window inferred（`SLOT_WINDOW_WITHOUT_LOCAL_DATE`） | **yes** | **yes** | only if `MEDIUM ∈ approvedTiers` | 仅 Option-2（未 ship） | `MEDIUM` |
| Null-slot time-window（`NULL_SLOT_TIME_WINDOW`） | **yes** | **yes** | only if `MEDIUM ∈ approvedTiers` | 仅 Option-2（未 ship） | `MEDIUM` |
| Null-slot same-day（`NULL_SLOT_SAME_DAY`） | **yes** | **yes** | **no**（frozen ratio 中 LOW 永不进入） | **no** | `LOW` |
| Unrecorded historical occurrence | **no** | **yes**（进 no-record count） | **yes**（进 denominator） | **no** | 无 binding confidence（不进 confidence breakdown，见 §25） |
| Unmatched actual intake | **yes** | **no** | **no** | **no**（无 `scheduledAt` 可比） | `NONE`（binding） |
| `FutureOccurrenceContext` | **no** | **no** | **no** | **no** | n/a |
| Legacy orphan（`localDate == null && zoneId == null` 的未匹配事件） | **yes** | **no** | **no** | **no** | `NONE`（binding）+ 低置信日期归属 |

**冻结说明**：
1. coverage **计数**接受全部四个 matched provenance（它描述的是投影是否把 event 连接到 occurrence），
   但必须能同时展示 **binding confidence breakdown**（§25）以便让 LOW/inferred 的质量透明；
2. **比例**（若未来启用）才应用 `approvedTiers` 过滤 —— 这解决了原 §3 与 §12 的冲突；
3. **medication identity 不参与 coverage eligibility**（identity 只影响 medication-specific 指标，见 §13）；
4. `Timing eligibility` 一列在本轮**全部为"未 ship"**：v1.7-B 不输出任何 punctuality/on-time/delay（§10）。

## 4. Match confidence（语义映射，不新增 domain enum）

| Confidence | Provenance | Recorded count | Occurrence-based insights | Timing | 展示要求 |
|---|---|---|---|---|---|
| `HIGH` | `EXACT_SLOT_AND_LOCAL_DATE` | 计入 | 计入 | 仅 Option-2 | 无需 inferred 标注 |
| `MEDIUM` | `SLOT_WINDOW_WITHOUT_LOCAL_DATE`、`NULL_SLOT_TIME_WINDOW` | 计入 | 计入（**必须可分层**） | 仅 Option-2 | 必须携带 inferred 标注（沿用 A-03-R1 中性文案） |
| `LOW` | `NULL_SLOT_SAME_DAY` | 计入 | 计入但**必须可分层**，且不得作为高置信输入 | 否 | 必须携带 inferred 标注 |
| `NONE` | `UnmatchedHistoricalIntake`（无 binding） | 计入（作为实际摄入） | 不进入 plan-based 指标 | 否 | 按真实 source 呈现；仅 `MANUAL` 可称 Manual |

**冻结要点**：confidence 是 **binding 置信度**，不是"历史处方真实性"（§9）；
confidence 不得被用来改写"这是否是一次已发生的权威摄入"这一事实（§5）。

**P3-16 修正（B-00-R1）**：confidence breakdown **只统计 recorded intakes**，bucket 恰好四个：
`HIGH` / `MEDIUM` / `LOW` / `NONE`。`UnrecordedHistoricalOccurrence` **没有** binding confidence，
**不进入** confidence breakdown（不设 "n/a (no record)" bucket）。

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

**B-00 决定**：v1.7-B 的 B-01..B-04 **不输出任何百分比**，只输出计数与分布（§17、§24）。
原因：ratio 的分母必然来自**当前计划重新生成**的 occurrence（§9），其历史真实性不可证；
展示百分比会被读成"应服用的比例"。为保证"不含糊"，此处冻结其**完整数学定义**，供未来单独批准后直接实现。

**P2-3 修正（B-00-R1）**：删除了原定义中未定义且错误的 `identityEligible(...)` 抽象。
**record coverage 与 medication identity 无关**；coverage eligibility 只由
**historical occurrence class + match state + confidence tier + date range** 决定。

```
eligibleMatched    = { e ∈ HistoricalEntry : e is MatchedHistoricalOccurrence
                       ∧ e.displayDate ∈ [startDate, endDate]
                       ∧ bindingConfidence(e.matchProvenance) ∈ approvedTiers }

eligibleUnrecorded = { e ∈ HistoricalEntry : e is UnrecordedHistoricalOccurrence
                       ∧ e.displayDate ∈ [startDate, endDate] }

numerator   = count(eligibleMatched)
denominator = count(eligibleMatched) + count(eligibleUnrecorded)

excluded    = FutureOccurrenceContext                     （非 HistoricalEntry，构造上不出现）
            ∪ UnmatchedHistoricalIntake                   （无 occurrence binding；作为 recorded intake 单独计数）
            ∪ matched occurrences below approvedTiers     （例如 LOW）
            ∪ out-of-range displayDate

confidence requirement = approvedTiers ⊆ {HIGH, MEDIUM}（默认仅 {HIGH}；LOW 永不进入）
time range             = LocalDate inclusive [startDate, endDate]，与 HistoryReadService 一致（§19）
timezone attribution   = 一律使用 entry.displayDate（不得由 Insights 重新按 zone 推导）
```

**identity 不参与** numerator/denominator/excluded 的任何一步。

### 12.1 这个 ratio **不是**什么（即使未来启用也必须写明）

必须同时展示以下两句（disclosure 不可省）：

- `This is record coverage against generated occurrences in the current schedule context.`
- `No recorded intake does not prove that the dose was not taken.`

**禁止**称它为（英文/中文皆禁）：`adherence %`、`compliance %`、`completion rate`、`missed-dose rate`、
`依从率`、`完成率`、`漏服率`。

**明确禁止**的写法：`denominator = all generated occurrences`（不解释当前计划重建的局限）——违反 Decision A。

**当前状态**：v1.7 **不 ship** 该 ratio（只 ship §17 的计数形式）。

## 13. Medication identity eligibility（**单值**处置 + 剂量规则；B-00-R1 冻结）

**来源事实**：`matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount)`；
生产映射 `MedicationPlan.toMedicationSchedule()` / `DoseEvent.toRecordedMedicationEvent()` 一律设
`medicationKey = ester.name`（**酯类槽位**），而 anti-androgen 的真实药物在 `extras[ExtraKey.ANTI_ANDROGEN_TYPE]`，
**投影不携带 extras**（A-04 已登记为 presentation-model debt）。

### 13.1 身份三态（单值处置，不再有 "raw key 或 Unknown" / "Unknown 或 exclude" 的歧义）

| 身份状态 | 判据 | 用户可见名称 | 能否进入 named-medication aggregate |
|---|---|---|---|
| `identity known` | `routeKey != ANTIANDROGEN` 且 `medicationKey ∈ {E2,EB,EV,EC,EN}` | 既有 `ester_*` 字符串 | **可以**（per-medication dose total / chart / breakdown） |
| `identity partial` | `medicationKey` 不在上述集合（未知/外来值），或 `routeKey` 无法映射 | **`Unknown medication`**（**不得**把 raw key 当作用户可见药名） | **不可以** |
| `identity unavailable` | `routeKey == ANTIANDROGEN`（真实药物不可从投影恢复） | **`Unknown medication`** | **不可以** |

**P2-2 冻结**：`partial` 与 `unavailable` 采用**同一个**用户可见名称 **`Unknown medication`**
（不采用 "Unknown anti-androgen" 分支：更简单的 aggregation model，且同样不猜药名）。
两者都不进入任何 named-medication aggregate；如需区分原因，只能作为**非用户可见**的内部诊断，不作为药名。

### 13.2 剂量规则（B-00-R1 冻结）

- **不提供跨药物的 overall dose total**：不同 medication 的 mg 不可直接相加，没有可比较的临床意义；
- **Per-medication dose total** 只统计 `identity == known`；
  `partial` / `unavailable` **排除**在 per-medication dose totals 之外；
- 另可提供 **`unknownIdentityRecordedIntakeCount`**（**计数**，不是剂量）以保持透明度；
- **不得**建立 `Unknown medication = 350 mg` 这类把不同药物 mg 混在一起的剂量桶。

**冻结**：`Ester.E2` 是**槽位占位符**，绝不能据以推断抗雄药身份；
真实修法需要投影携带 anti-androgen identity（`A-04/B presentation model debt`，不阻塞 B-01）。

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

## 16. Source × match provenance（**writer 级可达性审计**，B-00-R1 重建）

`MedicationIntakeSource`（事件来源，权威）与 `MedicationMatchProvenance`（绑定方式）是**两个独立维度**。
下表**不是**按直觉填写，而是逐一审计真实 production writers 后得出
（证据：`evidence/b-00-r1/source-provenance-reachability.txt`，含每个 writer 的代码位置与字段赋值）。

### 16.1 Production writer 审计（写入 `dose_events` 的全部路径）

| Writer（入口） | source | persisted `slotId` | persisted `localDate` | persisted `zoneId` |
|---|---|---|---|---|
| `DoseEventEditSessionFactory.createNew`（手动新建） | `MANUAL` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `DoseEventEditSessionFactory.createQuickEvent`（快速记录） | `MANUAL` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `ReminderDoseFactory.createReminderDoseEvent`（提醒确认） | `REMINDER` | **occurrence.slotId（非空）** | **计划日** | 有 |
| `WearAppConfirmationHandler.createConfirmationEvent`（Wear App 确认） | `WEAR` | **command.slotId（非空，协议校验 `isNonZero`）** | `command.localDate`（非空） | 有 |
| `WearDoseActionHandler.createWearDoseEvent`（**legacy Tile** 路径） | `WEAR` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `createWidgetDoseEvent`（Widget 快捷动作） | `WIDGET` | **slotId（非空参数）** | `occurredAt` 在该 zone 的日期 | 有 |
| `MahiroV1DomainAdapter`（JSON v1 导入） | `JSON_V1` | **null** | **null** | **null** |
| `DoseEventEntityMapper`（legacy 迁移行） | `LEGACY` | **null** | **null** | **null** |
| `DoseEventEditor` 的 **UPDATE** 路径 | 保持原 source | **保持原 `slotId`**（`original.copy`） | `occurredAtEdited` → 按**编辑 zone** 重算；否则保持原值 | 同上 |

> **对 review 假设的两处更正（以代码为准）**：
> (1) **WEAR 有两条 writer**：App 确认携带**非空** `slotId` + `localDate`（→ exact 可达）；
> legacy Tile 路径 `slotId = null` + `localDate != null`（→ null-slot 阶段可达）。因此"WEAR 一律 slotId = null"不成立。
> (2) **JSON_V1 与 LEGACY 都是 `localDate == null`**，因此 matcher phase 4 对它们**不可达**
> （phase 4 要求 `event.localDate != null`，见 `sameDayNullSlotMatchCandidate` 的首行守卫）。

### 16.2 可达性矩阵（三态记法）

记法：**R** = `REACHABLE`（当前 writer 可产生且 matcher 阶段可绑定）·
**C** = `CONDITIONALLY_REACHABLE`（需满足 matcher 的额外条件，如"从未有过窗口证据"）·
**U** = `UNREACHABLE_BY_CURRENT_WRITER`（**不是** domain invariant；domain 仍接受该组合，未来 writer 可能产生）。

| source ＼ provenance | exact | slot-window（无 localDate） | null-slot time-window | null-slot same-day | unmatched |
|---|---|---|---|---|---|
| `MANUAL`（createNew / quickEvent） | **U**（slotId 恒为 null） | **U**（同上） | **R** | **C**（需同 local date 且该事件此前无窗口证据） | **R** |
| `REMINDER` | **R**（slotId + 计划日） | **U**（`localDate` 恒非空） | **U**（slotId 非空） | **U**（同上） | **R**（例如编辑 occurredAt 后 `localDate` 不再等于任何 occurrence 日期） |
| `WEAR`（App 确认） | **R** | **U** | **U** | **U** | **R** |
| `WEAR`（legacy Tile） | **U** | **U** | **R** | **C** | **R** |
| `WIDGET` | **R**（slotId 非空） | **U** | **U** | **U** | **R** |
| `JSON_V1` | **U** | **U** | **R** | **U**（`localDate == null`） | **R** |
| `LEGACY`（迁移行） | **U** | **U** | **R** | **U**（`localDate == null`） | **R** |

**冻结要求**：
1. B-01 的 eligibility/aggregation **不得**假设某 source 只能落在某个 provenance；
   必须按 **entry 实际携带的 provenance** 判定（矩阵只用于解释"为什么某组合在实践中不出现"）；
2. 三态记法必须保留 **U 与"domain 不允许"的区别**——U 只说明"当前 writer 不产生"；
3. `source == LEGACY` **不等于** provenance inferred：两个维度分别断言（legacy 行既可能 unmatched，
   也可能在特定 matcher 条件下进入 inferred 绑定）；
4. 两个维度都必须可分层展示（§17-C/§25）。

## 17. Initial metric set（Phase-B 第一版，全部为事实型）

| # | Metric | 定义 | 资格/排除 |
|---|---|---|---|
| A | `Recorded intakes` | 区间内权威实际摄入事件计数（matched + unmatched，按 **authoritative event id** 去重） | 区间按 `displayDate`；撤销后不计；若区间含 `CURRENT_DISPLAY_TIMEZONE_DERIVED` 则**必须**继承 §26 的 timezone disclosure |
| B | `Days with recorded intake` | 具有 ≥1 个 matched 或 unmatched entry 的 `displayDate` 去重计数 | unrecorded-only 日不计；继承 §26 disclosure |
| C | `By source` | 按 `MedicationIntakeSource` 分组计数（matched + unmatched 均计入） | 六类真实来源，不合并、不重命名；继承 §26 disclosure |
| D | `Dose totals by medication` | 按身份分组累计 `matchKey.doseAmount`（**mg**，§27） | **仅** identity known；partial / unavailable → **排除**（不建 `Unknown medication = N mg` 桶）；**无**跨药物总量；另可给 `unknownIdentityRecordedIntakeCount`（计数）；按 range 过滤时继承 §26 disclosure |
| E | `Record coverage（计数形式）` | `occurrences with a matching record` 与 `occurrences with no matching record` 两个**计数**（全部 provenance 均进 matched count） | 只计数不输出百分比（§12）；**必须**同时携带 §24 的两句 disclosure + subtitle；必须可展示 §25 confidence breakdown |
| F | `Binding confidence breakdown` | 按 `HIGH`/`MEDIUM`/`LOW`/`NONE` 分组计数（**仅 recorded intakes**，见 §25） | 纯分组，不产生评分；不得设 `n/a (no record)` bucket |

上述六项的计数在区间上必须遵守 §19 的范围语义，并复用 `HistoricalReadModel.range(...)` 的 day/entry 结构。

---

## 18. Explicitly forbidden metrics / wording（冻结）

**指标层面禁止**：`adherence rate`/`adherence %`/`compliance %`/`completion rate`/`missed-dose rate`/`missed count`/`skipped count`/`forgot count`/
`on-time percentage`/`late count`/`delay from plan`/`uncompleted`（作为"未完成"语义）/
`undo count`/`retracted intake`/依从性评分或百分比/任何 red-green 评分。

**措辞层面禁止**：`Missed`/`Skipped`/`Forgot`/`Non-adherent`/`Late`/`Overdue`/`On time`/`Failed`/
`依从`/`漏服`/`漏吃`/`跳过`/`忘记`/`未依从`/`准时`/`延迟`/`逾期`。

**同时禁止**："没记录"被渲染为沉默的负向信号（颜色/图标/排序暗示"未完成"）——Ambiguity must stay ambiguous
（Invariant 4）。

**数据结构层面禁止**：把不同药物混入同一剂量桶（例如 `Unknown medication = 350 mg`）、
以及跨药物的 overall dose total（§13.2）。

---

## 19. Range semantics（**preset 端点冻结**，B-00-R1）

所有区间基于 **`LocalDate`，start 与 end 双端 inclusive**，与 `HistoryReadService`/`HistoricalRange` 一致。

| Preset | 冻结端点（`today` = 当前 display date） | 天数 |
|---|---|---|
| `Last 7 days` | `startDate = today − 6 days`，`endDate = today` | 恰好 **7** 个 LocalDate（含 today） |
| `Last 30 days` | `startDate = today − 29 days`，`endDate = today` | 恰好 **30** |
| `Last 90 days` | `startDate = today − 89 days`，`endDate = today` | 恰好 **90** |
| `Current month` | `startDate = 当月 1 日`，`endDate = today` | 当月 1 日..today |
| `Custom` | `startDate <= endDate <= today`，双端 inclusive | 任意 |

**明确禁止**（冻结）：
- "previous completed 7/30/90 days excluding today"（那是**另一个** preset，如未来需要必须新增命名，不得改变本表定义）；
- rolling **instant** 窗口（`Instant` 半开区间）用于历史指标；
- exclusive end（`endDate` 不含）。

若 UI 未来想提供"上一个完整周期"，属于新增 preset，本表冻结定义不变。

### 19.1 确定性区间示例（today = 2026-09-13）

| Preset | 结果 | 校验 |
|---|---|---|
| `Last 7 days` | `2026-09-07 .. 2026-09-13` | 2026-09-13 − 6d = 2026-09-07；含 today 共 7 日 |
| `Last 30 days` | `2026-08-15 .. 2026-09-13` | 08-15 + 29d = 09-13（8 月 31 日：16 + 13 = 29） |
| `Last 90 days` | `2026-06-16 .. 2026-09-13` | 06-16 + 89d = 09-13（6 月剩 14 + 7 月 31 + 8 月 31 + 13 = 89） |
| `Current month` | `2026-09-01 .. 2026-09-13` | 当月 1 日 .. today |
| `Custom`（合法） | `2026-09-01 .. 2026-09-10` | `startDate <= endDate <= today` |
| `Custom`（非法） | `2026-09-10 .. 2026-09-01` | `startDate > endDate` → 拒绝（fail-fast） |
| `Custom`（非法） | `2026-09-01 .. 2026-09-14` | `endDate > today` → 拒绝（未来不是历史） |

区间端点、空区间（无 entry）、DST 日、跨时区行为必须与 History 的既有 range 语义**逐项一致**（B-01 交叉断言）。

## 20. Open decisions（B-00 结束时仍开放，需后续批准）

| # | 开放项 | 现状 | 需要的批准 |
|---|---|---|---|
| 1 | Option-2 timing metric（"与当前方案时间相比"） | 已定义资格/文案/前置，**未 ship** | 单独产品警示 + 独立复审 |
| 2 | `Record coverage` 的百分比形式 | 数学定义已冻结（§12），**未 ship** | 需批准 denominator 披露方式与 approvedTiers |
| 3 | anti-androgen 真实身份 | 身份 unavailable（投影不携带） | 需 domain 字段（`A-04/B presentation model debt`） |
| 4 | Insights 的日/周粒度与图表形式（B-03） | 未决 | B-03 设计轮 |
| 5 | 缓存策略（若引入） | 未决 | 必须满足"完全可由权威数据重建" |
| 6 | 未来是否新增 "previous completed period" preset | 未决（本表 §19 定义不变） | 新增 preset 需单独批准 |

> **以上开放项均不阻塞 B-01**（见 §28 readiness checklist）。

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

## 22. 结论摘要（给复审，含 B-00-R1 修正）

1. Phase A 投影**足以**支撑一组完全事实型的 Insights；**不需要**新表/新派生层；
2. 只有 **binding confidence** 可证；**historical schedule authenticity 不可证** → 不做历史准时率；
3. v1.7-B 第一版：**无 adherence 百分比、无 punctuality**，只有计数/分布/每药物剂量/coverage 计数；
4. `UnrecordedHistoricalOccurrence` 保持中性语义，**禁止** `missed = unrecorded` 与 `recorded/(recorded+unrecorded)`；
5. `FutureOccurrenceContext` 完全不进历史指标；unmatched intake 进事实计数但不进 plan-based 分母；
6. source 与 provenance 两个维度分离，且**按 writer 级审计**给出三态可达性（§16.2）；
7. 区间 preset 端点冻结（含 today；§19.1 示例），identity 处置单值化（`Unknown medication`），
   剂量规则禁止跨药物 mg 混合（§13.2），coverage 公式删除 `identityEligible` 并附强制 disclosure（§12/§24）；
8. B-01 就绪清单见 §28 —— 全部冻结项已完成，开放项不阻塞 B-01。

---

## 23. ACCEPTANCE §2 B2 / SPEC §3 术语处置表（P3-14）

| Existing candidate term（来源） | v1.7 disposition | 替代/说明 |
|---|---|---|
| `Scheduled`（SPEC §3 候选指标行） | **不作为** historical prescription fact 使用 | 如必须描述 occurrence，用 **`Generated occurrence in current schedule context`** |
| `Completed`（SPEC §3） | **避免** | 用 **`Recorded`** |
| `Uncompleted`（SPEC §3） | **禁止** | 用 **`No recorded intake`** |
| `Completion rate`（SPEC §3 / ACCEPTANCE B2） | **不 ship** | 不重命名为 adherence；比例形式见 §12（未 ship），命名只能是 `Record coverage` |
| `Timing difference`（SPEC §3） | **不 ship in v1.7** | 仅在 Option-2 单独批准后启用（§10） |
| `late` / `missed` / `due` / `expired`（SPEC §3 未批准术语） | **禁止**（保持 SPEC 的"不得自创"约束） | 既有 repository policy 未被采用为历史语义 |

> 该表直接回应 ACCEPTANCE B2（术语语义需经批准）与 SPEC §3 的"未批准术语不得出现"。

## 24. Coverage disclosure（P3-15，对 **shipped 计数**同样适用）

第一版 ship 的 `record coverage counts`（matched historical occurrences / no-record historical occurrences）
**必须同时**展示/定义以下两句，不能等到 ratio 才披露第二句：

1. `Occurrences are generated from the current schedule context.`
2. `No recorded intake does not prove that a dose was not taken.`

推荐英文产品 subtitle（语义冻结，中文 localization 后续）：

> **`Records linked to generated schedule occurrences`**

同时必须可展示 **binding confidence breakdown**（§25），以便让 inferred/LOW 的匹配质量透明。

## 25. Confidence breakdown bucket（P3-16）

- breakdown **只针对 recorded intakes**（matched + unmatched 的绑定置信度）；
- bucket 恰好四个：`HIGH` / `MEDIUM` / `LOW` / `NONE`；
- `UnrecordedHistoricalOccurrence` **不进入** confidence breakdown（无 binding confidence）；
- **不设** `n/a (no record)` bucket（原 worked example 中的该 bucket 已删除）。

## 26. Timezone confidence disclosure（P3-17）

对**所有按 `displayDate` 聚合**的指标，若区间内存在
`displayDateProvenance == CURRENT_DISPLAY_TIMEZONE_DERIVED` 的 entry：

- 聚合模型必须能暴露 **`containsCurrentTimezoneDerivedDates = true`**（或语义等价信息）；
- 后续 UI 可显示中性说明：`Some legacy dates are shown using the current time zone.`

适用范围（**全部**，不只 coverage）：`recorded intakes`、`days with recorded intake`、`by source`、
`confidence breakdown`、以及任何**按 range 过滤**的 `dose totals`
（剂量数值本身不受日期归属影响，但"是否落入该区间"仍由 `displayDate` 决定，因此同样继承 range-level disclosure）。

B-00 只冻结该 requirement，不写代码。

## 27. Dose unit assumption（P3-18）

- 当前持久化/领域模型的剂量字段是 **`doseMG`**（`DoseEvent.doseMG`、`MedicationPlan.doseMG`，
  Room 列 `doseMG REAL`，见 `app/schemas/.../3.json`）；Wear 协议与导入适配器传递的也是同一 mg 数值；
- 因此 **v1.7 frozen model currently expresses dose in mg**；
- 这是**当前模型的描述**，**不是**"永久只有 mg"的架构保证：未来若引入单位/剂型维度，需按
  SPEC §2.2 的四步（文档 + 测试 + 迁移安全 + 独立复审）显式版本化；
- 任何 dose aggregate 的展示必须与 identity 处置（§13.2）联合使用，避免混合不同药物的 mg。

## 28. B-01 readiness checklist（P3-23）

B-01 只有在以下各项**全部冻结**后才能开始：

- [x] fact taxonomy（§2）
- [x] eligibility（§3：coverage-count 与 future-ratio 两列已拆分）
- [x] confidence（§4 + §25：四 bucket，仅 recorded intakes）
- [x] source/provenance（§16：writer 级审计 + 三态可达性）
- [x] range presets（§19：端点与示例全部冻结）
- [x] identity disposition（§13.1：单值 `Unknown medication`）
- [x] dose-total eligibility（§13.2：无跨药物总量；per-medication 仅 known）
- [x] record-coverage semantics（§12 + §24：公式已去 `identityEligible`，disclosure 必附）
- [x] timezone disclosure（§26：`containsCurrentTimezoneDerivedDates`）
- [x] delete/undo semantics（§15：物理删除，无 tombstone 指标）

**Open decisions 不阻塞 B-01**：charts / granularity（B-03）、cache（须可重建）、
Option-2 timing（需单独批准）、percentage UI（未 ship）、anti-androgen projection field（`A-04/B` debt，
只影响 anti-androgen 的 per-drug 指标，已按 identity unavailable 排除）。
