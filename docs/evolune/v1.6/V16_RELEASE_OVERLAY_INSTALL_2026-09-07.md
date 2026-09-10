# v1.6.0 Release 覆盖安装证据（2026-09-07）

## 构建身份

- branch: `main`
- HEAD: `df12329278eafa488713edf202554cdbd523b8d0`
- worktree: `DIRTY`（保留既有未提交改动）
- versionName: `1.6.0`
- Phone versionCode: `101060000`
- Wear versionCode: `1101060000`
- applicationId: `io.github.yingqiu0871.evolune`
- signer certificate SHA-256: `b9b6b9552fa4c7b656936d4c3aeb71c1229aa17c393337719bc8d0e07edaab08`
- build log: `V16_RELEASE_BUILD_20260907-213750.log`
- 该构建日志包含 `:experience-core:test`、`:wear:testDebugUnitTest`、`:app:testDebugUnitTest`、Phone/Wear `lintRelease` 和 `assembleRelease`，结果为 `BUILD SUCCESSFUL`（152 actionable tasks）。

## 正式制品

- Phone: `release-artifacts/v1.6.0/Evolune-Phone-v1.6.0.apk`
  - SHA-256: `B5FDA6EBFB2A7E87A0A5C687386515F9B8AF9F2920C5350CBB498573366EC9D9`
- Wear: `release-artifacts/v1.6.0/Evolune-Wear-v1.6.0.apk`
  - SHA-256: `33446E6592EAAA9A5EF0F64C76DD2D6F4983BCF96427F4C7CFBB80F679A022F7`

## 保留数据覆盖安装

- `emulator-5558`（Phone Pixel Fold）：`adb install -r` 成功。
- `emulator-5554`（Wear OS）：`adb install -r` 成功。
- 未卸载应用、未清除应用数据、未移除已有 Widget/Tile、未重置设备。
- Phone 设备端 APK SHA-256 与本地产物一致（大小写归一后）：
  `b5fda6ebfb2a7e87a0a5c687386515f9b8af9f2920c5350cbb498573366ec9d9`
- Wear 设备端 APK SHA-256 与本地产物一致（大小写归一后）：
  `33446e6592eaaa9a5ef0f64c76dd2d6f4983bcf96427f4c7cfbb80f679a022f7`
- 两台设备的 applicationId、versionName 和对应 versionCode 均匹配。

## 启动检查

- Phone Activity 启动并显示：`V16_RELEASE_PHONE_RUNTIME_2026-09-07.png`。
- Wear Activity 启动并显示：`V16_RELEASE_WEAR_RUNTIME_2026-09-07.png`。
- 精确扫描 `FATAL EXCEPTION`、`ANR in`、`Process ... has died`、`RuntimeException`、类加载/资源错误：Phone 0，Wear 0。
- 原始日志：
  - `V16_RELEASE_emulator-5558_LAUNCH_LOG_2026-09-07.txt`
  - `V16_RELEASE_emulator-5554_LAUNCH_LOG_2026-09-07.txt`

## 系统入口发现

- Phone Widget Picker 在 Release 覆盖安装后显示 `Evolune` 的 4 个 Widget：
  `Evolune-今日计划`、`Evolune-下一次服药`、`Evolune-当前 E2`、`Evolune-E2 趋势`。
- Picker 中的 Release 预览已保存：
  `V16_RELEASE_PHONE_EVOLUNE_WIDGETS_2026-09-07.png`。
- 对应 UI dump：
  `V16_RELEASE_PHONE_EVOLUNE_WIDGETS_UI_SCROLL_2026-09-07.xml`。
- Wear Complication provider chooser 已显示 Evolune 的数据内容（`暂无未完成计划`、`估算血药浓度`、`估算浓度暂不可用`）；UI dump：
  `V16_RELEASE_WEAR_COMPLICATION_CHOOSER_UI_2026-09-07.xml`。
- Wear APK 静态注册审计：`V16_RELEASE_STATIC_REGISTRATION_AUDIT_2026-09-07.log`，包含 4 个 Tile service 和 3 个 Complication provider。
- Phone AppWidget host 审计：`V16_RELEASE_PHONE_WIDGET_HOST_AUDIT_2026-09-07.log`。Release 的 4 个 provider 已被系统枚举；当前 host 仍保留 Debug 包的历史实例，未删除。

## 新增的系统入口与运行态证据

- Phone Release `Evolune-今日计划` 已通过第二个（Release）Widget provider 添加到 Launcher，宿主节点的 package 为 `io.github.yingqiu0871.evolune`，content-desc 为 `Evolune-今日计划`；运行态因该 Release 数据库没有启用方案，显示 `今天暂无安排` / `尚无启用方案`，不是黑屏或默认旧样式。证据：
  - `V16_RELEASE_PHONE_TODAY_PLAN_RUNTIME_UI_2026-09-07.xml`
  - `V16_RELEASE_PHONE_TODAY_PLAN_WIDGET_RUNTIME_2026-09-07.png`
- Wear Tile 添加管理页在模拟器中实际发现并展示了 Release 预览：旧兼容 `Evolune-E2 浓度曲线`，以及 `Evolune-下一次服药`、`Evolune-今日计划`、`Evolune-当前 E2`。其中新增 Tile 预览和名称证据：
  - `V16_WEAR_TILE_ADD_LIST_4_2026-09-07.png`
  - `V16_WEAR_TILE_ADD_LIST_TODAY3_2026-09-07.png`
  - `V16_WEAR_TILE_ADD_LIST_UI_4_2026-09-07.xml`
- 在保留既有旧曲线 Tile 的条件下，已从添加页分别添加并打开 `Evolune-下一次服药`、`Evolune-当前 E2`、`Evolune-今日计划`。无手机快照时三个 Tile 都给出明确状态卡而非纯黑：`正在同步/打开手机端后将自动刷新`、`E2 不可用/等待手机端浓度数据`、`今日暂无待服/计划变化后将自动刷新`。证据：
  - `V16_WEAR_NEXT_DOSE_RUNTIME_2026-09-07.png`
  - `V16_WEAR_TODAY_PLAN_RUNTIME_3_2026-09-07.png`
  - `V16_WEAR_TODAY_PLAN_RUNTIME_UI_3_2026-09-07.xml`
- Wear App Release 运行态显示 Evolune 品牌标题、分层卡片和明确的无数据提示：`V16_RELEASE_WEAR_APP_RUNTIME_2026-09-07.png`、`V16_RELEASE_WEAR_APP_RUNTIME_UI_2026-09-07.xml`。
- Wear App 的“确认服药”和“跳过本次”协议及 UI 分支已在生产代码中存在；`跳过本次` 仅在 `canConfirm && notificationId != null` 时显示。当前 Release 模拟器没有启用方案/待服 occurrence，因此本轮设备截图只能证明空状态，不能宣称已完成带真实 occurrence 的点击、回执和刷新闭环。
  - 静态链路审计：`V16_WEAR_SKIP_PROTOCOL_STATIC_AUDIT_2026-09-07.log`。

## 仍未完成的验收证据

- 2026-09-08 已使用本机 JDK 21 与恢复后的 Gradle 环境强制重跑 `:experience-core:test`、`:app:testDebugUnitTest`、`:wear:testDebugUnitTest`；结果为 `BUILD SUCCESSFUL in 1m 10s`，`54 actionable tasks: 54 executed`。新鲜原始日志：`V16_POST_RELEASE_JVM_FRESH_20260908.log`。2026-09-07 的失败日志继续保留为构建环境排障记录，不再构成当前测试阻塞。
- 尚缺少有启用方案和真实待服 occurrence 的 Phone/Wear 联动证据：确认服药、跳过本次、Data Layer 回执、Tile 刷新、Complication READY/STALE/失败状态，以及升级后旧实例继续工作。

本记录只证明 Release 制品构建、签名、覆盖安装和冷启动通过；Phone Widget、Wear Tile、Complication 的完整系统发现/添加/刷新矩阵仍需按 v1.6 验收范围单独记录，不能由本记录宣告整个 v1.6 DONE。
