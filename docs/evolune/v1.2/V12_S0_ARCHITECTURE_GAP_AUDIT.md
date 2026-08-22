# Evolune v1.2 S0 — Architecture & Gap Audit

**Date:** 2026-08-22
**Target milestone:** v1.2 — Google Integration & Data Continuity
**S0 rule:** documentation/architecture only; production-code diff must remain zero.

## 1. Executive verdict

**S0 architecture verdict: APPROVED TO PROCEED to HC1 and B1 after the S0 branch/doc commit exists.**

The current v1.1 architecture is sufficient to start v1.2 without a Room v4 migration. The two v1.2 tracks must remain independently testable:

1. **Health Connect:** optional, read-only weight integration with explicit user adoption into Evolune's existing local weight setting.
2. **Data continuity:** provider-independent, versioned backup/restore first; Google Drive is a storage provider, not the backup model and not a live-sync database.

The existing local data model remains authoritative. Health Connect and Google Drive are external integration layers only.

## 2. Baseline reconciliation

The live GitHub `main` branch was independently verified at:

- `cc1f963f315782163e9e64820ac98177737ac974`
- merge message: `Merge pull request #18 from YingQiu0871/docs/v1.1-post-release-sync`
- first parent: `ea7bb92151ae73126703e54b6e48bf0fd5bdb09e`
- second parent: `29ca5ec145ea91842a55242d3a14957a3dd78eb7`

This explains the local S0 audit discrepancy: the clean worktree at `29ca5ec` and stale local `origin/main` at `ea7bb921` are the two parents of the remote merge commit. A successful fetch should converge the local remote-tracking ref to `cc1f963`.

Local audit also reported the protected `D:\Evolune` inventory as 26 tracked changes + 194 untracked entries = 220 status entries. That protected root is out of scope and must remain untouched.

## 3. Current-state inventory

### 3.1 Platform and dependencies

Current phone app:

- `minSdk = 31`
- `targetSdk = 36`
- `compileSdk = 36.1`
- Room `2.8.4`
- Room schema version `3`
- no Health Connect dependency
- no Credential Manager / Google authorization dependency
- no Google Drive API integration

The app's minimum SDK means Health Connect only needs two runtime families in v1.2:

- Android 12–13 (API 31–33): separate Health Connect provider app may be required.
- Android 14+ (API 34+): Health Connect is part of the Android framework.

### 3.2 Weight model

Weight is currently a **single scalar preference** in `SettingsDataStore`, not a Room entity and not a historical series.

This is compatible with a v1.2 Health Connect MVP that lets the user inspect an external `WeightRecord` and explicitly choose **Use this weight**. It is not compatible with automatic historical weight synchronization without introducing a new local model.

### 3.3 PK recalculation gap

`HRTViewModel` currently captures `bodyWeightKG` in its constructor. The PK calculation input already supports a weight parameter, but the ViewModel does not observe weight changes after construction.

Therefore a Health Connect import that only updates `SettingsDataStore` is insufficient: the current process can keep calculating with the old weight until the ViewModel is recreated.

**Required HC fix:** make the ViewModel's simulation trigger react to the authoritative local weight value. Do not make Health Connect itself a PK dependency.

### 3.4 Room/domain/repository state

Room v3 already provides:

- stable IDs for dose events and medication plans;
- explicit plan/slot persistence;
- repository boundaries;
- transaction usage for plan + slot replacement;
- idempotent/conflict-aware dose-event insertion;
- migration and schema test infrastructure.

This is sufficient for v1.2 if backup restore introduces a dedicated whole-snapshot restore transaction boundary. Existing public repository methods are not sufficient for atomic whole-database restore when used as `deleteAll()` + repeated `save()`/`insert()` calls.

### 3.5 Mahiro JSON v1 compatibility boundary

Mahiro JSON v1 currently represents:

- scalar weight;
- dose events.

It does **not** represent the complete v1.1 local state (notably medication plans and scheduled slots). Its import path is incremental and may leave accepted earlier rows persisted if a later storage operation fails.

**Decision:** Mahiro JSON v1 remains a compatibility/import-export format. It must not be promoted into the v1.2 cloud-backup format or silently change semantics.

### 3.6 Android Auto Backup / device transfer

Current rules intentionally exclude the relevant app-private data domains from Android cloud backup and device-to-device transfer.

**Decision:** no change to `backup_rules.xml` or `data_extraction_rules.xml` in v1.2. User-controlled backup/restore must coexist with those exclusions.

## 4. Health Connect architecture freeze

### 4.1 v1.2 supported scope

Health Connect v1.2 MVP SHALL support only:

- `WeightRecord`
- read-only permission
- foreground, explicit user action
- read recent records only
- preview latest usable external weight
- explicit **Use this weight** action
- update the existing local scalar weight after confirmation
- trigger PK recalculation through the local weight state

It SHALL NOT include:

- Health Connect writes;
- medication/PHR writes;
- automatic foreground sync;
- background reads;
- historical-read permission;
- periodic polling;
- silent replacement of local weight;
- a Room historical-weight table.

### 4.2 Dependency/version policy

Pin stable:

`androidx.health.connect:connect-client:1.1.0`

Do not use `1.2.0-alpha*` in the production v1.2 branch without a separately approved dependency decision.

### 4.3 Permission policy

Request only the weight read permission associated with `WeightRecord` (`android.permission.health.READ_WEIGHT`).

Do not request:

- `WRITE_WEIGHT`
- `READ_HEALTH_DATA_HISTORY`
- `READ_HEALTH_DATA_IN_BACKGROUND`

Permission must be checked at every operation because the user can revoke it outside Evolune.

### 4.4 Availability policy

At every Health Connect operation:

1. query SDK/provider status;
2. distinguish available / provider update required / unavailable;
3. acquire a client only when available;
4. check current granted permissions;
5. perform the read;
6. surface recoverable UI state rather than crash.

### 4.5 Record-selection semantics

Default query window: recent 30 days. No historical permission is requested.

Select the most recent valid `WeightRecord` by record time. Present:

- weight in kg;
- recorded time;
- source/data origin when available;
- freshness/staleness state.

No record is a valid empty result, not an error.

### 4.6 Local adoption semantics

`Use this weight` is a local mutation and must:

1. validate the external value against the same safe local range used by settings;
2. write the scalar through the existing settings boundary;
3. update the reactive local weight state;
4. trigger/recompute PK through local state;
5. show a success/failure result.

Health Connect remains external observation provenance; it never becomes the PK source of truth.

## 5. Google backup/restore architecture freeze

### 5.1 Product semantics

v1.2 is **backup/restore**, not live multi-device sync.

Supported actions:

- user-triggered create backup;
- list available Evolune backups;
- preview restore contents;
- explicit destructive confirmation;
- replace local state from a validated backup;
- retain a small number of generations.

Not in v1.2:

- background periodic sync;
- automatic pull on startup;
- bidirectional conflict merge;
- record-level cloud reconciliation;
- Drive as an authoritative database.

### 5.2 Provider-independent backup model

Define a new Evolune backup format. It must not reuse the Mahiro JSON v1 schema.

Logical payload v1 covers:

- medication plans;
- scheduled dose slots;
- dose events;
- supported cross-device settings;
- current local scalar weight.

Exclude:

- device-bound secrets/credentials;
- OAuth access tokens;
- Google account tokens;
- temporary caches;
- diagnostic logs;
- Wear last-known caches;
- Health Connect external observations not explicitly adopted locally.

### 5.3 Envelope requirements

The envelope must be independently parseable and versioned, with at least:

- magic/application identifier;
- envelope format version;
- payload schema version;
- creation timestamp;
- producing app version/code;
- canonical payload bytes or encrypted payload;
- integrity/authentication metadata;
- algorithm identifiers required for restore.

Unknown future versions must fail safely before any local mutation.

### 5.4 Confidentiality recommendation

Medication history and weight are sensitive data. The preferred v1.2 design is client-side authenticated encryption before upload, with a user-controlled cross-device secret/passphrase.

Recommended primitive set using platform-supported crypto:

- AES-256-GCM for payload encryption/authentication;
- PBKDF2-HMAC-SHA-256 (or a separately approved memory-hard KDF dependency) to derive a key from the user secret;
- random per-backup salt and nonce;
- versioned KDF parameters stored in the clear envelope header.

The exact KDF work factor must be benchmarked on supported devices before release. Secrets must never be uploaded in recoverable plaintext form.

If client-side encryption is intentionally deferred, that is a release-gate decision requiring explicit owner approval and a clear disclosure that Google Drive is inside the confidentiality trust boundary.

### 5.5 Restore safety

Restore order:

1. download/read bytes;
2. authenticate/decrypt (if encrypted);
3. parse envelope;
4. validate format and supported version;
5. validate all domain objects and referential relationships;
6. produce a user preview (counts, timestamp, version, affected settings);
7. require explicit confirmation;
8. create a local pre-restore recovery snapshot/journal;
9. replace Room state in one database transaction;
10. apply DataStore settings;
11. verify postconditions;
12. clear the recovery journal only after success.

Because Room and DataStore cannot share one ACID transaction, the restore implementation needs compensating recovery. A small app-private restore journal in `noBackupFilesDir` is preferred over Room v4 solely for journaling.

If process death occurs mid-restore, the next app start must detect the journal and either finish a known-safe operation or roll back to the pre-restore snapshot before normal use.

### 5.6 Google authorization

Authentication and authorization remain separate concepts.

- Credential Manager may be used to establish/display the selected Google account when the backup UX needs account identity.
- Drive access must use Google `AuthorizationClient` and be requested only when the user initiates a backup/restore action.
- No Drive authorization at first app launch.
- No backend/offline refresh token is needed for v1.2 manual foreground operations.

### 5.7 Drive scope/storage

Use only:

`https://www.googleapis.com/auth/drive.appdata`

Store backups in Drive `appDataFolder`.

Do not request broad `drive`, `drive.readonly`, or `drive.file` scope for the hidden app backup implementation.

### 5.8 Generation policy

Use immutable timestamped backup generations rather than continuously overwriting one object.

Default retention: **3 latest successful generations**.

Upload policy:

1. create new generation;
2. verify successful remote creation/metadata;
3. only then prune older generations best-effort;
4. cleanup failure must not invalidate the new backup.

## 6. Legacy-spec disposition

### `docs/legacy-specs/health-connect.md`

Retain as historical design reference only. For v1.2, explicitly superseded items include:

- automatic foreground sync;
- persistent sync toggles;
- medication/PHR write path;
- reactive export after local dose changes;
- automatic adoption of new external weight.

The legacy principle that Evolune local state remains authoritative is retained.

### `docs/legacy-specs/cloud-sync.md`

Retain as historical design reference only. For v1.2, explicitly superseded items include:

- live/bidirectional synchronization semantics;
- automatic periodic WorkManager sync;
- three-way local/cloud/last-sync conflict logic;
- cloud-side overwrite conflict UX as a normal path.

Retained principles:

- provider isolation;
- versioned snapshot format;
- client-side confidentiality goal;
- explicit destructive restore confirmation;
- restore validation before mutation.

## 7. Room v4 decision

**Room v4 is NOT required for v1.2 S0 scope.**

Reconsider Room v4 only if scope later adds one of:

- historical local weight/provenance records;
- durable restore state that cannot be represented safely outside Room;
- additional persisted cloud-sync state that is truly domain data;
- other schema-changing features.

## 8. S0 exit criteria

S0 is complete when all are true:

- remote `main` baseline is reconciled to `cc1f963...`;
- S0 docs exist on a dedicated v1.2 branch;
- production-code diff against `cc1f963...` is zero;
- no dependency changes occur in S0;
- no tag/release changes occur;
- HC and backup tracks have independent implementation/test plans;
- legacy-spec supersession is documented;
- risk register is accepted before HC1/B1 production implementation.

## 9. Official references checked for S0

- Android Health Connect availability/get started: https://developer.android.com/health-and-fitness/health-connect/get-started
- Health Connect Jetpack releases: https://developer.android.com/jetpack/androidx/releases/health-connect
- Health Connect data types/permissions: https://developer.android.com/health-and-fitness/health-connect/data-types
- Google authorization guidance: https://developer.android.com/identity/authorization
- Sign in with Google / Credential Manager: https://developer.android.com/identity/sign-in/credential-manager-siwg
- Drive app-specific data: https://developers.google.com/workspace/drive/api/guides/appdata
- Google Drive OAuth scopes: https://developers.google.com/identity/protocols/oauth2/scopes
