# Phase 1 Batch 7 Closure Remediation Report

## 1. Baseline

- Integration HEAD before diagnosis: `7f4ef3bb48466ac42fa92c4d93c4f10c506f6aa7`
- Diagnosis commit: `49d55a1aa2b0f191e89bf059ec192a6db3a43d39`
- Diagnosis report: `reviews/PHASE_1_BATCH_7_API33_FAILURE_DIAGNOSIS.md`
- Remediation branch: `phase1/batch7-closure-remediation`
- Remediation worktree: `D:\Evolune-batch7-remediation`
- `phase-1-batch-7c` is an ancestor of the remediation HEAD.

## 2. Original closure failure

The historical API 33 closure run remains part of the evidence:

| Tests | Failures | Errors | Skipped |
|---:|---:|---:|---:|
| 115 | 1 | 0 | 2 |

- Failing test: `DoseEventProductionCutoverTest.providerAndHrtPathPersistReopenCasImportConflictAndDelete`
- Failing method duration: 10.163 seconds
- Suite duration: 71.234 seconds
- Failure: the existing 10-second test timeout expired while waiting for the next import result.

The new passing runs do not erase this historical failure.

## 3. Independent diagnosis

- Classification: `TEST DEFECT`
- Confidence: `HIGH`
- Original severity: `P2`
- Diagnosis P0/P1/P2: `0/0/1`
- No Room, CAS, reopen, repository, delete, persistence, or production-code defect was found.

## 4. Root cause

The test creates `HRTViewModel` with a test-only
`CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)`. It previously waited only for
`ImportResult.Success`. Production publishes that result before the outer operation lifecycle
calls `finishOperation()`, clears `operationInFlight`, and publishes the terminal IMPORT
operation state. The test could therefore dismiss the result and start the replay inside that
small window. `beginOperation()` rejected the reentrant call, leaving the result at `Idle` and
causing the subsequent `StateFlow.first()` to time out.

## 5. Remediation

The remediation is test-only. First import, replay, and conflict import now run through
`awaitImportSuccess(viewModel, action)`. The helper delegates to the existing
`assertSuccess(..., DoseEventOperation.IMPORT, action)` and `awaitOperation()` lifecycle:

1. `acknowledgeOperation()` clears stale terminal state.
2. The current import action starts.
3. The test waits for the current terminal IMPORT operation state.
4. The current `ImportResult.Success` is read only after the operation completes.

All original first-import, replay, conflict, persisted-state, CAS, reopen, delete, and final-state
assertions remain in place.

## 6. Production code changes

`NONE`

Static Git checks show no diff under `app/src/main`, `wear/src/main`, Room schemas, Gradle files,
or the manifest. The only code diff is the instrumentation test.

## 7. Timeout and retry policy

- Timeout remains `10_000L`.
- No retry rule or retry loop was added.
- No `Thread.sleep` was added.
- No `delay`-based synchronization was added.
- No assertion was removed or weakened.
- Synchronization is based on the operation lifecycle, not wall-clock timing.

## 8. API 33 focused warm validation

Device: `emulator-5558`, Android 13 / API 33, model `sdk_gphone64_x86_64`, 1080x2400,
phone form factor, not Wear.

The focused method passed 10 consecutive warm-process iterations without force-stop or retry.

| Iteration | Duration (s) |
|---:|---:|
| 1 | 1.457 |
| 2 | 1.086 |
| 3 | 1.104 |
| 4 | 1.307 |
| 5 | 1.157 |
| 6 | 1.081 |
| 7 | 1.123 |
| 8 | 1.113 |
| 9 | 1.134 |
| 10 | 1.113 |

- Result: `10/10 PASS`
- Mean: 1.168 seconds
- Median: 1.118 seconds

## 9. API 33 cold-process validation

Only the app and test processes were force-stopped between iterations. The emulator and its data
were not wiped.

| Iteration | Duration (s) |
|---:|---:|
| 1 | 1.315 |
| 2 | 1.122 |
| 3 | 1.111 |

- Result: `3/3 PASS`
- Mean: 1.183 seconds
- Median: 1.122 seconds

## 10. ProductionCutover class stress

The complete `DoseEventProductionCutoverTest` class ran five consecutive times.

| Class run | Tests | Duration (s) | Result |
|---:|---:|---:|---|
| 1 | 2 | 1.378 | PASS |
| 2 | 2 | 1.184 | PASS |
| 3 | 2 | 1.186 | PASS |
| 4 | 2 | 1.180 | PASS |
| 5 | 2 | 1.195 | PASS |

- Result: 5/5 class runs, 10/10 tests, 0 failures, 0 errors, 0 skipped
- Mean class duration: 1.225 seconds
- Median class duration: 1.186 seconds

## 11. Adjacent instrumentation

The adjacent production repository instrumentation package completed with:

- Suites: 7
- Tests: 42
- Failures: 0
- Errors: 0
- Skipped: 0
- Instrumentation result: `OK (42 tests)`
- Duration: 2.897 seconds

A local wrapper post-check still expected the historical count of 39 and therefore returned 1;
the instrumentation command itself passed all 42 discovered tests. No project test gate or
assertion was changed for this count difference.

## 12. Full API 33 connected suite

| Tests | Failures | Errors | Skipped | Gradle result |
|---:|---:|---:|---:|---|
| 115 | 0 | 0 | 2 | `BUILD SUCCESSFUL` |

- Device: `emulator-5558`, Android 13 / API 33 phone
- Gradle duration: 2 minutes 17 seconds
- The two skips are intentional foldable-only tests on a non-foldable phone.
- AGP/UTP progress output displayed 117 progress entries, while the authoritative discovered suite
  count was 115. The API 33 XML was subsequently replaced by the required API 35 connected run.

## 13. Full API 35 connected suite

| Tests | Failures | Errors | Skipped | Test time | Gradle result |
|---:|---:|---:|---:|---:|---|
| 115 | 0 | 0 | 2 | 58.52 s | `BUILD SUCCESSFUL` |

- Device: `emulator-5560`, Android 15 / API 35, model `sdk_gphone64_x86_64`, 1080x2400,
  phone form factor, not Wear
- Gradle duration: 2 minutes 17 seconds
- The two skips are intentional foldable-only tests on a non-foldable phone.
- XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`
- HTML: `app/build/reports/androidTests/connected/debug/index.html`

## 14. JVM regression

| Gate | Suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Full App JVM | 49 | 406 | 0 | 0 | 0 |
| PK | 5 | 49 | 0 | 0 | 0 |
| Batch 7A codec and domain adapter | 2 | 25 | 0 | 0 | 0 |
| Batch 7B import service and HRTViewModel | 2 | 26 | 0 | 0 | 0 |
| Batch 7C adapter, boundary, parity, export | 4 | 10 | 0 | 0 | 0 |
| Wear JVM | 1 | 1 | 0 | 0 | 0 |

## 15. PK parity

- Tolerance: `1e-6`
- Maximum observed concentration delta: `0.0`
- AUC delta: `0.0`
- Structural projections were equal.
- The deterministic identical-input AUC comparison passed.

## 16. Build gates

The combined build gate completed successfully in 2 minutes 13 seconds:

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:compileDebugAndroidTestKotlin` | PASS |
| `:wear:assembleDebug` | PASS |
| `:app:lintDebug` | PASS |

Lint reported 0 errors, 82 warnings, and 1 hint. The existing androidTest Kotlin compiler
warnings are unrelated to this test synchronization change.

## 17. APK 16KB alignment

`zipalign` from Android build-tools 36.1.0 verified
`app/build/outputs/apk/debug/app-debug.apk` using `-c -P 16 -v 4`.

Result: `Verification successful` with exit code 0.

## 18. Schema identity

KSP generation completed and both tracked schemas remain unchanged.

| Schema | Identity hash | Canonical blob SHA-256 | Result |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | UNCHANGED |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | UNCHANGED |

The SHA-256 values were calculated from canonical Git blob bytes so Windows working-tree line
endings cannot alter the verification result.

## 19. Boundary audit

- Production diff: zero
- `app/src/main`: zero diff
- `wear/src/main`: zero diff
- Room schemas: zero diff
- Gradle and version catalog: zero diff
- Manifest: zero diff
- Timeout: unchanged
- Retry, sleep, and delay synchronization: absent
- Temporary copied debug keystore: removed after validation
- Staging area: empty

## 20. Risk assessment

- Remediation P0/P1/P2: `0/0/0`
- The diagnosed P2 test race is resolved by lifecycle-based synchronization.
- The two connected-suite skips are intentional foldable-only coverage exclusions on phone AVDs,
  not remediation defects.
- Independent remediation review is still required before final Batch 7 closure.

## 21. Decision

Batch 7 closure remediation complete pending independent review.
