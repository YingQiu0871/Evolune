# 设置功能（v1.11.1）

更新日期：2026-10-01，适用 v1.11.1（v1.10.0 按用途重新分组了设置页；v1.11.0 起设置页作为主页面之一使用毛玻璃顶栏/底栏，v1.11.0 与 v1.11.1 未改动设置项本身）。Phone 设置由扁平的 SettingsScreen 及 `ui/screens/settings/` 下的分区组件、SettingsViewModel 和 SettingsDataStore 组成；同步/备份由各自 coordinator/provider 处理。当前版本见 [Current Status](CURRENT_STATUS.md)。

## 页面与功能

设置主页是一个可滚动的扁平页面（v1.7.2 起），v1.10.0 起按用途分为四组，自上而下依次为：

| 区块 | 已实现功能 | 边界 |
|---|---|---|
| 血药浓度计算 | 手动体重（0–300 kg），用于 PK 计算；从 Health Connect 同步体重（可选授权、前台读取、provider/权限/空数据/错误状态、管理权限入口）；显示 CPA 估算曲线（默认关闭，v1.10.0） | 较新的本地/手动值受到 Health Connect freshness 保护；不含后台 Health Connect 或用药/PHR 写入；CPA 开关是本机显示偏好，不进入备份/恢复/导出 |
| 外观 | 夜间模式（浅色/深色/OLED 全黑/系统默认，分段按钮）；配色来源：跟随壁纸（Dynamic，Android 12+）或预设配色——8 个预设调色板（蓝、紫罗兰、樱花、薄荷、青绿、琥珀、中性、薰衣草）仅在选择预设配色后显示；时间制式（跟随系统/12 小时/24 小时，分段按钮） | 从 v1.7.1 升级的旧“内置主题”会保持原有外观并显示“兼容保留”，直到用户主动选择新配色；时间制式不改变 occurrence 身份或实际记录时间 |
| 备份与数据 | Google Drive 备份与恢复（置顶：用户授权、手动加密备份、备份选择、恢复预览与校验）；Evolune Portable JSON v1（导出，可选最近 30 天/90 天/全部历史；增量导入）、CSV v1（仅导出）；Legacy / Mahiro JSON v1 文件与剪贴板导入导出（默认收起） | 非后台/实时云同步；导出与加密备份相互独立，不等同原生完整备份；剪贴板导出带隐私确认 |
| 关于与帮助 | 使用帮助（功能教程；首次引导——使用条款、数据边界与医疗免责声明）、隐私与权限、自动检查更新与手动 GitHub Release 检查、当前版本、关于（官网/开发者信息/版权与免责声明） | 引导状态独立于用药数据；更新检查不上传用药内容 |

Health Connect 读取最近 30 天的有效体重；provider 不可用或用户拒绝授权时，手动记录和核心功能仍可使用。Google Drive 使用 appDataFolder、上传回读验证与三个备份代次保留。原生备份使用 AES-256-GCM、PBKDF2-HMAC-SHA256（默认 600,000 次），恢复通过 journal 与事务协调 Room/DataStore；备份口令必须由用户保管。若恢复被中断，下次启动会依据 PREPARED journal 安全回滚（v1.8.0）。

「使用帮助」打开 HelpScreen，从中进入功能教程或首次引导。自 v1.11.0 起，设置页内容会滚动到半透明的毛玻璃顶栏和底部导航栏下方，首尾留出栏高，不会被遮挡。

## Phone Widget 配置

v1.6 在系统选择器提供今日计划、下一次服药、当前 E2、E2 趋势四个独立 provider。今日进度并入今日计划，旧 today-plan provider 身份保留。每个 appWidgetId 独立保存 Auto/Light/Dark、Material You 或八组预制配色以及透明度。

WidgetConfigurationActivity 在应用配置后才返回成功；取消/返回不覆盖旧值。预览与生产 RemoteViews 共享展示/颜色解析。样式由所选 provider 确定，不再要求用户从旧单 provider 中寻找所有功能。

## Wear 外观与数据

Wear App、三个新 Tile、旧兼容曲线 Tile 和三个 Complication 已交付。WearAppearanceStore 在手表本地保存系统颜色或八组预制配色；预制颜色体系与 Phone Widget 对齐，不表示 Phone 设置自动同步。用药快照来自 Phone，显示偏好和缓存都不是第二用药事实来源。表盘槽位须支持对应 Short Text 类型。

## 存储与备份边界

SettingsDataStore 保存设置偏好；计划、slots 和 DoseEvent 由 Phone Room/Repository 管理。备份格式只保存明确允许的设置；自 v1.7.2 起备份 schema v2 严格包含应用主题状态，同时保持对旧 v1 备份的恢复兼容。不能假定引导状态、Widget 宿主实例或所有设备偏好都随备份迁移。

Phone/Wear 的 Android Auto Backup 与设备迁移排除私有数据；这不等于没有应用内原生加密备份，也不等于 SQLCipher 数据库加密已经实现。

## 实现入口

- [SettingsScreen](../../app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt)
- [HelpScreen](../../app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/HelpScreen.kt)（使用帮助，v1.10.0）
- [设置分区组件](../../app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/settings/)（血药浓度计算、外观、配色、备份与数据、更新、关于与帮助）
- [配色调色板权威](../../app/src/main/java/io/github/yingqiu0871/evolune/theme/palette/)
- [Portable 导出/导入](../../app/src/main/java/io/github/yingqiu0871/evolune/export/)
- [SettingsViewModel](../../app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/SettingsViewModel.kt)
- [SettingsDataStore](../../app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt)
- [Health Connect adapter](../../app/src/main/java/io/github/yingqiu0871/evolune/healthconnect/AndroidHealthConnectWeightProvider.kt)
- [BackupRestoreCoordinator](../../app/src/main/java/io/github/yingqiu0871/evolune/backup/BackupRestoreCoordinator.kt)
- [GoogleDriveBackupProvider](../../app/src/main/java/io/github/yingqiu0871/evolune/backup/cloud/google/GoogleDriveBackupProvider.kt)
- [WidgetConfigurationActivity](../../app/src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt)
