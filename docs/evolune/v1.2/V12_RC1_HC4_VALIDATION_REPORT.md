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

## RC1-R2B — Live Google Drive Validation

### Scope and lineage

This is a docs-only post-HC4 validation follow-up. The approved RC1 base was
`da2c0142a8de40735ef52ae7a14948ef716cffee`, and the validation branch was
`v1.2/rc1-r2b-live-google-drive`. The separate R2A API35 triage commit was
`b8ad70ea7dad2feab79407848ab1195078a63e24` (docs-only, sibling lineage); R2B
was intentionally based on the approved RC1 commit rather than guessing a new
ancestry. No Kotlin, Java, XML, Manifest, Gradle, or test source was modified.

### API37 Fold and Google account evidence

| Item | Evidence |
|---|---|
| Target | `Pixel_10_Pro_Fold`, `emulator-5554`, API37 / Android 17 |
| Display | 2076x2152, density 390, font scale 1.0 |
| Package | `io.github.yingqiu0871.evolune.debug` |
| Debug SHA-1 | `9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B` |
| GMS | `pm path com.google.android.gms` returned installed APK paths |
| Account | GMS account chooser displayed the owner-provided test account; report-masked as `g***@gmail.com` |
| Service evidence level | `LIVE GOOGLE SERVICE` for the real AuthorizationClient flow; no token value was logged or persisted in the report |

The account chooser displayed `Evolune` requesting access, the test account
was selected, and Evolune resumed with `已连接（当前会话）`. No
`DEVELOPER_ERROR` or error code 10 was observed. This is authorization-flow
evidence only; it does not prove a Drive file write.

### OAuth scope and isolation audit

The production contract/factory audit found only
`https://www.googleapis.com/auth/drive.appdata`. No `drive`, `drive.file`,
`drive.readonly`, offline access, refresh token, or server-auth-code path was
found. The intended isolation semantics are per-user Google Drive
`appDataFolder`, hidden from normal My Drive UI, Evolune-only, and
client-side-encrypted. No Evolune server storage is claimed.

### Gate results

Every R2B gate uses exactly one of `PASS`, `FAIL`, `BLOCKED`, or `NOT TESTED`.

| Gate | Status | Evidence level and result |
|---|---|---|
| Real OAuth authorization | `PASS` | `LIVE GOOGLE SERVICE` — account chooser completed and app returned connected |
| `drive.appdata`-only scope audit | `PASS` | `LIVE GOOGLE SERVICE` — source contract/factory audit; no broader or offline scope path |
| First live upload | `FAIL` | `LIVE GOOGLE SERVICE` — synthetic non-sensitive state and passphrase were submitted; app returned `备份上传失败` |
| Readback byte/SHA verification | `NOT TESTED` | `LIVE GOOGLE SERVICE` — no verified generation after upload failure |
| List/download/decrypt/preview | `NOT TESTED` | `LIVE GOOGLE SERVICE` — stopped before remote generation selection |
| G1/G2/G3/G4 retention | `NOT TESTED` | `LIVE GOOGLE SERVICE` — no generation sequence or deletion pass attempted |
| Disconnect/reauthorization | `NOT TESTED` | `LIVE GOOGLE SERVICE` — stopped before disconnect |
| A→B→A preview and destructive restore | `NOT TESTED` | `LIVE GOOGLE SERVICE` — no download or restore reached |
| HC4 device-local preference after restore | `NOT TESTED` | `LIVE GOOGLE SERVICE` — no restore reached |

The first upload failure is an `RC1_BLOCKER` under the requested policy:
authorization had succeeded, but the app’s real backup action did not complete.
The user-visible result was the stable generic `备份上传失败`; no fileId,
remote generation, readback hash, or remote HTTP response category was
available to promote. A read-only diagnostic TCP probe to
`www.googleapis.com:443` and `oauth2.googleapis.com:443` succeeded from the
Fold, but this is not proof of a successful authenticated Drive write. The
run therefore stopped without retrying or changing production code.

### Regression result and stop condition

The requested fresh regression could not start a new trustworthy result in
this environment. `:app:assembleDebug --rerun-tasks` reached dependency
resolution and failed on a TLS handshake while resolving
`com.android.tools.build:builder-test-api:9.0.1`; the later
`:app:testDebugUnitTest --rerun-tasks` was blocked by Gradle 9.2.1 distribution
download `SocketException: Permission denied: getsockopt`. Previously accepted
RC1-HC4 unit/build results remain historical and are not counted as R2B reruns.

No production or test source change was made, no diagnostic source diff was
left behind, and no tag/release or RC2 was created. R2B stops here with the
live-upload `RC1_BLOCKER` and the pre-existing release/device gates still
open.

## RC1-R2B-T1 — Live Google Drive upload failure triage

### Scope and lineage

This is a docs-only triage branch from R2B commit
`c68bf920ee3b6ee336c1597d413b2235463ebb15`:
`v1.2/rc1-r2b-t1-drive-upload-triage`. The approved RC1 lineage remains
`da2c0142a8de40735ef52ae7a14948ef716cffee`. No Kotlin, Java, XML, Manifest,
Gradle, unit-test, or instrumentation-test source was modified. No temporary
diagnostic source change remains.

### Live service repeat matrix

The API37 Fold target was `Pixel_10_Pro_Fold` / `emulator-5554`, Android 17,
2076x2152, density 390, font scale 1.0. The installed package was
`io.github.yingqiu0871.evolune.debug` with owner-confirmed debug SHA-1
`9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B`. GMS was
present. The real AuthorizationClient account flow completed for the
owner-provided test account, masked here as `g***@gmail.com`, and the app
showed `已连接（当前会话）`. Evidence level: `LIVE GOOGLE SERVICE`.

| Attempt | Result | Exact observable |
|---|---|---|
| 1 | `FAIL` | Backup submission ended with `备份上传失败`; no HTTP/error line or fileId in sanitized logcat |
| 2 | `FAIL` | Same target and flow; same UI result; no HTTP/error line or fileId |
| 3 | `FAIL` | Same target and flow; same UI result; no HTTP/error line or fileId |

The passphrase and encrypted data were synthetic/non-sensitive and are omitted
from this report. No token value or full account address was logged. The
repetition proves a stable live failure, but not its remote cause.

### Failure-stage determination

| Stage | Result | Evidence |
|---|---|---|
| Snapshot | `UNKNOWN` | No stage telemetry in the installed build |
| B1 encode | `UNKNOWN` | A non-invalid encode failure maps to the same `BACKUP_UPLOAD_FAILED` code as create failure |
| Authorization | `PASS` | Real account chooser and connected-session UI completed |
| Multipart create/upload | `UNKNOWN` | No request/response log, status, reason, or exception was emitted |
| Readback | `UNKNOWN` / not observable | Provider enters it only after create success; no marker or fileId was exposed |
| SHA verification | `NOT OBSERVED` | No verified generation or readback bytes were available |

The first divergent observable is therefore the final UI state, not a proven
HTTP stage. The current UI distinguishes verification failure from upload
failure, but the upload text still conflates a non-invalid B1 encode failure
with the provider `UPLOAD_FAILED` path. The adapter also reads and discards
the non-2xx error stream, so the exact exception, HTTP status, Google reason,
and Google message are all `UNKNOWN / not emitted`.

### REST, metadata, token, and payload audit

The source-level request is a Drive v3 multipart upload:

`POST https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,size,appProperties`

with `Content-Type: multipart/related`, an encrypted-byte part, and metadata
equivalent to:

```json
{
  "name": "<generated Evolune backup name>",
  "mimeType": "application/octet-stream",
  "parents": ["appDataFolder"],
  "appProperties": {
    "evoluneKind": "backup",
    "backupFormat": "native",
    "envelopeFormatVersion": "<version>",
    "payloadSchemaVersion": "<version>",
    "backupCreatedAt": "<createdAt>",
    "contentSha256": "<sha256>"
  }
}
```

`AuthorizationClient` returns an access token and the source passes it to the
REST gateway as `Authorization: Bearer <token>`; source-level evidence is
`tokenPresent=YES`, while live token value and length were intentionally not
recorded. The live encrypted payload byte length and resulting HTTP request
content length are `UNKNOWN`; no zero-byte claim is made. No fileId was
available, so remote creation is `UNKNOWN` and readback attempted is
`UNKNOWN`. No explicit post-blocker diagnostic list, download, retention,
disconnect, or restore call was made. Any provider best-effort cleanup that
could be triggered by an unobserved failed upload/readback path is likewise
not observable from this build.

The owner configuration says Google Drive API is enabled. This run cannot
promote that to a live API-acceptance result because no HTTP status or Google
reason/message was observable. Fold TCP probes to `www.googleapis.com:443` and
`oauth2.googleapis.com:443` succeeded, but they do not distinguish an
authenticated Drive error from a successful request.

### Classification and next narrow action

Primary classification: **`T1-C — Production Drive provider/upload path
blocker (provisional)`**. The failure is repeatable after real authorization in
the provider-facing backup path. T1-A is not asserted without a concrete
Google Cloud error; T1-B is not asserted without a DNS/TLS/connectivity
failure; T1-D is not supported because create/readback success is unproven;
T1-E is not supported because no 5xx/429 response exists. The endpoint and
`parents: ["appDataFolder"]` metadata shape are correct by source audit, so
this classification does not claim a malformed request as the concrete root
cause.

The minimal next action is a separate narrow diagnostic/fix decision to retain
sanitized HTTP status/reason and an upload-stage marker for encode, create,
readback, and SHA verification, then repeat one live upload. Until that exists,
the exact create-versus-encode root cause is unresolved and all later Drive
gates remain paused.

### Stop state

The first live upload remains `FAIL` and an `RC1_BLOCKER`; readback/list/
download, G1–G4 retention, disconnect/reauthorization, and A→B→A remain
`NOT TESTED`. No production diff, diagnostic diff, tag, release, or RC2 was
created.

## RC1-R2B-T2 — Live Drive failure observability and stage isolation

### Scope and environment result

This docs-only T2 attempt is based on
`ad3c5f62ee3723c390b8ed5cf74ae8d7d3786c54` and branch
`v1.2/rc1-r2b-t2-drive-observability`. The target was the API37
`Pixel_10_Pro_Fold` / `emulator-5554`, package
`io.github.yingqiu0871.evolune.debug`. Temporary diagnostic logging compiled
successfully in `:app:assembleDebug --rerun-tasks`, and the debug APK installed
successfully. No diagnostic source remains.

After installation, the first real authorization attempt opened Google Play
Services' `登录 - Google 账号` screen and required an account to be added to
the device. The owner test account was not available as an active signed-in
session in this run. No credentials were entered or guessed, and the account
address was not recorded. Because AuthorizationClient did not return an access
token, no backup operation reached S0.

### S0–S13 stage map

| Stage | File | Class / method | Current T2 evidence |
|---|---|---|---|
| S0 Room/DataStore snapshot | `backup/LocalBackupSnapshotSource.kt` | `RestorePersistenceSnapshotSource.capture` | `BLOCKED` before invocation |
| S1 B1 payload construction | `backup/LocalBackupSnapshotSource.kt` | `capture`; `room.toPayload(settings)` | `NOT TESTED` |
| S2 canonical encode | `backup/EvoluneBackupCodec.kt` | `EvoluneBackupCodec.encode`; `canonicalPayloadBytes` | `NOT TESTED` |
| S3 PBKDF2 + AES-GCM encrypt | `backup/EvoluneBackupCodec.kt` | `encode`; `deriveKey`; `Cipher.doFinal` | `NOT TESTED` |
| S4 AuthorizationClient/token | `backup/cloud/google/GoogleAuthorizationGateway.kt` and `backup/BackupRestoreCoordinator.kt` | `authorize`; `AuthorizationClient.authorize` | `BLOCKED` at GMS account sign-in; no token returned |
| S5 multipart metadata/body | `backup/cloud/google/HttpUrlConnectionDriveRemoteGateway.kt` | `createFile`; `multipartBody` | `NOT TESTED` |
| S6 HTTP send | same gateway | `executeJson`; `configure` | `NOT TESTED` |
| S7 Drive files.create response | same gateway | `executeJson`; `createFile` | `NOT TESTED` |
| S8 fileId parse | same gateway | `parseMetadata`; provider `toGeneration` | `NOT TESTED` |
| S9 readback request | `backup/cloud/google/GoogleDriveBackupProvider.kt` and gateway | `uploadBackup`; `openDownload` | `NOT TESTED` |
| S10 readback response | same provider/gateway | `readDownload`; `openDownload` response handling | `NOT TESTED` |
| S11 SHA/byte verification | `backup/cloud/google/GoogleDriveBackupProvider.kt` | `uploadBackup`; `MessageDigest.isEqual` | `NOT TESTED` |
| S12 retention/list | same provider | `pruneOldGenerations`; `listBackups` | `NOT TESTED` |
| S13 success/UI | `backup/BackupRestoreCoordinator.kt`, `BackupRestoreViewModel.kt`, `ui/components/BackupRestoreSection.kt` | `createBackup`; `BackupSuccess`; `settings_backup_error_upload` mapping | `NOT TESTED` |

The UI text `备份上传失败` can cover a non-invalid S2/S3 encode failure or
the provider `UPLOAD_FAILED` mapping. It was not shown in this T2 run because
the authorization precondition stopped the flow first. The earlier T1 UI
result remains the only live upload failure evidence.

### Three-attempt diagnostic matrix

| Stage | Attempt 1 | Attempt 2 | Attempt 3 |
|---|---|---|---|
| S0 snapshot | `BLOCKED` — not reached | `NOT TESTED` | `NOT TESTED` |
| S1 payload | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S2 encode | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S3 encrypt | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S4 authorization | `BLOCKED` — GMS login screen | `BLOCKED` — same precondition, not run | `BLOCKED` — same precondition, not run |
| S5 multipart | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S6 HTTP send | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S7 create response | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S8 fileId | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S9 readback request | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S10 readback response | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |
| S11 SHA verify | `NOT TESTED` | `NOT TESTED` | `NOT TESTED` |

### Exception, HTTP, and request evidence

There was no application exception, cause chain, HTTP status, Google reason,
or sanitized Google message in this T2 run: the GMS account screen prevented
AuthorizationClient from returning to the app. Consequently:

- first failing stage: `S4` precondition blocked; no Drive stage was reached;
- exception: `NONE OBSERVED`;
- HTTP status / Google reason / message: `NONE — no request was sent`;
- endpoint, method, metadata, and `parents: ["appDataFolder"]`: source audit
  only; no live request was constructed;
- payload bytes and request content length: `NOT TESTED`;
- token present: `NO LIVE TOKEN`; AuthorizationClient did not return one;
- remote file created: `NO EVIDENCE / NOT TESTED`;
- readback attempted: `NO / NOT TESTED`.

The temporary diagnostic logs were designed to record only stage, exception
class, sanitized message, HTTP code/reason, byte lengths, token presence and
fileId presence. They emitted no S0–S13 stage event because the app did not
get past authorization. No token, passphrase, key, plaintext, medication data,
full email, or raw backup bytes were logged.

### Classification and next action

The sole T2 classification is **`T2-B — Environment/network`**, narrowed to a
missing signed-in owner test-account precondition on the Fold. This is a direct
environment observation from the GMS `登录 - Google 账号` screen. It does not
classify the prior T1 upload failure, and no evidence supports T2-A, T2-C,
T2-D, T2-E, T2-F, or T2-G in this run.

The minimal next action is owner-side: restore/sign in the approved Google test
user on `emulator-5554`, then rerun the same temporary diagnostics three times
without changing scopes or production behavior. Until that happens, the
original Drive blocker remains unresolved and all post-upload gates stay
paused.

### Stop and cleanup state

Temporary production diagnostics were reverted; `app/src/main/**`,
`app/src/test/**`, and `app/src/androidTest/**` have zero diff. No production
behavior, provider contract, OAuth scope, test code, retention, delete,
disconnect, reauthorization test, restore, RC2, tag, or release was changed.

## RC1-R2B-T3 — Post-account-recovery Drive stage isolation

### Scope and prerequisite result

T3 was started from `4578d6dc30ed934bd835af83c392ee768f156e60` on branch
`v1.2/rc1-r2b-t3-drive-stage-resume`, targeting API37
`Pixel_10_Pro_Fold` / `emulator-5554`. The requested account prerequisite was
not present at execution time: a sanitized account check returned
`accountSessionPresent=false`, and the top activity was still the Google Play
Services account sign-in flow. GMS and Play Store packages were available. No
credentials were entered, requested, or guessed.

The T3 rule says to stop if the Fold is still at Google login. Therefore no
temporary stage instrumentation was applied, no backup was submitted, and no
Drive request was made.

### T3 stage result

| Stage | Result | Evidence |
|---|---|---|
| S0 Room/DataStore snapshot | `NOT TESTED` | stopped before backup |
| S1 B1 payload construction | `NOT TESTED` | stopped before backup |
| S2 canonical encode | `NOT TESTED` | stopped before backup |
| S3 PBKDF2 + AES-GCM encrypt | `NOT TESTED` | stopped before backup |
| S4 AuthorizationClient/token | `BLOCKED` | GMS account sign-in flow; `accountSessionPresent=false` |
| S5 multipart metadata/body | `NOT TESTED` | no token/request |
| S6 HTTP send | `NOT TESTED` | no request |
| S7 Drive files.create response | `NOT TESTED` | no request |
| S8 fileId parse | `NOT TESTED` | no response |
| S9 readback request | `NOT TESTED` | no fileId |
| S10 readback response | `NOT TESTED` | no readback |
| S11 SHA/byte verification | `NOT TESTED` | no readback |
| S12 retention/list | `NOT TESTED` | explicitly out of scope |
| S13 success/UI | `NOT TESTED` | no backup submission |

No first failing Drive stage exists for this run. Exception, cause chain,
HTTP status, Google reason, sanitized Google message, endpoint, parents,
encrypted bytes, token presence, remote file creation, and readback are all
`NOT OBSERVED` because S4 did not return an authorization result. The previous
T1 three-attempt upload failure remains unresolved; it is not relabeled from
the new T3 evidence.

### T3 classification and next action

The sole T3 classification is **`T3-B — Environment/network`**, narrowly for
the missing active Google test-account session on the Fold. This does not claim
that the original Drive upload was a network failure and provides no evidence
for T3-A, T3-C, T3-D, T3-E, T3-F, or T3-G.

The owner must restore/sign in the approved Google test account on
`emulator-5554`. After the account check returns true and the GMS flow no
longer shows login, rerun the same temporary S0–S11 diagnostics and three live
backup attempts. No scope expansion or production fix is authorized by this
result.

### Stop state

No production or test source was changed; no diagnostic source diff exists.
Retention, delete, G1–G4, disconnect, reauthorization testing, A→B→A, UI1,
RC2, tag, and release remain paused.

## RC1-R2B-T4A — Authorization state discrepancy

### Device, account, and installed identity

T4A started from `2a1e7ed84d09f57812b30dde926fb3e18fc28f99` on branch
`v1.2/rc1-r2b-t4a-auth-state-triage`. The target was identified as adb serial
`emulator-5558`, AVD `Pixel_10_Pro_Fold`, product
`sdk_gphone16k_x86_64`, Android 17 / API 37. The current Android user/profile
was user `0`; `pm list users` showed only that running user.

Read-only account evidence found one Google account under user 0. The account
was recorded only as `g***@gmail.com`; its full address was not written to the
report. Play Store was also foreground and showed the signed-in account avatar.
The account and both Evolune packages were installed for the same user 0.

The actual installed production APK was pulled only for certificate inspection:

| Field | Observed |
|---|---|
| package | `io.github.yingqiu0871.evolune.debug` |
| version | `1.2.0-debug` |
| installed SHA-1 | `9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B` |
| expected OAuth SHA-1 | `9F:FD:E8:2F:21:6E:C5:06:BD:CE:BC:3D:B2:4F:BB:62:82:A5:85:0B` |
| installed SHA-256 | `2C:F0:A5:0C:33:B5:40:4A:85:36:3E:42:DC:15:78:1C:5C:7E:4C:36:BB:5F:0E:8C:C0:4A:0E:5D:26:AD:A7:72` |
| expected OAuth SHA-256 | `2C:F0:A5:0C:33:B5:40:4A:85:36:3E:42:DC:15:78:1C:5C:7E:4C:36:BB:5F:0E:8C:C0:4A:0E:5D:26:AD:A7:72` |
| identity match | `true` |

The installed instrumentation package was
`io.github.yingqiu0871.evolune.debug.test`, targeting the debug app and using
the same debug certificate. No release package was observed. T4A did not
uninstall, reinstall, clear package data, remove the account, reset GMS, or
wipe the AVD.

### AuthorizationClient evidence

One real production attempt was started from Settings → Backup & Restore →
立即备份. The foreground app stayed in Evolune and immediately displayed the
local backup-passphrase dialog; no Google chooser, consent, sign-in, or
reauthentication UI appeared. Source audit confirms the coordinator calls
`authorize()` before displaying that local dialog. After the dialog was
cancelled, the settings screen reported `已连接（当前会话）`.

| Item | Result |
|---|---|
| Initial result | `success / Authorized` |
| `hasResolution` | `false`; no pending intent launched |
| Resolution UI | `NONE` |
| Existing account offered | Not applicable; Google UI did not appear |
| Password actually required | `false` for Google; the visible prompt was only the local backup passphrase |
| Post-resolution result | Not applicable |
| GMS status/error | No `ApiException`, status code, message, or cause observed |
| tokenPresent | `true` by the authorized result/connected state; token value not recorded |

The local passphrase was cancelled before submission. Consequently T4A made no
Drive request, created no encrypted payload, and produced no remote file,
readback, HTTP evidence, or upload-stage evidence.

### Classification and correction

Final classification: **`B — account exists, normal OAuth authorization state
was previously misclassified`**. The system account is present, the Android
user/profile matches the installed app, the installed signer matches the
configured OAuth client, and the current Fold's production call is already
authorized.

The T3 claim that the Fold lacked an active system account is rejected. A
Google login/account UI proves only that resolution may be required; it does
not prove that a system account is absent. Because emulator serials can change,
the historical `emulator-5554` label is not treated as durable device identity
and no new historical device claim is made.

No production or test source was changed. No temporary source diagnostics were
needed or applied in T4A; pulled certificate-inspection APKs and temporary UI
dumps were removed after inspection. Drive upload, retention, disconnect,
reauthorization, restore, A→B→A, RC2, tag, and release remain paused.

## RC1-R2B-T4B — Live Drive S0–S11 stage isolation

### Scope and call order

T4B started from 3ad9b146a30d5e47a6fe08394216722464e82dd8 on branch
v1.2/rc1-r2b-t4b-drive-live-isolation, using the API37
Pixel_10_Pro_Fold under Android user 0. The test state was synthetic and
non-sensitive: one medication plan, one scheduled slot, zero dose events, and
body weight 55.0 kg. A disposable RC-only passphrase was entered on-device and
was not recorded in logs or this report.

The actual production order was passphrase validation, then S4 authorization,
then S0/S1 snapshot and B1 payload, S2 native canonical encoding, S3 PBKDF2 +
AES-256-GCM encryption, S5 multipart construction, S6 HTTP setup/send, S7
response, S8 fileId, S9 readback, S10 response, and S11 byte/SHA verification.
This reflects the call graph; S4 is not assumed to occur after S0 merely because
of its logical stage number.

### Three live attempts

| Stage | Attempt 1 | Attempt 2 | Attempt 3 |
|---|---|---|---|
| S0 authoritative snapshot | PASS — plans=1, slots=1, events=0 | PASS | PASS |
| S1 B1 payload | PASS | PASS | PASS |
| S2 canonical/native encode | PASS — 610 canonical bytes | PASS | PASS |
| S3 PBKDF2 + AES-GCM | PASS — 1225 encrypted envelope bytes | PASS | PASS |
| S4 AuthorizationClient/token | PASS — token present, length=324 | PASS | PASS |
| S5 multipart request | PASS — POST multipart, parents=[appDataFolder], media=1225 bytes | PASS | PASS |
| S6 HTTP request | FAIL — NetworkOnMainThreadException | FAIL — same | FAIL — same |
| S7 create response | NOT REACHED | NOT REACHED | NOT REACHED |
| S8 fileId | NOT REACHED | NOT REACHED | NOT REACHED |
| S9 readback request | NOT REACHED | NOT REACHED | NOT REACHED |
| S10 readback response | NOT REACHED | NOT REACHED | NOT REACHED |
| S11 bytes/SHA verification | NOT REACHED | NOT REACHED | NOT REACHED |

S4 was Authorized in all three attempts. The constructed endpoint was
https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,createdTime,size,appProperties;
the method was POST, multipart boundary/content type was constructed,
parents was ["appDataFolder"], media length was 1225 bytes, and the
Authorization header was present.

### First divergence and evidence boundary

The first divergence was S6 during the blocking connection/request-body setup
inside HttpUrlConnectionDriveRemoteGateway.executeJson → configure. The exact
exception was NetworkOnMainThreadException; cause class and message were
absent. Because the exception occurred before responseCode, no HTTP request was
sent. HTTP status, Google error.code, error.message, errors[].reason, and
sanitized error body are therefore NOT OBSERVED.

No fileId was returned, no remote file was created, readback was not attempted,
downloaded bytes are not applicable, and expected/actual SHA-256 are not
applicable. Retention/list/delete was not exercised; the temporary diagnostic
build bypassed S12 and that bypass was reverted before cleanup.

### Classification and exact production finding

Classification: T4B-F — B4 orchestration/error-mapping bug.

The exact reachable path is:

1. BackupRestoreViewModel.submitBackupPassphrase launches in the default
   viewModelScope, whose dispatcher is Main.
2. BackupRestoreCoordinator.createBackup calls
   GoogleDriveBackupProvider.uploadBackup on that context.
3. HttpUrlConnectionDriveRemoteGateway.executeJson, through its blocking
   configure request-body write, runs on Main and throws
   NetworkOnMainThreadException.
4. The failure is mapped to the generic BACKUP_UPLOAD_FAILED /
   备份上传失败 result.

The minimal next action is a separate narrow RC fix that dispatches blocking
Drive I/O to Dispatchers.IO at the chosen orchestration/transport boundary,
plus a regression test that exercises the production path off Main and checks
stable error mapping. T4B did not modify or fix production behavior.

The original T1 generic upload failure is reproducible 3/3, with the concrete
first failing stage now identified. Temporary diagnostics and the temporary
retention bypass were reverted; a clean debug APK was rebuilt and reinstalled.
No production/test source diff remains. Drive retention, disconnect,
reauthorization, A→B→A, UI1, RC2, tag, and release remain paused.

## RC1-F1 — Drive blocking I/O dispatcher fix

F1 was executed from T4B base
`0d7670c82b5c87b23cb3088894e63fa2ea59e5f3` on
`v1.2/rc1-f1-drive-io-dispatcher`. The T4B root cause was a blocking
`HttpURLConnection` request-body write on the Main dispatcher:

`BackupRestoreViewModel.submitBackupPassphrase` →
`BackupRestoreCoordinator.createBackup` →
`GoogleDriveBackupProvider.uploadBackup` →
`HttpUrlConnectionDriveRemoteGateway.executeJson/configure`.

### Narrow production change

`HttpUrlConnectionDriveRemoteGateway` now accepts an injected
`CoroutineDispatcher` whose production default is `Dispatchers.IO`. Its
complete synchronous JSON HTTP boundary covers connection creation,
configuration, request-body output, `responseCode`, JSON/error-stream reads,
response-body reads, and disconnect. `openDownload` uses the same boundary for
connection setup and response acquisition. Because the gateway returns a
network stream for bounded provider-side validation, `GoogleDriveBackupProvider`
also confines the actual bounded download reads and close to its injected IO
dispatcher. The ViewModel remains lifecycle-aware on its existing scope; no
provider-neutral model, B1/B2 protocol, encryption, authorization, or error
mapping was changed.

### Focused and regression tests

- `GoogleDriveRestGatewayTest.all blocking gateway operations run on injected io dispatcher`
  runs create/upload, metadata GET, list, download open, and delete from a
  Main-like executor and asserts observed connection work uses the injected IO
  executor.
- `GoogleDriveBackupProviderTest.download body is consumed on injected io dispatcher`
  asserts the returned network stream is consumed on the injected IO executor.
- Existing Drive provider tests continue to cover upload/readback verification,
  retention, upload serialization, pagination loops, SHA mismatch, bounded
  downloads, and 401 retry behavior.

### API37 live result

Device: `Pixel_10_Pro_Fold`, API 37 / Android 17, adb serial
`emulator-5558`, user 0, debug package
`io.github.yingqiu0871.evolune.debug`. Device facts were 2076×2152, density
390, font scale 1.0, gesture navigation, default Latin IME, animation scales
1.0, and current rotation value 1. The clean debug APK was installed after
package-data clear. The counted synthetic state had no medication plans or
scheduled slots, zero dose events, and body weight 55.0 kg.

| Stage | #1 | #2 | #3 |
|---|---|---|---|
| S0 snapshot | PASS | PASS | PASS |
| S1 B1 payload | PASS | PASS | PASS |
| S2 encode | PASS | PASS | PASS |
| S3 encrypt | PASS | PASS | PASS |
| S4 authorization | PASS — test account selected/authorized | PASS — authorized session | PASS — authorized session |
| S5 multipart | PASS | PASS | PASS |
| S6 HTTP send | PASS — no Main-thread exception | PASS | PASS |
| S7 create response | PASS | PASS | PASS |
| S8 fileId | PASS — subsequent readback path completed | PASS | PASS |
| S9 readback request | PASS | PASS | PASS |
| S10 response | PASS | PASS | PASS |
| S11 SHA/byte verification | PASS | PASS | PASS |

All three valid attempts ended with the production UI `备份已完成`. The
per-attempt logcat checks contained no `NetworkOnMainThreadException`, fatal
exception, or Drive I/O error. No temporary production diagnostics were added;
the stage matrix uses the successful end-to-end result plus the existing
production call order, with S6 additionally checked by sanitized logcat.

F1 result: `NetworkOnMainThreadException = CLOSED`; `S0–S11 complete PASS`
3/3. No new first failing stage was observed. The earlier T4B S6 failure is
closed, and no post-fix HTTP/readback blocker was exposed.

The requested sanity checks were also green on the same API37 Fold:
`HealthConnectSyncScreenTest` 6/6 and `RealAppImeFrameProbeTest` 1/1. No
large-scale HC4 or T2 triage was repeated.

Retention G1–G4, disconnect/reauthorization preservation, A→B→A restore,
Health Connect device-local preference after restore, UI1, RC2, tag, and
release remain paused. The remaining release gates are the explicitly tracked
device/Health Connect evidence, release signing/R8/signed smoke, broader live
Drive acceptance, and final owner review.

## RC1-F2 — Backup crypto CPU dispatcher confinement

F2 starts from the formally accepted RC1-F1 commit
`58451cd45a9e2d94a7b00719e4aba9675345fd58` on branch
`v1.2/rc1-f2-backup-crypto-dispatcher`.

The pre-F2 backup path was
`BackupRestoreViewModel.submitBackupPassphrase` (Main-scoped operation) →
`BackupRestoreCoordinator.createBackup` → synchronous
`EvoluneBackupCodec.encode` → PBKDF2-HMAC-SHA256 600,000 → AES-GCM. Restore
had the equivalent synchronous `decodeAndValidate` path after the Drive
download. F2 confines only these synchronous codec calls with an injected
`cryptoDispatcher`, defaulting to `Dispatchers.Default`:

```text
snapshot / UI orchestration          existing caller boundary
backup codec encode                   withContext(cryptoDispatcher)
restore codec decode/authenticate     withContext(cryptoDispatcher)
Drive blocking network                existing F1 Dispatchers.IO boundary
restore persistence transaction       existing B2 boundary
```

The codec boundary includes canonical serialization, envelope parsing and
validation, PBKDF2, AES-GCM, and authenticated-header handling. Room/DataStore
reads, Drive protocol/retention, restore preview, and destructive persistence
were not moved or redesigned. `withContext` preserves structured cancellation;
no detached coroutine or cancellation swallowing was added.

### Crypto invariants

PBKDF2-HMAC-SHA256, default iterations `600000`, 256-bit derived key,
AES-256-GCM, salt/nonce generation, authenticated header/AAD, version and KDF
parameters, and canonical envelope bytes are unchanged. The B1 golden fixture
remains byte-for-byte stable with SHA-256
`5cbc47bc978c23abcd3e6cbafcad25d4e96208a13dfff6e4a8f5e174349eeaa9`.

### Dispatcher evidence

The focused tests are:

- `EvoluneBackupCodecTest.encode runs on injected crypto dispatcher from main-like caller`
- `EvoluneBackupCodecTest.decode runs on injected crypto dispatcher from main-like caller`

Both tests call the suspend codec boundary from a dedicated Main-like
executor, inject a separate worker executor, and assert that the worker
executed the boundary. The B1 codec suite plus B2 restore transaction/
coordinator, B3 Drive gateway/provider, and B4 coordinator/ViewModel focused
tests passed. The full requested JVM regression also passed:

| Check | Result |
|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | PASS |
| `:experience-core:test --rerun-tasks` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:app:assembleDebug --rerun-tasks` | PASS |
| `:wear:assembleDebug --rerun-tasks` | PASS |

### API37 Fold sanity

On `emulator-5558` (Pixel 10 Pro Fold profile, API 37 / Android 17,
2076×2152, density 390, font scale 1.0, gesture navigation, Latin IME), a
clean debug install used synthetic empty state/body weight 55.0 kg and a
disposable passphrase. Backup passphrase submission reached the production
`备份已完成` UI, and restore passphrase submission reached the production
`恢复预览` UI. During the observed windows the app remained responsive and
logcat contained no ANR, `NetworkOnMainThreadException`, StrictMode, or fatal
exception markers. This sanity check did not rerun G1–G4 retention.

Production changes were limited to `EvoluneBackupCodec`'s injected crypto
dispatcher boundary and the two Coordinator call sites. Test changes were the
two dispatcher tests and the required asynchronous Preview wait in the
existing Coordinator/ViewModel regression. No UI1, Drive protocol, retention,
OAuth, backup-format, Room, DataStore, Manifest, or Gradle change was made.
No RC2, tag, or release was created.

## UI1 — Sync & Backup Settings Information Architecture

UI1 is the post-F2 Settings IA refactor based on
`4dbde12e2c0ff4d58d2f02559866b5a0bd2ae2eb`, on branch
`v1.2/ui1-sync-backup-settings`. The Settings home contains one
`同步与备份` navigation row. Its secondary page groups three passive entries:

- `导入与导出数据` — existing Mahiro file/clipboard actions moved without
  changing callbacks, dialogs, or clipboard/file-picker behavior;
- `Health Connect 同步` — existing HC4 route and status model, moved behind the
  new page;
- `Google Drive 备份与恢复` — existing B4 controls and ViewModel callbacks,
  moved behind the new page.

The refactor added route/page composition, shared settings-list presentation,
localized strings, and focused tests only. It did not change HC4 production
behavior, B1/B2 wire or restore semantics, B3 Drive behavior, B4 coordinator or
ViewModel behavior, crypto, OAuth, retention, Room, DataStore, Manifest, or
Gradle.

### UI1 validation matrix

| Check | Evidence | Result |
|---|---|---|
| Home hides direct HC/Drive/import-export controls | `HealthConnectSyncScreenTest` | PASS |
| Secondary page has passive Data/HC/Drive rows | `SyncAndBackupScreenTest` | PASS |
| Mahiro import/export actions | `SyncAndBackupScreenTest` | PASS |
| B4 backup/restore/disconnect actions | `SyncAndBackupScreenTest` | PASS |
| Route transitions and back navigation | `SyncAndBackupNavigationTest` | PASS |
| API33-A focused suite | 10/10, 0 skipped, 0 failed | PASS |
| API33-B focused suite | 10/10, 0 skipped, 0 failed | PASS |
| API37 Fold focused suite | 10/10, 0 skipped, 0 failed | PASS |
| API35 focused suite | No API35 AVD online | NOT TESTED |
| Fold OPENED manual check | 2076×2152, density 390; no clipping/overlap | PASS |
| Fold CLOSED manual check | 1080×2364; no clipping/overlap | PASS |

The focused suite included the existing six HC4 screen tests, three new
secondary-page tests, and one route/back-navigation test. Manual checks
confirmed Settings → Sync & Backup → each child route and return navigation in
both Fold postures. UI1 is pending independent reviewer acceptance; it is not a
final RC release decision. No retention, live G1–G4, A→B→A, RC2, tag, or
release activity was performed.

## UI1-R1 — zh-rCN string parity

The UI1 independent review approved the target with a non-blocking P2 note:
the ten UI1 strings added to `values/strings.xml` had no corresponding
`values-zh-rCN/strings.xml` entries. This raised the MissingTranslation count
from the historical 45 to 55. R1 adds exact Chinese mirrors for:

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

| Lint measure | Before R1 | After R1 |
|---|---:|---:|
| Total errors | 55 | 45 historical |
| MissingTranslation | 55 | 45 historical |
| Warnings | 97 | 97 |
| Hints | 1 | 1 |
| UI1-introduced delta | 10 | 0 |

After R1, `lintDebug` still reports the unchanged historical widget
translation debt and exits non-zero; no UI1 translation error remains. The
only production change is the zh-rCN resource parity. UI, navigation, business
logic, HC4, B1/B2/B3/B4 semantics, and tests are unchanged. The existing ten
focused UI1 tests are HC4 = 6, SyncAndBackup = 3, Navigation = 1; all passed
10/10 on API33-A, API33-B, and API37 Fold. API35 remains NOT TESTED because no
API35 AVD was online. No retention, A→B→A, RC2, tag, or release activity was
performed.

## RC1-R3-Fix — Disconnect session clear and Settings/picker polish

### Baseline and scope

Base: `f6345c622affba7f87e7f2519ed67e0e51311f5a`

Branch: `v1.2/rc1-r3-disconnect-ui-polish`

This cycle contains only the RC1 Disconnect blocker fix, the requested
Settings information-architecture polish, the generation-picker presentation
change, focused tests, and this documentation update. No B1/B2/B3/HC4
protocol or semantic change was made.

### Goal A — Disconnect root cause and fix

The previous owner-device run had already completed real OAuth authorization
and displayed `已连接（当前会话）`, but clicking `断开 Google Drive` produced
`断开 Google Drive 失败`.

The traced call chain was:

```text
GoogleDriveBackupRestoreScreen
  → BackupRestoreViewModel.disconnect()
  → BackupRestoreCoordinator.disconnect()
  → GoogleDriveBackupProvider.disconnect()
  → GoogleAuthorizationGateway.disconnect()
  → AuthorizationClient.revokeAccess(...).awaitTask()
```

The first failing boundary was the final `revokeAccess` operation. It used
authorization-grant revocation for a feature whose contract is current-session
disconnect. The fix removes that revoke path and makes the gateway call the
existing `clearToken(currentToken)` operation instead. Cancellation and error
mapping remain in the existing clear-token implementation; no detached
coroutine or token/account logging was added.

The resulting semantics are:

- current in-memory authorization session is cleared;
- remote appDataFolder backups are not deleted;
- local Room/DataStore data is not deleted;
- `drive.appdata` remains the only requested scope;
- offline access, refresh tokens, and server auth codes remain absent.

Focused evidence:

- `disconnect never deletes cloud backups`
- `disconnect failure preserves connected state and never deletes cloud backups`
- `successful disconnect clears connected state without deleting cloud backups`
- `disconnect clears current authorization session without deleting remote backups`
- `disconnect authorization failure is mapped without deleting remote backups`

On the owner Fold (`Pixel_10_Pro_Fold`, API 37 / Android 17, serial
`emulator-5558`), the owner manually completed the Google authorization
account UI. The app then showed `已连接（当前会话）`. The real Disconnect click
completed successfully and the same screen returned to `需要授权后才能使用`.
No live upload, restore, retention, or delete operation was performed in this
sanity pass. The successful live result closes the prior Disconnect blocker;
remote-preservation is established by the no-delete production path and
focused tests, not by a new remote generation experiment.

### Goal B — Settings and generation picker

Settings home order is now:

```text
体重
夜间模式 / 颜色主题 / 时间制式 / other existing settings
同步与备份
更新
```

Only Compose child order changed. The existing route and callbacks remain
unchanged. The home entry and the three Sync & Backup child entries all use
the pre-existing `SettingsNavigationRow` shared component, colors, and
`stableSegmentedShapes`; no second list-item visual system was introduced.

`GenerationPickerDialog` now renders each generation as a complete clickable
MD3 segmented row rather than a vertically stacked bare `TextButton`. It
preserves the provider's current list order and generation identity, shows
localized numbered labels (`备份 1`, `备份 2`, `备份 3`), and formats valid ISO
timestamps using the device time zone as supporting text. Invalid metadata
still falls back to the original value. The cancel action and
generation→passphrase→preview→confirm flow are unchanged.

The added UI test
`backupPickerUsesNumberedRowsWithReadableTimesAndPreservesSelection` verifies
all three numbered rows and selection of the middle generation. Final
focused instrumentation passed 4/4 on the API37 Fold for
`SyncAndBackupScreenTest`; the preceding run passed the same 4/4 suite on
API33-A, API33-B, and the Fold. The existing `HealthConnectSyncScreenTest`
passed 6/6 on the Fold. No HC4 production code was changed.

### Regression and release gates

| Check | Result |
|---|---|
| App focused Disconnect/coordinator/provider tests | PASS |
| `:app:testDebugUnitTest --rerun-tasks` | PASS |
| `:experience-core:test --rerun-tasks` | PASS |
| `:wear:testDebugUnitTest --rerun-tasks` | PASS |
| `:app:assembleDebug --rerun-tasks` | PASS |
| `:wear:assembleDebug --rerun-tasks` | PASS |
| `:app:lintDebug` | 45 historical errors / 97 warnings / 1 hint; no new delta |
| Settings/Sync & Backup focused instrumentation | PASS |
| Real owner-device authorize → connected → Disconnect | PASS |
| Retention G3/G4 | NOT TESTED / remains paused |
| RC2, tag, release | NOT STARTED |

No production diagnostics were left behind. No A→B→A re-run was performed in
this cycle; the previously accepted restore evidence remains unchanged.

## RC1-R3-R1 — Navigation test scroll fix

### Reviewer blocker and test-only correction

The independent R3 review identified one deterministic test failure:
`SyncAndBackupNavigationTest.settingsSyncBackupHealthConnectAndDriveBackNavigationIsStable`
called `performClick()` directly on `settings-sync-backup-entry`. The approved
R3 Settings placement puts `同步与备份` immediately above `更新`, which places
the entry below the initial fold on API33-A, API33-B, and API37 Fold. The
production placement and UI behavior were correct; the test did not model the
required user scroll.

R1 adds `performScrollTo()` before `performClick()`. No timeout was increased,
no wait or back-navigation assertion was removed, and no production code was
changed. The test still covers:

```text
Settings
  → scroll to Sync & Backup
  → Sync & Backup
  → Health Connect → Back
  → Google Drive → Back
  → Data Import & Export → Back
  → Settings
```

### Focused instrumentation evidence

The post-R1 focused composition is:

| Test class | Count |
|---|---:|
| `HealthConnectSyncScreenTest` | 6 |
| `SyncAndBackupScreenTest` | 4 |
| `SyncAndBackupNavigationTest` | 1 |
| **Total per device** | **11** |

Pre-R1, the navigation test failed because the entry was offscreen and was
clicked without scrolling. Post-R1 results:

| Device | Focused result |
|---|---|
| API33-A | 11/11 PASS |
| API33-B | 11/11 PASS |
| API37 Fold | 11/11 PASS |

The standalone `SyncAndBackupNavigationTest` also passed three consecutive
runs on API37 Fold. This closes the reviewer test blocker without changing
Disconnect live evidence or any approved R3 production behavior.

R1 is test + documentation only. No production Kotlin/resource/navigation,
backup, Health Connect, Room, DataStore, Wear, retention, RC2, tag, or release
change was made.

## RC1-R3-F2-T1 — Post-disconnect state-machine isolation

### Scope and baseline

Base: `4c7c7c1e50ad69d3e412906af5f6a33ddd569d8d`

This T1 is a post-R3-R1 triage of the suspected Disconnect state-machine
blocker. It produced no production diff and no permanent test diff. A
temporary deterministic ViewModel trace was reverted after execution.

### State transition table

| State | Entry | Exit/reset | User-action behavior |
|---|---|---|---|
| `Idle` | initial; cancel; terminal-message dismissal; successful Disconnect | backup/restore/Disconnect or remains Idle | backup/restore enabled; Disconnect shown only while connected |
| `Authorizing` | explicit backup/restore action or reauthorization request | authorization outcome, resolution result, or cancel | all three top-level actions blocked |
| `AwaitingBackupPassphrase` | authorized backup | passphrase submit/cancel | backup passphrase only |
| `PreparingBackup` / `Uploading` | backup submission / reserved upload state | codec/provider result or cancellation | blocked |
| `BackupSuccess` | verified upload result | dismissal/cancel → `Idle` | blocked |
| `LoadingBackups` | authorized restore/list | list result, authorization event, error, or cancel | blocked |
| `SelectingBackup` | list success | generation selection/cancel | generation selection only |
| `AwaitingRestorePassphrase` | generation selection | passphrase submit/cancel | restore passphrase only |
| `PreparingRestorePreview` | restore submission | preview, authorization, error, or cancellation | blocked |
| `Preview` | prepared restore | confirm/cancel | confirm/cancel only |
| `Restoring` | restore confirmation | success/warning/error/cancellation | blocked |
| `RestoreSuccess` / warning | successful persistence outcome | dismissal/cancel → `Idle` | blocked |
| `Error` | operation failure | dismissal/cancel → `Idle` | blocked |

`connected` is a separate `StateFlow`. The backup and restore buttons use
`state == Idle` as their enabled condition; they are not disabled merely
because `connected` is false. Explicit authorization is the reconnect path.

### Disconnect sequence and guard audit

The actual successful sequence is:

```text
Idle (connected=true, before Disconnect)
  → Idle (during Disconnect; no separate Disconnecting state)
  → Idle (connected=false, after coordinator/provider success)
```

The ViewModel owns the post-success reset. The coordinator's existing mutex
protects the provider call and is released when that call completes. The
ViewModel checks `operationJob?.isActive`, so the completed job reference does
not keep a stale busy guard. Failure maps to `Error(DISCONNECT_FAILED)` while
preserving `connected=true`; no remote-delete operation is present.

The temporary fake-backed trace recorded:

```text
[Idle, AwaitingBackupPassphrase, Idle, Idle,
 Error(error=BackupRestoreError(operation=RESTORE, code=NO_BACKUPS))]
connectedAfterDisconnect=false
```

After successful Disconnect, a direct `restoreFromBackup()` call was accepted
and reached the existing authorization/list path. The fake's `NO_BACKUPS`
result was the terminal error, not an action-gate rejection. Existing retained
tests also cover successful and failed Disconnect, connected-state behavior,
and no cloud-backup deletion.

### UI relocation and first live divergence

The UI1 relocation did not alter the state reset owner or event consumption.
The current Google Drive route still collects `uiEvents` and supplies the
same ViewModel callbacks; comparison with the pre-UI1 route shows the same
authorization event handoff and dismissal/cancel wiring.

The earlier Fold live observation after an explicit restore action showed no
Google Play Services `AuthorizationActivity` and no token/list evidence. The
first observed divergence is therefore after the ViewModel action dispatch,
at the live `GoogleAuthorizationGateway.authorize()` /
`AuthorizationClient` resolution-to-activity-result boundary. The source
authorization path is reached by the deterministic fake, but live activity
reachability was not observed. The exact internal Google failure remains
unresolved and must not be inferred from the post-action screen alone.

### T1 classification

| Class | Result | Evidence |
|---|---|---|
| T1-A — `connected=false` disables actions | **Not reproduced** | buttons are gated by `Idle`, not `connected`; direct restore remained callable |
| T1-B — stale busy/in-flight guard | **Not reproduced** | successful Disconnect returns `Idle`; completed job is inactive |
| T1-C — UI relocation removed reset/event consumption | **Not reproduced** | route/event/callback wiring matches the pre-UI1 path |
| T1-D — restore action not callable | **Not reproduced** | fake-backed direct call entered authorization/list logic |
| T1-E — UI state/action gate healthy; authorization boundary fails | **Confirmed remaining boundary** | no live GMS authorization activity or token/list result observed |
| T1-F — production/device-only race | **Not indicated** | no deterministic state race reproduced |

The minimal next action is a separate live authorization-boundary triage:
capture the `AuthorizationClient` task outcome and exactly-once
`LaunchAuthorization`/activity-result handoff, with no token/account/secret
logging. Required focused coverage is successful and failed Disconnect state
transitions, action callability after Disconnect, exactly-once restore
authorization event emission, and a live assertion for the authorization
activity/result stage. No button-gate or state-machine rewrite is justified by
T1 evidence.

Retention G3/G4 and another A→B→A run were not performed. The accepted
retention and A→B→A evidence is carried forward. RC2, tags, and release remain
paused; the live authorization blocker remains open.

## RC1-R3-F2-T2 — Live authorization handoff stage isolation

### Scope

Base: `ce121ea9e7d7db19ec4308d9fae2441d2ef6d7aa`

This was a temporary observability-only triage. Temporary logging and temporary
fake-seam tests were removed before the docs-only commit. Production behavior,
authorization scopes, backup format, restore semantics, and UI behavior are
unchanged.

### Source call graph

```text
GoogleDriveBackupRestoreScreen
  → BackupRestoreViewModel.restoreFromBackup()
  → BackupRestoreViewModel.authorize(RESTORE)
  → BackupRestoreCoordinator.authorizeFor(RESTORE)
  → GoogleAuthorizationGateway.authorize()
  → AuthorizationClient.authorize(AuthorizationRequest)
```

`DIRECT_AUTHORIZED` sets the session as connected and enters
`loadBackupsAfterAuthorization()`; this calls `listBackups()` and renders
`SelectingBackup`. `UserResolutionRequired` emits one
`LaunchAuthorization` event. `AppNavigation` consumes that event, invokes the
`StartIntentSenderForResult` launcher, and sends the activity result through
`outcomeFromIntent()` and `onAuthorizationOutcome()`.

### Deterministic fake-seam coverage

The temporary focused tests passed 21/21:

| Branch | Evidence |
|---|---|
| Direct `Authorized` after Disconnect | no event; exactly one provider list call; picker state reached |
| `UserResolutionRequired` | exactly one `LaunchAuthorization`; simulated authorization result; exactly one provider list call; picker state reached |
| Authorization error | `AUTHORIZATION_FAILED`; no provider list call |

The test source was reverted; no permanent test change remains.

### Live API37 Fold trace

Device: `emulator-5556`, API37 / Android 17. The page was revisited passively
with `connected=false` and `Idle`; no authorization or list stage was observed
until the one counted Restore click.

| Stage | Observed result |
|---|---|
| A0 | PASS — Restore action received with `Idle`, `connected=false`, inactive job |
| A1 | PASS — ViewModel entered `Authorizing` |
| A2 | PASS — gateway invoked, `tokenPresent=false` |
| A3 | PASS — `AuthorizationClient` task completed successfully |
| A4 | `DIRECT_AUTHORIZED` |
| A5D | PASS — token presence accepted; value never logged |
| A6D | PASS — `connected=true` |
| A7D | PASS — `listBackups` invoked |
| A8D | PASS — list success, count 3 |
| A9D | PASS — `选择备份` picker shown with three rows |

Resolution stages A5R through A13R were not entered. Consequently:

```text
LaunchAuthorization emitted: 0
collector received: 0
launcher invoked: 0
activity result returned: NOT APPLICABLE
AuthorizationClient exception/status: none observed
```

### T2 conclusion

The counted live attempt had no first failing stage. The absence of a Google
authorization UI was not a failure: the task completed as
`DIRECT_AUTHORIZED`, and the flow reached the live remote list and picker.
This is also why no `AuthorizationActivity` should be required for this run.

T1-E remains a boundary classification for the earlier uninstrumented
observation, not a proven Google or production root cause. T2-A through T2-K
were not reproduced. No UI gate, Disconnect, ViewModel state-machine,
collector, launcher, or provider fix is justified by this evidence.

The minimal next action, if the live flow fails again, is to repeat the same
sanitized stage map once and classify the first actual divergence. No second or
third Restore click was made after this successful picker result.

G3/G4 retention, latest-three retention, current-generation protection,
Disconnect session clear, and A→B→A evidence are carried forward unchanged.
No retention or A→B→A rerun, RC2, tag, or release activity was performed.

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

## UI3 — Settings category information architecture

UI3 is based on accepted UI2 commit
`03ff78856e2184b38d39d4453090f9a4917788ed`. Before UI3, the Settings home
combined direct controls with category navigation, and the owner identified
that hierarchy as inconsistent. UI3 turns the home into exactly five passive
category entries and relocates the existing controls without redesigning
their semantics.

The home entries are:

1. 基础数据 — 体重及计算相关数据
2. 外观与格式 — 主题、配色与时间显示
3. 同步与备份 — 导入导出、健康数据与云端备份
4. 更新 — 自动更新、版本检查与当前版本
5. 关于 — 版权信息与免责声明

All five use the shared `SettingsNavigationRow` with one MD3 single-item
shape, shared colors, equal spacing, leading icon, supporting text, and
chevron. Operational controls are absent from the home. The relocated pages
retain the existing body-weight validation/authority, appearance selectors,
Sync & Backup behavior, update actions, and About dialogs. The same
SettingsViewModel remains the single state owner; category entry has no
implicit side effects.

### UI3 focused evidence

| Device/gate | Result |
|---|---|
| API33-A | **16/16 PASS** — HC4 6, Sync & Backup 4, navigation 2, category behavior 4 |
| API33-B | **NOT TESTED** — no device online |
| API37 Fold | **16/16 PASS** — same composition |
| API37 Fold OPENED | **PASS** — five uniform home rows and category page bounds inspected; no clipping/overlap observed |
| API37 Fold CLOSED | **PASS** — five uniform home rows and category page bounds inspected; no clipping/overlap observed |
| app JVM / experience-core / wear JVM | **PASS** |
| app / wear debug builds | **PASS** |
| app lint | **EXPECTED BASELINE FAILURE** — 45 errors, 97 warnings, 1 hint; no UI3 delta |

No OAuth, Drive, Disconnect, retention, A→B→A, HC weight, RC2, tag, or
release gate was rerun. All earlier accepted live-service and HC4 evidence
is carried forward unchanged.

## RC1-R3-R3 — Final live Drive closure on UI3

This docs-only closure is based on accepted UI3 commit
`a6ced2730714653e44e3666aa5bd810b2318fe99` on branch
`v1.2/rc1-r3-r3-final-live-closure`. No Kotlin, resource, test, Manifest,
Gradle, or protocol file was changed.

The UI3 author evidence recorded API33-B as `NOT TESTED`. An independent UI3
reviewer subsequently ran the same 16-test composition on API33-B and reported
**16/16 PASS**. This section records that as independent reviewer evidence;
the original author record is intentionally not rewritten. API33-A and API37
Fold author evidence remain **16/16 PASS**, and API35 remains `NOT TESTED`.

### Owner-Fold final live closure

The final production path was exercised on the available API37 Pixel 10 Pro
Fold (`emulator-5556`):

`Settings → 同步与备份 → Google Drive 备份与恢复`.

The explicit restore action initiated the Google account authorization UI;
after the owner account was selected, the app displayed the current remote
list of exactly three generations. The picker showed `备份 1`, `备份 2`, and
`备份 3`, formatted readable timestamps, full-width clickable rows, and the
shared MD3 segmented list-row treatment. The picker was cancelled; no
generation was selected and no restore or backup write was performed.

| Gate | Evidence | Result |
|---|---|---|
| Existing remote list | Exactly 3 generations shown by the production picker; no new backup created | **PASS — LIVE GOOGLE SERVICE** |
| Four-generation retention | Prior accepted live run: verified G3/G4 completed; after G4 exactly three managed generations remained (`G4`, `G3`, and the prior retained generation); the older generation was pruned and verified current `G4` was retained/protected | **PASS — CARRIED FORWARD; not rerun in R3-R3** |
| Picker rows | Numbered rows, formatted timestamps, full-row click targets, preserved order | **PASS — LIVE GOOGLE SERVICE** |
| Disconnect / clear current session | `已连接（当前会话）` changed to `需要授权后才能使用`; no remote delete or local-data mutation | **PASS — LIVE GOOGLE SERVICE** |
| Passive Drive revisit | Re-entering the Drive page after disconnect did not authorize, list, or auto-recover | **PASS — LIVE GOOGLE SERVICE** |
| Explicit restore reauthorization | Explicit `从备份恢复` re-established the current session and reopened the 3-generation picker | **PASS — LIVE GOOGLE SERVICE** |
| RC1-R3 live core | All final closure gates | **PASS — not a release decision** |

The prior Disconnect blocker is therefore closed. In this report, Disconnect
means clearing the current app authorization session. It does not delete the
user's encrypted remote appDataFolder backups or local Room/DataStore data.
The accepted A→B→A restore, HC4 device-local preference, and T2 evidence are
carried forward. Retention is **PASS — carried forward from prior accepted
live retention evidence**; it was not rerun in R3-R3 and is not `NOT TESTED`.

### Closure scope and remaining release gates

The closure branch contains documentation only. The prior UI3 focused
instrumentation and the independent API33-B 16/16 review are preserved as
their original evidence sources. The closure regression also passed
`:app:testDebugUnitTest`, `:experience-core:test`,
`:wear:testDebugUnitTest`, `:app:assembleDebug`, and
`:wear:assembleDebug`. The UI3 publication retry was attempted once but
remained pending because the environment could not connect to GitHub; no
alternate credential or bypass was used.

The following remain release blockers even though the RC1 Drive live core is
closed: real Health Connect `WeightRecord` acceptance and watermark evidence;
physical-device KDF benchmark and large-history sanity; API35 status; release
signing credentials and signed/minified App/Wear artifacts; R8 runtime and
signer verification; signed smoke; physical Wear Data Layer evidence; and
final owner review. No G3/G4 retention rerun, RC2, tag, or release activity
was performed.

## RC1-R3-R4 — Retention acceptance consistency closure

This docs-only correction is based on `44a7afc4d2cd2c95e0f237217fe333068e43afd8`.
No live Google Drive operation, production source, or test source was changed.
The purpose is to make the current acceptance state agree with the previously
accepted live evidence while preserving every historical point-in-time result.

### Current Google Drive live core acceptance

| Gate | Evidence source | Current result |
|---|---|---|
| OAuth / AuthorizationClient | Prior accepted live authorization plus R3-R3 explicit restore authorization | **PASS — carried forward/current live evidence** |
| `drive.appdata`-only scope | Prior accepted scope audit | **PASS — carried forward** |
| Upload | Prior accepted verified live backup upload | **PASS — carried forward** |
| Readback verification | Prior accepted byte/SHA-verified live readback | **PASS — carried forward** |
| List | Prior accepted live list plus the R3-R3 production picker | **PASS — carried forward/current live evidence** |
| Download/decrypt | Prior accepted live A→B→A restore evidence | **PASS — carried forward** |
| Preview | Prior accepted non-mutating live restore preview | **PASS — carried forward** |
| A→B→A | Prior accepted synthetic-data restore evidence | **PASS — carried forward; not rerun in R3-R3** |
| Latest-three retention | Prior accepted G3/G4 live retention evidence: exactly three managed generations remained after G4 and the older generation was pruned | **PASS — carried forward; not rerun in R3-R3** |
| Current-generation protection | Prior accepted live evidence verified current G4 was retained/protected during its retention pass | **PASS — carried forward; not rerun in R3-R3** |
| Disconnect / clear current session | R3-R3 owner-Fold live action | **PASS — LIVE GOOGLE SERVICE** |
| Passive post-disconnect behavior | R3-R3 passive Drive-page revisit | **PASS — LIVE GOOGLE SERVICE** |
| Explicit reconnect | R3-R3 explicit restore action re-established the current session | **PASS — LIVE GOOGLE SERVICE** |
| Remote backup persistence across Disconnect | Prior accepted no-delete semantics plus R3-R3 list remained available after reconnect | **PASS — carried forward/current live evidence** |
| Picker | R3-R3 production picker showed `备份 1/2/3`, formatted timestamps, and full-row MD3 items | **PASS — LIVE GOOGLE SERVICE** |

The current Google Drive v1.2 live core is **PASS** and `RC1-R3` is
**CLOSED**. Retention is **PASS — carried forward** and is not a current
release blocker. The retention sequence was **not rerun in R3-R3**. This is
distinct from the historical `NOT TESTED` and `BLOCKED` rows retained above.

### Current remaining release blockers

Remaining release gates are Health Connect real `WeightRecord`/watermark
evidence, physical-device KDF 600k benchmarking, physical large-history
sanity, API35 current-lineage validation, release signing, App/Wear R8 runtime
smoke, signer verification, physical Wear runtime/Data Layer evidence if
required, and final owner review. No RC2, tag, or release activity was
performed.

## RC1-R4 — API35 current-lineage validation

This validation branch is based on the accepted current-lineage commit
`14660a9eac73aefa89b13e00b94ef669865dc5e7`. The earlier R2A API35 triage is a
historical sibling and was not used as current-lineage acceptance evidence.
No production or test source was changed.

The requested existing AVD was `evolune-hc3-api35`. The `.avd` directory was
present, but its AVD registration `.ini` was absent; `emulator.exe -list-avds`
did not list it. A non-destructive cold-boot attempt with
`-no-snapshot-load` failed before boot with `Unknown AVD name
[evolune-hc3-api35]` and the launcher reported that
`$HOME\.android\avd\evolune-hc3-api35.ini` was missing. The currently online
devices were API33, API37, and API33; no API35 serial appeared.

| API35 device field | Result |
|---|---|
| AVD | `evolune-hc3-api35` |
| Serial | NONE — never appeared in `adb devices` |
| API / Android / display / density / ABI | NOT AVAILABLE — AVD did not boot |
| GMS / Health Connect provider-controller | NOT AVAILABLE — AVD did not boot |

This is **R4-E1 — AVD launcher unavailable**. It is an environment blocker,
not a production or test failure. No lock file was deleted, no data was wiped,
no system image or hardware profile was changed, and no API35 emulator process
was left running by this validation.

### API35 validation status

| Gate | Result | Evidence |
|---|---|---|
| API35 boot / device identity | **BLOCKED / NOT TESTED** | `evolune-hc3-api35` not registered; no API35 serial |
| Current-lineage APK install | **NOT TESTED** | API35 device unavailable |
| UI3 focused 16-test suite | **NOT TESTED** | Stopped at environment gate |
| Historical-sensitive tests | **NOT TESTED** | Stopped at environment gate |
| Corrected IME frame probe | **NOT TESTED** | No API35 device |
| Health Connect API35 compatibility sanity | **NOT TESTED** | No API35 device |
| Full API35 connected instrumentation | **NOT TESTED** | No API35 device |
| API35 manual Settings sanity | **NOT TESTED** | No API35 device |
| API35 current-lineage gate | **BLOCKED** | R4-E1 — AVD launcher unavailable |

The API35 gate is not closed and is not promoted to `PASS`. No focused suite,
full instrumentation, IME cycle evidence, current APK identity evidence, or
manual UI evidence is claimed. The API35 release gate remains open for a
future environment-recovery validation.

RC1-R3 and the Google Drive v1.2 live core remain **CLOSED / PASS** and were
not reopened. No OAuth, upload, G1/G2/G3/G4 retention, Disconnect, reconnect,
A→B→A, RC2, tag, or release operation was performed.

## RC1-R4-R1 — API35 current-lineage full validation

This validation attempt is based on the current-lineage triage commit
`cc7febe3d7d8510366eb83d520432e91a05f6993`. The independent reviewer’s API35
UI3 focused result of 16/16 PASS is acknowledged as separate reviewer
evidence, not as author R1 evidence. No production or test source was changed.

The existing `evolune-hc3-api35.ini` was present. The emulator client listed
the AVD when given process-local `ANDROID_SDK_HOME` and `ANDROID_AVD_HOME`
paths; no registration file, AVD data, system image, or hardware profile was
modified. A cold boot with `-no-snapshot-load` found the Android 35 Google APIs
x86_64 system image, but QEMU repeatedly failed before boot while creating
`C:\\Users\\1\\.android\\emu-last-feature-flags.protobuf.lock` with `error: 5`.
No API35 serial appeared and `boot_completed=1` was not reached.

This is **R4-R1-C — emulator/environment failure**, not an application or
test failure. The API35 process was stopped after it failed to produce a
device; no AVD data was wiped and no configuration was changed.

| Gate | Result | Evidence |
|---|---|---|
| API35 device identity | **BLOCKED** | AVD known, but no serial/API35 device appeared |
| Current-lineage APK install | **NOT TESTED** | No API35 device |
| Reviewer UI3 focused evidence | **PASS — reviewer evidence** | Independent reviewer reported 16/16 PASS |
| Author R1 UI3 focused suite | **NOT TESTED** | Stopped at boot environment gate |
| Rapid double-tap / geometry tests | **NOT TESTED** | No API35 device |
| MedicationPlansScreenTest | **NOT TESTED** | No API35 device |
| Corrected IME frame probe | **NOT TESTED** | No API35 device |
| Health Connect API35 sanity | **NOT TESTED** | No API35 device |
| Full API35 instrumentation | **NOT TESTED** | No API35 device |
| Manual API35 Settings sanity | **NOT TESTED** | No API35 device |
| API35 current-lineage gate | **BLOCKED** | R4-R1-C — emulator/environment failure |

The API35 gate remains open and is not promoted to `PASS`. The earlier R4-E1
point-in-time record remains historical and unchanged. RC1-R3 and the Google
Drive v1.2 live core remain **CLOSED / PASS**; no Drive operation was reopened.

## RC1-R4-R2 — API35 environment recovery and current-lineage validation

This documentation-only record continues from the accepted R4-R1 commit
`03a73e7c9236934728c9dadd6655299d5cc3b9cd`.
The prior R4-E1 missing-registration record and R4-R1-C feature-lock record
remain intact as historical evidence. No production or test source was
changed.

### Recovery map

| Device line | AVD | Serial | API | Boot state | Validation |
|---|---|---|---:|---|---|
| API33-A | `evolune-hc3-api33` | `emulator-5554` | 33 | `boot_completed=1` | online |
| API33-B | `Evolune_API33_Migration` | none | 33 | not reached | recovery attempted, no tests |
| API37 Fold | `Pixel_10_Pro_Fold` | `emulator-5556` | 37 | `boot_completed=1` | online |

The API33-B attempt used the existing AVD only and did not wipe, recreate, or
modify it. Because no serial appeared, API33-B tests were not run. The process
command-line query through `Get-CimInstance Win32_Process` was access-denied;
only explicitly mapped emulator/qemu PIDs and known AVD launch commands were
used for the recovery cleanup.

### API35 retry

Before retry, the lock path
`C:\\Users\\1\\.android\\emu-last-feature-flags.protobuf.lock` was absent;
after the failed retry it remained absent. No absent lock file was created or
deleted, and no permission or ACL change was attempted.

Separate reviewer evidence confirmed that the `evolune-hc3-api35` registration
was present, that the same AVD had previously cold-booted successfully, and
that the lock path was absent. This is reviewer evidence, not a claim that the
author-side retry booted successfully.

The existing `evolune-hc3-api35` AVD was launched directly with explicit
process-local SDK/AVD paths and `-no-snapshot-load`. The Android 35 Google APIs
x86_64 system image was found, but QEMU failed before adb registration with
repeated:

```text
Unexpected error while creating:
C:\\Users\\1\\.android\\emu-last-feature-flags.protobuf.lock (error: 5)
```

No API35 serial appeared and `boot_completed=1` was never reached. The mapped
API35 emulator/qemu pair was stopped after the failed boot and temporary
diagnostic logs were removed. This is **R4-R2-C — emulator/environment
failure**. The exact owner/ACL cause remains unproven; no workaround was
applied.

### R4-R2 result matrix

| Gate | Result | Evidence |
|---|---|---|
| API35 boot / identity | **BLOCKED** | Reproduced feature-lock creation error; no serial |
| Current-lineage APK install | **NOT TESTED** | No API35 device |
| Reviewer UI3 focused result | **PASS — reviewer evidence** | 16/16 PASS, separate evidence |
| Author UI3 focused result | **NOT TESTED** | Boot gate blocked |
| `rapidDoubleTapInvokesOneInsert` | **NOT TESTED** | No API35 device |
| `doseLabelsAndFieldsKeepRelativeGeometryAcrossFocusChanges` | **NOT TESTED** | No API35 device |
| `MedicationPlansScreenTest` | **NOT TESTED** | No API35 device |
| Corrected `RealAppImeFrameProbeTest` | **NOT TESTED** | No API35 device |
| API35 Health Connect sanity | **NOT TESTED** | No API35 device |
| Full API35 instrumentation | **NOT TESTED** | No API35 device |
| Manual Settings sanity | **NOT TESTED** | No API35 device |
| API35 current-lineage gate | **BLOCKED** | R4-R2-C |

No author-side API35 tests, IME probe, HC sanity, full instrumentation, or
manual UX evidence is claimed. The app JVM and debug-build regression set was
not run after this environment gate. The Google Drive live core remains
**CLOSED / PASS** and was not rerun. No RC2, tag, or release activity was
performed.

## RC1-R5-R2 — HC4 Real Provider Freshness Watermark Live Acceptance

### Review scope and stop policy

This live acceptance used the accepted base
`93f32938f40d8873122dba08909ca4337827e324` on branch
`v1.2/rc1-r5-r2-hc4-live-watermark`. It used the frozen, separately reviewed
Health Connect Weight Seeder at
`D:\Evolune-Workspace\tools\hc-weight-seeder`, commit
`a9ae497ea94b8039985f7fb26e2d3673f9cffca2`. The Seeder was the only writer;
Evolune was never granted `WRITE_WEIGHT`. This was validation-only: no
production/test edit, fixture, fake response, or inline fix was allowed.

The first meaningful failure was recorded as
`R5-R2-E — manual freshness barrier not durable`. The run stopped before
creating HC-B or HC-C, preserving the required failure boundary.

### Device and evidence capture

| Field | Evidence |
|---|---|
| Device | API37 Fold, `emulator-5556`, `sdk_gphone16k_x86_64`, Android 17 |
| Display | 2076x2152, density 390, rotation 0 |
| Runtime | font scale 1.0; navigation mode 2 (gestural); window/transition animations 1.0/1.0; default IME `com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME` |
| Health Connect | `com.google.android.healthconnect.controller`, version 17 |
| Evolune | `io.github.yingqiu0871.evolune.debug`, version 1.2.0-debug |
| Permission model | Seeder `WRITE_WEIGHT` only; Evolune `READ_WEIGHT` only; no Evolune `WRITE_WEIGHT` |
| DataStore evidence | Read-only `run-as` base64 export of `files/datastore/settings.preferences_pb`; no DataStore write |

Before the formal sequence, the Health Connect Weight data view was empty.
The captured original state was body weight 55.0 kg, sync OFF, Evolune
READ_WEIGHT not granted, and no HC weight or adopted-at watermark keys. The
formal baseline was then set to 60.0 kg through the product UI. A preliminary
59.9 kg Seeder input was corrected by deleting that exact Seeder-owned record;
it was an operator setup correction and not counted as a product result.

### HC-A through first failure

| Step | Exact observation | Gate |
|---|---|---|
| HC-A | 61.1 kg; `2026-08-26T09:15:20.133723Z`; epoch `1787735720133`; record ID `778f30b5-cc8b-404a-a334-c9b9913cdc66`; Health Connect showed source `io.github.yingqiu0871.evolune.hcweightseeder` | **PASS** |
| Toggle OFF | Sync OFF and READ_WEIGHT absent; Evolune did not adopt HC-A; body weight remained 60.0 kg; watermark absent | **PASS** |
| Enable and adopt | Real Health Connect permission UI granted Weight read only; Evolune adopted 61.1 kg and persisted watermark `2026-08-26T09:15:20.133Z` | **PASS** |
| Restart #1 | Body weight, sync preference, HC metadata, and watermark persisted | **PASS** |
| Manual override | Normal Basic Data UI changed local body weight to 62.2 kg; HC-A metadata/watermark stayed unchanged | **PASS** |
| Stale reconciliation | Foreground reconciliation and restart #2 retained 62.2 kg; stale HC-A did not overwrite the local value | **PASS** |
| Manual freshness barrier | Expected `W_MANUAL > T_A` was absent. The DataStore still contained body weight 62.2 kg, HC weight 61.1 kg, and adopted-at `2026-08-26T09:15:20.133Z` | **FAIL — R5-R2-E** |

The failure is precise: manual local editing is preserved, but it does not
advance the HC freshness watermark. The current coordinator's `adoptIfFresh`
path only compares the fetched HC observation timestamp with the last HC
adoption timestamp. There is no production branch that records a manual local
body-weight edit as a freshness barrier. This is therefore a production
behavior gate failure, not a provider, permission, or stale-record overwrite
failure. No source was changed to repair it.

### Deferred gates

Because R5-R2-E failed, the following were deliberately not executed:

| Gate | Result |
|---|---|
| HC-B 63.3 kg newer adoption | **NOT TESTED** |
| HC-C same-value newer timestamp | **NOT TESTED** |
| Formal revoke → reauthorize → previous-record visibility | **NOT TESTED** |
| 30-day query-boundary check | **NOT RUN** |
| App JVM, focused HC4 suite, and debug builds after failure | **NOT RUN** |

### Cleanup evidence

The HC-A Seeder record was deleted by the Seeder UI using its exact record ID.
Health Connect Weight then showed no data. The Seeder was uninstalled; its
package was absent afterward. Evolune sync was disabled, local body weight was
restored to 55.0 kg through the normal UI, and Evolune READ_WEIGHT was revoked
through the system Health Connect permission manager. The test-generated
watermark remains in the captured DataStore because the validation prohibited
direct state writes; no claim is made that internal test metadata was reset.

### RC1-R5-R2 disposition

| Area | Result |
|---|---|
| Real provider / HC-A / first adoption | **PASS** |
| Toggle-OFF and stale local-value protection | **PASS** |
| Durable manual freshness barrier | **FAIL — R5-R2-E** |
| HC-B / HC-C / formal reauthorization | **NOT TESTED** |
| HC4 real-provider release gate | **FAIL** |
| Production/test diff | **ZERO** |
| Documentation diff | **This docs-only record** |

The existing HC4 automated/mock-backed tests remain separate evidence and do
not close this real-provider gate. Drive/live, B1/B2/B3/B4, and prior accepted
RC evidence are unchanged. No retention, RC2, tag, or release activity was
performed.

## RC1-R5 — HC4 Real WeightRecord & Freshness Watermark Validation

This round is based on accepted R4-R2 commit
`1365aea899c063450f3bbe831e2415a133db86c9`. It stopped at the explicit
real-source gate as **R5-E1 — no real HC WeightRecord injection source**.
There were no production or test changes and no temporary writer fixture.

### Real-provider source check

API37 Fold was the selected stable device: AVD `Pixel_10_Pro_Fold`, serial
`emulator-5556`, API37, Android 17, model `sdk_gphone16k_x86_64`, display
2076x2152, density 390. The installed Health Connect controller was
`com.google.android.healthconnect.controller` version 17. Evolune was
`io.github.yingqiu0871.evolune.debug` version 1.2.0-debug; its
`READ_WEIGHT` permission was declared but not granted at inspection, and
`WRITE_WEIGHT` was not declared.

The installed third-party inventory contained no package requesting
`android.permission.health.WRITE_WEIGHT`. `com.mkx.hrttracker` requested only
notification permission. The Health Connect shell interface exposed only
step-record insertion/read/delete commands, not WeightRecord insertion. No
existing owner health app, toolbox, or physical-device writer was available.
Consequently, no real `WeightRecord` was injected and no mock, fake gateway,
JVM fixture, or hard-coded response was used as a substitute.

### HC4 source inspection evidence

| Concern | Current source result |
|---|---|
| `bodyWeight` authority | `SettingsDataStore.bodyWeight` |
| Freshness owner | `SettingsDataStore` / `settings` DataStore |
| Freshness keys | `last_health_connect_weight_kg` and `last_health_connect_weight_adopted_at` |
| Sync toggle key | `health_connect_weight_sync_enabled` |
| Comparison unit | `Instant`, using `isAfter`, persisted as ISO-8601 text |
| Trigger | foreground `HealthConnectWeightSyncCoordinator.onForeground()` |
| Same-value newer record | metadata-only update; local body weight remains unchanged |
| Provider path | Health Connect SDK `WeightRecord.time` and data-origin package |

### RC1-R5 result

| Gate | Result | Evidence |
|---|---|---|
| Real WeightRecord writer | **BLOCKED** | R5-E1; no qualifying source |
| Toggle-OFF hard gate | **NOT TESTED** | No real record |
| First adoption | **NOT TESTED** | No real record |
| Restart #1 / #2 | **NOT TESTED** | Source gate blocked |
| Stale HC-A protection | **NOT TESTED** | No real record |
| Newer HC-B adoption | **NOT TESTED** | No real record |
| Same-value HC-C watermark proof | **NOT TESTED** | No real record or live proof |
| Permission revoke sanity | **NOT TESTED** | Source gate blocked |
| 30-day query sanity | **NOT RUN** | Optional; source unavailable |
| HC4 real live gate | **BLOCKED** | R5-E1 |

No app state, permission, or DataStore values were changed. Existing HC4
automated/mock-backed tests remain separate evidence and do not close the real
provider gate. API35 remains parked and environment-blocked. Drive remains
**CLOSED / PASS** and was not rerun. No RC2, tag, or release activity was
performed.
