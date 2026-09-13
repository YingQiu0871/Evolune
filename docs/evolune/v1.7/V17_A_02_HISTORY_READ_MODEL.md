# V17-A-02 — History Read Model & Date-Range Adapter（证据记录）

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-A / A-02（**不含 History UI**）
> 起始 HEAD：`03e3d97bcc14708b31f2cdab5acc9851063bbaf4`（A-01 测试/证据，已 APPROVE）
> Commit 1（review accuracy）：`docs: clarify v1.7-A review evidence`
> Commit 2（read model）：`feat: add history day and range read model`
> Commit 3（tests/evidence）：`test: verify v1.7 history range semantics`
> 阶段证据清单（A-02 candidate）：[`evidence/a-02/MANIFEST.sha256`](evidence/a-02/MANIFEST.sha256)（frozen，R1 不再改动）
> R1（极端时区 query-bound 修复）证据：[`evidence/a-02-r1/MANIFEST.sha256`](evidence/a-02-r1/MANIFEST.sha256)
> R1 commits：`fix: widen History event query for timezone extremes`、`docs: verify A-02 extreme timezone bounds`

---

## 1. 目标与边界

```
Room authoritative facts  +  current plan → generated occurrences
        ↓  （A-01 单一 matcher，不重复匹配）
HistoricalProjection（matched / unrecorded / unmatched）
        ↓
HistoricalReadModel（day / inclusive range）
        ↓
future History UI
```

本轮交付 domain read model + app read adapter + JVM 测试。
**未做**：History/Timeline/Insights/Export/retrospective PK 的任何 UI、导航入口、日历、图表、adherence 百分比。
**未改**：Room schema/migration、`/hrt/*` 协议、PK 参数、版本元数据、依赖（`git diff --name-only` 对 `schemas/`、`*.kts`、`gradle/` 均为 0）。

## 2. Unrecorded projection（关闭 High 的 P2）

新增 `UnrecordedHistoricalOccurrence`：**存在 occurrence，但在当前可用 authoritative event 数据中没有匹配到服药记录**。

- 语义仅此一条：**“No recorded intake”**。
- 禁止命名或解释为 `Missed` / `Skipped` / non-adherent / forgot medication；类型 KDoc 明确写出该禁令。
- 不得由该 entry 直接计算严格 historical adherence。
- 与 `MatchedHistoricalOccurrence`、`UnmatchedHistoricalIntake` 一起构成 sealed `HistoricalEntry` 三分支。

**Edited-plan safety 同样适用**：`UnrecordedHistoricalOccurrence` 与 matched 一样携带
`HistoricalScheduleTimeContext.CURRENT_SCHEDULE_CONTEXT`（该枚举仍只有这一个取值）。
即：过去日期由**当前** plan 重新生成的 occurrence 只是 current schedule context，
History 不得暗示“历史上确实应在 09:00 服用而未服用”——那个 09:00 可能是后来改过的计划。

## 3. Projection completeness invariant（可执行）

`HistoricalProjectionBuilder` 在构建末尾执行 `verifyCompleteness(...)`（`check`，**fail fast**，不静默降级）：

| 不变式 | 检查 |
|---|---|
| 每个 generated occurrence 恰好出现一次 | occurrence 引用数 == occurrence 数；集合相等且无重复 |
| 每个 authoritative event 恰好出现一次 | event 引用数 == event 数；集合相等且无重复 |
| 无静默消失 / 无重复消费 | 上述两条的集合与计数比较 |
| 一个 occurrence 最多一个 authoritative event；一个 event 最多被一个 occurrence 消费 | 由 matcher 的 `consumedEvents` 保证，并由本检查在投影层再验证 |

对应确定性测试见 §12。

**该不变式的证明范围（重要）**：它只能证明"**对已经送进 projection 的 inputs 不丢不重**"。
它**不能**证明上游数据库查询没有漏 row —— A-02-R1 的 P1 正是这个区别
（padding 不足导致 `findOccurredBetween` 根本没有返回某条 authoritative event，
projection 内部因此完全"自洽"，不变式不会报警）。上游查询边界的正确性必须由 §6.1 的 bound proof
加极端时区回归测试来保证。

## 4. Day read model

```kotlin
HistoricalDay(date, entries)   // entries 直接来自 projection
  recordedCount          // MatchedHistoricalOccurrence
  unrecordedCount        // UnrecordedHistoricalOccurrence（no recorded intake）
  unmatchedActualCount   // UnmatchedHistoricalIntake
```

**冻结的 entry 排序规则**（由 projection 定义、read model 复用）：

1. `displayDate` 升序；
2. `sortInstant` 升序 —— matched/unrecorded 用 occurrence 的 scheduled instant，unmatched 用 event 的 occurred instant；
3. kind rank —— matched(0) < unrecorded(1) < unmatched(2)；
4. 稳定 tie-break —— occurrence id，或 intake 的 event id。

输入顺序不影响输出顺序（测试用同一集合的正序/逆序断言相等）。

## 5. Range read model

```kotlin
HistoricalReadModel.range(projection, startDate, endDate): HistoricalRange
HistoricalRange(startDate, endDate, days)   // 两者皆 inclusive
```

冻结策略：

- `startDate` / `endDate` **均 inclusive**；
- `days` **升序**，且**只包含实际有 entry 的日期**（空日期由 UI/calendar 层补格）；
- 非法区间（`endDate < startDate`）**fail fast**（`IllegalArgumentException`），不交换、不截断；
- `startDate == endDate` 返回该单日（若为空则 `days` 为空列表）；
- `displayZone` 必须由调用方显式传入，不读取隐式 system default。

## 6. Event fetch: **dual-channel**（app adapter）

`HistoryReadService.readRange(startDate, endDate, displayZone, now, policy)` 使用**两个事件读取通道**，
外加一个 occurrence 生成 context。完整性**不再**依赖任何有限 instant padding。

| 通道 / context | 常量 | 值 | 覆盖的行 | 解决的问题 |
|---|---|---|---|---|
| **A — persisted local date** | `OCCURRENCE_CONTEXT_DAYS` | **1** calendar day | `localDate != null` | persisted 记录日的 completeness（与 `occurredAt` 距离无关） |
| **B — instant context** | `EVENT_QUERY_CONTEXT_DAYS` | **2** calendar days | `localDate == null`（legacy） | display-zone 推导归日 + bounded inferred 候选 |
| occurrence generation context | `OCCURRENCE_CONTEXT_DAYS` | **1** calendar day | — | matcher adjacency：±1h 窗口 + 跨午夜兼容 |

- Channel A：`DoseEventRepository.findRecordedLocalDateBetween(startInclusive, endInclusive)`，**日期端点两端 inclusive**，
  范围为 occurrence context 的日期 `[startDate − 1d, endDate + 1d]`；SQL 为
  `WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ?`（`localDate` 以 ISO-8601 `yyyy-MM-dd`
  持久化，字典序即时间序），排序 `(localDate, occurredAt, id)`。
- Channel B：`DoseEventRepository.findOccurredBetween(startInclusive, endExclusive)`，**instant 半开区间**
  `[ (startDate − 2d).atStartOfDay(zone) , (endDate + 3d).atStartOfDay(zone) )`。
- occurrence 生成窗口：`[ (startDate − 1d).atStartOfDay(zone) , (endDate + 2d).atStartOfDay(zone) )`。
- **并集与去重**：两个通道的结果在进入 `HistoricalProjectionBuilder` 之前按权威 `eventId` 去重
  （`LinkedHashMap`，先 A 后 B）。同一 database row 被两个通道同时命中时只进入 projection 一次；
  若同一 `eventId` 携带**不同内容**则 `check` fail-fast，不猜测哪一行正确。
- 上界：`MAX_RANGE_DAYS = 3660`（产品值未变）。当请求范围接近上限时，occurrence context 会超过
  generator 自身的单窗口上限 `OccurrenceGenerationWindow.MAX_WINDOW_DAYS`，因此 adapter 以
  **不重叠的半开 chunk** 调用同一个 generator（不是缩小 context，也不是第二套 recurrence）。

**为什么不复用 PK 查询**：`getEventsForPk(asOf)` 是 30 天窗口 + 最近 20 条回退 + 无上界，属 PK 专用语义，禁止用于 History。
**不做全表扫描**：A 是带 WHERE 的定向 SQL（不经过 `observeAll`），B 是受限 instant 区间；两者都与请求范围成正比。

### 6.1 Completeness proof（按行分两类，**不再依赖 offset 包络或 padding 数值**）

**Case A — `localDate != null`（含 reminder / Wear confirm 等“语义日 ≠ 行动时刻”的行）**

这类行的最终 display date 由 **persisted recording date**（`PERSISTED_RECORDING_DATE`）决定，而 Channel A
正是按该持久化日期检索，检索范围就是 occurrence context 的**日期区间**。因此：

> 只要 `event.localDate ∈ [startDate − 1d, endDate + 1d]`，该事件必然进入 projection。

该论证与以下因素**完全无关**：`occurredAt` 距离计划日多远、recording zone 与 display zone 的 offset 差、
DST、日期线跳变、以及 padding 取值。这是 R2 移除“完整性依赖有限 instant padding”这一架构前提的方式。

> 为什么必须如此：应用自身存在**无界**偏离的写入路径——reminder 确认写入 `localDate = 计划日` 而
> `occurredAt = 点击时刻`（`ReminderReceiverWork` 无任何最大迟到校验），Wear App confirm 写入
> `localDate = command.localDate`（快照里的计划日）而 `occurredAt = 处理时刻`（只校验 producer generation，
> `createdAt` 仅要求 >0）。因此任何有限的 instant padding 都不可能充分。

**Case B — `localDate == null`（v3 迁移前的 legacy 行，`zoneId` 亦为 NULL）**

这类行的 display date 由 `occurredAt` 在 **display zone** 推导（`CURRENT_DISPLAY_TIMEZONE_DERIVED`）。
“最终 display date ∈ requested range”意味着 `occurredAt` 落在 `[startDate 00:00, endDate+1 00:00)` 的
display-zone 区间内，而 Channel B 的窗口严格包含该区间（两端各多 2 个 calendar day）。因此 bounded
instant 查询对 Case B 是**可严格绑定**的，不需要 offset 包络假设。

**matcher adjacency 的额外 padding**：historical matcher 允许相邻日期的 inferred 兼容匹配，因此 occurrence
生成使用 `OCCURRENCE_CONTEXT_DAYS = 1` 的相邻日期 context；Channel A 使用**同一日期区间**，使相邻日的
persisted-date 行也能参与匹配。context-only 行随后由 `HistoricalReadModel.range` 按最终 display date
过滤回请求范围，不会泄漏。

**历史证明的处置（已删除/降级）**：R1 曾以“IANA 现代民用 offset 为 UTC−12…UTC+14”“单个 persisted local date
的 instant 跨度约 26h”“两个 calendar day 即使被 DST 缩短也不低于约 46h”作为 `EVENT_QUERY_CONTEXT_DAYS = 2`
的充分性证明。该论证实际依赖“persisted `localDate` 与 `occurredAt` 在 recording-zone offset 内一致”这一
**未被强制的隐含前提**，因此已被移除。实测补充：IANA 全库 offset 包络实为
`Asia/Manila` LMT −15.936h … `America/Metlakatla` LMT +15.228h（跨度 31.164h，而非 26h）；日期线跳变处
（如 `Pacific/Apia` 2011-12-30 被跳过）两个 calendar day 的实际长度可短至 **24h**，而非 ≥46h。
`EVENT_QUERY_CONTEXT_DAYS = 2` 保留，但其角色仅为 **Case B 与 adjacency 的 bounded context**，
**不再承担任何 persisted-date completeness 保证**。

CE1/CE2 保留为回归（§14），但它们现在主要通过 **Channel A** 被保证。


## 7. Occurrence context bounds

occurrence 用现有 `MedicationOccurrenceGenerator` 生成（无第二套 recurrence 逻辑），窗口为
`OCCURRENCE_CONTEXT_DAYS = 1` 的 `[startDate−1, endDate+1]`：

> **occurrence context 解决 matcher adjacency（±1h 窗口 + 跨午夜兼容）。**
> **Channel A 复用同一日期区间**，因为相邻日的 inferred 匹配是靠 persisted 日期决定的；
> **Channel B 的 instant context（2 天）解决 Case B 与 bounded 候选**，两者职责不同。

**occurrence context 不得被用来"顺带"修 persisted-date completeness**（R1 曾如此，R2 已改为独立通道）。

当请求范围接近 `MAX_RANGE_DAYS` 上限、context 跨度超过 generator 的单窗口上限
(`OccurrenceGenerationWindow.MAX_WINDOW_DAYS = 3660`) 时，adapter 用**不重叠半开 chunk** 调用同一个 generator
（保持 ±1 天 context 不被缩小，也不引入第二套 recurrence）。分块正确性由
`HistoryReadServiceOccurrenceChunkingTest` 覆盖：用**真实 plan** 与 3660 天区间强制分块，断言

- CUSTOM（`intervalDays = 3`）与 WEEKLY（Mon/Thu）的 recurrence phase 跨 chunk 不变；
- 相邻 occurrence 日期差恒等于周期（无 gap、无多出）；
- 日期无重复，且 occurrence 总数与**独立重算**的期望集合完全一致；
- 区间首尾的 occurrence 均未丢失。

由于 chunk 边界是**任意 instant**（`chunkStart + 3660d`）而非本地午夜，正确性依赖 generator 只按
`scheduledAt ∈ [start, end)` 过滤 occurrence——分块不会重复产出边界 occurrence。需注意 generator 的
recurrence 锚点是 `schedule.createdAt`（与窗口起点无关），这是分块不改变 phase 的根本原因。

**context 与最终返回范围分离**：投影在 context 上执行，随后按 **projection 的最终 display date** 过滤到 `[startDate, endDate]`；
相邻日期的 occurrence/entry 不会泄漏进结果（测试显式断言，含 Channel A 的 context 行）。

## 8. Timezone / date filtering

最终范围过滤**只消费 A-01 冻结的 display date 与 provenance**，不在 adapter 里另写
`occurredAt.atZone(displayZone).toLocalDate()`：

| entry 类型 | display date 来源 | provenance |
|---|---|---|
| MatchedHistoricalOccurrence | occurrence 的 intended local date | `INTENDED_LOCAL_DATE` |
| UnmatchedHistoricalIntake（有 persisted localDate） | persisted 记录日期 | `PERSISTED_RECORDING_DATE` |
| UnmatchedHistoricalIntake（true legacy orphan） | `instant + displayZone` 推导 | `CURRENT_DISPLAY_TIMEZONE_DERIVED` |

## 9. 关键用例

| 场景 | 结果 |
|---|---|
| Cross-midnight | 前一日 23:00 的 legacy event（无 slot/localDate）匹配到请求日首条 occurrence，落在**请求日**，provenance `NULL_SLOT_TIME_WINDOW`，`crossesLocalDateBoundary = true`；前一日的 day 查询为空 |
| Edited plan | unrecorded occurrence 携带 `CURRENT_SCHEDULE_CONTEXT`；matched 亦同；枚举无其它取值 |
| Deleted plan | 无 plan 时事件仍以 `UnmatchedHistoricalIntake` 出现在其记录日/范围内 |
| Timezone | 同一 true legacy orphan instant：Paris 归 2025-01-05、Shanghai 归 2025-01-06；persisted localDate 场景跨展示时区仍归记录日 |
| Context 隔离 | context 内的相邻日 entry 不出现在最终 range |

## 10. DoseCheckInMatcher 边界（High 的 P2，本轮只记录不修改）

`app/src/main/java/io/github/yingqiu0871/evolune/reminder/DoseCheckInMatcher.kt` 保留自己的
`DOSE_CHECK_IN_WINDOW_MILLIS = 1h` + `route/ester/dose` 判定。

- 它**不是** historical matcher：用途是**提醒抑制**（判断某个 plan 的 scheduled 时刻附近是否已有 check-in）。
- 它**不应**被 History / Timeline / Insights 复用；本轮未改其一行代码。
- 已知风险：reminder 语义与 History 语义可能不一致（例如同一事件在 reminder 侧算 check-in、在 projection 侧因歧义不归属）。
- 计划：在 A-04 hardening 或单独 task 中评估能否复用 `MedicationOccurrencePolicy`，**不得**为了“只剩一个 ±1h 常量”破坏 reminder 现有行为。
- 相应地，文档中"全仓只有一个 ±1h"的绝对表述已更正为"**historical occurrence matching 只有一个实现来源**"。

## 11. Stop conditions 复核

| 停止条件 | 是否触发 |
|---|---|
| History 需要第二套 matcher | 否（复用 A-01 projection） |
| `localDate` 列无法在不改 schema 的情况下查询 | 否（列已存在且为 ISO-8601 文本，`WHERE localDate >= ? AND localDate <= ?` 即可；未改 schema/version/migration） |
| repository query 必须全表扫描 | 否（Channel A 为带 WHERE 的定向 SQL，不经 `observeAll`；Channel B 为受限 instant 区间） |
| dual-channel union 必须猜测冲突 row | 否（按 eventId 去重；同 id 不同内容直接 fail-fast，不猜） |
| correct fetch 需要改变 Reminder/Wear 写入语义 | 否（写入侧一行未改，改的是读取侧通道） |
| chunking 测试暴露 generator 本身行为错误 | 否（CUSTOM/WEEKLY 跨 chunk phase 与期望集合完全一致） |
| range query 无法在不全量扫描/不改 schema 下正确完成 | 否（双通道 + 有界 context） |
| current plan 无法安全生成 requested context range | 否（generator + ±1 天 context + 分块） |
| orphan inclusion 需要伪造 plan ownership | 否（不恢复归属，只保留事实） |
| unrecorded state 必须被错误解释成 Missed | 否（命名与 KDoc 明确禁止） |
| timezone filtering 必须绕过 A-01 provenance | 否（只消费 display date/provenance） |

**未触发任何停止条件**，无需 `A-02-R2 ARCHITECTURE DECISION REQUIRED`。

## 12. 测试与 fresh 验证

命令（**显式 `--rerun-tasks`**，日志中三个 test task 均为实际执行，非 UP-TO-DATE）：

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

**A-02-R2 fresh 结果**（`evidence/a-02-r2/`，计数由 XML `testsuite` 属性求和，不从构建日志读）：

| 模块 | XML 文件 | tests (total cases) | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 84 | **718**（R1 707 + R2 新增 11） | 0 | 0 | 0 | 718 |
| experience-core | 13 | **125** | 0 | 0 | 0 | 125 |
| wear | 11 | **90** | 0 | 0 | 0 | 90 |
| **合计** | **108** | **933** | **0** | **0** | **0** | **933** |

- `BUILD SUCCESSFUL in 1m 3s`，`54 actionable tasks: 54 executed`（日志：`evidence/a-02-r2/a02r2-jvm-run.log`；日志内 `> Task :experience-core:test` / `:app:testDebugUnitTest` / `:wear:testDebugUnitTest` 均无 `UP-TO-DATE`）。
- **受影响面 Android instrumentation**：`RoomRepositoryTest`（含新增的 persisted-local-date DAO 用例）
  在 `Pixel_7(AVD) - 15`（API 35）上 `Starting 26 tests` → `Finished 26 tests`，XML `tests=26 / failures=0 / errors=0 / skipped=0`，
  `BUILD SUCCESSFUL`。日志与 XML 见 `evidence/a-02-r2/`。
- 计数由 XML `testsuite` 属性求和（`evidence/a-02-r2/jvm-aggregate.tsv`），**不从构建日志读计数**。
- `git diff --check`：工作树与 `72a468c..HEAD` 区间均为 0（evidence 目录受 `* -text -whitespace` 约束，源码/普通文档另行独立检查）。
- R2 新增覆盖：delayed reminder / delayed Wear confirm / D+400d 任意偏离（`HistoryReadServicePersistedDateTest`）、
  双通道去重、同 id 冲突 fail-fast、null-localDate legacy 仍走 instant 通道、context-only 不泄漏、
  CUSTOM/WEEKLY 跨 chunk 的 recurrence phase 与无 gap/无重复（`HistoryReadServiceOccurrenceChunkingTest`）、
  persisted-local-date DAO 的 inclusive 契约（instrumentation）。

> 历史（A-02 首轮）曾以 app 700 / experience-core 125 / wear 90 = 915 为基线，R1 为 707 / 125 / 90 = 922；
> 各自证据分别冻结在 `evidence/a-02/` 与 `evidence/a-02-r1/`，未被本轮修改。

## 14. A-02-R1 变更记录（极端时区 query-bound 修复）

**Finding（P1）**：原实现 event 查询与 occurrence 生成共用 ±1 calendar-day padding，
无法覆盖所有合法 persisted recording-zone ↔ display-zone 组合，会**静默丢 authoritative event**。

**反例复现（JVM tzdb，不手写 offset）**：

| 用例 | display zone | persisted zone / localDate | occurredAt | 旧 query bound | 旧结果 |
|---|---|---|---|---|---|
| CE1 下界 | `Etc/GMT+12`，2025-06-15..06-17 | `Pacific/Kiritimati` / 2025-06-15 00:00 | `2025-06-14T10:00:00Z` | start `2025-06-14T12:00:00Z` | **事件被漏掉** |
| CE2 上界 | `Pacific/Auckland`，2025-01-10..01-12 | `Etc/GMT+12` / 2025-01-12 23:59:59 | `2025-01-13T11:59:59Z` | endExclusive `2025-01-13T11:00:00Z` | **事件被漏掉** |

**修复**：拆分 `OCCURRENCE_CONTEXT_DAYS = 1` 与 `EVENT_QUERY_CONTEXT_DAYS = 2`（当时的 bound proof 见旧 §6.1；
**该证明已由 R2 判定为依赖未强制的隐含前提并移除**，见 §6.1 与 §15）；
未改 matcher / provenance / schema / DAO / recurrence / Widget / Wear / PK / UI / 依赖。
接近 `MAX_RANGE_DAYS` 时按 chunk 调用同一 generator（保持 context，不缩小）。

**before / after（同一测试套件，仅把 `EVENT_QUERY_CONTEXT_DAYS` 临时设回 1）**：

| 状态 | 结果 |
|---|---|
| before（padding = 1） | `HistoryReadServiceExtremeZoneTest`：`7 tests completed, 3 failed` —— CE1 下界、CE2 上界、以及"event padding 必须大于 occurrence padding"断言均 FAILED；CE1/CE2 的失败信息为 `query start … must include the authoritative instant …` / `query end … must be after the authoritative instant …` |
| after（padding = 2） | `BUILD SUCCESSFUL`，7/7 通过 |

**MAX_RANGE_DAYS 边界测试（P2）**：inclusive **3660** 天 → 允许（且 generator 分块生效）；
inclusive **3661** 天 → `IllegalArgumentException`，且**发生在 repository access 之前**（断言 `recordedRange == null`）。
`start == end` 用例保留。**产品值 `MAX_RANGE_DAYS` 未改**。

**legacy / display-zone 语义未变**：true legacy orphan 仍按 `occurredAt + displayZone` 归日
（query 变宽不影响最终 inclusion，测试断言同一 orphan 在 Paris 归 01-05、Shanghai 归 01-06、Shanghai 查 01-05 为空）。

**occurrence context 仍为 1 天**：跨午夜 `previous-day 23:00 → requested-day 00:00` 的 inferred match 有专门回归测试。

**R1 fresh 结果**（显式 `--rerun-tasks`，三个 test task 均实际执行、无 UP-TO-DATE）：

| 模块 | XML | tests | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 82 | **707**（700 + 新增 7） | 0 | 0 | 0 | 707 |
| experience-core | 13 | 125 | 0 | 0 | 0 | 125 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **106** | **922** | **0** | **0** | **0** | **922** |

证据：[`evidence/a-02-r1/MANIFEST.sha256`](evidence/a-02-r1/MANIFEST.sha256)（fresh XML + aggregate TSV + Gradle log + before/after 复现记录）。
A-02 candidate 的 `evidence/a-02/MANIFEST.sha256` 保持 frozen，未被 R1 修改。

**DoseCheckInMatcher**：R1 未修改，边界结论沿用 §10（reminder matcher ≠ historical matcher；A-04 / 独立 hardening 再评估）。

## 15. A-02-R2 变更记录（persisted-date-aware 双通道取数）

**Finding（P1，与 R1 不同源）**：R1 后 History 仍可能静默漏 authoritative event，因为 persisted `localDate` 与
`occurredAt` 之间**没有有界一致性 invariant**：reminder 确认写入 `localDate = 计划日` 而 `occurredAt = 点击时刻`
（`ReminderReceiverWork.handleLocked` 无任何最大迟到校验），Wear App confirm 写入 `localDate = command.localDate`
（快照计划日）而 `occurredAt = 处理时刻`（只校验 producer generation；`createdAt` 仅要求 >0）。
因此**任何有限 instant padding 都无法保证完整性**，把 padding 从 1 加到 2（或任何 N）都不是修复。

**确定性反例（不依赖极端时区）**：Europe/Paris、plan 08:00、D = 2025-06-15 的提醒被忽略，用户 2025-06-21 才确认
→ 写入 `occurredAt = 2025-06-21…`、`localDate = 2025-06-15`、`slotId = S`。查询 `[D, D]` 时 R1 的 instant 窗口
取不到该行；而阶段 1 只看 `slotId + localDate`（无时间距离判据）本会匹配它，于是该日被呈现为
`UnrecordedHistoricalOccurrence` —— History **对一条真实记录宣称“未记录”**。

**修复（架构层面）**：改为**双通道取数**（§6），使 completeness 不再依赖 padding 数值。

| 通道 | 查询 | 覆盖 |
|---|---|---|
| A — persisted local date | `findRecordedLocalDateBetween(start−1d, end+1d)`（两端 inclusive，SQL 带 `WHERE localDate IS NOT NULL AND localDate >= ? AND localDate <= ?`） | `localDate != null` 的所有行 |
| B — instant context | `findOccurredBetween(...)`（±2 天半开区间，**行为未改**） | `localDate == null` 的 legacy 行、bounded 相邻候选 |

并集按权威 `eventId` 去重（`LinkedHashMap`，A 后 B）；同 id 不同内容 → `check` fail-fast。
**未改**：Room entity / schema version / migration、A-01 matcher 与 provenance 语义、occurrence generator 实现、
Widget、Wear 协议与行为、PK、Compose/UI/navigation、依赖与版本。

**新增回归**（`HistoryReadServicePersistedDateTest`，8 例）：

| 用例 | 断言 |
|---|---|
| delayed reminder（D → D+6d） | 该日 `recordedCount == 1`、`unrecordedCount == 0`、provenance `EXACT_SLOT_AND_LOCAL_DATE`、`occurredAt` 保留 |
| delayed Wear confirm（D → D+30d） | 同上 |
| 任意大偏离（D → D+400d） | 仍匹配 → 证明 correctness 与 padding 数值无关 |
| 同一行被两个通道命中 | projection 中只出现一次，`recordedCount` 不翻倍 |
| 同 id 内容冲突 | `IllegalStateException` fail-fast，不猜哪行正确 |
| true legacy orphan（无 localDate） | 仍只经 Channel B；Paris 归 01-05、Shanghai 归 01-06 |
| 跨午夜 legacy 推断 | Channel A 存在时仍为 `NULL_SLOT_TIME_WINDOW` + `crossesLocalDateBoundary` |
| context-only 行（±1 天） | 被 Channel A 取到但**不泄漏**进最终范围；断言 A 的日期区间恰为 `[D−1, D+1]` |

**red evidence（同一套件，仅把 Channel A 临时置空 = 复现 R1 的 instant-only 架构）**：

| 状态 | 结果 |
|---|---|
| before（instant-only） | `8 tests completed, 4 failed` —— delayed reminder / delayed Wear / D+400d 三条 delayed 用例与冲突 fail-fast 用例 FAILED（`BUILD FAILED`） |
| after（双通道） | `BUILD SUCCESSFUL`，8/8 通过 |

日志：`evidence/a-02-r2/a02r2-before-instant-only.log`（红）、`a02r2-after-dual-channel.log`（绿）。

**CE1 / CE2 状态**：保留为回归（§14），未删除；`HistoryReadServiceExtremeZoneTest` 的 fake 现在**同时**按
instant 窗口与 persisted-date 区间过滤，并新增断言：CE1/CE2 的 persisted `localDate` 落在 Channel A 的日期区间内
→ 这两例现在**主要通过 Channel A** 被保证，不再被当作“2 天 instant padding 正确性”的核心证明。

**chunking P2 关闭**：新增 `HistoryReadServiceOccurrenceChunkingTest`（真实 plan、3660 天区间强制分块、CUSTOM 与 WEEKLY
的 phase/无 gap/无重复/首尾不丢断言），见 §7。**generator 实现未改**。

**DAO instrumentation（受影响面）**：`RoomRepositoryTest.persistedLocalDateRangeQueryIsInclusiveAndIgnoresInstantDistance`
在真实 Room 内存库上断言：两端 inclusive、`localDate IS NULL` 不进入该查询、`occurredAt` 相距数年仍被返回、
排序为 `(localDate, occurredAt, id)`、单日区间、以及反向区间 fail-fast。

## 13. 未做 / 已知限制

- 无任何 UI 层（连 preview fixture 都未新增）。
- disabled plan 在当前 schedule context 下不产生 occurrence；其历史事件仍以 unmatched intake 保留（语义已在文档冻结）。
- `DoseCheckInMatcher` 与 projection 的语义差异未收敛（见 §10），登记为后续评估项。
- Decision H（Widget 拒绝反馈机制）仍为实现待办，不在本轮。
