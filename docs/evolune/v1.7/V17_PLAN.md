# Evolune v1.7 开发计划（V17_PLAN）

> 状态：`BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED`（2026-09-13）
> **Product/code baseline**：`main` @ `72a468c`（与 `origin/main` 同指）；实施基线 `v1.6.0`
> **Phase-0 freeze documentation commit**：`docs/evolune/v1.7/*` 的 docs-only commit（**不是**产品实现基线）
> 架构/产品门裁决 Decision A–F 已应用（原文见 [V17_SPEC.md 附录](V17_SPEC.md#phase-0-gate-decisions架构产品门裁决2026-09-13)）；
> 强制补齐项见 [验收 §9](V17_ACCEPTANCE.md)
> 说明：以下正文为版本负责人提供的开发计划原文，逐字保留，未作语义改写。
> Phase 0 实际产出与结论见 [基线审计](V17_BASELINE_AUDIT.md)、[数据语义](V17_DATA_SEMANTICS.md)、[验收](V17_ACCEPTANCE.md)。
> 与计划的一处证据差异：Phase A 的共享历史派生层**已存在于仓库**（`experience-core` 的
> `MedicationOccurrencePresentation`），Phase A 应优先复用/扩展而非新建；见基线审计 §7。

---

# Evolune v1.7 Development Plan

Status: INITIAL PLAN  
Development model: Dual-agent implementation + independent review

## 1. Roles

### Development Agent

Model: DeepSeek V4.1 Flash Max

Responsibilities:

- inspect repository;
- implement approved scope;
- write/update tests;
- run fresh verification;
- maintain evidence;
- report exact changed files;
- report unresolved risks.

The development agent may modify the repository only within the currently approved phase.

### Independent Reviewer

Model: DeepSeek V4.1 Flash High

Responsibilities:

- independently inspect specification;
- inspect repository state and diff;
- verify tests and evidence;
- identify P0/P1/P2/P3 findings;
- return APPROVE or REQUEST_CHANGES.

The reviewer does not implement fixes during the review pass.

### Architecture / Product Gate

ChatGPT project conversation.

Responsibilities:

- maintain release scope;
- resolve disputed semantics;
- issue implementation instructions;
- review architecture-sensitive changes;
- prevent phase drift.

---

## 2. Review Independence

The reviewer must receive facts and evidence rather than persuasive summaries.

Preferred review package:

Baseline SHA  
Candidate SHA  
Relevant specification  
Git diff  
Changed files  
Fresh test logs  
Device evidence where relevant  
Known limitations

Avoid telling the reviewer:

“Everything is fixed.”

“Please approve.”

“This implementation is correct.”

The reviewer should derive that conclusion independently.

---

## 3. Phase 0 — Baseline Freeze

No product implementation is allowed until the baseline audit is complete.

Required outputs:

`V17_BASELINE_AUDIT.md`

`V17_DATA_SEMANTICS.md`

`V17_ACCEPTANCE.md`

Audit topics:

- current branch and HEAD;
- version identity;
- clean/dirty worktree;
- v1.6 completion state;
- authoritative medication storage;
- occurrence generation;
- event matching;
- manual events;
- undo;
- legacy/null-slot behavior;
- timezone handling;
- PK input pipeline;
- Phone/Wear/widget mutation paths;
- existing test coverage.

The audit must identify any conflict between `V17_SPEC.md` and current implementation.

Conflicts must be reported before code changes.

### Phase 0 — Correction round（2026-09-13，独立复审 REQUEST_CHANGES 后）

- 独立复审（High）返回 `REQUEST_CHANGES V17 PHASE-0 BASELINE`；本轮执行严格限定的 **Phase-0 correction**：
  只修订文档与证据，**不改产品代码、不改测试代码、不改 `.gitignore`**。
- 架构/产品门裁决 **Decision A–F** 正式写入 `V17_SPEC.md`（附录）、`V17_DATA_SEMANTICS.md`、
  `V17_ACCEPTANCE.md` 与本计划。
- 新增 tracked 证据清单 `V17_BASELINE_EVIDENCE.md`（命令、环境、原始产物路径与 SHA-256、JUnit XML manifest、
  `git status --porcelain` 快照、`git diff --check` 结果、重跑方法）。
- 允许创建**一个 docs/evidence-only commit**（Phase-0 freeze documentation commit）：
  不 push、不 tag、不 merge、不 bump version、不提交 `.idea`/`.kotlin`/旧 v1.6 raw evidence/APK/产品与测试源码。
- **Product/code baseline 仍是 `72a468c`**；freeze commit 只固化文档，不改变实现基线。

Gate（本轮结束时的目标状态）：

`READY FOR V17 PHASE-0 RE-REVIEW`

---

## 4. Phase A — History Foundation

### A-01 Domain model audit

Identify the minimal shared historical projection needed by History.

No UI-first implementation.

### A-02 Historical derivation layer

Implement/reuse one deterministic historical derivation pipeline.

Expected responsibilities may include:

planned occurrence  
+ authoritative medication event  
+ existing match policy  
→ historical projection

The exact type/API must follow repository architecture.

### A-03 Deterministic domain tests

Cover normal and adversarial cases before UI completion.

必须包含 [V17_ACCEPTANCE.md §9](V17_ACCEPTANCE.md) 的 mandatory backlog **M1–M8**
（occurrence fixed golden vector、删除计划后事件存活、计划编辑后归属、精确跨日边界、
时区 A→B、DST×匹配/去重/撤销、provenance 逐阶段断言、孤儿事件按真实 source 呈现）。

### A-04 Phone History UI

Implement selected-date history presentation.

### A-05 Navigation and details

Calendar/day navigation and event details.

### A-06 Legacy and midnight validation

Explicit regression suite.

### A-07 Fresh verification

JVM + Android/device tests as applicable.

### A-08 Independent review

DeepSeek High.

Gate:

`APPROVE V1.7-A CANDIDATE IMPLEMENTATION`

---

## 5. Phase B — Adherence Insights

Insights must consume Phase A historical projections.

No separate event matching.

Work sequence:

B-01 metric semantics  
B-02 aggregation domain layer  
B-03 7/30/90-day ranges  
B-04 medication breakdown  
B-05 UI  
B-06 deterministic tests  
B-07 fresh verification  
B-08 independent review

Gate:

`APPROVE V1.7-B CANDIDATE IMPLEMENTATION`

Any disputed term such as “late” or “missed” requires explicit semantic approval before implementation.

**Decision A 约束**：不得基于不可证明的 historical plan reconstruction 输出"严格历史 adherence rate"；
优先 factual medication insights；只有 provenance 足够可靠的数据可进入计划符合度统计。
**具体 B 指标在 A 的 provenance model 冻结后**才最终冻结。

---

## 6. Phase C — Retrospective PK

Architecture-sensitive phase.

Recommended review level:
DeepSeek High for normal review, with Max-level second-pass review if material PK or medication-event semantics change.

Work sequence:

C-01 inspect current PK source pipeline  
C-02 historical event adapter  
C-03 retrospective interval calculation  
C-04 planned/actual markers  
C-05 UI integration  
C-06 numerical regression tests  
C-07 timezone/DST tests  
C-08 fresh verification  
C-09 independent review

Gate:

`APPROVE V1.7-C CANDIDATE IMPLEMENTATION`

PK output must remain explicitly model-derived.

---

## 7. Phase D — Medication Timeline

Timeline must consume the same shared historical representation as History.

Work sequence:

D-01 timeline projection  
D-02 chronological grouping  
D-03 selected-date / range behavior  
D-04 UI  
D-05 accessibility and localization  
D-06 tests  
D-07 independent review

Gate:

`APPROVE V1.7-D CANDIDATE IMPLEMENTATION`

---

## 8. Phase E — Export & Data Portability

Work sequence:

E-01 export contract  
E-02 CSV schema freeze  
E-03 JSON schema freeze  
E-04 deterministic serialization  
E-05 sharing/storage UX  
E-06 privacy validation  
E-07 round-trip / fixture tests where appropriate  
E-08 independent review

Export must not mutate source data.

Gate:

`APPROVE V1.7-E CANDIDATE IMPLEMENTATION`

---

## 9. Phase F — Final Consistency Gate

Architecture-sensitive release phase.

Required validation matrix:

Phone record  
→ History  
→ Timeline  
→ Insights  
→ PK

Widget record  
→ same result

Wear record  
→ same result

Undo from supported surface  
→ same result

Legacy event  
→ deterministic preserved result

Additional required cases:

cross-midnight  
DST forward transition  
DST backward transition  
timezone change  
duplicate/retry  
manual intake  
same medication multiple times/day  
schedule edit  
schedule deletion  
legacy migration

Required evidence:

fresh JVM suite  
fresh Android suite  
fresh Wear/device suite where affected  
build success  
raw logs  
candidate SHA  
clean final worktree or explicitly documented generated artifacts

Final independent review must report P0-P3 findings.

Release gate:

`APPROVE V1.7.0 RELEASE CANDIDATE`

---

## 9.5 PRE-A — 仅在 Phase-0 复审 APPROVE 之后

`V17-PRE-A-01 Wear Skip Reachability Fix`：manifest/reachability 修正 + 回归覆盖；
不改变 `/hrt/*` protocol、不改 Room、不创造 authoritative skipped medication fact（Decision C）。
边界与验收见 [V17_ACCEPTANCE.md §10](V17_ACCEPTANCE.md)。本轮不执行。

---

## 10. Change Discipline

One phase at a time.

Do not opportunistically:

- refactor unrelated modules;
- redesign navigation outside v1.7 needs;
- change branding;
- alter widget appearance;
- alter Wear visual design;
- change medication semantics without specification;
- perform dependency upgrades without necessity.

If an unrelated defect is discovered, document it separately unless it blocks v1.7 correctness.

---

## 11. Commit / Evidence Discipline

Each implementation slice should be attributable.

Evidence should always state:

baseline SHA  
candidate SHA  
changed files  
test commands  
test counts  
failures/errors  
device target where relevant  
known limitations

A passing test summary without raw or reproducible evidence is insufficient for a release gate.

---

## 12. Stop Conditions

The developer must stop implementation and report rather than guess when:

- the repository contradicts the specification;
- two authoritative medication sources appear to exist;
- legacy matching semantics cannot be determined;
- changing occurrence identity appears necessary;
- migration would destroy historical facts;
- PK requires semantic assumptions not documented elsewhere;
- test failures appear to originate from an unrelated baseline defect and cannot safely be distinguished.

---

## 13. Release Definition

v1.7 is successful when Evolune can reliably reconstruct and present medication history without creating a parallel truth model.

The central success criterion is not the number of new screens.

It is:

**The same medication fact produces the same historical interpretation everywhere.**

END OF PLAN