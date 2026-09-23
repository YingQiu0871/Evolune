# EVOLUNE v1.8.0 — D-04 / M04

## Engine cancellation-latency characterization

This is an evidence-only characterization of the existing Home and retrospective PK execution
chains at baseline commit `057011b83d74277af9045acf417499f941f95a0c`.

No production source, product test, scheduler behavior, publication behavior, or implementation
was changed. The temporary measurement harness used only the already-existing calculator and curve
runner seams, was captured under `logs/temporary-measurement-harness.kt`, and was removed before
the evidence-only commit.

Disposition:

`M04 CONFIRMED — SEPARATE IMPLEMENTATION REVIEW REQUIRED`

The Home path repeatedly returned superseded engine results to the HRT publication boundary and
accepted stale state in the repeated stress trace. The retrospective path consistently observed
post-engine cancellation and rejected the old generation. All successor results were numerically
equivalent to clean-reference calculations.

The raw Windows checkout and canonical tracked-LF regression are intentionally reported separately:

- Raw checkout: the inherited portable JSON and CSV byte assertions reproduce as the known
  environment normalization failures.
- Canonical tracked-LF gate: app 1,440/1,440, experience-core 171/171, wear 90/90; total
  1,701/1,701 pass, zero failures/errors/skips.
