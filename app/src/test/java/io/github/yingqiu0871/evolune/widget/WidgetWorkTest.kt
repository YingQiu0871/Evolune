package io.github.yingqiu0871.evolune.widget

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.application.widgetOccurrenceActionEventId
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.pk.DoseEvent as PkDoseEvent
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SimulationEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class WidgetWorkTest {
    private val plan = syntheticPlan()
    private val now = Instant.parse("2027-01-15T08:30:00.789Z")
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `snapshot uses enabled Domain plans and preserves PK concentration`() = runBlocking {
        val included = event(UUID(0L, 701L), now.minusSeconds(3_600L), Route.ORAL)
        val future = event(UUID(0L, 702L), now.plusMillis(1L), Route.ORAL)
        val antiAndrogen = event(
            UUID(0L, 703L),
            now.minusSeconds(1_800L),
            Route.ANTIANDROGEN
        )
        val disabled = syntheticPlan(UUID(0L, 704L), enabled = false)
        val third = syntheticPlan(UUID(0L, 705L))
        val loader = WidgetSnapshotLoader(
            medicationPlans = FakeMedicationPlanRepository(
                listOf(plan, disabled, third, syntheticPlan(UUID(0L, 706L)))
            ),
            doseEvents = FakeDoseEventRepository().apply {
                pkEvents = listOf(included, future, antiAndrogen)
            },
            bodyWeight = { 60.0 },
            clock = Clock.fixed(now, ZoneOffset.UTC)
        )

        val snapshot = loader.load()
        val nowH = now.toEpochMilli() / 3_600_000.0
        val expected = SimulationEngine(
            events = listOf(
                PkDoseEvent(
                    id = included.id,
                    route = included.route,
                    timeH = included.occurredAt.toEpochMilli() / 3_600_000.0,
                    doseMG = included.doseMG,
                    ester = included.ester
                )
            ),
            bodyWeightKG = 60.0,
            startTimeH = nowH - 0.01,
            endTimeH = nowH,
            numberOfSteps = 2
        ).run().concPGmL.last()

        assertEquals(
            listOf(plan.id, third.id),
            snapshot.presentation.visiblePlans.map { it.planId }
        )
        assertEquals(expected, snapshot.concentration!!, 1e-6)
    }

    @Test
    fun `empty event selection has no concentration`() = runBlocking {
        val snapshot = WidgetSnapshotLoader(
            medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
            doseEvents = FakeDoseEventRepository(),
            bodyWeight = { 60.0 },
            clock = Clock.fixed(now, ZoneOffset.UTC)
        ).load()

        assertNull(snapshot.concentration)
    }

    @Test
    fun `snapshot repository failure does not create a fake state`() {
        val plans = FakeMedicationPlanRepository(listOf(plan)).apply {
            observeFailure = RepositoryPersistenceException("synthetic Widget plans")
        }
        assertThrows(RepositoryPersistenceException::class.java) {
            runBlocking {
                WidgetSnapshotLoader(
                    medicationPlans = plans,
                    doseEvents = FakeDoseEventRepository(),
                    bodyWeight = { 60.0 },
                    clock = Clock.fixed(now, ZoneOffset.UTC)
                ).load()
            }
        }
    }

    @Test
    fun `quick action persists complete Widget metadata before refresh and toast`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = WidgetEffectsSpy()
        val result = quickWork(events, effects).handle(command())

        assertEquals(WidgetQuickActionOutcome.Accepted(false), result)
        val event = events.lastInserted!!
        assertEquals(widgetOccurrenceActionEventId(occurrenceId()), event.id)
        assertEquals(now, event.occurredAt)
        assertEquals(zoneId, event.zoneId)
        assertEquals(now.atZone(zoneId).toLocalDate(), event.localDate)
        assertEquals(plan.slots.single().id, event.slotId)
        assertEquals(DoseEventSource.WIDGET, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
        assertEquals(listOf("refresh", "toast:Synthetic plan"), effects.order)
    }

    @Test
    fun `same occurrence is idempotent across minutes Widget instances and process work`() =
        runBlocking {
        val events = FakeDoseEventRepository()
        val firstEffects = WidgetEffectsSpy()
        val secondEffects = WidgetEffectsSpy()

        assertEquals(
            WidgetQuickActionOutcome.Accepted(false),
            quickWork(events, firstEffects, now).handle(command())
        )
        assertEquals(
            WidgetQuickActionOutcome.Accepted(true),
            quickWork(events, secondEffects, now.plusSeconds(5 * 60L)).handle(command())
        )

        assertEquals(1, events.insertCalls)
        assertEquals(1, events.events.size)
        assertEquals(listOf("refresh", "toast:Synthetic plan"), firstEffects.order)
        assertEquals(listOf("refresh", "toast:Synthetic plan"), secondEffects.order)
    }

    @Test
    fun `Widget collision and storage failure never refresh or overwrite`() = runBlocking {
        val id = widgetOccurrenceActionEventId(occurrenceId())
        val collision = event(id, now, Route.ORAL).copy(source = DoseEventSource.MANUAL)
        val conflictEvents = FakeDoseEventRepository(listOf(collision))
        val conflictEffects = WidgetEffectsSpy()
        assertSame(
            WidgetQuickActionOutcome.Conflict,
            quickWork(conflictEvents, conflictEffects).handle(command())
        )
        assertEquals(collision, conflictEvents.events[id])
        assertTrue(conflictEffects.order.isEmpty())

        val failedEvents = FakeDoseEventRepository().apply {
            getFailure = RepositoryPersistenceException("synthetic Widget read")
        }
        val failedEffects = WidgetEffectsSpy()
        assertSame(
            WidgetQuickActionOutcome.StorageFailure,
            quickWork(failedEvents, failedEffects).handle(command())
        )
        assertTrue(failedEffects.order.isEmpty())
    }

    @Test
    fun `refresh failure keeps the accepted row and does not retry insert`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = WidgetEffectsSpy(failRefresh = true)

        val result = quickWork(events, effects).handle(command())

        assertSame(WidgetQuickActionOutcome.AcceptedWithSideEffectFailure, result)
        assertEquals(1, events.insertCalls)
        assertEquals(1, events.events.size)
        assertEquals(listOf("refresh"), effects.order)
    }

    @Test
    fun `invalid missing and disabled Widget actions perform no write`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = WidgetEffectsSpy()
        assertSame(
            WidgetQuickActionOutcome.Invalid,
            quickWork(events, effects).handle(
                WidgetQuickActionCommand("bad-id", null, null, null)
            )
        )
        assertSame(
            WidgetQuickActionOutcome.PlanNotFound,
            ContractWidgetQuickActionWork(
                FakeMedicationPlanRepository(),
                events,
                effects,
                Clock.fixed(now, ZoneOffset.UTC),
                { zoneId }
            ).handle(command())
        )
        val disabled = plan.copy(isEnabled = false)
        assertSame(
            WidgetQuickActionOutcome.PlanDisabled,
            ContractWidgetQuickActionWork(
                FakeMedicationPlanRepository(listOf(disabled)),
                events,
                effects,
                Clock.fixed(now, ZoneOffset.UTC),
                { zoneId }
            ).handle(command())
        )
        assertEquals(0, events.insertCalls)
        assertTrue(effects.order.isEmpty())
    }

    @Test
    fun `stale date forged identity and foreign slot perform no write`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = WidgetEffectsSpy()
        val today = now.atZone(zoneId).toLocalDate()
        val valid = command()

        val stale = valid.copy(scheduledLocalDate = today.minusDays(1).toString())
        val forged = valid.copy(occurrenceId = UUID(9L, 90L).toString())
        val foreignSlot = UUID(9L, 91L)
        val foreignOccurrence = MedicationOccurrenceIdentity.derive(
            plan.id,
            foreignSlot,
            today
        ).value
        val foreign = valid.copy(
            slotId = foreignSlot.toString(),
            occurrenceId = foreignOccurrence.toString()
        )

        assertSame(WidgetQuickActionOutcome.Invalid, quickWork(events, effects).handle(stale))
        assertSame(WidgetQuickActionOutcome.Invalid, quickWork(events, effects).handle(forged))
        assertSame(
            WidgetQuickActionOutcome.Invalid,
            quickWork(events, effects).handle(foreign)
        )
        assertEquals(0, events.insertCalls)
        assertTrue(effects.order.isEmpty())
    }

    @Test
    fun `three same-day occurrences of one plan have separate action identities`() =
        runBlocking {
            val threeSlotPlan = syntheticPlan(slots = listOf(
                java.time.LocalTime.of(8, 30),
                java.time.LocalTime.of(17, 0),
                java.time.LocalTime.of(20, 30)
            ))
            val events = FakeDoseEventRepository()
            val today = now.atZone(zoneId).toLocalDate()
            val commands = threeSlotPlan.slots.map { slot ->
                command(threeSlotPlan, slot.id, today)
            }

            commands.forEachIndexed { index, action ->
                assertEquals(
                    WidgetQuickActionOutcome.Accepted(false),
                    quickWork(
                        events,
                        WidgetEffectsSpy(),
                        now.plusSeconds(index * 60L),
                        threeSlotPlan
                    ).handle(action)
                )
            }

            assertEquals(3, events.insertCalls)
            assertEquals(3, events.events.size)
            assertEquals(3, events.events.keys.distinct().size)
        }

    @Test
    fun `update loads once and renders one complete snapshot to every Widget`() = runBlocking {
        val rendered = mutableListOf<Pair<Int, WidgetRenderState>>()
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val work = ContractWidgetUpdateWork(
            snapshotLoader = WidgetSnapshotLoader(
                plans,
                FakeDoseEventRepository(),
                bodyWeight = { 60.0 },
                timeFormat = { TimeFormat.HOUR_24 },
                clock = Clock.fixed(now, ZoneOffset.UTC)
            ),
            renderer = WidgetSnapshotRenderer { id, state -> rendered += id to state }
        )

        work.handle(intArrayOf(10, 11))

        assertEquals(listOf(10, 11), rendered.map { it.first })
        assertSame(rendered[0].second, rendered[1].second)
        val snapshot = (rendered.single { it.first == 10 }.second as WidgetRenderState.Loaded).snapshot
        assertEquals(plan.id, snapshot.presentation.visiblePlans.single().planId)
        assertEquals(TimeFormat.HOUR_24, snapshot.timeFormat)
    }

    @Test
    fun `update renders the same truthful failure to every Widget`() = runBlocking {
        val rendered = mutableListOf<Pair<Int, WidgetRenderState>>()
        val plans = FakeMedicationPlanRepository(listOf(plan)).apply {
            observeFailure = RepositoryPersistenceException("synthetic Widget plans")
        }
        val work = ContractWidgetUpdateWork(
            snapshotLoader = WidgetSnapshotLoader(
                plans,
                FakeDoseEventRepository(),
                bodyWeight = { 60.0 },
                clock = Clock.fixed(now, ZoneOffset.UTC)
            ),
            renderer = WidgetSnapshotRenderer { id, state -> rendered += id to state }
        )

        work.handle(intArrayOf(20, 21, 22))

        assertEquals(listOf(20, 21, 22), rendered.map { it.first })
        assertTrue(rendered.all { it.second is WidgetRenderState.ReadFailure })
    }

    private fun quickWork(
        events: FakeDoseEventRepository,
        effects: WidgetEffectsSpy,
        actionTime: Instant = now,
        actionPlan: io.github.yingqiu0871.evolune.core.model.MedicationPlan = plan
    ) = ContractWidgetQuickActionWork(
        medicationPlans = FakeMedicationPlanRepository(listOf(actionPlan)),
        doseEvents = events,
        sideEffects = effects,
        clock = Clock.fixed(actionTime, ZoneOffset.UTC),
        zoneId = { zoneId }
    )

    private fun command() = command(
        plan,
        plan.slots.single().id,
        now.atZone(zoneId).toLocalDate()
    )

    private fun command(
        actionPlan: io.github.yingqiu0871.evolune.core.model.MedicationPlan,
        slotId: UUID,
        scheduledLocalDate: java.time.LocalDate
    ): WidgetQuickActionCommand {
        val occurrenceId = MedicationOccurrenceIdentity.derive(
            actionPlan.id,
            slotId,
            scheduledLocalDate
        ).value
        return WidgetQuickActionCommand(
            actionPlan.id.toString(),
            slotId.toString(),
            scheduledLocalDate.toString(),
            occurrenceId.toString()
        )
    }

    private fun occurrenceId(): UUID = UUID.fromString(requireNotNull(command().occurrenceId))

    private fun event(
        id: UUID,
        occurredAt: Instant,
        route: Route
    ) = DoseEvent(
        id = id,
        route = route,
        occurredAt = occurredAt,
        doseMG = plan.doseMG,
        ester = plan.ester,
        source = DoseEventSource.MANUAL
    )
}

private class WidgetEffectsSpy(
    private val failRefresh: Boolean = false
) : WidgetQuickActionSideEffects {
    val order = mutableListOf<String>()

    override suspend fun refreshWidgets() {
        order += "refresh"
        if (failRefresh) throw IllegalStateException("synthetic Widget refresh")
    }

    override suspend fun showRecorded(planName: String) {
        order += "toast:$planName"
    }
}
