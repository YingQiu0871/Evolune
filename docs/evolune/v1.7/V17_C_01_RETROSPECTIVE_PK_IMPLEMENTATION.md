# V17-C-01 — Retrospective PK — Implementation Specification

> 状态：`IMPLEMENTATION SPEC / NOT IMPLEMENTED`（**R4 corrected**；docs-only；不写生产代码）
> Round：v1.7-C / **C-01**（approved history architecture → projection result → retrospective
> extraction → eligibility → ordering → patch grammar → unchanged SimulationEngine → typed result）
> 起始 HEAD：`97943722a5f37efb7dff8d4b39e500df2017db82`（B-04 APPROVE / **PHASE B CLOSED**）
> 上游契约（约束性输入，**优先于本文件**）：
> [`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`](V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md)（R4 corrected）
> 关联：`V17_SPEC.md` §4/§12/§14 · `V17_PLAN.md` §6 · `V17_ACCEPTANCE.md` §3（C1–C9）·
> `V17_A_02_HISTORY_READ_MODEL.md` · `V17_A_04_HARDENING.md` · `V17_B_01_INSIGHTS_DOMAIN.md`
> 本文件只定义**实现规格**；实现轮在独立复审本规格后另行开工。

---

## 0. R4 修正处置（独立 GLM 复审 6×P2；architect accepted）

| # | 项 | 处置 |
|---|---|---|
| RC-1 | finite 非正剂量行为（逐 route） | §6/§7.3：受支持非 patch route 与一阶 PATCH 的 finite `dose <= 0` → **保留为合法引擎输入**（零贡献 parity）、**非 producing**、**无 exclusion**、不触发 `EXCLUDED_RECORDED_INTAKES`；仅 NaN/±Inf → `UNSUPPORTED_OR_INCOMPLETE_EVENT`；全零贡献事实 → `NO_ELIGIBLE_RECORDED_INTAKES`；INJECTION×E2 / 零级 PATCH 特例保持 |
| RC-2 | cursor 不可达路径 | §5.3/§5.6/§9：`calculatedInterval == visibleWindow`；`QUERY_OUTSIDE_CALCULATED_INTERVAL` 为**防御性 typed 路径**（公开请求不可达，保留于冻结 taxonomy）；新增内部 `RetrospectivePkIntervalValidator.validateCursor(...)`，服务在插值前调用；U7/S13c 改为直接测该 validator（刻意制造 malformed 内部关系） |
| RC-3 | matchKey 提取/lookbackStart/pk id | §6：`route = Route.valueOf(matchKey.routeKey)`、`doseMG = matchKey.doseAmount`；解析顺序（先 route；ANTIANDROGEN 不解析 ester；否则 `Ester.valueOf(...)`）；解析失败 → `RetrospectivePkContractViolationException`；`lookbackStart = min consumed occurredAt`（精确）；`pk.DoseEvent.id = authoritative eventId` |
| RC-4 | 100k guard 异常机制 | §3.2/§4.2/§5.5/§9：授权 generator 的 100k guard **仅异常类型化**（`MedicationOccurrenceGenerationLimitExceededException : IllegalStateException`）；HistoryReadService **只捕获该精确类型**并重包 history 诊断；服务把该诊断映射 `HISTORICAL_INPUT_UNAVAILABLE`（零 partial）；禁止 broad catch |
| RC-5 | 极端窗口资源行为 | §5.1/§9：`MAX_RETROSPECTIVE_PK_CALCULATED_DURATION = 366 days`；duration > 366 天 → `INVALID_QUERY_INTERVAL`；数值安全带 `|epochMillis| ≤ 3_800_000_000_000_000`；溢出安全比较；资源门之后才计算 steps，规则 = `max(ceil(hours*12.0) + 1, 1000)`（**floor**，短窗不得判违例）、checked fits Int、无饱和（R4.1 修正） |
| RC-6 | 异常分类与取消 | §9/§12：源码核对的精确类型（`RepositoryStorageException` sealed 子类）；`CancellationException` 永远 rethrow；`ensureActive()` ≥4 位点；禁止 `catch (Throwable/Exception/IllegalStateException)` 语义分类；不修改 `SimulationEngine` |
| P3 | 独立复审 P3 吸收 | §5.1（DateTimeException）、§10（多查询一致性边界）、§13（fresh undo 测试、pk id、安全带门、body-weight 校验、术语、guard 扫描范围、extras equality 回归） |

> **R4.1（本轮窄修正，architect P2）**：恢复冻结的 1000 点**最小网格 floor** ——
> `steps = max(ceil(hours * 12.0) + 1, 1000)`（与现行生产一致），短窗口不得判契约违例；
> 计算仅在 366 天资源门通过后、以 checked 非饱和算术进行（§5.1/§9；测试 V22–V27）。
> 其余 RC-1..RC-6 与全部 P3 内容不变。

**R1/R2/R3 已接受、不再 reopen**：单一 `HistoryReadService` reader + `AllAvailableHistory`；
四条 limitation；affected-chain 补丁语法；单次 100k guard（无累计上限）；六个 extras；
full-lookback `UNRECORDED_OCCURRENCES_PRESENT`；typed model provenance；cursor 公开不变量；
typed DST 解析；毫秒端点；有限输入门；五值 exclusion reasons；公开 unavailable 四值；
黄金 AUC `23285.499354395688 ± 1e-9` / 1441 点 / 六个固定采样。

---

## 0.1 范围与硬约束（违反即 stop）

**在范围内**：给既有 History 架构增加 all-history 只读入口；回顾性提取/资格/排序/补丁语法预处理；
cursor 防御校验；有限输入门与 366 天资源门；调用**未改动**的 `SimulationEngine`；
返回 typed 结果；配套 JVM 测试与 1 条 Room instrumentation 查询测试。

1. **唯一 historical reader = `HistoryReadService`**；回顾性层不得出现 `DoseEventRepository`/DAO 引用。
2. 不得改动 `pk/` 数值文件（引擎/解析器/模型/参数/枚举）。
3. 不得 `getEventsForPk` / `MedicationPlanPredictor` / synthetic dose / unrecorded 折算计。
4. 不得 Room schema/entity/migration/index 变更。
5. 不得改 Phase A/B 冻结语义；**唯一例外（R4 授权）**：`MedicationOccurrenceGenerator.kt` 的
   100k guard 异常类型化（阈值/计数/控制流/生成语义不变）。
6. 全路径 zero-write；异常按精确类型分类；`CancellationException` 永远 rethrow；禁止 broad catch。

---

## 1. 现行状态审计（实现前必须知道的既有事实）

| 事实 | 位置 | 影响 |
|---|---|---|
| Home PK 输入 = `getEventsForPk`（30 天；<20 条回退最近 20） | `RoomDoseEventRepository.kt:70-87`、`:252` | **不得复用**；回顾性无下界 |
| Home PK 加入 current-plan 预测 | `PkSimulationCalculator.kt:40-53`、`MedicationPlanPredictor.kt:140/149` | 回顾性不得调用 |
| 曲线 UI 裁剪（36h 视窗、丢负值、±1e6、端点回退） | `ConcentrationChart.kt:152-157`、`ConcentrationChartGeometry.kt:120-126`、`:50/:65`、`SimulationEngine.kt:104-107` | 回顾性数值与 cursor 估计不得经过 |
| 三处现行消费者 | `HRTViewModel.kt:354`、`WearAppDataLayer.kt:90`、`WidgetWork.kt:95` | 保持原样 |
| History 读路径（双通道 + 分块 + 单投影） | `HistoryReadService.kt:58-205` | all-history 入口复用同类实现 |
| 投影唯一匹配入口 + 完整性不变式 | `MedicationTimeline.kt:86-121`、`HistoricalProjection.kt:204-331` | 回顾性只能经此 |
| `RecordedMedicationEvent` 当前无 extras；`matchKey(routeKey, medicationKey, doseAmount)` | `MedicationTimeline.kt:18-26`；`MedicationOccurrenceDomainMapper.kt:44-59` | 批准扩展 + 提取来源（§6） |
| 事件→投影映射（`status != RECORDED` → null） | `MedicationOccurrenceDomainMapper.kt:44-59` | null → 契约违例 |
| generator 单窗上限 + 100k guard | `MedicationOccurrenceGenerator.kt:10-28/77-78` | 分块复用；R4 授权异常类型化 |
| 绝对 epoch-hours | `LegacyTimeAdapter.kt:26/58-73` | 唯一时间桥 |
| 贴片配对依赖列表顺序 | `SimulationEngine.kt:57-71`、`:148-154` | 确定性排序必需 |
| 存储异常层级 | `RepositoryStorageException.kt:9-53` | 精确分类依据（§12） |
| 体重校验 | `SettingsDataStore.kt:63-66`（finite ∧ >0 ∧ ≤300） | 请求边界 precond（§9） |
| 黄金基线 | `SimulationEngineTest.kt:39-55` | 不得改动 |

---

## 2. 目标链路（冻结）

```
authoritative Room facts (dose_events)
        │  repository boundary（唯一出入口）
        ▼
HistoryReadService
        │  ├─ 既有 readRange(...)                        ← 不变
        │  └─ 新 all-history entry readAllAvailable(...) ← C-01 capability
        │       └─ generator 100k guard → 捕获专用异常 → history 内部诊断
        ▼
AllAvailableHistory（lookbackStart?, upperBoundInclusive, HistoricalProjection）
        ▼
RetrospectivePkExtractor（只读投影 entries；matchKey 提取；extras 来自派生层）
        │  ├─ 契约断言 + 有限输入门 + typed exclusion records
        │  ├─ 六键 extras（HistoricalMedicationExtraKey → pk ExtraKey）
        │  └─ deterministic ordering（occurredAt, eventId）
        ▼
RetrospectivePkPatchPreprocessor（语法 + unusable transition 保守失效）
        ▼
SimulationEngine（未改动；数值事件 + patch control 事件）
        ▼
RetrospectivePkResult（model context + summary + series/curve + cursorEstimate + exclusions + 四条 limitations）
```

**禁止**：第二个并行 historical reader；回顾性 PK 直接查询 repository/DAO；复制 matcher/projection/completeness/分块。

---

## 3. 精确到文件的改动计划（R4 更新）

### 3.1 生产改动（新增）

| 文件 | 内容 |
|---|---|
| `app/.../history/AllAvailableHistory.kt`（新） | `AllAvailableHistorySource`（唯一实现 = HistoryReadService）+ `AllAvailableHistory` |
| `app/.../history/pk/RetrospectivePkModels.kt`（新） | §5 全部类型/枚举/异常/窗口/时间解析/interval validator/series 校验 |
| `app/.../history/pk/RetrospectivePkExtractor.kt`（新） | 投影 → 引擎输入（§6） |
| `app/.../history/pk/RetrospectivePkPatchPreprocessor.kt`（新） | 语法/受影响链/unusable transition（§8） |
| `app/.../history/pk/RetrospectivePkService.kt`（新） | 组装 + 引擎 + cursor + typed 结果（§9） |
| `experience-core/.../experience/HistoricalMedicationExtra.kt`（新） | `HistoricalMedicationExtraKey`（六键） |

### 3.2 生产改动（修改）

| 文件 | 改动 | 约束 |
|---|---|---|
| `app/.../history/HistoryReadService.kt` | 实现 `AllAvailableHistorySource`；`readAllAvailable` 只捕获专用 generator 异常并重包；`readRange` 0 改动 | 不新建 reader；不 broad catch |
| **`experience-core/.../experience/MedicationOccurrenceGenerator.kt`** | **R4 授权（仅异常类型化）**：把 `MAX_GENERATED_OCCURRENCES` guard 的 generic `check(...)` 替换为 `MedicationOccurrenceGenerationLimitExceededException(message) : IllegalStateException(message)`；阈值/计数/控制流/生成语义完全不变 | 不得改 matcher/identity/scheduling/formulas/limits；需阈值 parity + `readRange` 回归证明 |
| `app/.../data/DoseEventDao.kt` | +1 查询 `getEventsUpToOccurredAt(endInclusive: Long)` | 只读 |
| `app/.../core/dataapi/DoseEventRepository.kt` | +1 抽象方法 `findAllOccurredUpTo(endInclusive: Instant)`（无默认实现） | 仅供 HistoryReadService |
| `app/.../data/repository/RoomDoseEventRepository.kt` | 实现该方法 | 只读 |
| `experience-core/.../experience/MedicationTimeline.kt` | `RecordedMedicationEvent` 追加 `extras`（默认仅兼容） | 见 §7.1 的 equality 告警 |
| `app/.../core/presentation/MedicationOccurrenceDomainMapper.kt` | 穷尽映射六个 core `ExtraKey` → `HistoricalMedicationExtraKey` | 不得依赖默认 empty；parity 测试 |
| `app/.../core/adapter/DomainDoseEventToPkAdapter.kt` | 新增 `HistoricalMedicationExtraKey.toPkExtraKey()` | 唯一 pk 侧映射 |

### 3.3 测试侧机械改动（申报）

- `RepositoryFakes.kt`：实现 `findAllOccurredUpTo` 且按上界过滤；记录 `lastAllEventsUpperBound`。
- 其余显式 implementer 机械补方法；`by delegate` 包装器无需改动；
  既有 `RecordedMedicationEvent` 构造点因默认参数兼容（equality 回归见 §13.0 V17）。

---

## 4. All-history 能力

### 4.1 数据通道

```kotlin
@Query(
    """
    SELECT * FROM dose_events
    WHERE occurredAtEpochMillis <= :endInclusive
    ORDER BY occurredAtEpochMillis ASC, id ASC
    """
)
suspend fun getEventsUpToOccurredAt(endInclusive: Long): List<DoseEventEntity>
```

```kotlin
/** 无下界、上界 inclusive；仅供 HistoryReadService 使用；不得被非 History 层调用。 */
suspend fun findAllOccurredUpTo(endInclusive: Instant): List<DoseEvent>
```

SQL `ORDER BY` 只保证稳定；权威 tie-break 由 Kotlin `(occurredAt, id.toString())`（§6）。

### 4.2 入口、consumed lookback 与 guard 处理

```kotlin
fun interface AllAvailableHistorySource {
    suspend fun readAllAvailable(
        upperBoundInclusive: Instant,
        displayZone: ZoneId,
        policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
    ): AllAvailableHistory
}

data class AllAvailableHistory(
    val upperBoundInclusive: Instant,
    /** R4 精确冻结：min(occurredAt) over consumed authoritative event set（upper-bound read 后、
     *  eligibility/exclusion 前）；null ⇔ consumed 集合为空。 */
    val lookbackStart: Instant?,
    val projection: HistoricalProjection
)
```

1. `events = doseEvents.findAllOccurredUpTo(upperBoundInclusive)`。
2. 契约断言：id 唯一；`toRecordedMedicationEvent() != null`；排序 `(occurredAt, id)`。
3. 空集合 → `lookbackStart = null` + 空投影。
4. occurrence 上下文（覆盖到上界）：
   ```
   contextStartDate = min(candidateDate) - 1 day
   contextEndDate   = max(max(candidateDate), upperBound 本地日期) + 1 day
   candidateDate(event) = persisted localDate ?: occurredAt.atZone(displayZone).toLocalDate()
   ```
5. 分块 `generateContextOccurrences`（≤3660 天/块；**无累计上限**）。
   **单次** `generate()` 触发 guard → generator 抛
   `MedicationOccurrenceGenerationLimitExceededException`（R4 授权的新类型）；
   `readAllAvailable` **只捕获该精确类型**并重包为 `HistoricalOccurrenceLimitExceededException`
   （history 层内部诊断）。
6. `HistoricalProjectionBuilder.derive(occurrences, mappedEvents, now = upperBoundInclusive, displayZone, policy)`。
7. 断言投影事件引用集合 == consumed id 集合；返回 `AllAvailableHistory`。

**R4**：guard 内部原因不得成为公开 semantic reason；服务把 history 诊断映射为
`HISTORICAL_INPUT_UNAVAILABLE`（无 partial/truncated 输出）。`readRange` 行为不变（见 §13.0 V8）。

---

## 5. 类型模型（concrete Kotlin types；R4 corrected）

### 5.1 窗口、资源边界、时间解析与端点精度

```kotlin
sealed interface LocalQueryTimeResolution {
    data class Resolved(val instant: Instant, val offset: ZoneOffset) : LocalQueryTimeResolution
    data class GapAdjusted(
        val requested: LocalDateTime, val resolvedInstant: Instant, val resolvedOffset: ZoneOffset
    ) : LocalQueryTimeResolution
    data class OverlapChoiceRequired(
        val requested: LocalDateTime, val candidates: List<Resolved>
    ) : LocalQueryTimeResolution
}

object LocalQueryTimeResolver {
    fun resolve(selected: LocalDateTime, zoneId: ZoneId, explicitOffset: ZoneOffset? = null): LocalQueryTimeResolution
}

data class RetrospectivePkWindow(
    val startInclusive: Instant,
    val endInclusive: Instant
) {
    init {
        require(startInclusive.isBefore(endInclusive)) { "window must be strictly ordered" }
        require(startInclusive.isMillisecondAligned() && endInclusive.isMillisecondAligned()) {
            "retrospective window endpoints must be millisecond-aligned"
        }
    }

    companion object {
        fun fromLocalDates(startDate: LocalDate, endDate: LocalDate, displayZone: ZoneId): RetrospectivePkWindow {
            require(!endDate.isBefore(startDate)) { "end date must not precede start date" }
            return RetrospectivePkWindow(
                startInclusive = startDate.atStartOfDay(displayZone).toInstant(),
                endInclusive = endDate.plusDays(1).atStartOfDay(displayZone).toInstant().minusMillis(1)
            )
        }

        /** OverlapChoiceRequired 不得进入（fail-fast 提示候选 offset）。 */
        fun fromResolutions(start: LocalQueryTimeResolution, end: LocalQueryTimeResolution): RetrospectivePkWindow
    }
}

/** 不溢出谓词；禁止 toEpochMilli()/ofEpochMilli() 往返判定。 */
private fun Instant.isMillisecondAligned(): Boolean = nano % 1_000_000 == 0

/** R4：单窗口资源边界（非历史正确性截断）。 */
val MAX_RETROSPECTIVE_PK_CALCULATED_DURATION: Duration = Duration.ofDays(366)

/** R4：数值安全带（保证毫秒端点 ↔ epoch-hours 往返等式的可执行成立）。 */
const val MAX_RETROSPECTIVE_PK_EPOCH_MILLIS = 3_800_000_000_000_000L
```

**Query 校验顺序（service 数值边界，全部映射 typed `INVALID_QUERY_INTERVAL`，不抛异常）**：

```
a. guarded toEpochMilli(start), toEpochMilli(end)   // ArithmeticException -> INVALID_QUERY_INTERVAL
b. |ms| <= MAX_RETROSPECTIVE_PK_EPOCH_MILLIS        // 带外 -> INVALID_QUERY_INTERVAL
c. duration = endMs - startMs（溢出安全：subtractExact / 前置安全带保证）+ 溢出检查
   duration <= 366 days ? 允许 : INVALID_QUERY_INTERVAL   // 资源门；> 366 天 -> 不读历史、不调引擎
d. 防御性 cursor validator（§5.6）-> 非 null 则 Unavailable(reason)
```

**数值网格（R4.1 冻结；与现行生产规则一致）**：仅在 366 天资源门通过后计算；
`hours = endTimeH - startTimeH` 必须已 finite 且非负：

```kotlin
val densitySteps = ceil(hours * 12.0).toLong() + 1L          // density = 12.0 points/hour
val stepsLong = maxOf(densitySteps, 1000L)                   // minimum grid size = 1000
if (stepsLong > Int.MAX_VALUE) {
    throw RetrospectivePkContractViolationException(
        "retrospective PK step count exceeds Int range"
    )
}
val steps = stepsLong.toInt()
```

**短窗口必须 floor 到 1000**（`densitySteps < 1000` **不得**判契约违例）；禁止 Double→Int 饱和转换；
禁止在资源门前求值。参考点：24h → 1000；83.25h → 1000；84h → 1009；366 天 → 105,409（fits Int）。

**DateTimeException（P3）**：极端 `LocalDate`/`LocalDateTime` 运算（`plusDays`/`atStartOfDay`/`atZone` 等）
可能抛 `DateTimeException`；属于 request/helper-boundary failure，必须映射为 `INVALID_QUERY_INTERVAL`
（typed）或请求边界失败，不得未类型化逃逸。

**数值安全带后果**：窗口端点在 `|ms| ≤ 3.8e15` 内时，`instantToTimeH → timeHToEpochMillis(Math.round)`
往返精确；带外一律拒绝（不再依赖“文档假设”）。

### 5.2 Model context（enum）与 request（cursor）

```kotlin
enum class RetrospectivePkParameterSet { EVOLUNE_E2_PK_PARAMETER_SET_V1 }
enum class ParameterBasis { CURRENT_MODEL_PARAMETERS }
enum class BodyWeightBasis { CURRENT_SETTING_AT_QUERY_TIME }
enum class IntakeBasis { RECORDED_ACTUAL_INTAKES }
enum class HistoricalParameterSnapshotAvailability { UNAVAILABLE }

data class RetrospectivePkModelContext(
    val parameterSet: RetrospectivePkParameterSet,
    val parameterBasis: ParameterBasis,
    val bodyWeightKg: Double,
    val bodyWeightBasis: BodyWeightBasis,
    val intakeBasis: IntakeBasis,
    val historicalParameterSnapshotAvailability: HistoricalParameterSnapshotAvailability,
    val capturedAt: Instant
) {
    companion object { fun current(bodyWeightKg: Double, capturedAt: Instant): RetrospectivePkModelContext }
}

data class RetrospectivePkRequest(
    val visibleWindow: RetrospectivePkWindow,
    val cursor: Instant?,          // null 或 ∈ [start,end]；越界 = 请求构造失败（不放宽）
    val displayZone: ZoneId,
    val bodyWeightKG: Double,      // precondition: finite ∧ >0 ∧ <=300（§9 step 1）
    val capturedAt: Instant,
    val policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
) {
    init {
        require(cursor == null || (!cursor.isBefore(visibleWindow.startInclusive) &&
            !cursor.isAfter(visibleWindow.endInclusive))) { "cursor must lie inside the visible window" }
    }
}
```

### 5.3 Exclusions / unavailable / interval validator（R4）

```kotlin
enum class RetrospectivePkExclusionReason {
    UNKNOWN_OR_PARTIAL_IDENTITY,
    ANTIANDROGEN_IDENTITY_UNAVAILABLE,
    UNSUPPORTED_CURRENT_MODEL_COMBINATION,
    UNSUPPORTED_OR_INCOMPLETE_EVENT,
    AMBIGUOUS_PATCH_PAIRING
}

data class RetrospectivePkExcludedEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val reason: RetrospectivePkExclusionReason
)

enum class RetrospectivePkLimitation {
    EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE,
    UNRECORDED_OCCURRENCES_PRESENT,
    EXCLUDED_RECORDED_INTAKES,
    AMBIGUOUS_PATCH_PAIRING
}

/** 公开 semantic taxonomy，恰好四值。 */
enum class RetrospectivePkUnavailableReason {
    NO_ELIGIBLE_RECORDED_INTAKES,
    INVALID_QUERY_INTERVAL,
    QUERY_OUTSIDE_CALCULATED_INTERVAL,
    HISTORICAL_INPUT_UNAVAILABLE
}

/** R4：防御性内部 seam；公开请求不可达（calculatedInterval == visibleWindow）。 */
internal object RetrospectivePkIntervalValidator {
    fun validateCursor(
        cursor: Instant?,
        calculatedInterval: RetrospectivePkWindow
    ): RetrospectivePkUnavailableReason?   // 越界 -> QUERY_OUTSIDE_CALCULATED_INTERVAL，否则 null
}
```

### 5.4 Summary / series / cursor / result

```kotlin
data class RetrospectivePkInputSummary(
    val lookbackStart: Instant?,               // 精确值 = min consumed occurredAt（§4.2）
    val upperBoundInclusive: Instant,
    val engineInputEventIds: List<UUID>,
    val concentrationProducingEventIds: List<UUID>,
    val patchControlEventIds: List<UUID>
)

data class RetrospectivePkPoint(val instant: Instant, val concentrationPGmL: Double)

data class RetrospectivePkSeries(
    val startInclusive: Instant,
    val endInclusive: Instant,
    val points: List<RetrospectivePkPoint>
)

data class RetrospectivePkCursorEstimate(val cursor: Instant, val concentrationPGmL: Double)

sealed interface RetrospectivePkResult {
    val modelContext: RetrospectivePkModelContext
    val summary: RetrospectivePkInputSummary
    val exclusions: List<RetrospectivePkExcludedEvent>
    val limitations: Set<RetrospectivePkLimitation>

    data class Available(
        val calculatedInterval: RetrospectivePkWindow,          // 当前 == visibleWindow
        val series: RetrospectivePkSeries,
        val cursorEstimate: RetrospectivePkCursorEstimate?,
        val curve: SimulationResult,
        override val modelContext: RetrospectivePkModelContext,
        override val summary: RetrospectivePkInputSummary,
        override val exclusions: List<RetrospectivePkExcludedEvent>,
        override val limitations: Set<RetrospectivePkLimitation>
    ) : RetrospectivePkResult

    data class Unavailable(
        val reason: RetrospectivePkUnavailableReason,
        override val modelContext: RetrospectivePkModelContext,
        override val summary: RetrospectivePkInputSummary,
        override val exclusions: List<RetrospectivePkExcludedEvent>,
        override val limitations: Set<RetrospectivePkLimitation>
    ) : RetrospectivePkResult
}
```

### 5.5 异常（R4：精确分类）

```kotlin
/** R4 授权：generator guard 专用类型（experience-core，类型化 only）。 */
class MedicationOccurrenceGenerationLimitExceededException(
    message: String
) : IllegalStateException(message)

/** history 层内部诊断：readAllAvailable 只捕获上面精确类型后的重包。 */
class HistoricalOccurrenceLimitExceededException(message: String) : RuntimeException(message)

/** 回顾性层内部契约违例；fail-fast，服务不捕获。 */
class RetrospectivePkContractViolationException(message: String) : IllegalStateException(message)

object RetrospectivePkSeriesValidator {
    /** 每点有限且非负；违反抛 RetrospectivePkContractViolationException。 */
    fun validate(points: List<RetrospectivePkPoint>)
}
```

### 5.6 seam（含测试用引擎缝）

```kotlin
fun interface RetrospectivePkSource {
    suspend fun estimate(request: RetrospectivePkRequest): RetrospectivePkResult
}

/** app-internal 测试缝；生产默认实现逐字调用未改动的 SimulationEngine。 */
fun interface RetrospectivePkCurveRunner {
    fun run(
        events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
        bodyWeightKG: Double,
        startTimeH: Double,
        endTimeH: Double,
        numberOfSteps: Int
    ): SimulationResult
}

class RetrospectivePkService(
    private val history: AllAvailableHistorySource,          // 唯一生产实现 = HistoryReadService
    private val curveRunner: RetrospectivePkCurveRunner = DefaultRetrospectivePkCurveRunner
) : RetrospectivePkSource
```

---

## 6. 提取（projection → engine input；R4 提取/分类冻结）

```kotlin
object RetrospectivePkExtractor {
    fun extract(source: AllAvailableHistory, window: RetrospectivePkWindow): RetrospectivePkExtraction
}

data class RetrospectivePkExtraction(
    val engineEvents: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
    val engineInputEventIds: List<UUID>,
    val concentrationProducingEventIds: List<UUID>,
    val patchControlEventIds: List<UUID>,
    val exclusions: List<RetrospectivePkExcludedEvent>
)
```

```
1. entries = source.projection.entries
   intake events = MatchedHistoricalOccurrence.event + UnmatchedHistoricalIntake.event
2. 契约断言（fail-fast）：同一 eventId 恰好一次；所有 intake event.occurredAt <= upperBoundInclusive
3. 逐 event 提取（R4 冻结；只用既有 matchKey，不扩展 RecordedMedicationEvent）：
     route  = Route.valueOf(event.matchKey.routeKey)      // parse failure -> RetrospectivePkContractViolationException
     if (route == ANTIANDROGEN) -> ANTIANDROGEN_IDENTITY_UNAVAILABLE（**不解析 ester**）
     else ester = Ester.valueOf(event.matchKey.medicationKey)
                                     // parse failure（E2 PK route）-> RetrospectivePkContractViolationException
     doseMG = event.matchKey.doseAmount
4. eligibility：
     INJECTION × E2                     -> UNSUPPORTED_CURRENT_MODEL_COMBINATION
     non-finite model-driving 值        -> UNSUPPORTED_OR_INCOMPLETE_EVENT
     不可表示时间（instantToTimeH 失败）:
         patch route                    -> unusable transition（§8.2）
         非 patch route                 -> UNSUPPORTED_OR_INCOMPLETE_EVENT
     PATCH_APPLY / PATCH_REMOVE         -> patch transition 序列
     else                               -> candidate（剂量资格 §7.3(c)）
5. 映射：timeH；extras 经 HistoricalMedicationExtraKey -> pk ExtraKey；
     pk.DoseEvent(id = event.eventId, route, timeH, doseMG, ester, extras)
     // pk.id 必须显式使用 authoritative eventId，**绝不**依赖 pk.DoseEvent 的随机 UUID 默认值
6. 排序：按 (event.occurredAt, event.eventId.toString()) 升序（冻结顺序；patch 配对依赖）
7. patch grammar 预处理（§8）→ 受影响链 + unusable transition 保守失效
8. producing 分类（§7.3(d)）：concentrationProducing = 进入引擎且
     (zero-order PATCH with finite rate > 0) ∨ (dose-driven mode with finite doseMG > 0)
```

**约束**：不读取 repository/DAO/原始行；不改写 provenance；不重跑匹配；
AFTER_QUERY_WINDOW_END 在读取边界过滤（无 exclusion record）；
解析失败绝不降级为 `UNKNOWN_OR_PARTIAL_IDENTITY`。

---

## 7. extras 映射与有限输入门

### 7.1 两层映射（唯一实现）

```
Row(dose_events.extras) --toRecordedMedicationEvent()--> RecordedMedicationEvent.extras
   --HistoricalMedicationExtraKey.toPkExtraKey()--> pk DoseEvent.extras
```

六键一一对应（`CONCENTRATION_MG_ML` / `AREA_CM2` / `RELEASE_RATE_UG_PER_DAY` /
`SUBLINGUAL_THETA` / `SUBLINGUAL_TIER` / `ANTI_ANDROGEN_TYPE`）。禁止第二份映射、key 过滤/改名、
retrospective-only raw-event join、依赖默认 `emptyMap()`。缺失键 = 合法输入。

**P3 告警（冻结）**：追加 `extras` 改变 `RecordedMedicationEvent` 的 `equals`/`hashCode`；
全量回归必须覆盖此前假设“无 extras”的手工构造期望对象。

### 7.2 引擎现行行为（文档，不修改）

| 输入 | 引擎分支/结果 |
|---|---|
| release rate 缺失 | 一阶 |
| 有限 rate `> 0` | 零级 |
| 有限 rate `<= 0` | 一阶 |
| rate `NaN` | 一阶 |
| rate `+Infinity` | 零级 + 非有限传播 |
| rate `-Infinity` | 一阶 |
| 剂量驱动模式 `doseMG <= 0`（有限） | 贡献 0 |

### 7.3 回顾性适配器资格（冻结；R4 剂量规则）

**（a）release-rate**：缺失 → 一阶；有限 `> 0` → 零级；有限 `<= 0` → 现行一阶分支；
`NaN`/`±Infinity` → 排除 `UNSUPPORTED_OR_INCOMPLETE_EVENT`。

**（b）通用有限门**：**non-finite model-driving numeric value → exclude**
（`UNSUPPORTED_OR_INCOMPLETE_EVENT`）；不得把“有限但未知”的值当作排除理由。

**（c）剂量规则（R4 冻结，逐 route）**：

| 情形 | 有限 `doseMG > 0` | 有限 `doseMG <= 0` | NaN/±Inf |
|---|---|---|---|
| 受支持非 patch（INJECTION EB/EV/EC/EN、ORAL 全部、SUBLINGUAL 全部、GEL 任意酯） | producing | **引擎输入保留（零贡献 parity）；非 producing；无 exclusion；不触发 `EXCLUDED_RECORDED_INTAKES`** | 排除 |
| PATCH 零级（有限 rate `> 0`） | `doseMG` 可为 0 → producing | 负值保留 R2/R3 规则（排除） | 排除（R2/R3 规则，不扩大） |
| PATCH 一阶（rate 缺失或有限 `<= 0`） | producing | **引擎输入保留（零贡献 parity）；非 producing；无 exclusion** | 排除 |

**（d）producing 定义（冻结）**：

```
producing ⇔ (zero-order PATCH with finite rate > 0)
          ∨ (doseMG finite > 0 in a dose-driven mode)
```

**（e）后果（冻结）**：全部为零贡献事实 → `concentrationProducingEventIds.isEmpty()`
→ `NO_ELIGIBLE_RECORDED_INTAKES`；不得输出零值 Available 曲线。

**（f）`SUBLINGUAL_TIER` 精确行为**：有限 `code.toInt()` ∈ {0,1,2,3} → 对应档位；
其它有限 → current STANDARD fallback（不排除）；NaN/±Inf → 排除。

**（g）输出保证**：`Available.series` 每点有限且非负（§5.5），违反 = 内部契约违例。

---

## 8. 贴片语法与预处理器

### 8.1 规则（C-00 §6.2）

- G1 同 instant patch transition = ambiguous（不得用 id 制造先后）
- G2 APPLY while active = ambiguous
- G3 orphan REMOVE = ambiguous
- G4 duplicate REMOVE = ambiguous
- G5 final APPLY 可保持 open（合法）
- 只排除受影响歧义链；其它可解析链与非贴片事件不受影响。

### 8.2 算法（含 unusable transition）

```
group = []; groupHasFailure = false; openApplies = 0; lastInstant = null

for (ev in orderedPatchTransitions) {
    sameInstant = (lastInstant != null && ev.occurredAt == lastInstant)
    if (!sameInstant && openApplies == 0 && group.isNotEmpty()) {
        if (groupHasFailure) emitExcludedRecords(group)
        group = []; groupHasFailure = false
    }
    if (ev.isUnusable) {
        groupHasFailure = true                 // unusable 不打开、不关闭
    } else when (ev.route) {
        PATCH_APPLY -> {
            if (sameInstant) groupHasFailure = true          // G1
            if (openApplies >= 1) groupHasFailure = true     // G2
            openApplies += 1
        }
        PATCH_REMOVE -> {
            if (sameInstant) groupHasFailure = true          // G1
            if (openApplies == 0) groupHasFailure = true     // G3/G4
            else openApplies -= 1
        }
    }
    group += ev; lastInstant = ev.occurredAt
}
if (group.isNotEmpty() && groupHasFailure) emitExcludedRecords(group)
engineEvents = orderedUsableEvents - allExcludedIds
```

`emitExcludedRecords`：unusable transition 自身 → `UNSUPPORTED_OR_INCOMPLETE_EVENT`；
该链其余成员 → `AMBIGUOUS_PATCH_PAIRING`。

**断言**：unusable `PATCH_REMOVE` 不得使前置 APPLY 变成伪合法 final-open；不得发明 patch-instance identity；
不得丢弃无关合法链/非 patch 事件。

### 8.3 示例（逐行测试）

| 输入 | 保留 | 排除 |
|---|---|---|
| `A` / `A, R` / `A, R, A` | 保留 | — |
| `R` | — | R |
| `A, R1, R2` | A, R1 | R2 |
| `A=t, R=t` | — | A, R |
| `A1=t, A2=t` | — | A1, A2 |
| `A1, A2, R` | — | A1, A2, R |
| `A1, R1, A2, A3, R2` | A1, R1 | A2, A3, R2 |
| `A1, A2, R1, A3, R2` | — | A1, A2, R1, A3, R2 |
| `A1, R1=t, A2=t` | — | A1, R1, A2 |
| `A=t, R1=t+1h, A2=t+1h` | — | A, R1, A2 |
| `A, R(unusable)` | — | A（`AMBIGUOUS_PATCH_PAIRING`）、R（`UNSUPPORTED_OR_INCOMPLETE_EVENT`） |
| `A(unusable)` | — | A（`UNSUPPORTED_OR_INCOMPLETE_EVENT`） |
| `R(unusable), A, R2` | A, R2 | R1（`UNSUPPORTED_OR_INCOMPLETE_EVENT`） |

---

## 9. 服务、模拟、cursor 与结果（R4 corrected）

`RetrospectivePkService.estimate(request)`：

```
 0. modelContext = RetrospectivePkModelContext.current(request.bodyWeightKG, request.capturedAt)
 1. require(isValidBodyWeight(request.bodyWeightKG))       // finite ∧ >0 ∧ <=300；fail-fast；
                                                           // 永不进入引擎输入
 2. Query/资源校验（typed INVALID_QUERY_INTERVAL；无异常逃逸）：
      a. guarded toEpochMilli(start/end)  -> ArithmeticException -> INVALID_QUERY_INTERVAL
      b. |ms| <= 3_800_000_000_000_000    -> 带外 -> INVALID_QUERY_INTERVAL
      c. duration = endMs - startMs（溢出安全）；> 366 days -> INVALID_QUERY_INTERVAL
         （不读历史、不调引擎）
      d. calculatedInterval = request.visibleWindow
         reason = RetrospectivePkIntervalValidator.validateCursor(cursor, calculatedInterval)
         reason != null -> Unavailable(reason)              // 防御性；公开请求不可达
 3. currentCoroutineContext().ensureActive()                // before read
 4. source = try { history.readAllAvailable(calculatedInterval.endInclusive, displayZone, policy) }
      catch (CancellationException)                            { throw }   // 永远 rethrow
      catch (HistoricalOccurrenceLimitExceededException)       { Unavailable(HISTORICAL_INPUT_UNAVAILABLE) }
      catch (RepositoryStorageException)                       { Unavailable(HISTORICAL_INPUT_UNAVAILABLE) }
      // 普通 IllegalStateException / RetrospectivePkContractViolationException 不捕获（fail-fast）
 5. currentCoroutineContext().ensureActive()                // after read
 6. source.lookbackStart == null -> Unavailable(NO_ELIGIBLE_RECORDED_INTAKES)
 7. extraction = RetrospectivePkExtractor.extract(source, calculatedInterval)
 8. concentrationProducingEventIds 为空 -> Unavailable(NO_ELIGIBLE_RECORDED_INTAKES)
 9. steps 计算（仅资源门后；R4.1 冻结）：hours = endTimeH - startTimeH（finite、非负）；
      densitySteps = ceil(hours * 12.0).toLong() + 1L；
      stepsLong = maxOf(densitySteps, 1000L)；若 > Int.MAX_VALUE ->
      RetrospectivePkContractViolationException；steps = stepsLong.toInt()；
      **禁止**饱和转换；短窗 floor 到 1000（不是违例）
10. currentCoroutineContext().ensureActive()                // before engine
    curve = curveRunner.run(extraction.engineEvents, bodyWeightKG, startTimeH, endTimeH, steps)
11. currentCoroutineContext().ensureActive()                // after engine
12. series = curve.timeH -> Instant.ofEpochMilli(timeHToEpochMillis(it))
    RetrospectivePkSeriesValidator.validate(series.points)
13. cursorEstimate = cursor?.let { 对 series.points 二分+线性插值（**不得**调用
      SimulationResult.concentration()）}
14. limitations（恰好四条）
15. Available(calculatedInterval, series, cursorEstimate, curve, modelContext, summary, exclusions, limitations)
```

**R4 冻结**：

- `HISTORICAL_INPUT_UNAVAILABLE` 绝不附带 partial/truncated 数值输出；被拒绝的 range **不读历史、不调引擎**；
- 语义分类只允许精确类型（`HistoricalOccurrenceLimitExceededException`、`RepositoryStorageException`）；
  禁止 `catch (Throwable)` / `catch (Exception)` / `catch (IllegalStateException)`；
- `CancellationException` 永远 rethrow；`ensureActive()` 覆盖 read 前/后、engine 前/后；
- cursor 插值只对 series；永不用端点 clamp；`curve` 原样返回；steps 密度 `12.0` 与现行一致
  （`PkSimulationCalculator.kt:17`）；
- 不修改 `SimulationEngine`（366 天资源门是无挂起引擎调用的保护之一）。

---

## 10. 零写入边界与一致性边界

- 回顾性包只调用 `AllAvailableHistorySource.readAllAvailable`（→ HistoryReadService → repository 只读查询）、
  `HistoricalProjection` 读取、`curveRunner`（默认 → `SimulationEngine.run`）。
- 禁止：`DoseEventRepository`/`DoseEventDao`/`AppDatabase`/`Entity` 引用、`findAllOccurredUpTo` 调用、
  写方法、`getEventsForPk`、`MedicationPlanPredictor`、派生实现引用、`SimulationResult.concentration`。
- 计数替身按五条 intent 断言零写入且 reads > 0。
- **多查询一致性边界（P3）**：History 的 plan/slot/event 读取不保证单次原子数据库快照；
  投影在同一读取批次内派生；C-01 不引入新写事务、不引入第二事实来源。

---

## 11. 依赖与接线（C-01 不接线）

组合根接线属 C-05；cursor UI 属 C-03/C-05。

---

## 12. 异常分类表（R4 冻结；源码核对）

| 触发 | 层 | 结果 |
|---|---|---|
| `CancellationException`（含 `runStorageOperation` 重抛） | 任意 | **永远 rethrow** |
| generator guard（专用 `MedicationOccurrenceGenerationLimitExceededException`） | experience-core | history 层只捕获该精确类型 → 重包 `HistoricalOccurrenceLimitExceededException` → 服务映射 `HISTORICAL_INPUT_UNAVAILABLE` |
| `RepositoryStorageException`（sealed；`CorruptAggregateException`/`RepositoryConstraintException`/`RepositoryPersistenceException`） | data | `HISTORICAL_INPUT_UNAVAILABLE` |
| `RetrospectivePkContractViolationException` | retrospective | fail-fast 传播 |
| 投影 completeness / union conflict 的普通 `IllegalStateException`；程序不变量失败 | projection/history | fail-fast 传播 |
| request 构造非法（反向/sub-ms/cursor 越 visible/overlap 未选 offset） | 请求边界 | `IllegalArgumentException` |
| 可表示性/安全带/366 天/溢出 | service | typed `INVALID_QUERY_INTERVAL` |
| matchKey route/ester 解析失败 | extractor | `RetrospectivePkContractViolationException` |

**禁止**：围绕 history read 的语义分类使用 `catch (Throwable)` / `catch (Exception)` /
`catch (IllegalStateException)`；`RepositoryStorageException` **继承** `IllegalStateException`，
必须按精确子类型分类。

---

## 13. 精确测试矩阵（C-01 实现轮交付）

### 13.0 R4 delta（必须存在）

| # | 用例 | 判定 |
|---|---|---|
| V1 | oral dose 0 / negative | 引擎输入存在；零贡献；**无 exclusion**；非 producing |
| V2 | gel dose 0；supported injection dose 0；sublingual dose 0 | 同上（逐 route） |
| V3 | 一阶 PATCH dose 0 | 引擎输入存在；零贡献；无 exclusion；非 producing |
| V4 | 仅零贡献 recorded facts | `NO_ELIGIBLE_RECORDED_INTAKES`（无零值 Available 曲线） |
| V5 | `pk.DoseEvent.id` == authoritative `eventId` | 恒等（绝不随机默认值） |
| V6 | `lookbackStart` | 精确 == `min(consumed occurredAt)`；空集 → null |
| V7 | matchKey route/ester 解析失败 | `RetrospectivePkContractViolationException`（不是 `UNKNOWN_OR_PARTIAL_IDENTITY`） |
| V8 | generator 专用异常 + 阈值 parity | 第 100,000 条边界行为与既有语义一致；`readRange` 回归不变 |
| V9 | 防御性 interval validator | 刻意 malformed 内部关系 → `QUERY_OUTSIDE_CALCULATED_INTERVAL` |
| V10 | cursor 越 visibleWindow | 构造抛 `IllegalArgumentException`（公开路径） |
| V11 | 366 days 精确 | 接受（读历史 + 调引擎） |
| V12 | 366 days + 1 ms | `INVALID_QUERY_INTERVAL`；**引擎未被调用**（计数 runner = 0） |
| V13 | 数值安全带边界 | `|ms| == 3.8e15` 接受；带外 → `INVALID_QUERY_INTERVAL` |
| V14 | steps 溢出防护 | 极端允许窗口下 steps fits Int、>=1000；无静默饱和 |
| V15 | `CancellationException` | 原样逃逸（不吞、不映射） |
| V16 | repository storage/read 失败 | `HISTORICAL_INPUT_UNAVAILABLE`（精确类型） |
| V17 | 投影/内部 plain ISE | 不被吞、不被转 unavailable（fail-fast） |
| V18 | fresh undo/delete | 事件存在 → 贡献存在；删除/撤销后下一次 fresh read → 贡献与事件均消失；无缓存/ghost |
| V19 | extras equality 回归 | 手工构造期望对象包含更新后的 `extras`；全量回归无假阳性 |
| V20 | body weight 边界 | `!finite` / `<=0` / `>300` → precondition failure；永不进引擎 |
| V21 | DateTimeException 映射 | 极端日期运算 → `INVALID_QUERY_INTERVAL`（不未类型化逃逸） |
| V22 | **24-hour window** | `steps == 1000`（floor 生效，无违例） |
| V23 | **short window (< 83.25 h)** | `steps == 1000` |
| V24 | **exactly 83.25 h** | `steps == 1000`（999 + 1 = 1000） |
| V25 | **84-hour window** | `steps == 1009`（1008 + 1） |
| V26 | **366-day maximum window** | step 数 fits `Int` 且保持 12/hour 密度（105,409） |
| V27 | **rejected >366-day window** | step 计算从未发生（runner 计数 = 0；与 V12 一致） |

### 13.1 `HistoryReadServiceAllAvailableTest`

| # | 用例 | 判定 |
|---|---|---|
| R1 | 25 条事件横跨 90 天 | 全部进入投影；无 30d/20 语义 |
| R2 | 上界捕获 | 实参 == `calculatedInterval.endInclusive` |
| R3 | 跨越 4000 天 | 最旧事件仍在；无 `MAX_RANGE_DAYS` 截断 |
| R4 | 分块上下文（>3660 天） | 无 gap/无重复；完整性成立 |
| R5 | 单次 generator 超限 | 专用类型 → history 诊断 → 服务 `HISTORICAL_INPUT_UNAVAILABLE`（V8） |
| R6 | 跨 chunk 合计 > 100k 且每块未超 | 正常返回（无累计上限） |
| R7 | 最后记录与上界之间的计划 occurrence | 投影含 `UnrecordedHistoricalOccurrence` |
| R8 | 空事件 | `lookbackStart == null` + 空投影（V6） |
| R9 | 重复 eventId / 非 RECORDED | fail-fast（契约违例，非 unavailable） |
| R10 | 延迟提醒形状 | 匹配仍可达 |

### 13.2 `RetrospectivePkExtractorTest`

| # | 用例 | 判定 |
|---|---|---|
| E1 | route×ester 资格表 | 逐格 classified；exact mapping（INJECTION×E2、ANTIANDROGEN；V7） |
| E2 | `INJECTION × E2` | 排除且与“包含”逐点相等 |
| E3 | ANTIANDROGEN | 不在引擎输入；不解析 ester；reason 精确 |
| E4 | 六键 extras 三层 parity | 逐键相等 |
| E5a | NaN/±Inf model-driving | 排除；不在引擎输入 |
| E5b | 有限 ≤0 rate + 正 dose（一阶 PATCH） | 走现行一阶分支（引擎对照逐点相等） |
| E5c | **有限 ≤0 dose（非 patch / 一阶 patch）** | 引擎输入保留、零贡献、无 exclusion、非 producing（V1–V3） |
| E5d | 零级 `doseMG == 0` + 正 rate | 保留且贡献 > 0 |
| E9a | 有限未知 tier | STANDARD parity |
| E9b | 非有限 tier | 排除 |
| E6 | `Instant.MAX`-equivalent | patch → unusable transition；非 patch → `UNSUPPORTED_OR_INCOMPLETE_EVENT`；不崩溃 |
| E7 | 唯一性/上界违约 | 契约违例 |
| E8 | 排序 + pk id | 冻结序；pk id == eventId（V5） |

### 13.3 `RetrospectivePkPatchPreprocessorTest`

| # | 用例 | 判定 |
|---|---|---|
| P1 | §8.3 表格全部行 | 保留/排除集合与 reason 逐一相等 |
| P2 | 合法链 parity | 与直接引擎一致（不多排除） |
| P3 | 歧义链作用域 | 仅受影响链移除 |
| P4 | 排列/同 instant | 同 instant 不被 id 拆开；结果确定 |
| P5 | final open APPLY | 保留 |
| P6 | orphan/duplicate REMOVE | 判 ambiguity 并排除 |
| P7 | unusable REMOVE 前置 APPLY | 不得伪 valid open |

### 13.4 `RetrospectivePkServiceTest`

| # | 用例 | 判定 |
|---|---|---|
| S1 | 固定 fixture 黄金值 | 锁定 AUC/采样；既有黄金不变 |
| S2 | anti-plan | 与仅历史基线一致；guard 无 `MedicationPlanPredictor` |
| S3 | 上界 end / end+1ms | 端点含、越界不含；非 exclusion |
| S4 | 前史 | 窗口曲线 > 0 |
| S5 | 无记录 / 全部排除 / 全零贡献 | `NO_ELIGIBLE_RECORDED_INTAKES`（V4） |
| S6 | 非法/不可表示 query | `INVALID_QUERY_INTERVAL`，无异常逃逸（V11–V14、V21） |
| S7 | 确定性重复 | 两次结果相等 |
| S8 | clamp 无泄漏 | curve 与引擎逐点相等；guard 无图表 clamp |
| S9 | model context enum | 精确枚举值；非法不可表达 |
| S10 | limitations 四条条件 | 精确；边界诊断与零剂量不计入 EXCLUDED |
| S11 | summary 非损失 | **lookbackStart 精确值**（V6）+ 三类 id 列表 |
| S12 | 毫秒端点往返 | window↔series 端点精确（安全带内） |
| S13a | cursor == null | `cursorEstimate == null` |
| S13b | cursor at start/inside/at end | 插值与手工一致 |
| S13c | **防御 validator（malformed 内部关系）** | 直接调用返回 `QUERY_OUTSIDE_CALCULATED_INTERVAL`（V9） |
| S13d | cursor 越 visible（公开构造） | `IllegalArgumentException`（V10） |
| S14 | cursor 不使用 clamp | 真插值；guard 禁止 `SimulationResult.concentration` |
| S15 | all-history 读取失败 | `HISTORICAL_INPUT_UNAVAILABLE`，无 partial（V16） |
| S16 | 单次 guard | `HISTORICAL_INPUT_UNAVAILABLE`，零 partial（V8） |
| S17 | 公开 enum 恰四值 | 集合相等；旧原因不公开 |
| S18 | **取消传播** | `CancellationException` 逃逸（V15） |
| S19 | **内部 ISE 不吞** | 投影/契约违例传播（V17） |
| S20 | **fresh undo** | 删除后 next-read 贡献消失（V18） |
| S21 | **body weight** | 非法权重 precondition failure（V20） |
| D1 | DST gap typed | `GapAdjusted`（requested + resolved） |
| D2 | DST overlap 显式选择 | 无 offset → `OverlapChoiceRequired`；显式 → 各自 `Resolved` |
| D3 | 日期线跳变 | 全日 helper 端点确定、不丢事件 |
| T1 | 同 instants 跨 displayZone | 数值逐点相等 |
| P10k | 10k 事件 smoke | 线性完成（非 gate） |

### 13.5 `RetrospectivePkZeroWriteTest`

五条 intent：zero writes 且 reads > 0。

### 13.6 `RetrospectivePkGuardTest`（扫描范围冻结）

**扫描范围 = 新增的 `app/.../history/pk/**` 回顾性包**（**不含** `HistoryReadService` 或
`repository/data` 实现——那里 Generator/ProjectionBuilder/DAO 相关 token 是合法必需的）。
禁止 token：`DoseEventRepository`、`DoseEventDao`、`AppDatabase`、`Entity`、`findAllOccurredUpTo`、
`getEventsForPk`、`MedicationPlanPredictor`、`MedicationOccurrenceGenerator`、`HistoricalProjectionBuilder`、
`MedicationOccurrenceMatcher`、`withTransaction`、写方法、`System.currentTimeMillis`、`SystemClock`、
`ZoneId.systemDefault`、`ConcentrationChart`、`calculateVisibleWindowYScale`、
`SimulationResult.concentration`、`coerceIn(`（数值 clamp；白名单需显式）。
正确 token：`AllAvailableHistorySource` 至少一次（非 vacuous）。

### 13.7 回归

- 全量 JVM（`--rerun-tasks`）：既有 1161 基线不得回退（含 V19 extras equality 回归）。
- `SimulationEngineTest` 黄金值（AUC/1441/六采样）不变。
- `readRange` 既有行为与阈值回归（V8）。

### 13.8 Room instrumentation（1 条追加到既有 `RoomRepositoryTest`）

`findAllOccurredUpToIsInclusiveAndOrderedWithoutLowerBound`：`end` 返回、`end+1ms` 不返回、极旧行返回、
顺序一致；真实 Room + 生产 repository。

---

## 14. 显式非目标（C-01 禁止）

1. UI/Compose/导航/字符串/Preview；cursor UI 与日期时间选择器（C-03/C-05）。
2. planned/actual 标记与差值（C-04）。
3. 修改 `getEventsForPk` / Home / Wear / Widget 现行 PK 管线。
4. 第二个 historical reader / 复制匹配/投影/完整性/分块。
5. 回顾性层直接访问 repository/DAO 或 raw-event join。
6. Room schema/entity/migration/index 变更。
7. 数值/参数/公式改动；新 parameter set；per-query 覆盖
   （**例外**：§3.2 授权范围内的 generator guard 异常类型化）。
8. 跨 chunk 累计上限；非有限值进入引擎；公开原因新增/替换。
9. 图表/新依赖/缓存/导出/Timeline/CPA/依从性与 timing 指标。
10. 任何写入路径；device/UI 测试（C-01 只跑 JVM + §13.8 定向 instrumentation）。

---

## 15. 与 C 阶段验收（C1–C9）及后续切片的关系

| 验收 | C-01 覆盖 | 后续 |
|---|---|---|
| C1 只消费权威实际事件 | ✅ | — |
| C2 显式区间/查询 API | ✅（visible + cursor + 资源门） | C-03 选择 UI |
| C3 上界 | ✅ | — |
| C4 前史显式 | ✅（EARLIEST_...） | — |
| C5 标记来自投影 | ✅（数据就绪） | C-04 标记计算 |
| C6 黄金值 | ⚠️ 部分（新增历史区间黄金值） | C-06 完整回归 |
| C7 DST/时区 | ✅（D1/D2/D3/T1） | C-07 扩展 |
| C8 模型估算 | —（model context 就绪） | C-05 UI 文案 |
| C9 fresh + 独立复审 | 实现轮执行 | C-09 |

---

## 16. 实现轮的验证与证据要求

- fresh JVM（`--rerun-tasks`）；计数取 JUnit XML；目标 task 无 `UP-TO-DATE`。
- 定向 `--tests` 会替换 `build/test-results` → 先定向、最后全量、再拷 XML。
- evidence：`docs/evolune/v1.7/evidence/c-01/` + `MANIFEST.sha256` + 覆盖断言 + 自哈希 + HEAD blob 0 mismatch。
- 独立复审重点：RC-1 逐 route 剂量语义（V1–V4）、matchKey 提取/失败分类（V7）、pk id（V5）、
  lookbackStart 精确（V6）、generator 专用异常与阈值 parity（V8）、防御 validator（V9）、
  366 天/安全带/溢出（V11–V14）、**1000 点网格 floor（V22–V27）**、取消与异常分类（V15–V17）、
  fresh undo（V18）、extras equality（V19）、body weight（V20）；R1–R3 已接受面 0 改动；zero-write。

---

## 17. 结论

C-01 规格完成（R4 corrected；R4.1 网格 floor 修正）：非正有限剂量逐 route 单值化；cursor 防御路径显式不可达；
matchKey 提取顺序与失败分类、lookbackStart、pk id 冻结；generator 100k guard 专用异常类型化 + 精确 catch；
366 天资源门 + 数值安全带 + **1000 点最小网格 floor（`max(ceil(hours*12.0)+1, 1000)`，
资源门后 checked 非饱和计算，短窗不判违例）**；异常分类与协程取消纪律显式化；独立复审 P3 全部吸收。
R1/R2/R3 已接受内容不变。**本轮不实现生产代码**；实现轮在独立复审后开工。
