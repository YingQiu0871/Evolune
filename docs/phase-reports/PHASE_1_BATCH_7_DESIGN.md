# Evolune Phase 1 Batch 7 Design and Current-State Audit

Date: 2026-08-07

Status: Batch 7 design ready for independent review. No Batch 7 implementation has started.

## 1. Executive summary

Batch 7 is authorized by `docs/PHASE_1_DESIGN.md:869-878` and the current
roadmap to complete two remaining boundary migrations:

1. Replace the private JSON v1 bridge with an explicit JSON v1 protocol DTO,
   codec, Domain adapter, and Repository-backed import service.
2. Replace the Batch 6 temporary Domain-to-PK projections with one formal,
   pure Kotlin structural adapter and preserve all existing selection,
   ordering, simulation, and numerical behavior at its call sites.

Batch 7 does not authorize a new wire format, a new database boundary, a
change to Repository contracts, a Domain redesign, a PK algorithm change, a
Room migration, or a release. The production database remains the only fact
source. JSON is an external protocol projection, and PK is a calculation
input projection; neither becomes a second fact source.

Current audit result: `P0/P1/P2 = 0/0/4`.

- P2-1: JSON v1 preserves the existing random UUID behavior for missing or
  corrupt IDs, so repeating such an import is intentionally not idempotent.
- P2-2: JSON v1 cannot represent Domain `source`, `status`, `revision`,
  `zoneId`, `localDate`, or `slotId`; export/import is therefore a protocol
  projection rather than a lossless Domain round trip.
- P2-3: `core.model` still reuses `pk.Route` and `pk.Ester`, as explicitly
  accepted by ADR-015. Batch 7 does not remove that ownership debt.
- P2-4: The already accepted Wear replay limitation remains: the current
  three-field payload cannot revalidate the original `plan_id` after first
  materialization. This is inherited from Batch 6 and remains a Batch 8
  protocol review item, not a Batch 7 data-source change.

The four P2 items do not block design review. Batch 7 remains blocked from
implementation if any stop condition in Section 24 is encountered.

## 2. Authority, baseline, and exclusions

The audit read the following authoritative material and current source:

- `docs/PHASE_1_DESIGN.md`, especially Sections 5, 7, 13, 15, 16, 18,
  19, and 20;
- `docs/evolune/ROADMAP.md`;
- `docs/evolune/ARCHITECTURE.md`;
- `docs/evolune/DECISIONS.md`, ADR-014, ADR-015, and ADR-016;
- Batch 6 design, replay-policy addendum, reports, and independent reviews;
- `MahiroJsonFormat.kt`, its tests, `Batch6DoseEventCompatibility.kt`, its
  tests, `HRTViewModel.kt`, `AppNavigation.kt`, and current fixtures;
- `core.model.DoseEvent`, `MedicationPlan`, Repository contracts,
  `ProductionRepositoryProvider`, `SimulationEngine`, PK models, the
  predictor, Widget projection, and Wear dashboard path.

The required start checks passed:

- branch: `phase1/batch7-design`;
- worktree: clean;
- staging area: clean;
- `phase-1-batch-6` is an ancestor of `HEAD` and points to the final Batch 6
  review commit together with `phase-1-batch-6c`;
- Room version is `3` and `exportSchema = true`;
- no Batch 7 implementation change exists.

Batch 7 must not modify:

- `DoseEvent`, `MedicationPlan`, Repository contracts, DAO, Entity, or
  `AppDatabase` schema semantics;
- `MIGRATION_2_3`, schema 2, schema 3, or Slot ID v1;
- Wear paths, DataMap keys, payload fields, action ID ownership, or Widget
  protocol;
- `SimulationEngine`, PK algorithms, PK parameter constants, or tolerances;
- Tracked Date, Health Connect, Glance, WorkManager, cloud sync, a second
  database, real data, or a release.

## 3. Authoritative Batch 7 scope

### 3.1 In scope

- A dedicated JSON v1 DTO boundary.
- JSON text to DTO parsing and DTO to JSON serialization.
- DTO to Domain and Domain to DTO conversion.
- Repository-backed JSON import with explicit per-file outcomes.
- Domain `DoseEvent` to PK `DoseEvent` structural conversion.
- Replacement of HRT and Widget temporary event projections.
- Reuse of the formal adapter's explicit ExtraKey mapping where it removes
  duplicate production mappings without changing semantics.
- PK parity and JSON compatibility tests.
- Static verification that the Batch 6 bridges are no longer production
  call paths.

### 3.2 Explicitly out of scope

- Changing JSON v1 field names, requiredness, defaults, or external meaning.
- Adding Domain fields or moving `Route`/`Ester` out of `pk`.
- Adding `planId` to `DoseEvent` or changing the Wear payload.
- Changing `getEventsForPk`, the 30-day/20-event rule, branch order, or
  sorting behavior.
- Moving selection, filtering, sorting, time-window logic, or simulation into
  the formal adapter.
- Changing any `SimulationEngine` implementation, PK parameter, or result
  tolerance.
- Removing `timeH` or `timeOfDay`, their migration shadows, legacy DAO
  methods, or legacy persistence implementations.
- Changing Repository contract result sets or introducing a batch transaction
  contract for JSON import.

### 3.3 Acceptance definition

Batch 7 can be considered implemented only when all of these are true:

1. JSON text is parsed into a protocol DTO before any Domain conversion.
2. JSON parsing and Domain conversion are separate, testable boundaries.
3. JSON import writes every valid Domain event through `insert` only.
4. `Inserted`, `Idempotent`, `Conflict`, `Invalid`, and storage failure are
   distinguishable; no upsert, overwrite, fallback, or clear-and-import path
   exists.
5. JSON export reads Repository Domain events and does not write storage.
6. The formal PK adapter maps complete representable input fields and no
   consumer-specific selection or ordering.
7. HRT and Widget use that adapter; Home and Wear continue consuming their
   existing derived HRT state and do not gain duplicate conversion paths.
8. JSON golden/compatibility and PK parity tests pass, including all current
   route, ester, extras, timing, ordering, duplicate, and boundary cases.
9. The Batch 6 JSON and PK bridges have zero production callers and are
   removed only after parity tests pass.
10. Schema 2/3, migration, Repository contracts, Wear protocol, and PK
    numerical output remain unchanged.

## 4. Current JSON call graph

### 4.1 Export

The actual current export path is:

```text
Settings UI / AppNavigation.kt:171-178, 403-409
  -> HRTViewModel.exportToMahiroJson() :285
  -> Batch6MahiroJsonBridge.export() :57-59
  -> Domain event -> temporary PkDoseEvent :103-117
  -> MahiroJsonFormat.generateExport() :149-176
  -> kotlinx.serialization JsonElement encoding
```

The source list is `HRTViewModel.events`, which is the contract-backed
Domain `observeAll()` flow. No DAO, Entity, or legacy Repository is used in
this path. The current serializer writes `meta.version = 1`, a current
`Instant.now()` `exportedAt`, `weight`, the input event order, `events`, and
empty `labResults` and `doseTemplates` arrays.

### 4.2 Import

The actual current import path is:

```text
OpenDocument / clipboard in AppNavigation.kt:125-169
  -> HRTViewModel.importFromMahiroJson() :244-274
  -> Batch6MahiroJsonBridge.import() :27-55
  -> MahiroJsonFormat.parseImport() :90-99
  -> temporary PK event -> Domain event :82-100
  -> DoseEventRepository.insert() :39-45
  -> RoomDoseEventRepository -> v3 Room
```

The parser itself does not open a database or call a Repository. The current
Bridge performs the persistence loop. It calls `insert` once for each parsed
event in input order. `Inserted`, `Idempotent`, `Conflict`, and `Invalid` are
counted and processing continues. A runtime exception aborts the operation;
previous successful inserts remain committed because there is no file-level
transaction contract or rollback in the current path. `HRTViewModel` maps
that exception to a generic import error and performs no legacy fallback.

## 5. Current JSON v1 wire contract

The contract below is derived from `MahiroJsonFormat.kt:35-51,58-82,90-176`
and `MahiroJsonFormatTest.kt:17-297`. Names and external meanings are fixed.

### 5.1 Top-level fields

| Field | Current type | Read behavior | Export behavior |
|---|---|---|---|
| `meta` | object | Not inspected; `version` does not select a parser branch | Object with `version: 1` and `exportedAt` |
| `weight` | JSON number or absent | `Double?`; absent or non-number primitive becomes null | Always written as the supplied `Double` |
| `events` | array or absent | Absent means empty list; wrong top-level type fails the document parse | Array in caller-provided order |
| `labResults` | any current JSON value | Ignored | Always written as empty array |
| `doseTemplates` | any current JSON value | Ignored | Always written as empty array |

Unknown top-level fields are ignored by the current object lookup. An empty
valid object imports as null weight and zero events. Empty or syntactically
invalid text fails the whole parse.

### 5.2 Event fields

| Field | Current type | Requiredness and behavior |
|---|---|---|
| `id` | string | Missing ID generates `UUID.randomUUID()`. A non-null invalid UUID also generates a random UUID. A wrong JSON type can make that entry malformed and skipped. Valid UUID values are preserved as UUID identity and normalize to standard lowercase on later serialization. |
| `route` | string | Required. Exact mappings are `injection`, `oral`, `sublingual`, `gel`, `patch_apply`, `patch_remove`, and `antiandrogen`. Missing or unknown values skip the entry. |
| `ester` | string | Required. Exact `E2`, `EB`, `EV`, `EC`, or `EN`. Missing or unknown values skip the entry. |
| `timeH` | JSON number | Required. Parsed as `Double`, then validated by `LegacyTimeAdapter`; invalid/non-finite/out-of-range values become an invalid Domain entry and are not persisted. |
| `doseMG` | JSON number | Required numeric value. Missing or unparseable values skip the entry; repository mapping remains the final persistence validation boundary. |
| `extras` | object or absent | Missing means empty map. Known keys are `sublingualTier`, `sublingualTheta`, `concentrationMgMl`, `areaCm2`, `releaseRateUgPerDay`, and `antiAndrogenType`. Unknown keys and non-numeric known values are ignored as in the current parser. |

Unknown event fields are ignored. An exception while parsing one event is
caught by the current `parseEvent` boundary and skips only that event; an
exception while parsing the top-level document fails the whole import.

### 5.3 Domain defaults at import

The JSON v1 protocol does not contain these Domain fields. Every successfully
adapted imported event therefore receives:

| Domain field | Import value |
|---|---|
| `occurredAt` | `LegacyTimeAdapter.timeHToInstant(timeH)` |
| `zoneId` | `null` |
| `localDate` | `null` |
| `slotId` | `null` |
| `source` | `DoseEventSource.JSON_V1` |
| `status` | `DoseEventStatus.RECORDED` |
| `revision` | `1L` |

The adapter must never reconstruct missing Domain metadata from PK fields or
from the current device zone. These defaults express unknown protocol data,
not a claim that the event happened in the current zone.

## 6. JSON DTO and codec design

### 6.1 DTO boundary

The planned protocol package is an app-internal logical boundary:
`io.github.yuninggu.evolune.external.mahiro.v1`. This is a package only; no
new Gradle module is created.

The DTO set is expected to contain:

- `MahiroV1DocumentDto`: the supported v1 weight and event projection;
- `MahiroV1DoseEventDto`: raw protocol fields before Domain validation;
- entry/document parse diagnostics that preserve the input index and reason
  without exposing a Domain, Entity, PK, Repository, or Android type.

The DTO represents the JSON v1 protocol only. It is not a Domain event, Room
Entity, PK event, or Repository command. It must not contain `Context`, DAO,
Entity annotations, `DoseEventSource`, `DoseEventStatus`, `Instant`, or
`SimulationEngine` fields as a shortcut around the adapter boundary.

### 6.2 Codec boundary

`MahiroV1Codec` owns exactly these transformations:

```text
JSON text <-> MahiroV1DocumentDto
```

It may use the existing `kotlinx.serialization.json` dependency but has no
Android or Room dependency. Because the current parser skips malformed
individual entries while failing malformed documents, the codec must parse
entries independently rather than relying on one strict serializable list
that turns one bad entry into a whole-document exception.

The codec preserves the current top-level behavior: it ignores `meta.version`
for parser selection, ignores `labResults` and `doseTemplates`, accepts
missing `events` as empty, and ignores unknown fields. It does not call a
Repository or generate a Domain ID.

### 6.3 DTO serialization

The serializer keeps these wire details unchanged:

- field names and casing;
- `meta.version = 1`;
- ISO `Instant` text form for `exportedAt`;
- event order supplied by the caller;
- route strings and ester names;
- numeric `timeH`, `doseMG`, and extras values;
- known extras key names;
- empty `labResults` and `doseTemplates` arrays;
- UTF-8 text output and existing pretty JSON structure.

`exportedAt` is injected through a `Clock` or equivalent serializer input for
tests. Production uses the current UTC clock. Golden tests fix that clock;
they do not compare two calls made at different wall-clock instants.

The DTO adapter preserves the current event input order. It does not sort by
time, ID, source, or revision. Repository `observeAll()` already provides
the authoritative descending event order, while direct codec tests can pass
a fixed order. Extras retain a deterministic `LinkedHashMap` order in DTO
construction; this changes no JSON field names or values.

## 7. JSON v1 Domain adapter

`MahiroV1DoseEventAdapter` owns:

```text
MahiroV1DoseEventDto -> core.model.DoseEvent
core.model.DoseEvent  -> MahiroV1DoseEventDto
```

### 7.1 Import mapping

- Valid UUID strings are parsed and preserved exactly as UUID identity.
- Missing or corrupt UUID strings use the existing v1 `UUID.randomUUID()`
  behavior. The production generator is injectable in JVM tests so the
  behavior can be tested without asserting a particular random value.
- Route mapping uses the seven explicit wire strings and `when`/map entries;
  no `ordinal` or lowercase heuristic is added.
- Ester mapping uses the five explicit enum names.
- `timeH` uses `LegacyTimeAdapter.timeHToInstant`, including finite,
  `Long`, multiplication, and `Math.round` semantics. No timezone is read.
- `doseMG` and each recognized extras value retain the current numeric value;
  no unit conversion or clamping is added.
- The six ExtraKey values use an exhaustive explicit mapping.
- Import metadata uses the fixed defaults in Section 5.3.

An invalid required field produces an entry-specific invalid result. It does
not generate a replacement ID, current time, current zone, null Domain event,
or Repository write. Missing/corrupt ID behavior is the one intentional v1
exception: it generates a new random UUID exactly as the current protocol
does.

### 7.2 Export mapping

Domain events are projected to protocol fields as follows:

| Domain input | JSON v1 output |
|---|---|
| `id` | lowercase standard UUID string |
| `route` | locked wire route string |
| `ester` | enum name |
| `occurredAt` | `LegacyTimeAdapter.instantToTimeH` |
| `doseMG` | same numeric value |
| `extras` | six locked wire keys and same values |

`source`, `status`, `revision`, `zoneId`, `localDate`, and `slotId` are not
written because v1 cannot represent them. This is an intentional external
projection, not deletion from Domain or storage. An `Instant` that cannot be
represented as a legal v1 `timeH` is an explicit export failure; it is never
clamped or replaced.

### 7.3 JSON import persistence service

An application-level `MahiroJsonV1ImportService` will consume the codec,
adapter, and `DoseEventRepository`. The codec never writes storage.

For each valid adapted event, in source-file order, it calls:

```text
DoseEventRepository.insert(event)
```

The service maps results without weakening them:

| Repository result | Import summary |
|---|---|
| `Inserted` | `insertedCount += 1` |
| `Idempotent` | `idempotentCount += 1` |
| `Conflict` | `conflictCount += 1`; keep the original row |
| `Invalid` | `invalidCount += 1`; no fallback |
| `RepositoryStorageException` or infrastructure exception | stop and return a failed import with `failedCount += 1`; do not claim success |

Malformed/invalid DTO entries are counted as invalid and processing continues.
Insert outcomes continue to be counted independently. A storage exception
stops at the failing entry, because the current Repository contract has no
file transaction and the current bridge already leaves earlier accepted rows
committed. The failed result must include the partial counts and the failing
entry index, without pretending that the file was atomically imported. It
must not retry through a legacy writer, clear existing rows, upsert, or
continue after an infrastructure failure as if it were an entry conflict.

The HRT ViewModel can keep its existing public import entry point while
mapping this typed service result to UI state. A successful summary is only
returned when the parser and all processed entries finish without an
infrastructure failure. The weight callback remains after successful import
completion, preserving the current behavior that a failed import does not
apply a parsed weight as if persistence completed.

## 8. JSON export consistency and compatibility

The export source is the Repository-backed Domain event list. Export never
queries DAO/Entity directly and never writes the database. The list order is
preserved exactly; the exporter does not re-select a time window or apply PK
filters.

Compatibility guarantees:

- current v1 files remain readable;
- valid UUID identity is preserved;
- missing/corrupt UUID randomization remains unchanged;
- `timeH` is quantized only by the already locked `LegacyTimeAdapter`;
- current route, ester, dose, extras, field names, and empty arrays remain;
- Domain-only metadata is intentionally omitted from the v1 projection;
- export/import is deterministic for fixed Domain input, fixed extras order,
  and fixed `Clock` value;
- `exportedAt` remains a generated protocol field, not an event timestamp.

The golden fixture must compare the complete text after fixing `exportedAt`,
not merely assert that parsing succeeds. A separate semantic round-trip test
must assert the representable fields and explicitly assert the intentional
metadata omissions.

## 9. Current Domain-to-PK conversion inventory

### 9.1 Actual production event projections

| Location | Input | Current conversion | Selection/order owner | Consumer | Temporary |
|---|---|---|---|---|---|
| `Batch6DoseEventCompatibility.kt:61-79` | Domain `DoseEvent` | `occurredAt -> timeH` through `LegacyTimeAdapter`; direct route/dose/ester; explicit six-key extras map | Caller/Repository selection; list order preserved | `HRTViewModel.kt:135-136,293-296` and JSON export helper | Yes |
| `WidgetWork.kt:181-206` | Domain `DoseEvent` | Same time conversion and explicit six-key extras map | `WidgetSnapshotLoader` filters antiandrogen/future; list order comes from Repository | Widget concentration simulation | Yes |

No other production code currently constructs a PK event from a persisted
Domain event. `SimulationEngine` itself accepts PK events and owns numerical
calculation only.

### 9.2 Plan prediction is a different boundary

`MedicationPlanPredictor.kt:48-127,140-147,185-191` converts a Domain
`MedicationPlan` into future, non-persisted PK events. It owns schedule
semantics, system-zone `atZone`, future filtering, sorting, conflict-window
filtering, and random IDs for predictions. It is not a persisted
Domain-event adapter and must not be forced into that adapter.

Batch 7 may reuse the formal adapter's explicit Route/Ester/ExtraKey mapping
helper to remove duplicate conversion code, but `MedicationPlanPredictor`
continues to own schedule selection, ordering, DST behavior, prediction
filtering, and random non-persisted prediction IDs.

### 9.3 HRT, Home, Widget, and Wear boundaries

- **HRT:** repository selection and `RECORDED`/antiandrogen filtering happen
  before formal conversion. The adapter maps the resulting Domain list in
  exactly the same order. Both `doseTimePoints` and `SimulationEngine` input
  use it.
- **Home:** `HomeScreen.kt:49-60,79-87` consumes HRT `PKState`, Domain plans,
  and HRT dose time points. It has no independent persisted-event-to-PK
  projection. No duplicate Home adapter call is permitted.
- **Widget:** existing plan/event selection and future-event filtering remain
  in Widget code; each selected Domain event is then passed to the formal
  adapter. `SimulationEngine` and the two-point sampling remain unchanged.
- **Wear:** `MainActivity.kt:102-118` publishes HRT's already computed
  `SimulationResult` and `sampleWearCurve`; `WearDataLayer.kt:111-120` only
  samples that result. Wear does not currently convert Domain events itself.
  Batch 7 must not add a second Wear projection. Existing Wear payload and
  dashboard values remain unchanged.

### 9.4 Legacy/UI time conversions that are not the formal adapter

`MedicationRecordItem.kt:146-159,189`, preview-only UI helpers, chart labels,
and `MedicationRecordsScreen` preview fixtures use `timeH` for display or
synthetic UI examples. They are not production Domain-to-PK adapter paths.
They must not be used as the formal adapter or silently expanded into a
second calculation mapping. Any later display cleanup needs its own scope.

## 10. Formal Domain-to-PK adapter design

The formal adapter is a pure Kotlin app-internal boundary, proposed under
`io.github.yuninggu.evolune.core.adapter`. It owns structural conversion
only:

```text
core.model.DoseEvent -> pk.DoseEvent
```

It may use `LegacyTimeAdapter` and the existing `pk.Route`, `pk.Ester`, and
`pk.DoseEvent` types. It must not import Android, Room, Repository, Context,
Widget, Wear, or UI types.

### 10.1 Field mapping

- `id` -> `id` unchanged;
- `route` -> `route` unchanged because ADR-015 still deliberately shares
  the PK enum;
- `occurredAt` -> `timeH` through the shared `LegacyTimeAdapter` formula;
- `doseMG` -> `doseMG` unchanged;
- `ester` -> `ester` unchanged;
- `extras` -> exhaustive explicit six-key PK mapping;
- `zoneId`, `localDate`, `slotId`, `source`, `status`, and `revision` are
  intentionally not representable in PK input and are not read by the
  calculation adapter.

The adapter returns a typed mapping failure or throws the existing explicit
compatibility exception when `Instant` cannot be represented. It must never
use current time, system default zone, Locale, random replacement ID,
sorting, filtering, or clamping.

### 10.2 Responsibilities deliberately outside the adapter

The caller remains responsible for:

- Repository `getEventsForPk(asOf)` selection;
- `status == RECORDED` filtering;
- antiandrogen filtering where the existing consumer applies it;
- future-event generation and conflict-window filtering;
- ascending/descending order preservation;
- duplicate timestamp behavior;
- simulation range, number of steps, current-time sampling, and tolerance.

The adapter must map one input list to one output list in the same order and
must not call `SimulationEngine`.

### 10.3 Route/Ester ownership decision

ADR-015 explicitly accepted `core.model` reuse of `pk.Route` and `pk.Ester`
until a later design. Batch 7 therefore uses direct identity mapping and
does not create duplicate Domain enums. Removing the dependency would change
Domain, mappers, Repository implementation boundaries, and tests and would
require a new decision. It is not silently included in this batch.

## 11. Selection and ordering invariants

The following existing behavior is frozen:

1. `RoomDoseEventRepository.getEventsForPk()` retains the 30-day window,
   20-dose fallback, and branch-specific order.
2. HRT preserves the selected list order before adding sorted future
   predictions. `SimulationEngine.run()` continues to receive the same
   sequence; its PATCH apply/remove matching remains input-order-sensitive.
3. Widget retains its `getEventsForPk(now)` selection, antiandrogen filter,
   future filter, and two-step concentration range.
4. Predictor retains `DAILY`, `WEEKLY`, and `CUSTOM` semantics, the current
   system timezone, Java `atZone` DST behavior, conflict window, and sorted
   predicted output.
5. Equal event timestamps are not deduplicated, and the adapter does not
   impose an ID tie-breaker.
6. Duplicate timestamps, zero/edge doses, PATCH apply/remove pairs, and all
   supported route/ester combinations remain valid inputs wherever they were
   valid before.

## 12. HRT/Home/Widget/Wear cutover plan

The production target chain is:

```text
Repository Domain events
  -> existing consumer selection/filtering/order
  -> formal DomainToPkDoseEventAdapter
  -> unchanged SimulationEngine
  -> existing UI/Widget/Wear projection
```

HRT removes `Batch6HrtPkProjection` usage. Widget removes its private
`toWidgetPkEvent` and uses the same adapter. Home and Wear retain their
current state/projection boundaries and must not create a second adapter.
The JSON exporter uses the JSON Domain adapter directly, not the PK adapter.

No UI or Wear protocol redesign is part of this cutover.

## 13. Temporary bridge and legacy code removal

Removal is conditional and ordered:

1. Add DTO/codec/JSON Domain adapter tests and golden fixtures.
2. Add formal PK adapter tests and old/new parity tests.
3. Switch HRT JSON service to DTO/Domain/Repository and switch HRT/Widget
   event projections to the formal adapter.
4. Run the complete JSON, PK, HRT, Widget, and regression matrices.
5. Confirm zero production references to `Batch6MahiroJsonBridge`,
   `Batch6HrtPkProjection`, private Widget PK conversion, and duplicated
   Domain-to-PK ExtraKey mappings.
6. Only then delete or replace those Batch 6 helpers and update their tests.

The following legacy persistence code remains intentionally:

- `data/DoseEventEntity.kt`, `MedicationPlanEntity.kt`, DAOs, and Room
  Repository implementations;
- `data/DoseEventRepository.kt` and `data/MedicationPlanRepository.kt`
  definitions if still required by compatibility or persistence tests;
- `timeH`, `timeOfDay`, migration shadows, and legacy mappers.

Batch 7 has no authority to delete these. Their removal requires the 1.0
compatibility-window decision and a separate schema design.

## 14. JSON compatibility test matrix

### 14.1 Codec and DTO

- valid full v1 document and all seven routes;
- all five ester names;
- required/optional field presence and null/wrong-type behavior;
- missing events, empty events, empty document object, invalid top-level JSON;
- unknown top-level and event fields;
- `meta.version` absent, `1`, and another value with unchanged parser behavior;
- `labResults` and `doseTemplates` ignored on import and emitted empty on export;
- missing/corrupt/valid UUID behavior, including deterministic injected UUID
  supplier for tests;
- timeH numeric precision, negative values, finite boundaries, NaN/infinity
  representation, conversion failure, and the 1 ms reconstruction rule;
- doseMG and all six extras, unknown keys, invalid extra values, and empty
  extras;
- exact route and ester wire strings.

### 14.2 Export and round trip

- fixed-clock golden export with complete text/byte equality;
- output field order and event order;
- UUID lowercase formatting;
- timestamp and floating-point formatting;
- all extras output keys and values;
- empty arrays and empty extras;
- export with Domain metadata proves metadata is intentionally omitted from
  v1 rather than erased from the input Domain object;
- export -> import round trip for all representable fields;
- existing v1 fixture remains readable by the new codec.

### 14.3 Repository import semantics

- one `insert` call per valid Domain event;
- `Inserted`, `Idempotent`, `Conflict`, and `Invalid` all appear in one mixed
  synthetic file and remain separately counted;
- duplicate ID with same content returns Repository idempotency and preserves
  the first row;
- duplicate ID with different content returns conflict and never overwrites;
- missing/corrupt IDs are not treated as idempotent retries;
- storage failure returns a failed summary, stops at the failing entry, does
  not delete or overwrite data, and does not invoke legacy fallback;
- parser-invalid entries are reported per entry and do not prevent later
  valid entries from being processed;
- export never calls Repository write methods;
- no clear-and-import or upsert path exists.

## 15. PK parity test matrix

For the same synthetic Domain event/list, compare the old temporary projection
with the formal adapter before deleting the old helper:

- ID, route, ester, doseMG, occurredAt/timeH, and all six extras;
- all supported routes, including antiandrogen and PATCH apply/remove;
- all five esters;
- zero, edge, negative, and fractional millisecond instants where legal;
- duplicate timestamps and input order;
- selection outputs from HRT, Widget, and Repository `getEventsForPk`;
- predicted event lists separately, including schedule type, DST, conflict
  window, and ordering;
- `SimulationResult.timeH`, every concentration sample, AUC, current
  concentration, and boundary timestamps;
- absolute numerical tolerance remains `1e-6`;
- parameter and algorithm tests remain unchanged and pass without tolerance
  widening.

The parity oracle compares adapter input and output collections as well as
final concentration values. A final-value-only comparison is insufficient.

## 16. Architecture boundaries

The completed design and later implementation must maintain:

- UI/ViewModel -> Repository Domain events, never Entity/DAO;
- JSON codec/DTO -> Domain adapter -> application import service ->
  Repository contract;
- Domain event -> formal PK adapter -> SimulationEngine;
- JSON adapter and PK adapter do not call or depend on each other;
- JSON adapter has no Room dependency;
- PK adapter has no Android/Room/Repository dependency;
- SimulationEngine has no Repository dependency;
- Widget and Wear have no direct Entity/DAO/legacy Repository calls;
- one `ProductionRepositoryProvider` and one Room fact source;
- no external DTO in Domain, Room, UI state, or PK model.

The current bypass audit found no remaining feature/background production
caller outside the data persistence implementation layer. The remaining
`AppDatabase`, DAO, Entity, and legacy Repository references are within
`app/src/main/java/.../data` persistence definitions, mappers, migration, or
Repository implementations and are not Batch 7 bypasses.

## 17. Legacy timeH and timeOfDay compatibility

### `timeH`

`timeH` remains in the Entity and legacy DAO queries, migration preflight and
rollback shadow, the PK input model, the JSON v1 wire DTO, display/preview
helpers, and `LegacyTimeAdapter`. The formal JSON and PK adapters are the only
new boundaries allowed to convert it for their respective protocols.

The Domain contract exposes `occurredAt`, not raw legacy `timeH`. Therefore
the formal JSON export uses `occurredAt -> timeH` through the shared adapter;
it does not reach through the Repository to recover an Entity shadow. This
keeps the Repository boundary honest. The v1 compatibility guarantee is the
locked millisecond reconstruction tolerance, not a new Domain field.

### `timeOfDay`

`timeOfDay` remains in the legacy plan Entity/model/DAO, migration parser,
rollback shadow, plan compatibility UI, and legacy predictor overload. Domain
plans use `slots`; Batch 7 does not delete or rewrite `timeOfDay`. Predictor
parity tests may continue to compare legacy and Domain plan overloads.

The committed compatibility window remains at least one formal 1.0 release
cycle. No Batch 7 change may claim that either legacy field is removable.

## 18. Release and data safety

- All test databases are disposable and file-backed with synthetic fixtures.
- The production `evolune_database` is never opened by Batch 7 tests.
- No real, anonymized-from-real, or real-derived medication/health data is
  used or logged.
- No intermediate Room v3 APK/AAB is distributed or used to upgrade a user
  database.
- Room v3 remains internal and unreleasable.
- No release, tag, staging, or production rollout is part of this design.

Schema gates remain:

| Schema | Identity hash | Canonical Git blob SHA-256 | Required change |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | none |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | none |

Room version remains `3`; `exportSchema` remains `true`; no migration or
schema change is permitted in Batch 7.

## 19. Expected production file inventory

The following is a planned inventory, not an authorization to create files
before independent review. Exact names can be finalized only within these
responsibilities; no new Gradle module is expected.

| Planned path | Type and responsibility | Pure Kotlin | Android allowed | Room annotation | Production connection |
|---|---|---:|---:|---:|---|
| `app/src/main/java/io/github/yuninggu/evolune/external/mahiro/v1/MahiroV1Dto.kt` | Protocol DTOs and typed parse diagnostics | Yes | No | No | No |
| `app/src/main/java/io/github/yuninggu/evolune/external/mahiro/v1/MahiroV1Codec.kt` | JSON text/DTO codec and fixed v1 serializer | Yes | No | No | Initially no, then service only |
| `app/src/main/java/io/github/yuninggu/evolune/external/mahiro/v1/MahiroV1DomainAdapter.kt` | DTO/Domain field mapping and v1 defaults | Yes | No | No | Service only |
| `app/src/main/java/io/github/yuninggu/evolune/core/adapter/DomainDoseEventToPkAdapter.kt` | Domain event to PK structural mapping | Yes | No | No | HRT/Widget call sites |
| `app/src/main/java/io/github/yuninggu/evolune/application/MahiroJsonV1ImportService.kt` | Per-entry Repository insert and summary | Yes | No | No | HRT/ViewModel |
| `app/src/main/java/io/github/yuninggu/evolune/utils/MahiroJsonFormat.kt` | Existing public compatibility façade, refactored to DTO codec | Yes | No | No | Service/tests only |
| `app/src/main/java/io/github/yuninggu/evolune/application/Batch6DoseEventCompatibility.kt` | Temporary bridge | N/A | N/A | N/A | Must have zero callers, then remove |

`MedicationPlanPredictor.kt`, `HRTViewModel.kt`, `WidgetWork.kt`, and the
required existing mapper/test files are expected modifications for cutover.
`HomeScreen.kt` and `WearDataLayer.kt` should remain unchanged for event
conversion unless a parity audit proves a direct duplicate path. No Entity,
DAO, Repository contract, schema, Gradle, Manifest, JSON wire, or PK
algorithm file is an authorized target.

## 20. Expected test file inventory

Expected focused JVM coverage includes:

- `app/src/test/java/io/github/yuninggu/evolune/external/mahiro/v1/MahiroV1CodecTest.kt`;
- `app/src/test/java/io/github/yuninggu/evolune/external/mahiro/v1/MahiroV1DomainAdapterTest.kt`;
- `app/src/test/java/io/github/yuninggu/evolune/application/MahiroJsonV1ImportServiceTest.kt`
  (or the existing application test package with the same boundary);
- `app/src/test/java/io/github/yuninggu/evolune/core/adapter/DomainDoseEventToPkAdapterTest.kt`;
- updated `MahiroJsonFormatTest.kt`, HRT/Widget compatibility tests, and
  PK parity tests;
- updated existing `Batch6DoseEventCompatibilityTest.kt` only as an
  intermediate migration step, followed by removal when its bridge no
  longer exists.

Instrumentation coverage may extend the existing disposable Repository
cutover test for JSON import round trip, but it must never open the production
database. Existing migration and schema tests are regression gates, not
Batch 7 implementation targets.

## 21. Implementation staging without new roadmap numbering

These are internal implementation stages under the existing Batch 7 roadmap
entry. They are not new roadmap Batch 7A/7B labels.

### Stage A: JSON DTO and pure adapter

**Atomic boundary:** text/DTO and DTO/Domain are complete before any
production import call site changes.

**Expected files:** DTO, codec, Domain adapter, focused tests, and the
existing formatter façade only as needed.

**Stop if:** wire fields change, parser behavior cannot be preserved, valid
UUIDs are not retained, or missing/corrupt ID semantics become ambiguous.

**Independent review:** yes, before production cutover.

### Stage B: JSON production cutover

**Atomic boundary:** HRT import/export uses the DTO/Domain service and the
Repository contract; no old JSON bridge writer remains.

**Expected files:** application import service, HRTViewModel, formatter
facade, JSON call-site tests, disposable Repository integration regression.

**Stop if:** parser writes storage, import uses upsert/clear/fallback, a
conflict overwrites, a storage exception is reported as success, or metadata
defaults differ from Section 5.3.

**Independent review:** yes, before PK bridge removal.

### Stage C: Formal PK adapter and parity

**Atomic boundary:** formal adapter passes old/new structural and simulation
parity before any consumer changes are considered complete.

**Expected files:** formal adapter, HRT/Widget call sites, shared mapping
cleanup, adapter and parity tests. Predictor may consume shared explicit
mapping but keeps its own scheduling logic.

**Stop if:** adapter sorts/filters/selects, the order changes, algorithm or
parameters change, or any numerical output exceeds `1e-6`.

**Independent review:** yes, with route/ester and PK-owner review.

### Stage D: Consumer cutover and bridge removal

**Atomic boundary:** HRT and Widget have no private event PK projection;
Home/Wear have no duplicate conversion; old bridge production references are
zero.

**Expected files:** static boundary tests, HRT/Widget regression tests,
removal/replacement of Batch 6 compatibility files, and report updates only
after validation.

**Stop if:** any consumer bypasses Repository, any legacy writer appears, any
Wear/Widget protocol changes, or parity is only final-value based.

**Independent review:** yes; this is the final Batch 7 implementation gate.

## 22. Test device and JVM matrix

### JVM

- JSON codec and DTO tests;
- JSON Domain adapter tests;
- Repository import service tests with fakes that count calls and outcomes;
- HRT JSON import/export tests;
- formal PK adapter tests;
- old/new parity tests for every supported route, ester, extras, order, and
  time boundary;
- Predictor legacy/Domain parity tests;
- existing SimulationEngine, parameter, migration, mapper, core, and PK
  regression suites unchanged;
- no skipped or ignored tests and no loop-only assertions.

### Instrumentation

- disposable v3 Repository import/idempotency/conflict round trip;
- no production database access;
- schema 2 and 3 content/hash equality checks;
- existing migration, rollback, FK, cascade, unique index, and full-content
  Room equality regression.

### Device matrix

- API 33 phone AVD: JSON import/export and HRT/Widget regression;
- API 35 phone AVD: same matrix;
- Wear OS AVD remains a separate Wear acceptance target and is never used
  for phone UI tests;
- the existing Batch 6 phone/watch automation boundary remains explicit:
  protocol fixtures and phone-side tests do not claim a real paired
  phone-to-watch round trip unless one is actually executed.

## 23. Static and release gates

The final production scan must have zero references outside the allowed data
persistence implementation/test boundaries to:

- `Batch6DoseEventCompatibility` and `Batch6HrtPkProjection`;
- temporary JSON bridge functions;
- private Domain-to-PK projections and duplicate Route/Ester/ExtraKey maps;
- JSON parser direct Repository writes;
- PK adapter Repository queries or writes;
- `AppDatabase`, DAO, Entity, or legacy Repository in UI, ViewModel,
  receiver, Widget, Wear, or external protocol code.

The scan must separately prove that retaining legacy Entity/DAO/Repository
definitions and `timeH`/`timeOfDay` shadows is intentional and not a bypass.

Release remains prohibited until the existing Batch 8/ADR-016 gate is met:
device migration matrix, complete Repository implementation, metadata
double-write, all production entry points contract-backed, JSON source/time
adaptation, PK parity, and Phase 1 exit validation.

## 24. Stop conditions

Stop design/implementation and return to review if any of the following is
found:

- current v1 files cannot be read without changing the wire format;
- valid, missing, or corrupt UUID behavior cannot be uniquely preserved;
- malformed entry, unknown field, null/default, or import conflict behavior
  remains ambiguous after source/test review;
- formal PK conversion cannot reproduce old structural input or simulation
  parity;
- the formal adapter must select, sort, filter, call Repository, or run PK;
- parity requires modifying `SimulationEngine`, PK algorithms, parameters, or
  tolerance;
- formal adapter implementation requires modifying Domain, schema, migration,
  Repository contracts, or `DoseEvent` to add `planId`;
- Route/Ester removal is required rather than optional cleanup;
- JSON v1 must contain source/status/revision/zone/localDate/slot metadata;
- legacy `timeH` or `timeOfDay` must be deleted to pass Batch 7;
- a temporary bridge must remain as a second production fact path;
- Widget/Wear/HRT cannot share the adapter without changing protocol or
  selection behavior;
- Repository import needs upsert, dual write, silent fallback, clear-and-
  import, or a second database;
- a real or real-derived database/data set is needed;
- any P0 or P1 remains unresolved;
- the implementation would imply Room v3 is releasable.

## Deferred Wear product requirements

The following Wear product requirements are explicitly out of scope for Batch 7
and must not be implemented as part of the JSON-v1 or Domain-to-PK adapter cutover:

- Wear remains a separate APK.
- Phone remains the only plan creation/edit/delete authority.
- Wear app must eventually provide:
  - current concentration;
  - PK curve;
  - previous two scheduled occurrences with scheduled time and actual recorded time;
  - one current/nearest actionable occurrence;
  - next five scheduled occurrences;
  - confirm-dose action;
  - postpone/snooze action.
- Wear Tile must eventually provide:
  - current concentration;
  - current/nearest actionable occurrence;
  - confirm-dose action;
  - postpone/snooze action.
- Wear Tile MUST NOT display the PK curve.
- Wear plan/timeline data remain derived from the phone source of truth.
- Batch 7 must preserve existing Wear protocol and behavior and must not introduce
  timeline, snooze, Tile UI, or protocol changes.

## Final decision

No contract, schema, ADR, JSON protocol, Wear protocol, or PK algorithm
conflict was found. The four P2 items are documented and non-blocking.

**Batch 7 design ready for independent review.**

Batch 7 has not been implemented. Room v3 remains internal and unreleasable.
No production database, release, staging, commit, or tag is authorized by
this design task.
