# Evolune v1.5 — Acceptance Matrix

## Status

Planning matrix. All rows are open until evidence is attached during v1.5 implementation and
RC validation. This matrix starts from the sealed `v1.4.0` baseline.

## Exit rule

v1.5 is release-ready only when every applicable row is `PASS`, the behavior-equivalence audit
for cleanup is complete, the Phone/Wear device matrix is recorded, and no new P0/P1 stability
issue remains.

## Matrix

| Area | Required coverage | Evidence to record | Status |
|---|---|---|---|
| Fresh install | Install, first launch, required disclosures, tutorial, Home entry | Clean-install test record and APK identity | OPEN |
| Upgrade | v1.4.0 install/data upgraded to the v1.5 candidate without losing local data or creating an incorrect onboarding state | In-place upgrade record with before/after data checks | OPEN |
| Lifecycle | Process recreation, force-stop, reboot, cold launch, and restored navigation state | Automated lifecycle tests plus device smoke | OPEN |
| Time | Cross-midnight, date change, time-zone change, DST transition | Deterministic clock tests and device confirmation | OPEN |
| Medication data | Plan create/edit/delete, dose record/edit/delete, reminders, notification actions | Regression suite and persisted-state assertions | OPEN |
| Phone Widget | Multiple instances, configuration isolation, refresh, launcher recreation, empty/error states | Widget instrumentation and launcher smoke | OPEN |
| Wear | Disconnect/reconnect, stale/pending/failed states, retry, Data Layer delivery, Phone authority | Phone/Wear device matrix and transport logs | OPEN |
| Backup/restore | Valid restore, preview, malformed/incompatible data, failure recovery | Backup/restore regression evidence | OPEN |
| Startup/performance | Cold/warm startup, PK, Room, Compose, Widget, Wear and background work baseline | Repeatable measurements before/after change | OPEN |
| Energy/background | WorkManager, polling, wakeups, Flow collection, refresh frequency, Wear active/background work | Battery/background observation record | OPEN |
| Static/quality | Static checks, test isolation, error handling, log boundaries | CI output and audit notes | OPEN |
| Cleanup equivalence | Removed code/resources/dependencies/state/compatibility layers do not alter behavior | Before/after tests and reviewed cleanup manifest | OPEN |
| Phone launcher Logo | Overall foreground mark and moon are visually smaller than v1.4.0; aspect ratio, center, safe zone, density variants, monochrome and launcher masks remain coherent | Before/after screenshots on Phone launcher plus owner visual approval | OPEN |
| Release identity | Phone/Wear package IDs, `versionName`, version codes, signing and non-debuggable release state | Build metadata and signing verification | OPEN |
| RC gate | No new P0/P1 stability issues; release notes, tag and APK hashes match the candidate | Final RC checklist and immutable release record | OPEN |

## Preserved boundaries

- Legal/disclosure/tutorial state remains separate from Room, settings restore, and backup.
- Room remains the medication-data authority.
- Phone remains the Wear authority.
- Existing PK calculations, backup format, Widget action identity, and Wear protocol remain
  behaviorally compatible unless a separately approved v1.5 defect fix requires otherwise.

## Residual risk register

Any row that cannot be exercised must state the unavailable environment, the compensating
evidence, the owner decision, and whether the gap blocks release. “Not exercised” is not a pass.
