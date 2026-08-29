# Evolune v1.2 B2 Restore Protocol

Status: local restore transaction and crash-recovery protocol for v1.2.

## Scope and frozen boundaries

B2 restores an already decoded and validated `EvoluneBackupPayloadV1` into the
local authoritative stores. It does not change the B1 envelope, AES-GCM/KDF,
golden vectors, Room schema version, Health Connect behavior, Drive/Auth, UI,
WorkManager, or any cloud/background policy.

The authoritative targets remain:

- medication plans, scheduled slots, and dose events: Room v3;
- scalar user settings: `SettingsDataStore`;
- Health Connect: external observation only;
- Drive: remote snapshot only, outside B2.

`DoseEvent.slotId` remains nullable and may intentionally reference a missing
slot. Restore preserves that value; it does not invent or repair history.

## Prepare and explicit restore

`prepare(validatedPayload)` is pure. It re-validates the provider-independent
payload, maps it to the Room/DataStore restore target, and returns a preview
with counts and all settings. It does not open a journal, write Room, write
DataStore, schedule reminders, update widgets, sync Wear, or read Health
Connect.

Only an explicit caller may invoke `restore(preparedRestore)`. The coordinator
holds one process-local `Mutex`, resolves any prior journal, captures the
complete current Room and settings state, and then executes:

1. write and durably flush `PREPARED` journal;
2. replace all Room v3 tables in one `Room.withTransaction`;
3. replace all five supported settings in one DataStore `edit`;
4. re-read Room and settings and require exact postconditions;
5. atomically write `COMMITTED`;
6. delete the journal. If cleanup fails after `COMMITTED`, the target remains
   authoritative and startup retries cleanup.

The transaction itself does not call reminders, widget update, Wear sync,
Health Connect, PK, or other external side effects. Existing observers may
react after a successful committed persistence change.

## Recovery journal

The journal is an unencrypted, strict version-1 JSON record at:

`Context.noBackupFilesDir/evolune_restore_journal.json`

It is written with Android `AtomicFile` and includes:

- `formatVersion`, `operationId`, `createdAt`, and `phase`;
- the complete canonical pre-restore Room state;
- the complete pre-restore settings state.

Only `PREPARED` and `COMMITTED` are accepted. Unknown fields, missing fields,
truncated JSON, invalid identifiers/timestamps, invalid payload values, and
future journal versions fail closed. A `PREPARED` journal rolls back to its
captured before-state. A `COMMITTED` journal keeps the target and only cleans
up the journal. Recovery runs in `MainActivity.onCreate` before repository
consumers and reminder/widget/Wear side effects are started. Recovery failure
finishes startup and leaves the journal for operator-visible recovery rather
than using uncertain data.

## Failure matrix

| Point of interruption | Durable state on restart | Recovery action |
| --- | --- | --- |
| A. before Room transaction | `PREPARED`, old stores | keep/restore old state |
| B. after Room commit, before DataStore completion | `PREPARED`, mixed process state | restore old Room and settings |
| C. after DataStore write, before verification | `PREPARED`, target may be present | restore old Room and settings |
| D. after verification, before `COMMITTED` | `PREPARED`, target verified in process | restore old Room and settings |
| E. after `COMMITTED`, before journal deletion | `COMMITTED`, target complete | keep target, retry cleanup |

If compensation or its verification fails, B2 returns `ROLLBACK_FAILED` or
`RECOVERY_REQUIRED` and retains `PREPARED`. It never deletes an unresolved
journal merely because rollback was attempted.

Stable coordinator error codes include `LOCAL_SNAPSHOT_FAILED`,
`JOURNAL_WRITE_FAILED`, `DATABASE_RESTORE_FAILED`, `SETTINGS_RESTORE_FAILED`,
`POSTCONDITION_FAILED`, `JOURNAL_COMMIT_FAILED`, `ROLLBACK_FAILED`,
`RECOVERY_REQUIRED`, `RECOVERY_JOURNAL_CORRUPT`, and
`UNSUPPORTED_JOURNAL_VERSION`. Coroutine cancellation is rethrown unchanged.

## Verification coverage

The B2 tests cover pure preview/no-write behavior, representative Room v3
state with dangling event slot IDs, all settings, Room transaction failure,
settings failure, postcondition failure, commit-journal failure, rollback
retention, PREPARED/COMMITTED startup recovery, malformed/future journals,
strict journal fields, concurrency serialization, and an actual in-memory Room
replacement/rollback integration seam.
