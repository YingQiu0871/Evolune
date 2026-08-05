package io.github.yuninggu.evolune.reminder

import io.github.yuninggu.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.data.MedicationPlan as LegacyMedicationPlan
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class MedicationPlanReminderScheduleTest {
    @Test
    fun `Domain and legacy reminder schedules match for every schedule type`() {
        listOf(
            ScheduleType.DAILY to LegacyMedicationPlan.ScheduleType.DAILY,
            ScheduleType.WEEKLY to LegacyMedicationPlan.ScheduleType.WEEKLY,
            ScheduleType.CUSTOM to LegacyMedicationPlan.ScheduleType.CUSTOM
        ).forEach { (domainType, legacyType) ->
            val times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 30))
            val domain = domainPlan(domainType, times)
            val legacy = legacyPlan(legacyType, times)

            assertEquals(
                reminderOccurrences(legacy, NOW),
                reminderOccurrences(domain, NOW)
            )
        }
    }

    @Test
    fun `duplicate times preserve source order and scheduling count`() {
        val times = listOf(
            LocalTime.of(20, 0),
            LocalTime.of(8, 30),
            LocalTime.of(8, 30)
        )

        val occurrences = reminderOccurrences(
            domainPlan(ScheduleType.DAILY, times),
            NOW
        )

        assertEquals(90, occurrences.size)
        assertTrue(occurrences.take(30).all { it.timePosition == 0 })
        assertTrue(occurrences.drop(30).take(30).all { it.timePosition == 1 })
        assertTrue(occurrences.drop(60).all { it.timePosition == 2 })
        assertEquals(LocalTime.of(20, 0), occurrences[0].dateTime.toLocalTime())
        assertEquals(LocalTime.of(8, 30), occurrences[30].dateTime.toLocalTime())
        assertEquals(LocalTime.of(8, 30), occurrences[60].dateTime.toLocalTime())
    }

    @Test
    fun `request code offsets remain compatible with legacy alarm keys`() {
        val occurrences = reminderOccurrences(
            domainPlan(ScheduleType.DAILY, listOf(LocalTime.of(8, 30), LocalTime.of(20, 0))),
            NOW
        )
        val firstOfSecondTime = occurrences.first { it.timePosition == 1 }

        assertEquals(1000, firstOfSecondTime.requestOffset)
        assertEquals(
            PLAN_ID.hashCode() + firstOfSecondTime.requestOffset,
            reminderRequestCode(
                PLAN_ID,
                firstOfSecondTime.timePosition,
                firstOfSecondTime.occurrencePosition
            )
        )
    }

    @Test
    fun `one hour reminder window boundary remains unchanged`() {
        val atExclusiveBoundary = calculateReminderTimes(
            scheduleType = ScheduleType.DAILY,
            daysOfWeek = emptySet(),
            intervalDays = 1,
            time = LocalTime.of(7, 0),
            now = NOW
        )
        val insideWindow = calculateReminderTimes(
            scheduleType = ScheduleType.DAILY,
            daysOfWeek = emptySet(),
            intervalDays = 1,
            time = LocalTime.of(7, 1),
            now = NOW
        )

        assertEquals(NOW.toLocalDate().plusDays(1), atExclusiveBoundary.first().toLocalDate())
        assertEquals(NOW.toLocalDate(), insideWindow.first().toLocalDate())
    }

    @Test
    fun `Domain and legacy local schedules resolve equally across DST gap and overlap`() {
        val zoneId = ZoneId.of("America/New_York")
        listOf(
            LocalDateTime.of(2024, 3, 10, 0, 0) to LocalTime.of(2, 30),
            LocalDateTime.of(2024, 11, 3, 0, 0) to LocalTime.of(1, 30)
        ).forEach { (now, time) ->
            val domain = reminderOccurrences(
                domainPlan(ScheduleType.DAILY, listOf(time)),
                now
            ).first()
            val legacy = reminderOccurrences(
                legacyPlan(LegacyMedicationPlan.ScheduleType.DAILY, listOf(time)),
                now
            ).first()

            assertEquals(
                legacy.dateTime.atZone(zoneId).toInstant(),
                domain.dateTime.atZone(zoneId).toInstant()
            )
        }
    }

    private fun domainPlan(
        scheduleType: ScheduleType,
        times: List<LocalTime>
    ): DomainMedicationPlan = DomainMedicationPlan(
        id = PLAN_ID,
        name = "Synthetic reminder plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = scheduleType,
        slots = times.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = UUID(1L, position.toLong()),
                planId = PLAN_ID,
                localTime = time,
                position = position
            )
        },
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        intervalDays = 3,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = Instant.parse("2024-01-02T03:04:05Z")
    )

    private fun legacyPlan(
        scheduleType: LegacyMedicationPlan.ScheduleType,
        times: List<LocalTime>
    ): LegacyMedicationPlan = LegacyMedicationPlan(
        id = PLAN_ID,
        name = "Synthetic reminder plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = scheduleType,
        timeOfDay = times,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        intervalDays = 3,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = Instant.parse("2024-01-02T03:04:05Z").toEpochMilli()
    )

    private companion object {
        val PLAN_ID: UUID = UUID(0L, 701L)
        val NOW: LocalDateTime = LocalDateTime.of(2024, 1, 1, 8, 0)
    }
}
