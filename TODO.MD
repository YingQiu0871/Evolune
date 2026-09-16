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
- [ ] Phase D · D-05 contract — **CONTRACT — REVIEW PENDING**
      （`V17_D_05_ACCESSIBILITY_LOCALIZATION_CONTRACT.md`）：final accessibility/localization
      hardening of the shipped D-04 Timeline surface（五项 P2 已闭环：day-cell 单节点 +
      Role.Button + selected/disabled + localized 非 ISO spoken 语法；month-title/section
      heading；entry-card Role.Button；font-scale 1.0/1.3/1.5/2.0 responsive + executed 2.0x
      evidence；MATCHED 双侧 semantic groups + natural separator）。含 key/placeholder parity
      guards、uniform resolved cell width、无 live-region、无程序化 a11y focus。
      **D-05 PRODUCTION — NOT STARTED**；D-06 recurring gate；D-07 final gate。
- [ ] NEXT：**V17-D-05 contract review**（契约批准前不得开工 D-05 生产；不自行发明范围）。
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
