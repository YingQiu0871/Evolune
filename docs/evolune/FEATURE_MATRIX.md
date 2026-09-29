# 功能矩阵

本表以已发布的 `v1.9.0` 和当前 `main` 为准。状态使用 `SHIPPED v1.x`、`PARTIAL`、`PLANNED v1.x`、`DEFERRED` 和 `NOT IMPLEMENTED`。

| 功能 | 状态 | 截至 v1.9 的实现事实 / 边界 |
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
| RemoteViews phone Widget | SHIPPED v1.0 | 基础 RemoteViews Widget、浓度展示、方案展示和快速记录 |
| Widget advanced layouts/config/privacy | SHIPPED v1.1 | occurrence-driven 多 slot 行、响应式/可滚动尺寸、配置预览、配色/透明度/对比度与多 Widget 隔离 |
| Wear plan/concentration snapshot | SHIPPED v1.0 | Phone `/hrt/plans` DataItem，Wear 本地缓存和 Tile 刷新 |
| Wear Tile dose actions | SHIPPED v1.0 | 稳定 action/event ID、persist-first、eligible replay、conflict 与精确 DataItem 删除边界 |
| Versioned Wear App protocol | SHIPPED v1.3; extended v1.6 | `experience-core` 协议 v1、snapshot/request、确认/撤销回执；v1.6 增加 tag 11 todaySummary，旧 `/hrt/*` 通道保持独立兼容 |
| Wear App experience | SHIPPED v1.3; fixed v1.3.1 | 轻量 Wear App、版本化快照、确认/撤销及 Phone 权威回执；v1.3.1 修复 Wear APK 安装失败；v1.6 增加 occurrence 级跳过 |
| Update checker | SHIPPED v1.0 | 从 GitHub Releases 检查较新稳定版本 |
| Health Connect | SHIPPED v1.2 | SDK、READ_WEIGHT、可选前台读取最近 30 天体重、权限/provider 状态和本地 freshness 保护；不含用药写入 |
| Google Drive backup/restore | SHIPPED v1.2 | 显式授权、手动 appDataFolder 加密备份、回读验证、三代保留与恢复预览；不是实时同步 |
| Background / real-time cloud sync | NOT IMPLEMENTED | 不常驻同步，不把云端作为另一用药数据库 |
| Onboarding / terms / permission guidance | SHIPPED v1.4 | v1.4-A 信任/权限基础与 v1.4-B 六步功能教程已实现、验收并发布于 `v1.4.0` |
| Stability / performance / code cleanup | SHIPPED v1.5 | 范围内稳定性、性能和清理；Energy/background 为 SKIPPED_BY_OWNER，不能宣称电池实测 PASS |
| Phone launcher Logo scale | SHIPPED v1.5 | adaptive-icon foreground/monochrome 安全区和 launcher mask 已验收 |
| Expanded Phone/Wear widget gallery | SHIPPED v1.6 | 四个 Phone Widget、三个新 Tile、兼容曲线 Tile 和三个 Complication，共用 Phone 派生状态边界 |
| History（按日历日） | SHIPPED v1.7; polished v1.7.1–v1.7.2 | 共享历史投影：实际记录是权威，区分计划时点与实际摄入并显示时间差；未匹配事件按真实来源显示；只读，不写入数据 |
| Timeline（月度） | SHIPPED v1.7; date strip v1.7.1 | 已匹配 / 未记录计划 / 未匹配摄入三类行；仅呈现事实，无评分或依从性判定 |
| Insights（7/30/90 天） | SHIPPED v1.7 | 记录次数、来源分布、身份置信度；不输出严格历史依从率、百分比或 timing 指标 |
| Retrospective PK | SHIPPED v1.7; chart redesigned v1.7.1 | 基于所选区间权威已记录摄入的模型估算曲线；7/30/90 天区间；始终标注为估算，不是实测血药浓度 |
| Evolune Portable JSON v1 / CSV v1 | SHIPPED v1.7 | JSON 导出（30 天/90 天/全部）与增量导入；CSV 仅导出；与加密备份相互独立 |
| Flat Settings and app color schemes | SHIPPED v1.7.1–v1.7.2 | 设置页扁平化；Dynamic + 8 预设配色；旧内置主题兼容；备份 schema v2（兼容 v1 恢复） |
| Navigation stability and motion | SHIPPED v1.7.3 / v1.7.4 | 全屏子页面卡顿修复；220 ms 淡入 + 0.98→1.0 缩放进入动效 |
| Global hygiene (performance / lifecycle / cancellation / restore recovery) | SHIPPED v1.8 | 后台处理与刷新成本、Home 生命周期与协作式取消、被中断恢复的 PREPARED journal 安全回滚；无 schema/备份/语义变化 |
| Checkout-reproducible tests and config cleanup | SHIPPED v1.9 | Windows CRLF 检出下测试确定性；移除冗余直接依赖声明与过期 Glance/ProGuard 配置；无 schema/备份/语义变化 |
| Optional CPA PK curve | PLANNED（未立项；原 v1.7 早期草案，未随 v1.7 发布） | 默认关闭；开启后与 E2 在同一时间轴/图表区域显示并以图例区分，保持独立单位；实施前需独立科学与来源审查 |
| User-controlled JSON migration | SHIPPED v1.0 | 文件/剪贴板兼容交换；不等同完整原生备份 |
| Encrypted backup format | SHIPPED v1.2 | AES-256-GCM、PBKDF2-HMAC-SHA256（默认 600,000 次）、版本校验、恢复 journal；不等同 SQLCipher 数据库加密 |
| Tracked Date | DEFERRED | 当前无实体、表或产品入口；不属于 v1.0/v1.1 已锁范围 |
| Personalized calibration / PK 2.0 | DEFERRED | 不属于 v1.0；未来需独立科学、来源与回归评估 |
| SQLCipher database encryption | NOT IMPLEMENTED | 当前 Room 默认存储；未来需威胁模型与迁移/密钥恢复设计 |
| Gradle module extraction | DEFERRED | 逻辑边界已实现；物理拆分由后续测试和构建收益驱动 |

## 版本方向

- `v1.0.0` 已发布并封存；表中 `SHIPPED v1.0` 仅描述该实现。
- `v1.1`、`v1.2.0/v1.2.2`、`v1.3.0/v1.3.1`、`v1.4.0`、`v1.5.0`、`v1.6.0`、`v1.7.0–v1.7.4`、`v1.8.0`、`v1.9.0` 均有公开发布；v1.6 及更早的日期与证据见 [版本回顾](DOCUMENTATION_REVIEW_V16_2026-09-12.md)，v1.7–v1.9 见 [Current Status](CURRENT_STATUS.md) 与各版本目录的发布说明。可选 CPA 曲线仍是未立项候选。
- `DEFERRED` 不表示承诺进入某个版本。
