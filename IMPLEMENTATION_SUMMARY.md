# Evolune Implementation Summary — through v1.6.0

文档核对日期：2026-09-12。当前公开稳定版为 [v1.6.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0)，发布于 2026-09-10，源码 tag 指向 `58ab66fc22b93630de4ea7137651b2388ff5f1a2`。本次盘点起点为 `main@c7f3d266357af08b737aa4fd4015f1b1391279c3`；该后续提交补充双语发布说明。

## 版本累计成果

| 版本 | 已发布成果 |
|---|---|
| v1.0.0 | Room v3、稳定 slots、Repository、用药记录/计划/提醒、PK、JSON、基础 Widget/Tile |
| v1.1.0 | occurrence 驱动 Widget、滚动列表、实例外观、Phone/Wear 统一 application ID |
| v1.2.0 | Health Connect 前台体重读取、原生加密备份、恢复 journal、手动 Google Drive 备份/恢复 |
| v1.2.2 | 安全唯一的延迟 null-slot 匹配、品牌与 About 维护 |
| v1.3.0 / v1.3.1 | Wear App、版本化快照、确认/撤销；v1.3.1 修复 Wear APK 安装缺陷 |
| v1.4.0 | 首次信任/权限引导、可重看的功能教程 |
| v1.5.0 | 有范围和证据的稳定性/性能/清理；Energy/background 保留负责人豁免 |
| v1.6.0 | 四个 Phone Widget、三新 Tile、旧 Tile 兼容、三 Complication、统一外观、精确跳过提醒 |

完整日期、tag 和来源见 [版本回顾](docs/evolune/DOCUMENTATION_REVIEW_V16_2026-09-12.md)。

## 数据与工程边界

- `:app`、`:wear` 为 Android application；`:experience-core` 为共享 JVM 契约模块。
- Phone Room v3/domain/repository 管理用药事实；`MedicationPlan` 聚合稳定的有序 slots，`DoseEvent.occurredAt` 是权威实际时间。
- UI、Widget、提醒和 Wear 动作通过应用动作与 Repository 持久化，成功后再刷新；Wear 仅保存可重建快照。
- Mahiro JSON v1 是兼容交换格式；原生加密备份是独立的版本化格式，恢复包含预览、验证及 Room/DataStore 协调。
- Health Connect 只读前台体重，Google Drive 只做主动授权的手动加密备份；不实现实时云同步或第二用药数据库。
- v1.6 未改 PK 数学模型、Room schema、备份格式、正式包身份或签名连续性。

## v1.6 用户入口

| 展示面 | 最终入口 | 关键行为 |
|---|---|---|
| Phone | 今日计划、下一次服药、当前 E2、E2 趋势 | 四个独立 provider；每实例外观；今日完成度并入今日计划；除今日计划外三项只读 |
| 新 Wear Tile | 下一次服药、今日计划、当前 E2 | 共用 Phone 快照及刷新；确认等待 Phone 结果 |
| 旧 Wear Tile | `DoseTileService` 浓度曲线 | 保留组件身份与旧实例，更新品牌/预览 |
| Complication | 下一次服药、今日进度、当前 E2 | 三个 Short Text provider；点击进入 App，不直接记药 |
| Wear App | 最近记录、后续最多五个 occurrence、当前 E2 | 确认/撤销；精确“跳过本次”由 Phone 抑制提醒，不生成 DoseEvent |

Phone 快速确认受 `AVAILABLE` 和 action-time 复核约束；Wear 的 `UPCOMING/DUE` 兼容语义不能直接套回 Phone。布局、缓存和缺失字段均不能伪造完成状态。

## 验证与后续

[最终发布门禁](docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md) 记录 863 JVM tests、签名与 APK 回读、Phone/Wear 保留数据覆盖安装及负责人真表验收。该数字是发布时记录，本次文档更新没有重跑 Android 构建或设备测试。

Phone 最终视觉证据包含隔离模拟器全流程和真机覆盖/实例保留，未在最终真机逐项重新新建四款 Widget。可选 AlarmManager/并发跳过压力测试仍为 P3；v1.5 耗电豁免不是实测 PASS。

v1.7 CPA 仍为未开始候选；Tracked Date、个性化 PK、SQLCipher 和进一步模块拆分不属于已交付承诺。完整文档分类见 [文档索引](docs/evolune/DOCUMENTATION_INDEX.md)。
