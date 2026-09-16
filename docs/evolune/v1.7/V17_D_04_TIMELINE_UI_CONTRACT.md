# V17-D-04 — Timeline UI — Contract

> 状态：`CONTRACT — APPROVED / FROZEN`（**D-04 PRODUCTION — NOT STARTED**）；
> approved final semantic contract HEAD：`c934c24532025f7c82654a7af4e5cd0bafd4f40d`
> （Architect APPROVED；独立复审 APPROVED，P0/P1/P2 = none；closure 记录见 §38）
> docs-only contract；R1/R2/R3 修正已并入。
> R1：display-zone ownership / zone-change load semantics / presentation-zone consistency /
> day-strip viewport-centering 澄清已并入（§3.1/§6.1/§11.1/§23.1-23.3/§31/§35/§37）；其余冻结决定不变。
> R2：logical-request-intent ownership / published-vs-logical separation / pending-zone command
> targeting / snapshot-relative `state.today` 已并入（§3.2/§6.1-6.4/§23.4/§31/§35/§37）。
> R3：logical `requestToday` ownership / refresh advancement / selectDate validation authority /
> logical-vs-published today separation 已并入（§3.2/§5/§6.2-6.5/§23.5/§31/§35/§37）。
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

### 3.1 Display-zone generation ownership（R1 冻结）

`displayZone` 是 **generation/request context**，不是一次附属的 UI formatting lookup。

- TimelineViewModel owns a **current display-zone provider**；
- 对**任何**可能创建新 D-03 authoritative request generation 的操作，必须**成对捕获**：

  ```text
  capturedAt
  currentDisplayZone
  ```

- 适用操作（全部）：initial request、route re-entry、app foreground return、month change、
  retry、return-to-current-month recovery；
- `selectDate()` 保持 **0-read local selection**，**不**创建新的 zone/request generation。

### 3.2 VM-owned logical request intent（R2 冻结）

**D-03 public/private API audit（source-verified）**：closed D-03 coordinator 的 D-04-consumable
public surface 仅 `load(request)` / `refresh(capturedAt)` / `retry(capturedAt)` /
`selectDate(date)` / `state`；其内部概念（`latestLogicalContext` / `latestAcceptedContext` /
`pendingContext` / `selectedDateIntent` / `generationCounter` / `readInFlight`）均为 **private**。
D-04 **不得**依赖私有字段，**不得**仅为 UI 添加 D-03 accessor。

因此 TimelineViewModel 必须拥有一个**窄的 VM-owned logical request intent mirror**，表示 D-04
自身最近提交/接受的 request intent：

```text
TimelineUiRequestIntent(
    requestedMonth: YearMonth,
    selectedDate: LocalDate,
    displayZone: ZoneId,
    requestToday: LocalDate
)
```

（Exact Kotlin name 为实现规划值。）

`requestToday` 定义（R3 冻结）：today derived from the capturedAt + displayZone of the
**latest accepted generation-producing D-04 command**。它**不**意味着：renderer today /
continuously running wall-clock today / background timer date / 独立观测的 `LocalDate.now()`。

规则：

- 该 mirror **不是**第二套 Timeline state machine；
- 它**不得**包含：generation、pending/read-in-flight flags、loading phase、`TimelineReadModel`、
  `TimelineDay`、failure、source-read status、retry scheduler；
- `requestToday` 只是从构造 accepted D-03 command 的**同一 explicit capture context** 派生的
  immutable value；加入它**不**使 mirror 成为另一个 coordinator（F26 边界保持）；
- D-03 仍是唯一 orchestration/publication authority；
- 其唯一用途：当 published state 落后于更新的 pending logical request 时，**正确构造下一个 UI command**。

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
requestToday = today        // 初始 logical intent 与 D-03 initial request 共用同一 capture context
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

### 6.1 Same-zone refresh vs zone-change reload（R1 冻结）

**Same zone**（`currentDisplayZone == TimelineViewModel.latestLogicalIntent.displayZone`）：

- 普通 activation/retry refresh 可继续使用 D-03 已冻结 API：
  `coordinator.refresh(freshCapturedAt)`；
- **不得**改动 D-03。

**Zone changed**（`currentDisplayZone != TimelineViewModel.latestLogicalIntent.displayZone`）：

- **不得**使用 `refresh(capturedAt)` —— D-03 refresh 会保留先前 logical request 的 displayZone；
- 必须构造**新的** `TimelineMonthRequest`：

  ```text
  fresh capturedAt
  new displayZone
  resolved month
  resolved selectedDate
  ```

  并通过已批准的 D-03 `load(newRequest)` 提交 —— 即**一个新的 D-03 generation**；
- **不得**直接调用 `HistoryRangeSource` 或任何 repository。

**Zone-change month/date resolution（确定性；冻结）：**

```text
newToday        = freshCapturedAt.atZone(newDisplayZone).toLocalDate()
newCurrentMonth = YearMonth.from(newToday)
起点            = latest logical Timeline request
```

- **Case A — 旧 requested month 仍为 historical/loadable**（`oldRequestedMonth <= newCurrentMonth`）:
  - `requestedMonth` 保持 `oldRequestedMonth`；
  - 仅当 `YearMonth.from(oldSelectedDate) == oldRequestedMonth` **且** `oldSelectedDate <= newToday`
    时保留旧 selectedDate；
  - 否则解析：`oldRequestedMonth == newCurrentMonth` → `selectedDate = newToday`；
    否则 → `selectedDate = oldRequestedMonth.atDay(1)`。
- **Case B — 旧 requested month 在新 zone 下成为 future**（`oldRequestedMonth > newCurrentMonth`，
  向西跨时区月界可能发生）:
  - `requestedMonth = newCurrentMonth`；
  - `selectedDate = newToday`；
- 正常 D-04 activation **不得**仅因系统 display zone 变化而把用户留在 future-month request；
- 这**不**改变 D-03 `NOT_LOADABLE` 语义；只是 D-04 控件构造了一个新的 valid logical request。

> **R2 comparison source（冻结）**：same-zone/zone-change 判断必须比较
> `currentDisplayZone` 与 **`TimelineViewModel.latestLogicalIntent.displayZone`**，
> **不得**使用 `coordinator.state.value.displayZone`——pending context 可能使二者不同；
> published state 只回答"当前渲染的是什么"，logical intent 才回答"下一个 command 作用于什么"。
> `coordinator.state.value.displayZone` 仅当两者已被证明指向同一 latest logical context 时才可使用。

### 6.2 Intent-mirror 初始化与更新规则（R2 冻结）

初始化（VM construction）：

```text
capture fresh capturedAt + displayZone -> derive today/current month
initial intent = current month + today + displayZone
同一组值构造唯一的 D-03 initial request（intent 与 initial request 起始一致，无第二读）
```

更新规则（同步，在 D-04 **接受/发出** logical command 时，而不是 D-03 稍后发布 state 时）：

| command | intent 更新 |
|---|---|
| `load` / month change | intent = 新 request 的 month + selectedDate + displayZone + `requestToday = commandToday`（在提交 `coordinator.load(newRequest)` 的同时） |
| zone-change load | 同上（含 `requestToday`）：resolved newRequest → intent 立即更新 → `coordinator.load(newRequest)` |
| valid `selectDate` | `logicalIntent.selectedDate = selectedDate` + `coordinator.selectDate(selectedDate)`（两者对 selection 均 0-read；`requestToday` 不变） |
| invalid/out-of-month/future selection | **不**替换 logical intent（与 CLOSED D-03 语义一致） |
| same-zone refresh | 保留 month/selectedDate/displayZone；**更新 `requestToday` = 该 refresh fresh capturedAt 派生值**（R3 修正 R2 的 “intent 保持不变” 表述） |

**每个 generation-producing command 都更新 requestToday（R3 冻结）**：每次捕获
`capturedAt + currentDisplayZone` 时派生

```text
commandToday = capturedAt.atZone(currentDisplayZone).toLocalDate()
```

latest logical intent 同步记录 `requestToday = commandToday`。适用于：initial load、explicit
month load、same-zone activation refresh、zone-change load、foreground refresh/load、retry、
return-to-current-month recovery。

示例（same-zone refresh advancement）：

```text
Oct 1:  intent.requestToday = Oct 1
Oct 20 route re-entry: refresh(capturedAt Oct 20)
-> latest logical intent after acceptance:
   requestedMonth = October
   selectedDate   = previous valid intent
   displayZone    = same zone
   requestToday   = Oct 20
```

这与 CLOSED D-03 的 new-generation today 语义一致。Refresh 后若被保留的 logical selectedDate
在新 command context 下变为 invalid：按已冻结的 D-03-compatible request resolution rules 处理
（同一 zone 且时钟向前时，已 valid 的 selectedDate 通常保持 valid；zone-change 继续走 R1
Case A/B）。**不得**发明额外的自动 selection 移动。

### 6.3 Never derive command target from last published state（R2 冻结）

- D-04 command construction 必须使用 **VM-owned latest logical intent**，**不得**把最后发布的
  `TimelineRangeState` 当作 logical request target；
- published state 的权威范围：**当前渲染的内容**；
- logical intent 的权威范围：**D-04 意图让下一个 command 操作的对象**；
- 二者必须保持分离（镜像 D-03 已冻结的 latest logical context vs last published state 区分）。

**Pending-zone 示例（确定性冻结）：**

```text
Example A
  published state zone = Europe/Paris
  latest logical intent = Asia/Tokyo（Tokyo load 仍 pending）
  current display zone = Asia/Tokyo
  -> foreground/re-entry: current zone == logical intent zone
     => refresh(freshCapturedAt)（作用于 D-03 的 latest logical Tokyo request）
     不得因为 published state 仍是 Paris 而再发一次 zone-change Tokyo load

Example B
  published state zone = Europe/Paris
  latest logical intent = Asia/Tokyo（Tokyo request pending）
  current display zone 变回 Europe/Paris
  -> foreground/re-entry: current zone != logical intent zone
     => 构造/load 新的 Europe/Paris request
     不得因为旧 published state 也是 Europe/Paris 而调用 plain refresh
```

### 6.4 Retry ownership / return-to-current-month recovery（R2 冻结）

**Retry after ERROR** 使用：latest logical intent + fresh capturedAt + fresh currentDisplayZone +
fresh `commandToday`：

- zone 未变 → 保留 requestedMonth/selectedDate/displayZone、更新 `requestToday = commandToday`，
  走 D-03-compatible 的 `coordinator.retry/refresh` 路径；
- zone 已变 → 应用 R1 zone-resolution rules、更新**完整** logical intent（含 `requestToday`）、
  `coordinator.load(newRequest)`；
- **不得**从 stale rendered state 推导 retry target。

**INVALID_REQUEST / NOT_LOADABLE defensive recovery**：

```text
fresh capture capturedAt + currentDisplayZone
newToday = capturedAt.atZone(zone).toLocalDate()
logical intent = YearMonth.from(newToday) + newToday + zone + requestToday = newToday
coordinator.load(newRequest)   // exactly once
```

不得使用 stale published month/date 作为 command authority。

### 6.5 selectDate validation authority（R3 冻结）

VM selection command boundary：candidate date 为 valid **iff**：

```text
YearMonth.from(candidate) == latestLogicalIntent.requestedMonth
AND
candidate <= latestLogicalIntent.requestToday
```

Valid → `logicalIntent.selectedDate = candidate` + `coordinator.selectDate(candidate)`；
source reads = **0**。Invalid → logical intent 不变、coordinator selection intent 不变、**0 reads**。
必须与 CLOSED D-03 语义一致。

**不得**用 published state 验证：`candidate <= coordinator.state.value.today` **不是**通用
command-validity 规则（published state 可能落后于 latest accepted logical context）。示例：

```text
published state.today = Sep 16
latest logical refresh requestToday = Sep 17（refresh 仍 pending）
candidate = Sep 17
-> VM 按 latestLogicalIntent.requestToday = Sep 17 判定（不是 stale published Sep 16）
```

**不得**用 live wall clock 验证：selection handler **不得**用 `LocalDate.now()` / `Instant.now()` /
`Clock.instant()` / `ZoneId.systemDefault()` 独立推导 validity；selection command 作用于 latest
accepted logical request context，而不是无关的更新 wall-clock 观测。fresh wall-clock/zone capture
只在**有意接受一个 generation-producing command** 时发生。

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

### 11.1 Selected-cell viewport centering（R1 澄清）

在既有 UI35–UI39 / F19–F21 之外补充一个 presentation 点：

- **selected day cell 应在实际可行时被带入 day-strip viewport 的水平视觉中心**；
- 这**独立于** day-cell 内部居中：
  - selected date number → 几何居中于自身 highlight；
  - selected day cell → 在 layout bounds 允许时居中于可见 day-strip viewport；
- 边缘日期：优先使用**对称 content padding** 的布局，使 first/last selectable cell 在所选
  Compose primitive 允许的情况下也能到达视觉居中位置；
- **禁止** manual x-offset magic number；
- 若平台约束下无法精确 viewport 居中：selected cell **至少必须完全可见**，且 day-strip
  容器本身保持对称。

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

### 23.1 No timezone-split presentation（R1 冻结）

- **每一个可见 Timeline timestamp，只要属于一个已发布的 D-03 state，就必须以该 state/request 的
  displayZone 格式化**；适用：`scheduledAt`、`occurredAt`、presentation 需要的 section-date 计算、
  Today/Yesterday 判定、absolute date labels、weekday labels、任何 focus/scroll date mapping；
- row/section renderer **不得**独立调用 `ZoneId.systemDefault()` 去重新解释已发布 model；
- 该渲染快照的权威 displayZone 是 D-03 已发布的 displayZone。

### 23.2 12/24h 独立于 display zone（R1 冻结）

- 12/24h choice → 现行 app/system time-format preference（既有约定）；
- 但 **time-format preference != timezone source**：
  - `displayZone = Europe/Paris` + `is24Hour = false` 意味着：**在 Europe/Paris 中格式化该 instant，
    使用 12-hour 表示法**；
  - 它**绝不**意味着：render 时用当时 `ZoneId.systemDefault()` 去转换 instant。

### 23.3 Formatter requirement（R1；source-audited）

- 源核对结果：`HistoryFormatting.timeText(instant, zone, is24Hour)`、
  `fullDateTimeText(instant, zone, is24Hour)`、`actualTimestampPresentation(...)`、
  `scheduleTimeText(instant, zone, ...)` **均已接受显式 `ZoneId`**，内部不使用
  `ZoneId.systemDefault()` → D-04 必须传入 D-03 state 的 `displayZone`，可直接复用；
- 若某个既有 helper 隐式使用 system default 而无法满足本契约：**不得**为 Timeline timestamp
  conversion 静默复用它；
- D-04 可添加一个只接收 `Instant / ZoneId / is24Hour` 的窄 presentation formatter/helper
  （例如用于 section date/weekday 派生），**不得**改动 History 行为、**不得**做 broad History refactor；
- dose formatting 复用保持不变。

### 23.4 Snapshot-relative today（R2 冻结）

- 对**每一个已发布的 `TimelineRangeState`**，UI 必须把 `state.today` 当作该渲染快照的**权威
  "today" 引用**；这与"用户/生命周期创建新 generation 时捕获 fresh current Instant/zone"是两件事；
- renderer **不得**为了在一个未变化的 published snapshot 内改变相对标签而独立读取
  `LocalDate.now()` / `Clock.instant()` / `Instant.now()`；
- **Today / Yesterday 标签**：

  ```text
  section.date == state.today               -> Today
  section.date == state.today.minusDays(1)  -> Yesterday
  otherwise                                  -> absolute date + weekday
  （全部使用 published state 的 display context）
  ```

  标签在 D-03 发布更新 generation 之前保持稳定。示例：`state.today = Sep 16`，屏幕跨午夜保持
  打开且无 refresh/load —— Sep 16 对未变化的快照仍是 **Today**；在合法 fresh generation 发布
  `state.today = Sep 17` 之后，Sep 16 才可以变为 **Yesterday**；
- **当前月控件状态**：渲染已发布快照的 month control enabled/disabled 时，派生
  `publishedCurrentMonth = YearMonth.from(state.today)`；Compose rendering 期间**不得**独立读取
  wall-clock。因此 next-month 的 enabled/disabled 渲染保持 snapshot-consistent；
- 当用户实际触发一个 generation-creating action 时：ViewModel 捕获 fresh current time + display
  zone，并按更新的 context resolve command（§6.2）。

### 23.5 logical requestToday vs published state.today（R3 冻结）

- `latestLogicalIntent.requestToday` 用途：**next-command construction / selection validation**；
- `TimelineRangeState.today` 用途：**current published snapshot rendering**；
- 两者在新工作 pending 期间**可以合法地不同**。示例：

  ```text
  published state.today       = Sep 16
  logical intent.requestToday = Sep 17（refresh/load pending）
  -> UI rendering:  Sep 16 仍是 "Today"
  -> command validation: Sep 17 已可作为 valid selectedDate
     （若 Sep 17 属于 latest logical request month）
  ```

  该区分是刻意设计；
- mirror **不自动 tick**：`requestToday` 只在新的 generation-producing D-04 command 捕获 fresh
  Instant/zone 时前进；**不得**引入 midnight timer、polling、date-change loop 或 renderer
  clock observation。

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
| UI40 | same displayZone foreground/re-entry: uses D-03 refresh with fresh capturedAt; no unnecessary load |
| UI41 | displayZone changes while activity-scoped VM survives: ordinary refresh path is NOT used; exactly one D-03 load with the new displayZone is submitted |
| UI42 | zone change preserving a past requested month: requested month unchanged and valid selectedDate retained |
| UI43 | zone change makes the previous requested month future: request resolves to new current month + newToday |
| UI44 | visible scheduled/actual timestamps and section labels use the published state's displayZone, not an independently queried system zone |
| UI45 | 12/24h preference changes notation only and does not change the timezone used to interpret the published Timeline snapshot |
| UI46 | selecting an off-screen day scrolls it into a centered/visually centered viewport position where layout bounds permit |
| UI47 | first/last selectable day preserves symmetric strip geometry and never disturbs the internal weekday/date/highlight center axis |
| UI48 | published state Paris + latest logical intent pending Tokyo + current zone Tokyo -> activation chooses same-zone refresh against the logical Tokyo context, not another load based on the published Paris state |
| UI49 | published state Paris + logical pending Tokyo + current zone changes back to Paris -> activation performs a new Paris load rather than a plain refresh |
| UI50 | valid selectDate updates the VM logical intent immediately and remains the selection used by a subsequent refresh while older published state is still visible |
| UI51 | an unchanged published snapshot crossing midnight does NOT independently change Today/Yesterday labels; labels derive from state.today |
| UI52 | after a new generation publishes a new state.today, relative date labels update to the new snapshot reference date |
| UI53 | month-navigation enabled/disabled rendering derives from state.today and does not change merely from recomposition/current wall-clock passage |
| UI54 | same-zone refresh across midnight updates latestLogicalIntent.requestToday while preserving requestedMonth/selectedDate/displayZone as valid |
| UI55 | same-zone refresh later in the same current month advances requestToday; a date previously future but now <= requestToday becomes a valid 0-read selectDate |
| UI56 | published state.today may lag a pending logical requestToday; selectDate validity follows requestToday, not published state.today |
| UI57 | a candidate date later than latestLogicalIntent.requestToday is rejected even if an independently observed wall clock would already consider it today |
| UI58 | month-change / retry / zone-change commands update requestToday from the same capturedAt + displayZone used for the submitted D-03 generation |
| UI59 | renderer relative labels continue to use published state.today and never latestLogicalIntent.requestToday |

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
| F22 | row/section renderer 独立用 `ZoneId.systemDefault()` 重新解释 Timeline instants（而非已发布的 D-03 displayZone） |
| F23 | displayZone 变化被 plain `coordinator.refresh(capturedAt)` 处理（该 API 会保留旧 logical zone） |
| F24 | zone-change 处理走 D-03 `load(newRequest)` 之外的路径（直接 source read、repository call 或第二 orchestration path） |
| F25 | D-04 generation-command targeting 用最后的 published `TimelineRangeState` 替代 VM 的 latest logical request intent |
| F26 | VM-owned logical intent 变成第二套 orchestration machine（持有 generation counter、pending/read status、`TimelineReadModel`、phase 或 failure） |
| F27 | Timeline renderer 用 `LocalDate.now()`/`Instant.now()`/`Clock` 读取来推导未变化 published snapshot 的 Today/Yesterday 或当前月控件状态 |
| F28 | Today/Yesterday/current-month presentation 不派生自已发布的 `TimelineRangeState.today` |
| F29 | TimelineViewModel 在可能已存在更新 logical request context 时用 published `TimelineRangeState.today` 验证 `selectDate` |
| F30 | TimelineViewModel 用独立读取的 live wall clock 验证 `selectDate`（validity 必须用 `latestLogicalIntent.requestToday`） |
| F31 | `latestLogicalIntent.requestToday` 未从每个被接受的 generation-producing command 的 capturedAt + displayZone 刷新 |
| F32 | logical `requestToday` 被用作 CURRENT snapshot rendering 中 published `TimelineRangeState.today` 的替代 |

## 36. Status mapping（truthful）

- D1 complete via D-01；
- D2 complete/consumed via D-01；
- D3 complete via D-03；
- D4 **functional UI targeted by D-04**；accessibility/localization acceptance 保持 open 至 D-05；
- D5 verification/review 保持 gate work；
- **Phase D remains IN PROGRESS**；D-06 recurring gate；D-07 final gate。

## 37. Documentation status

- 本契约状态：`CONTRACT — APPROVED / FROZEN`（Architect APPROVED；独立复审 APPROVED，
  P0/P1/P2 = none；P3 editorial dispositions 见 §38）；
- **D-04 PRODUCTION — NOT STARTED**；
- D-05 NOT STARTED；指针更新仅限 `TODO.MD` / `CURRENT_STATUS.md` / `ROADMAP.md` /
  `V17_PLAN.md` 的最小状态行；**NEXT: V17-D-04 production implementation**
  （仅可针对 frozen final contract @ `c934c24532025f7c82654a7af4e5cd0bafd4f40d`）。
- **R1 amendment（architect REQUEST_CHANGES；docs-only）**：已并入
  §3.1 display-zone generation ownership（capturedAt + currentDisplayZone 成对捕获）、
  §6.1 same-zone refresh vs zone-change reload（zone-change 走 D-03 `load(newRequest)`，
  Case A/B month/date resolution）、§11.1 selected-cell viewport centering、
  §23.1-23.3 presentation-zone consistency / 12-24h 独立 / formatter source audit、
  §31 的 UI40–UI47、§35 的 F22–F24。其余 D-04 决定（surface/nav、ownership、retention、
  lifecycle、selectedDate-as-focus、ordering、row truthfulness、identity/dose、delta 禁令、
  七相位映射、pending 政策、centering rules、D-05 split、UTF-8 evidence）保持不变；
  状态保持 `CONTRACT — REVIEW PENDING`、production 保持 NOT STARTED。
- **R2 amendment（architect REQUEST_CHANGES；docs-only）**：已并入
  §3.2 VM-owned logical request intent（含 D-03 public/private API audit；narrow intent mirror；
  非第二 state machine）、§6.1 R2 comparison source（`latestLogicalIntent.displayZone`，
  不得用 `coordinator.state.value.displayZone`）、§6.2 intent 初始化/同步更新规则、
  §6.3 published-vs-logical 分离 + pending-zone 示例 A/B、§6.4 retry/recovery targeting、
  §23.4 snapshot-relative `state.today`（Today/Yesterday 与当前月控件），
  §31 的 UI48–UI53、§35 的 F25–F28。  其余 D-04 决定（含 R1 全部内容与 centering rules）不变；
  状态保持 `CONTRACT — REVIEW PENDING`、production 保持 NOT STARTED。
- **R3 amendment（architect REQUEST_CHANGES；docs-only）**：已并入
  §3.2 扩展的 `requestToday`（narrow mirror 的 immutable capture 派生值，F26 边界保持）、
  §5 初始 `requestToday = today`、§6.2 每个 generation-producing command 更新 `requestToday`
  （含 same-zone refresh advancement 示例与 “intent 保持不变” 表述的 R3 修正）、
  §6.4 retry/recovery 的 `commandToday`/`requestToday` 同步、§6.5 selectDate validation authority
  （`requestToday` 判定、不用 published state.today、不用 live wall clock）、
  §23.5 logical `requestToday` vs published `state.today` 分离、
  §31 的 UI54–UI59、§35 的 F29–F32。其余 D-04 决定（含 R1/R2 全部内容与 centering rules）不变；
  状态保持 `CONTRACT — REVIEW PENDING`、production 保持 NOT STARTED。

---

## 38. Final contract closure（bookkeeping；APPROVED / FROZEN）

- Approved final semantic contract HEAD：`c934c24532025f7c82654a7af4e5cd0bafd4f40d`；
- Architect verdict：**APPROVE V17-D04 CONTRACT — ARCHITECT CLOSURE**；
- Independent verdict：**APPROVE V17-D04 CONTRACT**（independent reviewer：Qwen3.8 Flash，
  fresh independent read-only session；reviewed final contract HEAD 同上）；
- Independent findings：P0 = none，P1 = none，P2 = none；
- 本契约进入 `CONTRACT — APPROVED / FROZEN`；**No further D-04 contract semantic changes
  without explicit reopening**；
- 本 closure 为 bookkeeping/status：**不新增、不改变任何 D-04 语义要求**；D-01/D-03 remain
  frozen；
- Production implementation 仅授权针对上述 frozen final contract HEAD 进行。

### 38.1 P3 dispositions（editorial；不重写 frozen 契约正文）

- **P3-1**：部分 frozen-document 的 heading/status prose 仍保留早期范围简写
  `UI1–UI39 / F1–F21`；**权威 mandatory ranges = `UI1–UI59` / `F1–F32`**。Implementation
  planning / review / evidence 必须使用**最终表格本身**，不得依赖 stale heading-range
  shorthand。仅为该 editorial 问题**不**重写 approved contract body。
- **P3-2**：§32 的 literal “no verbal self-certification” 句子只点名 UI35–UI39；但最终契约通过
  mandatory UI matrix、geometry/instrumentation scope 与 §11.1 viewport-centering rules 独立要求
  UI46–UI47。因此 implementation review **必须**对 **UI35–UI39 AND UI46–UI47** 要求真实几何验证：
  deterministic Compose geometry assertions / captured-device visual evidence / 既有 approved
  visual-evidence mechanism 之一；verbal-only implementation claims 不足。无需重写 approved
  契约语义。

### 38.2 Final frozen D-04 summary（要点）

- Navigation：History → Timeline entry card → dedicated `TIMELINE_ROUTE`；no sixth tab / Home /
  embed / deep-link / date route argument；
- Ownership：one activity-scoped `TimelineViewModel` + one `TimelineRangeCoordinator`；no direct
  source/repository/DAO/Room bypass；
- Initial：current month + today；
- Logical command intent：`requestedMonth` / `selectedDate` / `displayZone` / `requestToday`；
- Published rendering：`TimelineRangeState` / `state.today` / `state.displayZone`；
- Command–render split：`latestLogicalIntent.requestToday` → next command / selectDate
  validation；published `state.today` → current snapshot rendering；
- Zone：same-zone → refresh/retry-compatible path；changed-zone → new D-03 `load(newRequest)`；
- Lifecycle：initial read once；re-entry/foreground fresh generation；recomposition 0 reads；
  selection 0 reads；
- Timeline：selectedDate = focus, not filter；whole loaded month visible；newest day sections
  first；rows canonical ascending；
- Rows：MATCHED = schedule + recorded sides；UNRECORDED = schedule + neutral
  no-recorded-intake；UNMATCHED = recorded only；
- Identity：KNOWN / PARTIAL / UNAVAILABLE truth preserved；
- Forbidden：delta / early / late / on-time / adherence；route in MVP；planName-as-identity；
  AA guessing；internal IDs；new truth store；PK coupling；
- Visual：calendar/date selector centered；month title geometrically centered；symmetric nav
  slots；weekday/date/highlight share center axis；selected date number centered on both axes；
  selected cell viewport-centered when practical；Timeline body left aligned；
- Verification：`UI1–UI59` + `F1–F32`；Android instrumentation required；real geometry evidence
  required for UI35–UI39 and UI46–UI47；
- D-05：final accessibility/localization hardening remains open；
- **NEXT：V17-D-04 production implementation**（仅针对 frozen contract @
  `c934c24532025f7c82654a7af4e5cd0bafd4f40d`；本 closure 不新增语义）。
