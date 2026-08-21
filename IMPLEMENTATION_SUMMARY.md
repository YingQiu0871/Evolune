# Evolune Implementation Summary — through completed v1.1

本文总结当前 `main` 已包含的实现。`v1.0.0` 是当前公开稳定 Release；v1.1
Phone Widget Completion 已于 2026-08-21 完成并关闭，v1.2 尚未开始。

当前文档基线为 `main @ 5418819ef236d6e815a6bee5b06166e4d2305d40`；v1.1
Phone Widget 最终实现合并提交仍记录为 `7531af3fdb73b5ecfc2bfe5af65771d670945bdc`。

## Domain and persistence

- `MedicationPlan` 是包含方案属性、周期、启用状态、创建时间和有序
  `ScheduledDoseSlot` 列表的领域 aggregate。
- 每个 `ScheduledDoseSlot` 使用稳定 UUIDv5 身份、所属 plan ID、分钟精度的
  `localTime` 和连续 `position`。编辑器按本地时间规范化顺序，并在时间重排时
  尽可能复用既有槽位 ID；position 会重新编号，但槽位身份不是列表索引。
- `DoseEvent` 使用权威 `occurredAt: Instant`，并可携带 zone、local date、slot、
  source、status、revision 和途径/剂量等领域元数据。
- Room v3 保存计划、槽位和事件。Repository contract 与 Room 实现分离；生产
  入口通过 Repository 和 application action 写入，不直接绕过 DAO。
- 计划主体与槽位在 transaction 中替换并回读验证，事件写入区分插入、幂等、冲突、
  无效和存储失败等结果。

## Scheduling, reminders and records

- DAILY、WEEKLY、CUSTOM 方案均支持多个每日时间槽。
- ReminderManager/receiver 根据启用方案安排通知；通知确认、Phone UI、Widget
  和 Wear action 最终都进入同一权威 `DoseEventRepository`。
- 记录、编辑、删除和历史查看由 Phone Compose UI 提供，实际记录时间与计划时间
  分开保存。

## JSON and PK

- Mahiro JSON v1 通过独立 DTO、codec 和 adapter 导入/导出，并报告 inserted、
  idempotent、conflict、invalid 等逐项结果。
- PK 路径为：领域 `DoseEvent` → `DomainDoseEventToPkAdapter` → PK model /
  `SimulationEngine` → Home 图表和 Widget 浓度消费者。
- v1.1 没有改变 PK 数值算法、参数或回归行为：`PK_NUMERICAL_ALGORITHM_DIFF = ZERO`。

## Settings and Phone UI

- `SettingsDataStore` 保存体重、应用主题模式（Light/Dark/AMOLED/System）、应用
  颜色主题、时间制式和自动检查更新开关。
- Settings screen 同时提供 Mahiro JSON 文件/剪贴板导入导出、更新检查、版权和
  免责声明入口。
- Phone Compose 顶层导航包含 Home、Records、Medication Plans 和 Settings。
  紧凑窗口使用底部导航；中等和展开窗口使用 Navigation Rail；编辑器通过独立
  的全屏 transition layer 保持导航 chrome 的连续性。

## Completed v1.1 Phone Widget

- Widget 仍使用 RemoteViews，不建立独立数据库或第二事实来源。
- Widget 从权威计划和事件生成今日 occurrence 行；同一计划的多个时间槽显示为
  独立、按时间排序的 occurrence。
- 2×2 是完整日常使用规格；更大尺寸显示更多行，超出可见容量时使用官方
  RemoteViews collection/list 垂直滚动，顶部进度和浓度区域保持固定。
- 每日进度只统计实际已记录 occurrence；过去但未记录的 occurrence 不会自动完成。
- Widget action 携带 planId、slotId、scheduledLocalDate 和 occurrenceId，记录
  实际点击时间，写入精确 slot/date，并在持久化成功后刷新 Widget。重复点击和多
  Widget 实例遵守 occurrence-scoped 幂等语义。
- 每个 Widget 实例独立保存 Auto/Light/Dark、Material You 或 curated Monet
  palette、30%–100% 背景透明度及恢复默认值。前景色根据实际解析后的背景保证
  可读性；配置页预览与生产 Widget 共用同一语义解析。

## Wear

当前公开能力是 Wear Tile/Data Layer：Phone 发送计划/浓度快照，Wear 保存可重建
缓存并提交剂量 action；Phone Room 仍是权威来源，action 采用 persist-first、
重放/冲突处理和精确 DataItem 删除边界。完整 Wear OS Companion App 仍规划为
v1.3。

## Validation boundary

最终 v1.1 Owner 设备验收、Widget/Experience-core/App/Wear 测试、Widget
instrumentation 和 post-merge main CI 均通过；Room schema、JSON v1、Wear
protocol、PK 数值算法和受保护根完整性保持不变。
