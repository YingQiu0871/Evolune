package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.application.RecordAcceptance
import io.github.yingqiu0871.evolune.application.RecordDoseEventActionResult
import io.github.yingqiu0871.evolune.application.WearActionRecorder
import io.github.yingqiu0871.evolune.application.widgetOccurrenceActionEventId
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.core.presentation.toMedicationSchedule
import io.github.yingqiu0871.evolune.core.presentation.toRecordedMedicationEvent
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.experience.MedicationOccurrence
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceGenerator
import io.github.yingqiu0871.evolune.experience.MedicationOccurrencePresentation
import io.github.yingqiu0871.evolune.experience.OccurrenceGenerationWindow
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.reminder.ContractNotificationActionWork
import io.github.yingqiu0871.evolune.reminder.NotificationActionCommand
import io.github.yingqiu0871.evolune.reminder.NotificationActionOutcome
import io.github.yingqiu0871.evolune.reminder.NotificationActionSideEffects
import io.github.yingqiu0871.evolune.reminder.reminderDoseEventId
import io.github.yingqiu0871.evolune.widget.ContractWidgetQuickActionWork
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionCommand
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionOutcome
import io.github.yingqiu0871.evolune.widget.WidgetQuickActionSideEffects
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReceiverWidgetProductionCutoverTest {
    private lateinit var context: Context
    private var database: AppDatabase? = null

    @Before
    fun prepareDisposableDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none(File::exists))
    }

    @After
    fun removeDisposableDatabase() {
        closeDatabase()
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none(File::exists))
    }

    @Test
    fun notificationAndWidgetActionsPersistReplayAndReopenThroughProviderContracts() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val notificationEffects = NotificationEffects()
        val notificationWork = ContractNotificationActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = notificationEffects,
            clock = Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        )
        val notificationCommand = NotificationActionCommand(
            planId = PLAN_ID,
            notificationId = 77,
            scheduledAtMillis = scheduledAtMillis(FIRST_SLOT_TIME)
        )

        assertEquals(
            NotificationActionOutcome.Accepted(false),
            notificationWork.handle(notificationCommand)
        )
        val delayedNotificationWork = ContractNotificationActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = notificationEffects,
            clock = Clock.fixed(NOTIFICATION_REPLAY_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        )
        assertEquals(
            NotificationActionOutcome.Accepted(true),
            delayedNotificationWork.handle(notificationCommand)
        )
        val reminderId = reminderDoseEventId(PLAN_ID, scheduledAtMillis(FIRST_SLOT_TIME))
        val reminder = requireNotNull(provider.doseEvents.getById(reminderId))
        assertEquals(DoseEventSource.REMINDER, reminder.source)
        assertEquals(NOTIFICATION_OCCURRED_AT, reminder.occurredAt)
        assertEquals(TEST_ZONE, reminder.zoneId)
        assertEquals(OCCURRENCE_DATE, reminder.localDate)
        assertEquals(plan().slots.first().id, reminder.slotId)
        assertEquals(1L, reminder.revision)
        assertEquals(2, notificationEffects.refreshes)
        assertEquals(2, notificationEffects.cancellations)

        val widgetEffects = WidgetEffects()
        val widgetWork = ContractWidgetQuickActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = widgetEffects,
            clock = Clock.fixed(WIDGET_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        )
        val widgetCommand = widgetCommand(SECOND_SLOT_TIME)
        assertEquals(WidgetQuickActionOutcome.Accepted(false), widgetWork.handle(widgetCommand))
        val replayWidgetWork = ContractWidgetQuickActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = widgetEffects,
            clock = Clock.fixed(WIDGET_REPLAY_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        )
        assertEquals(WidgetQuickActionOutcome.Accepted(true), replayWidgetWork.handle(widgetCommand))
        val widgetId = widgetOccurrenceActionEventId(widgetOccurrenceId(SECOND_SLOT_TIME))
        val widget = requireNotNull(provider.doseEvents.getById(widgetId))
        assertEquals(DoseEventSource.WIDGET, widget.source)
        assertEquals(WIDGET_OCCURRED_AT, widget.occurredAt)
        assertEquals(TEST_ZONE, widget.zoneId)
        assertEquals(OCCURRENCE_DATE, widget.localDate)
        assertEquals(plan().slots.last().id, widget.slotId)
        assertEquals(1L, widget.revision)
        assertEquals(2, widgetEffects.refreshes)
        assertEquals(2, widgetEffects.toasts)
        assertEquals(2, rawEventCount())
        assertEquals(3, requireNotNull(database).openHelper.readableDatabase.version)
        assertSingleDisposableDatabase()

        closeDatabase()
        val reopenedProvider = ProductionRepositoryProvider(openDatabase())
        assertEquals(reminder, reopenedProvider.doseEvents.getById(reminderId))
        assertEquals(widget, reopenedProvider.doseEvents.getById(widgetId))
        val restoredPlan = requireNotNull(reopenedProvider.medicationPlans.getById(PLAN_ID))
        assertEquals(plan(), restoredPlan)
        assertEquals(listOf(LocalTime.of(8, 30), LocalTime.of(20, 0)), restoredPlan.slots.map { it.localTime })
        assertSingleDisposableDatabase()
    }

    @Test
    fun delayedNotificationCompletesTheEarlySlotWithoutCompletingTheLaterSlot() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val effects = NotificationEffects()
        val result = ContractNotificationActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = effects,
            clock = Clock.fixed(NOTIFICATION_DELAYED_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        ).handle(notificationCommand(FIRST_SLOT_TIME))

        assertEquals(NotificationActionOutcome.Accepted(false), result)
        val reminder = requireNotNull(
            provider.doseEvents.getById(reminderDoseEventId(PLAN_ID, scheduledAtMillis(FIRST_SLOT_TIME)))
        )
        assertEquals(plan().slots.first().id, reminder.slotId)
        assertEquals(OCCURRENCE_DATE, reminder.localDate)
        assertEquals(NOTIFICATION_DELAYED_OCCURRED_AT, reminder.occurredAt)

        val presentation = MedicationOccurrencePresentation.derive(
            occurrences = occurrences(plan()),
            recordedEvents = provider.doseEvents.observeAll().first()
                .mapNotNull(DoseEvent::toRecordedMedicationEvent),
            now = NOTIFICATION_DELAYED_OCCURRED_AT
        )
        assertEquals(
            reminder.id,
            presentation.single { it.occurrence.slotId == plan().slots.first().id }.recordedEventId
        )
        assertNull(
            presentation.single { it.occurrence.slotId == plan().slots.last().id }.recordedEventId
        )
        assertEquals(1, rawEventCount())
    }

    @Test
    fun notificationThenWidgetForTheSameOccurrenceReturnsWidgetReplay() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val notificationEffects = NotificationEffects()
        assertEquals(
            NotificationActionOutcome.Accepted(false),
            ContractNotificationActionWork(
                provider.medicationPlans,
                provider.doseEvents,
                notificationEffects,
                Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
                { TEST_ZONE }
            ).handle(notificationCommand(FIRST_SLOT_TIME))
        )

        val widgetEffects = WidgetEffects()
        assertEquals(
            WidgetQuickActionOutcome.Accepted(true),
            ContractWidgetQuickActionWork(
                provider.medicationPlans,
                provider.doseEvents,
                widgetEffects,
                Clock.fixed(WIDGET_REPLAY_OCCURRED_AT, ZoneOffset.UTC),
                { TEST_ZONE }
            ).handle(widgetCommand(FIRST_SLOT_TIME))
        )
        val reminder = requireNotNull(
            provider.doseEvents.getById(reminderDoseEventId(PLAN_ID, scheduledAtMillis(FIRST_SLOT_TIME)))
        )
        assertEquals(DoseEventSource.REMINDER, reminder.source)
        assertEquals(1, rawEventCount())
        assertNull(provider.doseEvents.getById(widgetOccurrenceActionEventId(widgetOccurrenceId(FIRST_SLOT_TIME))))
        assertEquals(1, widgetEffects.refreshes)
        assertEquals(1, widgetEffects.toasts)
    }

    @Test
    fun invalidNotificationScheduledAtDoesNotWriteOrRunSuccessSideEffects() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val effects = NotificationEffects()

        assertEquals(
            NotificationActionOutcome.Invalid,
            ContractNotificationActionWork(
                provider.medicationPlans,
                provider.doseEvents,
                effects,
                Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
                { TEST_ZONE }
            ).handle(notificationCommand(INVALID_SLOT_TIME))
        )
        assertEquals(0, rawEventCount())
        assertEquals(0, effects.refreshes)
        assertEquals(0, effects.cancellations)
    }

    @Test
    fun slotDeletionDisablesAndDeletionOfPlanRejectStaleNotificationConservatively() = runBlocking {
        suspend fun assertNoRecordAfter(
            mutate: suspend (ProductionRepositoryProvider) -> Unit,
            expected: NotificationActionOutcome
        ) {
            val provider = ProductionRepositoryProvider(openDatabase())
            assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
            mutate(provider)
            val effects = NotificationEffects()
            assertEquals(
                expected,
                ContractNotificationActionWork(
                    provider.medicationPlans,
                    provider.doseEvents,
                    effects,
                    Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
                    { TEST_ZONE }
                ).handle(notificationCommand(FIRST_SLOT_TIME))
            )
            assertEquals(0, rawEventCount())
            if (expected == NotificationActionOutcome.Invalid) {
                assertTrue(effects.refreshes == 0 && effects.cancellations == 0)
            } else {
                assertEquals(listOf("cancel"), effects.log)
            }
            closeDatabase()
            deleteDatabaseArtifacts()
        }

        assertNoRecordAfter(
            mutate = { provider ->
                provider.medicationPlans.save(
                    plan().copy(slots = listOf(plan().slots.last().copy(position = 0)))
                )
            },
            expected = NotificationActionOutcome.Invalid
        )
        assertNoRecordAfter(
            mutate = { provider ->
                provider.medicationPlans.save(plan().copy(isEnabled = false))
            },
            expected = NotificationActionOutcome.StalePlan
        )
        assertNoRecordAfter(
            mutate = { provider -> provider.medicationPlans.delete(PLAN_ID) },
            expected = NotificationActionOutcome.StalePlan
        )
    }

    @Test
    fun conflictAndStorageFailureLeaveRowsUnchangedAndRunNoSuccessSideEffects() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val reminderId = reminderDoseEventId(PLAN_ID, scheduledAtMillis(FIRST_SLOT_TIME))
        val collision = event(
            reminderId,
            DoseEventSource.MANUAL,
            NOTIFICATION_OCCURRED_AT.minusSeconds(24L * 60L * 60L)
        )
        assertEquals(InsertResult.Inserted, provider.doseEvents.insert(collision))
        val notificationEffects = NotificationEffects()
        val notificationResult = ContractNotificationActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = notificationEffects,
            clock = Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        ).handle(
            NotificationActionCommand(PLAN_ID, 78, scheduledAtMillis(FIRST_SLOT_TIME))
        )
        assertEquals(NotificationActionOutcome.Conflict, notificationResult)
        assertEquals(collision, provider.doseEvents.getById(reminderId))
        assertEquals(0, notificationEffects.refreshes)
        assertEquals(0, notificationEffects.cancellations)

        val widgetId = widgetOccurrenceActionEventId(widgetOccurrenceId(FIRST_SLOT_TIME))
        opened.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER batch6b_widget_insert_failure
            BEFORE INSERT ON dose_events
            WHEN NEW.id = '$widgetId'
            BEGIN
                SELECT RAISE(ABORT, 'synthetic Widget insert failure');
            END
            """.trimIndent()
        )
        val widgetEffects = WidgetEffects()
        val widgetResult = ContractWidgetQuickActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = widgetEffects,
            clock = Clock.fixed(WIDGET_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        ).handle(widgetCommand(FIRST_SLOT_TIME))
        assertEquals(WidgetQuickActionOutcome.StorageFailure, widgetResult)
        assertNull(provider.doseEvents.getById(widgetId))
        assertEquals(0, widgetEffects.refreshes)
        assertEquals(0, widgetEffects.toasts)
        assertEquals(1, rawEventCount())
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertSingleDisposableDatabase()
    }

    @Test
    fun wearRecorderUsesStoredEventBeforePlanAndPreservesRepositoryEquality() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val actionId = UUID(0L, 820L)
        var materializerCalls = 0
        val firstResult = WearActionRecorder(
            provider.medicationPlans,
            provider.doseEvents
        ).record(PLAN_ID, actionId, WEAR_OCCURRED_AT) { materializedPlan ->
            materializerCalls += 1
            wearEvent(materializedPlan, actionId, WEAR_OCCURRED_AT)
        }
        val first = firstResult as RecordDoseEventActionResult.Accepted
        assertEquals(RecordAcceptance.Inserted, first.acceptance)
        assertEquals(1, materializerCalls)
        assertEquals(actionId, first.event.id)
        assertEquals(WEAR_OCCURRED_AT, first.event.occurredAt)

        assertEquals(
            PlanSaveResult.Updated,
            provider.medicationPlans.save(plan().copy(name = "Edited synthetic plan"))
        )
        val replayPlans = CountingMedicationPlanRepository(provider.medicationPlans)
        var replayMaterializerCalls = 0
        val replay = WearActionRecorder(replayPlans, provider.doseEvents).record(
            PLAN_ID,
            actionId,
            WEAR_OCCURRED_AT
        ) {
            replayMaterializerCalls += 1
            wearEvent(it, actionId, WEAR_OCCURRED_AT)
        } as RecordDoseEventActionResult.Accepted
        assertEquals(RecordAcceptance.FirstAcceptedReplay, replay.acceptance)
        assertEquals(first.event, replay.event)
        assertNull(replay.plan)
        assertEquals(0, replayPlans.getCalls)
        assertEquals(0, replayMaterializerCalls)

        val conflictPlans = CountingMedicationPlanRepository(provider.medicationPlans)
        var conflictMaterializerCalls = 0
        val conflict = WearActionRecorder(conflictPlans, provider.doseEvents).record(
            PLAN_ID,
            actionId,
            WEAR_OCCURRED_AT.plusMillis(1)
        ) {
            conflictMaterializerCalls += 1
            wearEvent(it, actionId, WEAR_OCCURRED_AT.plusMillis(1))
        }
        assertEquals(RecordDoseEventActionResult.Conflict, conflict)
        assertEquals(0, conflictPlans.getCalls)
        assertEquals(0, conflictMaterializerCalls)
        assertEquals(first.event, provider.doseEvents.getById(actionId))

        assertEquals(InsertResult.Idempotent, provider.doseEvents.insert(first.event))
        assertEquals(
            InsertResult.Conflict,
            provider.doseEvents.insert(first.event.copy(doseMG = first.event.doseMG + 1.0))
        )
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertSingleDisposableDatabase()
    }

    @Test
    fun concurrentWearActionsKeepOneAuthoritativeRow() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val sameActionId = UUID(0L, 821L)
        val recorder = WearActionRecorder(provider.medicationPlans, provider.doseEvents)
        val sameOutcomes = coroutineScope {
            List(2) {
                async(Dispatchers.IO) {
                    recorder.record(PLAN_ID, sameActionId, WEAR_OCCURRED_AT) { materializedPlan ->
                        wearEvent(materializedPlan, sameActionId, WEAR_OCCURRED_AT)
                    }
                }
            }.awaitAll()
        }
        assertTrue(sameOutcomes.all { it is RecordDoseEventActionResult.Accepted })
        assertEquals(
            1,
            sameOutcomes.count {
                (it as RecordDoseEventActionResult.Accepted).acceptance == RecordAcceptance.Inserted
            }
        )
        assertEquals(WEAR_OCCURRED_AT, provider.doseEvents.getById(sameActionId)?.occurredAt)

        val conflictingActionId = UUID(0L, 822L)
        val conflictingOccurrences = listOf(WEAR_OCCURRED_AT, WEAR_OCCURRED_AT.plusMillis(1))
        val conflictingOutcomes = coroutineScope {
            conflictingOccurrences.map { recordedAt ->
                async(Dispatchers.IO) {
                    recorder.record(PLAN_ID, conflictingActionId, recordedAt) { materializedPlan ->
                        wearEvent(materializedPlan, conflictingActionId, recordedAt)
                    }
                }
            }.awaitAll()
        }
        assertEquals(1, conflictingOutcomes.count { it is RecordDoseEventActionResult.Accepted })
        assertEquals(1, conflictingOutcomes.count { it == RecordDoseEventActionResult.Conflict })
        assertTrue(
            provider.doseEvents.getById(conflictingActionId)?.occurredAt in conflictingOccurrences
        )
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertSingleDisposableDatabase()
    }

    private fun plan(): MedicationPlan = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic integration plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        slots = listOf(LocalTime.of(8, 30), LocalTime.of(20, 0)).mapIndexed { position, time ->
            val slotId = (ScheduledDoseSlotId.generate(PLAN_ID, position, time) as SlotIdResult.Success).id
            ScheduledDoseSlot(slotId, PLAN_ID, time, position)
        },
        daysOfWeek = setOf(DayOfWeek.FRIDAY),
        intervalDays = 1,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        createdAt = Instant.parse("2026-01-02T03:04:05Z")
    )

    private fun notificationCommand(localTime: LocalTime): NotificationActionCommand =
        NotificationActionCommand(
            planId = PLAN_ID,
            notificationId = 78,
            scheduledAtMillis = scheduledAtMillis(localTime)
        )

    private fun widgetCommand(localTime: LocalTime): WidgetQuickActionCommand {
        val slotId = plan().slots.single { it.localTime == localTime }.id
        return WidgetQuickActionCommand(
            planId = PLAN_ID.toString(),
            slotId = slotId.toString(),
            scheduledLocalDate = OCCURRENCE_DATE.toString(),
            occurrenceId = MedicationOccurrenceIdentity.derive(
                PLAN_ID,
                slotId,
                OCCURRENCE_DATE
            ).value.toString()
        )
    }

    private fun widgetOccurrenceId(localTime: LocalTime): UUID =
        UUID.fromString(requireNotNull(widgetCommand(localTime).occurrenceId))

    private fun occurrences(plan: MedicationPlan): List<MedicationOccurrence> =
        MedicationOccurrenceGenerator.generate(
            schedules = listOf(plan.toMedicationSchedule()),
            window = OccurrenceGenerationWindow(
                startInclusive = OCCURRENCE_DATE.atStartOfDay(TEST_ZONE).toInstant(),
                endExclusive = OCCURRENCE_DATE.plusDays(1L).atStartOfDay(TEST_ZONE).toInstant()
            ),
            zoneId = TEST_ZONE
        )

    private fun scheduledAtMillis(localTime: LocalTime): Long =
        OCCURRENCE_DATE.atTime(localTime).atZone(TEST_ZONE).toInstant().toEpochMilli()

    private fun event(
        id: UUID,
        source: DoseEventSource,
        occurredAt: Instant
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.ORAL,
        occurredAt = occurredAt,
        zoneId = TEST_ZONE,
        localDate = occurredAt.atZone(TEST_ZONE).toLocalDate(),
        doseMG = 2.0,
        ester = Ester.E2,
        source = source
    )

    private fun wearEvent(
        plan: MedicationPlan,
        id: UUID,
        occurredAt: Instant
    ): DoseEvent = DoseEvent(
        id = id,
        route = plan.route,
        occurredAt = occurredAt,
        zoneId = TEST_ZONE,
        localDate = occurredAt.atZone(TEST_ZONE).toLocalDate(),
        doseMG = plan.doseMG,
        ester = plan.ester,
        extras = plan.extras,
        slotId = null,
        source = DoseEventSource.WEAR,
        revision = 1L
    )

    private fun rawEventCount(): Int = requireNotNull(database)
        .openHelper.readableDatabase.query("SELECT COUNT(*) FROM dose_events")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        TEST_DATABASE
    ).build().also { database = it }

    private fun closeDatabase() {
        database?.close()
        database = null
    }

    private fun deleteDatabaseArtifacts() {
        context.deleteDatabase(TEST_DATABASE)
        databaseArtifacts().forEach { artifact ->
            if (artifact.exists()) assertTrue(artifact.delete())
        }
    }

    private fun databaseArtifacts(): List<File> {
        val path = context.getDatabasePath(TEST_DATABASE)
        return listOf(
            path,
            File(path.path + "-wal"),
            File(path.path + "-shm"),
            File(path.path + "-journal")
        )
    }

    private fun assertSingleDisposableDatabase() {
        val matches = context.databaseList().filter { it.startsWith(TEST_DATABASE_PREFIX) }
        val allowedArtifacts = setOf(
            TEST_DATABASE,
            "$TEST_DATABASE-wal",
            "$TEST_DATABASE-shm",
            "$TEST_DATABASE-journal"
        )
        assertTrue(TEST_DATABASE in matches)
        assertFalse(matches.any { it !in allowedArtifacts })
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "batch6b_receiver_widget_"
        const val TEST_DATABASE = "${TEST_DATABASE_PREFIX}test.db"
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val PLAN_ID: UUID = UUID(0L, 801L)
        val OCCURRENCE_DATE = java.time.LocalDate.of(2027, 1, 15)
        val FIRST_SLOT_TIME: LocalTime = LocalTime.of(8, 30)
        val SECOND_SLOT_TIME: LocalTime = LocalTime.of(20, 0)
        val INVALID_SLOT_TIME: LocalTime = LocalTime.of(12, 0)
        val NOTIFICATION_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(FIRST_SLOT_TIME).plusSeconds(30).plusNanos(123_000_000)
                .atZone(TEST_ZONE).toInstant()
        val NOTIFICATION_REPLAY_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(FIRST_SLOT_TIME).plusSeconds(31).plusNanos(456_000_000)
                .atZone(TEST_ZONE).toInstant()
        val NOTIFICATION_DELAYED_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(16, 30, 0, 123_000_000).atZone(TEST_ZONE).toInstant()
        val WIDGET_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(SECOND_SLOT_TIME).plusSeconds(30).plusNanos(456_000_000)
                .atZone(TEST_ZONE).toInstant()
        val WIDGET_REPLAY_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(SECOND_SLOT_TIME).plusSeconds(31).plusNanos(789_000_000)
                .atZone(TEST_ZONE).toInstant()
        val WEAR_OCCURRED_AT: Instant =
            OCCURRENCE_DATE.atTime(FIRST_SLOT_TIME).plusSeconds(32).plusNanos(123_000_000)
                .atZone(TEST_ZONE).toInstant()
    }
}

private class CountingMedicationPlanRepository(
    private val delegate: MedicationPlanRepository
) : MedicationPlanRepository by delegate {
    var getCalls = 0

    override suspend fun getById(id: UUID): MedicationPlan? {
        getCalls += 1
        return delegate.getById(id)
    }
}

private class NotificationEffects : NotificationActionSideEffects {
    var refreshes = 0
    var cancellations = 0
    val log = mutableListOf<String>()

    override suspend fun refreshWidgets() {
        refreshes += 1
        log += "refresh"
    }

    override fun cancelNotification(notificationId: Int) {
        cancellations += 1
        log += "cancel"
    }
}

private class WidgetEffects : WidgetQuickActionSideEffects {
    var refreshes = 0
    var toasts = 0

    override suspend fun refreshWidgets() {
        refreshes += 1
    }

    override suspend fun showRecorded(planName: String) {
        toasts += 1
    }
}
