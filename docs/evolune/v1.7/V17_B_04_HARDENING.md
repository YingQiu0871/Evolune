# Evolune v1.7 — B-04 Phase-B Hardening & Release Gate

**轮次**：v1.7-B-04（hardening / release gate，**不新增产品功能**）
**起点**：`3b0ef751ba5a17fd48f14877960375779e35b4b6`（B-03 APPROVE）
**冻结语义**：B-00（措辞/披露/禁用词）、B-01（`MedicationInsightsSummary`）、B-02-R1（编排/refresh/快照）、B-03（已批准 UI 语义与区块）。**本轮未重新解释任何冻结语义**；未触发任何 stop verdict（无语义重开、无新 data contract、无 release blocker）。

---

## 1. Scope

允许并已做：accessibility/semantics 修复、测试与证据补全、release gate、回归中发现的窄口径 bug 修复。
禁止且未做：新指标/百分比/timing、新 chart data contract、新 chart 依赖、新导航目的地、新 Room schema、新 DAO/query path、新 identity 语义、Phase C 工作。

## 2. Accessibility P2 关闭（B-03 review finding）

**问题**：metric 行原先在父 `Row`/`Card` 上设 `contentDescription`，同时又保留子 `Text`（label/value）→ non-merging semantics 下 TalkBack 可能重复朗读。

**修复（A-03 先例）**：对四类 metric 载体统一改用
```kotlin
Modifier.clearAndSetSemantics { contentDescription = <localized "label：value"> }
```
覆盖：overview 计数卡、coverage/source/confidence 计数行、dose 行（`insights_value_row_description`，value 为已格式化的 mg 文本）、unknown identity 计数行。合并树（TalkBack 遍历的那棵树）每行**恰好一个**节点，子 label/value 不再单独出现。

## 3. Semantics tree regression（device，live）

`InsightsScreenTest.everyMetricRowExposesExactlyOneSemanticFact` 对四类行逐一断言：
1. 合并树中该 tag **恰好 1 个**节点；
2. 该节点的 `contentDescription` == 本地化 `label：value`；
3. 合并树中**不存在**单独的 label 节点与单独的 value 节点（重复朗读的直接反证）。

辅助：`insights-unknown-identity` 同样只以合并事实暴露（原 `onNodeWithText` 断言随之改为 contentDescription 断言）。

## 4. Resource precision

`insights_bar_description` 已不专属 bar/chart → 重命名为 **`insights_count_row_description`**（`%1$s：%2$d`），并新增 **`insights_value_row_description`**（`%1$s：%2$s`，用于 mg 文本行）。EN/default 与 zh-rCN 两个文件同步；测试引用同步。用户语义不变。旧 B-03 证据保持冻结、不回写（差异在 B-04 证据与本文件记录）。

## 5. Custom range picker round-trip（device）

- `InsightsReleaseGateTest.theCustomRangeRoundTripKeepsTheIntendedDates`：进入 Insights（默认 Last30）→ 记录 heading → 点 Custom → `insights-custom-range-picker` 出现 → 确认 → 对话框关闭、Custom chip selected、**heading 与确认前逐字相同**（证明 picker millis ↔ `LocalDate` 往返无 ±1 天漂移）。
- picker 的 UTC 语义集中在 `InsightsDatePicker`（`toUtcMillis`/`toLocalDate`/`fromSelection`），**不使用 system timezone 解码**。

## 6. Date round-trip（JVM，locale/DST 覆盖）

`InsightsHardeningTest.picker millis round-trip every season and never consult the system zone`：在 `TimeZone.setDefault("Europe/Paris")` 下覆盖冬季 CET、夏季 CEST、春/秋 DST 切换日、年末/年初，断言 `toLocalDate(toUtcMillis(D)) == D`，并断言 millis 等于 UTC 午夜（非本地午夜）。

## 7. Real navigation / lifecycle proof（split-proof，如实声明）

| 层 | 证据 | 能证明什么 |
|---|---|---|
| 真实产品路径（device） | `InsightsNavigationTest`（History→Insights→back→History、离开再进入不崩溃、bottom bar 在 destination 内隐藏） | 导航/组合真实可用，无第六 tab |
| 生产 bridge + 计数 seam（device harness） | `InsightsSurfaceLifecycleTest`（冷启动 1 read；composition 重入 +1；真实 ON_STOP→ON_START +1；**disposed 后 lifecycle 迁移 0 read**） | 计数级 lifecycle 契约（observer 真随 composition dispose） |
| 编排计数（JVM） | `InsightsViewModelTest`（initial 1、range change +1、same-selection 0、refresh coalesce 2 等） | 精确 read 计数 |

**诚实边界**：生产 `MainActivity → AppNavigation → InsightsRoute` 的 read **计数**无法在不改生产 wiring 的前提下直接注入计数器；因此本轮的 device 路径只证明 UI/lifecycle 行为，read 计数由 harness（真实 bridge + 计数 seam）与 JVM 计数测试证明 —— 不伪称 device 直接计数。

## 8. Lazy ViewModel / startup gate（device）

`InsightsReleaseGateTest.theInsightsViewModelIsCreatedLazilyOnFirstEntry`：app 启动后 `activity.viewModelStore.keys()` 中**没有** Insights ViewModel；切到 History 仍没有；首次进入 Insights 后恰好 1 个。→ Insights 不在启动时创建、不产生启动期 Insights 查询。

## 9. Activity-scope retention（device）

`theSelectedRangeSurvivesLeavingAndReEnteringInsights`：进入 → 选 Last7 → back → 再进入 → Last7 **仍 selected**，且 ViewModel 实例仍为 1（Activity 作用域）。→ 不会退回 Last30，surface-return refresh 生效（计数证明见 §7）。

## 10. Rotation / SavedState（device）

`aConfigurationChangeKeepsTheSelectionAndReloadsTheSurface`：选 Last7 → `scenario.recreate()` → `insights-screen` 存在且 Last7 仍 selected（SavedState 选择恢复；无 crash、无重复 observer —— observer 随旧 composition dispose）。

## 11. Large font（device，强化）

`largeFontScaleKeepsTheSurfaceReadable`（1.5x）：range selector、默认 chip、overview 卡均可见；卡片仍暴露完整 metric 语义（值不被省略）；`theRangeSelectorStaysOperableAtLargeFontScale` 证明 1.5x 下点击 chip 仍到达 ViewModel（intent 可发）。

## 12. Localization parity（程序化，JVM）

`InsightsHardeningTest.both shipped locales expose the same Insights keys with compatible format arguments`：两文件 `insights_*` key 集合**完全相等**（≥40），每个 key 的 `%n$s`/`%n$d` 占位符序列一致；另有专项断言 `insights_count_row_description` = `%1$s,%2$d`、`insights_value_row_description` = `%1$s,%2$s`。

## 13. Forbidden-language audit（强化）

- `InsightsUiGuardTest` 现覆盖：**源码标识符**（去注释后的 UI 代码里禁止 adherence/compliance/completionRate/missedDose/skippedDose/onTimeRate/lateRate/timingDifference/averageDelay/percentage/percent/ratio）、**用户可见资源值**（英/中禁用词）、**中文禁用词**（漏服/依从率/准时率/延迟/完成率/合规）、禁止 blame 词命名 unrecorded。
- **语言策略如实说明**：`values/strings.xml` 本身就是中文默认，`values-zh-rCN` 亦为中文；因此英文 token guard 价值有限，中文 token guard 与本轮新增的 parity/identifier guard 才是主防线（该事实由 guard 测试自身断言，写入证据）。

## 14. No-chart guard（回归）

`InsightsUiGuardTest` 新增：UI 包内无 `Canvas(`/`drawArc`/`drawPath`/`drawScope`/`Path(`；`libs.versions.toml` 与 `app/build.gradle.kts` 未声明任何 chart 库（vico/mpandroidchart/compose-charts/koalaplot/charts）；presentation 层不读 `HistoricalRange`、不读 `.days`（即不派生逐日 series）。Phase B 的「no daily chart shipped」状态被锁定。

## 15. Metric truth audit（Phase B 全量）

证据 `metric-truth-map.txt`：每个 UI 可见值 → `InsightsPresentation` 字段 → `MedicationInsightsSummary` 字段的一一映射（程序化生成 + 人工核对），并列出 UI 包中 `groupBy`/`sumOf`/`mapValues` 等重算原语为 0。UI 不产生任何新事实。

## 16. Presentation invariants

`InsightsHardeningTest`：六来源恰好六个、四档 confidence 恰好四个（0 值保留）、dose 行只含 frozen identity key、不产生跨药物合计、unknown 计数在 dose 列表之外、coverage 两句披露恒同现、timezone 披露仅在 flag=true。

## 17. State matrix（device + JVM 双层）

- device：`InsightsScreenTest.theStateMatrixKeepsOnlyTheAllowedNodesPerPhase` 在同一 composition 内切换 5 个 phase，逐相断言 selector/sections/retry/validation/empty/loading 的出现与**陈旧内容缺席**。
- JVM：`InsightsHardeningTest.the render matrix is locked for every phase` 断言各 phase 的 model 只暴露允许字段。

## 18. Golden 用例（Phase B 关键语义）

| golden | 断言 |
|---|---|
| unrecorded-only | CONTENT、计数 0、unlinked 计数与两句披露可见、**EMPTY 文案缺席**（JVM + device） |
| unknown identity | 仅计数句；dose 区无 `mg`、无 `E2`/`EV`/`CPA`/`spironolactone`/`antiandrogen`（device） |
| coverage | 只显示两个计数；无 `%`、无 `N/M`、无 progress/ring（device） |

## 19. Zero-write release gate（device）

`InsightsZeroWriteDeviceTest`：用计数装饰器包裹**生产 Room 仓库**，经真实 `HistoryReadService` 驱动 `InsightsViewModel` 的 initial / range change / surface return / foreground / retry 五种 intent，断言 `dose_events` 与 `plans` 的 writes 均为 **0** 且 reads 增加、最终 phase 为成功的 CONTENT/EMPTY、`failure == null`。

## 20. Room / schema boundary

`source-diff-stat.txt`：`app/schemas`、`app/src/main/.../data`、DAO/query path 0 改动；本轮未新建 index；`dose_events.localDate` index 仍为 **DEFERRED**（A-04 决策不变）。

## 21. Performance sanity

`InsightsHardeningTest.a ninety day range with several hundred intakes stays linear and cheap to present`：500 recorded intakes / 90 天 / 六来源 / 五 key dose 的 aggregate，presentation 只做固定尺寸映射（6/4/5 行），耗时断言 < 250 ms（实测远低于此），无逐 entry 遍历。

## 22. Carry-over disposition（B-02 P3）

| 项 | 处置 |
|---|---|
| invalid-supersede dedicated test | **已关闭**：`a superseding selection that is invalid leaves no refresh latch behind`（0 额外 read、被取代的读不入聚合、后续 refresh 仍 0 read） |
| new-refresh-after-selection summary discriminator | **已关闭**：`a preserved refresh publishes the follow-up summary of the new selection`（断言 follow-up 的 summary 胜出） |
| custom-specific +1 read assertion | **已关闭**：`a new custom selection performs exactly one read`（+1 且相同 custom 再选 0 read） |
| assemble fresh/APK hash | backlog（本轮仍 build-green，见 §25） |
| provider self-heal | backlog（无生产 close 路径；不因 release gate 重构 provider） |

## 23. A-04 carry-over P2（维持登记）

「surface active 时后台权威写入不实时订阅刷新」仍登记；Phase B **未**新增 Flow/subscription。以下三条路径均可靠并有证据：return-to-surface refresh（device + JVM）、foreground refresh（device + JVM）、pending refresh 不丢失（JVM：success/ReadFailure/ContractViolation 三种完成各恰好一次 follow-up）。

## 24. History / navigation 回归

- `InsightsReleaseGateTest.theHistoryEntryDoesNotBreakTheCalendarOrTheScroll`：入口卡与 month title/calendar 同现，`history-content-list` 仍可滚动到 calendar 与入口卡（入口未抢焦点、未破坏滚动、未覆盖日历）。
- `everyPrimaryTabStillOpensAndInsightsIsNotOneOfThem`：五个主 tab 仍可打开；不存在 `nav-bar-insights`（Insights **不是** bottom tab）；History→Insights→back 后 History 仍选中且底部栏未被污染。
- 全量 Phone 运行同时覆盖既有 `HistoryNavigationTest`/`HistoryScreenTest`。

## 25. Verification（fresh）

| 运行 | 结果 |
|---|---|
| focused JVM（B-01/B-02/B-03/B-04 + init 回归） | **105 tests / 0 failures / 8 XML**（`--rerun-tasks`，28/28 executed） |
| full JVM | **app 902 + experience-core 169 + wear 90 = 1161**，0/0/0，54/54 executed（相对 B-03 的 1142 **+19** = 本轮 JVM 测试） |
| targeted Android（SavedState 2 + SurfaceLifecycle 3 + ScreenTest 18 + Nav 2 + ReleaseGate 7 + ZeroWrite 1） | **34 tests / 0 failures**（device `Pixel_7(AVD) - 15`） |
| full Phone | **277 tests / 5 skipped（既有 assumeTrue，逐项见证据）/ 0 failures**（相对 B-03 的 263 **+14**） |
| `:app:assembleDebug` | `BUILD SUCCESSFUL`；若 38/38 UP-TO-DATE → 只称 **build-green** |

## 26. Evidence

`docs/evolune/v1.7/evidence/b-04/`：a11y semantics before/after、custom picker round-trip、date round-trip、navigation/lifecycle split-proof、lazy VM/query-count、state matrix、golden 回归、zero-write proof、localization parity、forbidden-language audit、metric truth map、acceptance mapping、source/diff boundary、focused/full JVM、targeted/full Phone、assemble、`MANIFEST.sha256`。旧 evidence（B-02/B-02-R1/B-03 等）**0 改动**。

## 27. Release gate 结论与遗留

Phase B 的 acceptance 项全部有「实现 → 测试 → 证据」三元映射（见 `acceptance-mapping.txt`），无 "tested somewhere" 模糊态。遗留（不阻塞 Phase B 关闭）：assemble fresh/APK hash 证据、provider self-heal、A-04 P2 实时订阅、逐日 series/chart contract（属 B-04 之后的明确新 contract）、产品决策项（timing/coverage 百分比 UI/anti-androgen 投影字段）。
