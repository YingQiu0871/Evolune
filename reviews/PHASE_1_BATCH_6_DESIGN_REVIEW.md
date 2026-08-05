# Evolune Phase 1 Batch 6 设计审阅报告

**审阅日期**: 2026-08-05
**审阅者**: DeepSeek（独立高级架构审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch6-design`（HEAD: `c3b7dc2`，前置 tag `phase-1-batch-5`）
**方式**: 只读设计审阅；未修改设计/代码/测试；未实施 6A/6B/6C；未暂存/提交/打标签

---

## Executive summary

- **最终决定**: **REQUEST CHANGES**
- **P0/P1/P2**: **0/1/5**（设计自称 0/0/5；本轮发现 1 个 P1：receiver 异步生命周期未在设计正文锁定）
- **是否允许提交设计**: 否（P1 补充后）
- **是否允许提交后进入 6A**: 否（P1 修订后）
- **最大剩余风险**: receiver（goAsync/PendingResult）生命周期若实现不当会造成 ANR/结果丢失；其余设计要素（metadata/CAS/JSON-PK 边界/Wear ack/绕过清零）均已充分锁定。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch6-design` ✓ |
| 前置 tag | `phase-1-batch-5` 为 HEAD 祖先（exit 0）✓ |
| Batch 5 正式封存 | ✓（tag `phase-1-batch-5` 指向 `21cc80c`，即 Batch 5B 切流提交）|
| 工作树变化 | 仅 1 个未跟踪文件：`docs/phase-reports/PHASE_1_BATCH_6_DESIGN.md` ✓ |
| 暂存区 | 空 ✓ |
| `git diff --check` | 通过 ✓ |
| Kotlin/测试/schema/migration/Gradle/Manifest 变化 | 无 ✓ |
| 数据库/APK/日志/真实数据 | 无 ✓ |

---

## Authoritative Batch 6 scope verdict

- `docs/PHASE_1_DESIGN.md:856-867` 权威范围核实：HRTViewModel、记录 UI 和格式化组件、reminder receivers/factory/matcher、EvoluneWidgetReceiver/WidgetUtils、WearDataLayer、composition root —— 设计 §1 引用精确 ✓
- 验收标准核实（L867）：UI/通知/Widget/Wear 不直接访问 DAO/Entity；Wear payload 不变；快速记录时间精度/稳定 ID/±1 小时窗口/Widget 刷新不变 —— 设计各节均覆盖（§6.2-6.5、§15 停止条件）✓
- **Batch 7 内容未提前纳入**：JSON DTO/adapter（§7）、正式 Domain-to-PK adapter（§8）、SimulationEngine/PK 参数（§8）全部明确推迟 ✓
- **与权威范围一致**，无 P1 冲突。

---

## Production bypass inventory verdict

**全仓独立 grep（76 个 DAO/Entity/legacy 引用）与设计 §4 21 项清单逐项对比**：

| 设计引用 | 实际核实 |
|---|---|
| `MainActivity.kt:40-44` | L40-42 构造两个 legacy repo + provider ✓ |
| `HRTViewModel.kt:37-69` | L38-39/56/64 legacy 依赖 ✓ |
| `HRTViewModel.kt:107-119` | L107-109 upsertEvent、L116-118 deleteEvent（legacy）✓ |
| `HRTViewModel.kt:131-142` | L131-139 JSON import → legacy upsert ✓ |
| `HRTViewModel.kt:182-248` | L182-186 PK 选取、L231/246 SimulationEngine ✓ |
| `MedicationRecordsScreen.kt:46-104` | L51/65-73 quick-add、L91/100-101 upsert/delete ✓ |
| `MedicationRecordBottomSheet.kt:54-60,338-376` | L85/341/367-370 重构 PK event（丢 metadata）✓ |
| `MedicationRecordItem.kt:160-188` | 显示层（此前批次核实）✓ |
| `HomeScreen.kt:44-87` | L56-57/80-87 读 timeH/legacy plans ✓ |
| `MedicationReminderReceiver.kt:38-58` | L38-39 DAO 读 ✓ |
| `MedicationNotificationActionReceiver.kt:53-66` | L53-54/65 Entity upsert ✓ |
| `ReminderRescheduleReceiver.kt:22-26` | L22-23 legacy repo 构造 ✓ |
| `EvoluneWidgetReceiver.kt:105-121,211-237,245-260` | L111-112/215-227/246-248 ✓ |
| `WearDataLayer.kt:121-129,145-171` | L122-123/145/157/163 ✓ |

**无遗漏生产写入口**：grep 全部 76 个匹配落在 data 定义/mapper/Repository implementations + 设计已列出的 6 个生产文件；Widget 写、Wear 写、Notification action 写、HRT 写、JSON import 写全部在清单内 ✓。**无 P0/P1 绕过遗漏**。

---

## 6A/6B/6C split verdict

- 6A（手机 HRT/UI/CAS 编辑/composition root/私有 PK 桥）→ 6B（receiver/Widget）→ 6C（Wear/final 清零）——依赖顺序合理（UI 语义先于后台、后台先于跨设备）✓
- 每阶段：可独立编译/测试（§10 文件清单）；不半切换（§9 "No slice may invoke both old and new writers for one command"）；临时同库 legacy **读**仅限未封存中间提交且必须删除（§9）✓
- Repository 失败不 fallback（§13）✓
- 阶段完整审阅后才进下一阶段；Batch 6 标签只在全部完成后（§16）✓
- receiver+Widget 同放 6B：范围可接受（两者均为后台事件入口，共享命令处理器设计）；如需进一步拆分可建议按审阅提交细分，不创造新批次 ✓

---

## DoseEvent create metadata verdict

- 设计 §5.4 为每个入口锁定 metadata（对照真实 `DoseEventSource`/`DoseEventStatus` 核实）：
  - manual/quick add → `MANUAL`；JSON → `JSON_V1`；reminder → `REMINDER`；Widget → `WIDGET`；Wear → `WEAR` ✓（全部是真实枚举值）
  - status 仅 `RECORDED` ✓；revision=1 ✓（与 ADR-015 一致）
  - zoneId/localDate：显式 caller 提供、从不隐式读取（§5.2 "explicit ZoneId supplied by the caller"）✓
  - slotId=null（§6.1 禁止推断）✓
- 新事件用 `insert`（非 legacy upsert）✓；ID 在会话/入口边界生成一次 ✓；occurredAt 毫秒精度（§6.4 明确不 floor 持久化 instant）✓
- 幂等/冲突语义：同 ID 同内容 Idempotent、异内容 Conflict（§5.3、§6.2-6.5）✓
- 失败不重试 legacy DAO ✓
- **无 P1**（每种入口 metadata 已锁定）。

---

## DoseEvent CAS edit verdict

- 设计 §5.2：编辑会话持有**完整不可变 Domain 事件**（非 PK 子集）；expectedRevision = original.revision；`original.copy(...)` 保留 id/source/status/slotId/revision；未编辑时 zoneId/localDate 精确保留；显式时间编辑只更新时间上下文 ✓
- 不使用 PK model 重建 Domain（当前 BottomSheet L367-370 的重构被替换）——**P0 条件（metadata 丢失/PK 重建）不触发** ✓
- update 走 contract CAS（expectedRevision）；成功后 revision 由 `RoomDoseEventRepository.update` 递增（设计明示）✓
- RevisionConflict/NotFound/Invalid 不覆盖、不重插、不 fallback（§5.3）✓
- 失败不关编辑器（§5.3 "conflict or invalid result must not be presented as saved"）✓
- 重复保存不并发（§11.1 duplicate-submit gate 测试）✓；旧异步结果不覆盖新状态（in-flight gate 模式延续 5B）✓

---

## Delete verdict

- UI 删除走 contract `delete(id)`；Deleted/NotFound 明确；infrastructure 异常保留为错误；无 DAO 重试（§5.3）✓
- receiver/Widget/Wear 不使用 delete（无真实产品路径；skip/stale 走拒绝/忽略而非删除）✓
- 删除后派生刷新顺序明确（§6.2 notification/Widget refresh after accepted）✓
- 失败不假报成功 ✓

---

## HRT and private PK bridge verdict

- 设计 §8：私有消费者局部投影——保留 30 天/20 条双分支顺序、仅 RECORDED、逐字段复制、`LegacyTimeAdapter.instantToTimeH`（**不复制 3_600_000 常量**）、显式 ExtraKey 穷尽映射（非 ordinal）、antiandrogen 过滤/预测合并/1 小时冲突/模拟范围/步数/常量不变 ✓
- 仅在 HRT 和 Widget 的 PK 调用边界存在（§8 "HRT and Widget may use this private local bridge"）——**未扩散** ✓
- 非公开持久化 contract（private consumer-local）✓；不复制 PK 算法 ✓；不写数据库 ✓
- Batch 7 可明确替换删除（§8 "Batch 7 replaces the local bridge"）✓；有 parity 测试（§11.1 "HRT PK compatibility projection preserves list order..."）✓
- 名称/可见性：设计明确"must not introduce a public/general adapter"——不会成为事实正式 adapter ✓
- **无 P1**（未扩散为第二正式 adapter）。

---

## JSON v1 boundary verdict

- 设计 §7：MahiroJsonFormat 字段/UUID 规则/导出不变；不提前创建 DTO；PK 参数/SimulationEngine 不变 ✓
- JSON import 在 6A 后**不再走 legacy writer**：HRT 调用点用临时桥（`LegacyTimeAdapter.timeHToInstant` + 显式 ExtraKey + `source=JSON_V1`）→ contract `insert` ✓ —— **P1 条件（import 留 legacy writer）不触发** ✓
- insert/idempotent/conflict 锁定（§7.3）✓
- 桥为调用点专用、不可复用为 JSON API（§7）✓

---

## Receiver lifecycle verdict

**唯一 P1（F1）**——见 Findings。设计 §10.2 只声明 "make Android receivers thin provider/delegation shells"，未锁定 goAsync()/PendingResult 完成语义。

---

## Widget verdict

- 设计 §6.4：enabled plans 读走 contract；quick action 走 contract insert；确定性 ID 保留（`nameUUIDFromBytes("widget:<planId>:<epochMinute>")`）；重复 delivery 幂等（first-accepted）；冲突明确；source=WIDGET/slotId=null；Repository 成功后 Toast/refresh ✓
- **注意**：§6.4 明确终止 legacy upsert 的"last delivery overwrites time"行为（ADR-015 语义）——行为变更被显式声明为设计意图 ✓
- cache/snapshot 非事实来源 ✓；刷新数量/时机/展示不变 ✓；PK 桥不扩散 ✓
- 6B 无需修改 Widget protocol/Gradle/Manifest（§2.1 排除）✓

---

## Wear action and acknowledgement verdict

- action_id 手表生成（`DoseTileService` 每次点击随机 UUID，写入 DataItem path + action_id + 稳定 recorded_at）✓；手机不替换 ID（§6.5 "The phone must not generate a replacement ID"）✓
- 重复同 ID 同内容 → getById 检查 source=WEAR + occurredAt 一致 → replay ack ✓；不同 source/time → conflict、不覆盖、非虚假成功 ack ✓；StorageFailure → 不删 DataItem 保留重试（不提前 ack）✓ —— **P0 条件（提前成功 ack）不触发** ✓
- ack payload/protocol 不变；跨设备时钟差异不影响 action_id（action_id 独立于时间）✓
- listener 生命周期：设计 §10.3 修改 WearDataLayer + 现有 service scope 模式延续；Wear 端 DoseTileService/WearPlanListenerService **无需修改**（§10.3 明确）——需确认与真实 wear 端一致（此前批次已读 DoseTileService：enqueueDoseAction 生成 actionId ✓）
- contract 足以表达（InsertResult/getById/DeleteResult）✓ —— 无 contract 冲突

---

## Quick-record precision verdict

- 手机 quick add：保留 minute-floor 发生时间（§5.4 "preserve the current minute-floor occurrence time"）✓
- Widget quick action：实际处理毫秒、不 floor 持久化 instant（§6.4）✓
- notification action：确认时间毫秒（§6.2）✓
- Wear action：recorded_at 毫秒（§6.5）✓
- 各入口 ID 来源/zoneId/localDate/source/slotId/revision/idempotency key 全部锁定 ✓
- ±1 小时窗口：receiver 用半开区间 `[scheduledAt-1h, scheduledAt+1h+1ms)` + `abs(delta) <= 1h` matcher 保留边界（§6.3）✓
- 排序/PK 选取保留（§8）✓；无取整到分钟降低精度 ✓

---

## Composition-root verdict

- MainActivity 6A 后不再构造 legacy DoseEventRepository（§10.1）；HRTViewModel 注入 contract ✓
- receivers/Widget/services 用同一 production provider（§6 通用规则）✓
- 单 AppDatabase singleton（§12.10 扫描标准）✓；feature/UI 不获 DAO/Entity/AppDatabase（§12）✓
- provider 不持 Activity ✓；无 Hilt/Koin/第二 service locator（§2.1 排除）✓
- 6A 后无调用方的 legacy 实例应删除（§4 legacy repos 行："No production caller after Batch 6; definition may remain until Batch 8 cleanup policy permits removal"）——设计明确无调用方即不再构造 ✓

---

## Final bypass-zero verdict

- §12 提供 10 项可执行扫描标准（Entity 引用/DAO 调用/legacy 构造/upsert 调用/provider 单例/写入唯一路径/无 fallback/JSON 桥唯一/PK 派生/单 builder）✓
- 允许位置（persistence impl/mapper/migration/tests）与禁止位置（MainActivity/ViewModel/Compose/reminder/Widget/Wear/feature/navigation）明确 ✓
- JSON/PK Batch 7 过渡不直接访问 DAO/Entity/legacy（§7/§8 桥已限定）✓
- 可执行 grep/static audit（§12）而非仅人工声明 ✓

---

## Test matrix verdict

- 6A JVM（metadata/CAS/conflict/stale/NotFound/Storage/gate/JSON 边界/PK parity/无 legacy）✓
- 6A instrumentation（insert/reopen/CAS/revision/conflict/delete/rollback/**API 33+API 35 phone**/Compose）✓
- 6B（receiver async/idempotency/Widget/±1h/side-effect ordering）✓
- 6C（Wear OS AVD/action ID/retry/conflict/ack/disconnect/payload 兼容/phone-watch 边界）✓
- 全量（App JVM/双 phone connected/Wear OS 35/Repository/migration/mapper/core/PK 1e-6/builds/lint/KSP/hashes）✓
- **phone Compose tests 不得在 Wear AVD 上验收**（§11.3 "A Wear OS AVD result cannot satisfy any phone UI or phone receiver test"）——F1 教训已吸收 ✓
- 计数取自 JUnit XML（§11.5）；编译不等于设备执行 ✓

---

## Rollback and failure verdict

- insert failure/CAS conflict/NotFound/StorageFailure/生命周期取消/重复 delivery/ack 失败/Widget refresh 失败/notification 更新失败——全部覆盖（§13 + 各入口节）✓
- 数据库失败不触发成功副作用；数据库成功而派生失败不经 legacy 写回滚；无 destructive migration/清数据/silent fallback（§13）✓
- 源码回滚仅限未发布内部阶段 ✓

---

## P2 findings verdict

设计 5 个 P2 逐项核实：

| # | P2 | 真实 | 非阻断 | 归属 |
|---|---|---|---|---|
| 1 | core.model 依赖 PK Route/Ester | ✓ | ✓ | 独立 ADR |
| 2 | JSON/PK 窄调用点桥（Batch 7 前）| ✓ | ✓ | Batch 7 替换删除 |
| 3 | reminder/Widget 确定性 ID 非 UUIDv5（事件 action ID，非 Slot ID）| ✓ | ✓ | 设计明确不重解释 |
| 4 | slotId=null（payload 无稳定 slot 身份）| ✓ | ✓ | 未来协议/UI 提供时 |
| 5 | ARCHITECTURE 文档漂移 | ✓ | ✓ | 文档清理 |

**无"未锁定 source/slotId/revision/ack"被误列为 P2**——这些均已锁定 ✓。

---

## Schema and release-safety verdict

- schema/migration/contract/Domain/DAO/Entity 无变化（设计 §2.1 排除 + 本轮 git diff 全空）✓
- Room version=3；schema 2/3 与 MIGRATION_2_3 不变（设计 §11.5 门）✓
- 持续禁止：真实/派生数据库、内部 v3 APK 覆盖、release、destructive migration、提交敏感数据；Batch 6 完成不宣称 v3 可发布（§16）✓
- Batch 7/8 + ADR-016 门槛仍必须完成 ✓

---

## Report-quality verdict

设计包含全部要求章节（权威范围/调用链/绕过清单/metadata/insert-CAS-delete/JSON-PK 边界/6A-6B-6C/每阶段文件/测试矩阵/rollback/static audit/P0-P1-P2/停止条件/数据安全/v3 不可发布）✓；未声称已实施/绕过清零/Wear ack 已验证/真实库/v3 可发布 ✓。

**唯一缺口**：receiver 生命周期（见 F1）。

---

## Findings

### F1 (P1) — receiver 异步生命周期未在设计正文锁定

- **严重程度**: P1
- **文件**: `PHASE_1_BATCH_6_DESIGN.md:305-333`（§10.2 6B）
- **问题**: 设计只声明 "make Android receivers thin provider/delegation shells"，未锁定 `BroadcastReceiver.onReceive` 的 `goAsync()`/`PendingResult.finish()` 完成语义：命令处理器成功、Repository 失败、冲突、stale plan、基础设施异常、协程取消、进程终止时，PendingResult 必须在何时以何种方式 finish；未描述 receiver 协程 scope 的取消/生命周期归属。三个 receiver（MedicationReminderReceiver/MedicationNotificationActionReceiver/ReminderRescheduleReceiver）的异步工作都依赖这一语义。
- **触发条件**: 6B 实施时接收器在 onReceive 内启动异步命令处理。
- **影响**: 若实现误用（goAsync 未调用或 finish 遗漏），BroadcastReceiver 超时（约 10 秒）会触发 ANR/结果丢失；若命令处理器异常传播路径未与 finish 对齐，重复 delivery 可能被误判成功。
- **依据**: 审阅标准 §十二"如 receiver 生命周期未设计清楚，列为 P1"。现有代码（MedicationReminderReceiver/MedicationNotificationActionReceiver 的 goAsync + finally-finish 模式）可作基线，但设计未显式引用或扩展它。
- **最小修订建议**: 在 §10.2 补充 receiver 生命周期规则：(a) onReceive 同步调用 goAsync；(b) 命令处理器返回 typed outcome 后 finish（成功/拒绝/冲突均 finish）；(c) 异常路径 finally finish；(d) 协程 scope 用 SupervisorJob + 独立 scope（不依赖 receiver 生命周期之外的对象），取消不影响 PendingResult 完成；(e) 明确 stale plan/缺失 plan 的 finish 时机（拒绝后立即 finish）；(f) 引用现有 receiver 的 goAsync/finally 模式作为实现基线。
- **是否阻止实施**: 是（P1 未修订前不得提交设计/开始 6B；6A 不受影响可先行）

### F2 (P2) — 无（保留设计的 5 个 P2 作为设计自声明项，均真实）

### F3 (P2) — receiver/Widget 同放 6B 的提交粒度（建议性）

- **严重程度**: P2
- **文件**: `PHASE_1_BATCH_6_DESIGN.md:305-333`
- **问题**: receiver 与 Widget 共享 6B 原子边界；若实施时发现 6B 过大，可建议按审阅提交拆分为 receiver 与 Widget 两个提交（不创造新批次）。
- **影响**: 无。
- **是否阻止实施**: 否

**其余无 P0/P1**（绕过清单完整性、metadata、CAS、JSON/PK 边界、Wear ack、composition root、final bypass-zero、测试矩阵、rollback、5 个 P2 真实性均通过）。

---

## Independent validation performed

本轮为设计审阅，**未运行任何实现测试**（无实施代码）。实际执行：

- Git 边界：分支/祖先/封存 tag/单文件变化 ✓
- 设计文档全文 read（525 行）✓
- 权威依据：PHASE_1_DESIGN Batch 6（L856-867）、ADR-014/015/016、Batch 5 设计+三次审阅（此前批次核实）✓
- 全仓 grep 76 个 DAO/Entity/legacy 引用，与设计 §4 21 项清单逐项对比（全部精确、无遗漏写入口）✓
- HRTViewModel/RecordsScreen/BottomSheet/HomeScreen 行号抽查（设计引用精确）✓
- DoseEventSource/DoseEventStatus/insert-CAS-delete contract 语义核对（此前批次核实）✓

---

## Final decision

### **REQUEST CHANGES**

存在 1 个 P1（receiver 异步生命周期未锁定）。设计其余要素（绕过清单、metadata、CAS、JSON/PK 边界、Wear ack、测试矩阵、最终清零）质量高且与权威范围一致。

**提交前必须修改的设计内容**：
1. F1：§10.2 补充 receiver 生命周期规则（goAsync/PendingResult finish 于成功/拒绝/冲突/异常/取消路径；协程 scope 归属；引用现有 goAsync+finally 基线）。

**可以推迟的事项**：
- F3（6B 提交粒度，实施时可再细分）
- 设计的 5 个 P2（真实、非阻断、有归属）

**是否建议提交 Batch 6 设计**：否（F1 修订后再提交，标签 `phase-1-batch-6-design-v1`）。

**是否建议随后进入 6A**：是（F1 修订、设计提交后；6A 不依赖 receiver 生命周期细节，可先行，但设计必须整体封存后再开始实施）。

**是否继续禁止真实数据库和 release**：**是**。Room v3 仍处 ADR-016 内部不可发布区间；Batch 6 完成不授权 Batch 8/真实库演练/生产分发/release；Batch 7（JSON/PK 正式 adapter）仍必须完成。

---

*审阅结束。最终工作树：仅原设计文档 + 本审阅报告；未修改任何其他文件。*
