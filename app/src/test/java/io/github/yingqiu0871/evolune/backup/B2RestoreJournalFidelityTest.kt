package io.github.yingqiu0871.evolune.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class B2RestoreJournalFidelityTest {
    @Test
    fun `prepared recovery preserves canonical dynamic settings through journal`() {
        val before = BackupSettingsV1(
            bodyWeightKg = 60.0,
            themeMode = "SYSTEM",
            colorTheme = "DYNAMIC",
            autoCheckUpdates = true,
            timeFormat = "SYSTEM",
            themeColorSource = "DYNAMIC",
            themePresetId = null
        )
        val persistence = DataStoreLikePersistence(
            room = RestoreRoomState(emptyList(), emptyList(), emptyList()),
            settings = BackupSettingsV1(55.0, "DARK", "BUILTIN", false, "HOUR_24", "PRESET", "LEGACY_BUILTIN")
        )
        val journal = EncodingJournalStore()
        journal.current = RestoreJournal(
            1,
            OPERATION_ID,
            CREATED_AT,
            RestoreJournalPhase.PREPARED,
            RestoreRoomState(emptyList(), emptyList(), emptyList()),
            before
        )

        val result = kotlinx.coroutines.runBlocking {
            RestoreTransaction(persistence, journal).recoverInterruptedRestoreIfNeeded()
        }

        assertEquals(RestoreRecoveryResult.Recovered, result)
        assertEquals(before, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `prepared recovery preserves canonical legacy builtin settings through journal`() {
        assertPreparedRecoveryRestores(
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
    fun `prepared recovery preserves canonical named preset through journal`() {
        assertPreparedRecoveryRestores(
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
    fun `legacy five-field dynamic journal decodes to canonical dynamic settings`() {
        val settings = decodeLegacySettings("DYNAMIC")

        assertEquals(
            BackupSettingsV1(60.0, "SYSTEM", "DYNAMIC", true, "SYSTEM", "DYNAMIC", null),
            settings
        )
    }

    @Test
    fun `legacy five-field builtin journal decodes to canonical legacy builtin settings`() {
        val settings = decodeLegacySettings("BUILTIN")

        assertEquals(
            BackupSettingsV1(60.0, "SYSTEM", "BUILTIN", true, "SYSTEM", "PRESET", "LEGACY_BUILTIN"),
            settings
        )
    }

    @Test
    fun `malformed current and legacy theme identities fail closed`() {
        assertCorrupt(currentSettingsJson("UNKNOWN", "null"))
        assertCorrupt(currentSettingsJson("DYNAMIC", "\"LEGACY_BUILTIN\""))
        assertCorrupt(currentSettingsJson("PRESET", "null"))
        assertCorrupt(currentSettingsJson("PRESET", "\"NOT_A_PRESET\""))
        assertCorrupt(legacySettingsJson("UNKNOWN"))
    }

    private fun assertPreparedRecoveryRestores(before: BackupSettingsV1) {
        val persistence = DataStoreLikePersistence(
            room = RestoreRoomState(emptyList(), emptyList(), emptyList()),
            settings = BackupSettingsV1(55.0, "SYSTEM", "DYNAMIC", true, "SYSTEM", "DYNAMIC", null)
        )
        val journal = EncodingJournalStore()
        journal.current = RestoreJournal(
            1,
            OPERATION_ID,
            CREATED_AT,
            RestoreJournalPhase.PREPARED,
            RestoreRoomState(emptyList(), emptyList(), emptyList()),
            before
        )

        val result = kotlinx.coroutines.runBlocking {
            RestoreTransaction(persistence, journal).recoverInterruptedRestoreIfNeeded()
        }

        assertEquals(RestoreRecoveryResult.Recovered, result)
        assertEquals(before, persistence.settings)
        assertTrue(journal.deleted)
    }

    private fun decodeLegacySettings(colorTheme: String): BackupSettingsV1 {
        val text = legacySettingsJson(colorTheme)
        return (RestoreJournalCodec.decode(text) as RestoreJournalDecodeResult.Success)
            .journal.beforeSettings
    }

    private fun assertCorrupt(text: String) {
        val result = RestoreJournalCodec.decode(text)
        assertEquals(
            RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT,
            (result as RestoreJournalDecodeResult.Failure).error.code
        )
    }

    private fun currentSettingsJson(source: String, presetJson: String): String = journalText(
        "{\"bodyWeightKg\":60.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"BUILTIN\"," +
            "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\",\"themeColorSource\":\"$source\"," +
            "\"themePresetId\":$presetJson}"
    )

    private fun legacySettingsJson(colorTheme: String): String = journalText(
        "{\"bodyWeightKg\":60.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"$colorTheme\"," +
            "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\"}"
    )

    private fun journalText(settingsJson: String): String =
        "{\"formatVersion\":1,\"operationId\":\"$OPERATION_ID\",\"createdAt\":\"$CREATED_AT\"," +
            "\"phase\":\"PREPARED\",\"beforeRoom\":{\"medicationPlans\":[],\"scheduledDoseSlots\":[]," +
            "\"doseEvents\":[]},\"beforeSettings\":$settingsJson}"

    private class DataStoreLikePersistence(
        var room: RestoreRoomState,
        var settings: BackupSettingsV1
    ) : RestorePersistence {
        override suspend fun readRoomState(): RestoreRoomState = room

        override suspend fun replaceRoom(state: RestoreRoomState) {
            room = state
        }

        override suspend fun readSettings(): BackupSettingsV1 = settings

        override suspend fun replaceSettings(settings: BackupSettingsV1): Boolean {
            this.settings = settings.toUserSettings().toBackupSettings()
            return true
        }
    }

    private class EncodingJournalStore : RestoreJournalStore {
        var current: RestoreJournal? = null
            set(value) {
                field = value?.let {
                    (RestoreJournalCodec.decode(RestoreJournalCodec.encode(it))
                        as RestoreJournalDecodeResult.Success).journal
                }
            }
        var deleted = false

        override suspend fun read(): RestoreJournalReadResult =
            current?.let { RestoreJournalReadResult.Found(it) }
                ?: RestoreJournalReadResult.Missing

        override suspend fun write(journal: RestoreJournal) {
            current = journal
        }

        override suspend fun delete() {
            deleted = true
            current = null
        }
    }

    private companion object {
        const val OPERATION_ID = "00000000-0000-4000-8000-000000000100"
        const val CREATED_AT = "2026-08-23T12:34:56Z"
    }
}
