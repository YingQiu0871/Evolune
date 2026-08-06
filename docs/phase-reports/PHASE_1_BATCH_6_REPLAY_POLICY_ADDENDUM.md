# Evolune Phase 1 Batch 6 Replay Policy Addendum

Date: 2026-08-06

Status: design-only addendum. No production or test code is implemented by this document.

## 1. Purpose and authority

This addendum resolves the replay-policy conflict discovered after Batch 6B was sealed and before Batch 6C implementation began. It supplements the Batch 6 design without rewriting the original design, the Batch 6A or 6B reports, or their independent reviews.

The two stop findings were:

1. `RecordDoseEventAction` currently treats an existing event with the expected source as replay without checking `occurredAt` or other content. The same weak classification is repeated after an insert conflict. This can misclassify the same ID and source with different content as accepted replay.
2. Repository full-content idempotency cannot be used as the only Wear replay rule. The Wear payload contains only `plan_id`, `action_id`, and `recorded_at`; route, dose, ester, and extras are materialized from the phone's current editable plan. After a plan edit, a retry cannot reconstruct the first persisted candidate, and `DoseEvent` does not retain `planId`.

This is not a defect in `RoomDoseEventRepository`. Its insert behavior remains authoritative for persistence equality:

- same ID and complete Domain content: `InsertResult.Idempotent`;
- same ID and any different persisted content: `InsertResult.Conflict`;
- a conflicting row is never overwritten.

The conflict came from treating two different concepts as one:

- **Repository idempotency** asks whether a candidate event is completely equal to the stored event.
- **Application command deduplication** asks whether a previously accepted logical command should be recognized again even when retry-time materialization cannot reproduce every persisted field.

Repository equality remains unchanged. Application command deduplication must use an explicit entry-specific policy and must report its result separately from Repository idempotency.

## 2. Locked principles

1. Every record operation selects an existing-event policy explicitly. There is no default policy.
2. Repository results are never silently weakened or re-labelled.
3. `FirstAcceptedReplay` is an application command result, not `InsertResult.Idempotent`.
4. Notification and Widget retain their sealed Batch 6B first-accepted behavior.
5. Wear does not use the Notification/Widget source-only policy.
6. Wear checks both the watch-owned action ID and the stable watch-provided occurrence time.
7. No policy overwrites an existing event, generates a replacement ID, or falls back to a legacy writer.
8. Conflict, invalid input, storage failure, and infrastructure exceptions never trigger recorded-dose success side effects.
9. Repository contracts, Domain models, Room Repository equality, DAO, Entity, schema, migration, and Wear protocol remain unchanged.

## 3. Explicit existing-event policies

### 3.1 `RepositoryStrict`

`RepositoryStrict` applies only when the complete candidate can be reconstructed identically for every retry.

Flow:

1. Do not pre-read by ID.
2. Construct and validate the complete candidate.
3. Call `DoseEventRepository.insert(candidate)` once.
4. Map `InsertResult.Inserted` to `Inserted`.
5. Map `InsertResult.Idempotent` to `RepositoryIdempotent`.
6. Map `InsertResult.Conflict` to `Conflict` without re-reading or reinterpreting it.
7. Map `InsertResult.Invalid` to `Invalid`.
8. Classify storage and infrastructure exceptions as failures; never as replay.

Current Wear actions do not use `RepositoryStrict`, because their complete persisted plan snapshot is not present in the protocol.

### 3.2 `FirstAcceptedBySource`

`FirstAcceptedBySource` is restricted to trusted local deterministic identities:

- Notification scheduled occurrences with expected source `REMINDER`;
- Widget minute actions with expected source `WIDGET`.

It must not accept an arbitrary externally supplied action ID and must not be available as the Wear handler's recorder dependency.

Flow:

1. Derive the event ID from the approved local action kind and its locked inputs.
2. Read the existing event by that ID.
3. If an event exists with the expected local source, return `FirstAcceptedReplay` with the stored event.
4. If an event exists with another source, return `Conflict`.
5. If no event exists, construct and validate the candidate and call `insert` once.
6. Map `Inserted` and `Idempotent` without relabelling them.
7. After `InsertResult.Conflict`, re-read once. Matching expected local source becomes `FirstAcceptedReplay`; any other or missing row remains `Conflict`.
8. Never compare a retry-time `occurredAt` to the first accepted local event, and never overwrite the first event.

This policy implements local command deduplication. It is intentionally weaker than Repository full-content equality and is safe only because the recorder itself derives a trusted local deterministic ID for a specific local action kind.

### 3.3 `FirstAcceptedBySourceAndOccurredAt`

`FirstAcceptedBySourceAndOccurredAt` is the only policy approved for Wear under the current protocol.

Its required parameters are explicit and have no defaults:

- expected source: `DoseEventSource.WEAR`;
- expected occurrence: `Instant.ofEpochMilli(recorded_at)`.

Flow:

1. Parse and validate `action_id` and `recorded_at`.
2. Use `action_id` unchanged as `DoseEvent.id`.
3. Read the existing event before reading the plan.
4. If an event exists, accept it as `FirstAcceptedReplay` only when `source == WEAR` and `occurredAt == expectedOccurredAt`.
5. Any existing event with another source or occurrence is `Conflict`.
6. Only when no event exists, parse and validate `plan_id`, read the current plan, require the approved enabled-plan state, and materialize the first candidate from that plan and `recorded_at`.
7. Call `insert` once.
8. Map `Inserted` and `Idempotent` directly.
9. After `InsertResult.Conflict`, re-read once and apply the same source-and-occurrence rule. A match becomes `FirstAcceptedReplay`; otherwise the result remains `Conflict`.
10. Never rebuild a replay candidate from an edited current plan.

Wear must not use source-only matching. It must not generate a new ID after conflict, reinterpret conflict as Repository idempotency, or delete a failed action DataItem.

## 4. Wear action identity and known protocol limit

The watch remains the owner of both stable action fields:

- `action_id` is one random UUID generated for the tap and is persisted unchanged as `DoseEvent.id`;
- `recorded_at` is generated once for that action and is stable across DataItem redelivery.

For application command deduplication, Wear action identity is `action_id`, with `recorded_at` as the stable consistency check. `plan_id` is a first-materialization input, not part of replay identity under the current protocol. Once the first event is persisted, that stored event is the authoritative materialized result and later plan edits do not alter replay classification.

The current payload and `DoseEvent` model cannot prove the original `plan_id` during replay. Therefore:

- the first attempt must validate `plan_id` and read the corresponding current plan;
- an existing accepted event is evaluated before any plan read;
- matching source and `recorded_at` make the stored event authoritative;
- the protocol cannot detect the same `action_id` and `recorded_at` replayed with another `plan_id`.

This is a known **P2 protocol limitation**, not complete action-authenticity validation. It is not hidden by changing Repository equality, adding a schema field, or modifying the current payload in Batch 6. Protocol versioning can be reconsidered before the Batch 8 release gate.

Malformed or missing `action_id` or `recorded_at` is rejected without a database write, random replacement, DataItem deletion, or success report.

## 5. Entry-specific rules

### 5.1 Notification

- Policy: `FirstAcceptedBySource`.
- Local action kind: reminder scheduled occurrence.
- ID: `UUID.nameUUIDFromBytes(UTF8("reminder:<planId>:<scheduledAtMillis>"))`.
- Expected source: `REMINDER`.
- `occurredAt`: actual processing time of the first accepted confirmation.
- Delayed duplicate delivery preserves the first persisted event and returns `FirstAcceptedReplay`.
- `Inserted`, `RepositoryIdempotent`, and `FirstAcceptedReplay` permit the existing accepted notification side effects.
- Another-source collision is `Conflict` and runs no recorded-dose success side effect.

### 5.2 Widget

- Policy: `FirstAcceptedBySource`.
- Local action kind: Widget plan/minute action.
- ID: `UUID.nameUUIDFromBytes(UTF8("widget:<planId>:<epochMinute>"))`.
- Expected source: `WIDGET`.
- The existing product rule folds repeated delivery and repeated taps within one minute.
- The first accepted precise `occurredAt` remains authoritative and is never replaced by a later millisecond.
- `Inserted`, `RepositoryIdempotent`, and `FirstAcceptedReplay` permit Widget refresh and recorded feedback.
- Another-source collision is `Conflict` and runs no success side effect.

This is command deduplication at the existing product minute key. It does not change PK time precision or persistence precision.

### 5.3 Wear

- Policy: `FirstAcceptedBySourceAndOccurredAt`.
- ID: watch-provided `action_id`, unchanged.
- Expected source: `WEAR`.
- Expected occurrence: exact `Instant.ofEpochMilli(recorded_at)`.
- Existing-event classification happens before plan lookup.
- First materialization uses the validated current Domain plan and stable `recorded_at`.
- Accepted persistence or accepted replay permits deletion of the corresponding DataItem URI.
- Conflict or failure retains the DataItem.

## 6. Result model and side effects

The application result must distinguish these outcomes explicitly:

| Result | Meaning | Success side effects allowed |
|---|---|---|
| `Inserted` | Repository inserted the candidate | Yes |
| `RepositoryIdempotent` | Repository proved complete candidate equality | Yes |
| `FirstAcceptedReplay` | Entry policy recognized a previously accepted logical command | Yes |
| `Conflict` | Existing identity is not accepted by the selected policy | No |
| `Invalid` | Input or candidate is invalid | No |
| `StorageFailure` | Persistence infrastructure failed | No |
| `UnexpectedFailure` | Non-storage infrastructure failure | No |
| `PlanNotFound` / `PlanDisabled` | First materialization cannot use the requested plan | No |

`FirstAcceptedReplay` must carry the stored authoritative event. It must never be logged, reported, or tested as Repository idempotency.

Success side effects run only after one of the three approved accepted outcomes. A side-effect failure does not roll back an already persisted event and does not trigger a second insert or legacy fallback.

## 7. DataItem acknowledgement and cleanup

The protocol has no independent acknowledgement path or acknowledgement fields. Deleting the exact source DataItem URI is the only successful acknowledgement and cleanup.

Deletion is permitted only after:

- `Inserted`;
- `RepositoryIdempotent`;
- legal Wear `FirstAcceptedReplay` under source-and-occurrence matching.

The DataItem is retained after:

- `Conflict`;
- `Invalid`, including malformed identifiers or time;
- `PlanNotFound` or `PlanDisabled`;
- `StorageFailure`;
- unexpected infrastructure exception;
- in-process cancellation before an accepted result.

DataItem delivery completion, Repository acceptance, DataItem deletion completion, and listener lifecycle completion are distinct states. If deletion fails after accepted persistence, the event remains committed. A later redelivery of the same action is recognized as `FirstAcceptedReplay` and may retry deletion. No new ack field, path, message, or storage format is introduced.

## 8. Concurrency and race handling

### 8.1 Same Wear action delivered concurrently

Two handlers may both pre-read no existing event. Both may materialize a candidate and call `insert`:

- one insert succeeds;
- the other may receive `RepositoryIdempotent` if the complete candidates match;
- if the other receives `Conflict`, it re-reads exactly once;
- source `WEAR` plus the same `recorded_at` becomes `FirstAcceptedReplay`;
- any other source or occurrence remains `Conflict`.

The Repository remains the final atomic persistence guard. A global permanent lock is not required for correctness. A bounded action-level gate may reduce duplicate concurrent work, but its cancellation or failure must release the gate and Repository semantics remain authoritative.

### 8.2 Same action ID with different occurrence

The first accepted event remains unchanged. Any later action with the same ID and a different `recorded_at` is `Conflict`, regardless of source. It is not acknowledged or deleted and never overwrites the row.

### 8.3 Local insert races

Notification and Widget use the same one-read-after-conflict rule under `FirstAcceptedBySource`. The re-read may return `FirstAcceptedReplay` only for the expected trusted local source. Otherwise it remains `Conflict`.

## 9. API alternatives and recommendation

### 9.1 Option A: generic `execute(candidate, replayPolicy)`

Advantages:

- one action surface;
- one centralized policy engine;
- minimal structural change.

Disadvantages:

- a Wear caller can accidentally select source-only policy;
- trusted local identity derivation is easier to bypass with an externally supplied ID;
- result handling can collapse distinct accepted reasons if represented only by a Boolean.

Option A is acceptable only with no default policy, restricted policy constructors, explicit accepted-reason results, and static boundary tests. It is not the preferred public call-site shape.

### 9.2 Option B: typed local and Wear recorders over one private policy engine

Recommended.

Conceptual structure:

```kotlin
private sealed interface ExistingEventPolicy {
    data object RepositoryStrict : ExistingEventPolicy

    data class FirstAcceptedBySource(
        val expectedSource: DoseEventSource
    ) : ExistingEventPolicy

    data class FirstAcceptedBySourceAndOccurredAt(
        val expectedSource: DoseEventSource,
        val expectedOccurredAt: Instant
    ) : ExistingEventPolicy
}

internal class LocalActionRecorder { /* REMINDER or WIDGET identity only */ }

internal class WearActionRecorder { /* WEAR source + required recordedAt only */ }
```

Both recorders delegate equality, insert-result mapping, exception classification, and one-time conflict re-read to one private engine. The engine has no default policy.

The local recorder accepts a closed trusted local action kind (`REMINDER` or `WIDGET`) and derives its deterministic ID internally. It does not accept an arbitrary external event ID. The Wear recorder's type exposes only the source-and-occurrence flow and requires `recorded_at`; it has no source-only method. The Wear handler depends on `WearActionRecorder`, never on `LocalActionRecorder` or a generic policy selector. A static boundary test rejects local recorder/policy references from the Wear package.

This shape makes accidental Wear use of source-only policy structurally difficult while retaining one implementation of policy and race logic.

### 9.3 Option C: caller-owned pre-read and classification

Rejected. It would duplicate replay and race rules across Notification, Widget, and Wear, make result naming inconsistent, and permit entry points to silently weaken Repository conflicts.

## 10. Test matrix

### 10.1 Policy isolation

1. No call compiles or executes without explicit recorder/policy selection.
2. `RepositoryStrict` performs no pre-read and does not reinterpret conflict.
3. Wear recorder requires exact `recorded_at` and has no source-only API.
4. Wear production/static tests reject `LocalActionRecorder` and `FirstAcceptedBySource` references.
5. Local recorder rejects an action kind/source mismatch.
6. Local deterministic IDs are derived internally and cannot be replaced by arbitrary external IDs.

### 10.2 Notification and Widget first-accepted behavior

7. Reminder first delivery returns `Inserted`.
8. Delayed reminder duplicate with a different retry-time `occurredAt` returns `FirstAcceptedReplay`.
9. Widget first delivery returns `Inserted`.
10. Widget action in the same minute with a different precise millisecond returns `FirstAcceptedReplay`.
11. The first stored event remains unchanged field-for-field.
12. Same ID with another source returns `Conflict`.
13. Insert conflict followed by matching-source re-read returns `FirstAcceptedReplay`.
14. Storage failure and infrastructure exception never return replay.
15. Existing 6B notification and Widget accepted side effects remain unchanged.

### 10.3 Wear behavior

16. New valid action persists the watch `action_id` and exact `recorded_at`.
17. Existing same ID, source `WEAR`, and occurrence returns `FirstAcceptedReplay` without plan lookup.
18. A plan edit after first persistence does not change legal replay classification.
19. Existing same ID and source with another occurrence returns `Conflict`.
20. Existing same ID and occurrence with another source returns `Conflict`.
21. Same ID and occurrence with another `plan_id` documents the P2 limitation and follows the stored-event authority rule.
22. Invalid ID or time produces no write, replacement ID, success result, or deletion.
23. Missing or disabled plan on first materialization produces no write or deletion.
24. Storage failure, exception, and cancellation retain the DataItem.
25. `Inserted`, `RepositoryIdempotent`, and legal `FirstAcceptedReplay` delete only the corresponding URI.
26. Deletion failure leaves the committed event and redelivery retries cleanup as `FirstAcceptedReplay`.

### 10.4 Concurrency and regression

27. Concurrent identical Wear deliveries produce one row and accepted plus replay/idempotent outcomes.
28. Concurrent same ID with different occurrence leaves the first row and returns conflict for the other action.
29. Conflict re-read happens at most once and never retries insert with a new ID.
30. Batch 6B Notification and Widget JVM/instrumentation behavior remains green.
31. Room insert still distinguishes complete equality from any content difference.
32. Schema, migration, Domain, contracts, Repository implementation, and Wear protocol have no diff.

All fixtures remain synthetic. Disposable file-backed Room tests may be used during implementation; the production database must never be opened.

## 11. Follow-up implementation boundaries

Batch 6B history remains sealed and its report/review are not rewritten. A prerequisite implementation may make the minimum reviewed changes needed to:

- replace the current implicit source-only `RecordDoseEventAction` API with typed explicit recorders and accepted-reason results;
- make Notification and Widget select trusted local first-accepted behavior explicitly;
- preserve all existing 6B side-effect and receiver-lifecycle behavior;
- add focused JVM and disposable-Room tests for policy isolation and races.

Batch 6C may then make Wear explicitly use `FirstAcceptedBySourceAndOccurredAt` and the locked DataItem cleanup rules. This addendum does not implement either step and does not authorize unrelated production changes.

No data migration is required. Existing 6B events are not rewritten. Repository contracts, Room Repository equality, Domain, DAO, Entity, schema, migration, Wear paths, keys, and payload remain unchanged.

## 12. Stop conditions for implementation

Stop and return to design review if implementation:

- requires one implicit replay rule for every entry;
- permits the Wear handler to use local source-only policy;
- accepts arbitrary external IDs under `FirstAcceptedBySource`;
- cannot distinguish `RepositoryIdempotent` from `FirstAcceptedReplay`;
- runs success side effects after conflict, invalid input, storage failure, or exception;
- requires changing a Repository contract or weakening Room full-content equality;
- requires changing Domain, DAO, Entity, schema, migration, or Wear protocol;
- requires overwriting the first accepted event or generating a replacement ID;
- requires a legacy fallback, second database, or second fact source;
- cannot retain failed Wear DataItems or precisely delete the accepted action URI;
- cannot preserve Batch 6B Notification/Widget behavior;
- cannot cover races without a global permanent lock;
- requires real or real-derived data;
- leaves any unresolved P0/P1.

## 13. Compatibility, data, and release safety

- Room remains version 3 and internal-only.
- Schema 2 and schema 3, `MIGRATION_2_3`, Slot ID v1, JSON v1, and PK behavior remain unchanged.
- The Wear protocol remains `/hrt/dose-actions/<actionId>` with `plan_id`, `action_id`, and `recorded_at`.
- There is no independent ack path; DataItem URI deletion remains the only successful acknowledgement.
- No real database, user data, real medication data, or real-derived health data is used by this design task.
- Batch 6C has not started.
- This addendum does not authorize staging, committing, tagging, release creation, or a claim that Room v3 is releasable.

## 14. Decision

Risk status: **P0/P1/P2 = 0/0/1**.

The one P2 is the current protocol limitation that an already accepted Wear action replay cannot re-verify the original `plan_id`; `plan_id` is only a first-materialization input. This is documented as a limitation and is not represented as complete action-authenticity validation.

Replay policy conflict resolved at design level pending independent review.
