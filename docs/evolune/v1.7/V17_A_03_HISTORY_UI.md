# V17-A-03 — History UI & Calendar Presentation（状态：**BLOCKED / CONTRACT GAP**）

> 状态：**A-03 HISTORY CONTRACT GAP** —— UI 未开工（按 §2 / §28 停止条件）
> Round：v1.7-A / A-03
> 起始 HEAD：`a149ed4b3e1fb20498b0d4239d41caab9f0f2462`（A-02 R2 关闭）
> Commit（本轮实际产出）：`docs: close A-02 review precision notes`、`docs: report A-03 history contract gap`
> 证据清单：[`evidence/a-03/MANIFEST.sha256`](evidence/a-03/MANIFEST.sha256)（112 条目，coverage 112=112，manifest 自身 SHA-256 `d6d4559afa0c428bc3b8affcbfa29d6375f2ffdbed1cce20e3927db7e27ff892`）

---

## 0. 本轮实际做了什么 / 没做什么

| 项 | 状态 |
|---|---|
| A-02 review precision notes（P3-1..P3-4） | **已完成**（见 §1） |
| §2 History contract preflight（未来 occurrence 行为） | **已完成，发现缺口**（见 §2） |
| History ViewModel / Compose 页面 / 日历 / 导航 | **未开工**（停止条件触发） |
| Compose UI 测试、preview、截图 | **未开工** |
| JVM 全量 fresh 验证 | **已完成**（见 §7） |

**未修改任何产品 UI 代码，也未在 UI 层做任何过滤**（§2 明确禁止）。

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

## 2. HISTORY CONTRACT PREFLIGHT —— 发现的缺口（P1 级，阻塞 UI）

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
`evidence/a-03/preflight-contract-test-source.kt.txt`（**未留在源码树**，以保持 JVM 全绿；修复轮应把它放回
`app/src/test/java/io/github/yingqiu0871/evolune/history/HistoryFutureOccurrenceContractTest.kt` 并把期望固定为修复后行为）。

**根因（读取当前源码得出）**：`HistoricalProjectionBuilder.derive(...)` 对**每一个**没有匹配事件的
presentation item 生成 `UnrecordedHistoricalOccurrence`（`HistoricalProjection.kt`），分类时不参考
`now` 与 occurrence 的先后关系；`status`（`UPCOMING`/`DUE`/`PAST_UNRECORDED`）虽已算出，但只作为字段携带，
不参与分类。`HistoricalReadModel.day/range` 也只按 display date 过滤，因此"今天更晚"的 occurrence 必然落入当天。

**为什么不能在本轮 UI 修**（§2 明确禁止）：在 ViewModel/Compose 里过滤 `status == UPCOMING` 属于
"Compose 层偷偷过滤"，会把域语义缺口藏在表现层，并让未来任何消费者（Timeline/Insights）各自重复该规则。

## 3. 建议的最小修复（**未实现**，待复审决定）

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

**待 UI 轮决策（UNRESOLVED，本轮不自行决定）**：History 挂载为第 5 个 tab 还是 Settings 二级页（`V17_PLAN.md` 的 A-04/A-05 未指定，仓库无既有 `HistoryScreen`）；日历组件选型（仓库无既有日历组件）；是否为 History 引入新的可注入时间 provider 抽象。

## 10. 下一步（需评审决定）

1. 评审 §3 的两个修复方案（推荐 A），批准后由 **read-model/domain 层**修复并让 §2 契约测试转绿；
2. 修复通过后，A-03 UI 轮按 §4.2/§4.3 的冻结要求实现（ViewModel + 日历 + 当日列表 + 导航 + Compose 测试 + instrumentation）；
3. `dose_events.localDate` index 评估保持 A-04 schema hardening 候选，A-03 不做。
