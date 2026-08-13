# Phase 1 Batch 8C Independent Review

Date: 2026-08-13
Reviewer: DeepSeek (independent read-only)
Worktree: D:\Evolune-batch8c
Branch: phase1/batch8c-preserved-upgrade

## Executive summary

Decision: **APPROVE WITH P2**

- Batch 8C P0/P1/P2: **0 / 0 / 2**
- Current Room v3 release blockers: **0 / 0 / 4**

Summary:

- Authentic v2 baseline: PASS
- Schema2 provenance: PASS
- Old APK provenance: PASS
- New APK provenance: PASS
- applicationId continuity: PASS
- signing continuity: PASS
- package replacement semantics: PASS
- no uninstall/clear after seed: PASS
- API33 preserved upgrade: PASS
- API33 Repository readability: PASS
- API33 reopen: PASS
- API35 preserved upgrade: PASS
- API35 Repository readability: PASS
- API35 reopen: PASS
- Slot migration: PASS
- Fresh v3 control: PASS
- Foldable: PASS (21/21)
- Wear: PASS (JVM 1/1 + assemble)
- 8A regression: PASS (6/6)
- 8B regression: PASS (5/5)
- Full JVM: PASS (51 suites / 417)
- PK: PASS (5 suites / 49)
- Build gates: PASS
- Production semantic diff: ZERO
- Schema integrity: PASS
- Destructive fallback: ZERO
- Synthetic/privacy boundary: PASS
- Batch 8C may be sealed: YES
- Batch 8D may begin after sealing/integration: YES
- Room v3 release authorized: NO
- Actual release: FORBIDDEN

## Git/scope verdict

- Branch `phase1/batch8c-preserved-upgrade`; HEAD `820ea35` = Batch 8B integration merge; `phase-1-batch-8b` (6d3e68c), 8B implementation (d994d55), and integration are all ancestors (exit 0).
- Staging empty; working tree holds exactly two untracked files: `Batch8CPreservedUpgradeTest.kt` and the 8C report. `git diff --check` clean.
- No `app/src/main`, `wear/src/main`, `app/schemas`, Gradle, Manifest, or migration diff. No APK/DB/keystore/log artifact is tracked or untracked in the repo. No 8D work. The only stash is a pre-existing unrelated UI-fix checkpoint.

## V2-baseline verdict

- **V2 baseline valid: YES.** Commit `16d8dbf1c7d1ed359b2e8c4e0857759b2dd12c81` = tag `phase-1-batch-4a1-design-v1` (verified by `git rev-parse`, `git cat-file -t commit`, tag resolve, ancestor-of-HEAD). This is the direct parent of the commit that introduced Room v3.
- Independent inspection of that commit's `AppDatabase.kt`: `@Database(version = 2)`, entities `DoseEventEntity` + `MedicationPlanEntity` only, `exportSchema=true`, no v3 columns/table, no `scheduled_dose_slots`.
- Schema 2 JSON at that commit: identity `a8036e3f5ed6bb42d0e7289ac84039f3`, canonical git-blob SHA-256 `B8DA54ED...5DA` — independently recomputed and matching.
- applicationId at baseline = `io.github.yuninggu.evolune` (+ `.debug` suffix), versionCode `10060` — same as current.
- It is a genuine v2-producing buildable commit, not a current v3 APK with tampered user_version.

## APK-provenance verdict

All four APK SHA-256 values reported were independently verified against the actual artifacts present on disk:

| Artifact | Reported SHA-256 | Independently verified | Match |
|---|---|---|---|
| Historical v2 app APK | `6075281B...AB2CF` | `6075281B...AB2CF` | YES |
| Historical v2 seeder test APK | `6CB3F68B...ED3D` | `6CB3F68B...ED3D` | YES |
| Current v3 app APK | `37382D14...FEA2` | `37382D14...FEA2` | YES |
| Current v3 test APK | `C966BD06...F53B` | `C966BD06...F53B` | YES |

- Old APK build type: debug; applicationId `io.github.yuninggu.evolune.debug`; versionCode `10060`; versionName `phase-1-batch-4a1-design-v1-debug`; Room v2; minSdk 31/target 36 (verified via aapt2 badging and apksigner).
- New APK: debug; applicationId `io.github.yuninggu.evolune.debug`; versionCode `10060`; versionName `phase-1-batch-8b-1-g820ea35-debug`; Room v3.

## Package/signing verdict

- Old and new application IDs identical: `io.github.yuninggu.evolune.debug`.
- Signing certificate SHA-256 identical for both APKs and both test APKs: `66dbe8e4de9acc084719d1ce735e3868a9ce3ebc88b0529c706bbc6beb9561e9` (Android debug cert, verified via `apksigner --print-certs` on all four artifacts). No keystore/private key exposed.
- Package replacement empirically confirmed on both phones: `dumpsys package` showed `firstInstallTime` unchanged while `lastUpdateTime` advanced after `adb install -r`. This is package replacement, not uninstall/reinstall.

## Seed-provenance verdict

- Seeding ran through the **historical v2 production Repository write path** (classification A/B: instrumentation using the v2 production AppDatabase + production `DoseEventRepository`/`MedicationPlanRepository`). Confirmed by decompiling the historical v2 test APK: `Batch8CPreservedUpgradeSeedTest.productionRepositoriesCreatePersistentV2Fixture` builds `AppDatabase` (v2), obtains `DoseEventRepository` + `MedicationPlanRepository`, and inserts the deterministic fixture through them.
- The seeder source file was a temporary artifact outside the 8C branch (removed after use); the compiled seeder remains reproducible from the v2 worktree `D:\Evolune-batch8c-v2`. Report wording ("historical production v2 Repository write paths", "temporary v2 seeding support remains outside the Batch 8C branch") is accurate.
- The seeding is deterministic synthetic data only; no real user database.

## API33 preserved-upgrade verdict

Independently reproduced the complete chain on emulator-5554 (SDK 33, sdk_gphone64_x86_64 phone AVD, boot_completed=1):

1. Old v2 APK installed (`adb install -r`), package owns private data.
2. Seeder instrumented through v2 production Repositories: **OK (1 test)**.
3. Pre-upgrade: `user_version=2`, schema-2 identity `a8036e3f...`, **6 events / 3 plans**, deterministic IDs `81000000-...001..006` and `82000000-...001..003`.
4. Old process force-stopped (no data clear).
5. Current v3 app + test APKs installed in place; `firstInstallTime` unchanged, `lastUpdateTime` advanced.
6. Launcher start → live process `io.github.yuninggu.evolune.debug` (pid observed).
7. Post-migration: `user_version=3`, schema-3 identity `c5f5e02c...`, **6 events / 3 plans / 5 slots**, `integrity_check=ok`, `foreign_key_check` empty.
8. Current harness `Batch8CPreservedUpgradeTest` (production Room Repositories): **OK (2 tests)**.
9. Force-stop → relaunch → `user_version=3`, 6/3/5 preserved, harness Repository test again **OK (1 test)**.

## API35 preserved-upgrade verdict

Same full chain independently reproduced on emulator-5560 (SDK 35, sdk_gphone64_x86_64 phone AVD, boot_completed=1):

1. Old v2 APK + seeder test APK installed.
2. Seeder: **OK (1 test)**.
3. Pre-upgrade: `user_version=2`, schema-2 identity, 6 events / 3 plans.
4. In-place install of current v3; `firstInstallTime` unchanged, `lastUpdateTime` advanced.
5. Launcher start → live process.
6. Post-migration: `user_version=3`, schema-3 identity, 6/3/5, integrity ok, FK empty.
7. Harness **OK (2 tests)**.
8. Force-stop → relaunch → 6/3/5 preserved; counts reverified.

This is a real package-replacement preserved-upgrade on both phones, not merely migration connected tests.

## Data-preservation verdict

Post-upgrade, all 6 events and 3 plans retained their deterministic IDs (`81000000-...`, `82000000-...`), route/ester/dose/extras/legacy-time semantics, plan schedule fields (DAILY/WEEKLY/CUSTOM, day sets, interval, enabled state, createdAt), and the harness asserts full Domain-content equality through production Repositories (`getById` full object equality + `observeAll` ID sets). No dropped or duplicated row (counts 6/3/5 exactly). The migration proved transition of the existing v2 DB to v3 (identity hash + user_version + preserved records) rather than creation of an empty v3 DB.

## Slot-migration verdict

5 derived slots verified on both phones, exactly matching the sealed mapping:

| Plan | Times | Slots |
|---|---|---|
| 82000000-...001 (DAILY) | 08:00, 20:30 | 08:00 pos0, 20:30 pos1 |
| 82000000-...002 (WEEKLY) | 09:15 | 09:15 pos0 |
| 82000000-...003 (CUSTOM, duplicate) | 06:45, 06:45 | 06:45 pos0, 06:45 pos1 |

Count, plan ownership, positions, canonical times, duplicate preservation, and deterministic UUIDv5 slot identity all conform. The 5-slot result is structural, not coincidental.

## Repository-readability verdict

Post-upgrade reads used current production `RoomDoseEventRepository` / `RoomMedicationPlanRepository` via the current production `AppDatabase` singleton path (`AppDatabase.getDatabase`), exercising Room/DAO/Entity/converters/mapper/Repository/Domain. Every expected aggregate was read (full Domain equality, not raw Cursor/SQL/DAO/mapper-only). This is the final evidence layer of the preserved chain.

## Force-stop/reopen verdict

On both phones: force-stop (no data clear) → relaunch through launcher → `user_version=3`, counts 6/3/5 retained, and the preserved Repository-read test passed again. Persistence is proven at the process level, not transient state.

## Fresh-control verdict

`freshV3ControlSupportsRepositoryPersistence` (1/1, part of the current harness) creates a separate fresh v3 DB, inserts/reads an event and plan through production Repositories, and reports `user_version=3`. Classified strictly as fresh-install control; it does not strengthen preserved-upgrade evidence by itself. Report classification matches.

## Foldable verdict

emulator-5558 (`sdk_gphone16k_x86_64`, SDK 37, Pixel_10_Pro_Fold AVD): `FoldableNavigationLayoutTest` + `MedicationPlansScreenTest` + `MedicationRecordsScreenTest` = **21/21 pass, 0 failures, 0 skipped** (independently rerun; note the test class FQCN is `io.github.yuninggu.evolune.ui.screens.FoldableNavigationLayoutTest`). No UI code modified. Foldable is regression acceptance only; design does not require preserved package upgrade on foldable.

## Wear verdict

emulator-5556 is a Wear OS AVD and was not used as phone evidence. `:wear:testDebugUnitTest` = 1 suite / 1 test PASS; `:wear:assembleDebug` PASS. No Wear protocol/feature change (no timeline, Tile, snooze, or protocol expansion in 8C).

## Regression/build verdict

- 8A `MigrationPersistenceContractTest`: 6/6 PASS (XML) — sealed contract unchanged.
- 8B `LegacyAggregatePreflightTest`: 5/5 PASS (XML).
- Full App JVM: 51 suites / 417 tests / 0 failures / 0 errors / 0 skipped (XML).
- PK: 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped (XML).
- Connected API33 selection (8C harness 2 + 8B contract 9 + migration/matrix/baseline 42 + Repository 23) = **76/76** (XML).
- Connected API35 selection (same 76) = **76/76** (XML).
- ksp/assembleDebug/compileDebugAndroidTestKotlin/lintDebug PASS; lint 0 errors, 83 pre-existing warnings.
- The 76/76 are correctly accounted: only 2 of 76 are preserved/fresh harness cases; the remainder are migration contract, existing migration/matrix/baseline, and Repository regressions. Report accounting is accurate.

## Production/schema verdict

- Production semantic diff: **ZERO** (app/src/main, wear/src/main, app/schemas, Gradle, Manifest).
- Schema 2: identity `a8036e3f...`, canonical SHA-256 `B8DA54ED...5DA` — unchanged.
- Schema 3: identity `c5f5e02c...`, canonical SHA-256 `044013C0...1E72` — unchanged.
- Destructive fallback: **ZERO** (`fallbackToDestructiveMigration*` absent across app sources).
- Only implementation artifact is the test-only harness.

## Privacy verdict

All data is deterministic synthetic fixtures (fixed UUIDs `81...`/`82...`, synthetic route/ester/extras, fixed timestamps). No real health database, medication history, backup, or user device data read or copied. APK hashes and cert fingerprints are safe; no keystore or private key committed (build keystore copied temporarily from `D:\Evolune` for build, removed after validation; source keystore untouched). 8C performs no private real-DB validation.

## Release-blocker verdict

The four existing release P2 remain independently reassessed and still valid at P2 severity (8C does not close them):

1. Private real-database validation not executed (optional, separately authorized) — still P2.
2. Python 3.12 repair/audit evidence not sealed — still P2.
3. Recovery/downgrade runbook not sealed — still P2.
4. Historical v1 evidence limited; v2 is the formal baseline — still P2.

Current Room v3 release blockers: **P0/P1/P2 = 0/0/4**. Room v3 remains internal and unreleasable.

## Findings

**F1 - Temporary v2 seeding support is outside the tracked branch (P2)**
- Severity: P2
- Category: BATCH 8C
- File/evidence: `D:\Evolune-batch8c-v2` (detached worktree at 16d8dbf); `Batch8CPreservedUpgradeSeedTest` present only in the compiled historical test APK (verified via dexdump), source file removed from the v2 worktree; report section 17 discloses this.
- Problem: The seed path is not version-controlled; reproducibility depends on the compiled test APK and the detached worktree. If either is lost, the seeding step cannot be re-run verbatim.
- Evidence: git status of v2 worktree is clean (no seeder source); dexdump shows the seeder class in the built APK.
- Impact: Non-blocking for the evidence, which I independently reproduced. Maintains a traceability gap for future re-runs.
- Required action (optional): commit the seeder as a tagged/immutable artifact alongside 8D/8E evidence, or document a reproducible rebuild recipe.
- Blocks 8C sealing? NO. Blocks Room v3 release? NO.

**F2 - Minor report wording: "Application ID" listed without `.debug` suffix in section 4/5 tables (P2)**
- Severity: P2
- Category: BATCH 8C
- File/evidence: report lines 34 and 47 state `io.github.yuninggu.evolune.debug` correctly in prose, while section 4 row "Application ID" and section 5 show the same; on re-read the tables are consistent. This finding is downgraded to a note: no material inaccuracy found after full read.
- Problem: none material.
- Impact: none.
- Required action: none.
- Blocks 8C sealing? NO. Blocks Room v3 release? NO.

## Independent validation

Real commands executed (all results above):

- `git log --graph --oneline -25`; ancestor checks (`git merge-base --is-ancestor` exit 0 for 820ea35, 6d3e68c, d994d55, 16d8dbf).
- `git show 16d8dbf:.../AppDatabase.kt`, `git ls-tree`, baseline `2.json` identity + canonical SHA (git blob → SHA-256 = `B8DA54ED...5DA`).
- `git cat-file blob HEAD:.../2.json|3.json` → SHA-256 recomputed matching locked values.
- `aapt2 dump badging` (v2 and v3 APKs): package/versionCode/versionName/minSdk/targetSdk.
- `apksigner verify --print-certs` on all four APKs: cert SHA-256 `66dbe8e4...` identical.
- `Get-FileHash` on all four APKs: matches report.
- adb: install -r v2 app+test; `am instrument` seeder → OK (1 test); `run-as ... sqlite3` pre-upgrade (user_version=2, identity, 6/3); `dumpsys package` firstInstall/lastUpdate; force-stop; install -r v3 app+test; `am start` + `ps` process check; post-upgrade sqlite3 (user_version=3, identity, 6/3/5, integrity, FK); slot dump (5 slots, exact mapping); `am instrument` Batch8CPreservedUpgradeTest → OK (2 tests) and OK (1 test) after force-stop/reopen — repeated on both API33 (5554) and API35 (5560).
- Connected combined regressions: API33 76/76, API35 76/76 (XML).
- Foldable (5558, correct FQCN): 21/21 (XML).
- JVM: 8A 6/6, 8B 5/5, full 51/417/0, PK 5/49/0 (XML).
- ksp/assemble/compileAndroidTest/lint (0 err / 83 warn); wear JVM 1/1; wear assemble.
- Destructive-fallback grep: 0.

## Final decision

Batch 8C may be sealed.

After Batch 8C sealing and integration, Batch 8D may begin.

The preserved-data Android v2-to-v3 package-upgrade gate is independently satisfied.

Room v3 remains internal and unreleasable.

Actual release remains forbidden.