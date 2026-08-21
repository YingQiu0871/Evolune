package io.github.yingqiu0871.evolune.core.presentation

import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.experience.MedicationScheduleType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MedicationOccurrenceDomainMapperTest {
    @Test
    fun `domain plan maps to immutable normalized schedule projection`() {
        val plan = syntheticPlan()
        val mapped = plan.toMedicationSchedule()

        assertEquals(plan.id, mapped.planId)
        assertEquals(plan.name, mapped.presentation.planName)
        assertEquals(plan.route.name, mapped.presentation.matchKey.routeKey)
        assertEquals(plan.ester.name, mapped.presentation.matchKey.medicationKey)
        assertEquals(plan.doseMG, mapped.presentation.matchKey.doseAmount, 0.0)
        assertEquals(MedicationScheduleType.DAILY, mapped.scheduleType)
        assertEquals(plan.slots.map { it.id }, mapped.slots.map { it.id })
        assertEquals(plan.createdAt, mapped.createdAt)
        assertEquals(plan.isEnabled, mapped.enabled)
    }

    @Test
    fun `accepted domain event maps without changing event identity`() {
        val event = DoseEvent(
            id = UUID(0L, 901L),
            route = Route.ORAL,
            occurredAt = Instant.parse("2025-01-02T10:00:00Z"),
            localDate = LocalDate.parse("2025-01-02"),
            doseMG = 2.0,
            ester = Ester.E2,
            source = DoseEventSource.WEAR
        )
        val mapped = event.toRecordedMedicationEvent()!!

        assertEquals(event.id, mapped.eventId)
        assertEquals(event.occurredAt, mapped.occurredAt)
        assertEquals(event.slotId, mapped.slotId)
        assertEquals(event.localDate, mapped.localDate)
        assertEquals(event.route.name, mapped.matchKey.routeKey)
        assertNull(event.slotId)
    }
}
