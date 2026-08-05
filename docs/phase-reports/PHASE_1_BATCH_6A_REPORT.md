# Evolune Phase 1 Batch 6A Report

Date: 2026-08-05

## 1. Scope

Batch 6A switches the phone HRT and DoseEvent record path to the Phase 1 contracts:

- `HRTViewModel` depends on `core.dataapi.DoseEventRepository` and Domain `DoseEvent`;
- phone create uses contract `insert`;
- phone edit uses contract CAS `update(event, expectedRevision)`;
- phone delete uses contract `delete`;
- edit sessions preserve complete Domain metadata and the original revision;
- `MainActivity` injects `ProductionRepositoryProvider.doseEvents` and `.medicationPlans`;
- JSON v1 parsing and serialization remain unchanged while import writes through a private contract bridge;
- current HRT PK behavior uses a private Batch 6 Domain-to-PK projection;
- quick record from a medication plan writes through the DoseEvent contract.

Batch 6A does not switch reminder receivers, Widget, or Wear. It does not modify Repository contracts, Domain models, Room Repository implementations, DAOs, Entities, `AppDatabase`, migrations, schemas, Gradle, Manifest, JSON v1 fields, PK algorithms, or PK parameters. It does not start Batch 6B, 6C, or Batch 7.

## 2. Files

Modified production files:

- `app/src/main/java/io/github/yuninggu/evolune/MainActivity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationRecordBottomSheet.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationRecordItem.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/screens/HomeScreen.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationRecordsScreen.kt`
- `app/src/main/java/io/github/yuninggu/evolune/viewmodel/HRTViewModel.kt`

Added production files:

- `app/src/main/java/io/github/yuninggu/evolune/application/DoseEventEditor.kt`
- `app/src/main/java/io/github/yuninggu/evolune/application/Batch6DoseEventCompatibility.kt`

Added tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/DoseEventEditorTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/application/Batch6DoseEventCompatibilityTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/viewmodel/HRTViewModelTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/DoseEventProductionCutoverTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationRecordsScreenTest.kt`

Report:

- `docs/phase-reports/PHASE_1_BATCH_6A_REPORT.md`

## 3. Production call-chain cutover

Before Batch 6A:

```text
MedicationRecordsScreen / HRTViewModel / JSON import
  -> legacy data.DoseEventRepository
  -> legacy DAO @Upsert path
```

After Batch 6A:

```text
MedicationRecordsScreen / HomeScreen
  -> DoseEventEditSession + DoseEventEditorInput
  -> HRTViewModel
  -> core.dataapi.DoseEventRepository
  -> ProductionRepositoryProvider.doseEvents
  -> RoomDoseEventRepository
  -> DoseEventDao v3 contract methods
```

There is no old/new dual write, silent fallback, second database, second truth source, direct DAO access, or Entity access in the phone HRT/UI path.

`MainActivity` still creates the legacy medication-plan Repository only for the deferred Wear dashboard synchronization path. It is not injected into `HRTViewModel`; HRT receives `ProductionRepositoryProvider.medicationPlans`.

## 4. DoseEvent editor and session

`DoseEventEditSessionFactory` owns stable create/edit boundaries.

For a new manual session:

- one UUID is generated when the session starts;
- one millisecond-precision `occurredAt` is captured;
- the exact device `ZoneId` is captured;
- `localDate` is derived from that `occurredAt` and `ZoneId`;
- recomposition, validation failure, conflict, and storage failure do not regenerate identity or time;
- only closing the old session and starting a new session creates a new identity.

For an existing event:

- the complete Domain event initializes the session;
- ID, source, status, zoneId, localDate, slotId, revision, and all extras are retained;
- the original revision is the CAS `expectedRevision`;
- an explicit time edit updates occurredAt, zoneId, and localDate together;
- an unchanged time preserves the original time metadata;
- the event is never rebuilt from the narrower PK model.

Input failures are structured as invalid dose, non-positive dose, non-millisecond or out-of-persistence-range time, and non-finite extras. No input is silently repaired.

## 5. Create metadata

Manual editor and quick-record events use:

- `source = MANUAL`;
- `status = RECORDED`;
- `revision = 1`;
- `slotId = null`;
- current device `zoneId`;
- matching `localDate`.

Quick record preserves the existing minute-flooring behavior. IDs and timestamps are captured once per operation. `Inserted` and `Idempotent` are success; `Conflict` and `Invalid` are explicit failures. Exceptions become `StorageFailure`. No failure invokes a legacy writer.

## 6. CAS update and delete

Edit calls `update(completeEvent, originalRevision)`.

- `Updated` and `NoChange` re-read the persisted event and publish its actual revision;
- `RevisionConflict` remains an explicit conflict and does not overwrite storage;
- `NotFound` does not become an insert;
- `Invalid` becomes `RepositoryInvalid`;
- storage exceptions become `StorageFailure`;
- failure keeps the same edit session open;
- UI never predicts or increments revision itself.

Delete calls the contract by ID. `Deleted` publishes one success event and closes the editor through the UI event collector. `NotFound` and storage failure keep the editor open and show a structured error.

All write operations share one synchronized in-flight gate. Duplicate submit, overlapping operations, cancellation, and exceptions cannot leave the gate locked. Terminal state is published before the success UI event so the collector can close the session without racing a stale running state.

## 7. JSON v1 bridge

`Batch6MahiroJsonBridge` is private to the Batch 6 HRT path. It keeps `MahiroJsonFormat` unchanged:

- valid IDs remain unchanged;
- missing or corrupt IDs retain the existing random UUID parser behavior;
- route, ester, timeH, doseMG, extras, field names, field order, and export structure are unchanged;
- imported Domain events use `source = JSON_V1`, `status = RECORDED`, `revision = 1`, and null zoneId/localDate/slotId;
- each valid event calls contract `insert`;
- idempotent rows count as accepted existing rows;
- conflicts are counted explicitly and never overwrite;
- invalid rows are counted explicitly;
- storage failure propagates to structured import failure with no fallback.

The formal JSON DTO/adapter remains Batch 7 work.

## 8. Temporary PK projection

`Batch6HrtPkProjection` is a private, read-only compatibility boundary used only by the current HRT PK input and JSON export bridge. It preserves:

- event order and contract selection order;
- ID, route, ester, dose, occurredAt/timeH, and extras;
- the existing `getEventsForPk` 30-day/20-event selection contract;
- current `SimulationEngine`, parameter, and tolerance behavior.

Domain-only metadata remains in the Domain/storage source and is omitted only because the legacy PK type cannot represent it. The projection does not write storage and is not reused by receivers, Widget, or Wear. The formal Domain-to-PK adapter remains Batch 7 work.

## 9. Phone UI behavior

The record list, record item, bottom sheet, and Home screen consume Domain DoseEvent and Domain MedicationPlan values.

- success closes the editor only after the Repository result and persisted re-read;
- validation, conflict, not-found, and storage failure keep the editor open;
- error text is exposed through a stable `record-error` semantic node;
- save/delete controls are disabled while an operation is running;
- `record-save`, `record-delete`, and `record-dose` provide deterministic Compose test boundaries;
- editing preserves source, status, zoneId, localDate, slotId, revision, and extras;
- Home uses the ViewModel's Domain-derived time-point flow and Domain plan predictor path.

No unrelated visual redesign or resource rewrite was made.

## 10. Real Room v3 integration

`DoseEventProductionCutoverTest` uses a disposable file-backed v3 database named `batch6a_cutover_test.db`. It never opens `evolune_database`.

The two integration tests verify:

- provider contract type and HRT ViewModel insertion;
- close/reopen through a new provider over the same disposable file;
- manual metadata persistence;
- complete non-null Wear metadata persistence through an edit;
- CAS revision increment from 1 to 2;
- stale revision conflict with every stored field unchanged;
- NotFound update with no insert;
- same-ID/same-content idempotency;
- same-ID/different-content conflict;
- JSON v1 bridge insertion through the contract;
- contract delete while unrelated rows remain;
- `user_version = 3`;
- no second test database;
- teardown cleanup of database, WAL, SHM, and journal files.

A synthetic SQLite trigger aborts a real event update. The ViewModel reports `StorageFailure`, while the original row, all v3 metadata, revision, legacy time shadow, row count, and database version remain unchanged. This proves no partial update survives the transaction failure.

## 11. Target tests

| Suite | Tests | Failures | Skipped | Result |
|---|---:|---:|---:|---|
| `DoseEventEditorTest` | 8 | 0 | 0 | PASS |
| `Batch6DoseEventCompatibilityTest` | 7 | 0 | 0 | PASS |
| `HRTViewModelTest` | 12 | 0 | 0 | PASS |
| `DoseEventProductionCutoverTest` | 2 | 0 | 0 | PASS |
| `MedicationRecordsScreenTest` | 7 | 0 | 0 | PASS |

The 27 new JVM tests cover stable sessions, complete metadata, create results, CAS results, returned persisted revision, duplicate-submit gating, cancellation release, delete results, quick-record metadata, JSON outcomes, and PK order/selection delegation.

The seven Compose tests cover create success close, local validation failure, conflict, storage failure, complete edit metadata, delete failure, and duplicate user input with one Repository write.

## 12. Connected phone matrix

The full connected suite ran independently on two phone AVDs. The Wear OS AVD was explicitly excluded from phone UI validation.

| Device | Serial | Android/API | Characteristics | Size/density | Tests | Failures | Skipped |
|---|---|---|---|---|---:|---:|---:|
| Evolune API 33 phone | `emulator-5560` | Android 13 / API 33 | `emulator` | 1080x2400 / 420 | 84 | 0 | 0 |
| Pixel 7 phone | `emulator-5558` | Android 15 / API 35 | `emulator` | 1080x2400 / 420 | 84 | 0 | 0 |

Excluded phone target:

- `emulator-5556`: API 35, model `sdk_gwear_x86_64`, characteristics `emulator,nosdcard,watch`, 454x454 at density 320.

Each 84-test run contained:

- `AppDatabaseMigrationTest`: 18;
- `AppDatabaseMigrationMatrixTest`: 22;
- `AppDatabaseV2BaselineTest`: 2;
- `RoomRepositoryTest`: 23;
- `ProductionRepositoryProviderTest`: 2;
- `MedicationPlanProductionCutoverTest`: 2;
- `DoseEventProductionCutoverTest`: 2;
- `MedicationPlansScreenTest`: 5;
- `MedicationRecordsScreenTest`: 7;
- `ExampleInstrumentedTest`: 1.

Reports:

- HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- API 33 XML at execution: `app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`;
- API 35 XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`.

## 13. Complete regression

| Validation | Suites/classes | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| Migration JVM | 3 | 43 | 0 | 0 | 0 | PASS |
| Mapper JVM | 6 | 53 | 0 | 0 | 0 | PASS |
| Core JVM | 5 | 47 | 0 | 0 | 0 | PASS |
| Batch 5 plan JVM | 3 | 35 | 0 | 0 | 0 | PASS |
| Full App JVM | 34 | 301 | 0 | 0 | 0 | PASS |
| PK regression | 5 | 49 | 0 | 0 | 0 | PASS |
| API 33 full connected | 10 | 84 | 0 | 0 | 0 | PASS |
| API 35 full connected | 10 | 84 | 0 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | 0 | PASS |
| App `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| Wear `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `lintDebug` | N/A | N/A | 0 errors | N/A | 0 | PASS |
| App `compileDebugAndroidTestKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `kspDebugKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |

Artifacts and reports:

- App APK: `app/build/outputs/apk/debug/app-debug.apk` (69,783,931 bytes);
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes);
- App JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`;
- Wear JVM HTML: `wear/build/reports/tests/testDebugUnitTest/index.html`;
- lint HTML: `app/build/reports/lint-results-debug.html`.

App and Wear assemble commands completed successfully; their final package tasks were `UP-TO-DATE` and the APK artifacts were present. Lint completed with 0 errors, 81 warnings, and 1 hint. The warnings are non-blocking existing locale, deprecation, Compose style, and resource findings. The recurring SDK tooling warning states that the installed processor understands SDK XML through version 3 while encountering version 4.

## 14. Validation fixes during Batch 6A

Focused validation found and corrected:

- extreme `Instant` input now produces structured `InvalidOccurredAtPrecision` instead of escaping as `ArithmeticException`;
- compatibility tests compare JSON v1 while excluding only the intentionally dynamic `exportedAt` value and independently validate that timestamp;
- Compose test fixtures use one `setContent` per test and wait for the save button's enabled state before duplicate-submit gestures.

The initial production compile also exposed stale nullable-session and preview-model assumptions; both were corrected before the final validation matrix. No production failure semantics were relaxed.

## 15. Writer and boundary audit

Repository-wide source inspection confirms:

- `HRTViewModel` has no legacy DoseEvent Repository dependency;
- phone HRT create/edit/delete and JSON import have no legacy writer;
- `MainActivity` injects provider contracts into HRT;
- no UI/ViewModel code imports DoseEvent DAO, Entity, or `AppDatabase`;
- the application editor and compatibility files contain no Android, Room, or Compose dependency;
- no fallback or dual write exists;
- no real, anonymized-from-real, or derived-from-real health data appears in tests.

The remaining direct DoseEvent DAO/Entity/legacy Repository production references are unchanged and deferred:

- `MedicationReminderReceiver` and `MedicationNotificationActionReceiver`: Batch 6B;
- `EvoluneWidgetReceiver`: Batch 6C;
- `WearDataLayer` and Wear listener path: Batch 6C.

No new bypass was introduced. Receiver, Widget, Wear production, Repository contracts, Domain models, DAO/Entity, Room Repository, `AppDatabase`, migration, schema, Gradle, Manifest, JSON protocol, and PK parameter/algorithm files have no Git difference.

## 16. Database and schema gate

- `AppDatabase` remains version `3` with `exportSchema = true`.
- Schema 2 identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Schema 3 identity hash remains `c5f5e02cb04b048ca28fe96a74d61606`.
- Schema 2, schema 3, and `MIGRATION_2_3` have no Git difference.
- `:app:kspDebugKotlin --rerun-tasks` executed and did not change either schema.

Canonical tracked SHA-256 values:

```text
schema 2: B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA
schema 3: 044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72
```

Migration rollback, foreign keys, cascade behavior, uniqueness, order, duplicate times, and UUIDv5 remain covered by the final migration, migration-matrix, Repository, and production-cutover suites. PK absolute tolerance remains `1e-6` and the PK suite passed 49/49.

## 17. Transitional status

The accepted transition remains:

- reminder receivers are not switched until Batch 6B;
- Widget and Wear are not switched until Batch 6C;
- the formal JSON v1 adapter and Domain-to-PK adapter remain Batch 7 work;
- all paths continue to use the same Room database as the single truth source;
- Room v3 remains an internal, non-releasable schema until later Phase 1 release gates pass.

No unresolved implementation finding was identified before independent review: P0/P1/P2 = `0/0/0`. The deferred items above are locked batch boundaries, not claims that Batch 6 is complete.

## 18. Batch decision

Batch 6A implementation passed pending independent review.

Batch 6A implementation and validation are complete. The next permitted action is DeepSeek independent read-only review. This report does not authorize staging, committing, tagging, starting Batch 6B, opening a real user database, or releasing Room v3.
