# Evolune Implementation Summary — through v1.11.1

文档核对日期：2026-10-01。当前公开稳定版为 [v1.11.1](https://github.com/YingQiu0871/Evolune/releases/tag/v1.11.1)，发布于 2026-10-01（Phone 1.11.1 / 101110100，Wear 1.11.1 / 1101110100），annotated tag `v1.11.1` 指向 `release/v1.11.1` PR 在 `main` 上的合并提交；上一稳定版为 v1.11.0（2026-10-01），更早的 v1.10.0 发布于 2026-09-30。v1.9.0（2026-09-28）的 annotated tag 指向提交 `d099998c46e747b99855b7c9c56b3e1bd671a089`。v1.6.0（2026-09-10，tag 指向 `58ab66fc22b93630de4ea7137651b2388ff5f1a2`）及更早版本的盘点见[版本回顾](DOCUMENTATION_REVIEW_V16_2026-09-12.md)；v1.7–v1.11 的发布状态见 [Current Status](CURRENT_STATUS.md)。

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
| v1.7.0 | History & Insights：历史、月度时间线、洞察、回顾性 PK、Portable JSON v1 / CSV v1 导出与导入 |
| v1.7.1 | 真机 UI 修正：History 层级与日历居中、回顾性 PK 图表重设计、Timeline 日期条、独立“隐私与权限”入口 |
| v1.7.2 | 扁平设置页、Dynamic + 8 预设配色、旧主题兼容、备份 schema v2、移除 History 推断匹配解释文案 |
| v1.7.3 / v1.7.4 | 全屏子页面导航卡顿热修；随后恢复 220 ms 进入动效 |
| v1.8.0 | 全局卫生：后台处理与刷新成本、生命周期约束、Home 协作式取消、被中断恢复的安全回滚 |
| v1.9.0 | 维护：检出可复现的测试、冗余直接依赖与过期 Glance/ProGuard 配置清理 |
| v1.9.1 | 代码梳理与精简：移除经审计确认无引用的死代码、未使用资源与导入；行为不变 |
| v1.10.0 | 可选、默认关闭的 CPA 估算曲线（仅首页图，右侧 ng/mL 轴）；历史页从子页面返回保留滚动位置；设置页按用途分四组，「使用帮助」合并功能教程与首次引导 |
| v1.11.0 | 五个主页面顶栏/底栏毛玻璃（无新依赖）；记录/方案变化后的 Widget 刷新与 Wear 快照/仪表盘同步移出主线程；发布脚本要求 PowerShell 7 |
| v1.11.1 | 修复：恰好新增一条记录时，记录页滚动到该记录 |

完整日期、tag 和来源见 [版本回顾](DOCUMENTATION_REVIEW_V16_2026-09-12.md) 与 [Current Status](CURRENT_STATUS.md)。

## 数据与工程边界

- `:app`、`:wear` 为 Android application；`:experience-core` 为共享 JVM 契约模块。
- Phone Room v3/domain/repository 管理用药事实；`MedicationPlan` 聚合稳定的有序 slots，`DoseEvent.occurredAt` 是权威实际时间。
- UI、Widget、提醒和 Wear 动作通过应用动作与 Repository 持久化，成功后再刷新；Wear 仅保存可重建快照。
- Mahiro JSON v1 是兼容交换格式；原生加密备份是独立的版本化格式，恢复包含预览、验证及 Room/DataStore 协调。
- Health Connect 只读前台体重，Google Drive 只做主动授权的手动加密备份；不实现实时云同步或第二用药数据库。
- 历史投影是 History、Timeline、Insights 与回顾性 PK 的共享读模型：实际记录（`DoseEvent`）是历史权威，当前计划只是 schedule 上下文，不改写过去；未匹配事件保持可见；`ReminderSkipStore` 不会伪造长期“跳过/漏服”历史。
- Portable JSON v1 / CSV v1 与加密备份（`.evbackup`，自 v1.7.2 起 schema v2 并兼容 v1）相互独立。
- v1.6–v1.11.1 均未改 E2 PK 数学模型；v1.9.0–v1.11.1 均无 Room schema 迁移、无备份或 Portable 格式变化，正式包身份与签名连续性保持不变；v1.10–v1.11.1 的 Wear 模块无代码改动。
- CPA 估算曲线（v1.10.0，`pk/cpa/CpaPk.kt`）是独立的口服一室模型（Androcur 参数），只计入明确标为 CPA 的记录和已启用的 CPA 方案，只在首页图显示；开关是本机显示偏好，不进入备份/恢复/导出，E2 数值、Widget、Wear 与回顾性 PK 不受影响。它是模型估算，会低估单次服药后的峰值。

## v1.6 用户入口

| 展示面 | 最终入口 | 关键行为 |
|---|---|---|
| Phone | 今日计划、下一次服药、当前 E2、E2 趋势 | 四个独立 provider；每实例外观；今日完成度并入今日计划；除今日计划外三项只读 |
| 新 Wear Tile | 下一次服药、今日计划、当前 E2 | 共用 Phone 快照及刷新；确认等待 Phone 结果 |
| 旧 Wear Tile | `DoseTileService` 浓度曲线 | 保留组件身份与旧实例，更新品牌/预览 |
| Complication | 下一次服药、今日进度、当前 E2 | 三个 Short Text provider；点击进入 App，不直接记药 |
| Wear App | 最近记录、后续最多五个 occurrence、当前 E2 | 确认/撤销；精确“跳过本次”由 Phone 抑制提醒，不生成 DoseEvent |

Phone 快速确认受 `AVAILABLE` 和 action-time 复核约束；Wear 的 `UPCOMING/DUE` 兼容语义不能直接套回 Phone。布局、缓存和缺失字段均不能伪造完成状态。

## v1.7–v1.11 用户入口

| 展示面 | 入口 | 关键行为 |
|---|---|---|
| Phone | 历史（顶层）→ 用药洞察、回顾性 PK、用药时间线 | 只读；按日历日/月/7-30-90 天展示事实与模型估算，不输出依从率评分；回顾性 PK 明确标注为模型估算；自 v1.10.0 起从子页面返回保留滚动位置 |
| Phone | 设置 → 备份与数据 | Google Drive 备份置顶；Portable JSON v1（30 天/90 天/全部历史导出、增量导入）、CSV v1（仅导出）；旧版 Mahiro JSON 兼容路径默认收起 |
| Phone | 设置 → 外观 | Dynamic 或 8 个预设配色（色块仅在选择预设配色后显示）；旧内置主题兼容保留 |
| Phone | 设置 → 血药浓度计算 → 显示 CPA 估算曲线 | v1.10.0 起；默认关闭，开启后首页图叠加 CPA 估算虚线与右侧 ng/mL 轴 |
| Phone | 设置 → 关于与帮助 → 使用帮助 | v1.10.0 起合并「功能教程」与「首次引导」（原「指南」） |
| Phone | 五个主页面（主页、记录、历史、方案、设置） | v1.11.0 起顶栏与底栏为毛玻璃，内容滚动到栏下方；全屏子页面布局不变 |

## 验证与后续

v1.9.0 的验证记录见 [Current Status](CURRENT_STATUS.md)：新检出的完整 JVM 门禁为 187 个套件、1,709 个测试、0 失败/0 错误/0 跳过（证据 `docs/evolune/v1.9.0/evidence/P-02-release-packaging/jvm-xml-recount.txt`）。[v1.6 最终发布门禁](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md) 记录 863 JVM tests、签名与 APK 回读、Phone/Wear 保留数据覆盖安装及负责人真表验收。v1.10.0–v1.11.1 各版发布时记录的结果为 app JVM 1,461 个测试 0 失败、androidTest 396 个 0 失败 5 跳过（条件门控），详见各版 release notes。这些数字是各自发布时记录，本次文档更新没有重跑 Android 构建或设备测试。

Phone 最终视觉证据包含隔离模拟器全流程和真机覆盖/实例保留，未在最终真机逐项重新新建四款 Widget。可选 AlarmManager/并发跳过压力测试仍为 P3；v1.5 耗电豁免不是实测 PASS。

Optional CPA PK Curve 未随 v1.7 发布，后在 v1.10.0 以默认关闭的首页估算曲线交付（见上）；Tracked Date、个性化 PK、SQLCipher 和进一步模块拆分不属于已交付承诺。完整文档分类见 [文档索引](DOCUMENTATION_INDEX.md)。
