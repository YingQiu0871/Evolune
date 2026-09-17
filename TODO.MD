# 后续路线（v1.6.0 已封版）

截至 2026-09-12，v1.0–v1.6 已发布。下文区分已完成里程碑与未授权的未来候选，
不是把旧版本重新列入待办。完整版本及文档索引见 [文档盘点](docs/evolune/DOCUMENTATION_REVIEW_V16_2026-09-12.md)。

## v1.2 — Google Integration & Data Continuity（已发布）

- [x] v1.2.0：可选 Health Connect 前台体重读取、权限/provider 状态和本地 freshness 保护。
- [x] v1.2.0：原生加密备份、恢复预览/校验/journal、手动 Google Drive appDataFolder 备份/恢复。
- [x] v1.2.2：延迟 null-slot 记录的确定性唯一匹配修复及品牌维护。
- 边界：不含后台 Health Connect、用药写入或实时云端数据库同步。

## v1.3 — Wear OS Companion App（已独立发布）

- [x] 当前基线已包含可打开的轻量 Wear App、版本化 snapshot、occurrence identity、Phone
      权威 Repository、persist-first confirmation/undo 和可重建派生缓存；v1.6 继续以此为兼容前提。
- [x] v1.3.0 发布 Wear App；v1.3.1 修复旧 Wear APK 的 taskAffinity 安装错误并完成升级验证。

## v1.4.0 — Onboarding / Terms / Permission Guidance（已封版发布）

- [x] v1.4-A：首次使用、条款/隐私/医疗免责声明和上下文式权限引导。
- [x] v1.4-B：用药方案、记录服药、PK 图、Widget、Wear 与备份六步功能教程。
- [x] 自动化回归、Phone clean-install smoke 与独立复核。
- [x] 创建 `v1.4.0` release tag/GitHub Release，上传签名 Phone/Wear APK，并完成最终发布签核。

## v1.5 — Stability / Performance / Cleanup（已封版，v1.6 基线）

- [x] v1.5.0 独立设计、稳定性验收矩阵和性能基线已封存。
- [x] 新装、进程重建、重启、跨午夜、时区/DST、升级恢复和异常数据状态已验收。
- [x] 方案修改/删除、多 Widget、Phone/Wear 断连重连、Widget 刷新和备份恢复已验收。
- [x] 启动、PK、Room、Compose、Widget、Wear Data Layer、WorkManager 和后台任务已完成范围内检查。
- [x] 代码清理、真实 Phone/Wear 矩阵和无新增 P0/P1 的 RC 验收已封存；Energy/background 保留明确豁免。

## v1.6 — Widget Gallery（已封版发布）

实施计划：[V16_PLAN](docs/evolune/v1.6/V16_PLAN.md)；
审阅方式：[V16_REVIEW](docs/evolune/v1.6/V16_REVIEW.md)。A–G 已完成，最终矩阵与产物身份见 [V16_ACCEPTANCE](docs/evolune/v1.6/V16_ACCEPTANCE.md) 和 [最终发布门禁](docs/evolune/v1.6/V16_FINAL_RELEASE_GATE_2026-09-09.md)。

- [x] v1.6-A：冻结 v1.5.0 基线、样式/状态/尺寸规格、设备矩阵和刷新性能预算。
- [x] v1.6-B：共享展示状态、按实例样式配置、旧 Widget 兼容及 Wear 字段/协议契约。
- [x] v1.6-C：手机今日计划、下一次服药、当前 E2 三类信息入口及宿主验收；今日完成度并入今日计划。
- [x] v1.6-D：手机大尺寸只读 PK 图表、数值一致性、响应式坐标轴及多实例性能验收。
- [x] v1.6-E：Wear 下一次服药、今日计划、当前 E2 三种 Tile 及跨端确认/跳过动作审阅。
- [x] v1.6-F：下一次服药时间、当前 E2、今日完成度三种表盘信息及真实手表验收。
- [x] v1.6-G：升级、全入口一致性、后台/性能回归、独立审阅、签名候选和真实 Phone/Wear 发布验收。

## v1.7 — History & Insights program（Phase A + Phase B 已收口；Phase C：APPROVED / CLOSED）

- [x] Phase A（历史投影 / 读模型 / UI / hardening）：A-01…A-04 全部 APPROVE，PHASE A CLOSED。
- [x] Phase B（Insights）：B-00/B-00-R1/B-01/B-02/B-02-R1/B-03 全部 APPROVE；B-04 hardening 与
      release gate 独立复审 APPROVE：read-only、无百分比/timing、无图表依赖、schema 0 改动。
- [x] Phase C 前置契约：C-00 回顾性 PK 语义契约 — **APPROVED / FROZEN**（`V17_C_00_RETROSPECTIVE_PK_SEMANTICS.md`，
      `APPROVE V17-C-00 SEMANTICS CONTRACT`）；C-01 实现规格 — **APPROVED**
      （`V17_C_01_RETROSPECTIVE_PK_IMPLEMENTATION.md`）。
- [x] Phase C · C-01 生产实现 — **APPROVED / CLOSED**（final independent implementation review APPROVE）：
      contract HEAD `34ca5e1b2bd7f7f7476a63e795d75a9c827acef9`，approved implementation HEAD
      `145d53bd922c30338171cc7b0529a36dc482b4a6`；最终验证：1260 JVM tests / 0 failures / 0 errors /
      0 skipped · fresh 54/54 Gradle tasks executed · Room instrumentation 1/1 PASS（Pixel_7 AVD API 35）·
      golden PK regression preserved · zero-write PASS · schema / PK numerical source / Home·Wear·Widget
      orchestration unchanged。**C-01 冻结**：未经重开评审不得再改 C-01 生产代码；后续切片消费已批准 API/结果契约。
- [x] Phase C · C-04 contract — **APPROVED / FROZEN**（`V17_C_04_RETROSPECTIVE_SURFACE_CONTRACT.md`，
      `APPROVE V17-C04 CONTRACT`；R1/R2/R3 修正已并入）：contract HEAD
      `8be09339bfae1c4a138b4ccb739f86302c60d627`。
- [x] Phase C · C-04 生产实现 — **APPROVED / CLOSED**（final independent implementation review APPROVE；
      R1 T4 disclosure closure 后通过）：approved implementation HEAD
      `823bd9ce5c276dc473cc041efba409bd931c589f`；evidence `docs/evolune/v1.7/evidence/c-04/`。
      最终验证：focused C-04 JVM 73 / 0 / 0 / 0 · fresh full JVM 1333 tests / 0 failures / 0 errors / 0 skipped ·
      fresh 54/54 Gradle tasks executed · affected instrumentation 14/14 PASS（Pixel_7 AVD API 35）·
      evidence 169/169 manifest data entries · 171/171 HEAD blob verification · 0 mismatches ·
      C-01 / schema / PK numerical source / Home·Wear·Widget / ConcentrationChart / build-dependency 全部 zero-diff。
      **C-04 冻结**：未经重开评审不得再改 C-04 生产；后续切片必须消费已批准的 C-01 retrospective PK
      API/结果契约与 C-04 只读 surface 行为。
- [x] Phase C · closure — **PHASE-C CLOSED — APPROVED**（`V17_C_PHASE_CLOSURE.md`）：Phase gate
      `APPROVE V1.7-C CANDIDATE IMPLEMENTATION` 已批准；approved Phase-C closure HEAD
      `19652baa07b5057f4aa6c07a79a77a158ac31468`。**Phase C is CLOSED**：未经重开评审不得再改 Phase-C
      生产；后续切片必须消费已批准的 C-01 retrospective PK API/结果契约与 C-04 只读 surface 行为。
- [x] Phase D · D-01 contract — **APPROVED / FROZEN**（`V17_D_01_TIMELINE_READ_MODEL_CONTRACT.md`，
      `APPROVE V17-D01 CONTRACT`）：contract HEAD `a245a5ec7a2dcd977ff0de3b3a79c8b129f67e34`。
- [x] Phase D · D-01 implementation — **APPROVED / CLOSED**（final independent implementation review
      APPROVE）：implementation HEAD `97838fbf7c8692ada9d44d8401a6008283fac178`；
      evidence `docs/evolune/v1.7/evidence/d-01/`。验证摘要：focused D-01 JVM 32 / 0 / 0 / 0 ·
      fresh full JVM 1365 / 0 failures / 0 errors / 0 skipped · 54/54 Gradle tasks executed ·
      instrumentation not applicable（无 Android/UI surface）· evidence 167/167 manifest coverage、
      169/169 HEAD blob verification、0 mismatches。**D-01 冻结**：未经重开评审不得再改 D-01 生产。
- [x] Phase D · D-02 — **FULLY CONSUMED BY CLOSED D-01**（chronological grouping 由 D-01 的
      date-section model + canonical ordering + same-instant determinism 交付；不创建 D-02 contract，
      不重编号）。
- [x] Phase D · D-03 contract — **APPROVED / FROZEN**（`V17_D_03_TIMELINE_RANGE_DATE_CONTRACT.md`，
      `APPROVE V17-D03 CONTRACT`；含 R1/R2）：contract HEAD
      `ec0416e32ca0094ce3347a776f76c66b63c0261e`。
- [x] Phase D · D-03 implementation — **APPROVED / CLOSED**（final independent implementation review
      `APPROVE V17-D03 IMPLEMENTATION`；implementation HEAD `d7d27f204e0eb298ca9a1ac629c7022ef621f603`；
      evidence `docs/evolune/v1.7/evidence/d-03/`）。验证摘要：focused D-03 JVM 56 / 0 / 0 / 0 ·
      fresh full JVM 1421 / 0 / 0 / 0（app 1160 · experience-core 171 · wear 90）· 54/54 tasks ·
      instrumentation not applicable（Android/UI-free）· evidence 170 files / 168/168 manifest
      coverage / 170/170 HEAD blob verification / 0 mismatches · sha256(MANIFEST.sha256)
      `bddfcd4eb8ea51d3d47c6fb12dfcba270278824616bf312a23e2f22a6b7a4d5e`。收口要点：month-scoped
      只读编排 + client-supplied capturedAt/displayZone；结构性 INVALID_REQUEST 先于时间性
      NOT_LOADABLE；全局 ≤1 active source read / ≤1 latest pending context / latest-request-wins；
      publication authority 在接受时转移；pending 先验证后读；0-read generation 使旧飞行代失效；
      NOT_LOADABLE fresh-capture 重评估。**D-03 关闭**——未经重开评审不得再改 D-03 生产。
      P3 hygiene（仅记录，不改证据）：tr-hc-f-mapping.txt 头部为 pre-R1 计数（50/36），最终证据
      为 56/42（复审已核实）；d-03 Gradle 日志为 UTF-16-LE（完整性已核实）——D-04 起新日志
      显式 UTF-8 落盘、证据汇总计数从最终 XML 更新。
- [x] Phase D · D-04 — **CLOSED / FROZEN**（contract `V17_D_04_TIMELINE_UI_CONTRACT.md` @
      `c934c24532025f7c82654a7af4e5cd0bafd4f40d`；final implementation HEAD
      `e53342bcd6c4c4af428adc5822f603c71ed8bb24` = candidate `b53b83e` + R1 evidence/test
      closure `e53342b`，R1 production 零语义 diff）。
      CONTRACT — APPROVED / FROZEN；PRODUCTION — IMPLEMENTED / APPROVED / CLOSED；EVIDENCE —
      COMPLETE（`evidence/d-04/` 179 files / 178 manifest entries，178/178 coverage，0 mismatch）。
      Architect candidate **APPROVE** → 初始独立复审 **REQUEST_CHANGES**（1 P2：UI34 缺 executed
      proof）→ R1 **APPROVE**（Architect + final independent；P0/P1/P2 = none；UI34 real route-cycle
      executed proof，无 production test hook）。验收：focused JVM 65/0、full JVM 1486/0、
      instrumentation 39/39（Pixel_7 API 35）；UI35–UI39/UI46–UI47 真实几何断言关闭。
      **D-04 冻结**：未经重开评审不得改 D-04 语义。
- [x] Phase D · D-05 — **CLOSED / FROZEN**（contract `V17_D_05_ACCESSIBILITY_LOCALIZATION_CONTRACT.md` @
      `b1cdd662fe1bee14beab5cad651aef772f211c68`；final implementation HEAD
      `2a79f1d048905113f53d4be470071a94072c2f96`）。
      CONTRACT — APPROVED / FROZEN；PRODUCTION — IMPLEMENTED / APPROVED / CLOSED；EVIDENCE —
      COMPLETE（`evidence/d-05/` 187 files / 186 manifest entries，186/186 coverage，0 mismatch）。
      Architect **APPROVE V17-D05 IMPLEMENTATION — ARCHITECT CANDIDATE CLOSURE** + 独立复审
      **APPROVE**（Qwen3.8 Flash；P0/P1/P2 = none）。验收：focused JVM 69/0 · full JVM 1490/0 ·
      instrumentation 59/59（Pixel_7 API 35；VisualEvidence = 2）· semantics-tree closure（day-cell
      单节点 + Role.Button；section-header 单短语；MATCHED 双侧）· font-scale 1.0/1.3/1.5/2.0
      measured · touch target ≥48dp（touchBoundsInRoot）· Timeline families 34/34 parity。
      P3-1/2/3 为 CLOSED / 非阻塞。**D-05 冻结**：未经重开评审不得改 D-05 语义。
- [x] Phase D · D-06 — **RECURRING VERIFICATION GATE — PASS**（verified executable source HEAD
      `13aeaf6ab60cbe58236290de063122e42a4296de`；fresh：focused Phase-D JVM 157/0/0/0、full JVM
      1490/0/0/0（app 1229 + ec 171 + wear 90）、Android 59/0/0/0（Pixel_7 API 35）；post-approval
      production drift 为空（`git diff 2a79f1d..13aeaf6 -- app/experience-core/wear` 均空）；
      resource parity 34/34、placeholder parity 0 mismatch；date/time authority 0 forbidden APIs；
      forbidden semantics 0 hits。evidence `evidence/d-06/`（179/179 manifest，
      sha256(MANIFEST.sha256) = `20145e461c65267af0ee94207a01f87d32b42fe7d1f3a27e499a2437a0b43091`）。
      **Phase D 仍 IN PROGRESS**（**不**标记 CLOSED）。
- [x] Phase D · D-07 — **FINAL PHASE-D INDEPENDENT GATE — PASS**（**APPROVE V17-D07 FINAL
      PHASE-D GATE**；独立复审 Qwen3.8 Flash，fresh independent read-only session；P0/P1/P2 = none、
      无新 P3）：verified gate HEAD `2560afcf4f28557a92667db973a5ce3cac280723`；verified executable
      source HEAD `13aeaf6ab60cbe58236290de063122e42a4296de`；executable tree 与 D-05 approved
      implementation `2a79f1d...` 逐字节一致（app `2ceebb6e76f91d4d20be2e9e992f7c438876f3dd` ·
      experience-core `b4a7af411f93662098fc22e6119892dbcbe67359` · wear
      `8f77a6dab307bb140277dc23c618341eacd92f00`）⇒ 批准后零可执行变更；八问全 YES；历史证据树
      （d-01/d-03/d-04/d-05）未被改写；ACCEPTANCE D1–D5 与 G1–G7 满足；packet
      `review-packets/v17-d07-final-phase-d-independent-review.txt`。**Phase D — CLOSED / FROZEN**
      （未经重开评审不得改 Phase-D 语义；本收口 docs-only）。
- [x] Phase E contract — **PHASE-E CONTRACT — APPROVED / FROZEN**
      （`V17_E_EXPORT_DATA_PORTABILITY_CONTRACT.md`；approved contract HEAD
      `ab9a79625e5a1f7ef1626fd36b473c00f287cf64`；Architect **APPROVE V17 PHASE-E CONTRACT R2 —
      ARCHITECT CLOSURE** + 独立复审 **APPROVE**（Qwen3.8 Flash，fresh strict read-only
      session），P0/P1/P2 = none；R1/R2 修正已并入）：E-01 export contract / E-02 CSV v1 schema /
      E-03 Evolune Portable JSON v1 schema。冻结要点：portable truth = dose_events only，**
      11 portable 字段**（`revision` 为 repository-local concurrency metadata，不在格式内；新导入
      行 `revision = 1`；不重开 schema/DAO/repository）；canonical `schema="evolune-portable"` /
      `version=1` strict（unknown field/duplicate key/version fail-closed；全量预校验 + additive
      per-record + stable-ID replay-safe，equality 含 stored revision>1 的 E7.6）；Mahiro v1 =
      permissive legacy compatibility（无 version gate、不作为 canonical/E7 证明；E2.6 覆盖
      canonical→Mahiro zero-write 负向证据）；CSV export-only **16 列**（planned_time/
      timing_delta 恒 empty；medication 穷举映射表）；ranges 30/90/all（绝对 Instant、边界
      inclusive）；E1 zero-write、E3 逐字节确定性、E4 backup 分离（EG3–EG6）、E6 隐私/clipboard
      确认；验收范围 `E1.1–E1.2 / E2.1–E2.6 / E3.1–E3.3 / E4.1–E4.4 / E5.1–E5.4 / E6.1–E6.6 /
      E7.1–E7.6 / E8.1`；guards `EG1–EG25`。**PHASE-E PRODUCTION — NOT STARTED**（E-04…E-07 未授权）。
- [x] Phase-E implementation + final closure — **PHASE-E PRODUCTION — APPROVED / CLOSED /
      FROZEN**（candidate implementation HEAD `e119869a080b6780e4138c75606dd393fd8431ee`
      `feat: implement v1.7 Phase E export portability`，parent / frozen-contract base
      `53530858c8f0f9a071fc3eae93eec6d17d5f45b0`，approved semantic contract HEAD
      `ab9a79625e5a1f7ef1626fd36b473c00f287cf64`；Architect **APPROVE V17 PHASE-E CANDIDATE
      IMPLEMENTATION — ARCHITECT FINAL GATE** + fresh independent **APPROVE**（Qwen3.8 Flash，
      strict read-only session），P0/P1/P2 = none；E1–E7 VERIFIED / PASS、**E8.1 —
      INDEPENDENT IMPLEMENTATION REVIEW — PASS**、EG1–EG25 PASS；evidence
      `docs/evolune/v1.7/evidence/phase-e/` 196 files / MANIFEST 195/195 / HEAD-blob
      196/196 0 mismatch；focused Phase-E JVM 88/88、full JVM 1578/0（app 1317 fresh
      executed；experience-core 171 / wear 90 tasks UP-TO-DATE、源未变结果有效）、Pixel_7
      API 35 12/12；final closure packet `review-packets/v17-phase-e-final-closure.txt`；
      **Phase E CLOSED / FROZEN**，未经重开评审不得改 Phase-E 语义）。
- [ ] NEXT：**Phase F — Final Consistency Gate**（`V17_PLAN.md` §9；架构敏感 release phase：
      Phone/Widget/Wear/Undo/Legacy 记录 → History/Timeline/Insights/PK 一致性矩阵 +
      cross-midnight / DST 双向 / timezone change / duplicate-retry / manual intake /
      same-medication-multiple-times-a-day / schedule edit / schedule deletion / legacy
      migration 用例；fresh JVM + fresh Android + affected Wear 证据；release gate
      `APPROVE V1.7.0 RELEASE CANDIDATE`；不在本收口内开始）。
- P3 处置（D-04 implementation；CLOSED / 非阻塞，不重开 D-04）：① `TimelineUiArchitectureGuardTest`
      标签 "F14" 用于 coroutine-scope guard，与契约 F14（依赖策略）编号漂移——命名/追溯性影响，
      无行为缺陷；② `TimelineGeometryTest` 未使用的 `assertCenteredBothAxes` helper 名称（both
      axes）与实现（仅 x）不一致——实际几何断言不依赖它；③ `TimelineNavigationTest` 类 KDoc 仍写
      UI31–UI33（现亦含 UI34 executed test）——注释级。均可在未来自然触碰的 test-hygiene/docs pass
      中清理。
- P3（非阻塞，仅记录）：marker 家族身份通过图例 + marker 样式呈现，不新增逐 marker 文本标签要求；
      `retrospective_legend_entry_format` 为实现确认的 presentation-only cardinality key，不授权 ratio /
      percentage / coverage / adherence / punctuality / scoring 语义；最终实现身份为 `823bd9ce...`，
      历史 evidence 中的 pre-amend 引用仅为过程信息。
- [ ] 明确的新 contract 才可推进：逐日 series / chart、coverage 百分比 UI、Option-2 timing metric、
      anti-androgen 真实身份投影字段（均需语义批准，不得在 hardening 中偷加）。
- [ ] `v1.7` 早期草案的 Optional CPA PK Curve 保持独立科学/来源评审门槛，不因 C-01 关闭而改变状态。

## v1.7 — Optional CPA PK Curve

- [ ] 在独立科学、来源和回归评估通过后，增加默认关闭且单位独立的 CPA 估算曲线。

## Deferred

- v1.6 最终门禁保留的可选 P3：真实 AlarmManager 队列与连续并发跳过压力测试；不伪装成已执行。
- 本次文档盘点发现的历史日志/截图可追溯性缺口见 [盘点报告](docs/evolune/DOCUMENTATION_REVIEW_V16_2026-09-12.md)；不补造证据。

- Tracked Date、个性化 PK/calibration、SQLCipher 和 Gradle module extraction 仍需
  单独产品/安全/迁移决策。
