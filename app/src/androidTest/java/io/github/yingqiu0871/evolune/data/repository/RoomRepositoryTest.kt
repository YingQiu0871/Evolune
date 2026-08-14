package io.github.yingqiu0871.evolune.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.core.dataapi.DeleteResult
import io.github.yingqiu0871.evolune.core.dataapi.InsertResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanSaveResult
import io.github.yingqiu0871.evolune.core.dataapi.PlanUpdateResult
import io.github.yingqiu0871.evolune.core.dataapi.UpdateResult
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var eventRepository: RoomDoseEventRepository
    private lateinit var planRepository: RoomMedicationPlanRepository

    @Before
    fun createDatabase() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        eventRepository = RoomDoseEventRepository(database)
        planRepository = RoomMedicationPlanRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun eventPersistsAndReadsEveryV3FieldWithLegacyTimeShadow() = runBlocking {
        val event = syntheticEvent(
            id = uuid(101),
            occurredAt = Instant.ofEpochMilli(1_700_000_000_123L),
            zoneId = ZoneId.of("Asia/Shanghai"),
            localDate = LocalDate.of(2023, 11, 15),
            slotId = uuid(501),
            source = DoseEventSource.WEAR
        )

        assertEquals(InsertResult.Inserted, eventRepository.insert(event))
        assertEquals(event, eventRepository.getById(event.id))

        database.openHelper.readableDatabase.query(
            """
            SELECT timeH, occurredAtEpochMillis, zoneId, localDate, slotId,
                source, status, revision
            FROM dose_events WHERE id = ?
            """.trimIndent(),
            arrayOf(event.id.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_700_000_000_123L / 3_600_000.0, cursor.getDouble(0), 0.0)
            assertEquals(1_700_000_000_123L, cursor.getLong(1))
            assertEquals("Asia/Shanghai", cursor.getString(2))
            assertEquals("2023-11-15", cursor.getString(3))
            assertEquals(uuid(501).toString(), cursor.getString(4))
            assertEquals("WEAR", cursor.getString(5))
            assertEquals("RECORDED", cursor.getString(6))
            assertEquals(1L, cursor.getLong(7))
        }
    }

    @Test
    fun eventSupportsEpochZeroAndPositiveAndNegativeTimes() = runBlocking {
        val events = listOf(
            syntheticEvent(uuid(102), Instant.EPOCH),
            syntheticEvent(uuid(103), Instant.ofEpochMilli(3_600_001L)),
            syntheticEvent(uuid(104), Instant.ofEpochMilli(-3_600_001L))
        )

        events.forEach { assertEquals(InsertResult.Inserted, eventRepository.insert(it)) }

        events.forEach { assertEquals(it, eventRepository.getById(it.id)) }
        assertEquals(0.0, rawEventTimeH(uuid(102)), 0.0)
        assertEquals(3_600_001L / 3_600_000.0, rawEventTimeH(uuid(103)), 0.0)
        assertEquals(-3_600_001L / 3_600_000.0, rawEventTimeH(uuid(104)), 0.0)
    }

    @Test
    fun eventNullableMetadataRemainsNull() = runBlocking {
        val event = syntheticEvent(uuid(105), Instant.ofEpochMilli(123L))

        assertEquals(InsertResult.Inserted, eventRepository.insert(event))
        val restored = eventRepository.getById(event.id)
        assertNotNull(restored)

        assertNull(restored?.zoneId)
        assertNull(restored?.localDate)
        assertNull(restored?.slotId)
    }

    @Test
    fun eventInsertDistinguishesIdempotentAndConflict() = runBlocking {
        val event = syntheticEvent(uuid(106), Instant.ofEpochMilli(1000L))

        assertEquals(InsertResult.Inserted, eventRepository.insert(event))
        assertEquals(InsertResult.Idempotent, eventRepository.insert(event))
        assertEquals(
            InsertResult.Conflict,
            eventRepository.insert(event.copy(doseMG = event.doseMG + 1.0))
        )
        assertEquals(event, eventRepository.getById(event.id))
    }

    @Test
    fun eventUpdateEnforcesNoChangeRevisionAndConflicts() = runBlocking {
        val event = syntheticEvent(uuid(107), Instant.ofEpochMilli(2000L))
        assertEquals(InsertResult.Inserted, eventRepository.insert(event))

        assertEquals(UpdateResult.NoChange, eventRepository.update(event, expectedRevision = 1))
        assertEquals(1L, eventRepository.getById(event.id)?.revision)

        val edited = event.copy(doseMG = 4.0)
        assertEquals(UpdateResult.Updated, eventRepository.update(edited, expectedRevision = 1))
        assertEquals(2L, eventRepository.getById(event.id)?.revision)
        assertEquals(
            UpdateResult.RevisionConflict,
            eventRepository.update(edited.copy(doseMG = 5.0), expectedRevision = 1)
        )
        assertEquals(
            UpdateResult.NotFound,
            eventRepository.update(syntheticEvent(uuid(999), Instant.EPOCH), expectedRevision = 1)
        )
        assertEquals(UpdateResult.Invalid, eventRepository.update(edited, expectedRevision = 0))
    }

    @Test
    fun eventRejectsSubMillisecondTimeWithoutWritingFakeEpochZero() = runBlocking {
        val invalid = syntheticEvent(uuid(108), Instant.ofEpochSecond(0, 1))

        assertEquals(InsertResult.Invalid, eventRepository.insert(invalid))
        assertNull(eventRepository.getById(invalid.id))
        assertEquals(0, rawCount("dose_events"))
    }

    @Test
    fun eventRangeAndObservationUseOccurredAtContractOrdering() = runBlocking {
        val first = syntheticEvent(uuid(109), Instant.ofEpochMilli(1000L))
        val second = syntheticEvent(uuid(110), Instant.ofEpochMilli(2000L))
        val boundary = syntheticEvent(uuid(111), Instant.ofEpochMilli(3000L))
        listOf(first, second, boundary).forEach { eventRepository.insert(it) }

        assertEquals(
            listOf(first, second),
            eventRepository.findOccurredBetween(
                Instant.ofEpochMilli(1000L),
                Instant.ofEpochMilli(3000L)
            )
        )
        assertEquals(listOf(boundary, second, first), eventRepository.observeAll().first())
    }

    @Test
    fun eventPkSelectionPreservesBothLegacyBranchOrders() = runBlocking {
        val asOf = Instant.ofEpochMilli(2_000_000_000_000L)
        val sparse = (1L..3L).map { hours ->
            syntheticEvent(uuid(120 + hours), asOf.minusSeconds(hours * 3600L))
        }
        sparse.forEach { eventRepository.insert(it) }
        assertEquals(
            sparse.sortedByDescending { it.occurredAt },
            eventRepository.getEventsForPk(asOf)
        )

        assertEquals(DeleteResult.Deleted, eventRepository.deleteAll())
        val dense = (0 until 20).map { index ->
            syntheticEvent(
                uuid(200L + index),
                asOf.minusSeconds((20L - index) * 60L)
            )
        }
        dense.forEach { eventRepository.insert(it) }
        assertEquals(dense, eventRepository.getEventsForPk(asOf))
    }

    @Test
    fun eventDeleteAndDeleteAllReportNotFoundPrecisely() = runBlocking {
        val first = syntheticEvent(uuid(112), Instant.ofEpochMilli(100L))
        val second = syntheticEvent(uuid(113), Instant.ofEpochMilli(200L))
        eventRepository.insert(first)
        eventRepository.insert(second)

        assertEquals(DeleteResult.Deleted, eventRepository.delete(first.id))
        assertEquals(DeleteResult.NotFound, eventRepository.delete(first.id))
        assertEquals(DeleteResult.Deleted, eventRepository.deleteAll())
        assertEquals(DeleteResult.NotFound, eventRepository.deleteAll())
    }

    @Test
    fun eventConstraintFailureIsAStorageExceptionAndLeavesNoRow() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER synthetic_event_failure
            BEFORE INSERT ON dose_events
            BEGIN
                SELECT RAISE(ABORT, 'synthetic event failure');
            END
            """.trimIndent()
        )

        assertSuspendFails<RepositoryConstraintException> {
            eventRepository.insert(syntheticEvent(uuid(114), Instant.ofEpochMilli(100L)))
        }
        assertEquals(0, rawCount("dose_events"))
    }

    @Test
    fun planSavesEmptySlotsAndCanonicalEmptyLegacyArray() = runBlocking {
        val plan = syntheticPlan(uuid(301), emptyList())

        assertEquals(PlanSaveResult.Created, planRepository.save(plan))
        assertEquals(plan, planRepository.getById(plan.id))
        assertEquals("[]", rawPlanTimeOfDay(plan.id))
        assertEquals(0, rawSlotCount(plan.id))
    }

    @Test
    fun planSavesBoundaryTimesAndFixedUuidV5Vector() = runBlocking {
        val plan = syntheticPlan(
            FIXED_VECTOR_PLAN_ID,
            listOf(LocalTime.of(8, 30), LocalTime.MIDNIGHT, LocalTime.of(23, 59))
        )

        assertEquals(PlanSaveResult.Created, planRepository.save(plan))
        assertEquals(
            listOf("08:30", "00:00", "23:59"),
            rawSlotRows(plan.id).map { it.localTime }
        )
        assertEquals(FIXED_VECTOR_SLOT_ID, rawSlotRows(plan.id)[0].id)
        assertEquals("[\"08:30\",\"00:00\",\"23:59\"]", rawPlanTimeOfDay(plan.id))
    }

    @Test
    fun planPreservesOriginalOrderAndDuplicateTimes() = runBlocking {
        val times = listOf(LocalTime.of(20, 0), LocalTime.of(8, 30), LocalTime.of(8, 30))
        val plan = syntheticPlan(uuid(302), times)

        assertEquals(PlanSaveResult.Created, planRepository.save(plan))
        assertEquals(times, planRepository.getById(plan.id)?.slots?.map { it.localTime })
        assertEquals(listOf(0, 1, 2), rawSlotRows(plan.id).map { it.position })
        assertEquals(3, rawSlotRows(plan.id).map { it.id }.toSet().size)
    }

    @Test
    fun planUpdatePreciselyReplacesSlotsAndLegacyTimes() = runBlocking {
        val original = syntheticPlan(
            uuid(303),
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        )
        planRepository.save(original)
        val oldSlotIds = rawSlotRows(original.id).map { it.id }.toSet()
        val updated = syntheticPlan(
            original.id,
            listOf(LocalTime.of(9, 15), LocalTime.of(9, 15), LocalTime.of(21, 45)),
            name = "Updated synthetic plan"
        )

        assertEquals(PlanSaveResult.Updated, planRepository.save(updated))
        assertEquals(updated, planRepository.getById(updated.id))
        assertEquals("[\"09:15\",\"09:15\",\"21:45\"]", rawPlanTimeOfDay(updated.id))
        assertTrue(rawSlotRows(updated.id).none { it.id in oldSlotIds })
    }

    @Test
    fun planUpdateToEmptySlotsLeavesNoPartialAggregate() = runBlocking {
        val original = syntheticPlan(uuid(304), listOf(LocalTime.of(8, 0)))
        planRepository.save(original)
        val updated = syntheticPlan(original.id, emptyList())

        assertEquals(PlanSaveResult.Updated, planRepository.save(updated))
        assertEquals(updated, planRepository.getById(updated.id))
        assertEquals("[]", rawPlanTimeOfDay(updated.id))
        assertEquals(0, rawSlotCount(updated.id))
    }

    @Test
    fun planFieldUpdatePreservesSlotsAndIdempotentSaveIsNoChange() = runBlocking {
        val original = syntheticPlan(uuid(305), listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)))
        assertEquals(PlanSaveResult.Created, planRepository.save(original))
        assertEquals(PlanSaveResult.NoChange, planRepository.save(original))
        val updated = original.copy(name = "Renamed synthetic plan", doseMG = 4.5)

        assertEquals(PlanSaveResult.Updated, planRepository.save(updated))
        assertEquals(original.slots, planRepository.getById(original.id)?.slots)
    }

    @Test
    fun planSetEnabledUsesUpdatedNoChangeAndNotFound() = runBlocking {
        val plan = syntheticPlan(uuid(306), listOf(LocalTime.of(8, 0)), enabled = true)
        planRepository.save(plan)

        assertEquals(PlanUpdateResult.NoChange, planRepository.setEnabled(plan.id, true))
        assertEquals(PlanUpdateResult.Updated, planRepository.setEnabled(plan.id, false))
        assertFalse(planRepository.getById(plan.id)?.isEnabled ?: true)
        assertEquals(PlanUpdateResult.NotFound, planRepository.setEnabled(uuid(999), false))
    }

    @Test
    fun planDeleteCascadesOnlyItsSlotsAndPreservesOtherPlan() = runBlocking {
        val first = syntheticPlan(uuid(307), listOf(LocalTime.of(8, 0)))
        val second = syntheticPlan(uuid(308), listOf(LocalTime.of(20, 0)))
        planRepository.save(first)
        planRepository.save(second)

        assertEquals(DeleteResult.Deleted, planRepository.delete(first.id))
        assertEquals(DeleteResult.NotFound, planRepository.delete(first.id))
        assertEquals(0, rawSlotCount(first.id))
        assertEquals(second, planRepository.getById(second.id))
        assertEquals(1, rawSlotCount(second.id))
    }

    @Test
    fun planDeleteAllCascadesEverySlot() = runBlocking {
        planRepository.save(syntheticPlan(uuid(309), listOf(LocalTime.of(8, 0))))
        planRepository.save(syntheticPlan(uuid(310), listOf(LocalTime.of(20, 0))))

        assertEquals(DeleteResult.Deleted, planRepository.deleteAll())
        assertEquals(DeleteResult.NotFound, planRepository.deleteAll())
        assertEquals(0, rawCount("medication_plans"))
        assertEquals(0, rawCount("scheduled_dose_slots"))
    }

    @Test
    fun planRejectsUnexpectedSlotIdWithoutWriting() = runBlocking {
        val plan = syntheticPlan(uuid(311), listOf(LocalTime.of(8, 30)))
        val invalid = plan.copy(
            slots = listOf(plan.slots.single().copy(id = uuid(888)))
        )

        assertEquals(PlanSaveResult.Invalid, planRepository.save(invalid))
        assertNull(planRepository.getById(plan.id))
    }

    @Test
    fun planReadRejectsLegacySlotMismatchWithoutRepairingIt() = runBlocking {
        val plan = syntheticPlan(uuid(312), listOf(LocalTime.of(8, 30)))
        planRepository.save(plan)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE medication_plans SET timeOfDay = ? WHERE id = ?",
            arrayOf("[\"09:30\"]", plan.id.toString())
        )

        assertSuspendFails<CorruptAggregateException> { planRepository.getById(plan.id) }
        assertEquals("[\"09:30\"]", rawPlanTimeOfDay(plan.id))
        assertEquals("08:30", rawSlotRows(plan.id).single().localTime)
    }

    @Test
    fun planSaveRollsBackPlanSlotsAndLegacyTimeWhenSlotInsertFails() = runBlocking {
        val original = syntheticPlan(uuid(313), listOf(LocalTime.of(8, 0)), name = "Original")
        val unaffected = syntheticPlan(uuid(314), listOf(LocalTime.of(12, 0)), name = "Unaffected")
        planRepository.save(original)
        planRepository.save(unaffected)
        val originalRows = rawSlotRows(original.id)
        val originalLegacyTimes = rawPlanTimeOfDay(original.id)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER synthetic_slot_failure
            BEFORE INSERT ON scheduled_dose_slots
            BEGIN
                SELECT RAISE(ABORT, 'synthetic slot failure');
            END
            """.trimIndent()
        )
        val attempted = syntheticPlan(
            original.id,
            listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            name = "Attempted update"
        )

        assertSuspendFails<RepositoryConstraintException> { planRepository.save(attempted) }
        assertEquals(original, planRepository.getById(original.id))
        assertEquals(originalRows, rawSlotRows(original.id))
        assertEquals(originalLegacyTimes, rawPlanTimeOfDay(original.id))
        assertEquals(unaffected, planRepository.getById(unaffected.id))
    }

    @Test
    fun planObservationReturnsAggregatesInContractOrderAndEnabledSubset() = runBlocking {
        val older = syntheticPlan(
            uuid(315),
            listOf(LocalTime.of(8, 0)),
            enabled = false,
            createdAt = Instant.ofEpochMilli(1000L)
        )
        val newer = syntheticPlan(
            uuid(316),
            listOf(LocalTime.of(20, 0)),
            enabled = true,
            createdAt = Instant.ofEpochMilli(2000L)
        )
        planRepository.save(older)
        planRepository.save(newer)

        assertEquals(listOf(newer, older), planRepository.observeAll().first())
        assertEquals(listOf(newer), planRepository.observeEnabled().first())
    }

    private fun syntheticEvent(
        id: UUID,
        occurredAt: Instant,
        zoneId: ZoneId? = null,
        localDate: LocalDate? = null,
        slotId: UUID? = null,
        source: DoseEventSource = DoseEventSource.MANUAL
    ): DoseEvent = DoseEvent(
        id = id,
        route = Route.INJECTION,
        occurredAt = occurredAt,
        zoneId = zoneId,
        localDate = localDate,
        doseMG = 3.0,
        ester = Ester.EV,
        extras = mapOf(ExtraKey.CONCENTRATION_MG_ML to 20.0),
        slotId = slotId,
        source = source,
        status = DoseEventStatus.RECORDED,
        revision = 1
    )

    private fun syntheticPlan(
        id: UUID,
        times: List<LocalTime>,
        name: String = "Synthetic plan",
        enabled: Boolean = true,
        createdAt: Instant = Instant.ofEpochMilli(1_700_000_000_000L)
    ): MedicationPlan = MedicationPlan(
        id = id,
        name = name,
        route = Route.SUBLINGUAL,
        ester = Ester.E2,
        doseMG = 2.0,
        scheduleType = ScheduleType.WEEKLY,
        slots = times.mapIndexed { position, time ->
            ScheduledDoseSlot(
                id = generatedSlotId(id, position, time),
                planId = id,
                localTime = time,
                position = position
            )
        },
        daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        intervalDays = 2,
        isEnabled = enabled,
        extras = mapOf(ExtraKey.SUBLINGUAL_THETA to 0.4),
        createdAt = createdAt
    )

    private fun generatedSlotId(planId: UUID, position: Int, time: LocalTime): UUID =
        (ScheduledDoseSlotId.generate(planId, position, time) as SlotIdResult.Success).id

    private fun rawEventTimeH(id: UUID): Double =
        database.openHelper.readableDatabase.query(
            "SELECT timeH FROM dose_events WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getDouble(0)
        }

    private fun rawPlanTimeOfDay(id: UUID): String =
        database.openHelper.readableDatabase.query(
            "SELECT timeOfDay FROM medication_plans WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun rawSlotRows(planId: UUID): List<RawSlotRow> =
        database.openHelper.readableDatabase.query(
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
                    add(
                        RawSlotRow(
                            id = cursor.getString(0),
                            localTime = cursor.getString(1),
                            position = cursor.getInt(2)
                        )
                    )
                }
            }
        }

    private fun rawSlotCount(planId: UUID): Int =
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = ?",
            arrayOf(planId.toString())
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun rawCount(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private suspend inline fun <reified T : Throwable> assertSuspendFails(
        crossinline block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) {
                return error
            }
            throw error
        }
        fail("Expected ${T::class.java.simpleName}")
        throw AssertionError("unreachable")
    }

    private fun uuid(value: Long): UUID = UUID(0L, value)

    private data class RawSlotRow(
        val id: String,
        val localTime: String,
        val position: Int
    )

    private companion object {
        val FIXED_VECTOR_PLAN_ID: UUID =
            UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val FIXED_VECTOR_SLOT_ID = "17d1fd14-9d70-5344-beaa-0b158c9f62f4"
    }
}
