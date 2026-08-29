package io.github.yingqiu0871.evolune.backup

import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat

/** Stable result codes for the B2 restore transaction and startup recovery. */
internal enum class RestoreErrorCode {
    INVALID_PAYLOAD,
    LOCAL_SNAPSHOT_FAILED,
    JOURNAL_WRITE_FAILED,
    DATABASE_RESTORE_FAILED,
    SETTINGS_RESTORE_FAILED,
    POSTCONDITION_FAILED,
    JOURNAL_COMMIT_FAILED,
    JOURNAL_CLEANUP_FAILED,
    ROLLBACK_FAILED,
    RECOVERY_REQUIRED,
    RECOVERY_JOURNAL_CORRUPT,
    UNSUPPORTED_JOURNAL_VERSION
}

internal data class RestoreError(
    val code: RestoreErrorCode,
    val cause: Throwable? = null
)

internal data class RestorePreview(
    val createdAt: String?,
    val producerAppVersionName: String?,
    val producerAppVersionCode: Int?,
    val medicationPlanCount: Int,
    val scheduledDoseSlotCount: Int,
    val doseEventCount: Int,
    val bodyWeightKg: Double,
    val themeMode: String,
    val colorTheme: String,
    val autoCheckUpdates: Boolean,
    val timeFormat: String
)

internal data class RestoreRoomState(
    val medicationPlans: List<BackupMedicationPlanV1>,
    val scheduledDoseSlots: List<BackupScheduledDoseSlotV1>,
    val doseEvents: List<BackupDoseEventV1>
) {
    fun canonical(): RestoreRoomState = RestoreRoomState(
        medicationPlans = medicationPlans.sortedBy { it.id },
        scheduledDoseSlots = scheduledDoseSlots.sortedWith(
            compareBy<BackupScheduledDoseSlotV1> { it.planId }
                .thenBy { it.position }
                .thenBy { it.id }
        ),
        doseEvents = doseEvents.sortedWith(
            compareBy<BackupDoseEventV1> { it.occurredAt }
                .thenBy { it.id }
        )
    )

    fun toPayload(settings: BackupSettingsV1): EvoluneBackupPayloadV1 =
        EvoluneBackupPayloadV1(
            medicationPlans = medicationPlans,
            scheduledDoseSlots = scheduledDoseSlots,
            doseEvents = doseEvents,
            settings = settings
        )

    companion object {
        fun fromPayload(payload: EvoluneBackupPayloadV1): RestoreRoomState =
            RestoreRoomState(
                medicationPlans = payload.medicationPlans,
                scheduledDoseSlots = payload.scheduledDoseSlots,
                doseEvents = payload.doseEvents
            ).canonical()
    }
}

internal class PreparedRestore internal constructor(
    val preview: RestorePreview,
    val room: RestoreRoomState,
    val settings: BackupSettingsV1
)

internal sealed interface RestorePrepareResult {
    data class Success(val prepared: PreparedRestore) : RestorePrepareResult
    data class Failure(val error: RestoreError) : RestorePrepareResult
}

internal sealed interface RestoreResult {
    data class Success(val cleanupPending: Boolean = false) : RestoreResult
    data class Failure(val error: RestoreError) : RestoreResult
}

internal sealed interface RestoreRecoveryResult {
    data object NothingToRecover : RestoreRecoveryResult
    data object Recovered : RestoreRecoveryResult
    data class Failure(val error: RestoreError) : RestoreRecoveryResult
}

/** The only persistence operations the transaction coordinator is allowed to use. */
internal interface RestorePersistence {
    suspend fun readRoomState(): RestoreRoomState
    suspend fun replaceRoom(state: RestoreRoomState)
    suspend fun readSettings(): BackupSettingsV1
    suspend fun replaceSettings(settings: BackupSettingsV1): Boolean
}

internal enum class RestoreJournalPhase { PREPARED, COMMITTED }

internal data class RestoreJournal(
    val formatVersion: Int,
    val operationId: String,
    val createdAt: String,
    val phase: RestoreJournalPhase,
    val beforeRoom: RestoreRoomState,
    val beforeSettings: BackupSettingsV1
)

internal sealed interface RestoreJournalReadResult {
    data object Missing : RestoreJournalReadResult
    data class Found(val journal: RestoreJournal) : RestoreJournalReadResult
    data class Failure(val error: RestoreError) : RestoreJournalReadResult
}

internal interface RestoreJournalStore {
    suspend fun read(): RestoreJournalReadResult
    suspend fun write(journal: RestoreJournal)
    suspend fun delete()
}

internal fun BackupSettingsV1.toUserSettings(): UserSettings = UserSettings(
    bodyWeight = bodyWeightKg,
    themeMode = ThemeMode.valueOf(themeMode),
    colorTheme = ColorTheme.valueOf(colorTheme),
    autoCheckUpdates = autoCheckUpdates,
    timeFormat = TimeFormat.valueOf(timeFormat)
)

internal fun UserSettings.toBackupSettings(): BackupSettingsV1 = BackupSettingsV1(
    bodyWeightKg = bodyWeight,
    themeMode = themeMode.name,
    colorTheme = colorTheme.name,
    autoCheckUpdates = autoCheckUpdates,
    timeFormat = timeFormat.name
)

internal fun restorePreview(
    payload: EvoluneBackupPayloadV1,
    metadata: BackupProducerMetadataV1?
): RestorePreview = RestorePreview(
    createdAt = metadata?.createdAt,
    producerAppVersionName = metadata?.producerAppVersionName,
    producerAppVersionCode = metadata?.producerAppVersionCode,
    medicationPlanCount = payload.medicationPlans.size,
    scheduledDoseSlotCount = payload.scheduledDoseSlots.size,
    doseEventCount = payload.doseEvents.size,
    bodyWeightKg = payload.settings.bodyWeightKg,
    themeMode = payload.settings.themeMode,
    colorTheme = payload.settings.colorTheme,
    autoCheckUpdates = payload.settings.autoCheckUpdates,
    timeFormat = payload.settings.timeFormat
)
