# Evolune v1.7.2 — Implementation Plan (Phase 0 draft)

Status: IMPLEMENTATION NOT STARTED. Baseline: v1.7.1 @ `746fc0a…`. Contract:
`V172_CONTRACT.md`; inventory: `V172_INVENTORY.md`. One commit per slice; each slice is
independently reviewable and revertable. No version bump in the implementation phase.

---

## Slice A — Shared palette catalog + widget golden protection

Goal: create ONE token authority for the 8 presets without changing any behavior or persisted
identity.

- NEW `app/src/main/java/io/github/yingqiu0871/evolune/theme/palette/` (pure Kotlin):
  `PresetPalette` (8 ids), `PaletteSeed` (11 ARGB Longs), `PaletteCatalog`.
- Widget adapter: `WidgetAppearance.kt` keeps `WidgetColorScheme` (9 values), `PRESETS` and
  `preset(...)` delegate to `PaletteCatalog`; every resolved ARGB output stays byte-identical.
- NEW golden test (widget package tests): the 9 persisted id strings and the 8 preset hex
  seed tables are pinned; SharedPreferences key names (`widget_<id>_theme|color|opacity|style`,
  file `widget_appearance`) pinned.
- NEW legacy snapshot + equivalence task (P2-1): snapshot the EXACT v1.7.1 App built-in
  light/dark scheme values (`ui/theme/Theme.kt` lightScheme/darkScheme resolved from
  `ui/theme/Color.kt`) into a test; then PROVE or DISPROVE exact equality against the
  preset-derived MONET_TEAL scheme (every role actually used, light AND dark). The recorded
  result decides whether BUILTIN → MONET_TEAL auto-mapping is allowed; the
  `LEGACY_BUILTIN` compatibility identity in slice B is required unless equality is proven.
- Existing tests must pass untouched: `WidgetAppearanceTest`, `WidgetRemoteViewsTest`,
  `WidgetHostContractTest`, `ColorRoleConformanceTest`.
- Dependency guard: `palette` package must not import Compose/Android; widget may import
  palette; palette imports nothing from widget; `LEGACY_BUILTIN` is App-side only and never
  appears in `WidgetColorScheme`.
- Acceptance: path-scoped audit shows no UI/output change; all widget tests green; new golden
  tests green; zero persisted-id delta; equivalence result recorded for slice B.

## Slice B — App Material theme preset support

Goal: `ThemeColorSource {DYNAMIC, PRESET}` + `PresetPalette` selection driving the App theme,
with exact legacy preservation and full backup round-trip.

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
- Backup codec (Decision B, contract §12): `PAYLOAD_SCHEMA_VERSION_LEGACY = 1` /
  `PAYLOAD_SCHEMA_VERSION = 2`; encoder writes v2; decoder accepts v1 (exact parsing
  unchanged) and v2 (settings object allowed-set check; optional `themeColorSource` /
  `themePresetId` validated when present); plans/slots/events serialization byte-identical;
  older readers reject v2 deterministically (`UNSUPPORTED_PAYLOAD_VERSION`).
- Tests (mandatory): upgrade default (no keys → DYNAMIC); legacy BUILTIN → PRESET +
  `LEGACY_BUILTIN` with EXACT effective colors (light+dark snapshot comparison); DYNAMIC
  behavior unchanged; live switch; persistence across recreation; invalid/unknown stored
  values fall back per the ladder without crash; contrast golden for all 8 palettes ×
  light/dark; backup/codec suite: v1 payload → deterministic restore (v1 DYNAMIC / v1
  BUILTIN), v2 round-trip for DYNAMIC + every exposed preset + `LEGACY_BUILTIN`, missing
  optional fields, unknown preset id, malformed source, unknown payload version rejection;
  restore never mutates widget appearance; existing `EvoluneBackupCodecTest` /
  `BackupRestoreCoordinatorTest` updated for v2 where they pin payload versions.
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
