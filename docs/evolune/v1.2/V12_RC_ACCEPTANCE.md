# Evolune v1.2 Release Candidate Acceptance

## RC1 identity and evidence rule

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
