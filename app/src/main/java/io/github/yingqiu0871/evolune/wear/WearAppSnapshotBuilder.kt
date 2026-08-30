package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePresentation
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentration
import io.github.yingqiu0871.evolune.experience.wear.WearAppConcentrationStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppOverallStatus
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppRecentDose
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshot
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotRules
import io.github.yingqiu0871.evolune.experience.wear.WearAppUpcomingOccurrence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

internal object WearAppSnapshotBuilder {
    fun build(
        plans: List<MedicationPlan>,
        events: List<DoseEvent>,
        generatedAt: Instant,
        zoneId: ZoneId,
        snapshotRevision: Long,
        currentConcentration: Double?,
        concentrationError: Boolean = false,
        producerIdentity: WearAppProducerIdentity
    ): WearAppSnapshot {
        val enabledPlans = plans
            .filter { plan ->
                plan.isEnabled &&
                    plan.name.isNotBlank() &&
                    plan.doseMG.isFinite() &&
                    plan.doseMG >= 0.0
            }
            .sortedBy { it.id.toString() }
        val recordedEvents = WearAppRecentDoseSelector.eligible(events)

        val occurrences = MedicationOccurrenceGenerator.generate(
            schedules = enabledPlans.map(MedicationPlan::toMedicationSchedule),
            window = OccurrenceGenerationWindow(
                startInclusive = generatedAt.minus(Duration.ofHours(1)),
                endExclusive = generatedAt.plus(Duration.ofDays(366))
            ),
            zoneId = zoneId
        )
        val timeline = MedicationOccurrencePresentation.derive(
            occurrences = occurrences,
            recordedEvents = recordedEvents.mapNotNull(DoseEvent::toRecordedMedicationEvent),
            now = generatedAt
        )
        val upcoming = timeline
            .asSequence()
            .filter { item ->
                item.status == MedicationOccurrenceStatus.UPCOMING ||
                    item.status == MedicationOccurrenceStatus.DUE
            }
            .sortedWith(
                compareBy(
                    { item -> item.occurrence.scheduledAt },
                    { item -> item.occurrence.id.value.toString() }
                )
            )
            .take(WearAppSnapshotRules.MAX_UPCOMING_OCCURRENCES)
            .map { item ->
                WearAppUpcomingOccurrence(
                    occurrenceId = item.occurrence.id.value,
                    planId = item.occurrence.planId,
                    slotId = item.occurrence.slotId,
                    localDate = item.occurrence.scheduledLocalDateTime.toLocalDate(),
                    scheduledAt = item.occurrence.scheduledAt,
                    medicationName = item.occurrence.presentation.planName,
                    route = item.occurrence.presentation.matchKey.routeKey,
                    dose = item.occurrence.presentation.matchKey.doseAmount,
                    doseUnit = WearAppSnapshotRules.DOSE_UNIT_MILLIGRAM,
                    status = item.status.toWearAppStatus()
                )
            }
            .toList()

        val recentDose = WearAppRecentDoseSelector
            .select(recordedEvents)
            ?.toWearAppRecentDose(plans)

        val concentration = when {
            concentrationError -> WearAppConcentration(WearAppConcentrationStatus.ERROR)
            currentConcentration != null &&
                currentConcentration.isFinite() &&
                currentConcentration >= 0.0 -> WearAppConcentration(
                status = WearAppConcentrationStatus.AVAILABLE,
                value = currentConcentration,
                unit = WearAppSnapshotRules.CONCENTRATION_UNIT_PG_ML
            )
            else -> WearAppConcentration(WearAppConcentrationStatus.EMPTY)
        }

        return WearAppSnapshot(
            protocolVersion = io.github.yingqiu0871.evolune.experience.wear.WearAppProtocol.PROTOCOL_VERSION,
            snapshotRevision = snapshotRevision,
            generatedAt = generatedAt,
            zoneId = zoneId.id,
            overallStatus = if (enabledPlans.isEmpty()) {
                WearAppOverallStatus.EMPTY
            } else {
                WearAppOverallStatus.READY
            },
            recentDose = recentDose,
            upcomingOccurrences = upcoming,
            concentrationState = concentration,
            producerInstanceId = producerIdentity.producerInstanceId,
            producerGeneration = producerIdentity.producerGeneration
        ).also { snapshot ->
            check(WearAppSnapshotRules.isValid(snapshot))
        }
    }

    private fun MedicationOccurrenceStatus.toWearAppStatus() = when (this) {
        MedicationOccurrenceStatus.UPCOMING ->
            io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus.UPCOMING
        MedicationOccurrenceStatus.DUE ->
            io.github.yingqiu0871.evolune.experience.wear.WearAppOccurrenceStatus.DUE
        MedicationOccurrenceStatus.RECORDED,
        MedicationOccurrenceStatus.PAST_UNRECORDED -> error("non-upcoming occurrence")
    }

    private fun DoseEvent.toWearAppRecentDose(plans: List<MedicationPlan>): WearAppRecentDose {
        val matchingPlan = plans
            .sortedBy { it.id.toString() }
            .firstOrNull { plan ->
                slotId != null && plan.slots.any { slot -> slot.id == slotId }
            }
        return WearAppRecentDose(
            eventId = id,
            planId = matchingPlan?.id,
            slotId = slotId,
            localDate = localDate,
            occurredAt = occurredAt,
            medicationName = matchingPlan?.name ?: ester.fullName(),
            route = route.name,
            dose = doseMG,
            doseUnit = WearAppSnapshotRules.DOSE_UNIT_MILLIGRAM,
            source = source.name,
            eventRevision = revision
        )
    }
}
