package io.github.yingqiu0871.evolune.backup

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/** PK 2.0 slice 2b: lab results in backup payload schema v3 and the restore journal. */
class BackupLabResultsSchemaV3Test {
    private val passphrase = "correct horse battery staple".toCharArray()
    private val metadata = BackupProducerMetadataV1(
        createdAt = "2026-10-04T12:00:00Z",
        producerAppVersionName = "1.12.0",
        producerAppVersionCode = 100_000_100
    )

    @Test
    fun `payload with lab results is written as schema v3 and round trips`() {
        val payload = payload(labResults = listOf(lab(LAB_B, "2026-09-02T08:00:00Z"), lab(LAB_A, "2026-09-01T08:00:00.125Z")))

        val encoded = encode(payload)

        assertEquals(3, envelopeVersion(encoded))
        val decoded = decode(encoded) as BackupDecodeResult.Success
        // Canonical bytes order labs by measuredAt, so the decoded list is sorted.
        assertEquals(payload.copy(labResults = payload.labResults!!.reversed()), decoded.payload.payload)
    }

    @Test
    fun `payload without lab results is still written as schema v2 and decodes to null labs`() {
        val payload = payload(labResults = null)

        val encoded = encode(payload)

        assertEquals(EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION, envelopeVersion(encoded))
        val decoded = decode(encoded) as BackupDecodeResult.Success
        assertNull(decoded.payload.payload.labResults)
        assertEquals(payload, decoded.payload.payload)
    }

    @Test
    fun `an empty lab list is distinct from no lab results`() {
        val encoded = encode(payload(labResults = emptyList()))

        assertEquals(3, envelopeVersion(encoded))
        val decoded = decode(encoded) as BackupDecodeResult.Success
        assertEquals(emptyList<BackupLabResultV1>(), decoded.payload.payload.labResults)
    }

    @Test
    fun `snapshot writes labs only when there are any`() = runBlocking {
        val settings = payload(labResults = null).settings
        val empty = LabAwarePersistence(
            RestoreRoomState(emptyList(), emptyList(), emptyList(), emptyList()),
            settings
        )
        val withLabs = LabAwarePersistence(
            RestoreRoomState(emptyList(), emptyList(), emptyList(), listOf(lab(LAB_A))),
            settings
        )

        val emptyPayload = (RestorePersistenceSnapshotSource(empty).capture() as SnapshotCaptureResult.Success).payload
        val labsPayload = (RestorePersistenceSnapshotSource(withLabs).capture() as SnapshotCaptureResult.Success).payload

        assertNull(emptyPayload.labResults)
        assertEquals(EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION, EvoluneBackupFormat.payloadSchemaVersionFor(emptyPayload))
        assertEquals(listOf(lab(LAB_A)), labsPayload.labResults)
        assertEquals(3, EvoluneBackupFormat.payloadSchemaVersionFor(labsPayload))
    }

    @Test
    fun `invalid lab results are rejected by validation`() {
        val codec = EvoluneBackupCodec()
        val invalid = listOf(
            listOf(lab(LAB_A), lab(LAB_A, "2026-09-03T08:00:00Z")),
            listOf(lab("not-a-uuid")),
            listOf(lab(LAB_A).copy(unit = "pg/ml")),
            listOf(lab(LAB_A).copy(value = 0.0)),
            listOf(lab(LAB_A).copy(value = Double.NaN)),
            listOf(lab(LAB_A).copy(measuredAt = "2026-09-01T08:00:00.000000001Z")),
            listOf(lab(LAB_A).copy(revision = 0L))
        )
        invalid.forEach { labs ->
            assertTrue(
                "expected invalid: $labs",
                codec.validate(payload(labResults = labs)) is BackupValidationResult.Invalid
            )
        }
        assertTrue(codec.validate(payload(labResults = listOf(lab(LAB_A)))) is BackupValidationResult.Valid)
    }

    @Test
    fun `schema and labResults key must agree`() {
        val v3Carrier = encode(payload(labResults = emptyList()))
        val v2Carrier = encode(payload(labResults = null))

        assertMalformed(craft(v3Carrier, payloadJson(version = 3, labsJson = null)))
        assertMalformed(craft(v2Carrier, payloadJson(version = 2, labsJson = "[]")))
        assertMalformed(
            craft(
                v3Carrier,
                payloadJson(
                    version = 3,
                    labsJson = "[{\"id\":\"$LAB_A\",\"measuredAt\":\"2026-09-01T08:00:00Z\"," +
                        "\"value\":100.0,\"unit\":\"PG_PER_ML\",\"revision\":1,\"extra\":true}]"
                )
            )
        )
        val accepted = decode(craft(v3Carrier, payloadJson(version = 3, labsJson = "[]")))
        assertEquals(emptyList<BackupLabResultV1>(), (accepted as BackupDecodeResult.Success).payload.payload.labResults)
    }

    @Test
    fun `restore preview counts lab results only when the backup carries them`() {
        assertEquals(2, restorePreview(payload(listOf(lab(LAB_A), lab(LAB_B))), null).labResultCount)
        assertNull(restorePreview(payload(null), null).labResultCount)
    }

    @Test
    fun `room state matching ignores labs only when the target leaves them out`() {
        val withLabs = RestoreRoomState(emptyList(), emptyList(), emptyList(), listOf(lab(LAB_A)))
        val otherLabs = withLabs.copy(labResults = listOf(lab(LAB_B)))
        val untouched = withLabs.copy(labResults = null)

        assertTrue(withLabs.matches(withLabs))
        assertFalse(otherLabs.matches(withLabs))
        assertTrue(otherLabs.matches(untouched))
    }

    @Test
    fun `journal records lab results as format v2 and legacy state as format v1`() {
        val labs = journal(RestoreRoomState(emptyList(), emptyList(), emptyList(), listOf(lab(LAB_A))))
        val legacy = journal(RestoreRoomState(emptyList(), emptyList(), emptyList(), null))

        val labsText = RestoreJournalCodec.encode(labs)
        val legacyText = RestoreJournalCodec.encode(legacy)

        assertEquals(2, Json.parseToJsonElement(labsText).jsonObject.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertEquals(1, Json.parseToJsonElement(legacyText).jsonObject.getValue("formatVersion").jsonPrimitive.content.toInt())
        assertFalse(legacyText.contains("labResults"))
        val decoded = (RestoreJournalCodec.decode(labsText) as RestoreJournalDecodeResult.Success).journal
        assertEquals(labs.beforeRoom, decoded.beforeRoom)
        val decodedLegacy = (RestoreJournalCodec.decode(legacyText) as RestoreJournalDecodeResult.Success).journal
        assertNull(decodedLegacy.beforeRoom.labResults)
    }

    @Test
    fun `journal version and labResults key must agree`() {
        val labsText = RestoreJournalCodec.encode(
            journal(RestoreRoomState(emptyList(), emptyList(), emptyList(), emptyList()))
        )
        val legacyText = RestoreJournalCodec.encode(
            journal(RestoreRoomState(emptyList(), emptyList(), emptyList(), null))
        )

        assertCorrupt(labsText.replace("\"formatVersion\":2", "\"formatVersion\":1"))
        assertCorrupt(legacyText.replace("\"formatVersion\":1", "\"formatVersion\":2"))
    }

    @Test
    fun `restoring a backup without lab results keeps local labs and succeeds`() = runBlocking {
        val localLabs = listOf(lab(LAB_A))
        val persistence = LabAwarePersistence(
            room = RestoreRoomState(emptyList(), emptyList(), emptyList(), localLabs),
            settings = settings()
        )
        val journal = MemoryJournalStore()
        val transaction = RestoreTransaction(persistence, journal)
        val prepared = (transaction.prepare(ValidatedEvoluneBackupPayloadV1(payload(null)))
            as RestorePrepareResult.Success).prepared

        val result = transaction.restore(prepared)

        assertEquals(RestoreResult.Success(), result)
        assertEquals(localLabs, persistence.room.labResults)
        assertEquals(2, journal.writtenVersions.first())
    }

    @Test
    fun `restoring a backup with lab results replaces local labs`() = runBlocking {
        val persistence = LabAwarePersistence(
            room = RestoreRoomState(emptyList(), emptyList(), emptyList(), listOf(lab(LAB_A))),
            settings = settings()
        )
        val transaction = RestoreTransaction(persistence, MemoryJournalStore())
        val prepared = (transaction.prepare(ValidatedEvoluneBackupPayloadV1(payload(listOf(lab(LAB_B)))))
            as RestorePrepareResult.Success).prepared

        assertEquals(RestoreResult.Success(), transaction.restore(prepared))
        assertEquals(listOf(lab(LAB_B)), persistence.room.labResults)
    }

    private fun payload(labResults: List<BackupLabResultV1>?) = EvoluneBackupPayloadV1(
        medicationPlans = emptyList(),
        scheduledDoseSlots = emptyList(),
        doseEvents = emptyList(),
        settings = settings(),
        labResults = labResults
    )

    private fun settings() = BackupSettingsV1(
        bodyWeightKg = 60.0,
        themeMode = "SYSTEM",
        colorTheme = "DYNAMIC",
        autoCheckUpdates = true,
        timeFormat = "SYSTEM",
        themeColorSource = "DYNAMIC",
        themePresetId = null
    )

    private fun lab(id: String, measuredAt: String = "2026-09-01T08:00:00Z") =
        BackupLabResultV1(id, measuredAt, 367.1, "PMOL_PER_L", 1L)

    private fun journal(room: RestoreRoomState) = RestoreJournal(
        formatVersion = restoreJournalFormatVersionFor(room),
        operationId = OPERATION_ID,
        createdAt = metadata.createdAt,
        phase = RestoreJournalPhase.PREPARED,
        beforeRoom = room,
        beforeSettings = settings()
    )

    private fun encode(payload: EvoluneBackupPayloadV1): ByteArray =
        when (val result = EvoluneBackupCodec().encode(payload, passphrase, metadata, 100_000)) {
            is BackupEncodeResult.Success -> result.bytes
            is BackupEncodeResult.Failure -> error("encode failed: ${result.error}")
        }

    private fun decode(bytes: ByteArray): BackupDecodeResult =
        EvoluneBackupCodec().decodeAndValidate(bytes, passphrase.copyOf())

    private fun craft(carrier: ByteArray, payloadText: String): ByteArray =
        BackupTamperTestSupport.replaceCiphertextWithPayload(carrier, passphrase, payloadText)

    private fun envelopeVersion(bytes: ByteArray): Int =
        Json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8))
            .jsonObject.getValue("payloadSchemaVersion").jsonPrimitive.content.toInt()

    private fun payloadJson(version: Int, labsJson: String?): String =
        "{\"payloadSchemaVersion\":$version,\"medicationPlans\":[],\"scheduledDoseSlots\":[]," +
            "\"doseEvents\":[],\"settings\":{\"bodyWeightKg\":60.0,\"themeMode\":\"SYSTEM\"," +
            "\"colorTheme\":\"DYNAMIC\",\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\"," +
            "\"themeColorSource\":\"DYNAMIC\",\"themePresetId\":null}" +
            (labsJson?.let { ",\"labResults\":$it" } ?: "") + "}"

    private fun assertMalformed(bytes: ByteArray) {
        val failure = decode(bytes) as? BackupDecodeResult.Failure
            ?: throw AssertionError("expected failure")
        assertEquals(BackupCodecErrorCode.MALFORMED_PAYLOAD, failure.error.code)
    }

    private fun assertCorrupt(text: String) {
        val failure = RestoreJournalCodec.decode(text) as? RestoreJournalDecodeResult.Failure
            ?: throw AssertionError("expected corrupt journal")
        assertEquals(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT, failure.error.code)
    }

    /** Mirrors RoomRestorePersistence: a state without labs leaves stored labs untouched. */
    private class LabAwarePersistence(
        var room: RestoreRoomState,
        var settings: BackupSettingsV1
    ) : RestorePersistence {
        override suspend fun readRoomState(): RestoreRoomState = room

        override suspend fun replaceRoom(state: RestoreRoomState) {
            room = state.copy(labResults = state.labResults ?: room.labResults)
        }

        override suspend fun readSettings(): BackupSettingsV1 = settings

        override suspend fun replaceSettings(settings: BackupSettingsV1): Boolean {
            this.settings = settings
            return true
        }
    }

    private class MemoryJournalStore : RestoreJournalStore {
        private var current: RestoreJournal? = null
        val writtenVersions = mutableListOf<Int>()

        override suspend fun read(): RestoreJournalReadResult =
            current?.let { RestoreJournalReadResult.Found(it) } ?: RestoreJournalReadResult.Missing

        override suspend fun write(journal: RestoreJournal) {
            val text = RestoreJournalCodec.encode(journal)
            writtenVersions += Json.parseToJsonElement(text).jsonObject
                .getValue("formatVersion").jsonPrimitive.content.toInt()
            current = (RestoreJournalCodec.decode(text) as RestoreJournalDecodeResult.Success).journal
        }

        override suspend fun delete() {
            current = null
        }
    }

    private companion object {
        const val LAB_A = "00000000-0000-4000-8000-0000000000a1"
        const val LAB_B = "00000000-0000-4000-8000-0000000000a2"
        const val OPERATION_ID = "00000000-0000-4000-8000-000000000100"
    }
}
