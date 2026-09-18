package io.github.yingqiu0871.evolune.data

import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v1.7.2 Slice B — deterministic runtime theme-state derivation (local DataStore, NOT backup).
 *
 * Matrix under test:
 * - legacy storage without new keys: DYNAMIC -> DYNAMIC; BUILTIN -> PRESET + LEGACY_BUILTIN
 * - canonical DYNAMIC ignores stale preset ids
 * - canonical PRESET uses a valid preset id; otherwise the deterministic ladder
 *   (legacy BUILTIN -> LEGACY_BUILTIN, else MONET_TEAL)
 * - malformed local values never crash and fall back deterministically
 */
class ThemeStateDerivationTest {

    @Test
    fun `legacy dynamic storage derives canonical dynamic`() {
        val derived = deriveThemeState(
            storedSource = null,
            storedPresetId = null,
            legacyColorTheme = "DYNAMIC"
        )
        assertEquals(ThemeColorSource.DYNAMIC, derived.source)
        assertNull(derived.preset)
    }

    @Test
    fun `legacy builtin storage derives preset legacy builtin and never monet teal`() {
        val derived = deriveThemeState(
            storedSource = null,
            storedPresetId = null,
            legacyColorTheme = "BUILTIN"
        )
        assertEquals(ThemeColorSource.PRESET, derived.source)
        assertEquals(ThemePresetSelection.LegacyBuiltin, derived.preset)
    }

    @Test
    fun `missing or malformed legacy storage derives dynamic`() {
        listOf(null, "", "MONET_TEAL", "UNKNOWN").forEach { legacy ->
            val derived = deriveThemeState(null, null, legacy)
            assertEquals("legacy=$legacy", ThemeColorSource.DYNAMIC, derived.source)
            assertNull("legacy=$legacy", derived.preset)
        }
    }

    @Test
    fun `canonical dynamic ignores a stale preset id`() {
        val derived = deriveThemeState(
            storedSource = "DYNAMIC",
            storedPresetId = "MONET_BLUE",
            legacyColorTheme = "BUILTIN"
        )
        assertEquals(ThemeColorSource.DYNAMIC, derived.source)
        assertNull(derived.preset)
    }

    @Test
    fun `canonical preset resolves every valid identity`() {
        PresetPalette.entries.forEach { palette ->
            val derived = deriveThemeState("PRESET", palette.name, "BUILTIN")
            assertEquals(ThemeColorSource.PRESET, derived.source)
            assertEquals(ThemePresetSelection.Preset(palette), derived.preset)
        }
        val legacy = deriveThemeState(
            "PRESET",
            ThemePresetSelection.LEGACY_BUILTIN_ID,
            "BUILTIN"
        )
        assertEquals(ThemePresetSelection.LegacyBuiltin, legacy.preset)
    }

    @Test
    fun `canonical preset with invalid id uses the deterministic fallback ladder`() {
        val withLegacyBuiltin = deriveThemeState("PRESET", "NOT_A_PRESET", "BUILTIN")
        assertEquals(ThemeColorSource.PRESET, withLegacyBuiltin.source)
        assertEquals(ThemePresetSelection.LegacyBuiltin, withLegacyBuiltin.preset)

        listOf(null, "", "NOT_A_PRESET", "DYNAMIC").forEach { invalidId ->
            val fallback = deriveThemeState("PRESET", invalidId, "DYNAMIC")
            assertEquals(ThemeColorSource.PRESET, fallback.source)
            assertEquals(
                "invalidId=$invalidId",
                ThemePresetSelection.Preset(PresetPalette.MONET_TEAL),
                fallback.preset
            )
        }
    }

    @Test
    fun `invalid source with builtin legacy derives the exact legacy compatibility state`() {
        val derived = deriveThemeState("NOT_A_SOURCE", "MONET_BLUE", "BUILTIN")
        assertEquals(ThemeColorSource.PRESET, derived.source)
        assertEquals(ThemePresetSelection.LegacyBuiltin, derived.preset)
    }

    @Test
    fun `legacy color theme projection mirrors the canonical source`() {
        assertEquals(ColorTheme.DYNAMIC, ThemeColorSource.DYNAMIC.toLegacyColorTheme())
        assertEquals(ColorTheme.BUILTIN, ThemeColorSource.PRESET.toLegacyColorTheme())
    }

    @Test
    fun `preset selection persistence ids are the stable strings`() {
        assertEquals("LEGACY_BUILTIN", ThemePresetSelection.LegacyBuiltin.persistedId)
        PresetPalette.entries.forEach { palette ->
            assertEquals(palette.name, ThemePresetSelection.Preset(palette).persistedId)
        }
        assertEquals(
            ThemePresetSelection.LegacyBuiltin,
            ThemePresetSelection.fromStored("LEGACY_BUILTIN")
        )
        assertEquals(
            ThemePresetSelection.Preset(PresetPalette.MONET_SAKURA),
            ThemePresetSelection.fromStored("MONET_SAKURA")
        )
        assertNull(ThemePresetSelection.fromStored("NOT_A_PRESET"))
        assertNull(ThemePresetSelection.fromStored(null))
    }
}
