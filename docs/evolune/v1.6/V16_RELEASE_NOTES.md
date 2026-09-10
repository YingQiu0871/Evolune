# Evolune v1.6.0 正式版 / Official Release

Evolune v1.6.0 扩展了手机和 Wear OS 上的快捷信息入口，并统一采用 Evolune 的动态配色和视觉语言。
Evolune v1.6.0 expands glanceable information across Phone and Wear OS while bringing Evolune’s dynamic color system and visual language to every surface.

## 主要更新 / Highlights

- 手机系统小组件拆分为四个清晰入口：今日计划、下一次服药、当前 E2 和 E2 趋势。
  Phone widgets are now available as four distinct entries: Today’s Plan, Next Dose, Current E2, and E2 Trend.
- 每个手机小组件可以独立选择自动、浅色或深色显示，以及 Material You 或八组预制配色。
  Each Phone widget can independently use automatic, light, or dark appearance with Material You or eight built-in color palettes.
- 今日计划恢复可滚动的用药列表、完成状态和确认按钮；下一次服药与当前 E2 使用自适应居中排版。
  Today’s Plan restores a scrollable medication list, completion states, and confirmation controls, while Next Dose and Current E2 use adaptive centered layouts.
- E2 趋势以圆角柱状图区分历史浓度和预计浓度，并提供自适应坐标轴与均匀时间刻度。
  E2 Trend uses rounded bars to distinguish historical and projected concentrations, with adaptive axes and evenly spaced time labels.
- Wear OS 新增下一次服药、今日计划和当前 E2 三种 Tile，并保留旧血药浓度 Tile 的升级兼容。
  Wear OS adds Next Dose, Today’s Plan, and Current E2 Tiles while preserving upgrade compatibility for the legacy concentration Tile.
- Wear App、Tile 与 Complication 统一支持 Material You 和预制配色；所有入口统一使用 `Evolune-XXX` 名称与品牌图标。
  The Wear App, Tiles, and Complications now share Material You and preset palettes, consistent `Evolune-XXX` names, and Evolune branding.
- Wear 用药条目支持“确认用药 / 跳过本次”；动作由手机端校验，跳过会按具体计划时段和时间持久化，并阻止该次未来提醒再次出现。
  Wear medication entries support “Confirm dose / Skip this dose.” Phone validates each action, and a skipped occurrence is persisted by plan slot and time so its future reminder stays suppressed.
- 新增下一次服药、今日完成度和当前 E2 三种表盘 Complication。
  Three watch-face Complications are now available: Next Dose, Today’s Completion, and Current E2.
- 优化圆形手表的安全边距、圆角、按钮层级和图表布局，并维持事件驱动刷新与低功耗后台策略。
  Round-screen safe areas, corners, button hierarchy, and chart layout have been refined while retaining event-driven refresh and a low-power background strategy.

## 兼容性 / Compatibility

- 支持从 v1.5.0 直接覆盖升级，保留手机数据、旧 Widget、旧 Tile 和实例配置。
  Direct in-place upgrade from v1.5.0 is supported while preserving Phone data, legacy Widgets, legacy Tiles, and per-instance settings.
- Phone Room 仍是唯一权威数据来源；PK 数学模型、数据库 schema 和备份格式未改变。
  Phone Room remains the single source of truth. The PK mathematical model, database schema, and backup format are unchanged.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.6.0` · versionCode `101060000`
- Wear: `1.6.0` · versionCode `1101060000`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.6.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.6.0/Evolune-Phone-v1.6.0.apk)
- [Wear APK — Evolune-Wear-v1.6.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.6.0/Evolune-Wear-v1.6.0.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.6.0/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `E964FC8A89711B4F0F68B9BC93EB185BF77189E41CC0145F53E77DCD94A76665`
- Wear: `B344C07CCB67DFEDCCE294EC8A655F6229B65D6C36A158E78461C25DBB8D5BA4`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

从 v1.5.0 升级可直接覆盖安装，无需卸载或清除数据。GitHub Actions 提供的 Debug APK 使用不同应用 ID 和签名，不能代替正式安装文件。
You can upgrade directly from v1.5.0 without uninstalling the app or clearing data. Debug APKs produced by GitHub Actions use a different application ID and signature and are not substitutes for the official release packages.

感谢使用 Evolune。
Thank you for using Evolune.
