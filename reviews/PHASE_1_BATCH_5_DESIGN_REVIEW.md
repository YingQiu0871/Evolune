# Evolune Phase 1 Batch 5 生产接线设计审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级架构审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/production-wiring-design`（HEAD: `e1817c8`，前置 tag `phase-1-batch-3c`）
**方式**: 只读设计审阅；未修改设计/代码/测试；未实施 wiring；未创建 ADR；未开始 Batch 5A

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 3（历史文档状态漂移记录、Route/Ester 过渡依赖、只读派生消费者未切换——均为已声明且非阻断）
- **是否允许封存设计并开始 Batch 5A**: 是
- **最大剩余风险**: 无 P0/P1。设计准确、范围封闭、术语无歧义；唯一 legacy 计划写入口（UI→ViewModel→legacy repo→DAO）已被识别且 Batch 5 切换后无其他写者；v3 仍不可发布。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/production-wiring-design` ✓ |
| 前置 tag | `phase-1-batch-3c` 为 HEAD 祖先（merge-base 退出码 0）✓ |
| 工作树变化 | 仅 1 个未跟踪文件：`docs/phase-reports/PHASE_1_BATCH_5_DESIGN.md` ✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| Kotlin/schema/migration/Gradle/Manifest 变化 | 无 ✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Authoritative batch and terminology verdict

**权威批次核实（全部精确）**：

| 引用 | 实际 | 结论 |
|---|---|---|
| `PHASE_1_DESIGN.md:843-854` = Batch 5 "双读双写与计划槽位切换" | L843 标题逐字一致；L845-852 涉及文件（Room Repository impls/mappers、MedicationPlan.kt、Predictor、ReminderManager、ViewModel、计划编辑 UI）；L854 验收（双写一致/预测 DST parity/未编辑槽 ID 不变）| ✓ 精确 |
| `PHASE_1_DESIGN.md:856-867` = Batch 6 | L856-867：HRTViewModel、记录 UI、receivers/factory/matcher、Widget、Wear、composition root | ✓ 精确 |
| `PHASE_1_DESIGN.md:869-878` = Batch 7 | L869-878：JSON v1 + PK adapter | ✓ 精确 |
| `PHASE_1_DESIGN.md:880-894` = Batch 8 | L880-894：Phase 1 退出核验，不删除 legacy 列 | ✓ 精确 |
| Batch 5-8 顺序 | 设计文档 §1 引用与实际逐行一致 | ✓ 无冲突 |

- "Batch 5" 编号有权威依据（PHASE_1_DESIGN §20 固定序列）；设计未创造新的永久批次编号（5A/5B 明确为 Batch 5 下的两个可审阅提交，非新批次）✓
- ROADMAP.md（docs/evolune/）不含批次编号（产品级路线图），权威实施序列在 PHASE_1_DESIGN §20——设计以 PHASE_1_DESIGN 为权威正确 ✓
- **历史漂移记录（设计 §1.1）核实为非阻断**：`ARCHITECTURE.md:117` 确为 stale v2 描述（实际 Room v3，`AppDatabase.kt:15-28` 三实体）；`DECISIONS.md:146-160`（ADR-015）与 `PHASE_1_DESIGN.md:800-802`（Batch 3C 标题含"生产接线"）确含 3C 含接线措辞，但已交付的 3C（Repository 实现无运行时调用方）与 Batch 5/6 序列证明为历史范围漂移而非契约/schema 冲突 ✓
- ADR-013 依赖方向（`feature -> core:data-api <- database`，DECISIONS.md:123-131）与 ADR-014（Slot ID v1）不变 ✓

**术语风险**：标题"双读双写"被 §5 L201 明确限定——"一个 Room repository transaction 写 v3 权威 + 保留的 legacy shadow；**不**意味着同时调用新旧两个 repository、写两个数据库、或失败后经 DAO 重试"。§3 逐条拒绝 silent fallback 与双事实来源。术语无实施误导 ✓（非 P1）

---

## Current production flow verdict

**27 个引用点逐项核实（全部精确）**：

| 引用 | 实际核实 |
|---|---|
| `MainActivity.kt:38-41` 构造两个 legacy 仓库 | L40-41 ✓ |
| `MainActivity.kt:82-100` 注入 ViewModel | L83-99 ✓ |
| `MainActivity.kt:101-116` Wear push | L102-116（collectAsState + LaunchedEffect + syncDashboard）✓ |
| `HRTViewModel.kt:6-8,37-40` legacy 依赖 | L6-7 imports、L38-39 构造参数 ✓ |
| `HRTViewModel.kt:122-147` importFromMahiroJson | L131 ✓ |
| `HRTViewModel.kt:179-203` PK selection | L182/186 ✓ |
| `HRTViewModel.kt:220-256` SimulationEngine | L231/246 ✓ |
| `MedicationPlanViewModel.kt:7-22` legacy 依赖 | L8-9 imports、L20-22 构造 ✓ |
| `MedicationRecordsScreen.kt:65-74,87-103` | L66/73/91/101 ✓ |
| `MedicationPlanBottomSheet.kt:78-80,245-259,313-327` | L78-79（时间状态）、L249（LocalTime 编辑）、L313（创建 legacy plan）✓ |
| `MedicationPlansScreen.kt:103-153` | L143（viewModel.upsertPlan）✓ |
| `data/DoseEventRepository.kt:17-39,42-60` | L17/27（读）、L45-46（写）✓ |
| `DoseEventDao.kt:93-133` legacy 方法区 | L97/120-121 ✓ |
| `DoseEventEntity.kt:55-74` fromDoseEvent | L59/63 ✓ |
| `data/MedicationPlanRepository.kt:12-35,37-63` | L15/24（读）、L40-42（upsertPlan）✓ |
| `MedicationPlanDao.kt:78-118` legacy 区 | L82/99-100 ✓ |
| `RoomDoseEventRepository.kt:22-178` | 3C 审阅已读全文，类范围一致 ✓ |
| `RoomMedicationPlanRepository.kt:45-97` save | 3C 审阅已读全文 ✓ |
| `AppNavigation.kt:125-176` | L126-127/134 ✓ |
| `MahiroJsonFormat.kt:90-176` | L90/108-110/149 ✓ |
| `MedicationPlanPredictor.kt:31-117` | L31/107（atZone systemDefault）✓ |
| `MedicationReminderReceiver.kt:38-64` | L39-45 ✓ |
| `MedicationNotificationActionReceiver.kt:53-67` | L54/64 ✓ |
| `ReminderRescheduleReceiver.kt:22-26` | L22-23 ✓ |
| `ReminderManager.kt:20-195` | 数据库无关、接受 legacy plans（此前批次已确认）✓ |
| `EvoluneWidgetReceiver.kt:105-122,211-260` | L112（直读 plans）、L216/226/248（直写 events）✓ |
| `WearDataLayer.kt:41-72,117-176` | L42/62（prefs 缓存）、L123-124/157/162（listener 直读/直写）✓ |
| `AppDatabase.kt:15-28,30-73,38-69` | L16（三实体）、L21（version=3）、L38/61-68（builder + 双 migration）✓ |
| `PHASE_1_DESIGN.md:707-715` 回滚 | L707-715 ✓（L714 修复版本回退）|
| `ARCHITECTURE.md:117` stale | 精确 ✓ |

---

## Bypass and write-entry verdict

**独立扫描（非仅依赖设计文档）**：

- **唯一 legacy plan 写链**：`MedicationPlansScreen.kt:143` → `MedicationPlanViewModel.upsertPlan`（L48-50）→ `data/MedicationPlanRepository.upsertPlan`（L40-42）→ `MedicationPlanDao.upsertPlan`（L100 @Upsert）。**无其他生产调用方**（全仓 grep 确认）✓
- **唯一 Room builder**：`AppDatabase.kt:63`；无 in-memory 生产库（grep 确认）✓
- **JSON 不写计划**：MahiroJsonFormat 仅生成 PK DoseEvent；HRTViewModel.importFromMahiroJson 只 upsert event ✓
- **Widget/Wear/Reminder 对计划只读**：EvoluneWidgetReceiver 直读 plans + 直写 events（不写 plans）；WearDoseListenerService 直读 plans + 直写 events；MedicationReminderReceiver/ReminderRescheduleReceiver 只读 ✓
- **设计 §3 bypass 清单 12 项全部与真实代码一致**；每项标记 Batch 5/6/7 处理或推迟，无遗漏写入口 ✓
- **关键论断成立**：Batch 5 切换计划 UI 后，`RoomMedicationPlanRepository` 成为唯一计划写所有者；不存在其他生产写者造成双事实来源 ✓

---

## Batch 5 scope verdict

- 计划创建/编辑/读取/保存/删除/启停全部在范围（§6.2：save/setEnabled/delete + observe/getById）✓
- 无其他能写 medication_plans 的生产入口（扫描确认）✓
- Predictor 适配为"必要的最小适配"（Domain plan 路径 + parity 测试，保留 legacy 入口给推迟调用方）——与 PHASE_1_DESIGN Batch 5 涉及文件列表一致 ✓
- JSON 在 Batch 5 后不写计划（只写事件，Batch 7 处理）✓
- Widget/Wear 只读派生状态（Batch 6 切换）✓
- DoseEvent 留 Batch 6/7 不阻塞计划切换（legacy 事件路径不变；HRTViewModel legacy 计划读取为同库临时兼容读）✓
- Batch 5 后仍只有 Room 一份事实来源（slots 权威 + timeOfDay 同事务 shadow）✓

---

## Provider verdict

- 要求：复用唯一 AppDatabase singleton、不建第二 builder（§4.1、§8.1、§10.7）✓
- 契约类型暴露、DAO/Entity 隐藏（§4.2、§9.1 static dependency scan）✓
- Repository 生命周期 = application-scoped（§6.2.1）✓
- provider 不持 Activity、无 Context 泄漏（mapper 无 Context 依赖；provider 只收 AppDatabase 实例）✓
- 测试可替换依赖（§8.1 database-injection seam for instrumentation）✓
- 无 Hilt/Koin/Gradle 依赖、无全局可变 service locator（§6.3）✓
- App 启动不升级真实库（§10 数据安全门）✓

---

## Draft/slot adapter verdict

- UI draft → Domain aggregate：纯 mapper（`application/MedicationPlanDraftMapper.kt`），无 Android/Room/UI 依赖（§8.1）✓
- slots 原顺序、重复时间、position==index 连续、UUIDv5 固定（ADR-014）、canonical HH:mm、00:00/23:59、非分钟拒绝——全部由 3C 已验证的 Domain 不变量与 mapper 保证 ✓
- legacy timeOfDay 与 slots 同事务同步（RoomMedicationPlanRepository.save 已验证）✓
- revision/conflict：计划 contract 无 conflict 成员（ADR-015 已锁定），save 原子全替换 ✓
- 禁止 UI：生成随机 slot ID（DraftMapper 强制 UUIDv5 校验，UnexpectedSlotId 拒绝）、写 Entity、访问 DAO、静默修复、忽略失败后回退（§9.2 instrumentation 断言无 legacy fallback）✓

---

## Switching strategy verdict

- A（一次性全切）拒绝：违反 Batch 5-7 序列、丢失事件 revision/source 元数据——理由充分 ✓
- B（按 aggregate 垂直切片）推荐：聚合内一致性强、回滚边界有界、未切换消费者仍读同库 shadow——正确 ✓
- C（双读双写）拒绝：双写所有者、结果分歧、回滚歧义、诱导 silent fallback——正确 ✓
- 垂直切换覆盖所有计划生产入口（UI 写链唯一，扫描确认）而非单页面 ✓
- 保留 legacy timeOfDay 但不保留第二套业务写路径（§4.5、§11 legacy 写不重新启用）✓

---

## 5A/5B split verdict

- 5A：provider + 纯 draft/slot mapper + JVM/provider tests，不改生产行为 ✓
- 5B：计划入口原子切换（ViewModel/UI + Predictor/Reminder 最小适配 + MainActivity wiring + integration instrumentation）✓
- 两阶段之间无半切换风险：§6.4 明确"不得出现 plan 读用新 aggregate 而写仍用 legacy @Upsert 或反之"；5A 不发布、不用真实库 ✓
- 仅 5B 完成并审阅后打 Batch 5 标签；5A 不产生"Batch 5 完成"误标签 ✓

---

## Error handling verdict

- 全部分支有落点：Created/Updated/NoChange = 成功；Invalid/Infrastructure exception = 明确失败（§6.2.6）；NotFound/Invalid 显式（§9.1）✓
- ViewModel 暴露错误；UI 保持现有行为；conflict 不覆盖（DoseEvent contract 已有 Conflict；plan 无 conflict 按 ADR-015 全替换）✓
- persistence failure 不回退（§11）；corrupt aggregate 不自动修复（3C CorruptAggregateException）✓
- 错误信息不泄漏完整健康记录（3C 错误边界）✓
- 保存失败不触发 Reminder 副作用、不关闭编辑器伪装成功（§6.2.7、§9.2）✓
- 重复点击不并发写出不一致（Room withTransaction 串行化 + Repository 事务）✓

---

## Predictor, DST and reminder verdict

- 计划时间继续设备系统时区（§6.2.8 保留现有 DST 规则；Predictor 现有 atZone(systemDefault()) 行为不变）✓
- 不在 UI adapter 重写 DST；复用既有 Domain/Java time 行为（§9.1 parity 测试）✓
- Predictor 输入来自 Domain（新增 Domain plan 路径），legacy 路径仅留给推迟调用方 ✓
- Reminder 只作派生消费者；Batch 5 不建立 Reminder 为事实来源 ✓
- 计划保存成功后才触发派生更新；失败不更新 Reminder ✓
- 派生更新失败不反向造成数据库部分提交（Reminder 副作用在 Repository 事务成功后执行，与 3C 已验证的事务边界一致）✓

---

## Database safety verdict

- 持续禁止：真实库执行、内部 v3 APK 覆盖正式安装、自动升级真实 v2、真实/派生 fixture、release 构建、提交真实库/日志/导出（§10）✓
- 真实数据库演练前的证据清单明确（§10.1：Batch 5-8 证据、全部入口走 contract、JSON/PK adapter、矩阵绿、repair 工具 3.12 验证、所有者授权）✓
- v2/v3 哈希与 identity 保持（§10.4-5）✓

---

## Test plan verdict

- 5A JVM/provider：唯一实例、无第二 builder、draft/slot mapper、顺序/重复/UUID、result/error 分支、DST/预测 parity、无 Room Entity 泄漏 ✓
- 5B instrumentation：真实 disposable Room 库上的保存/重开/回滚（§9.2），非仅 mock ✓
- 完整回归矩阵：23 repository、66 connected、migration matrix 43、43 primitives、53 mapper、47 core、231+ App、49 PK（1e-6）、Wear、builds、lint、KSP/hashes（§9.3，计数取自 JUnit XML）✓

---

## Expected file scope verdict

- 全部文件真实存在（MainActivity/MedicationPlanViewModel/PlansScreen/BottomSheet/Card/Predictor/ReminderManager 已核实）✓
- 修改理由明确；不含 JSON/DoseEvent/Widget/Wear 扩张（§7 明确排除）✓
- provider 位置（data/repository/）符合架构（data 边界）✓
- MainActivity 修改必要（注入 plan contract + 保留临时 legacy 依赖给 Batch 6/7 消费者）且最小 ✓
- Predictor/Reminder 适配保持纯 Domain 边界 ✓
- 不修改 contract/Domain/Entity/schema/migration（§8.3、§6.3）✓

---

## Findings

### F1 (P2) — 历史文档状态漂移（设计已如实记录）

- **严重程度**: P2
- **文件**: `docs/evolune/ARCHITECTURE.md:117`（stale v2 描述）；`docs/evolune/DECISIONS.md:146-160` 与 `docs/PHASE_1_DESIGN.md:800-802`（3C 措辞）
- **问题**: 设计文档 §1.1 记录了这些漂移；已核实为非阻断历史状态。
- **影响**: 无实现影响；仅文档可读性。
- **最小修复方向**: 未来文档清理批次统一修正措辞，但不得重定义 Batch 5-8。
- **是否阻止实施**: 否

### F2 (P2) — `core.model` 依赖 `pk.Route`/`pk.Ester`

- **严重程度**: P2
- **文件**: `core/model/DoseEvent.kt`、`core/model/MedicationPlan.kt`
- **问题**: 已知过渡依赖（Batch 3B 接受），设计 §12 P2 已声明。
- **影响**: 无循环依赖/错误映射/持久化漂移（3C mapper 双向显式 when 互逆已验证）。
- **最小修复方向**: 留待独立 ADR 迁移枚举。
- **是否阻止实施**: 否

### F3 (P2) — 只读派生消费者（Reminder/Widget/Wear/JSON/PK/HRT）未切换

- **严重程度**: P2
- **文件**: 设计 §3 bypass 清单
- **问题**: 计划内推迟到 Batch 6/7；设计 §6.2.9-10 明确同库临时兼容读与 Wear 不变策略。
- **影响**: 过渡期内 HRTViewModel 等继续经 legacy 路径读同库 shadow；无写路径，无双事实来源。
- **最小修复方向**: 不修复；按 Batch 6/7 顺序切换。
- **是否阻止实施**: 否

**其余无问题（None）。** 未发现：双事实来源设计、同操作新旧双写、失败回退 legacy、遗漏计划写入口、计划/slots 漂移、内部构建升级真实库、术语误导（"双写"已限定）、错误分支无落点、仅 mock 测试、范围过宽等 P0/P1 情形。

---

## Independent validation performed

本轮为设计审阅，**未运行任何实现测试**（无实施代码）。实际执行与核实：

- Git 边界：分支/祖先/暂存区/单文件变化/无 schema 变化 ✓
- 27 个生产文件行号引用逐项 grep/read 核实（全部精确）✓
- PHASE_1_DESIGN Batch 5-8 段落（L843-894）、回滚（L707-715）、3C 措辞（L800-802）read 核实 ✓
- ADR-013/014/015/016 全文 read 核实 ✓
- ARCHITECTURE.md:117 stale 描述核实 ✓
- 全仓 grep：唯一 legacy plan 写链（PlansScreen→ViewModel→legacy repo→DAO）、唯一 Room builder（AppDatabase.kt:63）、JSON 不写计划、Widget/Wear/Reminder 只读计划 ✓

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。设计准确（27 个引用点全部精确）、范围封闭（唯一计划写入口识别且切换后无其他写者）、术语无歧义、5A/5B 拆分可执行、数据安全与停止条件完备。

**提交前必须修改的设计内容**：无。

**可推迟事项**：
- F1 文档漂移清理（独立文档批次）
- F2 Route/Ester 枚举迁移（独立 ADR）
- F3 Batch 6/7 入口切换

**是否建议提交 Batch 5 设计**：是。提交建议信息：`docs: add phase 1 batch 5 production wiring design`，打标签 `phase-1-batch-5-design-v1`。

**是否建议下一步进入 Batch 5A**：是，但仅在设计提交并打标签之后。Batch 5A 按 §8.1 只实现 provider + 纯 draft/slot mapper + JVM/provider tests（不改生产行为、不接运行时）；5B 完成并审阅后才标记 Batch 5。

**是否仍禁止真实数据库和 release**：**是**。设计 §10 持续禁止真实/派生数据库、内部 v3 APK、自动升级真实 v2、release 构建；真实数据库演练需 Batch 5-8 全部证据 + 所有者明确授权。Room v3 仍处 ADR-016 内部不可发布区间。

---

*审阅结束。最终工作树：仅原设计文档 + 本审阅报告；未修改任何其他文件。*
