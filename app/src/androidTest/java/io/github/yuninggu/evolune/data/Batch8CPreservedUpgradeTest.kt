package io.github.yuninggu.evolune.data

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
import io.github.yuninggu.evolune.data.repository.RoomDoseEventRepository
import io.github.yuninggu.evolune.data.repository.RoomMedicationPlanRepository
import io.github.yuninggu.evolune.pk.Ester
import io.github.yuninggu.evolune.pk.Route
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Batch8CPreservedUpgradeTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun deleteFreshControlDatabase() {
        context.deleteDatabase(FRESH_DATABASE)
    }

    @Test
    fun preservedV2DatabaseMigratesAndEveryAggregateIsRepositoryReadable() = runBlocking {
        val database = AppDatabase.getDatabase(context)
        assertMigratedState(database)
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
        const val FRESH_DATABASE = "phase1-batch8c-fresh-control"
    }
}
