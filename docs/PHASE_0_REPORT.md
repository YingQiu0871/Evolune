# Evolune Phase 0 报告

**报告日期**：2026-08-01

**状态**：Phase 0 技术基线完成；Phase 1 未开始。

**建议提交**：`chore: complete Evolune phase 0 baseline`

## 范围

本轮只处理项目身份、文档一致性、许可证/来源、Android Auto Backup、敏感数据排除和不影响业务行为的清理。以下内容明确未实施：

- `DoseEvent` 数据结构和 `timeH` 语义；
- Room schema、迁移和 `exportSchema`；
- PK 算法、参数和曲线计算；
- Wear 协议、Data Layer payload 和同步逻辑；
- Health Connect、Glance、WorkManager、云同步；
- Gradle 大规模模块拆分。

## 已完成变更

### 文档与身份

- 根 `readme.md` 作为 GitHub 入口，指向 `docs/evolune/` 的详细文档、`docs/SOURCE_PROVENANCE.md` 和审阅记录。
- 修复详细 README 的 `LICENSE` 相对链接，补充 NOTICE、备份边界和当前 Wear 能力说明。
- 统一包名说明：手机 `io.github.yuninggu.evolune`，Wear `io.github.yuninggu.evolune.wear`。
- 删除应用版权信息中的旧作者归属和旧项目直接归属表述，改为指向可审计的 `NOTICE` 与来源台账。
- 新增根 `NOTICE`，明确 MIT 项目边界、迁移资料区边界和第三方依赖仍需保留各自许可证。
- `docs/SOURCE_PROVENANCE.md` 增加 Phase 0 边界记录、来源状态定义和逐项记录模板。

### Android Auto Backup

手机和 Wear Manifest 均保留 `allowBackup="true"`，但同时引用旧版与 Android 12+ 规则：

- `app/src/main/AndroidManifest.xml`
- `wear/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `wear/src/main/res/xml/backup_rules.xml`
- `wear/src/main/res/xml/data_extraction_rules.xml`

云备份和设备迁移均排除 `root`、`file`、`database`、`sharedpref`、`external` 以及 device-protected 对应域。这样手机 Room 数据库、DataStore、Wear SharedPreferences 和未来同域敏感文件不会通过系统备份自动迁移；用户使用现有 JSON 导出/导入完成主动迁移。

规则域和 `dataExtractionRules` 结构依据 [Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup)。

## 验证记录

以下为最终 Phase 0 文件状态下重新执行的命令。所有命令均在 `D:\Evolune` 执行，Gradle 使用 `C:\Program Files\kedou\jre` 作为 `JAVA_HOME`。

| 命令 | 结果 | 说明 |
|---|---|---|
| `git diff --check` | PASS | 退出码 0；仅有 Git 的 LF/CRLF 提示 |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace` | PASS | 12 个测试套件，88 个测试，0 failures，0 errors，0 skipped |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :app:assembleDebug --no-daemon --stacktrace` | PASS | `app/build/outputs/apk/debug/app-debug.apk`，约 66.7 MiB |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :wear:testDebugUnitTest --no-daemon --stacktrace` | PASS | 1 个测试套件，1 个测试，0 failures，0 errors，0 skipped |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :wear:assembleDebug --no-daemon --stacktrace` | PASS | `wear/build/outputs/apk/debug/wear-debug.apk`，约 14.1 MiB |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :app:lintDebug --rerun-tasks --no-daemon --stacktrace` | PASS | 0 errors，78 warnings，1 hint |
| `$env:JAVA_HOME='C:\Program Files\kedou\jre'; .\gradlew.bat :wear:lintDebug --rerun-tasks --no-daemon --stacktrace` | PASS | 0 errors，6 warnings |

### 首次失败与处理

- 首次手机测试因终端没有 `JAVA_HOME` 且 PATH 没有 Java 失败；改用本机 JDK 17 `C:\Program Files\kedou\jre` 后通过。
- 首次手机 lint 因被 Git 忽略的 `local.properties` 未按 Java properties 语法转义 Windows 盘符而失败；修正本机 `sdk.dir` 为 `C\:\\Users\\1\\AppData\\Local\\Android\\Sdk`，并用 `--rerun-tasks` 重跑后通过。
- Gradle 多次输出 SDK XML version 4 与当前工具仅理解 version 3 的兼容性警告；不影响本轮构建、测试或 lint 结果。
- lint 保留既有警告，未在 Phase 0 越界修改业务代码；完整报告位于 `app/build/reports/lint-results-debug.html` 和 `wear/build/reports/lint-results-debug.html`。

## 发布前检查清单

- [x] 根 README、详细 README、LICENSE、NOTICE 和来源台账链接可达。
- [x] 手机和 Wear Manifest 均引用 `fullBackupContent` 与 `dataExtractionRules`。
- [x] 手机和 Wear 敏感应用私有数据在云备份和设备迁移中均被排除。
- [x] 迁移文档不再指导对 Evolune 直接应用补丁。
- [x] 手机单元测试、Wear 单元测试、手机 debug 构建、Wear debug 构建和两端 lint 通过。
- [ ] 项目所有者完成来源台账的逐文件确认，并决定是否需要扩展第三方依赖 NOTICE。
- [ ] 在独立提交中复核工作树，确认既有业务改动不会与本 Phase 0 基线混合。
- [ ] 由项目所有者提交 Phase 0 后再创建发布标签。

## 标签

本轮不自动创建标签。当前工作树在任务开始前已经包含未提交的业务、资源和文档改动；直接把标签指向现有 `HEAD` 会使标签不包含本次 Phase 0 内容。

建议项目所有者确认并提交后创建：

```powershell
git tag -a architecture-baseline-v1 -m "Evolune architecture baseline v1"
git tag -a phase-0-complete -m "Evolune Phase 0 complete"
```

Phase 1 不会因本报告自动开始。
