# v1.6 Phone Widget visual polish review

日期：2026-09-06
范围：Phone Widget 系统入口、外观设置预览、今日计划、下一次服药、当前 E2、E2 趋势
工作区：`main` / `df12329278eafa488713edf202554cdbd523b8d0`，dirty，未创建提交

## 当前结论

用户本轮指出的 Phone Widget 视觉问题已经修复并进入最新 Debug APK。该包通过 `adb install -r`
覆盖安装到隔离模拟器 `emulator-5558`，没有卸载、清除数据或移除既有实例。主机 APK 与设备临时副本的
SHA-256 一致，Pixel Launcher 已验证四个独立入口、四种系统预览及带真实数据的四个运行态实例。

本记录只关闭本轮 Phone Debug 模拟器视觉缺陷，不批准 v1.6 最终发布。Release 签名构建、真实手机、
Wear Tile 与 Complication 的最终实机矩阵仍需后续独立验收，因此 v1.6 保持 `IN_PROGRESS`。

## 已确认根因和修复

| 用户现象 | 已确认根因 | 修复与结果 |
|---|---|---|
| 下一次服药、当前 E2 的外观设置示意图视觉上未居中 | 右栏布局框的数学中心正确，但全高 `Column` 加上字体 ascent/descent 额外留白后，字形视觉重心仍显偏移 | 右栏改为全尺寸居中 `Box` 包裹按内容高度测量的文字组，并为两行关闭 `includeFontPadding`；药名 18sp、时间 16sp，横向均占满右栏并居中 |
| 今日计划与 v1.5 排版不一致且只显示一条 | v1.6 紧凑布局按 `rowCapacity` 截断数据，并叠加偏离 v1.5 的行背景 | 恢复 v1.5 行结构、色条、字段和对勾；向 `ListView` 提供完整当日条目，由宿主支持滚动 |
| 未记录项出现错误图标 | 非已记录 occurrence 曾映射为 `OPEN_APP` 图标 | 所有未记录 occurrence 恢复 `RECORD -> ic_widget_check`；已记录项仍使用填充圆形对勾 |
| 趋势标题/浓度太小 | 运行态没有按尺寸应用趋势字体档位 | 新增四档趋势字体策略；当前 4x2 运行态标题与浓度使用 16sp |
| X/Y 轴未交汇 | X 轴和 Y 轴各自带偏移 | 去除错位并统一 2dp 轴线；左下角真实交汇 |
| `−24h 现在 +24h` 挤在中间 | 三段文字存放在单个居中 TextView 中，空格不能按实际宽度分配 | 改为三个等权字段，分别 start/center/end 对齐；运行态、系统 Picker 与外观设置预览同步 |
| Picker 柱从中间向下悬挂 | 静态预览横向 `LinearLayout` 的默认文字基线对齐覆盖底部对齐 | Picker 和四套运行态图表容器均设置 `baselineAligned=false`，柱底统一贴 X 轴并向上延伸 |
| 运行态峰值只有约一半 | 宿主同时提供横竖屏 min/max 尺寸，旧代码始终读取 minHeight，折叠屏竖屏实例按错误短高度计算 | 竖屏选 `minWidth/maxHeight`，横屏选 `maxWidth/minHeight`；按实际浓度最大值归一化到绘图区约 75%，保留圆角顶部空间 |

## 最终 Debug 产物身份

| 字段 | 值 |
|---|---|
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| applicationId | `io.github.yingqiu0871.evolune.debug` |
| versionName | `1.6.0-debug` |
| versionCode | `101060000` |
| variant | `debug` |
| SHA-256 | `356D1C7432213B29E638D5C75F5CACF093CE9EAC656F374C9A1F5284DB23AF76` |
| 模拟器安装 | `adb install -r` 覆盖安装成功；主机与设备临时副本 SHA-256 一致 |
| 安装时间 | `2026-09-06 21:45:44` |

Merged manifest 包含 `EvoluneWidgetReceiver`、`NextDoseWidgetReceiver`、`CurrentE2WidgetReceiver`、
`PkChartWidgetReceiver` 及各自 provider XML；APK 包含四个 preview layout 与专用圆角柱资源。

## Phone Widget 最终证据矩阵

| 入口 | 独立发现/Picker | 外观设置示意图 | 实际显示 | 滚动/尺寸 |
|---|---|---|---|---|
| Evolune-今日计划 | PASS；独立 2x2 入口 | PASS；v1.5 三行示意、色条、字段及圆形对勾 | PASS；真实计划、进度、E2、多个 occurrence 与对勾可见 | PASS；实际滚动后显示 23:00、23:05、23:30，不再只显示一条 |
| Evolune-下一次服药 | PASS；独立 3x1 入口 | PASS；右栏字形视觉居中；药名略小，08:00 放大且完整显示 | PASS；真实药名、23:00 和标题均居中 | PASS；按 3x1 当前尺寸使用响应式字号 |
| Evolune-当前 E2 | PASS；独立 3x1 入口 | PASS；右栏 `~120`/`pg/mL` 字形视觉居中 | PASS；真实 `~171` 与计算时间居中显示 | PASS；按 3x1 当前尺寸使用响应式字号 |
| Evolune-E2 趋势 | PASS；独立 4x2 入口，柱从 X 轴向上 | PASS；标题/浓度放大、轴交汇、示例峰值约 75%，三时间刻度均匀分布 | PASS；真实 E2、历史实色/预测淡色、轴交汇、峰值约 75%，刻度左中右分布 | PASS；按当前方向尺寸和样本最大浓度归一化 |

证据文件：

- `V16_EMU5558_WIDGET_PICKER_AXIS_FINAL_2026-09-06.png` 与 `...BOTTOM...png/.xml`：四个独立入口、居中 hero 预览及均匀时间刻度。
- `V16_EMU5558_WIDGET_AXIS_RUNTIME_FINAL_2026-09-06.png/.xml`：四种真实运行态和趋势图最终时间轴。
- `V16_EMU5558_TODAY_PLAN_SCROLLED_FINAL_2026-09-06.png/.xml`：滚动后的今日计划，同时包含当前 E2 运行态。
- `V16_EMU5558_NEXT_DOSE_VISUAL_CENTER_FINAL_2026-09-06.png/.xml`：下一次服药外观页，关闭字体额外留白后的视觉居中结果。
- `V16_EMU5558_CURRENT_E2_VISUAL_CENTER_FINAL_2026-09-06.png/.xml`：当前 E2 外观页的视觉居中结果。
- `V16_EMU5558_E2_TREND_CONFIG_AXIS_FINAL_2026-09-06.png/.xml`：趋势外观页的轴、柱高和三时间刻度。

用户已取消单独的“今日进度”Widget；完成度保留在“今日计划”头部。因此选择器的正确结果是四个 Evolune 入口。

## 验证结果

| 验证 | 结果 | 证据 |
|---|---|---|
| Fresh Phone JVM suite | PASS；684 tests，0 failures/errors/skipped | `V16_PHONE_WIDGET_HERO_VISUAL_CENTER_FINAL_2026-09-06.log` |
| Debug APK + AndroidTest APK | PASS；74 tasks executed | 同上 |
| Merged manifest / APK resources | PASS；四 receiver、四 provider XML、四 preview 与专用柱资源均打包 | `V16_PHONE_WIDGET_AXIS_LABEL_APK_AUDIT_2026-09-06.log` |
| 覆盖安装与身份 | PASS；`install -r`，主机/设备 SHA-256 一致 | `V16_PHONE_WIDGET_HERO_VISUAL_CENTER_APK_AUDIT_2026-09-06.log` |
| 实际桌面显示 | PASS；四种真实实例可见 | 最终运行态截图/XML |
| 今日计划滚动 | PASS；实际手势滚动到晚间三条 | 最终滚动截图/XML |
| 工作树检查 | PASS；`git diff --check` 返回 0；保留既有 dirty 工作树 | 当前命令记录 |

## 剩余问题

| 级别 | 状态 | 项目 |
|---|---|---|
| P0 | 无 | 本轮未发现崩溃、数据破坏或 PK 模型改动 |
| P1 | 无 | 用户本轮 Phone Widget 视觉、方向、时间轴和滚动缺陷已在 Debug 模拟器关闭 |
| P2 | OPEN | Phone 点击后的业务结果、重配、多种尺寸和数据变化后的自动刷新仍需完整矩阵；Wear Tile/Complication 仍需最终模拟器及真表验证 |
| P3 | OPEN | Release 签名构建、真实手机覆盖升级、完整发布复审仍未关闭 |

在 P2/P3 证据补齐以前，不执行最终 `DONE`，不提交、合并或发布。
