# V17-A-02 — History Read Model & Date-Range Adapter（证据记录）

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-A / A-02（**不含 History UI**）
> 起始 HEAD：`03e3d97bcc14708b31f2cdab5acc9851063bbaf4`（A-01 测试/证据，已 APPROVE）
> Commit 1（review accuracy）：`docs: clarify v1.7-A review evidence`
> Commit 2（read model）：`feat: add history day and range read model`
> Commit 3（tests/evidence）：`test: verify v1.7 history range semantics`
> 阶段证据清单：[`evidence/a-02/MANIFEST.sha256`](evidence/a-02/MANIFEST.sha256)

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

## 6. Event query bounds（app adapter）

`HistoryReadService.readRange(startDate, endDate, displayZone, now, policy)`：

| 项 | 值 |
|---|---|
| context 日期 | `[startDate − 1 天, endDate + 1 天]`（`CONTEXT_DAYS = 1`） |
| event 查询窗口 | **instant** 半开区间 `[ (startDate−1).atStartOfDay(zone) , (endDate+2).atStartOfDay(zone) )` |
| 使用的现有 API | `DoseEventRepository.findOccurredBetween(startInclusive, endExclusive)` |
| 上界 | `MAX_RANGE_DAYS = 3660`（与 occurrence generator 的十年上限一致），超出即 `require` 失败 |

**为什么按 instant 而不是 `localDate`**：legacy 行可能没有 `localDate`，且 display zone 会改变 derived day；
按 instant 查询可保证不遗漏。**为什么不复用 PK 查询**：`getEventsForPk(asOf)` 是 30 天窗口 + 最近 20 条回退 + 无上界，
属 PK 专用语义，禁止用于 History（测试不引用它）。

**不做全表扫描**：查询窗口严格为 `请求天数 + 2 天`，与请求范围成正比。

## 7. Occurrence context bounds

occurrence 用现有 `MedicationOccurrenceGenerator` 生成（无第二套 recurrence 逻辑），窗口与 §6 的 context 相同
（`[startDate−1, endDate+1]`）。这样既覆盖请求日期，又保留 ±1h / 跨午夜兼容匹配所需的相邻日期 context。

**context 与最终返回范围分离**：投影在 context 上执行，随后按 **projection 的最终 display date** 过滤到 `[startDate, endDate]`；
相邻日期的 occurrence/entry 不会泄漏进结果（测试显式断言）。

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
| range query 无法在不全量扫描/不改 schema 下正确完成 | 否（有界 instant context 窗口） |
| current plan 无法安全生成 requested context range | 否（generator + ±1 天 context） |
| orphan inclusion 需要伪造 plan ownership | 否（不恢复归属，只保留事实） |
| unrecorded state 必须被错误解释成 Missed | 否（命名与 KDoc 明确禁止） |
| timezone filtering 必须绕过 A-01 provenance | 否（只消费 display date/provenance） |

**未触发任何停止条件**，无需 `A-02 ARCHITECTURE DECISION REQUIRED`。

## 12. 测试与 fresh 验证

命令（**显式 `--rerun-tasks`**，日志中三个 test task 均为实际执行，非 UP-TO-DATE）：

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --rerun-tasks --no-daemon --console=plain
```

| 模块 | XML 文件 | tests (total cases) | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 81 | **700**（689 + 新增 11） | 0 | 0 | 0 | 700 |
| experience-core | 13 | **125**（109 + 新增 16） | 0 | 0 | 0 | 125 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **105** | **915** | **0** | **0** | **0** | **915** |

- `BUILD SUCCESSFUL in 55s`，`54 actionable tasks: 54 executed`（日志：`evidence/a-02/v17a02-jvm-run.log`；日志内 `> Task :experience-core:test` / `:app:testDebugUnitTest` / `:wear:testDebugUnitTest` 均无 `UP-TO-DATE`）。
- 计数由 XML `testsuite` 属性求和（`evidence/a-02/jvm-aggregate.tsv`），**不从构建日志读计数**。
- 新增覆盖：completeness（含 mixed batch、重复消费）、day 分组与排序、range（单日/多日/空/start=end/非法）、timezone（三态归因 + 跨时区归日）、cross-midnight context、edited-plan、deleted-plan；app adapter 侧覆盖 query bounds、context bounds、displayZone 转发、projection 复用、最终范围过滤、非法区间 fail-fast。
- `git diff --check`：工作树与 `72a468c..HEAD` 区间均为 0（evidence 目录受 `* -text -whitespace` 约束，源码/普通文档另行独立检查）。
- 未运行 instrumentation：本轮只改 JVM/domain 与 app read adapter，未触及 DAO/Room Android 实现。

## 13. 未做 / 已知限制

- 无任何 UI 层（连 preview fixture 都未新增）。
- disabled plan 在当前 schedule context 下不产生 occurrence；其历史事件仍以 unmatched intake 保留（语义已在文档冻结）。
- `DoseCheckInMatcher` 与 projection 的语义差异未收敛（见 §10），登记为后续评估项。
- Decision H（Widget 拒绝反馈机制）仍为实现待办，不在本轮。
