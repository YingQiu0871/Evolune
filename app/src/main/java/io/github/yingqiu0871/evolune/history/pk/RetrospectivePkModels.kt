package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import io.github.yingqiu0871.evolune.pk.SimulationResult
import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

// ---------------------------------------------------------------------------
// Frozen resource and grid constants (V17-C-01 §5.1, §9).
// ---------------------------------------------------------------------------

/** Single numerical-window resource boundary; NOT a historical correctness cutoff. */
val MAX_RETROSPECTIVE_PK_CALCULATED_DURATION: Duration = Duration.ofDays(366)

/** Numerical safety band for window endpoints (guarantees the millisecond round-trip). */
const val MAX_RETROSPECTIVE_PK_EPOCH_MILLIS: Long = 3_800_000_000_000_000L

/** Grid density, identical to the current production simulation pipeline. */
const val RETROSPECTIVE_PK_POINTS_PER_HOUR: Double = 12.0

/** Minimum grid size, identical to the current production simulation pipeline. */
const val RETROSPECTIVE_PK_MINIMUM_GRID_SIZE: Long = 1000L

/**
 * R4.2 frozen negative-roundoff output-validation tolerance (pg/mL).
 *
 * This is a numerical output-validation tolerance only: it is not a PK model parameter, not a
 * scientific parameter-set change, not a display clamp and not a limitation enum member.
 */
const val RETROSPECTIVE_PK_NEGATIVE_ROUNDOFF_TOLERANCE_PG_ML = 1e-9

// ---------------------------------------------------------------------------
// Local date-time resolution (V17-C-01 §5.1).
// ---------------------------------------------------------------------------

sealed interface LocalQueryTimeResolution {
    data class Resolved(
        val instant: Instant,
        val offset: ZoneOffset
    ) : LocalQueryTimeResolution

    /** DST gap: the requested value is preserved, the resolved value is explicit. */
    data class GapAdjusted(
        val requested: LocalDateTime,
        val resolvedInstant: Instant,
        val resolvedOffset: ZoneOffset
    ) : LocalQueryTimeResolution

    /** DST overlap: the caller must choose an offset explicitly. */
    data class OverlapChoiceRequired(
        val requested: LocalDateTime,
        val candidates: List<Resolved>
    ) : LocalQueryTimeResolution
}

object LocalQueryTimeResolver {
    /**
     * selected LocalDateTime -> explicit ZoneId/ZoneOffset -> Instant.
     *
     * A finite explicit offset must match a valid offset; overlap without an explicit
     * offset is reported as [LocalQueryTimeResolution.OverlapChoiceRequired] and never
     * silently resolved to an earlier/later offset.
     */
    fun resolve(
        selected: LocalDateTime,
        zoneId: ZoneId,
        explicitOffset: ZoneOffset? = null
    ): LocalQueryTimeResolution {
        try {
            val validOffsets = zoneId.rules.getValidOffsets(selected)
            return when {
                validOffsets.size == 1 -> {
                    val offset = validOffsets.single()
                    if (explicitOffset != null && explicitOffset != offset) {
                        throw IllegalArgumentException(
                            "explicit offset $explicitOffset is not valid for $selected in $zoneId"
                        )
                    }
                    LocalQueryTimeResolution.Resolved(selected.toInstant(offset), offset)
                }

                validOffsets.isEmpty() -> {
                    val resolved = selected.atZone(zoneId)
                    LocalQueryTimeResolution.GapAdjusted(
                        requested = selected,
                        resolvedInstant = resolved.toInstant(),
                        resolvedOffset = resolved.offset
                    )
                }

                else -> {
                    val candidates = validOffsets.map { offset ->
                        LocalQueryTimeResolution.Resolved(selected.toInstant(offset), offset)
                    }
                    if (explicitOffset == null) {
                        LocalQueryTimeResolution.OverlapChoiceRequired(selected, candidates)
                    } else {
                        candidates.firstOrNull { it.offset == explicitOffset }
                            ?: throw IllegalArgumentException(
                                "explicit offset $explicitOffset is not an overlap candidate " +
                                    "for $selected in $zoneId"
                            )
                    }
                }
            }
        } catch (error: DateTimeException) {
            throw IllegalArgumentException("local query time cannot be resolved: $selected", error)
        }
    }
}

// ---------------------------------------------------------------------------
// Window (V17-C-01 §5.1).
// ---------------------------------------------------------------------------

data class RetrospectivePkWindow(
    val startInclusive: Instant,
    val endInclusive: Instant
) {
    init {
        require(startInclusive.isBefore(endInclusive)) { "window must be strictly ordered" }
        require(startInclusive.isMillisecondAligned() && endInclusive.isMillisecondAligned()) {
            "retrospective window endpoints must be millisecond-aligned"
        }
    }

    val duration: Duration get() = Duration.between(startInclusive, endInclusive)

    companion object {
        /**
         * Date-only whole-day helper (deterministic): `atStartOfDay` resolves DST gaps
         * forward and overlap picks the earlier offset, matching the occurrence generator.
         */
        fun fromLocalDates(
            startDate: LocalDate,
            endDate: LocalDate,
            displayZone: ZoneId
        ): RetrospectivePkWindow {
            require(!endDate.isBefore(startDate)) { "end date must not precede start date" }
            return try {
                RetrospectivePkWindow(
                    startInclusive = startDate.atStartOfDay(displayZone).toInstant(),
                    endInclusive = endDate
                        .plusDays(1)
                        .atStartOfDay(displayZone)
                        .toInstant()
                        .minusMillis(1)
                )
            } catch (error: DateTimeException) {
                throw IllegalArgumentException("query interval cannot be represented", error)
            }
        }

        /**
         * Builds a window from explicit resolutions. An overlap that still requires an
         * offset choice must not be used to construct a window.
         */
        fun fromResolutions(
            start: LocalQueryTimeResolution,
            end: LocalQueryTimeResolution
        ): RetrospectivePkWindow {
            val startInstant = start.resolvedInstantOrNull()
                ?: throw IllegalArgumentException(
                    "start resolution requires an explicit offset choice: $start"
                )
            val endInstant = end.resolvedInstantOrNull()
                ?: throw IllegalArgumentException(
                    "end resolution requires an explicit offset choice: $end"
                )
            return RetrospectivePkWindow(startInstant, endInstant)
        }
    }
}

// Non-overflowing alignment predicate; epoch-millis representability is checked at the
// service numerical boundary instead (V17-C-01 §5.1).
private fun Instant.isMillisecondAligned(): Boolean = nano % 1_000_000 == 0

private fun LocalQueryTimeResolution.resolvedInstantOrNull(): Instant? = when (this) {
    is LocalQueryTimeResolution.Resolved -> instant
    is LocalQueryTimeResolution.GapAdjusted -> resolvedInstant
    is LocalQueryTimeResolution.OverlapChoiceRequired -> null
}

// ---------------------------------------------------------------------------
// Typed model provenance (V17-C-01 §5.2).
// ---------------------------------------------------------------------------

enum class RetrospectivePkParameterSet { EVOLUNE_E2_PK_PARAMETER_SET_V1 }

enum class ParameterBasis { CURRENT_MODEL_PARAMETERS }

enum class BodyWeightBasis { CURRENT_SETTING_AT_QUERY_TIME }

enum class IntakeBasis { RECORDED_ACTUAL_INTAKES }

enum class HistoricalParameterSnapshotAvailability { UNAVAILABLE }

data class RetrospectivePkModelContext(
    val parameterSet: RetrospectivePkParameterSet,
    val parameterBasis: ParameterBasis,
    val bodyWeightKg: Double,
    val bodyWeightBasis: BodyWeightBasis,
    val intakeBasis: IntakeBasis,
    val historicalParameterSnapshotAvailability: HistoricalParameterSnapshotAvailability,
    val capturedAt: Instant
) {
    companion object {
        fun current(bodyWeightKg: Double, capturedAt: Instant): RetrospectivePkModelContext =
            RetrospectivePkModelContext(
                parameterSet = RetrospectivePkParameterSet.EVOLUNE_E2_PK_PARAMETER_SET_V1,
                parameterBasis = ParameterBasis.CURRENT_MODEL_PARAMETERS,
                bodyWeightKg = bodyWeightKg,
                bodyWeightBasis = BodyWeightBasis.CURRENT_SETTING_AT_QUERY_TIME,
                intakeBasis = IntakeBasis.RECORDED_ACTUAL_INTAKES,
                historicalParameterSnapshotAvailability =
                    HistoricalParameterSnapshotAvailability.UNAVAILABLE,
                capturedAt = capturedAt
            )
    }
}

// ---------------------------------------------------------------------------
// Request (V17-C-01 §5.2).
// ---------------------------------------------------------------------------

data class RetrospectivePkRequest(
    val visibleWindow: RetrospectivePkWindow,
    val cursor: Instant?,
    val displayZone: ZoneId,
    val bodyWeightKG: Double,
    val capturedAt: Instant,
    val policy: MedicationOccurrencePolicy = MedicationOccurrencePolicy()
) {
    init {
        require(
            cursor == null || (!cursor.isBefore(visibleWindow.startInclusive) &&
                !cursor.isAfter(visibleWindow.endInclusive))
        ) { "cursor must lie inside the visible window" }
    }
}

// ---------------------------------------------------------------------------
// Exclusions / limitations / unavailable (V17-C-01 §5.3).
// ---------------------------------------------------------------------------

enum class RetrospectivePkExclusionReason {
    UNKNOWN_OR_PARTIAL_IDENTITY,
    ANTIANDROGEN_IDENTITY_UNAVAILABLE,
    UNSUPPORTED_CURRENT_MODEL_COMBINATION,
    UNSUPPORTED_OR_INCOMPLETE_EVENT,
    AMBIGUOUS_PATCH_PAIRING
}

data class RetrospectivePkExcludedEvent(
    val eventId: UUID,
    val occurredAt: Instant,
    val reason: RetrospectivePkExclusionReason
)

enum class RetrospectivePkLimitation {
    EARLIEST_AVAILABLE_HISTORY_ZERO_BASELINE,
    UNRECORDED_OCCURRENCES_PRESENT,
    EXCLUDED_RECORDED_INTAKES,
    AMBIGUOUS_PATCH_PAIRING
}

enum class RetrospectivePkUnavailableReason {
    NO_ELIGIBLE_RECORDED_INTAKES,
    INVALID_QUERY_INTERVAL,
    QUERY_OUTSIDE_CALCULATED_INTERVAL,
    HISTORICAL_INPUT_UNAVAILABLE
}

/**
 * Defensive internal validator (V17-C-01 RC-2): retained for future calculated-interval
 * implementations and internal invariant checks. It is not reachable through a normally
 * constructed current public request because `calculatedInterval == visibleWindow` and the
 * request already constrains the cursor to the visible window.
 */
internal object RetrospectivePkIntervalValidator {
    fun validateCursor(
        cursor: Instant?,
        calculatedInterval: RetrospectivePkWindow
    ): RetrospectivePkUnavailableReason? {
        if (cursor == null) return null
        return if (cursor.isBefore(calculatedInterval.startInclusive) ||
            cursor.isAfter(calculatedInterval.endInclusive)
        ) {
            RetrospectivePkUnavailableReason.QUERY_OUTSIDE_CALCULATED_INTERVAL
        } else {
            null
        }
    }
}

// ---------------------------------------------------------------------------
// Summary / series / cursor / result (V17-C-01 §5.4).
// ---------------------------------------------------------------------------

data class RetrospectivePkInputSummary(
    val lookbackStart: Instant?,
    val upperBoundInclusive: Instant,
    val engineInputEventIds: List<UUID>,
    val concentrationProducingEventIds: List<UUID>,
    val patchControlEventIds: List<UUID>
)

data class RetrospectivePkPoint(
    val instant: Instant,
    val concentrationPGmL: Double
)

data class RetrospectivePkSeries(
    val startInclusive: Instant,
    val endInclusive: Instant,
    val points: List<RetrospectivePkPoint>
)

data class RetrospectivePkCursorEstimate(
    val cursor: Instant,
    val concentrationPGmL: Double
)

sealed interface RetrospectivePkResult {
    val modelContext: RetrospectivePkModelContext
    val summary: RetrospectivePkInputSummary
    val exclusions: List<RetrospectivePkExcludedEvent>
    val limitations: Set<RetrospectivePkLimitation>

    data class Available(
        val calculatedInterval: RetrospectivePkWindow,
        val series: RetrospectivePkSeries,
        val cursorEstimate: RetrospectivePkCursorEstimate?,
        val curve: SimulationResult,
        override val modelContext: RetrospectivePkModelContext,
        override val summary: RetrospectivePkInputSummary,
        override val exclusions: List<RetrospectivePkExcludedEvent>,
        override val limitations: Set<RetrospectivePkLimitation>
    ) : RetrospectivePkResult

    data class Unavailable(
        val reason: RetrospectivePkUnavailableReason,
        override val modelContext: RetrospectivePkModelContext,
        override val summary: RetrospectivePkInputSummary,
        override val exclusions: List<RetrospectivePkExcludedEvent>,
        override val limitations: Set<RetrospectivePkLimitation>
    ) : RetrospectivePkResult
}

// ---------------------------------------------------------------------------
// Exceptions and series validation (V17-C-01 §5.5).
// ---------------------------------------------------------------------------

/** Retrospective-layer internal contract violation; fail-fast, never converted. */
class RetrospectivePkContractViolationException(message: String) : IllegalStateException(message)

object RetrospectivePkSeriesValidator {
    /**
     * R4.2 frozen validity domain (replaces the old "finite and non-negative" rule):
     *  - `concentration >= 0`                        -> valid;
     *  - `-1e-9 <= concentration < 0`                -> valid floating-point cancellation artifact
     *    (tau = 0 analytic-solution cancellation); the exact original Double is retained unchanged;
     *  - `concentration < -1e-9` / NaN / +Inf / -Inf -> [RetrospectivePkContractViolationException].
     *
     * No clamp / floor / normalize / drop / shift / rewrite is performed on any point; the series
     * concentrations stay exactly the unchanged engine concentrations.
     */
    fun validate(points: List<RetrospectivePkPoint>) {
        points.forEach { point ->
            val value = point.concentrationPGmL
            if (!value.isFinite() || value < -RETROSPECTIVE_PK_NEGATIVE_ROUNDOFF_TOLERANCE_PG_ML) {
                throw RetrospectivePkContractViolationException(
                    "retrospective PK series contains a non-finite or out-of-tolerance " +
                        "concentration at ${point.instant}"
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Seams (V17-C-01 §5.6).
// ---------------------------------------------------------------------------

fun interface RetrospectivePkSource {
    suspend fun estimate(request: RetrospectivePkRequest): RetrospectivePkResult
}

/** App-internal test seam; the default implementation calls the unchanged SimulationEngine. */
fun interface RetrospectivePkCurveRunner {
    fun run(
        events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
        bodyWeightKG: Double,
        startTimeH: Double,
        endTimeH: Double,
        numberOfSteps: Int
    ): SimulationResult
}

internal object DefaultRetrospectivePkCurveRunner : RetrospectivePkCurveRunner {
    override fun run(
        events: List<io.github.yingqiu0871.evolune.pk.DoseEvent>,
        bodyWeightKG: Double,
        startTimeH: Double,
        endTimeH: Double,
        numberOfSteps: Int
    ): SimulationResult = SimulationEngine(
        events = events,
        bodyWeightKG = bodyWeightKG,
        startTimeH = startTimeH,
        endTimeH = endTimeH,
        numberOfSteps = numberOfSteps
    ).run()
}
