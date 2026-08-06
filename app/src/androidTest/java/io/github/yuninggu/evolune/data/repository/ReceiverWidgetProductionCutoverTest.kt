package io.github.yuninggu.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.PlanSaveResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.core.model.MedicationPlan
import io.github.yuninggu.evolune.core.model.ScheduleType
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlot
import io.github.yuninggu.evolune.core.model.ScheduledDoseSlotId
import io.github.yuninggu.evolune.core.model.SlotIdResult
import io.github.yuninggu.evolune.data.AppDatabase
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.reminder.ContractNotificationActionWork
import io.github.yuninggu.evolune.reminder.NotificationActionCommand
import io.github.yuninggu.evolune.reminder.NotificationActionOutcome
import io.github.yuninggu.evolune.reminder.NotificationActionSideEffects
import io.github.yuninggu.evolune.reminder.reminderDoseEventId
import io.github.yuninggu.evolune.widget.ContractWidgetQuickActionWork
import io.github.yuninggu.evolune.widget.WidgetQuickActionCommand
import io.github.yuninggu.evolune.widget.WidgetQuickActionOutcome
import io.github.yuninggu.evolune.widget.WidgetQuickActionSideEffects
import io.github.yuninggu.evolune.widget.widgetDoseEventId
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
        assertEquals(
            NotificationActionOutcome.Accepted(true),
            notificationWork.handle(notificationCommand)
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
        val widgetCommand = WidgetQuickActionCommand(PLAN_ID.toString())
        assertEquals(WidgetQuickActionOutcome.Accepted(false), widgetWork.handle(widgetCommand))
        assertEquals(WidgetQuickActionOutcome.Accepted(true), widgetWork.handle(widgetCommand))
        val widgetId = widgetDoseEventId(PLAN_ID, WIDGET_OCCURRED_AT.toEpochMilli())
        val widget = requireNotNull(provider.doseEvents.getById(widgetId))
        assertEquals(DoseEventSource.WIDGET, widget.source)
        assertEquals(WIDGET_OCCURRED_AT, widget.occurredAt)
        assertEquals(TEST_ZONE, widget.zoneId)
        assertEquals(WIDGET_OCCURRED_AT.atZone(TEST_ZONE).toLocalDate(), widget.localDate)
        assertNull(widget.slotId)
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

        val widgetId = widgetDoseEventId(PLAN_ID, WIDGET_OCCURRED_AT.toEpochMilli())
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
        ).handle(WidgetQuickActionCommand(PLAN_ID.toString()))
        assertEquals(WidgetQuickActionOutcome.StorageFailure, widgetResult)
        assertNull(provider.doseEvents.getById(widgetId))
        assertEquals(0, widgetEffects.refreshes)
        assertEquals(0, widgetEffects.toasts)
        assertEquals(1, rawEventCount())
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
        val WIDGET_OCCURRED_AT: Instant = Instant.parse("2027-01-15T08:31:00.456Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
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
