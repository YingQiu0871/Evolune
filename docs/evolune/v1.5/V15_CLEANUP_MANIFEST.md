# Evolune v1.5-D — Cleanup Manifest

## Scope

This manifest records only cleanup items that were statically shown to be unused or redundant
under the v1.5 release boundaries. No PK, Room, backup, migration, Wear protocol, signing, or
launcher-artwork behavior was changed by these removals.

The separate Wear launcher compatibility fix is tracked in `V15_ACCEPTANCE.md`; it is a
verified artwork/resource defect correction, not a dead-code cleanup removal.

Device serials in the verification record are scoped to their capture time. Emulator ports were
reused between runs, so each historical Phone/Wear label is paired with the AVD assignment that
existed during that run; current port mappings must not be used to relabel the historical result.

## Removed resources

The following resources had no production or layout references and were reported by Android
Lint as `UnusedResources`:

- Template colors from `app/src/main/res/values/colors.xml`: `purple_200`, `purple_500`,
  `purple_700`, `teal_200`, `teal_700`, `black`, and `white`.
- Unused Widget drawables: `ic_widget_alarm.xml`, `ic_widget_alarm_preview.xml`,
  `ic_widget_arrow.xml`, `ic_widget_close.xml`, `ic_widget_medication.xml`, and
  `ic_widget_medication_preview.xml`.
- Superseded Widget shapes: `widget_button.xml` and `widget_panel.xml`.
- Unused strings from both default and `zh-rCN` resources: `import_importing`,
  `import_weight_updated`, and `home_chart_title`.
- Unused Widget strings from both default and `zh-rCN` resources: `widget_due_now`,
  `widget_next`, `widget_upcoming`, and `widget_concentration_unavailable`.

This is 25 resource entries across one color file, eight drawable files, and the two localized
string files. The four unused Widget strings existed only in the default resource file; the
other three unused strings were removed from both localized files.
The resource names were re-searched after deletion; no remaining source or resource reference
was found.

## Redundant and unsafe code paths corrected

- Removed API-level branches that were unreachable with the Phone `minSdk` of 31: notification
  channel creation, exact-alarm API selection, and dynamic-color availability checks now use the
  platform contracts directly.
- Made all record-dose numeric formatting explicit about its locale and replaced the integer
  edit-index state with `mutableIntStateOf`.
- Added action validation to the Phone reschedule receiver and Wear sync receiver so an
  unexpected broadcast action is ignored before any asynchronous work starts. Test-only
  receiver construction keeps synthetic lifecycle tests isolated without widening production
  manifest behavior.

## Rejected cleanup candidate

An attempted move of `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` to the unqualified
`mipmap-anydpi` directory was reverted. Although the updated contract test passed, the unqualified
resource creates an additional `IconXmlAndPng` warning while the legacy density family remains a
required compatibility contract. The existing Phone resource path therefore remains unchanged.
The Wear adaptive icon intentionally remains in `wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:
a device test showed that moving it to an unqualified directory makes Wear resolve the legacy PNG
and reintroduces the black-corner risk. The notification XML/PNG pair was also intentionally
retained because the notification icon is an existing compatibility asset and is explicitly outside
this cleanup removal.

## Verification

- `:app:lintDebug` — PASS; 0 errors and 0 `UnusedResources` findings.
- `:wear:lintDebug` — PASS.
- `:experience-core:test` — PASS.
- `:app:testDebugUnitTest` — PASS.
- `:wear:testDebugUnitTest` — PASS.
- `:app:assembleDebug` and `:wear:assembleDebug` — PASS.
- `ReceiverLifecycleInstrumentationTest` — PASS, 5/5 on isolated Phone `emulator-5554`.
- `OnboardingFlowScreenTest` — PASS, 3/3 on isolated Phone `emulator-5554`.
- `DisclosuresScreenTest` — PASS, 2/2 on isolated Phone `emulator-5554`.
- `ColorRoleConformanceTest` — PASS, 3/3 on isolated Phone `emulator-5554`; the test-only
  onboarding and nested appearance navigation setup was corrected without changing production
  theme behavior.
- `V15InPlaceUpgradeDeviceTest` — PASS, 2/2 across its two-phase execution on isolated Phone
  `emulator-5554`: v1.4.0 Debug state preparation 1/1 followed by v1.5.0 Debug state and
  integrity verification 1/1; this is a test-only package-upgrade contract.
- `V15TimeBoundaryDeviceTest` — PASS, 1/1 on isolated Phone `emulator-5554`; deterministic
  cross-midnight, time-zone, and DST behavior was exercised on Android without production
  changes.
- `HomeScheduleBoundaryTest` — PASS; the Home refresh boundary recomputes from the realtime
  state snapshot and correctly advances a daily plan across local midnight without adding a
  per-second Home content recomposition.
- `V15PkPerformanceDeviceTest` — PASS, 1/1 on isolated Phone `emulator-5554`; the fixed
  EMPTY/STEADY/DENSE datasets produced repeated PK outputs and emitted same-device v1.4.0 to
  v1.5.0 runtime measurements without changing the PK implementation.
- `scripts/measure_v15c_background.ps1` — PASS, valid same-device v1.4.0→v1.5.0 Debug
  screen-off captures; the resulting diagnostic record is in `V15C_BACKGROUND_CAPTURE.md`.
- Selected migration matrix, Room repository, restore persistence, and Widget RemoteViews
  instrumentation classes — PASS, 78/78 on isolated Phone `emulator-5554`.
- `FeatureTutorialNavigationTest` and `SyncAndBackupNavigationTest` — PASS, 4/4 on isolated
  Phone `emulator-5554`; the tutorial suite includes Activity recreation and the Release package
  also passed the post-reboot cold-launch smoke.
- `MedicationPlansScreenTest` and `MedicationRecordsScreenTest` — PASS, 26/26 on isolated
  Phone `emulator-5554`; the record test's emulator-specific IME cleanup assertion was corrected
  in the test harness without changing production medication behavior.
- `MedicationPlanProductionCutoverTest`, `DoseEventProductionCutoverTest`, and
  `ReceiverLifecycleInstrumentationTest` — PASS, 10/10 on isolated Phone `emulator-5554`.
- `SyncAndBackupScreenTest` — PASS, 4/4 on isolated Phone `emulator-5554`.
- `PhysicalBackupPerformanceTest` — PASS, 6/6 on isolated Phone `emulator-5554`; valid large-history
  encode/decode/preview counts matched and crypto ran off the main looper.
- `WidgetRemoteViewsTest` and `ReceiverWidgetProductionCutoverTest` — PASS, 17/17 on isolated
  Phone `emulator-5554`; Widget configuration, action identity, replay, failure, and concurrency
  behavior remained covered.
- Pixel Launcher Widget smoke — PASS on isolated Phone `emulator-5554`; two real Evolune
  Widget instances survived a Pixel Launcher process restart and were rediscovered in the
  launcher host hierarchy.
- `WearProductionCutoverTest` — PASS, 5/5 on isolated Phone `emulator-5554`; production Wear
  publication, stale/pending/failure handling, retry, and Phone-authority behavior remained
  covered.
- `WearManifestContractTest` — PASS, 1/1 on isolated Wear `emulator-5554`.
- `WearLauncherIconResourceTest` — PASS, 1/1 on isolated Wear `emulator-5554`; the installed
  launcher resource resolved to adaptive background/foreground layers.
- Cross-density `LauncherIconResourceTest` — PASS, `11/11`; the five Wear legacy launcher PNGs
  have transparent corners and no opaque near-black pixels, preventing recurrence of the reported
  Wear launcher black-corner defect.
- `WearDataLayerDeviceProbeTest` — compiled and invoked on the current Wear emulator; it
  explicitly skipped because no paired Phone/Wear node was available. It is retained as the
  live transport probe for the next paired-device run and is not counted as evidence of
  delivery.
- Current-source Wear instrumentation — PASS, `3` tests with `0` failures and `1` conditional
  skip on Wear `emulator-5554`; `WearManifestContractTest` and `WearLauncherIconResourceTest`
  passed `1/1` each, while the transport probe skipped for the documented unpaired-node
  condition.
- Wear runtime smoke — PASS, v1.5.0 Debug launched on isolated Wear `emulator-5554` and
  rendered the expected disconnected/cache state without fatal log keywords; a 30-second
  screen-off observation kept the process stable. Data Layer delivery remains unexercised.
- `git diff --check` — PASS.
- Complete Phone connected regression after the two-phase upgrade-test isolation fix — PASS,
  `205` tests with `0` failures and `5` conditional skips on `emulator-5554`.
- Current-source complete Phone regression before the final one-line color mapping correction —
  PASS, `201` tests with `0` failures and `5` conditional skips on Phone `emulator-5556`.
- Historical final-source complete Phone regression — `201` tests with `5` conditional skips and
  one Compose timing timeout in `MedicationRecordsScreenTest.createSuccessClosesEditorAfterContractInsert`;
  the other `200` tests passed, and the failed method passed isolated `1/1`. The timeout was
  traced to the test harness losing a save tap while the emulator IME was transitioning.
- Final-source complete Phone regression after the IME harness fix — PASS, `201` test cases with
  `0` failures and `5` conditional skips on Phone `emulator-5556`. The full
  `MedicationRecordsScreenTest` class passed `12/12`; targeted Widget/Wear boundary tests passed
  `22/22`. The harness now uses the system IME state and a real touch save path; production
  medication behavior was unchanged.
- Latest current-source complete Phone regression — PASS, XML results report `203` tests with
  `0` failures, `0` errors, and `5` conditional skips on Pixel 7 API 35 `emulator-5556`.
  This supersedes the earlier `201`-case count; the five skips remain the documented upgrade,
  RepairTool, and compact-device gates.
- Current-source boundary regression after KTX and compile-time system-color cleanup — PASS,
  `22/22` targeted Widget, Widget Receiver, and Wear action instrumentation tests on Phone
  `emulator-5556`.
- Latest current-source `MedicationRecordsScreenTest` rerun after the date/time picker and
  exposed-dropdown API compatibility fixes — PASS, `12/12` on Phone `emulator-5556`. A prior
  short-class-name invocation failed only at test-class discovery; the fully-qualified rerun
  completed successfully.

Lint currently reports 0 errors for both modules: App has 44 non-blocking warnings and the latest
Wear report has 6 non-blocking warnings. They cover dependency freshness, target/legacy API
compatibility, typography, launcher/icon compatibility, and Widget design constraints. The Compose
parameter-ordering, KTX call-site, version-catalog, and resource-reflection warnings were
removed without changing behavior; remaining warnings are not silently removed or suppressed by
this manifest.

## Remaining lint warnings — reviewed, not defects

- Dependency freshness (`AndroidGradlePluginVersion`, `GradleDependency`, and
  `NewerVersionAvailable`) is intentionally deferred. A dependency sweep would be a separate
  compatibility change, not dead-code cleanup, and is outside the v1.5 behavior-equivalence
  boundary.
- `OldTargetApi` reflects the current target SDK 36 declaration; the project has no higher
  tested target in this release line. `ObsoleteSdkInt` points at the adaptive launcher resource
  directory; moving it to an unqualified directory was tried and failed the launcher resource
  contract test, so the required v26 path is retained.
- `IconXmlAndPng` is the notification compatibility XML/PNG pair, explicitly retained by the
  manifest. `IconLauncherShape` applies to the existing legacy launcher bitmaps, and
  `IconDuplicates` reflects the deliberate byte-identical Phone foreground/monochrome assets;
  both are covered by the launcher resource contract and visual review rather than removed as
  dead files.
- `SmallSp` is intentional for the compact RemoteViews layouts. Raising those values would
  alter the Widget density/fit contract and requires a separate visual approval.
- Wear's remaining `IconDuplicates` findings are the deliberate byte-identical foreground and
  monochrome layers reused across five densities; `ObsoleteSdkInt` is the required v26 adaptive
  icon resource qualifier for the minSdk-30 Wear module. The primary API 30+ Wear path resolves
  to the adaptive icon and its device rendering is covered by `WearLauncherIconResourceTest`.
  A trial move to unqualified `mipmap-anydpi` was rejected because the Wear emulator resolved
  the legacy PNG and the adaptive-resource test failed; the standard `-v26` path is retained.

The Kotlin compiler still reports four reviewed compatibility warnings: the Wear `Image.Builder`
resource API, three legacy directional Material Icons, and the old `ButtonGroup` overload in the
sublingual tier selector. The `ButtonGroup` replacement is not a drop-in signature change: the
new overload requires registering overflow-capable custom items and would change the selector's
interaction model unless its overflow behavior receives a separate UI contract and test. It is
therefore intentionally retained for behavior equivalence and is not suppressed as if it were
dead code.

The reviewed warning set contains no `UnusedResources`, `UseKtx`, `DiscouragedApi`, Compose
parameter-ordering, or resource-reflection findings. Therefore the remaining App 44 and Wear 6
warnings do not represent unresolved v1.5 cleanup defects.

The `Cleanup equivalence` acceptance row is PASS for the declared v1.5-D scope: the complete
Phone regression, App/Wear JVM suites, lint, and targeted boundary tests are recorded above, and
the reviewed manifest preserves the excluded persistence, migration, backup, PK, Wear protocol,
signing, and launcher-artwork boundaries. The Wear transport, Release identity, Phone launcher
visual approval, energy, and RC device matrix remain separate open release gates.
