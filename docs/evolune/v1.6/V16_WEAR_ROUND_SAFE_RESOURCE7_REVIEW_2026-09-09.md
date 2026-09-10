# v1.6 Wear Tile 圆屏安全区复审（资源版本 7，2026-09-09）

## 结论

实体 Galaxy Watch 对正式候选包 `B346F1...` 的复查显示，“当前 E2”和“下一次服药”面板虽然没有丢失
文字，但 `168dp` 固定宽度在 206dp 宽的实机逻辑视口上只留下约 19dp 单侧余量，配合 `20dp` 圆角会形成
明显的切平/削角观感。此前“资源版本 6 已彻底解决圆角”的结论被实机证据否定。

本轮将三个新 Tile 的公共面板宽度收窄到 `156dp`，把 Wear App、旧曲线 Tile 和三个新 Tile 共用的圆角
令牌统一到 `24dp`，并把 Tile 资源版本提升到 `7`，保证覆盖升级后的已有实例重新请求布局。当前结论为
`SIGNED_RELEASE_PHYSICAL_ACTIVE_TILES_PASS / TODAY_PLAN_PHYSICAL_ADD_REQUIRED`。已有“当前 E2”和“下一次服药”
实机实例通过；“今日计划”使用同一生产容器并通过模拟器，但尚未在实体手表添加，不能把共同代码路径替代
实体添加证据。

## 源码和回归约束

- `WearGalleryTileService.kt`：面板宽度 `156dp`；使用公共 `24dp` 圆角；资源版本 `7`。
- `WearAppearance.kt`：Wear 全局卡片圆角统一为 `24dp`。
- `WearGalleryTilePolicyTest.kt`：锁定资源版本、面板宽度和圆角。
- `WearAppearancePolicyTest.kt`：锁定 Wear App、旧 Tile 和三个新 Tile 共用圆角令牌。

Wear JVM 测试结果为 `90/90` 通过，0 failures、0 errors、0 skipped。Debug APK、Debug AndroidTest APK
和 Debug Lint 完整复跑：`BUILD SUCCESSFUL in 46s`，78 个任务全部执行。原始日志：
`V16_WEAR_ROUND_SAFE_RESOURCE7_BUILD_20260909.log`。

## 454×454 圆屏模拟器运行证据

在隔离 AVD `evolune-v16-wear-dialog`（`emulator-5560`）使用 `adb install -r` 覆盖 Debug 包，没有卸载或
清除应用数据。只读夹具提供 READY 状态的 E2=153.2 pg/mL 和三条示例计划。为了只验证渲染，在该隔离
模拟器的 Tile 数据库中加入三个 Debug provider；这项测试操作没有作用于实体手表。

三个服务都被系统请求并成功渲染：

- `CURRENT_E2 state=READY`
- `NEXT_DOSE state=READY`
- `TODAY_PLAN state=READY`

运行截图中，156dp 面板在 320dpi、454px 屏幕上宽约 312px，左右各留约 71px。两端圆角连续，未与圆形
视口边界相交，标题、主值和次要文字保持居中：

- `V16_WEAR_EMU_RESOURCE7_CURRENT_E2_20260909.png`
- `V16_WEAR_EMU_RESOURCE7_NEXT_DOSE_20260909.png`
- `V16_WEAR_EMU_RESOURCE7_TODAY_PLAN_20260909.png`
- `V16_WEAR_EMU_RESOURCE7_TILE_LOGCAT_20260909.log`
- `V16_WEAR_EMU_RESOURCE7_WEAR_SERVICE_20260909.log`
- `V16_WEAR_EMU_RESOURCE7_PIXEL_AUDIT_20260909.log`

像素审计测得“当前 E2”和“下一次服药”面板边界均为 `(71,158)..(382,333)`，“今日计划”为
`(71,134)..(382,357)`；三者中线左右留白均为 71px，证明面板关于屏幕中心对称且没有接触圆屏边缘。

## 正式签名候选包和实体手表复验

上一份正式候选包目录为 `release-artifacts/v1.6.0-rc/20260909-202609`，Wear SHA-256 为
`B346F1E2C111CD8CD4D9ED16F9984D54558A6CA5EF6F84D7C0C3E32864E8ABF3`。它包含横向“跳过本次 / 确认用药”
弹窗修复，但不包含本轮 156dp、24dp、资源版本 7 的最终圆屏修复，因此已被本轮源码取代，不能作为发布包。

新候选包目录为 `release-artifacts/v1.6.0-rc/20260909-210641`。完整脚本执行 152 个任务，结果为
`BUILD SUCCESSFUL in 2m34s`；JVM 测试合计 858/858 通过（experience-core 84、Phone 684、Wear 90），
0 failures、0 errors、0 skipped。Phone/Wear 均通过 APK Signature Scheme v2 验签，签名证书 SHA-256 为
`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`：

- Phone SHA-256：`7A2483B8C74FA51B74E638FBB04BE139B987AC7165471E89527020C8B312DA15`
- Wear SHA-256：`4C0056266665710009A25AE749667395C02C7A2E79164BEDE38933F84EF47FBE`

实体 Galaxy Watch `SM_L500` 使用 `adb install -r` 覆盖成功，没有卸载、清数据或移除旧 Tile。
`firstInstallTime` 保持 `2026-08-22 12:28:12`，`lastUpdateTime` 更新为 `2026-09-09 21:15:25`；从设备
拉回 APK 的 SHA-256 为 `4C005626...F47FBE`，与候选包完全一致。已有 Tile 34、37、38 和系统 25 项顺序
仍保留。

系统日志确认资源更新后重新请求 `CURRENT_E2 state=READY` 和 `NEXT_DOSE state=READY`，随后焦点准确落在
Tile 37 和 38。两张 438×438 实机截图的面板精确色块边界均为 `(53,145)..(384,331)`，中线左右留白
均为 53px；四角连续、对称，未触碰圆屏边缘：

- `V16_WEAR_PHYSICAL_RESOURCE7_CURRENT_E2_20260909.png`
- `V16_WEAR_PHYSICAL_RESOURCE7_NEXT_DOSE_20260909.png`
- `release-artifacts/v1.6.0-rc/20260909-210641/device-verification/galaxy-watch/PHYSICAL-RESOURCE7-PIXEL-AUDIT.log`
- `release-artifacts/v1.6.0-rc/20260909-210641/device-verification/galaxy-watch/LOGCAT-TILE-TRAVERSAL.log`

圆角/边缘缺陷对已有“当前 E2”和“下一次服药”实机实例已关闭。“今日计划”尚未在真表添加；整个
v1.6 仍需完成该入口和三个 Complication 的设备证据，不能据此执行最终 `DONE`。

实体手表当前已有 25 个 Tile，达到 Samsung 宿主上限。系统仍枚举 `Evolune-今日计划`，但“添加卡片”页
无法继续进入选择器；按照升级验证边界，没有删除用户旧 Tile 来腾位置。因此 Today Plan 真表添加被明确
标为 `BLOCKED_BY_HOST_CAPACITY`，模拟器 READY 渲染证据保持独立，不冒充实体通过。
