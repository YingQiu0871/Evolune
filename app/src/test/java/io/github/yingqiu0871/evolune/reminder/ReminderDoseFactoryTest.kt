package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ReminderDoseFactoryTest {

    private val plan = syntheticPlan(
        id = UUID.fromString("5f99f21a-4fc4-4457-8d2b-967b25c77541")
    )
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `same occurrence creates the same id`() {
        val scheduledAt = 1_800_000_000_000L

        val first = createReminderDoseEvent(plan, scheduledAt + 1_000L, scheduledAt, zoneId)
        val second = createReminderDoseEvent(plan, scheduledAt + 5_000L, scheduledAt, zoneId)

        assertEquals(first.id, second.id)
    }

    @Test
    fun `different occurrences create different ids`() {
        val first = createReminderDoseEvent(
            plan, 1_800_000_000_000L, 1_800_000_000_000L, zoneId
        )
        val second = createReminderDoseEvent(
            plan, 1_800_086_400_000L, 1_800_086_400_000L, zoneId
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `confirmed record copies plan details and confirmation time`() {
        val recordedAt = 1_800_000_123_000L
        val event = createReminderDoseEvent(
            plan, recordedAt, 1_800_000_000_000L, zoneId
        )

        assertEquals(plan.route, event.route)
        assertEquals(plan.ester, event.ester)
        assertEquals(plan.doseMG, event.doseMG, 0.0)
        assertEquals(plan.extras, event.extras)
        assertEquals(Instant.ofEpochMilli(recordedAt), event.occurredAt)
        assertEquals(zoneId, event.zoneId)
        assertEquals(event.occurredAt.atZone(zoneId).toLocalDate(), event.localDate)
        assertEquals(DoseEventSource.REMINDER, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
        assertEquals(null, event.slotId)
    }
}
