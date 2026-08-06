# Evolune Phase 1 Batch 6C Prerequisite Replay Policy Report

Date: 2026-08-06

Status: implementation complete and validated, pending independent read-only review.

## 1. Scope

This prerequisite batch replaces the shared implicit source-only replay classification with explicit application replay policies. It preserves the sealed Batch 6B Notification and Widget behavior and adds a typed, disconnected Wear application boundary for the later Batch 6C cutover.

Implemented scope:

- explicit `RepositoryStrict`, `FirstAcceptedBySource`, and `FirstAcceptedBySourceAndOccurredAt` policies;
- explicit `Inserted`, `RepositoryIdempotent`, and `FirstAcceptedReplay` accepted reasons;
- `LocalActionRecorder` for trusted Notification and Widget identities;
- `WearActionRecorder` for source `WEAR` plus exact `recordedAt` matching;
- one shared application replay engine for pre-read, insert, conflict re-read, and failure classification;
- JVM, static boundary, disposable Room v3, concurrency, and regression tests.

Excluded scope:

- no Wear production listener or `WearDataLayer` cutover;
- no DataItem deletion or acknowledgement implementation;
- no Repository contract, Room Repository, Domain, DAO, Entity, schema, or migration change;
- no Wear payload, path, or key change;
- no JSON v1, PK, Gradle, Manifest, UI, or ViewModel change;
- no real or real-derived data and no production database access;
- no Batch 6C implementation, release, staging, commit, or tag.

## 2. Stop history and authority

Batch 6C stopped twice before this prerequisite implementation:

1. source-only matching could accept the same ID and source with a different occurrence as replay;
2. Repository full-content equality could not safely classify Wear retry materialized after a plan edit.

The authoritative resolution is:

- `docs/phase-reports/PHASE_1_BATCH_6_REPLAY_POLICY_ADDENDUM.md`;
- `reviews/PHASE_1_BATCH_6_REPLAY_POLICY_ADDENDUM_REVIEW.md`;
- tag `phase-1-batch-6-replay-policy-design-v1`;
- independent design verdict `APPROVE WITH P2`.

The implementation keeps Repository full-content equality authoritative and separates it from application command deduplication.

## 3. Changed files

| File | Status | Responsibility |
|---|---|---|
| `app/src/main/java/io/github/yuninggu/evolune/application/RecordDoseEventAction.kt` | Modified | Shared replay engine, explicit policies, result classification, race re-read, and failure mapping |
| `app/src/main/java/io/github/yuninggu/evolune/application/LocalActionRecorder.kt` | Added | Trusted reminder and Widget facade with internally derived deterministic IDs |
| `app/src/main/java/io/github/yuninggu/evolune/application/WearActionRecorder.kt` | Added | Disconnected Wear facade restricted to source plus exact occurrence policy |
| `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderReceiverWork.kt` | Modified | Notification action selects `LocalActionRecorder` and preserves 6B side effects |
| `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetWork.kt` | Modified | Widget action selects `LocalActionRecorder` and preserves minute-key behavior |
| `app/src/test/java/io/github/yuninggu/evolune/application/RecordDoseEventActionTest.kt` | Modified | Policy, result, race, failure, and concurrency JVM coverage |
| `app/src/test/java/io/github/yuninggu/evolune/application/ReplayPolicyBoundaryTest.kt` | Added | Static typed-facade and disconnected-Wear boundary coverage |
| `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/ReceiverWidgetProductionCutoverTest.kt` | Modified | Disposable file-backed Room v3 replay-policy and concurrency regression |

No changed file contains sensitive, real, or real-derived data. All UUIDs, plans, times, and database rows are synthetic.

## 4. API structure

### 4.1 Shared replay engine

`RecordDoseEventEngine` centralizes:

- explicit policy selection with no default;
- plan lookup where first materialization requires it;
- existing-event pre-read for first-accepted policies;
- candidate identity validation;
- one Repository insert;
- at most one re-read after insert conflict;
- storage, invalid, conflict, cancellation, and unexpected failure handling.

It is an app-module internal implementation. Production feature entry points use the typed recorders rather than selecting a policy directly. `CancellationException` is rethrown; storage failures and unexpected failures are not accepted as replay.

### 4.2 Typed facades

`LocalActionRecorder` exposes only reminder and Widget operations. It derives the deterministic local event ID internally and always selects `FirstAcceptedBySource` with the locked local source.

`WearActionRecorder` exposes only a Wear operation. It fixes the expected source to `WEAR`, requires an explicit `Instant recordedAt`, and always selects `FirstAcceptedBySourceAndOccurredAt`. It has no source-only method.

Wear production files do not reference either recorder or the policy engine in this prerequisite batch.

## 5. Result model

Accepted results remain distinguishable:

| Acceptance | Meaning |
|---|---|
| `Inserted` | Repository inserted the candidate |
| `RepositoryIdempotent` | Repository proved complete Domain equality |
| `FirstAcceptedReplay` | The selected application policy recognized an earlier accepted command |

Rejected and failure results remain distinct: `PlanNotFound`, `PlanDisabled`, `Conflict`, `Invalid`, `StorageFailure`, and `UnexpectedFailure`.

Only the three accepted reasons permit existing success side effects. `FirstAcceptedReplay` is never relabelled as Repository idempotency.

## 6. RepositoryStrict

`RepositoryStrict` performs no event pre-read. It constructs and validates the candidate, calls `insert` once, maps `Inserted`, `Idempotent`, `Conflict`, and `Invalid` directly, and does not reinterpret conflict through a re-read. It has no fallback or replacement-ID behavior.

No current production entry point selects this policy. Its behavior is locked by focused engine tests.

## 7. Local first-accepted behavior

### 7.1 Notification

- Event ID remains `UUID.nameUUIDFromBytes(UTF8("reminder:<planId>:<scheduledAtMillis>"))`.
- Expected source remains `REMINDER`.
- The first processing time remains the stored `occurredAt`.
- A delayed duplicate with another retry-time occurrence returns `FirstAcceptedReplay` and preserves the first row.
- A source collision returns `Conflict` with no recorded-dose success side effect.
- `Inserted`, `RepositoryIdempotent`, and `FirstAcceptedReplay` preserve the existing Widget refresh and notification cancellation sequence.
- Stale or disabled plan handling and receiver lifecycle behavior remain the sealed Batch 6B behavior.

### 7.2 Widget

- Event ID remains `UUID.nameUUIDFromBytes(UTF8("widget:<planId>:<epochMinute>"))`.
- Expected source remains `WIDGET`.
- The first precise millisecond remains authoritative within the existing minute key.
- A later same-minute action returns `FirstAcceptedReplay` and does not overwrite the first row.
- A source collision returns `Conflict` with no refresh or recorded feedback.
- Accepted outcomes preserve the existing refresh and recorded feedback behavior.
- PK time precision, persistence precision, Widget protocol, and enabled-plan rule remain unchanged.

The outward `replayed` Boolean remains compatible with Batch 6B: it is false only for `Inserted`, and true for both Repository idempotency and first-accepted replay.

## 8. Wear recorder boundary

The disconnected Wear boundary applies these rules:

1. use the watch-owned action ID unchanged as the event ID;
2. read by action ID before plan lookup or candidate materialization;
3. accept an existing row only when `source == WEAR` and `occurredAt == recordedAt`;
4. return `Conflict` for a source or occurrence mismatch;
5. read the plan and invoke the materializer only when no event exists;
6. require an enabled plan for first materialization;
7. require the materialized event ID, source, and occurrence to match the action inputs;
8. map Repository idempotency separately from first-accepted replay;
9. re-read at most once after an insert conflict.

The focused JVM and disposable Room tests use plan Repository and materializer counters. Legal replay and existing-event conflict both assert zero plan reads and zero materializer calls. Only a new action invokes first materialization.

This batch does not parse Wear payloads, delete DataItems, or connect `WearActionRecorder` to production Wear code.

## 9. Concurrency

Synthetic JVM and disposable Room tests cover:

- concurrent identical Wear actions: one authoritative row and two accepted outcomes;
- concurrent same action ID with different occurrences: one authoritative row and one conflict;
- Repository atomic insert as the persistence guard;
- one re-read after insert conflict;
- no replacement UUID, overwrite, retry insert, global permanent lock, or fallback.

## 10. Disposable Room validation

`ReceiverWidgetProductionCutoverTest` uses a uniquely named disposable file-backed Room v3 database. Setup and teardown delete the database and its WAL, SHM, and journal sidecars. The production `evolune_database` is never opened.

The four focused tests verify:

- Notification and Widget first insert and delayed replay;
- original event field preservation;
- source collisions and zero success side effects;
- Wear source-plus-occurrence replay with zero plan/materializer calls;
- Wear occurrence conflict with zero plan/materializer calls;
- Repository full-content `Idempotent` versus `Conflict` behavior;
- concurrent same and conflicting Wear actions;
- `user_version = 3` and disposable database cleanup.

## 11. Validation results

All commands below completed successfully. Test commands used rerun execution, and connected tests ran on real emulator processes rather than compile-only validation.

| Validation | Suites | Tests | Failures | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Replay-policy JVM | 2 | 18 | 0 | 0 | PASS |
| Batch 6B JVM | 7 | 37 | 0 | 0 | PASS |
| Batch 6A JVM | 3 | 27 | 0 | 0 | PASS |
| Batch 5 JVM | 3 | 35 | 0 | 0 | PASS |
| Migration JVM | 3 | 43 | 0 | 0 | PASS |
| Mapper JVM | 6 | 53 | 0 | 0 | PASS |
| Core JVM | 5 | 47 | 0 | 0 | PASS |
| PK JVM | 5 | 49 | 0 | 0 | PASS |
| Full App JVM | 40 | 344 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | PASS |
| API 33 focused connected | 1 | 4 | 0 | 0 | PASS |
| API 33 full connected | actual run | 93 | 0 | 0 | PASS |
| API 35 focused connected | 1 | 4 | 0 | 0 | PASS |
| API 35 full connected | actual run | 93 | 0 | 0 | PASS |
| API 35 Repository/Migration connected | actual run | 75 | 0 | 0 | PASS |

Connected device matrix:

| AVD | Serial | Android | API | Result |
|---|---|---:|---:|---|
| `Evolune_API33_Migration` | `emulator-5554` | 13 | 33 | Focused and full PASS |
| `Pixel_7` | `emulator-5554` | 15 | 35 | Focused, full, and Repository/Migration PASS |

The two AVDs ran sequentially on the same emulator serial. The API 35 device reported model `sdk_gphone64_x86_64`.

Additional validation:

| Command | Result | Notes |
|---|---|---|
| `:app:assembleDebug` | PASS | Gradle completed; previously validated task outputs were up-to-date |
| `:wear:assembleDebug` | PASS | Gradle completed; previously validated task outputs were up-to-date |
| `:app:lintDebug` | PASS | 0 errors, 81 warnings, 1 hint |
| `:app:compileDebugAndroidTestKotlin` | PASS | androidTest Kotlin compiled successfully; not counted as device execution |
| `:app:kspDebugKotlin` | PASS | KSP and Room schema generation completed |

Result artifacts:

- App JVM XML: `app/build/test-results/testDebugUnitTest/`;
- Wear JVM XML: `wear/build/test-results/testDebugUnitTest/`;
- connected XML: `app/build/outputs/androidTest-results/connected/debug/`;
- connected HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- lint: `app/build/reports/lint-results-debug.html`.

## 12. Batch 6B regression

Notification and Widget behavior remains green across JVM and connected tests:

- first acceptance runs the sealed success side effects;
- delayed Notification and same-minute Widget deliveries are accepted replay;
- the first stored event remains unchanged;
- source collision, invalid input, storage failure, and unexpected failure run no success side effect;
- receiver `goAsync` ownership and `PendingResult.finish()` exactly-once behavior are unchanged;
- receiver and Widget IDs, metadata, and protocols are unchanged;
- there is no direct DAO, Entity, legacy Repository, or fallback writer path introduced.

## 13. Schema and architecture boundaries

Room remains version 3 with `exportSchema = true`. It remains an internal, non-releasable Phase 1 database version.

| Schema | Identity hash | Canonical Git blob SHA-256 | Change |
|---|---|---|---|
| v2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | None |
| v3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | None |

Verified unchanged:

- `MIGRATION_2_3` and migration primitives;
- Repository contracts and `RoomDoseEventRepository` full-content equality;
- Domain models, DAO, Entity, and schema files;
- Wear production code, module, payload, path, and keys;
- Notification/Widget ID algorithms, protocols, and receiver lifecycle;
- JSON v1, PK behavior and tolerance, Gradle, Manifest, UI, and ViewModel.

## 14. Known transitional risk

Risk status: **P0/P1/P2 = 0/0/1**.

The sole accepted P2 remains unchanged: after a Wear action has been accepted, replay cannot revalidate the original payload `plan_id`. The current persisted `DoseEvent` has no plan ID, and the current protocol does not persist an independent first-action plan identity. Therefore `plan_id` is a first-materialization input only; accepted replay authority is the stored action ID plus source `WEAR` plus exact `recorded_at`.

This limitation is not represented as full action-authenticity validation. Protocol versioning must be reconsidered before the Batch 8 release gate.

## 15. Decision

Batch 6C prerequisite replay-policy implementation passed pending independent review.

The implementation is ready for DeepSeek independent read-only review. Batch 6C has not started and remains prohibited until this prerequisite implementation is independently approved, committed, and tagged. Wear production cutover and DataItem deletion acknowledgement remain future Batch 6C work.

Room v3 remains internal and must not be released.
