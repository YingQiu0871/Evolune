package io.github.yingqiu0871.evolune.reminder

import io.github.yingqiu0871.evolune.application.FakeDoseEventRepository
import io.github.yingqiu0871.evolune.application.FakeMedicationPlanRepository
import io.github.yingqiu0871.evolune.application.syntheticPlan
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ReminderReceiverWorkTest {
    private val plan = syntheticPlan()
    private val scheduledAtMillis = Instant.parse("2027-01-15T00:30:00Z").toEpochMilli()
    private val recordedAt = Instant.parse("2027-01-15T08:30:00.123Z")
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun `delivery reads the inclusive window and suppresses a matching boundary check-in`() =
        runBlocking {
            val events = FakeDoseEventRepository().apply {
                rangeEvents = listOf(
                    eventAt(scheduledAtMillis + DOSE_CHECK_IN_WINDOW_MILLIS)
                )
            }
            val sideEffects = ReminderEffectsSpy()
            val result = ContractReminderDeliveryWork(
                medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
                doseEvents = events,
                sideEffects = sideEffects
            ).handle(command())

            assertSame(ReminderDeliveryOutcome.CheckInFound, result)
            assertEquals(
                Instant.ofEpochMilli(scheduledAtMillis - DOSE_CHECK_IN_WINDOW_MILLIS),
                events.lastRange?.first
            )
            assertEquals(
                Instant.ofEpochMilli(scheduledAtMillis + DOSE_CHECK_IN_WINDOW_MILLIS + 1L),
                events.lastRange?.second
            )
            assertEquals(0, sideEffects.notifications)
            assertEquals(1, sideEffects.schedules)
            assertEquals(0, events.insertCalls)
        }

    @Test
    fun `delivery notifies only after successful Domain reads`() = runBlocking {
        val sideEffects = ReminderEffectsSpy()
        val result = ContractReminderDeliveryWork(
            medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
            doseEvents = FakeDoseEventRepository(),
            sideEffects = sideEffects
        ).handle(command())

        assertSame(ReminderDeliveryOutcome.Notified, result)
        assertEquals(listOf("notify", "schedule"), sideEffects.order)
    }

    @Test
    fun `plan and event read failures create no reminder`() = runBlocking {
        val planFailureEffects = ReminderEffectsSpy()
        val planFailure = FakeMedicationPlanRepository(listOf(plan)).apply {
            getFailure = RepositoryPersistenceException("synthetic plan read")
        }
        assertSame(
            ReminderDeliveryOutcome.StorageFailure,
            ContractReminderDeliveryWork(
                planFailure,
                FakeDoseEventRepository(),
                planFailureEffects
            ).handle(command())
        )
        assertTrue(planFailureEffects.order.isEmpty())

        val eventFailureEffects = ReminderEffectsSpy()
        val eventFailure = FakeDoseEventRepository().apply {
            rangeFailure = RepositoryPersistenceException("synthetic event read")
        }
        assertSame(
            ReminderDeliveryOutcome.StorageFailure,
            ContractReminderDeliveryWork(
                FakeMedicationPlanRepository(listOf(plan)),
                eventFailure,
                eventFailureEffects
            ).handle(command())
        )
        assertTrue(eventFailureEffects.order.isEmpty())
    }

    @Test
    fun `notification failure does not write or schedule through a fallback`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = ReminderEffectsSpy(failNotification = true)
        val result = ContractReminderDeliveryWork(
            FakeMedicationPlanRepository(listOf(plan)),
            events,
            effects
        ).handle(command())

        assertSame(ReminderDeliveryOutcome.UnexpectedFailure, result)
        assertEquals(0, events.insertCalls)
        assertEquals(0, effects.schedules)
    }

    @Test
    fun `notification action persists complete metadata before side effects`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = NotificationEffectsSpy()
        val result = notificationWork(events, effects).handle(notificationCommand())

        assertEquals(NotificationActionOutcome.Accepted(false), result)
        val event = events.lastInserted!!
        assertEquals(reminderDoseEventId(plan.id, scheduledAtMillis), event.id)
        assertEquals(recordedAt, event.occurredAt)
        assertEquals(zoneId, event.zoneId)
        assertEquals(
            Instant.ofEpochMilli(scheduledAtMillis).atZone(zoneId).toLocalDate(),
            event.localDate
        )
        assertEquals(plan.slots.single().id, event.slotId)
        assertEquals(DoseEventSource.REMINDER, event.source)
        assertEquals(DoseEventStatus.RECORDED, event.status)
        assertEquals(1L, event.revision)
        assertEquals(listOf("refresh", "cancel"), effects.order)
    }

    @Test
    fun `duplicate notification delivery is replayed without a second write`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = NotificationEffectsSpy()
        val work = notificationWork(events, effects)

        assertEquals(NotificationActionOutcome.Accepted(false), work.handle(notificationCommand()))
        assertEquals(NotificationActionOutcome.Accepted(true), work.handle(notificationCommand()))

        assertEquals(1, events.insertCalls)
        assertEquals(1, events.events.size)
        assertEquals(4, effects.order.size)
    }

    @Test
    fun `notification conflict and storage failure have zero success side effects`() = runBlocking {
        val collisionId = reminderDoseEventId(plan.id, scheduledAtMillis)
        val collision = eventAt(recordedAt.toEpochMilli()).copy(
            id = collisionId,
            source = DoseEventSource.MANUAL
        )
        val conflictEvents = FakeDoseEventRepository(listOf(collision))
        val conflictEffects = NotificationEffectsSpy()
        assertSame(
            NotificationActionOutcome.Conflict,
            notificationWork(conflictEvents, conflictEffects).handle(notificationCommand())
        )
        assertTrue(conflictEffects.order.isEmpty())
        assertEquals(collision, conflictEvents.events[collisionId])

        val failedEvents = FakeDoseEventRepository().apply {
            getFailure = RepositoryPersistenceException("synthetic notification read")
        }
        val failedEffects = NotificationEffectsSpy()
        assertSame(
            NotificationActionOutcome.StorageFailure,
            notificationWork(failedEvents, failedEffects).handle(notificationCommand())
        )
        assertTrue(failedEffects.order.isEmpty())
    }

    @Test
    fun `accepted database write remains when notification side effect fails`() = runBlocking {
        val events = FakeDoseEventRepository()
        val effects = NotificationEffectsSpy(failRefresh = true)

        val result = notificationWork(events, effects).handle(notificationCommand())

        assertSame(NotificationActionOutcome.AcceptedWithSideEffectFailure, result)
        assertEquals(1, events.events.size)
        assertEquals(1, events.insertCalls)
        assertEquals(listOf("refresh"), effects.order)
    }

    @Test
    fun `stale notification cancels without reporting a recorded dose`() = runBlocking {
        val effects = NotificationEffectsSpy()
        val work = ContractNotificationActionWork(
            medicationPlans = FakeMedicationPlanRepository(),
            doseEvents = FakeDoseEventRepository(),
            sideEffects = effects,
            clock = Clock.fixed(recordedAt, ZoneOffset.UTC),
            zoneId = { zoneId }
        )

        assertSame(NotificationActionOutcome.StalePlan, work.handle(notificationCommand()))
        assertEquals(listOf("cancel"), effects.order)
    }

    @Test
    fun `reschedule passes all Domain plans once and preserves scheduler fail-fast`() = runBlocking {
        val plans = listOf(plan, syntheticPlan(UUID(0L, 602L)), syntheticPlan(UUID(0L, 603L)))
        val scheduler = SchedulerSpy(failAt = plans[1].id)
        val result = ContractReminderRescheduleWork(
            FakeMedicationPlanRepository(plans),
            scheduler
        ).handle()

        assertSame(ReminderRescheduleOutcome.UnexpectedFailure, result)
        assertEquals(listOf(plans[0].id, plans[1].id), scheduler.visited)
    }

    @Test
    fun `reschedule read failure does not invoke scheduler`() = runBlocking {
        val plans = FakeMedicationPlanRepository(listOf(plan)).apply {
            observeFailure = RepositoryPersistenceException("synthetic observe")
        }
        val scheduler = SchedulerSpy()

        assertSame(
            ReminderRescheduleOutcome.StorageFailure,
            ContractReminderRescheduleWork(plans, scheduler).handle()
        )
        assertTrue(scheduler.visited.isEmpty())
    }

    private fun command() = ReminderDeliveryCommand(
        planId = plan.id,
        notificationId = 41,
        scheduledAtMillis = scheduledAtMillis
    )

    private fun notificationCommand() = NotificationActionCommand(
        planId = plan.id,
        notificationId = 42,
        scheduledAtMillis = scheduledAtMillis
    )

    private fun notificationWork(
        events: FakeDoseEventRepository,
        effects: NotificationEffectsSpy
    ) = ContractNotificationActionWork(
        medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
        doseEvents = events,
        sideEffects = effects,
        clock = Clock.fixed(recordedAt, ZoneOffset.UTC),
        zoneId = { zoneId }
    )

    private fun eventAt(epochMillis: Long) = DoseEvent(
        id = UUID(0L, epochMillis),
        route = plan.route,
        occurredAt = Instant.ofEpochMilli(epochMillis),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        source = DoseEventSource.MANUAL
    )
}

private class ReminderEffectsSpy(
    private val failNotification: Boolean = false
) : ReminderDeliverySideEffects {
    val order = mutableListOf<String>()
    var notifications = 0
    var schedules = 0

    override fun sendReminder(plan: MedicationPlan, command: ReminderDeliveryCommand) {
        order += "notify"
        notifications += 1
        if (failNotification) throw IllegalStateException("synthetic notification failure")
    }

    override fun scheduleNextBatch(plan: MedicationPlan) {
        order += "schedule"
        schedules += 1
    }
}

private class NotificationEffectsSpy(
    private val failRefresh: Boolean = false
) : NotificationActionSideEffects {
    val order = mutableListOf<String>()

    override suspend fun refreshWidgets() {
        order += "refresh"
        if (failRefresh) throw IllegalStateException("synthetic refresh failure")
    }

    override fun cancelNotification(notificationId: Int) {
        order += "cancel"
    }
}

private class SchedulerSpy(
    private val failAt: UUID? = null
) : MedicationPlanReminderScheduler {
    val visited = mutableListOf<UUID>()

    override fun schedule(plan: MedicationPlan) = Unit

    override fun cancel(planId: UUID) = Unit

    override suspend fun reschedule(plans: List<MedicationPlan>) {
        plans.forEach { plan ->
            visited += plan.id
            if (plan.id == failAt) {
                throw IllegalStateException("synthetic schedule failure")
            }
        }
    }
}
