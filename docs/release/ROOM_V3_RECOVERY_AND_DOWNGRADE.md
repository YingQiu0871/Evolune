# Evolune Room v3 Recovery and Downgrade Guide

## Purpose

This guide is for an advanced user, operator, or maintainer handling a failed
Evolune Room v2 to v3 upgrade. It documents the supported safety boundary. It
is not an automatic repair feature and does not authorize a release.

## Immediate response

Do not uninstall Evolune, clear app data, delete the database, repeatedly swap
v2/v3 builds, edit the database manually, or enable destructive migration.
Those actions can permanently remove or further mutate local data.

1. Preserve the sole original app data and database untouched.
2. Stop repeated mutation attempts.
3. If diagnosis is required, obtain a closed or SQLite-consistent copy,
   including required WAL/SHM state.
4. Record a private SHA-256 and create a separate immutable safety backup.
5. Audit or repair another working copy only.
6. Share sanitized structural results only, never health data or database files.

## Strict migration behavior

Evolune intentionally fails migration when persisted v2 data is invalid rather
than dropping rows, guessing values, rewriting medication semantics, or
resetting the database. DDL, complete legacy preflight, backfill, and
postcondition checks run in the outer Room/SQLite upgrade transaction. A
failure rolls back schema changes, data changes, and `user_version` together.

## Offline audit and repair

The sealed workflow is:

`source snapshot -> immutable backup -> working copy -> audit-only -> preview
-> explicit manifest -> preview token -> copy repair -> verify -> official
MIGRATION_2_3 -> Repository validation`

Repair is offline, explicit, copy-only, operator-authorized, and separate from
the application runtime. The tool rejects active sidecars, unknown/non-v2
schemas, mismatched identities, in-place/existing output, and unresolved
blocking issues.

Only explicitly supported time corrections in an operator-approved manifest
may change. The tool does not guess route, ester, medication identity, dose,
schedule, UUID, extras, days, interval, Boolean values, or other medical/domain
meaning. Stop when a clean copy would require such inference.

## Verification after repair

Before considering a repaired copy:

1. prove the original hash and modification time are unchanged;
2. prove the copy remains exact Room v2 with `user_version=2`;
3. require `PRAGMA integrity_check = ok` and a clean audit;
4. prove only manifest-authorized time fields changed;
5. run official `MIGRATION_2_3` on the copy;
6. require an empty `PRAGMA foreign_key_check`;
7. verify row counts and retained legacy values;
8. close/reopen and read every migrated event and plan through production
   Repository contracts.

Do not replace the sole original without an independently reviewed recovery
plan explicitly authorizing that operation.

## Downgrade policy

Evolune has no `MIGRATION_3_2` and no destructive downgrade fallback. A v2
application must not open a database already upgraded to v3. Direct downgrade
with the same database is unsupported.

The only safe conceptual rollback path is an authentic pre-upgrade v2 snapshot
plus a compatible v2 application build. Without such a snapshot, no lossless
downgrade is promised: remain on a compatible v3 build and preserve data for
diagnosis. Evolune does not currently provide a Google Drive/cloud database
backup feature, so this guide does not rely on one.

Room v2 is the formal local-database compatibility baseline. Historical v1
evidence is limited; the tool safely rejects v1 and no v1-to-v3 repair or
general upgrade support is claimed.

## Privacy and stop conditions

Never publish a database, backup, WAL/SHM file, medication history, dose,
schedule, extras, timestamps, raw UUIDs, health-valued manifest, APK signing
key, or private audit directory. Share only sanitized categories, tool/runtime
version, schema identity, non-sensitive fingerprints, counts, and structural
outcomes.

Stop when source identity cannot be proven, any correction requires guessing
domain meaning, the original would need mutation, migration still fails after
verified copy repair, Repository reads disagree, destructive/silent fallback
would be required, or sensitive data would need disclosure.

This runbook technically addresses the Phase 1 recovery/downgrade gate pending
independent Batch 8E review.
