# Evolune v1.7.2 — Implementation Plan

Status: Phase 0 CLOSED / CONTRACT FROZEN; Slice A CLOSED / FROZEN (incl. the evidence
encoding correction 5acddc2); Slice B CLOSED / FROZEN (independent review APPROVE V1.7.2
SLICE B @ e01a55e); Slice C CLOSED / FROZEN @ 69f859d; Slice D CLOSED / FROZEN @
50b20e8; Slice E-R1 CLOSED / FROZEN @ 61cd190; Slice E ACCEPTANCE COMPLETE / REVIEW
PENDING; Slice F NOT STARTED.
Baseline: v1.7.1 @ `746fc0a…`. Contract: `V172_CONTRACT.md`; inventory:
`V172_INVENTORY.md`. One commit per slice; each slice is independently reviewable and
revertable. No version bump in the implementation phase.

---

## Slice A — Shared palette catalog + widget golden protection — CLOSED / FROZEN

Goal: create ONE token authority for the 8 presets without changing any behavior or persisted
identity.

Actual implementation (commit `refactor(theme): centralize palette definitions`):
- NEW `app/src/main/java/io/github/yingqiu0871/evolune/theme/palette/` (pure Kotlin):
  `PresetPalette` (8 ids), `PaletteSeed` (11 ARGB Longs), `PaletteCatalog`,
  `LegacyBuiltinTheme` (35 effective roles x2 + exact AMOLED overrides).
- Widget delegation: `WidgetAppearance.kt` maps `WidgetColorScheme -> PresetPalette` and builds
  its `WidgetPalette` from `PaletteCatalog`; the MONET token table was removed from the widget
  file (0 seed literals remain). `WidgetAppearanceKeys` (file/keys) and
  `WidgetColorScheme.fromStored` were extracted for testability; behavior identical.
- NEW golden tests: `WidgetPaletteIdentityGoldenTest` (names/order, file/key formats,
  fallback), `WidgetPaletteTokenGoldenTest` (8 presets x light/dark x 11 fields = 176 exact
  ARGB outputs), `PaletteCatalogGoldenTest` (88 seeds), `PaletteArchitectureGuardTest`
  (pure-Kotlin + one-way dependency + no widget seed remnants).
- Legacy snapshot: `LegacyBuiltinSnapshotTest` (3 tests, 35 roles x2 + AMOLED).
- Equivalence verdict: `LegacyVsMonetTealEquivalenceTest` — DISPROVED exact equality in both
  modes (light 11/20 mismatches, dark 14/20; dark tertiary 0xFFAFC9E7 vs 0xFFB2C8E8). The
  operative migration path is therefore legacy BUILTIN -> `LEGACY_BUILTIN`, never MONET_TEAL.
- Existing widget tests pass untouched (`WidgetAppearanceTest`, `WidgetHostContractTest`, and
  the instrumentation `WidgetRemoteViewsTest` 12/12); full app JVM 143 suites / 1360 tests / 0
  failures.
- Pre/post golden equality: identity 5/5 and tokens 2/2 pass with IDENTICAL expectations
  before and after the extraction (characterization-first order, see evidence
  `docs/evolune/v1.7.2/evidence/slice-a/`).
- Bounded boundary duplication (documented): the 8 legacy teal literals also in
  `ui/theme/Color.kt` remain until Slice B connects `LEGACY_BUILTIN` to the theme wiring; this
  is the only allowed duplication and it is not a MONET authority.

## Slice B — App Material theme preset support — CLOSED / FROZEN

Goal: `ThemeColorSource {DYNAMIC, PRESET}` + `PresetPalette` selection driving the App theme,
with exact legacy preservation and full backup round-trip.

Actual implementation (commit `feat(theme): add preset theme state and backup v2`):
- NEW `data/ThemeState.kt`: `ThemeColorSource`, `ThemePresetSelection` (Preset/LegacyBuiltin,
  persisted ids = palette names / `LEGACY_BUILTIN`), pure `deriveThemeState` runtime matrix.
- `SettingsDataStore`: keys `theme_color_source` + `theme_preset_id`, atomic writers keeping
  the legacy `color_theme` projection in sync, extended `replaceSettings`; `SettingsViewModel`
  gained `updateThemeColorSource`/`updateThemePreset` (old `updateColorTheme` maps to both).
- `ui/theme/PresetColorSchemes.kt`: preset light/dark builders from the shared catalog +
  legacy built-in builders from `LegacyBuiltinTheme`; `EvoluneTheme(themeMode, colorTheme,
  colorSource, preset, content)` with the legacy `colorTheme` param retained as a pre-Slice-C
  compatibility projection; old inline `lightScheme`/`darkScheme` + the 70 standard Color.kt
  constants removed after a pre-deletion role-by-role continuity run.
- Backup: schema v2 constants + strict `SETTINGS_FIELDS_V2`, v1 frozen parser, dual-version
  reading with envelope/payload equality, canonical v2 writer (always emits both keys; legacy
  inputs canonicalized), B2 `toUserSettings`/`toBackupSettings` derive/write the canonical
  identity + coherent projection.
- Decision records: v1 extra-key classification stays MALFORMED_PAYLOAD (frozen); v2
  settings violations are INVALID_PAYLOAD; no payload-internal cross-field rule (new fields
  authoritative, projection repaired on restore).
- Verification: full app JVM 147 suites / 1387 tests / 0 failures; device 14/14; debug build
  38/38 + AVD smoke (DYNAMIC default + legacy BUILTIN path live). Evidence:
  `docs/evolune/v1.7.2/evidence/slice-b/`.

- `SettingsDataStore.kt` (or adjacent): `ThemeColorSource` enum; new keys
  `theme_color_source`, `theme_preset_id`; read precedence per contract §10/§11 (legacy
  `color_theme` fallback mapping: DYNAMIC→DYNAMIC, BUILTIN→PRESET+`LEGACY_BUILTIN` unless
  slice A proved exact MONET_TEAL equality); writers `updateThemeColorSource`/
  `updatePresetPalette` that also keep legacy `color_theme` in sync; `replaceSettings`
  derives/writes all theme keys atomically.
- `LEGACY_BUILTIN` compatibility identity (unless slice A proved exact equality): defined in
  the shared authority / `ui/theme`, reproducing the v1.7.1 built-in light+dark schemes
  exactly; never listed among the 8 user-selectable presets; never alters Widget tokens.
- `ui/theme/`: preset light/dark scheme builders from `PaletteSeed` per contract §13
  (contrastOn helper + documented blend); `EvoluneTheme` selects: DYNAMIC → dynamic schemes
  (unchanged), PRESET → preset schemes, `LEGACY_BUILTIN` → exact v1.7.1 schemes; AMOLED
  post-processing unchanged.
- Backup codec (Decision B, STRICT v2 schema, contract §12): `PAYLOAD_SCHEMA_VERSION_LEGACY = 1` /
  `PAYLOAD_SCHEMA_VERSION = 2`; encoder writes v2; decoder accepts v1 (byte/semantically
  FROZEN exact parsing) and v2 (complete exact `V2_SETTINGS_FIELDS` key-set validation —
  unknown extra key or missing required key → `INVALID_PAYLOAD`; no allowed-set weakening);
  both new keys are always serialized (DYNAMIC writes `themePresetId: null` via the codec's
  standard present-key/JSON-null convention); plans/slots/events serialization byte-identical;
  older readers reject v2 deterministically (`UNSUPPORTED_PAYLOAD_VERSION`).
- Tests (mandatory): upgrade default (no keys → DYNAMIC); legacy BUILTIN → PRESET +
  `LEGACY_BUILTIN` with EXACT effective colors (light+dark snapshot comparison); DYNAMIC
  behavior unchanged; live switch; persistence across recreation; invalid/unknown stored
  settings values fall back per the §10/§11 ladder without crash; contrast golden for
  all 8 palettes × light/dark; backup/codec suite v1: exact legacy field set accepted, v2 key
  added to a v1 payload rejected, v1 DYNAMIC/BUILTIN derivation; backup/codec suite v2: exact
  field set required, both keys always serialized, DYNAMIC → null, every `MONET_*` preset and
  `LEGACY_BUILTIN` round-trip, missing source rejected, missing preset key rejected, unknown
  source/preset rejected, DYNAMIC+non-null and PRESET+null rejected, extra settings field
  rejected, invalid backup → `INVALID_PAYLOAD` with ZERO Settings/Widget mutation, old-reader
  v2 rejection asserted; existing `EvoluneBackupCodecTest` / `BackupRestoreCoordinatorTest`
  updated for v2 where they pin payload versions.
- Acceptance: theme switches live on device; existing user default unchanged; legacy BUILTIN
  user sees zero color change; backup files round-trip the exact preset identity; widget
  tests untouched.

## Slice C — Settings flattening + Goal B settings UI — IMPLEMENTED / REVIEW PENDING

Goal: one flat Settings screen per contract §5/§6, plus the 配色 selector UI.

Actual implementation (`feat(settings): flatten settings and add palette selector`):
- `ui/screens/settings/` sections: SettingsSectionHeader, SettingsBasicDataSection,
  SettingsAppearanceSection, SettingsColorSchemeSection, SettingsImportExportBlock,
  SettingsHealthConnectSection, SettingsSyncBackupSection, SettingsUpdateSection,
  SettingsNavigationRowsSection; SettingsScreen is the only state collection point.
- 配色 UI: source rows 跟随壁纸/预设配色 + exactly the 8 PresetPalette tiles (labels from
  `widget_config_palette_*`), swatch preview from PaletteCatalog seeds, selected state via
  selectable semantics + border + Check (never hue-only). Tiles are directly tappable while
  DYNAMIC (frozen contract §10); tapping the PRESET source row while DYNAMIC enters PRESET
  atomically with MONET_TEAL (the contract §11 terminal ladder identity); tapping it while
  already PRESET is a no-op guard (never re-defaults an existing selection). Never writes
  PRESET+null.
- LEGACY_BUILTIN: truthful compatibility row 当前主题：内置主题（兼容保留）, no tile selected,
  first tile tap migrates monotonically.
- Route cleanup: all six removable routes + their six now-dead screens removed after the
  repository-wide reachability proof (zero callers, no deep links/auto routes/tutorial refs);
  top-bar overrides + isSettingsSubroute updated; GOOGLE_DRIVE / ONBOARDING / DISCLOSURES /
  FEATURE_TUTORIAL / ABOUT kept.
- Tests: SettingsCategoryScreenTest 8 (structure/owners/large font), SettingsPaletteSelectorTest
  4 (state matrix + inventory), SettingsThemeIndependenceTest 3 (real DataStore), updated
  SyncAndBackupNavigationTest / SyncAndBackupScreenTest / HealthConnectSyncScreenTest /
  DataImportExportScreenTest / AuthorizationGuidanceTest / FeatureTutorialNavigationTest /
  ColorRoleConformanceTest; SliceCSettingsSmokeTest for AVD acceptance.
- Verification: full app JVM 147 suites/1387/0; focused device 39/39; AVD smoke 1/1 + 6
  screenshots; evidence `docs/evolune/v1.7.2/evidence/slice-c/`.

## Slice D — History copy removal — IMPLEMENTED / REVIEW PENDING

Goal: remove only `根据记录上下文推断匹配` from the medication-record card.

Actual implementation (`fix(history): remove inferred-match display note`):
- `HistoryPresentation.matched(...)`: `noteRes = null` unconditionally; `isInferredMatch` /
  `matchProvenance` usage unchanged (classification preserved and still asserted).
- `history_note_inferred_match` deleted from both locale authorities after the repository
  scan showed zero live references; the wording guard now asserts the key does not exist.
- Tests updated: HistoryPresentationTest (classification kept, note null), 
  HistoryQuickRecordInferredTest (production quick-record shape, note null), 
  HistoryScreenTest (record present + removed sentence absent, real pipeline screenshot),
  HistoryPresentationWordingTest (absence guard).
- Verification: History-focused JVM 37 suites/449/0; full app JVM 147 suites/1387/0;
  device HistoryScreenTest 21/21; debug build 38/38; evidence
  `docs/evolune/v1.7.2/evidence/slice-d/`.

## Slice E — Regression + real-device acceptance

- Fresh focused JVM (settings/theme/history) and full `:app:testDebugUnitTest --rerun-tasks`;
  affected instrumentation suites; debug build.
- Physical-device acceptance: flattened Settings walkthrough, palette switching
  (DYNAMIC/PRESET + live apply + relaunch persistence), History card check; screenshots to a
  new evidence root (e.g. `docs/evolune/v1.7.2/evidence/`).
- Report exact counts; no full-suite claim beyond what was actually run.

## Slice F — Docs / status + packaging preparation

- Update `docs/evolune/ROADMAP.md`, `docs/evolune/CURRENT_STATUS.md`, `TODO.MD` to
  IMPLEMENTED / REVIEW PENDING; keep v1.7.1 RELEASED history intact.
- Prepare (but do not execute) the packaging checklist: version bump 1.7.2 / ordinal
  `1_070_200` / Phone `101_070_200` / Wear `1_101_070_200`, signed release builds, evidence,
  MANIFEST — executed only in the separate future packaging task.

---

## Ordering rationale

A before B/C: the shared catalog and its golden tests must exist before either consumer
changes, otherwise widget identity could silently shift. B before C: the theme model must be
persisted/testable before the Settings UI that edits it. D is independent and can land any
time after A; it is kept separate to keep the copy-only delta auditable. E after A–D; F last.

## Commit plan (one per slice)

- `refactor: extract shared preset palette catalog` (A)
- `feat: support preset color palettes in app theme` (B)
- `feat: flatten settings and add color scheme controls` (C)
- `fix: remove inferred-match note from history card` (D)
- `test: regress and accept v1.7.2` (E, evidence)
- `docs(v1.7.2): record implemented status` (F)

Each commit: no version bump, no tag, no release, worktree clean; evidence/packet per project
convention when the slice is reviewed.
