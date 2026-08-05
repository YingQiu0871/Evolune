# Evolune Phase 1 Batch 6 设计重新审阅报告（F1 复验）

**审阅日期**: 2026-08-05
**审阅者**: DeepSeek（独立重新审阅）
**项目目录**: `D:\Evolune-phase1`
**分支**: `phase1/batch6-design`（前置 tag `phase-1-batch-5`）
**第一次审阅**: `reviews/PHASE_1_BATCH_6_DESIGN_REVIEW.md`（REQUEST CHANGES，P0/P1/P2 = 0/1/5，唯一阻断 F1）
**方式**: 只读重新审阅；未修改设计/第一次 review/代码/测试；未实施 6A/6B/6C

---

## Executive summary

- **最终决定**: **APPROVE WITH P2**
- **P0/P1/P2**: **0/0/5**（原 5 个 P2 保留，均非阻断）
- **F1 是否解决**: **RESOLVED**（receiver 生命周期已由 §6.6 完整锁定，10 项清除条件全部满足）
- **是否允许提交设计**: 是
- **是否允许创建 `phase-1-batch-6-design-v1`**: 是
- **是否允许随后进入 6A**: 是（设计提交并打标签后）
- **最大剩余风险**: 无 P0/P1。receiver 生命周期为**设计层锁定**——实现与代码验证仍属 6B 阶段工作；进程终止可阻止 finally（设计已诚实声明为进程内保证）。

---

## Git and scope

| 检查项 | 结果 |
|---|---|
| 当前分支 | `phase1/batch6-design` ✓ |
| 前置 tag | `phase-1-batch-5` 为 HEAD 祖先（exit 0）✓ |
| 暂存区 | 空 ✓ |
| 工作树 | 仅设计文档 + 第一次 review（均未跟踪）✓ |
| 第一次 review 未修改 | ✓（存在且未覆盖）|
| 代码/测试/schema/migration/Gradle/Manifest 变化 | 无 ✓ |
| 生命周期模式全仓搜索 | 24 处命中：3 个 reminder receiver（goAsync+SupervisorJob+finally 现网模式）、Widget（L65-101 含静态 WIDGET_SCOPE L101）、WearDataLayer（runBlocking L121/144）、AppNavigation/SettingsViewModel（非 receiver）——设计 §6.6.1 引用全部精确 ✓ |

---

## Original review disposition

第一次 review 的 F1（P1）原问题：

- **缺少什么**: 原设计（§10.2）只声明 "make Android receivers thin provider/delegation shells"，未锁定 `goAsync()` 调用/所有权、CoroutineScope 所有权、`PendingResult.finish()` 在所有路径恰好一次、success/idempotent/conflict/NotFound/StorageFailure/异常/取消路径的完成语义、receiver 副作用与持久化结果先后顺序。
- **为什么是 P1**: 审阅标准 §十二"如 receiver 生命周期未设计清楚，列为 P1"；实现误用（goAsync 未调用/finish 遗漏）会导致 ANR/结果丢失，重复 delivery 可能被误判成功。
- **后果**: ANR、结果丢失、重复 delivery 误判、副作用的错误顺序。
- **最小修订要求**: 补充 receiver 生命周期规则（finish 于所有路径、协程 scope 归属、引用现有 goAsync+finally 基线）。

**本次修订准确针对该问题**（§6.6 L234-383），无倒推改写。

---

## goAsync ownership verdict

设计 §6.6.2（L250-261）明确：

1. `onReceive()` 同步只做必要字段校验 ✓
2. 同步调用 `goAsync()` **恰好一次**（在启动工作前）并本地持有 PendingResult ✓
3. 数据库/Repository/有界派生工作/异步副作用只在 goAsync 后、绝不在主线程 ✓
4. 不使用 `runBlocking`/Future/Task 等待/无界 Flow/长阻塞操作 ✓
5. 不使用 GlobalScope/Activity/ViewModel scope/静态 receiver scope/fire-and-forget ✓
6. **goAsync 成功后无分支可在任务拥有 PendingResult 与最外层 finally 前返回**（L259）——消除"goAsync 后同步 return 遗漏 finish" ✓
7. 同步拒绝路径分类明确（§6.6.5 表格：MedicationReminderReceiver 无效 ID、NotificationAction skip/unknown/invalid ID 同步返回；Reschedule 无同步路径；Widget 非记录动作同步返回）✓
8. 一个异步 delivery 一个 PendingResult；不在 Repository 完成前 finish ✓
9. applicationContext 传递、不捕获 receiver/Activity context/PendingResult 超出单任务 ✓

**无"实现者自行决定"模糊空间**。

---

## Coroutine-scope ownership verdict

设计 §6.6.3（L263-274）：

- 每次 delivery 一个独立 `SupervisorJob`（或等价独立 Job）+ `Dispatchers.IO` 或项目受控 IO dispatcher ✓
- 无父 Activity/ViewModel/静态 Widget job；无跨 delivery 共享（**明确废弃 Widget 静态 WIDGET_SCOPE**，§6.6.1 L246 + §10.2）✓
- scope 生命周期随单任务结束；子工作不得逃逸到 finish 之后 ✓
- "SupervisorJob + Dispatchers.IO" 表达为**设计要求**而非未经论证的固定实现（"or a project-owned bounded IO dispatcher"给 seam 空间）；§6.6.11 明确可注入 dispatcher/scope factory 测试 seam ✓
- 任务结束自然完成（无永不取消的泄漏 scope）✓
- 进程终止不保证 finally（§6.6.6 诚实声明）✓

**scope 所有权无泄漏风险**。

---

## PendingResult finish verdict

设计 §6.6.4（L276-304）：

- finish **只出现在最外层 finally**，恰好一处 ✓
- 在 handler 与允许的副作用尝试**之后**执行 ✓
- success/rejected/idempotent/conflict/NotFound/StorageFailure/意外异常/进程内取消**全部**执行 ✓
- 绝不在业务分支/命令 handler 内 ✓；绝不先于 Repository 完成 ✓；绝不被副作用失败跳过 ✓；绝不委托 fire-and-forget 子任务 ✓
- 伪代码（L290-302）准确表达 try/finally 结构，并标注 "equivalent to"（非固定实现授权）✓
- §6.6.9 三 receiver 矩阵逐项列出 finish 位置 ✓

**finish 恰好一次在所有路径成立**。

---

## Cancellation and exception verdict

设计 §6.6.7（L328-334）：

- 业务结果（Inserted/Idempotent/Conflict/Invalid/NotFound）为普通 typed 结果，非异常 ✓
- `CancellationException` **重抛**、不吞、不转 StorageFailure；最外层 finally 在进程内取消展开时仍 finish 恰好一次 ✓
- 取消不转业务成功、不触发 legacy fallback、不发送成功 notification/ack ✓
- 无无界重试；区分协程取消与 Repository 业务失败 ✓
- 未设计"catch 所有 Throwable → StorageFailure"（StorageFailure 仅接收器级明确映射）✓

---

## Business-result separation verdict

设计 §6.6.10（L369-371）：

- `finish()` **只表示 receiver 异步进程内生命周期结束**；不代表 Repository 成功/幂等确认/通知成功/Widget 刷新成功/Wear ack 成功 ✓
- 业务成功仅由 Repository result 与入口特定协议 outcome 决定 ✓
- §6.6.4 L304 "finish() does not mean the command succeeded" 双重强调 ✓

**finish 不可能被实施者当作成功 ack**。

---

## Receiver lifecycle matrix verdict

§6.6.9 三 receiver 矩阵（L361-367）逐项完整：

**MedicationReminderReceiver**：无效 ID 同步拒绝（零 goAsync）；有效 delivery 恰好一次 goAsync；读 plan（getById）+ 有界事件窗口（findOccurredBetween）→ check-in 判断；仅启用且无 check-in 时提醒；读失败不制造虚假提醒；通知/重排失败无写/无 fallback；取消重抛；finish 在最外层 finally ✓

**MedicationNotificationActionReceiver**：skip/unknown/invalid ID 同步拒绝；有效 confirm 恰好一次 goAsync；读 plan → build event → insert → 分类 replay/conflict（race 时一次 getById 重读）；成功副作用（取消通知/刷新 Widget）只在 Inserted/已识别幂等后；live-plan conflict/invalid/storage/exception 无记录成功路径；stale plan 清理非成功；副作用失败不回滚已提交行；finish 一次 ✓

**ReminderRescheduleReceiver**：无同步拒绝路径（每次 delivery 恰好一次 goAsync）；observeAll().first() → rescheduleDomainReminders；**保持 fail-fast**（引用 ReminderManager.kt:105-107）；失败不重写计划/不 fallback/不假成功；所有路径收敛最外层 finally ✓

**同步路径无遗漏 finish**（§6.6.5 明确"goAsync 后无同步 return"）✓

---

## Side-effect ordering verdict

§6.6.8（L336-359）：

- 写入型（NotificationAction/Widget）：insert → 分析真实结果 → 仅 Inserted/幂等后成功副作用 → conflict/storage/exception 零成功副作用 → finally finish ✓
- 读取/派生型（ReminderReceiver/Reschedule）：read → 成功才构建通知/调度 → 派生失败不回写数据库 → finally finish ✓
- 数据库成功 + 通知失败：**不回滚数据库、不 legacy 写重试**（§6.6.8 NotificationAction L345）✓
- 数据库失败：不显示成功 ✓
- finish 不先于必要副作用处理（§6.6.4 L282）✓
- 无第二事实来源 ✓

---

## Process-lifetime verdict

§6.6.6（L322-326）：

- goAsync 非持久队列；handler 为有限一次性 Repository 操作 + 有界副作用；无延迟循环/无界重试/网络等待/无界 Flow ✓
- 超窗 → 停止并返回设计（非隐藏长工作在 goAsync 后、非引入 WorkManager）✓ —— 与 Batch 6 禁止新增 WorkManager 范围一致
- 进程终止可阻止 finally（**进程内保证 vs 持久保证明确区分**）✓
- 未完成持久化前不报告成功；Wear DataItem 保留重试（属 Wear 协议，非普通 receiver 生命周期）✓

---

## Receiver test-matrix verdict

§6.6.11 seam（L373-383）：可替换 work delegate + 受控 dispatcher/scope factory + finish 回调 spy + 同步解析报告 starter 调用 + 协程完成原语（无 Thread.sleep/日志-only、不改 Android 框架）✓

§11.1（L536-556）14 项生命周期测试全部可执行：success/idempotent/conflict/NotFound/StorageFailure/exception/cancellation 各 finish 一次；早期拒绝零 goAsync；无重复无遗漏（含副作用失败）；onReceive 不阻塞；静态扫描（GlobalScope/runBlocking/共享 scope/逃逸子任务）；数据库失败无成功通知；数据库成功+副作用失败保留行+finish 一次；API 33/35 phone 实际 receiver/PendingResult 集成（JUnit XML 计数）；Wear AVD 不作为 phone 验收 ✓

**finish exactly-once 可测试**（seam 设计明确）→ 无 P1。

---

## Stop-condition verdict

§15 新增停止条件（L696-706）覆盖：suspend 路径省略 goAsync/阻塞 onReceive/runBlocking/GlobalScope/共享静态 scope/finally 外 finish/子工作逃逸（L696）；生命周期测试无法证明恰好一次（L697-698）；无所有权 scope（L699）；阻塞主线程（L700）；提前成功副作用（L701）；取消绕过 finally（L702）；超窗需持久框架（L703）；legacy fallback（L704）；测试不可确定性证明（L705）；需 Manifest/protocol/contract/Domain/schema/WorkManager 变化（L706）——**全部与 Batch 6 范围一致**（§2.1 排除项对齐）✓

---

## Non-regression of approved design verdict

修订前后对比（本次仅新增 §6.6 + §11.1 生命周期测试 + §14/§15/§16 状态更新）：

| 已通过设计部分 | 是否改变 |
|---|---|
| Batch 6 权威范围（§1-2）| 未变 ✓ |
| 绕过清单 21 项（§4）| 未变 ✓ |
| DoseEvent metadata 默认值（§5.4）| 未变 ✓ |
| insert/CAS/delete 规则（§5.3）| 未变 ✓ |
| JSON v1 留 Batch 7（§7）| 未变 ✓ |
| 私有 PK bridge 边界（§8）| 未变 ✓ |
| Widget metadata/idempotency（§6.4）| 未变 ✓ |
| Wear action_id 所有权与 ack（§6.5）| 未变 ✓ |
| 6A/6B/6C 边界（§10）| 未变（6B 仅补充生命周期要求）✓ |
| final bypass-zero（§12）| 未变 ✓ |
| API 33/35 phone + Wear OS 独立矩阵（§11.3-11.4）| 未变 ✓ |
| schema/migration/release 门槛（§11.5/§16）| 未变 ✓ |
| 原 5 个 P2（§14）| 保留 ✓ |

**无意外范围变化** → 无 P1。

---

## Remaining P2 findings

原 5 个 P2 保留（§14 L676-680）：Route/Ester 依赖、JSON/PK 临时桥（Batch 7 归属）、reminder/Widget 确定性 ID 非 UUIDv5（事件 action ID）、slotId=null（协议无 slot 身份）、ARCHITECTURE 文档漂移。全部真实、非阻断、有归属 ✓

---

## Schema and release-safety verdict

- 无 schema/migration/contract/Domain/DAO/Entity 变化（§2.1 + 本轮 git diff 空）✓
- Room version=3；schema 2/3 门（§11.5 L622-627）保留 ✓
- 持续禁止真实库/release（§16）✓

---

## Findings

### F1 (P1→RESOLVED) — receiver 异步生命周期

- **设计/审阅行号**: 第一次 review F1；修订 `PHASE_1_BATCH_6_DESIGN.md:234-383`（§6.6）+ `536-556`（§11.1 生命周期测试）+ `662`（§14 状态）+ `696-706`（§15 停止条件）
- **问题**: 原设计未锁定 receiver 生命周期（第一次 review P1）。
- **依据**: 修订 §6.6.1-6.6.11 完整锁定 goAsync 所有权、per-delivery scope、finish 恰好一次、异常/取消、三 receiver 矩阵、副作用顺序、进程终止、测试 seam。
- **当前状态**: **RESOLVED**（10 项清除条件全部满足；为设计层锁定，代码验证属 6B）
- **是否阻止提交**: 否

### F2-F6 (P2) — 原 5 个 P2 保留

- **是否阻止提交**: 否

**无新的 P0/P1。**

---

## Independent validation performed

本轮为设计重新审阅，**未运行任何实现测试**（无实施代码）。实际执行：

- Git 边界：分支/祖先/暂存/单文件变化/第一次 review 未覆盖 ✓
- 修订设计全文 read（719 行）：§6.6（L234-383）、§11.1（L536-556）、§14（L662）、§15（L696-706）、§16 ✓
- 全仓生命周期模式搜索（24 处）：3 个 reminder receiver 现网 goAsync+SupervisorJob+finally 模式行号与设计 §6.6.1 引用逐点一致（L35-37/66-67、L49-52/72-73、L19-21/27-28）；Widget 静态 WIDGET_SCOPE（L101）被设计识别为需废弃 ✓；WearDataLayer runBlocking（L121/144）属 6C 范围 ✓
- 第一次 review F1 原文复述与修订内容对照（无倒推改写）✓
- 非回归对比（已通过设计部分 vs 修订版）✓

---

## Final decision

### **APPROVE WITH P2**

**F1 是否关闭**: **是（RESOLVED）** —— 10 项清除条件全部满足：goAsync 所有权明确（§6.6.2）；scope 所有权明确（§6.6.3，含 Widget 静态 scope 废弃）；finish 所有路径恰好一次（§6.6.4+§6.6.9 矩阵）；exception/cancellation 明确（§6.6.7）；三 receiver 矩阵完整（§6.6.9）；副作用顺序明确（§6.6.8）；测试计划可执行（§6.6.11 seam + §11.1 14 项）；停止条件完整（§15）；未破坏原已通过设计（非回归对比）；无新 P0/P1（§14 0/0/5）。

**提交前是否还有必须修订事项**: 无。

**是否建议提交设计**: 是。提交建议信息：`docs: revise batch 6 design with receiver lifecycle contract`。

**是否建议保留第一次 review**: 是（不可变历史记录，§16 明确）。

**是否建议提交 rereview**: 是（随设计提交）。

**是否建议创建 `phase-1-batch-6-design-v1`**: 是（设计 + 两次审阅提交后）。

**是否建议随后进入 6A**: 是（设计封存后；6A 不依赖 receiver 生命周期细节，但设计整体必须封存后再开始实施）。

**是否继续禁止真实数据库和 release**: **是**。Room v3 仍处 ADR-016 内部不可发布区间；Batch 6 完成不授权 Batch 8/真实库演练/生产分发/release；Batch 7（JSON/PK 正式 adapter）仍必须完成。

---

*重新审阅结束。最终工作树：仅设计文档 + 第一次 review + 本 rereview；未修改任何现有文件。*
