# Phase 1 Batch 7B Independent Review

Date: 2026-08-10
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-batch7b`
Branch: `phase1/batch7b-import-service`

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 3**（两项实现级 P2 + 一项继承设计 P2；无阻断项）
- Import service: **PASS**
- Repository reuse: **PASS**
- Idempotency: **PASS**
- Conflict: **PASS**
- Storage failure: **PASS**
- Partial summary: **PASS**
- Numeric-ID cutover: **PASS**
- Random-ID replay semantics: **PASS**
- HRT production cutover: **PASS**
- Legacy import retirement: **PASS**（零 production 调用者）
- Legacy export preserved: **PASS**
- 7A integrity: **PASS**
- Repository boundary: **PASS**
- PK boundary: **PASS**
- Widget boundary: **PASS**
- Wear boundary: **PASS**
- Custom medication boundary: **PASS**
- MedicationPlan boundary: **PASS**
- Schema/migration boundary: **PASS**
- **7B may be sealed: YES**
- **7C may begin after sealing: YES**（仅 7B 提交/review/tag 封存后）
- **Batch8: reserved**
- **Release: forbidden**

## Git/scope verdict

- 分支 `phase1/batch7b-import-service`（= `phase1/batch7-design` 同 HEAD `9733c6d`）✓
- **7A sealed baseline 经 git graph 独立确认**（不依赖 prompt 猜测）：`phase-1-batch-7a` tag → `ce07369`（7A review commit）→ 祖先：`9733c6d`（merge）→ `da3a3b6`（7A implementation）+ `ac0ce36`（design review tag `phase-1-batch-7-design-v1`）✓ 报告 §1 SHA 与 graph 一致
- 暂存区空；change set = 3 modified + 3 new（与预期一致）；无 APK/DB/截图/log/keystore（实施方临时 keystore 已移除，本审阅复用的也被移除）✓
- 无 7C/Widget/Wear/Custom/Plan/schema 实现 ✓

## Import-service architecture verdict

- 真实调用链（从代码确认）：`HRTViewModel.importFromMahiroJson` → `MahiroJsonV1ImportService.import` → `MahiroV1Codec.decode` → `MahiroV1DoseEventAdapter.toDomain` → `DoseEventRepository.insert` → Room ✓
- Service 只做 orchestration：不重新 parse JSON 字段、不重新 parse UUID/timeH/route/ester（全消费 codec/adapter 结果）✓
- 无 DAO/Entity/RoomDatabase/PK/UI 引用（grep 零命中）✓
- **源索引对齐正确**：`sourceEntryCount = events.size + diagnostics.size` + `eventIndex` 仅在非 diagnostic 条目推进——codec 过滤后的 events 列表与源索引精确对应（含 diagnostic 占位）✓（此逻辑被 storage 测试的 `sourceIndex=3` 断言验证）
- 不重新实现 7A semantics ✓

## Repository semantics verdict

- 直接消费 `insert` 结果：Inserted/Idempotent/Conflict/Invalid 独立计数 ✓
- **无第二套 conflict/idempotency 算法**：无 DoseEvent/Entity/hash 比较、无 pre-query、无 "exists then..."、无 upsert/overwrite/clear-and-import/rollback/legacy 重试 ✓
- Conflict 不覆盖（Repository 保证）+ 继续后续条目（测试 `conflict does not prevent a later insert`：2 insertCalls）✓
- Idempotent/Invalid 后继续（测试）✓

## Invalid/skip verdict

- codec entry diagnostics + adapter conversion failures → `invalidCount` + 继续（测试 `codec and adapter invalid entries are skipped before a later valid event`：numeric id + unknown route 各 1 invalid，第 3 项 insert）✓
- **Document-level decode failure 区分正确**：`Failure(Document)` + empty summary + **Repository 零调用**（测试 `malformed document returns typed failure without repository calls`）✓
- 空 payload → Success 零调用 ✓

## Numeric-ID verdict

- 正式行为锁定（生产 cutover 后）：valid string → preserve；missing/blank/malformed string → random UUID；**numeric JSON token → invalid/skip（无随机化、无 Repository 调用）** ✓（service 测试 + VM 测试双层锁定：`numeric id is invalid...` 断言 3 insert + 1 invalid + 独立生成 UUID 序列；`JSON numeric id follows formal v1 invalid behavior...` 断言 VM 层 invalidCount=1 + 第 2 项 insert）
- **未为 legacy 兼容偷收 numeric ID** ✓（实现严格按 7A 锁定语义）
- **cutover 已如实声明**：报告 §5 显式说明 numeric-token 行为"intentionally differs from the more permissive legacy path"且"compatibility question is a stop condition"——**任务 §14 要求满足** ✓

## Random-ID replay verdict

- missing/blank/malformed string → 每次独立 `UUID.randomUUID()`（注入 supplier 断言独立）——重复导入不保证幂等 ✓
- **报告准确表达**：§5 "Repeating such a payload is therefore not guaranteed to be idempotent. A stable valid ID is the only repeated-import identity guarantee."——**无虚假"所有重复文件导入均幂等"声称** ✓
- 测试：`repeated missing id payload creates independent identities`（两次导入各 Inserted，UUID 201/202）✓

## Storage/partial-summary verdict

- storage/infrastructure 异常（RuntimeException 非 Cancellation）→ **中止剩余** + `Failure(Storage(sourceIndex))` + 部分计数 ✓（CancellationException rethrow 保持取消语义）✓
- 测试精确：events [inserted, numeric-invalid, idempotent, fail, never-attempted] → summary inserted=1, idempotent=1, invalid=1, failed=1, **processed=4**（不含第 5 项）, sourceIndex=3, insertCalls=3, attempted=[1,2,3]——**partial 精确、未尝试项不计入 processed、已提交项不隐藏** ✓✓
- 无 rollback、无 generic Unknown（typed Document/Storage error）✓
- VM 层：`ImportResult.Error` 携带 partial counts + failedIndex + weight 回调不触发（storage 失败测试断言 importedWeight=null）✓

## HRT cutover verdict

- 最小 cutover：仅 import 调用点换 service（构造注入 `jsonImportService`，测试可注入 fake）✓
- 不重新设计 VM、不读 DTO 字段、不直接 per-item insert、不混 JSON parsing、不改无关 UI state ✓
- 既有 entry point/operation gate/coroutine 生命周期/UI state shape/成功 weight 回调时序保持（报告 §3 声明 + 代码证实）✓
- **P2-F1**：Document 与 Storage 失败在 VM 层都映射 `fail(IMPORT, StorageFailure)`（service 层 error 类型区分保留；`ImportResult.Error.failedIndex` 区分 Storage；Document 无 index）——UI 呈现可后续细化（设计允许），诊断性可接受

## Legacy-path verdict

- 全仓调用图：production import 唯一入口 = `HRTViewModel.importFromMahiroJson` → **仅新 service**；`Batch6MahiroJsonBridge.import` **零 production 调用者**（保留仅作兼容/tests——bridge 清理归 7C）✓
- legacy export 保留：`exportToMahiroJson` 仍走 `jsonBridge.export`（设计批准保留）✓ 未误切
- `MahiroJsonFormat` 未修改（legacy compat 14/14 通过）✓

## Test-quality verdict

- ImportServiceTest（12）：valid 全字段/混合结果独立计数/conflict·idempotent·Invalid 后继续/codec·adapter invalid 跳过/numeric-ID 锁定/独立随机 ID/storage 中止精确 partial/空 payload/malformed 零写/stable-ID replay/random-ID replay——**具体断言**
- **FakeRepository 质量**：scripted results（ArrayDeque 预设 InsertResult）+ 调用记录——**不重新实现 conflict/idempotency 算法**，无 self-validation ✓
- HRTViewModelTest（13，含 7B 新增）：storage partial（imported=1/failedIndex=1/weight 不触发/insertCalls=2）+ numeric-ID VM 级 ✓
- CutoverTest（androidTest，真实 Room）：首 import（Success 1/0/0 + 完整 metadata）→ **replay（0/1/0 真实幂等）** → **conflict doseMG=9.0（0/0/1 + 原行逐字段不变）**——**真实 production path（VM→service→Repository→Room）** ✓ 测试名与覆盖一致 ✓
- 无 @Ignore；fixtures 全合成 ✓

## Device-evidence verdict

- 独立核实：`emulator-5556` = **SDK 33 phone**（sdk_gphone64_x86_64，无 watch feature）；`emulator-5560` = **SDK 35 phone** ✓——报告 §8 设备声称准确；5558（Wear）未用于本批 ✓
- 本审阅独立重跑：API33 **2/2**、API35 **2/2**（XML 核实 0 failures/errors/skipped）✓——报告 instrumentation 证据复现 ✓
- keystore 缺失为环境问题（报告如实披露其临时处理；本审阅同样复制-使用-移除，未进入 change set）✓

## Forbidden-boundary verdict

- 7A sealed 文件（external/mahiro/v1 三个）`git diff` 空——**sealed semantics 未改** ✓
- 越界 diff 全空：schemas/migration/core/data/Gradle ✓；无 PK（49 回归一致）/Widget/Wear/Custom/Plan 实现 ✓
- 新文件 grep：无 Repository 实现/DAO/Entity/DB/PK/UI 引用（service 只依赖 `core.dataapi.DoseEventRepository` contract）✓

## Schema identity verdict

- 独立计算 git blob SHA-256：schema2 = **B8DA54ED...**、schema3 = **044013C0...** ✓（与报告/锁定值一致）
- schema3 identityHash = c5f5e02c...（工作树读取）✓；KSP 重跑后无 diff ✓；Room v3 internal/unreleasable ✓

## Report-accuracy verdict

`PHASE_1_BATCH_7B_REPORT.md`（231 行）逐项核对：

- baseline/tag/merge SHA 与 git graph 一致 ✓；architecture 与代码一致 ✓
- Repository 语义表（§4）与实现逐项一致 ✓；ID cutover 边界（§5）显式声明 + stop condition ✓；random replay caveat 正确 ✓；HRT cutover 最小化 ✓；export 保留 ✓
- 测试计数：7B focused **25**（ImportServiceTest 12 + HRTViewModelTest 13，独立 XML 一致）✓；7A 回归 25（12+13）✓；legacy compat 21（14+7）✓；全量 **46/402**（独立一致）✓；PK 49 ✓；双 phone 2/2 各机（独立复现）✓
- schemas 声明与独立计算一致 ✓；P2 四项如实（numeric/random/投影/ADR-015）✓；deferred 边界完整 ✓
- **无夸大**（未声称 7C 或 PK cutover）✓

## Findings

### P0
None.

### P1
None.

### P2

**F1 — VM 层 Document 与 Storage 失败共用 StorageFailure operation**
- Severity: P2
- File: `HRTViewModel.kt:281-293`（Failure 分支）
- Problem: service 层区分 `Document`/`Storage` error；VM 层两者均 `fail(IMPORT, StorageFailure)`（`ImportResult.Error.failedIndex` 仅 Storage 时非空）
- Impact: 用户可见失败呈现不区分文档错误与存储错误；诊断性部分保留（failedIndex）；设计允许后续 presentation 细化，不要求本批扩 UI
- Required fix: 后续 presentation refinement（可选）
- Blocks sealing? NO

**F2 — service `catch (RuntimeException)` 宽于必要**
- Severity: P2
- File: `MahiroJsonV1ImportService.kt:50`
- Problem: 捕获所有 RuntimeException 归为 Storage——Repository 存储异常统一为 `RepositoryStorageException`（RuntimeException 子类，可捕获，当前正确）；非存储 RuntimeException（编程错误）会被误标 Storage
- Impact: 当前 Repository 仅抛存储异常类，实际分类正确；与 RecordDoseEventAction 的既有先例（收窄 catch）一致可改进
- Required fix: 可收窄为 `RepositoryStorageException`（后续批次）
- Blocks sealing? NO

**F3 — 继承设计 P2（非本批新增）**
- Severity: P2
- File: 报告 §10
- Problem: 四项 P2（numeric-ID 有意收紧 / random-ID 非幂等 / v1 投影非无损 / ADR-015 枚举共享）由 7A/设计继承且如实声明——本批无新增 P2
- Blocks sealing? NO

## Independent validation

- Git: branch/HEAD/graph/tag（`phase-1-batch-7a`→ce07369 确认）/status/diff/--check/ls-files/stash 全部核实
- 完整读取: ImportService（119 行）、HRTViewModel import/export 函数、ImportServiceTest（328 行）、HRTViewModelTest diff、CutoverTest diff、7B 报告（231 行）、7A 实现（本会话上下文）
- 独立运行:
  - `:app:testDebugUnitTest --tests "*MahiroJsonV1ImportServiceTest" --tests "*HRTViewModelTest"` → **12 + 13 = 25/25**（XML）
  - 7A 回归（CodecTest + AdapterTest）→ **12 + 13 = 25/25**
  - legacy compat（MahiroJsonFormatTest 14 + Batch6DoseEventCompatibilityTest 7）→ **21/21**
  - `:app:testDebugUnitTest --rerun-tasks` → **46 suites / 402 / 0 / 0 / 0**（XML 累加）
  - PK（`io.github.yuninggu.evolune.pk.*`）→ **5 suites / 49 / 0**
  - `:app:assembleDebug` PASS；`:app:lintDebug` PASS（0 errors）；`:app:kspDebugKotlin --rerun-tasks` PASS；`git diff --check` PASS
  - API33（5556 phone）focused instrumentation → **2/2**（XML tests=2 failures=0 errors=0 skipped=0）
  - API35（5560 phone）focused instrumentation → **2/2**（XML 核实）
- 设备身份: 5556=API33 phone、5560=API35 phone（无 watch）——报告证据复现
- Schema: git blob SHA-256 独立计算匹配（B8DA54ED.../044013C0...）；identityHash 读取匹配
- 未运行: 全量 connected（不在 7B 门槛；focused 2/2 双机覆盖 cutover 测试）

## Final decision

**APPROVE WITH P2**

批准标准逐项：P0=0 ✓；P1=0 ✓；Repository-backed path 正确 ✓；无第二 conflict logic ✓；conflict/idempotent continue 正确 ✓；storage failure abort 正确 ✓；partial summary 正确 ✓；numeric id locked semantics 正确 ✓；random missing/corrupt replay caveat 正确 ✓；HRT production cutover 完成 ✓；legacy import 无 production reachability ✓；legacy export 按设计保留 ✓；7A unchanged ✓；无 PK/Widget/Wear/Custom/Plan/schema/migration ✓；报告足够准确 ✓。

**Batch 7B may be sealed.**

**Batch 7C may begin only after Batch 7B implementation/review/tag sealing.**

- 7C（Domain→PK adapter + parity + consumer cutover + bridge removal）：封存后
- Widget / Wear expansion / Custom medication / MedicationPlan JSON / Batch 8：继续禁止
- Room v3：仍 internal/unreleasable
