# Phase 1 Batch 8B Report

## 1. Status

Batch 8B implementation complete pending independent review.

## 2. Baseline

| Item | Value |
|---|---|
| Batch 8A implementation | `adb655d3b081ce843298e35b06e4690ff82a5a10` |
| Batch 8A review | `a49914860fd9afc61b0f689f1b01c1af78084c8b` |
| Batch 8A tag | `phase-1-batch-8a` -> `a49914860fd9afc61b0f689f1b01c1af78084c8b` |
| Batch 8A integration merge | `19e1e94f002a1651833113e85b8065d13268adfa` |
| Batch 8B branch | `phase1/batch8b-strict-migration` |
| Batch 8B worktree | `D:\Evolune-batch8b` |

## 3. Scope

Batch 8B implements strict v2-to-v3 migration preflight, atomic failure evidence,
postcondition integrity checks, and production Repository readability evidence.
It does not change schema, Room version, Domain, Repository, mapper, converter,
Gradle, Manifest, UI, JSON, PK, Widget, or Wear semantics.

## 4. Original Release P1

Before Batch 8B, `MIGRATION_2_3` validated IDs, event `timeH`, and plan
`timeOfDay`, but a SQLite-valid row could still migrate and later be rejected by
production converters, mappers, or Repositories. The sealed 8A matrix identified
13 such Batch 8B rejection cases.

## 5. Production Changes

- `AppDatabaseMigrations.kt`: reads and validates all 18 v2 fields before any
  data mutation; enforces storage classes, canonical IDs and Booleans; adds
  `integrity_check` to existing postconditions.
- `LegacyAggregatePreflight.kt`: new migration-owned adapter that executes the
  existing production converter and mapper acceptance language.
- `LegacyMigrationError.kt`: adds typed persisted-value failure categories.
- `LegacyMigrationException.kt`: replaces raw row identifiers with deterministic
  16-hex-character SHA-256 fingerprints.

No production fault switch or alternate migration path was added.

## 6. Complete Preflight Architecture

The locked sequence remains:

1. Apply additive v3 DDL inside Room's outer upgrade transaction.
2. Read and validate the complete `dose_events` dataset.
3. Read and validate the complete `medication_plans` dataset and derive slots in memory.
4. Only after all rows pass, update event timestamps and insert slot rows.
5. Validate row content, slot content, `integrity_check`, and `foreign_key_check`.

There is no row-by-row validate-then-write loop, explicit transaction ownership,
silent fallback, repair, clock, timezone guess, or random replacement.

## 7. DoseEvent Validation

Preflight covers all six v2 event fields: canonical UUID and TEXT storage,
explicit route, numeric `timeH`, exact legacy-to-epoch conversion, numeric
`doseMG`, explicit ester, extras JSON grammar, and all recognized extras keys.
The mapper oracle also proves the locked v3 defaults form a readable Domain event:
`zoneId/localDate/slotId=null`, `source=LEGACY`, `status=RECORDED`, `revision=1`.

## 8. MedicationPlan Validation

Preflight covers all twelve v2 plan fields: canonical UUID, text name, explicit
route/ester/schedule, numeric dose, minute-precision ordered `timeOfDay`,
`daysOfWeek` JSON and `1..7` values, `intervalDays` in the Domain-accepted range,
canonical Boolean `0/1`, extras JSON and keys, and integer `createdAt`.

Derived slots are validated as an aggregate against the existing production
mapper, including plan ownership, continuous positions, canonical local times,
deterministic UUIDv5 IDs, duplicate preservation, ordering, and consistency with
the legacy time list.

## 9. Converter-Backed Validation

The migration calls the existing `Converters` for extras, time lists, and day
sets, then calls the existing production mappers. Malformed JSON is classified as
`CONVERTER_REJECTED`; unknown enums, extras keys, invalid days, invalid interval,
or aggregate mismatch are classified as `MAPPER_REJECTED`. Accepted persisted
language is therefore not reimplemented as a divergent migration-only parser.

## 10. Cross-Field Invariant Validation

`MedicationPlanAggregateEntity.toDomainMedicationPlan()` is the plan acceptance
oracle. It checks the relationship between the original time list and derived
slots as well as Domain construction. `DoseEventEntity.toV3DomainDoseEvent()`
checks exact `timeH`/`occurredAtEpochMillis` consistency and Domain construction.

## 11. Failure Model

Failures identify table, persisted field/reason category, operation, and an
optional row fingerprint. Categories distinguish invalid storage class,
noncanonical ID, converter rejection, mapper rejection, and noncanonical Boolean.
Exceptions propagate to Room's outer upgrade transaction; no invalid row is
dropped, coerced, defaulted, repaired, or converted to success.

## 12. Privacy-Safe Diagnostics

Row identifiers are SHA-256 hashed and truncated to 16 lowercase hex characters.
Exception text contains no raw UUID, medication name, dose, extras payload, or
other health payload. A focused JVM test asserts the complete message shape and
that the raw identifier is absent.

## 13. Preflight-Before-Mutation Proof

The production call order is `DDL -> preflightEvents -> preflightPlans ->
backfillEvents -> insertSlots -> validateMigration`. A connected fixture with
valid rows followed by a late invalid plan proves the entire dataset is rejected
before any event update or slot insert. Reopening through a raw v2 helper shows
`user_version=2`, exact original rows, exact v2 columns, and no slot table.

## 14. Mutation Atomicity

An androidTest-only dynamic proxy injects deterministic failures at event UPDATE,
slot INSERT, and postcondition `integrity_check`. Every case rolls back to exact
v2 rows and structure with `user_version=2`. The seam wraps `MIGRATION_2_3` only
inside instrumentation; production code contains no reachable fault switch.

## 15. Valid Fixture Preservation

The valid matrix preserves IDs, counts, route, ester, dose, extras, legacy timing,
plan schedule fields, enabled state, creation time, slot order, duplicate times,
and UUIDv5 slot identity. Event metadata uses only the locked v3 defaults. The
large fixture includes 71 events, 11 plans, and 13 derived slots.

## 16. Invalid Fixture Rejection

All malformed IDs/times and all 13 former Batch 8B gaps now fail migration and
restore exact v2 state. There is no post-migration Repository failure used as the
rejection mechanism.

## 17. 8A CURRENT_GAP Disposition

| 8A case | Before 8B | Batch 8B disposition |
|---|---|---|
| unknown event route | Migrated, mapper rejected | Preflight rejects, exact rollback |
| unknown event ester | Migrated, mapper rejected | Preflight rejects, exact rollback |
| unknown event extra key | Migrated, mapper rejected | Preflight rejects, exact rollback |
| malformed event extras | Migrated, converter rejected | Preflight rejects, exact rollback |
| unknown plan route | Migrated, mapper rejected | Preflight rejects, exact rollback |
| unknown plan ester | Migrated, mapper rejected | Preflight rejects, exact rollback |
| unknown plan schedule | Migrated, mapper rejected | Preflight rejects, exact rollback |
| invalid plan day | Migrated, mapper rejected | Preflight rejects, exact rollback |
| zero plan interval | Migrated, Domain rejected | Preflight rejects, exact rollback |
| unknown plan extra key | Migrated, mapper rejected | Preflight rejects, exact rollback |
| malformed plan days | Migrated, converter rejected | Preflight rejects, exact rollback |
| malformed plan extras | Migrated, converter rejected | Preflight rejects, exact rollback |
| noncanonical enabled integer | Migrated with coercion | Preflight rejects, exact rollback |

The separate 8A atomicity-harness gap is also addressed through the test-only
UPDATE, INSERT, and postcondition failure proxy.

## 18. Repository Readability

The valid connected matrix opens the upgraded database with production
`AppDatabase`, then reads every event and plan by ID through
`RoomDoseEventRepository` and `RoomMedicationPlanRepository`. It compares every
fixture ID and count and verifies all derived slots. This exercises
Room/DAO/Entity/converter/mapper/Repository/Domain rather than raw SQL alone.

## 19. Semantic Boundary

Git path audit shows zero changes to Repository implementations/contracts,
production mappers, `Converters`, Domain, Entity, DAO, `AppDatabase`, schema,
Gradle, and Manifest. Migration was adapted to the sealed production language;
the language was not weakened to accept corrupt data.

## 20. Legacy Time Behavior

The existing `LegacyTimeAdapter` remains authoritative. Positive, zero, negative,
millisecond, old, boundary, and duplicate times retain exact behavior. Invalid or
nonrepresentable values fail. No current date, system timezone, rounding repair,
or clock is introduced.

## 21. Schema Identity

| Schema | Version | Identity hash | Canonical Git blob SHA-256 |
|---|---:|---|---|
| v2 | 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` |
| v3 | 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` |

KSP regenerated schema output without tracked schema changes. Room remains v3;
no version 4 or new migration path exists.

## 22. Destructive Migration Audit

Search across production and tests found zero
`fallbackToDestructiveMigration*` calls. No second database or destructive
fallback was added.

## 23. Focused 8B Tests

| Suite | Environment | Tests | Result |
|---|---|---:|---|
| `LegacyAggregatePreflightTest` | JVM | 5 | 5 pass |
| `AppDatabaseMigrationContractTest` | API 33 phone | 9 | 9 pass |
| `AppDatabaseMigrationContractTest` | API 35 phone | 9 | 9 pass |

The connected suite covers all former gaps, complete-dataset preflight,
Repository readability, exact rollback, and three mutation/postcondition faults.

## 24. 8A Contract Regression

`MigrationPersistenceContractTest`: 1 suite / 6 tests / 0 failures / 0 errors /
0 skipped. The sealed expected contract was not changed.

## 25. Existing Migration Regression

`AppDatabaseMigrationTest`, `AppDatabaseMigrationMatrixTest`, and
`AppDatabaseV2BaselineTest`: 42/42 pass on API 33 and 42/42 pass on API 35.
On API 33 these divide into 18 migration tests, 22 matrix tests, and 2 baseline
tests.

## 26. Repository Connected

`RoomRepositoryTest` on API 33: 23/23 pass. Combined with the API 33 existing
migration selection, the connected regression is 65/65 pass.

## 27. Device Evidence

| Serial | Model | API | Role | Result |
|---|---|---:|---|---|
| `emulator-5554` | `sdk_gphone64_x86_64` | 33 | Phone | 8B 9/9; migration 42/42; Repository 23/23 |
| `emulator-5560` | `sdk_gphone64_x86_64` | 35 | Phone | 8B 9/9; migration 42/42 |

The Wear AVD `emulator-5556` and API 37 foldable `emulator-5558` were excluded
from the required phone evidence.

## 28. Full App JVM

JUnit XML: 51 suites / 417 tests / 0 failures / 0 errors / 0 skipped.

## 29. PK

JUnit XML: 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped.

## 30. Build, Lint, KSP, AndroidTest Compile

- `:app:kspDebugKotlin`: PASS.
- `:app:assembleDebug`: PASS.
- `:app:compileDebugAndroidTestKotlin`: PASS.
- `:app:lintDebug`: PASS, 0 error entries.

Existing compiler deprecation warnings are outside Batch 8B and did not affect
the gates.

## 31. Production Diff Audit

The exact production diff is limited to:

- `app/src/main/java/io/github/yuninggu/evolune/data/migration/AppDatabaseMigrations.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyAggregatePreflight.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyMigrationError.kt`
- `app/src/main/java/io/github/yuninggu/evolune/data/migration/LegacyMigrationException.kt`

Test changes are limited to migration tests and the focused preflight JVM test.

## 32. Batch 8B Findings

Batch 8B implementation findings: **P0/P1/P2 = 0/0/0**.

No schema, contract, mapper, converter, Repository, privacy, or atomicity conflict
was found during implementation and validation.

## 33. Current Room v3 Release Blockers

Historical state entering Batch 8B: **P0/P1/P2 = 0/1/4**.

Technical disposition: Batch 8B implementation evidence indicates the
converter/preflight and Repository-readability P1 is technically addressed,
pending independent review. It is not formally closed in release status before
that review.

The four existing P2 remain out of scope:

1. Private real-database validation has not been authorized or executed.
2. Python 3.12 repair/audit evidence is not sealed.
3. Recovery and downgrade runbook is not sealed.
4. Historical v1 evidence remains limited; v2 is the formal compatibility baseline.

Room v3 remains internal and unreleasable.

## 34. Deferred

Batch 8C device/preserved-upgrade evidence, Batch 8D repair qualification and
private process, and Batch 8E release authorization remain deferred. No real
database was read, copied, or modified. No release was created.

## 35. Decision

Batch 8B implementation complete pending independent review.
