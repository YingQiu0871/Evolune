# 功能矩阵

状态说明：`已确认` 表示当前 Evolune 源码存在，不等同于目标架构已经完成；`部分确认` 表示迁移资料或现有代码有局部实现；`当前不存在` 表示本仓库没有实现；`待确认` 表示需要真实设备、账号或人工审查。括号中的版本表示计划进入的发布阶段。

| 功能 | 当前状态 | 来源项目 | Evolune 目标模块 | 核心模型 | UI/入口 | 后台 | 手机 | Wear | Widget | Health Connect | 同步 | 权限 | 隐私/许可证风险 | 测试要求 | 完成度 | 优先级 | 依赖 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 多药物计划 | 已确认 | HRTTracker/Evolune | `feature:medications` | `MedicationPlan` | `MedicationPlansScreen` | Reminder 重排 | 是 | 快照 | 只读 | 无 | 本地 | 通知/闹钟 | 本地敏感数据；代码需独立维护 | Repository/计划预测测试 | 基础可用 | P1 | Room、DataStore |
| 实际用药事件 | 已确认 | HRTTracker/Evolune | `feature:history` | `DoseEvent` | Records、通知动作 | Widget/Wear 刷新 | 是 | 基础动作 | 快速动作 | 无 | 基础 Wear | 通知 | 当前缺少来源、槽位和审计字段 | DAO/幂等/时间测试 | 基础可用 | P1 | Room |
| 给药途径和剂量 | 已确认 | HRTTracker/Evolune | `core:model` | `Route`、`Ester`、extras | 记录表单 | 无 | 是 | 部分 | 部分 | 无 | 无 | 无额外 | PK 参数和 UI 文案需独立核验 | 参数边界测试 | 基础可用 | P1 | PK adapter |
| PK 浓度估算 | 已确认 | Evolune | `feature:statistics` | `SimulationResult` | Home、Chart | 计算在 ViewModel 流程中 | 是 | Tile 快照 | 当前主小组件可显示有限信息 | 无 | 无 | 无 | 估算值不能冒充检测值 | 数值、空数据、边界时间 | 基础可用 | P1 | `SimulationEngine` |
| 历史记录 | 已确认 | HRTTracker/Evolune | `feature:history` | `DoseEvent` | `MedicationRecordsScreen` | 无 | 是 | 无 | 部分 | 无 | 本地 | 无 | 删除/撤销语义不完整 | 排序、删除、导入导出 | 基础可用 | P1 | Room |
| 提醒通知 | 已确认 | HRTTracker/Evolune | `core:notifications` | `MedicationPlan`、slot | Reminder receivers | AlarmManager | 是 | 无 | 无 | 无 | 本地 | 通知、精确闹钟 | 时区/设备省电影响 | 重排、开关、动作测试 | 基础可用 | P1 | AlarmManager |
| JSON 导入导出 | 已确认 | HRTTracker/Evolune | `feature:backup` | External DTO | Settings/navigation | 无 | 是 | 无 | 无 | 无 | 文件/剪贴板 | 文件选择 | 导出文件包含健康数据 | 版本、坏数据、兼容性 | 基础可用 | P1 | Serialization |
| RemoteViews 小组件 | 已确认 | HRTTracker/Evolune | `widget` | 当前直接读 Room | `EvoluneWidgetReceiver` | 事件后手动刷新 | 是 | 无 | 是 | 无 | 无 | AppWidget | 桌面可能暴露药物信息 | provider、动作和尺寸测试 | 基础可用 | P1 | Room、RemoteViews |
| Glance 小组件 | 当前不存在 | Featherline 快照 | `widget` | `WidgetSnapshot` | 未来配置 Activity | WorkManager 候选 | 是 | 无 | 目标 | 无 | 无 | AppWidget | Glance 状态和 OEM 差异 | 预览、尺寸、刷新、设备矩阵 | 0% | P2 | Glance、snapshot |
| Wear Tile | 已确认（基础实现） | Evolune | `wear` | `WearDashboard` | `DoseTileService` | Data Layer | 无 | 是 | 无 | 无 | 基础设备传输 | Wearable | 当前 payload 未版本化 | Tile、缓存、离线测试 | 基础可用；非目标协议 | P1 | Wearable SDK |
| Wear 完整 App | 当前不存在 | Featherline 快照 | `wear` | Wear snapshot | 未来 Wear Activity | 无 | 配对依赖 | 目标 | 无 | 无 | Data Layer | Wearable | GPL 快照不可直接复制 | UI、断连、安装矩阵 | 0% | P1（1.0） | protocol |
| Wear 协议版本化 | 当前不存在 | Featherline 快照 | `core:wear-protocol` | Envelope、Command | 无 | 重试/队列 | Bridge | 是 | 无 | 无 | Data/Message | Wearable | 快照源码 GPL，需重写 | 编解码兼容/幂等/校验 | 0% | P1 | Kotlin/JVM |
| Health Connect 体重读取 | 当前不存在 | Featherline 快照 | `core:healthconnect` | Weight sample DTO | Settings | 手动/WorkManager | 目标 | 无 | 无 | 读取 | 无 | Health Connect | 健康数据权限 | Provider/撤权/单位测试 | 0% | P2 | Health Connect SDK |
| Health Connect 用药写入 | 当前不存在 | Featherline 快照 | `core:healthconnect` | Dose export DTO | Settings | 手动同步 | 目标 | 无 | 无 | 写入/PHR 待验证 | 无 | Health Connect | PHR 能力和映射风险 | schema/provider/撤回测试 | 0% | P2 | Health Connect PHR |
| Tracked Date | 当前不存在；待产品确认 | Featherline 快照 | `core:model`、`core:data-api`、`core:database` | `TrackedDate` | 未来设置/日历 | Widget refresh | 目标 | 无 | 目标 | 无 | 非同步前置 | 无 | 日期和健康信息组合暴露 | 时区、DST、迁移、审计 | 0% | P2（1.0，非 MVP） | Room、时间 API |
| 本地加密备份 | 当前不存在 | Featherline 快照 | `feature:backup` | Backup envelope | Settings | 用户触发 | 目标 | 无 | 无 | 无 | 文件 | Biometric 可选 | 密钥丢失无法恢复 | tamper、wrong password、version | 0% | P2 | Keystore、crypto |
| Google Drive appData | 当前不存在 | Featherline 快照 | `core:sync`（仅云 provider） | Encrypted snapshot | Settings | WorkManager 候选 | 目标 | 无 | 无 | 无 | Drive | OAuth、网络 | OAuth/冲突/删除/隐私 | fake gateway、恢复演练 | 0% | P3 | 本地备份格式、明确决策后 |

## 结论

- P0：先处理许可证来源冲突、备份规则和数据模型时间语义。
- P1：优先稳定本地模型、提醒、Wear 协议和手机数据库接口。
- P2：Health Connect、Glance 和加密本地备份按独立任务实现；Tracked Date 暂定 P2/1.0，待产品所有者确认，明确不进入 MVP。
- P3：云同步不应先于本地导出、加密文件格式和冲突恢复。
