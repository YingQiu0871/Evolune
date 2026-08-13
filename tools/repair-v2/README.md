# Evolune Room v2 Offline Repair Toolkit

Version `2.0.0` is an operator/developer tool for an explicitly selected,
offline copy of an Evolune Room v2 database. It is not linked into the app and
is never invoked by app startup or `MIGRATION_2_3`.

The authority boundary is fixed:

- valid v2 data is upgraded only by the official Room migration;
- invalid v2 data makes the official migration fail;
- this tool can audit a separate v2 copy and, after preview plus an explicit
  manifest, create another repaired v2 copy;
- the repaired copy must still pass the official migration and production
  Repository checks before it is usable evidence.

The tool never changes the selected input, never overwrites an output, never
automatically repairs findings, and never invents medical semantics.

## Runtime

- Python `3.12` is the sealed Batch 8D runtime. Evidence used Python `3.12.13`.
- Only the Python standard library is used.
- Run tests with:

```powershell
python -m unittest discover -s tools/repair-v2 -p "test_*.py" -v
```

Batch 8D evidence: 94 tests, 94 pass, 0 failures or errors.

## Database identity and snapshot gate

Only the formal Evolune v2 baseline is accepted:

- `PRAGMA user_version = 2`;
- `dose_events`, `medication_plans`, and `room_master_table` exist;
- required v2 columns exist;
- Room identity hash is `a8036e3f5ed6bb42d0e7289ac84039f3`.

v1, v3, unknown versions, unknown schemas, wrong Room identities, non-SQLite
files, and symbolic-link inputs fail safely. The tool does not guess.

The selected database must be cleanly closed. A non-empty sibling `-wal`,
`-shm`, or `-journal` file is rejected. For a live database, first use a
SQLite-consistent snapshot method such as the SQLite backup API or `VACUUM
INTO`; copying only the main file while a WAL is active is invalid.

## Commands

All output paths, including audit paths, must not already exist.

### 1. Read-only scan

```powershell
python tools/repair-v2/repair_v2.py scan `
  --input C:\offline-work\legacy-v2-copy.db `
  --audit C:\offline-work\scan-audit.jsonl
```

`scan` opens the input read-only and performs zero mutation. Exit `0` means
clean; exit `1` means blocking persisted data was found.

### 2. Mandatory preview

Create an explicit manifest, then validate it and obtain a deterministic token:

```powershell
python tools/repair-v2/repair_v2.py preview `
  --input C:\offline-work\legacy-v2-copy.db `
  --manifest C:\offline-work\corrections.json `
  --audit C:\offline-work\preview-audit.jsonl
```

The token is bound to tool version, exact input SHA-256, and exact manifest
SHA-256. Changing any of them invalidates the token.

### 3. Explicit copy repair

```powershell
python tools/repair-v2/repair_v2.py repair `
  --input C:\offline-work\legacy-v2-copy.db `
  --output C:\offline-work\legacy-v2-repaired.db `
  --manifest C:\offline-work\corrections.json `
  --preview-token <exact-token-from-preview> `
  --audit C:\offline-work\repair-audit.jsonl
```

Repair copies the input to a new path, proves the pre-mutation copy hash equals
the input hash, applies all corrections in one SQLite transaction, verifies the
output, and removes the output after any failure. The original file hash, size,
and modification time must remain unchanged.

### 4. Read-only verification

```powershell
python tools/repair-v2/repair_v2.py verify `
  --input C:\offline-work\legacy-v2-repaired.db `
  --audit C:\offline-work\verify-audit.jsonl
```

`verify` performs no mutation. A clean Python result is necessary but not
sufficient: the copy must next pass `MIGRATION_2_3`, Room v3 reopen,
`integrity_check`, `foreign_key_check`, and production Repository reads.

## Manifest v1

```json
{
  "version": 1,
  "inputSha256": "64 hexadecimal characters from the exact input file",
  "eventCorrections": {
    "00000000-0000-0000-0000-000000000101": {
      "timeH": 123.456
    }
  },
  "planCorrections": {
    "00000000-0000-0000-0000-000000000201": {
      "timeOfDay": ["08:30", "20:00"]
    }
  }
}
```

The manifest is operator input, not an inference result. UUIDs must be
canonical and identify rows with blocking time findings. It must cover every
blocking time row and must not modify a clean row. Duplicate JSON keys, unknown
fields, non-finite numbers, overflow, and non-canonical replacement times are
rejected. Plan time order and duplicates are preserved.

## Complete v2 audit contract

The scanner checks the sealed v2 persisted grammar for:

- canonical event and plan UUIDs;
- known route and ester values;
- numeric dose storage;
- JSON object extras, known extra keys, and numeric values;
- numeric legacy `timeH`, finite conversion, Java-compatible rounding, and
  signed 64-bit epoch-millisecond range;
- known schedule types;
- strict plan time JSON arrays and minute precision;
- day JSON arrays containing only integers 1 through 7;
- `intervalDays` in `1..Int.MAX_VALUE` using INTEGER storage;
- canonical Boolean storage `0` or `1`;
- INTEGER `createdAt` storage;
- expected SQLite storage classes for all inspected fields.

Empty plan time storage and duplicate local times remain valid. The tool does
not sort, deduplicate, renumber, clamp, normalize irrelevant schedule fields,
or substitute the current time.

## Repairability

| Category | Detectable | Tool repair | Operator input | Policy |
|---|---:|---:|---:|---|
| Invalid legacy event `timeH` | Yes | Explicit manifest only | Required | Exact replacement cannot be inferred |
| Invalid/non-minute plan `timeOfDay` | Yes | Explicit manifest only | Required | Exact intended schedule cannot be inferred |
| UUID, route, ester, schedule type | Yes | No | External decision | No medical/identity guessing |
| Extras JSON/key/value | Yes | No | External decision | Meaning cannot be reconstructed safely |
| Days, interval, Boolean, storage class | Yes | No | External decision | No semantic coercion or normalization |
| Wrong schema/version/Room identity | Yes | No | Not applicable | Fail safe; v2 only |

`OPERATOR_MANIFEST_REQUIRED` does not mean automatic repair. The tool only
executes values explicitly supplied after scan and preview. All other findings
are `NO_SAFE_AUTOMATIC_REPAIR` and prevent repair output creation.

## Privacy-safe persistent output

Console summaries and optional JSONL audits contain structural information
only: tool version, mode, schema/version identity, category, field, aggregate
type, issue count, repairability, and a 16-hex SHA-256 row fingerprint.

They do not contain database paths, raw UUIDs, raw invalid values, dose,
medication names, schedules, extras payloads, timestamps, or SQL rows. Real
databases, manifests, repaired copies, and audit files must never be committed
or published even though the output format is sanitized.

## Private real-database validation runbook

Private validation is not authorized merely by having this tool. The required
sequence is:

1. The user supplies the exact `REAL_DB_PATH` in a separate message.
2. The user explicitly authorizes `PRIVATE REAL-DB VALIDATION`.
3. Do not search any filesystem, backup, or connected device for a database.
4. Ensure the source is closed or create a SQLite-consistent snapshot.
5. Keep the original immutable and locally record its SHA-256.
6. Create an independent safety backup and prove its SHA-256 equals the source.
7. Create a separate working copy; operate only on that copy.
8. Run read-only `scan` first and retain only sanitized evidence.
9. If clean, migrate another copy and run integrity, FK, structural/count, and
   production Repository checks.
10. If invalid, stop. Validation authorization does not authorize repair.
11. Obtain a separate explicit `REAL-DB REPAIR AUTHORIZED` decision before
    preparing any manifest or running preview/repair.
12. Never commit a database, audit, manifest, backup, APK, or health data.
13. Delete disposable migrated/repaired copies only when authorized; retain the
    original untouched.

Without an exact path and explicit authorization, private execution is
`NOT EXECUTED`, not a pass.

## Synthetic evidence and 8C reproducibility

`create_synthetic_evidence.py` creates an ephemeral invalid v2 fixture, scans
it, previews an explicit manifest, repairs a new copy, and verifies that copy.
The generated DB files belong under an ignored build/temp directory and must be
deleted after Android evidence. `RepairToolOutputMigrationTest` accepts the
gzip/base64 bytes and SHA-256 only as explicit instrumentation arguments; no DB
binary is stored in Git.

The Batch 8C historical package-upgrade evidence can be reproduced without
committing APKs:

1. Create a detached worktree at
   `16d8dbf1c7d1ed359b2e8c4e0857759b2dd12c81`, tagged
   `phase-1-batch-4a1-design-v1`.
2. Build its debug app with the same local debug signing key. Expected app APK
   SHA-256 from the sealed run:
   `6075281B0FD4C3C1CB42E270FC36AEBA7CF80BF05346C82DF5F6838EA29AB2CF`.
3. Recreate the deterministic instrumentation seeder from the synthetic
   fixture declared by current `Batch8CPreservedUpgradeTest`: six event IDs
   `81000000-...-001` through `...-006`, three plan IDs
   `82000000-...-001` through `...-003`, and all expected Domain fields in that
   test. At the historical worktree, insert those values through its production
   `DoseEventRepository` and `MedicationPlanRepository`, never raw SQL.
4. Build the historical test APK. Expected SHA-256 from the sealed run:
   `6CB3F68BAB0ACADAA16EB3751CF3AB2ED707546EC6CC276CBAA586373B5FED3D`.
5. Follow `PHASE_1_BATCH_8C_REPORT.md` section 7 for seed, in-place install,
   Room v3 reopen, Repository comparison, force-stop/reopen, and cold restart.

The recorded APK hashes are provenance checks for the sealed run, not binaries
to add to source control or release artifacts.

## Exit codes

| Code | Meaning |
|---:|---|
| `0` | Successful operation with no blocking issue |
| `1` | Read-only scan found blocking data |
| `2` | Command, path, manifest, or preview-token usage error |
| `3` | Input is not the recognized Evolune Room v2 database |
| `4` | Repair failed or verification found blocking data |
| `5` | Unexpected internal error |
