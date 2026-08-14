package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.application.MedicationPlanDraft
import io.github.yingqiu0871.evolune.application.MedicationPlanEditSession
import io.github.yingqiu0871.evolune.application.MedicationPlanEditSessionFactory
import io.github.yingqiu0871.evolune.application.MedicationPlanEditorInput
import io.github.yingqiu0871.evolune.application.MedicationPlanInputResult
import io.github.yingqiu0871.evolune.application.toDomainMedicationPlan
import io.github.yingqiu0871.evolune.application.toMedicationPlanDraft
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3
import io.github.yingqiu0871.evolune.pk.AntiAndrogen
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import io.github.yingqiu0871.evolune.pk.SublingualTier
import io.github.yingqiu0871.evolune.reminder.MedicationPlanReminderScheduler
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperation
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationError
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationState
import io.github.yingqiu0871.evolune.viewmodel.MedicationPlanOperationSuccess
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlanSaveRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private var database: AppDatabase? = null
    private val scopes = mutableListOf<CoroutineScope>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun prepareDisposableDatabases() {
        context = instrumentation.targetContext
        DATABASES.forEach(::deleteDatabaseArtifacts)
    }

    @After
    fun removeDisposableDatabases() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        closeDatabase()
        DATABASES.forEach(::deleteDatabaseArtifacts)
    }

    @Test
    fun exactUserScenarioPersistsAndReopensInFreshV3() = runBlocking {
        exerciseExactUserScenario(FRESH_DATABASE, expectLegacyPlan = false)
    }

    @Test
    fun exactUserScenarioPersistsAndReopensAfterSyntheticV2Migration() = runBlocking {
        migrationHelper.createDatabase(MIGRATED_DATABASE, 2).use { v2 ->
            insertLegacyPlan(v2)
        }
        migrationHelper.runMigrationsAndValidate(
            MIGRATED_DATABASE,
            3,
            true,
            MIGRATION_2_3
        ).close()

        exerciseExactUserScenario(MIGRATED_DATABASE, expectLegacyPlan = true)
    }

    @Test
    fun existingPlanIdUsesDocumentedUpdatePathWithoutDuplicatingRows() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase(FRESH_DATABASE))
        val initial = unrelatedDraft().copy(
            id = EXACT_PLAN_ID,
            name = "Synthetic existing collision",
            times = listOf(LocalTime.of(6, 0))
        ).toDomain()
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(initial))

        val viewModel = viewModel(provider, ReminderSpy())
        viewModel.startCreateSession()
        val state = awaitSave(viewModel) {
            viewModel.saveDraft(exactInput().toDraft(requireNotNull(viewModel.editSession.value)))
        }

        val saved = (state as MedicationPlanOperationState.Success).result
            as MedicationPlanOperationSuccess.Saved
        assertEquals(PlanSaveResult.Updated, saved.repositoryResult)
        assertEquals("E2", provider.medicationPlans.getById(EXACT_PLAN_ID)?.name)
        assertEquals(1, rawPlanCount(EXACT_PLAN_ID))
    }

    @Test
    fun slotIdCollisionRollsBackPlanAndMapsStorageFailure() = runBlocking {
        val opened = openDatabase(FRESH_DATABASE)
        insertBlockingSlotCollision(opened)
        val provider = ProductionRepositoryProvider(opened)
        val reminders = ReminderSpy()
        val viewModel = viewModel(provider, reminders)
        viewModel.startCreateSession()

        val state = awaitSave(viewModel) {
            viewModel.saveDraft(exactInput().toDraft(requireNotNull(viewModel.editSession.value)))
        }

        assertEquals(
            MedicationPlanOperationState.Failure(
                MedicationPlanOperation.SAVE,
                MedicationPlanOperationError.StorageFailure
            ),
            state
        )
        assertNull(provider.medicationPlans.getById(EXACT_PLAN_ID))
        assertEquals(0, rawPlanCount(EXACT_PLAN_ID))
        assertEquals(1, rawSlotCount(BLOCKING_PLAN_ID))
        assertEquals(0, reminders.scheduleCalls)
    }

    private suspend fun exerciseExactUserScenario(
        databaseName: String,
        expectLegacyPlan: Boolean
    ) {
        val opened = openDatabase(databaseName)
        val provider = ProductionRepositoryProvider(opened)
        val reminders = ReminderSpy()
        val viewModel = viewModel(provider, reminders)

        viewModel.startCreateSession()
        val session = requireNotNull(viewModel.editSession.value)
        val draft = exactInput().toDraft(session)
        val domain = draft.toDomain()
        assertEquals(EXACT_TIMES, domain.slots.map { it.localTime })
        assertEquals(listOf(0, 1, 2), domain.slots.map { it.position })
        assertEquals(3, domain.slots.map { it.id }.distinct().size)
        domain.slots.forEach { slot ->
            val repeated = ScheduledDoseSlotId.generate(
                domain.id,
                slot.position,
                slot.localTime
            ) as SlotIdResult.Success
            assertEquals(repeated.id, slot.id)
        }

        val state = awaitSave(viewModel) { viewModel.saveDraft(draft) }
        assertTrue(state is MedicationPlanOperationState.Success)
        val saved = (state as MedicationPlanOperationState.Success).result
            as MedicationPlanOperationSuccess.Saved
        assertEquals(PlanSaveResult.Created, saved.repositoryResult)
        assertEquals(1, reminders.scheduleCalls)

        viewModel.closeEditSession()
        assertNull(viewModel.editSession.value)
        val stored = requireNotNull(provider.medicationPlans.getById(EXACT_PLAN_ID))
        assertEquals("E2", stored.name)
        assertEquals(Route.ORAL, stored.route)
        assertEquals(Ester.E2, stored.ester)
        assertEquals(2.0, stored.doseMG, 0.0)
        assertEquals(ScheduleType.DAILY, stored.scheduleType)
        assertEquals(EXACT_TIMES, stored.slots.map { it.localTime })
        assertEquals(PERSISTED_CREATED_AT, stored.createdAt)

        val unrelated = unrelatedDraft().toDomain()
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(unrelated))

        closeDatabase()
        val reopenedProvider = ProductionRepositoryProvider(openDatabase(databaseName))
        val reopenedExact = requireNotNull(reopenedProvider.medicationPlans.getById(EXACT_PLAN_ID))
        assertEquals(EXACT_TIMES, reopenedExact.slots.map { it.localTime })
        assertEquals(PERSISTED_CREATED_AT, reopenedExact.createdAt)
        assertEquals(
            listOf(LocalTime.of(8, 0)),
            requireNotNull(reopenedProvider.medicationPlans.getById(UNRELATED_PLAN_ID))
                .slots.map { it.localTime }
        )
        assertEquals(expectLegacyPlan, reopenedProvider.medicationPlans.getById(LEGACY_PLAN_ID) != null)
        assertEquals(3, requireNotNull(database).openHelper.readableDatabase.version)
    }

    private fun viewModel(
        provider: ProductionRepositoryProvider,
        reminders: ReminderSpy
    ): MedicationPlanViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return MedicationPlanViewModel(
            repository = provider.medicationPlans,
            reminderScheduler = reminders,
            sessionFactory = MedicationPlanEditSessionFactory(
                idSupplier = { EXACT_PLAN_ID },
                clock = Clock.fixed(DEVICE_CLOCK_INSTANT, ZoneOffset.UTC)
            ),
            operationScope = scope
        )
    }

    private suspend fun awaitSave(
        viewModel: MedicationPlanViewModel,
        action: () -> Unit
    ): MedicationPlanOperationState {
        action()
        return withTimeout(10_000L) {
            viewModel.operationState.filter { state ->
                when (state) {
                    is MedicationPlanOperationState.Success ->
                        state.result.operation == MedicationPlanOperation.SAVE
                    is MedicationPlanOperationState.Failure ->
                        state.operation == MedicationPlanOperation.SAVE
                    MedicationPlanOperationState.Idle,
                    is MedicationPlanOperationState.Running -> false
                }
            }.first()
        }
    }

    private fun exactInput(): MedicationPlanEditorInput = MedicationPlanEditorInput(
        name = "E2",
        route = Route.ORAL,
        ester = Ester.E2,
        selectedAntiAndrogen = AntiAndrogen.CPA,
        doseMGText = "2",
        scheduleType = ScheduleType.DAILY,
        times = EXACT_TIMES,
        daysOfWeek = emptySet(),
        intervalDaysText = "1",
        isEnabled = true,
        sublingualTier = SublingualTier.STANDARD
    )

    private fun MedicationPlanEditorInput.toDraft(
        session: MedicationPlanEditSession
    ): MedicationPlanDraft {
        val result = toMedicationPlanDraft(session)
        assertTrue(result is MedicationPlanInputResult.Success)
        return (result as MedicationPlanInputResult.Success).draft
    }

    private fun unrelatedDraft(): MedicationPlanDraft = MedicationPlanDraft(
        id = UNRELATED_PLAN_ID,
        name = "Synthetic unrelated plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        times = listOf(LocalTime.of(8, 0)),
        daysOfWeek = emptySet(),
        intervalDays = 1,
        isEnabled = true,
        extras = emptyMap(),
        createdAt = PERSISTED_CREATED_AT.plusMillis(1)
    )

    private fun MedicationPlanDraft.toDomain(): MedicationPlan =
        when (val result = toDomainMedicationPlan()) {
            is io.github.yingqiu0871.evolune.application.DraftMappingResult.Success -> result.value
            is io.github.yingqiu0871.evolune.application.DraftMappingResult.InvalidDraft ->
                error("synthetic draft must be valid")
        }

    private fun openDatabase(name: String): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        name
    ).addMigrations(MIGRATION_2_3).build().also { database = it }

    private fun closeDatabase() {
        database?.close()
        database = null
    }

    private fun insertLegacyPlan(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO medication_plans(
                id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                LEGACY_PLAN_ID.toString(),
                "Synthetic legacy plan",
                "ORAL",
                "E2",
                1.0,
                "DAILY",
                "[\"06:30\"]",
                "[]",
                1,
                1,
                "{}",
                0L
            )
        )
    }

    private fun insertBlockingSlotCollision(database: AppDatabase) {
        val slotId = (ScheduledDoseSlotId.generate(
            EXACT_PLAN_ID,
            0,
            EXACT_TIMES.first()
        ) as SlotIdResult.Success).id
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO medication_plans(
                id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                BLOCKING_PLAN_ID.toString(),
                "Synthetic slot collision owner",
                "ORAL",
                "E2",
                1.0,
                "DAILY",
                "[\"08:00\"]",
                "[]",
                1,
                1,
                "{}",
                0L
            )
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO scheduled_dose_slots(id, planId, localTime, position)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(slotId.toString(), BLOCKING_PLAN_ID.toString(), "08:00", 0)
        )
    }

    private fun rawPlanCount(planId: UUID): Int = queryCount(
        "SELECT COUNT(*) FROM medication_plans WHERE id = ?",
        planId
    )

    private fun rawSlotCount(planId: UUID): Int = queryCount(
        "SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = ?",
        planId
    )

    private fun queryCount(sql: String, planId: UUID): Int =
        requireNotNull(database).openHelper.readableDatabase.query(
            sql,
            arrayOf(planId.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun deleteDatabaseArtifacts(name: String) {
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        listOf(
            path,
            File(path.path + "-wal"),
            File(path.path + "-shm"),
            File(path.path + "-journal")
        ).forEach { artifact ->
            if (artifact.exists()) {
                assertTrue(artifact.delete())
            }
        }
        assertFalse(path.exists())
    }

    private class ReminderSpy : MedicationPlanReminderScheduler {
        var scheduleCalls = 0

        override fun schedule(plan: MedicationPlan) {
            scheduleCalls += 1
        }

        override fun cancel(planId: UUID) = Unit

        override suspend fun reschedule(plans: List<MedicationPlan>) = Unit
    }

    private companion object {
        const val FRESH_DATABASE = "plan-save-regression-fresh.db"
        const val MIGRATED_DATABASE = "plan-save-regression-migrated.db"
        val DATABASES = listOf(FRESH_DATABASE, MIGRATED_DATABASE)
        val EXACT_PLAN_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")
        val UNRELATED_PLAN_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000002")
        val LEGACY_PLAN_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000003")
        val BLOCKING_PLAN_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000004")
        val DEVICE_CLOCK_INSTANT: Instant = Instant.parse("2026-08-07T01:02:03.123456789Z")
        val PERSISTED_CREATED_AT: Instant = Instant.parse("2026-08-07T01:02:03.123Z")
        val EXACT_TIMES = listOf(
            LocalTime.of(1, 0),
            LocalTime.of(9, 0),
            LocalTime.of(17, 0)
        )
    }
}
