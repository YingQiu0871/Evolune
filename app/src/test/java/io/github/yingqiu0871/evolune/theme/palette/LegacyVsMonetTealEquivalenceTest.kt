package io.github.yingqiu0871.evolune.theme.palette

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * v1.7.2 Slice A — PROVE/DISPROVE: the released v1.7.1 built-in App theme is NOT exactly equal
 * to the Material scheme that would be derived from MONET_TEAL.
 *
 * The derivation below is a test-local mirror of the approved contract §13 mapping. Slice B
 * moves the production derivation into ui/theme; this test exists to freeze the verdict of
 * Slice A: legacy BUILTIN must migrate to the LEGACY_BUILTIN compatibility identity, never to
 * MONET_TEAL.
 */
class LegacyVsMonetTealEquivalenceTest {

    private fun contrastOn(background: Long): Long =
        if (contrast(WHITE, background) >= contrast(BLACK, background)) WHITE else BLACK

    private fun contrast(fg: Long, bg: Long): Double {
        val lighter = maxOf(luminance(fg), luminance(bg))
        val darker = minOf(luminance(fg), luminance(bg))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun blend(base: Long, target: Long, amount: Double): Long {
        fun channel(shift: Int): Long {
            val start = (base shr shift) and 0xFF
            val end = (target shr shift) and 0xFF
            return (start + (end - start) * amount).roundToInt().toLong()
        }
        return 0xFF000000L or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun derivedMonetTealRoles(dark: Boolean): Map<LegacyThemeRole, Long> {
        val seed = PaletteCatalog.seed(PresetPalette.MONET_TEAL)
        val surface = if (dark) seed.darkSurface else seed.lightSurface
        val primary = if (dark) seed.darkPrimary else seed.lightPrimary
        val secondary = if (dark) seed.darkSecondary else seed.lightSecondary
        val tertiary = if (dark) seed.darkTertiary else seed.lightTertiary
        val container = if (dark) seed.darkContainer else seed.lightContainer
        val onContainer = if (dark) 0xFFFFFFFF else seed.lightOnContainer
        val onSurface = if (dark) 0xFFF2EFF4 else 0xFF1F1D20
        val onSurfaceVariant = if (dark) 0xFFCDC5D3 else 0xFF4C464E
        val error = if (dark) 0xFFFFB4AB else 0xFFBA1A1A
        return mapOf(
            LegacyThemeRole.PRIMARY to primary,
            LegacyThemeRole.ON_PRIMARY to contrastOn(primary),
            LegacyThemeRole.PRIMARY_CONTAINER to container,
            LegacyThemeRole.ON_PRIMARY_CONTAINER to onContainer,
            LegacyThemeRole.SECONDARY to secondary,
            LegacyThemeRole.ON_SECONDARY to contrastOn(secondary),
            LegacyThemeRole.SECONDARY_CONTAINER to blend(surface, secondary, 0.12),
            LegacyThemeRole.ON_SECONDARY_CONTAINER to contrastOn(blend(surface, secondary, 0.12)),
            LegacyThemeRole.TERTIARY to tertiary,
            LegacyThemeRole.ON_TERTIARY to contrastOn(tertiary),
            LegacyThemeRole.TERTIARY_CONTAINER to blend(surface, tertiary, 0.12),
            LegacyThemeRole.ON_TERTIARY_CONTAINER to contrastOn(blend(surface, tertiary, 0.12)),
            LegacyThemeRole.ERROR to error,
            LegacyThemeRole.BACKGROUND to surface,
            LegacyThemeRole.SURFACE to surface,
            LegacyThemeRole.ON_SURFACE to onSurface,
            LegacyThemeRole.ON_SURFACE_VARIANT to onSurfaceVariant,
            LegacyThemeRole.SURFACE_VARIANT to blend(surface, primary, 0.08),
            LegacyThemeRole.OUTLINE to onSurfaceVariant,
            LegacyThemeRole.OUTLINE_VARIANT to blend(surface, onSurfaceVariant, 0.50)
        )
    }

    @Test
    fun `legacy built-in is not exactly equal to derived monet teal in either mode`() {
        listOf(false, true).forEach { dark ->
            val legacy = if (dark) LegacyBuiltinTheme.darkRoles else LegacyBuiltinTheme.lightRoles
            val derived = derivedMonetTealRoles(dark)
            val mode = if (dark) "dark" else "light"
            val mismatches = derived.filter { (role, value) -> legacy[role] != value }
            println("EQUIV|$mode|compared=${derived.size}|mismatches=${mismatches.size}")
            mismatches.forEach { (role, value) ->
                println("EQUIV|$mode|mismatch|${role.name}|legacy=${hex(legacy[role])}|derived=${hex(value)}")
            }
            assertTrue(
                "the derived $mode comparison must cover the shared role set",
                mismatches.size < derived.size
            )
            assertTrue(
                "legacy built-in vs derived MONET_TEAL must have at least one mismatch ($mode)",
                mismatches.isNotEmpty()
            )
            assertNotEquals(
                "legacy built-in must not equal the derived MONET_TEAL scheme ($mode)",
                derived,
                legacy.filterKeys { it in derived.keys }
            )
        }
    }

    @Test
    fun `the known dark tertiary mismatch is present`() {
        assertEquals(0xFFAFC9E7, LegacyBuiltinTheme.darkRoles.getValue(LegacyThemeRole.TERTIARY))
        assertEquals(0xFFB2C8E8, PaletteCatalog.seed(PresetPalette.MONET_TEAL).darkTertiary)
        assertNotEquals(
            LegacyBuiltinTheme.darkRoles.getValue(LegacyThemeRole.TERTIARY),
            PaletteCatalog.seed(PresetPalette.MONET_TEAL).darkTertiary
        )
        assertFalse(
            "legacy built-in must never be auto-mapped to MONET_TEAL",
            LegacyBuiltinTheme.darkRoles == derivedMonetTealRoles(dark = true)
        )
    }

    private fun hex(value: Long?): String =
        if (value == null) "null" else "0x" + value.toUInt().toString(16).uppercase().padStart(8, '0')

    private companion object {
        const val BLACK = 0xFF000000L
        const val WHITE = 0xFFFFFFFFL
    }
}
