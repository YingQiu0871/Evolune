# Evolune v1.6 — 验收记录

状态：`DONE / RELEASED AS v1.6.0`。实现、自动化、最新签名候选、真实 Phone/Wear 保留数据覆盖安装、负责人真实手表验收及独立最终技术复审全部通过。
规划文档本身不构成独立审阅或发布批准。

## Final release matrix

本表比照 v1.5 的最终验收结构。`PASS` 表示已有可追溯证据；本次没有 owner waiver，也没有开放 P0/P1/P2。

| Area | Required coverage | Status |
|---|---|---|
| Phone Widget gallery | 四个独立系统入口、配置、显示、刷新/点击、多实例与旧实例升级 | PASS |
| Phone E2 chart | 历史/预测圆角柱、X/Y 轴、均匀刻度、响应式高度和数值一致性 | PASS |
| Wear Tiles | 三个新 Tile 可发现/添加/显示/刷新，旧曲线 Tile 保留组件身份 | PASS |
| Wear actions | 确认、撤销、跳过；Phone 权威校验、持久化优先和竞态抑制 | PASS |
| Complications | 三个 provider 的注册、支持类型、品牌、状态、选择、显示和点击 | PASS |
| Appearance | Phone/Wear Material You、八组预制配色、深浅模式、对比度和圆屏安全区 | PASS |
| Upgrade | v1.5→v1.6 保留数据覆盖安装，旧 Widget/Tile 和 legacy/v1/action 路径兼容 | PASS |
| Static/quality | JVM、Lint、构建、manifest/resource/APK 审计和设备回归 | PASS |
| Release identity | applicationId、版本号、单 signer、APK v2 签名、主机/设备哈希一致 | PASS |
| Product review | 模拟器视觉回归与负责人真实手表 Tile/Complication 验收 | PASS |
| Independent review | P0/P1/P2 清零；仅保留非阻塞 P3 压力测试建议 | PASS |
| RC gate | 最新候选、Release Notes、最终资产名和 SHA-256 一致 | PASS |

最终候选为 `release-artifacts/v1.6.0-rc/20260909-224735`：Phone SHA-256
`E964FC8A89711B4F0F68B9BC93EB185BF77189E41CC0145F53E77DCD94A76665`，Wear SHA-256
`B344C07CCB67DFEDCCE294EC8A655F6229B65D6C36A158E78461C25DBB8D5BA4`，签名证书 SHA-256
`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`。

## v1.5 实施基线

| 项目 | 值 |
|---|---|
| 基线标签 | `v1.5.0` |
| 基线提交 | `df12329278eafa488713edf202554cdbd523b8d0` |
| 工作分支 | `main` |
| 基线 Phone/Wear 版本 | `1.5.0` / `101050000`、`1101050000` |
| 记录日期 | 2026-09-06 |
| 最终范围 | A–G 全部关闭；历史阶段状态保留在下方证据表 |
| 发布候选 | `20260909-224735`，最终提交由 `v1.6.0` tag 固定 |

## A 阶段证据

字段遵循 [V16_REVIEW.md](V16_REVIEW.md) 的最小记录结构。`PASS` 只表示该行证据已记录，
不表示 A 阶段已经关闭。

| 阶段/用例 | 预期 | 起点/候选提交 | 设备与数据集 | 命令或操作 | 结果 | 证据路径 | 审阅者/日期 | 问题与复核 |
|---|---|---|---|---|---|---|---|---|
| A-01 源码身份 | HEAD 精确对应 v1.5.0 | `df123292` / 未创建 | 工作区 Git | `git rev-parse HEAD`；`git describe --tags --exact-match HEAD` | PASS | [V16_A_EVIDENCE.md#A-01](V16_A_EVIDENCE.md#A-01) | 实施记录 / 2026-09-05 | 待独立复核 dirty state |
| A-02 工程身份校验 | 包名、版本和版本号符合 v1.5.0 | `df123292` / 未创建 | Windows；无业务数据集 | `validateEvoluneIdentityAndVersioning`（首次与 `--rerun-tasks` fresh rerun） | PASS | [A-03-gradle-2026-09-05.log](A-03-gradle-2026-09-05.log)、[fresh rerun](A-03-gradle-2026-09-05-rerun.log) | 实施记录 / 2026-09-05 | 独立审阅者仍需核对候选提交；不替代 connected/device 测试 |
| A-03 JVM 基线 | Experience Core、Phone、Wear 单测通过 | `df123292` / 未创建 | Windows；固定 JVM fixtures | `:experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks` | PASS | [A-03-gradle-2026-09-05-rerun.log](A-03-gradle-2026-09-05-rerun.log) | 实施记录 / 2026-09-05 | Fresh rerun 已完成；不替代 connected/device 测试 |
| A-04 设备枚举 | 记录真实 Phone/Wear 设备和 API | `df123292` / 未创建 | Pixel 11 Pro API 37；Phone API 35/37；Wear API 37 | `adb devices -l` 与 `getprop` | PASS | [V16_A_EVIDENCE.md#A-04](V16_A_EVIDENCE.md#A-04) | 实施记录 / 2026-09-05 | API 31/36、真表和真实 Launcher 待补 |
| A-05 业务状态/尺寸规格 | 所有展示状态、动作、尺寸可审阅 | `df123292` / 未创建 | 源码盘点；无真机视觉数据 | 复核 Widget 实现及 [V16_SPEC.md](V16_SPEC.md) §4–§7 | NOT_RUN | [V16_SPEC.md](V16_SPEC.md) §3–§7 | 待独立产品/视觉审阅 | Phone `AVAILABLE` 与 Wear `UPCOMING/DUE` 矩阵已冻结；stale/trend/PK 窗口、展示延迟和真实尺寸仍缺 |
| A-06 Wear 契约盘点 | 快照字段、协议版本、上限和旧 Tile 路径明确 | `df123292` / 未创建 | `WearAppProtocol`、`DoseTileService`、Wear 派生 snapshot/cache | 复核字段、版本、旧 `/hrt/*` 路径和 stale 基线 | NOT_RUN | [V16_SPEC.md](V16_SPEC.md) §3–§5；[V16_A_EVIDENCE.md](V16_A_EVIDENCE.md) | 待独立数据/协议审阅 | 已明确 Wear `UPCOMING/DUE` 兼容动作和可重建 cache；缺独立 inventory、旧/新组合矩阵和 v1.5 包身份复核 |
| A-07 性能预算 | 固定数据集和 1/3/5 实例预算有有效采样 | `df123292` / 未创建 | `PK-EMPTY/STEADY/DENSE`；多 Widget/Wear | 复用 v1.5 fixture，补采样 | NOT_RUN | [V16_A_EVIDENCE.md#A-07](V16_A_EVIDENCE.md#A-07) | 待性能审阅 | 当前仅有 v1.5 单 Widget 路径基线 |
| A-08 独立只读审阅 | 按 P0–P3 检查规格、证据格式和调用链 | `df123292` / 未创建 | ChatGPT 工作区只读会话 | 复核 V16_SPEC/V16_ACCEPTANCE/计划/实现 | REQUEST_CHANGES | [V16_A_REVIEW_2026-09-05.md](V16_A_REVIEW_2026-09-05.md) | ChatGPT 独立会话 / 2026-09-05 | P1/P2/P3 已写入修订项，待修复后复审 |
| A-09 修订后独立只读复审 | 验证上一轮修订并复核 Phone/Wear 动作、状态文档和缓存边界 | `df123292` / 未创建 | ChatGPT 工作区只读会话；当前文档批次 | 复核 9 份 v1.6/状态文档并沿 Phone/Wear 调用链检查 | REQUEST_CHANGES | [V16_A_REVIEW_2026-09-05.md](V16_A_REVIEW_2026-09-05.md) §修订后复审 | ChatGPT 独立会话 / 2026-09-05 | Phone/Wear 动作矩阵与状态/cache 表述已按意见修订；A 证据仍未齐，不得关闭 |
| A-10 第三次独立只读复审 | 复核跨端动作边界、当前版本状态、派生 cache 边界和 fresh rerun | `df123292` / 未创建 | ChatGPT 工作区只读会话；当前文档批次 | 复核 v1.6 规格/计划/验收/证据及当前状态文档，并核对 fresh rerun 日志 | REQUEST_CHANGES | [V16_A_REVIEW_2026-09-05.md](V16_A_REVIEW_2026-09-05.md) §第三次复审 | ChatGPT 独立会话 / 2026-09-05 | 跨端矩阵、v1.5.0/v1.6-A 顶部状态、cache 边界、fresh rerun 均获认可；Phone action-time gate 和一处过时 Wear limitation 已写回，A 仍未关闭 |

## B 阶段实施证据（首批）

| 阶段/用例 | 预期 | 起点/候选提交 | 设备与数据集 | 命令或操作 | 结果 | 证据路径 | 审阅者/日期 | 问题与复核 |
|---|---|---|---|---|---|---|---|---|
| B-01 Phone action-time gate | 点击时重新验证 `AVAILABLE`、occurrence identity、日期和计划状态；失败不写入，成功记录可幂等重放 | `df123292` / 未创建 | Windows；固定 Clock/ZoneId；Widget occurrence fixtures | `:app:testDebugUnitTest --tests WidgetWorkTest --tests WidgetUiTest --tests CrossEntryOccurrenceConcurrencyTest --no-daemon` | PASS | [V16_B_EVIDENCE.md#B-01](V16_B_EVIDENCE.md#B-01)、[B-03-gradle-2026-09-06.log](B-03-gradle-2026-09-06.log) | 实施记录 / 2026-09-06 | P1 occurrence 上下文已修复；跨 Phone/Wear 组合仍未完成 |
| B-02 只读 `OPEN_APP` 行 | `UPCOMING`/过期 occurrence 不出现确认动作；collection/non-collection 入口都进入应用 | `df123292` / 未创建 | `emulator-5556`；Pixel_7 AVD；API 35；19 connected tests | `:app:connectedDebugAndroidTest`（`WidgetRemoteViewsTest`、`ReceiverWidgetProductionCutoverTest`） | PASS | [V16_B_EVIDENCE.md#B-02](V16_B_EVIDENCE.md#B-02)、[B-04-android-2026-09-06.log](B-04-android-2026-09-06.log) | 实施记录 / 2026-09-06 | 仍需真实 launcher 与真机宿主验收 |
| B-03 实例样式配置兼容 | 缺失/未知 style 回退 `legacy_default`；写入、删除和恢复按 `appWidgetId` 隔离 | `df123292` / 未创建 | JVM + `emulator-5556` SharedPreferences | `WidgetAppearanceTest`；`WidgetRemoteViewsTest.perWidgetAppearancePersistsIndependentlyAndDeletionIsScoped` | PASS | [V16_B_EVIDENCE.md#B-03](V16_B_EVIDENCE.md#B-03)、[B-03-gradle-2026-09-06.log](B-03-gradle-2026-09-06.log)、[B-04-android-2026-09-06.log](B-04-android-2026-09-06.log) | 实施记录 / 2026-09-06 | C renderer/config 候选已实现；真实配置流程仍待宿主验证 |
| B-04 修订后回归 | 验证 P1 occurrence 匹配修复、OPEN_APP 只读入口和日志追溯字段 | `df123292` / 未创建 | Windows；`emulator-5556` API 35；19 connected tests | B-03 JVM + B-04 Android；`git diff --check` | PASS | [B-03-gradle-2026-09-06.log](B-03-gradle-2026-09-06.log)、[B-04-android-2026-09-06.log](B-04-android-2026-09-06.log) | 实施记录 / 2026-09-06 | 首批独立复审已批准；B 仍未关闭 |
| B-05 首批独立只读复审 | 复核 P1/P2 修订、调用链、测试记录和证据边界 | `df123292` / 未创建 | ChatGPT 工作区只读会话；同一 custom-domain connector | 复核当前 diff、实现、测试、日志和 B 文档 | APPROVE FIRST SLICE | [V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md) | ChatGPT 独立会话 / 2026-09-06 | 只批准首批 slice；A/B 仍为 `IN_PROGRESS` |
| B-06 Wear 字段与兼容矩阵 | 冻结 Phone 派生字段缺口、legacy/v1 通道边界和旧/新配对预期 | `df123292` / 未创建 | JVM；Phone + Experience Core + Wear fixtures | `WearAppLegacyCompatibilityTest`、`WearAppCompatibilityMatrixTest`、snapshot/request codec 和 follow-up 回归；`--rerun-tasks` | PASS（修订后独立复审通过） | [V16_B_WEAR_MATRIX.md](V16_B_WEAR_MATRIX.md)、[B-05-wear-contract-2026-09-06.log](B-05-wear-contract-2026-09-06.log)、[V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md) | ChatGPT 独立会话 / 2026-09-06 | B-06 技术契约检查点已批准；未修改线上 payload；`todaySummary` 实现和真实组合仍待后续 |
| B-07 B-06 修订后独立只读复审 | 只读复核修订后的版本基线、todaySummary 语义、测试边界和 fresh 日志 | `df123292` / 未创建 | ChatGPT 工作区只读会话；同一 custom-domain connector | 复核当前 diff、V16_B_WEAR_MATRIX、V16_B_EVIDENCE、V16_ACCEPTANCE、V16_SPEC、协议实现和 B-05 日志 | APPROVE B-06 TECHNICAL CONTRACT CHECKPOINT | [V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md) | ChatGPT 独立会话 / 2026-09-06 | P0/P1/P2/P3 均无；批准范围仅限 Wear 字段设计、兼容矩阵和测试证据；B 仍为 `IN_PROGRESS` |
| B-08 `todaySummary` tag 11 候选实现 | Phone 从完整当日 occurrence 派生日期、时间、完成/总数和三态 summary；旧 v1 payload/通道保持兼容 | `df123292` / 未创建 | Windows；Experience Core + Phone + Wear JVM | `:experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks`；92 suites / 815 tests | PASS（修订后独立复审通过） | [V16_B_EVIDENCE.md#B-08](V16_B_EVIDENCE.md#B-08)、[B-08-wear-summary-2026-09-06.log](B-08-wear-summary-2026-09-06.log)、[V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md) | ChatGPT 独立只读复审 / 2026-09-06 | `APPROVE B-08 CANDIDATE IMPLEMENTATION`；协议版本仍为 1；未做真实 APK/Data Layer 配对、Wear UI 展示或发布批准 |

## C/D/E/F 候选实现证据

| 阶段/用例 | 预期 | 起点/候选提交 | 设备与数据集 | 命令或操作 | 结果 | 证据路径 | 审阅者/日期 | 问题与复核 |
|---|---|---|---|---|---|---|---|---|
| C/D Phone gallery + PK candidate | 五种 Phone style 共用 snapshot；PK 48h/25 点采样边界稳定 | `df123292` / 未创建 | Windows；固定 JVM fixtures | `:experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon` | PASS；95 suites / 834 tests，0 failures/errors | [V16_C_D_E_F_EVIDENCE.md](V16_C_D_E_F_EVIDENCE.md)、[JVM log](V16_C_D_E_F_JVM_2026-09-06.log) | 实施记录 / 2026-09-06 | 真 Launcher 截图、数值对照和 1/3/5 实例成本待 A/D/G |
| C/D RemoteViews structure | 新 chart container/segment/axis、旧四种布局和 production routing 可膨胀 | `df123292` / 未创建 | Pixel_7 AVD API 35；19 focused connected tests | `ANDROID_SERIAL=emulator-5556 :app:connectedDebugAndroidTest -P...WidgetRemoteViewsTest,...ReceiverWidgetProductionCutoverTest` | PASS；19/19 | [V16_C_D_E_F_EVIDENCE.md](V16_C_D_E_F_EVIDENCE.md)、[Android log](V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log) | 实施记录 / 2026-09-06 | 真 Launcher 宿主仍待产品/视觉签核 |
| E Wear Tiles | 三新 Tile 读取 v1 snapshot，今日待服按本地日期筛选并提示截断，下一次服药沿用 occurrence confirmation，过期 E2 明确占位，旧 Dose Tile 不变 | `df123292` / 未创建 | Wear JVM + Wear debug APK | `:wear:testDebugUnitTest`；`:wear:assembleDebug` | PASS；Wear 单测包含日期/溢出/确认/过期状态，debug APK 成功 | [V16_C_D_E_F_EVIDENCE.md](V16_C_D_E_F_EVIDENCE.md)、[assemble log](V16_C_D_E_F_ASSEMBLE_2026-09-06.log) | 实施记录 / 2026-09-06 | 真表、Data Layer 回执、旧/新 APK 配对待补 |
| F Wear Complications | 三 Short Text provider 复用 READY snapshot，缺失或过期浓度输出 `--`，快照/回执请求更新 | `df123292` / 未创建 | Wear JVM + AndroidX watchface data-source 1.2.1 | `WearComplicationTextTest`；`:wear:testDebugUnitTest`；`:wear:assembleDebug` | PASS；Wear 单测与 debug APK 成功 | [V16_C_D_E_F_EVIDENCE.md](V16_C_D_E_F_EVIDENCE.md)、[JVM log](V16_C_D_E_F_JVM_2026-09-06.log)、[assemble log](V16_C_D_E_F_ASSEMBLE_2026-09-06.log) | 实施记录 / 2026-09-06 | 真实表盘槽位、Ambient、圆形裁切和后台刷新待补 |

## 2026-09-06 C/D/E/F 最终独立复审

- 结论：`APPROVE V1.6 C/D/E/F CANDIDATE`；P0/P1/P2/P3 均无；C2C 状态为 `DONE`。
- 复审确认：今日待服使用 `todaySummary.totalCount - completedCount` 计算完整待服数；`RETRYABLE_STORAGE_FAILURE` 显示“确认失败，请重试”并进入 `retryPending`；三个 Complication provider 的 READY/STALE、ShortText value/contentDescription、preview 和 tap 行为一致；Snapshot 只在 `Applied` 刷新，result 只在 `Applied/Duplicate` 刷新。
- 独立复审以当前工作树 `df123292`、95 suites / 834 tests、Pixel 7 19/19、Phone/Wear assemble 和三份 raw log 为依据。该批准仍只覆盖 C/D/E/F 候选实现，不关闭真实 Launcher、Wear/Data Layer 配对、真表/表盘槽位、签名升级包或 A/G 阶段。

## 2026-09-06 A/G 设备与集成观察

- 新增 [V16_G_EVIDENCE.md](V16_G_EVIDENCE.md)：Pixel 7 API 35 和 Wear OS API 37 的独立 debug
  包安装、冷启动、应用进程日志、Wear Tile/Complication manifest 注册检查均已记录。
- 新增 [V16_A_G_PIXEL7_PERF_2026-09-06.log](V16_A_G_PIXEL7_PERF_2026-09-06.log)：固定数据集
  Android runtime 性能观察 2/2 通过；新增 [V16_G_PIXEL7_MIGRATION_2026-09-06.log](V16_G_PIXEL7_MIGRATION_2026-09-06.log)：
  Room 保持性回归 2/2 通过。
- 当前工作树的 `:app:assembleRelease :wear:assembleRelease` 已成功，Phone/Wear APK 均通过签名验证；
  产物和证书指纹见 [V16_G_RELEASE_ASSEMBLE_2026-09-06.log](V16_G_RELEASE_ASSEMBLE_2026-09-06.log)。
-  该批 APK 生成于版本身份更新之前，仍是 v1.5.0；`build.gradle.kts` 现已切换到 v1.6.0，
  需重新构建和验证后才能作为 v1.6.0 发布候选。
- [V16_VERSION_BUMP_2026-09-06.log](V16_VERSION_BUMP_2026-09-06.log) 记录了新版本身份和 JVM
  身份回归通过结果。
- [V16_RELEASE_BUILD_1.6.0_2026-09-06.log](V16_RELEASE_BUILD_1.6.0_2026-09-06.log) 已补记新身份
  Release Candidate：Phone/Wear APK、23/23 Pixel 7 回归和签名校验均通过。
- [V16_G_UPGRADE_PIXEL7_WEAR_2026-09-06.log](V16_G_UPGRADE_PIXEL7_WEAR_2026-09-06.log) 已记录 Phone
  同签名覆盖安装/冷启动和 Wear Release 清洁安装/冷启动；完整数据与宿主验收仍未关闭。
- [V16_G_REAL_PIXEL11_2026-09-06.log](V16_G_REAL_PIXEL11_2026-09-06.log) 已记录真实 Pixel 11 Pro API 37
  上的同签名覆盖安装、冷启动、Pixel Launcher widget host，以及 `Evolune / 月序用药微件` 的真实
  Widget picker 添加、展示和未来 occurrence 只读入口。
- 这些记录补充 A-04/A-07/G-01/G-02/G-03/G-04 的实证，但不关闭 A-07，也不替代真实 Launcher、
  第二 Launcher、真表/表盘槽位、Data Layer 配对、1/3/5 实例成本或完整 v1.5→v1.6 数据/配置保留。

## 阶段关闭矩阵

| 阶段 | 状态 | 关闭证据 |
|---|---|---|
| A | DONE | 冻结规格、基线、设备/性能证据和独立审阅均已记录 |
| B | DONE | 共享展示/动作/配置、Wear 版本化快照与 legacy/v1 兼容通过独立审阅和组合验证 |
| C | DONE | 四个独立 Phone provider、实例级外观配置、滚动今日计划及模拟器/真机宿主验证通过 |
| D | DONE | 48h/25 点只读 E2 趋势、向上圆角柱、坐标轴、数值一致性和响应式几何验证通过 |
| E | DONE | 三个新 Tile、旧曲线 Tile 兼容、真实 Data Layer、确认/跳过与真表验收通过 |
| F | DONE | 三个 Short Text provider 的注册、状态、点击和负责人真实手表验收通过 |
| G | DONE | 自动化、独立最终复审、签名/哈希、保留数据覆盖安装和发布候选门禁通过 |

## 2026-09-05 独立审阅结论

- 结论：`REQUEST_CHANGES`；没有 P0。
- P1：`next_dose` 不能把未来 `UPCOMING` occurrence 渲染成必然可用的确认按钮；现有
  `ContractWidgetQuickActionWork` 会拒绝非当前本地日期。规格已补充 `AVAILABLE` 门槛、跨日只读规则，
  `WidgetUiMapper` 的现有映射需在 B/C 修正并测试。
- P2：验收表最小字段不完整；A-02/A-03 缺少可定位日志路径；v1.5 `Energy/background`
  的 `SKIPPED_BY_OWNER` 没有在 v1.6 基线中明确继承；样式选择入口和旧实例 `styleId` 回退没有冻结。
  本批次已补齐表结构、日志路径、豁免说明和 `legacy_default` 规则。
- P3：`V16_REVIEW.md` 顶部状态和性能基线措辞已修正；其余产品状态稿和真实宿主证据仍待补齐。
- 复核状态：修订后的 `V16_SPEC.md`、`V16_ACCEPTANCE.md`、`V16_REVIEW.md` 和状态文档需再次只读审阅；
  在复审前 A 继续保持 `IN_PROGRESS`。

## 2026-09-05 修订后独立复审结论

- 结论：`REQUEST_CHANGES`；没有 P0。
- 已确认通过的修订：Phone `next_dose` 的 `AVAILABLE`/`UPCOMING` 边界、验收表追溯字段、
  A-02/A-03 日志路径、v1.5 `SKIPPED_BY_OWNER`、`legacy_default` 回退、系统 picker 决策、
  `V16_REVIEW.md` 状态头和重复采样措辞。
- 剩余 P1：已冻结为 Phone Gallery 采用 `AVAILABLE`，Wear App 与新 Wear Tile 保留现有
  v1.3 `UPCOMING/DUE` 兼容动作；这项跨端矩阵、版本协商和旧/新组合测试必须在 B 前形成证据，
  不能把两端实现误认为同一规则。
- 剩余 P2：`CURRENT_STATUS.md`、`ROADMAP.md` 的公开版本/当前里程碑顶部已发现漂移，必须统一；
  V16_SPEC 的“禁止缓存”已改为“不新增权威事实来源，允许可重建 Wear 派生 cache”，需在最终复审中确认。
- 剩余 P3：关键 JVM 测试还需独立 fresh rerun；TODO 中 v1.3 的历史状态需要与当前已有 Wear App
  实现对齐。

## 2026-09-06 B 首批修订后独立复审结论

- 结论：`APPROVE V1.6-B FIRST SLICE`；无 P0/P1/P2。
- 已确认：完整 occurrence 上下文 action-time 重检、legacy/null-slot sibling 回归、OPEN_APP
  只读路由与零 quick-action 写入、TODO/验收状态同步，以及 JVM/Android 日志追溯字段。
- P3 日志 `Result` 行空行问题已修复。
- 该结论只覆盖 B 首批 action/configuration slice；Wear 协议矩阵、C/D/E/F renderer 候选、真实宿主和
  阶段关闭证据仍待完成。

## A 阶段历史缺口（已由最终门禁关闭）

1. Phone `next_dose` 的 due/upcoming/跨日/同一时刻多 occurrence 决策与 `MedicationActionAvailability` 的测试，
   以及 Wear `UPCOMING/DUE` 兼容动作的旧/新组合矩阵。
2. E2 stale threshold、E2 trend window、PK chart time window、最大采样点数和宿主展示延迟上限。
3. 五种 Phone、三种 Tile、三种 Complication 的状态稿、最小 dp/resize 断点和可读性审阅。
4. Phone API 31/36 门槛、两个真实 Launcher、真实圆形 Wear、Complication 表盘槽位和 v1.5 Wear 包身份。
5. `PK-EMPTY/STEADY/DENSE × 1/3/5 Widget`、多 Wear 展示面的重复采样，以及 PK/Data Layer/wakeup/真实刷新延迟记录。
6. Phone/Wear 动作协议的最终版本协商与回执证据；不得只记录文档约定。
7. 独立审阅者对修订后调用链和 fresh 关键测试的复核结论（`APPROVE` 或新的 `REQUEST_CHANGES`）。
8. A 阶段候选提交、确切 diff、产品/视觉批准及 A-05/A-06/A-07 的实际关闭记录。
9. Phone action handler 的 `AVAILABLE` action-time 重检已在 B-01 提供确定性 JVM 证据；A 最终关闭仍需
   独立复核、候选提交和与 Wear 的组合/回执证据。
10. `CURRENT_STATUS.md` 的 Wear limitation、`ROADMAP.md` 的历史版本措辞及 fresh rerun 的执行者表述需保持
    与当前事实一致。

## B 阶段历史缺口（已由最终门禁关闭）

1. `todaySummary` 候选实现的独立协议复审、版本化协商和回执矩阵；当前字段语义和配对预期已记录在 [V16_B_WEAR_MATRIX.md](V16_B_WEAR_MATRIX.md)，生产发布仍需真实组合证据。
2. 旧 Phone/新 Wear、新 Phone/旧 Wear、两端新版的真实动作与重复回放组合测试；当前 45 项纯契约测试不替代真实 Data Layer 配对。
3. 配置页实际样式选择、预览、取消/保存/再次配置和升级保留；C renderer/config 候选已实现，真实流程和升级保留仍待宿主证据。
4. 首批独立只读审阅已批准 B-01/B-02/B-03/B-04，B-06 技术契约检查点也已批准；B 阶段最终关闭仍需 Wear 协议扩展、配置页
   流程和跨端兼容证据，后续批次仍需独立复审。

## 审阅规则

每批提交必须包含起点/候选提交、变更列表、测试命令、设备与数据集、截图/测量、已知问题和证据路径。独立审阅者只读检查 diff 和调用链，并重跑关键测试；产品/视觉审阅必须使用真实 Launcher、Tile 或表盘。证据缺失时不能给 `APPROVE`。

P0/P1 必须关闭；P2 若违反本阶段必交付项同样阻塞阶段关闭；P3 只能作为建议。修复后需复核受影响链路，阶段只有在技术和产品审阅都通过后才标记 `DONE`。

## 2026-09-09 最新签名候选补充

本文件前部的历史阶段表保留各批次当时状态。最终有效候选为 `20260909-224735`；正式构建、
签名、真实 Pixel 11 Pro 与 Galaxy Watch 的保留数据覆盖安装、Wear Tile 资源版本 7 圆角复验，以及用户取消
独立“今日进度”Phone Widget 后的最终四入口矩阵，统一见
[V16_FINAL_RELEASE_GATE_2026-09-09.md](V16_FINAL_RELEASE_GATE_2026-09-09.md)。

项目负责人于 2026-09-09 确认真实手表验收完成，并明确批准进入发布流程；该人工结论补齐三个
Complication 与“今日计划”Tile 的真实宿主产品验收。Wear“跳过本次”发布阻断修复随后通过
独立最终增量复审，P0/P1/P2 均无；最新候选完成真实 Phone/Wear 覆盖安装和回读。本验收封版为 `DONE`。
