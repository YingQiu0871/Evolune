# Evolune v1.9.1 正式版 / Official Release

Evolune v1.9.1 是一个纯内部的代码梳理与精简版本：经全仓库静态审计，移除已证实无人引用的死代码、未使用的资源与导入，不改变任何用户可见行为。
Evolune v1.9.1 is an internal code-audit and slimming release: a repository-wide static audit removed code, resources and imports that are provably referenced nowhere. No user-visible behavior changes.

## 主要更新 / Highlights

- 移除未使用的文件：`pk/PKExample.kt`（println 示例）、`widget/WidgetUtils.kt`（及 `ScheduledDoseInfo`）、`ui/theme/Color.kt`（仅服务于已移除的未使用中/高对比度配色）。/ Removed unused files: the `PKExample` println demo, `WidgetUtils` (and `ScheduledDoseInfo`), and `ui/theme/Color.kt` (it only fed the removed, unused medium/high contrast schemes).
- 移除 `Theme.kt` 中四个未使用的中/高对比度配色方案与 `ColorFamily`。/ Removed four unused medium/high contrast color schemes and `ColorFamily` from `Theme.kt`.
- 数据层：移除遗留的 entity↔`pk` 模型映射、遗留的 `MedicationPlan.getDescription`，以及 12 个从未被调用的原始 DAO 方法（含绕过受检写入边界的 `upsertPlan`/`upsertEvent` 等）。实体与 schema 未改动。/ Data layer: removed legacy entity↔`pk` mappers, the legacy `MedicationPlan.getDescription`, and 12 never-called raw DAO methods (including `upsertPlan`/`upsertEvent`, which bypassed the checked-write boundary). Entities and schema are untouched.
- 移除其他零引用成员：`PKState.getConcentrationLevel`、`MedicationPlanPredictor.generateFutureEventsForPlans`、`calculateWidgetConcentration`、`BackupRestoreUiState.Uploading`、`DraftIssue.SlotIdMismatch`、`SUPPORTED_THEME_COLOR_SOURCES` 及 Wear 侧五个未使用成员。/ Removed other zero-reference members (see the list above and the commit message for names).
- 清理 103 条未使用的 import、21 条未使用的字符串（中英文成对移除，保持一致）和 1 个未使用的 drawable。/ Removed 103 unused imports, 21 unused strings (removed in both locales, parity kept) and one unused drawable.

## 有意未改动 / Intentionally untouched

- `retrospective_*`（恰好 24 个）与 `insights_*`（不少于 40 个）字符串键集合受测试冻结：其中未被代码引用的 4 个键（`insights_range_heading`、`insights_range_custom_heading`、`insights_range_custom_pick`、`retrospective_loading`）予以保留。CI 首次运行时因误删它们导致 3 个字符串对等性测试失败，已恢复。/ The `retrospective_*` (exactly 24) and `insights_*` (at least 40) string key sets are frozen by tests, so four unreferenced keys were kept. The first CI run failed three string-parity tests because they had been removed; they are restored.
- PK 数值代码（含未被引用的常量与函数）、Room `TypeConverter`、已冻结语义代码（历史匹配、回顾性 PK、Insights、备份 schema 与迁移）、`@Preview` 函数与 data class 字段。/ PK numerics (including unreferenced constants and functions), Room `TypeConverter`s, frozen semantic code (history matching, retrospective PK, Insights, backup schema and migrations), `@Preview` functions and data-class fields.
- `androidx-graphics-path` 版本目录条目（v1.9.0 D-02 已冻结）。/ The `androidx-graphics-path` catalog entry (frozen by v1.9.0 D-02).

## 审计中确认无问题 / Audit checks with no findings

- 所有 `PendingIntent` 均带 `FLAG_IMMUTABLE`/`FLAG_MUTABLE`。/ Every `PendingIntent` sets an immutability flag.
- 所有 suspend 上下文中的宽泛 `catch` 都对 `CancellationException` 做了处理。/ Every broad `catch` in a suspend context handles `CancellationException`.
- 生产源码无 TODO/FIXME、`GlobalScope`、`printStackTrace`。/ No TODO/FIXME, `GlobalScope` or `printStackTrace` in production sources.

## 兼容性 / Compatibility

- 无 Room 数据库 schema 迁移；无备份格式变化；用药计算语义不变；应用 ID 与签名证书连续性不变。/ No Room schema migration, no backup format change, medication calculation semantics unchanged, application ID and signing continuity unchanged.
- 版本信息：Phone `1.9.1` — versionCode `101090100`；Wear `1.9.1` — versionCode `1101090100`。/ Version: Phone `1.9.1` / `101090100`; Wear `1.9.1` / `1101090100`.

## 验证状态 / Verification status

- 作者环境无法运行 Gradle/Android SDK，因此以 GitHub Actions 为验证环境（PR #31，草稿）。/ The authoring environment had no Gradle or Android SDK, so GitHub Actions on PR #31 (draft) was the verification environment.
- **CI 第 1 次运行（#81，`f6655c6`）失败**：3 个字符串对等性测试因误删受冻结键集约束的 4 个键而失败，其余（三个模块编译、experience-core 与 wear 测试）通过；已在 `e5e0b53` 修复。/ CI run #81 (`f6655c6`) failed three string-parity tests caused by removing four keys in frozen key sets; fixed in `e5e0b53`.
- **CI 第 2 次运行（#82，`e5e0b53`）全部通过**：`./gradlew test`（含 app 1448 个 JVM 测试）、`:app:assembleDebug`、`:wear:assembleDebug` 与 APK 上传。/ CI run #82 (`e5e0b53`) passed in full: `./gradlew test` (including the 1,448 app JVM tests), `:app:assembleDebug`, `:wear:assembleDebug` and the APK upload.
- **签名 Release 构建（2026-09-29，所有者本机，候选提交 f89d728 起）**：`scripts/release_verify.ps1` 以发布环境变量签名构建 Phone/Wear Release（含 R8）成功；两个 APK 的 versionName=1.9.1，versionCode 分别为 101090100 / 1101090100，包名 `io.github.yingqiu0871.evolune`，签名证书 SHA-256 均为 `b9b6b9552fa4c7b656936d4c3aeb71c1229aa17c393337719bc8d0e07edaab08`，与发布身份一致。/ Signed Release build (owner machine, from candidate commit f89d728): both APKs built with R8, versionName 1.9.1, versionCode 101090100 / 1101090100, signing certificate matches the release identity.
  - 发布前复核（2026-09-29）：重新计算两个 APK 的 SHA-256 与上面记录一致；`apksigner verify --print-certs` 显示 v2 签名、1 个签名者、证书 SHA-256 `b9b6b955…aab08`（`CN=Evolune Release`）。合并到 `main` 后相对 f89d728 仅有文档变化（构建不嵌入 git 信息），因此发布的就是这两个 APK，未重新构建。/ Pre-release re-check: both hashes recomputed and matched; apksigner shows v2, one signer, the release certificate. After merging into `main` only documentation differs from f89d728 (the build embeds no git data), so these exact APKs are published without a rebuild.
- **androidTest（2026-09-29，本机模拟器 `evolune-r9-api35-fresh2`，Android 15 / API 35，Debug 构建）**：`:app:connectedDebugAndroidTest` 编译通过；**396 个测试，0 失败，0 错误，5 跳过**（跳过项为条件门控：V15 原地升级设备测试 2 项、仅折叠屏的布局测试 2 项、修复工具迁移测试 1 项）。该结果在系统语言 zh-CN、屏幕 1080×2400 下取得（与 v1.9.0 证据所用 Pixel 7 分辨率一致）。/ androidTest: 396 tests, 0 failures, 0 errors, 5 skipped (condition-gated), run under zh-CN at 1080×2400 (the Pixel 7 resolution used by the v1.9.0 evidence).
  - 首次运行使用该 AVD 的默认配置（en-US、1080×1920）时有 8 个 Insights/Timeline UI 测试失败（中文日期格式断言 1 个；小屏下 lazy 列表节点不在组合范围内 7 个）。**在同一模拟器上对 v1.9.0 基线（`main` @ `37bf0f2`）运行相同 5 个测试类，失败的恰好是同样 8 个测试**，因此属于环境前提，不是 v1.9.1 回归；切换到 zh-CN + 1080×2400 后这 5 个测试类 73/73 通过，随后整套 396 个测试通过。/ A first run with the AVD defaults (en-US, 1080×1920) failed 8 Insights/Timeline UI tests; the v1.9.0 baseline on the same emulator failed exactly the same 8, so this is an environment precondition, not a regression.
- **模拟器冒烟（2026-09-29，发布 APK 本身）/ Emulator smoke on the release APKs**：
  - Phone（API 35 模拟器）：从已安装的 v1.2.0 发布版 `adb install -r` 覆盖升级成功（签名连续）；另做一次卸载后全新安装，冷启动 `TotalTime` 497 ms。首次引导 → 主页 / 记录 / 历史 / 方案 / 设置，以及历史下的用药洞察（统计）、回顾性 PK、用药时间线均可进入。新建用药方案（口服 EV 2 mg、每天 09:00，授予通知权限）并记录一次服药，历史日历与主页浓度随之更新。Evolune Portable JSON 导出（全部历史）→ 删除该记录 → 导入：“新增 1 条，已存在 0 条”，再次导入：“新增 0 条，已存在 1 条”，记录恢复。桌面小组件选择器列出 4 个 Evolune 小组件，添加“今日计划”并完成外观配置后正确显示本次方案与“1/1 完成”。/ Upgrade in place from v1.2.0 and a clean install both launched; all top-level tabs plus Insights, Retrospective PK and Timeline opened; created a plan and recorded a dose; Portable JSON export → delete → import restored the record and re-import was idempotent; the today-plan widget was added and showed live data.
  - Wear（Wear OS API 35 模拟器，未与手机配对）：安装成功（versionCode 1101090100），冷启动 `TotalTime` 243 ms，显示“手机未连接”的缓存状态；4 个 Tile 与 3 个 Complication 服务均已注册，“今日计划” Tile 可添加并渲染。/ Wear installs and launches (unpaired, shows the cached disconnected state); Tiles and Complications are registered and the today-plan Tile renders.
  - 两端 logcat 中 Evolune 进程无 `FATAL EXCEPTION`、无 ANR。/ No FATAL EXCEPTION or ANR for the Evolune process on either device.
- **未覆盖 / Not covered**：Google Drive 加密备份与恢复（需要真实 Google 账号授权）；手机与手表配对后的数据同步与确认/撤销；真机安装。/ Google Drive encrypted backup/restore (needs a real Google account), paired Phone↔Wear sync, real-device install.

## 安装包 / Downloads

GitHub Release [`v1.9.1`](https://github.com/YingQiu0871/Evolune/releases/tag/v1.9.1) 附带以下 3 个资产。/ The v1.9.1 GitHub Release carries these three assets.

| 资产 / Asset | 大小 / Size (bytes) | SHA-256 |
|---|---:|---|
| `Evolune-Phone-v1.9.1.apk` | 6,303,192 | `6fdcb9ed094b5d51d07fc3169546021087f01c5653616fc666b6964893c73062` |
| `Evolune-Wear-v1.9.1.apk` | 2,604,840 | `8b7824a7dcf06185a451cd1bd52a09d37c323a2c7b2893196fc18aa930014131` |
| `SHA256SUMS.txt` | 181 | `a4ec74ea9ea70df226928f15c2d116bd900c64b35e7097d737ee47f228abc303` |

两个 APK 均使用持久 Evolune 发布证书签名（SHA-256 `b9b6b9552fa4c7b656936d4c3aeb71c1229aa17c393337719bc8d0e07edaab08`），可从 v1.9.0 直接覆盖安装，无需卸载或清除数据。/ Both APKs are signed with the persistent Evolune release certificate and upgrade in place from v1.9.0 without clearing data.
