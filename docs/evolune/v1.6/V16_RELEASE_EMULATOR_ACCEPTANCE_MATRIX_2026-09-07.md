# v1.6.0 Release 模拟器验收矩阵（2026-09-07）

本矩阵只记录当前 Release 制品在 `emulator-5558`（Pixel Fold Phone）和
`emulator-5554`（Wear OS）上的新鲜证据。`PASS` 表示该列已有对应证据；`OPEN`
表示仍需真实数据、点击回执或设备证据，不能用静态代码替代。

## Phone Widget

| 功能 | 规格/生产入口 | Manifest/打包 | 系统发现 | 添加/配置 | 实际显示 | 刷新/点击 | 升级兼容 |
|---|---|---|---|---|---|---|---|
| 今日计划 | `EvoluneWidgetReceiver` / `widget_evolune*` | PASS | PASS | PASS（Release provider） | PASS（无启用方案明确占位；随后建立 Release 测试方案） | OPEN（需对 Widget 本体执行真实计划刷新/点击） | OPEN（保留旧实例，需完整重测） |
| 下一次服药 | `NextDoseWidgetReceiver` / `widget_evolune_wide` | PASS | PASS | PASS（Picker 预览） | OPEN（需单独添加运行态） | OPEN | OPEN |
| 当前 E2 | `CurrentE2WidgetReceiver` / `widget_evolune_compact` | PASS | PASS | PASS（Picker 预览） | OPEN（需单独添加运行态） | OPEN | OPEN |
| E2 趋势 | `PkChartWidgetReceiver` / `widget_evolune_expanded` | PASS | PASS | PASS（Picker 预览；柱体从 X 轴向上） | OPEN（需单独添加运行态） | OPEN | OPEN |
| 今日进度 | 已按冻结设计取消独立 provider，与今日计划合并 | N/A | N/A | N/A | N/A | N/A | N/A |

Phone 证据：`V16_RELEASE_PHONE_EVOLUNE_WIDGETS_2026-09-07.png`、
`V16_RELEASE_PHONE_EVOLUNE_WIDGETS_UI_SCROLL_2026-09-07.xml`、
`V16_RELEASE_PHONE_TODAY_PLAN_WIDGET_RUNTIME_2026-09-07.png`。

## Wear Tile

| 功能 | 注册/预览 | 系统发现 | 添加 | 实际显示 | 刷新/点击 | 升级兼容 |
|---|---|---|---|---|---|---|
| 下一次服药 | `NextDoseTileService` + Release preview | PASS | PASS | PASS（同步占位卡） | PASS（Release 测试 occurrence 已由 Phone 同步到 Wear App；Tile 的同一快照刷新已有日志） | OPEN（旧实例矩阵） |
| 今日计划 | `TodayPlanTileService` + Release preview | PASS | PASS | PASS（无待服明确占位卡） | PASS（Phone 日志 `Synced 1 plan(s)`；Wear `snapshot result=Applied`，TodayPlan Tile 请求后为 READY） | OPEN |
| 当前 E2 | `CurrentE2TileService` + Release preview | PASS | PASS | PASS（E2 不可用明确提示） | OPEN（需手机浓度快照） | OPEN |
| 旧 E2 曲线兼容 | `DoseTileService` 身份保留 | PASS | 已有实例保留 | PASS（旧曲线仍可打开） | OPEN | PASS（未删除） |

证据：`V16_WEAR_TILE_ADD_LIST_4_2026-09-07.png`、
`V16_WEAR_TILE_ADD_LIST_TODAY3_2026-09-07.png`、
`V16_WEAR_NEXT_DOSE_RUNTIME_2026-09-07.png`、
`V16_WEAR_TODAY_PLAN_RUNTIME_2026-09-07.png`（当前 E2 运行态）、
`V16_WEAR_TODAY_PLAN_RUNTIME_3_2026-09-07.png`、
`V16_RELEASE_WEAR_APP_RUNTIME_2026-09-07.png`。

## Complication

| Provider | 注册/类型/图标 | 选择器发现 | 展示 | 刷新/点击 | 状态覆盖 |
|---|---|---|---|---|---|
| 下一次服药 | `NextDoseComplicationDataSource` | PASS（chooser 可见 Evolune） | OPEN（需支持槽位） | OPEN | OPEN |
| 今日完成度 | `TodayProgressComplicationDataSource` | PASS（同上） | OPEN | OPEN | OPEN |
| 当前 E2 | `CurrentE2ComplicationDataSource` | PASS（同上） | OPEN | OPEN | OPEN |

选择器证据：`V16_RELEASE_WEAR_COMPLICATION_CHOOSER_UI_2026-09-07.xml`。

## Release Phone → Wear 真实数据链路（2026-09-08）

- 在 `emulator-5558` 的 Release Phone 应用中完成首次引导，并新建仅用于模拟器验收的 `Test Plan`（口服、EV、1.0 mg、每天）。保存后方案列表显示该计划；Phone 日志记录 `Synced 1 plan(s) to Wear OS`。
- `emulator-5554` Release Wear 应用记录 `snapshot result=Applied`，并显示 `Test Plan · 1.00 mg / 09-08 22:05 · 待服`。
- 向上滚动到操作区后，旧 Release 交互中的“确认服药”和“跳过本次”均完整可见、可点击。这证明此前“没有显示按钮”的截图停留在可滚动页面的上部，并非 Release 包缺少按钮或跳过协议。随后工作树已按视觉复核改为“点按整条记录 → 操作面板”，面板底部为左侧“跳过本次”、右侧“确认用药”；该变更需进入下一份签名 Release 后复核。
- 已点击模拟器测试 occurrence 的“跳过本次”；Wear 显示发送提示，但该 occurrence 当时没有对应的 Release 系统通知（方案时间已过且下一次闹钟排到后续日期），因此本轮不能把“手机通知被取消”记为通过。必须在通知真实存在时复测取消结果。

证据：`V16_RELEASE_PHONE_TEST_PLAN_LIST_2026-09-08.png`、
`V16_RELEASE_PHONE_TEST_PLAN_LIST_UI_2026-09-08.xml`、
`V16_RELEASE_WEAR_TEST_PLAN_SCROLLED_2026-09-08.png`、
`V16_RELEASE_WEAR_ACTIONS_VISIBLE_2026-09-08.png`、
`V16_RELEASE_WEAR_ACTIONS_VISIBLE_UI_2026-09-08.xml`、
`V16_RELEASE_PHONE_WEAR_SYNC_LOG_2026-09-08.log`。

## 结论

- Release APK 的构建、签名、覆盖安装、Phone Widget Picker、Wear Tile 添加页和空数据运行态均已有模拟器证据。
- Release Phone→Wear 的启用方案、待服 occurrence、应用快照和两个操作按钮现已有模拟器证据。跳过请求的通知取消结果、确认操作的权威回执、Complication 支持槽位和 READY/STALE/失败状态仍为 `OPEN`。
- JVM fresh rerun 已于 2026-09-08 通过：`V16_POST_RELEASE_JVM_FRESH_20260908.log`，54 个任务全部执行。其余设备与真实数据证据完成前仍不能宣告最终验收或 DONE。
