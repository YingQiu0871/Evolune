# Evolune Plan Save Regression Fix Report

Date: 2026-08-07

Status: Plan-save production regression fixed, pending independent review.

## 1. User-visible symptom

On a real Android device, creating a medication plan with the following values failed:

- name: E2
- route: ORAL
- ester: E2
- dose: 2 mg
- schedule: DAILY
- times: 01:00, 09:00, 17:00

The editor stayed open and the UI displayed an unknown error instead of a structured save failure.

## 2. Save call chain

The affected production path is:

```text
MedicationPlansScreen
  -> MedicationPlanBottomSheet
  -> MedicationPlanViewModel
  -> MedicationPlanEditSession / draft mapper
  -> core.dataapi.MedicationPlanRepository
  -> RoomMedicationPlanRepository
  -> Room transaction
  -> medication_plans and scheduled_dose_slots
```

`MainActivity` supplies the production repository through `ProductionRepositoryProvider`. No legacy Repository, direct DAO, Entity, second database, or fallback write is used by this path.

## 3. Root cause

`MedicationPlanEditSessionFactory.createNew()` previously stored `clock.instant()` without normalizing its precision. A real device clock can return an `Instant` containing non-millisecond nanoseconds.

The v3 persistence mapper intentionally requires `createdAt` to convert to epoch milliseconds without loss. The non-millisecond value therefore produced `PlanSaveResult.Invalid` during persistence mapping. The UI then collapsed structured save failures into the generic unknown-error message.

The database, migration, slot UUIDv5 generation, transaction, Repository contract, and MainActivity provider wiring were not the cause.

## 4. Why existing tests missed it

Existing editor and Repository fixtures used millisecond-aligned `Instant` values. They did not exercise a new-session clock value such as `2026-08-07T01:02:03.123456789Z`.

The regression test now uses that synthetic nanosecond-precision device-clock input and verifies that the persisted value is the explicitly normalized `2026-08-07T01:02:03.123Z`.

## 5. Changes

Production changes are limited to the plan-save boundary:

- New edit sessions truncate the clock value to `ChronoUnit.MILLIS` before constructing a Domain plan.
- Edit sessions preserve the existing Domain plan `createdAt` unchanged.
- `MedicationPlanViewModel` preserves cancellation, maps known storage failures to `StorageFailure`, and maps other runtime failures to `UnexpectedFailure`.
- Plan UI surfaces distinguish invalid draft, invalid Repository result, not found, storage failure, delete failure, and unexpected failure.
- English and Simplified Chinese strings were added for those structured outcomes.

Test changes are limited to regression coverage and deterministic Compose synchronization:

- `PlanSaveRegressionTest` covers fresh v3, synthetic v2-to-v3 migration, exact user values, reopen persistence, a second plan, plan-ID update behavior, and slot-ID transaction rollback.
- Editor, ViewModel, and plan-screen tests cover millisecond normalization, metadata preservation, structured errors, cancellation, and success rendering.
- `MedicationRecordsScreenTest.createSuccessClosesEditorAfterContractInsert` now waits for the existing save button to become enabled before clicking. This fixes a suite-only Compose timing race without changing production behavior or assertions.

## 6. Automated exact-scenario result

The exact E2 scenario passed in both disposable database modes:

- Fresh v3 database: 2/2 exact-scenario assertions passed.
- Synthetic v2-to-v3 migrated database: 2/2 exact-scenario assertions passed.

The regression suite also confirmed three ordered, unique UUIDv5 slots, exact times, complete plan persistence, close/reopen persistence, and a second unrelated 08:00 plan.

The slot collision fixture rolled back the plan transaction and mapped the failure to `StorageFailure`. An existing plan ID followed the documented `Updated` path without duplicating rows.

## 7. Error mapping

The ViewModel now distinguishes these outcomes:

| Failure source | UI-facing result |
|---|---|
| Invalid draft | Invalid input |
| Repository invalid result | Invalid plan |
| Repository not found | Plan not found |
| Known `IllegalStateException` storage failure | Save or delete failed |
| Other runtime exception | Unexpected failure / unknown error |
| `CancellationException` | Rethrown; operation returns to idle |

No exception is converted into success, no invalid input is silently repaired, and no legacy writer is used after failure.

## 8. Connected phone validation

Connected tests were executed on both required phone AVDs. AndroidTest compilation is not counted as device execution.

| Device | Android | API | Connected result |
|---|---:|---:|---:|
| Evolune API 33 migration phone AVD | 13 | 33 | 104/104, 0 failures, 0 skipped |
| Pixel 7 phone AVD | 15 | 35 | 104/104, 0 failures, 0 skipped |

The API 33 run initially reproduced one Compose timeout in the unrelated `MedicationRecordsScreenTest` save-success method. The method passed in isolation. Adding the existing enabled-button wait pattern made the target method and the full 104-test suite pass. No DoseEvent or record production code was changed.

## 9. Real-device verification

The real device was a Samsung `SM-S918B` running Android 16 / API 36. The installed package was the debug variant `io.github.yuninggu.evolune.debug`, versionCode 10060.

The first in-place installation attempt was correctly stopped because the local APK certificate differed from the installed package certificate. After the user explicitly confirmed that the medication records were backed up and authorized the destructive reinstall, only that debug package was uninstalled and the current APK was installed. No `pm clear`, database pull, database read, user-data export, or other package was touched.

The local APK and newly installed package then had the same certificate SHA-256:

`2cf0a50c33b5404a85363e42dc15781c5c7e4c36bb5f0e8cc04a0e5d26ada772`

The user manually verified both scenarios:

1. E2 / ORAL / E2 / 2 mg / DAILY / 01:00, 09:00, 17:00 saved successfully, the editor closed, the plan appeared, and it remained after app restart without an unknown error.
2. A second E2 / 2 mg / DAILY / 08:00 plan also saved successfully.

## 10. Full regression results

All final commands were run with `--rerun-tasks` where applicable and completed successfully.

| Validation | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Full App JVM | 42 | 362 | 0 | 0 | 0 | PASS |
| PK regression | 5 | 49 | 0 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | 0 | PASS |
| API 33 connected | 1 aggregate | 104 | 0 | 0 | 0 | PASS |
| API 35 connected | 1 aggregate | 104 | 0 | 0 | 0 | PASS |

Connected classes included migration tests (18), migration matrix tests (22), v2 baseline (2), PlanSaveRegressionTest (4), Room/provider and cutover tests, receiver lifecycle tests, UI tests, and the example instrumentation test.

Additional successful commands:

- `:app:assembleDebug --rerun-tasks`: PASS.
- `:wear:assembleDebug --rerun-tasks`: PASS with temporary local debug signing parameters.
- `:app:lintDebug --rerun-tasks`: PASS, 0 errors, 82 warnings, 1 informational issue.
- `:app:kspDebugKotlin --rerun-tasks`: PASS.
- `:app:compileDebugAndroidTestKotlin --rerun-tasks`: PASS; compilation only.

Reports and outputs are under:

- `app/build/test-results/testDebugUnitTest/`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `wear/build/test-results/testDebugUnitTest/`
- `wear/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/outputs/androidTest-results/connected/debug/`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/apk/debug/app-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

## 11. Schema, migration, and contract boundaries

Room remains internal version 3 with `exportSchema=true`. KSP regenerated schema output with no Git diff.

| Schema | Identity hash | Canonical Git-content SHA-256 |
|---|---|---|
| v2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` |
| v3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` |

No schema, migration, Domain, Entity, DAO, Repository contract, JSON v1, PK algorithm, PK parameter, Wear protocol, or Room implementation changed in this fix. `MIGRATION_2_3` remains unchanged.

## 12. Data safety and scope

No production database was opened, copied, pulled, inspected, or exported by the agent. Synthetic disposable databases were used by the regression instrumentation test and were deleted in test teardown.

The explicit user-authorized uninstall removed the existing debug package data after the user confirmed a backup. No other package or database was touched. The final manual verification ran against the freshly installed debug package.

Changed files are limited to:

- `app/src/main/java/io/github/yuninggu/evolune/application/MedicationPlanEditor.kt`
- `app/src/main/java/io/github/yuninggu/evolune/viewmodel/MedicationPlanViewModel.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationPlanBottomSheet.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh-rCN/strings.xml`
- `app/src/test/java/io/github/yuninggu/evolune/application/MedicationPlanEditorTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/viewmodel/MedicationPlanViewModelTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreenTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationRecordsScreenTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/PlanSaveRegressionTest.kt`

No other report, generated schema, build configuration, local keystore, or unrelated production file was added to the change set.

## 13. Release and Batch 7 boundary

Batch 7 has not started. JSON v1 and PK algorithms remain unchanged. Room v3 remains an internal, unreleasable version and this fix does not create a release.

## 14. Risk classification and decision

Fix-specific P0/P1/P2 classification is `0/0/0`. The pre-existing Batch 6C accepted replay-policy P2 remains outside this fix and is unchanged.

The plan-save production regression is fixed and verified by JVM, disposable Room v3, API 33/API 35 connected tests, and the authorized real-device manual scenarios. The change is ready for independent read-only review; it is not yet committed or tagged.
