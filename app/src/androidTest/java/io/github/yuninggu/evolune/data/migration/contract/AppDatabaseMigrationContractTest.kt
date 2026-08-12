package io.github.yuninggu.evolune.data.migration.contract

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.data.AppDatabase
import io.github.yuninggu.evolune.data.migration.MIGRATION_2_3
import io.github.yuninggu.evolune.data.repository.CorruptAggregateException
import io.github.yuninggu.evolune.data.repository.RoomDoseEventRepository
import io.github.yuninggu.evolune.data.repository.RoomMedicationPlanRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
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
    fun currentMigrationLetsMapperInvalidRowsReachRepositoryBoundary() = runBlocking {
        val cases = MigrationContractMatrix.batch8BRejections.filter {
            it.currentOutcome == CurrentMigrationOutcome.MIGRATES_BUT_REPOSITORY_REJECTS &&
                it.rejectionStage == RejectionStage.PRODUCTION_MAPPER
        }

        cases.forEachIndexed { index, case ->
            val databaseName = databaseName("mapper-$index")
            createV2Database(databaseName).use(case.fixture!!::insertInto)
            migrateToV3(databaseName).close()

            withProductionDatabase(databaseName) { database ->
                val error = when (case.aggregate) {
                    PersistedAggregate.DOSE_EVENT -> assertSuspendFails<CorruptAggregateException> {
                        val id = UUID.fromString(case.fixture.events.single().id)
                        RoomDoseEventRepository(database).getById(id)
                    }

                    PersistedAggregate.MEDICATION_PLAN -> assertSuspendFails<CorruptAggregateException> {
                        val id = UUID.fromString(case.fixture.plans.single().id)
                        RoomMedicationPlanRepository(database).getById(id)
                    }

                    PersistedAggregate.DATABASE -> error("No aggregate repository for database case")
                }
                assertNotNull(error.mappingError)
            }
        }
    }

    @Test
    fun currentMigrationLetsMalformedConverterPayloadsReachRepositoryBoundary() = runBlocking {
        val cases = MigrationContractMatrix.batch8BRejections.filter {
            it.rejectionStage == RejectionStage.PRODUCTION_CONVERTER
        }

        cases.forEachIndexed { index, case ->
            val databaseName = databaseName("converter-$index")
            createV2Database(databaseName).use(case.fixture!!::insertInto)
            migrateToV3(databaseName).close()

            withProductionDatabase(databaseName) { database ->
                when (case.aggregate) {
                    PersistedAggregate.DOSE_EVENT -> assertSuspendFails<SerializationException> {
                        val id = UUID.fromString(case.fixture.events.single().id)
                        RoomDoseEventRepository(database).getById(id)
                    }

                    PersistedAggregate.MEDICATION_PLAN -> assertSuspendFails<SerializationException> {
                        val id = UUID.fromString(case.fixture.plans.single().id)
                        RoomMedicationPlanRepository(database).getById(id)
                    }

                    PersistedAggregate.DATABASE -> error("No aggregate repository for database case")
                }
            }
        }
    }

    @Test
    fun noncanonicalEnabledIntegerCurrentlyMigratesAndCoercesToTrue() = runBlocking {
        val case = MigrationContractMatrix.batch8BRejections.single {
            it.name == "noncanonical-enabled-integer"
        }
        val databaseName = databaseName("enabled-coercion")
        createV2Database(databaseName).use(case.fixture!!::insertInto)
        migrateToV3(databaseName).close()

        withProductionDatabase(databaseName) { database ->
            val row = case.fixture.plans.single()
            val plan = RoomMedicationPlanRepository(database).getById(UUID.fromString(row.id))
            assertEquals(true, plan?.isEnabled)
            database.openHelper.readableDatabase.query(
                "SELECT isEnabled FROM medication_plans WHERE id = ?",
                arrayOf(row.id)
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
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

    private fun migrateToV3(name: String): SupportSQLiteDatabase =
        migrationHelper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

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

    private suspend inline fun <reified T : Throwable> assertSuspendFails(
        crossinline block: suspend () -> Unit
    ): T {
        try {
            block()
        } catch (error: Throwable) {
            if (error is T) return error
            throw error
        }
        fail("Expected ${T::class.java.simpleName}")
        throw AssertionError("unreachable")
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
}
