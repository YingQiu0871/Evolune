package io.github.yingqiu0871.evolune.healthconnect

import androidx.health.connect.client.HealthConnectClient
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class HealthConnectWeightProviderTest {
    private val now = Instant.parse("2026-08-22T12:00:00Z")

    @Test
    fun `sdk statuses map to stable Evolune availability values`() {
        assertEquals(
            HealthConnectAvailability.AVAILABLE,
            mapHealthConnectSdkStatus(HealthConnectClient.SDK_AVAILABLE)
        )
        assertEquals(
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED,
            mapHealthConnectSdkStatus(
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
            )
        )
        assertEquals(
            HealthConnectAvailability.UNAVAILABLE,
            mapHealthConnectSdkStatus(HealthConnectClient.SDK_UNAVAILABLE)
        )
        assertEquals(
            HealthConnectAvailability.UNAVAILABLE,
            mapHealthConnectSdkStatus(Int.MIN_VALUE)
        )
    }

    @Test
    fun `missing provider on Android 12 through 13 is unavailable`() {
        assertEquals(
            HealthConnectAvailability.UNAVAILABLE,
            resolveHealthConnectAvailability(
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                apiLevel = 33,
                providerInstalled = false
            )
        )
        assertEquals(
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED,
            resolveHealthConnectAvailability(
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                apiLevel = 33,
                providerInstalled = true
            )
        )
    }

    @Test
    fun `Android 14 and newer keeps SDK status authoritative`() {
        assertEquals(
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED,
            resolveHealthConnectAvailability(
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
                apiLevel = 34,
                providerInstalled = false
            )
        )
    }

    @Test
    fun `required permissions contain only read weight`() {
        val provider = provider()

        assertEquals(setOf(HealthConnectPermission.READ_WEIGHT), provider.requiredPermissions)
    }

    @Test
    fun `available status is exposed`() = runBlocking {
        val provider = provider(status = HealthConnectAvailability.AVAILABLE)

        assertEquals(
            HealthConnectAvailabilityResult.Status(HealthConnectAvailability.AVAILABLE),
            provider.availability()
        )
    }

    @Test
    fun `unavailable status is preserved`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            status = HealthConnectAvailability.UNAVAILABLE
        )

        assertEquals(
            HealthConnectPermissionResult.Unavailable(HealthConnectAvailability.UNAVAILABLE),
            AndroidHealthConnectWeightProvider(gateway).permissionState()
        )
        assertEquals(0, gateway.permissionCalls)
        assertEquals(0, gateway.readCalls)
    }

    @Test
    fun `provider update required is distinct from unavailable`() = runBlocking {
        val provider = provider(
            status = HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
        )

        assertEquals(
            HealthConnectWeightResult.Unavailable(
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
            ),
            provider.readLatestWeight(now)
        )
    }

    @Test
    fun `permission unavailable is forwarded as weight unavailable`() = runBlocking {
        val availability = HealthConnectAvailability.UNAVAILABLE

        assertEquals(
            HealthConnectWeightResult.Unavailable(availability),
            provider(status = availability).readLatestWeight(now)
        )
    }

    @Test
    fun `permission state reports granted and not granted`() = runBlocking {
        val granted = provider(granted = true)
        val denied = provider(granted = false)

        assertEquals(HealthConnectPermissionResult.Granted, granted.permissionState())
        assertEquals(HealthConnectPermissionResult.NotGranted, denied.permissionState())
    }

    @Test
    fun `latest valid weight is selected from the recent thirty day window`() = runBlocking {
        val older = now.minus(Duration.ofDays(10))
        val latest = now.minus(Duration.ofHours(2))
        val gateway = FakeHealthConnectClientGateway(
            permissions = setOf(READ_WEIGHT_PERMISSION),
            records = listOf(
                HealthConnectWeightRecordData(61.0, older, "com.example.scale"),
                HealthConnectWeightRecordData(63.4, latest, "com.example.scale")
            )
        )

        val result = AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now)

        assertEquals(
            HealthConnectWeightResult.Success(
                HealthConnectWeightObservation(63.4, latest, "com.example.scale")
            ),
            result
        )
        assertEquals(now.minus(DEFAULT_RECENT_WEIGHT_WINDOW), gateway.lastStart)
        assertEquals(now, gateway.lastEnd)
    }

    @Test
    fun `no records returns explicit no data without writes`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            permissions = setOf(READ_WEIGHT_PERMISSION)
        )

        assertEquals(
            HealthConnectWeightResult.NoData,
            AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now)
        )
        assertEquals(0, gateway.writeCalls)
    }

    @Test
    fun `invalid records are rejected and do not become observations`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            permissions = setOf(READ_WEIGHT_PERMISSION),
            records = listOf(
                HealthConnectWeightRecordData(0.0, now.minus(Duration.ofHours(1)), null),
                HealthConnectWeightRecordData(Double.NaN, now.minus(Duration.ofHours(2)), null),
                HealthConnectWeightRecordData(1000.0, now.minus(Duration.ofHours(3)), null)
            )
        )

        assertEquals(
            HealthConnectWeightResult.NoData,
            AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now)
        )
        assertEquals(0, gateway.writeCalls)
    }

    @Test
    fun `permission denial prevents a read`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            permissions = emptySet(),
            records = listOf(HealthConnectWeightRecordData(63.0, now, null))
        )

        assertEquals(
            HealthConnectWeightResult.PermissionNotGranted,
            AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now)
        )
        assertEquals(0, gateway.readCalls)
    }

    @Test
    fun `status and platform exceptions map to stable errors`() = runBlocking {
        val statusFailure = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(statusFailure = IllegalStateException())
        )
        val permissionFailure = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(permissionFailure = SecurityException())
        )
        val readFailure = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(
                permissions = setOf(READ_WEIGHT_PERMISSION),
                readFailure = IllegalStateException()
            )
        )

        assertEquals(
            HealthConnectAvailabilityResult.Error(HealthConnectError.STATUS_CHECK_FAILED),
            statusFailure.availability()
        )
        assertEquals(
            HealthConnectPermissionResult.Error(HealthConnectError.PERMISSION_CHECK_FAILED),
            permissionFailure.permissionState()
        )
        assertEquals(
            HealthConnectWeightResult.Error(HealthConnectError.WEIGHT_READ_FAILED),
            readFailure.readLatestWeight(now)
        )
    }

    @Test
    fun `permission error is forwarded as weight read error`() = runBlocking {
        val provider = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(
                permissions = setOf(READ_WEIGHT_PERMISSION),
                permissionFailure = SecurityException()
            )
        )

        assertEquals(
            HealthConnectWeightResult.Error(HealthConnectError.PERMISSION_CHECK_FAILED),
            provider.readLatestWeight(now)
        )
    }

    @Test
    fun `availability cancellation is rethrown`() = runBlocking {
        val cancellation = CancellationException("availability cancelled")
        val provider = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(statusFailure = cancellation)
        )

        try {
            provider.availability()
            fail("availability cancellation must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `permission check cancellation is rethrown`() = runBlocking {
        val cancellation = CancellationException("permission check cancelled")
        val provider = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(permissionFailure = cancellation)
        )

        try {
            provider.permissionState()
            fail("permission check cancellation must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `weight read cancellation is rethrown`() = runBlocking {
        val cancellation = CancellationException("weight read cancelled")
        val provider = AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(
                permissions = setOf(READ_WEIGHT_PERMISSION),
                readFailure = cancellation
            )
        )

        try {
            provider.readLatestWeight(now)
            fail("weight read cancellation must be rethrown")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `invalid time window is rejected without platform access`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            permissions = setOf(READ_WEIGHT_PERMISSION)
        )

        assertEquals(
            HealthConnectWeightResult.Error(HealthConnectError.INVALID_TIME_WINDOW),
            AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now, Duration.ZERO)
        )
        assertEquals(0, gateway.availabilityCalls)
        assertEquals(0, gateway.writeCalls)
    }

    @Test
    fun `records outside the requested window are ignored`() = runBlocking {
        val gateway = FakeHealthConnectClientGateway(
            permissions = setOf(READ_WEIGHT_PERMISSION),
            records = listOf(
                HealthConnectWeightRecordData(72.0, now.plusSeconds(1), "future"),
                HealthConnectWeightRecordData(
                    71.0,
                    now.minus(Duration.ofDays(31)),
                    "too-old"
                )
            )
        )

        assertEquals(
            HealthConnectWeightResult.NoData,
            AndroidHealthConnectWeightProvider(gateway).readLatestWeight(now)
        )
    }

    private fun provider(
        status: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        granted: Boolean = true
    ): AndroidHealthConnectWeightProvider =
        AndroidHealthConnectWeightProvider(
            FakeHealthConnectClientGateway(
                status = status,
                permissions = if (granted) setOf(READ_WEIGHT_PERMISSION) else emptySet()
            )
        )

    private class FakeHealthConnectClientGateway(
        private val status: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        private val permissions: Set<String> = emptySet(),
        private val records: List<HealthConnectWeightRecordData> = emptyList(),
        private val statusFailure: Exception? = null,
        private val permissionFailure: Exception? = null,
        private val readFailure: Exception? = null
    ) : HealthConnectClientGateway {
        var availabilityCalls = 0
        var permissionCalls = 0
        var readCalls = 0
        var writeCalls = 0
        var lastStart: Instant? = null
        var lastEnd: Instant? = null

        override fun availability(): HealthConnectAvailability {
            availabilityCalls += 1
            statusFailure?.let { throw it }
            return status
        }

        override suspend fun grantedPermissions(): Set<String> {
            permissionCalls += 1
            permissionFailure?.let { throw it }
            return permissions
        }

        override suspend fun readWeightRecords(
            startInclusive: Instant,
            endExclusive: Instant
        ): List<HealthConnectWeightRecordData> {
            readCalls += 1
            lastStart = startInclusive
            lastEnd = endExclusive
            readFailure?.let { throw it }
            return records
        }
    }

    private companion object {
        const val READ_WEIGHT_PERMISSION = "android.permission.health.READ_WEIGHT"
    }
}
