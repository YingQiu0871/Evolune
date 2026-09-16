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

---

## 4.5 Phase A — Status (v1.7-A-04 hardening, 2026-09-13)

Phase A 的 gate 与证据现状（详见 [`V17_A_04_HARDENING.md`](V17_A_04_HARDENING.md)）：

| Gate | 状态 | 证据 |
|---|---|---|
| A1–A5, A8, A9 | CLOSED | A-01/A-02/A-03 轮测试与文档；A-04 fresh JVM 复核（1027 tests / 0 fail） |
| **A6** 撤销语义 | **CLOSED（A-04）** | Phone `delete` 与 Wear latest-delete 两条生产路径 → 历史投影（matched→unrecorded / unmatched 消失 / early intake→future context / delayed+DST 无 ghost）+ 文案审查 |
| **A7** 读路径无写入 | **CLOSED（A-04）** | 计数型 repository 替身（服务层 + ViewModel 层，全部 mutation 方法）+ 真 Room counting decorator 设备证据 |
| **A10** fresh 验证 | **CLOSED（A-04）** | JVM 全量 + 受影响面 targeted instrumentation + full Phone suite 243/0/5-skip + `assembleDebug` |
| M1–M8 | CLOSED | A-01/A-02 轮；A-04 补齐 M5/M6 的撤销侧 |
| `dose_events.localDate` index | **INDEX DEFERRED**（不改 schema） | 20k 行真 Room `EXPLAIN QUERY PLAN` + 实测 median ≈ 2 ms，acceptance 无性能门 |

**PHASE A — CLOSED**（候选实现，待独立复审）。Phase B 未开始。

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

> **Status note (2026-09-15)**: 上述 C-01…C-09 是本 Phase 的原始计划命名，保持计划原文。在 C-00 语义契约
> 与 C-01 实现规格获批后，**C-01 生产实现已 APPROVED / CLOSED**（contract HEAD
> `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`，approved implementation HEAD
> `145d53bd922c30338171cc7b0529a36dc482b4a6`，evidence `docs/evolune/v1.7/evidence/c-01/`）；该交付已覆盖
> 本序列中 "historical event adapter" 的核心（全历史读取 → Phase A 投影 → 提取/资格/排序/贴片预处理 →
> typed 结果）。**C-01 冻结**：未经重新开启评审不得改动其生产合同。
>
> **Status note (2026-09-16)**: active next slice = **C-04 — Schedule-Context / Recorded-Intake markers &
> retrospective PK surface (MVP)**；contract **R3 corrected**、**RE-REVIEW PENDING**、生产未开始：
> [`V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md`](V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md)
> （R1：marker coverage 改由 range seam 提供 + localization wording 修正；R2：两 family 的 marker scope
> 冻结为 summary ID join + 身份分类、policy 条款改为 value/semantics 等价；R3：same-ID mutation 的
> non-atomic truthfulness 规则 + 图例披露修正）。
> 原始命名序列（C-02 historical event adapter、C-03 retrospective interval calculation 等）保持原文；新切片
> 需经契约批准后实施，本轮不发明契约之外的任何范围。
>
> **Status note (2026-09-16, C-04 closure)**: **C-04 生产实现已 APPROVED / CLOSED**（final independent
> implementation review APPROVE；R1 T4 disclosure closure 后通过）：contract HEAD
> `8be09339bfae1c4a138b4ccb739f86302c60d627`（APPROVED / FROZEN），approved implementation HEAD
> `823bd9ce5c276dc473cc041efba409bd931c589f`，evidence `docs/evolune/v1.7/evidence/c-04/`。
> 交付：Schedule-Context / Recorded-Intake markers + 30×24h retrospective PK read-only surface（MVP）——
> 三次读非原子架构、body-weight gate、typed unavailable/limitations、disclosure 与 marker scope 全部冻结。
> **C-04 冻结**：未经重开评审不得再改 C-04 生产。**NEXT: Phase-C post-C04 remaining-scope audit /
> next-slice contract definition**；原始命名序列保持原文，gated 候选（daily series/chart、coverage %、
> Option-2 timing metric、anti-androgen 真身份、CPA curve）仍未授权，新切片需经契约定义后实施。
>
> **Status note (2026-09-16, Phase-C closure candidate)**: Phase-C closure record 已创建
> （[`V17_C_PHASE_CLOSURE.md`](V17_C_PHASE_CLOSURE.md)）——`PHASE-C CANDIDATE CLOSURE — REVIEW PENDING`：
> C1–C9 验收矩阵完整映射到 closed C-01/C-04，Phase-C product scope functionally complete；
> 本 Phase gate `APPROVE V1.7-C CANDIDATE IMPLEMENTATION` **待独立/架构复审**，当前不自我批准，
> **Phase C 尚未标记 CLOSED**。本记录为 docs-only：未重跑测试、未重生成证据；gated 候选保持未授权；
> optional interaction/detail residue（selectors / cursor / marker detail）不构成 closure 缺失。
>
> **Status note (2026-09-16, Phase-C closure approved)**: Phase gate `APPROVE V1.7-C CANDIDATE
> IMPLEMENTATION` 已由独立/架构 closure review **APPROVED**：**Phase C is CLOSED**（approved
> Phase-C closure HEAD `19652baa07b5057f4aa6c07a79a77a158ac31468`，closure record
> [`V17_C_PHASE_CLOSURE.md`](V17_C_PHASE_CLOSURE.md)）。未经重开评审不得再改 Phase-C 生产；
> 后续切片必须消费已批准的 C-01 API/结果契约与 C-04 只读 surface 行为。
> **NEXT: Phase D — Medication Timeline planning / contract design**（Phase D 未启动；本记录不发明
> Phase-D contract；retrospective selector/cursor polish 不因此转入 Phase D）。gated 候选保持未授权；
> T*/release gates 保持独立（Phase C CLOSED ≠ v1.7 release 全局批准）。
>
> **Status note (2026-09-16, Phase-D planning / D-01 contract)**: Phase D planning started；
> **V17-D-01 Timeline read-model / semantics contract 已起草**（
> [`V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md`](V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md)，
> `CONTRACT — REVIEW PENDING`）：read-only projection over `HistoryRangeSource`；三 row families
> （MATCHED / UNRECORDED_SCHEDULE / UNMATCHED_INTAKE）、typed row identity、schedule-context 与
> recorded-intake 分离、delta 显式排除、ordering 冻结（OCCURRENCE_ORDER 字段序列）、displayDate/
> D3 基础；**D-01 production NOT STARTED**，D-02…D-07 未开始。**NEXT: V17-D-01 contract review**。
> Phase C 保持 CLOSED；C-01/C-04 不被依赖。
>
> **Status note (2026-09-16, D-01 implementation)**: **D-01 contract APPROVED**（`APPROVE V17-D01
> CONTRACT`）；**D-01 implementation 已提交、REVIEW PENDING**：`history/timeline/` pure read-model
> projection over `HistoricalRange`（三 row family、typed row identity、schedule/recorded 分离、
> delta 显式排除、OCCURRENCE_ORDER 字段序列排序 + TLM18 live generator-parity、architecture guards）；
> focused 32/0/0/0 · fresh full JVM 1365/0/0/0 · 54/54 tasks。**NEXT: V17-D-01 implementation review**；
> D-02…D-07 未开始；Phase C 保持 CLOSED、C-01/C-04 未被依赖或修改。
>
> **Status note (2026-09-16, D-01 closure)**: **D-01 implementation APPROVED / CLOSED**（final
> independent implementation review APPROVE）：contract HEAD
> `a245a5ec7a2dcd977ff0de3b3a79c8b129f67e34`（APPROVED / FROZEN），implementation HEAD
> `97838fbf7c8692ada9d44d8401a6008283fac178`，evidence `docs/evolune/v1.7/evidence/d-01/`。
> 验证：focused 32/0/0/0 · fresh full JVM 1365/0/0/0 · 54/54 tasks · instrumentation N/A
> （无 Android/UI surface）· evidence 167/167 manifest coverage、169/169 blob verification、0 mismatches。
> **D-01 冻结**：未经重开评审不得再改 D-01 生产。由于 D-01 已交付 row projection / date-section
> model / canonical chronological ordering / same-instant determinism，**PLAN §7 的 D-02
> "chronological grouping" 需先对账**：**NEXT: Phase-D post-D01 remaining-scope audit /
> D-02…D-03 reconciliation**；D-02…D-07 未完成、不发明 D-02 contract；Phase C 保持 CLOSED；
> delta/early-late/on-time 等 gated 语义保持未授权。

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