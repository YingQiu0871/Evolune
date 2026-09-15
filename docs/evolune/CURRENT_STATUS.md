# Evolune Current Status

This document is the canonical quick reference for the current public release and development baseline.
Historical plans and phase reports remain evidence of earlier decisions but do not override this status.

Documentation reconciled on 2026-09-12 against `main@c7f3d266357af08b737aa4fd4015f1b1391279c3`,
the `v1.6.0` source tag and GitHub Releases. See the [review](DOCUMENTATION_REVIEW_V16_2026-09-12.md)
and [complete documentation index](DOCUMENTATION_INDEX.md). This is a documentation audit, not a new device acceptance run.

## Current Release

- Stable version: [`v1.6.0`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0)
- Release date: 2026-09-10
- Release source: `v1.6.0` → `58ab66fc22b93630de4ea7137651b2388ff5f1a2`; preserve this tag
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
- A scrollable today-plan widget with occurrence-scoped confirmation, subject to the Phone `AVAILABLE`
  and action-time gate. Next-dose, current-E2 and the 48-hour historical/predicted concentration widget
  are read-only and open the App; the final next-dose renderer has no direct confirmation button.
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
compatibility exchange path. Native encrypted backup/restore and manual Google Drive backup have
also shipped since v1.2; these are separate from Android's automatic backup mechanism.

## Capabilities Carried Forward from v1.2–v1.5

- v1.2.0 (2026-08-28): optional foreground Health Connect weight reads from the preceding 30 days,
  with permission/provider states and local/manual weight freshness protection; no medication writes.
- v1.2.0: native versioned AES-256-GCM backups, PBKDF2-HMAC-SHA256 (default 600,000 iterations),
  restore preview/validation and recovery journaling; manual Google Drive `appDataFolder` backup/restore,
  upload read-back verification and retention of three verified generations. No real-time cloud sync.
- v1.2.2 (2026-08-30): deterministic, unambiguous delayed null-slot dose matching and branding fixes.
- v1.3.0 (2026-09-01): Wear companion App, versioned snapshots and Phone-authoritative confirmation/undo.
  Its Wear APK had an installation defect; v1.3.1 (2026-09-02) corrected it and verified in-place upgrades.
- v1.4.0: first-use trust/permission guidance and a replayable feature tutorial.
- v1.5.0: scoped stability/performance cleanup; its `Energy/background = SKIPPED_BY_OWNER` remains a
  historical waiver, not proof of measured battery improvement.

Release dates and references are listed in the [version review](DOCUMENTATION_REVIEW_V16_2026-09-12.md).

Because v1.0 Wear used the old application ID `io.github.yingqiu0871.evolune.wear`, that one old
package cannot update in place to the shared v1.1+ identity. See
[Wear v1.1 Identity Migration](WEAR_V11_MIGRATION.md). This does not apply to v1.1–v1.5 upgrades.

## Verification Summary

The following results are carried forward from the recorded release gate, not rerun by this audit.
The final Phone evidence combines full Pixel Fold emulator flows with real Pixel in-place installation,
provider/old-instance retention and APK read-back. Four fresh real-Pixel additions were not re-recorded.
Wear product acceptance includes the owner's manual real-watch check; do not describe every state as
independently exercised on a real watch. See the [gate's per-surface matrix](v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md).

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

- Health Connect is limited to optional foreground body-weight reads; background access and medication/PHR writes are not implemented.
- Google Drive requires explicit authorization and manual backup/restore; background and real-time multi-device cloud synchronization are not implemented.
- Auto Backup/device transfer intentionally excludes private app data.
- Tracked Date, personalized calibration/PK 2.0 and SQLCipher remain deferred or unimplemented.
- v1.7 Optional CPA PK Curve remains a candidate only (it is **not** part of the current v1.7
  History & Insights program); it is default-off and requires independent scientific and source
  review before implementation or release.

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
- `v1.7`: History & Insights program — **Phase A CLOSED**（A-04 APPROVE）；**Phase B CLOSED**
  （B-04 APPROVE）：B-00（语义冻结）· B-01（domain 聚合）· B-02/B-02-R1（编排与 race 修复）·
  B-03（Insights UI）全部 APPROVE；Phase B release gate 结论：read-only（zero-write 设备门）·
  无百分比/timing 指标 · 无图表/无新依赖 · schema/DAO 0 改动（除 B-03 已申报的 1 行崩溃修复）。
  **Phase C（Retrospective PK）— C-01 CLOSED**：
  - C-00 语义契约 — **APPROVED / FROZEN**（`APPROVE V17-C-00 SEMANTICS CONTRACT`；
    [`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`](v1.7/V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md)）。
  - C-01 实现规格 — **APPROVED**（[`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`](v1.7/V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md)）。
  - C-01 生产实现 — **APPROVED / CLOSED**（final independent implementation review **APPROVE**）：
    contract HEAD `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`，approved implementation HEAD
    `145d53bd922c30338171cc7b0529a36dc482b4a6`，evidence [`v1.7/evidence/c-01/`](v1.7/evidence/c-01/)。
  - 最终验证摘要：1260 JVM tests / 0 failures / 0 errors / 0 skipped · fresh 54/54 Gradle tasks executed ·
    Room instrumentation 1/1 PASS（Pixel_7 AVD API 35）· golden PK regression preserved · zero-write PASS ·
    schema / PK numerical source / Home·Wear·Widget orchestration unchanged。
  - **C-01 冻结**：未经重新开启评审，不得再对 C-01 生产代码做改动；后续 Phase-C 切片必须消费已批准的 C-01
    API/结果契约。**NEXT:** Phase-C next-slice planning / contract definition（V17_PLAN §6 仍保留原始
    C-01…C-09 命名序列；C-02 契约尚未定义，本轮不发明）。
  当前程序定义见 [`v1.7/`](v1.7/) 目录（[`V17_SPEC.md`](v1.7/V17_SPEC.md) · [`V17_PLAN.md`](v1.7/V17_PLAN.md) ·
  [`V17_ACCEPTANCE.md`](v1.7/V17_ACCEPTANCE.md) · [`V17_A_04_HARDENING.md`](v1.7/V17_A_04_HARDENING.md) ·
  [`V17_B_00_INSIGHTS_SEMANTICS.md`](v1.7/V17_B_00_INSIGHTS_SEMANTICS.md) ·
  [`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`](v1.7/V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md) ·
  [`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`](v1.7/V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md)）。
- `v1.7` 早期草案中的 **Optional CPA Pharmacokinetic Curve** 不再代表 v1.7 的程序范围；
  该候选保持 **CANDIDATE / NOT STARTED**（见下方 Current Limitations），如要推进需单独立项。

See the [Roadmap](ROADMAP.md) for the historical release sequence and future boundaries.
