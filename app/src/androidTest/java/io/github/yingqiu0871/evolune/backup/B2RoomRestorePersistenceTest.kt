package io.github.yingqiu0871.evolune.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.data.AppDatabase
import io.github.yingqiu0871.evolune.data.AtomicSettingsStore
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.SettingsStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import java.time.Instant
import io.github.yingqiu0871.evolune.data.RoomRestorePersistence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(AndroidJUnit4::class)
class B2RoomRestorePersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var settings: FakeAtomicSettingsStore
    private lateinit var persistence: RoomRestorePersistence

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        settings = FakeAtomicSettingsStore()
        persistence = RoomRestorePersistence(database, settings, settings)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `room replacement preserves all v3 fields and dangling event slot`() = runBlocking {
        val target = targetRoom()

        persistence.replaceRoom(target)

        assertEquals(target, persistence.readRoomState())
        assertEquals(DANGLING_SLOT_ID, persistence.readRoomState().doseEvents.single().slotId)
    }

    @Test
    fun `room trigger failure rolls back the complete replacement transaction`() = runBlocking {
        val before = RestoreRoomState(emptyList(), emptyList(), emptyList())
        persistence.replaceRoom(before)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER b2_restore_failure
            BEFORE INSERT ON dose_events
            BEGIN
                SELECT RAISE(ABORT, 'b2 injected Room failure');
            END
            """.trimIndent()
        )

        assertThrows(Exception::class.java) {
            runBlocking { persistence.replaceRoom(targetRoom()) }
        }

        assertEquals(before, persistence.readRoomState())
    }

    @Test
    fun `settings replacement uses one atomic replacement call and preserves every field`() =
        runBlocking {
            val target = BackupSettingsV1(62.5, "DARK", "BUILTIN", false, "HOUR_24")

            assertEquals(true, persistence.replaceSettings(target))

            assertEquals(1, settings.replaceCalls)
            assertEquals(target, persistence.readSettings())
        }

    @Test
    fun `journal store uses no backup files and fails closed on truncation`() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = FileRestoreJournalStore(context)
        val journal = RestoreJournal(
            1,
            "00000000-0000-4000-8000-000000000100",
            "2026-08-23T12:34:56Z",
            RestoreJournalPhase.PREPARED,
            RestoreRoomState(emptyList(), emptyList(), emptyList()),
            BackupSettingsV1(55.0, "SYSTEM", "DYNAMIC", true, "SYSTEM")
        )

        store.delete()
        store.write(journal)
        assertEquals(journal, (store.read() as RestoreJournalReadResult.Found).journal)
        assertTrue(File(context.noBackupFilesDir, "evolune_restore_journal.json").exists())

        File(context.noBackupFilesDir, "evolune_restore_journal.json")
            .writeText("{\"formatVersion\":1", StandardCharsets.UTF_8)
        assertEquals(
            RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT,
            (store.read() as RestoreJournalReadResult.Failure).error.code
        )
        store.delete()
    }

    private fun targetRoom() = RestoreRoomState(
        medicationPlans = listOf(
            BackupMedicationPlanV1(
                PLAN_ID, "Weekly estradiol", "INJECTION", "EV", 2.0,
                "WEEKLY", listOf(1, 3), 1, true,
                mapOf("CONCENTRATION_MG_ML" to 10.0), "2026-08-20T08:00:00Z"
            )
        ),
        scheduledDoseSlots = listOf(
            BackupScheduledDoseSlotV1(SLOT_ONE_ID, PLAN_ID, "08:00", 0),
            BackupScheduledDoseSlotV1(SLOT_TWO_ID, PLAN_ID, "20:00", 1)
        ),
        doseEvents = listOf(
            BackupDoseEventV1(
                EVENT_ID, "INJECTION", "2026-08-22T10:15:00Z", "Asia/Shanghai",
                "2026-08-22", 2.0, "EV", emptyMap(), DANGLING_SLOT_ID,
                "MANUAL", "RECORDED", 2L
            )
        )
    )

    private class FakeAtomicSettingsStore : SettingsStore, AtomicSettingsStore {
        override val userSettings = MutableStateFlow(UserSettings())
        var replaceCalls = 0

        override suspend fun updateBodyWeight(weight: Double): Boolean {
            userSettings.value = userSettings.value.copy(bodyWeight = weight)
            return true
        }

        override suspend fun updateThemeMode(mode: ThemeMode) {
            userSettings.value = userSettings.value.copy(themeMode = mode)
        }

        override suspend fun updateColorTheme(theme: ColorTheme) {
            userSettings.value = userSettings.value.copy(colorTheme = theme)
        }

        override suspend fun updateAutoCheckUpdates(enabled: Boolean) {
            userSettings.value = userSettings.value.copy(autoCheckUpdates = enabled)
        }

        override suspend fun updateTimeFormat(format: TimeFormat) {
            userSettings.value = userSettings.value.copy(timeFormat = format)
        }

        override suspend fun updateHealthConnectWeightSyncEnabled(enabled: Boolean) {
            userSettings.value = userSettings.value.copy(
                healthConnectWeightSyncEnabled = enabled
            )
        }

        override suspend fun updateBodyWeightFromHealthConnect(
            weight: Double,
            adoptedAt: Instant
        ): Boolean {
            userSettings.value = userSettings.value.copy(
                bodyWeight = weight,
                lastHealthConnectWeightKg = weight,
                lastHealthConnectWeightAdoptedAt = adoptedAt
            )
            return true
        }

        override suspend fun updateHealthConnectWeightMetadata(
            weight: Double,
            adoptedAt: Instant
        ): Boolean {
            userSettings.value = userSettings.value.copy(
                lastHealthConnectWeightKg = weight,
                lastHealthConnectWeightAdoptedAt = adoptedAt
            )
            return true
        }

        override suspend fun replaceSettings(settings: UserSettings): Boolean {
            replaceCalls++
            userSettings.value = settings
            return true
        }
    }

    companion object {
        private const val PLAN_ID = "00000000-0000-4000-8000-000000000001"
        private const val SLOT_ONE_ID = "00000000-0000-4000-8000-000000000002"
        private const val SLOT_TWO_ID = "00000000-0000-4000-8000-000000000003"
        private const val EVENT_ID = "00000000-0000-4000-8000-000000000004"
        private const val DANGLING_SLOT_ID = "00000000-0000-4000-8000-000000000099"
    }
}
