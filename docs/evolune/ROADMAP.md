# 路线图

本路线图从已发布的 v1.0.0 向后规划。当前实现事实见 [Current Status](CURRENT_STATUS.md)，pre-v1 分阶段计划见已标记为历史文档的 [Migration Plan](MIGRATION_PLAN.md)。

路线图描述的是产品目标和版本边界，不自动授权实现。每个版本进入开发前仍需完成独立设计、来源审查、回归门槛和真实设备验收。

## Released

### v1.0.0 — 2026-08-15

首个公开稳定版本已经封存并发布，release commit 为 `780f167074cc737954c884d375825ef95db605c7`。

主要范围：

- Phone/Wear 公共应用身份、持久 Release signing 与经过验证的签名 APK
- 用药方案、稳定 scheduled-dose slots、用药事件、历史和提醒
- Room v3、schema export、严格 v2-to-v3 migration 与修复工具
- Repository/data boundary、domain/entity mapping 和 PK adapter
- Mahiro JSON v1 导入导出
- PK 估算与浓度图
- RemoteViews Phone Widget 及快速记录
- Wear Tile/Data Layer、dose action 持久化优先、重放/幂等/冲突处理
- Phone/Wear 私有数据的 Android backup/device-transfer 排除规则
- 更新检查、来源与第三方通知、明确的公开发布边界

`v1.0.0` tag 与 GitHub Release 保持封存；后续工作不会移动或重建该 tag。

## Completed milestones

### v1.1 — Phone Widget Completion

v1.1 已于 2026-08-21 完成并关闭。最终 main merge 为
`7531af3fdb73b5ecfc2bfe5af65771d670945bdc`；Owner、真实设备和 CI
最终门均已通过。v1.1 是已完成的开发里程碑，不代表创建了新的公开
Release/tag；`v1.0.0` 仍是当前发布的稳定版本。

完成范围：把手机桌面 Widget 从“可用”收敛为稳定、完整、适合日常使用的今日服药入口。

实现边界：

- 保持 Phone Room/domain/repository 为唯一事实来源，Widget 只做派生展示和动作入口。
- occurrence-driven rows 按时间顺序展示；同一 MedicationPlan 的多个 scheduled slots 作为独立 occurrence 展示和记录。
- 以 2×2 为最低完整规格，尺寸允许时显示更多 occurrence；超出容量时使用 RemoteViews collection/list 纵向滚动，顶部完成数、E2 浓度和进度区域保持固定。
- 今日完成数和进度条只依据实际已记录 occurrence，不因计划时间已过自动完成。
- 未记录 occurrence 使用明确的勾选动作；记录使用 occurrence-scoped identity、实际点击时间、精确 slot/date 关联、幂等和 persistence-before-side-effects。
- 维持响应式尺寸、Material 3、Material You、Monet 预设、Light/Dark、每 Widget 独立透明度和长按重新配置。
- 保持多 Widget 隔离、进程重建、日期/时区变化和 OEM Launcher 行为可验证；配置预览与实际 Widget 保持一致。

## Next planned milestone

### v1.2 — Google Integration & Data Continuity

v1.2 尚未开始实现，仍是下一开发里程碑。

目标：让 Evolune 在不改变本地权威模型的前提下融入 Android/Google 数据生态，并解决换机、重装和长期数据保存问题。

v1.2 分成两个可独立验收的 batch，不允许相互耦合阻塞。

#### Health Connect batch

- 明确 Evolune 真正需要读取或写入的数据类型和用户价值。
- 优先评估显式授权的体重读取；用药写入/PHR 作为独立设计项。
- Room 始终保持 Evolune 核心事实来源，Health Connect 只作为交换/集成层。
- 覆盖首次授权、拒绝、撤权、provider 不可用、来源标记、单位映射、重复数据和错误恢复。
- 权限采用上下文式申请，不在启动时一次性索取无关权限。

#### Google cloud backup batch

- 先定义版本化、可验证、具完整性保护的备份格式，再接入 Google provider。
- 覆盖备份创建、恢复预览、换机恢复、重装恢复、损坏备份、错误密钥/凭据和版本兼容。
- 明确密钥生命周期与用户控制，不把敏感健康数据静默上传。
- v1.2 默认目标是 backup/restore，而不是实时多设备数据库同步。
- cloud backup、Wear Data Layer 和本地导入导出保持不同职责边界。

### v1.3 — Wear OS Companion App

目标：在保留现有 Wear Tile 的同时，提供一个真正可打开、可完成基础日常操作的轻量 Wear OS App。

核心能力：

- 首页显示最近一次已记录服药：药物、剂量、实际记录时间和状态。
- 支持对最近一次记录进行精确撤销；撤销必须按真实 `DoseEvent.id` 经 Phone 权威 repository 完成。
- 显示接下来最多 5 个服药 occurrence，而不是 5 个去重后的 MedicationPlan。
- 每个未来/待完成 occurrence 可在手表上手动确认服药，继续使用稳定 occurrence identity、幂等、实际点击时间和 persistence-before-side-effects。
- 已确认 occurrence 支持精确撤销，不使用模糊的“最近一小时/同一种药”删除。
- App 内显示当前 E2 估算浓度，可附最近更新时间和简洁趋势；第一版不复制手机完整 PK 图。
- 现有 Wear Tile/小组件继续保留，作为“一眼查看/快速操作”入口；完整 Wear App 负责更丰富查看和撤销。

架构边界：

- Phone 仍是唯一事实来源，Watch 不建立第二套药物数据库。
- Watch 可缓存 last-known snapshot（最近一次记录、后续 occurrence、当前 E2），缓存不升级为权威状态。
- v1.3 不承诺复杂的完全离线双向冲突同步；需要 Phone 写入的动作必须有明确的连接/排队/失败反馈策略。
- Snooze 可在 v1.3 重新评估，但只有在状态语义和持久化模型明确后才进入实现，不作为默认必做项。

### v1.4 — Onboarding, Terms & Permission Guidance

目标：补齐首次使用、信任、法律说明和权限授予体验，让新用户能够理解 Evolune 的用途、数据边界和主要功能。

主要范围：

- 使用条款、隐私说明、医疗/PK 估算免责声明及应用内可重新查看入口。
- 首次使用引导：产品定位、创建第一个用药方案、记录服药、理解 PK 图、添加 Widget、连接 Wear、启用备份。
- 权限授予引导采用“先解释价值，再触发系统权限”的上下文式流程。
- 覆盖通知、Health Connect、Wear/附近设备以及未来备份所需权限或授权。
- 支持跳过非必要步骤，并可从设置重新进入相关引导。
- 无障碍、文字可读性、深色模式和折叠屏/大屏布局纳入验收。

实现要求：可研究成熟健康类应用的可观察 onboarding/权限 UX，但 Evolune 保持自己的界面、文案、代码和来源边界；任何外部源码或专属资源复用都需另行来源与许可审查。

### v1.5 — Stability, Performance & Code Cleanup

目标：原则上不新增用户功能，集中偿还技术债并形成一次系统性的稳定性版本。

主要范围：

- 全量 bug sweep：新装、升级、进程被杀、重启、跨午夜、日期/时区/DST、修改/删除方案、多 Widget、Phone/Wear 断连重连、备份恢复和异常数据状态。
- 性能检查：启动时间、PK 计算、Room 查询、Compose 重组、Widget 刷新、Wear Data Layer 和后台任务。
- 耗电检查：WorkManager、轮询、wakeups、重复 Flow collection、重复 Widget refresh、Wear 活跃状态和异常后台任务。
- 清理死代码、重复 mapper、废弃资源、无用依赖、重复状态模型和已经失效的兼容层。
- 精简必须以行为等价和回归证据为前提，不为减少代码行数破坏已验证架构。
- 强化静态检查、测试隔离、错误处理和日志边界。

v1.5 退出条件：核心自动化、真实设备矩阵、升级/恢复场景、耗电/后台行为和代码清理审计全部通过，且没有新的 P0/P1 稳定性问题。

### v1.6 — Widget Gallery

目标：在 v1.1 Widget 数据/动作基础和 v1.3 Wear 数据模型稳定后，增加多种可选的手机与手表 Widget/Tile/Complication 样式，而不重新发明数据层。

Phone 候选：

- 今日计划：当前 v1.1 的完整服药计划卡。
- 下一次服药：更小尺寸，只突出最近 occurrence 和快速确认。
- 当前 E2：显示当前估算浓度、更新时间和简洁趋势。
- PK 图表：面向较大尺寸的只读浓度图 Widget。
- 今日进度：极简完成数/进度样式。

Wear 候选：

- 下一次服药 Tile。
- 今日计划 Tile。
- 当前 E2 Tile。
- 下一次服药时间、当前 E2、今日完成度等 Complication。

所有样式必须复用统一 presentation state、稳定 occurrence/action semantics 和现有配置体系；不得为每个 Widget 建立独立事实来源。

### v1.7 — Optional CPA Pharmacokinetic Curve

目标：在不影响现有 E2 模型默认体验和数值回归的前提下，新增醋酸环丙孕酮（Cyproterone Acetate, CPA）的估算浓度曲线。

产品行为：

- 默认关闭，不改变现有用户的图表。
- 设置中新增独立开关，例如“显示 CPA 估算曲线”。
- 开启后，CPA 与 E2 共用同一时间轴和同一个图表区域，并通过清晰图例区分。
- 因 E2 与 CPA 单位不同，优先采用明确的双 Y 轴或其他不会混淆数值量纲的设计：E2 使用 pg/mL，CPA 使用 ng/mL。
- CPA 曲线只依据明确记录为 CPA 的事件计算，不影响 E2 曲线本身。
- 图表和设置中明确其为模型估算，不等同于实验室检测结果。

科学与来源门槛：

- 研究 `https://hrt.mahiro.uk/` 及其公开实现对 CPA 的处理方式，但不得未经验证直接复制参数或代码。
- 当前公开 Oyama 实现可作为复现实验基线：口服 CPA 分支使用简化的一阶模型参数（当前代码中可见 `k1≈1.0 h⁻¹`、`k3≈0.017 h⁻¹`、`F≈0.7`、`Vd≈14 L/kg`），并单独输出 CPA ng/mL 序列。
- 在 Evolune 落地前必须将该基线与原始药代研究和药品说明书独立核对；参数来源、单位、剂量范围、重复给药累积行为和适用局限必须写入模型文档和测试。
- 现有文献显示 CPA 终末半衰期约为 1.7–2.3 天，部分研究/说明书约 44–54 小时；单次给药研究报告的表观分布容积可接近约 986–1300 L。公开实现中的数值只能作为候选，不作为无条件真值。
- 若文献证据支持更合适的双相/多室模型，应以科学验证结果为准，而不是为了与参考实现一致而固定简化模型。

工程边界：

- CPA series 与 E2 series 在模拟层保持分离，不把不同单位的数值相加形成“总浓度”。
- 优先复用现有 PK 事件、时间网格、插值、图表和回归基础设施，但 CPA 参数解析应保持独立可测试。
- 默认关闭时，E2 输出必须与 v1.6 前基线数值完全一致。
- 如果现有抗雄记录模型无法可靠区分 CPA 剂量与 occurrence，先完成领域语义审计；是否需要 Room migration 必须单独立项，不在路线图中预设。

参考入口：

- Oyama's HRT Tracker: https://hrt.mahiro.uk/
- Oyama-s-HRT-Tracker public repository: https://github.com/xunxunProjects/Oyama-s-HRT-Tracker
- Androcur pharmacokinetic product information and primary CPA pharmacokinetic studies should be included in the v1.7 scientific review package.

## Later / Deferred

- Personalized PK / calibration evolution，包括 PK 2.0；需独立科学、来源和回归评估。
- Tracked Date；仍需产品决策和领域语义设计。
- Repository rehousing 与 `D:\Evolune` protected-root retirement；应在 v1.2 之后作为单独、可验证的迁移批次安排，不与功能版本混合。
- 由测试隔离和构建收益驱动的 Gradle module extraction。
- SQLCipher 或其他数据库透明加密；先完成威胁模型、迁移与密钥恢复设计。
- 超出 v1.6 范围的更多统计、筛选和桌面/手表只读可视化。

## 跨版本永久边界

- Phone Room/domain/repository 是核心事实来源；Widget、Wear、Health Connect 和云端均不得静默升级为第二事实来源。
- 所有记录/撤销动作遵守 persistence-before-side-effects、稳定 identity、幂等和明确冲突语义。
- 不把 PK/CPA 估算描述为实验室结果、诊断或治疗建议。
- 不静默上传健康或用药数据。
- 不以未来功能破坏 v1.0 schema、稳定 ID、JSON 兼容或 sealed release history；需要 schema 变化时必须有独立 migration 与回归门槛。
- 不扩大已记录的 PK permission scope；始终保留来源和贡献者 attribution。
- 外部项目可作为行为、科学或 UX 研究来源，但任何源码、资产或实现复用都必须先完成来源与许可核验。
