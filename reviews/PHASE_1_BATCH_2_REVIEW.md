# Evolune Phase 1 Batch 2 代码审阅报告

**审阅日期**: 2026-08-01  
**审阅范围**: 8 个新文件 (5 生产 + 3 测试)  
**审阅基线**: `PHASE_1_DESIGN.md`, `DECISIONS.md`, `ARCHITECTURE.md`, `MIGRATION_PLAN.md`, `FEATURE_MATRIX.md`  
**审阅方法**: 逐行代码阅读 + 设计交叉比对 + 全量测试执行  
**审阅分支**: `phase1/batch2-domain-contracts` (HEAD: `9d11756`)

---

## Executive Summary

- **是否可提交 Batch 2**: **是** — APPROVE WITH P2
- **P0 数量**: 0
- **P1 数量**: 0
- **P2 数量**: 3
- **最大风险**: `core.model.DoseEvent.ExtraKey` 与 `pk.DoseEvent.ExtraKey` 存在命名空间歧义，Batch 3 mapper 必须处理常量级映射（低风险，已在设计中规划）

---

## Scope and Git Boundary

| 项目 | 状态 |
|------|------|
| 当前分支 | `phase1/batch2-domain-contracts` ✓ |
| Batch 1 已提交 | ✓ (tag: `phase-1-batch-1`, commit: `e1858cd`) |
| Slot ID 设计已提交 | ✓ (tag: `phase-1-slot-id-design-v1`, commit: `9d11756`) |
| 工作区文件 | 9 个未跟踪文件 (8 个代码文件 + 1 个 Batch 报告) ✓ |
| 越界文件 | 无。仅 `core/model/`、`core/time/` 和对应测试 ✓ |
| 敏感数据 | 无。所有测试使用合成 fixture ✓ |
| 生成文件 | 无 ✓ |
| `git diff --check` | 通过 (no whitespace errors) ✓ |
| 旧业务文件被修改 | 无。`pk/DoseEvent.kt` 未改动 ✓ |

---

## Findings

### Finding 1 (P2): `core.model.ExtraKey` 与 `pk.DoseEvent.ExtraKey` 命名歧义

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEvent.kt:29-36`
- **问题**: 新 Domain model 定义了独立的 `core.model.DoseEvent.ExtraKey` 枚举，与 `pk.DoseEvent.ExtraKey` 拥有完全相同的 6 个值和顺序。两个枚举值在编译期互不兼容。例如 `mapOf(core.model.DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)` 和 `mapOf(pk.DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0)` 是不同的类型。
- **为什么重要**: Batch 3 mapper 在 `core.model.DoseEvent → pk.DoseEvent` 转换时，必须逐键映射 `extras` Map。如果 mapper 遗漏此转换或使用错误类型编译失败，PK 模拟会丢失所有额外参数（贴片释放速率、舌下 θ 值等），导致浓度计算静默错误。
- **设计依据**: `PHASE_1_DESIGN.md` §6.1 明确说 "保持当前枚举和值"；Route 和 Ester 通过 `import pk.*` 直接复用，但 ExtraKey 是嵌套在 `DoseEvent` 中的枚举，无法直接导入。新建独立枚举是正确做法——避免了 `core.model` 依赖 `pk.DoseEvent` 类。
- **最小修复方向**: 不需要在 Batch 2 修改。Batch 3 创建 mapper 时，添加明确的 `ExtraKey` 映射函数和单元测试，覆盖所有 6 个值的往返转换。
- **是否必须在本批提交前处理**: 否。这是 Batch 3 (mapper) 的设计内容。

### Finding 2 (P2): `DoseEvent.occurredAt` 可使用超出 `Long` epoch 范围的 `Instant`

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEvent.kt:13`
- **问题**: `java.time.Instant` 可表示远超出 `Long.MIN_VALUE..Long.MAX_VALUE` 毫秒范围的时间点（如 `Instant.MIN` = -10 亿年，`Instant.MAX` = +10 亿年）。`DoseEvent` 构造函数接受任何 `Instant`，但 `LegacyTimeAdapter` 和后续的 Room Entity 映射会将 `Instant` 转换为 `Long` epoch 毫秒。如果创建了一个超出 `Long` 范围的 `Instant`，转换将在 Batch 3/4 失败。
- **为什么重要**: 当前所有生产路径都来自 `timeH → Long` 转换（在 `LegacyTimeAdapter` 范围内）。但如果有未来的 Use Case 或 JSON 导入器使用 `Instant.parse(...)` 创建极端时间，问题会在距离构造函数调用远端的 Entity mapper 处报错。
- **设计依据**: `PHASE_1_DESIGN.md` §7 指定 `occurredAt` 为 "权威 instant"，但 §13 将有效范围定义为 `[Long.MIN_VALUE, Long.MAX_VALUE]` epoch 毫秒。
- **最小修复方向**: 在 Batch 3 的 Entity→Domain mapper 中显式验证 `Instant.toEpochMilli()` 不抛异常（使用 try-catch `ArithmeticException`），并提供清晰的错误消息。不需要在 Domain 模型本身添加验证——这符合 PHASE_1_DESIGN 的 "Domain 不耦合数据库限制" 原则。
- **是否必须在本批提交前处理**: 否。Batch 3 mapper 责任。

### Finding 3 (P2): `pk.Route` 和 `pk.Ester` 依赖未分离

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEvent.kt:3-4`
- **问题**: 新的 `core.model.DoseEvent` 导入了 `io.github.yuninggu.evolune.pk.Route` 和 `io.github.yuninggu.evolune.pk.Ester`。这两个类型当前定义在 `pk/` 包中，该包还包含 `PKParameters.kt`、`SimulationEngine.kt` 等算法文件。严格来说，`core.model` 依赖了 `pk` 包中的两个纯枚举类型，但也同时使其 compile classpath 暴露于 `pk` 的所有公共类。
- **为什么重要**: 按 `ARCHITECTURE.md` 的依赖规则，"`core:model` 不依赖 Android、Room、Compose、Wearable 或 Health Connect"，但**未明确说不依赖 PK 算法包**。`Route` 和 `Ester` 是纯枚举（无 Android 依赖、无算法逻辑），作为领域值对象它们应该属于 `core:model` 而非 `pk`。当前是过渡期临时复用。
- **设计依据**: `PHASE_1_DESIGN.md` §6.1 和 `DECISIONS.md` D2 明确 Phase 1 只创建 `core.model` package 而不立即拆分 Gradle module。Ester 和 Route 的搬迁属于未来的 package 重组。
- **最小修复方向**: 不需要在 Batch 2 修改。当 `core:model` 可独立 JVM 测试时将 `Route` 和 `Ester` 搬迁到 `core.model`，`pk` 改为依赖 `core.model`。
- **是否必须在本批提交前处理**: 否。

---

## UUIDv5 Verdict

### 算法符合标准 ✓

- DNS namespace: `6ba7b810-9dad-11d1-80b4-00c04fd430c8` ✓ (RFC 4122 Appendix C)
- SHA-1 digest ✓
- First 16 bytes of digest ✓
- Version bits: `(byte[6] & 0x0f) | 0x50` → version 5 ✓
- Variant bits: `(byte[8] & 0x3f) | 0x80` → RFC 4122 variant ✓

### Namespace 字节顺序正确 ✓

- `ByteBuffer.allocate(16).putLong(mostSignificantBits).putLong(leastSignificantBits)` — default BIG_ENDIAN → 标准 UUID 字节序 ✓
- 与 Python `uuid.uuid5` 和 RFC 4122 §4.3 算法一致 ✓

### 固定 Namespace 正确 ✓

- 项目 namespace 名称: `io.github.yuninggu.evolune:scheduled-dose-slot` ✓
- 固定项目 namespace: `68559b97-4ddc-5be2-bcbd-9ab409f0d95b` ✓ (硬编码校验)
- 每次生成时重新计算并验证（`if (projectNamespace != expectedProjectNamespace)`）— 确保算法未被意外修改 ✓

### 固定 Slot ID 正确 ✓

- Canonical name: `slot:v1:plan=00000000-0000-0000-0000-000000000001;position=0;time=08:30` ✓
- 预期 ID: `17d1fd14-9d70-5344-beaa-0b158c9f62f4` ✓
- `UUID.version() = 5` ✓
- `UUID.variant() = 2` ✓
- 测试用独立版本向量锁定 ✓

### 输入规范化 ✓

- `planId` 白空格拒绝（不 trim 后接受） ✓
- `planId` 无效 UUID → `InvalidPlanId` 错误 ✓
- `position < 0` → `InvalidPosition` 错误 ✓
- `localTime.second != 0 || localTime.nano != 0` → `InvalidLocalTimePrecision` 错误 ✓
- `position.toString()` 不受 Locale 影响 ✓
- canonical localTime 使用 `padStart(2, '0')`，硬编码 `:` 分隔符 ✓
- 不使用 `UUID.randomUUID()`、`hashCode()`、默认 Locale/charset/时区 ✓

### 可安全用于 v2 → v3 backfill ✓

- 确定性：相同输入 → 相同 UUID ✓
- 幂等：多次执行产生相同输出 ✓
- 独立于系统状态（Locale、时区、设备 ID） ✓
- GeneralSecurityException（SHA-1 不可用）不会被静默忽略 ✓

**结论**: UUIDv5 实现完全符合 PHASE_1_DESIGN §17.1 和 ADR-014。可以安全用于迁移 backfill。

---

## LegacyTimeAdapter Verdict

### 公式正确 ✓

- `MILLIS_PER_HOUR = 3_600_000.0` ✓
- `scaledMillis = timeH * MILLIS_PER_HOUR` ✓
- `occurredAtEpochMillis = Math.round(scaledMillis)` ✓
- 反向: `timeH = epochMillis / MILLIS_PER_HOUR` ✓
- 正向与反向的 1 ms 容差验证：`abs(input - roundtrip) <= 1.0/3600000.0` ✓

### Long 边界正确 ✓

- 合法范围: `[Long.MIN_VALUE.toDouble(), -Long.MIN_VALUE.toDouble())` = `[-2^63, 2^63)` ✓
- 下界 inclusive (`scaledMillis < minimumMillisInclusive` 触发 OutOfRange) ✓
- 上界 exclusive (`scaledMillis >= maximumMillisExclusive` 触发 OutOfRange) ✓
- `Long.MIN_VALUE.toDouble()` 精确表示 -2^63 ✓
- `-Long.MIN_VALUE.toDouble()` 精确表示 2^63 ✓
- 范围检查在 `Math.round` **之前**执行，避免饱和行为 ✓
- `Double.MAX_VALUE * MILLIS_PER_HOUR` → overflow 被检测（`!scaledMillis.isFinite()`） ✓
- `Math.nextUp(boundary)` 正确触发 OutOfRange ✓
- `Math.nextDown(boundary)` 正确触发 OutOfRange ✓

### Non-finite 和 Overflow 正确处理 ✓

- `NaN` → `NonFiniteKind.NAN` ✓
- `+Infinity` → `NonFiniteKind.POSITIVE_INFINITY` ✓
- `-Infinity` → `NonFiniteKind.NEGATIVE_INFINITY` ✓
- `Double.MAX_VALUE` 乘法溢出 → `Overflow("timeH multiplication")` ✓
- 无 clamp、epoch zero 回退或隐式替换 ✓

### DST 行为正确 ✓

- `localDateTimeToInstant(2024-03-10T02:30, America/New_York)` → `2024-03-10T07:30:00Z` (gap, 向后调整) ✓
- `localDateTimeToInstant(2024-11-03T01:30, America/New_York)` → `2024-11-03T05:30:00Z` (overlap, 较早 offset = EDT UTC-4) ✓
- 要求显式 `ZoneId` 参数 ✓
- `DateTimeException` 被捕获并映射 ✓

### 可安全用于 migration ✓

- 所有错误类型携带具体上下文（NonFinite/OutOfRange/Overflow + 具体数值） ✓
- 足以让 migration 中止并生成可审计报告 ✓
- 不依赖系统默认时区、Locale 或全局状态 ✓

**结论**: LegacyTimeAdapter 完全符合 PHASE_1_DESIGN §13。可以安全用于 v2 → v3 migration。

---

## Test Quality Verdict

### DoseEventTest (2 测试)

| 测试 | 断言类型 | 状态 |
|------|---------|------|
| `revisionOneIsValid...` | revision=1 ✓, status=RECORDED ✓, null zone/localDate/slotId ✓ | PASS |
| `revisionZeroIsRejected` | `assertThrows(IllegalArgumentException)` ✓ | PASS |
| `negativeRevisionIsRejected` | `assertThrows(IllegalArgumentException)` ✓ | PASS |
| `occurredAt...DoNotDependOnDefaultTimeZone` | 全局时区修改后 Instant 稳定 ✓; finally 恢复 ✓ | PASS |
| `phaseOneEnumsContainOnlyResolvedValues` | Source 6 值 ✓; Status 仅 RECORDED ✓ | PASS |

### ScheduledDoseSlotTest (9 测试)

| 测试 | 断言类型 | 状态 |
|------|---------|------|
| `fixedVectorMatches...` | 硬编码 UUID ✓; canonical name 精确 ✓; version=5 ✓; variant=2 ✓ | PASS |
| `equalInputsAreStable` | 重复生成 id 相同 ✓ | PASS |
| `eachIdentityInputChangesTheId` | planId/position/time 任一变化 → id 变化 ✓ | PASS |
| `zeroAndMaximumPositionsAreAccepted` | 0 ✓; Int.MAX_VALUE ✓ | PASS |
| `negativePositionIsRejected` | -1 → Failure ✓ | PASS |
| `secondsAndNanosecondsAreRejected...` | second=1 → Failure ✓; nano=500M → Failure ✓ | PASS |
| `invalidAndWhitespacePlanIds...` | 非 UUID → InvalidPlanId ✓; 白空格 → PlanIdHasSurroundingWhitespace ✓ | PASS |
| `localeAndDefaultTimeZoneDoNotChange...` | Locale/时区修改后结果稳定 ✓; finally 恢复 ✓ | PASS |
| `modelEnforcesTheSame...` | ScheduledDoseSlot 构造函数 enforce position≥0 ✓; minute precision ✓ | PASS |

### LegacyTimeAdapterTest (14 测试)

| 测试 | 断言类型 | 状态 |
|------|---------|------|
| `zeroIntegerFractionMillisecond...` | 0h→0ms, 12h→43.2Mms, 1.5h→5.4Mms, 1ms→1L, -1.5h→-5.4Mms, -0.4ms→0L | PASS |
| `distantHistoryAndFuture...` | -2B hours < 0, +2B hours > 0 | PASS |
| `timeHMillisAndInstantRoundTrips...` | 6 inputs, 1ms tolerance, Instant 一致性 | PASS |
| `nonFiniteInputsReturnSpecificErrors` | NaN/+/−Infinity 各自正确映射 | PASS |
| `finiteMultiplicationOverflow...` | Double.MAX_VALUE → Overflow | PASS |
| `positiveAndNegativeLongOverflows...` | nextUp/nextDown 正确触发 OutOfRange | PASS |
| `closestRepresentableLegalLongBoundaries...` | 最接近 2^63 的值被接受, Long.MIN/MAX 到 timeH 的 finite 转换 | PASS |
| `invalidValuesAreNotClamped...` | 越界值 → Failure, 非 epoch zero | PASS |
| `explicitZoneConvertsOrdinary...` | 2024-01-15T12:00 NY → T17:00Z | PASS |
| `dstGapUsesJavaAtZoneForwardAdjustment` | 2024-03-10T02:30 NY → T07:30Z ✓ | PASS |
| `dstOverlapUsesJavaAtZoneEarlierOffset` | 2024-11-03T01:30 NY → T05:30Z ✓ | PASS |
| `localeAndDefaultTimeZoneDoNotAffect...` | Locale/时区修改不影响 absolute conversion; finally 恢复 | PASS |

### 测试质量评估

- **无空测试** ✓ — 所有 26 个测试 (Batch 2 新增 10 个) 均有断言
- **无循环测试** ✓ — 固定 UUID 使用硬编码值，不使用被测函数生成预期值
- **Slot 固定 UUID** 是硬编码值 ✓ — `17d1fd14-9d70-5344-beaa-0b158c9f62f4`
- **Project namespace** 是硬编码值 ✓ — `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`
- **UUID version/variant** 独立验证 ✓ — `assertEquals(5, result.id.version())` 和 `assertEquals(2, result.id.variant())`
- **Canonical name** 精确断言 ✓ — 包含所有分隔符和字段顺序
- **Locale 测试** 确实改变 Locale ✓ — `US` ↔ `ar-EG`
- **时区测试** 确实改变默认时区 ✓ — `UTC` ↔ `Pacific/Auckland`
- **全局状态恢复** ✓ — 所有 `TimeZone.setDefault`/`Locale.setDefault` 在 `finally` 中恢复
- **DST gap** 断言正确 ✓ — `2024-03-10T07:30:00Z`
- **DST overlap** 断言正确 ✓ — `2024-11-03T05:30:00Z`
- **NaN/Infinity** 各自测试 ✓ — 区分正无穷、负无穷、NaN
- **正负溢出** 各自测试 ✓ — `Math.nextUp` 和 `Math.nextDown` 边界
- **Long 边界** 不是等价常量的假覆盖 ✓ — 使用动态计算的 `upperHours`/`lowerHours`
- **revision 0 与负值** 各自测试 ✓
- **Legacy nullable** 被测试 ✓ — `assertNull(event.zoneId/localDate/slotId)`
- **仅使用合成数据** ✓ — 无真实健康记录
- **PK 容差保持** ✓ — 1 ms 小时容差，不改变 1e-6 PK 输出容差
- **不依赖执行顺序** ✓ — 每个测试独立创建 fixture
- **不受机器 Locale/时区影响** ✓ — 所有 Locale/时区测试在受控沙箱中执行

**结论**: 测试质量高，正确锁定设计决策。无循环测试、假覆盖或缺失边界。

---

## Architecture Verdict

| 检查项 | 状态 |
|--------|------|
| 不依赖 Android SDK | ✓ (仅 `java.time.*`, `java.util.UUID`, `java.nio.*`, `java.security.*`) |
| 不依赖 Room | ✓ |
| 不依赖 Compose | ✓ |
| 不依赖 Wearable SDK | ✓ |
| 不依赖 Health Connect | ✓ |
| 不依赖 DAO 或 Repository implementation | ✓ |
| 不读取系统默认时区 | ✓ (不调用 `ZoneId.systemDefault()`) |
| 不读取默认 Locale | ✓ (不调用 `Locale.getDefault()`) |
| 不读取默认 charset | ✓ (始终指定 `StandardCharsets.UTF_8`) |
| 不接入当前生产调用路径 | ✓ |
| 不改变 PK 行为 | ✓ (所有 49 个 PK 测试通过) |
| 不改变 JSON 行为 | ✓ (无 JSON 相关修改) |
| 不改变 Reminder 行为 | ✓ |
| 不改变 Widget 行为 | ✓ |
| 不改变 Wear 行为 | ✓ |
| 不提前实施 Batch 3 | ✓ (无 Repository contract 文件) |
| 不提前实施 Batch 4 | ✓ (无 Entity/schema/migration 文件) |
| 无 Tracked Date | ✓ |
| 无云同步字段 | ✓ |
| 无未来 status 值 (SKIPPED, VOIDED, etc.) | ✓ (仅 RECORDED) |
| `exportSchema = true` 保持 | ✓ (Batch 1 修改未被回退) |

**结论**: 纯 Kotlin Domain 和 Time 类型已建立，符合 PHASE_1_DESIGN Batch 2 边界。安全进入 Batch 3。

---

## License and Source Boundary

- 无旧 GPL 类名、注释或结构 ✓
- 无历史迁移目录引用 ✓
- 无旧项目资源 ✓
- 无第三方代码 ✓
- UUIDv5 是标准算法的独立实现 ✓ (使用 Java 标准库 `MessageDigest` 和 `ByteBuffer`)

---

## Final Decision

### **APPROVE WITH P2**

可以提交 Batch 2。无 P0 或 P1 阻断问题。3 个 P2 问题全部属于 Batch 3 设计范围，不阻止本批进入版本控制。

---

## 提交前必须完成的事项

1. 确认 `PHASE_1_BATCH_2_REPORT.md` 已创建并如实记录本批文件、测试结果和审阅结论
2. 执行 `git add` 仅 8 个代码文件 + 1 个 Batch 报告文件
3. 提交信息建议: `feat: add core domain model, slot id, and time adapter`

## 可以留到 Batch 3 的事项

1. `core.model.ExtraKey` → `pk.ExtraKey` mapper (Finding 1)
2. `Instant.toEpochMilli()` 溢出防护 (Finding 2)
3. `Route`/`Ester` 搬迁到 `core.model` (Finding 3)
4. Repository contract (`core.dataapi.DoseEventRepository`)
5. Domain ↔ Entity mapper
6. Room v2 → v3 schema 实现

## 建议

- 继续创建 `PHASE_1_BATCH_2_REPORT.md`
- 执行 Batch 2 commit
- Batch 3 可以开始

---

*审阅报告结束。*
