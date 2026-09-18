package io.github.yingqiu0871.evolune.data

import io.github.yingqiu0871.evolune.theme.palette.PresetPalette

/**
 * v1.7.2 Slice B — canonical App theme-color source. Persisted as the enum name in the
 * `settings` DataStore key `theme_color_source`; the legacy `color_theme` key remains a
 * compatibility projection only.
 */
enum class ThemeColorSource {
    DYNAMIC,
    PRESET;

    companion object {
        fun fromStored(value: String?): ThemeColorSource? =
            entries.firstOrNull { it.name == value }
    }
}

/**
 * v1.7.2 Slice B — the selected preset identity when [ThemeColorSource.PRESET] is active.
 *
 * The eight normal values reuse [PresetPalette] (one vocabulary with the widget presets);
 * `LegacyBuiltin` is the internal compatibility identity for upgraded users who selected the
 * released v1.7.1 built-in theme. It is persisted as its own stable string id and is never
 * part of [PresetPalette].
 */
sealed interface ThemePresetSelection {

    data class Preset(val palette: PresetPalette) : ThemePresetSelection

    data object LegacyBuiltin : ThemePresetSelection

    val persistedId: String
        get() = when (this) {
            is Preset -> palette.name
            LegacyBuiltin -> LEGACY_BUILTIN_ID
        }

    companion object {
        const val LEGACY_BUILTIN_ID = "LEGACY_BUILTIN"

        fun fromStored(value: String?): ThemePresetSelection? = when {
            value == null -> null
            value == LEGACY_BUILTIN_ID -> LegacyBuiltin
            else -> PresetPalette.fromId(value)?.let { Preset(it) }
        }
    }
}

/** The effective runtime theme state derived from the persisted keys. */
internal data class DerivedThemeState(
    val source: ThemeColorSource,
    val preset: ThemePresetSelection?
)

/**
 * v1.7.2 Slice B — deterministic runtime derivation (local DataStore, NOT backup validation).
 *
 * Matrix:
 * - valid `theme_color_source` = DYNAMIC  -> DYNAMIC (stale preset ids are ignored)
 * - valid `theme_color_source` = PRESET   -> PRESET + valid `theme_preset_id`; otherwise the
 *   deterministic fallback: legacy `color_theme` == BUILTIN -> LEGACY_BUILTIN, else MONET_TEAL
 * - missing/invalid source -> derive from legacy `color_theme`:
 *   DYNAMIC -> DYNAMIC; BUILTIN -> PRESET + LEGACY_BUILTIN; anything else -> DYNAMIC
 *
 * Slice A proved legacy BUILTIN != MONET_TEAL, so the legacy migration never uses MONET_TEAL.
 * Malformed local values never crash startup and never loosen backup schema validation.
 */
internal fun deriveThemeState(
    storedSource: String?,
    storedPresetId: String?,
    legacyColorTheme: String?
): DerivedThemeState = when (ThemeColorSource.fromStored(storedSource)) {
    ThemeColorSource.DYNAMIC -> DerivedThemeState(
        source = ThemeColorSource.DYNAMIC,
        preset = null
    )

    ThemeColorSource.PRESET -> DerivedThemeState(
        source = ThemeColorSource.PRESET,
        preset = ThemePresetSelection.fromStored(storedPresetId)
            ?: presetFallbackFor(legacyColorTheme)
    )

    null -> when (legacyColorTheme) {
        ColorTheme.BUILTIN.name -> DerivedThemeState(
            source = ThemeColorSource.PRESET,
            preset = ThemePresetSelection.LegacyBuiltin
        )

        else -> DerivedThemeState(
            source = ThemeColorSource.DYNAMIC,
            preset = null
        )
    }
}

private fun presetFallbackFor(legacyColorTheme: String?): ThemePresetSelection =
    if (legacyColorTheme == ColorTheme.BUILTIN.name) {
        ThemePresetSelection.LegacyBuiltin
    } else {
        ThemePresetSelection.Preset(PresetPalette.MONET_TEAL)
    }

/** Compatibility projection for the legacy `color_theme` key / pre-Slice-C UI. */
internal fun ThemeColorSource.toLegacyColorTheme(): ColorTheme =
    if (this == ThemeColorSource.DYNAMIC) ColorTheme.DYNAMIC else ColorTheme.BUILTIN
