package io.github.yingqiu0871.evolune.wear

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import androidx.wear.tiles.TileService

internal const val WEAR_TILE_FRESHNESS_MILLIS = 15 * 60 * 1000L
internal const val WEAR_CARD_CORNER_RADIUS_DP = 24f

/** Wear-wide color choice. Preset seeds intentionally match the Phone Widget palette set. */
internal enum class WearColorScheme(val displayName: String) {
    SYSTEM("系统颜色"),
    BLUE("蓝"),
    VIOLET("紫罗兰"),
    SAKURA("樱花"),
    MINT("薄荷"),
    TEAL("青绿"),
    AMBER("琥珀"),
    NEUTRAL("中性"),
    LAVENDER("薰衣草")
}

internal data class WearPalette(
    val background: Int,
    val surface: Int,
    val surfaceHigh: Int,
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val primaryContainer: Int,
    val onBackground: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val onPrimaryContainer: Int,
    val outline: Int
)

internal object WearAppearanceStore {
    private const val PREFERENCES = "wear_appearance"
    private const val KEY_SCHEME = "color_scheme"

    fun read(context: Context): WearColorScheme {
        val raw = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(KEY_SCHEME, null)
        return WearColorScheme.entries.firstOrNull { it.name == raw } ?: WearColorScheme.SYSTEM
    }

    fun write(context: Context, scheme: WearColorScheme) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(KEY_SCHEME, scheme.name) }
        requestWearAppearanceRefresh(context)
    }

    fun resolve(context: Context, scheme: WearColorScheme = read(context)): WearPalette =
        if (scheme == WearColorScheme.SYSTEM) dynamicPalette(context) else PRESETS.getValue(scheme)

    private fun dynamicPalette(context: Context): WearPalette {
        val fallback = PRESETS.getValue(WearColorScheme.TEAL)
        fun color(name: String, default: Int): Int {
            val id = context.resources.getIdentifier(name, "color", "android")
            return if (id == 0) default else runCatching { context.getColor(id) }.getOrDefault(default)
        }
        return fallback.copy(
            background = color("system_neutral1_900", fallback.background),
            surface = color("system_neutral1_900", fallback.surface),
            surfaceHigh = color("system_neutral2_700", fallback.surfaceHigh),
            primary = color("system_accent1_200", fallback.primary),
            secondary = color("system_accent2_200", fallback.secondary),
            tertiary = color("system_accent3_200", fallback.tertiary),
            primaryContainer = color("system_accent1_700", fallback.primaryContainer),
            onBackground = color("system_neutral1_50", fallback.onBackground),
            onSurface = color("system_neutral1_50", fallback.onSurface),
            onSurfaceVariant = color("system_neutral2_200", fallback.onSurfaceVariant),
            onPrimaryContainer = color("system_accent1_50", fallback.onPrimaryContainer),
            outline = color("system_neutral2_600", fallback.outline)
        )
    }

    private fun preset(
        surface: Long,
        primary: Long,
        secondary: Long,
        tertiary: Long,
        container: Long
    ) = WearPalette(
        background = Color.BLACK,
        surface = surface.toInt(),
        surfaceHigh = blend(surface.toInt(), container.toInt(), 0.38f),
        primary = primary.toInt(),
        secondary = secondary.toInt(),
        tertiary = tertiary.toInt(),
        primaryContainer = container.toInt(),
        onBackground = 0xFFF2EFF4.toInt(),
        onSurface = 0xFFF2EFF4.toInt(),
        onSurfaceVariant = 0xFFCDC5D3.toInt(),
        onPrimaryContainer = Color.WHITE,
        outline = blend(0xFFCDC5D3.toInt(), container.toInt(), 0.28f)
    )

    private fun blend(start: Int, end: Int, amount: Float): Int {
        fun channel(shift: Int): Int =
            (((start ushr shift) and 0xff) * (1f - amount) +
                ((end ushr shift) and 0xff) * amount).toInt()
        return Color.rgb(channel(16), channel(8), channel(0))
    }

    private val PRESETS = mapOf(
        WearColorScheme.BLUE to preset(0xFF111318, 0xFFA8C7FA, 0xFFBEC6DC, 0xFFDDBCE0, 0xFF284777),
        WearColorScheme.VIOLET to preset(0xFF151217, 0xFFDDB8FF, 0xFFD1C0D8, 0xFFF5B9A5, 0xFF573D74),
        WearColorScheme.SAKURA to preset(0xFF181113, 0xFFFFB1C5, 0xFFE5BDC6, 0xFFE7C086, 0xFF7D2947),
        WearColorScheme.MINT to preset(0xFF0E1511, 0xFF9DD6B3, 0xFFB7CCBC, 0xFFA6CDDF, 0xFF1D5138),
        WearColorScheme.TEAL to preset(0xFF0E1514, 0xFF81D5CD, 0xFFB0CCC8, 0xFFB2C8E8, 0xFF00504B),
        WearColorScheme.AMBER to preset(0xFF18130B, 0xFFF6BD6C, 0xFFDEC6A1, 0xFFBACD97, 0xFF614000),
        WearColorScheme.NEUTRAL to preset(0xFF141316, 0xFFC9C5CD, 0xFFCBC5CD, 0xFFCAC3DB, 0xFF47464D),
        WearColorScheme.LAVENDER to preset(0xFF141218, 0xFFD0BCFF, 0xFFCCC2DC, 0xFFEFB8C8, 0xFF4F378B)
    )
}

internal fun requestWearAppearanceRefresh(context: Context) {
    val updater = TileService.getUpdater(context.applicationContext)
    updater.requestUpdate(DoseTileService::class.java)
    updater.requestUpdate(NextDoseTileService::class.java)
    updater.requestUpdate(TodayPlanTileService::class.java)
    updater.requestUpdate(CurrentE2TileService::class.java)
    requestWearComplicationUpdates(context.applicationContext)
}
