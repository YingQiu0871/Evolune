# Evolune v1.5 — Stability, Performance & Code Cleanup

## Status

Planning baseline after the sealed `v1.4.0` release. No v1.5 production change is included
in this document or started in code.

## Goal

Make Evolune a more predictable, efficient, and maintainable release without adding a new
user-facing product capability. v1.5 pays down technical debt while preserving the behavior,
data authority, and device boundaries already released in v1.4.0.

## Scope

### Stability sweep

- Fresh install, upgrade from the published v1.4.0 line, process recreation, force-stop,
  reboot, and abnormal app state.
- Cross-midnight behavior, date changes, time-zone changes, and DST transitions.
- Medication-plan create/edit/delete, dose recording, reminders, and notification actions.
- Multiple Phone Widgets, Widget refresh, configuration isolation, and launcher recreation.
- Phone/Wear disconnect, reconnect, stale snapshots, retry behavior, and Data Layer delivery.
- Backup/restore, restore preview, malformed or incompatible data, and recoverable failures.

### Performance and energy

- Establish a repeatable baseline for cold/warm startup, PK calculation, Room queries,
  Compose recomposition, Widget refresh, Wear Data Layer work, and scheduled background work.
- Audit WorkManager, polling, wakeups, repeated Flow collection, duplicate refreshes, and
  unnecessary Wear active/background work.
- Keep measurements comparable across the same Phone/Wear emulator and physical-device matrix;
  thresholds are to be frozen in the acceptance record before optimization.

### Code and dependency cleanup

- Remove dead code, duplicate mappers, obsolete resources, unused dependencies, duplicate
  state models, and invalid compatibility layers.
- Strengthen static checks, test isolation, error handling, and log boundaries.
- Every cleanup must preserve observable behavior and be backed by regression evidence. Code
  line reduction alone is not an acceptance criterion.

### Phone launcher Logo correction

The v1.4.0 Phone launcher icon currently presents the moon/foreground mark too large for its
launcher surface. v1.5 will:

- reduce the overall foreground mark and moon visual footprint;
- preserve the mark's aspect ratio, center alignment, recognizable crescent shape, and
  adaptive-icon safe zone;
- keep foreground, background, and monochrome behavior coherent across density variants and
  launcher masks;
- verify the result on the Phone emulator launcher and at least one additional launcher/mask
  presentation when available.

This is a visual-scale correction, not a brand redesign. Wear launcher assets and unrelated
in-app imagery are out of scope unless the audit finds that they share the same Phone asset.

## Non-goals

- No new Health Connect or Google backup capability.
- No new full Wear application capability.
- No Widget Gallery, CPA curve, Tracked Date, personalized calibration, SQLCipher, or physical
  Gradle module extraction.
- No change to Room as medication-data authority, Phone as Wear authority, existing PK model,
  backup semantics, Widget action identity, or Wear transport contracts.

## Execution order

1. Freeze the v1.4.0 baseline and create the scenario/device matrix.
2. Capture performance, background, and energy baselines before changing implementation.
3. Close stability defects and add deterministic regression coverage.
4. Apply performance and energy fixes, then repeat the same measurements.
5. Apply the Logo scale correction and perform visual/mask/density review.
6. Perform code/dependency cleanup with behavior-equivalence checks.
7. Run RC validation on the real Phone/Wear matrix and publish only after all v1.5 exit gates
   are closed.

## Release invariants

- v1.4.0 remains immutable; its tag and Release are not moved or rebuilt.
- The local medication domain and Room remain the sole source of medication truth.
- Phone remains authoritative for Wear state; Wear remains a derived/cache and action surface.
- Existing release signing, package identities, PK attribution, and backup boundaries remain
  unchanged.
