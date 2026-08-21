package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.time.LegacyTimeAdapter
import io.github.yingqiu0871.evolune.core.time.LegacyTimeResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.model.DoseEvent
import io.github.yingqiu0871.evolune.core.model.DoseEventSource
import io.github.yingqiu0871.evolune.core.model.ExtraKey
import io.github.yingqiu0871.evolune.core.model.MedicationPlan
import io.github.yingqiu0871.evolune.core.model.ScheduleType
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlot
import io.github.yingqiu0871.evolune.core.model.ScheduledDoseSlotId
import io.github.yingqiu0871.evolune.core.model.SlotIdResult
import io.github.yingqiu0871.evolune.data.repository.RoomDoseEventRepository
import io.github.yingqiu0871.evolune.data.repository.RoomMedicationPlanRepository
import io.github.yingqiu0871.evolune.pk.Ester
import io.github.yingqiu0871.evolune.pk.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Batch8CPreservedUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun deleteBatch8cDatabase() {
        context.deleteDatabase(BATCH8C_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE)
    }

    @After
    fun deleteTestDatabases() {
        context.deleteDatabase(BATCH8C_DATABASE_NAME)
        context.deleteDatabase(FRESH_DATABASE)
    }

    @Test
    fun preservedV2DatabaseMigratesAndEveryAggregateIsRepositoryReadable() = runBlocking {
        val expectedEvents = eventFixture()
        val expectedPlans = planFixture()

        migrationHelper.createDatabase(BATCH8C_DATABASE_NAME, 2).use { database ->
            seedV2State(database, expectedEvents, expectedPlans)
            assertEquals(2, pragmaVersion(database))
            assertEquals(expectedEvents.size, rowCount(database, "dose_events"))
            assertEquals(expectedPlans.size, rowCount(database, "medication_plans"))
            assertEquals(expectedEvents.map { it.id }.toSet(), queryIds(database, "dose_events"))
            assertEquals(expectedPlans.map { it.id }.toSet(), queryIds(database, "medication_plans"))
        }

        val database = Room.databaseBuilder(context, AppDatabase::class.java, BATCH8C_DATABASE_NAME)
            .addMigrations(io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            assertMigratedState(database)
        } finally {
            database.close()
        }
    }

    @Test
    fun freshV3ControlSupportsRepositoryPersistence() = runBlocking {
        context.deleteDatabase(FRESH_DATABASE)
        val database = Room.databaseBuilder(context, AppDatabase::class.java, FRESH_DATABASE)
            .allowMainThreadQueries()
            .build()
        try {
            val eventRepository = RoomDoseEventRepository(database)
            val planRepository = RoomMedicationPlanRepository(database)
            val event = eventFixture().first()
            val plan = planFixture().first()
            assertEquals(InsertResult.Inserted, eventRepository.insert(event))
            assertEquals(PlanSaveResult.Created, planRepository.save(plan))
            assertEquals(event, eventRepository.getById(event.id))
            assertEquals(plan, planRepository.getById(plan.id))
            assertEquals(3, pragmaVersion(database))
        } finally {
            database.close()
        }
    }

    private suspend fun assertMigratedState(database: AppDatabase) {
        assertEquals(3, pragmaVersion(database))
        assertEquals(
            "c5f5e02cb04b048ca28fe96a74d61606",
            database.openHelper.readableDatabase.query(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }
        )

        val eventRepository = RoomDoseEventRepository(database)
        val planRepository = RoomMedicationPlanRepository(database)
        val expectedEvents = eventFixture().associateBy { it.id }
        val expectedPlans = planFixture().associateBy { it.id }

        assertEquals(expectedEvents.keys, eventRepository.observeAll().first().map { it.id }.toSet())
        assertEquals(expectedPlans.keys, planRepository.observeAll().first().map { it.id }.toSet())
        expectedEvents.forEach { (id, expected) ->
            assertEquals(expected, eventRepository.getById(id))
        }
        expectedPlans.forEach { (id, expected) ->
            assertEquals(expected, planRepository.getById(id))
        }

        val raw = database.openHelper.readableDatabase
        assertEquals(6, rowCount(raw, "dose_events"))
        assertEquals(3, rowCount(raw, "medication_plans"))
        assertEquals(5, rowCount(raw, "scheduled_dose_slots"))
        raw.query("PRAGMA integrity_check").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("ok", cursor.getString(0))
        }
        raw.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
    }

    private fun pragmaVersion(database: AppDatabase): Int =
        database.openHelper.readableDatabase.query("PRAGMA user_version").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun rowCount(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun seedV2State(
        database: SupportSQLiteDatabase,
        events: List<DoseEvent>,
        plans: List<MedicationPlan>
    ) {
        events.forEach { event ->
            val timeH = when (val result = LegacyTimeAdapter.instantToTimeH(event.occurredAt)) {
                is LegacyTimeResult.Success -> result.value
                is LegacyTimeResult.Failure -> error("Synthetic event time cannot be encoded: ${event.id}")
            }
            database.execSQL(
                """
                INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    event.id.toString(),
                    event.route.name,
                    timeH,
                    event.doseMG,
                    event.ester.name,
                    Converters().fromMap(event.extras.mapKeys { it.key.name })
                )
            )
        }

        plans.forEach { plan ->
            database.execSQL(
                """
                INSERT INTO medication_plans(
                    id, name, route, ester, doseMG, scheduleType, timeOfDay,
                    daysOfWeek, intervalDays, isEnabled, extras, createdAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    plan.id.toString(),
                    plan.name,
                    plan.route.name,
                    plan.ester.name,
                    plan.doseMG,
                    plan.scheduleType.name,
                    Converters().fromStringList(plan.slots.map { it.localTime.toString() }),
                    Converters().fromIntSet(plan.daysOfWeek.map { it.value }.toSet()),
                    plan.intervalDays,
                    if (plan.isEnabled) 1 else 0,
                    Converters().fromMap(plan.extras.mapKeys { it.key.name }),
                    plan.createdAt.toEpochMilli()
                )
            )
        }
    }

    private fun queryIds(database: SupportSQLiteDatabase, table: String): Set<UUID> =
        database.query("SELECT id FROM `$table`").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(UUID.fromString(cursor.getString(0)))
            }
        }

    private fun pragmaVersion(database: SupportSQLiteDatabase): Int =
        database.query("PRAGMA user_version").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun eventFixture() = listOf(
        event(1, Route.ORAL, 0L, 1.0, Ester.E2),
        event(2, Route.SUBLINGUAL, 45_000_000L, 2.0, Ester.E2, mapOf(ExtraKey.SUBLINGUAL_TIER to 2.0)),
        event(3, Route.INJECTION, -86_400_000L, 5.0, Ester.EV, mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0)),
        event(4, Route.PATCH_APPLY, -3_153_600_000_000L, 0.0, Ester.E2, mapOf(ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0)),
        event(5, Route.GEL, 1_700_000_000_123L, 0.75, Ester.E2, mapOf(ExtraKey.AREA_CM2 to 4.0)),
        event(6, Route.ANTIANDROGEN, 45_000_000L, 25.0, Ester.E2, mapOf(ExtraKey.ANTI_ANDROGEN_TYPE to 1.0))
    )

    private fun event(
        index: Int,
        route: Route,
        epochMillis: Long,
        doseMG: Double,
        ester: Ester,
        extras: Map<ExtraKey, Double> = emptyMap()
    ) = DoseEvent(
        id = UUID.fromString("81000000-0000-0000-0000-${index.toString().padStart(12, '0')}"),
        route = route,
        occurredAt = Instant.ofEpochMilli(epochMillis),
        doseMG = doseMG,
        ester = ester,
        extras = extras,
        source = DoseEventSource.LEGACY
    )

    private fun planFixture() = listOf(
        plan(1, "Batch 8C daily", Route.ORAL, Ester.E2, 1.0, ScheduleType.DAILY, listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), emptySet(), 14, true, emptyMap(), 1_600_000_000_000L),
        plan(2, "Batch 8C weekly", Route.INJECTION, Ester.EV, 5.0, ScheduleType.WEEKLY, listOf(LocalTime.of(9, 15)), setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 7, false, mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0), 1_700_000_000_000L),
        plan(3, "Batch 8C custom", Route.SUBLINGUAL, Ester.E2, 2.0, ScheduleType.CUSTOM, listOf(LocalTime.of(6, 45), LocalTime.of(6, 45)), setOf(DayOfWeek.SUNDAY), 3, true, mapOf(ExtraKey.SUBLINGUAL_THETA to 0.4), 1_800_000_000_000L)
    )

    private fun plan(
        index: Int,
        name: String,
        route: Route,
        ester: Ester,
        doseMG: Double,
        scheduleType: ScheduleType,
        times: List<LocalTime>,
        days: Set<DayOfWeek>,
        intervalDays: Int,
        enabled: Boolean,
        extras: Map<ExtraKey, Double>,
        createdAt: Long
    ): MedicationPlan {
        val id = UUID.fromString("82000000-0000-0000-0000-${index.toString().padStart(12, '0')}")
        val slots = times.mapIndexed { position, time ->
            val slotId = when (val result = ScheduledDoseSlotId.generate(id, position, time)) {
                is SlotIdResult.Success -> result.id
                is SlotIdResult.Failure -> error("Synthetic slot ID failed: ${result.error}")
            }
            ScheduledDoseSlot(slotId, id, time, position)
        }
        return MedicationPlan(id, name, route, ester, doseMG, scheduleType, slots, days, intervalDays, enabled, extras, Instant.ofEpochMilli(createdAt))
    }

    private companion object {
        const val BATCH8C_DATABASE_NAME = "batch8c_preserved_upgrade_test"
        const val FRESH_DATABASE = "phase1-batch8c-fresh-control"
    }
}
