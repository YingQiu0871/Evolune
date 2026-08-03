# Evolune Phase 1 Batch 3A 重点检查报告

**检查日期**: 2026-08-02
**检查范围**: Batch 3A 未跟踪新增（5 生产 + 2 测试 + 1 报告）
**分支**: `phase1/batch3-repository-mappers` (HEAD: `0cd5e96`)
**检查方式**: 只读；对照 ADR-015、PHASE_1_DESIGN.md §6.5/6.6/9.1/9.2/9.3 与 Batch 3A/3B/3C 边界

---

## 结论摘要

**可以提交 Batch 3A。** 五项重点检查全部通过，未发现 P0/P1 问题。

| 检查项 | 结果 |
|---|---|
| MedicationPlan 不变量与 ADR-015 一致 | ✅ 10 条不变量全部实现并测试 |
| contract 不暴露 Room | ✅ 依赖面仅 Domain + Flow + java.time/UUID |
| result variants 完整 | ✅ 五个 sealed 集合与锁定值完全一致 |
| 未提前实施 mapper / Repository 实现 | ✅ 无 mapper、无实现、无生产接线 |
| fake contract tests 只锁定接口 | ✅ 签名桩 + 变体穷举，无虚构行为 |

---

## 检查 1：MedicationPlan 不变量 vs ADR-015

对照 ADR-015 不变量与 PHASE_1_DESIGN §6.6 十项规则（`core/model/MedicationPlan.kt:23-29`）：

| ADR-015 / 设计规则 | 实现 | 测试 |
|---|---|---|
| `slot.planId` == `MedicationPlan.id` | `require(slot.planId == id)` (L26) | `mismatchedSlotPlanIdIsRejected` ✓ |
| slots 列表顺序权威，不自动排序 | 无排序逻辑 | `slotsAreNotAutomaticallySorted` ✓ |
| `slot.position` == 列表索引 | `require(slot.position == index)` (L27) | `eachPositionMustMatchItsListIndex` ✓ |
| position 零基、连续、唯一 | 由 `position == index` 传递保证 | `firstPositionMustBeZero`、`positionsMustBeContinuous` ✓ |
| 允许重复 localTime | 无唯一性检查 | `duplicateLocalTimesAreValid` ✓ |
| 允许空 slots | 无非空检查 | `emptySlotsAreValid` ✓ |
| 不自动重编号/去重/修正 | 仅 require，无写入 | — |
| `intervalDays` ∈ `1..Int.MAX_VALUE` | `require(intervalDays >= 1)` (L24) | `intervalDaysZeroIsRejected`、`negativeIntervalDaysAreRejected`、`intervalDaysOneIsValid`、`intervalDaysMaximumIsValid` ✓ |
| DAILY 忽略 daysOfWeek/intervalDays；WEEKLY 用 daysOfWeek；CUSTOM 用 intervalDays | 构造器不按类型清理字段 | `dailyRetainsIrrelevantScheduleFields`、`weeklyAllowsEmptyDaysOfWeek`、`customRetainsDaysOfWeek` ✓ |
| irrelevant 字段保留 legacy 值 | 无清理/标准化 | `nameDoseAndExtrasReceiveNoNewCompatibilityValidation` ✓ |

其余设计符合性：
- `createdAt: Instant`、`scheduleType: core.model.ScheduleType`（独立枚举，DAILY/WEEKLY/CUSTOM）✓
- 不包含 `getDescription()` 等显示方法 ✓
- 不包含 revision/并发版本字段（§9.2 明确禁止）✓
- `slots` 使用 `ScheduledDoseSlot`（Batch 2 类型），position 精度校验由 slot 自身 init 保证 ✓

**结论**：与 ADR-015 一致，无偏差。

---

## 检查 2：contract 是否真正不暴露 Room

逐一核对三个 contract 文件的 import：

- `DoseEventRepository.kt`: `core.model.DoseEvent`、`kotlinx.coroutines.flow.Flow`、`java.time.Instant`、`java.util.UUID`
- `MedicationPlanRepository.kt`: `core.model.MedicationPlan`、`Flow`、`UUID`
- `RepositoryResults.kt`: 仅 Kotlin 标准库

无 `androidx.room`、无 Entity、无 DAO、无 `Context`、无 Cursor、无 Compose、无 Wear、无 JSON。方法签名全部使用 Domain 类型、`Instant`、`UUID`、`Flow`。`getEventsForPk(asOf: Instant)` 保留 30 天/20 条语义与分支顺序（注释明确），`findOccurredBetween` 使用半开区间 `[startInclusive, endExclusive)` —— 与 §9.1 一致。

**结论**：contract 是纯接口层，不暴露任何持久化类型。

---

## 检查 3：result variants 是否完整

对照 §9.3 与 ADR-015 锁定的五个集合（`RepositoryResults.kt`）：

| Result | 锁定成员 | 实现 | 完整 |
|---|---|---|---|
| `InsertResult` | Inserted / Idempotent / Conflict / Invalid | 4 个 data object | ✅ |
| `UpdateResult` | Updated / NoChange / NotFound / RevisionConflict / Invalid | 5 个 | ✅ |
| `DeleteResult` | Deleted / NotFound | 2 个 | ✅ |
| `PlanSaveResult` | Created / Updated / NoChange / Invalid | 4 个 | ✅ |
| `PlanUpdateResult` | Updated / NoChange / NotFound / Invalid | 4 个 | ✅ |

`allResolvedResultVariantsRemainDistinctAndExhaustive` 测试用穷尽 `when` 名称映射锁定全部 19 个成员，未来增删成员会同时破坏编译与测试。基础设施故障（Room 打开、transaction、I/O）按设计继续作为异常，不伪装为业务结果 —— 符合 ADR-015 "数据库打开、transaction 和不可恢复 I/O 故障仍作为异常处理"。

**结论**：variants 完整且与设计逐字一致。

---

## 检查 4：是否提前实施了 mapper 或 Repository implementation

新增文件仅：
- `core/model/MedicationPlan.kt`、`ScheduleType.kt`（Domain）
- `core/dataapi/DoseEventRepository.kt`、`MedicationPlanRepository.kt`（纯接口）
- `core/dataapi/RepositoryResults.kt`（sealed 类型）

不存在：
- `data/mapper/` 目录或任何 Entity→Domain mapper（3B 边界，未越界）
- 现有 `data/DoseEventRepository.kt` / `data/MedicationPlanRepository.kt` 的任何修改（`git status` 无 tracked 改动）
- Repository 的 Room 实现类或 composition root 接线（3C 边界）
- v3 schema、Entity、DAO、migration 相关文件

`MedicationPlanRepository.save()` 的 KDoc 明确声明"v2 schema 没有 slots 存储，不提供生产实现，等待 v3" —— 正确表达了延迟原因。

**结论**：严格停留在 3A 边界，未提前实施 3B/3C 内容。

---

## 检查 5：fake contract tests 是否只锁定接口

`RepositoryContractTest.kt` 的两个 `SignatureOnly*` fake：

- 每个方法返回固定值（`flowOf(listOf(event))`、`emptyList()`、`Inserted`、`NoChange`、`NotFound`）
- 无状态、无逻辑、无持久化行为；注释与报告明确"只使编译器验证签名和 domain-only 依赖"
- `doseEventContractUsesOnlyDomainAndStandardTimeTypes` / `medicationPlanContractUsesOnlyDomainTypes` 断言的是：方法存在、类型兼容、Domain 对象可流经接口 —— 即签名锁定

两个测试没有虚构语义：
- 未声称 insert 的幂等/冲突行为（那是 3C 实现测试职责）
- 未测试 Room、事务或排序行为
- `assertSame` 用于 data object 单例，是引用相等断言，语义正确
- `allResolvedResultVariantsRemainDistinctAndExhaustive` 是变体穷举锁定，非行为虚构

唯一可留意的点（P2 级备注，非问题）：fake 返回值如 `InsertResult.Inserted` 是桩内硬编码，断言它只证明"变体存在且可实例化"，不构成未来实现的语义约束 —— 这正是本批想要的锁定粒度，3C 阶段再以真实实现测试补语义覆盖。

**结论**：fake 测试只锁定接口形状与变体集合，无虚构行为。

---

## 验证运行

- `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` → **BUILD SUCCESSFUL**（5 套件 / 47 测试，与本批报告一致）
- `git diff --check` → 通过；无 tracked 文件被修改
- 3A 报告中的全量 17/135、PK 49、assembleDebug、lint、androidTest 编译结果与本批文件无冲突

---

## 提交建议

**结论：APPROVE。** 无 P0/P1，P2 备注（fake 返回值语义由 3C 真实实现测试补充）不影响提交。

提交前事项：
1. `git add` 5 个生产文件 + 2 个测试文件 + `docs/phase-reports/PHASE_1_BATCH_3A_REPORT.md`
2. 提交信息建议：`feat: add phase 1 domain plan aggregate and repository contracts`

留给 Batch 3B 的事项：
- v2 Entity → Domain 只读 mapper（DoseEventEntityMapper / MedicationPlanEntityMapper / PersistenceEnumMapper）
- 穷尽 `when` 的 enum / ExtraKey 显式映射（禁 ordinal）
- `Instant.toEpochMilli()` 的 `ArithmeticException` 边界转换
- `timeOfDay` 按原顺序生成 Slot ID v1（空列表/重复时间保留）

不可提前开始：3C（等 Batch 4 v3 schema）、Batch 4 任何 schema/Entity/DAO 变更。
