# Evolune Phase 1 Batch 3A Report

Validation date: 2026-08-02 (Asia/Shanghai)

## 1. Batch scope

Batch 3A establishes only disconnected, pure Kotlin domain and contract foundations inside the App module:

- Domain `MedicationPlan`;
- `ScheduleType` with `DAILY`, `WEEKLY`, and `CUSTOM`;
- `DoseEventRepository` contract;
- `MedicationPlanRepository` contract;
- sealed Repository business result types;
- JVM tests for model invariants, interface shapes, and dependency boundaries.

This batch does not include a mapper, Room implementation, production Repository implementation, production-path wiring, Room v3 migration, Entity or DAO change, JSON change, PK change, UI integration, Reminder integration, Widget integration, or Wear integration. Domain writes to Room v2 remain prohibited because v2 cannot preserve the complete Phase 1 event metadata or scheduled-slot identity. Batch 3C remains deferred until Batch 4 completes and verifies the v3 schema, Entity, DAO, and migration.

## 2. Added files

Production files:

- `app/src/main/java/io/github/yuninggu/evolune/core/model/MedicationPlan.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/model/ScheduleType.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/DoseEventRepository.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/MedicationPlanRepository.kt`
- `app/src/main/java/io/github/yuninggu/evolune/core/dataapi/RepositoryResults.kt`

Test files:

- `app/src/test/java/io/github/yuninggu/evolune/core/model/MedicationPlanTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/core/dataapi/RepositoryContractTest.kt`

No existing tracked production or test file was modified.

## 3. MedicationPlan domain

The model contains exactly these fields:

- `id: UUID`;
- `name: String`;
- `route: pk.Route`;
- `ester: pk.Ester`;
- `doseMG: Double`;
- `scheduleType: core.model.ScheduleType`;
- `slots: List<ScheduledDoseSlot>`;
- `daysOfWeek: Set<DayOfWeek>`;
- `intervalDays: Int`;
- `isEnabled: Boolean`;
- `extras: Map<core.model.ExtraKey, Double>`;
- `createdAt: Instant`.

Constructor invariants require `intervalDays >= 1`, every slot plan ID to equal the plan ID, and every slot position to equal its list index. This makes positions zero-based, continuous, and unique. Empty slot lists and duplicate local times remain valid. The model does not sort, renumber, deduplicate, repair, clear, or normalize input fields. It adds no compatibility validation for name, dose, extras, or the persistence range of `Instant`.

The domain stores schedule values without calculating occurrences. `DAILY` retains but later scheduling ignores `daysOfWeek` and `intervalDays`; `WEEKLY` uses `daysOfWeek`, with an empty set representing no occurrence; `CUSTOM` uses `intervalDays` and ignores `daysOfWeek`. Legacy irrelevant values remain unchanged.

## 4. Repository contracts and results

`DoseEventRepository` defines only:

- `observeAll()`;
- `getById(UUID)`;
- `findOccurredBetween(Instant, Instant)` using `[startInclusive, endExclusive)`;
- `getEventsForPk(Instant)`, preserving the current 30-day/20-event selection and branch-specific ordering;
- `insert(DoseEvent)`;
- `update(DoseEvent, expectedRevision)`;
- `delete(UUID)`;
- `deleteAll()` as a maintenance capability.

`MedicationPlanRepository` defines only:

- `observeAll()`;
- `observeEnabled()`;
- `getById(UUID)`;
- `save(MedicationPlan)` for an atomic plan-and-slots aggregate;
- `setEnabled(UUID, Boolean)`;
- `delete(UUID)`;
- `deleteAll()` as a maintenance capability.

The locked no-payload result variants are:

- `InsertResult`: `Inserted`, `Idempotent`, `Conflict`, `Invalid`;
- `UpdateResult`: `Updated`, `NoChange`, `NotFound`, `RevisionConflict`, `Invalid`;
- `DeleteResult`: `Deleted`, `NotFound`;
- `PlanSaveResult`: `Created`, `Updated`, `NoChange`, `Invalid`;
- `PlanUpdateResult`: `Updated`, `NoChange`, `NotFound`, `Invalid`.

Infrastructure failures are not encoded in these business results and remain exceptions for future implementations.

## 5. Architecture boundaries

- `core.dataapi` depends only on `core.model`, Java time/UUID types, `kotlinx.coroutines.flow.Flow`, and the Kotlin standard library.
- Static scanning found no Android, Room, Entity, DAO, Cursor, Context, Compose, Wear, or JSON dependency in the contract files.
- The test fakes only make the compiler verify signatures and domain-only dependencies. They do not implement or claim to prove Room persistence, idempotency, conflict handling, transactions, or ordering behavior.
- Existing `data.DoseEventRepository` and `data.MedicationPlanRepository` remain unchanged.
- No mapper or production composition-root connection was created.
- No real or real-derived health data is present.

## 6. Validation results

| Command | Exit code | Result | Suites / tests | Failures / skipped | Actual execution and output |
| --- | ---: | --- | --- | --- | --- |
| `git diff --check` | 0 | PASS | N/A | N/A | No whitespace errors in tracked differences. The seven added Kotlin files were also checked for trailing whitespace. |
| `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.yuninggu.evolune.core.*" --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 5 / 47 | 0 / 0 | Core tests were rerun. XML: `app/build/test-results/testDebugUnitTest`; HTML: `app/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 17 / 135 | 0 / 0 | All App JVM tests were rerun. XML and HTML report paths are the same as above. |
| `.\gradlew.bat :app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 5 / 49 | 0 / 0 | PK regression tests were rerun without changing parameters or tolerances. |
| `.\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | 0 | PASS | N/A | N/A | Gradle successfully verified the debug build as up-to-date. APK: `app/build/outputs/apk/debug/app-debug.apk` (69,404,290 bytes). |
| `.\gradlew.bat :wear:testDebugUnitTest --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | 1 / 1 | 0 / 0 | Wear test was rerun. XML: `wear/build/test-results/testDebugUnitTest`; HTML: `wear/build/reports/tests/testDebugUnitTest/index.html`. |
| `.\gradlew.bat :wear:assembleDebug --no-daemon --stacktrace` | 0 | PASS | N/A | N/A | Gradle successfully verified the debug build as up-to-date. APK: `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes). |
| `.\gradlew.bat :app:lintDebug --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | N/A | 0 errors | Lint was rerun and reported 79 warnings and 1 hint. Reports: `app/build/reports/lint-results-debug.html` and `.xml`. |
| `.\gradlew.bat :app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon --stacktrace` | 0 | PASS | Compilation only | N/A | Kotlin compilation was rerun. `AppDatabaseV2BaselineTest.class` exists under `app/build/intermediates/built_in_kotlinc/debugAndroidTest/compileDebugAndroidTestKotlin/classes`. No instrumentation test was executed. |

Key warnings were the existing Android SDK XML parser version mismatch, experimental Gradle properties, and existing Kotlin/API deprecations. No warning was automatically fixed in this batch.

## 7. Database and schema status

- `AppDatabase` remains version 2.
- `exportSchema` remains `true`.
- Room schema identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- The LF-normalized canonical schema SHA-256 remains `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
- The Windows working-tree file uses CRLF because `core.autocrlf=true`; its raw byte SHA-256 is `C4770838B9D6E78A06E796418D4CEF6292F3090E40B77425F769360E0CEEC4DA`. This is a line-ending representation difference: the schema has no Git diff and its LF-normalized content matches the locked baseline.
- No `MIGRATION_2_3` or source database version 3 exists.
- No schema `3.json` exists.
- Entity, DAO, current Repository, `AppDatabase`, and schema files have no Git difference.

## 8. Batch decision

Batch 3A passed.

All runnable tests, builds, lint, and androidTest compilation passed. The new files remain disconnected from production runtime paths, and the Room v2 schema semantics remain unchanged. The working-tree CRLF schema byte representation is recorded explicitly and does not represent a schema content change.

The prerequisites for a read-only review are satisfied. After review approval, Batch 3B may add only the designed v2 Entity-to-Domain read mappers and explicit enum/ExtraKey mappings. Batch 3B must not add a generic Domain-to-v2 write mapper, modify the database version or schema, connect a production Repository implementation, or begin Batch 3C.
