package io.github.yingqiu0871.evolune.ui.screens

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class HomeScheduleBoundaryTest {
    @Test
    fun nextForkPointMovesToTheFollowingDayAfterMidnightBoundary() {
        val zone = ZoneId.systemDefault()
        val localNow = LocalDateTime.of(2026, 9, 4, 23, 59)
        val currentTimeH = localNow.atZone(zone).toInstant().toEpochMilli() / MILLIS_PER_HOUR
        val plan = planWithSlot(LocalTime.of(0, 5))

        val nextForkPoint = nextPlanForkPointTimeH(listOf(plan), currentTimeH)
        assertNotNull(nextForkPoint)
        assertTrue(nextForkPoint!! > currentTimeH)

        val nextLocal = Instant.ofEpochMilli((nextForkPoint * MILLIS_PER_HOUR).toLong())
            .atZone(zone)
            .toLocalDateTime()
        assertEquals(LocalDate.of(2026, 9, 5), nextLocal.toLocalDate())
        assertEquals(LocalTime.of(0, 5), nextLocal.toLocalTime())
    }

    private fun planWithSlot(time: LocalTime): MedicationPlan {
        val planId = UUID.fromString("95000000-0000-0000-0000-000000000001")
        val slotId = when (val result = ScheduledDoseSlotId.generate(planId, 0, time)) {
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Success -> result.id
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Failure ->
                error("failed to generate schedule boundary slot: ${result.error}")
        }
        return MedicationPlan(
            id = planId,
            name = "schedule boundary fixture",
            route = Route.ORAL,
            ester = Ester.E2,
            doseMG = 1.0,
            scheduleType = ScheduleType.DAILY,
            slots = listOf(ScheduledDoseSlot(slotId, planId, time, 0)),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = true,
            extras = emptyMap(),
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
    }
}
