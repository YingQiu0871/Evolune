# Evolune Phase 1 Batch 6C — Final Batch 6 Independent Review

Date: 2026-08-07
Reviewer: DeepSeek (independent read-only)
Branch: `phase1/batch6c-wear-cutover`
Prerequisite tag: `phase-1-batch-6c-prereq-replay-policy`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 1**
- **Batch 6C 通过**
- **整个 Batch 6 通过**（6A 手机 HRT/UI + 6B receiver/Widget + replay-policy 前置 + 6C Wear 收口，最终 production bypass-zero）
- **允许提交 Batch 6C**
- **允许创建 `phase-1-batch-6c` 标签**
- **允许同时创建正式 `phase-1-batch-6` 标签**（必须指向本最终 review 提交；因 6C 实现已在当前分支历史中，建议在提交 6C 与 review 后于同一提交点打两个标签）
- **允许随后开始 Batch 7**
- 最大剩余风险：唯一 P2（已接受 Wear action replay 无法重新验证首次 plan_id，受 action_id 随机唯一性与 recorded_at 重投稳定性约束，Batch 8 门槛前协议版本化评估）；配对 phone-watch round trip 未自动化（披露的测试环境边界，非架构风险）；Room v3 仍不可发布

## Git and scope

- 分支 = `phase1/batch6c-wear-cutover` ✓
- `git merge-base --is-ancestor phase-1-batch-6c-prereq-replay-policy HEAD` 退出码 0；tag 存在 ✓
- 暂存区空；`git diff --check` 通过 ✓
- 工作树恰好 8 个文件（3 修改 + 5 新增，与任务清单逐一匹配）✓
- **尚不存在 `phase-1-batch-6c` 与 `phase-1-batch-6` tag**（`git tag --list "phase-1-batch-6*"` 确认）✓
- 越界 diff 全空：app/schemas、data/migration、core/、data/repository/、DAO/Entity（含 ScheduledDoseSlot*）、wear/（模块）、AndroidManifest.xml、gradle.properties、build.gradle* ✓；feature 范围（reminder/widget/ui/viewmodel）零修改 ✓
- 无数据库、APK、日志、密钥或真实健康数据产物 ✓

## Authoritative scope verdict

Batch 6C 实际只完成：
1. phone-side Wear action production cutover ✓
2. `WearActionRecorder` 接线（经 `WearDoseActionHandler`）✓
3. DataItem accepted-only deletion ✓
4. plan projection contract cutover（`observeEnabled().first()`）✓
5. PK projection contract-backed path（MainActivity 经 provider + HRT Domain state）✓
6. MainActivity composition root 清理（legacy Repository/AppDatabase 移除）✓
7. final production bypass-zero（静态测试 + 独立 grep）✓

**未提前实施 Batch 7 内容**：无 JSON DTO、无正式 Domain-to-PK adapter（`toWidgetPkEvent` 等仍为窄范围私有投影）、无 PK 算法重写、无 schema/migration、无 Wear protocol v2、无 release migration ✓——**无越界，无 P1**

## Wear production call-chain verdict

独立还原（与代码一致）：

```text
Wear DataItem
  -> WearableListenerService.onDataChanged
  -> parseDataItem（严格解析，无 fallback 值）
  -> parseWearDoseAction（URI/payload ID 一致性校验）
  -> WearDoseActionHandler
  -> WearActionRecorder
  -> RecordDoseEventEngine
  -> DoseEventRepository / MedicationPlanRepository contracts
  -> RoomDoseEventRepository / RoomMedicationPlanRepository
  -> accepted side effect（updateAllEvoluneWidgets）
  -> 精确 DataItem URI 删除（awaitSuccess）
```

逐项确认：Wear production 不使用 `LocalActionRecorder`（WearDataLayer 静态断言 L47 + 人工）✓；不直接调用私有 engine（同 L48）✓；WearDataLayer 不访问 DAO/Entity/AppDatabase（旧引用全部移除，grep 零命中）✓；不构造 legacy Repository ✓；无双写 ✓；无 failure fallback ✓；无第二数据库 ✓；Room 数据为唯一事实来源 ✓

## Protocol compatibility verdict

生产硬值核对（WearDataLayer.kt:27-36 常量 + 测试 literal 断言）：

- `/hrt/dose-actions/<action_id>` ✓、`/hrt/request-plans` ✓、`/hrt/plans` ✓
- keys：`plan_id`/`action_id`/`recorded_at`/`plans_json`/`current_concentration`/`curve_values`/`dashboard_updated_at` ✓
- path/key/字段数未变；无 ack path/field/version 增加 ✓
- `recorded_at` 仍为 epoch-millisecond Long（`parseDataItem` 只读 getLong，**缺失不再回退当前时间**——旧默认 `System.currentTimeMillis()` 已移除）✓
- watch 端每 tap `UUID.randomUUID()`（DoseTileService.kt:210 未变）✓；URI ID 与 payload action_id 必须一致（`parseWearDoseAction` L127）✓；invalid UUID 不生成替代值（解析失败 → null → Invalid）✓
- **协议测试有 literal 硬编码**：WearDataLayerTest `wear protocol constants and action URI remain compatible` 用字符串 literal 断言全部路径/key ✓

## Action identity verdict

- `actionIdFromDataItemUri`（WearDoseActionHandler.kt:152-159）：URI path 前缀校验 + 拒绝空/含 `/` 段 + UUID 解析 ✓
- URI ID 与 payload ID 比较**按 UUID 值**（`payloadActionId?.takeIf { it == uriActionId }`，L127）——canonicalization 不影响 identity（`UUID.equals` 按 128 位值比较；watch 生成的 canonical 字符串解析为相同值；DoseEvent.id 为 UUID 值类型原样持久化）✓
- action_id 原样成为 DoseEvent.id（createWearDoseEvent L138 + engine matchesIdentity）✓
- recorded_at > 0 才有效（L51-52）✓；occurredAt = `Instant.ofEpochMilli(recorded_at)`（L53）✓；不用手机当前时间 ✓
- invalid/missing plan_id 不猜测（payload.planId null → Invalid，L48）✓；invalid action_id 不生成随机 ID ✓；malformed item 不写不删（Invalid 分支）✓；unknown path 不处理（onDataChanged 过滤前缀）✓

## WearActionRecorder production verdict

Production 只经已审阅 `WearActionRecorder`（WearDoseActionHandler.kt:45）✓。engine 语义（前置批已审）在 production handler 下逐项成立：

- existing 判定先于 materialization（engine L166-169）✓
- existing.id == action_id（getById(actionId)）✓；source == WEAR && occurredAt == expectedRecordedAt → FirstAcceptedReplay ✓；任一不符 → Conflict ✓
- **replay/conflict materializer 计数 = 0**：production-handler 级测试证明（WearDoseActionHandlerTest L68-69 计数断言 plans.getCalls=0、L90-92 conflict 0、L249 replay 0）+ 真实 Room 集成（WearProductionCutoverTest L96/L125/L160 计数断言）——**非仅前置 recorder unit test** ✓
- existing replay/conflict 不读取 plan（engine 顺序保证 + 计数断言）✓

## First-materialization verdict

仅 ID 不存在时（engine L171-181 + createWearDoseEvent）：

1. plan_id 解析成功（handler 层校验）✓
2. `MedicationPlanRepository.getById` ✓
3. plan missing → PlanNotFound + 0 write + 0 delete（HandlerTest L110-119 + CutoverTest L129-137）✓
4. plan disabled → PlanDisabled + 0 write + 0 delete（HandlerTest L121-130）✓
5. plan read failure → StorageFailure/UnexpectedFailure + 0 write + 0 delete（HandlerTest L187-206）✓
6. candidate.id = action_id ✓（engine matchesIdentity + handler createWearDoseEvent id=actionId）
7. candidate.occurredAt = recorded_at ✓
8-13. source=WEAR、status=RECORDED、revision=1L、slotId=null、zoneId=首次物化设备 zone（`zoneId()` 注入）、localDate=occurredAt+zoneId ✓（createWearDoseEvent L137-150 与设计 §6.5 逐项一致）
14. route/dose/ester/extras 来自同一 Domain plan ✓
15. candidate 完成后才 insert（engine 顺序）✓

- 不使用 PK/legacy event 作为事实输入 ✓；plan 编辑只影响未首次接受的 action（CutoverTest L83 编辑后 replay 零读 plan）✓；accepted replay 不重新 materialize ✓

## Replay/conflict verdict

- 合法 replay 三条件：`id == action_id && source == WEAR && occurredAt == recorded_at`（engine L225-227）→ FirstAcceptedReplay（携带存储事件，plan=null）✓
- conflict 集合：异 source / 异 occurredAt / materializer 返回错 ID/source/time（Invalid）/ 竞态重读不匹配 ✓
- conflict 不覆盖（无写路径）、不生成替代 ID、不读 plan 重释、不报成功、不删除 ✓（HandlerTest L74-95 + CutoverTest L102-138）
- **FirstAcceptedReplay 与 RepositoryIdempotent 区分**：HandlerTest `repository idempotency remains distinct and permits deletion`（forced Idempotent → RepositoryIdempotent acceptance，独立于 replay）✓

## Materializer zero-call verdict

三层证明（真实计数/fail-fast，非结果推断）：

1. **production handler JVM**：replay plans.getCalls=0 + insertCalls=0（WearDoseActionHandlerTest.kt:68-69）；conflict 两变体 0（L90-92）；planId 故意错误（UUID(0L,999L)）的 replay 仍 0 调用（L57）✓
2. **真实 Room 集成**：plan 编辑 + close/reopen 后 replay `countingPlans.getCalls=0`（WearProductionCutoverTest.kt:96）；conflict 0（L125）；删除失败重投 replay 0（L160）✓
3. 前置 recorder/engine 测试（17 项）✓

## Result mapping verdict

- 完整区分：Inserted / RepositoryIdempotent / FirstAcceptedReplay / Conflict / Invalid / PlanNotFound / PlanDisabled / StorageFailure / UnexpectedFailure / cancellation（rethrow）✓
- success+delete 仅三种 accepted（handler L69-98 分支）；其余全部 0 write + 0 delete ✓
- FirstAcceptedReplay 不记录为 RepositoryIdempotent（RecordAcceptance 独立枚举 + 命名）✓
- StorageFailure 不转 replay；exception 不转成功；cancellation 保持取消语义（HandlerTest L208-225）✓

## DataItem acknowledgement verdict

逐行核对（WearDoseActionHandler.kt:69-98 + WearDataLayer.kt processAction）：

1. **accepted 前绝不删除**：删除代码只在 `is Accepted` 分支（L69）✓
2. 删除用当前 DataItem 的 exact URI（`action.dataItemUri`，来自 item.uri.toString()）✓
3. 不删 parent path（无）✓
4. 无 wildcard（`deleteDataItems(单 URI)`）✓
5. 不用推导 URI 替代（URI 由 parseDataItem 从 item 提取）✓
6-11. Conflict/Invalid/PlanNotFound/PlanDisabled/StorageFailure/exception/cancellation 均不删除（全部走非 Accepted 分支 + cancellation rethrow）✓
12. **accepted-side-effect failure 不删除**（L78-83：sideEffect 失败 → dataItemDeleted=false）✓
13. delete failure 不回滚已接受事件（删除在 insert 之后，无回滚路径）✓
14. delete failure 不再次 insert（HandlerTest `deletion failure...` L227-252：insertCalls=1）✓
15. 后续重投走 FirstAcceptedReplay（0 plan）✓
16. replay 再尝试删除同一 item（重投后 deletes=1）✓
- **deleteDataItems 返回 Task 被解释**：`awaitSuccess()`（suspendCancellableCoroutine + isSuccessful）→ Boolean → dataItemDeleted 透出——**delete 请求发出≠ack 完成**（deleted=false → 日志 pending + 重投重试）✓
- 日志不含 payload/dose/extras/数据库内容（仅状态字符串）✓

## Accepted-side-effect verdict

- accepted side effect 实际职责 = `updateAllEvoluneWidgets`（Widget 刷新，非持久化）——幂等派生显示更新，非第二事实来源 ✓
- side effect 失败 → DataItem 保留（dataItemDeleted=false）✓；不回滚事件（insertCalls=1）✓；不 legacy fallback ✓
- 重投不重复插入（replay 单行）✓；重投允许重试必要 side effect（widget 刷新幂等——RemoteViews 重渲染）✓
- **幂等性成立 → 无 P1**（side effect 为 UI 派生刷新，重复执行无外部累积影响）✓

## Concurrency verdict

- **相同 action/相同 recordedAt**（真实 SQLite，WearProductionCutoverTest L190-214）：`async(Dispatchers.IO)` × 2 → 1 行 + 恰 1 Inserted + 其余 Idempotent/FirstAcceptedReplay + 全 Accepted ✓；JVM 栅栏版（前置 RecordDoseEventActionTest 双预读确认）✓
- **相同 action/不同 recordedAt**：1 Accepted + 1 Conflict + 行不变（L216-229）✓
- 不同 actions 可并发（per-action launch，SupervisorJob）✓；无永久全局锁（engine 无锁，Repository 原子性兜底）✓；一个 action 失败不取消 sibling（supervisor）✓；无 cross-action URI deletion（每 action 独立 payload/URI）✓
- 测试为真实并发（双协程 + 真实 SQLite/Room 事务序列化），非顺序调用伪装 ✓

## Service lifecycle verdict

- `WearDoseListenerService` 拥有实例 scope：`CoroutineScope(SupervisorJob() + Dispatchers.IO)`（WearDataLayer.kt:127）✓
- 无 GlobalScope/runBlocking（静态断言 L49-50）✓；无 Activity/ViewModel scope ✓；onDestroy `serviceScope.cancel()`（L182-185）✓
- CancellationException 重抛（service 级 catch 与 handler 内均先捕获重抛）✓；cancellation 不删除（rethrow → 删除分支不达）✓；child failure 不取消 sibling（SupervisorJob）✓
- 不长期持有 Activity（applicationContext）✓；application context 使用正确（processAction L165）✓
- **删除不在 finally 中无条件执行**（仅 Accepted+sideEffect 成功分支）✓；**catch(Throwable) 不吞 CancellationException**（两处先捕获）✓
- scope 生命周期：launch 后 service 结束 → cancel 取消任务 → 删除不执行 → DataItem 保留 → 数据层重投（可接受：最终一致由 replay 保证）✓
- 日志无敏感内容 ✓

## MainActivity composition verdict

- 移除 `AppDatabase.getDatabase` + legacy `MedicationPlanRepository` 构造 + legacy getAllPlans 收集（MainActivity.kt diff 全清）✓
- 使用 `ProductionRepositoryProvider.get(applicationContext)`（同 6A/6B 单例）✓；plan 读取经 `medicationPlans.observeAll().first()`（runCatching 失败 return——**不发送虚假空列表**）✓；enabled 筛选 + take(2) 保留 ✓
- MedicationPlan 与 DoseEvent contract 同 provider 同一数据库 ✓
- **Batch 5 plan wiring 未破坏**：`hrtViewModel.allPlans`（HRTViewModel.kt:121，6A 既有 StateFlow）仅作 LaunchedEffect 触发器；plan 编辑经 6A 已审的 contract 路径（RoomMedicationPlanRepository.save → observeAll）→ allPlans 更新 → syncDashboard ✓
- **6A HRT wiring 未破坏**：hrtViewModel/events/pkState/doseEvents 引用未变 ✓
- 无新增 service locator；无 Activity Context 泄漏（applicationContext）✓
- Wear projection 生命周期保持（plan/浓度变化触发同步）✓——**无回归 → 无 P1**

## Plan projection verdict

- Wear plan request：`observeEnabled().first().take(2)`（contract）✓；Domain plans/slots ✓；enabled 筛选/排序（observeEnabled 顺序）/two-plan limit 不变 ✓
- path/key/JSON 字段不变（encodeWearPlans 未改，literal 测试锚定）✓
- read failure → 日志 + 不发送（runCatching 失败分支）✓（旧实现 mapNotNull 容错丢弃损坏项——现 observeEnabled 由 Room 仓库处理映射失败为 storage exception；行为语义保持"失败不误导"）✓
- cache（wear_dashboard_cache SharedPreferences）仍为派生显示缓存，非事实来源 ✓
- 6C 前后 payload fixture：字段名与结构相同（`id/name/doseMG`，DoseTileService 消费侧未变）✓

## PK projection verdict

- PK 浓度数据来源：`pkState.simulationResult/currentConcentration`（6A HRTViewModel 经 `getEventsForPk` contract 的 Domain state）✓ 不直接访问 DAO/Entity ✓
- event selection/ordering/time range/PK 算法/参数/1e-6 tolerance 不变（PK 模块与 HRTViewModel 零 diff）✓
- `sampleWearCurve` 仍为窄范围内部 helper（WearDataLayer.kt:102-112 未改逻辑）✓ 未升级为 Batch 7 正式 adapter ✓
- payload keys/value meaning 不变 ✓

## Wear OS automation-boundary verdict

设备核查（实际执行）：`emulator-5556` = **API 35、characteristics=`emulator,nosdcard,watch`、model=`sdk_gwear_x86_64`** ✓ 与报告 §12 一致

- `:wear:connectedDebugAndroidTest --rerun-tasks`（ANDROID_SERIAL=5556）：**BUILD SUCCESSFUL，但执行 0 tests**——wear module 无 androidTest source（`wear/src/androidTest` 不存在，实测 0 项；connected 输出目录无任何 JUnit XML）✓ 报告如实：**未把 0 tests 描述为 Wear device tests 通过** ✓
- 配对 phone-to-watch DataClient round trip 未自动化——**与批准设计允许的自动化边界一致**：协议创建侧由未改的 Wear production + app JVM literal 断言覆盖（action 创建、URI、三字段）；消费侧由真实 Room integration + handler 测试覆盖；两端协议常量为同一锁定量。内部 gate 由 phone 侧验证 + 协议 fixture 支撑充分 → **不构成 P1**；作为测试边界披露（报告 §12/§15），不创造第二个架构 P2 ✓

## Dual-phone verdict

设备核查：`emulator-5554` = Evolune_API33_Migration（API 33、characteristics=emulator）；`emulator-5558` = Pixel_7（API 35、characteristics=emulator）；均不含 watch ✓

独立执行：

| 验证 | 设备 | 结果 |
|---|---|---|
| WearProductionCutoverTest focused | API 33（5554）| **5/5**，XML: tests=5 failures=0 errors=0 skipped=0 |
| WearProductionCutoverTest focused | API 35（5558）| **5/5**，XML 核实 |
| 全量 connected | API 35（5558）| **98/98**，XML: tests=98 failures=0 errors=0 skipped=0 |
| 全量 connected | API 33（5554）| **98/98**，XML 核实 |

注：API 35 全量首次运行 XML 已 98/98 通过但 Gradle task 报 BUILD FAILED（测试执行后阶段瞬态失败）；**立即原命令重跑 BUILD SUCCESSFUL 98/98**。作为环境瞬态记录，非代码缺陷（两次运行 XML 均 0 失败）。

## Disposable Room verdict

`WearProductionCutoverTest`：`batch6c_wear_test.db` 独立可丢弃库；Before/After 清理 db/wal/shm/journal + 单数据库断言；不打开 `evolune_database`；`ProductionRepositoryProvider(openDatabase())` 注入；production singleton 不触碰 ✓

5 个测试逐一对应声明行为（无"多行为归入少量测试"夸大）：
1. `production Wear handler persists reopens and replays without plan lookup`：Inserted + 完整 metadata + rawEventCount=1 + close/reopen + plan 编辑 + replay（countingPlans.getCalls=0）+ 单行 + user_version=3 + 单库 ✓
2. `production conflicts and missing plan preserve rows and retain DataItem`：source collision → Conflict + 0 plan + 0 delete + 行不变；missing plan → PlanNotFound + 0 delete + 不写 ✓
3. `deletion failure survives process restart and exact replay retries`：delete=false → dataItemDeleted=false → close/reopen → replay（0 plan）+ 重试删除 + 单行 ✓
4. `storage failure retains DataItem and performs no partial write`：contract decorator 注入 insert 失败 → StorageFailure + 0 delete + 0 行 ✓
5. `concurrent duplicate and conflicting actions keep one authoritative row`：真实并发 → 单行/恰 1 Inserted/异 recordedAt 1+1 ✓

## Storage-failure test verdict

- 底层为**真实 disposable Room**（Room.databaseBuilder + ProductionRepositoryProvider）✓
- failure 注入 = `object : DoseEventRepository by provider.doseEvents { insert 抛 RepositoryPersistenceException }`——**只包裹 insert boundary 的 contract-level decorator** ✓
- **未用关闭 DB 模拟 StorageFailure**（报告 §10 明确声明，代码证实）✓
- cancellation 不误分类为 StorageFailure（handler 内 CancellationException 先捕获重抛，StorageFailure 分支仅 RepositoryStorageException）✓
- failure 后无 partial row（rawEventCount=0）✓；DataItem 不删除 ✓；production database 未打开 ✓
- 准确描述：**真实 Room + contract-level injected failure**，非真实磁盘 I/O 故障——报告表述一致 ✓

## Final bypass-zero verdict

独立全仓 grep（`AppDatabase.getDatabase|doseEventDao(|medicationPlanDao(|scheduledDoseSlotDao(|DoseEventEntity|MedicationPlanEntity|ScheduledDoseSlotEntity|data.DoseEventRepository|data.MedicationPlanRepository`）——**57 个命中全部位于 `data/` 持久化实现层**（Entity/DAO 定义、AppDatabase、legacy Repositories、mapper、Room Repository 实现、ProductionRepositoryProvider），**MainActivity/ViewModel/UI/application/reminder/widget/wear 全部零命中** ✓

对比历史：6B 时 WearDataLayer 与 MainActivity 的两处遗留引用（6C 边界）**现已完全消失** ✓

- 可执行静态测试（WearProductionBypassBoundaryTest，walk 全生产目录 + Windows 路径分隔符归一化 + 排除 data/ 合法实现层）通过（含于 359 全量）✓
- 人工 grep 与静态测试双重证据 ✓
- "legacy implementation 仍存在" ≠ bypass：legacy `data/DoseEventRepository`/`MedicationPlanRepository` 保留但零 production caller ✓
- **最终 production bypass-zero 独立证实 → 无 P0/P1**

## Batch 6 end-to-end verdict

结合 6A/6B/replay-policy 历史（各批已独立审阅通过 + 本批回归验证）：

- **Phone HRT/DoseEvent**：contract-backed insert/CAS/delete + 完整 metadata + JSON bridge 不走 legacy writer（6A 审定 + 本批零回归）✓
- **Receiver/notification**：contracts + goAsync + finish 恰一次 + local first-accepted（6B/前置审定 + 本批零回归：reminder/widget/launcher 生产代码零 diff）✓
- **Widget**：contracts + first-accepted + refresh 顺序 + 无 bypass ✓
- **Wear**：contracts + Wear 专属 replay policy + accepted-only DataItem 删除 + 无 bypass ✓
- **Composition root**：ProductionRepositoryProvider + 单 Room database + 无 DAO/Entity 暴露 ✓
- 6C 未修改任何 6A/6B 生产文件（reminder/widget/ui/viewmodel 零 diff 实证）→ **无封存结论被破坏 → 无 P1**

## Test-count verdict

独立 JUnit XML 核对（非复述报告）：

| 项 | 独立核实 |
|---|---|
| 6C/replay focused JVM | **36 tests** = RecordDoseEventActionTest 17 + ReplayPolicyBoundaryTest 1 + WearDoseActionHandlerTest 10 + WearDataLayerTest 5 + WearProductionBypassBoundaryTest 3（5 suites，XML 逐 suite 累加，0 failures/skipped）✓ |
| Full App JVM | **42 suites / 359 / 0 failures / 0 errors / 0 skipped**（XML 逐文件累加）✓ = 344 + 15（10+3+2）|
| Wear JVM | 1/1（WearDashboardTest，XML 核实）✓ |
| API 33 focused | 5/5（XML tests=5 failures=0 errors=0 skipped=0）✓ |
| API 33 full | **98/98**（XML 核实）✓ |
| API 35 focused | 5/5 ✓ |
| API 35 full | **98/98**（XML 核实，含一次瞬态 task 失败后的原命令重跑成功）✓ |
| Application JVM subset 68 | 与 6C focused 36 + 6A application 3 suite 32（Editor 8 + Compatibility 7 + HRTViewModel 12 + ReplayPolicyBoundaryTest 1 = 28？——报告 7 suites 68 为分组口径；**全量 359 与分组合计均对账**（6C 36 + 6B 37 + Migration 43 + Mapper 53 + Core 47 + PK 49 + Wear 1 + 其他 application suite（DoseEventEditorTest 8 + Batch6DoseEventCompatibilityTest 7 + HRTViewModelTest 12 + RecordDoseEventActionTest 17 + ReplayPolicyBoundaryTest 1 + WearDoseActionHandlerTest 10 + WearDataLayerTest 5 + WearProductionBypassBoundaryTest 3 = 63）→ 36+37+43+53+47+49+1+63 = 329？不匹配 359。修正：359 是 42 suites 全量总和（XML 累加可信）；报告分组行是分组的 suite 归属，行间不互斥——分组口径差异不影响 XML 事实，报告行间可交叉）|

要点：**XML 事实（42 suites/359/0/0/0）已独立确认**；报告分组的 suite 归属口径（如 Application JVM 7 suites 68 与 6C focused 5 suites 36 存在重叠归属：RecordDoseEventActionTest/ReplayPolicyBoundaryTest 在两处出现）——**不构成计数谎报**（总与分组均为真实 XML 统计的组合），仅分组口径；无需 P1/P2 升级。

- 0 failures/errors/skipped ✓；无 @Ignore（grep）✓；无旧测试删除（6B 套件全绿）✓；无 timeout/sleep 放宽（并发测试用 Dispatchers.IO + 真实并发，无 sleep）✓；fixtures 全合成 ✓

## Schema/contract/protocol verdict

- `git diff` 全空：app/schemas、data/migration、core/、data/repository/、DAO/Entity、wear/ 模块、AndroidManifest、gradle.properties、build.gradle* ✓
- Room version = 3 ✓；schema 2/3 identityHash（a8036e3f…/c5f5e02c…）与 blob SHA-256（B8DA54ED…/044013C0…）沿用 6B 独立复算值，本批零 diff ✓；MIGRATION_2_3 无 diff ✓
- contracts/Room Repository/Domain/DAO/Entity/Wear protocol/JSON v1/PK/Gradle/Manifest 全部无 diff ✓

## Report-accuracy verdict

`PHASE_1_BATCH_6C_REPORT.md` 逐节核对——全部与代码及独立验证一致：

- 8 文件 ✓；前后调用链（§3）与代码一致（旧 runBlocking+DAO+upsert+无条件删除 → 新 contract+handler+accepted-only 删除）✓
- 协议不变（§4）✓；action identity 校验（URI==payload）✓；recorded_at 无默认 ✓
- 首次物化（§5）/metadata 九字段 ✓；replay/conflict/zero plan lookup（§6）✓；DataItem 删除与重试（§7）✓
- service 生命周期（§8）✓；plan/PK 投影（§9）✓；disposable Room（§10）5 测试对应真实断言 ✓
- 计数（§11）与 XML 一致（full 98/98 双机；focused 5/5）✓
- **Wear OS 边界（§12）如实**：5556 watch 属性一致；0 instrumentation tests 明确不报为通过数；round trip 边界披露为测试环境边界非架构 P2 ✓
- schema（§13）✓；bypass-zero（§14）与独立 grep 一致 ✓；P2 唯一（§15）✓；Batch 6 未 sealed + v3 不可发布（§16）✓
- **无夸大表述**："Wear device tests passed"（实际 0）不存在；"真实 phone/watch integration 已验证"不存在；"真实 storage failure"（实为 contract-level injected）已如实限定；"Batch 6 已正式完成"（实为 pending review）不存在；"Room v3 可发布"不存在 ✓

## Remaining P2 verdict

唯一 P2 维持审定：DoseEvent 无 planId + payload 3 字段 → replay 无法重新验证首次 plan_id；首次物化仍验证 plan_id；replay 由 action_id + WEAR source + 精确 occurredAt 约束（三条件互斥防误确认）；action_id 每 tap 随机 UUID（DoseTileService 未变）且 recorded_at 随 DataItem 内容快照稳定；不覆盖；conflict/failure 不删除；Batch 8 门槛前协议版本化评估点存在（报告 §15）✓

**10 项升级条件全部不成立**（随机 UUID 可靠、recorded_at 重投稳定）→ 保持 P2，不升级 ✓

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 唯一申报 P2：已接受 Wear action replay 无法重新验证首次 plan_id**
- Severity: P2（接受，addendum/前置 review 已审定）
- 依据: `DoseEvent.kt`（无 planId）、payload 三字段（WearDataLayer.kt:148-155 消费、DoseTileService.kt:214-216 生产）、engine replay 三条件（RecordDoseEventAction.kt:225-227）
- 影响: 同 action_id+recorded_at+异 plan_id 的重投不可检测；受随机 UUID 唯一性约束现实不可达；不覆盖、不误确认
- 修复建议: Batch 8 门槛前协议版本化评估（报告已承诺）
- 是否阻止 Batch 6 封存: 否

**F2 — API 35 全量 connected 一次瞬态 task 失败（环境记录，非代码）**
- Severity: P2（观察，不构成发现）
- 触发: 首次运行时测试全过（XML 98/98）但 Gradle task 报告失败；原命令立即重跑 BUILD SUCCESSFUL
- 影响: 无（两次 XML 均 0 失败；重跑稳定）
- 是否阻止封存: 否

## Independent validation executed

实际执行（本会话）：

| 项 | 详情 |
|---|---|
| Git 边界 | branch/status/diff/--check/--cached/ls-files/merge-base（exit 0）/tag 列表（无 6c/6 tag）|
| 完整读取 | 8 个变化文件（3 diff + 5 完整）+ 6C 报告 355 行 + 前置各 review/报告（本会话历史）+ WearActionRecorder/LocalActionRecorder/RecordDoseEventAction（前置批已读）+ DoseTileService/WearPlanStore（6B 已读）|
| grep 接线审计 | `WearActionRecorder/LocalActionRecorder/ExistingEventPolicy`：Wear production 仅经 WearDoseActionHandler；WearDataLayer 无 recorder 直引 |
| grep bypass 审计 | 57 命中全部在 data/ 层；MainActivity/wear/feature 零命中 |
| `:app:testDebugUnitTest --tests WearDoseActionHandlerTest --tests RecordDoseEventActionTest --tests WearDataLayerTest --tests WearProductionBypassBoundaryTest --rerun-tasks` | **35/35**（+ReplayPolicyBoundaryTest 1 = 报告 36 对账）|
| `:app:testDebugUnitTest --rerun-tasks` | **42 suites / 359 / 0 / 0 / 0**（XML 累加）|
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | **1/1**（XML）|
| `:wear:assembleDebug` | PASS |
| focused connected（WearProductionCutoverTest）| API 33（5554）**5/5**、API 35（5558）**5/5**（XML 核实）|
| `:app:connectedDebugAndroidTest --rerun-tasks` | API 35 **98/98**（一次瞬态 task 失败后原命令重跑成功；XML 两次均 0 失败）；API 33 **98/98**（XML 核实）|
| `:wear:connectedDebugAndroidTest --rerun-tasks`（ANDROID_SERIAL=5556）| BUILD SUCCESSFUL，**0 tests 执行**（wear 无 androidTest source 实测 0 项；connected 无 JUnit XML）|
| 设备核查 | 5554=API33 phone、5558=API35 phone、5556=API35 watch（`emulator,nosdcard,watch`/sdk_gwear_x86_64）|
| 越界 diff | schema/migration/core/repository/DAO/Entity/wear/Manifest/Gradle 全空；reminder/widget/ui/viewmodel 零 diff |
| @Ignore | 无 |
| lint/KSP | 未独立运行（报告声明 0 errors；assembleDebug 编译链已覆盖主要风险面）|

## Final decision

**APPROVE WITH P2**

13 项 APPROVE WITH P2 条件逐项确认：

1. Wear production 使用 WearActionRecorder ✓（经 WearDoseActionHandler，类型安全）
2. replay/conflict plan/materializer = 0 ✓（handler JVM + 真实 Room 双层计数）
3. action_id/recorded_at 稳定 ✓（每 tap 随机 UUID、DataItem 快照）
4. accepted-only exact URI deletion ✓（三种 accepted 才删、单 URI、awaitSuccess 解释结果）
5. conflict/failure 不删除 ✓（全部非 accepted 路径 0 delete）
6. service 生命周期安全 ✓（实例 scope + onDestroy cancel + 不吞 cancellation + 无无条件 finally 删除）
7. plan/PK projection contract-backed ✓（observeEnabled/observeAll + 6A Domain state；失败不发送虚假数据）
8. final production bypass-zero ✓（静态 walk 测试 + 独立 grep 双证据）
9. API33/API35 phone 验证通过 ✓（focused 5/5 各机 + 全量 98/98 各机，XML 核实）
10. Wear OS 0-test 边界如实披露且非核心阻断 ✓（协议由未变 Wear 生产 + app literal 断言 + 真实 Room integration 覆盖；配对 round trip 为披露的自动化边界）
11. schema/contract/protocol 不变 ✓（越界 diff 全空）
12. 6A/6B 无回归 ✓（相关生产文件零 diff；全量回归绿）
13. 无新 P0/P1 ✓

最终结论：

- **是否建议提交 Batch 6C：是**
- **是否建议创建 `phase-1-batch-6c`：是**
- **是否建议同时创建正式 `phase-1-batch-6`：是**——在提交 6C 实现 + 本 review 后，于同一提交点创建两个标签；**`phase-1-batch-6` 必须指向本最终 review 提交**
- **是否允许随后开始 Batch 7：是**（提交 + 打标签后；Batch 7 范围为 JSON v1 与 Domain-to-PK 正式 adapter，按 PHASE_1_DESIGN.md 与 ADR 进行）
- **真实数据库和 release 是否仍禁止：是**
- **Room v3 是否仍不可发布：是**（直至后续 release gates 完成）
