package io.github.yingqiu0871.evolune.backup

import android.content.Context
import androidx.core.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

private const val JOURNAL_FILE_NAME = "evolune_restore_journal.json"
private const val JOURNAL_FORMAT_VERSION = 1

@Serializable
private data class RestoreJournalWire(
    val formatVersion: Int,
    val operationId: String,
    val createdAt: String,
    val phase: String,
    val beforeRoom: RestoreRoomStateWire,
    val beforeSettings: RestoreSettingsWire
)

@Serializable
private data class RestoreRoomStateWire(
    val medicationPlans: List<RestorePlanWire>,
    val scheduledDoseSlots: List<RestoreSlotWire>,
    val doseEvents: List<RestoreEventWire>
)

@Serializable
private data class RestorePlanWire(
    val id: String,
    val name: String,
    val route: String,
    val ester: String,
    val doseMG: Double,
    val scheduleType: String,
    val daysOfWeek: List<Int>,
    val intervalDays: Int,
    val isEnabled: Boolean,
    val extras: Map<String, Double>,
    val createdAt: String
)

@Serializable
private data class RestoreSlotWire(
    val id: String,
    val planId: String,
    val localTime: String,
    val position: Int
)

@Serializable
private data class RestoreEventWire(
    val id: String,
    val route: String,
    val occurredAt: String,
    val zoneId: String?,
    val localDate: String?,
    val doseMG: Double,
    val ester: String,
    val extras: Map<String, Double>,
    val slotId: String?,
    val source: String,
    val status: String,
    val revision: Long
)

@Serializable
private data class RestoreSettingsWire(
    val bodyWeightKg: Double,
    val themeMode: String,
    val colorTheme: String,
    val autoCheckUpdates: Boolean,
    val timeFormat: String
)

internal sealed interface RestoreJournalDecodeResult {
    data class Success(val journal: RestoreJournal) : RestoreJournalDecodeResult
    data class Failure(val error: RestoreError) : RestoreJournalDecodeResult
}

/** Strict, versioned, unencrypted journal representation. */
internal object RestoreJournalCodec {
    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
        explicitNulls = true
    }

    fun encode(journal: RestoreJournal): String = json.encodeToString(journal.toWire())

    fun decode(text: String): RestoreJournalDecodeResult {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (_: Exception) {
            return corrupt()
        }
        if (!root.hasExactKeys("formatVersion", "operationId", "createdAt", "phase", "beforeRoom", "beforeSettings")) {
            return corrupt()
        }
        val room = root["beforeRoom"] as? JsonObject ?: return corrupt()
        val settings = root["beforeSettings"] as? JsonObject ?: return corrupt()
        if (!room.hasExactKeys("medicationPlans", "scheduledDoseSlots", "doseEvents") ||
            !settings.hasExactKeys("bodyWeightKg", "themeMode", "colorTheme", "autoCheckUpdates", "timeFormat")
        ) {
            return corrupt()
        }
        if (!room["medicationPlans"].hasExactArrayObjectKeys(
                "id", "name", "route", "ester", "doseMG", "scheduleType",
                "daysOfWeek", "intervalDays", "isEnabled", "extras", "createdAt"
            ) ||
            !room["scheduledDoseSlots"].hasExactArrayObjectKeys("id", "planId", "localTime", "position") ||
            !room["doseEvents"].hasExactArrayObjectKeys(
                "id", "route", "occurredAt", "zoneId", "localDate", "doseMG", "ester",
                "extras", "slotId", "source", "status", "revision"
            )
        ) {
            return corrupt()
        }
        val wire = try {
            json.decodeFromString<RestoreJournalWire>(text)
        } catch (_: Exception) {
            return corrupt()
        }

        if (wire.formatVersion != JOURNAL_FORMAT_VERSION) {
            return RestoreJournalDecodeResult.Failure(
                RestoreError(RestoreErrorCode.UNSUPPORTED_JOURNAL_VERSION)
            )
        }
        val phase = try {
            RestoreJournalPhase.valueOf(wire.phase)
        } catch (_: IllegalArgumentException) {
            return RestoreJournalDecodeResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT)
            )
        }
        try {
            UUID.fromString(wire.operationId)
            Instant.parse(wire.createdAt)
        } catch (_: RuntimeException) {
            return RestoreJournalDecodeResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT)
            )
        }

        val payload = wire.toPayload()
        return when (val validation = EvoluneBackupCodec().validate(payload)) {
            is BackupValidationResult.Valid -> RestoreJournalDecodeResult.Success(
                RestoreJournal(
                    formatVersion = wire.formatVersion,
                    operationId = wire.operationId,
                    createdAt = wire.createdAt,
                    phase = phase,
                    beforeRoom = RestoreRoomState.fromPayload(validation.payload.payload),
                    beforeSettings = validation.payload.payload.settings
                )
            )
            is BackupValidationResult.Invalid -> RestoreJournalDecodeResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT)
            )
        }
    }

    private fun corrupt(): RestoreJournalDecodeResult.Failure =
        RestoreJournalDecodeResult.Failure(
            RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT)
        )

    private fun JsonObject.hasExactKeys(vararg keys: String): Boolean =
        this.keys == keys.toSet()

    private fun kotlinx.serialization.json.JsonElement?.hasExactArrayObjectKeys(
        vararg keys: String
    ): Boolean {
        val array = this as? JsonArray ?: return false
        return array.all { (it as? JsonObject)?.hasExactKeys(*keys) == true }
    }

    private fun RestoreJournal.toWire(): RestoreJournalWire {
        val payload = beforeRoom.toPayload(beforeSettings)
        return RestoreJournalWire(
            formatVersion = formatVersion,
            operationId = operationId,
            createdAt = createdAt,
            phase = phase.name,
            beforeRoom = RestoreRoomStateWire(
                medicationPlans = payload.medicationPlans.map { it.toWire() },
                scheduledDoseSlots = payload.scheduledDoseSlots.map { it.toWire() },
                doseEvents = payload.doseEvents.map { it.toWire() }
            ),
            beforeSettings = beforeSettings.toWire()
        )
    }

    private fun RestoreJournalWire.toPayload(): EvoluneBackupPayloadV1 =
        EvoluneBackupPayloadV1(
            medicationPlans = beforeRoom.medicationPlans.map { it.toPayload() },
            scheduledDoseSlots = beforeRoom.scheduledDoseSlots.map { it.toPayload() },
            doseEvents = beforeRoom.doseEvents.map { it.toPayload() },
            settings = beforeSettings.toPayload()
        )

    private fun BackupMedicationPlanV1.toWire() = RestorePlanWire(
        id, name, route, ester, doseMG, scheduleType, daysOfWeek, intervalDays,
        isEnabled, extras, createdAt
    )

    private fun RestorePlanWire.toPayload() = BackupMedicationPlanV1(
        id, name, route, ester, doseMG, scheduleType, daysOfWeek, intervalDays,
        isEnabled, extras, createdAt
    )

    private fun BackupScheduledDoseSlotV1.toWire() =
        RestoreSlotWire(id, planId, localTime, position)

    private fun RestoreSlotWire.toPayload() =
        BackupScheduledDoseSlotV1(id, planId, localTime, position)

    private fun BackupDoseEventV1.toWire() = RestoreEventWire(
        id, route, occurredAt, zoneId, localDate, doseMG, ester, extras,
        slotId, source, status, revision
    )

    private fun RestoreEventWire.toPayload() = BackupDoseEventV1(
        id, route, occurredAt, zoneId, localDate, doseMG, ester, extras,
        slotId, source, status, revision
    )

    private fun BackupSettingsV1.toWire() = RestoreSettingsWire(
        bodyWeightKg, themeMode, colorTheme, autoCheckUpdates, timeFormat
    )

    private fun RestoreSettingsWire.toPayload() = BackupSettingsV1(
        bodyWeightKg, themeMode, colorTheme, autoCheckUpdates, timeFormat
    )
}

/** AtomicFile-backed store in Context.noBackupFilesDir; never included in Auto Backup. */
internal class FileRestoreJournalStore(context: Context) : RestoreJournalStore {
    private val file = File(context.noBackupFilesDir, JOURNAL_FILE_NAME)
    private val atomicFile = AtomicFile(file)

    override suspend fun read(): RestoreJournalReadResult = withContext(Dispatchers.IO) {
        if (!file.exists() && !File(file.path + ".bak").exists()) {
            return@withContext RestoreJournalReadResult.Missing
        }
        try {
            val text = String(atomicFile.readFully(), StandardCharsets.UTF_8)
            when (val decoded = RestoreJournalCodec.decode(text)) {
                is RestoreJournalDecodeResult.Success ->
                    RestoreJournalReadResult.Found(decoded.journal)
                is RestoreJournalDecodeResult.Failure ->
                    RestoreJournalReadResult.Failure(decoded.error)
            }
        } catch (_: FileNotFoundException) {
            RestoreJournalReadResult.Missing
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            RestoreJournalReadResult.Failure(
                RestoreError(RestoreErrorCode.RECOVERY_JOURNAL_CORRUPT, error)
            )
        }
    }

    override suspend fun write(journal: RestoreJournal) = withContext(Dispatchers.IO) {
        val bytes = RestoreJournalCodec.encode(journal).toByteArray(StandardCharsets.UTF_8)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(bytes)
            output.flush()
            output.fd.sync()
            atomicFile.finishWrite(output)
            output = null
        } catch (error: CancellationException) {
            output?.let { atomicFile.failWrite(it) }
            throw error
        } catch (error: Exception) {
            output?.let { atomicFile.failWrite(it) }
            throw error
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        atomicFile.delete()
    }
}
