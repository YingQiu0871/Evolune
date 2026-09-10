# Evolune v1.6 — Widget Gallery 规格（A 阶段）

状态：`FROZEN / SHIPPED AS v1.6.0`。本文件记录 v1.6 的冻结规格；最终交付和批准见 `V16_ACCEPTANCE.md`。

## 1. 基线

| 项目 | A 阶段记录 |
|---|---|
| 版本基线 | `v1.5.0` |
| 基线提交 | `df12329278eafa488713edf202554cdbd523b8d0` |
| 基线标签 | `v1.5.0`（HEAD 精确匹配） |
| 当前分支 | `main` |
| Phone 包名 | `io.github.yingqiu0871.evolune` |
| Wear 命名空间 | `io.github.yingqiu0871.evolune.wear` |
| Phone/Wear 版本 | `1.5.0`；版本号 `101050000` / `1101050000` |
| Phone SDK | min 31，target 36，compile 36.1 |
| Wear SDK | min 30，target 36，compile 36.1 |
| 权威数据 | Phone Room/domain/repository；Wear 数据可重建 |

当前工作树包含 v1.6 规划文档变更；生产基线仍以 `v1.5.0` 标签和 A 阶段记录的实际起点提交为准。

## 2. 设备矩阵观察

下表是 2026-09-05 的连接观察，不能替代阶段关闭时的完整真机证据。

| 角色 | 设备 | API | 当前观察 |
|---|---|---:|---|
| Phone | Pixel 11 Pro（ADB over Wi‑Fi） | 37 | Phone `1.5.0` 已安装 |
| Phone | `emulator-5556` / `sdk_gphone64_x86_64` | 35 | Phone `1.5.0` 已安装 |
| Phone | `emulator-5558` / `sdk_gphone16k_x86_64` | 37 | 当前为历史 `1.2.2`，不能作为 v1.6 证据 |
| Wear | `emulator-5554` / `sdk_gwear_x86_64` | 37 | 已连接；候选包需在测试前重新确认安装身份 |

A 阶段关闭前必须补齐：最低支持 API 的 Phone、当前目标 API 的 Phone、两个真实 Launcher、Wear 模拟器、真实圆形手表，以及支持 Complication 的真实表盘。

## 3. 现有事实来源与复用边界

| 事实 | 当前实现 | v1.6 约束 |
|---|---|---|
| occurrence 时间线 | `WidgetPresentation.kt` 的 `WidgetPresentationMapper` | 新样式复用 occurrence ID、plan ID、slot ID 和本地日期，不按药名合并 |
| 今日进度 | `WidgetDailyProgress` | 分母是 Phone 生成的今日 occurrence 数；总数为 0 时显示“今日无计划”，不得伪造 100% |
| 下一次选择 | `WidgetPresentationPolicy` / `MedicationTimelineSelector` | 复用现有 due/upcoming 语义和本地时区边界 |
| E2 标量 | `WidgetSnapshotLoader` / `SimulationEngine` | 单位 `pg/mL`，必须带计算时间；D 阶段不能把标量伪装成曲线 |
| Widget 外观配置 | `WidgetAppearanceStore` | 继续按 `appWidgetId` 隔离主题、颜色和透明度；样式 ID 作为新增字段 |
| Widget 动作 | `ContractWidgetQuickActionWork` | 继续走 occurrence 确认、持久化成功和幂等路径；`next_dose` 只有在现有动作可用时才显示确认 |
| Wear App 快照 | `WearAppProtocol`，协议版本 1；`WearAppStore`/`WearPlanStore` 的可重建派生快照 | Phone 生成完整汇总；缺字段显示不可用，不默认成零；缓存不拥有写入权威 |
| 旧 Wear Tile | `DoseTileService` / `WearPlanStore` | 保留旧 service 身份；新 Tile 不得把旧 `planId` 动作直接当成 occurrence 确认 |

## 4. 展示矩阵

### Phone Widget

| 样式 ID | 内容 | 目标尺寸 | 动作 |
|---|---|---|---|
| `today_plan` | 今日计划、状态和快速确认 | 紧凑/横向 | occurrence 确认；更多内容进入 App |
| `next_dose` | 下一次服药、时间、剂量 | 紧凑 | `AVAILABLE` 时精确 occurrence 确认；未来或未到 due 时只读并进入 App |
| `current_e2` | 当前 E2、单位、计算时间、简洁趋势 | 紧凑/横向 | 只读，点击进入 App |
| `today_progress` | 今日完成数/总数和进度 | 紧凑 | 只读，点击进入 App |
| `pk_chart` | 有界 PK 曲线、时间轴、当前时间标记 | 大尺寸，目标 4×3 | 只读，点击进入 App |

Phone 的 2×2、4×2、4×3 只是设计目标；实际最小 dp、缩放断点和 Launcher 网格必须通过宿主实测冻结。

### Wear Tile 与 Complication

| 类型 | 样式 |
|---|---|
| Tile | `next_dose`、`today_plan`、`current_e2` |
| Complication | 下一次服药时间、当前 E2、今日完成度 |

Tile 的确认必须等待 Phone 持久化成功回执；Complication 只打开对应 App 页面，不直接写入用药记录。

### 权威展示/动作矩阵（A 阶段冻结）

v1.6 选择 **Phone Gallery 使用 `AVAILABLE`，Wear 保留现有 v1.3 兼容动作语义**。这是有意的
跨端兼容边界，不是未决事项：Phone 的现有快速动作会按当前 action zone 日期重新校验，不能把
未来 occurrence 当作可确认；现有 Wear App 的产品契约则允许对快照中的 `UPCOMING` 或 `DUE`
occurrence 发起确认，最终仍以 Phone 持久化回执为准。

| 展示面 | 可显示对象 | 可确认条件 | 其他状态 |
|---|---|---|---|
| Phone `today_plan` | 今日 occurrence，包含 `UPCOMING`/`DUE` | 仅 `MedicationActionAvailability.AVAILABLE` 且 occurrence 本地日期等于当前 action zone 日期 | `UPCOMING`、跨日未来、已记录、过期和未知状态只读并进入 App |
| Phone `next_dose` | selector 选出的最近 `UPCOMING`/`DUE` occurrence | 同上；未来 occurrence 可以显示但不能显示确认按钮 | 不可确认时只读并进入 App |
| Phone `legacy_default` | 保留 v1.5 组合布局和今日 occurrence identity | 按 Phone `AVAILABLE` 门槛重新渲染；点击时 action handler 还必须重新派生并校验 `AVAILABLE`、occurrence identity、本地日期和计划状态，旧 pending intent 也必须经过同一门槛 | 布局兼容不等于保留会失败的未来动作 |
| Wear App 与 v1.6 `today_plan`/`next_dose` Tile | 现有版本化快照中的 `UPCOMING`/`DUE` occurrence | 保留 v1.3 `UPCOMING` 或 `DUE` confirmation；使用 occurrence identity、Phone 持久化和回执 | `sent`/`pending`/`failed` 不显示完成；新协议若改为 `AVAILABLE` 必须版本协商后迁移 |
| `current_e2`、`today_progress`、PK Widget、Complication | 派生数值和状态 | 不直接写入用药记录 | 只读，点击进入对应 App 页面 |

因此 B/C 阶段必须分别测试 Phone 的 `AVAILABLE` 渲染门槛和点击时 action-time 门槛、Wear 的现有
`UPCOMING` 兼容路径，以及旧 Phone/新 Wear、新 Phone/旧 Wear、两端新版组合；新 Wear Tile 不得绕过
Phone 回执自行标记完成。

Phone 的 `AVAILABLE`-only 是双重门槛：渲染时隐藏不可用动作，点击时由 action handler 重新计算并验证
当前 availability、occurrence identity、action-zone 本地日期和计划状态。旧/stale PendingIntent、渲染后
从 `AVAILABLE` 变为 `WINDOW_EXPIRED`、同日 `UPCOMING` 或已删除/修改的方案必须返回无效结果且不得写入
记录。B 阶段已在 `ContractWidgetQuickActionWork` 中补上点击时的 due-window/availability 重检：已有
同一 occurrence 的成功记录仍按幂等重放返回，未有成功记录的命令必须在当前时刻重新得到
`AVAILABLE` 才能落库。C 阶段接入新样式时必须复用这一 handler 和测试门槛。

## 5. 状态和语义

所有样式都必须覆盖：正常、有计划但无 upcoming、无启用计划、无记录、全部完成、数据过期、读取/协议错误、加载中。

以下规则已冻结为实现约束：

- 今天由 Phone 的 `ZoneId` 和 occurrence 本地日期定义；跨午夜、DST 和时区切换沿用领域规则。
- 任务身份由 occurrence ID 及其 plan/slot/date 组合确认；不以药名或 plan ID 代替 occurrence。
- “下一次”的显示对象和可确认对象分开定义：selector 可以返回未来的 `UPCOMING` occurrence，
  但只有 `MedicationActionAvailability.AVAILABLE` 且 occurrence 的本地日期等于当前 action zone 日期时，
  才能渲染确认动作；点击时还必须由 action handler 重新验证同一门槛。`NOT_YET_DUE`、跨到下一本地日的未来 occurrence、`ALREADY_RECORDED`、
  `WINDOW_EXPIRED` 和未知状态均只读并进入 App。不得渲染一个会被
  `ContractWidgetQuickActionWork` 的日期检查或 action-time availability 检查拒绝的 `RECORD` action；如果将来要支持未来 occurrence
  的独立确认窗口，必须另立 B/C 契约和测试。B 阶段的 `WidgetUiMapper` 已将 `UPCOMING` 和
  `PAST_UNRECORDED` 映射为只读的 `OPEN_APP`，只有 `DUE` 生成 `RECORD`，已记录 occurrence 生成完成态。
- E2 必须显示单位和计算时间；`EMPTY`、`STALE`、`ERROR` 不显示伪造数值。
- 今日总数为 0 时显示“今日无计划”，不显示 `0/0` 的满进度。
- 不新增独立的权威存储或事实来源；Phone Room/domain/repository 仍是唯一事实来源。允许复用现有
  `WearAppStore`/`WearPlanStore` 这类可重建的派生 snapshot/cache，但它们必须携带 freshness、
  producer identity 等现有状态、不能直接写入 Phone 事实，也不能为新样式再建立一份事实缓存。
- 不增加每秒后台刷新；刷新由系统宿主、数据变更、日期/时区变化和显式动作触发。
- 配置入口冻结为继续使用现有 Widget provider 配置页，不按样式拆分系统 provider picker；样式选择和预览
  在该配置页完成。旧实例缺少 `styleId` 时映射到 `legacy_default`（原组合布局），未知 `styleId` 回退到
  `legacy_default`，取消保存不覆盖旧值，删除时清理实例配置；这些映射在 B 阶段用迁移测试锁定。

仍需在 A 的产品/数据审阅中最终冻结的值：E2 过期时长、趋势时间窗、PK 图表时间窗与最大采样点数、各宿主允许的刷新延迟。这些值未冻结前，A 不得标记 `DONE`。

## 6. 性能预算与测量

v1.5 已有的固定输入继续作为 v1.6 基线：`PK-EMPTY`、`PK-STEADY`、`PK-DENSE`。已有 Phone Widget 数据路径观察为：

| 数据集 | Snapshot load 中位数 | UI mapping 中位数 | PK 结果 |
|---|---:|---:|---:|
| `PK-EMPTY` | 4.3901 ms | 0.0168 ms | 空 |
| `PK-STEADY` | 7.6618 ms | 0.0233 ms | `3.012827399306029` |
| `PK-DENSE` | 14.0526 ms | 0.0363 ms | `18.076964395836175` |

这些来自同一 emulator 会话的重复采样（2 次 warm-up、7 次 measured passes），不是发布阈值。
它们只作为 v1.6 的可复测起点；v1.5 的 Energy/background 行为仍是 `SKIPPED_BY_OWNER` 豁免，
不构成 v1.6 PASS，必须重新测量。v1.6 的结构性预算先冻结为：

1. 一次刷新批次只加载一次共享快照；实例数增加不能逐个重复完整 PK 计算。
2. `current_e2` 和 `pk_chart` 使用同一批次的权威计算结果；只有图表样式才请求有界曲线数据。
3. 多个 Tile/Complication 共用一次 Wear 快照和刷新协调，不建立常驻轮询链。
4. A 阶段必须在同机、同数据、预热后报告中位数、尾部值、PK 调用数、Data Layer 请求数和唤醒数；无有效采样不得宣称性能改善。

## 7. A 阶段关闭条件

- [ ] E2 过期、趋势和 PK 图表窗口/采样上限已由产品与数据审阅者确认。
- [ ] 最小 dp、Launcher、真表和 Complication 宿主矩阵已记录。
- [ ] `PK-EMPTY/STEADY/DENSE` 的 1/3/5 Widget 及多 Wear 展示面测量已完成。
- [ ] `next_dose` 的显示/确认边界、`MedicationActionAvailability` 门槛和 legacy/default 样式回退已独立确认。
- [ ] v1.5 `SKIPPED_BY_OWNER` 的 Energy/background 豁免已记录，且没有被继承为 v1.6 PASS。
- [ ] `V16_ACCEPTANCE.md` 的 A 行有命令、设备、结果和证据路径。
- [ ] 独立审阅者复核本文件和基线 diff，并给出 `APPROVE`。
