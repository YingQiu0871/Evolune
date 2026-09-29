# Evolune v1.9.1（候选 / Release Candidate — 尚未发布 / NOT PUBLISHED）

Evolune v1.9.1 是一个纯内部的代码梳理与精简版本：经全仓库静态审计，移除已证实无人引用的死代码、未使用的资源与导入，不改变任何用户可见行为。
Evolune v1.9.1 is an internal code-audit and slimming release: a repository-wide static audit removed code, resources and imports that are provably referenced nowhere. No user-visible behavior changes.

> 本文件是候选说明，随发布打包（签名、哈希、GitHub Release）再补全“安装包 / SHA-256”部分。
> This is a candidate note; the APK and SHA-256 sections will be added at release packaging.

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
  - Phone `app-release.apk` SHA-256：`6fdcb9ed094b5d51d07fc3169546021087f01c5653616fc666b6964893c73062`
  - Wear `wear-release.apk` SHA-256：`8b7824a7dcf06185a451cd1bd52a09d37c323a2c7b2893196fc18aa930014131`
  - 注：这是候选构建哈希；若之后候选分支再有代码提交，须重新构建并更新。/ Candidate-build hashes; rebuild and update if the branch changes again.
- **尚未覆盖**：androidTest 不在 CI 内编译/运行（需本机或模拟器）；真机/模拟器冒烟仍待打包阶段；GitHub Release 与 tag 尚未创建。/ Not covered yet: androidTest is not compiled or run in CI; device/emulator smoke remains; the GitHub Release and tag are not created.
