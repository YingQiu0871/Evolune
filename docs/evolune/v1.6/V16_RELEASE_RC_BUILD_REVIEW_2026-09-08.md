# v1.6.0 正式签名候选包构建复审（2026-09-08）

> 2026-09-09 更新：候选目录 `20260909-202609`（Wear SHA-256 `B346F1...`）已完成横向操作弹窗的
> 模拟器与实体手表复验，但实体手表仍显示 Tile 面板切平痕迹。后续源码已改为 `156dp` 安全宽度、统一
> `24dp` 圆角和资源版本 7，并通过隔离圆屏模拟器。由于该修改尚未生成新的正式签名候选包，本文件所列
> 旧候选包均不再具备发布资格。详见 `V16_WEAR_ROUND_SAFE_RESOURCE7_REVIEW_2026-09-09.md`。

> 2026-09-09 21:15 更新：包含资源版本 7 的新正式候选目录为 `20260909-210641`，Wear SHA-256
> `4C005626...F47FBE`。该包已在实体 Galaxy Watch 保留数据覆盖，设备拉回哈希一致，已有 Tile 实例与顺序
> 保留；“当前 E2”和“下一次服药”实机圆角/边缘复验通过。“今日计划”真表添加及三个 Complication 的完整
> 设备证据仍未关闭，因此整版仍未进入最终发布。

> 同一候选包的 Phone APK（SHA-256 `7A2483...DA15`）也已在实体 Pixel 11 Pro 使用 `adb install -r`
> 覆盖。`firstInstallTime=2026-09-02 19:15:43` 保持不变，设备拉回哈希一致，冷启动 PID 存在且无
> FATAL/ANR 命中。系统仍注册 4 个独立 Widget provider，并保留 4 个已有宿主实例。

## 结论

包含圆屏底板修复的第二份正式签名 Release Candidate 已构建完成，并通过 Phone/Wear 模拟器及实体
Pixel 11 Pro 的保留数据覆盖安装。Phone Widget 与 Wear Tile 的系统发现和运行态主要路径已取得设备证据。
第二份 Wear 候选包尚未完成实体 Galaxy Watch 覆盖复验：2026-09-09 重连时手表 TLS 返回
`SSLV3_ALERT_CERTIFICATE_UNKNOWN`，需要使用手表当前显示的配对码重新登记本机证书。当前仍不满足公开发布或
`DONE` 条件：必须完成实体手表圆屏边缘复验，并补齐真实配对链路中的确认用药、跳过本次及三个
Complication 选择器证据。

## 构建身份

- 分支：`main`
- HEAD：`df12329278eafa488713edf202554cdbd523b8d0`
- variant：`release`
- applicationId：`io.github.yingqiu0871.evolune`
- Phone：`versionName=1.6.0`，`versionCode=101060000`
- Wear：`versionName=1.6.0`，`versionCode=1101060000`
- 签名证书 SHA-256：`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`
- 工作树包含未提交的 v1.6 修改；候选包对应上面 HEAD 加构建时记录的工作树状态，不能只用提交号复现。

## 最新候选产物（包含圆屏底板修复）

- 构建目录：`release-artifacts/v1.6.0-rc/20260908-211446`
- Phone：`Evolune-Phone-v1.6.0-RC.apk`
  - SHA-256：`A039DDC4B788A55DA2D6F6C938337F177D2FD7380EBBE84522DFB6A5183C30D5`
- Wear：`Evolune-Wear-v1.6.0-RC.apk`
  - SHA-256：`EA730F269DC33955911A980265C4FDC47A0FCAD63DA811D41AB6A132EFF2BE1F`

两份 APK 均通过 `apksigner verify`，签名证书 SHA-256 均为
`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`。版本号、构建 variant、工作树状态、
签名报告和设备拉回哈希均保存在该目录的构建与验证日志中。

## 上一份候选产物（实体手表问题复现基线）

- Phone：`release-artifacts/v1.6.0-rc/20260908-110248/Evolune-Phone-v1.6.0-RC.apk`
  - SHA-256：`2C46DBCF7FC0A495DB7B459973C300C5349C863AB4831D0C84EB9AB6B8FDF0F4`
- Wear：`release-artifacts/v1.6.0-rc/20260908-110248/Evolune-Wear-v1.6.0-RC.apk`
  - SHA-256：`FD357CCBBF85D8083561B2BB8052F921C9D51FAA9B13523E875BD1DEFDE2BD1E`

本机重新计算的 SHA-256 与 `SHA256SUMS.txt`、`BUILD-IDENTITY.txt` 一致。Phone 与 Wear 均通过
`apksigner verify`，均使用同一证书，并通过与上一份 v1.6 正式签名包的证书兼容检查。

## 自动检查与打包结果

最新候选构建执行了 experience-core、Phone 和 Wear JVM 测试、Phone/Wear Release Lint 以及两个 Release
APK 构建。Gradle 结果为 `BUILD SUCCESSFUL in 2m44s`，152 个任务全部执行。测试结果合计 857/857 通过：
experience-core 84、Phone 684、Wear 89，0 failures、0 errors、0 skipped。输出中的 SDK 元数据重复、
弃用 API 和无法剥离部分原生库均为警告，没有导致任务失败；Release Lint 报告已生成。

## APK 静态审计

- Phone manifest 包含配置 Activity，以及 4 个独立 Widget receiver：今日计划、下一次服药、当前 E2、
  E2 趋势；每个 receiver 均带 `android.appwidget.provider` metadata。
- Wear manifest 包含保留组件身份的旧 `DoseTileService`、规格要求的 3 个新 Tile、3 个 Complication；
  Tile 服务包含绑定 action、预览 metadata 和品牌图标引用。
- 静态审计证明组件已进入最终 APK，但不能替代系统选择器发现、添加、显示、刷新和点击的设备证据。

## 待补验收

1. 重新配对实体手表的 ADB 证书；当前网络端口可达，但 TLS 明确拒绝旧证书。
2. 在实体手表用 `adb install -r` 保留数据覆盖最新 Wear 包，确认资源版本 6 已刷新已有 Tile。
3. 复验“当前 E2”“下一次服药”“今日计划”的 168dp 圆屏安全宽度和统一 20dp 圆角。
4. 补齐 4 个 Phone Widget 的多实例、尺寸变化和数据刷新证据。
5. 在支持相应类型的表盘槽位复验 3 个 Complication。
6. 对 Wear 确认用药与跳过本次执行真实配对链路，确认 Phone 权威 occurrence 结果回传。
7. 完成独立最终复审后，再判断是否公开发布。

## Phone Release RC 模拟器复验（2026-09-08）

目标设备为 Pixel Fold AVD `emulator-5558`。使用 `adb install -r` 覆盖安装成功，没有卸载或清数据：
`firstInstallTime` 保持 `2026-09-01 16:10:55`，`lastUpdateTime` 更新为 `2026-09-08 09:12:38`。
从设备拉回的 `base.apk` SHA-256 为
`2C46DBCF7FC0A495DB7B459973C300C5349C863AB4831D0C84EB9AB6B8FDF0F4`，与本地候选包一致。

为了防止同名 Debug 包污染系统选择器，临时将 `io.github.yingqiu0871.evolune.debug` 设置为
`disabled-user`；没有卸载或删除其数据。正式包冷启动成功，日志中没有 `FATAL EXCEPTION` 或 ANR。

Pixel Launcher 的真实 Widget 选择器显示 Evolune 有 4 个微件，并逐项显示：

- `Evolune-今日计划`
- `Evolune-下一次服药`
- `Evolune-当前 E2`
- `Evolune-E2 趋势`

四个入口均有实际预览。E2 趋势预览中的柱体从 X 轴向上延伸，历史/预测颜色可区分，
`-24h`、`现在`、`+24h` 分布在 X 轴左、中、右位置。

实际添加“下一次服药”时进入“微件外观”，预览中的左右文字与药名/时间可见，显示模式、Material You
与预制配色可选。取消配置后，Launcher 分配的待定实例从 1 变为 0，证明取消没有留下默认实例；再次添加并
点击应用后，正式 provider 实例数变为 1。桌面实际显示 `Test Plan` 和 `23:20`，点击微件会打开正式包
`MainActivity`。

运行证据：

- `V16_PHONE_RC_EMU5558_LAUNCH_20260908.png`
- `V16_PHONE_RC_WIDGET_PICKER_20260908.png`
- `V16_PHONE_RC_EVOLUNE_4_WIDGETS_20260908.png`
- `V16_PHONE_RC_EVOLUNE_CHART_PREVIEW_20260908.png`
- `V16_PHONE_RC_WIDGET_CONFIG_ENTRY_20260908.png`
- `V16_PHONE_RC_NEXT_DOSE_APPLIED_20260908.png`

Phone Release RC 的系统发现、配置取消、配置应用、实际显示和点击已通过模拟器复验。多实例不同配色、
尺寸变化后的排版、数据变化后的刷新以及全部四类实例的运行态显示仍需继续补证。

## Wear Release RC 模拟器复验（2026-09-08）

ADB 主机密钥状态恢复后，`Wear_OS_Large_Round`（`emulator-5554`）变为 `device`。使用 `adb install -r`
覆盖安装成功，没有卸载、清数据或移除旧 Tile：`firstInstallTime` 保持 `2026-09-06 11:51:08`，
`lastUpdateTime` 更新为 `2026-09-08 09:27:36`。从设备拉回的 `base.apk` SHA-256 为
`FD357CCBBF85D8083561B2BB8052F921C9D51FAA9B13523E875BD1DEFDE2BD1E`，与 Wear 候选包一致。正式包冷启动
成功，日志中没有 `FATAL EXCEPTION` 或 ANR。

系统实际枚举并运行了三个新 Tile：`Evolune-今日计划`、`Evolune-当前 E2`、`Evolune-下一次服药`。三个
Tile 均显示居中的 Monet 配色卡片和明确离线/同步提示，没有纯黑运行态。系统添加列表还显示了保留组件身份
的 `Evolune-E2 浓度曲线`；其预览和添加后运行态均为真实曲线、坐标轴与目标范围色带，没有旧 HRT 加号或
整块曲线底板。已有“当前 E2”表盘 Complication 在覆盖升级后保留并显示离线占位。

运行证据：

- `V16_WEAR_RC_EMU5554_LAUNCH_20260908.png`
- `V16_WEAR_RC_WATCHFACE_20260908.png`
- `V16_WEAR_RC_TILE_EDIT_ENTRY_20260908.png`
- `V16_WEAR_RC_TILE_ADD_LIST_20260908.png`
- `V16_WEAR_RC_LEGACY_CURVE_TILE_ADDED_20260908.png`
- `V16_WEAR_RC_LEGACY_CURVE_TILE_RUNTIME_20260908.png`

Pixel Fold 与 Wear AVD 的 Data Layer 配对尚未建立。Pixel Watch 配套应用的专用
`EmulatorActivity` 能进入 `EMULATOR_PAIRING:STARTED`，接受服务条款后进入连接页，但由于 Android Studio
配对助手没有登记该会话而返回 `EMULATOR_NOT_FOUND`。这不影响上述离线渲染证据，但实时同步、确认用药与
跳过本次仍未在模拟器配对链路通过。

## 实体 Pixel 11 Pro 覆盖升级（2026-09-08）

目标设备为 Pixel 11 Pro。覆盖前正式包为 `versionName=1.6.0`、`versionCode=101060000`，签名证书 SHA-256
与候选包同为 `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`。使用
`adb install -r` 覆盖成功，`firstInstallTime` 保持 `2026-09-02 19:15:43`，`lastUpdateTime` 更新为
`2026-09-08 11:57:11`。从设备拉回的 APK SHA-256 为
`2C46DBCF7FC0A495DB7B459973C300C5349C863AB4831D0C84EB9AB6B8FDF0F4`，与候选包完全一致。

正式包冷启动进入 `MainActivity`，没有 `FATAL EXCEPTION` 或 ANR。系统仍注册四个独立 Widget provider；
原有六个 Evolune Widget 实例在覆盖升级后仍由 Pixel Launcher 持有。手机连续记录
`HRTWearDataLayer: Synced 2 plan(s) to Wear OS`，证明候选包能向实体手机的 Wear 数据层写入计划；该日志
不能单独证明实体手表已安装本次 Wear RC 或已执行交互。

## 实体 Galaxy Watch 覆盖升级（2026-09-08）

实体 Galaxy Watch8 Classic（`SM_L500`）已通过 ADB 连接。覆盖前正式包为 `versionName=1.6.0`、
`versionCode=1101060000`，签名证书与候选包一致。使用 `adb install -r` 覆盖成功，没有卸载、清数据、
删除旧 Tile 或重置手表；`firstInstallTime` 保持 `2026-08-22 12:28:12`，`lastUpdateTime` 更新为
`2026-09-08 12:02:54`。从设备拉回的 APK SHA-256 为
`FD357CCBBF85D8083561B2BB8052F921C9D51FAA9B13523E875BD1DEFDE2BD1E`，与当时 Wear RC 完全一致。

手表 App 显示 `已同步`、真实 E2 数值和更新时间。系统添加列表能发现 Evolune 服务，品牌图标为月牙；已有
旧曲线 Tile、当前 E2 Tile 和下一次服药 Tile 均在覆盖升级后保留并能显示数据。实体运行截图包括：

- `V16_WEAR_PHYSICAL_RC_LAUNCH_20260908.png`
- `V16_WEAR_PHYSICAL_RC_TILE_EVOLUNE_EXPANDED_20260908.png`
- `V16_WEAR_PHYSICAL_RC_TILE_EVOLUNE_REMAINING_20260908.png`
- `V16_WEAR_PHYSICAL_RC_NEXT_DOSE_CORNER_REAL_20260908.png`
- `V16_WEAR_PHYSICAL_RC_CURRENT_E2_CORNER_REAL_20260908.png`

这些实机截图同时暴露出内容底板在 Galaxy Watch 圆屏上过宽、圆角偏大的缺陷，因此只能证明旧 RC 的安装、
发现和数据链路，不能作为修复后视觉验收。

## 圆屏底板边缘修复与 Debug 复验（2026-09-08）

三个新 Tile 原先使用固定 `184dp × 88dp` 内容底板及 `28dp` 圆角；在不同手表密度和圆形安全区内，左右
边缘过于接近屏幕边界，并可能与宿主缓存的旧布局叠加产生被裁切的观感。修复将三个新 Tile 的底板宽度统一
为 `168dp`，将 Wear App、旧曲线 Tile 操作块和三个新 Tile 的卡片圆角统一为 `20dp`，并把新 Tile
资源版本提升到 `6`，使覆盖安装后的已有实例重新请求布局。Debug provider 使用独立标签，避免与正式 provider
混淆；Release 名称仍保持 `Evolune-XXX`。

Wear JVM 测试、Debug APK 和 Debug AndroidTest APK 已全部重新执行，Gradle 结果为
`BUILD SUCCESSFUL in 32s`，69 个任务全部执行。隔离 Wear 模拟器上的只读夹具成功写入示例数据；实际添加的
Debug“下一次服药”Tile 中，彩色底板边界为 `[59,158][395,334]`，在 454px 圆屏左右各保留约 59px，
四个圆角完整。其外层主槽边界仍为 `[46,158][408,334]`，说明新增留白来自生产内容容器收窄，而不是截图
裁剪或宿主缩放。当前 E2 使用同一 `tonalPanelContainer`，系统预览同样显示完整圆角。

证据：

- `V16_WEAR_EMU_DEBUG_ROUND_SAFE_ACTUAL_20260908.png`
- `V16_WEAR_EMU_DEBUG_ROUND_SAFE_ACTUAL_20260908.xml`
- `V16_WEAR_EMU_DEBUG_CURRENT_PICKER_20260908.png`
- `V16_WEAR_EMU_DEBUG_ADD_GRID_20260908.xml`

该修复已进入 SHA 为 `EA730F...` 的最新正式 Wear RC，并在 454×454 圆屏模拟器完成签名 Release 覆盖复验：
已有正式 Tile 实例保留，三个新服务重新渲染，内容底板边界保持 `[59,158][395,334]`，圆角完整。实体
Galaxy Watch 仍需在恢复 ADB 配对后覆盖复验，才能关闭此视觉缺陷。

## 最新正式候选设备覆盖记录（2026-09-08 至 2026-09-09）

最新 Phone 候选包已在实体 Pixel 11 Pro 使用 `adb install -r` 覆盖。`firstInstallTime` 保持
`2026-09-02 19:15:43`，设备拉回 APK 的 SHA-256 与候选包 `A039DD...` 一致；四个 Widget provider 和旧实例
仍保留，冷启动无崩溃关键词。

最新 Wear 候选包已在 `emulator-5554` 使用 `adb install -r` 覆盖。`firstInstallTime` 保持
`2026-09-06 11:51:08`，设备拉回 APK 的 SHA-256 与候选包 `EA730F...` 一致。已有正式“当前 E2”和
“下一次服药”实例在资源版本 6 下重新渲染，底板边界为 `[59,158][395,334]`，圆角完整。证据为：

- `V16_WEAR_EMU_LATEST_RC_CURRENT_E2_20260908.png`
- `V16_WEAR_EMU_LATEST_RC_CURRENT_E2_20260908.xml`
- `V16_WEAR_EMU_LATEST_RC_NEXT_DOSE_STABLE_20260908.png`
- `V16_WEAR_EMU_LATEST_RC_NEXT_DOSE_20260908.xml`

2026-09-09 实体 Galaxy Watch 广播地址为 `192.168.31.98:40353`，TCP 可达；ADB TLS 握手返回
`SSLV3_ALERT_CERTIFICATE_UNKNOWN`。因此最新候选包尚未写入手表，覆盖前设备仍是 SHA 为 `FD357...` 的旧
候选包，`firstInstallTime=2026-08-22 12:28:12`。此项明确记录为未验证，不能用模拟器结果替代。
