# Evolune v1.2 HC4 Foreground Health Connect Weight Sync

## Scope and acceptance boundary

HC4 replaces the HC2 one-shot preview/use interaction with a foreground-only
Health Connect weight synchronization path. The implementation is limited to
read-only `WeightRecord` observations and does not add background Health
Connect access, Health Connect writes, WorkManager, a foreground service, boot
sync, medication-data synchronization, Drive/Auth changes, or backup payload
changes.

`SettingsDataStore.bodyWeight` remains the authoritative local weight. The
existing `HRTViewModel` and pharmacokinetic calculation path continue to
observe that value; Health Connect is only an observation source that may
update it under the freshness rules below.

## Settings information architecture

The Settings home contains one `Health Connect 同步` entry. It opens an
independent Health Connect Sync page with these sections:

- connection status;
- `体重同步` switch, disabled by default;
- permission management and reauthorization actions;
- a disabled `用药数据同步` placeholder, deferred to v1.8 or later.

The former inline `读取 Health Connect 体重` / preview / `使用此体重` controls
are removed. There is no manual adoption button in HC4.

## Enable and permission flow

1. The user turns on `体重同步`.
2. Evolune checks provider availability and the current read permission.
3. If permission is missing, Evolune emits exactly one Activity Result
   permission request event for that enable attempt and leaves the persisted
   switch off.
4. After a granted result, Evolune persists the switch as enabled and performs
   one immediate read.
5. A denial leaves the switch off and does not retry or open another dialog
   without a new user action.
6. The page can open the official Health Connect manage-data entry point and
   offers reauthorization when an enabled sync later observes revoked
   permission.

No permission request is emitted by the silent foreground path.

## Foreground trigger and state machine

The Activity invokes the coordinator from `onStart()`. This is the only
automatic trigger in HC4. Each foreground check is serialized by a coroutine
`Mutex`, so overlapping starts cannot interleave availability, permission,
read, and adoption operations.

The coordinator exposes stable UI states:

`DISABLED`, `CHECKING`, `SYNCING`, `CONNECTED`, `PERMISSION_REQUIRED`,
`UNAVAILABLE`, `UPDATE_REQUIRED`, `NO_DATA`, and `ERROR`.

When sync is disabled, foreground entry performs no provider call. When sync
is enabled, the silent path checks availability and permission, reads the
latest observation within the provider's bounded window, and never launches a
permission request.

## Freshness and persistence rules

The following fields are added to SettingsDataStore and are not part of the
existing B1 backup wire model:

- `healthConnectWeightSyncEnabled: Boolean`, default `false`;
- `lastHealthConnectWeightKg: Double?`;
- `lastHealthConnectWeightAdoptedAt: Instant?`.

An observation is eligible only when its timestamp is strictly newer than
`lastHealthConnectWeightAdoptedAt`. An equal or older observation is ignored,
including after a later manual local weight edit; therefore an old Health
Connect record cannot overwrite the manual value. A newer observation updates
the authoritative local `bodyWeight` and the Health Connect metadata in one
DataStore edit. If the newer observation already equals the local weight, only
the Health Connect metadata is advanced, preserving the same local authority
without a redundant body-weight write.

The metadata watermark is intentionally outside `replaceSettings()` and the
B1 backup conversion. Backup restore therefore continues to restore only the
pre-existing B1 settings fields and does not silently enable Health Connect or
replace its sync watermark.

## Explicit non-goals

- no `READ_HEALTH_DATA_IN_BACKGROUND`;
- no `WRITE_WEIGHT`, medical-data writes, or any Health Connect write API;
- no WorkManager, periodic work, foreground service, boot receiver, or
  background sync;
- no medication plan/record synchronization;
- no changes to Room, B1 payloads/golden fixtures, B2 restore semantics, B3
  Drive/Auth behavior, or the RC1 IME probe.

## HC4 verification target

Focused tests cover the disabled default, enable/permission grant and denial,
one-shot permission event, immediate read, foreground adoption, strict
watermark behavior across manual edits, same-value metadata advancement,
invalid/no-data handling, serialized foreground calls, and cancellation
propagation. The Compose screen test covers the Settings-page content,
disabled medication placeholder, permission entry, reauthorization state, and
last-sync/no-data presentation.

Owner-device validation remains a release-candidate gate. It must demonstrate
real provider availability, permission grant/revoke, a valid `WeightRecord`,
foreground adoption, restart persistence, PK recalculation from the adopted
local weight, and Activity recreation behavior. Automated or fake-provider
evidence does not close those device gates.
