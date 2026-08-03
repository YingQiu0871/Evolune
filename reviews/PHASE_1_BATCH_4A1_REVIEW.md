# Evolune Phase 1 Batch 4A-1 代码审阅报告

**审阅日期**: 2026-08-03
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch4a1-v3-schema`（HEAD: `16d8dbf`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未开始 Batch 4B/3C；未创建 repair CLI

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 1（lint 警告计数由 79 → 80 未在报告中说明来源；纯记录性，不阻塞）
- **是否允许提交**: 是
- **最大剩余风险**: 无 P0/P1 风险。唯一剩余风险是 v3 处于 ADR-016 内部不可发布区间（计划内），以及 Batch 3C/4B 未完成（计划内限制）。迁移完整性、回滚、FK/cascade/unique、UUIDv5、时间语义均经独立 connected 验证。

---

## Git and scope boundary

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch4a1-v3-schema` ✓ |
| 文件变化 | 恰好 12 个：5 修改 + 7 新增 ✓ |
| 已修改 | `ExampleInstrumentedTest.kt`、`AppDatabaseV2BaselineTest.kt`、`AppDatabase.kt`、`DoseEventEntity.kt`、`DoseEventEntityMapperTest.kt` ✓ |
| 已新增 | `3.json`、`AppDatabaseMigrationTest.kt`、`ScheduledDoseSlotDao.kt`、`ScheduledDoseSlotEntity.kt`、`AppDatabaseMigrations.kt`、`LegacyMigrationException.kt`、`4A1_REPORT.md` ✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过（仅 CRLF 提示，无空白错误）✓ |
| 设计 tag 在历史 | `phase-1-batch-4a1-design-v1` ✓（`c976c9e` 后；`16d8dbf` 澄清迁移顺序） |
| 越界文件 | 无 Batch 4B/3C/repair CLI 文件 ✓ |
| 敏感数据 | 无真实数据库、APK、日志、密钥、manifest、patch、本地配置 ✓ |
| schema 2.json | 无变化（`git diff --name-status` 为空）✓ |

---

## Findings

### F1 (P2) — lint 警告计数 79 → 80 未在报告中解释来源

- **严重程度**: P2
- **文件**: `docs/phase-reports/PHASE_1_BATCH_4A1_REPORT.md:143`（lint 行）
- **问题**: 报告声明 `0 errors / 80 warnings / 1 hint`，与 Batch 4A-0 的 79 warnings 相比多出 1 条，但报告未说明新增警告的来源。新增的 warning 大概率来自新增的 androidTest/测试代码（例如 `MigrationTestHelper` 用法或测试内字符串字面量），但报告没有确认。
- **影响**: 极小。lint 0 errors 已独立验证（`:app:lintDebug` BUILD SUCCESSFUL）；警告计数差异不影响正确性。仅报告完整性不足。
- **设计依据**: 无对应设计要求；纯报告质量。
- **最小修复方向**: 报告补一句新增 1 条 warning 的来源（或确认与既有警告同类）。不阻塞提交。
- **是否阻止提交**: 否

**其余无问题（None）。**

---

## DoseEvent v3 verdict

**符合设计。** `DoseEventEntity.kt`：

- 旧 6 字段顺序（id/route/timeH/doseMG/ester/extras）未变 ✓
- 新增 7 字段齐全，`@ColumnInfo(defaultValue)` 与 DDL/schema 3 一致（0 / 无 / 无 / 无 / 'LEGACY' / 'RECORDED' / 1）✓
- **Kotlin 运行时默认不是 literal 0L**：`occurredAtEpochMillis: Long = strictOccurredAtEpochMillis(id, timeH)`（L26）—— 运行时严格从合法 timeH 推导 ✓
- `strictOccurredAtEpochMillis` 直接复用 4A-0 primitive `legacyTimeHToOccurredAtEpochMillis`（即 `LegacyTimeAdapter`）—— 零公式复制 ✓
- NaN/±Inf/±溢出 → `IllegalArgumentException` 明确失败 ✓；不 clamp、不回退当前时间 ✓
- **合法 epoch 0 正常得到 0L**：`runtimeEntityStrictlyDerivesLegitimateOccurredAtValues` 断言 `epochZero.occurredAtEpochMillis == 0L` ✓；`legitimateEpochZeroRemainsZeroAfterMigration` 断言迁移后 timeH=0.0 且 occurredAt=0L ✓（0 不会被误判为未回填）
- zoneId/localDate/slotId 默认 null ✓；source=LEGACY、status=RECORDED、revision=1 ✓
- factory `fromDoseEvent` 与直接构造使用同一 `strictOccurredAtEpochMillis` ✓
- 新字段全部带默认值且位于旧字段之后 —— 无位置参数兼容问题 ✓
- 未修改 Repository/UI/JSON/Reminder/Widget/Wear 调用方（生产调用面不变）✓
- **无运行时写假 epoch 0 的路径** ✓

---

## Slot schema and DAO verdict

**符合设计。**

`ScheduledDoseSlotEntity.kt`：
- 精确 4 字段：id (UUID PK)、planId (UUID)、localTime (String)、position (Int) ✓
- tableName = `scheduled_dose_slots` ✓
- FK → `medication_plans.id`，onUpdate=NO_ACTION、onDelete=CASCADE ✓
- 索引：`index_scheduled_dose_slots_planId`（普通）+ `index_scheduled_dose_slots_planId_position`（unique）✓
- localTime 不 unique ✓；ID 不自动生成 ✓；无随机 UUID ✓；无额外字段 ✓
- 无 Domain mapper、无生产 aggregate ✓

`ScheduledDoseSlotDao.kt`：
- 仅 `getSlotsForPlan(planId)`，`ORDER BY position ASC` ✓
- 无 insert/upsert/delete/Flow/transaction ✓

---

## MIGRATION_2_3 verdict

**符合设计。** `AppDatabaseMigrations.kt`（459 行）逐段核实：

**顺序**：DDL → 全量预检（events+plans）→ 回填 → 后置验证 ✓（L10-16）

**DDL (L39-86)**：7 列 SQLite 类型/nullability/default 与 Entity 和 schema 3 完全一致；slot 表 4 列 + PK + FK(cascade) + 2 索引 ✓；**无** beginTransaction/setTransactionSuccessful/endTransaction/COMMIT/user_version 手工修改/room_master_table 手工修改/destructive migration/fallbackToDestructiveMigration（事务由 Room 外层 upgrade transaction 包裹）✓

**全量事件预检 (L88-132)**：
- DDL 后、任何 UPDATE/INSERT 前完成 ✓
- `SELECT ... ORDER BY id` 稳定排序 ✓
- id 经 `requireText`（isNull + getType==STRING 先检查）+ `parseUuid` ✓
- timeH：isNull → NULL class；getType → storageClass；**先 validateLegacyTimeHStorageClass 通过后才 getDouble** ✓（L104-119）
- INTEGER/FLOAT 接受、NULL/STRING/BLOB 拒绝 ✓
- 复用 `legacyTimeHToOccurredAtEpochMillis`（4A-0 primitive）✓；不复制公式 ✓
- 全部收集到内存后才进入写入 ✓；Cursor `use{}` 正确关闭 ✓；任一失败抛 `LegacyMigrationException` ✓

**全量计划预检 (L134-177)**：
- ORDER BY id；id 必须合法 UUID ✓；timeOfDay 必须 TEXT ✓
- 复用 `LegacyPlanTimeParser` ✓；顺序/重复保留 ✓；非分钟明确失败 ✓；无截断/舍入/跳过/修复 ✓；不修改 timeOfDay ✓；无随机 Slot ID ✓
- 所有 slot 收集完成后才写入 ✓；Cursor 关闭 ✓

**回填 (L179-232)**：
- compiled UPDATE 绑定参数；**每行更新数必须恰为 1**（`updatedRows != 1` → failure）✓
- 只 SET `occurredAtEpochMillis`，不修改 timeH ✓；source/status/revision 由 DDL 默认 ✓；nullable metadata 保持 null ✓
- slot INSERT 绑定参数（id/planId/canonical time/position）；`executeInsert() == -1L` → failure ✓
- 不推断 legacy event slotId ✓；statement `use{}` 生命周期 ✓

**后置验证 (L234-383)**：
- event 行数、timeH 未变（getDouble 对比）、occurredAtEpochMillis、zoneId/localDate/slotId 为 null、LEGACY/RECORDED/revision=1 ✓
- slot 行数 + 内容（id/planId/localTime/position）✓
- `PRAGMA foreign_key_check` 非空即失败 ✓
- **合法 epoch 0 判定**：expected 来自 preflight 的 0L，`getLong(2) != expected` 对 0 行成立于 expected==0 —— 不误判 ✓

---

## Rollback verdict

**符合设计且经设备验证。**

`failedMigrationRollsBackSchemaDataAndUserVersion`（AppDatabaseMigrationTest L323-361）：
- 固定 version 2 raw helper（`FrameworkSQLiteOpenHelperFactory` + `Callback(2)`，onCreate/onUpgrade 均 `fail()`）✓
- 断言 user_version=2 ✓；7 列不存在（V2_EVENT_COLUMNS 对比）✓；slot 表不存在 ✓；两个 index 不存在 ✓
- 原数据不变：event 逐字段（route/timeH/doseMG/ester/extras）✓；plan timeOfDay 字节不变 ✓
- 无部分 UPDATE/INSERT（表与列均不存在即证）✓
- 该测试在 emulator-5556 上独立复现通过（见 Independent validation）✓

Room/SQLiteOpenHelper 外层 transaction 在异常时整体回滚（migration 不手工管理事务，异常传播至上界）✓

---

## Schema verdict

独立核实（实际解析 schema 3.json）：

| 项 | 值 | 核对 |
|---|---|---|
| version | 3 | ✓ |
| identityHash | `c5f5e02cb04b048ca28fe96a74d61606` | ✓（与报告一致）|
| 表 | dose_events / medication_plans / scheduled_dose_slots | ✓（3 张）|
| medication_plans | 与 v2 一致（未变）| ✓ |
| dose_events 旧 6 列 | id/route/timeH/doseMG/ester/extras NOT NULL 不变 | ✓ |
| 7 新字段 | INTEGER NOT NULL default=0 / TEXT nullable ×3 / TEXT NOT NULL default='LEGACY' / 'RECORDED' / INTEGER NOT NULL default=1 | ✓ |
| slot 4 列 | 全 NOT NULL | ✓ |
| FK | medication_plans(planId→id)，NO ACTION/CASCADE | ✓ |
| 索引 | 2 个；unique 在 (planId, position)；localTime 不 unique | ✓ |
| **schema 3 工作树 SHA-256** | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | ✓（Get-FileHash 实测与报告一致）|
| schema 2 | git diff 为零；工作树 CRLF 哈希 `C4770838...` 为行尾表示差异 | ✓ |
| schema 2 canonical blob | `B8DA54ED...`（此前批次实测，diff 为零即未变）| ✓ |

---

## Instrumentation quality verdict

**独立复现（非采信报告）**：18 个迁移测试覆盖矩阵全部核实：

| # | 要求 | 测试 | 状态 |
|---|---|---|---|
| 1 | 空库 v2→v3 | `emptyV2DatabaseMigratesToValidatedV3Schema` | ✓ |
| 2 | 单事件 | `singleSyntheticEventMigratesWithoutChangingLegacyValues` | ✓ |
| 3 | epoch 0 | `legitimateEpochZeroRemainsZeroAfterMigration` | ✓ |
| 4 | 单 slot | `singlePlanCreatesOneSlotWithFixedUuidV5Vector` | ✓ |
| 5 | 多 slot 原顺序 | `multipleSlotsPreserveOriginalOrder`（3 个硬编码 UUID）| ✓ |
| 6 | 重复时间 | `duplicateTimesRemainDistinctByPositionAndSlotId` | ✓ |
| 7 | 空字符串 | `emptySqlStringCreatesNoSlotsAndRemainsUnchanged` | ✓ |
| 8 | 空数组 | `emptyJsonArrayCreatesNoSlotsAndRemainsUnchanged` | ✓ |
| 9 | 固定 UUIDv5 | `fixedUuidV5VectorMatchesLockedExpectedValue`（17d1fd14...）| ✓ |
| 10 | timeOfDay 原样 | `originalTimeOfDayStringsRemainByteForByteEquivalent` | ✓ |
| 11 | foreign_keys=1 | `foreignKeysAreEnabledAfterMigration` | ✓ |
| 12 | orphan 失败 | `orphanSlotInsertIsRejectedByForeignKey` | ✓ |
| 13 | cascade | `deletingPlanCascadesOnlyItsSlots` | ✓ |
| 14 | unique | `uniquePlanPositionRejectsOnlyTrueConflicts` | ✓ |
| 15 | 非分钟失败 | `nonMinutePlanFailsWithStructuredContext`（tableName/rowId/planId/position/originalValue 全部断言）| ✓ |
| 16 | 整体回滚 | `failedMigrationRollsBackSchemaDataAndUserVersion` | ✓ |
| 17 | 合法 runtime Entity | `runtimeEntityStrictlyDerivesLegitimateOccurredAtValues` | ✓ |
| 18 | 非法 runtime Entity | `runtimeEntityRejectsEveryInvalidLegacyTimeValue`（NaN/±Inf/±MAX）| ✓ |

- 每测试独立数据库（`phase1-batch4a1-${methodName}`）+ @Before/@After 删除 ✓
- v2 baseline 不触发 v3 迁移：raw helper 的 onCreate/onUpgrade 均 fail()，且 `20:30:15` fixture 仍为合法 v2 数据（baseline 不迁移）✓
- 固定 UUID 硬编码，非被测函数生成 ✓；无假覆盖 ✓
- ExampleInstrumentedTest：从 instrumentation manifest 取 variant-specific `targetPackage` 与 `targetContext.packageName` **精确相等比较**（非 contains/startsWith、非硬编码 debug/release 包名）✓
- 合成 fixture 仅 ✓

---

## JVM compatibility verdict

`DoseEventEntityMapperTest.kt` diff：
- 仅新增 `corruptEntity(timeH)` helper（显式 `occurredAtEpochMillis = 0L` 等模拟已物化损坏行），用于 NaN/±Inf/±溢出测试 —— 因为 v3 运行时构造已严格拒绝非法 timeH，mapper 测试需显式构造损坏行验证防御能力 ✓
- 注释明确"模型已物化损坏行，非生产 fallback" ✓
- 未删除/放宽/替换原错误断言（InvalidTimeH + cause 断言保留）✓
- 生产 mapper 未修改（diff 只涉及测试文件）✓

---

## Architecture boundary verdict

- 未切换 Repository contract/implementation；无 Domain→v2 写 mapper；JSON/PK 行为未变；ViewModel/UI/Reminder/Widget/Wear 生产路径未变；Gradle/Manifest/applicationId 未变 ✓
- 未引入 Health Connect/Glance/WorkManager/Hilt/SQLCipher/云同步 ✓
- `timeH`/`timeOfDay` 保留为兼容字段 ✓；Tracked Date 未进入 ✓
- 全部迁移 fixture 合成，无真实/派生健康数据 ✓
- Batch 3C/4B/4C 未开始（计划内）✓

---

## Report accuracy verdict

逐项核对报告（`PHASE_1_BATCH_4A1_REPORT.md`）：

| 报告声明 | 独立核实 |
|---|---|
| 12 文件范围 | ✓ 与工作树一致 |
| 21/21 connected（emulator-5556, sdk_gphone64_x86_64, API 33, Evolune_API33_Migration(AVD)-13）| ✓ 独立重跑通过 |
| 18/18 migration | ✓ 独立重跑（Starting/Finished 18 tests, BUILD SUCCESSFUL）|
| 2/2 baseline | ✓ 独立重跑 |
| JVM：migration 43 / mapper 43 / core 47 / App 221 / PK 49 / Wear 1 | ✓ 全量 25/221 实测；此前批次核实过其余 |
| App/Wear debug build | ✓ 独立重跑 |
| lint 0 errors / 80 warnings / 1 hint | 0 errors 已确认；80 warnings 未逐条独立计数（见 F1）|
| v3 identityHash `c5f5e02c...` | ✓ schema 3 实测 |
| schema 3 SHA-256 `044013C0...` | ✓ Get-FileHash 实测 |
| schema 2 无变化 | ✓ git diff 为空 |
| v3 不可发布 | ✓ 报告明确声明 |
| Batch 3C/4B/4C 未完成 | ✓ 报告如实声明 |
| 不夸大：未声称可发布/真实数据库/Batch 4 完成/生产双写/slots 接入 plan 保存 | ✓ 全部如实 |

报告与代码/结果一致（除 F1 的警告计数来源说明缺失）。

---

## Independent validation executed

以下全部为本轮实际执行的命令（`JAVA_HOME=C:\Program Files\kedou\jre`，`ANDROID_SERIAL=emulator-5556`）：

| 命令 | 结果 |
|---|---|
| `adb devices -l` / `getprop sys.boot_completed` / `ro.build.version.sdk` / `ro.product.model` | emulator-5556, boot=1, API 33, sdk_gphone64_x86_64 ✓ |
| `:app:connectedDebugAndroidTest -P...class=...AppDatabaseMigrationTest --rerun-tasks` | **PASS** — Starting/Finished 18 tests on Evolune_API33_Migration(AVD) - 13 |
| `:app:connectedDebugAndroidTest -P...class=...AppDatabaseV2BaselineTest --rerun-tasks` | **PASS** — 2 tests |
| `:app:connectedDebugAndroidTest --rerun-tasks`（全量）| **PASS** — 21 tests |
| `:app:testDebugUnitTest --rerun-tasks`（全量）| **PASS** — 25 suites / 221 tests / 0 failures（JUnit XML 实测）|
| `:app:testDebugUnitTest --tests "pk.*" --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS（0 errors）|
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | PASS（仅编译）|
| schema 3 SHA-256（Get-FileHash）| `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` ✓ |

未声称执行任何未实际运行的命令。设备 instrumentation 结果为本轮独立复现，非引用实施方结果。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。唯一 P2（lint 警告计数来源说明）为纯报告记录性事项，不阻塞提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- F1：lint 80 warnings 的来源说明（可在报告修订或后续批次补充）
- Batch 3C（等 Batch 4B 迁移矩阵与 v3 验证完成）
- Batch 4C repair CLI

**是否建议提交 Batch 4A-1**：是。提交建议信息：`feat: add room v3 schema, strict migration 2_3, and slot entities`，并打标签 `phase-1-batch-4a1`。

**是否建议提交后进入 Batch 4B**：是，但仅在 4A-1 提交并打标签之后。Batch 4B 需按 PHASE_1_DESIGN §Batch 4B 实际执行第 18.3 节完整 migration matrix（emulator/专用设备，含 v1→v2→v3 链、固定种子长历史、FK/cascade/unique、connected instrumentation）—— androidTest 仅编译不算通过。

**是否仍处于不可发布内部版本**：**是**。Room v3 处于 ADR-016 §19.1 内部不可发布区间，直到 Phase 1 Batch 8 退出验收全部通过；禁止生产 APK/AAB、用户主数据库升级、正式 release 和在真实健康数据上运行。

---

*审阅结束。最终工作树：原 12 个 Batch 4A-1 文件 + 本审阅报告；未修改任何其他文件。*
