package io.github.yingqiu0871.evolune.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.ThemePresetSelection

/**
 * v1.7.2 Slice B: the released v1.7.1 built-in light/dark schemes now live in the shared
 * compatibility authority (`LegacyBuiltinTheme`) and are realized by
 * `legacyBuiltinLightScheme()` / `legacyBuiltinDarkScheme()`; the former inline definitions
 * and their Color.kt constants were removed once continuity was proven.
 */

internal fun ThemeMode.usesDarkColors(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK,
    ThemeMode.AMOLED -> true
    ThemeMode.SYSTEM -> systemInDarkTheme
}

internal fun ColorScheme.withAmoledSurfaces(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF202020),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF101010),
    surfaceContainerHighest = Color(0xFF181818)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EvoluneTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DYNAMIC,
    colorSource: ThemeColorSource? = null,
    preset: ThemePresetSelection? = null,
    content: @Composable() () -> Unit
) {
    val systemInDarkTheme = isSystemInDarkTheme()
    
    // 根据主题模式确定是否使用深色主题
    val darkTheme = themeMode.usesDarkColors(systemInDarkTheme)
    
    // v1.7.2 canonical source; `colorTheme` is a compatibility projection used only when the
    // caller does not provide the canonical value (pre-Slice-C screens/tests/previews).
    val effectiveSource = colorSource
        ?: if (colorTheme == ColorTheme.DYNAMIC) ThemeColorSource.DYNAMIC else ThemeColorSource.PRESET
    
    val baseColorScheme = when (effectiveSource) {
        ThemeColorSource.DYNAMIC -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        ThemeColorSource.PRESET -> {
            val effectivePreset = preset
                ?: ThemePresetSelection.LegacyBuiltin
            val scheme = when (effectivePreset) {
                ThemePresetSelection.LegacyBuiltin ->
                    if (darkTheme) legacyBuiltinDarkScheme() else legacyBuiltinLightScheme()

                is ThemePresetSelection.Preset ->
                    if (darkTheme) {
                        presetDarkScheme(effectivePreset.palette)
                    } else {
                        presetLightScheme(effectivePreset.palette)
                    }
            }
            scheme
        }
    }
    val colorScheme = if (themeMode == ThemeMode.AMOLED) {
        baseColorScheme.withAmoledSurfaces()
    } else {
        baseColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
        motionScheme = MotionScheme.expressive()
    )
}

