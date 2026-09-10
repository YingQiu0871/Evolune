# Evolune v1.6-B — 共享展示状态、动作与配置证据

记录日期：2026-09-06
阶段：B `DONE`（下文保留首批实现时的历史检查点状态；最终关闭见 `V16_ACCEPTANCE.md`）
工作目录：`current/Evolune-v1.2`
实施基线：`df12329278eafa488713edf202554cdbd523b8d0`（`v1.5.0`）
候选提交：未创建；本批次仍在工作树中

本记录只覆盖 B 阶段已经落地并可重跑的共享动作、只读入口和实例配置契约。C/D/E/F 的完整布局候选已在
[V16_C_D_E_F_EVIDENCE.md](V16_C_D_E_F_EVIDENCE.md) 记录；Wear 协议扩展的真实组合、真实宿主验收和发布候选仍未完成，不能用本文件替代 B 阶段最终审阅。

## B-01 — Phone action-time `AVAILABLE` 双重门槛

变更范围：

- `WidgetWork.kt` 在已有 occurrence identity、plan、slot 和 action-zone 日期校验之后，
  对尚未成功记录的点击按当前点击时刻重新派生 `MedicationActionAvailability`。
- 只有 `AVAILABLE` 才会进入写入；`UPCOMING`/未到 due、窗口过期、已删除或已改方案均返回
  `Invalid`，不写入、不刷新、不显示成功副作用。
- 已经存在同一 Widget action 或跨入口已持久化的同一 occurrence 仍按既有幂等路径返回 replay，
  即使重放发生在 due window 关闭之后，也不会重复插入。
- `WidgetUiMapper` 将 `DUE` 映射为 `RECORD`，`RECORDED` 映射为 `COMPLETED`，
  `UPCOMING` 和 `PAST_UNRECORDED` 映射为只读 `OPEN_APP`，避免渲染出点击必失败的确认按钮。
- 修订后，action-time availability 使用完整的 `presentationOccurrences` 一次派生，再按
  occurrence ID 取得目标项；这样 legacy/null-slot 事件不会在同日 sibling 之间被重新归属。

确定性 JVM 测试：

- `WidgetWorkTest.action time rechecks availability and rejects upcoming or expired occurrence`
- `WidgetWorkTest.accepted widget action remains idempotent after its availability window closes`
- `WidgetWorkTest.three same-day occurrences of one plan have separate action identities`
- `CrossEntryOccurrenceConcurrencyTest` 的 Widget/跨入口并发场景
- `WidgetUiTest` 的 due、recorded、upcoming 和 past action 映射

初始命令与结果：

```text
.\gradlew :app:testDebugUnitTest --tests WidgetAppearanceTest --tests WidgetUiTest --tests WidgetWorkTest --tests CrossEntryOccurrenceConcurrencyTest --no-daemon
BUILD SUCCESSFUL in 13s
28 actionable tasks: 1 executed, 27 up-to-date
```

初始输出：[B-01-gradle-2026-09-05.log](B-01-gradle-2026-09-05.log)。

修订后回归：

- `WidgetWorkTest.action-time availability keeps legacy null-slot event on earlier sibling` 覆盖
  同一计划的 O1/O2、反向事件/计划输入顺序、O2 当前 `DUE` 和单次写入。
- `WidgetWorkTest` 15 项、`WidgetUiTest` 13 项、`CrossEntryOccurrenceConcurrencyTest` 3 项，
  共 31 项全部通过。
- 日志头记录了 HEAD、工作树摘要、完整命令、测试数和结果：[B-03-gradle-2026-09-06.log](B-03-gradle-2026-09-06.log)。

## B-02 — 只读行进入应用及 RemoteViews 回归

变更范围：

- 新增 `WidgetRowAction.OPEN_APP` 和 `ic_widget_open` 图标。
- `EvoluneWidgetReceiver` 已为 collection/non-collection 行分别接入 fill-in 或普通
  `PendingIntent`，显示 occurrence 的计划时间，不触发写入；本轮新增 Android 回归直接
  验证了 OPEN_APP fill-in 的只读 identity、MainActivity 路由及 quick-action 工厂未被调用。
- 已记录行继续显示完成态；只有当前 `DUE` 行保留确认入口。

设备与结果：

- 设备：`emulator-5556`，Pixel_7 AVD，API 35。
- 测试：`WidgetRemoteViewsTest`、`ReceiverWidgetProductionCutoverTest`，共 19 项。
- 结果：`BUILD SUCCESSFUL in 40s`，19 项全部通过；新增 OPEN_APP fill-in 只读字段、
  `MainActivity` 路由和 quick-action 工厂零调用断言。

初始命令与日志：[B-02-android-2026-09-05.log](B-02-android-2026-09-05.log)。修订后日志：
[B-04-android-2026-09-06.log](B-04-android-2026-09-06.log)。

## B-03 — 实例隔离的样式 ID 与兼容回退

变更范围：

- `WidgetStyle` 固化 `legacy_default`、`today_plan`、`next_dose`、`current_e2`、
  `today_progress`、`pk_chart` 六个稳定 ID。
- `WidgetAppearanceConfig` 增加末尾默认字段，保持现有三参数构造和旧配置读取兼容。
- `WidgetAppearanceStore` 以 `appWidgetId` 隔离读写、删除和 style key；缺失或未知 style ID
  回退 `legacy_default`，删除一个实例不会影响其他实例。
- 本检查点只保存并恢复样式身份；新五种样式的实际布局继续由 C 阶段实现，当前渲染保持
  legacy renderer。

覆盖测试：

- `WidgetAppearanceTest`：缺失/未知回退、稳定 ID、取消不写入、两个实例隔离。
- `WidgetRemoteViewsTest.perWidgetAppearancePersistsIndependentlyAndDeletionIsScoped`：
  Android `SharedPreferences` 写入、重建 Store 读取和按实例删除。

Android 端同样见 [B-04-android-2026-09-06.log](B-04-android-2026-09-06.log)；JVM 端见
[B-03-gradle-2026-09-06.log](B-03-gradle-2026-09-06.log)。

## 当前边界与下一批

- B 仍为 `IN_PROGRESS`：Wear 今日计划/进度的 Phone 派生字段、版本协商、旧 Phone/新 Wear
  与新 Phone/旧 Wear 组合矩阵尚未形成可执行证据。
- C/D/E/F 仍需完成真实 launcher、PK 数值/成本、Wear/表盘宿主、预览/取消/保存/重配流程和多实例宿主验证；
  新样式必须复用 B-01 的 action-time handler，不能在 UI 层另造写入路径。
- A 仍未关闭：真实宿主、性能、尺寸、产品/视觉审阅、候选提交和跨端组合证据仍是发布前门槛。
- 本批次无数据库 schema、PK 模型、Room 权威数据源、应用身份或 Wear 既有 provider 身份变更。

## 独立复审

- 首轮复审为 `REQUEST_CHANGES`，P1/P2 已按 [V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md)
  记录并修订。
- 修订后复审结论为 `APPROVE V1.6-B FIRST SLICE`，无 P0/P1/P2；P3 日志可读性问题也已修复。
- 该批准只覆盖首批 action/configuration slice，不关闭 B 阶段。

## B-06 — Wear 字段缺口与旧/新兼容矩阵

- [V16_B_WEAR_MATRIX.md](V16_B_WEAR_MATRIX.md) 盘点当前 v1 snapshot、今日汇总字段缺口、
  legacy `/hrt/*` 与 v1 `/hrt/v1/wear-app/*` 的 additive 边界，以及旧 Phone/新 Wear、
  新 Phone/旧 Wear、两端新版和未知协议版本的预期行为。
- B-06 只冻结契约和测试，不修改线上 payload 或协议版本；矩阵是契约预期，不是实体
  Phone–Wear Data Layer 配对证据。
- 首轮独立复审指出 v1.5 baseline 配对语义、todaySummary 字段定义、测试名称/断言边界和
  UP-TO-DATE 日志问题；修订已写入矩阵和测试，复审记录见
  [V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md)。
- 修订后使用 `--rerun-tasks` fresh 执行 45 项 Phone/Experience Core/Wear 编解码、
  producer/revision、等待态和兼容矩阵测试，0 失败；日志见
  [B-05-wear-contract-2026-09-06.log](B-05-wear-contract-2026-09-06.log)。
- 修订后独立只读复审结论为 `APPROVE B-06 TECHNICAL CONTRACT CHECKPOINT`，P0/P1/P2/P3
  均无。批准范围仅限字段设计、兼容矩阵和测试证据；不批准 `todaySummary` 生产扩展、真实
  APK/Data Layer 配对或 B 阶段关闭。

## B-08 — 可选 `todaySummary` tag 11 实现

变更范围：

- `WearAppProtocol` 增加可选 `WearAppTodaySummary` 和 `WearAppTodaySummaryState`，仍使用协议
  版本 1；tag 11 缺失时旧 snapshot 保持可读。
- `WearAppSnapshotCodec` 增加 tag 11 的编码/解码与约束校验：Phone 本地日期、计算时间、
  completed/total 计数、无启用计划/无 occurrence/有 occurrence 三态；不加入含义未冻结的
  `truncated`。
- `WearAppSnapshotBuilder` 从完整当日 occurrence 集合派生 summary，避免从最多 5 条 upcoming
  列表推导进度；当前 `computedAt == generatedAt`，Wear 不拥有事实来源。
- 没有修改 legacy `/hrt/*`、confirmation/undo、producer/revision 或协议版本；tag 11 候选实现已获
  独立复审通过，但发布仍需真实旧/新组合、回执和宿主验证。

确定性测试与 fresh 结果：

- `WearAppSnapshotCodecTest` 15 项：tag 11 round-trip、旧 payload 缺失 tag 11、日期/计数/状态约束、
  duplicate/malformed tag 11 拒绝；
  `WearAppSnapshotBuilderTest` 15 项：完整当日计数、已记录 occurrence、无启用计划和无 occurrence
  的区分；跨午夜匹配上下文与超过 5 条 upcoming 上限的完整当日计数也有回归；既有 Wear
  matrix/follow-up 继续通过。
- 全量 Experience Core、Phone、Wear JVM：92 suites，815 tests，0 failures，0 errors，
  `--rerun-tasks`，54 个任务执行；日志见
  [B-08-wear-summary-2026-09-06.log](B-08-wear-summary-2026-09-06.log)。
- 第三次范围收窄的独立只读复审已通过，结论为 `APPROVE B-08 CANDIDATE IMPLEMENTATION`，P0/P1/P2/P3
  均无；复审记录见 [V16_B_REVIEW_2026-09-06.md](V16_B_REVIEW_2026-09-06.md)。Android/Data Layer
  真实组合、确认/回执实机验证和 Wear UI 展示仍待后续证据。
