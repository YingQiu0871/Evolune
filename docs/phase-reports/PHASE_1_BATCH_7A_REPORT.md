# Phase 1 Batch 7A Report

Date: 2026-08-10

Status: Batch 7A implementation complete pending independent review.

## 1. Baseline

- Worktree: `D:\Evolune-batch7a`
- Branch: `phase1/batch7a-json-v1`
- Design commit: `4b7cd3c8013cab396088fee404fd58f3d8eacc3f`
- Independent design review commit: `ac0ce360060d2071afa14664b79a0f65c8954031`
- Baseline tag: `phase-1-batch-7-design-v1`
- Baseline tag target and implementation parent: `ac0ce360060d2071afa14664b79a0f65c8954031`
- Design review decision: `APPROVE WITH P2`
- Design review findings: `P0/P1/P2 = 0/0/3`

The three design-review findings remain preserved as review history. This
implementation applies their constraints without modifying the sealed design
or review documents: JSON v1 has no MedicationPlan projection, Custom
medication remains deferred, and Widget appearance/work remains deferred.

## 2. Scope

Batch 7A establishes only the JSON v1 protocol boundary:

- protocol DTOs and typed document/entry diagnostics;
- JSON text to DTO decoding and DTO to JSON text encoding;
- protocol event DTO to Domain DoseEvent import adaptation;
- Domain DoseEvent to protocol event DTO export adaptation;
- pure focused tests and compatibility regression tests.

Batch 7A does not implement a Repository-backed import service, database
writes, HRT production cutover, Domain-to-PK conversion, PK changes, UI,
Widget, Wear, Custom medication, schema changes, migrations, or release work.
JSON v1 contains events only; it does not contain MedicationPlan, schedule,
or slot protocol DTOs.

## 3. Existing Behavior Preserved

The existing `MahiroJsonFormat` facade and `Batch6MahiroJsonBridge` production
path remain unchanged in Batch 7A. The formal boundary is introduced in
parallel so production cutover remains an atomic Batch 7B responsibility.

Compatibility preserved by the new boundary includes:

- top-level `meta`, `weight`, `events`, `labResults`, and `doseTemplates`;
- missing `events` as an empty list;
- parser selection independent of `meta.version`;
- unknown top-level and event fields ignored;
- malformed individual entries diagnosed and skipped without dropping valid
  later entries;
- malformed documents returned as document-level failures;
- exact route, ester, dose, timeH, and six extras wire meanings;
- event order and deterministic extras order on export;
- pretty JSON with `meta.version = 1`, generated `exportedAt`, and empty
  `labResults` and `doseTemplates` arrays.

## 4. Protocol DTO

Package: `io.github.yuninggu.evolune.external.mahiro.v1`

- `MahiroV1DocumentDto` represents nullable weight and ordered event DTOs.
- `MahiroV1DoseEventDto` represents raw v1 ID, route, ester, timeH, doseMG,
  and extras fields before Domain conversion.
- `MahiroV1DecodeResult`, `MahiroV1DocumentError`, and
  `MahiroV1EntryDiagnostic` distinguish document failure from indexed entry
  diagnostics.

The DTO layer contains no Android, Room, Repository, DAO, Entity, PK event,
UI, or system-timezone dependency. Shared `pk.Route` and `pk.Ester` appear
only in the Domain adapter because ADR-015 still assigns those enums to the
current Domain contract.

## 5. Codec Boundary

`MahiroV1Codec` owns only:

```text
JSON text <-> MahiroV1DocumentDto
```

It uses the existing `kotlinx.serialization.json` dependency. No Gradle or
dependency change was required. It does not parse UUID semantics, construct
Domain objects, call Repository/DAO/Room, perform PK simulation, or write
storage.

Decoding parses event entries independently. Structural entry errors retain
their source index. Unknown extras and non-numeric extra values remain
ignored at the protocol boundary, matching the existing facade behavior.

Encoding receives an injectable `Clock`; production defaults to UTC system
time and tests use a fixed clock for complete golden JSON equality.

## 6. Domain Adapter

`MahiroV1DoseEventAdapter` owns only:

```text
MahiroV1DoseEventDto -> core.model.DoseEvent
core.model.DoseEvent -> MahiroV1DoseEventDto
```

It uses explicit mappings for all seven routes, all five esters, and all six
extras keys. It does not use enum ordinal, lowercase heuristics, Repository,
DAO, Room, Entity, Android, UI, or PK simulation.

Imported Domain events receive the locked metadata:

- `zoneId = null`
- `localDate = null`
- `slotId = null`
- `source = JSON_V1`
- `status = RECORDED`
- `revision = 1L`

Domain-only metadata is intentionally absent from exported JSON v1 DTOs.

## 7. ID Semantics

- Valid UUID strings are preserved.
- Missing IDs use `UUID.randomUUID()` through an injectable supplier.
- Blank or malformed IDs use the same random UUID behavior.
- Independent missing/corrupt adaptations create independent UUIDs.
- No UUIDv5, hash, deterministic repair, zero UUID, or exception-only
  replacement behavior was introduced.

The random behavior means repeated imports of an event without a valid ID are
not inherently idempotent. Batch 7A preserves this existing v1 behavior.

## 8. Time Semantics

Import delegates to `LegacyTimeAdapter.timeHToInstant`. Export delegates to
`LegacyTimeAdapter.instantToTimeH`. The adapter therefore retains the locked
finite checks, multiplication limits, `Long` bounds, and `Math.round`
quantization, including negative and sub-millisecond values.

No current date, system zone, locale, or best-effort time fallback is used.
An unrepresentable Domain `Instant` returns the typed
`UnrepresentableInstant` export failure; it is not truncated, clamped,
omitted, or replaced.

## 9. Extras Semantics

The exact supported wire keys remain:

- `sublingualTier`
- `sublingualTheta`
- `concentrationMgMl`
- `areaCm2`
- `releaseRateUgPerDay`
- `antiAndrogenType`

Import ignores unknown keys and malformed extra values. Export constructs
extras in the fixed order above and preserves numeric values without unit
conversion or clamping. No Custom medication identity or placeholder is
encoded through extras.

## 10. Error Semantics

The implementation distinguishes:

- document syntax failure;
- document representation failure;
- indexed entry representation diagnostics;
- unknown route or ester Domain conversion failure;
- invalid timeH Domain conversion failure;
- unrepresentable Domain Instant export failure.

Repository conflicts, storage failures, import summaries, retries, and
per-item persistence outcomes are not Batch 7A types. They remain reserved
for Batch 7B.

## 11. Compatibility

The existing facade was not rewritten or removed. New-boundary compatibility
tests compare synthetic v1 data with `MahiroJsonFormat` semantics. Existing
facade and Batch 6 bridge tests also pass unchanged.

The semantic round trip preserves every field representable by JSON v1 and
explicitly applies the fixed import defaults to Domain-only metadata. It does
not claim a lossless Domain round trip.

## 12. Tests

All fixtures use synthetic UUIDs and synthetic values. No real or
real-derived database or JSON export was used.

| Validation | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Batch 7A focused codec/adapter JVM | 2 | 25 | 0 | 0 | 0 | PASS |
| Existing JSON facade/Batch 6 compatibility JVM | 2 | 21 | 0 | 0 | 0 | PASS |
| Full App JVM | 45 | 389 | 0 | 0 | 0 | PASS |
| PK regression JVM | 5 | 49 | 0 | 0 | 0 | PASS |
| Existing JSON production-path instrumentation | 1 | 2 | 0 | 0 | 0 | PASS |

The focused instrumentation regression ran on `emulator-5556`, model
`sdk_gphone64_x86_64`, Android 13, API 33. XML output:
`app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`.
HTML output:
`app/build/reports/androidTests/connected/debug/index.html`.

Additional gates:

| Gate | Result |
|---|---|
| `:app:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS, 0 errors, 83 warnings, 1 hint |
| `:app:kspDebugKotlin --rerun-tasks` | PASS |
| `git diff --check` | PASS |

The first assemble attempt reached signing validation and stopped because a
new worktree does not contain ignored `app/debugkeystore.jks`. The existing
baseline debug keystore was copied temporarily, the build passed, and the
temporary copy was removed. It is absent from final status.

## 13. Boundary Audit

- Three new production files exist only under `external/mahiro/v1`.
- Two new focused test files exist under the matching test package.
- No existing production or test file was modified.
- No Repository implementation/contract, DAO, Entity, Room database,
  migration, schema, Gradle, PK, UI, navigation, Widget, or Wear file changed.
- New production files contain no Repository, DAO, Entity, Room, Android,
  Compose, Widget, Wear, SimulationEngine, or ParameterResolver reference.
- Schema 2 and schema 3 working-tree blobs equal their `HEAD` blobs after
  explicit KSP execution.
- The staging area remains empty.

## 14. Deferred Work

- Batch 7B: Repository-backed JSON import service and production HRT cutover.
- Batch 7C: formal Domain-to-PK adapter, numerical parity, consumer cutover,
  and Batch 6 bridge removal.
- MedicationPlan JSON import/export, schedules, and slots.
- Custom medication identity and its Domain/persistence/protocol design.
- Widget Material You and transparency work.
- Wear timeline, occurrence, snooze/postpone, and Tile work.
- Batch 8, release validation, and Room v3 release authorization.

Batch 7B is not authorized by this report.

## 15. Risks

`P0/P1/P2 = 0/0/3`

- P2: missing/corrupt v1 IDs intentionally retain random, non-idempotent UUID
  creation.
- P2: JSON v1 cannot represent Domain source, status, revision, zoneId,
  localDate, or slotId; round trip is intentionally a protocol projection.
- P2: Domain Route/Ester still use the accepted ADR-015 transitional PK enum
  ownership.

No contract, schema, migration, dependency, Repository, PK algorithm, Widget,
Wear, or Custom medication conflict was found.

## 16. Decision

**Batch 7A implementation complete pending independent review.**

Batch 7B has not started and is not authorized. Room v3 remains internal and
unreleasable. No staging, commit, tag, independent review, or release was
performed.
