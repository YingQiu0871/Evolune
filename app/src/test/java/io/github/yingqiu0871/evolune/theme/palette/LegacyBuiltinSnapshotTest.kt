package io.github.yingqiu0871.evolune.theme.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.2 Slice A — independent snapshot of the released v1.7.1 built-in App theme.
 *
 * Expected values are a second transcription of the released v1.7.1 `ui/theme/Color.kt`
 * constants / `Theme.kt` wiring (the first lives in [LegacyBuiltinTheme]). Slice B uses this
 * to prove `LEGACY_BUILTIN` equals the released appearance exactly.
 */
class LegacyBuiltinSnapshotTest {

    private val expectedLight = mapOf(
        LegacyThemeRole.PRIMARY to 0xFF006A64, LegacyThemeRole.ON_PRIMARY to 0xFFFFFFFF,
        LegacyThemeRole.PRIMARY_CONTAINER to 0xFF9DF2E9, LegacyThemeRole.ON_PRIMARY_CONTAINER to 0xFF00504B,
        LegacyThemeRole.SECONDARY to 0xFF4A6360, LegacyThemeRole.ON_SECONDARY to 0xFFFFFFFF,
        LegacyThemeRole.SECONDARY_CONTAINER to 0xFFCCE8E4, LegacyThemeRole.ON_SECONDARY_CONTAINER to 0xFF324B49,
        LegacyThemeRole.TERTIARY to 0xFF48617A, LegacyThemeRole.ON_TERTIARY to 0xFFFFFFFF,
        LegacyThemeRole.TERTIARY_CONTAINER to 0xFFCFE5FF, LegacyThemeRole.ON_TERTIARY_CONTAINER to 0xFF304962,
        LegacyThemeRole.ERROR to 0xFFBA1A1A, LegacyThemeRole.ON_ERROR to 0xFFFFFFFF,
        LegacyThemeRole.ERROR_CONTAINER to 0xFFFFDAD6, LegacyThemeRole.ON_ERROR_CONTAINER to 0xFF93000A,
        LegacyThemeRole.BACKGROUND to 0xFFF4FBF9, LegacyThemeRole.ON_BACKGROUND to 0xFF161D1C,
        LegacyThemeRole.SURFACE to 0xFFF4FBF9, LegacyThemeRole.ON_SURFACE to 0xFF161D1C,
        LegacyThemeRole.SURFACE_VARIANT to 0xFFDAE5E2, LegacyThemeRole.ON_SURFACE_VARIANT to 0xFF3F4947,
        LegacyThemeRole.OUTLINE to 0xFF6F7977, LegacyThemeRole.OUTLINE_VARIANT to 0xFFBEC9C6,
        LegacyThemeRole.SCRIM to 0xFF000000, LegacyThemeRole.INVERSE_SURFACE to 0xFF2B3231,
        LegacyThemeRole.INVERSE_ON_SURFACE to 0xFFECF2F0, LegacyThemeRole.INVERSE_PRIMARY to 0xFF81D5CD,
        LegacyThemeRole.SURFACE_DIM to 0xFFD5DBDA, LegacyThemeRole.SURFACE_BRIGHT to 0xFFF4FBF9,
        LegacyThemeRole.SURFACE_CONTAINER_LOWEST to 0xFFFFFFFF, LegacyThemeRole.SURFACE_CONTAINER_LOW to 0xFFEFF5F3,
        LegacyThemeRole.SURFACE_CONTAINER to 0xFFE9EFED, LegacyThemeRole.SURFACE_CONTAINER_HIGH to 0xFFE3E9E8,
        LegacyThemeRole.SURFACE_CONTAINER_HIGHEST to 0xFFDDE4E2
    )

    private val expectedDark = mapOf(
        LegacyThemeRole.PRIMARY to 0xFF81D5CD, LegacyThemeRole.ON_PRIMARY to 0xFF003734,
        LegacyThemeRole.PRIMARY_CONTAINER to 0xFF00504B, LegacyThemeRole.ON_PRIMARY_CONTAINER to 0xFF9DF2E9,
        LegacyThemeRole.SECONDARY to 0xFFB0CCC8, LegacyThemeRole.ON_SECONDARY to 0xFF1C3532,
        LegacyThemeRole.SECONDARY_CONTAINER to 0xFF324B49, LegacyThemeRole.ON_SECONDARY_CONTAINER to 0xFFCCE8E4,
        LegacyThemeRole.TERTIARY to 0xFFAFC9E7, LegacyThemeRole.ON_TERTIARY to 0xFF18324A,
        LegacyThemeRole.TERTIARY_CONTAINER to 0xFF304962, LegacyThemeRole.ON_TERTIARY_CONTAINER to 0xFFCFE5FF,
        LegacyThemeRole.ERROR to 0xFFFFB4AB, LegacyThemeRole.ON_ERROR to 0xFF690005,
        LegacyThemeRole.ERROR_CONTAINER to 0xFF93000A, LegacyThemeRole.ON_ERROR_CONTAINER to 0xFFFFDAD6,
        LegacyThemeRole.BACKGROUND to 0xFF0E1514, LegacyThemeRole.ON_BACKGROUND to 0xFFDDE4E2,
        LegacyThemeRole.SURFACE to 0xFF0E1514, LegacyThemeRole.ON_SURFACE to 0xFFDDE4E2,
        LegacyThemeRole.SURFACE_VARIANT to 0xFF3F4947, LegacyThemeRole.ON_SURFACE_VARIANT to 0xFFBEC9C6,
        LegacyThemeRole.OUTLINE to 0xFF899391, LegacyThemeRole.OUTLINE_VARIANT to 0xFF3F4947,
        LegacyThemeRole.SCRIM to 0xFF000000, LegacyThemeRole.INVERSE_SURFACE to 0xFFDDE4E2,
        LegacyThemeRole.INVERSE_ON_SURFACE to 0xFF2B3231, LegacyThemeRole.INVERSE_PRIMARY to 0xFF006A64,
        LegacyThemeRole.SURFACE_DIM to 0xFF0E1514, LegacyThemeRole.SURFACE_BRIGHT to 0xFF343A39,
        LegacyThemeRole.SURFACE_CONTAINER_LOWEST to 0xFF090F0F, LegacyThemeRole.SURFACE_CONTAINER_LOW to 0xFF161D1C,
        LegacyThemeRole.SURFACE_CONTAINER to 0xFF1A2120, LegacyThemeRole.SURFACE_CONTAINER_HIGH to 0xFF252B2A,
        LegacyThemeRole.SURFACE_CONTAINER_HIGHEST to 0xFF2F3635
    )

    private val expectedAmoled = mapOf(
        LegacyThemeRole.BACKGROUND to 0xFF000000,
        LegacyThemeRole.SURFACE to 0xFF000000,
        LegacyThemeRole.SURFACE_DIM to 0xFF000000,
        LegacyThemeRole.SURFACE_BRIGHT to 0xFF202020,
        LegacyThemeRole.SURFACE_CONTAINER_LOWEST to 0xFF000000,
        LegacyThemeRole.SURFACE_CONTAINER_LOW to 0xFF050505,
        LegacyThemeRole.SURFACE_CONTAINER to 0xFF0A0A0A,
        LegacyThemeRole.SURFACE_CONTAINER_HIGH to 0xFF101010,
        LegacyThemeRole.SURFACE_CONTAINER_HIGHEST to 0xFF181818
    )

    @Test
    fun `legacy built-in light roles match the released v1_7_1 values`() {
        assertEquals(expectedLight, LegacyBuiltinTheme.lightRoles)
    }

    @Test
    fun `legacy built-in dark roles match the released v1_7_1 values`() {
        assertEquals(expectedDark, LegacyBuiltinTheme.darkRoles)
    }

    @Test
    fun `legacy role sets are complete and symmetric and amoled overrides are a subset`() {
        assertEquals(LegacyThemeRole.entries.toSet(), LegacyBuiltinTheme.lightRoles.keys)
        assertEquals(LegacyThemeRole.entries.toSet(), LegacyBuiltinTheme.darkRoles.keys)
        assertTrue(
            "AMOLED overrides must reference known roles",
            LegacyThemeRole.entries.toSet().containsAll(LegacyBuiltinTheme.amoledSurfaceOverrides.keys)
        )
        assertEquals(expectedAmoled, LegacyBuiltinTheme.amoledSurfaceOverrides)
    }
}
