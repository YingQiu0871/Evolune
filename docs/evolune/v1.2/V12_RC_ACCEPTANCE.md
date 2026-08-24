# Evolune v1.2 Release Candidate Acceptance

## RC0 identity and release rule

- RC0 branch: `v1.2/rc0-integration-freeze`
- Functional baseline: `45c1b440ba2a5ab3b362ff68cec8ddac08da3d40`
- Target final version: `1.2.0`
- Current gate state: `NOT RELEASED`

Gate statuses are intentionally explicit:

- `PASS` — evidence is recorded in this repository or attached owner evidence.
- `FAIL` — a reproducible regression blocks the gate.
- `NOT TESTED` — no qualifying evidence exists yet.
- `BLOCKED — ...` — external configuration, device, or service access is required.

Passing automated tests never substitutes for the owner-device, live Drive,
KDF-device, end-to-end, or signed/minified release gates below.

## A. Automated regression

- [x] `PASS` — `:app:testDebugUnitTest --rerun-tasks`
- [x] `PASS` — `:experience-core:test --rerun-tasks`
- [x] `PASS` — `:wear:testDebugUnitTest --rerun-tasks`
- [x] `PASS` — `:app:assembleDebug --rerun-tasks`
- [x] `PASS` — `:wear:assembleDebug --rerun-tasks`
- [ ] `NOT TESTED` — API33 instrumentation on a connected target
- [ ] `NOT TESTED` — API35 instrumentation on a connected target
- [x] `PASS` — HC1, HC2 R-09, HC3, B1 golden, B2 crash matrix, B3, B4,
  Mahiro v1, and Room/schema regression slices

## B. Health Connect owner-device gate

The following HC3 items remain `NOT TESTED` until owner evidence is supplied.
They block v1.2 release but do not block post-RC development planning.

- [ ] `NOT TESTED` — API31/32 physical or emulator compatibility
  - provider installed/current
  - permission grant/deny
  - weight read where the environment permits
- [ ] `NOT TESTED` — API33 provider installed/current
  - availability
  - permission
  - read/no-data behavior
- [ ] `NOT TESTED` — API31–33 real provider update-required case
  - distinct from provider missing
- [ ] `NOT TESTED` — physical Android device
  - `READ_WEIGHT` grant
  - revoke
  - re-read after revoke
- [ ] `NOT TESTED` — real valid `WeightRecord`
  - preview displays weight/time/source
  - local weight remains unchanged before confirmation
  - `Use this weight` updates authoritative weight
  - PK recalculates
- [ ] `NOT TESTED` — adopted weight survives force-stop/restart
- [ ] `NOT TESTED` — permission request plus Activity recreation
  - no unsolicited second permission launch
  - retry remains available after denial

## C. Google Drive live RC gate

Current status for every live-service item:
`BLOCKED — external Google Cloud OAuth configuration and an approved live
account/device are required.` No live PASS is inferred from fake B3 tests.

- [ ] `BLOCKED` — authorize with `drive.appdata` only
- [ ] `BLOCKED` — verify no offline access
- [ ] `BLOCKED` — upload synthetic encrypted B1 backup
- [ ] `BLOCKED` — readback verification
- [ ] `BLOCKED` — `listBackups()` sees the generation
- [ ] `BLOCKED` — bounded download
- [ ] `BLOCKED` — B1 decrypt
- [ ] `BLOCKED` — four generations retain exactly the latest three
- [ ] `BLOCKED` — verified current generation is never self-pruned
- [ ] `BLOCKED` — disconnect/revoke leaves backups intact
- [ ] `BLOCKED` — backup is absent from normal My Drive UI

## D. Backup → restore end-to-end gate

- [ ] `BLOCKED — live Drive unavailable` — canonical local state A
  → manual backup → live B3 upload/readback → change local state to B
  → generation picker → download → passphrase → preview → destructive
  confirmation → B2 restore → semantic state equals A
- [ ] `NOT TESTED` — plans, slots, events, and dangling `event.slotId`
- [ ] `NOT TESTED` — body weight, theme, color theme, auto-update, time format
- [ ] `NOT TESTED` — PK, reminders, widgets, and Wear settle to a reasonable
  post-restore state

## E. Security and performance gate

- [ ] `NOT TESTED` — KDF benchmark on a real target device
  - PBKDF2-HMAC-SHA256
  - 600,000 iterations
  - device, SoC, Android version, repeated encode/decode duration, median,
    worst observed
- [x] `PASS` — representative large-history backup/restore-preview JVM
  sanity; no obvious OOM, ANR, or unexpected main-thread blocking
- [x] `PASS` — no passphrase/token persistence or logging in the RC0 code path
- [x] `PASS` — corrupt, wrong-secret, unsupported, and invalid local data fail
  before restore mutation

## F. Signed and minified release gate

- [ ] `BLOCKED — release signing credentials unavailable` — release signing
  credentials and keystore availability
- [ ] `BLOCKED — release signing credentials unavailable` — app release build
  signed and R8/minification completed
- [ ] `BLOCKED — release signing credentials unavailable` — Wear release build
  signed and R8/minification completed
- [ ] `BLOCKED — release APKs unavailable` — `apksigner verify` for both APKs
- [ ] `BLOCKED — release APKs unavailable` — signer certificate SHA-256 recorded and matches the
  approved shared signing identity
- [ ] `NOT TESTED` — release smoke: startup, B1 codec, restore journal, HC,
  AuthorizationClient, Drive REST JSON, and Settings Backup/Restore dialogs

No keystore password, key password, private key, or token belongs in this
document.

## G. Final release metadata gate

- [x] `PASS` — Phone and Wear use shared `versionName = 1.2.0`
- [x] `PASS` — Phone version code is `101020000`
- [x] `PASS` — Wear version code is `1101020000`
- [x] `PASS` — RC0 does not move or recreate v1.0/v1.1 tags
- [x] `PASS` — no GitHub Release, Play release, or v1.2.0 tag is created by
  RC0
- [ ] `NOT TESTED` — final release notes and owner evidence review

## Release blockers before RC1/final release

The v1.2 release remains blocked until all of the following are PASS:

1. Health Connect owner-device gate;
2. Google Drive live gate;
3. live Backup → Restore E2E gate;
4. real-device KDF benchmark;
5. signed/minified release smoke and signer verification; and
6. final release metadata/evidence review.

RC0 is an integration-freeze branch and is not itself a release publication.
