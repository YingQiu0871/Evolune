# 设置功能（截至 v1.6.0）

更新日期：2026-09-12。Phone 设置由 SettingsScreen、分类子页面、SettingsViewModel 和 SettingsDataStore 组成；同步/备份由各自 coordinator/provider 处理。当前版本见 [Current Status](docs/evolune/CURRENT_STATUS.md)。

## 页面与功能

| 入口 | 已实现功能 | 边界 |
|---|---|---|
| 体重 | 手动体重，用于 PK；可选 Health Connect 数据来源 | 较新的本地/手动值受到 freshness 保护 |
| 外观 | Light/Dark/AMOLED/System、Material You 与预制配色 | Phone Widget 按实例配置；Wear 有独立本地配色选择 |
| 时间 | System/12 小时/24 小时 | 不改变 occurrence 身份或实际记录时间 |
| 同步与备份 → 本地数据 | Mahiro JSON v1 文件/剪贴板导入导出 | 兼容交换格式，不等同原生完整备份 |
| 同步与备份 → Health Connect | 可选授权、前台体重读取、provider/权限/空数据/错误状态 | 不含后台 Health Connect 或用药/PHR 写入 |
| 同步与备份 → Google Drive | 用户授权、手动加密备份、备份选择、恢复预览与校验 | 非后台/实时云同步 |
| 更新 | 自动检查开关与手动 GitHub Release 检查 | 不上传用药内容 |
| 帮助/关于 | 功能教程、条款、隐私、医疗/PK 说明、作者与网站 | 引导状态独立于用药数据 |

Health Connect 读取最近 30 天的有效体重；provider 不可用或用户拒绝授权时，手动记录和核心功能仍可使用。Google Drive 使用 appDataFolder、上传回读验证与三个备份代次保留。原生备份使用 AES-256-GCM、PBKDF2-HMAC-SHA256（默认 600,000 次），恢复通过 journal 与事务协调 Room/DataStore；备份口令必须由用户保管。

## Phone Widget 配置

v1.6 在系统选择器提供今日计划、下一次服药、当前 E2、E2 趋势四个独立 provider。今日进度并入今日计划，旧 today-plan provider 身份保留。每个 appWidgetId 独立保存 Auto/Light/Dark、Material You 或八组预制配色以及透明度。

WidgetConfigurationActivity 在应用配置后才返回成功；取消/返回不覆盖旧值。预览与生产 RemoteViews 共享展示/颜色解析。样式由所选 provider 确定，不再要求用户从旧单 provider 中寻找所有功能。

## Wear 外观与数据

Wear App、三个新 Tile、旧兼容曲线 Tile 和三个 Complication 已交付。WearAppearanceStore 在手表本地保存系统颜色或八组预制配色；预制颜色体系与 Phone Widget 对齐，不表示 Phone 设置自动同步。用药快照来自 Phone，显示偏好和缓存都不是第二用药事实来源。表盘槽位须支持对应 Short Text 类型。

## 存储与备份边界

SettingsDataStore 保存设置偏好；计划、slots 和 DoseEvent 由 Phone Room/Repository 管理。备份格式只保存明确允许的设置，不能假定引导状态、Widget 宿主实例或所有设备偏好都随备份迁移。

Phone/Wear 的 Android Auto Backup 与设备迁移排除私有数据；这不等于没有应用内原生加密备份，也不等于 SQLCipher 数据库加密已经实现。

## 实现入口

- [SettingsScreen](app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt)
- [SyncAndBackupScreen](app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SyncAndBackupScreen.kt)
- [SettingsViewModel](app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/SettingsViewModel.kt)
- [SettingsDataStore](app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt)
- [Health Connect adapter](app/src/main/java/io/github/yingqiu0871/evolune/healthconnect/AndroidHealthConnectWeightProvider.kt)
- [BackupRestoreCoordinator](app/src/main/java/io/github/yingqiu0871/evolune/backup/BackupRestoreCoordinator.kt)
- [GoogleDriveBackupProvider](app/src/main/java/io/github/yingqiu0871/evolune/backup/cloud/google/GoogleDriveBackupProvider.kt)
- [WidgetConfigurationActivity](app/src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt)
