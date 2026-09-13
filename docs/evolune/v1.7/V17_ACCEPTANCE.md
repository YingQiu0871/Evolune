# Evolune v1.7 — 验收标准（V17_ACCEPTANCE）

> 状态：`BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED`（2026-09-13）
> **Product/code baseline**：`main` @ `72a468c`；实施基线 `v1.6.0`
> **Phase-0 freeze documentation commit**：`docs/evolune/v1.7/*` 的 docs-only commit（不是产品实现基线）
> 本文已应用架构/产品门裁决 **Decision A–F**（原文见 [V17_SPEC.md 附录](V17_SPEC.md#phase-0-gate-decisions架构产品门裁决2026-09-13)）
> 本文件定义 v1.7 各阶段的可判定验收项。所有条目都必须能用"可复现命令 + 原始输出 + 计数"证明；
> 仅给出"测试通过"的结论性摘要不构成验收证据（开发计划 §11）。
> 配套：[规格](V17_SPEC.md) · [开发计划](V17_PLAN.md) · [基线审计](V17_BASELINE_AUDIT.md) · [数据语义](V17_DATA_SEMANTICS.md)

---

## 0. 通用验收前置（每个阶段都适用）

| # | 要求 | 判定方式 |
|---|---|---|
| G1 | 变更范围与批准阶段一致 | `git diff --stat` 对照阶段范围；越界即 REQUEST_CHANGES |
| G2 | 未触碰边界清单 | 无 Room schema/migration、无 JSON v1 契约、无 Wear `/hrt/*` 协议、无 PK 数值参数、无依赖、无版本元数据改动；用 `git diff --name-only` 逐一核对 |
| G3 | 行为等价证据 | 每条新增/修改的语义规则都有确定性测试（规格 Invariant 7） |
| G4 | 原始证据 | 基线 SHA、候选 SHA、changed files、测试命令、计数、失败/错误、设备目标、已知限制 |
| G5 | 独立复审 | 由不同模型实例复审，且复审拿到的是事实与证据而非说服性摘要 |
| G6 | 工作树状态 | `git status --porcelain` 原文；不得宣称 clean 除非确实 clean |
| G7 | 无静默语义变更 | 规格 §2.2 的十项既有语义逐项确认未被改写，或已按"文档 + 测试 + 迁移安全 + 独立复审"四步显式版本化 |

---

## 1. Phase A — History Foundation

| # | 验收项 | 判定 |
|---|---|---|
| A1 | 历史投影来自既有权威事实：`dose_events` + `medication_plans`/`scheduled_dose_slots`，无新表、无新事实列 | schema 未变（`app/schemas/**` 无 diff）；新增代码只读现有 repository |
| A2 | 复用共享派生层：History 与 Widget/Wear/Reminder 使用同一 `MedicationOccurrencePresentation`（或其唯一后继），不得出现第二套匹配实现 | 代码审查 + 边界测试（参照既有 `PureDependencyBoundaryTest`、`ReplayPolicyBoundaryTest` 做法） |
| A3 | 既有四阶段匹配语义保持不变（顺序、1h 闭区间、唯一候选、消费规则） | 既有 `MedicationOccurrencePresentationTest` 全绿且**未被削弱**（不允许修改期望值来迁就新实现） |
| A4 | 日视图能区分：计划 occurrence、已记录（含实际时间）、未记录、**未匹配事件/孤儿事件**；孤儿事件必须展示，且按**真实 `source`** 呈现（只有 `source == MANUAL` 可标 Manual intake）——**Decision B** | 新增投影类型 + 确定性测试；覆盖语义文档 §5/§6 |
| A4b | **Match provenance 必须可携带**：至少区分 `exact identity/date match` / `bounded inferred match` / `legacy/null-slot inferred match` / `unmatched actual event`（enum/type 名由实现阶段定）——**Decision F** | 投影类型 + 逐阶段测试（含跨 local-date 的 legacy inferred match 不得标为 exact） |
| A5 | 跨日匹配（昨日 23:00 legacy 事件 → 今日 00:00 occurrence，`±1h` 闭区间）呈现为 **Legacy inferred match**，不得伪装成 exact historical match——**Decision F** | **新增精确跨日边界回归测试**（当前仅有 code-derived behavior）+ 人工确认（不得表现为"当日准时完成"） |
| A6 | 撤销语义正确：撤销后行不存在于任何历史投影；长期历史**只**表达 `Recorded` / `No recorded intake`，不得虚构 `Skipped` / `Missed`——**Decision C** | 复用物理删除机制；补测试（Phone delete + Wear latest-delete 各自的结果）+ 文案审查 |
| A7 | 读路径无写入：打开 History 不产生任何 `dose_events`/`plans` 变更（Invariant 5） | 测试用计数型 repository 替身断言零写入 |
| A8 | 时间语义：显示日期与时区规则已文档化并实现一致；`schedule wall-clock time`（user intent）与 `scheduledAt`（valid instant）在类型/API/呈现上保持区分——**Decision D** | 见 §4 的 T1–T6 时间用例 |
| A9 | 新增回归：删除计划后事件存活、计划编辑后历史归属、时区 A→B、DST×匹配、**occurrence identity fixed golden vector** | 五类新测试（见 §9 mandatory backlog；基线审计 §8 标为缺口/NONE） |
| A10 | fresh 验证 | JVM 全量 + app instrumentation（受影响面）；`BUILD SUCCESSFUL` 原文 |

门禁：`APPROVE V1.7-A CANDIDATE IMPLEMENTATION`

---

---

## 1.5 Phase A — 关闭状态与证据（2026-09-13，v1.7-A-04）

| # | 状态 | 证据（fresh，A-04） |
|---|---|---|
| A1–A5 | CLOSED | A-01/A-02/A-03 轮实现与测试；A-04 `git diff --name-only` 复核 schema 0 改动 |
| **A6** | **CLOSED** | `HistoryUndoProjectionTest`（5）：Phone `HRTViewModel.deleteEvent` 与 Wear `WearAppUndoHandler` 两条权威 mutation 路径 → matched→unrecorded / unmatched 消失 / early intake→`FutureOccurrenceContext`（History 中消失）/ delayed cross-date 与 DST 日无 ghost；Decision C 文案审查（`history_*` 标签禁用词扫描 + strings 复核） |
| **A7** | **CLOSED** | `HistoryReadZeroWriteTest`（4，计数替身覆盖两个仓库全部 mutation 方法；reads 计数证明读路径真的执行）+ `HistoryReadZeroWriteDeviceTest`（真 Room，读前后 id→revision / plans 快照一致，写入计数 0） |
| **A8/A9** | CLOSED | T1–T7 与 M1–M8 覆盖（A-01/A-02 轮 + A-04 fresh JVM 复核） |
| **A10** | **CLOSED** | fresh JVM **1027 / 0 fail**（app 797 / core 140 / wear 90）+ targeted instrumentation 5/5 + full Phone **243 / 0 fail / 5 skip**（基线同 skip 集合）+ `assembleDebug` `BUILD SUCCESSFUL` |
| M1–M8 | CLOSED | M5/M6 的撤销侧由 A-04 补齐 |
| index | INDEX DEFERRED | [A-04 §14](V17_A_04_HARDENING.md)：20k 行 EXPLAIN（`SCAN` + temp B-tree）+ 实测 median ≈ 2 ms，acceptance/performance gate 均不要求 |

证据包：`docs/evolune/v1.7/evidence/a-04/`（含 gate matrix、fresh JVM XML/log、targeted 与 full Phone XML/log、
zero-write、undo-projection、refresh、`explain-query-plan.txt`、聚合 TSV、assemble log、`MANIFEST.sha256`）。

**PHASE A — CLOSED**（候选实现，待独立复审）：门禁目标为 `APPROVE V1.7-A CANDIDATE IMPLEMENTATION`。

## 2. Phase B — Adherence Insights

| # | 验收项 | 判定 |
|---|---|---|
| B1 | 指标**只**消费 Phase A 的投影，无独立匹配逻辑 | 代码审查 + 边界测试 |
| B2 | 术语语义经批准：`Scheduled` / `Completed` / `Uncompleted` / `Completion rate` / `Timing difference` / 分药指标 | 每项给出定义与边界（含歧义事件、孤儿事件、skip 的计入规则） |
| B2a | **Decision A 约束**：不得基于不可证明的 historical plan reconstruction 输出"严格历史 adherence rate"；优先 factual medication insights；只有 provenance 足够可靠的数据可进入计划符合度统计 | 指标清单逐项标注数据来源与 provenance 要求；`legacy/null-slot inferred match` 不得作为高置信输入 |
| B2b | **Decision C 约束**：长期历史只表达 `Recorded` / `No recorded intake`，不得出现 `Skipped` / `Missed` | `strings.xml` + 文案审查 |
| B2c | 具体 B 阶段指标在 **A 的 provenance model 冻结后**才最终冻结 | 冻结记录（A 门禁产物）作为 B 的前置 |
| B3 | 未批准术语（`late`/`missed`/`due`/`expired` 等）**未出现**在 UI 文案中 | 文案清单 + `strings.xml` diff 审查 |
| B4 | 无医学判断、无评分羞辱、无未支持健康声明 | 文案审查（规格 §10） |
| B5 | 区间 7/30/90 天（+可选自定义）边界正确 | 边界测试：区间端点的半开语义、跨 DST、跨时区 |
| B6 | 分母/分子在有 legacy、孤儿、歧义、手动事件时一致 | 构造数据集逐项断言 |
| B7 | fresh 验证 + 独立复审 | 同 G4/G5 |

### 2.1 Widget UX acceptance item（Decision H，非 A-01 范围）

| # | 要求 | 判定 |
|---|---|---|
| W-DH-1 | Widget action 被 authoritative validation 拒绝（跨午夜 / stale occurrence / invalid occurrence）时：不写入错误 completion、刷新权威 widget 状态、不得显示或保留 completed 假象 | 拒绝路径测试 + widget 状态断言 |
| W-DH-2 | 必须提供明确 user-visible rejection feedback（Toast / notification / 瞬时 widget 反馈任选其一，机制由后续阶段决定） | 文案与交互审查；**机制不在 A-01 实现** |

门禁：`APPROVE V1.7-B CANDIDATE IMPLEMENTATION`

---

## 3. Phase C — Retrospective PK

| # | 验收项 | 判定 |
|---|---|---|
| C1 | 回顾性 PK 只消费**权威实际事件**（`dose_events` 行）；计划/预测事件不得混入历史曲线 | 代码审查 + 测试：输入集中出现预测事件即失败 |
| C2 | 区间输入显式：给定 `[start, end]` 重建曲线；不再依赖 `currentTimeH ± 15d` 硬编码路径 | 新 API 签名 + 测试 |
| C3 | 取数使用半开区间且有**上界**（不得把 `end` 之后的事件算入） | 测试：区间外事件（含未来）不改变结果 |
| C4 | 前史处理显式（长尾 depot 的起始条件），并文档化 | 设计说明 + 数值测试（不同前史窗口的差异必须可解释） |
| C5 | 计划/实际标记与差值来自 Phase A 投影，不重算匹配 | 代码审查 |
| C6 | 数值回归：既有黄金值不变（AUC `23285.499354395688` ±1e-9 等）；新增历史区间黄金值 | `SimulationEngineTest` 全绿 + 新锁定值 |
| C7 | 时区/DST：历史区间在 DST gap/overlap 下不产生重复或丢失事件 | 专项测试 |
| C8 | 呈现明确标注为模型估算，非实测血药浓度 | UI 文案审查（规格 §4） |
| C9 | fresh 验证 + 独立复审（必要时 Max 级二次复审） | 同 G4/G5 |

门禁：`APPROVE V1.7-C CANDIDATE IMPLEMENTATION`

---

## 4. 时间与日期语义专项（v1.7 全阶段共用）

| # | 用例 | 期望 |
|---|---|---|
| T1 | Europe/Paris 记录 → Asia/Shanghai 读取 | 事件绝对时刻**不变**；展示日期按 **Decision G 三类归因**（bound → intended local date；persisted context → 保留 persisted；true legacy orphan → current display timezone 推导）并携带对应 provenance；legacy orphan 不得声称是原始当地日期、不得作为高置信 adherence 输入 |
| T2 | 时区 A 记录 → 时区 B 确认/撤销（Wear/Widget 路径） | 行为已定义（成功或明确拒绝），不得静默错记 |
| T3 | DST 前进（Europe/Paris 2026-03-29 02:30 不存在） | occurrence 生成规则与既有测试一致；展示的"计划时间"取值有明确定义 |
| T4 | DST 后退（Europe/Paris 2026-10-25 02:30 重复） | 不产生重复 occurrence；对较晚偏移的记录行为已定义 |
| T5 | 跨午夜计划（23:30）与次日记录（00:20） | 归属与展示符合已批准决策；Widget 拒绝路径的用户反馈已定义（**仍为未决项**，见语义文档 §13 #6） |
| T6 | DST overlap 日"第二个 02:30"的真实 intake | **Decision E**：保持现有 earlier-offset materialization；文档公开"+1h instant difference"限制；UI 不得把它呈现为无异常的准时记录 |
| T7 | **Decision G**：display-date provenance 必须可被断言 | 三类 provenance 各有确定性用例；true legacy orphan 用例必须证明来源是 `event instant + current display zone`，且不被当作原始日期 |

上述 T1–T5 必须在 **v1.7 release 之前**具备确定性覆盖（规格 §9 明示 "DST tests are mandatory"）。

---

## 5. Phase D — Medication Timeline

| # | 验收项 | 判定 |
|---|---|---|
| D1 | Timeline 与 History 使用同一投影（不得复制匹配逻辑） | 代码审查 + 共享层单测覆盖 |
| D2 | 时间排序稳定（同刻多事件的确定性次序） | 与 `OCCURRENCE_ORDER` 一致的测试 |
| D3 | 日期/区间行为与 History 一致 | 交叉断言测试 |
| D4 | 无障碍与本地化（时制 12/24、语言、字号） | UI 测试 + 人工抽查 |
| D5 | fresh 验证 + 独立复审 | 同 G4/G5 |

门禁：`APPROVE V1.7-D CANDIDATE IMPLEMENTATION`

---

## 6. Phase E — Export & Data Portability

| # | 验收项 | 判定 |
|---|---|---|
| E1 | 导出**只读**：导出过程零写入（Invariant 5） | 计数型替身断言 |
| E2 | CSV/JSON schema 冻结并文档化（版本化字段清单 + 语义说明） | 新增契约文档 + 测试 |
| E3 | 序列化确定性：同输入 → 逐字节相同输出（含排序、时区、精度、换行） | 重复运行比对 + fixture |
| E4 | 与既有 backup 契约**分离**，不构成第二套恢复路径 | 代码审查；不得复用 backup 的 schema/文件名 |
| E5 | 区间：30 天 / 90 天 / 全部历史 | 边界测试 |
| E6 | 隐私：导出内容与分享动作有明确用户触发；不静默上传；无凭据/路径泄漏 | 审查 + 文案 |
| E7 | 手动/legacy/孤儿/歧义事件在导出中的表示已定义且无损语义 | fixture 测试 |
| E8 | 独立复审 | 同 G5 |

门禁：`APPROVE V1.7-E CANDIDATE IMPLEMENTATION`

---

## 7. Phase F — 一致性门与发布

### 7.1 必测矩阵

| 来源 | 期望 |
|---|---|
| Phone 记录 → History / Timeline / Insights / PK | 同一事实 |
| Widget 记录 → 同上 | 同一投影结果（数据行差异已文档化） |
| Wear（App confirm / legacy Tile）→ 同上 | 同上 |
| 撤销（Phone delete / Wear latest-delete）→ 同上 | 行消失且所有投影一致 |
| legacy 事件 → 同上 | 行为确定且保持既有歧义策略 |

### 7.2 附加必测用例

跨午夜、DST 前进、DST 后退、时区变化、重复/重试、手动录入、同药同日多次、计划编辑、计划删除、legacy 迁移、孤儿事件、歧义未归属。

### 7.3 证据包

fresh JVM 全量、fresh Android（`.apk` 构建 + instrumentation）、受影响面的 Wear 验证、`BUILD SUCCESSFUL` 原文、
候选 SHA、changed files、原始日志路径、已知限制、`git status --porcelain` 原文。

### 7.4 复审要求

最终独立复审须输出 P0–P3 分级结论；无未解决 P0/P1 方可发布。

门禁：`APPROVE V1.7.0 RELEASE CANDIDATE`

---

## 8. 与 v1.6 基线的对照锚点（用于"未回归"判定）

| 项 | 基线值（`72a468c`） |
|---|---|
| JVM 测试 | 863（app 689 / experience-core 84 / wear 90），0 失败 / 0 错误 / 0 跳过 |
| app instrumentation | **208 executed / 0 失败 / 0 错误 / 5 跳过**（`Pixel_7 AVD, API 35`；来源 = JUnit XML，见 [V17_BASELINE_EVIDENCE.md](V17_BASELINE_EVIDENCE.md)） |
| wear instrumentation | **5 executed / 0 失败 / 0 错误 / 1 跳过**（`Wear_OS_Large_Round AVD, API 37`；跳过项需配对 Phone；来源 = JUnit XML） |
| PK 黄金值 | AUC `23285.499354395688`（±1e-9），1441 点，6 个采样点锁定 |
| 迁移锁定 | v2→v3 矩阵（2000 事件 / 100 计划固定种子，两库结果全等）；v1.4→v1.5 真机就地升级 |
| slot 身份固定向量 | `17d1fd14-9d70-5344-beaa-0b158c9f62f4`（UUIDv5，namespace `68559b97-4ddc-5be2-bcbd-9ab409f0d95b`） |
| occurrence 身份 | `planId + slotId + localDate`（实现为 MD5 派生的 v3 UUID；**无固定向量测试**） |

> v1.7 任何阶段不得降低上述任一项的强度；若必须调整既有期望值，须在复审包中显式说明理由与影响面。

---

## 9. v1.7-A mandatory backlog（Phase-0 审计产出的强制补齐项）

以下项目**不是可选优化**：在 v1.7-A 门禁前必须具备确定性覆盖，否则 A 不通过。

| # | 项目 | 当前状态 | 来源 |
|---|---|---|---|
| M1 | **occurrence identity fixed golden vector**：锁定具体 UUID 值与 `version()`（当前实现为 **v3/MD5**；slot 为 v5/SHA-1） | 缺（仅有关系性质测试） | 基线审计 §2.2 / P2-1 |
| M2 | **删除计划后 `dose_events` 存活**断言（孤儿事件事实保留） | 零用例 | 基线审计 §8 / 语义 §5 |
| M3 | **计划编辑后历史事件归属**（改时间/改剂量/换 slot 身份三态） | 零用例 | 语义 §4 |
| M4 | **精确跨日边界**：昨日 23:00 legacy null-slot 事件 × 今日 00:00 occurrence（`±1h` 闭区间） | 仅 code-derived behavior | 语义 §1 / Decision F |
| M5 | **时区 A 记录 → 时区 B 读取/确认/撤销** | 零用例 | 语义 §10 / T1–T2 |
| M6 | **DST × 匹配/去重/撤销**（超出生成/换算层） | 零用例 | 语义 §11–§12 / T3–T4 |
| M7 | **provenance 类型**的逐阶段断言（exact / bounded inferred / legacy inferred / unmatched） | 类型尚不存在 | Decision F / A4b |
| M8 | **孤儿事件按真实 source 呈现**（不得统一标 Manual） | 类型尚不存在 | Decision B / A4 |

## 10. PRE-A 阶段（仅在 Phase-0 复审 APPROVE 之后执行）

### V17-PRE-A-01 — Wear Skip Reachability Fix

| 项 | 内容 |
|---|---|
| 触发条件 | Phase-0 复审返回 `APPROVE`（本轮 correction 之后） |
| 背景 | P1-1：Wear 端向 `/hrt/v1/wear-app/skip-notification` 发送 DataItem，但 Phone manifest 未注册该 path（详见基线审计 §6 P1-1） |
| 允许边界 | manifest / reachability 修正 + 回归覆盖；**不改变 `/hrt/*` protocol 语义**；**不改 Room**；**不创造 authoritative skipped medication fact**（遵循 Decision C） |
| 禁止 | 借机重构 Wear 协议、改动 Tile/Complication 设计、把 skip 提升为用药事实 |
| 验收 | 修复后需给出：可达性证据（注册点 + 端到端测试或设备证据）、既有协议测试全绿、`git diff --check` |

> 本轮（Phase-0 correction）**不执行** PRE-A-01，也不执行 hygiene 提交（H1 亦延后，见基线审计 §9）。
