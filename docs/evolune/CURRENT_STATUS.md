# Evolune Current Status

This document is the canonical quick reference for the current public release and development baseline.
Historical plans and phase reports remain evidence of earlier decisions but do not override this status.

## Current Release

- Stable version: [`v1.6.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0)
- Release date: 2026-09-10
- Release reference: immutable `v1.6.0` tag
- Previous sealed stable release: [`v1.5.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.5.0)
- Release downloads: signed Phone and Wear APKs attached to the v1.6.0 GitHub Release

v1.6.0 completed the Widget Gallery milestone. The release passed independent final review, signed
Phone/Wear artifact verification, in-place installation on a real Pixel 11 Pro and Galaxy Watch,
and owner acceptance on the real watch. See [v1.6 Acceptance](v1.6/V16_ACCEPTANCE.md) and the
[final release gate](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md).

## v1.6.0 Release Identity

| Target | Application ID | Version | Minimum API |
|---|---|---:|---:|
| Phone | `io.github.yingqiu0871.evolune` | `1.6.0 (101060000)` | 31 |
| Wear | `io.github.yingqiu0871.evolune` | `1.6.0 (1101060000)` | 30 |

Both release APKs use the persistent Evolune release certificate with SHA-256
`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`.
Debug builds use a separate `.debug` application ID suffix and signing identity.

## Shipped v1.6 Capabilities

- Four separate Phone Widget picker entries: Evolune-今日计划、Evolune-下一次服药、
  Evolune-当前 E2 and Evolune-E2 趋势.
- Per-widget appearance configuration with automatic/light/dark modes, Material You and eight
  preset color groups; responsive typography and contrast.
- A scrollable today-plan widget with occurrence-scoped confirmation, plus read-only next-dose,
  current-E2 and 48-hour historical/predicted concentration widgets.
- Three new Wear Tiles for next dose, today plan and current E2, plus the existing E2 curve Tile
  retained under its original component identity.
- Three Short Text Complications for next-dose time, current E2 and today completion.
- Wear App, Tiles and Complications share the Evolune naming, brand resources, tonal palette and
  round-screen-safe layout language.
- Wear occurrence actions include confirm and skip. Skip is persisted by exact plan, slot and
  scheduled time on Phone, cancels the matching reminder and suppresses delivery races without
  recording a dose.

## Authority and Compatibility Boundaries

Phone Room/domain/repository remains the only medication-data authority. Widgets and Wear use
derived presentation state or rebuildable caches. Wear actions are validated and applied by Phone;
Wear does not become a second source of truth.

The release preserves the old `DoseTileService` component, v1/legacy/action paths, PK mathematics,
Room schema, backup format and occurrence identity semantics. Phone and Wear private data remain
excluded from Android Auto Backup and device transfer; user-controlled JSON export/import is the
supported migration path.

Because v1.0 Wear used the old application ID `io.github.yingqiu0871.evolune.wear`, that one old
package cannot update in place to the shared v1.1+ identity. See
[Wear v1.1 Identity Migration](WEAR_V11_MIGRATION.md). This does not apply to v1.1–v1.5 upgrades.

## Verification Summary

- Signed Release build: 152 tasks executed, `BUILD SUCCESSFUL`.
- Current-source Debug gate: 863 JVM tests with zero failures/errors/skips; Phone/Wear Lint zero errors;
  both Debug and instrumentation APKs built.
- Pixel 11 Pro receiver regression: 7/7 tests passed, including old-alarm compatibility, skipped
  occurrence suppression and malformed new slot identity rejection.
- Final signed Phone/Wear candidate passed APK Signature Scheme v2 verification with one signer.
- Real Pixel 11 Pro and Galaxy Watch accepted `adb install -r`; first-install timestamps and existing
  data/components were retained, device APK hashes matched the candidate, and cold launch had no
  fatal exception or ANR keywords.
- Project owner completed manual real-watch Tile/Complication acceptance. Independent final review
  reported P0/P1/P2 clear; optional AlarmManager stress coverage remains P3.

## Current Limitations

- Health Connect is not implemented.
- Google cloud backup or cloud synchronization is not implemented.
- Auto Backup/device transfer intentionally excludes private app data.
- Tracked Date, personalized calibration/PK 2.0 and SQLCipher remain deferred or unimplemented.
- v1.7 Optional CPA PK Curve remains a candidate only; it is default-off and requires independent
  scientific and source review before implementation or release.

## Provenance

Explicit permission was received from the `HRT-Recorder-PKcomponent-Test` author on 2026-08-14 for
Evolune to use, copy, modify, port, further develop and distribute the relevant author-owned or
authorizable work under the MIT License. Attribution is preserved. This scoped permission does not
relicense unrelated third-party contributions. See [Source Provenance](../SOURCE_PROVENANCE.md),
[NOTICE](../../NOTICE) and [Third-Party Notices](../../THIRD_PARTY_NOTICES.md).

`PK_PERMISSION_STATUS = EXPLICIT_PERMISSION_GRANTED`

`PK_PERMISSION_SCOPE = AUTHOR_OWNED_OR_AUTHORIZABLE_RIGHTS`

`PK_PROVENANCE_RISK = RESOLVED_WITH_ATTRIBUTION_REQUIREMENT`

## Next Milestone

- `v1.6.0`: Widget Gallery — **CLOSED / RELEASED**.
- `v1.7`: Optional CPA Pharmacokinetic Curve — **CANDIDATE / NOT STARTED**.

See the [Roadmap](ROADMAP.md) for the historical release sequence and future boundaries.
