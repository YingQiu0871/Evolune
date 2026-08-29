package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.ColorTheme
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceAndFormatScreen(
    settings: UserSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onColorThemeChange: (ColorTheme) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-appearance-format-screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ThemeModeSection(settings.themeMode, onThemeModeChange)
        ColorThemeSection(settings.colorTheme, onColorThemeChange)
        TimeFormatSection(settings.timeFormat, onTimeFormatChange)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeSection(
    currentMode: ThemeMode,
    onModeChange: (ThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_theme_mode_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                    ThemeMode.AMOLED -> stringResource(R.string.settings_theme_mode_amoled)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                }
                val description = when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light_desc)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark_desc)
                    ThemeMode.AMOLED -> stringResource(R.string.settings_theme_mode_amoled_desc)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system_desc)
                }
                val icon = when (mode) {
                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                    ThemeMode.AMOLED -> Icons.Outlined.Contrast
                    ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid
                }
                SegmentedListItem(
                    modifier = Modifier.testTag("theme-mode-${mode.name.lowercase()}"),
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    shapes = stableSegmentedShapes(index, ThemeMode.entries.size),
                    colors = settingsListItemColors(),
                    leadingContent = {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.testTag("theme-mode-icon-${mode.name.lowercase()}")
                        )
                    },
                    trailingContent = {
                        RadioButton(selected = currentMode == mode, onClick = null)
                    },
                    supportingContent = { Text(description) }
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorThemeSection(
    currentTheme: ColorTheme,
    onThemeChange: (ColorTheme) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_color_theme_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ColorTheme.entries.forEachIndexed { index, theme ->
                val label = when (theme) {
                    ColorTheme.DYNAMIC -> stringResource(R.string.settings_color_theme_dynamic)
                    ColorTheme.BUILTIN -> stringResource(R.string.settings_color_theme_builtin)
                }
                val description = when (theme) {
                    ColorTheme.DYNAMIC -> stringResource(R.string.settings_color_theme_dynamic_desc)
                    ColorTheme.BUILTIN -> stringResource(R.string.settings_color_theme_builtin_desc)
                }
                val icon = when (theme) {
                    ColorTheme.DYNAMIC -> Icons.Outlined.ColorLens
                    ColorTheme.BUILTIN -> Icons.Outlined.Palette
                }
                SegmentedListItem(
                    modifier = Modifier.testTag("color-theme-${theme.name.lowercase()}"),
                    selected = currentTheme == theme,
                    onClick = { onThemeChange(theme) },
                    shapes = stableSegmentedShapes(index, ColorTheme.entries.size),
                    colors = settingsListItemColors(),
                    leadingContent = { Icon(imageVector = icon, contentDescription = null) },
                    trailingContent = {
                        RadioButton(selected = currentTheme == theme, onClick = null)
                    },
                    supportingContent = { Text(description) }
                ) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimeFormatSection(
    currentFormat: TimeFormat,
    onFormatChange: (TimeFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_time_format_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TimeFormat.entries.forEachIndexed { index, format ->
                val label = when (format) {
                    TimeFormat.SYSTEM -> stringResource(R.string.settings_time_format_system)
                    TimeFormat.HOUR_12 -> stringResource(R.string.settings_time_format_12h)
                    TimeFormat.HOUR_24 -> stringResource(R.string.settings_time_format_24h)
                }
                val description = when (format) {
                    TimeFormat.SYSTEM -> stringResource(R.string.settings_time_format_system_desc)
                    TimeFormat.HOUR_12 -> stringResource(R.string.settings_time_format_12h_desc)
                    TimeFormat.HOUR_24 -> stringResource(R.string.settings_time_format_24h_desc)
                }
                val icon = when (format) {
                    TimeFormat.SYSTEM -> Icons.Outlined.PhoneAndroid
                    TimeFormat.HOUR_12, TimeFormat.HOUR_24 -> Icons.Outlined.Schedule
                }
                SegmentedListItem(
                    modifier = Modifier.testTag("time-format-${format.name.lowercase()}"),
                    selected = currentFormat == format,
                    onClick = { onFormatChange(format) },
                    shapes = stableSegmentedShapes(index, TimeFormat.entries.size),
                    colors = settingsListItemColors(),
                    leadingContent = { Icon(imageVector = icon, contentDescription = null) },
                    trailingContent = {
                        RadioButton(selected = currentFormat == format, onClick = null)
                    },
                    supportingContent = { Text(description) }
                ) { Text(label) }
            }
        }
    }
}
