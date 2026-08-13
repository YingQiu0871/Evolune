package io.github.yuninggu.evolune.data.migration.contract

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.data.AppDatabase
import io.github.yuninggu.evolune.data.migration.MIGRATION_2_3
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepairToolParityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val createdDatabases = mutableListOf<String>()

    @get:Rule
    val migrationHelper = MigrationTestHelper(instrumentation, AppDatabase::class.java)

    @After
    fun deleteDatabases() {
        createdDatabases.forEach(context::deleteDatabase)
    }

    @Test
    fun sharedCorpusMatchesOfficialMigrationClassification() {
        val corpus = instrumentation.context.assets
            .open("repair-v2-parity-corpus.json")
            .bufferedReader()
            .use { Json.decodeFromString<ParityCorpus>(it.readText()) }
        assertEquals(1, corpus.version)

        corpus.cases.forEachIndexed { index, case ->
            val databaseName = "phase1-batch8d-parity-$index"
            createdDatabases += databaseName
            migrationHelper.createDatabase(databaseName, 2).use { database ->
                when (case.aggregate) {
                    "event" -> eventFixture(index, case.values).insertInto(database)
                    "plan" -> planFixture(index, case.values).insertInto(database)
                    else -> error("Unsupported parity aggregate: ${case.aggregate}")
                }
            }
            val migrated = runCatching {
                migrationHelper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3)
                    .close()
            }.isSuccess
            assertEquals(case.name, case.expectedValid, migrated)
        }
    }

    private fun eventFixture(index: Int, values: Map<String, String>): V2EventRow = V2EventRow(
        id = values["id"] ?: "31000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
        route = values["route"] ?: "ORAL",
        timeH = persistedValue(values["timeH"], 1.0),
        doseMG = persistedValue(values["doseMG"], 1.0),
        ester = values["ester"] ?: "E2",
        extras = values["extras"] ?: "{}"
    )

    private fun planFixture(index: Int, values: Map<String, String>): V2PlanRow = V2PlanRow(
        id = values["id"] ?: "32000000-0000-0000-0000-${index.toString().padStart(12, '0')}",
        route = values["route"] ?: "ORAL",
        ester = values["ester"] ?: "E2",
        scheduleType = values["scheduleType"] ?: "DAILY",
        timeOfDay = values["timeOfDay"] ?: "[\"08:30\"]",
        daysOfWeek = values["daysOfWeek"] ?: "[]",
        intervalDays = persistedValue(values["intervalDays"], 1),
        isEnabled = persistedValue(values["isEnabled"], 1),
        extras = values["extras"] ?: "{}",
        createdAt = persistedValue(values["createdAt"], 0L)
    )

    private fun persistedValue(value: String?, default: Number): Any = when (value) {
        null -> default
        "Infinity" -> Double.POSITIVE_INFINITY
        "TEXT" -> "synthetic-text"
        else -> value.toLong()
    }
}

@Serializable
private data class ParityCorpus(
    val version: Int,
    val cases: List<ParityCase>
)

@Serializable
private data class ParityCase(
    val name: String,
    val aggregate: String,
    val expectedValid: Boolean,
    val values: Map<String, String> = emptyMap()
)
