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
import io.github.yingqiu0871.evolune.data.isValidBodyWeight
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailability
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailabilityResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectPermissionResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightObservation
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightProvider
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightResult
import io.github.yingqiu0871.evolune.utils.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

enum class HealthConnectWeightUiError {
    AVAILABILITY_CHECK_FAILED,
    PERMISSION_CHECK_FAILED,
    READ_FAILED,
    ADOPTION_FAILED,
    INVALID_WEIGHT
}

sealed interface HealthConnectWeightUiState {
    data object Idle : HealthConnectWeightUiState
    data object Checking : HealthConnectWeightUiState
    data object PermissionNeeded : HealthConnectWeightUiState
    data object Loading : HealthConnectWeightUiState
    data class Preview(val observation: HealthConnectWeightObservation) : HealthConnectWeightUiState
    data object NoData : HealthConnectWeightUiState
    data class Unavailable(val availability: HealthConnectAvailability) : HealthConnectWeightUiState
    data object UpdateRequired : HealthConnectWeightUiState
    data class Adopting(val observation: HealthConnectWeightObservation) : HealthConnectWeightUiState
    data class Adopted(val weightKg: Double) : HealthConnectWeightUiState
    data class Error(val reason: HealthConnectWeightUiError) : HealthConnectWeightUiState
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

    private val _healthConnectWeightState =
        MutableStateFlow<HealthConnectWeightUiState>(HealthConnectWeightUiState.Idle)
    val healthConnectWeightState: StateFlow<HealthConnectWeightUiState> =
        _healthConnectWeightState.asStateFlow()

    private val _healthConnectPermissionRequestVersion = MutableStateFlow(0)
    val healthConnectPermissionRequestVersion: StateFlow<Int> =
        _healthConnectPermissionRequestVersion.asStateFlow()

    private var healthConnectReadJob: Job? = null
    private var healthConnectAdoptionJob: Job? = null

    /**
     * 更新体重
     */
    fun updateBodyWeight(weight: Double) {
        scope.launch {
            settingsDataStore.updateBodyWeight(weight)
        }
    }

    fun readHealthConnectWeight() {
        readHealthConnectWeightInternal(requestPermission = true)
    }

    fun onHealthConnectPermissionResult() {
        readHealthConnectWeightInternal(requestPermission = false)
    }

    fun useHealthConnectWeight() {
        val preview = _healthConnectWeightState.value as? HealthConnectWeightUiState.Preview
            ?: return
        if (healthConnectAdoptionJob?.isActive == true || healthConnectReadJob?.isActive == true) {
            return
        }

        val observation = preview.observation
        if (!isValidBodyWeight(observation.weightKg)) {
            _healthConnectWeightState.value =
                HealthConnectWeightUiState.Error(HealthConnectWeightUiError.INVALID_WEIGHT)
            return
        }

        healthConnectAdoptionJob = scope.launch {
            _healthConnectWeightState.value = HealthConnectWeightUiState.Adopting(observation)
            try {
                if (settingsDataStore.updateBodyWeight(observation.weightKg)) {
                    _healthConnectWeightState.value =
                        HealthConnectWeightUiState.Adopted(observation.weightKg)
                } else {
                    _healthConnectWeightState.value =
                        HealthConnectWeightUiState.Error(HealthConnectWeightUiError.ADOPTION_FAILED)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _healthConnectWeightState.value =
                    HealthConnectWeightUiState.Error(HealthConnectWeightUiError.ADOPTION_FAILED)
            }
        }
    }

    private fun readHealthConnectWeightInternal(requestPermission: Boolean) {
        if (healthConnectReadJob?.isActive == true || healthConnectAdoptionJob?.isActive == true) {
            return
        }

        healthConnectReadJob = scope.launch {
            _healthConnectWeightState.value = HealthConnectWeightUiState.Checking
            try {
                when (val availability = healthConnectWeightProvider.availability()) {
                    is HealthConnectAvailabilityResult.Error -> {
                        _healthConnectWeightState.value = HealthConnectWeightUiState.Error(
                            HealthConnectWeightUiError.AVAILABILITY_CHECK_FAILED
                        )
                        return@launch
                    }

                    is HealthConnectAvailabilityResult.Status -> {
                        if (applyAvailability(availability.availability)) return@launch
                    }
                }

                when (val permission = healthConnectWeightProvider.permissionState()) {
                    HealthConnectPermissionResult.Granted -> Unit
                    HealthConnectPermissionResult.NotGranted -> {
                        _healthConnectWeightState.value =
                            HealthConnectWeightUiState.PermissionNeeded
                        if (requestPermission) {
                            _healthConnectPermissionRequestVersion.value++
                        }
                        return@launch
                    }

                    is HealthConnectPermissionResult.Unavailable -> {
                        applyAvailability(permission.availability)
                        return@launch
                    }

                    is HealthConnectPermissionResult.Error -> {
                        _healthConnectWeightState.value = HealthConnectWeightUiState.Error(
                            HealthConnectWeightUiError.PERMISSION_CHECK_FAILED
                        )
                        return@launch
                    }
                }

                _healthConnectWeightState.value = HealthConnectWeightUiState.Loading
                when (val result = healthConnectWeightProvider.readLatestWeight()) {
                    is HealthConnectWeightResult.Success -> {
                        _healthConnectWeightState.value =
                            HealthConnectWeightUiState.Preview(result.observation)
                    }

                    HealthConnectWeightResult.NoData -> {
                        _healthConnectWeightState.value = HealthConnectWeightUiState.NoData
                    }

                    HealthConnectWeightResult.PermissionNotGranted -> {
                        _healthConnectWeightState.value =
                            HealthConnectWeightUiState.PermissionNeeded
                        if (requestPermission) {
                            _healthConnectPermissionRequestVersion.value++
                        }
                    }

                    is HealthConnectWeightResult.Unavailable -> {
                        applyAvailability(result.availability)
                    }

                    is HealthConnectWeightResult.Error -> {
                        _healthConnectWeightState.value = HealthConnectWeightUiState.Error(
                            HealthConnectWeightUiError.READ_FAILED
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _healthConnectWeightState.value =
                    HealthConnectWeightUiState.Error(HealthConnectWeightUiError.READ_FAILED)
            }
        }
    }

    private fun applyAvailability(availability: HealthConnectAvailability): Boolean {
        when (availability) {
            HealthConnectAvailability.AVAILABLE -> return false
            HealthConnectAvailability.UNAVAILABLE ->
                _healthConnectWeightState.value =
                    HealthConnectWeightUiState.Unavailable(availability)
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED ->
                _healthConnectWeightState.value = HealthConnectWeightUiState.UpdateRequired
        }
        return true
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
            return SettingsViewModel(settingsDataStore, healthConnectWeightProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
