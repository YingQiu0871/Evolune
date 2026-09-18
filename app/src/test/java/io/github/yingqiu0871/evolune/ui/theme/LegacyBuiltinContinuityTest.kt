package io.github.yingqiu0871.evolune.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.yingqiu0871.evolune.theme.palette.LegacyBuiltinTheme
import io.github.yingqiu0871.evolune.theme.palette.LegacyThemeRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * v1.7.2 Slice B — LEGACY_BUILTIN continuity.
 *
 * Chain of proof:
 * 1. Slice A `LegacyBuiltinSnapshotTest` pins the shared compatibility role values against
 *    independent constants transcribed from the released v1.7.1 theme source;
 * 2. before this slice deleted the old inline scheme definitions, `legacyBuiltinLightScheme()`
 *    / `legacyBuiltinDarkScheme()` were verified role-by-role against the released
 *    `lightScheme` / `darkScheme` (including AMOLED) — recorded in the slice-b evidence;
 * 3. this committed test guards that every App scheme role is wired from the shared authority
 *    exactly (no approximation, no MONET_TEAL substitution).
 */
class LegacyBuiltinContinuityTest {

    private fun assertRolesFromMap(
        scheme: ColorScheme,
        roles: Map<LegacyThemeRole, Long>
    ) {
        fun expected(role: LegacyThemeRole): Color = Color(roles.getValue(role))
        assertEquals("primary", expected(LegacyThemeRole.PRIMARY), scheme.primary)
        assertEquals("onPrimary", expected(LegacyThemeRole.ON_PRIMARY), scheme.onPrimary)
        assertEquals("primaryContainer", expected(LegacyThemeRole.PRIMARY_CONTAINER), scheme.primaryContainer)
        assertEquals("onPrimaryContainer", expected(LegacyThemeRole.ON_PRIMARY_CONTAINER), scheme.onPrimaryContainer)
        assertEquals("inversePrimary", expected(LegacyThemeRole.INVERSE_PRIMARY), scheme.inversePrimary)
        assertEquals("secondary", expected(LegacyThemeRole.SECONDARY), scheme.secondary)
        assertEquals("onSecondary", expected(LegacyThemeRole.ON_SECONDARY), scheme.onSecondary)
        assertEquals("secondaryContainer", expected(LegacyThemeRole.SECONDARY_CONTAINER), scheme.secondaryContainer)
        assertEquals("onSecondaryContainer", expected(LegacyThemeRole.ON_SECONDARY_CONTAINER), scheme.onSecondaryContainer)
        assertEquals("tertiary", expected(LegacyThemeRole.TERTIARY), scheme.tertiary)
        assertEquals("onTertiary", expected(LegacyThemeRole.ON_TERTIARY), scheme.onTertiary)
        assertEquals("tertiaryContainer", expected(LegacyThemeRole.TERTIARY_CONTAINER), scheme.tertiaryContainer)
        assertEquals("onTertiaryContainer", expected(LegacyThemeRole.ON_TERTIARY_CONTAINER), scheme.onTertiaryContainer)
        assertEquals("background", expected(LegacyThemeRole.BACKGROUND), scheme.background)
        assertEquals("onBackground", expected(LegacyThemeRole.ON_BACKGROUND), scheme.onBackground)
        assertEquals("surface", expected(LegacyThemeRole.SURFACE), scheme.surface)
        assertEquals("onSurface", expected(LegacyThemeRole.ON_SURFACE), scheme.onSurface)
        assertEquals("surfaceVariant", expected(LegacyThemeRole.SURFACE_VARIANT), scheme.surfaceVariant)
        assertEquals("onSurfaceVariant", expected(LegacyThemeRole.ON_SURFACE_VARIANT), scheme.onSurfaceVariant)
        assertEquals("surfaceTint", expected(LegacyThemeRole.PRIMARY), scheme.surfaceTint)
        assertEquals("inverseSurface", expected(LegacyThemeRole.INVERSE_SURFACE), scheme.inverseSurface)
        assertEquals("inverseOnSurface", expected(LegacyThemeRole.INVERSE_ON_SURFACE), scheme.inverseOnSurface)
        assertEquals("error", expected(LegacyThemeRole.ERROR), scheme.error)
        assertEquals("onError", expected(LegacyThemeRole.ON_ERROR), scheme.onError)
        assertEquals("errorContainer", expected(LegacyThemeRole.ERROR_CONTAINER), scheme.errorContainer)
        assertEquals("onErrorContainer", expected(LegacyThemeRole.ON_ERROR_CONTAINER), scheme.onErrorContainer)
        assertEquals("outline", expected(LegacyThemeRole.OUTLINE), scheme.outline)
        assertEquals("outlineVariant", expected(LegacyThemeRole.OUTLINE_VARIANT), scheme.outlineVariant)
        assertEquals("scrim", expected(LegacyThemeRole.SCRIM), scheme.scrim)
        assertEquals("surfaceBright", expected(LegacyThemeRole.SURFACE_BRIGHT), scheme.surfaceBright)
        assertEquals("surfaceDim", expected(LegacyThemeRole.SURFACE_DIM), scheme.surfaceDim)
        assertEquals("surfaceContainer", expected(LegacyThemeRole.SURFACE_CONTAINER), scheme.surfaceContainer)
        assertEquals("surfaceContainerHigh", expected(LegacyThemeRole.SURFACE_CONTAINER_HIGH), scheme.surfaceContainerHigh)
        assertEquals("surfaceContainerHighest", expected(LegacyThemeRole.SURFACE_CONTAINER_HIGHEST), scheme.surfaceContainerHighest)
        assertEquals("surfaceContainerLow", expected(LegacyThemeRole.SURFACE_CONTAINER_LOW), scheme.surfaceContainerLow)
        assertEquals("surfaceContainerLowest", expected(LegacyThemeRole.SURFACE_CONTAINER_LOWEST), scheme.surfaceContainerLowest)
    }

    @Test
    fun `legacy builtin light scheme is wired from the shared compatibility authority`() {
        assertRolesFromMap(legacyBuiltinLightScheme(), LegacyBuiltinTheme.lightRoles)
    }

    @Test
    fun `legacy builtin dark scheme is wired from the shared compatibility authority`() {
        assertRolesFromMap(legacyBuiltinDarkScheme(), LegacyBuiltinTheme.darkRoles)
    }

    @Test
    fun `legacy builtin amoled applies the exact frozen surface overrides`() {
        val amoled = legacyBuiltinDarkScheme().withAmoledSurfaces()
        LegacyBuiltinTheme.amoledSurfaceOverrides.forEach { (role, value) ->
            val actual = when (role) {
                LegacyThemeRole.BACKGROUND -> amoled.background
                LegacyThemeRole.SURFACE -> amoled.surface
                LegacyThemeRole.SURFACE_DIM -> amoled.surfaceDim
                LegacyThemeRole.SURFACE_BRIGHT -> amoled.surfaceBright
                LegacyThemeRole.SURFACE_CONTAINER_LOWEST -> amoled.surfaceContainerLowest
                LegacyThemeRole.SURFACE_CONTAINER_LOW -> amoled.surfaceContainerLow
                LegacyThemeRole.SURFACE_CONTAINER -> amoled.surfaceContainer
                LegacyThemeRole.SURFACE_CONTAINER_HIGH -> amoled.surfaceContainerHigh
                LegacyThemeRole.SURFACE_CONTAINER_HIGHEST -> amoled.surfaceContainerHighest
                else -> error("unexpected AMOLED override role $role")
            }
            assertEquals("amoled $role", Color(value), actual)
        }
        // Non-surface semantics survive the AMOLED post-processing untouched.
        assertEquals(
            Color(LegacyBuiltinTheme.darkRoles.getValue(LegacyThemeRole.PRIMARY)),
            amoled.primary
        )
    }

    @Test
    fun `legacy builtin scheme is built from the shared compatibility role authority`() {
        assertEquals(35, LegacyBuiltinTheme.lightRoles.size)
        assertEquals(35, LegacyBuiltinTheme.darkRoles.size)
        assertEquals(9, LegacyBuiltinTheme.amoledSurfaceOverrides.size)
    }
}
