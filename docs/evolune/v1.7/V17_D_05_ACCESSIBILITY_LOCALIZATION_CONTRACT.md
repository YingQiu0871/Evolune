# V17-D-05 — Accessibility / Localization Hardening — Contract

> 状态：`CONTRACT — APPROVED / FROZEN`；**PRODUCTION — IMPLEMENTED / APPROVED / CLOSED**；
> **EVIDENCE — COMPLETE**（final implementation HEAD：
> `2a79f1d048905113f53d4be470071a94072c2f96`；final closure 记录见 §48）；
> approved semantic contract HEAD：`b1cdd662fe1bee14beab5cad651aef772f211c68`
> （Architect **APPROVE V17-D05 CONTRACT — ARCHITECT CLOSURE**；独立复审 **APPROVE V17-D05
> CONTRACT**（Qwen3.8 Flash，fresh independent read-only session），P0/P1/P2 = none；
> P3 dispositions 与 closure 记录见 §47）；**No further semantic D-05 contract change without
> explicit reopening**；
> docs-only contract；D-05 只加固 presentation/accessibility/localization，不重解释 D-04。
> Slice：**V17-D-05 — final accessibility + localization hardening of the shipped D-04 Timeline
> surface（只加固 presentation/accessibility/localization，不重解释 D-04）**
> Base HEAD：`e631bf75852cbf9ebf286059eea70a489a096328`（D-04 CLOSED / FROZEN）
> 上游（约束性输入，**优先于本文件**）：
> - D-04 APPROVED / FROZEN 语义契约：`c934c24532025f7c82654a7af4e5cd0bafd4f40d`
>   （final implementation `e53342bcd6c4c4af428adc5822f603c71ed8bb24`，closure `e631bf7`）
> - [`V17_SPEC.md`](V17_SPEC.md) §10（UI Principles：factual / 无 grading / 无 shame 语言）
> - [`V17_PLAN.md`](V17_PLAN.md) §7（D-05 = accessibility and localization）
> - [`V17_ACCEPTANCE.md`](V17_ACCEPTANCE.md) §5 D4（无障碍与本地化：时制 12/24、语言、字号；
>   UI 测试 + 人工抽查）+ §0 G1–G7
> - pre-contract audit：`review-packets/v17-d05-pre-contract-audit.txt`（strict read-only）
> D-01/D-03/D-04 全部保持 CLOSED / FROZEN；D-05 **不得**修改其任何语义。

---

## 0. Source-verified identifiers（写死名字前已核对）

| 事实 | 位置（D-04 生产，审计时核对） |
|---|---|
| day-cell 结构：Column(width 48dp) → weekday Text + Box(36dp, CircleShape) → number Text | `TimelineScreen.kt:369-426`；常量 `DAY_CELL_WIDTH = 48.dp`（:70）、`DAY_HIGHLIGHT_SIZE = 36.dp`（:71）、`MONTH_CONTROL_SLOT = 48.dp`（:72） |
| day-cell 现状语义：`clickable(enabled)` + `semantics(mergeDescendants = true) { contentDescription = dayCellDescription; selected; disabled }` | `TimelineScreen.kt:385-395` |
| day-cell 现状 description：ISO 日期 + “今天/已选中/未到日期”（全角逗号 joinToString） | `dayCellDescription` `TimelineScreen.kt:429-435` |
| scroll/scroll-to-section（视觉 only） | `TimelineScreen.kt:334-338`（strip 居中）、`LaunchedEffect(state.selectedDate, model.sections)`（body focus，视觉 only） |
| Month nav：等宽 48dp 槽 + weight(1f) 居中 title；next `enabled = model.canGoToNextMonth` | `TimelineScreen.kt:258-318` |
| Section header：Relative（今天/昨天资源）或 Absolute（`timeline_section_date_weekday` + 单字符星期） | `TimelineScreen.kt:439-474` |
| MATCHED 两侧：ScheduleSide / RecordedSide 两个 Column（各自 label + time + `timeline_row_identity_dose`） | `TimelineScreen.kt:479-599` |
| UNRECORDED：ScheduleSide + `timeline_no_recorded_intake` Text | `TimelineScreen.kt:506-520` |
| UNMATCHED：RecordedSide only | `TimelineScreen.kt:522-528` |
| 七状态区域：Loading/NeutralNotice/DefensiveBlock/TimelineError | `TimelineScreen.kt:145-214, 604-683` |
| History 入口卡：Card + `clickable(onClickLabel)`，title/subtitle + 装饰 icon(null) | `HistoryScreen.kt:280-317` |
| UI 只消费 state/is24Hour/callbacks；零时钟/零 systemDefault（renderer 守卫） | D-04 F22/F27/F28 guards；`TimelinePresentation.timeText` 走 `HistoryFormatting` 显式 ZoneId |
| 现有 weekday 资源（可见，单字符） | `history_weekday_mon..sun`（values/ + values-zh-rCN/ 均有） |
| 现有状态资源 | `history_cell_today`（今天）、`history_cell_selected`（已选中）、`history_cell_not_arrived`（未到日期） |
| 现有 identity/dose/time 资源 | `ester_e2..en`、`timeline_identity_partial`、`timeline_identity_unavailable`、`timeline_row_identity_dose`、`history_label_current_schedule_context`、`history_label_actual_time` |
| 资源权威：`values/`（中文默认）与 `values-zh-rCN/`（中文）；**无 `values-en/`**。当前整文件集合为 values/ 540 unique keys vs values-zh-rCN/ 532（**非全局 532/532 parity**）；其中 8 个为 **PRE-EXISTING default-only NON-TIMELINE keys**（`about_developer_contact_email`、`about_developer_contact_uri`、`about_website_url`、`export_filename`、`home_concentration_placeholder`、`home_level_placeholder`、`records_quick_add_format`、`unit_mg`），属历史遗留、**不在 D-05 范围**（不扩张为整应用 localization cleanup）。Timeline keys 保持 **22/22 parity**；D-05 parity 义务仅适用于 `timeline_*` + `timeline_a11y_*` | 审计 §14/§15（source-verified）+ P3-1 closure 修正 |
| 现有执行性 guards | `TimelineUiArchitectureGuardTest`（25 tests，含 forbidden wording + timeline key parity） |
| 现有 geometry/instrumentation 断言基线 | `TimelineGeometryTest`（8）、`TimelineNavigationTest`（3）、`TimelineLifecycleTest`（4）、`TimelineScreenTest`（23） |
| Compose test 依赖 | composeBom 2026.02.01；`androidx.compose.ui.test:ui-test-junit4`；`DeviceConfigurationOverride`（Compose UI test 公共 API，>=1.7）用于 fontScale override |

---

## 1. Goal 与范围（冻结）

D-05 对**已发布的 D-04 Timeline 表面**做最终 accessibility / localization hardening：
semantic 结构、content-description 策略、heading、roles/states、reading order、font-scale
resilience、hit target、localization key/placeholder parity、最终 wording、accessibility 专用
日期/星期措辞、bounded device/evidence gate。

**In scope（冻结）**：TalkBack/semantic structure；day-cell 单节点模型；Role/selected/disabled；
spoken grammar（localized，非 ISO）；full weekday 资源；month-title/section-header heading；
entry-card Role.Button；MATCHED 双侧 semantic groups；UNRECORDED/UNMATCHED truthful semantics；
七状态可到达 target；font-scale 1.0/1.3/1.5/2.0 responsive 规则；hit target 规则；Resource
key/placeholder parity guards；Timeline wording 一致性；bounded instrumentation/visual evidence。

**Out of scope（冻结，禁止发明）**：任何新 Timeline 产品行为；新 medication 语义；新 row family；
新 navigation destination；新 locale family（`values-en` 等）；theme redesign；D-01/D-03 修改；
PK/schema/truth-store；D-04 semantics 重解释。

### 1.1 P2 closure（本契约必须闭环的五项）

1. day-cell announcement/state semantics（单节点 + Role.Button + selected/disabled 属性 +
   localized 非 ISO 语法）→ §3–§6；
2. missing heading semantics（month title + section headers）→ §8/§9；
3. History Timeline entry-card role（Role.Button）→ §10；
4. font-scale resilience/evidence（1.0/1.3/1.5/2.0 + responsive 规则 + executed evidence）
   → §22–§29、§42；
5. MATCHED per-side accessibility grouping / spoken separator → §12–§15。

## 2. 不可变边界（D-04 frozen semantics）

D-05 必须原样保留（任何改动 ⇒ STOP）：History → Timeline `TIMELINE_ROUTE`；activity-scoped
`TimelineViewModel` + 单一 `TimelineRangeCoordinator`；`requestToday` / `state.today` 职责分离；
`state.displayZone` ownership；same-zone refresh vs zone-change `load(newRequest)`；month
navigation 语义（next 在当前月禁用、selection 解析规则）；selectedDate focus-not-filter；
whole-month rendering；newest-day sections first + rows canonical ascending；三 row families 与
truthfulness；identity KNOWN/PARTIAL/UNAVAILABLE；per-side dose provenance；七个 phase；无 UI-local
pending machine；calendar centered / body left aligned；D-04 全部 F1–F32 guards 继续为真。

## 3. Day cell — final accessibility node model（冻结）

- 每个 Timeline day cell 在 **accessibility/merged tree 中恰好暴露一个 meaningful node**；
- 视觉 children（weekday Text、highlight Box、number Text）**不得**成为额外 TalkBack
  navigation stop，**不得**产生重复 spoken 内容（§5/§6 的 phrase 是唯一 spoken 内容）；
- 同时候：

```text
merged / accessibility tree : 恰好一个 day-cell node
unmerged test tree          : timeline-day-weekday-<date> / timeline-day-highlight-<date> /
                              timeline-day-number-<date> / timeline-day-cell-<date> 仍可查询
```

- **不强制**某一 Compose primitive（`clearAndSetSemantics` 或等价技术皆可）；契约冻结的是
  **可观察结果**（上述两棵树的行为），implementation 细节自由；
- D-04 geometry 断言（unmerged tags）必须继续可执行（见 §30）。

## 4. Day cell role（冻结）

- day cell 是 interactive button：**`Role.Button`**；
- **禁止**：`Role.Tab`、radio-button semantics、伪造 collection/grid selection semantics；
- 理由（冻结）：`selectedDate` 是 Timeline presentation focus，不是 tab/category/product mode；
  day cell 保持 D-04 的 **zero-read focus-selection action**。

## 5. Day cell state semantics（冻结）

- 必须暴露 semantic properties：
  - `selected = true/false`（当前选中日）；**selected 主要通过该 semantic property 表达**；
  - 当 defensive future/unavailable 分支实际不可选时，暴露 **disabled** semantics；
- **不要求** contentDescription 重复 “已选中”（selected 属性已表达）；
- disabled 同理以 platform disabled semantics 为主；“未到日期”短语**可以**在该分支可达时发声，
  但**不替代** disabled semantics；
- 禁止只靠颜色表达 screen-reader state（颜色仅视觉；语义由上述属性承担）。

## 6. Day cell spoken date grammar（冻结）

- 移除机器式 ISO 语音（如 `2026-09-16`）；
- spoken phrase grammar（资源/localization-backed）：

```text
localized month/day  +  full weekday name  +  optional “今天”
示例：9月16日，星期三，今天   /   9月15日，星期二
```

- **不得**用可见单字符星期（一/二/…）作为 spoken weekday；
- selected/disabled 由 §5 semantic properties 承担，**不要求** lexical suffix 重复；
- 计划资源 keys（planning values；必须存在于两个资源文件且 placeholder 一致）：

```text
timeline_a11y_day_format        = "%1$d月%2$d日"
timeline_a11y_weekday_mon..sun  = 星期一 … 星期日（7 keys）
timeline_a11y_date_weekday      = "%1$s，%2$s"
timeline_a11y_day_cell_today    = "%1$s，%2$s，%3$s"
```

- 今天标记复用既有 `history_cell_today`（今天）；day cell merged description 由上述资源组合，
  最终 spoken 结果必须与示例 grammar 一致；不得出现 ISO 日期、不得出现单字符星期。

## 7. Day strip structure（冻结）

- day strip 保持普通 horizontal list（LazyRow）；**禁止** calendar-grid collection semantics、
  tab-row semantics、pager semantics；
- 至多 31 个 day cell 保持独立可导航（每 cell 一个 node，见 §3）；
- D-04 viewport-centering 行为（selected cell 居中、first/last 对称 padding、无手动 x-offset）
  继续冻结（大字号下的 resolved-width 规则见 §27）。

## 8. Month title heading（冻结）

- Timeline month title 暴露 **`heading()`**；
- visible text 保持既有 localized month title；visible text 充分时**无需**自定义
  contentDescription；
- **禁止**自动 live announcement（§20）。

## 9. Section header heading + spoken form（冻结）

- 每个 Timeline date-section header 暴露 **`heading()`**；
- Today / Yesterday header 使用既有资源 wording 即可（visible = spoken）；
- absolute-date header 的 **spoken** phrase 必须是：

```text
localized absolute date + FULL weekday name
示例：2026年9月10日，星期四
```

  （planned resource：`timeline_a11y_date_weekday`；full weekday 资源见 §6/§35）；
- visible short weekday form（`timeline_section_date_weekday`）**保持视觉不变**；不得仅为
  TalkBack 而加长 visible header —— 通过 accessibility description 提供 full-weekday 短语。

## 10. History Timeline entry card semantics（冻结）

- entry card 保持**一个 merged clickable node**；
- 增加 **`Role.Button`**；
- 保留：title、subtitle、`onClickLabel`（`timeline_entry_action`）、装饰 icon
  `contentDescription = null`；
- visible text + onClickLabel 充分时**无需** custom whole-card contentDescription。

## 11. Entry copy terminology（冻结）

- Timeline 用户可见术语统一用 **“方案”**（不再混用 “计划上下文”）；
- **必须**更新 `timeline_entry_subtitle` 远离 “计划上下文”，intended meaning 等价：

```text
按月份查看当前方案与已记录摄入
```

  （exact punctuation 遵循仓库中文文案惯例；medication truth 语义不变）；
- 其余 terminology 规则见 §37。

## 12. MATCHED row — accessibility structure（冻结）

- visual D-04 双侧布局**不变**；
- accessibility tree 必须暴露 **恰好两个 meaningful side groups**：
  1. Current schedule-context side；2. Recorded-intake side；
- 两侧必须是**彼此分离的 accessibility nodes**；**禁止**：把两侧 merge 成一个 node、flatten
  schedule 与 actual facts、identity/dose/time 交叉替换。

## 13. MATCHED schedule-side speech（冻结）

- schedule side 的 spoken semantic order 必须为：**label → scheduled time → identity → dose**；
- 概念结果：`当前方案时间，08:00，雌二醇，2.0 mg`；
- 只使用 schedule-side 字段（`scheduleContext.scheduledAt` / `.identity` / `.matchKey` dose）；
- 实现可由资源 + presentation data 组合；planned resource：

```text
timeline_a11y_row_side = "%1$s，%2$s，%3$s，%4$s"
```

- **不得**让 TalkBack 依赖可见 “·” separator（见 §15）。

## 14. MATCHED recorded-side speech（冻结）

- recorded side 的 spoken semantic order 必须为：**label → actual time → identity → dose**；
- 概念结果：`实际时间，08:05，雌二醇，3.0 mg`；
- 只使用 recorded-intake 字段（`recordedIntake.occurredAt` / `.identity` / `.matchKey` dose）；
- **永不**推断 historical prescription truth。

## 15. Visible identity/dose separator（冻结）

- 既有 visible `identity · dose` 布局**可以保持不变**；D-05 **不要求**为 “·” 发声问题做
  visible-copy redesign；
- accessibility grouped speech 必须使用自然 localized separator（如 “，”），**不得**依赖 “·”；
- 由此关闭审计 P2-5（grouping + separator），且不无必要地改动 D-04 视觉表面。

## 16. UNRECORDED accessibility（冻结）

- `UNRECORDED_SCHEDULE` 保持 truthfulness：

```text
schedule-side accessibility group  +  独立的 neutral “未记录摄入” node
```

- **禁止**：missed / skipped / late / overdue / failed / not taken / adherence 解释；
- **不得**合成 recorded-intake side。

## 17. UNMATCHED accessibility（冻结）

- `UNMATCHED_INTAKE` 暴露 **一个 recorded-intake accessibility group only**；
- **禁止**：schedule side、planned time、historical prescription、missed-plan phrase。

## 18. Identity states（冻结）

- 保持 D-04 identity truth：KNOWN / PARTIAL / UNAVAILABLE；
- accessibility speech 使用同一 truthful identity text（`ester_e2..en` /
  `timeline_identity_partial` / `timeline_identity_unavailable`）；
- **禁止**猜测：planName、antiandrogen identity、placeholder → real drug。

## 19. Seven UI states（冻结）

- 恰好七个 phase：LOADING / CONTENT / EMPTY_RANGE / EMPTY_DAY / INVALID_REQUEST /
  NOT_LOADABLE / ERROR；
- 每个 state 至少暴露一个 meaningful、reachable 的 accessibility target；
- recovery/action labels 保持彼此可区分：`重试`（Retry）与 `返回当前月份`（Return to current
  month）；
- 不得新增第八个 state；不得语义重解释。

## 20. Live-region decision（冻结）

- D-05 **不引入** automatic live-region announcements；
- 以下**不要求**自动 TalkBack announcement：loading 完成、month-title change、day selection、
  visual section scroll；
- 理由（冻结）：避免重复/噪音 announcement，保持用户主导的 screen-reader navigation；
- 静态 visible/state semantics 充分；**禁止** programmatic announcement API。

## 21. Accessibility focus decision（冻结）

- **禁止**程序化 accessibility-focus jump：day selection、month change、visual
  scroll-to-section、re-entry；
- D-04 visual scroll/focus 保持**视觉**行为；被激活的 day cell 依 platform 默认保持
  accessibility focus；
- **不得**为镜像视觉滚动而调用 focus API。

## 22. Interactive hit targets（冻结）

- 所有 D-05 支持的 interactive controls 保持至少 **48dp × 48dp** effective touch target；
  包含：previous month、next month、day cells、Retry、Return to current month、History
  Timeline entry card；
- **大于 48dp 允许；小于 48dp 禁止**（任何 font scale）。

## 23. Font-scale support target（冻结）

- 支持 Android fontScale：**1.0 / 1.3 / 1.5 / 2.0**；
- D-05 acceptance 必须包含 **executed evidence through fontScale = 2.0**（不接受仅分析性结论）；
- 目标为 presentation/accessibility support，**不是**每 scale 像素级相同 layout。

## 24. Large-font day-cell responsive rule（冻结）

- 现状 48dp cell width / 36dp highlight 是 **1.0x implementation baselines，不是不可变
  D-05 dimensions**；
- 大字号下 day cell 与 highlight **可以增长**；required invariants（所有支持 scale）：

```text
- effective interactive cell size >= 48dp
- date number 完全落在 highlight 内
- number 水平 + 垂直居中
- weekday / number / highlight 共享同一 center axis
- 无 clipping
- 无相邻 cell overlap
- 无 text 被 selection background 遮挡
```

- **禁止**以降低 font size（低于 system-configured scale）解决 overflow。

## 25. Uniform resolved day-cell width（冻结）

- **必须**保持 D-04 strip geometry：同一 rendered fontScale/configuration 下，strip 内所有
  day cell 使用**同一个 resolved cell width**；
- resolved width 可以大于 48dp；**禁止** single-digit/double-digit/selected/today/ordinary 使用
  不同宽度（防止 viewport-centering drift）；
- resolved width 计算必须确定（deterministic）：取 `max(48dp, 所需内容宽度 + padding)`，其中
  内容宽度覆盖该 strip 内最宽 date number（两字符预算）与 weekday label；同一 strip 内一次性
  解析（uniform），并在 strip 配置/fontScale 变化时重新解析。

## 26. Highlight responsive size（冻结）

- 1.0x 可保持既有 36dp 行为；
- 更大 font scale 下 highlight 必须 resolve 到足以容纳 date-number text 加合理 internal
  space 的尺寸；
- highlight 保持结构性居中；在当前圆形视觉处理下**宽高相等**（除非未来显式重开视觉契约）；
- **禁止** magic x/y offsets、asymmetric padding、baseline 技巧。

## 27. Viewport centering at large font（冻结）

- D-04 viewport-centering 契约在所有支持 scale 生效；
- symmetric strip content padding **必须**使用 **resolved uniform cell width**（不得假设 48dp）；
- 必须保持：selected off-screen cell 在 bounds 允许下可达 viewport 视觉中心；first/last cell
  保持对称 edge 行为；fallback 仍为 fully visible + symmetric container；
- **禁止**硬编码 x-offset 修正。

## 28. Month header large-font rule（冻结）

- 所有支持 scale：左右 navigation slots 保持对称；month title 保持几何居中；next-disabled
  slot 保持 reserved；month title 不得 clip（必要时允许 wrap）；
- **不得**因按钮可见性/用 asymmetric layout 补偿 title。

## 29. Body large-font rule（冻结）

- Timeline body 保持 left aligned；
- fontScale 至 2.0：critical row text 保持 reachable/readable；允许纵向增长与换行；
- **不得** clip/ellipsize 掉：time、identity、dose、side label、neutral state text、
  error/action text；**不得**全局居中 body。

## 30. D-04 geometry preservation（冻结）

- 1.0x：既有 D-04 geometry 行为/测试（`TimelineGeometryTest` 8 tests）必须保持 green；
- 1.3/1.5/2.0：**relational** geometry 必须保持：month title centered；nav slots symmetric；
  weekday/date/highlight common axis；selected number centered both axes；calendar centered；
  body left aligned；viewport centering + symmetric edge behavior；
- 绝对 48dp/36dp 尺寸**不要求**大于 1.0x 时相等（见 §24/§25）。

## 31. Selected / today visual state（冻结）

- **不得** redesign Timeline 视觉主题；
- accessibility 含义不得依赖颜色（selected/today 由 §5 语义 + §6 phrase 表达）；
- SHOULD：D-05 manual visual review 确认 selected / today / unselected 仍可合理区分；若存在既有
  非颜色 cue 则记录；**不得**仅为该审计 P3 引入 broad styling redesign。

## 32. Localization resource authority（冻结）

- 资源权威保持：`app/src/main/res/values/strings.xml` 与
  `app/src/main/res/values-zh-rCN/strings.xml`；
- **禁止**创建 `values-en` 或任何新 locale；
- 任何新增 Timeline accessibility string 必须同时存在于两个现有资源集合。

## 33. Timeline resource key parity（冻结）

- 执行性 parity 要求：全部 `timeline_*` keys + 任何 D-05 Timeline accessibility keys（
  `timeline_a11y_*`）在两个资源文件中**集合完全相同**；
- 不得出现因缺失 Timeline key 而 fallback；
- parity 必须由 executable guard 断言（不依赖 Android 资源编译）。

## 34. Placeholder parity（冻结）

- 对每个 Timeline-related formatted resource，强制：相同 placeholder 数量、相同类型、相同
  位置索引/顺序（`%1$s` / `%2$s` / `%1$d` / `%2$d` 序列逐项相等）；
- 必须由 deterministic executable guard 比较 placeholder 序列；
- **不得**只依赖 Android resource compilation。

## 35. Full weekday accessibility vocabulary（冻结）

- absolute dates/day cells 的 accessibility speech 使用 full weekday names：
  星期一 / 星期二 / 星期三 / 星期四 / 星期五 / 星期六 / 星期日；
- visible day strip 保留单字符 weekday labels；
- 实现使用 dedicated localized accessibility resources（§6 planned keys）或等价 deterministic
  localized formatter；
- **禁止**以 system timezone inference 参与 weekday formatting（日期本身为 display-zone
  LocalDate，天然无 zone 转换）。

## 36. Date/time authority — frozen

- D-05 必须保留：`state.displayZone` → 当前渲染 timestamp 时区；`state.today` → 当前快照
  Today/Yesterday 语义；12/24h preference → 仅 notation；`latestLogicalIntent.requestToday` →
  仅 command validation；
- **禁止** reintroduce：renderer `ZoneId.systemDefault()`、renderer `Instant.now()`、
  renderer `LocalDate.now()`（D-04 F22/F27/F28 继续为真）。

## 37. Wording freeze（冻结）

- 最终 Timeline wording 保持：factual / neutral / non-judgmental / 无 adherence scoring；
- 术语冻结在 **“方案”**（不再混用 “计划上下文”，见 §11）；
- 保留 neutral concepts：`未记录摄入`、`用药信息不完整`、`用药身份不可用`、`重试`、
  `返回当前月份`；
- **禁止**：missed / failed / late / overdue / noncompliant / adherent / non-adherent 及任何
  grading/shame 语言（除非未来显式 semantic contract 重开）。

## 38. Month control descriptions（冻结）

- 保持 resource-backed descriptions：`timeline_previous_month`（上一月）、
  `timeline_next_month`（下一月）；
- disabled next 使用 platform enabled/disabled semantics；icon glyph/name 不得单独 announce；
- **无**自动 month-change live announcement（§20）。

## 39. Accessibility / content-description matrix（冻结）

| Element | accessible node count | role | visible text vs description | heading | selected/disabled | merged-child policy |
|---|---|---|---|---|---|---|
| History Timeline entry card | 1 | `Role.Button` | visible title+subtitle + `onClickLabel`；无 custom description | — | — | 一个 merged clickable node |
| month title | 1 | — | visible text 充分 | **heading()** | — | 无子节点 |
| previous month | 1 | button（IconButton） | `timeline_previous_month` description | — | — | icon description 随按钮 merge |
| next month | 1 | button（IconButton） | `timeline_next_month` description | — | disabled semantics（当前月） | 同上 |
| day cell | **1** | **`Role.Button`** | resource-backed localized phrase（§6，非 ISO） | — | `selected` property；defensive branch disabled semantics | 视觉 children 不成为 stops、不重复发声；unmerged tags 保留 |
| day visual children（weekday/highlight/number） | 0 | — | 不独立发声 | — | — | 从 accessibility tree 排除（技术自由） |
| loading | 1（region text）+ progress | — | visible text 充分 | — | — | 无 |
| empty range | 1 | — | visible text 充分 | — | — | 无 |
| empty day | 1 | — | visible text 充分 | — | — | 无 |
| invalid request | 1 + 1 action | — | visible text + button label | — | — | 无 |
| not loadable | 1 + 1 action | — | visible text + button label | — | — | 无 |
| error | 1 + 1 action | — | visible text + button label | — | — | 无 |
| Retry | 1 | button | `timeline_retry` | — | — | — |
| Return to current month | 1 | button | `timeline_return_current_month` | — | — | — |
| section header | 1 | — | Relative：visible=spoken；Absolute：visible 短星期 + full-weekday description（§9） | **heading()** | — | 无子节点 |
| MATCHED schedule side | **1** | — | resource-backed composed phrase（§13；label→time→identity→dose） | — | — | children 不重复发声 |
| MATCHED recorded side | **1** | — | resource-backed composed phrase（§14） | — | — | children 不重复发声 |
| UNRECORDED notice | 1（+ schedule side group） | — | neutral `未记录摄入` | — | — | 无 |
| UNMATCHED recorded side | **1** | — | resource-backed composed phrase（§14） | — | — | children 不重复发声 |
| decorative icons / dividers | 0 | — | `contentDescription = null` / 无 semantics | — | — | — |

## 40. D-05 acceptance matrix（冻结 ID）

### Accessibility（A11Y）

| # | requirement |
|---|---|
| A11Y1 | entry card 是**一个** `Role.Button` node（title+subtitle+onClickLabel 可读，icon 静默） |
| A11Y2 | month title 暴露 `heading()` |
| A11Y3 | 每个 section header 暴露 `heading()` |
| A11Y4 | day cell 在 accessibility/merged tree 中**恰好一个** meaningful node |
| A11Y5 | day cell 使用 `Role.Button` |
| A11Y6 | day cell 暴露 `selected` semantic property（选中/未选中正确） |
| A11Y7 | defensive 不可选分支暴露 disabled semantics（适用时） |
| A11Y8 | weekday/date 视觉 children 不产生额外 stop、不重复发声 |
| A11Y9 | day-cell spoken 语法为 localized、非 ISO（§6 grammar） |
| A11Y10 | spoken weekday 为 full weekday（星期X），非单字符 |
| A11Y11 | MATCHED 恰好两个 side groups（schedule / recorded，彼此分离） |
| A11Y12 | schedule group 仅用 schedule 字段且 spoken order = label→time→identity→dose |
| A11Y13 | recorded group 仅用 recorded 字段且 spoken order = label→time→identity→dose |
| A11Y14 | UNRECORDED 保持 neutral（schedule group + 未记录摄入；无 forbidden wording） |
| A11Y15 | UNMATCHED 仅 recorded group（无 schedule/planned/prescription 文本） |
| A11Y16 | decorative elements 静默（icon null / divider 无 semantics） |
| A11Y17 | 七状态各有 meaningful reachable target |
| A11Y18 | recovery labels 彼此可区分（重试 vs 返回当前月份） |
| A11Y19 | 无 live-region 自动 announcement |
| A11Y20 | 无程序化 a11y-focus jump（selection/month/scroll/re-entry） |
| A11Y21 | interactive targets ≥ 48dp × 48dp（所有支持 font scale） |

### Font scale（FONT）

| # | requirement |
|---|---|
| FONT1 | 1.0x：既有 D-04 geometry 断言全部保持 green |
| FONT2 | 1.3x：day-cell 内容完整（number 在 highlight 内、无 clip/overlap） |
| FONT3 | 1.5x：同上 |
| FONT4 | 2.0x：同上（**executed evidence 必须覆盖 2.0x**） |
| FONT5 | 每 scale 内 strip 使用 uniform resolved cell width |
| FONT6 | number 在 grown highlight 内双轴居中 |
| FONT7 | weekday / number / highlight 共享 center axis（所有 scale） |
| FONT8 | 2.0x：month title 居中、nav slots 对称、disabled-next slot 仍保留 |
| FONT9 | 2.0x：body left aligned，critical text 无 clip/ellipsize |
| FONT10 | viewport centering 使用 resolved cell width（selected cell 达视觉中心；first/last 对称） |
| FONT11 | 大字号 first/last edge symmetry 保持（无 manual offset） |

### Localization（LOC）

| # | requirement |
|---|---|
| LOC1 | 仅使用两个现资源权威（无新 locale / 无 `values-en`） |
| LOC2 | Timeline keys（含 D-05 新 a11y keys）两文件集合完全相同（executable guard） |
| LOC3 | placeholder 数量/类型/顺序逐项一致（executable guard） |
| LOC4 | 无 user-visible hardcoded literal（D-05 touched sources CJK 扫描） |
| LOC5 | entry 术语使用 “方案”（entry subtitle 已更新） |
| LOC6 | accessibility weekday 使用 full weekday 词汇 |
| LOC7 | explicit-zone formatting 保留（renderer 无 systemDefault / live clock，F22/F27/F28） |
| LOC8 | 12/24h 仅改 notation（zone interpretation 不变） |
| LOC9 | forbidden/adherence 语言缺席（含新 a11y 文案） |

## 41. D-05 contract guards（冻结，违反 ⇒ STOP）

| # | forbidden |
|---|---|
| DG1 | 直接修改 D-01/D-03 语义 |
| DG2 | row-family 改变 |
| DG3 | truthfulness 改变（flatten / cross-substitute / 合成缺失侧） |
| DG4 | 新增 Timeline state |
| DG5 | navigation redesign（route/tab/参数） |
| DG6 | 新 Timeline persistence |
| DG7 | PK/schema/TOS/truth-store coupling |
| DG8 | renderer `ZoneId.systemDefault()` / live clock |
| DG9 | programmatic a11y-focus jump |
| DG10 | automatic live-region announcement |
| DG11 | day-cell spoken ISO machine date |
| DG12 | day-cell child 重复 announcement / 额外 stop |
| DG13 | MATCHED 两侧合并为一个 node |
| DG14 | sub-48dp interactive targets |
| DG15 | >1.0x 时因文本裁剪而硬编码 48/36 几何 |
| DG16 | per-cell variable width 导致 centering drift |
| DG17 | 新 locale 创建 |
| DG18 | resource key mismatch |
| DG19 | placeholder mismatch |
| DG20 | hardcoded user-visible D-05 strings |
| DG21 | adherence/grading vocabulary（任何新旧文案） |
| DG22 | 仅 source-scan 而无 semantics 断言（A11Y 树行为必须有真实 Compose semantics 断言） |
| DG23 | 以低于 system font scale 的字号解决 overflow |

## 42. Test / evidence gate（冻结）

### 42.1 Semantics instrumentation（必须 executed）

- 真实 Compose semantics assertions 覆盖：roles、headings、selected、disabled、merged node
  count / 无重复 children、side grouping、action labels；
- **不接受** only source-scan proof 的 accessibility tree 行为（A11Y* 需 device 断言）。

### 42.2 Font-scale gate（必须 executed）

- fontScale coverage：1.0 / 1.3 / 1.5 / 2.0；device：Pixel_7 AVD API 35（或项目既有等价物）；
- 机制：`DeviceConfigurationOverride.FontScale`（Compose UI test 公共 API）；若 resolved
  dependency 不提供该 API，允许等价的确定性 configuration override 机制；
- 大字号必须 measured 断言：date-number bounds inside highlight；weekday/date/highlight axis；
  uniform day-cell width；month-title center；nav-slot symmetry；body left alignment；critical
  text 无 clip；selected-cell viewport center；first/last edge symmetry。

### 42.3 12/24h font-scale sanity（禁止 full cross-product）

- 至少一条 large-font sanity path 证明 12h 与 24h 均保持：explicit published
  `state.displayZone`、readable timestamp、无 clip。

### 42.4 Localization JVM guards

- 扩展既有 D-04 guard：Timeline key parity、placeholder sequence parity、
  hardcoded user-visible literal scan、forbidden wording scan（含新 a11y 文案）；复用既有
  test framework，不新发明框架。

### 42.5 Regression（必须 fresh rerun）

- 既有 D-04 geometry tests、navigation tests、lifecycle tests（accessibility/font-scale 改动可能
  影响 Compose layout/semantics）；
- focused D-05 JVM；fresh full JVM；D-05 instrumentation（bounded matrix）；final counts 一律
  取自 final JUnit XML。

### 42.6 Manual / visual spot check（bounded）

- visual evidence 最小集：normal-font CONTENT、2.0x CONTENT、2.0x EMPTY_DAY（或等价 neutral
  state）、2.0x ERROR/recovery；
- screenshots/PNGs 为 measured assertions 的补充；不要求每 state/font 组合截图；
- SHOULD：一次 recorded manual spot check 覆盖 selected / today / ordinary / defensive
  disabled、schedule vs recorded side cues；**不得**声称未实测的 WCAG ratio；**不得**无实际
  blocking defect 时 redesign theme。

### 42.7 Matrix size 限制

- 新增 D-05 instrumentation 目标约 **12–16 methods**；禁止 7 states × 4 fonts × 2 time
  formats 的组合爆炸。

### 42.8 D-04 test impact policy

- `TimelineGeometryTest` / `TimelineNavigationTest` / `TimelineLifecycleTest`：**保持不变且必须
  green**；
- `TimelineScreenTest`：behavioral intent 必须保留；仅当 frozen D-05 semantics 替代 merged-tree
  文本暴露时，允许调整 assertion mechanics（visible text 仍渲染；可用 unmerged tree 查询；
  day-cell 的 “已选中” 文本断言改为 selected semantic property 断言）；不得弱化/删除行为断言。

## 43. Production boundary（规划值）

Likely 修改（仅 presentation/a11y/localization）：

```text
ui/screens/timeline/TimelineScreen.kt          （semantics/heading/role/短语组合/responsive 尺寸）
ui/screens/timeline/TimelinePresentation.kt    （如需 a11y date/weekday presentation helper）
ui/screens/HistoryScreen.kt                    （entry card Role.Button）
res/values/strings.xml + values-zh-rCN/strings.xml（entry subtitle 更新 + timeline_a11y_* keys）
```

Likely 新增测试：D-05 accessibility semantics 测试类、font-scale 测试类（或同类的
group）；扩展 `TimelineUiArchitectureGuardTest`。Exact 拆分/命名 remain planning values；
production 不得超出上述范围（扩展需在实现评审中说明理由）。

## 44. Existing D-04 P3 hygiene（冻结为 out of required scope）

- F14 guard-label drift、未使用的 `assertCenteredBothAxes` helper、`TimelineNavigationTest`
  KDoc UI31–UI33 drift：**不加入** D-05 required scope；仅当 D-05 自然触碰同一文件/符号时才可
  顺带清理；不得为它们制造额外 churn。

## 45. Contract self-review（P0/P1/P2 closure record）

- P0：none；
- P1：none；
- P2 closure：
  1. day-cell announcement/state semantics → §3/§4/§5/§6（单节点 + Role.Button +
     selected/disabled 属性 + localized 非 ISO 语法，均冻结且可测）；
  2. heading semantics → §8/§9（month title + section headers，均冻结）；
  3. entry-card role → §10（Role.Button，冻结）；
  4. font-scale → §23/§24/§25/§26/§27/§28/§29 + §42.2（支持集、responsive 规则、executed
     evidence，冻结）；
  5. MATCHED grouping/separator → §12/§13/§14/§15（双侧分离 + natural separator + spoken
     order，冻结）。
- 契约文本对五项 P2 无 TBD / decide later / maybe / possibly / choose one 类未决措辞。

## 46. Documentation status

- 本契约状态：`CONTRACT — APPROVED / FROZEN`；**PRODUCTION — IMPLEMENTED / APPROVED / CLOSED**；
  **EVIDENCE — COMPLETE**（Architect APPROVED；独立复审 APPROVED，P0/P1/P2 = none；
  closure 记录见 §47；final production closure 见 §48）；
- final implementation HEAD：`2a79f1d048905113f53d4be470071a94072c2f96`；
- D-06 recurring verification gate；D-07 final Phase-D independent gate；
- D-01/D-03/D-04 保持 CLOSED / FROZEN；指针更新仅限 `TODO.MD` / `CURRENT_STATUS.md` /
  `ROADMAP.md` / `V17_PLAN.md` 的最小状态行；**NEXT: V17-D-06 recurring verification gate**。

---

## 47. Final contract closure（bookkeeping）

- Approved semantic contract HEAD：`b1cdd662fe1bee14beab5cad651aef772f211c68`；
- Architect verdict：**APPROVE V17-D05 CONTRACT — ARCHITECT CLOSURE**；
- Independent verdict：**APPROVE V17-D05 CONTRACT**（independent reviewer：Qwen3.8 Flash，
  fresh independent read-only session；reviewed final contract HEAD 同上）；
- Final blocking findings：P0 = none，P1 = none，P2 = none；
- 本契约进入 `CONTRACT — APPROVED / FROZEN`；**No further semantic D-05 contract change without
  explicit reopening**；
- 本 closure 为 bookkeeping + 两项 factual §0 anchor 修正：**不新增、不改变任何 D-05 语义要求**
  （§3–§44 规范行为、A11Y1–A11Y21、FONT1–FONT11、LOC1–LOC9、DG1–DG23、day-cell 规则、heading
  规则、MATCHED grouping、font-scale target、uniform resolved cell width、localization/date-time
  authority、live-region/focus 决策、D-04 frozen boundary 全部原样保留）；
- Production implementation 仅授权针对上述 frozen semantic contract HEAD 进行。

### 47.1 P3 dispositions（closure 记录）

- **P3-1（global resource count source-fact）**：修正 §0 的资源事实——整文件集合为 values/ 540
  unique keys vs values-zh-rCN/ 532，其中 8 个 PRE-EXISTING default-only NON-TIMELINE keys 属历史
  遗留、**不在 D-05 范围**；Timeline keys 22/22 parity；D-05 parity 义务仅限
  `timeline_*` + `timeline_a11y_*`。**不**扩张为整应用 localization cleanup；
- **P3-2（stale line anchors）**：§0 中 `DAY_CELL_WIDTH` / `DAY_HIGHLIGHT_SIZE` /
  `MONTH_CONTROL_SLOT` 的源码行锚由 `TimelineScreen.kt:46–48` 修正为
  `TimelineScreen.kt:70–72`（independent verified）；几何规则本身不变；
- **P3-3（section-header single-spoken-phrase auditability）**：absolute-date section header 的
  “one meaningful spoken phrase / 不重复播报 visible 短星期 + full-weekday description” 结果已由
  §9 + §39 + §42.1 冻结，但无独立 acceptance/guard ID；**NON-BLOCKING**，且 independent
  approval 之后 **不新增/不重编号** 任何 A11Y/FONT/LOC/DG ID。改为记录：implementation review
  必须显式验证 absolute-date section header 只产生一个 meaningful spoken phrase、不
  double-announce。

### 47.2 Frozen acceptance / guard ranges

- Acceptance：**A11Y1–A11Y21**、**FONT1–FONT11**、**LOC1–LOC9**（未重编号、未增删）；
- Guards：**DG1–DG23**（未重编号、未增删）。

### 47.3 Implementation authorization

- D-05 production implementation authorized ONLY against the frozen semantic contract reviewed
  at `b1cdd662fe1bee14beab5cad651aef772f211c68`；
- 本 closure commit 为 bookkeeping/factual-anchor correction only，不新增语义要求；
- **NEXT: V17-D-05 production implementation**。

---

## 48. Final production closure（bookkeeping；CLOSED / FROZEN）

- Final implementation HEAD：`2a79f1d048905113f53d4be470071a94072c2f96`
  （candidate implementation commit，parent `c298bac`；relative to the frozen contract it adds
  no semantic requirement）；
- Architect implementation review：**APPROVE V17-D05 IMPLEMENTATION — ARCHITECT CANDIDATE
  CLOSURE**；
- Independent implementation review：**APPROVE V17-D05 IMPLEMENTATION**（Qwen3.8 Flash，fresh
  independent read-only session）；
- Final blocking findings：P0 = none，P1 = none，P2 = none；
- Final validation：focused D-05 JVM 69/0/0/0（TimelineViewModelTest 23 / TimelinePresentationTest
  18 / TimelineUiArchitectureGuardTest 28）；fresh full JVM 1490/0/0/0（app 1229 +
  experience-core 171 + wear 90）；Android instrumentation 59/0/0/0 on Pixel_7 AVD API 35
  （TimelineScreenTest 23 / TimelineGeometryTest 8 / TimelineLifecycleTest 4 /
  TimelineNavigationTest 3 / TimelineVisualEvidenceTest **2** / TimelineAccessibilityTest 13 /
  TimelineFontScaleTest 6）；
- Evidence：`docs/evolune/v1.7/evidence/d-05/` — 187 files / 186 manifest-listed data files，
  coverage 186/186，sha256(MANIFEST.sha256) =
  `3d2f2da573ba07553b7372029cf485550731014a61ac09a7bf7a86368e774044`，187/187 HEAD-blob
  verification，0 mismatches，UTF-8 clean（无 UTF-16 残留；implementation.diff 与真实 git diff
  逐字节一致）；
- Accessibility tree closure（executed）：day cell merged tree 恰一个 meaningful node +
  Role.Button + `selected` property + controlled defensive `disabled`；weekday/date children 不
  构成重复 stop；unmerged geometry tags（weekday/highlight/number/cell）保持可查询。合规结论
  不得仅凭 `mergeDescendants` / `clearAndSetSemantics` 等 source keyword；
- Section-header P3-3 duty CLOSED（executed）：absolute-date header 有 heading semantic、单一
  node、exact spoken phrase（localized date + full weekday）、该 node 无 Text property、visible
  short-weekday 短语缺席 merged tree ⇒ 无 duplicate short+full 播报；
- MATCHED closure（executed）：accessibility tree 恰含 1 schedule-side + 1 recorded-side；
  spoken order 分别为 label→scheduled time→identity→dose 与 label→actual time→identity→dose；
  distinct test values 证明无字段交叉替换；visible “·” 仅视觉，speech 使用 natural localized
  punctuation；
- Font-scale closure（executed）：1.0/1.3/1.5/2.0 measured（number contained & centered both
  axes、weekday/date/highlight common axis、uniform day-cell width、highlight growth、>=48dp
  cell、month title centered、nav slots symmetric、body left aligned、viewport centering、
  first/last edge）；无 font-size cheating；48dp/36dp 为 minimum/baseline，不是不可变 >1.0x 尺寸；
- Touch-target closure：effective touch target >= 48dp × 48dp（previous/next month、day cells、
  Retry、Return to current month、History entry card），证据为 actual Compose semantics node
  的 `touchBoundsInRoot`（非 visual icon bounds、非 developer-computed constants）；
- Localization closure：Timeline families 34/34（22 existing + 12 new `timeline_a11y_*`）across
  values/ + values-zh-rCN/；无 values-en / 新 locale；placeholder sequence parity verified；
  whole-application resource parity remains OUT OF SCOPE（inherited values 540 / zh-rCN 532
  global fact 不作为 global parity 声明）；
- D-04 regression closure：TimelineGeometryTest 8/8、TimelineLifecycleTest 4/4、
  TimelineNavigationTest 3/3、TimelineScreenTest 23/23 全 green；Geometry/Lifecycle source 未改；
  Screen/Navigation 仅 semantics-assertion mechanics 适配，行为未弱化；
- Frozen surfaces：zero unauthorized semantic change（TimelineViewModel、TimelineRangeCoordinator、
  TimelineRangeState、TimelineReadModel、TimelineProjectionBuilder、TimelineSurfaceLifecycle、
  HistoryRangeSource/HistoryReadService/HistoricalProjectionBuilder、D-01/D-03/D-04 semantics、
  MainActivity、AppNavigation、Phase C/PK、Room/schema/DAO、Home/Wear/Widget、
  Gradle/dependencies；Timeline command semantics、row facts/order、seven phases 保持）；
- **D-05 — CLOSED / FROZEN**；Phase D remains IN PROGRESS；**NEXT: V17-D-06 recurring
  verification gate**（D-07 final Phase-D independent gate remains required）。

### 48.1 Implementation P3 dispositions（CLOSED / 非阻塞，不重开 D-05）

- **P3-1（FONT9 evidence strength）**：critical-text no-clipping proof 采用 measured layout
  bounds + non-degenerate side layout + 2.0x captured-device visual evidence 的有界组合，而非
  per-element TextLayout clipping queries；independent review 判定对 D-05 充分；不重开 D-05，
  D-06 可在 recurring verification 中加强；
- **P3-2（zh device-locale expectations）**：TimelineAccessibilityTest 对部分 exact spoken
  phrases 使用固定 zh expectations；对既定的 zh Pixel_7 API35 D-05 目标有效；未来更广 locale/
  device matrix 可改用 resource-derived expectations；non-blocking，不重开 D-05；
- **P3-3（review packet Android breakdown typo）**：implementation review packet 文本曾写
  TimelineVisualEvidenceTest = 1，最终 source/XML 独立证明为 **2**；packet 的 total 59 正确，
  仅 breakdown 行为笔误；closure record 采用经 XML 核验的正确 breakdown；无 repository behavior/
  evidence 缺陷。
