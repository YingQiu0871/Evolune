package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore 实例
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 夜间模式枚举
 */
enum class ThemeMode {
    LIGHT,      // 浅色
    DARK,       // 深色
    AMOLED,     // OLED/AMOLED 纯黑
    SYSTEM      // 系统默认
}

/**
 * 颜色主题枚举
 */
enum class ColorTheme {
    DYNAMIC,    // 跟随系统动态着色
    BUILTIN     // 使用内置配色方案
}

/**
 * 时间制式枚举
 */
enum class TimeFormat {
    SYSTEM,     // 跟随系统设置
    HOUR_12,    // 12小时制
    HOUR_24     // 24小时制
}

/**
 * 用户设置数据类
 */
data class UserSettings(
    val bodyWeight: Double = 55.0,           // 默认体重 55kg
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    val autoCheckUpdates: Boolean = true,    // 默认开启自动检查更新
    val timeFormat: TimeFormat = TimeFormat.SYSTEM  // 默认跟随系统时间制式
)

/**
 * 设置数据存储管理类
 */
class SettingsDataStore(private val context: Context) {
    
    companion object {
        private val BODY_WEIGHT_KEY = doublePreferencesKey("body_weight")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
        private val AUTO_CHECK_UPDATES_KEY = booleanPreferencesKey("auto_check_updates")
        private val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
    }
    
    /**
     * 获取用户设置的 Flow
     */
    val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            bodyWeight = preferences[BODY_WEIGHT_KEY] ?: 55.0,
            themeMode = preferences[THEME_MODE_KEY]?.let { 
                try { 
                    ThemeMode.valueOf(it) 
                } catch (e: IllegalArgumentException) { 
                    ThemeMode.SYSTEM 
                }
            } ?: ThemeMode.SYSTEM,
            colorTheme = preferences[COLOR_THEME_KEY]?.let {
                try {
                    ColorTheme.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    ColorTheme.DYNAMIC
                }
            } ?: ColorTheme.DYNAMIC,
            autoCheckUpdates = preferences[AUTO_CHECK_UPDATES_KEY] ?: true,
            timeFormat = preferences[TIME_FORMAT_KEY]?.let {
                try {
                    TimeFormat.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    TimeFormat.SYSTEM
                }
            } ?: TimeFormat.SYSTEM
        )
    }
    
    /**
     * 保存体重
     */
    suspend fun updateBodyWeight(weight: Double) {
        context.dataStore.edit { preferences ->
            preferences[BODY_WEIGHT_KEY] = weight
        }
    }
    
    /**
     * 保存主题模式
     */
    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
    
    /**
     * 保存颜色主题
     */
    suspend fun updateColorTheme(theme: ColorTheme) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_THEME_KEY] = theme.name
        }
    }

    /**
     * 保存自动检查更新开关
     */
    suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CHECK_UPDATES_KEY] = enabled
        }
    }

    /**
     * 保存时间制式
     */
    suspend fun updateTimeFormat(format: TimeFormat) {
        context.dataStore.edit { preferences ->
            preferences[TIME_FORMAT_KEY] = format.name
        }
    }
}
