package io.github.yingqiu0871.evolune.wear

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.reminder.reminderOccurrences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

class WearSkipNotificationPolicyTest {
    private val planId = UUID(0L, 91L)
    private val slotId = UUID(0L, 92L)
    private val now = Instant.parse("2026-09-09T08:00:00Z")
    private val plan = MedicationPlan(
        id = planId,
        name = "E2",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        slots = listOf(ScheduledDoseSlot(slotId, planId, LocalTime.of(9, 0), 0)),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = now
    )

    @Test
    fun `valid occurrence resolves the exact alarm request offset`() {
        val occurrence = reminderOccurrences(plan, now.atOffset(ZoneOffset.UTC).toLocalDateTime()).first()
        val scheduledAt = occurrence.dateTime.toInstant(ZoneOffset.UTC)
        val command = command(scheduledAt, planId.hashCode() + occurrence.requestOffset)

        assertEquals(
            occurrence.requestOffset,
            validateWearSkipNotification(plan, command, now, ZoneOffset.UTC)?.requestOffset
        )
    }

    @Test
    fun `stale identity and forged notification id are rejected`() {
        val scheduledAt = Instant.parse("2026-09-09T09:00:00Z")
        assertNull(validateWearSkipNotification(plan, command(scheduledAt, 123), now, ZoneOffset.UTC))
        assertNull(
            validateWearSkipNotification(
                plan,
                command(scheduledAt, planId.hashCode()).copy(slotId = UUID(0L, 999L)),
                now,
                ZoneOffset.UTC
            )
        )
        assertNull(
            validateWearSkipNotification(
                plan,
                command(scheduledAt, planId.hashCode()).copy(occurrenceId = UUID(0L, 999L)),
                now,
                ZoneOffset.UTC
            )
        )
    }

    private fun command(scheduledAt: Instant, notificationId: Int) = WearSkipNotificationCommand(
        occurrenceId = MedicationOccurrenceIdentity.derive(
            planId = plan.id,
            slotId = plan.slots.single().id,
            scheduledLocalDate = scheduledAt.atZone(ZoneOffset.UTC).toLocalDate()
        ).value,
        planId = planId,
        slotId = slotId,
        scheduledAt = scheduledAt,
        notificationId = notificationId
    )
}
