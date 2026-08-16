package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceStatus
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class WidgetPresentationTest {
    private val mapper = WidgetPresentationMapper()
    private val now = Instant.parse("2027-01-15T08:30:00Z")

    @Test
    fun `no enabled plans is distinct from an enabled plan without an occurrence`() {
        assertEquals(
            WidgetPresentationState.NoEnabledPlans,
            mapper.map(emptyList(), emptyList(), now, ZoneOffset.UTC)
        )

        val withoutSlots = plan(UUID(0L, 1L), UUID(1L, 1L), enabled = true)
            .copy(slots = emptyList())
        val state = mapper.map(listOf(withoutSlots), emptyList(), now, ZoneOffset.UTC)

        assertTrue(state is WidgetPresentationState.NoUpcomingOccurrence)
    }

    @Test
    fun `simultaneous enabled occurrences remain current and disabled plans are excluded`() {
        val first = plan(UUID(0L, 1L), UUID(1L, 1L), enabled = true)
        val second = plan(UUID(0L, 2L), UUID(1L, 2L), enabled = true)
        val disabled = plan(UUID(0L, 3L), UUID(1L, 3L), enabled = false)

        val state = mapper.map(listOf(first, disabled, second), emptyList(), now, ZoneOffset.UTC)
            as WidgetPresentationState.Timeline

        assertEquals(listOf(first.id, second.id), state.visiblePlans.map { it.planId })
        assertEquals(2, state.window.current.size)
        assertTrue(state.window.current.all { it.occurrence.scheduledAt == now })
    }

    @Test
    fun `recorded event uses E1 matching and next boundary is the due window close`() {
        val plan = plan(UUID(0L, 4L), UUID(1L, 4L), enabled = true)
        val recorded = DoseEvent(
            id = UUID(2L, 4L),
            route = Route.ORAL,
            occurredAt = now,
            doseMG = plan.doseMG,
            ester = plan.ester,
            slotId = plan.slots.single().id,
            source = DoseEventSource.MANUAL,
            status = DoseEventStatus.RECORDED
        )

        val state = mapper.map(listOf(plan), listOf(recorded), now, ZoneOffset.UTC)
            as WidgetPresentationState.Timeline

        assertTrue(
            state.window.previous.any {
                it.status == MedicationOccurrenceStatus.RECORDED &&
                    it.recordedEventId == recorded.id
            }
        )
        assertEquals(now.plusSeconds(86_400L - 3_600L), state.nextMeaningfulBoundary)
    }

    @Test
    fun `logical occurrence identity survives timezone reconstruction and day rollover`() {
        val plan = plan(UUID(0L, 5L), UUID(1L, 5L), enabled = true)
        val shanghai = mapper.map(
            listOf(plan),
            emptyList(),
            Instant.parse("2027-01-15T00:30:00Z"),
            ZoneId.of("Asia/Shanghai")
        ) as WidgetPresentationState.Timeline
        val paris = mapper.map(
            listOf(plan),
            emptyList(),
            Instant.parse("2027-01-15T07:30:00Z"),
            ZoneId.of("Europe/Paris")
        ) as WidgetPresentationState.Timeline

        assertEquals(
            shanghai.window.current.single().occurrence.id,
            paris.window.current.single().occurrence.id
        )
    }

    @Test
    fun `coordinator retains accepted update reasons`() = runBlocking {
        val reasons = mutableListOf<WidgetUpdateReason>()
        val coordinator = ContractWidgetUpdateCoordinator { reason -> reasons += reason }

        coordinator.request(WidgetUpdateReason.PLAN_CHANGED)
        coordinator.request(WidgetUpdateReason.ACCEPTED_WEAR_DOSE_EVENT)

        assertEquals(
            listOf(
                WidgetUpdateReason.PLAN_CHANGED,
                WidgetUpdateReason.ACCEPTED_WEAR_DOSE_EVENT
            ),
            reasons
        )
    }

    private fun plan(id: UUID, slotId: UUID, enabled: Boolean) = syntheticPlan(
        id = id,
        enabled = enabled,
        slots = listOf(LocalTime.of(8, 30))
    ).copy(
        slots = listOf(
            ScheduledDoseSlot(
                id = slotId,
                planId = id,
                localTime = LocalTime.of(8, 30),
                position = 0
            )
        )
    )
}
