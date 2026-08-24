# Evolune v1.2 B1 — Backup Format API Note

## Scope

This note records the B1 API exposure used by the v1.2 restore preview. It does
not change the encrypted wire format, envelope versions, payload schema, KDF,
golden vectors, or provider contract.

## Authenticated metadata exposure

`BackupDecodeResult.Success.metadata` contains the producer metadata from the
envelope header only after the codec has completed:

1. envelope parsing and supported-version checks;
2. AES-GCM authentication/decryption with the supplied passphrase; and
3. payload parsing and complete B1 validation.

The metadata contains `createdAt`, `producerAppVersionName`, and
`producerAppVersionCode`. A malformed, unsupported, tampered, or wrong-secret
backup returns `BackupDecodeResult.Failure`; it never exposes a successful
metadata value to B4.

The field is an API exposure of already-existing authenticated envelope header
values. It is not a new wire-format field and does not change serialized bytes.

## Compatibility evidence

- B1 envelope format version remains `1`.
- B1 payload schema version remains `1`.
- Existing B1 golden tests remain unchanged and continue to run in RC0.
- No provider, Google Drive, Room, or restore-protocol behavior is changed by
  this note.
