# V17-C-00 — Retrospective PK — Semantics Contract Landing

> 状态：`SEMANTICS FROZEN / DESIGN GATE`（**R4 corrected**；无生产代码改动；docs-only）
> Round：v1.7-C / **C-00**（契约落地、既有语义冻结、C-01 实现边界定义）
> 起始 HEAD：`97943722a5f37efb7dff8d4b39e500df2017db82`（B-04 APPROVE / **PHASE B CLOSED**）
> 权威：`APPROVE V17-C-00 SEMANTICS CONTRACT`（架构/产品门）；本文件负责原文落地，不重新裁决。
> R1/R2/R3（accepted，不再 reopen）：单一 History reader / 四条 limitation / 补丁语法 / derived-layer extras /
> full-lookback 架构；typed model provenance / cursor 契约 / typed DST 解析 / 毫秒端点 / 有限输入门 /
> exclusion taxonomy；公开 unavailable 四值 / 对齐谓词不溢出 / tier 精度 / guard 内部化 /
> unusable patch transition 保守失效。
> R4（本轮并入，独立 GLM 复审 6×P2）：
> ① 每条 route 的 finite 非正剂量行为冻结（§4.3）；② cursor 不可达路径显式化（§2.6）；
> ③ matchKey 提取顺序/失败分类 + lookbackStart + pk id 冻结（§3.1/§3.2/§8.4）；
> ④ 100k guard 专用异常机制授权（§3.2/§8.4）；⑤ 极端窗口资源边界 366 天 + 数值安全带（§2.4）；
> ⑥ 异常分类与协程取消纪律（§8.4）。
> R4.1（本轮窄修正，architect P2）：恢复冻结的 1000 点**最小网格 floor** ——
> `steps = max(ceil(durationHours * 12.0) + 1, 1000)`，仅在 366 天资源门后以 checked 非饱和算术计算，
> 短窗口不得判契约违例（§2.4）。
> R4.2（runtime contract conflict resolution，architect accepted）：
> ① 冻结负向 roundoff 校验容差 `RETROSPECTIVE_PK_NEGATIVE_ROUNDOFF_TOLERANCE_PG_ML = 1e-9`
> （`[-1e-9, 0)` 合法且保留原始 Double；`< -1e-9` / NaN / ±Inf 才违例；禁止任何 clamp/normalize，
> 与未改动引擎输出逐位一致，§4.3(h)）；
> ② 修正 PATCH present-and-finite-`<= 0` release rate 的 producing 分类：事件保留为引擎输入、
> 无 exclusion、贡献 0、**NOT producing**（即使 dose 为正），§4.3(b)(c)(d)(e)。
> 依据：`V17_SPEC.md` §4/§11/§12/§14 · `V17_PLAN.md` §6 · `V17_ACCEPTANCE.md` §3（C1–C9）·
> `V17_DATA_SEMANTICS.md`（Decision A–H）· `V17_A_01`–`V17_A_04` · `V17_B_00_INSIGHTS_SEMANTICS.md`
> 配套实现规格：[`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`](V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md)
> 证据：本轮未产生原始证据；本文件即 C-00 交付物（C-01 起建 `evidence/c-01/`）。

---

## 0. 范围与首要规则（Phase-C cardinal rules）

**性质**：Retrospective PK estimate（回顾性 PK **估算**）。曲线由**权威实际摄入**驱动，
**永远是模型推导**，不得呈现为实测血药浓度、诊断或治疗建议（SPEC §4 / §12 Invariant 3 / C8）。

**首要规则（冻结，七条不可协商）**：

```
1. 架构边界：唯一 historical reader = 既有 HistoryReadService；回顾性 PK 只消费其 all-history 入口产出的
   批准投影结果（AllAvailableHistory），不得新建并行 reader、不得直接访问 repository/DAO。
2. 数据边界：只消费 authoritative Room facts 经 Phase A 共享派生层后的 actual matched + unmatched
   recorded intake；不得建立第二套 medication truth。
3. 数值边界：数值只经既有 ParameterResolver + ThreeCompartmentModel + SimulationEngine
   （不改公式、不改参数）；非有限 model-driving 值绝不进入回顾性引擎（§4.3）。
4. 计划边界：不得混入 current-plan prediction / synthetic dose / unrecorded-occurrence 剂量。
5. 查询边界：visible/calculated interval 与可选 cursor 显式建模；cursor 估计不得使用端点 clamp（§2.6）；
   单个数值窗口受 366 天资源边界约束（§2.4）。
6. 读取边界：异常按精确类型分类；CancellationException 永远 rethrow；禁止 broad catch（§8.4）。
7. 写入边界：整条回顾性读路径 zero-write。
```

**冻结的 basis 标识符（enum 承载）**：

| 标识 | 类型 | 值 |
|---|---|---|
| `product` | 展示标识 | `Retrospective PK estimate` |
| `parameterSet` | `enum RetrospectivePkParameterSet` | `EVOLUNE_E2_PK_PARAMETER_SET_V1` |
| `parameterBasis` | `enum ParameterBasis` | `CURRENT_MODEL_PARAMETERS` |
| `bodyWeightBasis` | `enum BodyWeightBasis` | `CURRENT_SETTING_AT_QUERY_TIME` |
| `intakeBasis` | `enum IntakeBasis` | `RECORDED_ACTUAL_INTAKES` |

---

## 1. 数据来源（只读审计）

### 1.1 架构（冻结）

```
authoritative Room facts (dose_events)
        ↓  repository boundary（唯一出入口：HistoryReadService）
HistoryReadService（既有唯一 historical reader）
        │  ├─ all-history 入口 readAllAvailable(upperBoundInclusive, displayZone)
        │  └─ 复用同一 union / 分块 / MedicationOccurrenceGenerator / HistoricalProjectionBuilder
        ▼
AllAvailableHistory（lookbackStart? + upperBoundInclusive + HistoricalProjection）
        ▼
retrospective PK extraction（只读投影 entries；extras 来自 RecordedMedicationEvent.extras）
```

**禁止**：第二个并行 historical reader；回顾性 PK 直接查询 `DoseEventRepository`；DAO 访问；
复制 matcher/projection/completeness 逻辑。`HistoryRangeSource` seam 与 VM 接线不变；
all-history entry 由**同一个** `HistoryReadService` 实现。

| 来源 | 是否可进入 Retrospective PK | 说明 |
|---|---|---|
| `dose_events` | **是（唯一剂量来源）** | 只能经 `HistoryReadService` 读取 |
| `medication_plans` + `scheduled_dose_slots` | **仅经 HistoricalProjection** | 当前计划；不得生成剂量事件 |
| `HistoricalProjection` | **是（唯一入口）** | 禁止第二套 matcher；future 不参与 PK |
| `MedicationPlanPredictor` | **否（明确禁止）** | 回顾性路径不得调用 |
| `ReminderSkipStore` / Widget/Wear 缓存 | **否** | 非权威/派生 |

**已知事实（源码）**：

- 现行生产 PK 输入 = `getEventsForPk`：30 天窗口；<20 条回退最近 20（`RoomDoseEventRepository.kt:70-87`、`:252`）。
  不是回顾性正确性规则（§3）。
- 现行曲线在 `PkSimulationCalculator.kt:40-53` 加入 current-plan 预测；回顾性路径不得包含。
- 读取/存储异常层级（RC-6 源码核对）：`sealed class RepositoryStorageException : IllegalStateException`，
  子类 `CorruptAggregateException` / `RepositoryConstraintException` / `RepositoryPersistenceException`
  （`data/repository/RepositoryStorageException.kt:9-26`）；`runStorageOperation` 重抛
  `CancellationException` 与 `RepositoryStorageException`，SQLite* 包装为上述子类（`:33-53`）。
  该层级是 C-01 的精确分类依据（§8.4）。

---

## 2. 数值与参数基础（冻结）

### 2.1 参数集 `EVOLUNE_E2_PK_PARAMETER_SET_V1`

使用**当前**参数表整体（`pk/PKParameters.kt`、`ParameterResolver.kt`、`ThreeCompartmentModel.kt`、
`SimulationEngine.kt`、`SublingualTier.kt`、`Ester.kt`、`Route.kt`）。任何参数/公式改动必须走独立科学/
来源/回归评审；不得 per-event/per-query 覆盖。

### 2.2 体重（`CURRENT_SETTING_AT_QUERY_TIME`）

取查询时刻当前设置（`SettingsDataStore.kt:50/117`）。不得重建历史体重。
**请求边界校验（冻结）**：`isValidBodyWeight` = `weight.isFinite() && weight > 0.0 && weight <= 300.0`
（`SettingsDataStore.kt:63-66`）；不合法 → request/service precondition failure（fail-fast），
**永不进入引擎输入**。由 `BodyWeightBasis` enum 进入 typed model context。

### 2.3 时间数值（absolute Instant/epoch-hours）

`timeH = occurredAtEpochMillis / 3_600_000.0`（`LegacyTimeAdapter.kt:26/58-73`）。
事件时间一律来自权威 `occurredAt`。无法表示的时间 → typed exclusion（§8.1），不得崩溃。

### 2.4 窗口、端点精度、资源边界与本地时间/DST 解析（冻结；R3/R4）

**窗口**：`start < end`（严格），端点**毫秒对齐**，`nano % 1_000_000 == 0` 判定（不溢出）；
**epoch-millis 可表示性**在 service 数值边界检查（guarded `toEpochMilli()`），失败 →
`INVALID_QUERY_INTERVAL`（typed，不得抛出、不得映射为其它原因）。

**数值安全带（R4，冻结）**：数值窗口端点必须满足 `|epochMillis| ≤ 3_800_000_000_000_000`；
带外 → `INVALID_QUERY_INTERVAL`。这使毫秒端点 ↔ epoch-hours 往返等式的成立**可执行保证**，
而不是假设。

**单窗口资源边界（R4，冻结）**：

```
MAX_RETROSPECTIVE_PK_CALCULATED_DURATION = 366 days
duration = endInclusive - startInclusive        // 先做可表示性/安全带检查，再做溢出安全减法
duration <= 366 days  -> allowed
duration >  366 days  -> INVALID_QUERY_INTERVAL（不读历史、不调引擎）
```

这是**查询/资源安全边界，不是历史正确性截断**：窗口之前的全部权威历史仍作为 PK 前史被消费。
溢出（ArithmeticException 等）→ `INVALID_QUERY_INTERVAL`。**数值网格（R4.1 冻结，与现行生产规则一致）**：
回顾性数值网格保留现行生产规则 `steps = max(ceil(durationHours * 12.0) + 1, 1000)`；
仅在**资源门通过之后**以 checked 算术（`toLong`）计算，再断言 fits `Int`（否则内部契约违例），
**不得**使用饱和转换；**短窗口必须 floor 到 1000，不得判契约违例**。
日期全日 helper 的后果：`fromLocalDates` 最多接受 366 个日历日（含两端），367 日历日的窗口会被资源门拒绝。

**DateTimeException（P3 吸收）**：极端 `LocalDate`/`LocalDateTime` 运算可能抛 `DateTimeException`；
它属于 request/helper-boundary failure，必须映射为 `INVALID_QUERY_INTERVAL`（typed）或请求边界失败，
不得作为未类型化崩溃逃逸。

**日期全日 helper（确定性，保留）**：

```
fromLocalDates(startDate, endDate, displayZone):
    startInclusive = startDate.atStartOfDay(displayZone).toInstant()
    endInclusive   = endDate.plusDays(1).atStartOfDay(displayZone).toInstant().minusMillis(1)
```

**通用 LocalDateTime 解析契约（typed）**：

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
```

规则：LDT → 显式 `ZoneId`/`ZoneOffset` → `Instant` → epoch-hours → PK；gap → typed `GapAdjusted`；
overlap → typed `OverlapChoiceRequired`（必须显式选择偏移，不得静默 earlier/later）；
`OverlapChoiceRequired` 不得用于构造窗口；`atStartOfDay()` 仅保证日期全日 helper 的确定性。

### 2.5 无 `SimulationResult` clamp 泄漏（no clamp leakage）

回顾性数值结果 = `SimulationEngine` 原始输出，不得经过任何展示层裁剪（图表 Y 轴丢负值/±1e6 分数 clamp/
默认视窗/`SimulationResult.concentration()` 端点回退）。cursor 估计尤其不得使用该方法（§2.6）。
展示层可裁剪渲染，但不得写回数值结果。

### 2.6 cursor / query 契约（冻结；R4 显式化不可达路径）

- 公开请求不变量：`cursor == null || cursor ∈ [visibleWindow.startInclusive, visibleWindow.endInclusive]`；
  越界 = 请求构造失败（request-boundary exception）。**不得放宽**。
- 当前 C-01：`calculatedInterval == visibleWindow`。
- Available 暴露 `cursorEstimate`（cursor 为 null 时为 null）；估计只对 Instant-based series 插值。
- `QUERY_OUTSIDE_CALCULATED_INTERVAL` 是**防御性 typed 路径**：保留在冻结的公开 taxonomy 中，
  供未来 calculated-interval 实现与内部不变量检查使用；**通过当前正常构造的公开请求不可达**。
  服务在插值前调用内部 `RetrospectivePkIntervalValidator.validateCursor(cursor, calculatedInterval)`
  （返回该 reason ⇔ cursor 落在 calculated interval 之外）。

---

## 3. 摄入选择语义（intake basis，冻结）

### 3.1 计入集合

剂量输入 = **actual matched + unmatched recorded intake only**（四 provenance 全部 matched + unmatched）；
每个 authoritative eventId 恰好一次。禁止：unrecorded 折算剂量、plan prediction、因 provenance 低置信而丢弃。

**提取冻结（R4）**：`route = Route.valueOf(event.matchKey.routeKey)`、`doseMG = event.matchKey.doseAmount`、
`ester = Ester.valueOf(event.matchKey.medicationKey)`（ANTIANDROGEN 不解析 ester）；解析失败 →
`RetrospectivePkContractViolationException`（契约违例，不是 exclusion）。
映射到 pk 事件时 **`pk.DoseEvent.id` 必须等于 authoritative `eventId`**，不得依赖 pk 模型的随机 UUID 默认值。

### 3.2 全历史、上界与 consumed lookback（冻结）

- **全历史**：无下界；`occurredAt <= upperBoundInclusive` 的权威行进入 consumed 集合。
- **严格上界**：端点 inclusive（`<=`）。`AFTER_QUERY_WINDOW_END` 是读边界诊断（filter 发生在
  eligibility/patch/engine 之前），不是 exclusion，不触发 `EXCLUDED_RECORDED_INTAKES`。
- **lookbackStart（R4 冻结）**：
  ```
  lookbackStart = min(occurredAt) over consumed authoritative event set
                  （inclusive upper-bound read 之后、eligibility/exclusion 之前）
  null ⇔ consumed authoritative event set 为空
  ```
- **consumed lookback（显式）**：occurrence 上下文覆盖到上界：
  ```
  candidateDate(event) = persisted localDate ?: occurredAt.atZone(displayZone).toLocalDate()
  contextStartDate = min(candidateDate) - 1 day
  contextEndDate   = max(max(candidateDate), upperBound 的 display-zone 本地日期) + 1 day
  ```
  投影 horizon `now = upperBoundInclusive`。
- **前史**：窗口曲线起始条件由全部更早权威摄入决定（长尾 depot），不得固定截断。
- **MAX_RANGE_DAYS = 3660 不是回顾性正确性截断**；超 3660 天按分块生成，不得裁剪数据。
- **MAX_GENERATED_OCCURRENCES = 100_000（冻结；R4 异常机制授权）**：
  - 既有 `MedicationOccurrenceGenerator.generate()` **单次调用**的 guard；绝不截断；
  - **不存在**跨 chunk 累计上限；多个合法 chunk 合计可超过 100_000；
  - 触发时公开结果 = **`HISTORICAL_INPUT_UNAVAILABLE`**（完整历史输入无法证明），
    绝不返回 partial/truncated 数值结果；
  - **机制（R4 授权）**：C-01 实现轮可将该 guard 的 generic `check(...)` 替换为
    `MedicationOccurrenceGenerationLimitExceededException(message) : IllegalStateException(message)`
    （阈值/计数/控制流/生成语义**完全不变**，仅异常类型化）；
    `HistoryReadService.readAllAvailable()` 只捕获**该精确类型**并重包为 history 层内部诊断；
    服务把该诊断映射为 `HISTORICAL_INPUT_UNAVAILABLE`。禁止捕获 generic
    `IllegalStateException`/`Exception`/`Throwable` 做语义分类（§8.4）。

### 3.3 30 天 / 最近 20 条不是正确性规则

回顾性真值读取只经 all-history 入口，不得调用 `getEventsForPk`；现行 Home 曲线接口保持现状。

---

## 4. 历史 extras 边界（冻结）

### 4.1 derived-layer 扩展

```kotlin
enum class HistoricalMedicationExtraKey {
    CONCENTRATION_MG_ML, AREA_CM2, RELEASE_RATE_UG_PER_DAY,
    SUBLINGUAL_THETA, SUBLINGUAL_TIER, ANTI_ANDROGEN_TYPE
}

data class RecordedMedicationEvent(
    /* 既有字段不变 */
    val extras: Map<HistoricalMedicationExtraKey, Double> = emptyMap()  // 追加在末尾
)
```

- 恰好六个 key；生产 mapping 穷尽 + parity 测试；默认 `emptyMap()` 仅 source/test 兼容；
- 回顾性 PK 只消费投影 `event.extras`；不得用 retrospective-only raw-event join；
- **P3 告警（冻结备注）**：新增 `extras` 会改变该 data class 的 `equals`/`hashCode`；
  回归测试必须覆盖此前假设“无 extras”的手工构造期望对象。

### 4.2 key 语义（逐键）

| Key | 类别 | 现行行为（冻结） |
|---|---|---|
| `CONCENTRATION_MG_ML` / `AREA_CM2` | 非 model-driving | 数值模型不消费 |
| `RELEASE_RATE_UG_PER_DAY` | model-driving | 零级/一阶判据（§4.3） |
| `SUBLINGUAL_THETA` | model-driving | `coerceIn(0.0,1.0)`（有限值） |
| `SUBLINGUAL_TIER` | model-driving | §4.3（tier 精度） |
| `ANTI_ANDROGEN_TYPE` | 非 model-driving（事件整体排除） | 无数值意义 |

### 4.3 回顾性有限输入与剂量资格（冻结；R4 冻结非正剂量）

**（a）既有引擎行为（仅文档化，不修改）**：

| 输入 | 引擎分支/结果 |
|---|---|
| release rate 缺失 | 一阶 |
| 有限 rate `> 0` | 零级 |
| 有限 rate `<= 0` | 一阶 |
| rate `NaN` | 一阶 |
| rate `+Infinity` | 零级 + 非有限传播 |
| rate `-Infinity` | 一阶 |
| 剂量驱动模式下 `doseMG <= 0`（有限） | 贡献 0（`batemanAmount`/`analytic3C`/`dualAbs*` 的既有 guard） |

**（b）release-rate 资格（R3 冻结；R4.2 细化了 `<= 0` 的 producing 分类）**：
缺失 → 一阶消费 `doseMG`；有限 `> 0` → 零级（释放速率驱动）；有限 `<= 0` → **现行引擎路径原样保留**
（present-but-non-positive：参数走零级形状、`k1Fast = 0`，贡献为 0）；`NaN`/`±Infinity` → 排除
`UNSUPPORTED_OR_INCOMPLETE_EVENT`。

**（c）剂量资格（R4 冻结，R4.2 修正 producing 列）**：

| 情形 | 有限 `doseMG > 0` | 有限 `doseMG <= 0` | `doseMG` NaN/±Inf |
|---|---|---|---|
| 受支持非 patch（INJECTION EB/EV/EC/EN、ORAL 全部、SUBLINGUAL 全部、GEL 任意酯） | 正常模型行为；concentration-producing | **保留为合法引擎输入（保持零贡献 parity）；非 producing；无 exclusion；不触发 `EXCLUDED_RECORDED_INTAKES`** | 排除 `UNSUPPORTED_OR_INCOMPLETE_EVENT`；永不进入引擎 |
| PATCH_APPLY，release rate **缺失**（一阶） | producing（现行一阶行为） | **保留为合法引擎输入（零贡献 parity）；非 producing；无 exclusion** | 排除 `UNSUPPORTED_OR_INCOMPLETE_EVENT` |
| PATCH_APPLY，release rate 有限 `> 0`（零级） | `doseMG` 可为 0（有限且 ≥0）→ producing | 负值保留 R2/R3 规则（排除） | 排除（R2/R3 规则，不扩大） |
| PATCH_APPLY，release rate **present 且有限 `<= 0`** | **保留为合法引擎输入；无 exclusion；贡献 0（现行参数解析）；NOT producing** | 同左（保留；无 exclusion；非 producing） | 排除 `UNSUPPORTED_OR_INCOMPLETE_EVENT` |

**（d）concentration-producing 定义（冻结，R4.2 修正）**：usable 引擎输入中

```
producing ⇔ (PATCH_APPLY with finite release rate > 0)                       // 零级
          ∨ (PATCH_APPLY with release rate ABSENT and finite doseMG > 0)    // 一阶
          ∨ (supported non-patch route with finite doseMG > 0)
```

**present-but-finite-`<= 0` 的 release rate 不是 producing**：即使 `doseMG` 为正，也不得因剂量而
把它算作 producing（现行引擎在该路径贡献为 0）。

**（e）后果（冻结，R4.2 修正）**：若 authoritative recorded facts 全部为非 producing 事件
（finite 零/非正剂量，或 present-and-finite-`<= 0` release rate 的 PATCH），
则 `concentrationProducingEventIds.isEmpty()` → `NO_ELIGIBLE_RECORDED_INTAKES`，
**不得**输出零值 Available 曲线。

**（f）通用有限门（R3 措辞不变）**：**non-finite model-driving numeric value → exclude**；
不得把“有限但未知”的值当作排除理由。`SUBLINGUAL_TIER`：有限 `code.toInt()` ∈ {0,1,2,3} → 对应档位；
其它有限 → current STANDARD fallback（不排除）；NaN/±Inf → 排除。

**（g）特例保持**：`INJECTION × E2` → `UNSUPPORTED_CURRENT_MODEL_COMBINATION`（与剂量无关）。

**（h）输出保证（R4.2 冻结，替换“有限且非负”旧措辞）**：`Available.series` 的每个点必须 finite；
并且：

```
concentration >= 0            -> valid
-1e-9 <= concentration < 0    -> valid（tau=0 浮点消去伪影）；保留原始 Double 不变
concentration < -1e-9         -> RetrospectivePkContractViolationException
NaN / +Inf / -Inf             -> RetrospectivePkContractViolationException
```

`RETROSPECTIVE_PK_NEGATIVE_ROUNDOFF_TOLERANCE_PG_ML = 1e-9` 是**数值输出校验容差**：
不是 PK 模型参数、不是 parameter set 科学改动、不是 display clamp、不是 limitation enum 成员。
**禁止**对任何浓度点做 clamp/floor/normalize/drop/shift/改写；数值结果必须与未改动的引擎输出
逐位一致（唯一允许的转换仍是已批准的 timeH → Instant 表示转换）。未来 UI 可以在**渲染时**视觉裁掉
零点以下的 roundoff，但**不得**改动数值结果。

---

## 5. 生产资格（current production eligibility，逐格冻结）

| Route | Ester | 现行状态 | 数值后果 |
|---|---|---|---|
| `INJECTION` | EB / EV / EC / EN | **supported** | 双部分储库三室；剂量资格 §4.3(c) |
| `INJECTION` | E2 | **`UNSUPPORTED_CURRENT_MODEL_COMBINATION`** | 参数表缺 E2 → 贡献恒 0；命名排除 |
| `ORAL` | E2 | current E2 branch | `K_ABS_E2=0.32`，一室 Bateman，`k2` 不参与 |
| `ORAL` | EV | current EV branch | `K_ABS_EV=0.05`；一室路径不消费 `k2` |
| `ORAL` | EB / EC / EN | current E2-like fallback | `K_ABS_E2` 分支、`k2=0` |
| `SUBLINGUAL` | EV | current mixed path | `k2>0` → `dualAbsMixedAmount` |
| `SUBLINGUAL` | E2 / EB / EC / EN | current E2-like branch | `k2=0` → `dualAbsAmount` |
| `GEL` | 任意 | current ester-independent | `k1=0.022`、`F=0.05`；`AREA_CM2` 不参与 |
| `PATCH_APPLY` | 任意 | 零级 / 一阶两态 | §4.3(b)(c) |
| `PATCH_REMOVE` | 任意 | **control event only** | 贡献 0；APPLY 的终止控制 |
| `ANTIANDROGEN` | 任意 | **excluded from E2 PK** | 命名排除（§8.1） |

**冻结说明**：命名排除不得静默归零；“E2-like fallback”是现状事实；`GEL` 不区分 ester；
`PATCH_REMOVE` 必须保留在引擎输入（参与配对）。**状态**：只接受 `RECORDED`；其它值属契约违例（§8.4）。

---

## 6. 贴片语义与语法（PATCH，冻结）

### 6.1 现行引擎行为（不改）

```
wearH = (allEvents.firstOrNull { route == PATCH_REMOVE && timeH > applyStart }?.timeH ?: +∞) - applyStart
```

佩戴终点 = 传入列表顺序中第一个在其之后出现的 `PATCH_REMOVE`；无 remove 视为未移除。
`PATCH_REMOVE` 自身被跳过（`SimulationEngine.kt:148-154`）。确定性依赖传入列表顺序（§7）。

### 6.2 冻结语法与歧义规则

```
patch-history := (APPLY, REMOVE)*, APPLY?
```

| # | 规则 |
|---|---|
| G1 | transition instants 严格递增；**同 instant patch transition = ambiguous**（不得用 id 制造先后） |
| G2 | APPLY while another APPLY is active = ambiguous |
| G3 | orphan REMOVE = ambiguous |
| G4 | duplicate REMOVE = ambiguous |
| G5 | final APPLY may remain open（合法） |

排除范围：只排除受影响歧义链；其它可解析链与非贴片事件不受影响。
受影响链边界：仅当 instant **严格大于**上一 transition 且 open APPLY count == 0 时结算；否则累积为
affected group；扫描结束 group 有 failure → 整组排除（final open APPLY 且无 failure → 保留）。

**示例（C-01 逐行钉死）**：

| 输入 | 保留 | 排除 |
|---|---|---|
| `A` | A | — |
| `A, R` | A, R | — |
| `A, R, A` | A, R, A | — |
| `R` | — | R |
| `A, R1, R2` | A, R1 | R2 |
| `A=t, R=t` | — | A, R |
| `A1=t, A2=t` | — | A1, A2 |
| `A1, A2, R` | — | A1, A2, R |
| `A1, R1, A2, A3, R2` | A1, R1 | A2, A3, R2 |
| `A1, A2, R1, A3, R2` | — | A1, A2, R1, A3, R2 |
| `A1, R1=t, A2=t` | — | A1, R1, A2 |
| `A=t, R1=t+1h, A2=t+1h` | — | A, R1, A2 |

### 6.3 不可用 patch transition 的保守失效（R3 冻结）

无法表示/无法参与可信配对的 patch transition = **unusable transition**，在语法分组中按其 instant 作为
grammar failure 参与：unusable APPLY 不打开；unusable REMOVE 不关闭。其受影响因果链整体排除：

- unusable transition 自身 → `UNSUPPORTED_OR_INCOMPLETE_EVENT`；
- 该链其余成员 → `AMBIGUOUS_PATCH_PAIRING`。

**断言**：malformed/unrepresentable/incomplete `PATCH_REMOVE` 不得在预处理前“消失”而使其前置 APPLY
变成伪合法的 final-open episode；不得发明 patch-instance identity；不得丢弃无关合法链/非 patch 事件。

---

## 7. 确定性排序（冻结）

核心顺序：`occurredAt` 升序 → `id` 字符串序升序；`id` 仅用于确定性处理与报告；
同 instant patch transition 按 §6.2 G1 判歧义，不得用 id 制造时间先后。

---

## 8. Typed 结果语义

### 8.0 Typed model context（每结果必须携带）

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
)
```

basis 不是 String；`CURRENT_MODEL_PARAMETERS` / `CURRENT_SETTING_AT_QUERY_TIME` 属于 model context，
不是 limitation 成员。

### 8.1 排除 taxonomy（事件级，恰好五值）

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
```

| 情形 | reason |
|---|---|
| 抗雄事件 | `ANTIANDROGEN_IDENTITY_UNAVAILABLE` |
| `INJECTION × E2` | `UNSUPPORTED_CURRENT_MODEL_COMBINATION` |
| identity 非 KNOWN（**保留语义位；当前无生产来源**） | `UNKNOWN_OR_PARTIAL_IDENTITY` |
| non-finite model-driving 值 / 不可表示时间 / unusable patch transition | `UNSUPPORTED_OR_INCOMPLETE_EVENT` |
| 受影响贴片链其余成员（含歧义链） | `AMBIGUOUS_PATCH_PAIRING` |

**边界与违例**：`AFTER_QUERY_WINDOW_END` 仅边界诊断（非 exclusion、不触发 `EXCLUDED_RECORDED_INTAKES`）；
重复 eventId 是内部 typed 契约违例，不是 exclusion；
**matchKey route/ester 解析失败是契约违例，不是 `UNKNOWN_OR_PARTIAL_IDENTITY`**（§8.4）。

### 8.2 限制（limitations；恰好四条）

| 值 | 条件 |
|---|---|
| `EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE` | 每个 Available 结果都携带 |
| `UNRECORDED_OCCURRENCES_PRESENT` | 当且仅当完整 consumed PK lookback 投影含 ≥1 个 `UnrecordedHistoricalOccurrence`（含最后记录到上界之间） |
| `EXCLUDED_RECORDED_INTAKES` | 当且仅当存在至少一个 `RetrospectivePkExcludedEvent`（边界诊断不计；finite 零剂量非 producing 事件**不计**） |
| `AMBIGUOUS_PATCH_PAIRING` | 当且仅当存在 reason = `AMBIGUOUS_PATCH_PAIRING` 的排除（同时满足 EXCLUDED_RECORDED_INTAKES） |

### 8.3 不可用（公开 semantic taxonomy 恰四值）

```kotlin
enum class RetrospectivePkUnavailableReason {
    NO_ELIGIBLE_RECORDED_INTAKES,
    INVALID_QUERY_INTERVAL,
    QUERY_OUTSIDE_CALCULATED_INTERVAL,
    HISTORICAL_INPUT_UNAVAILABLE
}
```

| 条件 | 公开 reason |
|---|---|
| 无 authoritative recorded intakes；或有 recorded 但无 concentration-producing eligible event（含全为 finite 零/非正剂量） | `NO_ELIGIBLE_RECORDED_INTAKES` |
| 非法/反向/非毫秒/epoch-millis 不可表示；数值安全带（§2.4）之外；duration > 366 天；相关溢出 | `INVALID_QUERY_INTERVAL` |
| cursor 落在 calculated interval 之外（防御路径，§2.6） | `QUERY_OUTSIDE_CALCULATED_INTERVAL` |
| repository/all-history 读取失败（精确类型见 §8.4）、completeness 无法证明、单次 generator guard 阻止完整投影 | `HISTORICAL_INPUT_UNAVAILABLE` |

**冻结**：`HISTORICAL_INPUT_UNAVAILABLE` 绝不带 partial/truncated 数值输出；旧原因
（`NO_RECORDED_INTAKES`、`NO_ELIGIBLE_INTAKES`、`OCCURRENCE_GENERATION_LIMIT_EXCEEDED`、
`PERSISTENCE_RANGE_EXCEEDED`）不得作为公开 reason（仅内部诊断）；每个 `Unavailable` 携带 model context +
input summary（可空 lookbackStart/空 ids）。

### 8.4 异常分类与契约违例（R4 冻结）

**术语（P3 吸收）**：

- **request-boundary exception**：结构/value-object/请求构造非法（构造期 `require`、cursor 越 visible、
  overlap 未选 offset、sub-ms/反向窗口）→ `IllegalArgumentException`；
- **`INVALID_QUERY_INTERVAL`（typed）**：请求**成功构造后**在 service 数值/查询校验失败
  （可表示性、数值安全带、366 天资源门、溢出）。

**精确分类表（源码核对后冻结）**：

| 异常/信号 | 处置 |
|---|---|
| `CancellationException`（含 `runStorageOperation` 重抛的） | **永远 rethrow**（不得吞并、不得映射） |
| `MedicationOccurrenceGenerationLimitExceededException`（R4 授权的新类型；唯一来源 = generator guard） | history 层只捕获该精确类型 → 重包 `HistoricalOccurrenceLimitExceededException` → 服务映射 `HISTORICAL_INPUT_UNAVAILABLE` |
| `RepositoryStorageException`（sealed；`CorruptAggregateException`/`RepositoryConstraintException`/`RepositoryPersistenceException`）——精确的读取/存储失败类型 | `HISTORICAL_INPUT_UNAVAILABLE` |
| `RetrospectivePkContractViolationException` | fail-fast 传播；不得转 unavailable |
| 投影 completeness / union conflict 的普通 `IllegalStateException`；程序不变量失败 | fail-fast 传播；不得转 unavailable |

**禁止**（冻结）：围绕 history read 做语义分类时使用 `catch (Throwable)` / `catch (Exception)` /
`catch (IllegalStateException)`。注意 `RepositoryStorageException` **继承** `IllegalStateException`，
故分类必须按**精确子类型**。

**协程取消纪律（冻结）**：`currentCoroutineContext().ensureActive()` 至少出现在：
history read **前**、read **后**、`SimulationEngine.run` **前**、**后**。
不得为取消而修改 `SimulationEngine`；366 天资源门是对无挂起引擎调用的保护之一。

**其它契约违例（fail-fast）**：重复 eventId；投影丢行/多行/重复消费；`status != RECORDED`/不可映射；
entry `occurredAt > upperBoundInclusive`；matchKey route/ester 解析失败；series 出现 NaN/±Inf 或
低于 `-1e-9` 的负值（`[-1e-9, 0)` 的浮点消去伪影是合法值，保留原样，不属违例）。

---

## 9. 权威与写入边界（冻结）

1. **zero-write**：读路径不得调用 mutation；计数替身证明零写入且 reads > 0。
2. **no new medication truth store**；缓存（若未来出现）必须完全可由权威数据重建。
3. **no DAO bypass**：唯一 repository 出入口是 `HistoryReadService`。
4. **no PK formula changes**：黄金 AUC `23285.499354395688 ± 1e-9`、1441 点、六个固定采样必须保持。
   **例外（R4 授权）**：`MedicationOccurrenceGenerator.kt` 的 100k guard **仅异常类型化**
   （阈值/计数/控制流/生成语义不变），并需回归证明阈值与既有 `readRange` 行为不变。
5. **不改现有消费面**：Home / Wear / Widget 现行 PK 管线（含 `getEventsForPk`）保持原样。
6. **多查询一致性边界（P3 吸收）**：History 的 plan/slot/event 读取**不保证**是单次原子数据库快照；
   投影在同一读取批次内派生。C-01 不引入新写事务、不引入第二事实来源。

---

## 10. 明确禁止（anti-goals）

- 不得输出/暗示依从性、准时率、missed/skipped、处方真实性（B-00 禁用词表继续适用）。
- 不得由 `UnrecordedHistoricalOccurrence` 推导“当时未服药”或生成补偿剂量。
- 不得用当前计划重建“历史处方剂量/历史计划时间”。
- 不得反推/展示 patch-instance 配对。
- 不得把模型估算呈现为实测血药浓度（C8）。
- 不得用 String 代替 basis enum；不得用端点 clamp 制造 cursor 外推；不得把非有限值送入引擎。
- 不得把内部诊断原因暴露为公开 unavailable semantic reason；不得 broad-catch 分类。
- 不得把 finite 零/非正剂量当作 exclusion 或触发 `EXCLUDED_RECORDED_INTAKES`。
- 不得新增图表依赖、Compose 图表语义或 UI（属 C-05）。

---

## 11. 与 V17_ACCEPTANCE §3 的映射（C1–C9）

| # | 验收项 | 本契约落实 | 证明 |
|---|---|---|---|
| C1 | 只消费权威实际事件 | §0/§1/§3.1 | C-01 anti-plan 测试 |
| C2 | 区间显式 | §2.4/§2.6/§3.2 | C-01 API + 测试 |
| C3 | 上界存在 | §3.2（inclusive；边界非 exclusion） | C-01 边界测试 |
| C4 | 前史显式 | §3.2 + EARLIEST_... | C-01 数值测试 |
| C5 | 标记来自投影 | §1.1/§3.1 | C-01 接线（标记本体 C-04） |
| C6 | 黄金值 | §9.4 | C-01/C-06 |
| C7 | DST | §2.4 | C-01 专项 |
| C8 | 模型估算 | §0/§8.0/§10 | C-05 UI |
| C9 | fresh + 独立复审 | C-01 §13/§16 | C-01 实现轮 |

---

## 12. 变更管理

| 想改什么 | 需要什么 |
|---|---|
| 参数/公式（V1 表值） | 独立科学 + 来源 + 回归评审，新 parameterSet（V2） |
| extras 集合或派生层传播 | 新数据契约决策 + 全 writer 审计 |
| basis 类型改回 String | 明确禁止 |
| 公开 unavailable taxonomy | 明确禁止扩展/替换四值；内部诊断可增 |
| 窗口对齐谓词改回 epoch-millis 转换判定 | 明确禁止 |
| 有限未知 tier code 排除 | 明确禁止（STANDARD fallback） |
| finite 非正剂量改为 exclusion | 明确禁止（R4：保留零贡献 parity，非 producing，无 exclusion） |
| present-and-finite-`<= 0` release rate 的 PATCH 改为 producing | 明确禁止（R4.2：贡献 0，NOT producing，即使 dose 为正） |
| 对浓度点做 clamp/floor/normalize（含把 roundoff 改写成 0.0） | 明确禁止（R4.2：保留原始 Double；唯一允许转换仍是 timeH → Instant） |
| 改动 roundoff 校验容差（`1e-9`）或把它当成模型/科学参数 | 明确禁止；仅可在新契约轮修订 |
| 366 天资源门 / 数值安全带放宽 | 需 C-00 修订（资源边界，非正确性） |
| 跨 chunk 累计上限 | 明确禁止 |
| 非有限值进入引擎 | 明确禁止 |
| generator guard 换用非专用异常或 broad catch | 明确禁止（R4：仅专用类型化） |
| patch-instance identity | 新契约 + schema 立项 |
| 计划预测混入回顾曲线 | 不允许 |

---

## 13. 结论

`V17-C-00` 冻结完成（R4 corrected；R4.1 网格 floor 修正；R4.2 runtime contract conflict resolution）：
非正有限剂量行为逐 route 单值化（保留零贡献 parity、非 producing、无 exclusion、不触发
`EXCLUDED_RECORDED_INTAKES`；全零贡献事实 → `NO_ELIGIBLE_RECORDED_INTAKES`）；
PATCH present-and-finite-`<= 0` release rate 明确为非 producing（贡献 0，即使 dose 为正）；
`Available.series` 采用冻结容差规则（`[-1e-9, 0)` 合法且保留原始 Double，`< -1e-9`/NaN/±Inf 违例，
禁止任何 clamp/normalize，与未改动引擎输出逐位一致）；数值网格保留现行
`max(ceil(hours*12.0)+1, 1000)` 规则（资源门后 checked、无饱和、短窗 floor）；cursor 防御路径显式不可达但不移除；
matchKey 提取顺序/失败分类、lookbackStart、pk id 冻结；100k guard 专用异常类型化 + 精确 catch；
366 天资源门 + 数值安全带；异常分类与取消纪律单值化。R1/R2/R3 已接受内容不变。
C-01 production 实现已暂停，等待本轮 hotfix 独立复审。
