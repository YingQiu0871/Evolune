# Phase 1 Batch 8D Report

## 1. Status

Batch 8D implementation complete pending independent review.

No Batch 8D commit, tag, merge, release, or Batch 8E work was performed.

## 2. Baseline

| Item | Value |
|---|---|
| Batch 8C implementation | `b518c3c70698e357816e1114a0c5d55dbcd99cd2` |
| Batch 8C review | `8c6e775bd2967b974d488c7eceed912a700e4e0c` |
| `phase-1-batch-8c` target | `8c6e775bd2967b974d488c7eceed912a700e4e0c` |
| Batch 8C integration merge | `64d4dbecdfe0c285cb97463b2296d7e27e2e8f6d` |
| Batch 8D branch | `phase1/batch8d-repair-realdb-safety` |
| Batch 8D worktree | `D:\Evolune-batch8d` |

The Batch 8D branch remains at the integration merge. All Batch 8D files are unstaged.

## 3. Scope

Batch 8D implements an offline Evolune Room v2 audit and repair tool, synthetic evidence, Kotlin/Python classification parity, a repaired-copy migration and Repository validation harness, and a private real-database safety runbook.

It does not add an app repair UI or startup hook. It does not change production migration, schema, Domain, Entity, mapper, converter, Repository, Gradle, Manifest, Widget, Wear, JSON, PK, or product behavior.

## 4. Migration/Repair Separation

The official migration remains strict:

- valid v2 data is deterministically migrated to v3;
- invalid v2 data fails migration;
- migration never invokes repair;
- repair acts only on an explicitly selected offline v2 copy;
- a repaired v2 copy must still pass the official Room migration and production Repository validation.

`MIGRATION_2_3` remains the migration authority. The Python tool is not a production source of truth.

## 5. Existing Tool Audit

The pre-Batch 8D repository already contained `tools/repair-v2/repair_v2.py`, tests, a sample manifest, and documentation. The v1 tool used Python standard-library APIs and already separated scan, repair, and verify, rejected in-place output, copied before mutation, and used a correction manifest.

The main gaps were incomplete v2 persisted-contract coverage, no mandatory preview token, persistent audit output containing paths/raw identifiers/raw invalid values, incomplete schema identity evidence, no Kotlin/Python parity corpus, no official repaired-copy Room chain, no private real-DB runbook, and no sealed Python 3.12 evidence.

## 6. Tool Architecture

Tool version `2.0.0` remains outside app runtime and uses only Python's standard library. Its commands are:

- `scan`: read-only complete v2 persisted-contract audit;
- `preview`: validates the operator manifest and emits a deterministic authorization token;
- `repair`: requires the exact preview token and creates a new repaired v2 copy;
- `verify`: read-only post-repair audit.

Exit codes distinguish clean success, blocking data, usage error, database identity rejection, repair/verify failure, and unexpected internal failure.

## 7. Audit-Only Mode

`scan` opens the explicitly selected input read-only, checks database identity and all sealed v2 persisted fields, emits only sanitized structural diagnostics, and performs zero mutation. Tests prove that input SHA-256 and modification time are unchanged.

A dirty audit returns exit code `1`; clean audit returns `0`. Finding an issue never triggers repair.

## 8. Repair Authorization Model

Repair requires all of the following:

1. An explicit version 1 correction manifest.
2. A manifest SHA bound to the exact input SHA.
3. A successful `preview` operation.
4. The exact 64-hex preview token bound to tool version, input SHA, and manifest SHA.
5. A distinct, non-existing output path.
6. Explicit invocation of the `repair` command.

Changing the tool version, input, or manifest invalidates the token. There is no automatic repair, force-in-place option, output overwrite, fallback, or semantic inference.

## 9. Backup/Snapshot Semantics

The input is the immutable source copy. Repair creates a separate output, verifies that its pre-mutation hash equals the input hash, performs changes transactionally, validates the result, and leaves the input hash, size, and modification time unchanged.

The operator runbook additionally requires a separate immutable safety backup whose locally recorded SHA-256 equals the supplied source before any working copy is used. The tool never replaces the sole original.

## 10. WAL/SHM Consistency

Input databases with non-empty sibling `-wal`, `-shm`, or `-journal` files are rejected. Operators must provide a cleanly closed database or create a SQLite-consistent snapshot through the SQLite backup API, `VACUUM INTO`, or another controlled snapshot process. Copying only the main file while WAL is active is explicitly invalid.

After repair, the tool checkpoints when necessary, rejects remaining non-empty output sidecars, and removes empty disposable sidecars.

## 11. Supported Schemas

The tool accepts only the formal Room v2 baseline:

- `PRAGMA user_version = 2`;
- required `dose_events`, `medication_plans`, and `room_master_table` structures;
- required v2 columns;
- Room identity hash `a8036e3f5ed6bb42d0e7289ac84039f3`.

Non-SQLite files, v1, v3, unknown versions, missing structures, and wrong Room identities fail safely. Historical v1 is recognized as unsupported; no v1 repair is promised.

## 12. Repairable Categories

The only supported repair operations are explicit operator-supplied replacements for blocking legacy time fields:

| Category | Detection | Repair |
|---|---|---|
| Invalid event `timeH` storage/value/conversion | Complete | Explicit manifest replacement only |
| Invalid or non-minute plan `timeOfDay` payload | Complete | Explicit manifest replacement only |

The replacement is never inferred. It must identify an existing canonical UUID row, cover each blocking time row, avoid clean rows, and satisfy the sealed time grammar. Plan order and duplicate times are preserved.

## 13. Unrepairable Categories

The audit detects but the tool does not repair invalid UUIDs, route, ester, dose storage, extras JSON/keys/values, schedule type, day storage/values, interval, Boolean storage, created-at storage, arbitrary storage-class violations, unknown schema, unknown version, or wrong Room identity.

These categories require an external operator/user decision or remain unrepaired. Their presence prevents creation of a repaired output.

## 14. No-Invented-Semantics Policy

The tool never guesses medication, route, ester, dose, schedule, date, time, UUID, or extras. It does not clamp, truncate, sort, deduplicate, renumber, normalize irrelevant fields, substitute current time, or convert an unknown value to a default. Operator-supplied time replacements are explicit data decisions rather than automated medical inference.

## 15. Privacy-Safe Diagnostics

Persistent summaries and JSONL audit records contain only tool version, mode, schema/version identity, aggregate/table category, field/category, issue count, repairability, result, and a 16-hex SHA-256 row fingerprint.

They exclude database paths, raw UUIDs, raw invalid values, dose, medication name, schedule, extras payload, timestamps, and SQL rows. Tests verify that the path, canonical synthetic UUID, and invalid time value do not appear in serialized output. Real audit files remain private and forbidden from Git even though their format is sanitized.

## 16. Python 3.12 Evidence

Runtime: `Python 3.12.13` at the bundled Codex Python executable.

The final focused suite ran 94 tests: 94 passed, 0 failures, 0 errors. Coverage includes database identity, unsupported v1/v3, complete persisted grammar, read-only audit, preview authorization, privacy, sidecar rejection, Java-compatible time conversion, manifest validation, copy-only repair, failure cleanup, semantic idempotency, and synthetic evidence generation.

The Python 3.12 release P2 is closed by this evidence.

## 17. Kotlin/Python Parity

A single 25-case JSON corpus is stored in the tool directory and mirrored into Android test assets. It covers valid event/plan aggregates and invalid identity, enum, JSON, storage-class, numeric, time, day, interval, Boolean, and created-at cases.

Python classification passed all 25 cases. `RepairToolParityTest` passed on API 33 and API 35 and evaluated the same 25 cases through the sealed Kotlin/Room migration classification boundary. There was no validity disagreement.

## 18. Synthetic Audit Corpus

The Python suite validates clean representative v2 databases and the full sealed invalid category matrix, including unknown route/ester, malformed or unknown extras, schedule and day failures, invalid interval/Boolean/storage classes, malformed time payloads, invalid UUIDs, non-finite and overflowing event time, and cross-field persisted constraints.

The generated repair-chain fixture begins with exactly two blocking categories: `TIME_H_NON_FINITE` and `TIME_OF_DAY_NON_MINUTE`. The read-only audit reports two blockers without changing the input.

## 19. Synthetic Repair Corpus

The deterministic generator created an invalid v2 source and an independently repaired v2 copy. One event time and one plan time were explicitly corrected. The repaired output had zero remaining issues.

Evidence hashes:

- original synthetic v2 SHA-256: `DEE9ECE5A329F44547737B16D755A3B5A7B63DD52E1E4FDDBEA97AFDC1F9A2E7`;
- repaired synthetic v2 SHA-256: `BF93CC10054F50D36D0CCB224FC6E92AA965790201CB9DC705AAD0206D806D96`.

CLI smoke results were: dirty scan exit `1`, preview exit `0`, repair exit `0`, verify exit `0`, and preview token length 64.

## 20. Repair Idempotency

The first repair changes the two explicitly targeted fields. A second repair attempt at the semantic boundary uses a clean manifest and produces zero event corrections, zero plan corrections, and a clean output. No further semantic change occurs.

## 21. Failure Atomicity

All corrections execute in one SQLite transaction. A failed or incomplete repair removes the output copy. Tests cover wrong preview token, stale/wrong input hash, unresolved issues, unknown corrections, existing output, same-path aliases, injected database failure, and unrepairable categories. In every case the input remains unchanged and no half-written authoritative database is retained.

## 22. Repair to Migration to Repository Chain

`RepairToolOutputMigrationTest` consumed only the explicit gzip/base64 bytes and SHA-256 of the generated repaired copy. On both API 33 and API 35 it:

1. proved the supplied repaired-copy SHA;
2. ran the official `MIGRATION_2_3`;
3. reopened the database through Room v3;
4. passed `integrity_check` and `foreign_key_check`;
5. read the migrated event and plan through production Room Repository implementations.

Both device runs passed. The repaired database binary was not added to Git.

## 23. Private Real-DB Workflow

The executable runbook requires an exact user-supplied `REAL_DB_PATH`, explicit `PRIVATE REAL-DB VALIDATION` authorization, no filesystem/device search, a closed or SQLite-consistent snapshot, immutable original and safety backup with local SHA evidence, a separate working copy, read-only audit first, sanitized local evidence, and migration of a copy only when clean.

If invalid, validation stops. Repair requires a second, separate `REAL-DB REPAIR AUTHORIZED` decision. The completed chain must include official migration, integrity/FK checks, structural/count comparison, and production Repository reads. No database, manifest, audit, APK, backup, or health value may be committed.

## 24. Private Real-DB Execution

NOT EXECUTED - awaiting explicit user path/authorization.

No real database path was supplied. No filesystem, Android backup, connected device, or other project was searched. This is not recorded as a pass and remains a release P2.

## 25. Batch 8C Reproducibility Disposition

The previous temporary v2 seeder reproducibility P2 is addressed by a documented recipe identifying the historical v2 commit/tag, production Repository seeding boundary, deterministic event/plan fixture IDs, expected historical APK hashes, and the preserved-upgrade replay sequence.

No historical APK or seeder binary was committed. The recipe provides provenance and rebuild instructions for future 8E replay without modifying sealed 8C evidence.

## 26. Regression

| Gate | Result |
|---|---|
| Python 3.12 repair/audit suite | 94/94 pass |
| Shared parity corpus | 25/25 classifications agree |
| Batch 8A `MigrationPersistenceContractTest` | 6/6 pass |
| Batch 8B `LegacyAggregatePreflightTest` | 5/5 pass |
| API 33 migration/baseline/contract/Repository selection | 75/75 pass, 0 failed/skipped |
| API 33 repaired-copy migration/Repository chain | Pass |
| API 35 repaired-copy migration/Repository chain | Pass |
| API 33 parity harness | Pass, 25 cases |
| API 35 parity harness | Pass, 25 cases |
| Full App JVM | 51 suites / 417 tests / 0 failures/errors/skipped |
| PK regression | 5 suites / 49 tests / 0 failures/errors/skipped |
| Wear JVM | 1 suite / 1 test / 0 failures/errors/skipped |

API 33 and API 35 evidence came from real connected instrumentation execution, not Android-test compilation.

## 27. Build/Lint

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin` | Pass |
| `:app:assembleDebug` | Pass |
| `:app:compileDebugAndroidTestKotlin` | Pass |
| `:app:lintDebug` | Pass |
| `:wear:testDebugUnitTest` | Pass, 1/1 |
| `:wear:assembleDebug` | Pass |

## 28. Schema Integrity

| Schema | Identity hash | Canonical Git-blob SHA-256 | Result |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | Unchanged |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | Unchanged |

KSP completed without a tracked schema diff.

## 29. Production Boundary

The Batch 8D diff under `app/src/main`, `wear/src/main`, schemas, Gradle files, and Manifests is zero. The two Android files are instrumentation-only evidence. The repair tool is never invoked by app startup or migration and does not weaken mapper, converter, Repository, or Room behavior.

Search for `fallbackToDestructiveMigration*` in production sources returned zero matches.

## 30. Privacy/Data Boundary

All implemented tests and generated databases use deterministic synthetic identifiers and values. No real database, medication history, backup, APK, keystore, signing secret, or user health data belongs in the Batch 8D change set. Generated databases and local build evidence are disposable and must be removed before review handoff.

## 31. Batch 8D P0/P1/P2

Batch 8D implementation findings: **P0/P1/P2 = 0/0/0**.

The tool remains explicit, copy-only, fail-safe, privacy-safe, cross-language consistent, and outside production runtime. No unresolved implementation defect was found in the completed scope.

## 32. Current Room v3 Release Blockers

Current Room v3 release blockers: **P0/P1/P2 = 0/0/3**.

1. Private real-database validation is not executed and requires an exact user path plus separate authorization.
2. User-visible recovery and downgrade guidance remains for Batch 8E.
3. Historical v1 evidence remains limited; v2 is the formal baseline and the tool safely rejects v1.

The Python 3.12 evidence blocker is closed. The Batch 8C seeder reproducibility finding is addressed by the documented replay recipe. Room v3 remains internal and unreleasable.

## 33. Deferred to Batch 8E

Batch 8E must independently review and seal recovery/downgrade guidance, reassess the remaining release P2 items, and perform any separately authorized private validation. Release signing, distribution, and actual release remain forbidden. Batch 8E has not started.

## 34. Decision

Batch 8D implementation complete pending independent review.
