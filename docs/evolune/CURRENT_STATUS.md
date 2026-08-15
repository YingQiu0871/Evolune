# Evolune Current Status

This document is the canonical quick reference for the current public release and development baseline. Historical plans and phase reports remain evidence of earlier decisions, but they do not override this status.

## Current Release

- Stable version: [`v1.0.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.0.0)
- Release date: 2026-08-15
- Release commit: `780f167074cc737954c884d375825ef95db605c7`
- Current development branch baseline: `main`
- Release downloads: signed Phone and Wear APKs are attached to the GitHub Release.

The `v1.0.0` tag and its published Release are sealed. Development after v1.0 continues from `main` without changing that tag.

## Public Identity

| Target | Application ID | Minimum API |
|---|---|---:|
| Phone | `io.github.yingqiu0871.evolune` | 31 (Android 12) |
| Wear | `io.github.yingqiu0871.evolune.wear` | 30 |

Both targets use `versionName = 1.0.0` and `versionCode = 10060` in the stable release. Debug builds use a separate `.debug` application ID suffix and a different signing identity.

## v1.1 Development State

The `v1.1/wear-identity-repair` development line repairs the v1.0 Wear Data Layer identity mismatch. Phone and Wear v1.1 use installed application ID `io.github.yingqiu0871.evolune`; the Wear Kotlin namespace remains `io.github.yingqiu0871.evolune.wear`. The existing `/hrt/plans`, `/hrt/request-plans`, and `/hrt/dose-actions/<actionId>` wire formats remain unchanged.

Wear now distinguishes waiting, disconnected, pending, failed, stale, authoritative no-plan, and ready states. These are derived transport/presentation states only. Phone Room v3 remains the source of truth, and replay, conflict, JSON v1, and persistence-before-side-effects behavior are unchanged.

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

## Current Limitations

- Health Connect is not implemented.
- Google cloud backup or cloud synchronization is not implemented.
- Auto Backup and device transfer intentionally exclude private app data; user-controlled Mahiro JSON export/import is the available migration path.
- The Wear transport uses the shipped `/hrt/*` Data Layer payloads and does not yet provide a general versioned envelope, acknowledgement protocol, or full Wear application experience.
- The current RemoteViews widget is functional; broader size, configuration, privacy, and interaction enhancements remain future work.
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

- `v1.1`: Wear OS + Phone Widget Enhancement. Wear identity/Data Layer repair is in development; later Wear/Widget scope remains separately gated.
- `v1.2`: Health Connect and Google cloud backup, delivered as separate batches.

See the [Roadmap](ROADMAP.md) for later and deferred work.
