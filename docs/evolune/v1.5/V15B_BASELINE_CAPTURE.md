# Evolune v1.5-B — Baseline Capture

## Purpose

This document records the first repeatable v1.5-B measurement attempt. It is an observation
record, not an optimization claim. Production performance changes remain gated on a valid,
same-device before/after comparison.

## Reproduction

Run from the repository root in PowerShell:

```text
.\scripts\measure_v15b_baseline.ps1 -Serial emulator-5558 -ColdStartRuns 20 -IdleSeconds 60
```

The script force-stops the Phone package for each cold-start sample, records `am start -W`
`TotalTime`, then launches the app once more and attempts a fixed-duration `gfxinfo` idle
observation. It emits JSON so a later candidate can be measured with the same procedure.

## Capture record

| Item | Value |
|---|---|
| Capture date | 2026-09-03 |
| Branch | `codex/v1.5-b` |
| HEAD | `9ccddae697c4df76a4903892ad859c8d45d18498` |
| Device | `emulator-5558` (`sdk_gphone64_x86_64`) |
| Package | `io.github.yingqiu0871.evolune` |
| Installed identity | `1.4.0` / `101040000` |
| Cold-start samples | 20 / 20 |
| Cold-start median | 256.5 ms |
| Cold-start mean | 270.5 ms |
| Cold-start range | 206–438 ms |

Cold-start samples, in order: `438, 419, 261, 206, 270, 211, 277, 279, 233, 242,
283, 306, 310, 299, 239, 252, 228, 228, 215, 214 ms`.

## Frame observation status

The 60-second `dumpsys gfxinfo` interval returned `totalFramesRendered=0`, so the frame
sample is marked `INVALID_NO_FRAME_SAMPLES`. The two instantaneous process CPU snapshots
both reported `1%`; they are not a CPU-time measurement and must not be used as a before
baseline.

This means the startup sample is reusable for a later run only when the APK, data state,
device image, and clock setup are held constant. The frame and Home recomposition baseline
still needs a valid foreground trace or recomposition counter before any production change.

## Decision

No production optimization is authorized from this capture. The next measurement step is to
make the Home foreground/frame instrumentation observable, then capture the fixed 60-second
Home scenario and the three PK datasets described in `V15_PLAN.md`. The known 15/15
`ComposeTimeoutException` comparison against sealed v1.4.0 remains excluded from v1.5-B
optimization scope.
