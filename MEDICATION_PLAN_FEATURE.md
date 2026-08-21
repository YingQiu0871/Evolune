# 用药方案与时间槽

本文描述当前生产代码中的 `MedicationPlan` 领域语义，以及它与提醒、记录和
Phone Widget 的关系。Room v3 的表结构和迁移细节以
[产品概览](docs/evolune/PRODUCT_OVERVIEW.md) 与 schema 导出为准；本文不改变
数据库协议。

## MedicationPlan aggregate

`MedicationPlan` 是一个完整的领域 aggregate，包含名称、药物/酯类、给药途径、
剂量、周期类型、启用状态、创建时间和有序的 `ScheduledDoseSlot` 列表。周期类型
包括：

- `DAILY`：每天按全部时间槽生成；
- `WEEKLY`：只在选定的星期生成；
- `CUSTOM`：按 `intervalDays` 生成。

计划保存和编辑通过 `MedicationPlanRepository` 完成。Phone UI、提醒、Widget 和
Wear 入口都通过 Repository/application action 写入，生产路径不直接绕过 DAO。

## ScheduledDoseSlot

每个时间槽包含稳定的 UUIDv5 `id`、所属 `planId`、分钟精度的 `localTime` 和用于
展示/持久化顺序的 `position`。时间槽在编辑器、领域映射和 Repository 边界按本地
时间规范化为 chronological order；相同时间仍可作为不同槽位保留。

槽位身份不是“列表第几个”的临时 UI 身份。编辑器在时间重排时尽可能复用已有槽位
ID，`position` 可以重新编号；因此依赖槽位身份的 occurrence、提醒和 Widget action
不会因为用户只调整顺序而被错误地当作新记录。新槽位才会依据稳定的 UUIDv5 规则
生成 ID。

## Local creation-date activation

Occurrence generation 以计划 `createdAt` 在显式时区中的**本地日期**作为起点：

```text
计划在 18:00 创建；时间槽为 09:00、17:00、22:00
→ 创建日仍生成 09:00、17:00、22:00 三个 occurrence
```

早于本地创建日期的日期不生成 occurrence；不能再使用“scheduled instant 必须晚于
精确 `createdAt`”的旧规则。夏令时 gap 采用 Java time 的 forward 解析，overlap
采用 earlier offset；日期身份仍以该时区的 `LocalDate` 为准。

## MedicationOccurrence

Occurrence 是一个具体计划、槽位和日期的逻辑发生项，包含：

- `planId`；
- `slotId` 与槽位 position；
- `scheduledLocalDateTime`、`scheduledAt` 和 `zoneId`；
- 由 `planId + slotId + intended scheduled local date` 派生的确定性 occurrence ID。

同一计划的多个每日时间因此成为多个独立 occurrence，而不是一个合并按钮。时间轴
按实际 scheduled time 排序，并保留相同时间的全部 occurrence。记录匹配优先使用
精确 `slotId + localDate`；旧的 null-slot 事件只在候选唯一且时间落在包含端点的 ±1
小时窗口内时回退匹配，零个或多个候选都保持未匹配。

## Editor save/update behavior

编辑器保存前校验名称、剂量、周期字段和时间槽；时间槽以 chronological order
保存，position 连续但不作为业务身份。Repository 在一个 transaction 中替换计划
主体和槽位，随后回读并验证 aggregate。启用/禁用也通过 transaction 完成，并触发
提醒重排和相关派生状态更新。

## Reminder relationship

`ReminderManager` 根据启用计划和 occurrence 计算未来提醒，广播接收器只负责触发
通知入口。通知确认最终写入同一个 `DoseEventRepository`；提醒不是另一份用药事实。
计划禁用或修改后，旧提醒会被取消/重排。

## Widget relationship

Phone Widget 通过 `WidgetSnapshotLoader` 从权威计划和 DoseEvent Repository 构建
occurrence-driven 展示。一个计划的多个时间槽对应多个独立行；2×2 是完整日常规格，
更大尺寸显示更多行，容量不足时使用 RemoteViews collection 纵向滚动。勾选动作携带
`planId`、`slotId`、`scheduledLocalDate` 和 `occurrenceId`，记录实际点击时间，先持久化
再刷新 Widget，并以 occurrence 身份保证重复点击和多个 Widget 实例的幂等性。

Widget 只是展示和动作入口，Phone Room/domain/repository 仍是唯一事实来源。配置、
缓存或 RemoteViews 状态都不能独立创建或修改用药事件。

## Persistence boundary

Room v3 持久化 `MedicationPlanEntity`、`ScheduledDoseSlotEntity` 和 `DoseEventEntity`。
迁移、JSON v1、PK adapter、Wear Data Layer 与 Widget 均通过各自的边界适配，不改变
本文件描述的 aggregate 语义。任何 schema、稳定 ID 或协议变化都必须另立迁移和回归
门槛。
