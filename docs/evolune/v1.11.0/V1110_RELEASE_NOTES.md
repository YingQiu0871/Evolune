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

（发布前补充 / to be completed before publication）
