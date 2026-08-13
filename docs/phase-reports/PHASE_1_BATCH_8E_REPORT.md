# Phase 1 Batch 8E Final Release Gate Report

## 1. Status

Phase 1 Batch 8E release gate complete pending independent review.

This is not an actual release. No final tag, push, production signing change,
GitHub Release, store upload, or distribution was performed.

## 2. Baseline

| Item | SHA / reference |
|---|---|
| Batch 8D implementation | `22b64a72c597aa38d9b76aa229851ef3914dc7c0` |
| Batch 8D review | `da558c73d81fdb6e6e577d1d6c1d6f2960884103` |
| Batch 8D tag | `phase-1-batch-8d` -> review SHA |
| Batch 8D merge | `c465204c7441d293840ddf0850fc9f8c0e499403` |
| 8E branch/worktree | `phase1/batch8e-release-authorization` / `D:\Evolune-batch8e` |

## 3. Architecture and Room state

The locked direction remains `feature -> core:data-api <- core:database`.
Batch 8E has zero production semantic diff and adds no feature, Domain or
Repository change, schema/migration semantic change, JSON/PK change, Widget or
Wear expansion, Health Connect, cloud, Hilt, SQLCipher, or second database.

Room is version 3 with `exportSchema=true`; `MIGRATION_1_2` and
`MIGRATION_2_3` are registered. No `MIGRATION_3_2`, `MIGRATION_3_4`, destructive
migration, or downgrade fallback exists. Legacy `timeH`/`timeOfDay` remain.

## 4. Migration and preserved upgrade

Strict DDL -> complete preflight -> backfill -> postcondition behavior and
outer-transaction rollback remain unchanged. Connected coverage includes
invalid storage/grammar/invariants, overflow, complete preflight, mutation and
postcondition rollback, FK/cascade/unique, ordering, duplicates, UUIDv5,
legacy-value retention, and Repository reopen.

The trusted historical v2 package and production Repository seed were upgraded
in place without uninstall/data clear:

| Target | Result | State after upgrade | Reopen |
|---|---|---|---|
| API 33 / Android 13 / `emulator-5554` | PASS | v3, 6 events, 3 plans, 5 slots, integrity `ok`, FK empty | PASS after force-stop/reboot |
| API 35 / Android 15 / `emulator-5560` | PASS | v3, 6 events, 3 plans, 5 slots, integrity `ok`, FK empty | PASS after force-stop |

Installation times remained continuous and update times advanced. Historical
and current APK hashes plus the common debug certificate are recorded in the
authorization matrix. APKs remain local validation artifacts.

## 5. Repair, recovery, and downgrade

The offline repair tool remains explicit, copy-only, and outside production.
Python 3.12.13 / SQLite 3.53.1 ran 94/94. The Batch 8D independent rerun was
94/94 on Python 3.14.6 / SQLite 3.50.4; it is not represented as reproducing
3.12.13. Shared 25-case Kotlin/Python parity had zero disagreement on API 33
and API 35. Repaired-copy migration and Repository validation passed on both.

Synthetic SQLite byte hashes can vary by runtime; semantic classification,
counts, integrity, and Repository output are the invariants.

`docs/release/ROOM_V3_RECOVERY_AND_DOWNGRADE.md` covers failure preservation,
copy-only audit/repair, unsupported direct downgrade, authentic v2 snapshot
rollback, uninstall warning, and privacy. The recovery/downgrade P2 is
technically addressed pending independent review.

## 6. Private data disposition

Private real SQLite database validation is **NOT EXECUTED**. No `REAL_DB_PATH`
was supplied or authorized and no database location was searched. Sealed design
classifies execution as optional/separately authorized, so 8E recommends this
as `ACCEPTED_RESIDUAL_P2`; only the reviewer may accept it formally.

The user supplied one repository-external private JSON v1 export. A temporary
JVM harness invoked the production codec, domain adapter, and import service
against an in-memory Repository:

- 109/109 entries accepted on first import;
- 109/109 idempotent on replay;
- zero invalid, conflict, or storage failure.

No health value, raw UUID, timestamp, weight, or JSON content was printed or
copied into the repository. The harness was deleted. This is additional JSON
compatibility evidence, not a substitute for real SQLite validation.

## 7. Historical v1

Room v2 is the formal local compatibility baseline. No trusted historical
Room `1.json` exists. The minimal authorized synthetic v1-to-v2-to-v3 chain
passes and the tool rejects v1. 8E recommends `ACCEPTED_RESIDUAL_P2` with
release wording that supported local database upgrade begins at v2. General
v1-to-v3 support is not claimed.

## 8. Final validation

| Gate | Result |
|---|---|
| 8A contract | 6/6 |
| 8B preflight | 5/5 |
| Full App JVM | 51 suites, 417/417, zero failure/error/skipped |
| PK | 5 suites, 49/49 |
| Wear JVM | 1/1 |
| Python repair/audit | 94/94 |
| API 33 connected | 78/78, zero failure/error/skipped |
| API 35 connected | 78/78, zero failure/error/skipped |
| Foldable API 37 | 21/21, zero failure/skipped |
| App KSP / assemble / androidTest compile / lint | PASS |
| Wear assemble | PASS |
| 16 KB `zipalign -P 16` | PASS |

The current app APK SHA is
`F2810408F6257CDD246E03903DC0D20E5AB1236A969665F7B0AE018AC41BFFE4`;
the androidTest APK SHA is
`083072D59BDECB0CAEA5C885C08E1CBC1027065ABEE0282A552D1140B0391290`.

## 9. Schema and static audits

| Schema | Identity | Canonical Git blob SHA-256 |
|---|---|---|
| v2 | `a8036e3f5ed6bb42d0e7289ac84039f3` | `B8DA54EDCEA0559AEFAD5E633E5ABAF3D4AEBF7C598C9DC491B8060EA845E5DA` |
| v3 | `c5f5e02cb04b048ca28fe96a74d61606` | `044013C0A911E2927DC2C79D03D41D36A6187F7133DEB419B9E5538983301E72` |

The canonical blob hash is authoritative; platform line endings can change a
worktree-file hash. Production destructive fallback scan returned zero. The
root remains MIT; Featherline GPL material remains reference-only and outside
build/runtime source. No private DB/backup/JSON/APK/keystore/manifest/generated
database belongs in the deliverable.

## 10. Findings and recommendation

- Batch 8E implementation findings: `P0/P1/P2 = 0/0/0`.
- Formal entering release blockers: `P0/P1/P2 = 0/0/3`.
- Recovery/downgrade: technically addressed pending review.
- Proposed post-review blockers: `P0/P1/P2 = 0/0/0`.
- Proposed accepted residual P2 caveats: `2` (private SQLite not executed;
  historical v1 evidence limited).
- `ready_for_independent_release_review = YES`.
- `ROOM V3 RELEASE AUTHORIZED = NO` until independent review.

Deferred post-v1 inventory remains unimplemented: Widget appearance,
expanded Wear timeline/tile, Health Connect, cloud/Google Drive backup,
calibration, custom medication design, and Tracked Date.

## 11. Decision

Phase 1 Batch 8E release gate complete pending independent review.
