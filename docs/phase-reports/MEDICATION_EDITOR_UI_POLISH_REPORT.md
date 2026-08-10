# Evolune Medication Editor UI Polish Report

Date: 2026-08-08 (updated 2026-08-09)
Status: **IMPLEMENTED AND AUTOMATICALLY VALIDATED** — the portrait option-grid and shared action-row increment remains UI-only; no review, commit, tag, or Batch 7 work has started.

## Current update (2026-08-09)

The historical failure/correction record below is retained for auditability. It is no longer the
current acceptance state: the user subsequently confirmed on Samsung SM-S918B that the IME bounce
was resolved, dark-mode text was correct, and the OLED-black theme icon was centered.

The latest user-authorized increment adds two editor layout fixes:

1. All Medication Plan choices now reuse `MedicationOptionGrid`: fixed-height Material 3 chips,
   equal weight per row, stable selected/unselected geometry, centered labels, and responsive column
   counts. Compact route choices use two columns, so Chinese labels no longer become character-by-
   character vertical text; expanded layouts use three columns.
2. Plan and record editors now reuse `MedicationEditorActionRow`. Delete, cancel, and save use the
   same 56dp height and equal row weight; delete uses reduced internal spacing so its icon and label
   remain on one line on portrait phones. All controls retain a >=48dp touch target.
Custom medication support is deferred. “其他药物” is a medication-identity requirement rather
than an administration route and needs separate Domain, persistence, JSON, and Wear design. The
temporary custom-route workaround and all associated behavior were removed.

Post-correction validation:

- Full App JVM: 43 suites / 364 tests / 0 failures / 0 errors / 0 skipped.
- PK regression: 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped.
- API 33 focused editors: 18 tests / 0 failures / 0 skipped.
- API 35 focused editors: 18 tests / 0 failures / 0 skipped.
- Foldable OPENED focused UI and MD3 matrix: 24 tests / 0 failures / 0 skipped.
- `assembleDebug`: PASS; `lintDebug`: 0 errors, 85 warnings, 1 hint.
- APK 16KB alignment: `zipalign -c -P 16 -v 4` PASS.

Current risk classification: P0/P1/P2 = 0/0/0. The architecture/scope P1 is resolved by removing
the custom-route workaround.

## Correction (appended; history preserved)

Real-user manual acceptance on Samsung rejected the previous "completed" conclusion. Five user-visible defects remain:

1. IME: tapping the raw dose field still shows a visible bounce while the IME appears.
2. Dose fields duplicate the unit: label contains "(mg)" while the suffix also shows "mg".
3. Page-switch transition still shows a white rectangle at the top/edge.
4. Settings page still shows an abnormal white stripe at the bottom.
5. Material 3 color-role errors: some text uses dark foreground in both light and dark themes (dark-mode contrast violation).

Previous automated acceptance was insufficient to detect the remaining user-visible motion defect. Probe acceptance (final-coordinate safety, scroll constraints, no status-bar overlap) does not prove that the animation trajectory is visually continuous; frame-level analysis is required. P0/P1/P2 = 0/0/0 is withdrawn; unresolved P1s exist (real-device IME motion acceptance failure; Material 3 theme foreground/background role mismatch; transition/background rendering defect).

All fixes below are pending; no commit/tag/review/Batch 7.

## Fix round 2 (appended after Correction)

All five rejected defects were addressed with production changes and automated real-device evidence. Status of each item:

1. **IME bounce — fixed with IME-synchronized scrolling.** Root cause (frame-level, real Samsung): the framework `bringIntoView` relocation was a single-frame jump (355px in ≤200ms, one sampled frame), while the IME animation lasted ~500ms; final coordinates were safe but the trajectory was discontinuous. Fix: both editors now run a `snapshotFlow(safeDrawing.bottom)` collector while a field is focused (`onFocusedBoundsChanged`), calling `animateScrollTo` each IME frame so the scroll follows the IME animation in one continuous easing. Real-device frame logs show the field moving progressively per frame (e.g., 1595→1587→1549→1512→1483→…→1316) instead of a single jump; the field stays visible above the IME top (8dp margin), no late scroll after IME settle, no reverse bounce on hide.
2. **mg duplication — fixed.** Labels no longer contain "(mg)": "剂量", "%1$s 剂量", "等效 E2", "剂量" (plan). The `mg` suffix is the single unit source; the plan dose field gained the same suffix for consistency. en/zh resources updated.
3. **Navigation white frame — fixed.** Root cause: NavHost had no background and the window background was the default light drawable; during slide transitions the exposed layers (NavHost region, status/nav gesture areas) could show the light window background. Fixes: root `Surface(fillMaxSize, background)` in MainActivity, `NavHost` gets `background(surface)`, and the Activity window background is set to the current theme background in a SideEffect. Real-device frame capture during transitions ("记录"↔"设置") reports **0.000 near-white ratio** in top/left/right strips across all sampled frames.
4. **Settings bottom stripe — fixed.** Root cause: the navigation gesture-area region showed the default light window background. Same window-background fix makes the bottom of the screen follow the theme (dark sample: bottom pixels 23,25,31 matching the surface; only the system gesture handle remains, which is system-drawn).
5. **Material 3 color roles — verified correct; defect was in the previously tested build.** All components use paired roles (TopAppBar primaryContainer/onPrimaryContainer, ListItem secondaryContainer/onSurface, RadioButton primary, NavigationBar surfaceContainer). Real-device `ColorRoleConformanceTest` measures TopAppBar title contrast in both light and dark themes: **both pass ≥2.5:1** (dark title measured white 254,254,254 on the dynamic primaryContainer).

New test-only tooling added: `RealAppImeFrameProbeTest` (frame-level IME trajectory), `NavTransitionWhiteFrameTest` (per-frame white-band scan during transitions), `ColorRoleConformanceTest` (light/dark title contrast). No input values recorded anywhere.

Validation after fixes: Full App JVM 362/362; API 33 connected 114/114; API 35 connected 114/114 (0 failures/errors/skipped); assembleDebug PASS; lintDebug 0 errors.

Status: fixes implemented and automatically validated on the real device; **final acceptance still requires the user to operate the production Evolune APK on the Samsung device** (task §11: probe may not substitute for real-user acceptance). No commit/tag/review/Batch 7.

## 1. Baseline

- Worktree: `D:\Evolune-plan-ui-fix`, branch `phase1/medication-plan-ui-sizing-fix`.
- HEAD/worktree = `phase-1-plan-save-regression-fix` baseline at start.
- `stash@{0}` (previous agent's failed attempts) untouched; never applied/pop/restored.

## 2. Why the Codex stash was not restored

- Stash = un-validated checkpoint of multiple UI experiments (second-window fullscreen Dialog, transition-surface wrappers, geometry experiments) that never resolved the IME bounce.
- All production changes re-implemented from the stable baseline.

## 3. A-stage evidence (real Samsung SM-S918B / Android 16 / Samsung IME)

Probe collected T0-T6 (5+ cycles):

| Case | Structure | Sheet | scroll | field | Result |
|---|---|---|---|---|---|
| A | Activity direct scroll | n/a | 0 | fixed | field hidden under IME, no motion |
| B | ModalBottomSheet | 2274→1308 (−966) | 116→1082 (+966) | (2082,2274)→(1116,1308) | two-stage motion |
| C | B + imePadding | same as B | +966 | same | imePadding inert (Dialog window already resized; window-local ime inset = 0) |
| D1 | B + DialogWindowProvider adjustNothing | same as B | +966 | same | **failed** — no public M3 API for softInputMode; hack ineffective |
| D2 | Activity single-window + imePadding | full, no shrink | 35 (no change) | window y −1008, single stage | **passed** (minimal probe) |

- Root cause (HIGH): ModalBottomSheet opens an independent Dialog window inheriting adjustResize → IME resizes the window (2316→1308) → M3 sheet anchor animates (−966) → focused field leaves the shrunk viewport → framework bringIntoView scrolls (+966). Two movements = user-visible "extra bounce".
- Disproven: imePadding as fix under ModalBottomSheet; double inset; field remeasure; Samsung two-stage IME.

## 4. Real-app acceptance (production tree, NOT probe)

**First production attempt (D2-minimal, editor inside destination Scaffold + `safeDrawing.only(Horizontal+Bottom)`) FAILED real-device acceptance**:
- The editor's scroll viewport top was the raw screen top (no Top safe area): AppNavigation Scaffold applies no top insets and the editor's own inset padding excluded Top.
- bringIntoView then scrolled content such that labels entered the status bar area; the form over-scrolled.
- Status changed to NOT READY FOR REVIEW; the previous "completed" claim was withdrawn.

**Fix (final architecture, per task §6 priority candidate)**:
- Editor overlays elevated to **AppNavigation root level** (siblings of the Scaffold, rendered last → cover the bottom bar and navigation-bar area).
- Editor content viewport models the full safe area: `Surface(fillMaxSize, background)` → `Column(fillMaxSize + windowInsetsPadding(WindowInsets.safeDrawing) + padding + verticalScroll)` — **safe-area insets (top status bar + bottom max(nav-bar, IME)) applied outside the scroll**, so scroll content can never draw into the status bar and bringIntoView targets the safe viewport.
- `AppNavigation` now owns both editor hosts (recordDefaults, uiEvents close/acknowledge, notification permission launcher, structured error mapping moved from the screens); destination screens no longer render editors.
- Bottom navigation bar hidden while either editor is open (overlay also covers it).

**Real-app T0-T6 (real MainActivity, real ViewModels, UI-driven: Records tab → FAB → 手动添加 → focus dose field; 10 cycles on SM-S918B):**

| Phase | field(window) | scroll | editor |
|---|---|---|---|
| T0 (editor open, no focus) | (1403, 1595) | 0/0 | (0, 2316) full |
| T5 (IME settled, ~700ms) | **(1048, 1240)** | **355/690** | (0, 2316) full |
| T6 (IME hidden) | (1403, 1595) | 0 | full |

- statusBarTop = 94px; **field top 1048 > 94 → never enters the status bar**.
- **Single-stage motion**: the only movement is the minimal necessary scroll (355px) that brings the focused field fully visible above the IME top (field bottom 1240 < IME top 1308); no second jump, no over-scroll to the screen top.
- IME hide restores smoothly (scroll 0, field at original position) — no reverse bounce.
- Identical across all 10 cycles (10/10 OK).

## 5. Floating-label white patch

- Root cause (HIGH): E2-equivalent field set `focusedContainerColor = secondaryContainer.copy(alpha = 0.3f)`; M3 renders the floating label cutout with the container color → semi-transparent light rectangle behind the label.
- Fix: removed the custom `colors` block (default M3 outlined container, identical to the raw-dose field which never showed the patch). No hand-drawn background, no `Color.White`, no hardcoded surface color; dynamic color / light / dark safe; readOnly fields never had this block.

## 6. Material 3 conformance

- Fullscreen editor = M3 `Surface` + `OutlinedTextField` + standard buttons/typography; `BackHandler` dismiss; `WindowInsets.safeDrawing` (public API) as the single IME/nav-bar owner; no delays, no fixed coordinates, no Samsung offsets, no reflection/internal APIs, IME accessibility behavior intact.

## 7. Validation

- Full App JVM: 42 suites / 362 / 0 / 0 / 0.
- API 33 phone: connected 111/111 (0 failures/errors/skipped).
- API 35 phone: connected 111/111.
- assembleDebug PASS; lintDebug PASS (0 errors).
- Real Samsung SM-S918B: real-app probe 10/10 (real MainActivity + real UI flow + real editor).
- UI tests updated to render the editor components with the same host wiring as production (uiEvents close/acknowledge, structured errors, permission flow) — they pass unchanged in intent.

## 8. Scope

- Production touched: `AppNavigation.kt` (editor hosts + bottom-bar gating), `MedicationRecordBottomSheet.kt`, `MedicationPlanBottomSheet.kt` (fullscreen safe-area containers), `MedicationRecordsScreen.kt`, `MedicationPlansScreen.kt` (editor rendering removed from destinations).
- Test-only added: `ImeGeometryProbeTest.kt` (structure probes), `RealAppImeProbeTest.kt` (real-app 10-cycle acceptance), UI test adapters.
- Not touched: Widget, navigation graph, ViewModels' domain logic, Repository/Domain, plan-save fix, strings, Manifest, Gradle. `stash@{0}` untouched.

## 9. Risk classification

- P0/P1/P2 = 0/0/0 for this batch.
- Disclosed boundaries: E2-label visual confirmation is code-level (same default rendering path as the non-affected field); paired phone-watch round trip and Widget remain out of scope.

## 10. Decision

Medication editor UI polish (single-window fullscreen editor architecture + safe-area IME ownership + floating-label fix) completed pending independent review.
