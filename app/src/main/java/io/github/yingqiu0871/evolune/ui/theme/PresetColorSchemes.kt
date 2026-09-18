package io.github.yingqiu0871.evolune.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.yingqiu0871.evolune.theme.palette.LegacyBuiltinTheme
import io.github.yingqiu0871.evolune.theme.palette.LegacyThemeRole
import io.github.yingqiu0871.evolune.theme.palette.PaletteCatalog
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlin.math.roundToInt

/**
 * v1.7.2 Slice B — App Material schemes built from the shared palette authority.
 *
 * - [presetLightScheme]/[presetDarkScheme] realize the frozen contract §13 derivation for the
 *   eight [PresetPalette] values; seeds come exclusively from [PaletteCatalog] (no duplicated
 *   MONET tables).
 * - [legacyBuiltinLightScheme]/[legacyBuiltinDarkScheme] reproduce the released v1.7.1 built-in
 *   theme role-by-role from [LegacyBuiltinTheme] (App compatibility identity).
 *
 * Roles outside the frozen derivation table (scrim / inverse / surfaceDim / surfaceBright /
 * surfaceContainer*) intentionally keep Material's fixed baseline constants for presets; the
 * legacy schemes pass every role explicitly.
 */
private const val LIGHT_ON_SURFACE = 0xFF1F1D20
private const val DARK_ON_SURFACE = 0xFFF2EFF4
private const val LIGHT_ON_SURFACE_VARIANT = 0xFF4C464E
private const val DARK_ON_SURFACE_VARIANT = 0xFFCDC5D3
private const val LIGHT_ERROR = 0xFFBA1A1A
private const val DARK_ERROR = 0xFFFFB4AB
private const val LIGHT_ON_ERROR = 0xFFFFFFFF
private const val DARK_ON_ERROR = 0xFF690005
private const val DARK_ON_PRIMARY_CONTAINER = 0xFFFFFFFF

internal fun presetLightScheme(preset: PresetPalette): ColorScheme {
    val seed = PaletteCatalog.seed(preset)
    val surface = Color(seed.lightSurface)
    val primary = Color(seed.lightPrimary)
    val secondary = Color(seed.lightSecondary)
    val tertiary = Color(seed.lightTertiary)
    val secondaryContainer = surface.blendToward(secondary, 0.12)
    val tertiaryContainer = surface.blendToward(tertiary, 0.12)
    val onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT)
    return lightColorScheme(
        primary = primary,
        onPrimary = contrastOn(primary),
        primaryContainer = Color(seed.lightContainer),
        onPrimaryContainer = Color(seed.lightOnContainer),
        secondary = secondary,
        onSecondary = contrastOn(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contrastOn(secondaryContainer),
        tertiary = tertiary,
        onTertiary = contrastOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contrastOn(tertiaryContainer),
        error = Color(LIGHT_ERROR),
        onError = Color(LIGHT_ON_ERROR),
        background = surface,
        onBackground = Color(LIGHT_ON_SURFACE),
        surface = surface,
        onSurface = Color(LIGHT_ON_SURFACE),
        surfaceVariant = surface.blendToward(primary, 0.08),
        onSurfaceVariant = onSurfaceVariant,
        outline = onSurfaceVariant,
        outlineVariant = surface.blendToward(onSurfaceVariant, 0.50)
    )
}

internal fun presetDarkScheme(preset: PresetPalette): ColorScheme {
    val seed = PaletteCatalog.seed(preset)
    val surface = Color(seed.darkSurface)
    val primary = Color(seed.darkPrimary)
    val secondary = Color(seed.darkSecondary)
    val tertiary = Color(seed.darkTertiary)
    val secondaryContainer = surface.blendToward(secondary, 0.12)
    val tertiaryContainer = surface.blendToward(tertiary, 0.12)
    val onSurfaceVariant = Color(DARK_ON_SURFACE_VARIANT)
    return darkColorScheme(
        primary = primary,
        onPrimary = contrastOn(primary),
        primaryContainer = Color(seed.darkContainer),
        onPrimaryContainer = Color(DARK_ON_PRIMARY_CONTAINER),
        secondary = secondary,
        onSecondary = contrastOn(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = contrastOn(secondaryContainer),
        tertiary = tertiary,
        onTertiary = contrastOn(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = contrastOn(tertiaryContainer),
        error = Color(DARK_ERROR),
        onError = Color(DARK_ON_ERROR),
        background = surface,
        onBackground = Color(DARK_ON_SURFACE),
        surface = surface,
        onSurface = Color(DARK_ON_SURFACE),
        surfaceVariant = surface.blendToward(primary, 0.08),
        onSurfaceVariant = onSurfaceVariant,
        outline = onSurfaceVariant,
        outlineVariant = surface.blendToward(onSurfaceVariant, 0.50)
    )
}

internal fun legacyBuiltinLightScheme(): ColorScheme =
    legacyScheme(LegacyBuiltinTheme.lightRoles)

internal fun legacyBuiltinDarkScheme(): ColorScheme =
    legacyScheme(LegacyBuiltinTheme.darkRoles)

/**
 * Builds a scheme with EVERY role passed explicitly, so the light/dark builder choice cannot
 * influence the result (their only difference is defaults for unset roles). `surfaceTint` is
 * passed explicitly for the same reason.
 */
private fun legacyScheme(roles: Map<LegacyThemeRole, Long>): ColorScheme {
    fun role(value: LegacyThemeRole): Color = Color(roles.getValue(value))
    return lightColorScheme(
        primary = role(LegacyThemeRole.PRIMARY),
        onPrimary = role(LegacyThemeRole.ON_PRIMARY),
        primaryContainer = role(LegacyThemeRole.PRIMARY_CONTAINER),
        onPrimaryContainer = role(LegacyThemeRole.ON_PRIMARY_CONTAINER),
        secondary = role(LegacyThemeRole.SECONDARY),
        onSecondary = role(LegacyThemeRole.ON_SECONDARY),
        secondaryContainer = role(LegacyThemeRole.SECONDARY_CONTAINER),
        onSecondaryContainer = role(LegacyThemeRole.ON_SECONDARY_CONTAINER),
        tertiary = role(LegacyThemeRole.TERTIARY),
        onTertiary = role(LegacyThemeRole.ON_TERTIARY),
        tertiaryContainer = role(LegacyThemeRole.TERTIARY_CONTAINER),
        onTertiaryContainer = role(LegacyThemeRole.ON_TERTIARY_CONTAINER),
        error = role(LegacyThemeRole.ERROR),
        onError = role(LegacyThemeRole.ON_ERROR),
        errorContainer = role(LegacyThemeRole.ERROR_CONTAINER),
        onErrorContainer = role(LegacyThemeRole.ON_ERROR_CONTAINER),
        background = role(LegacyThemeRole.BACKGROUND),
        onBackground = role(LegacyThemeRole.ON_BACKGROUND),
        surface = role(LegacyThemeRole.SURFACE),
        onSurface = role(LegacyThemeRole.ON_SURFACE),
        surfaceVariant = role(LegacyThemeRole.SURFACE_VARIANT),
        onSurfaceVariant = role(LegacyThemeRole.ON_SURFACE_VARIANT),
        outline = role(LegacyThemeRole.OUTLINE),
        outlineVariant = role(LegacyThemeRole.OUTLINE_VARIANT),
        scrim = role(LegacyThemeRole.SCRIM),
        inverseSurface = role(LegacyThemeRole.INVERSE_SURFACE),
        inverseOnSurface = role(LegacyThemeRole.INVERSE_ON_SURFACE),
        inversePrimary = role(LegacyThemeRole.INVERSE_PRIMARY),
        surfaceDim = role(LegacyThemeRole.SURFACE_DIM),
        surfaceBright = role(LegacyThemeRole.SURFACE_BRIGHT),
        surfaceContainerLowest = role(LegacyThemeRole.SURFACE_CONTAINER_LOWEST),
        surfaceContainerLow = role(LegacyThemeRole.SURFACE_CONTAINER_LOW),
        surfaceContainer = role(LegacyThemeRole.SURFACE_CONTAINER),
        surfaceContainerHigh = role(LegacyThemeRole.SURFACE_CONTAINER_HIGH),
        surfaceContainerHighest = role(LegacyThemeRole.SURFACE_CONTAINER_HIGHEST),
        surfaceTint = role(LegacyThemeRole.PRIMARY)
    )
}

internal fun contrastOn(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
        Color.White
    } else {
        Color.Black
    }

internal fun contrastRatio(foreground: Color, background: Color): Double {
    val lighter = maxOf(luminance(foreground), luminance(background))
    val darker = minOf(luminance(foreground), luminance(background))
    return (lighter + 0.05) / (darker + 0.05)
}

private fun luminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

private fun Color.blendToward(target: Color, amount: Double): Color {
    val fraction = amount.coerceIn(0.0, 1.0)
    fun channel(start: Float, end: Float): Float {
        val startByte = start * 255f
        val endByte = end * 255f
        return (startByte + (endByte - startByte) * fraction)
            .roundToInt()
            .coerceIn(0, 255) / 255f
    }
    return Color(
        red = channel(red, target.red),
        green = channel(green, target.green),
        blue = channel(blue, target.blue),
        alpha = 1f
    )
}
