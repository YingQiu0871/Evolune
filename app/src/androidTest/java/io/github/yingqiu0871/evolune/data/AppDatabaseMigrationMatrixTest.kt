package io.github.yingqiu0871.evolune.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationMatrixTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @get:Rule
    val testName = TestName()

    private val databaseName: String
        get() = "phase1-batch4b-${testName.methodName}"

    @Before
    fun deleteDatabaseBeforeTest() {
        context.deleteDatabase(databaseName)
        resetProductionDatabaseSingleton()
        context.deleteDatabase(PRODUCTION_DATABASE_NAME)
    }

    @After
    fun deleteDatabaseAfterTest() {
        context.deleteDatabase(databaseName)
        resetProductionDatabaseSingleton()
        context.deleteDatabase(PRODUCTION_DATABASE_NAME)
    }

    @Test
    fun legalTimeHBoundaryMatrixMigratesWithIndependentExpectedValues() {
        val vectors = listOf(
            TimeVector("00000000-0000-0000-0000-000000000101", 0.0, 0L),
            TimeVector("00000000-0000-0000-0000-000000000102", 1.0, 3_600_000L),
            TimeVector("00000000-0000-0000-0000-000000000103", -1.0, -3_600_000L),
            TimeVector(
                "00000000-0000-0000-0000-000000000104",
                472_222.22225638886,
                1_700_000_000_123L
            ),
            TimeVector(
                "00000000-0000-0000-0000-000000000105",
                1.388888888888889e-7,
                1L
            ),
            TimeVector(
                "00000000-0000-0000-0000-000000000106",
                -1.388888888888889e-7,
                -1L
            ),
            TimeVector(
                "00000000-0000-0000-0000-000000000107",
                2_562_047_788_015.2153,
                9_223_372_036_854_774_784L
            ),
            TimeVector(
                "00000000-0000-0000-0000-000000000108",
                -2_562_047_788_015.2153,
                -9_223_372_036_854_774_784L
            )
        )
        createV2Database().use { database ->
            vectors.forEach { vector -> insertV2Event(database, vector.id, vector.timeH) }
        }

        migrateToV3().use { database ->
            database.query(
                "SELECT id, timeH, occurredAtEpochMillis FROM dose_events ORDER BY id"
            ).use { cursor ->
                assertEquals(vectors.size, cursor.count)
                vectors.forEach { expected ->
                    assertTrue(cursor.moveToNext())
                    assertEquals(expected.id, cursor.getString(0))
                    assertEquals(expected.timeH, cursor.getDouble(1), 0.0)
                    assertEquals(expected.expectedEpochMillis, cursor.getLong(2))
                }
            }
        }
    }

    @Test
    fun floatStorageClassMigratesNormally() {
        createV2Database().use { database ->
            insertV2Event(database, EVENT_ID, 12.25)
            assertEquals("real", storageClass(database, EVENT_ID))
        }
        migrateToV3().use { database ->
            assertEventTime(database, EVENT_ID, 12.25, 44_100_000L)
        }
    }

    @Test
    fun integerBindingIsNormalizedByExactV2RealAffinityAndMigrates() {
        createV2Database().use { database ->
            insertV2EventRaw(database, EVENT_ID, 2L)
            assertEquals("real", storageClass(database, EVENT_ID))
        }
        migrateToV3().use { database ->
            assertEventTime(database, EVENT_ID, 2.0, 7_200_000L)
        }
    }

    @Test
    fun exactV2NotNullConstraintRejectsNullBeforeMigration() {
        createV2Database().use { database ->
            assertConstraintFailure { insertV2EventRaw(database, EVENT_ID, null) }
            assertEquals(0, rowCount(database, "dose_events"))
        }
    }

    @Test
    fun androidSqliteCannotMaterializeNanInExactV2RealNotNullColumn() {
        createV2Database().use { database ->
            assertConstraintFailure {
                insertV2EventRaw(database, EVENT_ID, Double.NaN)
            }
            assertEquals(0, rowCount(database, "dose_events"))
        }
    }

    @Test
    fun positiveInfinityFailsAndRollsBack() {
        assertEventFailureRollback(Double.POSITIVE_INFINITY)
    }

    @Test
    fun negativeInfinityFailsAndRollsBack() {
        assertEventFailureRollback(Double.NEGATIVE_INFINITY)
    }

    @Test
    fun positiveEpochRangeOverflowFailsAndRollsBack() {
        assertEventFailureRollback(2_562_047_788_015.216)
    }

    @Test
    fun negativeEpochRangeOverflowFailsAndRollsBack() {
        assertEventFailureRollback(-2_562_047_788_015.216)
    }

    @Test
    fun positiveMultiplicationOverflowFailsAndRollsBack() {
        assertEventFailureRollback(Double.MAX_VALUE)
    }

    @Test
    fun negativeMultiplicationOverflowFailsAndRollsBack() {
        assertEventFailureRollback(-Double.MAX_VALUE)
    }

    @Test
    fun textTimeHStorageFailsBeforeNumericReadAndRollsBack() {
        assertRawEventFailureRollback("not-a-number", "text")
    }

    @Test
    fun blobTimeHStorageFailsBeforeNumericReadAndRollsBack() {
        assertRawEventFailureRollback(byteArrayOf(0x01, 0x02, 0x03), "blob")
    }

    @Test
    fun malformedPlanJsonFailsAndRollsBack() {
        assertPlanFailureRollback("[\"08:30\"")
    }

    @Test
    fun nonStringPlanElementFailsAndRollsBack() {
        assertPlanFailureRollback("[\"08:30\",42]")
    }

    @Test
    fun invalidLocalTimeFailsAndRollsBack() {
        assertPlanFailureRollback("[\"25:00\"]")
    }

    @Test
    fun nonMinutePlanTimeFailsAndRollsBack() {
        assertPlanFailureRollback("[\"08:30\",\"20:30:15\"]")
    }

    @Test
    fun plansAndSlotsPreserveOrderDuplicatesBoundariesAndLegacyStrings() {
        val plans = linkedMapOf(
            "00000000-0000-0000-0000-000000000201" to
                "[\"20:00\",\"08:30\",\"08:30\",\"00:00\",\"23:59\"]",
            "00000000-0000-0000-0000-000000000202" to "[]",
            "00000000-0000-0000-0000-000000000203" to "",
            "00000000-0000-0000-0000-000000000204" to
                "[\"08:30:00\",\"08:30\"]"
        )
        createV2Database().use { database ->
            plans.forEach { (id, rawTimes) -> insertV2Plan(database, id, rawTimes) }
        }

        migrateToV3().use { database ->
            plans.forEach { (id, rawTimes) ->
                assertEquals(rawTimes, planTimeOfDay(database, id))
            }
            val expectedTimes = mapOf(
                plans.keys.elementAt(0) to listOf("20:00", "08:30", "08:30", "00:00", "23:59"),
                plans.keys.elementAt(1) to emptyList(),
                plans.keys.elementAt(2) to emptyList(),
                plans.keys.elementAt(3) to listOf("08:30", "08:30")
            )
            expectedTimes.forEach { (planId, times) ->
                val slots = readSlots(database, planId)
                assertEquals(times.size, slots.size)
                times.forEachIndexed { position, localTime ->
                    val slot = slots[position]
                    assertEquals(planId, slot.planId)
                    assertEquals(position, slot.position)
                    assertEquals(localTime, slot.localTime)
                    assertEquals(slotIdV1(planId, position, localTime), slot.id)
                }
            }
            assertNotEquals(
                readSlots(database, plans.keys.first())[1].id,
                readSlots(database, plans.keys.first())[2].id
            )
            assertEquals(7, rowCount(database, "scheduled_dose_slots"))
        }
    }

    @Test
    fun foreignKeyIndexCascadeAndUniqueMatrixIsEnforced() {
        val planA = "00000000-0000-0000-0000-000000000211"
        val planB = "00000000-0000-0000-0000-000000000212"
        createV2Database().use { database ->
            insertV2Plan(database, planA, "[\"08:30\",\"08:30\"]")
            insertV2Plan(database, planB, "[\"08:30\"]")
        }
        migrateToV3().close()

        withProductionV3Database { database ->
            assertEquals(1, pragmaInt(database, "foreign_keys"))
            assertEquals(
                listOf("planId"),
                indexColumns(database, "index_scheduled_dose_slots_planId")
            )
            assertEquals(
                listOf("planId", "position"),
                indexColumns(database, "index_scheduled_dose_slots_planId_position")
            )
            assertFalse(hasUniqueLocalTimeIndex(database))
            assertConstraintFailure {
                insertSlot(
                    database,
                    "30000000-0000-0000-0000-000000000211",
                    "ffffffff-ffff-ffff-ffff-ffffffffffff",
                    0
                )
            }
            assertConstraintFailure {
                insertSlot(database, "30000000-0000-0000-0000-000000000212", planA, 0)
            }
            insertSlot(database, "30000000-0000-0000-0000-000000000213", planA, 2)
            insertSlot(database, "30000000-0000-0000-0000-000000000214", planB, 1)
            database.execSQL("DELETE FROM medication_plans WHERE id = ?", arrayOf(planA))
            assertEquals(0, slotCount(database, planA))
            assertEquals(2, slotCount(database, planB))
            assertEquals(0, foreignKeyViolationCount(database))
        }
    }

    @Test
    fun actualProductionMigrationChainUpgradesMinimalAuthorizedV1FixtureToV3() {
        createMinimalAuthorizedV1Database().use { helper ->
            insertV1Event(helper.writableDatabase, EVENT_ID, 12.5)
        }

        val roomDatabase = AppDatabase.getDatabase(context)
        try {
            val database = roomDatabase.openHelper.writableDatabase
            assertEquals(3, pragmaInt(database, "user_version"))
            assertEventTime(database, EVENT_ID, 12.5, 45_000_000L)
            assertTrue(tableExists(database, "medication_plans"))
            assertTrue(tableExists(database, "scheduled_dose_slots"))
            assertEquals(V3_IDENTITY_HASH, roomIdentityHash(database))
        } finally {
            roomDatabase.close()
            resetProductionDatabaseSingleton()
        }
    }

    @Test
    fun fixedSeedLongHistoryMigratesTwoThousandEventsAndOneHundredPlans() {
        val fixture = syntheticFixture(seed = STRESS_SEED, eventCount = 2_000, planCount = 100)
        createV2Database().use { database -> insertFixture(database, fixture) }

        val startedAtNanos = System.nanoTime()
        migrateToV3().use { database ->
            val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000
            Log.i(STRESS_LOG_TAG, "events=2000 plans=100 slots=${fixture.slotCount} elapsedMs=$elapsedMillis")
            assertFixture(database, fixture)
            assertEquals(0, foreignKeyViolationCount(database))
        }
    }

    @Test
    fun identicalFixturesInSeparateDatabasesProduceIdenticalMigrationResults() {
        val fixture = syntheticFixture(seed = REPLAY_SEED, eventCount = 250, planCount = 25)
        val firstName = "$databaseName-first"
        val secondName = "$databaseName-second"
        context.deleteDatabase(firstName)
        context.deleteDatabase(secondName)
        try {
            val first = migrateFixture(firstName, fixture)
            val second = migrateFixture(secondName, fixture)
            assertEquals(first, second)
        } finally {
            context.deleteDatabase(firstName)
            context.deleteDatabase(secondName)
        }
    }

    private fun createV2Database(): SupportSQLiteDatabase =
        migrationHelper.createDatabase(databaseName, 2)

    private fun migrateToV3(): SupportSQLiteDatabase =
        migrationHelper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3)

    private fun migrateFixture(name: String, fixture: SyntheticFixture): MigrationSnapshot {
        migrationHelper.createDatabase(name, 2).use { database -> insertFixture(database, fixture) }
        return migrationHelper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).use { database ->
            assertFixture(database, fixture)
            migrationSnapshot(database)
        }
    }

    private fun insertFixture(database: SupportSQLiteDatabase, fixture: SyntheticFixture) {
        database.beginTransaction()
        try {
            fixture.events.forEach { insertV2Event(database, it.id, it.timeH) }
            fixture.plans.forEach { insertV2Plan(database, it.id, it.rawTimeOfDay) }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun assertFixture(database: SupportSQLiteDatabase, fixture: SyntheticFixture) {
        assertEquals(fixture.events.size, rowCount(database, "dose_events"))
        assertEquals(fixture.plans.size, rowCount(database, "medication_plans"))
        assertEquals(fixture.slotCount, rowCount(database, "scheduled_dose_slots"))
        database.query(
            "SELECT id, timeH, occurredAtEpochMillis FROM dose_events ORDER BY id"
        ).use { cursor ->
            fixture.events.sortedBy { it.id }.forEach { expected ->
                assertTrue(cursor.moveToNext())
                assertEquals(expected.id, cursor.getString(0))
                assertEquals(expected.timeH, cursor.getDouble(1), 0.0)
                assertEquals(expected.expectedEpochMillis, cursor.getLong(2))
            }
            assertFalse(cursor.moveToNext())
        }
        fixture.plans.forEach { plan ->
            assertEquals(plan.rawTimeOfDay, planTimeOfDay(database, plan.id))
            val slots = readSlots(database, plan.id)
            assertEquals(plan.times.size, slots.size)
            plan.times.forEachIndexed { position, localTime ->
                assertEquals(
                    SlotRow(slotIdV1(plan.id, position, localTime), plan.id, localTime, position),
                    slots[position]
                )
            }
        }
    }

    private fun syntheticFixture(seed: Long, eventCount: Int, planCount: Int): SyntheticFixture {
        val random = Random(seed)
        val events = (0 until eventCount).map { index ->
            val timeH = when (index) {
                0 -> 0.0
                1 -> -1.0
                else -> {
                    val millis = random.nextInt().toLong()
                    millis.toDouble() / MILLIS_PER_HOUR
                }
            }
            SyntheticEvent(
                id = UUID(0x4000L + index, seed xor index.toLong()).toString(),
                timeH = timeH,
                expectedEpochMillis = Math.round(timeH * MILLIS_PER_HOUR)
            )
        }
        val plans = (0 until planCount).map { index ->
            val slotCount = random.nextInt(6)
            val times = (0 until slotCount).map { position ->
                val minuteOfDay = when {
                    position > 0 && position % 3 == 0 -> 510
                    else -> random.nextInt(24 * 60)
                }
                "%02d:%02d".format(java.util.Locale.ROOT, minuteOfDay / 60, minuteOfDay % 60)
            }
            val id = UUID(0x5000L + index, seed.inv() xor index.toLong()).toString()
            SyntheticPlan(id, times, jsonTimes(times))
        }
        return SyntheticFixture(events, plans)
    }

    private fun assertEventFailureRollback(timeH: Double) {
        createV2Database().use { database ->
            insertV2Event(database, BASELINE_EVENT_ID, 1.0)
            insertV2Event(database, EVENT_ID, timeH)
            insertV2Plan(database, BASELINE_PLAN_ID, "[\"08:30\"]")
        }
        assertMigrationFailureAndRollback()
    }

    private fun assertRawEventFailureRollback(value: Any, expectedStorageClass: String) {
        createV2Database().use { database ->
            insertV2Event(database, BASELINE_EVENT_ID, 1.0)
            insertV2EventRaw(database, EVENT_ID, value)
            assertEquals(expectedStorageClass, storageClass(database, EVENT_ID))
            insertV2Plan(database, BASELINE_PLAN_ID, "[\"08:30\"]")
        }
        assertMigrationFailureAndRollback()
    }

    private fun assertPlanFailureRollback(rawTimeOfDay: String) {
        createV2Database().use { database ->
            insertV2Event(database, BASELINE_EVENT_ID, 1.0)
            insertV2Plan(database, BASELINE_PLAN_ID, "[\"08:30\"]")
            insertV2Plan(database, INVALID_PLAN_ID, rawTimeOfDay)
        }
        assertMigrationFailureAndRollback()
    }

    private fun assertMigrationFailureAndRollback() {
        val before = openExistingV2Database().use { helper ->
            databaseSnapshot(helper.writableDatabase)
        }
        val failure = runCatching { migrateToV3().close() }.exceptionOrNull()
        assertNotNull("Migration must fail", failure)
        openExistingV2Database().use { helper ->
            val database = helper.writableDatabase
            assertEquals(2, pragmaInt(database, "user_version"))
            assertEquals(V2_EVENT_COLUMNS, columnNames(database, "dose_events"))
            assertFalse(tableExists(database, "scheduled_dose_slots"))
            assertFalse(indexExists(database, SLOT_PLAN_INDEX))
            assertFalse(indexExists(database, SLOT_POSITION_INDEX))
            assertEquals(V2_IDENTITY_HASH, roomIdentityHash(database))
            assertEquals(before, databaseSnapshot(database))
        }
    }

    private fun databaseSnapshot(database: SupportSQLiteDatabase): LegacySnapshot =
        LegacySnapshot(
            events = database.query(
                """
                SELECT id, route, typeof(timeH), quote(timeH), hex(timeH), doseMG, ester, extras
                FROM dose_events ORDER BY id
                """.trimIndent()
            ).use { cursor -> cursor.rowsAsStrings(8) },
            plans = database.query(
                "SELECT id, timeOfDay FROM medication_plans ORDER BY id"
            ).use { cursor -> cursor.rowsAsStrings(2) }
        )

    private fun migrationSnapshot(database: SupportSQLiteDatabase): MigrationSnapshot =
        MigrationSnapshot(
            events = database.query(
                """
                SELECT id, timeH, occurredAtEpochMillis, zoneId, localDate, slotId,
                    source, status, revision FROM dose_events ORDER BY id
                """.trimIndent()
            ).use { cursor -> cursor.rowsAsStrings(9) },
            slots = database.query(
                "SELECT id, planId, localTime, position FROM scheduled_dose_slots ORDER BY planId, position"
            ).use { cursor -> cursor.rowsAsStrings(4) },
            identityHash = roomIdentityHash(database),
            indices = explicitIndexNames(database, "scheduled_dose_slots"),
            foreignKeys = database.query(
                "PRAGMA foreign_key_list(`scheduled_dose_slots`)"
            ).use { cursor -> cursor.rowsAsStrings(cursor.columnCount) }
        )

    private fun Cursor.rowsAsStrings(columnCount: Int): List<List<String?>> = buildList {
        while (moveToNext()) {
            add((0 until columnCount).map { index -> if (isNull(index)) null else getString(index) })
        }
    }

    private fun createMinimalAuthorizedV1Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(PRODUCTION_DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        database.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `dose_events` (
                                `id` TEXT NOT NULL,
                                `route` TEXT NOT NULL,
                                `timeH` REAL NOT NULL,
                                `doseMG` REAL NOT NULL,
                                `ester` TEXT NOT NULL,
                                `extras` TEXT NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                }
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).also {
            it.writableDatabase
        }
    }

    private fun insertV1Event(database: SupportSQLiteDatabase, id: String, timeH: Double) {
        database.execSQL(
            "INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, "ORAL", timeH, 2.0, "EV", "{}")
        )
    }

    private fun openExistingV2Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        fail("Expected existing v2 database")
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        fail("Failed migration must leave user_version at 2")
                    }
                }
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun withProductionV3Database(block: (SupportSQLiteDatabase) -> Unit) {
        val roomDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            block(roomDatabase.openHelper.writableDatabase)
        } finally {
            roomDatabase.close()
        }
    }

    private fun insertV2Event(database: SupportSQLiteDatabase, id: String, timeH: Double) {
        insertV2EventRaw(database, id, timeH)
    }

    private fun insertV2EventRaw(database: SupportSQLiteDatabase, id: String, timeH: Any?) {
        database.execSQL(
            """
            INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(id, "ORAL", timeH, 2.0, "EV", "{}")
        )
    }

    private fun insertV2Plan(database: SupportSQLiteDatabase, id: String, timeOfDay: String) {
        database.execSQL(
            """
            INSERT INTO medication_plans(
                id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(id, "Synthetic $id", "ORAL", "E2", 1.0, "DAILY", timeOfDay, "[]", 1, 1, "{}", 0L)
        )
    }

    private fun insertSlot(database: SupportSQLiteDatabase, id: String, planId: String, position: Int) {
        database.execSQL(
            "INSERT INTO scheduled_dose_slots(id, planId, localTime, position) VALUES (?, ?, ?, ?)",
            arrayOf<Any?>(id, planId, "08:30", position)
        )
    }

    private fun assertEventTime(
        database: SupportSQLiteDatabase,
        id: String,
        expectedTimeH: Double,
        expectedEpochMillis: Long
    ) {
        database.query(
            "SELECT timeH, occurredAtEpochMillis FROM dose_events WHERE id = ?",
            arrayOf(id)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedTimeH, cursor.getDouble(0), 0.0)
            assertEquals(expectedEpochMillis, cursor.getLong(1))
        }
    }

    private fun readSlots(database: SupportSQLiteDatabase, planId: String): List<SlotRow> =
        database.query(
            "SELECT id, planId, localTime, position FROM scheduled_dose_slots WHERE planId = ? ORDER BY position",
            arrayOf(planId)
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(SlotRow(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getInt(3)))
                }
            }
        }

    private fun planTimeOfDay(database: SupportSQLiteDatabase, planId: String): String =
        database.query("SELECT timeOfDay FROM medication_plans WHERE id = ?", arrayOf(planId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun storageClass(database: SupportSQLiteDatabase, id: String): String =
        database.query("SELECT typeof(timeH) FROM dose_events WHERE id = ?", arrayOf(id)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun roomIdentityHash(database: SupportSQLiteDatabase): String =
        database.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun rowCount(database: SupportSQLiteDatabase, tableName: String): Int =
        database.query("SELECT COUNT(*) FROM `$tableName`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun slotCount(database: SupportSQLiteDatabase, planId: String): Int =
        database.query("SELECT COUNT(*) FROM scheduled_dose_slots WHERE planId = ?", arrayOf(planId)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun pragmaInt(database: SupportSQLiteDatabase, pragma: String): Int =
        database.query("PRAGMA $pragma").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun columnNames(database: SupportSQLiteDatabase, tableName: String): List<String> =
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
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

    private fun explicitIndexNames(database: SupportSQLiteDatabase, tableName: String): Set<String> =
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = ? AND sql IS NOT NULL",
            arrayOf(tableName)
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun indexColumns(database: SupportSQLiteDatabase, indexName: String): List<String> =
        database.query("PRAGMA index_info(`$indexName`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(2)) }
        }

    private fun hasUniqueLocalTimeIndex(database: SupportSQLiteDatabase): Boolean =
        database.query("PRAGMA index_list(`scheduled_dose_slots`)").use { indices ->
            while (indices.moveToNext()) {
                val indexName = indices.getString(indices.getColumnIndexOrThrow("name"))
                val unique = indices.getInt(indices.getColumnIndexOrThrow("unique")) == 1
                if (unique && indexColumns(database, indexName).contains("localTime")) return@use true
            }
            false
        }

    private fun foreignKeyViolationCount(database: SupportSQLiteDatabase): Int =
        database.query("PRAGMA foreign_key_check").use { it.count }

    private fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SQLite constraint failure")
        } catch (_: SQLiteConstraintException) {
            Unit
        }
    }

    private fun resetProductionDatabaseSingleton() {
        val instanceField = AppDatabase::class.java.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        (instanceField.get(null) as? AppDatabase)?.close()
        instanceField.set(null, null)
    }

    private fun slotIdV1(planId: String, position: Int, localTime: String): String {
        val canonicalName = "slot:v1:plan=$planId;position=$position;time=$localTime"
        return uuidV5(PROJECT_SLOT_NAMESPACE, canonicalName).toString()
    }

    private fun uuidV5(namespace: UUID, name: String): UUID {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(uuidBytes(namespace))
        val hash = digest.digest(name.toByteArray(StandardCharsets.UTF_8)).copyOf(16)
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        val buffer = ByteBuffer.wrap(hash)
        return UUID(buffer.long, buffer.long)
    }

    private fun uuidBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    private fun jsonTimes(times: List<String>): String =
        if (times.isEmpty()) "[]" else times.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")

    private data class TimeVector(val id: String, val timeH: Double, val expectedEpochMillis: Long)
    private data class SlotRow(val id: String, val planId: String, val localTime: String, val position: Int)
    private data class SyntheticEvent(val id: String, val timeH: Double, val expectedEpochMillis: Long)
    private data class SyntheticPlan(val id: String, val times: List<String>, val rawTimeOfDay: String)
    private data class SyntheticFixture(val events: List<SyntheticEvent>, val plans: List<SyntheticPlan>) {
        val slotCount: Int = plans.sumOf { it.times.size }
    }
    private data class LegacySnapshot(val events: List<List<String?>>, val plans: List<List<String?>>)
    private data class MigrationSnapshot(
        val events: List<List<String?>>,
        val slots: List<List<String?>>,
        val identityHash: String,
        val indices: Set<String>,
        val foreignKeys: List<List<String?>>
    )

    companion object {
        private const val MILLIS_PER_HOUR = 3_600_000.0
        private const val EVENT_ID = "10000000-0000-0000-0000-000000000101"
        private const val BASELINE_EVENT_ID = "10000000-0000-0000-0000-000000000102"
        private const val BASELINE_PLAN_ID = "20000000-0000-0000-0000-000000000101"
        private const val INVALID_PLAN_ID = "20000000-0000-0000-0000-000000000102"
        private const val PRODUCTION_DATABASE_NAME = "evolune_database"
        private const val V2_IDENTITY_HASH = "a8036e3f5ed6bb42d0e7289ac84039f3"
        private const val V3_IDENTITY_HASH = "c5f5e02cb04b048ca28fe96a74d61606"
        private const val SLOT_PLAN_INDEX = "index_scheduled_dose_slots_planId"
        private const val SLOT_POSITION_INDEX = "index_scheduled_dose_slots_planId_position"
        private const val STRESS_SEED = 0x4B4B2026L
        private const val REPLAY_SEED = 0x4B4B3036L
        private const val STRESS_LOG_TAG = "Batch4BMigration"
        private val PROJECT_SLOT_NAMESPACE = UUID.fromString("68559b97-4ddc-5be2-bcbd-9ab409f0d95b")
        private val V2_EVENT_COLUMNS = listOf("id", "route", "timeH", "doseMG", "ester", "extras")
    }
}
