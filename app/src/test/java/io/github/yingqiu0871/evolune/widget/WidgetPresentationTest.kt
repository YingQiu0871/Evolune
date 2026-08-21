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
        assertEquals(2, state.todayItems.size)
        assertEquals(WidgetDailyProgress(0, 2), state.dailyProgress)
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
        assertEquals(WidgetDailyProgress(1, 1), state.dailyProgress)
        assertEquals(now.plusSeconds(86_400L - 3_600L), state.nextMeaningfulBoundary)
    }

    @Test
    fun `late Widget event preserves actual time and completes today's slot by local date`() {
        val plan = plan(UUID(0L, 40L), UUID(1L, 40L), enabled = true)
        val clickedAt = Instant.parse("2027-01-15T14:00:00Z")
        val recorded = DoseEvent(
            id = UUID(2L, 40L),
            route = Route.ORAL,
            occurredAt = clickedAt,
            localDate = clickedAt.atZone(ZoneOffset.UTC).toLocalDate(),
            doseMG = plan.doseMG,
            ester = plan.ester,
            slotId = plan.slots.single().id,
            source = DoseEventSource.WIDGET,
            status = DoseEventStatus.RECORDED
        )

        val state = mapper.map(listOf(plan), listOf(recorded), clickedAt, ZoneOffset.UTC)
            as WidgetPresentationState.Timeline

        assertEquals(clickedAt, recorded.occurredAt)
        assertEquals(WidgetDailyProgress(1, 1), state.dailyProgress)
        assertEquals(MedicationOccurrenceStatus.RECORDED, state.todayItems.single().status)
    }

    @Test
    fun `one plan keeps three same-day slot occurrences and exact sibling recording`() {
        val testNow = Instant.parse("2027-01-15T18:30:00Z")
        val plan = syntheticPlan(
            id = UUID(0L, 41L),
            slots = listOf(LocalTime.of(9, 0), LocalTime.of(17, 0), LocalTime.of(22, 0))
        ).copy(name = "1", createdAt = Instant.parse("2027-01-15T18:00:00Z"))
        val localDate = testNow.atZone(ZoneOffset.UTC).toLocalDate()

        fun state(events: List<DoseEvent>) = mapper.map(
            listOf(plan),
            events,
            testNow,
            ZoneOffset.UTC
        ) as WidgetPresentationState.Timeline

        val initial = state(emptyList())
        assertEquals(3, initial.todayItems.size)
        assertEquals(1, initial.todayItems.map { it.occurrence.planId }.distinct().size)
        assertEquals(3, initial.todayItems.map { it.occurrence.slotId }.distinct().size)
        assertEquals(3, initial.todayItems.map { it.occurrence.id }.distinct().size)
        assertEquals(WidgetDailyProgress(0, 3), initial.dailyProgress)

        val recorded09 = DoseEvent(
            id = UUID(2L, 41L),
            route = plan.route,
            occurredAt = Instant.parse("2027-01-15T10:30:00Z"),
            localDate = localDate,
            doseMG = plan.doseMG,
            ester = plan.ester,
            slotId = plan.slots[0].id,
            source = DoseEventSource.WIDGET,
            status = DoseEventStatus.RECORDED
        )
        val after09 = state(listOf(recorded09))
        assertEquals(WidgetDailyProgress(1, 3), after09.dailyProgress)
        assertEquals(
            listOf(
                MedicationOccurrenceStatus.RECORDED,
                MedicationOccurrenceStatus.PAST_UNRECORDED,
                MedicationOccurrenceStatus.UPCOMING
            ),
            after09.todayItems.map { it.status }
        )

        val recorded17 = recorded09.copy(
            id = UUID(2L, 42L),
            occurredAt = Instant.parse("2027-01-15T18:15:00Z"),
            slotId = plan.slots[1].id
        )
        val after17 = state(listOf(recorded09, recorded17))
        assertEquals(WidgetDailyProgress(2, 3), after17.dailyProgress)
        assertEquals(
            listOf(
                MedicationOccurrenceStatus.RECORDED,
                MedicationOccurrenceStatus.RECORDED,
                MedicationOccurrenceStatus.UPCOMING
            ),
            after17.todayItems.map { it.status }
        )
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
