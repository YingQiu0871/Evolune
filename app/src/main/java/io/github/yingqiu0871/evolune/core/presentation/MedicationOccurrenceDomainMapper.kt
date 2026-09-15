package io.github.yingqiu0871.evolune.core.presentation

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.experience.HistoricalMedicationExtraKey
import io.github.yingqiu0871.evolune.experience.MedicationIntakeSource
import io.github.yingqiu0871.evolune.experience.MedicationMatchKey
import io.github.yingqiu0871.evolune.experience.MedicationPresentation
import io.github.yingqiu0871.evolune.experience.MedicationSchedule
import io.github.yingqiu0871.evolune.experience.MedicationScheduleSlot
import io.github.yingqiu0871.evolune.experience.MedicationScheduleType
import io.github.yingqiu0871.evolune.experience.RecordedMedicationEvent

fun MedicationPlan.toMedicationSchedule(): MedicationSchedule = MedicationSchedule(
    planId = id,
    presentation = MedicationPresentation(
        planName = name,
        matchKey = MedicationMatchKey(
            routeKey = route.name,
            medicationKey = ester.name,
            doseAmount = doseMG
        )
    ),
    scheduleType = when (scheduleType) {
        ScheduleType.DAILY -> MedicationScheduleType.DAILY
        ScheduleType.WEEKLY -> MedicationScheduleType.WEEKLY
        ScheduleType.CUSTOM -> MedicationScheduleType.CUSTOM
    },
    slots = slots.map { slot ->
        MedicationScheduleSlot(
            id = slot.id,
            localTime = slot.localTime,
            position = slot.position
        )
    },
    daysOfWeek = daysOfWeek,
    intervalDays = intervalDays,
    enabled = isEnabled,
    createdAt = createdAt
)

fun DoseEvent.toRecordedMedicationEvent(): RecordedMedicationEvent? =
    takeIf { it.status == DoseEventStatus.RECORDED }?.let { event ->
        RecordedMedicationEvent(
            eventId = event.id,
            occurredAt = event.occurredAt,
            slotId = event.slotId,
            matchKey = MedicationMatchKey(
                routeKey = event.route.name,
                medicationKey = event.ester.name,
                doseAmount = event.doseMG
            ),
            source = event.source.toMedicationIntakeSource(),
            localDate = event.localDate,
            zoneId = event.zoneId,
            extras = event.extras.mapKeys { (key, _) -> key.toHistoricalMedicationExtraKey() }
        )
    }

/**
 * Exhaustive production mapping of the six authoritative extras keys into the
 * approved derived layer (V17-C-00 §4). Deliberately has no `else` branch so a
 * future key cannot be dropped silently.
 */
fun ExtraKey.toHistoricalMedicationExtraKey(): HistoricalMedicationExtraKey = when (this) {
    ExtraKey.CONCENTRATION_MG_ML -> HistoricalMedicationExtraKey.CONCENTRATION_MG_ML
    ExtraKey.AREA_CM2 -> HistoricalMedicationExtraKey.AREA_CM2
    ExtraKey.RELEASE_RATE_UG_PER_DAY -> HistoricalMedicationExtraKey.RELEASE_RATE_UG_PER_DAY
    ExtraKey.SUBLINGUAL_THETA -> HistoricalMedicationExtraKey.SUBLINGUAL_THETA
    ExtraKey.SUBLINGUAL_TIER -> HistoricalMedicationExtraKey.SUBLINGUAL_TIER
    ExtraKey.ANTI_ANDROGEN_TYPE -> HistoricalMedicationExtraKey.ANTI_ANDROGEN_TYPE
}

/**
 * Maps the authoritative Phone event origin onto the pure-Kotlin historical domain.
 * Only [DoseEventSource.MANUAL] may ever be presented as a manual intake.
 */
fun DoseEventSource.toMedicationIntakeSource(): MedicationIntakeSource = when (this) {
    DoseEventSource.LEGACY -> MedicationIntakeSource.LEGACY
    DoseEventSource.MANUAL -> MedicationIntakeSource.MANUAL
    DoseEventSource.JSON_V1 -> MedicationIntakeSource.JSON_V1
    DoseEventSource.REMINDER -> MedicationIntakeSource.REMINDER
    DoseEventSource.WIDGET -> MedicationIntakeSource.WIDGET
    DoseEventSource.WEAR -> MedicationIntakeSource.WEAR
}
