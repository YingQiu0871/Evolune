package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.dataapi.DoseEventRepository
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.MedicationPlanRepository
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.DoseEventStatus
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProductionRepositoryProviderTest {
    private lateinit var context: Context
    private var database: AppDatabase? = null

    @Before
    fun prepareDisposableDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none { it.exists() })
    }

    @After
    fun removeDisposableDatabase() {
        closeDatabase()
        deleteDatabaseArtifacts()
        assertTrue(databaseArtifacts().none { it.exists() })
    }

    @Test
    fun providerUsesStableContractRepositoriesFromOneInjectedDatabase() = runBlocking {
        val opened = openDatabase()
        val provider = ProductionRepositoryProvider(opened)

        val doseEvents: DoseEventRepository = provider.doseEvents
        val medicationPlans: MedicationPlanRepository = provider.medicationPlans
        assertSame(doseEvents, provider.doseEvents)
        assertSame(medicationPlans, provider.medicationPlans)
        assertEquals(
            DoseEventRepository::class.java,
            ProductionRepositoryProvider::class.java.getMethod("getDoseEvents").returnType
        )
        assertEquals(
            MedicationPlanRepository::class.java,
            ProductionRepositoryProvider::class.java.getMethod("getMedicationPlans").returnType
        )

        val plan = syntheticPlan()
        val event = syntheticEvent()
        assertEquals(PlanSaveResult.Created, medicationPlans.save(plan))
        assertEquals(plan, medicationPlans.getById(plan.id))
        assertEquals(InsertResult.Inserted, doseEvents.insert(event))
        assertEquals(event, doseEvents.getById(event.id))
        assertEquals(3, opened.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()
        assertTrue(databaseFile().exists())
    }

    @Test
    fun dataSurvivesReopenThroughANewProviderForTheSameDisposableFile() = runBlocking {
        val firstDatabase = openDatabase()
        val firstProvider = ProductionRepositoryProvider(firstDatabase)
        val plan = syntheticPlan()
        val event = syntheticEvent()
        assertEquals(PlanSaveResult.Created, firstProvider.medicationPlans.save(plan))
        assertEquals(InsertResult.Inserted, firstProvider.doseEvents.insert(event))

        closeDatabase()
        val reopenedDatabase = openDatabase()
        val reopenedProvider = ProductionRepositoryProvider(reopenedDatabase)

        assertEquals(plan, reopenedProvider.medicationPlans.getById(plan.id))
        assertEquals(event, reopenedProvider.doseEvents.getById(event.id))
        assertEquals(3, reopenedDatabase.openHelper.readableDatabase.version)
        assertNoSecondTestDatabase()
        assertFalse(
            ProductionRepositoryProvider::class.java.declaredMethods.any {
                it.returnType.name.contains("Dao") || it.returnType.name.contains("Entity")
            }
        )
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
            if (artifact.exists()) {
                assertTrue(artifact.delete())
            }
        }
    }

    private fun databaseFile(): File = context.getDatabasePath(TEST_DATABASE)

    private fun databaseArtifacts(): List<File> {
        val databasePath = databaseFile()
        return listOf(
            databasePath,
            File(databasePath.path + "-wal"),
            File(databasePath.path + "-shm"),
            File(databasePath.path + "-journal")
        )
    }

    private fun matchingTestDatabases(): List<String> =
        context.databaseList()
            .filter { it.startsWith(TEST_DATABASE_PREFIX) }
            .sorted()

    private fun assertNoSecondTestDatabase() {
        val matching = matchingTestDatabases()
        assertTrue(TEST_DATABASE in matching)
        assertTrue(matching.all { it in expectedDatabaseArtifacts() })
    }

    private fun expectedDatabaseArtifacts(): Set<String> = setOf(
        TEST_DATABASE,
        "$TEST_DATABASE-wal",
        "$TEST_DATABASE-shm",
        "$TEST_DATABASE-journal"
    )

    private fun syntheticPlan(): MedicationPlan {
        val planId = uuid(501)
        val times = listOf(LocalTime.of(8, 30), LocalTime.of(20, 45))
        return MedicationPlan(
            id = planId,
            name = "Synthetic provider plan",
            route = Route.SUBLINGUAL,
            ester = Ester.E2,
            doseMG = 1.5,
            scheduleType = ScheduleType.WEEKLY,
            slots = times.mapIndexed { position, localTime ->
                ScheduledDoseSlot(
                    id = generatedSlotId(planId, position, localTime),
                    planId = planId,
                    localTime = localTime,
                    position = position
                )
            },
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            intervalDays = 3,
            isEnabled = true,
            extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.4),
            createdAt = Instant.parse("2024-01-02T03:04:05.006Z")
        )
    }

    private fun syntheticEvent(): DoseEvent = DoseEvent(
        id = uuid(601),
        route = Route.ORAL,
        occurredAt = Instant.parse("2024-01-03T04:05:06.007Z"),
        zoneId = null,
        localDate = null,
        doseMG = 2.0,
        ester = Ester.EV,
        extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 10.0),
        slotId = null,
        source = DoseEventSource.MANUAL,
        status = DoseEventStatus.RECORDED,
        revision = 1
    )

    private fun generatedSlotId(
        planId: UUID,
        position: Int,
        localTime: LocalTime
    ): UUID = (ScheduledDoseSlotId.generate(
        planId,
        position,
        localTime
    ) as SlotIdResult.Success).id

    private fun uuid(value: Int): UUID = UUID(0L, value.toLong())

    private companion object {
        const val TEST_DATABASE_PREFIX = "batch5a_provider_"
        const val TEST_DATABASE = "${TEST_DATABASE_PREFIX}test.db"
    }
}
