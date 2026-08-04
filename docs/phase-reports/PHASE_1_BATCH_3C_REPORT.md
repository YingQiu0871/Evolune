# Evolune Phase 1 Batch 3C Report

Date: 2026-08-04

## 1. Batch scope

Batch 3C implements the existing Phase 1 repository contracts against the committed Room v3 schema. It adds complete v3 Domain-to-persistence mapping, Room-backed event and medication-plan repositories, aggregate plan and slot reads, and atomic plan-plus-slots writes.

This batch does not wire the repositories into `MainActivity`, ViewModels, JSON import/export, reminders, widgets, Wear, or any other production entry point. It does not change the Room version, either exported schema, `MIGRATION_2_3`, Domain models, repository interfaces, Gradle configuration, PK behavior, or design documents.

Room v3 remains an internal, non-releasable schema under ADR-016.

## 2. Implemented contracts

The following existing interfaces are implemented without signature changes:

- `core.dataapi.DoseEventRepository`
  - `observeAll()`
  - `getById(UUID)`
  - `findOccurredBetween(Instant, Instant)`
  - `getEventsForPk(Instant)`
  - `insert(DoseEvent)`
  - `update(DoseEvent, expectedRevision)`
  - `delete(UUID)`
  - `deleteAll()`
- `core.dataapi.MedicationPlanRepository`
  - `observeAll()`
  - `observeEnabled()`
  - `getById(UUID)`
  - `save(MedicationPlan)`
  - `setEnabled(UUID, Boolean)`
  - `delete(UUID)`
  - `deleteAll()`

The data-side implementation classes are:

- `data.repository.RoomDoseEventRepository`
- `data.repository.RoomMedicationPlanRepository`

No feature or UI package depends on these implementations yet.

## 3. Modified and added files

Modified production files:

- `app/src/main/java/io/github/yuninggu/evolune/data/DoseEventDao.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/MedicationPlanDao.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/ScheduledDoseSlotDao.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/DoseEventEntityMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MappingResult.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapper.kt`

Added production files:

- `app/src/main/java/io/github/yuninggu/evolune/data/MedicationPlanAggregateEntity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/repository/RepositoryStorageException.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/repository/RoomDoseEventRepository.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/repository/RoomMedicationPlanRepository.kt`

Added tests:

- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/V3PersistenceMapperTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/RoomRepositoryTest.kt`

Added report:

- `docs/phase-reports/PHASE_1_BATCH_3C_REPORT.md`

## 4. DAO changes

`DoseEventDao` now provides repository-specific v3 reads ordered by `occurredAtEpochMillis`, a half-open range query, the two queries required by the frozen PK selection branches, ID lookup, insert-with-conflict detection, revision-checked update, and row-count-returning delete operations. Existing legacy DAO methods remain available and unchanged.

`MedicationPlanDao` now provides transactional `MedicationPlanAggregateEntity` reads using Room `@Relation`, checked insert and complete-row update operations, checked enabled-state update, checked deletes, and plan counting. Existing legacy DAO methods remain available and unchanged.

`ScheduledDoseSlotDao` now provides checked slot insertion, exact deletion by plan, and count queries used to verify aggregate replacement and cascade deletion.

All SQL is static. No UI-, Widget-, Wear-, cloud-, paging-, or LiveData-specific query was added.

## 5. Mapping behavior

### DoseEvent

The v3 mapper treats `occurredAtEpochMillis` as authoritative while requiring the retained `timeH` shadow to round-trip to the same millisecond. It explicitly maps Route, Ester, ExtraKey, source, and status values. It parses nullable `zoneId` and `localDate`, preserves nullable `slotId`, and validates revision invariants.

Domain-to-Entity mapping:

- requires an `Instant` representable exactly at millisecond precision;
- persists exact `occurredAtEpochMillis`;
- generates `timeH = epochMillis / 3_600_000.0` and verifies the reverse conversion;
- never falls back to epoch zero, current time, current zone, or an inferred slot;
- persists nullable metadata without substitution;
- preserves source, status, revision, extras, dose, route, and ester.

The Batch 3B legacy v2 `DoseEventEntity -> Domain` mapper remains present and all of its tests still pass.

### MedicationPlan aggregate

`MedicationPlanAggregateEntity` reads a plan and its Room-related slot rows in a transaction. The mapper orders the slot set by `position` and then validates:

- every slot belongs to the plan;
- positions are zero-based, continuous, and unique;
- every `localTime` is canonical `HH:mm` at minute precision;
- every ID matches Slot ID UUIDv5 v1 for plan, position, and local time;
- the legacy `timeOfDay` list is semantically identical to the authoritative slot list.

Legacy zero-second representations such as `08:30:00` remain readable, but newly persisted values are always canonical `08:30`. Order and duplicate local times are preserved. No read performs an automatic database repair.

Domain-to-persistence mapping produces both the complete `MedicationPlanEntity` and the complete ordered `ScheduledDoseSlotEntity` list. `timeOfDay` is generated from the slots in position order as canonical strings, including `[]` for an empty list.

## 6. MedicationPlan aggregate transaction

`RoomMedicationPlanRepository.save` performs the following steps inside one `AppDatabase.withTransaction` block:

1. map and validate the complete Domain aggregate before database writes;
2. read and validate the existing aggregate;
3. return `NoChange` for identical content;
4. insert a new plan or update exactly one existing plan row;
5. delete exactly the previous number of slot rows;
6. insert the complete new slot list and verify the insert result count;
7. re-read and verify the final aggregate before transaction completion;
8. return `Created` or `Updated` according to the existing contract.

The plan contract has no conflict result. Therefore a changed aggregate with an existing ID is an atomic full replacement and returns `Updated`, as locked by ADR-015. This differs from event insert behavior, which has an explicit `Conflict` result.

`setEnabled`, single delete, and delete-all also use checked row counts and transactions. Plan deletion verifies FK cascade removed all slots. Other plans remain untouched.

## 7. Transaction rollback proof

The instrumentation test creates a synthetic SQLite trigger that aborts slot insertion after the plan row has been updated and the old slots have been deleted. The repository surfaces a `RepositoryConstraintException` and the outer Room transaction rolls back.

After failure, the test verifies:

- the original plan is unchanged;
- the original slots are unchanged;
- legacy `timeOfDay` is unchanged;
- there is no partial slot deletion or insertion;
- another plan and its slots are unchanged.

This is a real Room/SQLite failure on `emulator-5556`, not a mock transaction test.

## 8. DoseEvent repository semantics

- New ID and valid revision 1: `Inserted`.
- Same ID and identical content: `Idempotent`, with no second row.
- Same ID and different content: `Conflict`, with no overwrite.
- Invalid persistence mapping or non-initial insert revision: `Invalid`, with no row.
- Update with matching revision and meaningful content change: row is updated and revision increments by one.
- Update with identical business content: `NoChange`, with no revision increment.
- Missing update target: `NotFound`.
- Mismatched revision: `RevisionConflict`.
- Delete results use checked row counts and return `Deleted` or `NotFound`.
- `observeAll` uses occurred-at descending order.
- `findOccurredBetween` uses the required half-open interval and ascending order.
- `getEventsForPk` retains the legacy 30-day/20-event decision and the distinct descending/ascending branch orders.

## 9. Error boundary

Input mapping failures return the existing `Invalid` business results. Persisted rows or aggregates that violate the v3 contract throw `CorruptAggregateException` with a structured `MappingError`; reads do not silently guess or repair values.

SQLite constraint failures are wrapped as `RepositoryConstraintException`. Other SQLite failures are wrapped as `RepositoryPersistenceException`. These remain infrastructure exceptions as required by ADR-015. Stable business results do not expose SQLite messages, complete rows, doses, extras, or health records.

## 10. Synthetic test coverage

The 23 repository instrumentation tests cover:

- event complete-field persistence and readback;
- exact legacy `timeH` double-write behavior;
- epoch zero, positive, and negative times;
- nullable and non-null metadata;
- event idempotency, conflict, update revision, no-change, not-found, invalid input, range, observation order, PK branch order, deletes, and database constraint mapping;
- empty, single, and multiple-slot plans;
- `00:00`, `08:30`, and `23:59`;
- order and duplicate preservation;
- fixed UUIDv5 vector `17d1fd14-9d70-5344-beaa-0b158c9f62f4`;
- aggregate read, exact slot replacement, update-to-empty, field-only update, idempotent save, enabled-state update, delete, FK cascade, delete-all, and other-plan isolation;
- invalid slot ID rejection;
- explicit corrupt legacy/slot mismatch with no repair;
- real transaction rollback after slot insertion failure.

The 10 new JVM mapper tests cover v3 full-field round trips, sub-millisecond rejection, event shadow inconsistency, invalid metadata strings, plan round trips, invalid slot identity, wrong plan ID, duplicate/non-contiguous positions, non-canonical slot time, and legacy/slot mismatch.

All fixtures use fixed or deterministically generated synthetic UUIDs, times, labels, doses, and extras. No real, anonymized-from-real, or real-derived health data is present.

## 11. Validation results

Environment:

- device serial: `emulator-5556`;
- model: `sdk_gphone64_x86_64`;
- Android: 13;
- API level: 33;
- boot completed: `1`;
- Android service manager: available, 255 services reported.

| Command | Exit | Tests / artifact | Result |
|---|---:|---|---|
| `:app:connectedDebugAndroidTest -P...class=...RoomRepositoryTest --rerun-tasks` | 0 | 23 tests, 0 failures, 0 errors, 0 skipped | PASS, executed on emulator-5556 |
| `:app:connectedDebugAndroidTest --rerun-tasks` | 0 | 66 tests, 0 failures, 0 errors, 0 skipped | PASS, executed on emulator-5556 |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.*" --rerun-tasks` | 0 | 9 suites, 96 tests, 0 failures, 0 errors, 0 skipped | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` | 0 | 6 suites, 53 tests, 0 failures, 0 errors, 0 skipped | PASS; includes all 43 Batch 3B tests plus 10 v3 tests |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` | 0 | 5 suites, 47 tests, 0 failures, 0 errors, 0 skipped | PASS |
| `:app:testDebugUnitTest --rerun-tasks` | 0 | 26 suites, 231 tests, 0 failures, 0 errors, 0 skipped | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | 0 | 5 suites, 49 tests, 0 failures, 0 errors, 0 skipped | PASS |
| `:app:assembleDebug` | 0 | `app/build/outputs/apk/debug/app-debug.apk`, 69,587,252 bytes | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | 0 | 1 suite, 1 test, 0 failures, 0 errors, 0 skipped | PASS |
| `:wear:assembleDebug` | 0 | `wear/build/outputs/apk/debug/wear-debug.apk`, 14,565,775 bytes | PASS |
| `:app:lintDebug --rerun-tasks` | 0 | 0 errors, 80 warnings, 1 hint | PASS; existing warnings were not modified |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | 0 | androidTest Kotlin compiled | PASS |
| `:app:kspDebugKotlin --rerun-tasks` | 0 | Room processing completed | PASS |
| `git diff --check` | 0 | no whitespace errors | PASS |

Reports and outputs:

- connected XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`;
- connected HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`;
- lint HTML: `app/build/reports/lint-results-debug.html`;
- lint XML: `app/build/reports/lint-results-debug.xml`;
- App APK: `app/build/outputs/apk/debug/app-debug.apk`;
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk`.

The Android SDK tooling continues to emit the existing SDK XML version warning and existing source deprecation warnings. No new build or lint error was introduced.

## 12. Database and schema gate

- `AppDatabase.version = 3`.
- `exportSchema = true`.
- schema 2 has no Git diff.
- schema 3 has no Git diff.
- schema 3 identity hash remains `c5f5e02cb04b048ca28fe96a74d61606`.
- schema 3 SHA-256 remains `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`.
- `MIGRATION_2_3` has no Git diff.
- no Entity field, table, column, index, foreign key, or database version changed in Batch 3C.

## 13. Architecture boundary

- Repository interfaces and Domain models are unchanged.
- DAO methods return persistence types, not Domain models.
- Repository methods return Domain models and contract results, not Room entities, cursors, or SQLite databases.
- Mappers do not depend on Activity, Context, Compose, Wear, Health Connect, Glance, WorkManager, cloud sync, or UI code.
- No second production database builder or global mutable repository singleton was created.
- Existing legacy repositories and all current callers remain unchanged; Batch 3C is deliberately not wired into runtime entry points.
- JSON, PK, reminders, widgets, Wear production code, Manifest, Gradle, and design documents are unchanged.

## 14. Known transitional risks

- The new repositories are not yet connected to production callers. Existing UI, reminder, widget, Wear, and JSON paths still use the legacy repositories until a later authorized batch.
- `core.model` still depends on the transitional PK Route and Ester enums, as already accepted in Batch 3B.
- Room v3 remains non-releasable until all ADR-016 release gates, including entry-point conversion and Batch 8 exit validation, pass.
- Plan save intentionally follows the existing `PlanSaveResult` contract, which has no conflict member and no plan revision field.

## 15. Batch decision

Batch 3C engineering implementation and validation passed.

The Room-backed repositories, complete v3 mappers, aggregate transaction, real rollback proof, JVM tests, connected tests, App/Wear builds, PK regression, lint, androidTest compilation, KSP, and schema gates all pass. No P0 or P1 issue remains from this implementation.

The next permitted action is independent DeepSeek read-only review. This report does not authorize staging, committing, tagging, runtime wiring, the next batch, or release of Room v3.
