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
import java.time.Clock
import java.time.Instant

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
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,  // 默认跟随系统时间制式
    /** User intent for foreground-only Health Connect weight adoption. */
    val healthConnectWeightSyncEnabled: Boolean = false,
    /** Sync metadata only; [bodyWeight] remains the local authority. */
    val lastHealthConnectWeightKg: Double? = null,
    /** Shared freshness barrier for HC observations and local authority writes. */
    val lastHealthConnectWeightAdoptedAt: Instant? = null
)

const val MAX_BODY_WEIGHT_KG = 300.0

fun isValidBodyWeight(weight: Double): Boolean =
    weight.isFinite() && weight > 0.0 && weight <= MAX_BODY_WEIGHT_KG

interface SettingsStore {
    val userSettings: Flow<UserSettings>

    suspend fun updateBodyWeight(weight: Double): Boolean
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateColorTheme(theme: ColorTheme)
    suspend fun updateAutoCheckUpdates(enabled: Boolean)
    suspend fun updateTimeFormat(format: TimeFormat)
    suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean)
    suspend fun updateBodyWeightFromHealthConnect(weight: Double, adoptedAt: Instant): Boolean
    suspend fun updateHealthConnectWeightMetadata(weight: Double, adoptedAt: Instant): Boolean
}

/**
 * Atomic replacement seam used by crash-safe restore. Implementations must
 * update all settings in one DataStore edit.
 */
interface AtomicSettingsStore {
    /** Replace all restore-owned scalar settings in one DataStore edit. */
    suspend fun replaceSettings(settings: UserSettings): Boolean
}

/**
 * 设置数据存储管理类
 */
class SettingsDataStore(
    private val context: Context,
    private val clock: Clock = Clock.systemUTC()
) : SettingsStore, AtomicSettingsStore {
    
    companion object {
        private val BODY_WEIGHT_KEY = doublePreferencesKey("body_weight")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val COLOR_THEME_KEY = stringPreferencesKey("color_theme")
        private val AUTO_CHECK_UPDATES_KEY = booleanPreferencesKey("auto_check_updates")
        private val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
        private val HEALTH_CONNECT_WEIGHT_SYNC_ENABLED_KEY =
            booleanPreferencesKey("health_connect_weight_sync_enabled")
        private val LAST_HEALTH_CONNECT_WEIGHT_KG_KEY =
            doublePreferencesKey("last_health_connect_weight_kg")
        private val LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY =
            stringPreferencesKey("last_health_connect_weight_adopted_at")
    }
    
    /**
     * 获取用户设置的 Flow
     */
    override val userSettings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
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
            } ?: TimeFormat.SYSTEM,
            healthConnectWeightSyncEnabled =
                preferences[HEALTH_CONNECT_WEIGHT_SYNC_ENABLED_KEY] ?: false,
            lastHealthConnectWeightKg = preferences[LAST_HEALTH_CONNECT_WEIGHT_KG_KEY],
            lastHealthConnectWeightAdoptedAt = preferences[LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY]
                ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
        )
    }
    
    /**
     * 保存体重
     */
    override suspend fun updateBodyWeight(weight: Double): Boolean {
        if (!isValidBodyWeight(weight)) return false

        context.dataStore.edit { preferences ->
            preferences[BODY_WEIGHT_KEY] = weight
            preferences[LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY] = clock.instant().toString()
        }
        return true
    }
    
    /**
     * 保存主题模式
     */
    override suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
    
    /**
     * 保存颜色主题
     */
    override suspend fun updateColorTheme(theme: ColorTheme) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_THEME_KEY] = theme.name
        }
    }

    /**
     * 保存自动检查更新开关
     */
    override suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CHECK_UPDATES_KEY] = enabled
        }
    }

    /**
     * 保存时间制式
     */
    override suspend fun updateTimeFormat(format: TimeFormat) {
        context.dataStore.edit { preferences ->
            preferences[TIME_FORMAT_KEY] = format.name
        }
    }

    override suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HEALTH_CONNECT_WEIGHT_SYNC_ENABLED_KEY] = enabled
        }
    }

    override suspend fun updateBodyWeightFromHealthConnect(
        weight: Double,
        adoptedAt: Instant
    ): Boolean {
        if (!isValidBodyWeight(weight)) return false

        context.dataStore.edit { preferences ->
            preferences[BODY_WEIGHT_KEY] = weight
            preferences[LAST_HEALTH_CONNECT_WEIGHT_KG_KEY] = weight
            preferences[LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY] = adoptedAt.toString()
        }
        return true
    }

    override suspend fun updateHealthConnectWeightMetadata(
        weight: Double,
        adoptedAt: Instant
    ): Boolean {
        if (!isValidBodyWeight(weight)) return false

        context.dataStore.edit { preferences ->
            preferences[LAST_HEALTH_CONNECT_WEIGHT_KG_KEY] = weight
            preferences[LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY] = adoptedAt.toString()
        }
        return true
    }

    override suspend fun replaceSettings(settings: UserSettings): Boolean {
        if (!isValidBodyWeight(settings.bodyWeight)) return false

        context.dataStore.edit { preferences ->
            preferences[BODY_WEIGHT_KEY] = settings.bodyWeight
            preferences[LAST_HEALTH_CONNECT_WEIGHT_ADOPTED_AT_KEY] = clock.instant().toString()
            preferences[THEME_MODE_KEY] = settings.themeMode.name
            preferences[COLOR_THEME_KEY] = settings.colorTheme.name
            preferences[AUTO_CHECK_UPDATES_KEY] = settings.autoCheckUpdates
            preferences[TIME_FORMAT_KEY] = settings.timeFormat.name
        }
        return true
    }
}
