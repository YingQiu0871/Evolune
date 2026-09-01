package io.github.yingqiu0871.evolune.application

import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.data.repository.RepositoryPersistenceException
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePresentation
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.experience.wear.WearAppCommandType
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmCommand
import io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResultType
import io.github.yingqiu0871.evolune.experience.wear.WearAppProducerIdentity
import io.github.yingqiu0871.evolune.experience.wear.WearAppSnapshotIdentity
import io.github.yingqiu0871.evolune.reminder.ContractNotificationActionWork
import io.github.yingqiu0871.evolune.reminder.NotificationActionCommand
import io.github.yingqiu0871.evolune.reminder.NotificationActionOutcome
import io.github.yingqiu0871.evolune.widget.ContractWidgetQuickActionWork
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionCommand
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionOutcome
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionSideEffects
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class NotificationOccurrenceConcurrencyTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-08-30T08:30:00Z")
    private val plan = syntheticPlan(
        id = UUID(0L, 1_501L),
        slots = listOf(LocalTime.of(8, 30))
    )
    private val producer = WearAppProducerIdentity(UUID(0L, 1_502L), 3L)
    private val target = occurrence(plan)

    @Test
    fun `Wear then notification leaves one Wear event and notification is replay`() = runBlocking {
        val events = FakeDoseEventRepository()
        val wear = wearHandler(events)

        assertEquals(WearAppConfirmResultType.CONFIRMED, wear.handle(wearCommand()).resultType)
        val notification = notificationWork(FakeMedicationPlanRepository(listOf(plan)), events)

        assertEquals(
            NotificationActionOutcome.Accepted(true),
            notification.handle(notificationCommand())
        )
        assertSinglePresentedEvent(
            events = events,
            expectedSource = DoseEventSource.WEAR,
            expectedId = wearAppConfirmationEventId(wearCommand().operationId)
        )
    }

    @Test
    fun `notification then Wear leaves one Reminder event and Wear is already confirmed`() =
        runBlocking {
            val events = FakeDoseEventRepository()
            val notification = notificationWork(FakeMedicationPlanRepository(listOf(plan)), events)

            assertEquals(
                NotificationActionOutcome.Accepted(false),
                notification.handle(notificationCommand())
            )
            val wear = wearHandler(events)
            val wearResult = wear.handle(wearCommand())

            assertEquals(WearAppConfirmResultType.ALREADY_CONFIRMED, wearResult.resultType)
            assertSinglePresentedEvent(
                events = events,
                expectedSource = DoseEventSource.REMINDER,
                expectedId = reminderEventId()
            )
        }

    @Test
    fun `Wear and notification deterministic interleave leaves one event`() = runBlocking {
        val wearInsertReached = CompletableDeferred<Unit>()
        val allowWearInsert = CompletableDeferred<Unit>()
        val events = FakeDoseEventRepository().apply {
            beforeInsert = { event ->
                if (event.id == wearAppConfirmationEventId(wearCommand().operationId)) {
                    wearInsertReached.complete(Unit)
                    allowWearInsert.await()
                }
            }
        }
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val wear = wearHandler(events)
        val notification = notificationWork(plans, events)

        coroutineScope {
            val wearJob = async { wear.handle(wearCommand()) }
            wearInsertReached.await()
            val notificationJob = async(start = CoroutineStart.UNDISPATCHED) {
                notification.handle(notificationCommand())
            }
            allowWearInsert.complete(Unit)

            assertEquals(WearAppConfirmResultType.CONFIRMED, wearJob.await().resultType)
            assertEquals(NotificationActionOutcome.Accepted(true), notificationJob.await())
        }
        assertSinglePresentedEvent(
            events = events,
            expectedSource = DoseEventSource.WEAR,
            expectedId = wearAppConfirmationEventId(wearCommand().operationId)
        )
    }

    @Test
    fun `Widget then notification and notification then Widget each leave one event`() = runBlocking {
        val widgetFirstEvents = FakeDoseEventRepository()
        val widgetFirstPlans = FakeMedicationPlanRepository(listOf(plan))
        val widgetFirst = widgetWork(widgetFirstPlans, widgetFirstEvents)
        assertEquals(WidgetQuickActionOutcome.Accepted(false), widgetFirst.handle(widgetCommand()))
        assertEquals(
            NotificationActionOutcome.Accepted(true),
            notificationWork(widgetFirstPlans, widgetFirstEvents).handle(notificationCommand())
        )
        assertSinglePresentedEvent(
            widgetFirstEvents,
            DoseEventSource.WIDGET,
            widgetEventId()
        )

        val notificationFirstEvents = FakeDoseEventRepository()
        val notificationFirstPlans = FakeMedicationPlanRepository(listOf(plan))
        assertEquals(
            NotificationActionOutcome.Accepted(false),
            notificationWork(notificationFirstPlans, notificationFirstEvents)
                .handle(notificationCommand())
        )
        assertEquals(
            WidgetQuickActionOutcome.Accepted(true),
            widgetWork(notificationFirstPlans, notificationFirstEvents).handle(widgetCommand())
        )
        assertSinglePresentedEvent(
            notificationFirstEvents,
            DoseEventSource.REMINDER,
            reminderEventId()
        )
    }

    @Test
    fun `Widget and notification deterministic interleave leaves one Widget event`() = runBlocking {
        val widgetInsertReached = CompletableDeferred<Unit>()
        val allowWidgetInsert = CompletableDeferred<Unit>()
        val events = FakeDoseEventRepository().apply {
            beforeInsert = { event ->
                if (event.id == widgetEventId()) {
                    widgetInsertReached.complete(Unit)
                    allowWidgetInsert.await()
                }
            }
        }
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val widget = widgetWork(plans, events)
        val notification = notificationWork(plans, events)

        coroutineScope {
            val widgetJob = async { widget.handle(widgetCommand()) }
            widgetInsertReached.await()
            val notificationJob = async(start = CoroutineStart.UNDISPATCHED) {
                notification.handle(notificationCommand())
            }
            allowWidgetInsert.complete(Unit)

            assertEquals(WidgetQuickActionOutcome.Accepted(false), widgetJob.await())
            assertEquals(NotificationActionOutcome.Accepted(true), notificationJob.await())
        }
        assertSinglePresentedEvent(events, DoseEventSource.WIDGET, widgetEventId())
    }

    @Test
    fun `repeated notification is idempotent and different occurrences remain distinct`() =
        runBlocking {
            val events = FakeDoseEventRepository()
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val notification = notificationWork(plans, events)
            assertEquals(
                NotificationActionOutcome.Accepted(false),
                notification.handle(notificationCommand())
            )
            assertEquals(
                NotificationActionOutcome.Accepted(true),
                notification.handle(notificationCommand())
            )
            assertEquals(1, events.events.size)

            val secondPlan = syntheticPlan(
                id = UUID(0L, 1_503L),
                slots = listOf(LocalTime.of(5, 0))
            )
            plans.plans[secondPlan.id] = secondPlan
            val secondTarget = occurrence(secondPlan)
            val secondNotification = notificationWorkAt(
                plans,
                events,
                secondTarget.scheduledAt.plusSeconds(60L)
            )
            assertEquals(
                NotificationActionOutcome.Accepted(false),
                secondNotification.handle(
                    NotificationActionCommand(
                        planId = secondPlan.id,
                        notificationId = 2,
                        scheduledAtMillis = secondTarget.scheduledAt.toEpochMilli()
                    )
                )
            )
            assertEquals(2, events.events.size)
            assertEquals(
                setOf(reminderEventId(), reminderEventId(secondPlan, secondTarget)),
                events.events.keys
            )
        }

    @Test
    fun `same plan different scheduledAt values are not confused`() = runBlocking {
        val twoSlotPlan = syntheticPlan(
            id = UUID(0L, 1_504L),
            slots = listOf(LocalTime.of(5, 0), LocalTime.of(8, 30))
        )
        val occurrences = occurrences(twoSlotPlan)
        val events = FakeDoseEventRepository()
        val plans = FakeMedicationPlanRepository(listOf(twoSlotPlan))
        occurrences.reversed().forEachIndexed { index, occurrence ->
            assertEquals(
                NotificationActionOutcome.Accepted(false),
                notificationWorkAt(
                    plans,
                    events,
                    occurrence.scheduledAt.plusSeconds(60L)
                ).handle(
                    NotificationActionCommand(
                        planId = twoSlotPlan.id,
                        notificationId = 10 + index,
                        scheduledAtMillis = occurrence.scheduledAt.toEpochMilli()
                    )
                )
            )
        }
        assertEquals(2, events.events.size)
        assertEquals(
            occurrences.map { reminderEventId(twoSlotPlan, it) }.toSet(),
            events.events.keys
        )
    }

    @Test
    fun `notification rereads a modified or deleted plan after waiting for the coordinator`() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val plans = FakeMedicationPlanRepository(listOf(plan))
            val events = FakeDoseEventRepository()
            val work = notificationWork(plans, events)

            val holder = async {
                OccurrenceConfirmationCoordinator.withLock {
                    entered.complete(Unit)
                    release.await()
                }
            }
            entered.await()
            val waiting = async(start = CoroutineStart.UNDISPATCHED) {
                work.handle(notificationCommand())
            }
            plans.plans[plan.id] = plan.copy(
                slots = listOf(
                    plan.slots.single().copy(localTime = LocalTime.of(9, 0))
                )
            )
            release.complete(Unit)
            holder.await()
            assertEquals(NotificationActionOutcome.Invalid, waiting.await())
            assertEquals(0, events.events.size)

            val secondEntered = CompletableDeferred<Unit>()
            val secondRelease = CompletableDeferred<Unit>()
            val secondHolder = async {
                OccurrenceConfirmationCoordinator.withLock {
                    secondEntered.complete(Unit)
                    secondRelease.await()
                }
            }
            secondEntered.await()
            val deleted = async(start = CoroutineStart.UNDISPATCHED) {
                work.handle(notificationCommand())
            }
            plans.plans.remove(plan.id)
            secondRelease.complete(Unit)
            secondHolder.await()
            assertEquals(NotificationActionOutcome.StalePlan, deleted.await())
            assertEquals(0, events.events.size)
        }

    @Test
    fun `legacy slot window null slot and same day events all suppress notification`() =
        runBlocking {
            listOf(
                event(UUID(0L, 1_601L), DoseEventSource.MANUAL, slotId = target.slotId, localDate = targetDate()),
                event(UUID(0L, 1_602L), DoseEventSource.MANUAL, slotId = target.slotId, localDate = null),
                event(UUID(0L, 1_603L), DoseEventSource.MANUAL, slotId = null, localDate = null),
                event(
                    UUID(0L, 1_604L),
                    DoseEventSource.MANUAL,
                    slotId = null,
                    localDate = targetDate(),
                    occurredAt = now.minusSeconds(2 * 60 * 60L)
                )
            ).forEach { existing ->
                val events = FakeDoseEventRepository(listOf(existing))
                val result = notificationWork(
                    FakeMedicationPlanRepository(listOf(plan)),
                    events
                ).handle(notificationCommand())
                assertEquals(NotificationActionOutcome.Accepted(true), result)
                assertEquals(1, events.events.size)
                assertEquals(0, events.insertCalls)
            }
        }

    @Test
    fun `cross date event does not suppress today's occurrence`() = runBlocking {
        val previousDay = event(
            UUID(0L, 1_605L),
            DoseEventSource.MANUAL,
            slotId = null,
            localDate = targetDate().minusDays(1L),
            occurredAt = now.minusSeconds(2 * 60 * 60L)
        )
        val events = FakeDoseEventRepository(listOf(previousDay))
        val result = notificationWork(FakeMedicationPlanRepository(listOf(plan)), events)
            .handle(notificationCommand())

        assertEquals(NotificationActionOutcome.Accepted(false), result)
        assertEquals(2, events.events.size)
        assertEquals(1, events.events.values.count { it.source == DoseEventSource.REMINDER })
    }

    @Test
    fun `notification insert failure retries without duplicate or false success`() = runBlocking {
        val events = FakeDoseEventRepository().apply {
            insertFailure = RepositoryPersistenceException("synthetic notification insert")
        }
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val work = notificationWork(plans, events)

        assertEquals(NotificationActionOutcome.StorageFailure, work.handle(notificationCommand()))
        assertEquals(0, events.events.size)
        events.insertFailure = null
        assertEquals(NotificationActionOutcome.Accepted(false), work.handle(notificationCommand()))
        assertEquals(1, events.events.size)
    }

    @Test
    fun `cancellation releases coordinator for a successful retry`() = runBlocking {
        val events = FakeDoseEventRepository().apply {
            insertFailure = kotlinx.coroutines.CancellationException("synthetic cancellation")
        }
        val plans = FakeMedicationPlanRepository(listOf(plan))
        val work = notificationWork(plans, events)

        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            runBlocking { work.handle(notificationCommand()) }
        }
        events.insertFailure = null
        assertEquals(NotificationActionOutcome.Accepted(false), work.handle(notificationCommand()))
        assertEquals(1, events.events.size)
    }

    @Test
    fun `Wear journal replay after notification winner remains already confirmed`() = runBlocking {
        val events = FakeDoseEventRepository()
        val journal = NotificationTestConfirmationJournal()
        val plans = FakeMedicationPlanRepository(listOf(plan))
        assertEquals(
            NotificationActionOutcome.Accepted(false),
            notificationWork(plans, events).handle(notificationCommand())
        )
        val wear = wearHandler(events, journal)
        val first = wear.handle(wearCommand())
        val replay = wear.handle(wearCommand())

        assertEquals(WearAppConfirmResultType.ALREADY_CONFIRMED, first.resultType)
        assertEquals(first, replay)
        assertEquals(1, events.events.size)
    }

    @Test
    fun `presentation and PK input remain single and independent of event input order`() {
        val reminder = event(
            UUID(0L, 1_606L),
            source = DoseEventSource.REMINDER,
            slotId = null,
            localDate = targetDate()
        ).copy(id = reminderEventId())
        val forward = listOf(reminder).mapNotNull(DoseEvent::toRecordedMedicationEvent)
        val reverse = listOf(reminder).reversed().mapNotNull(DoseEvent::toRecordedMedicationEvent)
        val occurrences = occurrences(plan)
        val forwardPresentation = MedicationOccurrencePresentation.derive(
            occurrences,
            forward,
            now
        )
        val reversePresentation = MedicationOccurrencePresentation.derive(
            occurrences,
            reverse,
            now
        )

        assertEquals(1, forwardPresentation.count { it.recordedEventId != null })
        assertEquals(
            forwardPresentation.map { it.recordedEventId },
            reversePresentation.map { it.recordedEventId }
        )
        assertEquals(1, listOf(reminder).distinctBy { it.id }.size)
    }

    private fun notificationWork(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository
    ) = notificationWorkAt(plans, events, now)

    private fun notificationWorkAt(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository,
        recordedAt: Instant
    ) = ContractNotificationActionWork(
        medicationPlans = plans,
        doseEvents = events,
        sideEffects = NoOpNotificationSideEffects,
        clock = Clock.fixed(recordedAt, zone),
        zoneId = { zone }
    )

    private fun widgetWork(
        plans: FakeMedicationPlanRepository,
        events: FakeDoseEventRepository
    ) = ContractWidgetQuickActionWork(
        medicationPlans = plans,
        doseEvents = events,
        sideEffects = NotificationNoOpWidgetSideEffects,
        clock = Clock.fixed(now, zone),
        zoneId = { zone }
    )

    private fun wearHandler(
        events: FakeDoseEventRepository,
        journal: WearAppConfirmationOperationJournal = NotificationTestConfirmationJournal()
    ) = WearAppConfirmationHandler(
        context = null,
        medicationPlans = FakeMedicationPlanRepository(listOf(plan)),
        doseEvents = events,
        clock = Clock.fixed(now, zone),
        zoneId = { zone },
        producerIdentity = { producer },
        operationJournal = journal
    )

    private fun notificationCommand() = NotificationActionCommand(
        planId = plan.id,
        notificationId = 1,
        scheduledAtMillis = target.scheduledAt.toEpochMilli()
    )

    private fun widgetCommand() = WidgetQuickActionCommand(
        planId = plan.id.toString(),
        slotId = target.slotId.toString(),
        scheduledLocalDate = targetDate().toString(),
        occurrenceId = target.id.value.toString()
    )

    private fun wearCommand() = WearAppConfirmCommand(
        protocolVersion = 1,
        commandType = WearAppCommandType.CONFIRM_OCCURRENCE,
        operationId = UUID(0L, 1_505L),
        createdAt = now.minusSeconds(60L),
        sourceSnapshot = WearAppSnapshotIdentity(
            producerInstanceId = producer.producerInstanceId,
            producerGeneration = producer.producerGeneration,
            snapshotRevision = 11L
        ),
        occurrenceId = target.id.value,
        planId = target.planId,
        slotId = target.slotId,
        localDate = targetDate(),
        scheduledAt = target.scheduledAt
    )

    private fun assertSinglePresentedEvent(
        events: FakeDoseEventRepository,
        expectedSource: DoseEventSource,
        expectedId: UUID
    ) {
        assertEquals(1, events.events.size)
        val stored = events.events.values.single()
        assertEquals(expectedSource, stored.source)
        assertEquals(expectedId, stored.id)
        val presentation = MedicationOccurrencePresentation.derive(
            occurrences(plan),
            events.events.values.mapNotNull(DoseEvent::toRecordedMedicationEvent),
            now
        ).single()
        assertEquals(target.id.value, presentation.occurrence.id.value)
        assertEquals(expectedId, presentation.recordedEventId)
        assertEquals(1, events.events.values.map { it.id }.distinct().size)
    }

    private fun occurrence(plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan) =
        occurrences(plan).single()

    private fun occurrences(
        plan: io.github.yingqiu0871.evolune.core.model.MedicationPlan
    ): List<MedicationOccurrence> = MedicationOccurrenceGenerator.generate(
        schedules = listOf(plan.toMedicationSchedule()),
        window = OccurrenceGenerationWindow(
            targetDate().atStartOfDay(zone).toInstant(),
            targetDate().plusDays(1L).atStartOfDay(zone).toInstant()
        ),
        zoneId = zone
    )

    private fun targetDate(): LocalDate = now.atZone(zone).toLocalDate()

    private fun reminderEventId(
        eventPlan: io.github.yingqiu0871.evolune.core.model.MedicationPlan = plan,
        eventTarget: MedicationOccurrence = target
    ): UUID = io.github.yingqiu0871.evolune.reminder.reminderDoseEventId(
        eventPlan.id,
        eventTarget.scheduledAt.toEpochMilli()
    )

    private fun widgetEventId(): UUID = widgetOccurrenceActionEventId(target.id.value)

    private fun event(
        id: UUID,
        source: DoseEventSource,
        slotId: UUID?,
        localDate: LocalDate?,
        occurredAt: Instant = now
    ) = DoseEvent(
        id = id,
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = zone,
        localDate = localDate,
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = slotId,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = 1L
    )
}

private object NoOpNotificationSideEffects : io.github.yingqiu0871.evolune.reminder.NotificationActionSideEffects {
    override suspend fun refreshWidgets() = Unit

    override fun cancelNotification(notificationId: Int) = Unit
}

private object NotificationNoOpWidgetSideEffects : WidgetQuickActionSideEffects {
    override suspend fun refreshWidgets() = Unit

    override suspend fun showRecorded(planName: String) = Unit
}

private class NotificationTestConfirmationJournal : WearAppConfirmationOperationJournal {
    private val records = linkedMapOf<UUID, WearAppStoredConfirmation>()

    override fun read(operationId: UUID): WearAppStoredConfirmation? = records[operationId]

    override fun begin(operationId: UUID, fingerprint: String): Boolean {
        val existing = records[operationId]
        if (existing != null) return existing.fingerprint == fingerprint
        records[operationId] = WearAppStoredConfirmation(fingerprint, null)
        return true
    }

    override fun saveResult(
        operationId: UUID,
        fingerprint: String,
        result: io.github.yingqiu0871.evolune.experience.wear.WearAppConfirmResult
    ): Boolean {
        records[operationId] = WearAppStoredConfirmation(fingerprint, result)
        return true
    }
}
