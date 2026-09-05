# Evolune v1.5 — Acceptance Matrix

## Status

Final v1.5.0 release matrix. Rows marked `PASS` have recorded evidence; `OPEN` rows remain
release gates. `SKIPPED_BY_OWNER` is reserved for an explicit, documented owner waiver and is
not evidence that the skipped behavior is improved. This matrix starts from the sealed `v1.4.0`
baseline.

Device serials are scoped to the capture timestamp. The emulator service reused ports between
runs: earlier records that identify `emulator-5554` as a Phone capture are historical and were
made while that port hosted a Phone AVD; the current mapping is recorded separately below. New
evidence must identify the AVD model as well as the serial and must not infer device type from a
reused port alone.

## Exit rule

v1.5 is release-ready only when every applicable row is `PASS` or has an explicit documented
owner waiver, the behavior-equivalence audit for cleanup is complete, the Phone/Wear device
matrix is recorded, and no new P0/P1 stability issue remains.

## Matrix

| Area | Required coverage | Evidence to record | Status |
|---|---|---|---|
| Fresh install | Install, first launch, required disclosures, tutorial, Home entry | Clean-install test record and APK identity | PASS |
| Upgrade | v1.4.0 install/data upgraded to the v1.5 candidate without losing local data or creating an incorrect onboarding state | In-place upgrade record with before/after data checks | PASS |
| Lifecycle | Process recreation, force-stop, reboot, cold launch, and restored navigation state | Automated lifecycle tests plus device smoke | PASS |
| Time | Cross-midnight, date change, time-zone change, DST transition | Deterministic clock tests and device confirmation | PASS |
| Medication data | Plan create/edit/delete, dose record/edit/delete, reminders, notification actions | Regression suite and persisted-state assertions | PASS |
| Phone Widget | Multiple instances, configuration isolation, refresh, launcher recreation, empty/error states | Widget instrumentation and launcher smoke | PASS |
| Wear | Disconnect/reconnect, stale/pending/failed states, retry, Data Layer delivery, Phone authority | Phone/Wear device matrix and transport logs | PASS |
| Backup/restore | Valid restore, preview, malformed/incompatible data, failure recovery | Backup/restore regression evidence | PASS |
| Startup/performance | Cold/warm startup, PK, Room, Compose, Widget, Wear and background work baseline | Repeatable measurements before/after change | PASS |
| Energy/background | WorkManager, polling, wakeups, Flow collection, refresh frequency, Wear active/background work | Battery/background observation record | SKIPPED_BY_OWNER |
| Static/quality | Static checks, test isolation, error handling, log boundaries | CI output and audit notes | PASS |
| Cleanup equivalence | Removed code/resources/dependencies/state/compatibility layers do not alter behavior | Before/after tests and reviewed cleanup manifest | PASS |
| Phone launcher Logo | Overall foreground mark and moon are visually smaller than v1.4.0; aspect ratio, center, safe zone, density variants, monochrome and launcher masks remain coherent | Before/after screenshots on Phone launcher plus owner visual approval | PASS |
| Wear launcher Logo | Existing mark uses adaptive layers and renders without black outer corners or an unintended white wrapper on the Wear launcher | Resource contract, Wear device test, and post-reinstall launcher screenshot | PASS |
| Release identity | Phone/Wear package IDs, `versionName`, version codes, signing and non-debuggable release state | Build metadata and signing verification for the current candidate | PASS |
| RC gate | No new P0/P1 stability issues; release notes, tag and APK hashes match the candidate | Final RC checklist and immutable release record | PASS |

## Evidence added — 2026-09-04 to 2026-09-05

- Fresh-install smoke: the v1.5.0 Phone Release APK was installed on isolated
  `emulator-5554` with no prior Release package. The five disclosure steps were completed,
  the optional feature tutorial was skipped through its supported path, and Home was reached.
  The Release process remained alive with no fatal or AndroidRuntime error keywords.
- Disclosure gating: on steps 3 and 4, the `下一步` action was disabled while
  `我已阅读并理解` was unchecked and became available only after the checkbox was selected.
- Release identity: Phone/Wear `aapt2` metadata reported package
  `io.github.yingqiu0871.evolune`, version `1.5.0`, and version codes `101050000` /
  `1101050000`. Both installed packages were non-debuggable and passed `apksigner` verification
  with the Release certificate (`b9b6b9552fa4c7b656936d4c3aeb71c1229aa17c393337719bc8d0e07edaab08`).
- Current-source identity policy: `validateEvoluneIdentityAndVersioning` passed on the working
  tree, confirming version `1.5.0`, Phone code `101050000`, Wear code `1101050000`, shared package
  identity, and the documented version-code ranges. This does not replace final Release signing
  and artifact verification.
- Final current-source Release artifact hashes from the 2026-09-05 rebuild: Phone
  `app-release.apk` SHA-256
  `6B125296D3C7B32F568891726F46639B261A1A0D4F85EE07013BC4FE336A7E2A`; Wear
  `wear-release.apk` SHA-256
  `C750EC710520C02D75255B094BB148345B0F9F647440B94CB1625F035453E372`.
  Both artifacts report package `io.github.yingqiu0871.evolune`, version `1.5.0`, the expected
  Phone/Wear version codes, non-debuggable Release state, and successful APK signature
  verification with certificate SHA-256
  `b9b6b9552fa4c7b656936d4c3aeb71c1229aa17c393337719bc8d0e07edaab08`.
- Wear smoke: the v1.5.0 Wear Release APK was installed on `emulator-5556` and reached the
  normal `未连接手机，显示上次数据` waiting state without fatal or AndroidRuntime errors.
- Current-source regression: `:experience-core:test`, `:app:testDebugUnitTest`,
  `:wear:testDebugUnitTest`, `:app:lintDebug`, `:wear:lintDebug`, and both Debug APK builds
  passed after the cleanup and quality fixes. Lint reports 0 errors and 0 `UnusedResources`
  findings; App has 44 non-blocking warnings and the pre-Wear-icon-fix report had 5, limited to
  dependency freshness, target/legacy API compatibility, launcher/icon contracts, and Widget
  typography constraints.
  The Compose parameter-ordering, KTX call-site, version-catalog, and resource-reflection
  warnings were removed without changing behavior.
- Current-source Phone boundary regression: `WidgetRemoteViewsTest`,
  `ReceiverWidgetProductionCutoverTest`, and `WearProductionCutoverTest` passed `22/22` on
  Phone `emulator-5556` after the KTX and compile-time system-color cleanup. Widget appearance
  persistence, URI identity, Widget replay, and Wear action contracts remained green.
- Complete Phone connected regression after the two-phase upgrade-test isolation fix:
  `:app:connectedDebugAndroidTest` finished `205` tests with `0` failures and `5` conditional
  skips on `emulator-5554`. The skips are the intentionally gated two-phase upgrade methods,
  the pre-existing RepairTool migration case, and the compact-device Foldable cases.
- Current-source complete Phone regression after the KTX/resource cleanup and before the final
  one-line `system_neutral1_10` mapping correction: `:app:connectedDebugAndroidTest` finished
  `201` tests with `0` failures and `5` conditional skips on Phone `emulator-5556`.
- Historical final-source Phone regression after the mapping correction completed `201` tests
  with `5` conditional skips and one Compose timing timeout in
  `MedicationRecordsScreenTest.createSuccessClosesEditorAfterContractInsert`; the other `200`
  tests passed. The timeout was traced to the test harness losing a save tap while the emulator
  IME was transitioning, not to production persistence or UI-event handling.
- Final-source complete Phone regression after the IME harness fix: `:app:connectedDebugAndroidTest`
  completed `201` test cases with `0` failures and `5` conditional skips on Phone
  `emulator-5556`. `MedicationRecordsScreenTest` passed `12/12`; the harness now waits for the
  system IME state, dismisses it before save, and verifies the save through a real touch path.
  This supersedes the historical timeout above; the final-source Widget/Wear boundary set also
  passed `22/22`.
- Latest current-source complete Phone regression on 2026-09-05: the XML result for
  `:app:connectedDebugAndroidTest` on Pixel 7 API 35 (`emulator-5556`) reports `203` tests,
  `0` failures, `0` errors, and `5` conditional skips. This supersedes the earlier `201`-case
  count; the five skips remain the documented upgrade, RepairTool, and compact-device gates.
- Cleanup equivalence: the reviewed v1.5-D manifest is now closed for its declared scope.
  The latest current-source Phone regression passed `203` test cases with `0` failures and `5`
  conditional skips, App JVM passed `656/656`, Wear JVM passed `64/64`, Experience Core passed `80/80`,
  lint passed with `0` errors, and the targeted Widget/Wear boundary set passed `22/22`.
  Excluded boundaries (persistence,
  migration, backup, PK, Wear protocol, signing, and launcher artwork) retain their separate
  acceptance rows and were not changed by cleanup.
- Current emulator identity check: the active ADB mapping reports `emulator-5554` as
  `sdk_gwear_x86_64` (Wear) and `emulator-5556`/`emulator-5558` as Phone devices. A direct
  pre-fix 201-test run on Phone `emulator-5556` had `200` passes and one timeout in the then
  existing `MedicationRecordsScreenTest.rapidDoubleTapInvokesOneInsert`; that historical result
  is superseded by the final 0-failure run above.
  The earlier multi-device attempt on `emulator-5554` is invalid as Phone evidence and is not
  counted.
- Current-source device tests: `ReceiverLifecycleInstrumentationTest` 5/5,
  `OnboardingFlowScreenTest` 3/3, and `DisclosuresScreenTest` 2/2 passed on isolated
  Phone `emulator-5554`. A Release cold-launch/force-stop/relaunch smoke also passed with
  the package visible and no fatal, AndroidRuntime, or ANR keywords in the process log.
- In-place package upgrade: on isolated Phone `emulator-5554`, the v1.4.0 Debug package
  (`101040000`) was prepared with one plan, one scheduled slot, one dose event, body weight
  `62.4 kg`, dark theme, and completed onboarding. Installing the v1.5.0 Debug candidate
  (`101050000`) with data preserved passed `V15InPlaceUpgradeDeviceTest.verifyV15State` 1/1:
  all three rows, the v3 database version, settings, onboarding state, and SQLite integrity
  check matched after upgrade. This closes the Upgrade row; it is separate from final Release
  signing verification.
- Time-boundary device regression: `V15TimeBoundaryDeviceTest` passed 1/1 on the current
  Phone candidate, exercising cross-midnight expansion, Europe/Paris versus Asia/Shanghai
  local-time identity, DST spring-gap resolution, and DST fall-overlap resolution on the
  Android runtime. The shared deterministic JVM occurrence tests also remain green.
- Home schedule-boundary regression: `HomeScheduleBoundaryTest` passed after the Home refresh
  fix. A daily plan at `00:05` observed at `23:59` resolves its next fork point on the following
  local date, so the realtime refresh boundary does not remain pinned to a stale day.
- Background observation: the schema-v1.5c harness recorded valid 30-second screen-off
  v1.4.0→v1.5.0 Debug captures on the same Phone. Alarm lines stayed at 0, Wake Lock lines at
  0, Job scheduler lines at 4, and process CPU was 0.13%→0.10%. This is diagnostic evidence
  only; physical battery discharge and the Wear active/background matrix remain unexercised.
  Per the owner decision for this RC, the Energy/background row is `SKIPPED_BY_OWNER`; no
  battery improvement is claimed.
- Current-source background spot capture: the schema-v1.5c harness recorded a `VALID` 30.010 s
  screen-off window for the current v1.5 Debug package on Phone `emulator-5556`. The device was
  `Asleep` at both boundaries, the app PID stayed `14595`, CPU delta was `18` ticks at 100 Hz
  (`0.60%` average process CPU), Alarm lines were `0`, and the four Job lines remained scheduler
  accounting with `TopAppTimer` inactive. The capture also recorded `worktreeDirty=true`, so it
  is explicitly not a clean-commit artifact. This strengthens the diagnostic observation only;
  it does not close the physical battery or Wear active/background requirements.
- Current-source color-role/device regression: `ColorRoleConformanceTest` passed 3/3 on
  isolated Phone `emulator-5554`, covering light/dark/OLED title contrast, OLED theme
  selection, and theme-icon vertical centering. The test now establishes completed
  onboarding and enters the nested appearance screen through stable test tags; production
  theme behavior was unchanged.
- Current-source data/device regression: the selected migration matrix, Room repository,
  restore persistence, and Widget RemoteViews classes completed 78/78 on isolated Phone
  `emulator-5554`.
- Current-source medication UI regression: `MedicationPlansScreenTest` passed 14/14 and
  `MedicationRecordsScreenTest` passed 12/12 on isolated Phone `emulator-5556`. The record
  suite's cross-image IME cleanup assertion was made tolerant of emulator images that omit
  `mImeWindowVis`, and save actions are synchronized with the system IME; no production
  medication UI behavior was changed for these test-only fixes.
- Latest current-source rerun after the date/time picker and exposed-dropdown API compatibility
  fixes: `MedicationRecordsScreenTest` passed `12/12` on Phone `emulator-5556`. The initial
  short-class-name invocation failed to load the test class at the Runner boundary and was
  discarded; the fully-qualified invocation completed successfully.
- Wear launcher icon defect: inspection of the prior five Wear `ic_launcher.png` density assets
  found opaque black corner pixels (`ARGB(255,0,0,0)`) and no adaptive icon declaration. The fix
  adds `wear/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, reuses the current v1.5 Phone
  foreground/background/monochrome layers, and makes the Wear legacy/background outer black
  pixels transparent. The cross-density `LauncherIconResourceTest` now locks the four-corner and
  no-opaque-near-black invariant and passed `11/11`; `WearLauncherIconResourceTest` and
  `WearManifestContractTest` also passed on Wear `emulator-5554`. After uninstall/reinstall, the
  actual `最近用过` launcher view showed a circular pink/blue gradient with the white crescent,
  with no black corners or white wrapper. Screenshot evidence: `wear-launcher-after.png`.
- Latest Wear lint after the icon fix reports 0 errors and 6 reviewed warnings: five intentional
  foreground/monochrome duplicate-asset findings and one required API-26 adaptive-resource
  qualifier finding; the old `IconLauncherShape` findings are gone from the primary icon path.
- Current Phone launcher spot check: the v1.5.0 Debug package was installed on Phone
  `emulator-5556` and the Pixel Launcher app drawer rendered the reduced gradient/crescent mark
  without clipping. The full launcher capture is `phone-launcher-after.png`; multiple historical
  Evolune variants were co-installed in that capture, so it is technical evidence only and not a
  clean-package visual sign-off. On 2026-09-05 the owner completed the Phone launcher visual
  review and approved the reduced, centered mark; this closes the Phone launcher Logo row.
- Current-source medication persistence/actions: `MedicationPlanProductionCutoverTest` passed
  3/3, `DoseEventProductionCutoverTest` passed 2/2, and
  `ReceiverLifecycleInstrumentationTest` passed 5/5 on the same Phone. Together they cover
  plan create/edit/delete and reminder scheduling/cancellation, dose create/edit/delete/import
  and conflict handling, notification actions, reschedule, and Widget receiver delivery.
- Current-source Widget/action device regression: `WidgetRemoteViewsTest` and
  `ReceiverWidgetProductionCutoverTest` passed 17/17 on isolated Phone `emulator-5554`.
  Coverage includes per-instance appearance isolation, exact configuration targeting, stable
  occurrence identities, refresh/action replay, stale cleanup, storage/conflict failures, and
  concurrent authoritative writes.
- Phone Launcher Widget smoke: Pixel Launcher on `emulator-5554` hosted two simultaneous
  Evolune Widget instances from the v1.5 Debug provider. `dumpsys appwidget` reported two
  provider entries under the launcher host; after force-stopping and relaunching Pixel
  Launcher, the same two instances remained and the UI hierarchy contained two
  `LauncherAppWidgetHostView` and two `widget_root` nodes. The live instances also exercised
  the empty-state rendering. This closes the Phone Widget row together with the instrumentation
  coverage above.
- Current-source navigation/device regression: `FeatureTutorialNavigationTest` and
  `SyncAndBackupNavigationTest` passed 4/4 combined on isolated Phone `emulator-5554`. The
  tutorial navigation suite includes Activity recreation and passed 2/2. After a real emulator
  reboot, the v1.5.0 Release package cold-launched again with package visibility and process-log
  checks passing.
- Current-source backup/device regression: `SyncAndBackupScreenTest` passed 4/4, covering the
  sync/backup settings rows, existing Mahiro import/export actions, Google Drive backup/restore
  entry actions, and numbered backup selection. `PhysicalBackupPerformanceTest` passed 6/6,
  including the 600,000-iteration PBKDF2 worker benchmark and a 100-plan/1,000-slot/10,000-event
  native encode/decode/preview pipeline. The measured KDF median was 1,566.302 ms; the large
  history pipeline completed encryption in 4,616.219 ms and decrypt-to-preview in 2,604.566 ms,
  with matching counts and no OOM/process-death/ANR.
- Backup/restore JVM coverage: `EvoluneBackupCodecTest` 19/19, `BackupRestoreCoordinatorTest`
  18/18, and `B2RestoreTransactionTest` 13/13 passed. Together they cover valid restore and
  preview, wrong-passphrase/tamper and malformed/incompatible input rejection, atomic rollback,
  journal recovery, and failure-safe cloud behavior; this closes the Backup/restore row.
- Current-source Wear device regression: `WearManifestContractTest` 1/1 passed on isolated
  Wear `emulator-5554`.
- Current-source Wear runtime smoke: the v1.5.0 Debug APK launched on Wear
  `emulator-5554`, showed the expected disconnected/cache state (`未连接手机，显示上次数据`),
  and its process log contained no fatal or AndroidRuntime keywords. A 30-second screen-off
  observation kept the process stable with zero package alarm matches and one process CPU tick;
  the emulator entered `Dozing` rather than `Asleep`. This is diagnostic evidence only and does
  not close the Wear Data Layer reconnect/delivery or physical energy requirements.
- Live Wear Data Layer probe: `WearDataLayerDeviceProbeTest` compiled successfully and was run
  on the current Wear emulator. It skipped with the explicit reason that no paired Phone/Wear
  node was available; the Phone `dumpsys companiondevice` association list is empty. This is
  recorded as missing environment evidence, not a pass, and keeps the Wear row open.
- Current-source Wear instrumentation: `:wear:connectedDebugAndroidTest` completed `3` tests
  with `0` failures and `1` conditional skip on Wear `emulator-5554`. The manifest and adaptive
  launcher-icon contracts passed `1/1` each; the only skipped case was the same unpaired-node
  Data Layer probe.
- Live Phone/Wear Data Layer RC probe on 2026-09-05: Phone `Pixel 11 Pro`
  (`adb-67181FDKX002MX-SC0jva._adb-tls-connect._tcp`) and Wear `Galaxy Watch SM-L500`
  (`192.168.31.122:41601`) were both connected and running the current v1.5.0 Debug variants.
  `WearDataLayerDeviceProbeTest` passed `1/1`: Wear discovered the Phone node
  `Pixel 11 Pro/be5c95ec`, received a newer authoritative dashboard, ended in
  `CONNECTED` state, and had `pendingSince=0`. The initial failed attempt was traced to the
  Phone Debug package being removed by the preceding instrumentation task; after reinstalling
  the current Phone Debug package, the probe passed without a production-code change.
- Final Release device smoke on 2026-09-05: the current Phone Release APK installed successfully
  on the connected Pixel 11 Pro and reported `1.5.0 / 101050000`; explicit `MainActivity` cold
  launch completed with process alive and `0` fatal/AndroidRuntime/ANR log keywords. The current
  Wear Release APK installed successfully on the connected Galaxy Watch SM-L500 and reported
  `1.5.0 / 1101050000`; explicit `WearAppActivity` cold launch completed with process alive and
  `0` fatal/AndroidRuntime/ANR log keywords. The Wear wireless address changed during the
  session, so its capture-time ADB serial is authoritative rather than the original pairing
  address.
- Final Release performance capture on 2026-09-05: the current Phone Release package on
  `emulator-5556` produced `VALID` foreground Home evidence with `10/10` cold starts (median
  `337 ms`, mean `333.4 ms`), `10/10` warm starts (median `56.5 ms`, mean `54.5 ms`, stable
  process), and a 30.015-second Home window. The window rendered `35` frames with p99 frame time
  `19 ms`, `23` janky frames, and `1.90%` process CPU; Activity and window focus were confirmed
  at both boundaries. Together with the recorded v1.4.0→v1.5.0 PK, Room, Compose, Widget,
  startup, and Wear/background comparisons, this closes the Startup/performance row. The
  measurement was taken from the current dirty worktree and is tied to the separately hashed
  Release APKs above.
- Wear background before/after: the v1.4.0-debug and v1.5.0-debug APKs were measured on the
  same Wear `emulator-5554` with valid 30.008-second screen-off windows. Both reported explicit
  `DOZE_SUSPEND`, stable process PIDs, `0` Alarm lines, `4` scheduler-accounting Job lines,
  no active application Wake Lock, and `0.00%` process CPU at the tick resolution. This closes
  the Wear emulator background observation only; Data Layer delivery and physical battery
  discharge remain open.

- Current-source Home performance capture: the schema-v2 harness reported `VALID` for both an
  empty and a populated `PK-STEADY` Home window on Debug Phone `emulator-5554` after the
  measured state-boundary change. Both windows asserted the target Activity was resumed and
  focused at the start and end, kept the screen awake, and collected cumulative process CPU
  ticks. The empty run recorded `HomeScreenContent=3` compositions and `1` frame over 60 s;
  the populated run recorded `HomeScreenContent=3`, `ConcentrationChart=1`, `66` frames and
  `2.37%` process CPU, with the visible concentration changing from `2341.1` to `2341.0 pg/mL`.
  This supports the targeted recomposition reduction while preserving the realtime card and
  chart; frame/jank and end-to-end CPU are not claimed as improved.
- PK runtime comparison: `V15PkPerformanceDeviceTest` passed 1/1 on the same Phone with the
  fixed EMPTY/STEADY/DENSE datasets against v1.4.0 Debug and v1.5.0 Debug. Repeated outputs
  matched; the measured medians were `0.0071/0.0205 ms` (EMPTY), `222.1943/194.3778 ms`
  (STEADY), and `2329.0368/2220.1271 ms` (DENSE). These are diagnostic one-session timings,
  not a global performance claim; the Startup/performance row remains open for the required
  cold/warm, Widget, Wear, and background coverage.
- Current-source startup/performance capture on Phone `emulator-5556`: the schema-v2 harness
  completed `20/20` cold starts (median `1411.5 ms`, mean `1416.8 ms`) and `20/20` warm starts
  using a uniform `WaitTime` metric (median `55.5 ms`, mean `52.6 ms`, stable process PID). The
  same run recorded a valid focused/resumed 60.015-second empty Home window with one frame,
  p50/p90/p95/p99 of `17/17/17/17 ms`, `0.28%` process CPU, and `HomeScreenContent=3`.
  This adds reproducible cold/warm startup evidence; the broad startup/performance row remains
  open for the required populated, Widget, Wear, and before/after release comparison.
- Same-device startup comparison on Phone `emulator-5558` completed valid v1.4.0-debug →
  v1.5.0-debug in-place measurements with preserved empty Home data. Cold startup moved from
  `1294.5 ms` to `1281 ms` median; warm `WaitTime` moved from `13 ms` to `14 ms` median; the
  process CPU window moved from `0.50%` to `0.23%`. Both builds had 1 frame in the 60-second
  empty Home window and stable warm PIDs. This confirms no measured empty-Home startup
  regression on the same device, but does not close the broad row's populated/Widget/Wear and
  Release-comparison requirements.
- Same-device populated Home comparison on Phone `emulator-5558` completed with the v1.4.0
  upgrade fixture preserved through the v1.5 install. Both captures were `VALID` for 60 seconds
  with 20/20 cold starts and 20/20 warm starts. Cold medians were `1335/1342 ms`, warm
  `WaitTime` medians `12/13 ms`, and both rendered 66 frames. v1.5 recorded `57` janky frames
  versus `66` in v1.4, frame p50/p90/p95/p99 of `18/32/34/34 ms` versus `34/36/48/85 ms`,
  and process CPU `1.93%` versus `2.65%`; Home/Chart diagnostics were `3/1` in v1.5. The
  visible `0.1 pg/mL` result remained stable and `verifyV15State` passed `1/1`. This supports
  the targeted Home state-boundary change, but remains a one-session dirty-source comparison
  and does not close the broad Startup/performance row's Widget, Wear, background, or Release
  requirements.
- Room repository runtime comparison on the same Phone `emulator-5558` completed with the fixed
  three datasets, two warm-ups, and seven measured passes per operation for both v1.4.0-debug and
  v1.5.0-debug. `pkFetch` medians were `0.901/1.200 ms` (EMPTY), `3.567/4.057 ms` (STEADY), and
  `7.539/8.091 ms` (DENSE); enabled-plan aggregate medians were `1.559/1.577 ms`, `2.060/2.092
  ms`, and `2.357/2.264 ms`. Outputs matched on every pass. This confirms the Room contract and
  fixed-shape behavior remain intact, but the one-session latency sample does not justify a Room
optimization or close the broad Startup/performance row.
- Widget refresh data-path measurement: `V15WidgetPerformanceDeviceTest` passed `1/1` on Phone
  `emulator-5558` with the fixed EMPTY/STEADY/DENSE datasets, two warm-ups, and seven measured
  passes. Snapshot-load medians were `4.3901/7.6618/14.0526 ms`; UI-model mapping medians were
  `0.0168/0.0233/0.0363 ms`. Snapshot and UI-model outputs matched on every pass. This adds the
  missing Widget data-path observation, while launcher-host rendering, clean Release comparison,
  Wear, and physical-device evidence keep the broad Startup/performance row open.
- Wear publication boundary audit: `currentTimeH` is a one-second UI clock stream and is not part
  of the PK calculation trigger or either Phone-to-Wear publication effect's key set. The current
  source therefore has no evidence of a per-second Wear publish loop; no speculative transport or
  energy production change was made. Unpaired-node delivery and physical battery evidence remain
  open as required by the Wear and Energy/background rows.

Per owner decision for this RC, the Energy/background comparison is explicitly
`SKIPPED_BY_OWNER`; this is a release waiver, not a PASS and does not support a battery-life
improvement claim. The physical battery/Wear active-background comparison remains unverified.

The final current-source Release build is signed, metadata-verified, hashed, installed on both
connected real devices, and cold-launched successfully. Release identity,
Startup/performance, Phone launcher Logo, and Wear transport rows are closed. The merged main
commit carrying this matrix is the immutable `v1.5.0` release record: it is tagged `v1.5.0`, and
the GitHub Release publishes the matching APK hashes listed above. The Energy/background
comparison is covered only by the explicit `SKIPPED_BY_OWNER` waiver above.

## Preserved boundaries

- Legal/disclosure/tutorial state remains separate from Room, settings restore, and backup.
- Room remains the medication-data authority.
- Phone remains the Wear authority.
- Existing PK calculations, backup format, Widget action identity, and Wear protocol remain
  behaviorally compatible unless a separately approved v1.5 defect fix requires otherwise.

## Residual risk register

Any row that cannot be exercised must state the unavailable environment, the compensating
evidence, the owner decision, and whether the gap blocks release. “Not exercised” is not a pass.
