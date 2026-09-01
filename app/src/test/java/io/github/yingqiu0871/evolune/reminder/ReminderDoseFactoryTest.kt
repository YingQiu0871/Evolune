package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ReminderDoseFactoryTest {

    private val plan = syntheticPlan(
        id = UUID.fromString("5f99f21a-4fc4-4457-8d2b-967b25c77541")
    )
    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val firstOccurrence = occurrenceOn(LocalDate.of(2026, 1, 5))
    private val secondOccurrence = occurrenceOn(LocalDate.of(2026, 1, 6))

    @Test
    fun `same occurrence creates the same id`() {
        val first = createReminderDoseEvent(
            plan,
            firstOccurrence,
            firstOccurrence.scheduledAt.toEpochMilli() + 1_000L,
            zoneId
        )
        val second = createReminderDoseEvent(
            plan,
            firstOccurrence,
            firstOccurrence.scheduledAt.toEpochMilli() + 5_000L,
            zoneId
        )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `different occurrences create different ids`() {
        val first = createReminderDoseEvent(
            plan,
            firstOccurrence,
            firstOccurrence.scheduledAt.toEpochMilli(),
            zoneId
        )
        val second = createReminderDoseEvent(
            plan,
            secondOccurrence,
            secondOccurrence.scheduledAt.toEpochMilli(),
            zoneId
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `confirmed record copies plan details and confirmation time`() {
        val recordedAt = firstOccurrence.scheduledAt.toEpochMilli() + 123_000L
        val event = createReminderDoseEvent(
            plan,
            firstOccurrence,
            recordedAt,
            zoneId
        )

        assertEquals(plan.route, event.route)
        assertEquals(plan.ester, event.ester)
        assertEquals(plan.doseMG, event.doseMG, 0.0)
        assertEquals(plan.extras, event.extras)
        assertEquals(Instant.ofEpochMilli(recordedAt), event.occurredAt)
        assertEquals(zoneId, event.zoneId)
        assertEquals(firstOccurrence.scheduledLocalDateTime.toLocalDate(), event.localDate)
        assertEquals(DoseEventSource.REMINDER, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
        assertEquals(firstOccurrence.slotId, event.slotId)
    }

    private fun occurrenceOn(date: LocalDate): MedicationOccurrence =
        MedicationOccurrenceGenerator.generate(
            schedules = listOf(plan.toMedicationSchedule()),
            window = OccurrenceGenerationWindow(
                startInclusive = date.atStartOfDay(zoneId).toInstant(),
                endExclusive = date.plusDays(1L).atStartOfDay(zoneId).toInstant()
            ),
            zoneId = zoneId
        ).single()
}
