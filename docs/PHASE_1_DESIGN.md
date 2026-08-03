# Evolune Phase 1 实施设计

**状态**：设计决策已解决；待按批次实施，本文件本轮不触发任何代码或 schema 修改

**基线日期**：2026-08-01

**范围**：核心用药事件时间语义、稳定标识、计划槽位、Repository contract、Room schema 基线与迁移测试。

**本轮限制**：本文只形成设计，不修改业务代码、Room schema、PK 参数、JSON 格式或 Wear 协议。

## 1. 设计依据与边界

本文依据当前工作树中的实际实现，以及以下文档：

- `docs/evolune/ARCHITECTURE.md`
- `docs/evolune/DECISIONS.md`
- `docs/evolune/MIGRATION_PLAN.md`
- `docs/evolune/FEATURE_MATRIX.md`
- `docs/legacy-specs/wear-protocol.md`

约束结论：

1. Room 仍是主要事实来源。
2. 依赖方向必须是 `feature -> core:data-api <- core:database`；Phase 1 先用 `app` 内 package 表达，不立即创建全部 Gradle module。
3. Tracked Date 不进入 Phase 1，不创建模型、表、字段或迁移。
4. Wear 仅提供核心模型需求：稳定事件 ID、稳定槽位 ID、revision 和幂等语义；本阶段不实现新协议。
5. Health Connect、Glance、WorkManager、本地加密备份和云同步均不进入本阶段。
6. `timeH` 不在 Phase 1 删除；它继续作为旧数据库/JSON 兼容字段和 PK 层时间坐标。

## 2. 当前真实模型

### 2.1 `DoseEvent`

文件：`app/src/main/java/io/github/yuninggu/evolune/pk/DoseEvent.kt`

| 字段 | Kotlin 类型 | 默认值 | 当前含义 |
|---|---|---|---|
| `id` | `UUID` | `UUID.randomUUID()` | 数据库主键；编辑时复用，部分入口使用确定性 UUID |
| `route` | `Route` | 无 | 给药途径 |
| `timeH` | `Double` | 无 | Unix epoch 起的绝对小时数，即 `epochMillis / 3_600_000.0` |
| `doseMG` | `Double` | 无 | 剂量，mg |
| `ester` | `Ester` | 无 | 酯类/药物类型 |
| `extras` | `Map<DoseEvent.ExtraKey, Double>` | 空 map | 浓度、面积、贴片释放率、舌下参数和抗雄类型等扩展参数 |

当前 `DoseEvent` 同时承担 Domain、PK 输入、Repository 返回值、UI model 和外部 JSON 映射对象，尚未分层。

### 2.2 `DoseEventEntity`

文件：`app/src/main/java/io/github/yuninggu/evolune/data/DoseEventEntity.kt`

| 字段 | Kotlin 类型 | Room 存储类型 | 当前约束 |
|---|---|---|---|
| `id` | `UUID` | `TEXT NOT NULL` | 主键，经 `Converters` 转字符串 |
| `route` | `String` | `TEXT NOT NULL` | `Route.name` |
| `timeH` | `Double` | `REAL NOT NULL` | 唯一持久化事件时间 |
| `doseMG` | `Double` | `REAL NOT NULL` | 剂量 |
| `ester` | `String` | `TEXT NOT NULL` | `Ester.name` |
| `extras` | `Map<String, Double>` | `TEXT NOT NULL` | Kotlin Serialization JSON |

Entity 与 `DoseEvent` 通过 `toDoseEvent()` 和 `fromDoseEvent()` 直接互转。未知枚举或损坏 JSON 会在映射时抛出异常，没有显式错误类型。

### 2.3 `MedicationPlan`

文件：`app/src/main/java/io/github/yuninggu/evolune/data/MedicationPlan.kt`

| 字段 | Kotlin 类型 | 默认值 | 当前含义 |
|---|---|---|---|
| `id` | `UUID` | 随机 UUID | 计划主键 |
| `name` | `String` | 无 | 用户可见名称 |
| `route` | `Route` | 无 | 给药途径 |
| `ester` | `Ester` | 无 | 酯类/药物类型 |
| `doseMG` | `Double` | 无 | 每次计划剂量 |
| `scheduleType` | `ScheduleType` | 无 | `DAILY`、`WEEKLY`、`CUSTOM` |
| `timeOfDay` | `List<LocalTime>` | 无 | 一个计划内的多个本地时间点；元素当前没有稳定 ID |
| `daysOfWeek` | `Set<DayOfWeek>` | 空 set | WEEKLY 使用 |
| `intervalDays` | `Int` | `1` | CUSTOM 使用 |
| `isEnabled` | `Boolean` | `true` | 是否启用 |
| `extras` | `Map<DoseEvent.ExtraKey, Double>` | 空 map | 与给药方式相关的扩展参数 |
| `createdAt` | `Long` | 当前 epoch millis | 创建时间 |

### 2.4 `MedicationPlanEntity`

文件：`app/src/main/java/io/github/yuninggu/evolune/data/MedicationPlanEntity.kt`

| 字段 | Kotlin 类型 | Room 存储类型 | 当前含义 |
|---|---|---|---|
| `id` | `UUID` | `TEXT NOT NULL` | 主键 |
| `name` | `String` | `TEXT NOT NULL` | 名称 |
| `route` | `String` | `TEXT NOT NULL` | `Route.name` |
| `ester` | `String` | `TEXT NOT NULL` | `Ester.name` |
| `doseMG` | `Double` | `REAL NOT NULL` | 剂量 |
| `scheduleType` | `String` | `TEXT NOT NULL` | `ScheduleType.name` |
| `timeOfDay` | `List<String>` | `TEXT NOT NULL` | JSON 字符串列表，每项由 `LocalTime.toString()` 生成 |
| `daysOfWeek` | `Set<Int>` | `TEXT NOT NULL` | JSON 整数集合，值为 1-7 |
| `intervalDays` | `Int` | `INTEGER NOT NULL` | 自定义间隔 |
| `isEnabled` | `Boolean` | `INTEGER NOT NULL` | 0/1 |
| `extras` | `Map<String, Double>` | `TEXT NOT NULL` | JSON map |
| `createdAt` | `Long` | `INTEGER NOT NULL` | epoch millis |

## 3. 当前 Room v2 schema

文件：`app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt`

- 数据库名：`evolune_database`
- 当前版本：2
- `exportSchema = false`
- 表：`dose_events`、`medication_plans`
- 已有迁移：`MIGRATION_1_2`，只创建 `medication_plans`，保留 `dose_events`
- 当前没有 `app/schemas/`、Room schema JSON 或 `MigrationTestHelper`
- 当前没有 `androidx.room:room-testing` 测试依赖
- 当前 DAO 的所有事件排序和范围查询均依赖 `timeH`

版本 1 可从现有迁移反推为只有 `dose_events`；仓库没有可复核的 v1/v2 导出 schema，因此实施前必须先固定 v2 基线，不能直接跳到下一版本。

## 4. `timeH` 的全部当前用途

| 层/入口 | 当前用途 | 文件 |
|---|---|---|
| Room | `dose_events.timeH REAL`；全部、最近、范围和某时刻后的查询与排序 | `DoseEventEntity.kt`、`DoseEventDao.kt` |
| Repository | 计算最近 30 天边界；不足 20 次时回退到最近 20 条 | `DoseEventRepository.kt` |
| 手动记录 | 快速添加按分钟向下对齐；表单用 `Date.time / 3_600_000.0` | `MedicationRecordsScreen.kt`、`MedicationRecordBottomSheet.kt` |
| UI 展示 | 记录倒序；乘回毫秒后用设备当前时区格式化日期和时间 | `MedicationRecordsScreen.kt`、`MedicationRecordItem.kt` |
| 主页/图表 | 当前时刻每秒更新；图表窗口、坐标、插值和标签均使用绝对小时 | `HRTViewModel.kt`、`PKState.kt`、`HomeScreen.kt`、`ConcentrationChart.kt` |
| PK | 事件起点、相对时间 `tau`、贴片移除匹配、模拟范围、排序、插值和 AUC 网格 | `SimulationEngine.kt` |
| 计划预测 | `LocalDateTime.atZone(ZoneId.systemDefault())` 转 instant 后除以 3,600,000；预测冲突窗口为 1 小时 | `MedicationPlanPredictor.kt` |
| 提醒 | 记录时间由毫秒转换；计划命中使用相同药物字段和 `timeH` ±1 小时 | `ReminderDoseFactory.kt`、`DoseCheckInMatcher.kt`、`MedicationReminderReceiver.kt` |
| Widget | 计划时间转换、±1 小时/补录窗口、快速记录时间和 Widget PK 计算 | `WidgetUtils.kt`、`EvoluneWidgetReceiver.kt` |
| Wear | Wear `recorded_at` 毫秒转 `timeH`；当前曲线以 `currentTimeH + offset` 采样 | `WearDataLayer.kt` |
| JSON v1 | 读取和写出名为 `timeH` 的 Double；当前 parser 不使用 `meta.version` 分支 | `MahiroJsonFormat.kt` |
| 测试 | PK 相对时间、JSON 往返、提醒/Widget 窗口、Wear 毫秒转换和预测冲突 | `app/src/test/...` |

补充不变量：`getEventsForSimulation()` 在“最近 30 天不足 20 次给药”分支返回 DAO 的倒序最近 20 条，在另一分支返回升序记录。`SimulationEngine` 的贴片移除逻辑使用输入列表中的第一个后续移除事件。Phase 1 不得顺手统一排序，否则可能改变现有 PK 结果。

## 5. 不能改变的行为和数值不变量

1. 已有事件的 `id`、route、ester、doseMG、extras 和原始 `timeH` 位值必须保留。
2. `occurredAt` 必须代表与旧 `timeH` 相同的 instant；不得把 epoch 小时误当成本地小时。
3. 手动快速记录继续按分钟向下对齐；普通表单、Widget、Wear 和提醒入口继续保留当前毫秒语义。
4. 历史列表继续按实际时间倒序；范围查询边界语义在迁移前后保持一致。
5. 提醒和 Widget 的现有 ±1 小时命中窗口、预测冲突 1 小时窗口不得变化。
6. PK 的 route/ester/extras 解释、事件输入顺序、过滤条件、模拟范围、步数、插值、AUC 和所有参数常量不得变化。
7. `CorePK`、`TwoPartDepotPK`、`InjectionPK`、`EsterPK`、`OralPK`、`SublingualTheta`、`TransdermalGelPK` 和 `PatchPK` 的值不得修改。
8. 现有 PK 测试使用的 `1e-6` 数值容差不得因迁移而放宽；参数常量测试必须原样通过。
9. JSON v1 的字段名、route/extras 映射、有效 UUID 保留、未知 route/ester 跳过、weight 读取和空数组输出保持兼容。
10. 当前 Wear `/hrt/*` 路径、DataMap key 和 payload 不在 Phase 1 修改。
11. 当前删除仍是物理删除；Phase 1 不把历史数据自动改成软删除或跳过事件。
12. Tracked Date 不得出现在 Domain、Entity、schema、迁移或 Repository contract 中。

## 6. 目标 Domain Model

### 6.1 `core:model DoseEvent`

Phase 1 目标 Domain `DoseEvent`：

| 字段 | 类型 | Phase 1 语义 |
|---|---|---|
| `id` | `UUID` | 持久化稳定 ID；创建后不可改变 |
| `route` | `Route` | 保持当前枚举和值 |
| `occurredAt` | `Instant` | 实际给药发生的权威 instant |
| `zoneId` | `ZoneId?` | 创建/记录时已知的时区上下文；未知旧数据为 null，不猜测 |
| `localDate` | `LocalDate?` | 用户记录时的日历日期语义；未知旧数据为 null |
| `doseMG` | `Double` | 保持当前含义 |
| `ester` | `Ester` | 保持当前含义 |
| `extras` | `Map<ExtraKey, Double>` | 保持当前键和值 |
| `slotId` | `UUID?` | 若记录明确来自计划槽，指向稳定 `ScheduledDoseSlot.id`；旧记录和无法证明关联的记录为 null |
| `source` | `DoseEventSource` | 记录入口来源 |
| `status` | `DoseEventStatus` | Phase 1 仅使用 `RECORDED` |
| `revision` | `Long` | 从 1 开始的持久化行版本；有意义的编辑递增 |

当前 `pk.DoseEvent` 在 Phase 1 保留为 PK 输入模型。新 Domain 对象通过纯 Kotlin adapter 转为现有 PK 对象，避免在同一批修改 PK 算法和持久化模型。

### 6.2 `DoseEventSource`

Phase 1 允许值：

- `LEGACY`：v2 及更早数据库迁移行，具体入口不可证明。
- `MANUAL`：手机记录表单或快速添加。
- `JSON_V1`：Mahiro JSON v1 导入。
- `REMINDER`：通知确认动作。
- `WIDGET`：现有 RemoteViews 快速记录。
- `WEAR`：现有 Wear 动作。

预测事件不是已发生记录，不写入 `dose_events`，因此不增加 `PREDICTED` 持久化来源。

### 6.3 `DoseEventStatus`

Phase 1 只定义 `RECORDED`，表示当前表中已经实际保存的给药事件。`SKIPPED`、`VOIDED`、`DELETED`、`UNDO` 等状态推迟到其产品行为和迁移语义明确后；不得从 Wear 远期规格提前引入。

### 6.4 `ScheduledDoseSlot`

| 字段 | 类型 | 语义 |
|---|---|---|
| `id` | `UUID` | 计划内稳定槽位 ID |
| `planId` | `UUID` | 所属 `MedicationPlan.id` |
| `localTime` | `LocalTime` | 当前 `timeOfDay` 元素的业务语义 |
| `position` | `Int` | 保留当前列表顺序，并允许历史数据中出现重复时间 |

`ScheduledDoseSlot` 不包含 Tracked Date、提醒状态、库存或 Wear 字段。计划周期仍由 `MedicationPlan.scheduleType`、`daysOfWeek` 和 `intervalDays` 决定。

### 6.5 目标 `MedicationPlan`

目标文件为 `app/src/main/java/io/github/yuninggu/evolune/core/model/MedicationPlan.kt`，目标类型为纯 Kotlin Domain model。字段正式锁定为：

| 字段 | 类型 | 规则 |
|---|---|---|
| `id` | `UUID` | 计划稳定身份，不由 mapper 重写 |
| `name` | `String` | 保留当前值，不新增兼容性校验 |
| `route` | 当前暂时复用 `pk.Route` | Batch 3 不搬迁 Route |
| `ester` | 当前暂时复用 `pk.Ester` | Batch 3 不搬迁 Ester |
| `doseMG` | `Double` | 保留当前值，不新增兼容性校验 |
| `scheduleType` | `core.model.ScheduleType` | 不依赖旧内嵌枚举 |
| `slots` | `List<ScheduledDoseSlot>` | 替代 `timeOfDay`，列表顺序是权威业务顺序 |
| `daysOfWeek` | `Set<DayOfWeek>` | WEEKLY 使用；其他类型保留 legacy 值 |
| `intervalDays` | `Int` | 合法范围为 `1..Int.MAX_VALUE` |
| `isEnabled` | `Boolean` | 保留当前启停语义 |
| `extras` | `Map<core.model.ExtraKey, Double>` | 使用显式键映射，不使用 ordinal |
| `createdAt` | `Instant` | Entity mapper 在边界转换 epoch millis |

`getDescription()` 和其他显示格式化方法不进入 Domain。`timeOfDay` 不进入目标模型；兼容读取只允许在 mapper 或 adapter 中通过 `slots.map { it.localTime }` 生成，不改变 Domain 的权威字段。

### 6.6 `core.model.ScheduleType`

新增独立枚举，值固定为 `DAILY`、`WEEKLY`、`CUSTOM`。新 Domain contract 不依赖当前 `data.MedicationPlan.ScheduleType` 内嵌枚举；旧枚举与新枚举之间必须由 Batch 3B 使用显式 `when` 映射，禁止 ordinal 映射。

`MedicationPlan` 不变量：

1. 每个 `slot.planId` 必须等于 `MedicationPlan.id`。
2. `slots` 的列表顺序是权威业务顺序；不自动排序。
3. 每个 `slot.position` 必须等于其列表索引。
4. position 必须从 0 开始、连续且唯一。
5. 允许多个 slot 使用相同 `localTime`。
6. 允许空 `slots`。
7. 不自动重编号、不去重、不修正非法输入；planId、position 或时间精度错误必须显式失败。
8. `intervalDays` 必须在 `1..Int.MAX_VALUE`。
9. `DAILY` 调度忽略 `daysOfWeek` 和 `intervalDays`；`WEEKLY` 使用 `daysOfWeek`，空集合表示不产生 occurrence；`CUSTOM` 使用 `intervalDays` 并忽略 `daysOfWeek`。
10. irrelevant 字段保留 legacy 值，不在构造时清空或标准化。

上述规则不改变当前 Predictor、Reminder 或 Widget 行为；这些入口仍在后续批次通过 adapter 迁移。

## 7. 时间字段语义

| 字段 | 权威性 | 规则 |
|---|---|---|
| `occurredAt` | 权威 instant | 排序、范围查询、PK 和跨设备事件时间均以此为准 |
| `zoneId` | 可选上下文 | 仅在入口明确知道时写入；不得为旧数据库或 JSON v1 伪造原始时区 |
| `localDate` | 可选日历语义 | 新手机本地记录可在保存时从用户选择的日期保留；不因设备换时区自动重写 |
| `slotId` | 可选计划关联 | 只有能证明来自某稳定槽位时填写；相似药物和 ±1 小时匹配不能反向补写 |
| `source` | 必填来源 | v2 迁移为 `LEGACY`；新入口按实际来源写入 |
| `status` | 必填状态 | Phase 1 全部为 `RECORDED` |
| `revision` | 必填行版本 | v2 迁移和新建均为 1；有意义的编辑 +1；重复的相同幂等命令不递增 |

### 7.1 时区和 DST

1. 真实事件先确定 instant，再记录可用的 zone/localDate 元数据；修改时区不能改变 `occurredAt`。
2. 现有计划预测使用 `LocalDateTime.atZone(ZoneId.systemDefault())`。Phase 1 先锁定该行为：DST gap 按 Java time 当前规则向后调整到有效 instant；DST overlap 使用 `atZone` 的默认较早 offset。
3. Phase 1 不给 `MedicationPlan` 新增固定时区，因为当前产品没有“计划跟随创建地时区”或“跟随设备时区”的选择。
4. v2 旧事件没有原始 zone/localDate；迁移后保持 null。UI 在这些字段为空时继续按设备当前时区显示，以保持现状。
5. 历史事件和新事件的 UI 均继续按当前设备时区显示；新事件在记录时仍保存当时的 `zoneId`。`zoneId` 不改变 Phase 1 的显示规则，`localDate` 只用于稳定保存记录时的日历语义和未来查询。

## 8. 目标 Entity 与 Room aggregate

### 8.1 `DoseEventEntity` 目标字段

保留现有 6 列，并 additive 增加：

| 新列 | SQLite 类型 | null/default | 说明 |
|---|---|---|---|
| `occurredAtEpochMillis` | `INTEGER` | `NOT NULL DEFAULT 0`，随后回填 | `Instant.toEpochMilli()` |
| `zoneId` | `TEXT` | nullable | `ZoneId.id`，旧数据未知 |
| `localDate` | `TEXT` | nullable | ISO `yyyy-MM-dd`，旧数据未知 |
| `slotId` | `TEXT` | nullable | 稳定槽位 UUID；旧数据不猜测 |
| `source` | `TEXT` | `NOT NULL DEFAULT 'LEGACY'` | enum name |
| `status` | `TEXT` | `NOT NULL DEFAULT 'RECORDED'` | Phase 1 唯一状态 |
| `revision` | `INTEGER` | `NOT NULL DEFAULT 1` | 行版本 |

旧 `timeH REAL NOT NULL` 在 Phase 1 保留并双写。Entity 是数据库细节，不直接暴露给 feature、Wear 或 Widget。

### 8.2 `MedicationPlanEntity`

目标 schema 版本继续保留当前 12 列，包括 `timeOfDay`。该列在兼容窗口内作为 rollback shadow 双写，不立即删除。

### 8.3 `ScheduledDoseSlotEntity`

新增表 `scheduled_dose_slots`：

| 列 | SQLite 类型 | 约束 |
|---|---|---|
| `id` | `TEXT NOT NULL` | 主键 |
| `planId` | `TEXT NOT NULL` | 外键到 `medication_plans.id`，删除计划时 cascade |
| `localTime` | `TEXT NOT NULL` | 规范 ISO local time |
| `position` | `INTEGER NOT NULL` | 计划内显示/计算顺序 |

索引：`planId` 普通索引；`(planId, position)` 唯一索引。`localTime` 不唯一，以保留当前可能存在的重复时间值。

Room 读取计划时使用一个只属于 persistence 层的 aggregate（例如 plan entity + slot entities）；该 aggregate 不是 Domain 或 External DTO。

## 9. Repository contract

contract 放入 `app` 内新的逻辑 package，例如 `io.github.yuninggu.evolune.core.dataapi`，只依赖纯 Kotlin Domain model 和 `Flow`，不暴露 DAO、Entity、Room 或 Android `Context`。

### 9.1 `DoseEventRepository` contract

| 能力 | 签名形状与语义 |
|---|---|
| `observeAll()` | `Flow<List<DoseEvent>>`；按 `occurredAt` 倒序 |
| `getById(id)` | `suspend`；按稳定 `UUID` 获取，缺失返回 null |
| `findOccurredBetween(startInclusive, endExclusive)` | `suspend`；参数为 `Instant`；使用 `[startInclusive, endExclusive)`，按 `occurredAt` 升序 |
| `getEventsForPk(asOf)` | `suspend`；参数为 `Instant`；冻结当前 30 天/20 条选择逻辑以及两个分支各自的现有返回顺序，Batch 3 不统一排序 |
| `insert(event)` | `suspend`；返回 `InsertResult` |
| `update(event, expectedRevision)` | `suspend`；返回 `UpdateResult` |
| `delete(id)` | `suspend`；保持物理删除，返回 `DeleteResult` |
| `deleteAll()` | `suspend`；返回 `DeleteResult`；维护能力，不暴露给普通 UI |

### 9.2 `MedicationPlanRepository` contract

| 能力 | 签名形状与语义 |
|---|---|
| `observeAll()` | `Flow<List<MedicationPlan>>`；保持当前 `createdAt` 倒序 |
| `observeEnabled()` | `Flow<List<MedicationPlan>>`；只返回启用计划，保持当前 `createdAt` 倒序 |
| `getById(id)` | `suspend`；按稳定 `UUID` 获取，缺失返回 null |
| `save(plan)` | `suspend`；返回 `PlanSaveResult`；语义是计划和全部 slots 的原子 aggregate 保存 |
| `setEnabled(id, enabled)` | `suspend`；返回 `PlanUpdateResult` |
| `delete(id)` | `suspend`；保持物理删除，返回 `DeleteResult` |
| `deleteAll()` | `suspend`；返回 `DeleteResult`；维护能力，不暴露给普通 UI |

v2 没有 slots 表，因此 Batch 3 不实现 `save(plan)` 的生产写入路径。实际 plan + slots transaction implementation 等待 Batch 4 v3 schema、Entity 和 DAO 完成。Phase 1 不给 `MedicationPlan` 增加 revision 或并发版本字段。

### 9.3 Repository 业务结果

正常业务结果使用纯 Kotlin sealed result，不使用 Boolean 混合语义，也不把异常文本作为协议。具体文件拆分可以在 Batch 3A 按以下固定集合实现，但不得改变成员和含义：

- `InsertResult`：`Inserted`、`Idempotent`、`Conflict`、`Invalid`。
- `UpdateResult`：`Updated`、`NoChange`、`NotFound`、`RevisionConflict`、`Invalid`。
- `DeleteResult`：`Deleted`、`NotFound`。
- `PlanSaveResult`：`Created`、`Updated`、`NoChange`、`Invalid`。
- `PlanUpdateResult`：`Updated`、`NoChange`、`NotFound`、`Invalid`。

事件 insert 规则：新 ID 返回 `Inserted`；同 ID 且业务内容相同返回 `Idempotent`，不新增第二行；同 ID 但业务内容不同返回 `Conflict`，不得覆盖；Domain 或 mapping 校验失败返回 `Invalid`。

事件 update 规则：ID 存在、`expectedRevision` 匹配且业务内容有意义地变化时 revision +1 并返回 `Updated`；内容完全相同返回 `NoChange` 且 revision 不递增；ID 不存在返回 `NotFound`；revision 不匹配返回 `RevisionConflict`；输入或 mapping 非法返回 `Invalid`。

计划 save 规则：新 aggregate 返回 `Created`；已有 aggregate 内容变化返回 `Updated`；内容相同返回 `NoChange`；输入或 mapping 非法返回 `Invalid`。`setEnabled` 使用 `PlanUpdateResult` 的对应语义。

Room 打开失败、transaction 失败和不可恢复的 I/O/数据库故障继续作为异常；幂等、冲突、未找到和 revision mismatch 是正常业务结果。

Repository contract 不负责 JSON、Wear payload、UI 文案或 PK 参数。JSON 导入、提醒动作、Widget/Wear 动作通过 Use Case 调用 contract；这些 Use Case 可以在 Phase 1 先保留在 `app` 内。

## 10. 模型映射

| 边界 | 时间表示 | ID/版本 | 映射规则 |
|---|---|---|---|
| Entity | `occurredAtEpochMillis: Long` + legacy `timeH` | TEXT UUID、revision Long | Room mapper 校验 enum、JSON extras 和时间 |
| Domain | `Instant`、可选 `ZoneId`/`LocalDate` | `UUID`、source/status/revision | 业务事实来源；不含 Room 注解 |
| PK input | 现有 `pk.DoseEvent.timeH: Double` | 现有 UUID | `occurredAt.toEpochMilli() / 3_600_000.0`；route/dose/ester/extras 原样复制 |
| External JSON v1 DTO | `timeH: Double` | 字符串 UUID，可缺失/损坏 | 由专用 v1 adapter 转 Domain；不直接复用 Entity/Domain 序列化 |
| Wear DTO | Phase 1 不改当前 payload | 当前 action ID 保持 | 只要求未来 DTO 能携带 event/slot/revision；本阶段现有 `recorded_at` 转 Domain |
| UI model | 格式化后的日期/时间、可选来源标签 | 字符串 key | 由 Domain + 显示 ZoneId 生成；UI 不读取 Entity |

Batch 3 的 Room v2 mapper policy 正式锁定为只读：

1. 只提供 `DoseEventEntity v2 -> core.model.DoseEvent`、`MedicationPlanEntity v2 -> core.model.MedicationPlan`、纯枚举/ExtraKey 显式映射和必要的只读 legacy adapter。
2. `DoseEventEntity` legacy 映射使用 `LegacyTimeAdapter`；成功后 `zoneId`、`localDate`、`slotId` 为 null，`source=LEGACY`、`status=RECORDED`、`revision=1`。
3. `MedicationPlanEntity.timeOfDay` 按原列表顺序生成 slots；position 等于原列表索引，slot ID 使用第 17.1 节 UUIDv5 规范。重复时间和空列表必须原样保留。
4. ScheduleType 和 ExtraKey 使用穷尽 `when` 映射；禁止 ordinal。未知存储字符串、非法 route/ester、损坏 extras、非法 timeH、非法 LocalTime 或 Slot ID 生成失败必须返回明确 mapping failure，不得跳过或替换。
5. Batch 3 不提供通用 `Domain -> Room v2 Entity` mapper。v2 无法保存 `zoneId`、`localDate`、`slotId`、`source`、`status`、`revision` 和 `ScheduledDoseSlot.id`，不得 best-effort、lossy 或静默丢字段写回。
6. 完整双向 Domain/Entity mapper 等待 Batch 4 v3 Entity 完成后实施；Batch 3 不接入新的 Repository 生产写路径。
7. `Instant.toEpochMilli()` 的可持久化范围在 persistence mapper 边界捕获 `ArithmeticException` 并返回明确 mapping error；Domain 构造函数不加入数据库范围限制。

`SimulationResult.timeH` 和图表坐标继续属于 PK/UI 数值模型，不因 Domain 改用 `Instant` 而删除。

## 11. Phase 1 字段范围

### 本阶段进入

- `occurredAtEpochMillis`
- nullable `zoneId`
- nullable `localDate`
- nullable `slotId`
- `source`
- `status`，仅 `RECORDED`
- `revision`
- `ScheduledDoseSlot` 和 `scheduled_dose_slots`
- `core.model.MedicationPlan` 和 `core.model.ScheduleType`
- Repository contract 和 Room implementation 边界
- v2/v3 schema 导出与 migration test
- JSON v1 adapter 和 PK adapter

### 明确推迟

- Tracked Date 全部模型和 schema
- skip/undo/soft-delete 状态模型
- Health Connect 来源 token/provider DTO
- Wear envelope、ack、checksum、协议版本和离线队列
- Widget snapshot/Glance 状态
- WorkManager
- 云同步 revision/vector clock/conflict model
- 库存、血液检测、日记和其他当前不存在功能
- MedicationPlan 固定时区产品能力
- 删除 `timeH` 或 `medication_plans.timeOfDay`
- 全量 Gradle 多模块拆分

## 12. Room v2 到 v3 additive migration

目标数据库版本确定为 v3，作为已固定 v2 schema baseline 之后的首个 additive migration 版本；v2 和 v3 schema 均由 Room 自动导出并纳入版本控制。

`MIGRATION_2_3` 必须按以下顺序执行：

### Phase 1：DDL

在 Room/SQLiteOpenHelper 提供的外层 upgrade transaction 中：

1. 对 `dose_events` 使用 `ALTER TABLE ADD COLUMN` 添加 7 个新列。
2. 创建 `scheduled_dose_slots` 表。
3. 创建 `planId` index。
4. 创建唯一的 `planId/position` index。

Migration 不得显式调用 `beginTransaction` 或 `commit`，不得修改 `user_version`，不得使用 destructive migration，也不得删除表重建后只复制部分列。

### Phase 2：全量预检

DDL 执行后、任何数据回填 `UPDATE` 或 `INSERT` 前：

1. 按稳定顺序读取并验证全部 `dose_events`。
2. 检查每行 `timeH` 的 SQLite storage class。
3. 通过 `MigrationPrimitives` 计算全部 `occurredAtEpochMillis`。
4. 按稳定顺序读取并验证全部 `medication_plans.timeOfDay` 原始字符串。
5. 通过 `LegacyPlanTimeParser` 生成全部 slot rows，保留原列表顺序、空列表和重复时间。
6. 将全部验证结果保存在内存中，本阶段不执行任何数据回填。

“全量预检”精确定义为：在任何数据回填 `UPDATE` 或 `INSERT` 前验证全部 legacy 数据，而不是在 schema 变更前验证。DDL 可以先在外层 upgrade transaction 中执行；任一预检异常必须向上抛出，不得吞掉或转换为成功，由外层 transaction 一并回滚 DDL、数据变化和 `user_version`。该原子回滚行为必须由 connected instrumentation 测试证明，不能只依赖文档推断。

### Phase 3：回填

只有全部预检成功后才能：

1. 使用预检结果逐行更新 `occurredAtEpochMillis`，每个 `UPDATE` 必须恰好影响一行。
2. 按预检保留的顺序插入 `scheduled_dose_slots`，并校验每个 `INSERT` 成功。
3. 让 legacy 行的 `source='LEGACY'`、`status='RECORDED'`、`revision=1` 使用与 Entity 完全一致的 DDL defaults；zoneId/localDate/slotId 保持 null。
4. backfill slot ID 严格使用第 17.1 节 Slot ID v1 规范及固定 UUIDv5 测试向量。
5. 不修改原 `timeH` 或 `medication_plans.timeOfDay`。
6. 不通过药物、时间或 ±1 小时规则推断旧 event 的 `slotId`。

### Phase 4：后置验证

1. event 行数不变，且每个 `occurredAtEpochMillis` 与预检结果一致。
2. `source`、`status`、`revision` 正确，nullable metadata 为 null。
3. slot 数量等于预检结果，无 orphan，且同一计划内 position 唯一。
4. 原 `timeH` 和 `timeOfDay` 未改变。
5. `foreign_key_check` 无错误。
6. 让 Room 校验最终 schema，并输出 v3 schema JSON。

以上四个阶段必须全部位于 SQLiteOpenHelper 提供的同一个外层 upgrade transaction 中。任何 DDL、预检、回填、插入或后置验证失败都必须整体回滚，数据库 `user_version` 保持 2，且不得留下部分新增列、索引、slot 行或数据更新。

`occurredAtEpochMillis DEFAULT 0` 只用于 `ALTER TABLE` 与 Room schema 兼容，不是运行时业务默认值。Batch 4A 的兼容 `DoseEventEntity` 构造必须调用共享的严格 persistence helper，由合法 `timeH` 计算 `occurredAtEpochMillis`；Room 创建的新 Entity 不得把 0 绑定为实际事件时间。兼容构造暂时写入 `zoneId=null`、`localDate=null`、`slotId=null`、`source='LEGACY'`、`status='RECORDED'`、`revision=1`。非法 `timeH` 必须明确失败，不得回退到 SQL 默认值。

### 12.1 历史 `timeOfDay` 兼容规则（B1）— Resolved

1. 读取现有 `medication_plans.timeOfDay` JSON 时，严格保留列表顺序和重复值；SQL 空字符串与 JSON 空数组 `[]` 均解释为空列表。
2. 每个元素按 ISO `LocalTime` 语义解析。`HH:mm`、`HH:mm:00` 以及 second 和 nano 均为 0 的等效 ISO 表达可迁移。
3. 合法值只在新建 slot 的 `localTime` 中 canonicalize 为 `HH:mm`；原 `medication_plans.timeOfDay` 字符串逐字保持不变。
4. second 或 nano 任一非零时，整个 migration 中止；不得截断、舍入、跳过 slot、修改旧列或自动修复。
5. 错误必须包含 `planId`、零基 `position`、原始字符串和失败原因。
6. Slot ID v1 的 namespace、canonical name、UTF-8 编码和固定测试向量保持不变；非分钟值不得通过重新解释 v1 算法迁移。
7. 非分钟值只能由 Batch 4C 工具根据显式 correction manifest 在数据库副本中修复。

当前 v2 合成 fixture 中的 `20:30:15` 固定为正式负向迁移用例和 Batch 4A-1 提交阻断条件。connected instrumentation 必须证明：migration 抛出异常；数据库可由 v2 raw helper 重新打开；`PRAGMA user_version = 2`；`dose_events` 不存在七个新列；`scheduled_dose_slots` 和两个新索引不存在；原 `dose_events` 数据及 `medication_plans.timeOfDay` 原始字符串不变；没有部分 `UPDATE` 或 `INSERT`。

## 13. `timeH` 转换、舍入和容差

### 13.1 统一公式

旧值到 instant：

```text
scaledMillis = timeH * 3_600_000.0
occurredAtEpochMillis = Math.round(scaledMillis)
occurredAt = Instant.ofEpochMilli(occurredAtEpochMillis)
```

instant 到兼容 `timeH`：

```text
timeH = occurredAtEpochMillis / 3_600_000.0
```

迁移、JSON v1 adapter、PK adapter、Wear/Widget/提醒入口必须调用同一个纯 Kotlin 转换器，不得各自复制常量和舍入逻辑。

### 13.2 校验、容差与 storage class（B3）— Resolved

- `timeH` 必须 finite，乘法结果也必须 finite 且在 `Long` 范围内；Phase 1 不额外设置主观的历史/未来日期阈值。
- 对当前由 epoch millis 生成的数据，要求 `Math.round(reconstructedTimeH * 3_600_000.0)` 恢复原 millis。
- 对任意合法 JSON v1 Double，允许的时间量化误差不超过 1 ms，即 `2.7777778e-7` 小时。
- 数据库迁移后旧 `timeH` 列逐位不变。
- PK 回归继续使用现有 `1e-6` 输出容差；不得通过放宽测试掩盖时间转换差异。
- migration 读取 `timeH` 时先检查 `Cursor.isNull` 和 `Cursor.getType`。只有 `FIELD_TYPE_INTEGER` 与 `FIELD_TYPE_FLOAT` 合法；`FIELD_TYPE_STRING`、`FIELD_TYPE_BLOB`、`FIELD_TYPE_NULL` 和 `isNull=true` 均明确失败。
- 只有 storage class 校验通过后才可调用 `getDouble`，随后统一交给 `LegacyTimeAdapter`；不得由 migration 复制或弱化转换规则。
- NaN、positive infinity、negative infinity、乘法溢出或超出 `Long` 范围必须使 migration transaction 中止，并使 migration test 和发布检查失败；不得 clamp、删除异常记录、替换为 0 或当前时间，也不得更新原 `timeH`。
- 全部 event 和 plan 输入必须在任何回填 `UPDATE` 或 slot `INSERT` 前验证完毕；任一失败由外层 SQLiteOpenHelper upgrade transaction 回滚。
- exact v2 `NOT NULL` 表若无法可靠保存 NaN，不得声称设备迁移已覆盖该情形。NaN 必须由 `LegacyTimeAdapter` JVM test 覆盖；instrumentation 只记录 Android/SQLite 的实际表示能力。NULL 和异常 storage class 仍由真实 migration failure tests 覆盖。

### 13.3 `repair-v2` 显式修复工具（B4）— Resolved

Batch 4C 在 `tools/repair-v2/` 实现 Python 3.12-compatible 标准库工具，只允许使用 `sqlite3`、`argparse`、`json`、`hashlib`、`pathlib`、`shutil` 和 `datetime`；不引入 SQLite JDBC、Gradle module 或第三方依赖。

- `scan`：默认只读，检查非法 `timeH` 与非分钟 `timeOfDay`，只输出计数和最小必要诊断。
- `repair`：必须提供显式 JSON correction manifest；输入数据库只读，输出到不同的新路径，禁止覆盖输入或原地修改。
- `verify`：重新扫描输出副本；只有不存在阻断数据时才成功。
- manifest 使用稳定 ID 显式映射 `event ID -> replacement timeH` 和 `plan ID -> replacement timeOfDay list`。
- 禁止猜测时间、截断秒/纳秒、使用当前时间、根据相邻记录或时区推断，以及无 manifest 自动修复。
- JSONL 审计记录工具版本、输入/输出 SHA-256、dry-run/apply 模式、修复数和失败数，不记录完整行内容。
- 仓库只提交工具源码、README、合成 fixture 和单元测试；真实数据库及副本、用户 manifest 和审计日志不得提交。

Batch 4C 不阻塞 4A/4B 的技术实施，但阻塞完整 Batch 4 的最终验收。该工具不进入 Android 运行时，也不替代 migration 的严格失败策略。

## 14. 旧列保留和删除时机

Phase 1 全程保留并双写：

- `dose_events.timeH`
- `medication_plans.timeOfDay`

新读取路径切换到 `occurredAtEpochMillis` 和 `scheduled_dose_slots` 后，旧列至少跨一个正式发布周期保留；本决议将该兼容窗口标记为 `1.0`。删除只能在窗口结束后的独立 schema 设计中提出，并同时满足：

1. 所有生产调用不再读取旧列。
2. 双写一致性遥测/测试没有差异。
3. JSON v1 和 PK 已只依赖独立 adapter。
4. 从最后一个含旧列的发布升级测试通过。
5. 项目所有者明确结束 rollback shadow 窗口。

保留旧列不能让旧 v2 APK 直接打开 v3 数据库；Room 数据库版本已升级，二进制降级仍不受支持。它只支持在 v3 schema 上回滚读取路径。

## 15. JSON v1 继续读取策略

1. 建立专用 `MahiroEventV1Dto`/adapter；Domain 和 Entity 不直接按 v1 字段序列化。
2. parser 继续接受当前顶层 `weight` 和 `events`，忽略当前未消费的 `labResults`、`doseTemplates`。
3. Phase 1 继续保持 parser 不按 `meta.version` 分支的现有行为；字段可按 v1 解析时照常读取，缺失或其他版本值不会触发新的格式推断。
4. `timeH` 使用第 13 节转换；Domain source 为 `JSON_V1`，zoneId/localDate/slotId 为 null。
5. 有效 UUID 原样保留，因此重复导入同一文件继续命中同一主键。
6. 未提供或损坏 UUID 继续生成随机 UUID。Phase 1 明确不引入内容派生 ID；因此这类事件重复导入时仍可能产生新记录，这是已接受的 JSON v1 兼容行为。
7. 未知 route/ester 或缺少 timeH/doseMG 的事件继续跳过；整体非法 JSON 继续失败。
8. v1 导出继续写 `meta.version=1`、`timeH`、现有 route/extras 键和空 `labResults`/`doseTemplates`。
9. 对 v2 迁移行，兼容窗口内可直接使用保留的原始 `timeH` 写 v1；新事件使用统一公式生成。

## 16. PK adapter 与数值回归

PK 层继续接收现有 `pk.DoseEvent` 和绝对小时坐标。Domain 到 PK 的 adapter：

- id、route、doseMG、ester、extras 原样映射。
- `timeH = occurredAt.toEpochMilli() / 3_600_000.0`。
- source、status、revision、zoneId、localDate、slotId 不进入 PK 参数解析。
- 只有 `status=RECORDED` 的实际事件进入历史 PK 输入。
- antiandrogen 过滤、预测事件合并、1 小时冲突窗口和贴片移除行为保持当前调用顺序。

回归测试必须固定一组当前 v2/JSON v1 fixture，在迁移前记录以下结果，迁移后用相同体重、范围和步数比较：

- 输入事件顺序与 `timeH`
- `SimulationResult.timeH`
- 每个采样点 `concPGmL`
- `auc`
- 指定时刻 `concentration()`
- 贴片应用/移除、舌下、口服、凝胶和各注射 ester

验收：参数测试原样通过；时间数组相同；浓度和 AUC 绝对差不超过现有 `1e-6`。任何差异先判断是否来自排序或量化，不能修改 PK 参数补偿。

## 17. 稳定 ID 与重复事件

当前事实：

- 手动新建使用随机 UUID，编辑复用原 ID。
- 通知使用 `reminder:{planId}:{scheduledAtMillis}` 的确定性 UUID，重复广播幂等。
- Wear 使用 action ID 作为事件 ID，重复 delivery 幂等。
- Widget 使用 plan ID + 记录分钟生成确定性 UUID，同一分钟重复点击幂等。
- JSON v1 保留有效 ID；缺失/损坏 ID 随机生成。
- 预测事件当前每次生成随机 UUID，但不持久化。

Phase 1 策略：

1. 持久化 `DoseEvent.id` 继续使用现有 UUID，不重写历史 ID。
2. `ScheduledDoseSlot.id` 在迁移时确定性生成；新槽使用随机 UUID，编辑时间不改变已有 slot ID。
3. 计划 occurrence 的稳定业务键为 `(slotId, scheduledAtInstant)`；预测事件不依赖临时随机 ID 做去重。
4. Repository `insert` 遇到同 ID、同业务内容时返回幂等成功；同 ID、不同内容返回 conflict，不能无提示覆盖。
5. 手动编辑使用 `expectedRevision`；成功后 revision +1。
6. 迁移不通过相似药物、剂量或时间窗口合并现有事件。

### 17.1 ScheduledDoseSlot ID v1 — Resolved

Slot ID v1 是版本化、确定性的领域标识。它不是数据库行 ID 的通用替代物，也不是 `DoseEvent.id`。本规范一经发布不得重新解释；未来不兼容变更必须使用新的版本前缀或新的 namespace，并定义 Slot ID v2。

#### UUIDv5 算法与 namespace

- 使用标准 name-based UUID version 5，摘要算法为 SHA-1，UUID variant 为 RFC 标准 variant。
- SHA-1 仅用于生成稳定领域标识，不用于密码学安全、签名、认证或数据完整性校验。
- 输出使用 `UUID.toString()`，即小写、带连字符的标准 UUID 字符串。
- 根 namespace 使用标准 DNS namespace `6ba7b810-9dad-11d1-80b4-00c04fd430c8`。
- 项目 namespace 名称是 UTF-8 编码的 `io.github.yuninggu.evolune:scheduled-dose-slot`。
- `projectSlotNamespace = UUIDv5(DNS_NAMESPACE, UTF8("io.github.yuninggu.evolune:scheduled-dose-slot"))`。
- 固定项目 namespace 为 `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`，一经发布不得改变。

禁止使用 `UUID.randomUUID()`、`String.hashCode()`、平台默认 charset、Locale 相关格式、随机 salt、设备 ID、当前时间或数据库自增 ID。

#### 输入规范化

`planId`：

1. 最终输入类型表达为 UUID。
2. 字符串输入不得包含首尾空白；不得通过 trim 后继续接受。
3. 字符串必须能被 UUID 解析，并规范化为 `UUID.toString()` 的小写带连字符格式。
4. 非法输入返回明确错误，不得替换为随机 UUID 或空 UUID。

`position`：

1. 表示同一计划中时间槽的零基索引，合法范围是 `0..Int.MAX_VALUE`。
2. canonical 形式是无正号、无前导零的十进制 ASCII；数字零仅表示为 `0`。
3. 负数非法；`+1`、`01` 等非 canonical 字符串输入非法；格式不受 Locale 影响。

`localTime`：

1. 使用 `java.time.LocalTime`，Phase 1 只允许分钟精度。
2. `second` 和 `nano` 必须均为 0；非分钟精度输入返回明确错误，不得静默截断。
3. canonical 格式固定为 24 小时制 `HH:mm`，始终为 5 个 ASCII 字符，使用 `Locale.ROOT` 或等效非本地化实现。
4. canonical 值不包含日期、时区或 offset。合法示例为 `00:00`、`08:30`、`23:59`；`08:30:01` 和 `08:30:00.500` 非法。

#### Canonical name 与生成规则

canonical name 必须精确为：

```text
slot:v1:plan=<canonicalPlanUuid>;position=<canonicalPosition>;time=<canonicalLocalTime>
```

字段顺序固定。版本后使用冒号，字段之间使用分号，key 与 value 之间使用等号。整个 canonical name 明确使用 UTF-8 编码。三个 value 已有严格 ASCII 格式，不执行 URL encoding、JSON escaping、Unicode normalization 或其他二次转换。

最终 ID：

```text
slotId = UUIDv5(projectSlotNamespace, UTF8(canonicalName))
```

#### 固定测试向量

```text
planId: 00000000-0000-0000-0000-000000000001
position: 0
localTime: 08:30
canonicalName: slot:v1:plan=00000000-0000-0000-0000-000000000001;position=0;time=08:30
projectSlotNamespace: 68559b97-4ddc-5be2-bcbd-9ab409f0d95b
slotId: 17d1fd14-9d70-5344-beaa-0b158c9f62f4
```

该向量已使用 Python 标准库 `uuid.uuid5` 独立验证；纳入版本控制后不得在 v1 中改变。

#### 错误与身份语义

以下情况必须返回明确错误：planId 不是有效 UUID、planId 含首尾空白、position 小于 0、localTime 的 second 非零、localTime 的 nano 非零、UUIDv5 输入构建失败。具体 Kotlin 错误类型名称在实现阶段按统一 Result 设计确定，但不得返回随机 ID、静默 null、数据库自增回退或自动修正输入，也不得读取系统时间、Locale 或时区。

Slot 身份由 `planId`、`position` 和 `canonicalLocalTime` 共同决定，任一变化都会改变 Slot ID。同一计划中相同 localTime 可由不同 position 区分。剂量、药物名称、route、ester 和启用状态不属于 Slot 身份，改变这些字段不得改变 Slot ID。调整时间列表顺序会改变 position，因此可能改变 Slot ID。

## 18. Schema 导出与 migration test

### 18.1 Schema 目录

确定目录：

```text
app/schemas/io.github.yuninggu.evolune.data.AppDatabase/2.json
app/schemas/io.github.yuninggu.evolune.data.AppDatabase/3.json
```

先在数据库仍为 v2 时开启 `exportSchema=true` 并配置 KSP schema location，生成和人工核对 `2.json`；确认其与 Entity 和 `MIGRATION_1_2` DDL 一致后再编写 v3。禁止手写一个未经 Room 验证的 v2 baseline 冒充导出结果。

仓库当前没有可信的 v1 schema JSON。除非能从历史构建或提交恢复，不创建伪造的 `1.json`；v1 链路测试使用按当前 `MIGRATION_1_2` 前置条件建立的最小 `dose_events` SQL fixture，并在进入 v2 后用导出的 `2.json` 校验。

### 18.2 测试位置与依赖

- 增加 Room testing 依赖。
- 将 `app/schemas` 加入 androidTest assets。
- migration test 放在 `app/src/androidTest/java/io/github/yuninggu/evolune/data/AppDatabaseMigrationTest.kt`。
- 纯转换、slot ID、Domain mapper 和 PK adapter 测试放在 `app/src/test/...`。
- 公共仓库中的 migration fixture 必须是合成数据，不得由真实健康数据库样本直接或间接替代；fixture 生成过程应固定种子并可重复。
- 合成 fixture 必须主动覆盖跨时区、DST 边界、贴片 apply/remove、舌下 tier、未来事件和数千条长历史，而不是依赖真实样本提供覆盖率。
- 只有合成 fixture 无法复现具体问题时，才允许在获得知情同意后，对测试设备数据库进行不可逆脱敏并放入仅本地 migration test 环境。原始样本、脱敏样本和包含行数据的日志均不得提交或上传到公共仓库。

### 18.3 必测矩阵

1. v2 空数据库 -> v3。
2. v2 含每种 route/ester/extras 的事件 -> v3。
3. `timeH` 的 INTEGER/FLOAT storage class、整数值、小数值、真实 epoch、分钟对齐和毫秒精度。
4. `timeH` 为 NULL、STRING、BLOB、NaN、positive/negative infinity、乘法溢出或超出 `Long` 范围时按第 13.2 节失败；只有 Android/SQLite 实际可表示的异常值才声称 instrumentation 覆盖，NaN 始终由 JVM adapter test 覆盖。
5. 计划无槽、单槽、多槽、重复时间、跨午夜时间；SQL 空字符串和 JSON `[]` 均得到空 slots。
6. `HH:mm`、`HH:mm:00` 和 second/nano 均为零的等效 ISO `timeOfDay` 可迁移，slot canonical time 为 `HH:mm`，原字符串不变。
7. `timeOfDay` 原顺序、重复值及 slot position/ID 稳定。
8. 固定使用现有含 `20:30:15` 的 v2 fixture 做负向迁移：migration 抛出异常后，以 v2 raw helper 重新打开并验证 `user_version=2`、七个新列不存在、`scheduled_dose_slots` 与两个新索引不存在、原 event 数据和 `timeOfDay` 字符串不变、无部分 `UPDATE` 或 `INSERT`。
9. 任一 event 或 plan 预检失败都由外层 upgrade transaction 回滚 DDL、数据变化和 `user_version`；该用例必须作为 Batch 4A-1 的 connected instrumentation 提交阻断测试实际执行。
10. 使用明确标记为 test fixture 的 v1 `dose_events` DDL 执行 v1 -> v2 -> v3 完整链；不得把 fixture 冒充历史导出 schema。
11. v3 schema 与 Room 自动导出的 JSON 一致。
12. `scheduled_dose_slots` 的 FK、cascade、唯一索引、顺序和重复时间行为。
13. 新旧列双写一致。
14. DST gap、DST overlap、设备换时区和 legacy zone null。
15. JSON v1 fixture 和导出再导入。
16. 相同 reminder/Wear/Widget ID 重放不产生重复行。
17. revision 更新冲突。
18. 全部现有 PK、提醒、Wear、Widget utility 和 UI 纯 JVM 测试。
19. 固定种子的合成长历史 fixture，覆盖数千条事件、跨时区、DST、贴片配对和舌下 tier。
20. migration matrix 必须在 emulator 或专用测试设备上实际执行；仅编译 androidTest 不等于通过。
21. 本地脱敏 fixture 仅作为非必需补充测试；公共 CI 和 Phase 1 验收不得依赖本地私有样本才能通过。

## 19. 回滚方案

1. 每批独立提交；schema bump 之前的批次可直接回滚。
2. v3 发布后不降低数据库 version，也不启用 destructive downgrade。
3. v3 保留 `timeH`、`timeOfDay` 并双写，可通过 feature flag/adapter 配置临时切回 legacy 读取路径。
4. migration 先在外层 upgrade transaction 中执行 DDL，再在任何数据回填前完成全量预检；任一阶段失败时，Room 必须整体回滚 DDL、数据变化和 `user_version`，保留可由 v2 raw helper 打开的原数据库。该行为必须由 connected instrumentation 证明。
5. 发布前保存并版本化合成 v2 fixture；真实健康数据库和从其派生的脱敏 fixture 不进入仓库。若本地私有 fixture 暴露迁移缺陷，先将缺陷归纳为最小合成回归用例，再修复并停止发布，不能通过清库解决。
6. 若 v3 生产问题需要 APK 回退，应发布“旧行为 + v3 schema 兼容”的修复版本，而不是安装不认识 v3 的旧 APK。
7. 不从 `occurredAt` 反向覆盖历史 `timeH`；旧列始终是 rollback shadow 的原始证据。

### 19.1 v3 内部不可发布区间与发布门槛（B2）— Resolved

从首个包含 Room v3 schema 的提交开始，直到 Phase 1 Batch 8 全部退出验收通过，所有构建均为内部、不可发布的迁移版本。期间禁止创建正式 release、分发生产 APK/AAB、升级用户主数据库，或在包含真实健康数据的设备/数据库上安装和运行。只允许合成 fixture、测试数据库、emulator、使用非真实数据的专用测试设备和本地可丢弃数据库。

进入正式发布至少必须同时满足：

1. v2 -> v3 migration matrix 已在设备或 emulator 实际执行通过。
2. Batch 3C Repository implementation 完成。
3. event 写入正确同步 `timeH`、`occurredAtEpochMillis`、`zoneId`、`localDate`、`slotId`、`source`、`status`、`revision`。
4. plan 保存能在同一 transaction 中同步 `medication_plans.timeOfDay` 与 `scheduled_dose_slots`。
5. Reminder、Widget、Wear、UI 和其他现有入口不再绕过新 Repository contract。
6. JSON v1 导入完成 `source` 与时间字段适配，既有格式和随机 UUID 行为保持不变。
7. PK adapter 回归在 `1e-6` 绝对容差内通过，参数不变。
8. Batch 8 的完整退出验收通过。

Batch 4A 期间现有写路径不会同步 slot 表，且迁移行的 `source`/`slotId` 语义仍处于兼容状态，因此即使构建、schema 和基础 migration tests 通过也不得发布。设备 migration 验证之外，任何真实数据库问题只能在受控本地副本上调查；不得用中间 APK 直接升级用户数据。

## 20. 分批实施顺序、文件和验收

### Batch 0：设计签署

**文件**：`docs/PHASE_1_DESIGN.md`、必要时 `docs/evolune/DECISIONS.md`

**测试/验收**：第 21 节全部决策为 `Resolved`；正文与决策一致；确认 Tracked Date 和其他后续功能仍不进入 Phase 1。本轮完成后停止，不自动开始 Batch 1。

### Batch 1：固定 v2 schema 与行为基线

**涉及文件**：

- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt`
- `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/2.json`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/AppDatabaseMigrationTest.kt`
- `app/src/androidTest/.../fixtures/` 下的固定种子合成 fixture/generator
- 当前 JSON、PK、提醒、Wear 和 Predictor 测试文件

**测试/验收**：数据库仍为 v2；schema 由 Room 生成；现有测试全通过；建立迁移前 PK fixture；无生产行为变化。

### Batch 2：纯 Kotlin 时间和 Domain 类型

**涉及文件**：

- 新增 `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEvent.kt`
- 新增 `DoseEventSource.kt`、`DoseEventStatus.kt`、`ScheduledDoseSlot.kt`
- 新增 `app/src/main/java/io/github/yuninggu/evolune/core/time/LegacyTimeAdapter.kt`
- 对应 `app/src/test/...` 测试

**测试/验收**：不接 Room；公式、1 ms 容差、ZoneId/localDate、DST gap/overlap、slot ID 和 enum 测试通过；PK 参数无变化。

### Batch 3A：Domain aggregate 与 Repository contract

**允许新增文件**：

- `app/src/main/java/io/github/yuninggu/evolune/core/model/MedicationPlan.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/model/ScheduleType.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/DoseEventRepository.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/MedicationPlanRepository.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/RepositoryResults.kt`
- 上述类型对应的 `app/src/test/...` 纯 JVM 测试

**边界**：只定义第 6.5、6.6 和第 9 节已经锁定的纯 Kotlin Domain aggregate、枚举、contract 和业务结果。不得修改 Room Entity、DAO、当前 Repository 实现、composition root 或任何 ViewModel/UI/Reminder/Widget/Wear 调用方；不得把 contract 接入生产路径。

**测试/验收**：验证 MedicationPlan 全部字段和不变量、ScheduleType 三个值、五组 result 的完整成员、Repository 方法集合与半开时间区间语义。测试必须证明 slot 不会被自动排序、重编号或去重；重复时间和空 slots 保持合法。仍可在单一 `app` module 编译，且 core 不新增 Android、Room、Compose 或 Wear 依赖。

### Batch 3B：Room v2 只读 mapper

**允许新增文件**：

- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/DoseEventEntityMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/PersistenceEnumMapper.kt`
- 上述 mapper 对应的 `app/src/test/...` 纯 JVM 测试

**边界**：只实现第 10 节的 v2 Entity -> Domain 读取映射和显式枚举/ExtraKey 映射。不得提供通用 Domain -> v2 Entity mapper，不得修改当前 Repository、DAO、Entity、Room version/schema 或生产调用路径。

**P2 处置与测试/验收**：

1. Domain/PK ExtraKey 之间使用穷尽 `when`，显式覆盖 `CONCENTRATION_MG_ML`、`AREA_CM2`、`RELEASE_RATE_UG_PER_DAY`、`SUBLINGUAL_THETA`、`SUBLINGUAL_TIER`、`ANTI_ANDROGEN_TYPE`；禁止 ordinal，未知持久化值明确失败。
2. persistence mapper 调用 `Instant.toEpochMilli()` 时捕获 `ArithmeticException` 并返回明确 mapping failure；测试覆盖 `Instant.MIN`、`Instant.MAX`，但不把数据库范围限制放入 Domain 构造函数。
3. `core.model` 暂时继续依赖 `pk.Route`/`pk.Ester`；本批不移动枚举、不修改 `SimulationEngine`，并把该依赖记录为后续技术债。
4. MedicationPlan v2 mapper 按 `timeOfDay` 原顺序生成 slots，使用 Slot ID v1；测试覆盖空列表、重复时间、顺序、非法时间和确定性 ID。schema 2 identity hash 和 SHA-256 必须保持 Batch 1 基线值。

### Batch 3C：Repository 实现与生产接线（推迟）

Batch 3C 不并入 Batch 4A。它必须等待 v3 schema、Entity、DAO、migration tests 和 schema validation 就绪后单独实施，届时才允许实现完整无损双向 mapper、Repository contract 的 Room 实现、计划与 slots 的原子 transaction、revision/idempotency/conflict 语义及 composition root 接线。Batch 3A、3B 或 4A-0 的通过均不授权提前实施 3C；3C 仍受第 19.1 节不可发布门槛约束。

### Batch 4A-0：Migration primitives

**只允许实现**：

- `LegacyPlanTimeParser`
- persistence compatibility time helper
- migration error 类型
- 对应纯 JVM tests

本批不得修改 `AppDatabase` version、Entity 或 Room schema，不得生成 `3.json`。验收重点是 B1/B3 的解析、storage class 后续调用契约、错误上下文、`LegacyTimeAdapter` 复用和边界测试。

### Batch 4A-1：Atomic v3 schema implementation

以下内容必须在一个可构建、不可拆分的提交中完成：

- `DoseEventEntity` 七个新字段
- `ScheduledDoseSlotEntity`
- `ScheduledDoseSlotDao`
- 必要 persistence aggregate
- `AppDatabase version = 3`
- `MIGRATION_2_3` 及注册
- Room 自动生成的 `app/schemas/.../3.json`
- 基础 migration instrumentation tests
- 调整 v2 baseline test 以兼容数据库版本升级

基础 instrumentation 必须实际连接 emulator 或专用测试设备，使用含 `20:30:15` 的 v2 fixture 证明预检失败会回滚 DDL、数据变化和 `user_version`；仅编译 androidTest 不足以通过，该 connected rollback test 是 Batch 4A-1 的提交阻断条件。

禁止提交 Entity 已改但 version 仍为 2、version 3 没有 `MIGRATION_2_3`、migration DDL 与 `ColumnInfo.defaultValue` 不一致、Slot Entity 未注册、手写 `3.json`、connected rollback test 未实际通过或生产代码无法构建的中间状态。本批即使通过也处于第 19.1 节内部不可发布区间；现有写路径尚不维护 slot 表。

### Batch 4B：完整 migration matrix

实现并在 emulator 或专用测试设备实际执行第 18.3 节矩阵，至少覆盖非法数据、transaction rollback、slot 顺序与重复值、FK/cascade/unique index、v1 -> v2 -> v3、固定种子长历史、Room schema validation 和 connected instrumentation。androidTest 只编译不算本批通过。

### Batch 4C：Python repair toolkit

按第 13.3 节在 `tools/repair-v2/` 实现 `scan`、`repair`、`verify`、显式 manifest、JSONL 审计、合成 fixture 和单元测试。4C 不阻塞 4A/4B 技术工作，但未通过时 Batch 4 不得完成最终验收。

**Batch 4 总体验收**：4A-0、4A-1、4B 和 4C 全部通过；旧 `timeH`/`timeOfDay` 不变；新 event 时间误差不超过 1 ms；slot 顺序和重复值完整；无 destructive migration。此验收不等于可发布，最终 release 仍必须满足第 19.1 节和 Batch 8 门槛。

### Batch 5：双读双写与计划槽位切换

**涉及文件**：

- Room Repository implementations/mappers
- `MedicationPlan.kt`
- `MedicationPlanPredictor.kt`
- `ReminderManager.kt`
- `MedicationPlanViewModel.kt`
- 计划编辑 UI 文件

**测试/验收**：新旧时间和 slots/timeOfDay 双写一致；计划预测数量、时刻、排序和 DST 行为与基线一致；编辑槽位不改变未编辑槽 ID。

### Batch 6：现有入口改走 contract

**涉及文件**：

- `HRTViewModel.kt`
- 记录 UI 和格式化组件
- reminder receivers/factory/matcher
- `EvoluneWidgetReceiver.kt`、`WidgetUtils.kt`
- 当前 `WearDataLayer.kt`
- composition root（当前 `MainActivity`/导航组装位置）

**测试/验收**：UI、通知、Widget 和 Wear 不直接访问 DAO/Entity；现有 Wear payload 不变；快速记录时间精度、稳定 ID、±1 小时窗口和 Widget 刷新行为不变。

### Batch 7：JSON v1 与 PK adapter 切换

**涉及文件**：

- `MahiroJsonFormat.kt` 和新 External DTO/mapper
- 新 Domain -> PK adapter
- `SimulationEngine` 调用点，不修改算法文件和参数文件
- `MahiroJsonFormatTest.kt`、PK regression tests

**测试/验收**：旧 fixture 继续读取；v1 输出字段不变；有效 UUID 保留；PK 时间数组、浓度和 AUC 在既定容差内；所有参数测试原样通过。

### Batch 8：Phase 1 退出核验

**涉及文件**：文档、测试报告；不删除 legacy 列。

**测试/验收**：

- schema 2/3 已纳入版本控制。
- v1->2->3 和 v2->3 migration test 通过。
- 手机单元测试、debug 构建、lint 通过。
- 相关 instrumentation migration tests 通过。
- Wear/Widget 现有路径回归通过。
- feature、Wear、Widget 无直接 DAO/Entity 依赖。
- Tracked Date、Health Connect、Glance、WorkManager 和云同步没有被引入。
- `timeH` 和 `timeOfDay` 仍在 `1.0` 兼容窗口内保留并双写，未安排删除 migration。
- 公共仓库和 CI 只使用合成 fixture；本地私有 fixture 没有被提交、上传或设为验收前提。

## 21. Resolved 决策记录

以下 20 项均已由项目所有者解决。重新评估条件只允许触发新的设计记录，不会自动改变本文件的 Phase 1 基线。

### D1. 目标 schema 版本 — Resolved

- **最终选择**：使用 v3 作为已固定 v2 schema baseline 之后的首个 additive migration 目标版本。
- **理由**：当前生产模型为 Room v2；从 v2 固定基线后迁移到 v3，能够保持版本链清晰且避免伪造历史 schema。
- **影响范围**：第 12、18、19、20 节；`AppDatabase`、schema 2/3、v1->2->3 和 v2->3 migration test。
- **重新评估条件**：仅当实施前发现仓库外已经存在使用 v3 的可分发数据库，发生版本号冲突时重新设计目标版本。

### D2. Phase 1 package 边界 — Resolved

- **最终选择**：先在 `app` module 内使用 `core.model`、`core.dataapi`、`core.time` 逻辑 package，不立即创建新的 Gradle modules。
- **理由**：先建立依赖方向和 contract，降低 Phase 1 同时迁移数据与重组构建结构的风险。
- **影响范围**：第 6、9、10、20 节；Batch 2 和 Batch 3 的文件位置。
- **重新评估条件**：package 边界稳定、依赖检查通过，且出现明确的独立编译或复用收益时，再单独设计模块化。

### D3. Legacy 时区元数据 — Resolved

- **最终选择**：v2 数据库和 JSON v1 事件的 `zoneId`、`localDate` 保持 null，不用迁移设备时区补写。
- **理由**：旧数据只保存 instant 等价的 `timeH`，无法证明记录时的时区和日历日期；补写会制造虚假历史事实。
- **影响范围**：第 6、7、8、10、12、15、18 节；legacy mapper 和 migration fixture。
- **重新评估条件**：仅当存在可信、逐事件保存且可验证的原始时区来源时，另行设计定向补充流程；不得批量推断。

### D4. 事件显示时区 — Resolved

- **最终选择**：历史事件和新事件均继续按设备当前时区显示；新事件记录时保存当时的 `zoneId`。
- **理由**：保持现有 UI 行为，避免 Phase 1 因显示时区变化造成用户可见回归，同时为未来明确的时区产品能力保留上下文。
- **影响范围**：第 5、7、10、18、20 节；UI mapper、记录入口和时区测试。
- **重新评估条件**：产品提供“按事件时区显示”或“按当前时区显示”的明确交互选择时，单独评估显示规则；不得改变 `occurredAt`。

### D5. 计划时区 — Resolved

- **最终选择**：Phase 1 的计划继续跟随设备当前系统时区，不给 `MedicationPlan` 增加固定时区。
- **理由**：这是当前 `LocalDateTime.atZone(ZoneId.systemDefault())` 的实际行为，Phase 1 目标是迁移而非扩展计划产品语义。
- **影响范围**：第 6、7、11、18、20 节；计划预测、提醒和 Widget 基线。
- **重新评估条件**：产品需要旅行模式、固定创建地时区或逐计划时区设置时，另立功能和迁移设计。

### D6. DST gap/overlap — Resolved

- **最终选择**：保持 Java `LocalDateTime.atZone` 默认行为；gap 向后调整到有效 instant，overlap 使用默认较早 offset。
- **理由**：锁定当前实现语义，避免计划预测、提醒和 Widget 在迁移时产生时刻漂移。
- **影响范围**：第 7、13、18、20 节；纯时间测试和计划预测回归。
- **重新评估条件**：产品要求用户选择 overlap offset，或 Android/Java time 行为发生不兼容变化时，必须先增加显式规则和迁移测试。

### D7. 非法 `timeH` — Resolved

- **最终选择**：NaN、Infinity 或无法表示为 epoch millis 的溢出值使 migration transaction 中止，并使 migration test/发布检查失败；Phase 1 提供独立 CLI 数据修复脚本，不另设主观日期阈值。
- **理由**：静默替换或丢弃会改变健康记录含义；显式失败和可审计修复能够保护原始数据。
- **影响范围**：第 12、13、18、19、20 节；迁移实现、错误报告、CLI 工具及其测试。
- **重新评估条件**：只有形成经项目所有者批准的、不会歧义改变事件时间的数据修复规则时，才可扩展 CLI；应用内自动容错仍需独立决策。

### D8. revision 起点与递增 — Resolved

- **最终选择**：迁移行和新建行均从 `revision=1` 开始；仅有意义的成功编辑递增，幂等重放不递增。
- **理由**：统一新旧数据的版本语义，并为并发冲突检测提供稳定基线。
- **影响范围**：第 6、7、8、9、10、12、17、18、20 节；Repository contract 和 mapper tests。
- **重新评估条件**：未来云同步或多主写入需要 vector clock/服务器版本时，另行设计同步版本；不得复用或重解释本地 revision。

### D9. 重复 ID 冲突 — Resolved

- **最终选择**：同 ID 同业务内容视为幂等成功；同 ID 不同内容返回显式 conflict，不再无条件覆盖。
- **理由**：保留提醒、Wear 和 Widget 重放的幂等性，同时防止 ID 碰撞静默改写健康记录。
- **影响范围**：第 9、17、18、20 节；Repository insert contract、入口适配和冲突测试。
- **重新评估条件**：只有定义了可审计的合并 UI 或同步冲突策略后，才可增加 conflict resolution；默认仍不得覆盖。

### D10. JSON v1 缺失或损坏 ID — Resolved

- **最终选择**：继续生成随机 UUID，不引入内容派生 ID。
- **理由**：完全保持现有 JSON v1 导入行为，避免因无法证明等价性的内容哈希错误合并事件。
- **影响范围**：第 5、10、15、17、18、20 节；JSON v1 adapter 和兼容测试。
- **重新评估条件**：仅在定义新的导入格式版本和明确去重交互后重新评估；JSON v1 行为保持冻结。

### D11. Slot backfill ID — Resolved

- **最终选择**：使用第 17.1 节锁定的版本化 UUIDv5 规范。根 namespace 为标准 DNS namespace；项目 namespace 为 `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`；canonical name 为 `slot:v1:plan=<canonicalPlanUuid>;position=<canonicalPosition>;time=<canonicalLocalTime>`；输出使用标准 UUID 小写带连字符格式。
- **理由**：明确的 namespace、UTF-8 编码、字段规范化、UUID 版本和固定测试向量使同一 v2 数据库重复迁移得到相同 ID；position 保留顺序并区分重复时间，且算法不依赖 Locale、时区或设备状态。
- **影响范围**：第 6、8、12、17、18、20 节；slot 纯函数、migration 和固定输出测试。
- **重新评估条件**：仅当现有 `timeOfDay` 出现无法解析或规范化后碰撞的数据时，阻止迁移并设计新的版本前缀或 namespace；不得修改或重新解释 Slot ID v1 已发布输出。

### D12. Phase 1 status 范围 — Resolved

- **最终选择**：只定义并持久化 `RECORDED`；skip、undo、soft-delete 等状态推迟。
- **理由**：当前数据库只保存实际记录，提前引入未实现状态会制造没有产品行为支撑的 schema 语义。
- **影响范围**：第 5、6、7、8、11、12、16、20 节；Domain enum、Entity default 和 PK adapter。
- **重新评估条件**：相关用户流程、同步语义和删除策略均完成产品设计后，以独立 schema 变更重新评估。

### D13. PK 回归门槛 — Resolved

- **最终选择**：继续使用现有 `1e-6` 绝对容差；不得修改 PK 参数或放宽测试来消除迁移差异。
- **理由**：Phase 1 只改变模型边界和时间存储，不应改变药代模型数值结果。
- **影响范围**：第 5、13、16、18、20 节；全部参数测试、浓度、AUC 和时间数组回归。
- **重新评估条件**：只有独立、经过验证的 PK 算法版本变更可以提出新容差；不得在 Phase 1 内处理。

### D14. Legacy 列保留窗口 — Resolved

- **最终选择**：`timeH` 和 `timeOfDay` 至少保留一个正式发布周期，当前窗口标记为 `1.0`；删除必须另开 schema 设计。
- **理由**：保留 rollback shadow，允许验证双写一致性和发布后的迁移稳定性。
- **影响范围**：第 8、11、14、15、19、20 节；v3 schema、双写测试和 Phase 1 退出条件。
- **重新评估条件**：完整发布周期结束且第 14 节五项删除前提全部满足后，才可提出独立删除 migration；未满足则继续保留。

### D15. Migration fixture 隐私边界 — Resolved

- **最终选择**：公共仓库和 CI 优先且默认只使用固定种子的合成 fixture。真实健康数据库不得进入公共仓库；只有合成数据无法复现具体问题时，才允许在知情同意、不可逆脱敏后用于仅本地 migration test，样本不得提交或上传。
- **理由**：跨时区、DST、贴片配对、舌下 tier 和长历史需要高覆盖率，但真实健康数据的隐私风险高于复用样本的便利性。
- **影响范围**：第 18、19、20 节；fixture generator、本地测试约定、`.gitignore`/敏感文件检查和发布流程。
- **重新评估条件**：若未来建立经过法律、隐私和安全审查的受控测试数据系统，可另行评估访问方式；公共 Git 历史仍不得包含真实或真实派生健康数据库。

### D16. Batch 3 contract、MedicationPlan 与 v2 mapper 分阶段策略 — Resolved

- **最终选择**：Batch 3 分为 3A、3B 和 3C。3A 只新增 `core.model.MedicationPlan`、`core.model.ScheduleType`、Repository contract、固定业务结果及纯 JVM 测试；3B 只新增 Room v2 Entity -> Domain 只读 mapper 和显式枚举/ExtraKey mapper；3C 推迟到 Batch 4 v3 schema、Entity 和 DAO 通过后，再实现无损双向 mapper、Repository Room 实现和生产接线。v2 不提供会丢失 Phase 1 元数据或 slot ID 的通用写 mapper。
- **理由**：v2 无法持久化 `occurredAt` 元数据、revision 和 `ScheduledDoseSlot.id`。先锁定纯 contract 和只读边界，可以获得可测试的依赖方向，同时避免以 best-effort 写回静默丢失领域事实。
- **影响范围**：第 6.5、6.6、9、10、11、20 节；Batch 3A/3B 的新增文件和测试、Batch 4 的前置条件以及 Batch 3C 的实施时机。`getEventsForPk` 保持现有 30 天/20 条选择逻辑和两个分支各自顺序；Route/Ester 暂不迁移；ExtraKey 必须显式映射；持久化 Instant 溢出必须明确失败。
- **重新评估条件**：只有 v3 schema、Entity、DAO 和 migration tests 已通过，才可解除 3C 阻塞并设计生产接线；若需要移动 Route/Ester、扩展 Repository 方法、改变 PK 选取规则或允许有损写回，必须新增 ADR，不得在实现中临时改变。

### D17. B1：历史非分钟 `timeOfDay` — Resolved

- **最终选择**：保持原列表顺序和重复值；SQL 空字符串与 JSON `[]` 解释为空列表；使用 ISO `LocalTime` 解析。只有 second/nano 均为 0 的值可迁移，slot canonicalize 为 `HH:mm`，旧 `timeOfDay` 不变。任一非零 second/nano 中止整个 migration，并报告 `planId`、position、原始值和原因；`20:30:15` 固定为 rollback 负向用例。
- **理由**：分钟精度是 Slot ID v1 的已发布输入语义；截断、舍入或跳过会改变计划含义并破坏确定性标识。
- **影响范围**：第 12、13、17、18、19、20 节；`LegacyPlanTimeParser`、migration、错误类型、Batch 4B 和 4C。
- **重新评估条件**：只有通过新的版本化 Slot ID 设计和独立数据迁移 ADR 才能改变该规则；不得重新解释 Slot ID v1。

### D18. B2：v3 内部不可发布区间 — Resolved

- **最终选择**：从首个 v3 schema 提交到 Batch 8 退出验收全部通过，所有构建均为内部不可发布版本；只允许合成或可丢弃测试数据环境。发布必须满足第 19.1 节全部门槛。
- **理由**：4A 的 schema、现有写路径和后续 Repository/入口适配不是一个原子产品切换；中间版本可能无法维护完整 slot、source 和双写语义。
- **影响范围**：第 12、14、15、16、18、19、20 节；构建分发、设备测试、3C、Batch 5–8 和 release 流程。
- **重新评估条件**：只有形成另一个经完整 migration、写路径和入口验收的可发布切片时，才可通过新 ADR 缩短区间；不得仅以构建成功解除门槛。

### D19. B3：migration `timeH` storage class — Resolved

- **最终选择**：先用 `Cursor.isNull`/`getType` 验证，只允许 INTEGER/FLOAT，随后才调用 `getDouble` 并交给 `LegacyTimeAdapter`。NULL/STRING/BLOB、非 finite、乘法或 `Long` 溢出全部失败；所有 event/plan 在任何回填写入前完成预检，任一失败回滚外层 upgrade transaction。
- **理由**：SQLite 动态类型可能让声明为 REAL 的列保存异常 storage class；先验证真实存储类型可避免隐式转换掩盖损坏数据。
- **影响范围**：第 12、13、18、19、20 节；migration reader、错误报告、JVM 与 instrumentation tests。
- **重新评估条件**：只有 Android/SQLite 支持矩阵或 v2 实际 schema 证据发生变化时重新评估测试构造方式；严格失败语义不因测试平台限制而放宽。

### D20. B4：v2 显式修复工具 — Resolved

- **最终选择**：Batch 4C 在 `tools/repair-v2/` 提供 Python 3.12-compatible 标准库 `scan/repair/verify` 工具。repair 必须使用稳定 ID correction manifest，从只读输入生成不同输出副本并写最小 JSONL 审计；禁止自动猜测、截断或原地修复。
- **理由**：不可无歧义迁移的数据需要人工确认，同时必须保护原数据库、保持操作可重复和可审计，并避免把修复逻辑引入应用运行时。
- **影响范围**：第 12、13、18、19、20 节；工具源码、README、合成 fixture、测试、敏感文件排除和 Batch 4 验收。
- **重新评估条件**：只有 correction manifest 格式、支持的 v2 schema 或审计合规要求变化时新增工具版本；真实数据库、manifest 和审计日志仍不得进入仓库。

**未决问题**：无。任何触发上述重新评估条件的事项必须建立新的设计决策，不得在 Batch 实施中临时改变本基线。
