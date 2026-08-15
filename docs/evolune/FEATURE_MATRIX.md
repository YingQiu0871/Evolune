# 功能矩阵

本表以已发布 `v1.0.0` production source 为准。状态仅使用 `SHIPPED v1.0`、`PARTIAL`、`PLANNED v1.1`、`PLANNED v1.2`、`DEFERRED` 和 `NOT IMPLEMENTED`。

| 功能 | 状态 | v1.0 事实 / 后续边界 |
|---|---|---|
| MedicationPlan | SHIPPED v1.0 | 领域模型、Room aggregate、启用状态、daily/weekly/custom schedule 均已接入生产路径 |
| Scheduled dose slots | SHIPPED v1.0 | 稳定 UUIDv5、分钟精度 local time、权威顺序与连续 position；v2-to-v3 backfill 已实现 |
| DoseEvent | SHIPPED v1.0 | 稳定 UUID、权威 `occurredAt`、来源/状态/revision 与可选 zone/date/slot metadata |
| Repository/data boundary | SHIPPED v1.0 | `core.dataapi` contracts + Room implementations；当前是 `app` 内 package 边界 |
| Dose insert/update conflict handling | SHIPPED v1.0 | 插入区分 idempotent/conflict；更新使用 expected revision |
| Medication history | SHIPPED v1.0 | 手机端查看、编辑、删除以及 PK 查询路径 |
| Reminders and notification actions | SHIPPED v1.0 | AlarmManager、通知接收器、重排与 typed action 写入 |
| Mahiro JSON v1 import/export | SHIPPED v1.0 | 独立 DTO/codec/adapter；时间和来源兼容、逐项结果与明确失败 |
| PK visualization | SHIPPED v1.0 | Estradiol PK 估算、当前浓度、历史/预测图；不是医学检测或建议 |
| Room v3 migration/schema | SHIPPED v1.0 | 三实体、schema 2/3 导出、严格迁移矩阵和 copy-based repair tool |
| Android backup exclusions | SHIPPED v1.0 | Phone/Wear 私有数据均排除于 cloud backup 与 device transfer |
| RemoteViews phone Widget | SHIPPED v1.0 | 显示浓度与最多两个启用方案，支持快速记录和持久化后刷新 |
| Widget advanced layouts/config/privacy | PLANNED v1.1 | 首先执行 Wear / Widget Gap Audit，再锁定实现范围 |
| Wear plan/concentration snapshot | SHIPPED v1.0 | Phone `/hrt/plans` DataItem，Wear 本地缓存和 Tile 刷新 |
| Wear Tile dose actions | SHIPPED v1.0 | 稳定 action/event ID、persist-first、eligible replay、conflict 与精确 DataItem 删除边界 |
| General versioned Wear protocol | PARTIAL | 当前 transport 可用但没有通用 envelope/version/checksum/ack；纳入 v1.1 gap audit |
| Full future Wear experience | PLANNED v1.1 | v1.0 以 Tile 为主要交互；具体增强由 gap audit 决定 |
| Update checker | SHIPPED v1.0 | 从 GitHub Releases 检查较新稳定版本 |
| Health Connect | PLANNED v1.2 | 当前无 SDK/权限/provider；必须作为显式授权的可选 adapter 独立实施 |
| Google cloud backup | PLANNED v1.2 | 当前无 OAuth/provider/cloud sync；与 Health Connect 分为不同 batch |
| User-controlled JSON migration | SHIPPED v1.0 | 文件/剪贴板导入导出；当前跨设备迁移路径 |
| Encrypted backup format | NOT IMPLEMENTED | Google cloud backup 前需单独设计版本、密钥、恢复和冲突语义 |
| Tracked Date | DEFERRED | 当前无实体、表或产品入口；不属于 v1.0/v1.1 已锁范围 |
| Personalized calibration / PK 2.0 | DEFERRED | 不属于 v1.0；未来需独立科学、来源与回归评估 |
| SQLCipher database encryption | NOT IMPLEMENTED | 当前 Room 默认存储；未来需威胁模型与迁移/密钥恢复设计 |
| Gradle module extraction | DEFERRED | 逻辑边界已实现；物理拆分由后续测试和构建收益驱动 |

## 版本方向

- `v1.0.0` 已发布并封存；表中 `SHIPPED v1.0` 仅描述该实现。
- `v1.1` 的第一步是 Wear / Widget Gap Audit，不在本文件提前承诺具体 UI 或协议方案。
- `v1.2` 的 Health Connect 与 Google cloud backup 必须分别设计、实现和验收。
- `DEFERRED` 不表示承诺进入某个版本。
