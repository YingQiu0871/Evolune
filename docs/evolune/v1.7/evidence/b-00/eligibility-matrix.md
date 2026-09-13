# B-00 eligibility matrix + match confidence (snapshot)
# Extraction rule: verbatim copy of V17_B_00_INSIGHTS_SEMANTICS.md sections 3 and 4
#   start: line beginning '## 3. Eligibility matrix'
#   end:   line beginning '## 5. Recorded intake semantics'
# Source document at the B-00 base HEAD; the source document is authoritative if they differ.

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
