# Evolune v1.6 — G 集成与发布证据

记录日期：2026-09-06
阶段：G `DONE`（下文保留早期集成观察；最终候选和关闭结论见 `V16_FINAL_RELEASE_GATE_2026-09-09.md`）
工作目录：`current/Evolune-v1.2`
基线提交：`df12329278eafa488713edf202554cdbd523b8d0`（`v1.5.0`）

本文件只记录已经在当前工作树完成的集成观察。它不把 debug APK、模拟器或
诊断基线当作签名发布候选，也不关闭真实 Launcher、真表、Data Layer 配对和发布审阅门槛。

## G-01 — 稳定包与候选包身份

| 设备 | 包 | 观察结果 |
|---|---|---|
| Pixel 7 API 35 (`emulator-5556`) | `io.github.yingqiu0871.evolune` | 已有稳定 `1.5.0`，versionCode `101050000` |
| Pixel 7 API 35 (`emulator-5556`) | `io.github.yingqiu0871.evolune.debug` | debug 候选安装成功，`1.5.0-debug`，target SDK 36 |
| Wear OS API 37 (`emulator-5554`) | `io.github.yingqiu0871.evolune.debug` | debug 候选安装成功，`1.5.0-debug`，versionCode `1101050000`，target SDK 36 |

候选使用 `.debug` application ID，不覆盖 Pixel 7 上的稳定包。v1.6 版本号、release
签名和可覆盖升级仍需 G-05 发布候选步骤完成后再冻结。

## G-02 — 冷启动与运行日志

- [V16_G_PIXEL7_STABLE_COLD_LAUNCH_2026-09-06.log](V16_G_PIXEL7_STABLE_COLD_LAUNCH_2026-09-06.log)：Pixel 7
  上的稳定 `1.5.0` 包冷启动成功，主页显示当前浓度、主页/记录/方案/设置入口，进程日志无致命关键字。
- Pixel 7 debug 候选冷启动后显示首次使用引导；`uiautomator` 根节点包名为
  `io.github.yingqiu0871.evolune.debug`，应用进程存在，应用进程日志没有
  `FATAL EXCEPTION`、`AndroidRuntime`、`NoClassDefFoundError` 或 `ANR`。
- Wear API 37 debug 候选冷启动后显示 Evolune Wear App、连接状态和“估算浓度暂不可用”，
  这与无手机快照的可用性状态一致；进程存在且无上述致命日志。
- 此项只证明 debug 包的冷启动和无数据状态；没有证明 Data Layer 已配对或收到生产快照。

## G-03 — Wear 服务清单

在 `emulator-5554` 上对 `io.github.yingqiu0871.evolune.debug` 执行 `dumpsys package`，
确认旧 `DoseTileService` 与新增的 `NextDoseTileService`、`TodayPlanTileService`、
`CurrentE2TileService` 均声明 `BIND_TILE_PROVIDER`；三个 Complication provider
`NextDoseComplicationDataSource`、`TodayProgressComplicationDataSource`、
`CurrentE2ComplicationDataSource` 均声明 `BIND_COMPLICATION_PROVIDER`。

这证明 manifest 注册进入 APK；真实 Tile 选择、表盘槽位、Ambient 和后台刷新仍未完成。

## G-04 — 设备性能与数据库保持性

- [V16_A_G_PIXEL7_PERF_2026-09-06.log](V16_A_G_PIXEL7_PERF_2026-09-06.log)：Pixel 7 API 35，
  `V15WidgetPerformanceDeviceTest` 与 `V15PkPerformanceDeviceTest` 2/2 通过。
  当前轮 `PK-EMPTY/STEADY/DENSE` 的 Widget 加载中位数约为 `7.51/9.07/17.97 ms`，PK 计算中位数
  约为 `0.02/245.80/2201.22 ms`；同一快照下另记录了 1/3/5 实例映射批次。不同运行的设备热身
  会影响诊断值，它们不是 v1.6 发布阈值。
- [V16_G_PIXEL7_MIGRATION_2026-09-06.log](V16_G_PIXEL7_MIGRATION_2026-09-06.log)：Pixel 7 API 35，
  `Batch8CPreservedUpgradeTest` 2/2 通过，覆盖 v2→v3 Room 保持性和新库控制路径。

## G-05 — Release 构建门槛

- [V16_G_RELEASE_ASSEMBLE_2026-09-06.log](V16_G_RELEASE_ASSEMBLE_2026-09-06.log)：使用当前工作树执行
  `:app:assembleRelease :wear:assembleRelease` 成功，Phone/Wear APK 均通过 `apksigner verify`，
  使用同一 Release 证书，APK Signature Scheme v2 验证退出码均为 0。
- 产物 SHA-256、大小、签名证书指纹均已写入日志。该次构建发生在版本身份更新之前，使用的是
  `1.5.0` / `101050000` / `1101050000`，因此不能标记为 `v1.6.0` 发布候选。

## G-06 — v1.6.0 版本身份更新

- 已将 `build.gradle.kts` 的共享版本身份更新为 `1.6.0`；Phone/Wear versionCode 为
  `101060000`/`1101060000`。
- [V16_VERSION_BUMP_2026-09-06.log](V16_VERSION_BUMP_2026-09-06.log)：身份校验、Experience Core、
  Phone 和 Wear JVM 回归均通过。
- 之前生成的 Release APK 保留为版本更新前的证据；必须用新身份重新生成并验证 APK，才能进入
  v1.6.0 release-candidate review。

## G-07 — v1.6.0 Release Candidate 构建

- [V16_RELEASE_BUILD_1.6.0_2026-09-06.log](V16_RELEASE_BUILD_1.6.0_2026-09-06.log)：v1.6.0 身份校验、
  JVM 回归和 Pixel 7 Android 回归均通过；Phone/Wear Release APK 已重新生成。
- Phone `1.6.0` / `101060000` 与 Wear `1.6.0` / `1101060000` 均由 `aapt2 dump badging` 确认；
  两个 APK 的 `apksigner verify` 均通过，使用同一 Release 证书。
- PowerShell 包装脚本曾因把 SDK 警告 stderr 传入 `Tee-Object` 而误抛异常，但 Gradle 实际结果为
  `BUILD SUCCESSFUL`，不能把该包装异常当作 Release 构建失败。

## G-08 — v1.5.0→v1.6.0 安装升级与冷启动

- [V16_G_UPGRADE_PIXEL7_WEAR_2026-09-06.log](V16_G_UPGRADE_PIXEL7_WEAR_2026-09-06.log)：Phone Pixel 7
  使用同一 Release 签名执行 `adb install -r` 成功，包身份为 `1.6.0` / `101060000`；未卸载、
  未清除数据，冷启动后仍显示当前浓度、主页和记录入口，进程日志无致命关键字。
- Wear OS API 37 以 Release APK 安装成功，包身份为 `1.6.0` / `1101060000`，冷启动显示无手机
  快照时的断连/无浓度状态，进程日志无致命关键字。该 Wear 观察是模拟器清洁安装，不是旧 Wear
  用户数据升级。
- 该记录证明同签名覆盖安装和基本冷启动；完整 Room 数据、Widget/Tile 配置保留、备份恢复和
  Data Layer 配对仍需单独验证。

## G-09 — 真实 Phone 与 Pixel Launcher 宿主观察

- [V16_G_REAL_PIXEL11_2026-09-06.log](V16_G_REAL_PIXEL11_2026-09-06.log)：在真实 Pixel 11 Pro
  (API 37) 上使用同一 Release 签名执行 `adb install -r`，v1.5.0→v1.6.0 覆盖安装成功；
  未卸载、未清除数据，包身份为 `1.6.0` / `101060000`，冷启动主页可见，进程日志无致命关键字。
- Pixel Launcher 的 Widget picker 可搜索并显示 `Evolune / 月序用药微件`（2×2），拖入真实主屏后
  看到 `今天 3/4 完成`、`E2 ~160`、四段进度和 occurrence 行；点击未来 occurrence 的入口打开
  Phone 应用，未直接写入记录。该证据覆盖一个真实 Launcher 的添加、展示和只读入口路径。
- 当前没有连接真实圆形手表；Wear Tile、表盘槽位和 Phone–Wear Data Layer 仍只能保持未完成状态。

## 当前 G 阻塞项

1. v1.6.0 Release Candidate APK 已生成并通过签名验证；Pixel 7 模拟器和真实 Pixel 11 Pro 的同签名
   覆盖安装、基本冷启动均通过，仍需完整数据/配置保留和最终独立发布审阅。
2. 目前只有一个真实 Pixel Launcher 的添加/展示/只读入口证据；仍没有第二个真实 Launcher、圆形真表、
   真实表盘槽位、Phone/Wear Data Layer 配对回执和后台刷新记录。
3. A-07 已有同一快照下 `1/3/5 Widget` 映射观察，但仍缺多次完整刷新、多 Tile/Complication 展示面、
   Data Layer 请求和真实后台唤醒的重复采样；当前 G-04 仍是固定数据集设备观察。
4. 因此 G 保持 `IN_PROGRESS`，候选实现和构建通过不能替代发布审阅。
