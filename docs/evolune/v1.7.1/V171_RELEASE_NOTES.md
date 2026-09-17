# Evolune v1.7.1 正式版 / Official Release

Evolune v1.7.1 是一次以真机界面质量为重点的维护更新：改进历史页面的信息层级、修正日历与卡片对齐、重做回顾性 PK 图表的可读性，并让用药时间线的日期条连续可读。
Evolune v1.7.1 is a maintenance update focused on real-device UI quality: it improves the History page hierarchy, corrects calendar and card alignment, makes the Retrospective PK chart readable, and turns the Timeline date strip into a continuous sequence.

## 主要更新 / Highlights

- 历史（History）层级改进：日历与当天事实记录优先展示；用药洞察、回顾性 PK、用药时间线三个次级入口统一移至页面底部。
  History hierarchy: the calendar and the selected day's factual records come first; the three secondary entries (Insights, Retrospective PK, Timeline) are grouped at the bottom of the page.
- 历史日历对齐修正：选中日期的数字现在在选中背景内水平、垂直精确居中。
  History calendar alignment: the selected day number is now exactly centered both horizontally and vertically inside its selection background.
- 洞察（Insights）卡片间距修正：记录摄入次数与有记录的天数两张卡片不再贴连。
  Insights card spacing: the recorded-intakes and recorded-days metric cards no longer touch.
- 回顾性 PK 可视化重设计：新增近 7 天 / 近 30 天 / 近 90 天区间选择（默认近 7 天）；曲线按视图去抽样并保留首尾与极值；标记改为底部小型刻度/圆点；新增约 5–7 个日期轴标签；支持点按或拖动查看某一时刻的模型估算浓度。全部数值仍为模型估算，并非实测血药浓度。
  Retrospective PK redesign: a 7/30/90-day range selector (default 7 days); a view-only decimated curve that preserves the first/last point and extrema; small baseline tick/dot markers; about five to seven date labels; and tap/drag inspection of the model-estimated concentration at a moment in time. All values remain model estimates, not measured blood concentrations.
- 用药时间线（Timeline）日期条：连续显示日期并包含相邻月份；切换月份后当月 1 号居中显示且左侧可见上月日期；相邻月份日期以弱化样式呈现，未来日期不可选。
  Timeline date strip: dates render as one continuous sequence including adjacent-month days; after switching months, day 1 is centered with the preceding month's dates visible to its left; adjacent-month days are subdued and future dates stay non-selectable.
- 时间线卡片间距改进：同一天的多张记录卡片之间留出清晰间隔。
  Timeline spacing: multiple record cards within one day now keep a clear visual separation.
- 设置（Settings）：新增独立的"隐私与权限"入口，设置页八项入口全部直接可见。
  Settings: a standalone "Privacy & permissions" entry joins the now eight directly visible Settings entries.

## 兼容性 / Compatibility

- 支持从 v1.7.0 直接就地升级，保留手机数据、微件与 Wear 安装。
  Direct in-place upgrade from v1.7.0 is supported while preserving Phone data, widgets, and existing Wear installs.
- Phone Room 仍是唯一权威数据源；数据库 schema、导入导出与备份格式未改变。
  Phone Room remains the single source of truth; the database schema, import/export and backup formats are unchanged.
- 回顾性 PK 仍为模型估算，不构成诊断、处方或剂量建议。
  Retrospective PK remains a model estimate and is not diagnosis, prescription, or dosing advice.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.7.1` — versionCode `101070100`
- Wear: `1.7.1` — versionCode `1101070100`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.7.1.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.1/Evolune-Phone-v1.7.1.apk)
- [Wear APK — Evolune-Wear-v1.7.1.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.1/Evolune-Wear-v1.7.1.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.1/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `E34D6749AC98F3B7C549B351899490FABE49C00D46788C791E30EFE33AFB7DFB`
- Wear: `96AE56BC50401F919E28CF14F36401FE260AE9BBF116A5597460F6FECB88F41A`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.7.0 直接覆盖安装，无需卸载或清除数据。Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.7.0 without uninstalling the app or clearing data. Debug APKs use a different application ID and signature and are not substitutes for the official release packages.

感谢使用 Evolune。
Thank you for using Evolune.
