# Evolune Phase 1 Batch 3C 代码审阅报告

**审阅日期**: 2026-08-04
**审阅者**: DeepSeek（独立高级代码审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch3c-room-repositories`（HEAD: `a97dec7`，前置 tag `phase-1-batch-4c`）
**方式**: 只读审阅；未修改/暂存/提交/打标签任何文件；未接入生产入口；未开始下一批

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0**: 0
- **P1**: 0
- **P2**: 2（均为实施方已声明的过渡风险：未生产接线；Route/Ester 暂依赖 PK）
- **是否允许提交**: 是
- **最大剩余风险**: 无 P0/P1。聚合保存原子性经真实 SQLite trigger 回滚证明；幂等/冲突/revision 语义与 ADR-015 一致；schema/migration 零变化。v3 仍处 ADR-016 内部不可发布区间。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch3c-room-repositories` ✓ |
| 前置 tag | `phase-1-batch-4c` 在历史中 ✓ |
| 修改文件 | 6 个（3 DAO + 3 mapper）✓ |
| 新增文件 | 7 个（aggregate + repository/ 3 文件 + androidTest + JVM 测试 + 报告）✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| schema/migration/Entity/AppDatabase 变化 | 无（git diff 全空）✓ |
| UI/JSON/Widget/Reminder/Wear/Manifest/Gradle 变化 | 无 ✓ |
| 敏感数据 | 无真实/派生健康数据 ✓ |
| 下一批 | 未开始 ✓ |

---

## Contract implementation matrix

**真实接口（仓库核实，非报告推断）**：

| Contract 方法 | 实现 | 一致性 |
|---|---|---|
| `DoseEventRepository.observeAll()` | `dao.observeAllForRepository()`（occurredAt DESC, id ASC）→ map+orThrowCorrupt | ✓ |
| `getById(UUID)` | `getEventById` → toV3Domain → orThrowCorrupt | ✓ |
| `findOccurredBetween(start, end)` | `getEventsByOccurredAtRange`（>= start AND < end，半开，ASC）| ✓ |
| `getEventsForPk(asOf)` | 30 天窗口 + 20 条回退，两种分支顺序保留（测试验证）| ✓ |
| `insert(event)` | withTransaction：INSERT IGNORE → Inserted / 同内容 Idempotent / 异内容 Conflict | ✓ |
| `update(event, expectedRevision)` | CAS（WHERE revision=expected）：Updated / NoChange / NotFound / RevisionConflict / Invalid | ✓ |
| `delete(id)` / `deleteAll()` | 行数检查 → Deleted / NotFound | ✓ |
| `MedicationPlanRepository.observeAll()` | `observeAllPlanAggregates()`（createdAt DESC, id ASC）| ✓ |
| `observeEnabled()` | `observeEnabledPlanAggregates()` | ✓ |
| `getById(UUID)` | `getPlanAggregateById` → toDomainOrThrow | ✓ |
| `save(plan)` | withTransaction 原子全替换（详见下）| ✓ |
| `setEnabled(id, enabled)` | 行数检查 + 验证 → Updated / NoChange / NotFound | ✓ |
| `delete(id)` / `deleteAll()` | 级联验证（countSlots==0）→ Deleted / NotFound | ✓ |

- 无新发明公开接口、无重载绕开、无异常直接上抛（runStorageOperation 包装）、无吞异常、无全映射 NotFound ✓
- 未修改任何 contract 文件 ✓

---

## Findings

### F1 (P2) — Repository 尚未接入生产调用方（实施方已声明）

- **严重程度**: P2
- **文件**: `docs/phase-reports/PHASE_1_BATCH_3C_REPORT.md:245-248`；`RoomDoseEventRepository.kt`/`RoomMedicationPlanRepository.kt` 无调用方
- **问题**: 新 Repository 类未被 MainActivity/ViewModel/JSON/Reminder/Widget/Wear 使用；现有旧 Repository 路径不变。
- **影响**: 计划内边界（ADR-015 3C 之后才有生产接线批次）。无运行时风险——新代码不进入生产路径。
- **依据**: ADR-015/PHASE_1_DESIGN §Batch 3C 明确"生产接线"在后续批次。
- **最小修复方向**: 不修复；留待生产入口接线规划。
- **是否阻止提交**: 否

### F2 (P2) — `core.model` 暂时依赖 `pk.Route`/`pk.Ester`（实施方已声明）

- **严重程度**: P2
- **文件**: `core/model/DoseEvent.kt`、`core/model/MedicationPlan.kt`（import pk 枚举）；mapper 反向映射显式 `when`
- **问题**: 过渡依赖已由 Batch 3B 接受；本批确认无循环依赖、无错误映射（Route/Ester 双向显式 `when` 且 `toLegacyStorageRoute`/`toLegacyStorageEster` 与 `routeFromLegacyStorage`/`esterFromLegacyStorage` 完全互逆）。
- **影响**: 无持久化漂移风险；技术债留待独立 ADR 迁移枚举。
- **依据**: ADR-015 过渡取舍。
- **最小修复方向**: 不修复。
- **是否阻止提交**: 否

**其余无问题（None）。** 特别确认：未发现聚合写入非原子、rollback 后部分数据、旧 slots 丢失、同 ID 异内容被覆盖、假 epoch 0、schema 修改、敏感数据入库等 P0 情形。

---

## DoseEvent repository verdict

**符合设计。** `RoomDoseEventRepository.kt`：

- **保存路径**：Domain 验证（revision==1、`toV3Entity` 映射含精度/往返校验）发生在写库前（Invalid 无写入）✓；`occurredAtEpochMillis` 精确保存（测试用 `1_700_000_000_123L` 全字段断言）✓；合法 epoch 0 正常保存（`eventSupportsEpochZero...`）✓；`timeH` legacy 字段与 occurredAt 双写一致（mapper 往返校验 + raw SQL 断言 `1_700_000_000_123L/3_600_000.0`）✓；无当前时间/当前时区兜底 ✓；zoneId/localDate/slotId 可空（null 保持 null）✓；source/status 显式映射 ✓；revision 首次为 1 ✓
- **幂等/冲突**：同 ID 同内容 → `Idempotent`（无第二行）；同 ID 异内容 → `Conflict`（无覆盖，原数据不变）✓ —— 测试 `eventInsertDistinguishesIdempotentAndConflict` 验证
- **update**：revision 匹配 + meaningful edit → 递增 +1；相同内容 → `NoChange` 不递增；`RevisionConflict`/`NotFound`/`Invalid(expectedRevision=0)` 全部测试 ✓
- **亚毫秒拒绝**：`Instant.ofEpochSecond(0,1)` → `Invalid` + 无行写入（**不写假 epoch 0**）✓ —— 由 toV3Entity 的 `InvalidOccurredAtPrecision` 精度回读校验保证
- **DAO 返回值全部检查**：insert(-1)/update(1)/delete(1) ✓；无 REPLACE（`OnConflictStrategy.IGNORE` 仅幂等检测）✓
- **读取路径**：全字段映射 + timeH/occurredAt 一致性校验（InconsistentEventTime）+ 损坏结构化失败（CorruptAggregateException）+ 不静默修复 + 不写库 + 不泄漏 SQLite 异常（RepositoryStorageException 包装）✓

---

## MedicationPlan repository verdict

**符合设计。** `RoomMedicationPlanRepository.kt` + `MedicationPlanAggregateEntity`：

- aggregate 定义：plan + @Relation slots（@Transaction 一致快照）；映射层显式 `sortedBy position` 消除 @Relation 顺序不确定性 ✓
- slot 校验链（`toDomainSlots`/`toPersistenceAggregate`）：planId==plan.id、position==index（连续/唯一/零基）、localTime canonical HH:mm 分钟精度、**UUIDv5 expected ID 校验**（UnexpectedSlotId 拒绝随机/错误 ID）、legacy timeOfDay 与 slots 语义一致性（InconsistentPlanTimes）✓
- 不产生：随机 slot ID（生成器校验）、重复/间断 position、跨计划 slot、错误 UUIDv5、非 canonical localTime ✓
- 测试覆盖：空/单/多 slot、00:00/08:30/23:59、顺序/重复、固定向量 `17d1fd14-9d70-5344-beaa-0b158c9f62f4` 硬编码、更新替换、更新为空、字段更新不丢 slots、幂等 NoChange ✓

---

## Aggregate transaction verdict

**符合设计（原子性经真实 SQLite 失败证明）。**

`save` 算法（RoomMedicationPlanRepository.kt:45-97）：
1. Domain→persistence 映射验证（写库前，Invalid 无写入）✓
2. 事务内读 existing aggregate；identical → `NoChange` ✓
3. 无 existing → `insertPlanChecked`（ABORT，-1 → 抛）✓；有 → `requireSinglePlanUpdate`（行数≠1 → 抛）✓
4. `deleteSlotsForPlan` 返回数必须 == 旧 slots 数 ✓
5. `insertSlotsChecked` 批量（数量 + 无 -1 校验）✓
6. 事务内重读 aggregate 验证 == plan，否则抛 ✓
7. 全部在**单个 `database.withTransaction`**（真实 Room transaction，非 mutex）✓

**无 P0 情形**：无 catch-then-commit（异常直接传播出 withTransaction → Room 回滚）；无先提交 plan 再独立写 slots；无失败留空 slots；无部分插入。plan 更新、slots 删除、slots 插入同一 transaction ✓。

`setEnabled`/`delete`/`deleteAll` 同样事务 + 行数检查 + 级联验证 ✓。

---

## Rollback verdict

**真实回滚证明（非 mock、非 validation 拒绝）**：`RoomRepositoryTest.kt:381-409`：

- 构造：original plan（plan 行 + 1 slot + legacy timeOfDay）与 unaffected plan 均已保存 ✓
- 真实失败：`BEFORE INSERT ON scheduled_dose_slots` trigger `RAISE(ABORT)` —— 失败发生在 **slot 插入阶段、transaction 内、plan 行已更新/旧 slots 已删除之后**；不是写库前 validation 拒绝，不是 mock，未跳过 DAO 调用 ✓
- 失败后读取真实数据库验证：
  - 原 plan 逐字段不变（`getById == original`，含 name/时间等全部字段）✓
  - 原 slots（id/localTime/position）逐项不变（`originalRows` 对比）✓
  - legacy timeOfDay 逐字不变 ✓
  - 无部分删除/插入 ✓
  - 其他计划（unaffected）及 slots 不变 ✓
- 异常类型：`RepositoryConstraintException`（SQLite trigger ABORT → 包装）✓

超出最低要求（不仅比较数量/仅验证 plan 存在）—— 逐字段 + 逐行对比 ✓

---

## Legacy timeOfDay verdict

- 写入/更新：`timeOfDay = slots.map { canonicalLocalTime }`（按 position 顺序、重复保留、canonical HH:mm、空列表 → `[]`）✓ —— **slots 为 v3 权威来源，legacy timeOfDay 为同步派生的兼容表达**
- 不保存 offset/timezone/非零秒；不删除 legacy 字段 ✓
- 读取时：legacy 与 slots 不一致 → `InconsistentPlanTimes` 结构化失败，**不静默修复**（`planReadRejectsLegacySlotMismatchWithoutRepairingIt` 验证原值不变）✓
- 测试覆盖 10 项清单全部命中（空/单/多/顺序/重复/更新/更新为空/字段更新不丢/一致性/损坏不修复）✓

---

## Slot identity and ordering verdict

- 锁定 UUIDv5 v1 namespace + canonical 字符串；无随机 UUID ✓
- 固定向量硬编码（`FIXED_VECTOR_SLOT_ID = 17d1fd14-9d70-5344-beaa-0b158c9f62f4`）✓ —— 非纯生产 generator 循环覆盖（有独立锚点 + 独立错误断言）
- position 与输入顺序对应；不按时间排序；不去重 ✓
- 相同 localTime 不同 position → 不同 ID（`planPreservesOriginalOrderAndDuplicateTimes` 验证 3 个唯一 ID）✓
- update 后旧 slots 全部替换（`none { it.id in oldSlotIds }`）无残留/无 orphan ✓；其他 plan slots 不受影响 ✓

---

## Conflict, idempotency and revision verdict

- **DoseEvent**：首存 revision=1；同 ID 同内容 → Idempotent（不写第二行）；同 ID 异内容 → Conflict；meaningful edit → revision+1；相同内容重复 update → NoChange 不递增 —— 与 ADR-015 完全一致 ✓
- **MedicationPlan**：contract 无 conflict 成员、无 plan revision（ADR-015 已锁定）→ 同 ID 异 aggregate 是**原子全替换返回 Updated**，同 ID 同 aggregate → NoChange；仅 plan 字段/仅 slot 变化均走全替换 ✓ —— 未以常见 upsert 语义替代 ADR-015；实现未暗中通过 save 覆盖 conflict（contract 无 conflict，报告 §6 明确说明）✓

---

## DAO verdict

- 只增加 Repository 所需最小操作；静态 SQL、参数绑定 ✓
- 排序稳定：event 查询含 `id ASC` tiebreaker；slots `ORDER BY position ASC` ✓
- insert/update/delete 全部返回 row count 且被检查 ✓；批量 insert 数量检查 ✓
- 无 INSERT OR REPLACE（避免隐藏 cascade 副作用）✓
- 无 UI/Widget/Wear-specific query；无新增 Flow 超出 contract 要求 ✓
- aggregate read 用 `@Transaction`（一致快照）✓；`@Relation` 顺序不确定性由映射层 `sortedBy position` 显式消除 ✓

---

## Mapper verdict

- Batch 3B v2 只读 mapper（`toDomainDoseEvent`/`toDomainMedicationPlan`）未改动，43 个旧测试原样通过 ✓
- 新增 v3 双向映射为独立函数，不触碰 legacy 路径 ✓
- `MappingResult` 只**新增**错误类型（InvalidOccurredAtPrecision/InconsistentEventTime/InvalidZoneId/InvalidLocalDate/InvalidSource/InvalidStatus/InvalidSlotPlan/InvalidSlotPosition/InvalidSlotLocalTime/UnexpectedSlotId/InconsistentPlanTimes），未删改旧类型 ✓
- 全字段持久化/读取 ✓；无效 enum/UUID/time → 结构化失败；无静默默认值；无假 epoch 0（精度回读 + 往返校验）✓
- Route/Ester 双向显式 `when` 互逆 ✓；mapper 无 Context/数据库/Repository 依赖 ✓

---

## Error and privacy verdict

- 错误分层：`Invalid`（输入映射失败，业务结果）；`CorruptAggregateException`（读取损坏 aggregate，带结构化 MappingError）；`RepositoryConstraintException`/`RepositoryPersistenceException`（基础设施异常）✓
- SQLite message 不作为稳定协议（类型化异常类）；SQLiteException 不暴露给 API（包装）；cause 仅内部诊断 ✓
- 错误不含完整事件/dose/extras/完整计划时间表 ✓
- 不吞异常（CancellationException 直传）；constraint 与 conflict 不混淆（conflict 是业务结果，constraint 是异常）✓
- 数据损坏读取不自动修复（CorruptAggregateException + 原值不变）✓

---

## Test quality verdict

**独立核实（非采信报告）**：

- `RoomRepositoryTest`：**23 个测试逐一核对**（L62-430），覆盖事件全字段/epoch 0/正负/可空元数据/幂等/冲突/revision/NoChange/NotFound/Invalid/亚毫秒拒绝/范围/观察顺序/PK 双分支顺序/删除/约束异常；计划空/单/多/边界/固定向量/顺序/重复/更新替换/更新为空/字段更新/幂等/启停/级联/deleteAll/意外 ID/损坏读取/真实回滚/观察顺序 ✓
- 每测试独立 in-memory 数据库 + @After close ✓；合成数据 ✓；无执行顺序依赖 ✓
- failure 测试确实进入目标路径（trigger 在 INSERT 时触发，非预校验拒绝）✓
- expected 未全部由实现内部 helper 生成（固定向量硬编码 + 独立错误断言 + 原始 SQL 断言）✓
- `V3PersistenceMapperTest`：10 个新测试（43+10=53，JUnit XML 实测 6 suites/53）✓ —— 双向 roundtrip、亚毫秒拒绝、影子不一致、无效元数据、意外 slot ID、跨计划 slot、重复/间断 position、非 canonical 时间、legacy 不匹配 ✓
- 全量 JVM：26 suites / **231 tests** / 0 failures（实测）✓

---

## Schema and architecture verdict

- `git diff` 对 schemas/migration/三 Entity/AppDatabase → **全空** ✓
- Room version=3、schema 3 identityHash `c5f5e02cb04b048ca28fe96a74d61606`、SHA-256 `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`（实测不变）✓
- 无第二个 production builder、无新 Gradle module、无新依赖 ✓
- DAO 返回 persistence 类型；Repository 返回 Domain/contract 结果；mapper 无 UI 依赖 ✓
- Repository 接口与 Domain 模型未修改 ✓

---

## Report accuracy verdict

逐项核对 `PHASE_1_BATCH_3C_REPORT.md`：

| 声明 | 独立核实 |
|---|---|
| 23/23 Repository instrumentation（emulator-5556）| ✓ 独立重跑（Starting/Finished 23 tests）|
| 66/66 全量 connected | ✓ 独立重跑（Finished 66 tests, BUILD SUCCESSFUL）|
| App 231/231；mapper 53/53（43+10）；core 47；PK 49；Wear 1 | ✓ 26/231 与 6/53 实测；其余此前批次核实 |
| App/Wear build、lint 0 errors | ✓ 独立重跑 |
| schema 2/3 无变化；identity/hash 不变 | ✓ git diff 全空 + SHA-256 实测 |
| 未生产接线；Route/Ester P2；v3 不可发布 | ✓ 与代码一致，如实声明 |
| 未夸大：不声称 UI/JSON 已切换、不声称真实数据库验证、不声称 Phase 1 完成 | ✓ 全部如实 |

报告与代码/结果一致。

---

## Independent validation executed

以下全部为本轮实际执行（`JAVA_HOME=C:\Program Files\kedou\jre`，`ANDROID_SERIAL=emulator-5556`）：

| 命令 | 结果 |
|---|---|
| `adb -s emulator-5556 shell getprop sys.boot_completed` / `ro.build.version.sdk` | 1 / 33 ✓ |
| `:app:connectedDebugAndroidTest -P...class=...RoomRepositoryTest --rerun-tasks` | **PASS** — Starting/Finished 23 tests |
| `:app:connectedDebugAndroidTest --rerun-tasks`（全量）| **PASS** — Finished 66 tests, 0 failed |
| `:app:testDebugUnitTest --rerun-tasks`（全量）| **PASS** — 26 suites / 231 tests（JUnit XML 实测）|
| `:app:testDebugUnitTest --tests "data.mapper.*" --rerun-tasks` | **PASS** — 6 suites / 53 tests |
| `:app:testDebugUnitTest --tests "pk.*" --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS（0 errors）|
| `git diff`（schemas/migration/Entity/AppDatabase）| 全空 ✓ |
| schema 3 SHA-256（Get-FileHash）| `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` ✓ |

未声称执行任何未实际运行的命令。

---

## Final decision

### **APPROVE WITH P2**

无 P0/P1。两个 P2（未生产接线、Route/Ester 过渡依赖）均为计划内过渡风险，不阻止提交。

**提交前必须处理事项**：无。

**可推迟事项**：
- 生产入口接线（Batch 5/6 规划：HRTViewModel、记录 UI、reminder、Widget、Wear 逐步改走 contract）
- Route/Ester 枚举迁移（需独立 ADR）

**是否建议提交 Batch 3C**：是。提交建议信息：`feat: add room-backed repositories with atomic plan aggregate save`，并打标签 `phase-1-batch-3c`。

**是否建议提交后进入生产入口接线规划**：是，但仅在 3C 提交并打标签之后。接线需按 PHASE_1_DESIGN Batch 5/6 分批（双读双写 → 入口切换），不得一次性全量切换。

**v3 是否仍不可发布**：**是**。Room v3 仍处 ADR-016 §19.1 内部不可发布区间；Batch 3C 完成不等于 Phase 1 完成或可发布，最终 release 需满足 Batch 8 全部门槛（含入口转换与真实设备验证）。

---

*审阅结束。最终工作树：仅原 Batch 3C 文件 + 本审阅报告；未修改任何其他文件。*
