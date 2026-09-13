# v1.3 / v1.3.1 发布回顾

本页于 2026-09-12 根据 GitHub Release、tag 与当前源码补记，不冒充当年的新增测试记录。

| 版本 | 发布日（UTC） | 源码提交 | 结果 |
|---|---|---|---|
| [v1.3.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.3.0) | 2026-09-01 | `6d912919a118c58d835242126da624bc8e879cd6` | 首个完整 Wear App；该版 Wear APK 有 taskAffinity 安装缺陷，Phone 不受此安装问题影响 |
| [v1.3.1](https://github.com/YingQiu0871/Evolune/releases/tag/v1.3.1) | 2026-09-02 | `f6a8dad5a7d4754fa7768672fd04ace4ea79af83` | 修复 Wear 安装问题、调整品牌居中，Release 记录真实 Phone/Wear 升级与同步验证 |

Wear App 提供最近已确认记录、后续最多五个 occurrence、两步确认、最近记录精确撤销和当前 E2 估算，并区分等待、同步、离线、过期、空和错误状态。

共享协议位于 experience-core 的 WearAppProtocol、WearAppConfirmationProtocol 与 WearAppUndoProtocol。Phone 负责权威计算、持久化和回执；Wear 使用可重建缓存及可重试命令。旧 DoseTileService 与旧 Data Layer 路径保留。

v1.6 在此基础上扩展 Tile/Complication、todaySummary、外观与精确跳过提醒。不能把整个 Wear App 的首次交付时间改写成 v1.6，也不能因 v1.3.0 的构建通过忽略其公开 APK 安装缺陷。

当前用户应安装 [v1.6.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.6.0)，无需退回旧版。全部版本见 [总回顾](../DOCUMENTATION_REVIEW_V16_2026-09-12.md)。
