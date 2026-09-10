# Evolune v1.6-A — 基线与执行证据

记录日期：2026-09-05
阶段：A `DONE`（下文保留实施当时的历史检查点状态；最终关闭见 `V16_ACCEPTANCE.md`）
工作目录：`current/Evolune-v1.2`
起点提交：`df12329278eafa488713edf202554cdbd523b8d0`（精确匹配 `v1.5.0`）
候选提交：未创建；当前批次仍是工作树文档变更

## A-00 — 批次起点工作树快照

在建立本批次证据文件前记录的工作树范围如下；它用于区分 v1.6 文档规划变更与生产源码：

```text
## main...origin/main
 M TODO.MD
 M docs/evolune/ROADMAP.md
?? docs/evolune/v1.6/V16_ACCEPTANCE.md
?? docs/evolune/v1.6/V16_PLAN.md
?? docs/evolune/v1.6/V16_REVIEW.md
?? docs/evolune/v1.6/V16_SPEC.md
```

该快照没有业务源码、资源、manifest 或构建配置变更；完整批次状态和当前确切差异仍以
后续 `git status --short --branch`、`git diff --name-status` 和候选提交为准。A 阶段尚未创建
候选提交，因此这里不能替代最终 exact diff。

当前批次的可复核命令输出保存在 [A-00-worktree-2026-09-05.log](A-00-worktree-2026-09-05.log)。
该日志还记录了相对于 `df12329278eafa488713edf202554cdbd523b8d0` 的 tracked diff；未跟踪的
v1.6 文档目录由同一份 `git status` 明确列出。

## A-01 — 源码身份

- 命令：`git rev-parse HEAD`；`git describe --tags --exact-match HEAD`
- 结果：`df12329278eafa488713edf202554cdbd523b8d0`；`v1.5.0`
- 工作树：`main`；包含 v1.6 规划、规格、验收和状态文档变更，尚未提交。
- 复核：与 [V16_SPEC.md](V16_SPEC.md) §1、[V16_ACCEPTANCE.md](V16_ACCEPTANCE.md) 当前基线一致。

## A-02/A-03 — 身份校验和 JVM 基线

- 命令：
  `.\gradlew validateEvoluneIdentityAndVersioning :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --no-daemon`
- 环境：Windows 工作区，Gradle Wrapper 9.2.1，2026-09-05；候选提交未创建。
- 首次结果：`BUILD SUCCESSFUL in 11s`；`55 actionable tasks: 1 executed, 54 up-to-date`；
  完整输出见 [A-03-gradle-2026-09-05.log](A-03-gradle-2026-09-05.log)。
- fresh rerun：同一命令加 `--rerun-tasks`，结果 `BUILD SUCCESSFUL in 1m 9s`；
  `55 actionable tasks: 55 executed`；完整输出见
  [A-03-gradle-2026-09-05-rerun.log](A-03-gradle-2026-09-05-rerun.log)。
- 说明：两份日志同时覆盖 `validateEvoluneIdentityAndVersioning`、Experience Core、Phone 和
  Wear JVM 测试；fresh rerun 能证明任务被重新执行，但不能单凭日志证明执行者独立；两份日志也不替代
  connected/device 测试。

## A-04 — 设备观察

| 角色 | ADB 标识 | 型号 | API | 当前观察 |
|---|---|---|---:|---|
| Phone | `adb-67181FDKX002MX-SC0jva._adb-tls-connect._tcp` | Pixel 11 Pro | 37 | Phone `1.5.0`，versionCode `101050000` |
| Wear | `emulator-5554` | `sdk_gwear_x86_64` | 37 | 已连接；测试前需重新确认 v1.5.0 Wear 包身份 |
| Phone | `emulator-5556` | `sdk_gphone64_x86_64` | 35 | Phone `1.5.0`，versionCode `101050000` |
| Phone | `emulator-5558` | `sdk_gphone16k_x86_64` | 37 | 历史 `1.2.2`，不能作为 v1.6 证据 |

这只是连接快照。A 关闭仍需补 API 31 最低支持环境、API 36/目标环境、两个真实
Launcher、真实圆形 Wear 和支持 Complication 的真实表盘。

## A-05/A-06 — 现有链路盘点

只读检查的事实来源和调用链记录在 [V16_SPEC.md](V16_SPEC.md) §3；对应实现为：

- Phone：`WidgetPresentation.kt`、`WidgetWork.kt`、`WidgetAppearance.kt`、`WidgetUi.kt`。
- Wear：`WearAppProtocol.kt`、`DoseTileService.kt`。
- A 阶段盘点时发现的动作缺口已由 B 首批实现修正：`WidgetUiMapper` 现在只为 `DUE` 生成
  `RECORD`，`UPCOMING`/`PAST_UNRECORDED` 进入 `OPEN_APP`；`ContractWidgetQuickActionWork` 对尚未
  成功记录的点击按当前时刻重检 `AVAILABLE`/due-window，旧/stale PendingIntent 不得落库。
  该修正的确定性测试和 Android 回归见 [V16_B_EVIDENCE.md](V16_B_EVIDENCE.md)。Wear 的
  `UPCOMING/DUE` 兼容动作及旧/新组合矩阵仍待后续阶段证据。
- Wear 当前 `WearAppStore.canConfirm()` 允许快照中的 `UPCOMING` 或 `DUE`；这是 v1.3
  兼容动作语义，v1.6 的 Phone Gallery 不复用它。新 Wear Tile 仍必须走 occurrence identity、
  Phone 持久化和回执，不能自行确认成功。
- 当前 Wear 派生状态的 stale 基线为 `WEAR_APP_STALE_AFTER_MILLIS = 15 min` 和
  `WEAR_APP_CONCENTRATION_STALE_AFTER_MILLIS = 15 min`；A 阶段保留这些现有值，尚未把它们
  直接升级为统一的 v1.6 产品阈值。

## A-07 — 性能基线和豁免继承

- 固定数据集：`PK-EMPTY`、`PK-STEADY`、`PK-DENSE`。
- 现有 v1.5 基线原始记录：[V15B_BASELINE_CAPTURE.md](../v1.5/V15B_BASELINE_CAPTURE.md)。
- 记录方式：同一 emulator 会话，2 次 warm-up、7 次 measured passes；本文件不把这些
  数字当作 v1.6 发布阈值。
- v1.5 `Energy/background` 的状态为 `SKIPPED_BY_OWNER`，真实 battery/Wear
  active-background 仍未验证；此豁免不继承为 v1.6 PASS。
- v1.6 仍缺 `EMPTY/STEADY/DENSE × 1/3/5 Widget`、多 Tile/Complication、真实宿主
  刷新延迟、PK 调用数、Data Layer 请求数和后台 wakeup 数的重复采样。
- A-02/A-03 的首次日志包含完整命令输出，但其中多数任务为 `UP-TO-DATE`；需由独立审阅者
  以 fresh rerun 重跑关键 JVM 测试，不能把该日志当作独立复跑。

### 2026-09-06 设备观察补充

- Pixel 7 API 35 (`emulator-5556`) fresh connected run：
  `V15WidgetPerformanceDeviceTest` 与 `V15PkPerformanceDeviceTest` 共 2/2 通过，
  原始记录见 [V16_A_G_PIXEL7_PERF_2026-09-06.log](V16_A_G_PIXEL7_PERF_2026-09-06.log)。
- 该运行记录了 `PK-EMPTY/STEADY/DENSE` 的 Widget load/map、共享快照下 1/3/5 实例批量映射
  和 PK 计算中位数。1/3/5 观察只覆盖同一快照的 UI 映射，不等价于多个真实宿主刷新；多
  Tile/Complication、Data Layer 请求、真实后台唤醒和完整预算仍缺，因此 A-07 仍为 `NOT_RUN`
  的阶段关闭状态。

## A-04 设备观察补充 — 2026-09-06

- Pixel 7 稳定包 `io.github.yingqiu0871.evolune` 仍为 `1.5.0` / `101050000`；
  debug 候选以独立包 `io.github.yingqiu0871.evolune.debug` 安装成功，`1.5.0-debug`、
  target SDK 36。
- Wear OS API 37 (`emulator-5554`) debug 候选安装成功并冷启动；包版本为
  `1.5.0-debug` / `1101050000`，target SDK 36。真实表、最低 API 31/36 Phone、双 Launcher
  和 Data Layer 配对仍是未完成的宿主证据。

## 工作树和检查

- `git status --short --branch`：`main...origin/main`；当前变更包含 v1.6 B 首批 Phone Widget
  action/configuration 代码、测试、资源，以及 `TODO.MD`、状态/路线图和
  `docs/evolune/v1.6/` 证据文档。
- `git diff --check`：通过；仅有现有 Git ignore 权限和 LF/CRLF 提示，无 whitespace error。
- 文档批次尚未提交，因此不能作为候选提交、发布包或 A 阶段最终 APPROVE 的替代证据。
