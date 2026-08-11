# Phase 1 Batch 7C Report

Date: 2026-08-11

Status: Batch 7C implementation complete pending independent review.

## 1. Baseline

- Worktree: `D:\Evolune-batch7c`
- Branch: `phase1/batch7c-domain-pk`
- Sealed Batch 7B tag: `phase-1-batch-7b`
- Batch 7B implementation SHA: `c6fc7bc6d380436adcb75e774a7ef87d27e30259`
- Batch 7B review SHA and tag target: `a2dd2257b2484cd361dee985ba0ad87be49450c9`
- Batch 7B integration merge SHA: `44d4dfd6db6609a56d67867390b6cdf41487bb86`
- Batch 7C parent SHA: `44d4dfd6db6609a56d67867390b6cdf41487bb86`

The worktree was created from the validated Batch 7 integration head after
the Batch 7B implementation, review, and annotated tag were sealed. The
staging area remains empty. No Batch 7C commit, tag, review, release, or
Batch 8 work was created.

## 2. Scope

Batch 7C implements only:

- a formal pure Kotlin Domain DoseEvent to PK DoseEvent adapter;
- structural and numerical old/new projection parity;
- minimal HRT and Widget persisted-event consumer cutover;
- one shared explicit Domain ExtraKey to PK ExtraKey mapping;
- formal JSON v1 export replacement required to remove the Batch 6 bridge;
- Batch 6 bridge removal after production reachability reached zero;
- focused, compatibility, boundary, and regression tests.

No Domain, Repository contract or implementation, DAO, Entity, Room,
schema, migration, Gradle, dependency, Manifest, PK algorithm, PK parameter,
selection rule, tolerance, Widget protocol, Wear protocol, Custom medication,
MedicationPlan JSON, Batch 8, or release change was made.

## 3. Previous Production Path

Before Batch 7C, persisted Domain events reached PK through two temporary
production projections:

```text
HRT Repository Domain events
  -> Batch6HrtPkProjection
  -> SimulationEngine

Widget Repository Domain events
  -> WidgetWork.toWidgetPkEvent
  -> SimulationEngine
```

`Batch6MahiroJsonBridge.import` already had zero production callers after
Batch 7B. `Batch6MahiroJsonBridge.export` was still the HRT JSON v1 export
path and projected Domain events through a temporary PK event solely to call
the legacy formatter.

`MedicationPlanPredictor` is a separate plan-to-future-PK-event boundary. It
continues to own schedule calculation, DST behavior, future selection,
conflict filtering, ordering, and synthetic prediction IDs.

## 4. Formal Adapter Architecture

The new production chain is:

```text
Repository Domain events
  -> existing consumer selection/filter/order
  -> DomainDoseEventToPkAdapter
  -> existing PK DoseEvent
  -> unchanged SimulationEngine
```

`DomainDoseEventToPkAdapter` is under `core.adapter` and imports only Domain,
legacy time compatibility, and current PK model types. It has no Android,
Room, Repository, DAO, Entity, Context, UI, Widget, Wear, system clock,
system timezone, locale, random ID, sorting, filtering, or simulation
dependency.

One input event maps to one output event. List adaptation uses ordinary
ordered `map` and does not select, filter, sort, deduplicate, canonicalize, or
mutate input.

## 5. Selection Ownership

Selection remains outside the adapter.

- `RoomDoseEventRepository.getEventsForPk(asOf)` retains the 30-day window.
- If fewer than 20 non-removal dose events are in the window, the existing
  recent-20 fallback remains authoritative.
- HRT still requests the Repository selection at its captured `now`.
- Widget still requests the Repository selection at its captured `now`.
- Predictor still selects enabled plans and future occurrences independently.

No query, time window, event count, plan selection, or second fact source was
introduced into the adapter.

## 6. Ordering Ownership

Ordering remains outside the adapter.

- The recent-window Repository branch remains ascending by occurrence and ID.
- The recent-20 fallback branch remains descending by occurrence and ID.
- HRT preserves the selected historical order before appending the existing
  Predictor output.
- Widget preserves Repository order after its existing filters.
- Predictor retains its existing future-event sorting.
- Equal timestamps remain distinct and retain caller order through adaptation.

The adapter adds no timestamp sort, ID tie-breaker, set conversion, or
canonical ordering.

## 7. Filtering Ownership

Filtering remains in existing consumers.

- HRT filters to `RECORDED` and excludes `ANTIANDROGEN` before adaptation.
- Widget excludes `ANTIANDROGEN` and future events before adaptation.
- Predictor keeps enabled-plan, schedule, future, and conflict-window filters.
- The adapter adds no source, status, route, ester, metadata, or date filter.

## 8. Route, Ester, Dose, and Time Mapping

The formal mapping is:

| Domain field | PK field | Rule |
|---|---|---|
| `id` | `id` | same UUID |
| `route` | `route` | direct identity under ADR-015 shared enum ownership |
| `ester` | `ester` | direct identity under ADR-015 shared enum ownership |
| `occurredAt` | `timeH` | `LegacyTimeAdapter.instantToTimeH` |
| `doseMG` | `doseMG` | same `Double`, no conversion or correction |

No unit conversion, rounding beyond the existing legacy epoch-millisecond
projection, clamp, absolute value, fallback, replacement ID, or timezone
invention was added. An unrepresentable `Instant` remains an explicit
`IllegalArgumentException` compatibility failure.

Domain-only `zoneId`, `localDate`, `slotId`, `source`, `status`, and
`revision` are intentionally omitted because the current PK input does not
represent or consume them.

## 9. Extras Mapping

The one shared exhaustive mapping covers:

- `CONCENTRATION_MG_ML`
- `AREA_CM2`
- `RELEASE_RATE_UG_PER_DAY`
- `SUBLINGUAL_THETA`
- `SUBLINGUAL_TIER`
- `ANTI_ANDROGEN_TYPE`

Values are preserved without normalization or unit conversion. The previous
copies in the Batch 6 bridge, Widget, and Predictor were removed. The data
mapper retains only storage-to-Domain and PK-to-Domain responsibilities and
its tests import the shared Domain-to-PK mapping owner.

## 10. Structural Parity

Focused JVM tests compare the old temporary projection oracle with the new
adapter field by field. Exact equality passed for:

- ID, route, ester, timeH, doseMG, and all six extras;
- all seven current routes and all five current esters;
- empty lists, input order, duplicate timestamps, and same-time events;
- negative and millisecond timestamps;
- zero and negative Domain dose values without correction;
- Domain-only metadata omission without input mutation;
- explicit failure for an unrepresentable `Instant`.

Structural parity result: PASS.

## 11. Numerical Parity

The synthetic parity corpus includes representative oral, sublingual,
injection, gel, patch apply/remove, antiandrogen, mixed-route, mixed-ester,
same-timestamp, repeated-dose, long-history, and sparse-history inputs.

For every corpus, the old oracle and formal adapter feed the same unchanged
`SimulationEngine` with the same body weight, range, step count, count, and
order. Tests compare exact sample times, every concentration sample, and AUC.

- Required absolute tolerance: `1e-6`
- Maximum observed concentration delta: `0.0`
- AUC delta: `0.0`
- Result: PASS

No PK equation, parameter, solver, interpolation, simulation range, or
tolerance was changed.

## 12. Consumer Cutover

HRT now uses the formal adapter for:

- `doseTimePoints` projection from its observed Domain event list;
- selected historical PK inputs before baseline simulation;
- selected historical PK inputs before full historical-plus-predicted
  simulation.

Widget now uses the same adapter after its existing plan/event selection and
filters. Its body-weight lookup, two-point simulation range, current-time
conversion, rendering, quick action, idempotency, and side-effect behavior
are unchanged.

Home continues consuming HRT `PKState`. Wear continues consuming the already
derived HRT `SimulationResult`. Neither gained a second event adapter.

## 13. Legacy and Bridge Removal

Removed production bridge:

- `application/Batch6DoseEventCompatibility.kt`
  - retired import writer;
  - temporary HRT PK projection;
  - temporary JSON export projection;
  - duplicate ExtraKey maps.

Removed bridge-only test:

- `application/Batch6DoseEventCompatibilityTest.kt`

Its responsibilities now have separate formal coverage:

- import: sealed Batch 7A codec/adapter plus Batch 7B import service tests;
- export: `MahiroJsonV1ExportService` and compatibility tests;
- PK projection: formal adapter structural/numerical parity tests;
- production reachability: static boundary test.

Legacy Mahiro JSON v1 export functionality remains. HRT now exports Domain
events through `MahiroV1DoseEventAdapter` and `MahiroV1Codec`, preserving wire
fields and order. `MahiroJsonFormat` remains as a compatibility/test oracle
with zero production callers. Legacy Entity/DAO/Repository definitions,
`timeH`, `timeOfDay`, migration shadows, and persistence mappers remain and
were not treated as Batch 7 bypasses.

## 14. Tests and Build Gates

All fixtures use synthetic UUIDs, times, plans, events, and JSON. No real,
anonymized-from-real, or real-derived medication/health data was used.

| Validation | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Batch 7C focused adapter/export/HRT/Widget/mapper/Predictor JVM | 8 | 43 | 0 | 0 | 0 | PASS |
| Sealed Batch 7A codec/adapter JVM | 2 | 25 | 0 | 0 | 0 | PASS |
| Batch 7B import service + updated HRT JVM | 2 | 26 | 0 | 0 | 0 | PASS |
| Legacy facade + formal v1 export compatibility JVM | 2 | 16 | 0 | 0 | 0 | PASS |
| Full App JVM | 49 | 406 | 0 | 0 | 0 | PASS |
| PK regression JVM | 5 | 49 | 0 | 0 | 0 | PASS |

Additional gates:

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS, 0 errors, 70 warnings |
| `:app:compileDebugAndroidTestKotlin --rerun-tasks` | PASS |
| `git diff --check` | PASS |

The androidTest result is compilation only. Batch 7C does not claim a
connected device test from that gate. The core parity oracle is pure JVM and
does not depend on instrumentation.

A baseline ignored debug keystore was copied temporarily for assemble/lint,
verified by SHA-256 against the approved integration source, and removed.
It is absent from final status.

## 15. Schema Identity

Room remains version `3` with `exportSchema = true`. Explicit KSP generation
produced no schema diff.

| Schema | Identity hash | Canonical Git blob SHA-256 | Change |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | none |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | none |

No DAO, Entity, AppDatabase, Repository, migration, schema, or database data
was modified. Room v3 remains internal and unreleasable.

## 16. Boundary Audit

- Production references to `Batch6HrtPkProjection`: zero.
- Production references to `Batch6MahiroJsonBridge`: zero.
- Private Widget persisted-Domain-to-PK projection: removed.
- HRT and Widget direct `PkDoseEvent` construction: zero.
- Formal adapter Repository/DAO/Entity/Room/Android/UI dependency: zero.
- Formal adapter selection/filter/sort/clock/timezone/random behavior: zero.
- `MahiroJsonFormat` production callers: zero; compatibility facade retained.
- PK source changes: zero.
- Domain and Repository contract changes: zero.
- Gradle and dependency changes: zero.
- Widget/Wear protocol changes: zero.
- Staged files: zero.

## 17. Deferred Work

- Batch 8 and Phase 1 exit validation.
- Widget Material You and transparency work.
- Wear timeline, occurrence, snooze/postpone, Tile, and protocol expansion.
- Custom medication identity and its Domain/persistence/protocol design.
- MedicationPlan JSON import/export, schedules, and slots.
- Health Connect, cloud backup, onboarding, and release work.
- Legacy persistence removal after the formal compatibility window.

## 18. Risks

`P0/P1/P2 = 0/0/3`

No new Batch 7C P0, P1, or P2 was found. The three non-blocking independent
Batch 7B review findings remain unchanged and were not opportunistically
modified:

1. HRT presentation still folds document and storage import failures into a
   shared user-facing storage operation category.
2. `MahiroJsonV1ImportService` still catches `RuntimeException` more broadly
   than the storage exception type.
3. Inherited design risks remain grouped: JSON random-ID replay behavior,
   JSON v1 metadata projection limits, numeric-ID cutover semantics,
   ADR-015 Route/Ester ownership, and the existing Wear replay limitation.

None affects structural or numerical Domain-to-PK parity. No contract,
schema, migration, ADR, JSON wire, Widget/Wear protocol, or PK algorithm
conflict was found.

## 19. Decision

**Batch 7C implementation complete pending independent review.**

Batch 8 has not started and is not authorized by this report. Room v3 remains
internal and unreleasable. No staging, commit, tag, independent review, push,
release, or production database operation was performed.
