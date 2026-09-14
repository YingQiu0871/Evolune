# Evolune v1.7 — B-03 Insights UI（Range Selector, Summary Cards, Charts & Accessibility）

**轮次**：v1.7-B-03（Insights UI）
**起点**：`56f32cd580600e40347b97ecff7f18364998b100`（B-02-R1 APPROVE）
**冻结基线**：
- B-00 semantics（`V17_B_00_INSIGHTS_SEMANTICS.md`，APPROVE；措辞/披露/禁用词冻结）
- B-01 aggregator（`822de00…`，APPROVE；`MedicationInsightsSummary` 是唯一事实来源）
- B-02 orchestration（`56f32cd…`，APPROVE；`InsightsUiState`/`InsightsViewModel`/`InsightsSurfaceLifecycle`）
**范围**：新增用户可见 Insights 页面（History → Insights），**不**新增 bottom tab，**不**新增指标语义，**不**开始 B-04。

---

## 1. Entry point（History → Insights）

- 入口是 **History 内容顶部的可见入口卡片**（`history-insights-entry`，`Card` + 标题/副标题 + chevron），点击进入 `insights` destination；
- **不**新增第六个 bottom tab；**不**藏在 Settings；**不**使用长按/overflow-only；
- 现有导航架构（`Screen` enum 五个主项 + 顶层 sub-route const + 同一 NavHost）**未重构**，Insights 以既有 sub-route 形态加入 → 无需 `ENTRY-POINT DECISION REQUIRED`。

## 2. Navigation / route ownership

| 项 | 实现 |
|---|---|
| route | `private const val INSIGHTS_ROUTE = "insights"`（与 `settings_*` / `sync_and_backup` 等同类） |
| destination | `composable(INSIGHTS_ROUTE) { … InsightsRoute(viewModel, showTopBar = false) }` |
| back | 复用既有 sub-route back 语义：`isSettingsSubroute || INSIGHTS_ROUTE` → 隐藏 bottom bar/rail + 顶栏返回箭头 `popBackStack()`，**Insights → History** 走既有 back stack |
| 顶栏标题 | 复用共享 `AppTopBar` 的 `titleOverride`（`R.string.insights_title`） |
| ViewModel 归属 | **Activity 作用域、按需创建**：`viewModel(viewModelStoreOwner = activity ?: entry, factory = insightsViewModelFactory)`。首次进入才创建（不在 app 启动时多读一次历史），离开再进入复用同一实例 → B-02 的 surface-return refresh 在真实产品路径生效 |
| 唯一事实链 | `InsightsRoute → InsightsViewModel → HistoryRangeSource → HistoricalRange → B-01 aggregator → InsightsUiState → InsightsPresentation → Compose`；UI 不接触 DAO/Room/repository/HistoryReadService/aggregator |

## 3. Screen architecture

`InsightsRoute(viewModel)`（唯一有状态层：collect state + 托管 `InsightsSurfaceLifecycle` + 转发 intents）
→ `InsightsScreenContent(state, onSelectRange, onRetry)`（**纯渲染**，Preview 与 UI 测试都驱动它）
→ `InsightsPresentation.present(state)`（**纯 JVM 决策层**：选段、排序、文案资源 ID、可见性策略）

页面自上而下：Top app bar（共享）→ Range selector → range heading → timezone disclosure → Overview cards → Coverage（含强制披露）→ Sources → Confidence → Dose totals（含 unknown identity）。

**可在没有图表的情况下完整理解**：本轮**不 ship 图表**（见 §5）。

## 4. Range selector

- 五个选项直接绑定 `InsightsRangeSelection`：`Last 7 days` / `Last 30 days` / `Last 90 days` / `This month` / `Custom`；
- 默认 `Last 30 days`（B-02 `DEFAULT`）；当前选择通过 `FilterChip.selected` 暴露（semantics 可被 TalkBack 读出，也可被 UI 测试断言）；
- 横向可滚动 `Row`（窄屏/大字不溢出，不强行单行塞满）；
- UI **不做**任何 `today.minusDays(...)`；点击只调用 `viewModel.selectRange(...)`。

## 5. Charts / activity visualization（**本轮不 ship 图表**）

- **审计结论**：B-01 `MedicationInsightsSummary` 只暴露聚合计数与映射（recorded/recordedDay/matched/unrecorded/unmatched、sourceCounts、bindingConfidenceCounts、perMedicationDoseTotalsMg、unknownIdentity、timezone flag）——**没有逐日 series**；
- 依据任务书 §13/§14：不允许 Compose 从 `HistoricalRange` 自行 `groupBy`/求和来"造"series；因此本轮交付**无图表的 factual UI**（任务书允许的选项之一），**未**触发 `CHART DATA CONTRACT DECISION REQUIRED`；
- 未引入任何第三方 chart 依赖（任务书 §39）→ 无需 `CHART DEPENDENCY DECISION REQUIRED`；
- Sources/Confidence 采用 **label + count 列表**（任务书 §15 允许的 "list-with-counts"）：可读性/无障碍优于 pie，且不产生任何比例型视觉；
- 逐日 series（若要可视化）登记为 B-04 handoff 项：必须先成为 B-01 domain output 或明确的 presentation DTO（在 Compose 之外派生）。

## 6. Custom range

- `Custom` chip → Material 3 `DatePickerDialog` + `DateRangePicker`（沿用工程既有 date-picker 用法，不新增依赖）；
- 确认后只把 `Custom(startDate, endDate)` 交给 B-02：**UI 不判断合法性**；
- 当 `phase = INVALID_RANGE`：保留 range controls，按 typed `validationError` 显示中性文案（`START_AFTER_END` / `END_IN_FUTURE`，未知则 generic），不显示旧 summary、不读历史、不 crash。

## 7. Metrics & sections（全部来自 B-01，UI 0 计算）

| 区块 | 数据来源 | 说明 |
|---|---|---|
| Overview | `recordedIntakeCount`、`recordedDayCount` | 两张计数卡（无百分比、无 progress） |
| Coverage | `matchedOccurrenceCount`、`unrecordedOccurrenceCount` | 标题 = 冻结副标题「与生成的计划时点关联的记录」；**必须**同时显示两句披露 |
| Sources | `sourceCounts` | 六类各占一行（Manual/Reminder/Wear/Widget/JSON v1/Legacy），**不合并 Legacy 与 Manual**，0 值行保留（无障碍与总数逻辑不因隐藏而重算） |
| Confidence | `bindingConfidenceCounts` | 四档 High/Medium/Low/None；标题为「记录关联方式」并提供说明：置信度是关联程度，不代表用药可靠性/依从性 |
| Dose | `perMedicationDoseTotalsMg` | 仅已知身份；每药一行 name + mg（复用 `HistoryFormatting.dose`），**无跨药物总量** |
| Unknown identity | `unknownIdentityRecordedIntakeCount` | 仅计数文案「未知药物 · N 条记录」；**绝不**把未知身份混成剂量桶 |

## 8. Mandatory disclosure & forbidden wording

- Coverage 区块固定显示（资源化、中英策略一致的 zh 文案）：
  1. `计划时点由当前方案上下文生成。`
  2. `没有记录并不能证明当时没有服药。`
- timezone：`containsCurrentTimezoneDerivedDates == true` → 中性说明 `部分旧记录的日期按当前时区显示。`（info 语义，非 warning/error）；
- 禁用：`Missed`/`Skipped`/`Failed`/`Late`/`On time`/`adherence`/`compliance`/百分比；中文 `漏服`/`依从率`/`准时率`/`延迟`；
- 无任何 percentage/progress/ring：loading 仅用 `CircularProgressIndicator`（唯一允许的指示器）。

## 9. States

| phase | 行为 |
|---|---|
| `LOADING` | 保留 top bar + range selector；内容区 loading；**不**显示旧 summary（`summary = null` 被尊重） |
| `CONTENT` | Overview/Coverage/Sources/Confidence/Dose 全部渲染 |
| `CONTENT` 且 `recordedIntakeCount = 0`、`unrecordedOccurrenceCount > 0` | **仍是 Content**（unrecorded-only 是真实历史事实）：卡片显示 0、Coverage 显示 unlinked 计数与披露，**绝不**显示 Empty 文案（B-02 P1 的 UI 防回归） |
| `EMPTY` | 文案「本区间没有用药历史记录。」；无卡片/区块 |
| `ERROR` | 通用错误文案 + Retry；不显示 throwable/message/stacktrace/SQL；ReadFailure 与 ContractViolation 用户文案统一 |
| `INVALID_RANGE` | 保留 controls，显示 typed 中性文案；无旧 summary |

## 10. Accessibility

- 每张卡片、每个来源/置信度行都是**真实文本**（count 是 `Text`，不是几何/颜色）；
- 每行同时以 `contentDescription = "<label>：<count>"`（资源 `insights_bar_description`）暴露同一事实，TalkBack 获得与视觉等价的信息；
- 不依赖颜色/图标/图表形状传达信息；bar/geometry 型可视化本轮不存在；
- range chips 通过 `selected` semantics 暴露当前选择；custom 入口有可点击 label（`insights_entry_action`/chip 文案）。

## 11. Responsive / large font / theme / localization

- 单一 LazyColumn 单列流式布局（复用现有设计语言；未新建双栏架构）；
- 卡片不设固定高度、value 用 `headlineMedium`、label 可换行；`fontScale = 1.5f` 有 Preview + UI 测试；
- 全部颜色取自 Material theme tokens（无硬编码颜色）；confidence 不用 green/red 语义；
- 所有用户可见文案进 `res/values/strings.xml` + `res/values-zh-rCN/strings.xml`（保持工程现有语言策略），composable 内**无**硬编码文案（Preview 的 sample data 除外）。

## 12. Previews

独立文件 `InsightsPreviews.kt`（不依赖 repository/ViewModel），覆盖：content / unrecorded-only / timezone disclosure / unknown medication / loading / empty / error / invalid range / large font(1.5x)。

## 13. Lifecycle hosting（首次真实托管）

`InsightsRoute` 内直接调用 `InsightsSurfaceLifecycle(viewModel)`，且只在该 destination 的 composition 内存在：
- 首次 composition → `onSurfaceShown()` 只有 gate（0 read，冷启动唯一 read 属于 init load）；
- 离开 → 重新进入（同一 Activity 作用域实例）→ 1 次 refresh；
- 真实 `ON_STOP → ON_START` → 1 次 refresh；
- observer 随 composition dispose，用户不在 Insights 时无后台轮询。

## 14. Guards

- `InsightsUiGuardTest`（JVM，扫描 UI 源码 + 两个 strings.xml）：禁止重算原语（`.groupBy(`/`.sumOf(`/`.mapValues(`/HistoryRangeSource/HistoryReadService/AppDatabase/IdentityClassifier/OccurrenceMatcher/OccurrenceGenerator）→ 任务书 §38；禁止禁用词（英/中）→ §37；断言 B-01 字段只由 `InsightsPresentation` 直读、渲染文件不得出现 `summary.`；断言冻结披露与副标题逐字 ship。
- B-02 既有护栏（orchestration 包）保持不变。

## 15. 崩溃修复（B-03 发现，非语义变更）

**现象**：设备上进入 Insights 抛 `NullPointerException: Parameter specified as non-null is null … InsightsUiState.<init>, parameter selection`。

**根因（字节码证据）**：`InsightsRangeSelection` 是 **接口**且声明 default method `isRelativeToToday`；JVM 在初始化任何 implementor 之前必须先初始化该接口（JVMS §5.5）。当 `Last30Days`（`data object`）先被触碰时：`Last30Days.<clinit>` → 先初始化接口 → 接口 `<clinit>` 创建 `Companion` → Companion `<clinit>` 执行 `DEFAULT = Last30Days.INSTANCE` → 此刻 `Last30Days` 仍在初始化中，读到的 `INSTANCE` 为 **null** → `DEFAULT` 被永久写成 null（另一顺序则正常）→ 视进程内类加载顺序而定，`InsightsUiState(selection = null)` 崩溃。B-03 新增类改变了 dex 布局后该顺序风险被稳定触发。

**修复（1 行 + KDoc，语义不变）**：`val DEFAULT: InsightsRangeSelection get() = Last30Days` —— 计算属性让 Companion 初始化不再读取对象实例，两个顺序都安全（读时对象必然已初始化）。

**回归锁定**：`InsightsRangeSelectionInitTest`（JVM）用**全新 classloader** 强制 "object-first" 顺序（进程内已热无法复现）：修复前 red（`DEFAULT must never be null`），修复后 green 且 `DEFAULT === Last30Days`。

**边界**：这是 B-02 冻结文件里的**崩溃修复**，不改任何语义/端点/校验/状态契约；已在 closure report 中显式申报。

## 16. Tests

| 层 | 文件 | 覆盖 |
|---|---|---|
| JVM | `InsightsPresentationTest`（13） | 两张 overview 卡、coverage 两计数+两句披露、真正 EMPTY vs unrecorded-only CONTENT、六来源顺序与 Legacy 独立、四档 confidence、剂量行确定性排序与不合并、unknown identity、timezone 披露、loading/error/invalid 无旧 summary、range heading 只格式化已解析端点、五 preset 文案 |
| JVM | `InsightsUiGuardTest`（6） | 无重算原语、summary 字段只由 mapper 直读、禁用词（英/中）、披露与副标题逐字 ship、unrecorded 不得被冠以 blame 词 |
| JVM | `InsightsRangeSelectionInitTest`（1） | DEFAULT 在 object-first 初始化顺序下非 null（崩溃回归） |
| instrumentation | `InsightsScreenTest`（14） | 全部区块渲染、六来源/四档行存在、contentDescription=label:count、unrecorded-only 非 Empty、true empty、loading、error+retry、invalid typed、timezone 披露、unknown identity 仅计数、五选项与默认 Last30、真实 ViewModel 驱动 Last7（+1 read、state/summary 更新）、fontScale 1.5 冒烟、禁用词不出现 |
| instrumentation | `InsightsNavigationTest`（2） | 真实产品路径 History→Insights→back→History；离开再进入不崩溃且 bottom bar 在 Insights 内隐藏 |

## 17. Evidence

`docs/evolune/v1.7/evidence/b-03/`：source/diff boundary、nav integration、UI semantics snapshot、screen hierarchy snapshot、range selector 证据、unrecorded-only 回归、accessibility、localization/forbidden-language audit、no-metric-recomputation audit、previews inventory、crash-fix root cause（init-order）、focused JVM XML/日志、targeted Phone XML/日志、full JVM/Phone、assemble 日志、`MANIFEST.sha256`。旧 evidence（b-02、b-02-r1）**0 改动**。

**最终计数**：focused JVM **86**（B-02 既有 66 + `InsightsPresentationTest` 13 + `InsightsUiGuardTest` 6 + `InsightsRangeSelectionInitTest` 1）；fresh full JVM **1142**（app 883 / experience-core 169 / wear 90，0/0/0，54/54 executed）；targeted Android **20/0/0**（B-02 两个设备测试 + B-03 两个设备测试）；full Phone **263 / 5 skipped（既有 assumeTrue）/ 0 failed**；`:app:assembleDebug` `BUILD SUCCESSFUL` 但 38/38 UP-TO-DATE → 只称 **build-green**（不声称 fresh APK 身份）。manifest **158 条目 / coverage 158/158 / `sha256sum -c` 159 OK / 0 FAILED**（self hash 由 MANIFEST 自校验，数值见 closure report），提交后 HEAD blob 0 mismatch。

## 18. B-04 handoff

- 逐日 series 可视化（需 B-01 domain output 或 Compose 外的 presentation DTO）与 chart 依赖决策；
- 可选视觉增强（来源分布形状）——本轮以可读计数列表交付；
- 未批准指标（timing / percentage）仍不得进入；
- B-02 遗留 P3（invalid-supersede dedicated test、new-refresh summary discriminator、custom +1 断言、assemble fresh/APK hash、provider self-heal）顺延，不因 B-03 重开 B-02。
