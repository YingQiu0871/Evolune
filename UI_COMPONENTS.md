# UI 组件清单

本清单以当前源码为准。Phone 页面使用 Jetpack Compose；Phone 桌面 Widget 使用
Android RemoteViews；Wear Tile 是独立的 Wear surface。Widget 不属于 Compose 组件
树，也不建立独立数据源。

## Compose 屏幕

| 屏幕 | 入口 | 职责 |
|---|---|---|
| Home | `ui/screens/HomeScreen.kt` | 当前 E2、历史/预测浓度图和今日摘要 |
| Records | `ui/screens/MedicationRecordsScreen.kt` | DoseEvent 列表、新增、编辑、删除 |
| Medication Plans | `ui/screens/MedicationPlansScreen.kt` | 计划列表、启用状态和编辑入口 |
| Settings | `ui/screens/SettingsScreen.kt` | 体重、应用主题/颜色、时间制式、更新、JSON 和帮助 |

`navigation/Screen.kt` 定义目的地；`navigation/AppNavigation.kt` 根据窗口尺寸选择
紧凑底部导航或中等/展开 Navigation Rail。编辑器通过 transition layer 进入和退出，
保持导航 chrome 与页面运动的一致性，并支持系统返回、UI 返回、保存和取消。

## 共享记录和编辑组件

- `MedicationRecordItem.kt`：单条 DoseEvent 的药物、途径、剂量、时间和日期展示；
- `MedicationRecordBottomSheet.kt`：记录新增/编辑及删除确认；
- `MedicationPlanCard.kt`：计划摘要、启用开关和编辑入口；
- `MedicationPlanBottomSheet.kt`：药物、途径、剂量、周期及多个时间槽的编辑器；
- `MedicationOptionGrid.kt`：药物/途径等选项布局；
- `MedicationEditorActionRow.kt`：保存、取消和删除操作行；
- `EditorTransitionHost.kt`：编辑器的全屏进入/返回过渡；
- `ConcentrationChart.kt` 与 `ConcentrationChartGeometry.kt`：浓度曲线和几何布局。

组件通过 ViewModel/application action 使用 Repository contract，不直接操作 Room DAO。

## 响应式与折叠屏布局

`AppNavigation` 与各屏幕依据 Material window size 改变导航和内容排列；中等/展开窗口
使用 Navigation Rail，紧凑窗口使用底部导航。表单和图表保持可滚动，编辑器不在进入
次级页面时提前移除顶层导航 chrome。

## Phone Widget 边界

| 文件 | 职责 |
|---|---|
| `widget/WidgetConfigurationActivity.kt` | Compose 配置页、预览、外观保存/恢复 |
| `widget/WidgetAppearance.kt` | 按 `appWidgetId` 保存模式、调色板和透明度 |
| `widget/WidgetWork.kt` | 从 Phone Repository 加载快照、执行 occurrence action |
| `widget/WidgetPresentation.kt` | 生成 occurrence-driven 状态、进度和浓度 |
| `widget/WidgetUi.kt` | RemoteViews 兼容的尺寸、行密度、颜色和按钮 |
| `widget/EvoluneWidgetReceiver.kt` | AppWidget 生命周期、日期/时间/时区刷新和 collection |

Widget presentation 以 `MedicationOccurrence` 为行单位：同一计划的多个时间槽独立
显示，2×2 是完整日常规格，更大尺寸显示更多行，超出容量时使用 RemoteViews
collection/list 纵向滚动。勾选动作携带 plan/slot/date/occurrence identity，持久化
成功后才刷新；多 Widget 实例共享权威数据但保持外观配置隔离。

## Wear surface

`wear/` 模块中的 Tile、缓存和 `WearDataLayer` 负责快照展示与动作传输。Phone Room
仍为权威来源；Wear 缓存可重建，不是第二数据库。完整 Wear OS Companion App 属于
规划中的 v1.3。

## 维护边界

新增 UI 应优先复用现有组件和状态模型，保持 Room/domain/repository、PK adapter、
Widget RemoteViews 和 Wear Data Layer 的边界。不要把 Widget 改写为 Compose，也不要
在 UI 层复制用药事实或绕过 occurrence-scoped action 语义。
