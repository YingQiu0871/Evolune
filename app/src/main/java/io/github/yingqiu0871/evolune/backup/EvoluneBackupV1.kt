package io.github.yingqiu0871.evolune.backup

/**
 * Provider-independent, versioned Evolune backup contract.
 *
 * These types intentionally use stable wire primitives instead of Room entities
 * or Android/provider-specific types. Enum-like values are validated against the
 * current domain vocabulary by [EvoluneBackupCodec].
 */
data class EvoluneBackupPayloadV1(
    val medicationPlans: List<BackupMedicationPlanV1>,
    val scheduledDoseSlots: List<BackupScheduledDoseSlotV1>,
    val doseEvents: List<BackupDoseEventV1>,
    val settings: BackupSettingsV1
)

data class BackupMedicationPlanV1(
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

data class BackupScheduledDoseSlotV1(
    val id: String,
    val planId: String,
    val localTime: String,
    val position: Int
)

data class BackupDoseEventV1(
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

data class BackupSettingsV1(
    val bodyWeightKg: Double,
    val themeMode: String,
    val colorTheme: String,
    val autoCheckUpdates: Boolean,
    val timeFormat: String
)

data class BackupProducerMetadataV1(
    val createdAt: String,
    val producerAppVersionName: String,
    val producerAppVersionCode: Int
)

data class EvoluneBackupEnvelopeV1(
    val magic: String,
    val envelopeFormatVersion: Int,
    val payloadSchemaVersion: Int,
    val createdAt: String,
    val producerAppVersionName: String,
    val producerAppVersionCode: Int,
    val encryptionAlgorithm: String,
    val kdfAlgorithm: String,
    val kdfIterations: Int,
    val derivedKeyLengthBits: Int,
    val saltBase64: String,
    val nonceBase64: String,
    val ciphertextBase64: String
)

data class ValidatedEvoluneBackupPayloadV1(
    val payload: EvoluneBackupPayloadV1
)

enum class BackupCodecErrorCode {
    NOT_EVOLUNE_BACKUP,
    MALFORMED_ENVELOPE,
    UNSUPPORTED_ENVELOPE_VERSION,
    UNSUPPORTED_PAYLOAD_VERSION,
    UNSUPPORTED_CRYPTO,
    INVALID_KDF_PARAMETERS,
    AUTHENTICATION_FAILED,
    MALFORMED_PAYLOAD,
    INVALID_PAYLOAD,
    CRYPTO_FAILURE,
    INVALID_SECRET
}

data class BackupCodecError(
    val code: BackupCodecErrorCode,
    val field: String? = null
)

sealed interface BackupValidationResult {
    data class Valid(
        val payload: ValidatedEvoluneBackupPayloadV1
    ) : BackupValidationResult

    data class Invalid(
        val error: BackupCodecError
    ) : BackupValidationResult
}

sealed interface BackupEncodeResult {
    data class Success(val bytes: ByteArray) : BackupEncodeResult

    data class Failure(val error: BackupCodecError) : BackupEncodeResult
}

sealed interface BackupDecodeResult {
    data class Success(
        val payload: ValidatedEvoluneBackupPayloadV1
    ) : BackupDecodeResult

    data class Failure(val error: BackupCodecError) : BackupDecodeResult
}

object EvoluneBackupFormat {
    const val MAGIC = "EVOLUNE_BACKUP"
    const val ENVELOPE_FORMAT_VERSION = 1
    const val PAYLOAD_SCHEMA_VERSION = 1
    const val ENCRYPTION_ALGORITHM = "AES-256-GCM"
    const val KDF_ALGORITHM = "PBKDF2-HMAC-SHA256"
    const val DEFAULT_KDF_ITERATIONS = 600_000
    const val MIN_KDF_ITERATIONS = 100_000
    const val MAX_KDF_ITERATIONS = 1_000_000
    const val DERIVED_KEY_LENGTH_BITS = 256
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val GCM_TAG_BITS = 128
}
