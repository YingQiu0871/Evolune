# Evolune v1.2 RC1-HC4 Validation Report

## Scope and identity

- Cycle: `RC1-HC4 — Owner-device / Live-service Validation`
- Base: `4cce293ff2f298f95ecf49617b9ab5f215a62f3b`
- Branch: `v1.2/rc1-hc4-live-validation`
- Validation date: `2026-08-24`
- Target: `v1.2.0`, release state `NOT RELEASED`
- Evidence policy: current rows are independent of historical RC0/RC1 rows.
- Production and test source changes: none. This cycle records validation only.

The only repository changes for this cycle are the acceptance update and this
report. No production behavior was changed in response to validation. No tag,
GitHub Release, Play publication, or RC2 was created.

## Evidence levels and status rules

The acceptance document uses only `PASS`, `FAIL`, `NOT TESTED`, and `BLOCKED`.
Evidence levels are `UNIT`, `INSTRUMENTATION`, `EMULATOR`, `PHYSICAL DEVICE`,
`LIVE GOOGLE SERVICE`, and `SIGNED RELEASE`. A unit or emulator result does not
close a physical-device, live-service, KDF, or signed-release gate.

## Device matrix

| Target | Serial / AVD | API / Android | Display | Result |
|---|---|---|---|---|
| API33-A | `emulator-5554` / `evolune-hc3-api33` | API33 / Android 13 | 1080x1920, density 420 | instrumentation PASS: 152 total, 149 passed, 0 failed, 3 skipped |
| API33-B | `emulator-5556` / `Evolune_API33_Migration` | API33 / Android 13 | 1080x2400, density 420 | instrumentation PASS: 152 total, 149 passed, 0 failed, 3 skipped |
| API35 | `emulator-5559` / `evolune-hc3-api35` | target API35 | target remained offline | NOT TESTED; no result promoted |
| API37 Fold | `emulator-5558` / `Pixel_10_Pro_Fold` | API37 / Android 17 | 2076x2152, density 390 | instrumentation PASS: 152 total, 150 passed, 0 failed, 2 skipped |

API33 targets had GMS packages. API37 had GMS plus
`com.google.android.healthconnect.controller` and
`com.google.android.healthconnect.backuprestore`. API35 was attempted but
remained `offline`; it was not treated as a pass.

## Instrumentation and T2 probe

The final `:app:connectedDebugAndroidTest --rerun-tasks` completed successfully
on all three online targets. JUnit XML under
`app/build/outputs/androidTest-results/connected/debug` is authoritative for
the per-device counts above. The offline API35 target was skipped by Gradle.

`RealAppImeFrameProbeTest` was also run as a focused probe on each online
device, with one passing test per device. The final verdicts were:

- API33-A: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1307px`, application root `1080x1920`,
  `bouncingCycles=0/5`.
- API33-B: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1307px`, application root `1080x2400`,
  `bouncingCycles=0/5`.
- API37 Fold: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1096px`, application root `2076x2152`,
  `bouncingCycles=0/5`.

The probe used the application decor/content root and the same coordinate
system for IME insets. No invalid auxiliary-root green or coordinate-space
mismatch was observed.

## HC4 Settings UX and provider behavior

On API33-B, the Settings page showed the `Health Connect 同步` entry, no old
Read/Preview/Use UI, a weight-sync row, and the medication row disabled with
`暂未开放，计划在 v1.8 评估`. Because this AVD had no Health Connect provider
package, the app resolved the state to unavailable and displayed the stable
provider-missing message. This is emulator evidence for the missing-provider
path, not installed/current-provider evidence.

On API37 Fold, the same entry and disabled medication placeholder were visible.
The installed/current Health Connect controller was used for the real
permission flow. After grant, the app showed connected state and
`最近 30 天没有新的体重记录`.

## HC4 permission lifecycle

The API37 Fold lifecycle was exercised with the installed debug application
`io.github.yingqiu0871.evolune.debug`:

1. With `READ_WEIGHT` not granted, the first user OFF→ON action opened one
   Health Connect permission request.
2. Denial returned to Evolune with the switch off and a permission-required
   state. The local displayed weight remained unchanged.
3. A second user-initiated ON action reopened the request. Selecting weight and
   allowing it produced connected state, enabled sync, and no-data status.
4. The official `android.health.connect.action.MANAGE_HEALTH_PERMISSIONS`
   page was used to revoke the permission: body measurements changed from one
   selected item to zero. Returning to Evolune produced permission-required
   state, retained the user-intent switch, and did not automatically open a
   dialog.
5. After force-stop/relaunch, no unsolicited permission request appeared. A
   later explicit switch action opened one request again; granting it restored
   connected state and the enabled switch.

No WeightRecord was present, and the UI remained on the no-data path. The
source audit found only `READ_WEIGHT`; there is no `WRITE_WEIGHT`, background
Health Connect, or history permission in the production path. No local body
weight write was observed during this lifecycle.

## Real WeightRecord, adoption, watermark, and restart

The API37 Health Connect official home/data-management views were inspected.
They showed no data and no legal manual WeightRecord-entry path or existing
test health app with an appropriate write permission. No record was fabricated,
Evolune was not given `WRITE_WEIGHT`, and the real-record gate remains
`NOT TESTED`.

Consequently the following real-record gates remain open: preview of B,
non-mutating preview, explicit adoption, PK recalculation, R1/manual-edit/R2
watermark freshness, adopted-weight persistence, and stale-record protection.

The no-record restart portion was observed: after reauthorization and
force-stop/relaunch, the enabled preference and connected/no-data state were
still displayed. This does not close adopted-weight or watermark persistence,
which require a real WeightRecord.

## Fold UI and B4 smoke

The Pixel 10 Pro Fold AVD was tested in the official `CLOSED` state
(`1080x2364`) and `OPENED` state (`2076x2152`). The HC4 page showed its status,
permission, weight, and disabled-medication rows in both states. The switch
was visible and operable, the page was scrollable, and no button was clipped.
The reversible rotation/size probe did not produce an independent landscape
window; landscape is therefore `NOT TESTED` rather than inferred from the
opened state.

The B4 Settings page showed the `备份与恢复` entry and `立即备份` /
`从备份恢复` actions. The live action was not executed because the external
upload gate below was not approved for this run.

## Google OAuth and live Drive

GMS was present on the API37 Fold (`pm path com.google.android.gms` returned
installed APK paths). Production source inspection found only
`https://www.googleapis.com/auth/drive.appdata`; no `drive.file`,
`drive.readonly`, offline access, server-auth-code, or refresh-token path was
found.

The live OAuth/upload gate is `BLOCKED — EXTERNAL GOOGLE CLOUD CONFIGURATION`.
There was no approved Google Cloud OAuth/test-account service session in the
workspace, and a real backup would upload local application data to an
external destination. The live action was not attempted through a workaround.
Therefore no live authorization, app-data create/readback, list, bounded
download, retention, or disconnect result is claimed. This is an external
configuration/data-boundary blocker, not a production-code failure.

The same block leaves live synthetic-data A→B→A backup/restore, HC device-local
preference semantics after restore, post-restore PK/reminders/widgets, and
live retention evidence open. Existing local fake-provider/unit evidence is
not promoted to live evidence.

## Physical device, KDF, signing, R8, and Wear

No physical Android or physical Wear device was available. The physical HC
owner gate, physical KDF gate, Wear runtime/Data Layer gate, and large-history
device sanity remain `NOT TESTED`.

The four release signing variables were unset:
`EVOLUNE_KEYSTORE_PATH`, `EVOLUNE_KEYSTORE_PASSWORD`, `EVOLUNE_KEY_ALIAS`,
and `EVOLUNE_KEY_PASSWORD`. `:app:signingReport` reported a null release
configuration; no release APK was generated. App/Wear signed release,
minified/R8 runtime, `apksigner` verification, certificate comparison, and
signed smoke remain `BLOCKED`.

The debug application identity and fingerprints recorded without secrets were:

- applicationId: `io.github.yingqiu0871.evolune.debug`
- SHA-1: `9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B`
- SHA-256: `2C:F0:A5:0C:33:B5:40:4A:85:36:3E:42:DC:15:78:1C:5C:7E:4C:36:BB:5F:0E:8C:C0:4A:0E:5D:26:AD:A7:72`

Wear unit tests and debug assemble passed. This is not signed Wear runtime
evidence.

## Final automated regression

The final local regression completed successfully on `2026-08-24`:

| Task | Result |
|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL |
| `:experience-core:test` | BUILD SUCCESSFUL |
| `:wear:testDebugUnitTest` | BUILD SUCCESSFUL |
| `:app:assembleDebug --rerun-tasks` | BUILD SUCCESSFUL |
| `:wear:assembleDebug` | BUILD SUCCESSFUL |
| `:app:connectedDebugAndroidTest --rerun-tasks` | BUILD SUCCESSFUL; three online device matrices above |

Known compiler deprecation/warning output was unchanged repository output;
no production failure or RC1 blocker was observed in this validation cycle.

## Newly closed versus remaining gates

Newly closed at the emulator/instrumentation evidence level:

- full instrumentation on all three online AVDs;
- the focused T2 frame/IME probe on those devices;
- API33 provider-missing Settings behavior;
- API37 installed-provider no-data Settings behavior;
- API37 permission enable, deny, retry, grant, system revoke, explicit
  reauthorization, and no-automatic-dialog-after-relaunch behavior;
- API37 Fold CLOSED/OPENED HC4 layout sanity.

Remaining release blockers are API31/32 and update-required HC evidence,
physical-device HC/WeightRecord/watermark/KDF evidence, live Google OAuth and
Drive E2E/retention/disconnect, any required landscape owner evidence, signed
minified App/Wear release and R8 smoke, and final owner review.

No production code was modified. No tag or release was created. The protected
`D:\Evolune` root and the old linked worktree
`D:\Evolune-Workspace\current\Evolune` were not touched.
