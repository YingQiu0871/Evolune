# Evolune v1.5-B — Baseline Capture

## Purpose

This document records the first repeatable v1.5-B measurement attempt. It is an observation
record, not an optimization claim. Production performance changes remain gated on a valid,
same-device before/after comparison.

Device serials are scoped to the capture timestamp. Emulator ports may be reused after an AVD is
restarted; this record therefore treats the model/serial pair recorded at capture time as the
identity, not the port number alone.

## Reproduction

Run from the repository root in PowerShell:

```text
.\scripts\measure_v15b_baseline.ps1 -Serial emulator-5558 -ColdStartRuns 20 -WarmStartRuns 20 -IdleSeconds 60
```

The script force-stops the Phone package for each cold-start sample, records `am start -W`
`TotalTime`, then wakes the device, keeps the screen on, prewarms the app, and asserts that
the target Activity is both resumed and focused before and after the fixed-duration `gfxinfo`
window. It also records warm-start samples by backgrounding and relaunching the existing task,
requiring a stable process PID and preferring the uniform `WaitTime` metric. It records
preserved-data state, process identity, `/proc/<pid>/stat` CPU ticks, frame-time percentiles, a
dirty-worktree flag, and a schema-v2 validity status. It emits JSON so a later candidate can be
measured with the same procedure without mistaking uncommitted source for a clean commit.

## Harness corrections — 2026-09-05

Two harness-only issues found during the reproducible capture were corrected before the admitted
v1.4.0/v1.5.0 comparisons. The default Activity is now the fully-qualified
`io.github.yingqiu0871.evolune.MainActivity`, because the Debug application ID suffix does not
change the Activity class namespace. Detached baseline runs now record `branch: "(detached)"`
instead of failing while trimming an empty branch name. These changes affect measurement
robustness only; they do not change application behavior or the acceptance interpretation.

## Capture record

| Item | Value |
|---|---|
| Capture date | 2026-09-03 |
| Branch | `codex/v1.5-b` |
| HEAD | captured commit at run time |
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

The original 60-second interval returned `totalFramesRendered=0`, so that frame sample was
marked `INVALID_NO_FRAME_SAMPLES`. Its two instantaneous process CPU snapshots both reported
`1%`; they were not a CPU-time measurement and must not be used as a before baseline. The
schema-v2 harness now requires valid foreground assertions and a process CPU tick window
before a capture can be admitted as `VALID`.

The startup sample is reusable for a later run only when the APK, preserved data state, device
image, and clock setup are held constant. Frame-time data still does not measure Compose
recomposition count; a separate recomposition counter or trace is required before any
production change.

## Decision

No production optimization is authorized from this capture. The next measurement step is to
run the schema-v2 harness with a valid Home foreground window, then capture the fixed
60-second Home scenario and the three PK datasets defined in `V15_PLAN.md`. The known 15/15
`ComposeTimeoutException` comparison against sealed v1.4.0 remains excluded from v1.5-B
optimization scope.

## Schema-v2 harness validation — 2026-09-04

The enhanced harness was run against the preserved-data Phone `1.4.0` package on
`emulator-5560`, branch `codex/v1.5-b`, HEAD `aa0a98e`. It kept the device awake and recorded
both `topResumedActivity`/`mFocusedApp` assertions at the start and end of the window:

| Item | Value |
|---|---:|
| Scenario | `home-empty` |
| Cold-start samples | 20 / 20 |
| Cold-start median / mean | 208.5 ms / 221.0 ms |
| Cold-start range | 173–296 ms |
| Foreground and Home assertions | PASS / PASS |
| Window | 60.002 s |
| Total frames / janky frames | 1 / 1 |
| Frame p50/p90/p95/p99 | 16 / 16 / 16 / 16 ms |
| Window CPU | 0.45% average process CPU |
| Technical capture status | `VALID` |

This is a valid technical capture, but `home-empty` displays no medication data or chart.
It is therefore not evidence about the cost of the one-second Home time tick or chart
recomposition. The reproducible populated captures and the three fixed PK datasets below are
the admitted v1.5-B evidence.

## Populated Home capture — historical record

A previous record described a 109-record import on Phone `emulator-5560`. The user-provided
path `C:\\Users\\1\\Downloads\\evolune\\_export (4).json` is not present in the current
environment (`Test-Path` returned false), so that raw export cannot be independently rechecked
or reused here. It is not treated as the current v1.5-B fixture or as a release-blocking
measurement. The reproducible populated captures below use the deterministic fixtures defined
in `V15_PLAN.md` and imported through the production file-import flow.

The historical schema-v2 harness record was reported against the preserved-data Phone `1.4.0`
package on branch `codex/v1.5-b`, HEAD `aa0a98e`:

| Item | Value |
|---|---:|
| Scenario | `home-representative` |
| Data state | preserved; 109 imported event records |
| Cold-start samples | 20 / 20 |
| Cold-start median / mean | 187.5 ms / 200.3 ms |
| Cold-start range | 164–269 ms |
| Foreground and Home assertions | PASS / PASS |
| Window | 60.005 s |
| Total frames / janky frames | 65 / 54 |
| Frame p50/p90/p95/p99 | 17 / 18 / 18 / 18 ms |
| Window CPU | 1.27% average process CPU |
| Technical capture status | `VALID` |

This historical record is retained for traceability only. Because its source JSON is not
available in the current environment, the deterministic captures and the current-source
recomposition capture below are the admitted evidence for v1.5-B.

## Deterministic PK fixture captures — 2026-09-04

The three fixed datasets from `V15_PLAN.md` were generated with
`scripts/generate_v15b_fixture.ps1` and imported through the production JSON file-import flow
on the isolated Debug Phone `emulator-5554` (`1.4.0-debug`, package
`io.github.yingqiu0871.evolune.debug`). The generated inputs use reference UTC
`2026-09-04T12:00:00Z`, weight `62.0 kg`, injection/EV/2.0 mg events, and deterministic IDs.
No generated JSON was committed. The empty fixture was verified on a cleared app state; the
steady and dense states were verified by the Plans screen before importing their events.

| Dataset | Plans / slots | Expected events | Import confirmation | Clock setup |
|---|---:|---:|---|---|
| `PK-EMPTY` | 0 / 0 | 0 | cleared state; Home showed `暂无数据` | UTC fixture reference; device local clock unchanged |
| `PK-STEADY` | 1 / 3 | 90 | `成功导入 90 条记录` | UTC fixture reference; 30 days × 3 slots |
| `PK-DENSE` | 3 / 9 | 810 | `成功导入 810 条记录` | UTC fixture reference; 3 plans × 90 days × 3 slots |

All three schema-v2 captures below were `VALID`; the harness asserted the target Activity was
resumed and focused at both ends of each 60-second window and collected process CPU ticks.
The capture is a Home/rendering baseline. It is not a claim that the PK engine itself has been
isolated or that a Compose recomposition count has been measured.

| Dataset | Cold start median / mean / range | Window | Frames / janky | Frame p50/p90/p95/p99 | Window CPU | Home result |
|---|---:|---:|---:|---:|---:|---|
| `PK-EMPTY` | 1384 / 1473.2 / 1353–1809 ms | 60.005 s | 1 / 1 | 18 / 18 / 18 / 18 ms | 0.77% | empty state remained visible |
| `PK-STEADY` | 1424.5 / 1424.8 / 1374–1470 ms | 60.014 s | 67 / 63 | 32 / 34 / 48 / 450 ms | 2.72% | `2342.9 pg/mL`, above reference range |
| `PK-DENSE` | 1607 / 1604.0 / 1554–1646 ms | 60.013 s | 196 / 65 | 22 / 34 / 34 / 250 ms | 11.08% | completed at `7029.4 pg/mL`; start showed `计算中…` |

The dense state is measurably heavier than empty and steady in this environment, including
window CPU and cold-start time. This is valid evidence for a focused follow-up trace, but it is
not by itself permission to alter PK inputs, Room queries, chart structure, or navigation.

## Compose candidate comparison — 2026-09-04

The dense fixture was measured again on the same isolated Debug Phone with the same preserved
data state. The first run used the diagnostic-only chart implementation; the candidate keeps
`currentTimeH` as a stable state reference and reads it during Canvas drawing. Both runs used
the schema-v2 harness, a focused/resumed Home window, and the same 20 cold-start sequence.

| Run | Cold start median / mean / range | Frames / janky | Frame p50/p90/p95/p99 | Window CPU | Home compositions | Chart compositions | End concentration |
|---|---:|---:|---:|---:|---:|---:|---:|
| Before (`pk-dense-compose-final`) | 1605.5 / 1602.4 / 1525–1640 ms | 184 / 63 | 20 / 34 / 34 / 250 ms | 11.11% | 66 | 61 | `7030.3 pg/mL` |
| Candidate (`pk-dense-candidate-final`) | 1606 / 1602.8 / 1531–1673 ms | 194 / 56 | 18 / 23 / 32 / 250 ms | 10.95% | 65 | 1 | `7030.3 pg/mL` |

The candidate reduced successful `ConcentrationChart` compositions from 61 to 1 during the
60-second window while preserving the Home result and PK output. The frame and CPU differences
are small enough to treat as run-to-run variation, not as a proven end-to-end performance gain.
The composition counts are Debug-only `SideEffect` observations and are absent from Release
build behavior. This closes the targeted chart-recomposition evidence gate; full regression and
Release verification remain required before calling v1.5-B complete.

## Current-source Home recomposition capture — 2026-09-05

After moving the realtime clock read to the card/drawing boundary, the schema-v2 harness was
rerun on the isolated Debug Phone `emulator-5554` at HEAD `aa0a98e`. The debug package was
`io.github.yingqiu0871.evolune.debug` version `1.5.0-debug`; both captures used preserved app
data and the same 20 cold-start samples plus a 60-second awake, focused Home window.

| Dataset | Cold start median / mean / range | Frames / janky | Frame p50/p90/p95/p99 | Window CPU | Home compositions | Chart compositions | Visible result |
|---|---:|---:|---:|---:|---:|---:|---|
| Empty | 1606 / 1653.2 / 1481–2115 ms | 1 / 1 | 17 / 17 / 17 / 17 ms | 0.43% | 3 | 0 | empty state remained visible |
| `PK-STEADY` | 1540 / 1561.8 / 1504–1821 ms | 66 / 59 | 18 / 32 / 34 / 38 ms | 2.37% | 3 | 1 | `2341.1` → `2341.0 pg/mL` |

Both captures were admitted as `VALID`: foreground assertions passed at both ends, the Home
markers were present, and the process identity remained stable during the CPU window. The
composition counters are Debug-only `SideEffect` observations. Relative to the earlier
populated candidate record (`Home=65`, `Chart=61`), the current source keeps the chart at one
successful composition and the Home content at three while the realtime concentration still
changes. This is evidence for the scoped Compose state-boundary cleanup, not proof of a global
frame-time or energy improvement; the startup/performance and energy rows remain open.

## Current-source empty Home capture — 2026-09-05

After completing the Debug package's first-launch disclosures, automatic update checking was
disabled on the measurement device so the Debug-only Release notice could not obscure a cold
launch. No production source or persisted medication data was changed. The schema-v2 harness
ran on Phone `emulator-5556` with package `io.github.yingqiu0871.evolune.debug`, version
`1.5.0-debug`, branch `codex/v1.5-b`, HEAD `aa0a98e`; the worktree was dirty and the record is
diagnostic rather than a clean-commit release benchmark.

| Item | Value |
|---|---:|
| Data state | preserved, empty Home |
| Cold-start samples | 20 / 20 |
| Cold-start median / mean | 1411.5 ms / 1416.8 ms |
| Cold-start range | 1351–1537 ms |
| Warm-start samples | 20 / 20 |
| Warm-start median / mean (`WaitTime`) | 55.5 ms / 52.6 ms |
| Warm-start range (`WaitTime`) | 12–64 ms |
| Warm-start process stability | PASS; PID stable for all samples |
| Foreground and Home assertions | PASS / PASS |
| Window | 60.015 s |
| Total frames / janky frames | 1 / 1 |
| Frame p50/p90/p95/p99 | 17 / 17 / 17 / 17 ms |
| Window CPU | 0.28% average process CPU |
| Home / Chart compositions | 3 / unavailable (empty state) |
| Technical capture status | `VALID` |

This closes the previously invalid empty-Home collection attempt as a valid harness run and adds
the missing warm-start evidence. It does not replace the populated Home or PK fixture captures,
and it does not claim a global startup, frame-time, or energy improvement.

## Same-device startup comparison — 2026-09-05

The v1.4.0 and v1.5.0 Debug packages were measured in place on the same Phone
`emulator-5558` (`sdk_gphone16k_x86_64`) with preserved empty Home data, the same clock and
screen conditions, and automatic update checking disabled. The v1.5 package was installed as
an in-place version upgrade after the v1.4.0 run; onboarding remained complete and Home remained
visible. Both records used the final schema-v2 harness, 20 cold starts, 20 warm starts, and a
60-second focused/resumed window. The worktree was dirty for both records, so this is a
same-device diagnostic comparison rather than a clean-commit release benchmark.

| Build | Capture | Cold median / mean / range | Warm `WaitTime` median / mean / range | Frames / janky | Window CPU | Home compositions | Status |
|---|---|---:|---:|---:|---:|---:|---|
| v1.4.0-debug (`101040000`) | empty Home | 1294.5 / 1319.0 / 1213–1488 ms | 13 / 13.4 / 11–16 ms | 1 / 1 | 0.50% | unavailable | `VALID` |
| v1.5.0-debug (`101050000`) | empty Home | 1281 / 1292.4 / 1178–1495 ms | 14 / 14.2 / 11–19 ms | 1 / 1 | 0.23% | 3 | `VALID` |

The v1.5 run shows no startup or empty-Home CPU regression in this same-device sample. The
comparison is not a global performance claim: it excludes populated Home rendering, PK runtime,
Widget rendering, Wear transport, physical battery discharge, and Release-signing conditions,
which remain covered by their own evidence or open gates.

## Same-device populated Home comparison — 2026-09-05

The v1.4.0 and v1.5.0 Debug packages were measured on the same Phone `emulator-5558` with the
upgrade fixture's one enabled plan, three slots, and one recorded dose event preserved through
the in-place upgrade. Automatic update prompts were disabled by the fixture. Both runs used the
same schema-v2 harness, 20 cold starts, 20 warm starts, and a 60-second focused/resumed Home
window. The v1.4.0 run was from clean detached baseline HEAD `56fa1d2`; the v1.5.0 run was from
current dirty HEAD `aa0a98e`, so these are diagnostic measurements rather than clean-release
benchmarks.

| Build | Cold median / mean / range | Warm `WaitTime` median / mean / range | Frames / janky | Frame p50/p90/p95/p99 | Window CPU | Home / Chart compositions | Status |
|---|---:|---:|---:|---:|---:|---:|---|
| v1.4.0-debug (`101040000`) | 1335 / 1362.1 / 1251–1698 ms | 12 / 12.0 / 10–15 ms | 66 / 66 | 34 / 36 / 48 / 85 ms | 2.65% | unavailable / unavailable | `VALID` |
| v1.5.0-debug (`101050000`) | 1342 / 1356.7 / 1251–1666 ms | 13 / 13.4 / 10–20 ms | 66 / 57 | 18 / 32 / 34 / 34 ms | 1.93% | 3 / 1 | `VALID` |

The populated Home window preserved the same visible result (`0.1 pg/mL`) at both boundaries and
the post-upgrade persistence check passed `1/1`. In this session v1.5 reduced the measured
process CPU by 0.72 percentage points and the recorded janky-frame count by 9, while cold/warm
startup remained within the observed single-session range. The frame and CPU differences are
evidence for the targeted state-boundary change, not a statistically established device-wide
performance claim; Widget, Wear, background, and Release comparisons remain separate gates.

## Wear publication boundary audit — 2026-09-05

The production path was reviewed after the Home realtime clock moved to the drawing boundary.
`HRTViewModel.currentTimeH` emits once per second for the visible Home card/chart, but it is not
part of the `pkState` calculation trigger. The Phone `LaunchedEffect` blocks that publish the
dashboard and snapshot depend on plans, dose events, simulation output, concentration, and
simulation status/error—not on `currentTimeH`. Therefore this change does not establish a
per-second Wear Data Layer publish loop, and no speculative transport or energy change was made.
The unpaired-node limitation and physical battery requirement remain open in the acceptance matrix.

## PK engine runtime comparison — 2026-09-05

The same `V15PkPerformanceDeviceTest` and fixed in-memory inputs were run on the same Phone
`emulator-5554` against the v1.4.0 Debug APK and then the current v1.5.0 Debug APK. Each dataset
used one warm-up calculation followed by three measured calculations. The reference instant was
`2026-09-04T12:00:00Z`, the zone was `Europe/Paris`, and body weight was `62.0 kg`.

| Dataset | Shape | v1.4.0 median / mean | v1.5.0 median / mean | Repeated output |
|---|---:|---:|---:|---|
| `PK-EMPTY` | 0 plans / 0 slots / 0 events | 0.0071 / 0.011 ms | 0.0205 / 0.017 ms | empty / empty |
| `PK-STEADY` | 1 / 3 / 90 | 222.1943 / 231.038 ms | 194.3778 / 215.830 ms | `3.012827399306029` |
| `PK-DENSE` | 3 / 9 / 810 | 2329.0368 / 2328.154 ms | 2220.1271 / 2236.079 ms | `18.076964395836175` |

The concentration values and result-array sizes matched on repeated calculations for every
dataset. The elapsed-time differences are diagnostic observations from one emulator session,
not a frozen threshold or a claim of a statistically established global PK speedup. No PK input,
model, persistence, or output-format behavior was changed for this measurement.

## Room repository runtime comparison — 2026-09-05

The same `V15RoomPerformanceDeviceTest` and fixed Room fixture shapes were run on the same Phone
`emulator-5558` against the v1.4.0 Debug APK and then the current v1.5.0 Debug APK. The test APK
was compiled from the current source and targets the unchanged debug package identity; both app
versions exposed the same repository and mapper APIs. Each dataset used two warm-up passes and
seven measured passes against an isolated in-memory Room database. `pkFetch` includes the
Repository query, entity-to-domain mapping, and the v1.5 PK-window selection; `enabledPlanAggregate`
includes the Room relation query and aggregate mapping. Results were identical on every measured
pass. The recorded `pkEvents` count is the 30-day window returned by the repository, not the full
fixture event count.

| Dataset | Fixture shape | `pkEvents` | v1.4.0 `pkFetch` median / mean | v1.5.0 `pkFetch` median / mean | v1.4.0 enabled plans median | v1.5.0 enabled plans median |
|---|---:|---:|---:|---:|---:|---:|
| `PK-EMPTY` | 0 plans / 0 slots / 0 events | 0 | 0.901 / 0.969 ms | 1.200 / 1.151 ms | 1.559 ms | 1.577 ms |
| `PK-STEADY` | 1 / 3 / 90 | 88 | 3.567 / 3.628 ms | 4.057 / 4.013 ms | 2.060 ms | 2.092 ms |
| `PK-DENSE` | 3 / 9 / 810 | 264 | 7.539 / 7.444 ms | 8.091 / 8.057 ms | 2.357 ms | 2.264 ms |

This comparison shows no material change to the Room query contract or result shape, but it is
not a statistically powered latency claim: the dense PK fetch was about 0.55 ms slower in this
single session, while the plan-aggregate median was about 0.09 ms faster. No Room optimization is
justified by these observations, and the broad Startup/performance row remains open for the
populated Home, Widget, Wear, background, and current Release comparisons.

## Widget refresh data-path measurement — 2026-09-05

`V15WidgetPerformanceDeviceTest` ran on Phone `emulator-5558` with the same three fixed datasets,
using an isolated in-memory Room database, a fixed `2026-09-04T12:00:00Z` instant, `Europe/Paris`,
and `62.0 kg`. Each dataset used two warm-ups and seven measured passes. The test measured the
production `WidgetSnapshotLoader` and `WidgetUiMapper` paths separately, asserted exact snapshot
and UI-model equality across all passes, and left launcher-host/RemoteViews rendering covered by
the existing Widget device regression.

| Dataset | Shape | Rows | Snapshot load median / mean | UI mapping median / mean | Concentration |
|---|---:|---:|---:|---:|---:|
| `PK-EMPTY` | 0 plans / 0 slots / 0 events | 0 | 4.3901 / 4.305 ms | 0.0168 / 0.022 ms | none |
| `PK-STEADY` | 1 / 3 / 90 | 3 | 7.6618 / 7.725 ms | 0.0233 / 0.027 ms | `3.012827399306029` |
| `PK-DENSE` | 3 / 9 / 810 | 9 | 14.0526 / 14.251 ms | 0.0363 / 0.041 ms | `18.076964395836175` |

The data path completed successfully and produced identical outputs on every pass. Dense data
cost more than empty data as expected, but this single emulator session does not establish a
threshold or justify a Widget production optimization. The broad Startup/performance row remains
open for a clean Release comparison and the external Wear/physical-device requirements.

## Release build gate — 2026-09-04

The current-source Release build was checked with `:app:assembleRelease --no-daemon`. The build
stopped during the existing signing preflight because the approved environment did not provide
`EVOLUNE_KEYSTORE_PATH`, `EVOLUNE_KEYSTORE_PASSWORD`, `EVOLUNE_KEY_ALIAS`, and
`EVOLUNE_KEY_PASSWORD`. No signing configuration was changed and no debug-signed artifact was
treated as a Release artifact. The previously recorded v1.5.0 APK hashes therefore refer to an
earlier source state; Release compilation, identity, and signing verification for the current
candidate remain open until the persistent v1.5 signing environment is available.
