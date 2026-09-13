# B-00-R1 record-coverage denominator definition (snapshot)
# Extraction rule: start at "## 12. Percentage denominator definitions", end at "## 13. Medication identity eligibility"; verbatim.

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
