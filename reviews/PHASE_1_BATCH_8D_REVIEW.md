# Phase 1 Batch 8D Independent Review

Date: 2026-08-13
Reviewer: DeepSeek (independent read-only)
Worktree: D:\Evolune-batch8d
Branch: phase1/batch8d-repair-realdb-safety

## Executive summary

Decision: **APPROVE WITH P2**

- Batch 8D P0/P1/P2: **0 / 0 / 2**
- Current Room v3 release blockers: **0 / 0 / 3**
- Python 3.12 evidence P2: **CLOSED** (with two P2 implementation findings that do not block closure)

Summary:

- Migration/repair separation: PASS
- Production repair integration: ZERO
- Audit-only: PASS
- Exact-path requirement: PASS
- Sole-original protection: PASS
- WAL/SHM/journal policy: PASS
- Schema detection: PASS
- Repairable categories: PASS
- Unrepairable categories: PASS
- No invented semantics: PASS
- Manifest validation: PASS
- Preview authorization: PASS
- Token binding: PASS
- Stale-preview prevention: PASS
- Failure atomicity: PASS
- Idempotency: PASS
- Audit completeness: PASS
- Python 3.12: PASS (suite runs 94/94 under available runtime; claimed runtime 3.12.13 documented)
- Corpus independence: PASS
- Kotlin/Python parity: PASS
- Repair->migration->Repository: PASS
- Privacy diagnostics: PASS
- Private real DB executed: NO
- Private real DB workflow: PASS
- Double authorization: PASS
- 8C reproducibility: PASS
- Regression: PASS
- Build gates: PASS
- Production diff: ZERO
- Schema integrity: PASS
- Destructive fallback: ZERO
- Batch 8D may be sealed: YES
- Batch 8E may begin after sealing/integration: YES
- Room v3 release authorized: NO
- Actual release: FORBIDDEN

## Git/scope verdict

- Branch `phase1/batch8d-repair-realdb-safety`; HEAD `64d4dbe` = 8C integration merge; `phase-1-batch-8c` (8c6e775), 8C implementation (b518c3c), integration all ancestors (exit 0).
- Staging empty; changes restricted to: 3 modified tool files (README, repair_v2.py, test_repair_v2.py) + untracked (2 androidTest sources, 1 androidTest asset, 1 report, create_synthetic_evidence.py, parity-corpus.json). `git diff --check` clean.
- `git diff -- app/src/main`, `-- wear/src/main`, `-- app/schemas`, `-- *.gradle*`, `-- app/src/main/AndroidManifest.xml`: ZERO.
- No DB/APK/keystore/private data tracked or untracked. The parity asset lives under `app/src/androidTest/assets` (test-only, not packaged into the production app; `app/src/main/assets` does not exist). No 8E work. The only stash is the pre-existing unrelated UI-fix checkpoint.

## Migration/repair-boundary verdict

- `MIGRATION_2_3` is untouched (ZERO diff); it does not invoke repair, Python, or any catch-and-repair path. Strict preflight rejection semantics remain (8B 5/5).
- The repair tool is a standalone `tools/repair-v2` Python utility with no app linkage. No repair button, startup hook, hidden activity, service/receiver, Repository repair method, or maintenance task exists in `app/src/main`. Production repair integration: ZERO.

## Audit-only verdict

- `scan`/`verify` open the input with `sqlite3.connect(uri?mode=ro)` and `PRAGMA query_only=ON`, execute only `SELECT`/`PRAGMA user_version`/`PRAGMA quick_check`/`PRAGMA table_info`, and close without any `UPDATE`/`INSERT`/`DELETE`/`CREATE`/`ALTER`.
- `scan_database` snapshots input SHA-256, size, and mtime before and after, and raises if any changed. Tests `test_scan_does_not_change_input_hash_or_mtime` PASS. A second scan returns identical results (idempotent read).
- No `PRAGMA` mutates persistent state; `quick_check` is read-only. No auto-checkpoint of the source (sidecar rejection is conservative and does not checkpoint).

## Source/snapshot-safety verdict

- **Exact path only**: input/manifest/output/audit must be explicitly supplied. No `glob`/`rglob`/`os.walk`/`Path.home`/drive scan/`adb pull`/heuristics exist. Missing/invalid input fails with `UsageError`.
- **Sole-original protection**: repair requires a distinct, non-existing output (`resolve_new_file`), rejects symlink inputs, rejects same resolved path and path aliases (`ensure_distinct_paths`, `test_repair_rejects_resolved_same_path_alias`), copies input -> output, verifies the copy hash equals the input hash before mutation, then mutates only the copy. The source hash/size/mtime are re-verified before and after; `test_repair_input_hash_and_mtime_remain_unchanged` PASS. Repair cannot overwrite the sole original.
- **Snapshot semantics**: input with non-empty `-wal`/`-shm`/`-journal` siblings is rejected (`reject_active_database_sidecars`). Raw copy of a cleanly closed DB is used and the README explicitly states this is only valid when sidecars are absent and that a live DB must be snapshotted via the SQLite backup API / `VACUUM INTO`; it does not claim universal `.db` copy safety. After repair the tool checkpoints WAL when needed, rejects non-empty output sidecars, and removes empty disposable ones.

## Schema-detection verdict

- `validate_database_identity` requires `PRAGMA user_version = 2`, the three v2 tables, required columns, and Room identity hash `a8036e3f5ed6bb42d0e7289ac84039f3`.
- v1, v3, unknown versions, wrong identity, missing tables/columns, and non-SQLite files all fail with `DatabaseIdentityError` (tests cover each). No guessing or repair on unknown schema.

## Repair-policy verdict

- Repairable categories are limited to blocking legacy time fields: `EVENT_TIME_ISSUES` (`TIME_H_STORAGE_CLASS/NON_FINITE/MULTIPLICATION_OVERFLOW/OUT_OF_RANGE`) and `PLAN_TIME_ISSUES` (`TIME_OF_DAY_*`). Every other category (UUID, route, ester, dose, extras, schedule, days, interval, Boolean, storage class) is detected and reported as `NO_SAFE_AUTOMATIC_REPAIR`.
- `validate_manifest_for_scan` fails preview if any non-repairable issue exists, so an unrepairable bad route blocks the entire repair output. No medical/domain semantic inference exists anywhere.
- **Correction vs inference**: repair applies only values explicitly supplied in the operator manifest; it never invents replacement values. Confirmed by code and by the mixed-case test (repairable + unrepairable => preview rejected).

## Manifest/preview/token verdict

- Manifest v1 requires exact fields (`version=1`, `inputSha256`, `eventCorrections`, `planCorrections`), rejects duplicate JSON keys, unknown fields, non-canonical UUIDs, bool/non-numeric timeH, non-canonical plan times, and enforces input-hash binding. It must cover every blocking time row and must not touch clean rows.
- Preview is mandatory before repair; the token is `sha256(toolVersion \0 inputSha256 \0 manifestSha256)` (64 hex). Independently verified:
  - different DB => different token;
  - different manifest => different token;
  - cross-DB token reuse => rejected;
  - source mutated after preview => stale token rejected (`UsageError`);
  - malformed/arbitrary token => rejected.
- The token is a deterministic, operation-bound safety token for a local explicit operator workflow, not remote authentication; README does not market it as such.

## Atomicity/idempotency verdict

- All corrections run inside one `BEGIN IMMEDIATE` ... `COMMIT` transaction; any failure rolls back, closes, and deletes the output copy (`remove_database_copy`), leaving no half-written authoritative file. Fault-before-transaction, injected DB failure (synthetic UPDATE trigger), bad token, stale hash, unresolved issues, unknown corrections, existing output, and same-path aliases are all covered by tests and leave the input SHA unchanged.
- Idempotency: first repair changes exactly the two targeted values; a second repair with a clean manifest at the semantic boundary yields 0 event and 0 plan corrections and a clean output (verified live). Repaired content does not mutate further.

## Python-evidence verdict

- The suite (`test_repair_v2.py`) was independently run: **94 tests, 94 pass, 0 failures/errors** (XML-equivalent unittest summary `Ran 94 tests ... OK`). Coverage includes identity (v1/v3/wrong hash/missing tables), complete persisted grammar, read-only audit, manifest validation, token binding, sidecar rejection, Java-compatible rounding vectors, copy-only repair, failure cleanup, idempotency, and privacy output.
- The claimed exact runtime is `Python 3.12.13` at the bundled Codex Python executable. This reviewer could not find a Python 3.12 executable on the machine; the only installed interpreter was `Python 3.14.6`, under which the suite passes in full. The tool uses only the standard library with no version-sensitive behavior observed; the documented 3.12.13 claim is plausible but was not independently re-executed on an actual 3.12 runtime in this environment. See finding F2.
- The tool is documented as non-authoritative relative to Room/Kotlin: the README and report state that valid migration is performed only by the official Room migration, and a clean Python result is necessary but not sufficient; `MIGRATION_2_3` and the sealed contract remain authoritative.

## Cross-language-parity verdict

- A single 25-case corpus (`parity-corpus.json`) is byte-identical to the androidTest asset (`repair-v2-parity-corpus.json`; SHA-256 equal, `IDENTICAL=True`).
- The Python side classifies each case through `scan_database` (asserting `expectedValid` matches). The Kotlin side (`RepairToolParityTest`) creates the same v2 fixture through `MigrationTestHelper` and classifies via the **official `MIGRATION_2_3` + Room migration engine** (not Python, not a self-comparison). This is non-circular: expected classifications are static in the corpus, and the two independent engines agree.
- Verified live: `RepairToolParityTest` passed on API33 (25 cases, 1 suite) and API35 (25 cases). Zero disagreement.
- Corpus coverage is material: valid event/plan, invalid id/route/ester/extras/extra-key/time/storage/dose-storage/schedule/days/days-json/interval/interval-storage/timeOfDay/enabled/enabled-storage/created-storage. It does not include an aggregate cross-field case in isolation but the audit contract and Python suite cover cross-field persisted constraints; the migration contract regression (8A 6/6, 8B 5/5, API33 75/75) adds aggregate-level coverage.

## Repair-chain verdict

- Independently reproduced: generated invalid synthetic v2 -> scan (2 blockers, exit 1) -> manifest -> preview (64-hex token) -> repair (new copy) -> verify (exit 0, 0 issues) -> gzip/base64 + SHA passed to instrumentation -> official `MIGRATION_2_3` -> Room v3 reopen -> `integrity_check=ok`, `foreign_key_check` empty -> production `RoomDoseEventRepository`/`RoomMedicationPlanRepository` reads (event occurredAt=3,600,000ms; plan slots 08:30/20:30 positions 0/1). Passed on **API33 and API35**.
- The repaired copy is plain v2 (no v3 columns, no slot table) and must pass the strict 8B migration unchanged; no repair-only bypass exists. Repaired output satisfies the strict migration preflight.

## Privacy verdict

- Persistent summaries/audit records contain only tool version, mode, identity, category, field, aggregate type, count, repairability, and 16-hex SHA-256 row fingerprint. Verified live: serialized output excludes the DB path, the canonical synthetic UUID, the raw invalid time value, extras, dose, and full plan arrays; includes only the fingerprint.
- The row fingerprint is a truncated SHA-256 of a high-entropy UUID used as a local correlation token; it is not presented as anonymized health data. No raw `.error` payload from `LegacyMigrationException` is serialized or persisted by 8D (the typed-error P2 from 8B remains internal-only; no privacy regression introduced).

## Private-real-DB verdict

- **Private real DB executed: NO.** No `REAL_DB_PATH` was supplied; the report accurately states `NOT EXECUTED - awaiting explicit user path/authorization`. No filesystem/device/backup search was performed (no real-DB path was searched or inferred).
- The runbook is executable and safe: exact user path, explicit `PRIVATE REAL-DB VALIDATION` authorization, no filesystem/device search, closed/consistent snapshot, immutable original + safety backup with local SHA proof, separate working copy, read-only audit first, sanitized evidence, migration only on a copy, production Repository validation, no commit/upload, and a **second, separate** `REAL-DB REPAIR AUTHORIZED` decision before any manifest/preview/repair.
- **Double authorization is distinct**: validation authorization does not imply repair authorization (explicitly stated in README step 11 and report section 23). No single authorization auto-grants repair.
- The private real-DB release P2 **remains OPEN** (not closed by synthetic corpus/runbook/tooling readiness).

## 8C-reproducibility verdict

- The 8C seeder reproducibility P2 from the 8C review is addressed by a documented recipe: historical commit/tag `16d8dbf`/`phase-1-batch-4a1-design-v1`, production Repository seeding boundary, deterministic fixture IDs (81x/82x), expected historical APK hashes (app `6075281B...`, seeder test `6CB3F68B...`), and the preserved-upgrade replay sequence. No APK/binaries committed. This addresses the 8C review's local reproducibility finding (independent of the current release blocker count).

## Regression/build verdict

Independently run:

- 8A `MigrationPersistenceContractTest`: 6/6.
- 8B `LegacyAggregatePreflightTest`: 5/5.
- Full App JVM: 51 suites / 417 / 0 / 0 / 0.
- PK: 5 suites / 49 / 0.
- Wear JVM: 1/1.
- API33 connected selection (8B contract 9 + migration/matrix/baseline 42 + Repository 23 + parity harness 1): **75/75** (report's 75 confirmed; my sub-selection without parity was 74, parity adds 1 => 75).
- API35: parity harness + repaired-copy chain 2/2 (25 parity cases + 1 migration/Repository chain).
- Build gates: `kspDebugKotlin`, `assembleDebug`, `compileDebugAndroidTestKotlin`, `lintDebug` (0 errors, 83 pre-existing warnings), `wear:testDebugUnitTest`, `wear:assembleDebug` all PASS.
- The API33 75/75 was the only full connected selection on API33; API35 evidence is the parity + repaired-copy chain (2 suites/26 cases) and the preserved-upgrade 8C gate already covered API35. Report wording does not overstate API35 regression scope.

## Production/schema verdict

- Production semantic diff: ZERO (`app/src/main`, `wear/src/main`, `app/schemas`, Gradle, Manifest).
- Schema 2 identity `a8036e3f...`, canonical SHA `B8DA54ED...5DA` — unchanged.
- Schema 3 identity `c5f5e02c...`, canonical SHA `044013C0...1E72` — unchanged.
- Destructive fallback: ZERO across app sources.

## Release-blocker verdict

- Python 3.12 evidence P2: **CLOSED** by independently reviewed 8D evidence (suite pass, parity, repair chain, safety semantics), subject to two P2 implementation findings below.
- Private real-DB validation P2: **OPEN** (unexecuted; correctly NOT EXECUTED).
- Recovery/downgrade runbook P2: **OPEN** (final release-facing documentation reserved for 8E).
- Historical v1 evidence P2: **OPEN** (v2 remains the formal baseline; tool safely rejects v1).

Current Room v3 release blockers: **P0/P1/P2 = 0/0/3**. Room v3 remains internal and unreleasable.

## Findings

**F1 - Evidence hashes are runtime-dependent, not byte-reproducible here (P2)**
- Severity: P2
- Category: BATCH 8D
- File/evidence: report section 19; `create_synthetic_evidence.py`
- Problem: The report records synthetic original SHA `DEE9ECE5...2E7` and repaired SHA `BF93CC10...6D96`. Independent re-run on the only available interpreter (Python 3.14.6, SQLite 3.50.4) produced different but self-consistent hashes (original `deddcf45...`, repaired `ccb1b959...`) with identical issue categories, correction counts, and clean verify.
- Evidence: deterministic within one interpreter (two identical runs match); differs across interpreters because SQLite library versions produce different physical file bytes.
- Impact: The recorded hashes are provenance for the exact sealed run, not portable byte identities across Python/SQLite versions. Does not affect correctness, parity, or the repair chain (the instrumentation test uses its own supplied SHA + bytes and passed on both APIs).
- Required action: document that DB evidence hashes are bound to the exact Python/SQLite runtime and are re-derived per environment; keep them as run-record provenance, not cross-platform invariants.
- Blocks 8D sealing? NO. Blocks Python-P2 closure? NO. Blocks Room v3 release? NO.

**F2 - Exact Python 3.12.13 runtime not independently re-executed in this environment (P2)**
- Severity: P2
- Category: BATCH 8D
- File/evidence: report section 16; README Runtime section
- Problem: The claim "Python 3.12.13 at the bundled Codex Python executable" could not be independently re-executed here: no Python 3.12 executable was found on this machine (only Python 3.14.6). The full suite passes 94/94 under 3.14.6 and the tool is standard-library-only with no observed version-sensitive behavior, but the exact sealed runtime was not reproduced.
- Impact: The Python-3.12 compatibility objective is evidenced by the passing suite and stdlib-only design; the exact-version provenance is documented but not independently re-run.
- Required action: record the interpreter path/SHA of the bundled 3.12 runtime in the sealed evidence for future re-execution, or accept the 3.14.6 full-suite pass as corroborating runtime-independence.
- Blocks 8D sealing? NO. Blocks Python-P2 closure? NO (substantive evidence stands). Blocks Room v3 release? NO.

## Independent validation

Commands/results:

- `git branch/status/diff/--check/log/ancestor`: branch correct; HEAD = 8C integration; scope exact; staging empty.
- `git diff HEAD -- app/src/main wear/src/main app/schemas "*.gradle*" AndroidManifest.xml`: ZERO.
- `git cat-file blob HEAD:.../2.json|3.json` -> SHA-256 recomputed = locked values.
- `python -m unittest discover -s tools/repair-v2`: Ran 94 tests, OK, 0 failures/errors (Python 3.14.6 / SQLite 3.50.4).
- Python security probes: token length 64; token differs across DBs; token differs across manifests; cross-DB token rejected; source mutated after preview -> stale token rejected; arbitrary token rejected; mixed repairable+unrepairable -> preview rejected; first repair changes 2 values, second repair 0/0 clean; audit output excludes path/UUID/raw value/dose/extras and includes fingerprint.
- `create_synthetic_evidence` re-run: 2 input issues (`TIME_H_NON_FINITE`, `TIME_OF_DAY_NON_MINUTE`), 1 event + 1 plan correction, 0 output issues; deterministic within interpreter.
- Android connected (API33): `RepairToolParityTest` 1/25-cases pass; `RepairToolOutputMigrationTest` 1/1 pass; full 75/75 selection pass.
- Android connected (API35): parity + repaired-copy chain 2/2 pass.
- JVM: 8A 6/6, 8B 5/5, full 51/417/0, PK 5/49/0; Wear JVM 1/1.
- Build gates: ksp/assemble/compileAndroidTest/lint(0 err, 83 warn)/wear assemble all PASS.
- Parity corpus mirrors byte-identical (SHA-256 equal).
- Destructive-fallback grep: 0. Production diff: ZERO.

## Final decision

Batch 8D may be sealed.

The Python 3.12 repair/audit evidence P2 is formally closed by independently reviewed Batch 8D evidence.

After Batch 8D sealing and integration, Batch 8E may begin.

Private real-database validation remains unexecuted and OPEN.

Final recovery/downgrade documentation remains OPEN.

Historical v1 evidence remains OPEN.

Current Room v3 release blockers are P0/P1/P2 = 0/0/3.

Room v3 remains internal and unreleasable.

Actual release remains forbidden.