# Evolune Phase 1 Batch 6 Replay-Policy Addendum Independent Review

Date: 2026-08-06
Reviewer: DeepSeek (independent read-only design review)
Branch: `phase1/batch6-replay-policy-design`
Prerequisite tag: `phase-1-batch-6b`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 1**（唯一 P2 = 已接受 Wear action replay 无法重新验证首次 payload 中的原始 plan_id；另有 1 项实施前措辞强化建议，同属 P2 级、不阻止）
- **允许提交 addendum**
- **允许创建 replay-policy design 标签**
- **允许随后实施 replay-policy 前置修复**（共享 recorder 改造 + Notification/Widget 显式选 policy + 测试 + 独立 review + 单独 tag）
- **不得直接开始 Batch 6C**：必须按 §11 顺序，先完成前置实现并单独审阅、打标签
- 最大剩余风险：plan_id replay 验证限制（P2，受 action_id 随机唯一性与 recorded_at 稳定性约束，实际误确认场景极窄；Batch 8 发布门槛前有协议版本化评估点）；Room v3 仍不可发布

## Git and scope

- 分支 = `phase1/batch6-replay-policy-design` ✓
- `git merge-base --is-ancestor phase-1-batch-6b HEAD` 退出码 0；`phase-1-batch-6b` tag 指向 HEAD（`git tag --points-at`）✓
- 暂存区空；`git diff --check` 通过 ✓
- 唯一工作树变化 = `docs/phase-reports/PHASE_1_BATCH_6_REPLAY_POLICY_ADDENDUM.md`（未跟踪，已完整直接读取 374 行）✓
- 无代码、测试、schema、migration、Gradle、Manifest、数据库、APK、日志或真实数据变化 ✓
- 设计方声称"未修改代码"独立核实：全仓 grep `FirstAccepted|ReplayPolicy|expectedOccurredAt` 在生产代码零命中（仅 WearDataLayer 的 `recorded_at` key 常量为既有字段）✓

## Problem-definition verdict

- Stop finding 1 准确：`RecordDoseEventAction.kt:45-48`（existing → `toReplayResult`）与 `:65-67`（insert 竞态后重读）当前只检查 `source == expectedSource`（`RecordDoseEventAction.kt:81`），不检查 occurredAt 或内容——同 ID 同 source 异内容会被误分类为 replay ✓
- Stop finding 2 准确：Wear payload 仅 `plan_id`/`action_id`/`recorded_at` 三字段（`WearDataLayer.kt:148-155` 读取 + `DoseTileService.kt:214-216` 写入）✓；route/dose/ester/extras 从手机当前计划物化（`WearDataLayer.createWearDoseEvent` L192-203 用 `plan`）✓；`core/model/DoseEvent.kt:10-27` **无 planId 字段** ✓
- Repository idempotency 与 application command deduplication 的区分正确：前者 = 完整持久化内容相等的证明（`RoomDoseEventRepository.insert` L87 `existing == event` 全字段 data class equality）；后者 = 逻辑命令首次接受识别 ✓
- 该区分不会让 application 层吞掉真正 conflict：policy 显式选择、`FirstAcceptedBySource` 仅限本地确定性 ID（外部无法注入任意 ID）、Wear 使用双条件 policy、`RepositoryStrict` 下 Repository Conflict 永不重释（§3.1 flow 6）✓

## Repository-idempotency verdict

- `RoomDoseEventRepository.kt:68-95`：revision 检查 → entity 映射 → `insertEventIfAbsent`（主键冲突 -1）→ 冲突时重读 → **`existing == event`（完整 DoseEvent data class 比较，含 route/occurredAt/zoneId/localDate/doseMG/ester/extras/slotId/source/status/revision）→ Idempotent，否则 Conflict** ✓
- 冲突行永不覆盖（冲突分支无写路径）✓
- addendum §1 "not a defect in RoomDoseEventRepository" 属实；addendum 不修改、不削弱该语义（无代码变化）✓

## Policy separation verdict

- 三种 policy 边界清晰：`RepositoryStrict`（完整候选可重建时）/ `FirstAcceptedBySource`（本地确定性身份）/ `FirstAcceptedBySourceAndOccurredAt`（Wear 专用）✓
- `FirstAcceptedReplay` 被明确定义为 application command result，**不得被命名/记录/测试为 Repository idempotency**（§6 "never be logged, reported, or tested as Repository idempotency"）✓
- 每种 policy 的接受集合与成功副作用集合互不混淆 ✓

## RepositoryStrict verdict

- 不预读（§3.1 flow 1）✓；直接 `insert(candidate)` 一次 ✓；只接受 Inserted / RepositoryIdempotent ✓；Repository Conflict 不重释、不重读 ✓；StorageFailure/Invalid/exception 均失败 ✓；不生成新 ID、不 fallback ✓
- 当前 Wear 不使用该 policy 的理由成立：Wear 协议不含完整计划快照，重试无法重建首次候选 → 严格策略会把合法 replay 全判为 conflict ✓

## FirstAcceptedBySource verdict

- 仅限 Notification scheduled occurrence 与 Widget minute action ✓；expected source 显式（REMINDER / WIDGET）✓
- ID 由本地确定性代码派生（`reminder:<planId>:<scheduledAtMillis>` / `widget:<planId>:<epochMinute>`，与 Batch 6 设计 §6.2/§6.4 锁定规则一致），**不接受任意外部 action ID**（§3.2 "must not accept an arbitrary externally supplied action ID"）✓
- 已存在事件：expected source 匹配 → FirstAcceptedReplay（携带存储的权威事件）；source 不符 → Conflict ✓；不覆盖首次事件 ✓
- insert 竞态冲突后一次重读、同规则 ✓；StorageFailure/异常不转 replay ✓
- **Wear 无法误用**：§9.2 类型隔离（`WearActionRecorder` 只暴露 source+occurredAt 流程，无 source-only 方法）+ 静态边界测试拒绝 Wear 包引用 `LocalActionRecorder`/`FirstAcceptedBySource` + 停止条件 §12 明确"permits the Wear handler to use local source-only policy → stop" ✓
- **无 P1**：吞掉外部冲突需要"可伪造的 source + 任意 ID"——本地 recorder 内部派生 ID 且不接收外部 ID，无法伪造 ✓
- 注意：`FirstAcceptedBySource` 比 Repository equality 弱是有意为之（§3.2 "intentionally weaker ... safe only because the recorder itself derives a trusted local deterministic ID for a specific local action kind"）——与 Notification/Widget 的既有已批准语义一致，不构成削弱 ✓

## Wear source-and-occurredAt verdict

- 仅 Wear 使用 ✓；参数无默认值（expected source = WEAR、expectedOccurredAt = `Instant.ofEpochMilli(recorded_at)`）✓
- action_id 解析后原样作为 `DoseEvent.id`（§3.3 flow 2；设计 §6.5 "The phone persists that exact action UUID as DoseEvent.id"）✓
- 已存在事件必须**同时**满足 `source == WEAR && occurredAt == recorded_at`（§3.3 flow 4），任一不符 = Conflict ✓
- existing 判定在 plan 读取之前（flow 3）→ **replay 不读计划** ✓
- 事件不存在时才解析 plan_id、读取当前 Domain plan、要求 enabled、首次物化（flow 6）✓
- 首次候选 `occurredAt` 严格用 payload `recorded_at`，非手机当前时间（flow 6 + §5.3）✓
- insert 竞态冲突后一次重读、同 source+occurredAt 规则（flow 9）✓
- 不根据当前计划重建已存在 action 的候选（flow 10 "Never rebuild a replay candidate from an edited current plan"）✓；不生成替代 ID ✓；conflict/failure 不删除 DataItem ✓

## Wear action-identity verdict

- 裁决内部一致：action_id = 命令身份（`DoseTileService.kt:210` 每 tap `UUID.randomUUID()`，一次性）；recorded_at = 稳定一致性校验（PutDataMapRequest 内容固定，数据层重投同一 DataItem → 值稳定）；plan_id = 首次物化输入 ✓
- 首次成功后已存 DoseEvent 为权威结果；计划编辑不影响同 action replay（replay 不读计划、不重建候选）✓
- 逐项确认不会出现：
  - 同 action_id 异 recorded_at 当成功 → occurredAt 不匹配 → Conflict ✓
  - 异 source 当 Wear replay → source 检查 → Conflict ✓
  - plan not found 写猜测事件 → 首次物化 PlanNotFound/PlanDisabled 拒绝、不写 ✓
  - invalid action_id 随机替换 → 拒绝（§4 "rejected without a database write, random replacement, DataItem deletion, or success report"）✓
  - 计划编辑导致旧事件被覆盖 → 无覆盖路径 + replay 不重建 ✓

## Plan-id P2 verdict

逐项核实（均通过）：

1. 限制确由 payload（仅 3 字段）+ Domain 模型（DoseEvent 无 planId）共同造成：replay 时无法将 payload 的 plan_id 关联到首次物化时的计划快照 ✓
2. 不改 payload/Domain/schema 无法完整验证 ✓（addendum 明确不改）
3. 首次接受仍验证 plan_id（§3.3 flow 6）✓
4. replay 时 action_id + recorded_at + WEAR source 能阻止主要误确认场景：同 ID 异时间、异 source、计划编辑后内容漂移全部被挡 ✓
5. 碰撞现实约束：action_id 为每 tap 随机 UUID（`DoseTileService.kt:210`），相同 action_id 由不同计划正常产生需要 UUID 碰撞或 watch 复用随机 ID——非现实场景 ✓
6. 不覆盖既有数据库事件 ✓
7. conflict/StorageFailure 不删除 DataItem（§7）✓
8. Batch 8 发布门槛前有明确重新评估点（§4 "Protocol versioning can be reconsidered before the Batch 8 release gate"）✓
9. 报告未把限制描述为完整安全验证（§4 "This is a known P2 protocol limitation, not complete action-authenticity validation"；§14 同）✓

**不升级为 P1**：所有 P1 触发条件均不成立（action_id 高质量唯一 ✓；watch 不重复使用 action_id ✓；同 action_id 异计划不可正常产生 ✓；source+occurredAt 匹配足以防常见误确认 ✓；DataItem URI = `/hrt/dose-actions/<actionId>` 与 action_id 一对一唯一（`DoseTileService.kt:39,212`）✓；recorded_at 重试稳定（DataItem 内容快照）✓）

## DataItem acknowledgement verdict

- 协议无独立 ack path 独立核实：wear module 无 ack 字段/message/deleteDataItem（grep 确认；wear 侧只有 putDataItem + plans 监听 + request）；手机侧 `WearDataLayer.kt:172-174` 的 `deleteDataItems(item.uri)` 是唯一成功确认 ✓
- addendum 锁定：仅 Inserted / RepositoryIdempotent / 合法 Wear FirstAcceptedReplay 后才删除 ✓；Conflict/Invalid/PlanNotFound/PlanDisabled/StorageFailure/异常/取消均保留 ✓（§7）
- 删除失败不回滚数据库；重投安全识别为 FirstAcceptedReplay 并重试删除 ✓；只删当前 action 对应 URI（`item.uri` 精确定位）✓；不虚构新 ack 字段/path/存储格式 ✓
- 删除发生在持久化结果确认之后（§6 副作用规则 + §7 顺序）——**无 P0** ✓
- 当前实现（6B 后未切换）仍为"处理完无条件删除"——addendum 明确该修复属 6C 实施范围（§5.3 + §11），本阶段仅设计 ✓

## Concurrency verdict

- 相同 Wear action 并发：双预读空 → 双物化双 insert → 一 Inserted、另一 Idempotent（候选完全一致时）或 Conflict（内容不同时）→ conflict 重读 → source+occurredAt 匹配转 FirstAcceptedReplay ✓ 不重复创建、不覆盖 ✓
- 同 action_id 异 recorded_at：恒 Conflict、不删 DataItem、不生成新 ID（§8.2）✓
- Notification/Widget 竞态：source 匹配 first accepted、不符 conflict、不覆盖、不依赖全局永久锁（Repository 原子性为最终守护，§8.1 "A global permanent lock is not required for correctness"）✓
- StorageFailure 不会被当作"可能已成功"而确认：StorageFailure 保留 DataItem + 无成功副作用（§6/§7）✓

## Result-model verdict

- 七类结果显式区分：Inserted / RepositoryIdempotent / FirstAcceptedReplay / Conflict / Invalid / StorageFailure / UnexpectedFailure（+ PlanNotFound/PlanDisabled 首次物化拒绝）✓（§6 表）
- 成功副作用仅限前三类 ✓
- FirstAcceptedReplay 携带存储的权威事件（§6 "must carry the stored authoritative event"），命名/记录/测试不与 RepositoryIdempotent 混同 ✓
- Conflict 不执行 Notification/Widget/Wear 成功副作用 ✓；Wear DataItem 删除逻辑能区分（§7 条件列表）✓
- 不需要修改 Repository contract（§2 principle 9 + §13）✓

## API-shape verdict

- Option C（调用方自行预读）被拒绝，理由成立（逻辑复制、命名不一致、冲突可被静默削弱）✓
- 推荐 Option B：`LocalActionRecorder` + `WearActionRecorder` 委托单一私有 policy engine ✓ 满足全部要求：
  1. policy 无默认值（engine 无默认 policy；recorder 类型即显式选择）✓
  2. 调用点显式选择（Notification/Widget 用 local recorder、Wear 用 wear recorder）✓
  3. Wear 显式提供 expectedOccurredAt（`FirstAcceptedBySourceAndOccurredAt(expectedSource, expectedOccurredAt)` 无默认参数）✓
  4. Wear 无法轻易构造 FirstAcceptedBySource（类型不暴露 + 静态边界测试拒绝）✓
  5. equality/竞态重读集中在私有 engine（§9.2 "Both recorders delegate equality, insert-result mapping, exception classification, and one-time conflict re-read to one private engine"）✓
  6. Notification/Widget/Wear 不复制逻辑 ✓
  7. Repository contract 不变 ✓
  8. 类型设计防止误用（closed action kind、source-only 方法不存在于 Wear recorder）✓
- Option A 备选有明确护栏约束（无默认 policy、受限构造、显式 accepted-reason 结果、静态边界测试），可接受但非首选——设计判断合理 ✓
- 若实施中需要两个独立 action 类（而非共享 engine），addendum §9.2 结构已兼容，属实现细节而非设计修订 ✓

## Notification regression verdict

- ID 规则不变（`reminder:<planId>:<scheduledAtMillis>`，§5.1）✓；expected source = REMINDER 真实枚举 ✓
- occurredAt = 首次接受处理时间；延迟重投（当前时间已变）保留首次事件并返回 FirstAcceptedReplay ✓
- FirstAcceptedReplay 后可执行既有成功副作用（refresh + cancelNotification）✓；异 source = Conflict、无成功副作用 ✓；不覆盖 ✓
- 不改变 reminder schedule、PendingIntent、通知协议 ✓（与 6B 行为一致，测试矩阵 §10.2 项 15 保回归）

## Widget regression verdict

- ID 规则不变（`widget:<planId>:<epochMinute>`，§5.2）✓
- **分钟级折叠是既有已批准行为，非 addendum 新增**：Batch 6 设计 §6.4 已锁定（"preserve the existing ... widget:<planId>:<epochMinute>" + "The first accepted event wins within the existing plan/minute key"），6B 实现与 review 均按此执行（WidgetWorkTest 同分钟稳定性断言）→ **无 P1** ✓
- 首次精确 occurredAt 保留（不 floor，设计 §6.4 "do not floor the persisted instant"）；后续同分钟处理时间变化 → FirstAcceptedReplay ✓
- accepted 后刷新 ✓；不改变 PK 时间精度/算法/Widget protocol ✓（§5.2 "does not change PK time precision or persistence precision"）

## Wear materialization verdict

- 首次物化顺序（§3.3 flow 1-8）：验证 action_id → 验证 recorded_at → 验证 plan_id → 读当前 Domain plan（enabled）→ 构造完整 DoseEvent（source=WEAR、status=RECORDED、revision=1、zoneId/localDate 显式、slotId=null）→ occurredAt 严格 = recorded_at → insert 一次 ✓
- 不用 PK/legacy 子集（对比当前 `createWearDoseEvent` L192-203 的 legacy 格式——6C 将替换为完整 Domain 物化）✓
- 不修改 payload 或 Domain ✓
- **plan 编辑只影响尚未首次接受的 action，已披露**（§4 "Once the first event is persisted, that stored event is the authoritative materialized result and later plan edits do not alter replay classification"）✓

## Test-matrix verdict

- §10 共 32 项覆盖 policy isolation（1-6）/Notification/Widget（7-15）/Wear（16-26）/并发回归（27-32）✓
- Wear 覆盖：首次 insert（16）、同 ID 同 recorded_at（17）、同 ID 异 recorded_at（19）、异 source（20）、plan 编辑后重投（18）、replay 不读 plan（17）、plan not found（23）、invalid UUID/时间（22）、StorageFailure/异常/取消保留 DataItem（24）、删除条件（25）、删除失败重投（26）、并发（27-28）、不同 plan_id P2 fixture（21）✓
- 与审查要求的差异：项 17 "without plan lookup" 字面是结果断言——**P2 观察**：建议实施时将矩阵措辞明确为"plan repository 调用计数 == 0"（fake 计数断言），与审查要求"证明 replay 时 plan repository 零调用，而不只是结果相同"对齐；设计本身已强制顺序（existing 判定先于 plan 读取），故不构成 P1
- 回归：Room full-content equality（31）、6B Notification/Widget（15/30）、schema/contract/Domain/protocol 无 diff（32）✓
- 合成 fixtures + 可丢弃 Room（§10 "All fixtures remain synthetic... the production database must never be opened"）✓

## Implementation-staging verdict

- 顺序正确：前置实现（共享 recorder 改造 + Notification/Widget 显式 policy + 测试 + 独立 review + **单独 tag**）→ 6C（Wear source+occurredAt + DataItem 删除确认 + bypass-zero）✓
- addendum 阶段不实施代码（Status: design-only，§1）✓；前置修复不会被伪装为 6C 完成（§11 明确分离）✓；6B 报告/review 不重写 ✓；正式 `phase-1-batch-6` 标签仍需等 6C（§11）✓
- 停止条件 14 项（§12）覆盖全部 P0/P1 情形 ✓

## Schema/protocol/release-safety verdict

- 持续禁止：修改 Repository contract、削弱 Room equality、修改 Domain/schema/migration、修改 Wear payload/path/key、legacy fallback、随机替换 invalid action ID、真实数据库、release、声称 v3 可发布 ✓（§2/§12/§13）
- Room v3 internal-only；schema 2/3、MIGRATION_2_3、Slot ID v1、JSON v1、PK 不变（§13）✓
- 无数据迁移；既有 6B 事件不重写（§11）✓
- 6C 未开始 ✓

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 已接受 Wear action replay 无法重新验证首次 payload 的原始 plan_id（addendum 声明的唯一 P2）**
- Severity: P2（接受，不升级）
- 依据: `DoseTileService.kt:210-216`（每 tap 随机 UUID + 一次性 recorded_at）、`DoseEvent.kt:10-27`（无 planId）、`WearDataLayer.kt:148-155`（3 字段 payload）、`RecordDoseEventAction` 现状（addendum §1 描述准确）
- 影响: 同 action_id + 同 recorded_at + 异 plan_id 的重投不可检测；受随机 UUID 唯一性约束，现实不可达；不覆盖、不误确认（source+occurredAt 双条件拦截其余场景）
- 最小修订建议: 无（addendum §4/§14 已如实披露；Batch 8 门槛前协议版本化评估）
- 是否阻止提交或实施: 否

**F2 — 测试矩阵项 17 措辞可强化（实施前微调建议）**
- Severity: P2
- 文件: addendum §10.3 项 17（"returns FirstAcceptedReplay without plan lookup"）
- 问题: 字面是结果断言；审查要求为"证明 replay 时 plan repository 零调用，而不只是结果相同"。设计已强制顺序（§3.3 flow 3/6：existing 判定先于 plan 读取），实施时须以 fake 调用计数断言落实。
- 影响: 无（设计约束已存在；属测试矩阵措辞精度）
- 最小修订建议: 实施前置修复时，在矩阵项 17/18 补充"plan repository 调用次数 == 0"断言说明（或实施报告明确记录该断言）
- 是否阻止提交或实施: 否

## Independent validation performed

实际执行（本会话，全部 read-only）：

- Git：`git branch --show-current` / `git status --short` / `git diff --name-status` / `--stat` / `--check` / `--cached` / `git ls-files --others` / `git merge-base --is-ancestor phase-1-batch-6b HEAD`（exit 0）/ `git tag --points-at phase-1-batch-6b` / `git log --oneline -6` —— 分支正确、唯一变化为 addendum、暂存区空 ✓
- 完整读取：`PHASE_1_BATCH_6_REPLAY_POLICY_ADDENDUM.md`（374 行）、`WearDataLayer.kt`（203 行）、`DoseTileService.kt`（374 行）、`RoomDoseEventRepository.kt`（179 行）、`core/model/DoseEvent.kt`、`core/model/DoseEventSource.kt` ✓
- 本会话既有依据（6B 审阅已完成）：`RecordDoseEventAction.kt`（90 行）、`RecordDoseEventActionTest.kt`、`ReminderReceiverWork.kt`、`ReminderDoseFactory.kt`、`MedicationNotificationActionReceiver.kt`、`WidgetWork.kt`、`EvoluneWidgetReceiver.kt`、`Batch 6 设计`（§6.1-6.6）、`PHASE_1_BATCH_6B_REPORT.md`、`PHASE_1_BATCH_6B_REVIEW.md` ✓
- grep 核查：生产代码 `FirstAccepted|ReplayPolicy|expectedOccurredAt` 零命中（确认无代码实现）；wear module `ack|deleteDataItem|DataItem|path` 18 处命中——无 ack 字段/路径，删除仅在手机侧 `WearDataLayer.kt:172-174` ✓
- 静态事实确认：payload 三字段；`recorded_at` 默认值 `System.currentTimeMillis()`（`WearDataLayer.kt:152-155`）为 6C 须移除项（addendum §4 已规定缺失即拒绝）；当前 `deleteDataItems` 无条件调用为 6C 修复点（addendum §7/§11 已规定）✓
- **未运行任何测试/构建**（本阶段为设计审阅；未实施代码，不声称验证未实施内容）

## Final decision

**APPROVE WITH P2**

APPROVE WITH P2 条件逐项确认（全部满足）：

1. policy 无默认值 ✓（§2 principle 1、§3.3 "explicit and have no defaults"）
2. Wear 无法使用 source-only replay ✓（类型隔离 + 静态边界测试 + 停止条件）
3. recorded_at 重试稳定 ✓（DataItem 内容快照固定；`DoseTileService.kt:216` 一次性生成）
4. replay 不重新读取计划 ✓（§3.3 flow 3/6 顺序 + flow 10）
5. conflict/failure 不删除 DataItem ✓（§7 条件列表）
6. FirstAcceptedReplay 与 Repository idempotent 明确区分 ✓（§1/§2/§6）
7. plan_id 限制确实仅为 P2 ✓（§4/§7 论证 + 独立核实 9 项全部通过）
8. 测试矩阵可证明上述规则 ✓（§10；项 17 措辞强化建议见 F2）
9. 无新 P0/P1 ✓

最终结论：

- addendum 提交前是否仍需修订：**否**（F2 为实施前微调建议，不要求修订 addendum 本身）
- 是否保留唯一 P2：**是**（plan_id replay 验证限制）
- 是否建议提交 addendum：**是**
- 是否建议创建 replay-policy design 标签：**是**
- 是否建议随后实施 replay-policy 前置修复：**是**（共享 recorder 改造 + Notification/Widget 显式选 policy + 测试 + 独立 review + 单独 tag；实施须满足 §12 停止条件与 §10 测试矩阵）
- **明确不得直接开始 Batch 6C**：前置修复完成并独立审阅、打标签后方可进入 6C（Wear source+occurredAt + DataItem 删除确认）
- 是否继续禁止真实数据库和 release：**是**——Room v3 保持内部、不可发布，直至 Batch 7/8 完成
