# Evolune Phase 1 Batch 6A 代码与架构审阅报告

**审阅日期**: 2026-08-05
**审阅者**: DeepSeek（独立高级代码与架构审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch6a-hrt-doseevent-cutover`（前置 tag `phase-1-batch-6-design-v1`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 6B

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0/P1/P2**: **0/0/4**（均为非阻断观察：3 处 3_600_000 常量/ordinal 延续、错误文本变化——无正确性问题）
- **是否允许提交**: 是
- **是否允许创建 `phase-1-batch-6a`**: 是
- **是否允许随后进入 6B**: 是（6A 提交并打标签后）
- **最大剩余风险**: 无 P0/P1。手机 HRT/DoseEvent 路径已完整切换至 contract；receiver/Widget/Wear 绕过明确留给 6B/6C；v3 仍不可发布。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch6a-hrt-doseevent-cutover` ✓ |
| 前置 tag | `phase-1-batch-6-design-v1` 为 HEAD 祖先（exit 0）✓ |
| 文件变化 | 恰好 14 个（6 修改 + 8 新增）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| receiver/Widget/Wear/contract/Domain/DAO/Entity/AppDatabase/schema/migration/Gradle/Manifest | 无变化（git diff 全空）✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Production call-chain verdict

**独立还原的切换后手机端调用链**（与设计一致）：

```text
MedicationRecordsScreen / BottomSheet / HomeScreen
  -> DoseEventEditSession + DoseEventEditorInput（application）
  -> HRTViewModel（saveEvent/quickAddFromPlan/deleteEvent）
  -> core.dataapi.DoseEventRepository（contract）
  -> ProductionRepositoryProvider.doseEvents（MainActivity L84-89 注入）
  -> RoomDoseEventRepository
  -> Room v3
```

- MainActivity 从 provider 获得 doseEvents（L84）✓；legacy DoseEventRepository 构造已移除（L37-40 删除）✓
- HRTViewModel 只依赖 contract（imports 核实：core.dataapi.DoseEventRepository + application bridge，无 legacy）✓
- UI 使用 core.model.DoseEvent（RecordsScreen/RecordItem/HomeScreen imports 核实）✓
- UI/ViewModel 无 DAO/Entity/AppDatabase imports（grep 核实）✓
- 创建/编辑/删除全经 contract ✓；JSON import 经 jsonBridge（contract insert）✓
- 无 old/new 双写、无 fallback、无第二 AppDatabase ✓
- receiver/Widget/Wear 保持原实现（明确 6B/6C）✓
- **无手机端 legacy 写入口** ✓

---

## DoseEvent editor/session verdict

`DoseEventEditor.kt`（185 行）：

- `DoseEventEditSession`（mode/original/editZoneId + expectedRevision=UPDATE 时 original.revision）✓
- `DoseEventEditSessionFactory.createNew()`：occurredAt = clock.millis()（**毫秒精度**，非分钟非 timeH）一次；zoneId 一次；localDate 从 occurredAt.atZone(zoneId) 一次；id 一次；MANUAL/RECORDED/revision=1/slotId=null ✓
- `edit()`：original 完整保留（**含 revision/source/status/zoneId/localDate/slotId/extras**）+ editZoneId ✓
- `createQuickEvent(plan)`：**minute-floor 保留**（Math.floorDiv(clock.millis(), 60000)*60000，L67-68）✓；route/ester/dose/extras 从 plan；MANUAL/RECORDED/rev1/slotId=null ✓
- `toDoseEventCommand`：验证（dose finite/positive 含 PATCH_REMOVE/RATE 例外、**occurredAt 毫秒精度 round-trip 检查**（hasMillisecondPrecision L181-184，不退化）、extras finite）；编辑时间时才更新 occurredAt/zoneId/localDate（未编辑保留原值）；mergedExtras = original + putAll（保留未编辑键）；original.copy 保留 id/source/status/slotId/revision ✓
- **editor 纯 Kotlin**（imports 仅 core.model/pk/java.time）——无 Room/DAO/Repository/legacy 事实来源 ✓

---

## Create metadata verdict

- 手动/quick add → `source=MANUAL`、`status=RECORDED`（真实枚举值）、`revision=1`、`slotId=null`、显式 device zoneId + 匹配 localDate ✓（与 Batch 6 设计 §5.4 一致）
- insert 用 contract；Idempotent 计为成功；Conflict/Invalid 显式失败；exception → StorageFailure；无 legacy fallback ✓
- 真实 sealed result 名称核实（InsertResult.Inserted/Idempotent/Conflict/Invalid）✓
- quick record 与手动编辑器 metadata 差异符合设计（均 MANUAL；quick 用 plan 的 route/ester/dose/extras + minute-floor 时间）✓

---

## CAS update verdict

`HRTViewModel.persistCommand` Update 分支：

- 用完整 Domain event + `expectedRevision`（来自 session.original.revision，非 UI 猜测）✓
- Updated/NoChange → 重读持久化事件发布**真实 revision**（getById + 抛 IllegalStateException 防御）✓
- RevisionConflict → 显式失败不覆盖 ✓；NotFound → 失败不 insert ✓；Invalid → RepositoryInvalid ✓；exception → StorageFailure ✓
- 失败保持 editor 打开（UI 层）✓
- **in-flight gate**：`beginOperation`/`finishOperation` synchronized 原子 check-then-set + finally 释放（覆盖 create/update/delete/import/quick add）；CancellationException → pendingTerminalState=Idle + rethrow + finally 释放；finishOperation 同步边界发布终态 + Channel 发送 UI event（防 race）✓
- 无永久锁死 ✓；旧异步结果不覆盖新状态 ✓

---

## Delete verdict

- `deleteEvent` → contract delete：Deleted → success + `DoseEventUiEvent.Deleted`；NotFound → 显式失败 ✓
- 只有成功才关闭/报告删除成功（uiEvents collector closeEditSession）✓
- 失败不伪造 UI 状态（operationError 显示）✓；无 legacy DAO/destructive fallback ✓；NotFound 不当作 Deleted ✓

---

## HRTViewModel contract verdict

公开状态/方法：events/allPlans/enabledPlans（Domain StateFlow）、doseTimePoints（PK 投影）、pkState、currentTimeH（clock 注入）、editSession、operationState、uiEvents、importResult + startCreateSession/startEditSession/closeEditSession/acknowledgeOperation/saveEvent/quickAddFromPlan/deleteEvent/importFromMahiroJson/exportToMahiroJson/runSimulation ✓

- 事件 Flow 用 Domain ✓；查询用 contract（getEventsForPk/observeEnabled）✓
- create=insert、edit=CAS update、delete=delete、import=contract ✓
- 所有 result 分支完整（insert 4 分支、update 4 分支、delete 2 分支穷尽）✓
- conflict/revision-conflict 有明确 UI 状态（operationError.displayMessage）✓
- StorageFailure 不泄漏 SQLite message（结构化错误 + "记录无法保存" 等通用文案）✓
- error 不含完整事件/dose/extras/健康数据 ✓
- CancellationException 不吞（rethrow）✓；gate 在 cancellation 后释放 ✓
- 无 legacy fallback ✓

---

## JSON v1 bridge verdict

`Batch6MahiroJsonBridge`（internal）：

- MahiroJsonFormat 未修改（直接调用 parseImport/generateExport）✓；JSON 字段/导出排序/UUID 规则不变 ✓
- 每事件 toJsonV1DomainEvent（LegacyTimeAdapter.timeHToInstant + 显式 ExtraKey + zoneId=null/localDate=null/slotId=null + JSON_V1/RECORDED/rev1）→ contract insert ✓
- Inserted/Idempotent/Conflict/Invalid 分别计数（idempotent 计入 accepted）✓
- **JSON import 无 legacy writer**（HRTViewModel 用 jsonBridge）✓
- bridge internal、仅 HRT 使用（grep 核实：仅 application 定义 + HRTViewModel 引用）✓
- Batch 7 可删除（设计 §7）✓；未成为公开 adapter ✓

---

## Domain-to-PK compatibility verdict

`Batch6HrtPkProjection`（internal object）：

- 只服务 HRT PK/Simulation 输入（getEventsForPk 后投影 + doseTimePoints）✓
- 不写数据库 ✓；不复制 PK 算法/参数（用 LegacyTimeAdapter.instantToTimeH）✓
- route/ester/dose/timeH/extras 逐字段 + 显式 ExtraKey 穷尽映射（非 ordinal）✓
- 事件选择/排序/时间范围保留（contract getEventsForPk 双分支 + RECORDED 过滤）✓
- Domain metadata 保留在事实源（投影仅忽略 PK 无法表达字段）✓
- 未扩散（grep：仅 application + HRTViewModel）✓；Batch 7 可替换 ✓
- **未成为公开正式 adapter**（internal）→ 无 P1 ✓

---

## UI behavior verdict

- create success 才关闭（uiEvents Saved → closeEditSession）✓；validation/conflict/storage 保持打开（operationError 显示 + record-error testTag）✓
- edit 保留 metadata（session.original 完整）✓；delete failure 不假关闭 ✓
- duplicate click 不双写（gate + isOperationRunning 禁用按钮）✓
- UI 不直接生成 revision（editor/ViewModel 唯一 owner）✓；UI 无 DAO/Entity 访问 ✓
- testTag（record-save/delete/dose/error）仅服务测试 ✓；无无关视觉样式改变 ✓
- Compose 测试真实执行 UI（真实 ViewModel + 7 个测试：create success close/local validation/conflict/storage/edit metadata/delete failure/duplicate input）✓

---

## MainActivity wiring verdict

- `provider.doseEvents` + `provider.medicationPlans` 注入 HRTViewModel（L84-89）✓
- `provider.medicationPlans` 保持 Batch 5 plan ViewModel 接线 ✓
- legacy DoseEventRepository 构造移除（L37-40）✓
- legacy MedicationPlanRepository 仅保留给 Wear dashboard 同步（L102-104 getAllPlans collectAsState——**只读派生**，非注入 HRT）✓
- 无第二数据库 ✓；不暴露 DAO/Entity ✓；receiver/Widget/Wear 未修改 ✓；Wear payload 未修改 ✓

---

## Legacy writer audit verdict

全仓 grep 分类：

| 引用 | 分类 |
|---|---|
| `data.DoseEventRepository` 定义（L12/46 upsert）| 定义（无手机生产调用方）✓ |
| `DoseEventDao.upsertEvent`（L121）| 定义（被 legacy repo 定义引用）✓ |
| `EvoluneWidgetReceiver` L226-227/248 | **6C 计划内推迟** ✓ |
| `WearDataLayer` L162-163 | **6C 计划内推迟** ✓ |
| `MedicationNotificationActionReceiver` L64-65 | **6B 计划内推迟** ✓ |
| `Batch6MahiroJsonBridge`/`Batch6HrtPkProjection` | internal，仅 application + HRTViewModel ✓ |
| `ProductionRepositoryProvider.doseEvents` | 生产注入点 ✓ |
| `RoomDoseEventRepository` | contract 实现 ✓ |

- 手机 HRT/UI 无 legacy writer ✓；HRTViewModel 无 legacy Repository ✓；JSON import 无 legacy writer ✓；MainActivity 手机路径用 provider contract ✓
- receiver/Widget/Wear 旧绕过仅属 6B/6C ✓；无新增 bypass ✓；无双写/fallback ✓

---

## Room integration verdict

`DoseEventProductionCutoverTest`（2 测试）：disposable file-backed（batch6a_cutover_test.db）+ provider seam + 真实 HRTViewModel entry；未打开 evolune_database ✓

- 覆盖：insert/close-reopen/全 metadata/CAS revision 1→2/stale conflict（**逐字段不变**）/NotFound 不 insert/same-ID-same-content idempotent/same-ID-different-content conflict/JSON bridge 写入/delete 保留无关行/user_version=3/无第二库 ✓
- **真实 SQLite trigger 回滚**：BEFORE UPDATE trigger ABORT → StorageFailure → 原行/v3 metadata/revision/timeH shadow/行数/版本不变 ✓
- teardown 清理 sidecars ✓

---

## Test quality verdict

**独立核实（JUnit XML 实测）**：

- 6A JVM **27/27**（DoseEventEditorTest 8 + Batch6DoseEventCompatibilityTest 7 + HRTViewModelTest 12）✓
- 全量 JVM **34 suites / 301 tests** ✓
- 覆盖：editor identity/occurredAt 毫秒/zoneId-localDate/metadata/revision/edit preservation/insert-idempotent-conflict-storage/CAS success-conflict-notfound/delete/gate（duplicate submit + cancellation release）/JSON bridge/PK order-selection ✓
- 无 @Ignore、无旧测试删除/放宽（无 tracked 测试修改）✓；fixtures 合成 ✓
- Compose 7/7（双手机独立复现）✓

---

## Dual-phone matrix verdict

- 设备确认：5560（API 33, characteristics=emulator 不含 watch）、5558（API 35, characteristics=emulator 不含 watch）✓；5556 为 Wear（排除）✓
- cutover 2/2 + Compose 7/7：API 33 **9/9**、API 35 **9/9** 独立复现 ✓
- **API 35 全量 connected 84/84 独立复现**，JUnit XML 核实 tests=84 failures=0 errors=0 skipped=0 ✓
- Wear AVD 未用于 phone 验收 ✓

---

## Schema and boundary verdict

- `git diff` 对 schemas/migration/core/reminder/widget/wear → **全空** ✓
- Room version=3；schema 2/3 与 MIGRATION_2_3 不变 ✓
- JSON v1 协议未变（MahiroJsonFormat 未修改）✓；PK 参数/算法未变（PK 49/49 此前核实）✓

---

## Report accuracy verdict

报告与代码/验证结果一致：

| 声明 | 独立核实 |
|---|---|
| 14 文件 | ✓ |
| HRTViewModel 切换/insert/CAS/delete/JSON 桥/PK 投影 | ✓ 与代码一致 |
| 27 JVM、301 App JVM、双手机 84/84、cutover 2/2、Compose 7/7 | ✓ 独立复现（API 35 全量 XML 核实）|
| schema/migration/contract/Domain/DAO/Entity 无变化 | ✓ git diff 全空 |
| receiver/Widget/Wear 未切换（6B/6C）| ✓ grep 核实 |
| 无真实数据、Batch 6 未完成、v3 不可发布 | ✓ 如实 |
| 未夸大：不声称 6B/6C 开始、bypass 清零、JSON 正式 adapter、真实库验证、v3 可发布 | ✓ 全部如实 |

---

## Findings

### F1 (P2) — HRTViewModel/RecordItem 复制 3_600_000 常量

- **严重程度**: P2
- **文件**: `viewmodel/HRTViewModel.kt`（companion `MILLIS_PER_HOUR = 3_600_000.0`）、`ui/components/MedicationRecordItem.kt:189`（`occurredAt.toEpochMilli() / 3_600_000.0`）
- **问题**: 设计 §8 要求"不得在 LegacyTimeAdapter 外复制 3_600_000 转换常量"；HRTViewModel 的 currentTimeH 计算与 RecordItem 显示层复制了该值。
- **触发条件**: 未来常量变更时两处漂移。
- **影响**: 无正确性问题（数学与 LegacyTimeAdapter.epochMillisToTimeH 一致；且 currentTimeH 属既有 PK/UI 坐标模型而非 bridge）。可维护性观察。
- **最小修复建议**: 可选：currentTimeH 改用 `LegacyTimeAdapter.epochMillisToTimeH`；RecordItem 显示层直接以毫秒格式化。
- **是否阻止提交**: 否

### F2 (P2) — BottomSheet 的 ordinal extras 投影延续

- **严重程度**: P2
- **文件**: `ui/components/MedicationRecordBottomSheet.kt`（保存处 `sublingualTier.ordinal.toDouble()`、`selectedAntiAndrogen.ordinal.toDouble()`）
- **问题**: UI extras 投影用 ordinal（Batch 5B 计划侧已改为显式 toStableCode；事件侧延续旧行为）。
- **影响**: 无正确性问题（枚举声明顺序与稳定 code 一致）；一致性观察。
- **最小修复建议**: 可选：与 Editor 统一显式映射。
- **是否阻止提交**: 否

### F3 (P2) — 错误文案变化

- **严重程度**: P2
- **文件**: `viewmodel/HRTViewModel.kt`（`"Simulation unavailable"`、`"Import failed"`）
- **问题**: 旧错误文本（含 e.message）替换为通用文案——不泄漏内部消息（改进），但 UI 文案微变。
- **影响**: 无（通用错误展示，非诊断信息丢失——诊断由日志承担）。
- **是否阻止提交**: 否

### F4 (P2) — receiver/Widget/Wear 绕过为计划内 6B/6C 项

- **严重程度**: P2
- **文件**: `EvoluneWidgetReceiver.kt`、`WearDataLayer.kt`、`MedicationNotificationActionReceiver.kt`
- **问题**: 直接 DAO/Entity 引用延续（设计 §4/§10 明确推迟）。
- **影响**: 计划内；无新增 bypass。
- **是否阻止提交**: 否

**其余无 P0/P1**（调用链、metadata、insert/CAS/delete、gate、JSON/PK 桥、双手机矩阵、Room 集成、报告一致性全部通过）。

---

## Independent validation executed

以下全部为本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`）：

| 命令 | 结果 |
|---|---|
| `adb` 设备核实（5560/5558 characteristics）| API 33 + API 35，均不含 watch ✓ |
| 6A JVM 测试类（--rerun-tasks）| **PASS** — 27 tests（8+7+12，JUnit XML 实测）|
| connected cutover+Compose（5560）| **PASS** — 9/9（cutover 2 + Compose 7）|
| connected cutover+Compose（5558）| **PASS** — 9/9 |
| 全量 `testDebugUnitTest` | **PASS** — 34 suites / 301 tests |
| 全量 `connectedDebugAndroidTest`（5558）| **PASS** — 84/84，XML tests=84 failures=0 errors=0 skipped=0 |
| `assembleDebug` | PASS |
| legacy writer 全仓 grep | 手机路径零 legacy 写；推迟项仅 6B/6C 三处 ✓ |
| 禁止文件 `git diff` | 全空 ✓ |

未声称执行任何未实际运行的命令。API 33 全量 84/84 未独立重跑（双手机 9/9 + API 35 全量已复现；报告声明可采信——已与代码一致性核对）。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。4 个 P2 均为非阻断观察（常量复制/ordinal 延续/文案变化/计划内推迟项）。

**提交前必须处理事项**: 无。

**可推迟事项**:
- F1-F3（P2，可选清理）
- F4（6B/6C 计划内）

**是否建议提交 Batch 6A**: 是。提交建议信息：`feat: cut over phone hrt dose event flow to contracts`。

**是否建议创建 `phase-1-batch-6a`**: 是（提交后）。

**是否建议随后进入 6B**: 是（6A 提交并打标签后；6B 按 Batch 6 设计 §10.2 切换 reminder receivers + Widget，含 §6.6 receiver 生命周期合同）。

**是否继续禁止真实数据库和 release**: **是**。Room v3 仍处 ADR-016 内部不可发布区间；Batch 6A 完成不授权 6B/6C 跳过、真实库演练、生产分发或 release；Batch 7（JSON/PK 正式 adapter）仍必须完成。

---

*审阅结束。最终工作树：原 14 个 Batch 6A 文件 + 本审阅报告；未修改任何其他文件。*
