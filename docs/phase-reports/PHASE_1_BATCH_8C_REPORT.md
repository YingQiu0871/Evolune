# Phase 1 Batch 8C Report

## 1. Status

Batch 8C validation complete pending independent review.

## 2. Baseline

| Item | Value |
|---|---|
| Batch 8B implementation | `d994d550d5236936d87ea71609801ac51953ddb3` |
| Batch 8B review | `6d3e68cf10c3c5b12e3a391c32d52e9a2a8acf87` |
| Batch 8B tag | `phase-1-batch-8b` at the review commit |
| Batch 8B integration merge | `820ea350b1647f5ee00f021348be7c78cc1c078b` |
| Batch 8C branch | `phase1/batch8c-preserved-upgrade` |
| Batch 8C worktree | `D:\Evolune-batch8c` |

The original converter/preflight and Repository-readability release P1 remains formally closed. Room v3 remains internal and unreleasable.

## 3. Scope

Batch 8C validates a real Android in-place package upgrade from a trusted Room v2 application to the current Room v3 application while preserving synthetic application data. It adds one current-version androidTest harness and this report. Production semantic diff is zero.

This batch does not modify migration production code, Repository behavior, mappers, converters, Entity, Domain, Room version, schema, Gradle, Manifest, application ID, signing configuration, UI, Widget, or Wear production code.

## 4. Formal v2 APK Baseline

| Item | Value |
|---|---|
| Commit | `16d8dbf1c7d1ed359b2e8c4e0857759b2dd12c81` |
| Tag | `phase-1-batch-4a1-design-v1` |
| Selection evidence | Direct parent of the commit that introduced Room v3; production `AppDatabase` version is 2 |
| Schema 2 identity | `a8036e3f5ed6bb42d0e7289ac84039f3` |
| Application ID | `io.github.yuninggu.evolune.debug` |
| Version | code `10060`; name `phase-1-batch-4a1-design-v1-debug` |
| Write path | Historical production v2 `DoseEventRepository` and `MedicationPlanRepository` |

The baseline was identified from Git history and inspected before building. It is not a current v3 APK with a manually altered `user_version`.

## 5. Current v3 APK

| Item | Value |
|---|---|
| Commit | `820ea350b1647f5ee00f021348be7c78cc1c078b` |
| Room version | 3 |
| Schema 3 identity | `c5f5e02cb04b048ca28fe96a74d61606` |
| Application ID | `io.github.yuninggu.evolune.debug` |
| Version | code `10060`; name `phase-1-batch-8b-1-g820ea35-debug` |

## 6. APK Provenance And Continuity

| Artifact | SHA-256 |
|---|---|
| Historical v2 app APK | `6075281B0FD4C3C1CB42E270FC36AEBA7CF80BF05346C82DF5F6838EA29AB2CF` |
| Historical v2 synthetic seeder test APK | `6CB3F68BAB0ACADAA16EB3751CF3AB2ED707546EC6CC276CBAA586373B5FED3D` |
| Current v3 app APK | `37382D14B9783C03DD3759DD3D847CA3AC34709A6C33DC47678725258AF9FEA2` |
| Current v3 test APK | `C966BD06FC0391454AE329FF30F2C57C6113C63C7AE4AF619D9A81918EC7F53B` |

Both app APKs have the same application ID, version code, and signing certificate SHA-256 `66dbe8e4de9acc084719d1ce735e3868a9ce3ebc88b0529c706bbc6beb9561e9`. Android accepted `adb install -r` for the current APK. Package `firstInstallTime` remained unchanged while `lastUpdateTime` advanced, proving package replacement rather than uninstall/reinstall.

## 7. Preserved-Upgrade Method

Each mandatory phone scenario used this sequence:

1. Prepare the emulator before the gate.
2. Install the historical v2 APK and its test APK.
3. Run the historical production-Repository seeder.
4. Verify `user_version=2`, schema 2 identity, six events, and three plans.
5. Stop the historical app without clearing its data.
6. Install the current v3 app and test APKs with package-replacement semantics.
7. Launch the current application through its launcher and confirm a live process.
8. Read all expected aggregates through current production Room Repositories.
9. Verify Room v3, schema identity, integrity, foreign keys, row counts, and values.
10. Force-stop, relaunch, and repeat Repository verification.
11. Cold-restart the AVD and repeat the preserved-state test.

After the v2 seed and before final verification, no `adb uninstall`, `pm clear`, emulator data wipe, database deletion, or database-file replacement occurred.

## 8. Synthetic Dataset

The deterministic fixture contains six dose events and three medication plans producing five slots. It covers all representative route/ester/extras categories used by the historical model; positive, zero, negative, old, millisecond, and duplicate event times; `DAILY`, `WEEKLY`, and `CUSTOM`; duplicate plan times; enabled and disabled plans; day sets; interval values; and creation timestamps.

The v2 state was created through historical production v2 Repository write paths. No real medication, health, backup, or user database was read or copied.

## 9. API 33 Preserved Upgrade

| Item | Result |
|---|---|
| Device | `emulator-5554`, `sdk_gphone64_x86_64`, Android 13 / API 33, phone AVD |
| v2 seed | 1/1 pass through production v2 Repositories |
| Pre-upgrade proof | `user_version=2`; schema 2 identity; 6 events; 3 plans |
| In-place install | Current APK accepted with `adb install -r`; install continuity retained |
| First launch | Launcher start and process creation succeeded; no migration crash |
| Post-upgrade proof | `user_version=3`; schema 3 identity; 6 events; 3 plans; 5 slots |
| Repository readability | Every deterministic event and plan matched full expected Domain content |
| Integrity | `integrity_check=ok`; `foreign_key_check` empty |
| Force-stop/reopen | 1/1 pass with preserved counts and content |
| AVD cold restart | 1/1 preserved-state pass |

The final API 33 combined connected regression ran 76 tests with 0 failures and 0 skipped.

## 10. API 35 Preserved Upgrade

| Item | Result |
|---|---|
| Device | `emulator-5560`, `sdk_gphone64_x86_64`, Android 15 / API 35, phone AVD |
| v2 seed | 1/1 pass through production v2 Repositories |
| Pre-upgrade proof | `user_version=2`; schema 2 identity; 6 events; 3 plans |
| In-place install | Current APK accepted with `adb install -r`; install continuity retained |
| First launch | Launcher start and process creation succeeded; no migration crash |
| Post-upgrade proof | `user_version=3`; schema 3 identity; 6 events; 3 plans; 5 slots |
| Repository readability | Every deterministic event and plan matched full expected Domain content |
| Integrity | `integrity_check=ok`; `foreign_key_check` empty |
| Force-stop/reopen | 1/1 pass with preserved counts and content |
| AVD cold restart | 1/1 preserved-state pass |

The final API 35 combined connected regression ran 76 tests with 0 failures and 0 skipped.

## 11. Fresh v3 Control

A separate fresh Room v3 database on API 33 ran 1/1 pass. Current production Repositories inserted and read a synthetic event and plan, and the database reported `user_version=3`. This control is not counted as preserved-upgrade evidence.

## 12. Connected Regression Matrix

The final API 33 and API 35 selections each contained:

| Coverage | Tests per device | Result |
|---|---:|---|
| Batch 8C preserved/fresh current-version harness | 2 | Pass |
| Batch 8B strict migration contract | 9 | Pass |
| Existing migration, matrix, and v2-baseline regression | 42 | Pass |
| Production Room Repository connected regression | 23 | Pass |
| Total | 76 | 76 pass, 0 failures, 0 skipped |

This validates rollback, strict preflight, foreign keys, cascade, unique slot position, slot ordering, duplicate-time preservation, deterministic UUIDv5 slot identity, and complete Repository readability.

## 13. Foldable Regression

`emulator-5558` (`sdk_gphone16k_x86_64`, API 37 foldable phone AVD) ran `FoldableNavigationLayoutTest`, `MedicationPlansScreenTest`, and `MedicationRecordsScreenTest`: 21/21 pass, 0 failures, 0 skipped. No UI code was modified.

## 14. Wear Regression

`emulator-5556` is a Wear OS AVD and was not used as phone evidence. Existing Wear JVM test `WearDashboardTest` ran 1/1 pass, and `:wear:assembleDebug` passed. Batch 8C makes no Wear protocol or production change.

## 15. JVM And Build Gates

| Gate | Result |
|---|---|
| Batch 8A `MigrationPersistenceContractTest` | 6/6 pass |
| Batch 8B `LegacyAggregatePreflightTest` | 5/5 pass |
| Full App JVM | 51 suites / 417 tests / 0 failures / 0 errors / 0 skipped |
| PK regression | 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped |
| `:app:kspDebugKotlin` | Pass |
| `:app:assembleDebug` | Pass |
| `:app:compileDebugAndroidTestKotlin` | Pass |
| `:app:lintDebug` | Pass; 0 errors, 83 existing warnings |
| `:wear:testDebugUnitTest` | 1/1 pass |
| `:wear:assembleDebug` | Pass |

## 16. Schema Integrity

| Schema | Identity hash | Canonical Git-blob SHA-256 | Result |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | Unchanged |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | Unchanged |

KSP regenerated schema output without a tracked schema diff.

## 17. Destructive Fallback And Production Boundary

Search for `fallbackToDestructiveMigration*` returned zero. No production migration, Repository, mapper, converter, Entity, Domain, schema, Gradle, Manifest, application ID, signing, Widget, Wear, or UI file differs from the integrated Batch 8B base.

The only implementation artifact is the test-only `Batch8CPreservedUpgradeTest.kt`. Temporary v2 seeding support remains outside the Batch 8C branch and is not part of the deliverable.

## 18. Privacy And Data Boundary

All identifiers and values are deterministic synthetic fixtures. No real database, medication history, health payload, backup, or user device data was used. APKs and keystores are local validation artifacts and are not release artifacts.

## 19. Batch 8C Findings

Batch 8C findings: **P0/P1/P2 = 0/0/0**.

No package-upgrade, migration, startup, persistence, Repository-readability, signing, or version-continuity defect was found.

## 20. Current Room v3 Release Blockers

The four existing release P2 items remain unchanged and are not automatically closed by Batch 8C:

1. Private real-database validation has not been executed and requires separate authorization.
2. Python 3.12 repair/audit evidence has not been sealed.
3. Recovery and downgrade runbook has not been sealed.
4. Historical v1 evidence remains limited; v2 is the formal baseline.

Current Room v3 release blockers: **P0/P1/P2 = 0/0/4**.

## 21. Deferred

Batch 8D, Batch 8E, any separately authorized private real-database validation, repair execution, Python evidence, final recovery/runbook work, release signing, distribution, and actual release remain out of scope. No release artifact was created.

## 22. Decision

Batch 8C validation complete pending independent review.
