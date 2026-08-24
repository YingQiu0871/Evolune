# Evolune v1.2 RC0-HC4 Integration Freeze Report

## 1. Release identity and scope

- Cycle: `RC0-HC4 — Post-HC4 Integration Freeze`
- Evidence date: `2026-08-24`
- Base commit: `19b8e8b873938e59a30274038243a21cc141842d`
- Accepted HC4 functional commit: `6b6cc2a7b283fc0cf9286c68a8cb0c5293c9f37d`
- Branch: `v1.2/rc0-hc4-integration-freeze`
- Target version: `1.2.0`
- Release state: `NOT RELEASED`
- Scope: documentation-only freeze and evidence record

No Kotlin, Java, XML, test source, Gradle, manifest, Room, DataStore, tag, or
release file was changed in this cycle. No new RC1 cycle, live Drive session,
or physical owner-device validation was started.

## 2. Ancestry audit

Every milestone below was verified as an ancestor of the current base commit.

| Milestone | Commit | Ancestor of base |
|---|---|---|
| S0 | `1f142df8c166b5cc576ab2de2863e450d21c919b` | YES |
| HC1 | `05627b534b81cc9ecbf462e3dbd3e7983d68ca99` | YES |
| HC1-R1 | `6502719066b4122ddac28422bf464edb5485503f` | YES |
| B1 | `248a32892aa48b6027b12bd5c18ed3d19dfe39b9` | YES |
| B1-R1 | `7ef55f1312ba6bc521eabb1082af3c7649e83dfa` | YES |
| HC2 | `5930801e289e47243300cfa490c16c18f58ce58a` | YES |
| HC3 | `608406e4c79fabb3e93d2996820d7d1a31ecd9ca` | YES |
| HC3-R1 | `14ff01b5917a7b669a81c6e43e0e287c13e99003` | YES |
| B2 | `dc574f21842d7e706dac976658ac60250e69f4ab` | YES |
| B3 | `5e1cc3206f4328aff2d2abe05db28d015600bf0c` | YES |
| B3-R1 | `c51dd6e4093995f93d70335e4353e8adfcea8c7a` | YES |
| B4 | `45c1b440ba2a5ab3b362ff68cec8ddac08da3d40` | YES |
| RC1 live | `b0fee5c97f3fcea5acaa87aca3eb8f594ab9413d` | YES |
| RC1-T1 | `7891ec7e40da5f67c5afcc421dd9a1238e2fe574` | YES |
| RC1-Fix-Test | `4bb00aebcaca4376fcb601effac9bcb90c283efe` | YES |
| RC1-T2 | `94c5c54c9d4ce8836485e63e3aeacd7b9a33f490` | YES |
| HC4 | `6b6cc2a7b283fc0cf9286c68a8cb0c5293c9f37d` | YES |
| RC-Preflight T2 fix | `19b8e8b873938e59a30274038243a21cc141842d` | YES |

## 3. Automated regression and builds

| Area | Result |
|---|---|
| App JVM unit tests | `PASS`, `575/575`, 0 failures/errors |
| `experience-core` tests | `PASS`, `38/38`, 0 failures/errors |
| Wear JVM unit tests | `PASS`, `27/27`, 0 failures/errors |
| App debug assemble | `PASS`, `:app:assembleDebug --rerun-tasks` |
| Wear debug assemble | `PASS`, `:wear:assembleDebug --rerun-tasks` |
| Identity/version validation | `PASS`, `validateEvoluneIdentityAndVersioning` |

The focused regression selection was `100/100`, with zero failures/errors:

- `HealthConnectWeightSyncCoordinatorTest`: `13/13`
- `HRTViewModelTest`: `17/17`
- `EvoluneBackupCodecTest`: `17/17`
- `B2RestoreTransactionTest`: `13/13`
- `BackupRestoreCoordinatorTest`: `16/16`
- `GoogleDriveBackupProviderTest`: `21/21`
- `GoogleDriveRestGatewayTest`: `3/3`

## 4. Full instrumentation matrix

All three online AVDs completed the full
`:app:connectedDebugAndroidTest --rerun-tasks` run. JUnit XML is the source of
the counts below; skipped tests are included in total.

| Device | Serial | Model | API / Android | Total | Passed | Failed | Skipped | Result |
|---|---|---|---|---:|---:|---:|---:|---|
| API33-A | `emulator-5554` | `sdk_gphone64_x86_64` | API33 / Android 13 | 152 | 149 | 0 | 3 | PASS |
| API33-B | `emulator-5556` | `sdk_gphone64_x86_64` | API33 / Android 13 | 152 | 149 | 0 | 3 | PASS |
| API37 Fold | `emulator-5558` | `sdk_gphone16k_x86_64` | API37 / Android 17 | 152 | 150 | 0 | 2 | PASS |

API35 was not online and remains `NOT TESTED` where specifically required by
the acceptance gates.

## 5. RC1-T2 probe closure evidence

`RealAppImeFrameProbeTest` passed on all three devices after the T2 test-only
probe correction. Final logcat verdicts were:

- API33-A: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1307px`, app root `1080x1920`, `bouncingCycles=0/5`.
- API33-B: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1307px`, app root `1080x2400`, `bouncingCycles=0/5`.
- API37 Fold: `imeOpenedCycles=5/5`, `occludingCycles=5/5`,
  `maxImeInset=1096px`, app root `2076x2152`, `bouncingCycles=0/5`.

No `Coordinate-space mismatch` or `Ambiguous` probe log was present. The
manual API33-B editor/IME/save path also completed without a crash or
unexpected second dialog. This closes the historical T2 probe blocker only;
it is not owner-device or physical-device acceptance evidence.

## 6. HC4 UI regression

`HealthConnectSyncScreenTest` passed `6/6` on API33-A, API33-B, and API37.
The scenarios cover the Health Connect page and entry, foreground-only copy,
weight sync states, permission reauthorization, connected metadata/no-data,
unavailable, update-required, and the disabled medication placeholder.

## 7. HC4 authority, freshness, and restart audit

The implementation preserves the provider-neutral authority boundary:

- Health Connect is an observation source.
- `SettingsDataStore.bodyWeight` remains Evolune's authoritative local value.
- HRT receives `bodyWeightFlow` from SettingsDataStore.
- PK simulation consumes that body-weight value and has no direct Health
  Connect provider dependency.

The foreground coordinator uses a strict observation watermark. The tested
sequence of a Health Connect observation, a manual local edit, a stale repeat,
and a newer observation preserved the manual value against the stale record
and adopted the newer record. A same-value newer observation advanced
metadata without rewriting the body value. Coordinator recreation read the
persisted enabled preference and watermark. Foreground sync calls are
serialized by one mutex. The focused coordinator suite passed `13/13`.

## 8. Atomic adoption and B1 compatibility

Adoption writes the body weight, last observed weight, and adoption timestamp
in one `DataStore.edit`. Same-value newer observations update only observation
metadata in one edit and do not create a body-weight write.

B1 restore remains compatible because the B1 settings envelope contains only
body weight, theme mode, color theme, auto-update, and time format. Its restore
transaction writes only those B1 fields and does not enable Health Connect
sync or overwrite its device-local preference/metadata keys. This behavior is
supported by `EvoluneBackupCodecTest` and `B2RestoreTransactionTest`; no live
owner-device restore gate is claimed here.

B1 golden envelope evidence remains exact SHA-256:
`5cbc47bc978c23abcd3e6cbafcad25d4e96208a13dfff6e4a8f5e174349eeaa9`.

## 9. Health Connect permission and medication audit

The debug merged manifest contains only `android.permission.health.READ_WEIGHT`.
The prohibited permission sweep found no `WRITE_WEIGHT`,
`READ_HEALTH_DATA_IN_BACKGROUND`, `READ_HEALTH_DATA_HISTORY`,
`WRITE_MEDICAL_DATA`, or `READ_MEDICAL_DATA`.

Release merged-manifest evidence was not generated because release signing
credentials are unavailable. No prohibited permission was found in the
production-source scan.

The medication sync switch is disabled with the explicit `暂未开放，计划在
v1.8 评估` copy. No Health Connect medication/medical-record sync API or
background medication sync path was found. Existing medication reminders and
records are unrelated v1.2 functionality. Medication sync remains deferred
to v1.8+.

## 10. Foreground-only and battery audit

No WorkManager, periodic worker, JobScheduler, foreground service, or
background Health Connect polling path was found. Health Connect synchronization
is triggered from the app foreground lifecycle. Existing AlarmManager-based
medication reminders, boot rescheduling, and notifications are separate
reminder functionality and are not Health Connect polling.

## 11. Google Drive scope and authorization audit

The Drive contract requests only:
`https://www.googleapis.com/auth/drive.appdata`.

The request factory only sets requested scopes. There is no offline-access,
server-auth-code, refresh-token storage, `drive.file`, or `drive.readonly`
path. Authorization state is an in-memory access token; a 401 clears it and
requires authorization again. Provider and gateway focused tests passed
`21/21` and `3/3` respectively.

This is static/unit evidence only. Live OAuth, Drive retention, disconnect,
and service-backed backup/readback remain release-blocking `BLOCKED` gates.

## 12. Settings and B4 integration sanity

Static wiring confirms the Settings page exposes Health Connect sync and
Backup & Restore entries, while the HRT/PK path remains provider-neutral.
On API33-B, the installed debug app reached Settings, opened Health Connect
Sync, displayed the foreground-only page and sync states, and returned to the
Settings page with Backup & Restore visible. This was an AVD smoke check; it
is not live Drive or physical owner-device evidence.

## 13. Lint, version, and release signing

`:app:lintDebug` remains the known repository baseline failure:
`45 errors, 97 warnings, and 1 hint`, led by historical `MissingTranslation`
findings such as `widget_loading`. The production-source diff from the base
is empty, and no HC4-specific lint delta was found. The baseline was not
modified in this documentation-only cycle.

Version validation passed with:

- `versionName = 1.2.0`
- Phone version code `101020000`
- Wear version code `1101020000`

Release signing environment variables
`EVOLUNE_KEYSTORE_PATH`, `EVOLUNE_KEYSTORE_PASSWORD`, `EVOLUNE_KEY_ALIAS`,
and `EVOLUNE_KEY_PASSWORD` were absent. Signed/minified App and Wear release
builds, R8 runtime validation, signer verification, and release smoke are
therefore `BLOCKED`; no random key or signing bypass was used.

## 14. Fresh RC1 blockers

The following remain open for a future owner-controlled release decision:

1. API31/32 and API33 Health Connect compatibility and permission evidence.
2. Provider update-required evidence distinct from provider missing.
3. Physical Android grant/revoke/re-read and real `WeightRecord` preview and
   adoption evidence.
4. Restart persistence and PK-after-restart evidence on an owner device.
5. Activity recreation and permission retry evidence.
6. Live Google Drive OAuth, app-data retention, disconnect, and backup/readback
   evidence.
7. Live Backup → Restore end-to-end semantic verification.
8. Real-device KDF benchmark and large-history sanity.
9. Signed/minified App and Wear release, R8, signer, and release smoke gates.
10. Final owner evidence review.

These blockers are not resolved by the automated or AVD evidence in this
report. The branch is frozen at RC0-HC4 integration documentation and awaits
independent review; it does not enter RC1.
