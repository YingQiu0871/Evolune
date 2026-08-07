# Evolune Phase 1 Batch 6C Report

Date: 2026-08-07

Status: implementation complete and validated, pending independent read-only review.

## 1. Scope

Batch 6C completes the Phase 1 production Repository cutover for the phone-side Wear integration:

- phone Wear dose actions are parsed and validated without fallback values;
- `WearActionRecorder` owns Wear replay and conflict classification;
- the watch-owned `action_id` is preserved as `DoseEvent.id`;
- the watch-owned `recorded_at` is preserved as the expected event occurrence;
- first materialization reads the current Domain plan through `MedicationPlanRepository`;
- accepted results alone can trigger exact-URI DataItem deletion;
- phone-to-watch plan projection uses Domain plans from Repository contracts;
- the remaining MainActivity Wear composition wiring no longer creates a legacy Repository or accesses `AppDatabase`;
- final static coverage proves no production DAO, Entity, database, or legacy Repository bypass remains outside the persistence boundary.

Batch 6C does not modify the Wear payload, path, or keys. It does not modify Repository contracts, Room Repository implementations, Domain models, DAOs, Entities, `AppDatabase`, schemas, migrations, JSON v1, PK algorithms, PK parameters, Gradle, Manifest, receiver or Widget production code, HRT UI, or the Wear module production implementation. It does not use the production database, start Batch 7, or make Room v3 releasable.

## 2. Files

Modified production files:

- `app/src/main/java/io/github/yuninggu/evolune/MainActivity.kt`
- `app/src/main/java/io/github/yuninggu/evolune/wear/WearDataLayer.kt`

Added production file:

- `app/src/main/java/io/github/yuninggu/evolune/application/WearDoseActionHandler.kt`

Modified JVM test:

- `app/src/test/java/io/github/yuninggu/evolune/wear/WearDataLayerTest.kt`

Added JVM tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/WearDoseActionHandlerTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/wear/WearProductionBypassBoundaryTest.kt`

Added instrumentation test:

- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/WearProductionCutoverTest.kt`

No changed file contains sensitive, real, or real-derived data. UUIDs, plans, timestamps, database names, and database rows used by tests are synthetic.

## 3. Production call-chain cutover

Before Batch 6C, the phone listener used this path:

```text
Wear DataItem
  -> WearableListenerService with runBlocking
  -> AppDatabase
  -> MedicationPlanDao / DoseEventDao
  -> legacy Entity conversion and upsert
  -> unconditional DataItem deletion
```

After Batch 6C, the path is:

```text
Wear DataItem
  -> strict path and payload parsing
  -> WearDoseActionHandler
  -> WearActionRecorder
  -> DoseEventRepository and MedicationPlanRepository contracts
  -> existing Room v3 Repository implementations
  -> accepted-only side effects
  -> exact current DataItem URI deletion
```

There is no dual write, fallback to the legacy Repository, replacement UUID, direct DAO/Entity write, second database, or second fact source.

## 4. Protocol compatibility and action identity

The protocol remains unchanged:

| Item | Locked value |
|---|---|
| Action path | `/hrt/dose-actions/<action_id>` |
| Plan request path | `/hrt/request-plans` |
| Plan projection path | `/hrt/plans` |
| Action payload keys | `plan_id`, `action_id`, `recorded_at` |
| Plan payload key | `plans_json` |
| PK payload keys | `current_concentration`, `curve_values`, `dashboard_updated_at` |

The Wear module still creates one random UUID for each tap. The same value is written to the action URI and `action_id` payload field. The phone parser requires the payload ID to be a valid UUID and to equal the URI ID. Invalid or mismatched values are rejected; no random, empty, or database ID is substituted.

`recorded_at` remains an epoch-millisecond `Long` written into the DataItem. DataItem redelivery therefore reuses the original timestamp rather than reading phone system time. Missing or non-positive values are invalid and do not write or delete the DataItem.

The accepted action ID is used unchanged as `DoseEvent.id`. No acknowledgement path, protocol version field, or additional key was added.

## 5. First materialization

Only an action ID that does not already exist reaches plan lookup and materialization:

1. validate `plan_id`, `action_id`, URI identity, and `recorded_at`;
2. read the Domain plan through `MedicationPlanRepository.getById`;
3. reject missing or disabled plans;
4. construct one Domain `DoseEvent` using the action ID and payload timestamp;
5. call the Repository contract through `WearActionRecorder`;
6. classify the typed result before any DataItem deletion.

New Wear events preserve complete Domain metadata:

- `id` is the watch action UUID;
- `occurredAt` is `Instant.ofEpochMilli(recorded_at)`;
- `zoneId` is the phone device zone at first materialization;
- `localDate` is derived from that occurrence and zone;
- `source=WEAR`;
- `status=RECORDED`;
- `revision=1`;
- `slotId=null`, because the protocol does not identify a scheduled slot;
- route, dose, ester, and extras come from the same complete Domain plan.

Plan edits affect only actions that have not yet been accepted. An accepted action replay does not reinterpret the stored event using the current plan.

## 6. Replay, conflict, and zero plan lookup

An existing event is an accepted replay only when all are true:

```text
existing.id == action_id
existing.source == WEAR
existing.occurredAt == Instant.ofEpochMilli(recorded_at)
```

The result is `FirstAcceptedReplay`. The stored event remains unchanged, and plan lookup and materializer calls are both zero. This remains true after plan edits and after process or database reopen.

The following are conflicts:

- the same action ID belongs to a non-Wear source;
- the same action ID has a different occurrence;
- first materialization returns the wrong ID, source, or occurrence;
- an insert race re-read does not satisfy the Wear source-plus-occurrence policy.

Conflicts do not overwrite, generate a replacement ID, read the plan to reinterpret the event, report success, or delete the DataItem.

Accepted reasons remain distinct:

| Acceptance | Meaning |
|---|---|
| `Inserted` | The Repository inserted the candidate |
| `RepositoryIdempotent` | The Repository proved full Domain equality |
| `FirstAcceptedReplay` | The Wear replay policy recognized a previously accepted action |

`FirstAcceptedReplay` is not relabelled as Repository idempotency.

## 7. DataItem deletion and retry

The locked order is:

```text
parse and validate
  -> WearActionRecorder
  -> accepted result
  -> accepted side effect
  -> delete the exact current DataItem URI
```

Only `Inserted`, `RepositoryIdempotent`, and `FirstAcceptedReplay` permit deletion. `Conflict`, `Invalid`, plan-not-found, plan-disabled, `StorageFailure`, unexpected failure, exception, and cancellation do not delete.

Deletion targets the exact action URI, not a parent path or wildcard. A deletion failure does not roll back the accepted event or insert it again. Redelivery is recognized as `FirstAcceptedReplay`, performs zero plan reads, and retries deletion. An accepted-side-effect failure similarly leaves the DataItem pending without repeating the database write.

Unknown paths and malformed items are ignored or rejected without deletion. No synthetic acknowledgement message or path exists.

## 8. Concurrency and listener lifecycle

`WearDoseListenerService` owns a controlled `CoroutineScope(SupervisorJob() + Dispatchers.IO)`. Message and DataItem deliveries launch work in that scope. It does not use `runBlocking`, `GlobalScope`, an Activity/ViewModel scope, or a fire-and-forget scope without service ownership. `onDestroy()` cancels the service scope, and `CancellationException` is rethrown rather than converted to success.

Each action is independently launched. Repository atomic insert behavior and `WearActionRecorder` are the final correctness boundary for duplicate and competing deliveries. Tests cover:

- concurrent identical actions with one authoritative row and accepted outcomes;
- the same action ID with different occurrences, producing one accepted result and one conflict;
- different actions without a permanent global lock;
- insert-race re-read;
- deletion failure followed by replay;
- process/database close and reopen;
- storage failure with no partial write or deletion.

One action failure does not cancel sibling work because the service uses a supervisor job.

## 9. Plan and PK projection

Wear plan requests now use:

```text
Wear request message
  -> ProductionRepositoryProvider
  -> MedicationPlanRepository.observeEnabled().first()
  -> Domain MedicationPlan projection
  -> unchanged Wear plan payload
```

MainActivity no longer creates a legacy `MedicationPlanRepository` or directly obtains `AppDatabase` for Wear projection. It uses `ProductionRepositoryProvider` and Domain plans. Enabled filtering, ordering, two-plan limit, path, keys, and JSON field names remain unchanged.

PK/concentration projection continues to consume the contract-backed HRT Domain state established by Batch 6A and the existing narrow Wear sampling helper. Event selection, ordering, time range, simulation algorithm, parameters, and absolute `1e-6` tolerance are unchanged. No formal public Domain-to-PK adapter was introduced; that remains Batch 7 work.

The Wear local plan and dashboard stores remain derived caches, not facts or a second database.

## 10. Disposable Room v3 integration

`WearProductionCutoverTest` uses only `batch6c_wear_test.db`. Setup and teardown remove the database and WAL, SHM, and journal sidecars. The production `evolune_database` is never opened.

The five integration tests verify:

- production handler use with `ProductionRepositoryProvider` contracts;
- action ID equals persisted event ID;
- complete Wear metadata and first insert;
- close/reopen and legal replay;
- plan edit followed by replay with zero plan reads;
- source and occurrence conflicts without row mutation or deletion;
- missing plan with no write;
- deletion failure followed by safe replay deletion retry;
- explicit Repository storage failure with no partial event write and no deletion;
- concurrent duplicate and differing-occurrence actions;
- one disposable database and `user_version=3`.

The storage-failure test uses a contract decorator over the real disposable Room provider to inject a persistence exception at insert. Closing Room is not used as a storage-failure substitute because Room cancellation must remain cancellation and propagate.

## 11. Validation results

All final commands below completed successfully. Test commands used rerun execution. Connected tests ran on emulator processes and are not inferred from androidTest compilation.

| Validation | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Batch 6C/replay focused JVM | 5 | 36 | 0 | 0 | 0 | PASS |
| Application JVM (Batch 5/6A/replay/6C) | 7 | 68 | 0 | 0 | 0 | PASS |
| Batch 6B reminder/Widget JVM | 7 | 37 | 0 | 0 | 0 | PASS |
| Migration JVM | 3 | 43 | 0 | 0 | 0 | PASS |
| Mapper JVM | 6 | 53 | 0 | 0 | 0 | PASS |
| Core JVM | 5 | 47 | 0 | 0 | 0 | PASS |
| PK JVM | 5 | 49 | 0 | 0 | 0 | PASS |
| Full App JVM | 42 | 359 | 0 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | 0 | PASS |
| API 33 Wear focused connected | 1 | 5 | 0 | 0 | 0 | PASS |
| API 33 full connected | actual run | 98 | 0 | 0 | 0 | PASS |
| API 35 Wear focused connected | 1 | 5 | 0 | 0 | 0 | PASS |
| API 35 full connected | actual run | 98 | 0 | 0 | 0 | PASS |

The final full connected suite contains:

- `AppDatabaseMigrationTest`: 18;
- `AppDatabaseMigrationMatrixTest`: 22;
- `AppDatabaseV2BaselineTest`: 2;
- Repository/provider production tests: 33;
- `WearProductionCutoverTest`: 5;
- receiver lifecycle: 5;
- UI and example instrumentation: 13.

Connected phone matrix:

| AVD | Serial | Android | API | Characteristics | Result |
|---|---|---:|---:|---|---|
| `Evolune_API33_Migration` | `emulator-5554` | 13 | 33 | phone/emulator | focused 5/5 and full 98/98 PASS |
| `Pixel_7` | `emulator-5558` | 15 | 35 | phone/emulator | focused 5/5 and full 98/98 PASS |

Additional validation:

| Command | Result | Notes |
|---|---|---|
| `:app:assembleDebug` | PASS | App debug APK available; validated task outputs were up-to-date after rerun compilation |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS | 1/1 |
| `:wear:assembleDebug` | PASS | Wear debug APK available; validated task outputs were up-to-date after rerun compilation |
| `:app:lintDebug --rerun-tasks` | PASS | 0 errors, 82 warnings, 1 informational issue |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | PASS | Compilation only; not reported as device execution |
| `:app:kspDebugKotlin --rerun-tasks` | PASS | KSP and Room schema generation completed |

Result artifacts:

- App JVM XML: `app/build/test-results/testDebugUnitTest/`;
- App JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`;
- Wear JVM XML: `wear/build/test-results/testDebugUnitTest/`;
- Wear JVM HTML: `wear/build/reports/tests/testDebugUnitTest/index.html`;
- connected XML: `app/build/outputs/androidTest-results/connected/debug/`;
- connected HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- lint HTML: `app/build/reports/lint-results-debug.html`;
- App APK: `app/build/outputs/apk/debug/app-debug.apk`;
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk`.

## 12. Wear OS AVD validation and automation boundary

The dedicated Wear OS target was `emulator-5556`:

| Property | Value |
|---|---|
| API | 35 |
| Model | `sdk_gwear_x86_64` |
| Characteristics | `emulator,nosdcard,watch` |

The Wear module `connectedDebugAndroidTest` task completed successfully on this device, but the module currently has no `androidTest` source and therefore executed 0 instrumentation tests. This is not reported as a device-test pass count.

Protocol creation remains validated by the unchanged Wear production code plus app JVM protocol assertions: one random action UUID per tap, one matching action URI, and the unchanged `plan_id`, `action_id`, and epoch-millisecond `recorded_at` fields. The current environment did not automate a real paired phone-to-watch DataClient round trip. That is an explicit automation boundary, not evidence of a protocol failure and not an additional accepted architecture P2.

## 13. Schema and forbidden-scope verification

Room remains internal version 3 with `exportSchema=true`.

| Schema | Identity hash | Canonical SHA-256 |
|---|---|---|
| v2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` |
| v3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` |

KSP regenerated schema output without a Git schema diff. The v2 canonical hash was verified from the committed Git blob because Windows-generated working-tree line endings do not alter the normalized tracked content.

There is no diff in:

- schema 2 or schema 3;
- `MIGRATION_2_3` or `AppDatabase`;
- Repository contracts or Room Repository implementations;
- Domain, DAO, or Entity files;
- receiver or Widget production code;
- HRT UI/ViewModel production code;
- Wear module production code;
- JSON v1;
- PK algorithms or parameters;
- Gradle or Manifest files;
- Batch 6 design, addendum, prior reports, or reviews.

## 14. Final production bypass-zero audit

The executable static boundary test passed as part of the 359-test App JVM suite. Manual repository-wide search found direct DAO, Entity, database, and legacy Repository references only in allowed persistence locations:

- Entity and DAO definitions;
- `AppDatabase`;
- legacy persistence implementations retained for compatibility but no longer called by production feature entry points;
- mappers;
- Room Repository implementations;
- `ProductionRepositoryProvider` composition implementation;
- tests.

There are zero forbidden hits in MainActivity, ViewModels, Compose/UI, application handlers, reminders, Widget, Wear listener/service, WearDataLayer, feature, or navigation production paths.

## 15. Risk classification

Final implementation classification:

```text
P0/P1/P2 = 0/0/1
```

The sole approved P2 remains unchanged:

- replay of an already accepted Wear action cannot revalidate the original first-materialization `plan_id`, because the current protocol does not persist plan identity in `DoseEvent`; `plan_id` is therefore first-materialization input only. Replay remains safely constrained by the watch-owned action ID, `source=WEAR`, and exact `occurredAt`, and it never overwrites the first accepted event. Protocol versioning must be reconsidered before the Batch 8 release gate.

The non-automated paired phone-to-watch round trip is a disclosed test-environment boundary, not merged into this architecture P2.

## 16. Decision

Batch 6 implementation passed pending independent review.

Batch 6 is not sealed until an independent read-only review is accepted and the implementation is formally committed and tagged. Batch 7 has not started. The production database was not used. Room v3 remains an internal, unreleasable version until the later release gates are satisfied.
