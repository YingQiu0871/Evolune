# V17-D-03 — Timeline Range / Date Read Orchestration — Contract

> 状态：`CONTRACT — APPROVED / FROZEN`；**D-03 IMPLEMENTATION — APPROVED / CLOSED**
> （`APPROVE V17-D03 CONTRACT` 后 R1/R2 语义已并入本冻结文本；最终独立实现复审
> `APPROVE V17-D03 IMPLEMENTATION`）
> Approved final contract HEAD：`ec0416e32ca0094ce3347a776f76c66b63c0261e`
> Approved final implementation HEAD：`d7d27f204e0eb298ca9a1ac629c7022ef621f603`
> evidence：`docs/evolune/v1.7/evidence/d-03/`
> **D-03 closure 之后，未经重开评审不得再改 D-03 生产实现。**
> R1：capture / generation closure 修正已并入（§4/§5.1/§10/§12.1/§14/§16/§22）；其余冻结语义不变。
> R2：request replacement / NOT_LOADABLE revalidation 修正已并入（§7.1/§7.2/§9/§12.1/§12.2/§16/§22）。
> Round：v1.7-D / **D-03**（Timeline Range / Date Read Orchestration；read-only，无 UI、无 ViewModel）
> Base HEAD：`a536e27fea49ec488c2c3a241e0bdb4182df6912`（D-01 APPROVED / CLOSED）
> 上游（约束性输入，**优先于本文件**）：
> - `V17_SPEC.md` §5（Timeline）、§2（单一事实源/共享派生）、§7、§12 Invariant 2、§13
> - `V17_PLAN.md` §7（D-01…D-07；gate `APPROVE V1.7-D CANDIDATE IMPLEMENTATION`）
> - `V17_ACCEPTANCE.md` §5（D1–D5）、§0（G1–G7）
> - `V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md`（**APPROVED / FROZEN**；D-01 production CLOSED）
> - planning audit：`review-packets/v17-phase-d-post-d01-remaining-scope-audit.txt`
> 本文件只定义 **D-03 orchestration contract**；不实现 UI/ViewModel、不新增 read seam、
> 不修改 D-01 与 Phase C。

---

## 0. Source-verified conventions（写死名字前已核对）

| 事实 | 位置 | 引用 |
|---|---|---|
| `HistoryRangeSource.read(startDate, endDate, displayZone, now): HistoricalRange`；唯一生产实现 `HistoryReadService::readRange` | `app/.../history/HistoryRangeSource.kt:22-29` | D-03 的**唯一**读取 seam |
| `readRange`：`endDate >= startDate`；`< MAX_RANGE_DAYS (3660)`；按 displayDate 过滤成 `HistoricalRange` | `app/.../history/HistoryReadService.kt:62-124` | 请求边界与包含性 |
| History 现行月份装载规则：**当月 end = today，过去月 end = atEndOfMonth，未来月不可装载** | `HistoryViewModel.kt:77-82,124-138`（`:138` 即 `end = if (targetMonth == YearMonth.from(today)) today else targetMonth.atEndOfMonth()`） | D3 "consistent with History" 的直接先例 |
| `selectDate` 在已装载月内**不触发读取**；选择不在可见月则 no-op | `HistoryViewModel.kt:63-70` | 选择语义先例 |
| `HistoryUiState`（visibleMonth/selectedDate/today/displayZone/loadedMonth/loadedDays/loading/failed） | `history/HistoryUiModels.kt:16-26` | 状态形状先例（D-03 不复制 UI 字段） |
| `HistoryDayPhase { LOADING, CONTENT, EMPTY, ERROR }` | `HistoryUiModels.kt:29` | 相位命名惯例 |
| Insights typed failure 形状：`sealed interface InsightsLoadFailure { ReadFailure(cause), ContractViolation(cause) }` | `history/insights/InsightsUiState.kt:37-43` | D-03 failure 形状先例 |
| 竞态/generation 先例：token 先占后 cancel、stale 丢弃、`CancellationException` rethrow、refresh 合并为恰好一次 follow-up | `InsightsViewModel.kt:87-98,182-263`；`RetrospectivePkViewModel`（C-04） | D-03 并发语义先例 |
| `TimelineReadModel(days)` / `TimelineDay(date, rows)` / `TimelineRow` / `TimelineProjectionBuilder.build(HistoricalRange)` | D-01 production @ 97838fb（**frozen**） | D-03 的唯一投影入口 |
| `YearMonth` / `LocalDate` / `ZoneId` / `Instant` 均为项目现行类型 | 同上各处 | 请求类型字段 |

---

## 1. D-02 reconciliation（正式记录；不创建 D-02 contract）

**V17-D-02 — chronological grouping：FULLY CONSUMED BY CLOSED D-01。**

Basis：

- `TimelineReadModel.days`（date sections）+ `TimelineDay.date`；
- canonical date ordering（`displayDate` 升序，D-01 §12）；
- canonical row chronology（有效 `sortInstant` 升序）；
- same-instant deterministic ordering（A/B/C 规则 + `OCCURRENCE_ORDER` 字段序列）；
- TLM17–TLM20、TLM25 全绿（含 TLM18 live generator parity）。

归属冻结：

- section rendering / sticky headers / scrolling → **D-04**；
- selection / range behavior → **D-03**（本契约）。

PLAN §7 的历史命名保持原文、不重编号；**不创建 D-02 契约**。

---

## 2. D-03 goal（冻结）

D-03 提供**最小只读编排层**，满足：

1. 解析 month-scoped historical request；
2. 每次 load **恰好一次**权威 `HistoryRangeSource` 读取；
3. 把返回的 `HistoricalRange` 经 **CLOSED D-01** 投影；
4. 持有 loaded month 内的 selected-date state；
5. 区分 content / empty / unavailable / error 状态；
6. 支持显式 refresh 与 retry；
7. 拒绝 stale asynchronous generations。

**D-03 无 UI。**

---

## 3. Authorized data path（冻结；违反即 STOP）

```text
HistoryRangeSource
      ↓
TimelineRangeCoordinator
      ↓
TimelineProjectionBuilder   [CLOSED D-01]
      ↓
TimelineReadModel
      ↓
typed Timeline range/date state
```

**无新 seam。** Forbidden direct access：`HistoryReadService`、repository、DAO、Room、
`AllAvailableHistorySource`、matcher、generator。**D-01 production 保持 untouched。**

---

## 4. Range shape — MONTH-SCOPED（冻结）

D-03 **不实现任意 range**。公开编排请求概念等价于：

```text
TimelineMonthRequest(
    month: YearMonth,
    selectedDate: LocalDate,
    displayZone: ZoneId,
    capturedAt: Instant
)
```

（名称按 §0 惯例；实现评审最终确认 Kotlin 形式。）

规则：

- **不得**使用隐式 system time / system zone：`capturedAt` 由调用方提供，`displayZone` 显式；
- `today = generationCapturedAt.atZone(displayZone).toLocalDate()`；
- **每个新的 load generation 都必须接收新供给的 `capturedAt`**；`capturedAt` 在该 generation 内
  不可变；
- `refresh` / `retry` **不得**静默复用上一 generation 的 `capturedAt`（API 归属见 §10）；
- `today` 对每个新的 load / refresh / retry generation **重新计算一次**；
- **不得**内部使用：`Instant.now()`、`Clock.system*`、`ZoneId.systemDefault()`；
- D-03 无隐式 clock：`capturedAt` 一律由调用方在构造 request/context 时供给。

---

## 5. Effective historical range（冻结）

| requestedMonth 相对 today | startDate | endDate | reads |
|---|---|---|---|
| past month（`< YearMonth.from(today)`） | `requestedMonth.atDay(1)` | `requestedMonth.atEndOfMonth()` | 恰好 1 |
| current month（`== YearMonth.from(today)`） | `requestedMonth.atDay(1)` | **`today`** | 恰好 1 |
| future month（`> YearMonth.from(today)`） | — | — | **0**；phase = `NOT_LOADABLE` |

- 规则与 History 现行装载语义逐条一致（§0 引用 `HistoryViewModel.kt:138`）；
- **未来日期绝不发送给 `HistoryRangeSource`**；
- **不**把 future month clamp 进别的月份；**不**产生 future rows。

### 5.1 Cross-midnight / cross-month examples（冻结示例）

```text
Generation N:
  capturedAt = Sep 16 23:59 (displayZone)  ->  today = Sep 16

refresh generation N+1:
  capturedAt = Sep 17 00:01                ->  today = Sep 17
  -> current-month effective end 变为 Sep 17

Sep 30 -> Oct 1 refresh（requested month 仍为 Sep）:
  Sep 变为 past month
  -> effective Sep range = 完整月份（atDay(1) .. atEndOfMonth()）
```

这是预期的 **new-generation capture** 行为，**不是** request mutation。

---

## 6. Selected-date contract（冻结）

`selectedDate` 是 D-03 的 required state。不变式：

- `selectedDate` 必须属于 `requestedMonth`；
- `selectedDate <= today`（today = 当前 generation 捕获值）。

**初始 request：**

- `selectedDate` 不在 `requestedMonth` → **contract-invalid**：typed `INVALID_REQUEST`，reads = 0；
  **不得**静默移动日期；
- `selectedDate` 在月内但晚于 `today` → `NOT_LOADABLE`，reads = 0；**不得**静默 clamp。

**成功装载后的选择（`selectDate(date)`）：**

- 要求 date 属于已装载的 `requestedMonth`；
- 要求 date ≤ 当前 generation 捕获的 `today`；
- **0 次** `HistoryRangeSource` 读取；
- 若合法选中的日期没有对应 `TimelineDay` → `EMPTY_DAY`。

`EMPTY_DAY` **不是**：missed / skipped / not taken / adherence failure。中性语义，无合成行。

---

## 7. Phase taxonomy（冻结）

```text
LOADING
CONTENT
EMPTY_RANGE
EMPTY_DAY
INVALID_REQUEST
NOT_LOADABLE
ERROR
```

语义：

- **LOADING**：已接受的 range read 在飞行中；
- **CONTENT**：loaded range 在 `selectedDate` 有 rows；
- **EMPTY_RANGE**：权威读取成功但 `TimelineReadModel.days` 为空；
- **EMPTY_DAY**：range 有历史，但 `selectedDate` 无 `TimelineDay` rows；
- **INVALID_REQUEST**：结构性不一致请求（如 selectedDate 不在 requestedMonth）；
- **NOT_LOADABLE**：请求/选择属于未来历史上下文，不可装载；
- **ERROR**：权威读取失败。

**empty 与 error 永不混同。**

### 7.1 NOT_LOADABLE re-evaluation（R2 冻结）

`NOT_LOADABLE` **不是**永久终态：后续 `refresh`（fresh capturedAt/context）必须使用**新的
generation capture** 重新执行 request validation。

```text
Future month becomes current
  Sep 30: requestedMonth = October -> NOT_LOADABLE / 0 reads
  Oct 1 refresh: requestedMonth = October, fresh capturedAt
      -> current month -> effective Oct 1..today -> exactly 1 read

Future selected date becomes today
  Sep 16: selectedDate = Sep 17 -> NOT_LOADABLE / 0 reads
  Sep 17 refresh: same logical selection, fresh capturedAt
      -> valid -> exactly 1 range read
```

**不**需要 UI workaround，也**不得**伪造新 request。

### 7.2 INVALID_REQUEST 保持结构性无效（R2 冻结）

`INVALID_REQUEST` 与 `NOT_LOADABLE` 必须区分。示例：`requestedMonth = September` 且
`selectedDate = August 31` → `INVALID_REQUEST`。plain refresh 使用同一逻辑 request **不能**
修复它：重新验证后**仍为** `INVALID_REQUEST`、reads = 0。只有 caller 提交**真正修正过的**
load/request context 才能变为 valid。

---

## 8. State shape（冻结；无本地化字符串、无预格式化）

```text
TimelineRangeState(
    requestedMonth: YearMonth,
    effectiveStartDate: LocalDate?,   // 无读取发生（INVALID_REQUEST / NOT_LOADABLE）时为 null
    effectiveEndDate: LocalDate?,     // 同上
    selectedDate: LocalDate,
    today: LocalDate,
    displayZone: ZoneId,
    phase: TimelineRangePhase,
    timelineReadModel: TimelineReadModel?,   // 成功读取的不可变模型
    selectedDay: TimelineDay?,               // 由模型派生：days.firstOrNull { date == selectedDate }
    failure: TimelineRangeFailure?,
    generation: Int
)
```

- 不放入 localized strings；
- 不预格式化日期/时间；
- 不暴露 mutable rows（`TimelineReadModel`/`TimelineDay`/`TimelineRow` 均为 immutable data class）。

---

## 9. Read discipline（冻结计数）

| 操作 | reads |
|---|---|
| valid `load(request)`（past / current month） | **恰好 1** |
| future month | **0** |
| future `selectedDate` request | **0** |
| invalid request | **0** |
| `selectDate` within loaded month | **0** |
| `refresh` | **恰好 1**（新读） |
| `retry` after ERROR | **恰好 1**（新读） |

- 成功读取**恰好一次**经过 `TimelineProjectionBuilder`（TR19）；
- **不得**为推导 selection 做第二次 historical read；
- **superseded 的 pending context → 0 reads**；
- **pending context 验证为 `INVALID_REQUEST` / `NOT_LOADABLE` → 0 reads**；
- 因此 coalescing 发生时，operation-call 总数**不必然**等于 source-read 数；
- 不变量保持：一个被接受的 valid generation context → **至多一次** source read。

---

## 10. Refresh semantics（R1 修订：fresh-capture 归属）

D-03 只拥有显式 refresh / retry，且**每次都必须接收新供给的 capture 上下文**。

允许的等价实现形态（Kotlin 名称为实现规划值）：

- `refresh(capturedAt: Instant)` / `retry(capturedAt: Instant)`；或
- `refresh(newRequestContext)` / `retry(newRequestContext)`——新上下文提供 fresh `capturedAt`，
  同时按需保留当前 requested month / selected date / display zone。

规则：

- 每个新 generation 使用**新供给的** `capturedAt`；`today` 对该 generation 重新计算（§4/§5.1）；
- **不允许**无参 refresh/retry 复用上一 generation 的 `capturedAt`，除非显式注入的 capture
  provider 提供了新的 Instant；D-03 已选择 client-supplied capture，因此**优先显式 fresh-capture
  形态**，**不**引入内部 clock abstraction；
- `retry` 在 ERROR 后启动新 generation 且接收 fresh `capturedAt`；它保留逻辑 requested month 与
  当前 selected-date intent，但**必须按新 generation 的 `today` 重新校验**——跨午夜后 valid 性
  变化按新 today 判定（例如原本 in-month-and-<=today 的选择变为仍 valid；原本 <=today 但如今
  仍 valid；任何变为 future 的选择按新 today 进入 NOT_LOADABLE），**不得**复用 stale today；
- CONTENT / EMPTY_RANGE / EMPTY_DAY 之后 `refresh` → 新权威读取；
- ERROR 之后 `retry` → 新权威读取；
- **不得**增加：polling、timer refresh、live subscription、persisted cache、background monitor。

---

## 11. Activation boundary（关键；冻结）

D-03 **不拥有** Android/surface lifecycle。禁止观察：Lifecycle、ON_START、ON_STOP、Activity、
ProcessLifecycle、Compose activation。

D-03 只暴露 `refresh()`。后续 D-04 ViewModel/surface contract 可用已批准的 lifecycle pattern
在 surface activation / real foreground return 时调用它。**D-03 保持 Android-free。**

---

## 12. Concurrency / stale-generation rules（冻结）

- 每个被接受的 load / refresh / retry 都先认领**单调递增的新 generation**（即使该请求 0 reads，
  例如 INVALID_REQUEST / NOT_LOADABLE 也必须占用新 generation，防止旧飞行读取稍后覆盖新状态）；
- 只有 current generation 可以发布终态：CONTENT / EMPTY_RANGE / EMPTY_DAY / ERROR；
- 更旧的 generation 稍后完成 → **忽略**；
- `selectDate` **不**开启新的 historical read generation；
- 但：读取在飞行期间发生的 selection 变更，**不得**在读取完成时被该请求的旧 selectedDate 覆盖
  —— 完成发布时必须用**当前** `selectedDate` 重新派生 `selectedDay`/phase（TR17 必须显式测试）。

### 12.1 Global scheduling（R1 建立；R2 泛化：latest-request-wins）

**全局并发规则（冻结）：**

- 全局**至多一个** active `HistoryRangeSource` read —— 不只是"每 generation 一个"；
- 另有**至多一个** pending request context 等待在 active read 之后；
- pending context **永远**是**最新被接受的** load / refresh / retry context；
- 更早的 pending contexts 被 **superseded**；
- **不得**并行权威读取。

**泛化到所有会创建新 generation 的被接受操作**：`load(new request)`、
`refresh(fresh capture/context)`、`retry(fresh capture/context)`。若 generation N 有
active source read 且任何更新的操作被接受：

1. 立即 claim 更新的 generation；
2. generation N **立即**失去发布权；
3. **不**启动第二个并发 source read；
4. 保留**恰好一个** pending latest context；
5. 更新的被接受 context **替换**先前 pending context。

active source read 结束/取消后：**只评估/执行最新的 pending context**。

**操作优先级是时间序，不是类型序（冻结）：** 不得 `load > refresh`、`refresh > retry`
或任何静态优先级；**latest accepted context wins regardless of operation kind**。

```text
active Sep read -> refresh(Sep, capturedAt2) -> load(Aug, capturedAt3)
final pending context = Aug

active Sep read -> load(Aug) -> refresh(current logical Aug context)
final pending context = latest Aug refresh context
```

**load(request B) 替换规则（冻结）**：当 request A 正在读取时接受 `load(request B)`：
不得启动并行读取、不得丢弃 B；A 立即 stale；B 成为 latest pending context；
B 在 active source operation 释放后执行。若 B 执行前又到达 `load(request C)`：C 替换 B，
最终只评估/读取 C。（此规则为 D-04 month/date navigation 提供**唯一**编排策略。）

**Publication authority（冻结）**：generation ownership 在**更新操作被接受的那一刻**改变，
而不是在其 source read 刚开始时。因此 old active read 的完成**不能**发布，即使新的 context
仍然只是 pending。R1 TR28 保持有效。

**Pending context 在 source read 之前先验证（R2 冻结）**：active read 释放且存在 pending
latest context 时，**先做 generation validation**。若 latest context 解析为
`INVALID_REQUEST` 或 `NOT_LOADABLE`：发布该 0-read phase、该 generation 的
`HistoryRangeSource` reads = **0**、**不**启动 follow-up source read。

> R1 措辞修正：把 "queue exactly one follow-up authoritative read" 修正为
> **“queue exactly one follow-up CONTEXT；它在验证后产生 0-read 终态或恰好一次 source read”**。

### 12.2 Selection interaction（R1 保留；R2 澄清）

- `selectDate()` 执行 **0 reads**，且**不**创建 read generation；
- 若 selection 在 active read 或 pending context 存在期间变化：该 context 最终发布时，
  **必须使用最新的 valid selectedDate intent**；不得被本地 selection 之前捕获的 selectedDate
  snapshot 覆盖；
- 既有 TR17 保持权威。

---

## 13. Coalescing（冻结最小纪律）

- 同一 generation/read 已在飞行时到达的重复 `refresh()` 请求**合并为恰好一次 follow-up**
  （Insights/C-04 先例）；**不**要求并行重复读取；
- 不变量：**每个 generation 至多一个 active authoritative read**；
- **不**引入全局 cache。

---

## 14. Failure taxonomy（冻结；含取消规则）

```text
sealed interface TimelineRangeFailure
    data class ReadFailure(val cause: Throwable)        // 权威读取失败
    data class ContractViolation(val cause: Throwable)  // 内部不变式/冻结契约被违反
```

（形状沿用 `InsightsLoadFailure` 惯例；§0 引用。）

规则（R1 澄清；source-verified precedent：`InsightsViewModel` / `RetrospectivePkViewModel`）：

- `CancellationException` **永远 rethrow**，绝不转成 ERROR；
- 已知 source/read failures → `ReadFailure` → ERROR（**source/read failure ≠ EMPTY_RANGE**，
  永不转成空）；
- **显式识别的**（dedicated typed）orchestration/projection contract failure → `ContractViolation`
  → ERROR；当前 history 读取层没有此类专用违例类型，因此实现轮只可为**明确类型化**的违例做
  该映射；
- 其余 unexpected programmer/invariant exceptions（含 `RuntimeException`）按项目先例映射为
  `ReadFailure` → ERROR（precedent：`catch (error: Throwable) -> ReadFailure`），
  **绝不**被吞掉为 EMPTY；
- **contract-invalid request**（INVALID_REQUEST）与 **future/not-loadable**（NOT_LOADABLE）是
  typed **phase**，不是 `TimelineRangeFailure`；
- reviewer-attention：若实现轮希望把特定 fail-fast 上游类型（例如 history 层的
  `IllegalStateException` check 失败）单独映射为 `ContractViolation`，必须在实现评审中显式论证，
  不得默认 broad catch。

---

## 15. History-consistency cross-assertions（HC1–HC7）

D-03 implementation tests 必须证明与 History 的**语义一致性**：

- **HC1** displayDate 保持 projection-provided（不得重算）；
- **HC2** date boundaries 双端 inclusive；
- **HC3** future month 不被读取；
- **HC4** current month 读取结束于 today；
- **HC5** past month 读取完整月份；
- **HC6** 在 loaded month 内选择另一个日期执行 0 reads；
- **HC7** selected empty day 为中性 `EMPTY_DAY`。

"Consistent with History" **不**意味着复制：calendar grid、dot indicators、exact month controls、
sticky headers。

---

## 16. Required implementation tests（冻结）

| # | requirement |
|---|---|
| TR1 | past month -> 完整 inclusive 月份 / exactly 1 read |
| TR2 | current month -> start-of-month 到 today / exactly 1 read |
| TR3 | future month -> NOT_LOADABLE / 0 reads |
| TR4 | selectedDate 不在 requestedMonth -> INVALID_REQUEST / 0 reads |
| TR5 | selectedDate 晚于 today -> NOT_LOADABLE / 0 reads |
| TR6 | 成功且 selected day 非空 -> CONTENT |
| TR7 | 成功但 selected day 空（range 非空） -> EMPTY_DAY |
| TR8 | 成功但整个 range 为空 -> EMPTY_RANGE |
| TR9 | in-range `selectDate` -> 0 reads |
| TR10 | `selectDate` 使 CONTENT -> EMPTY_DAY，无重读 |
| TR11 | `selectDate` 使 EMPTY_DAY -> CONTENT，无重读 |
| TR12 | `refresh` -> 恰好 +1 read |
| TR13 | ERROR 后 `retry` -> 恰好 +1 read |
| TR14 | read failure -> ERROR，永不 EMPTY |
| TR15 | cancellation 传播，不产生 ERROR |
| TR16 | stale 旧 generation 不能覆盖新结果 |
| TR17 | 飞行中变更的 selection 在完成时保留（旧 request 的 selectedDate 不覆盖） |
| TR17a | INVALID_REQUEST / NOT_LOADABLE 也认领新 generation（防止旧读覆盖） |
| TR18 | 飞行中重复 refresh 被合并 / 无重复 active read |
| TR19 | 成功 range 恰好一次通过 D-01 builder 投影 |
| TR20 | displayDate / date sections 不被重算 |
| TR21 | current-month 的未来日期绝不到达 source |
| TR22 | 不合成 future rows |
| TR23 | 无 delta/adherence 语义 |
| TR24 | 相同 request + 相同 source 结果 -> 确定性输出 |
| TR25 | refresh 跨本地午夜使用 fresh capturedAt，并把 current month 的 effective end 扩展到新 today |
| TR26 | refresh 跨月边界把原 current month 重新归类为 past month，并使用完整月份边界 |
| TR27 | 一次飞行读取期间多次 refresh 合并为恰好一个 follow-up，且使用最新供给的 capturedAt/context |
| TR28 | 更新的 0-read INVALID_REQUEST/NOT_LOADABLE generation 被认领后，旧飞行 generation 不能发布 |
| TR29 | 旧读取在飞行时接受新 load：旧 generation 立即 stale；无并行 source read；新请求随后执行 |
| TR30 | 多种 pending 操作类型混合：无论 load/refresh/retry，latest accepted context 获胜 |
| TR31 | future month NOT_LOADABLE -> 跨月 rollover 后 refresh：变为可装载且恰好 1 次读取 |
| TR32 | future selectedDate NOT_LOADABLE -> 该日期变为 today 后 refresh：变为 valid 且恰好 1 次读取 |
| TR33 | pending latest context 验证为 INVALID_REQUEST/NOT_LOADABLE：该 generation 0 次 source read，
         且不启动不必要的 follow-up 读取 |
| TR34 | pending request 替换：A active、B pending、C 被接受 -> B 永不执行，只执行 C |

Architecture/static guards（实现轮必须证明不存在）：DAO/Room/repository、`HistoryReadService`、
`AllAvailableHistorySource`、matcher/generator、D-01 修改、retrospective/PK、Compose/navigation/
resources、ViewModel/Lifecycle、polling/timers、writes、gated semantics。

---

## 17. D1–D5 mapping（truthful；不夸大）

| acceptance | D-03 状态 |
|---|---|
| D1 shared projection | foundation complete via CLOSED D-01（D-03 继续消费同一投影） |
| D2 deterministic chronological grouping | **fully consumed by CLOSED D-01** |
| D3 selected-date / range behavior | **directly targeted by D-03** |
| D4 accessibility/localization | **NOT completed** |
| D5 fresh verification / independent review | 仍为 gate work |

**Phase D 不因本契约而完成。**

---

## 18. Contract guards（F1–F16；违反 ⇒ STOP）

| # | forbidden |
|---|---|
| F1 | 新 history truth seam |
| F2 | DAO / repository / Room |
| F3 | `HistoryReadService` 直接调用 |
| F4 | 第二 matcher / generator |
| F5 | D-01 修改（TimelineReadModel / TimelineRow / TimelineRowId / TimelineProjectionBuilder / TIMELINE_ROW_ORDER） |
| F6 | Phase-C / PK 依赖 |
| F7 | future rows / 读取超过 today |
| F8 | timing / adherence 语义 |
| F9 | writes |
| F10 | UI / Compose / navigation |
| F11 | ViewModel / Android lifecycle |
| F12 | localized / formatted strings |
| F13 | polling / live subscription / persistent cache |
| F14 | unmanaged / global `CoroutineScope` |
| F15 | 把 cancellation 变成用户 ERROR |
| F16 | 新依赖 |

---

## 19. Deferred to D-04 / D-05（本契约明确留开）

navigation entry、visual oldest/newest direction、section-header UI、sticky headers、
row presentation、selected-date visual treatment、loading/empty/error copy、12/24h formatting、
localization、TalkBack、font scale。

---

## 20. Production surface & runtime conventions（规划值）

规划（实现评审最终确认名称），位于既有包：

```text
app/src/main/java/.../history/timeline/
    TimelineMonthRequest      (request type)
    TimelineRangePhase        (7-phase enum)
    TimelineRangeState        (immutable state)
    TimelineRangeFailure      (sealed failure)
    TimelineRangeCoordinator  (orchestration)
```

- 不新建 data/repository 包；
- runtime 约定（source-verified 先例）：injected `CoroutineScope`（无 global scope、无内部自建
  unmanaged scope）+ `StateFlow`（或等价 immutable observable state）+ generation token；
- 保持 Android-free / ViewModel-free / lifecycle-free。

---

## 21. Non-goals（冻结 OUT）

Compose / Screen / Navigation / MainActivity / HistoryScreen 改动 / Timeline screen / ViewModel /
Android lifecycle / resources / strings / 12-24h formatting / accessibility / font scale；
timing delta、early/late/on-time、overdue、adherence/compliance、coverage percentage、
historical prescription reconstruction、anti-androgen identity expansion、PK；
任意 range / multi-month / pagination / 无限滚动 / cache。

---

## 22. Documentation status

- 本契约状态：**`CONTRACT — APPROVED / FROZEN`**（`APPROVE V17-D03 CONTRACT` 后 R1/R2 语义并入
  本冻结文本）；
- **R1（capture / generation closure，architect REQUEST_CHANGES）**：已并入 §4 的 capture 归属、
  §5.1 跨午夜/跨月示例、§10 fresh-capture API 形态、§12.1 coalescing / latest-context 规则、
  §14 failure mapping 澄清、§16 的 TR25–TR28；其余冻结语义不变（D-02 consumed、month-scoped、
  past/current/future boundaries、selection 规则、七相位、read-count、no-future、D-01 freeze、
  D-04/D-05 留开、gated 语义、D-06/D-07 分类、F1–F16 均未改动）；
- **R2（request replacement / NOT_LOADABLE revalidation，architect REQUEST_CHANGES）**：已并入
  §7.1（NOT_LOADABLE 可重新评估）、§7.2（INVALID_REQUEST 结构性无效）、§9（superseded /
  0-read pending validation 的 read-count 澄清）、§12.1（全局至多一个 active source read +
  单一 latest pending context + latest-request-wins + load 替换 + publication authority +
  pending 验证先于 source read）、§12.2（selection interaction 澄清）、§16 的 TR29–TR34；
  failure mapping（§14）与 R1/既有冻结语义保持不变；
- **D-03 production：APPROVED / CLOSED** @ `d7d27f204e0eb298ca9a1ac629c7022ef621f603`
  （最终独立实现复审 `APPROVE V17-D03 IMPLEMENTATION`；证据 `docs/evolune/v1.7/evidence/d-03/`：
  focused 56/0/0/0 · fresh full JVM 1421/0/0/0 · 54/54 tasks · 168/168 manifest coverage ·
  170/170 HEAD blob verification · 0 mismatches）；
- **未经重开评审，不得再改 D-03 生产实现**；
- Phase D：IN PROGRESS；D-01 CLOSED；**D-02 FULLY CONSUMED BY D-01**；**D-03 CLOSED**；
  D-04/D-05 NOT STARTED；D-06 = RECURRING GATE；D-07 = FINAL PHASE-D GATE；
- NEXT：**V17-D-04 Timeline UI pre-contract audit**（仅对账既有 deferred UI 决策，不直接实现
  D-04，不发明 timing-delta/adherence UI）。
