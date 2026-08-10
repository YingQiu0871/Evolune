# App UI Stabilization Report

分支 `phase1/medication-plan-ui-sizing-fix` · 基线 tag `phase-1-plan-save-regression-fix`
工作区 `D:\Evolune-plan-ui-fix` · 报告日期 2026-08-10

**当前状态（2026-08-10 更新）：Implementation complete pending independent review.**
用户已在 Samsung SM-S918B 上确认 IME 弹跳、深色模式文字、OLED 全黑选项图标、竖屏
选项网格、编辑器操作行、日期/时间布局、单一 `mg`、floating label、Settings 外观、导航
转场和紧凑底部导航均正常。下文保留先前失败结论作为排障历史；最终状态以本段、§21
和 §22 为准。未 review、commit、tag，也未开始 Batch 7。

---

## 1. 范围与目标

修复用户在三星 SM-S918B 真机上亲自观察到的 6 项 UI 缺陷，不改动任何
Domain / Repository contract / DAO / Entity / Room schema / migration / PK 算法 /
JSON protocol / Wear protocol / Widget production / Batch 7 设计与实现。

证据等级（本报告严格遵守）：

```
真实生产 UI  >  probe  >  instrumentation  >  agent 报告
```

即：instrumentation 全绿**不构成**缺陷已修复的结论；只有在真实运行的生产 UI 上
采集到的几何/帧数据才作为主要证据，instrumentation 仅作回归护栏。

## 2. 六项真机缺陷清单

| # | 现象（用户真机观察） | 修复单元 |
|---|---|---|
| 1 | Settings 底部出现异常矩形色块 | Fix B |
| 2 | MedicationRecord 输入时键盘弹起导致页面**弹跳**（最高优先级） | Fix D |
| 3 | 日期 / 时间控件换行折断 | Fix C |
| 4 | Settings 已选中项文字过淡、几乎不可读 | Fix A |
| 5 | 折叠屏 / 宽屏未做自适应导航 | Fix F |
| 6 | 快速连续切页时过渡动画错乱 | Fix E |

## 3. 变更面（git status / diffstat 实测）

生产代码 10 文件、测试与配置 3 文件，`645 insertions / 361 deletions`：

```
app/build.gradle.kts                                  |   1 +
gradle/libs.versions.toml                             |   1 +
MainActivity.kt                                       |  25 +-
navigation/AppNavigation.kt                           | 443 +++++++++++-----
ui/components/MedicationPlanBottomSheet.kt            |  37 +-
ui/components/MedicationRecordBottomSheet.kt          | 171 ++++---
ui/screens/MedicationPlansScreen.kt                   |  27 --
ui/screens/MedicationRecordsScreen.kt                 |  69 +---
ui/screens/SettingsScreen.kt                          |  94 ++---
res/values/strings.xml, values-zh-rCN/strings.xml     |  16 +-
androidTest/…/MedicationPlansScreenTest.kt            |  76 +++-
androidTest/…/MedicationRecordsScreenTest.kt          |  46 ++-
```

未被触碰：Domain、Repository、DAO/Entity、Room schema、migration、Wear、Widget。

## 4. Fix A — 已选项配色（缺陷 4）

**根因**：`SegmentedListItem` 选中态用了 `secondaryContainer` 作容器，但前景色沿用了
默认的 `onSurfaceVariant`，而非配对的 `onSecondaryContainer`。M3 的 container /
onContainer 是成对 token，错配后对比度塌到接近背景色，表现为"文字过淡"。

**修复**：在 `SettingsScreen.kt` 抽出 `settingsListItemColors()`，显式配对
`containerColor = secondaryContainer` ↔ `headlineColor / supportingColor /
leadingIconColor = onSecondaryContainer`，未选中态回落到 `surface` ↔ `onSurface`。

## 5. Fix B — Settings 底部异常矩形（缺陷 1）

**根因**：嵌套 Scaffold 的 inset **重复累加**。外层 Scaffold 已消费
`safeDrawing`，内层用 `Modifier.padding(innerPadding)` 施加同一份 inset ——
`Modifier.padding` **只做布局位移，不消费 inset**，只有 `consumeWindowInsets`
才会把 inset 从子树的 `WindowInsets` 中扣除。于是内层再取一次 `navigationBars`，
在底部多留出一段用容器色填充的条带，即用户看到的"矩形"。

**修复**：内层容器改用 `Modifier.consumeWindowInsets(innerPadding)`，并把
`contentWindowInsets` 收敛为单一来源。

## 6. Fix C — 日期 / 时间控件换行（缺陷 3）

**根因**：日期与时间两个 `AssistChip` 放在固定 `Row` 中，窄屏下文本
`measuredWidth` 之和超过可用宽度即被折断。

**修复**：`MedicationRecordBottomSheet.kt` 改为 `BoxWithConstraints` 驱动，
在 `maxWidth` 不足时才降级为纵向堆叠，足够时保持同行并排。

**真机生产 UI 证据**（在真实编辑器上 dump 得到，非 instrumentation）：

```
date "2026-08-09"  bounds [200,467][429,516]
time "08:33"       bounds [693,467][806,516]
```

两者 `top/bottom` 完全一致（467/516）、水平不重叠 → 单行并排，无换行。

## 7. Fix D — IME 弹跳：历史诊断与最终状态

**Historical / Superseded intermediate stage:** 删除
`snapshotFlow { imeInsetBottom }` + `animateScrollTo(...)` 消除了一个确定的双滚动来源，
但该中间版本在真实 Samsung Keyboard 上仍可稳定复现肉眼可见弹跳。因此“手动滚动是
唯一根因”和“改由框架后已修复”两个当时的结论均被该阶段的真机验收否定。

该阶段的生产实现为全屏 `Surface`，滚动 `Column` 同时使用
`windowInsetsPadding(WindowInsets.safeDrawing)` 与 `verticalScroll`。是否还存在
IME inset 驱动的 viewport 变化与 TextField bring-into-view 的速度曲线叠加，在当时仍需
进一步验证。后续实现和 Samsung production visual acceptance 已补齐最终验收门槛并通过；
当前状态见 §17 和 §19。

## 8. Fix E — 快速切页过渡错乱（缺陷 6）

**根因**：过渡方向依赖一个外部可变状态（上一次选中的 tab index）。快速连续切页时
该状态的写入与 `enterTransition` / `exitTransition` 的读取交错，方向被算成相反值，
表现为进出动画互相打断、页面从错误一侧滑入。

**修复**：改为**无状态方向推导** —— 直接从过渡 lambda 的
`initialState` / `targetState` 参数比较路由在 tab 序列中的下标，方向完全由本次过渡
自身的两个端点决定，不再依赖任何外部可变量。弹簧参数统一为
`Spring.DampingRatioNoBouncy` + `Spring.StiffnessMediumLow`。

## 9. Fix F — 折叠屏 / 宽屏自适应导航（缺陷 5）

**根因**：导航容器硬编码为 `NavigationBar`，宽屏与折叠展开态下底栏横跨整屏，
不符合 M3 自适应规范。

**修复**：引入 `calculateWindowSizeClass(activity)`：
`Compact` → `NavigationBar`；`Medium` / `Expanded` → `NavigationRail`。
导航栈与各页状态在尺寸类切换时保持不变。

**依赖门（§20）**：此修复需要 `androidx.compose.material3:material3-window-size-class`。
按 §20 要求已先停止实现、提交依赖论证（为什么现有依赖不足、建议版本、必要性、
替代方案），**并已获得用户批准**后才落地。版本经 Compose BOM 统一管理，
`gradle/libs.versions.toml` 与 `app/build.gradle.kts` 各 +1 行，未引入独立版本号。

## 10. Frame-level IME 探针方法论

缺陷 2 要求帧级轨迹分析，故未采用"测完终态就算过"的写法，而是构建
`RealAppImeFrameProbeTest`，在**真实 MedicationRecord 编辑器**上按帧采样：

```kotlin
private data class Frame(
    val tMs: Long, val imeBottom: Int, val fieldTop: Float,
    val fieldBottom: Float, val scroll: Float, val scrollMax: Float
)
```

判定指标由 `analyse()` 计算，其中直接对应"弹跳"的是：

- `dirChanges` —— 字段垂直位移的方向反转次数。单阶段 ease 应为 **0**。
- `motionPhases` —— 被静止段分隔的运动阶段数。单阶段 ease 应为 **1**。
- `lateScrollMs` —— IME 停稳后仍发生的滚动（第二次 ease 的特征），应为 **-1**（无）。
- `endFieldBottom` vs `endViewportBottom` —— 终态字段是否仍被键盘遮挡。

采样用 `sampleUntilStill(maxMs, stableMs, requireImeState)`，**同时**要求 IME 与
字段都静止、且 IME 已到达期望状态才退出，避免"从未开始"被误判为"已稳定"。

## 11. 为什么需要测试专用 IME（ProbeTestIme）

模拟器上的 Gboard 处于**未配置首启状态**，截图显示它渲染为左边缘的**浮动药丸**。
浮动 IME 不占用底部空间，因此探针读到的 `imeBottom` 只有 63px（候选词条），
此时"不发生滚动"是正确行为，探针据此得出的任何"通过"都是**空洞通过**。

尝试并失败的路径：改 `shared_prefs`（只有 feature flag，无键盘形态项）、
uiautomator（IME 窗口 0 个可见节点）、Gboard SettingsActivity（不存在）。
`pm clear` 属用户明令禁止项，已拒绝执行。

最终方案：在 **androidTest APK 内**新增 `ProbeTestIme`（`InputMethodService`），
输入视图为固定 `450dp`（≈1181px）的 `FrameLayout`，`onEvaluateFullscreenMode = false`。
它只存在于测试 APK，**不进入任何生产构建**。探针用
`@Before` / `@After` 通过 `uiAutomation.executeShellCommand` 的
`ime enable/set` 切换并在结束时还原原 IME。

IME 显隐由 `WindowInsetsControllerCompat(window, decorView).show/hide(Type.ime())`
在 `runOnMainSync` 中驱动 —— 因为 `performClick` 对已聚焦字段不会重新唤起键盘，
早期写法 5 个 cycle 里只有 1 个真正开过键盘。

## 12. IME 证据与结论修正（Historical / Superseded）

本节保留当时用于纠正自动化结论的方法学证据。它描述的是最终 Samsung 验收完成前的
中间诊断状态，不代表当前验收结论。

`RealAppImeFrameProbeTest#frameLevelImeMotionAnalysis` → **OK (1 test)**，
API35 Pixel_7（emulator-5558，411dp / 2400x1080 / 420dpi）：

```
BASELINE viewHeight=2400 fieldBottom=1408.0 imeClosed
VERDICT  imeOpenedCycles=5/5 occludingCycles=5/5
         maxImeInset=1181px viewHeight=2400px baselineFieldBottom=1408.0
VERDICT  bouncingCycles=0/5
         dirChanges=[0,0,0,0,0]
         motionPhases=[1,1,1,1,1]
         lateScroll=[-1,-1,-1,-1,-1]
```

每个 focus cycle：`fieldTravel=252px`（1408 → 1156）、
`maxFieldSpeed=3.71–8.13 px/ms`、`imeSettled=102–377ms`、
`endFieldBottom=1156` vs `endViewportBottom=1219` → 终态留 63px 余量，未被遮挡。

`dirChanges` 全 0、`motionPhases` 全 1、`lateScroll` 全 -1 只说明
**ProbeTestIme 5/5 个周期**没有该探针能识别的第二阶段运动。在该中间阶段的 Samsung
Keyboard 真实视觉验收失败后，这一结果被判定为 real-keyboard motion 的 false negative，
不能外推。该历史方法学限制不否定 §17 记录的后续最终用户验收 PASS。

探针内置的**空洞通过防护**：若 `occludingCycles == 0`（键盘未真正遮挡字段），
测试以 INCONCLUSIVE 失败而非通过 —— 本次 5/5 遮挡成立，故结论仅对
ProbeTestIme 的确定性场景有效。

### Samsung 视频证据（2026-08-09）

`Screen_Recording_20260809_201928_Evolune.mp4` 为 1080×2316、5.47 秒、344 个原始帧。
该视频从首帧到末帧 Samsung Keyboard 已经展开，因此它**没有记录 IME 从隐藏到展开的
inset 曲线**；它记录的是左右两个剂量 TextField 之间六次焦点切换。

按 540px 宽缩放后的逐帧几何结果：

- Samsung Keyboard 顶边：`y=654`，全片不变；
- 给药途径控件顶边：`y=211`，全片不变；
- 药物类型控件顶边：`y=367`，全片不变；
- 剂量框外轮廓顶边：`y=536..538`，仅有 focused stroke 的 2px 光栅差；
- 可见焦点切换约发生在 `0.548 / 1.307 / 2.091 / 2.782 / 3.708 / 4.508s`。

因此视频中肉眼看到的“弹”不是 editor root、scroll 或 IME 顶边位移，而是两个空
`OutlinedTextField` 的 Material 3 floating label 在“框内居中 ↔ 边框标签”之间同时
交叉动画。该结论对**此视频内的可见运动**置信度为 HIGH；它不替代尚未采集到的
Samsung Keyboard 展开阶段诊断。

### HRTTracker 对照

只读对照提交 `043fb2b2eae3b72b1af718b46bcba797ec6fe8dd`。两边使用相同
Compose BOM `2026.02.01` 和 Material3 `1.5.0-alpha15`，DoseInput 的两个
`OutlinedTextField` 也采用同一 floating-label 结构。因此不能把视频中的标签动画
归因于 Evolune 独有的 Material3 版本。

关键容器差异是：HRTTracker 使用 `ModalBottomSheet` + 单一 `verticalScroll`，没有在
表单滚动容器上追加 `windowInsetsPadding(WindowInsets.safeDrawing)`；Evolune 为满足
全屏 editor 要求改成 `Surface(fillMaxSize)` + `safeDrawing` + `verticalScroll`。
HRTTracker 的实现不能原样复制回去，因为本轮已锁定 fullscreen editor 不回退为圆角 sheet，
但它证明应优先审计 Evolune 新增的 fullscreen/insets 路径，而不是修改 Domain 或输入法。

## 13. 我在探针/工具链里发现并修正的三个自身缺陷

这些缺陷都属于"验证工具本身在骗人"，比生产缺陷更危险，逐条记录：

1. **`Activity.runOnUiThread` 是异步的**。在非主线程调用时它只是 post 到 handler 就立即返回，
   于是 `readViewHeight()` 读到 `viewHeight=0px`，inset 读取也在竞态。
   改用 `InstrumentationRegistry.getInstrumentation().runOnMainSync`（同步）后读到 `viewHeight=2400px`。

2. **`tr '>' '>\n'` 是空操作**。`tr` 做单字符映射且会截断 SET2，该命令什么也不做。
   所有基于它的 uiautomator "首个 bounds" 抽取都返回了文档第一个节点，
   导致计算出的点击点全部落在 `540,68`。改用 `sed 's/></>\n</g'` 修复。
   这一条使早期若干基于该管道的验证结论存疑（`grep -oE` 直接抽取的部分不受影响）。

3. **baseline 在调整之后才测**。`performClick` 已经唤起键盘，
   于是 `baselineFieldBottom` 记成了 1156（已被抬升值）而不是 1408（静息值），
   基线本身被污染。修法：先 `setImeVisible(false)` 并采样到静止，再测 baseline。

另有两处方法学修正：`sampleLoop` 原先"IME 500ms 未变即认定 settled"，
在键盘从未打开时平凡成立、无法区分"稳定"与"从未开始"，重写为
`sampleUntilStill(requireImeState)`；`imeFirstOpenMs >= 0` 这一守卫
被 63px 候选词条满足，已强化为"必须真实遮挡字段"。

## 14. 一个被明确撤回的错误假设

我曾断言模拟器 63px inset 是 AVD 的 `hw.keyboard=yes` 造成的，
并把 `C:\Users\1\.android\avd\Pixel_7.avd\config.ini` 改为 `no` 后冷启动。
**IME 仍然是 63px** —— 假设被证伪。我已明确撤回该结论，
并把 `hw.keyboard` **还原为 `yes`**，AVD 配置无遗留改动。
真实原因见 §11（Gboard 浮动药丸形态）。

## 15. 依赖变更（§20 gate）

唯一新增依赖：`androidx.compose.material3:material3-window-size-class`，
经 Compose BOM 统一版本，无独立版本号。
`gradle/libs.versions.toml` +1 行、`app/build.gradle.kts` +1 行。

该依赖为 Fix F 所必需：`WindowSizeClass` / `calculateWindowSizeClass(activity)`
是 compact / medium / expanded 断点的官方来源；手写 dp 阈值无法覆盖
折叠态切换与厂商异形比例。已按 §20 先停止、说明必要性与替代方案，
并在获得你的批准后才落地。**除此之外未新增任何依赖。**

## 16. 完整测试矩阵（累计实测结果）

| 项目 | 环境 | 结果 |
|---|---|---|
| JVM 单元测试 | 本地 | 43 suites / **364 tests, 0 failures/errors/skipped** |
| connected androidTest | API33 phone | **114 tests, 0 failures/errors, 2 skipped** |
| connected androidTest | API35 phone | **114 tests, 0 failures/errors, 2 skipped** |
| frame-level IME probe | emulator-5558 (API35) | **OK (1 test)**，见 §12 |
| Samsung Keyboard 真实生产 UI | Samsung SM-S918B | **PASS：用户最终视觉确认** |
| 折叠屏 focused UI/MD3 | Pixel_10_Pro_Fold，OPENED，API37 | **25 tests, 0 failures/errors/skipped** |
| `lintDebug` | — | **0 errors / 83 warnings / 1 hint** |
| `assembleDebug` | — | **SUCCESS**（69,759,224 bytes） |
| `compileDebugAndroidTestKotlin` | — | **SUCCESS** |
| `kspDebugKotlin` | — | **SUCCESS** |
| APK 16KB alignment | build-tools 36.1.0 | **`zipalign -c -P 16 -v 4` PASS** |
| 自适应导航矩阵 | 实时 resize | 360/412dp → NavigationBar；600/720/800/1000dp → NavigationRail；状态保持 |

新增 / 改动的测试文件：

```
新增  androidTest/AndroidManifest.xml                （声明 ProbeTestIme）
新增  androidTest/res/xml/probe_test_ime.xml
新增  androidTest/…/testime/ProbeTestIme.kt
新增  androidTest/…/RealAppImeFrameProbeTest.kt      （帧级弹跳判定）
新增  androidTest/…/ColorRoleConformanceTest.kt      （Fix A 配色回归护栏）
新增  androidTest/…/FoldableNavigationLayoutTest.kt  （导航断点与标题稳定性）
改动  androidTest/…/MedicationPlansScreenTest.kt     （+76）
改动  androidTest/…/MedicationRecordsScreenTest.kt   （+46）
```

## 17. 真机验收状态

Samsung SM-S918B（R5CW21W4THE）已通过 `adb install -r` 安装最终 debug APK；未卸载、
未 `pm clear`，也未读取或导出真实数据库。用户本人使用 Samsung Keyboard 在真实
production editor 中完成最终复验，确认 IME 弹跳及本轮全部视觉项均正常。因此真机
验收为**已执行且通过**。

**Historical diagnostic evidence:** 5.47 秒旧视频证明了一个独立的局部视觉来源：键盘
已展开且所有外部几何保持不变时，empty `OutlinedTextField` 的 floating label 焦点交叉
动画仍产生可见“弹”。该视频没有包含 IME opening，因此在当时不能单独关闭 IME issue；
后续用户实际 Samsung Keyboard visual acceptance 已补齐最终门槛并通过，它不再构成
当前未闭合项。

另有一处早期未闭合项：emulator-5556 曾被误用 —— `am get-config` 显示它是
**Wear OS 手表**（454x454、`-watch`、sw227dp），我把手机 APK 装到了手表上，
该轮"通过"已作废并弃用，未计入 §16。

## 18. 约束遵守情况

全程未执行：`git stash pop/apply/drop`、`git reset --hard`、`git clean -fd`、
切分支、`commit`、`tag`、`adb uninstall`、`pm clear`、清除用户 App 数据、
destructive reinstall。未读取、pull 或 dump 任何真实数据库。
未修改 Domain / Repository contract / DAO / Entity / Room schema / migration /
PK 算法 / JSON protocol / Wear protocol / Widget production / Batch 7 相关内容。
AVD 临时改动已还原（§14）。

Widget 需求（Material You 动态取色、用户可调透明度）已记录，**本次未实现**。

## 19. 完成标准逐条核对（12 项）

| # | 标准 | 状态 |
|---|---|---|
| 1 | 六项缺陷均已定位到根因（非症状规避） | 满足 |
| 2 | 每项修复为可独立审查的原子单元 A–F | 满足 |
| 3 | 未触碰禁改面 | 满足 |
| 4 | 依赖变更走 §20 gate 并获批准 | 满足 |
| 5 | JVM 单元测试全绿 | 满足（364/364） |
| 6 | connected androidTest 全绿（≥2 API level） | 满足（API33、API35 各 114 项，0 failures/errors） |
| 7 | `lintDebug` 无 error | 满足（0 errors） |
| 8 | `assembleDebug` 成功 | 满足 |
| 9 | IME 弹跳有真实 Samsung Keyboard 与确定性 probe 证据 | 满足 |
| 10 | 自适应导航在断点矩阵上实测 | 满足 |
| 11 | **三星真机验收** | **PASS（§17）** |
| 12 | **用户本人视觉验收** | **PASS** |

**12/12 completion criteria satisfied.**

Current status: **Implementation complete pending independent review.**

这不表示 independent review 已通过，也不授权提交、打标签、发布或 Batch 7 实施。

## 20. Next step

1. 对当前 UI stabilization change set 执行 independent read-only review。
2. 若 review 返回 APPROVE / APPROVE WITH P2，且没有 P0/P1：依次创建 implementation
   commit、review commit、UI stabilization tag，并合并回 `phase1/batch7-design`。
3. Batch 7 implementation 在 Batch 7 design independent review 完成前仍然禁止。
4. Widget Material You 动态取色和可调透明度继续延期。
5. Custom medication support 继续延期。

## 附：修复单元与文件对应表

| 单元 | 缺陷 | 主要文件 |
|---|---|---|
| A | 4 | `ui/screens/SettingsScreen.kt` |
| B | 1 | `navigation/AppNavigation.kt`, `MainActivity.kt` |
| C | 3 | `ui/components/MedicationRecordBottomSheet.kt` |
| D | 2 | `MedicationRecordBottomSheet.kt`, `MedicationPlanBottomSheet.kt` |
| E | 6 | `navigation/AppNavigation.kt` |
| F | 5 | `navigation/AppNavigation.kt`, `MainActivity.kt`, `libs.versions.toml`, `app/build.gradle.kts` |

## 21. 最新增量：编辑器按钮尺寸

### 21.1 竖屏尺寸统一

- 新增共享 `MedicationOptionGrid`，计划页的途径、酯类、给药周期、抗雄类型和舌下档位
  统一使用同一套稳定尺寸逻辑。
- 竖屏给药途径改为两列等宽网格；宽屏为三列。每项固定高度，长中文最多自然居中两行，
  selected/unselected 只改变语义和颜色，不改变宽高。
- 新增共享 `MedicationEditorActionRow`；方案与记录编辑器的删除、取消、保存均为等权重、
  56dp 高。删除图标和文字保持单行，未缩小无障碍触摸目标。

### 21.2 延期项：自定义药物

“其他药物”属于药物身份，不属于给药途径。本分支不再使用自定义 route、虚假 `E2`
占位或 PK 过滤分支表达该需求；相关临时实现已完整撤回。

后续独立设计需支持用户输入药物名称（例如“微粉化黄体酮”），并统一覆盖计划、记录、
提醒、排程、PK unsupported、Domain、持久化、JSON 与 Wear compatibility。本轮没有修改
Domain、Repository contract、schema、migration、JSON 或既有 PK 行为。

### 21.3 验证

| 验证 | 结果 |
|---|---|
| Full App JVM | 43 suites / 364 tests / 0 failures / 0 errors / 0 skipped |
| PK regression | 5 suites / 49 tests / 0 failures / 0 errors / 0 skipped |
| API 33 focused UI | 28 / 0 failures / 2 skipped |
| API 35 focused UI | 28 / 0 failures / 2 skipped |
| Foldable OPENED focused UI and MD3 | 25 / 0 failures / 0 errors / 0 skipped |
| App debug build | PASS |
| App lint | 0 errors / 83 warnings / 1 hint |
| androidTest Kotlin compile | PASS |
| KSP debug generation | PASS |
| APK 16KB alignment | PASS |

自定义 route architecture/scope P1 已解决；本增量 P0/P1/P2 = 0/0/0。这不等同于
review、commit、tag 或 Batch 7 授权。

## 22. 宽屏顶栏标题闪烁修复

### 22.1 根因

共享顶栏标题曾使用 `Crossfade`。导航切换时旧标题与新标题会短暂叠加，形成文字左右
闪烁的视觉效果；右侧刷新按钮也使用动态动画容器，使顶栏测量状态同时变化。

### 22.2 修复

- `CenterAlignedTopAppBar` 保持为 Material 3 标准组件。
- 标题改为单一 `Text` 层，使用 `MaterialTheme.typography.titleLarge` 和 `onSurface`。
- 标题内容随目标页原子更新，不再叠加两份文字。
- 右侧始终保留标准 48dp action 槽位；主页显示标准 `IconButton`，其他页面为空槽位。
- 页面主体继续使用既有 fade-through/scale 动画，标题层不参与内容转场。

### 22.3 证明

新增折叠屏逐帧回归：在 24 个切换帧中，`app-top-title` 始终只有一个语义节点，且中心
位置相对内容区域偏差不超过 1px。`360×800dp`、`412×915dp` 使用 NavigationBar；
`600×600dp`、`720×720dp`、`800×600dp`、`1000×700dp` 使用 NavigationRail。
展开态折叠屏最终 focused UI/MD3 矩阵为 25/25 PASS。

最终风险分类为 P0/P1/P2 = **0/0/0**。实现已完成，等待独立审阅。
