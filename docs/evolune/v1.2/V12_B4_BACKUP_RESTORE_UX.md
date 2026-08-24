# Evolune v1.2 B4 — Backup and Restore UX

## Scope

B4 is the manual foreground orchestration layer for the already-frozen B1
encrypted envelope, B2 crash-safe restore transaction, and B3 Google Drive
provider. Settings entry is local-only: opening Settings performs no Google
authorization, Drive list, upload, download, or passphrase request.

There is no automatic backup, startup cloud query, WorkManager sync, merge
restore, Credential Manager, ID token, backend, or broader Drive scope.

## Backup state flow

```text
Back up now
  -> AuthorizationClient authorization / one-shot resolution if required
  -> passphrase + confirmation
  -> authoritative Room snapshot + current SettingsDataStore snapshot
  -> B1 validate and encrypt
  -> B3 upload, read-back verification, retention
  -> verified success
```

The snapshot source reads plans, scheduled slots, and dose events from one Room
read transaction, then reads the current scalar settings snapshot from
SettingsDataStore. These stores do not share an ACID transaction; B4 does not
claim otherwise. The backup payload contains plans, slots, events, body weight,
theme mode, color theme, update-check preference, and time format. It excludes
Health Connect observations, Wear/widget caches, journal data, UI state, and
Google tokens.

An invalid local snapshot stops before upload with a stable local-data error.
Only B3 `verified = true` is a completed backup. A verified backup with pending
retention cleanup is shown as saved successfully with a cleanup warning.

## Restore state flow

```text
Restore from backup
  -> AuthorizationClient authorization / one-shot resolution if required
  -> B3 listBackups()
  -> generation picker (creation time; at most three normal generations)
  -> bounded B3 download
  -> passphrase
  -> B1 decrypt and validate
  -> restore preview
  -> explicit destructive confirmation
  -> B2 RestoreTransaction.restore()
  -> post-restore refresh reconciliation
```

An empty list shows `No backups found` and does not request a passphrase. The
preview is created after download/decryption and before any persistence
mutation. It includes creation time, safe producer metadata when available,
plan/slot/event counts, body weight, and settings summary.

## Passphrase lifetime

Backup creation requires a non-empty passphrase and matching confirmation.
Restore requires only a non-empty passphrase so older valid backups are not
rejected by a newly invented length rule. Passphrases are transient UI state and
are cleared when the dialog closes or the operation completes. They are not
written to DataStore, SharedPreferences, SavedStateHandle, rememberSaveable,
Room, files, cloud, logs, or analytics. Kotlin/Compose strings cannot provide a
guaranteed JVM-memory zeroization promise; the flow keeps the minimum necessary
lifetime and uses the existing CharArray codec boundary.

## Destructive confirmation semantics

The preview dialog explicitly states:

> Restore will replace the current Evolune data on this device.

The only v1.2 action is **Restore and replace**. Cancellation performs no
persistence mutation. B4 does not implement merge, smart merge, or keep-both
semantics.

## Authorization-resolution semantics

Drive access uses the existing `AuthorizationClient` seam and only
`drive.appdata`. B4 receives `AuthorizationRequired` as a one-shot UI event,
launches the returned PendingIntent, and resumes the pending Backup or Restore
intent after the result. PendingIntent, passphrases, bytes, and tokens are not
persisted. A user cancellation returns the operation to a stable error/idle
state without cloud or local mutation.

Credential Manager is not added: B4 does not need Google authentication or an
email identity, only foreground authorization to the app-data Drive scope.

## Post-restore refresh semantics

B2 is the only persistence mutation authority. Post-restore reconciliation runs
only after B2 returns complete success. The current app refreshes reminders and
requests widget refresh through their existing formal entry points. PK/UI and
Wear flows observe the authoritative Room/Settings changes already used by the
app and are not reimplemented as a second sync engine.

If a refresh effect fails after B2 commit, the result is
`Restore succeeded; refresh pending`, never `Restore failed`. B2 persistence is
not rolled back for a maintenance-side-effect failure.

## Error UX mapping

B4 distinguishes authorization required/cancelled, network failure, no
backups, backup-too-large, invalid local data, upload verification failure,
wrong secret/tampering, unsupported future backup, corrupt backup, restore
failure, recovery required, refresh pending, and disconnect failure. Raw
exceptions, SQLite details, HTTP bodies, access tokens, decrypted payloads, and
passphrases are not user-facing.

## Release gates

The live Google Drive flow remains `NOT TESTED` until the owner supplies the
approved OAuth project, signing configuration, account, and real foreground
device/emulator evidence for authorization, upload, read-back, list, download,
retention, and disconnect. B4 development uses fake B3 transport for JVM
orchestration tests; live Drive evidence remains an RC-only gate.
