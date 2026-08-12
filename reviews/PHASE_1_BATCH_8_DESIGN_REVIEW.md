# Phase 1 Batch 8 Independent Design Review

Date: 2026-08-11
Reviewer: DeepSeek (independent read-only design review)
Worktree: D:\Evolune-batch8-design
Branch: phase1/batch8-design

## Executive summary

**Decision: APPROVE WITH P2**

- Design P0/P1/P2: **0 / 0 / 0** (design has no defect; the current release P1 is correctly captured and planned)
- Current Room v3 release blockers: **P0/P1/P2 = 0 / 1 / 4**
  - P1: converter-backed legacy preflight + production Repository readability not yet implemented/proven
  - 4 P2: private real-DB not executed; Python 3.12 not sealed; recovery runbook not sealed; v1 evidence limited (all pending evidence, not design defects)
- Current Room v3 release authorized: **NO**
- Batch 8 implementation authorized: **NO**
- **8A may begin after design sealing: YES**
- **8B: NO** (requires 8A sealing + separate authorization)
- Actual release: **FORBIDDEN**

Dual-dimension classification (task sec.40): Design findings = **0/0/0**; Current release blockers tracked by design = **0/1/4**.

## Git / baseline verdict

- Branch phase1/batch8-design; HEAD = ed87034 (= phase-1-batch-7 tag target); ancestor exit 0
- Only untracked = design doc; staging empty; diff --check pass; no migration/code/schema change, no 8A impl, no private-DB artifact/audit temp
- Design sec.2 baseline SHAs match git

## Room baseline verdict

- AppDatabase.kt:21-22 version=3/exportSchema=true; :68 addMigrations(MIGRATION_1_2, MIGRATION_2_3); NO fallbackToDestructiveMigration (grep zero)
- Independent blob SHA-256: schema2 = B8DA54ED..., schema3 = 044013C0... (match, no drift)
- Structural delta additive (metadata cols, slots table, planId/unique idx, CASCADE); timeH/timeOfDay retained as shadow

## ADR-016 compliance verdict

- ADR-016 (DECISIONS.md L162-176) verified: requires DDL -> FULL data preflight (inside SQLiteOpenHelper upgrade transaction, before any UPDATE/INSERT) -> write -> validate
- Current impl follows order (applyV3Schema -> preflightEvents/Plans -> insertSlots -> validateMigration; AppDatabaseMigrations.kt:10-16)
- KEY GAP confirmed: preflight reads only id+timeH (event) and id+timeOfDay (plan) - the "full data validation" intended by ADR-016 covers only the time subset in the implementation. Design sec.7 states this honestly.

## Current migration verdict

- AppDatabaseMigrations.kt order matches design sec.6; preflightEvents reads SELECT id, timeH (L91); plan preflight reads id/timeOfDay
- Locked transforms (timeH->exact ms, metadata defaults, timeOfDay->slot UUIDv5) match ADR-016
- Existing coverage (18+22+2 migration, 43 JVM, 23 connected) matches sec.6

## Converter/preflight gap verdict

**Risk real = YES** (independent code check):
- MedicationPlanEntityMapper.toDomainMedicationPlan (L17) / MedicationPlanAggregateEntity.toDomainMedicationPlan (L77) contain many MappingResult.Failure: InvalidPlanInvariant(intervalDays), InconsistentPlanTimes, slotsFromLegacyStorage, schedule/route/ester/days parsing (L20-45,80-109)
- DoseEventEntityMapper.toV3DomainDoseEvent similar (route/ester/extras/zone/localDate/slotId)
- Repository read turns MappingResult.Failure into CorruptAggregateException (RepositoryStorageException.kt:14,28 orThrowCorrupt)
- Conclusion: a SQLite value valid under DDL/copy/NOT NULL can still fail production converter/mapper read (unknown route/ester, malformed extras/daysOfWeek/schedule, interval<1, non-minute time). Design sec.7 P1 is real.

## Repository readability verdict

- Design makes "migration succeeds only if every row decodes via production v3 Repository read path" a locked release-level consequence (sec.7)
- sec.8/14/24 require post-migration Entity->converter->mapper->Domain real read chain, every row readable, critical fields preserved, collection/enum decoded - not just SELECT COUNT(*); sec.14 "Raw-SQL post-validation alone does not satisfy the Repository-read invariant"

## Valid/invalid DB contract verdict

- sec.9 precisely defines valid v2 (user_version/structure/every value readable under historical converter contract/ID canonical/sec.8 rules/deterministic/migrates to v3/integrity/FK/Repository full read)
- invalid/unmigratable explicit (corrupt rows invalid even if SQLite can coerce)
- NOT REPRESENTABLE labelling (NULL in NOT NULL, NaN in REAL NOT NULL fixture path) handled correctly (JVM/parser coverage, no faked device evidence)

## Strict failure verdict

- sec.10 strict: typed failure + outer transaction rollback to v2 + no drop/coerce/default/current-time/current-zone/random-fix/empty-list/destructive; no silent repair, no second DB, no fallback; sanitized logs (no raw values)

## Atomicity verdict

- sec.11 two states (complete v3 or byte/semantic v2 + user_version)
- 8B fault matrix covers preflight/write/slot/post-validation/cancellation (deterministic test-only fault seam)
- test seam not production-reachable; no commit/rollback call inside MIGRATION_2_3; no PendingResult/UI-retry/second-transaction substitute for SQLiteOpenHelper atomicity; distinguishes preflight-before-mutation vs mutation-stage rollback

## Repair separation verdict

- sec.12 tools/repair-v2/ separate offline tool; not runtime-called, not release-facing, not automatic; manifest + separate copy + verify + SHA-256
- unknown route/ester/extra, malformed converter, invalid semantics NOT auto-repairable (need independent design + explicit human source)
- migration must not catch->repair->continue; 8D requires real Python 3.12 run + immutability + failed-output deletion + privacy check + repaired copy re-verified via official migration + Repository read

## Synthetic matrix verdict

- sec.13 comprehensive: baseline/enum-all/event-time-boundary/invalid-categories/event+plan-fields/plan-time/identity/optional/scale(2000/100)/determinism/atomicity/provenance; representable adversarial vs NOT REPRESENTABLE; fixtures synthetic-only

## Historical v1 verdict

- v2 = formal compatibility baseline (MIGRATION_1_2 exists but v1 not a formal release baseline; Batch 4 introduced schema 2/3) - design uses v2 as formal baseline, reasonable
- sec.29 P2 "no trusted historical Room 1.json" covered by minimal synthetic v1->v2->v3 chain; stays documentation P2, not release P1

## Fresh-install verdict

- sec.15 fresh v3 vs upgraded v3 strictly separated (separate provenance); fresh not cited as preserved-data evidence; fresh gate covers create/reopen/CRUD/CAS/conflict/slots/JSON/PK/app flows

## Preserved-upgrade verdict

- sec.16 mandatory gate = historical build (owns v2) -> production writer fills synthetic -> close -> install-over (SAME applicationId + signing lineage) -> launch without clearing data -> verify 6 items
- Explicitly forbids uninstall/pm clear/fresh reinstall/fixture replacement as upgrade success (sec.16.7); API33+API35 mandatory; foldable additional; Wear not authority
- Records provenance/package/signing digest/version codes/device/pre-post sanitized counts+hashes/Repository full read/report paths

## Private real-DB policy verdict

- sec.17 optional + separate explicit authorization; immutable original + second backup + original SHA-256 private + disposable copy only + integrity/foreign_key/row-count/critical-field + migration + reopen + Repository full read + delete disposable + retain backup to acceptance + sanitized report only
- Not executing real-DB in design phase is NOT a design blocker (pending evidence; sec.17 establishes strict future process) - reasonable

## Privacy/evidence verdict

- sec.18 allowBackup=true but backup_rules/data_extraction_rules exclude db/files/prefs/external/device-protected; Batch 8 must verify target-API rules, not assume cloud transfer protects DB
- No real DB/UUID/medication/dose/timestamp into Git/CI/issue/report

## Python audit-tool verdict

- sec.12/29: Python 3.12 tool is an auxiliary audit utility (scan/verify/repair-copy), NOT the sole migration truth; production migration authority remains actual Room/SQLite behavior
- 8D requires real 3.12 run evidence (version/command/expected hash/output/cross-check/retention); P2 = pending evidence, not design defect

## Recovery/downgrade verdict

- sec.23 forward-only migration; no v3->v2 Room migration designed; no fallbackToDestructiveMigrationOnDowngrade (grep zero)
- Failure -> transaction rollback to v2 + bounded recovery state; no crash-loop/clear/default-destructive
- Success rollback relies on immutable backup / platform release rollback, not reinterpreting v3 as v2; uninstall/clear/rebuild/fresh-start not rollback
- 8E approves release runbook (staged rollout/halt/support/backup/retry/recovery) without promising auto-backup

## Release authorization matrix verdict

- sec.24 MANDATORY/BLOCKING: schema identity (P0), no destructive fallback/second DB (P0), migration-readability contract (P1), strict invalid rejection (P1), atomic rollback (P0/P1), synthetic matrix (P1), fresh v3 (P1), preserved upgrade (P1), Repository reopen (P1), device coverage (P1), regressions (P1), build/lint/alignment (P1), repair separation (P1), privacy/backup (P1), private real-DB policy (P2 no-exec/P1 no-policy), recovery runbook (P1), surviving risks (P1)
- Only P0=0, P1=0, all mandatory pass, implementation report, independent approval may propose release authorization
- sec.28 authorization != release (no version bump/signing/publish/migrate user DB/execute private workflow)

## Batch 7 P2 disposition verdict

- sec.25: random replay/numeric-ID/JSON projection/HRT folding/broad catch/Wear plan_id/wrapper = NOT database release-gate relevant, retained (not opportunistically fixed)
- ADR-015 Route/Ester ownership = relevant (validate persisted tokens + Repository readability, do not move enums) - reasonable
- No scope creep (Batch 8 does not fix unrelated Batch 7 P2)

## Sub-batch sequencing verdict

- sec.26: 8A (tests/spec + synthetic matrix + 8A report, NO schema/production change) -> 8B (migration primitives + AppDatabaseMigrations + full preflight + atomicity, schema byte-identical) -> 8C (fresh/preserved upgrade device matrix + release-safety surface only) -> 8D (repair qualification + private process, no runtime integration) -> 8E (release authorization)
- Dependency order sound: 8A is tests/contract only, 8B is where production migration change is allowed; each sub-batch narrow + independent review + sealed before next
- Recommended authorization: 8A first only, not 8A-8E at once

## Feature-freeze verdict

- sec.4 freezes: Widget Material You/transparency, Wear timeline/Tile/snooze, custom medication, MedicationPlan JSON, Health Connect/cloud/onboarding, PK/JSON/Domain/contract, destructive/repair/legacy deletion
- Migration validating MedicationPlan existing persistence != MedicationPlan JSON feature

## Stop-condition verdict

- sec.27 comprehensive: schema hash drift, ADR-016 order/strict/outer-transaction/repair-separation not preservable, Repository read not guaranteed, lossless not guaranteed, Domain/contract/schema/migration-version/JSON/PK/Wear/Route-Ester/SlotID change required, destructive/silent/row-loss/current-time-zone inference/second-DB/auto-repair, private data would need commit/upload/log, new ADR required, P0/P1 or mandatory gate lacks evidence
- No key stop condition missing

## Findings

### P0
None.

### P1
None (Design layer).

### P2

**F1 - Current release P1 tracked by design (not a design defect)**
- Severity: P2 (Design) / P1 (Current release blocker)
- Category: CURRENT RELEASE BLOCKER
- Source: design sec.7/29; AppDatabaseMigrations.kt:88-176 (preflight only id/timeH/timeOfDay)
- Problem: migration success does not guarantee production Repository readability (non-time fields/converter payloads not preflighted; mapper rejection surface broad; Repository read turns mapping failure into CorruptAggregateException)
- Evidence: MedicationPlanEntityMapper L17-109 / DoseEventEntityMapper / RepositoryStorageException L14,28 (independently verified)
- Impact: migrated DB could throw CorruptAggregateException on production read - CURRENT Room v3 release blocker
- Required action: 8B implements complete preflight + Repository readability gate (design already mandates)
- Blocks design sealing? NO (design correctly captures and plans it)
- Blocks Room v3 release? **YES**

**F2 - 4 pending-evidence P2 (not design defects)**
- Severity: P2
- Category: CURRENT RELEASE BLOCKER (pending evidence) / DESIGN (none)
- Source: design sec.29
- Problem: private real-DB not executed (sec.17 process built); Python 3.12 not sealed (sec.12/8D); recovery runbook not sealed (sec.23/8E); v1 evidence limited (sec.29, v2 formal baseline reasonable)
- Evidence: all explicitly planned to execute/seal in later sub-batches, not design gaps
- Required action: execute/seal in 8D/8E
- Blocks design sealing? NO; Blocks Room v3 release? YES (must satisfy/clarify before release)

## Required 8A gates

8A may do (tests/spec only, no schema/production change):
- Freeze the executable valid/invalid v2 field contract from sec.8
- Add synthetic fixtures/tests for every non-time mapper/converter failure domain + full Repository readability tests
- Mark NOT REPRESENTABLE fixtures; produce 8A report/review

8A must not: change schema, change production migration, change Domain/Repository/JSON/PK, change Slot ID.

## Required 8B gates

(description only, not authorization):
- Extend preflight so all future Repository failures are caught before UPDATE/INSERT (incl. route/ester/extras/scheduleType/days/interval converter/mapper surface)
- Preserve DDL order, outer transaction ownership, defaults, post-checks; schemas byte-identical
- 8B fault matrix (preflight/write/slot/post-validation/cancellation) + Repository-reopen connected evidence
- No mapper-validation weakening, no repair/fallback

## Independent verification

- Git: branch/HEAD (phase-1-batch-7 tag)/status/diff/--check/stash
- Schema: independent blob SHA-256 (B8DA54ED.../044013C0...) match
- Migration source: AppDatabaseMigrations.kt order + preflight read scope (id/timeH/timeOfDay) verified
- Converter/mapper audit: MedicationPlanEntityMapper (L17-109 many Failure branches), DoseEventEntityMapper, RepositoryStorageException.orThrowCorrupt - P1 gap REAL
- ADR-016 (DECISIONS.md L162-176) verified (full-preflight intent vs implementation time-subset)
- Destructive migration search: no fallbackToDestructiveMigration / OnDowngrade
- Release config: no version bump/signing/publish change proposed

## Final decision

**Batch 8 design may be sealed.**

Only Batch 8A may begin after design sealing.

The current Room v3 release P1 remains unresolved.

Batch 8B and later implementation require their own authorization sequence.

Room v3 remains internal and unreleasable.

Actual release remains forbidden.