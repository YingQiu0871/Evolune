package io.github.yuninggu.evolune.utils

import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan as DomainMedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.data.MedicationPlan as LegacyMedicationPlan
import io.github.yuninggu.evolune.pk.DoseEvent
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.TimeZone
import java.util.UUID

class MedicationPlanPredictorParityTest {
    @Test
    fun `Domain and legacy predictions match for DAILY WEEKLY and CUSTOM`() {
        listOf(
            ScheduleType.DAILY to LegacyMedicationPlan.ScheduleType.DAILY,
            ScheduleType.WEEKLY to LegacyMedicationPlan.ScheduleType.WEEKLY,
            ScheduleType.CUSTOM to LegacyMedicationPlan.ScheduleType.CUSTOM
        ).forEach { (domainType, legacyType) ->
            assertEquivalent(
                domainPlan(domainType),
                legacyPlan(legacyType),
                LocalDateTime.of(2024, 1, 1, 7, 0),
                daysAhead = 10
            )
        }
    }

    @Test
    fun `Domain path preserves duplicate times boundaries and legacy ordering`() {
        val times = listOf(
            LocalTime.of(23, 59),
            LocalTime.MIDNIGHT,
            LocalTime.of(8, 30),
            LocalTime.of(8, 30)
        )
        val domain = domainPlan(ScheduleType.DAILY, times)
        val legacy = legacyPlan(LegacyMedicationPlan.ScheduleType.DAILY, times)

        val domainEvents = MedicationPlanPredictor.generateFutureEvents(
            domain,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            daysAhead = 2
        )
        val legacyEvents = MedicationPlanPredictor.generateFutureEvents(
            legacy,
            LocalDateTime.of(2024, 1, 1, 0, 0),
            daysAhead = 2
        )

        assertEquals(project(legacyEvents), project(domainEvents))
        assertEquals(7, domainEvents.size)
        val duplicateTimeH = LocalDateTime.of(2024, 1, 1, 8, 30)
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli() / 3_600_000.0
        assertEquals(2, domainEvents.count { it.timeH == duplicateTimeH })
    }

    @Test
    fun `Domain and legacy predictions use identical atZone DST gap and overlap rules`() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            listOf(
                LocalDateTime.of(2024, 3, 10, 0, 0) to LocalTime.of(2, 30),
                LocalDateTime.of(2024, 11, 3, 0, 0) to LocalTime.of(1, 30)
            ).forEach { (from, time) ->
                assertEquivalent(
                    domainPlan(ScheduleType.DAILY, listOf(time)),
                    legacyPlan(LegacyMedicationPlan.ScheduleType.DAILY, listOf(time)),
                    from,
                    daysAhead = 1
                )
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    private fun assertEquivalent(
        domain: DomainMedicationPlan,
        legacy: LegacyMedicationPlan,
        from: LocalDateTime,
        daysAhead: Int
    ) {
        val domainEvents = MedicationPlanPredictor.generateFutureEvents(domain, from, daysAhead)
        val legacyEvents = MedicationPlanPredictor.generateFutureEvents(legacy, from, daysAhead)
        assertEquals(project(legacyEvents), project(domainEvents))
    }

    private fun project(events: List<DoseEvent>): List<EventProjection> = events.map { event ->
        EventProjection(
            route = event.route,
            timeH = event.timeH,
            doseMG = event.doseMG,
            ester = event.ester,
            extras = event.extras
        )
    }

    private fun domainPlan(
        scheduleType: ScheduleType,
        times: List<LocalTime> = listOf(LocalTime.of(20, 0), LocalTime.of(8, 30))
    ): DomainMedicationPlan = DomainMedicationPlan(
        id = PLAN_ID,
        name = "Synthetic predictor plan",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.5,
        scheduleType = scheduleType,
        slots = times.mapIndexed { position, localTime ->
            ScheduledDoseSlot(
                id = UUID(1L, position.toLong()),
                planId = PLAN_ID,
                localTime = localTime,
                position = position
            )
        },
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        intervalDays = 3,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.4),
        createdAt = CREATED_AT
    )

    private fun legacyPlan(
        scheduleType: LegacyMedicationPlan.ScheduleType,
        times: List<LocalTime> = listOf(LocalTime.of(20, 0), LocalTime.of(8, 30))
    ): LegacyMedicationPlan = LegacyMedicationPlan(
        id = PLAN_ID,
        name = "Synthetic predictor plan",
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.5,
        scheduleType = scheduleType,
        timeOfDay = times,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        intervalDays = 3,
        isEnabled = true,
        extras = mapOf(DoseEvent.ExtraKey.SUBLINGUAL_THETA to 0.4),
        createdAt = CREATED_AT.toEpochMilli()
    )

    private data class EventProjection(
        val route: Route,
        val timeH: Double,
        val doseMG: Double,
        val ester: Ester,
        val extras: Map<DoseEvent.ExtraKey, Double>
    )

    private companion object {
        val PLAN_ID: UUID = UUID(0L, 801L)
        val CREATED_AT: Instant = Instant.parse("2024-01-02T03:04:05Z")
    }
}
