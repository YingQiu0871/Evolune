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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class WidgetWorkTest {
    private val plan = syntheticPlan()
    private val now = Instant.parse("2027-01-15T00:30:00.789Z")
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
    fun `PK chart uses fixed 48 hour window with 25 points and shares current scalar`() = runBlocking {
        val event = event(UUID(0L, 708L), now.minusSeconds(3_600L), Route.ORAL)
        val snapshot = WidgetSnapshotLoader(
            medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
            doseEvents = FakeDoseEventRepository().apply { pkEvents = listOf(event) },
            bodyWeight = { 60.0 },
            clock = Clock.fixed(now, ZoneOffset.UTC)
        ).load(includePkChart = true)

        assertEquals(WidgetPkChartPolicy.POINT_COUNT, snapshot.pkChart.size)
        assertEquals(-24.0, snapshot.pkChart.first().offsetHours, 1e-9)
        assertEquals(24.0, snapshot.pkChart.last().offsetHours, 1e-9)
        val current = snapshot.pkChart.minBy { kotlin.math.abs(it.offsetHours) }
        assertEquals(0.0, current.offsetHours, 1e-9)
        assertEquals(snapshot.concentration!!, current.concentration, 1e-6)
        assertEquals(now, snapshot.concentrationComputedAt)
    }

    @Test
    fun `non PK snapshot does not compute chart or concentration`() = runBlocking {
        val snapshot = WidgetSnapshotLoader(
            medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
            doseEvents = FakeDoseEventRepository(),
            bodyWeight = { error("body weight must not be read") },
            clock = Clock.fixed(now, ZoneOffset.UTC)
        ).load(includePkChart = true)

        assertTrue(snapshot.pkChart.isEmpty())
        assertNull(snapshot.concentration)
        assertNull(snapshot.concentrationComputedAt)
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
    fun `action time rechecks availability and rejects upcoming or expired occurrence`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = WidgetEffectsSpy()
        val beforeDue = now.minusSeconds(3_601L)
        val afterWindow = now.plusSeconds(3_601L)

        assertSame(
            WidgetQuickActionOutcome.Invalid,
            quickWork(events, effects, beforeDue).handle(command())
        )
        assertSame(
            WidgetQuickActionOutcome.Invalid,
            quickWork(events, effects, afterWindow).handle(command())
        )

        assertEquals(0, events.insertCalls)
        assertTrue(effects.order.isEmpty())
    }

    @Test
    fun `accepted widget action remains idempotent after its availability window closes`() = runBlocking {
        val events = FakeDoseEventRepository()
        val firstEffects = WidgetEffectsSpy()
        val replayEffects = WidgetEffectsSpy()

        assertEquals(
            WidgetQuickActionOutcome.Accepted(false),
            quickWork(events, firstEffects, now).handle(command())
        )
        assertEquals(
            WidgetQuickActionOutcome.Accepted(true),
            quickWork(events, replayEffects, now.plusSeconds(3_601L)).handle(command())
        )

        assertEquals(1, events.insertCalls)
        assertEquals(listOf("refresh", "toast:Synthetic plan"), replayEffects.order)
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

            commands.forEach { action ->
                val localDate = LocalDate.parse(requireNotNull(action.scheduledLocalDate))
                val slotId = UUID.fromString(requireNotNull(action.slotId))
                val actionInstant = localDate
                    .atTime(threeSlotPlan.slots.single { it.id == slotId }.localTime)
                    .atZone(zoneId)
                    .toInstant()
                assertEquals(
                    WidgetQuickActionOutcome.Accepted(false),
                    quickWork(
                        events,
                        WidgetEffectsSpy(),
                        actionInstant,
                        threeSlotPlan
                    ).handle(action)
                )
            }

            assertEquals(3, events.insertCalls)
            assertEquals(3, events.events.size)
            assertEquals(3, events.events.keys.distinct().size)
        }

    @Test
    fun `action-time availability keeps legacy null-slot event on earlier sibling`() =
        runBlocking {
            val twoSlotPlan = syntheticPlan(
                slots = listOf(
                    java.time.LocalTime.of(7, 0),
                    java.time.LocalTime.of(9, 30)
                )
            )
            val unrelatedPlan = syntheticPlan(
                id = UUID(0L, 602L),
                slots = listOf(java.time.LocalTime.of(12, 0))
            ).copy(route = Route.INJECTION)
            val today = now.atZone(zoneId).toLocalDate()
            val earlierAt = today.atTime(7, 0).atZone(zoneId).toInstant()
            val laterAt = today.atTime(9, 30).atZone(zoneId).toInstant()
            val legacyEvent = event(UUID(0L, 801L), earlierAt, Route.ORAL).copy(
                localDate = today,
                slotId = null
            )
            val unrelatedEvent = event(UUID(0L, 802L), earlierAt, Route.ANTIANDROGEN)

            listOf(
                listOf(twoSlotPlan, unrelatedPlan) to listOf(legacyEvent, unrelatedEvent),
                listOf(unrelatedPlan, twoSlotPlan) to listOf(unrelatedEvent, legacyEvent)
            ).forEach { (plans, initialEvents) ->
                val events = FakeDoseEventRepository(initialEvents)
                val outcome = quickWork(
                    events = events,
                    effects = WidgetEffectsSpy(),
                    actionTime = laterAt,
                    actionPlan = twoSlotPlan,
                    availablePlans = plans
                ).handle(command(twoSlotPlan, twoSlotPlan.slots[1].id, today))

                assertEquals(WidgetQuickActionOutcome.Accepted(false), outcome)
                assertEquals(1, events.insertCalls)
                assertEquals(twoSlotPlan.slots[1].id, events.lastInserted?.slotId)
                assertEquals(1, events.events.values.count { it.id == legacyEvent.id })
                assertEquals(1, events.events.values.count { it.slotId == twoSlotPlan.slots[1].id })
            }
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
        actionPlan: io.github.yingqiu0871.evolune.core.model.MedicationPlan = plan,
        availablePlans: List<io.github.yingqiu0871.evolune.core.model.MedicationPlan> =
            listOf(actionPlan)
    ) = ContractWidgetQuickActionWork(
        medicationPlans = FakeMedicationPlanRepository(availablePlans),
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
