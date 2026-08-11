# Phase 1 Batch 7 API33 Closure Failure Diagnosis

Date: 2026-08-11
Reviewer: Codex (independent read-only diagnosis)
Worktree: `D:\Evolune-phase1`
Branch: `phase1/batch7-design`
Integration HEAD: `7f4ef3bb48466ac42fa92c4d93c4f10c506f6aa7`

## Executive summary

Classification: **TEST DEFECT**
Confidence: **HIGH**
Provisional severity: **P2**

- The API33 failure is a reproducible but intermittent instrumentation synchronization race. It is not evidence of a Room, repository, close/reopen, CAS, import-conflict, or delete defect.
- The test observes `ImportResult.Success` and immediately starts another import while using an injected `Dispatchers.Unconfined` scope. `HRTViewModel` publishes that result before its outer `finally` clears `operationInFlight`. The next import can therefore be rejected by `beginOperation()` without publishing a new result, leaving the test's `StateFlow.first()` suspended until its 10-second timeout.
- The original provisional P1 is downgraded to P2 because the observed race depends on test-only scheduling. Production uses `viewModelScope` and user dismissal is delivered through a later Main/UI event after the uninterrupted success-to-`finishOperation()` section.
- No data corruption, wrong-row deletion, CAS overwrite, stale database exception, or partial write was observed.
- This diagnosis does not pass the closure gate. The test must be synchronized to the operation terminal state, independently reviewed, and the complete closure matrix rerun.

P0 / P1 / P2: **0 / 0 / 1**

## Baseline and devices

- Git branch: `phase1/batch7-design`
- Git HEAD: `7f4ef3bb48466ac42fa92c4d93c4f10c506f6aa7`
- `phase-1-batch-7c` is an ancestor of HEAD.
- Worktree and staging were clean before diagnosis.
- API33 phone: `emulator-5558`, AVD `Evolune_API33_Migration`, Android 13 / API 33, model `sdk_gphone64_x86_64`, 1080x2400, phone features present, no watch feature.
- API35 phone: `emulator-5560`, AVD `Pixel_7`, Android 15 / API 35, model `sdk_gphone64_x86_64`, 1080x2400, phone features present, no watch feature.

The integrated HEAD app and test APKs were used for direct AndroidJUnitRunner reproduction. The original Gradle closure result remains at:

`app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`

That XML records **115 tests / 1 failure / 0 errors / 2 skipped**, suite time **71.234 s**, and the failing method time **10.163 s**.

## Failing test

File:

`app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/DoseEventProductionCutoverTest.kt`

Method:

`providerAndHrtPathPersistReopenCasImportConflictAndDelete`

The test executes these stages:

1. Create a disposable Room database and production provider.
2. Create and persist a manual event through `HRTViewModel`.
3. Insert, replay, and conflict-check a Wear event through the repository.
4. Close the database and cancel the original injected scope.
5. Reopen the same database and create a new provider/ViewModel.
6. CAS-update the Wear event, verify revision 2, verify stale revision conflict, and verify NotFound.
7. Import one JSON v1 event.
8. Dismiss the first result and test idempotent replay.
9. Dismiss the replay result and test same-ID content conflict.
10. Delete the manual event and verify final persisted state.

## Timeout owner

All explicit 10-second owners in the failing method are:

- First import result: lines 139-141.
- Replay result: lines 155-157.
- Conflict result: lines 164-166.
- CREATE/UPDATE/DELETE operation helper: lines 251-267, with the timeout at line 258 and the terminal-state `Flow.first()` at line 266.

The retained failure stack contains only coroutine timeout internals:

`TimeoutCancellationException -> TimeoutCoroutine -> EventLoopImplBase -> DefaultExecutor`

There is no project frame in the stack. The actual suspended primitive is a `StateFlow.filter { ... }.first()` waiting for a terminal state that was not emitted after the next action.

## Reproduction matrix

| Environment | Mode | Runs | Pass | Fail | Failure stage | Mean / median duration |
|---|---:|---:|---:|---:|---|---:|
| API33 `emulator-5558` | Focused, warm process | 5 | 4 | 1 | Post-first-import terminal-state wait; timeout at 10 s | 2.481 s / 0.487 s |
| API33 `emulator-5558` | Focused, cold process via force-stop only | 3 | 3 | 0 | None | 0.481 s / 0.483 s |
| API33 `emulator-5558` | Entire `DoseEventProductionCutoverTest` class | 9 | 7 | 2 | Same post-first-import bounded stage | Per-run mean not retained / median about 0.563 s |
| API33 `emulator-5558` | Extended focused probe | 11 | 10 | 1 | Same 10-second wait signature | Aggregate duration not retained |
| API33 `emulator-5558` | Original full connected suite | 1 suite | 112 | 1 | Failing method timed out; 2 tests skipped | 71.234 s suite / 10.163 s failing method |
| API35 `emulator-5560` | Focused, warm process | 5 | 5 | 0 | None | 0.621 s / 0.639 s |

Focused API33 warm durations were `10.483, 0.487, 0.470, 0.474, 0.489` seconds. Cold-process durations were `0.483, 0.484, 0.477` seconds. The two API33 class failures were `10.561` and `10.529` seconds. API35 durations were `0.557, 0.642, 0.622, 0.646, 0.639` seconds.

The mixed API33 result, exact 10-second failure durations, clean cold-process passes, and API35 passes establish a scheduler-sensitive race rather than a deterministic functional defect. API level is a timing trigger, not by itself the root cause.

## Exact failure stage

The failure is bounded to **after the first JSON import has persisted successfully and before the final delete commits**.

The available unmodified test and coroutine-only stack cannot distinguish with certainty whether the timed-out collector is the replay wait at lines 155-157 or the conflict wait at lines 164-166. Both have the same unsafe sequence:

1. Observe `ImportResult.Success`.
2. Call `dismissImportResult()`, which writes `Idle`.
3. Immediately call `importFromMahiroJson()`.
4. Wait for another `ImportResult.Success`.

The high-confidence mechanism is that step 3 executes before the preceding import's `finishOperation()`. `beginOperation()` then returns `false` and the call returns silently. Since the test already changed `importResult` to `Idle`, no producer remains that can satisfy step 4, so `first()` reaches exactly 10 seconds.

This is a more precise conclusion than attributing the timeout to the database, while remaining honest about which of the two identical post-import waits failed in a particular run.

## Production state at timeout

A read-only SQLite observation during a reproduced failure found:

| Event | Persisted state |
|---|---|
| Manual event | dose 2.0, source `MANUAL`, revision 1 |
| Wear event | dose 3.5, source `WEAR`, revision 2 |
| JSON event | dose 2.0, source `JSON_V1`, revision 1 |

This proves that the following completed before the timeout:

- Database creation and provider construction.
- Manual create/persist.
- Close/reopen through a new database and provider.
- Wear CAS update with revision increment.
- First JSON insert through the Batch 7B import path.

The manual event still existed, so the final delete had not committed. No persisted row was malformed or partially updated. Replay and conflict are content-preserving operations, so the database alone cannot distinguish which post-import collector was active.

No Room, SQLite, closed-database, stale-DAO, repository, CAS, or mapping exception appeared in the instrumentation stack or log evidence.

## Source-level synchronization analysis

The test injects a test-only operation scope at lines 224-240:

`CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)`

The relevant production state sequence in `HRTViewModel.kt` is:

1. `importFromMahiroJson()` calls `beginOperation()` at lines 259-265.
2. `beginOperation()` rejects while `operationInFlight` is true at lines 479-489.
3. The import coroutine publishes `_importResult = ImportResult.Success` at lines 273-282.
4. The operation gate is cleared only by outer `finally -> finishOperation()` at lines 304-305.
5. `finishOperation()` sets `operationInFlight = false` and publishes terminal `operationState` at lines 492-499.

The test waits only for step 3. It does not wait for step 5 before dismissing the result and invoking the next import. `Dispatchers.Unconfined` permits the test collector and operation continuation to interleave in that gap.

The silent return at lines 263-264 is intentional overlapping-operation protection. It becomes a test hang because the test first resets the only observed state to `Idle`, then invokes an operation that can be rejected, then waits only for a new import result.

## Git/history analysis

- `DoseEventProductionCutoverTest.kt` was created in Batch 6A by `d16411f` (`feat: cut over phone dose event flow`).
- Batch 7B commit `c6fc7bc` (`feat(import): cut over Mahiro v1 import through repository`) added the replay/conflict sequences and their immediate dismiss/reimport pattern.
- The import operation gate and success-before-finally ordering relevant to this race also entered through the Batch 7B path.
- Batch 7C implementation `4cd43e6` changed HRT PK/export projection only. It did not modify this test's import synchronization or the import operation sequencing.
- `git diff phase-1-batch-7b..HEAD` confirms that current HRT changes are PK/export mapping changes, not the import-result race.

Therefore the latent test race is **A: present since Batch 7B but not exposed consistently**. Batch 7C did not introduce it.

## Root-cause analysis

### 1. Test-only `Dispatchers.Unconfined` re-entry before operation completion

Rank: **Primary, high confidence**

Evidence for:

- Exact source ordering exposes `ImportResult.Success` before `operationInFlight` is cleared.
- The test immediately dismisses and re-enters after observing only the earlier state.
- A rejected overlapping call emits no new import result, exactly explaining a `StateFlow.first()` timeout.
- Failures cluster at 10.48-10.56 seconds; passing runs complete near 0.47-0.64 seconds.
- Warm API33 mixes pass/fail, while cold API33 and API35 focused runs pass.
- Persisted state proves the repository path completed through first import.

Evidence against / residual uncertainty:

- Read-only diagnosis cannot capture `operationInFlight` in-process at the failure instant without instrumenting the test or production code.
- The coroutine-only stack cannot identify replay versus conflict wait.

### 2. Shared process/scheduler state amplifies the race

Rank: **Contributing trigger, medium confidence**

Evidence for:

- API33 warm and class runs fail intermittently; three force-stop cold-process runs pass.
- API35 focused runs pass with slightly different scheduler timing.
- The class consistently executes the rollback test first, then the flaky method.

Evidence against:

- The test deletes its disposable database artifacts in `@Before` and `@After`, closes Room, and cancels injected scopes.
- The same failure occurs in focused warm runs, so no specific preceding repository class is required.
- No persistent polluted state or second database was observed.

This explains exposure frequency but does not replace hypothesis 1 as the code-level cause.

### 3. Stale provider/DAO/Flow after Room close and reopen

Rank: **Rejected by evidence**

Evidence considered:

- The test closes the first database and creates a new `ProductionRepositoryProvider` over a reopened database.

Evidence against:

- The reopened provider reads both prior events.
- The reopened repository completes the Wear update and persists revision 2.
- The first JSON import persists through the reopened provider.
- No closed-database, invalidation, DAO, or SQLite exception occurs.

### 4. Repository CAS, import conflict, or delete implementation defect

Rank: **Rejected by evidence**

Evidence against:

- CAS success, revision conflict, and NotFound assertions complete before import.
- The authoritative JSON row remains unchanged.
- The failure is intermittent and has a fixed timeout signature rather than a typed repository result or storage exception.
- Delete has not started or committed in the observed failure state; there is no wrong-row deletion.

## Production impact

No actual-user defect is demonstrated.

Production constructs `HRTViewModel` without an injected operation scope, so line 121 selects `viewModelScope`. The import coroutine publishes `ImportResult.Success`, records terminal operation success, and reaches `finishOperation()` without another suspension between those steps.

The Settings UI renders a Material 3 `AlertDialog` for `ImportResult.Success` at `SettingsScreen.kt` lines 187-200. Dismissal or confirmation invokes `onDismissImportResult` from a later UI input event at lines 190 and 196. Main-thread UI processing cannot execute that user event in the test-only micro-window created by `Dispatchers.Unconfined` re-entry.

The production import callers are the file-picker path and clipboard path in `AppNavigation.kt` lines 165-173 and 197-204. They invoke the same ViewModel operation gate; there is no production loop that observes success and synchronously performs dismiss/reimport on an unconfined continuation.

This does not prove overlapping imports are a supported product workflow; they are intentionally rejected. It establishes that the observed closure timeout is not a reproducible production hang and that changing repository, Room, CAS, or import semantics would be the wrong remediation.

## Recommended remediation

Choice: **B. Instrumentation test synchronization fix**

Do not increase the timeout, add retries, skip the test, insert sleeps, weaken assertions, or change production behavior.

Preferred test correction:

- After each import, await terminal `operationState == DoseEventOperationState.Success(IMPORT)` before calling `dismissImportResult()` and starting replay/conflict.
- Then assert the corresponding `ImportResult.Success` counts exactly as today.

The terminal operation state is published inside `finishOperation()` after `operationInFlight` is cleared, so it is the correct lifecycle boundary for starting the next operation.

An acceptable alternative is a controlled coroutine test dispatcher whose scheduler is explicitly drained through operation completion. The test must not use timing delay as synchronization.

No production code, Repository contract, Room schema/migration, timeout value, or CAS/import semantics should change for this finding.

## Required follow-up validation

After the test-only remediation:

1. Run the exact focused method on API33 `emulator-5558` repeatedly, with at least 10 consecutive warm-process passes and no retry rule.
2. Run three force-stop-only cold-process API33 iterations without clearing the database outside the test's own lifecycle.
3. Run the full `DoseEventProductionCutoverTest` class repeatedly to cover class ordering and cleanup.
4. Run all adjacent production repository instrumentation classes.
5. Rerun the complete API33 connected suite and require 0 failures and 0 errors; account explicitly for intentional skips.
6. Run the complete API35 connected closure gate, not only the focused differential probe.
7. Resume all remaining Batch 7 build, lint, schema/alignment, App/Wear, and closure-report gates that were not run after the API33 failure.
8. Obtain an independent read-only review of the test-only change and the new closure evidence.

The original failed closure run must remain recorded; a later pass does not erase this diagnosis.

## Final diagnosis

- Classification: **TEST DEFECT**
- Confidence: **HIGH**
- Provisional severity: **P2**
- Recommended remediation: **instrumentation test synchronization fix**
- Production code fix required: **NO**
- Timeout increase or retry allowed: **NO**
- Current Batch 7 closure remains BLOCKED: **YES**
- `phase-1-batch-7` tag allowed: **NO**
- Batch 8 allowed: **NO**
- Room v3 release allowed: **NO**

The diagnosis is complete, but closure remains blocked until the test is corrected, independently reviewed, and the full closure matrix passes.
