# Evolune v1.8.0 正式版 / Official Release

Evolune v1.8.0 是一个性能与稳定性改进版本：介绍页与用药历史的加载、刷新和月份切换更流畅，轻量化的后台处理减少了界面线程的负担；回顾性 PK 曲线计算移至后台执行；页面生命周期与资源管理得到改进；并修复了恢复过程被中断后可能导致的启动失败问题。
Evolune v1.8.0 is a performance and reliability release: Home and medication History load, refresh and month switching more smoothly with lighter background processing, retrospective PK curve computation now runs in the background, lifecycle and resource handling were improved, and an interrupted-restore issue that could block startup was fixed.

## 主要更新 / Highlights

- 提升整体响应速度：历史页面加载与月份切换更流畅，沉重的数据处理不再占用界面线程。 / Improved overall responsiveness: History loading and month switching are smoother, and heavy processing no longer occupies the UI thread.
- 首页与用药历史在数据变化后的刷新更轻快，减少不必要的重复计算。 / Home and medication history refresh more lightly after data changes, with less redundant computation.
- 回顾性 PK 曲线计算移至后台执行，页面在计算期间保持可操作。 / Retrospective PK curve computation runs in the background, keeping the screen usable while it calculates.
- 改进应用生命周期与资源管理：首页时钟与边界刷新遵循生命周期，减少后台无效工作。 / Better lifecycle and resource management: the Home clock and boundary refresh respect the app lifecycle, reducing unnecessary background work.
- 更安全的中断恢复：若恢复过程被打断，下次启动可以正确回滚并继续正常使用，不会卡在启动失败。 / Safer interrupted-restore recovery: if a restore is interrupted, the next launch rolls back correctly and keeps working instead of getting stuck at startup.
- 更新检查的取消处理更可靠。 / More reliable update-check cancellation handling.
- 未改变用药记录、History、Insights、回顾性 PK、Timeline、Wear、Widget 行为；无数据库 schema 变化。 / No medication, History, Insights, retrospective PK, Timeline, Wear or Widget behavior changes; no database schema changes.

## 兼容性 / Compatibility

- 支持从 v1.7.4 直接就地升级，无需卸载或清除数据。 / Direct in-place upgrade from v1.7.4 without uninstalling or clearing data.
- 本版本没有 Room 数据库 schema 迁移；没有备份格式变化。 / No Room database schema migration and no backup format change in this release.

## 版本信息 / Version Information

- Application ID: `io.github.yingqiu0871.evolune`
- Phone: `1.8.0` — versionCode `101080000`
- Wear: `1.8.0` — versionCode `1101080000`

## 安装包 / APKs

- [Phone APK — Evolune-Phone-v1.8.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.8.0/Evolune-Phone-v1.8.0.apk)
- [Wear APK — Evolune-Wear-v1.8.0.apk](https://github.com/YingQiu0871/Evolune/releases/download/v1.8.0/Evolune-Wear-v1.8.0.apk)
- [SHA-256 清单 / checksum manifest — SHA256SUMS.txt](https://github.com/YingQiu0871/Evolune/releases/download/v1.8.0/SHA256SUMS.txt)

## SHA-256 校验值 / SHA-256 Checksums

- Phone: `B77CB794D85D8C1F7B49C09B94344EFFA40F21556C6ACCEEECCBE29DB0C63138A`
- Wear: `DFFFFA34EE9BD91FB6AB5D8A8EAA51CF8FA76DCEA64112E4F4C39A0A7C115D1A`
- Release certificate: `B9B6B9552FA4C7B656936D4C3AEB71C1229AA17C393337719BC8D0E07EDAAB08`

可从 v1.7.4 直接覆盖安装，无需卸载或清除数据。Debug APK 使用不同应用 ID 与签名，不能替代正式发布包。
You can upgrade directly from v1.7.4 without uninstalling the app or clearing data. Debug APKs use a different application ID and signature and are not substitutes for the official release packages.
