# V17-D-04 — Timeline UI — Contract

> 状态：`CONTRACT — REVIEW PENDING`（docs-only contract；**D-04 PRODUCTION — NOT STARTED**）
> Slice：**V17-D-04 — Timeline UI**
> Base HEAD：`544b1a85368502807171908954ae26bf8c732d10`（D-03 APPROVED / CLOSED）
> 上游（约束性输入，**优先于本文件**）：
> - `V17_SPEC.md` §5（Timeline）· §10（UI Principles）· §12 Invariant 2 · §13
> - `V17_PLAN.md` §7（D-04 UI；D-05 accessibility/localization；D-06/D-07 gates）
> - `V17_ACCEPTANCE.md` §5 D1–D5 + §0 G1–G7
> - [`V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md`](V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md)（APPROVED / FROZEN）
> - [`V17_D_03_TIMELINE_RANGE_DATE_CONTRACT.md`](V17_D_03_TIMELINE_RANGE_DATE_CONTRACT.md)（APPROVED / FROZEN）
> - planning audit：`review-packets/v17-d04-ui-pre-contract-audit.txt`
> D-04 **consumes** the closed D-01 read model and the closed D-03 range/date orchestration;
> it must not reinterpret or modify their domain/range/concurrency semantics.

---

## 0. Source-verified identifiers（写死名字前已核对）

| 事实 | 位置 |
|---|---|
| 子路由常量先例：`INSIGHTS_ROUTE = "insights"`、`RETROSPECTIVE_ROUTE = "retrospective"` | `navigation/AppNavigation.kt:158-159` |
| 子路由工厂参数：`insightsViewModelFactory` / `retrospectiveViewModelFactory` | `AppNavigation.kt:192-193` |
| `isSettingsSubroute`（抑制底栏）与 `titleOverride`（标题映射） | `AppNavigation.kt:544,...`, `:593` |
| Activity-scoped VM 先例：`viewModelStoreOwner = owner`（activity ?: entry） | `AppNavigation.kt:718-726`（RETROSPECTIVE_ROUTE 块） |
| 入口卡片先例与 testTags：`history-insights-entry` / `history-retrospective-entry` | `HistoryScreen.kt:201-202,230-238,281` |
| lifecycle 机制先例（同一 shape）：`InsightsSurfaceLifecycle` / `RetrospectiveSurfaceLifecycle`（`LaunchedEffect(Unit){ onSurfaceShown() }` + `ON_STOP -> ON_START`） | `history/insights/InsightsSurfaceLifecycle.kt:28-37`；`ui/screens/retrospective/RetrospectiveSurfaceLifecycle.kt:28-37` |
| 组合根 wiring 先例：`RetrospectivePkService(historyReadService)` + factory 传 seam | `MainActivity.kt:229-241,307-317` |
| 12/24h 中央派生：`TimeFormat` -> `DateFormat.is24HourFormat` -> `is24Hour` 传给屏幕 | `AppNavigation.kt:329-332` |
| 通用格式化：`HistoryFormatting.dose/timeText/fullDateTimeText/scheduleTimeText` | `history/HistoryFormatting.kt` |
| 通用身份/route 资源：`ester_e2..en`、`route_*`（产品词汇，可复用） | `res/values/strings.xml:288-305` |
| 资源结构：`values/` 与 `values-zh-rCN/` 均为中文；无 `values-en/` | 目录 source-verified |
| D-03 状态入口：`TimelineRangeCoordinator(rangeSource, scope, initialRequest)` + `state: StateFlow<TimelineRangeState>`；`load/refresh(capturedAt)/retry(capturedAt)/selectDate` | D-03 production（CLOSED） |
| D-01 输出：`TimelineReadModel(days)`、`TimelineDay(date, rows)`、`TimelineRow(rowId,rowKind,displayDate,sortInstant,scheduleContext?,recordedIntake?)` | D-01 production（CLOSED） |

---

## 1. Goal 与范围

D-04 交付 **Timeline 的功能性 UI**：渲染 frozen D-03 month-scoped range/date state 与 closed D-01
read model。**无新 domain 语义**；不新增 D-03 phase；不重解释 D-01/D-03。

D-04 关闭 Timeline 的功能性 UI；**ACCEPTANCE D4 不因 D-04 完全关闭**（见 §30 D-05 split）。

---

## 2. Product surface / navigation（冻结）

```text
History
  -> Timeline entry card
  -> dedicated TIMELINE_ROUTE
  -> Timeline screen
```

- **禁止**：第六个 bottom tab、Home 入口、嵌入 History 内部、deep link。
- 沿用 Insights/Retrospective 子路由模式：`launchSingleTop`、`titleOverride`（Timeline title）、
  `isSettingsSubroute` 处理（隐藏底栏、TopBar 返回）、Back → History。
- 代码标识符（规划值，实现评审最终确认）：`TIMELINE_ROUTE = "timeline"`。

### 2.1 无 route date/month 参数（D-04 MVP，F16）

History 入口卡片**不**向 Timeline 传 `selectedDate/month`。Initial Timeline context 独立为
**current month + today**。理由：contextual handoff 非权威 D-04 要求；且与 activity-scoped
retained VM 的语义冲突。未来 contextual date handoff 需要自己的显式扩展。F16 守卫此边界。

---

## 3. ViewModel / coordinator ownership（冻结）

- **一个 activity-scoped `TimelineViewModel`，拥有恰好一个 `TimelineRangeCoordinator`**；
- VM 拥有：coordinator lifetime、`viewModelScope`、Clock/current Instant capture boundary、
  display-zone capture、screen commands、必要的 presentation mapping；
- VM **不得**直接调用：`HistoryRangeSource`、`HistoryReadService`、repository、DAO、Room；
- composition root 可构造已批准的 coordinator（经批准的 `HistoryRangeSource` seam），
  factory 只注入 seam/Clock/displayZone（先例：`MainActivity.kt:229-241`）；
- UI 只消费 VM/coordinator state，不开独立 read path。

## 4. State retention（冻结；D-04 MVP）

- month/date state 在 activity-scoped VM 存活期间保留；
- **不得**引入：`SavedStateHandle` Timeline persistence、DataStore persistence、database
  persistence、last-viewed Timeline store；
- process recreation 且 VM 不存在时：**重新初始化 current month + today**（刻意行为）；
- D-04 不增加 process-death 状态恢复要求。

## 5. Initial request（冻结）

VM 首次构造：

```text
capturedAt   = externally captured current Instant  (Clock 注入；D-03 不内置 clock)
displayZone  = current display zone
today        = capturedAt.atZone(displayZone).toLocalDate()
month        = YearMonth.from(today)
selectedDate = today
```

构造**恰好一次** D-03 initial request。Initial composition **不得**产生
`initial load + immediate duplicate surface refresh` 两读（§6）。

## 6. Lifecycle / activation refresh（冻结）

复用已批准 Insights/Retrospective lifecycle 机制**同一 shape**：D-04 可引入
`TimelineSurfaceLifecycle`，但必须是既有模式，不是第三套语义 lifecycle model。

| 事件 | 行为 |
|---|---|
| 首次构造 | D-03 initial load 拥有第一读 |
| 首次 surface-shown callback | **不重复 refresh** |
| 之后 route re-entry | `refresh(fresh capturedAt)` |
| 真实 app foreground return（ON_STOP→ON_START） | `refresh(fresh capturedAt)` |
| ordinary recomposition | **0 reads** |
| selected-date UI 变更 | **0 reads** |

**禁止**：polling、timer refresh、periodic refresh、live subscription。**不得修改** closed
Insights/Retrospective lifecycle helpers。除既有先例外，不要求额外的 configuration-change
检测机制。

---

## 7. Month controls（冻结）

屏幕含：previous month / month title / next month。

- **next month 在 requestedMonth == current month 时 disabled**；正常控件无法进入未来月；
- previous month：

  ```text
  load(previousMonth, selectedDate = previousMonth.atDay(1), fresh capture)
  ```

- next month（仍在 current month 之前时）：

  ```text
  load(nextMonth,
       selectedDate = if (nextMonth == currentMonth) today else nextMonth.atDay(1),
       fresh capture)
  ```

- 每次被接受的 month change：**恰好一个** D-03 load generation；
- **禁止**：arbitrary range picker、infinite scroll、future month picker；
- D-03 `NOT_LOADABLE` 保持防御性支持（§16）。

## 8. Month header centering — REQUIRED VISUAL RULE（冻结）

- calendar/date-control region 必须 **居中、对称、轴对齐**；
- month/year title 必须保持在其可用 calendar region 的**视觉中心**；
- previous/next 控件必须预留**对称的水平布局槽位**：left slot width == right slot width；
- next-month disabled 时，右侧槽位**结构上保留**，使禁用不使 title 偏离屏幕中心；
- **禁止**产生：title 在 next 禁用时移动、title 只在可见 icon 之间居中（而非屏幕区域）、
  左右导航宽度不等、用 manual padding 伪造居中；
- 使用**结构性 Compose alignment**（例如等宽左右槽位 + `weight`/`Alignment.Center`），
  而非视觉微调。F20 守卫。

## 9. Compact day strip（冻结）

- 使用 compact horizontal day strip；**不得**复制 History 完整 calendar grid；
- 显示日期只从 `effectiveStartDate .. effectiveEndDate` 生成；**最多 31 个 day cells**；
- 日期顺序：ascending calendar order；
- 选择合法日期：`TimelineViewModel.selectDate(date)` → D-03 `selectDate(date)` → **0 source reads**；
- future/disabled dates 不可选。

## 10. Day cell centering — REQUIRED VISUAL RULE（冻结）

每个 day cell 是一个**稳定的居中视觉单元**：

- 同一水平中心轴必须共享：weekday label、date number、selected-state background/highlight/shadow；
- 对每个 cell：date number **水平居中**；
- 对选中日期：date number 必须**水平与垂直双向**居中于其 selected-state 视觉容器内；
- 选中日期数字必须位于其 background/shape/shadow 的**几何中心**；
- **禁止**依赖：非对称 padding、manual x/y nudge、negative offset、font-specific magic number、
  baseline coincidence、selected 与 unselected 不同 padding —— 即"看起来近似居中"的做法；
- 优先结构性布局，例如：

  ```text
  Box(contentAlignment = Alignment.Center)
  ```

  或任何保证**真双轴居中**的 Compose 结构。实现细节不冻结，**几何结果冻结**。
- 内部布局必须在以下条件下保持居中：single-digit / double-digit、selected/unselected、
  12/24h mode、normal supported font scaling。D-05 做 exhaustive font-scale stress；D-04
  不得引入在更大字号下立即破坏的已知几何。F19 守卫。

## 11. Day-strip overall centering（冻结）

- calendar/date-selection region 整体应视觉占据可用屏幕宽度的中心；
- **避免**：day cells 向左缘漂移、start/end padding 不等、day-cell 宽度不等、
  selected cell 相对其它 cell 偏移、导航元素移动 apparent center axis；
- 当显示日期数超过可见宽度而 strip 变为横向可滚动时：
  - 每个 day cell 仍保持其内部居中几何；
  - selected/current date 应在适当时被带入合理可见位置；
  - 滚动**永不**改变 weekday/date/highlight 内部对齐；
- 滚动动画细节非契约关键。

## 12. selectedDate = focus，不是 Timeline filtering（关键；F17）

- Timeline 是 **MONTH Timeline**：D-04 **始终渲染 loaded month 的全部非空
  `TimelineReadModel.days`**；
- `selectedDate` **不得**把 model 过滤成单日列表（防止 Timeline 变成 History 单日列表的重复）；
- `selectedDate` 是 presentation focus：
  - 在 day strip 中高亮选中日；
  - 若存在对应 `TimelineDay`：请求 presentation scroll/focus 至该 date section；
  - 若不存在：phase = `EMPTY_DAY`，显示中性 selected-day empty notice，
    **但保留其它非空 month sections 可见**；
- **不得**合成空 `TimelineDay`。

## 13. Canonical vs visual ordering（冻结；F18）

- D-01 canonical ordering 保持不动；
- presentation：
  - day sections：**newest date -> oldest date**；
  - rows within each day：**canonical ascending order**；
- 实现只做 day-section reversal：`days.asReversed()` 或语义等价；
- **不得**反转 `TimelineDay.rows`；**不得**修改 D-01/D-03 models 或 canonical comparators。

## 14. Date section presentation（冻结）

- 每个非空 `TimelineDay` 得到 **non-sticky** section header；
- D-04 MVP header：适用时 **Today / Yesterday** functional resource label，否则 absolute local
  date + weekday；
- section header 与下方内容可正常 **LEFT alignment**（与 centered calendar region 刻意不同）；
- 视觉层级冻结：

  ```text
  calendar / month / day selection          -> centered
  date section headings / weekday labels    -> left aligned
  medication records                        -> left aligned
  ```

- **不得**把全部 Timeline body copy 居中；**不得**合成空 date sections；sticky headers 非必需。

## 15. Row families（冻结）

只渲染 D-01 的三个家族：`MATCHED` / `UNRECORDED_SCHEDULE` / `UNMATCHED_INTAKE`。
**无第四 UI 语义家族。**

## 16. MATCHED row truthfulness（冻结；F1/F2 by construction）

MATCHED 必须渲染**两个视觉上可区分的侧面**：Current schedule context / Recorded intake。

- schedule side 只用：`scheduleContext.scheduledAt`、`scheduleContext.identity`、
  `scheduleContext.matchKey` dose；
- recorded side 只用：`recordedIntake.occurredAt`、`recordedIntake.identity`、
  `recordedIntake.matchKey` dose；
- **永不**flatten 为单一 medication fact；**永不**用 schedule dose 当 actual dose；
  **永不**用 actual event identity 当 historical prescription identity；
- 两条线必须保持**不同 provenance 的可理解性**。

## 17. UNRECORDED / UNMATCHED rows（冻结）

- `UNRECORDED_SCHEDULE`：渲染 Current schedule context + 中性
  **“No recorded intake / 未记录摄入”**（D-01 neutral semantics）。**禁止**：missed、skipped、
  not taken、overdue、failed、late。
- `UNMATCHED_INTAKE`：只渲染 recorded side（actual time / actual identity / actual dose）。
  **不得**合成：scheduled time、schedule context、missed plan、unavailable plan、
  historical prescription。

## 18. Identity presentation（冻结；F7/F8）

D-01 `MedicationIdentity.status` 是权威：

| status | UI |
|---|---|
| `KNOWN` | canonical medication identity，使用既有产品资源（`ester_e2..en`） |
| `PARTIAL` | 中性 functional resource：“medication information incomplete” |
| `UNAVAILABLE` | 中性 functional resource：“medication identity unavailable” |

**禁止**：planName 作为身份证据、antiandrogen 猜测、把 placeholder 转换成真实药名、
从不相关 schedule label 推断身份。

## 19. Dose / route（冻结）

- dose：渲染**各侧** `matchKey.doseAmount`，使用 `HistoryFormatting.dose` 或语义等价的既有
  通用格式化 helper；
- D-04 MVP **不渲染 route**（route 是 optional later polish，SPEC §5 未要求）；
- **禁止**：dose comparison、dose difference、percentage comparison、score、adherence
  interpretation。

## 20. Timing delta prohibition（冻结；F5）

D-04 **不得**实现：`+2h41m`、timing delta、early、late、on time、overdue、punctuality、
adherence、compliance。SPEC 旧示例中的 timing delta **不**推翻 closed D-01 决定。
scheduled time 与 actual time 可**各自独立**显示；其数值差**不得**被计算/呈现。
新增 source/strings guard 覆盖，防止这些语义意外复活。

---

## 21. State-to-UI mapping（冻结；只用 D-03 七个 phase）

| phase | UI |
|---|---|
| `LOADING` | screen-level loading region；按 D-03 已发布 state 原样渲染 |
| `CONTENT` | month controls + day strip + 全部非空 month sections |
| `EMPTY_RANGE` | month controls + day strip（effective range 存在时）+ 中性 month-empty region；含义 = 该 month/range 无任何 Timeline rows；**不得**暗示 no medication taken / missed / adherence failure |
| `EMPTY_DAY` | month controls + day strip + 中性 selected-day empty notice；**保留其它非空 month sections 可见** |
| `INVALID_REQUEST` | 防御性 generic recoverable error region；**不得**暴露 INVALID_REQUEST 等内部术语；用户动作 = return/load current month |
| `NOT_LOADABLE` | 防御性 generic unavailable region（正常控件应阻止到达）；用户动作 = return/load current month |
| `ERROR` | generic read error + **Retry**（Retry 必须提供 fresh `capturedAt`；无自动 retry loop） |

**不得**新增 domain phase。

## 22. Pending-new-context presentation（冻结；F10）

D-04 **不得**发明 UI-local pending state machine：只渲染 D-03 实际发布的 state。

- D-03 发布 LOADING → 渲染 LOADING；
- D-03 在新 context queued 期间暂时保留 prior published state → 继续渲染该 published state。

**禁止**：synthetic pending flag、UI-local stale-content overlay、第二 generation counter、
重复 loading orchestration、local retry scheduler。D-03 是唯一 orchestration authority。

## 23. Formatting / 12-24h（冻结）

- VM/coordinator 保留 raw domain values；UI/presentation layer 拥有 formatting；
- 复用：既有 `is24Hour` 派生（`TimeFormat` -> `DateFormat.is24HourFormat`，`AppNavigation.kt:329-332`）、
  `HistoryFormatting` 的时间 helpers（语义通用时）与 dose helper、既有 ester 资源；
- Timeline 必须尊重 12-hour / 24-hour / SYSTEM time-format 既有约定；
- **禁止**：domain/VM state 内硬编码用户可见 date/time format string；D-03 models 内
  存放 formatted/localized string。

## 24. Functional strings / localization split（冻结；F13）

- D-04 可添加**最小功能** `timeline_*` resource keys（title、entry card、month title pattern、
  day cells、section labels、row labels、empty/error/retry、Today/Yesterday、identity neutral
  wording 等），按项目约定写入**两个现有资源文件**；
- Compose 内**无**用户可见 literal；
- **D-05 owns**：final wording audit、localization completeness/parity、future language
  expansion、polish。

## 25. Accessibility split（冻结）

D-04 必须结构上提供：logical reading order、row-card semantic grouping、clickable controls
的 enabled/disabled semantics、month-control content descriptions、day-cell semantics、
稳定的 test tags（如适用）。Calendar/day-cell semantics 必须保留 weekday / date / selected
state 的关系，**不得**要求仅凭视觉位置理解 selection。

**D-05 owns**：exhaustive TalkBack wording audit、merged-semantics audit、font-scale stress、
final content-description matrix、localization parity。D-04 不得故意创建日后需要架构重写的
inaccessible structure。

## 26. TimelineScreen architecture（冻结）

偏好可测试拆分：

```text
route / VM collector layer
    ↓
stateless TimelineScreenContent(state, is24Hour, callbacks...)
```

或等价 repository convention。Compose content **不得**直接读取 repository / DAO / Room /
`HistoryRangeSource` / `HistoryReadService` / `TimelineRangeCoordinator`，只经批准的
ViewModel state/callback boundary。

## 27. Month / day focus scroll（冻结）

- selected date 有 rendered section 时：presentation scroll/focus 至该 section；
- 要求：selection 造成 **0 source reads**；scroll 不修改 Timeline model；scroll 不创建
  D-03 generation；scroll 不反转 canonical row ordering；
- initial current-month/today focus 可在 section 存在时定位到 today section；
- selected date 无 section 时：显示 EMPTY_DAY notice，保留其它 sections，**不合成 section**；
- 动画细节非契约关键。

## 28. History entry card（冻结）

新增一个 Timeline entry card，沿用 Insights/Retrospective card pattern：

- 打开 `TIMELINE_ROUTE`；
- **不携带** date/month 参数（§2.1）；
- functional title/subtitle resources；
- semantic click label + testTag（规划值：`history-timeline-entry`）；
- 既有 closed entry cards **语义不变**。

## 29. Forbidden presentation fields（冻结；F9）

**永不渲染**：`planId`、`slotPosition`、`slotId`、occurrence UUID、event UUID、`sortKey`、
internal provenance、generation token。它们是 implementation/comparator/internal identity fields。

---

## 30. Production boundary（规划值）与 frozen surfaces

Likely new：

```text
history/timeline/TimelineViewModel.kt            （含 factory）
ui/screens/timeline/TimelineScreen.kt
ui/screens/timeline/TimelinePresentation.kt
ui/screens/timeline/TimelineSurfaceLifecycle.kt
```

Minimal edits：`AppNavigation.kt`、`MainActivity.kt`、`HistoryScreen.kt`、
`res/values/strings.xml`、`res/values-zh-rCN/strings.xml`。
Tests：`TimelineViewModelTest`、Timeline presentation/unit tests、TimelineScreen instrumentation、
TimelineNavigation instrumentation、architecture guards。Exact filenames remain planning values.

**Frozen surfaces（零语义修改；只消费）**：`TimelineReadModel`、`TimelineProjectionBuilder`、
`TimelineRangeState`、`TimelineRangeCoordinator`、D-01、D-03、`HistoryReadService`、
`HistoryRangeSource` interface、`HistoricalProjectionBuilder`、matcher/generator、
Phase-C production、schema/DAO/Room、Home/Wear/Widget、build/dependencies。

## 31. D-04 required test matrix（UI1–UI39）

| # | requirement |
|---|---|
| UI1 | first construction -> current month/today；无 duplicate first-entry refresh |
| UI2 | later surface re-entry -> exactly one refresh with fresh capturedAt |
| UI3 | foreground return -> exactly one refresh with fresh capturedAt |
| UI4 | recomposition -> 0 reads |
| UI5 | previous month -> exactly one load |
| UI6 | next month disabled at current month |
| UI7 | past -> next month resolves correct selected day |
| UI8 | day selection -> 0 reads |
| UI9 | month sections rendered newest-first |
| UI10 | rows inside each section remain canonical ascending |
| UI11 | selected day with section focuses/scrolls without read |
| UI12 | EMPTY_DAY keeps other month sections visible |
| UI13 | MATCHED renders schedule + recorded as visibly distinct sides |
| UI14 | UNRECORDED renders schedule + neutral no-recorded-intake |
| UI15 | UNMATCHED renders recorded only |
| UI16 | KNOWN identity uses canonical identity |
| UI17 | PARTIAL neutral / no guess |
| UI18 | UNAVAILABLE neutral / no antiandrogen guess |
| UI19 | per-side dose provenance preserved |
| UI20 | no planName/comparator/internal IDs rendered |
| UI21 | no delta/early/late/on-time/adherence wording |
| UI22 | LOADING mapping |
| UI23 | EMPTY_RANGE mapping |
| UI24 | INVALID_REQUEST defensive mapping |
| UI25 | NOT_LOADABLE defensive mapping |
| UI26 | ERROR + Retry |
| UI27 | retry supplies fresh capture |
| UI28 | published-state-only pending behavior；no second local pending machine |
| UI29 | respects 12h mode |
| UI30 | respects 24h mode |
| UI31 | History card opens Timeline |
| UI32 | Back returns to History |
| UI33 | no Timeline bottom tab/Home entry |
| UI34 | activity-scoped VM retains current Timeline month/date across Timeline -> History -> Timeline within the same Activity |
| UI35 | selected-day date number is visibly centered inside its selected-state background/highlight on BOTH horizontal and vertical axes |
| UI36 | month title remains visually centered when next-month control is disabled；left/right navigation layout reservation remains symmetric |
| UI37 | calendar/day-selection region is visually centered while Timeline section headings and medication content below remain left aligned |
| UI38 | weekday label, date number and selected-state background share one horizontal center axis inside each day cell |
| UI39 | single-digit and double-digit selected dates remain geometrically centered without manual offsets |

Tests **不得**依赖精确动画时长。

## 32. Android instrumentation（冻结）

D-04 引入 Android/Compose/navigation UI → **affected instrumentation REQUIRED**。至少验证：
screen state rendering、row-side truthfulness、month/day interaction、History → Timeline → Back、
12/24h presentation、basic semantics structure、calendar/date selection geometry、
selected-date highlight alignment、month-title centering when next control disabled。

Screenshot/golden tests **非强制**（除非既有 mandatory program gate 要求）。
**但 UI35–UI39 需要视觉验证**：D-04 implementation review 必须包含以下至少一项：

- captured-device UI evidence；或
- deterministic Compose geometry assertions；或
- 既有 approved visual-evidence mechanism。

足以证明：selected date 真居中、month title 真居中、calendar centered/body left-aligned 层级。
**不得**仅以口头自证满足 UI35–UI39。D-05 owns exhaustive accessibility/font-scale matrix。

## 33. Centering acceptance principle（冻结）

```text
CALENDAR / DATE SELECTION = CENTERED
TIMELINE INFORMATION BODY = LEFT ALIGNED
```

- 必须遵循 centered visual axis：month title、weekday/date cells、selected-date highlight、
  date number inside highlight；
- 可保持 left aligned：section date、section weekday、schedule-context label、recorded-intake
  label、medication identity、dose、actual/scheduled time、empty/error explanatory content；
- **不得**把 "centered design" 解释为居中全部页面文本。目标 = **geometric symmetry above +
  readability below**。F21 守卫。

## 34. Evidence hygiene（冻结）

- 新的 textual Gradle/test logs **必须在 `MANIFEST.sha256` 构建前规范化为 UTF-8**；
- evidence summary 的 final test counts 必须来自**最终** JUnit XML；
- **不得**保留 stale pre-amend count summaries；
- **不得**改写历史 D-01/D-03 evidence。

## 35. Contract guards（F1–F21；违反 ⇒ STOP）

| # | forbidden |
|---|---|
| F1 | D-01/D-03 语义修改 |
| F2 | VM/UI 直接 read/repository/DAO/Room |
| F3 | 第二 matcher/generator/truth store |
| F4 | Phase-C/PK coupling |
| F5 | timing delta/adherence 语义 |
| F6 | future Timeline rows |
| F7 | planName-as-identity |
| F8 | antiandrogen 猜测 |
| F9 | internal comparator/UUID 字段渲染 |
| F10 | UI-local pending orchestration state machine |
| F11 | polling/timer/live subscription |
| F12 | persisted Timeline state store |
| F13 | hardcoded user-facing strings |
| F14 | new dependency |
| F15 | Home/Wear/Widget 修改 |
| F16 | D-04 MVP route date/month 参数 |
| F17 | selectedDate 过滤 TimelineReadModel 为单日 |
| F18 | D-01 row ordering 被修改 |
| F19 | calendar selected-day layout 用 manual x/y nudge 或 asymmetric padding 伪造居中 |
| F20 | month title 在一个 navigation control 禁用时移动 |
| F21 | Timeline body 因 calendar-centering 要求而全局居中 |

## 36. Status mapping（truthful）

- D1 complete via D-01；
- D2 complete/consumed via D-01；
- D3 complete via D-03；
- D4 **functional UI targeted by D-04**；accessibility/localization acceptance 保持 open 至 D-05；
- D5 verification/review 保持 gate work；
- **Phase D remains IN PROGRESS**；D-06 recurring gate；D-07 final gate。

## 37. Documentation status

- 本契约状态：`CONTRACT — REVIEW PENDING`；
- **D-04 PRODUCTION — NOT STARTED**；
- D-05 NOT STARTED；指针更新仅限 `TODO.MD` / `CURRENT_STATUS.md` / `ROADMAP.md` /
  `V17_PLAN.md` 的最小状态行；**NEXT: V17-D-04 contract review**。
