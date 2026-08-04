# Evolune Phase 1 Batch 5 Production Wiring Design

Date: 2026-08-04

Branch: `phase1/production-wiring-design`

Baseline tag: `phase-1-batch-3c`

Status: design and read-only production call-chain audit; no runtime wiring is implemented by this document.

## 1. Authority and next batch

The authoritative next batch is **Batch 5: 双读双写与计划槽位切换**. `docs/PHASE_1_DESIGN.md:843-854` defines its files and acceptance criteria. The following order is already fixed:

1. Batch 5 switches medication-plan aggregate persistence and slot-aware plan behavior.
2. Batch 6 switches the remaining HRT, reminder receiver, Widget, Wear, and composition-root entry points to contracts (`docs/PHASE_1_DESIGN.md:856-867`).
3. Batch 7 switches JSON v1 and the Domain-to-PK adapter (`docs/PHASE_1_DESIGN.md:869-878`).
4. Batch 8 performs the Phase 1 exit audit and still does not remove legacy columns (`docs/PHASE_1_DESIGN.md:880-894`).

The roadmap does not authorize replacing this sequence with a newly named permanent batch. This document therefore uses `PHASE_1_BATCH_5_DESIGN.md`.

### 1.1 Documentation consistency notes

- `docs/evolune/ARCHITECTURE.md:117` still describes Room v2 and two entities. Actual committed code is Room v3 with `ScheduledDoseSlotEntity` (`AppDatabase.kt:15-28`). This is stale status text, not a competing target architecture.
- ADR-015 and the Batch 3C heading retain wording that 3C includes production/composition-root wiring (`docs/evolune/DECISIONS.md:146-160`, `docs/PHASE_1_DESIGN.md:800-802`). The accepted Batch 3C report and review, current production references, and the more specific Batch 5/6 sequence prove that 3C delivered Repository implementations without runtime callers. This is historical scope drift, not a contract or schema disagreement.
- ADR-013's dependency direction remains consistent: `feature -> core:data-api <- database implementation` (`docs/evolune/DECISIONS.md:123-131`).
- ADR-014 Slot ID v1 and ADR-016 migration/release rules remain unchanged.

No new ADR is required for the design below. A later documentation-only cleanup may correct stale status/scope wording, but must not redefine Batch 5-8.

## 2. Current production data flow

### 2.1 Database creation

- `AppDatabase.getDatabase()` owns the only production `Room.databaseBuilder` and caches a volatile singleton (`AppDatabase.kt:30-73`).
- The database name is `evolune_database`; migrations `1 -> 2` and `2 -> 3` are registered (`AppDatabase.kt:38-69`).
- Production source scanning found no second Room builder and no in-memory production database.
- ViewModel `StateFlow` values are projections of Room flows, not independent facts. App-side Wear preferences cache concentration/curve output only (`WearDataLayer.kt:41-72`). The Wear app's `WearPlanStore` is a paired-device derived cache, not the phone medication fact source.

### 2.2 Main App composition and UI flow

```text
MainActivity
  -> AppDatabase singleton
  -> legacy data.DoseEventRepository(DoseEventDao)
  -> legacy data.MedicationPlanRepository(MedicationPlanDao)
  -> HRTViewModel / MedicationPlanViewModel
  -> Compose screens and form components
  -> legacy Repository
  -> DAO / Entity
  -> Room v3
```

Evidence:

- `MainActivity.kt:38-41` creates both legacy concrete repositories from DAO accessors.
- `MainActivity.kt:82-100` injects those concrete classes into both ViewModels.
- `HRTViewModel.kt:6-8,37-40` depends on legacy repositories and the PK `DoseEvent` model.
- `MedicationPlanViewModel.kt:7-22` depends on legacy `data.MedicationPlan` and the legacy concrete repository.
- `MedicationRecordsScreen.kt:65-74,87-103` creates/edits PK `DoseEvent` and calls legacy-style ViewModel upsert/delete methods.
- `MedicationPlanBottomSheet.kt:313-327` creates legacy `data.MedicationPlan`; `MedicationPlansScreen.kt:103-153` sends it to the ViewModel.

### 2.3 DoseEvent read/write path

Current App UI path:

```text
MedicationRecordsScreen / MedicationRecordBottomSheet
  -> HRTViewModel.upsertEvent/deleteEvent
  -> data.DoseEventRepository
  -> DoseEventDao legacy queries/@Upsert
  -> DoseEventEntity legacy mapper
  -> Room v3
```

- Reads use legacy `timeH` ordering and `DoseEventEntity.toDoseEvent()` (`data/DoseEventRepository.kt:17-39`).
- Writes use `DoseEventEntity.fromDoseEvent()` and DAO `@Upsert` (`data/DoseEventRepository.kt:42-60`, `DoseEventDao.kt:93-133`).
- The legacy Entity mapper supplies v3 `occurredAtEpochMillis` but leaves new event metadata at legacy defaults (`DoseEventEntity.kt:55-74`).
- The current update UI carries only the PK model and therefore loses `source`, `status`, `zoneId`, `localDate`, `slotId`, and `revision` before an edit (`MedicationRecordBottomSheet.kt:367-375`).

Implemented but unused contract path:

```text
core.dataapi.DoseEventRepository
  <- RoomDoseEventRepository
  -> v3 mapper
  -> repository-specific DAO methods
  -> Room v3
```

`RoomDoseEventRepository` implements Domain flows, half-open range reads, frozen PK selection, idempotent insert, explicit conflict, CAS update, and typed results (`RoomDoseEventRepository.kt:22-178`). Production code has no reference to this class; its current callers are instrumentation tests only.

### 2.4 MedicationPlan read/write path

Current App plan editor path:

```text
MedicationPlansScreen / MedicationPlanBottomSheet
  -> MedicationPlanViewModel
  -> data.MedicationPlanRepository
  -> MedicationPlanDao legacy queries/@Upsert
  -> MedicationPlanEntity
  -> medication_plans only
```

- Legacy reads return `data.MedicationPlan` from `MedicationPlanEntity.timeOfDay` (`data/MedicationPlanRepository.kt:12-35`).
- Legacy writes call `MedicationPlanDao.upsertPlan()` and do not update `scheduled_dose_slots` (`data/MedicationPlanRepository.kt:37-63`, `MedicationPlanDao.kt:78-118`).
- The editor adds, removes, and edits plain `LocalTime` values (`MedicationPlanBottomSheet.kt:78-80,245-259`) and never carries Slot IDs.

Implemented but unused aggregate path:

```text
core.dataapi.MedicationPlanRepository
  <- RoomMedicationPlanRepository
  -> MedicationPlanAggregateEntity + v3 mapper
  -> MedicationPlanDao + ScheduledDoseSlotDao
  -> one Room transaction
  -> medication_plans.timeOfDay + scheduled_dose_slots
```

`RoomMedicationPlanRepository.save()` validates the Domain aggregate, writes the plan and complete slots replacement in one transaction, verifies affected rows and rereads the aggregate before success (`RoomMedicationPlanRepository.kt:45-97`). No production caller currently uses it.

### 2.5 JSON and PK

- File/clipboard import is initiated by `AppNavigation.kt:125-176` and handled by `HRTViewModel.importFromMahiroJson()` (`HRTViewModel.kt:122-147`).
- `MahiroJsonFormat` directly creates and exports PK `DoseEvent`, including the accepted random UUID fallback for missing/damaged IDs (`MahiroJsonFormat.kt:90-176`).
- Import writes each parsed PK event through the legacy repository (`HRTViewModel.kt:131-142`).
- PK history comes from the legacy repository's 30-day/20-event selection (`HRTViewModel.kt:179-203`, `data/DoseEventRepository.kt:23-40`).
- `MedicationPlanPredictor` consumes legacy plans and produces transient PK events, converting device-local `LocalDateTime` with `atZone(systemDefault())` (`MedicationPlanPredictor.kt:31-117`).
- `SimulationEngine` receives PK events directly (`HRTViewModel.kt:220-256`).

JSON v1 and the Domain-to-PK adapter remain Batch 7 work. Batch 5 must not partially convert JSON or change PK parameters, event selection, order, or the `1e-6` regression tolerance.

### 2.6 Reminder, Widget, and Wear

- `MedicationReminderReceiver` directly reads plan/event DAOs and legacy Entity mappers (`MedicationReminderReceiver.kt:38-64`).
- `MedicationNotificationActionReceiver` directly reads `MedicationPlanDao` and writes a `DoseEventEntity` through `DoseEventDao.upsertEvent()` (`MedicationNotificationActionReceiver.kt:53-67`).
- `ReminderRescheduleReceiver` constructs a legacy plan repository from the DAO (`ReminderRescheduleReceiver.kt:22-26`).
- `ReminderManager` is database-free but accepts legacy plans and derives alarms from `timeOfDay` (`ReminderManager.kt:20-195`).
- `EvoluneWidgetReceiver` directly reads plans, directly writes event entities, and constructs a legacy event repository for PK concentration (`EvoluneWidgetReceiver.kt:105-122,211-260`).
- `WearDoseListenerService` directly reads plan DAOs and writes event entities; the Wear action ID is used as the event ID (`WearDataLayer.kt:117-176`).
- `MainActivity` also pushes plan/PK projections to Wear after observing legacy ViewModel state (`MainActivity.kt:101-116`).

These are real production bypasses, but the authoritative sequence assigns receiver/Widget/Wear conversion to Batch 6. Batch 5 may adapt `ReminderManager` only where plan-editor scheduling is inseparable from a successful Domain plan save; it must not switch the receivers, Widget, Wear payload, or Wear listener.

## 3. Repository bypass inventory

| Location | Current behavior | Risk | Batch 5 action |
|---|---|---|---|
| `MainActivity.kt:39-41` | Constructs legacy repositories from DAOs | Contract implementations remain unused | Create/use one production provider for the plan editor slice; keep temporary legacy dependencies only for deferred consumers |
| `HRTViewModel.kt:37-69` | Exposes PK events and legacy plans | Domain metadata/revision absent | Defer to Batch 6; do not force a lossy adapter |
| `MedicationPlanViewModel.kt:20-105` | Uses legacy plan repository | Plan writes can desynchronize slots | Must switch atomically in Batch 5 |
| `MedicationReminderReceiver.kt:38-50` | Direct plan/event DAO reads | UI-independent bypass | Defer to Batch 6 |
| `MedicationNotificationActionReceiver.kt:53-66` | Direct DAO event write | Can bypass idempotent/conflict/source semantics | Defer to Batch 6, but remain a P1 until switched |
| `ReminderRescheduleReceiver.kt:22-25` | Creates legacy repository | Bypasses provider/contract | Defer to Batch 6 |
| `EvoluneWidgetReceiver.kt:110-119` | Direct enabled-plan read | Corrupt rows are silently skipped | Defer to Batch 6 |
| `EvoluneWidgetReceiver.kt:211-237` | Direct plan read and event Entity upsert | Bypasses Domain metadata/results | Defer to Batch 6 |
| `EvoluneWidgetReceiver.kt:245-260` | Legacy event repository feeds PK | Still reads `timeH` | Defer to Batch 6/7 |
| `WearDataLayer.kt:121-128` | Direct enabled-plan read | Bypasses aggregate mapper | Defer to Batch 6 |
| `WearDataLayer.kt:145-166` | Direct plan read and event Entity upsert | Bypasses source/revision/conflict | Defer to Batch 6 |
| `MahiroJsonFormat.kt:90-176` | JSON directly reuses PK model | External DTO/domain boundary absent | Defer to Batch 7 |
| `MedicationPlanPredictor.kt:31-117` | Legacy plan to PK projection | Does not consume stable slots | Add a Domain-plan path and parity tests in Batch 5; keep legacy path only for deferred callers |

No direct DAO/Entity usage may be newly introduced. Every deferred bypass is an explicit tracked transition, not permission to add another caller.

## 4. Target architecture

```text
feature/application
  -> core.dataapi Repository contracts
  <- data.repository Room implementations
  -> DAO
  -> the existing AppDatabase singleton
  -> Room v3 (single fact source)

Repository Domain output
  -> explicit PK adapter
  -> Reminder adapter
  -> Widget snapshot/action adapter
  -> Wear adapter
```

Required properties:

1. The provider obtains the existing `AppDatabase` singleton; it never calls a second Room builder.
2. The provider exposes contract-typed repositories and hides DAO/Entity/database access from feature code.
3. `RoomMedicationPlanRepository` is the sole plan write owner after the Batch 5 cutover.
4. `scheduled_dose_slots` is the v3 authority; `medication_plans.timeOfDay` is a transactionally synchronized rollback shadow.
5. Temporary legacy consumers may read the same Room database while they await Batch 6, but they may not write the plan aggregate.
6. Repository failure is surfaced. No caller falls back to legacy DAO/repository writes.
7. Widget, Wear caches, and ViewModel state remain derived projections and cannot become alternate fact sources.

## 5. Switching strategies

| Strategy | Consistency | Rollback | Test cost | Transitional impact | Decision |
|---|---|---|---|---|---|
| A. Switch all App DoseEvent and MedicationPlan entries at once | Strong only if every adapter is complete | Large rollback surface | Highest; JSON/PK/UI/receivers change together | Violates Batch 5-7 sequencing and risks losing event revision/source metadata | Reject |
| B. Switch one aggregate as a vertical read/write slice | Strong within the selected aggregate | Revert one bounded internal commit; unchanged consumers still read the same DB shadows | Moderate and focused | Allows plan editor to become slot-authoritative while HRT/JSON/Widget/Wear remain unchanged | **Recommend** |
| C. Dual-read/dual-write old and new repositories | Two results can disagree; ordering and error semantics diverge | Ambiguous source during rollback | Very high comparison/failure matrix | Creates two write owners and invites silent fallback | Reject |

The phrase “双写” in Batch 5 means one Room repository transaction writes the v3 authority and its retained legacy shadow. It does **not** mean invoking both old and new repositories, writing two databases, or retrying through DAO after a Repository failure.

## 6. Recommended Batch 5 implementation scope

### 6.1 One aggregate, not both

Batch 5 should switch the MedicationPlan editor/read/write vertical slice only. DoseEvent production switching must remain in Batch 6, with JSON and PK adapters completed in Batch 7.

Switching DoseEvent in Batch 5 is not safe because:

- existing edit UI discards `revision` and all new Domain metadata;
- legacy `upsert` cannot be mapped unambiguously to contract `insert` versus CAS `update`;
- JSON import is embedded in `HRTViewModel` and still emits PK events;
- simulation still requires an explicit Domain-to-PK adapter;
- reminder, Widget, and Wear event writers still call DAO/Entity directly.

These are adapter/caller gaps, not missing Repository contract methods. The contracts and Room implementations are otherwise sufficient; no contract change is authorized.

### 6.2 Plan cutover behavior

The Batch 5 plan slice must:

1. Add an application-scoped production Repository provider/factory around the existing `AppDatabase` singleton.
2. Inject `core.dataapi.MedicationPlanRepository` into `MedicationPlanViewModel`.
3. Expose `core.model.MedicationPlan` from the plan ViewModel and plan editor UI.
4. Convert the editor's ordered local-time list into a valid slot list using Slot ID v1, `position == index`, and minute precision.
5. Call `save(plan)` for plan creation/edit, `setEnabled()` for enable changes, and `delete()` for deletion.
6. Treat `Created`, `Updated`, and `NoChange` as successful save outcomes; treat `Invalid` and infrastructure exceptions as explicit failures.
7. Schedule/cancel reminders only after the Repository operation reports success. A failed save must not change alarms or close the editor as if it succeeded.
8. Preserve the existing visual behavior, ordering, duplicate times, schedule semantics, device time-zone/DST rules, and reminder windows.
9. Keep HRTViewModel's temporary legacy plan reads as a same-database compatibility reader until Batch 6. Plan UI must no longer call the legacy plan writer.
10. Keep `WearDataLayer` unchanged. If `MainActivity` needs a legacy plan list for the unchanged Wear signature, source it from the existing legacy HRT plan flow rather than converting the new Repository result inside Wear code.

Slot identity details:

- Rebuilding an unchanged `(planId, position, localTime)` produces the same ID by ADR-014.
- Editing only one time at a stable position changes only that slot's ID.
- Removing/reordering an earlier item changes later positions and therefore legitimately changes those Slot IDs. Batch 5 tests must not assert identity preservation when an ADR-014 identity component changed.

### 6.3 Supporting layers

- **Provider required:** yes. It must construct `RoomDoseEventRepository` and `RoomMedicationPlanRepository` from one existing database instance and expose contract types. Batch 5 only consumes the plan contract; creating both once prevents a second ad hoc composition path in Batch 6.
- **Application service/use case:** no broad use-case layer is required. A small pure plan-editor/slot builder is justified to keep UUIDv5 generation and validation out of Compose. ViewModel remains the orchestration owner for Repository result handling and reminder side effects.
- **Coroutine dispatcher:** no new dispatcher abstraction is required. Room suspend/Flow APIs already handle database execution; current receivers already use IO scopes. Pure CPU parsing remains explicitly dispatched where it already is.
- **Transaction facade:** not required. `RoomMedicationPlanRepository.save()` already owns the necessary plan+slots transaction. Wrapping it in another transaction abstraction would obscure ownership.
- **Contract change:** none. If implementation proves a contract method cannot preserve required behavior, stop Batch 5 and open a separate design decision rather than extending the interface in place.

### 6.4 Reviewable sub-slices

Batch 5 may be implemented as two reviewable commits under the existing Batch 5 number, not as new roadmap batches:

1. **Provider and pure plan adapters:** provider/factory, slot/draft builder, result-handling tests; no runtime caller switched yet.
2. **Atomic plan entry cutover:** plan ViewModel/UI, predictor/reminder compatibility, MainActivity wiring, and connected integration tests.

The Batch 5 tag/report is created only after both sub-slices pass. The second sub-slice must not leave a build where plan reads use the new aggregate but plan writes still use legacy `@Upsert`, or vice versa.

## 7. Explicit exclusions

Batch 5 must not:

- switch HRTViewModel DoseEvent read/write;
- change JSON v1 parsing, export fields, valid/random UUID behavior, or import conflict policy;
- modify `SimulationEngine`, PK parameters, route/ester values, or tolerances;
- switch reminder receivers, Widget, or Wear listener DAO calls;
- change Wear paths, keys, payloads, caches, or the Wear module;
- modify Room entities, DAOs, schema 2/3, `AppDatabase` version, or migrations;
- remove or reinterpret `timeH` or `timeOfDay`;
- add another database, an in-memory fact source, Hilt, WorkManager, Health Connect, Glance, cloud sync, Tracked Date, or destructive migration;
- run against, upgrade, or install over a real user database;
- claim Room v3 is releasable.

## 8. Expected file scope

### 8.1 Expected additions

| File | Responsibility |
|---|---|
| `app/src/main/java/io/github/yuninggu/evolune/data/repository/ProductionRepositoryProvider.kt` | Obtain the existing database once and expose contract-typed Room repositories; include a database-injection seam for instrumentation |
| `app/src/main/java/io/github/yuninggu/evolune/application/MedicationPlanDraftMapper.kt` | Pure ordered-times/domain-slots construction using Slot ID v1; no Android/Room/UI dependency |
| focused JVM tests under `app/src/test/.../application/` and `viewmodel/` | Slot identity, duplicates/order, result handling, no reminder on failure |
| focused instrumentation test under `app/src/androidTest/.../repository/` | Provider and production plan entry persistence/rollback/reopen proof against a disposable database |

Exact helper names may follow local naming during implementation, but responsibilities and dependency directions are fixed.

### 8.2 Expected modifications

- `MainActivity.kt`: obtain contract repositories from the provider and inject the plan contract; retain temporary legacy dependencies only for unchanged Batch 6/7 consumers.
- `MedicationPlanViewModel.kt`: depend on the contract/Domain model, map result types, and order reminder side effects after successful persistence.
- `MedicationPlansScreen.kt`, `MedicationPlanBottomSheet.kt`, `MedicationPlanCard.kt`: use the Domain aggregate and slot-aware draft conversion without direct DAO/Entity access.
- `MedicationPlanPredictor.kt`: add/use a Domain plan/slots path with parity tests while retaining the legacy entry only for deferred callers.
- `ReminderManager.kt`: accept a Domain plan or a narrow explicit adapter for plan-editor scheduling; receiver classes remain unchanged.
- Existing corresponding JVM/UI tests: update expected model types and preserve all behavior assertions.

### 8.3 Files that should remain unchanged

- `AppDatabase.kt`, every Entity, DAO, mapper, Repository contract, and Room Repository implementation unless a separately proven P0/P1 defect is found;
- both Room schema JSON files and all migration files/tests;
- `HRTViewModel.kt` and medication record UI;
- `MahiroJsonFormat.kt`;
- `SimulationEngine.kt` and PK parameter files;
- reminder receivers/factory/matcher;
- `EvoluneWidgetReceiver.kt`, `WidgetUtils.kt`;
- `WearDataLayer.kt` and the complete `wear` module;
- Gradle files, Manifest, resources, and release configuration.

## 9. Test plan

### 9.1 JVM

- Provider surface is contract-typed and does not expose DAO/Entity.
- Plan ViewModel observes Domain plans and handles every `PlanSaveResult`, `PlanUpdateResult`, and `DeleteResult` branch.
- Save failure/`Invalid`/exception does not schedule or cancel reminders and does not report success.
- Created/Updated/NoChange behavior preserves current UI semantics; NotFound/Invalid are explicit.
- Empty slots, one/many slots, duplicate local times, original order, and minute precision.
- Fixed UUIDv5 vector remains `17d1fd14-9d70-5344-beaa-0b158c9f62f4`.
- Editing a time regenerates only the identity whose canonical inputs changed; no sorting, deduplication, or silent repair.
- DAILY/WEEKLY/CUSTOM predictor count, time, order, device-zone `atZone` DST behavior, and current baseline parity.
- Existing reminder windows and scheduling count/order remain unchanged.
- Static dependency scan proves plan ViewModel/UI contain no Room/DAO/Entity imports.
- Existing mapper, migration, core, App, PK, Wear, Widget, and reminder JVM regressions remain green.

### 9.2 Instrumentation

- Production provider returns `RoomDoseEventRepository`/`RoomMedicationPlanRepository` behind contract types and both share the same supplied `AppDatabase`.
- Save through the plan application/ViewModel entry writes both `timeOfDay` and slots; close/reopen preserves the Domain aggregate.
- Empty, ordered, duplicate, boundary-time, and enabled/disabled plans persist correctly.
- Triggered slot insert failure rolls back plan and slots, produces no reminder side effect, and never invokes legacy DAO fallback.
- Existing aggregate content remains unchanged after failure; another plan remains unaffected.
- No conflict silently overwrites an unrelated aggregate.
- `timeOfDay` remains byte/semantic compatible with slots and no orphan/position duplication appears.
- Schema 2 and 3 files and hashes remain unchanged.

### 9.3 Required regression matrix

At minimum rerun the accepted baselines:

- Repository instrumentation: 23 tests;
- full connected instrumentation: 66 tests;
- migration matrix and migration tests, including the existing 43 connected migration/baseline/matrix tests;
- migration primitives: 43 JVM tests;
- mapper tests: 53 JVM tests;
- core tests: 47 JVM tests;
- full App JVM suite: current baseline 231 tests, plus new Batch 5 tests;
- PK regression: 49 tests with `1e-6` absolute tolerance;
- Wear JVM test, App/Wear debug builds, lint with zero errors, androidTest Kotlin compilation, KSP/schema verification.

Counts must be taken from actual JUnit XML and updated if the suite grows. Compilation is not instrumentation execution.

## 10. Database safety gates

1. Use only synthetic fixtures and disposable emulator/test databases.
2. Do not open, copy, upgrade, or install over a real or real-derived user database.
3. Do not distribute an internal v3 APK/AAB or create a formal release.
4. Preserve v2 identity hash `a8036e3f5ed6bb42d0e7289ac84039f3` and canonical SHA-256 `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
5. Preserve v3 identity hash `c5f5e02cb04b048ca28fe96a74d61606` and SHA-256 `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`.
6. Keep all migration connected tests, repository rollback tests, foreign-key checks, cascade checks, uniqueness checks, and deterministic Slot ID checks passing.
7. Verify there is still exactly one production Room builder and one database filename.
8. Verify every plan-editor write reaches only `RoomMedicationPlanRepository.save/setEnabled/delete`.
9. Verify no Repository error is followed by legacy Repository or DAO write fallback.

Before any controlled real-database rehearsal can be proposed, Batch 5, Batch 6, Batch 7, and Batch 8 evidence must exist; all production entries must use contracts; JSON/PK adapters must pass; schema/migration/repository/device matrices must remain green; the offline v2 repair tool must have target Python 3.12 validation; and the project owner must explicitly authorize an offline-copy rehearsal. This document grants no such authorization.

## 11. Rollback strategy

- Before release, rollback is a source-level revert of the bounded Batch 5 commits. No real database is upgraded during this interval.
- On Room v3, a temporary legacy **read** adapter may be selected for deferred consumers because `timeH` and `timeOfDay` remain shadows.
- Legacy plan writes must not be re-enabled after the plan cutover: they do not maintain slots and would create an inconsistent aggregate.
- Repository failures propagate to the ViewModel/application result path. There is no silent retry, DAO fallback, or old/new dual write.
- Database version is never downgraded and destructive downgrade/migration remains prohibited.
- If a later v3 runtime problem requires behavioral rollback, use a v3-compatible corrective build as required by `docs/PHASE_1_DESIGN.md:707-715`, not an APK that only understands v2.

## 12. Risks

### P0

- Two independent fact sources or two plan write owners.
- Plan UI still reaches legacy `upsertPlan()` after new aggregate reads begin.
- Repository failure falls back to a legacy DAO write.
- Plan and slots commit separately or `timeOfDay` diverges from slots.
- Schema/migration/hash changes appear in Batch 5.
- An internal v3 build upgrades a real user database.

### P1

- Provider creates multiple `AppDatabase` instances/builders or has an incorrect lifecycle.
- Domain/Repository errors are collapsed into success and the UI closes or reminders change after a failed save.
- ViewModel/UI imports Room, DAO, Entity, or database factory types.
- Slot edits silently sort, deduplicate, renumber incorrectly, or violate UUIDv5 identity semantics.
- Temporary JSON/Widget/Wear readers drift from the same Room facts, or any of them retains an active plan write path.
- Tests only mock contracts and do not prove production-provider persistence, reopen, and rollback on a real disposable Room database.

### P2

- `core.model` still depends on PK Route/Ester.
- Reminder, Widget, Wear, JSON, PK, and HRT event paths remain transitional until Batch 6/7.
- Temporary legacy and Domain naming/adapter duplication remains while the migration is incomplete.
- The architecture document's current-version statement and Batch 3C scope wording remain stale.

## 13. Stop conditions

Stop implementation and return to design review if any of the following occurs:

- the provider cannot reuse the existing singleton without exposing DAOs to feature code;
- a Repository contract change appears necessary;
- preserving current plan UI behavior requires changing Slot ID v1;
- DoseEvent must be switched to make the plan slice compile;
- JSON, PK, receiver, Widget, or Wear protocol behavior must change;
- plan writes cannot be made exclusively through the aggregate transaction;
- an integration failure is “fixed” by legacy fallback, destructive migration, clearing data, or weakening assertions;
- schema 2/3, migration, database version, or hashes change;
- connected repository/migration rollback tests cannot execute on an emulator or synthetic-data device;
- any real or real-derived database is required for acceptance;
- a P0/P1 remains after the authorized implementation iterations.

## 14. Later entry order

After Batch 5 passes independent review and is committed:

1. Batch 6 switches HRTViewModel/event UI, reminder receivers/factory/matcher, Widget, Wear listener, and remaining composition-root callers to Repository contracts. Event UI must preserve Domain revision/source/slot metadata and map insert/update results explicitly.
2. Batch 7 introduces the dedicated JSON v1 DTO/adapter and Domain-to-PK adapter, then removes direct PK model reuse from import/export and persistence-facing ViewModels without changing JSON fields or PK algorithms.
3. Batch 8 proves no feature, Wear, or Widget production code accesses DAO/Entity directly and reruns every release gate.

Room v3 remains an internal, non-releasable schema through all of these steps. Batch 5 completion alone does not authorize release, production distribution, or real-database upgrade.

## 15. Design decision

Proceed to independent read-only review of this design. If approved, implement Batch 5 as a MedicationPlan aggregate vertical cutover with a single production provider and no old/new dual write. Do not include DoseEvent, JSON, receiver, Widget, or Wear entry conversion in Batch 5, and do not begin implementation from this design branch before explicit authorization.
