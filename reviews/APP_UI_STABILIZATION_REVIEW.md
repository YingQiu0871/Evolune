# Evolune App UI Stabilization Independent Review

Date: 2026-08-10
Reviewer: DeepSeek (independent read-only)
Worktree: `D:\Evolune-plan-ui-fix`
Branch: `phase1/medication-plan-ui-sizing-fix`
Baseline tag: `phase-1-plan-save-regression-fix`

## Executive summary

- **Final decision: APPROVE WITH P2**
- P0 / P1 / P2 = **0 / 0 / 4**（全部为非阻断的报告/验证边界项；无代码缺陷）
- **UI stabilization 通过**（代码与自动证据层面；用户视觉验收声明见下）
- **UI sizing increment 通过**（option grid + action row + date/time，见对应 verdict）
- **Samsung 用户视觉验收**：报告 §1/§17 明确记录**用户本人**在 SM-S918B 上使用 Samsung Keyboard 完成最终复验并确认全部视觉项正常。**本审阅时真机离线（R5CW21W4THE 未连接），无法独立复验该陈述**——按任务 §29 接受"用户陈述必须来自用户明确陈述"：该陈述由报告如实记录（区分了自动证据与用户陈述），审阅不擅自替用户声明。**该验收声明被接受为记录在案的证据，但标注为不可独立复验项**
- **允许 commit implementation**：是（P2 不阻止）
- **允许 commit review**：是
- **允许创建 UI stabilization tag**：是
- **允许合并回 `phase1/batch7-design`**：是
- **允许随后进行 Batch 7 Design independent review**：是
- **Batch 7 implementation 仍禁止**；**Widget / Custom medication 仍禁止**；**Room v3 仍不可发布**

## Git and scope verdict

- 分支正确；`phase-1-plan-save-regression-fix` 为祖先（exit 0）✓
- 暂存区为空；无 commit/tag；`stash@{0}` 原样（未 apply/pop/drop）✓
- 实际状态（非实施方宣称）：**17 modified + 13 untracked**。modified = 生产 13 + 测试 2 + Gradle 2；untracked = 新组件 2 + 测试 5（含 androidTest manifest/res/testime）+ 报告 3 + ThemeTest 1
- 所有变化可解释（本轮 UI stabilization + 本审阅会话的既有探针调整——其中 2 个早期探针文件已被实施方删除且无引用残留）✓
- 无 APK/录屏/截图/log/dump/数据库/keystore/真实数据在 change set 中（报告 §12 提及的 Samsung 视频仅作文本描述，文件不在工作树）✓
- 审阅期间注意：**AVD 端口曾被并行实施过程重分配**（emulator-5558 变为 Wear AVD）——早期一次 focused 运行因此误跑在 Wear 上产生假失败；在正确 phone 端口（API33=5556、API35=5560）重跑后全绿。已作为方法论记录，非产品缺陷

## File hygiene verdict

- untracked 全部为：正式测试（ProbeTestIme/testime/RealAppImeFrameProbeTest/ColorRoleConformanceTest/FoldableNavigationLayoutTest/ThemeTest）、androidTest 必需资源（AndroidManifest + probe_test_ime.xml）、最终报告（3 份）✓
- 无临时调试脚本/产物/敏感文件 ✓
- **P2-F1**：早期探针文件（ImeGeometryProbeTest/RealAppImeProbeTest）被删除——已确认无引用残留、无文档引用，卫生无问题；删除本身合理（被更严格的 RealAppImeFrameProbeTest 取代）

## Custom-route rollback verdict

- 全仓 grep `Route.CUSTOM|participatesInPk|customRoute|placeholder`：**零命中**（排除普通语义字符串后）✓
- `pk/Route.kt`：枚举仅 7 个既有 route（无 CUSTOM）✓
- `git diff pk/ core/ data/`（除 SettingsDataStore AMOLED 一行外）：**全空**——PK 算法/参数、JSON、Widget、Wear、DAO/Entity/Room 零变化 ✓
- Custom medication 仅以 deferred requirement 形式记录（报告 §21.2：药物身份、非 Route.CUSTOM、未来独立 Domain/persistence/JSON/Wear 设计、不允许 fake E2 占位）✓

## Option-grid verdict

`MedicationOptionGrid.kt`（62 行）：

- BoxWithConstraints：`maxWidth >= 600.dp → expandedColumns`，否则 compactColumns（调用点：竖屏两列、宽屏三列）✓
- `FilterChip` + `weight(1f)` 同行等宽 ✓；固定 `height(56.dp)`（selected/unselected 同 bounds，无 expressive width 动画——FilterChip 无宽度动画）✓
- `maxLines = 2` + `TextAlign.Center` + `overflow = Clip`——长中文最多两行居中，不逐字竖排 ✓
- 8.dp 行/列间距（MD3 chip 间距）✓；56dp touch target ≥48dp ✓；无 hardcoded device width ✓
- `MedicationPlanBottomSheet` 4 处调用（route/ester/schedule/antiandrogen 及舌下）统一使用 ✓
- 测试：`portraitOptionGridKeepsRouteSizesStableAcrossSelection`（API35 phone 独立运行通过）——**bounds 稳定性断言**（测量而非存在性）✓

## Editor-action-row verdict

`MedicationEditorActionRow.kt`（95 行）：

- 删除/取消/保存：`weight(1f)` 等宽 + `height(56.dp)` 固定 ✓
- 删除：`contentColor = error`（destructive 仅颜色 role，几何不变）+ 18dp icon + `maxLines=1, softWrap=false, overflow=Clip`（不 wrap）✓
- enabled/disabled 不改变尺寸（固定 height）✓；56dp touch target ✓；8dp spacing ✓
- Plan 与 Record 两编辑器均调用同一实现（`MedicationPlanBottomSheet.kt:287`、`MedicationRecordBottomSheet.kt:339`）✓
- 测试：`editActionsHaveEqualStableSizes`（两编辑器，API35 独立运行通过）✓

## Fullscreen-editor verdict

- 两编辑器为 `Surface(fillMaxSize, background)` 全屏（无圆角 sheet、无 dragHandle）✓
- editor 打开时 bottom bar/rail 隐藏（`navigationVisible` 条件）+ 覆盖 ✓
- Back/Cancel/Save/Delete 语义不变（BackHandler + 回调保持）✓
- edit session 仍由 ViewModel 持有（AppNavigation overlay 渲染，非 navigation destination）——**composition 重建不丢 session**（session 在 VM StateFlow）✓
- validation failure 保持 editor（结构化错误显示）；save success 关闭（uiEvents/operationState 收集在 AppNavigation）✓
- **ownership 检查**：uiEvents 收集单处（AppNavigation）、通知权限 launcher 单处、无重复消费/无事件泄漏（LaunchedEffect 随 AppNavigation 生命周期）✓；recordDefaults 状态在 AppNavigation（无 stale）✓
- 配置/自适应变化：editor 状态在 VM，尺寸类切换不重建 ✓

## IME architecture verdict

- 最终实现（当前 production）：**纯框架路径**——`windowInsetsPadding(WindowInsets.safeDrawing)` + `verticalScroll` + 框架 BringIntoView；**无手工 scroll sync**（实施方删除了此前引入的 `snapshotFlow{ime}+animateScrollTo`，注释明确其与框架滚动并发排队产生二次缓动=真机回弹）✓
- 该中间版在 Samsung Keyboard 上曾被真机否决（报告 §7 如实记录）→ 撤回后用户最终 PASS——**中间失败与最终状态的证据链完整、诚实** ✓
- 无 BringIntoViewRequester/imeNestedScroll/imePadding 叠加；单一 insets owner（safeDrawing）✓
- **报告包含**：用户本人最终确认无肉眼弹跳（Samsung Keyboard、多次 focus/open/close、输入过程）——记录于 §1/§17 ✓
- 自动证据：RealAppImeFrameProbeTest 双 phone 独立运行 `bouncingCycles=0/5`（dirChanges 全 0、motionPhases 全 1、lateScroll 全 -1）✓（确定性 ProbeTestIme，非 Samsung Keyboard——报告未将其冒充用户验收 ✓）

## IME test-quality verdict

`RealAppImeFrameProbeTest`（386 行）独立审阅：

- **ProbeTestIme**：androidTest APK 内确定性 IME（450dp 固定高、`onEvaluateFullscreenMode=false`）——只在测试 APK，不进生产 ✓；`@Before/@After` 经 `ime enable/set` 切换并还原原 IME ✓
- **空洞通过防护（历史上最重要风险）**：`occludingCycles > 0` 强制——IME 未真正遮挡字段时以 INCONCLUSIVE 失败而非通过；我的 API33/API35 独立运行中该防护**正确触发过失败**（一次误跑 Wear AVD 时）——防护真实有效 ✓
- baseline：先 `setImeVisible(false)` 采样静止再测 baseline（修复"baseline 污染"）✓
- `sampleUntilStill(requireImeState)`：IME 与 field 双静止才退出（防"从未开始"误判）✓；`dirChanges/motionPhases/lateScrollMs/endFieldBottom` 判定覆盖：方向反转、多阶段、IME 停稳后滚动、终态遮挡 ✓
- 采样 ~40-70ms/帧（非真 60fps）——**报告明确自认**该局限，并明确"ProbeTestIme 结果不能替代 Samsung Keyboard 用户验收" ✓
- 已知环境依赖：ProbeTestIme 显示依赖 AVD/IME 状态（在部分环境不可用）——测试以 INCONCLUSIVE 失败而非假通过，属正确行为（P2 测试环境依赖，见 F3）

## Samsung user-acceptance verdict

- 报告 §1/§17/§19 记录：用户本人（SM-S918B、Samsung Keyboard、真实 production editor）完成最终复验并确认 IME 弹跳及全部视觉项正常——**12/12 完成标准含"用户本人视觉验收 PASS"**
- 本审阅时真机离线：**该陈述无法独立复验**；审阅接受其作为实施方记录的明确用户陈述（报告未混淆自动证据与用户陈述，证据等级自述正确）
- **不批准代替用户声明**：审阅不将任何自动测试描述为"用户确认"

## Dose-field verdict

- label 无 `(mg)`：`record_sheet_dose_label="剂量"`、`dose_label_with_ester="%1$s 剂量"`、`e2_equivalent_label="等效 E2"`、`plan_sheet_dose_label="剂量"`（en/zh 同步）✓
- suffix `mg` 单一单位源（raw/E2/plan 三字段）✓
- E2 等效字段**无自定义 focusedContainerColor**（此前半透明 cutout patch 已移除——`DoseInputSection` 现用默认 M3 outlined container）✓；无 `Color.White` 手工 cutout ✓
- 测试：`doseLabelsAndFieldsStayFixedAcrossFocusChanges`（focused/unfocused 几何稳定，API35 独立运行通过）✓
- fontScale：maxLines/单行 + suffix 不裁切（trailingIcon 独立槽位）✓

## Date/time verdict

- `DateTimeSection`：BoxWithConstraints + **rememberTextMeasurer 实测文本宽度**（`widestTextPx + cardOverhead <= (available − spacing)/2`）→ 放得下同行、放不下纵向堆叠——**内容驱动判定，无固定 breakpoint 跳变风险** ✓
- 报告 §6 真机生产 UI dump：date `[200,467][429,516]` / time `[693,467][806,516]`——同行、top/bottom 一致、不换行 ✓（该证据为真实生产 UI dump，非 instrumentation）
- `2026-12-31` 单行：文本测量含完整日期串 ✓

## Settings bottom-surface verdict

- Fix B 根因：嵌套 Scaffold inset 重复累加（`Modifier.padding(innerPadding)` 只布局位移不消费；内层再取 navigationBars → 底部多一条容器色条带）→ 修复为内层 `consumeWindowInsets(innerPadding)` + `contentWindowInsets` 单一来源 ✓（**非同色 Box 遮盖**——inset ownership 修正）✓
- 配套：MainActivity window 背景主题化（SideEffect `setBackgroundDrawable(background)`）+ 根 Surface + NavHost background——底部手势区/状态栏区无浅色 window 背景暴露 ✓
- 本审阅真机截图验证（此前会话）：dark 下底部像素 23,25,31（surface 同色）✓
- 报告 §19 完成标准 11（Samsung 真机验收 PASS）涵盖该项 ✓

## Material3 color-role verdict

- `settingsListItemColors()`：未选 `secondaryContainer → onSecondaryContainer`（headline/supporting/leading/trailing 全配对）；选中 `primaryContainer → onPrimaryContainer`（含 selected* 全套）——**精确 semantic pairing**（修复"selected 文字过淡"）✓
- TopAppBar（Settings/Home 共享）：`primaryContainer` 容器 ↔ `onPrimaryContainer` 标题/图标/action ✓；Home `showTopBar` 条件化 insets ✓
- RadioButton：默认 primary 自动 ✓；NavigationBar/Rail：默认 surfaceContainer/自动选中指示 ✓
- **hardcoded color 审计**：`Color.White/Black/Gray/DarkGray` 生产代码零命中（TablerIcons 的 Color.Black 为 VectorImage stroke，经 Icon tint 覆盖，且该文件未被本轮修改——核实为合法）✓；`copy(alpha)` 命中均为主题色派生（合法）✓
- AMOLED themeMode：`Theme.kt` 新增纯黑 scheme（黑 surface + 保留 semantic 前景色——ThemeTest 断言）✓
- `ColorRoleConformanceTest`（本审阅独立运行）：light/dark/AMOLED 标题对比度全部 ≥2.5 ✓

## Selected-settings-content verdict

- selected headline 不再用 onSurfaceVariant（现 onPrimaryContainer）✓；supporting 同样配对 ✓；无整体 alpha 降低；disabled 未误用于 selected（enabled 状态独立）✓
- 用户验收记录覆盖此项（§1 "Settings 已选中项文字"）✓

## Navigation root-surface verdict

- `MaterialTheme → Surface(fillMaxSize, background)`（MainActivity 根）→ adaptive shell → `NavHost(background(surface))`——root 至 destination 全程不透明 ✓
- 无自定义 Surface 套娃（两个根 Surface 均必要：window 覆盖 + NavHost 过渡底）✓
- 状态栏/导航区始终被绘制（window 背景主题化 + 根 Surface）✓
- 本审阅此前帧扫描：过渡帧顶部/左/右近白比例 0.000 ✓

## Navigation motion verdict

- 过渡 = `fadeIn+scaleIn(0.98)` / `fadeOut`（tween）——**无方向依赖**（无 initialState/targetState 下标比较、无外部 mutable tab index——此前方向错乱根因已移除）✓
- 弹簧参数统一（报告 §8）；fade 过渡天然可中断、无堆叠状态 ✓；无 SizeTransform 暴露空白 ✓
- 快速切页：nav item 点击节流（200ms）+ fade 可中断 ✓
- `FoldableNavigationLayoutTest`：24 帧逐帧单标题断言（`mainClock.autoAdvance=false` + `advanceTimeByFrame`——**真逐帧，非静态终态**）✓ 误差 ≤1px 依据 semantics bounds 浮点（可信）✓

## Rapid-switch verdict

- 无状态方向推导（fade 无方向）——快速点击不会算错方向 ✓；点击节流防连击 ✓
- 本审阅未独立执行高频连点专项（报告 §19 覆盖；focused 套件含 nav 相关测试均过）——记录为未独立复验项（P2-F3）

## Title-stability verdict

- 共享 `CenterAlignedTopAppBar`：单一 `Text`（`app-top-title`，titleLarge/onSurface），无 Crossfade 双叠 ✓；action 槽位固定 48dp（Home 显示刷新按钮、其他页空槽）→ 标题不因 action 宽度横向跳 ✓
- 24 帧测试：每帧恰 1 个标题节点 + 居中 ≤1px ✓

## Adaptive-navigation verdict

- `calculateWindowSizeClass(activity)`：Compact → NavigationBar；Medium/Expanded → NavigationRail（`widthSizeClass != Compact`）✓
- 同一 NavController（Bar/Rail 共用）；尺寸类变化仅切换容器——导航栈/destination/screen state/selected state 保持（无重建）✓
- Rail 不遮 content（`alignWithNavigationRail` 顶栏偏移 + rail 独立布局区）✓；editor 打开时 bar/rail 隐藏 ✓
- **依赖门**：`material3-window-size-class` 经 Compose BOM 统一（libs.versions.toml +1、build.gradle.kts +1，无独立版本）——报告 §9 记录先停止实现、论证必要性/替代方案、**获用户批准后落地** ✓
- 矩阵：报告 §16 记录 360/412dp → Bar；600/720/800/1000dp → Rail（实时 resize 实测）；FoldableNavigationLayoutTest 按实际 `screenWidthDp` 自适应断言（非硬编码断点）✓
- 本审阅独立运行（phone compact 设备）：adaptive 测试的 rail 分支 assumeTrue skip、bar 分支 PASS ✓

## Material3 conformance verdict

- 组件全部 M3（FilterChip/OutlinedTextField/Button/OutlinedButton/NavigationBar/Rail/CenterAlignedTopAppBar/ListItem/RadioButton）✓
- 形状：FilterChip/ListItem 用 `MaterialTheme.shapes.medium`/token——**无全 App 单一 magic corner**（"统一圆角"按 component family 一致实现）✓
- spacing/typography/semantic colors/48dp+ touch：见各 verdict ✓

## Plan-save regression verdict

- `MedicationPlanEditSessionFactory.createNew()` 的 `truncatedTo(MILLIS)` canonicalization 仍在（未触碰）✓
- exact-scenario 测试（PlanSaveRegressionTest）保持且此前 6B 全绿；本轮未改 Domain/Repository/ViewModel 保存路径（editor relocation 仅 UI 层）✓
- JVM 364（含 Editor/ViewModel 套件）全绿 ✓

## Forbidden-boundary verdict

- `git diff pk/ core/ data/`（除 AMOLED 一行）：空 ✓；DAO/Entity/Room schema/MIGRATION_2_3/Wear/JSON/Widget：零 diff ✓
- 无 Route.CUSTOM 残留 ✓；Gradle 仅 window-size-class 两行 ✓

## Widget-deferred verdict

- Widget 需求（Material You 动态取色、透明度）仅记录于报告 §18/§20；**无实现**（widget 包零 diff）✓

## Custom-medication-deferred verdict

- 报告 §21.2 准确：未来"其他药物"= medication identity（如微粉化黄体酮），非 Route.CUSTOM；不允 fake Ester.E2 占位；后续独立 Domain/persistence/JSON/Wear 设计；本轮无实现 ✓

## Automated-validation verdict

独立执行（正确 phone 端口）：

| 项 | 结果 |
|---|---|
| `:app:testDebugUnitTest --rerun-tasks` | **43 suites / 364 / 0 / 0 / 0**（XML 累加）|
| `:app:assembleDebug` | PASS |
| `:app:lintDebug` | PASS（0 errors）|
| API 35（5560 Pixel_7）focused：Plans+Records+ColorRole+FrameProbe+Foldable | **全 PASS**（FrameProbe：imeOpened 5/5、occluding 5/5、bouncing 0/5）|
| API 33（5556 Evolune_API33）focused：同上 | **全 PASS**（FrameProbe：5/5、5/5、0/5）|

- 误跑 Wear AVD 的一次失败已查明为设备端口重分配（非产品问题）；正确设备复跑全绿
- **未独立运行**：双 phone 全量 connected 114、折叠屏（Pixel_10_Pro_Fold API37）25、PK 49 单独套件、ksp/zipalign——如实记录未运行

## Report-accuracy verdict

- `APP_UI_STABILIZATION_REPORT.md`（444 行）：证据等级自述正确；历史失败/中间状态/自身工具缺陷（§12-14）如实保留；**用户验收与自动证据明确区分**；Custom/Widget/deferred 边界准确；依赖 gate 记录完整
- **P2-F2（报告内部不一致）**：§3 变更面（645/361、10 生产文件）为早期快照，与当前实际 diffstat（1209/810、13 生产文件）不符——后续增量（§21/§22）未回填 §3
- **P2-F4（报告重复）**：`MEDICATION_PLAN_UI_SIZING_FIX_REPORT.md` 与 `APP_UI_STABILIZATION_REPORT.md` §21 为同一增量；`MEDICATION_EDITOR_UI_POLISH_REPORT.md` 为有效历史（含 correction/修复记录）——三份并存可能误导未来读者
- 报告未将 ProbeTestIme/emulator 冒充 Samsung Keyboard/用户验收 ✓；§16 声称的 114/0/2（双 phone 全量）与我的 focused 结果一致方向，但**全量 114 未独立复验**（记录为边界，不构成虚假声称——报告数字与 focused 绿一致且框架诚实）

## Findings

### P0
None.

### P1
None.

### P2

**F1 — 用户视觉验收陈述无法独立复验（验证边界）**
- Severity: P2
- 文件: `docs/phase-reports/APP_UI_STABILIZATION_REPORT.md` §1/§17/§19
- 问题: 审阅时 Samsung 真机离线（R5CW21W4THE 未连接），"用户本人确认"无法由审阅者复验；陈述来自实施方记录
- 影响: 无代码影响；验收证据等级受环境限制
- 依据: 报告如实区分用户陈述与自动证据；无冒充
- 最小修复建议: 用户后续可随时复核（装 APK 即验）；无需改代码
- 是否阻止封存: 否

**F2 — 报告 §3 变更面为过时快照**
- Severity: P2
- 文件: `APP_UI_STABILIZATION_REPORT.md` §3
- 问题: 645/361、10 生产文件的统计早于 §21/§22 增量；当前实际为 1209/810、13 生产文件
- 影响: 未来读者可能依据旧统计
- 最小修复建议: 提交前回填 §3 为最终 diffstat
- 是否阻止封存: 否

**F3 — 全量 connected 与折叠屏矩阵未独立复验**
- Severity: P2
- 文件: 验证边界
- 问题: 双 phone 全量 114 与折叠屏 25（Pixel_10_Pro_Fold API37）未在本审阅独立执行（focused 覆盖全部高风险项且全绿）
- 影响: 无代码影响；报告 §16 数字与 focused 结果方向一致
- 最小修复建议: 提交前可补跑全量（或接受 focused 覆盖）
- 是否阻止封存: 否

**F4 — 三份报告并存且部分重复**
- Severity: P2
- 文件: `MEDICATION_PLAN_UI_SIZING_FIX_REPORT.md` 与 `APP_UI_STABILIZATION_REPORT.md` §21
- 问题: sizing 报告与 stabilization 报告 §21 为同一增量；`MEDICATION_EDITOR_UI_POLISH_REPORT.md` 为有效历史
- 影响: 文档导航成本
- 最小修复建议: 提交时可合并/交叉引用
- 是否阻止封存: 否

## Independent validation executed

- **完整读取**：3 份报告（444/168/151 行）、MedicationOptionGrid、MedicationEditorActionRow、MedicationRecordBottomSheet（IME 区域/Dose/DateTime/action）、MedicationPlanBottomSheet（调用点）、AppNavigation（adaptive/过渡/标题/rail/bar）、FoldableNavigationLayoutTest、RealAppImeFrameProbeTest、ProbeTestIme、androidTest AndroidManifest + probe_test_ime.xml、ColorRoleConformanceTest、ThemeTest、Theme/SettingsDataStore/HomeScreen/SettingsScreen 的 diff、Route/Ester 枚举、基线上限
- **git/static audit**：`git status/diff --stat/--check/--cached/ls-files/stash list/merge-base`；grep Route.CUSTOM/placeholder/participatesInPk（零命中）；pk/core/data 越界 diff（仅 AMOLED 一行）；hardcoded color 审计
- **Gradle 独立运行**：`:app:testDebugUnitTest --rerun-tasks`（43 suites/364/0/0/0，XML 累加）；`:app:assembleDebug` PASS；`:app:lintDebug` PASS
- **API 35（emulator-5560 Pixel_7）focused**：Plans+Records+ColorRole+RealAppImeFrameProbe+FoldableNavigationLayout——BUILD SUCCESSFUL；FrameProbe VERDICT `imeOpenedCycles=5/5 occludingCycles=5/5 bouncingCycles=0/5 dirChanges=[0,0,0,0,0] motionPhases=[1,1,1,1,1] lateScroll=[-1,-1,-1,-1,-1]`
- **API 33（emulator-5556 Evolune_API33_Migration）focused**：同套件 BUILD SUCCESSFUL；FrameProbe `5/5、5/5、0/5`
- **未运行**：双 phone 全量 connected、折叠屏（Pixel_10_Pro_Fold API37，5554 在线但未驱动）、PK 单独套件、ksp/zipalign、Samsung 真机（离线）
- 设备核查：5556=API33 phone、5560=API35 phone、5554=Pixel_10_Pro_Fold（API37）、5558=Wear（弃用）；真机 R5CW21W4THE 离线

## Final decision

**APPROVE WITH P2**

22 项批准门槛逐项：1-21 全部满足（代码/自动证据/报告记录的用户验收声明）；22（无 P0/P1）满足。用户视觉验收陈述记录在案（不可独立复验——F1 已如实标注）。

最终结论：

- **是否允许 commit production/tests/reports：YES**
- **是否允许 commit review：YES**
- **是否允许创建 UI stabilization tag：YES**
- **是否允许合回 `phase1/batch7-design`：YES**
- **是否允许随后启动 Batch 7 DESIGN review：YES**
- **不得直接开始 Batch 7 implementation：NO**
- **不得实现 Widget：NO**
- **不得实现 Custom medication：NO**
- **Room v3 仍不可发布：NO**
