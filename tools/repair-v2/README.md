# Evolune v2 Repair Toolkit

This offline Python tool scans an Evolune Room v2 database copy for data that would block the strict v2-to-v3 Android migration. It can create a separately repaired v2 copy from an explicit correction manifest and verify that copy.

The tool does **not** upgrade a database to v3, run Room migrations, infer missing times, or modify an input database in place. A repaired copy must still be upgraded later by the official Evolune app migration.

## Safety status

- Requires Python 3.12 or a compatible newer Python 3 runtime.
- Uses only the Python standard library; there are no pip dependencies.
- Accepts only a recognizable Evolune Room v2 database with `user_version = 2`, the required v2 tables and columns, and Room identity hash `a8036e3f5ed6bb42d0e7289ac84039f3`.
- Opens scan and verify inputs read-only.
- Rejects symbolic-link inputs, existing outputs, equal resolved input/output paths, and active non-empty SQLite WAL or rollback-journal sidecars.
- Copies the input before repair, verifies SHA-256 before and after copying, and modifies only the output copy.
- Applies all corrections in one SQLite transaction, verifies the result before commit, and deletes an incomplete output after failure.
- Never changes `user_version`, `room_master_table`, schemas, or non-target columns.

Do not use the only copy of a database. Create an additional offline backup, rehearse against another copy first, and retain the untouched backup until the official app migration has been independently verified.

## Commands

### Scan

`scan` reports every blocking legacy `timeH` or `timeOfDay` issue without modifying the database.

Windows PowerShell:

```powershell
python tools/repair-v2/repair_v2.py scan `
  --input C:\offline-copy\legacy-v2.db `
  --audit C:\offline-copy\scan-audit.jsonl
```

Linux or macOS:

```bash
python3 tools/repair-v2/repair_v2.py scan \
  --input /offline-copy/legacy-v2.db \
  --audit /offline-copy/scan-audit.jsonl
```

Exit code `0` means no blocker; exit code `1` means blocking data was found.

### Repair

`repair` requires an explicit version 1 manifest. It copies the input to a new output path, applies only listed `timeH` and `timeOfDay` corrections, and performs complete verification before keeping the output.

Windows PowerShell:

```powershell
python tools/repair-v2/repair_v2.py repair `
  --input C:\offline-copy\legacy-v2.db `
  --output C:\offline-copy\legacy-v2-repaired.db `
  --manifest C:\offline-copy\corrections.json `
  --audit C:\offline-copy\repair-audit.jsonl
```

Linux or macOS:

```bash
python3 tools/repair-v2/repair_v2.py repair \
  --input /offline-copy/legacy-v2.db \
  --output /offline-copy/legacy-v2-repaired.db \
  --manifest /offline-copy/corrections.json \
  --audit /offline-copy/repair-audit.jsonl
```

There is no `--force-in-place` option. Output, manifest, and audit paths are never overwritten.

### Verify

`verify` read-only checks that a repaired copy has no data blockers. It does not upgrade or open the database through Android Room.

Windows PowerShell:

```powershell
python tools/repair-v2/repair_v2.py verify `
  --input C:\offline-copy\legacy-v2-repaired.db `
  --audit C:\offline-copy\verify-audit.jsonl
```

Linux or macOS:

```bash
python3 tools/repair-v2/repair_v2.py verify \
  --input /offline-copy/legacy-v2-repaired.db \
  --audit /offline-copy/verify-audit.jsonl
```

## Correction manifest v1

The manifest must contain exactly these top-level fields:

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

Rules:

- `inputSha256` must exactly match the input database bytes.
- IDs must be standard UUID strings and must exist in the input database.
- Duplicate JSON keys and unknown fields are rejected at every manifest object level.
- Event `timeH` must be a JSON number; booleans, non-finite values, and conversion overflow are rejected.
- Plan times must already be canonical five-character `HH:mm` values. `HH:mm:ss` is rejected rather than canonicalized.
- Order and duplicate plan times are preserved.
- Every blocking row must have a correction, and clean rows must not be included.
- The tool never guesses, truncates, sorts, deduplicates, clamps, or substitutes the current time.

`manifest.example.json` contains only synthetic identifiers and an intentionally invalid SHA placeholder. Replace the placeholder with the SHA-256 of the exact offline input copy before use.

## Compatibility rules

### Legacy `timeH`

- SQLite storage class must be `INTEGER` or `REAL`; `NULL`, `TEXT`, and `BLOB` are blocking.
- The binary64 value must be finite.
- Conversion multiplies by `3_600_000.0`, rejects multiplication overflow, requires the result in `[-2^63, 2^63)`, and uses `floor(value + 0.5)` to match Java `Math.round(double)` rather than Python bankers rounding.
- NaN, positive or negative Infinity, and range overflow are blocking. There is no clamp or zero fallback.

### Legacy `timeOfDay`

- An empty SQL string means an empty list.
- Otherwise the value must be a JSON array of strings.
- Historical `HH:mm`, `HH:mm:00`, and zero-only fractional forms such as `HH:mm:00.000` are accepted.
- Non-zero seconds or nanoseconds, invalid JSON, non-array roots, non-string elements, empty elements, offsets, zones, whitespace variants, and non-ISO separators are blocking.
- Accepted zero-second forms are interpreted at minute precision; the repair manifest itself must still use only canonical `HH:mm`.

## Audit privacy

Audit output is optional and is created only when `--audit` is explicitly supplied. Each JSONL file contains a summary plus individual blocking issue records. It includes the resolved database path, SHA-256, user version, issue and correction counts, UUIDs, plan positions, and possibly one raw invalid time value.

An audit file may therefore contain sensitive timing information and stable identifiers. It never intentionally records full database rows, `extras`, `doseMG`, complete `timeOfDay` arrays, user names, or unrelated health fields. Do not commit, publish, or share real audit files. The same prohibition applies to real databases, correction manifests, and repaired outputs.

## Exit codes

| Code | Meaning |
|---:|---|
| `0` | Success with no blocking issue |
| `1` | `scan` found blocking data |
| `2` | Command, path, or manifest usage error |
| `3` | Input is not a recognized Evolune Room v2 database |
| `4` | Repair failed, or `verify` found blocking data |
| `5` | Unexpected internal tool error |

## Tests

Tests dynamically create synthetic databases in temporary directories. They do not use or leave persistent database fixtures.

```powershell
python -m unittest discover -s tools/repair-v2 -p "test_*.py" -v
```

Run the same command with `python3` on systems where that is the Python 3 launcher.

