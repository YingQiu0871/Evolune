# Phase 1 Batch 8A Report

## 1. Status

Batch 8A implementation complete pending independent review.

## 2. Baseline

| Item | Value |
|---|---|
| Batch 8 design commit | `dc9e4b04aac624e301364bd750a9c2755e5589e5` |
| Batch 8 design review commit | `9a5566243bcf918dbed45fc8913870d8f3c6bbc0` |
| Design tag | `phase-1-batch-8-design-v1` |
| Design tag target | `9a5566243bcf918dbed45fc8913870d8f3c6bbc0` |
| 8A branch | `phase1/batch8a-migration-contract` |
| 8A worktree | `D:\Evolune-batch8a` |

The design tag is annotated with `Phase 1 Batch 8 migration and release-gate design independently reviewed and approved`.

## 3. Scope

Batch 8A adds only a migration contract, adversarial synthetic matrix, raw v2 fixture builder, JVM contract tests, and connected validation harness. It does not change production migration behavior.

Implemented test-only artifacts:

- A declarative `MigrationContractCase` matrix with validity, rejection stage, current outcome, and Batch 8B ownership.
- A deterministic raw v2 SQLite fixture builder that can insert intentionally malformed but SQLite-representable values.
- JVM persisted-field inventory, converter-language, and mapper-readability contracts.
- Connected current-state characterization through `MIGRATION_2_3` and production Room repositories.

## 4. Production Boundary

Production semantic diff: **ZERO**.

`git diff -- app/src/main` is empty. There are no changes to `AppDatabase`, `MIGRATION_2_3`, Entity, DAO, Repository, Domain, TypeConverter, mapper behavior, schema, Gradle, Manifest, version, release configuration, Wear, or Widget production code.

## 5. Current Release P1

**OPEN.** Batch 8A proves that SQLite-valid does not imply converter-, mapper-, or Repository-readable.

Current `MIGRATION_2_3` still preflights only:

- `dose_events.id`
- `dose_events.timeH` storage and conversion
- `medication_plans.id`
- `medication_plans.timeOfDay`

It does not perform complete converter-backed preflight and therefore does not guarantee that every successfully migrated aggregate is readable through the production Repository. Batch 8A does not partially, mostly, or effectively resolve this release blocker.

## 6. Persisted Contract Inventory

### DoseEvent v2 fields

| Field | SQLite form | Production decoder | Allowed legacy values | Invalid representable values | Failure | Preflight | Repository read |
|---|---|---|---|---|---|---|---|
| `id` | TEXT NOT NULL PK | `Converters.toUUID` | Canonical UUID | Malformed UUID | Migration exception | Required | Required |
| `route` | TEXT NOT NULL | explicit route mapper | `INJECTION`, `ORAL`, `SUBLINGUAL`, `GEL`, `PATCH_APPLY`, `PATCH_REMOVE`, `ANTIANDROGEN` | Unknown text | `InvalidRoute` | Required | Required |
| `timeH` | REAL NOT NULL | `LegacyTimeAdapter` | Finite, millisecond-representable hours | Non-numeric storage, infinity, overflow | Migration exception / `InvalidTimeH` | Required | Required |
| `doseMG` | REAL NOT NULL | Room numeric read | Values accepted by the current Domain | Non-numeric storage/coercible values | No dedicated semantic validator | Required | Required |
| `ester` | TEXT NOT NULL | explicit ester mapper | `E2`, `EB`, `EV`, `EC`, `EN` | Unknown text | `InvalidEster` | Required | Required |
| `extras` | TEXT NOT NULL | `Converters.toMap` + `ExtraKeyMapper` | Empty string or JSON map using six explicit keys | Malformed JSON or unknown key | Converter exception / `InvalidExtraKey` | Required | Required |

### MedicationPlan v2 fields

| Field | SQLite form | Production decoder | Allowed legacy values | Invalid representable values | Failure | Preflight | Repository read |
|---|---|---|---|---|---|---|---|
| `id` | TEXT NOT NULL PK | `Converters.toUUID` | Canonical UUID | Malformed UUID | Migration exception | Required | Required |
| `name` | TEXT NOT NULL | Room text read | All text; no new Domain validation | Non-text storage/coercible values | No dedicated semantic validator | Required | Required |
| `route` | TEXT NOT NULL | explicit route mapper | Seven locked route names | Unknown text | `InvalidRoute` | Required | Required |
| `ester` | TEXT NOT NULL | explicit ester mapper | `E2`, `EB`, `EV`, `EC`, `EN` | Unknown text | `InvalidEster` | Required | Required |
| `doseMG` | REAL NOT NULL | Room numeric read | Values accepted by the current Domain | Non-numeric storage/coercible values | No dedicated semantic validator | Required | Required |
| `scheduleType` | TEXT NOT NULL | explicit schedule mapper | `DAILY`, `WEEKLY`, `CUSTOM` | Unknown text | `InvalidScheduleType` | Required | Required |
| `timeOfDay` | TEXT NOT NULL | `Converters.toStringList` + `LegacyPlanTimeParser` | Empty string or JSON string list of minute-precision local times | Malformed JSON, non-string element, invalid/non-minute time | Converter/migration failure | Required | Required |
| `daysOfWeek` | TEXT NOT NULL | `Converters.toIntSet` + plan mapper | Empty string or JSON set using `1..7` | Malformed JSON or value outside `1..7` | Converter exception / `InvalidDayOfWeek` | Required | Required |
| `intervalDays` | INTEGER NOT NULL | Room integer read + Domain init | `1..Int.MAX_VALUE` | Zero or negative | `InvalidPlanInvariant` | Required | Required |
| `isEnabled` | INTEGER NOT NULL | Room Boolean adapter | Canonical `0` or `1` | Other integers | Currently coerced; no rejection | Required | Required |
| `extras` | TEXT NOT NULL | `Converters.toMap` + `ExtraKeyMapper` | Empty string or JSON map using six explicit keys | Malformed JSON or unknown key | Converter exception / `InvalidExtraKey` | Required | Required |
| `createdAt` | INTEGER NOT NULL | `Instant.ofEpochMilli` | Any persisted Long epoch millis | Non-integer storage/coercible values | No dedicated storage-class validator | Required | Required |

### v3 derived/default fields

`scheduled_dose_slots` is derived from `medication_plans.timeOfDay`. Event metadata is not present in v2 and is added by locked v3 defaults: `occurredAtEpochMillis` derived from `timeH`; `zoneId`, `localDate`, and `slotId` remain null; `source=LEGACY`; `status=RECORDED`; `revision=1`.

## 7. Valid v2 Definition

A valid v2 database must have the exact v2 structure and every persisted value must satisfy the production decode and Domain contract. Room opening or SQL copy success alone is insufficient.

Valid data requires canonical IDs, representable legacy times, explicit route/ester/schedule values, decodable converter payloads, valid extras keys, days in `1..7`, `intervalDays >= 1`, canonical Boolean storage, valid minute-precision plan times, and combinations accepted by the production mappers. Empty slots, duplicate local times, and irrelevant schedule fields remain valid and are preserved.

## 8. Invalid/Unmigratable Definition

SQLite-representable values are invalid/unmigratable when production converters, mappers, Domain construction, or Repository reads cannot preserve them as a valid aggregate.

The matrix includes malformed UUIDs, unknown route/ester/schedule values, malformed converter payloads, unknown extras keys, invalid day values, invalid interval values, malformed/non-minute plan times, unrepresentable `timeH`, and noncanonical Boolean storage.

Confirmed `NOT REPRESENTABLE` under the exact v2 schema/Android binding path:

- Null in a v2 `NOT NULL` column.
- NaN bound into the exact `REAL NOT NULL` event time column.
- Duplicate primary-key rows.
- Mutation-stage injected failure without adding a production fault-injection seam.

## 9. Raw Synthetic Fixture Architecture

`RawV2Fixture`, `V2EventRow`, and `V2PlanRow` are androidTest-only. They use deterministic direct SQLite inserts into a database explicitly created as schema version 2 by `MigrationTestHelper`.

The builder:

- Uses synthetic IDs and values only.
- Requires no external fixture file.
- Does not call production Repository or Entity constructors to create corrupt rows.
- Can materialize unknown enums, malformed JSON, invalid day/interval values, and noncanonical Boolean values.
- Has no production dependency inversion and reads no real health data.

## 10. DoseEvent Adversarial Matrix

| Class | Cases | Current result |
|---|---|---|
| Valid | Empty/minimal/multiple rows; all 7 routes; all 5 esters; all 6 extras keys; positive, zero, negative, millisecond, old, boundary, duplicate timestamps, larger deterministic fixture | Migrates and Repository-reads |
| Current preflight rejection | Malformed ID; infinity/overflow/nonrepresentable `timeH`; non-REAL storage in existing regression | Rejects and rolls back |
| Requires 8B mapper preflight | Unknown route; unknown ester; unknown extras key | Migration succeeds, Repository rejects |
| Requires 8B converter preflight | Malformed extras JSON | Migration succeeds, production converter rejects |
| Not representable | Null required field; NaN binding; duplicate PK | SQLite rejects before migration |

For migrated v2 events, `occurredAtEpochMillis` is derived exactly, `zoneId/localDate/slotId` remain null, and no current date, current time, or system zone is invented.

## 11. MedicationPlan Adversarial Matrix

| Class | Cases | Current result |
|---|---|---|
| Valid | Empty/minimal/multiple plans; all route/ester/schedule values; empty and `1..7` days; interval `1` and `Int.MAX_VALUE`; all extras keys; empty, ordered, duplicate, and boundary times; larger deterministic fixture | Migrates and Repository-reads |
| Current preflight rejection | Malformed ID; malformed times JSON; invalid/non-minute local time | Rejects and rolls back |
| Requires 8B mapper preflight | Unknown route/ester/schedule; day outside `1..7`; zero/negative interval; unknown extras key | Migration succeeds, Repository rejects |
| Requires 8B converter preflight | Malformed days JSON; malformed extras JSON | Migration succeeds, production converter rejects |
| Requires 8B storage semantics | `isEnabled=2` | Migration succeeds; Room reads true while raw value remains 2 |

`DAILY`, `WEEKLY`, and `CUSTOM` retain their stored values. Empty slots and duplicate times remain valid. No sorting, deduplication, schedule-field normalization, or repair is permitted.

## 12. Converter-Backed Field Coverage

JVM tests lock the accepted language for:

- UUID: canonical UUID succeeds; malformed UUID throws.
- Map: empty string and valid JSON map succeed; malformed JSON throws.
- String list: empty string and valid JSON string list succeed; malformed JSON throws.
- Int set: empty string and valid JSON integer set succeed; malformed JSON throws.
- Extras: all six explicit keys map; unknown keys produce `InvalidExtraKey`.
- Route, ester, and schedule storage values use explicit mappings and never ordinal conversion.

Null is not legal for these exact v2 `NOT NULL` payload columns.

## 13. Legacy Time Matrix

| Case | Contract |
|---|---|
| Positive | Exact hour-to-millisecond conversion |
| Zero | Legitimate epoch zero preserved |
| Negative historical | Negative epoch millis preserved |
| Millisecond | Millisecond precision round-trips |
| Very old | Representable historical value migrates |
| Positive/negative boundary | Largest tested representable values migrate |
| Infinity/overflow/nonrepresentable | Migration rejects and outer transaction rolls back |

All migrated legacy events keep `zoneId=null` and `localDate=null`. No system timezone, date, or clock is consulted.

## 14. Full Schema 2 to 3 Mapping Contract

All 18 v2 columns have an explicit disposition, plus eight derived/default v3 destinations:

| Source | Destination | Operation | Validator/postcondition |
|---|---|---|---|
| Six `dose_events` v2 columns | Same six v3 columns | Preserve | Value semantics unchanged; complete future preflight required |
| `dose_events.timeH` | `occurredAtEpochMillis` | Derive | Exact `LegacyTimeAdapter` epoch millis |
| None | `zoneId`, `localDate`, `slotId` | Default null | Must remain null for legacy rows |
| None | `source` | Default `LEGACY` | Exact value |
| None | `status` | Default `RECORDED` | Exact value |
| None | `revision` | Default `1` | Exact value |
| Twelve `medication_plans` v2 columns | Same twelve v3 columns | Preserve | Value semantics unchanged; complete future preflight required |
| `medication_plans.timeOfDay` | `scheduled_dose_slots` | Derive | Original order, duplicate preservation, continuous position, deterministic UUIDv5 |

The test-assisted inventory contains 18 persisted field contracts and 26 mapping dispositions. No schema 2 migrated column is omitted.

## 15. Mapper Failure Characterization

The materially distinct `DoseEventEntityMapper` failure surface is:

- Time conversion and precision: `InvalidTimeH` and `InvalidOccurredAtPrecision`.
- Persisted enum text: `InvalidRoute` and `InvalidEster`.
- Revision and Domain construction: `InvalidDoseEventInvariant`.
- Optional metadata syntax: `InvalidZoneId` and `InvalidLocalDate`.
- v3 metadata enums: `InvalidSource` and `InvalidStatus`.
- Cross-field time consistency: `InconsistentEventTime`.

The materially distinct `MedicationPlanEntityMapper` failure surface is:

- Creation timestamp conversion: `InvalidCreatedAt`.
- Interval and aggregate construction: `InvalidPlanInvariant`.
- Legacy-plan and derived-slot time consistency: `InconsistentPlanTimes`.
- Slot ownership, ordering, nested mapping, and forbidden persisted IDs: `InvalidSlotPlan`, `InvalidSlotPosition`, `InvalidSlot`, and `UnexpectedSlotId`.
- Legacy time-list decoding: `InvalidTimeOfDay`.

The new JVM suite directly characterizes the schema-v2-reachable persisted corruption categories for route, ester, extras keys, schedule type, day values, and plan invariants. Existing mapper suites lock the remaining time, metadata, slot, precision, and cross-field failures. The 8A contract has an explicit disposition for every production-reachable persisted corruption category.

The responsibility boundary is deliberate. A schema-v2 raw column or converter-backed payload that can make a migrated aggregate unreadable is a migration-input preflight responsibility. Failures involving v3-only metadata, derived slots, or post-migration cross-field consistency are postcondition and production Repository-readability responsibilities; they are not falsely described as direct schema-v2 columns.

Current full mapper evidence remains 5 suites / 43 tests / 0 failures. Connected raw fixtures map invalid cases to either a `CorruptAggregateException` carrying `MappingError`, or a production converter `SerializationException` before mapping.

## 16. Repository Readability Acceptance Contract

Future release acceptance is:

> After a successful migration, every migrated DoseEvent and MedicationPlan aggregate must be readable through the production Repository.

The valid synthetic matrix enforces this today. Adversarial characterization deliberately proves the current gap: selected SQLite-valid rows migrate successfully but then fail through production Repository conversion/mapping. A green characterization test is evidence of current behavior, not evidence that the future release contract is satisfied.

## 17. Atomicity Acceptance Contract

- **Pre-mutation invalid:** validation occurs after DDL but before any data `UPDATE`/`INSERT`; failure must roll back schema, data, and `user_version` to exact v2 state.
- **Mutation-stage failure:** any failure must roll back the complete outer upgrade transaction.

Existing migration regression proves rollback for current ID/time cases. There is no authorized production fault-injection seam for a synthetic mutation-stage failure. A dedicated mutation-stage harness is required in Batch 8B; Batch 8A does not add a production seam.

## 18. Current MIGRATION_2_3 Characterization

### Currently satisfied

- Exact v2 structure upgrades to validated schema 3.
- Event ID, `timeH` storage/conversion, plan ID, and plan-time parsing are preflighted.
- Invalid values in those fields fail and roll back.
- Event metadata defaults, slot UUIDv5, order, duplicates, FK, cascade, and unique position behavior are preserved.
- Valid synthetic aggregates are readable through production Repositories.

### Currently unsafe/not fully guaranteed

- Converter-backed extras and days payloads are not preflighted.
- Route, ester, and schedule enums are not preflighted.
- Plan interval and day mapper invariants are not preflighted.
- Canonical Boolean storage is not enforced.
- Other persisted storage classes are not comprehensively validated.
- Migration success does not imply Repository readability.

### Future Batch 8B acceptance

Every case marked `requiresBatch8B=true` must become a mandatory production migration rejection or readability gate without silent repair.

## 19. Required Batch 8B Work

Batch 8B must implement complete validation after DDL and before any backfill `UPDATE`/`INSERT`.

DoseEvent preflight must cover ID, route, ester, extras JSON and keys, numeric/storage contract, mapper invariants, exact legacy time conversion, and locked metadata/default postconditions.

MedicationPlan preflight must cover ID, name/storage contract, route, ester, schedule type, time list, days JSON and `1..7`, interval `1..Int.MAX_VALUE`, canonical enabled representation, extras JSON and keys, createdAt storage, plan invariants, and the exact relationship between legacy times and derived slots.

Batch 8B must additionally prove:

1. All invalid SQLite-representable payloads fail before mutation.
2. No value is silently repaired, normalized, sorted, deduplicated, or replaced.
3. Mutation-stage failure atomically restores v2 schema, data, and `user_version`.
4. Every successful migration is fully readable through production Repositories.

## 20. Focused Tests

| Suite | Environment | Suites | Tests | Failures | Errors | Skipped |
|---|---|---:|---:|---:|---:|---:|
| `MigrationPersistenceContractTest` | JVM | 1 | 6 | 0 | 0 | 0 |
| `AppDatabaseMigrationContractTest` | API 33 phone | 1 | 7 | 0 | 0 | 0 |
| `AppDatabaseMigrationContractTest` | API 35 phone | 1 | 7 | 0 | 0 | 0 |

The JVM tests cover persisted inventory, full-field disposition count, converter/storage language, and mapper failures. The connected suite covers declarative case ownership, valid Repository readability, current preflight rollback, mapper-invalid rows, malformed converter payloads, Boolean coercion, and exact-v2 nonrepresentability.

## 21. Existing Migration Regression

`AppDatabaseMigrationTest`, `AppDatabaseMigrationMatrixTest`, and `AppDatabaseV2BaselineTest` were selected together on API 33. The Android runner XML records 1 aggregate suite / 42 tests / 0 failures / 0 errors / 0 skipped.

This is existing migration characterization/regression. It does not resolve the current release P1.

## 22. Connected Contract

| Serial | Model | Device type | API | Suite | Result |
|---|---|---|---:|---|---|
| `emulator-5554` | `sdk_gphone64_x86_64` | Phone AVD | 33 | `AppDatabaseMigrationContractTest` | 7/7 pass, 0 skipped |
| `emulator-5560` | `sdk_gphone64_x86_64` | Phone AVD | 35 | `AppDatabaseMigrationContractTest` | 7/7 pass, 0 skipped |

Neither device is the Wear OS AVD. Evidence from the final rerun is retained under ignored local build paths `app/build/batch8a-final-evidence/contract-api33` and `app/build/batch8a-final-evidence/contract-api35`.

## 23. Repository Connected Regression

`RoomRepositoryTest` ran on `emulator-5554` / API 33: 1 suite / 23 tests / 0 failures / 0 errors / 0 skipped.

This proves current production Repository behavior is healthy for the tested valid state. It does not prove that all adversarial v2 payloads are preflighted by `MIGRATION_2_3`.

## 24. Full App JVM

JUnit XML result: 50 suites / 412 tests / 0 failures / 0 errors / 0 skipped.

Evidence path: `app/build/batch8a-final-evidence/full-app-jvm/testDebugUnitTest`.

## 25. PK Regression

JUnit XML result: 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped.

Evidence path: `app/build/batch8a-final-evidence/pk-jvm/testDebugUnitTest`. Batch 8A changes no PK behavior.

## 26. Build Gates

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin --rerun-tasks --no-daemon` | PASS |
| `:app:assembleDebug --no-daemon` | PASS |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks --no-daemon` | PASS |
| `:app:lintDebug --rerun-tasks --no-daemon` | PASS: 0 errors, 83 warnings, 1 hint |

An initial concurrent invocation caused shared Kotlin/AAPT intermediate-cache collisions. The gates were rerun serially and passed. No source change was made in response.

## 27. Schema Identity

KSP regenerated schema metadata without a tracked diff.

| Schema | Identity hash | Canonical Git blob SHA-256 | Status |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | UNCHANGED |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | UNCHANGED |

Hashes were independently recomputed from raw `git cat-file blob` output during Batch 8A closure.

## 28. Destructive Migration Audit

Production search for `fallbackToDestructiveMigration`, `fallbackToDestructiveMigrationFrom`, `fallbackToDestructiveMigrationOnDowngrade`, and equivalent destructive migration text returned **NONE**.

## 29. Privacy/Data Boundary

All fixtures are deterministic and synthetic. No real database was searched, copied, opened, materialized, or migrated. No medication or health data was read. No preserved APK upgrade, repair tool, private database validation, or release action was performed.

The local build used the existing ignored debug keystore copied from `D:\Evolune\app\debugkeystore.jks`. Source and temporary copy both had SHA-256 `D5E4FC1E641729E69AE6579C45A35CED63721A9853AAEE0133E5B18AA420980B`; the temporary worktree copy is removed before final audit.

## 30. Findings

Batch 8A implementation findings: **P0/P1/P2 = 0/0/0**.

The contract, matrix, fixture builder, and validation harness compile and pass their authorized gates. No new design, schema, ADR, production, privacy, or test-scope defect was found.

## 31. Current Release Blockers

Current Room v3 release blockers: **P0/P1/P2 = 0/1/4**.

P1 remains open: complete converter-backed preflight and successful-migration production Repository readability are not implemented in `MIGRATION_2_3`.

Retained P2 items:

1. Private real-database validation pending.
2. Python 3.12 repair-tool evidence pending.
3. Recovery/downgrade runbook pending.
4. Historical Room v1 evidence limited.

Room v3 remains unreleasable.

## 32. Deferred

- Batch 8B production strict-preflight implementation and review.
- Batch 8C preserved APK/device upgrade validation.
- Batch 8D private real-database validation.
- Batch 8E repair/recovery/release gates.
- Repair tooling, actual release, and post-v1 product features.

No deferred work is authorized by this report.

## 33. Decision

Batch 8A implementation complete pending independent review.
