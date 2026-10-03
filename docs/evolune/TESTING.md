# Evolune 测试环境 / Testing Environment

## 标准 androidTest 环境 / Standard androidTest environment

截至 v1.11.1（2026-10-01），`app` 的 androidTest 共 396 个设备测试；v1.10.0、v1.11.0 与 v1.11.1 发布前在下表标准环境上
均为 396 个测试、0 失败、0 错误、5 个条件门控跳过（详见各版本发布说明）。其中 Insights / Timeline 的 UI 测试依赖固定的语言与屏幕：Insights / Timeline 的 UI 测试依赖固定的语言与屏幕：

| 项目 | 标准值 |
|---|---|
| 系统镜像 | Android 15 / API 35，`google_apis`，x86_64 |
| 设备配置 | Pixel 7 profile |
| 屏幕 | 1080×2400（density 420） |
| 系统语言 | zh-CN |
| 动画 | 关闭（window / transition / animator scale = 0） |

v1.11.1 及以前，在 AVD 默认值（en-US、1080×1920）下有 Insights/Timeline 测试失败（v1.9.1 记录为 8 个，2026-10-03 在 CI
上复现为 11 个）：1 个断言中文日期格式，其余因屏幕太矮，lazy 列表节点不在组合范围内或被底栏遮住。这些测试现在会先滚动到
目标节点、用语义点击，并按设备语言计算日期，因此在 en-US / 1080×1920 上也通过；标准环境仍是发布证据的基准。
/ Up to v1.11.1 these Insights/Timeline tests failed on the AVD defaults (en-US, 1080×1920); they now scroll to their
target, click through semantics and format dates with the device locale, so they also pass there.

自 v1.11.0 起主页面内容会滚动到半透明（毛玻璃）底栏下方：`performScrollTo()` 之后节点可能位于底栏之后，
坐标点击会落在导航项上，因此真实 Activity 的设置页流程测试改用语义点击 `scrollToAndClick()`。切换标签页的测试
应重试直到目标标签被选中，以避开 200 ms 导航防连点窗口。/ Since v1.11.0 primary-tab content scrolls under the
translucent bottom bar; real-activity flows click through semantics (`scrollToAndClick()`), and tab-switching
helpers retry until the tab is selected because of the 200 ms navigation throttle.

## 本地 / Local

```powershell
# 一次性：创建标准 AVD（需要 cmdline-tools 与 API 35 x86_64 google_apis 镜像）
.\scripts\android_test_env.ps1 -Action CreateAvd
# 启动模拟器后，套用语言/尺寸/动画设置
.\scripts\android_test_env.ps1 -Action Apply
# 跑测试前确认环境（不符合会直接报错）
.\scripts\android_test_env.ps1 -Action Verify
.\gradlew :app:connectedDebugAndroidTest
```

## CI

- `Build Debug APK`（`apkdebug.yml`）：每次 PR / push 到 `main` 都运行 `test`、`:app:assembleDebugAndroidTest`
  （androidTest 编译门）、两个 Debug 构建。
- `Android Instrumented Tests`（`android-instrumented.yml`）：在标准模拟器上运行
  `scripts/android_test_env.sh` 后执行 `:app:connectedDebugAndroidTest`。push 到 `main` 与手动触发默认只跑
  `ui.screens.insights` 和 `ui.screens.timeline` 两个包；每周日（UTC 19:00）与手动选择 `full` 时跑全部。
  手动触发还可以填 `locale` / `size`（默认 `zh-CN` / `1080x2400`），例如 `en-US` / `1080x1920`，用来确认测试不依赖语言和屏幕高度。
  报告与原始结果（含每个测试的 logcat）作为 `androidtest-reports` 上传。

首次 CI 运行（2026-09-29，PR #32，core 范围）：环境确认为 zh-CN / 1080x2400 / API 35，92 个 insights + timeline 设备测试全部通过，
模拟器任务总耗时约 9 分 19 秒。PR 中只改动该 workflow 或 `scripts/android_test_env.sh` 时也会触发它。

`full` 范围首次在 CI 上运行（2026-10-03，PR #42）：396 个测试约 14 分钟跑完（任务总计约 17 分钟）。第一次有 1 个失败：
`RealAppImeFrameProbeTest.frameLevelImeMotionAnalysis` 在 5 个输入法弹出周期中的 1 个检测到方向反转。随后同一分支上的
2 次标准环境全量运行与 1 次 en-US / 1080×1920 全量运行均为 396 个测试、0 失败、5 个条件门控跳过，因此该探针记为间歇失败，
原因待查（logcat 现已随报告上传，再次失败时可直接查看各周期的帧数据）。
