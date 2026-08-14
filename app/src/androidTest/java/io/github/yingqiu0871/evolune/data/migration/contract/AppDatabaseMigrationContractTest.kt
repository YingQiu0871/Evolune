package io.github.yingqiu0871.evolune.data.migration.contract

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.migration.Migration
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.migration.MIGRATION_2_3
import io.github.yingqiu0871.evolune.data.repository.RoomDoseEventRepository
import io.github.yingqiu0871.evolune.data.repository.RoomMedicationPlanRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationContractTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val databaseNames = mutableSetOf<String>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @After
    fun deleteDatabases() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun declarativeMatrixLocksValidityStagesAndBatchOwnership() {
        val cases = MigrationContractMatrix.allCases

        assertEquals(cases.size, cases.map { it.name }.toSet().size)
        assertEquals(18, MigrationContractMatrix.validCases.size)
        assertEquals(5, MigrationContractMatrix.currentPreflightRejections.size)
        assertEquals(13, MigrationContractMatrix.batch8BRejections.size)
        assertEquals(4, MigrationContractMatrix.notRepresentableCases.size)
        assertTrue(MigrationContractMatrix.validCases.all { it.validity == ContractValidity.VALID })
        assertTrue(MigrationContractMatrix.validCases.none { it.requiresBatch8B })
        assertTrue(MigrationContractMatrix.currentPreflightRejections.all {
            it.rejectionStage == RejectionStage.MIGRATION_PREFLIGHT && !it.requiresBatch8B
        })
        assertTrue(MigrationContractMatrix.batch8BRejections.all {
            it.validity == ContractValidity.INVALID_UNMIGRATABLE && it.requiresBatch8B
        })
        assertTrue(MigrationContractMatrix.notRepresentableCases.all {
            it.validity == ContractValidity.NOT_REPRESENTABLE
        })
    }

    @Test
    fun validSyntheticMatrixMigratesAndEveryAggregateIsRepositoryReadable() = runBlocking {
        val databaseName = databaseName("valid-matrix")
        val fixture = validFixture()
        createV2Database(databaseName).use(fixture::insertInto)
        migrateToV3(databaseName).close()

        withProductionDatabase(databaseName) { database ->
            val eventRepository = RoomDoseEventRepository(database)
            val planRepository = RoomMedicationPlanRepository(database)
            fixture.events.forEach { row ->
                assertNotNull("Event ${row.id} must be repository-readable", eventRepository.getById(UUID.fromString(row.id)))
            }
            fixture.plans.forEach { row ->
                assertNotNull("Plan ${row.id} must be repository-readable", planRepository.getById(UUID.fromString(row.id)))
            }
            assertEquals(fixture.events.size, rowCount(database.openHelper.readableDatabase, "dose_events"))
            assertEquals(fixture.plans.size, rowCount(database.openHelper.readableDatabase, "medication_plans"))
            assertEquals(13, rowCount(database.openHelper.readableDatabase, "scheduled_dose_slots"))
        }
    }

    @Test
    fun currentPreflightCasesRejectAndRollBackToExactV2Structure() {
        MigrationContractMatrix.currentPreflightRejections.forEachIndexed { index, case ->
            val databaseName = databaseName("preflight-$index")
            createV2Database(databaseName).use { database ->
                RawV2Fixture(
                    events = listOf(V2EventRow(id = baselineEventId(index))),
                    plans = listOf(V2PlanRow(id = baselinePlanId(index), timeOfDay = "[\"08:30\"]"))
                ).insertInto(database)
                case.fixture!!.insertInto(database)
            }

            val failure = runCatching { migrateToV3(databaseName).close() }.exceptionOrNull()
            assertNotNull("${case.name} must fail migration", failure)
            openExistingV2Database(databaseName).use { helper ->
                val database = helper.writableDatabase
                assertEquals(2, pragmaInt(database, "user_version"))
                assertFalse(tableExists(database, "scheduled_dose_slots"))
                assertEquals(V2_EVENT_COLUMNS, columnNames(database, "dose_events"))
                assertEquals(V2_PLAN_COLUMNS, columnNames(database, "medication_plans"))
            }
        }
    }

    @Test
    fun batch8BMapperCasesRejectAndRollBackBeforeRepositoryOpen() {
        val cases = MigrationContractMatrix.batch8BRejections.filter {
            it.currentOutcome == CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS &&
                it.rejectionStage == RejectionStage.PRODUCTION_MAPPER
        }

        cases.forEachIndexed { index, case ->
            assertRejectsAndRestoresV2(case, "mapper-$index")
        }
    }

    @Test
    fun batch8BConverterCasesRejectAndRollBackBeforeRepositoryOpen() {
        val cases = MigrationContractMatrix.batch8BRejections.filter {
            it.rejectionStage == RejectionStage.PRODUCTION_CONVERTER
        }

        cases.forEachIndexed { index, case ->
            assertRejectsAndRestoresV2(case, "converter-$index")
        }
    }

    @Test
    fun noncanonicalEnabledIntegerRejectsAndRollsBack() {
        val case = MigrationContractMatrix.batch8BRejections.single {
            it.name == "noncanonical-enabled-integer"
        }
        assertRejectsAndRestoresV2(case, "enabled-coercion")
    }

    @Test
    fun completeDatasetPreflightRejectsLateInvalidRowBeforeAnyBackfill() {
        val invalidCase = MigrationContractMatrix.batch8BRejections.single {
            it.name == "unknown-plan-schedule-type"
        }
        val fixture = RawV2Fixture(
            events = (0 until 12).map { index -> V2EventRow(id = eventId(700 + index)) },
            plans = (0 until 12).map { index -> V2PlanRow(id = planId(700 + index)) } +
                invalidCase.fixture!!.plans
        )
        assertFixtureRejectsAndRestoresV2(fixture, "late-invalid")
    }

    @Test
    fun mutationAndPostconditionFailuresRollBackEntireUpgradeTransaction() {
        FaultPoint.entries.forEach { faultPoint ->
            val fixture = RawV2Fixture(
                events = listOf(V2EventRow(id = eventId(800 + faultPoint.ordinal))),
                plans = listOf(
                    V2PlanRow(
                        id = planId(800 + faultPoint.ordinal),
                        timeOfDay = "[\"08:30\"]"
                    )
                )
            )
            val databaseName = databaseName("atomic-${faultPoint.name.lowercase()}")
            val expected = createV2Database(databaseName).use { database ->
                fixture.insertInto(database)
                snapshotV2(database)
            }

            val failure = runCatching {
                migrationHelper.runMigrationsAndValidate(
                    databaseName,
                    3,
                    true,
                    faultingMigration(faultPoint)
                ).close()
            }.exceptionOrNull()
            assertNotNull("$faultPoint must fail the migration", failure)

            openExistingV2Database(databaseName).use { helper ->
                val database = helper.writableDatabase
                assertEquals(2, pragmaInt(database, "user_version"))
                assertFalse(tableExists(database, "scheduled_dose_slots"))
                assertEquals(V2_EVENT_COLUMNS, columnNames(database, "dose_events"))
                assertEquals(V2_PLAN_COLUMNS, columnNames(database, "medication_plans"))
                assertEquals(expected, snapshotV2(database))
            }
        }
    }

    @Test
    fun exactV2SchemaRejectsNotRepresentableNullNanAndDuplicatePrimaryKey() {
        val databaseName = databaseName("not-representable")
        createV2Database(databaseName).use { database ->
            assertConstraintFailure {
                V2EventRow(id = "12000000-0000-0000-0000-000000000001", route = null)
                    .insertInto(database)
            }
            assertConstraintFailure {
                V2EventRow(id = "12000000-0000-0000-0000-000000000002", timeH = Double.NaN)
                    .insertInto(database)
            }
            val duplicate = V2EventRow(id = "12000000-0000-0000-0000-000000000003")
            duplicate.insertInto(database)
            assertConstraintFailure { duplicate.insertInto(database) }
        }
    }

    private fun validFixture(): RawV2Fixture {
        val routes = listOf(
            "INJECTION",
            "ORAL",
            "SUBLINGUAL",
            "GEL",
            "PATCH_APPLY",
            "PATCH_REMOVE",
            "ANTIANDROGEN"
        )
        val esters = listOf("E2", "EB", "EV", "EC", "EN")
        val timeVectors = listOf(
            1.0,
            0.0,
            -1.0,
            1_700_000_000_123L / MILLIS_PER_HOUR,
            -876_000.0,
            2_562_047_788_015.2153,
            -2_562_047_788_015.2153
        )
        val allExtras = """{"CONCENTRATION_MG_ML":20.0,"AREA_CM2":4.0,"RELEASE_RATE_UG_PER_DAY":50.0,"SUBLINGUAL_THETA":0.4,"SUBLINGUAL_TIER":2.0,"ANTI_ANDROGEN_TYPE":1.0}"""
        val events = routes.mapIndexed { index, route ->
            V2EventRow(
                id = eventId(index),
                route = route,
                timeH = timeVectors[index],
                ester = esters[index % esters.size],
                extras = if (index == 0) allExtras else "{}"
            )
        } + (0 until 64).map { index ->
            V2EventRow(
                id = eventId(100 + index),
                route = routes[index % routes.size],
                timeH = (index - 32).toDouble() / 1000.0,
                ester = esters[index % esters.size]
            )
        }
        val plans = listOf(
            V2PlanRow(id = planId(1), scheduleType = "DAILY", timeOfDay = ""),
            V2PlanRow(
                id = planId(2),
                scheduleType = "WEEKLY",
                timeOfDay = "[\"20:00\",\"08:30\",\"08:30\"]",
                daysOfWeek = "[1,2,3,4,5,6,7]",
                intervalDays = Int.MAX_VALUE,
                extras = allExtras
            ),
            V2PlanRow(
                id = planId(3),
                scheduleType = "CUSTOM",
                timeOfDay = "[\"00:00\",\"23:59\"]",
                daysOfWeek = "[]",
                intervalDays = 1,
                isEnabled = 0
            )
        ) + (4 until 12).map { index ->
            V2PlanRow(
                id = planId(index),
                route = routes[index % routes.size],
                ester = esters[index % esters.size],
                scheduleType = listOf("DAILY", "WEEKLY", "CUSTOM")[index % 3],
                timeOfDay = "[\"%02d:%02d\"]".format(index, index),
                daysOfWeek = if (index % 2 == 0) "[1,5]" else "[]",
                intervalDays = index
            )
        }
        return RawV2Fixture(events = events, plans = plans)
    }

    private fun createV2Database(name: String): SupportSQLiteDatabase =
        migrationHelper.createDatabase(name, 2)

    private fun assertRejectsAndRestoresV2(case: MigrationContractCase, suffix: String) {
        assertFixtureRejectsAndRestoresV2(requireNotNull(case.fixture), suffix)
    }

    private fun assertFixtureRejectsAndRestoresV2(fixture: RawV2Fixture, suffix: String) {
        val databaseName = databaseName(suffix)
        val expected = createV2Database(databaseName).use { database ->
            fixture.insertInto(database)
            snapshotV2(database)
        }

        assertNotNull(runCatching { migrateToV3(databaseName).close() }.exceptionOrNull())
        openExistingV2Database(databaseName).use { helper ->
            val database = helper.writableDatabase
            assertEquals(2, pragmaInt(database, "user_version"))
            assertFalse(tableExists(database, "scheduled_dose_slots"))
            assertEquals(V2_EVENT_COLUMNS, columnNames(database, "dose_events"))
            assertEquals(V2_PLAN_COLUMNS, columnNames(database, "medication_plans"))
            assertEquals(expected, snapshotV2(database))
        }
    }

    private fun snapshotV2(database: SupportSQLiteDatabase): List<List<String>> =
        listOf(
            snapshotRows(
                database,
                "SELECT ${V2_EVENT_COLUMNS.joinToString { "`$it`" }} FROM dose_events ORDER BY id"
            ),
            snapshotRows(
                database,
                "SELECT ${V2_PLAN_COLUMNS.joinToString { "`$it`" }} FROM medication_plans ORDER BY id"
            )
        ).flatten()

    private fun snapshotRows(
        database: SupportSQLiteDatabase,
        query: String
    ): List<List<String>> = database.query(query).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    (0 until cursor.columnCount).map { index ->
                        when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> "NULL"
                            android.database.Cursor.FIELD_TYPE_INTEGER -> "I:${cursor.getLong(index)}"
                            android.database.Cursor.FIELD_TYPE_FLOAT -> "F:${cursor.getDouble(index)}"
                            android.database.Cursor.FIELD_TYPE_STRING -> "T:${cursor.getString(index)}"
                            android.database.Cursor.FIELD_TYPE_BLOB ->
                                "B:${cursor.getBlob(index).joinToString { byte -> "%02x".format(byte) }}"
                            else -> "UNKNOWN"
                        }
                    }
                )
            }
        }
    }

    private fun migrateToV3(name: String): SupportSQLiteDatabase =
        migrationHelper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

    private fun faultingMigration(faultPoint: FaultPoint): Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3.migrate(db.withInjectedFailure(faultPoint))
        }
    }

    private fun SupportSQLiteDatabase.withInjectedFailure(
        faultPoint: FaultPoint
    ): SupportSQLiteDatabase = proxy(SupportSQLiteDatabase::class.java, this) { method, arguments ->
        val sql = arguments.firstOrNull() as? String
        when {
            method.name == "compileStatement" && sql != null -> {
                val statement = invokeDelegate(method, this, arguments) as SupportSQLiteStatement
                when {
                    faultPoint == FaultPoint.EVENT_UPDATE && sql.contains("UPDATE `dose_events`") ->
                        statement.failOn("executeUpdateDelete", faultPoint)
                    faultPoint == FaultPoint.SLOT_INSERT &&
                        sql.contains("INSERT INTO `scheduled_dose_slots`") ->
                        statement.failOn("executeInsert", faultPoint)
                    else -> statement
                }
            }
            faultPoint == FaultPoint.POSTCONDITION &&
                method.name == "query" &&
                sql == "PRAGMA integrity_check" -> throw SQLiteException("synthetic postcondition failure")
            else -> invokeDelegate(method, this, arguments)
        }
    }

    private fun SupportSQLiteStatement.failOn(
        methodName: String,
        faultPoint: FaultPoint
    ): SupportSQLiteStatement = proxy(SupportSQLiteStatement::class.java, this) { method, arguments ->
        if (method.name == methodName) {
            throw SQLiteException("synthetic ${faultPoint.name.lowercase()} failure")
        }
        invokeDelegate(method, this, arguments)
    }

    private fun <T> proxy(
        contract: Class<T>,
        delegate: T,
        invocation: (java.lang.reflect.Method, Array<out Any?>) -> Any?
    ): T = requireNotNull(contract.cast(
        Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, arguments ->
            invocation(method, arguments ?: emptyArray())
        }
    ))

    private fun invokeDelegate(
        method: java.lang.reflect.Method,
        delegate: Any,
        arguments: Array<out Any?>
    ): Any? = try {
        method.invoke(delegate, *arguments)
    } catch (failure: InvocationTargetException) {
        throw requireNotNull(failure.targetException)
    }

    private inline fun <T> withProductionDatabase(
        name: String,
        block: (AppDatabase) -> T
    ): T {
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        return try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun openExistingV2Database(name: String): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        fail("Expected an existing v2 fixture")
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) {
                        fail("Failed migration must retain v2")
                    }
                }
            )
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    private fun databaseName(suffix: String): String =
        "phase1-batch8a-$suffix".also(databaseNames::add)

    private fun rowCount(database: SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun pragmaInt(database: SupportSQLiteDatabase, pragma: String): Int =
        database.query("PRAGMA $pragma").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun tableExists(database: SupportSQLiteDatabase, table: String): Boolean =
        database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { it.moveToFirst() }

    private fun columnNames(database: SupportSQLiteDatabase, table: String): List<String> =
        database.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SQLite constraint failure")
        } catch (_: SQLiteConstraintException) {
            Unit
        }
    }

    private fun eventId(index: Int): String =
        "30000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private fun planId(index: Int): String =
        "40000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private fun baselineEventId(index: Int): String =
        "50000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private fun baselinePlanId(index: Int): String =
        "60000000-0000-0000-0000-${index.toString().padStart(12, '0')}"

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
        val V2_EVENT_COLUMNS = listOf("id", "route", "timeH", "doseMG", "ester", "extras")
        val V2_PLAN_COLUMNS = listOf(
            "id",
            "name",
            "route",
            "ester",
            "doseMG",
            "scheduleType",
            "timeOfDay",
            "daysOfWeek",
            "intervalDays",
            "isEnabled",
            "extras",
            "createdAt"
        )
    }

    private enum class FaultPoint {
        EVENT_UPDATE,
        SLOT_INSERT,
        POSTCONDITION
    }
}
