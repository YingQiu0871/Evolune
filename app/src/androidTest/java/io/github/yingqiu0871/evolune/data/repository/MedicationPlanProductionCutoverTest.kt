package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.application.MedicationPlanDraft
import io.github.yingqiu0871.evolune.application.MedicationPlanEditSessionFactory
import io.github.yingqiu0871.evolune.application.DraftMappingResult
import io.github.yingqiu0871.evolune.application.toDomainMedicationPlan
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.reminder.MedicationPlanReminderScheduler
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperation
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationError
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationState
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MedicationPlanProductionCutoverTest {
    private lateinit var context: Context
    private var database: AppDatabase? = null
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun prepareDisposableDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none { it.exists() })
    }

    @After
    fun removeDisposableDatabase() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        closeDatabase()
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none { it.exists() })
    }

    @Test
    fun ViewModelProviderPathPersistsReopensEditsTogglesAndDeletesAggregate() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        val reminders = ReminderSpy()
        val viewModel = viewModel(provider.medicationPlans, reminders)

        assertTrue(provider.medicationPlans is RoomMedicationPlanRepository)
        viewModel.startCreateSession()
        val session = requireNotNull(viewModel.editSession.value)
        assertEquals(FIXED_PLAN_ID, session.id)
        assertEquals(CREATED_AT, session.createdAt)

        val initialDraft = draft(
            id = session.id,
            createdAt = session.createdAt,
            name = "Synthetic cutover plan",
            times = listOf(LocalTime.of(8, 30), LocalTime.of(23, 59))
        )
        assertSuccess(viewModel, MedicationPlanOperation.SAVE) {
            viewModel.saveDraft(initialDraft)
        }
        assertEquals(1, reminders.scheduleCalls)
        assertEquals("[\"08:30\",\"23:59\"]", rawTimeOfDay(FIXED_PLAN_ID))
        assertEquals(
            listOf(
                RawSlot(FIXED_SLOT_ID, "08:30", 0),
                RawSlot("d16b7a71-0fa9-523b-b0e5-ada70bf09c43", "23:59", 1)
            ),
            rawSlots(FIXED_PLAN_ID)
        )
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()

        val otherDraft = draft(
            id = OTHER_PLAN_ID,
            createdAt = CREATED_AT.plusSeconds(1),
            name = "Synthetic unaffected plan",
            times = listOf(LocalTime.NOON)
        )
        assertEquals(
            PlanSaveResult.Created,
            provider.medicationPlans.saveDomainDraft(otherDraft)
        )

        closeDatabase()
        val reopened = openDatabase()
        val reopenedProvider = ProductionRepositoryProvider(reopened)
        val reopenedReminders = ReminderSpy()
        val reopenedViewModel = viewModel(reopenedProvider.medicationPlans, reopenedReminders)
        val restored = requireNotNull(reopenedProvider.medicationPlans.getById(FIXED_PLAN_ID))
        assertEquals(initialDraft.name, restored.name)
        assertEquals(initialDraft.createdAt, restored.createdAt)
        assertEquals(initialDraft.times, restored.slots.map { it.localTime })

        reopenedViewModel.startEditSession(restored)
        assertEquals(restored.id, reopenedViewModel.editSession.value?.id)
        assertEquals(restored.createdAt, reopenedViewModel.editSession.value?.createdAt)
        val duplicateTimes = listOf(
            LocalTime.of(20, 0),
            LocalTime.of(8, 30),
            LocalTime.of(8, 30)
        )
        assertSuccess(reopenedViewModel, MedicationPlanOperation.SAVE) {
            reopenedViewModel.saveDraft(
                initialDraft.copy(
                    name = "Synthetic edited plan",
                    doseMG = 3.5,
                    times = duplicateTimes
                )
            )
        }
        val chronologicalDuplicateTimes = duplicateTimes.sorted()
        assertEquals(
            chronologicalDuplicateTimes,
            rawSlots(FIXED_PLAN_ID).map { LocalTime.parse(it.time) }
        )
        assertEquals("[\"08:30\",\"08:30\",\"20:00\"]", rawTimeOfDay(FIXED_PLAN_ID))

        assertSuccess(reopenedViewModel, MedicationPlanOperation.SAVE) {
            reopenedViewModel.saveDraft(initialDraft.copy(times = emptyList()))
        }
        assertEquals("[]", rawTimeOfDay(FIXED_PLAN_ID))
        assertTrue(rawSlots(FIXED_PLAN_ID).isEmpty())

        assertSuccess(reopenedViewModel, MedicationPlanOperation.SET_ENABLED) {
            reopenedViewModel.setPlanEnabled(FIXED_PLAN_ID, false)
        }
        assertFalse(requireNotNull(reopenedProvider.medicationPlans.getById(FIXED_PLAN_ID)).isEnabled)
        assertEquals(1, reopenedReminders.cancelCalls)

        val invalidId = UUID(0L, 909L)
        val reminderCallsBeforeInvalid = reopenedReminders.totalCalls
        val invalidState = awaitOperation(reopenedViewModel, MedicationPlanOperation.SAVE) {
            reopenedViewModel.saveDraft(
                draft(
                    id = invalidId,
                    createdAt = Instant.ofEpochSecond(1, 1),
                    name = "Synthetic non-millisecond plan",
                    times = listOf(LocalTime.of(9, 0))
                )
            )
        }
        assertEquals(
            MedicationPlanOperationState.Failure(
                MedicationPlanOperation.SAVE,
                MedicationPlanOperationError.RepositoryInvalid
            ),
            invalidState
        )
        assertNull(reopenedProvider.medicationPlans.getById(invalidId))
        assertEquals(reminderCallsBeforeInvalid, reopenedReminders.totalCalls)

        assertSuccess(reopenedViewModel, MedicationPlanOperation.DELETE) {
            reopenedViewModel.deletePlan(FIXED_PLAN_ID)
        }
        assertNull(reopenedProvider.medicationPlans.getById(FIXED_PLAN_ID))
        assertEquals(0, rawSlotCount(FIXED_PLAN_ID))
        assertEquals(otherDraft.name, reopenedProvider.medicationPlans.getById(OTHER_PLAN_ID)?.name)
        assertEquals(3, reopened.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()
    }

    @Test
    fun slotInsertFailureRollsBackPlanSlotsLegacyShadowAndReminder() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        val initialDraft = draft(
            name = "Synthetic rollback plan",
            times = listOf(LocalTime.of(8, 30), LocalTime.of(20, 0))
        )
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.saveDomainDraft(initialDraft))
        val before = requireNotNull(provider.medicationPlans.getById(FIXED_PLAN_ID))
        val beforeTimeOfDay = rawTimeOfDay(FIXED_PLAN_ID)
        val beforeSlots = rawSlots(FIXED_PLAN_ID)

        opened.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER batch5b_slot_failure
            BEFORE INSERT ON scheduled_dose_slots
            WHEN NEW.planId = '${FIXED_PLAN_ID}'
            BEGIN
                SELECT RAISE(ABORT, 'synthetic slot failure');
            END
            """.trimIndent()
        )

        val reminders = ReminderSpy()
        val viewModel = viewModel(provider.medicationPlans, reminders)
        val state = awaitOperation(viewModel, MedicationPlanOperation.SAVE) {
            viewModel.saveDraft(
                initialDraft.copy(
                    name = "Synthetic rollback edit",
                    doseMG = 5.0,
                    times = listOf(LocalTime.of(23, 59))
                )
            )
        }

        assertEquals(
            MedicationPlanOperationState.Failure(
                MedicationPlanOperation.SAVE,
                MedicationPlanOperationError.StorageFailure
            ),
            state
        )
        assertEquals(0, reminders.totalCalls)
        assertEquals(before, provider.medicationPlans.getById(FIXED_PLAN_ID))
        assertEquals(beforeTimeOfDay, rawTimeOfDay(FIXED_PLAN_ID))
        assertEquals(beforeSlots, rawSlots(FIXED_PLAN_ID))
        assertEquals(1, rawPlanCount(FIXED_PLAN_ID))
        assertEquals(3, opened.openHelper.readableDatabase.version)
    }

    @Test
    fun repositoryPersistsChronologicalPositionsWhileKeepingEditedSlotIdentity() = runBlocking {
        val opened = openDatabase()
        val repository = ProductionRepositoryProvider(opened).medicationPlans
        val initialDraft = draft(
            name = "Synthetic stable slot plan",
            times = listOf(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                LocalTime.of(22, 0)
            )
        )
        assertEquals(PlanSaveResult.Created, repository.saveDomainDraft(initialDraft))
        val initial = requireNotNull(repository.getById(FIXED_PLAN_ID))
        val initialIds = initial.slots.map { it.id }

        val editedDraft = initialDraft.copy(
            times = listOf(
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                LocalTime.of(8, 0)
            ),
            slotIds = initialIds
        )
        assertEquals(PlanSaveResult.Updated, repository.saveDomainDraft(editedDraft))

        val restored = requireNotNull(repository.getById(FIXED_PLAN_ID))
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(17, 0)),
            restored.slots.map { it.localTime }
        )
        assertEquals(
            listOf(initialIds[2], initialIds[0], initialIds[1]),
            restored.slots.map { it.id }
        )
        assertEquals(listOf(0, 1, 2), restored.slots.map { it.position })
        assertEquals("[\"08:00\",\"09:00\",\"17:00\"]", rawTimeOfDay(FIXED_PLAN_ID))
    }

    private fun viewModel(
        repository: MedicationPlanRepository,
        reminders: ReminderSpy
    ): MedicationPlanViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return MedicationPlanViewModel(
            repository = repository,
            reminderScheduler = reminders,
            sessionFactory = MedicationPlanEditSessionFactory(
                idSupplier = { FIXED_PLAN_ID },
                clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC)
            ),
            operationScope = scope
        )
    }

    private suspend fun assertSuccess(
        viewModel: MedicationPlanViewModel,
        operation: MedicationPlanOperation,
        action: () -> Unit
    ) {
        assertTrue(awaitOperation(viewModel, operation, action) is MedicationPlanOperationState.Success)
    }

    private suspend fun awaitOperation(
        viewModel: MedicationPlanViewModel,
        operation: MedicationPlanOperation,
        action: () -> Unit
    ): MedicationPlanOperationState {
        viewModel.acknowledgeOperation()
        action()
        return withTimeout(10_000L) {
            viewModel.operationState.filter { state ->
                when (state) {
                    is MedicationPlanOperationState.Success -> state.result.operation == operation
                    is MedicationPlanOperationState.Failure -> state.operation == operation
                    MedicationPlanOperationState.Idle,
                    is MedicationPlanOperationState.Running -> false
                }
            }.first()
        }
    }

    private suspend fun MedicationPlanRepository.saveDomainDraft(
        draft: MedicationPlanDraft
    ): PlanSaveResult = when (val result = draft.toDomainMedicationPlan()) {
        is DraftMappingResult.Success -> save(result.value)
        is DraftMappingResult.InvalidDraft -> error("synthetic fixture must be valid")
    }

    private fun draft(
        id: UUID = FIXED_PLAN_ID,
        createdAt: Instant = CREATED_AT,
        name: String,
        times: List<LocalTime>
    ): MedicationPlanDraft = MedicationPlanDraft(
        id = id,
        name = name,
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 1.5,
        scheduleType = ScheduleType.WEEKLY,
        times = times,
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        intervalDays = 3,
        isEnabled = true,
        extras = mapOf(
            ExtraKey.SUBLINGUAL_THETA to 0.4,
            ExtraKey.SUBLINGUAL_TIER to 2.0
        ),
        createdAt = createdAt
    )

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        TEST_DATABASE
    ).build().also { database = it }

    private fun closeDatabase() {
        database?.close()
        database = null
    }

    private fun rawTimeOfDay(planId: UUID): String = querySingleString(
        "SELECT timeOfDay FROM medication_plans WHERE id = ?",
        planId.toString()
    )

    private fun rawSlots(planId: UUID): List<RawSlot> =
        requireNotNull(database).openHelper.readableDatabase.query(
            """
            SELECT id, localTime, position
            FROM scheduled_dose_slots
            WHERE planId = ?
            ORDER BY position ASC
            """.trimIndent(),
            arrayOf(planId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(RawSlot(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
                }
            }
        }

    private fun rawSlotCount(planId: UUID): Int = querySingleInt(
        "SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = ?",
        planId.toString()
    )

    private fun rawPlanCount(planId: UUID): Int = querySingleInt(
        "SELECT COUNT(*) FROM medication_plans WHERE id = ?",
        planId.toString()
    )

    private fun querySingleString(sql: String, argument: String): String =
        requireNotNull(database).openHelper.readableDatabase.query(sql, arrayOf(argument)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun querySingleInt(sql: String, argument: String): Int =
        requireNotNull(database).openHelper.readableDatabase.query(sql, arrayOf(argument)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun deleteDatabaseArtifacts() {
        context.deleteDatabase(TEST_DATABASE)
        databaseArtifacts().forEach { artifact ->
            if (artifact.exists()) {
                assertTrue(artifact.delete())
            }
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

    private fun assertNoSecondTestDatabase() {
        val matching = context.databaseList().filter { it.startsWith(TEST_DATABASE_PREFIX) }
        assertTrue(TEST_DATABASE in matching)
        assertTrue(matching.all { it in expectedDatabaseArtifacts() })
    }

    private fun expectedDatabaseArtifacts(): Set<String> = setOf(
        TEST_DATABASE,
        "$TEST_DATABASE-wal",
        "$TEST_DATABASE-shm",
        "$TEST_DATABASE-journal"
    )

    private data class RawSlot(val id: String, val time: String, val position: Int)

    private class ReminderSpy : MedicationPlanReminderScheduler {
        var scheduleCalls = 0
        var cancelCalls = 0
        var rescheduleCalls = 0

        val totalCalls: Int
            get() = scheduleCalls + cancelCalls + rescheduleCalls

        override fun schedule(plan: MedicationPlan) {
            scheduleCalls += 1
        }

        override fun cancel(planId: UUID) {
            cancelCalls += 1
        }

        override suspend fun reschedule(plans: List<MedicationPlan>) {
            rescheduleCalls += 1
        }
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "batch5b_cutover_"
        const val TEST_DATABASE = "${TEST_DATABASE_PREFIX}test.db"
        val FIXED_PLAN_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val FIXED_SLOT_ID = "17d1fd14-9d70-5344-beaa-0b158c9f62f4"
        val OTHER_PLAN_ID: UUID = UUID(0L, 902L)
        val CREATED_AT: Instant = Instant.parse("2024-01-02T03:04:05.006Z")
    }
}
