# 设置功能（v1.9.0）

更新日期：2026-09-29，适用 v1.9.0（设置页结构自 v1.7.2 起未再变化）。Phone 设置由扁平的 SettingsScreen 及 `ui/screens/settings/` 下的分区组件、SettingsViewModel 和 SettingsDataStore 组成；同步/备份由各自 coordinator/provider 处理。当前版本见 [Current Status](docs/evolune/CURRENT_STATUS.md)。

## 页面与功能

v1.7.2 起设置主页是一个可滚动的扁平页面，常用区块直接显示，不再需要逐级进入分类子页面（v1.7.1 已先单独加入“隐私与权限”入口）。自上而下依次为：

| 区块 | 已实现功能 | 边界 |
|---|---|---|
| 基础数据 | 手动体重（0–300 kg），用于 PK 计算 | 较新的本地/手动值受到 Health Connect freshness 保护 |
| 外观与格式 | 夜间模式（系统默认/浅色/深色/OLED 全黑）；配色来源：跟随壁纸（Dynamic，Android 12+）或从 8 个预设调色板中选择（蓝、紫罗兰、樱花、薄荷、青绿、琥珀、中性、薰衣草）；时间制式（跟随系统/12 小时/24 小时） | 从 v1.7.1 升级的旧“内置主题”会保持原有外观并显示“兼容保留”，直到用户主动选择新配色；时间制式不改变 occurrence 身份或实际记录时间 |
| 同步与备份 → 本地数据 | Evolune Portable JSON v1（导出，可选最近 30 天/90 天/全部历史；增量导入）、CSV v1（仅导出）；旧版 Legacy / Mahiro JSON v1 文件与剪贴板导入导出 | 导出与加密备份相互独立，不等同原生完整备份；剪贴板导出带隐私确认 |
| 同步与备份 → Health Connect | 可选授权、前台体重读取、provider/权限/空数据/错误状态 | 不含后台 Health Connect 或用药/PHR 写入 |
| 同步与备份 → 云备份 | Google Drive 备份与恢复：用户授权、手动加密备份、备份选择、恢复预览与校验 | 非后台/实时云同步 |
| 更新 | 当前版本、自动检查开关与手动 GitHub Release 检查 | 不上传用药内容 |
| 指南 / 隐私与权限 / 功能教程 / 关于 | 重新查看首次引导与数据边界、隐私数据使用与权限说明、功能教程（用药方案、记录、PK、Widget、Wear 与备份）、官网/开发者信息/版权与免责声明 | 引导状态独立于用药数据 |

Health Connect 读取最近 30 天的有效体重；provider 不可用或用户拒绝授权时，手动记录和核心功能仍可使用。Google Drive 使用 appDataFolder、上传回读验证与三个备份代次保留。原生备份使用 AES-256-GCM、PBKDF2-HMAC-SHA256（默认 600,000 次），恢复通过 journal 与事务协调 Room/DataStore；备份口令必须由用户保管。若恢复被中断，下次启动会依据 PREPARED journal 安全回滚（v1.8.0）。

## Phone Widget 配置

v1.6 在系统选择器提供今日计划、下一次服药、当前 E2、E2 趋势四个独立 provider。今日进度并入今日计划，旧 today-plan provider 身份保留。每个 appWidgetId 独立保存 Auto/Light/Dark、Material You 或八组预制配色以及透明度。

WidgetConfigurationActivity 在应用配置后才返回成功；取消/返回不覆盖旧值。预览与生产 RemoteViews 共享展示/颜色解析。样式由所选 provider 确定，不再要求用户从旧单 provider 中寻找所有功能。

## Wear 外观与数据

Wear App、三个新 Tile、旧兼容曲线 Tile 和三个 Complication 已交付。WearAppearanceStore 在手表本地保存系统颜色或八组预制配色；预制颜色体系与 Phone Widget 对齐，不表示 Phone 设置自动同步。用药快照来自 Phone，显示偏好和缓存都不是第二用药事实来源。表盘槽位须支持对应 Short Text 类型。

## 存储与备份边界

SettingsDataStore 保存设置偏好；计划、slots 和 DoseEvent 由 Phone Room/Repository 管理。备份格式只保存明确允许的设置；自 v1.7.2 起备份 schema v2 严格包含应用主题状态，同时保持对旧 v1 备份的恢复兼容。不能假定引导状态、Widget 宿主实例或所有设备偏好都随备份迁移。

Phone/Wear 的 Android Auto Backup 与设备迁移排除私有数据；这不等于没有应用内原生加密备份，也不等于 SQLCipher 数据库加密已经实现。

## 实现入口

- [SettingsScreen](app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt)
- [设置分区组件](app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/settings/)（基础数据、外观、配色、同步与备份、更新、导航行）
- [配色调色板权威](app/src/main/java/io/github/yingqiu0871/evolune/theme/palette/)
- [Portable 导出/导入](app/src/main/java/io/github/yingqiu0871/evolune/export/)
- [SettingsViewModel](app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/SettingsViewModel.kt)
- [SettingsDataStore](app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt)
- [Health Connect adapter](app/src/main/java/io/github/yingqiu0871/evolune/healthconnect/AndroidHealthConnectWeightProvider.kt)
- [BackupRestoreCoordinator](app/src/main/java/io/github/yingqiu0871/evolune/backup/BackupRestoreCoordinator.kt)
- [GoogleDriveBackupProvider](app/src/main/java/io/github/yingqiu0871/evolune/backup/cloud/google/GoogleDriveBackupProvider.kt)
- [WidgetConfigurationActivity](app/src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt)
