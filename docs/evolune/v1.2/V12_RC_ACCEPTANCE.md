# Evolune v1.2 Release Candidate Acceptance

## HISTORICAL — PRE-HC4

All acceptance records before `## Post-HC4 RC Cycle`, including the earlier
RC0/RC1 cycle and its `PASS` rows, are historical investigation records only.
They are not current v1.2 release acceptance evidence and must not be treated
as inherited release gates. The current cycle is defined only by the
post-HC4 baseline and evidence recorded below.

## HISTORICAL — PRE-HC4: RC1 identity and evidence rule

- Validation date: `2026-08-24`
- RC1 branch: `v1.2/rc1-live-validation`
- Functional commit under validation: `99ee3618fe8e5e84451bd010bfdd58281fd1ca90`
- Functional baseline: `45c1b440ba2a5ab3b362ff68cec8ddac08da3d40`
- Target final version: `1.2.0`
- Release state: `NOT RELEASED`

Every row records status plus date, target/device/service, build or test
context, commit, and the observed evidence. `PASS` is used only where the
listed evidence exists. Unit or fake-provider evidence never substitutes for
the owner-device, live Drive, KDF-device, end-to-end, or signed release gates.

## HC4 validation-cycle reset

The previously documented RC1 validation cycle is invalidated for the HC4
feature scope. Its evidence remains historical investigation record only and
must not be reused as acceptance evidence for foreground Health Connect weight
sync. HC4 starts a new validation cycle from the accepted RC1-T2 functional
baseline `94c5c54c9d4ce8836485e63e3aeacd7b9a33f490` on branch
`v1.2/hc4-foreground-weight-sync`.

The new cycle covers the foreground-only sync contract documented in
`V12_HC4_WEIGHT_SYNC.md`: Settings entry/page IA, default-off enable flow,
one-shot permission request, silent foreground checks, strict observation
watermark, restart persistence, manual-weight protection, and the disabled
medication placeholder. The HC4 implementation commit is the commit carrying
this cycle's code and documentation; owner-device, live-service, signed
release, and RC acceptance evidence must be recorded against that new commit.

The RC remains `NOT RELEASED`. No prior RC1 `PASS` row closes an HC4-specific
gate, and the owner-device Health Connect checklist below remains release
blocking until revalidated against the new cycle.

## A. Automated regression

- [x] `PASS` — App unit tests — evidence: `2026-08-24 | local Windows | :app:testDebugUnitTest --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL; 574 tests, 0 failures/errors`
- [x] `PASS` — experience-core tests — evidence: `2026-08-24 | local Windows JVM | :experience-core:test --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [x] `PASS` — Wear unit tests — evidence: `2026-08-24 | local Windows | :wear:testDebugUnitTest --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL; 27 tests, 0 failures/errors`
- [x] `PASS` — App debug assemble — evidence: `2026-08-24 | local Windows | :app:assembleDebug --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [x] `PASS` — Wear debug assemble — evidence: `2026-08-24 | local Windows | :wear:assembleDebug --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [ ] `FAIL` — API33 instrumentation on `evolune-hc3-api33` — evidence: `2026-08-24 | Google AVD emulator-5554, API33/Android 13 | connectedDebugAndroidTest debug | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | 123 completed, 117 passed, 3 failed, 3 skipped before stop; MedicationPlansScreenTest editor field assertion failed`
- [ ] `NOT TESTED` — API33 instrumentation on `Evolune_API33_Migration` — evidence: `2026-08-24 | Google AVD emulator-5556, API33/Android 13 | connectedDebugAndroidTest debug | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | 123 completed, 120 passed, 0 failed, 3 skipped; 23 remaining tests not run after sibling API33 failure`
- [ ] `NOT TESTED` — API35+ instrumentation on `Pixel_10_Pro_Fold` — evidence: `2026-08-24 | Google AVD emulator-5558, API37/Android 17 | connectedDebugAndroidTest debug | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | 123 completed, 120 passed, 0 failed, 3 skipped; 23 remaining tests not run after API33 failure`
- [x] `PASS` — HC1, HC2 R-09, HC3, B1 golden, B2 crash matrix, B3, B4, Mahiro v1, and Room/schema JVM slices — evidence: `2026-08-24 | local Windows | App/Wear unit regression | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | relevant suites passed; no failures/errors`

## B. Health Connect owner-device gate

The online emulator inventory was recorded on `2026-08-24`: `emulator-5554`
(`evolune-hc3-api33`, API33, Android 13, GMS present), `emulator-5556`
(`Evolune_API33_Migration`, API33, Android 13, GMS present), and
`emulator-5558` (`Pixel_10_Pro_Fold`, API37, Android 17, GMS and Health
Connect packages present). The instrumentation failure stopped manual HC UI
validation before any HC emulator gate could be closed.

- [ ] `NOT TESTED` — API31/32 provider installed/current, permission grant/deny, and weight read — evidence: `2026-08-24 | no owner device or emulator | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no qualifying device evidence`
- [ ] `NOT TESTED` — API33 provider installed/current, availability, permission, and read/no-data behavior — evidence: `2026-08-24 | no owner device or emulator | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no qualifying device evidence`
- [ ] `NOT TESTED` — API31–33 provider update-required case distinct from provider missing — evidence: `2026-08-24 | no owner device or emulator | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | case not constructible locally`
- [ ] `NOT TESTED` — physical device grant, revoke, and re-read after `READ_WEIGHT` revocation — evidence: `2026-08-24 | no physical Android device | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no qualifying device evidence`
- [ ] `NOT TESTED` — real `WeightRecord`: preview B, local A unchanged, explicit adoption, and PK recalculation — evidence: `2026-08-24 | no physical Android device | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no qualifying Health Connect record`
- [ ] `NOT TESTED` — adopted weight survives force-stop/restart and PK uses B — evidence: `2026-08-24 | no physical Android device | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | restart evidence unavailable`
- [ ] `NOT TESTED` — permission flow plus Activity recreation has no duplicate request and retains retry — evidence: `2026-08-24 | no physical Android device | no build installed | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | recreation evidence unavailable`

## C. Google Drive live RC gate

All rows are blocked by the absence of an approved Google Cloud OAuth project,
live test account, and service-connected device. Source inspection found only
`https://www.googleapis.com/auth/drive.appdata`; this is not live-service
evidence.

- [ ] `BLOCKED` — authorize with `drive.appdata` only — evidence: `2026-08-24 | Google Cloud/live account unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | external OAuth configuration required`
- [ ] `BLOCKED` — verify no offline access — evidence: `2026-08-24 | Google Cloud/live account unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | external OAuth configuration required`
- [ ] `BLOCKED` — upload synthetic encrypted B1 backup and verify readback — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`
- [ ] `BLOCKED` — `listBackups()` sees the generation — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`
- [ ] `BLOCKED` — bounded download and B1 decrypt — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`
- [ ] `BLOCKED` — four generations retain exactly the latest three and never self-prune the verified current generation — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`
- [ ] `BLOCKED` — disconnect/revoke leaves backups intact — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`
- [ ] `BLOCKED` — backup is absent from normal My Drive UI and unrelated app-data objects are retained — evidence: `2026-08-24 | Google Drive unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no approved service session`

## D. Backup → restore live end-to-end gate

- [ ] `BLOCKED` — canonical state A → live backup/readback → change to B → generation selection → download → passphrase → preview → destructive confirmation → B2 restore → semantic state A — evidence: `2026-08-24 | Google Drive/device unavailable | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | live E2E cannot be executed`
- [ ] `NOT TESTED` — live verification of plans, slots, events, and dangling `event.slotId` — evidence: `2026-08-24 | no live device/service | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | owner E2E required`
- [ ] `NOT TESTED` — live verification of body weight, theme, color theme, auto-update, and time format — evidence: `2026-08-24 | no live device/service | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | owner E2E required`
- [ ] `NOT TESTED` — live post-restore PK, reminders, widgets, and Wear state — evidence: `2026-08-24 | no live device/service | no live build | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | owner E2E required`

The RC0 JVM fake-provider large-history preflight passed for 2,000 events;
it does not close this live gate.

## E. Security and performance gate

- [ ] `NOT TESTED` — PBKDF2-HMAC-SHA256 600,000-iteration benchmark on a real target — evidence: `2026-08-24 | no physical Android device | no benchmark harness run | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | encode/decode median and max unavailable`
- [ ] `NOT TESTED` — large-history device backup/decrypt/preview sanity — evidence: `2026-08-24 | no physical Android device | debug JVM preflight only | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | device ANR/OOM evidence unavailable`
- [x] `PASS` — no passphrase/token persistence or logging in the RC code path — evidence: `2026-08-24 | local source and unit tests | debug test suites | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | prohibited logging sweep clean; relevant tests passed`
- [x] `PASS` — corrupt, wrong-secret, unsupported, and invalid local data fail before restore mutation — evidence: `2026-08-24 | local JVM | App backup/restore tests | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | relevant suites passed`

## F. Signed and minified release gate

- [ ] `BLOCKED` — release signing credentials and keystore availability — evidence: `2026-08-24 | local Windows | release configuration audit | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | EVOLUNE_KEYSTORE_PATH/PASSWORD/ALIAS/KEY_PASSWORD missing; keystore unavailable`
- [ ] `BLOCKED` — signed/minified App release build — evidence: `2026-08-24 | local Windows | :app:assembleRelease not runnable | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | signing gate blocks before APK/R8 evidence`
- [ ] `BLOCKED` — signed/minified Wear release build — evidence: `2026-08-24 | local Windows | :wear:assembleRelease not runnable | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | signing gate blocks before APK/R8 evidence`
- [ ] `NOT TESTED` — `apksigner verify`, signer SHA-256, and shared-identity comparison — evidence: `2026-08-24 | no release APK | apksigner verification unavailable | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no signer evidence`
- [ ] `NOT TESTED` — signed release smoke for App and Wear — evidence: `2026-08-24 | no signed APK/device | no release smoke | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no qualifying evidence`

## G. Final release metadata gate

- [x] `PASS` — App and Wear use shared `versionName = 1.2.0` — evidence: `2026-08-24 | local Gradle validation | validateEvoluneIdentityAndVersioning | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | validation passed`
- [x] `PASS` — Phone version code `101020000` — evidence: `2026-08-24 | local Gradle validation | validateEvoluneIdentityAndVersioning | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | validation passed`
- [x] `PASS` — Wear version code `1101020000` — evidence: `2026-08-24 | local Gradle validation | validateEvoluneIdentityAndVersioning | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | validation passed`
- [x] `PASS` — RC1 does not move or recreate v1.0/v1.1 tags — evidence: `2026-08-24 | local Git repository | branch validation | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no tag mutation performed`
- [x] `PASS` — no GitHub Release, Play release, or v1.2.0 tag created — evidence: `2026-08-24 | local Git/repository state | RC1 branch only | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | no publication performed`
- [ ] `NOT TESTED` — final owner evidence review — evidence: `2026-08-24 | owner review pending | no final release review | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | device/service/signing blockers remain`

## Release blockers before final release

1. Health Connect owner-device evidence;
2. Google Drive live OAuth/service evidence;
3. live Backup → Restore E2E;
4. real-device KDF benchmark and device large-history sanity;
5. signed/minified release smoke and signer verification; and
6. final owner evidence review.

RC1 is a validation branch and is not a release publication.

## RC1 blocker

- `RC1_BLOCKER` — severity: release-blocking validation failure.
- Device/API: `evolune-hc3-api33` / `emulator-5554` / API33 / Android 13.
- Build/commit: debug instrumentation / `99ee3618fe8e5e84451bd010bfdd58281fd1ca90`.
- Exact tests: `MedicationPlansScreenTest.invalidDraftSkipsRepositoryAndKeepsEditorOpen` at `MedicationPlansScreenTest.kt:107`, `saveFailureKeepsEditorOpenAndShowsError` at `MedicationPlansScreenTest.kt:127`, and `deleteFailureKeepsEditorOpen` at `MedicationPlansScreenTest.kt:197`.
- Expected: the plan editor remains open and the `plan-name` field is displayed.
- Actual: `java.lang.AssertionError: Assert failed: The component with TestTag = 'plan-name' is not displayed!`
- Logs: local per-test logcat contained only the assertion and stack trace; no token, passphrase, or medication payload was recorded.
- Suspected component: unresolved; requires independent triage of the API33 Compose test/editor state. No production fix was attempted on RC1.

## RC1-T1 API33 MedicationPlans instrumentation triage

Evidence date: `2026-08-24`. Temporary diagnostic assertions and semantics
logging were used only during triage and were reverted before documentation
commit.

| Test | API33-A `emulator-5554` | API33-B `emulator-5556` | API37 `emulator-5558` |
|---|---|---|---|
| `invalidDraftSkipsRepositoryAndKeepsEditorOpen` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |
| `saveFailureKeepsEditorOpenAndShowsError` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |
| `deleteFailureKeepsEditorOpen` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |

API35 was `NOT TESTED`: no API35 device was online and the local shell did not
provide an `emulator` launcher to start the available AVD.

### First divergent state and semantics evidence

- Before each action, `plan-editor-surface` and `plan-name` both existed;
  `plan-name` bounds were approximately `l=63, t=271, r=1017, b=439`.
- The save/delete click completed: the expected repository call count and
  `MedicationPlanOperationState.Failure` were reached.
- After each failure, `editSession` remained non-null,
  `plan-editor-surface` remained in composition, and `plan-name` remained in
  the semantics tree with its text and editable actions.
- After `performScrollTo()` on the bottom action, the scroll range was at its
  end (`value=589`, `maxValue=674`) and `plan-name` bounds were approximately
  `l=63, t=-318, r=1017, b=-150`. The node existed but was outside the clipped
  viewport, so `assertIsDisplayed()` failed.
- `plan-error` remained present and displayed for the failure paths.

The first divergence is therefore the assertion semantic, not editor state:
the test asks for physical visibility of a field after deliberately scrolling
to a bottom action, while the test name requires logical editor persistence.
There was no evidence of editor dismissal, missing composition, animation
deadlock, IME obstruction, or repository-driven state closure.

### AVD configuration and clean-state check

- API33-A: `1080x1920`, density `420`, rotation `0`, navigation mode `2`,
  transition/window animation `1.0`, IME hidden.
- API33-B: `1080x2400`, density `420`, rotation `0`, navigation mode `2`,
  transition/window animation `1.0`, IME hidden, top display cutout present.
- After `pm clear io.github.yingqiu0871.evolune.debug` and reinstalling the
  debug/test APKs on API33-A, all three tests still failed once each with the
  same `plan-name` not displayed assertion. Stale app state is not the cause.

### T1 classification and next action

Classification: `T1-B — test harness/test assertion bug`.

Recommended narrow follow-up on a separate RC1-Fix-Test branch: preserve the
existing logical-open assertions and replace only the post-operation
`plan-name.assertIsDisplayed()` checks with `assertExists()`, or explicitly
scroll the field into view before asserting display if physical visibility is
intended. Do not change production UI code based on this evidence.

## RC1-Fix-Test — corrected MedicationPlans editor assertions

Evidence date: `2026-08-24`. The fix branch was created from the exact T1
triage commit `7891ec7e40da5f67c5afcc421dd9a1238e2fe574`. The T1-B finding is
closed by a test-only correction:

- In `invalidDraftSkipsRepositoryAndKeepsEditorOpen`,
  `saveFailureKeepsEditorOpenAndShowsError`, and
  `deleteFailureKeepsEditorOpen`, the post-operation
  `plan-name.assertIsDisplayed()` assertion was changed to
  `plan-name.assertExists()`.
- Each of those paths now also asserts
  `plan-editor-surface.assertExists()` to prove the logical editor remains
  composed/open.
- The `plan-error.assertIsDisplayed()` assertions remain strong, as do the
  repository-call, operation-state, and error-message assertions.
- No Kotlin production source, manifest, Gradle file, or production UI
  behavior was changed. Temporary triage diagnostics are not retained.

### Focused repeated instrumentation matrix

| Device | `invalidDraft...` | `saveFailure...` | `deleteFailure...` | Result |
|---|---:|---:|---:|---|
| API33-A `evolune-hc3-api33` / `emulator-5554` | 5/5 PASS | 5/5 PASS | 5/5 PASS | PASS |
| API33-B `Evolune_API33_Migration` / `emulator-5556` | 3/3 PASS | 3/3 PASS | 3/3 PASS | PASS |
| API37 `Pixel_10_Pro_Fold` / `emulator-5558` | 3/3 PASS | 3/3 PASS | 3/3 PASS | PASS |
| API35 | NOT AVAILABLE | NOT AVAILABLE | NOT AVAILABLE | NOT TESTED |

### Full connected instrumentation

The full `:app:connectedDebugAndroidTest --rerun-tasks` run completed on all
three online devices. Results are recorded per device; no device was stopped
early:

| Device | Total | Passed | Failed | Skipped | Result |
|---|---:|---:|---:|---:|---|
| API33-A `evolune-hc3-api33` | 146 | 143 | 0 | 3 | PASS |
| API33-B `Evolune_API33_Migration` | 146 | 142 | 1 | 3 | FAIL — existing `RealAppImeFrameProbeTest.frameLevelImeMotionAnalysis` keyboard-occlusion assertion |
| API37 `Pixel_10_Pro_Fold` | 146 | 144 | 0 | 2 | PASS |

The API33-B full-suite failure is outside MedicationPlans and is not caused
by this assertion-only change. Therefore the focused T1-B gate is `PASS`, but
the full automated RC instrumentation gate remains `BLOCKED` until the
unrelated `RealAppImeFrameProbeTest` failure is separately resolved or
accepted by the RC owner. API35, physical Health Connect, live Drive, signed
release/R8, KDF-device, and other owner-device gates remain unchanged and are
not reclassified by this fix.

## RC1-T2 — API33-B IME frame-probe triage

Evidence date: `2026-08-24`. The triage branch was created from the exact
RC1-Fix-Test commit `4bb00aebcaca4376fcb601effac9bcb90c283efe`. This section
records an investigation only; no Kotlin, manifest, Gradle, or test source
was changed.

### Classification

Primary classification: `T2-B — test/probe bug`.

The first divergence occurs before the first IME geometry assertion. The
probe's `activeWindowRoot()` selects a focused `840x555` (API33-B) or
`466px`-high (API37) root, while Compose semantics `boundsInWindow` continues
to report coordinates in the full application window (`1080x2400` on API33-B).
`readViewHeight()` and `readImeInset()` therefore use a different root from
the field and scroll coordinates. The impossible values
`viewHeight=555`, `ime=1307`, and `endViewportBottom=-752` are direct evidence
of that mismatch. The intermittent alternation between roots is a symptom of
the probe's window-root selection/synchronization, not evidence of a
production scroll race; `T2-D` is not the primary classification.

The probe also permits a vacuous green result: when the selected root reports
`imeOpenedCycles=0/5`, the static baseline can still make
`occludingCycles=5/5`, while `withIme` is empty and the remaining assertions
have no IME cycle to evaluate.

### Original repeated probe matrix

The counts below are raw instrumentation outcomes. `PASS*` means the runner
reported `OK`, but the run was not valid evidence because the probe selected
the wrong root or never observed a full IME.

| Device | Raw matrix | Probe-valid interpretation |
|---|---:|---|
| API33-A `evolune-hc3-api33` / `emulator-5554` | 4 PASS / 1 FAIL | 0 valid passes; all five runs used `viewHeight=555`; passes had `imeMax=5`, and the failure had `imeMax=1307` with `endViewportBottom=-752` |
| API33-B `Evolune_API33_Migration` / `emulator-5556` | 8 PASS / 2 FAIL | Runs 1–2 were valid full-window observations (`viewHeight=2400`, `imeMax=1307`, 5/5 cycles opened); PASS runs 5, 7–10 were vacuous (`viewHeight=555`, `imeMax=0`, 0/5 opened); failures included `bottom=1030 / viewport=555` and one bounce report with `endViewportBottom=-752` |
| API37 `Pixel_10_Pro_Fold` / `emulator-5558` | 4 PASS / 1 FAIL | 0 valid passes; PASS runs were vacuous (`viewHeight=466`, `imeMax=0`, 0/5 opened); the failure used `imeMax=1096`, `endViewportBottom=-630` |
| API35 | NOT AVAILABLE | NOT TESTED |

Representative exact failures were:

- `Field ended occluded by the keyboard in 1/1 cycles: bottom=1030.0 viewport=555`.
- `Field bounced in 1/4 cycles: dirChanges=[1,0,0,0] motionPhases=[1,1,1,1] lateScrollAt=[-1,-1,-1,-1]`, with `viewHeight=555` and `endViewportBottom=-752`.
- API33-A: `Field ended occluded by the keyboard in 3/5 cycles`, with
  `endViewportBottom=-752`.
- API37: `Field ended occluded by the keyboard in 4/4 cycles`, with
  `endViewportBottom=-630`.

### API33-B post-`pm clear` retest

After `pm clear io.github.yingqiu0871.evolune.debug`, reinstalling the debug
and test APKs, and resetting the test process, five completed runs were
counted: `3` raw PASS and `2` raw FAIL. The three raw PASS runs all reported
`viewHeight=555`, `imeOpenedCycles=0/5`, `occludingCycles=5/5`, and
`maxImeInset=0`; they are invalid/vacuous. The two failures reproduced the
same wrong-root family, including `bottom=1030 / viewport=555` and
`imeOpenedCycles=1/5`, `maxImeInset=417`, `viewHeight=555`. One additional
instrumentation attempt was interrupted while resetting the test process and
was excluded from the five completed results.

### Device, window, and IME evidence

| Device | Physical display / density | App bounds / cutout | IME and relevant settings |
|---|---|---|---|
| API33-A | `1080x1920` / `420` | `1080x1857`, no cutout | Gboard LatinIME; nav mode `2`; rotation `0`; animation scales observed as `1.0` |
| API33-B | `1080x2400` / `420` | `1080x2337`, top cutout `128px` | Same Gboard LatinIME; nav mode `2`; rotation `0`; animation scales observed as `1.0` |
| API37 | `2076x2152` / `390` | top cutout `136px` | Same Gboard LatinIME; nav mode `2`; rotation `0`; animation scales observed as `1.0` |

API33-A and API33-B use the same `sdk_gphone64_x86_64` model and API 33;
their material difference is the display height and API33-B top cutout. The
IME implementation and navigation/animation settings were otherwise the
same. During the direct API33-B probe evidence, the test logged:

```text
windowRoots selected=com.android.internal.policy.DecorView focus=true size=840x555
all=DecorView:1080x2400:focus=false ime=1307, DecorView:840x555:focus=true ime=0
```

The focused `840x555` root was selected while the `record-dose`
`boundsInWindow` field bottom remained in the full-screen coordinate space
(`1436`, later `1030` after scroll). A live manual dump independently showed
the MainActivity as the IME input target, `ITYPE_IME: visible`,
`ADJUST_RESIZE`, `mInputShown=true`, and the served Compose view at
`0,0-1080,2400`.

### Manual API33-B UX check

The real debug app was launched on `Evolune_API33_Migration` and the following
user path completed without an app crash or unexpected second dialog:

1. Open `记录`, open the add menu, and choose `手动添加`.
2. Focus the `EV 剂量` field; Gboard appeared and the editor scroll container
   resized from approximately `[63,181][1017,2274]` to
   `[63,181][1017,1454]`.
3. Enter `2`; the equivalent E2 field updated to `1.528`.
4. Save; the record list displayed `戊酸雌二醇 · 2.0 mg` with the expected
   time/date.

This validates the basic real-app editor, IME, input, and save path. It does
not claim a frame-level no-bounce result; that remains unmeasured until the
probe uses one consistent application window and coordinate space.

### Normalization and next action

No AVD-variable normalization experiment was run. The root mismatch was
directly reproduced across API33-A, API33-B, API37, and after API33-B
`pm clear`; changing density, cutout, navigation, or animation settings would
not add evidence and would expand this triage beyond its scope. No device
settings were changed.

The narrow follow-up is a separate test-only probe fix: bind height and IME
insets to the actual application window hosting the Compose semantics, or
measure all values through a consistent in-app/Compose coordinate space;
require `imeOpenedCycles == CYCLES`, reject impossible `imeBottom > viewHeight`,
and never treat `occludingCycles > 0` alone as evidence. No production UI
change is indicated by this triage. RC2/live-service gates, owner-device
gates, release signing/R8, tags, and releases remain paused.

## RC Preflight — T2 probe closure

Evidence date: 2026-08-24. This test-only follow-up was run on branch
`v1.2/rc-preflight-t2-ime-probe-fix`, created from the exact HC4 commit
`6b6cc2a7b283fc0cf9286c68a8cb0c5293c9f37d`. It closes the historical RC1-T2
probe blocker only; it is not a new RC owner-device validation cycle.

### Exact test fix and coordinate invariant

- `activeWindowRoot()` is no longer used as the geometry source. It could
  select a focused auxiliary IME root (`840x555`) while Compose semantics
  remained in the full application window.
- `applicationContentRoot()` is the activity decor view hosting the Compose
  semantics. Application dimensions, `WindowInsets.Type.ime()` visibility and
  bottom inset, `record-dose` and `record-editor-scroll` window bounds, and
  viewport calculations now all use that same application content window.
- The probe fails diagnostically if the IME inset exceeds the application
  root height, or if the scroll bounds leave that root. It does not use a
  hardcoded resolution, device-specific branch, or fixed root index.
- A single focused auxiliary root may be used only to issue platform IME
  show/hide commands. It is never used for geometry or inset measurement; more
  than one such root is an explicit failure rather than a guess.
- Every shown and hidden state must settle, and every cycle must be observed:
  `imeOpenedCycles == CYCLES` (`5/5`). A run with no observed IME is not green
  evidence.

### Repeated credible probe matrix

| Device | Application root / maximum IME inset | Repeated result |
|---|---|---|
| API33-A `evolune-hc3-api33` / `emulator-5554` | `1080x1920` / `1307` | `10/10 PASS`; each run had `5/5` shown, `5/5` hidden, and bounce `0/5` |
| API33-B `Evolune_API33_Migration` / `emulator-5556` | `1080x2400` / `1307` | `10/10 PASS`; each run had `5/5` shown, `5/5` hidden, and bounce `0/5` |
| API37 `Pixel_10_Pro_Fold` / `emulator-5558` | `2076x2152` / `1096` | `10/10 PASS`; each run had `5/5` shown, `5/5` hidden, and bounce `0/5` |

### API33-B clean-state retest

The requested base-package command `pm clear io.github.yingqiu0871.evolune`
returned `Failed` because connected debug instrumentation installs the debug
application id `io.github.yingqiu0871.evolune.debug`. After reinstalling the
debug and test APKs, `pm clear io.github.yingqiu0871.evolune.debug` returned
`Success`. Five further final-probe runs then passed (`5/5`), each observing
the full `5/5` shown and `5/5` hidden cycles with the `1080x2400` application
root and `1307` maximum IME inset.

### Full instrumentation and focused HC4 evidence

The full `:app:connectedDebugAndroidTest --rerun-tasks` completed on all three
devices. JUnit XML reported zero failures and errors:

| Device | Total | Passed | Skipped |
|---|---:|---:|---:|
| API33-A | 152 | 149 | 3 |
| API33-B | 152 | 149 | 3 |
| API37 Fold | 152 | 150 | 2 |

The focused `HealthConnectSyncScreenTest` also completed `6/6` on each of the
three devices. No skipped or failed focused HC4 test was observed.

### JVM, build, and historical regression evidence

The required JVM and build suite completed successfully:

- app unit tests: `575/575`
- `experience-core`: `38/38`
- wear unit tests: `27/27`
- `:app:assembleDebug`: PASS
- `:wear:assembleDebug`: PASS

The focused historical slices also completed `100/100`: HC4 coordinator,
HRT R-09, B1 golden codec, B2 restore transaction, backup/restore coordinator,
and B3 Google Drive provider/gateway tests all reported zero failures and
errors.

This closes the historical RC1-T2-B test/probe blocker only. It does not
change production code, start a new RC cycle, close owner-device or live-Drive
gates, change release signing/R8 status, create a tag, or create a release.

## Post-HC4 RC Cycle

This is the current RC0-HC4 integration-freeze cycle. It starts from the exact
RC-Preflight T2 fix commit and does not inherit any old RC0/RC1 `PASS` row:

- Base commit: `19b8e8b873938e59a30274038243a21cc141842d`
- Branch: `v1.2/rc0-hc4-integration-freeze`
- Validation date: `2026-08-24`
- Target version: `1.2.0`
- Release state: `NOT RELEASED`
- Scope: documentation-only post-HC4 integration freeze; no production or
  test source changes

### Current automated evidence

- [x] `PASS` — App unit tests: `575/575`, zero failures/errors.
- [x] `PASS` — `experience-core` tests: `38/38`, zero failures/errors.
- [x] `PASS` — Wear unit tests: `27/27`, zero failures/errors.
- [x] `PASS` — `:app:assembleDebug` and `:wear:assembleDebug`.
- [x] `PASS` — focused HC4, B1, B2, B3, HRT, and backup/restore slices:
  `100/100`.
- [x] `PASS` — full connected instrumentation on all three online AVDs:
  API33-A `152` total / `149` passed / `0` failed / `3` skipped; API33-B
  `152/149/0/3`; API37 Fold `152/150/0/2`.
- [x] `PASS` — `HealthConnectSyncScreenTest`: `6/6` on each online AVD.
- [x] `PASS` — `validateEvoluneIdentityAndVersioning`.

### Current release gates

The following remain open for the current release and are not changed to
`PASS` by unit, emulator, or source evidence:

- [ ] `NOT TESTED` — API31/32 Health Connect provider compatibility,
  permissions, and weight read.
- [ ] `NOT TESTED` — API33 provider installed/current, permission, and
  read/no-data behavior.
- [ ] `NOT TESTED` — API31–33 update-required case distinct from provider
  missing.
- [ ] `NOT TESTED` — physical Android device grant, revoke, and re-read.
- [ ] `NOT TESTED` — real `WeightRecord` preview, non-mutating preview, and
  explicit adoption.
- [ ] `NOT TESTED` — adopted-weight restart persistence and PK use after
  restart.
- [ ] `NOT TESTED` — Activity recreation during/after permission request.
- [ ] `BLOCKED` — live Google Drive OAuth, app-data scope, retention, and
  disconnect/revoke validation.
- [ ] `BLOCKED` — live Backup → Restore end-to-end validation.
- [ ] `NOT TESTED` — real-device KDF benchmark and large-history device
  sanity.
- [ ] `BLOCKED` — signed/minified App and Wear release, R8 runtime, signer
  verification, and release smoke.
- [ ] `NOT TESTED` — final owner evidence review.

The lint command still reports the known repository baseline of `45` errors,
`97` warnings, and `1` hint, led by historical `MissingTranslation` findings;
no HC4-specific lint delta was found. The release remains `NOT RELEASED`.
No tag, GitHub Release, Play publication, live Drive session, or new RC1
cycle is created by this freeze.

## RC1-HC4 Owner-device / Live-service Validation

This is the current post-HC4 RC1 validation cycle. It starts from the accepted
RC0-HC4 integration-freeze commit below. Historical RC0/RC1 rows above are not
inherited as current release evidence.

- Base commit: `4cce293ff2f298f95ecf49617b9ab5f215a62f3b`
- Branch: `v1.2/rc1-hc4-live-validation`
- Validation date: `2026-08-24`
- Target version: `1.2.0`
- Release state: `NOT RELEASED`
- Production/test source changes in this cycle: none

Every current row uses exactly one of these statuses: `PASS`, `FAIL`,
`NOT TESTED`, or `BLOCKED`. Evidence levels are: `UNIT`, `INSTRUMENTATION`,
`EMULATOR`, `PHYSICAL DEVICE`, `LIVE GOOGLE SERVICE`, and `SIGNED RELEASE`.
No emulator result closes a physical-device, live-service, KDF, or signed
release gate.

### Current automated and instrumentation evidence

- [x] `PASS` — App JVM unit tests — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows | debug unit
  build | UNIT | :app:testDebugUnitTest --rerun-tasks BUILD SUCCESSFUL`.
- [x] `PASS` — experience-core tests — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows JVM | test task
  | UNIT | :experience-core:test BUILD SUCCESSFUL`.
- [x] `PASS` — Wear JVM unit tests — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows | debug unit
  build | UNIT | :wear:testDebugUnitTest BUILD SUCCESSFUL`.
- [x] `PASS` — App and Wear debug assemble — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows | debug APK
  build | UNIT | :app:assembleDebug and :wear:assembleDebug BUILD SUCCESSFUL`.
- [x] `PASS` — full connected app instrumentation on online AVDs — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | debug
  instrumentation | INSTRUMENTATION | JUnit XML: API33-A 152 total/149
  passed/0 failed/3 skipped; API33-B 152/149/0/3; API37 Fold 152/150/0/2`.
- [x] `PASS` — `RealAppImeFrameProbeTest` focused regression — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | debug
  instrumentation | INSTRUMENTATION | 1/1 per online device; application
  decor/content root and same-coordinate IME insets; no auxiliary-root green`.
- [x] `PASS` — historical HC4/B1/B2/B3/HRT and backup/restore JVM slices —
  evidence: `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local
  Windows | debug unit build | UNIT | prior accepted focused suites remain
  green; no production source changed in this cycle`.

### Device matrix

- [x] `PASS` — API33-A `emulator-5554` / `evolune-hc3-api33` — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API33 Android
  13, 1080x1920, density 420 | debug instrumentation | INSTRUMENTATION |
  152 total, 149 passed, 0 failed, 3 skipped`.
- [x] `PASS` — API33-B `emulator-5556` / `Evolune_API33_Migration` — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API33 Android
  13, 1080x2400, density 420 | debug instrumentation | INSTRUMENTATION |
  152 total, 149 passed, 0 failed, 3 skipped`.
- [ ] `NOT TESTED` — API35 `emulator-5559` / `evolune-hc3-api35` — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | target AVD
  remained offline | instrumentation unavailable | INSTRUMENTATION | no
  device result recorded or promoted`.
- [x] `PASS` — API37 Fold `emulator-5558` / `Pixel_10_Pro_Fold` — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Android
  17, 2076x2152, density 390, GMS and Health Connect packages present |
  debug instrumentation | INSTRUMENTATION | 152 total, 150 passed, 0 failed,
  2 skipped`.

### Current Health Connect evidence

- [x] `PASS` — API33 provider-missing behavior and Settings UX — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API33-B
  emulator-5556 | debug app | EMULATOR | Health Connect entry visible,
  legacy Read/Preview/Use UI absent, medication row disabled with
  “v1.8” copy, status resolved to unavailable with provider-missing message`.
- [x] `PASS` — API37 provider-installed/current no-data behavior — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold
  emulator-5558 | debug app | EMULATOR | Health Connect controller and
  backup/restore packages present; connected state and “no new weight record”
  shown after grant; medication sync disabled`.
- [ ] `NOT TESTED` — API31/32 provider compatibility and record read —
  evidence: `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | no
  API31/32 device | no qualifying run | EMULATOR | no evidence`.
- [ ] `NOT TESTED` — API31–33 provider update-required case distinct from
  provider missing — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | no constructible target |
  no qualifying run | EMULATOR | no evidence`.
- [x] `PASS` — first enable, deny, retry, grant — evidence: `2026-08-24 |
  base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold |
  debug app | EMULATOR | OFF→ON opened one user-triggered Health Connect
  request; deny returned to permission-required/off; a second explicit ON
  reopened the request; grant produced connected/no-data state`.
- [x] `PASS` — system revoke and explicit reauthorization — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold |
  debug app | EMULATOR | official MANAGE_HEALTH_PERMISSIONS set selected body
  measurements to 0; app returned permission-required with user-intent switch
  retained, no automatic dialog after force-stop/relaunch, and only a later
  explicit switch action reopened one request`.
- [x] `PASS` — no unintended write/read on no-record path — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold |
  debug app | EMULATOR | local weight remained 55.0 kg and the UI remained
  “no new weight record”; source audit found READ_WEIGHT only and no
  WRITE_WEIGHT/background/history permission`.
- [x] `PASS` — enabled preference after force-stop/relaunch — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold |
  debug app | EMULATOR | after reauthorization and force-stop/relaunch,
  connected state, enabled switch, and no-data status remained visible without
  an unsolicited permission dialog`.
- [ ] `NOT TESTED` — actual Health Connect `WeightRecord` preview/adoption,
  local-vs-adopted weight, PK update, and real watermark freshness sequence —
  evidence: `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37
  Health Connect official data view | debug app | EMULATOR | no legal
  WRITE_WEIGHT producer or manual record-entry tool was available; no record
  was fabricated and no Evolune WRITE_WEIGHT permission was added`.
- [ ] `NOT TESTED` — physical Android Health Connect gate — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | no physical
  Android device | no qualifying build | PHYSICAL DEVICE | grant/revoke and
  real-record owner evidence still required`.

### Fold UI and B4 smoke

- [x] `PASS` — Fold narrow/folded-like and wide/opened HC4 page — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold |
  debug app | EMULATOR | official CLOSED state 1080x2364 and OPENED state
  2076x2152 both showed visible status/permission/weight/medication rows,
  enabled switch, scrolling content, and no clipped button`.
- [ ] `NOT TESTED` — independent landscape Fold layout — evidence: `2026-08-24
  | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | Fold orientation remained
  fixed while the reversible rotation/size probe was attempted | debug app |
  EMULATOR | no landscape result promoted`.
- [x] `PASS` — B4 Backup & Restore Settings entry — evidence: `2026-08-24 |
  base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold | debug app |
  EMULATOR | Settings page showed Backup & Restore entry and actions; live
  action was not executed because the external upload gate was blocked`.

### Google Drive live gate and restore E2E

- [ ] `BLOCKED` — OAuth authorization, live upload/readback/list/download,
  retention, and disconnect — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | API37 Fold has GMS; no approved
  Google Cloud OAuth/test-account session and no safe external-data upload
  authorization in this run | debug app | LIVE GOOGLE SERVICE | external
  Google Cloud/service gate not exercised; no code failure asserted`.
- [ ] `BLOCKED` — live A→B→A backup/restore, HC device-local preference after
  restore, and post-restore PK/reminders/widgets/Wear — evidence: `2026-08-24
  | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | Drive live gate blocked |
  no live build session | LIVE GOOGLE SERVICE | no live generation or restore
  result promoted`.
- [ ] `NOT TESTED` — actual Drive retention current-generation proof and
  disconnect preservation — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | no approved live Drive session |
  no live service run | LIVE GOOGLE SERVICE | owner evidence required`.

### Security, KDF, signing, and Wear release gates

- [ ] `NOT TESTED` — physical-device PBKDF2-HMAC-SHA256 600,000-iteration
  warmup plus five measured encode/decode runs and large-history device sanity
  — evidence: `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b
  | no physical device and no benchmark helper required | no device run |
  PHYSICAL DEVICE | no median/max evidence`.
- [ ] `BLOCKED` — signed/minified App and Wear release, R8 runtime, signer
  verification, and release smoke — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows | signingReport |
  SIGNED RELEASE | EVOLUNE_KEYSTORE_PATH, PASSWORD, ALIAS, and KEY_PASSWORD
  unset; release signing config null; no release APK or R8 smoke`.
- [x] `PASS` — debug signing identity recorded without secrets — evidence:
  `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local
  Windows | :app:signingReport | SIGNED RELEASE | applicationId
  io.github.yingqiu0871.evolune.debug; debug SHA-1
  9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B; debug
  SHA-256 2C:F0:A5:0C:33:B5:40:4A:85:36:3E:42:DC:15:78:1C:5C:7E:4C:36:BB:5F:0E:8C:C0:4A:0E:5D:26:AD:A7:72`.
- [x] `PASS` — Wear JVM/build regression — evidence: `2026-08-24 | base
  4cce293ff2f298f95ecf49617b9ab5f215a62f3b | local Windows | debug unit and
  assemble | UNIT | both tasks BUILD SUCCESSFUL`.
- [ ] `NOT TESTED` — physical Wear runtime, signed Wear R8 smoke, and Data
  Layer — evidence: `2026-08-24 | base 4cce293ff2f298f95ecf49617b9ab5f215a62f3b
  | no physical Wear device and no signed APK | no runtime run | PHYSICAL
  DEVICE | no evidence`.

### Current release blockers

1. API31/32 and provider update-required Health Connect evidence.
2. Physical Android Health Connect record, adoption, watermark, and restart
   evidence.
3. Fold landscape evidence, if required by the owner-device matrix.
4. Approved live Google OAuth/Drive session and synthetic-data A→B→A E2E,
   including retention and disconnect.
5. Physical-device KDF and large-history sanity.
6. Release keystore, signed/minified App/Wear APKs, R8 runtime, signer
   verification, and signed smoke.
7. Final owner evidence review.

No production behavior failure was observed in the earlier RC1-HC4 cycle. The
subsequent R2B live gate is recorded below. No tag, GitHub Release, Play
publication, or RC2 was created.

## RC1-R2B — Live Google Drive Validation

This is the current post-HC4 live-service validation record. The branch is
based on the approved RC1 commit `da2c0142a8de40735ef52ae7a14948ef716cffee`.
The separate R2A API35 triage produced the docs-only sibling commit
`b8ad70ea7dad2feab79407848ab1195078a63e24`; it is not an ancestor of this
validation branch. No production source or test source was changed for R2B.

The pre-R2B Google Drive rows above remain historical context. The rows below
are the current R2B results and use only `PASS`, `FAIL`, `BLOCKED`, or
`NOT TESTED`.

### Owner configuration and authorization

- [x] `PASS` — debug OAuth identity matched the owner configuration — evidence:
  `2026-08-24 | API37 Pixel_10_Pro_Fold / emulator-5554 | package
  io.github.yingqiu0871.evolune.debug | LIVE GOOGLE SERVICE | installed debug
  APK SHA-1 9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B;
  owner-provided Android OAuth client used the same package and SHA-1`.
- [x] `PASS` — Google account authorization flow — evidence:
  `2026-08-24 | GMS account chooser displayed the owner-provided test account
  (masked as g***@gmail.com) and the user-selected account | LIVE GOOGLE
  SERVICE | AuthorizationClient account UI completed; Evolune resumed with
  “已连接（当前会话）”; no DEVELOPER_ERROR/10 was observed`.
- [x] `PASS` — Google Play Services availability — evidence:
  `2026-08-24 | emulator-5554 | pm path com.google.android.gms returned
  installed GMS APK paths | LIVE GOOGLE SERVICE`.
- [x] `PASS` — scope contract audit — evidence:
  `2026-08-24 | source audit of GoogleDriveAuthorizationContract and
  GoogleAuthorizationRequestFactory | LIVE GOOGLE SERVICE | requested scope
  is drive.appdata only; no drive, drive.file, drive.readonly, offline access,
  refresh-token, or server-auth-code path`.

### Live backup and restore gates

- [ ] `FAIL` — first live encrypted upload — evidence:
  `2026-08-24 | API37 Pixel_10_Pro_Fold / emulator-5554 | synthetic
  non-sensitive state and passphrase entered after successful authorization |
  LIVE GOOGLE SERVICE | app returned “备份上传失败”; no fileId was promoted
  and no upload/readback success is claimed`.
- [ ] `NOT TESTED` — remote readback byte/SHA verification — evidence:
  `2026-08-24 | R2B stopped immediately after upload failure | LIVE GOOGLE
  SERVICE | no verified generation`.
- [ ] `NOT TESTED` — remote list and bounded download/decrypt/preview —
  evidence: `2026-08-24 | R2B stopped by fail policy | LIVE GOOGLE SERVICE |
  no generation was selected or downloaded`.
- [ ] `NOT TESTED` — four-generation retention G1/G2/G3/G4 — evidence:
  `2026-08-24 | no verified G1 generation; no G2/G3/G4 sequence attempted |
  LIVE GOOGLE SERVICE | no retention or deletion operation was attempted`.
- [ ] `NOT TESTED` — disconnect and reauthorization preservation — evidence:
  `2026-08-24 | R2B stopped before disconnect/reconnect | LIVE GOOGLE SERVICE |
  remote backup preservation and explicit reauthorization were not exercised`.
- [ ] `NOT TESTED` — A→B→A preview, destructive confirm, and final semantic
  state — evidence: `2026-08-24 | no live generation/readback; no destructive
  restore confirmation | LIVE GOOGLE SERVICE | plans, slots, events,
  bodyWeight, and supported settings were not promoted as restored evidence`.
- [ ] `NOT TESTED` — Health Connect weight-sync preference remains device-local
  across B1 restore — evidence: `2026-08-24 | no restore reached | LIVE GOOGLE
  SERVICE | no post-restore preference result`.

The upload failure is an `RC1_BLOCKER`: authorization succeeded in the real
Google account flow, but the first backup action did not complete. The app
exposed only the stable “备份上传失败” result, and no remote HTTP category or
response body was available in the user-visible evidence. TCP connectivity to
`www.googleapis.com:443` and `oauth2.googleapis.com:443` was reachable from
the Fold at diagnostic time, but that is not evidence of a successful Drive
write. Per the R2B policy, no retry or workaround was used and all subsequent
live gates were stopped.

### Remote isolation semantics

The intended backup boundary remains: each backup is stored in the signed-in
user’s own Google Drive `appDataFolder`, hidden from normal My Drive UI,
Evolune-only, and client-side encrypted before upload. R2B did not complete a
verified upload, so this run makes no claim that a remote generation was
created or that retention state was observed. No Evolune server storage is
claimed.

### R2B regression and release state

The requested fresh Gradle regression was blocked before a new result could be
promoted. `:app:assembleDebug --rerun-tasks` reached Gradle dependency
resolution in an isolated Gradle home and failed on the TLS handshake for
`com.android.tools.build:builder-test-api:9.0.1`; a subsequent
`:app:testDebugUnitTest --rerun-tasks` was blocked while downloading the Gradle
9.2.1 distribution with `SocketException: Permission denied: getsockopt`.
The previously accepted RC1-HC4 unit/build results remain historical evidence;
they are not relabeled as an R2B rerun.

No production code, tests, manifest, Gradle configuration, tag, release, or
RC2 was changed or created in R2B. The live upload blocker, release signing
gate, physical-device gates, and remaining Health Connect owner gates remain
open.

## RC1-R2B-T1 — Live Drive upload failure triage

This docs-only triage is based on the approved R2B baseline
`c68bf920ee3b6ee336c1597d413b2235463ebb15` and branch
`v1.2/rc1-r2b-t1-drive-upload-triage`. No production, test, Manifest, or
Gradle source was changed. The protected `D:\Evolune` root and the old linked
worktree were not touched.

### Live evidence and repeated result

The target was the API37 `Pixel_10_Pro_Fold` AVD (`emulator-5554`,
2076x2152, density 390, font scale 1.0) with package
`io.github.yingqiu0871.evolune.debug`. GMS was installed. The real account
chooser completed for the owner-provided test account (report-masked as
`g***@gmail.com`), Evolune returned `已连接（当前会话）`, and no
`DEVELOPER_ERROR/10` was observed. This is `LIVE GOOGLE SERVICE` evidence for
authorization only, not proof of a Drive write.

| Repeat | Setup and result | Sanitized observation |
|---|---|---|
| 1 | Connected session, synthetic non-sensitive local state, backup submitted | `FAIL` — UI showed `备份上传失败`; no app HTTP/error line, fileId, or readback marker |
| 2 | Same Fold target and flow after dismissing the prior error | `FAIL` — same UI result; no app HTTP/error line, fileId, or readback marker |
| 3 | Same Fold target and flow after dismissing the prior error | `FAIL` — same UI result; no app HTTP/error line, fileId, or readback marker |

The three attempts used sanitized logcat captures. No access token, passphrase,
encrypted payload, or full account address was recorded. The first observable
divergence is the final generic UI error after submission. The exact live
sub-stage is not observable from this build: the coordinator maps a non-invalid
B1 encode failure and the provider `UPLOAD_FAILED` path to the same stable
`备份上传失败` message. A readback verification failure has a distinct
`备份上传后验证失败` message, so the observed text does not prove that
readback began or that a remote file was created.

### Upload path and request audit

Source audit confirms the following path:

`snapshot → B1 encode → AuthorizationClient/access token → provider upload →
Drive v3 multipart POST → create response → readback → byte/SHA verification`.

| Item | Evidence |
|---|---|
| Authorization | `PASS` — real AuthorizationClient flow completed; no token value was logged |
| Scope | `PASS` — `https://www.googleapis.com/auth/drive.appdata` only; no broader scope, offline access, refresh token, or server-auth-code path |
| Request mode | Drive v3 multipart upload: `POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=...` |
| Metadata shape | JSON contains `name`, `mimeType: application/octet-stream`, `parents: ["appDataFolder"]`, and Evolune `appProperties` including format versions, created time, and `contentSha256` |
| Bearer token | `YES` by the source path: AuthorizationClient result is passed as `Authorization: Bearer <token>`; live token value and length were not recorded |
| Encrypted payload length | `UNKNOWN` live; the source passes the encoded B1 bytes into the multipart body, but this build records no byte length |
| HTTP status/reason/body | `UNKNOWN` / not emitted; the adapter maps status to a broad category and discards `errorStream` |
| Remote file created | `UNKNOWN`; no fileId was exposed and no list diagnostic was run |
| Readback attempted | `UNKNOWN` live; it is entered only after a successful create response, and no readback marker was logged |

The owner configuration states that Google Drive API is enabled, but this run
did not receive an HTTP response category or Google reason/message. Therefore
Drive API enablement/project mismatch is not asserted as a root cause. TCP
connectivity to `www.googleapis.com:443` and `oauth2.googleapis.com:443` was
reachable from the Fold, which is not authenticated Drive-write evidence.

### Triage classification and next action

Primary classification is **`T1-C — Production Drive provider/upload path
blocker (provisional)`**. The real post-authorization failure is repeatable in
the provider-facing backup path, while T1-A, T1-B, T1-D, and T1-E lack their
required concrete evidence: no Google error reason, no network/TLS exception,
no proven successful create/readback, and no 5xx/429 response. This is not a
claim that the endpoint or metadata is malformed; the exact create-versus-encode
root cause remains unresolved because the current adapter does not preserve
the needed evidence.

The next narrow action is a separate diagnostic/fix decision: preserve a
sanitized structured HTTP status/reason and upload-stage marker (encode/create/
readback/SHA) without logging secrets, then repeat one live upload. Do not
continue to retention, disconnect, or A→B→A until that blocker is classified.

### T1 stop state

The live upload gate remains `FAIL` and an `RC1_BLOCKER`. Readback, list,
download, retention, disconnect/reauthorization, and A→B→A remain `NOT TESTED`.
No production behavior fix, diagnostic source diff, RC2, tag, or release was
created.

## RC1-R2B-T2 — Live Drive failure observability and stage isolation

This docs-only T2 attempt is based on `ad3c5f62ee3723c390b8ed5cf74ae8d7d3786c54`
and branch `v1.2/rc1-r2b-t2-drive-observability`. A temporary diagnostic APK
was built and installed on the API37 Fold, but the Google Play Services
authorization precondition was unavailable: the real authorization flow
stopped at `登录 - Google 账号` and required an account to be added. No
credentials were entered or guessed.

The temporary source diagnostics compiled successfully and were fully reverted.
The current T2 run therefore did not reach the prior live-upload failure again;
it cannot promote a Drive HTTP status or classify the earlier T1-C provisional
blocker.

| Attempt | S0–S3 | S4 authorization | S5–S13 | Result |
|---|---|---|---|---|
| 1 | `BLOCKED` — not reached | `BLOCKED` — GMS account sign-in screen | `NOT TESTED` | `BLOCKED` |
| 2 | `NOT TESTED` | `BLOCKED` by the same unresolved precondition; not run | `NOT TESTED` | `BLOCKED` |
| 3 | `NOT TESTED` | `BLOCKED` by the same unresolved precondition; not run | `NOT TESTED` | `BLOCKED` |

T2 classification is **`T2-B — Environment/network`**, specifically the
missing signed-in owner test-account precondition on the Fold. This classifies
the current diagnostic blocker only; it is not evidence that the original
Drive upload failure was a network failure. The original first-live-upload
blocker remains unresolved and no T2-A/C/D/E/F/G classification is asserted.

Required next action: the owner must restore/sign in the approved Google test
user on the Fold, after which this observability branch can rerun the same
temporary stage logging and obtain the first failing stage, HTTP status, and
Google reason. Drive retention, disconnect, reauthorization testing, and
A→B→A remain paused.

## RC1-R2B-T3 — Post-account-recovery Drive stage isolation

T3 was based on `4578d6dc30ed934bd835af83c392ee768f156e60` on branch
`v1.2/rc1-r2b-t3-drive-stage-resume`. The owner-reported account recovery was
not visible on the target Fold at execution time: the account check reported
`accountSessionPresent=false`, and the top activity remained the GMS account
sign-in flow. GMS and Play Store packages were present. No credentials were
entered or guessed.

Per the T3 stop rule, no backup was submitted after this check. All S0–S3 and
S5–S13 gates are `NOT TESTED`; S4 is `BLOCKED`. No Drive HTTP request, token,
payload, fileId, readback, or error body was produced. T3 classification is
**`T3-B — Environment/network`**, limited to the missing active Google test
account prerequisite; it is not a classification of the original T1 upload
failure.

The owner must restore the approved Google test account on `emulator-5554` and
leave a usable signed-in session before T3 can resume. Retention, disconnect,
reauthorization testing, A→B→A, RC2, and release remain paused.

## RC1-R2B-T4A — Authorization state discrepancy

T4A was based on `2a1e7ed84d09f57812b30dde926fb3e18fc28f99` on branch
`v1.2/rc1-r2b-t4a-auth-state-triage`. The current Pixel 10 Pro Fold was
identified by AVD name, not by a reused emulator serial: adb serial
`emulator-5558`, AVD `Pixel_10_Pro_Fold`, product
`sdk_gphone16k_x86_64`, Android 17 / API 37. The current Android user/profile
was user `0`; the only running user was `0`.

### Independent account and identity evidence

| Check | Evidence | Result |
|---|---|---|
| System Google account | `dumpsys account` for user 0; one Google account, reported only as `g***@gmail.com` | `PASS` |
| Play Store/GMS session | Play Store was foreground and showed the signed-in account avatar | `PASS` |
| Current user/profile match | Google account and Evolune package are both installed for user 0 | `PASS` |
| APK package | Installed production package `io.github.yingqiu0871.evolune.debug`, version `1.2.0-debug` | `PASS` |
| Installed APK SHA-1 | `9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B` | `PASS` |
| Installed APK SHA-256 | `2C:F0:A5:0C:33:B5:40:4A:85:36:3E:42:DC:15:78:1C:5C:7E:4C:36:BB:5F:0E:8C:C0:4A:0E:5D:26:AD:A7:72` | `PASS` |
| Configured OAuth identity | Same package and SHA-1 as the installed production APK | `PASS` |
| Instrumentation identity | `io.github.yingqiu0871.evolune.debug.test` targets the debug app and is signed by the same debug certificate; no release package was observed | `PASS` |
| Clock/profile preconditions | Automatic time and timezone enabled; no user/profile mismatch observed | `PASS` |

The installed APK was verified from the device APK itself, not inferred from a
Gradle signing report. No `pm clear`, uninstall, account removal, GMS reset, or
AVD wipe was performed.

### AuthorizationClient state

The real production path was exercised once from Settings → Backup & Restore →
立即备份. No Google account chooser, consent, sign-in, or reauthentication UI
appeared. The app moved directly to its local backup-passphrase dialog. The
source-audited coordinator requests authorization before showing that dialog,
and cancelling the dialog returned to a state showing `已连接（当前会话）`.

| State-machine item | T4A result |
|---|---|
| Initial `authorize(request)` | `success / Authorized` |
| `hasResolution` | `false` for this call; no `PendingIntent` was launched |
| Resolution UI classification | `NONE` |
| Existing account offered | Not applicable; no Google UI appeared |
| Google password required | `false`; only the local backup-passphrase dialog appeared |
| Post-resolution result | Not applicable |
| GMS status/error | No `ApiException`, status code, status message, or cause observed |
| Token presence | `true` by the `Authorized` outcome and connected UI; token value was not recorded |

The local passphrase was not submitted, so no encrypted backup was created and
no Drive operation was started.

### Final classification and T3 correction

Final classification: **`B — account exists, normal OAuth authorization state
was previously misclassified`**. The system account is present in the same
Android user as Evolune, the installed APK identity matches the configured
OAuth client, and the current Fold's production call is already authorized.

The previous T3 statement that the Fold lacked an active system Google account
is **rejected**. A Google login/account UI can mean that authorization
resolution or consent is required; it does not prove that the system account is
absent. The historical serial cannot be used as device identity across rounds,
so T3's exact prior device state is not re-inferred from `emulator-5554`.

No Drive request was executed after S4. Retention, disconnect, reauthorization
testing, A→B→A, RC2, tag, and release remain paused.

## RC1-R2B-T4B — Live Drive S0–S11 stage isolation

T4B was based on 3ad9b146a30d5e47a6fe08394216722464e82dd8 on branch
v1.2/rc1-r2b-t4b-drive-live-isolation. The target remained
Pixel_10_Pro_Fold / API 37 / Android user 0. A minimal non-sensitive local
state was used: one plan, one scheduled slot, zero dose events, and body weight
55.0 kg. A disposable RC-only passphrase was submitted only in the device UI;
it is not recorded here.

### Actual production call order

The stage numbering is logical; the actual call order was:

passphrase validation → S4 authorize → S0 authoritative snapshot → S1 B1
payload → S2 canonical/native encode → S3 PBKDF2 + AES-256-GCM → S5 multipart
construction → S6 HTTP setup/send → S7 response → S8 fileId → S9 readback →
S10 response → S11 verification.

### Three-attempt matrix

| Stage | #1 | #2 | #3 |
|---|---|---|---|
| S0 snapshot | PASS — plans=1, slots=1, events=0 | PASS | PASS |
| S1 payload | PASS | PASS | PASS |
| S2 encode | PASS — canonical bytes=610 | PASS | PASS |
| S3 encrypt | PASS — encrypted envelope bytes=1225 | PASS | PASS |
| S4 authorization | PASS — token present, length=324 | PASS | PASS |
| S5 multipart | PASS — POST, uploadType=multipart, parents=[appDataFolder], media=1225 bytes | PASS | PASS |
| S6 HTTP send | FAIL — NetworkOnMainThreadException | FAIL — same | FAIL — same |
| S7 create response | NOT REACHED | NOT REACHED | NOT REACHED |
| S8 fileId | NOT REACHED | NOT REACHED | NOT REACHED |
| S9 readback | NOT REACHED | NOT REACHED | NOT REACHED |
| S10 response | NOT REACHED | NOT REACHED | NOT REACHED |
| S11 SHA verify | NOT REACHED | NOT REACHED | NOT REACHED |

### First failing stage and production finding

The first failing stage was S6, before HttpURLConnection.responseCode and
before any HTTP request was sent. The exception was
NetworkOnMainThreadException; cause chain and exception message were empty.
Therefore HTTP status, Google error code/reason/message, and error body are
NOT OBSERVED, not UNKNOWN.

The constructed endpoint was
https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,size,appProperties.
The metadata parent was exactly appDataFolder; the request method was POST,
multipart content type was present, and the Authorization header was present.
No remote file was created, no fileId was returned, no readback was attempted,
and expected/actual SHA values are NOT APPLICABLE.

The concrete production finding is T4B-F — B4 orchestration/error-mapping
bug:

- BackupRestoreViewModel.submitBackupPassphrase launches the operation in the
  default viewModelScope (Main dispatcher).
- BackupRestoreCoordinator.createBackup reaches
  GoogleDriveBackupProvider.uploadBackup on that context.
- HttpUrlConnectionDriveRemoteGateway.executeJson, specifically the blocking
  connection/request-body setup in configure, is therefore executed on the
  main thread and throws NetworkOnMainThreadException.
- The resulting failure is surfaced as the generic 备份上传失败 UI error.

The minimal next action is a separate narrow RC fix to move blocking Drive I/O
to an IO dispatcher at the appropriate orchestration/transport boundary, with a
regression test proving the live transport is not run on Main and that the
failure mapping remains stable. No fix was made in T4B.

The original generic T1 upload failure is reproducible 3/3, but it is now
classified at a concrete first stage rather than reported as an undifferentiated
upload failure. Retention was explicitly not exercised; temporary diagnostics
and the retention bypass were reverted, and a clean debug APK was reinstalled.
Disconnect, A→B→A, UI1, RC2, tag, and release remain paused.

## RC1-F1 — Drive blocking I/O dispatcher fix

F1 started from the accepted T4B commit
`0d7670c82b5c87b23cb3088894e63fa2ea59e5f3` on branch
`v1.2/rc1-f1-drive-io-dispatcher`. T4B's repeated S6
`NetworkOnMainThreadException` was fixed without moving the ViewModel or the
backup/restore coordinator wholesale to a background dispatcher.

### Production boundary

| Path | Before F1 | After F1 |
|---|---|---|
| Drive JSON request/response | `HttpURLConnection` work ran in the caller context, including Main | `HttpUrlConnectionDriveRemoteGateway.executeJson()` confines the complete blocking operation to injected `ioDispatcher`, default `Dispatchers.IO` |
| Drive download open/response | connection setup, `responseCode`, `inputStream`, and error-stream handling ran in the caller context | `openDownload()` confines connection setup and response acquisition to the same injected dispatcher |
| Download body consumption | provider read the returned network stream in the caller context | `GoogleDriveBackupProvider` confines bounded stream reads and close to its injected IO dispatcher, default `Dispatchers.IO` |

The affected remote operations are create/upload, metadata GET used for
download, download response acquisition and body read, list, and delete/prune.
Authorization, ViewModel lifecycle ownership, B1 bytes, encryption, restore
journal, error mapping, appDataFolder semantics, and retention policy were not
redesigned.

### Dispatcher regression evidence

`GoogleDriveRestGatewayTest` invokes create, metadata GET, list, download, and
delete from a dedicated Main-like executor and asserts that the injected IO
executor performed the observed blocking connection work. The download body
boundary is covered by `GoogleDriveBackupProviderTest`, which records the
thread consuming the returned stream. Existing provider, pagination, retention,
401, bounded-download, SHA-mismatch, B1, B2, and B4 assertions remain active.

### F1 live revalidation

Target: `Pixel_10_Pro_Fold`, API 37, adb serial `emulator-5558`, Android user
0, debug package `io.github.yingqiu0871.evolune.debug`. The clean debug APK was
installed after package-data clear. The counted state was synthetic and
non-sensitive: no medication plans or scheduled slots, zero dose events, and
body weight `55.0 kg`. The disposable passphrase was entered only on-device
and is not recorded here.

| Stage | #1 | #2 | #3 |
|---|---|---|---|
| S0 snapshot | PASS — clean local state | PASS | PASS |
| S1 B1 payload | PASS — end-to-end completion | PASS | PASS |
| S2 encode | PASS — end-to-end completion | PASS | PASS |
| S3 encrypt | PASS — end-to-end completion | PASS | PASS |
| S4 authorization | PASS — approved test account/session | PASS — existing authorized session | PASS — existing authorized session |
| S5 multipart | PASS — production upload path completed | PASS | PASS |
| S6 HTTP send | PASS — no `NetworkOnMainThreadException` | PASS — no exception | PASS — no exception |
| S7 create response | PASS — backup completed UI | PASS | PASS |
| S8 fileId | PASS — required for subsequent readback path | PASS | PASS |
| S9 readback request | PASS — backup completed UI | PASS | PASS |
| S10 readback response | PASS — backup completed UI | PASS | PASS |
| S11 byte/SHA verification | PASS — backup completed UI | PASS | PASS |

The app displayed `备份已完成` in all three counted attempts. Per-stage runtime
markers were not added to production; S0–S5 and S7–S11 are therefore promoted
from the successful production end-to-end result and the existing stage order,
while the former S6 exception was directly absent from sanitized per-attempt
logcat. No token, passphrase, encrypted payload, account address, fileId, or
remote response body was recorded.

`NetworkOnMainThreadException = CLOSED`. There was no new first failing stage;
S0–S11 completed successfully in 3/3 attempts. This closes the T4B RC1
blocker for the blocking Drive I/O boundary. Standalone G1–G4 retention,
disconnect/reauthorization preservation, A→B→A restore semantics, physical
Health Connect gates, device KDF/large-history gates, signed/minified release
validation, and final owner review remain open. No RC2, tag, or release was
created.

## RC1-F2 — Backup crypto CPU dispatcher confinement

F2 was based on the accepted RC1-F1 commit
`58451cd45a9e2d94a7b00719e4aba9675345fd58` and was implemented on
`v1.2/rc1-f2-backup-crypto-dispatcher`.

### CPU boundary

Before F2, the ViewModel's Main-scoped operation reached the synchronous
codec directly:

`BackupRestoreViewModel.submitBackupPassphrase` →
`BackupRestoreCoordinator.createBackup` → `EvoluneBackupCodec.encode` →
PBKDF2-HMAC-SHA256 → AES-GCM.

Restore had the corresponding synchronous path through
`BackupRestoreCoordinator.prepareRestore` →
`EvoluneBackupCodec.decodeAndValidate` → PBKDF2-HMAC-SHA256 → AES-GCM.

The F2 boundary is now the narrow codec call only:

- `encodeOnCryptoDispatcher()` wraps validation, canonical serialization,
  salt/nonce generation, PBKDF2, AES-GCM, and envelope encoding in
  `withContext(cryptoDispatcher)`.
- `decodeAndValidateOnCryptoDispatcher()` wraps envelope parsing, PBKDF2,
  AES-GCM authentication/decryption, payload parsing, and validation in the
  same boundary.
- The injected production default is `Dispatchers.Default`.
- Room/DataStore snapshot reads, Drive operations, restore preview, and the
  B2 persistence transaction were not moved or redesigned. F1's Drive
  blocking-I/O `Dispatchers.IO` boundary remains unchanged.

Structured coroutine cancellation is preserved by `withContext`; no detached
coroutine or cancellation-to-business-error conversion was introduced. The
synchronous PBKDF2/Cipher body is not made artificially cooperative.

### Crypto compatibility

No wire or cryptographic semantics changed:

| Contract | F2 result |
|---|---|
| KDF | PBKDF2-HMAC-SHA256, default 600,000 iterations — unchanged |
| Derived key | 256-bit — unchanged |
| Cipher | AES-256-GCM — unchanged |
| Salt/nonce | generated with the existing sizes and random source — unchanged |
| Authenticated header/AAD, version, KDF parameters, canonical JSON | unchanged |
| B1 golden SHA-256 | `5cbc47bc978c23abcd3e6cbafcad25d4e96208a13dfff6e4a8f5e174349eeaa9` — unchanged |

### Focused and regression evidence

`EvoluneBackupCodecTest` added two direct dispatcher tests. Each invokes the
codec from a dedicated Main-like executor with an injected worker executor and
asserts that the worker executed the encode/decode boundary:

- `encode runs on injected crypto dispatcher from main-like caller`
- `decode runs on injected crypto dispatcher from main-like caller`

The B1 codec suite, B2 restore transaction/coordinator tests, B3 Drive gateway
and provider tests, and B4 coordinator/ViewModel tests all passed. The one
existing ViewModel assertion that assumed same-stack Preview publication was
made to await the Preview state after the now-suspending crypto boundary; no
production behavior was changed by that test synchronization correction.

### API37 Fold responsiveness sanity

Target: `Pixel_10_Pro_Fold`, API 37 / Android 17, adb serial
`emulator-5558`, debug package `io.github.yingqiu0871.evolune.debug`, display
2076×2152, density 390, font scale 1.0, gesture navigation, Latin IME. A clean
debug install used an empty synthetic local state with body weight 55.0 kg and
a disposable passphrase.

| Flow | Result |
|---|---|
| Backup passphrase submit | PASS — UI stayed responsive; production UI reached `备份已完成` |
| Restore passphrase submit/decrypt | PASS — UI stayed responsive; production UI reached `恢复预览` |
| ANR / `NetworkOnMainThreadException` / StrictMode / fatal markers | PASS — none observed in the sanitized logcat observation windows |

This was a responsiveness sanity check only; G1–G4 retention was not rerun.
No API37 instrumentation source was changed.

F2 changed only the crypto dispatcher boundary, its focused/unit regression
tests, and this report plus the HC4 validation report. UI1 was not implemented;
retention was not executed; no RC2, tag, or release was created.

## UI1 — Sync & Backup Settings Information Architecture

UI1 starts from the accepted RC1-F2 target
`4dbde12e2c0ff4d58d2f02559866b5a0bd2ae2eb` on branch
`v1.2/ui1-sync-backup-settings`.

The Settings home now exposes one high-level `同步与备份` entry. The former
direct Health Connect entry, Google Drive backup controls, and Mahiro import /
export controls are no longer rendered on the home page. The new hierarchy is:

```text
设置
└── 同步与备份
    ├── 导入与导出数据
    ├── Health Connect 同步
    └── Google Drive 备份与恢复
```

The secondary page contains passive summaries and navigation rows only. The
Health Connect route remains the existing HC4 screen. The Google Drive route
reuses the existing B4 `BackupRestoreSection` state and callbacks. The local
data route reuses the existing Mahiro import/export callbacks, result dialogs,
clipboard feedback, and file-picker behavior. No Health Connect, Drive,
backup/restore, crypto, Room, DataStore, Manifest, or Gradle business logic was
changed.

### UI1 verification evidence

| Gate | Evidence | Result |
|---|---|---|
| Settings home has one Sync & Backup entry and no direct feature controls | Focused Compose/instrumentation | PASS |
| Sync & Backup → Data / Health Connect / Google Drive | Focused navigation instrumentation | PASS |
| Import/export actions remain available | Focused Compose test | PASS |
| B4 backup/restore/disconnect controls remain available | Focused Compose test | PASS |
| Back navigation across all new routes | Focused navigation instrumentation | PASS |
| Focused UI1 + HC4 tests on API33-A | `emulator-5554`, 10/10 | PASS |
| Focused UI1 + HC4 tests on API33-B | `emulator-5556`, 10/10 | PASS |
| Focused UI1 + HC4 tests on API37 Fold | `emulator-5558`, 10/10 | PASS |
| API35 focused instrumentation | No API35 AVD online in this run | NOT TESTED |
| API37 Fold OPENED visual/layout check | 2076×2152, no clipping/overlap observed | PASS |
| API37 Fold CLOSED visual/layout check | 1080×2364, no clipping/overlap observed | PASS |

UI1 is implementation-complete for this cycle but remains **pending
independent reviewer acceptance**. This evidence does not close the existing
RC owner-device, live-service, signing/R8, or other release gates. Retention,
live G1–G4, A→B→A, RC2, tag, and release were not executed.

## UI1-R1 — zh-rCN string parity

The UI1 independent review approved the implementation with a non-blocking
quality note: the ten UI1 strings added to the default resources were missing
from `values-zh-rCN/strings.xml`, increasing the existing MissingTranslation
count from 45 to 55. R1 adds only the following exact Chinese mirrors:

- `settings_sync_backup_title`
- `settings_sync_backup_desc`
- `settings_sync_backup_local_title`
- `settings_sync_backup_data_title`
- `settings_sync_backup_data_desc`
- `settings_sync_backup_health_title`
- `settings_sync_backup_cloud_title`
- `settings_sync_backup_google_drive_title`
- `settings_data_import_export_title`
- `settings_google_drive_backup_restore_title`

Lint evidence:

| Check | Before R1 | After R1 |
|---|---:|---:|
| Total lint errors | 55 | 45 historical |
| MissingTranslation | 55 | 45 historical |
| Warnings | 97 | 97 |
| Hints | 1 | 1 |
| UI1-introduced MissingTranslation delta | 10 | 0 |

The `lintDebug` task still exits non-zero because the 45 historical widget
translations remain; the UI1 delta is closed. No UI, navigation, business,
HC4, B1, B2, B3, B4, or test semantics changed. The existing UI1 focused suite
remains composed of HealthConnectSyncScreenTest = 6,
SyncAndBackupScreenTest = 3, and SyncAndBackupNavigationTest = 1; it passed
10/10 on API33-A, API33-B, and API37 Fold. API35 remains NOT TESTED because no
API35 AVD was online. No retention, A→B→A, RC2, tag, or release activity was
performed.

## RC1-R3-Fix — Disconnect session clear and Settings/picker polish

This narrow cycle is based on `f6345c622affba7f87e7f2519ed67e0e51311f5a`
and is implemented on `v1.2/rc1-r3-disconnect-ui-polish`.

### Goal A — Disconnect blocker

The prior RC1 live blocker was the real-device failure shown as
`断开 Google Drive 失败`. Code tracing identified the first failing stage as
`GoogleAuthorizationGateway.disconnect()` calling
`AuthorizationClient.revokeAccess(...).awaitTask()`. That API revokes the
authorization grant rather than clearing only the current app session.

The minimal fix now delegates Disconnect to the existing
`clearToken(currentToken)` seam. A missing current token is already a
successful no-op. The provider/coordinator path still has no remote-delete
call, and no local Room/DataStore data is deleted. The requested OAuth
contract remains `drive.appdata` only; no offline access, refresh token, or
server auth code was introduced.

Focused tests cover successful and failed Disconnect, connected-state update,
failure-state preservation, and the invariant that Disconnect never deletes a
cloud backup.

Owner-device live evidence on the API37 Pixel 10 Pro Fold (`emulator-5558`):
the owner manually completed the Google account authorization UI, the app
displayed `已连接（当前会话）`, and the real Disconnect action returned the
page to `需要授权后才能使用`. This sanity pass did not upload or delete a
backup; remote/local non-deletion is covered by the production call path and
focused tests.

### Goal B — Settings and backup picker

- The Settings home `同步与备份` row now appears after the existing settings
  sections and immediately above `更新`; route and click behavior are
  unchanged.
- The home entry and all three Sync & Backup hub entries continue to reuse
  `SettingsNavigationRow`, `settingsListItemColors`, and
  `stableSegmentedShapes`, preserving one MD3 visual treatment.
- The generation picker now uses full-width shared MD3 segmented list rows,
  keeps the existing generation order and selection callback, and displays
  localized `备份 1` / `备份 2` / `备份 3` labels with readable local date-time
  supporting text. Malformed timestamps retain the existing raw-value
  fallback; no generation or backup protocol changed.

The final focused Sync & Backup instrumentation passed 4/4 on the API37
Fold, including `backupPickerUsesNumberedRowsWithReadableTimesAndPreservesSelection`.
The preceding focused run passed 4/4 on API33-A, API33-B, and the Fold. The
existing Health Connect Settings suite passed 6/6 on the Fold. Full app,
experience-core, and Wear JVM regression plus app/Wear debug builds passed.
`app:lintDebug` remains the documented historical baseline of 45 errors, 97
warnings, and 1 hint; this cycle introduced no additional lint delta.

This cycle did not execute retention G3/G4, re-run A→B→A, RC2, signing/R8,
tag, or release activity. Those gates remain paused.

## RC1-R3-R1 — Navigation test scroll fix

The independent review found one test-only blocker after the approved R3
Settings placement: `SyncAndBackupNavigationTest.settingsSyncBackupHealthConnectAndDriveBackNavigationIsStable`
still clicked `settings-sync-backup-entry` without first bringing the row into
the visible viewport. Because `同步与备份` now sits immediately above `更新`,
the entry can be below the initial fold on API33-A, API33-B, and API37 Fold.

R1 adds `performScrollTo()` before the click. The test continues to exercise
the complete Settings → Sync & Backup → Health Connect / Google Drive / Data
Import & Export → Back navigation chain and keeps all existing waits and
assertions.

Focused suite composition after R1:

- `HealthConnectSyncScreenTest` = 6
- `SyncAndBackupScreenTest` = 4
- `SyncAndBackupNavigationTest` = 1
- Total = 11 tests per device

Pre-R1: navigation test failed deterministically because the entry was
offscreen and the test attempted to click without scrolling. Post-R1,
API33-A, API33-B, and API37 Fold all passed the 11-test focused suite. The
API37 Fold navigation test also passed three consecutive standalone runs.

This R1 contains no production Kotlin, resource, navigation, backup,
Health Connect, Room, DataStore, Wear, or UI behavior change. Disconnect live
evidence and the approved R3 production fix are unchanged. Retention, RC2,
tag, and release activity remain paused.

## RC1-R3-F2-T1 — Post-disconnect state-machine isolation

T1 is based on the accepted R3-R1 commit
`4c7c7c1e50ad69d3e412906af5f6a33ddd569d8d` and is a state-machine and live
failure-boundary triage only. No production or permanent test change was
made.

### Actual UI state and action table

The current `BackupRestoreViewModel` state machine is:

| State | Entered by | Exit/reset behavior | Action gate |
|---|---|---|---|
| `Idle` | initial state; cancel; message dismissal; successful Disconnect | starts backup, restore, or Disconnect | backup/restore enabled; Disconnect is shown only when `connected` and enabled in `Idle` |
| `Authorizing(operation)` | backup/restore authorization start or reauthorization request | authorization outcome, activity-result callback, or cancel | backup/restore/Disconnect blocked while non-Idle |
| `AwaitingBackupPassphrase` | backup authorization succeeds | backup passphrase submit or cancel | only backup passphrase submission is valid |
| `PreparingBackup` | backup passphrase submission | codec/provider result or cancellation | interactive actions blocked |
| `Uploading` | reserved upload state in the sealed model; no current ViewModel assignment | terminal result/cancellation if entered | interactive actions blocked |
| `BackupSuccess` | verified backup success | message dismissal/cancel → `Idle` | interactive actions blocked |
| `LoadingBackups` | restore authorization succeeds or backup list starts | list result, authorization event, error, or cancel | interactive actions blocked |
| `SelectingBackup` | backup list succeeds | generation selection or cancel | only generation selection is valid |
| `AwaitingRestorePassphrase` | generation selection | restore passphrase submit or cancel | only restore passphrase submission is valid |
| `PreparingRestorePreview` | restore passphrase submission | preview, authorization, error, or cancellation | interactive actions blocked |
| `Preview` | restore preparation succeeds | confirm or cancel | only confirm/cancel is valid |
| `Restoring` | restore confirmation | restore success/warning/error or cancellation | interactive actions blocked |
| `RestoreSuccess` / `RestoreSuccessRefreshWarning` | restore persistence completes | message dismissal/cancel → `Idle` | interactive actions blocked |
| `Error` | operation failure | message dismissal/cancel → `Idle` | interactive actions blocked |

The `connected` flow is separate from the UI-state enum. Backup and restore
buttons are gated by `state == Idle`, not by `connected == true`; an
authorization attempt is the intended way to reconnect after a successful
Disconnect.

### Disconnect trace and deterministic evidence

The successful Disconnect path is:

```text
Idle (connected=true, before call)
  → Idle (during coordinator/provider call; no separate Disconnecting state)
  → Idle (connected=false, after success)
```

`BackupRestoreViewModel.disconnect()` owns the successful reset: it calls the
coordinator, clears `connected`, and writes `Idle`. The coordinator holds its
existing operation mutex only around the provider operation. The ViewModel's
`operationJob` reference is inactive after completion, and guards check
`operationJob?.isActive`, so a completed Disconnect does not leave a permanent
busy lock. A failed Disconnect instead preserves the connected state and emits
`Error(DISCONNECT_FAILED)`; it does not delete remote generations.

The temporary deterministic trace used the existing ViewModel fakes and was
reverted before this documentation-only change. Its exact observed sequence
was:

```text
[Idle, AwaitingBackupPassphrase, Idle, Idle,
 Error(error=BackupRestoreError(operation=RESTORE, code=NO_BACKUPS))]
connectedAfterDisconnect=false
```

The first `Idle → AwaitingBackupPassphrase` is the setup authorization, the
next `Idle` is cancellation, and the following `Idle` is successful
Disconnect. A direct `restoreFromBackup()` call after Disconnect was accepted
by the ViewModel and reached the existing authorization/list path; the fake
then returned `NO_BACKUPS`. This proves the restore action is callable after
Disconnect and is not blocked by `connected=false`.

### UI relocation and live failure boundary

The UI1 Settings relocation did not remove or relocate the reset owner. The
current Google Drive route still collects `BackupRestoreViewModel.uiEvents`
and passes the same backup, restore, Disconnect, cancel, and dismissal
callbacks; the pre-UI1 Settings-hosted route used the same event collector and
callback wiring. There is therefore no evidence that the UI relocation caused
the post-Disconnect authorization failure.

The prior Fold live attempt must be classified separately from the deterministic
state evidence: after the explicit restore action, no Google Play Services
`AuthorizationActivity` was observable and no token/list result was observed.
The first observed failing boundary is therefore the live
`GoogleAuthorizationGateway.authorize()` / AuthorizationClient resolution and
activity-result handoff. The source path is reachable in the deterministic
fake, but the live GMS activity was not observed; this triage does not claim a
more specific internal Google error.

Classification:

- **T1-A:** not reproduced — `connected=false` does not disable the backup or
  restore buttons.
- **T1-B:** not reproduced — successful Disconnect returns the state to
  `Idle` and releases the active-operation guard.
- **T1-C:** not reproduced — UI relocation did not remove the existing event
  collector or reset callback wiring.
- **T1-D:** not reproduced by deterministic ViewModel evidence — direct
  restore after Disconnect is accepted and reaches authorization/list logic.
- **T1-E:** confirmed as the remaining live boundary — authorization/event
  handoff must be isolated before another live reauthorization attempt.
- **T1-F:** not indicated — no production race or device-only UI state race
  was demonstrated.

The minimal next fix is not a button or state-gate change. The next narrow
investigation should instrument the live `AuthorizationClient` task outcome
and the single `LaunchAuthorization` → activity-result handoff, without
logging tokens, accounts, or secrets. Required follow-up tests are: successful
Disconnect state reset, failed Disconnect state preservation, direct backup
and restore callability after Disconnect, exactly-once authorization event
emission, and a live instrumentation assertion for the actual authorization
activity/result boundary.

Retention evidence and the previously accepted A→B→A restore evidence are
carried forward unchanged. No G3/G4 retention rerun, RC2 work, tag, or release
activity was performed. The RC1 blocker remains open at the live authorization
boundary; this T1 does not claim it closed.

## RC1-R3-F2-T2 — Live authorization handoff stage isolation

T2 is based on `ce121ea9e7d7db19ec4308d9fae2441d2ef6d7aa` and uses temporary,
脱敏 stage observability only. All temporary production logging and diagnostic
tests were reverted before this documentation-only commit. No production
behavior, authorization scope, backup protocol, or restore behavior changed.

### Authorization call graph

The current source call graph is:

```text
GoogleDriveBackupRestoreScreen
  → BackupRestoreViewModel.restoreFromBackup()
  → BackupRestoreViewModel.authorize(RESTORE)
  → BackupRestoreCoordinator.authorizeFor(RESTORE)
  → GoogleAuthorizationGateway.authorize()
  → AuthorizationClient.authorize(AuthorizationRequest)
```

For a direct `Authorized` result, the ViewModel sets the connected session
state and invokes `loadBackupsAfterAuthorization()`, which calls
`BackupRestoreCoordinator.listBackups()` and then renders
`SelectingBackup`.

For `UserResolutionRequired`, the ViewModel emits one
`BackupRestoreUiEvent.LaunchAuthorization`; `AppNavigation` collects it,
launches `StartIntentSenderForResult`, and sends the result through
`GoogleAuthorizationGateway.outcomeFromIntent()` to
`BackupRestoreViewModel.onAuthorizationOutcome()`.

### Automated handoff seam evidence

Temporary tests using the existing fake authorization/provider seam passed
21/21 for the focused `BackupRestoreCoordinatorTest` class:

- disconnected + `Idle` + direct `Authorized`: no authorization event, one
  provider list call, `SelectingBackup` reached;
- disconnected + `Idle` + `UserResolutionRequired`: exactly one
  `LaunchAuthorization` event, simulated result callback accepted, one provider
  list call, `SelectingBackup` reached;
- authorization error: stable `AUTHORIZATION_FAILED`, zero provider list calls.

The temporary tests were removed after the run; no permanent test diff remains.

### API37 Fold counted live attempt

Device: API37 Fold `emulator-5556`, Android 17, debug build. Before the single
counted attempt, the Drive page showed `需要授权后才能使用` and the explicit
Restore action was enabled. Passive navigation into the page produced zero
observed authorization/list stage logs.

The one counted explicit Restore attempt produced this complete trace:

| Stage | Result |
|---|---|
| A0 — explicit Restore action | PASS; `Idle`, `connected=false`, no active job |
| A1 — ViewModel guard and `Authorizing` | PASS |
| A2 — gateway invoked | PASS; `tokenPresent=false` at entry |
| A3 — `AuthorizationClient` task | PASS; task completed successfully |
| A4 — first result classification | `DIRECT_AUTHORIZED` |
| A5D — access token accepted | PASS; only boolean presence observed |
| A6D — session/connected update | PASS; `connected=true` |
| A7D — `listBackups` invoked | PASS |
| A8D — list result | PASS; three generations returned |
| A9D — picker shown | PASS; `选择备份` rendered with three backup rows |

The HasResolution branch was not entered in this live attempt:

```text
A5R/A6R/A7R/A8R/A9R/A10R/A11R/A12R/A13R = NOT APPLICABLE
LaunchAuthorization emitted = 0
collector received = 0
launcher invoked = 0
activity result returned = NOT APPLICABLE
```

No AuthorizationClient exception or status code was observed. No token value,
account identifier, passphrase, or backup payload was logged.

### T2 result and classification

There was no first failing stage in the counted live attempt. The earlier
observation of no Google authorization UI is now explained as compatible with
the `DIRECT_AUTHORIZED` branch: a valid grant can produce a token directly and
skip the resolution UI. Absence of `AuthorizationActivity` is not a failure
signal by itself.

No T2-A through T2-K failure was reproduced. T1-E remains a boundary label for
the earlier uninstrumented observation, not a proven Google root cause. T2
provides no evidence for a missing event collector, duplicated ViewModel,
launcher failure, missing activity result, or list/picker failure.

No inline fix is justified. If the live issue recurs, repeat this same narrow
stage map before changing buttons, Disconnect, the state machine, or the
authorization architecture. No second or third real Restore click was made
because the first attempt reached the picker successfully.

The accepted G3/G4 retention, latest-three retention, protected-current
generation, Disconnect session-clear, and A→B→A evidence are carried forward
unchanged. No retention rerun, A→B→A rerun, RC2, tag, or release activity was
performed.

## UI2 — Single-item MD3 segmented shape parity

UI2 is a narrow visual correction based on the accepted T2 commit
`02af42ad732524f45f4b984146ba5111631ff76d`. UI1/R3 documentation correctly
recorded reuse of the shared `SettingsNavigationRow`, colors, and segmented
shape helper. Owner-device review nevertheless found that standalone rows
were still visibly less rounded than the grouped Settings rows: the existing
`stableSegmentedShapes(index = 0, count = 1)` path returned the ordinary
Expressive list-item shape rather than a full segmented container shape.
This section records that delta without rewriting the earlier UI1/R3 history.

### Shape correction

Before UI2, the shared helper delegated every position directly to Material 3
`segmentedShapes`; its effective semantics were `count = 1` for a standalone
row and first/middle/last positions for grouped rows. After UI2, the same
shared helper explicitly distinguishes `SINGLE`, `TOP`, `MIDDLE`, and
`BOTTOM`. `SINGLE` composes the existing Material 3 top and bottom segmented
shapes so all four corners use the segmented container treatment. No new
hard-coded corner dp value or second shape-constant system was introduced.
State shapes used for selected, pressed, focused, hovered, and dragged states
are the same resolved shape, so `SegmentedListItem` continues to clip its
container and interaction/ripple surface to the corrected outline.

The Settings-home `同步与备份` position is unchanged: it remains immediately
above `更新`. Only its standalone shape changed. On the Sync & Backup page,
the three separate navigation sections—`导入与导出数据`, `Health Connect
同步`, and `Google Drive 备份与恢复`—all continue to use the shared
`SettingsNavigationRow` and now resolve to `SINGLE`. Existing grouped settings
for 夜间模式、颜色主题、时间制式、更新、关于 retain their top/middle/bottom
segmentation and ordering.

### UI2 verification

| Gate | Evidence | Result |
|---|---|---|
| API33-A focused suite | `HealthConnectSyncScreenTest` 6 + `SyncAndBackupScreenTest` 4 + `SyncAndBackupNavigationTest` 1 | **11/11 PASS** |
| API33-B focused suite | device not online in this run | **NOT TESTED** |
| API37 Fold focused suite | same 11-test composition | **11/11 PASS** |
| API37 Fold navigation stability | standalone navigation class, three consecutive runs | **3/3 PASS** |
| API37 Fold OPENED visual gate | standalone Settings and Sync & Backup rows; grouped rows preserved; no visible square edge or ripple overflow | **PASS** |
| API37 Fold CLOSED visual gate | standalone Settings and Sync & Backup rows; grouped rows preserved; no visible square edge or ripple overflow | **PASS** |
| app JVM / experience-core / wear JVM | `testDebugUnitTest`, `:experience-core:test`, `wear:testDebugUnitTest` | **PASS** |
| app / wear debug build | `assembleDebug` | **PASS** |
| app lint | historical baseline: 45 errors, 97 warnings, 1 hint | **EXPECTED BASELINE FAILURE**; no UI2 delta |

The connected focused and standalone Gradle invocations also executed on the
online API33-A device; all its standalone navigation runs were 1/1 PASS. No
API33-B AVD was available, so it is not represented as a pass.

UI2 changed only the shared Settings shape component and this RC
documentation. There is zero authorization, Drive/Disconnect, B1, B2, B3,
B4, or HC4 behavior diff. T2 live authorization evidence is carried forward.
No retention rerun, RC2, tag, or release activity was performed.
