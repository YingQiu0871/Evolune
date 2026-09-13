# Evolune v1.7 — 数据语义冻结（V17_DATA_SEMANTICS）

> 状态：`BASELINE FROZEN` + `PHASE-0 CORRECTION APPLIED`（2026-09-13）
> 基线：`main` @ `72a468c`；封版稳定版 `v1.6.0`
> **Product/code baseline**：`72a468c`；**Phase-0 freeze documentation commit**：`docs/evolune/v1.7/*` 的 docs-only commit（不是产品基线）
> 证据规则：本文件所有"行为事实"来自可执行源码、Room schema JSON 与测试源码，标注 `路径:行号`。
> 仓库文档（含本目录）**不作为行为证据**，仅用于定位。无法从源码/测试确定的一律标记 `SEMANTICS UNRESOLVED`，不自行补定义。
> **架构/产品门裁决（Decision A–F）已应用到相关小节**，原文见 [V17_SPEC.md 附录](V17_SPEC.md#phase-0-gate-decisions架构产品门裁决2026-09-13)；
> 裁决只约束 **v1.7 的呈现与派生规则**，不改变本文记录的既有实现事实。
> 配套：[规格](V17_SPEC.md) · [开发计划](V17_PLAN.md) · [基线审计](V17_BASELINE_AUDIT.md) · [验收](V17_ACCEPTANCE.md) · [基线证据](V17_BASELINE_EVIDENCE.md)

---

## 0. 结论摘要（先读这段）

现有仓库**已经存在**一个确定性的历史派生层 `MedicationOccurrencePresentation`
（`experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/MedicationTimeline.kt:54-266`），
它把 `计划 → occurrence` 与 `已记录事件 → occurrence` 的匹配实现为**四个有序阶段**：

| 阶段 | 事件形态 | 归属条件 | 代码 |
|---|---|---|---|
| 1 | `slotId != null && localDate != null` | slot 相同 **且** 持久化 `localDate` == occurrence 本地日期；不看时间距离 | `MedicationTimeline.kt:100-117` |
| 2 | `slotId != null && localDate == null` | slot 相同 且 时间差 ∈ `[-1h, +1h]`（闭区间） | `MedicationTimeline.kt:119-136`、`:212-233` |
| 3 | `slotId == null`（任意 localDate） | 时间差 ∈ `[-1h, +1h]` 且 route/ester/剂量匹配，且候选 occurrence **唯一** | `MedicationTimeline.kt:138-174` |
| 4 | `slotId == null && localDate != null` | 与 occurrence 同一本地日期 且 route/ester/剂量匹配，且候选唯一；**仅限第三阶段未消费、且从未有过窗口证据的事件** | `MedicationTimeline.kt:176-208`、`:235-247` |

匹配是**逐事件**判定（`candidates.size == 1` 才成立），并发的多个候选一律判为歧义而不归属；
同一 occurrence 只保留一个事件（并列时取 `occurredAt` 最早者，`MedicationTimeline.kt:164-174`）。
`MedicationOccurrencePolicy` 的默认窗口为前后各 1 小时（`MedicationTimeline.kt:38-52`）。

事件侧的身份由 `dose_events` 的两列承载：`slotId`（可空）与 `localDate`（可空，记录时写死的墙上日期）。

---

## 1. 今日 `00:00` occurrence + 昨日 `23:00` legacy null-slot event 是否匹配？

**可以匹配**，路径是第三阶段的时间窗，而不是"同日"规则。

- legacy 迁移事件的三列全为 NULL：`slotId = null`、`zoneId = null`、`localDate = null`
  （迁移断言 `data/migration/AppDatabaseMigrations.kt:366-369`；legacy 映射 `data/mapper/DoseEventEntityMapper.kt:42-43`；legacy 源标记 `:48`）。
- 这类事件只能进第三阶段：过滤条件仅 `slotId == null && 未被消费`
  （`MedicationTimeline.kt:141-149`），归属条件为
  `fallbackMatchCandidate`：`difference ∈ [-matchBefore, +matchAfter]`，默认各 1 小时，
  **闭区间**（`MedicationTimeline.kt:217-220`；边界语义 `difference > policy.matchAfter` 才拒绝）。
- 昨日 `23:00` 事件与今日 `00:00` occurrence 的时间差恰为 `+1h`，未超过 `matchAfter` → 落入窗口。
- 三个必要条件同时成立才会真的归属：(a) route/ester/剂量（容差 `1e-6`）匹配；(b) 该事件在第三阶段中候选 occurrence **唯一**；(c) 该事件未被前两阶段消费。
- 若同一计划昨日也有 `23:00` occurrence 且未被消费，则该事件有 2 个候选 → **不归属**（歧义保持，`MedicationTimeline.kt:158-162`）。
- 携带 `localDate = 昨日` 的手动/JSON 事件走同一第三阶段；第四阶段的"同日回退"要求 `localDate == occurrence 本地日期`，对这种跨日情形不适用（`MedicationTimeline.kt:240-241`）。

既有测试：`MedicationOccurrencePresentationTest#null-slot time-window match takes precedence over same-day fallback`
（`experience-core/src/test/kotlin/io/github/yingqiu0871/evolune/experience/MedicationOccurrencePresentationTest.kt:184`）、
`#same-day null-slot fallback rejects an event from another local date`（`:477`）。

> ⚠️ 覆盖状态（更正）：上述用例覆盖的是**同日/同窗口内的阶段优先与拒绝规则**，
> **没有**覆盖"昨日 23:00 legacy 事件 × 今日 00:00 occurrence"这一**精确跨日边界**用例。
> 本节结论属 **code-derived behavior, currently without exact boundary regression test**；
> 补该边界测试已列入 v1.7-A mandatory backlog（见 [验收 §9](V17_ACCEPTANCE.md)）。

> **Decision F 适用**：v1.7 将上述命中定义为 **Legacy inferred match**，而不是 **Exact historical match**。
> v1.7-A historical projection 必须携带 match provenance，至少区分
> `exact identity/date match` / `bounded inferred match` / `legacy/null-slot inferred match` / `unmatched actual event`；
> 跨 local-date 的 inferred match 可以保持 v1.6 兼容行为，但**不得在 History UI 中伪装成 exact**，
> 也**不得作为严格 adherence 指标的高置信输入**。本轮与 PRE-A 阶段都**不修改**现有四阶段 matcher。

> v1.7 含义：History 中"昨日 23:00 的记录"有可能被呈现为"今天 00:00 计划已完成"。这是**既存语义**，不得静默改写（规格 §2.2）。

---

## 2. 同药、同剂量、一天三次时如何确定 occurrence？

- occurrence 身份 = `(planId, slotId, scheduledLocalDate)` 的确定性 UUID
  （`experience-core/src/main/kotlin/io/github/yingqiu0871/evolune/experience/MedicationOccurrence.kt:93-110`），
  三个 slot 天然是三个不同 occurrence（`app/src/test/.../MedicationOccurrenceGeneratorTest.kt:130`）。
- **事件带 `(slotId, localDate)`**（提醒确认 `reminder/ReminderDoseFactory.kt:39,35`、Widget `widget/WidgetWork.kt:438,434`、Wear App `application/WearAppConfirmationHandler.kt:312,308`）
  → 第一阶段精确归属，与时间无关，不存在"就近误判"。
- **事件 `slotId = null`**（Phone 手动 `application/DoseEventEditor.kt:50,79`、legacy Wear Tile `application/WearDoseActionHandler.kt:140`、legacy 迁移行）
  → 只有当时间窗/同日回退候选唯一时才归属；同药同剂量同日多次且时间接近时**判为歧义、不归属**。
  测试：`MedicationOccurrencePresentationTest.kt:132`（同日同刻两条 occurrence + 一条 null-slot 事件 → 0 条 RECORDED）、`:504`、`:157`、`:374`。
- 无 `(planId, slotId, localDate)` 数据库唯一约束（`data/DoseEventEntity.kt:16-36` 只有 `@PrimaryKey id`），
  去重完全依赖派生层的匹配与写入侧的确定性事件 id。

---

## 3. 计划 `23:30`，实际次日 `00:20`：occurrence date / history date / event instant 各是什么？

| 概念 | 取值 | 证据 |
|---|---|---|
| occurrence 的计划日期 | **`D`（计划当天）**，不因跨午夜位移 | `experience-core/.../MedicationOccurrenceGenerator.kt:66-68`（`LocalDateTime.of(date, slot.localTime)`，按日历日逐日展开） |
| occurrence 的绝对计划时刻 | `D 23:30` 在本机 zone 下的 Instant | 同上 `:72` |
| event instant（`occurredAt`） | **实际记录瞬间**（次日 `00:20` 的绝对时刻） | `core/model/DoseEvent.kt:13`；写入点见下 |
| event 持久化的 `localDate` | **取决于入口，不统一** | 见下表 |
| History 展示日期 | **当前无实现**（v1.7 待定，规格 §9） | `ui/screens/MedicationRecordsScreen.kt:233-255` 仅按 `occurredAt` 倒序平铺、无日期分组 |

各入口写入的 `localDate`：

| 入口 | `localDate` | `slotId` | 结果 |
|---|---|---|---|
| 提醒确认（计划 23:30 的通知在次日点确认） | **计划日 `D`**（`ReminderDoseFactory.kt:35` 取 occurrence 的 `scheduledLocalDateTime`） | occurrence 的 slot | 第一阶段精确归属到 23:30 occurrence |
| Widget（次日 `00:20` 点击昨日 23:30 行） | 记录日 `D+1`（`widget/WidgetWork.kt:434`） | 来自 Intent 的 slot | **动作被拒**：`WidgetWork.kt:244` 要求 Intent 携带的 `scheduledLocalDate` == 点击时的本地日期，跨午夜直接 `Invalid`（不写库） |
| Wear App 确认 | 命令携带的 `command.localDate`（= Phone 快照里该 occurrence 的日期 `D`，`WearAppConfirmationHandler.kt:308`） | 命令 slot（`:312`） | 第一阶段精确归属（前提：手机 zone 未变、occurrence 可重建，`:254-277`） |
| Phone 手动补录（次日 `00:20` 记 23:30 那一针） | 记录日 `D+1`（`DoseEventEditor.kt:75`） | `null`（`:79`） | 第三阶段时间窗 50 分钟 → 唯一候选时归属到 23:30 occurrence |
| legacy Wear Tile | 记录日（`WearDoseActionHandler.kt:142`） | `null`（`:140`） | 同 Phone 手动（第三阶段） |

**结论**：同一个"次日 00:20 补记 23:30"的用户意图，经不同入口会产生不同的 `(localDate, slotId)` 组合，
其中 Widget 入口**直接被拒绝**。三种落库形态在派生层都能（或都不能）归属到同一 occurrence，
但落库数据本身不等价——这是 v1.7 History 必须显式处理的既存事实。

---

## 4. intake 完成后修改 schedule，过去 history 是否改变？

**计划侧会被追溯改写；实际侧不变。**

- 计划与 slot 是**当前状态**，没有版本化：`RoomMedicationPlanRepository.save` 在同一事务内
  `deleteSlotsForPlan` 后整批重插（`data/repository/RoomMedicationPlanRepository.kt:68-83`），
  并按时间重排 position（`:180-186`）。
- 但 UI 编辑路径**保留既有 slot UUID**（`ui/components/MedicationPlanBottomSheet.kt:322` →
  `application/MedicationPlanEditor.kt:136-161` → `application/MedicationPlanDraftMapper.kt:72-81`）。
- 因此"改时间"后：occurrence 身份 `(planId, slotId, localDate)` **不变**，
  但 `scheduledAt` / `scheduledLocalDateTime` 使用**新时间**（`MedicationOccurrenceGenerator.kt:68-92`）
  → 过去日期的计划时间与"计划 vs 实际"差值被追溯改变。
- 若旧 slot UUID 被替换（新增时间条目；或调用方未提供 slotIds 且按 `localTime` 匹配失败导致重新生成 id，
  `MedicationPlanEditor.kt:148-159`、`MedicationPlanDraftMapper.kt:83-96`），
  已记录事件携带的旧 `slotId` 将不再匹配任何 occurrence：
  事件不丢失，但落到"未归属"形态，第一阶段失效、只可能靠第三阶段窗匹配（其 `slotId != null` 时又进不了第三阶段）
  → 实际上会变成**孤儿事件**（详见 §5 的结构性推断）。
- 事件行本身不可被计划编辑改写（`dose_events` 与 plan/slot 无外键，见 §5）。

> **Decision A 适用（已裁决，原 `SEMANTICS UNRESOLVED` #1 关闭）**：
> v1.7 不修改 Room schema、不建立 plan/slot version history。
> **actual intake 是历史权威事实**；对过去日期由当前 plan 重新生成的 scheduled time 只能表示
> **`Current schedule context`**（或等价明确文案），**不得**称为 `historical planned time`，
> 也**不得**把 `actual - reconstructed current scheduled time` 展示成确定的历史服药延迟。
> 只有当数据结构真正具有 immutable historical planned snapshot 时才可使用历史计划语义；现有数据不得假装拥有它。
> 该裁决同时约束 v1.7-B：不得基于不可证明的 plan reconstruction 输出"严格历史 adherence rate"。
> 测试缺口仍存在：无"已有历史事件的计划被编辑"用例（覆盖率审计缺口 #2），已列入 v1.7-A mandatory backlog。

---

## 5. intake 完成后删除 plan，历史事实是否仍存在？

**结构性推断：事件行保留，但其 occurrence 归属消失。**

- `dose_events` 无任何外键（`data/DoseEventEntity.kt:16-36` 只有 `@PrimaryKey id` 与普通列；
  schema `app/schemas/io.github.yingqiu0871.evolune.data.AppDatabase/3.json` 中 `dose_events` 无 foreignKeys）。
- 只有 `scheduled_dose_slots` 对 plan 有 `ForeignKey.CASCADE`（`data/ScheduledDoseSlotEntity.kt:9-37`）。
- `RoomMedicationPlanRepository.delete` 只删除 plan 并校验 slot 级联为 0（`RoomMedicationPlanRepository.kt:125-138`），
  **不触碰 `dose_events`**；`deleteAll()` 同理（`:140-155`）。
- occurrence 生成只展开传入的 schedule（`MedicationOccurrenceGenerator.kt:39-50` 且 `filter { it.enabled }`），
  计划删除或被禁用后该计划不再产出 occurrence → 事件不再有可归属对象。
- 关键限制：`dose_events` **没有 `planId` 列**（`data/DoseEventEntity.kt:16-36`），
  slot 是唯一的计划间接引用；slot 被级联删除后，**事件本身已不再携带任何"属于哪个计划"的信息**，
  因此孤儿事件的计划归属在现有模型下不可恢复。
- 因此：**历史事实（事件行）仍在库中，但 History 若无孤儿事件呈现策略，就会"看起来消失"**。

> 该结论目前是**源码结构性推断，零测试覆盖**（覆盖率审计将"删除计划后 dose_events 存活"列为 NONE）。
> v1.7-A 必须补一条确定性测试把该行为锁定，否则不满足规格 §8"Past medication facts must not silently disappear"。

> **Decision B 适用**：unmatched actual event **必须展示**。
> 只有 `source == MANUAL` 的事件可以称为 **Manual intake**；
> 其他 source（`LEGACY` / `JSON_V1` / `REMINDER` / `WIDGET` / `WEAR`）必须**按真实 source 呈现**，
> 不得把孤儿事件一律标成 Manual。孤儿事件在计划归属上不可恢复（无 `planId`），但事实本身必须可见。

---

## 6. 昨天手动补录、无正常 slot 时如何表示？

- 表示形式：`slotId = null` + `localDate = 补录时刻在该时区的本地日期` + `source = MANUAL`
  （新建会话 `application/DoseEventEditor.kt:37-55`；快速补录 `:65-84`）。
- 用户可把 `occurredAt` 改成过去时刻（编辑器无上下限、日期选择器未设 `selectableDates`，
  `ui/components/MedicationRecordBottomSheet.kt:398-434`；校验只有毫秒精度与剂量有限，`DoseEventEditor.kt:123-142`）。
  改时间时 `localDate` 会按编辑时区重算（`:144-150`），未改时间则沿用原值。
- 派生层结果有三种：第三阶段窗匹配到唯一 occurrence、第四阶段同日回退匹配、或**保持未归属**。
- 未归属事件在现有 UI 中仍是列表里的普通记录（`MedicationRecordsScreen.kt:233-255`），
  但没有任何"这不属于任何计划"的显式标记。
- 既有测试：`DoseEventEditorTest.kt:23,49,158`、`HRTViewModelTest.kt:442`、
  `WidgetPresentationTest.kt:106`（迟到的手动 null-slot 事件完成唯一同日 slot）。

> v1.7-A 需要一个明确的"未匹配/手动"呈现分类，否则无法满足规格 §3 的 Manual intake 行。
> 分类必须遵循 **Decision B**：`source == MANUAL` → Manual intake；其他 source 按真实 source 呈现（不得统一标 Manual）。
> 同时遵循 **Decision F**：未匹配 ≠ 未归属；被推断匹配的事件必须带上 inferred provenance，不能与 exact 混淆。

---

## 7. Wear completion 和 Phone completion 最终数据是否完全等价？

**不等价。** 两者只在仓储层 `DoseEventRepository.insert` 汇合，落库字段与上层编排均不同。

| 维度 | Wear App confirm | legacy Wear Tile | Phone 手动记录 |
|---|---|---|---|
| `id` | `nameUUIDFromBytes("wear-app-confirm-occurrence:v1:$operationId")`（`WearAppConfirmationHandler.kt:379-381`） | 手表生成的 actionId（`WearDoseActionHandler.kt:136`） | 随机 UUID（`DoseEventEditor.kt:32`） |
| `occurredAt` | **手机时钟**（`WearAppConfirmationHandler.kt:151`） | **手表时钟**（`DoseTileService.kt:227` → `WearDoseActionHandler.kt:53`） | 手机时钟，秒级截断到分钟（`DoseEventEditor.kt:67-69`） |
| `zoneId` | 手机 zone（`:307`） | 手机 zone（`WearDoseActionHandler.kt:142`） | 编辑时区（`DoseEventEditor.kt:75`） |
| `localDate` | 命令携带（计划日，`:308`） | 记录日（`:142`） | 记录日（`:75`） |
| `slotId` | 命令 slot（`:312`） | `null`（`:140`） | `null`（`:79`） |
| `source` | `WEAR`（`:313`） | `WEAR` | `MANUAL`（`:80`） |
| 上层编排 | `WearActionRecorder` → `RecordDoseEventEngine`（`FirstAcceptedBySourceAndOccurredAt`、`requireEnabledPlan=true`，`WearActionRecorder.kt:17-32`）+ 双 mutex（`WearAppConfirmationHandler.kt:56-60`）+ 操作日志幂等 | 同 engine（`WearDoseActionHandler.kt:56`） | **直接 `repository.insert`**，无 engine、无 engine 级校验、无互斥（`HRTViewModel.kt:403-404`） |

派生层匹配结果可能相同（都归到同一 occurrence），但**数据行不等价**；
且 Wear App 与 legacy Tile 的 `occurredAt` 时间源不同（手机时钟 vs 手表时钟），
两者在时钟偏差下会写出不同的实际发生时间。

既有测试：`CrossEntryOccurrenceConcurrencyTest`（Wear↔Widget↔Notification 交叉并发最终 1 行）、
`NotificationOccurrenceConcurrencyTest.kt:52,71,92,395`、`WearAppConfirmationHandlerTest.kt:129`。

---

## 8. Widget completion 是否走相同 authoritative mutation path？

**是同一持久化通道，但不是同一个应用层入口。**

- 调用链：`EvoluneWidgetReceiver.kt:731-735`（行内 RECORD 按钮 fill-in Intent，extra key：
  `plan_id`/`slot_id`/`scheduled_local_date`/`occurrence_id`/`widget_id`，`:35-39`）
  → `:801-814`（`Intent(context, EvoluneWidgetReceiver::class.java)`，action `…widget.RECORD_OCCURRENCE`）
  → `:89-125` 分发 → `:157-177` 生产 work
  → `widget/WidgetWork.kt:222-236`（`OccurrenceConfirmationCoordinator.withLock`）
  → `:291` `LocalActionRecorder.recordWidget`
  → `application/LocalActionRecorder.kt:36-52`（`eventId = nameUUIDFromBytes("widget-occurrence-action:v1:$occurrenceId")`，`:61-64`）
  → `RecordDoseEventAction.kt:52/129/152` → `RoomDoseEventRepository.kt:71-98`
  → `DoseEventDao.kt:49-50`（`@Insert(onConflict = IGNORE)`）→ `dose_events`。
- 与 Phone 手动记录**只在** `DoseEventRepository.insert` 汇合；
  Phone 手动路径不引用 engine 与 `OccurrenceConfirmationCoordinator`（全仓库 `OccurrenceConfirmationCoordinator` 使用者为
  `WearAppConfirmationHandler.kt:57`、`ReminderReceiverWork.kt:126`、`WidgetWork.kt:226`）。
- Widget 无自有数据库/队列，唯一本地持久化是外观 SharedPreferences（`widget/WidgetAppearance.kt:63-104`）。
- 幂等：确定性 eventId + `getById` 预检（`RecordDoseEventAction.kt:143-146`）+ 呈现重放预检
  （`WidgetWork.kt:266-288`）+ DAO `IGNORE` + 整事件相等比较（`RoomDoseEventRepository.kt:81-95`）。
- 顺序：**先写库 → 读回校验 → 刷新 Widget → Toast**（`WidgetWork.kt:291-303, 305-331, 383-384`）；副作用失败不回滚。
- 失败：接收端丢弃 `WidgetQuickActionOutcome`（`EvoluneWidgetReceiver.kt:114-121`），失败无提示、无重试队列；
  异常被 `ReceiverWorkLauncher.kt:12-16` 默认空回调吞掉。

---

## 9. undo 的真实机制是什么？

**物理删除行；没有 tombstone、没有状态位、没有反向事件。**

- `DoseEventStatus` 只有一个值 `RECORDED`（`core/model/DoseEventStatus.kt:3-5`）；
  持久化映射对未知状态直接判 corrupt（`data/mapper/DoseEventEntityMapper.kt:107-110`）。
- Phone：`HRTViewModel.deleteEvent` → `repository.delete(id)` → `DELETE FROM dose_events WHERE id = :id`
  （`viewmodel/HRTViewModel.kt:266-279` → `RoomDoseEventRepository.kt:150-157` → `DoseEventDao.kt:87-88`），
  **无 revision / 无 latest 校验**。
- Wear App：`WearAppUndoHandler.kt:126` → `deleteLatestRecordedIfRevisionMatches`
  → 事务内校验"存在 + revision 匹配 + 仍是最新一条 RECORDED"再删（`RoomDoseEventRepository.kt:175-211`，
  `DoseEventDao.kt:90-91`），失败返回 `REJECTED_NOT_LATEST` / `REJECTED_EVENT_CHANGED`。
- Widget：**没有 undo 功能**（`widget/` 目录内无 undo 相关代码）。
- 撤销是幂等日志驱动的（SharedPreferences `wear_app_confirmation_operations`），日志本身不是用药事实。
- 因此：撤销后行从历史与 PK 中**完全消失**，系统不保留"曾经记录、随后撤销"的事实；
  规格 §2.1 中"whether an intake event was undone or replaced"在当前模型里**无法表达**。
- 相关但不同的机制：提醒"跳过"（skip）只写 `ReminderSkipStore`（SharedPreferences，
  键 `"$planId|$slotId|$scheduledAtMillis"`，48 小时过期，`reminder/ReminderSkipStore.kt:13-55`），
  仅用于抑制排闹钟与投递（`ReminderManager.kt:52`、`MedicationReminderReceiver.kt:54`），**不写 Room、不进 PK/History**。

> **Decision C 适用**：`ReminderSkipStore` 是短期、非 authoritative 的 skip state，**不能**成为长期 History 的 medication fact。
> 因此长期历史只能表达事实状态：**`Recorded`** 或 **`No recorded intake`**；
> **不得**从 Room 历史虚构 `Skipped` / `Missed`。

---

## 10. Europe/Paris → Asia/Shanghai 后，过去 event instant / 原 local clock / 当前 history date 如何解释？

| 概念 | 现有实现 | 证据 |
|---|---|---|
| event instant | **绝对 epoch millis，跨时区不变** | `data/DoseEventEntity.kt:25-26`；读回 `data/mapper/DoseEventEntityMapper.kt:117` |
| 原 local clock | **记录当时的解释被持久化**：`zoneId`（规则 ID）+ `localDate`（墙上日期） | `DoseEventEntity.kt:27-28`；写入点 `DoseEventEditor.kt:46,75`、`ReminderDoseFactory.kt:34-35`、`WidgetWork.kt:433-434`、`WearAppConfirmationHandler.kt:307-308`、`WearDoseActionHandler.kt:141-142` |
| 当前 history date | **无既定实现**：现有列表按 `occurredAt` 倒序、用设备**当前**默认时区格式化，无日期分组 | `ui/screens/MedicationRecordsScreen.kt:233-255`；`ui/components/MedicationRecordItem.kt:138-154,180` |
| 匹配用的日期 | 使用**持久化的 `localDate`**（不随时区变化重算） | `MedicationTimeline.kt:103-117`、`:240-241` |

跨时区后的实际后果（源码事实）：

1. **显示日期与匹配日期可能分叉**：显示按当前时区重算，匹配用记录时写死的 `localDate`。
2. 第一阶段（精确 slot+date）在跨时区后可能失效 → 落到第二阶段窗口；若时间差超过 1 小时则不归属。
   既有测试只覆盖"同 localTime 在两个 zone 下 occurrence id 相同"
   （`MedicationOccurrenceGeneratorTest.kt:156`、`WidgetPresentationTest.kt:254`），
   **没有**"时区 A 记录 → 时区 B 读取/确认/撤销"用例。
3. 提醒/Widget/Wear 的精确重建用 `localDate.atStartOfDay(zone)` + `singleOrNull { it.scheduledAt == … }`
   （`ReminderReceiverWork.kt:283-296`、`WearAppConfirmationHandler.kt:254-277`、`WidgetWork.kt:401-416`），
   时区变化后可能重建失败 → 返回 `Invalid` / `REJECTED_OCCURRENCE_NOT_FOUND`（功能被拒，而非错记）。
4. 微件的同日校验 `WidgetWork.kt:244` 用**当前** zone 重算日期 → 跨时区/跨午夜点击被判 `Invalid`。
5. 提醒的"跳过"键含绝对 `scheduledAtMillis`（`ReminderSkipStore.kt:40-41`），
   时区改变后新算出的时刻与旧键不等 → 旧跳过记录失效。

> **Decision G 适用（已裁决，原 `SEMANTICS UNRESOLVED` #3 关闭）**：历史展示日期按**三类归因**处理，且必须携带 provenance：
>
> | 情形 | 展示日期（History occurrence/display date） | provenance |
> |---|---|---|
> | Bound / attributable（event 能可靠绑定 occurrence） | occurrence 的 **intended local date** | `INTENDED_LOCAL_DATE` |
> | Event 自身已持久化 `localDate` / `zoneId` | **保留 persisted semantics**（记录当时的墙上日期与规则 ID） | `PERSISTED_RECORDING_DATE` |
> | **True legacy orphan**（无 `localDate`、无 `zoneId`、无可靠 binding） | 只能由 `event instant` + **当前展示时区**推导 | `CURRENT_DISPLAY_TIMEZONE_DERIVED`（低置信） |
>
> 对 true legacy orphan：**不得**声称该日期是原始当地日期；这类数据**不得**作为高置信 historical adherence 输入。
> 任何情形下都**不修改** `occurredAt`（绝对 instant 保持不变）。

---

## 11. DST forward 如何处理不存在的 local time？

**无显式处理，完全依赖 `LocalDateTime.atZone` 的 java.time 默认规则：gap 本地时间前移。**

- 唯一决定性表达式：`experience-core/.../MedicationOccurrenceGenerator.kt:72`
  `val scheduledAt = requestedLocal.atZone(zoneId).toInstant()`，
  其上方 `:69-71` 的注释即全部说明（"java.time resolves gaps forward and chooses the earlier offset for overlaps"）。
- 全仓库无 `ZoneRules` / `ZoneOffsetTransition` / `isValidOffset` 等显式 DST 判定（穷尽 grep 结论）。
- 同口径表达式还有：`reminder/ReminderManager.kt:51,133`、`utils/MedicationPlanPredictor.kt:121-123`、
  `widget/WidgetWork.kt:350-351,403-405`、`application/WearAppConfirmationHandler.kt:261-263`、
  `wear/WearSkipNotificationPolicy.kt:32`。
- 既有测试断言该行为：`MedicationOccurrenceGeneratorTest#DST spring gap resolves forward using java time semantics`（`:178`）、
  `LegacyTimeAdapterTest#dstGapUsesJavaAtZoneForwardAdjustment`（`:117`）、
  `androidTest/.../time/V15TimeBoundaryDeviceTest.kt:61-68`（Paris 2025-03-30 02:30 → `2025-03-30T01:30:00Z`）。
- **已知内部不一致**：gap 日生成的 occurrence 中，`scheduledLocalDateTime` 仍保留**请求值** `02:30`
  而 `scheduledAt` 已对应 `03:30` 本地（`MedicationOccurrenceGenerator.kt:86,91`），代码中没有任何回验两者一致性的检查。
  由于精确重建用 `singleOrNull { it.scheduledAt == scheduledAt }`（`ReminderReceiverWork.kt:271` 等），
  对该日通知/微件命令的重放依赖 `scheduledAt` 一侧，行为可用；但 `scheduledLocalDateTime` 的语义（"请求的墙上时间"还是"实际计划墙上时间"）无定义 → 见 `SEMANTICS UNRESOLVED` #2。

> **Decision D 适用**：`schedule wall-clock time`（= 请求的墙上时间）表示 **user intent**；
> `scheduledAt` 是经当前 timezone/DST policy 解析后的 **valid instant**。
> matching / PK / 数值 timing calculation 一律使用 **valid instant**；两者必须在语义上保持区分。
> 因此 DST gap 日的 `scheduledLocalDateTime`（请求值 `02:30`）与 `scheduledAt`（对应 `03:30` 本地）**不是矛盾**，
> 而是"user intent"与"valid instant"的两种表示；呈现时不得把请求的墙上时间当作已发生的计划时刻。
> `SEMANTICS UNRESOLVED` #2 由此关闭（语义已裁决，仍需在 v1.7-A 的呈现层落实并测试）。

---

## 12. DST backward 如何区分重复的 local clock time？

**不区分；同一本地时间只存在一个 occurrence，取较早偏移，较晚偏移的绝对时刻永不物化。**

- occurrence 身份不含 offset：canonical 名为 `medication-occurrence:v1:plan=<uuid>;slot=<uuid>;localDate=<yyyy-MM-dd>`
  （`MedicationOccurrence.kt:99-109`）。
- 生成器按"日期 × slot"各调用一次 `atZone`（`MedicationOccurrenceGenerator.kt:65-99`），
  overlap 时 java.time 取**较早** offset（`:69-71` 注释）→ 只产出 1 条 occurrence。
- 既有测试：`MedicationOccurrenceGeneratorTest#DST fall overlap creates one occurrence at the earlier offset`（`:207`）、
  `LegacyTimeAdapterTest#dstOverlapUsesJavaAtZoneEarlierOffset`（`:128`）、
  `V15TimeBoundaryDeviceTest.kt:70-78`（`assertEquals(1, fallOverlap.size)`，Instant = 较早偏移）。
- 后果：
  1. 该日**不存在**对应"第二个 02:30"的 occurrence；提醒只覆盖较早偏移那一刻。
  2. 对较晚偏移时刻的精确重放（按 `scheduledAt` 严格相等）会失败 → 返回 `Invalid`。
  3. 匹配侧：第一阶段（slot+localDate）**不看时间距离**，因此在较晚 02:30 记录的事件仍会绑定到这个唯一 occurrence；
     若走第三阶段窗口，`+1h` 差值恰好落在闭区间边界内，同样可命中。
  4. 不会因 DST 产生重复 occurrence；但会产生"该日第二个 02:30 无计划"的**空缺**。

> **Decision E 适用**：v1.7-A **保持**现有 earlier-offset occurrence materialization，本阶段不重写 DST overlap generation。
> 同时必须**公开记录该限制**：第二个重复 local-clock instant 当前**不会**单独 materialize，
> 因此某些真实的"第二个 02:30" intake 可能表现出 **+1h 的 instant difference**（相对已物化的那个 occurrence）。
> **不得隐藏这一限制**（不得在 History 中把它呈现为无异常的准时记录）。

---

## 13. `SEMANTICS UNRESOLVED` 清单（不得由实现方自行补定义）

| # | 未决语义 | 为什么无法从仓库确定 | 影响 |
|---|---|---|---|
| 1 | 计划编辑后，**过去日期的"计划时间"**是否应保留当时值（当前会被追溯改写，§4） | 计划无版本化，仓库无任何历史计划快照机制或注释说明 | v1.7-A 历史"计划 vs 实际"差值语义 |
| 2 | DST gap 日 occurrence 的 `scheduledLocalDateTime`（请求值 `02:30`）与 `scheduledAt`（`03:30`）不一致是否为有意设计 | 代码无注释、无校验、无测试断言该不一致本身 | 提醒/微件重放与 History 展示的"计划时间"取值 |
| 3 | legacy（迁移前）事件 `zoneId/localDate` 为 NULL 时，用户改时区后的"正确归日" | 无历史时区信息，纯信息缺失 | History 对 legacy 行的日期归属 |
| 4 | 计划删除或 slot 身份被替换后，孤儿事件（§5）应如何在 History 中呈现 | 现有实现没有该概念，测试零覆盖 | 规格 §8 "Deleted schedule after intake" 验收项 |
| 5 | "未记录"与"已跳过（skip）"在 History 中是否应区分 | skip 只存在于 48 小时过期的 SharedPreferences，不是权威事实、不进备份 | Insights 的 `Uncompleted` 指标定义（规格 §3 禁止自创 `missed` 语义） |
| 6 | 跨午夜 Widget 点击被拒（`WidgetWork.kt:244`）后用户应看到什么 | 只有 `Invalid` 代码路径，无产品定义、无测试 | 规格 §8 "Cross-midnight occurrence" 验收 |

**状态更新**：Phase-0 Correction（2026-09-13）关闭 #1（Decision A）、#2（Decision D）、#4（Decision B）、#5（Decision C）；
v1.7-A gate（2026-09-13）关闭 **#3（Decision G）** 与 **#6（Decision H）**。

**剩余未决：无。** 全部六项均已有裁决，转入"需在 v1.7 落实 + 测试"状态。
特别说明 #3：信息在数据中**不存在**（true legacy orphan 无 zoneId/localDate），因此 Decision G 的解法是
**约定 + provenance**（`CURRENT_DISPLAY_TIMEZONE_DERIVED`），而非"恢复原始日期"；实现不得把它当作原始当地日期使用。

原六项按规格 §2.2 与开发计划 §12 属于**停止条件**范畴：在 v1.7 相关阶段实现前需由架构/产品门给出决策，
不得由开发代理自行补定义。

---

## 14. 证据边界说明

- 本文件不把 `docs/` 下任何叙述（包括 v1.6 验收与评审文档）当作运行时行为证据。
- 测试源码仅用于证明"该行为被断言过"；生产行为证据一律来自 `*/src/main` 与 Room schema。
- 所有文件行号对应基线 `72a468c`；若后续提交移动代码，需按符号名重新定位。
