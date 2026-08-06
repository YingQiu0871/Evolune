# Evolune Phase 1 Batch 6B Report

Date: 2026-08-05

## 1. Scope

Batch 6B switches the remaining phone reminder receivers and the App Widget path to the Phase 1 Repository contracts:

- `MedicationReminderReceiver` reads Domain plans and events through contracts;
- `MedicationNotificationActionReceiver` records confirmed doses through contract `insert`;
- `ReminderRescheduleReceiver` reads Domain plans through the contract;
- reminder factory, matcher, and scheduling helpers consume Domain values;
- `EvoluneWidgetReceiver` reads plans and events and records quick actions through contracts;
- receiver deliveries use the approved `goAsync()`/`PendingResult` lifecycle;
- recorded-dose side effects run only after accepted persistence.

Batch 6B does not switch the Wear listener. It does not modify the phone HRT/UI cutover from Batch 6A, Repository contracts, Domain models, DAOs, Entities, Room Repository implementations, `AppDatabase`, migrations, schemas, Gradle, Manifest, JSON v1, PK algorithms, PK parameters, or Wear protocol. It does not start Batch 6C or Batch 7.

## 2. Files

Modified production files:

- `app/src/main/java/io/github/yuninggu/evolune/reminder/DoseCheckInMatcher.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationNotificationActionReceiver.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationPlanReminderSchedule.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/MedicationReminderReceiver.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderDoseFactory.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderManager.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderRescheduleReceiver.kt`
- `app/src/main/java/io/github/yuninggu/evolune/widget/EvoluneWidgetReceiver.kt`
- `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetUtils.kt`

Added production files:

- `app/src/main/java/io/github/yuninggu/evolune/application/RecordDoseEventAction.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReceiverWorkLauncher.kt`
- `app/src/main/java/io/github/yuninggu/evolune/reminder/ReminderReceiverWork.kt`
- `app/src/main/java/io/github/yuninggu/evolune/widget/WidgetWork.kt`

Modified JVM tests:

- `app/src/test/java/io/github/yuninggu/evolune/reminder/DoseCheckInMatcherTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/MedicationPlanReminderScheduleTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/ReminderDoseFactoryTest.kt`

Added JVM tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/RecordDoseEventActionTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/application/RepositoryFakes.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/Batch6ReceiverStaticBoundaryTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/ReceiverWorkLauncherTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/reminder/ReminderReceiverWorkTest.kt`
- `app/src/test/java/io/github/yuninggu/evolune/widget/WidgetWorkTest.kt`

Added instrumentation tests:

- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/ReceiverWidgetProductionCutoverTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/reminder/ReceiverLifecycleInstrumentationTest.kt`

## 3. Production call-chain cutover

Before Batch 6B:

```text
Reminder receivers / Widget
  -> AppDatabase or legacy Repository
  -> DAO / Entity / legacy upsert
```

After Batch 6B:

```text
Receiver or Widget shell
  -> ProductionRepositoryProvider
  -> core.dataapi Repository contracts
  -> Domain work delegate
  -> Room Repository implementation
  -> the existing Room v3 database
```

There is no old/new dual write, legacy failure fallback, second database, or second fact source in these paths.

## 4. Receiver lifecycle

`ReceiverWorkLauncher` gives every asynchronous delivery an independent `SupervisorJob` and `Dispatchers.IO` scope. Receivers use `applicationContext`; no Activity/ViewModel scope, shared static receiver scope, `GlobalScope`, or `runBlocking` is used.

The lifecycle is:

```text
onReceive validates synchronous input
  -> one goAsync()
  -> one independent work task
  -> Repository work and permitted side effects
  -> one PendingResult.finish() in the outermost finally
```

The finish path executes for accepted, replayed, conflict, not-found/stale, disabled, invalid, storage failure, unexpected exception, side-effect failure, and in-process cancellation outcomes. `finish()` represents lifecycle completion only; it is not a persistence or notification success acknowledgement.

Synchronous paths intentionally do not call `goAsync()`:

- reminder delivery with missing or invalid plan ID;
- notification skip, unknown action, or invalid plan ID;
- Widget broadcasts other than its record action and framework update callbacks.

`ReminderRescheduleReceiver` has no synchronous rejection and starts one controlled task for each delivery.

## 5. Notification confirmation

The notification confirmation path is:

```text
MedicationNotificationActionReceiver
  -> ContractNotificationActionWork
  -> MedicationPlanRepository.getById
  -> DoseEventRepository.getById / insert
  -> accepted-only Widget refresh and notification cancellation
```

The deterministic event ID remains:

```text
UUID.nameUUIDFromBytes(UTF8("reminder:<planId>:<scheduledAtMillis>"))
```

New event metadata is:

- actual confirmation time at millisecond precision;
- explicit device `zoneId` and matching `localDate`;
- `source=REMINDER`;
- `status=RECORDED`;
- `revision=1`;
- `slotId=null`;
- plan route, dose, ester, and extras.

The first accepted occurrence wins. An existing event with the same deterministic ID and `source=REMINDER` is a recognized replay and is not rewritten with a later confirmation time. Another source is a conflict. An insert race is re-read once and classified under the same rule.

Missing/deleted plans are stale and may cancel the stale notification without claiming a recorded dose. Conflict, invalid, storage failure, and unexpected failure run no recorded-dose success side effect. A side-effect failure after accepted persistence does not roll back or repeat the database write.

## 6. Reminder delivery and rescheduling

`MedicationReminderReceiver` reads the plan with `MedicationPlanRepository.getById` and events with `DoseEventRepository.findOccurredBetween`. The query uses the half-open interval:

```text
[scheduledAt - 1 hour, scheduledAt + 1 hour + 1 millisecond)
```

The Domain matcher retains the inclusive absolute `+/-1 hour` boundary and compares route, ester, dose within `1e-6`, and extras. A check-in suppresses the reminder. Enabled plans retain the existing next-batch scheduling behavior. Read or side-effect failure performs no database fallback or write.

`ReminderRescheduleReceiver` reads `MedicationPlanRepository.observeAll().first()` and passes Domain plans to `ReminderManager`. Existing fail-fast scheduling behavior, alarm request codes, ordering, exact-alarm fallback, and device-system time-zone semantics are unchanged.

## 7. Widget reads and PK compatibility

The Widget read path is:

```text
EvoluneWidgetReceiver / updateAllEvoluneWidgets
  -> MedicationPlanRepository.observeEnabled().first()
  -> DoseEventRepository.getEventsForPk(now)
  -> Widget-private Domain-to-PK projection
  -> existing SimulationEngine parameters
  -> RemoteViews render
```

The Widget projection is private to `WidgetWork.kt`. It preserves event selection/order, IDs, route, ester, dose, extras, and `LegacyTimeAdapter` time conversion. It does not mutate Domain events or write storage. The PK algorithm, parameters, two-step concentration calculation, and absolute `1e-6` regression tolerance are unchanged. The formal Domain-to-PK adapter remains Batch 7 work.

Widget snapshots are derived display values, not a database or fact source. Repository failures do not synthesize a successful event or write through a legacy path.

## 8. Widget quick record and refresh

The Widget quick-action event ID remains:

```text
UUID.nameUUIDFromBytes(UTF8("widget:<planId>:<epochMinute>"))
```

New event metadata is:

- actual handling time at millisecond precision;
- explicit device `zoneId` and matching `localDate`;
- `source=WIDGET`;
- `status=RECORDED`;
- `revision=1`;
- `slotId=null`;
- plan route, dose, ester, and extras.

The quick action calls `DoseEventRepository.insert`. The first accepted plan/minute event wins; a recognized same-source replay is not rewritten to a later millisecond. Another-source collision is explicit conflict and cannot overwrite. Disabled, missing, invalid, conflict, storage, and unexpected outcomes show no recorded success.

Side-effect order is:

```text
accepted Repository result
  -> refresh Widget from current Domain state
  -> show recorded Toast
  -> finish receiver delivery
```

If persistence succeeds and Widget/Toast work fails, the row remains committed and is not inserted again. If persistence fails, no success Toast or success refresh is produced.

## 9. Tests added for Batch 6B

The focused JVM command ran eight suites with 45 tests, 0 failures, and 0 skipped. Coverage includes:

- stable reminder and Widget IDs and complete metadata;
- first insert, recognized replay, conflict, invalid, storage failure, and exception;
- duplicate delivery with one stored row;
- success-side-effect ordering and side-effect failure after commit;
- reminder plan/event reads, inclusive window boundaries, and no writes;
- reschedule behavior and storage failure;
- Widget enabled-plan/event reads, event order, PK parity, and snapshot rendering;
- receiver `finish()` exactly once for every typed result, exception, and cancellation;
- independent jobs and no cross-delivery cancellation;
- static source boundaries excluding DAO, Entity, legacy Repository, `GlobalScope`, and `runBlocking`.

The focused instrumentation set contains five receiver lifecycle tests and two disposable-Room production-cutover tests. It passed 7/7 independently on both API 33 and API 35 phone AVDs.

## 10. Disposable Room v3 integration

`ReceiverWidgetProductionCutoverTest` uses only `batch6b_receiver_widget_test.db`. It never opens `evolune_database`.

The two integration tests verify:

- `ProductionRepositoryProvider` contracts over one supplied Room database;
- notification and Widget inserts;
- deterministic replay without a second write;
- same-ID/another-source conflict without overwrite;
- synthetic trigger-based storage failure with no partial row or success side effect;
- complete metadata persistence;
- plan/slot read after close and reopen;
- `user_version=3`;
- no second database, while allowing only the test database's standard WAL/SHM/journal sidecars;
- cleanup of the disposable database and sidecars.

All fixtures use synthetic UUIDs, names, values, and timestamps. No real or real-derived user or medication data is present.

## 11. Dual-phone connected matrix

Both devices were verified as phone AVDs with characteristics `emulator`, not `watch`.

| Validation | Device | Serial | Android/API | Tests | Failures | Skipped | Result |
|---|---|---|---|---:|---:|---:|---|
| Batch 6B focused | Evolune API 33 phone | `emulator-5560` | Android 13 / API 33 | 7 | 0 | 0 | PASS |
| Batch 6B focused | Pixel 7 phone | `emulator-5558` | Android 15 / API 35 | 7 | 0 | 0 | PASS |
| Full connected | Evolune API 33 phone | `emulator-5560` | Android 13 / API 33 | 91 | 0 | 0 | PASS |
| Full connected | Pixel 7 phone | `emulator-5558` | Android 15 / API 35 | 91 | 0 | 0 | PASS |
| Repository/migration matrix | Evolune API 33 phone | `emulator-5560` | Android 13 / API 33 | 73 | 0 | 0 | PASS |

Connected reports:

- HTML: `app/build/reports/androidTests/connected/debug/index.html`;
- API 33 XML at execution: `app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`;
- API 35 XML at execution: `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`.

The full 91-test suites include migration, migration matrix, v2 baseline, Room Repository, provider, plan/event production cutover, Batch 6B receiver/Widget production cutover, receiver lifecycle, phone Compose screens, and the variant-aware example test. The Wear OS AVD `emulator-5556` was not credited as phone validation.

## 12. Complete regression

| Validation | Suites/classes | Tests | Failures | Skipped | Exit | Result |
|---|---:|---:|---:|---:|---:|---|
| Batch 6B focused JVM | 8 | 45 | 0 | 0 | 0 | PASS |
| Batch 6A editor/HRT/compatibility JVM | 3 | 27 | 0 | 0 | 0 | PASS |
| Batch 5 plan JVM | 3 | 35 | 0 | 0 | 0 | PASS |
| Migration JVM | 3 | 43 | 0 | 0 | 0 | PASS |
| Mapper JVM | 6 | 53 | 0 | 0 | 0 | PASS |
| Core JVM | 5 | 47 | 0 | 0 | 0 | PASS |
| Full App JVM | 39 | 334 | 0 | 0 | 0 | PASS |
| PK regression | 5 | 49 | 0 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | 0 | PASS |
| API 33 full connected | 1 XML aggregate | 91 | 0 | 0 | 0 | PASS |
| API 35 full connected | 1 XML aggregate | 91 | 0 | 0 | 0 | PASS |
| App `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| Wear `assembleDebug` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `lintDebug` | N/A | N/A | 0 errors | N/A | 0 | PASS |
| App `compileDebugAndroidTestKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |
| App `kspDebugKotlin` | N/A | N/A | N/A | N/A | 0 | PASS |

Artifacts and reports:

- App APK: `app/build/outputs/apk/debug/app-debug.apk` (69,849,455 bytes);
- Wear APK: `wear/build/outputs/apk/debug/wear-debug.apk` (14,565,775 bytes);
- App JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`;
- Wear JVM HTML: `wear/build/reports/tests/testDebugUnitTest/index.html`;
- lint HTML: `app/build/reports/lint-results-debug.html`.

Lint completed with 0 errors, 81 warnings, and 1 hint. The findings are non-blocking existing locale, deprecation, Compose/style, and resource findings. The recurring SDK tooling warning states that the installed processor understands SDK XML through version 3 while encountering version 4.

App and Wear assemble tasks completed successfully. Their final package tasks were `UP-TO-DATE`, and the APK artifacts were present. This is accepted build verification; all test commands above used `--rerun-tasks` and executed their tests.

## 13. Validation fixes

Validation exposed only test-fixture issues; production failure semantics were not relaxed:

- the instrumentation latch helper was renamed so Java's zero-argument `CountDownLatch.await()` did not shadow a Boolean Kotlin extension;
- disposable Room plan slots now use the locked UUIDv5 `ScheduledDoseSlotId` generator instead of arbitrary fixture IDs;
- the single-database assertion permits only the selected database and its standard WAL/SHM/journal sidecars;
- dynamic receiver test broadcasts are explicitly package-targeted so Android 15 delivers them deterministically to the registered target receiver.

API 33 and API 35 both passed after these test-only corrections.

## 14. Boundary and bypass audit

Static production scans confirm:

- the three reminder receivers contain no DAO, Entity, legacy Repository, `AppDatabase`, `GlobalScope`, or `runBlocking` access;
- reminder factory, matcher, and scheduling helpers consume Domain types;
- Widget production code contains no DAO, Entity, legacy Repository, `AppDatabase`, `GlobalScope`, or `runBlocking` access;
- notification and Widget writes use only contract `insert` through the shared action boundary;
- reminder and Widget reads use only contracts;
- no fallback or dual write exists;
- persistence implementation, mapper, migration, and DAO/Entity definitions remain unchanged and are expected internal persistence references.

Remaining production bypasses are limited to the deferred Wear path:

- `WearDataLayer.kt` still uses `AppDatabase`, DAO/Entity, and `runBlocking` for the phone-side Wear listener;
- `MainActivity.kt` still constructs the legacy medication-plan Repository only for Wear dashboard synchronization.

Those references are the locked Batch 6C boundary. No receiver or Widget bypass remains after Batch 6B.

## 15. Database, schema, and unchanged gates

- `AppDatabase` remains version 3 with `exportSchema = true`.
- Schema 2 identity hash remains `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Schema 3 identity hash remains `c5f5e02cb04b048ca28fe96a74d61606`.
- Schema 2, schema 3, and `MIGRATION_2_3` have no Git difference.
- `:app:kspDebugKotlin --rerun-tasks` executed and did not change either schema.

Canonical tracked SHA-256 values, independently read from the Git blobs, are:

```text
schema 2: B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA
schema 3: 044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72
```

Repository contracts, Domain, DAO/Entity, Room Repository, `AppDatabase`, migration, schema, JSON v1, Gradle, Manifest, PK algorithm/parameters, Batch 6 design/reviews, HRT/UI, and Wear production files have no Batch 6B Git difference.

Migration rollback, foreign keys, cascade behavior, uniqueness, slot order, duplicate times, and UUIDv5 remain covered by the 73-test Repository/migration matrix and both full connected runs. PK absolute tolerance remains `1e-6`, and the PK suite passed 49/49.

## 16. Transitional status

The accepted transition remains:

- phone HRT/UI is already on contracts from Batch 6A;
- reminder receivers and Widget are on contracts after Batch 6B;
- phone-side Wear listener/dashboard synchronization remains Batch 6C;
- formal JSON v1 and Domain-to-PK adapters remain Batch 7;
- the same Room database remains the single fact source;
- no real database or real/derived health data was used;
- Room v3 remains internal and non-releasable.

No unresolved implementation finding remains before independent review: P0/P1/P2 = `0/0/0`.

## 17. Batch decision

Batch 6B implementation passed pending independent review.

Batch 6B implementation and validation are complete. The next permitted action is DeepSeek independent read-only review. This report does not authorize staging, committing, tagging, starting Batch 6C, opening a real user database, creating a release, or claiming that Room v3 is releasable.
