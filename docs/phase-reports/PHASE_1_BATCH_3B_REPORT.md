# Evolune Phase 1 Batch 3B Report

Date: 2026-08-03

## 1. Scope

Phase 1 Batch 3B establishes only the following read-side persistence boundary:

- `DoseEventEntity` Room v2 to `core.model.DoseEvent`.
- `MedicationPlanEntity` Room v2 to `core.model.MedicationPlan`.
- Explicit legacy storage, Domain, and PK `ExtraKey` mappings.
- Explicit legacy and Domain `ScheduleType` mappings.
- Deterministic Scheduled Dose Slot ID v1 generation while reading legacy plan times.
- Structured mapping result and error types.
- Pure JVM mapper tests and persistence-Instant boundary tests.

This batch does not include:

- Domain to Room v2 Entity write mappers.
- Repository implementations or production wiring.
- Entity, DAO, `AppDatabase`, or Room schema changes.
- A v3 migration or `scheduled_dose_slots` table.
- Batch 3C or Batch 4 implementation.

## 2. Added files

### Production

- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MappingResult.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/ExtraKeyMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/DoseEventEntityMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/ScheduleTypeMapper.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapper.kt`

### Tests

- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/ExtraKeyMapperTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/DoseEventEntityMapperTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/ScheduleTypeMapperTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/MedicationPlanEntityMapperTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/data/mapper/PersistenceInstantMapperTest.kt`

## 3. Mapping rules

### DoseEvent

The read mapper preserves `id`, `doseMG`, and all recognized extras values. Route, ester, and all six ExtraKey names use explicit mappings. `timeH` is converted through `LegacyTimeAdapter`, which retains `Math.round` millisecond behavior and returns structured failures for non-finite values and range overflow.

The resulting legacy Domain defaults are:

- `occurredAt`: converted from `timeH`.
- `zoneId = null`.
- `localDate = null`.
- `slotId = null`.
- `source = LEGACY`.
- `status = RECORDED`.
- `revision = 1`.

Unknown route, ester, or ExtraKey inputs return `MappingResult.Failure`. The mapper does not invoke the existing PK-model `DoseEventEntity.toDoseEvent()` function.

The Room Entity exposes extras as `Map<String, Double>`. Unknown typed keys fail explicitly at this mapper boundary. Malformed serialized JSON fails earlier in the existing Room converter before an Entity can be constructed; that converter was intentionally not changed in this batch.

### MedicationPlan

All twelve v2 Entity fields are represented in the Domain result:

- Identity, name, dose, enabled state, interval, and extras values are preserved.
- Route, ester, schedule type, ExtraKey, and day-of-week values use explicit mappings.
- `createdAt` uses `Instant.ofEpochMilli`.
- `timeOfDay` is read in its original list order.
- Each generated slot uses the plan ID and the original zero-based list index.
- Slot IDs use the resolved versioned UUIDv5 Scheduled Dose Slot ID v1 algorithm.
- Duplicate local times remain separate slots because position participates in identity.
- An empty `timeOfDay` list remains an empty `slots` list.
- No sorting, deduplication, renumbering, trimming, default time zone, default Locale, or automatic repair is performed.
- Invalid `intervalDays` is rejected by the Domain invariant and returned as a mapping failure.

## 4. Mapping direction policy

Batch 3B is read-only. It provides only Room v2 Entity to Domain aggregate mapping plus pure enum/key conversions.

There is no generic `core.model.DoseEvent -> DoseEventEntity` mapper and no generic `core.model.MedicationPlan -> MedicationPlanEntity` mapper. Room v2 cannot persist the complete Phase 1 Domain state without loss, so a best-effort write path remains prohibited.

## 5. Claude P2 status

1. **Explicit ExtraKey mapping:** resolved. All six values are mapped explicitly between legacy storage, Domain, and PK types; unknown storage strings fail.
2. **Instant persistence range:** resolved for the future persistence boundary through `instantToEpochMillisForPersistence`. Ordinary values convert exactly; `Instant.MIN` and `Instant.MAX` return `InvalidCreatedAt` rather than clamping, returning zero, or using the current time. This helper does not create a v2 Entity write mapper.
3. **Route/Ester transitional dependency:** remains. `core.model` continues to use `pk.Route` and `pk.Ester` as designed for this phase.

The final code audit has P0 = 0, P1 = 0, and one remaining non-blocking P2 architectural dependency.

During the audit, one test-diagnostic P1 was found and resolved before final validation: combined loop-based cases were split into independently reported JUnit tests. No production mapper file was changed by this correction. The mapper suite increased from 36 to 43 actual tests.

## 6. Validation results

All commands ran sequentially with `JAVA_HOME=C:\Program Files\kedou\jre`.

| Command | Exit code | Result | Suites / tests | Failures / errors / skipped | Artifact or note |
|---|---:|---|---:|---:|---|
| `git diff --check` | 0 | PASS | N/A | N/A | No whitespace errors |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.data.mapper.*" --rerun-tasks` | 0 | PASS | 5 / 43 | 0 / 0 / 0 | Actual JUnit XML count |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks` | 0 | PASS | 5 / 47 | 0 / 0 / 0 | Actual JUnit XML count |
| `:app:testDebugUnitTest --rerun-tasks` | 0 | PASS | 22 / 178 | 0 / 0 / 0 | `app/build/reports/tests/testDebugUnitTest/index.html` |
| `:app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | 0 | PASS | 5 / 49 | 0 / 0 / 0 | PK regression unchanged |
| `:app:assembleDebug` | 0 | PASS | N/A | N/A | `app/build/outputs/apk/debug/app-debug.apk`, 69,390,640 bytes |
| `:wear:testDebugUnitTest --rerun-tasks` | 0 | PASS | 1 / 1 | 0 / 0 / 0 | Actual JUnit XML count |
| `:wear:assembleDebug` | 0 | PASS | N/A | N/A | `wear/build/outputs/apk/debug/wear-debug.apk`, 14,565,775 bytes |
| `:app:lintDebug --rerun-tasks` | 0 | PASS | N/A | 0 errors / 79 warnings | `app/build/reports/lint-results-debug.html` |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | 0 | PASS | Compilation only | Tests not executed | Instrumentation Kotlin compiled successfully |

`UP-TO-DATE` build tasks are treated as successful Gradle validation, not as missing validation. Existing compiler deprecations and the Android SDK XML version warning remain non-blocking environment/project warnings and were not modified in this batch.

## 7. Architecture boundaries

- No Repository implementation or production composition-root wiring was added.
- No existing Entity, DAO, Repository, AppDatabase, schema, Gradle, JSON, PK algorithm, ViewModel, UI, Reminder, Widget, or Wear source file changed.
- Mapper production files do not use Android Context, Room APIs, Cursor, system-default time zone, default Locale, default charset, random UUIDs, or enum declaration order.
- No Domain-to-v2-Entity generic write path exists.
- All test fixtures use synthetic UUIDs and synthetic values. No real or real-derived health data is present.
- Tracked Date, Health Connect, Glance, WorkManager, cloud synchronization, Batch 3C, and Batch 4 remain outside this batch.

## 8. Database/schema

- `AppDatabase` remains version 2.
- `exportSchema` remains `true`.
- Identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Canonical committed schema SHA-256 remains `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
- No `MIGRATION_2_3`, schema `3.json`, `scheduled_dose_slots` table, new column, or new index exists.
- `git diff --exit-code` confirms the tracked schema file is unchanged.

The checked-out Windows working-tree file uses CRLF line endings and therefore has byte SHA-256 `C4770838B9D6E78A06E796418D4CEF6292F3090E40B77425F769360E0CEEC4DA`. The HEAD blob uses LF and produces the required baseline hash above. This is a checkout line-ending representation difference, not a schema content or Git diff change; the schema was not edited to conceal it.

## 9. Instrumentation status

- `AppDatabaseV2BaselineTest` and the remaining androidTest Kotlin sources compiled successfully.
- `adb devices` reported `emulator-5554` in `device` state.
- `connectedDebugAndroidTest` was not requested or run in this batch.
- No instrumentation test execution is claimed as passed; only compilation is recorded.

## 10. Known limitations

- The new mappers are not connected to production runtime paths.
- Generic Room v2 writes remain prohibited because they would lose Phase 1 Domain fields.
- Batch 3C remains deferred until Batch 4 completes the v3 schema, Entity, DAO, and migration safety net.
- `core.model` temporarily depends on `pk.Route` and `pk.Ester`.
- Raw malformed extras JSON is rejected before Entity mapping by the existing Room converter; Batch 3B handles the typed Entity boundary and unknown typed keys only.
- Windows line-ending conversion changes the working-tree schema byte hash while the canonical Git blob and Room identity hash remain unchanged.

## 11. Decision

Batch 3B passed.

Batch 3A and Batch 3B are complete. Batch 3C remains deferred. The next step is an independent Claude read-only review, followed by an intentional Batch 3B submission if that review finds no blocking issue. Only after submission may Batch 4 design and migration preparation begin. Production Repository wiring must not begin directly from this decision.
