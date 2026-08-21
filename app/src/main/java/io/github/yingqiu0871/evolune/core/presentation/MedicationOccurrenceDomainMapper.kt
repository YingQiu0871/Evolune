package io.github.yingqiu0871.evolune.core.presentation

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
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
            localDate = event.localDate,
            matchKey = MedicationMatchKey(
                routeKey = event.route.name,
                medicationKey = event.ester.name,
                doseAmount = event.doseMG
            )
        )
    }
