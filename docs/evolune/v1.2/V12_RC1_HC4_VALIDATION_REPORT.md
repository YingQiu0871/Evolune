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
