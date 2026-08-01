# 设置页面功能文档

## 概述
已成功创建设置页面，提供体重输入、夜间模式选择和颜色主题选择功能。

## 实现的功能

### 1. 体重输入
- **位置**: [SettingsScreen.kt](app/src/main/java/io/github/yuninggu/hrttracker/ui/screens/SettingsScreen.kt)
- **功能**: 允许用户输入体重（单位：kg）
- **作用**: 影响血药浓度计算
- **验证**: 输入范围 0-300 kg
- **默认值**: 55.0 kg

### 2. 夜间模式选择
- **选项**:
  - **浅色**: 始终使用浅色主题
  - **深色**: 始终使用深色主题
  - **系统默认**: 跟随系统设置
- **默认值**: 系统默认

### 3. 颜色主题选择
- **选项**:
  - **动态着色**: 跟随系统动态着色（Android 12+）
  - **内置配色**: 使用应用内置配色方案
- **默认值**: 动态着色

## 技术实现

### 数据持久化
- 使用 **DataStore Preferences** 存储用户设置
- 文件: [SettingsDataStore.kt](app/src/main/java/io/github/yuninggu/hrttracker/data/SettingsDataStore.kt)
- 自动持久化，应用重启后保持设置

### ViewModel
- 文件: [SettingsViewModel.kt](app/src/main/java/io/github/yuninggu/hrttracker/viewmodel/SettingsViewModel.kt)
- 使用 Kotlin Flow 提供响应式数据流
- 提供更新设置的方法

### 主题系统
- 更新 [Theme.kt](app/src/main/java/io/github/yuninggu/hrttracker/ui/theme/Theme.kt)
- 支持根据用户设置动态切换主题
- 集成系统动态着色（Material You）

### 导航集成
- 更新 [Screen.kt](app/src/main/java/io/github/yuninggu/hrttracker/navigation/Screen.kt)
- 更新 [AppNavigation.kt](app/src/main/java/io/github/yuninggu/hrttracker/navigation/AppNavigation.kt)
- 在底部导航栏添加设置图标

## 使用方式

1. **访问设置**: 点击底部导航栏的"设置"图标
2. **修改体重**: 直接在输入框中输入新的体重值
3. **切换夜间模式**: 点击对应的模式选项
4. **切换颜色主题**: 点击对应的主题选项
5. **自动保存**: 所有更改会立即自动保存

## 文件清单

### 新增文件
- `app/src/main/java/io/github/yuninggu/hrttracker/data/SettingsDataStore.kt` - 设置数据存储
- `app/src/main/java/io/github/yuninggu/hrttracker/viewmodel/SettingsViewModel.kt` - 设置 ViewModel
- `app/src/main/java/io/github/yuninggu/hrttracker/ui/screens/SettingsScreen.kt` - 设置页面 UI

### 修改文件
- `gradle/libs.versions.toml` - 添加 DataStore 依赖版本
- `app/build.gradle.kts` - 添加 DataStore 依赖
- `app/src/main/java/io/github/yuninggu/hrttracker/ui/theme/Theme.kt` - 支持主题设置
- `app/src/main/java/io/github/yuninggu/hrttracker/navigation/Screen.kt` - 添加设置路由
- `app/src/main/java/io/github/yuninggu/hrttracker/navigation/AppNavigation.kt` - 集成设置页面
- `app/src/main/java/io/github/yuninggu/hrttracker/MainActivity.kt` - 集成设置数据流

## 注意事项

1. **体重同步**: MainActivity 现在会从用户设置中读取体重并传递给 HRTViewModel
2. **主题实时更新**: 主题更改会立即生效，无需重启应用
3. **动态着色**: 仅在 Android 12+ 设备上可用
4. **Preview**: SettingsScreen 包含浅色和深色预览
