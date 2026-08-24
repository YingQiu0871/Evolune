package io.github.yingqiu0871.evolune.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.util.Log
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightProvider
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncCoordinator
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncEnableResult
import io.github.yingqiu0871.evolune.utils.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新检查结果
 */
sealed class UpdateCheckResult {
    data object Idle : UpdateCheckResult()
    data object Checking : UpdateCheckResult()
    data class UpdateAvailable(val tagName: String, val releaseUrl: String) : UpdateCheckResult()
    data class UpdateAvailableDismissed(val tagName: String) : UpdateCheckResult()
    data class DebugBuild(val tagName: String, val releaseUrl: String) : UpdateCheckResult()
    data class DebugBuildDismissed(val tagName: String) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data object Error : UpdateCheckResult()
}

/**
 * 设置 ViewModel
 * 管理用户设置和偏好
 */
class SettingsViewModel(
    private val settingsDataStore: SettingsStore,
    private val healthConnectWeightProvider: HealthConnectWeightProvider,
    operationScope: CoroutineScope? = null
) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val scope = operationScope ?: viewModelScope

    private val healthConnectWeightSyncCoordinator = HealthConnectWeightSyncCoordinator(
        settingsStore = settingsDataStore,
        provider = healthConnectWeightProvider,
        scope = scope
    )

    /**
     * 用户设置状态
     */
    val userSettings: StateFlow<UserSettings> = settingsDataStore.userSettings
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    /**
     * 更新检查结果状态
     */
    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateCheckResult: StateFlow<UpdateCheckResult> = _updateCheckResult.asStateFlow()

    val healthConnectWeightSyncState: StateFlow<HealthConnectWeightSyncState> =
        healthConnectWeightSyncCoordinator.state

    private val healthConnectPermissionRequestChannel = Channel<Unit>(Channel.BUFFERED)
    val healthConnectPermissionRequests = healthConnectPermissionRequestChannel.receiveAsFlow()

    private var healthConnectPermissionRequestInFlight = false
    private var pendingPermissionAction: HealthConnectPermissionAction? = null

    fun onForeground() {
        healthConnectWeightSyncCoordinator.onForeground()
    }

    /**
     * 更新体重
     */
    fun updateBodyWeight(weight: Double) {
        scope.launch {
            settingsDataStore.updateBodyWeight(weight)
        }
    }

    fun setHealthConnectWeightSyncEnabled(enabled: Boolean) {
        if (!enabled) {
            pendingPermissionAction = null
            scope.launch { healthConnectWeightSyncCoordinator.disableWeightSync() }
            return
        }
        if (healthConnectPermissionRequestInFlight) return
        scope.launch {
            when (healthConnectWeightSyncCoordinator.enableWeightSync()) {
                HealthConnectWeightSyncEnableResult.ENABLED,
                HealthConnectWeightSyncEnableResult.BLOCKED -> Unit
                HealthConnectWeightSyncEnableResult.PERMISSION_REQUIRED ->
                    requestHealthConnectPermission(HealthConnectPermissionAction.ENABLE)
            }
        }
    }

    fun requestHealthConnectReauthorization() {
        requestHealthConnectPermission(HealthConnectPermissionAction.REAUTHORIZE)
    }

    fun onHealthConnectPermissionResult() {
        val action = pendingPermissionAction
        pendingPermissionAction = null
        healthConnectPermissionRequestInFlight = false
        when (action) {
            HealthConnectPermissionAction.ENABLE -> scope.launch {
                healthConnectWeightSyncCoordinator.completeEnableAfterPermission()
            }
            HealthConnectPermissionAction.REAUTHORIZE -> scope.launch {
                healthConnectWeightSyncCoordinator.syncIfEnabled()
            }
            null -> Unit
        }
    }

    private fun requestHealthConnectPermission(action: HealthConnectPermissionAction) {
        if (healthConnectPermissionRequestInFlight) return
        pendingPermissionAction = action
        if (healthConnectPermissionRequestChannel.trySend(Unit).isSuccess) {
            healthConnectPermissionRequestInFlight = true
        } else {
            pendingPermissionAction = null
        }
    }

    /**
     * 更新主题模式
     */
    fun updateThemeMode(mode: ThemeMode) {
        scope.launch {
            settingsDataStore.updateThemeMode(mode)
        }
    }

    /**
     * 更新颜色主题
     */
    fun updateColorTheme(theme: ColorTheme) {
        scope.launch {
            settingsDataStore.updateColorTheme(theme)
        }
    }

    /**
     * 更新自动检查更新开关
     */
    fun updateAutoCheckUpdates(enabled: Boolean) {
        scope.launch {
            settingsDataStore.updateAutoCheckUpdates(enabled)
        }
    }

    /**
     * 更新时间制式
     */
    fun updateTimeFormat(format: TimeFormat) {
        scope.launch {
            settingsDataStore.updateTimeFormat(format)
        }
    }

    /**
     * 在应用启动时，若自动检查更新已开启则执行检查
     */
    fun triggerAutoCheckOnStartup(versionName: String) {
        scope.launch {
            val settings = settingsDataStore.userSettings.first()
            if (settings.autoCheckUpdates) {
                performUpdateCheck(versionName)
            }
        }
    }

    /**
     * 手动触发检查更新
     */
    fun checkForUpdates(versionName: String) {
        scope.launch {
            performUpdateCheck(versionName)
        }
    }

    /**
     * 关闭更新弹窗。若当前结果为 UpdateAvailable 或 DebugBuild，
     * 则保留版本信息（转为 Dismissed 状态），否则重置为 Idle。
     */
    fun dismissUpdateCheckResult() {
        _updateCheckResult.value = when (val current = _updateCheckResult.value) {
            is UpdateCheckResult.UpdateAvailable -> UpdateCheckResult.UpdateAvailableDismissed(current.tagName)
            is UpdateCheckResult.DebugBuild -> UpdateCheckResult.DebugBuildDismissed(current.tagName)
            else -> UpdateCheckResult.Idle
        }
    }

    private suspend fun performUpdateCheck(versionName: String) {
        _updateCheckResult.value = UpdateCheckResult.Checking
        try {
            val release = withContext(Dispatchers.IO) {
                UpdateChecker.fetchLatestRelease()
            }
            val isDebug = versionName.contains("debug", ignoreCase = true)
            _updateCheckResult.value = when {
                release == null -> UpdateCheckResult.Error
                UpdateChecker.isNewerVersion(release.tagName, versionName) ->
                    UpdateCheckResult.UpdateAvailable(release.tagName, release.releaseUrl)
                isDebug ->
                    UpdateCheckResult.DebugBuild(release.tagName, release.releaseUrl)
                else -> UpdateCheckResult.UpToDate
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            _updateCheckResult.value = UpdateCheckResult.Error
        }
    }

    override fun onCleared() {
        healthConnectWeightSyncCoordinator.close()
        super.onCleared()
    }
}

/**
 * SettingsViewModel 工厂类
 */
class SettingsViewModelFactory(
    private val settingsDataStore: SettingsStore,
    private val healthConnectWeightProvider: HealthConnectWeightProvider
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                settingsDataStore,
                healthConnectWeightProvider
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

private enum class HealthConnectPermissionAction {
    ENABLE,
    REAUTHORIZE
}
