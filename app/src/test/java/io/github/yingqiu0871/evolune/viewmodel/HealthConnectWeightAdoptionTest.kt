package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailability
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailabilityResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectPermission
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectPermissionResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightObservation
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightProvider
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HealthConnectWeightAdoptionTest {
    @Test
    fun `startup stays idle and read produces preview without local write`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation())
        }
        val fixture = fixture(provider)
        try {
            assertEquals(HealthConnectWeightUiState.Idle, fixture.viewModel.healthConnectWeightState.value)
            assertEquals(0, provider.readCalls)

            fixture.viewModel.readHealthConnectWeight()

            assertEquals(
                HealthConnectWeightUiState.Preview(observation()),
                fixture.viewModel.healthConnectWeightState.value
            )
            assertTrue(fixture.store.bodyWeightWrites.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `explicit use writes preview weight and publishes adopted state`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation(weightKg = 63.4))
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()
            fixture.viewModel.useHealthConnectWeight()

            assertEquals(listOf(63.4), fixture.store.bodyWeightWrites)
            assertEquals(
                HealthConnectWeightUiState.Adopted(63.4),
                fixture.viewModel.healthConnectWeightState.value
            )
            assertEquals(63.4, fixture.store.userSettings.value.bodyWeight, 0.0)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `permission denial requests permission only after read and does not loop`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionResult = HealthConnectPermissionResult.NotGranted
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertEquals(
                HealthConnectWeightUiState.PermissionNeeded,
                fixture.viewModel.healthConnectWeightState.value
            )
            assertEquals(1, fixture.viewModel.healthConnectPermissionRequestVersion.value)

            fixture.viewModel.onHealthConnectPermissionResult()

            assertEquals(HealthConnectWeightUiState.PermissionNeeded, fixture.viewModel.healthConnectWeightState.value)
            assertEquals(1, fixture.viewModel.healthConnectPermissionRequestVersion.value)
            assertTrue(fixture.store.bodyWeightWrites.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `no data leaves authoritative local weight unchanged`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.NoData
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertEquals(HealthConnectWeightUiState.NoData, fixture.viewModel.healthConnectWeightState.value)
            assertTrue(fixture.store.bodyWeightWrites.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `provider unavailable and update required remain distinct`() {
        val unavailable = FakeHealthConnectWeightProvider().apply {
            availabilityResult = HealthConnectAvailabilityResult.Status(
                HealthConnectAvailability.UNAVAILABLE
            )
        }
        val unavailableFixture = fixture(unavailable)
        try {
            unavailableFixture.viewModel.readHealthConnectWeight()
            assertEquals(
                HealthConnectWeightUiState.Unavailable(HealthConnectAvailability.UNAVAILABLE),
                unavailableFixture.viewModel.healthConnectWeightState.value
            )
        } finally {
            unavailableFixture.close()
        }

        val updateRequired = FakeHealthConnectWeightProvider().apply {
            availabilityResult = HealthConnectAvailabilityResult.Status(
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
            )
        }
        val updateFixture = fixture(updateRequired)
        try {
            updateFixture.viewModel.readHealthConnectWeight()
            assertEquals(
                HealthConnectWeightUiState.UpdateRequired,
                updateFixture.viewModel.healthConnectWeightState.value
            )
        } finally {
            updateFixture.close()
        }
    }

    @Test
    fun `provider read error is exposed as stable error state`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Error(
                io.github.yingqiu0871.evolune.healthconnect.HealthConnectError.WEIGHT_READ_FAILED
            )
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertEquals(
                HealthConnectWeightUiState.Error(HealthConnectWeightUiError.READ_FAILED),
                fixture.viewModel.healthConnectWeightState.value
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `invalid preview is rejected at adoption boundary`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation(weightKg = 301.0))
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertEquals(
                HealthConnectWeightUiState.Preview(observation(weightKg = 301.0)),
                fixture.viewModel.healthConnectWeightState.value
            )
            fixture.viewModel.useHealthConnectWeight()
            assertEquals(
                HealthConnectWeightUiState.Error(HealthConnectWeightUiError.INVALID_WEIGHT),
                fixture.viewModel.healthConnectWeightState.value
            )
            assertTrue(fixture.store.bodyWeightWrites.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cancellation from availability is not converted to read error`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            availabilityFailure = CancellationException("availability cancelled")
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertTrue(
                fixture.viewModel.healthConnectWeightState.value !is
                    HealthConnectWeightUiState.Error
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cancellation from permission check is not converted to permission error`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            permissionFailure = CancellationException("permission cancelled")
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertTrue(
                fixture.viewModel.healthConnectWeightState.value !is
                    HealthConnectWeightUiState.Error
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cancellation from weight read is not converted to read error`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            readFailure = CancellationException("weight read cancelled")
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()

            assertTrue(
                fixture.viewModel.healthConnectWeightState.value !is
                    HealthConnectWeightUiState.Error
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `cancellation from adoption is not converted to adoption error`() {
        val provider = FakeHealthConnectWeightProvider().apply {
            weightResult = HealthConnectWeightResult.Success(observation())
        }
        val fixture = fixture(provider)
        fixture.store.updateFailure = CancellationException("adoption cancelled")
        try {
            fixture.viewModel.readHealthConnectWeight()
            fixture.viewModel.useHealthConnectWeight()

            assertTrue(
                fixture.viewModel.healthConnectWeightState.value !is
                    HealthConnectWeightUiState.Error
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun `duplicate read while loading is ignored`() {
        val gate = CompletableDeferred<Unit>()
        val provider = FakeHealthConnectWeightProvider().apply {
            readGate = gate
            weightResult = HealthConnectWeightResult.Success(observation())
        }
        val fixture = fixture(provider)
        try {
            fixture.viewModel.readHealthConnectWeight()
            fixture.viewModel.readHealthConnectWeight()

            assertEquals(1, provider.readCalls)
            assertEquals(HealthConnectWeightUiState.Loading, fixture.viewModel.healthConnectWeightState.value)

            gate.complete(Unit)
            assertEquals(HealthConnectWeightUiState.Preview(observation()), fixture.viewModel.healthConnectWeightState.value)
        } finally {
            fixture.close()
        }
    }

    private fun fixture(provider: FakeHealthConnectWeightProvider): Fixture {
        val store = FakeSettingsStore()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return Fixture(SettingsViewModel(store, provider, scope), store, scope)
    }

    private fun observation(weightKg: Double = 62.5): HealthConnectWeightObservation =
        HealthConnectWeightObservation(
            weightKg = weightKg,
            timestamp = Instant.parse("2026-08-23T00:00:00Z"),
            sourcePackageName = "com.example.health"
        )

    private data class Fixture(
        val viewModel: SettingsViewModel,
        val store: FakeSettingsStore,
        val scope: CoroutineScope
    ) {
        fun close() = scope.cancel()
    }
}

private class FakeSettingsStore : SettingsStore {
    override val userSettings = MutableStateFlow(UserSettings())
    val bodyWeightWrites = mutableListOf<Double>()
    var updateFailure: Throwable? = null

    override suspend fun updateBodyWeight(weight: Double): Boolean {
        updateFailure?.let { throw it }
        bodyWeightWrites += weight
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
}

private class FakeHealthConnectWeightProvider : HealthConnectWeightProvider {
    override val requiredPermissions = setOf(HealthConnectPermission.READ_WEIGHT)
    var availabilityResult: HealthConnectAvailabilityResult =
        HealthConnectAvailabilityResult.Status(HealthConnectAvailability.AVAILABLE)
    var permissionResult: HealthConnectPermissionResult = HealthConnectPermissionResult.Granted
    var weightResult: HealthConnectWeightResult = HealthConnectWeightResult.NoData
    var availabilityFailure: Throwable? = null
    var permissionFailure: Throwable? = null
    var readFailure: Throwable? = null
    var readGate: CompletableDeferred<Unit>? = null
    var readCalls = 0

    override suspend fun availability(): HealthConnectAvailabilityResult {
        availabilityFailure?.let { throw it }
        return availabilityResult
    }

    override suspend fun permissionState(): HealthConnectPermissionResult {
        permissionFailure?.let { throw it }
        return permissionResult
    }

    override suspend fun readLatestWeight(
        now: Instant,
        window: java.time.Duration
    ): HealthConnectWeightResult {
        readCalls++
        readGate?.await()
        readFailure?.let { throw it }
        return weightResult
    }
}
