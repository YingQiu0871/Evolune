# Phase 1 Batch 7 Design Independent Review

Date: 2026-08-10
Reviewer: DeepSeek (independent read-only design review)
Worktree: `D:\Evolune-phase1`
Branch: `phase1/batch7-design`
Design document: `docs/phase-reports/PHASE_1_BATCH_7_DESIGN.md` (untracked, 932 lines)

## Executive summary

**Decision: APPROVE WITH P2**

- P0 / P1 / P2 = **0 / 0 / 3**（全部为文档显式性 P2，无代码/语义缺陷）
- **7A ready: YES**（JSON DTO/codec/Domain adapter——设计 §6/§7/§14 完整锁定）
- **7B ready: YES**（Repository-backed import service——设计 §7.3/§14.3 完整锁定）
- **7C ready: YES**（Domain→PK adapter + parity——设计 §10/§11/§15 完整锁定）
- **Batch 7 implementation allowed: YES — with Batch 7A only**（见 Final decision）
- **Batch 8 still reserved: YES**
- **Widget deferred: YES**（无越界；文档未显式声明——P2-F3）
- **Custom medication deferred: YES**（无越界；文档未显式声明——P2-F2）
- **Wear feature expansion deferred: YES**（§Deferred Wear product requirements 明确）
- **Release forbidden: YES**（Room v3 internal/unreleasable；release gate 归 Batch 8）

设计文档对当前代码的审计（§4/§5/§9）经逐项代码核对全部准确（extras 六键、missing/corrupt ID 的 `UUID.randomUUID()` 行为、Batch6 bridge 的 import/export/project 结构、LegacyTimeAdapter 存在性）。

## Scope verdict

- 范围严格限于：JSON v1 协议边界（DTO/codec/Domain adapter）、Repository-backed import service、正式 Domain→PK adapter——与任务 §一 完全一致 ✓
- 明确排除：wire 格式变更、Domain 重设计、Repository contract 变更、schema/migration、PK 算法/参数/tolerance、Wear/Widget 协议、release ✓（§2/§3.2）
- 未借 Batch 7 重构无关代码（§3.2 排除项详尽；§19 修改文件清单受限）✓
- 权威依据引用（PHASE_1_DESIGN §869-878、ADR-014/015/016、Batch 6 全套）与任务基线一致 ✓

## JSON v1 verdict

- **DTO 与 Domain 明确隔离**：`external.mahiro.v1` 包（逻辑边界，无新 module）；DTO 不含 Context/DAO/Entity/`DoseEventSource`/`Instant`/`SimulationEngine`（§6.1）✓
- **codec 只管 protocol representation**：`JSON text <-> DTO`（§6.2）；不调 Repository、不生成 Domain ID；保留"malformed entry 跳过、malformed document 失败"的当前行为（逐条独立解析而非整表严格反序列化）✓
- **Domain adapter 负责语义转换**（§7）：DTO→Domain（import defaults §5.3）+ Domain→DTO（export projection §7.2）；**不重建 missing metadata**（zoneId/localDate=null 表达未知，非当前时区声明）✓
- **错误边界清晰**（§7.1/§7.3）：无效必填字段 → entry 级 invalid（不生成替代 ID/当前时间/当前 zone/null event/写库）；missing/corrupt ID 是**唯一有意的 v1 例外**（`UUID.randomUUID()`——代码 `MahiroJsonFormat.kt:108,110` 证实，设计明确保留，**未偷偷改成 deterministic**）✓
- **导出**：明确 `Instant` 不可表示为合法 v1 timeH 时显式失败（不 clamp/不替换）✓
- 审计准确性：§5 wire contract 与 `MahiroJsonFormat.kt`/测试逐字段一致（本审阅 grep 复核：extras 六键、UUID 行为、meta/labResults/doseTemplates 语义）✓

## Repository import verdict

- import service 只经 `DoseEventRepository.insert` 写入（§7.3）——**codec 不写库、adapter 不写库、无 DAO 直写、无绕过** ✓
- 每有效事件一次 insert、源文件顺序 ✓；结果独立计数（Inserted/Idempotent/Conflict/Invalid）✓
- storage/infrastructure 异常 → 停止 + failed 摘要（partial counts + failing index，不伪称原子成功）——与当前 bridge 行为一致（无文件事务契约）✓
- 无 upsert/clear-and-import/fallback/legacy writer ✓（§7.3 + §14.3 测试断言）
- weight 回调仅在成功导入后应用（保留当前行为）✓

## Idempotency/conflict verdict

- **完整复用 Repository 语义**：same ID + same content → Idempotent（首行保留）；same ID + different content → Conflict（不覆盖）——import service **不实现第二套 conflict logic**（§7.3 直接映射结果）✓
- 明确：missing/corrupt ID 生成的随机 UUID **不是 idempotent retry**（§14.3 "missing/corrupt IDs are not treated as idempotent retries"）✓
- repeated import / duplicate JSON / mixed batch 的结果模型（独立计数摘要）足够明确 ✓
- replay 语义继承 Repository（Idempotent 计入摘要，无特殊路径）✓

## MedicationPlan semantics verdict

- **JSON v1 不含 MedicationPlan**：§5.1 顶层字段（meta/weight/events/labResults/doseTemplates）无 plans——plan 的 createdAt/slot-ID/会话语义不受 Batch 7 影响（无 plan import/export 路径）✓
- **P2-F1**：设计未显式声明"v1 协议不包含 plan"——任务 §五/§十六 的 plan 语义问题应得到显式"不适用"答复（当前仅由字段表隐含）。建议 §5 或 §3.2 补一句。
- 设计未触碰 `MedicationPlanEditSessionFactory` canonicalization / slot UUIDv5 / plan Repository ✓

## DoseEvent/time semantics verdict

- import：`occurredAt = LegacyTimeAdapter.timeHToInstant(timeH)`（finite/Long/multiply/round 语义，§7.1）；zoneId/localDate=null（不重建）✓
- 不重新定义 timezone semantics；不提前移除 timeH（§17 明确保留于 Entity/DAO/migration shadow/PK 输入/JSON wire/显示 helper）✓
- 不 canonicalize 成不同时间（timeH 仅经锁定 adapter 量化，§8 "quantized only by the already locked LegacyTimeAdapter"）✓
- DST 行为不变（无 atZone 调用；Predictor 的 system-zone 行为保留于自身边界 §9.2）✓
- source=JSON_V1、status=RECORDED、revision=1、slotId=null 的 import defaults 固定（§5.3）✓

## PK adapter verdict

- **纯 Kotlin、无 Android/Room/Repository/UI 依赖**（§10 + §19 清单）✓
- 结构映射明确：id/route（ADR-015 共享枚举直映）/occurredAt→timeH（LegacyTimeAdapter）/doseMG/ester/extras 六键显式穷举 ✓
- **不 selection/sort/filter/call SimulationEngine**（§10.2 职责外移：Repository selection、RECORDED 过滤、antiandrogen 过滤、未来事件生成、顺序、重复时间戳、simulation 范围/tolerance 全归调用方）✓
- 失败语义：typed failure 或显式兼容异常（Instant 不可表示），无当前时间/zone/Locale/随机 ID/clamp ✓
- Predictor 保留独立边界（schedule/DST/冲突窗口/排序/随机预测 ID），仅可复用共享显式映射 helper（§9.2/§10.3）✓
- HRT/Widget 使用 adapter；Home/Wear 无第二投影（§9.3/§12）✓

## Numerical parity verdict

- **1e-6 tolerance 不变**（§15/§3.2）；参数与算法测试不变且不放宽 ✓
- parity oracle = **结构（adapter 输入/输出集合）+ 数值（timeH/浓度样本/AUC/当前浓度/边界时间戳）双比较**，明确"final-value-only 不足"（§15）✓
- 序不变性冻结：30 天/20 事件规则、PATCH 匹配输入序敏感、重复时间戳不 dedup、无 ID tie-breaker（§11）✓
- same domain data → same PK result：设计无 selection/ordering/过滤进入 adapter 的路径；**未发现会导致数值差异的设计风险** ✓

## Batch7/Batch8 boundary verdict

- Batch 8 保留：migration safety、real DB validation、release gate、v2→v3 production safety、repair/migration（§23/§18）✓
- Batch 7 明确不做：release v3、删 legacy fields、loosen migration、real-user DB ✓
- schema gates 表（hash/SHA 引用）与本仓库锁定值一致 ✓

## Wear boundary verdict

- §Deferred Wear product requirements 显式列出未来 Wear app/Tile 需求（浓度/曲线/前 2 次/当前动作/后 5 次/confirm/postpone；Tile 无曲线）——**全部 deferred** ✓
- Batch 7 不实现 timeline/snooze/Tile/新协议/occurrence matching；Wear 协议与 dashboard 值不变（§9.3/§12）✓

## Widget boundary verdict

- 无 Widget Material You 动态取色/透明度实现；Widget protocol 不变（§2 排除）✓
- **P2-F3**：文档未显式声明 Widget deferred（无越界——仅建议补一句）

## Custom medication boundary verdict

- 设计无 Route.CUSTOM、无 fake Ester.E2、无 JSON custom hack、无 PK placeholder、无 extras 伪 Domain 模型（全文档 grep 无越界倾向）✓
- **P2-F2**：文档未显式声明 Custom medication deferred（任务 §九 的确认项应由文档明确答复；建议 §2 或 deferred 章节补一句"未来其他药物=medication identity，非 Route.CUSTOM，本轮无实现"）

## Test-plan verdict

- JSON（§14.1/§14.2）：encode/decode、round trip、corrupt、missing/corrupt ID（含注入 UUID supplier）、enum 错误、optional、deterministic preserved fields、golden 全文本字节相等（固定 Clock）、metadata 有意省略断言 ✓
- Import（§14.3）：inserted/idempotent/conflict/mixed/storage/repeated ✓
- PK adapter（§15）：mapping/behavior parity/ordering parity/numerical parity ≤1e-6（结构+数值双 oracle）✓
- Integration：JSON→Domain→Repository（disposable v3）+ Domain→PK ✓
- 无 real user DB；fixtures synthetic only（§18/§22）✓；无 @Ignore/skipped/loop-only assertions（§22）✓

## Implementation-split verdict

- §21 四阶段（A JSON DTO/纯 adapter → B JSON production cutover → C 正式 PK adapter + parity → D consumer cutover + bridge removal），每阶段原子边界 + 独立 review + stop conditions ✓
- 与任务 7A/7B/7C 对应：A≈7A、B≈7B、C≈7C、D=7C 收尾——**可独立实施、独立测试、独立 review** ✓
- 设计无"必须在实现中临场决定"的关键问题（全部语义已锁定）→ **无 P1** ✓

## Findings

### P0
None.

### P1
None.

### P2

**F1 — JSON v1 不含 MedicationPlan 未显式声明**
- Severity: P2
- Design section: §5.1（顶层字段表）
- Problem: 字段表隐含无 plans 字段，但未显式声明"v1 协议不包含 plan"
- Why it matters: 任务 §五/§十六 的 plan 语义/plan-before-event ordering 问题需要显式"不适用"答复；未来读者可能误以为 v1 含 plan
- Required design clarification: §5 或 §3.2 补一句"JSON v1 顶层协议不含 MedicationPlan；plan 导出/导入不在 Batch 7 范围"
- Blocks which sub-batch: 无（全部子批次）

**F2 — Custom medication deferred 未显式声明**
- Severity: P2
- Design section: 全文（无越界，缺显式声明）
- Problem: 设计无任何 Route.CUSTOM/fake E2/JSON custom hack——符合，但未按任务 §九 显式确认延期
- Why it matters: 审阅确认项需要文档层面的明确答复
- Required design clarification: deferred 章节补一句："未来'其他药物'= medication identity，非 Route.CUSTOM，本轮无实现"
- Blocks which sub-batch: 无

**F3 — Widget deferred 未显式声明**
- Severity: P2
- Design section: §2（排除 Widget protocol，未提 Material You/透明度）
- Problem: 无越界实现，但未显式声明 Widget Material You/透明度继续延期
- Required design clarification: §2 或 deferred 章节补一句
- Blocks which sub-batch: 无

## Independent checks

- **git state**：分支 `phase1/batch7-design`；HEAD = `77b1430f`（UI stabilization merge commit，`git cat-file -t` 确认为 commit）；唯一未跟踪文件 = 设计文档；暂存区空；`git diff --check` 通过；`stash@{0}` 原样（来自另一工作树的 UI 分支 checkpoint，本工作树无 stash 操作）
- **design file reviewed**：`docs/phase-reports/PHASE_1_BATCH_7_DESIGN.md` 完整 932 行
- **related code inspected**：`MahiroJsonFormat.kt`（extras 六键映射、missing/corrupt ID 的 `UUID.randomUUID()` 行为 L108/110 与设计 §5.2/§7.1 一致）、`Batch6DoseEventCompatibility.kt`（import/export/project 结构 §4 引用准确）、`core/time/LegacyTimeAdapter.kt`（timeHToInstant/instantToTimeH 存在）
- **no implementation executed**：本轮零代码变更、零 Gradle 运行（设计审阅，无需构建）

## Final decision

**APPROVE WITH P2**

批准标准逐项确认：P0=0 ✓；P1=0 ✓；JSON semantics 锁定 ✓；import semantics 锁定 ✓；conflict/idempotency 锁定 ✓；time semantics 锁定 ✓；PK adapter 边界锁定 ✓；numerical parity 要求锁定 ✓；7A/7B/7C 可独立实施 ✓；Batch8 边界清楚 ✓；Custom/Wear/Widget 无越界 ✓。

**Batch 7 implementation may begin with Batch 7A only.**

- 7A（JSON DTO/codec/Domain adapter + 测试）可先行实施，实施后需独立 review 方可进入 7B
- 7B（import service + HRT cutover）在 7A review 通过后
- 7C（PK adapter + parity + consumer cutover + bridge removal）在 7B review 通过后，且为最终实施门（§21 Stage D）
- **不得直接授权同时实施 7A/7B/7C**
- Widget / Custom medication / Wear feature expansion / release：继续禁止
- Room v3：仍 internal/unreleasable
