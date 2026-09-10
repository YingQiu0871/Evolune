# v1.6 Wear 圆屏底板复审（2026-09-08）

> 2026-09-09 更新：实体手表再次观察到资源版本 6 的 `168dp/20dp` 面板仍有切平痕迹。本文件中的
> “圆角完整”结论已被后续实机证据取代。当前修复、模拟器证据和剩余实机复验见
> `V16_WEAR_ROUND_SAFE_RESOURCE7_REVIEW_2026-09-09.md`。

## 结论

实体 Galaxy Watch 反馈的“当前 E2”和“下一次服药”彩色底板圆角、左右边缘问题已在源码和 Wear 模拟器
Debug 产物中修复。三个新 Tile 使用同一内容容器，宽度从 184dp 收窄为 168dp；Wear App、旧曲线 Tile
操作块与三个新 Tile 的卡片圆角统一为 20dp。新 Tile 资源版本提升到 6，避免覆盖升级后继续显示宿主缓存的
旧布局。

当前结论为 `SIGNED_RELEASE_EMULATOR_PASS / PHYSICAL_REVERIFY_REQUIRED`。最新正式 RC 已包含本轮修改并通过
圆屏模拟器覆盖复验；实体 Galaxy Watch 尚未完成最新包覆盖，不能发布。

## 代码与回归检查

- `WearGalleryTileService.kt`：统一 `168dp` 圆屏安全宽度、`20dp` 圆角、资源版本 6。
- `WearAppearance.kt`：提供 Wear 全局卡片圆角常量。
- `WearAppActivity.kt`：页面卡片和操作对话框使用相同圆角。
- `DoseTileService.kt`：旧曲线 Tile 的操作块使用相同圆角。
- `WearGalleryTilePolicyTest.kt`：锁定资源版本、宽度与圆角。
- `WearAppearancePolicyTest.kt`：锁定 Wear App 与 Tile 共用圆角。
- `wear/src/debug/res/values/strings.xml`：只在 Debug 中区分 provider 标签，避免模拟器把正式与 Debug 服务混为
  同一项；Release 名称不变。

最终复跑命令覆盖 Wear JVM、Debug APK 与 Debug AndroidTest APK，结果为 `BUILD SUCCESSFUL in 32s`，
69 个任务全部执行。只有 Android SDK XML 版本和弃用 API 警告，无测试或构建失败。

## 模拟器运行证据

在 `Wear_OS_Large_Round` 454×454 圆屏模拟器中保留旧正式 Tile，并另行添加 Debug provider。只读测试夹具
生成 E2=153.2 pg/mL 和三条示例计划，确保验证真实内容态而非空状态。

“下一次服药”实际 Debug Tile 的彩色底板边界为 `[59,158][395,334]`，左右各约 59px；外层主槽为
`[46,158][408,334]`。四个圆角完整、文字居中，未与圆屏边界相交：

- `V16_WEAR_EMU_DEBUG_ROUND_SAFE_ACTUAL_20260908.png`
- `V16_WEAR_EMU_DEBUG_ROUND_SAFE_ACTUAL_20260908.xml`

“当前 E2”在系统 provider 预览中使用同一容器并显示完整圆角：

- `V16_WEAR_EMU_DEBUG_CURRENT_PICKER_20260908.png`
- `V16_WEAR_EMU_DEBUG_CURRENT_ADD_GRID_20260908.xml`

## 正式签名候选包与模拟器复验

包含本修复的 Wear 正式候选包为：

- `release-artifacts/v1.6.0-rc/20260908-211446/Evolune-Wear-v1.6.0-RC.apk`
- SHA-256：`EA730F269DC33955911A980265C4FDC47A0FCAD63DA811D41AB6A132EFF2BE1F`
- 签名证书 SHA-256：`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

在 `emulator-5554` 保留数据覆盖安装后，设备拉回 APK 哈希一致，旧正式 Tile 实例保留并以资源版本 6
重新渲染。“当前 E2”和“下一次服药”运行态底板均为 `[59,158][395,334]`，圆角完整。证据：

- `V16_WEAR_EMU_LATEST_RC_CURRENT_E2_20260908.png`
- `V16_WEAR_EMU_LATEST_RC_CURRENT_E2_20260908.xml`
- `V16_WEAR_EMU_LATEST_RC_NEXT_DOSE_STABLE_20260908.png`
- `V16_WEAR_EMU_LATEST_RC_NEXT_DOSE_20260908.xml`

## 未关闭项

1. 在实体 Galaxy Watch 上恢复 ADB 证书配对，然后执行 `adb install -r`，不得卸载或清数据。
2. 验证旧 Tile 实例保留，且“当前 E2”“下一次服药”“今日计划”刷新为资源版本 6 的安全宽度。
3. 保存实机修复后截图；完成前 v1.6 最终验收保持未通过。

2026-09-09 已确认手表的无线调试 TCP 端口可达，但 TLS 返回
`SSLV3_ALERT_CERTIFICATE_UNKNOWN`，说明手表已不再信任本机当前 ADB 证书。重新配对前未执行覆盖安装，
因此不能把旧候选包的实体截图误列为本轮修复证据。
