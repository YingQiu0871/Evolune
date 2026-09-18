package io.github.yingqiu0871.evolune.backup

import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 Slice B — backup schema v2 theme identity.
 *
 * Covers: canonical v2 serialization, strict exact field sets, the full valid/invalid matrix,
 * frozen schema-v1 compatibility, the intentional old-reader rejection, and the B2
 * settings↔UserSettings mappings.
 */
class BackupThemeSchemaV2Test {

    private val json = Json
    private val passphrase = "secret".toCharArray()

    // ---------- round-trip: every canonical identity ----------

    @Test
    fun `every canonical theme identity round-trips through schema v2`() {
        val identities = listOf(
            ThemeColorSource.DYNAMIC to null,
            ThemeColorSource.PRESET to ThemePresetSelection.LegacyBuiltin
        ) + PresetPalette.entries.map { ThemeColorSource.PRESET to ThemePresetSelection.Preset(it) }

        identities.forEach { (source, preset) ->
            val settings = settingsFor(source, preset)
            val encoded = requireEncoded(payloadWith(settings))
            val decoded = requireDecoded(encoded)

            assertEquals("source=$source preset=$preset", settings, decoded.settings)
            assertEquals(
                "legacy projection for source=$source",
                if (source == ThemeColorSource.DYNAMIC) "DYNAMIC" else "BUILTIN",
                decoded.settings.colorTheme
            )
        }
    }

    @Test
    fun `a legacy-shaped payload without canonical fields is canonicalized deterministically`() {
        val dynamic = requireDecoded(requireEncoded(payloadWith(legacySettings("DYNAMIC"))))
        assertEquals("DYNAMIC", dynamic.settings.themeColorSource)
        assertNull(dynamic.settings.themePresetId)
        assertEquals("DYNAMIC", dynamic.settings.colorTheme)

        val builtin = requireDecoded(requireEncoded(payloadWith(legacySettings("BUILTIN"))))
        assertEquals("PRESET", builtin.settings.themeColorSource)
        assertEquals("LEGACY_BUILTIN", builtin.settings.themePresetId)
        assertEquals("BUILTIN", builtin.settings.colorTheme)
    }

    // ---------- schema v2: strict matrix via crafted raw payloads ----------

    @Test
    fun `valid crafted schema v2 payloads decode`() {
        listOf(
            v2Settings("DYNAMIC", "null"),
            v2Settings("PRESET", "\"LEGACY_BUILTIN\""),
            v2Settings("PRESET", "\"MONET_BLUE\""),
            v2Settings("PRESET", "\"MONET_LAVENDER\"")
        ).forEach { settings ->
            val result = decodeCrafted(payloadJson(2, settings))
            assertTrue("settings=$settings -> $result", result is BackupDecodeResult.Success)
        }
    }

    @Test
    fun `schema v2 invalid theme fields fail with INVALID_PAYLOAD and no fallback`() {
        val invalidSettings = listOf(
            // missing themePresetId key
            "{\"bodyWeightKg\":55.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"BUILTIN\"," +
                "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\",\"themeColorSource\":\"PRESET\"}",
            // missing themeColorSource key
            "{\"bodyWeightKg\":55.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"BUILTIN\"," +
                "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\",\"themePresetId\":null}",
            // extra settings key
            "{\"bodyWeightKg\":55.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"BUILTIN\"," +
                "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\",\"themeColorSource\":\"DYNAMIC\"," +
                "\"themePresetId\":null,\"unexpected\":true}",
            // DYNAMIC + non-null preset
            v2Settings("DYNAMIC", "\"MONET_BLUE\""),
            // PRESET + null
            v2Settings("PRESET", "null"),
            // unknown source
            v2Settings("AUTO", "null"),
            // unknown preset
            v2Settings("PRESET", "\"NOT_A_PRESET\"")
        )

        invalidSettings.forEach { settings ->
            val result = decodeCrafted(payloadJson(2, settings))
            val failure = result as? BackupDecodeResult.Failure
                ?: throw AssertionError("expected INVALID_PAYLOAD for $settings but got $result")
            assertEquals(
                "settings=$settings",
                BackupCodecErrorCode.INVALID_PAYLOAD,
                failure.error.code
            )
        }
    }

    // ---------- schema v1: frozen ----------

    @Test
    fun `schema v1 payloads still decode with null canonical fields`() {
        val decoded = asSuccess(
            decodeCrafted(payloadJson(1, legacySettingsJson("BUILTIN")), envelopeVersion = 1)
        )
        assertNull(decoded.settings.themeColorSource)
        assertNull(decoded.settings.themePresetId)
        assertEquals("BUILTIN", decoded.settings.colorTheme)

        val mapped = decoded.settings.toUserSettings()
        assertEquals(ThemeColorSource.PRESET, mapped.themeColorSource)
        assertEquals(ThemePresetSelection.LegacyBuiltin, mapped.themePreset)
    }

    @Test
    fun `schema v1 with an added v2 key stays rejected with the frozen classification`() {
        // The frozen v1 parser rejects any extra settings key; the released v1.7.1 classification
        // for that exact-shape violation was MALFORMED_PAYLOAD and stays unchanged.
        val result = decodeCrafted(
            payloadJson(1, legacySettingsJson("DYNAMIC", extraFields = "\"themeColorSource\":\"DYNAMIC\",")),
            envelopeVersion = 1
        )
        val failure = result as? BackupDecodeResult.Failure
            ?: throw AssertionError("expected rejection but got $result")
        assertEquals(BackupCodecErrorCode.MALFORMED_PAYLOAD, failure.error.code)
    }

    @Test
    fun `envelope and payload schema versions must match and be supported`() {
        // Crafted envelope v1 with a v2 payload -> mismatch -> UNSUPPORTED.
        val mismatched = decodeCrafted(payloadJson(2, v2Settings("DYNAMIC", "null")), envelopeVersion = 1)
        val failure = mismatched as? BackupDecodeResult.Failure
            ?: throw AssertionError("expected mismatch failure but got $mismatched")
        assertEquals(BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION, failure.error.code)
    }

    // ---------- old reader (source-backed fixture, NOT an executed old APK) ----------

    @Test
    fun `a schema v1 reader deterministically rejects a v2 backup before mutation`() {
        val encoded = requireEncoded(payloadWith(settingsFor(ThemeColorSource.DYNAMIC, null)))
        val envelopeVersion =
            json.parseToJsonElement(String(encoded, Charsets.UTF_8))
                .jsonObject.getValue("payloadSchemaVersion").jsonPrimitive.content.toInt()
        assertEquals(EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION, envelopeVersion)

        // Frozen replica of the released v1.7.1 version gate: only schema 1 was readable.
        fun releasedV1ReaderGate(payloadSchemaVersion: Int): BackupCodecErrorCode? =
            if (payloadSchemaVersion != 1) BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION else null

        assertEquals(
            BackupCodecErrorCode.UNSUPPORTED_PAYLOAD_VERSION,
            releasedV1ReaderGate(envelopeVersion)
        )
    }

    // ---------- B2 settings mappings ----------

    @Test
    fun `B2 settings to UserSettings derives the canonical state for v1 and v2`() {
        val v2Dynamic = settingsFor(ThemeColorSource.DYNAMIC, null).toUserSettings()
        assertEquals(ThemeColorSource.DYNAMIC, v2Dynamic.themeColorSource)
        assertNull(v2Dynamic.themePreset)
        assertEquals(ColorTheme.DYNAMIC, v2Dynamic.colorTheme)

        PresetPalette.entries.forEach { palette ->
            val mapped = settingsFor(
                ThemeColorSource.PRESET,
                ThemePresetSelection.Preset(palette)
            ).toUserSettings()
            assertEquals(ThemeColorSource.PRESET, mapped.themeColorSource)
            assertEquals(ThemePresetSelection.Preset(palette), mapped.themePreset)
            assertEquals(ColorTheme.BUILTIN, mapped.colorTheme)
        }

        val legacyBuiltin = settingsFor(
            ThemeColorSource.PRESET,
            ThemePresetSelection.LegacyBuiltin
        ).toUserSettings()
        assertEquals(ThemePresetSelection.LegacyBuiltin, legacyBuiltin.themePreset)

        // v1 payload (no canonical fields): BUILTIN -> LEGACY_BUILTIN, DYNAMIC -> DYNAMIC.
        val v1Builtin = legacySettings("BUILTIN").toUserSettings()
        assertEquals(ThemeColorSource.PRESET, v1Builtin.themeColorSource)
        assertEquals(ThemePresetSelection.LegacyBuiltin, v1Builtin.themePreset)
        val v1Dynamic = legacySettings("DYNAMIC").toUserSettings()
        assertEquals(ThemeColorSource.DYNAMIC, v1Dynamic.themeColorSource)
        assertNull(v1Dynamic.themePreset)
    }

    @Test
    fun `UserSettings toBackupSettings writes the canonical identity and the legacy projection`() {
        val dynamic = UserSettings(themeColorSource = ThemeColorSource.DYNAMIC)
        val dynamicBackup = dynamic.toBackupSettings()
        assertEquals("DYNAMIC", dynamicBackup.themeColorSource)
        assertNull(dynamicBackup.themePresetId)
        assertEquals("DYNAMIC", dynamicBackup.colorTheme)

        PresetPalette.entries.forEach { palette ->
            val backup = UserSettings(
                themeColorSource = ThemeColorSource.PRESET,
                themePreset = ThemePresetSelection.Preset(palette)
            ).toBackupSettings()
            assertEquals("PRESET", backup.themeColorSource)
            assertEquals(palette.name, backup.themePresetId)
            assertEquals("BUILTIN", backup.colorTheme)
        }

        val legacyBackup = UserSettings(
            themeColorSource = ThemeColorSource.PRESET,
            themePreset = ThemePresetSelection.LegacyBuiltin
        ).toBackupSettings()
        assertEquals("PRESET", legacyBackup.themeColorSource)
        assertEquals("LEGACY_BUILTIN", legacyBackup.themePresetId)
        assertEquals("BUILTIN", legacyBackup.colorTheme)
    }

    // ---------- helpers ----------

    private fun settingsFor(
        source: ThemeColorSource,
        preset: ThemePresetSelection?
    ): BackupSettingsV1 = BackupSettingsV1(
        bodyWeightKg = 55.0,
        themeMode = "SYSTEM",
        colorTheme = if (source == ThemeColorSource.DYNAMIC) "DYNAMIC" else "BUILTIN",
        autoCheckUpdates = true,
        timeFormat = "SYSTEM",
        themeColorSource = source.name,
        themePresetId = preset?.persistedId
    )

    private fun legacySettings(colorTheme: String): BackupSettingsV1 = BackupSettingsV1(
        bodyWeightKg = 55.0,
        themeMode = "SYSTEM",
        colorTheme = colorTheme,
        autoCheckUpdates = true,
        timeFormat = "SYSTEM"
    )

    private fun payloadWith(settings: BackupSettingsV1): EvoluneBackupPayloadV1 =
        EvoluneBackupPayloadV1(
            medicationPlans = emptyList(),
            scheduledDoseSlots = emptyList(),
            doseEvents = emptyList(),
            settings = settings
        )

    private fun requireEncoded(payload: EvoluneBackupPayloadV1): ByteArray =
        when (
            val result = EvoluneBackupCodec().encode(
                payload,
                passphrase.copyOf(),
                BackupProducerMetadataV1(
                    createdAt = "2026-08-23T22:10:00Z",
                    producerAppVersionName = "test",
                    producerAppVersionCode = 1
                ),
                100_000
            )
        ) {
            is BackupEncodeResult.Success -> result.bytes
            is BackupEncodeResult.Failure -> error("encode failed: ${result.error}")
        }

    private fun requireDecoded(bytes: ByteArray): EvoluneBackupPayloadV1 =
        asSuccess(EvoluneBackupCodec().decodeAndValidate(bytes, passphrase.copyOf()))

    private fun asSuccess(result: BackupDecodeResult): EvoluneBackupPayloadV1 =
        when (result) {
            is BackupDecodeResult.Success -> result.payload.payload
            is BackupDecodeResult.Failure -> error("decode failed: ${result.error}")
        }

    /** Re-encrypts a crafted payload into the carrier envelope (helper keeps content honest). */
    private fun decodeCrafted(payload: String, envelopeVersion: Int = 2): BackupDecodeResult {
        var carrier = requireEncoded(payloadWith(settingsFor(ThemeColorSource.DYNAMIC, null)))
        if (envelopeVersion != EvoluneBackupFormat.PAYLOAD_SCHEMA_VERSION) {
            carrier = replaceEnvelopeField(
                carrier,
                "payloadSchemaVersion",
                JsonPrimitive(envelopeVersion)
            )
        }
        return EvoluneBackupCodec().decodeAndValidate(
            BackupTamperTestSupport.replaceCiphertextWithPayload(
                carrier,
                passphrase.copyOf(),
                payload
            ),
            passphrase.copyOf()
        )
    }

    private fun replaceEnvelopeField(bytes: ByteArray, field: String, value: JsonElement): ByteArray {
        val root = json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
        val replaced = JsonObject(
            root.mapValues { (key, current) -> if (key == field) value else current }
        )
        return json.encodeToString(JsonElement.serializer(), replaced).toByteArray(Charsets.UTF_8)
    }

    private fun payloadJson(payloadVersion: Int, settings: String): String = buildString {
        append("{\"payloadSchemaVersion\":").append(payloadVersion).append(',')
        append("\"medicationPlans\":[],\"scheduledDoseSlots\":[],\"doseEvents\":[],")
        append("\"settings\":").append(settings).append('}')
    }

    private fun v2Settings(source: String, presetJson: String): String =
        "{\"bodyWeightKg\":55.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"BUILTIN\"," +
            "\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\"," +
            "\"themeColorSource\":\"$source\",\"themePresetId\":$presetJson}"

    private fun legacySettingsJson(colorTheme: String, extraFields: String = ""): String =
        "{\"bodyWeightKg\":55.0,\"themeMode\":\"SYSTEM\",\"colorTheme\":\"$colorTheme\"," +
            "${extraFields}\"autoCheckUpdates\":true,\"timeFormat\":\"SYSTEM\"}"
}
