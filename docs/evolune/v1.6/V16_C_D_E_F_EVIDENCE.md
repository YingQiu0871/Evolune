# Evolune v1.6 — C/D/E/F 实施证据

记录日期：2026-09-06
基线：`df12329278eafa488713edf202554cdbd523b8d0`
候选提交：未创建；本记录对应当前工作树实现，不能替代真实宿主验收。

## C — Phone Gallery renderer

- `WidgetStyle` 的五个 v1.6 展示入口已接入同一份 `WidgetSnapshot`：今日计划、下一次服药、今日进度、当前 E2、PK 图表；旧 `legacy_default` 继续回退到今日计划时间线。
- 配置页新增按实例保存的样式选择、预览标题和稳定 fallback；现有 action-time `AVAILABLE` 校验仍由共享 handler 执行。
- `NEXT_DOSE` 只呈现当前/即将发生的最高优先级 occurrence；`TODAY_PROGRESS` 使用 Phone 派生的完整当日分母；`CURRENT_E2` 传递浓度值与计算时间。
- 定向 JVM：`WidgetUiTest`、`WidgetAppearanceTest`、`WidgetWorkTest` 覆盖无计划/无下一次、48h/25 点和标量一致性；Android 资源结构回归补充 chart container/segment/axis inflate 检查。最终 fresh JVM 为 95 suites / 834 tests，0 failures/errors，原始日志末尾含 XML 聚合摘要，见 [V16_C_D_E_F_JVM_2026-09-06.log](V16_C_D_E_F_JVM_2026-09-06.log)。

## D — Phone PK chart

- `WidgetSnapshotLoader.load(includePkChart = true)` 复用 Phone 权威 PK 适配器，在 `now ±24h` 运行 25 点采样；图表不写入权威存储。
- `WidgetPkChartPolicy` 固定 48 小时窗口和 25 点上限，过滤非有限/负值；RemoteViews 只渲染动态 bar，空数据回退为不可用状态。
- `ContractWidgetUpdateWork` 只在当前实例中存在 `PK_CHART` 时启用采样，避免普通实例重复运行 chart simulation。
- 定向 JVM：`WidgetUiTest` 与 `WidgetWorkTest` 覆盖 48h/25 点、±24h、当前点和非 PK 零计算；Pixel 7 focused Android 19/19 通过，原始日志见 [V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log](V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log)。真实 launcher 截图、数值对照和多实例成本仍待 A/D/G 宿主证据。

## E — Wear Tiles

- 新增 `NextDoseTileService`、`TodayPlanTileService`（产品标题为“今日待服”）、`CurrentE2TileService`；旧 `DoseTileService` identity/path 保留。
- 三个新 Tile 读取 `WearAppStore` 的 v1 snapshot；今日待服只筛选 snapshot 的本地当天 occurrence。下一次服药的点击使用既有 `WearAppDataLayer.confirmOccurrence`，沿用 occurrence identity、Phone authority、persist-first pending 和回执刷新。
- snapshot/confirmation result 到达后请求三个新 Tile 更新；断连、等待、过期、空数据均显示只读状态。今日待服最多显示三项，超过时追加“还有 N 项 · 打开手机查看”提示；独立过期 E2 显示“E2 已过期”。
- `WearGalleryTilePolicyTest` 覆盖今日日期筛选、基于 todaySummary 的完整溢出提示、发送中/已确认/可重试失败文案和过期浓度；`WearAppRefreshPolicyTest` 覆盖 Applied/Duplicate/Older/Rejected 刷新门槛；`:wear:testDebugUnitTest` 与 `:wear:assembleDebug` 通过；Phone/Wear debug assemble 原始日志见 [V16_C_D_E_F_ASSEMBLE_2026-09-06.log](V16_C_D_E_F_ASSEMBLE_2026-09-06.log)。真表、断连/回执视觉和旧/新 APK 配对仍未关闭。

## F — Wear Complications

- 新增 `NextDoseComplicationDataSource`、`TodayProgressComplicationDataSource`、`CurrentE2ComplicationDataSource`，均声明 `SHORT_TEXT` 和 5 分钟系统更新周期。
- 三者复用同一 v1 snapshot；没有 upcoming/todaySummary/concentration 或浓度独立 freshness 为 STALE 时统一输出 `--`，不会把过期估算显示成当前值。
- snapshot/result 更新后调用 `ComplicationDataSourceUpdateRequester.requestUpdateAll()`；实现使用 AndroidX `watchface-complications-data-source:1.2.1`。
- `WearComplicationTextTest` 覆盖 due 时间、今日 `completed/total`、E2 数值、STALE freshness、三 provider 的 READY/STALE value、preview 和 Short Text value/description/tap contract；provider 统一使用 READY snapshot，非 READY 输出 `--`，真实槽位仍需宿主证据。Wear 单测与 debug assemble 已通过。
- 不宣称表盘槽位、Ambient、圆形裁切和后台刷新已通过；这些仍需真实表盘证据。

## Fresh verification index

| 证据 | 结果 | 用途 |
|---|---|---|
| [V16_C_D_E_F_JVM_2026-09-06.log](V16_C_D_E_F_JVM_2026-09-06.log) | PASS；95 suites / 834 tests，0 failures/errors | Experience Core、Phone、Wear JVM 回归，末尾有 XML 聚合摘要 |
| [V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log](V16_C_D_E_F_ANDROID_PIXEL7_2026-09-06.log) | PASS；19/19 | Widget RemoteViews 与 production routing |
| [V16_C_D_E_F_ASSEMBLE_2026-09-06.log](V16_C_D_E_F_ASSEMBLE_2026-09-06.log) | PASS | Phone/Wear debug APK 构建 |

## 当前关闭边界

C/D/E/F 现在是“实现候选 + JVM/build 证据”，不是阶段完成。A-05/A-06/A-07、真实 Launcher/Widget、真实 Wear/Data Layer、表盘槽位、签名升级包和独立最终复审仍由 G 统一关闭。

最终独立复审（C2C iteration 6）返回 `APPROVE V1.6 C/D/E/F CANDIDATE`、P0/P1/P2/P3 全无并标记 `C2C DONE`；该结论不扩展到真实宿主验收或发布批准。

## 2026-09-07 Phone PK chart peak headroom follow-up

本次用户反馈“新增服药记录后最高柱顶部变平”已完成根因核对。根因是圆角柱的计算高度可能等于
绘图区可用高度，而 `widget_root` 的 `clipToOutline=true` 会裁掉贴近父布局上边缘的圆角；这是父布局几何裁切，
不是 PK 数值溢出，也不是预测数据颜色或柱体方向错误。

修复范围：

- `WidgetChartGeometryPolicy.maxSegmentHeightDp()` 按尺寸 tier 扣除布局 chrome，并保留 10dp 顶部留白；
- `EvoluneWidgetReceiver.bindPkChart()` 使用 `OPTION_APPWIDGET_MIN_HEIGHT` 和该上限计算柱高；
- 保留从底部向上绘制、历史实色/预测淡色、窄圆润柱和坐标轴；未修改 PK 数学模型、数据库或 occurrence 语义。

隔离 Phone Debug 模拟器 `emulator-5558` 的真实宿主验证：在停用同名旧 Release 包后，从 Debug-only
Picker 选择 `Evolune-E2 趋势`，完成配置并点击“应用”。运行态截图
[V16_EMU5558_DEBUG_WIDGET_CHART_RUNTIME_DEBUG_ONLY_2026-09-07.png](V16_EMU5558_DEBUG_WIDGET_CHART_RUNTIME_DEBUG_ONLY_2026-09-07.png)
显示真实趋势图，最高柱顶部有可见留白且圆角完整；柱体从底部向上，历史/预测颜色区分，坐标轴及
`−24h`、`现在`、`+24h` 均可见。运行态 XML 和 `dumpsys appwidget` 分别见同名 `.xml` 与
[V16_EMU5558_DEBUG_WIDGET_DUMPSYS_DEBUG_ONLY_2026-09-07.txt](V16_EMU5558_DEBUG_WIDGET_DUMPSYS_DEBUG_ONLY_2026-09-07.txt)。
该次实例 provider 已证实为 `io.github.yingqiu0871.evolune.debug/.widget.PkChartWidgetReceiver`，
不是旧 Release provider。

Fresh verification：

| 项目 | 结果 | 证据 |
|---|---|---|
| Phone JVM + Debug APK + AndroidTest APK | PASS；`WidgetUiTest` 19/19，0 failures/errors | [V16_WIDGET_PEAK_HEADROOM_FINAL_BUILD_2026-09-07.log](V16_WIDGET_PEAK_HEADROOM_FINAL_BUILD_2026-09-07.log) |
| Debug APK identity | `io.github.yingqiu0871.evolune.debug` / `1.6.0-debug` / `101060000` | `app/build/outputs/apk/debug/app-debug.apk` |
| Debug APK SHA-256 | `7CC370139D55C48FE8132B53BA383BB702538BBD2951EAB3739836175EA5A628` | final fresh build output |
| Manifest/resource static check | PASS；四个 receiver、预览和 chart segment 均在 APK；无显式 `<View>` | fresh APK `aapt2`/ZIP inspection |
| Connected instrumentation | NOT PASS；安装阶段 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，既有 Debug 包签名不同；未卸载或清数据 | [V16_WIDGET_PEAK_HEADROOM_CONNECTED_DEBUG_TEST_2026-09-07.log](V16_WIDGET_PEAK_HEADROOM_CONNECTED_DEBUG_TEST_2026-09-07.log) |
| Phone/Wear Release build | BLOCKED；缺少 `EVOLUNE_KEYSTORE_PASSWORD/PATH/KEY_ALIAS/KEY_PASSWORD` | [V16_WIDGET_RELEASE_BUILD_FRESH_2026-09-07.log](V16_WIDGET_RELEASE_BUILD_FRESH_2026-09-07.log) |

本次运行态证据只覆盖隔离 Debug 模拟器和趋势图流程，不等同于 v1.6 最终验收。Release 签名产物、覆盖升级、
真实 Phone/Wear、完整 5 Phone + 3 Tile + 3 Complication 矩阵及独立最终复审仍未关闭；因此 v1.6 继续保持
`IN_PROGRESS`，不得执行最终 `DONE`。

## 2026-09-06 Phone Widget v1.5 layout and axis-label follow-up

根据用户对真实模拟器界面的复核，本轮继续关闭四项 Phone Widget 缺陷：

- 今日计划恢复 v1.5 的行结构、色条、字段间距和对勾；不再按紧凑行容量截断数据，`ListView` 接收完整
  当日条目。模拟器内实际上滑后从早间记录滚动到 23:00、23:05、23:30，证明不是静态多行截图。
- 下一次服药和当前 E2 的运行态左右面板均居中；外观预览的右栏改为全尺寸居中 `Box` 包裹按内容
  高度测量的文字组，并关闭两行文字的字体额外留白。下一次服药的药名为 18sp、计划时间为 16sp，
  二者分别占满右栏后居中，避免药名换行挤掉时间，也消除字体基线造成的视觉偏移。
- 趋势图按当前设备方向选择 AppWidget min/max 尺寸，修复折叠屏竖屏实例读取 `minHeight` 后峰值只有约
  一半的问题；实际浓度仍按当前 25 点最大值归一化，最高柱约占绘图区 75%。
- `−24h`、`现在`、`+24h` 从单一居中文本拆为三个等权字段，分别左、中、右对齐。运行态、系统 Picker
  静态预览和 Compose 外观预览三处结果一致。

最终 fresh 构建执行 684 个 Phone JVM 测试，0 failures/errors/skipped；Debug 与 AndroidTest APK 共
74 个任务成功。最新 Debug APK SHA-256 为
`356D1C7432213B29E638D5C75F5CACF093CE9EAC656F374C9A1F5284DB23AF76`，通过 `adb install -r`
覆盖安装到 `emulator-5558`，设备临时副本哈希一致。原始构建日志、APK 审计、运行态和配置页证据分别见：

- `V16_PHONE_WIDGET_HERO_VISUAL_CENTER_FINAL_2026-09-06.log`
- `V16_PHONE_WIDGET_HERO_VISUAL_CENTER_APK_AUDIT_2026-09-06.log`
- `V16_PHONE_WIDGET_AXIS_LABEL_APK_AUDIT_2026-09-06.log`
- `V16_EMU5558_WIDGET_AXIS_RUNTIME_FINAL_2026-09-06.png/.xml`
- `V16_EMU5558_TODAY_PLAN_SCROLLED_FINAL_2026-09-06.png/.xml`
- `V16_EMU5558_NEXT_DOSE_VISUAL_CENTER_FINAL_2026-09-06.png/.xml`
- `V16_EMU5558_CURRENT_E2_VISUAL_CENTER_FINAL_2026-09-06.png/.xml`
- `V16_EMU5558_E2_TREND_CONFIG_AXIS_FINAL_2026-09-06.png/.xml`
- `V16_EMU5558_WIDGET_PICKER_AXIS_FINAL_BOTTOM_2026-09-06.png/.xml`

本轮没有修改 PK 数学模型、数据库 schema、备份格式或 occurrence 语义。Phone Debug 模拟器视觉缺陷已关闭，
但 Release 签名、真实手机覆盖升级及 Wear Tile/Complication 最终实机矩阵仍未完成，v1.6 保持
`IN_PROGRESS`。

## 2026-09-07 Wear Tile centered design and distinct preview follow-up

本轮在 Wear API 37 圆形模拟器 `emulator-5554` 上继续核对三个新 Tile。设备最初可见的
`NextDoseTileService`、`TodayPlanTileService`、`CurrentE2TileService` 均属于既有 Release 包，单独覆盖安装
Debug APK 不会改变这些 Release 实例；该事实由 `dumpsys wear_service` 的 `DB Visible Tiles` 组件全名确认。
为避免删除或替换用户已有实例，本轮在系统 Picker 中另外添加三个 Debug Tile，旧 Release Tile 和 legacy
`DoseTileService` 均保留。

已确认并修复：

- 三个新服务原先共用 `@drawable/tile_preview`，导致 Picker 无法从预览辨认功能。现在分别使用
  `tile_preview_next_dose`、`tile_preview_today_plan`、`tile_preview_current_e2`；Picker 实际截图显示今日计划
  三行记录和当前 E2 数值预览，不再是旧加号或统一 Logo。
- 运行态原先只有黑底上的标题和单行状态。现在保持 OLED 黑底与圆形安全区，主信息统一放入动态莫奈色
  圆角面板并水平/垂直居中；断连、空数据、READY 三类状态采用同一视觉层级。
- READY 首轮检查发现今日计划使用 `MM-dd HH:mm` 导致药名省略，以及下一次服药按钮默认浅底与文字取色
  不匹配。最终改为今日计划 `HH:mm + 药名`、下一次服药药名与 `剂量 · 时间` 两级居中，并让可点击区域
  复用高对比 tonal panel。
- Tile layout/resources 版本提升到 `3-*`。这是覆盖升级修复的一部分：宿主必须丢弃旧缓存布局，不能继续
  用版本 1 的单行界面渲染既有实例。

模拟器证据矩阵：

| Surface | 系统发现/添加 | 断连状态 | READY 布局 | 居中/裁切 | 备注 |
|---|---|---|---|---|---|
| Evolune-下一次服药 | PASS，Debug 新实例 id=5 | PASS，明确同步提示 | PASS，药名、12.5 mg、时间完整 | PASS | 整块 tonal panel 保持可点击 |
| Evolune-今日计划 | PASS，Debug 新实例 id=6 | PASS，明确未连接提示 | PASS，3 行时间与药名完整 | PASS | 超过 3 项仍使用既有 overflow 提示 |
| Evolune-当前 E2 | PASS，Debug 新实例 id=7 | PASS，明确未连接提示 | PASS，153.2 pg/mL 与计算时间 | PASS | 浓度 freshness 语义未改 |

READY 截图使用只允许在模拟器执行的 instrumentation fixture 注入只读样例 snapshot；它验证布局、更新请求
和宿主渲染，不替代 Phone→Wear Data Layer 真同步证据。三张最终截图为：

- `V16_EMU5554_WEAR_NEXT_DOSE_READY_CENTERED_FINAL_2026-09-07.png`
- `V16_EMU5554_WEAR_TODAY_PLAN_READY_CENTERED_FINAL_2026-09-07.png`
- `V16_EMU5554_WEAR_CURRENT_E2_READY_CENTERED_FINAL_2026-09-07.png`

Picker 功能预览见 `V16_EMU5554_WEAR_TILE_PICKER_SCROLLED_FUNCTION_PREVIEWS_2026-09-07.png`；断连布局、管理页、
组件 DB 与 UI XML 均保存在同目录。`V16_WEAR_TILE_READY_FIXTURE_INSTRUMENTATION_2026-09-07.log` 为最新
fixture 运行记录，结果 1/1 PASS。

最终 Debug APK 为 `wear/build/outputs/apk/debug/wear-debug.apk`，applicationId
`io.github.yingqiu0871.evolune.debug`，versionName `1.6.0-debug`，versionCode `1101060000`，SHA-256
`7F6B6293F5619EADECE949BD348A0A8F8E9EB5EC22397652890C76F9F6F4619A`。merged manifest 与 APK 内资源审计见
`V16_WEAR_TILE_CENTERED_DESIGN_APK_AUDIT_2026-09-07.log`；fresh JVM/build 为 83/83、69 tasks PASS，见
`V16_WEAR_TILE_CENTERED_DESIGN_FINAL_BUILD_2026-09-07.log`。

Complication 源码、merged manifest 和系统 service query 均确认三个 `SHORT_TEXT` provider 可发现且统一
Evolune 品牌图标；现有 Release `CurrentE2ComplicationDataSource` 在表盘槽位显示 `--`，点击可进入 Wear App。
本轮没有在支持槽位中逐个换入三个 Debug provider，因此三者 READY/STALE 的真实表盘渲染仍待最终设备矩阵。
没有触碰真实手表、卸载应用、清除数据、删除旧 Tile 或重置设备。v1.6 继续保持 `IN_PROGRESS`。

## 2026-09-06 Phone Widget visual polish and Picker direction follow-up

用户随后明确取消单独的“今日进度”Widget，并要求四个功能以独立 provider 出现在系统选择器；因此当前
Phone 交付矩阵为“今日计划、下一次服药、当前 E2、E2 趋势”四个入口，今日完成度保留在今日计划头部。

本轮修复了今日计划头部/行间距和圆角行背景、确认服药对勾、下一次服药/当前 E2 的居中与响应式字号、
趋势图轴交汇、75% 峰值高度，以及 Picker 独立静态预览中柱体从共同顶线向下悬挂的问题。该方向缺陷的
最终根因是横向 `LinearLayout` 默认的文字基线对齐覆盖了 bottom gravity；Picker 与四套运行态容器均已
显式设置 `baselineAligned=false`。最终 Debug APK SHA-256 为
`637D6DFE0EE2F72593BB835743D62C1E44FE2303EA8396563DF5C931A5267B3A`；覆盖安装到
`emulator-5558` 后设备 `base.apk` 哈希一致。

最终 Pixel Launcher 证据：

- `V16_EMU5558_WIDGET_PICKER_TOP_FINAL_2026-09-06.png/.xml`：四入口中的今日计划、下一次服药、当前 E2；
- `V16_EMU5558_WIDGET_PICKER_BASELINE_FIXED_2026-09-06.png/.xml`：E2 趋势每根柱以 X 轴为共同底线向上延伸，
  X/Y 轴交汇，历史实色、预测淡色；
- `V16_WIDGET_CHART_BASELINE_FIX_IDENTITY_2026-09-06.log`：版本、覆盖安装和主机/设备哈希；
- `V16_PHONE_WIDGET_VISUAL_POLISH_REVIEW_2026-09-06.md`：根因、修复、验证和剩余等级。

Fresh JVM 676/676、focused host contract 6/6、Debug/AndroidTest APK 构建均通过。本轮最终构建尚未在带真实
数据的四个已添加实例上重做全部刷新、点击、重配和多尺寸验证；Release/真实设备也未关闭，继续保持
`IN_PROGRESS`。

## 2026-09-07 Wear Monet、旧曲线 Tile 布局与省电补充

Wear 端现已使用与 Phone Widget 一致的“系统颜色 + 八组预制色”选择。选择入口位于 Wear App 内容末尾，
应用页、三个新 Tile、旧 `DoseTileService` 曲线 Tile 和三个 Complication 共用保存的颜色角色。旧服务组件名
保持不变；旧曲线 Tile 的标题简化为 `E2 · 数值`，图表至操作块间距从 4dp 增至 12dp，两个按钮缩短并下移，
模拟器实际宿主截图不再拥挤。

功耗策略经源码和 merged manifest 审计：无唤醒锁、前台常驻服务、闹钟、周期 Worker 或无限轮询；Wear App
只在可见生命周期按同步/陈旧截止时间刷新并在停止时清除回调；四个 Tile 和三个 Complication 的被动兜底
统一为 15 分钟，Data Layer 和配色变化继续事件驱动即时更新。

Fresh Wear JVM 85/85、Debug/AndroidTest 构建 69 tasks、模拟器 fixture 1/1 均通过。最终 Debug APK
SHA-256 为 `E8E360D4B196246C1BC4BB31E2D3C71126A920BA8FF8E73A36F45BE4B4137157`，通过保留数据的
`install -r` 覆盖安装到 `emulator-5554`，设备端哈希一致。完整复审、产物身份和截图索引见
`V16_WEAR_MONET_APP_TILE_POWER_REVIEW_2026-09-07.md`。真实手表与 Complication 表盘槽位仍是 P1 证据缺口，
因此 v1.6 继续保持 `IN_PROGRESS`。

截图中旧曲线 Tile 显示“未连接到手机”时没有确认按钮，原因是没有可验证的 occurrence 快照，按契约只显示
同步提示。连接并同步 `UPCOMING`/`DUE` occurrence 后，Wear App 现在会显示“确认服药 / 跳过本次”。新增的
跳过路径只取消 Phone 对应通知，不写 DoseEvent 或改变 occurrence 状态；Phone 先校验 plan、slot、计划时间和
通知 ID。断连或无数据时仍不显示操作。

## 2026-09-07 Wear/Phone 配对复核与“跳过本次”联调边界

用户指出 Wear 模拟器应与 Pixel Fold 配对。本轮对实际设备重新核对后，旧文档中的“已连接”快照不能作为当前事实：`emulator-5554` 的 AVD 为 `Wear_OS_Large_Round`，`emulator-5558` 的 AVD 为 `Pixel_10_Pro_Fold`，但 Wear `WearConnectivityService` 当前报告 `mIsCompanionConnected=false`、`Device=disconnected`、`Companion set=false`、未绑定地址；两端蓝牙 bonded devices 均为 0，Phone 端 Companion Device Associations 为空。Wear 端仅剩一个 `com.google.wear.services` 的系统自管理历史 association，不能证明它与 5558 建立了 Phone–Wear Data Layer 通道。原始核对见 [V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log](V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log)。

Phone Debug APK 已在 Pixel Fold 模拟器执行保留数据的 `install -r` 覆盖安装：`io.github.yingqiu0871.evolune.debug` / `1.6.0-debug` / `101060000`，本地与设备端 SHA-256 均为 `C7C921E9F74760FE08E33C4DF506048FDDE562EF6D6A862A121A39F29D770E44`；Wear Debug APK 保持 `1.6.0-debug` / `1101060000`，SHA-256 为 `E8E360D4B196246C1BC4BB31E2D3C71126A920BA8FF8E73A36F45BE4B4137157`。

已实现的 Wear“跳过本次”协议和 Phone 校验/取消通知路径仍通过 JVM 与 fixture 验证，但由于当前组合没有实际配对，尚未把按钮点击→Phone 收到 DataItem→取消对应通知做成实体 Data Layer 证据。配对缺失属于设备环境证据缺口，不把它解释成源码或缓存问题，也不把契约测试冒充端到端通过。v1.6 继续保持 `IN_PROGRESS`。

> 版本说明：本文件较早的 Phone Debug 记录中的 `7CC370...` 是 2026-09-07 headroom 构建；本轮跳过协议联调前重新确认的当前产物和 5558 设备端产物为 `C7C921...`，后者覆盖前者作为当前 Phone Debug 身份。

## 2026-09-07 配对成功后的 Wear Tile 刷新补充

用户完成 `Wear_OS_Large_Round` 与 `Pixel_10_Pro_Fold` 的网络/Data Layer 配对后，GMS 双端连接与 initial sync、
Wear 收到 Phone snapshot 和计划、以及设备端 Data Layer probe 均通过。先前“未配对”的判断已由
[V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log](V16_WEAR_PHONE_PAIRING_FRESH_2026-09-07.log) 末尾记录正式覆盖。

真实配对暴露了重复 snapshot 不清除 pending 的 Tile 状态机缺陷，现已修复；同时加入 API 37 active Tile
instance 精确刷新。模拟器约 286 秒时钟差现由 5 分钟显示容差处理，Wear App 已展示真实 `0.69 pg/mL`。
确认服药与跳过本次按钮在圆屏上改为单行居中。Wear JVM 86/86、设备安全测试 3/3、最终 APK/设备哈希一致。

完整根因、产物身份、矩阵与剩余 P1/P2 见
[V16_WEAR_PAIRED_TILE_REFRESH_REVIEW_2026-09-07.md](V16_WEAR_PAIRED_TILE_REFRESH_REVIEW_2026-09-07.md)。现有三个新
Tile 属于旧 Release 包；由于槽位已满且不得删除旧 Tile，本轮未能添加最新 Debug 的三项真实配对实例。
Complication 逐槽位与活动通知取消也仍待补，因此 v1.6 继续保持 `IN_PROGRESS`。
