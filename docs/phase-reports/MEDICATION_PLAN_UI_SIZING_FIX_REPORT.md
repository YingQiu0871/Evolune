# Evolune Medication Plan UI Sizing Fix Report

Date: 2026-08-10
Workspace: `D:\Evolune-plan-ui-fix`
Branch: `phase1/medication-plan-ui-sizing-fix`
Baseline: `phase-1-plan-save-regression-fix`
Status: **Implemented, visually accepted by user, pending independent review.**

No files were staged or committed, no tag was created, and Batch 7 was not started.

## 1. Scope

This increment resolves the latest user-reported Medication editor defects:

1. Medication Plan route options had inconsistent widths and heights on portrait phones.
2. Long Chinese route labels were forced into narrow character-by-character vertical layouts.
3. Medication Plan ester and schedule choices used different sizing behavior.
4. Delete, cancel, and save actions in the Medication Record editor did not have a stable shared size.
5. Existing save, edit, delete, persistence, and accessibility behavior must remain unchanged.

## 2. Previous behavior

The plan editor placed all route choices in one connected horizontal `ButtonGroup`. On common
portrait widths, the available width was divided among five options, making longer Chinese labels
extremely narrow. Selected controls could also use expressive width animation, so selection changed
the visual proportions of the row.

The record editor placed three equally weighted actions in one row, but the delete action contained
a full-size icon, an 8dp gap, default button padding, and text. Its content therefore wrapped even
though the outer button had the same nominal weight as cancel and save.

## 3. Final layout strategy

### 3.1 Shared option grid

`MedicationOptionGrid` is now the single sizing implementation for Medication Plan options.

- Material 3 `FilterChip` semantics are retained.
- Selected and unselected states use identical geometry.
- Every item in a row receives equal weight.
- Every option has a fixed group-specific height of at least 56dp.
- Labels are centered and may naturally use up to two lines.
- Compact route layout uses two columns.
- Expanded route layout uses three columns.
- Ester choices use three compact columns and up to five expanded columns.
- Schedule choices use three columns.
- Anti-androgen and sublingual choices use responsive grids through the same component.
- A one-physical-pixel remainder may be assigned by Compose when the available pixel width is odd;
  this does not change the perceived size and is covered by a one-pixel instrumentation tolerance.

This removes the former expressive width animation and prevents selection from moving surrounding
content.

### 3.2 Shared editor action row

`MedicationEditorActionRow` is now used by both Medication Plan and Medication Record editors.

- Delete, cancel, and save use equal row weights.
- All actions have a fixed 56dp height.
- Horizontal spacing is 8dp.
- The delete icon is 18dp with a 4dp text gap.
- Internal horizontal padding is reduced without reducing the touch target.
- Delete, cancel, and save labels remain on one line.
- Error color, enabled state, click behavior, test tags, and Material 3 button semantics are retained.

## 4. Deferred feature: custom medication support

The requested “其他药物” capability is a medication-identity feature, not an administration route.
It is intentionally not implemented in this UI-only increment.

A later, separately designed feature must support:

- an “其他药物” medication identity;
- a user-entered medication name, for example “微粉化黄体酮”;
- plans, records, reminders, and scheduling for that medication;
- explicit PK-unsupported behavior, so unsupported medication does not contribute to concentration;
- no fake `E2` or `Ester` placeholder used as medication identity;
- coordinated Domain, persistence, JSON, and Wear compatibility design.

The temporary custom-route workaround, its PK exclusions, mappings, placeholders, UI option, and
tests were fully removed before validation.

## 5. Main implementation files

### UI components

- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationOptionGrid.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationEditorActionRow.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationPlanBottomSheet.kt`
- `app/src/main/java/io/github/yuninggu/evolune/ui/components/MedicationRecordBottomSheet.kt`

### Tests

- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationPlansScreenTest.kt`
- `app/src/androidTest/java/io/github/yuninggu/evolune/ui/screens/MedicationRecordsScreenTest.kt`

## 6. Regression coverage

The new tests verify:

- all five existing visible route options have equal visual width and height on a portrait phone;
- the selected state does not change the option bounds;
- route options are wider than they are tall, preventing forced vertical Chinese text;
- plan-editor delete, cancel, and save actions have equal dimensions;
- record-editor delete, cancel, and save actions have equal dimensions;
- existing save, edit, delete, metadata-preservation, and double-tap protections remain unchanged.

## 7. Validation results

| Validation | Device / scope | Result |
|---|---|---|
| Medication Plan UI class | API 33 and API 35 phones | PASS |
| Medication Record UI class | API 33 and API 35 phones | PASS |
| Full App JVM | 43 suites / 364 tests | 0 failures, 0 errors, 0 skipped |
| PK regression | 5 suites / 49 tests | 0 failures, 0 errors, 0 skipped |
| Full connected tests | API 33 phone | 114 tests, 0 failures/errors, 2 skipped |
| Full connected tests | API 35 phone | 114 tests, 0 failures/errors, 2 skipped |
| Focused connected tests | API 33 phone | 28 tests, 0 failures/errors, 2 skipped |
| Focused connected tests | API 35 phone | 28 tests, 0 failures/errors, 2 skipped |
| Foldable focused UI and MD3 matrix | Pixel_10_Pro_Fold, OPENED, API 37 | 25/25 PASS |
| Adaptive navigation matrix | 360/412/600/720/800/1000dp widths | PASS |
| App debug build | `assembleDebug` | PASS |
| App lint | `lintDebug` | 0 errors, 83 warnings, 1 hint |
| Android test compile | `compileDebugAndroidTestKotlin` | PASS |
| KSP generation | `kspDebugKotlin` | PASS |
| APK 16KB alignment | `zipalign -c -P 16 -v 4` | PASS |
| Whitespace validation | `git diff --check` | PASS |

The connected tests were executed on the listed phone/foldable emulators only. The Wear emulator was
not used for phone UI validation. The real Samsung app was not uninstalled or cleared, and no real
database was read or exported.

## 8. Related branch-level UI status

The same worktree also contains the earlier UI-stabilization changes. User-confirmed results are:

- Samsung IME bounce: resolved.
- Dark-mode text colors: normal under Material 3 role pairing.
- OLED-black theme option icon: centered.
- portrait option grid and editor action-row sizing: accepted;
- date/time layout, single `mg`, floating label, Settings appearance, navigation motion, and compact
  bottom navigation: accepted.

The foldable navigation, stable top title, fade-through navigation transition, and 16KB native
library alignment were automatically revalidated. User visual acceptance on the Samsung device is
complete; independent review remains outstanding.

## 9. Boundaries

Unchanged by this increment:

- Medication Plan save behavior and ViewModel persistence flow;
- existing route and ester enums and behavior;
- Repository contracts and implementations;
- Room schema and migrations;
- DAO and Entity field structure;
- existing PK parameters and algorithms for all prior routes;
- Wear protocol and payloads;
- Widget appearance implementation;
- Batch 7.

## 10. Risks and decision

The architecture/scope P1 caused by the temporary custom-route workaround is resolved. The final
classification for this increment is P0/P1/P2 = **0/0/0**.

This report does not claim independent review, commit, tag, release readiness, or Batch 7
authorization.
