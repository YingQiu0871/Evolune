# Evolune Phase 1 Batch 5A-0 MedicationPlan Draft Contract

Date: 2026-08-04

Branch: `phase1/batch5a0-draft-contract`

Baseline tag: `phase-1-batch-5-design-v1`

Status: resolved design contract only. No provider, adapter, test, production wiring, schema change, or migration change is implemented by this document.

## 1. Stop reason

Batch 5A stopped before implementation because the committed Batch 5 design authorized a pure plan draft/slot adapter but the repository had no concrete `MedicationPlanDraft` type. The first implementation request also required plan revision preservation and invalid textual time/UUID tests that the real plan types cannot express.

The blocking facts were:

- `MedicationPlanBottomSheet` stores independent Compose state and directly creates legacy `data.MedicationPlan` (`MedicationPlanBottomSheet.kt:49-99`, `313-327`).
- The legacy model supplies default `UUID.randomUUID()` and `System.currentTimeMillis()` values (`data/MedicationPlan.kt:27-39`), while the editor also generates both values at save time (`MedicationPlanBottomSheet.kt:313-325`).
- `core.model.MedicationPlan` has no revision field (`core/model/MedicationPlan.kt:9-29`).
- UI plan times are already `List<LocalTime>` and are produced by a Material time picker (`MedicationPlanBottomSheet.kt:78-80`, `245-259`, `341-353`).
- `core.dataapi.MedicationPlanRepository.save` accepts a complete Domain aggregate, including slots (`core/dataapi/MedicationPlanRepository.kt:7-29`).

This document resolves those ambiguities without changing the previously committed Batch 5 design or any production type.

## 2. Real UI state and call chain

### 2.1 Current editor state

`MedicationPlanBottomSheet` currently owns these plan-editing values:

| UI state | Actual type | Evidence | Draft representation |
|---|---|---|---|
| `name` | `String` | `MedicationPlanBottomSheet.kt:50-52` | `name: String` |
| `selectedRoute` | `pk.Route` | `MedicationPlanBottomSheet.kt:54-56` | `route: Route` |
| `selectedEster` | `pk.Ester` | `MedicationPlanBottomSheet.kt:58-60` | `ester: Ester` |
| `selectedAntiAndrogen` | `pk.AntiAndrogen` | `MedicationPlanBottomSheet.kt:62-68` | projected through `extras[ANTI_ANDROGEN_TYPE]`, not a second fact |
| `scheduleType` | legacy `MedicationPlan.ScheduleType` | `MedicationPlanBottomSheet.kt:70-72` | `core.model.ScheduleType` after the Batch 5B UI-boundary conversion |
| `doseMGText` | `String` | `MedicationPlanBottomSheet.kt:74-76` | parsed by Batch 5B before `doseMG: Double` enters the Draft |
| `timeOfDay` | `List<LocalTime>` | `MedicationPlanBottomSheet.kt:78-80` | `times: List<LocalTime>` |
| `daysOfWeek` | `Set<DayOfWeek>` | `MedicationPlanBottomSheet.kt:82-84` | `daysOfWeek: Set<DayOfWeek>` |
| `intervalDays` | `String` | `MedicationPlanBottomSheet.kt:86-88` | parsed by Batch 5B before `intervalDays: Int` enters the Draft |
| `sublingualTier` | `pk.SublingualTier` | `MedicationPlanBottomSheet.kt:91-97` | projected through `extras[SUBLINGUAL_TIER]`, not a second fact |

`showTimePicker`, `timeIndexToEdit`, sheet visibility, notification permission state, and other UI mechanics are not plan facts and do not enter the Draft.

The editor currently derives `id`, `isEnabled`, and `createdAt` only when saving (`MedicationPlanBottomSheet.kt:313-325`). Batch 5B must move the new-plan `id` and `createdAt` decisions to creation-session start rather than retaining save-time generation.

### 2.2 Current create, edit, copy, save, and delete paths

- Create: `MedicationPlansScreen.onAddClick` sets `planToEdit = null` and opens the sheet (`MedicationPlansScreen.kt:111-114`). No plan ID or creation time is allocated until save.
- Edit: plan click stores the existing legacy plan in `planToEdit` and opens the sheet (`MedicationPlansScreen.kt:107-110`). The sheet initializes its values from that plan.
- Copy: there is no production plan-copy action or plan-copy call chain. Preview fixtures using random IDs are not product copy behavior.
- Save: the sheet constructs a legacy plan and calls `onSave` (`MedicationPlanBottomSheet.kt:301-327`); the screen immediately calls `viewModel.upsertPlan` and closes the sheet (`MedicationPlansScreen.kt:139-145`); the ViewModel calls the legacy repository and then updates reminders (`MedicationPlanViewModel.kt:48-58`).
- Delete: the sheet sends the existing ID (`MedicationPlanBottomSheet.kt:271-275`); the screen calls `viewModel.deletePlan` and closes the sheet (`MedicationPlansScreen.kt:147-150`); the ViewModel deletes through the legacy repository and then cancels the reminder (`MedicationPlanViewModel.kt:64-70`).
- Enable/disable: the screen calls `togglePlanEnabled` (`MedicationPlansScreen.kt:115-120`), which uses the legacy repository (`MedicationPlanViewModel.kt:76-88`).

Batch 5A-0 and Batch 5A do not change any of these production paths.

## 3. Draft decision

An independent `MedicationPlanDraft` is required.

It is a pure application/UI-boundary value, not a Room model, persistence contract, external DTO, or alternate Domain model. Its purpose is to hold a complete, typed plan-editing snapshot plus explicit creation metadata while replacing Domain `slots` with the editor's ordered local-time list.

The Draft:

- has no Android, Compose, Room, DAO, Entity, Repository, JSON, file, Reminder, Predictor, Widget, or Wear dependency;
- does not read `Context`, the system clock, time zone, Locale, or database;
- does not generate random UUIDs;
- has no persistence revision;
- does not contain transient UI controls;
- does not duplicate PK, DST, scheduling, or reminder behavior.

The implementation location remains the Batch 5 design location under `io.github.yuninggu.evolune.application`.

## 4. Exact Draft fields

The locked Draft is strongly typed and contains exactly these fields:

```kotlin
data class MedicationPlanDraft(
    val id: UUID,
    val name: String,
    val route: Route,
    val ester: Ester,
    val doseMG: Double,
    val scheduleType: ScheduleType,
    val times: List<LocalTime>,
    val daysOfWeek: Set<DayOfWeek>,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<ExtraKey, Double>,
    val createdAt: Instant
)
```

`Route` and `Ester` are the transitional PK enums already used by the Domain. `ScheduleType` and `ExtraKey` are the existing `core.model` types. This contract does not create new product fields.

The Draft intentionally uses typed `Double` and `Int` values rather than Compose text-field strings. Parsing `doseMGText` and `intervalDays` and translating `selectedAntiAndrogen`/`sublingualTier` to explicit `extras` are Batch 5B UI-boundary responsibilities. Domain-to-Draft mapping preserves the complete `extras` map so fields not displayed by the current editor are not silently lost.

## 5. Field ownership matrix

| Draft field | Source | New-plan rule | Edit rule | Domain field | Adapter validation |
|---|---|---|---|---|---|
| `id: UUID` | creation session or existing Domain plan | generated exactly once before the editor opens | preserve `plan.id` | `id` | typed UUID is preserved; no textual parsing or random fallback |
| `createdAt: Instant` | creation session or existing Domain plan | supplied exactly once before the editor opens | preserve `plan.createdAt` | `createdAt` | preserve exactly; do not call a clock |
| `name: String` | name editor | initialize to empty string | preserve/edit current value | `name` | blank is `MissingRequiredField(NAME)`, matching the current UI save gate |
| `route: Route` | route selector | current default `INJECTION` | preserve/edit current value | `route` | no new compatibility rule |
| `ester: Ester` | ester selector or existing value | current default `EV` | preserve/edit current value | `ester` | no new compatibility rule |
| `doseMG: Double` | parsed `doseMGText` | caller supplies parsed current editor value | preserve/edit current value | `doseMG` | no new Domain compatibility rule; text parsing remains Batch 5B |
| `scheduleType: ScheduleType` | schedule selector | current default `DAILY` | preserve/edit current value | `scheduleType` | no scheduling execution or irrelevant-field normalization |
| `times: List<LocalTime>` | time picker list | current initial `09:00` | derive from Domain slots in position order | converted to `slots` | accept empty, preserve order/duplicates, require minute precision |
| `daysOfWeek: Set<DayOfWeek>` | day selector | current default empty set | preserve/edit current value | `daysOfWeek` | preserve exactly; no sorting or schedule-based clearing |
| `intervalDays: Int` | parsed interval text | current default `1` | preserve/edit current value | `intervalDays` | Domain construction enforces `>= 1` |
| `isEnabled: Boolean` | creation session or existing plan | current default `true` | preserve existing value unless explicit enable action changes it | `isEnabled` | preserve exactly |
| `extras: Map<ExtraKey, Double>` | explicit UI projections plus existing Domain extras | caller supplies current editor projections | preserve complete map and update only explicitly edited keys | `extras` | preserve exactly; no ordinal reinterpretation inside adapter |

There is no Draft `revision` and no persistence-only field. Entity strings, legacy `timeOfDay`, slot entity rows, database row IDs, schema defaults, DAO values, and migration metadata do not enter the Draft.

## 6. ID ownership

The ID rule is locked as follows:

1. A new plan ID is generated once when the application starts a create-plan editing session.
2. The session passes that `UUID` into the Draft explicitly.
3. The Draft adapter accepts and preserves the typed UUID; it never calls `UUID.randomUUID()`.
4. Editing an existing plan uses the existing Domain ID.
5. Recomposition, redraw, validation, Domain-to-Draft conversion, and repeated save attempts retain the same ID.
6. The Repository does not generate or replace plan IDs.

In Batch 5B, the production `UUID.randomUUID()` call at `MedicationPlanBottomSheet.kt:314` moves to the create-session entry invoked by `MedicationPlansScreen.onAddClick` (`MedicationPlansScreen.kt:111-114`). It must execute once per newly opened create session, before the Draft is passed to the editor. Reopening a new create session may create a new ID; recomposition or save within one session may not.

Because the locked Draft uses `UUID`, malformed textual UUID input is not expressible at this boundary. Text UUID parsing remains an external/import concern and is not a Batch 5A adapter test. The all-zero UUID is not rejected because no existing Domain or product rule forbids it.

## 7. createdAt ownership

The creation-time rule is locked as follows:

1. A new plan's `createdAt` is captured once at create-session start by the caller/application boundary.
2. The value is supplied explicitly as `Instant`; production code may obtain it from an injected `Clock` or pass a caller-produced `Instant`.
3. The Draft and adapter do not call `Instant.now()`, `Clock.system*()`, or `System.currentTimeMillis()`.
4. Editing an existing plan preserves its original `createdAt`.
5. Recomposition, validation, Domain-to-Draft conversion, and repeated saves do not refresh it.
6. Domain-to-Draft-to-Domain mapping preserves it exactly.

In Batch 5B, the save-time `System.currentTimeMillis()` call at `MedicationPlanBottomSheet.kt:325` moves to the same create-session entry as the new ID. Batch 5A tests use fixed `Instant` values.

## 8. Revision conclusion

MedicationPlan has no revision in any authoritative plan layer:

- legacy `data.MedicationPlan` has no revision (`data/MedicationPlan.kt:27-39`);
- `core.model.MedicationPlan` has no revision (`core/model/MedicationPlan.kt:9-21`);
- `MedicationPlanEntity` has no revision (`data/MedicationPlanEntity.kt:15-29`);
- `core.dataapi.MedicationPlanRepository` has no expected-revision argument or plan conflict result (`core/dataapi/MedicationPlanRepository.kt:7-29`, `RepositoryResults.kt:23-34`).

Revision belongs to DoseEvent: it exists in `core.model.DoseEvent`, `DoseEventEntity`, `DoseEventDao`, and `RoomDoseEventRepository` CAS update behavior. It is not transferred to plan editing.

Therefore:

- `MedicationPlanDraft` does not add revision;
- the adapter does not validate or preserve plan revision;
- the original Batch 5A test item "revision preservation" is removed;
- no Domain, Entity, schema, migration, or Repository contract changes are authorized.

## 9. Time input boundary

The current plan editor does not accept textual times. It uses `List<LocalTime>` initialized with `LocalTime.of`, edited through `rememberTimePickerState`, and replaced with another `LocalTime` (`MedicationPlanBottomSheet.kt:78-80`, `245-259`, `341-353`).

The Draft adapter can and must validate or preserve:

- empty, single, and multiple time lists;
- input order and duplicate values;
- `00:00` and `23:59`;
- `second == 0` and `nano == 0`;
- zero-based continuous slot positions;
- canonical `HH:mm` semantics;
- deterministic Slot ID v1 output.

The Draft adapter cannot express and must not pretend to test:

- malformed time strings;
- offset or time-zone strings;
- damaged JSON;
- non-string external values.

No string parser is added in Batch 5A. JSON and external parsing remain in their authorized later boundaries. The adapter does not read a system time zone or duplicate Java `atZone`/DST behavior; plan scheduling continues to follow the existing downstream design.

## 10. Slot generation responsibility

`MedicationPlanRepository.save(plan)` accepts a complete `core.model.MedicationPlan`, and `RoomMedicationPlanRepository` maps and saves its complete slot aggregate (`RoomMedicationPlanRepository.kt:45-97`). The persistence mapper verifies slot ownership, position, minute precision, and expected Slot ID (`MedicationPlanEntityMapper.kt:138-175`); it does not create missing Domain slots for the caller.

The unique responsibility is therefore:

- Draft adapter: construct the complete ordered `List<ScheduledDoseSlot>` from `draft.times`, using `position == index` and the existing `ScheduledDoseSlotId.generate` UUIDv5 v1 implementation.
- Repository/persistence mapper: independently validate the supplied aggregate and persist it atomically. Validation is not a second slot-generation owner.

The adapter preserves empty lists, order, and duplicates and performs no sorting, deduplication, truncation, time-zone conversion, or repair.

The immutable fixed vector remains:

```text
planId: 00000000-0000-0000-0000-000000000001
position: 0
localTime: 08:30
slotId: 17d1fd14-9d70-5344-beaa-0b158c9f62f4
```

Domain-to-Draft mapping reads slots in their authoritative list order and verifies each UUIDv5 ID before returning its `localTime`. Existing Domain constructors already enforce slot ownership, zero-based continuous positions, and minute precision (`core/model/MedicationPlan.kt:23-28`, `core/model/ScheduledDoseSlot.kt:16-20`); the Draft boundary does not manufacture otherwise-invalid Domain objects to retest those constructors.

## 11. Draft mapping result

Persistence `MappingResult` is owned by `data.mapper` and includes Entity/storage errors (`MappingResult.kt:8-54`). Repository results describe storage operations (`RepositoryResults.kt:3-34`). Neither type is reused for the application draft boundary.

Batch 5A-2 may define the following minimal pure result in the same application file as the Draft mapper:

```kotlin
sealed interface DraftMappingResult<out T> {
    data class Success<T>(val value: T) : DraftMappingResult<T>
    data class InvalidDraft(
        val issues: List<DraftIssue>
    ) : DraftMappingResult<Nothing>
}

sealed interface DraftIssue {
    data class MissingRequiredField(val field: DraftField) : DraftIssue
    data class NonMinuteTime(val position: Int) : DraftIssue
    data class SlotIdMismatch(val position: Int) : DraftIssue
    data class SlotIdGenerationFailure(val position: Int) : DraftIssue
    data object DomainValidationFailure : DraftIssue
}

enum class DraftField {
    NAME
}
```

Issue codes are stable and do not contain exception text, complete plans, complete time lists, dose values, or other health data. `SlotIdGenerationFailure` represents a generator failure without exposing `SlotIdError.UuidV5Failure.message` as protocol.

`InvalidPlanId`, invalid time string, offset, and time-zone issues are intentionally absent: typed `UUID` and `LocalTime` cannot represent them. `InvalidSlotOwnership` and `NonContiguousPosition` are also absent because legal `MedicationPlan` construction already rejects them and the Draft contains times rather than caller-supplied slots. Text parsing errors belong to the boundary that owns text; corrupt persistence aggregates remain the persistence mapper's responsibility.

The adapter may catch `IllegalArgumentException` only around construction of existing Domain value types and map it to `DomainValidationFailure`; it must not use exception messages as result codes.

## 12. Batch 5A implementation split

### 12.1 Batch 5A-1 Provider

- Add the production Repository provider defined by the committed Batch 5 design.
- Reuse the single `AppDatabase.getDatabase(applicationContext)` singleton.
- Build both Room Repository implementations from the same `AppDatabase`.
- Expose only contract-typed stable getters.
- Add an internal database-injection seam for disposable instrumentation.
- Do not wire MainActivity, ViewModel, UI, Application initialization, or any production caller.
- Prove one database and stable Repository instances with instrumentation using a disposable database.

### 12.2 Batch 5A-2 Draft adapter

- Add `MedicationPlanDraft`, `DraftMappingResult`, issue codes, and pure Draft/Domain mapping under the application boundary.
- Require explicit typed ID and `createdAt`.
- Accept `List<LocalTime>` and construct complete slots with Slot ID v1.
- Support Domain-to-Draft display conversion with defensive slot validation.
- Add pure JVM tests.
- Do not call a clock, random generator, Repository, Room, Context, Predictor, Reminder, Widget, Wear, or JSON.
- Do not wire MainActivity, ViewModel, or Compose.

The provider and adapter may be delivered in one Batch 5A implementation commit, but their files and tests remain separate.

## 13. Batch 5B responsibilities

Only Batch 5B may:

- move new-plan ID generation from save time to create-session start;
- move new-plan `createdAt` capture from save time to create-session start;
- convert Compose text/control state to and from the typed Draft;
- preserve and update explicit `extras` projections;
- switch ViewModel/UI to Domain and Repository contracts;
- handle every Repository result and infrastructure failure;
- order Reminder side effects after successful persistence;
- remove the legacy plan save path as one atomic production cutover.

Batch 5A does not partially switch reads or writes and does not alter user-visible behavior.

## 14. Revised Batch 5A test matrix

### 14.1 Draft adapter JVM tests

The executable matrix is:

1. Empty `times` maps to empty slots.
2. One time maps to one position-zero slot.
3. Multiple times map to continuous positions.
4. Input order is preserved without sorting.
5. Duplicate times are preserved and receive distinct position-derived IDs.
6. `00:00` is accepted.
7. `23:59` is accepted.
8. A time with non-zero seconds is rejected as `NonMinuteTime`.
9. A time with non-zero nanos is rejected as `NonMinuteTime`.
10. The fixed `08:30` UUIDv5 vector matches the hard-coded expected ID.
11. Draft ID is preserved in Domain and every slot.
12. `createdAt` is preserved exactly from a fixed `Instant`.
13. All other plan fields are preserved.
14. Empty/duplicate times and irrelevant schedule fields retain existing Domain semantics.
15. Domain-to-Draft-to-Domain parity holds for a complete synthetic plan.
16. Domain-to-Draft rejects a mismatched Slot ID, which the current Domain constructor does not itself validate.
17. Blank name returns `MissingRequiredField(NAME)`.
18. Invalid Domain invariants return `DomainValidationFailure` without exception text.
19. Existing Domain tests continue to cover slot ownership and position invariants; Draft tests do not construct invalid Domain objects through reflection or unchecked casts.
20. Locale and default time zone changes do not alter mapping or IDs.
21. Static/source inspection proves no random UUID, clock, Android, Room, Repository, JSON, Reminder, Predictor, Widget, or Wear dependency.

The following former requirements are removed or deferred because this boundary cannot represent them:

- plan revision preservation: removed because no plan revision exists;
- malformed textual UUID: deferred to a text/external input boundary;
- invalid time string and offset/time-zone strings: deferred to text/JSON/external boundaries;
- damaged JSON: Batch 7/external parsing scope;
- UI runtime current-time behavior: Batch 5B creation-session tests;
- predictor/DST execution parity: remains in the Batch 5B production cutover tests, not the pure Draft mapper.

### 14.2 Provider instrumentation

Provider tests retain the committed Batch 5A requirements:

1. Both Room Repositories are constructed.
2. Both use one injected disposable `AppDatabase`.
3. Repeated provider access returns stable Repository instances.
4. One synthetic plan save/read succeeds.
5. One synthetic event save/read succeeds.
6. Data remains after closing and reopening the disposable file-backed database.
7. No second test database file is created.
8. Schema version is 3.
9. No destructive migration is used.
10. The test database is deleted after the test.

The provider test never opens `evolune_database` and never resets or mutates the production singleton.

## 15. Prohibitions and stop conditions

Batch 5A must not modify MainActivity, ViewModel, UI, JSON, Reminder, Receiver, Widget, Wear, Predictor/PK production code, Repository contracts, Domain models, DAOs, Room Repository implementations, Entities, AppDatabase, migrations, schemas, Gradle, Manifest, or the committed Batch 5 design.

Implementation must stop and return to design if:

- the Draft cannot remain strongly typed and pure;
- production requires the adapter to generate ID or read a clock;
- a plan revision is proposed;
- the Repository contract must change to accept times instead of the complete aggregate;
- Slot ID v1 inputs or output would change;
- the adapter would need to parse textual/JSON time input;
- provider isolation would require changing the production database name or singleton state;
- any schema or migration change appears;
- Batch 5A would need production wiring to be testable.

## 16. Architecture and release conclusion

This contract introduces no schema, Repository contract, Domain, ADR, or Slot ID conflict. It narrows the previously authorized application helper into an implementable typed boundary and removes tests that belong to other layers.

Room remains version 3. ADR-014, ADR-015, ADR-016, schema 2, schema 3, `MIGRATION_2_3`, and the Batch 5 production-wiring design remain unchanged. Tracked Date does not enter Phase 1.

Room v3 remains an internal, non-releasable migration version until all ADR-016 and Batch 8 release gates pass. Completion of Batch 5A-0 or Batch 5A is not permission to run against a real database, distribute a production build, or start Batch 5B without the required review.
