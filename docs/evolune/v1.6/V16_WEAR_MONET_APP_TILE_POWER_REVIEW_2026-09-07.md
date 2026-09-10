# v1.6 Wear Monet、旧曲线 Tile 与省电复审（2026-09-07）

## 结论

本轮要求的 Wear 模拟器范围已完成并通过：Wear App、三个新 Tile 和旧 E2 浓度曲线 Tile 共用一套
系统 Monet/八组预制配色；Wear App 的配色入口位于内容末尾；旧曲线 Tile 已改为当前设计语言并重新
安排图表与操作块间距。省电边界已落实为事件优先、前台按截止时间刷新、15 分钟被动兜底。

该结论只覆盖 Debug Wear 模拟器与构建产物，不代表真实手表最终验收，也不代表整个 v1.6 `DONE`。

## 修复内容

- `WearAppearance.kt` 提供“系统颜色、蓝、紫罗兰、樱花、薄荷、青绿、琥珀、中性、薰衣草”。系统颜色
  读取 Wear OS 动态色，八组预设与 Phone Widget 的颜色角色一致；选择结果持久保存，并请求四个 Tile
  与三个 Complication 立即重绘。
- `WearAppActivity.kt` 与 `activity_wear_app.xml` 将浓度、最近记录、待服计划和配色设置分为有层级的圆角
  tonal panel；标题、数值和状态按重点分级。配色卡位于待服内容之后，即整个页面底部。
- `WearGalleryTileService.kt` 的下一次服药、今日计划、当前 E2 三个 Tile 读取同一配色，并保留圆形屏幕
  安全区、居中和无数据/断连提示。
- `DoseTileService.kt` 保留原组件完整名称，避免覆盖升级丢失旧实例；旧血药浓度曲线使用 tonal chart、
  轴线、目标范围色带、当前点和配色操作块；已移除曲线下面的整体彩色底板，仅保留目标范围示意色。
  标题缩短为 `E2 · 数值`，图表与按钮间距由 4dp 增为 12dp，按钮高度由
  52dp 收至 46dp，按钮宽度和间距同时调整，解决两个按钮靠上、拥挤的问题。
- `tile_preview.xml` 改为真实曲线与操作块示意图；服务标签保持 `Evolune-E2 浓度曲线`。
- 三个 Complication 的 `UPDATE_PERIOD_SECONDS` 从 300 秒调整为 900 秒；四个 Tile 的 freshness 统一为
  900 秒。手机数据或用户操作到达时仍立即请求刷新。

## 省电核对

- Wear manifest 未声明 `WAKE_LOCK` 或 `FOREGROUND_SERVICE`。
- Wear 生产代码没有 `AlarmManager`、精确闹钟、周期 WorkManager、JobScheduler 或无限轮询。
- `WearAppActivity` 只在 `onStart` 到 `onStop` 之间注册状态接收器和安排下一次刷新；`onStop`/`onDestroy`
  移除回调。下一次刷新来自同步超时、缓存陈旧或浓度陈旧的实际截止时间，不使用固定高频计时器。
- 同步超时回调只在主动请求手机数据后存在 30 秒；服药操作反馈清除只在用户操作后存在 1 秒，均非
  常驻任务。
- Tile/Complication 使用 15 分钟低频兜底；Data Layer 新快照、操作结果和配色变化走事件更新。

## 构建与自动验证

- 分支：`main`
- HEAD：`df12329278eafa488713edf202554cdbd523b8d0`
- variant：Debug
- JVM：85/85，0 failures/errors/skipped，11 suites
- Debug APK 与 AndroidTest APK：69 tasks，`BUILD SUCCESSFUL`
- 模拟器 fixture：1/1，`OK (1 test)`
- diff check：无 whitespace error；仅现有 Git LF/CRLF 提示
- 模拟器 logcat：83 条相关记录，0 个 `FATAL EXCEPTION`/ANR 命中

原始记录：

- `V16_WEAR_MONET_POWER_LAYOUT_FINAL_BUILD_2026-09-07.log`
- `V16_WEAR_MONET_POWER_LAYOUT_INSTRUMENTATION_2026-09-07.log`
- `V16_WEAR_MONET_POWER_LAYOUT_APK_DEVICE_IDENTITY_FINAL_2026-09-07.log`
- `V16_WEAR_MONET_POWER_LAYOUT_LOGCAT_FINAL_2026-09-07.log`

## 产物身份

- APK：`wear/build/outputs/apk/debug/wear-debug.apk`
- applicationId：`io.github.yingqiu0871.evolune.debug`
- versionName：`1.6.0-debug`
- versionCode：`1101060000`
- SHA-256：`E8E360D4B196246C1BC4BB31E2D3C71126A920BA8FF8E73A36F45BE4B4137157`
- `emulator-5554` 设备端 `base.apk` SHA-256 与本地完全一致。
- `firstInstallTime=2026-09-07 12:09:33`，`lastUpdateTime=2026-09-07 12:34:03`，证明
  `install -r` 覆盖安装，未卸载或清数据。

Merged manifest 确认四个 TileService 均存在且分别引用预览资源；三个 Complication 的刷新 metadata 均为
`900`。测试 fixture 通过现有 Debug 实例请求宿主更新，没有删除旧 Tile。

## 模拟器视觉证据

- `V16_EMU5554_LEGACY_CURVE_TILE_SPACED_POWER_FINAL_2026-09-07.png`：旧 E2 曲线 Tile 的新标题、
  tonal 曲线、图表到按钮的 12dp 间距及下移后的两个操作块。
- `V16_EMU5554_WEAR_APP_POWER_PALETTE_TOP_FINAL_2026-09-07.png`：Wear App 顶部浓度层级和樱花配色。
- `V16_EMU5554_WEAR_APP_POWER_PALETTE_BOTTOM_FINAL_2026-09-07.png`：待服内容之后的底部配色卡。
- `V16_EMU5554_CURVE_PICKER_BEFORE_ADD_FINAL_2026-09-07.png`：旧曲线服务的系统 Picker 预览。
- `V16_EMU5554_WEAR_NEXT_DOSE_READY_CENTERED_FINAL_2026-09-07.png`、
  `V16_EMU5554_WEAR_TODAY_PLAN_READY_CENTERED_FINAL_2026-09-07.png`、
  `V16_EMU5554_WEAR_CURRENT_E2_READY_CENTERED_FINAL_2026-09-07.png`：三个新 Tile 的 READY 居中布局。

## 尚未关闭

- P0：无新增源码/模拟器阻断项。
- P1：真实手表上的覆盖升级、四个 Tile 实例保留、实际 Data Layer 数据刷新和耗电观察仍待最终实机。
- P1：三个 Complication 仍需在支持 `SHORT_TEXT` 的真实表盘槽位逐一验证发现、选择、READY/STALE、
  点击和配色。
- P2：Release 签名构建仍依赖当前环境未提供的签名变量；不得用卸载绕过签名兼容。
- P3：Kotlin/Android API 仅有既有 deprecated warning，不影响本轮行为。

因此本轮 Wear 模拟器设计和功耗策略通过专项复审；v1.6 总体验收继续保持 `IN_PROGRESS`，等待最后的
真实设备证据。

> 后续状态：Phone/Wear 模拟器网络 Data Layer 配对已在同日完成；重复 snapshot 的 Tile pending 缺陷、
> 浓度时钟差和圆屏动作按钮换行也已修复。当前产物身份、设备证据与仍缺的 Release Tile/Complication
> 验收以 [V16_WEAR_PAIRED_TILE_REFRESH_REVIEW_2026-09-07.md](V16_WEAR_PAIRED_TILE_REFRESH_REVIEW_2026-09-07.md)
> 为准。

## 追加：Wear “跳过本次”动作

Phone 原有 skip 语义是取消提醒通知，不产生 DoseEvent。新增的 Wear 路径在快照中携带对应通知 ID，Wear
App 在可确认的待服记录下显示两个操作块：`确认服药` 继续走 occurrence confirmation；`跳过本次` 发送
通知级 Data Item。Phone 端只接受协议版本正确、启用方案、匹配 slot、计划时间和可推导通知 ID 的请求，
然后调用同一个 `NotificationHelper.cancelNotification`，处理完删除一次性 Data Item。

这保证手表不会成为第二事实来源，也不会把“跳过”误写成服药或修改 occurrence。断连、未来数据未同步、
无通知 ID 或失配请求均不显示/不执行跳过动作。

本轮跨模块编译与 JVM 测试通过：Experience Core、Phone Debug、Wear Debug 均编译；Experience Core、
Phone JVM、Wear JVM 均 `BUILD SUCCESSFUL`，Wear fixture `1/1 OK`。最新 Wear APK 为
`E8E360D4B196246C1BC4BB31E2D3C71126A920BA8FF8E73A36F45BE4B4137157`，已保留数据覆盖安装到
`emulator-5554`。

## 关于截图中没有操作按钮

截图中的旧曲线 Tile 正处于 `未连接到手机` 状态。此状态没有可验证的 occurrence 快照，因此按契约只显示
同步提示，不显示药物操作块；这不是透明图表背景修改造成的。连接手机并收到 `UPCOMING`/`DUE` occurrence
后，旧 Tile 才按已有计划显示操作块，Wear App 的待服记录行才进入可点击的确认流程。

当前 v1.6 Wear 契约已补充通知级“跳过本次”路径：它携带 occurrence/plan/slot/计划时间和 Phone 通知 ID，
由 Phone 校验同一提醒并只取消通知，不写 DoseEvent、不改变 occurrence 状态。Wear App 仅在同步且可确认的
记录下显示“确认服药 / 跳过本次”；断连或无数据时仍不伪造操作。
本轮同时构建 Phone Debug APK，SHA-256 为
`C7C921E9F74760FE08E33C4DF506048FDDE562EF6D6A862A121A39F29D770E44`；Phone/Wear 的 Debug 产物均为
`1.6.0-debug`，Release 签名流程仍未执行。

## 配对复核（2026-09-07）

按用户指出的设备组合重新核对 `Wear_OS_Large_Round`（`emulator-5554`）与 `Pixel_10_Pro_Fold`（`emulator-5558`）。当前实际状态仍是未配对：Wear `WearConnectivityService` 报告 `mIsCompanionConnected=false`、`Device=disconnected`、`Companion set=false`、地址未绑定；两端 Bluetooth bonded devices 均为 0，Phone Companion Device Manager 为空。Wear 上存在的 `com.google.wear.services` self-managed association 是系统历史记录，不是 5558 的已连接证明。配对助手能打开，但只列出过期的 `featherline_wear_api35` 配置，未发现当前 `Wear_OS_Large_Round`；没有对设备执行卸载、清数据或重置。原始输出见 [V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log](V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log)。

因此本轮可以确认“跳过本次”源码协议和 Phone 端通知取消实现已落地并通过自动化验证，但无法在当前未配对组合上宣称真实按钮联调通过。缺少 Pair Wearable 助手完成后的 Data Layer 证据仍是 P1；最终验收继续保持 `IN_PROGRESS`。

## 追加：待服条目与操作面板重排（2026-09-08）

- 待服列表不再把“确认服药 / 跳过本次”两个有色底板直接嵌进每张卡片；整张待服卡现在是统一的点击入口。
- 点击可操作条目后显示“本次用药”面板，上方以三行以内说明药物、剂量、途径和计划时间；底部使用无独立底板的文字操作，左侧“跳过本次”，右侧“确认用药”。返回键或点按面板外仍可取消，避免额外挤占一枚取消按钮。
- 浓度、最近服药、待服条目和配色卡统一使用 `24dp` 圆角；待服卡统一使用 `16dp` 水平、`14dp` 垂直内边距和 `76dp` 最小高度。
- 处理中提示缩短为“确认处理中，点按重试”，避免圆屏中出现四至五行的失衡卡片。

最终 Debug Wear 构建已强制执行全部 41 个任务，`BUILD SUCCESSFUL`。APK 以保留数据的 `install -r`
覆盖到 `emulator-5554`，本地和设备 SHA-256 均为
`5D5CE0A7CE4CE9A28B3A24CAA9103FB59D0E03E00444EEC216C059823B864FF2`。

证据：`V16_WEAR_ENTRY_ACTION_FRESH_BUILD_20260908.log`、
`V16_WEAR_ENTRY_ACTION_CARD_FINAL_2026-09-08.png`、
`V16_WEAR_ENTRY_ACTION_CARD_FINAL_UI_2026-09-08.xml`。当前 Debug 数据存在一条协议待回执操作，故本轮
运行截图覆盖卡片终态；新面板的左右操作顺序由生产代码和成功构建确认，仍需在下一次无 pending 的
Release/实机 occurrence 上补一张运行截图。
