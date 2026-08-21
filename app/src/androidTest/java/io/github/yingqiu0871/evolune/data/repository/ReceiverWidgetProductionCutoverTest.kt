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
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.experience.MedicationOccurrenceIdentity
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
            scheduledAtMillis = SCHEDULED_AT_MILLIS
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
        val reminderId = reminderDoseEventId(PLAN_ID, SCHEDULED_AT_MILLIS)
        val reminder = requireNotNull(provider.doseEvents.getById(reminderId))
        assertEquals(DoseEventSource.REMINDER, reminder.source)
        assertEquals(NOTIFICATION_OCCURRED_AT, reminder.occurredAt)
        assertEquals(TEST_ZONE, reminder.zoneId)
        assertEquals(NOTIFICATION_OCCURRED_AT.atZone(TEST_ZONE).toLocalDate(), reminder.localDate)
        assertNull(reminder.slotId)
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
        val widgetCommand = widgetCommand()
        assertEquals(WidgetQuickActionOutcome.Accepted(false), widgetWork.handle(widgetCommand))
        val replayWidgetWork = ContractWidgetQuickActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = widgetEffects,
            clock = Clock.fixed(WIDGET_REPLAY_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        )
        assertEquals(WidgetQuickActionOutcome.Accepted(true), replayWidgetWork.handle(widgetCommand))
        val widgetId = widgetOccurrenceActionEventId(widgetOccurrenceId())
        val widget = requireNotNull(provider.doseEvents.getById(widgetId))
        assertEquals(DoseEventSource.WIDGET, widget.source)
        assertEquals(WIDGET_OCCURRED_AT, widget.occurredAt)
        assertEquals(TEST_ZONE, widget.zoneId)
        assertEquals(WIDGET_OCCURRED_AT.atZone(TEST_ZONE).toLocalDate(), widget.localDate)
        assertEquals(plan().slots.first().id, widget.slotId)
        assertEquals(1L, widget.revision)
        assertEquals(2, widgetEffects.refreshes)
        assertEquals(2, widgetEffects.toasts)
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
    fun conflictAndStorageFailureLeaveRowsUnchangedAndRunNoSuccessSideEffects() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val reminderId = reminderDoseEventId(PLAN_ID, SCHEDULED_AT_MILLIS)
        val collision = event(reminderId, DoseEventSource.MANUAL, NOTIFICATION_OCCURRED_AT)
        assertEquals(InsertResult.Inserted, provider.doseEvents.insert(collision))
        val notificationEffects = NotificationEffects()
        val notificationResult = ContractNotificationActionWork(
            medicationPlans = provider.medicationPlans,
            doseEvents = provider.doseEvents,
            sideEffects = notificationEffects,
            clock = Clock.fixed(NOTIFICATION_OCCURRED_AT, ZoneOffset.UTC),
            zoneId = { TEST_ZONE }
        ).handle(
            NotificationActionCommand(PLAN_ID, 78, SCHEDULED_AT_MILLIS)
        )
        assertEquals(NotificationActionOutcome.Conflict, notificationResult)
        assertEquals(collision, provider.doseEvents.getById(reminderId))
        assertEquals(0, notificationEffects.refreshes)
        assertEquals(0, notificationEffects.cancellations)

        val widgetId = widgetOccurrenceActionEventId(widgetOccurrenceId())
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
        ).handle(widgetCommand())
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
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        intervalDays = 1,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        createdAt = Instant.parse("2026-01-02T03:04:05Z")
    )

    private fun widgetCommand(): WidgetQuickActionCommand {
        val scheduledLocalDate = WIDGET_OCCURRED_AT.atZone(TEST_ZONE).toLocalDate()
        val slotId = plan().slots.first().id
        return WidgetQuickActionCommand(
            planId = PLAN_ID.toString(),
            slotId = slotId.toString(),
            scheduledLocalDate = scheduledLocalDate.toString(),
            occurrenceId = MedicationOccurrenceIdentity.derive(
                PLAN_ID,
                slotId,
                scheduledLocalDate
            ).value.toString()
        )
    }

    private fun widgetOccurrenceId(): UUID =
        UUID.fromString(requireNotNull(widgetCommand().occurrenceId))

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
        const val SCHEDULED_AT_MILLIS = 1_800_000_000_000L
        val PLAN_ID: UUID = UUID(0L, 801L)
        val NOTIFICATION_OCCURRED_AT: Instant = Instant.parse("2027-01-15T08:30:00.123Z")
        val NOTIFICATION_REPLAY_OCCURRED_AT: Instant =
            Instant.parse("2027-01-15T08:30:30.456Z")
        val WIDGET_OCCURRED_AT: Instant = Instant.parse("2027-01-15T08:31:00.456Z")
        val WIDGET_REPLAY_OCCURRED_AT: Instant = Instant.parse("2027-01-15T08:31:01.789Z")
        val WEAR_OCCURRED_AT: Instant = Instant.parse("2027-01-15T08:32:00.123Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
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

    override suspend fun refreshWidgets() {
        refreshes += 1
    }

    override fun cancelNotification(notificationId: Int) {
        cancellations += 1
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
