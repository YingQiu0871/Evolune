# Evolune v1.2 B3 Google Drive setup and evidence

## Scope

B3 adds a foreground/manual Google Drive `appDataFolder` provider for the
already-encrypted B1 bytes. It does not add B4 UI, passphrase entry, restore
preview, B2 restore execution, automatic sync, or background work.

The provider uses the Google Identity Services `AuthorizationClient` seam and
requests exactly one scope:

```text
https://www.googleapis.com/auth/drive.appdata
```

Offline access is disabled. Access tokens exist only in memory for the current
foreground operation. No token, account email, server authorization code,
refresh token, backup bytes, or medical content is written to app storage or
logs. B3 does not silently mix accounts or persist an account identity; B4 owns
the user-facing account-change/resolution flow.

## External Google Cloud configuration

The following configuration is an external environment/release-owner task and
must not be committed to this repository:

1. Select or create the approved Google Cloud project.
2. Enable the Google Drive API.
3. Configure the Android OAuth client for the application id
   `io.github.yingqiu0871.evolune` and the approved signing certificate(s).
4. Ensure the test account and device/emulator use the intended Google account.
5. Keep OAuth client configuration and signing material out of source control.

The implementation does not use a web-server client, a backend, a refresh-token
store, the deprecated Drive Android API, GoogleAuthUtil, or a visible Drive/root
scope. Drive files are created with `parents: ["appDataFolder"]` and listed
with `spaces=appDataFolder`; deletion is direct `files.delete`.

## Cloud object policy

Each upload creates a new immutable Drive file. File names are generated from a
timestamp and UUID and contain no medication names, patient data, or plaintext
backup content. The app properties marker is:

```text
evoluneKind=backup
backupFormat=native
```

Only non-sensitive format/version, creation-time, and SHA-256 metadata are
used alongside the marker. The encrypted bytes are hashed locally, uploaded,
downloaded again, and compared byte-for-byte before the upload is reported as
verified. Retention then keeps the newest three valid generations, ordered by
Drive `createdTime` and deterministic file-id tie-break.

Download ingress checks Drive metadata before opening the media stream when a
size is available. Missing or inconsistent size metadata is still protected by
the hard byte cap. The implementation chooses a 16 MiB cap because the current
encrypted snapshot is a small local-data artifact, while 16 MiB gives a clear
bounded heap budget for a foreground read and leaves malformed/oversized remote
objects fail-closed. B3 never decodes the B1 envelope or performs restore.

If a token receives HTTP 401, the in-memory token is cleared, authorization is
attempted once more, and the same Drive operation is retried once. A resolution
requirement is returned to the caller; B3 does not launch a UI. A second 401 is
reported as token expiry/authorization failure.

## Evidence taxonomy

The matrix uses these evidence categories exactly:

- `UNIT/FAKE`
- `INSTRUMENTATION`
- `EMULATOR`
- `PHYSICAL`
- `LIVE GOOGLE DRIVE`
- `NOT TESTED`

Current B3 evidence:

| Area | Evidence | Status |
| --- | --- | --- |
| Provider policy, retention, pagination, ordering, bounded ingress, 401 retry, cancellation | UNIT/FAKE | PASS |
| Exact `drive.appdata` scope and offline-access contract | UNIT/FAKE | PASS |
| Android instrumentation authorization flow | INSTRUMENTATION | NOT TESTED |
| Android compilation with `play-services-auth:21.6.0` | EMULATOR | NOT TESTED as a live authorization flow |
| Real device authorization and appDataFolder operation | PHYSICAL | NOT TESTED |
| Real Google Drive create/read-back/list/download/prune/delete smoke | LIVE GOOGLE DRIVE | NOT TESTED |

`LIVE GOOGLE DRIVE` remains `NOT TESTED` until an owner supplies the approved
OAuth project, signing configuration, account, and a real foreground smoke
run. No missing live evidence is represented as a unit-test PASS.

## v1.2 RC smoke gate

Before v1.2 RC release, the release owner must capture evidence for all of the
following against a real configured Google account:

- authorize with only `drive.appdata` and confirm already-granted,
  resolution, cancellation, and disconnect/revoke behavior;
- upload one B1 encrypted byte set and verify read-back equality;
- list all pages and confirm unrelated/non-Evolune objects are ignored;
- download a valid generation and confirm the bounded ingress behavior;
- create at least four generations and confirm only the newest three remain;
- confirm a failed verification never deletes an older generation;
- confirm a failed old-generation deletion reports verified upload plus pending
  retention cleanup;
- confirm no visible Drive/root/shared-drive object is created.

These live checks are an RC release gate. They do not authorize B4 UI or
automatic/background synchronization work.
