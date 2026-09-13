# v1.2 文档导航与发布状态

2026-09-12 文档补记：[v1.2.0](https://github.com/YingQiu0871/Evolune/releases/tag/v1.2.0) 已于 2026-08-28 发布；[v1.2.2](https://github.com/YingQiu0871/Evolune/releases/tag/v1.2.2) 于 2026-08-30 发布。当前稳定版本是 v1.6.0。

本目录保存分阶段设计、实现契约和 RC 过程记录。早期的 NOT TESTED、Release Ready NO、尚需 owner review 都是当时状态；不能据此将 Health Connect/Google Drive 描述成截至 v1.6 尚未实现，也不能把旧行全部改成 PASS。

| 阅读目的 | 文档 |
|---|---|
| 原生备份格式 | [B1](V12_B1_BACKUP_FORMAT.md) |
| 恢复协议与 journal | [B2](V12_B2_RESTORE_PROTOCOL.md) |
| Drive provider/OAuth 环境边界 | [B3](V12_B3_GOOGLE_DRIVE_SETUP.md) |
| 用户备份/恢复流程 | [B4](V12_B4_BACKUP_RESTORE_UX.md) |
| 前台 Health Connect 体重读取 | [HC4](V12_HC4_WEIGHT_SYNC.md) |
| 历史完整 RC 记录 | [RC acceptance](V12_RC_ACCEPTANCE.md) |
| 当前累计功能与全部文件 | [Current Status](../CURRENT_STATUS.md)、[文档索引](../DOCUMENTATION_INDEX.md) |

最终实现仅含前台授权体重读取和手动加密备份/恢复；不含 Health Connect 用药/PHR 写入、后台读取或实时云同步。原生加密备份不等同 SQLCipher；Android Auto Backup 排除规则不等同没有应用内主动备份。

公开 v1.2.1 tag/Release 未在本次检索中发现；不得将历史热修复分支名称当作公开发布证据。
