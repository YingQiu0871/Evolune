# V17-B-02 — Insights ViewModel & Range Orchestration

> 状态：`IMPLEMENTED / READY FOR INDEPENDENT REVIEW`
> Round：v1.7-B / **B-02**（range orchestration + ViewModel；**不含** Insights Compose UI / charts）
> B-00 semantics HEAD：`4458a47a19ae01488dd86c0e2e488df11965101b`（V17-B-00-R1 APPROVE，语义冻结）
> B-01 approved HEAD：`822de0048eb2aa4da7fdd5d1db16e379b2a31074`（V17-B-01 APPROVE，Insights domain）
> 起始 HEAD（本轮）：`822de0048eb2aa4da7fdd5d1db16e379b2a31074`
> 依据（§1 preflight 重读）：[`V17_B_00_INSIGHTS_SEMANTICS.md`](V17_B_00_INSIGHTS_SEMANTICS.md) ·
> [`V17_B_01_INSIGHTS_DOMAIN.md`](V17_B_01_INSIGHTS_DOMAIN.md) · [`V17_A_04_HARDENING.md`](V17_A_04_HARDENING.md) ·
> `HistoryViewModel.kt` / `HistoryRangeSource.kt` / 现有 ViewModel factory 与 lifecycle idiom
> 证据：[`evidence/b-02/`](evidence/b-02/)

**B-02 不重新定义任何 aggregate 数学**：所有计数/分布/剂量/披露语义仍由 B-01
`ReadOnlyMedicationInsightsAggregator` 产生，ViewModel 只做编排。

---

## 1. Architecture（冻结数据链）

```
InsightsViewModel
      ↓  （唯一读缝，与 History 共用）
HistoryRangeSource  ──►  HistoryReadService（组合根装配）
      ↓
HistoricalRange
      ↓
MedicationInsightsAggregator（B-01，pure JVM）
      ↓
MedicationInsightsSummary
```

生产包禁止访问：DAO、Room、`DoseEventRepository`、`MedicationPlanRepository`、
`HistoryReadService`（仅 factory 装配 seam 时引用一次）、matcher、occurrence generator、raw `DoseEvent`
（机器护栏：`InsightsOrchestrationGuardTest`）。**未引入第二个 History reader**
（`B-02 RANGE-SOURCE ARCHITECTURE DECISION REQUIRED` 未触发）。

## 2. Module location

- ViewModel / range model / resolver / lifecycle bridge：`app/src/main/java/.../history/insights/`
  （Phone 层，与既有 History presentation 相邻）；
- 聚合器与 summary 仍在 `experience-core`（pure JVM，B-01 未改）；
- `InsightsSurfaceLifecycle` 是**无渲染**的 lifecycle 接线（无 layout/颜色/文案），B-03 的 screen 直接托管它。

## 3. Range model（typed，无 magic string）

`InsightsRangeSelection`：`Last7Days` / `Last30Days` / `Last90Days` / `CurrentMonth` /
`Custom(startDate, endDate)`，并提供 `isRelativeToToday`。默认 `DEFAULT = Last30Days`（§8）。

## 4. Range resolver（唯一端点计算点）

`InsightsRangeResolver.resolve(selection, today) -> Resolved | Invalid`，pure/deterministic，
两端 **inclusive**，逐字实现 B-00 §19：

| selection | 端点 |
|---|---|
| `Last7Days` | `today − 6 .. today` |
| `Last30Days` | `today − 29 .. today` |
| `Last90Days` | `today − 89 .. today` |
| `CurrentMonth` | 当月 1 日 .. today |
| `Custom` | 原样（合法时） |

源码护栏断言 ViewModel 内**没有** `minusDays(6/29/89)`，端点只能来自 resolver（§5 要求）。

## 5. Custom validation

`startDate <= endDate <= today`；违反时返回 typed `InsightsRangeValidationError`
（`START_AFTER_END` / `END_IN_FUTURE`），ViewModel 进入 `INVALID_RANGE`，
**不发 History query、不 crash、不使用 raw exception string**（§6）。

## 6. State model（单一 `InsightsUiState`）

`selection` · `startDate`/`endDate`（invalid 时为 null）· `today` · `displayZone` ·
`phase`（`LOADING`/`CONTENT`/`EMPTY`/`ERROR`/`INVALID_RANGE`）· `summary: MedicationInsightsSummary?` ·
`failure: InsightsLoadFailure?` · `validationError: InsightsRangeValidationError?`。

ViewModel **不重述**任何 metric 字段（护栏断言 state 内不存在 `recordedIntakeCount` 等同名属性），
summary 直接来自 B-01。

## 7. Initial load

`init { load(selection) }`：默认 `Last30Days` → 恰好 **1 次 read + 1 次 aggregate**（计数测试锁定）。

## 8. Query discipline

| 动作 | reads |
|---|---|
| 首次创建 | 1 |
| 相同 selection 再次选择 | 0 |
| selection 改变 | 1 |
| 相同 endpoints 的 Custom | 0 |
| invalid Custom | 0 |
| retry | 1 |
| surface 首次进入 | 0（初始 load 拥有） |
| surface 再次进入 / 前台返回 | 1 |

recomposition 本身不触发任何 read（keyed effect 只在进入 composition 时触发）。

## 9. Aggregator discipline

一次成功的 read → **恰好一次** `aggregate(range)`（同一 `HistoricalRange` 实例，测试用 `assertSame` 锁定）；
read 失败 → aggregate 调用数不变；invalid Custom → reads 0 且 aggregates 0（§34）。

## 10. Failure taxonomy

`InsightsLoadFailure.ReadFailure(cause)`（普通读取失败）与
`InsightsLoadFailure.ContractViolation(cause)`（B-01 `InsightsContractViolationException`）。
两者都进入 `ERROR` 且保留 cause；**绝不**把契约破坏吞成 empty data，也**不**返回假 summary（§16）。
read 失败时保留 selection 与已解析 endpoints，`retry()` 可重试（§17）。

## 11. Cancellation / generation

`startLoad` 先 `++generation` 再 `cancel()` 旧 job；success、普通 failure、契约 failure 三条写 state 路径
都检查 generation；`CancellationException` 一律 rethrow（§14）。

**supersede vs coalesce（本轮冻结）**：

| 触发 | 语义 |
|---|---|
| `selectRange(newSelection)` | **supersede**：立即取消在途 load 并发起新 load（generation 使旧响应失效）；「一次 selection 改变 = 一次 read」 |
| `retry()` / `onSurfaceShown()` / `onAppForegrounded()` | **coalesce**：若在途则记 `pendingRefresh`，当前 load 完成后**恰好一次** follow-up |

这样 §15 的 stale success / stale failure 回归仍是可达场景（selection 改变即令旧请求过期），
而 §21 的「refresh 不得被丢弃」也成立。

## 12. Pending-refresh coalescing

`pendingRefresh` + `pendingSelection` 两个标志：多个 refresh 请求合并为 **1** 次 follow-up；
若期间发生 selection 改变，则 follow-up 使用**新的** selection（并立即预览新 selection 的 endpoints，不额外读）。
测试锁定：初始 load 在途 + 3 次 refresh → 总 reads **2**（原始 + 1 次 follow-up），最终 summary 来自 follow-up（§22）。

## 13. Surface / foreground refresh

`onSurfaceShown()`：首次进入只置 `surfaceShownOnce`（0 read），之后每次进入刷新当前 selection 一次；
`onAppForegrounded()`：由 `InsightsSurfaceLifecycle` 在**真实** `ON_STOP → ON_START` 后调用（内含
`wentToBackground` 位），因此冷启动的 `ON_START` 不产生第二次 load；observer 随 composition 存在/销毁，
用户不在 Insights 时不会后台轮询。**未新建第三种「screen visible」机制**（§19/§20）。

## 14. Rollover / timezone change

每次 load/refresh 重新取 `displayZone()`、`now`、`today = now.atZone(zone).toLocalDate()`，
然后**一次性**把 `zone`/`now`/新 endpoints 交给 seam（不允许先按旧 zone resolve 再按新 zone read）：

| selection | 跨日 rollover |
|---|---|
| `Last7/30/90` | endpoints 自动滚动（Sep13 Last7 = Sep7..Sep13 → Sep14 = Sep8..Sep14） |
| `CurrentMonth` | 月末 → 次月（Sep30 Sep1..Sep30 → Oct1 Oct1..Oct1） |
| `Custom` | endpoints 固定不变；validation 使用新的 today |
| 时区变化 | `today` 按新 zone 重算，相对 preset 随之重解析 |

## 15. SavedState

保存：selection **类型** + Custom 的两个端点；**不保存** summary/`HistoricalRange`/任何聚合 map（可重建）。
使用现有 `SavedStateHandle` + `CreationExtras` factory 模式，**未**重构全局 ViewModel 基础设施。
恢复规则：相对 preset 用恢复时的 today 重新 resolve；Custom 恢复端点后重新 validation（invalid → `INVALID_RANGE`，0 read）；
enum/日期 malformed → 回落 `Last30Days`，不 crash。

## 16. Explicitly absent（禁止项）

State/ViewModel 不产生任何 percentage / ratio / score / punctuality 字段，也不重算 B-01 指标；
不决定 chart/card/color/icon/本地化文案；range label 用类型（未来 B-03 映射资源）。护栏：
`InsightsOrchestrationGuardTest`（禁用词 + 依赖 + 单一读缝 + resolver 唯一端点来源 + state 不重述 metric）。

## 17. Tests

| 文件 | tests | 覆盖 |
|---|---:|---|
| `InsightsRangeResolverTest` | 12 | 7/30/90/current month 端点（对齐 B-00-R1 示例 `today=2026-09-13`）、跨月、valid/invalid custom、determinism |
| `InsightsViewModelTest` | 34 | 默认与初始 load、端点/zone/now 转发、四种 preset、valid/invalid custom、相同 selection 0 读、改变 1 读、retry 1 读、普通失败、契约失败、empty、stale success/failure、surface 首/再次进入、前台刷新、coalesced pending refresh（含 selection 改变获胜）、Last7 跨日、CurrentMonth 跨月、Custom 固定、时区变化、restore relative/custom、malformed 回落、selection 持久化、不持久化 summary |
| `InsightsViewModelIntegrationTest` | 2 | 真实 B-01 聚合器 + 假 seam：state 与 frozen aggregate 一致（含时间时区披露）＋同一 range 两次聚合结果相同 |
| `InsightsOrchestrationGuardTest` | 5 | 无第二事实路径、读缝唯一、禁用词、state 不重述 metric、resolver 是唯一端点计算点 |
| instrumentation `InsightsSavedStateFactoryTest` | 2 | 真实 `ViewModelProvider` + Activity `CreationExtras`：selection/custom 写入 saved state 并经真实 save/restore 保留；相对 preset 存类型而非冻结端点 |
| instrumentation `InsightsSurfaceLifecycleTest` | 2 | 最小 harness 托管生产 bridge：冷启动 1 read、composition 重入刷新 1 次、真实 `ON_STOP→ON_START` 刷新 1 次 |

## 18. Evidence

`docs/evolune/v1.7/evidence/b-02/`：architecture/source-boundary audit、state/API snapshot、
range resolver golden output、query-count evidence、stale-response evidence、refresh/coalescing evidence、
SavedState evidence、test-isolation incident（§21）、focused JVM XML/log、targeted Android XML/log、
full JVM XML/log、full Phone XML/log、aggregate TSV、`source-diff-stat.txt`、`MANIFEST.sha256`。

| run | 命令 | tests | skipped | failures | errors |
|---|---|---:|---:|---:|---:|
| fresh focused JVM（B-02 四个类） | `:app:testDebugUnitTest --rerun-tasks --tests "…history.insights.*"` | 53 | 0 | 0 | 0 |
| fresh full JVM | `test testDebugUnitTest --rerun-tasks`（app 850 / experience-core 169 / wear 90） | 1109 | 0 | 0 | 0 |
| targeted Android（B-02 两个类） | `:app:connectedDebugAndroidTest -P…class=…insights.*` | 4 | 0 | 0 | 0 |
| full Phone instrumentation（受影响面） | `:app:connectedDebugAndroidTest` | 247 | 5（既有 `assumeTrue`） | 0 | 0 |
| app 构建 | `:app:assembleDebug` | — | — | `BUILD SUCCESSFUL` | — |

计数口径：一切测试数只取自 JUnit XML（`jvm-*.tsv` / `androidtest-aggregate.tsv` 由脚本从 XML 汇总，
不采信日志文本）。`MANIFEST.sha256` 覆盖 evidence 目录全部文件（不含自身）：entry 数 = 磁盘文件数，
`sha256sum -c` 全 OK；提交后另做 self hash 与 HEAD blob 逐条比对（0 mismatch，见 closure report）。
冻结证据（a-01/a-02/a-03*/a-04/b-00*/b-01 目录）本轮 0 改动。

## 19. B-03 handoff

- B-03 只需决定 entry point（tab vs Settings 子页）与 Compose 呈现，**不需要**再碰 range/查询/refresh 逻辑；
- B-03 托管 `InsightsSurfaceLifecycle(viewModel)` 即可获得 tab 返回 / 前台刷新语义；
- 禁用词与 disclosure（B-00 §18/§24）在 B-03 渲染层继续适用；本轮的护栏只覆盖 orchestration 层；
- 本轮**没有**新增 nav destination / bottom tab / Compose screen（§40 遵守）。

## 20. Open debt（不阻塞 B-03）

- B-01 P3 顺延项（focused log/XML pairing、determinism repeat、matched timezone-disclosure test、
  guard robustness、process-incident traceability、identity-key duplication）——本轮已顺带补 determinism repeat
  与 timezone-disclosure 断言，其余仍在 backlog；
- A-04 复审遗留 P2：History active 时后台权威写入不实时刷新（本轮 Insights 已用 pendingRefresh 避免
  *丢弃* refresh，但**不**实现实时订阅；同一限制适用于 Insights）；
- History 的同类 refresh 丢弃边界**未**回改（§21 明确不要求）。

## 21. 附带修复：instrumentation 测试隔离（production singleton reset）

本轮验收要求 fresh 全量 Phone instrumentation。首次执行时出现 3 个**确定性**失败
（`HistoryReadZeroWriteDeviceTest` ×1、`HistoryRefreshDeviceTest` ×2，全部为 A-04 既有测试，
本轮 0 改动），根因为**既有测试隔离缺陷**，与 B-02 生产代码无关：

1. app 每次由 UTP 全新安装时，Android 会向新安装应用补发 package-scoped
   `BOOT_COMPLETED`（`dumpsys activity broadcasts` 中 `pkg=…evolune.debug` 的
   `BroadcastRecord{…BOOT_COMPLETED}`）；`ReminderRescheduleReceiver`（受理
   `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`/`TIME_SET`/`TIMEZONE_CHANGED`）据此在**同一进程**
   的后台线程执行生产工作，经 `ProductionRepositoryProvider.get(...)` 创建
   `AppDatabase.INSTANCE` 与 provider 单例（探针实测：首个测试之前二者已存在且 `isOpen=true`）。
2. `AppDatabaseMigrationMatrixTest` 的每个方法前后调用 `resetProductionDatabaseSingleton()`：
   它关闭并置空 `AppDatabase.INSTANCE`，但**未**清空 `ProductionRepositoryProvider.instance`，
   于是 provider 单例继续持有**已关闭**的 Room 数据库（探针实测：`appDatabase=false` 而
   `provider=true`）。
3. Room 2.8.4 的 `RoomDatabase.close() → onClosed()` 会 `coroutineScope.cancel()`；此后任何
   suspend DAO 调用在已取消的 `SupervisorJob` 上抛出
   `JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelled}`，而不是真实读结果。
   断言真实数据的两个 A-04 测试因此失败；只断言持久化 selection 的 SavedState 测试不受影响
   （读失败被 ViewModel 吸收为错误态）。

**修复（仅测试代码，15 行）**：`resetProductionDatabaseSingleton()` 同时置空
`ProductionRepositoryProvider.instance`，并在 KDoc 中写明上述广播路径。生产代码 0 改动。

**验证**：修复前全量 Phone `247 tests / 5 skipped / 3 failed`，修复后 `247 tests / 5 skipped / 0 failed`
（同一 AVD、同一顺序）。完整证据链见 `evidence/b-02/test-isolation-incident.txt`（含探针时间线、
广播记录、receiver 调用栈、Room 字节码依据）。

**遗留**：`ProductionRepositoryProvider` 无自愈路径（若将来有生产路径关闭该数据库，`get()` 会继续
返回已关闭句柄）。当前无生产路径关闭它（`RoomRestorePersistence` 用事务内替换行，不 close），
故登记为 P3 备忘而非产品缺陷。
