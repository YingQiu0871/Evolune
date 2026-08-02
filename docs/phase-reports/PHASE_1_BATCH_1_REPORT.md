# Evolune Phase 1 Batch 1 Report

**Batch**: Room schema and migration test baseline

**Date**: 2026-08-01

**Worktree**: `D:\Evolune-phase1`

**Branch**: `phase1/batch1-room-baseline`

**Initial result**: Not passed. The Room v2 schema baseline was generated successfully, but the committed baseline is missing `@mipmap/ic_launcher_round`. This unrelated resource error prevents all app resource-linking tasks, including app tests, lint, APK assembly, PK regression execution, and androidTest compilation.

## 1. Modified Files

| File | Purpose |
|---|---|
| `app/build.gradle.kts` | Configure KSP Room schema output, expose schemas as androidTest assets, and add Room testing dependency |
| `gradle/libs.versions.toml` | Add `androidx.room:room-testing` using the existing Room version |
| `app/src/main/java/io/github/yuninggu/evolune/data/AppDatabase.kt` | Change only `exportSchema` from `false` to `true`; database version remains 2 |
| `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/2.json` | Room/KSP-generated v2 schema; not manually edited |
| `app/src/androidTest/java/io/github/yuninggu/evolune/data/AppDatabaseV2BaselineTest.kt` | Synthetic v2 schema and close/reopen baseline tests |
| `docs/phase-reports/PHASE_1_BATCH_1_REPORT.md` | This report |

No Entity, DAO, Repository, JSON, PK, UI, ViewModel, Reminder, Widget, Wear, database version, table, column, or index was modified.

An ignored local `app/debugkeystore.jks` was generated with the repository CI command so the Wear APK could be assembled. It is not a Git change and must not be committed.

## 2. V2 Schema

- Path: `app/schemas/io.github.yuninggu.evolune.data.AppDatabase/2.json`
- Generator: Room compiler through KSP
- Generation command: `./gradlew :app:kspDebugKotlin`
- Schema format version: 1
- Database version: 2
- Identity hash: `a8036e3f5ed6bb42d0e7289ac84039f3`
- File SHA-256: `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`

The generated schema contains exactly two tables:

- `dose_events`: `id TEXT` primary key, `route TEXT`, `timeH REAL`, `doseMG REAL`, `ester TEXT`, `extras TEXT`.
- `medication_plans`: `id TEXT` primary key plus the existing 11 plan columns.

All 18 columns are `NOT NULL`. The current v2 schema has no nullable columns, no SQL default values, no auto-generated primary keys, no explicit indices, and no foreign keys. The baseline test records these absences explicitly instead of inventing nullable fields or defaults.

## 3. Dependency Versions

- Room runtime/KTX/compiler: `2.8.4`
- Room testing: `2.8.4`
- KSP plugin: `2.3.6`
- Android Gradle Plugin: `9.0.1`
- Gradle wrapper: `9.2.1`
- Test runner dependency: AndroidX JUnit `1.3.0`

## 4. Added Tests

`AppDatabaseV2BaselineTest` contains two instrumentation tests and uses only constants defined in the test source. It does not use real, de-identified, or real-derived health data.

1. `generatedV2SchemaMatchesCurrentContract`
   - Creates a v2 database from the generated schema with `MigrationTestHelper`.
   - Checks table names, ordered column contracts, affinities, UUID TEXT primary keys, `NOT NULL`, absent SQL defaults, absent explicit indices, and identity hash.
2. `syntheticV2FixtureSurvivesCloseAndReopenWithoutPrecisionLoss`
   - Inserts five synthetic events and two synthetic plans.
   - Covers integer and fractional `timeH`, epoch zero, distant history, distant future, UUID primary keys, multiple rows, enabled/disabled plans, empty/non-empty JSON fields, and explicit boundary values.
   - Closes the database, reopens it through Room, and compares all stored values.
   - Compares every `timeH` Double with zero tolerance to detect precision changes.

The tests could not be compiled or executed because app resource linking fails before the androidTest Kotlin compile task.

## 5. Commands and Results

| Command | Result | Actual outcome |
|---|---|---|
| `git status --short` before work | PASS | No repository changes; only global Git ignore permission warnings |
| `./gradlew --version` | PASS | Gradle 9.2.1 launched with Java 17 |
| `./gradlew :app:kspDebugKotlin` | PASS | Generated Room v2 schema |
| `./gradlew :app:dependencies --configuration debugAndroidTestRuntimeClasspath` | PASS | Resolved `androidx.room:room-testing:2.8.4` |
| `git diff --check` | PASS | No whitespace errors; CRLF conversion warnings only |
| `./gradlew :app:testDebugUnitTest` | FAIL | `:app:processDebugResources` cannot find `mipmap/ic_launcher_round`; no unit tests executed |
| `./gradlew :app:assembleDebug` | FAIL | Same missing `mipmap/ic_launcher_round` resource |
| `./gradlew :wear:testDebugUnitTest` | PASS | Wear unit tests executed successfully |
| `./gradlew :wear:assembleDebug` | PASS after local prerequisite | Initial run lacked ignored debug keystore; rerun passed after generating it with the CI command |
| `./gradlew :app:lintDebug` | FAIL | Same missing `mipmap/ic_launcher_round` resource; lint did not complete |
| `./gradlew :app:compileDebugAndroidTestKotlin` | FAIL | Same missing resource before migration test Kotlin compilation |
| `./gradlew :app:testDebugUnitTest --tests 'io.github.yuninggu.evolune.pk.*'` | FAIL | Same missing resource; PK tests did not execute |
| `adb devices` | PASS | No connected devices or emulators listed |
| `./gradlew :app:connectedDebugAndroidTest` | NOT RUN | No device available, and the test APK cannot currently compile |

## 6. Instrumentation Status

Instrumentation tests were neither executed nor successfully compiled. The attempt stopped at `:app:processDebugResources` before `:app:compileDebugAndroidTestKotlin`. This report does not claim device or instrumentation test success.

## 7. Known Warnings and Blockers

1. **Blocking baseline defect**: `app/src/main/AndroidManifest.xml` references `@mipmap/ic_launcher_round`, but that resource is absent from the committed worktree.
2. KSP reported that the installed SDK tools understand SDK XML through version 3 while an SDK XML version 4 file is present.
3. Gradle reports experimental `android.overridePathCheck=true` and `android.disallowKotlinSourceSets=false` settings.
4. Existing app and Wear source produces deprecation/compiler warnings unrelated to Batch 1.
5. Git reports LF-to-CRLF conversion warnings for modified text files.
6. Git cannot read `C:\Users\1\.config\git\ignore`; repository-local status still works.

The missing launcher resource was not repaired because Batch 1 explicitly forbids UI/resource changes and prohibits copying uncommitted files from the original worktree.

## 8. Behavior and Scope

- Database structure remains v2 and is unchanged.
- `timeH` remains SQLite `REAL` and Kotlin `Double`; its meaning is unchanged.
- Existing tables, columns, primary keys, defaults, indices, Entity, DAO, Repository, JSON, and PK behavior are unchanged.
- No real or real-derived health database fixture was used.
- No Phase 2 model, v3 migration, Tracked Date, new Wear protocol, Health Connect, Glance, WorkManager, Hilt, SQLCipher, or cloud sync was introduced.

## 9. Initial Batch Decision

**Batch 1 passed**: No.

The schema export portion is complete, but the required app verification and migration test compilation/execution are blocked by a pre-existing committed resource defect. Batch 1 cannot be accepted until that defect is resolved in a separately authorized baseline fix and the complete validation matrix is rerun.

**Batch 2 prerequisites satisfied**: No. Do not start Batch 2.

## Baseline blocker resolution

The initial validation failure above remains part of the Batch 1 history. It was caused by `app/src/main/AndroidManifest.xml` referencing the missing `@mipmap/ic_launcher_round` resource.

- Independent fix commit: `ce41c4bbc0faf1b1484aa371bb86bf700ea2a04e` (`fix: restore valid launcher round icon reference`).
- Fix: changed `android:roundIcon` from the missing `@mipmap/ic_launcher_round` to the existing `@mipmap/ic_launcher`.
- Scope: one resource reference line in the phone manifest.
- No launcher image, adaptive icon, monochrome icon, or other brand resource was added or replaced.
- The fix did not modify the database, Room schema, application data, or business behavior.

## Revalidation results

The full Batch 1 validation matrix was rerun after the independent baseline fix and the instrumentation test compile correction.

| Command | Result | Actual outcome |
|---|---|---|
| `git diff --check` | PASS | Exit code 0; no whitespace errors before or after validation |
| `./gradlew :app:testDebugUnitTest --rerun-tasks` | PASS | 12 suites, 88 tests, 0 failures, 0 skipped; all 26 actionable tasks executed |
| `./gradlew :app:assembleDebug` | PASS | `app/build/outputs/apk/debug/app-debug.apk` exists (69,292,293 bytes); 36 tasks validated as up-to-date |
| `./gradlew :wear:testDebugUnitTest --rerun-tasks` | PASS | 1 suite, 1 test, 0 failures, 0 skipped; all 24 actionable tasks executed |
| `./gradlew :wear:assembleDebug` | PASS | `wear/build/outputs/apk/debug/wear-debug.apk` exists (14,565,775 bytes); 33 tasks validated as up-to-date |
| `./gradlew :app:lintDebug --rerun-tasks` | PASS | 0 errors, 79 warnings, 1 informational issue; all 28 actionable tasks executed |
| `./gradlew :app:testDebugUnitTest --tests "io.github.yuninggu.evolune.pk.*" --rerun-tasks` | PASS | 5 suites, 49 tests, 0 failures, 0 skipped; all 26 actionable tasks executed |
| `./gradlew :app:compileDebugAndroidTestKotlin --rerun-tasks` | PASS | `AppDatabaseV2BaselineTest` compiled successfully; all 26 actionable tasks executed |
| `adb devices` | PASS | ADB responded successfully; no device or emulator was listed with status `device` |
| `./gradlew :app:connectedDebugAndroidTest` | NOT RUN | No eligible device or emulator was connected |

The repeated warnings were non-blocking: the installed SDK parser understands SDK XML through version 3 while an SDK XML version 4 file is present; two Android Gradle settings are experimental; and existing app and Wear sources produce deprecation/compiler warnings. No warning was automatically fixed in this batch.

The broad forbidden-content scan matched the string `ALTER TABLE` only inside generated androidTest DEX/APK dependency content. A follow-up scan excluding `app/build` found no `MIGRATION_2_3`, `ALTER TABLE`, or `version = 3` in project source or configuration. `AppDatabase` remains version 2 with `exportSchema = true`.

## Instrumentation status

- `AppDatabaseV2BaselineTest` compiled successfully as Android instrumentation test code.
- Its two tests remain `generatedV2SchemaMatchesCurrentContract` and `syntheticV2FixtureSurvivesCloseAndReopenWithoutPrecisionLoss`.
- No device or emulator was available, so neither test was actually executed through `connectedDebugAndroidTest`.
- This report does not claim that the instrumentation tests passed on a device.

## Final batch decision

**Batch 1 engineering baseline passed; device execution remains pending.**

All tasks that were runnable in the available environment passed. The v2 schema is Room-generated, the database remains version 2, existing phone and Wear tests pass, the PK regression baseline passes, androidTest Kotlin compiles, and no production behavior changed.

**Batch 2 prerequisites satisfied**: Yes. Batch 2 may begin after this Batch 1 work is reviewed and committed.

Device execution is not a hard gate for entering Batch 2. The Batch 1 acceptance rule in `docs/PHASE_1_DESIGN.md` section 20 requires the database to remain v2, Room-generated schema, passing existing tests, a pre-migration PK fixture, and no production behavior change; those conditions are satisfied. However, successful device execution of the relevant instrumentation migration tests remains required before final Phase 1 acceptance under the section 20 final validation criteria.
