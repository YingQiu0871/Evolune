# UI 组件清单

基线：v1.11.1；2026-10-01 文档核对。

本清单以当前源码为准。Phone 页面使用 Jetpack Compose；Phone 桌面 Widget 使用
Android RemoteViews；Wear Tile 是独立的 Wear surface。Widget 不属于 Compose 组件
树，也不建立独立数据源。

## Compose 屏幕

| 屏幕 | 入口 | 职责 |
|---|---|---|
| Home | `ui/screens/HomeScreen.kt` | 当前 E2、历史/预测浓度图（可选 CPA 估算曲线，v1.10.0）和今日摘要 |
| Records | `ui/screens/MedicationRecordsScreen.kt` | DoseEvent 列表、新增、编辑、删除；恰好新增一条记录时滚动到该记录（v1.11.1） |
| History | `ui/screens/HistoryScreen.kt` | 日历、选中日期的事实记录、次级入口（洞察/回顾性 PK/时间线）；从次级页面返回保留滚动位置（v1.10.0） |
| Timeline | `ui/screens/timeline/TimelineScreen.kt` | 月度统一时间线与连续日期条（History 子页面） |
| Insights | `ui/screens/insights/InsightsScreen.kt` | 7/30/90 天事实性汇总（History 子页面） |
| Retrospective PK | `ui/screens/retrospective/RetrospectivePkScreen.kt` | 回顾性模型估算浓度曲线与区间选择（History 子页面） |
| Medication Plans | `ui/screens/MedicationPlansScreen.kt` | 计划列表、启用状态和编辑入口 |
| Settings | `ui/screens/SettingsScreen.kt` + `ui/screens/settings/*` | 按用途分四组（v1.10.0）：血药浓度计算（体重、Health Connect 体重同步、CPA 曲线开关）、外观、备份与数据、关于与帮助（使用帮助/隐私与权限/更新/关于） |
| Google Drive backup | `ui/screens/GoogleDriveBackupRestoreScreen.kt` | Google Drive 加密备份与恢复（Settings 子页面） |
| Help | `ui/screens/HelpScreen.kt` | 「使用帮助」（v1.10.0，Settings 子页面）：链接到功能教程与首次引导 |
| About / Disclosures / Onboarding / Feature tutorial | `ui/screens/AboutScreen.kt`、`DisclosuresScreen.kt`、`OnboardingFlowScreen.kt`、`FeatureTutorialScreen.kt` | 关于、条款与隐私说明、首次引导、可重开的功能教程（后两者从「使用帮助」进入） |

`navigation/Screen.kt` 定义目的地；`navigation/AppNavigation.kt` 根据窗口尺寸选择
紧凑底部导航或中等/展开 Navigation Rail。编辑器通过 transition layer 进入和退出，
保持导航 chrome 与页面运动的一致性，并支持系统返回、UI 返回、保存和取消。
v1.11.0 起五个顶层页面的顶栏与底部导航栏为毛玻璃（`ui/components/FrostedChrome.kt`：
内容录入 `GraphicsLayer` 后经 RenderEffect 模糊重绘，叠加 60% 底色，无新依赖）；页面内容
滚到两栏下方，并通过 `LocalChromeInsets`/`chromePadding()` 留出首尾空间。全屏子页面保持
不透明、占位的原有布局。

## 共享记录和编辑组件

- `MedicationRecordItem.kt`：单条 DoseEvent 的药物、途径、剂量、时间和日期展示；
- `MedicationRecordBottomSheet.kt`：记录新增/编辑及删除确认；
- `MedicationPlanCard.kt`：计划摘要、启用开关和编辑入口；
- `MedicationPlanBottomSheet.kt`：药物、途径、剂量、周期及多个时间槽的编辑器；
- `MedicationOptionGrid.kt`：药物/途径等选项布局；
- `MedicationEditorActionRow.kt`：保存、取消和删除操作行；
- `EditorTransitionHost.kt`：编辑器的全屏进入/返回过渡；
- `ConcentrationChart.kt` 与 `ConcentrationChartGeometry.kt`：浓度曲线和几何布局；v1.10.0 起可选叠加
  CPA 估算虚线（右侧独立 ng/mL 轴，E2 仍为左侧 pg/mL 轴）；
- `FrostedChrome.kt`：毛玻璃顶栏/底栏的 backdrop 与 chrome insets 辅助（v1.11.0）；
- `SettingsListItem.kt`：设置页列表行样式。

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

该 receiver 文件还定义 NextDoseWidgetReceiver、CurrentE2WidgetReceiver、PkChartWidgetReceiver；
最终共四个系统入口。WidgetPresentation/WidgetUiMapper 提供展示状态，图表使用共享 PK 样本；
配置仅在应用后返回成功。今日进度并入今日计划，不再有独立公开 provider。

Widget presentation 以 `MedicationOccurrence` 为行单位：同一计划的多个时间槽独立
显示，2×2 是完整日常规格，更大尺寸显示更多行，超出容量时使用 RemoteViews
collection/list 纵向滚动。勾选动作携带 plan/slot/date/occurrence identity，持久化
成功后才刷新；多 Widget 实例共享权威数据但保持外观配置隔离。v1.11.0 起
`requestEvoluneWidgetUpdate` 在 `Dispatchers.Default` 上执行，记录/方案变化触发的 Widget 刷新
不再占用主线程。

## Wear surface

`wear/` 已包含 WearAppActivity、WearAppStore、WearGalleryTileService、DoseTileService 和
WearComplicationDataSource。三个新 Tile 与三个 Short Text provider 共享派生状态与刷新协调，
旧 Tile 组件身份保留。Phone Room 仍为权威来源，确认/撤销/跳过由 Phone 校验处理。
Phone 侧在记录/方案变化后发布 Wear 快照并同步仪表盘，v1.11.0 起这两项在后台线程执行（`MainActivity`）。

## 维护边界

新增 UI 应优先复用现有组件和状态模型，保持 Room/domain/repository、PK adapter、
Widget RemoteViews 和 Wear Data Layer 的边界。不要把 Widget 改写为 Compose，也不要
在 UI 层复制用药事实或绕过 occurrence-scoped action 语义。
