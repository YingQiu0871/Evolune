# 设置功能

设置页是当前 Phone Compose UI 的一部分，由 `SettingsScreen`、`SettingsViewModel`
和 `SettingsDataStore` 组成。本文只记录已经实现的设置，不把未来 v1.2 集成写成
当前功能。

## 已实现设置

### 体重

体重以 kg 保存，默认值为 55 kg，并用于 PK 浓度换算。输入在 0–300 kg 范围内校验；
修改后通过 Flow 传递给主页/PK 计算。

### 应用主题

`ThemeMode` 支持：

- `LIGHT`：浅色；
- `DARK`：深色；
- `AMOLED`：深色背景；
- `SYSTEM`：跟随系统。

### 应用颜色主题

`ColorTheme` 支持 `DYNAMIC`（Android 12+ Material You）和应用内置配色。该选择只
影响 Evolune Phone Compose UI。

### 时间制式

时间显示支持 `SYSTEM`、`HOUR_12` 和 `HOUR_24`。计划时间仍以本地分钟精度保存。

### 自动检查更新

用户可以开启或关闭从 GitHub Releases 检查新稳定版本的功能；该检查不上传用药内容。

### 数据与帮助入口

设置页提供 Mahiro JSON v1 文件/剪贴板导入导出、更新检查、关于、版权和免责声明
入口。导出文件由用户自行保存；当前 Phone/Wear 私有数据不使用 Android Auto Backup
或设备迁移复制。

## Phone Widget 是独立配置

桌面 Widget 外观不是全局应用主题的副作用。用户在官方 AppWidget 配置路径中对每个
Widget 实例单独选择：

- Auto/Light/Dark；
- Material You 或 curated Monet 调色板；
- 30%–100% 背景透明度。

`WidgetAppearance` 按 `appWidgetId` 隔离保存；`WidgetConfigurationActivity` 的
预览与生产 RemoteViews 共享同一调色板、背景合成和前景对比度解析。修改应用主题
不会覆盖已配置 Widget 的独立外观。

## 持久化边界

`SettingsDataStore` 只保存设置偏好。计划、槽位和 DoseEvent 仍由 Phone Room 与
Repository 管理；Widget 配置是展示偏好，不是第二份用药数据源。Health Connect、
Google backup/restore、完整 Wear App 和 Widget Gallery 均不是当前设置功能，分别
规划在 v1.2、v1.3 和 v1.6。

## 相关实现

- [SettingsScreen.kt](app/src/main/java/io/github/yingqiu0871/evolune/ui/screens/SettingsScreen.kt)
- [SettingsViewModel.kt](app/src/main/java/io/github/yingqiu0871/evolune/viewmodel/SettingsViewModel.kt)
- [SettingsDataStore.kt](app/src/main/java/io/github/yingqiu0871/evolune/data/SettingsDataStore.kt)
- [WidgetConfigurationActivity.kt](app/src/main/java/io/github/yingqiu0871/evolune/widget/WidgetConfigurationActivity.kt)
