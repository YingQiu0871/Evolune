package io.github.yingqiu0871.evolune.experience

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class OccurrenceGenerationWindow(
    val startInclusive: Instant,
    val endExclusive: Instant
) {
    init {
        require(startInclusive < endExclusive) { "occurrence window must not be empty" }
        require(Duration.between(startInclusive, endExclusive) <= MAX_WINDOW_DURATION) {
            "occurrence window exceeds $MAX_WINDOW_DAYS days"
        }
    }

    companion object {
        const val MAX_WINDOW_DAYS = 3_660L
        private val MAX_WINDOW_DURATION = Duration.ofDays(MAX_WINDOW_DAYS)
    }
}

object MedicationOccurrenceGenerator {
    private const val MAX_GENERATED_OCCURRENCES = 100_000

    fun generate(
        schedules: List<MedicationSchedule>,
        window: OccurrenceGenerationWindow,
        zoneId: ZoneId
    ): List<MedicationOccurrence> {
        val firstDate = window.startInclusive.atZone(zoneId).toLocalDate()
        val lastDate = window.endExclusive.minusNanos(1).atZone(zoneId).toLocalDate()
        val occurrences = mutableListOf<MedicationOccurrence>()

        schedules.asSequence()
            .filter { it.enabled }
            .forEach { schedule ->
                expandSchedule(
                    schedule = schedule,
                    firstDate = firstDate,
                    lastDate = lastDate,
                    window = window,
                    zoneId = zoneId,
                    destination = occurrences
                )
            }

        return occurrences.sortedWith(OCCURRENCE_ORDER)
    }

    private fun expandSchedule(
        schedule: MedicationSchedule,
        firstDate: LocalDate,
        lastDate: LocalDate,
        window: OccurrenceGenerationWindow,
        zoneId: ZoneId,
        destination: MutableList<MedicationOccurrence>
    ) {
        val anchorDate = schedule.createdAt.atZone(zoneId).toLocalDate()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            if (date.isOnSchedule(schedule, anchorDate)) {
                schedule.slots.forEach { slot ->
                    val requestedLocal = LocalDateTime.of(date, slot.localTime)
                    // java.time resolves gaps forward and chooses the earlier offset for overlaps.
                    val scheduledAt = requestedLocal.atZone(zoneId).toInstant()
                    if (
                        scheduledAt >= schedule.createdAt &&
                        scheduledAt >= window.startInclusive &&
                        scheduledAt < window.endExclusive
                    ) {
                        check(destination.size < MAX_GENERATED_OCCURRENCES) {
                            "occurrence result exceeds $MAX_GENERATED_OCCURRENCES items"
                        }
                        destination += MedicationOccurrence(
                            id = MedicationOccurrenceIdentity.derive(
                                schedule.planId,
                                slot.id,
                                requestedLocal.toLocalDate()
                            ),
                            planId = schedule.planId,
                            slotId = slot.id,
                            slotPosition = slot.position,
                            presentation = schedule.presentation,
                            scheduledAt = scheduledAt,
                            scheduledLocalDateTime = requestedLocal,
                            zoneId = zoneId
                        )
                    }
                }
            }
            if (date == lastDate) break
            date = date.plusDays(1)
        }
    }

    private fun LocalDate.isOnSchedule(
        schedule: MedicationSchedule,
        anchorDate: LocalDate
    ): Boolean {
        if (isBefore(anchorDate)) return false
        return when (schedule.scheduleType) {
            MedicationScheduleType.DAILY -> true
            MedicationScheduleType.WEEKLY -> dayOfWeek in schedule.daysOfWeek
            MedicationScheduleType.CUSTOM ->
                ChronoUnit.DAYS.between(anchorDate, this) % schedule.intervalDays == 0L
        }
    }
}

internal val OCCURRENCE_ORDER = compareBy<MedicationOccurrence>(
    { it.scheduledAt },
    { it.planId.toString() },
    { it.slotPosition },
    { it.slotId.toString() },
    { it.id.value.toString() }
)
