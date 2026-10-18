# Evolune v1.12.0 正式版 / Official Release

Evolune v1.12.0 新增「化验结果」记录，并提供一个实验性、默认关闭的选项：用你的雌二醇化验值校准首页与回顾性页面的 E2 估算曲线。
Evolune v1.12.0 adds Lab results and an experimental, default-off option that calibrates the Home and retrospective E2 estimate curves with your own estradiol lab values.

## 主要更新 / Highlights

### 化验结果 / Lab results

- 「历史」页新增「化验结果」卡片，进入全屏列表（按测量时间从新到旧），支持添加、编辑和删除。每条记录包含日期、时间、数值和单位；默认单位 pg/mL，也可选 pmol/L（睾酮的 ng/dL、nmol/L 同样可记录，但只保存，不参与 E2 校准）。/ The History tab gains a Lab results card that opens a full-screen list (newest first) with add, edit and delete. Each entry has a date, time, value and unit; the default unit is pg/mL, pmol/L is also available (testosterone ng/dL and nmol/L can be recorded too, but are only stored and never used for E2 calibration).
- 化验结果保存在独立的 Room 数据库 `evolune_labs`（v1）中；主数据库 `AppDatabase`（v3）的结构与迁移链不变。/ Lab results live in a separate Room database, `evolune_labs` (v1); the main `AppDatabase` (v3) schema and migration chain are unchanged.

### 按化验校准 E2 曲线（实验性，默认关闭）/ Calibrate the E2 curve with lab results (experimental, off by default)

- 设置中新增开关「按化验校准 E2 曲线」，默认关闭。开启后，应用用已记录的雌二醇化验（pg/mL、pmol/L）估算一个个人幅度系数：每条化验与未校准的模型在抽血时刻的预测对比，在对数空间以人群先验做最大后验（MAP）拟合，系数有硬性上下界，再把首页的 E2 曲线按该系数缩放。/ Settings gains a toggle, "按化验校准 E2 曲线" (calibrate the E2 curve with lab results), off by default. When on, the app estimates a personal amplitude factor from your recorded estradiol labs (pg/mL, pmol/L): each lab is compared with the uncalibrated model at the time of the draw, fitted in log space as a maximum a posteriori (MAP) estimate under a population prior with hard bounds, and the Home E2 curves are scaled by that factor.
- 化验值在图上以菱形 ◆ 标出；图表下方的说明卡片显示所用化验次数和系数，并注明曲线仍是模型估算，不是实测浓度。/ Lab values are drawn as diamonds (◆); a note card under the chart shows the number of labs used and the factor, and states that the curve is still a model estimate rather than a measured concentration.
- 回顾性 PK 页面在开启时使用同一个系数。PKState、桌面 Widget 与手表（Wear）不接入校准，仍显示未校准的模型值。/ The retrospective PK page applies the same factor when the toggle is on. PKState, Widgets and Wear are not calibrated and keep showing the uncalibrated model.
- **实验性功能，默认关闭。** 其校准参数（测量噪声 σ、人群先验 τ）是工程参考值，尚待独立科学审阅。结果是模型估算，不是医疗建议，不应用于调整剂量或治疗决策。/ **Experimental feature, off by default.** Its calibration parameters (measurement noise σ and population prior τ) are engineering reference values that still await independent scientific review. Results are model estimates, not medical advice, and must not be used to adjust doses or make treatment decisions.
- 关闭开关（默认）时不读取化验库，曲线与 v1.11.1 的数值一致；PK 模型本身未改动。/ With the toggle off (the default) the lab database is not read and curves match v1.11.1; the PK model itself is unchanged.

### 备份、恢复与导入导出 / Backup, restore and import/export

- 加密备份 payload 升至 schema v3，新增可选的 `labResults`；只有设备上存在化验结果时才写 v3，没有化验结果的备份仍写 v2，旧版本依然可以恢复。恢复 v3 备份会整体替换化验库；恢复 v1/v2 备份不触及化验库。恢复日志升至格式 v2，记录恢复前的化验结果，使启动恢复与失败回滚能同时还原两个数据库。/ The encrypted backup payload moves to schema v3 with an optional `labResults` section. v3 is written only when the device has lab results; lab-free backups are still written as v2 and remain restorable by older versions. Restoring a v3 backup replaces the lab database; restoring a v1/v2 backup leaves it untouched. The restore journal moves to format v2 and records the pre-restore lab results so startup recovery and rollback restore both databases.
- Mahiro v1 JSON 导入与导出携带 `labResults`（`id`、`timeH`、`concValue`、`unit`）；导入时无效条目会被跳过并单独计数；没有化验结果时导出与之前逐字节相同。/ Mahiro v1 JSON import and export carry `labResults` (`id`, `timeH`, `concValue`, `unit`); invalid entries are skipped on import and counted separately; with no lab results the export is byte-identical to before.

## 修复 / Fixes

- 预测性返回手势期间，页面不再透出下层页面：关于、帮助、Drive、披露页面回到设置页，以及历史页的子页面回到历史页时，两个页面会同时绘制，透明根容器会让下层页面透出来。现在每个目的地都绘制自己的不透明背景（#52）。/ During the predictive back gesture the page underneath no longer shows through: for About, Help, Drive and Disclosures going back to Settings, and the History child pages going back to History, both entries are drawn at once and a transparent root let the lower page show through. Every destination now paints its own opaque surface (#52).

## 工程与测试 / Engineering and tests

- CI 现在会在构建导出了未提交的 Room schema 时失败。/ CI now fails when the build exports a Room schema that is not committed.
- androidTest 更稳定：用条件等待代替固定休眠，洞察与时间线测试不再依赖语言和屏幕高度，CI 上传原始 androidTest 结果和逐测试 logcat。/ More reliable androidTest: condition waits replace fixed sleeps, the Insights and Timeline tests no longer depend on locale or screen height, and CI uploads raw androidTest results with per-test logcat.
- 根目录的零散文档移入 `docs/evolune/`，原始证据目录移出 `main`。/ Loose root documents moved into `docs/evolune/`, and raw evidence folders moved out of `main`.
- 设计与参数来源见 [PK 2.0 校准计划](../pk2/PK2_CALIBRATION_PLAN.md) 和 [清除率评估](../pk2/PK2_CLEARANCE_EVALUATION.md)（个人清除率拟合与化验时间加权评估后暂不实现）。/ Design and parameter provenance: see the [PK 2.0 calibration plan](../pk2/PK2_CALIBRATION_PLAN.md) and the [clearance evaluation](../pk2/PK2_CLEARANCE_EVALUATION.md) (personal clearance fitting and lab time weighting were evaluated and are not implemented).

## 兼容性 / Compatibility

- `AppDatabase` 的 schema 与迁移不变（仍为 v3）；化验结果使用新的独立数据库 `evolune_labs`。应用 ID 与签名证书连续，可从 v1.11.1 直接覆盖安装。/ The `AppDatabase` schema and migrations are unchanged (still v3); lab results use the new, separate `evolune_labs` database. Application ID and signing continuity are unchanged, and v1.11.1 upgrades in place.
- 包含化验结果的备份（payload schema v3）无法被 v1.11.x 及更早版本恢复——旧版本只能恢复 schema v1/v2 的备份。没有化验结果的备份仍是 v2。/ Backups that contain lab results (payload schema v3) cannot be restored by v1.11.x or older: older versions restore only schema v1/v2 backups. Backups without lab results are still v2.
- 从 v1.12.0 降级到 v1.11.x：主数据库结构未变，用药数据不受影响；旧版本不会打开化验数据库，因此其中的化验结果被忽略（文件仍保留在设备上）。降级不是受支持的路径，且尚未实测；降级前请先完成或取消正在进行的恢复（v1.12.0 写的恢复日志为格式 v2，旧版本启动恢复无法读取），无损回滚请先导出备份。/ Downgrading from v1.12.0 to v1.11.x: the main database schema is unchanged so dose data is unaffected; older versions never open the lab database, so its lab results are ignored (the file stays on the device). Downgrading is not a supported path and has not been tested here; finish or cancel any restore in progress first (v1.12.0 writes restore journal format v2, which older versions' startup recovery cannot read); export a backup first if you need a lossless rollback.
- 设置开关默认关闭，关闭时 PK 模型数值与 v1.11.1 相同。/ The toggle is off by default, and with it off PK model numerics are the same as v1.11.1.
- 版本信息：Phone `1.12.0` — versionCode `101120000`；Wear `1.12.0` — versionCode `1101120000`。/ Version: Phone `1.12.0` / `101120000`; Wear `1.12.0` / `1101120000`.

## 验证状态 / Verification status

- **CI**：`Build Debug APK` 工作流在 #44–#52 各自的 PR 检查上通过。/ CI: the `Build Debug APK` workflow passed on the PR checks of #44–#52.
- **androidTest 完整运行**：待补 / Pending — full `:app:connectedDebugAndroidTest` run, to be filled in after the owner runs it.
- **签名 Release 构建**：待补 / Pending — `scripts/release_verify.ps1` on the owner's machine (versionName, versionCode and release certificate check).
- **模拟器升级冒烟**：待补 / Pending — in-place upgrade of the release APK from v1.11.1 on an emulator, including Lab results and the calibration toggle.
- **未覆盖 / Not covered**：开启校准开关的结果尚未经过独立科学审阅；Google Drive 加密备份/恢复与 Phone↔Wear 同步在本版本中未做专门验证。/ Not covered: the calibration results have not had independent scientific review; Google Drive backup/restore and paired Phone↔Wear sync have no dedicated verification for this release.
