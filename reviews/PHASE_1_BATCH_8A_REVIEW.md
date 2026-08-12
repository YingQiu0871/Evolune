# Phase 1 Batch 8A Independent Review

Date: 2026-08-11
Reviewer: DeepSeek (independent read-only)
Worktree: D:\Evolune-batch8a
Branch: phase1/batch8a-migration-contract

## Executive summary

**Decision: APPROVE WITH P2**

- Batch 8A implementation P0/P1/P2: **0 / 0 / 0**
- Current Room v3 release blockers: **0 / 1 / 4**

Summary gates:
- Contract inventory: PASS
- DoseEvent coverage: PASS
- MedicationPlan coverage: PASS
- Converter coverage: PASS
- Mapper Failure coverage: PASS
- Raw fixture architecture: PASS
- SQLite representability: PASS
- Valid v2 definition: PASS
- Invalid/unmigratable definition: PASS
- LegacyTime contract: PASS
- Full Schema2->3 mapping: PASS
- Repository readability contract: PASS
- Atomicity contract: PASS
- Current migration characterization: PASS
- Future/current separation: PASS
- Production semantic diff: ZERO
- Destructive fallback: ZERO
- Schema integrity: PASS
- Focused JVM: PASS (6/6)
- Migration regression: PASS (42/42 in full JVM)
- API33 contract: PASS (7/7)
- API35 contract: PASS (7/7)
- Repository connected: PASS (23/23 in full connected)
- Full JVM: PASS (50/412)
- PK: PASS (49/49)
- Build gates: PASS
- Synthetic/privacy boundary: PASS
- 8B handoff: PASS
- 8A may be sealed: YES
- 8B may begin after sealing/integration: YES
- Room v3 release: NO
- Actual release: FORBIDDEN

## Git / scope verdict

- Branch phase1/batch8a-migration-contract; HEAD descends from phase-1-batch-8-design-v1 (ancestor exit 0)
- Staging empty; only 6 untracked 8A files (3 androidTest + 2 JVM test + 1 report)
- app/src/main diff ZERO; wear/src/main ZERO; schemas ZERO; Gradle ZERO; Manifest ZERO
- No private DB, keystore (temporarily copied for build, removed), APK/log/dump artifact, 8B work

## Persisted-contract verdict

PersistedContractInventory.kt declares 18 schema-2 fields (6 dose_events + 12 medication_plans). Independently verified against schema 2 JSON: dose_events has exactly 6 columns (id/route/timeH/doseMG/ester/extras) and medication_plans has exactly 12 (id/name/route/ester/doseMG/scheduleType/timeOfDay/daysOfWeek/intervalDays/isEnabled/extras/createdAt). Every field has: aggregate, SQLite representation, production decoder, allowed values, invalid representable values, failure behavior, preflight required=true, repository read required=true, fixture required=true. No persisted column omitted. 26 mapping dispositions (18 preserve + 8 derive/default) match schema 3 columns.

## DoseEvent verdict

All 6 v2 fields covered. Mapper failure branches independently verified: InvalidRoute (L187), InvalidEster (L196), InvalidTimeH (L28/L70/L153), InvalidDoseEventInvariant (L54/L130), InvalidZoneId (L82), InvalidLocalDate (L91), InvalidSource (L105), InvalidStatus (L109), InvalidOccurredAtPrecision (L140), InconsistentEventTime (L74/L157). Contract classifies unknown route/ester/extras as MIGRATES_BUT_REPOSITORY_REJECTS (current gap, 8B required). Malformed extras JSON classified as converter rejection. All 7 routes and 5 esters in valid matrix.

## MedicationPlan verdict

All 12 v2 fields covered. Mapper failures verified: InvalidCreatedAt (L46/L141), InvalidPlanInvariant/intervalDays (L68/L130), InconsistentPlanTimes (L109), InvalidSlotPlan/Position/Slot/UnexpectedSlotId (L148-L162), InvalidTimeOfDay (L197/L200). Cross-field invariants (schedule vs times, daysOfWeek 1..7, interval>=1) captured as 8B-required mapper preflight. isEnabled=2 classified as MIGRATES_WITH_INCONSISTENT_REPOSITORY_SEMANTICS. All schedule types in valid matrix.

## Converter verdict

4 TypeConverters verified: UUID (toUUID/fromUUID), Map (toMap/fromMap JSON), StringList (toStringList/fromStringList JSON), IntSet (toIntSet/fromIntSet JSON). All converter-backed fields (extras, timeOfDay, daysOfWeek) have invalid-representable cases in matrix. Malformed JSON -> SerializationException characterized. Unknown extras key -> InvalidExtraKey mapped. No converter-backed field omitted.

## Mapper-Failure verdict

Every materially distinct mapper Failure branch has ownership: DoseEvent 10 categories, MedicationPlan 8 categories. Each mapped to v2-preflight, v3-postcondition, Repository-readability, or NOT_REPRESENTABLE. No unowned failure branch found.

## Raw-fixture verdict

RawV2Fixture.kt uses direct SQLite execSQL inserts into MigrationTestHelper-created v2 database. V2EventRow/V2PlanRow use Any? fields to bypass production Entity validation. Can insert unknown enum strings, malformed JSON, invalid day/interval, noncanonical booleans. Does not call Repository or Entity constructors. Test-only, synthetic, deterministic. No real health data.

## SQLite-representability verdict

NOT REPRESENTABLE correctly identified: NULL in NOT NULL column, NaN in REAL NOT NULL, duplicate PK rows. These are SQLite-rejected before migration. Matrix distinguishes logical invalidity (SQLite-valid but Domain-invalid) from physical impossibility. No artificial impossible fixture used as evidence.

## Valid/invalid DB verdict

Valid v2 = exact structure + every value readable under historical converter contract + canonical IDs + representable times + recognized enums + decodable payloads + internally consistent schedule + mapper invariants satisfied + lossless v3 mapping. Invalid/unmigratable = any condition failing above, including SQLite-coercible but Domain-rejected values. Definitions operationally complete.

## Legacy-time verdict

Coverage: positive, zero, negative, millisecond boundary, old, max/min boundary, infinity/overflow. zoneId=null, localDate=null locked. No system zone/date/clock. LegacyTimeAdapter authoritative. timeH/timeOfDay retained as compatibility shadows.

## Full-mapping verdict

18 v2 columns -> 18 preserve + 8 derive/default = 26 dispositions. Independently cross-checked schema 2 JSON columns vs inventory. Zero omissions.

## Repository-readability verdict

Contract requires: after successful migration, every DoseEvent and MedicationPlan aggregate readable through production Room -> Entity -> converter -> mapper -> Repository -> Domain. Not just SELECT COUNT(*) or integrity_check. Valid matrix enforces today. Adversarial cases characterized as current gap (MIGRATES_BUT_REPOSITORY_REJECTS). 8B must close this.

## Atomicity verdict

Two boundaries required: (1) preflight-invalid -> reject before UPDATE/INSERT, rollback to v2; (2) mutation-stage failure -> transaction rollback. 8B must prove user_version, rows, schema, reopen state for both. Fault-injection seam deferred to 8B (8A not required to add production hooks).

## Current-migration verdict

MIGRATION_2_3 preflights only id+timeH (events) and id+timeOfDay (plans). ADR-016 structural order respected but complete intended preflight not satisfied. Current release P1 OPEN. 8A does not partially resolve this.

## Future-contract verdict

MigrationContract model: PersistedAggregate, ContractValidity (VALID/INVALID_UNMIGRATABLE/NOT_REPRESENTABLE), RejectionStage (SQLITE_CONSTRAINT/MIGRATION_PREFLIGHT/PRODUCTION_CONVERTER/PRODUCTION_MAPPER/REPOSITORY_SEMANTICS/POST_DDL_ATOMICITY_HARNESS), CurrentMigrationOutcome (MIGRATES_AND_REPOSITORY_READS/REJECTS_AND_ROLLS_BACK/MIGRATES_BUT_REPOSITORY_REJECTS/MIGRATES_WITH_INCONSISTENT_REPOSITORY_SEMANTICS/NOT_EXECUTABLE). Rich enough to guide 8B. No current-unsafe-behavior-as-desired-contract found. No @Ignore/skip/swallow/assertTrue(true). CURRENT_GAP explicitly visible.

## Test-quality verdict

MigrationPersistenceContractTest (6 JVM tests): inventory completeness (18 fields, 6+12 split, unique keys, all required), converter language (UUID/Map/StringList/IntSet valid+invalid), mapper failure surface (route/ester/extra/schedule/day/interval), field mapping (18->26), NOT REPRESENTABLE classification. Parameterized/declarative coverage far exceeds 6 method count.

AppDatabaseMigrationContractTest (7 connected tests): raw v2 fixture construction, valid migration + Repository read, current gap characterization (unknown route migrates but Repository rejects), malformed extras (converter rejection), invalid interval (mapper rejection), isEnabled=2 (inconsistent semantics), NOT REPRESENTABLE (SQLite constraint). Names match assertions.

## Independent validation verdict

- Focused JVM: 6/6 PASS (XML verified)
- Full JVM: 50 suites / 412 / 0 / 0 / 0 (XML verified)
- PK: 5 suites / 49 / 0
- API33 (emulator-5554, SDK 33, phone, no watch): 7/7 PASS (XML verified)
- API35 (emulator-5560, SDK 35, phone, no watch): 7/7 PASS (XML verified)
- assembleDebug PASS; lintDebug PASS (0 errors); kspDebugKotlin PASS; compileDebugAndroidTestKotlin PASS
- schema3 blob SHA-256 = 044013C0... (independently computed, matches)
- Production diff: app/src/main, wear/src/main, schemas, Gradle, Manifest all ZERO
- Destructive fallback: grep zero (no fallbackToDestructiveMigration*)
- Keystore temporarily copied from D:\Evolune, removed after validation

## Schema / destructive-fallback verdict

- Schema 2 identity: a8036e3f5ed6bb42d0e7289ac84039f3 (unchanged)
- Schema 2 SHA-256: B8DA54ED... (unchanged)
- Schema 3 identity: c5f5e02cb04b048ca28fe96a74d61606 (unchanged)
- Schema 3 SHA-256: 044013C0... (independently verified)
- No fallbackToDestructiveMigration/From/OnDowngrade in production

## Privacy verdict

All fixtures synthetic. RawV2Fixture uses hardcoded UUIDs (00000000-0000-0...), synthetic route/ester strings, synthetic JSON payloads. No real DB, no real-derived data, no health data in Git/CI/report.

## 8B handoff verdict

8B must: (1) expand MIGRATION_2_3 preflight to every converter/mapper-backed field, (2) validate after DDL before UPDATE/INSERT, (3) reject every SQLite-representable Domain-invalid state from 8A matrix, (4) preserve valid exactly, (5) no repair/default/coercion, (6) prove preflight-invalid no mutation, (7) prove mutation rollback, (8) reopen through production Room, (9) read every DoseEvent/MedicationPlan through Repository, (10) satisfy 8A adversarial matrix as acceptance oracle. Rules fully deterministic from 8A contract. No "decide later" remaining.

## Findings

### P0
None.

### P1
None (8A implementation layer).

### P2

**F1 - Current release P1 tracked by 8A (not 8A defect)**
- Category: CURRENT RELEASE BLOCKER
- Source: MIGRATION_2_3 preflight only id/timeH/timeOfDay
- Problem: migration success does not guarantee Repository readability for non-time fields
- Evidence: mapper Failure branches (InvalidRoute/InvalidEster/InvalidExtraKey/InvalidScheduleType/InvalidDayOfWeek/InvalidPlanInvariant) + CorruptAggregateException
- Blocks 8A sealing? NO. Blocks Room v3 release? YES.

**F2 - 4 pending-evidence P2 (inherited from design)**
- Category: CURRENT RELEASE BLOCKER (pending evidence)
- Problem: private real-DB not executed; Python 3.12 not sealed; recovery runbook not sealed; v1 evidence limited
- Blocks 8A sealing? NO. Blocks Room v3 release? YES.

## Independent validation

- Git: branch/HEAD/ancestor/status/diff/--check verified
- Focused JVM: 6/6 (XML)
- Full JVM: 50/412/0/0/0 (XML)
- PK: 5/49/0
- API33 (5554 SDK33 phone): 7/7 (XML)
- API35 (5560 SDK35 phone): 7/7 (XML)
- assemble/lint(0 errors)/ksp/compileAndroidTest: PASS
- Schema3 SHA-256: 044013C0... (independent)
- Production diff: ZERO
- Destructive fallback: ZERO

## Final decision

**Batch 8A may be sealed.**

After Batch 8A sealing and integration, Batch 8B may begin.

The current Room v3 release P1 remains unresolved and must be closed by independently reviewed Batch 8B evidence.

Room v3 remains internal and unreleasable.

Actual release remains forbidden.