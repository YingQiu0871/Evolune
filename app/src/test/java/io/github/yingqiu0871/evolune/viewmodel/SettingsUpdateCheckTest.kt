package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailability
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectAvailabilityResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectPermission
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectPermissionResult
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightProvider
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightResult
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUpdateCheckTest {
    @Test
    fun cancellationPropagatesWithoutPublishingFailure() {
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Unconfined)
        var fetchInvoked = false
        val viewModel = SettingsViewModel(
            settingsDataStore = TestSettingsStore,
            healthConnectWeightProvider = TestHealthConnectWeightProvider,
            operationScope = scope,
            fetchLatestRelease = {
                fetchInvoked = true
                throw CancellationException("synthetic cancellation")
            }
        )

        try {
            viewModel.checkForUpdates("1.7.4")

            assertTrue(fetchInvoked)
            assertEquals(UpdateCheckResult.Checking, viewModel.updateCheckResult.value)
            assertTrue("cancellation must not cancel the owning scope", parent.isActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun ordinaryFailureStillPublishesError() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(
            settingsDataStore = TestSettingsStore,
            healthConnectWeightProvider = TestHealthConnectWeightProvider,
            operationScope = scope,
            fetchLatestRelease = { throw IOException("synthetic network failure") },
            logUpdateCheckFailure = {}
        )

        try {
            viewModel.checkForUpdates("1.7.4")

            assertEquals(UpdateCheckResult.Error, viewModel.updateCheckResult.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newerReleaseStillPublishesUpdateAvailable() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(
            settingsDataStore = TestSettingsStore,
            healthConnectWeightProvider = TestHealthConnectWeightProvider,
            operationScope = scope,
            fetchLatestRelease = {
                io.github.yingqiu0871.evolune.utils.ReleaseInfo(
                    tagName = "v1.7.5",
                    releaseUrl = "https://example.test/releases/v1.7.5"
                )
            }
        )

        try {
            viewModel.checkForUpdates("1.7.4")

            assertEquals(
                UpdateCheckResult.UpdateAvailable(
                    tagName = "v1.7.5",
                    releaseUrl = "https://example.test/releases/v1.7.5"
                ),
                viewModel.updateCheckResult.value
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun matchingReleaseStillPublishesUpToDate() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = SettingsViewModel(
            settingsDataStore = TestSettingsStore,
            healthConnectWeightProvider = TestHealthConnectWeightProvider,
            operationScope = scope,
            fetchLatestRelease = {
                io.github.yingqiu0871.evolune.utils.ReleaseInfo(
                    tagName = "v1.7.4",
                    releaseUrl = "https://example.test/releases/v1.7.4"
                )
            }
        )

        try {
            viewModel.checkForUpdates("1.7.4")

            assertEquals(UpdateCheckResult.UpToDate, viewModel.updateCheckResult.value)
        } finally {
            scope.cancel()
        }
    }
}

private object TestSettingsStore : SettingsStore {
    override val userSettings: Flow<UserSettings> = flowOf(UserSettings())

    override suspend fun updateBodyWeight(weight: Double): Boolean = true

    override suspend fun updateThemeMode(mode: ThemeMode) = Unit

    override suspend fun updateColorTheme(theme: ColorTheme) = Unit

    override suspend fun updateThemeColorSource(source: ThemeColorSource) = Unit

    override suspend fun updateThemePreset(selection: ThemePresetSelection) = Unit

    override suspend fun updateAutoCheckUpdates(enabled: Boolean) = Unit

    override suspend fun updateTimeFormat(format: TimeFormat) = Unit

    override suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean) = Unit

    override suspend fun updateBodyWeightFromHealthConnect(
        weight: Double,
        adoptedAt: Instant
    ): Boolean = true

    override suspend fun updateHealthConnectWeightMetadata(
        weight: Double,
        adoptedAt: Instant
    ): Boolean = true
}

private object TestHealthConnectWeightProvider : HealthConnectWeightProvider {
    override val requiredPermissions: Set<HealthConnectPermission> =
        setOf(HealthConnectPermission.READ_WEIGHT)

    override suspend fun availability(): HealthConnectAvailabilityResult =
        HealthConnectAvailabilityResult.Status(HealthConnectAvailability.UNAVAILABLE)

    override suspend fun permissionState(): HealthConnectPermissionResult =
        HealthConnectPermissionResult.Unavailable(HealthConnectAvailability.UNAVAILABLE)

    override suspend fun readLatestWeight(
        now: Instant,
        window: Duration
    ): HealthConnectWeightResult = HealthConnectWeightResult.Unavailable(
        HealthConnectAvailability.UNAVAILABLE
    )
}
