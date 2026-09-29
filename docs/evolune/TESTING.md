# Evolune 测试环境 / Testing Environment

## 标准 androidTest 环境 / Standard androidTest environment

`app` 的 androidTest（约 396 个设备测试）里，Insights / Timeline 的 UI 测试依赖固定的语言与屏幕：

| 项目 | 标准值 |
|---|---|
| 系统镜像 | Android 15 / API 35，`google_apis`，x86_64 |
| 设备配置 | Pixel 7 profile |
| 屏幕 | 1080×2400（density 420） |
| 系统语言 | zh-CN |
| 动画 | 关闭（window / transition / animator scale = 0） |

在 AVD 默认值（en-US、1080×1920）下，有 8 个 Insights/Timeline 测试会失败：1 个断言中文日期格式，
7 个因屏幕太矮导致 lazy 列表节点不在组合范围内。v1.9.0 基线在同一模拟器上失败的也是这 8 个，
这是环境前提，不是回归。/ On the AVD defaults (en-US, 1080×1920) 8 Insights/Timeline tests fail; this is an
environment precondition, not a regression.

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
  报告作为 `androidtest-reports` 上传。

首次 CI 运行（2026-09-29，PR #32，core 范围）：环境确认为 zh-CN / 1080x2400 / API 35，92 个 insights + timeline 设备测试全部通过，
模拟器任务总耗时约 9 分 19 秒。PR 中只改动该 workflow 或 `scripts/android_test_env.sh` 时也会触发它。
`full` 范围（约 396 个测试）尚未在 CI 上跑过，耗时未知。
