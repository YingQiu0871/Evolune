package io.github.yingqiu0871.evolune.history.retrospective

import io.github.yingqiu0871.evolune.experience.HistoricalProjection
import io.github.yingqiu0871.evolune.experience.HistoricalRange
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePolicy
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.experience.UnrecordedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityClassifier
import io.github.yingqiu0871.evolune.experience.insights.MedicationIdentityStatus
import io.github.yingqiu0871.evolune.history.AllAvailableHistorySource
import io.github.yingqiu0871.evolune.history.HistoryRangeSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkContractViolationException
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkRequest
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkResult
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkSource
import io.github.yingqiu0871.evolune.history.pk.RetrospectivePkWindow
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The once-per-load captured context (V17-C-04 §4.1). Exactly one such value exists per
 * generation; every applicable read receives values from this single capture.
 */
data class RetrospectivePkLoadCapture(
    val capturedAt: Instant,
    val displayZone: ZoneId,
    val window: RetrospectivePkWindow,
    val policy: MedicationOccurrencePolicy,
    val bodyWeightKg: Double
)

/** Outcome of one three-read load (V17-C-04 §4.2). */
sealed interface RetrospectivePkLoadOutcome {
    data class Content(
        val result: RetrospectivePkResult.Available,
        val markers: List<RetrospectiveMarker>
    ) : RetrospectivePkLoadOutcome

    data class Unavailable(
        val result: RetrospectivePkResult.Unavailable
    ) : RetrospectivePkLoadOutcome

    data class Failed(
        val failure: RetrospectiveLoadFailure
    ) : RetrospectivePkLoadOutcome
}

/**
 * V17-C-04 three-read orchestration (contract §D-2/§4.2).
 *
 * Boundary (frozen): this coordinator consumes ONLY the three approved seams —
 * [RetrospectivePkSource], [AllAvailableHistorySource] and [HistoryRangeSource]. It never
 * touches repositories, DAOs, Room, the concrete `HistoryReadService`, the retrospective
 * extractor, parameter resolution or the simulation engine. The concrete `HistoryReadService`
 * may appear only at the composition root where these three seams are constructed.
 *
 * The reads are sequential and explicitly NON-ATOMIC (§4.2.1):
 * - Read 1 `estimate(...)`: Unavailable or a failure stops the load (no marker reads);
 * - Read 2 `readAllAvailable(...)`: authoritative current recorded-event facts;
 * - Read 3 `read(...)`: complete current-schedule occurrence context.
 * No retry exists between reads; races never produce ERROR and are owned by the next generation.
 */
class RetrospectivePkSurfaceCoordinator(
    private val retrospectivePkSource: RetrospectivePkSource,
    private val allAvailableHistorySource: AllAvailableHistorySource,
    private val historyRangeSource: HistoryRangeSource
) {

    suspend fun load(capture: RetrospectivePkLoadCapture): RetrospectivePkLoadOutcome {
        val window = capture.window

        // Read 1: numerical estimate (internally performs the C-01 all-history read).
        val estimate = try {
            retrospectivePkSource.estimate(
                RetrospectivePkRequest(
                    visibleWindow = window,
                    cursor = null,
                    displayZone = capture.displayZone,
                    bodyWeightKG = capture.bodyWeightKg,
                    capturedAt = capture.capturedAt,
                    policy = capture.policy
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return failed(error)
        }

        val available = when (estimate) {
            is RetrospectivePkResult.Available -> estimate
            is RetrospectivePkResult.Unavailable -> return RetrospectivePkLoadOutcome.Unavailable(estimate)
        }

        // Read 2: authoritative current event facts for RecordedIntakeMarker.
        val allAvailable = try {
            allAvailableHistorySource.readAllAvailable(
                upperBoundInclusive = window.endInclusive,
                displayZone = capture.displayZone,
                policy = capture.policy
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return failed(error)
        }

        // Read 3: complete current-schedule occurrence context for ScheduleContextMarker.
        val startLocalDate = window.startInclusive.atZone(capture.displayZone).toLocalDate()
        val endLocalDate = window.endInclusive.atZone(capture.displayZone).toLocalDate()
        val historicalRange = try {
            historyRangeSource.read(
                startDate = startLocalDate.minusDays(1),
                endDate = endLocalDate.plusDays(1),
                displayZone = capture.displayZone,
                now = capture.capturedAt
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            return failed(error)
        }

        // RecordedIntakeMarker: immutable Read-1 accepted non-control ID set joined against the
        // current authoritative Read-2 payload (contract §3.2A / §4.2.1 — no C-01 eligibility rerun).
        val eligibleRecordedMarkerIds: Set<UUID> =
            available.summary.engineInputEventIds.toSet() - available.summary.patchControlEventIds.toSet()
        val intakeMarkers = RetrospectiveMarkerDerivation.recordedIntakeMarkers(
            projection = allAvailable.projection,
            eligibleEventIds = eligibleRecordedMarkerIds,
            window = window
        )
        val scheduleMarkers = RetrospectiveMarkerDerivation.scheduleMarkers(
            range = historicalRange,
            window = window
        )

        val markers = (scheduleMarkers + intakeMarkers).sortedWith(RETROSPECTIVE_MARKER_ORDER)
        return RetrospectivePkLoadOutcome.Content(result = available, markers = markers)
    }

    private fun failed(error: Throwable): RetrospectivePkLoadOutcome.Failed =
        RetrospectivePkLoadOutcome.Failed(
            if (error is RetrospectivePkContractViolationException) {
                RetrospectiveLoadFailure.ContractViolation(error)
            } else {
                RetrospectiveLoadFailure.ReadFailure(error)
            }
        )
}

/**
 * Pure marker derivation (V17-C-04 §3.2). Both families are gated by their own family instant
 * (inclusive window) and never re-run C-01 eligibility:
 * - intake markers require membership in the immutable Read-1 accepted non-control ID set plus
 *   the lightweight current-Read2-payload presentation guards (KNOWN identity, non-PATCH_REMOVE);
 * - schedule markers require KNOWN identity and a non-PATCH_REMOVE route on the occurrence key.
 */
internal object RetrospectiveMarkerDerivation {

    /**
     * Route key that must never surface as a marker (V17-C-04 §2.1.3 — removals are reachable
     * via restore-provided plans and are deliberately excluded from both channels).
     */
    const val PATCH_REMOVE_ROUTE_KEY = "PATCH_REMOVE"

    fun scheduleMarkers(
        range: HistoricalRange,
        window: RetrospectivePkWindow
    ): List<ScheduleContextMarker> = range.days
        .asSequence()
        .flatMap { day -> day.entries.asSequence() }
        .mapNotNull { entry ->
            val occurrence = when (entry) {
                is MatchedHistoricalOccurrence -> entry.occurrence
                is UnrecordedHistoricalOccurrence -> entry.occurrence
                is UnmatchedHistoricalIntake -> return@mapNotNull null
            }
            val provenance = when (entry) {
                is MatchedHistoricalOccurrence -> ScheduleMarkerProvenance.MATCHED_OCCURRENCE
                is UnrecordedHistoricalOccurrence -> ScheduleMarkerProvenance.UNRECORDED_OCCURRENCE
                is UnmatchedHistoricalIntake -> return@mapNotNull null
            }
            if (!isInsideWindow(occurrence.scheduledAt, window)) return@mapNotNull null
            val matchKey = occurrence.presentation.matchKey
            if (matchKey.routeKey == PATCH_REMOVE_ROUTE_KEY) return@mapNotNull null
            if (MedicationIdentityClassifier.classify(matchKey).status != MedicationIdentityStatus.KNOWN) {
                return@mapNotNull null
            }
            ScheduleContextMarker(
                occurrenceId = occurrence.id,
                scheduledAt = occurrence.scheduledAt,
                provenance = provenance
            )
        }
        .toList()

    fun recordedIntakeMarkers(
        projection: HistoricalProjection,
        eligibleEventIds: Set<UUID>,
        window: RetrospectivePkWindow
    ): List<RecordedIntakeMarker> = projection.entries
        .asSequence()
        .mapNotNull { entry ->
            val event = when (entry) {
                is MatchedHistoricalOccurrence -> entry.event
                is UnmatchedHistoricalIntake -> entry.event
                is UnrecordedHistoricalOccurrence -> return@mapNotNull null
            }
            val provenance = when (entry) {
                is MatchedHistoricalOccurrence -> IntakeMarkerProvenance.MATCHED_INTAKE
                is UnmatchedHistoricalIntake -> IntakeMarkerProvenance.UNMATCHED_INTAKE
                is UnrecordedHistoricalOccurrence -> return@mapNotNull null
            }
            if (event.eventId !in eligibleEventIds) return@mapNotNull null
            if (!isInsideWindow(event.occurredAt, window)) return@mapNotNull null
            val matchKey = event.matchKey
            if (matchKey.routeKey == PATCH_REMOVE_ROUTE_KEY) return@mapNotNull null
            if (MedicationIdentityClassifier.classify(matchKey).status != MedicationIdentityStatus.KNOWN) {
                return@mapNotNull null
            }
            RecordedIntakeMarker(
                eventId = event.eventId,
                occurredAt = event.occurredAt,
                provenance = provenance
            )
        }
        .toList()

    private fun isInsideWindow(instant: Instant, window: RetrospectivePkWindow): Boolean =
        !instant.isBefore(window.startInclusive) && !instant.isAfter(window.endInclusive)
}

/** Deterministic marker order: instant ascending, then a stable family/id tie-break. */
internal val RETROSPECTIVE_MARKER_ORDER: Comparator<RetrospectiveMarker> =
    compareBy<RetrospectiveMarker> { it.instant }
        .thenBy { marker ->
            when (marker) {
                is ScheduleContextMarker -> "0:" + marker.occurrenceId.value
                is RecordedIntakeMarker -> "1:" + marker.eventId
            }
        }
