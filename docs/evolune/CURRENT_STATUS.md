# Evolune Current Status

This document is the canonical quick reference for the current public release and development baseline. Historical plans and phase reports remain evidence of earlier decisions, but they do not override this status.

## Current Release

- Stable version: [`v1.1.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.1.0)
- Release date: 2026-08-22
- Published source baseline: `ea7bb92151ae73126703e54b6e48bf0fd5bdb09e`
- Release reference: the immutable [`v1.1.0` tag](https://github.com/YingQiu0871/Evolune/releases/tag/v1.1.0)
- Previous sealed stable release: [`v1.0.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.0.0)
- Published development baseline: `main` after the v1.1.0 release
- Current working development branch: `codex/v1.4-a-trust-permission-foundation` with
  uncommitted v1.4 pre-release changes
- Release downloads: signed Phone and Wear APKs are attached to the GitHub Release.

The `v1.0.0` tag and its published Release are sealed. Development after v1.0 continues from `main` without changing that tag.

## Current development milestone

The current development branch contains the accepted v1.4-A trust/permission foundation
and v1.4-B guided feature tutorial. v1.4 is implemented and independently reviewed but is
not yet a sealed public Release. See [v1.4 Acceptance](v1.4/V14_ACCEPTANCE.md) and the
[v1.4 design records](v1.4/).

## Public Identity

| Target | Application ID | Minimum API |
|---|---|---:|
| Phone | `io.github.yingqiu0871.evolune` | 31 (Android 12) |
| Wear | `io.github.yingqiu0871.evolune` | 30 |

The v1.1.0 stable release uses `versionName = 1.1.0`, Phone `versionCode = 101010000`, and Wear `versionCode = 1101010000`. Debug builds use a separate `.debug` application ID suffix and a different signing identity.

## v1.1 Milestone State

The Phone/Wear identity repair (previously the `v1.1/wear-identity-repair` line) is merged into `main`. Phone and Wear use installed application ID `io.github.yingqiu0871.evolune`; the Wear Kotlin namespace remains `io.github.yingqiu0871.evolune.wear`. The existing `/hrt/plans`, `/hrt/request-plans`, and `/hrt/dose-actions/<actionId>` wire formats remain unchanged.

Phone Widget Completion is **CLOSED / COMPLETE** and is published in v1.1.0. The v1.1
implementation and release preparation are published under the immutable `v1.1.0` tag. Owner,
physical-device and post-merge CI gates passed. `v1.0.0` remains the previous sealed stable release.

Wear now distinguishes waiting, disconnected, pending, failed, stale, authoritative no-plan, and ready states. These are derived transport/presentation states only. Phone Room v3 remains the source of truth, and replay, conflict, JSON v1, and persistence-before-side-effects behavior are unchanged.

Wear background delivery uses the filtered `DATA_CHANGED` manifest listener scoped to `wear://*/hrt/plans`; the deprecated `BIND_LISTENER` registration was removed release-safely, with connectivity derived on demand through `NodeClient.connectedNodes`.

Because v1.0 Wear was published as `io.github.yingqiu0871.evolune.wear`, it cannot update in place to the v1.1 Wear package. See [Wear v1.1 Identity Migration](WEAR_V11_MIGRATION.md) for the one-time uninstall/reinstall procedure. The Phone package and Phone data are unaffected.

## Shipped v1.0 Capabilities

- Local medication plans with stable, ordered scheduled-dose slots.
- Dose-event recording, editing, deletion, history, reminders, and notification actions.
- Estradiol pharmacokinetic estimation and chart visualization.
- Mahiro JSON v1 import/export compatibility.
- Room v3 persistence with exported schemas, strict v2-to-v3 migration safeguards, and a repair workflow for invalid legacy data.
- Domain models and Repository contracts separated from Room entities and DAOs inside the existing `app` module.
- Idempotent/conflict-aware writes and optimistic revision checks.
- A RemoteViews phone widget that shows concentration and enabled plans and supports quick recording.
- A Wear Tile/Data Layer flow that receives plan and concentration snapshots and submits one-tap dose actions.
- Wear action replay/idempotency/conflict handling. Accepted actions are persisted before widget refresh and before deletion of the exact acknowledged DataItem; failed acknowledgement remains retryable.
- Update checking against GitHub Releases.
- Explicit Android backup and device-transfer exclusions for Phone and Wear private application data.

## Current Data Model

`AppDatabase` is Room version 3 with `DoseEventEntity`, `MedicationPlanEntity`, and `ScheduledDoseSlotEntity`. Schemas 2 and 3 are tracked under `app/schemas/`.

`DoseEvent.occurredAt` is the authoritative `Instant`. A dose event also has a stable UUID, optional zone/local-date/slot metadata, source, status, revision, route, dose, ester, and extras. The legacy `timeH` representation remains only at compatibility and PK adapter boundaries.

`MedicationPlan.slots` is an authoritative ordered list. Each `ScheduledDoseSlot` has a stable UUIDv5 ID, plan ID, minute-precision local time, and contiguous position. The historical namespace string `io.github.yuninggu.evolune:scheduled-dose-slot` is an immutable persisted compatibility constant, not the current application identity.

## Current Limitations of the published v1.1 baseline

- Health Connect is not implemented.
- Google cloud backup or cloud synchronization is not implemented.
- Auto Backup and device transfer intentionally exclude private app data; user-controlled Mahiro JSON export/import is the available migration path.
- The Wear transport uses the shipped `/hrt/*` Data Layer payloads and does not yet provide a general versioned envelope, acknowledgement protocol, or full Wear application experience.
- The v1.1 RemoteViews widget completion is functional and closed; broader widget gallery surfaces remain future work.
- Tracked Date is deferred and has no current entity or product surface.
- Personalized calibration and PK 2.0 are not part of v1.0.
- The Room database is not encrypted with SQLCipher.

## Provenance

Explicit permission was received from the `HRT-Recorder-PKcomponent-Test` author on 2026-08-14 for Evolune to use, copy, modify, port, further develop, distribute source and compiled applications, and release corresponding derivative code under the MIT License, to the extent the author owns or is authorized to license the relevant rights. Source and contributor attribution is preserved.

`PK_PERMISSION_STATUS = EXPLICIT_PERMISSION_GRANTED`

`PK_PERMISSION_SCOPE = AUTHOR_OWNED_OR_AUTHORIZABLE_RIGHTS`

`PK_PROVENANCE_RISK = RESOLVED_WITH_ATTRIBUTION_REQUIREMENT`

This scoped permission does not relicense the entire upstream repository, grant rights on behalf of third-party contributors, or establish that the upstream repository contains a formal `LICENSE` file. See [Source Provenance](../SOURCE_PROVENANCE.md), [NOTICE](../../NOTICE), and [Third-Party Notices](../../THIRD_PARTY_NOTICES.md).

## Next Milestones

- `v1.1`: Phone Widget Completion — **CLOSED / COMPLETE**.
- `v1.2`: Google Integration & Data Continuity — **PLANNED, NOT STARTED**; Health Connect and Google backup are separately gated batches.
- `v1.3`: Wear OS Companion App.
- `v1.4`: Onboarding, Terms & Permission Guidance — **IMPLEMENTED / ACCEPTED, pre-release**.
- `v1.5`: Stability, Performance & Code Cleanup — **NEXT PLANNED MILESTONE**.
- `v1.6`: Widget Gallery.
- `v1.7`: Optional CPA Pharmacokinetic Curve, default off and gated by independent scientific review.

See the [Roadmap](ROADMAP.md) for authoritative detail.
