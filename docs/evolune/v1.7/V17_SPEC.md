# Evolune v1.7 — History & Insights（V17_SPEC）

> 状态：`BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED` + `V1.7-A GATE DECISIONS（G/H）`（2026-09-13）
> **Product/code baseline**：`main` @ `72a468c`（与 `origin/main` 同指）；封版稳定版 `v1.6.0`
> **Phase-0 freeze documentation commit**：本目录 `docs/evolune/v1.7/*` 的 docs-only commit（**不是**产品实现基线；SHA 见 Phase-0 correction report）
> 说明：以下正文为版本负责人提供的规格原文，逐字保留（含其原始 `Status` 行），未作语义改写；
> **除 §8 一行按 Decision F 修订、以及文末追加的 Phase-0 Gate Decisions 附录外，正文未作改动。**
> 配套文档：[开发计划](V17_PLAN.md) · [基线审计](V17_BASELINE_AUDIT.md) · [数据语义](V17_DATA_SEMANTICS.md) · [验收](V17_ACCEPTANCE.md) · [基线证据](V17_BASELINE_EVIDENCE.md)
> 与当前实现的一致性结论、冲突与 `SEMANTICS UNRESOLVED` 项见基线审计与数据语义文档；规格本身不因实现现状被改写。

---

# Evolune v1.7 — History & Insights

Status: DRAFT FOR BASELINE FREEZE
Target: v1.7.0
Theme: History, Insights, Retrospective PK and Data Portability

## 1. Product Goal

Evolune v1.7 turns medication events already recorded by Evolune into a coherent historical record that users can review, understand and export.

The release must answer four questions reliably:

1. What was planned?
2. What actually happened?
3. How did actual medication use differ from the plan?
4. How did those events affect the retrospective PK timeline?

v1.7 must not introduce a second authoritative medication-history database.

Phone, widgets, Wear, History, Insights and PK must derive their state from the same authoritative medication-plan / occurrence / intake-event model already established by earlier versions.

---

## 2. Core Architectural Rule

### 2.1 Single source of truth

History, Timeline, Insights and Retrospective PK are derived surfaces.

They must not become independent sources of medication truth.

No v1.7 feature may persist a second copy of facts such as:

- whether a dose was completed;
- when a dose was taken;
- whether an occurrence exists;
- which dose/route belonged to an authoritative medication event;
- whether an intake event was undone or replaced.

Caching is permitted only when the cache is fully reconstructible from authoritative data.

### 2.2 Existing semantics win unless deliberately versioned

Before implementation, the existing repository must be audited for:

- medication occurrence identity;
- matching windows;
- legacy null-slot events;
- manual medication events;
- undo semantics;
- duplicate handling;
- cross-midnight matching;
- deleted/edited schedules;
- time-zone handling;
- Wear-originated actions;
- widget-originated actions.

v1.7 must not silently redefine these semantics.

If v1.7 requires a deliberate semantic change, it must be:

1. explicitly documented;
2. covered by deterministic tests;
3. migration-safe;
4. reviewed independently before implementation continues.

---

## 3. Scope

### v1.7-A — History Foundation

Provide a reliable historical representation of medication activity.

Required user-facing concepts:

| Concept | Requirement |
|---|---|
| Day history | Show medication activity for a selected calendar day |
| Calendar navigation | Navigate backward and forward through history |
| Planned occurrence | Distinguish planned medication from actual intake |
| Actual intake | Show authoritative actual intake time |
| Difference | Show planned vs actual timing where both exist |
| Manual intake | Clearly identify events without a normal planned occurrence when applicable |
| Undo/cancel state | Historical rendering must respect authoritative undo semantics |
| Details | Allow inspection of medication, dose, route, planned time and actual time |

History must be a derived projection, not new medication storage.

### v1.7-B — Adherence Insights

Provide factual summaries over selectable historical periods.

Initial ranges:

- 7 days
- 30 days
- 90 days
- custom range if implementation remains low-risk

Candidate derived metrics:

| Metric | Meaning |
|---|---|
| Scheduled | Number of qualifying planned occurrences |
| Completed | Planned occurrences matched to authoritative completed intake |
| Uncompleted | Planned occurrences without qualifying completion |
| Completion rate | Completed / qualifying scheduled occurrences |
| Timing difference | Actual time minus planned time |
| Medication breakdown | Metrics grouped by medication |

The exact semantics of terms such as `late`, `missed`, `due`, `expired` or similar MUST NOT be invented in v1.7 until the existing repository policy is audited.

If the existing domain already defines them authoritatively, v1.7 may reuse those definitions.

Otherwise, the UI should initially prefer neutral factual language such as:

- Completed
- Not recorded
- Taken +2h14m after planned time

rather than introducing a new clinical/adherence judgment.

No medical interpretation or treatment recommendation belongs in v1.7 Insights.

---

## 4. v1.7-C — Retrospective PK

Add retrospective PK visualization driven by authoritative historical intake events.

The retrospective PK view must support a historical interval and reconstruct the PK curve using actual recorded medication events.

Where applicable, the chart may display:

- planned occurrence marker;
- actual intake marker;
- actual-vs-planned timing delta;
- dose;
- route;
- medication;
- manual intake;
- dose changes.

Example:

Planned: 08:00
Taken: 11:32
Delta: +3h32m

The PK engine must not use a History-specific copy of medication events.

It must consume the same authoritative or derived event stream used elsewhere.

Historical PK must remain clearly model-based and must not be presented as measured blood concentration.

---

## 5. v1.7-D — Medication Timeline

Introduce a unified chronological Timeline derived from medication facts.

Example presentation:

08:00
Estradiol 2 mg
Taken 08:07

14:00
Progesterone 100 mg
Taken 14:32

20:00
Estradiol 2 mg
Taken 22:41
+2h41m

Timeline and History must share the same derivation layer wherever practical.

They must not implement separate medication matching logic.

Recommended architecture:

Authoritative medication facts
→ shared historical derivation/domain layer
→ History
→ Timeline
→ Insights
→ Retrospective PK adapters

---

## 6. v1.7-E — Export & Data Portability

Support user-readable export independently of Evolune backup/restore.

### CSV

CSV is intended for spreadsheets and analysis.

Minimum candidate fields:

`date`
`medication`
`dose`
`route`
`planned_time`
`actual_time`
`timing_delta`
`event_type`
`status`

The final schema must be versioned and documented.

### JSON

JSON is intended for structured portability.

It should preserve enough semantics to make exported medication history intelligible without relying on UI text.

### Export ranges

Initial target:

- last 30 days;
- last 90 days;
- all available history.

### Export vs Backup

These concepts must remain separate.

Backup:
Evolune application recovery.

Export:
Human-readable / machine-readable user-owned data.

v1.7 export must not accidentally become an undocumented replacement for the existing backup contract.

---

## 7. v1.7-F — Cross-Surface Consistency & Release Gate

The release candidate must demonstrate that equivalent facts are rendered consistently across:

- Phone app;
- Phone widgets;
- Wear app;
- Wear Tile;
- Wear Complications where relevant;
- History;
- Timeline;
- Insights;
- PK.

A medication action originating on one supported surface must eventually produce the same authoritative result on all other surfaces.

No surface may introduce its own medication truth.

---

## 8. Required Edge Cases

The implementation and acceptance suite must explicitly cover:

| Case | Required property |
|---|---|
| Cross-midnight occurrence | No incorrect day reassignment or false completion |
| Previous-day event near midnight | **（按 Decision F 修订）** Must not be presented as an *exact* historical match: a cross local-date legacy/null-slot match is a **Legacy inferred match** and must carry match provenance. The existing Phase-A matcher behaviour is preserved and is **not** required to stop matching; what is required is that History does not disguise an inferred cross-date match as exact, and that such matches are not high-confidence input to any strict adherence metric. |
| Legacy null-slot event | Existing ambiguity policy preserved |
| Multiple same-medication doses | Deterministic occurrence identity |
| Same medication + same dose multiple times/day | No nearest-event misattribution |
| Manual historical entry | Correct historical representation |
| Undo | Removed/reverted according to existing authoritative semantics |
| Schedule edited after intake | Historical behavior explicitly specified |
| Schedule deleted after intake | Past medication facts must not silently disappear unless existing domain contract requires it |
| Time-zone change | Absolute event instant preserved |
| DST transition | No duplicated or missing event caused solely by clock transition |
| Wear action | Same historical result as Phone action |
| Widget action | Same historical result as Phone action |
| Legacy upgraded data | No silent history corruption |

---

## 9. Time Semantics

v1.7 must clearly distinguish where relevant:

- absolute event instant;
- device/local time zone;
- planned local schedule time;
- historical display date;
- current display time zone.

A decision must be documented for historical display after time-zone changes.

The implementation must not rely on naive local-date comparison where an absolute instant is required.

DST tests are mandatory before v1.7 release.

---

## 10. UI Principles

v1.7 should prioritize clarity over gamification.

Avoid:

- medical grading;
- shame-oriented adherence scoring;
- unsupported health claims;
- excessive red/green judgment;
- invented precision.

Prefer factual language:

“Taken 2h 10m after planned time”

over:

“Bad adherence”

History must remain usable when the user has:

- incomplete records;
- legacy records;
- ambiguous matches;
- manual events.

Ambiguity must not be hidden by fabricated certainty.

---

## 11. Non-Goals

v1.7 does not target:

- AI medical advice;
- treatment recommendations;
- automatic dose optimization;
- cloud account architecture;
- social/community features;
- clinician portal;
- new widget style expansion unrelated to History;
- new Wear surfaces unrelated to historical consistency;
- major redesign of the medication scheduling engine unless required to correct an independently verified defect.

---

## 12. Development Invariants

The following are release-blocking invariants:

**Invariant 1 — One fact, one authority**
No second medication truth store.

**Invariant 2 — Shared derivation**
History, Timeline and Insights must not independently reinvent occurrence matching.

**Invariant 3 — PK consumes actual medication facts**
Retrospective PK must use authoritative historical intake data.

**Invariant 4 — Ambiguity stays ambiguous**
The implementation must not convert uncertain legacy matching into false certainty.

**Invariant 5 — Read paths cannot mutate truth**
Opening History/Insights/PK must never rewrite medication records as a side effect.

**Invariant 6 — Cross-surface convergence**
Phone and Wear actions must result in equivalent historical state.

**Invariant 7 — Testable semantics**
Every semantic rule affecting historical classification must have deterministic tests.

---

## 13. Release Acceptance

v1.7.0 may be approved only when:

1. the authoritative medication-event model has been documented;
2. History is demonstrably derived from existing facts;
3. Timeline and Insights share the same historical derivation semantics;
4. retrospective PK uses authoritative actual events;
5. export schema is documented and deterministic;
6. cross-midnight, timezone and DST cases have deterministic coverage;
7. legacy/null-slot behavior is preserved or explicitly migrated;
8. Phone/Wear/widget-originated actions converge;
9. fresh JVM tests pass;
10. fresh Android/device tests pass for affected surfaces;
11. independent review reports no unresolved P0/P1 blockers;
12. release evidence is reproducible from the repository.

---

## 14. Version Structure

v1.7-A — History Foundation
v1.7-B — Adherence Insights
v1.7-C — Retrospective PK
v1.7-D — Medication Timeline
v1.7-E — Export & Data Portability
v1.7-F — Consistency, Reliability & Release Gate

Each phase requires independent review before the next phase becomes implementation-active.

END OF SPEC
---

# Phase-0 Gate Decisions（架构/产品门裁决，2026-09-13）

> 以下裁决由架构/产品门在 Phase-0 独立复审（High `REQUEST_CHANGES V17 PHASE-0 BASELINE`）后作出，
> 原文写入，含义未作改动。它们是 v1.7 各阶段的**约束性输入**；与本文正文冲突时以本节为准，未决项按开发计划 §12 Stop Conditions 处理。

## Decision A — Historical planned time

v1.7 不修改 Room schema，不建立 plan/slot version history。

当前模型无法检测 slot 是否在历史 intake 后被编辑，因为：

* plan/slot 无历史版本；
* slot UUID 可跨时间修改继续保留；
* 当前 plan 可以重新生成过去 occurrence，但生成的是**当前计划投影**。

因此：

**actual intake 是历史权威事实。**

对于过去日期，由当前 medication plan 重新生成的 scheduled time，只能表示：

`Current schedule context`

或等价明确文案。

不得称为：

`historical planned time`

也不得在无法证明历史 planned snapshot 的情况下，将：

`actual - reconstructed current scheduled time`

展示成确定的历史服药延迟。

如果某一未来数据结构真正具有 immutable historical planned snapshot，后续可单独利用；现有数据不得假装拥有它。

这意味着 v1.7-B 的严格 adherence 指标必须重新约束：

* 不得基于不可证明的 historical plan reconstruction 输出"严格历史 adherence rate"；
* 优先做 factual medication insights；
* 只有 provenance 足够可靠的数据才可进入计划符合度统计；
* 具体 B 阶段指标在 A 的 provenance model 冻结后再最终冻结。

## Decision B — Orphan intake

Unmatched actual event 必须展示。

如果：

`source == MANUAL`

可以称 Manual intake。

其他 source 必须按真实 source 呈现，不得把所有 orphan 自动叫 Manual。

## Decision C — Skip

`ReminderSkipStore` 是短期、非 authoritative 的 skip state。

它不能成为长期 History 的 medication fact。

因此当前长期历史只能表达事实状态，例如：

* Recorded
* No recorded intake

不能从 Room 历史虚构：

* Skipped
* Missed

## Decision D — DST gap

`schedule wall-clock time` = user intent。

`scheduledAt` = 经当前 timezone/DST policy 解析后的 valid instant。

matching / PK / 数值 timing calculation 使用 valid instant。

二者必须在语义上保持区分。

## Decision E — DST overlap

v1.7-A 保持现有 earlier-offset occurrence materialization。

本阶段不重写 DST overlap generation。

同时在文档明确记录：

第二个重复 local-clock instant 当前不会单独 materialize，因此某些真实第二个 02:30 intake 可能表现出 +1h 的 instant difference。

不得隐藏这一限制。

## Decision F — Cross-date legacy null-slot

不要在本轮、也不要在 PRE-A 阶段修改现有四阶段 matcher。

当前：

昨日 23:00 legacy null-slot event

可能通过 closed ±1h window 命中：

今日 00:00 occurrence。

v1.7 将这种情况定义为：

**Legacy inferred match**

而不是：

**Exact historical match**

后续 v1.7-A historical projection 必须能够携带 match provenance，至少区分：

* exact identity/date match
* bounded inferred match
* legacy/null-slot inferred match
* unmatched actual event

具体 enum/type 名称由实现阶段结合现有代码确定。

跨 local-date 的 legacy/null-slot inferred match：

* 可以保持现有 v1.6 compatibility；
* 不得在 History UI 中伪装成 exact；
* 不得作为未来严格 adherence metric 的高置信输入。

## 受影响的规格条款（本文件内的处理）

| 规格位置 | 处理 |
|---|---|
| §8 `Previous-day event near midnight` | 已就地修订为 provenance/ambiguity 要求（不再要求"不匹配"） |
| §3 v1.7-B 指标 | 按 Decision A 重新约束：不得输出不可证明的严格历史 adherence rate；指标待 A 的 provenance model 冻结后最终冻结 |
| §3 Manual intake 行 | 按 Decision B：unmatched event 必须展示，并按真实 `source` 呈现 |
| §8 Undo / legacy 行 | 按 Decision C：长期历史只表达 `Recorded` / `No recorded intake`，不得虚构 `Skipped` / `Missed` |
| §9 Time Semantics | 按 Decision D/E：区分 schedule wall-clock（user intent）与 `scheduledAt`（valid instant）；DST overlap 保持现有 earlier-offset materialization，并公开 +1h 限制 |
| §12 Invariant 4（Ambiguity stays ambiguous） | 按 Decision F：由 provenance 承载歧义，而不是靠隐藏匹配 |

END OF PHASE-0 GATE DECISIONS

---

# v1.7-A Gate Decisions（架构/产品门裁决，2026-09-13）

> 以下裁决在 v1.7-A 进入实现时作出，关闭 Phase-0 遗留的两项 `SEMANTICS UNRESOLVED`。
> 原文写入，含义未作改动；与本文正文冲突时以本节为准。

## Decision G — legacy timezone attribution

对历史 event：

**Bound / attributable**

如果 event 能可靠绑定 occurrence，History 的 occurrence date 使用：

`intended local date`

如果 event 自身已经持久化：

`localDate`

`zoneId`

则保留这些 persisted semantics。

**True legacy orphan**

如果 event：

无 `localDate`
无 `zoneId`
无可靠 occurrence binding

则无法恢复 original local calendar date。

History 只能根据：

`event instant` + `current display timezone`

得到展示日期。

必须携带 provenance：

`display date derived from current timezone`

或等价 domain flag。

不得声称它是原始当地日期。

这类数据不得作为高置信 historical adherence 输入。

## Decision H — Widget rejected action feedback

跨午夜 / stale occurrence / invalid occurrence 等 Widget action 被 authoritative validation 拒绝时：

* 不写入错误 completion
* refresh authoritative widget state
* 不得显示/保持 completed 假象
* 后续必须提供明确 user-visible rejection feedback

具体 Toast / notification / transient widget feedback 机制**不在 A-01 实现**。

将其列为后续 **Widget UX acceptance item**。

## 受影响的规格条款（本文件内的处理）

| 规格位置 | 处理 |
|---|---|
| §8 `Widget action` 行 | 按 Decision H：拒绝路径不得留下 completed 假象，且已登记为后续 Widget UX acceptance item |
| §9 Time Semantics（historical display date） | 按 Decision G：三类归因（bound → intended local date；persisted context → 保留 persisted；true legacy orphan → current timezone derived + 低置信 provenance） |
| §13 Release Acceptance #7（legacy/null-slot 行为保留或显式迁移） | 按 Decision G：legacy orphan 不伪造原始日期，只提供带 provenance 的展示日期 |

END OF V1.7-A GATE DECISIONS
