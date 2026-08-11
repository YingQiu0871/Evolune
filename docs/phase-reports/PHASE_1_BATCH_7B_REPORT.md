# Phase 1 Batch 7B Report

Date: 2026-08-10

Status: Batch 7B implementation complete pending independent review.

## 1. Baseline

- Worktree: `D:\Evolune-batch7b`
- Branch: `phase1/batch7b-import-service`
- Sealed Batch 7A tag: `phase-1-batch-7a`
- Batch 7A tag target: `ce073697f3b699bb3b03eef8f5aff423937dcd40`
- Batch 7A integration merge: `9733c6daf3e2e0c05510629f399aa6cdb6a5ca19`
- Batch 7A implementation commit: `da3a3b6e191a4c5c1a59693acf9cb3ea3025c947`
- Batch 7A review commit: `ce073697f3b699bb3b03eef8f5aff423937dcd40`

The implementation worktree was created from the clean Batch 7A integration
merge. The staging area remains empty. No Batch 7B commit, tag, review, or
release was created.

## 2. Scope

Batch 7B implements only:

- a Repository-backed Mahiro JSON v1 import service;
- the existing HRT ViewModel import cutover to that service;
- typed import summaries and storage/document failure results;
- JVM, ViewModel, and disposable Room cutover regression tests.

Batch 7B does not implement the Domain-to-PK adapter or any PK consumer
cutover. It does not modify the JSON v1 wire contract, the sealed Batch 7A
DTO, codec, or Domain adapter.

The following remain deferred or forbidden: Batch 7C, Widget, Wear expansion,
Custom medication, MedicationPlan JSON, schema or migration work, Domain or
Repository contract changes, a second database, real data, Batch 8, and
release work.

## 3. Import architecture

The production HRT import chain is now:

```text
JSON text
  -> MahiroV1Codec
  -> MahiroV1DoseEventAdapter
  -> MahiroJsonV1ImportService
  -> DoseEventRepository.insert
  -> RoomDoseEventRepository
  -> Room
```

`MahiroJsonV1ImportService` is an application-layer orchestrator. The codec
does not call storage, the adapter does not call storage, and the service does
not call DAO, Entity, AppDatabase, or the legacy Repository implementation.
The ViewModel keeps its existing public import entry point, operation gate,
coroutine lifecycle, UI state shape, and successful weight callback timing.

The legacy `Batch6MahiroJsonBridge` remains only for the existing export
compatibility path and tests. Its import function has zero production callers
after this cutover. It was not removed because bridge cleanup is a later
boundary-removal task and Batch 7C is not authorized.

## 4. Repository result semantics

The service consumes the existing `DoseEventRepository.insert` result directly:

| Repository result | Import summary | Continue |
|---|---|---|
| `Inserted` | `insertedCount += 1` | Yes |
| `Idempotent` | `idempotentCount += 1` | Yes |
| `Conflict` | `conflictCount += 1`; original row remains | Yes |
| `Invalid` | `invalidCount += 1`; no fallback | Yes |
| Runtime storage/infrastructure failure | `failedCount = 1` with partial summary and source index | No |

The service performs no payload equality comparison, content hash, timestamp
comparison, upsert, overwrite, retry through a legacy writer, clear-and-import,
or whole-file rollback. A conflict is not a storage failure. Previously
processed entries remain represented in the partial summary, and entries after
the failing source index are not submitted to the Repository.

Codec entry diagnostics and adapter conversion failures count as invalid and
do not block later valid entries. A document-level decode failure returns a
typed document failure without a Repository call.

## 5. ID cutover boundary

The sealed Batch 7A behavior is now the production import behavior:

- valid UUID string: preserved;
- missing ID: generated UUID;
- blank string ID: generated UUID;
- malformed string ID: generated UUID;
- numeric JSON ID token: invalid entry diagnostic and skipped, with no
  generated replacement and no Repository call.

The numeric-token behavior intentionally differs from the more permissive
legacy `MahiroJsonFormat` path. It is required by the locked Batch 7 v1
contract and is explicitly tested at the service and HRT caller boundaries.
If historical production files are later shown to depend on numeric IDs, that
compatibility question is a stop condition for review; it is not silently
changed in Batch 7B.

Missing, blank, and malformed string IDs use independent random UUID
generation. Repeating such a payload is therefore not guaranteed to be
idempotent. A stable valid ID is the only repeated-import identity guarantee.

## 6. HRT cutover behavior

HRT import now consumes the formal service result. The existing UI-facing
success fields continue to report accepted entries, idempotent entries,
conflicts, and invalid entries. Storage failures preserve partial accepted,
existing, conflict, invalid, and failing-index data in `ImportResult.Error`;
the existing generic error presentation remains unchanged. Parsed weight is
applied only after a successful import result and is not applied after a
document or storage failure.

The HRT export path remains unchanged and continues to use the legacy facade
compatibility path. No export wire behavior was changed in this sub-batch.

## 7. Changed files

Production:

- `app/src/main/java/io/github/yuninggu/evolune/application/MahiroJsonV1ImportService.kt`
  - application import orchestration, summary, and typed failure results;
- `app/src/main/java/io/github/yuninggu/evolune/viewmodel/HRTViewModel.kt`
  - minimal import dependency and result mapping cutover.

Tests:

- `app/src/test/java/io/github/yuninggu/evolune/application/MahiroJsonV1ImportServiceTest.kt`
  - focused fake-Repository service contract tests;
- `app/src/test/java/io/github/yuninggu/evolune/viewmodel/HRTViewModelTest.kt`
  - HRT summary, storage partial-result, and numeric-ID cutover tests;
- `app/src/androidTest/java/io/github/yuninggu/evolune/data/repository/DoseEventProductionCutoverTest.kt`
  - disposable Room first import, idempotent replay, and conflict preservation.

No sealed Batch 7A DTO, codec, or Domain adapter file was modified. No
Repository contract, DAO, Entity, AppDatabase, schema, migration, Gradle,
Manifest, PK, Widget, Wear, or real-data file was modified.

## 8. Validation

| Validation | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Batch 7B focused service + HRT JVM | 2 | 25 | 0 | 0 | 0 | PASS |
| Sealed Batch 7A codec/adapter JVM | 2 | 25 | 0 | 0 | 0 | PASS |
| Legacy JSON facade + Batch 6 compatibility JVM | 2 | 21 | 0 | 0 | 0 | PASS |
| Repository contract JVM | 1 | 3 | 0 | 0 | 0 | PASS |
| Full App JVM | 46 | 402 | 0 | 0 | 0 | PASS |
| PK regression JVM | 5 | 49 | 0 | 0 | 0 | PASS |
| API 33 focused Room cutover instrumentation | 1 | 2 | 0 | 0 | 0 | PASS |
| API 35 focused Room cutover instrumentation | 1 | 2 | 0 | 0 | 0 | PASS |

The focused Room instrumentation test covers first insert, stable replay,
same-ID different-content conflict, original-row preservation, v3 metadata,
reopen behavior, CAS update, delete, and disposable database cleanup.

Device evidence:

- API 33: serial `emulator-5556`, model `sdk_gphone64_x86_64`, Android 13,
  API level 33; two tests passed.
- API 35: serial `emulator-5560`, model `sdk_gphone64_x86_64`, Android 15,
  API level 35; two tests passed.
- Wear serial `emulator-5558` was not used for this phone-side import gate.
- API 37 foldable `emulator-5554` was not used for this Batch 7B gate.

Instrumentation report paths:

- API 33 XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Evolune_API33_Migration(AVD) - 13-_app-.xml`
- API 35 XML: `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_7(AVD) - 15-_app-.xml`
- HTML: `app/build/reports/androidTests/connected/debug/index.html`

Additional gates:

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin --rerun-tasks` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:lintDebug --rerun-tasks` | PASS, 0 errors, 83 warnings, 1 hint |
| `git diff --check` | PASS |

Lint produced 84 warning/hint-level findings and zero errors. The required
synthetic debug keystore was copied
temporarily for device/build execution, verified against the approved source,
and removed before the final boundary audit.

## 9. Schema and persistence gates

Room remains version `3` with `exportSchema = true`. KSP regenerated schema
output successfully. The schema files had no Git diff after generation.

| Schema | Identity hash | Canonical Git blob SHA-256 | Change |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | none |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | none |

No migration, Entity, DAO, Room implementation, or Repository contract change
was made. Room v3 remains an internal, unreleasable version.

## 10. Boundary and risk audit

- P0: 0
- P1: 0
- P2: 4

The four non-blocking P2 items are:

1. Numeric JSON IDs intentionally change from legacy permissive handling to
   formal v1 invalid/skip handling at this production boundary.
2. Missing, blank, and malformed string IDs intentionally use random UUIDs and
   therefore do not guarantee repeated-import idempotency.
3. JSON v1 cannot represent Domain `source`, `status`, `revision`, `zoneId`,
   `localDate`, or `slotId`; import uses locked defaults and round trip is a
   protocol projection, not lossless Domain metadata preservation.
4. Domain `Route` and `Ester` continue to use the accepted ADR-015 transitional
   PK enum ownership.

No new P0/P1 was found. No contract, schema, migration, dependency, PK
algorithm, Widget, Wear protocol, Custom medication, or second-fact-source
conflict was found.

## 11. Deferred work and decision

Batch 7C remains unauthorized and will own the formal Domain-to-PK adapter,
parity, consumer cutover, and later Batch 6 bridge removal. Widget, Wear
feature expansion, Custom medication identity, MedicationPlan JSON, Batch 8,
release validation, and Room v3 release remain deferred or forbidden.

**Batch 7B implementation complete pending independent review.**
