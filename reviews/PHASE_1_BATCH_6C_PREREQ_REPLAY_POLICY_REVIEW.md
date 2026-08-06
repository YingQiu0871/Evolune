# Evolune Phase 1 Batch 6C Prerequisite Replay-Policy Independent Review

Date: 2026-08-06
Reviewer: DeepSeek (independent read-only)
Branch: `phase1/batch6c-prereq-replay-policy`
Prerequisite tag: `phase-1-batch-6-replay-policy-design-v1`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 1**（唯一 P2 = 已批准且先前已审定的 plan_id replay 验证限制；另有 2 项非阻断观察，同属 P2 级）
- **允许提交**前置实现
- **允许创建前置实现标签**
- **允许随后恢复 Batch 6C**（仅在前置实现提交 + 打标签后，且 Wear 接线/DataItem 删除确认为 6C 工作）
- **不得直接将本分支标签当作 `phase-1-batch-6c`**；正式 `phase-1-batch-6` 仍需等 6C 完成
- 最大剩余风险：Wear replay 无法重新验证原始 plan_id（P2，受 action_id 随机唯一性与 recorded_at 稳定性约束）；Wear production 尚未接线（6C 工作）；Room v3 仍不可发布

## Git and scope

- 分支 = `phase1/batch6c-prereq-replay-policy` ✓
- `git merge-base --is-ancestor phase-1-batch-6-replay-policy-design-v1 HEAD` 退出码 0；tag 存在且指向设计提交 ✓
- 暂存区空；`git diff --check` 通过 ✓
- 工作树恰好 9 个文件（5 修改 + 4 新增，与任务清单逐一匹配）✓
- 越界 diff 全部为空：app/schemas、data/migration、core/、RoomDoseEventRepository.kt、DAO/Entity、wear/（app 与模块）、AndroidManifest.xml、gradle.properties、app/wear build.gradle.kts ✓
- 无数据库、APK、日志、密钥或真实健康数据产物 ✓

## Architecture and facade verdict

- 独立还原结构：`ContractNotificationActionWork`/`ContractWidgetQuickActionWork` → `LocalActionRecorder` → `RecordDoseEventEngine` → `DoseEventRepository`（ReminderReceiverWork.kt:115、WidgetWork.kt:108）✓
- `WearActionRecorder` → 同一 engine（WearActionRecorder.kt:15），**当前无 production caller**（全仓 grep 证实：`WearActionRecorder` 仅出现于自身文件、测试与 androidTest）✓
- 私有 engine（`internal class RecordDoseEventEngine`，RecordDoseEventAction.kt:48）不向 feature/UI 暴露：reminder/widget 生产代码只引用 `LocalActionRecorder`，不引用 `ExistingEventPolicy`/engine（ReplayPolicyBoundaryTest L36-37 断言）✓
- `LocalActionRecorder` 只暴露 `recordReminder`/`recordWidget`（L17-47），ID 内部派生（`localEventId` L49-52），不接收外部 action ID ✓
- `WearActionRecorder` 只暴露 `record(planId, actionId, recordedAt, createEvent)`，固定 WEAR source + `FirstAcceptedBySourceAndOccurredAt`，**无 source-only 方法**（WearActionRecorder.kt:17-32）✓
- 调用点无法省略 policy（facade 固定）；无默认 weak policy（engine 签名 `policy: ExistingEventPolicy,` 无默认值，ReplayPolicyBoundaryTest L32-33 断言）✓
- 预读与竞态逻辑集中在 engine 一处，三入口不复制 ✓；Repository contract 不变 ✓
- Wear production 未接线：`WearDataLayer.kt` 无 LocalActionRecorder/WearActionRecorder/ExistingEventPolicy（ReplayPolicyBoundaryTest L38-40 断言 + 人工 grep 双确认）→ **无 P1**

## Type-safety verdict

- `LocalActionRecorder` 只能表达 FirstAcceptedBySource（两种方法内部均固定该 policy 与真实本地 source）✓
- `WearActionRecorder` 固定要求 `Instant recordedAt` 显式参数（无默认）✓；固定 source=WEAR ✓
- Wear 调用方无法通过公开参数选择 source-only：`record()` 内部硬编码 `FirstAcceptedBySourceAndOccurredAt`，无参数可改 ✓
- engine 的弱 policy 可从同 module 构造（Kotlin internal 无 package-private）——**残余面**：`ExistingEventPolicy.FirstAcceptedBySource` 与 engine 均为 `internal`（RecordDoseEventAction.kt:20-31,48），application 包外代码理论上可绕过 facade。当前生产无此类调用（grep + 静态测试），且 `policy.expectedSource != expectedSource → Invalid`（L69-70/82-83）提供第二道防护。此残余面记为 P2 观察（F3），不构成 P1：addendum 批准的结构以 internal 近似"私有"，静态边界测试 + 评审兜底
- 误用防护是编译结构（无 source-only 方法、无默认参数、facade 封闭）而非仅注释/约定 ✓

## Result-model verdict

- `RecordAcceptance`（Inserted / RepositoryIdempotent / FirstAcceptedReplay）独立枚举（RecordDoseEventAction.kt:14-18），`Accepted(plan, event, acceptance)` 携带权威事件 ✓
- 拒绝/失败结果完整：PlanNotFound / PlanDisabled / Conflict / Invalid / StorageFailure / UnexpectedFailure ✓
- **FirstAcceptedReplay 独立分类、不伪装为 RepositoryIdempotent**：`InsertResult.Idempotent` 严格映射 `RecordAcceptance.RepositoryIdempotent`（L146-150/L184-188），replay 走独立分支（L136-138/L167-169）✓；测试 `Local policy distinguishes repository idempotency from first accepted replay` 显式验证（forced Idempotent → RepositoryIdempotent，非 replay）✓
- Repository conflict 不被默认重释：strict 路径直接映射 Conflict（L209），first-accepted 路径的冲突重读有显式 policy 规则（仅 expected source/occurredAt 匹配才转 replay）✓
- StorageFailure/exception 不转 replay（catch 统一失败分类，L96-102；CancellationException rethrow）✓；不携带 SQLite 消息/DoseEvent/extras 到失败结果 ✓
- Notification/Widget 成功副作用只接受三种 accepted（调用点 `acceptance != RecordAcceptance.Inserted` 映射 replayed + accepted 分支）✓；Conflict/Invalid/StorageFailure 无成功副作用（当分支直接返回）✓

## RepositoryStrict verdict

- engine strict 路径（RecordDoseEventAction.kt:104-121,199-211）：loadPlan → createEvent → matchesIdentity → **单次 insert，无 event 预读** ✓
- Inserted/Idempotent/Conflict/Invalid 直接映射，**conflict 不重读不重释**（L209）✓
- StorageFailure/Invalid/exception 均失败 ✓；不生成新 ID、不 fallback ✓
- **零预读证明**：`RepositoryStrict maps repository outcomes...` 测试断言 Inserted/Idempotent/Conflict/Invalid 各路径 `getCalls == 0`（RecordDoseEventActionTest.kt:45,58,67,77）✓
- 当前无 production 入口使用（仅引擎测试锁定）✓——与 addendum §3.1 一致
- 注：strict 的 plan 读取是 materializer 前置（createEvent 需要 plan），非事件预读；addendum 语义"insert 前不预读（事件）"满足 ✓

## LocalActionRecorder verdict

- 流程与 addendum §3.2 逐项一致：ID 内部派生 → getById → existing 同 source → FirstAcceptedReplay（携带存储事件）/ 异 source → Conflict → 不存在 → createEvent + matchesIdentity → insert → Idempotent 直映 → conflict 一次重读同规则（RecordDoseEventAction.kt:123-156）✓
- 不覆盖首次事件（existing 分支无写）✓；不生成替代 ID ✓；不调 legacy Repository ✓
- **查询错误处理**：`doseEvents.getById` 抛 RepositoryStorageException → StorageFailure；其他异常 → UnexpectedFailure；CancellationException rethrow——**不会把查询失败当作"不存在"继续 insert**（catch 在 execute 外层，早于任何 insert）✓；测试 `Local storage and unexpected failures are never accepted as replay`（getFailure 注入 → StorageFailure/UnexpectedFailure + insertCalls==0）✓

## Notification regression verdict

- `ContractNotificationActionWork` 改用 `LocalActionRecorder.recordReminder`（ReminderReceiverWork.kt:115-129）：expected source = REMINDER（facade 固定）✓；ID 算法不变（`localEventId("reminder", planId, scheduledAtMillis)` == 6B `reminderDoseEventId`，字符串一致）✓；scheduledAtMillis 单位不变 ✓
- 首次 occurredAt = 首次处理时间（clock.millis() 一次捕获）✓；延迟重投（retry 时间不同）→ FirstAcceptedReplay + 首次事件保留（测试 `Reminder facade derives trusted ID...`，materializer 0 调用 + 原事件不变）✓
- source mismatch → Conflict（`Local policy rejects another source...`）✓
- 成功副作用：Accepted（三种 acceptance）→ refreshWidgets + cancelNotification（原 6B 顺序）；Conflict/StorageFailure 等 → 零副作用 ✓
- 失败分支无遗漏：`when` 穷尽所有 RecordDoseEventActionResult（Accepted/PlanNotFound/PlanDisabled/Conflict/Invalid/StorageFailure/UnexpectedFailure）✓
- receiver goAsync/finish 未修改（不在本批 diff 中）✓
- **真实 Room 验证**：instrumentation 用不同 clock（+30.456s）重投 notification → Accepted(true) + 首事件保留（ReceiverWidgetProductionCutoverTest 新逻辑）✓

## Widget regression verdict

- `ContractWidgetQuickActionWork` 改用 `LocalActionRecorder.recordWidget`（WidgetWork.kt:108-126）：expected source = WIDGET ✓；ID 算法不变（`localEventId("widget", planId, recordedAtMillis/60_000)` == 6B `widgetDoseEventId`）✓；epochMinute 规则不变 ✓
- 首次精确 occurredAt 保留（recorder 内部派生 ID，createEvent 仍收到 eventId + 精确毫秒）✓；同分钟重投 → FirstAcceptedReplay + 首行不变（测试 `Widget facade folds precise timestamps...`）✓
- source mismatch → Conflict + 零副作用 ✓；accepted 后才 refresh（accepted() 分支）✓；refresh 失败不重新 insert（6B 语义保留，accepted 内副作用 try/catch）✓
- Widget protocol/PendingIntent/PK 路径不变（WidgetWork 仅调用点改造）✓
- 真实 Room 验证：同分钟 +1.789s 重投 → Accepted(true) + 首事件保留 ✓

## WearActionRecorder boundary verdict

逐行核实（WearActionRecorder.kt + engine L158-194）：

1. action_id 原样作为 event ID ✓
2. **existing 判定先于 plan 读取**（L166-169）✓
3. 双条件 `source == WEAR && occurredAt == expectedOccurredAt` → FirstAcceptedReplay ✓
4. source 或 occurredAt 任一不同 → Conflict ✓
5. **已存在时 materializer 0 调用**（existing 分支在 createEvent 之前返回）✓
6. **conflict（existing 不匹配）时 materializer 0 调用** ✓
7. ID 不存在时才 loadPlan + materializer ✓
8. materializer 至多调用一次（单点调用 L176）✓
9. candidate 校验：`matchesIdentity(eventId, WEAR)` + `occurredAt == expectedOccurredAt`（L177-179）——错 ID/错 source/错时间 → Invalid + 零 insert（测试 `Wear first materialization rejects...` 三候选）✓
10. insert Idempotent → RepositoryIdempotent（L184-188）✓
11. insert conflict → 一次重读（L189-191）✓
12. 重读 source+occurredAt 匹配 → FirstAcceptedReplay；不匹配 → Conflict ✓
13. StorageFailure/exception 不 replay ✓；CancellationException rethrow ✓
14. 不生成随机 ID ✓；不读 plan（replay 路径）——plan 读取仅经 loadPlan（首次物化）✓
- 特别核查：materializer 抛 CancellationException → 外层 catch rethrow（保留取消语义，测试 `Wear storage unexpected and cancellation...`）✓；materializer 返回错误 ID/source/time → Invalid（L177-181 + 测试三候选）✓；insert 后重读失败（重读抛 StorageException）→ 外层 catch → StorageFailure ✓（竞态重读失败不转 replay）；并发重复 → 见 Concurrency verdict ✓

## Materializer zero-call verdict

关键验收项——全部以**真实调用计数或 fail-fast 证明**（非结果推断）：

- JVM（fake 计数）：Wear replay `plans.getCalls=0 + materializerCalls=0 + insertCalls=0`（RecordDoseEventActionTest.kt:327-329）；occurredAt/source mismatch `getCalls=0 + materializerCalls=0`（L350-351）；首次物化 `getCalls=1 + materializerCalls=1`（L371-373）；PlanNotFound/PlanDisabled `materializerCalls=0`（L433,447）✓
- **真实 Room（delegation 计数）**：replay 在 plan 编辑后 `replayPlans.getCalls=0 + replayMaterializerCalls=0 + replay.event == first.event + plan=null`（ReceiverWidgetProductionCutoverTest:239-244）；conflict（+1ms）`conflictPlans.getCalls=0 + conflictMaterializerCalls=0`（L255-259）✓
- 竞态重读不二次调用 materializer：forced Conflict 测试断言 `getCalls==2`（两次读：预读 + 重读）且 insertCalls==1——materializer 仅首次物化时 1 次（L370-373）✓
- **结论：零调用证明充分（fake + 真实 Room 双层）→ 无 P1**

## Concurrency verdict

- Local：`Local insert race rereads once...`（forced Conflict + 预读后注入行）→ 匹配 FirstAcceptedReplay / 不匹配 Conflict，各 getCalls==2 ✓
- Wear JVM 并发（RecordDoseEventActionTest.kt:501-539）：`CoordinatedDoseEventRepository` 用 `CompletableDeferred` 栅栏——**两个协程的初始预读都必须完成并返回 null 后才继续**（L594-602），确保双预读为空 → 双 insert（mutex 序列化）→ 断言 `setOf(Inserted, RepositoryIdempotent)` + 单行 + `insertCalls==2`——**非串行伪装，双协程真实并行** ✓
- Wear 异 occurredAt 并发：1 Accepted + 1 Conflict + 首行保留 ✓
- **真实 SQLite 并发**（ReceiverWidgetProductionCutoverTest `concurrentWearActionsKeepOneAuthoritativeRow`）：`async(Dispatchers.IO)` × 真实 Room——相同 action 恰 1 Inserted + 全 Accepted + 单行；异 occurredAt 1 Accepted + 1 Conflict ✓（真实 Repository/SQLite 事务序列化路径）
- 无永久全局锁（engine 无锁）；异常/取消不锁死（无共享状态）✓

## Repository fake verdict

- `FakeDoseEventRepository`（RepositoryFakes.kt 6B 版）insert equality = `existing == event`（DoseEvent data class 全字段）——与真实 Room `existing == event`（RoomDoseEventRepository.kt:87）**语义一致** ✓
- **未用 source-only equality 模拟 Repository idempotency**（fake 的 Idempotent 条件是全等；source-only 分类完全在 engine 层）✓
- query/insert/StorageFailure/Invalid/Conflict 可独立注入（getFailure/rangeFailure/insertFailure/forcedInsertResult/beforeForcedInsertResult）✓
- 并发 fake（Coordinated）Mutex + AtomicInteger 线程安全 ✓；计数线程安全 ✓
- expected result 由被测 engine 实际执行产生（fake 不预置结果，forcedInsertResult 只注入 Repository 层行为）✓
- fake 不掩盖 contract 缺陷：真实语义由 disposable Room 测试补强（两个新 instrumentation 测试全走真实 SQLite）✓

## Disposable Room verdict

- `ReceiverWidgetProductionCutoverTest`：disposable file-backed Room v3（`batch6b_receiver_widget_test.db`）+ `ProductionRepositoryProvider(openDatabase())` 注入，不打开 `evolune_database`，不触碰 production singleton ✓；Before/After 清理 db/wal/shm/journal + 单数据库断言 ✓
- 真实覆盖（含新增）：reminder 首次 insert + 动态 occurredAt 重投（不同 clock）+ widget 同分钟重投 + source mismatch（6B 已有）+ 原事件逐字段保留 + **Repository full-content Idempotent/Conflict**（`insert(first.event)` → Idempotent；`doseMG+1.0` → Conflict，真实 SQLite）✓ + Wear source+occurredAt replay（plan 编辑后，零调用计数）+ Wear occurredAt mismatch（零调用）+ 真实并发（同/异 action）+ StorageFailure（6B trigger 测试保留）+ `user_version=3` ✓
- 并发测试**经过真实 Repository/SQLite**（非 fake）——报告 §10 描述准确 ✓

## Static boundary verdict

`ReplayPolicyBoundaryTest`（1 测试）：

- 能发现：Wear production 提前接线（WearDataLayer 无任何 recorder/policy 引用 L38-40）；Wear 用 LocalActionRecorder（wearRecorder 无 FirstAcceptedBySource L31）；feature 调用私有 engine/直接 policy（reminder/widget 无 ExistingEventPolicy L36-37）；default policy（`policy: ExistingEventPolicy =` 不存在 L33）✓
- LocalActionRecorder 无 WEAR（L29）、有内部 ID 派生（L28）✓；engine 有显式 policy 参数（L32）✓
- 局限（与 6B Batch6ReceiverStaticBoundaryTest 同类）：扫描集为 4 个生产文件（ReminderReceiverWork/WidgetWork/WearDataLayer + 2 recorder + engine 自身）；子串匹配（换行/空格变体可逃逸）；相对路径依赖模块工作目录（当前有效）。非唯一架构证据（编译 + 17 项 JVM 行为测试 + 静态边界 + 真实 Room 共同构成）✓
- 不误判测试中的合法引用（只扫生产路径）✓

## Test-count verdict

独立读取 JUnit XML（非复述摘要）：

| 项 | 独立核实 |
|---|---|
| Replay-policy JVM | **2 suites / 18 tests**（RecordDoseEventActionTest 17 + ReplayPolicyBoundaryTest 1）——17 = 我逐条数出的 @Test 数 ✓ 与报告 18 一致 |
| Full App JVM | **40 suites / 344 tests / 0 failures / 0 errors / 0 skipped**（XML 逐文件累加）✓ = 334 + 10 |
| PK JVM | 报告 49——未独立运行（资源），6B 时已核；本轮代码未触及 pk/ |
| Wear JVM | 报告 1——未独立运行；本轮未触及 wear/ |
| API 33 focused | **9 tests / 0 / 0 / 0**（我跑的 focused 命令为 CutoverTest 4 + Lifecycle 5 = 9；报告 §11 的"4"仅计 CutoverTest 一个 suite——口径差异，报告表格列名 Suites/Tests 下"1/4"指其 focused 命令只含 CutoverTest；我独立执行两类的 9 测试全过，无冲突）|
| API 33 full | 报告 93——未独立运行（资源，API 33 focused 9/9 通过；API 35 full 93/93 独立通过）|
| API 35 focused | **9 tests / 0 / 0 / 0**（XML 核实）|
| API 35 full | **93 tests / 0 failures / 0 errors / 0 skipped**（XML 核实 tests=93）|
| Repository/Migration | 报告 75——已包含于 API 35 full 93 中（独立全量 0 失败）|

- 无 @Ignore（grep 全仓）✓；无 skipped ✓；fixtures 全合成（syntheticPlan/UUID(0L,...)/合成时间戳）✓；未删除或放宽 6B 测试（6B 套件全绿，见回归）✓
- **计数口径说明**："18/344/93" 中 18 与 344 与 93 分别 = test case / test case / test case（XML 计数）；报告表格的"2/18"、"40/344" 的 suites 数也与 XML 一致；"API 33 focused 4" 为 CutoverTest 单类口径

## Dual-phone verdict

设备核查（`adb getprop` / `wm`）：

- `emulator-5560` = **Evolune_API33_Migration**（SDK 33、characteristics=emulator 不含 watch、model sdk_gphone64_x86_64、1080x2400/420dpi）✓
- `emulator-5558` = **Pixel_7**（SDK 35、characteristics=emulator、model sdk_gphone64_x86_64）✓
- 均 `sys.boot_completed=1` ✓；Wear AVD（featherline_wear_api35）未启动/未用于验收 ✓

独立执行：

| 验证 | 设备 | 结果 |
|---|---|---|
| focused（CutoverTest 4 + Lifecycle 5 = 9）| API 33（5560）| 9/9，XML 核实 0 failures/errors/skipped |
| focused（同 9）| API 35（5558）| 9/9，XML 核实 |
| 全量 connected（93）| API 35（5558）| 93/93，XML 核实 tests=93 failures=0 errors=0 skipped=0 |

## Schema and forbidden-boundary verdict

- Room version = 3（AppDatabase 未变）✓
- schema 2/3、MIGRATION_2_3：git diff 空 ✓（blob SHA 在 6B 已独立复算：B8DA54ED… / 044013C0…；identityHash a8036e3f… / c5f5e02c… 本批未变）✓
- contracts/Domain/DAO/Entity/RoomDoseEventRepository：零 diff ✓
- Wear production（app/wear 两处）、Manifest、Gradle、JSON、PK：零 diff ✓

## Report-accuracy verdict

`PHASE_1_BATCH_6C_PREREQ_REPLAY_POLICY_REPORT.md` 逐节核对：

- 两次停止背景（§2）与 addendum/设计 review/tag 权威引用准确 ✓
- API 结构（§4）：共享 engine internal + 双 facade + 无默认 policy + Wear 未接线——与代码一致 ✓
- Result model（§5）三 acceptance 区分 ✓；§6 RepositoryStrict 无预读 ✓；§7 Local 行为与 6B 兼容（replayed 布尔语义不变）✓；§8 Wear 边界 9 条与 engine 逐条一致 ✓
- §9 并发、§10 disposable Room（4 focused tests——实际 CutoverTest 共 4 个 @Test，其中 2 新 2 旧）✓
- §11 计数：与我独立 XML 核对一致（18/344/93 为 test case 数；"focused 4" 为 CutoverTest 单类口径，如实标注列名）✓
- §12 6B 回归声明属实（7 suites 37 tests = 6B 原 8 suites 45 − RecordDoseEventActionTest 8 → 该 suite 现为 17 归入 replay-policy 组；**对账：6B 45 = 37 + 8（旧 RecordDoseEventActionTest）✓；本轮 18 = 17 + 1 ✓；全量 344 = 334 + 9 + 1 ✓**）✓
- §13 schema/架构边界与实际 diff 一致 ✓；§14 唯一 P2 声明与 addendum/设计 review 一致 ✓
- §15 诚实：未启动 6C、Wear 未切换、DataItem 未实施、Room v3 不可发布 ✓
- 未声称：Wear production 已切换 / DataItem 确认已实施 / bypass-zero 完成 / Batch 6 完成 / 真实数据库已验证 / v3 可发布 ✓（全部未出现或明确否定）

## Remaining P2 verdict

唯一 P2（plan_id replay 验证限制）维持 addendum 审定结论：payload 3 字段 + DoseEvent 无 planId 共同造成；首次物化仍验证 plan_id；replay 由 action_id + WEAR source + 精确 recorded_at 约束；碰撞受 watch 端随机 UUID 唯一性约束；不覆盖、不误确认；Batch 8 门槛前协议版本化评估点存在；报告未夸大为完整验证 ✓——**保持 P2，不升级**。

## Findings

### P0
None.

### P1
None.

### P2

**F1 — plan_id replay 验证限制（唯一申报 P2）**
- Severity: P2（接受）
- 依据: addendum §4/§14；`DoseEvent.kt` 无 planId；payload 三字段（6B 已核）
- 影响/修复/是否阻止: 与 addendum 审定一致；不阻止

**F2 — ID 派生三处并存**
- Severity: P2
- 文件: `LocalActionRecorder.kt:49-52`（localEventId）与 `WidgetWork.kt:151`（widgetDoseEventId）、`ReminderDoseFactory.kt:47`（reminderDoseEventId）
- 问题: 相同字符串算法三份实现（`widget:$planId:${ms/60000}`、`reminder:$planId:$scheduledAtMillis`）；后两者现主要被测试引用（RecordDoseEventActionTest 用它们断言 recorder 派生 ID 与 6B 锁定算法一致——作为**跨实现一致性锚**有效，但若实现错误则锚点同错；历史 6B 测试已独立锁定旧算法，风险低）
- 触发条件: 未来修改任一实现而不改其余
- 影响: 无当前行为影响；维护性
- 最小修复建议: 6C 或后续批次统一为单一共享 internal 函数（不阻止本批）
- 是否阻止提交: 否

**F3 — engine/policy 的 internal 可见性残余面**
- Severity: P2
- 文件: `RecordDoseEventAction.kt:20-31,48`（ExistingEventPolicy/engine 为 internal，module 内可见）
- 问题: 理论上同 module 其他包可绕过 facade 直接构造 engine + `FirstAcceptedBySource(任意 source)` + 任意 eventId（绕过本地 ID 派生约束）；当前生产零调用（grep + 静态边界测试）
- 触发条件: 未来新增调用方直接使用 engine
- 影响: 无当前影响；工程防线残余
- 最小修复建议: 静态边界测试扩展到全生产目录（当前只扫 4 个文件）；或 6C 后审视可见性（Kotlin 无 package-private，internal + 评审是 addendum 批准口径）
- 是否阻止提交: 否

## Independent validation executed

实际执行（本会话）：

| 命令 | 范围 | 结果 |
|---|---|---|
| Git 边界（branch/status/diff/--check/--cached/ls-files/merge-base/tag/log）| 分支、9 文件、6a 祖先、暂存区空 | 全部通过 |
| `gradlew :app:testDebugUnitTest --tests RecordDoseEventActionTest --tests ReplayPolicyBoundaryTest --rerun-tasks` | Replay-policy JVM | 2 suites / **18/18**（17+1），XML 核实 0/0/0 |
| `gradlew :app:testDebugUnitTest --rerun-tasks` | 全量 App JVM | **40 suites / 344 / 0 failures / 0 errors / 0 skipped**（XML 逐文件累加）|
| `gradlew :app:assembleDebug --no-daemon` | 构建 | PASS |
| focused connected（`-Pandroid.testInstrumentationRunnerArguments.class=ReceiverWidgetProductionCutoverTest,ReceiverLifecycleInstrumentationTest`）| API 33（5560）| **9/9**，XML: tests=9 failures=0 errors=0 skipped=0 |
| 同上 | API 35（5558）| **9/9**，XML 核实 |
| `gradlew :app:connectedDebugAndroidTest --rerun-tasks` | API 35 全量 | **93/93**，XML: tests=93 failures=0 errors=0 skipped=0 |
| 设备核查 | 5560/5558 | SDK 33/35、characteristics=emulator、model sdk_gphone64_x86_64、boot=1 |
| 越界 diff（schemas/migration/core/RoomRepo/DAO/Entity/wear/Manifest/Gradle）| 禁止范围 | 全空 |
| `grep @Ignore` | 测试 | 无 |
| 全仓 grep 接线核查 | WearActionRecorder/LocalActionRecorder/ExistingEventPolicy | Wear production 零引用 |

未独立运行（资源/已含于全量）：API 33 full connected（报告 93；API 33 focused 9/9 独立通过 + API 35 full 93/93 独立通过）、PK 49、Wear 1、lint/KSP 单项（assembleDebug 内含编译）。

## Final decision

**APPROVE WITH P2**

APPROVE WITH P2 十项条件逐项确认：

1. 两个 facade 类型安全 ✓（无 source-only 方法、ID 内部派生、显式 recordedAt）
2. Wear production 未接线 ✓（grep + 静态测试 + 人工）
3. Wear replay/conflict materializer 零调用 ✓（fake 计数 + 真实 Room delegation 计数双层证明）
4. RepositoryStrict 不重释 conflict ✓（L209 直映 + getCalls==0 测试）
5. FirstAcceptedReplay 独立分类 ✓（RecordAcceptance 枚举 + 测试）
6. Notification/Widget 6B 行为保持 ✓（ID/occurredAt/副作用/replayed 布尔不变；真实 Room 重投验证）
7. fake 与 Room 语义一致 ✓（data class equality 对齐；真实 SQLite 补强）
8. 双 phone 验证通过 ✓（focused 9/9 各机 + API 35 full 93/93，XML 核实）
9. schema/protocol/contract 不变 ✓（越界 diff 全空）
10. 无新 P0/P1 ✓

最终结论：

- 提交前必须处理事项：**无**
- 可推迟事项：F2（ID 函数统一）、F3（静态边界扫描扩展/可见性审视）、唯一 P2（plan_id replay 验证限制，Batch 8 门槛前协议版本化评估）
- 是否建议提交前置实现：**是**
- 是否建议创建前置实现标签：**是**
- 是否建议随后将该分支快进合并到 `phase1/batch6c-wear-cutover`：**是**（作为 6C 的基础，合并后 6C 专注 Wear 接线 + DataItem 删除确认）
- 是否允许之后继续 Batch 6C：**是**（前置实现提交 + 打标签后）
- **不得直接将当前标签当作 `phase-1-batch-6c`**：正式 `phase-1-batch-6` 需等 6C 完成（Wear 收口 + bypass-zero）
- 是否继续禁止真实数据库和 release：**是**——Room v3 保持内部、不可发布，直至 Batch 7/8 完成
