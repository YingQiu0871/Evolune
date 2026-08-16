package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePresentation
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationTimelineItem
import io.github.yingqiu0871.evolune.experience.MedicationTimelineSelector
import io.github.yingqiu0871.evolune.experience.MedicationTimelineWindow
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Immutable, Widget-owned projection of Phone domain state. It is derived on every
 * refresh and deliberately contains no Room entity or mutable medication cache.
 */
internal sealed interface WidgetPresentationState {
    val visiblePlans: List<WidgetPlanPresentation>

    data object NoEnabledPlans : WidgetPresentationState {
        override val visiblePlans: List<WidgetPlanPresentation> = emptyList()
    }

    data class Timeline(
        override val visiblePlans: List<WidgetPlanPresentation>,
        val window: MedicationTimelineWindow,
        val nextMeaningfulBoundary: Instant?
    ) : WidgetPresentationState

    data class NoUpcomingOccurrence(
        override val visiblePlans: List<WidgetPlanPresentation>,
        val window: MedicationTimelineWindow,
        val nextMeaningfulBoundary: Instant?
    ) : WidgetPresentationState
}

internal data class WidgetPlanPresentation(
    val planId: UUID,
    val name: String,
    val doseMg: Double
)

/**
 * Bounded Widget policy. Android's provider cadence is the delivery mechanism;
 * this policy only identifies semantic boundaries and never schedules an alarm.
 */
internal object WidgetPresentationPolicy {
    private val history = Duration.ofDays(2)
    private val horizon = Duration.ofDays(14)
    private val matchingMargin = Duration.ofHours(1)
    private val dueBefore = Duration.ofHours(1)
    private val dueAfter = Duration.ofHours(1)

    fun occurrenceWindow(now: Instant): OccurrenceGenerationWindow = OccurrenceGenerationWindow(
        startInclusive = now.minus(history),
        endExclusive = now.plus(horizon)
    )

    fun eventWindow(now: Instant): Pair<Instant, Instant> {
        val occurrenceWindow = occurrenceWindow(now)
        return occurrenceWindow.startInclusive.minus(matchingMargin) to
            occurrenceWindow.endExclusive.plus(matchingMargin)
    }

    fun nextMeaningfulBoundary(items: List<MedicationTimelineItem>, now: Instant): Instant? =
        items.asSequence()
            .mapNotNull { item ->
                when (item.status) {
                    MedicationOccurrenceStatus.UPCOMING ->
                        item.occurrence.scheduledAt.minus(dueBefore)
                    MedicationOccurrenceStatus.DUE ->
                        item.occurrence.scheduledAt.plus(dueAfter)
                    MedicationOccurrenceStatus.RECORDED,
                    MedicationOccurrenceStatus.PAST_UNRECORDED -> null
                }
            }
            .filter { it.isAfter(now) }
            .minOrNull()
}

internal class WidgetPresentationMapper {
    fun map(
        enabledPlans: List<MedicationPlan>,
        doseEvents: List<DoseEvent>,
        now: Instant,
        zoneId: ZoneId
    ): WidgetPresentationState {
        val plans = enabledPlans.filter(MedicationPlan::isEnabled)
        if (plans.isEmpty()) return WidgetPresentationState.NoEnabledPlans

        val visiblePlans = plans
            .sortedBy { it.id.toString() }
            .take(MAX_VISIBLE_PLANS)
            .map { plan ->
                WidgetPlanPresentation(
                    planId = plan.id,
                    name = plan.name,
                    doseMg = plan.doseMG
                )
            }
        val occurrences = MedicationOccurrenceGenerator.generate(
            schedules = plans.map(MedicationPlan::toMedicationSchedule),
            window = WidgetPresentationPolicy.occurrenceWindow(now),
            zoneId = zoneId
        )
        val items = MedicationOccurrencePresentation.derive(
            occurrences = occurrences,
            recordedEvents = doseEvents.mapNotNull(DoseEvent::toRecordedMedicationEvent),
            now = now
        )
        val window = MedicationTimelineSelector.select(items, now)
        val nextBoundary = WidgetPresentationPolicy.nextMeaningfulBoundary(items, now)

        return if (window.current.isEmpty() && window.upcoming.isEmpty()) {
            WidgetPresentationState.NoUpcomingOccurrence(visiblePlans, window, nextBoundary)
        } else {
            WidgetPresentationState.Timeline(visiblePlans, window, nextBoundary)
        }
    }

    private companion object {
        const val MAX_VISIBLE_PLANS = 2
    }
}
