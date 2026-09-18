# Evolune v1.7.2 — Implementation Plan

Status: Phase 0 CLOSED / CONTRACT FROZEN; Slice A CLOSED / FROZEN (incl. the evidence
encoding correction 5acddc2); Slice B IMPLEMENTED / REVIEW PENDING; Slice C NOT STARTED.
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

## Slice B — App Material theme preset support — IMPLEMENTED / REVIEW PENDING

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

## Slice C — Settings flattening + Goal B settings UI

Goal: one flat Settings screen per contract §5/§6, plus the 配色 selector UI.

- NEW section composables (settings package): `SettingsBasicDataSection`,
  `SettingsAppearanceSection`, `SettingsSyncBackupSection`, `SettingsUpdateSection`;
  navigation rows for 指南/隐私与权限/功能教程/关于 stay in `SettingsScreen`.
- Reuse existing row/control composables and dialog state; hoist all state as parameters;
  reuse existing callbacks (SettingsViewModel/HRTViewModel/BackupRestoreViewModel);
  Health Connect consent/permission flows unchanged; import/export dialogs unchanged.
- 配色 UI (appearance section): source selector (跟随壁纸 / 预设配色) + preset palette
  selector using the 8 presets, names from `widget_config_palette_*`, widget-style swatch
  tiles, selected-state border/check + semantics, disabled (non-interactive, dimmed) while
  DYNAMIC, live apply, persists.
- Migrated legacy UX (P2-1): when the effective state is `LEGACY_BUILTIN`, the section shows a
  truthful compatibility/current theme row (e.g. "当前主题：内置主题（兼容保留）") and NONE of
  the 8 tiles is marked selected; the first active tile selection migrates to normal PRESET
  persistence. MONET_TEAL is never shown as selected unless it actually is selected.
- Route cleanup (pending reachability proof): remove BASIC_DATA / APPEARANCE_FORMAT / UPDATE /
  SYNC_AND_BACKUP / DATA_IMPORT_EXPORT / HEALTH_CONNECT_SYNC destinations + route constants +
  top-bar title overrides; keep GOOGLE_DRIVE / ONBOARDING / DISCLOSURES / FEATURE_TUTORIAL /
  ABOUT; update `isSettingsSubroute`; delete now-unreachable screen files only after
  repository-wide reference proof (otherwise DEPRECATE + report).
- Tests: flattened sections visible; controls mutate the SAME preference owners; protections
  preserved; font-scale/scroll; kept-route back behavior; selector interaction + persistence +
  disabled semantics; updated navigation tests (SyncAndBackupNavigationTest,
  HealthConnectSyncScreenTest, SettingsCategoryScreenTest, FeatureTutorialNavigationTest).
- Acceptance: no workflow requires a removed screen; physical-device walkthrough of every
  section.

## Slice D — History copy removal

Goal: remove only `根据记录上下文推断匹配` from the medication-record card.

- `HistoryPresentation.matched(...)`: inferred case emits `noteRes = null`; classification
  fields untouched.
- Update tests: `HistoryPresentationTest` (56/81), `HistoryQuickRecordInferredTest`
  (73/98/102), `HistoryScreenTest` (213/235/256 display asserts → absence), wording test if
  the resource is deleted.
- Delete `history_note_inferred_match` from both locale files only if a repository-wide grep
  shows zero remaining references after the update; otherwise keep and report.
- Guard: inferred-match classification tests must still pass; path-scoped diff shows only the
  presentation note emission + tests (+ optional resource deletion).
- Acceptance: sentence absent on device; inferred matching behavior identical.

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
