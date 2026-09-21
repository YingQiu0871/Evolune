package io.github.yingqiu0871.evolune.viewmodel

import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ScheduleBoundaryTrackerTest {
    @Test
    fun `five point crossing consumes boundary once at exact deadline`() {
        val tracker = ScheduleBoundaryTracker()
        val identity = identity()
        val boundary = 100.0
        val followingBoundary = 124.0
        val observations = listOf(
            boundary - 2.0 / 3600.0,
            boundary - 1.0 / 3600.0,
            boundary,
            boundary + 1.0 / 3600.0,
            boundary + 2.0 / 3600.0
        ).map { nowTimeH ->
            tracker.observe(identity, nowTimeH) {
                if (nowTimeH < boundary) boundary else followingBoundary
            }
        }

        assertEquals(boundary, observations[0].nextDeadlineTimeH!!, 0.0)
        assertTrue(!observations[0].crossed)
        assertTrue(!observations[1].crossed)
        assertTrue(observations[2].crossed)
        assertEquals(followingBoundary, observations[2].nextDeadlineTimeH!!, 0.0)
        assertTrue(!observations[3].crossed)
        assertTrue(!observations[4].crossed)
    }

    @Test
    fun `close boundaries are consumed independently without duplicate refresh`() {
        val tracker = ScheduleBoundaryTracker()
        val identity = identity()
        val firstBoundary = 8.5
        val secondBoundary = 8.5 + 5.0 / 60.0
        val thirdBoundary = 9.0

        val before = tracker.observe(identity, firstBoundary - 1.0 / 3600.0) {
            firstBoundary
        }
        val firstCrossing = tracker.observe(identity, firstBoundary) {
            secondBoundary
        }
        val between = tracker.observe(identity, firstBoundary + 1.0 / 3600.0) {
            secondBoundary
        }
        val secondCrossing = tracker.observe(identity, secondBoundary) {
            thirdBoundary
        }

        assertTrue(!before.crossed)
        assertTrue(firstCrossing.crossed)
        assertEquals(secondBoundary, firstCrossing.nextDeadlineTimeH!!, 0.0)
        assertTrue(!between.crossed)
        assertTrue(secondCrossing.crossed)
        assertEquals(thirdBoundary, secondCrossing.nextDeadlineTimeH!!, 0.0)
    }

    @Test
    fun `long jump refreshes once and stores first future boundary after now`() {
        val tracker = ScheduleBoundaryTracker()
        val identity = identity()
        val oldBoundary = 100.0
        val firstFutureAfterJump = 200.0

        tracker.observe(identity, oldBoundary - 1.0) { oldBoundary }
        val jumped = tracker.observe(identity, 180.0) { firstFutureAfterJump }
        val afterJump = tracker.observe(identity, 181.0) { firstFutureAfterJump }

        assertTrue(jumped.crossed)
        assertEquals(firstFutureAfterJump, jumped.nextDeadlineTimeH!!, 0.0)
        assertTrue(!afterJump.crossed)
    }

    @Test
    fun `plan change invalidates old boundary without synthetic crossing`() {
        val tracker = ScheduleBoundaryTracker()
        val oldIdentity = identity(plan = plan("old"))
        val newIdentity = identity(plan = plan("new"))

        tracker.observe(oldIdentity, 99.0) { 100.0 }
        val changed = tracker.observe(newIdentity, 101.0) { 200.0 }

        assertTrue(!changed.crossed)
        assertEquals(200.0, changed.nextDeadlineTimeH!!, 0.0)
    }

    @Test
    fun `empty plans clear pending boundary and later plans initialize fresh`() {
        val tracker = ScheduleBoundaryTracker()
        val planIdentity = identity(plan = plan("enabled"))
        val emptyIdentity = identity()

        tracker.observe(planIdentity, 99.0) { 100.0 }
        val empty = tracker.observe(emptyIdentity, 101.0) { null }
        val restored = tracker.observe(planIdentity, 101.0) { 200.0 }

        assertTrue(!empty.crossed)
        assertEquals(null, empty.nextDeadlineTimeH)
        assertTrue(!restored.crossed)
        assertEquals(200.0, restored.nextDeadlineTimeH!!, 0.0)
    }

    @Test
    fun `timezone change invalidates boundary without crossing old zone`() {
        val tracker = ScheduleBoundaryTracker()
        val utcIdentity = identity(zoneId = ZoneOffset.UTC)
        val parisIdentity = identity(zoneId = ZoneId.of("Europe/Paris"))

        tracker.observe(utcIdentity, 99.0) { 100.0 }
        val changed = tracker.observe(parisIdentity, 101.0) { 200.0 }

        assertTrue(!changed.crossed)
        assertEquals(200.0, changed.nextDeadlineTimeH!!, 0.0)
    }

    @Test
    fun `schedule provider remains the chart source on every observation`() {
        val tracker = ScheduleBoundaryTracker()
        val identity = identity()
        var providerCalls = 0

        repeat(3) { index ->
            tracker.observe(identity, 99.0 + index / 10.0) {
                providerCalls += 1
                100.0
            }
        }

        assertEquals(3, providerCalls)
    }

    private fun identity(
        plan: MedicationPlan? = null,
        zoneId: ZoneId = ZoneOffset.UTC
    ): ScheduleBoundaryIdentity = ScheduleBoundaryIdentity(
        enabledPlans = plan?.let(::listOf) ?: emptyList(),
        zoneId = zoneId
    )

    private fun plan(name: String): MedicationPlan {
        val planId = UUID.nameUUIDFromBytes(name.toByteArray())
        val slotTime = LocalTime.of(8, 30)
        val slotId = when (val result = ScheduledDoseSlotId.generate(planId, 0, slotTime)) {
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Success -> result.id
            is io.github.yingqiu0871.evolune.core.model.SlotIdResult.Failure ->
                error("failed to generate schedule boundary slot: ${result.error}")
        }
        return MedicationPlan(
            id = planId,
            name = name,
            route = Route.ORAL,
            ester = Ester.E2,
            doseMG = 1.0,
            scheduleType = ScheduleType.DAILY,
            slots = listOf(ScheduledDoseSlot(slotId, planId, slotTime, 0)),
            daysOfWeek = emptySet(),
            intervalDays = 1,
            isEnabled = true,
            extras = emptyMap(),
            createdAt = Instant.parse("2026-01-01T00:00:00Z")
        )
    }
}
