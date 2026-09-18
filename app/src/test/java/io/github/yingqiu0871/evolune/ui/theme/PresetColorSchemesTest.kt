package io.github.yingqiu0871.evolune.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.yingqiu0871.evolune.theme.palette.PaletteCatalog
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 Slice B — App Material preset schemes.
 *
 * Every preset builds a coherent light + dark scheme whose palette roles come from the shared
 * catalog (no duplicated seed tables), with readable on-colors and the frozen §13 mapping.
 */
class PresetColorSchemesTest {

    private fun assertCatalogRoles(scheme: ColorScheme, preset: PresetPalette, dark: Boolean) {
        val seed = PaletteCatalog.seed(preset)
        val mode = if (dark) "dark" else "light"
        assertEquals("$preset/$mode primary", Color(if (dark) seed.darkPrimary else seed.lightPrimary), scheme.primary)
        assertEquals("$preset/$mode secondary", Color(if (dark) seed.darkSecondary else seed.lightSecondary), scheme.secondary)
        assertEquals("$preset/$mode tertiary", Color(if (dark) seed.darkTertiary else seed.lightTertiary), scheme.tertiary)
        assertEquals("$preset/$mode primaryContainer", Color(if (dark) seed.darkContainer else seed.lightContainer), scheme.primaryContainer)
        assertEquals("$preset/$mode surface", Color(if (dark) seed.darkSurface else seed.lightSurface), scheme.surface)
        assertEquals("$preset/$mode background", Color(if (dark) seed.darkSurface else seed.lightSurface), scheme.background)
        if (!dark) {
            assertEquals("$preset/$mode onPrimaryContainer", Color(seed.lightOnContainer), scheme.onPrimaryContainer)
        } else {
            assertEquals("$preset/$mode onPrimaryContainer", Color.White, scheme.onPrimaryContainer)
        }
    }

    private fun assertReadable(scheme: ColorScheme, preset: PresetPalette, dark: Boolean) {
        val mode = if (dark) "dark" else "light"
        listOf(
            "primary" to (scheme.primary to scheme.onPrimary),
            "secondary" to (scheme.secondary to scheme.onSecondary),
            "tertiary" to (scheme.tertiary to scheme.onTertiary),
            "secondaryContainer" to (scheme.secondaryContainer to scheme.onSecondaryContainer),
            "tertiaryContainer" to (scheme.tertiaryContainer to scheme.onTertiaryContainer),
            "surface" to (scheme.surface to scheme.onSurface),
            "surfaceVariant" to (scheme.surfaceVariant to scheme.onSurfaceVariant)
        ).forEach { (name, pair) ->
            assertTrue(
                "$preset/$mode $name contrast must be readable (was ${contrastRatio(pair.first, pair.second)})",
                contrastRatio(pair.first, pair.second) >= 4.5
            )
        }
    }

    @Test
    fun `every preset builds light and dark schemes from the shared catalog`() {
        PresetPalette.entries.forEach { preset ->
            val light = presetLightScheme(preset)
            val dark = presetDarkScheme(preset)
            assertCatalogRoles(light, preset, dark = false)
            assertCatalogRoles(dark, preset, dark = true)
            assertReadable(light, preset, dark = false)
            assertReadable(dark, preset, dark = true)
        }
    }

    @Test
    fun `preset schemes differ across palettes in their core roles`() {
        val lightPrimaries = PresetPalette.entries.map { presetLightScheme(it).primary }
        assertEquals("preset primaries must be distinct", PresetPalette.entries.size, lightPrimaries.distinct().size)
        val darkPrimaries = PresetPalette.entries.map { presetDarkScheme(it).primary }
        assertEquals(PresetPalette.entries.size, darkPrimaries.distinct().size)
        assertNotEquals(presetLightScheme(PresetPalette.MONET_BLUE), presetLightScheme(PresetPalette.MONET_TEAL))
    }

    @Test
    fun `light and dark schemes for the same preset are distinct`() {
        PresetPalette.entries.forEach { preset ->
            assertNotEquals(
                "preset $preset must not render identical light/dark schemes",
                presetLightScheme(preset).primary,
                presetDarkScheme(preset).primary
            )
        }
    }
}
