package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
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
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes

/**
 * v1.7.2 Slice C — Appearance & format inline section: ThemeMode, the canonical 配色 control
 * and time format. Each control keeps its existing state owner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsAppearanceSection(
    settings: UserSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onSelectDynamicSource: () -> Unit,
    onSelectPresetSource: () -> Unit,
    onPresetPaletteChange: (PresetPalette) -> Unit,
    onTimeFormatChange: (TimeFormat) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-appearance-section"),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance_format_title))
        ThemeModeSection(currentMode = settings.themeMode, onModeChange = onThemeModeChange)
        SettingsColorSchemeSection(
            source = settings.themeColorSource,
            preset = settings.themePreset,
            onSelectDynamicSource = onSelectDynamicSource,
            onSelectPresetSource = onSelectPresetSource,
            onPresetPaletteChange = onPresetPaletteChange
        )
        TimeFormatSection(
            currentFormat = settings.timeFormat,
            onFormatChange = onTimeFormatChange
        )
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
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TimeFormatSection(
    currentFormat: TimeFormat,
    onFormatChange: (TimeFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_time_format_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
