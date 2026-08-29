# Evolune v1.2 Release Notes Draft

> Draft only. This is not a release announcement and does not authorize a
> tag, GitHub Release, Play release, or distribution.

## Highlights

- Health Connect can be used to preview a current weight observation.
- The user explicitly chooses whether to adopt the previewed weight locally.
- Manual backups use an encrypted, versioned Evolune backup envelope.
- Google Drive backup is user-authorized and limited to the Drive app-data
  scope.
- Restore requires a passphrase, shows a preview, and requires explicit
  confirmation before replacing local data.
- Restore uses a crash-safe transaction and startup recovery journal.

## Important limits in v1.2

- Health Connect integration is weight read/preview/adoption only.
- Medication records are not written to Health Connect.
- Backup is manual; there is no automatic or background cloud sync.
- Restore is replace-only; merge restore and keep-both modes are not included.
- Google account identity UI, Credential Manager, offline access, and server
  auth-code flows are not included.
- Live device and Google Drive evidence remains an RC acceptance gate.
