# Evolune v1.7.2 — Contract (Phase 0 draft, read-only audit complete)

Status: CONTRACT DRAFTED / IMPLEMENTATION NOT STARTED. Baseline: v1.7.1 @
`746fc0a970bf0dfb3735c9225ce662e9511514ae`. Evidence for every statement below lives in
`V172_INVENTORY.md` (file:line references) or is cited inline. No product code may change
before this contract is reviewed and approved.

---

## 1. Release goal

v1.7.2 delivers exactly three product goals:

- **Goal A — Settings flattening:** expose the common controls that currently live one or two
  navigation levels deep directly on the main Settings screen, under meaningful section
  headers, without duplicating any preference authority.
- **Goal B — App preset color palettes:** let the user choose between system/wallpaper dynamic
  color and a preset palette; the preset family is the existing phone-widget palette family
  (8 `MONET_*` presets), shared as one token authority between the App Material theme and the
  phone widget rendering.
- **Goal C — History copy removal:** remove only the displayed sentence
  `根据记录上下文推断匹配` from the medication-record card; matching behavior is unchanged.

## 2. Non-goals (hard freeze)

v1.7.2 MUST NOT absorb: dead-code/legacy cleanup (DoseEventRepository, WidgetUtils, Mahiro),
Wear architecture or palette changes, Widget feature changes, matcher/occurrence-generator
changes, PK math/algorithms, retrospective PK changes, backup/import/export semantics, Room
schema/migrations, Health Connect behavior, medication-recording semantics, Timeline behavior,
Insights definitions, calendar geometry redesign, the calendar 48dp accessibility polish, PK
tooltip i18n cleanup, v1.8 work. No "while we are here" changes.

## 3. Released baseline

- v1.7.0 RELEASED; v1.7.1 RELEASED (`v1.7.1` annotated tag → commit `746fc0a…`), frozen and
  never rewritten.
- Development branch for this cycle: `feature/v1.7.2-settings-theme-history`, based exactly on
  `746fc0a…`.
- Version metadata stays `1.7.1 / 101070100 / 1101070100` for the whole implementation phase;
  the 1.7.2 bump belongs to release packaging after implementation approval.

## 4. Settings current-state inventory

See `V172_INVENTORY.md` §1 (full table). Summary of the audited reality:

- Settings is a pure navigation hub of 8 rows (基础数据 / 外观与格式 / 同步与备份 / 更新 /
  指南 / 隐私与权限 / 功能教程 / 关于). All eight labels exist in v1.7.1.
- 同步与备份 is itself a hub whose third level contains import/export actions (destructive
  paths with dialogs), Health Connect controls (permission + consent flows) and Google Drive
  backup/restore (secure multi-step workflow with passphrase/generation/preview).
- Every setting is owned by `SettingsViewModel`/`SettingsDataStore` (`settings` DataStore) or,
  for onboarding/widget surfaces, their own stores; there is no duplicated preference state.

## 5. Settings target structure

One vertically scrollable Settings screen with section headers; common controls inline; the
four content/navigation entries remain rows:

```
设置
├─ 基础数据            → inline: body-weight field (existing control + validation)
├─ 外观与格式          → inline: theme-mode selector, 配色 selector (Goal B), time-format selector
├─ 同步与备份          → inline: import/export action rows (all existing dialogs preserved),
│                        Health Connect controls (switch/status/manage/reauthorize),
│                        Google Drive status + entry row (opens the kept dedicated screen)
├─ 更新                → inline: auto-check switch, check-now + status, current version row
├─ 指南                → navigation row (existing route; content page)
├─ 隐私与权限          → navigation row (existing route; deep-linked)
├─ 功能教程            → navigation row (existing route; auto-start surface)
└─ 关于                → navigation row (existing route; content page)
```

Composable decomposition (names reflect the existing architecture; the four section
composables live in the settings package and receive hoisted state):

```
SettingsScreen(state = SettingsScreenState, on* callbacks)
  SettingsBasicDataSection(userSettings, onBodyWeightChange)
  SettingsAppearanceSection(userSettings, onThemeModeChange, onColorSourceChange,
                            onPresetPaletteChange, onTimeFormatChange)
  SettingsSyncBackupSection(userSettings, healthConnectState, backupRestoreConnected,
                            import/export callbacks + dialog state as today)
  SettingsUpdateSection(autoCheckUpdates, updateCheckResult, on* callbacks)
  SettingsNavigationRowsSection(onOpenGuide, onOpenPrivacy, onOpenFeatureTutorial, onOpenAbout)
```

Rules: reuse the existing ViewModels; collect each flow once (AppNavigation already collects
`userSettings`, HC state and Drive `connected` — the sections must receive them as parameters,
not re-collect); dialogs stay modal; destructive protections stay mandatory; no second
preference owner for any control.

## 6. Route retention / removal policy

| Route | Decision | Justification (audit) |
|---|---|---|
| BASIC_DATA_ROUTE | REMOVE (pending implementation proof) | only entered from Settings hub; content becomes an inline section |
| APPEARANCE_FORMAT_ROUTE | REMOVE (pending proof) | only Settings hub; content inline (+ new Goal B controls) |
| UPDATE_ROUTE | REMOVE (pending proof) | only Settings hub; content inline |
| SYNC_AND_BACKUP_ROUTE | REMOVE (pending proof) | hub row only; content inline |
| DATA_IMPORT_EXPORT_ROUTE | REMOVE (pending proof) | only entered from Sync hub; action rows inline with the same dialogs |
| HEALTH_CONNECT_SYNC_ROUTE | REMOVE (pending proof) | only Sync hub; controls inline; permission/system flows unchanged |
| GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE | KEEP | secure multi-step workflow (passphrase/generation/preview/journal) AND reused by FeatureTutorial step 5 (NAV:1128-1132) |
| ONBOARDING_ROUTE | KEEP | content entry (指南) + first-run gate composition (NAV:247-258) |
| DISCLOSURES_ROUTE | KEEP | deep link from MainActivity intents (MA:72-76), first-run overlay, About reuse |
| FEATURE_TUTORIAL_ROUTE | KEEP | auto-launch start route + entry row |
| ABOUT_ROUTE | KEEP | content page entry (关于) |
| Insights/Retrospective/Timeline routes | UNTOUCHED | not settings; frozen |

Removal is allowed only after implementation proves zero remaining navigation/deep-link/test
references; otherwise the route is DEPRECATED (kept but unreachable) and reported. Top-bar
title overrides (AppNavigation.kt:839-858) and the `isSettingsSubroute` list (805-818) must be
updated consistently with the removals (the sub-route list keeps insights/retrospective/
timeline and the kept settings children).

## 7. Theme current-state inventory

See `V172_INVENTORY.md` §2. Key facts:

- `EvoluneTheme` (ui/theme/Theme.kt:285-315) builds the scheme from
  `ColorTheme.DYNAMIC → dynamicLight/DarkColorScheme(context)` else the built-in teal
  `lightScheme`/`darkScheme` (Color.kt). `ThemeMode {LIGHT, DARK, AMOLED, SYSTEM}` is
  independent; AMOLED applies `withAmoledSurfaces()`.
- Persistence: `theme_mode`, `color_theme` enum-name strings in the `settings` DataStore;
  backup codec validates `SUPPORTED_COLOR_THEMES = {DYNAMIC, BUILTIN}` and
  `SUPPORTED_THEME_MODES = {LIGHT, DARK, AMOLED, SYSTEM}`.
- Live apply: DataStore → `SettingsViewModel.userSettings` → `MainActivity` root recompose.
- minSdk 31 ⇒ dynamic color API always available; no version gate or fallback needed.

## 8. Existing widget palette inventory

See `V172_INVENTORY.md` §3. Key facts:

- 9 user-selectable choices: `MATERIAL_YOU_AUTO` + 8 `MONET_*` presets
  (`WidgetAppearance.kt:16-26`, selector at WidgetConfigurationActivity.kt:255).
- Stable persisted identity = enum `name` string in SharedPreferences `widget_appearance`,
  key `widget_<id>_color`; unknown/missing → `MATERIAL_YOU_AUTO`.
- Exact token tables: `PRESETS` (WidgetAppearance.kt:326-335) + fill mapping (253-284) +
  fixed constants + `withResolvedForegroundContrast()` (337-381).
- Light+dark variants exist per preset. Widget style is provider-fixed, separate, and not
  user-selectable.
- Palette data currently lives only inside the widget package; duplicates exist in
  `ui/theme/Color.kt` (built-in teal) and `wear/WearAppearance.kt` (Wear, out of scope).
- Tests protect defaults, per-widget persistence round-trips and contrast; **no test pins the
  9 persisted ID strings or hex seeds** — this must be added before extraction (slice A).

The v1.7.2 preset family exposed to App theming is the **8 `MONET_*` presets**;
`MATERIAL_YOU_AUTO` is not a preset (it is the DYNAMIC source).

## 9. Shared palette architecture

New single token authority (pure Kotlin, no Android/Compose dependency):

```
io.github.yingqiu0871.evolune.theme.palette
  PresetPalette            enum of the 8 stable ids (MONET_BLUE … MONET_LAVENDER)
  PaletteSeed              data class: 11 ARGB tokens (as Long) — identical to today's seeds
  PaletteCatalog           object: seed(preset), stableIds, fromId(id): PresetPalette?
```

Consumers:
- **Widget**: `WidgetColorScheme` keeps its 9 values and its persisted ids unchanged;
  `PRESETS`/`preset(...)` become thin adapters reading `PaletteCatalog` for the 8 presets;
  `MATERIAL_YOU_AUTO` stays widget-side. Renderer/resolver behavior and every ARGB output
  value stay byte-identical (widget tests must remain green untouched).
- **App Material theme**: `ui/theme` gains preset scheme builders that consume the same
  `PaletteSeed`s (see §13).

Dependency direction: `ui/theme → palette` and `widget → palette`; `palette` depends on
nothing. No App → widget dependency; widget never depends on Compose MaterialTheme.
Wear duplication is explicitly out of scope (recorded P3).

## 10. ThemeColorSource model

```
enum class ThemeColorSource { DYNAMIC, PRESET }        // persisted name string
```

- Persisted key: `theme_color_source` (string) in the `settings` DataStore.
- Read precedence: `theme_color_source` if a valid name; else derive from legacy
  `color_theme` (DYNAMIC → DYNAMIC; BUILTIN → PRESET with `MONET_TEAL`); missing/invalid →
  DYNAMIC.
- `ColorTheme` remains in the codebase only as a legacy persisted value/backup token; the
  Appearance selector no longer edits it directly.

## 11. Preset palette model

- Persisted key: `theme_preset_id` (string), value = `PresetPalette.name`.
- Valid values: the 8 preset ids. Invalid/missing while source = PRESET → `MONET_TEAL`
  (closest to the legacy built-in scheme; documented fallback).
- One selected source + one selected preset id; no second authority anywhere.

## 12. Persistence / default / migration behavior

- Existing users (no new keys): source derives to DYNAMIC unless legacy `color_theme ==
  BUILTIN`, in which case PRESET + `MONET_TEAL`. Default behavior for the vast majority
  (DYNAMIC) is unchanged.
- Writes stay atomic and additive: `updateThemeColorSource` and `updatePresetPalette` write the
  new keys AND keep the legacy `color_theme` in sync (DYNAMIC → `DYNAMIC`; PRESET →
  `BUILTIN`) so old readers/backups stay meaningful.
- Backup/restore (audited): the backup schema is NOT changed in v1.7.2; `color_theme` remains
  the exported token. `replaceSettings` (restore path) must also write derived
  `theme_color_source`/`theme_preset_id` values from the restored `color_theme` so a restore
  cannot leave a stale source/preset behind. Restoring a v1.7.2-created backup on v1.7.1 keeps
  working (legacy key present, preset detail dropped) — documented limitation.
- No Room migration, no medication-data migration, no widget-preference migration.
- Widget palette ids are never touched by App theme changes; widget selections survive
  unchanged.

## 13. Light/dark behavior

- Dark/light/system/AMOLED remains `ThemeMode`, fully independent of the color source.
- PRESET provides coherent Material light+dark schemes built from the shared seeds with a
  fixed role mapping (implemented in `ui/theme`, verified by contrast/uniqueness tests):
  - primary = seed.primary; onPrimary = contrastOn(primary)
  - primaryContainer = seed.container; onPrimaryContainer = seed.onContainer
  - secondary/tertiary = seeds; onSecondary/onTertiary = contrastOn(...)
  - secondaryContainer/tertiaryContainer = 12% seed over surface (same blend used by the
    widget hero panels); on*Container = contrastOn(result)
  - surface/background = seed.surface; onSurface = fixed per variant (light `FF1F1D20`,
    dark `FFF2EFF4`); onSurfaceVariant = fixed per variant (`FF4C464E` / `FFCDC5D3`)
  - surfaceVariant = 8% primary over surface; outline = onSurfaceVariant;
    outlineVariant = 50% onSurfaceVariant over surface
  - error/onError and inverse roles: fixed project constants (no palette-specific error seed)
  - AMOLED post-processing (`withAmoledSurfaces`) applies to preset schemes exactly as today.
- Exact numeric values are finalized in slice B and must satisfy a contrast golden test
  (text roles ≥ 4.5:1, non-text ≥ 3:1 as already practiced by widget tests).

## 14. Dynamic-color behavior

- DYNAMIC keeps today's exact path (`dynamicLight/DarkColorScheme(context)`), minSdk 31
  guarantees API availability; no runtime gate is added; no fallback needed.
- DYNAMIC is the default and the pre-selection UI state for upgraded users.
- Switching DYNAMIC ↔ PRESET applies live (same DataStore → root recomposition path); no
  restart.

## 15. History copy-removal contract

Remove ONLY the displayed sentence `根据记录上下文推断匹配` from the medication-record card:

- `HistoryPresentation.matched(...)` stops emitting `R.string.history_note_inferred_match`
  (`noteRes = null` for the inferred case). `matchProvenance` and `isInferredMatch` are
  untouched; the note mechanism remains for `history_note_no_recorded_intake` and
  `history_note_plan_unavailable`.
- The UI component (`HistoryScreen.kt:763-770`) is unchanged structurally; it simply renders
  nothing for inferred entries. The card's spoken semantics are unchanged (the note is a plain
  Text).
- Tests are updated in the same slice: presentation tests assert `noteRes == null` for
  inferred entries; display tests assert the sentence is absent; the wording test drops the
  key from its scan if the resource is deleted.
- After the update, the resource has zero production references. It MAY then be deleted from
  both locale files in the same slice (default plan), provided a repository-wide grep shows no
  remaining references; otherwise it is kept.
- Nothing else changes: no matcher, occurrence generator, projection builder, repository,
  DoseEvent model, recording, Timeline, Insights or PK behavior.

## 16. Accessibility

- Flattened Settings sections keep the existing row semantics/roles; the new color-source
  selector uses radio/selected semantics with `Role.RadioButton`-equivalent behavior and the
  palette tiles expose name + selected state (mirroring the widget selector patterns) plus
  disabled semantics while DYNAMIC is active.
- The removed History note cannot regress spoken output (its text was not part of any merged
  content description).
- No new programmatic focus or live-region behavior.

## 17. Localization

- All new copy goes through both authorities (`values/strings.xml`, `values-zh-rCN/strings.xml`)
  with placeholder parity, per project convention (no `values-en`).
- Palette names reuse the existing widget palette strings (`widget_config_palette_*`) so names
  stay identical between Settings, widget configuration and future surfaces.
- Section headers reuse existing keys where present (`settings_basic_data_title`,
  `settings_appearance_format_title`, …); new keys are added symmetrically.

## 18. Compatibility

- Upgrade 1.7.1 → 1.7.2: default appearance unchanged (DYNAMIC source, existing theme mode).
  Users who explicitly chose the legacy built-in teal are mapped to PRESET + `MONET_TEAL`
  (visually near-identical; documented).
- Widgets: persisted palette ids and rendering untouched; no widget migration.
- Backup files: schema unchanged; legacy `color_theme` remains the portable token.
- No Room/schema or medication-data migration.

## 19. Frozen surfaces

No change to: Room/schema/DAOs/migrations, DoseEvent model, matching/occurrence logic,
HistoricalProjectionBuilder, Timeline, Insights, retrospective PK, export/import formats and
semantics, backup protocol, Health Connect behavior, Widget recording/rendering behavior
(tokens are relocated, output bytes identical), Wear module, version metadata, Gradle
dependencies, and all historical evidence bundles (v1.7.0/v1.7.1).

## 20. Testing requirements

- Settings: inline sections visible on the main screen; every control still mutates the SAME
  preference owner (SettingsViewModel/DataStore keys prove it); destructive/import/Drive
  protections preserved (clipboard warning, range dialog, restore preview); scrolling and
  font-scale; back behavior for kept routes.
- Theme: upgrade default = DYNAMIC; DYNAMIC unchanged; PRESET switch applies live; preset id
  persists across process recreation; invalid/legacy stored id falls back safely; every preset
  maps to a coherent light+dark scheme (contrast golden test); widget persisted selection
  unaffected; no circular dependency (guard test).
- History: sentence absent; inferred matched record behavior identical (same classification
  tests pass); no matcher/data-model delta (guard by path-scoped diff).
- Architecture guards: no duplicate palette tables (single-authority test scanning for hex
  literals outside the shared catalog); widget ids stable (new golden test); no Room/schema
  delta; no History matcher delta.
- Real-device acceptance after implementation (physical device) for the three goals.

## 21. Acceptance criteria

1. Settings shows the four inline sections and all their former second-level controls; no
   second-level navigation is required for any of them; the four entries (指南/隐私与权限/
   功能教程/关于) remain reachable and unchanged.
2. A user can pick 跟随壁纸(DYNAMIC) or 预设配色(PRESET) + one of the 8 presets; the choice
   applies live, survives relaunch, and dark/light modes remain independent.
3. Existing users see no default visual change after upgrade; widgets keep their palettes.
4. The sentence `根据记录上下文推断匹配` no longer appears on the medication-record card while
   inferred matching behavior is byte-identical.
5. All frozen surfaces are untouched (verified by path-scoped audit); JVM + affected
   instrumentation suites green; real-device acceptance recorded.

## 22. Rollback / failure behavior

- Each slice is independently revertable (docs-only, shared-catalog, theme, settings, history).
- If shared-catalog extraction cannot preserve widget token equality, slice A must stop and
  report; fallback = keep widget tokens in place and duplicate nothing (App reads via an
  adapter that imports widget constants — only if the review accepts the dependency
  direction; otherwise block).
- If a settings route removal reveals an unexpected dependency, the route stays DEPRECATED and
  is reported instead of forced-deleted.
- If preset schemes fail contrast tests, the palette is not exposed until mapping is fixed;
  DYNAMIC stays the default.
- A failed v1.7.2 phase never modifies released tags or evidence (v1.7.1 stays frozen).

## 23. Implementation slices

A — shared palette catalog + widget golden tests (no behavior change)
B — App Material theme preset support (source/preset model, persistence, schemes, tests)
C — Settings flattening + Goal B settings UI
D — History copy removal
E — regression + real-device acceptance
F — docs/status + packaging preparation (release packaging is a separate future task; no
    version bump in this cycle's implementation phase)

Details, file lists, ordering rationale and per-slice acceptance: see
`V172_IMPLEMENTATION_PLAN.md`.
