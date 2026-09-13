# B-00-R1 source x provenance reachability (snapshot of the writer-level audit)
# Extraction rule: start at "## 16. Source", end at "## 17. Initial metric set"; verbatim.
# Raw writer audit output: source-provenance-reachability.txt (reproducible commands).

## 16. Source × match provenance（**writer 级可达性审计**，B-00-R1 重建）

`MedicationIntakeSource`（事件来源，权威）与 `MedicationMatchProvenance`（绑定方式）是**两个独立维度**。
下表**不是**按直觉填写，而是逐一审计真实 production writers 后得出
（证据：`evidence/b-00-r1/source-provenance-reachability.txt`，含每个 writer 的代码位置与字段赋值）。

### 16.1 Production writer 审计（写入 `dose_events` 的全部路径）

| Writer（入口） | source | persisted `slotId` | persisted `localDate` | persisted `zoneId` |
|---|---|---|---|---|
| `DoseEventEditSessionFactory.createNew`（手动新建） | `MANUAL` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `DoseEventEditSessionFactory.createQuickEvent`（快速记录） | `MANUAL` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `ReminderDoseFactory.createReminderDoseEvent`（提醒确认） | `REMINDER` | **occurrence.slotId（非空）** | **计划日** | 有 |
| `WearAppConfirmationHandler.createConfirmationEvent`（Wear App 确认） | `WEAR` | **command.slotId（非空，协议校验 `isNonZero`）** | `command.localDate`（非空） | 有 |
| `WearDoseActionHandler.createWearDoseEvent`（**legacy Tile** 路径） | `WEAR` | **null** | `occurredAt` 在该 zone 的日期 | 有 |
| `createWidgetDoseEvent`（Widget 快捷动作） | `WIDGET` | **slotId（非空参数）** | `occurredAt` 在该 zone 的日期 | 有 |
| `MahiroV1DomainAdapter`（JSON v1 导入） | `JSON_V1` | **null** | **null** | **null** |
| `DoseEventEntityMapper`（legacy 迁移行） | `LEGACY` | **null** | **null** | **null** |
| `DoseEventEditor` 的 **UPDATE** 路径 | 保持原 source | **保持原 `slotId`**（`original.copy`） | `occurredAtEdited` → 按**编辑 zone** 重算；否则保持原值 | 同上 |

> **对 review 假设的两处更正（以代码为准）**：
> (1) **WEAR 有两条 writer**：App 确认携带**非空** `slotId` + `localDate`（→ exact 可达）；
> legacy Tile 路径 `slotId = null` + `localDate != null`（→ null-slot 阶段可达）。因此"WEAR 一律 slotId = null"不成立。
> (2) **JSON_V1 与 LEGACY 都是 `localDate == null`**，因此 matcher phase 4 对它们**不可达**
> （phase 4 要求 `event.localDate != null`，见 `sameDayNullSlotMatchCandidate` 的首行守卫）。

### 16.2 可达性矩阵（三态记法）

记法：**R** = `REACHABLE`（当前 writer 可产生且 matcher 阶段可绑定）·
**C** = `CONDITIONALLY_REACHABLE`（需满足 matcher 的额外条件，如"从未有过窗口证据"）·
**U** = `UNREACHABLE_BY_CURRENT_WRITER`（**不是** domain invariant；domain 仍接受该组合，未来 writer 可能产生）。

| source ＼ provenance | exact | slot-window（无 localDate） | null-slot time-window | null-slot same-day | unmatched |
|---|---|---|---|---|---|
| `MANUAL`（createNew / quickEvent） | **U**（slotId 恒为 null） | **U**（同上） | **R** | **C**（需同 local date 且该事件此前无窗口证据） | **R** |
| `REMINDER` | **R**（slotId + 计划日） | **U**（`localDate` 恒非空） | **U**（slotId 非空） | **U**（同上） | **R**（例如编辑 occurredAt 后 `localDate` 不再等于任何 occurrence 日期） |
| `WEAR`（App 确认） | **R** | **U** | **U** | **U** | **R** |
| `WEAR`（legacy Tile） | **U** | **U** | **R** | **C** | **R** |
| `WIDGET` | **R**（slotId 非空） | **U** | **U** | **U** | **R** |
| `JSON_V1` | **U** | **U** | **R** | **U**（`localDate == null`） | **R** |
| `LEGACY`（迁移行） | **U** | **U** | **R** | **U**（`localDate == null`） | **R** |

**冻结要求**：
1. B-01 的 eligibility/aggregation **不得**假设某 source 只能落在某个 provenance；
   必须按 **entry 实际携带的 provenance** 判定（矩阵只用于解释"为什么某组合在实践中不出现"）；
2. 三态记法必须保留 **U 与"domain 不允许"的区别**——U 只说明"当前 writer 不产生"；
3. `source == LEGACY` **不等于** provenance inferred：两个维度分别断言（legacy 行既可能 unmatched，
   也可能在特定 matcher 条件下进入 inferred 绑定）；
4. 两个维度都必须可分层展示（§17-C/§25）。
