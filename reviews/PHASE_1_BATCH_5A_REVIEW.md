# Evolune Phase 1 Batch 5A 代码审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch5a-provider-adapters`（HEAD: `01ec39a`，前置 tag `phase-1-batch-5a0-design-v1`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 Batch 5B

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 2（Provider instrumentation 反射断言的可维护性；Route/Ester 已知过渡依赖——均非阻断）
- **是否允许提交**: 是
- **是否允许提交后进入 Batch 5B**: 是（提交并打标签后）
- **最大剩余风险**: 无 P0/P1。Provider 单实例/线程安全/契约表面、Draft 12 字段精确、slot 生成责任唯一、DraftMappingResult 不掩盖错误、无生产行为变化；v3 仍不可发布。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch5a-provider-adapters` ✓ |
| 前置 tag | `phase-1-batch-5a0-design-v1` 为 HEAD 祖先（exit 0）✓ |
| 未跟踪文件 | 恰好 5 个（provider / draft mapper / 2 测试 / 报告）✓ |
| 已跟踪文件修改 | 无（`git diff --name-status` 为空）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| MainActivity/ViewModel/UI/JSON/Reminder/Widget/Wear 变化 | 无 ✓ |
| schema/migration/Gradle/Manifest 变化 | 无 ✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Provider API verdict

`ProductionRepositoryProvider.kt`（25 行）：

- `get(Context)` 使用 `context.applicationContext`（L21）✓
- 只调用现有 `AppDatabase.getDatabase(applicationContext)`（L21）——全仓 grep 确认唯一生产 builder 仍在 `AppDatabase.kt:63` ✓
- 两个 Repository（`RoomDoseEventRepository`/`RoomMedicationPlanRepository`）从**同一 database** 构造（L11-12）✓
- 对外属性类型为 contract（`DoseEventRepository`/`MedicationPlanRepository`，L11-12）✓
- 不暴露 AppDatabase/DAO/Entity（仅两个 contract val）✓
- 不持有 Activity/ViewModel/Compose ✓
- 不自动读写业务数据 ✓；无 destructive migration ✓；无 fallback implementation ✓
- 不吞数据库初始化异常（`AppDatabase.getDatabase` 异常自然传播）✓
- **单事实来源**：provider 只包装已有 singleton，不创建新数据库 ✓

---

## Provider lifecycle verdict

- **线程安全**：`@Volatile` + `synchronized(this)` 双检锁（L15-23）——并发首次调用只创建一个 provider ✓
- **getter 稳定**：`val` 属性构造时初始化一次，同一 provider 内重复访问返回同一 Repository 对象（instrumentation `assertSame` 验证）✓
- 不每次构造新 provider/新 Repository（instance 缓存）✓
- 无可变全局业务状态（instance 仅缓存 provider 引用，非 service locator 隐藏状态）✓
- 无测试污染（测试用 internal constructor + disposable database，不走 companion `get()`）✓

---

## Test seam verdict

- `internal constructor(database: AppDatabase)`（L8）只接收已创建 database；provider 自身无 builder ✓
- 不修改 production singleton（无 setter/reset；companion instance 只能由 `get()` 赋值）✓
- 不修改 production database name（测试用 `batch5a_provider_test.db` 独立文件）✓
- **internal 可见性**：androidTest 与 main 同 module，经 Kotlin friend path 可访问；其他 module/外部调用无法绕过 ✓
- 不允许运行时替换 production provider ✓
- 两个 Repository 确实使用注入的同一 database（instrumentation 保存/回读证明，非仅类名比较）✓
- 无全局 reset ✓
- 生产调用方无法误用（companion `get()` 是唯一生产入口，internal constructor 对 feature 代码不可见）✓

---

## Draft contract verdict

`MedicationPlanDraft`（L16-29）恰好 12 字段：id/name/route/ester/doseMG/scheduleType/times/daysOfWeek/intervalDays/isEnabled/extras/createdAt ✓

- 无 revision ✓；无 Room/Entity 字段 ✓；无 legacy timeOfDay ✓；无 UI transient state ✓；无 Context ✓；无 JSON/Reminder/Widget/Wear 字段 ✓
- `times: List<LocalTime>` ✓；`extras: Map<ExtraKey, Double>` 完整保留 ✓
- 纯 Kotlin（imports 仅 core.model/pk/java.time/UUID，L3-14）——无 Android/Compose/Room/DAO/Repository/文件/网络/Locale/时区 ✓

---

## Draft-to-Domain verdict

`toDomainMedicationPlan()`（L51-105）20 项全部符合：

- blank name → `MissingRequiredField(NAME)`（L53-55，`isBlank`）✓
- 顺序保留、重复保留、空列表合法 ✓
- `00:00`/`23:59` 合法；非零 second/nano → `NonMinuteTime(position)`（L59-62，无截断）✓
- `position == index`（forEachIndexed）✓；`slot.planId = id`（L67）✓
- 只用 `ScheduledDoseSlotId.generate`（L63）——不复制/修改 UUIDv5 算法 ✓
- 不排序/不去重/不调 `UUID.randomUUID`/不调时钟/不读时区/Locale ✓
- createdAt 精确保留（L97）✓；extras/daysOfWeek 完整保留 ✓
- 构造完整 Domain aggregate（L83-99）；generator 失败 → `SlotIdGenerationFailure(position)`（L72-74）✓
- **固定向量**：测试 8 硬编码 `17d1fd14-9d70-5344-beaa-0b158c9f62f4`（planId 0000...0001 / position 0 / 08:30）✓ 与 ADR-014 一致

---

## Domain-to-Draft verdict

`toMedicationPlanDraft()`（L107-156）：

- id/createdAt/全字段/extras 完整保留 ✓
- times 来自 Domain slots 权威列表顺序（L148 `slots.map { it.localTime }`）✓
- 验证：planId/position==index（L114）、minute precision（L120-123）、UUIDv5 重算（L124-133）✓
- slot ID 不一致 → `SlotIdMismatch(index)`（L126-128）✓
- 不静默修复/不重排/不访问数据库 ✓
- **SlotIdMismatch 测试可公开构造**：`ScheduledDoseSlot` 是公开 data class，`original.slots.single().copy(id = uuid(999))` + `original.copy(slots = ...)` 即可构造 Domain 允许但 ID 不匹配的对象（Domain 构造器不校验 slot ID）——无反射/unchecked cast/测试后门 ✓

---

## Slot identity verdict

- **责任唯一**：Draft adapter 生成完整 `List<ScheduledDoseSlot>`（position==index + UUIDv5 v1）；`RoomMedicationPlanRepository.save` 与 persistence mapper（`MedicationPlanEntityMapper.kt:138-175`）只验证并原子持久化——**无双重生成** ✓
- 相同 localTime 不同 position → 不同 ID（测试 4 `assertNotEquals`）✓
- 顺序变化按 ADR-014 合理改变后续 Slot ID（测试未断言错误稳定性）✓

---

## Draft result/error verdict

- 独立类型（L31-49），不复用 persistence `MappingResult` 或 Repository results ✓
- 5 类 issue 稳定、无异常 message 协议（`SlotIdGenerationFailure` 不暴露 `UuidV5Failure.message`）✓
- 不含完整 plan/times/dose/extras/健康数据 ✓
- issue 顺序稳定（字段问题先于时间问题，时间按 position 序——测试 14 验证聚合顺序）✓
- **不 catch Throwable/所有 RuntimeException**：只 catch `IllegalArgumentException` 于显式 Domain 构造边界（L100）——意外程序错误正常传播 ✓
- `DomainValidationFailure` 不过宽（仅 Domain 构造）；测试 13 用 `intervalDays = 0` 真实 invariant 验证 ✓
- 无 InvalidUuid/非法字符串/offset/JSON issue（typed UUID/LocalTime 无法表达）✓

---

## JVM test quality verdict

**18 个测试逐一核对（L24-284）**：

| # | 测试 | 覆盖 |
|---|---|---|
| 1 | `emptyTimesMapToEmptySlots` | 空列表 ✓ |
| 2 | `oneTimeMapsToPositionZero` | 单时间 ✓ |
| 3 | `multipleTimesKeepOrderAndContinuousPositions` | 顺序/连续 ✓ |
| 4 | `duplicateTimesArePreservedWithDistinctIds` | 重复+distinct IDs ✓ |
| 5 | `midnightAndEndOfDayAreAcceptedAndCanonical` | 00:00/23:59 ✓ |
| 6 | `nonZeroSecondsAreRejectedAtTheirPosition` | seconds ✓ |
| 7 | `nonZeroNanosAreRejectedAtTheirPosition` | nanos ✓ |
| 8 | `fixedUuidV5VectorMatchesLockedExpectedValue` | 硬编码固定 UUID ✓ |
| 9 | `planIdIsPreservedByPlanAndEverySlot` | plan ID/ownership ✓ |
| 10 | `fixedCreatedAtIsPreservedExactly` | createdAt ✓ |
| 11 | `everyDraftFieldIncludingExtrasIsPreserved` | 全字段+extras ✓ |
| 12 | `blankNameReturnsMissingRequiredField` | blank name ✓ |
| 13 | `invalidDomainInvariantReturnsStableIssue` | DomainValidationFailure（intervalDays=0 真实 invariant）✓ |
| 14 | `fieldAndTimeIssuesUseStableValidationOrder` | issue 聚合顺序 ✓ |
| 15 | `domainToDraftToDomainPreservesCompletePlan` | 全字段 round trip（含重复时间+extras）✓ |
| 16 | `domainToDraftRejectsUnexpectedSlotId` | SlotIdMismatch（公开构造）✓ |
| 17 | `localeAndDefaultTimeZoneDoNotChangeMapping` | Locale/TZ 真实改变 + finally 恢复 ✓ |
| 18 | `mapperSourceHasNoClockRandomPlatformOrSideEffectDependencies` | 静态 source audit ✓ |

- expected 不全由被测 mapper 生成（固定向量硬编码）✓
- round trip 比较全字段（测试 15 `assertEquals(original, roundTrip)` data class 全字段相等）✓
- Locale/TZ 测试恢复全局状态（finally）✓
- 静态 audit（测试 18）双路径 fallback（`src/main/...` 或 `app/src/main/...`），不过度脆弱 ✓
- 未删除/放宽现有 Domain/mapper tests（无 tracked 修改）✓；无真实数据 ✓
- 18/18 与报告一致（JUnit XML 实测）✓

---

## Provider instrumentation verdict

**2 个测试（L57-108）**：

1. `providerUsesStableContractRepositoriesFromOneInjectedDatabase`：disposable file-backed DB 注入 → getter 稳定（assertSame）→ 反射验证 JVM 返回类型为 contract → 合成 plan/event 保存/回读 → version=3 → 无第二测试库 ✓
2. `dataSurvivesReopenThroughANewProviderForTheSameDisposableFile`：保存 → close → 重开同文件 → 新 provider 包装 → 数据回读 → 反射验证无 Dao/Entity 返回类型 ✓

- 使用 disposable file-backed Room DB（`batch5a_provider_test.db`），非 in-memory ✓
- 不调用 production singleton（`ProductionRepositoryProvider(opened)` 直接构造）✓
- reopen 用新的 disposable instance + 新 provider ✓；close 后旧 provider 不再使用 ✓
- "同一 database"经保存/回读证明（非仅类名/路径）✓
- 第二数据库检查：`databaseList()` 前缀过滤 + 只允许期望 4 个 artifact（主文件/-wal/-shm/-journal）✓
- cleanup 在 @Before/@After（closeDatabase + deleteDatabaseArtifacts + 存在断言）✓
- 合成 fixture ✓；从未打开 `evolune_database` ✓ → **无 P0 风险**

---

## Production behavior verdict

- grep 全仓：`ProductionRepositoryProvider` 引用仅自身声明文件；`MedicationPlanDraftMapper`/`MedicationPlanDraft` 引用仅自身声明文件——**无生产调用方** ✓
- MainActivity/ViewModel/Compose 未使用 provider/Draft ✓（无 tracked 修改）
- legacy plan Repository 调用未变 ✓；无新生产数据库访问/双写/fallback ✓
- 无 Reminder/Widget/Wear side effect ✓
- **无隐式启用**（companion `get()` 无静态初始化调用；无 Application 类引用）✓ → 无 P1

---

## Schema and architecture verdict

- `git diff` 对 schemas/migration/core/AppDatabase → **全空** ✓
- Room version=3；v3 identityHash `c5f5e02cb04b048ca28fe96a74d61606`、SHA-256 `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`（diff 空即未变）✓
- Domain/contract/DAO/Entity 无变化 ✓
- 无新 Gradle 依赖/module；无 Hilt/Koin ✓

---

## Report accuracy verdict

逐项核对 `PHASE_1_BATCH_5A_REPORT.md`：

| 声明 | 独立核实 |
|---|---|
| 5 个文件 | ✓ 与工作树一致 |
| Draft JVM 18/18 | ✓ 独立运行（JUnit XML 18）|
| Provider instrumentation 2/2 | ✓ 独立重跑（Starting/Finished 2 tests）|
| Repository 23 / migration 18 / matrix 22 | ✓ 全量 connected 68/68 独立重跑（2+23+18+22+2+1=68 组成核实）|
| App JVM 27/249 | ✓ 独立运行（JUnit XML 27 suites/249）|
| mapper 53 / core 47 / migration JVM 43 / PK 49 / Wear 1 | ✓ 此前批次核实 + 本轮 PK/wear 独立运行 |
| App/Wear build、lint 0 errors | ✓ 独立重跑 |
| schema/domain/contract/DAO/Entity 无变化 | ✓ git diff 全空 |
| 无生产调用方/用户可见行为变化 | ✓ grep 核实 |
| 5B 未开始、Batch 5 未完成、v3 不可发布 | ✓ 如实声明 |
| 未夸大：不声称 UI/ViewModel 已接线、真实库已验证、provider 生产运行、v3 可发布 | ✓ 全部如实 |

报告与代码/结果一致。

---

## Findings

### F1 (P2) — Provider instrumentation 反射断言的可维护性

- **严重程度**: P2
- **文件**: `ProductionRepositoryProviderTest.kt:67-73, 103-107`
- **问题**: 测试 1 用 `getMethod("getDoseEvents")` 反射验证 getter 返回类型；测试 2 用 `declaredMethods` 返回类型字符串匹配（`contains("Dao")`/`contains("Entity")`）验证无泄漏。当前正确（Kotlin val 属性生成同名 getter），但若未来属性改为函数或方法签名变化，测试会脆弱失败。
- **触发条件**: 未来 provider API 重构。
- **影响**: 仅测试可维护性；当前行为正确。
- **依据**: 无对应设计要求；P2 定义（测试组织/可读性）。
- **最小修复建议**: 保持现状或改用 Kotlin 属性引用（`provider.doseEvents::class`）替代反射。
- **是否阻止提交**: 否

### F2 (P2) — `core.model` 依赖 `pk.Route`/`pk.Ester`（已知过渡依赖）

- **严重程度**: P2
- **文件**: `MedicationPlanDraftMapper.kt:9-10`（imports）、`core/model/*`
- **问题**: 与 3B/3C/5 设计一致的已知过渡依赖，Draft 复用同一枚举。
- **影响**: 无。
- **最小修复建议**: 留待独立 ADR。
- **是否阻止提交**: 否

**其余无问题（None）。** 未发现：provider 打开/删除/修改真实库、第二事实来源、Slot ID 不一致、schema/migration 修改、真实数据、并发单例破坏、test seam 生产滥用、Draft 字段遗漏、双重生成 slots、结果类型掩盖错误、假覆盖、生产行为变化、报告不一致等 P0/P1 情形。

---

## Independent validation executed

以下全部为本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`，`ANDROID_SERIAL=emulator-5556`）：

| 命令 | 结果 |
|---|---|
| `adb -s emulator-5556 shell getprop sys.boot_completed` / `ro.build.version.sdk` | 1 / 33 ✓ |
| `:app:testDebugUnitTest --tests "application.*" --rerun-tasks` | **PASS** — 18 tests（JUnit XML 实测）|
| `:app:connectedDebugAndroidTest -P...class=...ProductionRepositoryProviderTest --rerun-tasks` | **PASS** — Starting/Finished 2 tests |
| `:app:testDebugUnitTest --rerun-tasks`（全量）| **PASS** — 27 suites / 249 tests |
| `:app:connectedDebugAndroidTest --rerun-tasks`（全量）| **PASS** — Finished 68 tests, 0 failed |
| `:app:testDebugUnitTest --tests "pk.*" --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS（0 errors）|
| `git diff`（schemas/migration/core/AppDatabase）| 全空 ✓ |
| 全仓 grep（provider/mapper 引用）| 仅自身声明文件，无生产调用方 ✓ |

未声称执行任何未实际运行的命令。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。两个 P2（反射测试可维护性、Route/Ester 过渡依赖）均不阻止提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- F1：反射断言替代（可选）
- F2：Route/Ester 枚举迁移（独立 ADR）

**是否建议提交 Batch 5A**：是。提交建议信息：`feat: add production repository provider and medication plan draft adapter`，打标签 `phase-1-batch-5a`。

**是否建议提交后开始 Batch 5B**：是，但仅在 5A 提交并打标签之后。Batch 5B 按 5A-0 契约 §13 与 Batch 5 设计 §6.2 执行计划入口原子切换（ID/createdAt 会话、Compose text parsing、ViewModel/Repository 接线、Reminder 副作用排序、移除 legacy plan save path），必须保留"先 5B 审阅后标记 Batch 5"的门槛。

**是否继续禁止真实数据库和 release**：**是**。Room v3 仍处 ADR-016 内部不可发布区间；Batch 5A 完成不代表 Batch 5 完成；真实数据库演练需 Batch 5-8 全部证据 + 所有者授权。

---

*审阅结束。最终工作树：仅原 5 个 Batch 5A 文件 + 本审阅报告；未修改任何其他文件。*
