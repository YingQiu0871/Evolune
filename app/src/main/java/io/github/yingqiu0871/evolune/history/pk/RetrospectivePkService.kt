package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.data.isValidBodyWeight
import io.github.yingqiu0871.evolune.data.repository.RepositoryStorageException
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoricalOccurrenceLimitExceededException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

/**
 * Retrospective PK service (V17-C-01 §9).
 *
 * Read-only: it consumes [AllAvailableHistorySource] only, calls the unchanged
 * [io.github.yingqiu0871.evolune.pk.SimulationEngine] through the curve-runner seam and
 * never touches repositories, DAOs or persistence.
 */
class RetrospectivePkService internal constructor(
    private val history: AllAvailableHistorySource,
    private val curveRunner: RetrospectivePkCurveRunner,
    private val computationDispatcher: CoroutineDispatcher = Dispatchers.Default
) : RetrospectivePkSource {

    constructor(history: AllAvailableHistorySource) : this(
        history = history,
        curveRunner = DefaultRetrospectivePkCurveRunner,
        computationDispatcher = Dispatchers.Default
    )

    override suspend fun estimate(request: RetrospectivePkRequest): RetrospectivePkResult =
        withContext(computationDispatcher) {
            estimateOnComputationDispatcher(request)
        }

    private suspend fun estimateOnComputationDispatcher(
        request: RetrospectivePkRequest
    ): RetrospectivePkResult {
        require(isValidBodyWeight(request.bodyWeightKG)) {
            "retrospective PK body weight must be finite, positive and within the settings range"
        }
        val modelContext = RetrospectivePkModelContext.current(
            bodyWeightKg = request.bodyWeightKG,
            capturedAt = request.capturedAt
        )
        val window = request.visibleWindow

        // Query/resource validation, all typed INVALID_QUERY_INTERVAL (no exception escape),
        // performed before any history read, extraction, step calculation or engine call.
        validateQuery(window)?.let { reason ->
            return unavailable(reason, modelContext, emptySummary(window))
        }
        // Defensive interval validator (RC-2); unreachable through a normal public request.
        RetrospectivePkIntervalValidator.validateCursor(request.cursor, window)?.let { reason ->
            return unavailable(reason, modelContext, emptySummary(window))
        }

        currentCoroutineContext().ensureActive()
        val source = try {
            history.readAllAvailable(window.endInclusive, request.displayZone, request.policy)
        } catch (error: CancellationException) {
            throw error
        } catch (error: HistoricalOccurrenceLimitExceededException) {
            return unavailable(
                RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
                modelContext,
                emptySummary(window)
            )
        } catch (error: RepositoryStorageException) {
            return unavailable(
                RetrospectivePkUnavailableReason.HISTORICAL_INPUT_UNAVAILABLE,
                modelContext,
                emptySummary(window)
            )
        }
        currentCoroutineContext().ensureActive()

        if (source.lookbackStart == null) {
            return unavailable(
                RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES,
                modelContext,
                emptySummary(window)
            )
        }

        val extraction = RetrospectivePkExtractor.extract(source, window)
        val summary = RetrospectivePkInputSummary(
            lookbackStart = source.lookbackStart,
            upperBoundInclusive = window.endInclusive,
            engineInputEventIds = extraction.engineInputEventIds,
            concentrationProducingEventIds = extraction.concentrationProducingEventIds,
            patchControlEventIds = extraction.patchControlEventIds
        )

        if (extraction.concentrationProducingEventIds.isEmpty()) {
            return unavailable(
                reason = RetrospectivePkUnavailableReason.NO_ELIGIBLE_RECORDED_INTAKES,
                modelContext = modelContext,
                summary = summary,
                exclusions = extraction.exclusions,
                limitations = limitations(source, extraction.exclusions, includeEarliest = false)
            )
        }

        val startTimeH = instantToTimeH(window.startInclusive)
        val endTimeH = instantToTimeH(window.endInclusive)
        val hours = endTimeH - startTimeH
        val steps = computeSteps(hours)

        currentCoroutineContext().ensureActive()
        val curve = curveRunner.run(
            events = extraction.engineEvents,
            bodyWeightKG = request.bodyWeightKG,
            startTimeH = startTimeH,
            endTimeH = endTimeH,
            numberOfSteps = steps
        )
        currentCoroutineContext().ensureActive()

        val seriesPoints = curve.timeH.mapIndexed { index, timeH ->
            RetrospectivePkPoint(
                instant = Instant.ofEpochMilli(requireEpochMillis(timeH)),
                concentrationPGmL = curve.concPGmL[index]
            )
        }
        RetrospectivePkSeriesValidator.validate(seriesPoints)
        val series = RetrospectivePkSeries(
            startInclusive = window.startInclusive,
            endInclusive = window.endInclusive,
            points = seriesPoints
        )

        val cursorEstimate = request.cursor?.let { cursor ->
            RetrospectivePkCursorEstimate(
                cursor = cursor,
                concentrationPGmL = interpolate(seriesPoints, cursor)
            )
        }

        return RetrospectivePkResult.Available(
            calculatedInterval = window,
            series = series,
            cursorEstimate = cursorEstimate,
            curve = curve,
            modelContext = modelContext,
            summary = summary,
            exclusions = extraction.exclusions,
            limitations = limitations(source, extraction.exclusions, includeEarliest = true)
        )
    }

    private fun validateQuery(window: RetrospectivePkWindow): RetrospectivePkUnavailableReason? {
        val startMillis = try {
            window.startInclusive.toEpochMilli()
        } catch (error: ArithmeticException) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        val endMillis = try {
            window.endInclusive.toEpochMilli()
        } catch (error: ArithmeticException) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        val band = MAX_RETROSPECTIVE_PK_EPOCH_MILLIS
        if (startMillis > band || startMillis < -band || endMillis > band || endMillis < -band) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        val durationMillis = try {
            Math.subtractExact(endMillis, startMillis)
        } catch (error: ArithmeticException) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        if (durationMillis < 0L) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        if (durationMillis > MAX_RETROSPECTIVE_PK_CALCULATED_DURATION.toMillis()) {
            return RetrospectivePkUnavailableReason.INVALID_QUERY_INTERVAL
        }
        return null
    }

    private fun computeSteps(hours: Double): Int {
        // Double.toLong() is mathematically safe here because the frozen <=366-day
        // finite/non-negative precondition limits densitySteps to about 105,409; Double
        // saturation is unreachable. The explicit range check protects Long -> Int.
        if (!hours.isFinite() || hours < 0.0) {
            throw RetrospectivePkContractViolationException(
                "retrospective PK window hours must be finite and non-negative"
            )
        }
        val densitySteps = ceil(hours * RETROSPECTIVE_PK_POINTS_PER_HOUR).toLong() + 1L
        val stepsLong = maxOf(densitySteps, RETROSPECTIVE_PK_MINIMUM_GRID_SIZE)
        if (stepsLong > Int.MAX_VALUE) {
            throw RetrospectivePkContractViolationException(
                "retrospective PK step count exceeds Int range"
            )
        }
        return stepsLong.toInt()
    }

    private fun instantToTimeH(instant: Instant): Double =
        when (val result = LegacyTimeAdapter.instantToTimeH(instant)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw RetrospectivePkContractViolationException(
                "retrospective PK window endpoint cannot be represented in epoch hours: $instant"
            )
        }

    private fun requireEpochMillis(timeH: Double): Long =
        when (val result = LegacyTimeAdapter.timeHToEpochMillis(timeH)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> throw RetrospectivePkContractViolationException(
                "retrospective PK grid point cannot be represented in epoch milliseconds: $timeH"
            )
        }

    /**
     * Binary search plus linear interpolation over the Instant-based series only.
     * `SimulationResult.concentration()` is deliberately never used: its endpoint clamp
     * must not manufacture an out-of-range estimate.
     *
     * The cursor estimate is a convex combination of two already-valid finite neighbouring
     * series values; it does not mutate, replace or rewrite any stored series value.
     */
    private fun interpolate(points: List<RetrospectivePkPoint>, cursor: Instant): Double {
        if (points.isEmpty()) {
            throw RetrospectivePkContractViolationException(
                "retrospective PK cursor estimate requires a non-empty series"
            )
        }
        if (cursor.isBefore(points.first().instant) || cursor.isAfter(points.last().instant)) {
            throw RetrospectivePkContractViolationException(
                "cursor outside the calculated interval reached interpolation"
            )
        }
        var low = 0
        var high = points.size - 1
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (points[mid].instant <= cursor) low = mid else high = mid
        }
        val p0 = points[low]
        val p1 = points[high]
        val spanNanos = Duration.between(p0.instant, p1.instant).toNanos()
        if (spanNanos <= 0L) return p0.concentrationPGmL
        val offsetNanos = Duration.between(p0.instant, cursor).toNanos()
        val ratio = offsetNanos.toDouble() / spanNanos.toDouble()
        return p0.concentrationPGmL + (p1.concentrationPGmL - p0.concentrationPGmL) * ratio
    }

    private fun limitations(
        source: AllAvailableHistory,
        exclusions: List<RetrospectivePkExcludedEvent>,
        includeEarliest: Boolean
    ): Set<RetrospectivePkLimitation> {
        val result = linkedSetOf<RetrospectivePkLimitation>()
        if (includeEarliest) {
            result += RetrospectivePkLimitation.EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE
        }
        if (source.projection.entries.any { it is UnrecordedHistoricalOccurrence }) {
            result += RetrospectivePkLimitation.UNRECORDED_OCCURRENCES_PRESENT
        }
        if (exclusions.isNotEmpty()) {
            result += RetrospectivePkLimitation.EXCLUDED_RECORDED_INTAKES
        }
        if (exclusions.any { it.reason == RetrospectivePkExclusionReason.AMBIGUOUS_PATCH_PAIRING }) {
            result += RetrospectivePkLimitation.AMBIGUOUS_PATCH_PAIRING
        }
        return result
    }

    private fun emptySummary(
        window: RetrospectivePkWindow,
        source: AllAvailableHistory? = null
    ): RetrospectivePkInputSummary = RetrospectivePkInputSummary(
        lookbackStart = source?.lookbackStart,
        upperBoundInclusive = window.endInclusive,
        engineInputEventIds = emptyList(),
        concentrationProducingEventIds = emptyList(),
        patchControlEventIds = emptyList()
    )

    private fun unavailable(
        reason: RetrospectivePkUnavailableReason,
        modelContext: RetrospectivePkModelContext,
        summary: RetrospectivePkInputSummary,
        exclusions: List<RetrospectivePkExcludedEvent> = emptyList(),
        limitations: Set<RetrospectivePkLimitation> = emptySet()
    ): RetrospectivePkResult.Unavailable = RetrospectivePkResult.Unavailable(
        reason = reason,
        modelContext = modelContext,
        summary = summary,
        exclusions = exclusions,
        limitations = limitations
    )
}
