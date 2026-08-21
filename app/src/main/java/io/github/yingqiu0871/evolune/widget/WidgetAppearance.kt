package io.github.yingqiu0871.evolune.widget

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

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
    MONET_LAVENDER
}

internal data class WidgetAppearanceConfig(
    val themeMode: WidgetThemeMode = WidgetThemeMode.AUTO,
    val colorScheme: WidgetColorScheme = WidgetColorScheme.MATERIAL_YOU_AUTO,
    val backgroundOpacity: Float = DEFAULT_WIDGET_BACKGROUND_OPACITY
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
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )
) : WidgetAppearanceRepository {
    override fun read(appWidgetId: Int): WidgetAppearanceConfig {
        if (appWidgetId < 0) return WidgetAppearanceConfig.Default
        return WidgetAppearanceConfig(
            themeMode = preferences.getString(key(appWidgetId, "theme"), null)
                .toEnumOrDefault(WidgetThemeMode.AUTO),
            colorScheme = preferences.getString(key(appWidgetId, "color"), null)
                .toEnumOrDefault(WidgetColorScheme.MATERIAL_YOU_AUTO),
            backgroundOpacity = preferences.getFloat(
                key(appWidgetId, "opacity"),
                DEFAULT_WIDGET_BACKGROUND_OPACITY
            )
        ).normalized()
    }

    override fun write(appWidgetId: Int, config: WidgetAppearanceConfig) {
        require(appWidgetId >= 0)
        val normalized = config.normalized()
        preferences.edit()
            .putString(key(appWidgetId, "theme"), normalized.themeMode.name)
            .putString(key(appWidgetId, "color"), normalized.colorScheme.name)
            .putFloat(key(appWidgetId, "opacity"), normalized.backgroundOpacity)
            .apply()
    }

    override fun delete(appWidgetId: Int) {
        preferences.edit()
            .remove(key(appWidgetId, "theme"))
            .remove(key(appWidgetId, "color"))
            .remove(key(appWidgetId, "opacity"))
            .apply()
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    private fun key(appWidgetId: Int, suffix: String) = "widget_${appWidgetId}_$suffix"

    private companion object {
        const val PREFERENCES_NAME = "widget_appearance"
    }
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
}

internal object WidgetPaletteResolver {
    fun resolve(context: Context, config: WidgetAppearanceConfig): WidgetPalette {
        val dark = when (config.themeMode) {
            WidgetThemeMode.AUTO ->
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.DARK -> true
        }
        return resolve(config, dark) { name ->
            runCatching {
                val id = context.resources.getIdentifier(name, "color", "android")
                id.takeIf { it != 0 }?.let(context::getColor)
            }.getOrNull()
        }
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

    private fun preset(scheme: WidgetColorScheme, dark: Boolean): WidgetPalette {
        val seed = PRESETS.getValue(scheme)
        return if (dark) {
            WidgetPalette(
                seed.darkSurface,
                0xFFF2EFF4.toInt(),
                0xFFCDC5D3.toInt(),
                seed.darkPrimary,
                seed.darkSecondary,
                seed.darkTertiary,
                seed.darkContainer,
                0xFFFFFFFF.toInt(),
                0xFF4B4650.toInt(),
                0xFFFFB4AB.toInt(),
                seed.darkPrimary
            )
        } else {
            WidgetPalette(
                seed.lightSurface,
                0xFF1F1D20.toInt(),
                0xFF4C464E.toInt(),
                seed.lightPrimary,
                seed.lightSecondary,
                seed.lightTertiary,
                seed.lightContainer,
                seed.lightOnContainer,
                0xFFE1DAE2.toInt(),
                0xFFBA1A1A.toInt(),
                seed.lightPrimary
            )
        }
    }

    private data class Preset(
        val lightSurface: Int,
        val lightPrimary: Int,
        val lightSecondary: Int,
        val lightTertiary: Int,
        val lightContainer: Int,
        val lightOnContainer: Int,
        val darkSurface: Int,
        val darkPrimary: Int,
        val darkSecondary: Int,
        val darkTertiary: Int,
        val darkContainer: Int
    )

    private fun preset(
        lightSurface: Long,
        lightPrimary: Long,
        lightSecondary: Long,
        lightTertiary: Long,
        lightContainer: Long,
        lightOnContainer: Long,
        darkSurface: Long,
        darkPrimary: Long,
        darkSecondary: Long,
        darkTertiary: Long,
        darkContainer: Long
    ) = Preset(
        lightSurface.toInt(),
        lightPrimary.toInt(),
        lightSecondary.toInt(),
        lightTertiary.toInt(),
        lightContainer.toInt(),
        lightOnContainer.toInt(),
        darkSurface.toInt(),
        darkPrimary.toInt(),
        darkSecondary.toInt(),
        darkTertiary.toInt(),
        darkContainer.toInt()
    )

    private val PRESETS = mapOf(
        WidgetColorScheme.MONET_BLUE to preset(0xFFF8F9FF, 0xFF3F5F90, 0xFF565F71, 0xFF705575, 0xFFD6E3FF, 0xFF0B1B33, 0xFF111318, 0xFFA8C7FA, 0xFFBEC6DC, 0xFFDDBCE0, 0xFF284777),
        WidgetColorScheme.MONET_VIOLET to preset(0xFFFBF8FF, 0xFF70558F, 0xFF665A70, 0xFF815343, 0xFFEEDBFF, 0xFF29143F, 0xFF151217, 0xFFDDB8FF, 0xFFD1C0D8, 0xFFF5B9A5, 0xFF573D74),
        WidgetColorScheme.MONET_SAKURA to preset(0xFFFFF8F9, 0xFF9A405D, 0xFF75565F, 0xFF775930, 0xFFFFD9E2, 0xFF3E001D, 0xFF181113, 0xFFFFB1C5, 0xFFE5BDC6, 0xFFE7C086, 0xFF7D2947),
        WidgetColorScheme.MONET_MINT to preset(0xFFF6FCF7, 0xFF356A4E, 0xFF506355, 0xFF3F6374, 0xFFB8F2CE, 0xFF002112, 0xFF0E1511, 0xFF9DD6B3, 0xFFB7CCBC, 0xFFA6CDDF, 0xFF1D5138),
        WidgetColorScheme.MONET_TEAL to preset(0xFFF4FBF9, 0xFF006A64, 0xFF4A6360, 0xFF4A607C, 0xFF9DF2E9, 0xFF00201E, 0xFF0E1514, 0xFF81D5CD, 0xFFB0CCC8, 0xFFB2C8E8, 0xFF00504B),
        WidgetColorScheme.MONET_AMBER to preset(0xFFFFF9F0, 0xFF805600, 0xFF705D3E, 0xFF53643C, 0xFFFFDEA5, 0xFF291800, 0xFF18130B, 0xFFF6BD6C, 0xFFDEC6A1, 0xFFBACD97, 0xFF614000),
        WidgetColorScheme.MONET_NEUTRAL to preset(0xFFFAF9FC, 0xFF5F5E65, 0xFF616066, 0xFF605D6E, 0xFFE5E1E9, 0xFF1B1B1F, 0xFF141316, 0xFFC9C5CD, 0xFFCBC5CD, 0xFFCAC3DB, 0xFF47464D),
        WidgetColorScheme.MONET_LAVENDER to preset(0xFFFCF8FF, 0xFF6750A4, 0xFF625B71, 0xFF7D5260, 0xFFEADDFF, 0xFF21005D, 0xFF141218, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFF4F378B)
    )

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
