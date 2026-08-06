package io.github.yuninggu.evolune.reminder

import io.github.yuninggu.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

internal const val SCHEDULED_OCCURRENCES_PER_TIME = 30

internal data class MedicationPlanReminderOccurrence(
    val dateTime: LocalDateTime,
    val timePosition: Int,
    val occurrencePosition: Int
) {
    val requestOffset: Int = timePosition * 1000 + occurrencePosition
}

internal fun reminderOccurrences(
    plan: DomainMedicationPlan,
    now: LocalDateTime
): List<MedicationPlanReminderOccurrence> = reminderOccurrences(
    scheduleType = plan.scheduleType,
    daysOfWeek = plan.daysOfWeek,
    intervalDays = plan.intervalDays,
    times = plan.slots.sortedBy { it.position }.map { it.localTime },
    now = now
)

private fun reminderOccurrences(
    scheduleType: ScheduleType,
    daysOfWeek: Set<DayOfWeek>,
    intervalDays: Int,
    times: List<LocalTime>,
    now: LocalDateTime
): List<MedicationPlanReminderOccurrence> = times.flatMapIndexed { timePosition, time ->
    calculateReminderTimes(
        scheduleType = scheduleType,
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        time = time,
        now = now
    ).take(SCHEDULED_OCCURRENCES_PER_TIME)
        .mapIndexed { occurrencePosition, dateTime ->
            MedicationPlanReminderOccurrence(
                dateTime = dateTime,
                timePosition = timePosition,
                occurrencePosition = occurrencePosition
            )
        }
}

internal fun calculateReminderTimes(
    scheduleType: ScheduleType,
    daysOfWeek: Set<DayOfWeek>,
    intervalDays: Int,
    time: LocalTime,
    now: LocalDateTime
): List<LocalDateTime> {
    val today = now.toLocalDate()
    val reminderTimes = mutableListOf<LocalDateTime>()
    when (scheduleType) {
        ScheduleType.DAILY -> {
            for (dayOffset in 0 until 30) {
                val reminderTime = LocalDateTime.of(today.plusDays(dayOffset.toLong()), time)
                if (reminderTime.plusHours(1).isAfter(now)) {
                    reminderTimes += reminderTime
                }
            }
        }
        ScheduleType.WEEKLY -> {
            for (dayOffset in 0 until 60) {
                val reminderDate = today.plusDays(dayOffset.toLong())
                if (reminderDate.dayOfWeek in daysOfWeek) {
                    val reminderTime = LocalDateTime.of(reminderDate, time)
                    if (reminderTime.plusHours(1).isAfter(now)) {
                        reminderTimes += reminderTime
                    }
                }
            }
        }
        ScheduleType.CUSTOM -> {
            var dayOffset = 0
            while (reminderTimes.size < SCHEDULED_OCCURRENCES_PER_TIME) {
                val reminderTime = LocalDateTime.of(today.plusDays(dayOffset.toLong()), time)
                if (reminderTime.plusHours(1).isAfter(now)) {
                    reminderTimes += reminderTime
                }
                dayOffset += intervalDays
            }
        }
    }
    return reminderTimes.sorted()
}

internal fun reminderRequestCode(
    planId: UUID,
    timeIndex: Int,
    occurrenceIndex: Int
): Int = planId.hashCode() + timeIndex * 1000 + occurrenceIndex
