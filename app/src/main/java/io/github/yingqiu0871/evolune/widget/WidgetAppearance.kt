package io.github.yingqiu0871.evolune.widget

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.app.WallpaperColors
import android.app.WallpaperManager
import androidx.core.content.edit
import io.github.yingqiu0871.evolune.theme.palette.PaletteCatalog
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import kotlin.math.roundToInt

internal const val DEFAULT_WIDGET_BACKGROUND_OPACITY = 1f
internal const val MIN_WIDGET_BACKGROUND_OPACITY = 0.3f

internal enum class WidgetThemeMode { AUTO, LIGHT, DARK }

internal enum class WidgetColorScheme {
    MATERIAL_YOU_AUTO,
    MONET_BLUE,
    MONET_VIOLET,
    MONET_SAKURA,
    MONET_MINT,
    MONET_TEAL,
    MONET_AMBER,
    MONET_NEUTRAL,
    MONET_LAVENDER;

    companion object {
        /** Persisted-identity read rule (v1.7.1 behavior): unknown/missing -> MATERIAL_YOU_AUTO. */
        fun fromStored(value: String?): WidgetColorScheme =
            entries.firstOrNull { it.name == value } ?: MATERIAL_YOU_AUTO
    }
}

/**
 * Stable SharedPreferences identity of [WidgetAppearanceStore] (file name + per-widget key
 * format). Extracted for testable pinning only; values are the frozen v1.7.1 contract.
 */
internal object WidgetAppearanceKeys {
    const val PREFERENCES_NAME = "widget_appearance"

    fun theme(id: Int) = "widget_${id}_theme"
    fun color(id: Int) = "widget_${id}_color"
    fun opacity(id: Int) = "widget_${id}_opacity"
    fun style(id: Int) = "widget_${id}_style"
}

/** Stable persisted style identifiers shared by the phone gallery renderer. */
internal enum class WidgetStyle(val id: String) {
    LEGACY_DEFAULT("legacy_default"),
    TODAY_PLAN("today_plan"),
    NEXT_DOSE("next_dose"),
    CURRENT_E2("current_e2"),
    PK_CHART("pk_chart");

    companion object {
        fun fromId(value: String?): WidgetStyle =
            entries.firstOrNull { it.id == value } ?: LEGACY_DEFAULT
    }
}

internal data class WidgetAppearanceConfig(
    val themeMode: WidgetThemeMode = WidgetThemeMode.AUTO,
    val colorScheme: WidgetColorScheme = WidgetColorScheme.MATERIAL_YOU_AUTO,
    val backgroundOpacity: Float = DEFAULT_WIDGET_BACKGROUND_OPACITY,
    val styleId: WidgetStyle = WidgetStyle.LEGACY_DEFAULT
) {
    fun normalized() = copy(
        backgroundOpacity = backgroundOpacity.coerceIn(MIN_WIDGET_BACKGROUND_OPACITY, 1f)
    )

    companion object {
        val Default = WidgetAppearanceConfig()
    }
}

internal interface WidgetAppearanceRepository {
    fun read(appWidgetId: Int): WidgetAppearanceConfig
    fun write(appWidgetId: Int, config: WidgetAppearanceConfig)
    fun delete(appWidgetId: Int)
}

internal class WidgetAppearanceStore(
    context: Context,
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        WidgetAppearanceKeys.PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
) : WidgetAppearanceRepository {
    override fun read(appWidgetId: Int): WidgetAppearanceConfig {
        if (appWidgetId < 0) return WidgetAppearanceConfig.Default
        return WidgetAppearanceConfig(
            themeMode = preferences.getString(WidgetAppearanceKeys.theme(appWidgetId), null)
                .toEnumOrDefault(WidgetThemeMode.AUTO),
            colorScheme = WidgetColorScheme.fromStored(
                preferences.getString(WidgetAppearanceKeys.color(appWidgetId), null)
            ),
            backgroundOpacity = preferences.getFloat(
                WidgetAppearanceKeys.opacity(appWidgetId),
                DEFAULT_WIDGET_BACKGROUND_OPACITY
            ),
            styleId = WidgetStyle.fromId(
                preferences.getString(WidgetAppearanceKeys.style(appWidgetId), null)
            )
        ).normalized()
    }

    override fun write(appWidgetId: Int, config: WidgetAppearanceConfig) {
        require(appWidgetId >= 0)
        val normalized = config.normalized()
        preferences.edit {
            putString(WidgetAppearanceKeys.theme(appWidgetId), normalized.themeMode.name)
            putString(WidgetAppearanceKeys.color(appWidgetId), normalized.colorScheme.name)
            putFloat(WidgetAppearanceKeys.opacity(appWidgetId), normalized.backgroundOpacity)
            putString(WidgetAppearanceKeys.style(appWidgetId), normalized.styleId.id)
        }
    }

    override fun delete(appWidgetId: Int) {
        preferences.edit {
            remove(WidgetAppearanceKeys.theme(appWidgetId))
            remove(WidgetAppearanceKeys.color(appWidgetId))
            remove(WidgetAppearanceKeys.opacity(appWidgetId))
            remove(WidgetAppearanceKeys.style(appWidgetId))
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default
}

internal class WidgetConfigurationController(
    private val repository: WidgetAppearanceRepository,
    private val refresh: suspend (Int) -> Unit
) {
    fun load(appWidgetId: Int) = repository.read(appWidgetId)

    suspend fun apply(appWidgetId: Int, config: WidgetAppearanceConfig) {
        require(appWidgetId >= 0)
        repository.write(appWidgetId, config.normalized())
        refresh(appWidgetId)
    }

    fun restoreDefaults() = WidgetAppearanceConfig.Default

    fun cancel() = Unit
}

internal data class WidgetPalette(
    val surface: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val progressTrack: Int,
    val error: Int,
    val primaryForeground: Int
) {
    val medicationRailRoles: List<Int>
        get() = listOf(primary, secondary, tertiary)

    fun medicationRailColor(visibleRowIndex: Int): Int {
        require(visibleRowIndex >= 0)
        return medicationRailRoles[visibleRowIndex % medicationRailRoles.size]
    }

    fun heroLabelPanelColor(): Int = primaryContainer

    fun heroValuePanelColor(): Int = surface.blendToward(secondary, 0.12f)

    private fun Int.blendToward(target: Int, amount: Float): Int {
        val fraction = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val start = this ushr shift and 0xff
            val end = target ushr shift and 0xff
            return (start + (end - start) * fraction).roundToInt()
        }
        return 0xff000000.toInt() or
            (channel(16) shl 16) or
            (channel(8) shl 8) or
            channel(0)
    }
}

internal object WidgetPaletteResolver {
    fun resolve(context: Context, config: WidgetAppearanceConfig): WidgetPalette {
        val dark = when (config.themeMode) {
            WidgetThemeMode.AUTO -> resolveAutomaticDarkMode(context, config.backgroundOpacity)
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.DARK -> true
        }
        return resolve(config, dark) { name ->
            runCatching {
                systemColorId(name)?.let(context::getColor)
            }.getOrNull()
        }
    }

    private fun resolveAutomaticDarkMode(context: Context, backgroundOpacity: Float): Boolean {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        if (backgroundOpacity >= OPAQUE_BACKGROUND_THRESHOLD) return systemDark
        val colors = runCatching {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return systemDark
        return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT == 0
    }

    private const val OPAQUE_BACKGROUND_THRESHOLD = 0.8f

    private fun systemColorId(name: String): Int? = when (name) {
        "system_neutral1_10" -> android.R.color.system_neutral1_10
        "system_neutral1_50" -> android.R.color.system_neutral1_50
        "system_neutral1_100" -> android.R.color.system_neutral1_100
        "system_neutral1_200" -> android.R.color.system_neutral1_200
        "system_neutral1_600" -> android.R.color.system_neutral1_600
        "system_neutral1_700" -> android.R.color.system_neutral1_700
        "system_neutral1_900" -> android.R.color.system_neutral1_900
        "system_neutral2_200" -> android.R.color.system_neutral2_200
        "system_neutral2_600" -> android.R.color.system_neutral2_600
        "system_neutral2_700" -> android.R.color.system_neutral2_700
        "system_accent1_50" -> android.R.color.system_accent1_50
        "system_accent1_100" -> android.R.color.system_accent1_100
        "system_accent1_200" -> android.R.color.system_accent1_200
        "system_accent1_600" -> android.R.color.system_accent1_600
        "system_accent1_700" -> android.R.color.system_accent1_700
        "system_accent1_900" -> android.R.color.system_accent1_900
        "system_accent2_200" -> android.R.color.system_accent2_200
        "system_accent2_600" -> android.R.color.system_accent2_600
        "system_accent2_700" -> android.R.color.system_accent2_700
        "system_accent3_200" -> android.R.color.system_accent3_200
        "system_accent3_600" -> android.R.color.system_accent3_600
        "system_accent3_700" -> android.R.color.system_accent3_700
        else -> null
    }

    internal fun resolve(
        config: WidgetAppearanceConfig,
        dark: Boolean,
        dynamicColor: (String) -> Int? = { null }
    ): WidgetPalette = (
        if (config.colorScheme == WidgetColorScheme.MATERIAL_YOU_AUTO) {
            materialYou(dark, dynamicColor)
        } else {
            preset(config.colorScheme, dark)
        }
        ).withResolvedForegroundContrast()

    private fun materialYou(dark: Boolean, color: (String) -> Int?): WidgetPalette {
        val fallback = preset(WidgetColorScheme.MONET_TEAL, dark)
        fun system(name: String, default: Int) = color(name) ?: default
        return fallback.copy(
            surface = system(if (dark) "system_neutral1_900" else "system_neutral1_10", fallback.surface),
            onSurface = system(if (dark) "system_neutral1_50" else "system_neutral1_900", fallback.onSurface),
            onSurfaceVariant = system(if (dark) "system_neutral2_200" else "system_neutral2_700", fallback.onSurfaceVariant),
            primary = system(if (dark) "system_accent1_200" else "system_accent1_600", fallback.primary),
            secondary = system(if (dark) "system_accent2_200" else "system_accent2_600", fallback.secondary),
            tertiary = system(if (dark) "system_accent3_200" else "system_accent3_600", fallback.tertiary),
            primaryContainer = system(if (dark) "system_accent1_700" else "system_accent1_100", fallback.primaryContainer),
            onPrimaryContainer = system(if (dark) "system_accent1_50" else "system_accent1_900", fallback.onPrimaryContainer),
            progressTrack = system(if (dark) "system_neutral2_700" else "system_neutral2_200", fallback.progressTrack)
        )
    }

    private fun WidgetColorScheme.toPresetPalette(): PresetPalette = when (this) {
        WidgetColorScheme.MATERIAL_YOU_AUTO ->
            error("MATERIAL_YOU_AUTO is a dynamics source, not a preset palette")

        WidgetColorScheme.MONET_BLUE -> PresetPalette.MONET_BLUE
        WidgetColorScheme.MONET_VIOLET -> PresetPalette.MONET_VIOLET
        WidgetColorScheme.MONET_SAKURA -> PresetPalette.MONET_SAKURA
        WidgetColorScheme.MONET_MINT -> PresetPalette.MONET_MINT
        WidgetColorScheme.MONET_TEAL -> PresetPalette.MONET_TEAL
        WidgetColorScheme.MONET_AMBER -> PresetPalette.MONET_AMBER
        WidgetColorScheme.MONET_NEUTRAL -> PresetPalette.MONET_NEUTRAL
        WidgetColorScheme.MONET_LAVENDER -> PresetPalette.MONET_LAVENDER
    }

    private fun preset(scheme: WidgetColorScheme, dark: Boolean): WidgetPalette {
        // v1.7.2 Slice A: preset seeds come from the shared palette authority; the widget
        // keeps its own presentation mapping/fixed constants only.
        val seed = PaletteCatalog.seed(scheme.toPresetPalette())
        return if (dark) {
            WidgetPalette(
                seed.darkSurface.toInt(),
                0xFFF2EFF4.toInt(),
                0xFFCDC5D3.toInt(),
                seed.darkPrimary.toInt(),
                seed.darkSecondary.toInt(),
                seed.darkTertiary.toInt(),
                seed.darkContainer.toInt(),
                0xFFFFFFFF.toInt(),
                0xFF4B4650.toInt(),
                0xFFFFB4AB.toInt(),
                seed.darkPrimary.toInt()
            )
        } else {
            WidgetPalette(
                seed.lightSurface.toInt(),
                0xFF1F1D20.toInt(),
                0xFF4C464E.toInt(),
                seed.lightPrimary.toInt(),
                seed.lightSecondary.toInt(),
                seed.lightTertiary.toInt(),
                seed.lightContainer.toInt(),
                seed.lightOnContainer.toInt(),
                0xFFE1DAE2.toInt(),
                0xFFBA1A1A.toInt(),
                seed.lightPrimary.toInt()
            )
        }
    }

    private fun WidgetPalette.withResolvedForegroundContrast(): WidgetPalette = copy(
        surface = surface.opaque(),
        onSurface = readableForeground(surface, listOf(onSurface, onSurfaceVariant)),
        onSurfaceVariant = readableForeground(surface, listOf(onSurfaceVariant, onSurface)),
        primaryForeground = readableForeground(
            surface,
            listOf(primary, onSurface, onSurfaceVariant)
        ),
        onPrimaryContainer = readableForeground(
            primaryContainer,
            listOf(onPrimaryContainer, onSurface, onSurfaceVariant)
        ),
        error = readableForeground(surface, listOf(error, onSurface))
    )

    internal fun contrastRatio(foreground: Int, background: Int): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun readableForeground(background: Int, preferred: List<Int>): Int {
        val readablePreferred = preferred
            .map { it.opaque() }
            .distinct()
            .filter { contrastRatio(it, background) >= MIN_TEXT_CONTRAST }
        return readablePreferred.maxByOrNull { contrastRatio(it, background) }
            ?: listOf(OPAQUE_BLACK, OPAQUE_WHITE)
                .maxBy { contrastRatio(it, background) }
    }

    private fun relativeLuminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 0xff) / 255.0
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun Int.opaque(): Int = this or OPAQUE_ALPHA

    private const val MIN_TEXT_CONTRAST = 4.5
    private const val OPAQUE_ALPHA = -0x1000000
    private const val OPAQUE_BLACK = -0x1000000
    private const val OPAQUE_WHITE = -0x1
}
