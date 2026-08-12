# Phase 1 Batch 8 Design

## 1. Status

Design only. Implementation is not authorized.

Room v3 remains internal and unreleasable. This document does not authorize a
production database upgrade, a release candidate, an APK or AAB publication, a
version change, a real-database operation, or any Batch 8 implementation.

No real or real-derived medication database was read while producing this
design. The audit used source code, locked Room schemas, synthetic-test code,
and sealed reports only.

## 2. Baseline

| Item | Locked value |
|---|---|
| Branch | `phase1/batch8-design` |
| Worktree | `D:\Evolune-batch8-design` |
| Base tag | annotated tag `phase-1-batch-7` |
| Base/tag target | `ed87034c4e649f12a77ac4c6aed2b0a4c440c5ad` |
| Batch 7 closure report | `88db6a9381d541c9685c7092e86ed37d5343ac02` |
| Batch 7 closure review | `ed87034c4e649f12a77ac4c6aed2b0a4c440c5ad` |
| Batch 7 review decision | `APPROVE WITH P2`, `0/0/8` |

All Batch 7 sub-batch and remediation tags are ancestors of
`phase-1-batch-7`. The Batch 7 integration worktree and this design worktree
were clean before this document was created.

## 3. Objective

Batch 8 is the migration, real-database-safety, and release-authorization gate
for Room v3. It must answer one question with auditable evidence:

> Can an Evolune database produced under the locked v2 contract be upgraded
> atomically and losslessly to locked v3, reopened through the production Room
> and Repository paths, and used by a preserved-data installed-app upgrade?

Passing Batch 8 may authorize Room v3 for a later release operation. It does
not perform that release.

## 4. Non-goals

Batch 8 is under feature freeze. It must not introduce or redesign:

- Widget Material You colors or transparency;
- Wear timeline, Tile expansion, or snooze/postpone;
- custom medication identity;
- MedicationPlan JSON;
- Health Connect, cloud/Drive backup, or onboarding;
- PK algorithms, JSON v1 semantics, Domain fields, Repository contracts, or a
  second database/source of truth;
- destructive migration, automatic data repair, or legacy-column deletion.

Only release-safety work directly required by this design may be proposed in a
later authorized sub-batch.

## 5. Current Room state

`AppDatabase.kt:21-22` declares `version = 3` and `exportSchema = true`.
`AppDatabase.kt:68` registers both `MIGRATION_1_2` and `MIGRATION_2_3`; no
`fallbackToDestructiveMigration` call exists.

| Schema | Identity hash | Canonical SHA-256 | State |
|---|---|---|---|
| v2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | locked compatibility baseline |
| v3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | locked internal schema |

The v2-to-v3 structural delta is additive:

- `dose_events` gains `occurredAtEpochMillis`, `zoneId`, `localDate`, `slotId`,
  `source`, `status`, and `revision`;
- `scheduled_dose_slots` is created;
- a `planId` index and unique `(planId, position)` index are created;
- slots reference `medication_plans.id` with `ON DELETE CASCADE`;
- all existing `dose_events` and `medication_plans` columns remain unchanged.

The legacy `timeH` and `timeOfDay` columns remain compatibility shadows for at
least one formal 1.0 release cycle.

## 6. Existing migration behavior

`AppDatabaseMigrations.kt:8-16` performs:

1. DDL (`applyV3Schema`);
2. complete event and plan time preflight;
3. event timestamp UPDATE and slot INSERT;
4. post-migration row, value, slot, and foreign-key validation.

Event preflight currently reads only `id` and `timeH`
(`AppDatabaseMigrations.kt:88-131`). Plan preflight currently reads only `id`
and `timeOfDay` (`AppDatabaseMigrations.kt:134-176`). Data writes occur only
after both lists have been built in memory.

Locked transformations are:

- `timeH` numeric storage is converted through the shared strict legacy time
  primitive to exact epoch milliseconds;
- migrated events receive `zoneId = null`, `localDate = null`, `slotId = null`,
  `source = LEGACY`, `status = RECORDED`, and `revision = 1`;
- `timeOfDay` keeps original order and duplicates; accepted zero-second forms
  become minute-precision slot values and deterministic Slot ID v1 UUIDs;
- original `timeH` and `timeOfDay` values are not updated.

Invalid event ID, plan ID, storage class, non-finite/nonrepresentable `timeH`,
malformed `timeOfDay`, invalid local time, or non-minute plan time raises
`LegacyMigrationException`. It is not repaired, skipped, clamped, replaced, or
converted into migration success.

Current coverage consists of 18 `AppDatabaseMigrationTest` tests, 22
`AppDatabaseMigrationMatrixTest` tests, two v2 baseline tests, 43 migration JVM
tests (15 primitive, five storage-class, and 23 plan-parser tests), and 23 Room
Repository connected tests. Existing synthetic fixtures include empty/single/
multiple rows, boundary timestamps, duplicate slots, fixed UUIDv5 vectors,
rollback, FK/cascade/unique-index checks, a 2,000-event/100-plan history, and
deterministic two-database comparison.

## 7. Locked migration contract

ADR-016 (`docs/evolune/DECISIONS.md:162-176`) remains controlling and unchanged.
The implementation follows its required DDL -> full preflight -> data writes
-> post-validation order inside SQLiteOpenHelper's outer upgrade transaction.
DDL is allowed before preflight because any thrown failure rolls it back with
the data writes and `user_version`.

Batch 8 locks an additional release-level consequence of the existing
Repository architecture:

> A migration may report success only if every migrated legacy row can be
> decoded and mapped through the production v3 Repository read path.

Schema validation alone is insufficient. A database that opens as Room v3 but
contains a row rejected by `toV3DomainDoseEvent()` or
`MedicationPlanAggregateEntity.toDomainMedicationPlan()` has not passed the
release migration contract.

This exposes one current P1 release blocker: migration preflight validates the
time-related subset, while production mappers also reject unknown route,
ester, extra keys, schedule type, invalid day values, invalid interval values,
and malformed converter payloads. Repository reads turn mapping failures into
`CorruptAggregateException` (`RepositoryStorageException.kt:28-30`). Existing
tests prove mapper rejection but do not prove those rows block migration before
write-back.

Resolving that gap must preserve ADR-016 strictness and the locked schemas. It
must not weaken mapper validation or add fallback behavior.

## 8. v2 -> v3 field mapping table

`Valid` below means accepted by the locked v2 storage contract and the formal
v3 Repository read path. Batch 8 adds no new medication-value invariant where
Domain currently has none.

| v2 field | v3 field/result | Transformation and invariant | Invalid condition | Repairable automatically? |
|---|---|---|---|---|
| `dose_events.id` | same `id` | Preserve canonical UUID identity | NULL/non-TEXT/non-UUID/noncanonical token | No |
| `dose_events.route` | same `route` | Preserve one of seven explicit storage tokens | Unknown token or undecodable storage | No |
| `dose_events.timeH` | same plus `occurredAtEpochMillis` | Preserve raw value; strict exact millisecond conversion | NULL/TEXT/BLOB, non-finite, multiplication/range overflow | Only by explicit manifest in separate tool |
| `dose_events.doseMG` | same | Preserve numeric value exactly; no new Domain validation | Not readable under locked Room numeric contract | No automatic policy |
| `dose_events.ester` | same `ester` | Preserve one of `E2/EB/EV/EC/EN` | Unknown token or undecodable storage | No |
| `dose_events.extras` | same `extras` | Preserve converter-decodable map with six explicit keys | Malformed payload, wrong shape/type, unknown key | No |
| no v2 field | `zoneId` | Set NULL | Any non-NULL migrated default | Not applicable |
| no v2 field | `localDate` | Set NULL | Any non-NULL migrated default | Not applicable |
| no v2 field | `slotId` | Set NULL; do not infer old event slot | Any inferred value | Not applicable |
| no v2 field | `source` | Set `LEGACY` | Any other migrated default | Not applicable |
| no v2 field | `status` | Set `RECORDED` | Any other migrated default | Not applicable |
| no v2 field | `revision` | Set `1` | Value below 1 or any other migrated default | Not applicable |
| `medication_plans.id` | same `id` and slot `planId` | Preserve canonical UUID identity | NULL/non-TEXT/non-UUID/noncanonical token | No |
| `name` | same | Preserve exact TEXT; add no new name validation | Not readable as locked non-null TEXT | No |
| `route` | same | Preserve an explicit supported route token | Unknown token or undecodable storage | No |
| `ester` | same | Preserve an explicit supported ester token | Unknown token or undecodable storage | No |
| `doseMG` | same | Preserve numeric value exactly; add no new Domain validation | Not readable under locked Room numeric contract | No automatic policy |
| `scheduleType` | same | Preserve `DAILY`, `WEEKLY`, or `CUSTOM` | Unknown token or undecodable storage | No |
| `timeOfDay` | same plus slot rows | Preserve raw string; parse ordered minute slots and Slot ID v1 | Malformed JSON/type/time or non-zero seconds/nanos | Only by explicit manifest in separate tool |
| `daysOfWeek` | same | Preserve converter-decodable set; mapped values must be 1..7 | Malformed payload/type or value outside 1..7 | No |
| `intervalDays` | same | Preserve integer; Domain requires at least 1 | Less than 1 or unreadable storage | No |
| `isEnabled` | same | Preserve locked boolean representation and meaning | Noncanonical/unreadable boolean representation | No |
| `extras` | same | Preserve converter-decodable map with six explicit keys | Malformed payload, wrong shape/type, unknown key | No |
| `createdAt` | same | Preserve epoch-millisecond `Long` and exact `Instant` meaning | Unreadable integer representation | No |
| derived slots | `scheduled_dose_slots` | One row per list position; original order/duplicates; UUIDv5 v1 | Any ID/plan/position/time mismatch or collision | No |

Route and ester are currently owned through the accepted ADR-015 transitional
dependency. Batch 8 validates stored tokens against that boundary; it does not
move either enum. `doseMG` and extra values are preserved according to current
Domain compatibility. Imposing a new finite/positive rule would require a
separate Domain decision and is not hidden inside migration validation.

## 9. Valid / invalid legacy database definitions

A **valid v2 database** must satisfy all of the following:

1. `user_version = 2`, locked v2 Room identity, required tables/columns,
   constraints, and affinities;
2. every value is readable under the historical Room converter/storage
   contract, not merely coercible by arbitrary SQLite SQL;
3. every event and plan identity is canonical and stable;
4. every row satisfies all field rules in section 8;
5. all derived event milliseconds and slot rows are deterministic;
6. migrating the database produces locked schema v3;
7. integrity and foreign-key checks pass;
8. every event and complete plan aggregate can be read through production
   Repositories after reopen without mapping or converter failure.

An **invalid/unmigratable v2 database** is one that fails any condition above.
This includes externally corrupted rows even if SQLite can coerce them into a
query result. Duplicate primary IDs are structurally rejected by v2; duplicate
event times and duplicate plan slot times are valid and must remain distinct
where identity differs. Optional legacy collections may be empty. NULL is valid
only where the locked v2 schema and converter contract allow it.

Cases Android/SQLite cannot materialize in the exact v2 schema, such as a NULL
in a v2 NOT NULL column or NaN bound into the exact REAL NOT NULL fixture path,
must be marked `NOT REPRESENTABLE` at instrumentation level. Their policy still
requires JVM/parser coverage; tests must not fake a persisted state and call it
device evidence.

## 10. Strict-failure semantics

For any invalid row, the authorized future behavior is:

- migration throws a typed/contextual failure and startup does not treat the
  database as upgraded;
- the outer upgrade transaction leaves schema, rows, indexes, Room identity,
  and `user_version` at v2;
- no row is dropped, rewritten best-effort, or replaced with current time,
  current zone, guessed enum, empty metadata, or default medication values;
- no second database becomes a fallback source of truth;
- production logs and UI expose only sanitized diagnostic class/count/context,
  never raw medication values, complete rows, UUID lists, or time arrays;
- release builds must present a bounded recovery state rather than crash-loop,
  permit writes, clear data, or silently retry forever.

The recovery state may explain that the local database was not changed, direct
the user to retain a backup and contact the explicit repair/support workflow,
and allow a deliberate retry after correction. Data reset is a separately
confirmed destructive last resort, never the default recovery action. This is
a release-safety surface, not permission to implement it in this design phase.

## 11. Atomicity requirements

The migration transaction must produce exactly one of two outcomes:

- complete locked v3 schema, complete backfill, successful validation, v3 Room
  identity, and `user_version = 3`; or
- byte/semantic-equivalent v2 logical contents, no v3 columns/table/indexes,
  v2 Room identity, and `user_version = 2`.

8B connected tests must snapshot and compare original rows, schema objects,
indexes, `room_master_table`, `user_version`, `integrity_check`, and side-table
counts after failures in each phase. The matrix must include:

- a non-time preflight rejection after DDL but before any UPDATE/INSERT;
- an event-write failure after successful full preflight;
- a slot-write failure after event work has begun;
- a post-validation failure;
- cancellation/process interruption represented by a deterministic test-only
  fault seam where platform-level interruption cannot be asserted reliably.

Any test seam must be unavailable from production call paths and must not add a
commit/rollback call inside `MIGRATION_2_3`. `PendingResult`, UI retry, or a
second transaction cannot stand in for SQLiteOpenHelper atomicity.

## 12. Repair-tool separation

`tools/repair-v2/` remains a separate offline, developer/private operator tool.
Its current boundary is documented in `tools/repair-v2/README.md:1-18`:
read-only scan, explicit manifest, repair to a distinct copy, verify, SHA-256
binding, transaction rollback, and no in-place change. It currently handles
only `timeH` and `timeOfDay` corrections.

Batch 8 may qualify the existing tool and workflow; it must not call it from
app startup or migration. It is not release-facing app functionality and it is
not an automatic user action. A future private use requires separate explicit
authorization for the exact database copy.

Unknown routes/esters/extra keys, malformed unrelated converter payloads, or
invalid medication semantics are not automatically repairable. Extending the
manifest beyond the ADR-016 time boundary requires an independently reviewed
design and explicit human correction source. The migration must not catch a
failure, invoke repair, and continue.

Before any tool is relied on, 8D must run its synthetic suite on actual Python
3.12, prove input immutability and failed-output deletion, inspect its privacy
outputs, and verify the repaired copy again through official Android migration
and production Repository reads.

## 13. Synthetic fixture matrix

| Class | Required cases | Expected result |
|---|---|---|
| Baseline | empty, one event, one plan, multiple mixed rows | migrate and reopen |
| Enum coverage | all explicit Route and Ester tokens/combinations; all schedule types | preserve and Repository-read |
| Event time | positive, zero, negative, millisecond boundary, old timestamp, max/min representable boundaries | exact conversion |
| Invalid event time | NaN/Infinity when representable, multiplication/range overflow, TEXT/BLOB/NULL | typed failure and full rollback |
| Event fields | unknown route/ester/extra key, malformed extras, converter type mismatch | preflight failure and rollback |
| Plan fields | unknown route/ester/schedule, malformed days/extras, day outside 1..7, interval 0/negative, noncanonical boolean | preflight failure and rollback |
| Plan time | empty SQL string, `[]`, zero-second forms, duplicates, boundaries, malformed JSON, wrong element type, invalid/non-minute time | preserve valid; reject invalid |
| Identity | canonical IDs, malformed/noncanonical IDs, duplicate PK attempt, deterministic Slot ID v1 | preserve or reject before writes |
| Optional/legacy | empty collections, irrelevant schedule fields, repeated values | preserve current Domain semantics |
| Scale | at least 2,000 events/100 plans plus a larger bounded stress fixture | deterministic, no loss |
| Determinism | identical fixtures in two databases and repeated clean setup | byte/row-equivalent logical output |
| Atomicity | injected failures at preflight/write/post-validation boundaries | exact v2 rollback |
| Provenance | historical app-generated synthetic snapshots from locked commits | migrate through official path |

Instrumentation-impossible states are labeled `NOT REPRESENTABLE` and covered
at the narrowest trustworthy JVM/parser layer. Public fixtures remain fully
synthetic and contain fixed synthetic identifiers and values only.

## 14. Post-migration invariants

Every successful fixture and authorized private copy must prove:

- event and plan row counts, IDs, names, doses, routes, esters, extras,
  schedules, day sets, interval values, enabled state, created time, `timeH`,
  and `timeOfDay` are preserved under their locked representations;
- event milliseconds equal the strict compatibility calculation exactly;
- legacy event metadata is exactly NULL/NULL/NULL, `LEGACY`, `RECORDED`, `1`;
- slot count, plan association, order, duplicate times, positions, local times,
  and UUIDv5 v1 identities are exact;
- no row is duplicated or dropped and no orphan exists;
- unique and cascade behavior remains enforced;
- `PRAGMA integrity_check` is `ok` and `foreign_key_check` is empty;
- schema identity and canonical schema hash equal locked v3;
- Room closes and reopens;
- production `DoseEventRepository` reads every event and
  `MedicationPlanRepository` reads every complete aggregate;
- representative Repository CRUD after upgrade does not alter untouched legacy
  rows and continues dual-writing legacy compatibility fields.

Raw-SQL post-validation alone does not satisfy the Repository-read invariant.

## 15. Fresh-install validation

Fresh v3 and upgraded v3 are separate provenance paths. The fresh-install gate
must create and reopen locked v3 on API 33 and API 35 phones, then exercise:

- empty and populated database creation;
- DoseEvent and MedicationPlan Repository CRUD, CAS, conflict, slots, and
  reopen;
- JSON v1 import/export boundaries;
- PK-facing Repository reads and numerical regression;
- app startup, primary editor flows, reminders, Widget, and Wear handoff with
  synthetic data only.

Fresh install evidence cannot be cited as preserved-data migration evidence.

## 16. Preserved-data upgrade validation

The mandatory release gate must install a historical build that owns locked v2,
populate it through app/Room production writers with synthetic data, close the
app, install the candidate build over the same application ID and signing
lineage, and launch the upgraded app without clearing data.

Required paths are API 33 and API 35 phone AVDs. A foldable AVD adds responsive
UI/reopen smoke coverage but does not replace either phone gate. The workflow
must record build/tag provenance, package ID, signing certificate digest,
version codes, device serial/model/API, pre/post sanitized counts and hashes,
migration result, Repository full reads, and report locations.

It must prove:

1. v2 data exists before install-over;
2. install-over preserves app data;
3. first launch performs official `MIGRATION_2_3` exactly once;
4. existing records/plans/duplicates/order remain visible and editable;
5. a process restart and device restart still reopen the same v3 data;
6. failure fixture install-over leaves a v2 database and reaches the bounded
   recovery behavior without destructive fallback;
7. no uninstall, `pm clear`, fresh reinstall, or fixture replacement is counted
   as upgrade success.

The prior plan-save real-device fresh reinstall is useful product evidence but
is not this preserved-data gate.

## 17. Private real-database validation policy

Actual private real-database validation is optional and requires a separate,
explicit authorization. This design does not grant it. If later authorized:

1. stop the source app and obtain a consistent offline database plus required
   sidecars through an approved private extraction process;
2. retain an immutable original backup and a second safety backup;
3. record original SHA-256 privately;
4. work only on a separate copy and never mutate the sole original;
5. run read-only structural scan first, with sensitive output stored privately;
6. migrate only a disposable copy through the official Android migration;
7. run `integrity_check`, `foreign_key_check`, row-count and critical-field
   comparison, schema identity verification, reopen, and full Repository reads;
8. run app startup only if separately authorized on a private test target;
9. delete disposable copies according to the private handling plan while
   retaining the immutable backup until acceptance;
10. report only sanitized pass/fail, aggregate counts, hashes reduced to the
    approved evidence scope, and tool/build versions.

No real database, derivative fixture, WAL/SHM file, manifest, audit JSONL,
screenshot, raw log, UUID, medication name, dose, or timestamp may enter Git,
test resources, CI, issue trackers, or this report.

## 18. Privacy and evidence handling

Public and CI evidence is synthetic only. Private evidence must use access-
controlled local storage, least-privilege handling, and a documented deletion
plan. Command output must be sanitized before inclusion in a report.

The app currently has `allowBackup=true`, but both backup rule formats exclude
root, files, databases, preferences, external storage, and device-protected
equivalents (`backup_rules.xml:4-12`, `data_extraction_rules.xml:5-25`). Batch 8
must verify those rules on target APIs and must not assume Android cloud/device
transfer protects the database. Backup/recovery evidence therefore uses an
explicit private/offline process, not platform auto-backup.

Release evidence must be reproducible without exposing health data: commit/tag,
artifact hash, schema hashes, test XML totals, device properties, sanitized
database counts, and signed review decisions are sufficient.

## 19. Device validation matrix

| Target | Fresh v3 | Preserved v2 -> v3 | Failure rollback | UI/reopen | Required |
|---|---:|---:|---:|---:|---:|
| API 33 phone AVD | yes | yes | yes | yes | yes |
| API 35 phone AVD | yes | yes | yes | yes | yes |
| Foldable phone AVD | yes | targeted | targeted | folded/unfolded | yes |
| Samsung SM-S918B or equivalent private phone | optional fresh smoke | optional, separately authorized synthetic/private | no destructive operation | startup/editor | explicit authorization |
| Wear OS AVD | companion protocol/repository regression | not a phone DB substitute | replay/failure handling | Wear flow | yes |

Device serial, model, Android version, API level, package ID, test totals,
failures, errors, skips, and XML/HTML report paths must be recorded from the
actual run. AndroidTest compilation is never reported as connected execution.
Wear OS AVD is not used as a phone UI migration target.

## 20. Regression matrix

Every implementation sub-batch and final gate must run the narrowest relevant
tests first and then the complete gate:

- migration parser/primitives and all mapper tests;
- all migration, baseline, matrix, and Repository connected tests;
- production cutover tests for plan, event, receiver/Widget, Wear, provider,
  JSON import/export, and plan-save regression;
- full App JVM and full connected suites on API 33 and API 35;
- complete PK tests and locked numerical parity;
- Wear JVM and connected/companion validation where supported;
- App/Wear assemble, App androidTest compilation, lint, and release-candidate
  shrink/obfuscation checks;
- APK 16KB page-alignment verification;
- explicit search proving no destructive fallback, second DB, migration repair,
  schema drift, Domain/contract drift, or legacy-column removal.

Counts must distinguish suites, tests, failures, errors, skips, and assertions.
Historical results may support context but do not replace a final run from the
Batch 8 candidate commit.

## 21. Schema identity gates

At each sub-batch boundary and final authorization:

1. regenerate Room schema through KSP rather than hand-editing JSON;
2. compare tracked schema 2 and schema 3 against Git blobs;
3. verify identities and canonical SHA-256 values from section 5;
4. require zero schema-2 diff and zero unintended schema-3 diff;
5. verify `AppDatabase` remains version 3 with `exportSchema = true` and both
   migrations registered;
6. prove no `MIGRATION_3_4`, reverse migration, or destructive fallback was
   introduced.

Any need to change v3 schema is a design stop, not an implementation shortcut.

## 22. Legacy compatibility window

`timeH` and `timeOfDay` remain present and correctly dual-written for at least
one formal 1.0 release cycle. Batch 8 validates their equality and does not
schedule removal. Legacy cleanup is a future post-release schema migration with
its own design, evidence, and rollback analysis.

Zone/date metadata for migrated events remains unknown (`null`); migration must
not infer it from the migration device. Slot ID v1 namespace, canonical name,
UTF-8 encoding, version prefix, positions, and fixed vectors remain immutable.

## 23. Rollback / downgrade policy

Migration is forward-only. No v3 -> v2 Room migration is designed or implied.

- If migration fails, transaction rollback leaves v2 and the application enters
  the bounded recovery state.
- The previous signed app/build must be retained for controlled private support,
  but normal platform downgrade constraints mean it is not the sole end-user
  recovery mechanism.
- If migration succeeds, rollback depends on an immutable pre-upgrade backup or
  platform release rollback strategy that preserves compatibility; it does not
  reinterpret v3 as v2.
- Uninstall, data clear, destructive rebuild, and automatic fresh-start are not
  rollback.

Before authorization, 8E must approve a release runbook covering staged rollout,
halt criteria, support escalation, backup recommendation, failed-upgrade retry,
and recovery evidence. The runbook must not promise platform auto-backup because
the database is excluded.

## 24. Release authorization matrix

| Gate | Required evidence | Blocking severity if missing/failing |
|---|---|---|
| Locked schema identities/hashes | independent v2/v3 regeneration/blob check | P0 on mismatch |
| Complete migration-readability contract | field matrix plus every migrated row Repository-readable | P1 |
| Strict invalid-data rejection | adversarial tests before data writes | P1 |
| Atomic rollback | phase fault matrix, v2 schema/data/user_version intact | P0/P1 |
| Synthetic matrix | section 13, including NOT REPRESENTABLE labels | P1 |
| Fresh v3 | API 33/35 create/reopen/Repository CRUD | P1 |
| Preserved installed-app upgrade | API 33/35 install-over with retained synthetic data | P1 |
| Repository reopen | full event and aggregate reads after migration/restart | P1 |
| Device coverage | API 33, API 35, foldable target, separate Wear OS target | P1 |
| App/PK/Wear regressions | full green suites and numerical parity | P1 |
| Build/lint/alignment | App/Wear builds, lint, shrink, 16KB check | P1 |
| No destructive fallback/second DB | static audit and runtime failure proof | P0 |
| Repair separation | no runtime invocation; independently qualified private tool | P1 |
| Privacy/backup rules | exclusion verification and sanitized evidence audit | P1 |
| Private real-DB policy | approved workflow; execution only if separately authorized | P2 if no execution, P1 if policy absent |
| Recovery/downgrade runbook | bounded failure behavior and forward-only policy | P1 before release authorization |
| Surviving risks | P0 = 0, P1 = 0, reviewed P2 list | P1 while unresolved blocker exists |

Only `P0 = 0`, `P1 = 0`, all mandatory gates passing, an implementation report,
and an independent approval may propose Room v3 release authorization.

## 25. Batch 7 P2 disposition

| Batch 7 surviving P2 | Database release-gate relevant? | Batch 8 treatment |
|---|---:|---|
| Random UUID replay caveat | No | retain documented JSON v1 behavior |
| Numeric-ID strict compatibility boundary | No | retain documented JSON v1 behavior |
| JSON v1 metadata projection limitation | No | protocol limitation; no schema change |
| ADR-015 Route/Ester ownership | Yes | validate all persisted tokens and Repository readability; do not move enums |
| HRT failure folding | No | retain product P2; recovery diagnostics stay sanitized |
| Broad `RuntimeException` import catch | No | retain operational P2; not migration authorization evidence |
| Wear `plan_id` replay limitation | No | retain protocol P2; no Wear protocol change |
| Instrumentation wrapper count maintenance | No | report actual XML totals; maintenance remains separate |

The ADR-015 item is not itself a blocker because the transitional dependency is
accepted. The newly identified P1 is that migration does not yet prove every
legacy stored token and converter payload satisfies that accepted mapper
boundary.

## 26. Proposed Batch 8 sub-batches

### 8A - Migration contract and adversarial synthetic matrix

- Finalize the executable valid/invalid v2 field contract from section 8.
- Add synthetic fixture builders and tests for every non-time mapper/converter
  failure domain and full Repository readability.
- Mark platform-impossible fixtures explicitly.
- Expected files: migration test utilities, JVM/connected tests, and an 8A
  report/review; no schema or production behavior change.
- Stop if compatibility requires Domain, Repository, schema, Slot ID, JSON, or
  PK changes.

### 8B - Complete strict preflight and atomicity gate

- Extend migration validation so all future Repository failures are detected in
  complete preflight before UPDATE/INSERT.
- Preserve DDL order, outer transaction ownership, defaults, and post-checks.
- Add deterministic phase-failure and Repository-reopen connected evidence.
- Expected files: migration primitives/errors, `AppDatabaseMigrations.kt`,
  narrow tests, and an 8B report/review; schemas remain byte-identical.
- Stop if mapper validation must be weakened or any repair/fallback is needed.

### 8C - Fresh and preserved-data installed-app validation

- Produce traceable historical-v2 and candidate-v3 internal test artifacts.
- Execute API 33/API 35 fresh and install-over matrices, plus foldable and Wear
  targets, with synthetic data only.
- Validate bounded failure behavior, restarts, full Repository reads, regressions,
  lint/build/shrink/alignment, and evidence provenance.
- Expected changes should be test infrastructure, release-safety surface only if
  separately authorized, and an 8C report/review; no feature work.

### 8D - Repair qualification and private-validation process

- Exercise the existing repair toolkit on actual Python 3.12 with synthetic
  data and inspect privacy/immutability behavior.
- Rehearse scan -> explicit copy repair -> verify -> official Android migration
  -> Repository-read workflow using synthetic fixtures.
- Freeze the private real-DB procedure. Actual real data remains optional and
  needs separate explicit authorization.
- Expected files: tool tests/docs and an 8D report/review only if later
  authorized; no app runtime integration.

### 8E - Release authorization and Phase 1 exit gate

- Aggregate sealed 8A-8D evidence, rerun the complete candidate matrix, audit
  P0/P1/P2, schema hashes, backup/privacy, and recovery runbook.
- Obtain independent final review.
- May conclude `AUTHORIZED` or `NOT AUTHORIZED`; it does not publish anything.
- Expected files: closure evidence and review only. Actual release, versioning,
  signing, publishing, and GitHub/Play operations need a later explicit request.

Each sub-batch is narrow, independently reviewable, and stops before the next
one until its evidence is sealed.

## 27. Stop conditions

Stop and report without inventing a fix if any of the following occurs:

- schema 2 or 3 differs from its locked identity/hash;
- ADR-016 order, strict time semantics, outer transaction, or repair separation
  cannot be preserved;
- successful migration cannot guarantee every production Repository read;
- a valid v2 value cannot be migrated losslessly;
- required compatibility needs a Domain, Repository contract, schema, migration
  version, JSON v1, PK, Wear protocol, Route/Ester ownership, or Slot ID change;
- destructive fallback, silent correction, row loss, current-time/zone inference,
  second database, or automatic repair is proposed;
- private validation would require committing, uploading, logging, or exposing
  real health data;
- release authorization would require a new ADR not already approved;
- any P0 or P1 remains unresolved or a mandatory gate lacks authoritative
  evidence.

Current audit does not trigger the schema/ADR stop conditions. It does trigger
the section 7 P1 release blocker, which 8A/8B must resolve under separate
implementation authorization.

## 28. Release boundary

`Migration/release gate APPROVED` means evidence is sufficient to authorize a
future Room v3 release operation. It does not mean a release occurred.

Batch 8 must not automatically:

- bump `versionCode` or `versionName`;
- change application IDs, signing, backup rules, or release configuration;
- publish Git tags, GitHub releases, Play releases, APKs, or AABs;
- migrate a user's database;
- execute the private repair/validation workflow.

The current build configuration remains `versionCode = 10060`, git-derived
`versionName`, minified release with `.release` application ID suffix, and debug
with `.debug`. Any actual release requires a new explicit authorization after
8E approval.

## 29. Deferred product features and design risks

The feature-freeze list in section 4 remains outside Batch 8.

Design audit risk count is `P0/P1/P2 = 0/1/4`:

- **P1:** migration success does not currently guarantee Repository readability
  for all non-time legacy fields and converter payloads.
- **P2:** no private real-database validation has been performed; execution is
  optional and separately authorized.
- **P2:** Python 3.12 compatibility is documented but has not been independently
  evidenced on an actual 3.12 runtime in the sealed history reviewed here.
- **P2:** a final user-facing failed-upgrade recovery and forward-only rollback
  runbook is not yet sealed.
- **P2:** no trusted historical Room `1.json` exists; v1 coverage uses the
  documented minimal authorized synthetic v1 -> v2 -> v3 chain, while v2 is the
  formal compatibility baseline.

These are design findings, not authorization to modify implementation. Room v3
remains internal and unreleasable while the P1 or any mandatory gate remains.

## 30. Decision

Batch 8 design complete pending independent design review.
