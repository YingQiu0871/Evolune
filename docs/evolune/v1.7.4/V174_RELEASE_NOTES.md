# Evolune v1.7.4 正式版 / Official Release

Evolune v1.7.4 是一个导航动效精修版本：在保留 v1.7.3 防卡顿布局切换机制的前提下，恢复全屏子页面（历史↔用药洞察、历史↔回顾性 PK、历史↔用药时间线、设置↔关于）进入/退出时的柔和动效。目标页面在最终布局几何内执行 220 ms 的淡入 + 轻微 0.98→1.0 缩放；前进与返回对称生效，同几何页面切换动画保持不变。
Evolune v1.7.4 is a navigation-motion refinement release: it restores the soft entrance motion for full-screen child pages (History ↔ Insights, History ↔ Retrospective PK, History ↔ Timeline, Settings ↔ About) while keeping the v1.7.3 anti-jank layout mechanism. The destination page animates inside its settled geometry with a 220 ms fade plus a subtle 0.98 → 1.0 scale; forward and Back behave symmetrically, and same-geometry transitions keep their existing animation.

## 主要更新 / Highlights

- 恢复并精修全屏子页面导航动效：进入/退出时目标页面以柔和淡入 + 轻微缩放出现，动效在最终布局尺寸确定之后开始。 / Restored and refined full-screen child-page navigation motion: the destination appears with a soft fade and subtle scale, starting only after the final layout geometry has settled.
- 保留 v1.7.3 防卡顿规则：主导航栏显隐仍即时切换，不再出现页面展开/收缩的几何错位；同几何页面切换不叠加双重动画。 / The v1.7.3 anti-jank rule is preserved: primary navigation chrome still switches instantly, the expand/shrink geometry glitch cannot return, and same-geometry transitions do not double-animate.
- 未改变用药记录、History、Insights、回顾性 PK、Timeline、Wear、Widget 或 PK 行为；无数据库 schema 变化。 / No medication, History, Insights, retrospective PK, Timeline, Wear, Widget or PK behavior changes; no database schema changes.

## 兼容性 / Compatibility

- 支持从 v1.7.3 直接就地升级，无需卸载或清除数据。 / Direct in-place upgrade from v1.7.3 without uninstalling or clearing data.
- 本版本没有 Room 数据库 schema 迁移；没有备份格式变化。 / No Room database schema migration and no backup format change in this release.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.7.4` — versionCode `101070400`
- Wear: `1.7.4` — versionCode `1101070400`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.7.4.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.4/Evolune-Phone-v1.7.4.apk)
- [Wear APK — Evolune-Wear-v1.7.4.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.4/Evolune-Wear-v1.7.4.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.7.4/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `243049B56FC5C3431CE08897B109BC583C86B9002DC63FB6D1849B9305089BC2`
- Wear: `93A6980F74AC81AD6319492730677A361EE550DC93D96F23D9866A21ADDD6303`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.7.3 直接覆盖安装，无需卸载或清除数据。Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.7.3 without uninstalling the app or clearing data. Debug APKs use a different application ID and signature and are not substitutes for the official release packages.
