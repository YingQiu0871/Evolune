package io.github.yingqiu0871.evolune.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.migration.LegacyMigrationError
import io.github.yingqiu0871.evolune.data.migration.LegacyMigrationException
import io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @get:Rule
    val testName = TestName()

    private val databaseName: String
        get() = "phase1-batch4a1-${testName.methodName}"

    @Before
    fun deleteDatabaseBeforeTest() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun deleteDatabaseAfterTest() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun emptyV2DatabaseMigratesToValidatedV3Schema() {
        createV2Database().close()

        migrateToV3().use { database ->
            assertEquals(3, pragmaInt(database, "user_version"))
            assertEquals(
                setOf("dose_events", "medication_plans", "scheduled_dose_slots"),
                tableNames(database)
            )
            assertEquals(V3_EVENT_COLUMNS, columnNames(database, "dose_events"))
            assertEquals(SLOT_COLUMNS, columnNames(database, "scheduled_dose_slots"))
            assertEquals(SLOT_INDICES, explicitIndexNames(database, "scheduled_dose_slots"))
            assertSlotForeignKey(database)
        }
    }

    @Test
    fun singleSyntheticEventMigratesWithoutChangingLegacyValues() {
        createV2Database().use { database ->
            insertV2Event(
                database = database,
                id = EVENT_ID,
                timeH = 12.5,
                route = "SUBLINGUAL",
                doseMG = 1.25,
                ester = "E2",
                extras = "{\"SUBLINGUAL_THETA\":0.42}"
            )
        }

        migrateToV3().use { database ->
            database.query(
                """
                SELECT id, route, timeH, doseMG, ester, extras,
                    occurredAtEpochMillis, zoneId, localDate, slotId,
                    source, status, revision
                FROM dose_events
                """.trimIndent()
            ).use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                assertEquals(EVENT_ID, cursor.getString(0))
                assertEquals("SUBLINGUAL", cursor.getString(1))
                assertEquals(12.5, cursor.getDouble(2), 0.0)
                assertEquals(1.25, cursor.getDouble(3), 0.0)
                assertEquals("E2", cursor.getString(4))
                assertEquals("{\"SUBLINGUAL_THETA\":0.42}", cursor.getString(5))
                assertEquals(45_000_000L, cursor.getLong(6))
                assertTrue(cursor.isNull(7))
                assertTrue(cursor.isNull(8))
                assertTrue(cursor.isNull(9))
                assertEquals("LEGACY", cursor.getString(10))
                assertEquals("RECORDED", cursor.getString(11))
                assertEquals(1L, cursor.getLong(12))
            }
        }
    }

    @Test
    fun legitimateEpochZeroRemainsZeroAfterMigration() {
        createV2Database().use { database ->
            insertV2Event(database, EVENT_ID, timeH = 0.0)
        }

        migrateToV3().use { database ->
            database.query(
                """
                SELECT timeH, occurredAtEpochMillis, source, status, revision
                FROM dose_events WHERE id = ?
                """.trimIndent(),
                arrayOf(EVENT_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0.0, cursor.getDouble(0), 0.0)
                assertEquals(0L, cursor.getLong(1))
                assertEquals("LEGACY", cursor.getString(2))
                assertEquals("RECORDED", cursor.getString(3))
                assertEquals(1L, cursor.getLong(4))
            }
        }
    }

    @Test
    fun singlePlanCreatesOneSlotWithFixedUuidV5Vector() {
        createV2Database().use { database ->
            insertV2Plan(database, FIXED_VECTOR_PLAN_ID, "[\"08:30\"]")
        }

        migrateToV3().use { database ->
            assertSlots(
                database,
                FIXED_VECTOR_PLAN_ID,
                listOf(SlotRow(FIXED_VECTOR_SLOT_ID, FIXED_VECTOR_PLAN_ID, "08:30", 0))
            )
        }
    }

    @Test
    fun multipleSlotsPreserveOriginalOrder() {
        createV2Database().use { database ->
            insertV2Plan(database, ORDERED_PLAN_ID, "[\"20:00\",\"08:30\",\"12:15\"]")
        }

        migrateToV3().use { database ->
            assertSlots(
                database,
                ORDERED_PLAN_ID,
                listOf(
                    SlotRow("410551cd-7c2f-5915-8d28-05c5df16d7c1", ORDERED_PLAN_ID, "20:00", 0),
                    SlotRow("c74c4a5b-b718-5cf4-877c-d3d0cc947408", ORDERED_PLAN_ID, "08:30", 1),
                    SlotRow("86820580-1876-5a6e-b50b-67b029b20aef", ORDERED_PLAN_ID, "12:15", 2)
                )
            )
        }
    }

    @Test
    fun duplicateTimesRemainDistinctByPositionAndSlotId() {
        createV2Database().use { database ->
            insertV2Plan(database, DUPLICATE_PLAN_ID, "[\"08:30\",\"08:30\"]")
        }

        migrateToV3().use { database ->
            val expected = listOf(
                SlotRow("f7748ff4-a07e-5985-bd72-7c76e858aaa8", DUPLICATE_PLAN_ID, "08:30", 0),
                SlotRow("06cf3707-cd73-5567-b5b7-fdf00e408046", DUPLICATE_PLAN_ID, "08:30", 1)
            )
            assertSlots(database, DUPLICATE_PLAN_ID, expected)
            assertNotEquals(expected[0].id, expected[1].id)
        }
    }

    @Test
    fun emptySqlStringCreatesNoSlotsAndRemainsUnchanged() {
        assertEmptyPlanMigration(EMPTY_STRING_PLAN_ID, "")
    }

    @Test
    fun emptyJsonArrayCreatesNoSlotsAndRemainsUnchanged() {
        assertEmptyPlanMigration(EMPTY_ARRAY_PLAN_ID, "[]")
    }

    @Test
    fun fixedUuidV5VectorMatchesLockedExpectedValue() {
        createV2Database().use { database ->
            insertV2Plan(database, FIXED_VECTOR_PLAN_ID, "[\"08:30\"]")
        }

        migrateToV3().use { database ->
            database.query(
                "SELECT id FROM scheduled_dose_slots WHERE planId = ? AND position = 0",
                arrayOf(FIXED_VECTOR_PLAN_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("17d1fd14-9d70-5344-beaa-0b158c9f62f4", cursor.getString(0))
            }
        }
    }

    @Test
    fun originalTimeOfDayStringsRemainByteForByteEquivalent() {
        val expected = linkedMapOf(
            CANONICAL_PLAN_ID to "[\"08:30:00\"]",
            DUPLICATE_PLAN_ID to "[\"08:30\",\"08:30\"]",
            EMPTY_ARRAY_PLAN_ID to "[]"
        )
        createV2Database().use { database ->
            expected.forEach { (planId, timeOfDay) ->
                insertV2Plan(database, planId, timeOfDay)
            }
        }

        migrateToV3().use { database ->
            database.query(
                "SELECT id, timeOfDay FROM medication_plans ORDER BY id"
            ).use { cursor ->
                assertEquals(expected.size, cursor.count)
                while (cursor.moveToNext()) {
                    assertEquals(expected.getValue(cursor.getString(0)), cursor.getString(1))
                }
            }
            database.query(
                "SELECT localTime FROM scheduled_dose_slots WHERE planId = ?",
                arrayOf(CANONICAL_PLAN_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("08:30", cursor.getString(0))
            }
        }
    }

    @Test
    fun foreignKeysAreEnabledAfterMigration() {
        createV2Database().close()
        migrateToV3().close()
        withProductionV3Database { database ->
            assertEquals(1, pragmaInt(database, "foreign_keys"))
        }
    }

    @Test
    fun orphanSlotInsertIsRejectedByForeignKey() {
        createV2Database().close()
        migrateToV3().close()
        withProductionV3Database { database ->
            assertConstraintFailure {
                insertSlot(
                    database,
                    id = "20000000-0000-0000-0000-000000000001",
                    planId = "ffffffff-ffff-ffff-ffff-ffffffffffff",
                    position = 0
                )
            }
        }
    }

    @Test
    fun deletingPlanCascadesOnlyItsSlots() {
        createV2Database().use { database ->
            insertV2Plan(database, CASCADE_PLAN_A, "[\"08:30\"]")
            insertV2Plan(database, CASCADE_PLAN_B, "[\"09:30\"]")
        }

        migrateToV3().close()
        withProductionV3Database { database ->
            database.execSQL("DELETE FROM medication_plans WHERE id = ?", arrayOf(CASCADE_PLAN_A))
            assertEquals(0, slotCount(database, CASCADE_PLAN_A))
            assertEquals(1, slotCount(database, CASCADE_PLAN_B))
        }
    }

    @Test
    fun uniquePlanPositionRejectsOnlyTrueConflicts() {
        createV2Database().use { database ->
            insertV2Plan(database, UNIQUE_PLAN_A, "[]")
            insertV2Plan(database, UNIQUE_PLAN_B, "[]")
        }

        migrateToV3().use { database ->
            insertSlot(database, "30000000-0000-0000-0000-000000000001", UNIQUE_PLAN_A, 0)
            assertConstraintFailure {
                insertSlot(database, "30000000-0000-0000-0000-000000000002", UNIQUE_PLAN_A, 0)
            }
            insertSlot(database, "30000000-0000-0000-0000-000000000003", UNIQUE_PLAN_B, 0)
            insertSlot(database, "30000000-0000-0000-0000-000000000004", UNIQUE_PLAN_A, 1)
            assertEquals(2, slotCount(database, UNIQUE_PLAN_A))
            assertEquals(1, slotCount(database, UNIQUE_PLAN_B))
        }
    }

    @Test
    fun nonMinutePlanFailsWithStructuredContext() {
        createV2Database().use { database ->
            insertV2Plan(database, INVALID_PLAN_ID, "[\"08:00\",\"20:30:15\"]")
        }

        val thrown = runMigrationExpectingFailure()
        val migrationError = thrown.findCause<LegacyMigrationException>()
        assertNotNull("Expected LegacyMigrationException in cause chain", migrationError)
        migrationError!!
        assertEquals("medication_plans", migrationError.tableName)
        assertNotNull(migrationError.rowFingerprint)
        val error = migrationError.error as LegacyMigrationError.NonMinuteLocalTime
        assertEquals(UUID.fromString(INVALID_PLAN_ID), error.planId)
        assertEquals(1, error.position)
        assertEquals("20:30:15", error.originalValue)
    }

    @Test
    fun failedMigrationRollsBackSchemaDataAndUserVersion() {
        createV2Database().use { database ->
            insertV2Event(database, EVENT_ID, 12.5)
            insertV2Plan(database, INVALID_PLAN_ID, "[\"08:00\",\"20:30:15\"]")
        }

        runMigrationExpectingFailure()

        val helper = openExistingV2Database()
        try {
            val database = helper.writableDatabase
            assertEquals(2, pragmaInt(database, "user_version"))
            assertEquals(V2_EVENT_COLUMNS, columnNames(database, "dose_events"))
            assertFalse(tableExists(database, "scheduled_dose_slots"))
            assertFalse(indexExists(database, "index_scheduled_dose_slots_planId"))
            assertFalse(indexExists(database, "index_scheduled_dose_slots_planId_position"))
            database.query(
                "SELECT route, timeH, doseMG, ester, extras FROM dose_events WHERE id = ?",
                arrayOf(EVENT_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ORAL", cursor.getString(0))
                assertEquals(12.5, cursor.getDouble(1), 0.0)
                assertEquals(2.0, cursor.getDouble(2), 0.0)
                assertEquals("EV", cursor.getString(3))
                assertEquals("{}", cursor.getString(4))
            }
            database.query(
                "SELECT timeOfDay FROM medication_plans WHERE id = ?",
                arrayOf(INVALID_PLAN_ID)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("[\"08:00\",\"20:30:15\"]", cursor.getString(0))
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun runtimeEntityStrictlyDerivesLegitimateOccurredAtValues() {
        val ordinary = runtimeEntity(12.5)
        val epochZero = runtimeEntity(0.0)

        assertEquals(45_000_000L, ordinary.occurredAtEpochMillis)
        assertEquals(0L, epochZero.occurredAtEpochMillis)
    }

    @Test
    fun runtimeEntityRejectsEveryInvalidLegacyTimeValue() {
        assertRuntimeEntityFailure(Double.NaN)
        assertRuntimeEntityFailure(Double.POSITIVE_INFINITY)
        assertRuntimeEntityFailure(Double.NEGATIVE_INFINITY)
        assertRuntimeEntityFailure(Double.MAX_VALUE)
        assertRuntimeEntityFailure(-Double.MAX_VALUE)
    }

    private fun createV2Database(): SupportSQLiteDatabase =
        migrationHelper.createDatabase(databaseName, 2)

    private fun migrateToV3(): SupportSQLiteDatabase =
        migrationHelper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3)

    private fun withProductionV3Database(block: (SupportSQLiteDatabase) -> Unit) {
        val roomDatabase = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            block(roomDatabase.openHelper.writableDatabase)
        } finally {
            roomDatabase.close()
        }
    }

    private fun runMigrationExpectingFailure(): Throwable {
        var failure: Throwable? = null
        try {
            migrateToV3().close()
        } catch (error: Throwable) {
            failure = error
        }
        assertNotNull("Migration must fail", failure)
        return failure!!
    }

    private fun openExistingV2Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        fail("Failed migration database must already exist as v2")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        fail("Failed migration database must remain at v2")
                    }
                }
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun insertV2Event(
        database: SupportSQLiteDatabase,
        id: String,
        timeH: Double,
        route: String = "ORAL",
        doseMG: Double = 2.0,
        ester: String = "EV",
        extras: String = "{}"
    ) {
        database.execSQL(
            """
            INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(id, route, timeH, doseMG, ester, extras)
        )
    }

    private fun insertV2Plan(
        database: SupportSQLiteDatabase,
        id: String,
        timeOfDay: String
    ) {
        database.execSQL(
            """
            INSERT INTO medication_plans(
                id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                id,
                "Synthetic plan $id",
                "ORAL",
                "E2",
                1.0,
                "DAILY",
                timeOfDay,
                "[]",
                1,
                1,
                "{}",
                0L
            )
        )
    }

    private fun insertSlot(
        database: SupportSQLiteDatabase,
        id: String,
        planId: String,
        position: Int
    ) {
        database.execSQL(
            """
            INSERT INTO scheduled_dose_slots(id, planId, localTime, position)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(id, planId, "08:30", position)
        )
    }

    private fun assertEmptyPlanMigration(planId: String, rawTimeOfDay: String) {
        createV2Database().use { database ->
            insertV2Plan(database, planId, rawTimeOfDay)
        }
        migrateToV3().use { database ->
            assertEquals(0, slotCount(database, planId))
            database.query(
                "SELECT timeOfDay FROM medication_plans WHERE id = ?",
                arrayOf(planId)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(rawTimeOfDay, cursor.getString(0))
            }
        }
    }

    private fun assertSlots(
        database: SupportSQLiteDatabase,
        planId: String,
        expected: List<SlotRow>
    ) {
        val actual = database.query(
            """
            SELECT id, planId, localTime, position
            FROM scheduled_dose_slots
            WHERE planId = ?
            ORDER BY position
            """.trimIndent(),
            arrayOf(planId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        SlotRow(
                            id = cursor.getString(0),
                            planId = cursor.getString(1),
                            localTime = cursor.getString(2),
                            position = cursor.getInt(3)
                        )
                    )
                }
            }
        }
        assertEquals(expected, actual)
    }

    private fun assertSlotForeignKey(database: SupportSQLiteDatabase) {
        database.query("PRAGMA foreign_key_list(`scheduled_dose_slots`)").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("medication_plans", cursor.getString(2))
            assertEquals("planId", cursor.getString(3))
            assertEquals("id", cursor.getString(4))
            assertEquals("NO ACTION", cursor.getString(5))
            assertEquals("CASCADE", cursor.getString(6))
        }
    }

    private fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SQLite constraint failure")
        } catch (_: SQLiteConstraintException) {
            // Expected.
        }
    }

    private fun tableNames(database: SupportSQLiteDatabase): Set<String> =
        database.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
                AND name NOT LIKE 'room_%'
                AND name != 'android_metadata'
            """.trimIndent()
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    private fun columnNames(database: SupportSQLiteDatabase, tableName: String): List<String> =
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun explicitIndexNames(
        database: SupportSQLiteDatabase,
        tableName: String
    ): Set<String> = database.query(
        "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL",
        arrayOf(tableName)
    ).use { cursor ->
        buildSet {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun tableExists(database: SupportSQLiteDatabase, tableName: String): Boolean =
        database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(tableName)
        ).use { it.moveToFirst() }

    private fun indexExists(database: SupportSQLiteDatabase, indexName: String): Boolean =
        database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(indexName)
        ).use { it.moveToFirst() }

    private fun slotCount(database: SupportSQLiteDatabase, planId: String): Int =
        database.query(
            "SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = ?",
            arrayOf(planId)
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun pragmaInt(database: SupportSQLiteDatabase, pragma: String): Int =
        database.query("PRAGMA $pragma").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return current
            current = current.cause
        }
        return null
    }

    private fun runtimeEntity(timeH: Double): DoseEventEntity = DoseEventEntity(
        id = UUID.fromString(EVENT_ID),
        route = "ORAL",
        timeH = timeH,
        doseMG = 2.0,
        ester = "EV",
        extras = emptyMap()
    )

    private fun assertRuntimeEntityFailure(timeH: Double) {
        try {
            runtimeEntity(timeH)
            fail("Invalid timeH must fail strict Entity construction")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private data class SlotRow(
        val id: String,
        val planId: String,
        val localTime: String,
        val position: Int
    )

    private companion object {
        const val EVENT_ID = "10000000-0000-0000-0000-000000000001"
        const val FIXED_VECTOR_PLAN_ID = "00000000-0000-0000-0000-000000000001"
        const val FIXED_VECTOR_SLOT_ID = "17d1fd14-9d70-5344-beaa-0b158c9f62f4"
        const val ORDERED_PLAN_ID = "00000000-0000-0000-0000-000000000010"
        const val DUPLICATE_PLAN_ID = "00000000-0000-0000-0000-000000000011"
        const val EMPTY_STRING_PLAN_ID = "00000000-0000-0000-0000-000000000012"
        const val EMPTY_ARRAY_PLAN_ID = "00000000-0000-0000-0000-000000000013"
        const val CANONICAL_PLAN_ID = "00000000-0000-0000-0000-000000000014"
        const val INVALID_PLAN_ID = "00000000-0000-0000-0000-000000000015"
        const val CASCADE_PLAN_A = "00000000-0000-0000-0000-000000000016"
        const val CASCADE_PLAN_B = "00000000-0000-0000-0000-000000000017"
        const val UNIQUE_PLAN_A = "00000000-0000-0000-0000-000000000018"
        const val UNIQUE_PLAN_B = "00000000-0000-0000-0000-000000000019"

        val V2_EVENT_COLUMNS = listOf(
            "id", "route", "timeH", "doseMG", "ester", "extras"
        )
        val V3_EVENT_COLUMNS = V2_EVENT_COLUMNS + listOf(
            "occurredAtEpochMillis", "zoneId", "localDate", "slotId",
            "source", "status", "revision"
        )
        val SLOT_COLUMNS = listOf("id", "planId", "localTime", "position")
        val SLOT_INDICES = setOf(
            "index_scheduled_dose_slots_planId",
            "index_scheduled_dose_slots_planId_position"
        )
    }
}
