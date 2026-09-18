package io.github.yingqiu0871.evolune.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * v1.7.2 Slice B test support: re-encrypts a crafted payload into an existing envelope (the
 * envelope format and its authenticated header are untouched). Used by tests that must present
 * structurally valid but semantically invalid payloads to the RESTORE path.
 *
 * The AAD reconstruction mirrors the codec's canonical authenticated header exactly (same key
 * order, same default [Json] configuration).
 */
internal object BackupTamperTestSupport {

    private val json = Json

    private val AUTH_HEADER_FIELDS = listOf(
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
    )

    fun replaceCiphertextWithPayload(
        bytes: ByteArray,
        passphrase: CharArray,
        payload: String
    ): ByteArray {
        val root = json.parseToJsonElement(String(bytes, StandardCharsets.UTF_8)).jsonObject
        val salt = Base64.getDecoder().decode(root.getValue("salt").jsonPrimitive.content)
        val nonce = Base64.getDecoder().decode(root.getValue("nonce").jsonPrimitive.content)
        val iterations = root.getValue("kdfIterations").jsonPrimitive.intOrNull
            ?: error("missing kdfIterations")
        val keySpec = PBEKeySpec(
            passphrase,
            salt,
            iterations,
            EvoluneBackupFormat.DERIVED_KEY_LENGTH_BITS
        )
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
        val replaced = JsonObject(
            root.mapValues { (key, value) ->
                if (key == "ciphertext") {
                    JsonPrimitive(Base64.getEncoder().encodeToString(ciphertext))
                } else {
                    value
                }
            }
        )
        return json.encodeToString(JsonElement.serializer(), replaced)
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun authenticatedHeaderJson(root: JsonObject): String =
        json.encodeToString(
            JsonElement.serializer(),
            JsonObject(AUTH_HEADER_FIELDS.associateWith { root.getValue(it) })
        )
}
