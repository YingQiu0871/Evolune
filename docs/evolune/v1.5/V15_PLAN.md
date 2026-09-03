# Evolune v1.5 — Execution Plan

## Baseline and boundaries

v1.5 starts from the sealed `v1.4.0` release. The tag, release record, package identities,
signing lineage, Room/DataStore names, backup format, PK behavior, Phone/Wear authority
boundaries, and v1.4 onboarding/tutorial behavior are immutable baselines.

v1.5 is a stability, performance/power, and cleanup release. It is not a feature release.
Every implementation change must have a named behavior or measurement target; unmeasured
refactoring is out of scope.

## Bounded passes

### v1.5-A — Baseline, stability matrix, and Phone launcher Logo

- Record the exact release baseline and repeatable startup/background observations.
- Characterize lifecycle, date/time, medication, Widget, Wear, and backup scenarios.
- Reduce the Phone adaptive launcher foreground mark to approximately 0.85–0.90× its current
  visual footprint, centered with preserved aspect ratio and safe-zone margins.
- Keep the Phone background, manifest identity, notification icon, Wear assets, Widget artwork,
  and unrelated Logo assets unchanged.
- Keep adaptive, legacy-density, and monochrome Phone launcher resources visually coherent.

### v1.5-B — Measured performance/recomposition work

Only address startup, PK, Compose, or navigation hotspots supported by v1.5-A evidence. Any
PK optimization must preserve deterministic output and time-dependent behavior.

### v1.5-C — Background, battery, Phone/Wear/Widget work

Audit alarms, jobs, polling, Flow collection, Widget refreshes, Data Layer publication and
reconnect behavior. A power claim requires before/after wakeup or background-work evidence.

### v1.5-D — Proven dead-code cleanup and release sweep

Remove only code/resources/dependencies that are statically proven dead or redundant, covered
by behavior tests, and demonstrably outside persistence, backup, migration, protocol, signing,
or PK provenance boundaries.

## v1.5-A acceptance gate

- `V15_BASELINE_AND_BUDGET.md` records the exact v1.4.0 HEAD/tag and measured environment.
- Phone launcher screenshots/inspection show more breathing room, a visibly smaller moon, a
  centered mark, no clipping, and valid adaptive/monochrome resources.
- Existing JVM, compilation, and relevant connected-device regression suites remain green.
- No production optimization is claimed before a comparable measurement exists.
- The current diff contains no v1.6 feature work, authority change, protocol redesign, or
  release-identity change.
