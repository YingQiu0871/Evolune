# Evolune Phase 1 Batch 3B 代码审阅报告

**审阅日期**: 2026-08-03
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch3-repository-mappers`（HEAD: `0955cd8`）
**方式**: 只读审阅；未修改、暂存、提交任何文件；未开始 Batch 4

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 3（其中 1 项为报告已声明的 Route/Ester 过渡依赖；另 2 项为本轮新发现的防御性死代码/错误标签，均不可达、不阻塞）
- **是否可以提交**: 是
- **最大风险**: 无数据损坏、无 Slot ID 不稳定、无时间转换错误、无有损写 mapper、无生产接线、无敏感数据、无 schema 变更。最大风险仅为 `core.model` 对 `pk.Route`/`pk.Ester` 的已知过渡依赖（设计已接受）。

---

## Git and scope boundary

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch3-repository-mappers` ✓ |
| 未跟踪文件 | 恰好 11 个（5 生产 mapper + 5 测试 + 1 报告）✓ |
| 已跟踪文件修改 | 无（`git diff --name-status` 为空）✓ |
| `git diff --check` | 通过（无空白错误）✓ |
| 敏感数据/生成物 | 无 APK、数据库、日志、密钥、本地配置、schema 3 ✓ |
| Batch 3A 已提交 | ✓（`e35bf66`）+ 3A 审阅已提交（`0955cd8`）✓ |
| Batch 4 未开始 | ✓（无 `MIGRATION_2_3`、`version = 3`、`scheduled_dose_slots`）✓ |
| 报告所述 11 个文件 | 与工作树完全一致 ✓ |

---

## Findings

### F1 (P2) — `MedicationPlanEntityMapper.kt:39-45` 的 `catch (DateTimeException)` 不可达（防御性死代码）

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapper.kt:39-45`
- **问题**: `Instant.ofEpochMilli(createdAt)` 对任意 `Long` 输入都不会抛出异常：`Long` 毫秒范围 ±9.22e15 秒，远小于 `Instant` 的 ±3.15e16 秒范围；且即使溢出，`Instant.ofEpochMilli` 抛出的也是 `ArithmeticException` 而非 `DateTimeException`。
- **影响**: 该 catch 分支永远无法触发；`InvalidCreatedAt(EpochMillis)` 实际只在 `instantToEpochMillisForPersistence`（反向）路径有意义。无正确性影响。
- **设计依据**: ADR-015 要求 createdAt 的 Long→Instant 映射无时区依赖并明确失败；实现方向正确，只是防御分支不可达。
- **最小修复方向**: 可选：删除该 try/catch（保留 `instantToEpochMillisForPersistence` 作为反向边界的错误映射）；或改为捕获 `RuntimeException` 并注明不可达原因。不阻塞提交。
- **是否阻止提交**: 否

### F2 (P2) — `MedicationPlanEntityMapper.kt:95-98` slot 构造失败被错误标签为 `InvalidPlanInvariant(intervalDays)`

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapper.kt:95-98`
- **问题**: `ScheduledDoseSlot` 构造函数抛出的 `IllegalArgumentException` 被转换为 `MappingError.InvalidPlanInvariant(intervalDays)`，但该构造失败的真实原因是 position/planId 不变量或时间精度——与 `intervalDays` 无关。该分支实际不可达：position 取自非负列表索引、精度已在 L77-79 校验、planId 直接使用 `id`。
- **影响**: 仅错误分类标签不精确；无行为影响。
- **设计依据**: ADR-015 要求"不自动修正输入、显式失败"；实现方向正确。
- **最小修复方向**: 可选：为该分支引入 `InvalidSlotInvariant` 或直接删除 catch（不可达）。不阻塞提交。
- **是否阻止提交**: 否

### F3 (P2) — 已知过渡依赖：`core.model` 复用 `pk.Route`/`pk.Ester`

- **严重程度**: P2
- **文件**: `DoseEventEntityMapper.kt:54-72`、`MedicationPlanEntityMapper.kt`（经 `core.model` 传递）
- **问题**: 报告已声明的唯一 P2。Route/Ester 显式字符串映射复用 `pk` 包枚举，未移动枚举、未修改 `SimulationEngine`。
- **影响**: 符合 ADR-015 过渡取舍（"Batch 3 不移动枚举"）；作为技术债记录。
- **设计依据**: ADR-015、PHASE_1_DESIGN §6.5。
- **最小修复方向**: 后续独立 ADR 决定枚举迁移时机。
- **是否阻止提交**: 否

**未发现 P0/P1。** 错误类型覆盖度核对（全部满足）：

| 错误类别 | 类型 | 覆盖 |
|---|---|---|
| invalid timeH | `InvalidTimeH(value, cause: LegacyTimeError)` | ✓ |
| route | `InvalidRoute(value)` | ✓ |
| ester | `InvalidEster(value)` | ✓ |
| ExtraKey | `InvalidExtraKey(value)` | ✓ |
| ScheduleType | `InvalidScheduleType(value)` | ✓ |
| LocalTime | `InvalidTimeOfDay(value)` | ✓ |
| DayOfWeek | `InvalidDayOfWeek(value)` | ✓ |
| Slot ID | `InvalidSlot(position, cause: SlotIdError)` | ✓ |
| createdAt | `InvalidCreatedAt(CreatedAtInput)` | ✓ |
| Domain invariant | `InvalidDoseEventInvariant` / `InvalidPlanInvariant` | ✓ |

---

## DoseEvent mapper verdict

**符合设计。** `DoseEventEntityMapper.kt:12-52`：

- 只存在 `DoseEventEntity v2 -> core.model.DoseEvent` 单向映射 ✓
- id 原样、route/ester 显式解析（未知失败）、timeH 经 `LegacyTimeAdapter`（NaN/±Inf/±溢出 → `InvalidTimeH` 携带原因）✓
- extras 经 `toDomainExtras()` 全量显式转换，未知键失败、不丢键 ✓
- legacy 默认值正确：zoneId=null、localDate=null、slotId=null、source=LEGACY、status=RECORDED、revision=1 ✓
- 不调用旧 `toDoseEvent()`、不读系统时区/Locale、不用随机 UUID ✓
- 不存在 Domain→Entity 写函数 ✓
- 不接 Repository/DAO ✓

---

## MedicationPlan mapper verdict

**符合设计。** `MedicationPlanEntityMapper.kt`：

- 只存在 `MedicationPlanEntity v2 -> core.model.MedicationPlan` 单向映射，12 字段全覆盖 ✓
- slots：按 `timeOfDay` 原顺序生成；position=列表索引；planId=plan.id；Slot ID 用 UUIDv5 v1（`ScheduledDoseSlotId.generate`）；相同输入稳定（测试断言两次映射 ID 列表相等）；重复 localTime 保留（position 参与身份，ID 不同）；空列表保留；不排序/去重/修复/重编号 ✓
- LocalTime 解析：`LocalTime.parse` 要求 `HH:mm`（ISO_LOCAL_TIME 的时分冒号必填、小时两位宽），"08:30"/"00:00"/"23:59" 正确；"08:30:01"、"08:30:00.500" 解析成功后由 second/nano 检查明确拒绝为 `InvalidTimeOfDay`；"not-a-time" 解析失败同样拒绝 ✓
- DayOfWeek：整数 1..7 显式映射，0/8 明确失败 ✓
- 未知 ScheduleType/route/ester/ExtraKey 明确失败 ✓
- intervalDays 由 Domain 不变量拒绝（0/-1 → `InvalidPlanInvariant`）✓
- createdAt Long→Instant 无时区依赖 ✓（epoch millis 绝对时间）
- extras 全量保留；irrelevant legacy 字段原样保留（DAILY 保留 daysOfWeek/intervalDays 等）✓
- 不存在 Domain→Entity 写函数 ✓

---

## ExtraKey and ScheduleType verdict

**符合设计。** `ExtraKeyMapper.kt`、`ScheduleTypeMapper.kt`：

- 六个 ExtraKey 值在四个方向全部显式 `when` 映射：legacy String→Domain（未知失败）、Domain→legacy String、Domain→PK、PK→Domain ✓
- 全程无 `ordinal`、无依赖枚举声明顺序 ✓
- `toDomainExtras()` 迭代 map，未知键立即返回失败，不静默丢键 ✓
- 往返一致（测试覆盖全部六值 × 存储/PK 两组往返）✓
- ScheduleType：DAILY/WEEKLY/CUSTOM 双向显式映射，无 ordinal ✓
- 反向纯类型映射（`toLegacyStorageKey`、`toLegacyScheduleType`、`toPkExtraKey`、`toDomainExtraKey`）仅为类型完整性转换，不构造任何 Entity → **未形成 Domain→v2 Entity 写入路径** ✓（ADR-015 合规）

---

## Instant boundary verdict

**符合设计。** `MappingResult.kt:39-45` + `PersistenceInstantMapperTest`：

- `instantToEpochMillisForPersistence`：普通 Instant 精确转换；`Instant.MIN`/`Instant.MAX` → `InvalidCreatedAt(InstantValue)` 明确失败 ✓
- 不 clamp、不回退 0、不回退当前时间 ✓
- 该 helper 只做 Long 转换，不构造 Entity → 不构成 Domain→v2 写 mapper ✓
- 职责清晰：`CreatedAtInput` 区分 `EpochMillis` 与 `InstantValue` 两条失败来源 ✓

---

## Test quality verdict

**独立核实（非相信报告）**：实际解析 JUnit XML：

| 套件 | tests | failures/errors/skipped |
|---|---|---|
| DoseEventEntityMapperTest | 11 | 0/0/0 |
| ExtraKeyMapperTest | 7 | 0/0/0 |
| MedicationPlanEntityMapperTest | 20 | 0/0/0 |
| PersistenceInstantMapperTest | 3 | 0/0/0 |
| ScheduleTypeMapperTest | 2 | 0/0/0 |
| **合计** | **43 / 5 套件** | **0/0/0** |

与报告一致 ✓（审计拆分诊断测试的声明也与现状一致：未知 route/ester/ExtraKey 等均为独立 JUnit 方法）。

- 每个测试有实际断言，无空测试 ✓
- 固定 Slot UUID 使用硬编码预期值 `17d1fd14-9d70-5344-beaa-0b158c9f62f4` ✓；未调用被测函数生成预期 ✓
- ExtraKey 六值完整覆盖（四方向 + 两组往返 + 未知键）✓
- NaN/±Infinity 分别测试；正负溢出（±Double.MAX_VALUE）分别测试 ✓
- Instant.MIN/MAX 分别测试 ✓
- 重复 localTime 未被去重（position 区分 + ID 稳定断言）✓
- slots position 与 planId 逐项断言（L48-50）✓
- 空 slots、非法 LocalTime（格式/秒/纳秒）、非法 DayOfWeek（0/8）、非法 ScheduleType 均测试 ✓
- Locale/默认时区测试真实改变环境并在 finally 恢复 ✓
- 只使用合成数据；未降低 PK 1e-6 容差（PK 49 测试原样通过）✓
- 测试不依赖执行顺序 ✓

---

## Database and schema verdict

独立核实：

| 项 | 值 | 核对 |
|---|---|---|
| AppDatabase version | 2 | ✓（`AppDatabase.kt:19`） |
| exportSchema | true | ✓（`AppDatabase.kt:20`） |
| identityHash | `a8036e3f5ed6bb42d0e7289ac84039f3` | ✓（schema 2.json L5） |
| canonical schema SHA-256（HEAD blob） | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | ✓（`git cat-file blob` 经 cmd 重定向实测一致） |
| schema git diff | 零 | ✓（`git diff --name-status` 为空） |
| CRLF 工作树字节差异 | 仅行尾表示差异，非内容变更 | ✓（与 Batch 1/3A 记录一致） |
| MIGRATION_2_3 / version=3 / scheduled_dose_slots | 不存在 | ✓（grep 无命中） |

报告未将 CRLF 工作树哈希误写为 canonical 哈希——报告中两个值区分清楚。✓

---

## Architecture verdict

- 新增 mapper 为纯 Kotlin，依赖面仅 `core.model`、`core.time`、`pk`（Route/Ester 过渡）、`data.DoseEventEntity`/`MedicationPlanEntity`、`java.time` ✓
- 禁止模式扫描（mapper 生产目录）：`toEntity`、`fromDomain`、`DoseEventEntity(`、`MedicationPlanEntity(`、`ordinal`、`UUID.randomUUID`、`ZoneId.systemDefault`、`Locale.getDefault`、`Charset.defaultCharset`、`.toDoseEvent(` → **全部无命中** ✓
- 不依赖 Android Context、Room API、Cursor、Compose、Wearable、JSON DTO ✓
- 未修改 Entity、DAO、AppDatabase、schema、现有 Repository、MainActivity、ViewModel、UI、JSON、PK、Reminder、Widget、Wear、Gradle、设计文档 ✓
- 无 Domain→v2 Entity 写 mapper ✓
- Batch 3C（Repository 实现与生产接线）未提前实施 ✓

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。三个 P2（Route/Ester 过渡依赖、两处不可达防御代码）均不阻塞提交。

**提交前必须处理事项**：无（建议按惯例将本审阅报告与 11 个 Batch 3B 文件一并提交）。

**可留到后续批次事项**：
- F1/F2 的防御性代码清理（可随 Batch 4 mapper 重构时顺手处理）
- Route/Ester 枚举迁移（需独立 ADR）

**是否建议提交 Batch 3B**：是。提交建议信息：`feat: add read-only v2 entity mappers and explicit enum mappings`。

**是否建议在提交完成后进入 Batch 4 设计核验**：是。Batch 3A + 3B 的只读边界已锁定并通过独立验证；Batch 4（v2→v3 additive schema、Entity/DAO、migration test、schema 3 导出、CLI 修复工具）是 Batch 3C 的前置，建议按 PHASE_1_DESIGN §18 的矩阵顺序推进。

---

**验证命令实际执行清单**（均成功）：
1. `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` → PASS（5/43，JUnit XML 解析核实）
2. `:app:testDebugUnitTest --rerun-tasks` → PASS（全量）
3. `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` → PASS（PK 回归）
4. `:app:assembleDebug` → PASS
5. `:wear:testDebugUnitTest --rerun-tasks` → PASS
6. `:wear:assembleDebug` → PASS
7. `:app:lintDebug --rerun-tasks` → PASS（0 errors）
8. `:app:compileDebugAndroidTestKotlin --rerun-tasks` → PASS（仅编译，未执行 instrumentation）

未声称执行任何未实际运行的命令。未执行 `connectedDebugAndroidTest`。
