# Evolune Phase 1 Batch 4B 代码审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch4b-migration-matrix`（HEAD: `108e3cd`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 Batch 4C/3C；未创建 repair CLI

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 2（均为实施方已如实记录的平台/schema fixture 限制，非代码缺陷）
- **是否允许提交**: 是
- **最大剩余风险**: 无 P0/P1。两个 P2 为真实 SQLite/Android 平台边界（exact-v2 REAL affinity 无法保留 INTEGER storage class；NOT NULL + NaN→NULL 绑定使 NULL/NaN 无法在 exact-v2 落库），已被测试如实记录、以约束失败行为验证，并由 JVM primitive 测试独立覆盖策略层。v3 仍处 ADR-016 内部不可发布区间。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch4b-migration-matrix` ✓ |
| 未跟踪文件 | 恰好 2 个（`AppDatabaseMigrationMatrixTest.kt` + `4B_REPORT.md`）✓ |
| 已跟踪文件修改 | 无（`git diff` 对 production/schema/test/androidTest 全为空）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| `phase-1-batch-4a1` tag 在历史 | ✓（`dc95114` 提交 + tag；4A-1 审阅 `108e3cd`）✓ |
| 越界文件 | 无 Batch 4C/3C/repair CLI/Repository 接线 ✓ |
| 敏感数据 | 无真实数据库、APK、日志、密钥、真实健康数据 ✓ |
| production/schema 变化 | 无（MIGRATION_2_3 未修改；schema 2/3 无 diff）✓ |

---

## Findings

### F1 (P2) — exact-v2 REAL affinity 使 INTEGER storage class 无法落库（实施方已记录）

- **严重程度**: P2
- **文件**: `AppDatabaseMigrationMatrixTest.kt:123-132`（`integerBindingIsNormalizedByExactV2RealAffinityAndMigrates`）
- **问题**: 真实 v2 schema 的 `timeH REAL NOT NULL` 列受 SQLite affinity 规则约束，任何整数绑定都会被规范化为 `real` storage class。因此无法在 exact-v2 fixture 中构造并迁移一个真正以 INTEGER storage class 存储的 timeH。测试如实记录了该行为（`assertEquals("real", storageClass(...))`）并验证其可正常迁移。
- **影响**: 无实际风险——这正是真实 v2 数据库的必然状态；JVM primitive 测试（`LegacyStorageClassTest`）独立覆盖 INTEGER/FLOAT 允许、NULL/STRING/BLOB 拒绝的策略层。
- **依据**: ADR-016 storage class 政策；SQLite affinity 语义（REAL affinity 强制数值转 REAL）。
- **最小修复建议**: 不修复（平台边界，无法规避）；保持如实记录。
- **是否阻止提交**: 否

### F2 (P2) — exact-v2 NOT NULL + Android NaN 绑定使 NULL/NaN 无法落库（实施方已记录）

- **严重程度**: P2
- **文件**: `AppDatabaseMigrationMatrixTest.kt:134-150`（`exactV2NotNullConstraintRejectsNullBeforeMigration`、`androidSqliteCannotMaterializeNanInExactV2RealNotNullColumn`）
- **问题**: v2 `timeH` 列为 `NOT NULL`，NULL 无法落库；Android SQLite 将 `Double.NaN` 绑定为 NULL，同样被约束拒绝。两个测试均以 `assertConstraintFailure` + 0 行数验证实际行为。
- **影响**: 无实际风险——真实 v2 数据库不可能含 NULL/NaN timeH（约束层面已排除）；JVM `LegacyTimeAdapterTest`（4A-0）独立覆盖 NaN/±Inf 转换策略。
- **依据**: ADR-016 严格失败语义；真实 v2 schema 约束。
- **最小修复建议**: 不修复（平台边界）；保持如实记录。
- **是否阻止提交**: 否

**其余无问题（None）。**

---

## Matrix coverage verdict

22 个 @Test 方法逐一核对（行号）：

| # | 测试（行号） | 覆盖 |
|---|---|---|
| 1 | `legalTimeHBoundaryMatrix...` (L61) | 0/正/负/毫秒精度/±Math.round 临界/±接近 Long 边界 + 多事件同迁 |
| 2 | `floatStorageClassMigratesNormally` (L112) | REAL 正常迁移 |
| 3 | `integerBinding...Affinity...` (L123) | INTEGER→REAL affinity 规范化（P2-1 记录）|
| 4 | `exactV2NotNull...Null...` (L134) | NULL 约束拒绝（P2-2 记录）|
| 5 | `...CannotMaterializeNan...` (L142) | NaN 约束拒绝（P2-2 记录）|
| 6 | `positiveInfinityFailsAndRollsBack` (L152) | +∞ 失败+回滚 |
| 7 | `negativeInfinityFailsAndRollsBack` (L157) | −∞ 失败+回滚 |
| 8 | `positiveEpochRangeOverflow...` (L162) | +epoch 越界失败+回滚 |
| 9 | `negativeEpochRangeOverflow...` (L167) | −epoch 越界失败+回滚 |
| 10 | `positiveMultiplicationOverflow...` (L172) | +乘溢出失败+回滚 |
| 11 | `negativeMultiplicationOverflow...` (L177) | −乘溢出失败+回滚 |
| 12 | `textTimeHStorage...RollsBack` (L182) | TEXT 在数值读取前失败+回滚 |
| 13 | `blobTimeHStorage...RollsBack` (L187) | BLOB 在数值读取前失败+回滚 |
| 14 | `malformedPlanJsonFailsAndRollsBack` (L192) | 非法 JSON 失败+回滚 |
| 15 | `nonStringPlanElementFailsAndRollsBack` (L197) | 非字符串元素失败+回滚 |
| 16 | `invalidLocalTimeFailsAndRollsBack` (L202) | 非法 LocalTime 失败+回滚 |
| 17 | `nonMinutePlanTimeFailsAndRollsBack` (L207) | 非分钟时间失败+回滚 |
| 18 | `plansAndSlotsPreserveOrder...` (L212) | 多计划/slots/重复/边界/canonical/原串 |
| 19 | `foreignKeyIndexCascadeAndUnique...` (L255) | FK/index/cascade/unique 矩阵 |
| 20 | `actualProductionMigrationChain...V1FixtureToV3` (L296) | 真实 production builder v1→v2→v3 链 |
| 21 | `fixedSeedLongHistory...` (L316) | 2,000 events/100 plans 压力 |
| 22 | `identicalFixturesInSeparateDatabases...` (L330) | 确定性重放 |

每测试：独立方法 ✓、真实断言 ✓、不循环合并失败场景 ✓、独立数据库名（`phase1-batch4b-${methodName}`）✓、@Before/@After 清理（含 production 数据库名与单例重置）✓、合成 fixture ✓、不接触用户主数据库（emulator 测试环境）✓。

---

## timeH/storage-class verdict

**合法边界**（测试 1，L61-110）：
- 8 个向量的 expectedEpochMillis **全部为硬编码常量**，非被测 helper 生成 —— 无循环覆盖 ✓
- 覆盖：0、正、负、毫秒精度（472_222.22225638886 → 1_700_000_000_123L）、Math.round 正临界（1.388888888888889e-7 → 1L）、负临界（−1.388888888888889e-7 → −1L）、接近 Long.MAX_VALUE（9_223_372_036_854_774_784L = MAX−1023）、接近 Long.MIN_VALUE ✓
- `assertEquals(expected.timeH, cursor.getDouble(1), 0.0)` — delta 0.0 精确比较证明 timeH 逐位不变 ✓
- epoch 0 正确保留（向量 1）✓

**非法与 storage class**：
- ±Infinity 确实作为 REAL 保存并进入 migration 后失败（约束不拒绝 ∞，只有 NOT NULL 拒绝 null/NaN）✓
- overflow fixture 精确越过边界：合法 `...015.2153` 与非法 `...015.216` 相差一个 ULP 量级，非法值乘 3_600_000 后 ≥ 2^63 → OutOfRange ✓（正负方向均覆盖）
- TEXT/BLOB 先断言落库 storage class（`typeof`），再断言 migration 失败 —— 失败发生在读取数值前（4A-1 已核实 preflight 顺序）✓
- 每项失败均验证完整 rollback（见 Rollback verdict）✓
- NULL/NaN 未构造的场景在报告中如实写为限制（§4、§14），**未声称 instrumentation 已覆盖** ✓
- JVM `LegacyStorageClassTest`（5 测试）与 `LegacyTimeAdapterTest` 独立覆盖 NULL 策略与 NaN 转换 ✓

---

## Rollback verdict

`assertMigrationFailureAndRollback`（L460-476）在**每个**失败场景后验证：
- `user_version = 2` ✓
- 7 列不存在（`V2_EVENT_COLUMNS` 精确对比）✓
- slot 表不存在 ✓；两个 index 不存在 ✓
- **`room_master_table` 保持 v2 identity hash**（`a8036e3f...`）✓
- **迁移前全量快照 == 迁移后快照**：`databaseSnapshot` 含 `id, route, typeof(timeH), quote(timeH), hex(timeH), doseMG, ester, extras` + plans `id, timeOfDay` —— 以 typeof/quote/hex 捕获存储表示的字节级不变，证明无部分 UPDATE/INSERT ✓

rollback helper 使用固定 version 2 raw helper（`FrameworkSQLiteOpenHelperFactory` + `Callback(2)`，onCreate/onUpgrade 均 `fail()`）—— 不触发再次 migration、无 destructive fallback、连接关闭 ✓；原始 migration failure 被 `runCatching` 捕获并 `assertNotNull` —— 不掩盖 ✓。

非 P1：除 user_version 外，列/schema/index/identity/数据/存储表示全部验证 —— 完整回滚矩阵 ✓。

---

## Slot/FK/index verdict

- 多计划/0–8 slots（随机 0..5）/空与非空混合/重复 localTime/相同时间不同 position/不同 plan 相同 position/00:00/23:59/08:30:00→08:30 canonical —— 全覆盖 ✓
- 原 timeOfDay 字符串逐字不变 ✓；planId/连续 position/UUIDv5/原列表顺序/不排序/不去重/总 slot 数（7 = 5+0+0+2 硬编码断言）✓
- **Slot ID 预期由测试内独立 UUIDv5 实现（L747-763，自有 SHA-1/ByteBuffer/version/variant 位 + 硬编码 namespace `68559b97...`）生成**，非调用生产 `ScheduledDoseSlotId.generate` —— 独立 oracle ✓；与 4A-0/4A-1 的 Python uuid5 锁定向量一致 ✓
- FK/cascade/unique 在真实设备 SQLite 上验证（非仅查 schema JSON）：`PRAGMA foreign_keys=1`（production Room open）、orphan 拒绝、cascade 仅删对应计划、另一计划不受影响、`(planId,position)` unique、跨 plan 同 position 合法、同 plan 不同 position 合法、`PRAGMA index_info` 验证两 index 列序、无 localTime unique index、`foreign_key_check` 空 ✓

---

## v1 chain verdict

- 测试 20（L296-314）**未伪造 schema 1.json**；按 PHASE_1_DESIGN §18.2 使用"最小授权 v1 `dose_events` SQL fixture"（手写 DDL 与 MIGRATION_1_2 前置条件一致）
- 通过**真实 production `AppDatabase.getDatabase()`**（注册了私有 MIGRATION_1_2 + MIGRATION_2_3）打开 —— `MIGRATION_1_2` 与 `MIGRATION_2_3` 真实顺序执行 ✓
- 断言：user_version=3、事件保留并派生正确 epoch、medication_plans 表由 1_2 创建、slot 表存在、room identity = v3 hash ✓
- 报告 §8 **如实表述**："repository has no trusted Room 1.json. The test therefore does not invent one... minimal v1 dose_events SQL fixture" —— 未夸大为"正式 Room schema-1 全链路验证" ✓

---

## Stress and determinism verdict

**压力测试**（测试 21，L316-328）：
- 固定种子 `0x4B4B2026`（`Random(seed)`）✓
- 2,000 events（index 0→0.0、index 1→−1.0、其余随机毫秒派生 timeH）✓；100 plans、每 plan 0..5 slots（`random.nextInt(6)`）✓；每第 3 位强制 08:30 保证重复 ✓；仅分钟精度 ✓
- 断言：event/plan/slot 总数、逐事件 timeH（delta 0.0）+ occurredAt、逐 plan timeOfDay 原串 + 逐 slot 独立 UUIDv5、`foreign_key_check` 空 ✓
- **耗时非断言**：`elapsedMillis` 仅 `Log.i` 记录 —— 147/143 ms 是设备观测日志，无易波动硬阈值 ✓（报告 §9 明示 "No strict timing assertion is used"）

**确定性重放**（测试 22，L330-345）：
- 两个独立数据库（`-first`/`-second`），相同 fixture（REPLAY_SEED，250 events/25 plans），分别执行 `createDatabase`+`runMigrationsAndValidate` —— 未在同一库重复迁移 ✓
- 比较完整 `MigrationSnapshot`：全部 event 9 列、全部 slot 4 列（按 planId,position 排序）、Room identity、index 名集合、FK 元数据 —— 非仅 count/hash ✓

---

## Report accuracy verdict

逐项核对 `PHASE_1_BATCH_4B_REPORT.md`：

| 声明 | 独立核实 |
|---|---|
| 22/22 matrix（emulator-5556, API 33）| ✓ 独立重跑（Starting/Finished 22 tests, PASS）|
| 43/43 全量 connected | ✓ 独立重跑（43/43 completed, 0 failed）|
| 22+18+2+1 = 43 组成 | ✓ 与 4A-1 套件数一致 |
| 压力 2,000/100/267、固定种子、147/143 ms 为观测值 | ✓ 代码核实（seed/规模/Log 观测）；267 为 fixture 实际值 |
| P0/P1/P2 = 0/0/2 与两个 P2 边界 | ✓ 与代码行为一致；未夸大 |
| JVM：migration 43 / mapper 43 / core 47 / App 221 / PK 49 / Wear 1 | ✓ 全量 25/221 JUnit XML 实测；其余此前批次核实 |
| App/Wear build、lint 0 errors | ✓ 独立重跑 |
| schema v2/v3 无变化；v3 identity `c5f5e02c...`、SHA-256 `044013C0...` | ✓ git diff 为空；SHA-256 实测一致 |
| 未修改 production migration | ✓ git diff 全空 |
| v3 不可发布；4C/3C 未完成 | ✓ 报告如实声明 |

报告**未**声称：NULL/NaN exact-v2 instrumentation 通过（§4/§14 明示为平台限制）、INTEGER storage class 稳定保存、Batch 4 整体完成、v3 可发布、repair CLI 完成、Repository 已接线 ✓。

---

## Independent validation executed

以下全部为本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`，`ANDROID_SERIAL=emulator-5556`）：

| 命令 | 结果 |
|---|---|
| `adb -s emulator-5556 shell getprop sys.boot_completed` | 1 ✓ |
| `adb -s emulator-5556 shell service list` | 255 services ✓（报告称 256，为运行时刻差异，非实质问题）|
| `adb -s emulator-5556 shell getprop ro.build.version.sdk` | 33 ✓ |
| `:app:connectedDebugAndroidTest -P...class=...AppDatabaseMigrationMatrixTest --rerun-tasks` | **PASS** — Starting/Finished 22 tests |
| `:app:connectedDebugAndroidTest --rerun-tasks`（全量）| **PASS** — 43/43 completed, 0 failed |
| `:app:testDebugUnitTest --rerun-tasks`（全量）| **PASS** — 25 suites / 221 tests（JUnit XML 实测）|
| `:app:testDebugUnitTest --tests "pk.*" --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS（0 errors）|
| schema 3 SHA-256（Get-FileHash）| `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` ✓ |
| `git diff`（production/schema/test/androidTest）| 全空 ✓ |

未声称执行任何未实际运行的命令。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。两个 P2 为真实平台/schema fixture 边界，实施方已如实记录并以约束失败行为验证，JVM 层独立覆盖策略 —— 不阻止提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- 两个 P2 的平台边界记录保持现状（无修复途径）
- Batch 4C repair CLI（独立批次）

**是否建议提交 Batch 4B**：是。提交建议信息：`test: add v2-v3 migration matrix, rollback, chain, and stress coverage`，并打标签 `phase-1-batch-4b`。

**提交后是否建议进入 Batch 4C**：是，但仅在 4B 提交并打标签之后。4C 按 ADR-016 §13.3/PHASE_1_DESIGN §Batch 4C 在 `tools/repair-v2/` 实现 Python 3.12 标准库工具（scan/repair/verify、manifest、JSONL 审计、合成 fixture）。

**v3 是否仍不可发布**：**是**。Room v3 仍处 ADR-016 §19.1 内部不可发布区间；Batch 4B 通过不等于 Batch 4 完成或可发布，最终 release 需满足 Batch 8 全部门槛。

---

*审阅结束。最终工作树：仅原 2 个 Batch 4B 文件 + 本审阅报告；未修改任何其他文件。*
