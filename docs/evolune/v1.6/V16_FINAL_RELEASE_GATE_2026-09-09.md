# Evolune v1.6 — 最终发布门禁（2026-09-09）

状态：`PASS / RELEASED AS v1.6.0`。项目负责人已于 2026-09-09 在真实手表完成人工检查并
批准发布；独立最终复审与 2026-09-10 封版门禁通过。

## 候选身份

| 项目 | 值 |
|---|---|
| 工作分支 / HEAD | `main` / `df12329278eafa488713edf202554cdbd523b8d0` |
| 工作树 | dirty；包含未提交 v1.6 实现与证据，尚无候选提交 |
| 构建 variant | Phone/Wear `release` |
| 版本 | Phone `1.6.0 (101060000)`；Wear `1.6.0 (1101060000)` |
| 候选目录 | `release-artifacts/v1.6.0-rc/20260909-224735` |
| Phone SHA-256 | `E964FC8A89711B4F0F68B9BC93EB185BF77189E41CC0145F53E77DCD94A76665` |
| Wear SHA-256 | `B344C07CCB67DFEDCCE294EC8A655F6229B65D6C36A158E78461C25DBB8D5BA4` |
| 签名证书 SHA-256 | `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08` |
| 签名验证 | 两个 APK 均通过 APK Signature Scheme v2，单一 signer |

用户提供的 PowerShell 日志包含多轮成功构建；先前候选均被本表的
`20260909-224735` 取代。日志中的 `System.Management.Automation.RemoteException` 是 PowerShell 对
`apksigner` 警告流的包装标签；命令继续输出 `Verifies`、v2 `true`、单一 signer，并最终以
`BUILD SUCCESSFUL` 和 Candidate ready 结束，因此不构成签名失败。

## Fresh 自动验证

- 正式构建：152/152 tasks executed，`BUILD SUCCESSFUL in 2m34s`。
- 当前源码完整 Debug 门禁：共 863/863 JVM tests，0 failures/errors/skipped；Phone/Wear Lint 0 error；161 tasks 全部执行。
- 最终签名 Release：152/152 tasks executed，`BUILD SUCCESSFUL in 2m31s`。
- Pixel 11 Pro reminder receiver：7/7 instrumentation tests 通过。
- Wear 圆形安全区专项：90/90；Debug、AndroidTest、Lint 共 78 tasks，全部通过。
- `git diff --check` 退出码 0；仅有既存 LF/CRLF 提示。
- 本次修复没有修改 PK 数学模型、数据库 schema、备份格式或 occurrence 权威语义。

## 用户现象、根因与修复

| 现象 | 已证实根因 | 修复与结果 |
|---|---|---|
| Phone 选择器只有一个旧 Widget | 原实现为单 provider，配置页在进入时过早返回成功；后来产品要求明确为独立功能入口 | 拆为今日计划、下一次服药、当前 E2、E2 趋势四个 provider；配置只在“应用”后成功，取消/返回不误建实例 |
| Wear 添加界面黑色条目/旧加号 | 三个新服务缺少独立 preview metadata，旧服务及新服务仍引用旧 HRT 资源；另有截图实际是系统“添加卡片”的加号占位页 | 四个 Tile 均有功能预览和 Evolune 月牙品牌资源；系统加号保留为系统 UI；最终 APK 注册与资源已核对 |
| Current E2/Next Dose Tile 彩色面板两侧像被裁切 | 168dp 面板在真实表 206dp 逻辑视口仅剩约 19dp/侧，20dp 圆角在圆屏视觉边界中过紧；旧资源版本又允许宿主保留缓存 | 资源版本升至 7，面板宽度改为 156dp，共用 24dp 圆角；真表像素边界为 x=53..384，左右各 53px，圆角连续无裁切 |
| Wear“跳过本次”后未来提醒仍可能出现 | 旧实现只取消已经显示的通知，没有持久化 occurrence 抑制，也没有阻止对应未来 Alarm 投递 | Phone 按 plan/slot/scheduledAt 持久化跳过，验证 occurrenceId/notificationId，取消精确 PendingIntent，重排与 Receiver 双重过滤；旧 v1.5 无 slotId 闹钟保持兼容 |

## Phone Widget 交付矩阵

冻结规格原有五项；用户随后明确取消独立“今日进度”，其完成度并入“今日计划”头部。因此最终产品入口为
四个，不能再用旧五项数字衡量。四项均为独立 provider，且每个实例都有外观配置。

| 功能 | 源码/注册/打包 | 系统发现与配置 | 显示/刷新/点击 | 升级兼容 | 当前结论 |
|---|---|---|---|---|---|
| Evolune-今日计划 | PASS | 模拟器 PASS；独立入口、应用/取消流程与滚动列表有截图 | 模拟器 PASS；v1.5 行布局、对勾、滚动和入口已验证 | 真机 `install -r` 后既有实例保留 | PASS（模拟器视觉 + 真机覆盖） |
| Evolune-下一次服药 | PASS | 模拟器 PASS；独立入口、外观预览和居中修复有截图 | 模拟器 PASS；只读/可用动作由 action-time gate 控制 | 同上 | PASS（模拟器视觉 + 真机覆盖） |
| Evolune-当前 E2 | PASS | 模拟器 PASS；独立入口、外观预览和居中修复有截图 | 模拟器 PASS；数值与时间显示已验证 | 同上 | PASS（模拟器视觉 + 真机覆盖） |
| Evolune-E2 趋势 | PASS | 模拟器 PASS；独立预览柱体从 X 轴向上 | 模拟器 PASS；轴交汇、三标签均分、历史/预测颜色、75% 峰值和圆角柱已验证 | 同上 | PASS（模拟器视觉 + 真机覆盖） |

真实 Pixel 11 Pro 已保留数据覆盖安装候选，设备回读 APK 哈希与候选完全一致，首次安装时间保留，冷启动
成功且无 FATAL/ANR；系统注册四个 provider，四个旧宿主实例仍存在。最新候选未在真实 Pixel 上重新逐个
创建四个新实例，产品视觉证据来自隔离 Pixel Fold 模拟器。

## Wear Tile 交付矩阵

| 功能 | 源码/注册/打包 | 系统发现/添加 | 实际显示/刷新/点击 | 升级兼容 | 当前结论 |
|---|---|---|---|---|---|
| Evolune-下一次服药 | PASS | 真表 PASS，既有新 Tile 保留 | 真表 READY 渲染、资源 7 圆角和状态刷新 PASS；动作对话框与确认/跳过协议已有证据 | `install -r` 后实例保留 | PASS |
| Evolune-当前 E2 | PASS | 真表 PASS，既有新 Tile 保留 | 真表 READY 渲染、资源 7 圆角和状态刷新 PASS | `install -r` 后实例保留 | PASS |
| Evolune-今日计划 | PASS | 系统服务列表可发现；隔离模拟器添加 PASS；负责人真表人工验收 PASS | 隔离模拟器 READY 渲染 PASS；与另两项共用资源 7 容器；负责人真表人工验收 PASS | 旧组件未删除 | PASS |
| 旧 `DoseTileService` 曲线 Tile | PASS；组件身份保留 | 既有真表实例保留 | 新设计、目标范围底色、操作入口及省电策略已有模拟器/真表证据 | PASS | 兼容项，不计入三项新 Tile |

## Wear Complication 交付矩阵

| 功能 | 源码/注册/打包 | 系统发现 | 实际显示/点击 | READY/STALE/无数据 | 当前结论 |
|---|---|---|---|---|---|
| Evolune-下一次服药 | PASS，`SHORT_TEXT`、品牌图标、15 分钟兜底 | 隔离模拟器选择器、真表 package manager、负责人真表人工验收 PASS | 负责人真表人工验收 PASS | JVM 合同 PASS | PASS |
| Evolune-今日进度 | PASS，`SHORT_TEXT`、品牌图标、15 分钟兜底 | 隔离模拟器选择器、真表 package manager、负责人真表人工验收 PASS | 负责人真表人工验收 PASS | JVM 合同 PASS | PASS |
| Evolune-当前 E2 | PASS，`SHORT_TEXT`、品牌图标、15 分钟兜底 | 隔离模拟器选择器、真表 package manager、负责人真表人工验收 PASS | 模拟器真实表盘显示/点击及负责人真表人工验收 PASS | JVM READY/STALE/`--` 合同 PASS | PASS |

真表当前使用 Samsung Basic Dashboard，四个槽位均已有其他 provider。Samsung 固件未公开可直接启动的
Complication chooser intent；本轮只读取表盘/槽位并退出，没有替换表盘、写入槽位或清除宿主数据。

## 覆盖升级和产物回读

- Galaxy Watch：最终 Wear APK `install -r` 成功；首次安装时间保持 `2026-08-22 12:28:12`，设备回读
  SHA-256 与候选一致；25 个既有 Tile 及顺序保留。
- Pixel 11 Pro：最终 Phone APK `install -r` 成功；首次安装时间保持 `2026-09-02 19:15:43`，设备回读
  SHA-256 与候选一致；四个旧 Widget 实例保留。
- 未卸载、未清数据、未删除旧 Tile、未重置手机或手表。

## 剩余问题分级

- P0：无。
- P1：无；项目负责人已补齐真实手表人工验收并批准进入发布流程。
- P2：无。真实 Pixel 的四个新 Widget 未重复采集逐项新建截图；隔离 Pixel Fold 完整宿主流程、真实 Pixel 同签名覆盖安装与 APK 回读及负责人批准构成接受的最终证据组合。
- P3：可选补充真实 AlarmManager 队列和连续并发跳过压力测试；编译器 deprecation 与 SDK XML 工具版本提示不影响构建和签名。

结论：最终签名候选已通过技术验证、独立最终复审、项目负责人产品/真表验收和真实 Phone/Wear
保留数据覆盖安装，允许提交、标记 `v1.6.0` 并发布两份已验证 APK。
