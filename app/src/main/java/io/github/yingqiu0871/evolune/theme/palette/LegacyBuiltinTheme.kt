package io.github.yingqiu0871.evolune.theme.palette

/**
 * v1.7.2 Slice A — the effective Material color roles of the released v1.7.1 built-in App
 * theme (the hand-authored teal scheme that `ColorTheme.BUILTIN` selected).
 *
 * This is an App-side compatibility identity only:
 * - it is NOT one of the eight widget presets and must never appear in the widget enum;
 * - its values must never be "adjusted" to match MONET_TEAL (Slice A proves they differ);
 * - Slice B will consume it to keep upgraded `BUILTIN` users visually unchanged.
 *
 * Values are transcriptions of the released v1.7.1 `ui/theme/Color.kt` constants as wired by
 * `Theme.kt` lightScheme/darkScheme (35 roles per mode) plus the exact AMOLED surface
 * overrides (`withAmoledSurfaces`). Pure data: no Android/Compose imports.
 */
enum class LegacyThemeRole {
    PRIMARY, ON_PRIMARY, PRIMARY_CONTAINER, ON_PRIMARY_CONTAINER,
    SECONDARY, ON_SECONDARY, SECONDARY_CONTAINER, ON_SECONDARY_CONTAINER,
    TERTIARY, ON_TERTIARY, TERTIARY_CONTAINER, ON_TERTIARY_CONTAINER,
    ERROR, ON_ERROR, ERROR_CONTAINER, ON_ERROR_CONTAINER,
    BACKGROUND, ON_BACKGROUND, SURFACE, ON_SURFACE,
    SURFACE_VARIANT, ON_SURFACE_VARIANT, OUTLINE, OUTLINE_VARIANT, SCRIM,
    INVERSE_SURFACE, INVERSE_ON_SURFACE, INVERSE_PRIMARY,
    SURFACE_DIM, SURFACE_BRIGHT,
    SURFACE_CONTAINER_LOWEST, SURFACE_CONTAINER_LOW, SURFACE_CONTAINER,
    SURFACE_CONTAINER_HIGH, SURFACE_CONTAINER_HIGHEST
}

object LegacyBuiltinTheme {

    val lightRoles: Map<LegacyThemeRole, Long> = mapOf(
        LegacyThemeRole.PRIMARY to 0xFF006A64,
        LegacyThemeRole.ON_PRIMARY to 0xFFFFFFFF,
        LegacyThemeRole.PRIMARY_CONTAINER to 0xFF9DF2E9,
        LegacyThemeRole.ON_PRIMARY_CONTAINER to 0xFF00504B,
        LegacyThemeRole.SECONDARY to 0xFF4A6360,
        LegacyThemeRole.ON_SECONDARY to 0xFFFFFFFF,
        LegacyThemeRole.SECONDARY_CONTAINER to 0xFFCCE8E4,
        LegacyThemeRole.ON_SECONDARY_CONTAINER to 0xFF324B49,
        LegacyThemeRole.TERTIARY to 0xFF48617A,
        LegacyThemeRole.ON_TERTIARY to 0xFFFFFFFF,
        LegacyThemeRole.TERTIARY_CONTAINER to 0xFFCFE5FF,
        LegacyThemeRole.ON_TERTIARY_CONTAINER to 0xFF304962,
        LegacyThemeRole.ERROR to 0xFFBA1A1A,
        LegacyThemeRole.ON_ERROR to 0xFFFFFFFF,
        LegacyThemeRole.ERROR_CONTAINER to 0xFFFFDAD6,
        LegacyThemeRole.ON_ERROR_CONTAINER to 0xFF93000A,
        LegacyThemeRole.BACKGROUND to 0xFFF4FBF9,
        LegacyThemeRole.ON_BACKGROUND to 0xFF161D1C,
        LegacyThemeRole.SURFACE to 0xFFF4FBF9,
        LegacyThemeRole.ON_SURFACE to 0xFF161D1C,
        LegacyThemeRole.SURFACE_VARIANT to 0xFFDAE5E2,
        LegacyThemeRole.ON_SURFACE_VARIANT to 0xFF3F4947,
        LegacyThemeRole.OUTLINE to 0xFF6F7977,
        LegacyThemeRole.OUTLINE_VARIANT to 0xFFBEC9C6,
        LegacyThemeRole.SCRIM to 0xFF000000,
        LegacyThemeRole.INVERSE_SURFACE to 0xFF2B3231,
        LegacyThemeRole.INVERSE_ON_SURFACE to 0xFFECF2F0,
        LegacyThemeRole.INVERSE_PRIMARY to 0xFF81D5CD,
        LegacyThemeRole.SURFACE_DIM to 0xFFD5DBDA,
        LegacyThemeRole.SURFACE_BRIGHT to 0xFFF4FBF9,
        LegacyThemeRole.SURFACE_CONTAINER_LOWEST to 0xFFFFFFFF,
        LegacyThemeRole.SURFACE_CONTAINER_LOW to 0xFFEFF5F3,
        LegacyThemeRole.SURFACE_CONTAINER to 0xFFE9EFED,
        LegacyThemeRole.SURFACE_CONTAINER_HIGH to 0xFFE3E9E8,
        LegacyThemeRole.SURFACE_CONTAINER_HIGHEST to 0xFFDDE4E2
    )

    val darkRoles: Map<LegacyThemeRole, Long> = mapOf(
        LegacyThemeRole.PRIMARY to 0xFF81D5CD,
        LegacyThemeRole.ON_PRIMARY to 0xFF003734,
        LegacyThemeRole.PRIMARY_CONTAINER to 0xFF00504B,
        LegacyThemeRole.ON_PRIMARY_CONTAINER to 0xFF9DF2E9,
        LegacyThemeRole.SECONDARY to 0xFFB0CCC8,
        LegacyThemeRole.ON_SECONDARY to 0xFF1C3532,
        LegacyThemeRole.SECONDARY_CONTAINER to 0xFF324B49,
        LegacyThemeRole.ON_SECONDARY_CONTAINER to 0xFFCCE8E4,
        LegacyThemeRole.TERTIARY to 0xFFAFC9E7,
        LegacyThemeRole.ON_TERTIARY to 0xFF18324A,
        LegacyThemeRole.TERTIARY_CONTAINER to 0xFF304962,
        LegacyThemeRole.ON_TERTIARY_CONTAINER to 0xFFCFE5FF,
        LegacyThemeRole.ERROR to 0xFFFFB4AB,
        LegacyThemeRole.ON_ERROR to 0xFF690005,
        LegacyThemeRole.ERROR_CONTAINER to 0xFF93000A,
        LegacyThemeRole.ON_ERROR_CONTAINER to 0xFFFFDAD6,
        LegacyThemeRole.BACKGROUND to 0xFF0E1514,
        LegacyThemeRole.ON_BACKGROUND to 0xFFDDE4E2,
        LegacyThemeRole.SURFACE to 0xFF0E1514,
        LegacyThemeRole.ON_SURFACE to 0xFFDDE4E2,
        LegacyThemeRole.SURFACE_VARIANT to 0xFF3F4947,
        LegacyThemeRole.ON_SURFACE_VARIANT to 0xFFBEC9C6,
        LegacyThemeRole.OUTLINE to 0xFF899391,
        LegacyThemeRole.OUTLINE_VARIANT to 0xFF3F4947,
        LegacyThemeRole.SCRIM to 0xFF000000,
        LegacyThemeRole.INVERSE_SURFACE to 0xFFDDE4E2,
        LegacyThemeRole.INVERSE_ON_SURFACE to 0xFF2B3231,
        LegacyThemeRole.INVERSE_PRIMARY to 0xFF006A64,
        LegacyThemeRole.SURFACE_DIM to 0xFF0E1514,
        LegacyThemeRole.SURFACE_BRIGHT to 0xFF343A39,
        LegacyThemeRole.SURFACE_CONTAINER_LOWEST to 0xFF090F0F,
        LegacyThemeRole.SURFACE_CONTAINER_LOW to 0xFF161D1C,
        LegacyThemeRole.SURFACE_CONTAINER to 0xFF1A2120,
        LegacyThemeRole.SURFACE_CONTAINER_HIGH to 0xFF252B2A,
        LegacyThemeRole.SURFACE_CONTAINER_HIGHEST to 0xFF2F3635
    )

    /** Exact `withAmoledSurfaces` overrides (role -> ARGB) that Slice B must keep applying. */
    val amoledSurfaceOverrides: Map<LegacyThemeRole, Long> = mapOf(
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
}
