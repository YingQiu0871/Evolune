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
`MATERIAL_YOU_AUTO` is not a preset (it is the DYNAMIC source). In addition, the shared
authority carries ONE internal App compatibility identity, `LEGACY_BUILTIN` (§9/§10), which
exists only so upgraded legacy users keep their exact colors; it is never offered as a new
user-selectable preset and it never modifies any Widget palette.

Palette authority rule (frozen): every existing Widget preset's ARGB/token output must remain
value-identical during extraction. Widget palettes must NOT be altered to match old App
`Color.kt` values; the legacy App appearance is preserved through its own compatibility
identity instead.

## 9. Shared palette architecture

New single token authority (pure Kotlin, no Android/Compose dependency):

```
io.github.yingqiu0871.evolune.theme.palette
  PresetPalette            enum of the 8 stable ids (MONET_BLUE … MONET_LAVENDER)
  PaletteSeed              data class: 11 ARGB tokens (as Long) — identical to today's seeds
  PaletteCatalog           object: seed(preset), stableIds, fromId(id): PresetPalette?
  LegacyBuiltinTheme       internal compatibility identity: the EXACT v1.7.1 App built-in
                           Material light/dark schemes (sourced from ui/theme/Color.kt values)
```

Consumers:
- **Widget**: `WidgetColorScheme` keeps its 9 values and its persisted ids unchanged;
  `PRESETS`/`preset(...)` become thin adapters reading `PaletteCatalog` for the 8 presets;
  `MATERIAL_YOU_AUTO` stays widget-side. Renderer/resolver behavior and every ARGB output
  value stay byte-identical (widget tests must remain green untouched).
- **App Material theme**: `ui/theme` gains preset scheme builders that consume the same
  `PaletteSeed`s (see §13), plus the `LEGACY_BUILTIN` compatibility schemes (exact v1.7.1
  values; see §12/§13).

Dependency direction: `ui/theme → palette` and `widget → palette`; `palette` depends on
nothing. No App → widget dependency; widget never depends on Compose MaterialTheme.
`LEGACY_BUILTIN` is App-side only and must not appear among `WidgetColorScheme` values.
Wear duplication is explicitly out of scope (recorded P3).

Compatibility-identity rule (P2-1): `LEGACY_BUILTIN` is exposed to the theme only; it is not
a user-selectable new preset. The shared catalog may therefore contain the 8 canonical Widget
presets plus this one internal App legacy compatibility entry, and nothing else.

## 10. ThemeColorSource model

```
enum class ThemeColorSource { DYNAMIC, PRESET }        // persisted name string
```

- Persisted key: `theme_color_source` (string) in the `settings` DataStore.
- Read precedence: `theme_color_source` if a valid name; else derive from legacy
  `color_theme`:
  - `DYNAMIC` → DYNAMIC
  - `BUILTIN` → PRESET + the exact legacy compatibility identity `LEGACY_BUILTIN`.
    Mapping `BUILTIN → MONET_TEAL` is allowed ONLY if slice A proves the complete effective
    App Material color scheme (light AND dark, every role actually used) of the legacy
    built-in theme is EXACTLY equal to the preset-derived MONET_TEAL scheme. "Near-identical"
    or visually close is NOT sufficient. Until such proof exists, `LEGACY_BUILTIN` is
    mandatory.
  - missing/invalid → DYNAMIC
- `ColorTheme` remains in the codebase only as a legacy persisted value/backup token; the
  Appearance selector no longer edits it directly.

## 11. Preset palette model

- Persisted key: `theme_preset_id` (string), value = a preset identity name.
- Valid values: the 8 `MONET_*` ids plus the internal compatibility id `LEGACY_BUILTIN`.
- Only the 8 `MONET_*` ids are exposed as user-selectable preset choices; `LEGACY_BUILTIN`
  is a compatibility/current state only.
- Invalid/missing while source = PRESET: deterministic ladder — legacy `color_theme ==
  BUILTIN` → `LEGACY_BUILTIN`; otherwise → `MONET_TEAL`.
- One selected source + one selected preset id; no second authority anywhere.
- Settings UX for a migrated legacy user: the Appearance section shows the current state as a
  compatibility/current theme row (e.g. "当前主题：内置主题（兼容保留）") and NONE of the 8
  palette tiles is marked selected while `LEGACY_BUILTIN` is active. All 8 tiles stay
  available; the first time the user actively selects one, normal PRESET persistence takes
  over and the compatibility identity becomes unreachable (it is never silently shown as
  MONET_TEAL).

## 12. Persistence / default / migration behavior

- Existing users (no new keys): source derives to DYNAMIC unless legacy `color_theme ==
  BUILTIN`, in which case PRESET + `LEGACY_BUILTIN` — no visible color change solely because
  of the upgrade. Default behavior for the vast majority (DYNAMIC) is unchanged.
- Writes stay atomic and additive: `updateThemeColorSource` and `updatePresetPalette` write the
  new keys AND keep the legacy `color_theme` in sync (DYNAMIC → `DYNAMIC`; PRESET → `BUILTIN`)
  so old readers stay meaningful; the new keys carry the precise identity.
- Backup/codec policy — DECISION B (narrow payload schema version update, STRICT v2 schema).
  The audited codec enforces EXACT payload field sets (`requireExactPayloadFields` compares key
  sets, EvoluneBackupCodec.kt:455/527/937-949) and exact version equality
  (`EvoluneBackupV1.kt:134-135`, EvoluneBackupCodec.kt:313/457). Policy A (optional fields
  without a version bump) is therefore impossible without weakening the exact-field guard,
  which is forbidden. v1.7.2 therefore:
  - writes `payloadSchemaVersion = 2` in both the envelope and the canonical payload
    (`PAYLOAD_SCHEMA_VERSION_LEGACY = 1`, `PAYLOAD_SCHEMA_VERSION = 2`);
  - reads versions {1, 2} with EXACT field sets at both versions (the guard is preserved, not
    weakened):
    - `V1_SETTINGS_FIELDS` = the existing five fields (bodyWeightKg, themeMode, colorTheme,
      autoCheckUpdates, timeFormat) — parser byte/semantically FROZEN: no new keys accepted,
      no loosened field set, no interpretation of v2 keys;
    - `V2_SETTINGS_FIELDS` = `V1_SETTINGS_FIELDS` + `themeColorSource` + `themePresetId` —
      the v2 serializer MUST always emit both new keys (field absence is never used as state);
    - unknown extra key in v2 settings → `INVALID_PAYLOAD`; missing required v2 key →
      `INVALID_PAYLOAD`; there is NO generic allowed-set weakening anywhere;
  - canonical null representation follows the existing codec convention: a nullable field is
    always written as a present key holding JSON `null` (`put("zoneId", value)` /
    `requiredPayloadNullableString`, EvoluneBackupCodec.kt:591,596,960-962). Therefore
    `themePresetId` is always present: JSON `null` for DYNAMIC, a string otherwise;
  - serializer rules (canonical v2 write, every newly generated backup):
    - DYNAMIC                → `themeColorSource = "DYNAMIC"`, `themePresetId = null`
    - PRESET + normal palette → `themeColorSource = "PRESET"`, `themePresetId = <stable id>`
    - PRESET + compatibility → `themeColorSource = "PRESET"`, `themePresetId = "LEGACY_BUILTIN"`
    - legacy `color_theme` stays synchronized for compatibility only
      (DYNAMIC source → `DYNAMIC`; PRESET source → `BUILTIN`);
  - leaves plans/slots/events serialization byte-identical (no medication-format semantic
    change, no Drive workflow change);
  - v1.7.1 and older readers reject a v2 backup deterministically with
    `UNSUPPORTED_PAYLOAD_VERSION` — no crash, no partial restore (downgrade restore of new
    backups is intentionally blocked and documented).
- V2 field invariants (validated during decode; every violation → `INVALID_PAYLOAD`):
  - VALID:   DYNAMIC + `null`; PRESET + each of the 8 `MONET_*` ids; PRESET + `LEGACY_BUILTIN`
  - INVALID: DYNAMIC + non-null preset; PRESET + `null`; PRESET + unknown id;
             unknown/absent source; absent preset key; extra unexpected settings field
- Invalid v2 data must NOT silently fall back: no malformed/unknown v2 value may become
  `MONET_TEAL` or DYNAMIC. Fallback/derivation exists ONLY for older schema versions that
  genuinely do not contain the new identity information.
- Restore precedence (deterministic):
  1. Valid v2 fields (after full exact-schema validation) are AUTHORITATIVE — never derive
     from `color_theme` when valid v2 fields exist.
  2. Version-1 payloads: derive from the validated legacy `color_theme` per §10
     (DYNAMIC → DYNAMIC; BUILTIN → PRESET + `LEGACY_BUILTIN` unless exact MONET_TEAL
     equivalence is proven).
- Restore atomicity: decode + validate the ENTIRE backup before any Settings mutation. If v2
  theme fields are invalid, restore returns the existing typed failure `INVALID_PAYLOAD` and
  ZERO mutation occurs (`theme_mode`, `color_theme`, `theme_color_source`, `theme_preset_id`
  and Widget appearance all unchanged; no partial write). Only after successful full
  validation may `replaceSettings` perform its single atomic edit.
- `replaceSettings` (restore path) writes the legacy keys AND `theme_color_source`/
  `theme_preset_id` in one atomic edit, so a restore can never leave a stale source/preset
  behind.
- Round-trip guarantees (mandatory, tested): DYNAMIC → DYNAMIC; PRESET + each of the 8
  exposed presets → the same PRESET + same preset id; the `LEGACY_BUILTIN` compatibility state
  → the same effective legacy theme.
- Old-backup behavior (v1.7.1-and-earlier payloads, version 1): old DYNAMIC → DYNAMIC; old
  BUILTIN → the exact legacy built-in appearance (`LEGACY_BUILTIN` unless exact MONET_TEAL
  equivalence is proven). Restore never mutates Widget appearance preferences.
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
- The `LEGACY_BUILTIN` compatibility identity does NOT use the derivation above: its light and
  dark schemes are the EXACT v1.7.1 built-in `lightScheme`/`darkScheme` values from
  `ui/theme/Color.kt` (with the unchanged AMOLED post-processing), so an upgraded BUILTIN user
  sees zero color change.
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
  Users who explicitly chose the legacy built-in theme are migrated to PRESET +
  `LEGACY_BUILTIN`, which reproduces the v1.7.1 built-in light/dark schemes exactly — no
  visible color change occurs solely because of the upgrade. (Auto-mapping BUILTIN →
  `MONET_TEAL` only if slice A proves full effective-scheme equality; otherwise never.)
- Widgets: persisted palette ids and rendering untouched; no widget migration; restore never
  mutates widget appearance preferences.
- Backup files: the payload schema version becomes 2 (Decision B, §12). v1.7.2 writes and
  reads both v1 and v2; older apps deterministically reject v2 backups
  (`UNSUPPORTED_PAYLOAD_VERSION`, no crash). Old v1.7.1 backups restore on v1.7.2 with the
  §12 precedence (old DYNAMIC → DYNAMIC; old BUILTIN → exact legacy appearance).
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
- Palette compatibility (new, mandatory): stable Widget persisted ID strings pinned; stable
  Widget ARGB values before/after extraction pinned; exact legacy App built-in token snapshot
  pinned; an explicit test PROVES or DISPROVES exact legacy-built-in vs MONET_TEAL scheme
  equality (the result decides whether auto-mapping is allowed; the fallback compatibility
  identity must exist regardless).
- Upgrade: DYNAMIC remains DYNAMIC; an existing BUILTIN selection retains its exact effective
  colors (light+dark) after upgrade.
- Backup/codec (new, mandatory; strict v2 schema):
  - V1: exact legacy field set accepted; any v2 key added to a v1 payload rejected; v1 DYNAMIC
    derives DYNAMIC; v1 BUILTIN derives `LEGACY_BUILTIN`.
  - V2: exact v2 field set required; both new keys always serialized (DYNAMIC serializes
    `themePresetId` as JSON `null`); every `MONET_*` preset round-trips; `LEGACY_BUILTIN`
    round-trips; missing `themeColorSource` rejected; missing `themePresetId` key rejected;
    unknown source rejected; unknown preset rejected; DYNAMIC + non-null preset rejected;
    PRESET + null rejected; unknown extra settings field rejected — every invalid case returns
    `INVALID_PAYLOAD` with ZERO Settings mutation;
  - an invalid backup causes zero Settings mutation (`theme_mode` / `color_theme` /
    `theme_color_source` / `theme_preset_id` unchanged) and zero Widget preference mutation;
  - older-reader rejection of v2 is asserted (`UNSUPPORTED_PAYLOAD_VERSION`);
  - restore of valid v1 payloads (old DYNAMIC / old BUILTIN) restores deterministically; valid
    v2 fields are authoritative and never derived-over from `color_theme`.
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
3. Existing users see no default visual change after upgrade; a legacy BUILTIN selection keeps
   its exact effective light+dark colors via the compatibility state; widgets keep their
   palettes.
4. Backup round-trips (both on-device persistence and backup/restore): DYNAMIC → DYNAMIC;
   PRESET + MONET_BLUE → PRESET + MONET_BLUE; PRESET + MONET_SAKURA → PRESET + MONET_SAKURA;
   every exposed preset survives; the legacy compatibility theme restores to the same
   effective theme; old v1.7.1 backups restore deterministically; new backups never mutate
   widget appearance.
5. The sentence `根据记录上下文推断匹配` no longer appears on the medication-record card while
   inferred matching behavior is byte-identical.
6. All frozen surfaces are untouched (verified by path-scoped audit); JVM + affected
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
- Legacy BUILTIN continuity is NOT allowed to degrade: if slice A cannot prove exact
  legacy-vs-MONET_TEAL equality, the `LEGACY_BUILTIN` compatibility identity is mandatory and
  the BUILTIN → MONET_TEAL auto-migration is forbidden. If the compatibility identity cannot
  reproduce v1.7.1 colors exactly, slice B must stop and report.
- If the payload v2 codec change cannot be completed without weakening the exact-field guard,
  the backup round-trip requirement blocks and is reported (no partial schema change).
- A failed v1.7.2 phase never modifies released tags or evidence (v1.7.1 stays frozen).

## 23. Implementation slices

A — shared palette catalog + widget golden tests (no behavior change; includes the exact
    legacy built-in token snapshot and the PROVE/DISPROVE exact-equality test for
    legacy-built-in vs MONET_TEAL)
B — App Material theme preset support (source/preset model, `LEGACY_BUILTIN` compatibility
    identity, persistence, live schemes, backup codec v2 + restore precedence, tests)
C — Settings flattening + Goal B settings UI (including the truthful compatibility/current
    theme row for migrated legacy users)
D — History copy removal
E — regression + real-device acceptance
F — docs/status + packaging preparation (release packaging is a separate future task; no
    version bump in this cycle's implementation phase)

Details, file lists, ordering rationale and per-slice acceptance: see
`V172_IMPLEMENTATION_PLAN.md`.
