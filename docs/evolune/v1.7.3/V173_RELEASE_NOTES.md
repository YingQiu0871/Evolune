# Evolune v1.7.3 正式版 / Official Release

Evolune v1.7.3 是一个导航体验修复版本：修复进入/退出部分全屏子页面（历史↔用药洞察、历史↔回顾性 PK、历史↔用药时间线、设置↔关于、设置↔Google Drive 备份）时可稳定复现的页面展开/收缩卡顿。当主导航栏显隐会改变页面几何时，不再同时执行整页缩放/淡入淡出动画；普通同布局页面切换动画保持不变。
Evolune v1.7.3 is a navigation-experience hotfix: it fixes the deterministic expand/shrink stutter when entering or leaving selected full-screen child pages (History ↔ Insights, History ↔ Retrospective PK, History ↔ Timeline, Settings ↔ About, Settings ↔ Google Drive backup). Page transition animation is suppressed only when primary navigation chrome visibility changes the layout geometry; normal same-geometry transitions keep their existing animation.

## 主要更新 / Highlights

- 修复部分全屏子页面进入/退出时的展开/收缩卡顿：当底部导航栏/侧边导航栏的显隐会改变页面几何时，取消该次导航的整页缩放/淡入淡出；前进与返回对称生效。 / Fixed the expand/shrink stutter on selected full-screen child pages: when the bottom bar / navigation rail visibility changes the layout geometry, the page scale/fade is suppressed for that navigation; forward and Back behave symmetrically.
- 普通同几何页面切换（主页/记录/历史/方案/设置之间，以及全屏子页面之间）动画保持不变。 / Normal same-geometry transitions (between top-level tabs and between full-screen children) keep the existing animation.
- 未改变用药记录、History、Insights、回顾性 PK、Timeline、Wear、Widget 或 PK 行为；无数据库 schema 变化。 / No medication, History, Insights, retrospective PK, Timeline, Wear, Widget or PK behavior changes; no database schema changes.

## 兼容性 / Compatibility

- 支持从 v1.7.2 直接就地升级，无需卸载或清除数据。 / Direct in-place upgrade from v1.7.2 without uninstalling or clearing data.
- 本版本没有 Room 数据库 schema 迁移；没有备份格式变化。 / No Room database schema migration and no backup format change in this release.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.7.3` — versionCode `101070300`
- Wear: `1.7.3` — versionCode `1101070300`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.7.3.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.3/Evolune-Phone-v1.7.3.apk)
- [Wear APK — Evolune-Wear-v1.7.3.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.3/Evolune-Wear-v1.7.3.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.3/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `CB535CA7C6D0E0A73110AF3B91836B9DF89989F65E42A84F10D1E674D5EEFCAC`
- Wear: `DA398A340ABD0FF8955658C5C4D900BAE01A6A7CC15B9487E14EC6FBCC7822DF`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.7.2 直接覆盖安装，无需卸载或清除数据。Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.7.2 without uninstalling the app or clearing data. Debug APKs use a different application ID and signature and are not substitutes for the official release packages.
