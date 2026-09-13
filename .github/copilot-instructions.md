# Copilot Instructions

截至 2026-09-12，Evolune 当前公开稳定版为 v1.6.0。v1.2 的前台 Health Connect
体重读取和手动 Google Drive 加密备份、v1.3 Wear App、v1.4 引导、v1.5 稳定性和
v1.6 Gallery 均已发布。保持本文件简洁，以生产源码和 docs/evolune/CURRENT_STATUS.md 为准。

## Architecture

- Phone Room v3、domain model 和 Repository 是唯一事实来源；Widget、Wear、JSON 和
  外部集成只能按明确契约消费或交换数据，不升级为第二用药事实来源。
- 遵守 `consumer -> core.dataapi <- Room implementation` 的边界，不让 UI、Widget 或
  Wear 直接绕过 Repository/DAO contract。
- `DoseEvent.occurredAt` 是权威实际记录时间；PK 只通过
  `DomainDoseEventToPkAdapter` 进入 `SimulationEngine`。

## Stable schedule and occurrence semantics

- `ScheduledDoseSlot` 有稳定 UUIDv5 identity。按时间重排时可以重新编号 `position`，
  但不得把槽位身份当成临时列表索引或无故重建已有 ID。
- 时间槽按本地分钟 chronological order 呈现；Occurrence identity 必须稳定，并由
  计划、槽位和 intended scheduled local date 共同确定。
- 精确记录匹配优先使用 slot/date；null-slot 保留原窗口优先和 v1.2.2 唯一同日回退，
  原窗口竞争事件不能被回收去匹配其他 occurrence，不能把历史事件宣传成精确关联。

## Widget and Wear actions

- Phone Widget 保持 RemoteViews 技术边界，使用权威 snapshot；不要创建第二个数据库或
  另一套 DoseEvent writer。
- Widget action 必须是 occurrence-scoped，携带 planId、slotId、scheduledLocalDate
  和 occurrenceId，记录实际点击时间，先持久化再刷新/提示，并保持幂等。
- 多 Widget 实例、日期/时区变化、进程重建和 RemoteViews collection 滚动不能破坏
  上述语义。Widget 外观按 `appWidgetId` 隔离，不等于全局应用主题。
- Wear Tile/Data Layer 同样通过 Phone 权威 Repository；缓存可重建，不升级为事实来源。

## Change discipline

- 不修改 Room schema、JSON v1、Wear `/hrt/*` protocol、PK 数值参数或版本元数据，除非
  任务明确授权并提供独立迁移/回归计划。
- 不把历史报告、评审记录或 legacy specification 改写成当前状态。
- 编写新 Compose UI 时保持 `MainActivity.kt` 为组合入口，优先添加独立 composable
  和 preview；复用现有 Material 3 组件与窗口响应式布局。
- 不引入新的依赖、秘密、凭据或本地路径；变更前后保持 `git diff --check` 通过。
