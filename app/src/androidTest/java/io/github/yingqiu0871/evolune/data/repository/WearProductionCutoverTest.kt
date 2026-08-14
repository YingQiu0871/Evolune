package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.application.RecordAcceptance
import io.github.yingqiu0871.evolune.application.WearDoseActionHandler
import io.github.yingqiu0871.evolune.application.WearDoseActionOutcome
import io.github.yingqiu0871.evolune.application.WearDoseActionPayload
import io.github.yingqiu0871.evolune.application.createWearDoseEvent
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
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
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class WearProductionCutoverTest {
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
    fun `production Wear handler persists reopens and replays without plan lookup`() =
        runBlocking {
            val provider = ProductionRepositoryProvider(openDatabase())
            assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
            val first = handler(provider).handle(payload())
                as WearDoseActionOutcome.Accepted

            assertEquals(RecordAcceptance.Inserted, first.acceptance)
            assertEquals(ACTION_ID, first.event.id)
            assertEquals(RECORDED_AT, first.event.occurredAt)
            assertEquals(DoseEventSource.WEAR, first.event.source)
            assertEquals(1, rawEventCount())
            closeDatabase()

            val reopened = ProductionRepositoryProvider(openDatabase())
            assertEquals(
                PlanSaveResult.Updated,
                reopened.medicationPlans.save(plan().copy(name = "Edited after action"))
            )
            val countingPlans = WearCountingMedicationPlanRepository(reopened.medicationPlans)
            val replay = handler(
                provider = ProductionRepositoryProvider(
                    database = requireNotNull(database)
                ),
                medicationPlans = countingPlans
            ).handle(payload(planId = UUID(0L, 999L)))
                as WearDoseActionOutcome.Accepted

            assertEquals(RecordAcceptance.FirstAcceptedReplay, replay.acceptance)
            assertEquals(first.event, replay.event)
            assertEquals(0, countingPlans.getCalls)
            assertEquals(1, rawEventCount())
            assertEquals(3, requireNotNull(database).openHelper.readableDatabase.version)
            assertSingleDisposableDatabase()
        }

    @Test
    fun `production conflicts and missing plan preserve rows and retain DataItem`() =
        runBlocking {
            val provider = ProductionRepositoryProvider(openDatabase())
            assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
            val sourceCollision = createWearDoseEvent(
                plan(),
                ACTION_ID,
                RECORDED_AT,
                TEST_ZONE
            ).copy(source = DoseEventSource.MANUAL)
            assertEquals(
                io.github.yingqiu0871.evolune.core.dataapi.InsertResult.Inserted,
                provider.doseEvents.insert(sourceCollision)
            )
            var deletes = 0
        val countingPlans = WearCountingMedicationPlanRepository(provider.medicationPlans)
            val conflict = handler(
                provider,
                medicationPlans = countingPlans,
                deleteDataItem = { deletes += 1; true }
            ).handle(payload())
            assertEquals(WearDoseActionOutcome.Conflict, conflict)
            assertEquals(0, countingPlans.getCalls)
            assertEquals(0, deletes)
            assertEquals(sourceCollision, provider.doseEvents.getById(ACTION_ID))

            val missingId = UUID(0L, 1002L)
            val missing = handler(
                provider,
                deleteDataItem = { deletes += 1; true }
            ).handle(payload(actionId = missingId, planId = UUID(0L, 1003L)))
            assertEquals(WearDoseActionOutcome.PlanNotFound, missing)
            assertEquals(0, deletes)
            assertEquals(null, provider.doseEvents.getById(missingId))
            assertEquals(1, rawEventCount())
        }

    @Test
    fun `deletion failure survives process restart and exact replay retries`() = runBlocking {
        val firstProvider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, firstProvider.medicationPlans.save(plan()))
        val first = handler(firstProvider, deleteDataItem = { false })
            .handle(payload()) as WearDoseActionOutcome.Accepted
        assertFalse(first.dataItemDeleted)
        closeDatabase()

        val reopened = ProductionRepositoryProvider(openDatabase())
        val countingPlans = WearCountingMedicationPlanRepository(reopened.medicationPlans)
        var deletes = 0
        val replay = handler(
            ProductionRepositoryProvider(requireNotNull(database)),
            medicationPlans = countingPlans,
            deleteDataItem = { deletes += 1; true }
        ).handle(payload(planId = UUID(0L, 1004L))) as WearDoseActionOutcome.Accepted

        assertEquals(RecordAcceptance.FirstAcceptedReplay, replay.acceptance)
        assertTrue(replay.dataItemDeleted)
        assertEquals(0, countingPlans.getCalls)
        assertEquals(1, deletes)
        assertEquals(1, rawEventCount())
    }

    @Test
    fun `storage failure retains DataItem and performs no partial write`() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val failedActionId = UUID(0L, 1007L)
        val failingEvents = object : DoseEventRepository by provider.doseEvents {
            override suspend fun insert(event: DoseEvent): InsertResult {
                throw RepositoryPersistenceException("synthetic Wear insert failure")
            }
        }
        var deletes = 0

        val failure = handler(
            provider,
            doseEvents = failingEvents,
            deleteDataItem = { deletes += 1; true }
        ).handle(payload(actionId = failedActionId))

        assertEquals(WearDoseActionOutcome.StorageFailure, failure)
        assertEquals(0, deletes)
        assertEquals(null, provider.doseEvents.getById(failedActionId))
        assertEquals(0, rawEventCount())
    }

    @Test
    fun `concurrent duplicate and conflicting actions keep one authoritative row`() = runBlocking {
        val provider = ProductionRepositoryProvider(openDatabase())
        assertEquals(PlanSaveResult.Created, provider.medicationPlans.save(plan()))
        val duplicatePayload = payload()
        val duplicateResults = coroutineScope {
            List(2) {
                async(Dispatchers.IO) {
                    handler(provider, deleteDataItem = { true }).handle(duplicatePayload)
                }
            }.awaitAll()
        }
        assertEquals(1, rawEventCount())
        assertTrue(duplicateResults.all { it is WearDoseActionOutcome.Accepted })
        val duplicateAcceptances = duplicateResults.map {
            (it as WearDoseActionOutcome.Accepted).acceptance
        }
        assertEquals(1, duplicateAcceptances.count { it == RecordAcceptance.Inserted })
        assertTrue(
            duplicateAcceptances
                .filter { it != RecordAcceptance.Inserted }
                .all {
                    it == RecordAcceptance.RepositoryIdempotent ||
                        it == RecordAcceptance.FirstAcceptedReplay
                }
        )

        val conflictAction = UUID(0L, 1005L)
        val conflictingResults = coroutineScope {
            listOf(RECORDED_AT, RECORDED_AT.plusMillis(1L)).map { at ->
                async(Dispatchers.IO) {
                    handler(provider, deleteDataItem = { true }).handle(
                        payload(actionId = conflictAction, recordedAt = at)
                    )
                }
            }.awaitAll()
        }
        assertEquals(2, rawEventCount())
        assertEquals(1, conflictingResults.count { it is WearDoseActionOutcome.Accepted })
        assertEquals(1, conflictingResults.count { it == WearDoseActionOutcome.Conflict })
    }

    private fun handler(
        provider: ProductionRepositoryProvider,
        medicationPlans: MedicationPlanRepository = provider.medicationPlans,
        doseEvents: DoseEventRepository = provider.doseEvents,
        deleteDataItem: suspend (String) -> Boolean = { true }
    ) = WearDoseActionHandler(
        medicationPlans = medicationPlans,
        doseEvents = doseEvents,
        zoneId = { TEST_ZONE },
        deleteDataItem = deleteDataItem
    )

    private fun payload(
        planId: UUID = PLAN_ID,
        actionId: UUID = ACTION_ID,
        recordedAt: Instant = RECORDED_AT
    ) = WearDoseActionPayload(
        dataItemUri = "wear://synthetic-node/hrt/dose-actions/$actionId",
        planId = planId,
        actionId = actionId,
        recordedAtMillis = recordedAt.toEpochMilli()
    )

    private fun plan() = MedicationPlan(
        id = PLAN_ID,
        name = "Synthetic Wear plan",
        route = Route.ORAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.DAILY,
        slots = listOf(
            ScheduledDoseSlot(
                id = (ScheduledDoseSlotId.generate(
                    PLAN_ID,
                    0,
                    LocalTime.of(8, 30)
                ) as SlotIdResult.Success).id,
                planId = PLAN_ID,
                localTime = LocalTime.of(8, 30),
                position = 0
            )
        ),
        daysOfWeek = setOf(DayOfWeek.MONDAY),
        intervalDays = 1,
        isEnabled = true,
        extras = mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0),
        createdAt = Instant.parse("2024-01-02T03:04:05Z")
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
        assertTrue(matches.all { it in allowedArtifacts })
    }

    private companion object {
        const val TEST_DATABASE_PREFIX = "batch6c_wear_"
        const val TEST_DATABASE = "${TEST_DATABASE_PREFIX}test.db"
        val PLAN_ID: UUID = UUID(0L, 1001L)
        val ACTION_ID: UUID = UUID(0L, 1000L)
        val RECORDED_AT: Instant = Instant.parse("2027-01-15T08:32:00.123Z")
        val TEST_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

private class WearCountingMedicationPlanRepository(
    private val delegate: MedicationPlanRepository
) : MedicationPlanRepository by delegate {
    var getCalls = 0

    override suspend fun getById(id: UUID): MedicationPlan? {
        getCalls += 1
        return delegate.getById(id)
    }
}
