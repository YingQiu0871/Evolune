# Evolune v1.2 — Implementation Plan

**Milestone:** Google Integration & Data Continuity
**Baseline:** `cc1f963f315782163e9e64820ac98177737ac974`
**Rule:** Health Connect and backup/restore are independent tracks and must not block each other.

## 1. Branch and review model

Recommended branch sequence:

- `v1.2/s0-architecture-gap-audit` — docs only
- `v1.2/hc1-platform-seam`
- `v1.2/hc2-weight-import-pk-reactivity`
- `v1.2/hc3-hardening`
- `v1.2/b1-backup-envelope`
- `v1.2/b2-restore-transaction`
- `v1.2/b3-google-drive-provider`
- `v1.2/b4-backup-restore-ux`
- `v1.2/release-candidate`

Each branch should have a narrow diff and independent review evidence.

## 2. Global invariants

Throughout v1.2:

1. Room/domain/repository remains the authoritative medication model.
2. `SettingsDataStore` remains authoritative for current scalar weight unless a later schema migration is separately approved.
3. Health Connect is an external observation source only.
4. Drive is a remote snapshot provider only.
5. Mahiro JSON v1 remains unchanged as a compatibility format.
6. Android Auto Backup/device-transfer exclusions remain unchanged.
7. No direct commits to published tags or release branches.
8. No silent cloud upload.
9. No background Health Connect polling or cloud sync in v1.2 MVP.

---

# Track HC — Health Connect Weight

## HC1 — Platform seam and availability

### Goal

Introduce a testable Health Connect boundary without changing user data.

### Production changes

- Add stable dependency:
  - `androidx.health.connect:connect-client:1.1.0`
- Add manifest permission:
  - `android.permission.health.READ_WEIGHT`
- Add a small Health Connect abstraction, for example:
  - availability/status model;
  - granted-permission query;
  - latest-weight read operation;
  - platform implementation using `HealthConnectClient`;
  - fake implementation for JVM/UI tests.

### Required semantics

- status checked before client use;
- provider update required is distinct from unavailable;
- permission checked at operation time;
- query only recent weight records;
- no persistent sync state;
- no mutation of Evolune settings in HC1.

### Tests

- status mapping tests;
- permission-present/missing tests;
- latest-record selection;
- no-record result;
- malformed/out-of-range record rejection at the adapter boundary;
- provider exception mapping.

### Exit gate

- build passes;
- no settings/PK behavior change;
- no Health Connect write/historical/background permissions in manifest.

## HC2 — Settings UX, explicit adoption, and PK reactivity

### Goal

Let the user read the latest Health Connect weight and explicitly adopt it into Evolune.

### UI flow

Settings → Health Connect:

1. show availability state;
2. user taps **Read weight from Health Connect**;
3. if needed, request read permission contextually;
4. read latest recent weight;
5. show value, timestamp, and source when available;
6. user taps **Use this weight**;
7. update local scalar weight;
8. show confirmation;
9. PK recalculates from the updated local value.

### PK reactivity change

Replace constructor-captured weight semantics in `HRTViewModel` with a reactive local weight input.

Preferred implementation shape:

- `SettingsDataStore.userSettings` (or a mapped `Flow<Double>`) is injected/observed by `HRTViewModel`;
- simulation combine trigger includes current body weight;
- `PkSimulationInput.bodyWeightKG` remains the calculation parameter;
- changing weight cancels stale calculation via `collectLatest` and calculates again.

Do not inject `HealthConnectClient` into `HRTViewModel` or PK code.

### Compatibility

Manual weight editing and Mahiro JSON v1 weight import must go through the same authoritative settings update path and trigger the same reactive recalculation.

### Tests

- local weight change triggers one fresh simulation;
- Health Connect preview alone does not alter PK;
- explicit adoption alters local setting and simulation input;
- permission denied leaves settings unchanged;
- permission revoked between UI render and read is handled;
- stale/no record leaves settings unchanged;
- process recreation still reads local authoritative weight.

## HC3 — Real-device hardening

### Device matrix

At minimum:

- Android 12 or 13 with Health Connect provider installed;
- Android 12 or 13 with provider missing/update required;
- Android 14+ framework Health Connect;
- permission grant → revoke in system settings → return to Evolune.

### Acceptance

- no crash on missing provider;
- permission is never requested at app startup;
- no write permissions;
- no historical/background permissions;
- no automatic sync;
- local manual weight always remains usable without Health Connect.

---

# Track B — Backup / Restore / Google Drive

## B1 — Provider-independent backup envelope

### Goal

Create and validate a complete local snapshot format before adding any Google API.

### New logical model

Example high-level types:

- `EvoluneBackupEnvelopeV1`
- `EvoluneBackupPayloadV1`
- `BackupMedicationPlanV1`
- `BackupScheduledDoseSlotV1`
- `BackupDoseEventV1`
- `BackupSettingsV1`

Do not serialize Room entities directly as the public backup contract. Map through stable backup DTOs.

### Snapshot collection

Collect a coherent logical snapshot of:

- all plans;
- their stable slots;
- all dose events;
- supported settings including body weight.

Snapshot creation must be deterministic enough for reproducible validation/hash tests.

### Validation

Before an envelope is considered valid:

- IDs parse and are unique;
- slot → plan relationships resolve;
- enum values are known/supported;
- numeric values are finite and in allowed ranges;
- timestamps parse and fit supported persistence range;
- dose event revisions are valid;
- required source/status fields are present;
- settings pass existing validation rules.

### Crypto/integrity

Preferred v1.2 path:

- canonical payload serialization;
- authenticated encryption (AES-256-GCM);
- user-controlled cross-device secret/passphrase;
- versioned KDF parameters;
- authentication failure before JSON/domain parsing of plaintext.

Keep crypto behind an interface so codec/domain tests can use deterministic test vectors.

### Tests

- golden round-trip fixture;
- canonicalization determinism;
- wrong secret fails before restore;
- tampered ciphertext/header fails;
- unknown version fails;
- malformed payload fails;
- all v1.1 domain categories are represented;
- Mahiro JSON v1 codec/tests remain unchanged.

### Exit gate

B1 can be merged without Google dependencies.

## B2 — Restore preview and transactional replacement

### Goal

Make a downloaded/local backup safe to restore before connecting Drive.

### Restore pipeline

1. decode/authenticate;
2. parse;
3. validate complete snapshot;
4. build preview;
5. explicit user confirmation;
6. write durable local restore journal containing pre-image/target metadata;
7. replace Room state inside `AppDatabase.withTransaction`;
8. update DataStore-supported settings;
9. re-read and verify postconditions;
10. delete journal;
11. trigger normal dependent refreshes (PK, reminders, widgets, Wear snapshot) only after authoritative persistence succeeds.

### Required new persistence boundary

Add a dedicated restore transaction component near the data layer. It may use DAOs/entities internally but must expose a snapshot-level API to application code.

Do not implement restore as public repository loops such as:

`deleteAll()` → `save()` → repeated `insert()`.

### Side-effect ordering

Follow the established persistence-before-side-effects principle:

- no reminder reschedule;
- no widget refresh;
- no Wear Data Layer publication;
- no success UI

until the local restore commit and settings application are known good.

### Recovery journal

Preferred location: app-private `noBackupFilesDir`.

On startup, before normal mutation paths:

- if no journal: continue normally;
- if complete/success marker: cleanup;
- if interrupted restore: restore pre-image or complete only if state machine proves it safe.

No Room v4 is required solely for this journal.

### Tests

- successful full replace;
- Room failure rolls back Room transaction;
- DataStore failure triggers compensating recovery;
- simulated process interruption leaves detectable journal;
- restart recovery restores a consistent state;
- preview never mutates local data;
- invalid backup cannot reach mutation phase;
- reminders/widgets/Wear not refreshed on failed restore.

## B3 — Google account authorization and Drive provider

### Goal

Connect the already-tested backup engine to Google Drive without changing backup semantics.

### Authorization

- Drive permission is requested only after user taps a backup/restore action.
- Use `AuthorizationClient` for Google Drive authorization.
- Use only `drive.appdata` scope.
- Credential Manager may be used before authorization to establish/display the selected account.
- Do not request backend/offline access for manual foreground v1.2 operations.
- Treat token expiry/revocation as recoverable re-authorization states.

### Drive provider operations

Provider interface should support approximately:

- `listBackups()`
- `uploadBackup(bytes, metadata)`
- `downloadBackup(id)`
- `deleteBackup(id)`

Google implementation:

- space/parent: `appDataFolder`;
- immutable generation per successful backup;
- deterministic Evolune filename prefix plus timestamp/UUID;
- list only Evolune-managed backup objects;
- retain latest 3 successful generations.

### Upload safety

1. produce final encrypted/validated bytes locally;
2. upload new generation;
3. confirm creation/result metadata;
4. only then prune old generations;
5. cleanup failure is warning/non-fatal.

### Tests

Use provider fakes for most tests. Real Drive tests are separate/manual or dedicated integration tests and must never operate on `main` release data without a test account.

Cases:

- authorization required;
- authorization cancelled;
- token expired;
- list empty;
- upload success;
- upload network failure;
- download corruption;
- three-generation retention;
- cleanup failure;
- account change.

## B4 — Backup/restore UX

### Settings UI

Recommended user-facing section:

**Backup & restore**

- Google account / connection state
- **Back up now**
- last successful backup timestamp
- **Restore from backup**
- optional **Disconnect Google account** / reselect account behavior

No always-on sync toggle in v1.2.

### Backup flow

1. explain what will be uploaded and encryption/password implications;
2. authenticate/select account if needed;
3. authorize Drive app-data scope if needed;
4. obtain/unlock backup secret;
5. create snapshot;
6. upload;
7. confirm success and generation timestamp.

### Restore flow

1. authorize/select account;
2. list generations;
3. choose generation;
4. download;
5. unlock/decrypt;
6. validate;
7. show preview;
8. explicit destructive confirmation;
9. restore;
10. show success only after local verification.

### UX error classes

Separate at least:

- no Google account/credential;
- authorization denied/cancelled;
- network unavailable;
- no backups;
- wrong password/secret;
- corrupted/tampered backup;
- unsupported future backup version;
- local restore/storage failure.

---

# 3. Shared QA and release gates

## Automated gates

Run at minimum:

- app JVM tests;
- experience-core tests;
- Room migration/schema tests;
- repository contract tests;
- Mahiro JSON v1 regression tests;
- new Health Connect adapter tests;
- new backup codec/restore tests;
- widget/Wear regression tests affected by post-restore refresh.

`git diff --check` must pass.

## Upgrade gates

Test:

- clean install v1.2;
- upgrade installed v1.1.0 → v1.2;
- existing Room v3 data preserved;
- existing settings preserved;
- Health Connect disabled/unavailable does not affect core app;
- Google never authorized does not affect core app;
- Android backup exclusion rules remain unchanged.

## Real-device matrix

Health Connect:

- API 31–33 provider installed/missing/revoked;
- API 34+ framework.

Drive:

- at least one production-like Google account;
- account re-authorization;
- network loss during upload/download;
- reinstall/new-device restore drill.

## Release blockers

Do not release v1.2 with any of:

- P0/P1 local data-loss bug;
- restore can partially persist without recovery;
- backup format cannot represent complete current canonical data;
- Health Connect can silently overwrite local weight;
- PK does not react to adopted/manual/imported weight changes;
- broad Drive scope requested;
- background cloud sync introduced unintentionally;
- Auto Backup exclusions removed without separate approval;
- v1.1 tag/release mutated.

## 4. Proposed implementation order

The tracks can proceed independently, but recommended execution is:

1. S0 docs
2. HC1
3. B1
4. HC2
5. B2
6. HC3 real-device gate
7. B3
8. B4
9. integrated regression
10. v1.2 release candidate / owner device acceptance

This order exposes the highest-risk data-continuity work (backup format and restore safety) before Drive UX becomes the dominant implementation surface.
