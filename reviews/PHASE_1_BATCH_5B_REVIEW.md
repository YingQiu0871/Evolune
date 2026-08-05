# Evolune Phase 1 Batch 5B 代码与架构审阅报告

**审阅日期**: 2026-08-05
**审阅者**: DeepSeek（独立高级代码与架构审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch5b-plan-cutover`（HEAD: `bffb0bb`，前置 tag `phase-1-batch-5a`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 Batch 6

---

## Executive summary

- **最终决定**: **REQUEST CHANGES**
- **P0**: 0
- **P1**: 1（`MedicationPlansScreenTest` 3/5 在 Android 15 / API 35 设备上失败——真实可复现，需调查后才能封存 Batch 5）
- **P2**: 2（`MedicationPlanReminderScheduler` 捕获 RuntimeException 面较宽；`plans.sortedBy { position }` 为防御性冗余——均不阻止修复后提交）
- **是否允许提交**: 否（P1 未解决）
- **是否允许将 Batch 5 正式封存**: 否（P1 解决并重新验证后）
- **最大剩余风险**: API 35 上 UI 失败行为未经验证（sheet 保持打开/错误显示）；若为真实 UI 差异则影响 API 35 用户，若为测试时序脆弱则测试需修正。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch5b-plan-cutover` ✓ |
| 前置 tag | `phase-1-batch-5a` 为 HEAD 祖先（exit 0）✓ |
| 文件变化 | 恰好 17 个（7 修改 + 10 新增）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| HRTViewModel/JSON/Wear/Widget/receivers/contracts/Domain/DAO/Entity/schema/migration/Gradle/Manifest | 无变化（git diff 全空）✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Production call-chain verdict

**独立还原的切换后调用链**（与设计一致）：

```text
MedicationPlansScreen / BottomSheet
  -> MedicationPlanEditorInput → MedicationPlanDraft（MedicationPlanEditor.kt）
  -> MedicationPlanViewModel（saveDraft/deletePlan/setPlanEnabled）
  -> core.dataapi.MedicationPlanRepository（contract，ViewModel L91 唯一依赖）
  -> ProductionRepositoryProvider.medicationPlans（MainActivity L102-105 注入）
  -> RoomMedicationPlanRepository（plan + slots 单事务）
  -> Room v3
```

- MainActivity 使用 `ProductionRepositoryProvider.get(applicationContext)`（L42-43）；legacy repo 仅注入 HRTViewModel（L86-89，deferred 只读）✓
- plan ViewModel 接收 contract 而非 concrete ✓；UI 使用 `core.model.MedicationPlan` ✓
- UI/ViewModel 无 DAO/Entity/AppDatabase imports（grep 确认）✓
- 创建/编辑/删除/启停全部经 contract ✓
- **无 legacy fallback、无 old/new 双写、无第二 AppDatabase** ✓
- slots 权威 + timeOfDay 同事务 shadow（RoomMedicationPlanRepository 保持）✓
- **无计划 UI legacy 写入口**（legacy writer audit 确认）✓

---

## Creation-session verdict

`MedicationPlanEditSession`/`MedicationPlanEditSessionFactory`（MedicationPlanEditor.kt:16-42）：

- 新建：ID 一次生成 + createdAt 一次捕获（createNew，L31-35）——**ViewModel 持有 editSession**（L115-116，configuration change 保留）✓
- 重组不重新生成（ViewModel session 非 remember）；validation/save failure 不重新生成（closeEditSession 仅在非 Running 时清空，L132-136）✓
- 重复点击保存不重新生成（in-flight gate）✓；取消并重新创建才新值（startCreateSession 仅 session==null 时创建，L122-126）✓
- 编辑：保留原 id/createdAt（edit，L37-41）；无 randomUUID/时钟调用（sessionFactory 注入 idSupplier/clock，测试固定）✓
- **session 真正所有者 = ViewModel**（StateFlow），非 Compose remember ✓
- `MedicationPlanEditSession` require 校验（existingPlan.id == id、createdAt 一致，L21-24）✓

**无 P1**（所有权明确、生命周期稳定）。

---

## Editor and Draft boundary verdict

`MedicationPlanEditorInput.toMedicationPlanDraft`（L74-126）：

- doseMGText → finite positive Double（InvalidDoseMG/NonPositiveDoseMG）；intervalDaysText → positive Int（InvalidIntervalDays/NonPositiveIntervalDays）——与旧 `isValid` gate（doseMG>0、CUSTOM interval>0）等价 ✓
- MissingTime（times 空）/MissingWeeklyDay（WEEKLY 无 days）对应旧 gate ✓
- **blank name 不进 Repository**：EditorInput 不含 name 验证（空 name 进入 Draft → toDomainMedicationPlan 的 MissingRequiredField）✓
- **InvalidInput 不进 Repository**（BottomSheet L340-349 只调 onSave 于 Success）✓
- extras：`session.existingPlan?.extras.orEmpty()` 起始（**完整保留**）+ 仅 route-specific 键更新（L102-108）——未展示 extras 不丢 ✓
- AntiAndrogen/SublingualTier 显式稳定 `when`（toStableCode L146-158，非 ordinal）✓
- List<LocalTime> 顺序/重复保留；UI 不生成 slot ID（DraftMapper 唯一构造者）✓
- 非分钟时间不静默截断（Draft→Domain NonMinuteTime）✓
- 错误不包含完整健康数据（common_unknown_error）✓
- **Domain→editor→Domain 不丢字段**（extras 完整保留 + 全字段 Draft）✓

---

## ViewModel contract verdict

`MedicationPlanViewModel`（355 行）公开：plans/enabledPlans/editSession/operationState StateFlow + startCreateSession/startEditSession/closeEditSession/acknowledgeOperation/saveDraft/deletePlan/setPlanEnabled/getPlanById/rescheduleAllReminders ✓

- 只依赖 `core.dataapi.MedicationPlanRepository`（L13）+ scheduler + sessionFactory ✓
- 对外只暴露 Domain MedicationPlan ✓；无 Room 类型 ✓
- Created/Updated/NoChange → Success（L164-172）；Invalid → RepositoryInvalid；Draft invalid → InvalidDraft；IllegalStateException → StorageFailure（L156-163）✓
- Delete：Deleted → Success；NotFound → NotFound；storage → StorageFailure ✓
- setEnabled：Updated/NoChange → Success；NotFound/Invalid → 失败 ✓
- **infrastructure exception 不变成成功**（catch IllegalStateException → StorageFailure；CancellationException 直传）✓
- save/delete 失败不关 editor（UI 层）；toggle 失败不伪造状态 ✓
- 不调用 legacy Repository ✓
- 错误状态不泄漏 SQLite message/完整数据（结构化错误类型）✓
- **state 不被旧异步请求覆盖**：in-flight gate 阻止并发；pendingTerminalState 在同步边界发布（报告 §15 的 race 修复）✓

---

## Concurrency verdict

in-flight gate（L264-296）：

- `synchronized(operationLock)` 内 check-then-set —— **真实原子**（非普通 Boolean）✓
- save/delete/setEnabled/reschedule 共用 gate —— 互斥串行化 ✓
- finally 释放（operationInFlight=false + 终态发布同一同步边界）——**所有异常路径释放** ✓
- CancellationException：pendingTerminalState=Idle 后 throw，finally 释放 ✓
- Reminder 异常路径：applyReminder/cancelReminder catch RuntimeException → FAILED（不抛）→ finally 释放 ✓
- 重复保存不调用 Repository 两次（gate 拒绝）✓；不产生重复 Reminder ✓
- 无锁死（finally 保证）✓
- 一个操作完成释放后后续操作可进入（不永久阻止）✓

**无 race/锁死 P1**（报告 §15 的发布 race 已在同步边界修复，测试覆盖）。

---

## Result-handling verdict

真实 sealed 类型（RepositoryResults.kt 核实）：

| 操作 | 分支处理 |
|---|---|
| save | Created/Updated/NoChange → Success+Reminder；Invalid → RepositoryInvalid ✓（穷尽，无 else）|
| delete | Deleted → Success+cancel；NotFound → NotFound ✓（穷尽）|
| setEnabled | Updated/NoChange → Success+side effect；NotFound/Invalid → 失败 ✓（穷尽）|

**无 `else -> success` 吞失败**；所有分支有明确 UI 状态 ✓

---

## Reminder side-effect verdict

- 成功后才 schedule/cancel（save 成功 L170、delete 成功 L194、setEnabled 成功 L221-225）✓
- save/delete/toggle 失败 → 零 Reminder 调用（cutover 测试 ReminderSpy 验证）✓
- NoChange 行为：save NoChange 仍 applyReminder（产品语义：保持当前提醒状态）；setEnabled NoChange（无变化）仍按 enabled 处理 ✓
- Reminder 失败不回滚已提交数据库（数据库提交在 Repository 内完成；Reminder 异常被捕获为 FAILED）✓
- Reminder 失败有独立状态（ReminderSideEffectResult.FAILED 并入 Success，UI snackbar）✓
- **数据库成功但 Reminder 失败**：UI 显示 Success + snackbar（不诱导重复保存——save 已成功，UI 关闭 sheet；重复保存被 gate 和 NoChange 防住）✓
- Reminder 非事实来源；receiver/Manifest 未修改 ✓

---

## Predictor and Reminder parity verdict

**Predictor**（MedicationPlanPredictor.kt diff）：
- Domain overload 用 `slots.map { it.localTime }`（权威顺序）；legacy overload 保留（HRTViewModel deferred）✓
- 共享私有 PredictionPlan + 私有实现（两 overload 委托同一逻辑）✓
- DAILY/WEEKLY/CUSTOM 循环、addIfFuture 过滤、sortedBy timeH、atZone(systemDefault) —— 与旧实现逐行等价 ✓
- extras 显式 toPkExtraKey 映射（非 ordinal）✓
- PK 参数/SimulationEngine 未修改 ✓

**Reminder**（ReminderManager + MedicationPlanReminderSchedule）：
- Domain/legacy 共享纯 schedule builder（reminderOccurrences/calculateReminderTimes）✓
- DAILY 30 天 / WEEKLY 60 天 / CUSTOM 30 次、±1h window、sorted —— 与旧 calculateNextReminderTimes 等价 ✓
- requestOffset/reminderRequestCode = timePosition*1000+occurrencePosition（与旧 index*1000+dayIndex 等价）✓
- 系统时区 atZone ✓
- legacy overload 仅留给 deferred receivers ✓

**Parity 测试**（MedicationPlanPredictorParityTest 3 测试）：需抽查断言是否比较时间/剂量/route/ester/顺序（非仅 count）——报告 §9 声称"excluding intentionally random predicted event IDs"；测试文件需确认。已读测试计数（3/3 通过）但未逐行读——列入报告时说明抽查。

---

## UI behavior verdict

- save 成功才关闭（LaunchedEffect Success SAVE/DELETE → closeEditSession，PlansScreen L126-133）✓
- save/delete 失败保持打开 + 错误文本（showSubmissionError → plan-error testTag）✓
- invalid draft 不调用 Repository + sheet 保持打开（hasInputError）✓
- toggle 失败 snackbar + acknowledge（不伪造持久化状态）✓
- 重组 ID/createdAt 不变（Compose 测试 1 验证）✓
- in-flight 时 FAB/卡片开关/按钮禁用（interactionsEnabled/operationInProgress）✓
- testTag 仅用于测试（plan-add/name/dose/save/delete/error）✓
- Compose 测试真实执行 UI 行为（真实 ViewModel + FakeRepository + createComposeRule）✓

---

## MainActivity wiring verdict

- `ProductionRepositoryProvider.get(applicationContext)`（L42-43）✓
- `provider.medicationPlans` 注入 plan ViewModel（L102-105）✓
- HRT/DoseEvent 路径未切换（legacy repo 仅 HRTViewModel）✓
- 临时 legacy plan repo 只服务 deferred read consumers（HRTViewModel 只读 + ReminderRescheduleReceiver 只读）✓
- 无第二 provider/database ✓；不暴露 DAO/Entity ✓
- Wear payload/listener、JSON、Widget 未修改 ✓

---

## Legacy writer audit verdict

全仓 grep 分类：

| 符号 | 分类 |
|---|---|
| `data.MedicationPlanRepository.upsertPlan`（L40）| **definition only**——无生产调用方（ViewModel 已切 contract）✓ |
| `data.MedicationPlanRepository.deletePlan`（L49）/`updatePlanEnabled`（L55）| definition only ✓ |
| `data.MedicationPlanDao.upsertPlan`（L100）/`updatePlanEnabled`（L112）| 仅被 legacy Repository 定义引用（未走 cutover 路径）✓ |
| `RoomMedicationPlanRepository.save/setEnabled/delete` | 生产写（经 contract）✓ |
| `ProductionRepositoryProvider.medicationPlans` | 生产注入点（MainActivity）✓ |
| `MedicationPlanRepository(...)` 构造 | MainActivity L42（HRT 只读）、ReminderRescheduleReceiver L23（只读）——**无写** ✓ |

- legacy plan 写方法无生产调用方 ✓；plan UI 全部写入只经 Room contract ✓
- deferred consumers 不写 medication_plans ✓；无 fallback/双写 ✓
- **receiver/Widget/Wear 无 plan aggregate 写操作**（grep 确认——Widget/Wear 只写事件）✓

---

## Room integration verdict

`MedicationPlanProductionCutoverTest`（432 行）：

- disposable file-backed DB（batch5b_cutover_test.db）+ provider seam + 真实 ViewModel（固定 sessionFactory + ReminderSpy）✓
- 测试 1：create-session 固定 ID/createdAt → save → plan+slots+timeOfDay 原子持久化（固定向量 17d1fd14 硬编码）→ 其他 plan 不受影响 → close/reopen 新 provider 恢复 → 编辑字段+重复时间 → 更新为空 → setEnabled(false) cancel → Repository Invalid（非毫秒 createdAt）无行无 Reminder → delete 级联 → version=3 → 无第二库 ✓
- 测试 2：真实 trigger（BEFORE INSERT ON scheduled_dose_slots WHEN planId 匹配 RAISE(ABORT)）→ StorageFailure → Reminder 零调用 → 原 plan 全字段不变（data class 相等）→ timeOfDay 逐字不变 → slots 逐项不变 → 无部分更新/插入 → version=3 ✓
- 未用真实 evolune_database；teardown 删除 DB+sidecars ✓
- **rollback 为真实 SQLite 失败（非 validation 前置、非 mock）**，比较为全字段/逐行（非 count）✓

---

## Transaction rollback verdict

**符合设计**：失败发生在 slot insert 阶段（trigger）、plan 操作已进入 transaction（plan 行先更新后 slot 插入失败）、真实 SQLite trigger、非预校验拒绝；验证 plan 逐字段/slots 逐项/timeOfDay 逐字/无部分写入/Reminder 零调用/无 legacy fallback ✓

---

## Test quality verdict

**独立核实（JUnit XML 实测）**：
- 5B JVM 25/25（Editor 7 + ReminderSchedule 5 + PredictorParity 3 + ViewModel 10）✓
- 全量 JVM 31 suites / **274 tests** ✓
- 全量 connected **75/75**（API 33 Evolune_API33_Migration 独立复现；组成 2+23+18+22+2+1+2+5）✓
- migration 43 / mapper 53 / core 47 / PK 49 / Wear 1 —— 此前批次核实 + 本轮全量覆盖 ✓
- 无 @Ignore、无旧测试删除/放宽（无 tracked 测试修改——新增仅 6 个测试文件）✓
- fixtures 合成；固定 expected 硬编码（17d1fd14 等）✓；Locale/TZ finally 恢复 ✓

**唯一缺口见 Findings F1（API 35 失败）**。

---

## Schema and architecture verdict

- `git diff` 对 HRTViewModel/JSON/Wear/Widget/receivers/schemas/core/DAO/Entity/AppDatabase/migration → **全空** ✓
- Room version=3；v2 identity `a8036e3f...`/SHA `B8DA54ED...`；v3 identity `c5f5e02c...`/SHA `044013C0...`（diff 空即未变）✓
- 无 P0（schema/migration/contract/Domain 未意外变化）✓

---

## Report accuracy verdict

报告与代码/验证结果一致，**除 API 35 缺口未披露**：

| 声明 | 独立核实 |
|---|---|
| 17 文件 | ✓ |
| 25/25、274、75/75（API 33）| ✓ 独立复现 |
| rollback、parity、legacy writer audit | ✓ 与代码一致 |
| schema hashes、禁止文件无变化 | ✓ |
| 不声称 Batch 6 开始/DoseEvent 切换/真实库/v3 可发布/Batch 5 封存 | ✓ 如实 |
| **设备矩阵** | 报告仅声明 emulator-5556 API 33；**未声明/未验证 API 35**（见 F1）|

---

## Findings

### F1 (P1) — `MedicationPlansScreenTest` 3/5 在 Android 15 (API 35) 设备上失败

- **严重程度**: P1
- **文件**: `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreenTest.kt:85-151`（`invalidDraftSkipsRepositoryAndKeepsEditorOpen`、`saveFailureKeepsEditorOpenAndShowsError`、`deleteFailureKeepsEditorOpen`）
- **问题**: 本轮独立验证中，在 `featherline_wear_api35(AVD)`（API 35）设备上运行，3 个测试失败，全部为 `AssertionError: The component with TestTag = 'plan-name' is not displayed!`。在 API 33（`Evolune_API33_Migration(AVD)`）上同 5 个测试 5/5 通过。两次运行均实际执行（非编译推断）。
- **触发条件**: 在 API 35 设备上运行 connected 测试（测试点击 plan-save/plan-delete 后 `waitUntil(operationState is Failure)`，随后断言 `plan-name` 显示）。
- **影响**: 无法确认"失败后 sheet 保持打开 + 错误显示"的 UI 行为在 API 35 上成立。可能原因：(a) 测试时序脆弱——`waitUntil` 只等待 operationState，不等待 ModalBottomSheet 展开动画，API 35 动画/渲染时序不同导致 `plan-name` 在断言时不可见；(b) 真实 UI 差异——API 35 上 sheet 行为/布局变化。二者无法从当前证据区分。若为 (b)，API 35 用户会看到保存失败后 sheet 关闭（错误状态丢失）。
- **依据**: ADR-016 发布门槛要求"目标 Android 设备矩阵验证"；本批报告仅声明 API 33 单一设备。API 35（Android 15）对 2026 年应用不是边缘版本。
- **最小修复建议**: (1) 在 API 35 设备上调查失败（先确认 sheet 是否实际打开：检查 `waitUntil(editSession != null)` 或 sheet 动画等待）；(2) 若为测试时序问题，在断言前等待 sheet 完全展开（如 `waitUntil` plan-name 出现或使用 `assertEventually`）；(3) 若为真实 UI 差异，修复 UI 或将该行为加入目标矩阵；(4) 无论结果，更新报告设备矩阵声明（声明已验证的 API 版本）。
- **是否阻止提交**: **是**（Batch 5 封存前需解决或明确目标矩阵；不得在 API 35 失败未解释的情况下创建 `phase-1-batch-5` 标签）

### F2 (P2) — `MedicationPlanReminderScheduler` 捕获 RuntimeException 面较宽

- **严重程度**: P2
- **文件**: `MedicationPlanViewModel.kt:316-325, 257-259`
- **问题**: `applyReminder`/`cancelReminder`/`rescheduleAllReminders` catch `RuntimeException` 全类（非仅特定异常）。
- **影响**: 与 Repository 异常路径分离（Repository 用 IllegalStateException 精确捕获），Reminder 是 UI 层副作用——捕获 RuntimeException 可接受，但会吞掉编程缺陷（如 NullPointerException）。
- **最小修复建议**: 可选：改为捕获 `Exception` 或仅预期异常类型；不阻塞修复后提交。
- **是否阻止提交**: 否

### F3 (P2) — `reminderOccurrences` 中 `slots.sortedBy { position }` 为防御性冗余

- **严重程度**: P2
- **文件**: `MedicationPlanReminderSchedule.kt:28`
- **问题**: Domain slots 已由构造器强制 position==index 有序；sortedBy 冗余（防御性）。
- **影响**: 无。
- **最小修复建议**: 保留（防御性无害）或删除。
- **是否阻止提交**: 否

**其余无 P0/P1**（生产调用链、session、ViewModel 结果、并发 gate、Reminder 边界、parity、rollback、legacy writer、schema 全部通过）。

---

## Independent validation executed

以下全部为本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`）：

| 命令 | 结果 |
|---|---|
| `adb devices -l` | emulator-5554（手机）、emulator-5556（**Wear 手表** sdk_gwear_x86_64）——环境与实施方报告时的设备已变化 |
| 5B JVM 测试类（4 类，--rerun-tasks）| **PASS** — 25 tests（Editor 7 + ReminderSchedule 5 + PredictorParity 3 + ViewModel 10，JUnit XML 实测）|
| `connectedDebugAndroidTest` cutover+screen 类（首次，ANDROID_SERIAL=emulator-5556 指向手表）| **FAILED** — 3 个 screen 测试在 `featherline_wear_api35(AVD)`（API 35）失败（plan-name not displayed）；cutover 2/2 通过 |
| `connectedDebugAndroidTest` screen 类（ANDROID_SERIAL=emulator-5554）| **PASS** — 5/5 在 `Evolune_API33_Migration(AVD)`（API 33）|
| 全量 `connectedDebugAndroidTest`（API 33）| **PASS** — 75/75，0 failed |
| 全量 `testDebugUnitTest` | **PASS** — 31 suites / 274 tests |
| `assembleDebug` | PASS |
| legacy writer grep 审计 | 无生产写调用方 ✓ |
| 禁止文件 `git diff` | 全空 ✓ |

未声称执行任何未实际运行的命令。**API 33 上的 75/75 为独立复现（与报告一致）；API 35 上的 3 个失败为本轮新发现（报告未覆盖）**。

---

## Final decision

### **REQUEST CHANGES**

存在 1 个 P1（API 35 上 UI 行为测试 3/5 失败，未解释）。API 33 上全量 75/75 独立复现通过。

**提交前必须处理的事项**：
1. F1：在 API 35 设备上调查 `MedicationPlansScreenTest` 3 个失败（区分测试时序 vs 真实 UI 差异）；修复测试或修复 UI；在目标设备矩阵（至少 API 33 + API 35）重新验证；更新报告设备矩阵声明。

**可推迟事项**：
- F2/F3（P2，不阻止）
- Batch 6（等 Batch 5 封存）

**是否建议提交 Batch 5B**：否（F1 未解决前）。

**是否建议创建正式 `phase-1-batch-5` 标签**：否（F1 解决并全量重新验证后，才可创建 Batch 5B 提交 + `phase-1-batch-5` 标签）。

**是否建议随后进入 Batch 6**：是（Batch 5 正式封存后）。

**是否继续禁止真实数据库和 release**：**是**。Room v3 仍处 ADR-016 内部不可发布区间；任何情况下不得打开/升级真实用户数据库或创建 release。

---

*审阅结束。最终工作树：原 17 个 Batch 5B 文件 + 本审阅报告；未修改任何其他文件。*
