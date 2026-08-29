package io.github.yingqiu0871.evolune.healthconnect

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

internal data class HealthConnectWeightRecordData(
    val weightKg: Double,
    val timestamp: Instant,
    val sourcePackageName: String?
)

internal interface HealthConnectClientGateway {
    fun availability(): HealthConnectAvailability

    suspend fun grantedPermissions(): Set<String>

    suspend fun readWeightRecords(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<HealthConnectWeightRecordData>
}

internal class AndroidHealthConnectClientGateway(
    private val context: Context
) : HealthConnectClientGateway {
    override fun availability(): HealthConnectAvailability {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        val providerInstalled = if (
            Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU &&
            sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        ) {
            isHealthConnectProviderInstalled()
        } else {
            true
        }
        return resolveHealthConnectAvailability(
            sdkStatus = sdkStatus,
            apiLevel = Build.VERSION.SDK_INT,
            providerInstalled = providerInstalled
        )
    }

    @Suppress("DEPRECATION")
    private fun isHealthConnectProviderInstalled(): Boolean = try {
        context.packageManager.getApplicationInfo(HEALTH_CONNECT_PROVIDER_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override suspend fun grantedPermissions(): Set<String> =
        HealthConnectClient.getOrCreate(context)
            .permissionController
            .getGrantedPermissions()

    override suspend fun readWeightRecords(
        startInclusive: Instant,
        endExclusive: Instant
    ): List<HealthConnectWeightRecordData> {
        val client = HealthConnectClient.getOrCreate(context)
        val records = mutableListOf<HealthConnectWeightRecordData>()
        var pageToken: String? = null

        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInclusive, endExclusive),
                    pageToken = pageToken
                )
            )
            records += response.records.map { record ->
                HealthConnectWeightRecordData(
                    weightKg = record.weight.inKilograms,
                    timestamp = record.time,
                    sourcePackageName = record.metadata.dataOrigin.packageName
                        .takeIf { it.isNotBlank() }
                )
            }
            pageToken = response.pageToken
        } while (pageToken != null)

        return records
    }
}

internal class AndroidHealthConnectWeightProvider internal constructor(
    private val gateway: HealthConnectClientGateway
) : HealthConnectWeightProvider {
    constructor(context: Context) : this(AndroidHealthConnectClientGateway(context))

    override val requiredPermissions: Set<HealthConnectPermission> =
        setOf(HealthConnectPermission.READ_WEIGHT)

    private val requiredPlatformPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(WeightRecord::class))

    override suspend fun availability(): HealthConnectAvailabilityResult =
        try {
            HealthConnectAvailabilityResult.Status(gateway.availability())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HealthConnectAvailabilityResult.Error(HealthConnectError.STATUS_CHECK_FAILED)
        }

    override suspend fun permissionState(): HealthConnectPermissionResult =
        when (val availability = availability()) {
            is HealthConnectAvailabilityResult.Error ->
                HealthConnectPermissionResult.Error(availability.error)

            is HealthConnectAvailabilityResult.Status -> {
                if (availability.availability != HealthConnectAvailability.AVAILABLE) {
                    HealthConnectPermissionResult.Unavailable(availability.availability)
                } else {
                    readPermissionState()
                }
            }
        }

    override suspend fun readLatestWeight(
        now: Instant,
        window: Duration
    ): HealthConnectWeightResult {
        if (window.isZero || window.isNegative) {
            return HealthConnectWeightResult.Error(HealthConnectError.INVALID_TIME_WINDOW)
        }

        val availability = when (val result = availability()) {
            is HealthConnectAvailabilityResult.Error ->
                return HealthConnectWeightResult.Error(result.error)

            is HealthConnectAvailabilityResult.Status -> result.availability
        }
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return HealthConnectWeightResult.Unavailable(availability)
        }

        when (val permissionState = readPermissionState()) {
            HealthConnectPermissionResult.Granted -> Unit
            HealthConnectPermissionResult.NotGranted ->
                return HealthConnectWeightResult.PermissionNotGranted
            is HealthConnectPermissionResult.Unavailable ->
                return HealthConnectWeightResult.Unavailable(permissionState.availability)
            is HealthConnectPermissionResult.Error ->
                return HealthConnectWeightResult.Error(permissionState.error)
        }

        val startInclusive = now.minus(window)
        val records = try {
            gateway.readWeightRecords(startInclusive, now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return HealthConnectWeightResult.Error(HealthConnectError.WEIGHT_READ_FAILED)
        }

        val latest = records
            .asSequence()
            .filter { record ->
                !record.timestamp.isBefore(startInclusive) && !record.timestamp.isAfter(now)
            }
            .mapNotNull { it.toObservationOrNull() }
            .maxByOrNull { it.timestamp }

        return latest?.let(HealthConnectWeightResult::Success)
            ?: HealthConnectWeightResult.NoData
    }

    private suspend fun readPermissionState(): HealthConnectPermissionResult =
        try {
            if (gateway.grantedPermissions().containsAll(requiredPlatformPermissions)) {
                HealthConnectPermissionResult.Granted
            } else {
                HealthConnectPermissionResult.NotGranted
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            HealthConnectPermissionResult.Error(HealthConnectError.PERMISSION_CHECK_FAILED)
        }
}

internal fun mapHealthConnectSdkStatus(status: Int): HealthConnectAvailability =
    when (status) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

internal fun resolveHealthConnectAvailability(
    sdkStatus: Int,
    apiLevel: Int,
    providerInstalled: Boolean
): HealthConnectAvailability {
    val mapped = mapHealthConnectSdkStatus(sdkStatus)
    return if (
        apiLevel in Build.VERSION_CODES.S..Build.VERSION_CODES.TIRAMISU &&
        mapped == HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED &&
        !providerInstalled
    ) {
        HealthConnectAvailability.UNAVAILABLE
    } else {
        mapped
    }
}

private const val HEALTH_CONNECT_PROVIDER_PACKAGE = "com.google.android.apps.healthdata"

private fun HealthConnectWeightRecordData.toObservationOrNull(): HealthConnectWeightObservation? {
    if (!weightKg.isFinite() || weightKg <= 0.0 || weightKg > 300.0) {
        return null
    }
    return HealthConnectWeightObservation(
        weightKg = weightKg,
        timestamp = timestamp,
        sourcePackageName = sourcePackageName
    )
}
