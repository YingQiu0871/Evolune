# Evolune Phase 1 Batch 5A-0 MedicationPlan Draft Contract 审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级架构审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch5a0-draft-contract`（HEAD: `0d685d1`，前置 tag `phase-1-batch-5-design-v1`）
**方式**: 只读设计审阅；未修改设计/代码/测试；未实现 provider/adapter；未开始 Batch 5A/5B

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 3（5B UI session persistence 细节待实现时落定；文档表述可读性；Route/Ester 过渡依赖——均非阻断）
- **是否允许提交设计**: 是
- **是否允许进入 Batch 5A-1/5A-2**: 是（设计提交并打标签后）
- **最大剩余风险**: 无 P0/P1。停止原因 8 项全部真实、Draft 字段矩阵与真实 UI/Domain 完全对齐、ID/createdAt 所有权明确、slot 生成责任唯一、DraftMappingResult 不掩盖程序错误、测试矩阵全部可公开构造。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch5a0-draft-contract` ✓ |
| 前置 tag | `phase-1-batch-5-design-v1` 为 HEAD 祖先（exit 0）✓ |
| 工作树变化 | 仅 1 个未跟踪文件：`docs/phase-reports/PHASE_1_BATCH_5A0_DRAFT_CONTRACT.md` ✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| Kotlin/测试/schema/migration/Gradle/Manifest 变化 | 无 ✓ |
| 数据库/日志/缓存 | 无 ✓ |

---

## Stop-reason verdict

**8 项停止原因逐项核实（全部真实）**：

| # | 声称 | 实际核实 |
|---|---|---|
| 1 | 无独立 MedicationPlanDraft | 全仓 grep `MedicationPlanDraft`/`DraftMappingResult` 无命中；`application/` 包不存在 ✓ |
| 2 | Compose 局部编辑状态 | `MedicationPlanBottomSheet.kt:50-97` 十个 `remember(planToEdit, showBottomSheet)` 状态变量 ✓ |
| 3 | UI 保存时直接构造 legacy MedicationPlan | `MedicationPlanBottomSheet.kt:313-327`（构造 + onSave）✓ |
| 4 | ID 保存时生成 | `MedicationPlanBottomSheet.kt:314` `UUID.randomUUID()` ✓ |
| 5 | createdAt 保存时读当前时间 | `MedicationPlanBottomSheet.kt:325` `System.currentTimeMillis()` ✓ |
| 6 | core MedicationPlan 无 revision | `core/model/MedicationPlan.kt:9-29` 无 revision 字段 ✓ |
| 7 | 时间输入 List<LocalTime> | `MedicationPlanBottomSheet.kt:78-80` + `rememberTimePickerState`（L341-345）✓ |
| 8 | 现有结果类型不适合 Draft 边界 | `RepositoryResults.kt:23-34`（storage 语义）、`MappingResult.kt:8-54`（persistence 语义）均非 application draft 语义 ✓ |

停止原因真实，无虚构。

---

## Draft necessity verdict

- 纯 application/UI 边界值：非 Room 模型、非 persistence contract、非 external DTO、非第二 Domain ✓
- 无 Android/Compose/Room/DAO/Entity/Repository/JSON/Reminder/Predictor/Widget/Wear 依赖；不读 Context/时钟/时区/Locale/数据库；不生成随机 UUID；无 revision；无临时 UI 控件 ✓
- **与 Domain 字段重复评估**：12 个字段大部分同名，但两个关键差异构成明确边界——(a) `times: List<LocalTime>` 替代 `slots: List<ScheduledDoseSlot>`（编辑器只持有有序本地时间，slot 生成是 adapter 单向职责）；(b) 显式携带会话级创建元数据（id/createdAt）。Draft 是**编辑快照**，Domain 是**持久化事实**，非第二事实来源。**不构成 P1** ✓

---

## Draft field matrix verdict

**12 字段逐项核对**（来源/新建规则/编辑规则/Domain 对应/adapter 验证）：

| 字段 | 来源核实 | 规则核实 |
|---|---|---|
| `id` | 会话或现有 Domain（BottomSheet L314 现为保存时生成）| 会话开始一次；adapter 不生成 ✓ |
| `createdAt` | 会话或现有 Domain（L325 现为保存时生成）| 会话捕获一次；adapter 无时钟 ✓ |
| `name` | name 编辑器（L50-52）| `MissingRequiredField(NAME)` 与 `isValid` gate 的 `name.isNotBlank()`（L116）精确一致 ✓ |
| `route`/`ester` | 选择器（L54-60）| 默认 INJECTION/EV 与现状一致 ✓ |
| `doseMG` | doseMGText（L74-76）| Double 键入；文本解析留 5B ✓ |
| `scheduleType` | scheduleType（L70-72）| core.model.ScheduleType；5B UI 转换 ✓ |
| `times` | timeOfDay（L78-80）| List<LocalTime>；空/顺序/重复/minutes ✓ |
| `daysOfWeek`/`intervalDays` | L82-88 | 保留精确；irrelevant 语义不清除；intervalDays>=1 由 Domain invariant ✓ |
| `isEnabled` | 会话或现有 | 编辑保留（L323 现状 `planToEdit?.isEnabled ?: true`）✓ |
| `extras` | 投影 + 现有 Domain extras | **完整保留 map**；`selectedAntiAndrogen`→`ANTI_ANDROGEN_TYPE`、`sublingualTier`→`SUBLINGUAL_TIER` 为投影非第二事实；未显示键不丢 ✓ |

- **编辑 round trip 不丢字段**：Draft 12 字段 = Domain 13 字段（12 + slots）用 times 替代 slots，无遗漏无多余 ✓
- 未知 extras 保留：Domain→Draft 保留完整 map ✓
- 无字段丢失 → 不构成 P1 ✓

---

## ID ownership verdict

- 新 ID 会话开始生成一次；Draft 显式持有 typed UUID；adapter 不生成（`UUID.randomUUID()` 不在 adapter）；编辑保留原 ID；重组/重复保存不变；新会话可新 ID ✓
- 5B 落点：`MedicationPlansScreen.onAddClick`（L111-114）触发的创建会话入口（现 L314 的 randomUUID 移至此处）——真实可行 ✓
- **生命周期风险评估**：设计 §6 明确"recomposition/redraw/validation/repeated save retain the same ID"，§13 把"move ID generation to create-session start"列为 5B 职责。`rememberSaveable`/ViewModel session 的具体持有者留 5B——但**所有权已锁定**（会话开始一次、一次性入口），符合 P2 定义（"Batch 5B UI session persistence 细节，但前提是所有权明确"）。**不构成 P1** ✓

---

## createdAt ownership verdict

- 会话捕获一次；Draft 显式携带 Instant；adapter 不调用 `Instant.now()`/`Clock.system*()`/`System.currentTimeMillis()`；编辑保留原值（现 L325 移至 5B 会话入口）；round trip 精确保留；测试固定 Instant（§14.1.12）✓
- **Clock 仅作 5B caller 可测试依赖**（§7.2 "production code may obtain it from an injected Clock or pass a caller-produced Instant"），不进 Draft ✓
- ID 与 createdAt 由同一稳定 session owner 持有（§6+§7 均指向同一会话入口）✓
- 精度：Instant（Domain 与 Draft 同型）无 epoch-millis 精度损失 ✓
- **不构成 P1** ✓

---

## Revision verdict

**全链路独立核实无 plan revision**：

| 层 | 核实 |
|---|---|
| legacy `data.MedicationPlan` | L27-39 无 revision ✓ |
| `core.model.MedicationPlan` | L9-29 无 revision ✓ |
| `MedicationPlanEntity` | L15-29 无 revision ✓ |
| `MedicationPlanRepository` contract | L7-29 无 expected-revision 参数 ✓ |
| `RepositoryResults` | L23-34 PlanSaveResult/PlanUpdateResult 无 conflict/revision 成员 ✓ |
| `RoomMedicationPlanRepository` | save（L45-97）无 CAS/revision ✓ |

- Draft 不新增 revision；不改任何类型；"revision 保留"测试移除正确 ✓
- DoseEvent revision（DoseEvent/DoseEventEntity/DAO/CAS）不迁移到计划模型 ✓
- **未找到实际 plan revision 或 CAS contract** → 不构成 P1 ✓

---

## Time-boundary verdict

- 真实 UI 使用 `List<LocalTime>` 且由 Material time picker 生成（L78-80、L341-345 `rememberTimePickerState`、L351 `timeOfDay.mapIndexed`）✓
- adapter 可测：空/单/多/顺序/重复/00:00/23:59/second==0/nano==0/canonical HH:mm/position 连续/UUIDv5 —— 全部可表达 ✓
- 移出 5A：非法时间字符串/offset/timezone/损坏 JSON/非字符串外部值 —— 正确（typed List<LocalTime> 无法表达）✓
- 不加字符串 parser（§9 明确）✓
- **NonMinuteTime 真实可表达**：`LocalTime` 可含非零秒/nano（`LocalTime.of(8,30,1)` 合法构造），Domain `ScheduledDoseSlot` require（L18）与 Draft adapter 校验均真实需要 ✓

---

## Slot responsibility verdict

- contract `save(plan: core.model.MedicationPlan)` 接收完整 aggregate（L19 核实）；`RoomMedicationPlanRepository.save` 验证并持久化（L45-97）；persistence mapper 验证 slot ownership/position/precision/expected ID（`MedicationPlanEntityMapper.kt:138-175` 区间含 InvalidSlotPlan/InvalidSlotPosition/UnexpectedSlotId）—— **验证不生成** ✓
- 职责唯一：Draft adapter 从 `draft.times` 构造完整 `List<ScheduledDoseSlot>`（position==index + `ScheduledDoseSlotId.generate` UUIDv5 v1）；Repository 不重复生成 ✓
- 固定向量 `17d1fd14-9d70-5344-beaa-0b158c9f62f4`（planId 0000...0001 / position 0 / 08:30）与 ADR-014 一致 ✓
- adapter 不排序/不去重/不截断/不修复；空列表与重复时间合法 ✓
- Domain→Draft：按权威顺序读 slots、独立验证 UUIDv5 ID、不静默修复、不访问数据库 ✓
- **无双重生成** → 不构成 P1 ✓

---

## DraftMappingResult verdict

- 独立于 persistence `MappingResult` 与 `RepositoryResults`（§11 明确 + 核实两者语义）✓
- 5 类 issue 全部真实可表达：`MissingRequiredField(NAME)`（对应现有 UI `name.isNotBlank()` gate）；`NonMinuteTime`（真实可表达，见 Time-boundary）；`SlotIdMismatch`（Domain 构造器不校验 slot ID，可公开构造 id 不匹配对象）；`SlotIdGenerationFailure`（`ScheduledDoseSlotId.generate` 可返回 `SlotIdResult.Failure`，真实但罕见——SHA-1 不可用）；`DomainValidationFailure`（Domain 构造 IllegalArgumentException）✓
- issue code 稳定、无异常 message 协议（`SlotIdGenerationFailure` 不暴露 `UuidV5Failure.message`）✓
- 不含完整计划/时间表/dose/健康数据 ✓
- 无 InvalidPlanId/非法字符串/offset/JSON 等不可表达错误（typed UUID/LocalTime 无法表达）✓
- `DomainValidationFailure` 只捕获现有 Domain value construction 的明确 IllegalArgumentException（§11 L242 "may catch IllegalArgumentException only around construction of existing Domain value types"）—— 不吞 Throwable/RuntimeException 全类 ✓
- **不掩盖程序错误** → 不构成 P1 ✓

---

## Provider design verdict

- 唯一 `AppDatabase.getDatabase(applicationContext)` singleton；两个 Room Repository 同实例；契约类型稳定 getter；不暴露 DAO/Entity/AppDatabase；不持 Activity；无 Hilt/Koin；不改 Gradle；不自动接线；不自动读写业务数据（§12.1）✓
- **internal database-injection seam**：可测试 disposable AppDatabase；不创建第二 production builder；不重置 production singleton；feature 代码取不到数据库；非全局可变 service locator（provider 仅包 database 实例，无隐藏可变状态）；可证明两 Repository 同实例（§14.2.2 instrumentation 断言）✓

---

## Provider test-isolation verdict

- §14.2 reopen 测试可行且无语义混淆：同一 provider 生命周期内 getter 稳定；reopen 测试关闭旧 disposable database 后用**新测试 provider** 包装重开的同一测试文件；不操作 production singleton；不打开 `evolune_database`；测试结束删除文件与 sidecar（§14.2.10 + §15）✓
- 不会导致使用真实 production DB → 不构成 P0/P1 ✓

---

## Adapter scope verdict

- 只新增：`MedicationPlanDraft`、`DraftMappingResult`、`DraftIssue`、Draft↔Domain mapper、JVM tests ✓
- 不修改：MainActivity/ViewModel/Compose/contract/Domain/Entity/DAO/Room Repository/schema/migration/Predictor/Reminder/Widget/Wear/JSON ✓
- 包位置 `io.github.yuninggu.evolune.application`（当前不存在，将新建）；不依赖 data implementation package（Draft 只用 core.model + ScheduledDoseSlotId）✓

---

## Batch 5B boundary verdict

- 8 项职责全部推迟 5B：会话 ID、会话 createdAt、Compose text parsing、UI extras 投影、ViewModel/Repository 接线、RepositoryResult 处理、Reminder 副作用、移除 legacy save path ✓
- 5A 不半切换读或写（§13 "Batch 5A does not partially switch reads or writes"；§12.1/12.2 均不接 MainActivity/ViewModel/UI）✓
- 无双事实来源 ✓

---

## Test matrix verdict

**21 项 Draft JVM 全部可执行且非假覆盖**：

- 固定 UUID 硬编码（测试 10）✓
- expected 不全由被测 mapper 生成（固定向量独立）✓
- ID/createdAt/extras/全字段 round trip（测试 11-13、15）✓
- **SlotIdMismatch 可公开构造**：`ScheduledDoseSlot` 是公开 data class，Domain `MedicationPlan` 构造器（L24-27）不校验 slot ID —— 传错误 id 即可构造合法 Domain 触发 Domain→Draft 的 SlotIdMismatch；**无需反射** ✓
- **DomainValidationFailure 用真实 invariant**：`intervalDays = 0` 触发 `require(intervalDays >= 1)`（L24）✓
- Locale/timezone 独立性（测试 20）✓
- 移除项正确：无 revision、无非法字符串/offset/JSON、无 UI 运行时 current-time、predictor/DST parity 归 5B ✓
- 静态检查仅补充（测试 21），非替代运行时测试 ✓

**10 项 Provider instrumentation 全部可执行**：disposable AppDatabase 注入、同实例断言、稳定 getter、合成 plan/event 保存读取、file-backed close/reopen、无第二 DB 文件、schema version 3、无 destructive、删除清理 ✓

---

## Batch 5 design consistency verdict

与 `PHASE_1_BATCH_5_DESIGN.md` 对照（§6.3 已授权"small pure plan-editor/slot builder"）：

- 5A-0 只消除实施歧义（Draft 类型、ID/createdAt 所有权、revision 结论、slot 责任、结果类型、测试矩阵）✓
- 未改变 Batch 5 目标；未改变 Batch 5-8 顺序；未修改 Slot ID v1（固定向量不变）；未修改 Repository contract（save 仍接收完整 aggregate）；未修改 Room schema；Draft 非第二事实来源；未提前实施 5B（8 项职责明确推迟）；未推翻垂直切换策略（B 方案）✓
- **无真实冲突** → 不构成 P1 ✓

---

## Data and release safety verdict

- 仅合成 fixture、disposable emulator DB（§14.2）；不接真实数据库；不安装内部 v3 构建覆盖真实安装；不创建 release；Room v3 仍不可发布；5A 完成不代表 Batch 5 完成（§16）✓

---

## Findings

### F1 (P2) — 5B UI session persistence 持有者未在本契约锁定

- **严重程度**: P2
- **文件**: `PHASE_1_BATCH_5A0_DRAFT_CONTRACT.md:131,146`（ID/createdAt 会话规则）、§13
- **问题**: 契约锁定"创建会话开始一次"的所有权，但 `rememberSaveable` vs ViewModel session state 的具体持有者留 5B 实现。
- **触发条件**: Batch 5B 实现时选择错误的持有者（如 remember 而非 rememberSaveable）可能在 configuration change 后重新生成 ID。
- **影响**: 仅影响 5B 实现细节；所有权（一次性会话入口）已锁定，风险受控。
- **依据**: P2 定义明确允许（"Batch 5B UI session persistence 细节，但前提是所有权明确"——所有权已明确）。
- **最小修订建议**: 不修改本契约；5B 设计/实现时在 `MedicationPlansScreen` 会话入口选择 rememberSaveable 或 ViewModel 持有，并加 configuration-change 测试。
- **是否阻止实现**: 否

### F2 (P2) — 文档可读性（现为单个文件，未来可拆）

- **严重程度**: P2
- **文件**: `PHASE_1_BATCH_5A0_DRAFT_CONTRACT.md`（全文 359 行）
- **问题**: 契约同时承担停止原因/当前状态/Draft 决策/实施拆分/测试矩阵/安全声明，内容密度高。
- **影响**: 无实现影响。
- **最小修订建议**: 不修改；如需可在 5B 设计文档中引用而非复制。
- **是否阻止实现**: 否

### F3 (P2) — `core.model` 依赖 `pk.Route`/`pk.Ester`（已知过渡依赖）

- **严重程度**: P2
- **文件**: `core/model/MedicationPlan.kt:3-4`（imports）
- **问题**: 文档 §4 明确 Draft 复用过渡 PK 枚举，与 3B/3C/5 设计一致。
- **影响**: 无。
- **最小修订建议**: 留待独立 ADR。
- **是否阻止实现**: 否

**其余无问题（None）。** 未发现：Draft/provider 第二事实来源、provider 测试打开真实库、adapter 与 Repository 双重生成 slot、要求 schema/contract 变化、Draft 字段遗漏丢数据、ID/createdAt 生命周期未锁定、provider isolation 不可实现、DraftMappingResult 掩盖程序错误、测试矩阵含不可构造场景、与 Batch 5 设计冲突、5A 半接线等 P0/P1 情形。

---

## Independent validation performed

本轮为设计审阅，**未运行任何测试**（无实施代码）。实际执行：

- Git 边界：分支/祖先/暂存区/单文件变化 ✓
- 契约文档全文 read（359 行）✓
- 20+ 真实代码引用点 grep/read 核实（BottomSheet 状态 L50-97/保存 L313-327/时间编辑 L245-259,341-353/删除 L271-275/isValid gate L115-122；legacy MedicationPlan L27-39；Domain L9-29 不变量 L24-27；ScheduledDoseSlot L16-20；contract L7-29；ViewModel L48-88；Screen L107-152；Entity L15-29；RepositoryResults L23-34；mapper L138-175；MappingResult L8-54；RoomMedicationPlanRepository L45-97）全部精确 ✓
- 全仓 grep：无现成 Draft 类型；`application/` 包不存在 ✓
- 静态核查：无 revision、无第二 builder、无随机 UUID 进入 adapter 路径 ✓

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。停止原因真实、字段矩阵与真实代码对齐、ID/createdAt 所有权明确、slot 责任唯一、结果类型不掩盖错误、测试矩阵全部可公开构造。

**提交前必须修改的内容**：无。

**可推迟事项**：
- F1：5B 实现时落定 session 持有者（rememberSaveable/ViewModel）+ configuration-change 测试
- F2：文档拆分（可选）
- F3：Route/Ester 枚举迁移（独立 ADR）

**是否建议提交 5A-0 contract**：是。提交建议信息：`docs: define batch 5a-0 medication plan draft contract`，打标签 `phase-1-batch-5a0-draft-contract-v1`。

**是否建议随后实施 Batch 5A-1/5A-2**：是，但仅在契约提交并打标签之后。5A-1 实现 provider（唯一 singleton + injection seam + 契约 getter + instrumentation）；5A-2 实现 Draft/DraftMappingResult/DraftIssue/mapper + 21 项 JVM 测试；两者可一个提交交付但文件与测试分离；不接任何生产调用方。

**是否继续禁止真实数据库和 release**：**是**。Room v3 仍处 ADR-016 内部不可发布区间；Batch 5A 完成不代表 Batch 5 完成；真实数据库演练需 Batch 5-8 全部证据 + 所有者授权。

---

*审阅结束。最终工作树：仅原契约文档 + 本审阅报告；未修改任何其他文件。*
