# Evolune Phase 1 Batch 5A Report

Date: 2026-08-04

## 1. Scope

Batch 5A adds two unconnected production foundations:

- Batch 5A-1: a minimal production Repository provider that constructs the two existing Room Repository implementations from the existing production `AppDatabase` singleton;
- Batch 5A-2: a pure Kotlin `MedicationPlanDraft` boundary and bidirectional Draft/Domain adapter;
- focused JVM and connected instrumentation tests;
- complete App, Wear, Room, migration, PK, lint, and build regression.

This batch does not wire `MainActivity`, ViewModels, Compose UI, JSON, reminders, widgets, Wear, or Predictor/PK to the new provider or Draft adapter. It does not change user-visible behavior or start Batch 5B.

## 2. Added files

Production:

- `app/src/main/java/io/github/yuninggu/evolune/data/repository/ProductionRepositoryProvider.kt`
- `app/src/main/java/io/github/yuninggu/evolune/application/MedicationPlanDraftMapper.kt`

Tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/MedicationPlanDraftMapperTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/ProductionRepositoryProviderTest.kt`

Report:

- `docs/phase-reports/PHASE_1_BATCH_5A_REPORT.md`

No existing tracked file was modified.

## 3. Production Repository provider

`ProductionRepositoryProvider` exposes stable contract-typed properties:

- `doseEvents: core.dataapi.DoseEventRepository`
- `medicationPlans: core.dataapi.MedicationPlanRepository`

Production access is through:

```kotlin
ProductionRepositoryProvider.get(context: Context)
```

The production path passes `context.applicationContext` to the existing `AppDatabase.getDatabase` singleton and creates both `RoomDoseEventRepository` and `RoomMedicationPlanRepository` from that same `AppDatabase` instance. The provider itself is a thread-safe singleton and each Repository property is initialized once, so repeated access through one provider returns the same Repository objects.

The provider does not expose `AppDatabase`, a DAO, or an Entity. It performs no automatic read or write and has no Activity, ViewModel, Compose, JSON, reminder, widget, Wear, or PK dependency. No second production Room builder, Hilt binding, Koin binding, or Gradle dependency was added.

## 4. Single AppDatabase proof

Source inspection found one production construction path in the provider: `AppDatabase.getDatabase(context.applicationContext)`. Both concrete Room Repositories receive the database obtained by that call.

The connected provider test additionally injected one disposable file-backed `AppDatabase`, saved and read one synthetic `MedicationPlan` and one synthetic `DoseEvent` through the two contract surfaces, and verified:

- both Repository getters are stable;
- their public JVM return types are the Repository contracts;
- both operate on the same injected database;
- the database has `user_version = 3`;
- no second database with the Batch 5A test prefix exists;
- only the expected database, WAL, SHM, or journal sidecars may appear;
- data survives close and reopen through a new provider over the same file.

The test never calls the production provider singleton and never opens `evolune_database`.

## 5. Disposable test seam

The only test seam is:

```kotlin
internal constructor(database: AppDatabase)
```

Instrumentation supplies an explicitly named disposable Room database to that constructor. The provider does not own or alter the test database lifecycle. Test setup and teardown close the database and remove the database file plus `-wal`, `-shm`, and `-journal` sidecars. The seam does not reset or mutate the production singleton and is not a public mutable service locator.

## 6. MedicationPlanDraft

The Draft is a pure application-boundary value with exactly 12 fields:

```kotlin
data class MedicationPlanDraft(
    val id: UUID,
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<ExtraKey, Double>,
    val createdAt: Instant
)
```

It has no revision, Room field, legacy `timeOfDay` string, Android `Context`, Compose state, JSON field, or reminder/widget/Wear field. It does not read a clock, generate a random UUID, inspect the default time zone or Locale, access a Repository, or perform I/O.

## 7. DraftMappingResult

The application boundary defines its own pure result protocol instead of reusing persistence or Repository results:

- `DraftMappingResult.Success<T>`
- `DraftMappingResult.InvalidDraft`

Stable issues are:

- `MissingRequiredField(NAME)`
- `NonMinuteTime(position)`
- `SlotIdMismatch(position)`
- `SlotIdGenerationFailure(position)`
- `DomainValidationFailure`

Issues contain no exception message, complete plan, complete time list, dose, extras, or other health data. Validation order is stable: field issues precede time issues, and time issues follow input position order. Only `IllegalArgumentException` from explicit Domain construction is translated to `DomainValidationFailure`; unrelated programming errors continue to propagate.

## 8. Draft to Domain

`MedicationPlanDraft.toDomainMedicationPlan()`:

- rejects a blank name with `MissingRequiredField(NAME)`;
- accepts empty `times`;
- preserves time order and duplicate values;
- accepts `00:00` and `23:59`;
- rejects non-zero seconds or nanos without truncation;
- assigns each slot `position` from the original list index;
- assigns every slot the Draft plan ID;
- uses only `ScheduledDoseSlotId.generate` for UUIDv5 Slot ID v1;
- preserves the plan ID, fixed `createdAt`, extras, days of week, and every other Draft field;
- constructs the complete `core.model.MedicationPlan` aggregate;
- performs no sorting, deduplication, repair, clock read, random ID generation, or persistence access.

The locked vector remains:

```text
planId: 00000000-0000-0000-0000-000000000001
position: 0
localTime: 08:30
slotId: 17d1fd14-9d70-5344-beaa-0b158c9f62f4
```

## 9. Domain to Draft

`MedicationPlan.toMedicationPlanDraft()` preserves the plan ID, `createdAt`, complete extras map, and every other field. It derives `times` from Domain slots in authoritative list order and validates each slot's plan ownership, index position, minute precision, and expected UUIDv5 ID.

A mismatched ID returns `SlotIdMismatch(position)`. The adapter does not silently repair, reorder, renumber, deduplicate, or query the database.

ID and creation-time ownership remain explicit: Batch 5A accepts and preserves caller-supplied values. New-plan ID generation and `createdAt` capture remain Batch 5B creation-session responsibilities.

## 10. Target validation

| Command or group | Suites | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.application.*"` | 1 | 18 | 0 | 0 | 0 | PASS |
| Provider-only connected instrumentation | 1 | 2 | 0 | 0 | 0 | PASS |
| Repository connected instrumentation | 1 | 23 | 0 | 0 | 0 | PASS |
| `AppDatabaseMigrationTest` | 1 | 18 | 0 | 0 | 0 | PASS |
| `AppDatabaseMigrationMatrixTest` | 1 | 22 | 0 | 0 | 0 | PASS |
| Full `connectedDebugAndroidTest` | 6 | 68 | 0 | 0 | 0 | PASS |

Connected tests ran on the real configured test target for this batch:

- serial: `emulator-5556`;
- model: `sdk_gphone64_x86_64`;
- Android: `13`;
- API level: `33`.

The final connected report is `app/build/reports/androidTests/connected/debug/index.html`; the XML result is under `app/build/outputs/androidTest-results/connected/debug/`.

The 68-test connected run contains:

- `ProductionRepositoryProviderTest`: 2;
- `RoomRepositoryTest`: 23;
- `AppDatabaseMigrationTest`: 18;
- `AppDatabaseMigrationMatrixTest`: 22;
- `AppDatabaseV2BaselineTest`: 2;
- `ExampleInstrumentedTest`: 1.

These are device-executed results, not merely androidTest compilation results.

## 11. JVM and build regression

| Validation | Suites | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| Migration JVM tests | 3 | 43 | 0 | 0 | 0 | PASS |
| Mapper JVM tests | 6 | 53 | 0 | 0 | 0 | PASS |
| Core JVM tests | 5 | 47 | 0 | 0 | 0 | PASS |
| Full App JVM tests | 27 | 249 | 0 | 0 | 0 | PASS |
| PK regression | 5 | 49 | 0 | 0 | 0 | PASS |
| Wear JVM tests | 1 | 1 | 0 | 0 | 0 | PASS |
| App `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| Wear `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `lintDebug` | N/A | N/A | 0 errors | N/A | 0 | PASS |
| App `compileDebugAndroidTestKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `kspDebugKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |

Reports and artifacts:

- App JVM report: `app/build/reports/tests/testDebugUnitTest/index.html`;
- Wear JVM report: `wear/build/reports/tests/testDebugUnitTest/index.html`;
- lint report: `app/build/reports/lint-results-debug.html`;
- App APK: `app/build/outputs/apk/debug/app-debug.apk`;
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk`.

Lint completed with 0 errors, 80 warnings, and 1 hint. The recurring SDK tooling warning states that the installed processor understands SDK XML through version 3 while encountering version 4. Wear compilation also reports two existing deprecated Tiles API calls in `DoseTileService.kt`. These warnings are non-blocking and were not auto-fixed in this batch.

## 12. Room schema and migration gate

- `AppDatabase` remains version `3` with `exportSchema = true`.
- Schema 2 has no Git difference.
- Schema 3 has no Git difference.
- `MIGRATION_2_3` has no Git difference.
- Domain models, Repository contracts, DAOs, Entities, and existing Room Repository implementations have no Git difference.
- `:app:kspDebugKotlin --rerun-tasks` completed successfully without producing a schema change.

Schema 3 fixed values remain:

```text
identityHash: c5f5e02cb04b048ca28fe96a74d61606
SHA-256: 044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72
```

Batch 5A does not alter or weaken migration, rollback, FK, cascade, uniqueness, ordering, duplicate-time, or UUIDv5 behavior. Those checks remain covered by the 18 migration tests, 22 migration-matrix tests, and 23 Repository tests in the full connected run.

## 13. Architecture boundary audit

Repository-wide source inspection confirmed:

- `ProductionRepositoryProvider` has no production caller;
- the Draft mapping functions have no UI or ViewModel caller;
- `MainActivity`, all ViewModels, Compose UI, and current legacy read/write paths are unchanged;
- no new production database access, dual write, fallback, or automatic data operation exists;
- no Reminder, Widget, Wear, Predictor, or JSON side effect was introduced;
- no Android or Room dependency enters the pure Draft adapter;
- no forbidden tracked file was modified.

The only provider references outside its declaration are in `ProductionRepositoryProviderTest`. The only new Draft adapter references outside its declaration are in `MedicationPlanDraftMapperTest`. Existing persistence mapper functions with similar names are separate and unchanged.

## 14. Data provenance

All UUIDs, plan names, times, doses, extras, events, and database rows used by Batch 5A tests are synthetic constants. No real, anonymized-from-real, or real-derived health data is present. The disposable instrumentation database is test-only and is deleted with its sidecars after each test.

## 15. Findings and transitional status

Implementation validation found:

- P0: 0;
- P1: 0;
- P2: 0.

The first provider instrumentation attempt exposed a test-only assumption that Android's database listing excludes WAL/SHM sidecars. The test was narrowed to permit only the known sidecars while still rejecting any second database. The corrected provider test passed 2/2 and the full connected suite passed 68/68. Production code was not changed for that test issue.

Independent DeepSeek read-only review has not yet occurred and remains the next review gate.

## 16. Batch decision

Batch 5A passed.

Batch 5B has not started. Batch 5 as a whole remains incomplete, and no production UI/ViewModel call chain has been switched. Room v3 remains an internal, non-releasable schema until the later ADR-016 and Batch 8 release gates are satisfied.

The next permitted action is independent DeepSeek read-only review of the five Batch 5A files. This report does not authorize staging, committing, tagging, starting Batch 5B, using a real database, or releasing Room v3.
