# Evolune Phase 1 Batch 2 Report

Validation date: 2026-08-02 (Asia/Shanghai)

Code review: `reviews/PHASE_1_BATCH_2_REVIEW.md` — `APPROVE WITH P2` (P0: 0, P1: 0, P2: 3)

## 1. Batch scope

This batch establishes only the following disconnected Phase 1 foundations inside the App module:

- Domain `DoseEvent`;
- `DoseEventSource`;
- `DoseEventStatus`;
- `ScheduledDoseSlot`;
- versioned Slot ID UUIDv5 generation;
- `LegacyTimeAdapter`;
- JVM unit tests for these foundations.

This batch does not include a Repository contract, Room mapper, PK adapter, Room v3 migration, or production business-path switch. The new types are not connected to ViewModel, UI, Reminder, Widget, Wear, JSON import/export, DAO, Repository, or Room runtime paths.

## 2. Added files

Production files:

- `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEvent.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEventSource.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/model/DoseEventStatus.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/model/ScheduledDoseSlot.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/time/LegacyTimeAdapter.kt`

Test files:

- `app/src/test/java/io/github/yuninggu/evolune/core/model/DoseEventTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/core/model/ScheduledDoseSlotTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/core/time/LegacyTimeAdapterTest.kt`

Review file:

- `reviews/PHASE_1_BATCH_2_REVIEW.md`

## 3. Domain model

- `occurredAt: Instant` is the authoritative event time.
- Legacy `zoneId`, `localDate`, and `slotId` are nullable.
- `revision` starts at 1 and must remain greater than or equal to 1.
- Phase 1 `DoseEventStatus` contains only `RECORDED`.
- `DoseEventSource` contains `LEGACY`, `MANUAL`, `JSON_V1`, `REMINDER`, `WIDGET`, and `WEAR`.
- The transitional model temporarily reuses existing PK `Route` and `Ester` types.
- Domain `ExtraKey` is distinct from PK `ExtraKey`; an explicit mapper remains required before integration.

## 4. ScheduledDoseSlot ID v1

- Algorithm: RFC variant UUID version 5 using SHA-1 only for stable identification, not for cryptographic security, signatures, authentication, or integrity checks.
- Root DNS namespace: `6ba7b810-9dad-11d1-80b4-00c04fd430c8`.
- Project namespace name: UTF-8 `io.github.yuninggu.evolune:scheduled-dose-slot`.
- Fixed project namespace: `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`.
- Canonical name: `slot:v1:plan=<canonicalPlanUuid>;position=<canonicalPosition>;time=<canonicalLocalTime>`.
- `position` accepts `0..Int.MAX_VALUE` and uses non-localized decimal ASCII.
- `localTime` requires minute precision (`second = 0`, `nano = 0`) and canonical `HH:mm` output.
- Fixed vector input `00000000-0000-0000-0000-000000000001`, position `0`, and `08:30` produces `17d1fd14-9d70-5344-beaa-0b158c9f62f4`.
- Tests verify UUID version 5 and RFC variant 2.
- Invalid UUID text, surrounding plan ID whitespace, negative positions, non-minute times, namespace verification failure, and UUIDv5 construction failure return explicit failures. The generator does not trim, truncate, generate random fallback IDs, return a silent null, or read system time, locale, or time zone.

## 5. LegacyTimeAdapter

- Conversion formula: `scaledMillis = timeH * 3_600_000.0`, followed by `Math.round(scaledMillis)`.
- Non-finite `timeH` and non-finite multiplication results are explicit failures.
- The accepted pre-rounding millisecond interval is `[-2^63, 2^63)`.
- `timeH` to millisecond and Instant round trips are tested with a maximum one-millisecond tolerance.
- Local date-time conversion requires an explicit `ZoneId` and uses Java `LocalDateTime.atZone` behavior.
- DST gaps move forward by the gap and DST overlaps use the earlier offset, matching Java defaults.
- Invalid values are not clamped and do not fall back to epoch zero.

## 6. Test results

| Command | Exit code | Result | Suites / tests | Failures / skipped | Actual execution and output |
| --- | ---: | --- | --- | --- | --- |
| `git diff --check` | 0 | PASS | N/A | N/A | Executed before validation; no whitespace errors in tracked differences. |
| `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 3 / 26 | 0 / 0 | Tests were rerun. XML: `app/build/test-results/testDebugUnitTest`; HTML: `app/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 15 / 114 | 0 / 0 | Tests were rerun. XML: `app/build/test-results/testDebugUnitTest`; HTML: `app/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 5 / 49 | 0 / 0 | PK tests were rerun. XML: `app/build/test-results/testDebugUnitTest`; HTML: `app/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | 0 | PASS | N/A | N/A | Gradle verified all tasks as up-to-date. APK exists at `app/build/outputs/apk/debug/app-debug.apk` (69,325,116 bytes). |
| `.\gradlew.bat :wear:testDebugUnitTest --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 1 / 1 | 0 / 0 | Test was rerun. XML: `wear/build/test-results/testDebugUnitTest`; HTML: `wear/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :wear:assembleDebug --no-daemon --stacktrace` | 0 | PASS | N/A | N/A | Gradle verified all tasks as up-to-date. APK exists at `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes). |
| `.\gradlew.bat :app:lintDebug --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | N/A | 0 errors | Lint was rerun: 79 warnings and 1 informational issue. Reports: `app/build/reports/lint-results-debug.html` and `.xml`. |
| `.\gradlew.bat :app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | Compilation only | N/A | Kotlin compilation was rerun. `AppDatabaseV2BaselineTest.class` was generated under `app/build/intermediates/built_in_kotlinc/debugAndroidTest/compileDebugAndroidTestKotlin/classes`. No instrumentation test was executed. |
| `Get-FileHash app\schemas\io.github.yuninggu.evolune.data.AppDatabase\2.json -Algorithm SHA256` | 0 | PASS (canonical schema content) | N/A | N/A | Raw Windows bytes returned `C4770838...EC4DA` because the checkout uses CRLF; LF-normalized canonical content is `B8DA54ED...E5DA` and the schema has no Git diff. |

Key warnings were the existing Android SDK XML parser version mismatch, experimental `android.overridePathCheck` and `android.disallowKotlinSourceSets` settings, and existing Kotlin/API deprecations. No warning was automatically fixed in this batch.

## 7. Claude review

The independent review in `reviews/PHASE_1_BATCH_2_REVIEW.md` reached `APPROVE WITH P2` with P0 = 0 and P1 = 0. The following three P2 findings are explicit Batch 3 inputs and were not changed in this batch:

1. Add an explicit mapper between `core.model.ExtraKey` and `pk.DoseEvent.ExtraKey`, including all six values and round-trip tests.
2. Validate `Instant.toEpochMilli()` range at the Entity mapper boundary and return an explicit error for out-of-range values.
3. Treat the temporary `pk.Route` and `pk.Ester` reuse as a transitional dependency; package separation remains future work.

The review independently confirmed the UUIDv5 algorithm, namespace byte order, fixed vector, time conversion boundaries, DST behavior, synthetic-only fixtures, and absence of production-path integration. None of these P2 findings blocks Batch 2.

## 8. Architecture boundaries

- The new core files have no Android, Room, Compose, Wear, Health Connect, Glance, WorkManager, Hilt, SQLCipher, or cloud-sync dependency.
- No Entity, DAO, Repository, JSON format, PK parameter, PK implementation, Wear code, or production path was modified.
- No runtime path consumes the new domain types.
- No production business behavior changed.
- Tests use synthetic constants only and contain no real, de-identified-from-real, or real-derived health data.

## 9. Database and schema

- `AppDatabase` remains version 2 with `exportSchema = true`.
- The source and schema scan found no `MIGRATION_2_3`, `version = 3`, or `ALTER TABLE` implementation. A recursive scan including generated androidTest dex/APK files produced two generic dependency-string matches for `ALTER TABLE`; these are build artifacts, not migration code, schema content, or production source.
- `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/2.json` has no Git difference.
- Room identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- The LF-normalized schema SHA-256 remains the expected `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
- The raw `Get-FileHash` command returned `C4770838B9D6E78A06E796418D4CEF6292F3090E40B77425F769360E0CEEC4DA` because the checked-out file contains CRLF line endings. The LF-normalized canonical schema content returns the expected SHA-256, and the project review accepts this as a non-blocking working-tree representation issue. No database content or schema contract changed.

## 10. Instrumentation status

- `:app:compileDebugAndroidTestKotlin` completed successfully.
- No device or emulator execution was performed in this Batch 2 validation.
- Instrumentation tests are therefore not claimed as passed.
- Batch 1 device execution remains pending, but it is not a Batch 2 hard gate under the reviewed scope.

## 11. Known transitional risks

- `core.model` temporarily depends on PK `Route` and `Ester`; these dependencies must not become the final architecture boundary.
- Domain `ExtraKey` and PK `ExtraKey` require an explicit, independently tested mapper.
- Repository contract and mappers remain for Batch 3.
- The schema baseline currently has a platform line-ending hash discrepancy: semantic/Git content is unchanged, while raw Windows bytes do not match the required LF-byte hash.

## 12. Batch decision

Batch 2 passed.

All runnable Gradle verification tasks passed, the Room identity hash is unchanged, the expected canonical schema SHA-256 matches, and the schema has no Git difference. The raw Windows line-ending representation is recorded as a non-blocking environment note.

Batch 3 prerequisites are satisfied. Batch 3 may establish only the Repository contract and mappers. It must not directly begin the Room v3 schema migration.
