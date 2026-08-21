# 当前 UI 结构速览

本文说明当前生产 UI 的导航和主要组件。Phone 主界面使用 Jetpack Compose；桌面
Widget 仍是 Android `RemoteViews`，不是 Compose/Glance 页面；Wear Tile 是独立的
Wear surface。

## 顶层导航

`AppNavigation` 维护四个顶层目的地：

- **主页 Home**：当前 E2 浓度、历史/预测曲线和今日摘要；
- **记录 Records**：浏览、添加、编辑和删除 `DoseEvent`；
- **方案 Medication Plans**：管理 `MedicationPlan`、启用状态和时间槽；
- **设置 Settings**：应用设置、JSON 导入导出、更新检查、关于与免责声明。

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

## 设置页

`SettingsScreen` 通过 `SettingsViewModel` 读写 `SettingsDataStore`，包括体重、应用
主题（Light/Dark/AMOLED/System）、应用颜色主题、时间制式和自动检查更新。JSON
导入导出、关于和免责声明也从设置入口进入。应用主题设置不会替 Widget 的独立外观
配置。

## Phone Widget 配置与展示

`WidgetConfigurationActivity` 是 Compose 配置页，提供代表性预览、Auto/Light/Dark、
Material You/Monet 配色、透明度和恢复默认值。预览与生产 Widget 复用同一 palette、
背景和前景解析；每个 `appWidgetId` 独立保存配置。

`EvoluneWidgetReceiver`、`WidgetWork`、`WidgetPresentation` 和 `WidgetUi` 构成
RemoteViews 边界：

- occurrence-driven 行按计划时间槽和本地日期生成；
- 2×2 是完整日常规格，更大尺寸自然显示更多行；
- 超出容量时使用 RemoteViews collection/list 纵向滚动，标题和进度区保持固定；
- 勾选动作携带 occurrence-scoped 身份，先持久化 `DoseEvent` 再刷新 Widget；
- 日期、时间、时区变化和多个 Widget 实例都通过 receiver/coordinator 重新构建状态。

Widget 不创建第二份数据源，也不通过 Compose 渲染。其颜色、透明度和完成状态来自
解析后的 Widget presentation state。

## Wear surface

`wear` 模块提供 Wear Tile 和 Phone/Wear Data Layer。Wear 缓存可重建的计划/浓度快照，
Phone 验证并持久化 Wear action；Phone Room 仍是权威来源。完整的 Wear OS Companion
App 属于规划中的 v1.3，不应把当前 Tile 误写成完整 App。

## 代码导航

```text
app/src/main/java/io/github/yingqiu0871/evolune/
├── navigation/       Screen.kt, AppNavigation.kt
├── ui/screens/       Home, Records, Medication Plans, Settings
├── ui/components/    editors, record items, cards, charts
├── widget/            RemoteViews receiver, presentation, configuration
├── data/              SettingsDataStore and Room-facing adapters
├── core/model/        MedicationPlan, slots, DoseEvent, occurrence primitives
└── core/dataapi/      Repository contracts
wear/                  Wear Tile and Data Layer surface
```
