# V17-B-01 — Insights Domain（read-only aggregation foundation）

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-B / **B-01**（Insights Domain；**不含** ViewModel / UI / charts）
> B-00 contract HEAD：`4458a47a19ae01488dd86c0e2e488df11965101b`（V17-B-00-R1 APPROVE，语义冻结）
> 起始 HEAD（本轮）：`4458a47a19ae01488dd86c0e2e488df11965101b`
> 依据（§0 preflight 重读）：[`V17_B_00_INSIGHTS_SEMANTICS.md`](V17_B_00_INSIGHTS_SEMANTICS.md)（B-00-R1 冻结版）·
> [`V17_SPEC.md`](V17_SPEC.md)（§2/§3/§10/§12）· [`V17_PLAN.md`](V17_PLAN.md) §5 ·
> [`V17_ACCEPTANCE.md`](V17_ACCEPTANCE.md) §2 · [`V17_DATA_SEMANTICS.md`](V17_DATA_SEMANTICS.md) ·
> Phase-A contracts：[`V17_A_01_HISTORICAL_PROJECTION.md`](V17_A_01_HISTORICAL_PROJECTION.md) ·
> [`V17_A_02_HISTORY_READ_MODEL.md`](V17_A_02_HISTORY_READ_MODEL.md) ·
> [`V17_A_03_HISTORY_UI.md`](V17_A_03_HISTORY_UI.md) · [`V17_A_04_HARDENING.md`](V17_A_04_HARDENING.md)
> 证据：[`evidence/b-01/`](evidence/b-01/)

---

## 0. B-01 不得重新解释的 frozen decisions（B-00-R1，逐条继承）

| # | 冻结决定 | B-01 落地方式 |
|---|---|---|
| 1 | Insights 只能从冻结的 Historical facts 派生，**不得**建第二套 medication truth（§2 首要规则） | 聚合器只接受 `HistoricalRange`；生产包内无持久层/传输层/UI 依赖（源码护栏） |
| 2 | `UnrecordedHistoricalOccurrence` = "可用数据中无匹配记录"，**禁止** missed/skipped 措辞与 `missed = unrecorded` | 模型只有 `unrecordedOccurrenceCount`；禁用词护栏 |
| 3 | `FutureOccurrenceContext` 不进任何历史指标 | 聚合器根本不引用该类型（护栏） |
| 4 | unmatched actual intake 是真实摄入，可进事实计数，**不进** plan-based 分母 | `unmatchedActualIntakeCount` + 计入 recorded/来源/剂量（若身份可证） |
| 5 | binding confidence ≠ 历史处方真实性；confidence breakdown 只含 recorded intakes 的 `HIGH/MEDIUM/LOW/NONE` | `InsightsBindingConfidence` 四值；unrecorded 不进入 |
| 6 | 区间 preset 端点冻结（含 today；§19/§19.1） | B-01 不做 preset 选择（B-02 职责），只校验输入区间完整性 |
| 7 | identity 三态单值处置：`partial` 与 `unavailable` 统一 `Unknown medication`，不进 named aggregate | `MedicationIdentityStatus` + `unknownIdentityRecordedIntakeCount` |
| 8 | 剂量规则：无跨药物总量、per-medication 仅 known、禁止 Unknown 剂量桶 | `perMedicationDoseTotalsMg`（仅 known，key 为稳定枚举） |
| 9 | coverage counts 接受全部 matched provenance；比例**不 ship** | `matchedOccurrenceCount` 计全部 provenance；模型中不存在任何 ratio 字段 |
| 10 | 所有按 `displayDate` 聚合的指标必须能暴露 `containsCurrentTimezoneDerivedDates`（§26） | summary 字段 + 判定规则 |
| 11 | undo = 物理删除，无 tombstone | 聚合器无 undo 相关字段/计数 |
| 12 | source 与 provenance 两个独立维度 | 分别统计，且用测试证明独立性（不互相推导） |

---

## 1. Input contract

```
Input : HistoricalRange(startDate: LocalDate, endDate: LocalDate, days: List<HistoricalDay>)
        HistoricalDay(date, entries: List<HistoricalEntry>)
        HistoricalEntry ∈ { MatchedHistoricalOccurrence, UnrecordedHistoricalOccurrence, UnmatchedHistoricalIntake }
Output: MedicationInsightsSummary
```

- 只消费 Phase A 冻结值；`range` 已经携带最终 date attribution 与 match provenance；
- **不**接受 `now`、`ZoneId`、`Clock`、locale 或任何环境输入（无参时间来源可注入 → deterministic）；
- B-01 不取得 History：调用方（未来 B-02）负责 range 选择与加载。

## 2. Output model（`MedicationInsightsSummary`）

| 字段 | 语义 |
|---|---|
| `startDate` / `endDate` | 原样来自 `HistoricalRange` |
| `recordedIntakeCount` | 权威实际摄入次数（matched + unmatched，每个 event 恰好一次） |
| `recordedDayCount` | 至少含一个 recorded intake 的 **distinct `displayDate`** |
| `matchedOccurrenceCount` | 全部 `MatchedHistoricalOccurrence`（含 LOW；projection-linking 计数） |
| `unrecordedOccurrenceCount` | 全部 `UnrecordedHistoricalOccurrence` |
| `unmatchedActualIntakeCount` | 全部 `UnmatchedHistoricalIntake` |
| `sourceCounts` | 六类来源的完整 map（含 0），`sum == recordedIntakeCount` |
| `bindingConfidenceCounts` | 四档 confidence 的完整 map（含 0），`sum == recordedIntakeCount` |
| `perMedicationDoseTotalsMg` | 仅 known identity 的 mg 累计（key 为 `MedicationIdentityKey`，非 localized 字符串） |
| `unknownIdentityRecordedIntakeCount` | 身份不可证的 recorded intakes **计数**（绝不是剂量） |
| `containsCurrentTimezoneDerivedDates` | 区间内是否存在 `CURRENT_DISPLAY_TIMEZONE_DERIVED` entry |

**模型中不存在**任何 ratio / 百分比 / 评分 / 准时性字段（§30/§10 冻结）。

## 3. Architecture boundary（单向依赖）

```
HistoricalRange  ──►  MedicationInsightsAggregator  ──►  MedicationInsightsSummary
```

- 位置：`experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights/`
  （与 projection/read model 同模块同层；`experience-core` 是**纯 Kotlin JVM** 模块，因此聚合器天然纯 JVM、可单测）；
- 生产包**禁止**（机器护栏，见 §13）：持久层/数据访问、Android/Compose、transport、matcher、generator、
  `HistoryReadService`、`FutureOccurrenceContext`、`Clock`/`now`/`systemDefault`/`Locale`/`Random`；
- **无**新增依赖、**无**新模块（`B-01 MODULE BOUNDARY DECISION REQUIRED` 未触发）。

## 4. Count semantics

- matched → **+1 recorded**；unmatched actual → **+1 recorded**；unrecorded → **+0**；
- 每个 authoritative `eventId` 恰好一次；重复 → **fail fast**（`InsightsContractViolationException`），
  绝不静默去重（那会隐藏上游契约违反，§7）；
- 不因 provenance / source / identity / confidence 改变 recorded 事实。

## 5. Recorded days

`recordedDayCount = |{ entry.displayDate : entry ∈ matched ∪ unmatched }|`；
不含 unrecorded-only 日期，不含 future context；同日多次摄入只算 1 天；
**不按 event instant 自行归日**（§8）。

## 6. Coverage counts 与 confidence mapping（冻结映射）

| Provenance | Confidence |
|---|---|
| `EXACT_SLOT_AND_LOCAL_DATE` | `HIGH` |
| `SLOT_WINDOW_WITHOUT_LOCAL_DATE` | `MEDIUM` |
| `NULL_SLOT_TIME_WINDOW` | `MEDIUM` |
| `NULL_SLOT_SAME_DAY` | `LOW` |
| （unmatched actual intake） | `NONE` |
| （unrecorded occurrence） | **不进入** breakdown（无 binding confidence） |

## 7. Source aggregation

- matched：读 **authoritative event** 的 `source`；unmatched：读 entry 的 `source`；
- 六类（`MANUAL`/`REMINDER`/`WEAR`/`WIDGET`/`JSON_V1`/`LEGACY`）分别计数，**不合并**（尤其 LEGACY ≠ MANUAL）；
- required invariant：`sum(sourceCounts) == recordedIntakeCount`（模型 `init` 强制）。

## 8. Identity classification（`MedicationIdentityClassifier`）

| 输入（**authoritative event** 的 matchKey） | 结果 |
|---|---|
| `routeKey == "ANTIANDROGEN"` | `UNAVAILABLE`（即使 `medicationKey == "E2"` 占位符也绝不映射成酯类） |
| `routeKey != ANTIANDROGEN` 且 `medicationKey ∈ {E2,EB,EV,EC,EN}` | `KNOWN(key)` |
| 其它任何 key（未知/外来/无法映射） | `PARTIAL`（不发明药物名，不用 raw key 作聚合键） |

`MedicationIdentity.key` 仅在 `KNOWN` 时非空（`init` 强制）；聚合器对 `PARTIAL`/`UNAVAILABLE` 只累加
`unknownIdentityRecordedIntakeCount`。

## 9. Dose source audit（§19/§20/§21 gating check，**未触发 stop**）

审计结论（证据：`evidence/b-01/dose-source-audit.txt`）：

- `MatchedHistoricalOccurrence` **同时**携带两套 key：
  `occurrence.presentation.matchKey`（**当前计划**重新生成的剂量）与 `event.matchKey`（**权威事件自身**的剂量/route/medication）；
- `RecordedMedicationEvent.matchKey` 由生产 mapper 从 `DoseEvent` 的 route/ester/doseMG 构造，
  因此 event key 就是 actual recorded dose 的冻结表示；
- 聚合器**只**使用 `event.matchKey`（dose + identity），从不使用 `occurrence.presentation.matchKey`；
- 因此 §20 的 mismatch 场景（计划 2 mg vs 实际 3 mg）可表达，并有测试锁定（统计 3 mg）；
- `B-01 DOSE CONTRACT DECISION REQUIRED` **未触发**。

## 10. Date / disclosure semantics

- 聚合只用 `entry.displayDate`（不做任何 `atZone`/`toLocalDate` 重算；护栏禁止这些调用出现在生产包）；
- `containsCurrentTimezoneDerivedDates = ∃ entry.displayDateProvenance == CURRENT_DISPLAY_TIMEZONE_DERIVED`；
- 该 flag 对**所有**聚合结果生效（不区分指标类型），符合 B-00 §26 的"range-level disclosure"要求。

## 11. Range / day invariants 与 error policy

| 检查 | 违反时 |
|---|---|
| `startDate <= endDate` | fail fast |
| day 唯一（无重复日期） | fail fast |
| 每个 `day.date ∈ [startDate, endDate]` | fail fast |
| 每个 `entry.displayDate == day.date` | fail fast |
| 重算的 per-day 计数 == `HistoricalDay` 的计数 | fail fast（`HistoricalDay` 目前由 entries 派生，此检查为 defence-in-depth，测试记录该前提） |
| 重复 authoritative `eventId` | fail fast |

- 统一异常：`InsightsContractViolationException`（`IllegalStateException` 的子类）；
- **不**检查 `endDate <= today`：聚合器没有 clock，那是 B-02 range-selection 的职责（B-00 §19）；
- 空 `days`：合法，输出全 0 + `false`，不抛异常。

## 12. Aggregate invariants（模型 `init` 强制 + 测试锁定）

```
recordedIntakeCount == matchedOccurrenceCount + unmatchedActualIntakeCount
sum(sourceCounts) == recordedIntakeCount
sum(bindingConfidenceCounts) == recordedIntakeCount
matchedOccurrenceCount == HIGH + MEDIUM + LOW
NONE == unmatchedActualIntakeCount
recordedDayCount <= recordedIntakeCount
unknownIdentityRecordedIntakeCount <= recordedIntakeCount
sourceCounts.keys == MedicationIntakeSource.entries
bindingConfidenceCounts.keys == InsightsBindingConfidence.entries
```

## 13. Explicitly absent（禁止项，机器护栏）

- **指标禁止**：adherence、compliance、completion（含 completionRate）、coverageRatio、percentage/percent、
  ratio、score、missed、skipped、punctuality、onTime、overdue、timingDelta、delay；
- **依赖禁止**：`import android.`、`import androidx.`、Compose、Room、Dao、Entity、Repository、
  DataClient、SharedPreferences；
- **第二套派生禁止**：atZone、toLocalDate()、systemDefault、Clock、Instant.now、LocalDate.now、Locale、
  Random、Matcher、Generator、HistoryReadService、OccurrenceGenerator、FutureOccurrenceContext；
- 护栏使用**词边界**匹配（避免 "related" 之类的误伤），且这些词只允许出现在护栏自身的 forbidden list 与文档中。
  本轮实现过程中护栏确实拦截了一处生产 KDoc 措辞（已改写）——证明护栏有效。

## 14. Tests（`experience-core`，纯 JVM）

| 文件 | 覆盖 |
|---|---|
| `MedicationInsightsAggregatorTest`（21） | 计数（exact/inferred/unrecorded/unmatched）；recorded days（同日多次、仅 unrecorded 的日）；coverage（全 provenance、不含 unmatched）；confidence 四档；六类来源各自计数 + 不合并 LEGACY/MANUAL；source × confidence 独立性；identity 三态（known 五个 key、partial 未知/外来 key、unavailable anti-androgen 占位符）；dose（authoritative event 剂量 3 mg 胜过计划 2 mg、unrecorded 计划剂量不进总量、unknown identity 不进剂量）；timezone disclosure；重复 eventId / 重复 day / entry 越日 / 计数派生前提 / 反向区间 / 空区间 |
| `MedicationInsightsGoldenDatasetTest`（2） | B-00 worked example 的**数学一致**（recorded 9 / days 6 / coverage 6·1 / sources 1·1·3·2·1·1 / confidence 4·1·1·3 / E2 11.0 mg · EV 6.0 mg / unknown 1 / disclosure true）+ 全归因数据集无 disclosure |
| `InsightsBoundaryGuardTest`（5） | 禁用词（词边界）、依赖禁止、第二套派生禁止、聚合器只消费冻结 entry 三分支、summary 字段清单 |
| `MedicationInsightsPerformanceSmokeTest`（1） | 10,000 entries（2,000 天 × 5）线性形状 smoke（非 release gate） |

## 15. Evidence

`docs/evolune/v1.7/evidence/b-01/`（**136 条目，coverage 136=136，`sha256sum -c` 136 OK / 0 FAILED**，
manifest 自哈希 `c7980d0cc5e44224a5f10ffdbf9680ee31cff8e8a20c8ff8f1a77df0c833df32`）：`source-boundary-audit.txt`（含禁用 token 逐项计数）、
`model-api-snapshot.md`（按记录的抽取规则）、`dose-source-audit.txt`（§19–§21 gating 审计）、
`focused-insights-xml/`（4 个 XML）+ `focused-insights-aggregate.tsv` + `b01-focused-insights.log`、
`jvm-{app,experience-core,wear}/`（123 XML）+ `jvm-aggregate.tsv` + `b01-jvm-run.log`、
`golden-dataset-output.txt`、`source-diff-stat.txt`（6 项边界 0 改动证明）、`MANIFEST.sha256`；
提交后校验：coverage 100% / `sha256sum -c` / self hash / HEAD blob 0 mismatch。

## 16. Open debt（不阻塞 B-02）

- `MedicationIdentityKey` 与 app 侧 `Ester` 是同一套 key 的两个表示（app 模块依赖 experience-core，反向不可用）；
  若未来新增酯类，Insights 侧会安全降级为 `PARTIAL`，需要同步扩展该枚举；
- anti-androgen 真实药名仍缺投影字段（`A-04/B presentation model debt`）→ Insights 只能 `UNAVAILABLE`；
- cache 未实现（B-00 open decision，须"完全可由权威数据重建"）；
- B-02/B-03 未开始：range 选择、加载/状态、UI/图表、术语白名单渲染。
