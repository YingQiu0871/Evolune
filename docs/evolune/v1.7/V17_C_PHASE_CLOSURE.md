# V17-C — Phase C Closure Record（Phase C 候选收口记录）

> 状态：`PHASE-C CLOSED — APPROVED`
> Phase gate：`APPROVE V1.7-C CANDIDATE IMPLEMENTATION`（独立/架构 closure review **APPROVED**）
> Approved Phase-C closure candidate HEAD：`19652baa07b5057f4aa6c07a79a77a158ac31468`
> 本文件是 **closure / mapping record**，不是新的语义契约；不修改 C-00/C-01/C-04 任何冻结语义。
> **Phase C is CLOSED. No further Phase-C production changes are authorized without reopening review.**
>
> Frozen identities：
> - C-00（Retrospective PK semantics）— **APPROVED / FROZEN**
> - C-01 approved implementation — `145d53bd922c30338171cc7b0529a36dc482b4a6`（**APPROVED / CLOSED**；
>   contract HEAD `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`）
> - C-04 approved contract — `8be09339bfae1c4a138b4ccb739f86302c60d627`（**APPROVED / FROZEN**）
> - C-04 approved implementation — `823bd9ce5c276dc473cc041efba409bd931c589f`（**APPROVED / CLOSED**）
> - 本记录基线（docs baseline）— `b1bada4fdfd7a1c453cbf2bedb5bf0c6e1d294fc`
>
> Evidence 消费声明：本 docs-only 收口候选**不重新生成、不重新运行任何可执行测试**；
> 它只消费 C-01 / C-04 已批准并封存的最终切片证据（`docs/evolune/v1.7/evidence/c-01/`、
> `docs/evolune/v1.7/evidence/c-04/`）。
>
> 上游（约束性输入，优先于本文件）：`V17_SPEC.md` §4/§9/§11/§12/§13 · `V17_PLAN.md` §6 ·
> `V17_ACCEPTANCE.md` §3/§4 · `V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md` ·
> `V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md` · `V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md`。

---

## 0. 历史 C-01…C-09 规划对账（冻结）

| 原始命名（`V17_PLAN.md` §6 原文保留，不重编号） | 收盘结论 |
|---|---|
| old C-01 inspect current PK source pipeline | **SUPERSEDED / consumed by C-00/C-01** |
| old C-02 historical event adapter | **COMPLETE / consumed by closed C-01** |
| old C-03 retrospective interval calculation | **COMPLETE / consumed by closed C-01** |
| old C-04 planned/actual markers | **COMPLETE / delivered by closed C-04** |
| old C-05 UI integration | **REQUIRED CORE COMPLETE / delivered by C-04**；optional interaction/detail residue 经审计确认**不构成** Phase-C closure 的缺失范围（§4/§5） |
| old C-06 numerical regression | **SUPERSEDED AS SEPARATE SLICE / satisfied as recurring gate**（in approved C-01/C-04 closures） |
| old C-07 timezone/DST | 同上（recurring gate） |
| old C-08 fresh verification | 同上（recurring gate） |
| old C-09 independent review | 同上（recurring gate） |

依据：本仓库 `V17_PLAN.md` §6 status notes（C-01 2026-09-15、C-04 2026-09-16 closure notes）与
`review-packets/v17-post-c04-remaining-scope-audit.txt` 的独立结论（本文件对映射做了独立复核，见 §1–§8）。

---

## 1. Phase-C 验收矩阵（V17_ACCEPTANCE.md §3 C1–C9 收口映射）

| # | authoritative requirement | implementing closed slice | implementation / source surface | final evidence / review proof | closure status |
|---|---|---|---|---|---|
| **C1** | 回顾性 PK 只消费权威实际事件（`dose_events` 行）；计划/预测事件不得混入历史曲线 | **C-01**（closed） | `HistoryReadService.readAllAvailable(...)` 全历史权威事件路径；`RetrospectivePkExtractor` 只从投影 matched/unmatched **event** 构造引擎输入（无 predictor / 无合成 dose） | `evidence/c-01`（extractor/service tests；C-01 spec §6/§7.3） | **CLOSED** |
| **C2** | 区间输入显式：给定 `[start, end]` 重建曲线（不再依赖 `currentTimeH ± 15d` 硬编码路径） | **C-01**（API）+ **C-04**（surface） | C-01：`RetrospectivePkRequest.visibleWindow`（显式 `[startInclusive, endInclusive]`，ms 对齐）与 `RetrospectivePkResult.calculatedInterval`；C-04：显式固定 rolling 720h（30×24h）visible interval（`RetrospectivePkViewModel.captureWindow`） | C-01 request/interval tests；C-04 W1–W4（720h / ms 归一 / 无未来段 / cursor null）与读取计数 0/1/3 证据（`evidence/c-04/junit/focused`） | **CLOSED**（closure interpretation 见 §2） |
| **C3** | 取数使用半开区间且有上界（不得把 `end` 之后的事件算入） | **C-01**（closed；frozen inclusive-endpoint reconciliation） | `readAllAvailable(upperBoundInclusive)` 只消费 `occurredAt <= upperBoundInclusive` 的权威行；extractor 对 `occurredAt.isAfter(window.endInclusive)` 直接 fail-fast（C-01 §5/§6）；C-04 `endInclusive = capturedAt` | C-01 all-history/upper-bound tests；C-04 MC3/MC4（边界时刻含入、±1ms 排除） | **CLOSED**（不重开 inclusive/half-open 语义） |
| **C4** | 前史处理显式（长尾 depot 起始条件）并文档化 | **C-01**（closed） | `lookbackStart = min(consumed occurredAt)`（精确）；`EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE` limitation 语义（C-00/C-01 frozen） | `evidence/c-01`（lookback/limitation 测试；C-01 spec §9） | **CLOSED** |
| **C5** | 计划/实际标记与差值来自 Phase A 投影，不重算匹配 | **C-04**（closed；closure meaning 见 §3） | ScheduleContextMarker ← `HistoryRangeSource`（Phase A 投影经既有 single reader）；RecordedIntakeMarker ← `AllAvailableHistorySource`（Phase A 投影）∩ 不可变 Read-1 accepted ID 集；无第二匹配实现（architecture guard 证明无 matcher/DAO/repository 复刻） | `evidence/c-04`（MS1–MS5 / MI1–MI12 / MC1–MC7；`RetrospectiveArchitectureGuardTest`） | **CLOSED**；actual-vs-planned **delta 不在本 closure 要求内**（gated，见 §5） |
| **C6** | 数值回归：黄金值不变 + 新增历史区间锁定值 | **C-01**（closed；recurring gate） | 未改动 `SimulationEngine` / `ThreeCompartmentModel` / `ParameterResolver` / `PKParameters`（Audit zero-diff）；C-01 默认 curve runner 调用未改动引擎 | `evidence/c-01`：golden AUC `23285.499354395688 ± 1e-9` / 固定采样；R4.2 runtime fixture AUC `48338.59081520633`；C-04 最终 fresh full JVM 1333 / 0 / 0 / 0（含全部既有 PK 测试） | **CLOSED** |
| **C7** | 时区/DST：历史区间在 DST gap/overlap 下不产生重复或丢失事件 | **C-01**（closed；recurring gate）+ **C-04**（窗口边界） | C-01：typed DST 解析（gap 前移 / overlap 取较早 offset）、ms 端点、极端时区覆盖；C-04：ms 归一端点、Instant-only 窗口成员判定 | `evidence/c-01`（ExtremeZone / DST / 毫秒端点测试）；`evidence/c-04`（W3/W4、MC3/MC4、wrapper 无重算） | **CLOSED**（release 级 obligations 见 §7） |
| **C8** | 呈现明确标注为模型估算，非实测血药浓度 | **C-04**（closed） | mandatory 常显 disclosure「模型估算——并非实测血药浓度。」（`RetrospectivePkScreen`，位于 phase body 之外）；不得只藏 dialog | C-04 R1 T4 三态设备闭合：CONTENT / UNAVAILABLE / ERROR 可见披露 PASS（`t4ContentStateShowsTheMandatoryModelEstimateDisclosure`、`t4UnavailableStateShowsTheMandatoryModelEstimateDisclosure`、`t4ErrorStateShowsTheMandatoryModelEstimateDisclosure`）；字符串双文件 parity | **CLOSED** |
| **C9** | fresh 验证 + 独立复审（必要时 Max 级二次复审） | **C-01** 与 **C-04** 各自独立收口 | C-01：final independent implementation review APPROVE（`APPROVED / CLOSED` @ `145d53b`）；C-04：final independent implementation review APPROVE（R1 T4 closure 后，`APPROVED / CLOSED` @ `823bd9c`） | `evidence/c-01` 与 `evidence/c-04` 最终 bundles（§8） | **CLOSED** |

---

## 2. SPEC historical-interval 解释（C2 closure interpretation，essential）

`V17_SPEC.md`:144 要求 “The retrospective PK view **must support a historical interval** and
reconstruct the PK curve using actual recorded medication events”。

**冻结解释：** C2 要求的是**显式 `[start, end]` 回顾性查询/区间**，其满足方式为：

1. **C-01** 的区间参数化 API/结果契约（`RetrospectivePkRequest.visibleWindow` +
   `RetrospectivePkResult.calculatedInterval`，任意合法 `[start, end]` 可用）；
2. **C-04** 的显式固定 rolling **720h（30×24h）** visible interval（显式区间，不是隐藏的
   `currentTimeH ± 15d` 硬编码路径）。

**C2 不要求：** user-selectable preset selector、custom date picker、cursor UI。

Disposition（与 post-C04 audit 一致，独立复核后维持）：

| capability | disposition |
|---|---|
| preset interval selector | OPTIONAL / POLISH（NOT missing Phase-C scope） |
| custom range picker | OPTIONAL / POLISH |
| cursor readout | OPTIONAL / requires new product contract（docs 未要求） |
| cursor scrubbing | NOT REQUIRED |

---

## 3. C5 marker-vs-delta reconciliation（冻结）

C5 的 required closure meaning：

- 计划/日程上下文标记（ScheduleContextMarker）与已记录摄入标记（RecordedIntakeMarker）
  **derive from the Phase-A historical projection / approved historical seams**；
- **不引入第二匹配实现**（no second matcher / no eligibility re-implementation）；
- matched 与 unrecorded 同等贡献 schedule-context marker；unmatched accepted intake 只贡献
  recorded-intake marker。

**明确记录：actual-vs-planned timing delta 不是 Phase-C closure 的要求。**
它保持为独立 gated 的 Option-2 / product semantic（`V17_SPEC.md`:150 只是 "may display" 的示例；
C-04 §16 gated；TODO gated line）。C5 closure **不授权**：

- early / late / on-time；
- adherence / compliance；
- historical prescription truth；
- timing score。

---

## 4. Phase-C product-completeness statement

**Every REQUIRED capability in `V17_SPEC.md` §4 and `V17_ACCEPTANCE.md` §3 C1–C9 is implemented
and closed**（C-01 + C-04；见 §1）。

剩余 Phase-C-adjacent 想法**不阻塞 closure**，且不得被静默转成 Phase-C backlog requirements：

| 剩余想法 | disposition |
|---|---|
| preset interval selector | OPTIONAL / POLISH |
| custom range picker | OPTIONAL / POLISH |
| cursor readout | OPTIONAL / requires new product contract |
| cursor scrubbing | NOT REQUIRED |
| per-marker detailed popup / 文本明细（time / medication / route / dose） | OPTIONAL / **primarily Phase-D territory**（见 §6） |

---

## 5. Gated candidates（保持 UNAUTHORIZED）

Phase-C closure **不授权**以下任何一项；推进需各自的新语义 contract 批准：

- daily series / chart as a new product semantic；
- coverage percentage；
- Option-2 timing metric / actual-vs-planned delta；
- anti-androgen real-identity product expansion；
- optional CPA PK curve（保持独立科学/来源门槛）。

依据：`V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md` §16、`TODO.MD` 的 gated bullet、
`V17_SPEC.md` §11 Non-Goals、以及 C-04 §13 F12/F14 guards。

---

## 6. Phase-C / Phase-D boundary（冻结）

- **Phase C own**：retrospective PK reconstruction 与其 read-only surface
  （曲线、markers、disclosure、unavailable/limitations、activation refresh）。
- **Phase D own**：Medication Timeline / chronological per-event detail
  （`V17_PLAN.md` §7 D-01…D-07；`V17_ACCEPTANCE.md` §5 D1–D5；`V17_SPEC.md` §5 的逐事件呈现示例）。

因此以下事项**应在 Phase D 评估**，而不是重开 Phase C：medication name、route、dose、
event time/detail presentation、chronological event rows。

未来若产品确需 retrospective-specific selector/cursor，可获其自己的小型 contract，
**但它不是进入 Phase D 之前的要求**。

---

## 7. Cross-phase T obligations（release-gate 区分）

- `V17_ACCEPTANCE.md` §3 C1–C9 是 **Phase-C acceptance**（本文件收口对象）。
- `V17_ACCEPTANCE.md` §4 T1–T* 是 **全阶段共用 / v1.7 release 前义务**
  （§4 末行明确 “T1–T5 必须在 v1.7 release 之前具备确定性覆盖”；`V17_SPEC.md`:312
  “DST tests are mandatory before v1.7 release”）。

记录：与 Phase C 相关的 C-stage DST/time 测试（C-01 gap/overlap/extreme-zone、C-04 窗口边界）
已支持 Phase C；但任何被明确指定为 **final v1.7 release gate** 的验收项仍保持 release-gate
义务。**Phase C closure 不将未来 release gates 标记为全局完成。**

---

## 8. Evidence inventory（消费既有已批准证据；不重新生成）

C-01（final approved evidence，concise）：

- approved implementation `145d53bd922c30338171cc7b0529a36dc482b4a6`；contract HEAD
  `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`；evidence `docs/evolune/v1.7/evidence/c-01/`。
- 最终验证（C-01 closure 记录）：1260 JVM tests / 0 failures / 0 errors / 0 skipped ·
  fresh 54/54 Gradle tasks executed · Room instrumentation 1/1 PASS（Pixel_7 AVD API 35）·
  golden PK regression preserved · zero-write PASS。

C-04（final approved evidence）：

- approved contract `8be09339bfae1c4a138b4ccb739f86302c60d627`；approved implementation
  `823bd9ce5c276dc473cc041efba409bd931c589f`；evidence `docs/evolune/v1.7/evidence/c-04/`。
- focused C-04 JVM：**73 / 0 / 0 / 0**；
- fresh full JVM：**1333 tests / 0 failures / 0 errors / 0 skipped**；
- Gradle：**54 / 54 actionable tasks executed**；
- affected instrumentation：**14 / 14 PASS**（Pixel_7 AVD API 35）；
- C-04 evidence：**169 / 169** data-manifest coverage · **171 / 171** HEAD blob verification ·
  **0 mismatches**。

> Phase-C closure consumes the already approved final-slice evidence; this docs-only closure
> candidate does not regenerate or rerun executable tests.

---

## 9. Phase gate（APPROVED）

`V17_PLAN.md` §6 的 Phase gate：

`APPROVE V1.7-C CANDIDATE IMPLEMENTATION`

最终状态：

- **APPROVED** —— 独立/架构 Phase-C closure review 通过；
- Approved Phase-C closure candidate HEAD：`19652baa07b5057f4aa6c07a79a77a158ac31468`；
- C-00 APPROVED / FROZEN；C-01 APPROVED / CLOSED；C-04 APPROVED / FROZEN + APPROVED / CLOSED。

**Phase C is CLOSED.**
**No further Phase-C production changes are authorized without reopening review.**
后续切片必须消费已批准的 C-01 retrospective PK API/结果契约与 C-04 只读 surface 行为。

---

## 10. Change log

| 日期 | 变更 |
|---|---|
| 2026-09-16 | 初始创建：PHASE-C CANDIDATE CLOSURE — REVIEW PENDING（docs-only；无生产/测试/证据变更）。 |
| 2026-09-16 | Final closure bookkeeping：状态更新为 `PHASE-C CLOSED — APPROVED`；记录 gate 批准、approved closure candidate HEAD `19652baa07b5057f4aa6c07a79a77a158ac31468`；P3 typo `DAW` → `DAO`；未改动 C1–C9 语义。 |
