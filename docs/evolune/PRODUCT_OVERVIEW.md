# 产品概览

## 文档状态

本文描述 `v1.0.0` 已发布实现。当前发布、身份和限制的快速入口是 [Current Status](CURRENT_STATUS.md)；pre-v1 规划保留在 [Migration Plan](MIGRATION_PLAN.md) 中，仅作历史记录。

## 产品定位

Evolune 是面向个人长期记录的 Android 与 Wear OS 应用，用于管理激素相关用药方案、记录实际用药事件、安排提醒，并根据本地记录计算药代动力学估算趋势。

它不是医疗器械，不提供诊断、处方或治疗建议。浓度曲线是基于输入数据和模型参数的估算，不等同于实验室检测结果。

## v1.0 核心场景

### 建立用药方案

用户可配置名称、酯类、剂量、给药途径、周期和多个每日时间。当前领域 `MedicationPlan` 持有稳定、有序的 `slots` 列表；每个 `ScheduledDoseSlot` 具有 UUID、所属 plan ID、分钟精度 `localTime` 和连续 `position`。

计划通过 `MedicationPlanRepository` 保存。Room 实现会在单个 transaction 中替换 plan 与 slots，并回读验证整个 aggregate；UI 和平台入口不以 DAO 作为生产写入边界。

### 记录与查看用药

手机 UI、通知、小组件与 Wear 动作最终写入同一 `DoseEventRepository`。`DoseEvent` 包含稳定 UUID、权威 `occurredAt: Instant`、可选 `zoneId`/`localDate`/`slotId`、`source`、`status`、`revision`、途径、剂量、酯类与 extras。

v1.0 的 source 包括 `LEGACY`、`MANUAL`、`JSON_V1`、`REMINDER`、`WIDGET` 和 `WEAR`；status 当前仅为 `RECORDED`。同 ID 相同内容可识别为幂等重放，不同内容则报告冲突；编辑使用 revision 乐观并发检查。

### 查看浓度趋势

PK 计算通过 `DomainDoseEventToPkAdapter` 将当前领域事件投影为 PK 模型输入。`occurredAt` 是业务权威时间；旧 `timeH` 只保留在 Room v2/JSON v1/PK 兼容边界，不再是核心唯一时间语义。

### 提醒和快速记录

提醒继续使用 AlarmManager 与广播接收器。通知和 Widget 的记录动作通过 typed application action 与 Repository contract 完成持久化；只有接受写入后才执行 Widget 刷新、通知或提示等副作用。

### Phone Widget

当前实现是 RemoteViews AppWidget。它通过 Repository contract 加载最多两个启用方案及当前 PK 浓度，支持一键记录。Widget 事件使用稳定的分钟级动作 ID 处理重复投递，并在持久化成功后刷新和显示反馈。

### Wear Tile 与 Data Layer

手机将最多两个启用方案、当前浓度与曲线快照写入 `/hrt/plans` DataItem；Wear 在本地 `SharedPreferences` 缓存仪表盘并刷新 Tile。Wear Tile 通过 `/hrt/dose-actions/<actionId>` DataItem 提交动作。

手机验证 URI/payload action ID、plan ID 和记录时间后，以 action ID 作为事件 ID 写入 `source=WEAR` 的事件。成功或可接受重放后先刷新 Widget，再只删除本次动作对应的精确 DataItem。冲突、非法数据或存储失败不会删除动作；副作用或删除失败会保留 DataItem 供后续重试。

当前 payload 没有通用 envelope、checksum、ack 或版本协商。这是明确限制，不影响上述 v1.0 已实现的幂等/冲突边界。

### JSON v1 兼容

`MahiroJsonV1ImportService` 与 `MahiroJsonV1ExportService` 通过独立 DTO、codec 和 adapter 保持 Mahiro JSON v1 兼容。导入保留可表示的事件时间和 ID，将来源映射为 `JSON_V1`，区分 inserted、idempotent、conflict、invalid 与 storage failure；v1 无法表达的领域元数据不会被伪造。导出在事件无法无损表示为 v1 时明确失败。

## 持久化与迁移

`AppDatabase` 当前为 Room v3：

| 表/实体 | 作用 |
|---|---|
| `dose_events` / `DoseEventEntity` | 用药事件及 v3 领域元数据；保留 legacy `timeH` 兼容列 |
| `medication_plans` / `MedicationPlanEntity` | 方案主体及 legacy `timeOfDay` 兼容列 |
| `scheduled_dose_slots` / `ScheduledDoseSlotEntity` | 稳定、有序的方案时间槽 |

`exportSchema = true`，schema 2 和 3 位于 `app/schemas/io.github.yingqiu0871.evolune.data.AppDatabase/`。v2-to-v3 migration 对旧数值时间、计划时间列表和 slots 进行严格预检；异常数据使 migration 回滚而不是静默修正。`tools/repair-v2/` 提供只读扫描、显式 manifest 修复到新副本和复核流程。

Phone 与 Wear Manifest 都引用 `data_extraction_rules.xml` 和 `backup_rules.xml`。规则排除全部应用私有 root/file/database/shared-preference 数据的 cloud backup 与 device transfer；当前主动迁移路径是用户控制的 JSON 导出/导入。

## v1.0 能力与边界

| 功能 | v1.0 状态 |
|---|---|
| 用药方案、事件、历史、提醒 | SHIPPED v1.0 |
| PK 估算与图表 | SHIPPED v1.0 |
| Mahiro JSON v1 导入导出 | SHIPPED v1.0 |
| Room v3、schema、严格 migration | SHIPPED v1.0 |
| Repository/data boundary | SHIPPED v1.0（当前为 app 内 package 边界） |
| RemoteViews Widget | SHIPPED v1.0 |
| Wear Tile/Data Layer 和 dose actions | SHIPPED v1.0 |
| 通用版本化 Wear 协议、完整 Wear App | PARTIAL / future enhancement |
| Health Connect | NOT IMPLEMENTED |
| Google cloud backup/sync | NOT IMPLEMENTED |
| Tracked Date | DEFERRED |
| 个性化 calibration/PK 2.0 | DEFERRED |

## 隐私原则

1. Evolune Room 数据库是核心事实来源；Wear 缓存和未来外部集成都不能静默覆盖本地事实。
2. 敏感数据不进入普通日志；当前自动云备份与设备迁移被明确排除。
3. 导出文件由用户保存和管理；未来云备份必须具有明确授权、加密格式、密钥生命周期和冲突恢复设计。
4. Health Connect 若进入 v1.2，必须作为可选 adapter，按数据类型授权并记录来源。

## 后续方向

- `v1.1`: 完成 Phone Widget。
- `v1.2`: Health Connect 与 Google 数据连续性，作为独立批次。
- `v1.3`: 轻量级 Wear OS 伴侣应用，同时保留现有 Tile。
- `v1.4`: 首次使用引导、条款、隐私与权限说明。
- `v1.5`: 稳定性、性能与代码清理。
- `v1.6`: 更多 Widget/Wear 展示样式（Widget Gallery）。
- `v1.7`: 可选 CPA 浓度曲线，默认关闭并需独立科学审查。

详见 [Roadmap](ROADMAP.md)。
