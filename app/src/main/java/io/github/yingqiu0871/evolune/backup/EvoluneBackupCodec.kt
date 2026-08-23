package io.github.yingqiu0871.evolune.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

fun interface BackupRandomSource {
    fun nextBytes(length: Int): ByteArray
}

object SecureBackupRandomSource : BackupRandomSource {
    private val secureRandom = SecureRandom()

    override fun nextBytes(length: Int): ByteArray =
        ByteArray(length).also(secureRandom::nextBytes)
}

class EvoluneBackupCodec(
    private val randomSource: BackupRandomSource = SecureBackupRandomSource
) {
    private val json = Json

    fun validate(payload: EvoluneBackupPayloadV1): BackupValidationResult {
        val planIds = linkedSetOf<String>()
        for ((index, plan) in payload.medicationPlans.withIndex()) {
            validateUuid(plan.id, "medicationPlans[$index].id")?.let {
                return BackupValidationResult.Invalid(it)
            }
            if (!planIds.add(plan.id)) {
                return invalid("medicationPlans[$index].id")
            }
            if (plan.route !in SUPPORTED_ROUTES) return invalid("medicationPlans[$index].route")
            if (plan.ester !in SUPPORTED_ESTERS) return invalid("medicationPlans[$index].ester")
            if (!plan.doseMG.isFinite() || plan.doseMG <= 0.0) {
                return invalid("medicationPlans[$index].doseMG")
            }
            if (plan.scheduleType !in SUPPORTED_SCHEDULE_TYPES) {
                return invalid("medicationPlans[$index].scheduleType")
            }
            if (!isCanonicalDaysOfWeek(plan.daysOfWeek)) {
                return invalid("medicationPlans[$index].daysOfWeek")
            }
            if (plan.scheduleType == "WEEKLY" && plan.daysOfWeek.isEmpty()) {
                return invalid("medicationPlans[$index].daysOfWeek")
            }
            if (plan.intervalDays < 1) return invalid("medicationPlans[$index].intervalDays")
            if (!validateFiniteExtras(plan.extras)) {
                return invalid("medicationPlans[$index].extras")
            }
            if (!isPersistableInstant(plan.createdAt)) {
                return invalid("medicationPlans[$index].createdAt")
            }
        }

        val slotIds = linkedSetOf<String>()
        val positionsByPlan = linkedMapOf<String, MutableList<Int>>()
        for ((index, slot) in payload.scheduledDoseSlots.withIndex()) {
            validateUuid(slot.id, "scheduledDoseSlots[$index].id")?.let {
                return BackupValidationResult.Invalid(it)
            }
            if (!slotIds.add(slot.id)) return invalid("scheduledDoseSlots[$index].id")
            validateUuid(slot.planId, "scheduledDoseSlots[$index].planId")?.let {
                return BackupValidationResult.Invalid(it)
            }
            if (slot.planId !in planIds) return invalid("scheduledDoseSlots[$index].planId")
            if (!isCanonicalLocalTime(slot.localTime)) {
                return invalid("scheduledDoseSlots[$index].localTime")
            }
            if (slot.position < 0) return invalid("scheduledDoseSlots[$index].position")
            positionsByPlan.getOrPut(slot.planId) { mutableListOf() }.add(slot.position)
        }
        positionsByPlan.values.forEach { positions ->
            if (positions.sorted() != (0 until positions.size).toList()) {
                return invalid("scheduledDoseSlots.position")
            }
        }

        val eventIds = linkedSetOf<String>()
        for ((index, event) in payload.doseEvents.withIndex()) {
            validateUuid(event.id, "doseEvents[$index].id")?.let {
                return BackupValidationResult.Invalid(it)
            }
            if (!eventIds.add(event.id)) return invalid("doseEvents[$index].id")
            if (event.route !in SUPPORTED_ROUTES) return invalid("doseEvents[$index].route")
            if (!isPersistableInstant(event.occurredAt)) {
                return invalid("doseEvents[$index].occurredAt")
            }
            if (event.zoneId != null && !isValidZoneId(event.zoneId)) {
                return invalid("doseEvents[$index].zoneId")
            }
            if (event.localDate != null && !isValidLocalDate(event.localDate)) {
                return invalid("doseEvents[$index].localDate")
            }
            if (!event.doseMG.isFinite() || event.doseMG < 0.0) {
                return invalid("doseEvents[$index].doseMG")
            }
            if (event.ester !in SUPPORTED_ESTERS) return invalid("doseEvents[$index].ester")
            if (!validateFiniteExtras(event.extras)) {
                return invalid("doseEvents[$index].extras")
            }
            if (event.slotId != null) {
                validateUuid(event.slotId, "doseEvents[$index].slotId")?.let {
                    return BackupValidationResult.Invalid(it)
                }
            }
            if (event.source !in SUPPORTED_EVENT_SOURCES) {
                return invalid("doseEvents[$index].source")
            }
            if (event.status !in SUPPORTED_EVENT_STATUSES) {
                return invalid("doseEvents[$index].status")
            }
            if (event.revision < 1L) return invalid("doseEvents[$index].revision")
        }

        val settings = payload.settings
        if (!settings.bodyWeightKg.isFinite() ||
            settings.bodyWeightKg <= 0.0 ||
            settings.bodyWeightKg > MAX_BODY_WEIGHT_KG
        ) {
            return invalid("settings.bodyWeightKg")
        }
        if (settings.themeMode !in SUPPORTED_THEME_MODES) {
            return invalid("settings.themeMode")
        }
        if (settings.colorTheme !in SUPPORTED_COLOR_THEMES) {
            return invalid("settings.colorTheme")
        }
        if (settings.timeFormat !in SUPPORTED_TIME_FORMATS) {
            return invalid("settings.timeFormat")
        }

        return BackupValidationResult.Valid(
            ValidatedEvoluneBackupPayloadV1(payload)
        )
    }

    fun encode(
        payload: EvoluneBackupPayloadV1,
        passphrase: CharArray,
        metadata: BackupProducerMetadataV1,
        kdfIterations: Int = EvoluneBackupFormat.DEFAULT_KDF_ITERATIONS
    ): BackupEncodeResult {
        val validated = when (val result = validate(payload)) {
            is BackupValidationResult.Valid -> result.payload
            is BackupValidationResult.Invalid -> return BackupEncodeResult.Failure(result.error)
        }
        validateSecret(passphrase)?.let { return BackupEncodeResult.Failure(it) }
        validateMetadata(metadata)?.let { return BackupEncodeResult.Failure(it) }
        validateKdfParameters(kdfIterations, EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS)
            ?.let { return BackupEncodeResult.Failure(it) }

        val salt = try {
            randomSource.nextBytes(EvoluneBackupFormat.SALT_BYTES)
        } catch (_: RuntimeException) {
            return BackupEncodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.CRYPTO_FAILURE)
            )
        }
        val nonce = try {
            randomSource.nextBytes(EvoluneBackupFormat.NONCE_BYTES)
        } catch (_: RuntimeException) {
            return BackupEncodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.CRYPTO_FAILURE)
            )
        }
        if (salt.size != EvoluneBackupFormat.SALT_BYTES ||
            nonce.size != EvoluneBackupFormat.NONCE_BYTES
        ) {
            return BackupEncodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.CRYPTO_FAILURE)
            )
        }

        val header = EvoluneBackupEnvelopeV1(
            magic = EvoluneBackupFormat.MAGIC,
            envelopeFormatVersion = EvoluneBackupFormat.ENVELOPE_FORMAT_VERSION,
            payloadSchemaVersion = EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION,
            createdAt = metadata.createdAt,
            producerAppVersionName = metadata.producerAppVersionName,
            producerAppVersionCode = metadata.producerAppVersionCode,
            encryptionAlgorithm = EvoluneBackupFormat.ENCRYPTION_ALGORITHM,
            kdfAlgorithm = EvoluneBackupFormat.KDF_ALGORITHM,
            kdfIterations = kdfIterations,
            derivedKeyLengthBits = EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS,
            saltBase64 = encodeBase64(salt),
            nonceBase64 = encodeBase64(nonce),
            ciphertextBase64 = ""
        )
        val plaintext = canonicalPayloadBytes(validated.payload)

        return try {
            val key = deriveKey(passphrase, salt, kdfIterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(EvoluneBackupFormat.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(canonicalAuthenticatedHeaderBytes(header))
            val ciphertext = cipher.doFinal(plaintext)
            val envelope = header.copy(ciphertextBase64 = encodeBase64(ciphertext))
            BackupEncodeResult.Success(canonicalEnvelopeBytes(envelope))
        } catch (_: GeneralSecurityException) {
            BackupEncodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.CRYPTO_FAILURE)
            )
        }
    }

    fun decodeAndValidate(
        bytes: ByteArray,
        passphrase: CharArray
    ): BackupDecodeResult {
        if (bytes.isEmpty()) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
            )
        }
        val jsonText = decodeUtf8(bytes) ?: return BackupDecodeResult.Failure(
            BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
        )
        if (hasDuplicateJsonObjectKeys(jsonText)) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
            )
        }
        val root = try {
            json.parseToJsonElement(jsonText)
        } catch (_: SerializationException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
            )
        } catch (_: IllegalArgumentException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
            )
        }
        val rootObject = root as? JsonObject ?: return BackupDecodeResult.Failure(
            BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE)
        )
        val magic = rootObject.stringOrNull("magic")
        if (magic != EvoluneBackupFormat.MAGIC) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.NOT_EVOLUNE_BACKUP)
            )
        }
        val envelope = try {
            parseEnvelope(rootObject)
        } catch (error: EnvelopeFormatException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, error.field)
            )
        }
        if (envelope.envelopeFormatVersion != EvoluneBackupFormat.ENVELOPE_FORMAT_VERSION) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.UNSUPPORTED_ENVELOPE_VERSION)
            )
        }
        if (envelope.payloadSchemaVersion != EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION)
            )
        }
        if (envelope.encryptionAlgorithm != EvoluneBackupFormat.ENCRYPTION_ALGORITHM ||
            envelope.kdfAlgorithm != EvoluneBackupFormat.KDF_ALGORITHM
        ) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.UNSUPPORTED_CRYPTO)
            )
        }
        validateKdfParameters(
            envelope.kdfIterations,
            envelope.derivedKeyLengthBits
        )?.let { return BackupDecodeResult.Failure(it) }
        if (envelope.ciphertextBase64.isEmpty()) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "ciphertext")
            )
        }
        validateSecret(passphrase)?.let { return BackupDecodeResult.Failure(it) }

        val salt = decodeBase64(envelope.saltBase64) ?: return BackupDecodeResult.Failure(
            BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "salt")
        )
        if (salt.size != EvoluneBackupFormat.SALT_BYTES) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.INVALID_KDF_PARAMETERS, "salt")
            )
        }
        val nonce = decodeBase64(envelope.nonceBase64) ?: return BackupDecodeResult.Failure(
            BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "nonce")
        )
        if (nonce.size != EvoluneBackupFormat.NONCE_BYTES) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "nonce")
            )
        }
        val ciphertext = decodeBase64(envelope.ciphertextBase64)
            ?: return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "ciphertext")
            )
        if (ciphertext.size <= EvoluneBackupFormat.GCM_TAG_BITS / 8) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "ciphertext")
            )
        }

        val plaintext = try {
            val key = deriveKey(passphrase, salt, envelope.kdfIterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(EvoluneBackupFormat.GCM_TAG_BITS, nonce)
            )
            cipher.updateAAD(canonicalAuthenticatedHeaderBytes(envelope))
            cipher.doFinal(ciphertext)
        } catch (_: BadPaddingException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.AUTHENTICATION_FAILED)
            )
        } catch (_: GeneralSecurityException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.CRYPTO_FAILURE)
            )
        }

        val payloadText = decodeUtf8(plaintext) ?: return BackupDecodeResult.Failure(
            BackupCodecError(BackupCodecErrorCode.MALFORMED_PAYLOAD)
        )
        if (hasDuplicateJsonObjectKeys(payloadText)) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_PAYLOAD)
            )
        }
        val payload = try {
            parsePayload(payloadText)
        } catch (error: UnsupportedPayloadVersionException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION)
            )
        } catch (error: PayloadFormatException) {
            return BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.MALFORMED_PAYLOAD, error.field)
            )
        }
        return when (val validation = validate(payload)) {
            is BackupValidationResult.Valid -> BackupDecodeResult.Success(validation.payload)
            is BackupValidationResult.Invalid -> BackupDecodeResult.Failure(
                BackupCodecError(BackupCodecErrorCode.INVALID_PAYLOAD, validation.error.field)
            )
        }
    }

    private fun parseEnvelope(root: JsonObject): EvoluneBackupEnvelopeV1 {
        requireExactEnvelopeFields(root)
        return EvoluneBackupEnvelopeV1(
            magic = root.requiredEnvelopeString("magic"),
            envelopeFormatVersion = root.requiredEnvelopeInt("envelopeFormatVersion"),
            payloadSchemaVersion = root.requiredEnvelopeInt("payloadSchemaVersion"),
            createdAt = root.requiredEnvelopeString("createdAt"),
            producerAppVersionName = root.requiredEnvelopeString("producerAppVersionName"),
            producerAppVersionCode = root.requiredEnvelopeInt("producerAppVersionCode"),
            encryptionAlgorithm = root.requiredEnvelopeString("encryptionAlgorithm"),
            kdfAlgorithm = root.requiredEnvelopeString("kdfAlgorithm"),
            kdfIterations = root.requiredEnvelopeInt("kdfIterations"),
            derivedKeyLengthBits = root.requiredEnvelopeInt("derivedKeyLengthBits"),
            saltBase64 = root.requiredEnvelopeString("salt"),
            nonceBase64 = root.requiredEnvelopeString("nonce"),
            ciphertextBase64 = root.requiredEnvelopeString("ciphertext")
        )
    }

    private fun parsePayload(text: String): EvoluneBackupPayloadV1 {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            throw PayloadFormatException(null)
        } catch (_: IllegalArgumentException) {
            throw PayloadFormatException(null)
        }
        val rootObject = root as? JsonObject ?: throw PayloadFormatException(null)
        requireExactPayloadFields(rootObject)
        val payloadVersion = rootObject.requiredPayloadInt("payloadSchemaVersion")
        if (payloadVersion != EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION) {
            throw UnsupportedPayloadVersionException()
        }
        val plans = rootObject.requiredPayloadArray("medicationPlans").mapIndexed { index, element ->
            parsePlan(element, index)
        }
        val slots = rootObject.requiredPayloadArray("scheduledDoseSlots").mapIndexed { index, element ->
            parseSlot(element, index)
        }
        val events = rootObject.requiredPayloadArray("doseEvents").mapIndexed { index, element ->
            parseEvent(element, index)
        }
        val settings = parseSettings(rootObject.requiredPayloadObject("settings"))
        return EvoluneBackupPayloadV1(plans, slots, events, settings)
    }

    private fun parsePlan(element: JsonElement, index: Int): BackupMedicationPlanV1 {
        val objectValue = element as? JsonObject ?: throw PayloadFormatException("medicationPlans[$index]")
        requireExactPayloadFields(objectValue, "medicationPlans[$index]")
        return BackupMedicationPlanV1(
            id = objectValue.requiredPayloadString("id"),
            name = objectValue.requiredPayloadString("name"),
            route = objectValue.requiredPayloadString("route"),
            ester = objectValue.requiredPayloadString("ester"),
            doseMG = objectValue.requiredPayloadDouble("doseMG"),
            scheduleType = objectValue.requiredPayloadString("scheduleType"),
            daysOfWeek = objectValue.requiredPayloadArray("daysOfWeek").mapIndexed { dayIndex, day ->
                (day as? JsonPrimitive)?.intOrNull
                    ?: throw PayloadFormatException("medicationPlans[$index].daysOfWeek[$dayIndex]")
            },
            intervalDays = objectValue.requiredPayloadInt("intervalDays"),
            isEnabled = objectValue.requiredPayloadBoolean("isEnabled"),
            extras = parseExtras(objectValue.requiredPayloadObject("extras"), "medicationPlans[$index].extras"),
            createdAt = objectValue.requiredPayloadString("createdAt")
        )
    }

    private fun parseSlot(element: JsonElement, index: Int): BackupScheduledDoseSlotV1 {
        val objectValue = element as? JsonObject
            ?: throw PayloadFormatException("scheduledDoseSlots[$index]")
        requireExactPayloadFields(objectValue, "scheduledDoseSlots[$index]")
        return BackupScheduledDoseSlotV1(
            id = objectValue.requiredPayloadString("id"),
            planId = objectValue.requiredPayloadString("planId"),
            localTime = objectValue.requiredPayloadString("localTime"),
            position = objectValue.requiredPayloadInt("position")
        )
    }

    private fun parseEvent(element: JsonElement, index: Int): BackupDoseEventV1 {
        val objectValue = element as? JsonObject
            ?: throw PayloadFormatException("doseEvents[$index]")
        requireExactPayloadFields(objectValue, "doseEvents[$index]")
        return BackupDoseEventV1(
            id = objectValue.requiredPayloadString("id"),
            route = objectValue.requiredPayloadString("route"),
            occurredAt = objectValue.requiredPayloadString("occurredAt"),
            zoneId = objectValue.requiredPayloadNullableString("zoneId"),
            localDate = objectValue.requiredPayloadNullableString("localDate"),
            doseMG = objectValue.requiredPayloadDouble("doseMG"),
            ester = objectValue.requiredPayloadString("ester"),
            extras = parseExtras(objectValue.requiredPayloadObject("extras"), "doseEvents[$index].extras"),
            slotId = objectValue.requiredPayloadNullableString("slotId"),
            source = objectValue.requiredPayloadString("source"),
            status = objectValue.requiredPayloadString("status"),
            revision = objectValue.requiredPayloadLong("revision")
        )
    }

    private fun parseSettings(objectValue: JsonObject): BackupSettingsV1 {
        requireExactPayloadFields(objectValue, "settings")
        return BackupSettingsV1(
            bodyWeightKg = objectValue.requiredPayloadDouble("bodyWeightKg"),
            themeMode = objectValue.requiredPayloadString("themeMode"),
            colorTheme = objectValue.requiredPayloadString("colorTheme"),
            autoCheckUpdates = objectValue.requiredPayloadBoolean("autoCheckUpdates"),
            timeFormat = objectValue.requiredPayloadString("timeFormat")
        )
    }

    private fun parseExtras(objectValue: JsonObject, field: String): Map<String, Double> =
        objectValue.entries.associate { (key, value) ->
            val number = (value as? JsonPrimitive)?.doubleOrNull
                ?: throw PayloadFormatException("$field.$key")
            key to number
        }

    private fun canonicalPayloadBytes(payload: EvoluneBackupPayloadV1): ByteArray {
        val root = buildJsonObject {
            put("payloadSchemaVersion", EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION)
            putJsonArray("medicationPlans") {
                payload.medicationPlans
                    .sortedBy { it.id }
                    .forEach { plan ->
                        addJsonObject {
                            put("id", plan.id)
                            put("name", plan.name)
                            put("route", plan.route)
                            put("ester", plan.ester)
                            put("doseMG", plan.doseMG)
                            put("scheduleType", plan.scheduleType)
                            putJsonArray("daysOfWeek") {
                                plan.daysOfWeek.sorted().forEach(::add)
                            }
                            put("intervalDays", plan.intervalDays)
                            put("isEnabled", plan.isEnabled)
                            putCanonicalExtras("extras", plan.extras)
                            put("createdAt", canonicalInstant(plan.createdAt))
                        }
                    }
            }
            putJsonArray("scheduledDoseSlots") {
                payload.scheduledDoseSlots
                    .sortedWith(compareBy<BackupScheduledDoseSlotV1> { it.planId }
                        .thenBy { it.position }
                        .thenBy { it.id })
                    .forEach { slot ->
                        addJsonObject {
                            put("id", slot.id)
                            put("planId", slot.planId)
                            put("localTime", canonicalLocalTime(slot.localTime))
                            put("position", slot.position)
                        }
                    }
            }
            putJsonArray("doseEvents") {
                payload.doseEvents
                    .sortedWith(compareBy<BackupDoseEventV1> { canonicalInstant(it.occurredAt) }
                        .thenBy { it.id })
                    .forEach { event ->
                        addJsonObject {
                            put("id", event.id)
                            put("route", event.route)
                            put("occurredAt", canonicalInstant(event.occurredAt))
                            put("zoneId", event.zoneId)
                            put("localDate", event.localDate)
                            put("doseMG", event.doseMG)
                            put("ester", event.ester)
                            putCanonicalExtras("extras", event.extras)
                            put("slotId", event.slotId)
                            put("source", event.source)
                            put("status", event.status)
                            put("revision", event.revision)
                        }
                    }
            }
            putJsonObject("settings") {
                put("bodyWeightKg", payload.settings.bodyWeightKg)
                put("themeMode", payload.settings.themeMode)
                put("colorTheme", payload.settings.colorTheme)
                put("autoCheckUpdates", payload.settings.autoCheckUpdates)
                put("timeFormat", payload.settings.timeFormat)
            }
        }
        return json.encodeToString(JsonElement.serializer(), root)
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun canonicalEnvelopeBytes(envelope: EvoluneBackupEnvelopeV1): ByteArray =
        json.encodeToString(JsonElement.serializer(), envelopeJson(envelope))
            .toByteArray(StandardCharsets.UTF_8)

    /**
     * V1 AAD is the canonical JSON object with these fields in this order:
     * magic, envelopeFormatVersion, payloadSchemaVersion, createdAt,
     * producerAppVersionName, producerAppVersionCode, encryptionAlgorithm,
     * kdfAlgorithm, kdfIterations, derivedKeyLengthBits, salt, nonce.
     * Changing this canonical representation requires a new envelope format version.
     */
    private fun canonicalAuthenticatedHeaderBytes(envelope: EvoluneBackupEnvelopeV1): ByteArray =
        json.encodeToString(JsonElement.serializer(), authenticatedHeaderJson(envelope))
            .toByteArray(StandardCharsets.UTF_8)

    private fun envelopeJson(envelope: EvoluneBackupEnvelopeV1): JsonObject = buildJsonObject {
        put("magic", envelope.magic)
        put("envelopeFormatVersion", envelope.envelopeFormatVersion)
        put("payloadSchemaVersion", envelope.payloadSchemaVersion)
        put("createdAt", envelope.createdAt)
        put("producerAppVersionName", envelope.producerAppVersionName)
        put("producerAppVersionCode", envelope.producerAppVersionCode)
        put("encryptionAlgorithm", envelope.encryptionAlgorithm)
        put("kdfAlgorithm", envelope.kdfAlgorithm)
        put("kdfIterations", envelope.kdfIterations)
        put("derivedKeyLengthBits", envelope.derivedKeyLengthBits)
        put("salt", envelope.saltBase64)
        put("nonce", envelope.nonceBase64)
        put("ciphertext", envelope.ciphertextBase64)
    }

    private fun authenticatedHeaderJson(envelope: EvoluneBackupEnvelopeV1): JsonObject =
        buildJsonObject {
            put("magic", envelope.magic)
            put("envelopeFormatVersion", envelope.envelopeFormatVersion)
            put("payloadSchemaVersion", envelope.payloadSchemaVersion)
            put("createdAt", envelope.createdAt)
            put("producerAppVersionName", envelope.producerAppVersionName)
            put("producerAppVersionCode", envelope.producerAppVersionCode)
            put("encryptionAlgorithm", envelope.encryptionAlgorithm)
            put("kdfAlgorithm", envelope.kdfAlgorithm)
            put("kdfIterations", envelope.kdfIterations)
            put("derivedKeyLengthBits", envelope.derivedKeyLengthBits)
            put("salt", envelope.saltBase64)
            put("nonce", envelope.nonceBase64)
        }

    private fun JsonObjectBuilder.putCanonicalExtras(key: String, values: Map<String, Double>) {
        putJsonObject(key) {
            values.toSortedMap().forEach { (extraKey, value) -> put(extraKey, value) }
        }
    }

    private fun deriveKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKeySpec {
        val keySpec = PBEKeySpec(
            passphrase,
            salt,
            iterations,
            EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS
        )
        return try {
            val keyBytes = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(keySpec)
                .encoded
            try {
                SecretKeySpec(keyBytes, "AES")
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            keySpec.clearPassword()
        }
    }

    private fun validateSecret(passphrase: CharArray): BackupCodecError? =
        if (passphrase.isEmpty()) {
            BackupCodecError(BackupCodecErrorCode.INVALID_SECRET)
        } else {
            null
        }

    private fun validateMetadata(metadata: BackupProducerMetadataV1): BackupCodecError? {
        if (!isPersistableInstant(metadata.createdAt)) {
            return BackupCodecError(BackupCodecErrorCode.MALFORMED_ENVELOPE, "createdAt")
        }
        if (metadata.producerAppVersionName.isBlank() || metadata.producerAppVersionCode < 0) {
            return BackupCodecError(
                BackupCodecErrorCode.MALFORMED_ENVELOPE,
                "producerAppVersion"
            )
        }
        return null
    }

    private fun validateKdfParameters(
        iterations: Int,
        keyLengthBits: Int
    ): BackupCodecError? {
        if (iterations !in EvoluneBackupFormat.MIN_KDF_ITERATIONS..EvoluneBackupFormat.MAX_KDF_ITERATIONS) {
            return BackupCodecError(BackupCodecErrorCode.INVALID_KDF_PARAMETERS, "kdfIterations")
        }
        if (keyLengthBits != EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS) {
            return BackupCodecError(
                BackupCodecErrorCode.INVALID_KDF_PARAMETERS,
                "derivedKeyLengthBits"
            )
        }
        return null
    }

    private fun validateUuid(value: String, field: String): BackupCodecError? = try {
        require(value.isNotBlank())
        val uuid = java.util.UUID.fromString(value)
        require(uuid.toString() == value)
        null
    } catch (_: IllegalArgumentException) {
        BackupCodecError(BackupCodecErrorCode.INVALID_PAYLOAD, field)
    }

    private fun validateFiniteExtras(values: Map<String, Double>): Boolean =
        values.keys.all { it in SUPPORTED_EXTRA_KEYS } && values.values.all(Double::isFinite)

    private fun isCanonicalDaysOfWeek(days: List<Int>): Boolean =
        days == days.distinct().sorted() && days.all { it in 1..7 }

    private fun isPersistableInstant(value: String): Boolean = try {
        val instant = Instant.parse(value)
        Instant.ofEpochMilli(instant.toEpochMilli()) == instant
    } catch (_: DateTimeException) {
        false
    } catch (_: ArithmeticException) {
        false
    }

    private fun isCanonicalLocalTime(value: String): Boolean = try {
        val parsed = LocalTime.parse(value)
        parsed.second == 0 && parsed.nano == 0 && canonicalLocalTime(value) == value
    } catch (_: DateTimeException) {
        false
    }

    private fun canonicalLocalTime(value: String): String {
        val parsed = LocalTime.parse(value)
        return parsed.hour.toString().padStart(2, '0') + ":" +
            parsed.minute.toString().padStart(2, '0')
    }

    private fun canonicalInstant(value: String): String = Instant.parse(value).toString()

    private fun isValidZoneId(value: String): Boolean = try {
        ZoneId.of(value)
        true
    } catch (_: DateTimeException) {
        false
    }

    private fun isValidLocalDate(value: String): Boolean = try {
        LocalDate.parse(value)
        true
    } catch (_: DateTimeException) {
        false
    }

    private fun invalid(field: String): BackupValidationResult.Invalid =
        BackupValidationResult.Invalid(
            BackupCodecError(BackupCodecErrorCode.INVALID_PAYLOAD, field)
        )

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)

    private fun decodeBase64(value: String): ByteArray? = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun hasDuplicateJsonObjectKeys(text: String): Boolean =
        JsonObjectKeyScanner(text).scan()

    /** Lightweight lexical scan; kotlinx.serialization remains the semantic JSON parser. */
    private class JsonObjectKeyScanner(
        private val text: String
    ) {
        private var index = 0
        private var duplicateFound = false

        fun scan(): Boolean {
            if (!scanValue()) return false
            skipWhitespace()
            return index == text.length && duplicateFound
        }

        private fun scanValue(): Boolean {
            skipWhitespace()
            if (index >= text.length) return false
            return when (text[index]) {
                '"' -> readString() != null
                '{' -> scanObject()
                '[' -> scanArray()
                else -> scanPrimitive()
            }
        }

        private fun scanObject(): Boolean {
            index++
            skipWhitespace()
            if (consume('}')) return true

            val keys = mutableSetOf<String>()
            while (true) {
                skipWhitespace()
                val key = readString() ?: return false
                if (!keys.add(key)) duplicateFound = true
                skipWhitespace()
                if (!consume(':')) return false
                if (!scanValue()) return false
                skipWhitespace()
                when {
                    consume('}') -> return true
                    consume(',') -> Unit
                    else -> return false
                }
            }
        }

        private fun scanArray(): Boolean {
            index++
            skipWhitespace()
            if (consume(']')) return true

            while (true) {
                if (!scanValue()) return false
                skipWhitespace()
                when {
                    consume(']') -> return true
                    consume(',') -> Unit
                    else -> return false
                }
            }
        }

        private fun scanPrimitive(): Boolean {
            val start = index
            while (index < text.length && text[index] !in " \t\r\n,]}") {
                index++
            }
            return index > start
        }

        private fun readString(): String? {
            if (!consume('"')) return null
            val value = StringBuilder()
            while (index < text.length) {
                when (val character = text[index++]) {
                    '"' -> return value.toString()
                    '\\' -> if (!readEscape(value)) return null
                    else -> {
                        if (character.code < 0x20) return null
                        value.append(character)
                    }
                }
            }
            return null
        }

        private fun readEscape(value: StringBuilder): Boolean {
            if (index >= text.length) return false
            when (val escape = text[index++]) {
                '"', '\\', '/' -> value.append(escape)
                'b' -> value.append('\b')
                'f' -> value.append('\u000C')
                'n' -> value.append('\n')
                'r' -> value.append('\r')
                't' -> value.append('\t')
                'u' -> {
                    if (index + 4 > text.length) return false
                    var codePoint = 0
                    repeat(4) {
                        val digit = Character.digit(text[index++], 16)
                        if (digit < 0) return false
                        codePoint = (codePoint shl 4) or digit
                    }
                    value.append(codePoint.toChar())
                }
                else -> return false
            }
            return true
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index] in " \t\r\n") index++
        }

        private fun consume(expected: Char): Boolean =
            if (index < text.length && text[index] == expected) {
                index++
                true
            } else {
                false
            }
    }

    private fun requireExactEnvelopeFields(objectValue: JsonObject) {
        if (objectValue.keys != ENVELOPE_FIELDS) throw EnvelopeFormatException(null)
    }

    private fun requireExactPayloadFields(objectValue: JsonObject, field: String? = null) {
        val expected = when (field) {
            null -> PAYLOAD_FIELDS
            "settings" -> SETTINGS_FIELDS
            else -> when {
                field.startsWith("medicationPlans[") -> PLAN_FIELDS
                field.startsWith("scheduledDoseSlots[") -> SLOT_FIELDS
                field.startsWith("doseEvents[") -> EVENT_FIELDS
                else -> PAYLOAD_FIELDS
            }
        }
        if (objectValue.keys != expected) throw PayloadFormatException(field)
    }

    private fun JsonObject.stringOrNull(field: String): String? =
        (this[field] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content

    private fun JsonObject.requiredEnvelopeString(field: String): String =
        stringOrNull(field) ?: throw EnvelopeFormatException(field)

    private fun JsonObject.requiredPayloadString(field: String): String =
        stringOrNull(field) ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadNullableString(field: String): String? {
        val value = this[field] ?: throw PayloadFormatException(field)
        if (value == JsonNull) return null
        return (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?: throw PayloadFormatException(field)
    }

    private fun JsonObject.requiredEnvelopeInt(field: String): Int =
        (this[field] as? JsonPrimitive)?.intOrNull
            ?: throw EnvelopeFormatException(field)

    private fun JsonObject.requiredPayloadInt(field: String): Int =
        (this[field] as? JsonPrimitive)?.intOrNull
            ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadLong(field: String): Long =
        (this[field] as? JsonPrimitive)?.longOrNull
            ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadDouble(field: String): Double =
        (this[field] as? JsonPrimitive)?.doubleOrNull
            ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadBoolean(field: String): Boolean =
        (this[field] as? JsonPrimitive)?.booleanOrNull
            ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadArray(field: String): JsonArray =
        (this[field] as? JsonArray) ?: throw PayloadFormatException(field)

    private fun JsonObject.requiredPayloadObject(field: String): JsonObject =
        (this[field] as? JsonObject) ?: throw PayloadFormatException(field)

    private companion object {
        const val MAX_BODY_WEIGHT_KG = 300.0

        val SUPPORTED_ROUTES = setOf(
            "INJECTION", "ORAL", "SUBLINGUAL", "GEL", "PATCH_APPLY", "PATCH_REMOVE", "ANTIANDROGEN"
        )
        val SUPPORTED_ESTERS = setOf("E2", "EB", "EV", "EC", "EN")
        val SUPPORTED_SCHEDULE_TYPES = setOf("DAILY", "WEEKLY", "CUSTOM")
        val SUPPORTED_EVENT_SOURCES = setOf("LEGACY", "MANUAL", "JSON_V1", "REMINDER", "WIDGET", "WEAR")
        val SUPPORTED_EVENT_STATUSES = setOf("RECORDED")
        val SUPPORTED_EXTRA_KEYS = setOf(
            "CONCENTRATION_MG_ML",
            "AREA_CM2",
            "RELEASE_RATE_UG_PER_DAY",
            "SUBLINGUAL_THETA",
            "SUBLINGUAL_TIER",
            "ANTI_ANDROGEN_TYPE"
        )
        val SUPPORTED_THEME_MODES = setOf("LIGHT", "DARK", "AMOLED", "SYSTEM")
        val SUPPORTED_COLOR_THEMES = setOf("DYNAMIC", "BUILTIN")
        val SUPPORTED_TIME_FORMATS = setOf("SYSTEM", "HOUR_12", "HOUR_24")

        val ENVELOPE_FIELDS = setOf(
            "magic",
            "envelopeFormatVersion",
            "payloadSchemaVersion",
            "createdAt",
            "producerAppVersionName",
            "producerAppVersionCode",
            "encryptionAlgorithm",
            "kdfAlgorithm",
            "kdfIterations",
            "derivedKeyLengthBits",
            "salt",
            "nonce",
            "ciphertext"
        )
        val PAYLOAD_FIELDS = setOf(
            "payloadSchemaVersion",
            "medicationPlans",
            "scheduledDoseSlots",
            "doseEvents",
            "settings"
        )
        val PLAN_FIELDS = setOf(
            "id", "name", "route", "ester", "doseMG", "scheduleType", "daysOfWeek",
            "intervalDays", "isEnabled", "extras", "createdAt"
        )
        val SLOT_FIELDS = setOf("id", "planId", "localTime", "position")
        val EVENT_FIELDS = setOf(
            "id", "route", "occurredAt", "zoneId", "localDate", "doseMG", "ester", "extras",
            "slotId", "source", "status", "revision"
        )
        val SETTINGS_FIELDS = setOf(
            "bodyWeightKg", "themeMode", "colorTheme", "autoCheckUpdates", "timeFormat"
        )
    }
}

private class EnvelopeFormatException(val field: String?) : RuntimeException()

private class PayloadFormatException(val field: String?) : RuntimeException()

private class UnsupportedPayloadVersionException : RuntimeException()
