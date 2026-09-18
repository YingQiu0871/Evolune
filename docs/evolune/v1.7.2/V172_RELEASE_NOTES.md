# Evolune v1.7.2 正式版 / Official Release

Evolune v1.7.2 带来更直接的设置体验与全新的应用配色选择：设置页常用功能全部内联、无需进入二级页面；除跟随壁纸的动态着色外，新增 8 个内置预设配色；旧版内置主题用户在升级后外观保持不变；主题状态纳入严格备份 schema v2 并兼容旧备份；历史记录卡片不再显示推断匹配的解释性句子。
Evolune v1.7.2 delivers a more direct Settings experience and a new app palette choice: common controls are inline on the main Settings page, Dynamic wallpaper/system colors are joined by eight built-in presets, the old built-in theme is preserved exactly for existing users, theme state is included in the strict backup schema v2 with legacy restore compatibility, and the History record card no longer shows the explanatory inferred-match sentence.

## 主要更新 / Highlights

- 设置（Settings）扁平化：基础数据、外观与格式、同步与备份、更新四大区块直接内联在设置主页；指南、隐私与权限、功能教程、关于保持独立入口。 / Flattened Settings: Basic data, Appearance & format, Sync & backup and Updates are inline on the main Settings page; Guide, Privacy & permissions, Feature tutorial and About remain separate entries.
- 应用配色（App palettes）：跟随壁纸（Dynamic）之外新增 8 个内置预设配色——蓝、紫罗兰、樱花、薄荷、青绿、琥珀、中性、薰衣草，选择即时生效并持久保存。 / App palettes: Dynamic (wallpaper/system) plus eight built-in presets — Blue, Violet, Sakura, Mint, Teal, Amber, Neutral, Lavender — applied live and persisted.
- 旧版内置主题兼容：升级后通过 LEGACY_BUILTIN 兼容状态精确保留旧内置主题外观，直到用户主动选择任一预设；不会误显示为其他配色。 / Legacy built-in compatibility: the old built-in appearance is preserved exactly through the LEGACY_BUILTIN compatibility state until the user actively chooses a preset; it is never falsely shown as another palette.
- 备份：主题状态纳入严格备份 schema v2，v1.7.1 及更早的 v1 备份仍可恢复（DYNAMIC 保持 DYNAMIC，BUILTIN 恢复为兼容内置主题）。 / Backup: theme state is included in the strict backup schema v2, and v1 backups from v1.7.1 and earlier remain restorable (DYNAMIC stays DYNAMIC; BUILTIN restores to the compatibility built-in theme).
- 历史（History）：移除“根据记录上下文推断匹配”解释性文案；推断匹配的判定与匹配行为完全不变。 / History: the explanatory “inferred match” sentence was removed; the inferred-match classification and matching behavior are unchanged.

## 兼容性 / Compatibility

- 支持从 v1.7.1 直接就地升级，无需卸载或清除数据；既有主题/夜间模式/时间制式/体重等设置全部保留。 / Direct in-place upgrade from v1.7.1 without uninstalling or clearing data; existing theme, dark mode, time format and body-weight settings are preserved.
- 本版本没有 Room 数据库 schema 迁移。 / No Room database schema migration in this release.
- Wear 与 PK 行为未变化；血药浓度仍为模型估算，不构成诊断、处方或剂量建议。 / Wear and PK behavior unchanged; PK values remain model estimates and are not diagnosis, prescription, or dosing advice.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.7.2` — versionCode `101070200`
- Wear: `1.7.2` — versionCode `1101070200`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.7.2.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.2/Evolune-Phone-v1.7.2.apk)
- [Wear APK — Evolune-Wear-v1.7.2.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.2/Evolune-Wear-v1.7.2.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.2/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `DCCCBEEEE2A6971BF35C0AFECDC3C02B701FD2ADF71345B2181EB184756B2549`
- Wear: `5EA4C5A747CE6E1177CADC618822E7BFB956D6D1D971F646FCDA85D38CA42D67`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.7.1 直接覆盖安装，无需卸载或清除数据。Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.7.1 without uninstalling the app or clearing data. Debug APKs use a different application ID and signature and are not substitutes for the official release packages.
