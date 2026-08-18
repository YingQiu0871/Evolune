# 功能矩阵

本表以已发布 `v1.0.0` production source 为准。状态使用 `SHIPPED v1.0`、`PARTIAL`、`PLANNED v1.1`、`PLANNED v1.2`、`PLANNED v1.3`、`PLANNED v1.4`、`PLANNED v1.5`、`PLANNED v1.6`、`PLANNED v1.7`、`DEFERRED` 和 `NOT IMPLEMENTED`。

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
| Widget advanced layouts/config/privacy | PLANNED v1.1 | 属于 v1.1 Phone Widget Completion 范围，完善尺寸、配置、隐私与交互 |
| Wear plan/concentration snapshot | SHIPPED v1.0 | Phone `/hrt/plans` DataItem，Wear 本地缓存和 Tile 刷新 |
| Wear Tile dose actions | SHIPPED v1.0 | 稳定 action/event ID、persist-first、eligible replay、conflict 与精确 DataItem 删除边界 |
| General versioned Wear protocol | PARTIAL | 当前 transport 可用但没有通用 envelope/version/checksum/ack；未来协议演进按 v1.3 Wear App 需要评估 |
| Full future Wear experience | PLANNED v1.3 | v1.0 ships Tile/Data Layer；v1.3 规划轻量完整 Wear App |
| Update checker | SHIPPED v1.0 | 从 GitHub Releases 检查较新稳定版本 |
| Health Connect | PLANNED v1.2 | 当前无 SDK/权限/provider；必须作为显式授权的可选 adapter 独立实施 |
| Google cloud backup | PLANNED v1.2 | 当前无 OAuth/provider/cloud sync；与 Health Connect 分为不同 batch |
| Onboarding / terms / permission guidance | PLANNED v1.4 | 首次使用引导、条款/隐私/医疗免责声明、上下文式权限授权流程 |
| Stability / performance / code cleanup | PLANNED v1.5 | 全量 bug sweep、性能/耗电/后台检查、清理死代码与冗余依赖 |
| Expanded Phone/Wear widget gallery | PLANNED v1.6 | 更多 Phone Widget、Wear Tile/Complication 样式，复用统一 presentation/domain 边界 |
| Optional CPA PK curve | PLANNED v1.7 | 默认关闭；开启后与 E2 在同一时间轴/图表区域显示并以图例区分，保持独立单位；实施前需独立科学与来源审查 |
| User-controlled JSON migration | SHIPPED v1.0 | 文件/剪贴板导入导出；当前跨设备迁移路径 |
| Encrypted backup format | NOT IMPLEMENTED | Google cloud backup 前需单独设计版本、密钥、恢复和冲突语义 |
| Tracked Date | DEFERRED | 当前无实体、表或产品入口；不属于 v1.0/v1.1 已锁范围 |
| Personalized calibration / PK 2.0 | DEFERRED | 不属于 v1.0；未来需独立科学、来源与回归评估 |
| SQLCipher database encryption | NOT IMPLEMENTED | 当前 Room 默认存储；未来需威胁模型与迁移/密钥恢复设计 |
| Gradle module extraction | DEFERRED | 逻辑边界已实现；物理拆分由后续测试和构建收益驱动 |

## 版本方向

- `v1.0.0` 已发布并封存；表中 `SHIPPED v1.0` 仅描述该实现。
- `v1.1`: Phone Widget Completion；`v1.2`: Google Integration & Data Continuity（Health Connect 与 Google backup 分批）；`v1.3`: Wear OS Companion App；`v1.4`: Onboarding/Terms/Permission Guidance；`v1.5`: Stability/Performance/Cleanup；`v1.6`: Widget Gallery；`v1.7`: Optional CPA PK Curve（默认关闭，科学审查门槛）。
- `DEFERRED` 不表示承诺进入某个版本。
