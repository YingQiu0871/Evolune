package io.github.yingqiu0871.evolune.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.7.2 Slice A — characterization golden for the Widget preset RESOLVED TOKEN OUTPUT.
 *
 * Every expected value is an independent transcription of the resolved output of the released
 * v1.7.1 implementation (probe captured BEFORE the shared-palette extraction), not a value
 * derived from the shared catalog at test time. The test must pass unchanged before AND after
 * extraction; any mismatch after extraction blocks the refactor.
 */
class WidgetPaletteTokenGoldenTest {

    private data class Expected(
        val surface: Long,
        val onSurface: Long,
        val onSurfaceVariant: Long,
        val primary: Long,
        val secondary: Long,
        val tertiary: Long,
        val primaryContainer: Long,
        val onPrimaryContainer: Long,
        val progressTrack: Long,
        val error: Long,
        val primaryForeground: Long
    )

    private fun expected(scheme: String, dark: Boolean): Expected = when (scheme to dark) {
        "MONET_BLUE" to false -> Expected(0xFFF8F9FF, 0xFF1F1D20, 0xFF1F1D20, 0xFF3F5F90, 0xFF565F71, 0xFF705575, 0xFFD6E3FF, 0xFF0B1B33, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_BLUE" to true -> Expected(0xFF111318, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFA8C7FA, 0xFFBEC6DC, 0xFFDDBCE0, 0xFF284777, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_VIOLET" to false -> Expected(0xFFFBF8FF, 0xFF1F1D20, 0xFF1F1D20, 0xFF70558F, 0xFF665A70, 0xFF815343, 0xFFEEDBFF, 0xFF1F1D20, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_VIOLET" to true -> Expected(0xFF151217, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFDDB8FF, 0xFFD1C0D8, 0xFFF5B9A5, 0xFF573D74, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_SAKURA" to false -> Expected(0xFFFFF8F9, 0xFF1F1D20, 0xFF1F1D20, 0xFF9A405D, 0xFF75565F, 0xFF775930, 0xFFFFD9E2, 0xFF3E001D, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_SAKURA" to true -> Expected(0xFF181113, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFFFB1C5, 0xFFE5BDC6, 0xFFE7C086, 0xFF7D2947, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_MINT" to false -> Expected(0xFFF6FCF7, 0xFF1F1D20, 0xFF1F1D20, 0xFF356A4E, 0xFF506355, 0xFF3F6374, 0xFFB8F2CE, 0xFF002112, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_MINT" to true -> Expected(0xFF0E1511, 0xFFF2EFF4, 0xFFF2EFF4, 0xFF9DD6B3, 0xFFB7CCBC, 0xFFA6CDDF, 0xFF1D5138, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_TEAL" to false -> Expected(0xFFF4FBF9, 0xFF1F1D20, 0xFF1F1D20, 0xFF006A64, 0xFF4A6360, 0xFF4A607C, 0xFF9DF2E9, 0xFF00201E, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_TEAL" to true -> Expected(0xFF0E1514, 0xFFF2EFF4, 0xFFF2EFF4, 0xFF81D5CD, 0xFFB0CCC8, 0xFFB2C8E8, 0xFF00504B, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_AMBER" to false -> Expected(0xFFFFF9F0, 0xFF1F1D20, 0xFF1F1D20, 0xFF805600, 0xFF705D3E, 0xFF53643C, 0xFFFFDEA5, 0xFF291800, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_AMBER" to true -> Expected(0xFF18130B, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFF6BD6C, 0xFFDEC6A1, 0xFFBACD97, 0xFF614000, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_NEUTRAL" to false -> Expected(0xFFFAF9FC, 0xFF1F1D20, 0xFF1F1D20, 0xFF5F5E65, 0xFF616066, 0xFF605D6E, 0xFFE5E1E9, 0xFF1B1B1F, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_NEUTRAL" to true -> Expected(0xFF141316, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFC9C5CD, 0xFFCBC5CD, 0xFFCAC3DB, 0xFF47464D, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        "MONET_LAVENDER" to false -> Expected(0xFFFCF8FF, 0xFF1F1D20, 0xFF1F1D20, 0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFEADDFF, 0xFF21005D, 0xFFE1DAE2, 0xFF1F1D20, 0xFF1F1D20)
        "MONET_LAVENDER" to true -> Expected(0xFF141218, 0xFFF2EFF4, 0xFFF2EFF4, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFF4F378B, 0xFFFFFFFF, 0xFF4B4650, 0xFFF2EFF4, 0xFFF2EFF4)
        else -> error("no golden for $scheme dark=$dark")
    }

    @Test
    fun `every preset resolves to the frozen v1_7_1 token output in both modes`() {
        WidgetColorScheme.entries
            .filter { it != WidgetColorScheme.MATERIAL_YOU_AUTO }
            .forEach { scheme ->
                listOf(false, true).forEach { dark ->
                    val palette = WidgetPaletteResolver.resolve(
                        WidgetAppearanceConfig(colorScheme = scheme),
                        dark,
                        dynamicColor = { null }
                    )
                    val mode = if (dark) "dark" else "light"
                    val gold = expected(scheme.name, dark)
                    fun check(field: String, actual: Int, want: Long) = assertEquals(
                        "$scheme/$mode/$field",
                        want,
                        actual.toLong() and 0xFFFFFFFFL
                    )
                    check("surface", palette.surface, gold.surface)
                    check("onSurface", palette.onSurface, gold.onSurface)
                    check("onSurfaceVariant", palette.onSurfaceVariant, gold.onSurfaceVariant)
                    check("primary", palette.primary, gold.primary)
                    check("secondary", palette.secondary, gold.secondary)
                    check("tertiary", palette.tertiary, gold.tertiary)
                    check("primaryContainer", palette.primaryContainer, gold.primaryContainer)
                    check("onPrimaryContainer", palette.onPrimaryContainer, gold.onPrimaryContainer)
                    check("progressTrack", palette.progressTrack, gold.progressTrack)
                    check("error", palette.error, gold.error)
                    check("primaryForeground", palette.primaryForeground, gold.primaryForeground)
                }
            }
    }

    @Test
    fun `material you auto is not a preset and resolves through the dynamic path`() {
        val fallback = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(
                colorScheme = WidgetColorScheme.MATERIAL_YOU_AUTO,
                themeMode = WidgetThemeMode.LIGHT
            ),
            dark = false,
            dynamicColor = { null }
        )
        // Without system color overrides the AUTO path falls back to the MONET_TEAL preset.
        val teal = WidgetPaletteResolver.resolve(
            WidgetAppearanceConfig(colorScheme = WidgetColorScheme.MONET_TEAL),
            dark = false,
            dynamicColor = { null }
        )
        assertEquals(teal, fallback)
    }
}
