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
  **Phase C（Retrospective PK）— APPROVED / CLOSED**：
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
    API/结果契约。
  - C-04 contract（Schedule-Context / Recorded-Intake markers & retrospective PK surface MVP）—
    **APPROVED / FROZEN**（[`V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md`](v1.7/V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md)），
    contract HEAD `8be09339bfae1c4a138b4ccb739f86302c60d627`。
  - C-04 生产实现 — **APPROVED / CLOSED**（final independent implementation review APPROVE；R1 T4 disclosure
    closure 后通过）：approved implementation HEAD `823bd9ce5c276dc473cc041efba409bd931c589f`，
    evidence [`v1.7/evidence/c-04/`](v1.7/evidence/c-04/)。
  - C-04 最终验证摘要：focused C-04 JVM 73 / 0 / 0 / 0 · fresh full JVM 1333 tests / 0 failures / 0 errors /
    0 skipped · fresh 54/54 Gradle tasks executed · affected instrumentation 14/14 PASS（Pixel_7 AVD API 35）·
    evidence 169/169 manifest data entries · 171/171 HEAD blob verification · 0 mismatches ·
    C-01 / schema / PK numerical source / Home·Wear·Widget / ConcentrationChart / build-dependency zero-diff。
  - **C-04 冻结**：未经重新开启评审，不得再对 C-04 生产代码做改动；后续切片必须消费已批准的 C-01
    retrospective PK API/结果契约与 C-04 只读 surface 行为。
  - Phase-C closure — **PHASE-C CLOSED — APPROVED**（[`V17_C_PHASE_CLOSURE.md`](v1.7/V17_C_PHASE_CLOSURE.md)）：
    Phase gate `APPROVE V1.7-C CANDIDATE IMPLEMENTATION` 已批准；approved Phase-C closure HEAD
    `19652baa07b5057f4aa6c07a79a77a158ac31468`。**Phase C is CLOSED**：未经重开评审不得再改 Phase-C
    生产；后续切片必须消费已批准的 C-01 retrospective PK API/结果契约与 C-04 只读 surface 行为。
  - Phase D · D-01 contract — **APPROVED / FROZEN**（[`V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md`](v1.7/V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md)，
    contract HEAD `a245a5ec7a2dcd977ff0de3b3a79c8b129f67e34`）；D-01 implementation —
    **APPROVED / CLOSED**（implementation HEAD `97838fbf7c8692ada9d44d8401a6008283fac178`；
    evidence [`v1.7/evidence/d-01/`](v1.7/evidence/d-01/)）。验证摘要：focused D-01 JVM 32 / 0 / 0 / 0 ·
    fresh full JVM 1365 / 0 failures / 0 errors / 0 skipped · 54/54 Gradle tasks executed ·
    instrumentation not applicable（无 Android/UI surface）· evidence 167/167 manifest coverage、
    169/169 HEAD blob verification、0 mismatches。**D-01 冻结**：未经重开评审不得再改 D-01 生产。
  - Phase D · D-02 — **FULLY CONSUMED BY CLOSED D-01**（不创建 D-02 contract、不重编号）。
  - Phase D · D-03 contract — **APPROVED / FROZEN**（[`V17_D_03_TIMELINE_RANGE_DATE_CONTRACT.md`](v1.7/V17_D_03_TIMELINE_RANGE_DATE_CONTRACT.md)，
    contract HEAD `ec0416e32ca0094ce3347a776f76c66b63c0261e`；含 R1/R2）；D-03 implementation —
    **APPROVED / CLOSED**（final independent implementation review `APPROVE V17-D03 IMPLEMENTATION`；
    implementation HEAD `d7d27f204e0eb298ca9a1ac629c7022ef621f603`；evidence
    [`v1.7/evidence/d-03/`](v1.7/evidence/d-03/)）。验证摘要：focused D-03 JVM **56 / 0 failures /
    0 errors / 0 skipped** · fresh full JVM **1421 / 0 / 0 / 0**（app 1160 · experience-core 171 ·
    wear 90）· Gradle **54/54** actionable tasks executed · instrumentation **not applicable**
    （D-03 Android/UI-free）· evidence **170 files · 168/168 manifest coverage · 170/170 HEAD
    blob verification · 0 mismatches** · sha256(MANIFEST.sha256)
    `bddfcd4eb8ea51d3d47c6fb12dfcba270278824616bf312a23e2f22a6b7a4d5e`。
    收口语义（要点）：month-scoped 历史编排 + 显式 client-supplied capturedAt/displayZone；
    结构性 INVALID_REQUEST 先于时间性 NOT_LOADABLE；past/current/future 边界与 History 一致；
    selected-date 0-read 语义；恰七相位；HistoryRangeSource → coordinator → closed D-01 projector；
    全局 ≤1 active source read + ≤1 latest pending context + latest-request-wins（load/refresh/retry
    无类型优先级）；publication authority 在接受时转移；pending 先验证后读；0-read generation 使旧
    飞行代失效；NOT_LOADABLE 以 fresh capture 重评估；latest logical context ≠ last published state；
    飞行中有效 selectedDate 存活；cancellation rethrow / failure 永不转 EMPTY；Android/ViewModel/
    lifecycle-free。**D-03 关闭**——未经重开评审不得再改 D-03 生产。
  - Phase D · D-04 — **CLOSED / FROZEN**（contract
    [`V17_D_04_TIMELINE_UI_CONTRACT.md`](v1.7/V17_D_04_TIMELINE_UI_CONTRACT.md) @
    `c934c24532025f7c82654a7af4e5cd0bafd4f40d`；final implementation HEAD
    `e53342bcd6c4c4af428adc5822f603c71ed8bb24` = candidate `b53b83e` + R1 evidence/test closure，
    R1 production 零语义 diff）：CONTRACT — APPROVED / FROZEN；PRODUCTION — IMPLEMENTED /
    APPROVED / CLOSED；EVIDENCE — COMPLETE（`evidence/d-04/` 178/178 manifest，0 mismatch；
    focused JVM 65/0；full JVM 1486/0；instrumentation 39/39 Pixel_7 API 35；UI35–UI39 +
    UI46–UI47 真实几何断言关闭）。Architect **APPROVE V17-D04 IMPLEMENTATION — ARCHITECT
    CANDIDATE CLOSURE** → 初始独立复审 **REQUEST_CHANGES**（1 P2：UI34 缺 executed proof）→
    R1 real route-cycle executed proof + mapping 修正 + UTF-8 evidence 卫生 → **APPROVE V17-D04
    IMPLEMENTATION R1**（Architect + final independent，P0/P1/P2 = none）。D-04 P3 hygiene
    （F14 标签漂移 / 未使用 helper / KDoc 范围注释）为 CLOSED / 非阻塞，不重开 D-04。
  - **NEXT:** V17-D-05（accessibility / localization hardening）；D-05 NOT STARTED；D-06
    recurring gate；D-07 final gate。
  - Implementation-review P3 处置（hygiene，仅记录；不改证据）：① `d-03/tr-hc-f-mapping.txt`
    头部保留 pre-R1 计数（focused 50 / Coordinator 36），最终提交的 JUnit 证据为 focused 56 /
    Coordinator 42（独立复审已核实最终 XML 计数与六个 R1 增项）；历史已批准证据不回写，后续
    证据生成流程应从最终 XML 更新汇总计数。② d-03 Gradle 日志为 UTF-16-LE（UTF-8 卫生偏好
    之外）；哈希/manifest/blob 完整性与解码内容已独立核实；不回写历史日志；D-04 起的证据生成
    应在 manifest 构造前把新文本日志显式规范化为 UTF-8。
  当前程序定义见 [`v1.7/`](v1.7/) 目录（[`V17_SPEC.md`](v1.7/V17_SPEC.md) · [`V17_PLAN.md`](v1.7/V17_PLAN.md) ·
  [`V17_ACCEPTANCE.md`](v1.7/V17_ACCEPTANCE.md) · [`V17_A_04_HARDENING.md`](v1.7/V17_A_04_HARDENING.md) ·
  [`V17_B_00_INSIGHTS_SEMANTICS.md`](v1.7/V17_B_00_INSIGHTS_SEMANTICS.md) ·
  [`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`](v1.7/V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md) ·
  [`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`](v1.7/V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md)）。
- `v1.7` 早期草案中的 **Optional CPA Pharmacokinetic Curve** 不再代表 v1.7 的程序范围；
  该候选保持 **CANDIDATE / NOT STARTED**（见下方 Current Limitations），如要推进需单独立项。

See the [Roadmap](ROADMAP.md) for the historical release sequence and future boundaries.
