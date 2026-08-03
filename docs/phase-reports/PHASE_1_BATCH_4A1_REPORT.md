# Evolune Phase 1 Batch 4A-1 Report

Date: 2026-08-03

## 1. Batch scope

Batch 4A-1 establishes the internal Room v3 schema and the strict `MIGRATION_2_3` path. It adds the seven additive `dose_events` columns, the `scheduled_dose_slots` table, its foreign key and indexes, deterministic legacy backfill, post-migration validation, and synthetic migration instrumentation tests.

This batch does not switch repositories or product flows to the new domain model. It does not change JSON, PK parameters, UI, reminders, widgets, Wear production behavior, or release policy. Batch 3C and Batch 4B have not started.

Room v3 remains an internal, non-releasable schema until the later Phase 1 release gates are completed.

## 2. Changed files

Production and schema:

- `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/DoseEventEntity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/ScheduledDoseSlotEntity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/ScheduledDoseSlotDao.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/AppDatabaseMigrations.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyMigrationException.kt`
- `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/3.json`

Tests:

- `app/src/androidTest/java/io/github/yuninggu/evolune/data/AppDatabaseMigrationTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/AppDatabaseV2BaselineTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ExampleInstrumentedTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/DoseEventEntityMapperTest.kt`

Report:

- `docs/phase-reports/PHASE_1_BATCH_4A1_REPORT.md`

## 3. DoseEvent v3 schema

Room database version is now `3` and `exportSchema` remains `true`.

The migration adds these columns to `dose_events` without deleting or renaming any v2 column:

| Column | SQLite type | Nullability | DDL default |
|---|---:|---:|---:|
| `occurredAtEpochMillis` | INTEGER | NOT NULL | `0` |
| `zoneId` | TEXT | nullable | none |
| `localDate` | TEXT | nullable | none |
| `slotId` | TEXT | nullable | none |
| `source` | TEXT | NOT NULL | `'LEGACY'` |
| `status` | TEXT | NOT NULL | `'RECORDED'` |
| `revision` | INTEGER | NOT NULL | `1` |

Legacy `timeH` remains `REAL` and is not removed or rewritten. Runtime construction derives `occurredAtEpochMillis` strictly from valid legacy `timeH`; invalid non-finite or out-of-range values remain explicit failures. The mapper tests use an explicit synthetic persisted value only to exercise defensive handling of a corrupted materialized entity; this is not a production fallback.

## 4. Scheduled dose slot schema

The new `scheduled_dose_slots` table contains:

- `id TEXT NOT NULL PRIMARY KEY`
- `planId TEXT NOT NULL`
- `localTime TEXT NOT NULL`
- `position INTEGER NOT NULL`

Constraints and indexes:

- Foreign key from `planId` to `medication_plans.id` with `ON DELETE CASCADE`.
- Non-unique index `index_scheduled_dose_slots_planId`.
- Unique index `index_scheduled_dose_slots_planId_position`.

Backfilled slot identifiers use the locked Slot ID v1 UUIDv5 algorithm and fixed vectors. Original slot order is preserved through zero-based `position`; duplicate local times remain distinct by position and identifier.

## 5. Migration algorithm

`MIGRATION_2_3` follows the resolved order inside Room/SQLiteOpenHelper's outer upgrade transaction:

1. Apply additive DDL for the seven event columns, slot table, and indexes.
2. Preflight every legacy event and plan before any data `UPDATE` or `INSERT`.
3. Keep validated event conversions and slot rows in memory.
4. Backfill `occurredAtEpochMillis` and insert slots only after the complete preflight succeeds.
5. Verify affected rows, retained legacy values, metadata defaults, slot rows, row counts, and `PRAGMA foreign_key_check`.

The migration does not explicitly begin or commit a transaction, does not alter `user_version`, and does not use destructive migration. Exceptions propagate to the outer upgrade transaction.

## 6. Rollback and constraint verification

Connected instrumentation tests on `emulator-5556` proved the following behavior:

- A legal v2 fixture containing non-minute `20:30:15` data causes migration failure with structured context.
- The failed upgrade rolls back to `PRAGMA user_version = 2`.
- The seven v3 event columns, slot table, and v3 indexes are absent after rollback.
- Original `dose_events` values and `medication_plans.timeOfDay` strings remain unchanged.
- No partial event update or slot insertion remains.
- Foreign keys are enabled when the migrated database is opened through the generated Room implementation.
- Orphan slot insertion is rejected.
- Deleting a medication plan cascades only to its slots.
- The `(planId, position)` uniqueness constraint rejects true conflicts.
- Original ordering, duplicate times, empty legacy schedules, the fixed UUIDv5 vector, and byte-equivalent legacy `timeOfDay` strings are preserved.

An initial raw-helper constraint check exposed that `MigrationTestHelper` does not itself run the generated Room `onOpen` callback. The tests were corrected to reopen through the real Room database before asserting runtime foreign-key behavior. Production code was not relaxed or changed for this test lifecycle issue.

## 7. Instrumentation execution

Device:

- Serial: `emulator-5556`
- Model: `sdk_gphone64_x86_64`
- Android: `13`
- API level: `33`
- Gradle device name: `Evolune_API33_Migration(AVD) - 13`

Results:

| Test scope | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| `AppDatabaseMigrationTest` | 18 | 0 | 0 | 0 | PASS |
| `AppDatabaseV2BaselineTest` | 2 | 0 | 0 | 0 | PASS |
| `ExampleInstrumentedTest` target run | 1 | 0 | 0 | 0 | PASS |
| Full `connectedDebugAndroidTest` | 21 | 0 | 0 | 0 | PASS |

The tests were actually executed on the emulator; this is not an androidTest compilation-only claim.

Reports:

- XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`
- HTML: `app/build/reports/androidTests/connected/debug/index.html`

The full connected run initially exposed one unrelated legacy sample-test failure: it hardcoded the release application ID while the debug variant uses an application ID suffix. `BuildConfig.APPLICATION_ID` was attempted first, but this project does not generate an accessible app `BuildConfig` class under the current configuration and Gradle changes were outside scope. The test now obtains the instrumentation manifest's variant-specific `targetPackage` and compares it with `targetContext.packageName`. It does not hardcode either debug or release package names and does not weaken the equality assertion.

## 8. JVM, build, and lint validation

All commands used `JAVA_HOME=C:\Program Files\kedou\jre` and completed with exit code `0`.

| Command | Suites/tests or artifact | Result |
|---|---|---|
| `:app:kspDebugKotlin --rerun-tasks` | Room schema regenerated | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.migration.*" --rerun-tasks` | 3 suites / 43 tests / 0 failed / 0 skipped | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` | 5 suites / 43 tests / 0 failed / 0 skipped | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` | 5 suites / 47 tests / 0 failed / 0 skipped | PASS |
| `:app:testDebugUnitTest --rerun-tasks` | 25 suites / 221 tests / 0 failed / 0 skipped | PASS |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | 5 suites / 49 tests / 0 failed / 0 skipped | PASS |
| `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` (69,456,188 bytes) | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | 1 suite / 1 test / 0 failed / 0 skipped | PASS |
| `:wear:assembleDebug` | `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes) | PASS |
| `:app:lintDebug --rerun-tasks` | 0 errors / 80 warnings / 1 hint | PASS |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | androidTest Kotlin compiled | PASS |
| `git diff --check` | No whitespace errors | PASS |

Warnings were not automatically fixed. Existing warnings include experimental Android Gradle options, an SDK XML tooling-version warning, and existing deprecations/static-analysis findings.

## 9. Schema verification

KSP regenerated the v3 schema after the connected tests and before the final regression pass.

- Schema path: `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/3.json`
- Version: `3`
- Identity hash: `c5f5e02cb04b048ca28fe96a74d61606`
- SHA-256: `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`
- Tables: `dose_events`, `medication_plans`, `scheduled_dose_slots`
- Slot foreign keys: `1`
- Slot indexes: `2`

The committed v2 schema has no working-tree difference. Its canonical Git blob SHA-256 remains:

`B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`

## 10. Architecture boundaries

- No repository contract or implementation was switched.
- No Domain-to-v2 write mapper was introduced.
- No JSON or PK behavior changed.
- No ViewModel, UI, reminder, widget, or Wear production path changed.
- No Gradle, Manifest, or application ID setting changed.
- No Health Connect, Glance, WorkManager, Hilt, SQLCipher, or cloud synchronization was introduced.
- All migration fixtures are synthetic; no real or real-derived health data is present.
- `timeH` and `timeOfDay` remain retained compatibility fields.
- Tracked Date remains outside Phase 1.

## 11. Known limitations

- Room v3 is not yet releasable; later Phase 1 batches and release gates remain mandatory.
- Batch 3C repository/mapping integration is still deferred and is not part of this migration batch.
- The migration is intentionally strict: invalid legacy time storage, non-minute plan times, malformed UUIDs, or conversion overflow abort the upgrade rather than silently repairing data.
- Repair tooling and Batch 4B are not implemented in this batch.

## 12. Batch decision

**Batch 4A-1 passed.**

All available JVM, connected instrumentation, build, lint, schema, rollback, foreign-key, cascade, uniqueness, ordering, duplicate-time, and UUIDv5 checks passed. The implementation and this report are ready for independent DeepSeek read-only review. No staging, commit, tag, or Batch 4B work was performed.
