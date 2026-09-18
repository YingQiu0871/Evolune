package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkModelContext
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkPoint
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkRequest
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSeries
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkUnavailableReason
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import io.github.yingqiu0871.evolune.pk.SimulationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/**
 * V17-C-04 test fixtures: recording fakes at the three approved seam boundaries plus typed
 * result builders. Every "how many reads / with which capture" assertion comes from these
 * recordings, so read discipline, capture sharing and race behavior are counted, never assumed.
 */

internal val C04_UTC: ZoneId = ZoneOffset.UTC
internal val C04_NOW: Instant = LocalDate.of(2026, 9, 16).atTime(12, 0).toInstant(ZoneOffset.UTC)

/**
 * The window the surface queries; v1.7.1 UI hotfix default is the 7-day range, and tests may ask
 * for any other selected length explicitly.
 */
internal fun c04Window(
    endingAt: Instant = C04_NOW,
    days: Long = RetrospectivePkRange.LAST_7_DAYS.days
): RetrospectivePkWindow =
    RetrospectivePkWindow(startInclusive = endingAt.minus(Duration.ofDays(days)), endInclusive = endingAt)

// ---------- recording seams ----------

internal class RecordingRetrospectivePkSource(
    var result: RetrospectivePkResult
) : RetrospectivePkSource {
    val requests = mutableListOf<RetrospectivePkRequest>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun estimate(request: RetrospectivePkRequest): RetrospectivePkResult {
        requests += request
        gate?.await()
        failure?.let { throw it }
        return result
    }
}

internal class RecordingAllAvailableHistorySource(
    var result: AllAvailableHistory
) : AllAvailableHistorySource {
    data class Call(
        val upperBoundInclusive: Instant,
        val displayZone: ZoneId,
        val policy: MedicationOccurrencePolicy
    )

    val calls = mutableListOf<Call>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun readAllAvailable(
        upperBoundInclusive: Instant,
        displayZone: ZoneId,
        policy: MedicationOccurrencePolicy
    ): AllAvailableHistory {
        calls += Call(upperBoundInclusive, displayZone, policy)
        gate?.await()
        failure?.let { throw it }
        return result
    }
}

internal class RecordingHistoryRangeSource(
    var result: HistoricalRange
) : HistoryRangeSource {
    data class Call(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val displayZone: ZoneId,
        val now: Instant
    )

    val calls = mutableListOf<Call>()
    var failure: Throwable? = null

    override suspend fun read(
        startDate: LocalDate,
        endDate: LocalDate,
        displayZone: ZoneId,
        now: Instant
    ): HistoricalRange {
        calls += Call(startDate, endDate, displayZone, now)
        failure?.let { throw it }
        return result
    }
}

internal class FakeSettingsStore(
    initialWeight: Double = 55.0
) : SettingsStore {
    val settings = MutableStateFlow(UserSettings(bodyWeight = initialWeight))
    var readCount = 0
        private set

    override val userSettings: Flow<UserSettings> = settings.onStart { readCount++ }

    override suspend fun updateBodyWeight(weight: Double): Boolean {
        settings.value = settings.value.copy(bodyWeight = weight)
        return true
    }

    override suspend fun updateThemeMode(mode: ThemeMode) = Unit
    override suspend fun updateColorTheme(theme: ColorTheme) = Unit
    override suspend fun updateThemeColorSource(source: ThemeColorSource) = Unit
    override suspend fun updateThemePreset(selection: ThemePresetSelection) = Unit
    override suspend fun updateAutoCheckUpdates(enabled: Boolean) = Unit
    override suspend fun updateTimeFormat(format: TimeFormat) = Unit
    override suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean) = Unit
    override suspend fun updateBodyWeightFromHealthConnect(weight: Double, adoptedAt: Instant): Boolean = true
    override suspend fun updateHealthConnectWeightMetadata(weight: Double, adoptedAt: Instant): Boolean = true
}

// ---------- typed result builders ----------

internal fun c04AvailableResult(
    window: RetrospectivePkWindow,
    engineInputEventIds: List<UUID> = emptyList(),
    concentrationProducingEventIds: List<UUID> = emptyList(),
    patchControlEventIds: List<UUID> = emptyList(),
    lookbackStart: Instant? = window.startInclusive.plusSeconds(3600),
    limitations: Set<io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation> = emptySet(),
    exclusions: List<io.github.yingqiu0871.evolune.history.pk.RetrospectivePkExcludedEvent> = emptyList(),
    series: RetrospectivePkSeries? = null
): RetrospectivePkResult.Available {
    val effectiveSeries = series ?: RetrospectivePkSeries(
        startInclusive = window.startInclusive,
        endInclusive = window.endInclusive,
        points = listOf(
            RetrospectivePkPoint(window.startInclusive, 0.0),
            RetrospectivePkPoint(window.endInclusive, 100.0)
        )
    )
    return RetrospectivePkResult.Available(
        calculatedInterval = window,
        series = effectiveSeries,
        cursorEstimate = null,
        curve = SimulationResult(
            timeH = effectiveSeries.points.map {
                Duration.between(window.startInclusive, it.instant).toMinutes() / 60.0
            },
            concPGmL = effectiveSeries.points.map { it.concentrationPGmL },
            auc = 0.0
        ),
        modelContext = RetrospectivePkModelContext.current(55.0, window.endInclusive),
        summary = io.github.yingqiu0871.evolune.history.pk.RetrospectivePkInputSummary(
            lookbackStart = lookbackStart,
            upperBoundInclusive = window.endInclusive,
            engineInputEventIds = engineInputEventIds,
            concentrationProducingEventIds = concentrationProducingEventIds,
            patchControlEventIds = patchControlEventIds
        ),
        exclusions = exclusions,
        limitations = limitations
    )
}

internal fun c04UnavailableResult(
    window: RetrospectivePkWindow,
    reason: RetrospectivePkUnavailableReason = RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES,
    limitations: Set<io.github.yingqiu0871.evolune.history.pk.RetrospectivePkLimitation> = emptySet()
): RetrospectivePkResult.Unavailable = RetrospectivePkResult.Unavailable(
    reason = reason,
    modelContext = RetrospectivePkModelContext.current(55.0, window.endInclusive),
    summary = io.github.yingqiu0871.evolune.history.pk.RetrospectivePkInputSummary(
        lookbackStart = null,
        upperBoundInclusive = window.endInclusive,
        engineInputEventIds = emptyList(),
        concentrationProducingEventIds = emptyList(),
        patchControlEventIds = emptyList()
    ),
    exclusions = emptyList(),
    limitations = limitations
)

internal fun c04AllAvailable(
    projection: HistoricalProjection = HistoricalProjection(entries = emptyList()),
    upperBoundInclusive: Instant = C04_NOW
): AllAvailableHistory = AllAvailableHistory(
    upperBoundInclusive = upperBoundInclusive,
    lookbackStart = projection.entries.firstOrNull()?.sortInstant,
    projection = projection
)

internal fun c04EmptyRange(window: RetrospectivePkWindow, zone: ZoneId = C04_UTC): HistoricalRange =
    HistoricalRange(
        startDate = window.startInclusive.atZone(zone).toLocalDate().minusDays(1),
        endDate = window.endInclusive.atZone(zone).toLocalDate().plusDays(1),
        days = emptyList()
    )
