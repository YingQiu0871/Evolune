# Phase 1 Batch 7 Closure Remediation Independent Review

Date: 2026-08-11
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-batch7-remediation`
Branch: `phase1/batch7-closure-remediation`

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 2**（一项报告口径观察 + 一项环境瞬态记录；均不阻断）
- Root cause confirmed: **YES**
- Test-only remediation: **PASS**
- Current-operation synchronization: **PASS**
- Stale terminal state handling: **PASS**
- Assertions preserved: **PASS**
- Production diff: **ZERO**
- Timeout unchanged: **YES**（10_000L 保持）
- Retry absent: **YES**
- Sleep/delay absent: **YES**
- API33 warm: **PASS**（独立复现 10/10）
- API33 cold: **PASS**（独立复现 3/3）
- Class stress: **PASS**（独立复现 5/5）
- API33 full: **PASS**（115/0/0/2）
- API35 full: **PASS**（115/0/0/2；一次瞬态 task 失败后重跑成功）
- JVM: **PASS**（49/406）
- PK parity: **PASS**（49/49；delta 0.0 保持）
- Build gates: **PASS**
- 16KB alignment: **PASS**
- Schema integrity: **PASS**
- **Remediation may be sealed: YES**
- **Batch 7 final closure gate may be rerun after sealing: YES**（整合分支上重跑）
- **Batch 8: NO**
- **phase-1-batch-7 tag: NO**（本 review 不授权）
- **Release: forbidden**

## Git/scope verdict

- 分支 `phase1/batch7-closure-remediation` ✓；HEAD `49d55a1`（diagnosis commit）✓
- `phase-1-batch-7c` 为祖先（git graph 确认：f69fd64/4cd43e6 → merge 7f4ef3b → diagnosis）✓
- 唯一 modified = `DoseEventProductionCutoverTest.kt`（+17/−12）；唯一 new = remediation report ✓
- **生产 diff 零**（`git diff -- app/src/main wear/src/main app/schemas gradle` 全空）✓；无 keystore/DB/artifact（临时 keystore 已移除）✓；staged 空 ✓

## Root-cause verdict

- diagnosis（`reviews/PHASE_1_BATCH_7_API33_FAILURE_DIAGNOSIS.md`，commit 49d55a1）与修复一致：test-only `Dispatchers.Unconfined` 下，`ImportResult.Success` 在 `finishOperation()`（释放 operationInFlight + 发布 terminal IMPORT state）之前发布——测试 dismiss 后立即重入 → `beginOperation()` 拒绝 → 等待不存在的 result → 10s timeout ✓
- 独立代码核对：HRTViewModel 中 `_importResult.value = Success`（L273-278）先于 `succeed(IMPORT)` + `finally { finishOperation() }`（L295-303）——**窗口真实存在** ✓
- 排除 Room/CAS/reopen/Repository/delete/数据损坏/生产挂起——本审阅无反向证据 ✓
- 历史失败保留为证据（报告 §2）——不擦除 ✓

## Synchronization verdict

- 修复 = `awaitImportSuccess(viewModel) { importFromMahiroJson(...) }`（三次 import 均替换）✓
- helper 复用既有 `awaitOperation` 生命周期（L256-273）：
  1. `acknowledgeOperation()` 清除 stale terminal state ✓
  2. `action()` 启动当前 import ✓
  3. `operationState.filter { Success/Failure && operation == IMPORT }.first()`（10s 超时不变）✓
  4. terminal state 确认后才读 `importResult.value as Success` ✓
- **同步基于 operation lifecycle（非 wall-clock）**：awaitOperation 等待的是 operationState 的 terminal——它在 `finishOperation()` 后可见（= operationInFlight 已释放）→ **gate 可重入性由 terminal state 保证** ✓
- 无 timing 依赖：无 sleep/delay/retry/轮询 ✓

## Stale-state verdict

- **关键检查**：`awaitOperation` 第一步 `acknowledgeOperation()`——VM 实现仅在非 Running 时置 Idle（`if (operationState !is Running) Idle`）——上次的 `Success(IMPORT)` 被清除 → filter 不会命中历史值（Idle 被排除、Running 被排除）→ **只能命中本次操作的 terminal** ✓
- **非 "first { it == Success(IMPORT) }" 无清理**——修复正确避免了 stale 误命中 ✓
- 当前-operation attribution：filter 的 `state.operation == operation`（IMPORT）→ 即便有历史其他 operation 的 terminal 也不匹配 ✓

## Assertion-preservation verdict

- diff 仅改同步方式：所有断言原样保留——first import（1/0/0）、replay（0/1/0）、conflict（0/0/1）、原行逐字段不变（`assertEquals(imported, getById(...))`）、metadata、reopen、CAS、delete、最终态（diff 无删减行）✓
- 无 assertion 弱化/删除 ✓

## Production-boundary verdict

- `git diff -- app/src/main wear/src/main app/schemas gradle "*.gradle*" libs.versions.toml` 全空 ✓
- HRTViewModel/Repository/Room/schema/migration 零变化 ✓
- timeout 未变（10_000L）；无 retry/sleep/delay/yield/runCatching 新增（diff 扫描）✓

## Reproduction verdict

独立复现（API33 phone = emulator-5558，动态核实 SDK 33 / sdk_gphone64_x86_64 / sw411dp / 无 watch）：

- **warm 10/10 PASS**（1.07-1.41s/次，与报告 1.08-1.46 一致）✓
- **cold 3/3 PASS**（force-stop app+test 进程，不 wipe）✓
- **class 5/5 PASS**（每类 2 tests，10/10 tests 0 失败）✓
- 时长分布稳定（无慢样本/无超时迹象）✓

## Full-connected verdict

- API33 full（5558）：**115 / 0 failures / 0 errors / 2 skipped**（XML 核实；skips = foldable-only 在 phone 上的 intentional 排除）✓
- API35 full（5560，SDK 35 动态核实）：**115 / 0 / 0 / 2**（XML 核实；progress 显示 117 entries 含 2 skipped——报告 §12/§13 表述一致）✓
- **P2-F2**：API35 首次运行 BUILD FAILED（53min——测试全过、XML 0 失败；疑似设备/UTP 瞬态）；原命令重跑 **BUILD SUCCESSFUL 13m41s**。与历史 API35 瞬态模式一致（此前多批出现过"XML 0 失败但 task 报错、重跑通过"）——非本修复引入；记录为环境瞬态

## JVM/PK verdict

- Full App JVM：**49 suites / 406 / 0 / 0 / 0**（XML 累加）✓
- PK：5 suites / 49 / 0 ✓
- 7A：Codec 12 + Adapter 13 = **25** ✓；7B：ImportService 12（+ HRT 计入全量）✓；7C：Adapter 5 + Parity 1 + Boundary 2 + Export 2 = **10** ✓；Wear：1/1 ✓
- PK parity：`DomainDoseEventToPkParityTest` 通过（delta 0.0 保持——本批零 PK 相关改动）✓

## Build/alignment verdict

- `:app:kspDebugKotlin --rerun-tasks` PASS；`:app:assembleDebug` PASS；`:app:compileDebugAndroidTestKotlin --rerun-tasks` PASS；`:wear:assembleDebug` PASS；`:app:lintDebug` PASS（0 errors——报告 82 warnings/1 hint 与此类一致；BUILD SUCCESSFUL）✓
- zipalign 36.1.0 `-c -P 16 -v 4`：**Verification successful**（exit 0）✓

## Schema verdict

- 独立计算 git blob SHA-256：schema2 = **B8DA54ED…**、schema3 = **044013C0…** ✓（与报告/锁定值一致）；identityHash 未变（KSP 重跑无 diff）✓

## Report-accuracy verdict

`PHASE_1_BATCH_7_CLOSURE_REMEDIATION_REPORT.md`（约 240 行）逐项核对：

- baseline/diagnosis SHAs 与 git 一致 ✓；root cause 与代码窗口一致 ✓；remediation 描述与 diff 一致 ✓
- 生产 NONE ✓；timeout/retry 政策 ✓；warm 10/10（时长表与我复现一致）、cold 3/3、class 5/5 ✓
- 相邻 instrumentation 7 suites 42（报告如实说明 wrapper 历史 39 差异）✓；API33/35 full 115/0/0/2 ✓；JVM/PK/7A/7B/7C/Wear 计数与我的 XML 一致 ✓；PK parity 0.0 ✓；build gates/alignment/schema ✓；风险 P0/P1/P2=0/0/0 ✓
- **P2-F1**：报告 §12 "AGP progress 117 vs discovered 115" 与 §11 wrapper 39 vs 42——**均为计数维护/展示口径，非 gate 缺陷**（instrumentation 42/42 与全量 115/0 失败均由独立运行证实）——按任务 §15 判定为 P2 或无 finding（记录）

## Findings

### P0
None.

### P1
None.

### P2

**F1 — wrapper/UTP 计数口径差异（39 vs 42、117 vs 115）**
- Severity: P2
- File: `PHASE_1_BATCH_7_CLOSURE_REMEDIATION_REPORT.md` §11/§12
- Problem: 本地 wrapper post-check 期待历史 39、UTP progress 显示 117 entries vs discovered 115
- Evidence: 相邻 instrumentation 实测 42/42 PASS（报告）；API33 full 115/0/0/2（本审阅 XML 独立核实）
- Impact: 无——wrapper 非正式 project gate；测试命令本身全过
- Required fix: 无（或 wrapper 常量后续维护）
- Blocks sealing? NO

**F2 — API35 全量一次瞬态 task 失败（环境记录）**
- Severity: P2
- File: 验证边界
- Problem: 首次 API35 全量运行 XML 115/0/0/2 但 Gradle task 失败（53min）；原命令重跑 BUILD SUCCESSFUL（13m41s）
- Evidence: 重跑 XML tests=115 failures=0 errors=0 skipped=2；历史多批存在同类瞬态
- Impact: 无（最终通过；非本修复引入）
- Required fix: 无
- Blocks sealing? NO

## Independent validation

- Git: branch/HEAD/graph（7c sealed 确认）/status/diff/--check/ls-files/stash 全部核实
- 完整读取: remediation report、CutoverTest diff（+17/−12）、awaitImportSuccess/awaitOperation/assertSuccess helper、HRTViewModel（只读，确认 publish 顺序窗口）、diagnosis（commit 49d55a1 内容经报告引用）
- 设备身份（动态核实）: `emulator-5558` = API 33 phone（SDK 33/sdk_gphone64_x86_64/sw411dp/无 watch）；`emulator-5560` = API 35 phone（SDK 35）——与报告 §8/§13 一致
- API33 focused warm（5558）: **10/10 PASS**（1.07-1.41s，am instrument 连续无 force-stop）
- API33 cold: **3/3 PASS**（force-stop app+test 进程）
- Class stress: **5/5 PASS**（2 tests each）
- API33 full connected: **115 / 0 / 0 / 2**（XML）
- API35 full connected: **115 / 0 / 0 / 2**（XML；一次瞬态 task 失败后重跑 BUILD SUCCESSFUL）
- Full JVM: **49 / 406 / 0 / 0 / 0**（XML）；PK 49；7A 25；7B 12（import）+HRT（全量内含）；7C 10；Wear 1
- ksp/assemble/compile androidTest/wear assemble/lint: 全 PASS（lint 0 errors）
- zipalign 36.1.0 `-c -P 16 -v 4`: Verification successful（exit 0）
- Schema blob SHA-256: 独立计算匹配（B8DA54ED…/044013C0…）
- Production diff: `app/src/main`、`wear/src/main`、`app/schemas`、Gradle 全空
- 未运行: 相邻 7-suite 42 单独命令（API33 全量 115 含全部 repository instrumentation 且 0 失败——覆盖充分）

## Final decision

**APPROVE WITH P2**

批准标准逐项：P0=0 ✓；P1=0 ✓；lifecycle synchronization 正确 ✓；stale terminal state 已处理（acknowledgeOperation 清除）✓；current-operation attribution 正确（filter operation==IMPORT + Idle/Running 排除）✓；production diff zero ✓；timeout 未变（10_000L）✓；无 retry/sleep/delay ✓；所有原断言保留 ✓；API33 warm 10/10 ✓；API33 cold 3/3 ✓；class stress 5/5 ✓；full API33 PASS ✓；full API35 PASS ✓；JVM PASS ✓；PK parity ≤1e-6（0.0 保持）✓；build gates PASS ✓；alignment PASS ✓；schema 未变 ✓。

**Batch 7 closure remediation may be sealed.**

**After remediation sealing and merge, the full Batch 7 closure gate must be rerun from the integrated branch.**

- 不写 "Batch 7 closed"（最终门需在整合分支重跑确认）
- Batch 8：不授权
- `phase-1-batch-7` tag：不授权
- Release：禁止
- Room v3：仍 internal/unreleasable
