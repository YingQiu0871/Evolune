# V17-A-01 — Historical Projection & Match Provenance Foundation（证据记录）

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-A / A-01（**不含 History UI**）
> 起始 HEAD：`7372ce9883615ec8970f0b7525658fe580ceadbd`（PRE-A-01E evidence closure）
> Gate cleanup commit：`a0144df85f2890b83b1af7a240939705128071c5`（`docs: resolve v1.7-A gate semantics`）
> 实现 commit：`192c8d3bd9edb25db3b2c2809a168b086d7cd8ae`（`feat: add historical projection provenance foundation`）
> 测试/证据 commit：本文件所在的 `test: verify v1.7 historical projection semantics`
> 阶段证据清单：[`evidence/a-01/MANIFEST.sha256`](evidence/a-01/MANIFEST.sha256)

---

## 1. 目标与边界

构建共享 historical projection foundation：authoritative plans/occurrences/events → **既有 matching policy** → projection + match provenance → 供未来 History / Timeline / Insights / retrospective PK 消费。

- 未新建第二个事实源，未新建 Room table，未复制 matching 逻辑。
- 本轮**不含** History/Timeline/Insights/Export UI，不含 retrospective PK，不含 Widget/Wear 实现。
- 未改 Room schema/migration、`/hrt/*` 协议、PK 数值参数、版本元数据、依赖。

## 2. Occurrence identity golden vector（M1）

实现未改动（仍为 `UUID.nameUUIDFromBytes`，RFC 4122 **version 3 / MD5**）。

| 输入 | 值 |
|---|---|
| `planId` | `00000000-0000-0000-0000-000000000001` |
| `slotId` | `00000000-0000-0001-0000-000000000001` |
| `intendedLocalDate` | `2025-01-02` |
| **expected occurrence UUID** | **`31d6dd5f-b6f1-3607-8d62-28947fa406fb`**（version 3） |

期望值由**独立实现**推导（外部 MD5 复现 RFC 4122 v3 语义，不调用被测函数），hard-code 进测试；
测试同时断言 `version() == 3`、同输入稳定、date/slot/plan 任一变化即不同。
测试文件：`experience-core/src/test/.../MedicationOccurrenceIdentityGoldenVectorTest.kt`（6 个用例）。

## 3. Match provenance model（M7）

四阶段 matcher 被**提取为单一实现** `MedicationOccurrenceMatcher`（`experience-core/.../MedicationOccurrenceMatcher.kt`，
算法逐行沿用，未改 policy/窗口/唯一性判据），每次决策携带：

| Provenance | 对应阶段 | 含义 |
|---|---|---|
| `EXACT_SLOT_AND_LOCAL_DATE` | 阶段 1 | 持久化 `slotId` + 持久化 `localDate` 精确匹配（不看时间距离） |
| `SLOT_WINDOW_WITHOUT_LOCAL_DATE` | 阶段 2 | 有 `slotId` 但无可信 `localDate`，按 ±1h 闭区间匹配 |
| `NULL_SLOT_TIME_WINDOW` | 阶段 3 | 无 slot 身份（legacy null-slot），按 ±1h 闭区间匹配 |
| `NULL_SLOT_SAME_DAY` | 阶段 4 | 无 slot 身份，仅由延迟的 same-local-date 回退匹配 |

`MedicationOccurrencePresentation.deriveWithMatches(...)` 返回 **items + matches**（单次匹配），
`derive(...)` 行为不变（既有 23 个表示层用例全绿）。

**Shared-matcher guarantee**：`HistoricalProjectionBuilder` 只调用 `deriveWithMatches`，
不包含任何窗口/唯一性判定；`Entry` 的 provenance 直接来自该决策。

**准确表述（A-02 更正）**：这是"**historical occurrence matching 只有一个实现来源**"，
**不等同于**"全仓只有一个 ±1h 概念"：`reminder/DoseCheckInMatcher.kt` 另有独立 ±1h 检查用于**提醒抑制**，
`wear/WearAppSnapshotBuilder` 用 `matchBefore + matchAfter` 计算上下文窗口宽度（不参与匹配判定）。
边界与后续评估见 [V17_A_02_HISTORY_READ_MODEL.md](V17_A_02_HISTORY_READ_MODEL.md)。测试 `projection provenance agrees with the presentation matcher for the same input`
断言两个 API 对同一输入给出同一 pairing。

## 4. Unmatched / orphan intake projection（M8）

新增 sealed `HistoricalEntry`：
- `MatchedHistoricalOccurrence`（occurrence + event + provenance + status + actionAvailability + schedule context + cross-date flag）
- `UnmatchedHistoricalIntake`（event 全字段 + `source` + display date provenance）

authoritative actual event **不会**因为没有 occurrence 而从投影消失。`source` 规则：
`isManualIntake` 仅在 `source == MANUAL` 时为真（测试遍历全部 6 个 source，断言只有 MANUAL 命中）。

## 5. 关键语义用例（全部确定性）

| 需求 | 用例 | 结果 |
|---|---|---|
| M4 跨日 legacy | 昨日 23:00 legacy null-slot event（无 slot/无 localDate）↔ 今日 00:00 occurrence | 命中阶段 3 `NULL_SLOT_TIME_WINDOW`，且 `crossesLocalDateBoundary == true`；**未**标为 exact；既有兼容行为保留 |
| M4 边界 | `-1h` / `+1h` 闭区间命中；`+1h+1s`（无 persisted date）不匹配 | 通过；越界者成为 unmatched 且 provenance = `CURRENT_DISPLAY_TIMEZONE_DERIVED` |
| M4 同日回退辨析 | 同 `localDate` 但远超窗口的 null-slot 事件 | 命中阶段 4 `NULL_SLOT_SAME_DAY`（与窗口匹配明确区分） |
| M4 歧义 | 两条 occurrence 同时落在同一 ±1h 窗口内 | **不匹配**（保持 unmatched），不升级为 certainty |
| M3 计划改时间 | 08:00 → 09:00、slotId 保持 | 仍为 `EXACT_SLOT_AND_LOCAL_DATE`（身份/日期匹配）；`scheduleTimeContext == CURRENT_SCHEDULE_CONTEXT`；断言 `HistoricalScheduleTimeContext` **只有**该值（不存在 historical snapshot 概念）；event instant 不变 |
| M2 计划删除 | 无 occurrence，事件仍在 | 事件保留为 unmatched，id/instant/persisted localDate/matchKey 均可用 |
| M5 时区 | bound → `INTENDED_LOCAL_DATE`；persisted context → `PERSISTED_RECORDING_DATE`；true legacy orphan → `CURRENT_DISPLAY_TIMEZONE_DERIVED` | 通过；同一 legacy orphan instant 在 Paris=01-02、Shanghai=01-03（`occurredAt` 不变），且 `isOriginalLocalDate == false` |
| M6 DST gap | Paris 2026-03-29 02:30 | wall-clock intent `02:30` 与 resolved instant `01:30Z` 分离；matching/差值用 instant |
| M6 DST overlap | Paris 2026-10-25 02:30 | 仍只 materialize **1** 条 occurrence（较早偏移）；在第二个 02:30 记录的真实 intake 绑定到该 occurrence 并呈现 `+1h` instant difference；未创建第二条 occurrence |

## 6. Fresh 验证（JUnit XML 为计数来源）

命令：

```bash
./gradlew :experience-core:test :app:testDebugUnitTest :wear:testDebugUnitTest --no-daemon --console=plain
```

| 模块 | XML 文件 | tests (total cases) | skipped | failures | errors | passed |
|---|---:|---:|---:|---:|---:|---:|
| app | 80 | 689 | 0 | 0 | 0 | 689 |
| experience-core | 12 | **109**（基线 84 + 新增 25） | 0 | 0 | 0 | 109 |
| wear | 11 | 90 | 0 | 0 | 0 | 90 |
| **合计** | **103** | **888** | **0** | **0** | **0** | **888** |

- `BUILD SUCCESSFUL`（原始运行日志：`evidence/a-01/v17a01-jvm-run.log`）。
- 计数由 XML 的 `testsuite` 属性求和得出（`evidence/a-01/jvm-aggregate.tsv`），**不从构建日志读计数**。

**执行新鲜度口径（据实说明）**：该捕获日志中 `:experience-core:test` 为 **UP-TO-DATE**，而 `:app:testDebugUnitTest`、`:wear:testDebugUnitTest` **实际执行**。
因此：

- JUnit XML 提供的是 **candidate state 的 888 个测试结果**（artifacts 可验证）；
- 其中 **109 个 experience-core candidate XML 可独立验证**（它们来自本轮一次真实执行），但**该日志本身不构成 `--rerun-tasks` 的 fresh 证明**；
- 本文件**不**声称"三个模块都在该日志中 fresh rerun"。A-02 起改为显式 `--rerun-tasks` 或等价可证明执行的新鲜运行。
- 新增测试分布（按测试类逐文件核对）：**golden vector 6 / provenance 10 / intake-timezone-DST 9 = 25**。
- 既有 `MedicationOccurrencePresentationTest`（23 用例）保持全绿 → matcher 行为未变。
- `git diff --check`：退出码 0。**范围说明**：`docs/evolune/v1.7/evidence/**` 受 `* -text -whitespace` 属性约束，因此该目录内的原始产物不会触发 whitespace lint；**源码与普通文档范围另行独立检查为 clean**。
- 未运行 instrumentation：本轮未触及 Android 集成（仅 experience-core domain 与 app 侧 mapper）。

## 7. Evidence manifest（阶段化）

`docs/evolune/v1.7/evidence/a-01/MANIFEST.sha256` 覆盖本阶段全部 data evidence（103 个 JUnit XML + 1 个 aggregate TSV + 1 个运行日志），
覆盖断言与校验方式：

```bash
cd docs/evolune/v1.7/evidence/a-01
expected=$(find . -type f ! -name 'MANIFEST.sha256' | wc -l)
listed=$(wc -l < MANIFEST.sha256)
[ "$expected" = "$listed" ] || { echo "COVERAGE MISMATCH"; exit 1; }
sha256sum -c MANIFEST.sha256
```

Phase-0（`evidence/MANIFEST.sha256`）与 PRE-A-01（`evidence/pre-a-01/MANIFEST.sha256`）的 scope **未改动**。

## 8. 未做 / 已知限制

- 未实现任何 UI（History/Timeline/Insights/Export/retrospective PK）；
- `UnmatchedHistoricalIntake` 不尝试恢复计划归属（`dose_events` 无 `planId` 列，数据上不可恢复）；
- `crossesLocalDateBoundary` 是**推断标注**，不改变既有匹配结果；
- DST overlap 的"+1h instant difference"是本轮**记录**的限制，未重新设计 DST generation；
- Decision H（Widget 拒绝动作的用户可见反馈机制）本轮未实现，保留为后续 Widget UX acceptance item。

## 9. Stop conditions 复核

| 停止条件 | 本轮是否触发 |
|---|---|
| provenance 需要第二套 matcher | 否（复用同一 matcher，提取而非复制） |
| orphan recovery 必须改 Room schema | 否（不恢复计划归属，只保留事实） |
| edited plan 语义无法在不声称 historical planned time 的前提下表达 | 否（`CURRENT_SCHEDULE_CONTEXT` 单值枚举 + 测试固化） |
| 既有 matcher 必须改变行为才能支持 projection | 否（行为未变，既有用例全绿） |
| occurrence identity golden vector 暴露已有不稳定性 | 否（独立推导值与实现一致，version 3 稳定） |
| legacy event 丢失 actual fact 的唯一解法需要 schema change | 否（未匹配事件完整保留） |

**未触发任何停止条件**，无需 `A-01 ARCHITECTURE DECISION REQUIRED`。
