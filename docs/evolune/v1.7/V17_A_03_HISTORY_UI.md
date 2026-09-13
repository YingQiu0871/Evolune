# V17-A-03 — History UI & Calendar Presentation（状态：**UI DELIVERED / READY FOR INDEPENDENT REVIEW**）

> 状态：**A-03 UI DELIVERED** —— History 已成为第 5 个主 tab；域契约（A-03-PRE-01）冻结不变，UI 未重做任何 domain 语义
> 第 0 轮（review cleanup）+ UI 轮：见 §13–§25
> Round：v1.7-A / A-03（preflight）→ **A-03-PRE-01（契约修复）**
> 起始 HEAD：`a149ed4b3e1fb20498b0d4239d41caab9f0f2462`（A-02 R2 关闭）→ preflight 关闭于 `fe8a5870c366367a12d8aed89dd2158b49aafcf6`
> Commit（preflight 轮）：`docs: close A-02 review precision notes`、`docs: report A-03 history contract gap`
> Commit（A-03-UI 轮）：`63f2c7b docs: close A-03 preflight review notes`、`0395ad8 feat: add History calendar presentation`、`206cdf2 feat: expose History as primary phone tab`、`1412a9f test: verify v1.7 History phone experience`、`docs: close A-03 History UI round`
> Commit（A-03-PRE-01）：`2afdbaf fix: exclude future unrecorded occurrences from History`、`c48826b test: verify History temporal horizon contract`、`docs: close A-03 History contract gap`
> 证据清单（preflight，冻结不改）：[`evidence/a-03/MANIFEST.sha256`](evidence/a-03/MANIFEST.sha256)（112 条目，coverage 112=112，manifest 自身 SHA-256 `d6d4559afa0c428bc3b8affcbfa29d6375f2ffdbed1cce20e3927db7e27ff892`）
> 证据清单（A-03 UI）：[`evidence/a-03-ui/MANIFEST.sha256`](evidence/a-03-ui/MANIFEST.sha256)（126 条目，coverage 126=126，`sha256sum -c` 126 OK / 0 FAILED，manifest 自身 SHA-256 `05d7d8266a3945f11fbb179583ba14b6552f46d42a9b89d81d653820169b9933`）
> 证据清单（A-03-PRE-01）：[`evidence/a-03-pre-01/MANIFEST.sha256`](evidence/a-03-pre-01/MANIFEST.sha256)（115 条目，coverage 115=115，`sha256sum -c` 115 OK / 0 FAILED，manifest 自身 SHA-256 `8d9ed4c89407eec2680851afe76bc99d4b828205c524af7d2c216e2ce4af5b8a`；该清单在 A-03-UI 第 0 轮的 **P3-A** 清理后重新生成，见 §13）

---

## 0. 本轮实际做了什么 / 没做什么

（preflight 轮）

| 项 | 状态 |
|---|---|
| A-02 review precision notes（P3-1..P3-4） | **已完成**（见 §1） |
| §2 History contract preflight（未来 occurrence 行为） | **已完成，发现缺口**（见 §2） |
| History ViewModel / Compose 页面 / 日历 / 导航 | **未开工**（停止条件触发） |
| Compose UI 测试、preview、截图 | **未开工** |
| JVM 全量 fresh 验证 | **已完成**（见 §7） |

（A-03-PRE-01 轮）

| 项 | 状态 |
|---|---|
| 冻结语义：未来 occurrence 不是 History（§8.1） | **已实现**（`HistoricalProjectionBuilder`） |
| preflight 契约测试放回源码树并转绿 | **已完成**（`HistoryFutureOccurrenceContractTest`，4 测试） |
| 边界 / 混合日 / read-model / service / 历史日期 / DST 回归测试 | **已完成**（19 个新测试，见 §8.5） |
| 补全性不变式扩展（matched XOR unrecorded XOR future） | **已完成**（`verifyCompleteness`，fail-fast 保留） |
| JVM 全量 fresh 验证（`--rerun-tasks`） | **已完成**（见 §11） |
| UI / ViewModel / Compose / 导航 / DAO / schema / matcher | **未改动（0 行）** |

**未修改任何产品 UI 代码，也未在 UI 层做任何过滤**（§2 明确禁止）。A-03-PRE-01 同样只动
`experience-core` 投影层与其测试。

## 1. A-02 文档精度清理（P3-1..P3-4）

commit `docs: close A-02 review precision notes`：

- **P3-1**：`V17_A_02_HISTORY_READ_MODEL.md` §3 的"证明范围"段不再指向已废弃的 R1 padding 论证，
  改为当前真实依据：**§6/§6.1 per-row 双通道完整性证明** + **delayed Reminder/Wear regression** +
  **CE1/CE2 作为 persisted-LocalDate 通道回归**。
- **P3-2**：登记 canonical `LocalDate` SQL 排序的**适用前提**（文档 assumption，不改代码）：
  应用合法数据使用普通四位年份 `0000..9999`；**不**声称该等价性对任意扩展年份成立。
- **P3-3**：新增结论句——**取全 authoritative 数据可能暴露此前被窄查询掩盖的歧义/优先级竞争，这是正确方向**；
  歧义必须保持歧义，不能通过漏数据获得虚假确定性。
- **P3-4**：新增 §15.1 chunking 测试独立性 caveat——`expectedDates(...)` 部分复述 recurrence predicate，
  独立层是 delta / 唯一性 / 边界断言；本轮不重写测试。

## 2. HISTORY CONTRACT PREFLIGHT —— 发现的缺口（P1 级，阻塞 UI）【RED 冻结记录，已在 §8 关闭】

**产品要求（§2）**：过去的 occurrence 可以是 `UnrecordedHistoricalOccurrence`；
**未来** occurrence 属于 schedule，**不得**被 History 呈现为 "No recorded intake"。

**实测结论：当前 A-01/A-02 domain 不满足该要求。** 今天（`now = 12:00`）规划在 `20:00` 的 occurrence，
在没有匹配记录时**同样**被投影为 `UnrecordedHistoricalOccurrence`（携带 `status = UPCOMING`）。

**确定性复现**（JVM，synthetic plan，无任何事件）：

```kotlin
// now = 2025-01-05T12:00:00Z；plan 每天 08:00 与 20:00；无 event
val range = service.readRange(LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 5), utc, now)
// 期望：08:00（过去）→ UnrecordedHistoricalOccurrence；20:00（未来）→ 不得出现为 no-recorded-intake
// 实际：20:00 也返回 UnrecordedHistoricalOccurrence
```

原始复现记录：`evidence/a-03/preflight-reproduction.txt`（`tests=1 failures=1`，断言消息含被返回的
`UnrecordedHistoricalOccurrence(...)`）；对应契约测试源码原样保存为
`evidence/a-03/preflight-contract-test-source.kt.txt`（preflight 轮**未留在源码树**，以保持 JVM 全绿）。
**A-03-PRE-01 已按该路径把测试放回源码树并转绿（断言未放宽）：**
`app/src/test/java/io/github/yingqiu0871/evolune/history/HistoryFutureOccurrenceContractTest.kt`（见 §8.5）。

**根因（读取当前源码得出）**：`HistoricalProjectionBuilder.derive(...)` 对**每一个**没有匹配事件的
presentation item 生成 `UnrecordedHistoricalOccurrence`（`HistoricalProjection.kt`），分类时不参考
`now` 与 occurrence 的先后关系；`status`（`UPCOMING`/`DUE`/`PAST_UNRECORDED`）虽已算出，但只作为字段携带，
不参与分类。`HistoricalReadModel.day/range` 也只按 display date 过滤，因此"今天更晚"的 occurrence 必然落入当天。

**为什么不能在本轮 UI 修**（§2 明确禁止）：在 ViewModel/Compose 里过滤 `status == UPCOMING` 属于
"Compose 层偷偷过滤"，会把域语义缺口藏在表现层，并让未来任何消费者（Timeline/Insights）各自重复该规则。

## 3. 建议的最小修复（**方案 A 已实现并验证 —— 见 §8**；下表保留为决策记录）

修复位置应在 **read-model / domain 层**，且不得改动 A-01 matcher 与 provenance 语义。两个候选：

| 方案 | 位置 | 要点 | 影响面 |
|---|---|---|---|
| **A（推荐）** | `HistoricalProjectionBuilder` | 无匹配时按 `occurrence.scheduledAt` 与 `now` 的关系分类：**过去** → `UnrecordedHistoricalOccurrence`；**未来** → 新增独立类别（例如 `UpcomingScheduledOccurrence`，或给 entry 增加显式 `isHistoryFact = false`），History 的 day/range **不含**该类别，计数亦不计入 | 触及 A-01 投影类型（新增 entry 分支），需同步 `verifyCompleteness` 与既有测试；语义最清晰，后续 Timeline/Insights 无需各自过滤 |
| **B** | `HistoricalReadModel.day/range` | 增加显式 `historyHorizon: Instant` 参数，构建 day/range 时排除"未来且无匹配"的 unrecorded entry | 改动面最小（只动 read model 与 adapter 调用点），但投影里仍存在"未来被标为 unrecorded"的中间状态，消费者需各自传 horizon |

无论哪种方案，必须同时满足：

- `HistoryReadService` 已显式持有 `now`，修复**不得**引入新的隐式时间源；
- 保留 `PAST_UNRECORDED` 的既有含义（过去、无记录）；
- 未来日期整天的行为不变（`range.endDate = min(月末, today)`，§5）；
- 契约测试（§2 复现）必须转绿并留在源码树中。

## 4. 本轮顺带登记（不改代码）

### 4.1 P2 performance backlog：`dose_events.localDate` 无 index

A-02 R2 引入的 **Channel A** 使用 `WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ?`，
而 `dose_events.localDate` **当前没有索引**，因此在大数据量下可能 table scan。这是 **P2 performance concern，
不是 correctness defect**，A-03 明确禁止 schema bump / migration / 新 index。

> **A-04 / schema hardening candidate：evaluate `dose_events.localDate` index。**

### 4.2 UI 侧必须遵守的查询纪律（供 UI 轮执行）

- **月份切换**才重新读 range；同一月份内切换选中日期**不得**再次访问 read service；
- 不得在 recomposition 中触发查询；不得每个 calendar cell / 每个 entry 单独查询；
- 一次月份加载 = **最多一次** `HistoryReadService.readRange(startOfMonth, min(endOfMonth, today))`；
- 快速切月必须可取消或用请求 token 防止旧响应覆盖新月份；
- 必须用 ViewModel 测试**计数**验证上述约束。

### 4.3 UI 轮的其它冻结要求（来自本轮任务书，未执行）

exact/inferred 呈现区分、`No recorded intake` 文案禁令（Missed/Skipped/Non-adherent/Forgot）、
`Current schedule context` 标注、actual timestamp 用 persisted `zoneId` 解释且在跨日时必须显示完整日期、
`CURRENT_DISPLAY_TIMEZONE_DERIVED` 需中性辅助说明、unmatched intake 仅 `source == MANUAL` 可标 Manual、
loading/error/empty 状态与 retry、`SavedStateHandle` 选择状态恢复、无障碍 contentDescription 含四类计数、
Preview 使用 synthetic UI model、导航只允许新增一个 destination + 一个现有风格入口。
**以上全部顺延到缺口修复后的 A-03 UI 轮。**

## 5. Stop conditions 映射

| 停止条件（§28） | 命中 |
|---|---|
| future occurrence 被 domain/read model 输出为 History unrecorded，必须 UI 偷偷过滤才能正确 | **命中 → A-03 HISTORY CONTRACT GAP** |
| UI 需要直接查 DAO/repository | 未触及（UI 未开工） |
| UI 必须重新运行 matcher/date attribution | 未触及 |
| current schedule context 无法在不误导为 historical schedule 的情况下展示 | 未触及 |
| navigation integration 需要全局 redesign | 未触及 |
| source/model 缺少字段导致 UI 必须猜 medication/plan 信息 | 未触及 |

## 6. 交付物

- 本文档（缺口报告 + 修复建议 + 顺延清单）；
- 证据：[`evidence/a-03/`](evidence/a-03/)（preflight 复现、契约测试源码存档、fresh JVM XML/日志/aggregate）；
- commit：`docs: close A-02 review precision notes`、`docs: report A-03 history contract gap`；
- **无 UI 代码、无 ViewModel、无导航改动、无 Compose 测试**。

## 7. Fresh 验证（本轮，证明源码树健康）

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

| 模块 | XML | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 84 | 718 | 0 | 0 | 0 | 718 |
| experience-core | 13 | 125 | 0 | 0 | 0 | 125 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **108** | **933** | **0** | **0** | **0** | **933** |

- 三个 test task 均为实际执行（无 `UP-TO-DATE`），`54 actionable tasks: 54 executed`，`BUILD SUCCESSFUL`；
- 计数由 JUnit XML 求和（`evidence/a-03/jvm-aggregate.tsv`）；
- 未运行 Phone instrumentation：本轮未写 UI，无受影响面；
- `git diff --check`：0。


## 8. A-03-PRE-01 —— 时间地平线契约（RED → 根因 → 域修复 → GREEN）

### 8.1 冻结语义（实现依据）

- **匹配优先**：先跑既有四阶段 matcher（不做任何前置过滤）。
  **matched occurrence 一律是 `MatchedHistoricalOccurrence`**，无论 `scheduledAt` 与 `now` 的先后 ——
  **提前服药（early intake）必须存活**；
- **未匹配**的 occurrence 才按时间地平线分类：
  - `scheduledAt <= now` → `UnrecordedHistoricalOccurrence`（`scheduledAt == now` 视为**已到达**）；
  - `scheduledAt > now` → **不是** `HistoricalEntry`，单独存于
    `HistoricalProjection.futureOccurrences: List<FutureOccurrenceContext>`
    （携带 `scheduleTimeContext = CURRENT_SCHEDULE_CONTEXT`，即"当前计划上下文"，不是历史计划快照）；
- 比较**只允许 Instant vs Instant**：不使用 `LocalDate.now`、不使用 presentation status
  （`UPCOMING` / `DUE`）、不在 UI 过滤。

### 8.2 根因（preflight 已定位，§2 保留原始复现）

`HistoricalProjectionBuilder.derive(...)` 对每个无匹配事件的 presentation item **无条件**生成
`UnrecordedHistoricalOccurrence`；`status`（`UPCOMING`/`DUE`/`PAST_UNRECORDED`）只是被携带的字段，
不参与分类。`HistoricalReadModel.day/range` 仅按 display date 过滤，因此"今天更晚"的 occurrence 必然
落进当天 —— 于是历史视图对**尚未到来**的计划时间宣称"没有服药记录"。

### 8.3 修复（方案 A，落点在域层）

`experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/HistoricalProjection.kt`：

| 变更 | 内容 |
|---|---|
| 新增类型 | `FutureOccurrenceContext(occurrence, status, actionAvailability, scheduleTimeContext = CURRENT_SCHEDULE_CONTEXT)` —— **故意不实现 `HistoricalEntry`**，因此**无法**出现在 `entries` 里 |
| 投影新增字段 | `HistoricalProjection.futureOccurrences: List<FutureOccurrenceContext> = emptyList()`，按 `scheduledAt` 升序、occurrence id 兜底（`FUTURE_OCCURRENCE_ORDER`） |
| 分类点 | matcher 结果之后（`presentation.matches[item.occurrence.id] == null` 分支内）：`item.occurrence.scheduledAt.isAfter(now)` → future context，否则 → unrecorded entry |
| 不变式 | `verifyCompleteness` 扩展为 **matched XOR unrecorded XOR future-context**（每个 occurrence 恰好一次）；事件仍为 matched XOR unmatched；违反仍 `check` fail-fast |
| read model | `HistoricalReadModel.day/range` **无需改动**（它们只消费 `entries`）→ 未来 occurrence 结构上不可能泄漏进 day/range/计数 |

**未改动**：`MedicationOccurrenceMatcher`、`MedicationTimeline`、`MedicationOccurrenceGenerator`、
`MedicationOccurrenceIdentity`、`MedicationIntakeSource`、DAO / repository / schema / migration、
`HistoryReadService` 主源码（0 行）、Compose / ViewModel / 导航（0 行）、Widget / Wear / PK / 依赖。

### 8.4 前置条件被守住的原因

- `HistoryReadService` 已显式持有 `now` 与 `displayZone`，修复**未引入新的隐式时间源**；
- 未来 occurrence 仍被生成、仍进入投影（可被 reminder/今天计划面消费），只是**不是历史事实**；
- 未来整天行为不变：`range.endDate = min(月末, today)` 的 UI 纪律（§4.2）不受影响；
- 提前服药：matched 分支完全不变，早于计划时间的记录仍是历史（§8.5 有端到端回归）。

### 8.5 测试（19 个新测试，全部在源码树中）

| 文件 | 覆盖 |
|---|---|
| `experience-core/src/test/.../HistoricalProjectionFutureOccurrenceTest.kt`（11） | 未来→upcoming context（非 entry/非 unrecorded）；`scheduledAt == now` 视为已到达；±1s 边界（`now` 侧）；`scheduledAt <= now` 一律历史；**early intake 存活**（20:00 计划、11:00 记录、`now = 12:00` → `EXACT_SLOT_AND_LOCAL_DATE`）；未来已记录 occurrence 仍留在历史；**混合当日**（08:00 unrecorded / 10:00 matched / 14:00 matched-early / 20:00 upcoming，`futureOccurrences.size == 1`）；future 排序；**显示时区无关**（UTC vs Pacific/Kiritimati 分类一致）；**DST gap**（Paris 2026-03-29：名义 02:30 的 occurrence 解析为 `01:30Z`，`now = 01:00Z` 时墙钟会误判"已到达"，Instant 判定为 future）；**DST overlap**（Paris 2026-10-25 02:30 解析为第一遍 `00:30Z`） |
| `experience-core/src/test/.../HistoricalReadModelFutureOccurrenceTest.kt`（4） | `day`/`range` 只消费 `HistoricalEntry`；upcoming 不被计为 unrecorded；未来日期**不产生空 day**（`range` 仍只返回有内容的日期）；历史日期回归（3 天未匹配全部 unrecorded，`futureOccurrences` 为空） |
| `app/src/test/.../history/HistoryFutureOccurrenceContractTest.kt`（4） | **preflight RED 契约测试放回源码树并转绿**（期望未放宽）；当日 08:00/20:00 无事件 → 只有 08:00 进历史、`unrecordedCount == 1`；**early intake 端到端**（service 读到 matched、`recordedCount == 1`、`unrecordedCount == 0`）；历史日期经 service 全为 unrecorded |

计数口径：以上为 JUnit XML 实测（§11 全量 fresh 运行包含在内），非估算。

### 8.6 Stop conditions 复核（本轮**未命中**）

| 停止条件 | 结果 |
|---|---|
| 需要改 matcher | 未命中（matcher 0 行改动） |
| 需要 UI 过滤 | 未命中（UI 0 行改动；未来 occurrence 结构上不进 read model） |
| early intake 无法保留 | 未命中（§8.5 端到端回归） |
| 不变式无法容纳"非历史 future" | 未命中（扩展为三分支，fail-fast 保留） |
| 需要 schema / repository 变更 | 未命中（0 行） |

### 8.7 P3-B 说明：三分支完整性的地位（本轮**不改 API**）

reviewer 指出：`HistoricalProjection.futureOccurrences` 的 `= emptyList()` 默认值意味着
`HistoricalProjection(entries = ...)` 这种直接构造可以**绕过** builder 的完整性检查。此处明确冻结该语义：

- **three-fate exhaustiveness 是 `HistoricalProjectionBuilder.derive(...)` 的 runtime invariant**，
  不是类型系统保证：它由 `verifyCompleteness(...)` 在每次 `derive` 时校验，违反即 `check` fail-fast；
- **直接手工构造的 `HistoricalProjection`（例如 test fixture，或任何只传 `entries` 的调用）不是
  authoritative projection**，其内容不享受上述不变式保证，也不得作为 History / Insights / 依从性判定的输入；
- 生产路径只有一条：`HistoryReadService.readRange(...)` → `HistoricalProjectionBuilder.derive(...)`
  → `HistoricalReadModel.day/range(...)`。UI 层（A-03）只消费该路径的输出；
- 本轮**刻意不做 API 收紧**（不加 `init { require(...) }`、不引入 sealed wrapper、不改构造签名）：
  该默认值不是缺陷，收紧会改变已 APPROVE 的 A-03-PRE-01 domain contract，并会破坏 fixture 构造方式。
  如未来需要类型级保证，应作为独立 hardening 任务评估。

## 9. 附录：Phone UI 接入面盘点（只读审计，供缺口修复后的 UI 轮直接使用）

本轮在写任何 UI 前先完成了 Phone UI/导航/字符串/测试约定的只读盘点（未改动任何文件）。要点：

| 关注点 | 事实（file:line） |
|---|---|
| 路由 | 顶层 tab 用 `enum class Screen(route,title)`（`navigation/Screen.kt:6-11`）；子页用 `AppNavigation.kt:137-147` 的私有 `const val`；`NavHost` 为**扁平** `composable(route)`（`AppNavigation.kt:636-659`） |
| 新增子页需同步 4 处 | 常量（`:137-147`）、`composable(ROUTE)`（如 `:709-714`）、`isSettingsSubroute` 白名单（`:528-538`）、`titleOverride`（`:559-575`） |
| 入口模式 | Settings 主页 7 行 `SettingsNavigationRow`（`ui/screens/SettingsScreen.kt:84-132`，组件 `ui/components/SettingsListItem.kt:98-123`，均带 `testTag`）；二级分组页模式见 `SyncAndBackupScreen.kt:85-110` |
| 返回/AppBar | 单一根 Scaffold + 根 `AppTopBar`（`AppNavigation.kt:544-588, 1003-1065`）；tab 页面用 `showTopBar=false` 避免双 bar（`:664,671,678,706`） |
| ViewModel 惯例 | `class XxxViewModel(...)` + 同文件 `XxxViewModelFactory : ViewModelProvider.Factory`（`viewmodel/HRTViewModel.kt:474-489`），在 `MainActivity.kt:196-219` 用 `viewModel(factory=...)` 获取 |
| 时间注入 | 现仓无统一 provider：`Clock.systemUTC()` 默认参数（`HRTViewModel.kt:122` 等）+ `() -> ZoneId = ZoneId::systemDefault` lambda（`DoseEventEditor.kt:34`）；`HistoryReadService.readRange` **已强制显式传 `displayZone` 与 `now`** |
| 状态恢复 | **全仓无 `SavedStateHandle`**；最接近先例是 `rememberSaveable`（`OnboardingFlowScreen.kt:51`、`FeatureTutorialScreen.kt:75`）→ 选择状态恢复需选型 |
| 字符串 | `values/strings.xml` 413 条（默认即中文）+ `values-zh-rCN/` 405 条；**无 plurals**，复数语义用 `%1$d`；命名 `<feature>_<element>_<role>` |
| 日期格式化 | 无统一工具函数：`DateTimeFormatter.ofPattern(...)`（`MedicationPlanBottomSheet.kt:560`）、`SimpleDateFormat`（`MedicationRecordItem.kt:138-154`）；时制由 `is24Hour` 从 Settings 向下传（`AppNavigation.kt:312-317`） |
| 测试 | Compose 组件级用 `createComposeRule()` + `EvoluneTheme`（`androidTest/.../SettingsCategoryScreenTest.kt`）；带 ViewModel 用文件内 fake + `Clock.fixed`（`MedicationRecordsScreenTest.kt:502-515`）；导航链路用 `ActivityScenario<MainActivity>` + 预置 onboarding（`SyncAndBackupNavigationTest.kt:30-157`）；共享 fake 在 `app/src/test/.../RepositoryFakes.kt:27,160`（androidTest 不能跨 source set 复用） |
| 护栏测试先例 | 源码文本断言式约定护栏：`app/src/test/.../ui/screens/MotionListUxBoundaryTest.kt:9-35` |

**待 UI 轮决策（本轮已关闭，见 §25）**：History 挂载方式 → 第 5 个主 tab；日历组件选型 → 自行用 Compose 实现；可注入时间 provider → 复用既有 `Clock` + `() -> ZoneId` 惯例，不引入新抽象。

## 10. 下一步

### 10.1 A-03-PRE-01（本轮）状态

1. ~~评审 §3 的修复方案~~ → **方案 A 已实现并验证**（§8）：`HistoricalProjection` 新增
   `futureOccurrences`，未来未匹配 occurrence 不再是 `HistoricalEntry`，`verifyCompleteness` 扩展为三分支；
2. ~~让 §2 契约测试转绿~~ → **已完成**：`HistoryFutureOccurrenceContractTest` 已回到源码树，断言未放宽；
3. 本轮**不带 push / tag / merge**，交由独立评审复核（§11 fresh 证据 + §12 证据清单）。

### 10.2 解锁后的 A-03 UI 轮（尚未开工）

A-03 UI 轮按 §4.2/§4.3 的冻结要求实现（ViewModel + 日历 + 当日列表 + 导航 + Compose 测试 + instrumentation）。
域层现在提供的保证：`HistoricalReadModel.day/range` 的输出**只包含已到达的事实**，UI 不需要、也不允许
再按 `status == UPCOMING` 过滤。仍待 UI 轮决策（UNRESOLVED，见 §9）：History 挂载方式（第 5 个 tab
vs Settings 二级页）、日历组件选型、是否引入可注入时间 provider。

`dose_events.localDate` index 评估保持 A-04 schema hardening 候选，A-03 / A-03-PRE-01 不做。

## 11. Fresh 验证（A-03-PRE-01）

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

| 模块 | XML | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 85 | 722 | 0 | 0 | 0 | 722 |
| experience-core | 15 | 140 | 0 | 0 | 0 | 140 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **111** | **952** | **0** | **0** | **0** | **952** |

- 三个 test task 均为实际执行（`--rerun-tasks`，三个 test task 日志无 `UP-TO-DATE`），`54 actionable tasks: 54 executed`，`BUILD SUCCESSFUL`；
- 计数由 JUnit XML 求和（`evidence/a-03-pre-01/jvm-aggregate.tsv`），原始 log
  `evidence/a-03-pre-01/a03pre01-jvm-run.log`；
- 相对 preflight（933）新增 **19** 个测试（experience-core +15、app +4），全部为新增契约/回归测试，
  无既有测试被删除或改写期望；
- 放回的 preflight 契约测试：RED 原始方法与 GREEN 恢复方法 **字节相同**（提取规则与命令写在
  `evidence/a-03-pre-01/contract-test-green.txt`：frozen 第 29–57 行 / restored 第 60–88 行，各 29 行，
  `diff -u` 与 `cmp` 均为空，提取文本 SHA-256
  `46365f8e622d552887750ee7ebbd7e0900812f64f2a83506793f0bb31cc48775`），仅由域修复转绿；
- 未运行 Phone instrumentation：本轮未写 UI，无受影响面（与 preflight 一致）；
- `git diff --check`：0（提交前复核）。

## 12. 交付物与证据（A-03-PRE-01）

- 源码：`HistoricalProjection.kt`（域修复）、3 个测试文件（§8.5）；
- 文档：本文档（§8 / §10 / §11 / §12）；
- 证据：[`evidence/a-03-pre-01/`](evidence/a-03-pre-01/)：
  `a03pre01-jvm-run.log`、三模块 JUnit XML 副本（111 个）、`jvm-aggregate.tsv`、`contract-test-green.txt`
  （放回的契约测试 RED→GREEN，含"恢复方法与 RED 方法字节相同"的 SHA-256 证明）、`source-diff-stat.txt`
  （改动面 + UI/Wear/adapter/matcher/schema/依赖各 0 改动的逐条证明）、`MANIFEST.sha256`
  （115 条目，coverage 115=115，`sha256sum -c` 115 OK / 0 FAILED，自哈希
  `d4c4c310…` → P3-A 清理后为 `8d9ed4c89407eec2680851afe76bc99d4b828205c524af7d2c216e2ce4af5b8a`）；
  P3-A 只改了 `contract-test-green.txt`（`9be8845b…` → `a48836e9…`）并重新生成 manifest，
  其余 114 个证据文件字节未变；
- preflight 证据 [`evidence/a-03/`](evidence/a-03/) **冻结未改**（`git diff` 0 改动，见
  `source-diff-stat.txt` 最后一项边界证明）；
- commit：`fix: exclude future unrecorded occurrences from History`、
  `test: verify History temporal horizon contract`、`docs: close A-03 History contract gap`；
- **无 UI 代码、无 ViewModel、无导航改动、无 Compose 测试、无 DAO/schema/matcher/依赖改动**。

## 13. A-03-UI 第 0 轮：PRE-01 reviewer P3 清理（只改文档/证据，不动产品代码）

A-03-PRE-01 独立复审结论 **APPROVE**（无 P0/P1/P2），另有两个 P3 记录在案，本轮开工前先关闭：

| 项 | 内容 | 处理 |
|---|---|---|
| **P3-A** | `contract-test-green.txt` 里 `46365f8e…` 这个 test-method SHA 的**提取规则未记录**，第三方无法独立复现 | 记入**精确提取规则**（frozen 第 29–57 行 / restored 第 60–88 行，各 29 行，判定规则为"文件唯一的 `@Test` 起、到其后第一行恰为 `    }` 止"）与可复制命令（`sed -n` / `diff -u` / `cmp` / `sha256sum`），并补 whole-file diff 的量化说明（116 added / 3 removed，3 行全部是 class KDoc）。常量因此**变为可独立复现**，同时保留 reviewer 已独立确认的字节相同/空 diff 证明。仅该文件字节变化（`9be8845b…` → `a48836e9…`），manifest 重新生成（自哈希 `d4c4c310…` → `8d9ed4c8…`），其余 114 个文件未变 |
| **P3-B** | `futureOccurrences = emptyList()` 默认值允许直接 `HistoricalProjection(entries = ...)` 绕过 builder 完整性 | 在 **§8.7** 明确：three-fate exhaustiveness 是 `HistoricalProjectionBuilder.derive` 的 **runtime invariant**；直接构造的 projection **不是** authoritative projection。本轮**不改 API**，不为 P3 触碰已 APPROVE 的 domain contract |

**本轮（第 0 轮）不修改任何产品代码**（`git diff` 仅含 `docs/`）。commit：`docs: close A-03 preflight review notes`。

## 14. A-03 UI 交付：History 成为第 5 个主 tab（导航决策）

产品决定：**History 是主功能，不放进 Settings**。审计结论：现有导航是**可加性**结构，无需重构，
因此按 **HOME | RECORDS | HISTORY | MEDICATION_PLANS | SETTINGS** 插入第 3 位（Settings 保持末位）：

| 关注点 | 事实 | 本轮改动 |
|---|---|---|
| 目的地集合 | `enum class Screen(route,title)`（`navigation/Screen.kt`） | **+1 枚举项** `HISTORY("history","历史")` |
| 底部栏 / 侧边栏 | 两者共用 `rememberNavItems(): List<BottomNavItem>`，遍历渲染；选中态按 `currentDestination.hierarchy` 判断 | **+1 个 `BottomNavItem`**（`Icons.Filled/Outlined.History`，Material 图标，无新图像资产） |
| 目的地注册 | 扁平 `NavHost { composable(route) }` | **+1 个 `composable(Screen.HISTORY.route)`** → `HistoryScreen(viewModel, is24Hour, showTopBar=false)` |
| 顶栏标题 | 根 `AppTopBar` 的 `when (currentScreen)` 映射 | **+1 行** `Screen.HISTORY -> stringResource(R.string.history_title)` |
| tab 手势/索引 | `screenIndex()` + `Screen.entries.indices`，通用实现 | 无需改动（自动纳入滑动顺序） |
| ViewModel 注入 | `MainActivity` 组合根构造 → `AppNavigation(...)` 参数 | **+1 个参数** `historyViewModel`，在 `MainActivity` 用 `HistoryViewModelFactory` 构造 |

**未做**：未重建导航、未改其它 tab 的信息架构与行为（route/label/icon/目的地零改动，仅顺序因插入而变化）、
未引入 nested-nav framework、未新增图像资产。

**唯一既有测试适配**：`FoldableNavigationLayoutTest.expandedRailAndSharedTopBarKeepStableGeometry` 内部硬编码了
`["home","records","medication_plans","settings"]` 这条**导航项清单**（用于断言 rail 各项等距、整组居中），
插入第 5 项后该清单必须包含 `"history"`，故 +1 行加入 `nav-rail-history`。断言的不变式（等距、组居中、
顶栏居中）未放宽。其余 tab 相关测试（`ColorRoleConformanceTest`、`FeatureTutorialNavigationTest` 等）零改动。

## 15. UI 架构

```
HistoryScreen (Composable, ui/screens/HistoryScreen.kt)
  └─ HistoryViewModel (history/HistoryViewModel.kt)
       └─ HistoryRangeSource (fun interface)  ← 唯一读缝
            └─ HistoryReadService.readRange(...)  ← 生产实现（本轮 0 改动）
```

| 层 | 文件 | 职责 |
|---|---|---|
| 读缝 | `history/HistoryRangeSource.kt` | 单一 `suspend read(start,end,displayZone,now): HistoricalRange`。ViewModel 不能越过它接触 DAO/仓库/matcher/generator |
| 状态 | `history/HistoryViewModel.kt` | `visibleMonth/selectedDate/today/displayZone/loadedMonth/loadedDays/loading/failed` + 意图（`selectDate`/`showPreviousMonth`/`showNextMonth`/`retry`） |
| 呈现模型 | `history/HistoryUiModels.kt` | `HistoryUiState`、`HistoryMonthUiModel`、`HistoryCalendarCellUiModel`、`HistoryDayUiModel`、`HistoryEntryUiModel`、`HistoryDayPhase`、`HistoryEntryKind` |
| 映射 | `history/HistoryPresentation.kt` | 纯函数：`HistoricalEntry` → `HistoryEntryUiModel`；月份网格；阶段判定。只做标签/取值/语义标志，不重新分类 |
| 格式化 | `history/HistoryFormatting.kt` | **唯一**时间戳路径（实际时间/当前方案时间/剂量/日期）；其余卡片不得自建格式 |
| 屏幕 | `ui/screens/HistoryScreen.kt` | `MonthNavigationHeader` / `WeekdayHeader` / `HistoryMonthCalendar` / `SelectedDaySummary` / 条目卡片 + Loading/Error/Empty；Preview ×3（synthetic state） |

UI 模型只承载：label 资源 id、格式化所需原始值（`Instant`+`ZoneId`+`needsFullDate`）、视觉语义标志
（`isInferredMatch`/`isManualSource`/`kind`）与计数。**未把 domain 分类在 UI 里重写一遍**（由 §22 的源码护栏测试机器化保证）。

## 16. ViewModel 状态与月份查询纪律

- 初始：`visibleMonth = today 所在月`、`selectedDate = today`；`today` 由注入的 `Clock` + `displayZone` 推导；
- `displayZone: () -> ZoneId = ZoneId::systemDefault` 与 `clock: Clock = Clock.systemUTC()` 均可注入（Compose 内不调用 `ZoneId.systemDefault()` 决定历史归日——`displayZone` 随状态下发，渲染只用状态里的 zone，护栏测试保证）；
- **一次月份加载最多一次 `readRange`**：当前月 → `[月初, today]`；历史月 → `[月初, 月末]`；未来月**不可加载**（`showNextMonth` 在当月直接 no-op，恢复出的未来月被夹回当月）；
- 同月内 `selectDate` **不触发任何读取**（只读 `loadedDays`）；
- 快速切月：`loadJob?.cancel()` **且** `loadToken` 世代校验，旧响应即使忽略取消也无法覆盖新月份；
- `retry()` 只重载当前月一次；失败时保留 `visibleMonth`/`selectedDate`；
- 未来日期不可选（`selectDate` 对 `date > today` no-op）。

## 17. 日历语义

真实 `YearMonth`/`LocalDate`；周一为一周首日（`dayOfWeek.value - 1` 个前导空 cell）；
格内状态：selected / today / enabled（`date > today` 仅作为**交互禁用**，不是历史过滤——domain 已保证未来 occurrence 不进 History）；
每格最多三个事实 indicator：`recorded` / `no recorded intake` / `other recorded intake`（MaterialTheme 语义色，
无硬编码 RGB，无 good/bad、无 adherence 百分比）。

## 18. 呈现文案（三类别 + 时间戳/时区 + exact vs inferred）

| 类别 | 状态文案（string resource） | 关键呈现 |
|---|---|---|
| `MatchedHistoricalOccurrence` | `history_status_recorded` | 实际时间一律取 `event.occurredAt`（**绝不用计划时间替代**）；方案时间标注 `history_label_current_schedule_context`（当前方案时间）；provenance ≠ EXACT 时附 `history_note_legacy_context` |
| `UnrecordedHistoricalOccurrence` | `history_status_no_recorded_intake` | `history_note_no_recorded_intake`（现有可用数据中未找到）+ `history_note_not_necessarily_missed`（不一定意味着漏服）；计划时间同样标注"当前方案时间" |
| `UnmatchedHistoricalIntake` | `history_status_recorded_intake` | 实际时间 + 可恢复的 medication/dose/route + 权威 source；无方案归属 → `history_note_plan_unavailable`；**只有 `MANUAL` 才显示"手动"** |

时间戳/时区规则（§13 已冻结，本轮实现为 `HistoryFormatting` 单一路径）：
有 persisted `zoneId` → 用事件自己的 zone；为 null → 用当前 displayZone；
实际归日 ≠ entry display date（delayed Reminder/Wear）→ 显示**完整日期 + 时间**（`yyyy-MM-dd HH:mm`）；
`CURRENT_DISPLAY_TIMEZONE_DERIVED` → `history_note_current_zone_date`（日期按当前时区显示）。

## 19. 状态恢复（SavedStateHandle，局部接入）

`HistoryViewModelFactory` 在 `create(modelClass, extras)` 中调用 `extras.createSavedStateHandle()`
（`runCatching` 兜底：拿不到就退化为不恢复、不崩溃），**未改动任何既有 ViewModelFactory / 导航基础设施**；
保存 `history.visibleMonth`（`YearMonth.toString()`）与 `history.selectedDate`（`LocalDate.toString()`）。
恢复时若保存的月份在当前月之后（时钟回拨/陈旧状态）→ 夹回当前月。

## 20. 无障碍

- 日历格 `contentDescription`（合并节点）至少包含：日期、今天（若适用）、已选中（若适用）、未到日期（未来禁用）、
  以及三类计数（无内容时为"无历史记录"）；未来格带 `disabled()` 语义（无 click action，不可选）；
- 条目卡片的组合顺序即读屏顺序：**状态 → 药物/剂量 → 实际时间 → 辅助上下文**；类别不依赖颜色区分（有文字标签）。

## 21. 性能纪律（P2 backlog）

- `dose_events.localDate` 无 index 继续作为 **A-04 schema hardening 候选**；本轮 0 schema/index/migration 改动；
- 通过 ViewModel 调用计数测试证明：**无 per-cell 查询、无 per-entry 查询、无 selection 查询、无 recomposition 查询**，
  一个月最多一次正常 load（§22 的 `HistoryViewModelTest` 逐条断言读取次数）。

## 22. 验证结果（A-03 UI，全部 fresh，计数取 JUnit/XML）

### 22.1 Fresh JVM

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

| 模块 | XML | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 88 | 764 | 0 | 0 | 0 | 764 |
| experience-core | 15 | 140 | 0 | 0 | 0 | 140 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **114** | **994** | **0** | **0** | **0** | **994** |

`54 actionable tasks: 54 executed`（`--rerun-tasks`，三个 test task 均实际执行）、`BUILD SUCCESSFUL`。
相对 A-03-PRE-01 基线 952 → **+42**（`HistoryViewModelTest` 19 / `HistoryPresentationTest` 18 /
`HistoryPresentationWordingTest` 5），既有测试期望零改写。

### 22.2 Focused History instrumentation

```bash
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=\
io.github.yingqiu0871.evolune.ui.screens.HistoryScreenTest,\
io.github.yingqiu0871.evolune.ui.screens.HistoryNavigationTest
```

`Starting 21 tests on Pixel_7(AVD) - 15` → **21 passed / 0 failed**（`HistoryScreenTest` 17 + `HistoryNavigationTest` 4）。

### 22.3 Full Phone instrumentation（closure 前必跑）

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon --console=plain
```

| 运行 | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|
| 改动前 baseline（`63f2c7b`，同机同 AVD） | 213 | 5 | 0 | 0 | 208 |
| **A-03 UI 最终树** | **235** | **5** | **0** | **0** | **230** |

- 新增 22 个测试（17 + 4 + 1 截图），**既有 213 个测试 0 回归**（`current-only failures = 0`）；
- 5 个 skipped 与 baseline 完全一致（折叠屏 rail / V15 升级类用例的 `assumeTrue` 跳过）；
- XML：`evidence/a-03-ui/androidtest-xml/{baseline-before-a03ui,final}-TEST-Pixel_7-AVD-15-app.xml`，
  汇总 `androidtest-aggregate.tsv`。

### 22.4 Assemble / hygiene

- `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`（`38 actionable tasks: 38 up-to-date`，APK 与当前源码一致，
  该 APK 正是 instrumentation 实际安装的那一份）；
- `git diff --check` → 0（提交前复核）。

## 23. 交付物与证据（A-03 UI）

- 证据：[`evidence/a-03-ui/`](evidence/a-03-ui/)：
  `a03ui-jvm-run.log`、`jvm-{app,experience-core,wear}/`（114 个 JUnit XML）、`jvm-aggregate.tsv`、
  `a03ui-androidtest-run.log`、`androidtest-xml/`（baseline + final 聚合 XML）、`androidtest-aggregate.tsv`、
  `a03ui-focused-history-ui-run.log`、`a03ui-assemble-debug.log`、`screenshots/`（3 张 Pixel 7 PNG）、
  `source-diff-stat.txt`（改动面 + 逐条 0 改动边界证明）、`MANIFEST.sha256`
  （**126 条目，coverage 126=126，`sha256sum -c` 126 OK / 0 FAILED**，自哈希
  `05d7d8266a3945f11fbb179583ba14b6552f46d42a9b89d81d653820169b9933`，提交后 HEAD blob 校验 0 mismatch）；
- 视觉证据（Pixel_7 AVD，1080×2400，synthetic UI state，不接 read service）：
  `history-01-current-month-mixed-day.png`、`history-02-empty-day.png`、`history-03-error-state.png`；
- commit：`feat: add History calendar presentation`、`feat: expose History as primary phone tab`、
  `test: verify v1.7 History phone experience`、`docs: close A-03 History UI round`；
- 冻结证据未改：`evidence/a-03/`、`evidence/a-03-pre-01/`、全部 A-02 evidence（`source-diff-stat.txt` 逐条 0 改动）。

## 24. 本轮未做 / 顺延（不宣称关闭）

- **A6 undo-projection tests、A7 read-path zero-write test**：本轮未做，仍留在 Phase-A backlog；
  本轮的完整 Phone instrumentation 只覆盖了 **A10 affected-surface instrumentation 的一部分**（History + 导航面），
  **不宣称 A6/A7/A10 关闭**；
- `dose_events.localDate` index 评估：仍是 **A-04 / schema hardening** 候选，A-03 UI 未动 schema/index/migration；
- Insights / Timeline / retrospective PK / Export / Widget / Wear / DoseCheckInMatcher hardening：本轮**未开始**。

## 25. A-03 UI 决策记录（原 §9 的 UNRESOLVED 项已关闭）

| 原待决项 | 本轮结论 |
|---|---|
| History 挂载方式 | **第 5 个主 tab**（HOME \| RECORDS \| HISTORY \| MEDICATION_PLANS \| SETTINGS），非 Settings 二级页 |
| 日历组件选型 | 自行用 Compose 实现（`YearMonth` 网格 + 前导空格 + 三态 indicator），不引入第三方日历依赖 |
| 是否引入可注入时间 provider | **不引入新抽象**：复用既有 `Clock` + `() -> ZoneId` 注入惯例（`HistoryViewModel` 构造参数），Compose 不读系统时区 |
| 选择状态恢复 | `SavedStateHandle`（经 `CreationExtras` 局部接入 factory），未重构全局 ViewModel 基础设施 |
