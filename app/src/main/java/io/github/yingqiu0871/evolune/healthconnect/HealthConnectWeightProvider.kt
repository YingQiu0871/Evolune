package io.github.yingqiu0871.evolune.healthconnect

import java.time.Duration
import java.time.Instant

enum class HealthConnectAvailability {
    AVAILABLE,
    UNAVAILABLE,
    PROVIDER_UPDATE_REQUIRED
}

enum class HealthConnectPermission {
    READ_WEIGHT
}

enum class HealthConnectError {
    STATUS_CHECK_FAILED,
    PERMISSION_CHECK_FAILED,
    WEIGHT_READ_FAILED,
    INVALID_TIME_WINDOW
}

sealed interface HealthConnectAvailabilityResult {
    data class Status(val availability: HealthConnectAvailability) : HealthConnectAvailabilityResult

    data class Error(val error: HealthConnectError) : HealthConnectAvailabilityResult
}

sealed interface HealthConnectPermissionResult {
    data object Granted : HealthConnectPermissionResult

    data object NotGranted : HealthConnectPermissionResult

    data class Unavailable(val availability: HealthConnectAvailability) : HealthConnectPermissionResult

    data class Error(val error: HealthConnectError) : HealthConnectPermissionResult
}

data class HealthConnectWeightObservation(
    val weightKg: Double,
    val timestamp: Instant,
    val sourcePackageName: String? = null
)

sealed interface HealthConnectWeightResult {
    data class Success(val observation: HealthConnectWeightObservation) : HealthConnectWeightResult

    data object NoData : HealthConnectWeightResult

    data object PermissionNotGranted : HealthConnectWeightResult

    data class Unavailable(val availability: HealthConnectAvailability) : HealthConnectWeightResult

    data class Error(val error: HealthConnectError) : HealthConnectWeightResult
}

interface HealthConnectWeightProvider {
    val requiredPermissions: Set<HealthConnectPermission>

    suspend fun availability(): HealthConnectAvailabilityResult

    suspend fun permissionState(): HealthConnectPermissionResult

    suspend fun readLatestWeight(
        now: Instant = Instant.now(),
        window: Duration = DEFAULT_RECENT_WEIGHT_WINDOW
    ): HealthConnectWeightResult
}

val DEFAULT_RECENT_WEIGHT_WINDOW: Duration = Duration.ofDays(30)
