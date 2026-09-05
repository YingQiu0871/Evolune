# Evolune v1.5-C — Background Observation

## Method

`scripts/measure_v15c_background.ps1` launches the selected package, sends it to the
launcher, forces the display off, clears logcat, and samples a fixed screen-off window. It
records the repository HEAD and dirty-worktree flag, package process CPU tick delta, Alarm and
Job scheduler lines, package-related Wake Lock lines, batterystats package lines, display state,
and matching logcat lines. A capture is admitted only when `dumpsys display` confirms `OFF`,
`DOZE`, or `DOZE_SUSPEND` at both boundaries and the elapsed window meets the requested
duration. The latter two states are required for Wear OS, whose power service reports
`mWakefulness=Dozing` during display-off ambient sleep.

This is an observation harness, not a battery benchmark. It does not reset device battery
statistics, mutate application data, or freeze a power threshold.

Device serials are scoped to the capture timestamp. Emulator ports were reused between the
historical Phone and Wear runs; the AVD model and the capture-time assignment are authoritative,
so a later `adb devices` mapping must not be used to relabel an earlier capture.

## Same-device v1.4 → v1.5 comparison

Phone: `emulator-5554`, Android 15. Both runs used the same 30-second screen-off scenario and
the Debug application ID so the package variant and scheduler accounting were comparable.

| Build | Status | Duration | Process CPU | Alarm lines | Job lines | Wake Lock lines | Package battery lines |
|---|---|---:|---:|---:|---:|---:|---:|
| v1.4.0 Debug (`101040000`) | `VALID` | 30.011 s | 0.13% | 0 | 4 | 0 | 1 |
| v1.5.0 Debug (`101050000`) | `VALID` | 30.012 s | 0.10% | 0 | 4 | 0 | 1 |

Both runs kept the same process alive for the window. The four Job lines were scheduler
accounting entries; no active application Job or application Wake Lock appeared in the
capture. The v1.5 process CPU difference is 0.03 percentage points and is not treated as a
performance threshold or a battery claim.

The current v1.5 Release package was also observed on the same Phone for 30.013 seconds:
`VALID`, process CPU `0.63%`, Alarm lines `0`, Job lines `4`, Wake Lock lines `0`, and package
battery lines `1`. This Release observation is diagnostic only and is not mixed into the Debug
before/after comparison.

The current-source v1.5 Debug package was independently spot-captured on Phone `emulator-5556`
on 2026-09-05. The 30.010-second window was `VALID`, with `Asleep` power state at both
boundaries, stable PID `14595`, 18 CPU ticks at 100 Hz (`0.60%` average process CPU), zero
Alarm lines, and four inactive scheduler-accounting Job lines. The JSON recorded
`worktreeDirty=true`, so this is explicitly a working-tree diagnostic rather than a clean-commit
artifact. It confirms the harness still admits a current-source screen-off window after the
cleanup edits; it is not a battery or cross-version performance claim.

## Same-device Wear OS v1.4 → v1.5 comparison — 2026-09-05

Wear `emulator-5554` was measured with the same 30-second screen-off procedure. The v1.4.0
Debug APK was built from the detached `v1.4.0` baseline worktree, measured, and then replaced by
the current v1.5.0 Debug APK. Both captures were admitted as `VALID`; the display state was
`DOZE_SUSPEND` at both boundaries, which is the Wear emulator's explicit screen-off state.

| Build | Status | Duration | Display state | Process CPU | Alarm lines | Job lines | Active app Wake Lock | Package battery lines |
|---|---|---:|---|---:|---:|---:|---|---:|
| v1.4.0 Debug (`1101040000`) | `VALID` | 30.008 s | `DOZE_SUSPEND` | 0.00% | 0 | 4 | none | 1 |
| v1.5.0 Debug (`1101050000`) | `VALID` | 30.008 s | `DOZE_SUSPEND` | 0.00% | 0 | 4 | none | 1 |

The process PID stayed stable in both windows. The raw Wake Lock list contained only historical
`launch` acquire/release pairs from starting the app; no application Wake Lock remained active
during either capture. This is a valid Wear background observation and shows no new scheduler or
Wake Lock signal, but it is not a physical battery-discharge result and does not exercise Phone/
Wear Data Layer delivery.

## Decision

The observation found no new periodic polling, package Wake Lock, or Alarm entry in the tested
screen-off window. It does not cover a physical battery discharge, the Wear active/background
matrix, or a scheduled alarm firing. Per the owner decision for this RC, the Energy/background
comparison is `SKIPPED_BY_OWNER`. This is an explicit release waiver, not a PASS or a battery-
improvement claim; no production background code was changed based on this capture.
