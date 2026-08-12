# Phase 1 Batch 8B Independent Review

Date: 2026-08-12
Reviewer: DeepSeek (independent read-only)
Worktree: D:\Evolune-batch8b
Branch: phase1/batch8b-strict-migration

## Executive summary

Decision: **APPROVE WITH P2**

- Batch 8B implementation P0/P1/P2: **0 / 0 / 2**
- Current Room v3 release blockers after review: **0 / 0 / 4**
- Original release P1 (converter/preflight + Repository-readability): **CLOSED**

Summary gates:

- Complete-dataset preflight: PASS
- Preflight-before-mutation: PASS
- DoseEvent validation: PASS
- MedicationPlan validation: PASS
- Converter parity: PASS
- Cross-field invariants: PASS
- All 13 8A CURRENT_GAP cases: PASS
- No silent repair: PASS
- Privacy-safe diagnostics: PASS
- Preflight rollback: PASS
- UPDATE rollback: PASS
- Slot INSERT rollback: PASS
- Postcondition rollback: PASS
- Valid data preservation: PASS
- Repository readability: PASS
- Every-row readability: PASS
- Mapper unchanged: PASS
- Converter unchanged: PASS
- Repository unchanged: PASS
- Domain/Entity unchanged: PASS
- Schema integrity: PASS
- Destructive fallback: ZERO
- 8A contract regression: PASS (6/6)
- API33 (8B 9/9; migration 42/42; repository 23/23): PASS
- API35 (8B 9/9; migration 42/42): PASS
- Full JVM: PASS (51 suites / 417 / 0)
- PK: PASS (5 suites / 49 / 0)
- Build gates: PASS (ksp/assemble/compileAndroidTest/lint 0 errors)
- Batch 8B may be sealed: YES
- Original release P1 formally closed: YES
- Batch 8C may begin after sealing/integration: YES
- Room v3 release authorized: NO
- Actual release: FORBIDDEN

## Git / scope verdict

- Branch `phase1/batch8b-strict-migration`; HEAD `19e1e94` = Batch 8A integration merge; both `phase-1-batch-8a` (a499148) and integration are ancestors (exit 0).
- Staging empty; working tree holds exactly the expected change set: 5 modified (AppDatabaseMigrationTest, AppDatabaseMigrationContractTest, AppDatabaseMigrations, LegacyMigrationError, LegacyMigrationException) + 3 new (LegacyAggregatePreflight, LegacyAggregatePreflightTest, report). `git diff --check` clean.
- Production diff restricted to the 4 migration files. `app/src/main` outside `data/migration/`, `app/schemas`, `wear`, `gradle`, Manifest: ZERO diff.
- The pre-existing stash@{0} belongs to an unrelated older branch (UI sizing); not created by 8B.
- No 8C work, real DB, keystore (temporarily copied for build, removed), APK/log/dump, feature work.

## Preflight architecture verdict

`LegacyAggregatePreflight.kt` executes the **existing production acceptance language**: it decodes persisted payloads through the production `Converters` (`toMap`, `toStringList`, `toIntSet`) and builds the production `DoseEventEntity` / `MedicationPlanEntity` / `MedicationPlanAggregateEntity` then runs the **production mappers** (`toV3DomainDoseEvent()`, `MedicationPlanAggregateEntity.toDomainMedicationPlan()`). No migration-only duplicate rule set exists; the acceptance oracle IS the production read path. Deterministic, clock-free, timezone-free, random-free, no Repository writes, no UI, no external state.

`MIGRATION_2_3.migrate` order (AppDatabaseMigrations.kt:8-17): `applyV3Schema -> preflightEvents -> preflightPlans -> backfillEvents -> insertSlots -> validateMigration`. Both preflight functions read and validate the **entire** dataset into in-memory lists before returning; `backfillEvents` (UPDATE) and `insertSlots` (INSERT) run only after both complete. No row-by-row validate-then-write loop exists.

## Full-field coverage verdict

Independent enumeration from schema 2 JSON: `dose_events` = id, route, timeH, doseMG, ester, extras (6); `medication_plans` = id, name, route, ester, doseMG, scheduleType, timeOfDay, daysOfWeek, intervalDays, isEnabled, extras, createdAt (12). Total 18.

| Field | Preflight check | Owner |
|---|---|---|
| event id | requireText + parseUuid (canonical) | migration |
| event route | requireText + mapper oracle | production mapper |
| event timeH | storage class + LegacyTimeAdapter | migration/adapter |
| event doseMG | requireNumeric (INTEGER/FLOAT) | contract storage |
| event ester | requireText + mapper oracle | production mapper |
| event extras | requireText + toMap + ExtraKeyMapper | production converter/mapper |
| plan id | requireText + parseUuid (canonical) | migration |
| plan name | requireText | contract storage |
| plan route/ester | requireText + mapper oracle | production mapper |
| plan doseMG | requireNumeric | contract storage |
| plan scheduleType | requireText + mapper oracle | production mapper |
| plan timeOfDay | LegacyPlanTimeParser + toStringList + aggregate mapper | production |
| plan daysOfWeek | toIntSet + toDomainDaysOfWeek | production converter/mapper |
| plan intervalDays | requireInt + Domain init (>=1) | production mapper |
| plan isEnabled | requireLong + canonical 0/1 check | migration strict |
| plan extras | toMap + ExtraKeyMapper | production converter/mapper |
| plan createdAt | requireLong (INTEGER) | contract storage |

No materially relevant persisted field lacks ownership. The canonical-UUID and canonical-Boolean checks are the two strictly-added preflight rules; both align with the sealed 8A contract (NONCANONICAL_ID / noncanonical enabled integer were classified INVALID and require 8B).

## DoseEvent verdict

All 6 v2 event fields preflighted. `preflightEvents` (AppDatabaseMigrations.kt:69-128) reads id/route/timeH/doseMG/ester/extras, validates TEXT storage for id/route/ester/extras and numeric storage for timeH/doseMG, rejects non-canonical IDs, computes exact epoch millis via `legacyTimeHToOccurredAtEpochMillis` (== `LegacyTimeAdapter.timeHToEpochMillis`), then requires `LegacyAggregatePreflight.requireReadable(event)` which runs the production `toV3DomainDoseEvent()`. That mapper oracle checks route/ester/extra keys, `timeH`/`occurredAtEpochMillis` consistency, and locked v3 defaults (`zoneId/localDate/slotId=null`, `source=LEGACY`, `status=RECORDED`, `revision=1`). Unknown route/ester -> MAPPER_REJECTED; malformed extras -> CONVERTER_REJECTED; unknown extra key -> MAPPER_REJECTED. No default/drop/substitute/coerce.

## MedicationPlan verdict

All 12 v2 plan fields preflighted. `preflightPlans` (AppDatabaseMigrations.kt:130-250) validates TEXT storage (id/name/route/ester/scheduleType/timeOfDay/daysOfWeek/extras), numeric doseMG, INTEGER intervalDays/isEnabled/createdAt, canonical UUID, canonical Boolean 0/1, and parses timeOfDay through `LegacyPlanTimeParser` (minute precision, string elements, deterministic Slot IDs). The aggregate oracle `MedicationPlanAggregateEntity.toDomainMedicationPlan()` validates route/ester/scheduleType, days 1..7, extras keys, slot planId/position/ID, and the `timeOfDay` <-> derived-slot canonical consistency (`InconsistentPlanTimes`). AGGREGATE-level validation exists (not just per-column).

## Converter-parity verdict

Preflight calls the **same** production `Converters` functions used by Room for persisted reads (`toMap`, `toStringList`, `toIntSet`). JSON grammar, collection grammar, empty/null handling, and unknown-key behavior are therefore identical by construction. LegacyPlanTimeParser and `Converters.toStringList` accept/reject the same language (verified element-by-element: empty string, `[]`, string elements, minute-precision, non-string/object roots all reject in both). Preflight is not more lenient than production; its extra rules (canonical UUID, canonical Boolean, INTEGER storage for integer columns, TEXT storage for text columns) exactly match the sealed 8A contract's allowed/invalid definitions and reject no legitimate historical v2 value.

## Cross-field-invariant verdict

`MedicationPlanAggregateEntity.toDomainMedicationPlan()` is used verbatim as the plan oracle: it validates `daysOfWeek` values 1..7, `intervalDays >= 1` (Domain init), slot planId == plan id, continuous positions, canonical minute local times, deterministic Slot ID v1, and `legacyTimes == slots canonical times`. The preflight therefore cannot form a superset of the production Domain contract: any preflight-accepted plan is by construction Domain-reconstructible. `DoseEventEntity.toV3DomainDoseEvent()` enforces exact `timeH`/`occurredAtEpochMillis` equality. Preflight-accepted-but-mapper-rejected = ZERO.

## CURRENT_GAP disposition verdict

All 13 `batch8BRejections` from the sealed 8A `MigrationContractMatrix` were independently enumerated (MigrationContract.kt:119-161) and each now fails migration in preflight with exact v2 restoration (verified by connected tests, not by a later Repository rejection):

| Case | 8A current | 8B actual | Evidence |
|---|---|---|---|
| unknown event route | MIGRATES_BUT_REPOSITORY_REJECTS | preflight reject + rollback | mapper-oracle test |
| unknown event ester | same | preflight reject + rollback | mapper-oracle test |
| unknown event extra key | same | preflight reject + rollback | mapper-oracle test |
| malformed event extras | same (converter) | preflight reject + rollback | converter test |
| unknown plan route | same | preflight reject + rollback | mapper-oracle test |
| unknown plan ester | same | preflight reject + rollback | mapper-oracle test |
| unknown plan schedule | same | preflight reject + rollback | mapper-oracle test |
| invalid plan day | same | preflight reject + rollback | mapper-oracle test |
| zero plan interval | same | preflight reject + rollback | mapper-oracle test |
| unknown plan extra key | same | preflight reject + rollback | mapper-oracle test |
| malformed plan days | same (converter) | preflight reject + rollback | converter test |
| malformed plan extras | same (converter) | preflight reject + rollback | converter test |
| noncanonical enabled integer | MIGRATES_WITH_INCONSISTENT_SEMANTICS | NONCANONICAL_BOOLEAN reject + rollback | explicit test |

No CURRENT_GAP remains open. Migration fails at preflight; no post-migration Repository failure is used as the rejection mechanism.

## Silent-repair audit

No `getOrDefault`/`getOrElse`/`runCatching`/`coerceAtLeast`/`coerceIn`/`randomUUID`/`systemDefault()`/`Instant.now()`/`LocalDate.now()`/`filterNot`/`parseOrNull`/`orEmpty`/`?:` fallback in any migration source. All parse/validation paths either succeed or throw `LegacyMigrationException`; no invalid legacy value is silently corrected. The only `?:`-like behavior is the locked v3 DDL defaults (zoneId/localDate/slotId=null, source/status/revision constants), which are exactly the sealed migration defaults and validated as such in `validateEventRows`.

## Failure/diagnostic verdict

`LegacyMigrationError` adds `InvalidPersistedValue(field, reason)` with 5 typed categories (INVALID_STORAGE_CLASS, NONCANONICAL_ID, CONVERTER_REJECTED, MAPPER_REJECTED, NONCANONICAL_BOOLEAN). `LegacyMigrationException` message = `Room migration <operation> failed for <table> row fingerprint <16 hex SHA-256>[: category]`. Row identifiers are SHA-256 hashed and truncated to 8 bytes (16 hex). Message contains no raw UUID, dose, medication, schedule detail, extras payload, timestamp, or row serialization; the focused JVM test asserts the exact message shape and absence of the raw identifier. The fingerprint is a high-entropy (UUID) 64-bit diagnostic correlation token, not anonymized data; it is not written to persistent telemetry and only used for local correlation. The typed `error` payloads are in-memory and only accessed programmatically (tests); no production code logs `.error`.

## Atomicity verdict

Room's migration transaction semantics are exercised by connected fixtures that assert, after each failure, `user_version=2`, no `scheduled_dose_slots` table, exact v2 columns, and an exact per-row/storage-class snapshot (`snapshotV2`) equal to the pre-migration state.

- **Preflight rejection rollback:** `assertFixtureRejectsAndRestoresV2` + `assertRejectsAndRestoresV2` prove DDL + no backfill restored exactly.
- **UPDATE fault:** proxy fails `executeUpdateDelete` on the compiled `UPDATE dose_events` statement (mutation-stage, after full preflight) -> exact v2 restore.
- **Slot INSERT fault:** proxy fails `executeInsert` on `INSERT INTO scheduled_dose_slots` (after event backfill began) -> exact v2 restore, no partial slot state.
- **Postcondition fault:** proxy throws at the `PRAGMA integrity_check` query (after all mutations) -> exact v2 restore.

## Data-preservation verdict

Valid matrix preserves IDs, counts, route, ester, dose, extras, legacy timing (positive/zero/negative/millisecond/old/boundary), plan schedule fields, day sets, interval values, enabled state, creation time, slot order, duplicate times, and UUIDv5 slot identity. `validateEventRows` asserts locked v3 defaults exactly (null/NULL/LEGACY/RECORDED/1). No "Repository-readable but field rewritten" masking: the migration `validateMigration` re-reads every backfilled event/slot row and compares against the preflighted expectations before reporting success, and the connected valid test reads every aggregate through production Repositories.

## Repository-readability verdict

`validSyntheticMatrixMigratesAndEveryAggregateIsRepositoryReadable` opens the upgraded database with production `Room.databaseBuilder(AppDatabase)` + `MIGRATION_2_3`, then reads **every** fixture event and plan by ID through `RoomDoseEventRepository` / `RoomMedicationPlanRepository` (not `first()`/sample/count-only), asserts full counts (71 events / 11 plans / 13 slots), and exercises Room -> DAO -> Entity/converters -> mapper -> Repository -> Domain. This is the final P1 criterion and it passes.

## Production-boundary verdict

- Mappers: ZERO diff (DoseEventEntityMapper, MedicationPlanEntityMapper).
- Converters: ZERO diff.
- Repository implementations/contracts: ZERO diff (RoomDoseEventRepository, RoomMedicationPlanRepository, RepositoryStorageException).
- Domain/Entity: ZERO diff.
- AppDatabase: unchanged, version=3, both migrations registered, no `MIGRATION_3_4`, no destructive fallback (grep zero).
- Migration adapted to the sealed production language; language not weakened. 8B makes the DB satisfy the Repository, not vice versa.

## Schema/destructive-fallback verdict

- Schema 2 identity `a8036e3f5ed6bb42d0e7289ac84039f3`, canonical SHA-256 `B8DA54ED...5DA` — verified unchanged.
- Schema 3 identity `c5f5e02cb04b048ca28fe96a74d61606`, canonical SHA-256 `044013C0...1E72` — verified unchanged (independent git blob).
- No `fallbackToDestructiveMigration*` anywhere in production or tests.

## Independent validation

- Focused JVM `LegacyAggregatePreflightTest`: 5/5 PASS (XML).
- Sealed 8A JVM `MigrationPersistenceContractTest`: 6/6 PASS (XML) — expected contract unchanged.
- Full JVM: 51 suites / 417 tests / 0 failures / 0 errors / 0 skipped (XML).
- PK: 5 suites / 49 / 0.
- API33 (emulator-5554, SDK 33 phone): 8B contract 9/9; migration regression 42/42; Repository 23/23.
- API35 (emulator-5560, SDK 35 phone): 8B contract 9/9; migration regression 42/42.
- kspDebugKotlin / assembleDebug / compileDebugAndroidTestKotlin / lintDebug: PASS; lint 0 errors (83 pre-existing warnings).
- Production diff scope, schema hashes, destructive audit: verified (above).

## Release-P1 disposition

All four closure criteria verified:

A. **COMPLETE PREFLIGHT** — PASS: both tables fully read+validated (all 18 fields) before any mutation; structurally guaranteed and empirically proven.
B. **STRICT REJECTION** — PASS: all 13 former gaps now fail migration at preflight with exact rollback.
C. **ATOMICITY** — PASS: preflight/UPDATE/slot-INSERT/postcondition faults all roll back to byte/semantic-equivalent v2 (rows, schema, user_version).
D. **PRODUCTION REPOSITORY READABILITY** — PASS: every successfully migrated aggregate read through production Repository.

Original converter/preflight + Repository-readability release P1: **CLOSED**.

## Remaining release blockers

Four existing P2 remain (unchanged, out of 8B scope):

1. Private real-database validation not executed (optional, separately authorized).
2. Python 3.12 repair/audit evidence not sealed.
3. Recovery/downgrade runbook not sealed.
4. Historical v1 evidence limited; v2 is the formal baseline.

Current Room v3 release blockers after 8B review: **P0/P1/P2 = 0/0/4**.

## Findings

**F1 - "Late invalid" test placement does not match its claim (P2)**
- Severity: P2
- Category: BATCH 8B IMPLEMENTATION
- File: `app/src/androidTest/java/io/github/yuninggu/evolune/data/migration/contract/AppDatabaseMigrationContractTest.kt:146-157`
- Problem: `completeDatasetPreflightRejectsLateInvalidRowBeforeAnyBackfill` inserts 12 valid plans (ids `40000000-...`) plus the invalid plan whose deterministic id (`21000000-...`) sorts **before** the valid ids, so in the `ORDER BY id` scan the invalid row is actually encountered first among plans, not "late". The same applies to report section 13's "valid rows followed by a late invalid plan" claim.
- Evidence: plan id prefixes 21... < 40... under lexicographic `ORDER BY id`.
- Impact: The structural guarantee (complete preflight before backfill) is nevertheless fully proven by the production call order and by the exact-v2 snapshot assertions; the test still demonstrates whole-dataset rejection + rollback. The claim about *position* is inaccurate, so mid/later-position invalid-row evidence is weaker than reported.
- Required action: optionally re-insert with the invalid row genuinely last, or reword the test/report to "complete dataset preflight rejects invalid row before any backfill".
- Blocks 8B sealing? NO. Blocks original P1 closure? NO. Blocks Room v3 release? NO.

**F2 - Report/exception model retains raw values in typed error payloads (P2)**
- Severity: P2
- Category: BATCH 8B IMPLEMENTATION (carried from 8A, not introduced by 8B)
- File: `LegacyMigrationError.kt` (`InvalidEventTimeH` carries eventId+rawTimeH; `InvalidTimeOfDayJson` planId+rawTimeOfDay; element/local-time errors carry originalValue), `LegacyMigrationException.kt`
- Problem: The exception *message* is sanitized (fingerprint + category only) and the focused JVM test asserts that. The typed `error` objects, however, still retain raw UUIDs and raw payload values and are exposed through `exception.error`. No production code logs `.error`, and it is only accessed programmatically in tests today.
- Impact: Message-level privacy requirement is met; this is a defense-in-depth / future-logging risk if a caller ever serializes `.error`.
- Required action (optional, non-blocking): document that `.error` is internal, or reduce its payload to fingerprints/categories if ever surfaced.
- Blocks 8B sealing? NO. Blocks original P1 closure? NO. Blocks Room v3 release? NO.

## Final decision

Batch 8B may be sealed.

The original converter/preflight and Repository-readability release P1 is formally closed by independently reviewed Batch 8B evidence.

After Batch 8B sealing and integration, Batch 8C may begin.

Current Room v3 release blockers are P0/P1/P2 = 0/0/4.

Room v3 remains internal and unreleasable.

Actual release remains forbidden.