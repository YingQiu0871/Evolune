# Evolune Phase 1 Batch 6 Design

Date: 2026-08-05

Status: read-only design and production call-chain audit. This document does not implement Batch 6.

## 1. Authority and baseline

The authoritative scope is `docs/PHASE_1_DESIGN.md:856-867`: Batch 6 switches the remaining HRT/DoseEvent UI, reminder receiver, Widget, Wear listener, and composition-root entry points to the existing Repository contracts. Batch 7 remains responsible for the dedicated JSON v1 DTO/adapter and the general Domain-to-PK adapter. Batch 8 remains the final Phase 1 exit audit.

The audit started from the following verified baseline:

- branch: `phase1/batch6-design`;
- worktree and staging area: clean;
- `phase-1-batch-5` is an ancestor of `HEAD` and points to the formally sealed Batch 5 history;
- `git diff --check`: pass;
- Room: version 3 with `exportSchema = true`;
- Room remains the only production fact source;
- no real or real-derived database was opened, copied, upgraded, or used;
- no source, schema, build, Manifest, or test file was changed by this audit.

The prompt names `docs/ROADMAP.md`; the repository's actual roadmap is `docs/evolune/ROADMAP.md`.

### 1.1 Locked decisions

Batch 6 must preserve these decisions:

- ADR-014: Scheduled Dose Slot IDs use the immutable UUIDv5 v1 specification. A Slot ID is not a DoseEvent ID.
- ADR-015: features depend on Repository contracts; inserts are idempotent only for the same ID and same content; updates use compare-and-set revision semantics; conflicts are explicit.
- ADR-016: Room v3 remains an internal, non-releasable schema and migration/rollback evidence remains mandatory.
- `timeH` and `timeOfDay` remain compatibility shadows.
- PK regression tolerance remains an absolute `1e-6`.
- JSON v1 fields and valid/random UUID behavior remain unchanged.
- Wear `/hrt/*` paths, DataMap keys, payload values, and watch-side action creation remain unchanged.
- Tracked Date, Health Connect, Glance, WorkManager, cloud sync, a second database, and a second fact source remain outside Phase 1.

## 2. Authoritative Batch 6 scope

Batch 6 performs one architectural change: all remaining non-JSON production event entry points and derived consumers use `core.dataapi.DoseEventRepository` and `core.dataapi.MedicationPlanRepository`, obtained from `ProductionRepositoryProvider`, instead of a DAO, Entity, or legacy Repository.

The complete scope is:

1. Switch HRT event observation, manual insert, CAS edit, delete, PK history selection, and plan observation to contracts and Domain models.
2. Preserve complete Domain event metadata through record-list and editor sessions.
3. Route the existing JSON parser's transitional output through the event contract without changing `MahiroJsonFormat`, its fields, or its UUID rules. The dedicated external DTO/adapter remains Batch 7.
4. Preserve current PK behavior through a strictly local compatibility projection that calls `LegacyTimeAdapter`; do not create the general Domain-to-PK adapter or modify PK algorithms. The dedicated adapter remains Batch 7.
5. Switch reminder delivery/check-in reads, reminder confirmation writes, and boot/update/time-change rescheduling to contracts.
6. Switch Widget plan reads, event writes, and PK input reads to contracts.
7. Switch the phone-side Wear plan snapshot and Wear action listener to contracts while preserving the current protocol.
8. Remove temporary legacy Repository construction from `MainActivity` and use the existing application-scoped provider as the composition root.
9. Prove that no feature, receiver, Widget, or Wear production caller invokes a DAO, constructs an Entity, or constructs a legacy Repository.

### 2.1 Explicit exclusions

Batch 6 does not:

- change a Repository contract or result type;
- change Domain, Entity, DAO, `AppDatabase`, migration, schema 2, or schema 3;
- modify `MahiroJsonFormat.kt`, JSON v1 fields, parsing rules, or export rules;
- add the dedicated `MahiroEventV1Dto`/adapter;
- add the general Domain-to-PK adapter or modify `SimulationEngine` or any PK parameter;
- change the Wear module protocol, paths, keys, payload, action ID generation, or cache format;
- infer a `slotId` from medication similarity or a time window;
- add dual write, a legacy write fallback, destructive migration, or another database;
- open a real database or create a release.

## 3. Current production call chain

The current transitional chain is:

```text
MainActivity
  -> AppDatabase singleton
  -> legacy DoseEventRepository and legacy MedicationPlanRepository
  -> HRTViewModel
  -> PK DoseEvent and legacy MedicationPlan UI models

Android receivers / Widget / phone Wear listener
  -> AppDatabase singleton
  -> DAO and Entity, or a newly constructed legacy Repository

ProductionRepositoryProvider
  -> existing AppDatabase singleton
  -> RoomDoseEventRepository and RoomMedicationPlanRepository
  -> contract-typed properties
```

Batch 5 already switched the medication-plan editor vertical slice. Batch 6 must reuse that provider and must not create another composition path.

## 4. Complete bypass inventory

| Location | Current bypass | Kind | Required Batch 6 destination |
|---|---|---|---|
| `MainActivity.kt:40-44` | Opens `AppDatabase`, constructs both legacy Repositories, and separately obtains `ProductionRepositoryProvider` | composition root | Inject `provider.doseEvents` and `provider.medicationPlans`; remove direct database and legacy Repository construction |
| `HRTViewModel.kt:37-69` | Exposes PK `DoseEvent` and legacy `MedicationPlan` flows from legacy Repositories | read | Observe contract Domain flows |
| `HRTViewModel.kt:107-119` | One unconditional legacy upsert method represents both create and edit; delete calls the legacy Repository | write | Separate `insert`, CAS `update`, and `delete`; expose typed operation state |
| `HRTViewModel.kt:131-142` | JSON parser emits PK events and legacy upsert persists them | external write | Keep parser unchanged; use a local transitional PK-to-Domain bridge and contract `insert`; dedicated JSON DTO/adapter remains Batch 7 |
| `HRTViewModel.kt:182-248` | Legacy 30-day/20-event query feeds `SimulationEngine` directly | derived consumption | Contract `getEventsForPk(asOf)` plus local compatibility projection; preserve order and PK behavior |
| `MedicationRecordsScreen.kt:46-104` | UI owns PK events, legacy plans, quick-add PK construction, and closes before persistence result | read/write command producer | Domain events/plans, explicit insert/update/delete results, close only after accepted result |
| `MedicationRecordBottomSheet.kt:54-60,338-376` | Editor reconstructs a six-field PK event | write command producer | Edit an immutable Domain snapshot or editor session and preserve metadata/revision |
| `MedicationRecordItem.kt:160-188` | Record display accepts PK event and `timeH` | read/display | Accept Domain event and format `occurredAt` in the current display zone |
| `HomeScreen.kt:44-87` | Reads PK event `timeH` and legacy plans for derived display/prediction | derived consumption | Domain event instants and Domain plans; keep Predictor/PK parity |
| `MedicationReminderReceiver.kt:38-58` | Direct plan DAO read and inclusive legacy event DAO range read | read/derived | Plan `getById`; event half-open range adjusted to preserve the inclusive +/-1 hour boundary |
| `MedicationNotificationActionReceiver.kt:53-66` | Direct plan DAO read and `DoseEventEntity` upsert | write | Contract plan read plus idempotent Domain event insert with explicit result handling |
| `ReminderRescheduleReceiver.kt:22-26` | Constructs legacy plan Repository from DAO | read/derived | `provider.medicationPlans.observeAll().first()` and Domain reminder scheduling |
| `ReminderDoseFactory.kt` | Produces PK event from a legacy plan | write factory | Produce complete Domain event with source and metadata rules below |
| `DoseCheckInMatcher.kt` | Compares PK event and legacy plan by route/ester/dose | derived | Compare Domain event and Domain plan without inferring `slotId` |
| `EvoluneWidgetReceiver.kt:105-121` | Direct enabled-plan DAO read and legacy mapping with corrupt rows silently skipped | read/derived | Contract `observeEnabled().first()`; Repository mapping failures remain infrastructure/corruption failures, not skipped rows |
| `EvoluneWidgetReceiver.kt:211-237` | Direct plan DAO read and `DoseEventEntity` upsert | write | Contract plan `getById` and event `insert` with Widget idempotency policy |
| `EvoluneWidgetReceiver.kt:245-260` | Constructs legacy event Repository for concentration | derived consumption | Contract `getEventsForPk` plus local PK compatibility projection |
| `WidgetUtils.kt` | Uses legacy plans and PK events for schedule/taken derivation | derived consumption | Domain plans/events; preserve schedule order and +/-1 hour behavior |
| `WearDataLayer.kt:121-129` | Phone listener directly reads enabled plan DAO for snapshot | read/derived | Contract `observeEnabled().first()`; payload remains byte-for-byte compatible in shape |
| `WearDataLayer.kt:145-171` | Phone listener directly reads plan DAO and upserts an Entity | write | Contract `getById` and event `insert`; action ID remains event ID |
| `MainActivity.kt:101-116` | Wear dashboard is fed by HRT's legacy plan flow | derived/composition | Feed Domain plans from the contract-backed flow; keep existing payload and curve sampling |
| `data/DoseEventRepository.kt` | Legacy DAO-backed event Repository remains callable by MainActivity, HRT, and Widget | legacy definition and active bypass | No production caller after Batch 6; definition may remain until Batch 8 cleanup policy permits removal |
| `data/MedicationPlanRepository.kt` | Legacy DAO-backed plan Repository remains callable by MainActivity, HRT, and reschedule receiver | legacy definition and active bypass | No production caller after Batch 6; no legacy plan writes may be restored |
| legacy methods in `DoseEventDao` and `MedicationPlanDao` | `getAllEvents`, `getEventsByTimeRange`, `getRecentEvents`, `getEventsAfter`, `upsertEvent`, and legacy plan query/upsert methods | persistence definitions | No feature/background caller; do not remove in Batch 6 because schema/rollback compatibility is not this batch |

No other production DAO, Entity, or legacy Repository caller was found outside persistence implementations/mappers/entities themselves.

## 5. DoseEvent UI and command semantics

### 5.1 Metadata currently lost

The record UI currently reconstructs `pk.DoseEvent(id, route, timeH, doseMG, ester, extras)`. On edit this drops all fields that only exist in `core.model.DoseEvent`:

- `revision`;
- `source`;
- `status`;
- `zoneId`;
- `localDate`;
- `slotId`.

It also collapses create and edit into one upsert and closes the editor before a persistence result exists.

### 5.2 Editor session

The implementation should add a small pure Kotlin editor/session boundary under `application`, not a Room or Compose model.

An edit session owns:

- the complete immutable original Domain event;
- `expectedRevision = original.revision`;
- editable route, occurred local date/time, dose, ester, and extras;
- a stable new UUID created once when a create session starts;
- an explicit `ZoneId` supplied by the caller, never read implicitly by the pure helper.

Saving an existing event starts from `original.copy(...)`. It must preserve `id`, `source`, `status`, `slotId`, and the original `revision`. If date/time was not edited, it also preserves the original `zoneId` and `localDate` exactly. If the user explicitly edits date/time, the selected local date/time and explicit display `ZoneId` produce the new `occurredAt`; `zoneId` and `localDate` are then updated to that explicit edit context. No other metadata is cleared or normalized.

The submitted event keeps the original revision. `RoomDoseEventRepository.update` is the sole owner of incrementing it after a successful meaningful CAS update.

### 5.3 Create, update, and delete result rules

| Operation | Repository call | Accepted outcomes | Non-accepted outcomes |
|---|---|---|---|
| Manual create or quick add | `insert(event)` with revision 1 | `Inserted`; `Idempotent` for replay of the same create command | `Conflict` and `Invalid` keep/report the failed command; no legacy fallback |
| Edit | `update(event, expectedRevision)` | `Updated`; `NoChange` | `RevisionConflict`, `NotFound`, and `Invalid` keep/report stale or invalid state; never overwrite |
| Delete | `delete(id)` | `Deleted`; `NotFound` is an explicit stale/already-removed result | infrastructure exception remains an error; no DAO retry |

The Compose sheet closes and updates defaults only after an accepted result. A conflict or invalid result must not be presented as saved.

### 5.4 Manual event values

- New manual form: random UUID generated once per editor session; `source=MANUAL`; `status=RECORDED`; `revision=1`; explicit selected instant; current explicit device zone and selected local date; `slotId=null` unless a future UI supplies a proven Slot ID.
- Quick add from plan: preserve the current minute-floor occurrence time and random event UUID behavior; `source=MANUAL`; `status=RECORDED`; `revision=1`; current explicit device zone/local date; `slotId=null` because the button identifies a plan, not one unambiguous slot.
- Existing event edit: preserve provenance and slot metadata as described above. A manual edit does not rewrite the original source to `MANUAL`.

## 6. Background and cross-device event rules

### 6.1 Common rules

All background-created events use `status=RECORDED` and `revision=1`. They call `insert`, never `update` or DAO upsert. Success side effects such as a recorded-dose Toast or Widget refresh occur only after persistence is accepted according to the entry-specific policy. Skip actions and non-retryable stale/malformed input may still perform their existing cleanup, but that cleanup must be represented as a rejected/ignored outcome and must never be reported as a persisted dose.

No entry may infer a Slot ID from route, ester, dose, a +/-1 hour window, or local-time equality. The current reminder, Widget, and Wear payloads do not carry an unambiguous slot position/ID. Their Batch 6 events therefore use `slotId=null`.

### 6.2 Reminder confirmation

- Event ID: preserve the existing deterministic `UUID.nameUUIDFromBytes(UTF8("reminder:<planId>:<scheduledAtMillis>"))` result. Do not reinterpret it as Slot ID UUIDv5.
- Occurrence time: actual confirmation time in milliseconds, preserving current behavior.
- Metadata: current explicit phone `ZoneId` and local date at confirmation; `source=REMINDER`; `slotId=null`; revision 1.
- Idempotency: derive the ID before work. If that ID already exists with `source=REMINDER`, the occurrence is already accepted and must not be rewritten with a later confirmation time. On an insert race returning `Conflict`, re-read once and apply the same source/ID check. A row with another source is a real conflict.
- The notification is cancelled and Widget refresh is requested after `Inserted` or the recognized already-applied result. The existing skip action still cancels without writing. A missing/deleted plan is a non-retryable stale reminder and still cancels the notification without claiming a dose. Invalid/conflict/infrastructure failure for an otherwise live plan is not reported as recorded and does not run the recorded-dose success path.

The current deterministic ID omits dose fields intentionally: plan edits must not create a second event for the same scheduled occurrence. Dose/route/ester/extras are the plan snapshot used by the first accepted processing attempt.

### 6.3 Reminder delivery and rescheduling

`MedicationReminderReceiver` reads the plan through `getById`. To preserve the legacy inclusive +/-1 hour query with a half-open contract, it requests:

```text
[scheduledAt - 1 hour, scheduledAt + 1 hour + 1 millisecond)
```

and keeps the existing `abs(delta) <= 1 hour` matcher. This preserves events exactly on both boundaries at Room's millisecond precision.

Rescheduling uses Domain plans from `observeAll().first()` and the existing Domain reminder scheduling path. Alarm request codes, trigger calculations, device-system time-zone behavior, exact-alarm fallback, and occurrence order remain unchanged.

### 6.4 Widget record action

- Event ID: preserve the existing `UUID.nameUUIDFromBytes(UTF8("widget:<planId>:<epochMinute>"))` result.
- Occurrence time: actual action handling milliseconds, preserving current millisecond semantics; do not floor the persisted instant merely to make content equal.
- Metadata: current explicit phone zone/local date; `source=WIDGET`; `status=RECORDED`; `revision=1`; `slotId=null`.
- Idempotency: the first accepted event wins within the existing plan/minute key. An existing ID with `source=WIDGET` is treated as an already-applied action and is not CAS-updated to a later millisecond. A different source/content collision is explicit failure.
- Toast and refresh occur only after `Inserted` or recognized already-applied action. Repository failure has no Entity/DAO fallback.

This preserves one record per plan/minute and stable IDs. It deliberately ends the legacy upsert's hidden "last delivery overwrites time" behavior because that behavior violates ADR-015 revision/conflict ownership.

### 6.5 Wear action ownership

The Wear OS side remains the owner of `action_id`: `DoseTileService` creates one random UUID per tap, writes it into both the DataItem path and `action_id`, and sends one stable `recorded_at`. The phone persists that exact action UUID as `DoseEvent.id`. The phone must not generate a replacement ID.

Phone-side values are:

- `occurredAt = Instant.ofEpochMilli(recorded_at)`;
- `source=WEAR`;
- `status=RECORDED`;
- `revision=1`;
- `zoneId` and `localDate` from the explicit phone zone at the supplied instant, because the current payload carries no watch zone;
- `slotId=null`, because the payload carries a plan ID but no stable slot identity.

Repeated delivery first checks `getById(actionId)`. An existing `source=WEAR` row with the same `occurredAt` proves that the action was already accepted and avoids rebuilding content from a potentially edited plan. On an insert race, a `Conflict` is followed by one re-read with the same check. An existing row with another source or time is an explicit conflict and must not be overwritten.

The DataItem acknowledgement policy is fixed as follows:

- `Inserted` or recognized replay: delete the DataItem and report the command as accepted;
- malformed identifiers/time, missing plan, or disabled plan: emit an explicit non-retryable rejection, delete the poison/stale DataItem, and never report a persisted dose;
- same action ID with another source or recorded time: emit an explicit collision/conflict rejection, delete the non-retryable DataItem, and never overwrite the row;
- Repository/storage infrastructure failure: do not delete the DataItem, so Data Layer can redeliver it.

These outcomes require focused tests and must not be collapsed into one `runCatching` success/failure branch.

There is no Wear action ID conflict with the current contract or schema: action ID ownership maps directly to `DoseEvent.id`, while `InsertResult` and `getById` can express success, replay, and collision without changing the payload.

### 6.6 BroadcastReceiver lifecycle contract

This section resolves the receiver lifecycle gap reported as F1/P1 by the first independent design review. It applies to every Batch 6 `BroadcastReceiver`/`AppWidgetProvider` path that performs a suspend Repository operation or another bounded asynchronous command.

#### 6.6.1 Existing implementation baseline

The authoritative local pattern already exists in all three reminder receivers:

- `MedicationReminderReceiver.kt:35-37,66-67` calls `goAsync()`, launches one `CoroutineScope(SupervisorJob() + Dispatchers.IO)` task, and finishes from the outer `finally`;
- `MedicationNotificationActionReceiver.kt:49-52,72-73` uses the same pattern for a valid confirm action;
- `ReminderRescheduleReceiver.kt:19-21,27-28` uses the same pattern for every reschedule delivery.

`EvoluneWidgetReceiver.kt:65-70,80-101` also uses `goAsync()` and `finally`, but currently reuses one static `WIDGET_SCOPE`. That shared scope is not the Batch 6 lifecycle authority because it outlives one delivery and couples independent broadcasts. During 6B, Widget record and update deliveries must adopt the same per-delivery scope ownership as the reminder receivers and remove the shared static scope.

`WearSyncReceiver` (`wear/src/main/java/io/github/yuninggu/evolune/wear/WearSyncManager.kt:46-52`) does not call a suspend Repository and only triggers the existing Google Task/Tile APIs. It does not enter the Batch 6 Repository receiver lifecycle conversion and does not justify a second `goAsync()` pattern.

#### 6.6.2 `goAsync()` ownership

For every delivery that needs suspend Repository work:

1. `onReceive()` synchronously validates only the fields needed to decide whether asynchronous work is necessary.
2. `onReceive()` synchronously calls `goAsync()` exactly once before launching work and keeps the returned `PendingResult` in the local delivery scope.
3. Database access, Repository calls, bounded derived work, and asynchronous side effects run only after `goAsync()` and never directly on the main thread.
4. `onReceive()` must not use `runBlocking`, wait on a Future/Task, collect an unbounded Flow, or perform another long/blocking operation.
5. It must not use `GlobalScope`, an Activity/ViewModel scope, a static receiver scope, or an untracked fire-and-forget coroutine.
6. After `goAsync()` succeeds, no branch may return before the launched task owns the `PendingResult` and its outer `finally`.

The receiver passes `context.applicationContext` to `ProductionRepositoryProvider.get(...)`, the command handler, and any derived side-effect collaborator. It does not capture or retain the receiver instance, Activity context, original Context, or `PendingResult` beyond this one task. Intent values should be parsed into immutable command values before suspension where practical.

#### 6.6.3 Per-delivery CoroutineScope

Each asynchronous delivery owns exactly one independent scope/task:

- one new `SupervisorJob` (or a behaviorally equivalent independent Job) per delivery;
- `Dispatchers.IO` or a project-owned bounded IO dispatcher;
- no parent Activity/ViewModel/static Widget job;
- no sharing between two broadcasts, two Widget updates, or two notification actions;
- scope lifetime ends when the one handler task completes or is cancelled;
- no child work may escape the handler and continue after `PendingResult.finish()`.

Expected Repository outcomes (`Inserted`, `Idempotent`, `Conflict`, `Invalid`, `NotFound`, and the plan result equivalents) are converted to receiver-level typed outcomes inside the command handler. Repository/storage exceptions may become an explicit receiver-level `StorageFailure`; they must not become success or trigger fallback. `CancellationException` retains cancellation semantics. Regardless of which outcome is produced, the outer lifecycle block owns completion.

#### 6.6.4 `PendingResult.finish()` exactly once

`PendingResult.finish()` must:

- appear only once, in the outermost coroutine `finally`;
- execute after the handler and all permitted in-delivery side-effect attempts complete;
- execute for success, rejected input, idempotent replay, conflict, `NotFound`, receiver-level `StorageFailure`, unexpected exception, and in-process coroutine cancellation;
- never appear in individual outcome branches or command handlers;
- never run before a Repository operation completes;
- never be skipped because a notification, Widget refresh, Toast, reschedule, or logging side effect fails;
- never be delegated to another fire-and-forget child.

The required structure is equivalent to:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    receiverScope.launch {
        try {
            handleBroadcast(context.applicationContext, intent)
        } finally {
            pendingResult.finish()
        }
    }
}
```

Expected result/error logging may be placed inside `handleBroadcast` or inside the `try`, but it cannot move, duplicate, or suppress the outer `finally`. An unexpected exception may still be reported as failure after cleanup; `finish()` does not mean the command succeeded.

#### 6.6.5 Synchronous paths that do not call `goAsync()`

Only paths with no suspend/long work may return synchronously:

| Receiver path | Synchronous behavior | Why no `goAsync()` |
|---|---|---|
| `MedicationReminderReceiver` missing `EXTRA_PLAN_ID` or invalid UUID (`MedicationReminderReceiver.kt:28-34`) | Reject without Repository access | Validation is complete and has no asynchronous cleanup |
| `MedicationNotificationActionReceiver` `ACTION_SKIP_DOSE` (`MedicationNotificationActionReceiver.kt:34-36`) | Cancel the notification synchronously and return | Skip does not write or query a Repository |
| `MedicationNotificationActionReceiver` unknown action (`MedicationNotificationActionReceiver.kt:38-39`) | Ignore and return | No asynchronous work exists |
| `MedicationNotificationActionReceiver` confirm with missing/invalid plan UUID (`MedicationNotificationActionReceiver.kt:42-44`) | Reject and return | Repository lookup cannot begin without an ID |
| `ReminderRescheduleReceiver` | None | Every accepted delivery performs suspend plan observation and therefore calls `goAsync()` |
| `EvoluneWidgetReceiver.onReceive` non-record action (`EvoluneWidgetReceiver.kt:61-63`) | Return after the framework callback path | The custom record handler has no work for this action; framework `onUpdate`/options callbacks own their separate async update if invoked |
| `EvoluneWidgetReceiver` record action and `updateAsync` (`EvoluneWidgetReceiver.kt:65-91`) | None | Record and render paths perform suspend Repository/Settings work and always own one `goAsync()` task |

A stale/missing/disabled plan can only be learned from a Repository lookup. It is therefore an asynchronous typed rejection: the handler completes any permitted cleanup, returns the rejection, and then the one outer `finally` finishes the `PendingResult`.

#### 6.6.6 Bounded work and process termination

`goAsync()` extends a broadcast only for bounded work; it is not a durable job queue. Receiver handlers must perform a finite number of one-shot Repository operations and bounded local side effects, with no delay loop, unbounded retry, network wait, or unbounded Flow collection. If the required work cannot reliably complete inside Android's broadcast execution window, implementation must stop and return to design review rather than hiding long work behind `goAsync()` or introducing WorkManager outside the authorized scope.

The exactly-once `finish()` guarantee is an in-process guarantee. Android process termination can prevent any `finally` from running, and Batch 6 must not claim otherwise. No success acknowledgement or success UI side effect occurs before persistence. Wear remains recoverable because its DataItem is deleted only after accepted persistence/replay; an infrastructure interruption leaves the item for redelivery. Reminder/Widget processing remains short and bounded, and process death is never converted into a successful result or a legacy fallback.

#### 6.6.7 Business outcomes, exceptions, and logging

Expected Repository business outcomes remain ordinary typed results. `Inserted`, recognized `Idempotent`, `Conflict`, `Invalid`, `NotFound`, and the corresponding plan outcomes are not thrown or logged as unexpected exceptions. Each receiver maps them explicitly to accepted, rejected, stale, or ignored command outcomes.

Unexpected storage or side-effect exceptions may be logged only with a non-sensitive identifier or category needed for diagnosis. Logs must not contain a complete event or plan, dose values, `extras`, raw database messages, or database row content. They must not convert the command to success, trigger a legacy fallback, or start an unbounded retry.

`CancellationException` must be rethrown after any local classification needed for tests; it must never be swallowed or converted to `StorageFailure`. The outermost `finally` still calls `PendingResult.finish()` exactly once during in-process cancellation unwinding.

#### 6.6.8 Receiver-specific result and side-effect order

**`MedicationNotificationActionReceiver`**

1. Synchronous action/UUID validation runs before `goAsync()` as listed in section 6.6.5.
2. A valid confirmation reads the plan through `MedicationPlanRepository.getById`, builds one complete Domain event, and calls `DoseEventRepository.insert` before any recorded-dose success side effect.
3. `Inserted` and a recognized already-applied/idempotent event permit the existing notification cancellation and Widget refresh. An insert race that returns `Conflict` may be re-read once under the section 6.2 ID/source rules; only a matching accepted event becomes recognized idempotency.
4. Same ID with different content/source remains `Conflict`. `Conflict`, `Invalid`, receiver-level `StorageFailure`, and unexpected exceptions must not report a recorded dose or execute the recorded-dose success path.
5. A missing/deleted plan remains a non-retryable stale result: it may perform the existing notification cleanup, but that cleanup is not Repository success or a recorded-dose acknowledgement.
6. Once persistence has returned `Inserted` or recognized idempotency, a notification-cancellation or Widget-refresh failure does not roll back or compensate the database write, does not invoke a fallback writer, and does not change the Repository outcome. The outer `finally` still finishes once after the permitted side-effect attempts end.

**`MedicationReminderReceiver`**

1. A valid delivery reads the plan through `MedicationPlanRepository.getById`, then reads the event window through `DoseEventRepository.findOccurredBetween`, before deciding whether a reminder is needed.
2. A plan/event read failure cannot manufacture a plan, an empty event list, or a reminder. Missing/disabled plans and detected check-ins follow their explicit no-notification outcomes.
3. Notification dispatch and refresh of the next reminder batch are bounded derived side effects only. A notification dispatch exception performs no database write, no plan rewrite, and no legacy DAO/Repository fallback.
4. The one outer `finally` covers every plan result, event-window result, suppression decision, notification path, reschedule path, unexpected exception, and in-process cancellation.

**`ReminderRescheduleReceiver`**

1. Every delivery calls `goAsync()`, obtains all Domain plans through `MedicationPlanRepository.observeAll().first()`, and completes `ReminderManager.rescheduleDomainReminders(plans)` before `finish()`.
2. Preserve the current fail-fast behavior in `ReminderManager.kt:105-107`: if cancellation or scheduling for one plan throws, the current loop aborts and later plan operations do not continue under a catch-and-skip policy.
3. A read or scheduling failure does not rewrite a plan, mutate Repository state, retry through a legacy Repository, or report successful rescheduling.
4. Success, empty-plan input, read failure, single-plan scheduling failure, unexpected exception, and in-process cancellation all converge on the one outer `finally`, which finishes exactly once.

#### 6.6.9 Three-receiver lifecycle matrix

| Receiver | `goAsync` | Async work | Repository operation | Success side effect | Failure behavior | `finish` location |
|---|---|---|---|---|---|---|
| `MedicationReminderReceiver` | Missing/invalid plan ID rejects synchronously without `goAsync`; every valid UUID delivery calls it exactly once | Read plan, read the bounded event window, evaluate check-in, optionally notify, then refresh the next reminder batch | `medicationPlans.getById`; `doseEvents.findOccurredBetween` | Send a reminder only for an enabled plan with no matching check-in; preserve existing bounded reschedule behavior | Read failure creates no fake reminder; notification/reschedule failure performs no write or fallback; cancellation is rethrown | Once in the outermost coroutine `finally`, after all accepted read/result/notification/reschedule paths |
| `MedicationNotificationActionReceiver` | Skip, unknown action, and invalid confirm ID return synchronously without `goAsync`; valid confirm calls it exactly once | Read plan, build event, insert, classify replay/conflict, then attempt permitted notification/Widget effects | `medicationPlans.getById`; `doseEvents.insert`; one `doseEvents.getById` re-read only for insert-race conflict classification | Cancel stale notification without claiming persistence, or cancel notification and refresh Widget only after `Inserted`/recognized idempotency | Live-plan conflict/invalid/storage/exception has no recorded-dose success path; accepted DB state is not rolled back when a later side effect fails | Once in the outermost coroutine `finally`, after Repository classification and permitted side-effect attempts |
| `ReminderRescheduleReceiver` | No synchronous rejection path; every delivery calls it exactly once | Read the complete first plan snapshot, cancel current reminders, then schedule enabled Domain plans in existing order | `medicationPlans.observeAll().first()` | Complete the existing all-plan reschedule operation | Preserve fail-fast on one-plan scheduling exception; no plan rewrite, catch-and-skip continuation, legacy fallback, or false success | Once in the outermost coroutine `finally`, after the reschedule call returns or unwinds |

#### 6.6.10 Meaning of `PendingResult.finish()`

`finish()` means only that this receiver's asynchronous in-process lifetime has ended. It does not mean that a Repository operation succeeded, a notification was sent or cancelled, a Widget refreshed, a reminder was rescheduled, or Wear acknowledged an action. Business success is determined only by the explicit Repository result and the entry-specific protocol outcome; no caller may use `finish()` itself as a success acknowledgement.

#### 6.6.11 Receiver lifecycle test seam

The implementation must expose a narrow internal test seam without changing Android framework classes:

- the receiver shell delegates parsed commands to a replaceable receiver work delegate, so tests can return each typed outcome or throw at a precise suspension point;
- asynchronous launch uses an injected or internal replaceable dispatcher/scope factory whose production default is the per-delivery `SupervisorJob + Dispatchers.IO` contract and whose test form is controlled deterministically;
- the lifecycle runner accepts a finish callback adapted from `pendingResult::finish`, allowing a counter/spy in tests while production still calls the real `PendingResult`;
- synchronous parsing reports whether the async starter was invoked, proving that early rejection does not call `goAsync()`;
- tests coordinate with coroutine/test completion primitives, not `Thread.sleep`, Android framework modification, or log-only assertions.

The seam may be a small shared internal receiver runner because it enforces one lifecycle contract; it must not become a second service locator, retain Context globally, or hide business result mapping inside Android framework glue.

## 7. JSON v1 boundary remains Batch 7

Batch 6 must remove the legacy event Repository from HRT without changing JSON v1. The temporary rule is narrowly scoped:

1. `MahiroJsonFormat.parseImport` remains unchanged and still emits its current PK model.
2. HRT converts each parsed object at the call site using `LegacyTimeAdapter.timeHToInstant`, explicit ExtraKey mapping, the original UUID, `source=JSON_V1`, `status=RECORDED`, revision 1, and null zone/localDate/slotId.
3. Persistence uses contract `insert`; same-ID/same-content is accepted and same-ID/different-content is an explicit conflict.
4. Export remains on the existing JSON path until Batch 7.

This is only a compatibility bridge needed to remove the legacy writer. It is not the dedicated External DTO/adapter, does not change valid/random UUID behavior, and must not become a reusable JSON API. Batch 7 creates the dedicated DTO/adapter, removes direct PK reuse from import/export, and freezes complete import result semantics with the JSON fixture matrix.

## 8. PK boundary remains Batch 7

Batch 6 must not keep the legacy Repository merely to feed PK. It uses contract `getEventsForPk(asOf)` and a private consumer-local compatibility projection with these exact rules:

- preserve input list order from both frozen 30-day/20-event branches;
- include only `status=RECORDED` persisted events;
- copy ID, route, dose, ester, and extras exactly;
- obtain `timeH` through `LegacyTimeAdapter.instantToTimeH`;
- map ExtraKey exhaustively without ordinal;
- keep antiandrogen filtering, future prediction merge, one-hour conflict filtering, simulation range, step count, and all PK constants unchanged.

HRT and Widget may use this private local bridge during Batch 6. They must not introduce a public/general adapter, modify `SimulationEngine`, or copy the `3_600_000` conversion constant outside `LegacyTimeAdapter`. Batch 7 replaces the local bridge with the dedicated Domain-to-PK adapter and executes the formal fixture/time-array/concentration/AUC parity proof.

Batch 6 still runs all existing PK tests at absolute tolerance `1e-6` and adds focused projection parity tests. Compilation or passing mocks alone is insufficient.

## 9. Strategy comparison

| Strategy | Assessment | Decision |
|---|---|---|
| A. Switch HRT, all receivers, Widget, and Wear in one change | Large Android lifecycle and failure-policy blast radius; UI CAS, background idempotency, RemoteViews, and cross-device delivery failures are difficult to isolate | Reject |
| B. Switch phone HRT/UI, then background/Widget, then Wear/final composition | Each slice has one fact source and a testable vertical boundary; failures remain attributable; no released intermediate build is allowed | Accept |
| C. Dual-write old/new Repositories or fall back to DAO/legacy Repository | Results can disagree, revisions can diverge, and a failed contract write can be silently overwritten by legacy upsert | Reject |

No slice may invoke both old and new writers for one command. A temporary same-database legacy read is allowed only inside an unsealed intermediate commit when required to keep the next slice buildable; it must be named, read-only, and removed before Batch 6 acceptance. Batch 6 as a whole cannot pass with any feature/background legacy Repository caller.

## 10. Recommended implementation split

The names 6A, 6B, and 6C are reviewable sub-slices under the existing Batch 6 roadmap item. They do not create new roadmap batches.

### 10.1 6A: phone HRT and record UI

Atomic boundary:

- switch HRT event and plan dependencies to contracts;
- switch record and Home screens to Domain models;
- implement pure editor-session/CAS command handling;
- route existing JSON parser output through the narrow transitional contract bridge without modifying JSON code;
- preserve HRT PK behavior through the private local compatibility projection;
- inject both provider contracts from `MainActivity`;
- change only the phone-to-Wear plan encoder signature needed to accept Domain plans, without changing payload data;
- keep all reminder, Widget, and Wear listener DAO writers unchanged until their own slices; do not release or tag this intermediate state as Batch 6.

Expected production files:

- add `app/src/main/java/io/github/yuninggu/evolune/application/DoseEventEditor.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/MainActivity.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/viewmodel/HRTViewModel.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/ui/screens/HomeScreen.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/ui/screens/MedicationRecordsScreen.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationRecordBottomSheet.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationRecordItem.kt`;
- modify only the Domain-plan encoding/sync surface in `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt`.

Expected tests:

- add `app/src/test/java/io/github/yuninggu/evolune/application/DoseEventEditorTest.kt`;
- add `app/src/test/java/io/github/yuninggu/evolune/viewmodel/HRTViewModelTest.kt`;
- add/update record UI tests under `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/`;
- add a disposable-Room production cutover test under `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/`.

### 10.2 6B: reminder receivers and Widget

Atomic boundary:

- introduce a small application command handler with injected contracts and typed outcomes for background record actions;
- make Android receivers thin provider/delegation shells that implement the single lifecycle contract in section 6.6;
- preserve the existing reminder receiver `goAsync()`/per-delivery `SupervisorJob + Dispatchers.IO`/outer `finally` baseline;
- replace the Widget's shared static `WIDGET_SCOPE` with one independent scope/task per `PendingResult`;
- switch reminder reads, confirmation writes, and rescheduling;
- switch Widget plan/event reads and Widget writes;
- switch Widget schedule/taken derivation to Domain values;
- retain exact alarm, window, deterministic ID, RemoteViews, Toast, refresh, and PK output behavior except where ADR-015 explicitly replaces hidden overwrite with first-accepted idempotency.

Expected production files:

- add `app/src/main/java/io/github/yuninggu/evolune/application/RecordDoseEventAction.kt` or an equivalently narrow pure/injected command handler;
- modify `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationReminderReceiver.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationNotificationActionReceiver.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderRescheduleReceiver.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderDoseFactory.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/reminder/DoseCheckInMatcher.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/widget/EvoluneWidgetReceiver.kt`;
- modify `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetUtils.kt`.

Expected tests:

- update reminder factory and matcher JVM tests for Domain metadata and exact boundaries;
- add command-handler idempotency/conflict/side-effect tests;
- add receiver lifecycle tests proving zero `goAsync()` calls on each listed synchronous path, exactly one call on every asynchronous path, and exactly one `finish()` after success/rejection/idempotent/conflict/NotFound/StorageFailure/exception/cancellation;
- prove `finish()` occurs after Repository completion and still occurs when a derived side effect throws;
- add a static check rejecting `runBlocking`, `GlobalScope`, static/shared receiver scopes, and branch-local `finish()` calls in Batch 6 receiver/Widget code;
- add Widget factory/schedule JVM tests;
- add phone instrumentation for reminder action persistence and Widget action/refresh using only a disposable database.

### 10.3 6C: Wear listener and final closure

Atomic boundary:

- switch Wear plan request and dose action processing to provider contracts;
- preserve watch-owned action ID, recorded time, paths, keys, and payload;
- lock DataItem acknowledgement/retry behavior;
- run final production scans and remove every remaining feature/background legacy Repository, DAO, and Entity caller;
- remove any temporary intermediate read bridge that is not explicitly the Batch 7 JSON/PK call-site compatibility code.

Expected production files:

- modify `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt`;
- revisit `app/src/main/java/io/github/yuninggu/evolune/MainActivity.kt` only if final composition cleanup remains;
- no production change is expected in `wear/src/main/.../DoseTileService.kt` or `WearPlanListenerService.kt` because the protocol and action generation already satisfy the design.

Expected tests:

- update `app/src/test/java/io/github/yuninggu/evolune/wear/WearDataLayerTest.kt` for complete Domain metadata and action ownership;
- add phone-side listener command/integration tests with a disposable Room database;
- run a separate paired Wear OS AVD action/snapshot acceptance test.

## 11. Test matrix

### 11.1 Pure JVM and ViewModel tests

Required cases include:

- create session owns one stable random UUID across recomposition/retry;
- manual insert maps every `InsertResult` and never calls update;
- edit preserves source/status/slot/zone/localDate/revision when unchanged;
- explicit date/time edit updates only the time context while preserving provenance and Slot ID;
- `Updated` increments revision once; `NoChange` does not increment;
- stale expected revision returns `RevisionConflict` and leaves stored content unchanged;
- edit/delete `NotFound` is explicit;
- conflict/invalid/infrastructure failure does not close the editor or update defaults;
- quick add remains minute-floor and has source MANUAL/revision 1/null slot;
- JSON compatibility bridge preserves valid IDs, random IDs already created by the parser, time within 1 ms, and source JSON_V1;
- HRT PK compatibility projection preserves list order, IDs, fields, and timeH;
- reminder query includes both exact +/-1 hour boundaries;
- reminder replay and insert race produce one row without revision change;
- Widget same-plan/same-minute replay produces one first-accepted row;
- Wear replay after plan mutation still recognizes the existing action by action ID/source/recorded time;
- same ID with another source/time is a conflict and is never overwritten;
- malformed/stale Wear action and infrastructure failure follow the locked acknowledgement policy;
- Widget refresh, Toast, notification cancellation, and DataItem deletion occur only after accepted persistence;
- receiver lifecycle runner finishes exactly once for every typed outcome, exception, and in-process cancellation;
- asynchronous receiver handlers use application context, one independent IO task, and no escaped child coroutine;
- all fixtures are synthetic.

Receiver lifecycle tests must use the section 6.6.11 seams and cover all of the following without timing sleeps or log-only assertions:

- `Inserted`/success finishes once after its permitted success side effects;
- recognized idempotency finishes once and duplicate delivery does not duplicate a write;
- `Conflict` finishes once and performs no recorded-dose success notification, cancellation, Widget refresh, or acknowledgement;
- `NotFound` finishes once and only an explicitly allowed stale-input cleanup may run;
- receiver-level `StorageFailure` finishes once and performs no success side effect or legacy fallback;
- an unexpected Repository exception finishes once after being classified/logged under the non-sensitive logging rule;
- coroutine cancellation rethrows `CancellationException` while the outer `finally` finishes once;
- early intent rejection does not invoke the async starter/`goAsync()`;
- no path duplicates or omits `finish()`, including a failure thrown by a success side effect;
- `onReceive()` returns without waiting for Repository work and never blocks the instrumentation/main thread;
- production and test scans find no `GlobalScope`, `runBlocking`, shared static receiver scope, or escaped child task;
- database failure cannot produce a success notification/Widget state;
- database acceptance followed by notification/Widget failure preserves the committed row and still finishes once;
- API 33 and API 35 phone instrumentation exercise the actual receiver/PendingResult integration, with counts taken from JUnit XML;
- a Wear OS AVD is never credited as execution of a phone receiver test.

### 11.2 Repository and disposable-Room instrumentation

On a disposable file-backed Room v3 database, prove:

- UI insert persists complete Domain metadata and reopens unchanged;
- CAS update preserves metadata and increments revision once;
- concurrent stale edit loses with `RevisionConflict` and cannot overwrite;
- same-ID/same-content insert is idempotent;
- same-ID/different-content insert is conflict and leaves the original row unchanged;
- physical delete and explicit not-found behavior;
- reminder, Widget, and Wear action commands each produce one row with the correct source;
- rollback/infrastructure failure produces no partial row and no success side effect;
- provider repositories share the one supplied database;
- tests never open the production `evolune_database`.

### 11.3 Phone API 33 and API 35 matrix

Run the same phone acceptance matrix on one API 33 phone AVD and one API 35 phone AVD:

- manual add, quick add, edit, CAS conflict, delete, rotation/recomposition, and process recreation;
- source/slot/revision preservation after close/reopen;
- reminder delivery suppression at both +/-1 hour boundaries;
- notification confirm replay and stale-plan behavior;
- reboot/app-update/time-zone reschedule behavior with synthetic plans;
- Widget enabled-plan display, action persistence, duplicate delivery, Toast, and refresh;
- reminder/reschedule/Widget broadcasts complete their `PendingResult` on accepted, rejected, conflict, storage-failure, side-effect-failure, and cancellation test paths without blocking the instrumentation thread;
- full App connected suite;
- migration, FK, cascade, unique Slot ID, repository rollback, and provider tests remain green.

Both targets must be phone AVDs. A Wear OS AVD result cannot satisfy any phone UI or phone receiver test.

### 11.4 Separate Wear OS acceptance

Use a Wear OS AVD only for Wear acceptance, paired with an approved synthetic-data phone test environment:

- phone publishes the unchanged first-two-enabled-plans dashboard payload;
- Wear receives plans/current concentration/curve and refreshes Tile state;
- one Tile tap creates one action ID and one recorded-at value;
- phone persists exactly that action ID as one source WEAR event;
- repeated DataItem delivery remains one row;
- successful replay is acknowledged; infrastructure failure is retained for retry;
- plan deletion/disable and malformed payload follow the documented rejection policy;
- no phone UI test is executed or credited on the Wear AVD;
- Wear JVM tests and Wear debug build pass.

### 11.5 Complete regression and immutable gates

Every sub-slice runs focused tests first. Batch 6 final validation then runs:

- all core, mapper, migration, repository, reminder, Widget, Wear-data, HRT, and application JVM tests;
- full App JVM suite;
- PK regression with absolute `1e-6` tolerance;
- App and Wear debug builds;
- Wear JVM suite;
- App lint with zero errors;
- androidTest Kotlin compilation;
- full connected suite on API 33 and API 35 phone AVDs;
- separate paired Wear OS AVD acceptance;
- KSP/schema generation and schema diff/hash verification.

Test/suite/assertion counts must come from actual JUnit XML. AndroidTest compilation must never be reported as device execution.

Schema gates remain:

- schema 2 identity hash: `a8036e3f5ed6bb42d0e7289ac84039f3`;
- schema 2 canonical SHA-256: `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`;
- schema 3 identity hash: `c5f5e02cb04b048ca28fe96a74d61606`;
- schema 3 SHA-256: `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`;
- `AppDatabase.version = 3` and `exportSchema = true`;
- no migration, Entity, DAO, schema, Slot ID, PK parameter, JSON format, or tolerance change.

## 12. Legacy writer removal proof

Batch 6 cannot pass until repository-wide production scans prove:

1. no caller outside persistence definitions/implementations references `DoseEventEntity` or `MedicationPlanEntity`;
2. no feature, receiver, Widget, or Wear code calls `doseEventDao()` or `medicationPlanDao()`;
3. no production caller constructs `data.DoseEventRepository` or `data.MedicationPlanRepository`;
4. no production caller invokes legacy `upsertEvent`, `upsertPlan`, or legacy DAO read methods;
5. `MainActivity` obtains both contracts from exactly one `ProductionRepositoryProvider`;
6. every event writer reaches `RoomDoseEventRepository.insert/update/delete` only;
7. no rejected Repository result is followed by DAO/Entity/legacy Repository fallback;
8. JSON v1 remains the documented call-site compatibility bridge only, with no legacy writer;
9. PK remains a derived consumer, never a persistence model or fact source;
10. only one production Room builder and database filename exist.

Legacy classes and DAO methods may remain as uncalled compatibility definitions. Their deletion is not a Batch 6 schema cleanup task. Batch 8 performs the final feature/Wear/Widget access audit.

## 13. Rollback and failure policy

- Before release, rollback is a source-level revert of an atomic 6A, 6B, or 6C change. No real database is used.
- Database version is never downgraded. A v3 runtime rollback must remain a v3-compatible corrective build.
- Repository conflict/invalid/not-found results are normal typed outcomes and must not be converted into success.
- Infrastructure/corrupt-storage exceptions propagate to an explicit failure path. They do not trigger legacy retry.
- CAS conflict leaves the stored row and revision unchanged.
- Idempotent insert leaves one row and revision 1.
- Background side effects are ordered after accepted persistence.
- Every asynchronous receiver delivery owns one `PendingResult`; the outermost `finally` finishes it exactly once for every in-process outcome and cancellation path.
- A derived side-effect failure cannot skip receiver completion and cannot trigger a legacy write fallback.
- Wear infrastructure failure retains the DataItem; success/replay acknowledgement deletes it.
- No action clears data, uses destructive migration, or writes a second fact source.

## 14. Risks and findings

Revised design status: **P0/P1/P2 = 0/0/5**. The design-layer F1 blocker has been revised and is awaiting independent re-review. This status does not claim that Batch 6 production code exists or that receiver lifecycle behavior has been validated in code. All five previously identified P2 items remain listed below.

### P0

None found.

### P1

None in the revised design. The first independent review reported F1/P1 because the original design did not lock receiver `goAsync()`/`PendingResult` ownership. Section 6.6 revises that design-layer gap by fixing the existing per-delivery reminder pattern as authoritative, requiring exactly-once outer-`finally` completion, normalizing Widget scope ownership, enumerating receiver-specific result/side-effect order and synchronous paths, and defining cancellation/process-termination/test-seam requirements. Independent re-review is still required before submission or implementation; this is not code validation.

The existing contracts still express all required production reads, insert/idempotency/conflict outcomes, CAS update, and physical delete. No schema or Domain change is required. Wear action ID ownership remains compatible with `DoseEvent.id`.

### P2

1. `core.model` still depends on PK `Route` and `Ester`; this is the previously accepted transitional dependency.
2. Batch 6 needs narrow call-site JSON-to-Domain and Domain-to-PK compatibility bridges until Batch 7 creates the dedicated adapters. Duplication must be private, tested, and removed in Batch 7.
3. Reminder and Widget deterministic event IDs use the existing legacy `UUID.nameUUIDFromBytes` rules rather than ADR-014 UUIDv5. They are event action IDs, not Slot IDs, and must not be reinterpreted in Batch 6.
4. Reminder, Widget, and Wear payloads do not prove a Slot ID, so new events from those entries retain `slotId=null` until a future protocol/UI explicitly supplies one.
5. The architecture document retains historical Room v2 status wording. Actual committed code and ADR-016 establish Room v3; this documentation drift is not an implementation blocker and is outside this one-file task.

## 15. Stop conditions

Stop implementation and return to design review if any of the following occurs:

- a required behavior cannot be expressed by the existing Repository contracts/results;
- preserving metadata or action ownership requires a Domain, Entity, DAO, schema, or migration change;
- Wear action ID can no longer remain the persisted DoseEvent ID without changing the payload;
- an entry requires inferring `slotId` from medication similarity or a time window;
- preserving JSON compatibility requires modifying JSON v1 before Batch 7;
- preserving PK behavior requires modifying `SimulationEngine`, a PK parameter, event ordering, or the `1e-6` tolerance;
- implementation proposes old/new dual write, silent fallback, second database, or second fact source;
- a Repository conflict is resolved by overwrite;
- metadata or revision is discarded by the UI/editor;
- reminder/Widget/Wear writes construct an Entity or call a DAO;
- a suspend receiver path omits `goAsync()`, blocks `onReceive`, uses `runBlocking`/`GlobalScope`/a shared static scope, finishes outside one outer `finally`, or allows child work to escape after `finish()`;
- receiver lifecycle tests cannot prove exactly one `finish()` for success, rejection, idempotent, conflict, NotFound, StorageFailure, exception, side-effect failure, and in-process cancellation;
- exactly-once `finish()` cannot be guaranteed for every in-process path;
- receiver work requires `GlobalScope`, another unowned scope, or any scope that outlives one delivery;
- a receiver must block the main thread to complete its Repository or side-effect work;
- a success notification, Widget state, reminder success, or acknowledgement must occur before the Repository operation completes;
- coroutine cancellation can bypass the outer `finally` or omit `finish()`;
- required work exceeds the short receiver execution lifetime and therefore needs a durable-work framework;
- any failure path requires a legacy DAO/Repository fallback;
- tests cannot deterministically prove exception and cancellation completion without `Thread.sleep` or log-only assertions;
- receiver completion requires a Manifest, Widget/Wear protocol, Repository contract, Domain/schema, or WorkManager change;
- schema 2/3, migration behavior, Slot ID v1, or hashes change;
- API 33/API 35 phone connected tests cannot execute;
- Wear acceptance is attempted on a phone AVD or phone UI acceptance is attempted on a Wear AVD;
- a real or real-derived database is required;
- any P0/P1 remains unresolved after the authorized implementation iterations.

## 16. Batch 6 acceptance decision

The revised Batch 6 design is ready for independent DeepSeek read-only re-review of F1. The first review remains an immutable `REQUEST CHANGES` historical record and is not modified by this revision.

Implementation should use strategy B and the reviewable 6A/6B/6C sub-slices described above. Batch 6 passes only after all three slices are complete, every final bypass scan is clean, all required JVM/build/lint/schema/device matrices pass, and no P0/P1 remains.

Batch 6 completion will not authorize Batch 8, a real-database rehearsal, production distribution, or release. Batch 7 must still deliver the dedicated JSON v1 and Domain-to-PK adapters. Room v3 remains internal and non-releasable through the Batch 8 release gates.
