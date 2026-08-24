package io.github.yingqiu0871.evolune.healthconnect

import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.viewmodel.SettingsViewModel
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectWeightSyncCoordinatorTest {
    @Test
    fun `disabled sync does not probe Health Connect`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider()
        val store = FakeSettingsStore()
        val coordinator = coordinator(store, provider)

        coordinator.syncIfEnabled()

        assertEquals(0, provider.availabilityCalls)
        assertEquals(0, provider.permissionCalls)
        assertEquals(0, provider.readCalls)
        assertEquals(HealthConnectWeightSyncStatus.DISABLED, coordinator.state.value.status)
    }

    @Test
    fun `enabling with granted permission persists enabled and adopts immediately`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation(62.5, "2026-08-23T00:00:00Z"))
        }
        val store = FakeSettingsStore()
        val coordinator = coordinator(store, provider)

        assertEquals(
            HealthConnectWeightSyncEnableResult.ENABLED,
            coordinator.enableWeightSync()
        )

        assertTrue(store.userSettings.value.healthConnectWeightSyncEnabled)
        assertEquals(listOf(62.5), store.bodyWeightWrites)
        assertEquals(1, provider.readCalls)
        assertEquals(HealthConnectWeightSyncStatus.CONNECTED, coordinator.state.value.status)
    }

    @Test
    fun `first enable with missing permission does not persist or read`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionResult = HealthConnectPermissionResult.NotGranted
        }
        val store = FakeSettingsStore()
        val coordinator = coordinator(store, provider)

        assertEquals(
            HealthConnectWeightSyncEnableResult.PERMISSION_REQUIRED,
            coordinator.enableWeightSync()
        )

        assertFalse(store.userSettings.value.healthConnectWeightSyncEnabled)
        assertEquals(0, provider.readCalls)
        assertEquals(
            HealthConnectWeightSyncStatus.PERMISSION_REQUIRED,
            coordinator.state.value.status
        )
    }

    @Test
    fun `permission request is one shot and denial leaves sync disabled`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionResult = HealthConnectPermissionResult.NotGranted
        }
        val store = FakeSettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(store, provider, scope)
        val firstRequest = CompletableDeferred<Unit>()
        val collector = launch {
            viewModel.healthConnectPermissionRequests.first()
            firstRequest.complete(Unit)
        }

        viewModel.setHealthConnectWeightSyncEnabled(true)
        viewModel.setHealthConnectWeightSyncEnabled(true)
        firstRequest.await()
        viewModel.onHealthConnectPermissionResult()
        collector.join()

        assertFalse(store.userSettings.value.healthConnectWeightSyncEnabled)
        assertEquals(0, provider.readCalls)
        scope.cancel()
    }

    @Test
    fun `permission grant callback enables sync and performs first read`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionResult = HealthConnectPermissionResult.NotGranted
            weightResult = HealthConnectWeightResult.Success(observation(64.0, "2026-08-23T00:00:00Z"))
        }
        val store = FakeSettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(store, provider, scope)
        val permissionRequest = launch { viewModel.healthConnectPermissionRequests.first() }

        viewModel.setHealthConnectWeightSyncEnabled(true)
        permissionRequest.join()
        provider.permissionResult = HealthConnectPermissionResult.Granted
        viewModel.onHealthConnectPermissionResult()

        assertTrue(store.userSettings.value.healthConnectWeightSyncEnabled)
        assertEquals(listOf(64.0), store.bodyWeightWrites)
        assertEquals(1, provider.readCalls)
        scope.cancel()
    }

    @Test
    fun `revoked permission is silent until the user requests reauthorization`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionResult = HealthConnectPermissionResult.NotGranted
        }
        val store = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(store, provider, scope)
        var permissionEvents = 0
        val firstPermissionEvent = CompletableDeferred<Unit>()
        val collector = launch {
            viewModel.healthConnectPermissionRequests.collect {
                permissionEvents++
                firstPermissionEvent.complete(Unit)
            }
        }

        repeat(10) { viewModel.onForeground() }
        yield()

        assertEquals(0, permissionEvents)
        assertEquals(0, provider.readCalls)
        assertTrue(store.bodyWeightWrites.isEmpty())
        assertEquals(
            HealthConnectWeightSyncStatus.PERMISSION_REQUIRED,
            viewModel.healthConnectWeightSyncState.value.status
        )

        viewModel.requestHealthConnectReauthorization()
        firstPermissionEvent.await()
        assertEquals(1, permissionEvents)
        collector.cancel()
        scope.cancel()
    }

    @Test
    fun `foreground sync adopts a newer observation without a preview step`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation(61.0, "2026-08-23T00:00:00Z"))
        }
        val store = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        val coordinator = coordinator(store, provider)

        coordinator.syncIfEnabled()

        assertEquals(listOf(61.0), store.bodyWeightWrites)
        assertEquals(Instant.parse("2026-08-23T00:00:00Z"), store.userSettings.value.lastHealthConnectWeightAdoptedAt)
    }

    @Test
    fun `watermark prevents stale observation from overwriting manual local weight`() = runBlocking {
        val first = observation(60.0, "2026-08-23T00:00:00Z")
        val stale = first
        val newer = observation(58.0, "2026-08-24T00:00:00Z")
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResults += HealthConnectWeightResult.Success(first)
            weightResults += HealthConnectWeightResult.Success(stale)
            weightResults += HealthConnectWeightResult.Success(newer)
        }
        val store = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        val coordinator = coordinator(store, provider)

        coordinator.syncIfEnabled()
        store.userSettings.value = store.userSettings.value.copy(bodyWeight = 55.0)
        coordinator.syncIfEnabled()
        assertEquals(55.0, store.userSettings.value.bodyWeight, 0.0)
        coordinator.syncIfEnabled()

        assertEquals(listOf(60.0, 58.0), store.bodyWeightWrites)
        assertEquals(58.0, store.userSettings.value.bodyWeight, 0.0)
        assertEquals(newer.timestamp, store.userSettings.value.lastHealthConnectWeightAdoptedAt)
    }

    @Test
    fun `newer same-value observation advances metadata without body write`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResults += HealthConnectWeightResult.Success(observation(55.0, "2026-08-23T00:00:00Z"))
            weightResults += HealthConnectWeightResult.Success(observation(55.0, "2026-08-24T00:00:00Z"))
        }
        val store = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        val coordinator = coordinator(store, provider)

        coordinator.syncIfEnabled()
        coordinator.syncIfEnabled()

        assertTrue(store.bodyWeightWrites.isEmpty())
        assertEquals(2, store.metadataWrites.size)
        assertEquals(Instant.parse("2026-08-24T00:00:00Z"), store.userSettings.value.lastHealthConnectWeightAdoptedAt)
    }

    @Test
    fun `no data and invalid observations do not change local authority or watermark`() = runBlocking {
        val invalidResults = listOf(
            HealthConnectWeightResult.NoData,
            HealthConnectWeightResult.Success(observation(0.0, "2026-08-23T00:00:00Z")),
            HealthConnectWeightResult.Success(observation(301.0, "2026-08-24T00:00:00Z")),
            HealthConnectWeightResult.Success(observation(Double.NaN, "2026-08-25T00:00:00Z"))
        )
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResults += invalidResults
        }
        val initial = UserSettings(bodyWeight = 57.0, healthConnectWeightSyncEnabled = true)
        val store = FakeSettingsStore(initial)
        val coordinator = coordinator(store, provider)

        repeat(invalidResults.size) { coordinator.syncIfEnabled() }

        assertEquals(initial, store.userSettings.value)
        assertTrue(store.bodyWeightWrites.isEmpty())
        assertTrue(store.metadataWrites.isEmpty())
    }

    @Test
    fun `foreground sync calls are serialized by one mutex`() = runBlocking {
        val readGate = CompletableDeferred<Unit>()
        val firstReadStarted = CompletableDeferred<Unit>()
        val provider = FakeHealthConnectWeightProvider().apply {
            this.readGate = readGate
            this.firstReadStarted = firstReadStarted
            weightResults += HealthConnectWeightResult.Success(observation(61.0, "2026-08-23T00:00:00Z"))
            weightResults += HealthConnectWeightResult.Success(observation(62.0, "2026-08-24T00:00:00Z"))
        }
        val store = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        val coordinator = coordinator(store, provider)
        val first = launch { coordinator.syncIfEnabled() }
        firstReadStarted.await()
        val second = launch { coordinator.syncIfEnabled() }
        yield()

        assertEquals(1, provider.readCalls)
        assertEquals(1, provider.maxConcurrentReads)
        assertTrue(second.isActive)

        readGate.complete(Unit)
        first.join()
        second.join()

        assertEquals(2, provider.readCalls)
        assertEquals(1, provider.maxConcurrentReads)
        assertEquals(listOf(61.0, 62.0), store.bodyWeightWrites)
    }

    @Test
    fun `cancellation propagates from availability permission and read`() = runBlocking {
        val availabilityProvider = FakeHealthConnectWeightProvider().apply {
            availabilityFailure = CancellationException("availability cancelled")
        }
        assertCancellation { coordinator(FakeSettingsStore(), availabilityProvider).enableWeightSync() }

        val permissionProvider = FakeHealthConnectWeightProvider().apply {
            permissionFailure = CancellationException("permission cancelled")
        }
        assertCancellation { coordinator(FakeSettingsStore(), permissionProvider).enableWeightSync() }

        val readProvider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation())
            readFailure = CancellationException("read cancelled")
        }
        val readStore = FakeSettingsStore(UserSettings(healthConnectWeightSyncEnabled = true))
        assertCancellation { coordinator(readStore, readProvider).syncIfEnabled() }
    }

    @Test
    fun `recreated coordinator reads persisted enabled preference and watermark`() = runBlocking {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(
                observation(63.0, "2026-08-23T00:00:00Z")
            )
        }
        val store = FakeSettingsStore()
        val firstCoordinator = coordinator(store, provider)
        firstCoordinator.enableWeightSync()

        val bodyWritesAfterFirstSync = store.bodyWeightWrites.size
        val recreatedCoordinator = coordinator(store, provider)
        recreatedCoordinator.syncIfEnabled()

        assertTrue(store.userSettings.value.healthConnectWeightSyncEnabled)
        assertEquals(
            Instant.parse("2026-08-23T00:00:00Z"),
            store.userSettings.value.lastHealthConnectWeightAdoptedAt
        )
        assertEquals(bodyWritesAfterFirstSync, store.bodyWeightWrites.size)
    }

    private fun coordinator(
        store: FakeSettingsStore,
        provider: FakeHealthConnectWeightProvider
    ): HealthConnectWeightSyncCoordinator = HealthConnectWeightSyncCoordinator(
        settingsStore = store,
        provider = provider,
        clock = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    )

    private fun observation(weightKg: Double = 62.5, timestamp: String = "2026-08-23T00:00:00Z") =
        HealthConnectWeightObservation(
            weightKg = weightKg,
            timestamp = Instant.parse(timestamp),
            sourcePackageName = "com.example.health"
        )

    private suspend fun assertCancellation(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("expected CancellationException")
        } catch (error: CancellationException) {
            assertTrue(error.message?.contains("cancelled") == true)
        }
    }
}

private class FakeSettingsStore(
    initial: UserSettings = UserSettings()
) : SettingsStore {
    override val userSettings = MutableStateFlow(initial)
    val bodyWeightWrites = mutableListOf<Double>()
    val metadataWrites = mutableListOf<Pair<Double, Instant>>()
    val enabledWrites = mutableListOf<Boolean>()

    override suspend fun updateBodyWeight(weight: Double): Boolean {
        userSettings.value = userSettings.value.copy(bodyWeight = weight)
        return true
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        userSettings.value = userSettings.value.copy(themeMode = mode)
    }

    override suspend fun updateColorTheme(theme: ColorTheme) {
        userSettings.value = userSettings.value.copy(colorTheme = theme)
    }

    override suspend fun updateAutoCheckUpdates(enabled: Boolean) {
        userSettings.value = userSettings.value.copy(autoCheckUpdates = enabled)
    }

    override suspend fun updateTimeFormat(format: TimeFormat) {
        userSettings.value = userSettings.value.copy(timeFormat = format)
    }

    override suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean) {
        enabledWrites += enabled
        userSettings.value = userSettings.value.copy(healthConnectWeightSyncEnabled = enabled)
    }

    override suspend fun updateBodyWeightFromHealthConnect(
        weight: Double,
        adoptedAt: Instant
    ): Boolean {
        bodyWeightWrites += weight
        metadataWrites += weight to adoptedAt
        userSettings.value = userSettings.value.copy(
            bodyWeight = weight,
            lastHealthConnectWeightKg = weight,
            lastHealthConnectWeightAdoptedAt = adoptedAt
        )
        return true
    }

    override suspend fun updateHealthConnectWeightMetadata(
        weight: Double,
        adoptedAt: Instant
    ): Boolean {
        metadataWrites += weight to adoptedAt
        userSettings.value = userSettings.value.copy(
            lastHealthConnectWeightKg = weight,
            lastHealthConnectWeightAdoptedAt = adoptedAt
        )
        return true
    }
}

private class FakeHealthConnectWeightProvider : HealthConnectWeightProvider {
    override val requiredPermissions = setOf(HealthConnectPermission.READ_WEIGHT)
    var availabilityResult: HealthConnectAvailabilityResult =
        HealthConnectAvailabilityResult.Status(HealthConnectAvailability.AVAILABLE)
    var permissionResult: HealthConnectPermissionResult = HealthConnectPermissionResult.Granted
    var weightResult: HealthConnectWeightResult = HealthConnectWeightResult.NoData
    val weightResults = ArrayDeque<HealthConnectWeightResult>()
    var availabilityFailure: Throwable? = null
    var permissionFailure: Throwable? = null
    var readFailure: Throwable? = null
    var readGate: CompletableDeferred<Unit>? = null
    var firstReadStarted: CompletableDeferred<Unit>? = null
    var availabilityCalls = 0
    var permissionCalls = 0
    var readCalls = 0
    var activeReads = 0
    var maxConcurrentReads = 0

    override suspend fun availability(): HealthConnectAvailabilityResult {
        availabilityCalls++
        availabilityFailure?.let { throw it }
        return availabilityResult
    }

    override suspend fun permissionState(): HealthConnectPermissionResult {
        permissionCalls++
        permissionFailure?.let { throw it }
        return permissionResult
    }

    override suspend fun readLatestWeight(now: Instant, window: Duration): HealthConnectWeightResult {
        readCalls++
        activeReads++
        maxConcurrentReads = maxOf(maxConcurrentReads, activeReads)
        firstReadStarted?.complete(Unit)
        readGate?.await()
        readFailure?.let { error ->
            activeReads--
            throw error
        }
        activeReads--
        return if (weightResults.isEmpty()) weightResult else weightResults.removeFirst()
    }
}
