# Evolune v1.2 HC3 Device Matrix

- Date: 2026-08-23
- App commit: `608406e4c79fabb3e93d2996820d7d1a31ecd9ca`
- Scope: Health Connect read-only weight observation; `SettingsDataStore` remains authoritative; adoption remains explicit.

## Evidence taxonomy

- `UNIT`: JVM/unit-test evidence.
- `INSTRUMENTATION`: Android instrumentation-test evidence.
- `EMULATOR`: manual behavior observed on an Android emulator.
- `PHYSICAL DEVICE`: manual behavior observed on a physical Android device.
- `NOT TESTED`: the scenario was not verified in the available environment.

| Device / API | Health Connect implementation | Scenario | Result | Notes |
|---|---|---|---|---|
| `evolune-hc3-api33` AVD / API 33 | External provider APK | Provider missing | PASS | Provider package was absent. Evolune showed `Health Connect 当前不可用`, kept local weight at 55.0 kg, and did not crash. |
| `evolune-hc3-api33` AVD / API 33 | External provider APK | Provider installed/current | NOT TESTED | No compatible Health Connect provider APK was available in this environment. |
| `evolune-hc3-api33` AVD / API 33 | External provider APK | Provider update required | NOT TESTED | An installed provider/update-required state was not constructible without the provider APK. Unit/API mapping coverage remains automated. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | Availability and first permission request | PASS | Framework controller was present; permission UI opened after the HC3 rationale entry points were added. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | Permission deny and retry | PASS | Denial returned to the app with permission-needed state, no preview, local 55.0 kg unchanged, and a later Read tap reopened the request. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | Permission grant and no record | PASS | Grant succeeded; Read showed no data for the recent 30-day window and did not write local weight. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | Permission revoke in Health Connect and re-read | PASS | Revoked from Health Connect app permissions; the next Read tap reopened the permission request. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | Valid recent record, preview, and explicit adoption | NOT TESTED | The emulator Health Connect store had no record and the available data-management UI did not provide a usable manual weight-entry path. |
| `evolune-hc3-api35` AVD / API 35 | Framework module | App force-stop/restart and local weight | PASS | After restart, Settings restored the local 55.0 kg value. Adopted-weight persistence could not be exercised without a valid HC record. |
| API 31 / 32 | External provider APK | Provider/permission/data matrix | NOT TESTED | No API 31/32 device or compatible provider was available. |
| Physical device | Device-dependent | Full HC3 matrix | NOT TESTED | No physical Android device was attached. |
| JVM / instrumentation automation | Fake gateway and package resolver | Cancellation, one-shot permission event, status mapping, and manifest entry points | PASS | Automated PASS is not a device PASS; see the HC provider and adoption tests plus the manifest instrumentation test. |

The initial API 35 permission request reproduced a platform contract failure (`App should support rationale intent, finishing!`). HC3 adds the required rationale/usage entry points and the request then opened normally. The initial API 33 missing-provider state also reproduced an incorrect update-required mapping; HC3 now distinguishes missing provider from update-required on API 31–33.

## v1.2 RC Owner-Device Acceptance Gate

These checks do not block B2 development. All of them block v1.2 RC publication until the RC owner records evidence for each applicable item.

- [ ] API31/32 Health Connect compatibility
  - provider installed/current
  - permission grant/deny
  - weight read where environment permits
- [ ] API33 provider installed/current
  - availability
  - permission
  - read/no-data behavior
- [ ] API31–33 provider update-required on an actual device/emulator
  - verify it is distinct from provider missing
- [ ] Physical Android device
  - Health Connect availability
  - grant
  - revoke
  - re-read after revoke
- [ ] Valid Health Connect `WeightRecord`
  - preview displays weight/time/source
  - preview alone does not change local weight
  - explicit “Use this weight” adopts it
- [ ] Adopted-weight restart persistence
  - adopt B
  - force-stop/restart Evolune
  - `SettingsDataStore` remains B
  - PK uses B after restart
- [ ] Activity recreation during/after permission request
  - no unsolicited second permission dialog
  - retry remains available after denial
