# Phase 1 Batch 7 Closure Report

## 1. Status

Batch 7 integrated and validated pending independent closure review.

This report does not close Batch 7, authorize Batch 8, create a final Batch 7
tag, or authorize a Room v3 release.

## 2. Final integrated HEAD

- Integration worktree: `D:\Evolune-phase1`
- Integration branch: `phase1/batch7-design`
- Integrated HEAD: `064d164d432c6b6c598b0c42dd030cc20a166384`
- Remediation merge SHA: `064d164d432c6b6c598b0c42dd030cc20a166384`
- Merge subject: `merge: batch 7 closure test remediation`

## 3. Complete ancestry

Every required tag is an ancestor of the integrated HEAD.

| Stage | Implementation SHA | Review/tag SHA | Integration merge SHA |
|---|---|---|---|
| Batch 7 design | `4b7cd3c8013cab396088fee404fd58f3d8eacc3f` | `ac0ce360060d2071afa14664b79a0f65c8954031` (`phase-1-batch-7-design-v1`) | Not applicable |
| Batch 7A | `da3a3b6e191a4c5c1a59693acf9cb3ea3025c947` | `ce073697f3b699bb3b03eef8f5aff423937dcd40` (`phase-1-batch-7a`) | `9733c6daf3e2e0c05510629f399aa6cdb6a5ca19` |
| Batch 7B | `c6fc7bc6d380436adcb75e774a7ef87d27e30259` | `a2dd2257b2484cd361dee985ba0ad87be49450c9` (`phase-1-batch-7b`) | `44d4dfd6db6609a56d67867390b6cdf41487bb86` |
| Batch 7C | `4cd43e6fa100cb863831aadeb8ed10e97914afba` | `f69fd64b25ad612792f7f40d6307fbed79a2c1e8` (`phase-1-batch-7c`) | `7f4ef3bb48466ac42fa92c4d93c4f10c506f6aa7` |
| Closure diagnosis | `49d55a1aa2b0f191e89bf059ec192a6db3a43d39` | Not tagged | Not applicable |
| Closure remediation | `605ba55dd841d6806169467eb05f1f8924de8cf0` | `496e71b93d7570d0bd5e75b8626eef7bec9e351e` (`phase-1-batch-7-closure-remediation`) | `064d164d432c6b6c598b0c42dd030cc20a166384` |

The remediation tag is annotated and points to the independent remediation
review commit.

## 4. Batch 7 objectives completed

- Batch 7A established the formal Mahiro JSON v1 DTO, codec, and Domain
  adapter boundary.
- Batch 7B introduced Repository-backed import and cut the HRT production
  import path over to that service.
- Batch 7C established the formal Domain-to-PK adapter, proved structural and
  numerical parity, cut production consumers over, and retired the Batch 6
  compatibility bridges.
- Closure remediation corrected only instrumentation lifecycle
  synchronization and changed no production code.

## 5. Final architecture

JSON import follows one production path:

```text
JSON
  -> Mahiro v1 DTO
  -> Mahiro v1 codec
  -> Mahiro v1 Domain adapter
  -> MahiroJsonV1ImportService
  -> DoseEventRepository contract
  -> RoomDoseEventRepository / Room v3
```

Persisted event PK follows one production path:

```text
Domain DoseEvent
  -> existing selection / filtering / ordering
  -> DomainDoseEventToPkAdapter
  -> unchanged PK engine
```

`MedicationPlanPredictor` remains the separate plan-to-future-PK-event path;
it is not a duplicate persisted-Domain-event projection.

## 6. JSON final state

The integrated JSON gate passed all formal representation, codec, UUID, time,
enum, extras, and Domain-adapter behavior.

| Gate | Suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Batch 7A codec and Domain adapter | 2 | 25 | 0 | 0 | 0 |
| Import service and HRT cutover | 2 | 26 | 0 | 0 | 0 |
| Formal export service | 1 | 2 | 0 | 0 | 0 |
| Combined formal JSON behavior | 5 | 53 | 0 | 0 | 0 |

Locked ID behavior remains:

- Valid stable UUID strings preserve identity and replay idempotently through
  Repository full-content equality.
- Missing, blank, or malformed UUID strings receive independent random UUIDs.
- Numeric JSON ID tokens are invalid entries and are skipped without a
  Repository call.

## 7. Import final state

The newly measured focused import/repository gate completed as follows:

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `MahiroJsonV1ImportServiceTest` | 12 | 0 | 0 | 0 |
| `HRTViewModelTest` | 14 | 0 | 0 | 0 |
| `RepositoryContractTest` | 3 | 0 | 0 | 0 |
| Total | 29 | 0 | 0 | 0 |

The gate confirms stable-ID idempotency, same-ID content conflicts,
storage-failure abort, exact partial summaries, numeric-ID rejection, and
independent random fallback for missing, blank, and malformed UUID strings.
The full connected suites also exercised the production
ViewModel-to-service-to-Repository-to-Room cutover path.

## 8. Export final state

Formal export remains JSON v1 compatible. It preserves the locked v1 field
order and representation and rejects unrepresentable legacy time values.
`MahiroJsonFormat` remains only a compatibility/test oracle and has zero
production callers.

MedicationPlan JSON was not added.

## 9. PK final state

The formal adapter is the only production projection from persisted Domain
events to PK events. The existing PK engine and its tolerance were not
modified.

| Gate | Suites | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| Adapter, boundary, parity, and export focused gate | 4 | 10 | 0 | 0 | 0 |
| Complete PK regression | 5 | 49 | 0 | 0 | 0 |

## 10. Production reachability

Integrated static searches confirmed:

- `Batch6HrtPkProjection`: zero production references.
- `Batch6MahiroJsonBridge`: zero production references.
- `MahiroJsonFormat`: zero production callers; definition retained as a
  compatibility/test oracle.
- Persisted Domain DoseEvent to PK conversion: only
  `DomainDoseEventToPkAdapter`.
- Production adapter consumers: `HRTViewModel` and the allowed Batch 7C
  `WidgetWork` PK adapter cutover.
- Duplicate persisted-Domain-to-PK production mapping: zero.
- `MedicationPlanPredictor`: retained as the separate plan prediction path.

## 11. Structural parity

The integrated parity test re-ran on the final HEAD. Old and formal adapter
projections had equal event count, ordering, route, ester, dose, timestamps,
and extras. Selection, filtering, prediction, and ordering behavior remained
outside the pure structural adapter and unchanged.

## 12. Numerical parity

- Locked tolerance: `1e-6`
- Maximum observed concentration delta: `0.0`
- AUC delta: `0.0`
- Result: PASS

The integrated test recomputed both old and formal-adapter paths. Every
concentration delta was measured and the maximum was asserted exactly zero;
the deterministic identical curve inputs produced equal AUC values within the
locked tolerance.

## 13. Original closure failure

The original API 33 closure evidence remains visible and is not rewritten by
the final passing run:

| Tests | Failures | Errors | Skipped |
|---:|---:|---:|---:|
| 115 | 1 | 0 | 2 |

- Failure:
  `DoseEventProductionCutoverTest.providerAndHrtPathPersistReopenCasImportConflictAndDelete`
- Exception: `TimeoutCancellationException`
- Failing method duration: 10.163 seconds
- Original suite duration: 71.234 seconds

## 14. Diagnosis and remediation

- Independent diagnosis: `TEST DEFECT`
- Confidence: `HIGH`
- Diagnosis severity: `P2`
- Remediation type: test-only lifecycle synchronization
- Production diff: zero
- Independent remediation review: `APPROVE WITH P2`
- Remediation review P0/P1/P2: `0/0/2`

The test previously observed `ImportResult.Success` before the outer IMPORT
operation had cleared `operationInFlight`. The fix now acknowledges stale
terminal state, starts the current operation, waits for that operation's
terminal IMPORT success state, and only then reads the current import result.
The timeout remains `10_000L`; no retry, sleep, delay synchronization, or
assertion weakening was added.

## 15. Final connected validation

Both phone gates were first-attempt authoritative runs on the final integrated
HEAD. No automatic rerun was used.

| API | Serial | Model | Physical size | Tests | Failures | Errors | Skipped | XML time | Result |
|---:|---|---|---|---:|---:|---:|---:|---:|---|
| 33 | `emulator-5558` | `sdk_gphone64_x86_64` | 1080x2400 | 115 | 0 | 0 | 2 | 846.645 s | PASS |
| 35 | `emulator-5560` | `sdk_gphone64_x86_64` | 1080x2400 | 115 | 0 | 0 | 2 | 716.954 s | PASS |

Both devices expose phone telephony/touchscreen features and no watch feature.
The two skips on each phone are the foldable-only navigation tests.

API 33 Gradle duration was 17 minutes 36 seconds. API 35 Gradle duration was
14 minutes 54 seconds. UTP progress displayed 117 completion entries against
the authoritative XML count of 115; XML records zero failures and errors.

The existing UI/foldable focused matrix was also executed on the foldable
phone AVD `emulator-5554`, API 37, model `sdk_gphone16k_x86_64`, physical size
2076x2152:

| UI suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `FoldableNavigationLayoutTest` | 3 | 0 | 0 | 0 |
| `MedicationPlansScreenTest` | 9 | 0 | 0 | 0 |
| `MedicationRecordsScreenTest` | 9 | 0 | 0 | 0 |
| `ColorRoleConformanceTest` | 3 | 0 | 0 | 0 |
| Total | 24 | 0 | 0 | 0 |

This covers adaptive navigation, foldable width and title geometry, the
MedicationPlan editor regression, medication-record UI, and existing color/UI
smoke behavior without redesigning production UI.

## 16. JVM / PK / Wear validation

All values below were measured on the integrated HEAD.

| Gate | Suites | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---:|---|
| Full App JVM | 49 | 406 | 0 | 0 | 0 | PASS |
| Batch 7A JSON | 2 | 25 | 0 | 0 | 0 | PASS |
| Import/HRT/Repository focused | 3 | 29 | 0 | 0 | 0 | PASS |
| Adapter/export/parity focused | 4 | 10 | 0 | 0 | 0 | PASS |
| Complete PK | 5 | 49 | 0 | 0 | 0 | PASS |
| Wear JVM | 1 | 1 | 0 | 0 | 0 | PASS |

Wear `assembleDebug` passed. Existing Wear behavior is unchanged. Batch 7
adds no Wear timeline, snooze/postpone, Tile, or protocol expansion. The Wear
production tree itself was unchanged by Batch 7.

## 17. Build gates

| Gate | Result |
|---|---|
| `:app:kspDebugKotlin` | PASS |
| `:app:assembleDebug` | PASS |
| `:app:lintDebug` | PASS |
| `:app:compileDebugAndroidTestKotlin` | PASS |
| `:wear:testDebugUnitTest` | PASS |
| `:wear:assembleDebug` | PASS |
| APK 16KB page alignment | PASS |

The final App build command completed successfully in 2 minutes 42 seconds.
Lint reported 0 errors, 83 warnings, and 1 hint. Android build-tools 36.1.0
`zipalign -c -P 16 -v 4` returned `Verification successful` with exit code 0
for `app-debug.apk`.

## 18. Schema identity

KSP completed before the final independent schema check. Both tracked schemas
remain unchanged.

| Schema | Identity hash | Canonical Git blob SHA-256 | Result |
|---|---|---|---|
| 2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` | UNCHANGED |
| 3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` | UNCHANGED |

No migration, AppDatabase, Entity, DAO, schema, or release behavior was
changed by closure remediation or the final gate.

## 19. Historical evidence clarifications

Historical reports remain unchanged:

1. The original API 33 closure gate was 115 tests, 1 failure, 0 errors, and 2
   skips. The failure was diagnosed independently as a high-confidence P2 test
   race.
2. The remediation was test-only and its independent review decision was
   `APPROVE WITH P2`.
3. The adjacent instrumentation wrapper retained an expected historical count
   of 39 while the independently discovered package contained 42 passing
   tests. The stale wrapper count is a maintenance issue, not a test failure.
4. During remediation review, one API 35 task-level transient occurred before
   a later complete authoritative pass. In this final integrated gate, the
   first API 35 authoritative attempt passed cleanly and was not rerun.
5. The Batch 7C report recorded a focused count of 43, while independent review
   ran the broader named eight-suite set and counted 51. This closure report
   labels every newly run focused subset explicitly and does not replace either
   historical record.
6. UTP displayed 117 progress completions for final phone suites whose
   authoritative XML count was 115. Both XML results contain zero failures and
   errors.

## 20. Final risk inventory

Final Batch 7 surviving risk assessment:

`P0/P1/P2 = 0/0/8`

The eight surviving non-blocking P2 items are:

1. Missing, blank, or malformed JSON v1 IDs receive random UUIDs, so replay of
   such source entries is intentionally not idempotent.
2. Numeric JSON ID tokens use the stricter formal invalid/skip behavior rather
   than the permissive legacy random-ID fallback.
3. JSON v1 cannot represent all Domain metadata; export is a protocol
   projection rather than lossless Domain persistence.
4. Domain Route/Ester ownership remains the accepted ADR-015 transitional
   dependency.
5. HRT presentation still folds document and storage import failures into a
   common user-facing failure category.
6. `MahiroJsonV1ImportService` catches `RuntimeException` more broadly than the
   storage exception type it intends to classify.
7. Existing Wear replay cannot revalidate the original plan ID after an action
   has already been accepted; plan ID remains first-materialization input.
8. The adjacent instrumentation wrapper's historical 39 expectation lags the
   42 currently discovered tests and requires maintenance.

The historical API 35 transient is retained as evidence but is not counted as
a current product risk after a clean first final-gate run. The historical 7C
43/51 reporting difference is explicitly reconciled here and is not counted
as a surviving implementation risk.

## 21. Deferred work

Batch 7 introduced no implementation of the following deferred features:

- Batch 8 migration/release work
- Widget Material You or transparency controls
- Wear timeline, snooze/postpone, Tile expansion, or protocol expansion
- Custom medication
- MedicationPlan JSON
- Health Connect
- Google or cloud backup
- onboarding
- release or release-candidate work

The Batch 7C `WidgetWork` change is only the authorized Domain-to-PK adapter
cutover and is not Widget feature expansion. Any pre-existing Wear Tile code
predates Batch 7 and was not changed or expanded in this batch.

## 22. Release boundary

Room v3 remains internal and unreleasable.

No final Batch 7 tag, release APK, release candidate, real-user database
operation, migration change, or Batch 8 work is authorized by this report.

## 23. Closure decision

Batch 7 integrated and validated pending independent closure review.
