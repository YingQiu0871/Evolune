# Evolune v1.2 RC1 Validation Report

## Scope and identity

- Validation date: `2026-08-24`
- Branch: `v1.2/rc1-live-validation`
- Functional commit under test: `99ee3618fe8e5e84451bd010bfdd58281fd1ca90`
- Parent functional baseline: `45c1b440ba2a5ab3b362ff68cec8ddac08da3d40`
- Build target: v1.2.0 RC validation; no tag or release publication
- RC1 production-code changes: none

RC1 followed validation-first and no-silent-fixes policy. No production code
was fixed on this branch. The connected instrumentation run produced an
`RC1_BLOCKER` on one API33 emulator; validation stopped immediately as
required. No production diagnosis or workaround was applied.

## Signed release and R8

Status: `BLOCKED`.

On `2026-08-24`, the local signing audit on the RC1 functional commit found
the following required variables missing: `EVOLUNE_KEYSTORE_PATH`,
`EVOLUNE_KEYSTORE_PASSWORD`, `EVOLUNE_KEY_ALIAS`, and
`EVOLUNE_KEY_PASSWORD`. No keystore path was available. The prior RC0 release
build attempts stopped at the same project signing gate; RC1 therefore has no
release APK, no `apksigner` output, and no signer certificate SHA-256 to
record. Passwords and private key material were not printed or committed.

App minification is configured on. Wear minification is configured on with the
default optimized ProGuard file. R8 execution, signed APK verification, signer
identity comparison, and signed release smoke remain unverified because the
signing gate stops before APK production.

## Device matrix and Health Connect

The online emulator inventory on `2026-08-24` was:

| Serial | AVD | API / Android | Manufacturer / model | Google service evidence |
|---|---|---|---|---|
| `emulator-5554` | `evolune-hc3-api33` | API33 / Android 13 | Google / `sdk_gphone64_x86_64` | GMS packages present |
| `emulator-5556` | `Evolune_API33_Migration` | API33 / Android 13 | Google / `sdk_gphone64_x86_64` | GMS packages present |
| `emulator-5558` | `Pixel_10_Pro_Fold` | API37 / Android 17 | Google / `sdk_gphone16k_x86_64` | GMS and Health Connect packages present |

Status: `NOT TESTED` for the manual Health Connect UI gates. The connected
instrumentation run was stopped after the first API33 failure, before manual
provider-missing, permission lifecycle, recreation, WeightRecord, or fold UI
validation.

The provider-missing mapping remains covered by unit tests only. An installed
but outdated provider case was not constructed and remains `NOT TESTED`.

## RC1_BLOCKER — API33 instrumentation failure

- Severity: release-blocking validation failure.
- Device: `emulator-5554` / `evolune-hc3-api33` / API33 / Android 13.
- Build/commit: debug instrumentation / `99ee3618fe8e5e84451bd010bfdd58281fd1ca90`.
- Suite: `:app:connectedDebugAndroidTest --rerun-tasks`.
- Device progress when stopped: 123 completed, 117 passed, 3 failed,
  3 skipped; 23 tests were not reached.
- Exact failures:
  - `MedicationPlansScreenTest.invalidDraftSkipsRepositoryAndKeepsEditorOpen`
    at `MedicationPlansScreenTest.kt:107`;
  - `MedicationPlansScreenTest.saveFailureKeepsEditorOpenAndShowsError` at
    `MedicationPlansScreenTest.kt:127`;
  - `MedicationPlansScreenTest.deleteFailureKeepsEditorOpen` at
    `MedicationPlansScreenTest.kt:197`.
- Expected: the plan editor remains open and the `plan-name` field is
  displayed.
- Actual: `java.lang.AssertionError: Assert failed: The component with
  TestTag = 'plan-name' is not displayed!`.
- Logs: per-test local logcat contained only the assertion and stack trace;
  no token, passphrase, or medication payload was recorded.
- Suspected component: unresolved; requires independent triage of API33
  Compose editor/test state. No production fix was attempted on RC1.

The other two devices were also stopped at the same point: API33 migration
AVD had 123/146 completed, 120 passed, 0 failed, 3 skipped; API37 Fold had
123/146 completed, 120 passed, 0 failed, 3 skipped. Their remaining 23 tests
are `NOT TESTED`, not PASS.

## RC1-T1 triage result

Triage date: `2026-08-24`. Temporary diagnostic assertions and
`printToLog` semantics output were added only for the triage run and reverted
before commit.

| Test | API33-A `emulator-5554` | API33-B `emulator-5556` | API37 `emulator-5558` |
|---|---|---|---|
| `invalidDraftSkipsRepositoryAndKeepsEditorOpen` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |
| `saveFailureKeepsEditorOpenAndShowsError` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |
| `deleteFailureKeepsEditorOpen` | 5/5 FAIL | 3/3 FAIL | 3/3 FAIL |

API35 was `NOT TESTED`: no API35 target was online and the local shell did not
provide an `emulator` launcher to start the available AVD.

### Semantics and first divergence

Before each action, `plan-editor-surface` and `plan-name` existed, with
`plan-name` approximately at `l=63, t=271, r=1017, b=439`. The save/delete
action completed, the expected fake repository call count was reached, and
the ViewModel failure state was reached.

After each failure, `editSession` remained non-null and the editor surface
remained in composition. `plan-name` remained in the semantics tree with its
text and editable actions, but after the test's `performScrollTo()` on the
bottom action its bounds were approximately
`l=63, t=-318, r=1017, b=-150`; the scroll range was at its end
(`value=589`, `maxValue=674`). `plan-error` was present and displayed.

The first divergent step is the final assertion's interpretation of physical
visibility. The editor was logically open; the node was merely outside the
clipped viewport. No editor close, missing composition, animation deadlock,
IME obstruction, or repository state transition was observed.

### AVD configuration and clean-state retest

- API33-A: `1080x1920`, density `420`, rotation `0`, navigation mode `2`,
  transition/window animation `1.0`, IME hidden.
- API33-B: `1080x2400`, density `420`, rotation `0`, navigation mode `2`,
  transition/window animation `1.0`, IME hidden, top display cutout present.
- After `pm clear io.github.yingqiu0871.evolune.debug` and reinstalling the
  debug/test APKs on API33-A, all three tests failed once again with the same
  assertion. Stale app state is excluded.

Classification: `T1-B — test harness/test assertion bug`, reproduced across
both API33 AVDs and API37. The narrow next action is a separate RC1-Fix-Test
change: use `assertExists()` for the logical-editor-open contract, or scroll
the field into view before asserting display if physical visibility is the
intended contract. No production UI change is indicated by this triage.

## Google OAuth and live Drive

Status: `BLOCKED`.

Source inspection on `2026-08-24` found only the requested scope:
`https://www.googleapis.com/auth/drive.appdata`. No live Google Cloud OAuth
project, approved Android OAuth client, signing fingerprint configuration,
test account, or connected device was available for RC1. No offline access,
refresh-token, server-auth-code, `drive.file`, or `drive.readonly` path was
enabled by this validation.

Consequently, live authorization, readback, generation listing, bounded
download, live retention, unrelated-object preservation, disconnect/revoke,
and normal-My-Drive absence were not executed. No fake-provider result is
promoted to live evidence.

## Live Backup → Restore E2E

Status: `BLOCKED`.

The required live A → backup/readback → modify to B → select/download/
passphrase/preview → replace → semantic A flow was not executable without a
live Drive service and device. Plans, slots, events, dangling slot IDs, body
weight, themes, update/time settings, PK, reminders, widgets, and Wear
post-restore behavior therefore remain owner evidence gaps.

RC0's local JVM fake-provider preflight for 2,000 events passed and remains
useful regression evidence, but it does not close this live gate.

## KDF and large-history device sanity

Status: `NOT TESTED`.

No device or benchmark harness was available for five measured
PBKDF2-HMAC-SHA256 600,000-iteration encode/decode runs. No iteration change
was made. The 2,000-event JVM sanity passed in RC0; device ANR/OOM and visible
blocking evidence remain unavailable.

## Instrumentation and automated regression

Instrumentation status: `NOT TESTED` because no device/AVD was connected.

Automated results on `2026-08-24`, using the RC1 functional commit and debug
configuration:

- `:app:testDebugUnitTest --rerun-tasks`: `BUILD SUCCESSFUL`; 574 tests,
  0 failures/errors.
- `:experience-core:test --rerun-tasks`: `BUILD SUCCESSFUL`.
- `:wear:testDebugUnitTest --rerun-tasks`: `BUILD SUCCESSFUL`; 27 tests,
  0 failures/errors.
- `:app:assembleDebug --rerun-tasks`: `BUILD SUCCESSFUL`.
- `:wear:assembleDebug --rerun-tasks`: `BUILD SUCCESSFUL`.

## Permission and background audit

The available merged debug manifests on `2026-08-24` contain:

- App: `POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`,
  `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `SCHEDULE_EXACT_ALARM` with
  `maxSdkVersion=32`, `USE_EXACT_ALARM`,
  `android.permission.health.READ_WEIGHT`, and the debug dynamic-receiver
  permission.
- Wear: `RECEIVE_BOOT_COMPLETED` and the debug dynamic-receiver permission.

The release merged manifest could not be produced because signing blocked the
release build. No unexpected Google/Drive Android permission was introduced;
the only Health Connect permission is `READ_WEIGHT`.

Production-source sweep found no `WRITE_WEIGHT`,
`READ_HEALTH_DATA_HISTORY`, `READ_HEALTH_DATA_IN_BACKGROUND`, `WorkManager`,
`PeriodicWorkRequest`, background cloud sync, or Drive watcher path. The
OAuth source contains only `drive.appdata`; no offline access, refresh-token,
server-auth-code, `drive.file`, or `drive.readonly` production path was found.

## Outstanding blockers

1. Configure approved release signing credentials and verify App/Wear signed
   minified APKs, certificates, and R8 smoke.
2. Provide owner Android device/emulator evidence for HC1–HC5 and permission
   recreation.
3. Provide Google Cloud OAuth/live Drive account and device evidence for
   Gates 6–10.
4. Run the real-device 600k KDF benchmark and large-history sanity.
5. Run API33/API35 instrumentation when targets are available.
6. Complete owner evidence review.

## Foldable UI and remaining emulator gates

Status: `NOT TESTED`. The Pixel 10 Pro Fold AVD was online and its Google
packages were present, but fold/rotate UI sanity and Health Connect UI flows
were not started after the API33 instrumentation blocker.

## Final status

- Newly closed release gates: none.
- API33 instrumentation: `FAIL` and recorded above.
- API37 instrumentation: partial execution only; `NOT TESTED` for remaining
  tests.
- API33/API37 Health Connect manual gates: `NOT TESTED`.
- Live Drive, full live restore, signed release, physical-device HC, physical
  KDF, and Wear signed R8 runtime: still `BLOCKED` or `NOT TESTED`.

No tag, GitHub Release, Play release, or production-code fix was created by
RC1.

## RC1-Fix-Test result — MedicationPlans editor-open assertions

Evidence date: `2026-08-24`. A narrow fix branch was created from the T1
triage commit `7891ec7e40da5f67c5afcc421dd9a1238e2fe574`. The finding remains
classified as `T1-B — test harness/test assertion bug`, and is closed by a
test-only change:

1. `invalidDraftSkipsRepositoryAndKeepsEditorOpen`,
   `saveFailureKeepsEditorOpenAndShowsError`, and
   `deleteFailureKeepsEditorOpen` now use
   `plan-name.assertExists()` after the bottom action scroll.
2. Each test also asserts `plan-editor-surface.assertExists()` so logical
   editor persistence is checked independently of viewport clipping.
3. `plan-error.assertIsDisplayed()` remains unchanged and still proves the
   failure message is visible. Repository call counts, non-null edit session,
   operation failure state, and specific error messages remain asserted.

No Kotlin/Java/XML production source, manifest, Gradle configuration, or UI
behavior was modified. The temporary semantics diagnostics used during T1
triage were reverted and are not part of this fix.

### Focused repeated matrix

The corrected three tests passed repeatedly on the available devices:

| Device | Invalid draft | Save failure | Delete failure |
|---|---:|---:|---:|
| API33-A `evolune-hc3-api33` / `emulator-5554` | 5/5 PASS | 5/5 PASS | 5/5 PASS |
| API33-B `Evolune_API33_Migration` / `emulator-5556` | 3/3 PASS | 3/3 PASS | 3/3 PASS |
| API37 `Pixel_10_Pro_Fold` / `emulator-5558` | 3/3 PASS | 3/3 PASS | 3/3 PASS |
| API35 | NOT AVAILABLE | NOT AVAILABLE | NOT AVAILABLE |

### Full instrumentation result

`:app:connectedDebugAndroidTest --rerun-tasks` completed on every online
device:

| Device | Total | Passed | Failed | Skipped | Result |
|---|---:|---:|---:|---:|---|
| API33-A `evolune-hc3-api33` | 146 | 143 | 0 | 3 | PASS |
| API33-B `Evolune_API33_Migration` | 146 | 142 | 1 | 3 | FAIL — `RealAppImeFrameProbeTest.frameLevelImeMotionAnalysis` reported field keyboard occlusion |
| API37 `Pixel_10_Pro_Fold` | 146 | 144 | 0 | 2 | PASS |

The remaining API33-B failure is unrelated to MedicationPlans and was not
altered in this scope. The focused T1-B correction is therefore validated,
while the overall automated instrumentation gate remains blocked by that
independent UI/IME regression. No live Drive or other paused RC gate was
resumed, and no tag or release was created.

JVM regression after the correction:

- `:app:testDebugUnitTest --rerun-tasks`: `BUILD SUCCESSFUL`.
- `:app:assembleDebug --rerun-tasks`: `BUILD SUCCESSFUL`.

## RC1-T2 result — API33-B IME frame-probe triage

Evidence date: `2026-08-24`. The triage branch started from
`4bb00aebcaca4376fcb601effac9bcb90c283efe`. No Kotlin, manifest, Gradle, or
test source was changed; this is a documentation-only triage record.

### Finding and classification

Primary classification: `T2-B — test/probe bug`.

The first divergence happens in `RealAppImeFrameProbeTest`'s window-root
selection, before its IME geometry assertions. `activeWindowRoot()` selects a
focused transient `840x555` root on API33-B (and a `466px`-high root on API37),
but the Compose semantics field remains in the full app window's
`boundsInWindow` coordinates. Thus `readViewHeight()` and `readImeInset()` are
not measured in the same root as `record-dose` and `record-editor-scroll`.
The resulting values (`viewHeight=555`, `ime=1307`,
`endViewportBottom=-752`) are geometrically impossible for one window.

The probe's green path is also vacuous: `imeOpenedCycles=0/5` can still yield
`occludingCycles=5/5` from the mismatched baseline, after which no cycle is
available to the bounce assertion. The intermittent root choice is recorded
as a probe synchronization symptom, not as a primary production race (`T2-D`)
or a production IME defect.

### Raw repeated matrix

| Device | Raw outcome | Evidence interpretation |
|---|---:|---|
| API33-A `evolune-hc3-api33` | 4 PASS / 1 FAIL (x5) | 0 trustworthy passes; every run used `viewHeight=555`; PASS runs had no full IME geometry, while the failure reached `endViewportBottom=-752` |
| API33-B `Evolune_API33_Migration` | 8 PASS / 2 FAIL (x10) | Only runs 1–2 were valid full-window observations (`viewHeight=2400`, `imeMax=1307`, 5/5 opened); other PASS runs were vacuous; failures included `bottom=1030 / viewport=555` and an impossible negative viewport |
| API37 `Pixel_10_Pro_Fold` | 4 PASS / 1 FAIL (x5) | 0 trustworthy passes; PASS runs were vacuous with `viewHeight=466`, `imeMax=0`, 0/5 opened; failure reached `endViewportBottom=-630` |
| API35 | NOT AVAILABLE | NOT TESTED |

Exact assertion examples:

- `Field ended occluded by the keyboard in 1/1 cycles: bottom=1030.0 viewport=555`.
- `Field bounced in 1/4 cycles: dirChanges=[1,0,0,0] motionPhases=[1,1,1,1] lateScrollAt=[-1,-1,-1,-1]`, with `endViewportBottom=-752`.

### API33-B clean-state retest

After `pm clear io.github.yingqiu0871.evolune.debug`, reinstalling both APKs,
and resetting the instrumentation process, five completed runs produced
`3` raw PASS and `2` raw FAIL. All three raw PASS runs were invalid/vacuous:
`viewHeight=555`, `imeOpenedCycles=0/5`, `occludingCycles=5/5`,
`maxImeInset=0`. The failures reproduced the same root mismatch, including
`bottom=1030 / viewport=555` and `imeOpenedCycles=1/5`, `maxImeInset=417`.
One interrupted process-reset attempt was not counted.

### Environment and live window evidence

| Device | Display / density | App bounds / cutout | IME environment |
|---|---|---|---|
| API33-A | `1080x1920` / `420` | `1080x1857`, no cutout | Gboard LatinIME, nav mode `2`, rotation `0`, animation scales `1.0` |
| API33-B | `1080x2400` / `420` | `1080x2337`, top cutout `128px` | Same Gboard LatinIME, nav mode `2`, rotation `0`, animation scales `1.0` |
| API37 | `2076x2152` / `390` | top cutout `136px` | Same Gboard LatinIME, nav mode `2`, rotation `0`, animation scales `1.0` |

API33-A/API33-B share the same API 33 model; their meaningful difference is
the display height and API33-B top cutout. The direct probe log included:

```text
windowRoots selected=com.android.internal.policy.DecorView focus=true size=840x555
all=DecorView:1080x2400:focus=false ime=1307, DecorView:840x555:focus=true ime=0
```

The manual live window dump showed MainActivity as the IME target,
`ITYPE_IME: visible`, `ADJUST_RESIZE`, `mInputShown=true`, and a served Compose
view at `0,0-1080,2400`. This separates the probe's mismatched measurement
from the actual app/IME connection.

### Manual API33-B UX validation

On the real debug app, `记录 → add menu → 手动添加` opened the editor. Focusing
`EV 剂量` showed Gboard and resized the scroll container from approximately
`[63,181][1017,2274]` to `[63,181][1017,1454]`. Typing `2` produced the
equivalent value `1.528`; saving produced a list card reading
`戊酸雌二醇 · 2.0 mg` with time/date. No crash or unsolicited second dialog
was observed. This confirms the basic editor/IME/input/save path, not the
invalid frame-level probe claim.

### Normalization and disposition

No AVD normalization experiment was run: the same wrong-root evidence was
directly reproduced on all three available devices and after API33-B
`pm clear`, so changing device variables would not distinguish the cause.
No device settings were changed.

Recommended next step is a separate test-only probe correction: keep height,
IME insets, and semantics coordinates in one application/Compose window;
require all expected IME cycles to open; reject `imeBottom > viewHeight`; and
do not accept `occludingCycles > 0` as a standalone proof. No production fix
is indicated. RC2/live-service gates, owner-device gates, release signing/R8,
tags, and releases remain paused. The project remains stopped at RC1-T2.
