# Evolune v1.7.2 — Repository Inventory (read-only audit)

Baseline: `feature/v1.7.2-settings-theme-history` @ `746fc0a970bf0dfb3735c9225ce662e9511514ae`
(released v1.7.1). This inventory was produced by reading the actual source; it is the evidence
base for `V172_CONTRACT.md`. No product change was made in Phase 0.

Scope of v1.7.2: Goal A Settings flattening · Goal B App preset color palettes (reusing the
existing phone-widget palette family) · Goal C History copy removal. Everything else is frozen
(see contract §19).

---

## 1. Settings tree (current state)

`SettingsScreen` (`app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt`)
is a pure navigation hub (8 `SettingsNavigationRow`s, `Column + verticalScroll`); every row
navigates to a second-level route. No control of its own.

| # | Top level | Row file:line | Route constant / string | Destination file | Second-level content | State owner | Persistence | Side effects | Target v1.7.2 location | Keep route? | Risk |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 基础数据 | SettingsScreen.kt:86-92 | `BASIC_DATA_ROUTE` = `settings_basic_data` (AppNavigation.kt:163) | BasicDataScreen.kt (1052-1057) | body-weight `OutlinedTextField` (`settings-weight-input`, 69-92) | SettingsViewModel.userSettings (VM:69-74) | `body_weight` double (SettingsDataStore.kt:99,151-159) + adopted-at tick | none | Inline section 基础数据 | REMOVE (pending proof) | low |
| 2 | 外观与格式 | 93-99 | `APPEARANCE_FORMAT_ROUTE` = `settings_appearance_format` (164) | AppearanceAndFormatScreen.kt (1058-1065) | ThemeMode radios (95-112); ColorTheme radios (148-159); TimeFormat radios (197-208) | SettingsViewModel.userSettings | `theme_mode` (100,164-168), `color_theme` (101,173-177), `time_format` (103,191-195) | global theme recompose; time-format → widget refresh (MA:175-180) | Inline section 外观与格式 (+ new color source/preset controls) | REMOVE (pending proof) | medium (Goal B touches this surface) |
| 3 | 同步与备份 | 100-106 | `SYNC_AND_BACKUP_ROUTE` = `sync_and_backup` (165) | SyncAndBackupScreen.kt (1066-1087) — hub only | 3 navigation rows: local data / Health Connect / Google Drive | userSettings + HC state (VM:82-83) + BackupRestoreViewModel.connected (BK:50-51) | read-only hub | none at hub level | Inline section 同步与备份 | REMOVE (pending proof) | medium |
| 3a | └ 本地数据 | SyncAndBackupScreen.kt:45-52 | `DATA_IMPORT_EXPORT_ROUTE` = `data_import_export` (168) | DataImportExportScreen.kt (1141-1166) | portable export JSON/CSV (206-221) + portable import (222-229); legacy import file/clipboard (254-269), legacy export file/clipboard (270-285); range dialog, clipboard warning, import-result dialogs | HRTViewModel + local dialog state (NAV:362-370) | writes events via existing services; legacy import overwrites `body_weight` (NAV:401-405, 444-446) | SAF pickers, bounded 32 MiB reads, off-main IO | Inline action rows in 同步与备份 (all dialogs preserved) | REMOVE (pending proof) | high (destructive import paths; must keep protections) |
| 3b | └ Health Connect | SyncAndBackupScreen.kt:54-64 | `HEALTH_CONNECT_SYNC_ROUTE` = `health_connect_sync` (169) | HealthConnectSyncScreen.kt (1167-1185) | weight-sync switch (89-94), last-sync text, disabled med-sync placeholder, manage-permissions button (141-148), reauthorize (149-161) | userSettings + HC coordinator state | `health_connect_weight_sync_enabled` (104-105,197-201); adoption writes body_weight + metadata | permission contract (NAV:300-324), contextual consent dialogs, external HC manage-data activity, foreground sync (MA:81-86) | Inline controls in 同步与备份 | REMOVE (pending proof) | high (permission flow + authoritative weight adoption) |
| 3c | └ Google Drive | SyncAndBackupScreen.kt:66-79 | `GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE` = `google_drive_backup_restore` (170) | GoogleDriveBackupRestoreScreen.kt → BackupRestoreSection.kt (1186-1212) | backup now / restore / disconnect; passphrase dialogs; generation picker; restore preview with destructive warning | BackupRestoreViewModel (BK:47-51) | not in UserSettings; Play Services authorization; restore atomically writes Room + `replaceSettings` (230-242) + crash journal | Play Services IntentSender, network, post-restore reminder reschedule + widget refresh | KEEP as dedicated screen; section shows status + entry row | KEEP | high (secure multi-step workflow; also reused by FeatureTutorial step 5) |
| 4 | 更新 | 107-113 | `UPDATE_ROUTE` = `settings_update` (166) | UpdateScreen.kt (1088-1095) | auto-check switch (128-151); check-now row + status (153-173); current version row w/ clipboard copy (175-193) | SettingsViewModel (`updateCheckResult` VM:79-80; autoCheckUpdates) | `auto_check_updates` boolean (102,182-186); check result in-memory only | GitHub API fetch (UpdateChecker.kt:23-45), global result dialogs (NAV:676-727) | Inline section 更新 | REMOVE (pending proof) | low |
| 5 | 指南 | 114-120 | `ONBOARDING_ROUTE` = `onboarding` (171) | OnboardingFlowScreen.kt (1103-1120) | terms/PK acknowledgement flow, disclosures link, cancel | OnboardingViewModel + OnboardingStateStore | onboarding DataStore keys (ONB:55-64) | none | Keep as navigation row (content page) | KEEP (also first-run gate NAV:247-258) | low |
| 6 | 隐私与权限 | 121-127 | `DISCLOSURES_ROUTE` = `disclosures` (172) | DisclosuresScreen.kt (1138-1140) | 3 static disclosure sections | none | none | none | Keep as navigation row | KEEP (deep link MA:72-76; first-run overlay NAV:232-237; About row) | low |
| 7 | 功能教程 | 128-134 | `FEATURE_TUTORIAL_ROUTE` = `feature_tutorial` (176) | FeatureTutorialScreen.kt (1121-1137) | 6-step tour with launches into Home/Plan/Record/Drive | local step state | `feature_tutorial_auto_launch_pending` (ONB:61-62) | app-start auto-launch route (NAV:188-198,919-926) | Keep as navigation row | KEEP (auto-start + Drive reuse) | low |
| 8 | 关于 | 135-141 | `ABOUT_ROUTE` = `settings_about` (167) | AboutScreen.kt (1096-1102) | website/contact external links; copyright dialog; disclaimer → disclosures | local state | none | external browser/mail intents | Keep as navigation row | KEEP | low |

Hub wiring: `AppNavigation.kt:1021-1051`. Sub-route top-bar titles/back handling:
`AppNavigation.kt:839-866`; `isSettingsSubroute` list `805-818`.

### 1.1 UserSettings + DataStore keys (SettingsDataStore.kt)

| Field | Type | Default | Key | Writer |
|---|---|---|---|---|
| bodyWeight | Double | 55.0 | `body_weight` | updateBodyWeight 151-159 / HC 203-215 / replaceSettings |
| themeMode | ThemeMode{LIGHT,DARK,AMOLED,SYSTEM} | SYSTEM | `theme_mode` | updateThemeMode 164-168 |
| colorTheme | ColorTheme{DYNAMIC,BUILTIN} | DYNAMIC | `color_theme` | updateColorTheme 173-177 |
| autoCheckUpdates | Boolean | true | `auto_check_updates` | updateAutoCheckUpdates 182-186 |
| timeFormat | TimeFormat{SYSTEM,HOUR_12,HOUR_24} | SYSTEM | `time_format` | updateTimeFormat 191-195 |
| healthConnectWeightSyncEnabled | Boolean | false | `health_connect_weight_sync_enabled` | 197-201 |
| lastHealthConnectWeightKg | Double? | null | `last_health_connect_weight_kg` | 203-228 |
| lastHealthConnectWeightAdoptedAt | Instant? | null | `last_health_connect_weight_adopted_at` | 203-228 |

Read fallbacks: unknown enum name → SYSTEM / DYNAMIC / SYSTEM (118-139). DataStore name
`"settings"` (17). Restore uses `AtomicSettingsStore.replaceSettings` (230-242) which writes
bodyWeight/adopted-at/themeMode/colorTheme/autoCheckUpdates/timeFormat atomically.

Outside UserSettings: onboarding DataStore (`onboarding` keys, ONB:55-64); widget appearance
SharedPreferences `widget_appearance` (WidgetAppearance.kt:63-113); update result in memory;
reminder-skip and wear caches (not settings UI).

---

## 2. Theme architecture (current state)

| Item | Location | Notes |
|---|---|---|
| Theme entry | `ui/theme/Theme.kt` — `EvoluneTheme` (285-315) | `MaterialExpressiveTheme(colorScheme, AppTypography, MotionScheme.expressive())` |
| Light/dark built-in schemes | `Theme.kt:22-96` (`lightScheme`, `darkScheme`) from `ui/theme/Color.kt` tokens (teal seed) | medium/high-contrast schemes defined but unused (117,155,193,231) |
| Dynamic color | `Theme.kt:294-299` `colorTheme == ColorTheme.DYNAMIC` → `dynamicLight/DarkColorScheme(context)` | **no SDK gate needed**: minSdk 31 (app/build.gradle.kts:63); no dynamic availability check |
| Dark mode | `ThemeMode.usesDarkColors` `Theme.kt:98-103`; AMOLED surfaces `Theme.kt:105-115,304-308` | independent of color source |
| Persistence | `theme_mode` / `color_theme` (SettingsDataStore.kt:100-101) | enum-name strings; backup codec validates `SUPPORTED_THEME_MODES`/`SUPPORTED_COLOR_THEMES` (EvoluneBackupCodec.kt:162-166,1011-1012) |
| Recomposition path | DataStore → `SettingsViewModel.userSettings` (VM:69-74) → `MainActivity.kt:172-186` `EvoluneTheme(...)` → whole subtree | live apply without restart |
| Widget refresh on time-format | `MainActivity.kt:175-180` | irrelevant to palette |
| App theme tests | `ThemeTest.kt`; `ColorRoleConformanceTest.kt:148-219`; `SettingsCategoryScreenTest.kt:46-71`; `V15InPlaceUpgradeDeviceTest.kt:101-105,146-148` | |

---

## 3. Widget palette inventory (current state)

Single source: `widget/WidgetAppearance.kt`. Traditional RemoteViews (no Glance).

- `WidgetColorScheme` (16-26): **9 user-selectable choices** — persisted identity = enum
  `name` string in SharedPreferences `widget_appearance`, key `widget_<id>_color`
  (108-111, 89-92). Read fallback → `MATERIAL_YOU_AUTO` (105-106).
- `WidgetStyle` (29-40): 5 stable string ids (`legacy_default`, `today_plan`, `next_dose`,
  `current_e2`, `pk_chart`); provider-fixed, **not user-selectable** (WidgetConfigurationActivity
  :89,103,108,120; WidgetHostContractTest.kt:41-56).
- Preset tokens: `PRESETS` (326-335) — 11 seeds per palette (light surface/primary/secondary/
  tertiary/container/onContainer; dark surface/primary/secondary/tertiary/container); fixed
  per-variant tokens and `withResolvedForegroundContrast()` (337-381); Material You role
  mapping (199-251) with `MONET_TEAL` fallback.

| # | Stable ID | Visible name (key) | Light tokens (seed) | Dark tokens (seed) |
|---|---|---|---|---|
| 1 | `MATERIAL_YOU_AUTO` | Material You (`widget_config_material_you`) | system roles | system roles |
| 2 | `MONET_BLUE` | 蓝 (`widget_config_palette_blue`) | 3F5F90/565F71/705575/D6E3FF/F8F9FF/0B1B33 | A8C7FA/BEC6DC/DDBCE0/284777/111318 |
| 3 | `MONET_VIOLET` | 紫罗兰 (`..._violet`) | 70558F/665A70/815343/EEDBFF/FBF8FF/29143F | DDB8FF/D1C0D8/F5B9A5/573D74/151217 |
| 4 | `MONET_SAKURA` | 樱花 (`..._sakura`) | 9A405D/75565F/775930/FFD9E2/FFF8F9/3E001D | FFB1C5/E5BDC6/E7C086/7D2947/181113 |
| 5 | `MONET_MINT` | 薄荷 (`..._mint`) | 356A4E/506355/3F6374/B8F2CE/F6FCF7/002112 | 9DD6B3/B7CCBC/A6CDDF/1D5138/0E1511 |
| 6 | `MONET_TEAL` | 青绿 (`..._teal`) | 006A64/4A6360/4A607C/9DF2E9/F4FBF9/00201E | 81D5CD/B0CCC8/B2C8E8/00504B/0E1514 |
| 7 | `MONET_AMBER` | 琥珀 (`..._amber`) | 805600/705D3E/53643C/FFDEA5/FFF9F0/291800 | F6BD6C/DEC6A1/BACD97/614000/18130B |
| 8 | `MONET_NEUTRAL` | 中性 (`..._neutral`) | 5F5E65/616066/605D6E/E5E1E9/FAF9FC/1B1B1F | C9C5CD/CBC5CD/CAC3DB/47464D/141316 |
| 9 | `MONET_LAVENDER` | 薰衣草 (`..._lavender`) | 6750A4/625B71/7D5260/EADDFF/FCF8FF/21005D | D0BCFF/CCC2DC/EFB8C8/4F378B/141218 |

(Full exact tables with per-field mappings: contract §8; source of truth remains
`WidgetAppearance.kt` until extraction.)

Duplication found (audit):
- `ui/theme/Color.kt` reproduces the built-in teal seed values (≈ `MONET_TEAL`, minor tertiary
  delta) — the App `ColorTheme.BUILTIN` scheme is a hand-built Material scheme, not the widget
  palette catalog.
- `wear/.../WearAppearance.kt:12-22,108-117` duplicates the 8 dark presets for Wear (Wear is
  OUT OF SCOPE for v1.7.2; record as P3).
- No raw palette tables in RemoteViews builders / WidgetUi / Settings.

Persistence compatibility: no migration, no version key; unknown/missing palette id →
`MATERIAL_YOU_AUTO`; widget appearance is NOT part of backup/restore.

Tests protecting compatibility: `WidgetAppearanceTest.kt` (defaults 12-24, per-widget
isolation 51-66, distinct readable palettes 87-101, contrast 250-309), `WidgetRemoteViewsTest.kt`
(real SharedPreferences round-trip 84-114, rendered roles 116-171), `WidgetHostContractTest.kt`
(provider identity, no style selector). Gap: no golden test pins the 9 persisted ID strings or
the hex seeds — v1.7.2 must add one BEFORE refactoring.

---

## 4. History copy (current state)

Exact phrase `根据记录上下文推断匹配`:

| Artifact | Location | Detail |
|---|---|---|
| Resource | `values/strings.xml:505`, `values-zh-rCN/strings.xml:495` | key `history_note_inferred_match` |
| Producer | `history/HistoryPresentation.kt:126-130` | `noteRes = history_note_inferred_match` when `matchProvenance != EXACT_SLOT_AND_LOCAL_DATE`; `isInferredMatch` flag at :134 |
| Model | `history/HistoryUiModels.kt:70` | `noteRes: Int?` shared by three note kinds |
| UI call site | `ui/screens/HistoryScreen.kt:763-770` | `entry.noteRes?.let { Text(...) }` inside the entry card; testTag `history-entry-note` |
| Other users of noteRes | `HistoryPresentation.kt:159` (`history_note_no_recorded_intake`), `:188` (`history_note_plan_unavailable`) | the note component must stay |
| Tests | `HistoryPresentationTest.kt:56,81`; `HistoryQuickRecordInferredTest.kt:73,98,102`; `HistoryScreenTest.kt:213,235,256` (display asserts), `:336` (absence assert); `HistoryPresentationWordingTest.kt:88` (reads the resource text) | |
| Semantics | the note text is a plain `Text` (no own semantics/merged description); removing it does not alter the card's spoken description | verified at HistoryScreen.kt:763-770 / `calendarCellDescription` is calendar-only |

Removing the displayed sentence therefore = stop emitting the inferred note from the
`matched(...)` branch (`HistoryPresentation.kt:94,126-130`, `noteRes = null` for the inferred
case) and update the display/wording tests; classification (`matchProvenance`,
`isInferredMatch`) stays untouched. The resource becomes production-dead but test-referenced;
deletion decision belongs to the implementation slice (see contract §15).

---

## 5. Phase-0 correction findings (facts added after the contract review)

P2-1 — legacy BUILTIN vs MONET_TEAL equality is NOT established.
- The legacy built-in scheme is hand-authored in `ui/theme/Theme.kt:22-96` from
  `ui/theme/Color.kt`; `MONET_TEAL` is the widget seed set in `WidgetAppearance.kt:326-335`.
  Token inspection already shows at least one mismatch (e.g. tertiary dark `0xFFAFC9E7`
  built-in vs `0xFFB2C8E8` widget), and the preset-derived Material scheme is generated by a
  different mapping than the hand-authored scheme. Exact equality is therefore unproven and
  must be PROVEN or DISPROVEN by an implementation-time test (contract §10/§20); until then
  the `LEGACY_BUILTIN` compatibility identity is mandatory and BUILTIN → MONET_TEAL
  auto-mapping is forbidden.

P2-2 — the backup payload is strictly exact; optional fields require a version update.
- Envelope parsing uses `requireExactEnvelopeFields` (`EvoluneBackupCodec.kt:428,933-935`);
  payload objects use `requireExactPayloadFields` with exact key-set equality
  (`:455,475,497,509,527,937-949`); versions are constant-folded (`EvoluneBackupV1.kt:134-135`)
  and compared with exact equality (`:313,457`). Unknown fields are REJECTED, so
  `color_theme`-only + optional new keys without a schema bump (Policy A) is impossible
  without weakening the exact-field guard. The contract therefore adopts Decision B: narrow
  payload schema version update (1 → 2) with version-gated parsing; v1.7.1 readers
  deterministically reject v2 payloads (`UNSUPPORTED_PAYLOAD_VERSION`).
