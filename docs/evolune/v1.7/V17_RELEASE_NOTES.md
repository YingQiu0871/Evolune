# Evolune v1.7.0 正式版 / Official Release

Evolune v1.7.0 将 Evolune 已记录的用药事件转化为可回看、可理解、可导出的历史记录，并保持手机、微件与 Wear 的历史一致性。
Evolune v1.7.0 turns medication events already recorded by Evolune into a coherent history you can review, understand, and export, while keeping Phone, widgets, and Wear consistent.

## 主要更新 / Highlights

- 历史（History）：按日历日查看用药活动，区分计划时点与实际摄入，并显示计划与实际时间差；未匹配事件按真实来源呈现，不猜测计划归属。
  History: review medication activity by calendar day, distinguishing planned occurrences from actual intake with timing differences; unmatched events are shown by their real source without guessing plan ownership.
- 时间线（Timeline）：按月浏览统一时间线，行分为已匹配、未记录计划与未匹配摄入三类；仅呈现事实，不含评分或依从性判定。
  Timeline: browse a unified monthly timeline with matched, unrecorded-schedule, and unmatched-intake rows — facts only, with no scoring or adherence judgment.
- 洞察（Insights）：7/30/90 天的事实性汇总（记录次数、来源分布、身份置信度），不输出严格历史依从率。
  Insights: factual summaries over 7/30/90 days (recorded counts, source breakdown, identity confidence) without strict historical adherence rates.
- 回顾性 PK：按自选历史区间基于权威已记录摄入重建浓度曲线，明确标注为模型估算而非实测血药浓度。
  Retrospective PK: reconstruct concentration curves over a chosen historical interval from authoritative recorded intakes, clearly labeled as model estimates rather than measured blood concentrations.
- 数据可携（Export & Data Portability）：新增 Evolune Portable JSON v1（导出与增量导入）与 CSV v1（仅导出），支持最近 30 天 / 90 天 / 全部历史；导入完整预校验、保持稳定事件 ID、可安全重试；剪贴板导出仅保留旧版兼容路径并新增隐私确认。
  Export & Data Portability: new Evolune Portable JSON v1 (export and additive import) and CSV v1 (export only) with 30-day / 90-day / all-history ranges; imports are fully prevalidated, keep stable event IDs, and are safe to retry; clipboard export remains a legacy-only path with a privacy confirmation.
- 一致性：手机、微件与 Wear 的用药动作产生同一权威结果；被拒绝的微件操作会刷新权威状态并给出明确提示，不再静默无响应。
  Consistency: Phone, widget, and Wear actions produce the same authoritative result; rejected widget actions now refresh authoritative state and show explicit feedback instead of failing silently.
- 无障碍与本地化更新覆盖 History、Timeline、Insights、回顾性 PK 与导出界面。
  Accessibility and localization updates cover History, Timeline, Insights, retrospective PK, and the export surfaces.

## 兼容性 / Compatibility

- 支持从 v1.6.0 直接就地升级，保留手机数据、旧版微件与 Wear 安装。
  Direct in-place upgrade from v1.6.0 is supported while preserving Phone data, legacy widgets, and existing Wear installs.
- Phone Room 仍是唯一权威数据源；数据库 schema 与备份格式未改变。
  Phone Room remains the single source of truth; the database schema and backup format are unchanged.
- 导出（Evolune Portable JSON / CSV）与加密备份（`.evbackup`）保持相互独立。
  Export (Evolune Portable JSON / CSV) remains separate from encrypted backup (`.evbackup`).

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.7.0` — versionCode `101070000`
- Wear: `1.7.0` — versionCode `1101070000`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.7.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.0/Evolune-Phone-v1.7.0.apk)
- [Wear APK — Evolune-Wear-v1.7.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.0/Evolune-Wear-v1.7.0.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.0/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `FA6B3ACE07D3D4CDFC7F014967878C21C55C23A7C3AE0B6BC8AD2C1D44C24B14`
- Wear: `B8E8D8F38C07EA5A4406C9D6007AB5F69DE33A76CA5233E05D80AD654A24BF39`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.6.0 直接覆盖安装，无需卸载或清除数据。GitHub Actions 提供的 Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.6.0 without uninstalling the app or clearing data. Debug APKs produced by GitHub Actions use a different application ID and signature and are not substitutes for the official release packages.

感谢使用 Evolune。
Thank you for using Evolune.
