# v1.10 — Optional CPA Estimated Curve: Phase 0 Audit & Implementation Plan

Status: **PLAN — APPROVED BY OWNER FOR IMPLEMENTATION (2026-09-30)**. Baseline: `main` @ `8e487ad` (v1.9.1 + CI).

This plan turns the ROADMAP "Optional CPA Pharmacokinetic Curve" candidate into a bounded, default-off
feature. It records the Phase 0 audit, the frozen semantics, and the implementation slices.

## 1. Phase 0 audit findings

### 1.1 CPA identity is already recorded — no Room migration needed

- Anti-androgen intake uses `Route.ANTIANDROGEN` (`pk/Route.kt`) with `ExtraKey.ANTI_ANDROGEN_TYPE`.
- Stable codes: `0.0 = CPA`, `1.0 = MPA`, `2.0 = BICALUTAMIDE`, `3.0 = SPIRONOLACTONE`
  (`MedicationPlanEditor.toStableCode()` / `selectedAntiAndrogen()`; the record sheet writes
  `AntiAndrogen.ordinal`, which is identical for the current enum order).
- Every plan-derived record path copies `plan.extras` into the event (`DoseEventEditor`, `WidgetWork`,
  `WearDoseActionHandler`, `WearAppConfirmationHandler`, `ReminderDoseFactory`), and the plan editor always
  writes the type for anti-androgen plans. Manual records write it from the record sheet.
- `doseMG` is the CPA dose in mg for anti-androgen events (the `ester` slot is a placeholder `E2`).
- Conclusion: CPA events are identifiable from existing data. **No schema, DAO, backup-format or
  Portable-format change.** Events with a missing/unknown type code are **not** treated as CPA (no guessing).

### 1.2 Current PK pipeline

- `DefaultPkSimulationCalculator` (`viewmodel/PkSimulationCalculator.kt`) filters out `Route.ANTIANDROGEN`
  and produces `PKState` (E2, pg/mL). `PKState` is consumed by Home, Widgets (via `MainActivity`) and Wear
  (`WearAppDataLayer`).
- `SimulationEngine`, `ThreeCompartmentModel`, `ParameterResolver`, `CorePK` are the frozen E2 numerical
  source (golden regression tests exist).
- Home chart: `ui/components/ConcentrationChart.kt` (single pg/mL Y axis) used only by `HomeScreen.ChartCard`.
- Retrospective PK (`history/pk`, `ui/screens/retrospective`) is a frozen v1.7 Phase C surface.
- Settings: `data/SettingsDataStore.kt` (`UserSettings`, `SettingsStore`, `AtomicSettingsStore.replaceSettings`
  used by restore).

### 1.3 Scientific review (parameter decision)

| Source | Value |
|---|---|
| Androcur SmPC/PI, 50 mg oral | Cmax ≈ 140 ng/mL at ≈ 3 h; terminal t½ 43.9 ± 12.8 h; total CL 3.5 ± 1.5 mL/min/kg |
| Androcur SmPC/PI, 100 mg oral | Cmax 239 ± 114 ng/mL at 2.8 h; t½ 42.8 ± 9.7 h; CL 3.8 ± 2.2 mL/min/kg |
| Androcur SmPC | absolute oral bioavailability ≈ 88 % (literature range 68–100 %) |
| Oyama `logic.ts` (reference only) | one-compartment oral: ka 1.0 h⁻¹, ke 0.017 h⁻¹, F 0.7, Vd 14 L/kg, output ng/mL |

Derivation: ke = ln2 / 43.9 h = 0.01579 h⁻¹; CL = 3.5 mL/min/kg = 0.210 L/h/kg; Vd = CL / ke ≈ 13.3 L/kg.
This independently agrees with Oyama's Vd 14 L/kg / ke 0.017 h⁻¹, so the one-compartment terminal-phase
model reproduces AUC, accumulation and trough/average steady-state levels.

**Known limitation (must be disclosed in UI copy and model KDoc):** the real disposition is biphasic; a
one-compartment model reproduces AUC and the terminal phase but **underestimates single-dose peaks**
(≈ 40–45 ng/mL predicted for 50 mg at 70 kg vs ≈ 140 ng/mL reported for 50 mg). A two-compartment model is deferred
until fitted parameters with a citable source exist.

**Frozen v1.10 parameters** (`CpaPkParameters`):

- `KA_PER_H = 1.0` (absorption; Tmax of the reference data is ~3 h, ka = 1.0 gives Tmax ≈ 4–5 h, acceptable)
- `KE_PER_H = ln(2) / 43.9`  (≈ 0.01579)
- `F = 0.88`
- `VD_L_PER_KG = 13.3`
- Output unit: **ng/mL**. Amount (mg) × 1e6 / (Vd_L × 1000 mL) → ng/mL.

## 2. Frozen semantics

S1. **Default off.** New setting `showCpaCurve: Boolean = false` (DataStore key `show_cpa_curve`).
S2. **Zero E2 impact.** With the toggle off *or on*, `PKState`, E2 curves, current E2, Widgets, Wear
    snapshots/Tiles/Complications and Retrospective PK are byte-for-byte unchanged. CPA is computed in a
    separate path and never enters `PkSimulationCalculator`, `PKState` or `SimulationEngine`.
S3. **Selection.** CPA events = `status == RECORDED && route == ANTIANDROGEN &&
    extras[ANTI_ANDROGEN_TYPE] == 0.0 && doseMG > 0 && doseMG.isFinite()`. Missing/unknown codes excluded.
    Future predictions: enabled plans with `route == ANTIANDROGEN` and type code `0.0`, predicted with the
    existing `MedicationPlanPredictor` (15 days ahead) and `filterConflictingPredictions`, exactly like E2.
S4. **Model.** Per-event oral Bateman one-compartment with §1.3 parameters, superposed linearly; body
    weight = the same `UserSettings.bodyWeight` used for E2. Same time window and step count as the E2
    calculation (`currentTimeH ± 15 days`, 12 points/h, ≥ 1000 steps) so both series share the X grid.
S5. **Separate units, never summed.** CPA series is ng/mL on its own right Y axis; E2 stays pg/mL on the left.
S6. **Computed only when enabled.** When off, no CPA work runs at all.
S7. **Not in backup/restore/export.** `showCpaCurve` is a local display preference: not written by the
    backup codec, not touched by `replaceSettings` (restore keeps the local value), not in Portable JSON/CSV.
    Backup schema stays v2.
S8. **Always labelled as estimate.** Legend and settings copy state "模型估算，非实测" and the peak
    limitation; never described as a lab result or used for dosing advice. No reference/target range for CPA.
S9. **Home chart only.** No Widget, Wear, Retrospective PK, Insights or History change in v1.10.

## 3. Implementation slices

### Slice A — CPA model (pure Kotlin, JVM tests)

- New `app/src/main/java/io/github/yingqiu0871/evolune/pk/cpa/CpaPk.kt`:
  `object CpaPkParameters` (constants + KDoc with the §1.3 sources and limitation),
  `data class CpaSeries(val timeH: List<Double>, val concNgMl: List<Double>) { fun concentration(hour): Double? }`
  (same interpolation contract as `SimulationResult.concentration`),
  `object CpaSimulator { fun simulate(events: List<pk.DoseEvent>, bodyWeightKG, startTimeH, endTimeH, numberOfSteps, cancellationCheck: () -> Unit = {}): CpaSeries }`
  plus `fun isCpaEvent(...)` selection helpers for domain `core.model.DoseEvent` and `MedicationPlan`.
- Do **not** modify `SimulationEngine`, `ThreeCompartmentModel`, `ParameterResolver`, `CorePK`, `PKParams`,
  `PKParameters`.
- Tests (`app/src/test/.../pk/cpa/CpaSimulatorTest.kt`): single 50 mg dose analytic check vs closed-form
  Bateman (tolerance 1e-9 relative); t½ check (concentration ratio at t and t+43.9 h in terminal phase ≈ 0.5);
  steady-state accumulation for 12.5 mg q24h after 20 days within 1 % of analytic
  `F·D/(Vd)·ka/(ka−ke)·(e^{−ke t}/(1−e^{−ke τ}) − e^{−ka t}/(1−e^{−ka τ}))`; linearity (2× dose → 2× conc);
  zero before first dose; non-CPA anti-androgen and missing-type events excluded; cancellationCheck invoked.

### Slice B — Setting (default off, not backed up)

- `UserSettings.showCpaCurve: Boolean = false`; `SHOW_CPA_CURVE_KEY = booleanPreferencesKey("show_cpa_curve")`;
  `SettingsStore.updateShowCpaCurve(enabled)`; update every `SettingsStore` fake/test implementation.
- `replaceSettings` must NOT write or remove this key. Backup codec unchanged — verify existing
  `EvoluneBackupCodecTest` golden tests still pass unmodified.
- Settings UI: a switch row in the existing flattened Settings screen, in the display/appearance area,
  title「显示 CPA 估算曲线」, subtitle「在首页浓度图叠加醋酸环丙孕酮估算浓度（ng/mL，右侧坐标轴）。模型估算，非实测；单次服药峰值可能被低估。」
  Add both `values/strings.xml` and every other locale folder that exists (keep resource parity — check
  existing parity tests). Row must meet 48dp touch target and use `Role.Switch` / `toggleable` semantics.

### Slice C — Orchestration

- In the ViewModel that owns `pkState` (find where `DefaultPkSimulationCalculator.calculate` is invoked for
  Home), add a separate `cpaState: StateFlow<CpaSeries?>`: computed on `Dispatchers.Default` from the same
  dose events / enabled plans / body weight / currentTimeH inputs **only when `showCpaCurve` is true**;
  `null` when off or when no CPA events/plans exist. Cancel/restart with the same triggers as the E2
  simulation; cancellation must be cooperative (pass `ensureActive` as cancellationCheck).
- Do not add fields to `PKState`, do not change `PkSimulationInput`, `WearAppDataLayer` or widget code.
- JVM test: toggle off → CPA calculator never invoked and `cpaState == null`; toggle on with CPA events →
  non-null series on the same grid as E2; E2 `PKState` identical with toggle on vs off.

### Slice D — Chart

- `ConcentrationChart` gets an optional `cpaSeries: CpaSeries? = null` parameter. When null, drawing is
  identical to today (existing chart tests must pass unmodified).
- When non-null: draw the CPA line in `MaterialTheme.colorScheme.secondary` (distinct from E2 primary /
  baseline / GAHT band), with its own right-side Y axis scaled from the visible-window CPA max (reuse
  `calculateVisibleWindowYScale` logic on the CPA series alone) and labelled "ng/mL"; E2 axis labelled "pg/mL".
  Add a compact legend row under the chart title in `HomeScreen.ChartCard`: E2 (pg/mL) / CPA 估算 (ng/mL).
  Touch inspection shows both values at the selected time.
- `HomeScreen` passes `cpaState` into `ChartCard` → `ConcentrationChart`.
- Accessibility: legend readable as text; chart contentDescription mentions CPA only when shown.

### Slice E — Verification & docs

- `./gradlew test`, `:app:assembleDebug`, `:wear:assembleDebug` all green; list any test you had to change and why
  (changes to existing E2/PK golden tests are **not** allowed).
- Add `docs/evolune/v1.10/V110_IMPLEMENTATION_REPORT.md`: files changed, test counts, deviations from this plan.
- Do not bump version, tag, release, push, or edit ROADMAP/CURRENT_STATUS (owner does that at release time).

## 4. Out of scope (explicit)

Two-compartment CPA model; CPA in Widgets/Wear/Retrospective PK/Insights; CPA target ranges or dose advice;
MPA/bicalutamide/spironolactone curves; Room/backup/Portable format changes; version bump and release.
