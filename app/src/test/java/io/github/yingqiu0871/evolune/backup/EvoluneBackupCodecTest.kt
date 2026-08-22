package io.github.yingqiu0871.evolune.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class EvoluneBackupCodecTest {
    private val passphrase = "correct horse battery staple".toCharArray()
    private val metadata = BackupProducerMetadataV1(
        createdAt = "2026-08-23T12:34:56Z",
        producerAppVersionName = "1.1.0",
        producerAppVersionCode = 100_000_003
    )
    private val json = Json

    @Test
    fun `representative payload round trips with every supported field preserved`() {
        val payload = representativePayload()

        val encoded = requireEncoded(payload)
        val decoded = requireDecoded(encoded)

        assertEquals(payload, decoded.payload)
    }

    @Test
    fun `fixed salt and nonce produce stable golden representation`() {
        val codec = EvoluneBackupCodec(FixedRandomSource())
        val encoded = requireEncoded(
            representativePayload(),
            codec = codec,
            kdfIterations = EvoluneBackupFormat.DEFAULT_KDF_ITERATIONS
        )

        assertEquals("5cbc47bc978c23abcd3e6cbafcad25d4e96208a13dfff6e4a8f5e174349eeaa9", sha256Hex(encoded))
    }

    @Test
    fun `randomized encodes differ and do not reuse salt or nonce`() {
        val codec = EvoluneBackupCodec()
        val first = requireEncoded(representativePayload(), codec, kdfIterations = 100_000)
        val second = requireEncoded(representativePayload(), codec, kdfIterations = 100_000)

        assertNotEquals(String(first, StandardCharsets.UTF_8), String(second, StandardCharsets.UTF_8))
        assertNotEquals(envelopeField(first, "salt"), envelopeField(second, "salt"))
        assertNotEquals(envelopeField(first, "nonce"), envelopeField(second, "nonce"))
    }

    @Test
    fun `correct passphrase succeeds and wrong passphrase is authentication failure`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)

        val success = requireDecoded(encoded)
        assertEquals(representativePayload(), success.payload)

        val wrong = EvoluneBackupCodec().decodeAndValidate(
            encoded,
            "wrong passphrase".toCharArray()
        )
        assertFailure(wrong, BackupCodecErrorCode.AUTHENTICATION_FAILED)
    }

    @Test
    fun `tampered ciphertext and authenticated metadata fail authentication`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)
        val ciphertext = Base64.getDecoder().decode(envelopeField(encoded, "ciphertext"))
        ciphertext[0] = (ciphertext[0].toInt() xor 1).toByte()
        val tamperedCiphertext = replaceEnvelopeField(
            encoded,
            "ciphertext",
            JsonPrimitive(Base64.getEncoder().encodeToString(ciphertext))
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(tamperedCiphertext, passphrase),
            BackupCodecErrorCode.AUTHENTICATION_FAILED
        )

        val tamperedHeader = replaceEnvelopeField(
            encoded,
            "createdAt",
            JsonPrimitive("2026-08-23T12:34:57Z")
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(tamperedHeader, passphrase),
            BackupCodecErrorCode.AUTHENTICATION_FAILED
        )
    }

    @Test
    fun `future envelope and payload versions fail closed before decoding`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)

        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                replaceEnvelopeField(encoded, "envelopeFormatVersion", JsonPrimitive(2)),
                passphrase
            ),
            BackupCodecErrorCode.UNSUPPORTED_ENVELOPE_VERSION
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                replaceEnvelopeField(encoded, "payloadSchemaVersion", JsonPrimitive(2)),
                passphrase
            ),
            BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION
        )
    }

    @Test
    fun `invalid magic algorithm and malformed envelope are rejected`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                replaceEnvelopeField(encoded, "magic", JsonPrimitive("OTHER_BACKUP")),
                passphrase
            ),
            BackupCodecErrorCode.NOT_EVOLUNE_BACKUP
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                replaceEnvelopeField(encoded, "encryptionAlgorithm", JsonPrimitive("AES-128-GCM")),
                passphrase
            ),
            BackupCodecErrorCode.UNSUPPORTED_CRYPTO
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(encoded.copyOf(encoded.size / 2), passphrase),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(byteArrayOf(0xC3.toByte(), 0x28), passphrase),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(ByteArray(0), passphrase),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
    }

    @Test
    fun `invalid kdf iterations salt nonce and secret are rejected`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)
        val codec = EvoluneBackupCodec()

        assertFailure(
            codec.decodeAndValidate(
                replaceEnvelopeField(encoded, "kdfIterations", JsonPrimitive(0)),
                passphrase
            ),
            BackupCodecErrorCode.INVALID_KDF_PARAMETERS
        )
        assertFailure(
            codec.decodeAndValidate(
                replaceEnvelopeField(
                    encoded,
                    "kdfIterations",
                    JsonPrimitive(EvoluneBackupFormat.MAX_KDF_ITERATIONS + 1)
                ),
                passphrase
            ),
            BackupCodecErrorCode.INVALID_KDF_PARAMETERS
        )
        assertFailure(
            codec.decodeAndValidate(
                replaceEnvelopeField(
                    encoded,
                    "salt",
                    JsonPrimitive(Base64.getEncoder().encodeToString(byteArrayOf(1)))
                ),
                passphrase
            ),
            BackupCodecErrorCode.INVALID_KDF_PARAMETERS
        )
        assertFailure(
            codec.decodeAndValidate(
                replaceEnvelopeField(
                    encoded,
                    "nonce",
                    JsonPrimitive(Base64.getEncoder().encodeToString(byteArrayOf(1)))
                ),
                passphrase
            ),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
        assertFailure(
            codec.decodeAndValidate(encoded, CharArray(0)),
            BackupCodecErrorCode.INVALID_SECRET
        )
    }

    @Test
    fun `missing or extra envelope fields are malformed`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)

        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                removeEnvelopeField(encoded, "ciphertext"),
                passphrase
            ),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(
                addEnvelopeField(encoded, "unexpected", JsonPrimitive(true)),
                passphrase
            ),
            BackupCodecErrorCode.MALFORMED_ENVELOPE
        )
    }

    @Test
    fun `authenticated malformed payload is rejected before domain validation`() {
        val encoded = requireEncoded(representativePayload(), kdfIterations = 100_000)
        val malformedPayload = replaceCiphertextWithPayload(
            encoded,
            "{\"payloadSchemaVersion\":1"
        )

        assertFailure(
            EvoluneBackupCodec().decodeAndValidate(malformedPayload, passphrase),
            BackupCodecErrorCode.MALFORMED_PAYLOAD
        )
    }

    @Test
    fun `payload validation rejects duplicate ids and broken references`() {
        val payload = representativePayload()
        val codec = EvoluneBackupCodec()

        assertInvalid(
            codec.validate(payload.copy(medicationPlans = listOf(payload.medicationPlans[0], payload.medicationPlans[0])))
        )
        assertInvalid(
            codec.validate(payload.copy(scheduledDoseSlots = listOf(payload.scheduledDoseSlots[0], payload.scheduledDoseSlots[0])))
        )
        assertInvalid(
            codec.validate(
                payload.copy(
                    scheduledDoseSlots = listOf(
                        payload.scheduledDoseSlots[0].copy(planId = ORPHAN_PLAN_ID)
                    )
                )
            )
        )
        assertInvalid(
            codec.validate(payload.copy(doseEvents = listOf(payload.doseEvents[0], payload.doseEvents[0])))
        )
    }

    @Test
    fun `payload validation rejects invalid numbers timestamps enums and settings`() {
        val payload = representativePayload()
        val codec = EvoluneBackupCodec()

        assertInvalid(codec.validate(payload.copy(settings = payload.settings.copy(bodyWeightKg = Double.NaN))))
        assertInvalid(
            codec.validate(
                payload.copy(
                    medicationPlans = listOf(payload.medicationPlans[0].copy(doseMG = Double.POSITIVE_INFINITY))
                )
            )
        )
        assertInvalid(
            codec.validate(
                payload.copy(
                    doseEvents = listOf(payload.doseEvents[0].copy(doseMG = -1.0))
                )
            )
        )
        assertInvalid(
            codec.validate(
                payload.copy(
                    doseEvents = listOf(payload.doseEvents[0].copy(occurredAt = "not-a-timestamp"))
                )
            )
        )
        assertInvalid(
            codec.validate(
                payload.copy(
                    medicationPlans = listOf(payload.medicationPlans[0].copy(route = "UNKNOWN"))
                )
            )
        )
        assertInvalid(
            codec.validate(
                payload.copy(
                    settings = payload.settings.copy(bodyWeightKg = 301.0)
                )
            )
        )
    }

    private fun representativePayload(): EvoluneBackupPayloadV1 =
        EvoluneBackupPayloadV1(
            medicationPlans = listOf(
                BackupMedicationPlanV1(
                    id = PLAN_ID,
                    name = "Estradiol weekly plan",
                    route = "SUBLINGUAL",
                    ester = "EV",
                    doseMG = 2.5,
                    scheduleType = "WEEKLY",
                    daysOfWeek = listOf(1, 3, 5),
                    intervalDays = 2,
                    isEnabled = true,
                    extras = mapOf(
                        "ANTI_ANDROGEN_TYPE" to 1.0,
                        "AREA_CM2" to 750.0,
                        "CONCENTRATION_MG_ML" to 0.75,
                        "RELEASE_RATE_UG_PER_DAY" to 50.0,
                        "SUBLINGUAL_THETA" to 0.11,
                        "SUBLINGUAL_TIER" to 2.0
                    ),
                    createdAt = "2026-08-20T08:00:00Z"
                ),
                BackupMedicationPlanV1(
                    id = SECOND_PLAN_ID,
                    name = "Antiandrogen plan",
                    route = "ANTIANDROGEN",
                    ester = "E2",
                    doseMG = 1.0,
                    scheduleType = "CUSTOM",
                    daysOfWeek = emptyList(),
                    intervalDays = 1,
                    isEnabled = false,
                    extras = mapOf("ANTI_ANDROGEN_TYPE" to 2.0),
                    createdAt = "2026-08-21T09:30:00Z"
                )
            ),
            scheduledDoseSlots = listOf(
                BackupScheduledDoseSlotV1(SLOT_ID, PLAN_ID, "08:00", 0),
                BackupScheduledDoseSlotV1(SECOND_SLOT_ID, PLAN_ID, "20:00", 1),
                BackupScheduledDoseSlotV1(SECOND_PLAN_SLOT_ID, SECOND_PLAN_ID, "07:30", 0)
            ),
            doseEvents = listOf(
                BackupDoseEventV1(
                    id = EVENT_ID,
                    route = "PATCH_APPLY",
                    occurredAt = "2026-08-22T10:15:00Z",
                    zoneId = "Asia/Shanghai",
                    localDate = "2026-08-22",
                    doseMG = 0.75,
                    ester = "E2",
                    extras = mapOf("AREA_CM2" to 750.0),
                    slotId = SLOT_ID,
                    source = "MANUAL",
                    status = "RECORDED",
                    revision = 3L
                ),
                BackupDoseEventV1(
                    id = SECOND_EVENT_ID,
                    route = "PATCH_REMOVE",
                    occurredAt = "2026-08-22T18:15:00Z",
                    zoneId = null,
                    localDate = null,
                    doseMG = 0.0,
                    ester = "E2",
                    extras = emptyMap(),
                    slotId = null,
                    source = "WIDGET",
                    status = "RECORDED",
                    revision = 1L
                )
            ),
            settings = BackupSettingsV1(
                bodyWeightKg = 55.0,
                themeMode = "DARK",
                colorTheme = "BUILTIN",
                autoCheckUpdates = false,
                timeFormat = "HOUR_24"
            )
        )

    private fun requireEncoded(
        payload: EvoluneBackupPayloadV1,
        codec: EvoluneBackupCodec = EvoluneBackupCodec(),
        kdfIterations: Int = 100_000
    ): ByteArray = when (val result = codec.encode(payload, passphrase, metadata, kdfIterations)) {
        is BackupEncodeResult.Success -> result.bytes
        is BackupEncodeResult.Failure -> error("encode failed: ${result.error}")
    }

    private fun requireDecoded(bytes: ByteArray): ValidatedEvoluneBackupPayloadV1 =
        when (val result = EvoluneBackupCodec().decodeAndValidate(bytes, passphrase)) {
            is BackupDecodeResult.Success -> result.payload
            is BackupDecodeResult.Failure -> error("decode failed: ${result.error}")
        }

    private fun assertFailure(result: BackupDecodeResult, expected: BackupCodecErrorCode) {
        val failure = result as? BackupDecodeResult.Failure
            ?: throw AssertionError("expected failure but got $result")
        assertEquals(expected, failure.error.code)
    }

    private fun assertInvalid(result: BackupValidationResult) {
        val invalid = result as? BackupValidationResult.Invalid
            ?: throw AssertionError("expected invalid payload but got $result")
        assertEquals(BackupCodecErrorCode.INVALID_PAYLOAD, invalid.error.code)
    }

    private fun envelopeField(bytes: ByteArray, field: String): String =
        json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8))
            .jsonObject.getValue(field).jsonPrimitive.content

    private fun replaceEnvelopeField(
        bytes: ByteArray,
        field: String,
        value: JsonElement
    ): ByteArray = rewriteEnvelope(bytes) { root ->
        buildJsonObject {
            root.forEach { (key, current) ->
                put(key, if (key == field) value else current)
            }
        }
    }

    private fun removeEnvelopeField(bytes: ByteArray, field: String): ByteArray =
        rewriteEnvelope(bytes) { root ->
            buildJsonObject {
                root.forEach { (key, value) -> if (key != field) put(key, value) }
            }
        }

    private fun addEnvelopeField(
        bytes: ByteArray,
        field: String,
        value: JsonElement
    ): ByteArray = rewriteEnvelope(bytes) { root ->
        buildJsonObject {
            root.forEach { (key, current) -> put(key, current) }
            put(field, value)
        }
    }

    private fun rewriteEnvelope(
        bytes: ByteArray,
        transform: (JsonObject) -> JsonObject
    ): ByteArray {
        val root = json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
        return json.encodeToString(JsonElement.serializer(), transform(root))
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun replaceCiphertextWithPayload(bytes: ByteArray, payload: String): ByteArray {
        val root = json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
        val salt = Base64.getDecoder().decode(root.getValue("salt").jsonPrimitive.content)
        val nonce = Base64.getDecoder().decode(root.getValue("nonce").jsonPrimitive.content)
        val iterations = root.getValue("kdfIterations").jsonPrimitive.intOrNull
            ?: error("missing kdfIterations")
        val keySpec = PBEKeySpec(passphrase, salt, iterations, EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(keySpec)
            .encoded
        keySpec.clearPassword()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(EvoluneBackupFormat.GCM_TAG_BITS, nonce)
        )
        cipher.updateAAD(authenticatedHeaderJson(root).toByteArray(StandardCharsets.UTF_8))
        val ciphertext = cipher.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        keyBytes.fill(0)
        return replaceEnvelopeField(
            bytes,
            "ciphertext",
            JsonPrimitive(Base64.getEncoder().encodeToString(ciphertext))
        )
    }

    private fun authenticatedHeaderJson(root: JsonObject): String =
        json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                listOf(
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
                    "nonce"
                ).forEach { put(it, root.getValue(it)) }
            }
        )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    private class FixedRandomSource : BackupRandomSource {
        override fun nextBytes(length: Int): ByteArray = when (length) {
            EvoluneBackupFormat.SALT_BYTES -> ByteArray(length) { (it + 1).toByte() }
            EvoluneBackupFormat.NONCE_BYTES -> ByteArray(length) { (it + 33).toByte() }
            else -> error("unexpected random length: $length")
        }
    }

    private companion object {
        const val PLAN_ID = "11111111-1111-4111-8111-111111111111"
        const val SECOND_PLAN_ID = "44444444-4444-4444-8444-444444444444"
        const val ORPHAN_PLAN_ID = "88888888-8888-4888-8888-888888888888"
        const val SLOT_ID = "22222222-2222-4222-8222-222222222222"
        const val SECOND_SLOT_ID = "33333333-3333-4333-8333-333333333333"
        const val SECOND_PLAN_SLOT_ID = "55555555-5555-4555-8555-555555555555"
        const val EVENT_ID = "66666666-6666-4666-8666-666666666666"
        const val SECOND_EVENT_ID = "77777777-7777-4777-8777-777777777777"
    }
}
