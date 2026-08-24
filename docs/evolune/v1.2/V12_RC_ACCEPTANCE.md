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

## A. Automated regression

- [x] `PASS` — App unit tests — evidence: `2026-08-24 | local Windows | :app:testDebugUnitTest --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL; 574 tests, 0 failures/errors`
- [x] `PASS` — experience-core tests — evidence: `2026-08-24 | local Windows JVM | :experience-core:test --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [x] `PASS` — Wear unit tests — evidence: `2026-08-24 | local Windows | :wear:testDebugUnitTest --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL; 27 tests, 0 failures/errors`
- [x] `PASS` — App debug assemble — evidence: `2026-08-24 | local Windows | :app:assembleDebug --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [x] `PASS` — Wear debug assemble — evidence: `2026-08-24 | local Windows | :wear:assembleDebug --rerun-tasks | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | BUILD SUCCESSFUL`
- [ ] `NOT TESTED` — API33 instrumentation — evidence: `2026-08-24 | no connected Android device/AVD | connected test unavailable | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | adb devices -l returned no devices`
- [ ] `NOT TESTED` — API35 instrumentation — evidence: `2026-08-24 | no connected Android device/AVD | connected test unavailable | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | adb devices -l returned no devices`
- [x] `PASS` — HC1, HC2 R-09, HC3, B1 golden, B2 crash matrix, B3, B4, Mahiro v1, and Room/schema JVM slices — evidence: `2026-08-24 | local Windows | App/Wear unit regression | 99ee3618fe8e5e84451bd010bfdd58281fd1ca90 | relevant suites passed; no failures/errors`

## B. Health Connect owner-device gate

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
