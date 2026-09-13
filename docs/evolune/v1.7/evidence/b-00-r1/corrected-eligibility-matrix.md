# B-00-R1 corrected eligibility matrix + confidence (snapshot)
# Extraction rule: start at the line beginning "## 3. Eligibility matrix", end at "## 5. Recorded intake semantics"; verbatim.
# Source: docs/evolune/v1.7/V17_B_00_INSIGHTS_SEMANTICS.md @ B-00-R1 (authoritative if they differ).

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
