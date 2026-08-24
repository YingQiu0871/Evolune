package io.github.yingqiu0871.evolune.healthconnect

import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.data.isValidBodyWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.cancel
import java.time.Clock

enum class HealthConnectWeightSyncStatus {
    DISABLED,
    CHECKING,
    SYNCING,
    CONNECTED,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    UPDATE_REQUIRED,
    NO_DATA,
    ERROR
}

data class HealthConnectWeightSyncState(
    val status: HealthConnectWeightSyncStatus = HealthConnectWeightSyncStatus.DISABLED,
    val lastWeightKg: Double? = null,
    val lastAdoptedAt: java.time.Instant? = null
)

enum class HealthConnectWeightSyncEnableResult {
    ENABLED,
    PERMISSION_REQUIRED,
    BLOCKED
}

/**
 * Owns foreground-only Health Connect weight synchronization.
 *
 * Health Connect is an observation source. SettingsDataStore.bodyWeight remains
 * Evolune's authoritative local value and is the only value consumed by PK.
 */
class HealthConnectWeightSyncCoordinator(
    private val settingsStore: SettingsStore,
    private val provider: HealthConnectWeightProvider,
    private val clock: Clock = Clock.systemUTC(),
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )
) {
    private val syncMutex = Mutex()
    private val _state = MutableStateFlow(HealthConnectWeightSyncState())
    val state: StateFlow<HealthConnectWeightSyncState> = _state.asStateFlow()

    fun onForeground() {
        scope.launch {
            syncIfEnabled()
        }
    }

    fun close() {
        scope.cancel()
    }

    suspend fun enableWeightSync(): HealthConnectWeightSyncEnableResult =
        syncMutex.withLock { enableWeightSyncLocked() }

    suspend fun completeEnableAfterPermission(): HealthConnectWeightSyncEnableResult =
        syncMutex.withLock { enableWeightSyncLocked() }

    suspend fun disableWeightSync() {
        syncMutex.withLock {
            settingsStore.updateHealthConnectWeightSyncEnabled(false)
            publish(HealthConnectWeightSyncStatus.DISABLED, settingsStore.userSettings.first())
        }
    }

    /** Silent foreground path. It never emits a permission request. */
    suspend fun syncIfEnabled() {
        syncMutex.withLock {
            val settings = settingsStore.userSettings.first()
            if (!settings.healthConnectWeightSyncEnabled) {
                publish(HealthConnectWeightSyncStatus.DISABLED, settings)
                return@withLock
            }
            syncEnabledLocked(settings)
        }
    }

    private suspend fun enableWeightSyncLocked(): HealthConnectWeightSyncEnableResult {
        val current = settingsStore.userSettings.first()
        publish(HealthConnectWeightSyncStatus.CHECKING, current)

        return try {
            when (val availability = provider.availability()) {
                is HealthConnectAvailabilityResult.Error -> {
                    publish(HealthConnectWeightSyncStatus.ERROR, current)
                    HealthConnectWeightSyncEnableResult.BLOCKED
                }

                is HealthConnectAvailabilityResult.Status -> {
                    when (availability.availability) {
                        HealthConnectAvailability.AVAILABLE -> when (
                            val permission = provider.permissionState()
                        ) {
                            HealthConnectPermissionResult.Granted -> {
                                settingsStore.updateHealthConnectWeightSyncEnabled(true)
                                syncEnabledLocked(settingsStore.userSettings.first())
                                HealthConnectWeightSyncEnableResult.ENABLED
                            }

                            HealthConnectPermissionResult.NotGranted -> {
                                publish(HealthConnectWeightSyncStatus.PERMISSION_REQUIRED, current)
                                HealthConnectWeightSyncEnableResult.PERMISSION_REQUIRED
                            }

                            is HealthConnectPermissionResult.Unavailable -> {
                                publish(statusFor(permission.availability), current)
                                HealthConnectWeightSyncEnableResult.BLOCKED
                            }

                            is HealthConnectPermissionResult.Error -> {
                                publish(HealthConnectWeightSyncStatus.ERROR, current)
                                HealthConnectWeightSyncEnableResult.BLOCKED
                            }
                        }

                        HealthConnectAvailability.UNAVAILABLE,
                        HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> {
                            publish(statusFor(availability.availability), current)
                            HealthConnectWeightSyncEnableResult.BLOCKED
                        }
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publish(HealthConnectWeightSyncStatus.ERROR, current)
            HealthConnectWeightSyncEnableResult.BLOCKED
        }
    }

    private suspend fun syncEnabledLocked(settings: UserSettings) {
        publish(HealthConnectWeightSyncStatus.CHECKING, settings)
        try {
            when (val availability = provider.availability()) {
                is HealthConnectAvailabilityResult.Error -> {
                    publish(HealthConnectWeightSyncStatus.ERROR, settings)
                    return
                }

                is HealthConnectAvailabilityResult.Status -> {
                    if (availability.availability != HealthConnectAvailability.AVAILABLE) {
                        publish(statusFor(availability.availability), settings)
                        return
                    }
                }
            }

            when (val permission = provider.permissionState()) {
                HealthConnectPermissionResult.Granted -> Unit
                HealthConnectPermissionResult.NotGranted -> {
                    publish(HealthConnectWeightSyncStatus.PERMISSION_REQUIRED, settings)
                    return
                }

                is HealthConnectPermissionResult.Unavailable -> {
                    publish(statusFor(permission.availability), settings)
                    return
                }

                is HealthConnectPermissionResult.Error -> {
                    publish(HealthConnectWeightSyncStatus.ERROR, settings)
                    return
                }
            }

            publish(HealthConnectWeightSyncStatus.SYNCING, settings)
            when (val result = provider.readLatestWeight(now = clock.instant())) {
                is HealthConnectWeightResult.Success -> {
                    if (!isValidBodyWeight(result.observation.weightKg)) {
                        publish(HealthConnectWeightSyncStatus.ERROR, settings)
                        return
                    }

                    // Re-read after the provider suspension so a manual local
                    // edit made while Health Connect was being read is the
                    // comparison baseline for freshness and same-value writes.
                    val latest = adoptIfFresh(
                        settingsStore.userSettings.first(),
                        result.observation
                    )
                    if (latest != null) {
                        publish(HealthConnectWeightSyncStatus.CONNECTED, latest)
                    }
                }

                HealthConnectWeightResult.NoData -> {
                    publish(HealthConnectWeightSyncStatus.NO_DATA, settings)
                }

                HealthConnectWeightResult.PermissionNotGranted -> {
                    publish(HealthConnectWeightSyncStatus.PERMISSION_REQUIRED, settings)
                }

                is HealthConnectWeightResult.Unavailable -> {
                    publish(statusFor(result.availability), settings)
                }

                is HealthConnectWeightResult.Error -> {
                    publish(HealthConnectWeightSyncStatus.ERROR, settings)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publish(HealthConnectWeightSyncStatus.ERROR, settings)
        }
    }

    private suspend fun adoptIfFresh(
        settings: UserSettings,
        observation: HealthConnectWeightObservation
    ): UserSettings? {
        val lastAdoptedAt = settings.lastHealthConnectWeightAdoptedAt
        if (lastAdoptedAt != null && !observation.timestamp.isAfter(lastAdoptedAt)) {
            return settings
        }

        val updated = if (settings.bodyWeight == observation.weightKg) {
            settingsStore.updateHealthConnectWeightMetadata(
                weight = observation.weightKg,
                adoptedAt = observation.timestamp
            )
        } else {
            settingsStore.updateBodyWeightFromHealthConnect(
                weight = observation.weightKg,
                adoptedAt = observation.timestamp
            )
        }
        return if (updated) settingsStore.userSettings.first() else null
    }

    private fun publish(status: HealthConnectWeightSyncStatus, settings: UserSettings) {
        _state.value = HealthConnectWeightSyncState(
            status = status,
            lastWeightKg = settings.lastHealthConnectWeightKg,
            lastAdoptedAt = settings.lastHealthConnectWeightAdoptedAt
        )
    }

    private fun statusFor(availability: HealthConnectAvailability): HealthConnectWeightSyncStatus =
        when (availability) {
            HealthConnectAvailability.AVAILABLE -> HealthConnectWeightSyncStatus.CONNECTED
            HealthConnectAvailability.UNAVAILABLE -> HealthConnectWeightSyncStatus.UNAVAILABLE
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED ->
                HealthConnectWeightSyncStatus.UPDATE_REQUIRED
        }
}
