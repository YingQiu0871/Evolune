# Evolune v1.6.0

v1.6.0 扩展了手机和 Wear OS 上的快捷信息入口，并统一采用 Evolune 的动态配色和视觉语言。

## 新增与改进

- 手机系统小组件拆分为四个清晰入口：今日计划、下一次服药、当前 E2 和 E2 趋势。
- 每个手机小组件可以独立选择自动、浅色或深色显示，以及 Material You 或八组预制配色。
- 今日计划恢复可滚动的用药列表、完成状态和确认按钮；下一次服药与当前 E2 使用自适应居中排版。
- E2 趋势以圆角柱状图区分历史浓度和预计浓度，增加自适应坐标轴与均匀时间刻度。
- Wear OS 新增下一次服药、今日计划和当前 E2 三种 Tile，并保留旧血药浓度 Tile 的升级兼容。
- Wear App、Tile 与 Complication 统一支持 Material You 和预制配色；所有入口统一使用 `Evolune-XXX` 名称与品牌图标。
- Wear 用药条目支持“确认用药 / 跳过本次”；动作由手机端校验，跳过会按具体计划时段和时间持久化，并阻止该次未来提醒再次出现。
- 新增下一次服药、今日完成度和当前 E2 三种表盘 Complication。
- 优化圆形手表的安全边距、圆角、按钮层级和图表布局，并维持事件驱动刷新与低功耗后台策略。

## 兼容性

- 支持从 v1.5.0 直接覆盖升级，保留手机数据、旧 Widget、旧 Tile 和实例配置。
- Phone Room 仍是唯一权威数据来源；PK 数学模型、数据库 schema 和备份格式未改变。

## 安装文件

- `Evolune-Phone-v1.6.0.apk` — Android 12+，SHA-256 `E964FC8A89711B4F0F68B9BC93EB185BF77189E41CC0145F53E77DCD94A76665`
- `Evolune-Wear-v1.6.0.apk` — Wear OS / Android API 30+，SHA-256 `B344C07CCB67DFEDCCE294EC8A655F6229B65D6C36A158E78461C25DBB8D5BA4`
- `SHA256SUMS.txt` — 两个 APK 的校验清单

两个 APK 使用相同的 Evolune Release 证书，证书 SHA-256 为
`B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`。

从 v1.5.0 升级可直接覆盖安装，无需卸载或清除数据。GitHub Actions 提供的 Debug APK 使用不同应用 ID 和签名，不能代替正式安装文件。
