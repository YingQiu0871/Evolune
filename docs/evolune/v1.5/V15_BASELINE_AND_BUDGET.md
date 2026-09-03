# Evolune v1.5-A — Baseline and Budget

## Release source baseline

| Item | Recorded value |
|---|---|
| Release | `v1.4.0` |
| Tag | `v1.4.0` |
| HEAD | `56fa1d243cd1937eba8fcfb62e90a4a26660d697` |
| Release commit | `56fa1d2` — merged v1.4-A trust/permission foundation |
| Working branch | `codex/v1.5-a` |
| Phone application ID | `io.github.yingqiu0871.evolune` |
| Wear namespace | `io.github.yingqiu0871.evolune.wear` |
| Phone/Wear version name | `1.4.0` |
| Phone/Wear version codes | `101040000` / `1101040000` |
| Room authority | Phone Room remains the medication-data authority |
| Wear authority | Phone remains authoritative; Wear cache is rebuildable |
| PK/backup/onboarding | Existing v1.4 behavior and persistence boundaries preserved |

The source baseline is the sealed v1.4.0 commit. Earlier v1.5 planning documents were already
present as intentional documentation changes before this implementation slice; they are not
production-code changes and are carried on the v1.5 branch.

## Device and build environment

| Role | Device | Observed installed identity |
|---|---|---|
| Phone baseline | `emulator-5558`, `sdk_gphone64_x86_64` | Evolune `1.4.0` |
| Wear baseline | `emulator-5554`, `sdk_gwear_x86_64` | Evolune `1.4.0` |

The additional `emulator-5556` instance was still on `1.2.2` and was not used for the v1.5-A
baseline. Measurements are initial observations, not release thresholds.

## JVM baseline

Executed from the repository root:

```text
./gradlew validateEvoluneIdentityAndVersioning :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest
```

Result: PASS. Existing unit suites and shared Phone/Wear identity validation were green before
the launcher change.

## Startup and frame observations

Phone package: `io.github.yingqiu0871.evolune`; activity: `.MainActivity`.

Five force-stop/cold launches using `am start -W` reported total times of `322, 324, 245,
252, 227 ms`; median `252 ms`, arithmetic mean `274 ms`. A repeated start while the existing
activity was already in the foreground reported `0 ms` for all five runs, so it is recorded as
an already-running no-op rather than a warm-start performance claim.

`dumpsys gfxinfo ... framestats` exposed 6 frame rows in this observation. This is a coarse
device snapshot; v1.5-B must use a repeatable frame/trace measurement before changing Compose,
PK, startup, or navigation code.

## Background observation

On the same Phone emulator after launch:

- `dumpsys alarm`: 0 package-matching lines.
- `dumpsys jobscheduler`: 9 package-matching lines.
- `dumpsys batterystats --charged`: 2 Evolune process lines.

These counts are diagnostic observations only. v1.5-C must compare equivalent idle and
representative medication-change scenarios before claiming a power improvement.

## Launcher resource baseline

The Phone manifest resolves both `android:icon` and `android:roundIcon` to `@mipmap/ic_launcher`.
The adaptive resource chain is:

```text
mipmap-anydpi-v26/ic_launcher.xml
  background -> @mipmap/ic_launcher_background
  foreground -> @mipmap/ic_launcher_foreground
  monochrome -> @mipmap/ic_launcher_monochrome
```

The foreground and monochrome resources are matching transparent raster canvases. Their
non-transparent content bounds are approximately:

| Density | Canvas | Content bounds | Content size |
|---|---:|---|---:|
| mdpi | 108×108 | (25,23)–(83,86) | 59×64 |
| xxxhdpi | 432×432 | (106,99)–(327,337) | 222×239 |

The v1.5-A visual change should scale this existing foreground/monochrome content around the
canvas center, regenerate legacy composite launcher rasters from the same adjusted source,
and leave the adaptive wiring and background palette unchanged.

## Measurement budget

- v1.5-A: baseline capture, resource inspection, and launcher adjustment only.
- v1.5-B: no optimization without same-device before/after evidence and output-parity tests.
- v1.5-C: no power claim without wakeup/background-work comparison.
- v1.5-D: no removal without static proof, behavior coverage, and a boundary review.
- No arbitrary CI threshold is frozen from this single emulator snapshot.
