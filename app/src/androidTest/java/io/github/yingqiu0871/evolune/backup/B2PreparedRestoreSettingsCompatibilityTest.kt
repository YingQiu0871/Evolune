package io.github.yingqiu0871.evolune.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.RoomRestorePersistence
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class B2PreparedRestoreSettingsCompatibilityTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var settings: SettingsDataStore
    private lateinit var persistence: RoomRestorePersistence
    private lateinit var journal: FileRestoreJournalStore
    private lateinit var originalSettings: io.github.yingqiu0871.evolune.data.UserSettings

    @Before
    fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        settings = SettingsDataStore(context)
        persistence = RoomRestorePersistence(database, settings, settings)
        journal = FileRestoreJournalStore(context)
        originalSettings = settings.userSettings.first()
        journal.delete()
    }

    @After
    fun tearDown() = runBlocking {
        journal.delete()
        settings.replaceSettings(originalSettings)
        database.close()
    }

    @Test
    fun `current dynamic journal recovers through real Room and DataStore`() = runBlocking {
        recoverCurrent(
            BackupSettingsV1(60.0, "SYSTEM", "DYNAMIC", true, "SYSTEM", "DYNAMIC", null)
        )
    }

    @Test
    fun `current legacy builtin journal recovers through real Room and DataStore`() = runBlocking {
        recoverCurrent(
            BackupSettingsV1(
                62.5,
                "DARK",
                "BUILTIN",
                false,
                "HOUR_24",
                "PRESET",
                "LEGACY_BUILTIN"
            )
        )
    }

    @Test
    fun `current named preset journal recovers without collapsing preset identity`() = runBlocking {
        recoverCurrent(
            BackupSettingsV1(
                62.5,
                "DARK",
                "BUILTIN",
                false,
                "HOUR_24",
                "PRESET",
                "MONET_AMBER"
            )
        )
    }

    @Test
    fun `legacy dynamic five-field journal recovers and maps to canonical dynamic`() = runBlocking {
        recoverLegacy("DYNAMIC", BackupSettingsV1(60.0, "SYSTEM", "DYNAMIC", true, "SYSTEM", "DYNAMIC", null))
    }

    @Test
    fun `legacy builtin five-field journal recovers and maps to legacy builtin preset`() = runBlocking {
        recoverLegacy(
            "BUILTIN",
            BackupSettingsV1(60.0, "SYSTEM", "BUILTIN", true, "SYSTEM", "PRESET", "LEGACY_BUILTIN")
        )
    }

    private suspend fun recoverCurrent(beforeSettings: BackupSettingsV1) {
        val beforeRoom = RestoreRoomState(emptyList(), emptyList(), emptyList())
        seed(beforeRoom, beforeSettings)
        journal.write(
            RestoreJournal(
                formatVersion = 1,
                operationId = OPERATION_ID,
                createdAt = CREATED_AT,
                phase = RestoreJournalPhase.PREPARED,
                beforeRoom = beforeRoom,
                beforeSettings = beforeSettings
            )
        )
        mutate()

        val result = RestoreTransaction(persistence, journal).recoverInterruptedRestoreIfNeeded()

        assertEquals(RestoreRecoveryResult.Recovered, result)
        assertEquals(beforeRoom, persistence.readRoomState())
        assertEquals(beforeSettings, persistence.readSettings())
        assertEquals(RestoreJournalReadResult.Missing, journal.read())
    }

    private suspend fun recoverLegacy(colorTheme: String, expectedSettings: BackupSettingsV1) {
        val beforeRoom = RestoreRoomState(emptyList(), emptyList(), emptyList())
        seed(beforeRoom, expectedSettings)
        writeLegacyJournal(colorTheme)
        mutate()

        val result = RestoreTransaction(persistence, journal).recoverInterruptedRestoreIfNeeded()

        assertEquals(RestoreRecoveryResult.Recovered, result)
        assertEquals(beforeRoom, persistence.readRoomState())
        assertEquals(expectedSettings, persistence.readSettings())
        assertEquals(RestoreJournalReadResult.Missing, journal.read())
    }

    private suspend fun seed(beforeRoom: RestoreRoomState, beforeSettings: BackupSettingsV1) {
        persistence.replaceRoom(beforeRoom)
        assertTrue(persistence.replaceSettings(beforeSettings))
        assertEquals(beforeRoom, persistence.readRoomState())
        assertEquals(beforeSettings, persistence.readSettings())
    }

    private suspend fun mutate() {
        persistence.replaceRoom(
            RestoreRoomState(
                medicationPlans = listOf(
                    BackupMedicationPlanV1(
                        id = PLAN_ID,
                        name = "Interrupted target",
                        route = "INJECTION",
                        ester = "EV",
                        doseMG = 2.0,
                        scheduleType = "WEEKLY",
                        daysOfWeek = listOf(1, 3),
                        intervalDays = 1,
                        isEnabled = true,
                        extras = emptyMap(),
                        createdAt = CREATED_AT
                    )
                ),
                scheduledDoseSlots = emptyList(),
                doseEvents = emptyList()
            )
        )
        assertTrue(
            persistence.replaceSettings(
                BackupSettingsV1(55.0, "DARK", "DYNAMIC", false, "HOUR_24", "DYNAMIC", null)
            )
        )
    }

    private fun writeLegacyJournal(colorTheme: String) {
        val file = File(context.noBackupFilesDir, JOURNAL_FILE_NAME)
        File(file.path + ".bak").delete()
        file.writeText(
            "{\"formatVersion\":1,\"operationId\":\"$OPERATION_ID\",\"createdAt\":\"$CREATED_AT\"," +
                "\"phase\":\"PREPARED\",\"beforeRoom\":{\"medicationPlans\":[],\"scheduledDoseSlots\":[]," +
                "\"doseEvents\":[]},\"beforeSettings\":{\"bodyWeightKg\":60.0,\"themeMode\":\"SYSTEM\"," +
                "\"colorTheme\":\"$colorTheme\",\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\"}}",
            StandardCharsets.UTF_8
        )
    }

    private companion object {
        const val JOURNAL_FILE_NAME = "evolune_restore_journal.json"
        const val OPERATION_ID = "00000000-0000-4000-8000-000000000101"
        const val CREATED_AT = "2026-08-23T12:34:56Z"
        const val PLAN_ID = "00000000-0000-4000-8000-000000000201"
    }
}
