package io.github.yingqiu0871.evolune.history.pk

import io.github.yingqiu0871.evolune.core.adapter.toPkExtraKey
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.experience.HistoricalMedicationExtraKey
import io.github.yingqiu0871.evolune.experience.MatchedHistoricalOccurrence
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.UnmatchedHistoricalIntake
import io.github.yingqiu0871.evolune.history.AllAvailableHistory
import io.github.yingqiu0871.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import java.time.Instant
import java.util.UUID

/** Engine input produced from the approved all-history projection result. */
data class RetrospectivePkExtraction(
    val engineEvents: List<PkDoseEvent>,
    val engineInputEventIds: List<UUID>,
    val concentrationProducingEventIds: List<UUID>,
    val patchControlEventIds: List<UUID>,
    val exclusions: List<RetrospectivePkExcludedEvent>
)

/**
 * Extracts the retrospective PK engine input from a consumed projection (V17-C-01 §6).
 *
 * It never reads repositories or raw rows; every numerical fact comes from the derived
 * layer (`RecordedMedicationEvent`, including its `extras`).
 */
object RetrospectivePkExtractor {

    private val MODEL_DRIVING_EXTRA_KEYS = setOf(
        HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY,
        HistoricalMedicationExtraKey.SUBLINGUAL_THETA,
        HistoricalMedicationExtraKey.SUBLINGUAL_TIER
    )

    fun extract(
        source: AllAvailableHistory,
        window: RetrospectivePkWindow
    ): RetrospectivePkExtraction {
        val exclusions = mutableListOf<RetrospectivePkExcludedEvent>()
        val seenEventIds = mutableSetOf<UUID>()
        val candidates = mutableListOf<Candidate>()

        source.projection.entries.forEach { entry ->
            val event = when (entry) {
                is MatchedHistoricalOccurrence -> entry.event
                is UnmatchedHistoricalIntake -> entry.event
                else -> return@forEach
            }
            if (!seenEventIds.add(event.eventId)) {
                throw RetrospectivePkContractViolationException(
                    "retrospective extraction received duplicate authoritative event ${event.eventId}"
                )
            }
            if (event.occurredAt.isAfter(window.endInclusive)) {
                throw RetrospectivePkContractViolationException(
                    "projection entry ${event.eventId} lies after the consumed upper bound"
                )
            }
            classify(event, exclusions, candidates)
        }

        val ordered = candidates.sortedWith(
            compareBy({ it.occurredAt }, { it.eventId.toString() })
        )

        val patchTransitions = ordered
            .filter { it.route == Route.PATCH_APPLY || it.route == Route.PATCH_REMOVE }
            .map { candidate ->
                RetrospectivePkPatchTransition(
                    eventId = candidate.eventId,
                    occurredAt = candidate.occurredAt,
                    route = candidate.route,
                    usable = candidate.pkEvent != null
                )
            }
        val patchResult = RetrospectivePkPatchPreprocessor.preprocess(patchTransitions)
        exclusions += patchResult.exclusions

        val engineCandidates = ordered.filter { candidate ->
            if (candidate.pkEvent == null) {
                // Unusable patch transitions are only retained when the grammar keeps them;
                // they never reach the engine with a null pk event.
                false
            } else if (candidate.route == Route.PATCH_APPLY || candidate.route == Route.PATCH_REMOVE) {
                candidate.eventId in patchResult.keptEventIds
            } else {
                true
            }
        }

        return RetrospectivePkExtraction(
            engineEvents = engineCandidates.map { requireNotNull(it.pkEvent) },
            engineInputEventIds = engineCandidates.map { it.eventId },
            concentrationProducingEventIds = engineCandidates
                .filter { it.producing }
                .map { it.eventId },
            patchControlEventIds = engineCandidates
                .filter { it.route == Route.PATCH_REMOVE }
                .map { it.eventId },
            exclusions = exclusions
        )
    }

    private fun classify(
        event: RecordedMedicationEvent,
        exclusions: MutableList<RetrospectivePkExcludedEvent>,
        candidates: MutableList<Candidate>
    ) {
        val route = parseRoute(event)
        if (route == Route.ANTIANDROGEN) {
            // Anti-androgen identity is unavailable for E2 PK; Ester must not be parsed.
            exclusions += RetrospectivePkExcludedEvent(
                eventId = event.eventId,
                occurredAt = event.occurredAt,
                reason = RetrospectivePkExclusionReason.ANTIANDROGEN_IDENTITY_UNAVAILABLE
            )
            return
        }
        val ester = parseEster(event)
        val doseMG = event.matchKey.doseAmount
        val extras = event.extras

        if (route == Route.INJECTION && ester == Ester.E2) {
            exclusions += RetrospectivePkExcludedEvent(
                eventId = event.eventId,
                occurredAt = event.occurredAt,
                reason = RetrospectivePkExclusionReason.UNSUPPORTED_CURRENT_MODEL_COMBINATION
            )
            return
        }

        val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
            is LegacyTimeResult.Success -> result.value
            is LegacyTimeResult.Failure -> null
        }
        val hasNonFiniteModelDrivingValue = !doseMG.isFinite() || extras.any { (key, value) ->
            key in MODEL_DRIVING_EXTRA_KEYS && !value.isFinite()
        }
        val unusable = timeH == null || hasNonFiniteModelDrivingValue

        val isPatchTransition = route == Route.PATCH_APPLY || route == Route.PATCH_REMOVE
        if (!isPatchTransition && unusable) {
            exclusions += RetrospectivePkExcludedEvent(
                eventId = event.eventId,
                occurredAt = event.occurredAt,
                reason = RetrospectivePkExclusionReason.UNSUPPORTED_OR_INCOMPLETE_EVENT
            )
            return
        }

        val pkEvent = if (unusable) {
            null
        } else {
            PkDoseEvent(
                id = event.eventId,
                route = route,
                timeH = requireNotNull(timeH),
                doseMG = doseMG,
                ester = ester,
                extras = extras.mapKeys { (key, _) -> key.toPkExtraKey() }
            )
        }
        candidates += Candidate(
            eventId = event.eventId,
            occurredAt = event.occurredAt,
            route = route,
            pkEvent = pkEvent,
            producing = pkEvent != null && isConcentrationProducing(route, doseMG, extras)
        )
    }

    private fun parseRoute(event: RecordedMedicationEvent): Route = try {
        Route.valueOf(event.matchKey.routeKey)
    } catch (error: IllegalArgumentException) {
        throw RetrospectivePkContractViolationException(
            "authoritative event ${event.eventId} has an unmapped route key " +
                "'${event.matchKey.routeKey}'"
        )
    }

    private fun parseEster(event: RecordedMedicationEvent): Ester = try {
        Ester.valueOf(event.matchKey.medicationKey)
    } catch (error: IllegalArgumentException) {
        throw RetrospectivePkContractViolationException(
            "authoritative event ${event.eventId} has an unmapped medication key " +
                "'${event.matchKey.medicationKey}'"
        )
    }

    /**
     * Frozen producing definition (V17-C-01 §7.3(d), R4.2):
     *  - PATCH_REMOVE -> never producing;
     *  - PATCH_APPLY with the release-rate key PRESENT -> producing iff the rate is finite and > 0
     *    (a present-but-non-positive rate keeps the current zero-contribution engine path and is
     *    NOT producing even for a positive dose);
     *  - PATCH_APPLY with the release-rate key ABSENT -> producing iff the finite dose is > 0
     *    (current first-order behavior);
     *  - supported non-patch routes -> producing iff the finite dose is > 0.
     *
     * A non-finite release rate has already failed eligibility before this predicate is reached.
     */
    private fun isConcentrationProducing(
        route: Route,
        doseMG: Double,
        extras: Map<HistoricalMedicationExtraKey, Double>
    ): Boolean = when (route) {
        Route.PATCH_REMOVE -> false
        Route.PATCH_APPLY -> {
            val releaseRate = extras[HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY]
            if (releaseRate != null) {
                releaseRate.isFinite() && releaseRate > 0.0
            } else {
                doseMG.isFinite() && doseMG > 0.0
            }
        }
        else -> doseMG.isFinite() && doseMG > 0.0
    }

    private data class Candidate(
        val eventId: UUID,
        val occurredAt: Instant,
        val route: Route,
        val pkEvent: PkDoseEvent?,
        val producing: Boolean
    )
}
