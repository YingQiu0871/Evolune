# v1.6 Wear 配对、Tile 刷新与服药动作复审（2026-09-07）

## 当前结论

`Wear_OS_Large_Round`（`emulator-5554`）与 `Pixel_10_Pro_Fold`（`emulator-5558`）已经通过 Wear OS
模拟器网络/Data Layer 通道配对。GMS 双端初始同步完成，Wear 收到 Phone 的计划与 v1.6 snapshot；Bluetooth
bond 与 `WearConnectivityService` 仍显示未连接是模拟器隧道的传输特征，不再作为否定配对的依据。

本轮修复了重复 snapshot 导致 Tile 永久停在“正在同步”的状态机缺陷、模拟器小幅时钟差导致当前浓度误报
不可用，以及 Wear App 两个服药按钮在圆屏上换行的问题。最终 Debug Wear App 已显示真实 `0.69 pg/mL`，
“确认服药”和“跳过本次”均为单行居中按钮。

现有三个新 Tile 是旧 Release 包实例；最新 Debug APK 不会替换另一个 applicationId 的实例。临时停用 Release
后，管理列表只剩 Debug 的 legacy 曲线 Tile 与系统联系人 Tile，证明组件归属。系统槽位已满，且本轮遵守
不删除旧 Tile 的边界，因此没有把三个 Debug Tile 以真实配对数据重新加入系统轮播。最终验收继续保持
`IN_PROGRESS`。

## 已证实根因与修复

1. `WearAppStore.acceptSnapshot` 原先只处理 `Applied`。Tile 主动请求后，Phone 返回相同 revision 与内容时结果为
   `Duplicate`，代码直接返回，遗留 `pending_since`，且 `shouldRefreshAfterSnapshot` 不请求 Tile 重绘。现在
   `Duplicate` 被视为一次有效权威响应：刷新接收时间、恢复 `CONNECTED`、清除 pending/request/failure，并
   刷新三个新 Tile；`Older` 与 `Rejected` 仍被拒绝。
2. snapshot 的浓度计算时间比 Wear 时钟快约 286 秒。原逻辑对任意未来 1ms 都返回 `UNAVAILABLE`。现在允许
   5 分钟配对设备时钟偏差，年龄按 0 计算；超过 5 分钟仍拒绝，15 分钟陈旧规则不变。
3. 圆屏中两个等宽按钮每个约 72dp，旧的 13sp 字号加两侧各 13dp padding 会让四个汉字换行。现在使用
   12.5sp、两侧 6dp padding，并强制单行，保留 40dp 高度和中心 gravity。
4. API 37 宿主收到 snapshot 后还会查询本应用 active Tile，并在按组件刷新之外按 instance ID 精确刷新，
   同时保留低频 15 分钟 freshness。该路径只在事件到达时运行，不增加轮询或常驻任务。

## 设备与产物证据

- 分支：`main`
- HEAD：`df12329278eafa488713edf202554cdbd523b8d0`
- variant：Debug
- APK：`wear/build/outputs/apk/debug/wear-debug.apk`
- applicationId：`io.github.yingqiu0871.evolune.debug`
- versionName/versionCode：`1.6.0-debug` / `1101060000`
- APK 与设备 `base.apk` SHA-256：`F016BD17F7218D2D11E8D3B880F5313C7ED046C16AFFF2B73EE8C3E24ED3CBE3`
- AndroidTest APK SHA-256：`D395B10C5F9706B1D54CB5028F9CA5D2AA80CA3573EE9F83C69B47F69CEEBDA1`
- 安装方式：`adb install -r`，未卸载、未清数据、未移除旧 Tile、未重置设备。
- Release 包在临时归属检查后已重新启用；`RELEASE_DISABLED=False`。

自动验证：Wear JVM `86/86`，0 failures；Debug 与 AndroidTest APK `69 tasks BUILD SUCCESSFUL`。设备安全测试
排除了会覆盖真实 snapshot 的视觉 fixture，manifest、launcher icon、真实配对 Data Layer 计划往返共 `3/3`
通过。Merged manifest 确认旧服务、三个新 Tile 与三个 Complication 均注册；四个 Tile 都带 preview metadata
并引用统一品牌图标。

原始记录：

- `V16_WEAR_PAIRING_TILE_CLOCK_FINAL_BUILD_2026-09-07.log`
- `V16_WEAR_PAIRED_DEVICE_SAFE_TESTS_2026-09-07.log`
- `V16_WEAR_FINAL_APK_INSTALL_MANIFEST_AUDIT_2026-09-07.log`
- `V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log`

运行截图：

- `V16_WEAR_APP_FINAL_PAIRED_TOP_2026-09-07.png`：真实浓度与已同步状态。
- `V16_WEAR_APP_FINAL_ACTIONS_SINGLE_LINE_2026-09-07.png`：确认/跳过单行居中。
- `V16_WEAR_PAIRED_TILE_RUNTIME_2026-09-07.png`：保留的 legacy 曲线 Tile 显示真实数据。
- `V16_WEAR_DEBUG_ONLY_MANAGEMENT_CORRECT_2026-09-07.png`：停用 Release 时的 Debug 实例归属证据。

## 当前矩阵

| Surface | 源码/打包 | 系统发现 | 配对真实数据 | 添加后显示 | 刷新/点击 | 升级兼容 | 结论 |
|---|---|---|---|---|---|---|---|
| Legacy E2 曲线 Tile | PASS | PASS | PASS，1.8 pg/mL | PASS | 药物入口可见 | PASS，实例保留 | PASS（模拟器） |
| Evolune-下一次服药 Tile | PASS | PASS | snapshot 已到达 | 现有实例为旧 Release，仍同步中 | Debug 状态机已修复；缺 fresh 系统实例 | 旧实例保留 | P1 设备证据待补 |
| Evolune-今日计划 Tile | PASS | PASS | snapshot 已到达 | 现有实例为旧 Release，仍同步中 | Debug 状态机已修复；缺 fresh 系统实例 | 旧实例保留 | P1 设备证据待补 |
| Evolune-当前 E2 Tile | PASS | PASS | snapshot 已到达 | 现有实例为旧 Release，仍同步中 | Debug 状态机已修复；缺 fresh 系统实例 | 旧实例保留 | P1 设备证据待补 |
| Wear App 当前 E2 | PASS | launcher 可达 | PASS，0.69 pg/mL | PASS | snapshot 事件刷新 | install-r 保留数据 | PASS（模拟器） |
| Wear App 确认服药 | PASS | App 内可达 | occurrence 已同步 | PASS，单行居中 | 早期联调已发送；本轮未再次写记录 | 不改 occurrence 契约 | PASS（UI），完整结果链沿用既有证据 |
| Wear App 跳过本次 | PASS | App 内可达 | notificationId 已同步 | PASS，单行居中 | DataItem 曾被 Phone 处理并删除；当时无活动通知可观察 | 不写 DoseEvent | P1：有活动通知时补可见取消证据 |
| 3 个 Complication | PASS | manifest/query PASS | snapshot 已到达 | 未逐槽位重做 | 未逐项 READY/STALE/点击 | provider 身份保留 | P1 设备证据待补 |

## 剩余等级

- P0：无已知源码或构建阻断。
- P1：用签名匹配的最新 Release 覆盖手表后，在不删除旧实例的条件下逐项验证三个新 Tile 的真实配对
  READY/刷新/点击；三个 Complication 在支持 `SHORT_TEXT` 的槽位验证发现、READY/STALE、点击和配色；
  有活动 Phone 通知时验证“跳过本次”可见取消结果。
- P2：当前环境缺少 Release 签名变量，无法生成可覆盖现有 Release package 的 APK；Debug applicationId
  不能替代 Release 实例证据。
- P3：现有 Android/API deprecated warning，不影响本轮行为。

没有修改 PK 数学模型、数据库 schema、备份格式或 occurrence 语义。Wear 仍只使用 Phone 权威 snapshot；
5 分钟时钟容差只影响浓度展示判定，不产生或修改事实数据。关键 Release Tile 与 Complication 实机证据未齐，
不得宣告 v1.6 `DONE`。
