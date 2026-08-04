# Evolune Phase 1 Batch 4C Report

Date: 2026-08-04

## 1. Scope

Batch 4C adds an offline Python repair toolkit for strict Evolune Room v2 data preflight. The toolkit can read-only scan a v2 database copy, create a separately repaired v2 copy from an explicit correction manifest, and read-only verify the repaired copy.

This batch does not modify Android app or Wear code, Room entities, schemas, migrations, repositories, JSON product formats, PK behavior, UI, reminders, widgets, Gradle configuration, or runtime production paths. It does not run `MIGRATION_2_3` and does not upgrade a database to v3.

## 2. Files

- `tools/repair-v2/repair_v2.py`
- `tools/repair-v2/test_repair_v2.py`
- `tools/repair-v2/README.md`
- `tools/repair-v2/manifest.example.json`
- `docs/phase-reports/PHASE_1_BATCH_4C_REPORT.md`

No binary SQLite fixture is stored in the repository. All test and smoke-test databases are synthetic and created in temporary directories.

## 3. CLI commands

The single-file CLI exposes three subcommands:

- `scan`: opens the explicit input read-only, validates v2 identity, reports every migration-blocking `timeH` and `timeOfDay` issue, and never modifies the input.
- `repair`: validates a version 1 manifest, copies the input to a distinct non-existing output path, applies only explicit target-column corrections in one transaction, verifies the output, and removes failed output copies.
- `verify`: opens the explicit repaired v2 copy read-only and requires zero blockers without invoking Android Room or upgrading the schema.

`python tools/repair-v2/repair_v2.py --help` completed successfully and displayed all three commands.

## 4. Evolune v2 identity validation

Every command requires:

- an existing non-symlink regular file;
- a SQLite database that passes `PRAGMA quick_check`;
- `PRAGMA user_version = 2`;
- `dose_events`, `medication_plans`, and `room_master_table`;
- `dose_events.id` and `dose_events.timeH`;
- `medication_plans.id` and `medication_plans.timeOfDay`;
- Room v2 identity hash `a8036e3f5ed6bb42d0e7289ac84039f3` at `room_master_table.id = 42`.

Version 3, arbitrary SQLite databases, missing tables or columns, and incorrect Room identities are rejected with database identity exit code `3`. The tool never changes `room_master_table` or `user_version`.

## 5. timeH compatibility

The conversion is an independent Python implementation of the locked `LegacyTimeAdapter` numeric semantics:

- persisted storage class must be SQLite `integer` or `real`;
- booleans are not accepted as manifest numbers;
- values are converted to IEEE-754 binary64 and must be finite;
- scale is exactly `3_600_000.0`;
- multiplication overflow is rejected;
- the scaled value must be in `[-2^63, 2^63)`;
- rounding is `floor(value + 0.5)`, matching Java `Math.round(double)` instead of Python bankers rounding;
- NaN, positive and negative Infinity, and positive and negative overflow fail explicitly;
- no clamp, epoch-zero fallback, or current-time fallback exists.

Fixed independent expected vectors cover zero, ordinary positive and negative values, millisecond precision, positive and negative half-millisecond boundaries, and legal and illegal values near both `Long` limits.

## 6. timeOfDay compatibility

The strict parser implements the locked legacy boundary:

- an empty SQL string means an empty list;
- all non-empty values must be JSON arrays of strings;
- `HH:mm`, `HH:mm:00`, and zero-only fractional seconds through nanosecond precision are accepted;
- accepted zero-second forms produce minute-precision canonical values;
- non-zero seconds or fractions, malformed JSON, non-array roots, non-string elements, null elements, empty strings, offsets, zones, whitespace variants, invalid clock values, and non-ISO separators fail;
- list order and duplicate local times are preserved;
- correction manifests must already use exact canonical `HH:mm` and are never silently canonicalized.

Issue records contain the plan UUID, element position, issue code, and at most the single invalid element. Parse-level failures do not emit a complete `timeOfDay` value.

## 7. Correction manifest schema

Manifest version is fixed at `1` and requires exactly:

- `version`;
- `inputSha256`;
- `eventCorrections`;
- `planCorrections`.

Duplicate JSON keys are rejected through `object_pairs_hook`. Unknown or missing fields are rejected at top-level and correction-object levels. IDs must be valid UUIDs, must exist in the input, and case variants cannot define the same UUID twice. Event replacements must be valid strict JSON numbers and plan replacements must be arrays of canonical `HH:mm` strings.

The manifest SHA-256 must match the exact input bytes. It must cover every blocking time row and may not modify a clean row or a non-time field. Invalid row IDs are explicit unrepairable blockers rather than candidates for generated replacement IDs.

`manifest.example.json` contains only synthetic UUIDs and times plus an intentionally invalid, obvious SHA placeholder.

## 8. Copy and transaction safety

- Input, output, manifest, and optional audit paths are resolved and compared.
- Symlink inputs, existing outputs, equal resolved paths, and active non-empty WAL or rollback-journal sidecars are rejected.
- Input SHA-256 is checked before copy, against the copied output, and again after copy and repair.
- Input size, SHA-256, and modification time must remain unchanged.
- `shutil.copy2` creates the output before any repair.
- The only repair SQL statements are parameterized updates of `dose_events.timeH` and `medication_plans.timeOfDay` by ID.
- Every update must affect exactly one row.
- Plan arrays use compact stable JSON while preserving order and duplicates.
- A single SQLite transaction contains all updates and complete post-update scanning.
- Any failure rolls back, closes the database, and removes the output and its sidecars.
- A successful output remains `user_version = 2`, retains the v2 Room identity, and contains no v3 column or slot table.

There is no in-place or force option, no automatic database search, and no automatic correction inference.

## 9. Audit privacy

Audit JSONL is disabled by default and created only for an explicit new `--audit` path. Existing audit files are never overwritten.

Each audit contains individual issue records and a summary with UTC timestamp, tool version, mode, resolved path, SHA-256, user version, issue counts, correction counts, success status, and exit code. Repair summaries distinguish initial blocker counts from zero remaining blockers and include output SHA-256.

Audit records do not include full database rows, `extras`, `doseMG`, complete plan arrays, user names, or unrelated health fields. They can include stable UUIDs and one invalid time element, so the README explicitly treats real audit files as sensitive and prohibits committing or sharing them.

## 10. Test results

Runtime available in this environment:

- `py -3.12`: unavailable;
- actual fallback runtime: Python `3.14.6`;
- implementation syntax and APIs remain compatible with Python 3.12.

Command:

```text
python -m unittest discover -s tools/repair-v2 -p "test_*.py" -v
```

Result:

- tests: `84`;
- failures: `0`;
- errors: `0`;
- skipped: `0`;
- exit code: `0`;
- result: PASS.

The tests dynamically create exact-purpose synthetic v2 databases under `tempfile.TemporaryDirectory`. Coverage includes identity failures, all scan blocker classes, Java-compatible fixed vectors, strict legacy time parsing, duplicate-key and strict-field manifest validation, explicit repair combinations, unchanged input hash and mtime, target-column isolation, rollback/output deletion, retained v2 schema, verify exit behavior, and JSONL privacy.

## 11. Smoke test

A separate temporary-directory CLI smoke test executed the actual commands in sequence against one synthetic v2 database containing positive Infinity and `20:30:15`:

| Step | Exit code | Result |
|---|---:|---|
| `scan` | `1` | PASS: reported exactly two blockers |
| `repair` | `0` | PASS: applied one event and one plan correction |
| `verify` | `0` | PASS: zero remaining blockers |

The smoke test additionally confirmed:

- input SHA-256 unchanged;
- input modification time unchanged;
- output `user_version = 2`;
- no `occurredAtEpochMillis` v3 column;
- event and plan target values matched the manifest;
- all three audit JSONL files were parseable and ended with summary records;
- the temporary directory was removed (`TEMP_REMOVED=True`).

No smoke database, manifest, output, or audit file remains in the repository.

## 12. Third-party dependencies

None. The implementation imports only the authorized Python standard-library modules. No `requirements.txt`, pip package, network access, SQLite JDBC, pandas, or SQLAlchemy is used.

## 13. Android and Room boundary

No file under `app/`, `wear/`, or `gradle/` is modified. Room schema 2 and schema 3 are unchanged. `MIGRATION_2_3`, entities, DAOs, repositories, product JSON, PK, UI, reminder, widget, and Wear production code are unchanged.

## 14. Data provenance

All UUIDs, times, labels, database rows, manifests, and audits used by tests and the smoke run are synthetic. The toolkit has not been executed against a real, anonymized-from-real, or real-derived user database.

## 15. Known limitations

- Python 3.12 was not installed in this environment, so the exact runtime validation used Python 3.14.6 while retaining 3.12-compatible language and standard-library APIs.
- The tool intentionally refuses active WAL or rollback-journal inputs; users must prepare an offline standalone database copy first.
- Invalid event or plan IDs cannot be repaired because the authorized manifest changes only legacy time fields.
- This toolkit has not proved successful repair of any real user database.
- Successful verification proves only strict v2 data readiness; it does not execute or prove the Android v2-to-v3 Room migration.

## 16. Batch status

Batch 4C engineering implementation and local synthetic validation passed. It is not yet finalized because independent DeepSeek read-only review, intentional commits, and a Batch 4C tag have not occurred.

Completion of Batch 4C does not authorize v3 release. Room v3 remains an internal, non-releasable schema, and Phase 1 Batch 3C has not started.

## 17. Next step

The next permitted step is independent DeepSeek read-only review of these five files. Only after review and explicit submission may work proceed to Batch 3C. No review file, staging, commit, tag, or Batch 3C implementation was created in this batch.
