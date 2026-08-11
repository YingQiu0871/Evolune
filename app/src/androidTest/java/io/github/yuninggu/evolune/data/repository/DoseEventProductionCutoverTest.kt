package io.github.yuninggu.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.application.DoseEventEditSessionFactory
import io.github.yuninggu.evolune.application.DoseEventEditorInput
import io.github.yuninggu.evolune.core.dataapi.InsertResult
import io.github.yuninggu.evolune.core.dataapi.UpdateResult
import io.github.yuninggu.evolune.core.model.DoseEvent
import io.github.yuninggu.evolune.core.model.DoseEventSource
import io.github.yuninggu.evolune.core.model.DoseEventStatus
import io.github.yuninggu.evolune.core.model.ExtraKey
import io.github.yuninggu.evolune.data.AppDatabase
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import io.github.yuninggu.evolune.viewmodel.DoseEventOperation
import io.github.yuninggu.evolune.viewmodel.DoseEventOperationError
import io.github.yuninggu.evolune.viewmodel.DoseEventOperationState
import io.github.yuninggu.evolune.viewmodel.HRTViewModel
import io.github.yuninggu.evolune.viewmodel.ImportResult
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DoseEventProductionCutoverTest {
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
        cancelScopes()
        closeDatabase()
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none { it.exists() })
    }

    @Test
    fun providerAndHrtPathPersistReopenCasImportConflictAndDelete() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        val viewModel = viewModel(provider)

        assertTrue(provider.doseEvents is RoomDoseEventRepository)
        viewModel.startCreateSession()
        val createSession = requireNotNull(viewModel.editSession.value)
        assertEquals(MANUAL_EVENT_ID, createSession.original.id)
        assertSuccess(viewModel, DoseEventOperation.CREATE) {
            viewModel.saveEvent(editorInput(doseMG = 2.0))
        }
        val created = requireNotNull(provider.doseEvents.getById(MANUAL_EVENT_ID))
        assertEquals(MANUAL_OCCURRED_AT, created.occurredAt)
        assertEquals(TEST_ZONE, created.zoneId)
        assertEquals(LocalDate.of(2026, 1, 2), created.localDate)
        assertNull(created.slotId)
        assertEquals(DoseEventSource.MANUAL, created.source)
        assertEquals(DoseEventStatus.RECORDED, created.status)
        assertEquals(1L, created.revision)

        val wearableEvent = event(
            id = WEAR_EVENT_ID,
            source = DoseEventSource.WEAR,
            slotId = SLOT_ID,
            revision = 1L
        )
        assertEquals(InsertResult.Inserted, provider.doseEvents.insert(wearableEvent))
        assertEquals(InsertResult.Idempotent, provider.doseEvents.insert(wearableEvent))
        assertEquals(
            InsertResult.Conflict,
            provider.doseEvents.insert(wearableEvent.copy(doseMG = 9.0))
        )
        assertEquals(wearableEvent, provider.doseEvents.getById(WEAR_EVENT_ID))
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()

        cancelScopes()
        closeDatabase()
        val reopened = openDatabase()
        val reopenedProvider = ProductionRepositoryProvider(reopened)
        val reopenedViewModel = viewModel(reopenedProvider, id = JSON_EVENT_ID)
        assertEquals(created, reopenedProvider.doseEvents.getById(MANUAL_EVENT_ID))
        val restoredWear = requireNotNull(reopenedProvider.doseEvents.getById(WEAR_EVENT_ID))

        reopenedViewModel.startEditSession(restoredWear)
        assertSuccess(reopenedViewModel, DoseEventOperation.UPDATE) {
            reopenedViewModel.saveEvent(editorInput(doseMG = 3.5))
        }
        val updated = requireNotNull(reopenedProvider.doseEvents.getById(WEAR_EVENT_ID))
        assertEquals(3.5, updated.doseMG, 0.0)
        assertEquals(2L, updated.revision)
        assertEquals(DoseEventSource.WEAR, updated.source)
        assertEquals(SLOT_ID, updated.slotId)
        assertEquals(restoredWear.zoneId, updated.zoneId)
        assertEquals(restoredWear.localDate, updated.localDate)
        assertEquals(restoredWear.extras, updated.extras)

        assertEquals(
            UpdateResult.RevisionConflict,
            reopenedProvider.doseEvents.update(restoredWear.copy(doseMG = 8.0), 1L)
        )
        assertEquals(updated, reopenedProvider.doseEvents.getById(WEAR_EVENT_ID))
        val missing = event(id = MISSING_EVENT_ID)
        assertEquals(
            UpdateResult.NotFound,
            reopenedProvider.doseEvents.update(missing, expectedRevision = 1L)
        )
        assertNull(reopenedProvider.doseEvents.getById(MISSING_EVENT_ID))

        val firstImport = awaitImportSuccess(reopenedViewModel) {
            reopenedViewModel.importFromMahiroJson(jsonV1())
        }
        assertEquals(1, firstImport.importedCount)
        assertEquals(0, firstImport.existingCount)
        assertEquals(0, firstImport.conflictCount)
        val imported = requireNotNull(reopenedProvider.doseEvents.getById(JSON_EVENT_ID))
        assertEquals(DoseEventSource.JSON_V1, imported.source)
        assertEquals(DoseEventStatus.RECORDED, imported.status)
        assertEquals(1L, imported.revision)
        assertNull(imported.zoneId)
        assertNull(imported.localDate)
        assertNull(imported.slotId)

        reopenedViewModel.dismissImportResult()
        val replay = awaitImportSuccess(reopenedViewModel) {
            reopenedViewModel.importFromMahiroJson(jsonV1())
        }
        assertEquals(1, replay.importedCount)
        assertEquals(1, replay.existingCount)
        assertEquals(0, replay.conflictCount)

        reopenedViewModel.dismissImportResult()
        val conflict = awaitImportSuccess(reopenedViewModel) {
            reopenedViewModel.importFromMahiroJson(jsonV1(doseMG = 9.0))
        }
        assertEquals(0, conflict.importedCount)
        assertEquals(0, conflict.existingCount)
        assertEquals(1, conflict.conflictCount)
        assertEquals(imported, reopenedProvider.doseEvents.getById(JSON_EVENT_ID))

        assertSuccess(reopenedViewModel, DoseEventOperation.DELETE) {
            reopenedViewModel.deleteEvent(MANUAL_EVENT_ID)
        }
        assertNull(reopenedProvider.doseEvents.getById(MANUAL_EVENT_ID))
        assertEquals(updated, reopenedProvider.doseEvents.getById(WEAR_EVENT_ID))
        assertEquals(3, reopened.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()
    }

    @Test
    fun repositoryUpdateFailureRollsBackEveryEventFieldAndHrtReportsStorageFailure() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)
        val original = event(
            id = WEAR_EVENT_ID,
            source = DoseEventSource.WEAR,
            slotId = SLOT_ID,
            revision = 1L
        )
        assertEquals(InsertResult.Inserted, provider.doseEvents.insert(original))
        val before = rawEvent(WEAR_EVENT_ID)

        opened.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER batch6a_event_update_failure
            BEFORE UPDATE ON dose_events
            WHEN OLD.id = '${WEAR_EVENT_ID}'
            BEGIN
                SELECT RAISE(ABORT, 'synthetic event update failure');
            END
            """.trimIndent()
        )

        val viewModel = viewModel(provider)
        viewModel.startEditSession(original)
        val state = awaitOperation(viewModel, DoseEventOperation.UPDATE) {
            viewModel.saveEvent(editorInput(doseMG = 7.0))
        }

        assertEquals(
            DoseEventOperationState.Failure(
                DoseEventOperation.UPDATE,
                DoseEventOperationError.StorageFailure
            ),
            state
        )
        assertEquals(before, rawEvent(WEAR_EVENT_ID))
        assertEquals(original, provider.doseEvents.getById(WEAR_EVENT_ID))
        assertEquals(1, rawEventCount(WEAR_EVENT_ID))
        assertEquals(3, opened.openHelper.readableDatabase.version)
    }

    private fun viewModel(
        provider: ProductionRepositoryProvider,
        id: UUID = MANUAL_EVENT_ID
    ): HRTViewModel {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scopes += scope
        return HRTViewModel(
            repository = provider.doseEvents,
            medicationPlanRepository = provider.medicationPlans,
            sessionFactory = DoseEventEditSessionFactory(
                idSupplier = { id },
                clock = Clock.fixed(MANUAL_OCCURRED_AT, ZoneOffset.UTC),
                zoneIdSupplier = { TEST_ZONE }
            ),
            clock = Clock.fixed(MANUAL_OCCURRED_AT, ZoneOffset.UTC),
            operationScope = scope
        )
    }

    private suspend fun assertSuccess(
        viewModel: HRTViewModel,
        operation: DoseEventOperation,
        action: () -> Unit
    ) {
        assertTrue(awaitOperation(viewModel, operation, action) is DoseEventOperationState.Success)
    }

    private suspend fun awaitImportSuccess(
        viewModel: HRTViewModel,
        action: () -> Unit
    ): ImportResult.Success {
        assertSuccess(viewModel, DoseEventOperation.IMPORT, action)
        return viewModel.importResult.value as ImportResult.Success
    }

    private suspend fun awaitOperation(
        viewModel: HRTViewModel,
        operation: DoseEventOperation,
        action: () -> Unit
    ): DoseEventOperationState {
        viewModel.acknowledgeOperation()
        action()
        return withTimeout(10_000L) {
            viewModel.operationState.filter { state ->
                when (state) {
                    is DoseEventOperationState.Success -> state.operation == operation
                    is DoseEventOperationState.Failure -> state.operation == operation
                    DoseEventOperationState.Idle,
                    is DoseEventOperationState.Running -> false
                }
            }.first()
        }
    }

    private fun editorInput(doseMG: Double): DoseEventEditorInput = DoseEventEditorInput(
        occurredAt = MANUAL_OCCURRED_AT,
        occurredAtEdited = false,
        route = Route.SUBLINGUAL,
        doseMG = doseMG,
        ester = Ester.E2,
        extras = mapOf(
            ExtraKey.SUBLINGUAL_THETA to 0.4,
            ExtraKey.SUBLINGUAL_TIER to 2.0
        )
    )

    private fun event(
        id: UUID,
        source: DoseEventSource = DoseEventSource.MANUAL,
        slotId: UUID? = null,
        revision: Long = 1L
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.SUBLINGUAL,
        occurredAt = WEAR_OCCURRED_AT,
        zoneId = ZoneId.of("Europe/Berlin"),
        localDate = LocalDate.of(2024, 1, 2),
        doseMG = 2.0,
        ester = Ester.E2,
        extras = mapOf(
            ExtraKey.SUBLINGUAL_THETA to 0.4,
            ExtraKey.SUBLINGUAL_TIER to 2.0
        ),
        slotId = slotId,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = revision
    )

    private fun jsonV1(doseMG: Double = 2.0): String = """
        {
          "weight": 55,
          "events": [{
            "id":"$JSON_EVENT_ID",
            "route":"oral",
            "ester":"E2",
            "timeH":1.0,
            "doseMG":$doseMG,
            "extras":{}
          }]
        }
    """.trimIndent()

    private fun rawEvent(id: UUID): RawEvent = requireNotNull(database)
        .openHelper.readableDatabase.query(
            """
            SELECT route, timeH, doseMG, ester, extras, occurredAtEpochMillis,
                zoneId, localDate, slotId, source, status, revision
            FROM dose_events WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            RawEvent(
                route = cursor.getString(0),
                timeH = cursor.getDouble(1),
                doseMG = cursor.getDouble(2),
                ester = cursor.getString(3),
                extras = cursor.getString(4),
                occurredAtEpochMillis = cursor.getLong(5),
                zoneId = if (cursor.isNull(6)) null else cursor.getString(6),
                localDate = if (cursor.isNull(7)) null else cursor.getString(7),
                slotId = if (cursor.isNull(8)) null else cursor.getString(8),
                source = cursor.getString(9),
                status = cursor.getString(10),
                revision = cursor.getLong(11)
            )
        }

    private fun rawEventCount(id: UUID): Int = requireNotNull(database)
        .openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM dose_events WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun openDatabase(): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        TEST_DATABASE
    ).build().also { database = it }

    private fun cancelScopes() {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    private fun closeDatabase() {
        database?.close()
        database = null
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

    private data class RawEvent(
        val route: String,
        val timeH: Double,
        val doseMG: Double,
        val ester: String,
        val extras: String,
        val occurredAtEpochMillis: Long,
        val zoneId: String?,
        val localDate: String?,
        val slotId: String?,
        val source: String,
        val status: String,
        val revision: Long
    )

    private companion object {
        const val TEST_DATABASE_PREFIX = "batch6a_cutover_"
        const val TEST_DATABASE = "${TEST_DATABASE_PREFIX}test.db"
        val MANUAL_EVENT_ID: UUID = UUID(0L, 601L)
        val WEAR_EVENT_ID: UUID = UUID(0L, 602L)
        val JSON_EVENT_ID: UUID = UUID(0L, 603L)
        val MISSING_EVENT_ID: UUID = UUID(0L, 604L)
        val SLOT_ID: UUID = UUID(0L, 605L)
        val MANUAL_OCCURRED_AT: Instant = Instant.parse("2026-01-02T03:04:05.678Z")
        val WEAR_OCCURRED_AT: Instant = Instant.parse("2024-01-02T03:04:05.006Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}
