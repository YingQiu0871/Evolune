# 当前 UI 结构速览

适用基线：v1.9.0；2026-09-29 文档核对（v1.7 起新增历史/时间线/洞察/回顾性 PK 界面，v1.7.2 起设置页扁平化）。完整入口见 [快速开始](QUICK_START_GUIDE.md)。

本文说明当前生产 UI 的导航和主要组件。Phone 主界面使用 Jetpack Compose；桌面
Widget 仍是 Android `RemoteViews`，不是 Compose/Glance 页面；Wear Tile 是独立的
Wear surface。

## 顶层导航

`AppNavigation` 维护五个顶层目的地（`Screen.kt`）：

- **主页 Home**：当前 E2 浓度、历史/预测曲线和今日摘要；
- **记录 Records**：浏览、添加、编辑和删除 `DoseEvent`；
- **历史 History**：按日历日查看用药活动，区分计划时点与实际摄入；日历与当天事实记录在前，用药洞察、回顾性 PK、用药时间线三个次级入口统一在页面底部；
- **方案 Medication Plans**：管理 `MedicationPlan`、启用状态和时间槽；
- **设置 Settings**：扁平设置主页（基础数据、外观与格式、同步与备份、更新、指南/隐私与权限/功能教程/关于）。

History 的三个次级入口是全屏子页面（`insights`、`retrospective`、`timeline` 路由）；设置的“关于”和“Google Drive 备份与恢复”同样是全屏子页面。这些页面在主导航栏显隐会改变页面几何时，不再叠加整页缩放/淡入（v1.7.3），并在最终几何内执行 220 ms 淡入 + 0.98→1.0 缩放的进入动效（v1.7.4）。

紧凑窗口使用 Material 3 底部导航；中等和展开窗口使用 Navigation Rail。编辑器以
全屏 transition layer 进入，导航 chrome 与页面一起过渡，系统返回、UI 返回、保存和
取消都回到原来的顶层目的地。折叠屏/大屏通过窗口尺寸决定导航和内容布局，不依赖
固定手机宽度。

## 方案编辑器

`MedicationPlanBottomSheet` 与 `MedicationPlanCard` 负责方案创建和编辑。编辑器支持
药物、途径、剂量、DAILY/WEEKLY/CUSTOM 周期和多个每日时间槽；时间槽保存时按本地
时间排序，稳定 slot ID 不因简单重排而被当作新的列表索引。启用开关通过
Repository/application action 保存并重排提醒。

## 记录与共享组件

- `MedicationRecordsScreen` 展示历史记录，并通过 `MedicationRecordItem` 显示途径、
  药物、剂量、时间和日期。
- `MedicationRecordBottomSheet` 提供新增/编辑表单和删除确认。
- `MedicationOptionGrid`、`MedicationEditorActionRow` 等组件复用表单选择和保存/取消
  操作。
- `ConcentrationChart` 与 `ConcentrationChartGeometry` 绘制主页浓度趋势；它们接收
  PK 投影结果，不直接访问 Room DAO。

## 历史、时间线、洞察与回顾性 PK

- `HistoryScreen`（`ui/screens/HistoryScreen.kt`）+ `HistoryViewModel`：日历与选中日期的事实记录；计划与实际的时间差，未匹配事件按真实来源显示，不猜测计划归属。
- `TimelineScreen`（`ui/screens/timeline/`）：按月的统一时间线，行分为已匹配、未记录计划与未匹配摄入；日期条连续显示并包含相邻月份，未来日期不可选。仅呈现事实，不含评分或依从性判定。
- `InsightsScreen`（`ui/screens/insights/`）：7/30/90 天事实性汇总（记录次数、来源分布、身份置信度），不输出严格历史依从率。
- `RetrospectivePkScreen` 与 `RetrospectiveConcentrationChart`（`ui/screens/retrospective/`）：7/30/90 天区间（默认 7 天）的模型估算浓度曲线；曲线仅在视图层去抽样并保留首尾与极值，支持点按/拖动查看某一时刻的估算值；标记为底部小型刻度/圆点。所有数值都是模型估算，并非实测血药浓度。

这些界面读取共享的历史投影（`history/` 包，`HistoryReadService`），不直接访问 Room DAO，也不写入数据；投影和 PK 后处理在 `Default` dispatcher 上执行（v1.8.0）。

## 设置页

`SettingsScreen` 通过 `SettingsViewModel` 读写 `SettingsDataStore`，是一个可滚动的扁平页面，由 `ui/screens/settings/` 下的分区组件组成：基础数据（体重）、外观与格式（夜间模式、配色来源与 8 个预设调色板、时间制式）、同步与备份（本地数据导出/导入、Health Connect、Google Drive 入口）、更新，以及指南、隐私与权限、功能教程、关于。应用主题设置不会替 Widget 的独立外观配置。

同步与备份将 Evolune Portable JSON/CSV 与旧版 Mahiro JSON 兼容路径、可选 Health Connect 前台体重读取和手动 Google Drive 加密备份分开；首次信任/权限引导（v1.4）和可重开的功能教程仍在“指南/功能教程”中。

## Phone Widget 配置与展示

`WidgetConfigurationActivity` 是 Compose 配置页，提供代表性预览、Auto/Light/Dark、
Material You/Monet 配色、透明度和恢复默认值。预览与生产 Widget 复用同一 palette、
背景和前景解析；每个 `appWidgetId` 独立保存配置。

v1.6 起（v1.9.0 仍然如此）系统选择器有四个独立 provider：EvoluneWidgetReceiver（今日计划）、NextDoseWidgetReceiver、CurrentE2WidgetReceiver、PkChartWidgetReceiver。今日进度并入今日计划；配置只在“应用”后成功，取消/返回不误建实例。

`EvoluneWidgetReceiver`、`WidgetWork`、`WidgetPresentation` 和 `WidgetUi` 构成
RemoteViews 边界：

- occurrence-driven 行按计划时间槽和本地日期生成；
- 今日计划沿用紧凑/滚动布局；其他入口和大尺寸 E2 趋势按各自 provider 尺寸自适应；
- 超出容量时使用 RemoteViews collection/list 纵向滚动，标题和进度区保持固定；
- 勾选动作携带 occurrence-scoped 身份，先持久化 `DoseEvent` 再刷新 Widget；
- 日期、时间、时区变化和多个 Widget 实例都通过 receiver/coordinator 重新构建状态。

Widget 不创建第二份数据源，也不通过 Compose 渲染。其颜色、透明度和完成状态来自
解析后的 Widget presentation state。

## Wear surface

`wear` 模块自 v1.3 起提供可打开的 WearAppActivity、版本化快照和确认/撤销。
v1.6 提供三新 Tile、旧兼容曲线 Tile、三 Short Text Complication，统一预览/品牌与圆屏布局。
Phone 验证并持久化确认/撤销与精确跳过提醒；Wear 只缓存可重建状态。

## 代码导航

```text
app/src/main/java/io/github/yingqiu0871/evolune/
├── navigation/       Screen.kt, AppNavigation.kt, PrimaryNavigationChrome.kt
├── ui/screens/       Home, Records, History, Medication Plans, Settings (+ settings/, insights/, retrospective/, timeline/)
├── ui/components/    editors, record items, cards, charts
├── history/          read service, projection, view models (+ insights/, retrospective/, timeline/, pk/)
├── export/           Portable JSON/CSV codecs, export and import services
├── theme/palette/    shared palette authority and legacy built-in theme
├── widget/           RemoteViews receiver, presentation, configuration
├── data/             SettingsDataStore and Room-facing adapters
├── core/model/       MedicationPlan, slots, DoseEvent, occurrence primitives
└── core/dataapi/     Repository contracts
wear/                 Wear App, Tiles, Complications, derived cache and Data Layer
experience-core/      shared occurrence/presentation/history/insights logic and versioned Wear contracts
```
