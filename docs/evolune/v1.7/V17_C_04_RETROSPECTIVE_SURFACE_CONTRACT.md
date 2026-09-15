# V17-C-04 — Retrospective PK Surface & Markers — Contract

> 状态：`CONTRACT — R3 CORRECTED / RE-REVIEW PENDING`（docs-only contract；**C-04 生产未开始**）
> Round：v1.7-C / **C-04**（Schedule-Context / Recorded-Intake markers & retrospective PK surface, MVP）
> R1：marker-window 修正（range seam 覆盖）已 ACCEPTED。R2：marker scope 与 policy 修正已 ACCEPTED。
> R3：本轮只闭合 non-atomic same-ID mutation truthfulness gap，见 §0.1；其余已接受决定不变。
> Base HEAD：`24f7d41963bec3616bd6467545d1f3fa6c298837`（C-01 closure docs）
> 上游（约束性输入，**优先于本文件**）：
> - [`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`](V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md)（R4 corrected，FROZEN）
> - [`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`](V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md)
>   （IMPLEMENTATION SPEC / implemented / CLOSED；approved implementation HEAD
>   `145d53bd922c30338171cc7b0529a36dc482b4a6`；evidence `docs/evolune/v1.7/evidence/c-01/`）
> 关联：`V17_SPEC.md` §4/§12 · `V17_PLAN.md` §6（原始 C-01…C-09 命名保留）· `V17_ACCEPTANCE.md` §3 ·
> B-03 Insights 先例（History 入口 + 子路由 + activation refresh + generation token）·
> B-00 `MedicationIdentityClassifier`（单一 medication identity 分类器）· A-03 range read seam（`HistoryRangeSource`）。
> 本文件只定义 **C-04 契约**。生产实现必须在独立契约复审 APPROVE 之后另行开工；
> 本文件本身不实现任何 Kotlin/Compose 代码、不修改测试、不触碰 C-01 生产。

---

## 0. Reconciliation freeze（规划对账冻结）

| 原始命名 | 冻结结论 |
|---|---|
| C-01 inspect current PK source pipeline | **SUPERSEDED / consumed**（结论并入 C-00 语义 + C-01 实现规格） |
| C-02 historical event adapter | **COMPLETE / consumed by closed C-01**（全历史只读通道 + 投影 + 提取/资格/排序/贴片预处理） |
| C-03 retrospective interval calculation | **COMPLETE / consumed by closed C-01**（可见/计算区间、366 天门、网格、Instant series、typed 结果） |
| C-04 planned/actual markers | **NOT STARTED** → 本契约（active next slice） |
| C-05 UI integration | **NOT STARTED** → 由本契约的 surface 范围承接（MVP） |
| C-06…C-09 | **SUPERSEDED**，转为**每个切片**都必须重复执行的验证/复审 gates（§15） |

**Active next slice = C-04 — Schedule-Context / Recorded-Intake Markers & Retrospective PK Surface (MVP)。**
不重编号为 C-02；`V17_PLAN.md` §6 的原始命名序列保持原文，只通过 status note 指向本契约。

**C-01 冻结不变量**：未经重新开启评审，不得修改 `app/.../history/pk/**` 的任何生产代码或结果/API 语义；
C-04 只**消费**已批准接缝（`RetrospectivePkSource`、`AllAvailableHistorySource`、`HistoryRangeSource`、
`RetrospectivePkResult`）。

### 0.1 Correction log（architect review 处置；未列出的决定一律不变）

| # | 轮次 | 级别 | 项 | 处置 |
|---|---|---|---|---|
| R1-1 | R1 | P1 | `AllAvailableHistorySource` 单独无法证明 ScheduleContextMarker 在固定 30 天窗口内的完整覆盖（occurrence context 锚定最早事件日期） | ScheduleContextMarker 改由既批准 range seam `HistoryRangeSource` 提供；新增覆盖回归 MC1–MC7；C-01 行为不改（已 ACCEPTED） |
| R1-2 | R1 | P2 | occurrence policy 未在多次读之间显式共享 | 单次捕获包含 `MedicationOccurrencePolicy`；全部读共享捕获值（R2 进一步修正为 value/semantics 等价，见 R2-2） |
| R1-3 | R1 | P2 | EN runtime-localization 措辞与资源结构冲突 | EN 仅为规范参考（documentation-only）；实际落位遵循现有中文资源结构（已 ACCEPTED） |
| R2-1 | R2 | P1 | marker medication / PK-input scope 未冻结 | §D-6/§3 对两家族分别冻结 scope：RecordedIntakeMarker = 对 Read 1 `Available.summary` 的 **ID join**（`engineInputEventIds − patchControlEventIds`）∩ Read 2 权威事实；ScheduleContextMarker = 复用 B-00 `MedicationIdentityClassifier` 的 **KNOWN** 身份过滤（ANTIANDROGEN/PARTIAL 不显示；PATCH_REMOVE 显式排除，含可达性证明）。新增 MS1–MS4 / MI1–MI8；MC1–MC7 措辞改为 in-scope |
| R2-2 | R2 | P2 | “same policy instance 到达三次读”不可达（Read 3 无 policy 参数） | §D-3/§4.1/§6 改为冻结：每 load 捕获一次 canonical current policy **值**；Read 1/2 接收该值；Read 3 使用其冻结默认值；不变量为 **value/semantics 等价**，非对象同一性；并记录 reopen trigger |
| R3-1 | R3 | P1 | non-atomic same-ID mutation truthfulness：同一 `event.id` 可在 Read1→Read2 之间被编辑（id 不变），ID 成员资格**不**证明 payload 一致；“markers show recorded inputs used by this estimate” 属 overclaim | §2.1.4 源码证明；§3.2A 谓词扩展为 5 条件（ID join + 当前 Read2 payload 的 classifier KNOWN + routeKey≠PATCH_REMOVE）；§4.2.1 增加 Case C 同 ID 变更规则与“证明/不证明”边界；披露改为 truthful wording（§9/§11）；MI9–MI12 + T6；§12.1/§16 明确拒绝重开 C-01（无 fingerprint/snapshot/事务/retry-until-equal） |

R1/R2 未触碰的已接受决定（窗口/毫秒归一化/cursor null/无 range selector/无 timing-delta/无 adherence/
`Current schedule context`/`Recorded intake`/R4.2 series/无 clamp/薄 retrospective chart/无 now 线/无未来预测/
History→子路由/activation refresh + generation token/可见 disclosure/四个 unavailable/四个 limitation/
zero-write/无缓存/无 schema 改动/无 PK 模型改动/Home·Wear·Widget 零 diff/无新依赖/gated candidates/
`HistoryRangeSource` 覆盖方案/三次读非原子架构）全部保持原状。

---

## 1. Architect decisions（逐条冻结）

### D-1 编排接缝（consumed seams；R1/R2 修订）

幕面编排只允许消费三条已批准接缝，**角色互斥**：

- `RetrospectivePkSource` → 数值 retrospective curve + 其 `Available.summary`（**PK 成员资格的唯一权威**）；
- `AllAvailableHistorySource` → **仅** RecordedIntakeMarker 的权威事件事实（Read 2）；
- `HistoryRangeSource` → **仅** ScheduleContextMarker 的 current-schedule occurrence 上下文（Read 3）。

所有生产实现都仍然由**同一个** `HistoryReadService` 支撑（`readAllAvailable` + `readRange`），
**不是**第二个 historical reader。

**不得**在 presentation/domain 编排中消费具体 `HistoryReadService` 类型——已批准接缝足够时，边界收敛在接缝上。
**不得**出现 repository/DAO/Room 访问；不得新增第二个 history reader、第二个 matcher/generator 实现、第二个权威存储。
**不得**修改 `HistoryRangeSource` / `readRange` 签名（F17）；**不得**改动 C-01 API/result。

### D-2 三次顺序读取，显式 NON-ATOMIC（R1 修订；R2 补充 ID-join 语义）

成功的 CONTENT load 的 history 层读取序列（每次 load 固定顺序，全部**顺序执行、显式非原子**）：

1. **Read 1**：`retrospectivePkSource.estimate(request)`（内部执行 C-01 all-history read）；
   - 返回 `Unavailable` → **停止** → `UNAVAILABLE`，**不执行**任何 marker read；
   - 抛分类异常（C-01 内部 typed 契约违例/读失败）→ **停止** → `ERROR`，**不执行**任何 marker read；
   - 只有返回 `Available` 才继续。
2. **Read 2**：`allAvailableHistorySource.readAllAvailable(upperBoundInclusive, displayZone, policy)` →
   RecordedIntakeMarker 事实（与 Read 1 summary 做 ID join，§3.2A/§4.2.1）。
3. **Read 3**：`historyRangeSource.read(startDate, endDate, displayZone, now)` →
   ScheduleContextMarker occurrence 事实（按身份 scope 过滤，§3.2B）。

读取计数矩阵（冻结）：

| 路径 | Read 1 | Read 2 | Read 3 | history 层读取总数 |
|---|---|---|---|---|
| 无效体重（§6.3） | 不执行 | 不执行 | 不执行 | **0** |
| estimate 抛异常 | 失败中止 | 不执行 | 不执行 | 1（尝试） |
| estimate 返回 Unavailable | 完成 | 不执行 | 不执行 | **恰好 1** |
| Available，Read 2 失败 | 完成 | 失败中止 | 不执行 | 2（尝试）→ `ERROR` |
| Available，Read 3 失败 | 完成 | 完成 | 失败中止 | 3（尝试）→ `ERROR` |
| **CONTENT** | 完成 | 完成 | 完成 | **恰好 3** |

- Read 2 或 Read 3 失败 → 整个 load `ERROR`，**不发布部分 curve/marker CONTENT**。
- Manual Retry 启动**完全新的 load/generation**（新捕获、新窗口滚动）。
- **不得**为伪造原子性而添加 retry/合并读；**不得**发明 combined snapshot API；**不得**在三次读之间插入额外 read。
- 三次读之间的数据变化按 §4.2.1 的 non-atomic join 规则处理：**不回退、不 ERROR、不重试**。

### D-3 每次 load 只捕获一次上下文（R1 建立；R2 修正 policy 语义）

每次 load 捕获一次并全程复用：

- `capturedAt`
- `displayZone`
- `visibleWindow`
- `upperBoundInclusive`
- `MedicationOccurrencePolicy`（**canonical current policy 值**，见下）
- `currentBodyWeightKg`（C-01 冻结的 `CURRENT_SETTING_AT_QUERY_TIME` 值）

**Policy 语义（R2 冻结）**：

- 每 load C-04 只创建/捕获一次 canonical current policy **值**；
- Read 1 接收该捕获值；Read 2 接收该捕获值；
- Read 3 经 `HistoryRangeSource`（签名无 policy 参数）使用其生产实现的 current canonical default policy；
- 不变量：`capturedPolicy == HistoryRangeSource effective default policy` **按值/语义等价**，**不是对象同一性**；
- 当前不存在任何 user-configurable / non-default policy；
- **Reopen trigger**：若未来引入 configurable occurrence policy、non-default policy、default 值变更或
  HistoryRangeSource policy 注入，C-04 marker 语义**必须**重新开启评审（§16）。

**不得**在不同编排位点各自创建默认 `MedicationOccurrencePolicy()` 实例作为“共享”证据；
recomposition 不得触发新读取。

### D-4 固定 MVP 时间窗

- 恰好一个区间，不可选：
  - `endInclusive = capturedAt` normalized to epoch-millisecond precision（`Instant.ofEpochMilli(...)`，毫秒截断）；
  - `startInclusive = endInclusive.minus(Duration.ofDays(30))`（恰好 30×24 小时）。
- 语义 = “截至查询时刻的最近 30×24 小时”；**没有**今天的未来段、没有 range selector、没有 presets、没有 date picker。
- `cursor = null`；**没有** cursor UI、scrubbing、cursor 浓度读数。

### D-5 体重（body weight）

- 保持 C-01：`BodyWeightBasis.CURRENT_SETTING_AT_QUERY_TIME`；复用现行生产的**只读 current-body-weight 接缝**（§6）。
- **不得**新增：历史体重重建、体重快照、新持久化、fallback 历史推断。
- 当前体重不满足已冻结 C-01 有效性规则（`isValidBodyWeight`）时的行为按 §6.3 冻结。

### D-6 Marker 语义（仅两个用户可见家族；R2 冻结 scope）

两个家族**没有相同的 eligibility 规则**；共同的仅有：最终包含按各自 Instant 的闭区间判定。

**A. `RecordedIntakeMarker`（PK-input scope）**

它代表**被冻结 C-01 estimate 接受的已记录 E2-PK 输入**，不是 AllAvailableHistory 里的每一行药。

对 `Available` 结果定义（ID join，权威来自 Read 1 summary）：

```text
eligibleRecordedMarkerIds =
    Available.summary.engineInputEventIds
    MINUS
    Available.summary.patchControlEventIds
```

Read 2 仍从 `AllAvailableHistorySource` 获取权威事件事实。**ID 成员资格只证明**：“一个具有该 ID 的权威事件
曾被 Read1 接受为 non-control 引擎输入”；它**不证明** Read2 payload 与 Read1 消费的 payload 在字节/值上一致
（§2.1.4）。一枚 `RecordedIntakeMarker` 满足全部条件才发出（R3 起 5 条）：

1. entry 是 `MatchedHistoricalOccurrence` 或 `UnmatchedHistoricalIntake`；
2. `event.eventId ∈ eligibleRecordedMarkerIds`；
3. `event.occurredAt ∈ visibleWindow`（闭区间）；
4. `MedicationIdentityClassifier.classify(event.matchKey).status == KNOWN`（对**当前 Read2 payload** 的轻量呈现守卫）；
5. `event.matchKey.routeKey != "PATCH_REMOVE"`（对**当前 Read2 payload** 的轻量呈现守卫）。

第 4/5 条**不是** C-01 eligibility 复跑：不得 parse ester、不得检查 release-rate extras、不得重建
route/ester 资格、不得调用 `RetrospectivePkExtractor` / `ParameterResolver` / `SimulationEngine`、
不得判定 concentration-producing。它们只防止**并发变更后的当前 Read2 payload** 被渲染为 estrogen-intake marker。
Read2 payload 与 Read1 数值快照在 dose/time/ester 上的差异属于已声明的 non-atomic 边界，
**不得**被描述为 exact numerical-input snapshot（§4.2.1 Case C / §9 wording）。

Consequences（冻结）：

- `PATCH_REMOVE` **永不**成为 “Recorded intake”；
- C-01 排除的 ANTIANDROGEN **永不**成为 marker；
- C-01 excluded 的 unsupported/incomplete 事件**永不**成为 marker；
- ambiguous excluded patch transition **永不**成为 marker；
- 合法的非-control 零贡献引擎输入（C-01 保留在 `engineInputEventIds`）**仍是** RecordedIntakeMarker；
- matched 与 unmatched 的 accepted actual 事件都可以成为 marker；
- marker 构造**不得**重跑 C-01 eligibility；**不得**检查 PK 公式/route/extras 来决定 C-01 资格；
- **C-01 summary IDs 是 PK 成员资格的唯一权威**；
- 这是对 Read 2 权威事实的 **ID join**，不是第二份 eligibility 实现；
- **不得**只用 `concentrationProducingEventIds`（会丢掉合法保留的零贡献 non-control 输入）；
- **不得**把 excluded 事件渲染成正常 RecordedIntakeMarker（其存在已由 C-01 exclusions /
  `EXCLUDED_RECORDED_INTAKES` limitation 表示）。

**B. `ScheduleContextMarker`（contextual only；identity scope）**

它**永远不是**数值 PK 输入。来源保持 Read 3（`HistoryRangeSource`），但**不得**在 E2 PK 幕面显示无关药物方案。

对每个候选 occurrence：

```text
identity = MedicationIdentityClassifier.classify(occurrence.presentation.matchKey)
```

仅当 identity 为 `KNOWN` 且 `occurrence.scheduledAt ∈ visibleWindow`（闭区间）时发出 marker。
另外：**显式排除 `PATCH_REMOVE`**（routeKey == "PATCH_REMOVE"；removal 不是 scheduled intake marker；
其可达性证明见 §2.1.3，因此必须显式排除而不是依赖不可达假设）。

因此：

- E2 / EB / EV / EC / EN 的 current schedule context 可以显示；
- ANTIANDROGEN current schedule occurrence **不显示**（classifier → UNAVAILABLE）；
- PARTIAL / unknown / foreign medication occurrence **不显示**；
- **不**创建 synthetic dose；**不**重跑 C-01 eligibility；
- **不**推断该 scheduled occurrence 对数值曲线有贡献；**不**作 historical prescription 声明；
- matched 与 unrecorded 分类**不改变** schedule-context marker 是否存在。

**共同规则**：

- 强制用户可见标签：`Current schedule context` /「当前方案上下文」· `Recorded intake` /「已记录摄入」；
- MVP **不得**暴露：confidence score、punctuality、actual-vs-schedule delta、early/late、on-time、adherence classification；
- 未记录的 schedule occurrence 可以表现为 schedule-context marker，但**永不**标记 missed/skipped；
- unmatched recorded intake 表现为 Recorded intake marker，**不得**凭空发明对应 schedule marker；
- **禁止**第三家族（excluded / unsupported / anti-androgen）；excluded-input 不确定性只留在 C-01 limitation 文案。

### D-7 曲线

- 只渲染已批准的 `RetrospectivePkResult.Available.series`，作为一条连续 retrospective model-estimate curve。
- presentation 层**不做**任何数值重算；**不做**浓度归一化/clamp。
- **不得**引入：daily aggregation、daily-series 产品语义、coverage 百分比、timing metric、anti-androgen identity、CPA curve。
- 历史图表行为：**没有** live/current "now" 线、**没有**未来预测、**没有** Home 式 current baseline、
  **没有** live 36 小时视窗语义；viewport = retrospective `visibleWindow`。
- 图表复用决定（source-verified，§2.4）：`ConcentrationChart` 的 live 语义无法在不改变 Home/Wear/Widget 行为的前提下关闭
  → 使用**薄 retrospective-only presentation component**，只复用既有 Compose/chart 原语与内部 geometry helpers。
- **不得**新增图表依赖。

### D-8 入口与导航

- 冻结：`History → Retrospective PK entry card → Retrospective PK sub-route`；Back 返回 History。
- **不得**新增 bottom-navigation tab；沿用既有 Insights 子路由先例（§7）。

### D-9 Refresh

- 冻结：activation-based refresh；A-04 风格 generation token / stale-result rejection；typed load failure 提供手动 Retry。
- **不得**：live subscription、polling、background refresh loop、result persistence/cache。

### D-10 Model-estimate disclosure

- 必须可见的等价措辞：`Model estimate — not a measured blood concentration.` /「模型估算——并非实测血药浓度。」
- 必须在 retrospective 屏幕上**直接可见**，不得只藏在 info dialog 中。
- **R2 新增、R3 修正的第二强制披露**（marker 图例，见 §9/§11）：
  - EN canonical reference：`Schedule markers show current-plan context. Recorded markers show the currently recorded intakes linked to this estimate; recent edits may appear after the curve was calculated.`
  - zh 产品落位：`方案标记表示当前方案上下文；摄入标记显示与本次估算关联的当前记录。若记录刚刚被修改，标记内容可能比曲线更新。`
  - 该披露**不得**暗示：historical prescription truth、adherence、missed/skipped、punctuality、超出冻结模型的因果确定性，
    也不得声称 markers 是曲线的 exact/same snapshot（§12.1 R3 词表）。
- C-01 `ModelContext` 可作为 secondary detail 暴露，但**不得**发明任何科学性/准确性声明。

### D-11 Typed unavailable UX

C-01 typed reasons 内部保持不变；幕面语义映射冻结如下（wording 见 §11）：

| C-01 reason | 幕面语义 |
|---|---|
| `NO_ELIGIBLE_RECORDED_INTAKES` | No eligible recorded intakes are available for this estimate. |
| `INVALID_QUERY_INTERVAL` | This time range cannot be calculated. |
| `QUERY_OUTSIDE_CALCULATED_INTERVAL` | 防御性 generic “estimate unavailable” 状态 |
| `HISTORICAL_INPUT_UNAVAILABLE` | Historical records could not be read completely. Try again. |

C-01 的四条 limitation 保持披露；至少为用户可见定义语义：
`EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE` · `UNRECORDED_OCCURRENCES_PRESENT` · `EXCLUDED_RECORDED_INTAKES` ·
`AMBIGUOUS_PATCH_PAIRING`；**不得**翻译成 adherence 判断。

### D-12 Wording guards

新增 retrospective 用户可见字符串不得出现：adherence / compliance / missed / skipped / late / on-time / delay（及中文对应词，§12）。
浓度措辞不得暗示：measured concentration / actual concentration / true blood level / lab result（及中文对应词）。
`Model estimate` 与 `not a measured blood concentration` 的语义**强制存在**（§10/§12）。

### D-13 禁止修改面（violate ⇒ STOP）

C-04 不得修改：

- `app/.../history/pk/**`；C-01 result/API 语义；
- `SimulationEngine`、`ThreeCompartmentModel`、`ParameterResolver`、`PKParameters`；
- Room schema / entities / migrations / indexes；
- `DoseEventDao` 历史真相语义；
- Home PK orchestration、Wear PK orchestration、Widget PK orchestration。

**不得**新增第二个 history reader、第二个 matcher、新权威存储；**不得**写入（全路径 zero-write）。

### D-14 本任务边界（R2 轮）

本轮为 **DOCS-ONLY contract correction（R2）**：只修订本文件并 amend 同一 contract commit；
不实现代码、不改测试、不触碰 C-01 生产、不 push/merge/tag/release。

---

## 2. Source-verified integration inventory（逐文件核对，2026-09-16）

> 以下每一项均已从当前源码核对；标注 `line` 为当前 source HEAD 的行锚。标注 **NEW** 的是实现轮将创建的规划面，
> 其文件名/API 在实现评审时最终确认；其余均为已存在、可直接消费的面。

### 2.1 消费的已批准接缝（EXISTS）

| 面 | 位置 | 核对事实 |
|---|---|---|
| `AllAvailableHistorySource` / `AllAvailableHistory` | `app/src/main/java/io/github/yingqiu0871/evolune/history/AllAvailableHistory.kt:15-37` | seam 只有 `readAllAvailable(upperBoundInclusive, displayZone, policy)`；结果携带 `projection: HistoricalProjection` |
| `HistoryRangeSource` | `app/src/main/java/io/github/yingqiu0871/evolune/history/HistoryRangeSource.kt:22-29` | `read(startDate, endDate, displayZone, now): HistoricalRange`；**签名不含 policy**；返回的 range 只含 domain entries（matched + arrived-unrecorded；future occurrences 刻意不在该类型内） |
| `HistoryReadService.readRange` | `app/src/main/java/io/github/yingqiu0871/evolune/history/HistoryReadService.kt:62-124` | 生产实现（唯一 reader）：current plans → occurrence context（±1 天）→ 单一 projection（now = 传入 instant）→ `HistoricalReadModel.range` |
| `HistoricalRange` / `HistoricalReadModel.range` | `experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/HistoricalReadModel.kt:50-80` | 按 **displayDate** ∈ [startDate, endDate] 过滤并分组；entry 顺序冻结 |
| `RetrospectivePkSource` | `app/src/main/java/io/github/yingqiu0871/evolune/history/pk/RetrospectivePkModels.kt:393-395` | `suspend fun estimate(request): RetrospectivePkResult` |
| `RetrospectivePkRequest` / `RetrospectivePkWindow` | 同上 `:123-180`、`:234-248` | window 端点必须毫秒对齐；`cursor` 可为 null；`policy` 默认 `MedicationOccurrencePolicy()` |
| `RetrospectivePkInputSummary` | 同上 `:308-314` | `engineInputEventIds` / `concentrationProducingEventIds` / `patchControlEventIds`（PK 成员资格权威） |
| `RetrospectivePkResult.Available/Unavailable`、`RetrospectivePkModelContext`、limitations/unavailable 枚举 | 同上 `:196-228`、`:254-355` | typed reasons 与 limitations 为冻结 taxonomy |
| summary 构造（服务内） | `app/src/main/java/io/github/yingqiu0871/evolune/history/pk/RetrospectivePkService.kt:84-90` | summary 直接来自 extraction 的三个 ID 列表 |
| `RetrospectivePkExtractor` | `app/src/main/java/io/github/yingqiu0871/evolune/history/pk/RetrospectivePkExtractor.kt:32-231` | ANTIANDROGEN exclusion `:115-123`；INJECTION×E2 exclusion `:128-135`；unsupported/incomplete exclusion `:141-154`；patch grammar `:71-94`；PATCH_REMOVE → `patchControlEventIds` `:102-104`；`engineInputEventIds` `:96-98`；`concentrationProducingEventIds` `:99-101`；producing 定义 `:195-222` |
| `RetrospectivePkService` 公开构造 | `app/src/main/java/io/github/yingqiu0871/evolune/history/pk/RetrospectivePkService.kt:25-33` | `constructor(history: AllAvailableHistorySource)`——composition root 可只为它提供 history seam |
| `HistoryReadService` 同时支撑全部三条 seam | `app/src/main/java/io/github/yingqiu0871/evolune/history/HistoryReadService.kt:58,62,135` | `readRange(...)` 与 `override readAllAvailable(...)` 同一实例（`HistoryRangeSource` 的生产绑定即 `HistoryReadService::readRange`；既有 wire 先例 `InsightsViewModelFactory` 包装 `historyReadService.readRange`） |
| 投影事实形状（intake markers 的事实来源） | `experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/HistoricalProjection.kt:62-182` | `MatchedHistoricalOccurrence`（有 occurrence + event）、`UnrecordedHistoricalOccurrence`（仅 occurrence）、`UnmatchedHistoricalIntake`（仅 event）、`futureOccurrences`（`:151-157,169`，非 history） |
| occurrence 形状（schedule markers 的来源） | `experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/MedicationOccurrence.kt:79-88` | `id` / `scheduledAt: Instant` / `scheduledLocalDateTime` / `presentation.matchKey: MedicationMatchKey`（`:34-42`）——足够创建 ScheduleContextMarker 并做身份判定 |
| **身份分类器（复用，不新建）** | `experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights/MedicationIdentityClassifier.kt:16-31` | 输入 `MedicationMatchKey`：`ANTIANDROGEN` routeKey（常量 `:19`）→ `UNAVAILABLE`；`medicationKey ∈ {E2,EB,EV,EC,EN}` → `KNOWN`；否则 `PARTIAL` |
| 身份枚举 | `experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/insights/InsightsModels.kt:14-47` | `MedicationIdentityKey {E2, EB, EV, EC, EN}`；`MedicationIdentityStatus {KNOWN, PARTIAL, UNAVAILABLE}`；`MedicationIdentity(status, key)` |
| 触发 PATCH_REMOVE 可达性的 plan 写入面 | `app/src/main/java/io/github/yingqiu0871/evolune/core/presentation/MedicationOccurrenceDomainMapper.kt:18-44`（`toMedicationSchedule` 不做 route 过滤）· `app/src/main/java/io/github/yingqiu0871/evolune/backup/EvoluneBackupCodec.kt:72,996-998`（plan route 校验集合含 `PATCH_REMOVE`）· `app/src/main/java/io/github/yingqiu0871/evolune/data/RoomRestorePersistence.kt:50`（restore 写入 plans）· `ui/components/MedicationPlanBottomSheet.kt:391`（UI 创建过滤掉 PATCH_APPLY/PATCH_REMOVE，但 restore 路径不受此过滤约束） | §2.1.3 证明：PATCH_REMOVE occurrence 可达 |
| 同 ID 可变事件（R3 证明） | `app/src/main/java/io/github/yingqiu0871/evolune/application/DoseEventEditor.kt:123-173`（`session.original.copy(...)` `:154-162`，**不重赋 id**；expectedRevision `:167-170`）· `core/model/DoseEvent.kt:10-22`（可变字段 + `revision`）· `core/dataapi/DoseEventRepository.kt:58-61`（`update(..., expectedRevision): UpdateResult`）· `data/repository/RoomDoseEventRepository.kt:124-160`（同 row 更新 `:147`；`revision + 1` `:131`；revision conflict `:141-144`） | §2.1.4：same-ID mutation 可达 |

### 2.1.1 Marker coverage finding（R1 P1 源码证明）

**事实（`readAllAvailable`，`HistoryReadService.kt:135-227`）：**

- `events.empty` → 直接返回**空 projection**（occurrences = emptyList；`:153-165`）——该行为正确且**冻结**，本轮不改；
- `events.nonEmpty` → occurrence context 起点锚定于最早 authoritative event 的候选日期：
  `candidateDates = events.map { localDate ?: occurredAt.atZone(displayZone).toLocalDate() }`（`:171-174`），
  `contextStartDate = candidateDates.min().minusDays(OCCURRENCE_CONTEXT_DAYS)`（`:176`，OCCURRENCE_CONTEXT_DAYS = 1），
  上界锚定 `max(candidateDates.max(), upperBoundLocalDate) + 1`（`:175-183`）。

**确定性推论：** 设 `T = capturedAt`，窗口起点 `T−30d`；若最早 authoritative event 在 `T−2d`，
则 occurrence context 起点 ≈ `T−3d` ⇒ `readAllAvailable` **不可能**为 `T−30d … T−3d` 区间 materialize
任何 current-plan occurrence ⇒ 该区间的 ScheduleContextMarker 无法由 `AllAvailableHistorySource` 证明完整。
极端情形（零 authoritative events）：all-history projection 完全为空（零 occurrence）。

**结论（R1 处置，已 ACCEPTED）：** ScheduleContextMarker 的来源必须是与窗口相交的完整 local-date 上下文，
即既批准 range seam（D-1/D-6）。C-01 保持冻结，不修改。

### 2.1.2 Range seam 适用性证明（R1：结论 = 足以复用，NOT BLOCKED）

对 `W = [window.startInclusive, window.endInclusive]`（Instant，闭区间），令：

- `startLD = window.startInclusive.atZone(displayZone).toLocalDate()`
- `endLD = window.endInclusive.atZone(displayZone).toLocalDate()`
- Read 3 请求：`readRange(startLD − 1d, endLD + 1d, displayZone, now = capturedAt)`

证明链（全部基于现有单一实现，不引入新 matcher/generator/reader）：

1. **生成覆盖**：`readRange` 用既有 `MedicationOccurrenceGenerator`（唯一 recurrence 实现，经
   `generateContextOccurrences` 分块，`HistoryReadService.kt:261-280`）覆盖 local dates
   `[startLD−1, endLD+1]`（`:77-101`）——任何 `scheduledAt ∈ W` 的 occurrence 其 local date 落入
   `[startLD, endLD]`（固定 zone 下 instant→local date 单调），必然被生成；`scheduledAt ≤ capturedAt = now` ⇒
   该 occurrence 在 projection 中要么 matched、要么 arrived-unrecorded（horizon split，`HistoricalProjection.kt:204-255`），
   即成为 entry，绝不落入 `futureOccurrences`。
2. **过滤安全**：`HistoricalReadModel.range` 按 displayDate ∈ [请求起点, 请求终点] 过滤（`HistoricalReadModel.kt:66-80`）；
   请求两端各外扩 1 个 local day，边界 local-date 归属不会吞掉 in-window occurrence。
3. **最终包含仍是 Instant**：marker 只取 `!scheduledAt.isBefore(W.start) && !scheduledAt.isAfter(W.end)`
   ——matched 但 `scheduledAt > capturedAt` 的 early-intake 条目（`HistoricalProjection.kt:193-199`）因此被正确排除。
4. **语义保持**：occurrence 由 **current plans** 生成（`medicationPlans.observeAll().first()`，`:95`），
   `CURRENT_SCHEDULE_CONTEXT` 语义保持（`HistoricalProjection.kt:37-39,73-74,104-105`）。
5. **边界/资源**：请求跨度 ≈ 30 + 2 + 1 天 ≪ `MAX_RANGE_DAYS`（3660）与 `OccurrenceGenerationWindow` 分块上限；
   DST/local-date 边界由既有 `atStartOfDay(displayZone)` 惯例处理，最终判定仍为 Instant。
6. **接缝纪律**：C-04 只持有 fun-interface seam；production 绑定仍是同一 `HistoryReadService` 实例；
   零 DAO/repository 访问；不新增 matcher/generator；不修改 C-01 API；不修改 `HistoryRangeSource` 签名。

**结论：NOT BLOCKED —— 现有 range seam 足以提供完整的 ScheduleContextMarker occurrence 上下文；
最终 marker 包含条件保持为 `scheduledAt ∈ visibleWindow`（R2 再叠加身份 scope，§3.2B）。**

### 2.1.3 Marker scope 证明（R2 P1）

**C-01 PK 成员资格事实（Read 1 权威）：**

- `RetrospectivePkExtraction`（`RetrospectivePkExtractor.kt:18-24`）在 extract 时产出三个 ID 列表，
  service 原样放入 `Available.summary`（`RetrospectivePkService.kt:84-90`）。
- `engineInputEventIds`（`:96-98`）= 通过 eligibility 且被 patch grammar 保留的引擎输入（含 PATCH_APPLY/PATCH_REMOVE）。
- `concentrationProducingEventIds`（`:99-101`）= 其中 `producing == true` 的子集（producing 定义 `:195-222`：
  PATCH_REMOVE 永否；PATCH_APPLY rate-present ≤0 非 producing；等）。
- `patchControlEventIds`（`:102-104`）= `engineInputEventIds` 中 route == PATCH_REMOVE 的子集 ⇒
  `engineInputEventIds − patchControlEventIds` 精确去掉 removal 控制事件，保留合法零贡献 non-control 输入。
- 排除路径（永不出现在任何 ID 列表）：ANTIANDROGEN（`:115-123`）、INJECTION×E2（`:128-135`）、
  unusable non-patch（`:141-154`）、ambiguous/不合语法的 patch transitions（patch grammar exclusions）。

**身份分类器事实（复用，不新建）：**

- `MedicationIdentityClassifier.classify(matchKey: MedicationMatchKey)`（`:16-31`）为纯函数，输入
  `MedicationOccurrence.presentation.matchKey`（`MedicationOccurrence.kt:34-42,79-88`）**可直接传入**，
  不改变 Phase-A/B 契约（无状态、无新类型、无接口变更）。
- ANTIANDROGEN routeKey → `UNAVAILABLE`（`:25-27`）；medicationKey ∈ enum {E2,EB,EV,EC,EN} → `KNOWN`（`:28-30`）；
  其它 → `PARTIAL`（`:28-29`）。
- B-00 语义说明：classifier 用于**权威 intake identity**；C-04 仅将其复用于**schedule-context 屏幕筛选**
  （当前方案是否已知雌激素），不重解释 B-00 的 event-identity 归属语义、不新增分类器、不改 B-00 契约。

**PATCH_REMOVE 可达性证明（因此必须显式排除，而不是假设不可达）：**

1. restore 路径校验 plan route ∈ `SUPPORTED_ROUTES`（`EvoluneBackupCodec.kt:72`），该集合**包含** `PATCH_REMOVE`
   （`:996-998`）；restore 持久化 plans（`RoomRestorePersistence.kt:50`）。
2. `MedicationPlan.toMedicationSchedule()` 映射 `route.name` 到 `MedicationMatchKey.routeKey`，**不做 route 过滤**
   （`MedicationOccurrenceDomainMapper.kt:18-44`）。
3. 因此 restore 带入的 PATCH_REMOVE plan 会被现有 generator materialize 为 `MedicationOccurrence`
   （routeKey = "PATCH_REMOVE"）⇒ ScheduleContextMarker 必须显式排除该 routeKey。
4. UI 创建表单过滤 PATCH_APPLY/PATCH_REMOVE（`MedicationPlanBottomSheet.kt:391`）**不**构成不可达证明，
   因为 restore 路径不受该过滤约束；`MedicationPlan.getDescription()` 甚至含 PATCH_REMOVE 分支（`MedicationPlan.kt:54-61`）。
5. 即便该 occurrence 经 classifier 会得到 `KNOWN`（routeKey 非 ANTIANDROGEN、ester 为 E2 占位），
   removal **不是** scheduled intake marker ⇒ D-6B 显式排除（这是本契约的冻结规则，不是对不可达的假设）。

### 2.1.4 Same-ID mutation 证明（R3 P1）

**可达性（源码证明）：**

1. `DoseEventEditor.kt:123-173`：UPDATE 命令由 `session.original.copy(route, occurredAt, zoneId, localDate,
   doseMG, ester, extras)`（`:154-162`）构造——`copy` **不重赋 `id`**；`expectedRevision = session.expectedRevision`
   （`:167-170`）。
2. `DoseEvent`（`core/model/DoseEvent.kt:10-22`）：除 `id` 外均为可变事实——`route`、`occurredAt`、`zoneId`、
   `localDate`、`doseMG`、`ester`、`extras`、`slotId`，另有 `revision: Long`（乐观并发）。
3. `DoseEventRepository.update(..., expectedRevision): UpdateResult`（`core/dataapi/DoseEventRepository.kt:58-61`）；
   `RoomDoseEventRepository.update`（`:124-160`）在事务内对**同一 row** 执行 `updateEventIfRevisionMatches`
   （`:147`），成功后 `revision + 1`（`:131`）；revision 不匹配 → `RevisionConflict`（`:141-144`）。

**结论：** UPDATE 可以保留 `event.id` 而改变 `occurredAt` / `route` / `doseMG` / `ester` / `extras` /
`localDate` / `zoneId` / `revision`。C-01 `RetrospectivePkInputSummary` 不暴露任何 payload fingerprint，
因此 ID join 只能证明“Read1 曾接受过该 ID 为 non-control 引擎输入”，**不能**证明 Read2 payload 与 Read1
数值快照一致。C-04 **不得**为此重开 C-01（§16）。

### 2.2 体重只读接缝（EXISTS）

| 面 | 位置 | 核对事实 |
|---|---|---|
| `UserSettings.bodyWeight`、`isValidBodyWeight`、`MAX_BODY_WEIGHT_KG` | `app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt:49-66` | C-01 使用的冻结有效性规则：`finite && > 0 && <= 300` |
| `SettingsStore.userSettings: Flow<UserSettings>` | 同上 `:68-69`、`:115-146` | 现行为 current-setting 的唯一只读来源；DataStore 映射默认 55.0 |
| 组合根现有读法先例 | `app/src/main/java/io/github/yingqiu0871/evolune/MainActivity.kt:93,204` | `SettingsDataStore(applicationContext)` 实例在组合根构造并注入 ViewModel |

### 2.3 B-03 / A-04 先例面（EXISTS，作为形态模板）

| 面 | 位置 | 核对事实 |
|---|---|---|
| Insights 子路由常量 | `app/src/main/java/io/github/yingqiu0871/evolune/navigation/AppNavigation.kt:156` | `private const val INSIGHTS_ROUTE = "insights"` |
| `AppNavigation` 工厂参数 | 同上 `:189` | `insightsViewModelFactory: ViewModelProvider.Factory` 由组合根传入 |
| 子路由处理（无底栏 / 标题 / 返回） | 同上 `:540-551`、`:572-589`、`:590-601` | `isSettingsSubroute` 抑制 bottom bar；`titleOverride` 注入标题；navigate-up 走 `popBackStack()` |
| History → Insights 导航 | 同上 `:693-696` | `navController.navigate(INSIGHTS_ROUTE) { launchSingleTop = true }` |
| Activity 级 VM 先例 | 同上 `:698-708` | destination 内 `viewModel(viewModelStoreOwner = activity ?: entry, factory = ...)` |
| History 入口卡片 | `app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/HistoryScreen.kt:168,198,224-259` | `onOpenInsights` 参数 + `item { InsightsEntryCard(...) }` + `testTag("history-insights-entry")` + `onClickLabel` |
| ViewModel 生成/竞态先例 | `app/src/main/java/io/github/yingqiu0871/evolune/history/insights/InsightsViewModel.kt:52-102,182-263,364-388` | `RequestSnapshot` 单次捕获；`loadJob`/`generation`/`pendingRefresh`；token 先占后 cancel；`CancellationException` rethrow；factory 形态（含 `HistoryRangeSource` 包装先例 `:380-382`） |
| UI 状态先例 | `app/src/main/java/io/github/yingqiu0871/evolune/history/insights/InsightsUiState.kt:8-64` | phase 枚举 + typed load failure 的形态（C-04 另建同形状态，不复用 Insights 类型） |
| activation refresh 形态 | `app/src/main/java/io/github/yingqiu0871/evolune/history/insights/InsightsSurfaceLifecycle.kt:27-47` | `LaunchedEffect(Unit) { onSurfaceShown() }` + `ON_STOP→ON_START` foreground 刷新；observer 随 composition 消亡（不轮询） |
| 字符串落位先例 | `app/src/main/res/values/strings.xml:490-494`、`app/src/main/res/values-zh-rCN/strings.xml:480-484` | B-03 对两个文件写入同一份简体中文文案（source-verified：两文件当前均为 zh 文案；`values/` 498 key / `values-zh-rCN/` 490 key，差集为 8 个语言无关 ASCII key；**不存在 `values-en/`**） |
| UI 包布局 | `app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/insights/`（`InsightsScreen.kt`、`InsightsPresentation.kt`、`InsightsPreviews.kt`） | 子 surface 的既有文件布局模板 |

### 2.4 图表复用决定（source-verified）

| 核对项 | 位置 | 事实 |
|---|---|---|
| 公共签名强制 live 参数 | `app/src/main/java/io/github/yingqiu0871/evolune/ui/components/ConcentrationChart.kt:115-123` | 必需 `currentTimeHState: State<Double>`；`baselineSimulationResult`、`forkPointTimeHState`、`doseTimePoints` |
| 默认视窗 = live 36h，取自系统时钟 | 同上 `:152-157` | `System.currentTimeMillis()/3600000.0` 的 −24h…+12h 默认视窗（含未来段） |
| Canvas 无条件绘制 current-time 标记 | 同上 `:349`（`currentTimeHState.value`）及 now/fork 绘制段 | live 语义无法通过参数关闭（传值仍会画 now 线且视窗仍按 live 初始化） |
| 内部 geometry helpers 可复用 | `app/src/main/java/io/github/yingqiu0871/evolune/ui/components/ConcentrationChartGeometry.kt:10,15,26,39,71,112` | `ChartSeries`/`YAxisScale`/`PlotGeometry`/`calculatePlotGeometry`/`calculateVisibleWindowYScale` 均为 `internal`（app 模块可见） |

**冻结决定**：不复用 `ConcentrationChart`（其 live/now/36h/预测语义无法在不改变 Home 行为的前提下关闭）。
C-04 新建薄组件 `ui/screens/retrospective/RetrospectiveConcentrationChart.kt`，只复用上述内部 geometry 原语；
不得触碰 `ConcentrationChart.kt` 本身。

### 2.5 规划中的新面（NEW — 实现轮创建）

| 面 | 规划位置（包已核对为不存在，属新增） |
|---|---|
| marker/presentation coordinator（纯函数 + 三次读编排 + ID join + 身份 scope） | `app/src/main/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectivePkSurfaceCoordinator.kt` |
| ViewModel + factory | `.../history/retrospective/RetrospectivePkViewModel.kt`（含 `RetrospectivePkViewModelFactory`；工厂只接收三条 seam + `SettingsStore` + `Clock` + `displayZone`） |
| UI state / presentation model | `.../history/retrospective/RetrospectivePkUiState.kt` |
| Compose surface | `.../ui/screens/retrospective/RetrospectivePkScreen.kt` |
| 薄 retrospective 图表 | `.../ui/screens/retrospective/RetrospectiveConcentrationChart.kt` |
| surface lifecycle bridge | `.../ui/screens/retrospective/RetrospectiveSurfaceLifecycle.kt`（形态同 `InsightsSurfaceLifecycle`，不建第三种“screen visible”机制） |
| 导航接入 | `navigation/AppNavigation.kt`：新增 `RETROSPECTIVE_ROUTE`、`isSettingsSubroute` 条目、`titleOverride`、`composable(...)`；新增 `retrospectiveViewModelFactory` 参数 |
| History 入口卡片 | `ui/screens/HistoryScreen.kt`：新增参数 + 卡片（形态仿 `InsightsEntryCard`） |
| 组合根接线 | `MainActivity.kt`：构造 `RetrospectivePkService(historyReadService)` 与 `HistoryRangeSource { s, e, z, n -> historyReadService.readRange(s, e, z, n) }`，把三条 seam 传给 factory（形态仿 `:218-231,307-317` 与 `InsightsViewModelFactory:380-382`） |
| 字符串 | `values/strings.xml` + `values-zh-rCN/strings.xml` 同步新增（§11.2/§11.3） |

---

## 3. Marker semantics & derivation（冻结；R2 scope 修订）

### 3.1 内部 presentation model

```text
sealed interface RetrospectiveMarker { val instant: Instant }

data class ScheduleContextMarker(
    val occurrenceId: MedicationOccurrenceId,
    val scheduledAt: Instant,
    val provenance: ScheduleMarkerProvenance   // INTERNAL ONLY — 禁止渲染
) : RetrospectiveMarker { override val instant get() = scheduledAt }

data class RecordedIntakeMarker(
    val eventId: UUID,
    val occurredAt: Instant,
    val provenance: IntakeMarkerProvenance     // INTERNAL ONLY — 禁止渲染
) : RetrospectiveMarker { override val instant get() = occurredAt }

enum class ScheduleMarkerProvenance { MATCHED_OCCURRENCE, UNRECORDED_OCCURRENCE }
enum class IntakeMarkerProvenance { MATCHED_INTAKE, UNMATCHED_INTAKE }
```

`provenance` 只服务于内部模型/测试/诊断；任何用户可见层不得据此生成文案、图标差异或状态分类。
**禁止**加入 delta / confidence / punctuality / early-late / on-time / adherence 字段。

### 3.2 派生规则（R2：两家族各自的 scope，彼此不重组）

**A. RecordedIntakeMarker（Read 2 + Read 1 summary 的 ID join）**

先由 Read 1（`Available`）构造权威 ID 集：

```text
eligibleRecordedMarkerIds = summary.engineInputEventIds − summary.patchControlEventIds   // 集合差
```

对 Read 2 返回 all-history projection 的 entries：

| entry | intake marker |
|---|---|
| `MatchedHistoricalOccurrence` | `eventId ∈ eligibleRecordedMarkerIds` 且 `eventId ∉ patchControlEventIds` 且 `occurredAt ∈ W` → 1 枚 `RecordedIntakeMarker`（MATCHED_INTAKE） |
| `UnmatchedHistoricalIntake` | 同上条件 → 1 枚 `RecordedIntakeMarker`（UNMATCHED_INTAKE） |
| `UnrecordedHistoricalOccurrence` | **不产生**（永不 missed/skipped） |

- **不得**重跑/复刻 C-01 eligibility；**不得**检查 route/ester/extras/PK 公式来决定资格；
- **不得**只用 `concentrationProducingEventIds`；合法零贡献 non-control 输入保留；
- excluded 事件（ANTIANDROGEN、unsupported/incomplete、ambiguous patch 等）不在任何 ID 列表 ⇒ 不产生 marker；
- 同 instant 多枚 marker 允许并存，**不得**去重/合并/补偿。

**B. ScheduleContextMarker（Read 3 + 身份 scope）**

对 Read 3 返回 range 的每一天的 entries：

| entry | schedule marker |
|---|---|
| `MatchedHistoricalOccurrence` | `routeKey != "PATCH_REMOVE"` 且 `classify(occurrence.presentation.matchKey).status == KNOWN` 且 `scheduledAt ∈ W` → 1 枚（MATCHED_OCCURRENCE） |
| `UnrecordedHistoricalOccurrence` | 同上条件 → 1 枚（UNRECORDED_OCCURRENCE） |
| `UnmatchedHistoricalIntake` | **忽略**（不产生；unmatched 属于 intake 家族，由 A 处理） |

- ANTIANDROGEN（classifier → UNAVAILABLE）、PARTIAL/unknown/foreign → 不产生 marker；
- PATCH_REMOVE **显式排除**（可达性证明 §2.1.3；removal 不是 scheduled intake marker）；
- matched 与 unrecorded **同权**、无用户可见状态差异；
- `futureOccurrences` 永不产生 marker（range seam 本身不暴露；且其 `scheduledAt > capturedAt`，被最终过滤）；
- **不**推断 scheduled occurrence 是数值输入；**不**重跑 C-01 eligibility；**不**作 prescription 声明。

**共同规则（两家族）：**

- 成员判定：`!instant.isBefore(startInclusive) && !instant.isAfter(endInclusive)`（**双端闭区间**；边界时刻可见）；
- 每枚 marker 只按**自己家族的 Instant** 判定；**绝不**用 displayDate / persisted localDate / match occurrence time
  替代 inclusion 判定；
- 两家族的源 projection 相互独立（不同 read、不同 context）；**不要求也不做**跨读 reconcile（race 规则见 §4.2.1）；
- 排序：`instant` 升序，然后 occurrence-id / eventId 稳定 tie-break（确定性）。

### 3.3 计数不变式（测试可断言）

- `scheduleMarkerCount = |{range matched | routeKey≠PATCH_REMOVE ∧ classify==KNOWN ∧ scheduledAt∈W}|`
  `                    + |{range unrecorded | routeKey≠PATCH_REMOVE ∧ classify==KNOWN ∧ scheduledAt∈W}|`
- `intakeMarkerCount = |{Read2 events | eventId ∈ (engineInputEventIds − patchControlEventIds) ∧ occurredAt∈W}|`
- 无第三家族、无合成 marker、无因 unmatched 而补造的 schedule marker、无因 schedule 缺失而补造的 intake marker。

---

## 4. Window & orchestration（冻结；R2 policy 修订）

### 4.1 单次捕获（每次 load 恰好一次）

```text
capture (once per load):
  rawNow       = clock.instant()
  displayZone  = displayZoneProvider()
  endInclusive = Instant.ofEpochMilli(rawNow.toEpochMilli())     // 毫秒截断
  startInclusive = endInclusive.minus(Duration.ofDays(30))       // 恰好 720h
  visibleWindow = RetrospectivePkWindow(startInclusive, endInclusive)
  upperBoundInclusive = endInclusive                             // 与 window.end 同一 instant
  capturedAt   = endInclusive                                    // request/modelContext 同一 instant
  policy       = canonical current MedicationOccurrencePolicy value   // 捕获一次（R2）
  bodyWeight   = settingsStore.userSettings.first().bodyWeight   // 只读一次；无 live collection

  // 三条读的共享参数（同一捕获）：
  Read 1 request = RetrospectivePkRequest(
      visibleWindow, cursor = null, displayZone, bodyWeightKG = bodyWeight,
      capturedAt, policy = policy)
  Read 2 = readAllAvailable(upperBoundInclusive, displayZone, policy)
  Read 3 dates: startLD = startInclusive.atZone(displayZone).toLocalDate()
                endLD   = endInclusive.atZone(displayZone).toLocalDate()
                readRange(startLD.minusDays(1), endLD.plusDays(1), displayZone, now = capturedAt)
```

- Read 1、Read 2 接收捕获的 policy 值；Read 3 经 `HistoryRangeSource`（签名无 policy 参数）使用其生产实现的
  current canonical default policy——要求的不变量是 **value/semantics 等价**，非对象同一性（D-3/R2-2）。
- 所有读共享同一 `displayZone` / `capturedAt` / `upperBoundInclusive` / 时间窗；**不得**各自重新采样时钟。
- `bodyWeightKg` 为 C-01 冻结的 `CURRENT_SETTING_AT_QUERY_TIME` 捕获值（§6）。

### 4.2 读取顺序与失败语义（R1 修订；R2 ID join）

严格按 D-2 的序列与计数矩阵执行：

1. **Read 1** `estimate(request)`：
   - 抛异常（含 C-01 内部 typed 契约违例/读失败）→ `ERROR`，**不执行**任何 marker read；
   - 返回 `Unavailable` → `UNAVAILABLE`（结果与 limitations 原样保留），**不执行**任何 marker read；
   - 返回 `Available` → 继续，并保留 `summary` 的 `engineInputEventIds` / `patchControlEventIds` 作为
     intake-marker join 的唯一权威。
2. **Read 2** `readAllAvailable(...)` → 权威事件事实；与 Read 1 summary 做 ID join（§3.2A）；
   - 抛异常 → `ERROR`（**不发布部分内容**），不执行 Read 3。
3. **Read 3** `readRange(startLD−1, endLD+1, displayZone, now = capturedAt)` → schedule candidates；身份 scope（§3.2B）；
   - 抛异常 → `ERROR`（不发布部分内容）。
4. 三步都成功 → 一次提交新状态（curve + 两家族 markers 同帧发布）。

- 成功 CONTENT = **恰好 3** 次 history 层读取；Unavailable = 恰好 1；无效体重 = 0（矩阵见 D-2）。
- **不得**为原子性添加 retry；**不得**在三次读之间插入额外 read；**不得**重试单次读来“修复”非原子性。
- Read 3 的最终包含仍为 `scheduledAt ∈ visibleWindow`（外扩的 local dates 只用于请求，不进入渲染）。

### 4.2.1 Non-atomic ID-join 语义（R2 冻结）

不得假装三次顺序读是原子的，**也不得**声称 marker payload 与曲线数值输入是同一快照。
recorded marker 成员资格 = Read 1 的**不可变** `Available.summary` ID 集与 Read 2 **实际存在的**权威事实的 join
（Read2 payload 还需通过 §3.2A 第 4/5 条轻量呈现守卫）：

```text
recordedMarkers =
    Read2 events
        INTERSECT eligibleRecordedMarkerIds (from Read1 Available.summary)
        filtered by occurredAt in visibleWindow
        AND current Read2 payload passes KNOWN-identity / non-PATCH_REMOVE guards
```

**ID 成员资格证明的内容（冻结）：**

- 只证明：“一个具有该 ID 的权威事件曾被 Read1 接受为 non-control 引擎输入”；
- **不证明**：“Read2 的事件 payload 与 Read1 消费的 payload 在字节/值上一致”；
- C-01 summary 不暴露 `revision` / 输入 `occurredAt` / `doseMG` / `route` / `ester` / extras fingerprint，
  C-04 **不得**为此重开 C-01（§16）。

**三类 race（全部冻结）：**

| Case | 情形 | C-04 行为 |
|---|---|---|
| A | Read1→Read2 之间事件被删除 | 不出现在 Read2 → **无** RecordedIntakeMarker |
| B | 事件仅在 Read2 前新增（Read1 accepted 集不含该 ID） | → **无** RecordedIntakeMarker |
| C | **同一 ID 在 Read1→Read2 之间被编辑**（occurredAt / route / doseMG / ester / extras / localDate-zone 任意组合） | Read2 可能携带不同 payload；marker 呈现使用 **Read2 当前权威 payload**（且须通过 §3.2A 第 4/5 条守卫）；曲线保持 Read1 的不可变估算；**不得**声称显示中的 Read2 payload 精确生成了该曲线；**不得**重试伪造原子性；**不得**仅因此 race 返回 `ERROR`；下一次 activation / 手动 Retry 负责收敛 |

- **不要求** Read 1 ID 集与 Read 2 行集相等；
- 这是已冻结的 non-atomic consistency boundary 的明确化（不是新的错误来源）。

### 4.3 状态模型（NEW，形态仿 Insights）

```text
enum class RetrospectivePhase { LOADING, CONTENT, UNAVAILABLE, ERROR }

sealed interface RetrospectiveLoadFailure {
    data class ReadFailure(cause: Throwable)            // 读取本身失败
    data class ContractViolation(cause: Throwable)      // 冻结契约被违反（fail-fast 类型原样保留诊断）
    object InvalidBodyWeight                            // §6.3 防御路径（0 history reads）
}

data class RetrospectivePkUiState(
    val windowStart: Instant,
    val windowEnd: Instant,
    val displayZone: ZoneId,
    val phase: RetrospectivePhase = RetrospectivePhase.LOADING,
    val result: RetrospectivePkResult? = null,     // CONTENT → Available；UNAVAILABLE → Unavailable
    val markers: List<RetrospectiveMarker> = emptyList(),  // 仅成功路径填充；仅 CONTENT 渲染
    val failure: RetrospectiveLoadFailure? = null
)
```

- CONTENT 的 `result` 必须是 `Available`；UNAVAILABLE 的 `result` 必须是 `Unavailable`（同帧发布，不留半态）。
- presentation 层不重算数值、不改写 limitations/exclusions。

---

## 5. Curve contract（冻结）

- 渲染输入只有 `RetrospectivePkResult.Available.series`（`startInclusive`/`endInclusive`/`points`）与 markers。
- viewport = `[series.startInclusive, series.endInclusive]` = 本次 `visibleWindow`；**无** live 视窗、**无**手势默认视窗。
- 点值**原样**进入仿射屏幕映射：`[-1e-9, 0)` 的浮点抵消值保留（显示为 ~0 属映射结果，**不得**改写数值）；
  y 轴范围由 series 原始极值计算；**不得**经过 `visibleValues` 类 clamp/裁切路径（±1e6、丢负值、端点回退一概不经过）。
- **无** now 线、**无** fork 点、**无** baseline 曲线、**无**预测段、**无** live/current 标记。
- MVP **不做**：pan/zoom、tap 读数、cursor scrubbing、浓度读数浮层。
- 图表无数据/单点等退化输入按 series 实际内容渲染或进入 UNAVAILABLE/ERROR 路径（Available 契约保证网格 ≥1000 点，退化路径仅作防御）。
- Canvas 语义（accessibility）：提供整体 contentDescription（含“model estimate”语义）+ 无交互元素声明的形态，
  沿用 B-04 合并语义先例；markers 以文本标签暴露（§11/§12）。

---

## 6. Body weight integration（冻结）

### 6.1 接缝

- 只读 `SettingsStore.userSettings.first().bodyWeight`（每次 load 一次；不 collect、不订阅、不缓存）；
  捕获值随单一捕获快照供整个 generation 使用（D-3）。
- factory 注入 `SettingsStore`（现由 `SettingsDataStore` 实现）；组合根已持有该实例（`MainActivity.kt:93`）。

### 6.2 有效性

- 沿用冻结规则 `isValidBodyWeight(weight)`（`SettingsDataStore.kt:65-66`：finite ∧ >0 ∧ ≤300）。
- 有效时：作为 `bodyWeightKG` 传入 request（C-01 `BodyWeightBasis.CURRENT_SETTING_AT_QUERY_TIME` 不变）。

### 6.3 无效时的行为（冻结）

- ViewModel 在**任何 history 层读取之前**检查有效性；无效 → 不调用任何 seam、0 次 history 层读取、不抛异常：
  - `phase = ERROR`，`failure = InvalidBodyWeight`；
  - 用户文案 = §11 的 defensive generic「Estimate unavailable.」/「估算暂不可用。」+ 可见 Retry；
  - **不得**新增 C-01 unavailable reason、**不得** clamp/替换 55.0/回退历史体重/读取其它设置；
  - Retry 重新走完整 load（重新读体重）；设置修正后即恢复正常。
- 该路径为防御性：正常产品路径中设置页已阻止持久化非法体重（`updateBodyWeight` 返回 false）。

---

## 7. Entry & navigation（冻结）

- `HistoryScreen` 新增第 2 张入口卡片（形态同 `InsightsEntryCard`：`Card` + `onClickLabel` + testTag），
  参数 `onOpenRetrospectivePk: () -> Unit`；卡片置于 LazyColumn 顶部区域（与 Insights 卡片并列）。
- `AppNavigation`：
  - `private const val RETROSPECTIVE_ROUTE = "retrospective"`（名称实现轮可微调，语义为 retrospective PK 子路由）；
  - 加入 `isSettingsSubroute`（抑制 bottom bar，TopBar 显示返回）；
  - `titleOverride` → `R.string.retrospective_title`；
  - `composable(RETROSPECTIVE_ROUTE)` 内使用 Activity 级 scoped VM（形态同 Insights `:698-708`）；
  - History 目的地新增 `onOpenRetrospectivePk = { navController.navigate(RETROSPECTIVE_ROUTE) { launchSingleTop = true } }`。
- Back（系统返回与 TopBar 返回）→ 回 History；**不新增** bottom tab、不重构既有导航。

---

## 8. Refresh & staleness（冻结）

- `onSurfaceShown()`：首次 composition 入口由初始 load 拥有；此后每次重新进入 surface 刷新一次。
- `onAppForegrounded()`：仅真实 `ON_STOP → ON_START` 触发一次刷新（形态同 `InsightsSurfaceLifecycle`）。
- generation token：
  - 新 load 先 `++generation` 认领 token、再取消旧 job；
  - 成功/失败/契约违例写状态前都必须校验 token；旧 token 一律丢弃（stale-result rejection）；
  - `CancellationException` 永远 rethrow。
- `retry()`：启动**完全新的 load/generation**（新捕获、窗口按新捕获滚动，R1 明确）；若 load 在飞行中，
  合并为**恰好一次** follow-up（不排队多份）。
- **禁止**：live subscription、polling、后台循环、结果持久化/缓存；compose recomposition 不触发读取。

---

## 9. Disclosure & secondary model context（冻结；R2 增加 marker 图例披露）

- 强制 disclosure 字符串（§11）必须在 CONTENT、UNAVAILABLE、ERROR、LOADING 之后的主滚动内容中**可见**（不得仅存在于 dialog）。
- **强制 marker 图例披露（R2 建立；R3 truthfulness 修正）**：在 markers 渲染的 CONTENT 屏幕上必须直接可见
  （不得仅存在于 dialog）：
  - EN canonical reference：`Schedule markers show current-plan context. Recorded markers show the currently recorded intakes linked to this estimate; recent edits may appear after the curve was calculated.`
  - zh 产品落位：`方案标记表示当前方案上下文；摄入标记显示与本次估算关联的当前记录。若记录刚刚被修改，标记内容可能比曲线更新。`
  - 语义必须保留：current record / linked by identity / non-atomic / recent edit may make marker newer than curve；
  - **不得**使用或暗示：exact input、same snapshot、used payload、synchronized、atomic（除非未来契约引入真正的
    snapshot boundary）；
  - 该披露**不得**暗示：historical prescription truth、adherence、missed/skipped、punctuality、超出冻结模型的因果确定性。
- `ModelContext` 允许作为 secondary detail（OPTIONAL 实现），仅限中性事实：
  parameter set 名称、`CURRENT_MODEL_PARAMETERS`/`CURRENT_SETTING_AT_QUERY_TIME` 依据、当前体重设置值；
  **禁止**任何 accuracy/科学声明、**禁止**宣称测量值。

---

## 10. Unavailable / limitations UX（冻结，文案见 §11）

| 状态 | 渲染要求 |
|---|---|
| `NO_ELIGIBLE_RECORDED_INTAKES` | 专属文案；Retry 可见；不展示曲线/markers |
| `INVALID_QUERY_INTERVAL` | 专属文案；Retry 可见 |
| `QUERY_OUTSIDE_CALCULATED_INTERVAL` | defensive generic 文案（与 §6.3 同一 generic copy） |
| `HISTORICAL_INPUT_UNAVAILABLE` | 专属文案 + Retry；语义限定为“读取不完整，请重试”，不得暗示无历史 |
| `ERROR`（ReadFailure/ContractViolation/InvalidBodyWeight） | generic「Estimate unavailable.」+ Retry；诊断只入内部 failure，不上屏 |
| limitations（0..4 条） | 结果携带的 set 原样渲染；每条使用 §11 中性文案；不转义为 adherence 判断 |

- UNAVAILABLE（含 estimate 返回 Unavailable 的路径：未执行 marker reads）与 ERROR 一律不渲染 markers 与曲线（无部分内容）。
- 所有状态都保留 disclosure（§9）。

---

## 11. Copy & wording（EN canonical reference + zh 落位冻结；R2 增加图例披露）

### 11.1 硬性措辞

- 强制存在（语义等价不可省）：
  - EN reference：`Model estimate — not a measured blood concentration.`
  - zh（产品落位）：`模型估算——并非实测血药浓度。`
- 强制标签：
  - EN reference：`Current schedule context`；产品落位：`当前方案上下文`（每个 schedule marker 的可见标签）
  - EN reference：`Recorded intake`；产品落位：`已记录摄入`（每个 intake marker 的可见标签）
- 强制 marker 图例披露（R2 建立；R3 修正）：
  - EN reference：`Schedule markers show current-plan context. Recorded markers show the currently recorded intakes linked to this estimate; recent edits may appear after the curve was calculated.`
  - 产品落位：`方案标记表示当前方案上下文；摄入标记显示与本次估算关联的当前记录。若记录刚刚被修改，标记内容可能比曲线更新。`

### 11.2 字符串表（键名为规划值，实现评审最终确认）

| key（规划） | EN canonical reference（**documentation-only**） | zh-rCN（产品落位文案） |
|---|---|---|
| `retrospective_title` | Retrospective PK | 回顾性 PK |
| `retrospective_entry_title` | Retrospective PK | 回顾性 PK |
| `retrospective_entry_subtitle` | 30-day model estimate from recorded intakes | 由已记录摄入推算的 30 天模型估算 |
| `retrospective_entry_action` | Open retrospective PK | 打开回顾性 PK |
| `retrospective_window_caption` | Last 30 × 24 hours — ending %1$s | 最近 30×24 小时——截至 %1$s |
| `retrospective_disclosure` | Model estimate — not a measured blood concentration. | 模型估算——并非实测血药浓度。 |
| `retrospective_marker_legend_disclosure` | Schedule markers show current-plan context. Recorded markers show the currently recorded intakes linked to this estimate; recent edits may appear after the curve was calculated. | 方案标记表示当前方案上下文；摄入标记显示与本次估算关联的当前记录。若记录刚刚被修改，标记内容可能比曲线更新。 |
| `retrospective_marker_schedule_context` | Current schedule context | 当前方案上下文 |
| `retrospective_marker_recorded_intake` | Recorded intake | 已记录摄入 |
| `retrospective_loading` | Loading… | 加载中… |
| `retrospective_retry` | Retry | 重试 |
| `retrospective_unavailable_no_eligible_intakes` | No eligible recorded intakes are available for this estimate. | 此估算暂无可用的已记录摄入。 |
| `retrospective_unavailable_invalid_interval` | This time range cannot be calculated. | 该时间范围无法计算。 |
| `retrospective_unavailable_generic` | Estimate unavailable. | 估算暂不可用。 |
| `retrospective_unavailable_history_unavailable` | Historical records could not be read completely. Try again. | 历史记录未能完整读取，请重试。 |
| `retrospective_limitation_zero_baseline` | The curve starts from the earliest available history; earlier concentrations are unknown. | 曲线自最早可用记录起算，更早的浓度无从得知。 |
| `retrospective_limitation_unrecorded_occurrences` | Some scheduled occurrences have no matching recorded intake in the available data. | 部分计划时点在现有数据中没有对应的摄入记录。 |
| `retrospective_limitation_excluded_intakes` | Some recorded intakes are not included in this estimate. | 部分已记录摄入未计入此估算。 |
| `retrospective_limitation_ambiguous_patch` | Some patch records could not be reliably paired and are not included. | 部分贴片记录无法可靠配对，未计入此估算。 |

OPTIONAL（实现可选，若提供必须逐字使用；否则整项不出现）：

| key（规划） | EN canonical reference | zh-rCN |
|---|---|---|
| `retrospective_model_context` | Model: Evolune E2 parameter set v1 · current settings | 模型：Evolune E2 参数集 v1 · 当前设置 |
| `retrospective_model_context_weight` | Current body weight setting: %1$s kg | 当前体重设置：%1$s kg |

### 11.3 落位规则（source-verified；R1 修订）

- 当前仓库 `values/strings.xml` 与 `values-zh-rCN/strings.xml` **均为简体中文文案**；**`values-en/` 不存在**。
- C-04 **不声明也不交付英文运行时本地化**；EN canonical 列只是本契约记录的**规范语义/参考措辞**（documentation-only，
  供 code review 与未来英文 locale 立项时参考）。
- 实际落位的 C-04 资源文案遵循现有中文资源结构：上表 zh-rCN 列**同时写入两个文件**（B-03 先例）；
  新增 key 不得与既有 key 冲突；插入位置按文件既有分段注释风格（v1.7-C-04 注释块）。
- **新增 `values-en/` 不在 C-04 范围**（F15）。

---

## 12. Wording guards（冻结）

### 12.1 禁用词（新增 retrospective 用户可见字符串与其渲染路径）

EN：`adherence`、`compliance`、`missed`、`skipped`、`late`、`on-time`、`delay`
zh：`依从`、`漏服`、`跳过`、`迟到`、`准时`、`按时`、`延迟`

浓度措辞禁止暗示：
EN：`measured concentration`、`actual concentration`、`true blood level`、`lab result`
zh：`实测`、`真实`、`实际血药浓度`、`化验`

marker↔curve 关系措辞禁止使用/暗示（R3，除非未来契约引入真正 snapshot boundary）：
EN：`exact input`、`same snapshot`、`used payload`、`synchronized`、`atomic`
zh：`精确输入`、`同一快照`、`所用输入`、`已同步`、`原子`

### 12.2 审计方式（实现轮必须执行并留证）

- 对两个 strings 文件的新增 key 行做禁用词扫描（大小写不敏感；zh 词逐字扫描）；
- 对 retrospective presentation/UI 代码的字符串字面量与 `stringResource` 引用目标做同一扫描；
- 对 schedule marker 渲染路径断言只出现 `当前方案上下文` 标签、不出现任何 missed/skipped 类文案；
- disclosure 存在性断言（CONTENT/UNAVAILABLE/ERROR 三态 + R2 图例披露于 CONTENT）；
- **R3**：对用户可见文案做 marker↔curve 关系词扫描（§12.1 R3 词表），并断言图例披露逐字匹配且不声称
  当前 Read2 payload 是曲线数值输入的 exact/same snapshot。
- **R1 说明**：EN 禁用词/措辞扫描仅为**补充性 source/doc guard**（检查契约表、代码字面量、未来文档），
  **不得**被呈现为英文运行时 locale 的证据（英文 locale 不存在）。

### 12.3 语义边界

- `Recorded intake` 是事实描述，不含准时/偏差含义；
- 未记录 occurrence 只表达“当前方案上下文”，不表达缺失；
- limitations 文案只描述数据可用性，不描述用户行为。

---

## 13. Forbidden surfaces（违反 ⇒ STOP；与本 contract review 对齐）

| # | 禁止面 |
|---|---|
| F1 | 修改 `app/.../history/pk/**` 或 C-01 result/API 语义 |
| F2 | 修改 `SimulationEngine` / `ThreeCompartmentModel` / `ParameterResolver` / `PKParameters` |
| F3 | Room schema / entities / migrations / indexes 变更 |
| F4 | `DoseEventDao` 历史真相语义变更 |
| F5 | Home PK orchestration / Wear PK orchestration / Widget PK orchestration 变更 |
| F6 | 第二 history reader / 第二 matcher / 新权威存储 / 任何写入（全路径 zero-write） |
| F7 | presentation/domain 消费具体 `HistoryReadService`（只允许 seam） |
| F8 | 为“原子性”添加 retry 或合并快照；重开 C-01 |
| F9 | 新增图表依赖或任何新依赖；修改 `ConcentrationChart.kt` |
| F10 | 新增 bottom tab；无契约的导航重构 |
| F11 | 暴露 confidence/punctuality/delta/early-late/on-time/adherence 字段或文案 |
| F12 | 引入 daily aggregation / daily-series 产品语义 / coverage 百分比 / timing metric / anti-androgen identity / CPA curve |
| F13 | 历史体重重建 / 体重快照 / fallback 推断 / 新持久化 |
| F14 | live subscription / polling / 后台刷新循环 / 结果持久化缓存 |
| F15 | 新增 `values-en/` 或改动既有 locale 结构（本 slice 内） |
| F16 | 未经契约批准新增 unavailable reason / limitation / marker 家族 |
| **F17** | 修改 `HistoryRangeSource` / `HistoryReadService.readRange` 签名（含为其添加 policy 参数）；修改 A-03 range read 语义 |
| **F18** | 复刻/重实现 C-01 eligibility matrix（markers 一律走 summary ID join） |
| **F19** | 解析 route / ester / extras 以重建 PK 资格 |
| **F20** | 新建第二个 medication identity classifier（只复用 B-00 `MedicationIdentityClassifier`） |
| **F21** | 把 `PATCH_REMOVE` 当作 Recorded intake（或任何用户可见摄入） |
| **F22** | 在 E2 PK marker 层渲染 ANTIANDROGEN / PARTIAL / foreign schedule context |
| **F23** | 渲染不在 Read 1 accepted non-control ID 集内的 recorded event（excluded 事件不得成为正常 marker） |
| **F24** | 把 excluded / unsupported / anti-androgen 做成第三 marker 家族 |
| **F25** | 声称/暗示 schedule markers 是数值 PK 输入 |
| **F26** | 为 marker↔curve 一致性重开 C-01：向 `RetrospectivePkInputSummary` 添加 revision / fingerprint / event snapshot 字段；修改 `history/pk/**` |
| **F27** | combined atomic snapshot API；跨三次读的事务；retry-until-equal 循环 |
| **F28** | 持久化 curve/marker 快照 |
| **F29** | 用户可见文案声称 markers 是曲线数值输入的 exact/same snapshot（含 R2 旧措辞 “inputs used by this estimate”） |
| **F30** | 对 Read2 payload 施加超出 classifier-KNOWN + routeKey≠PATCH_REMOVE 的 eligibility 复检 |

---

## 14. Acceptance matrix（未来实现轮必须全部满足）

> 验证方式说明：`JVM` = app JVM 单元测试（fake seams）；`Instr` = affected instrumentation；
> `Audit` = 源码/资源审计；`Review` = 独立复审核对。槽中内容为实现轮命名测试的占位。

### 14.1 Marker mapping（基础家族映射；R2 措辞按 in-scope 集）

| ID | Requirement | 验证 |
|---|---|---|
| M1 | matched occurrence：schedule 与 event 各自按 own instant 入窗；分别按 §3.2A/B scope 决定是否发出 | JVM |
| M2 | unmatched recorded intake（accepted、非 patch control）：只产生 intake marker，**零**合成 schedule marker | JVM |
| M3 | unrecorded occurrence（KNOWN 身份）：只产生 schedule marker，**零** intake marker；无 missed/skipped 文案 | JVM + Audit |
| M4 | schedule markers 仅来自 Read 3 range entries（matched + unrecorded，身份/route scope 后）；intake markers 仅来自 Read 2 ∩ Read 1 accepted set；`futureOccurrences` 永不产生 marker | JVM |
| M5 | 窗口为双端闭区间：`== startInclusive` / `== endInclusive`（= capturedAt）可见；窗口外不可见 | JVM |
| M6 | 计数不变式（§3.3）：无去重/合并/合成；matched 对同 instant 两枚并存；排序确定 | JVM |

### 14.2 Marker coverage regressions（R1 引入；R2 改为 in-scope 措辞）

| ID | Requirement | 验证 |
|---|---|---|
| MC1 | 最早 recorded intake 在 T−2d、KNOWN 身份 current schedule 全程覆盖 30 天窗口：T−2d 之前的 schedule markers 仍然全部存在（证明 range-seam 必要性） | JVM |
| MC2 | accepted recorded event（`eventId ∈ engineInputEventIds − patchControlEventIds`）的 occurredAt 在窗口内、但 persisted localDate 落在可见 local-date span 之外：RecordedIntakeMarker 仍存在（只按 occurredAt 判定） | JVM |
| MC3 | in-scope occurrence/event 的 Instant 恰为窗口 start / end：包含 | JVM |
| MC4 | in-scope occurrence/event 的 Instant 在窗口 start/end 之外 1ms：排除 | JVM |
| MC5 | 一个 matched occurrence（accepted non-control）且身份 KNOWN：恰好 1 枚 schedule marker + 1 枚 intake marker | JVM |
| MC6 | 一个 unrecorded occurrence（KNOWN 身份）：只有 schedule marker，无任何 missed/skipped 分类 | JVM + Audit |
| MC7 | 一个 unmatched accepted actual：只有 intake marker；无合成 schedule marker | JVM |

### 14.3 Schedule-marker identity scope（R2 新增）

| ID | Requirement | 验证 |
|---|---|---|
| MS1 | KNOWN 身份（E2/EB/EV/EC/EN）schedule occurrence 在窗口内 → 恰好 1 枚 ScheduleContextMarker | JVM |
| MS2 | ANTIANDROGEN schedule occurrence 在窗口内 → 零 ScheduleContextMarker | JVM |
| MS3 | PARTIAL / foreign 身份 schedule occurrence 在窗口内 → 零 ScheduleContextMarker | JVM |
| MS4 | KNOWN 身份 unrecorded occurrence → 只有 schedule marker；无 missed/skipped/adherence 解释 | JVM + Audit |
| MS5 | routeKey == "PATCH_REMOVE" occurrence（可达，§2.1.3）→ 零 ScheduleContextMarker（即便 classifier 会给 KNOWN） | JVM |

### 14.4 Recorded-marker membership（R2 新增 / R3 same-ID 扩展）

| ID | Requirement | 验证 |
|---|---|---|
| MI1 | non-control event ID 在 `engineInputEventIds` 且存在于 Read 2 且窗口内 → 1 枚 RecordedIntakeMarker | JVM |
| MI2 | PATCH_REMOVE ID（∈ patchControlEventIds）→ 零 RecordedIntakeMarker | JVM |
| MI3 | ANTIANDROGEN / unsupported / incomplete（C-01 excluded、不在 engineInputEventIds）→ 零 RecordedIntakeMarker | JVM |
| MI4 | 合法保留零贡献 non-control 引擎输入（在 engineInputEventIds、非 producing）→ 1 枚 RecordedIntakeMarker | JVM |
| MI5 | unmatched accepted actual → 1 枚 RecordedIntakeMarker，且零合成 schedule marker | JVM |
| MI6 | 新事件只在 Read 1 之后出现于 Read 2 → 本 generation 零 RecordedIntakeMarker | JVM |
| MI7 | event ID 在 Read 1 summary、但 Read 2 前消失 → 零 marker、无 retry、无 ERROR；下次刷新负责收敛 | JVM |
| MI8 | marker medication scope **不得**调用/重实现 `RetrospectivePkExtractor` / `ParameterResolver` / `SimulationEngine`（ID join + classifier only） | Audit + Review |
| **MI9** | Read1 以 EV 5mg 接受 ID A；Read2 前同一 ID 被编辑为 EV 3mg 且 occurredAt 不同 →（仍落在窗口内时）marker 使用**当前 Read2** 事实；曲线保持 Read1 结果；无 retry / 无 ERROR；UI 文案不得声称 Read2 payload 精确生成了曲线 | JVM + Audit |
| **MI10** | Read1 接受 ID A；Read2 前同一 ID 变为 ANTIANDROGEN → 本 generation 对 A 零 RecordedIntakeMarker | JVM |
| **MI11** | Read1 接受 ID A；Read2 前同一 ID 变为 PATCH_REMOVE → 零 RecordedIntakeMarker | JVM |
| **MI12** | Read1 接受 ID A；Read2 前同一 ID 变为 PARTIAL/foreign 身份 → 零 RecordedIntakeMarker | JVM |

### 14.5 Truthfulness

| ID | Requirement | 验证 |
|---|---|---|
| T1 | presentation model / state 无 timing delta、early/late、on-time、adherence 字段（类型审计） | Audit + Review |
| T2 | 未记录 occurrence 永不出现 missed/skipped 类文案或状态 | Audit + Review |
| T3 | 每个 schedule marker 渲染 `当前方案上下文` 强制标签 | JVM（presentation）+ Instr |
| T4 | 主 disclosure（§11.1 第一条）在 CONTENT/UNAVAILABLE/ERROR 主内容可见、逐字匹配 | Instr + Audit |
| T5 | R2 marker 图例披露（§11.1 第三条）在 CONTENT（markers 可见处）直接可见、逐字匹配、无禁止含义 | Instr + Audit |
| **T6** | 无论 race 如何，C-04 任何文案不得声称当前 Read2 marker payload 是 Read1 曲线精确/同一快照消耗的数值输入（§12.1 R3 词表 + 图例披露语义） | Audit + Review |

### 14.6 Window

| ID | Requirement | 验证 |
|---|---|---|
| W1 | `duration == 720h`：`endInclusive − 30 days`，无 DST/月长参与 | JVM |
| W2 | 无未来段：`endInclusive == capturedAt`；request/window/upperBound 同一 instant | JVM |
| W3 | 毫秒精度：端点毫秒对齐，`RetrospectivePkWindow` 构造不抛 | JVM |
| W4 | `cursor == null`；无 cursor UI；结果 cursorEstimate 为 null 时无渲染路径 | JVM + Instr + Audit |

### 14.7 Curve

| ID | Requirement | 验证 |
|---|---|---|
| C1 | 只消费 `Available.series`；presentation 无重算（点集映射与源 series 逐点一致） | JVM |
| C2 | 无 clamp/normalize：`[-1e-9,0)` 值原样保留；不经过 ±1e6/丢负值裁切路径 | JVM + Audit |
| C3 | viewport = `[series.startInclusive, series.endInclusive]`；无 36h/live 默认视窗 | JVM + Audit |
| C4 | 无 now 线 / fork / baseline / 预测段 / live 语义 | Audit + Review |
| C5 | 无新依赖；`ConcentrationChart.kt` 零 diff | Audit |

### 14.8 Orchestration

| ID | Requirement | 验证 |
|---|---|---|
| O1 | 单次捕获（window/zone/upperBound/capturedAt/policy 值/bodyWeight）为三次读共享；同 generation 内无第二次采样；policy 不变量按值/语义（非对象同一性） | JVM（recording fakes） |
| O2 | 读取计数矩阵（D-2）：CONTENT 恰好 3 次 history 读；Unavailable 恰好 1 次；无效体重 0 次；failure 中止不回退 | JVM + Review |
| O3 | stale：token 先占后 cancel；旧 token 的成功/失败/违例均不得覆盖新状态 | JVM |
| O4 | retry：全新 load/新捕获/新 generation；飞行中合并为恰好 1 次 follow-up；race 不触发 retry | JVM |
| O5 | zero-write：无 repository/DAO 引用与调用 | JVM + Audit |
| O6 | non-atomic join（§4.2.1）：Read1/Read2 集合差异不报 ERROR、不重试、不要求相等 | JVM |

### 14.9 Unavailable / limitations

| ID | Requirement | 验证 |
|---|---|---|
| U1 | `NO_ELIGIBLE_RECORDED_INTAKES` → 冻结文案 | JVM + Instr |
| U2 | `INVALID_QUERY_INTERVAL` → 冻结文案 | JVM |
| U3 | `QUERY_OUTSIDE_CALCULATED_INTERVAL` → generic（defensive） | JVM |
| U4 | `HISTORICAL_INPUT_UNAVAILABLE` → 冻结文案 + Retry | JVM + Instr |
| U5 | 四条 limitations 原样渲染、中性文案；0..4 条均正确 | JVM |
| U6 | 无效体重：0 次 history 读、ERROR + generic + Retry；修正后可恢复 | JVM |

### 14.10 Navigation / UI

| ID | Requirement | 验证 |
|---|---|---|
| N1 | History 入口卡片可见、可点、testTag 稳定；无新 bottom tab | Instr + Audit |
| N2 | 子路由：launchSingleTop、无 bottom bar、标题 override | Instr + Audit |
| N3 | Back（系统 + TopBar）返回 History | Instr |
| N4 | LOADING/CONTENT/UNAVAILABLE/ERROR 四态区分；markers 仅 CONTENT | JVM + Instr |
| N5 | C-04 resource key 与 placeholder 在两个既有资源文件间保持一致，两份 shipped copy 均保持冻结的中文语义；EN canonical 参考措辞保持 documentation-only | Audit |
| N6 | accessibility：图表 contentDescription 含 model-estimate 语义；markers 文本可读；禁用词审计通过（§12.2） | Instr + Audit |

### 14.11 Regression

| ID | Requirement | 验证 |
|---|---|---|
| R1 | `history/pk/**` 零 diff；C-01 全部测试仍通过 | Audit + JVM |
| R2 | Room schema/entities/migrations/indexes 零 diff | Audit |
| R3 | Home/Wear/Widget orchestration 与 `ConcentrationChart.kt` 零 diff | Audit |
| R4 | build 文件零 diff（无新依赖）；`HistoryRangeSource`/`readRange` 零 diff | Audit |
| R5 | 既有 1260 JVM tests 基线不回归 | JVM full |

---

## 15. Verification requirements（每个未来实现轮强制重复）

1. **focused JVM**：新切片相关测试类全绿（含 MC1–MC7 / MS1–MS5 / MI1–MI12）；
2. **fresh full JVM `--rerun-tasks`**：全量重跑、记录 executed/failure/error/skip；
3. **affected instrumentation**：相关 Room/UI 测试在 AVD 上通过（若本切片触及 DAO/UI 路径则必须；否则记录“无 affected instrumentation”结论与依据）；
4. **evidence manifest/hash/blob 验证**：证据目录 + `MANIFEST.sha256` + HEAD-blob 校验，0 mismatch；
5. **independent review**：独立实现复审（P0–P3 输出），APPROVE 后方可关闭；
6. 禁用词/无重算/zero-write/无新依赖审计（B-03/B-04 先例）逐项留证；
7. marker scope 审计：ID-join only（无 eligibility 复刻）、身份分类仅用 B-00 classifier、PATCH_REMOVE 双通道排除、
   same-ID mutation race 三案例（§4.2.1）。

---

## 16. Explicit non-goals（本 slice 不做）

- daily aggregation / 逐日 series / chart 之外的任何聚合产品语义；
- coverage 百分比 / timing metric / punctuality / adherence 判断；
- anti-androgen 真实身份投影字段；CPA 曲线（保持独立科学/来源门槛）；
- interval selector / presets / date picker / cursor 交互；
- Home/Wear/Widget 的图表或编排改动；
- 结果持久化、缓存、后台刷新；
- C-01 的任何生产修改（含 combined snapshot / all-history occurrence context 改动）；
- `HistoryRangeSource` / `readRange` 签名或语义修改；
- 复刻 C-01 eligibility、route/ester/extras 解析复检、第二个身份分类器、第三 marker 家族；
- `values-en/` 新增。

**C-04 明确拒绝的 same-ID mutation “解法”（R3；违反即 STOP / F26–F28）：**

- 在 `RetrospectivePkInputSummary` 增加 revision / fingerprint / event snapshot 字段；
- 修改 `history/pk/**`；
- combined atomic snapshot API；
- 跨三次读的事务；
- retry-until-equal 循环；
- 持久化 curve/marker 快照。

需要严格 marker↔curve 快照一致性的未来产品需求必须走**新契约**并重新开启架构评审。

**Reopen triggers（R2 记录，出现时必须重开 C-04 marker 语义评审）：**

- 引入 user-configurable occurrence policy 或 non-default policy；
- `MedicationOccurrencePolicy` 默认值变更；
- `HistoryRangeSource` 增加 policy 注入或签名变化；
- C-01 的 `engineInputEventIds` / `patchControlEventIds` 语义或构造变化；
- `MedicationIdentityClassifier` 行为变化（KNOWN/PARTIAL/UNAVAILABLE 映射）。

---

## 17. 附：本契约为 docs-only 交付

- 本文件创建于 contract landing 轮；R1/R2 轮只修订本文件并 amend 同一 contract commit；
  不伴随任何生产代码、测试、schema、资源改动。
- 状态结论：**C-01 CLOSED** · **C-04 CONTRACT（R3 CORRECTED） / RE-REVIEW PENDING** · **C-04 PRODUCTION NOT STARTED**。
