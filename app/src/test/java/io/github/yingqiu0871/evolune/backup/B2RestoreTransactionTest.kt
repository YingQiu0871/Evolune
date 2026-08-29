package io.github.yingqiu0871.evolune.backup

import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitAll
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class B2RestoreTransactionTest {
    @Test
    fun `prepare and preview perform no persistence or journal writes`() {
        val persistence = FakeRestorePersistence(beforePayload())
        val journal = FakeRestoreJournalStore()
        val transaction = transaction(persistence, journal)

        val result = transaction.prepare(validated(targetPayload()), metadata())

        val prepared = (result as RestorePrepareResult.Success).prepared
        assertEquals(1, prepared.preview.medicationPlanCount)
        assertEquals(2, prepared.preview.scheduledDoseSlotCount)
        assertEquals(1, prepared.preview.doseEventCount)
        assertEquals("1.2.0", prepared.preview.producerAppVersionName)
        assertEquals(0, persistence.roomWriteCount)
        assertEquals(0, persistence.settingsWriteCount)
        assertTrue(journal.writes.isEmpty())
        assertFalse(journal.deleted)
    }

    @Test
    fun `normal restore atomically replaces room and every setting and preserves dangling slot`() =
        runBlocking {
            val persistence = FakeRestorePersistence(beforePayload())
            val journal = FakeRestoreJournalStore()
            val transaction = transaction(persistence, journal)

            val result = transaction.restore(requirePrepared(transaction, targetPayload()))

            assertEquals(RestoreResult.Success(), result)
            assertEquals(targetRoom(), persistence.room)
            assertEquals(targetPayload().settings, persistence.settings)
            assertEquals(2, journal.writes.size)
            assertEquals(RestoreJournalPhase.PREPARED, journal.writes[0].phase)
            assertEquals(RestoreJournalPhase.COMMITTED, journal.writes[1].phase)
            assertTrue(journal.deleted)
            assertEquals(DANGLING_SLOT_ID, persistence.room.doseEvents.single().slotId)
        }

    @Test
    fun `room failure rolls back to the captured before state`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload()).apply { failRoomOnce = true }
        val journal = FakeRestoreJournalStore()
        val transaction = transaction(persistence, journal)

        val result = transaction.restore(requirePrepared(transaction, targetPayload()))

        assertEquals(RestoreErrorCode.DATABASE_RESTORE_FAILED, failureCode(result))
        assertEquals(beforeRoom(), persistence.room)
        assertEquals(beforePayload().settings, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `settings failure rolls back both room and settings`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload()).apply { failSettingsOnce = true }
        val journal = FakeRestoreJournalStore()
        val transaction = transaction(persistence, journal)

        val result = transaction.restore(requirePrepared(transaction, targetPayload()))

        assertEquals(RestoreErrorCode.SETTINGS_RESTORE_FAILED, failureCode(result))
        assertEquals(beforeRoom(), persistence.room)
        assertEquals(beforePayload().settings, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `postcondition failure rolls back instead of committing`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload()).apply {
            tamperTargetRoomAfterWrite = true
        }
        val journal = FakeRestoreJournalStore()
        val transaction = transaction(persistence, journal)

        val result = transaction.restore(requirePrepared(transaction, targetPayload()))

        assertEquals(RestoreErrorCode.POSTCONDITION_FAILED, failureCode(result))
        assertEquals(beforeRoom(), persistence.room)
        assertEquals(1, journal.writes.count { it.phase == RestoreJournalPhase.PREPARED })
        assertEquals(0, journal.writes.count { it.phase == RestoreJournalPhase.COMMITTED })
    }

    @Test
    fun `committed journal write failure rolls back before the commit point`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload())
        val journal = FakeRestoreJournalStore().apply { failCommittedWrite = true }
        val transaction = transaction(persistence, journal)

        val result = transaction.restore(requirePrepared(transaction, targetPayload()))

        assertEquals(RestoreErrorCode.JOURNAL_COMMIT_FAILED, failureCode(result))
        assertEquals(beforeRoom(), persistence.room)
        assertEquals(beforePayload().settings, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `rollback failure leaves prepared journal and later startup recovery repairs it`() =
        runBlocking {
            val persistence = FakeRestorePersistence(beforePayload()).apply {
                alwaysFailSettings = true
            }
            val journal = FakeRestoreJournalStore()
            val transaction = transaction(persistence, journal)

            val failed = transaction.restore(requirePrepared(transaction, targetPayload()))

            assertEquals(RestoreErrorCode.ROLLBACK_FAILED, failureCode(failed))
            assertNotNull(journal.current)
            assertEquals(RestoreJournalPhase.PREPARED, journal.current?.phase)

            persistence.alwaysFailSettings = false
            val recovery = transaction.recoverInterruptedRestoreIfNeeded()

            assertEquals(RestoreRecoveryResult.Recovered, recovery)
            assertEquals(beforeRoom(), persistence.room)
            assertEquals(beforePayload().settings, persistence.settings)
            assertTrue(journal.deleted)
        }

    @Test
    fun `prepared startup recovery restores before state`() = runBlocking {
        val persistence = FakeRestorePersistence(targetPayload())
        val journal = FakeRestoreJournalStore().apply {
            current = RestoreJournal(
                1, OPERATION_ID, CREATED_AT, RestoreJournalPhase.PREPARED,
                beforeRoom(), beforePayload().settings
            )
        }
        val transaction = transaction(persistence, journal)

        assertEquals(RestoreRecoveryResult.Recovered, transaction.recoverInterruptedRestoreIfNeeded())
        assertEquals(beforeRoom(), persistence.room)
        assertEquals(beforePayload().settings, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `committed startup recovery keeps target and only cleans journal`() = runBlocking {
        val persistence = FakeRestorePersistence(targetPayload())
        val journal = FakeRestoreJournalStore().apply {
            current = RestoreJournal(
                1, OPERATION_ID, CREATED_AT, RestoreJournalPhase.COMMITTED,
                beforeRoom(), beforePayload().settings
            )
        }
        val transaction = transaction(persistence, journal)

        assertEquals(RestoreRecoveryResult.Recovered, transaction.recoverInterruptedRestoreIfNeeded())
        assertEquals(targetRoom(), persistence.room)
        assertEquals(targetPayload().settings, persistence.settings)
        assertTrue(journal.deleted)
    }

    @Test
    fun `corrupt and future journals fail closed`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload())
        val corrupt = FakeRestoreJournalStore().apply {
            readFailure = RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT)
        }
        assertEquals(
            RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT,
            recoveryCode(transaction(persistence, corrupt).recoverInterruptedRestoreIfNeeded())
        )

        val unsupported = FakeRestoreJournalStore().apply {
            readFailure = RestoreError(RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION)
        }
        assertEquals(
            RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION,
            recoveryCode(transaction(persistence, unsupported).recoverInterruptedRestoreIfNeeded())
        )
    }

    @Test
    fun `journal codec rejects truncation future version and unknown fields`() {
        val journal = RestoreJournal(
            1, OPERATION_ID, CREATED_AT, RestoreJournalPhase.PREPARED,
            beforeRoom(), beforePayload().settings
        )
        val encoded = RestoreJournalCodec.encode(journal)

        assertEquals(
            RestoreJournalErrorCode.CORRUPT,
            journalDecodeCode(encoded.substring(0, encoded.length / 2))
        )
        assertEquals(
            RestoreJournalErrorCode.UNSUPPORTED,
            journalDecodeCode(encoded.replace("\"formatVersion\":1", "\"formatVersion\":2"))
        )
        assertEquals(
            RestoreJournalErrorCode.CORRUPT,
            journalDecodeCode(encoded.replaceFirst("{", "{\"unknown\":1,"))
        )
        assertTrue(RestoreJournalCodec.decode(encoded) is RestoreJournalDecodeResult.Success)
    }

    @Test
    fun `restore calls are serialized by the transaction mutex`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload()).apply { delayOnRoomWrite = true }
        val journal = FakeJournalStoreForConcurrency()
        val transaction = transaction(persistence, journal)
        val prepared = requirePrepared(transaction, targetPayload())

        val results = listOf(
            async { transaction.restore(prepared) },
            async { transaction.restore(prepared) }
        ).awaitAll()

        assertTrue(results.all { it is RestoreResult.Success })
        assertEquals(1, persistence.maxConcurrentRoomWrites)
    }

    @Test
    fun `coroutine cancellation is propagated instead of becoming a restore error`() = runBlocking {
        val persistence = FakeRestorePersistence(beforePayload()).apply {
            cancelOnRoomRead = true
        }
        val transaction = transaction(persistence, FakeRestoreJournalStore())

        assertThrows(CancellationException::class.java) {
            runBlocking {
                transaction.restore(requirePrepared(transaction, targetPayload()))
            }
        }
        Unit
    }

    private fun transaction(
        persistence: FakeRestorePersistence,
        journal: RestoreJournalStore
    ) = RestoreTransaction(
        persistence = persistence,
        journalStore = journal,
        now = { Instant.parse(CREATED_AT) },
        nextOperationId = { OPERATION_ID }
    )

    private fun requirePrepared(
        transaction: RestoreTransaction,
        payload: EvoluneBackupPayloadV1
    ): PreparedRestore = when (val result = transaction.prepare(validated(payload))) {
        is RestorePrepareResult.Success -> result.prepared
        is RestorePrepareResult.Failure -> error("prepare failed: ${result.error}")
    }

    private fun validated(payload: EvoluneBackupPayloadV1): ValidatedEvoluneBackupPayloadV1 =
        when (val result = EvoluneBackupCodec().validate(payload)) {
            is BackupValidationResult.Valid -> result.payload
            is BackupValidationResult.Invalid -> error("fixture invalid: ${result.error}")
        }

    private fun failureCode(result: RestoreResult): RestoreErrorCode =
        (result as RestoreResult.Failure).error.code

    private fun recoveryCode(result: RestoreRecoveryResult): RestoreErrorCode =
        (result as RestoreRecoveryResult.Failure).error.code

    private fun journalDecodeCode(text: String): RestoreJournalErrorCode = when (
        val result = RestoreJournalCodec.decode(text)
    ) {
        is RestoreJournalDecodeResult.Success -> RestoreJournalErrorCode.SUCCESS
        is RestoreJournalDecodeResult.Failure -> when (result.error.code) {
            RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION -> RestoreJournalErrorCode.UNSUPPORTED
            else -> RestoreJournalErrorCode.CORRUPT
        }
    }

    private fun metadata() = BackupProducerMetadataV1(
        createdAt = CREATED_AT,
        producerAppVersionName = "1.2.0",
        producerAppVersionCode = 120000
    )

    private fun beforeRoom() = RestoreRoomState.fromPayload(beforePayload())

    private fun targetRoom() = RestoreRoomState.fromPayload(targetPayload())

    private fun beforePayload() = EvoluneBackupPayloadV1(
        medicationPlans = emptyList(),
        scheduledDoseSlots = emptyList(),
        doseEvents = emptyList(),
        settings = BackupSettingsV1(55.0, "SYSTEM", "DYNAMIC", true, "SYSTEM")
    )

    private fun targetPayload() = EvoluneBackupPayloadV1(
        medicationPlans = listOf(
            BackupMedicationPlanV1(
                id = PLAN_ID,
                name = "Weekly estradiol",
                route = "INJECTION",
                ester = "EV",
                doseMG = 2.0,
                scheduleType = "WEEKLY",
                daysOfWeek = listOf(1, 3),
                intervalDays = 1,
                isEnabled = true,
                extras = mapOf("CONCENTRATION_MG_ML" to 10.0),
                createdAt = "2026-08-20T08:00:00Z"
            )
        ),
        scheduledDoseSlots = listOf(
            BackupScheduledDoseSlotV1(SLOT_ONE_ID, PLAN_ID, "08:00", 0),
            BackupScheduledDoseSlotV1(SLOT_TWO_ID, PLAN_ID, "20:00", 1)
        ),
        doseEvents = listOf(
            BackupDoseEventV1(
                id = EVENT_ID,
                route = "INJECTION",
                occurredAt = "2026-08-22T10:15:00Z",
                zoneId = "Asia/Shanghai",
                localDate = "2026-08-22",
                doseMG = 2.0,
                ester = "EV",
                extras = emptyMap(),
                slotId = DANGLING_SLOT_ID,
                source = "MANUAL",
                status = "RECORDED",
                revision = 2L
            )
        ),
        settings = BackupSettingsV1(62.5, "DARK", "BUILTIN", false, "HOUR_24")
    )

    private enum class RestoreJournalErrorCode { SUCCESS, CORRUPT, UNSUPPORTED }

    private class FakeRestorePersistence(initial: EvoluneBackupPayloadV1) : RestorePersistence {
        var room = RestoreRoomState.fromPayload(initial)
        var settings = initial.settings
        var failRoomOnce = false
        var failSettingsOnce = false
        var alwaysFailSettings = false
        var tamperTargetRoomAfterWrite = false
        var delayOnRoomWrite = false
        var cancelOnRoomRead = false
        var roomWriteCount = 0
        var settingsWriteCount = 0
        var activeRoomWrites = 0
        var maxConcurrentRoomWrites = 0

        override suspend fun readRoomState(): RestoreRoomState {
            if (cancelOnRoomRead) throw CancellationException("injected cancellation")
            return room
        }

        override suspend fun replaceRoom(state: RestoreRoomState) {
            roomWriteCount++
            activeRoomWrites++
            maxConcurrentRoomWrites = maxOf(maxConcurrentRoomWrites, activeRoomWrites)
            try {
                if (delayOnRoomWrite) delay(10)
                if (failRoomOnce) {
                    failRoomOnce = false
                    throw IllegalStateException("injected Room failure")
                }
                room = if (tamperTargetRoomAfterWrite && state.medicationPlans.isNotEmpty()) {
                    RestoreRoomState(emptyList(), emptyList(), emptyList())
                } else {
                    state
                }
            } finally {
                activeRoomWrites--
            }
        }

        override suspend fun readSettings(): BackupSettingsV1 = settings

        override suspend fun replaceSettings(settings: BackupSettingsV1): Boolean {
            settingsWriteCount++
            if (alwaysFailSettings) throw IllegalStateException("injected settings failure")
            if (failSettingsOnce) {
                failSettingsOnce = false
                throw IllegalStateException("injected settings failure")
            }
            this.settings = settings
            return true
        }
    }

    private open class FakeRestoreJournalStore : RestoreJournalStore {
        var current: RestoreJournal? = null
        var deleted = false
        var failCommittedWrite = false
        var readFailure: RestoreError? = null
        val writes = mutableListOf<RestoreJournal>()

        override suspend fun read(): RestoreJournalReadResult =
            readFailure?.let { RestoreJournalReadResult.Failure(it) }
                ?: current?.let { RestoreJournalReadResult.Found(it) }
                ?: RestoreJournalReadResult.Missing

        override suspend fun write(journal: RestoreJournal) {
            if (failCommittedWrite && journal.phase == RestoreJournalPhase.COMMITTED) {
                throw IllegalStateException("injected commit journal failure")
            }
            writes += journal
            current = journal
            deleted = false
        }

        override suspend fun delete() {
            current = null
            deleted = true
        }
    }

    private class FakeJournalStoreForConcurrency : FakeRestoreJournalStore()

    companion object {
        private const val PLAN_ID = "00000000-0000-4000-8000-000000000001"
        private const val SLOT_ONE_ID = "00000000-0000-4000-8000-000000000002"
        private const val SLOT_TWO_ID = "00000000-0000-4000-8000-000000000003"
        private const val EVENT_ID = "00000000-0000-4000-8000-000000000004"
        private const val DANGLING_SLOT_ID = "00000000-0000-4000-8000-000000000099"
        private const val OPERATION_ID = "00000000-0000-4000-8000-000000000100"
        private const val CREATED_AT = "2026-08-23T12:34:56Z"
    }
}
