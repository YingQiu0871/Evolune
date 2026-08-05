# Evolune Phase 1 Batch 5B Report

Date: 2026-08-05

## 1. Scope

Batch 5B atomically switches the medication-plan production UI entry from the legacy plan Repository to the Phase 1 Domain contract. The completed scope is:

- `MedicationPlansScreen`, its editor, and `MedicationPlanViewModel` use `core.model.MedicationPlan`;
- `MedicationPlanViewModel` depends only on `core.dataapi.MedicationPlanRepository`;
- `MainActivity` injects `ProductionRepositoryProvider.medicationPlans`;
- UI input is converted to `MedicationPlanDraft` and then to a complete Domain aggregate;
- create/edit sessions own stable plan ID and `createdAt` values;
- save, delete, and enable/disable side effects occur only after Repository success;
- Domain reminder and predictor paths preserve locked legacy behavior;
- focused JVM, Compose, and file-backed Room integration tests prove the cutover;
- complete App, Wear, Room, migration, PK, lint, build, androidTest, and KSP regression is executed.

Batch 5B does not switch DoseEvent, HRT/PK, JSON, reminder receivers, Widget, or Wear consumers. It does not modify Repository contracts, Domain models, Room Repositories, DAOs, Entities, `AppDatabase`, migrations, schemas, Gradle, or Manifest files. It does not start Batch 6.

## 2. Files

Modified production files:

- `app/src/main/java/io/github/yuninggu/evolune/MainActivity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderManager.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationPlanBottomSheet.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationPlanCard.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreen.kt`
- `app/src/main/java/io/github/yuninggu/evolune/utils/MedicationPlanPredictor.kt`
- `app/src/main/java/io/github/yuninggu/evolune/viewmodel/MedicationPlanViewModel.kt`

Added production files:

- `app/src/main/java/io/github/yuninggu/evolune/application/MedicationPlanEditor.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationPlanReminderSchedule.kt`
- `app/src/main/java/io/github/yuninggu/evolune/utils/MedicationPlanDescription.kt`

Added tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/MedicationPlanEditorTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/MedicationPlanReminderScheduleTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/utils/MedicationPlanPredictorParityTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/viewmodel/MedicationPlanViewModelTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/MedicationPlanProductionCutoverTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreenTest.kt`

Report:

- `docs/phase-reports/PHASE_1_BATCH_5B_REPORT.md`

## 3. Production call-chain cutover

Before Batch 5B:

```text
MedicationPlansScreen
  -> MedicationPlanViewModel
  -> legacy data.MedicationPlanRepository
  -> MedicationPlanDao legacy write methods
```

After Batch 5B:

```text
MedicationPlansScreen / MedicationPlanBottomSheet
  -> MedicationPlanDraft
  -> MedicationPlanViewModel
  -> core.dataapi.MedicationPlanRepository
  -> ProductionRepositoryProvider.medicationPlans
  -> RoomMedicationPlanRepository
  -> one Room transaction for medication_plans + scheduled_dose_slots
```

`scheduled_dose_slots` is the authoritative Domain time collection. `RoomMedicationPlanRepository` continues to maintain the v3 legacy `timeOfDay` shadow in the same aggregate transaction. No fallback, dual write, second `AppDatabase`, second truth source, direct DAO access, or Entity access was added to the UI/ViewModel path.

## 4. MainActivity wiring

`MainActivity` obtains `ProductionRepositoryProvider.get(applicationContext)` and injects `productionRepositoryProvider.medicationPlans` into `MedicationPlanViewModelFactory`.

The existing `AppDatabase.getDatabase(applicationContext)` singleton remains the database used by the deferred legacy readers. `HRTViewModel` still receives the legacy plan Repository only for its existing read flow, and Wear dashboard synchronization continues to consume that same read-only legacy flow. DoseEvent wiring is unchanged.

The provider does not expose `AppDatabase`, DAO, or Entity objects to Compose or ViewModel code.

## 5. Creation and edit sessions

`MedicationPlanEditSessionFactory` accepts an injectable UUID supplier and `Clock`.

For a new plan:

- one UUID is generated when the create session starts;
- one `Instant` is captured at the same boundary;
- Compose recomposition does not regenerate either value;
- validation failure, Repository failure, and repeated save attempts retain both values;
- only closing the old session and starting a new session generates a new ID and captures a new time.

For an existing plan:

- the current `plan.id` is retained;
- the current `plan.createdAt` is retained;
- no UUID supplier or clock call occurs.

Neither `MedicationPlanDraftMapper` nor the Domain model reads a clock or generates a random ID.

## 6. UI and Draft boundary

`MedicationPlanEditorInput` owns text parsing and UI-specific input errors. It converts:

- dose text to finite positive `Double`;
- interval text to positive `Int`;
- UI schedule/time/day state to `MedicationPlanDraft`;
- the active edit session ID and `createdAt` into the Draft.

Text parse failures remain separate from `DraftMappingResult`. A blank name reaches the existing Draft validation and produces `MissingRequiredField(NAME)`; it is not hidden inside a fabricated parse issue.

The editor preserves the complete existing extras map. It changes only a key represented by the visible route-specific editor. `AntiAndrogen` and `SublingualTier` use explicit stable `when` mappings; no enum ordinal mapping is used. Time order, duplicate times, `00:00`, and `23:59` are preserved, and the UI never generates Slot IDs directly.

## 7. MedicationPlanViewModel contract

`MedicationPlanViewModel` exposes Domain plans and structured operation state. It handles the actual contract results:

Save:

- `Created`, `Updated`, and `NoChange` are persistence successes;
- `Invalid` becomes `RepositoryInvalid`;
- Draft validation issues become `InvalidDraft`;
- Repository storage exceptions become `StorageFailure`.

Delete:

- `Deleted` is success;
- `NotFound` remains an explicit failure;
- storage failure remains an explicit failure.

Enable/disable:

- `Updated` and `NoChange` are success;
- `NotFound` and `Invalid` remain explicit failures;
- the persisted aggregate is re-read before scheduling an enabled plan.

Operations are protected by one in-flight gate. Duplicate save clicks, save/delete overlap, and concurrent enable changes do not invoke the Repository twice. Completion releases the in-flight gate and publishes the terminal state in one synchronized boundary, preventing a newly observed success from racing with the previous lock release.

## 8. Reminder side effects

Reminder behavior is ordered after persistence:

- successful enabled save schedules once;
- successful disabled save cancels once;
- successful delete cancels once;
- successful enable schedules the persisted aggregate;
- successful disable cancels;
- any Draft, Repository, not-found, or storage failure causes zero reminder calls.

A reminder exception does not roll back an already committed Room transaction and does not invoke a legacy Repository fallback. Persistence remains successful while `ReminderSideEffectResult.FAILED` is exposed separately to the UI.

`ReminderManager` retains its legacy overloads for deferred Batch 6 receiver work. Domain scheduling uses Domain slots in position order. Both Domain and legacy overloads now share one pure schedule builder, preserving occurrence count, source time order, duplicates, one-hour evaluation window, alarm request-code offsets, and Java `atZone` DST gap/overlap behavior.

## 9. Predictor parity

`MedicationPlanPredictor` retains its legacy overload and adds a Domain overload using `plan.slots.map { it.localTime }`. Both paths use one internal prediction representation.

Focused parity tests verify:

- DAILY, WEEKLY, and CUSTOM output equivalence;
- time count and ordering;
- duplicate times;
- `00:00` and `23:59` boundaries;
- device `ZoneId.systemDefault()` behavior;
- Java `LocalDateTime.atZone` behavior in DST gaps and overlaps;
- route, ester, dose, extras, and time equivalence while excluding intentionally random predicted event IDs.

PK parameters and `SimulationEngine` are unchanged.

## 10. UI behavior verification

The existing Compose instrumentation dependencies were used; no Gradle dependency was added. `MedicationPlansScreenTest` ran five device tests that verify:

- a create session keeps its ID and `createdAt` across recomposition;
- an invalid Draft does not call the Repository and keeps the sheet open;
- save failure keeps the sheet open and displays an error;
- save success closes the sheet;
- delete failure keeps the sheet open and displays an error.

The tests use stable semantic tags for add, name, dose, save, delete, and error elements.

## 11. Real Room integration

`MedicationPlanProductionCutoverTest` uses `emulator-5556` and a disposable file-backed v3 database named only for the test. It constructs `ProductionRepositoryProvider` from that one database and injects its plan contract into the real `MedicationPlanViewModel` entry.

The two integration tests verify:

- create-session save through ViewModel and provider contract;
- simultaneous `medication_plans` and `scheduled_dose_slots` persistence;
- canonical legacy `timeOfDay` shadow;
- fixed UUIDv5 Slot ID v1 vector;
- close and reopen through a new provider over the same file;
- edit of plan fields and complete slot collection;
- update to empty slots;
- duplicate time and original order preservation;
- enable/disable persistence and reminder ordering;
- explicit Repository `Invalid` with no row and no reminder;
- delete with FK cascade while another plan remains unchanged;
- `user_version = 3`;
- no second test database;
- teardown deletion of the database and WAL/SHM/journal sidecars.

No test opens the real production database and all fixture values are synthetic.

## 12. Transaction rollback

The rollback test saves a valid aggregate, installs a synthetic trigger that aborts slot insertion for that plan, then edits the plan through the ViewModel and real Room Repository.

The observed result is structured `StorageFailure`. The test verifies:

- zero reminder calls;
- the original plan row is unchanged;
- original slots and order are unchanged;
- original legacy `timeOfDay` is unchanged;
- no partial update or insert remains;
- the database remains version 3.

This proves that the cutover does not weaken the existing aggregate transaction.

## 13. Target test results

| Command or group | Suites/classes | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| New Batch 5B JVM tests | 4 | 25 | 0 | 0 | 0 | PASS |
| Batch 5A `MedicationPlanDraftMapperTest` | 1 | 18 | 0 | 0 | 0 | PASS |
| `MedicationPlanProductionCutoverTest` | 1 | 2 | 0 | 0 | 0 | PASS |
| `MedicationPlansScreenTest` | 1 | 5 | 0 | 0 | 0 | PASS |
| `ProductionRepositoryProviderTest` | 1 | 2 | 0 | 0 | 0 | PASS |
| `RoomRepositoryTest` | 1 | 23 | 0 | 0 | 0 | PASS |
| `AppDatabaseMigrationTest` | 1 | 18 | 0 | 0 | 0 | PASS |
| `AppDatabaseMigrationMatrixTest` | 1 | 22 | 0 | 0 | 0 | PASS |
| Full `connectedDebugAndroidTest` | 8 classes | 75 | 0 | 0 | 0 | PASS |

The final connected run also contains `AppDatabaseV2BaselineTest` 2/2 and `ExampleInstrumentedTest` 1/1.

Device execution was real, not inferred from compilation:

- serial: `emulator-5556`;
- model: `sdk_gphone64_x86_64`;
- Android: `13`;
- API level: `33`.

Reports:

- HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- XML: `app/build/outputs/androidTest-results/connected/debug/`.

## 14. Complete regression

| Validation | Suites | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| Migration JVM tests | 3 | 43 | 0 | 0 | 0 | PASS |
| Mapper JVM tests | 6 | 53 | 0 | 0 | 0 | PASS |
| Core JVM tests | 5 | 47 | 0 | 0 | 0 | PASS |
| Full App JVM tests | 31 | 274 | 0 | 0 | 0 | PASS |
| PK regression | 5 | 49 | 0 | 0 | 0 | PASS |
| Wear JVM tests | 1 | 1 | 0 | 0 | 0 | PASS |
| App `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| Wear `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `lintDebug` | N/A | N/A | 0 errors | N/A | 0 | PASS |
| App `compileDebugAndroidTestKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `kspDebugKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |

Artifacts:

- App APK: `app/build/outputs/apk/debug/app-debug.apk` (69,701,999 bytes);
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes);
- App JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`;
- Wear JVM report: `wear/build/reports/tests/testDebugUnitTest/index.html`;
- lint report: `app/build/reports/lint-results-debug.html`.

Lint completed with 0 errors, 81 warnings, and 1 hint. The warnings are non-blocking existing SDK/deprecation/style/resource findings and were not auto-fixed. The recurring SDK tooling warning states that the installed processor understands SDK XML through version 3 while encountering version 4.

## 15. Validation findings fixed during the batch

The first full connected run exposed an operation publication race: terminal state was observable immediately before the operation mutex was released, so an observer could issue the next operation inside that narrow interval and have it dropped. The ViewModel now ends the in-flight state and publishes the terminal result in one synchronized boundary. Focused ViewModel, Room integration, Compose, and final full connected tests all passed after the fix.

The first full connected run also exposed an over-coupled Compose fixture. The test was split into a pure recomposition/session case and an explicit blank-name Draft case. Both final behaviors are device verified.

The first final lint run found two `LocalContext.getString` Compose errors. The screen now captures the value through `stringResource`, and the repeated lint run passed with 0 errors. No lint baseline or suppression was added.

Before independent review, the implementation validation had identified no unresolved finding. DeepSeek later recorded one P1 device-matrix blocker and two non-blocking P2 observations; the P1 revalidation and current finding counts are recorded in section 19.

## 16. Writer and boundary audit

Repository-wide source inspection after the cutover confirms:

- medication-plan UI create/edit/delete/enable operations call only the core contract;
- `MedicationPlanViewModel` calls `save`, `delete`, and `setEnabled` only on `core.dataapi.MedicationPlanRepository`;
- `MainActivity` injects only `ProductionRepositoryProvider.medicationPlans` into that ViewModel;
- legacy `MedicationPlanRepository.upsertPlan`, `deletePlan`, and `updatePlanEnabled` have no production plan-UI caller;
- legacy DAO write methods are reachable only through the unchanged legacy Repository definition, not the cutover path;
- `HRTViewModel`, reminder receiver, Widget, Wear, and JSON remain unchanged deferred consumers;
- no fallback or new/legacy dual write exists;
- no Android or Room dependency enters the pure editor/session layer;
- no real, anonymized-from-real, or real-derived health data exists in added tests.

The following have no Git difference:

- `HRTViewModel`;
- Mahiro/JSON code and format;
- reminder receivers;
- Widget;
- Wear module and `WearDataLayer`;
- Repository contracts;
- Domain models;
- Room Repository implementations;
- DAOs and Entities;
- `AppDatabase` and migrations;
- schemas 2 and 3;
- Gradle and Manifest files.

## 17. Database and schema gate

- `AppDatabase` remains version `3` with `exportSchema = true`.
- Schema 2 remains version 2 with identity hash `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Schema 3 remains version 3 with identity hash `c5f5e02cb04b048ca28fe96a74d61606`.
- Schema 2 and schema 3 have no Git difference.
- `MIGRATION_2_3` has no Git difference.
- `:app:kspDebugKotlin --rerun-tasks` completed without changing either schema.

Canonical tracked schema SHA-256 values remain:

```text
schema 2: B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA
schema 3: 044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72
```

On this Windows checkout, schema 2 working-tree bytes use checkout line endings and therefore have a different raw file hash; the Git canonical blob is unchanged and `git diff` is empty. Schema 3 working-tree and canonical hashes both match the locked value.

Migration rollback, FK, cascade, uniqueness, ordering, duplicate-time, and UUIDv5 requirements remain covered by the final 18 migration, 22 migration-matrix, 23 Repository, and 2 cutover integration tests.

## 18. Transitional status

The accepted transition remains:

- HRT/PK, receiver, Widget, and Wear plan consumers keep the legacy read representation until their authorized later batch;
- their data comes from the same Room database and they do not provide a second plan aggregate writer;
- Domain `Route`/`Ester` transitional dependencies remain unchanged from prior accepted design;
- DoseEvent cutover is not part of Batch 5B;
- Room v3 remains an internal, non-releasable schema until the later ADR-016 and Batch 8 release gates are satisfied.

## 19. DeepSeek F1 API 35 revalidation

DeepSeek's first independent review returned `REQUEST CHANGES` with one P1 finding. It ran `MedicationPlansScreenTest` on `emulator-5556`, which was later confirmed to be the `featherline_wear_api35` Wear OS AVD rather than a phone:

- Android 15 / API 35;
- model `sdk_gwear_x86_64`;
- characteristics `emulator,nosdcard,watch`;
- 454 x 454 physical size at density 320.

On that invalid phone-UI target, three of the five tests reported that `plan-name` was not displayed:

- `invalidDraftSkipsRepositoryAndKeepsEditorOpen`;
- `saveFailureKeepsEditorOpenAndShowsError`;
- `deleteFailureKeepsEditorOpen`.

The Wear AVD result did not prove either a phone UI defect or a test timing defect. No production or test file was changed in response. Revalidation used the valid `Pixel_7(AVD)` phone at `emulator-5558`: Android 15 / API 35, model `sdk_gphone64_x86_64`, 1080 x 2400 at density 420.

API 35 phone stability results:

- the complete `MedicationPlansScreenTest` class passed 5/5 tests in five consecutive Gradle runs, for 25 test executions with zero failures and zero skipped;
- each of the three original failure methods passed in five consecutive isolated Gradle runs, for 15 additional test executions with zero failures and zero skipped;
- no fixed sleep, timeout-only workaround, assertion removal, or `assertIsDisplayed` weakening was introduced;
- the full connected suite passed 75/75 with zero failures and zero skipped.

API 33 phone revalidation used `Evolune_API33_Migration(AVD)` at `emulator-5560`: Android 13 / API 33, model `sdk_gphone64_x86_64`, 1080 x 2400 at density 420.

- `MedicationPlansScreenTest`: 5/5 passed;
- `MedicationPlanProductionCutoverTest`: 2/2 passed, including the real SQLite rollback case;
- full connected suite: 75/75 passed with zero failures and zero skipped.

The post-review regression also passed: Batch 5B JVM 25/25, full App JVM 274/274, migration JVM 43/43, mapper JVM 53/53, core JVM 47/47, PK 49/49, Wear JVM 1/1, App/Wear debug builds, lint with 0 errors, androidTest Kotlin compilation, and KSP. Schemas 2 and 3 and `MIGRATION_2_3` remain unchanged.

The resolved F1 root cause is an incorrect Wear OS form factor used for a phone Compose test. The current unresolved finding count is P0/P1/P2 = `0/0/2`: DeepSeek's broad Reminder `RuntimeException` catch and defensive slot sorting observations remain non-blocking P2 items. The original DeepSeek review is preserved unchanged as historical evidence.

## 20. Batch decision

Batch 5 implementation passed pending DeepSeek re-review.

Batch 5A and Batch 5B implementation and validation are complete. The next permitted action is DeepSeek independent read-only re-review. This report does not authorize staging, committing, tagging, starting Batch 6, opening a real user database, or releasing Room v3.
