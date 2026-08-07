# Evolune Plan-Save Regression Fix Independent Review

Date: 2026-08-07
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-plan-save-fix`
Branch: `phase1/plan-save-regression-fix`
Baseline: `phase-1-batch-6`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 2**
- **代码修复本身正确**：根因链完整成立（旧 `createNew` 非毫秒 `clock.instant()` → v3 persistence mapper 的 lossless createdAt 校验 → `PlanSaveResult.Invalid` → 旧 UI 折叠为 unknown error）；修复位于正确的 canonicalization ownership（create-session boundary，非 mapper/Repository 放宽）
- **P2-1（代码）**：`MedicationPlanViewModel` 的 `catch (_: IllegalStateException) → StorageFailure` 宽于必要（Repository 存储异常统一为 `RepositoryStorageException`，应只捕获该类）；不吞 cancellation、不转成功、无数据风险
- **P2-2（流程披露）**：真机 destructive reinstall 是经用户确认备份后授权的、仅限该 debug package 的操作；无数据安全违规；但真机结果只能证明 fresh-install 路径，**in-place upgrade / 原设备旧数据库保留路径未验证**——synthetic v2→v3 补 migration 回归，不等同 real-device preserved-data 验证
- **是否允许提交并合并回 Batch 7 baseline：是**（先提交 implementation/report → review → tag）
- **是否允许随后进行 MedicationPlan UI sizing fix：是**
- **Batch 7 implementation 仍禁止**，直到 Batch 7 design review 完成
- Room v3 仍不可发布

## Git/scope

- 分支 = `phase1/plan-save-regression-fix` ✓
- `git merge-base --is-ancestor phase-1-batch-6 HEAD` 退出码 0 ✓；Batch 7 不在本 worktree（基线 = batch-6）✓
- 暂存区空；`git diff --check` 通过 ✓
- 工作树 = 12 文件（10 修改 + 2 新增 = 11 代码/测试 + 1 report，与任务清单一致）✓
- 无 schema/build/keystore/database/log/敏感数据文件进入版本控制（`app/debugkeystore.jks` 被 .gitignore 忽略；本审阅为补全构建环境从旧工作树复制该被忽略文件，git status 不显示，版本控制内容不变）✓
- 越界 diff 全空：schemas、migration、core/、DAO/Entity、data/repository/、Manifest、gradle、wear/ ✓

## Root-cause verdict

独立证明（8 环全成立）：

1. 旧 `createNew`：`git show HEAD` 确认 `createdAt = clock.instant()` 无 truncate ✓
2. `Clock.systemUTC().instant()` 可返回非毫秒纳秒（Android 系统时钟精度可达纳秒；真机复现 + 报告 §3）✓
3. Domain `MedicationPlan.createdAt: Instant`（data class，无精度约束）接受该值 ✓
4. v3 mapper lossless 校验存在：`MedicationPlanEntityMapper.toPersistenceAggregate` L139 `if (Instant.ofEpochMilli(createdAtEpochMillis) != createdAt) return InvalidCreatedAt`——**毫秒重建不等即拒绝** ✓
5. `2026-08-07T01:02:03.123456789Z` → epochMillis 重建 = `.123Z` ≠ 原始 → **InvalidCreatedAt** ✓（PlanSaveRegressionTest 的 `DEVICE_CLOCK_INSTANT` 正是该值，`PERSISTED_CREATED_AT` = `.123Z`）
6. millisecond-aligned 值 → 重建相等 → 通过（既有 6B 测试全部是毫秒对齐值）✓
7. `RoomMedicationPlanRepository.save` L48：mapper Failure → `PlanSaveResult.Invalid` ✓
8. 旧 UI：`MedicationPlansScreen`（HEAD 版）`showSubmissionError = submissionFailure?.operation in listOf(SAVE, DELETE)` + BottomSheet 显示 `common_unknown_error`——**structured failure 被折叠为 unknown** ✓

**根因链无断裂 → 无 P1**

## Canonicalization-ownership verdict

修复 = `MedicationPlanEditSessionFactory.createNew()` 内 `clock.instant().truncatedTo(ChronoUnit.MILLIS)`（MedicationPlanEditor.kt:34）✓

**为什么 create-session boundary 是正确的 ownership**：
- `createdAt` 的语义 = 方案创建时刻；所有新建入口（`startCreateSession` → factory）都汇聚于此，一次规范化即覆盖全部生产路径 ✓
- mapper/Repository 保持严格 lossless 校验——**不放宽**（防其他入口带精度问题；保护 persistence 契约）✓
- 无 mapper 静默截断、无 Repository 静默修复、无 Room converter 舍入、无 equality 放宽、无 try/catch 伪装成功 ✓
- 修复只影响**新建**（未来事件）；既有存储数据（已 millis 对齐）不受影响 ✓

## Edit metadata preservation verdict

- `edit(plan)`：`createdAt = plan.createdAt`（原样保留，不重新 truncate——既有值已对齐）✓
- session init 断言 `existingPlan.createdAt == createdAt` 保持 ✓
- createNew 内 createdAt 只捕获一次（`clock.instant()` 单次调用）✓；`id = idSupplier()` 仍只调用一次 ✓
- recomposition 不生成新 createdAt：session 由 ViewModel 持有（`startCreateSession` 有 `if (_editSession.value == null)` 守卫，L124-127），Compose 重组不触碰 ✓
- retry save 不改变 createdAt：session 复用直至 close ✓（`closeEditSession` 仅在非 Running 时置 null，L133-137）
- draft.createdAt = session.createdAt（单一来源，L124）✓

## Repository semantics verdict

`RoomMedicationPlanRepository` 零 diff（`git diff data/repository` 空）✓：
- Inserted/Created/Updated/NoChange/Invalid/NotFound/StorageFailure 语义全部未变 ✓
- existing plan ID → `Updated` 路径（L90-94，documented）✓——PlanSaveRegressionTest `existingPlanIdUsesDocumentedUpdatePathWithoutDuplicatingRows` 实证（Updated + rawPlanCount=1）✓
- slot collision → 事务内约束异常 → 整体回滚（`withTransaction` L51；slot 冲突抛 `RepositoryPersistenceException`）→ ViewModel StorageFailure ✓——`slotIdCollisionRollsBackPlanAndMapsStorageFailure` 实证（plan 0 行 + blocking 行保留 + 0 reminder）✓
- 无 upsert fallback 超批准、无 legacy writer、无重复 plan 行 ✓

## Error mapping verdict

ViewModel 错误映射（MedicationPlanViewModel.kt）：
- InvalidDraft（draft 校验失败，L149-155）→ `InvalidDraft` → "请检查方案输入" ✓
- `PlanSaveResult.Invalid`（L174-177）→ `RepositoryInvalid` → "方案数据无效" ✓
- `DeleteResult.NotFound`（L198-201）→ `NotFound` → "方案不存在" ✓
- 存储异常（L161-164/L188-191/L212-215/L249-252）→ `StorageFailure` → SAVE="保存失败"/DELETE="删除失败"/SET_ENABLED/RESCHEDULE=unknown ✓
- 其他 runtime（launchOperation L288-292）→ `UnexpectedFailure` → unknown ✓
- **CancellationException 不吞**：三层（saveDraft 内 L159-160、launchOperation L285-287）均先捕获并 rethrow；测试 `repository cancellation returns operation to idle instead of unknown failure`（Idle 而非 Failure）✓
- `catch (_: RuntimeException)`（L288）在 `catch (CancellationException)`（L285）之后——顺序正确，不会吞 cancellation ✓
- SQL/exception message 不暴露到 UI（UI 只用映射后的 stringResource）✓
- delete/save 文案不混淆（按 `failure.operation` 分派，MedicationPlansScreen.kt）✓

## IllegalStateException classification verdict

**P2-1（唯一代码级发现）**：
- `catch (_: IllegalStateException)`（L161/L188/L212/L249）捕获**所有** IllegalStateException 并归为 StorageFailure
- Repository 层存储异常**已统一**为 `RepositoryStorageException extends IllegalStateException`（RepositoryStorageException.kt:9-12；SQLiteException→RepositoryPersistenceException、SQLiteConstraintException→RepositoryConstraintException、MappingError→CorruptAggregateException）
- 因此该 catch **能覆盖全部真实存储失败**（不窄）✓；但也**宽于必要**：未来编程/不变量错误若抛非存储 IllegalStateException（如 Room 框架状态、错误状态机）会被标为"保存失败"而非 unknown——**掩盖诊断性**，不伪装成功、无数据风险
- 回答任务问题：(a) 非所有 IllegalStateException 都是存储失败；(b) 可能把编程错误标为保存失败（当前代码无此类路径——draft 校验走 InvalidDraft、mapper 不变量走 Invalid、scheduler 错误走 FAILED 副作用）；(c) Repository 已有更精确的 `RepositoryStorageException` 类型；(d) 应收窄 catch 为 `RepositoryStorageException`（与 `RecordDoseEventAction`/`ContractReminderDeliveryWork` 的既有模式一致）
- **判定 P2**（非 P1：不吞 cancellation、不转成功、不破坏数据、当前无被误归类的已知路径）——最小修复：`catch (_: RepositoryStorageException)` + 对应测试；不阻止提交

## Exact-scenario regression verdict

`PlanSaveRegressionTest.exerciseExactUserScenario`（fresh + migrated 两模式各执行）：
- 输入：name=E2 / ORAL / E2 / 2mg / DAILY / 01:00, 09:00, 17:00；**clock = `2026-08-07T01:02:03.123456789Z`（真非毫秒）** ✓
- normalized createdAt = `2026-08-07T01:02:03.123Z`（`PERSISTED_CREATED_AT` 断言 L190/L199）✓
- 3 slots、position 0/1/2、UUIDv5 独立且 deterministic（`ScheduledDoseSlotId.generate` 重算对照）✓
- save = Created + reminder 1 次 ✓；close/reopen 持久化 ✓；第二个无关 08:00 plan Created ✓；user_version=3 ✓
- **走真实生产路径**：真实 ViewModel + `ProductionRepositoryProvider` + production mapper/repository + 真实 disposable Room ✓

## Fresh-v3 verdict

`exactUserScenarioPersistsAndReopensInFreshV3`：disposable file-backed Room v3（`plan-save-regression-fresh.db`）+ 生产路径 + exact scenario + reopen ✓；无 production DB（DB 名独立 + Before/After 清理 db/wal/shm/journal）✓

## Synthetic v2→v3 verdict

`exactUserScenarioPersistsAndReopensAfterSyntheticV2Migration`：
- `MigrationTestHelper.createDatabase(MIGRATED_DATABASE, 2)`——**从 committed v2 schema 起步**（MigrationTestHelper 用 schema 2 JSON 建库）✓
- `insertLegacyPlan`：v2 列结构原始行插入 ✓
- `runMigrationsAndValidate(3, true, MIGRATION_2_3)`——**实际执行 MIGRATION_2_3 + validate** ✓（非直接建 v3 伪称 migrated）
- exact scenario save + reopen + `expectLegacyPlan`（legacy 行迁移后保留）断言 ✓
- 无 production DB ✓

## Compose synchronization verdict

`MedicationRecordsScreenTest.createSuccessClosesEditorAfterContractInsert` 变化：
- 仅新增 `waitUntil(5_000L) { saveButton.assertIsEnabled() }` 后点击（与 `MedicationPlansScreenTest` 既有 `plan-save` 等待模式一致）✓
- 不改 production、不放宽断言（assertIsEnabled + 后续断言原样）、无 arbitrary sleep（Compose waitUntil 是条件轮询）、5s 超时为既有测试模式（非掩盖死锁的延长——该方法原为直接点击，新等待是真实同步修复，报告 §8 披露 API 33 首次复现的 Compose timeout 由此消除）✓
- isolated 与 suite 均通过（我的双 phone 全量 104/104 含该 suite 验证）✓
- 无测试删除 ✓

## Dual-phone verdict

设备核查：`emulator-5554` = Evolune_API33_Migration（API 33、emulator）、`emulator-5558` = Pixel_7（API 35、emulator）；`emulator-5556` = Wear（未用于 phone 验收）；**真机 `R5CW21W4THE` = SM-S918B 在线**（独立核查与报告 §9 一致）

独立执行（本审阅）：

| 验证 | 设备 | 结果 |
|---|---|---|
| PlanSaveRegressionTest focused | API 33 | **4/4**（XML tests=4 failures=0 errors=0 skipped=0）|
| PlanSaveRegressionTest focused | API 35 | **4/4** |
| 全量 connected | API 35 | **104/104**（XML 核实）|
| 全量 connected | API 33 | **104/104**（XML 核实）|
| targeted JVM（Editor+ViewModel）| — | **20/20**（8+12，XML 核实）|
| 全量 App JVM | — | **42 suites / 362 / 0 / 0 / 0**（XML 逐文件累加）|
| assembleDebug | — | PASS |

注：本工作树首次构建因 `app/debugkeystore.jks`（基线 build.gradle 引用的本地忽略文件）缺失而失败——已从旧工作树复制该 git 忽略文件补全构建环境（不进入版本控制），随后全部通过。这是环境问题，非代码缺陷。

## Real-device verdict

报告 §9 场景（用户手动验证）与设备核查一致（SM-S918B / Android 16 / API 36 在线）：
- 可证明：**fresh installed 当前 v3 debug 包 + 真实设备时钟 + exact E2 scenario save 成功、editor 关闭、plan 显示、重启保留**；第二 08:00 plan 成功 ✓
- 不可证明：保留旧 debug 包数据的 in-place upgrade；原设备旧数据库状态下修复后 save；原始真实 DB migration compatibility（详见下节）
- 报告如实区分，未夸大 ✓

## Destructive-reinstall/process-deviation verdict

**判定：disclosed non-blocking process deviation（P2 级披露，不升级）**

事实链（报告 §9 + 独立核查）：
1. 第一次 in-place install 因 certificate mismatch 正确停止（符合原任务"签名不兼容 → STOP"）✓
2. 用户随后**明确确认已有备份并授权 destructive reinstall**——授权在先，非未经授权 ✓
3. 仅卸载 `io.github.yuninggu.evolune.debug` 一个 package；未 `pm clear`、未 pull/read/export DB、未操作其他 package ✓
4. 安装后本地 APK 与已装包证书 SHA-256 相同（报告给出 `2cf0a50c...`；与"先 STOP 后授权重装"的签名一致链路自洽）✓
5. 真机验证在 fresh install 上成功 ✓

理由：
- 无数据安全违规（见 Data-safety verdict）→ 非 P0/P1
- 验证含义局限如实披露：fresh-install 路径已验证；**preserved-data in-place 路径未验证**——synthetic v2→v3（真 migration + validate）补 migration 回归，但不等同 real-device preserved-data 验证
- **synthetic migration + 代码证据足以覆盖当前修复的内部 gate**：根因（精度规范化）不涉及数据迁移；fresh v3 与 migrated v3 均实证；既有存储数据已 millis 对齐（无修复需求）→ 不构成产品风险升级
- 按任务 §19 分类 → **P2（披露的验证环境/流程限制）**，代码正确性不受影响

## Data-safety verdict

- 无未经授权删除（用户确认备份后授权，仅限该 debug package）✓
- 未读取/复制/导出真实 DB（报告 §12 声明 + 实施过程一致；本审阅未接触任何真实数据）✓
- 未操作其他 package ✓；无敏感数据进入 repo/report（无 DB 内容、无健康数据、无密钥——报告不含任何用户数据）✓
- 修复本身全部使用合成 fixtures ✓

## Schema/contract verdict

- `git diff` 空：schemas、MIGRATION_2_3、Domain、DAO/Entity、Repository contract、Room Repository、JSON、PK、Wear protocol、Gradle/Manifest ✓
- schema identity hash 沿用锁定值（v2 `a8036e3f…`、v3 `c5f5e02c…`；v3 canonical SHA `044013C0…`——6B/6C 已独立复算，本批零 diff）✓
- 修复零 schema/contract 接触 ✓

## Test-count verdict

独立 XML 核对（非复述）：
- Full App JVM：**42 suites / 362 / 0 failures / 0 errors / 0 skipped**（XML 累加；= 359 + 3：EditorTest +1、ViewModelTest +2）✓ 与报告一致
- PK 49 / Wear JVM 1：未单独重跑（本批未触及 pk/ 与 wear/ 模块；全量回归绿）——报告值与 6B/6C 历史一致
- API 33/35 full：**104/104 各机**（XML 核实；= 98 + PlanSaveRegressionTest 4 + MedicationPlansScreenTest 新增 2？——98 + 4 + 2 = 104 ✓：PlanSaveRegressionTest 4 新增 + MedicationPlansScreenTest 新增 2（storageFailureShowsSaveFailure + unexpectedFailureIsTheOnlySavePath）+ 1 改名（saveSuccessClosesEditor → saveSuccessClosesEditorWithoutUnknownError，同测试）——**对账成立**（98+4+2+0 = 104）✓
- 0 failures/errors/skipped；无 @Ignore（grep）；无旧测试删除（6C 的 98 全保留 + 5 新增/强化）；无 timeout/sleep 放宽（waitUntil 为条件轮询）；fixtures 全合成 ✓

## Findings

### P0
None.

### P1
None.

### P2

**P2-1 — ViewModel `catch (IllegalStateException)` 宽于必要**
- Severity: P2
- 文件: `app/src/main/java/io/github/yuninggu/evolune/viewmodel/MedicationPlanViewModel.kt:161,188,212,249`
- 问题: 捕获所有 IllegalStateException 归为 StorageFailure；Repository 已统一 `RepositoryStorageException`（其唯一实现基类）——catch 应收窄为该类型
- 触发条件: 未来非存储 IllegalStateException（如 Room 框架状态、编程不变量）→ 错误标为"保存失败"
- 影响: 诊断性掩盖；无成功伪装、无数据风险、不吞 cancellation（CancellationException 先捕获）
- 依据: RepositoryStorageException.kt:9-12；RoomMedicationPlanRepository 全部存储异常均为该类型；`RecordDoseEventAction` 既有收窄 catch 先例
- 最小修复建议: `catch (_: RepositoryStorageException)` + 更新对应测试（不阻止提交，可在后续批处理）
- 是否阻止 Batch 7 baseline 合并: 否

**P2-2 — destructive reinstall 导致的真机验证边界（披露）**
- Severity: P2（disclosed process deviation）
- 事实: 经用户明确授权（已有备份）后仅卸载/重装 debug package；无数据安全违规
- 影响: 真机结果覆盖 fresh-install 路径；**in-place upgrade 与原始 DB preserved-data 路径未验证**——synthetic v2→v3（真迁移）补 migration 回归，报告如实披露，未声称 preserved-data 验证
- 是否阻止: 否（代码正确性不受影响；synthetic migration + fresh/migrated 双模式实证覆盖当前修复内部 gate）
- 建议: 后续 release 前的真机 upgrade 验证中再覆盖 preserved-data 路径（Batch 8 gate 相关）

## Independent validation

实际执行（本会话，read-only）：

| 项 | 结果 |
|---|---|
| Git 边界（branch/status/diff/--check/--cached/ls-files/merge-base/log）| 分支正确、12 文件、batch-6 祖先、暂存区空 |
| 完整读取 | 报告 194 行、MedicationPlanEditor（旧版经 git show HEAD）、MedicationPlanViewModel、MedicationPlanBottomSheet/MedicationPlansScreen diff、strings、4 测试文件 diff、PlanSaveRegressionTest 425 行、MedicationPlanEntityMapper（L120-194）、MappingResult、RoomMedicationPlanRepository（L40-109）、RepositoryStorageException |
| 根因链独立证明 | 8 环全成立（旧代码 + mapper L139 lossless 校验 + L48 Invalid 映射 + 旧 UI unknown 折叠）|
| `:app:testDebugUnitTest --tests *MedicationPlanEditorTest --tests *MedicationPlanViewModelTest --rerun-tasks` | **20/20**（8+12，XML 核实）|
| `:app:connectedDebugAndroidTest -P...class=PlanSaveRegressionTest --rerun-tasks` | API 33 **4/4**、API 35 **4/4**（XML 核实）|
| `:app:connectedDebugAndroidTest --rerun-tasks` | API 35 **104/104**、API 33 **104/104**（XML 核实）|
| `:app:testDebugUnitTest --rerun-tasks` | **42 suites / 362 / 0 / 0 / 0** |
| `:app:assembleDebug` | PASS |
| 越界 diff | schemas/migration/core/DAO/Entity/repository/Manifest/Gradle/wear 全空 |
| @Ignore | 无 |
| 设备核查 | 5554=API33、5558=API35、5556=Wear、**R5CW21W4THE=SM-S918B 真机在线** |
| 环境补全 | `app/debugkeystore.jks`（git 忽略）从旧工作树复制以恢复构建；版本控制内容不变 |

未独立运行：PK 49 / Wear JVM 1 单独命令（本批零触及对应模块；全量回归绿）、lint/KSP（报告声明 0 errors）。

## Final decision

**APPROVE WITH P2**

- 代码修复本身是否正确：**正确**（canonicalization ownership 正确、根因 8 环实证、零 schema/contract 改动、双模式 regression + 真实 Room + 双 phone + 真机 fresh-install 验证通过）
- real-device preserved-data path 是否已验证：**未验证**（destructive reinstall 后 fresh install；如实披露）
- synthetic migrated-v3 是否足够支持当前 internal gate：**足够**（真 migration + validate + exact scenario 双模式；修复本身不涉及数据迁移）
- **是否允许 commit implementation/report：是**
- **是否允许 commit review：是**
- **是否允许打 regression-fix tag：是**
- **是否允许 fast-forward/cherry-pick/merge 回 `phase1/batch7-design`：是**
- **是否允许随后进行 MedicationPlan UI sizing fix：是**
- **Batch 7 implementation 是否仍禁止：是**（直到 Batch 7 design review 完成）
- **Room v3 是否仍不可发布：是**
- 可推迟事项：P2-1（异常分类收窄）、P2-2（release 前真机 upgrade 验证）
