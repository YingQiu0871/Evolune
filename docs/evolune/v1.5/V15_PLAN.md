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
- Keep the Phone background, manifest identity, notification icon, Widget artwork, and unrelated
  Logo assets unchanged. Correct the verified Wear launcher defect by using adaptive layers and
  transparent outer pixels while preserving the existing mark and package identity.
- Keep adaptive, legacy-density, and monochrome Phone launcher resources visually coherent.

### v1.5-B — Measured performance/recomposition work

Only address startup, PK, Compose, or navigation hotspots supported by v1.5-A evidence. Any
PK optimization must preserve deterministic output and time-dependent behavior.

The PK baseline uses three fixed data states so measurements remain comparable:

1. **PK-EMPTY** — no medication plans and no dose events.
2. **PK-STEADY** — one enabled daily plan with three scheduled slots and 30 days of
   deterministic recorded doses.
3. **PK-DENSE** — three enabled daily plans with three scheduled slots each and 90 days of
   deterministic recorded doses.

Each dataset records its plan count, slot count, event count, clock/time-zone setup, output
summary, and elapsed time. The datasets are measurement fixtures only; they do not authorize
changes to the PK model or persisted data.

The repeatable source for the event fixtures is
`scripts/generate_v15b_fixture.ps1`. It emits Mahiro JSON v1 with a fixed reference instant
(`2026-09-04T12:00:00Z` by default), 62.0 kg, deterministic event IDs, and the three slots
`03:15`, `09:00`, and `21:00`. The generated files are temporary test inputs and are not
checked into the repository. Plans are created through the app UI so the fixture remains
compatible with the production import path without changing persistence code.

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
- Wear launcher inspection shows the existing mark rendered as a clean circular gradient with
  no black outer corners or unintended white wrapper; adaptive-layer and device evidence are
  recorded separately from the Phone scale correction.
- Existing JVM, compilation, and relevant connected-device regression suites remain green.
- No production optimization is claimed before a comparable measurement exists.
- The current diff contains no v1.6 feature work, authority change, protocol redesign, or
  release-identity change.
