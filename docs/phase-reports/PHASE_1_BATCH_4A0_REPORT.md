# Evolune Phase 1 Batch 4A-0 Report

Date: 2026-08-03

## 1. Scope

Phase 1 Batch 4A-0 establishes only:

- Structured legacy migration error and result types.
- A strict legacy medication-plan time parser.
- A `timeH` migration primitive that delegates to `LegacyTimeAdapter`.
- A pure SQLite storage-class decision primitive.
- Pure JVM tests for these boundaries.

This batch does not include:

- Room v3 or an `AppDatabase` version change.
- `MIGRATION_2_3`, schema 3, new columns, or new tables.
- Entity, DAO, Repository, JSON, PK, UI, Reminder, Widget, or Wear changes.
- A Cursor or `SupportSQLiteDatabase` adapter.
- An androidTest migration implementation or device migration execution.
- A repair CLI, production wiring, Batch 3C, or Batch 4A-1.

The final code audit found P0 = 0, P1 = 0, and P2 = 0. No corrective code change was required during the final audit.

## 2. Added files

### Production

- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyMigrationError.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyPlanTimeParser.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/MigrationPrimitives.kt`

### Tests

- `app/src/test/java/io/github/yuninggu/evolune/data/migration/LegacyPlanTimeParserTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/migration/MigrationPrimitivesTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/migration/LegacyStorageClassTest.kt`

No previously tracked source, test, schema, Gradle, resource, design, or review file was modified.

## 3. Legacy timeH primitive

`legacyTimeHToOccurredAtEpochMillis` delegates directly to `LegacyTimeAdapter.timeHToEpochMillis`. It does not duplicate the multiplication constant, `Math.round`, finite-value checks, or range checks.

Covered successful values include epoch zero, positive and negative values, millisecond-precision reconstruction, representable values immediately across positive and negative half-millisecond rounding boundaries, and a fixed hard-coded conversion vector.

NaN, positive infinity, negative infinity, positive and negative multiplication overflow, and positive and negative epoch-millisecond range overflow return structured failures. Failures retain the event UUID, the original `timeH`, and the original `LegacyTimeError`. The primitive does not clamp, return zero, use the current time, or modify the legacy value.

## 4. Legacy plan time parser

The parser uses the default `kotlinx.serialization.json.Json` configuration, matching the existing Room `Converters` boundary. A SQL empty string and JSON `[]` both produce an empty list. All other inputs must be a JSON array containing only strings; malformed JSON, a non-array root, and non-string elements fail with structured context.

String elements use ISO `LocalTime` parsing. `HH:mm`, `HH:mm:00`, and equivalent ISO expressions whose second and nano are both zero are accepted. New slot time values are canonicalized to minute precision `HH:mm`, while the original string and complete raw JSON remain unchanged in the parsed result.

Any non-zero second or nano fails the complete parse. Values are not trimmed, truncated, rounded, skipped, sorted, deduplicated, renumbered, or repaired. Original order, duplicates, zero-based positions, plan UUID, and original values are preserved.

Slot IDs delegate to `ScheduledDoseSlotId.generate`, retaining the published UUIDv5 v1 namespace, UTF-8 canonical-name rules, and fixed vector `17d1fd14-9d70-5344-beaa-0b158c9f62f4`. No random UUID, default Locale, default time zone, or default charset participates in parsing or ID generation. Locale and time-zone independence tests change the actual process defaults and restore them in `finally` blocks.

## 5. Storage class policy

The pure decision primitive accepts `INTEGER` and `FLOAT`. It rejects `NULL`, `STRING`, and `BLOB` with a structured `InvalidTimeHStorageClass` error.

This is intentionally not a Cursor adapter. It has no Android, Room, DAO, database, or `SupportSQLiteDatabase` dependency. The actual `Cursor.isNull`/`Cursor.getType`/`Cursor.getDouble` ordering belongs to Batch 4A-1.

## 6. Validation

All final Gradle validations ran sequentially with `JAVA_HOME=C:\Program Files\kedou\jre`. Test counts come from UTF-8-decoded JUnit XML, not assertion counts.

| Command | Exit code | Result | Suites / tests | Failures / errors / skipped | Artifact or note |
|---|---:|---|---:|---:|---|
| `git diff --check` | 0 | PASS | N/A | N/A | No tracked whitespace errors; all seven untracked files also passed explicit trailing-whitespace and final-newline checks |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.migration.*" --rerun-tasks` | 0 | PASS | 3 / 43 | 0 / 0 / 0 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` | 0 | PASS | 5 / 47 | 0 / 0 / 0 | Actual JUnit XML count |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` | 0 | PASS | 5 / 43 | 0 / 0 / 0 | Actual JUnit XML count |
| `:app:testDebugUnitTest --rerun-tasks` | 0 | PASS | 25 / 221 | 0 / 0 / 0 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | 0 | PASS | 5 / 49 | 0 / 0 / 0 | PK regression and existing `1e-6` tolerance unchanged |
| `:app:assembleDebug` | 0 | PASS | N/A | N/A | `app/build/outputs/apk/debug/app-debug.apk`, 69,519,470 bytes |
| `:wear:testDebugUnitTest --rerun-tasks` | 0 | PASS | 1 / 1 | 0 / 0 / 0 | Actual JUnit XML count |
| `:wear:assembleDebug` | 0 | PASS | N/A | N/A | `wear/build/outputs/apk/debug/wear-debug.apk`, 14,565,775 bytes; valid UP-TO-DATE tasks count as successful validation |
| `:app:lintDebug --rerun-tasks` | 0 | PASS | N/A | 0 errors / 79 warnings | One additional hint; `app/build/reports/lint-results-debug.html` |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | 0 | PASS | Compilation only | Tests not executed | androidTest Kotlin compiled; no device execution is claimed |

An initial sandboxed Gradle-wrapper invocation could not access the fixed Gradle distribution URL. The same command passed after Gradle was allowed to use the host cache/network. This was an execution-environment restriction, not a source, compilation, or test failure. JUnit XML was explicitly read as UTF-8 because the localized machine hostname is not valid when decoded with PowerShell's legacy default encoding.

The Android SDK XML version warning, existing experimental Gradle property warnings, compiler deprecation warnings, and Git global-ignore permission warning remain non-blocking environment/project notices. No warning was automatically fixed in this batch.

## 7. Database/schema

- `AppDatabase` remains version 2.
- `exportSchema` remains `true`.
- Schema identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Canonical committed Git blob/LF SHA-256 remains `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
- The Windows working-tree byte SHA-256 is `C4770838B9D6E78A06E796418D4CEF6292F3090E40B77425F769360E0CEEC4DA` because the checkout uses CRLF. This line-ending representation difference does not produce a Git diff and is not the canonical schema hash.
- `git diff` for schema 2 is empty.
- No `MIGRATION_2_3`, schema `3.json`, `scheduled_dose_slots`, v3 column, destructive migration, or `fallbackToDestructiveMigration` exists.
- Existing Entity, DAO, and `AppDatabase` files are unchanged.

## 8. Architecture boundaries

- Production primitives are pure Kotlin/JVM and contain no Android, Room, Cursor, DAO, database, Repository, Compose, or Wear dependency.
- The `timeH` formula remains centralized in `LegacyTimeAdapter`.
- Slot ID generation remains centralized in `ScheduledDoseSlotId`.
- No schema, DDL, migration transaction, Cursor adapter, or production Repository wiring was introduced.
- Tests use only synthetic UUIDs, synthetic times, and synthetic values. No real or real-derived health data is present.
- No existing tracked file was changed.
- Tracked Date, Health Connect, Glance, WorkManager, cloud synchronization, repair tooling, Batch 3C, and Batch 4A-1 remain outside this batch.

## 9. Known limitations

- There is not yet a Cursor or `SupportSQLiteDatabase` adapter.
- There is not yet an actual v2-to-v3 transaction.
- There is not yet an instrumentation migration implementation or device migration execution.
- The repair CLI remains deferred to Batch 4C.
- Batch 4A-1 will enter the documented internal, non-releasable v3 interval and must not be distributed to users or run against real health data.

## 10. Decision

Batch 4A-0 passed.

The next step is an independent Claude read-only review. If that review finds no P0/P1 issue, the six implementation/test files and this report may be submitted intentionally and tagged `phase-1-batch-4a0`. Only after that tag exists may Batch 4A-1 be planned. This report does not authorize direct database changes, a release, Batch 3C, or Batch 4A-1 implementation.
