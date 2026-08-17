# Phase 1 Release Authorization Matrix

Allowed states are `PASS`, `CLOSED`, `ACCEPTED_RESIDUAL_P2`, `OPEN_BLOCKING`,
and `NOT_APPLICABLE`. Residual-risk states are recommendations until independent
Batch 8E review.

| Gate | Evidence | Result | Severity | Disposition | Review status |
|---|---|---:|---:|---|---|
| Architecture | `feature -> core:data-api <- core:database` | PASS | P1 | No inversion | Pending 8E |
| Production freeze | No 8E production/schema/contract/JSON/PK/Wear change | PASS | P0 | Semantic diff zero | Pending 8E |
| Room v3 | version 3, exportSchema, `MIGRATION_2_3` registered | PASS | P0 | Locked | Pending 8E |
| Strict preflight | Complete validation before UPDATE/INSERT | PASS | P1 | 8B/JVM/connected pass | Reviewed through 8D |
| Atomic rollback | Invalid preflight/mutation/postcondition restores v2 | PASS | P0 | Connected matrix pass | Reviewed through 8D |
| Repository readability | Migrated events/plans read after reopen | PASS | P1 | API 33/35 pass | Pending 8E |
| API 33 preserved upgrade | install-over without clear; 6 events, 3 plans, 5 slots | PASS | P1 | integrity `ok`, FK empty | Pending 8E |
| API 35 preserved upgrade | install-over without clear; 6 events, 3 plans, 5 slots | PASS | P1 | integrity `ok`, FK empty | Pending 8E |
| API 33 connected | Release-relevant selection | PASS | P1 | 78/78, zero skipped | Pending 8E |
| API 35 connected | Same selection | PASS | P1 | 78/78, zero skipped | Pending 8E |
| Kotlin/Python parity | Shared 25-case corpus | PASS | P1 | Zero disagreement, both APIs | Pending 8E |
| Foldable | Existing adaptive/UI selection on API 37 | PASS | P2 | 21/21 | Pending 8E |
| 8A contract | Persisted contract | PASS | P1 | 6/6 | Reviewed |
| 8B preflight | Focused JVM | PASS | P1 | 5/5 | Reviewed |
| Full App JVM | Current integrated source | PASS | P1 | 51 suites, 417/417 | Pending 8E |
| PK | Existing PK package | PASS | P1 | 5 suites, 49/49 | Pending 8E |
| Wear | JVM plus assembly | PASS | P1 | 1/1; build pass | Pending 8E |
| Python repair | Python 3.12.13 / SQLite 3.53.1 | PASS | P1 | 94/94 | Pending 8E |
| Repair separation | Offline, explicit, copy-only | PASS | P1 | No runtime path | Reviewed through 8D |
| Repaired-copy chain | repaired v2 -> official migration -> Repository | PASS | P1 | API 33/35 pass | Pending 8E |
| Recovery/downgrade | Failure, repair, downgrade, privacy runbook | PASS | P1 | Technically addressed | Pending 8E |
| Destructive fallback | Production scan | PASS | P0 | Zero matches | Pending 8E |
| Schema 2 | identity `a8036e3f5ed6bb42d0e7289ac84039f3` | PASS | P0 | SHA locked | Pending 8E |
| Schema 3 | identity `c5f5e02cb04b048ca28fe96a74d61606` | PASS | P0 | SHA locked | Pending 8E |
| Legacy columns | `timeH` and `timeOfDay` retained | PASS | P1 | Keep through formal 1.0 cycle | Pending 8E |
| JSON v1 | Production import plus private export validation | PASS | P1 | 109 accepted; replay 109 idempotent | Pending 8E |
| Build gates | KSP, app/wear assembly, androidTest compile, lint | PASS | P1 | All pass | Pending 8E |
| 16 KB alignment | `zipalign -c -P 16 -v 4` | PASS | P1 | Verification successful | Pending 8E |
| Private real SQLite DB | No authorized `REAL_DB_PATH` | ACCEPTED_RESIDUAL_P2 | P2 | Recommended non-blocking; optional by design | Pending acceptance |
| Historical Room v1 | No trusted `1.json`; v2 formal baseline | ACCEPTED_RESIDUAL_P2 | P2 | Do not claim general v1 support | Pending acceptance |
| License/source | MIT with reviewed provenance boundaries | PASS | P0 | No unlicensed source integrated | Pending 8E |
| Privacy/artifacts | No DB/JSON/APK/keystore/health data in deliverables | PASS | P0 | Three docs only | Pending 8E |
| Tracked Date | P2/non-MVP, absent from Domain/schema | NOT_APPLICABLE | P2 | Deferred | Existing decision |
| Actual release | Tag/push/sign/release/upload | NOT_APPLICABLE | P0 | Forbidden in 8E | Later authorization |

## Locked hashes and provenance

- Schema 2 canonical SHA-256:
  `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA`.
- Schema 3 canonical SHA-256:
  `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72`.
- Historical v2 commit/tag:
  `16d8dbf1c7d1ed359b2e8c4e0857759b2dd12c81` /
  `phase-1-batch-4a1-design-v1`.
- Historical app/test APK SHA-256: `6075281B...AB2CF` /
  `6CB3F68B...FED3D`.
- Current app/test APK SHA-256: `F2810408...BFFE4` /
  `083072D5...91290`.
- Shared debug certificate SHA-256: `66DBE8E4...61E9`.
- API 33 first install remained `2026-08-13 10:44:37`; last update advanced
  to `2026-08-13 10:51:23`.
- API 35 first install remained `2026-08-13 11:03:02`; last update advanced
  to `2026-08-13 11:18:38`.

## Residual-risk recommendation

| Risk | Severity | Why recommended accepted | Mitigation |
|---|---:|---|---|
| Private real SQLite DB not executed | P2 | Optional policy sealed; adversarial, repaired-copy, and two preserved upgrades pass; no authorized path | Use private copy-only workflow only with explicit path/authorization |
| Historical v1 evidence limited | P2 | v2 formal baseline; minimal synthetic chain passes; tool rejects v1 | Publish v2 as supported local upgrade baseline; stop if formal v1 release evidence appears |

- Batch 8E implementation findings: `P0/P1/P2 = 0/0/0`.
- Formal entering blockers: `P0/P1/P2 = 0/0/3`.
- Recovery/downgrade P2: technically addressed pending review.
- Proposed post-review blockers: `P0/P1/P2 = 0/0/0`.
- Proposed accepted residual P2 caveats: `2`.
- `ready_for_independent_release_review = YES`.
- `ROOM V3 RELEASE AUTHORIZED = NO` pending independent review.
