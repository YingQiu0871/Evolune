# Evolune v1.2 RC1 Validation Report

## Scope and identity

- Validation date: `2026-08-24`
- Branch: `v1.2/rc1-live-validation`
- Functional commit under test: `99ee3618fe8e5e84451bd010bfdd58281fd1ca90`
- Parent functional baseline: `45c1b440ba2a5ab3b362ff68cec8ddac08da3d40`
- Build target: v1.2.0 RC validation; no tag or release publication
- RC1 production-code changes: none

RC1 followed validation-first and no-silent-fixes policy. No production bug
was fixed on this branch. No `FAIL` reproduction was observed; the unresolved
release items below are `BLOCKED` or `NOT TESTED` because the required signing,
device, or live-service resources were unavailable.

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

Status: `NOT TESTED`.

On `2026-08-24`, `adb devices -l` returned no connected device or emulator.
Therefore no manufacturer/model, Android version, API level, Health Connect
implementation, build installation, permission flow, revoke/re-read,
WeightRecord preview/adoption, restart persistence, Activity recreation, or
API31–33 provider matrix evidence exists.

The provider-missing mapping remains covered by unit tests only. An installed
but outdated provider case was not constructed and remains `NOT TESTED`.

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

No tag, GitHub Release, Play release, or production-code fix was created by
RC1.
