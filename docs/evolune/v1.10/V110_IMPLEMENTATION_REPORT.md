# v1.10 — Optional CPA Estimated Curve: Implementation Report

Status: **IMPLEMENTED** (Slices A–E complete). Base plan:
`docs/evolune/v1.10/V110_CPA_CURVE_PLAN.md`. Branch: `feature/v1.10-cpa-curve`.

## 1. Commits

| Slice | Commit | Subject |
|---|---|---|
| A | `953d8a3` | feat(v1.10): add CPA one-compartment model (Slice A) |
| B | `4173490` | feat(v1.10): add CPA curve display setting (Slice B) |
| C | `72c49ef` | feat(v1.10): orchestrate CPA series alongside pkState (Slice C) |
| D | `bbcff52` | feat(v1.10): draw the optional CPA curve on the Home chart (Slice D) |
| E | (this doc's commit) | docs(v1.10): add CPA curve implementation report |

## 2. Files changed

### New

- `app/src/main/java/io/github/yingqiu0871/evolune/pk/cpa/CpaPk.kt` — `CpaPkParameters`,
  `CpaSeries`, `CpaSimulator`, `isCpaEvent`, `isCpaPlan`.
- `app/src/test/java/io/github/yingqiu0871/evolune/pk/cpa/CpaSimulatorTest.kt` — 9 tests.
- `docs/evolune/v1.10/V110_IMPLEMENTATION_REPORT.md` — this file.

### Modified

- `app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt` —
  `UserSettings.showCpaCurve`, `SHOW_CPA_CURVE_KEY`, `SettingsStore.updateShowCpaCurve`;
  `replaceSettings` intentionally left untouched by the new key (S7).
- `app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/SettingsViewModel.kt` —
  `updateShowCpaCurve`.
- `app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/settings/SettingsAppearanceSection.kt` —
  new switch row (`ShowCpaCurveSection`).
- `app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt`,
  `app/src/main/java/io/github/yingqiu0871/evolune/navigation/AppNavigation.kt` — threaded the
  new callback.
- `app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/HRTViewModel.kt` — `cpaState`,
  `CpaSimulationTrigger`, `calculateAndPublishCpa`, new `showCpaCurveFlow` constructor param
  (defaulted, factory wires it from `SettingsDataStore`).
- `app/src/main/java/io/github/yingqiu0871/evolune/ui/components/ConcentrationChart.kt` —
  optional `cpaSeries` parameter, right-axis scale/labels, CPA line, tooltip addition,
  conditional `contentDescription`.
- `app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/HomeScreen.kt` — legend row,
  `cpaState` wiring into `ChartCard`.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh-rCN/strings.xml` — new
  Settings strings (switch title/subtitle) and chart strings (legend, content description).
- Test fakes updated to implement `SettingsStore.updateShowCpaCurve`:
  `app/src/test/java/io/github/yingqiu0871/evolune/viewmodel/SettingsUpdateCheckTest.kt`,
  `app/src/test/java/io/github/yingqiu0871/evolune/healthconnect/HealthConnectWeightSyncCoordinatorTest.kt`,
  `app/src/test/java/io/github/yingqiu0871/evolune/history/retrospective/RetrospectiveTestFixtures.kt`,
  `app/src/androidTest/java/io/github/yingqiu0871/evolune/backup/B2RoomRestorePersistenceTest.kt`
  (this one also preserves `showCpaCurve` across its `replaceSettings` fake, matching S7).
- `app/src/androidTest/java/io/github/yingqiu0871/evolune/ui/screens/SettingsCategoryScreenTest.kt`,
  `app/src/androidTest/java/io/github/yingqiu0871/evolune/ui/screens/SettingsPaletteSelectorTest.kt` —
  threaded the new `onShowCpaCurveChange` parameter through existing preview/test call sites so
  `compileDebugAndroidTestKotlin` stays green (instrumented tests themselves were not run, per
  instructions).
- `app/src/test/java/io/github/yingqiu0871/evolune/viewmodel/HRTViewModelTest.kt` — 3 new tests
  plus a `cpaEvent()` fixture helper and a `showCpaCurveFlow` fixture parameter.

No file under the "Do NOT modify" list (`pk/SimulationEngine.kt`, `pk/ThreeCompartmentModel.kt`,
`pk/ParameterResolver.kt`, `pk/PKParams.kt`, `pk/PKParameters.kt`, `CorePK`, `PKState`,
`PkSimulationCalculator`/`PkSimulationInput`, `wear/`, `widget/`, `ui/screens/retrospective/`,
backup codec, Room entities/DAOs/schemas, export codecs) was touched.

## 3. New tests and counts

- `CpaSimulatorTest` (9 tests): analytic Bateman match (relative tolerance 1e-9), terminal
  half-life ratio, steady-state accumulation vs. the closed-form multi-dose formula (< 1 %),
  dose linearity, zero concentration before first dose, non-CPA/missing-type event exclusion,
  cancellation-check invocation count, and `isCpaEvent`/`isCpaPlan` selection semantics.
- `HRTViewModelTest` (+3, total 23): CPA setting off never publishes a series even with CPA
  events present; setting on with CPA events publishes a non-null series on the E2 time grid;
  E2 `PKState` is identical (`currentConcentration`) whether the CPA setting is on or off.

### Full suite counts (this run)

| Module | Tests | Failures | Errors |
|---|---:|---:|---:|
| `:app` (`testDebugUnitTest`) | 1460 | 0 | 0 |
| `:wear` (`testDebugUnitTest`) | 90 | 0 | 0 |
| `:experience-core` (`test`) | 171 | 0 | 0 |

`./gradlew test`, `./gradlew :app:assembleDebug`, and `./gradlew :wear:assembleDebug` all
completed with `BUILD SUCCESSFUL`.

No existing test was modified to make it pass — the only edits to pre-existing test files were
(a) adding the new `SettingsStore.updateShowCpaCurve` override to fakes, matching the plan's
explicit "updating `SettingsStore` fakes ... is fine," and (b) adding the new
`onShowCpaCurveChange` parameter to two androidTest call sites so they keep compiling against
the widened `SettingsScreen` signature. Neither changes what those tests assert.

## 4. Deviations from the plan

1. **CPA input source for orchestration (Slice C).** The plan's Slice C text does not specify
   exactly which event feed to filter; §1.2/§4.1 of the audit and the existing E2 path use
   `repository.getEventsForPk(now)` for the *value* while `eventUpdates` (unbounded
   `observeAll()`) only drives *recomputation timing*. For CPA, `calculateAndPublishCpa` uses
   the same `eventUpdates` list both as the recompute trigger and as the value to filter with
   `isCpaEvent`, rather than issuing a second `repository.getEventsForPk(now)` call. This keeps
   the CPA path simple and avoids an extra repository round trip; since anti-androgen dosing
   frequency is low and `DoseEventStatus` currently has only one member (`RECORDED`), the result
   is equivalent in practice. This is a plumbing detail, not a semantics change — S2–S9 are
   unaffected.
2. **CPA line has no baseline/plan-fork split.** E2 draws three curve segments (historical,
   baseline-without-plan, future-plan) split at a fork point. The plan's Slice D text does not
   ask for the same treatment for CPA, and CPA plan-derived future doses are comparatively rare;
   the CPA series is drawn as a single continuous line. If an owner wants the fork-point split
   for CPA too, that is a follow-up, not a correction.
3. **Chart's `contentDescription`.** `ConcentrationChart` previously had no
   `contentDescription`/semantics block at all. The plan says "chart contentDescription mentions
   CPA only when shown" without specifying a baseline description; a `chart_content_description`
   string was added for the no-CPA case, and `chart_content_description_with_cpa` for the CPA
   case, both new (not overriding any pre-existing behavior).

No deviation weakens S1–S9. In particular S2 (zero E2/Widget/Wear/Retrospective impact) is
covered by the "E2 PKState is identical whether the CPA setting is on or off" test, and the CPA
path never constructs or mutates `PKState`/`PkSimulationInput`/`SimulationEngine`.

## 5. Open questions / what the owner should verify manually on a device

- **Chart visuals**: confirm the CPA line (secondary color) is visually distinct from the
  primary/tertiary E2 lines and the GAHT target band across the app's theme presets, especially
  any preset where `secondary` is close to `primary` or `tertiary`.
- **Right Y-axis legibility**: the right-axis CPA tick labels are drawn in
  `MaterialTheme.colorScheme.secondary`; verify contrast against the card background in both
  light and dark mode, and check that labels don't visually collide with the plot's right edge
  on narrow phone widths (the shared horizontal inset is sized from `max(E2 label width, CPA
  label width)`, but this was not verified against a live narrow-screen render).
- **Dark mode / AMOLED**: verify the new Settings switch row and legend row read correctly under
  `ThemeMode.DARK` and `ThemeMode.AMOLED`.
- **Legend + touch tooltip**: confirm the two-line tooltip (E2 pg/mL + CPA ng/mL) doesn't get
  clipped near the top/right edges of the chart on small screens.
- **Real anti-androgen data**: the model's known limitation (single-dose peak underestimation,
  documented in `CpaPkParameters`'s KDoc and in the Settings subtitle) should be reviewed once
  the owner has real CPA dosing history to sanity-check against.

## 6. Out of scope (unchanged from the plan)

Two-compartment CPA model; CPA in Widgets/Wear/Retrospective PK/Insights; CPA target ranges or
dose advice; MPA/bicalutamide/spironolactone curves; Room/backup/Portable format changes;
version bump and release — none of these were touched.
