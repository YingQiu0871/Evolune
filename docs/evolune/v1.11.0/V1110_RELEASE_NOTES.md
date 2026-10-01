# Evolune v1.11.0 正式版 / Official Release

Evolune v1.11.0 为顶部标题栏和底部导航栏加入毛玻璃效果，并把用药记录变化后的 Widget 与手表同步工作移出主线程。
Evolune v1.11.0 gives the top app bar and bottom navigation bar a frosted-glass look and moves the widget and watch synchronization that follows a dose change off the main thread.

## 主要更新 / Highlights

### 毛玻璃导航栏 / Frosted-glass bars

- 主页、记录、历史、方案、设置五个主页面的内容现在会滚动到顶部标题栏和底部导航栏的下方；两个栏对其后方的内容做模糊，并叠加 60% 不透明度的底色。浅色、深色与 OLED 全黑模式均适用，侧边导航栏（宽屏）布局同样适用。/ On the five primary tabs, content now scrolls under the top bar and bottom navigation bar, which blur what is behind them under a 60% surface tint. Applies to light, dark and OLED modes and to the navigation-rail (wide) layout.
- 滚动内容的首尾留出两个栏的高度，不会被遮挡；记录页与方案页的悬浮按钮保持在底部导航栏上方。/ Scroll content keeps clear of the bars at both ends; the Records and Plans floating buttons stay above the bottom bar.
- 全屏子页面（用药洞察、回顾性 PK、关于等）保持原有布局。/ Full-screen child pages (Insights, Retrospective PK, About, …) keep their existing layout.
- 未新增依赖：使用 Compose `GraphicsLayer` 录制内容并以 RenderEffect 模糊重绘（最低 API 31，始终可用）。/ No new dependency: content is recorded into a Compose `GraphicsLayer` and redrawn through a RenderEffect blur (minSdk 31, always available).

### 性能 / Performance

- 每次用药记录或方案变化后，应用内原本在主线程上执行的三项工作现已移到后台线程：刷新所有桌面 Widget（读取数据库、PK 计算、生成 RemoteViews）、构建并发布手表快照、同步手表仪表盘。此前在放置了 Widget 或配对了手表的设备上，这会让界面短暂停顿，在「记录」页通过悬浮菜单一键添加已保存方案时最明显。/ After every dose-event or plan change, three pieces of in-app work that ran on the main thread now run in the background: refreshing all home-screen widgets (database reads, PK math, RemoteViews), building and publishing the watch snapshot, and syncing the watch dashboard. On devices with widgets placed or a watch paired this briefly stalled the UI, most visibly when quick-adding a record from a saved plan.

### 发布工具 / Release tooling

- 两个发布脚本要求 PowerShell 7；`release_publish.ps1` 固定目标仓库（`-Repo`，默认 `YingQiu0871/Evolune`）。/ Both release scripts require PowerShell 7; `release_publish.ps1` pins the target repository.

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份或 Portable 格式变化；PK 数值代码与用药语义不变；应用 ID 与签名证书连续。/ No Room schema migration, no backup or Portable format change, PK numerics and medication semantics unchanged, application ID and signing continuity unchanged.
- 版本信息：Phone `1.11.0` — versionCode `101110000`；Wear `1.11.0` — versionCode `1101110000`。/ Version: Phone `1.11.0` / `101110000`; Wear `1.11.0` / `1101110000`.

## 验证状态 / Verification status

- **CI #102（`ecd75b8`）通过**：`Build Debug APK` 工作流（`./gradlew test`、`:app:assembleDebug`、`:wear:assembleDebug`）。#34、#35、#36 各自的 PR 检查也均通过。/ CI run #102 (`ecd75b8`) passed; the PR checks of #34, #35 and #36 passed as well.
- **JVM**：`:app:testDebugUnitTest` 1,461 个测试，0 失败。/ JVM: 1,461 app tests, 0 failures.
- **androidTest（2026-10-01，本机 Pixel_7 AVD，Android 15 / API 35，zh-CN、1080×2400，Debug 构建）**：`:app:connectedDebugAndroidTest` **396 个测试，0 失败，0 错误，5 跳过**（条件门控，与此前版本相同）。/ androidTest: 396 tests, 0 failures, 0 errors, 5 condition-gated skips on the standard environment.
  - 因内容现在会滚到半透明底栏下方，设置页的真实界面流程测试改用语义点击（`scrollToAndClick`）：`performScrollTo()` 之后节点可能位于底栏之后，坐标点击会落在导航项上。/ Because content now scrolls under the translucent bottom bar, the real-activity Settings flows click through semantics (`scrollToAndClick`): after `performScrollTo()` a node can sit behind the bar, where a pointer click would hit a navigation item.
  - `InsightsReleaseGateTest` 在合入 #35 后首次运行失败 1 项：标签页切换更快完成，下一次点击落入 200 ms 导航防连点窗口而被忽略。测试的 `selectTab` 已改为重试直到标签页选中（History 导航测试已有相同做法），随后整套 396 项通过。/ `InsightsReleaseGateTest` failed once after #35 landed: tab switches settle faster, so the next click fell inside the 200 ms navigation throttle and was dropped. Its `selectTab` now retries until the tab is selected (as the History navigation tests already do); the full suite then passed.
- **签名 Release 构建（2026-10-01，所有者本机，候选提交 `ecd75b8`）**：`scripts/release_verify.ps1` 签名构建 Phone/Wear Release（含 R8）成功；versionName 1.11.0，versionCode 101110000 / 1101110000，两个 APK 的签名证书 SHA-256 均为 `b9b6b955…aab08`。哈希以 Release 附带的 `SHA256SUMS.txt` 为准。合并到 `main` 后相对该提交仅有文档变化，因此发布的就是这两个 APK。/ Signed Release build from candidate `ecd75b8` (R8): version 1.11.0, 101110000 / 1101110000, release certificate matches. Hashes: see the attached `SHA256SUMS.txt`. Only documentation differs after merging, so these APKs are published without a rebuild.
- **模拟器冒烟（发布 APK 本身）**：在已安装 v1.10.0 正式版（保留数据）的 Pixel_7 AVD 上 `adb install -r` 覆盖升级到 1.11.0，首次安装时间与已有记录保留；五个主页面可打开，毛玻璃栏在 R8 构建下正常绘制；新建方案后在「记录」页一键添加成功（期间最慢一帧 32 ms）；在桌面放置 Evolune「今日计划」Widget 后，应用内停用/重新启用方案，Widget 随之更新（经后台线程刷新）；无 Evolune 的 `FATAL EXCEPTION`、ANR 或线程错误。/ Emulator smoke on the release APK: in-place upgrade from v1.10.0 with data retained; all five tabs open and the frosted bars render in the R8 build; quick-add from a saved plan works (slowest frame 32 ms); with an Evolune today-plan widget placed, disabling and re-enabling a plan in the app updates the widget (background refresh path); no fatal exception, ANR or threading error.
- **性能**：同一模拟器、同样的滑动动作下，毛玻璃版本与 `main` 的滚动帧耗时无可测差异（中位 17 ms 对 19 ms）。「一键添加卡顿」在模拟器上无法复现（主机 CPU 较快），修复依据代码分析；其副作用（标签页切换后主线程更快空闲）已由上面的测试时序变化间接印证。/ Performance: no measurable scroll frame-time difference between the frosted build and `main` on the same emulator (median 17 ms vs 19 ms). The quick-add hitch could not be reproduced on the emulator (fast host CPU); the fix rests on code analysis, indirectly corroborated by the test-timing change above.
- **未覆盖**：真机上的毛玻璃滚动性能（尤其低端设备）与一键添加卡顿的最终确认；Google Drive 加密备份/恢复；Phone↔Wear 配对同步；Wear 发布 APK 安装冒烟（本次无 Wear 模块改动）；折叠屏几何测试在该 AVD 上为条件跳过（横屏侧边导航栏布局已截图确认）。/ Not covered: real-device confirmation of frosted scrolling performance (especially low-end hardware) and of the quick-add hitch; Google Drive backup/restore; paired Phone↔Wear sync; Wear release APK install smoke (no Wear module changes); the foldable geometry test is condition-skipped on this AVD (the landscape navigation-rail layout was checked by screenshot).
