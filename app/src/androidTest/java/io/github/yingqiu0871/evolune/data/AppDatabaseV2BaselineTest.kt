package io.github.yingqiu0871.evolune.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseV2BaselineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val testContext: Context = instrumentation.context

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @Before
    fun deleteDatabaseBeforeTest() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun deleteDatabaseAfterTest() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun generatedV2SchemaMatchesCurrentContract() {
        migrationHelper.createDatabase(TEST_DATABASE, DATABASE_VERSION).use { database ->
            assertEquals(
                setOf("dose_events", "medication_plans"),
                database.query(
                    "SELECT name FROM sqlite_master " +
                        "WHERE type = 'table' AND name NOT LIKE 'room_%' AND name != 'android_metadata'"
                ).use { cursor ->
                    buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(0))
                    }
                }
            )

            assertColumns(database, "dose_events", DOSE_EVENT_COLUMNS)
            assertColumns(database, "medication_plans", MEDICATION_PLAN_COLUMNS)
            assertNoExplicitIndices(database)
            assertIdentityHashMatchesGeneratedSchema(database)
        }
    }

    @Test
    fun syntheticV2FixtureSurvivesCloseAndReopenWithoutPrecisionLoss() {
        migrationHelper.createDatabase(TEST_DATABASE, DATABASE_VERSION).use { database ->
            SYNTHETIC_EVENTS.forEach { event ->
                database.execSQL(
                    """
                    INSERT INTO dose_events(id, route, timeH, doseMG, ester, extras)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        event.id,
                        event.route,
                        event.timeH,
                        event.doseMG,
                        event.ester,
                        event.extras
                    )
                )
            }
            SYNTHETIC_PLANS.forEach { plan ->
                database.execSQL(
                    """
                    INSERT INTO medication_plans(
                        id, name, route, ester, doseMG, scheduleType, timeOfDay,
                        daysOfWeek, intervalDays, isEnabled, extras, createdAt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        plan.id,
                        plan.name,
                        plan.route,
                        plan.ester,
                        plan.doseMG,
                        plan.scheduleType,
                        plan.timeOfDay,
                        plan.daysOfWeek,
                        plan.intervalDays,
                        plan.isEnabled,
                        plan.extras,
                        plan.createdAt
                    )
                )
            }
        }

        val reopened = openExistingV2Database()
        try {
            val database = reopened.writableDatabase
            assertSyntheticEvents(database)
            assertSyntheticPlans(database)
            assertIdentityHashMatchesGeneratedSchema(database)
        } finally {
            reopened.close()
        }
    }

    private fun openExistingV2Database(): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(DATABASE_VERSION) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        fail("Existing v2 database must not be created during raw reopen")
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        fail("Existing v2 database must not be upgraded during raw reopen")
                    }
                }
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun assertColumns(
        database: SupportSQLiteDatabase,
        tableName: String,
        expected: List<ColumnContract>
    ) {
        val actual = database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                val typeIndex = cursor.getColumnIndexOrThrow("type")
                val notNullIndex = cursor.getColumnIndexOrThrow("notnull")
                val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                val primaryKeyIndex = cursor.getColumnIndexOrThrow("pk")
                while (cursor.moveToNext()) {
                    add(
                        ColumnContract(
                            name = cursor.getString(nameIndex),
                            affinity = cursor.getString(typeIndex),
                            notNull = cursor.getInt(notNullIndex) == 1,
                            defaultValue = if (cursor.isNull(defaultIndex)) {
                                null
                            } else {
                                cursor.getString(defaultIndex)
                            },
                            primaryKeyPosition = cursor.getInt(primaryKeyIndex)
                        )
                    )
                }
            }
        }

        assertEquals(expected, actual)
        actual.forEach { column ->
            assertEquals("$tableName.${column.name} must remain NOT NULL", true, column.notNull)
            assertNull("$tableName.${column.name} must not gain a SQL default", column.defaultValue)
        }
    }

    private fun assertNoExplicitIndices(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
        ).use { cursor ->
            assertFalse("Room v2 must not contain explicit indices", cursor.moveToFirst())
        }
    }

    private fun assertIdentityHashMatchesGeneratedSchema(database: SupportSQLiteDatabase) {
        val expectedHash = testContext.assets.open(SCHEMA_ASSET).bufferedReader().use { reader ->
            JSONObject(reader.readText()).getJSONObject("database").getString("identityHash")
        }
        val actualHash = database.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            cursor.getString(0)
        }

        assertNotNull(actualHash)
        assertEquals(expectedHash, actualHash)
    }

    private fun assertSyntheticEvents(database: SupportSQLiteDatabase) {
        val expectedById = SYNTHETIC_EVENTS.associateBy { it.id }
        database.query(
            "SELECT id, route, timeH, doseMG, ester, extras FROM dose_events ORDER BY id"
        ).use { cursor ->
            assertEquals(expectedById.size, cursor.count)
            while (cursor.moveToNext()) {
                val expected = expectedById.getValue(cursor.getString(0))
                assertEquals(expected.route, cursor.getString(1))
                assertEquals(expected.timeH, cursor.getDouble(2), 0.0)
                assertEquals(expected.doseMG, cursor.getDouble(3), 0.0)
                assertEquals(expected.ester, cursor.getString(4))
                assertEquals(expected.extras, cursor.getString(5))
            }
        }
    }

    private fun assertSyntheticPlans(database: SupportSQLiteDatabase) {
        val expectedById = SYNTHETIC_PLANS.associateBy { it.id }
        database.query(
            """
            SELECT id, name, route, ester, doseMG, scheduleType, timeOfDay,
                daysOfWeek, intervalDays, isEnabled, extras, createdAt
            FROM medication_plans ORDER BY id
            """.trimIndent()
        ).use { cursor ->
            assertEquals(expectedById.size, cursor.count)
            while (cursor.moveToNext()) {
                val expected = expectedById.getValue(cursor.getString(0))
                assertEquals(expected.name, cursor.getString(1))
                assertEquals(expected.route, cursor.getString(2))
                assertEquals(expected.ester, cursor.getString(3))
                assertEquals(expected.doseMG, cursor.getDouble(4), 0.0)
                assertEquals(expected.scheduleType, cursor.getString(5))
                assertEquals(expected.timeOfDay, cursor.getString(6))
                assertEquals(expected.daysOfWeek, cursor.getString(7))
                assertEquals(expected.intervalDays, cursor.getInt(8))
                assertEquals(expected.isEnabled, cursor.getInt(9))
                assertEquals(expected.extras, cursor.getString(10))
                assertEquals(expected.createdAt, cursor.getLong(11))
            }
        }
    }

    private data class ColumnContract(
        val name: String,
        val affinity: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int
    )

    private data class SyntheticEvent(
        val id: String,
        val route: String,
        val timeH: Double,
        val doseMG: Double,
        val ester: String,
        val extras: String
    )

    private data class SyntheticPlan(
        val id: String,
        val name: String,
        val route: String,
        val ester: String,
        val doseMG: Double,
        val scheduleType: String,
        val timeOfDay: String,
        val daysOfWeek: String,
        val intervalDays: Int,
        val isEnabled: Int,
        val extras: String,
        val createdAt: Long
    )

    private companion object {
        const val TEST_DATABASE = "phase1-batch1-v2-baseline"
        const val DATABASE_VERSION = 2
        const val SCHEMA_ASSET = "io.github.yingqiu0871.evolune.data.AppDatabase/2.json"

        val DOSE_EVENT_COLUMNS = listOf(
            ColumnContract("id", "TEXT", true, null, 1),
            ColumnContract("route", "TEXT", true, null, 0),
            ColumnContract("timeH", "REAL", true, null, 0),
            ColumnContract("doseMG", "REAL", true, null, 0),
            ColumnContract("ester", "TEXT", true, null, 0),
            ColumnContract("extras", "TEXT", true, null, 0)
        )

        val MEDICATION_PLAN_COLUMNS = listOf(
            ColumnContract("id", "TEXT", true, null, 1),
            ColumnContract("name", "TEXT", true, null, 0),
            ColumnContract("route", "TEXT", true, null, 0),
            ColumnContract("ester", "TEXT", true, null, 0),
            ColumnContract("doseMG", "REAL", true, null, 0),
            ColumnContract("scheduleType", "TEXT", true, null, 0),
            ColumnContract("timeOfDay", "TEXT", true, null, 0),
            ColumnContract("daysOfWeek", "TEXT", true, null, 0),
            ColumnContract("intervalDays", "INTEGER", true, null, 0),
            ColumnContract("isEnabled", "INTEGER", true, null, 0),
            ColumnContract("extras", "TEXT", true, null, 0),
            ColumnContract("createdAt", "INTEGER", true, null, 0)
        )

        val SYNTHETIC_EVENTS = listOf(
            SyntheticEvent(
                "00000000-0000-0000-0000-000000000001",
                "ORAL",
                0.0,
                1.0,
                "E2",
                "{}"
            ),
            SyntheticEvent(
                "00000000-0000-0000-0000-000000000002",
                "SUBLINGUAL",
                1.0,
                2.0,
                "E2",
                "{\"SUBLINGUAL_TIER\":2.0}"
            ),
            SyntheticEvent(
                "00000000-0000-0000-0000-000000000003",
                "INJECTION",
                12345.6789012345,
                5.0,
                "EV",
                "{\"CONCENTRATION_MG_ML\":20.0}"
            ),
            SyntheticEvent(
                "00000000-0000-0000-0000-000000000004",
                "PATCH_APPLY",
                -876600.5,
                0.0,
                "E2",
                "{\"RELEASE_RATE_UG_PER_DAY\":50.0}"
            ),
            SyntheticEvent(
                "00000000-0000-0000-0000-000000000005",
                "GEL",
                876600.125,
                0.75,
                "E2",
                "{\"AREA_CM2\":750.0}"
            )
        )

        val SYNTHETIC_PLANS = listOf(
            SyntheticPlan(
                "10000000-0000-0000-0000-000000000001",
                "Synthetic daily plan",
                "ORAL",
                "E2",
                1.0,
                "DAILY",
                "[\"08:00\",\"20:30:15\"]",
                "[]",
                1,
                1,
                "{}",
                0L
            ),
            SyntheticPlan(
                "10000000-0000-0000-0000-000000000002",
                "Synthetic weekly plan",
                "INJECTION",
                "EV",
                5.0,
                "WEEKLY",
                "[\"09:15\"]",
                "[1,3,5]",
                7,
                0,
                "{\"CONCENTRATION_MG_ML\":20.0}",
                4102444800000L
            )
        )
    }
}
