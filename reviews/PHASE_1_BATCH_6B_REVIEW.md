# Evolune Phase 1 Batch 6B Independent Review

Date: 2026-08-06
Reviewer: DeepSeek (independent read-only)
Branch: `phase1/batch6b-receiver-widget-cutover`
Prerequisite tag: `phase-1-batch-6a`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 5**（全部为不阻断的质量/一致性观察项，均不影响正确性或数据安全）
- **允许提交** Batch 6B
- **允许创建 `phase-1-batch-6b`** 标签
- **允许随后进入 6C**（Wear 收口；Wear 为唯一剩余生产绕过点）
- 最大剩余风险：无 P0/P1。剩余风险仅限 6C 前 Wear 路径仍直接访问 DAO/Entity/AppDatabase（已锁定边界），以及 Room v3 仍为内部不可发布状态

## Git and scope

- 分支 = `phase1/batch6b-receiver-widget-cutover` ✓
- `git merge-base --is-ancestor phase-1-batch-6a HEAD` 退出码 0（6a 在历史中）✓；`phase-1-batch-6a` tag 存在 ✓
- 暂存区空 ✓
- 工作树恰好 25 个 6B 文件（12 修改 + 13 新增，与任务清单逐一匹配，含报告）✓
- 越界 diff（`git diff --stat`）：core/、DAO/、Entity/、MainActivity.kt、HRTViewModel.kt、AppDatabase.kt、data/migration/、wear/、pk/、AndroidManifest.xml、app/schemas 全部为空 ✓
- `git diff --check` 通过（仅有 CRLF 提示，无空白错误）
- 无数据库、APK、日志、密钥或真实健康数据产物

## Production call-chain verdict

- Notification confirm：`MedicationNotificationActionReceiver` → `ReceiverWorkLauncher` → `ContractNotificationActionWork` → `RecordDoseEventAction` → `DoseEventRepository.insert` → `RoomDoseEventRepository` ✓；成功/合法 replay 后 `refreshWidgets + cancelNotification` ✓
- Reminder read：`MedicationReminderReceiver` → launcher → `ContractReminderDeliveryWork` → `getById` + `findOccurredBetween` → Domain matcher → 通知副作用 ✓
- Reschedule：`ReminderRescheduleReceiver` → launcher → `ContractReminderRescheduleWork` → `observeAll().first()` → `ReminderManager`（Domain 路径）✓
- Widget：`EvoluneWidgetReceiver` → launcher → `ContractWidgetQuickActionWork`/`ContractWidgetUpdateWork` → contracts → PK 投影渲染 ✓
- 确认：所有入口仅通过 `ProductionRepositoryProvider` 的 contracts；无 DAO/Entity/AppDatabase 直接访问（全仓 grep 证实 reminder/、widget/、application/ 三包零命中）；不构造 legacy Repository；无双写、无 fallback、无第二个 AppDatabase；`MainActivity.kt:39-40` 的 legacy Repository 构造仅用于 Wear dashboard 同步（6A 已批准、6C 收口）✓

## Receiver lifecycle verdict

- 每个异步 delivery 恰一次 `goAsync()`（Notification L57 / Reminder L44 / Reschedule L30 / Widget record L74 与 updateAsync L92 各自独立）；同步拒绝路径（skip/unknown action/invalid UUID/非 record 广播）在 goAsync 前返回，符合设计 §6.6.5 同步路径表 ✓
- goAsync 后不存在同步 return；`finish` 仅存在于 launcher 最外层 finally（L29-36）✓
- 无 fire-and-forget；所有 suspend 工作经 launcher ✓
- `finish` 只表示 Android 生命周期结束（launcher 文档化语义，测试证实）✓
- Widget receiver 无独立未受控异步路径（`WIDGET_SCOPE` 静态共享 scope 已删除）✓

## Receiver scope/job verdict

`ReceiverWorkLauncher.kt`（44 行）逐行核验：

- 每次 `launch` 创建独立 `SupervisorJob()`（L21）+ 注入式 dispatcher（默认 `Dispatchers.IO`，L13）✓
- 无 GlobalScope / runBlocking / Activity/ViewModel scope ✓
- `finally { finish() → onFinished() → deliveryJob.complete() }`（L29-36 + L42-44）：**父 Job 在任务完成后显式 complete**，scope 有明确释放路径，不残留永久 active job ✓；PendingResult/Context/Receiver 不被长期持有（receiver 仅传 applicationContext 给 workFactory）✓
- `CancellationException` 先 catch 再 rethrow（L25-26），不被转成业务成功 ✓；其他 Throwable → `onUnexpectedFailure`（生产默认空，不吞业务语义）✓
- 一个 delivery 的取消不影响另一个（独立 job）——`ReceiverWorkLauncherTest` `cancelling one delivery does not affect another` 验证 ✓
- 测试 dispatcher seam（QueueDispatcher）仅注入测试实例，不触及 production singleton（receiver 实例字段、无参构造默认 IO）✓
- **结论：不构成 P1**。设计 §6.6.3 的 "scope lifetime ends when the handler completes" 通过 `completeDelivery()` 精确满足，比 6A 前共享 scope 更严格

## RecordDoseEventAction verdict

`RecordDoseEventAction.kt`（90 行）：

- 只依赖 `MedicationPlanRepository` + `DoseEventRepository` contract；调用 `insert`（非 upsert）✓
- 明确区分：`Accepted(replayed)` / `PlanNotFound` / `PlanDisabled` / `Conflict` / `Invalid` / `StorageFailure` / `UnexpectedFailure` ✓
- **replay 语义**：先 `getById`，existing + 同 source → Accepted(replayed=true)（返回 existing，不重写内容）；existing + 异 source → Conflict ✓；insert 竞态返回 `Conflict` 时**一次重读**再按同规则分类（L65-67）——设计 §6.2/§6.4 "re-read once" 精确实现 ✓
- `createEvent` 后校验 `event.id == eventId && event.source == source` 否则 Invalid（L51-53）✓
- conflict 不覆盖（无写路径）✓；StorageFailure 不 fallback ✓；`CancellationException` rethrow ✓；无 catch-all 返回成功（`UnexpectedFailure` 是失败结果）✓
- 不泄漏完整 DoseEvent/dose/extras/SQLite message 到失败结果 ✓；不成为持久化事实源（无缓存/状态）✓
- `ReminderReceiverWorkTest`/`RecordDoseEventActionTest` 覆盖：同 source replay、异 source collision 不覆盖、insert race 重读、Storage/Unexpected、cancellation、Invalid ✓

## Reminder stable-ID verdict

- 算法：`UUID.nameUUIDFromBytes("reminder:$planId:$scheduledAtMillis".toByteArray(StandardCharsets.UTF_8))`（`ReminderDoseFactory.kt:49-51`）——与设计 §6.2 完全一致 ✓
- 显式 UTF-8 ✓；planId 为 canonical `UUID.toString()`（canonical 形式）✓；scheduledAtMillis 为秒级稳定的 epoch 毫秒（`ReminderManager` 计算值，重复 delivery 恒定）✓
- 同一 plan 不同 scheduledAtMillis → 不同 ID（`ReminderDoseFactoryTest different occurrences create different ids`）✓
- 无随机 ID、不依赖 Locale/charset/时区字符串 ✓
- **内容稳定性（关键检查）**：ID 由 `scheduledAtMillis` 派生，内容 `occurredAt` 由首次处理时的 `recordedAtMillis` 派生——但重复 delivery 命中 existing（同 source）时**直接返回 existing、不调用 createEvent**（RecordDoseEventAction L45-48），因此同一 ID 的内容在首次持久化后恒定，重复 delivery 不会构造 same-ID/different-content 冲突；测试 `duplicate notification delivery is replayed without a second write` 证实（1 insert、1 行、4 副作用）✓
- 不与 slot ID 混淆（slotId=null，设计 §6.1）✓；未替换任何外部 protocol ID（Wear action_id 仍归 6C 设计 §6.5）✓
- **P2-F1**：notification confirm 对 disabled plan 使用 `requireEnabledPlan=false`（ReminderReceiverWork.kt L124），记录 disabled plan 的事件；旧行为 `takeIf { isEnabled }` 不记录。设计 §6.2 仅定义 missing/deleted = stale，未锁定 disabled 语义。实际触发路径极窄（`ReminderManager` 不为 disabled plan 调度通知，确认动作依赖通知存在），无数据风险。建议 6C 或后续设计确认。不阻止提交。

## Widget stable-ID verdict

- 算法：`UUID.nameUUIDFromBytes("widget:$planId:${recordedAtMillis / 60_000L}".toByteArray(StandardCharsets.UTF_8))`（`WidgetWork.kt:153-157`）——epochMinute 显式为 `recordedAtMillis / 60_000L`，与设计 §6.4 一致 ✓
- 同一 Intent 重复 delivery → 同一 ID（clock 在 handle 开头捕获一次，L113）✓
- **分钟级 key 语义（产品批准）**：同分钟内两次独立点击 → 相同 ID；第二次命中 existing（同 source）→ replay（first-accepted wins），不覆盖；设计 §6.4 明确批准 "The first accepted event wins within the existing plan/minute key"，并刻意终止旧 upsert 的 "last delivery overwrites time" 行为 ✓；`WidgetWorkTest same Widget intent is idempotent and stable within a minute` 显式断言 +1s 同 ID ✓
- **内容稳定性**：`occurredAt` = 精确处理毫秒（不 floor，设计 §6.4 "do not floor the persisted instant"）；重复 delivery 走 existing 返回，不重新构造内容 ✓；不因 refresh 时间变化构造不同内容 ✓
- 不依赖 Locale/charset/时区字符串 ✓；Widget 重建不重复创建（ID 确定性 + replay）✓
- 不将 ID 规则升级为未审协议（与设计一致，6A UI 路径的 widget 记录事件 ID 同规则）✓

## DoseEvent metadata verdict

- **Notification（source=REMINDER）**：`ReminderDoseFactory.createReminderDoseEvent`——id=确定性 UUID、occurredAt=确认毫秒、route/doseMG/ester/extras=plan 快照、source=REMINDER、status=RECORDED、revision=1L、zoneId=调用时设备 zone、localDate=occurredAt.atZone(zoneId).toLocalDate()、slotId=null ✓（设计 §6.2 逐项一致）
- **Widget（source=WIDGET）**：`WidgetWork.createWidgetDoseEvent`——同构，source=WIDGET ✓（设计 §6.4 逐项一致）
- 两份 metadata 均被 instrumentation 在**真实 Room 中**验证（`ReceiverWidgetProductionCutoverTest` L94-100/L116-122）✓
- source 真实枚举、入口区分正确；zoneId 与 occurredAt 对应；localDate 由 occurredAt+zoneId 计算；不发生 PK/legacy 子集丢字段（完整 Domain 对象持久化）✓

## Notification side-effect verdict

- 顺序：`insert → 分析 result → accepted 才 refreshWidgets + cancelNotification → finally finish`（`ContractNotificationActionWork.accepted` L145-156）✓
- conflict/invalid/storage/unexpected/stale → 无成功副作用（`notification conflict and storage failure have zero success side effects` 测试）✓
- 副作用失败 → `AcceptedWithSideEffectFailure`（数据库已提交、不重试 insert、不 rollback——`accepted database write remains when notification side effect fails` 测试）✓
- duplicate 不重复写（replay 单 insert）✓；finish 不代表记录成功 ✓

## Reminder receiver verdict

- 读取经 contracts（`getById` + `findOccurredBetween` 半开区间 `[scheduledAt−1h, scheduledAt+1h+1ms)`，ReminderReceiverWork.kt L54-57，与设计 §6.3 精确一致）✓
- Domain plan/slots；±1h 边界保留（`DoseCheckInMatcher` 毫秒化后语义等价：`abs(occurredAt − scheduledAtMillis) <= 3_600_000L`）✓
- read failure → StorageFailure + 零副作用（测试 L72-100）✓；notification failure → UnexpectedFailure + 零写入 ✓
- 不新增事件写入 ✓；`scheduleNextBatch` 保留（仅 enabled plan）✓
- 所有异步路径 finish 恰一次（launcher 唯一 finally）✓

## Reschedule receiver verdict

- `observeAll().first()` → Domain plans → `ReminderManager.rescheduleDomainReminders`（fail-fast 保留：`reminderReschedule` 测试 L206-216 验证首个失败即中止）✓
- 不构造 legacy plan Repository、不访问 DAO ✓
- 读失败 → StorageFailure + 零调度 ✓；取消/异常仍 finish ✓；无 fallback ✓

## Reminder helper parity verdict

- `DoseCheckInMatcher`：从 timeH（小时浮点）切到 occurredAt 毫秒 + `3_600_000L`——±1 小时窗口语义等价（边界毫秒精度优于浮点小时）；route/ester/dose(1e-6)/extras 过滤不变 ✓；边界事件测试保留（±1h 包含、+1h+1ms 排除）✓
- `MedicationPlanReminderSchedule`：仅删除 legacy overload，生产实现零变化；request code `planId.hashCode() + timeIndex*1000 + occurrenceIndex` 不变 ✓；DAILY/WEEKLY/CUSTOM 与顺序不变（测试改为对 Domain 每种类型断言 occurrence 数量与 timePosition 顺序）✓；DST gap/overlap 断言 Java 默认行为（实现未变，行为即旧行为）✓
- `ReminderManager`：Domain 版本保留；request code/notificationId 公式、`reminderEvaluationTimeMillis`、精确闹钟 fallback、双循环 cancel 均不变 ✓
- `ReminderDoseFactory`：ID/metadata 正确（见 metadata verdict）✓
- 无 Repository/DAO/Entity 访问；不复制事实数据到长期缓存 ✓

## Widget work verdict

- enabled plan 经 `observeEnabled().first().take(2)` ✓；event 经 `getEventsForPk(now)` ✓；quick-record 经 `RecordDoseEventAction`（insert）✓
- 无 DAO/Entity/AppDatabase；不构造 legacy Repository ✓
- read failure 不造假状态（`snapshot repository failure does not create a fake state` 断言抛异常）✓
- snapshot 只是派生显示值，不写存储 ✓
- PK 投影：`WidgetWork` 私有 `toWidgetPkEvent`/`toWidgetPkTimeH`（L183-208），selection（antiandrogen 排除、未来事件排除）、排序、2 步浓度、`LegacyTimeAdapter`、1e-6 tolerance 保留（`WidgetWorkTest` 以独立 `SimulationEngine` 对照 1e-6）✓；未扩散为公开 adapter（`internal`，仅 widget 包）✓
- 不复用 6A 私有桥形成跨边界依赖（`Batch6DoseEventCompatibility` 未被 Widget 引用）✓；Batch 7 可替换 ✓

## Widget refresh verdict

- 顺序：`insert → success/replay → refreshWidgets → showRecorded → finish`（`ContractWidgetQuickActionWork.accepted`）✓
- conflict/StorageFailure/exception → 不显示成功（`Widget collision and storage failure never refresh or overwrite`）✓
- refresh 失败 → 行保留、不重试 insert（`refresh failure keeps the accepted row and does not retry insert`）✓；下一次正常 refresh 恢复显示（refresh 每次从 contracts 重建）✓
- duplicate Intent：不重复插入、不重复成功副作用（replay 单次副作用，测试 L129-137 两次 handle → 4 个副作用/1 行）✓

## WidgetUtils verdict

- 展示字段/计划排序/事件选择/concentration/curve 行为不变（毫秒化窗口语义等价，见 helper parity）✓
- PendingIntent/action/extras 不变（`openAppPendingIntent` 等未改）✓
- 无第二缓存事实源 ✓；Manifest 无变化 ✓
- **P2-F2**：`WidgetUtils.widgetDisplayName()`（L260-266）复制 Ester 显示名映射，与 `utils/MedicationPlanDescription.kt` L45-51 及 legacy `data/MedicationPlan.kt` L103-108 三处重复（输出逐字一致，WidgetWorkTest/既有测试覆盖）。6A F1（常量复制）同类。建议提取共享 internal 扩展。不阻止提交。
- **P2-F3**：`WidgetUtils` 比较逻辑中 `maxByOrNull { localDateTimeToHours(...) }`（L99/L106）与其余 `scheduledMillis(...)` 混用——单调映射下功能等价，纯风格不一致。不阻止提交。

## Static boundary verdict

`Batch6ReceiverStaticBoundaryTest` 扫描 6 个目标生产文件（3 reminder receivers + ReminderReceiverWork + WidgetReceiver + WidgetWork）+ launcher，禁止 GlobalScope/runBlocking/WIDGET_SCOPE/`doseEventDao(`/`medicationPlanDao(`/`DoseEventEntity`/`MedicationPlanEntity`/`data.DoseEventRepository`/`data.MedicationPlanRepository`；launcher 文件断言 `finish()` 恰好出现一次、含 SupervisorJob 与 finally ✓

- 全限定名场景：`data.DoseEventRepository` 子串同时匹配 import 行与全限定使用，legacy Repository 裸用必然带 import → 有效 ✓
- 未扫描 RecordDoseEventAction/ReminderDoseFactory/ReminderManager/MedicationPlanReminderSchedule/DoseCheckInMatcher/WidgetUtils——经人工逐行审阅 + 全仓 grep 确认无违规 ✓（辅助证据性质，编译/集成验证为主证据）
- **P2-F4**：扫描方式的固有脆弱性——`finish()` 以子串 indexOf/lastIndexOf 计数（变体 `finish ()` 可逃逸）；相对路径依赖 Gradle 工作目录（当前有效，:app: 模块下运行）。当前代码全部合规，属测试鲁棒性观察。不阻止提交。

## Lifecycle test verdict

- `ReceiverWorkLauncherTest`（4 测试）：受控 `QueueDispatcher`（无 Thread.sleep、同步调度）；success/idempotent/conflict/not-found/storage-failure → finish 恰一次 + job completed；unexpected failure → onUnexpectedFailure 一次 + finish 一次；**in-process cancellation → finish 一次 + job cancelled**；一个 delivery 取消不影响另一个 ✓
- `ReceiverLifecycleInstrumentationTest`（5 测试）：真实设备注册 receiver（RECEIVER_NOT_EXPORTED + 包定向）+ CountDownLatch（10s 超时）；notification 9 种 typed outcome 全 finish once；reminder/reschedule/widget receiver finish once；onReceive 非阻塞（suspended work 挂起时已返回，resume 后 finish）；cancellation + exception 均 finish once（cancellation 不触发 onUnexpectedFailure，其他触发一次）；同步拒绝不启动 work ✓
- **异常/取消路径非假覆盖**：上述两类测试均覆盖异常与取消，finish 通过回调计数 + latch 断言（非日志推断）✓；notification/Widget receiver 均覆盖 ✓

## Room integration verdict

`ReceiverWidgetProductionCutoverTest`（2 测试）：

- 可丢弃 file-backed Room（`batch6b_receiver_widget_test.db`），Before/After 删除 db/wal/shm/journal，`assertSingleDisposableDatabase`（不打开 evolune_database、无第二数据库）✓；`ProductionRepositoryProvider` internal constructor 注入，不触碰 production singleton ✓
- 真实覆盖：notification insert + replay（2 次 handle → 1 行）+ 完整 metadata（真实 SQLite 回读断言 source/occurredAt/zoneId/localDate/slotId/revision）+ 副作用各 2 次 + `user_version=3` + close/reopen 后 plan slots 保序恢复（8:30, 20:00）✓
- conflict：MANUAL collision → Conflict + 行逐字段不变 + 零副作用 ✓
- **StorageFailure 走真实 SQLite**：`CREATE TRIGGER ... RAISE(ABORT)` 使真实 insert 失败 → StorageFailure + 无部分写入（rawEventCount==1）+ 零副作用 ✓（非 fake 覆盖）

## Legacy bypass audit verdict

全仓 grep（`doseEventDao|medicationPlanDao|DoseEventEntity|MedicationPlanEntity|AppDatabase.getDatabase|data.DoseEventRepository|data.MedicationPlanRepository|...Repository(`）：

- 3 个 reminder receiver：零命中 ✓
- EvoluneWidgetReceiver / WidgetUtils / WidgetWork：零命中 ✓
- notification/Widget 写只经 contract insert ✓；plan/event 读只经 contracts ✓；无双写、无 fallback ✓
- 剩余生产引用仅两处，均为 6C 锁定边界：`WearDataLayer.kt` L13/L122-163（DAO/Entity/AppDatabase/upsert）与 `MainActivity.kt` L39-40（legacy Repository 仅 Wear dashboard 同步）✓
- persistence implementation/mapper/migration/DAO/Entity 内的直接使用合法且未修改 ✓
- **无其他 feature/receiver/widget 生产绕过 → 无 P0/P1**

## Dual-phone matrix verdict

设备核查（`adb getprop`）：

- API 33 phone：`emulator-5554` = **Evolune_API33_Migration**（AVD 与任务指定 `emulator-5560` 相同，本次 adb 端口为 5554，characteristics=emulator 不含 watch）✓
- API 35 phone：`emulator-5558` = **Pixel_7**（SDK 35，characteristics=emulator）✓
- `emulator-5556` = featherline_wear_api35（Wear，未用于 phone 验收）✓

独立执行结果：

| 验证 | 设备 | 结果 |
|---|---|---|
| 6B focused instrumentation（2 类，7 测试）| API 33（5554）| 7/7，0 failures，0 skipped（XML 核实）|
| 6B focused instrumentation（同 2 类）| API 35（5558）| 7/7，0 failures，0 skipped（XML 核实）|
| 全量 connected（91 测试）| API 33（5554）| 91/91，0 failures，0 skipped（XML 核实）|
| 全量 connected（91 测试）| API 35（5558）| 91/91，0 failures，0 skipped（XML 核实）|

## Schema and boundary verdict

- `git diff`：app/schemas、data/migration、core/、DAO/、Entity/、MainActivity.kt、HRTViewModel.kt、wear/、AndroidManifest.xml 全空 ✓
- Room version = 3；`MIGRATION_2_3` 无变化；contracts/Domain/DAO/Entity/JSON v1/Wear protocol/PK/Gradle/Manifest 无变化 ✓
- schema blob SHA-256 独立验证（`git cat-file blob` 原始字节，cmd 重定向防编码污染）：
  - schema 2 = **B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA** ✓
  - schema 3 = **044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72** ✓
- 注：工作树 2.json 文件级 SHA-256 为 C4770838…（CRLF 工作副本），git 视为与 blob 相同（diff 为空）——不构成差异

## Report accuracy verdict

逐节核对 `PHASE_1_BATCH_6B_REPORT.md` 与代码/独立验证结果：

- 25 个文件清单 ✓；调用链切换 ✓；goAsync/scope/finish 描述 ✓；stable IDs 与 metadata ✓；replay/conflict/storage ✓；reminder/reschedule/widget 行为 ✓；legacy/DAO/Entity 审计 ✓（WearDataLayer 与 MainActivity 两处如实声明为 6C 边界）✓
- 45/45 6B JVM（8 suites = 4 新 suite 32 + 4 修改/边界 suite 13）——**独立对账一致** ✓；334/334 全量 JVM（39 suites，0 skip）✓；双 phone 91/91 ✓；assembleDebug PASS ✓
- schema hashes 与独立计算一致 ✓
- 诚实声明：Wear 未切换、绕过未全清（Wear 边界）、Batch 6 未完成、未用真实数据库、Room v3 不可发布 ✓
- 报告声称 P0/P1/P2=0/0/0——**与独立审阅不一致处**：本审阅认定 5 项 P2（均不阻止提交，不影响任何行为/数据）。其余结论如实。
- **P2-F5**：报告 §13 "production failure semantics were not relaxed" 与测试改动相符 ✓（无放宽断言证据；测试仅替换已删除 legacy 的对照目标为硬编码期望，生产实现未改）

## Findings

### P0
None.

### P1
None.

### P2

**F1 — disabled-plan notification confirm 记录行为变化未在设计锁定**
- Severity: P2
- 文件: `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderReceiverWork.kt:124`（`requireEnabledPlan = false`）
- 问题: 旧实现 `takeIf { it.isEnabled }` 对 disabled plan 不记录事件；新实现记录（Accepted）。设计 §6.2 只定义 missing/deleted = stale。
- 触发条件: disabled plan 的通知确认——实际几乎不可达（ReminderManager 不为 disabled plan 调度，通知不会存在）。
- 影响: 行为差异，无数据损坏风险。
- 依据: 6A 前 `MedicationNotificationActionReceiver` 代码（`plan?.isEnabled == true` 守卫）；设计 §6.2。
- 最小修复: 在 6C 或后续批次确认 disabled 语义（或设 `requireEnabledPlan=true` 并把 disabled 并入 stale 分支）。
- 是否阻止提交: 否。

**F2 — Ester 显示名映射三处复制**
- Severity: P2
- 文件: `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetUtils.kt:260-266`（`widgetDisplayName`）
- 问题: 与 `utils/MedicationPlanDescription.kt:45-51` 及 legacy `data/MedicationPlan.kt:103-108` 输出逐字重复。
- 影响: 无（输出一致，测试覆盖）。
- 依据: 逐字对比确认一致。
- 最小修复: 提取共享 internal 扩展。
- 是否阻止提交: 否。

**F3 — WidgetUtils 内比较函数混用**
- Severity: P2
- 文件: `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetUtils.kt:99,106`（`localDateTimeToHours`）与其余 `scheduledMillis` 混用
- 影响: 单调映射下功能等价，纯风格不一致。
- 是否阻止提交: 否。

**F4 — 静态边界测试扫描方式脆弱性**
- Severity: P2
- 文件: `app/src/test/java/io/github/yuninggu/evolune/reminder/Batch6ReceiverStaticBoundaryTest.kt`
- 问题: `finish()` 以子串计数（变体可逃逸）；扫描文件集不含 RecordDoseEventAction/ReminderDoseFactory/ReminderManager 等（已人工审阅合规）；相对路径依赖工作目录。
- 影响: 无（当前全部合规；编译 + 集成验证为主证据）。
- 是否阻止提交: 否。

**F5 — 报告 P0/P1/P2=0/0/0 与本审阅 P2 清单的差异**
- Severity: P2（报告口径）
- 文件: `docs/phase-reports/PHASE_1_BATCH_6B_REPORT.md:349`
- 问题: 本审阅认定 5 项 P2 观察（全部非阻断）。报告 0/0/0 的声称偏乐观，但不构成实现不实。
- 是否阻止提交: 否。

## Independent validation executed

以下全部为本次审阅实际执行：

| 命令 | 设备/范围 | 结果 |
|---|---|---|
| `adb devices` / `getprop ro.build.version.sdk` / `ro.build.characteristics` | 5554/5558/5556 | 5554=API33 phone（Evolune_API33_Migration）、5558=API35 phone（Pixel_7）、5556=Wear（不用）|
| `gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ReceiverWidgetProductionCutoverTest,ReceiverLifecycleInstrumentationTest --rerun-tasks`（ANDROID_SERIAL=emulator-5554）| API 33 phone | 7/7，XML: tests=7 failures=0 errors=0 skipped=0 |
| 同上（ANDROID_SERIAL=emulator-5558）| API 35 phone | 7/7，XML: tests=7 failures=0 errors=0 skipped=0 |
| `gradlew :app:connectedDebugAndroidTest --rerun-tasks`（ANDROID_SERIAL=emulator-5554）| API 33 phone 全量 | 91/91，XML: tests=91 failures=0 errors=0 skipped=0 |
| 同上（ANDROID_SERIAL=emulator-5558）| API 35 phone 全量 | 91/91，XML: tests=91 failures=0 errors=0 skipped=0 |
| `gradlew :app:testDebugUnitTest --tests RecordDoseEventActionTest --tests ReceiverWorkLauncherTest --tests ReminderReceiverWorkTest --tests WidgetWorkTest --rerun-tasks` | 6B 新增 4 suite | 8+4+11+9 = 32/32，XML 核实 0 failures/skipped |
| `gradlew :app:testDebugUnitTest --tests DoseCheckInMatcherTest --tests MedicationPlanReminderScheduleTest --tests ReminderDoseFactoryTest --tests Batch6ReceiverStaticBoundaryTest --rerun-tasks` | 6B 修改 4 suite | 4+5+3+1 = 13/13，0 failures/skipped；**与新增合并 = 45/45（8 suites）对账一致** |
| `gradlew :app:testDebugUnitTest --rerun-tasks` | 全量 App JVM | **39 suites / 334 tests / 0 failures / 0 skipped**（XML 逐文件累加）|
| `gradlew :app:assembleDebug --no-daemon` | 构建 | PASS |
| `git cat-file blob <schema2/3 blob>` SHA-256（cmd 原始字节）| schema | 2 = B8DA54ED…、3 = 044013C0…，均匹配报告 |
| `git diff --stat` 越界范围 + `git diff --check` + `git merge-base --is-ancestor` | 范围 | 全部通过 |
| `grep @Ignore` | 测试 | 无 |

未执行（资源/时间考虑，报告已声明值不独立复现）：Migration 43、Mapper 53、Core 47、PK 49、Wear 1 单项 JVM；lint/KSP；Repository/migration instrumentation 73 单独跑（已包含于双 phone 全量 91 中，两机均 0 失败）。

## Final decision

**APPROVE WITH P2**

- 提交前必须处理事项：无（5 项 P2 均可推迟，不改变行为/数据）。
- 可推迟事项：F1（6C 或后续设计确认 disabled 语义）；F2/F3（共享扩展重构）；F4（静态边界测试加固）；F5（报告口径）。
- 是否建议提交 Batch 6B：**是**。
- 是否建议创建 `phase-1-batch-6b`：**是**。
- 是否建议随后进入 6C：**是**（Wear listener + dashboard 同步收口，含设计 §6.5 action ownership 与 §6.6 生命周期合同；Wear 为唯一剩余生产绕过点）。
- 是否继续禁止真实数据库和 release：**是**——Room v3 保持内部、不可发布，直至 Batch 7/8 完成。
