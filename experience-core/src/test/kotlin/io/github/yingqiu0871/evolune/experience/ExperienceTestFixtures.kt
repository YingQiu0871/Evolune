package io.github.yingqiu0871.evolune.experience

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

internal val TEST_ZONE: ZoneId = ZoneId.of("UTC")

internal fun schedule(
    number: Long = 1L,
    name: String = "Plan $number",
    scheduleType: MedicationScheduleType = MedicationScheduleType.DAILY,
    times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    intervalDays: Int = 1,
    enabled: Boolean = true,
    createdAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
    routeKey: String = "ORAL",
    medicationKey: String = "E2",
    doseAmount: Double = 2.0
): MedicationSchedule {
    val planId = UUID(0L, number)
    return MedicationSchedule(
        planId = planId,
        presentation = MedicationPresentation(
            planName = name,
            matchKey = MedicationMatchKey(routeKey, medicationKey, doseAmount)
        ),
        scheduleType = scheduleType,
        slots = times.mapIndexed { position, time ->
            MedicationScheduleSlot(
                id = UUID(number, position.toLong() + 1L),
                localTime = time,
                position = position
            )
        },
        daysOfWeek = daysOfWeek,
        intervalDays = intervalDays,
        enabled = enabled,
        createdAt = createdAt
    )
}

internal fun occurrences(
    schedules: List<MedicationSchedule>,
    start: String,
    end: String,
    zoneId: ZoneId = TEST_ZONE
): List<MedicationOccurrence> = MedicationOccurrenceGenerator.generate(
    schedules = schedules,
    window = OccurrenceGenerationWindow(Instant.parse(start), Instant.parse(end)),
    zoneId = zoneId
)
