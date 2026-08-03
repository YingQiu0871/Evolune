# Evolune Phase 1 Batch 4A-0 代码审阅报告

**审阅日期**: 2026-08-03
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch4-v3-schema-migration`（HEAD: `c976c9e`）
**方式**: 只读审阅；未修改、暂存、提交、打标签任何文件；未开始 Batch 4A-1；未创建 Python 工具

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 1（`InvalidTimeHStorageClass` 缺少行级上下文；不阻塞）
- **是否允许提交**: 是
- **最大风险**: 无。无 Slot ID 不稳定、无时间静默截断/舍入/修复、timeH 算法完全复用 `LegacyTimeAdapter`、未提前修改数据库/schema、无敏感数据。

---

## Git and scope boundary

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch4-v3-schema-migration` ✓ |
| 未跟踪文件 | 恰好 7 个（3 生产 + 3 测试 + 1 报告）✓ |
| 已跟踪文件修改 | 无（`git diff --name-status`/`--stat` 为空）✓ |
| `git diff --check` | 通过 ✓ |
| 敏感数据/生成物 | 无 APK、数据库、日志、patch、密钥、本地配置、schema 3 ✓ |
| 设计提交在历史中 | `c976c9e`（Batch 4 migration design）+ tag `phase-1-batch-4-design-v1` ✓ |
| Batch 3B 已提交并审阅 | `e4feb53`（实现）+ `ca5fd55`（审阅）✓ |
| Batch 4A-1 未开始 | ✓（无 MIGRATION_2_3、version=3、schema 3）✓ |
| 报告所述 7 个文件 | 与工作树完全一致 ✓ |

---

## Findings

### F1 (P2) — `InvalidTimeHStorageClass` 缺少行级定位上下文

- **严重程度**: P2
- **文件**: `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyMigrationError.kt:53-55`
- **问题**: `InvalidTimeHStorageClass(storageClass)` 只携带 storage class 值，没有 eventId/planId/表名或行序上下文。与 `InvalidEventTimeH`（带 eventId+rawTimeH）和计划时间错误（带 planId+position+originalValue）相比，此错误无法单独定位出错行。
- **影响**: 极小。该 primitive 是纯决策函数（按 ADR-016 无 Cursor 依赖），4A-1 的 Cursor 适配层在逐行读取时自然会携带行上下文；migration 在首个非法 storage class 处整体中止，失败位置可通过扫描顺序确定。未来 CLI 报告（Batch 4C）如需精确行定位，需调用方补充行号。
- **设计依据**: ADR-016 "先检查 Cursor.isNull 和 Cursor.getType，只允许 INTEGER/FLOAT"；Batch 4A-0 明确不含 Cursor 适配器（report §5）。
- **最小修复方向**: 不修复（调用方在 4A-1 提供行上下文）；或在 4A-1 增加携带行序的包装错误。不阻塞提交。
- **是否阻止提交**: 否

**其余无问题（None）。** 错误体系 13 类结构化覆盖核对：

| 需求 | 错误类型 | 覆盖 |
|---|---|---|
| eventId | `InvalidEventTimeH.eventId` | ✓ |
| 原始 timeH | `InvalidEventTimeH.rawTimeH` | ✓ |
| planId | 计划时间各错误均含 `planId` | ✓ |
| position | 计划时间各错误均含 `position` | ✓ |
| 原始计划时间值 | `originalValue` / `rawTimeOfDay` | ✓ |
| 非 finite timeH | `InvalidEventTimeH(cause: NonFinite)` | ✓ |
| timeH 溢出 | `InvalidEventTimeH(cause: Overflow/OutOfRange)` | ✓ |
| 非法 JSON | `InvalidTimeOfDayJson(reason: MALFORMED)` | ✓ |
| 非字符串元素 | `TimeOfDayElementNotString(kind)` | ✓ |
| 非法 LocalTime | `InvalidLocalTime` | ✓ |
| 非分钟精度 | `NonMinuteLocalTime(parsedLocalTime)` | ✓ |
| Slot ID 生成错误 | `SlotIdGenerationFailed(cause: SlotIdError)` | ✓ |
| 非法 storage class | `InvalidTimeHStorageClass` | ✓（缺行级上下文，见 F1） |

不依赖自由格式 message（全部结构化字段）；不记录完整健康记录（仅 ID/原始值/位置）；不暴露 Room/Cursor/DAO/Database/Repository；与 `MappingResult` 语义无冲突（不同边界，各自 sealed 体系）。

---

## LegacyPlanTimeParser verdict

**符合设计。** `LegacyPlanTimeParser.kt` 逐项核对（23 项全部通过）：

- 空字符串 → 空结果（`emptySqlStringReturnsEmptyEntries`）✓；`[]` → 空结果 ✓
- 仅接受 JSON 字符串数组；对象根 → `ROOT_NOT_ARRAY` ✓；数字/Boolean/null/数组/对象元素 → `TimeOfDayElementNotString(kind)` ✓
- 原顺序保留 ✓；重复时间保留（position 参与 Slot ID，ID 不同）✓；position=列表索引 ✓；`originalValue` 原样 ✓；`rawTimeOfDay` 不修改 ✓
- ISO LocalTime 解析：`08:30` 成功 ✓；`08:30:00` 成功并 canonicalize 为 `08:30` ✓；`08:30:00.000000000`（零秒零纳秒）成功 ✓；非零秒/纳秒失败（不截断/舍入）✓；`00:00`/`23:59` 成功 ✓；` 08:30 ` 空白不 trim 直接失败 ✓
- `canonicalLocalTime = parsedLocalTime.toString()` → 分钟精度下为 `HH:mm` ✓
- Slot ID 调用 `ScheduledDoseSlotId.generate`；固定 namespace、canonical name、固定向量 `17d1fd14-9d70-5344-beaa-0b158c9f62f4` 未变（硬编码断言）✓
- 无随机 UUID；无默认 Locale/时区/charset（Locale/时区测试真实改变并在 finally 恢复+断言恢复）✓
- **JSON 行为与 Converters 一致**：`Converters.kt:41-48` 的 `toStringList` 使用默认 `Json` 配置且空字符串 → 空列表，parser 同样使用默认 `Json` 且空字符串 → 空结果 —— 完全一致 ✓
- 未接受旧 converter 不可能产生的宽松格式（默认 Json 非 lenient）✓
- **JSON 错误不会被解释为空列表**：malformed JSON → `MALFORMED` failure（测试 `malformedJsonReturnsStructuredFailure`）✓

---

## MigrationPrimitives verdict

**符合设计。** `MigrationPrimitives.kt`：

- **timeH primitive**：`legacyTimeHToOccurredAtEpochMillis` 直接委托 `LegacyTimeAdapter.timeHToEpochMillis`（L11），**零公式复制**（无 `3_600_000`、无 `Math.round`，grep 确认生产文件无命中）✓
- 0/正/负/毫秒精度向量正确（`wrapperMatchesHardcodedLegacyAdapterVector`: 123456.789 → 444_444_440_400L）✓
- NaN/正 Infinity/负 Infinity 分别结构化失败 ✓；正负乘法溢出（±Double.MAX_VALUE）✓；正负 epoch 范围溢出（nextUp/nextDown 边界）✓
- 不 clamp、不回退 0、不使用当前时间 ✓
- 失败保留 eventId + 原始 rawTimeH + 原始 `LegacyTimeError` cause ✓
- **storage class primitive**：INTEGER/FLOAT 允许；NULL/STRING/BLOB 拒绝 ✓；纯 Kotlin 无 Cursor ✓；无 `SupportSQLiteDatabase` adapter（grep 确认）✓
- 无 `MIGRATION_2_3`/`ALTER TABLE`/`CREATE TABLE`/Room Migration/version 3/schema 生成代码（grep 确认）✓

---

## Test quality verdict

**独立核实（非采信报告）**：实际运行并解析 JUnit XML：

| 套件 | tests | failures/errors/skipped |
|---|---|---|
| LegacyPlanTimeParserTest | 23 | 0/0/0 |
| MigrationPrimitivesTest | 15 | 0/0/0 |
| LegacyStorageClassTest | 5 | 0/0/0 |
| **合计** | **43 / 3 套件** | **0/0/0** |

与报告一致 ✓。

- 每个测试有真实断言；无空测试 ✓
- 无循环合并多个独立错误场景（每个错误场景独立 JUnit 方法）✓
- 未用被测实现生成期望值；固定 Slot UUID 硬编码 ✓
- 空字符串与 `[]` 独立覆盖 ✓；`08:30` 与 `08:30:00` 独立覆盖 ✓；多值顺序逐项断言 ✓；重复时间逐项断言（position 0/1 + ID 不同）✓
- position、planId、slotId 明确断言 ✓
- JSON 对象根失败、非字符串元素（数字/null）失败、空字符串元素失败、非法 LocalTime 失败、非零秒/纳秒失败、`00:00`/`23:59` 成功 —— 全部独立覆盖 ✓
- Locale 测试真实改变 Locale（US ↔ ar-EG）；时区测试真实改变默认时区（UTC ↔ Pacific/Auckland）；均 finally 恢复且断言恢复 ✓
- NaN/正/负 Infinity 分开测试 ✓；正负溢出分开测试 ✓；Math.round 正负临界（`Math.nextUp(0.5ms)` → 1L；`Math.nextDown(-0.5ms)` → -1L）分开测试 ✓
- eventId 错误上下文被断言（`failureRetainsEventIdAndRawTimeH`）✓
- 五种 storage class 分别测试 ✓
- 全部合成 fixture；不依赖 Android runtime/真实数据库 ✓
- 测试验证的是设计要求（固定向量、canonicalization、非分钟拒绝、Locale/TZ 独立性）而非仅实现自身 ✓

---

## Database/schema verdict

独立核实：

| 项 | 值 | 核对 |
|---|---|---|
| AppDatabase version | 2 | ✓（`AppDatabase.kt:19`） |
| exportSchema | true | ✓（Batch 1 起保持） |
| identityHash | `a8036e3f5ed6bb42d0e7289ac84039f3` | ✓（schema 2.json） |
| canonical Git blob/LF SHA-256 | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | ✓（`git cat-file blob` + cmd 重定向实测） |
| schema git diff | 零 | ✓ |
| CRLF 工作树差异 | 行尾表示差异，非内容变更 | ✓（报告区分清楚） |
| schema 3.json / scheduled_dose_slots / MIGRATION_2_3 / destructive migration | 不存在 | ✓（grep 无命中） |

报告未将 CRLF 哈希误写为 canonical 哈希 ✓。

---

## Architecture verdict

- 生产 primitives 为纯 Kotlin/JVM：依赖面仅 `core.model`（SlotIdError/ScheduledDoseSlotId）、`core.time`（LegacyTimeAdapter/LegacyTimeError）、`kotlinx.serialization`、`java.time`、`java.util.UUID` ✓
- 禁止模式扫描（migration 生产目录）：`android.`、`androidx.room`、`Cursor`、`SupportSQLiteDatabase`、`AppDatabase`、`Dao`、`Repository`、`Context`、`Compose`、`Wear`、`ZoneId.systemDefault`、`Locale.getDefault`、`Charset.defaultCharset`、`UUID.randomUUID`、`Math.round`、`3_600_000`、`3600000`、`ALTER TABLE`、`CREATE TABLE`、`MIGRATION_2_3` → **全部无命中** ✓
- 未修改 Converters、LegacyTimeAdapter、ScheduledDoseSlotId、Entity、DAO、AppDatabase ✓
- 未创建 Room migration、未接 Repository、未开始 Batch 4A-1 ✓

---

## Validation independently executed

以下为实际执行的命令（`JAVA_HOME=C:\Program Files\kedou\jre`），全部 BUILD SUCCESSFUL：

| 命令 | 结果 |
|---|---|
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.migration.*" --rerun-tasks` | PASS（3/43，JUnit XML 解析核实）|
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` | PASS |
| `:app:testDebugUnitTest --rerun-tasks` | PASS（全量）|
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | PASS（5/49，JUnit XML 解析核实）|
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS（0 errors，lint HTML 报告生成）|
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | PASS（仅编译）|

未执行 `connectedDebugAndroidTest`；未声称 instrumentation 通过。lint 具体 warning 计数以报告为准（本轮仅确认 0 errors）。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。唯一 P2（`InvalidTimeHStorageClass` 行级上下文）不阻塞提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- F1：`InvalidTimeHStorageClass` 行级定位上下文由 4A-1 Cursor 适配层携带；如需在 Batch 4C CLI 报告中精确定位，届时补充行序包装错误。

**是否建议提交 Batch 4A-0**：是。提交建议信息：`feat: add migration primitives, strict plan time parser, and structured errors`，并打标签 `phase-1-batch-4a0`。

**是否建议提交完成后进入 Batch 4A-1 的独立规划**：是，但仅在 4A-0 提交并打标签之后。4A-1 必须按 PHASE_1_DESIGN §Batch 4A-1 与 ADR-016 的约束以单个可构建原子提交完成（Entity 七字段 + Slot Entity/DAO + AppDatabase v3 + MIGRATION_2_3 + 3.json + 基础 instrumentation），并处于 ADR-016 第 19.1 节内部不可发布区间；不得建议直接开始修改数据库，除非 4A-0 已完成提交和标签。

---

*审阅结束。最终工作树：仅原 7 个 Batch 4A-0 文件 + 本审阅报告；未修改任何其他文件。*
