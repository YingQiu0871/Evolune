package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette

/**
 * Appearance section: ThemeMode, the canonical 配色 control and time format. The two
 * single-choice settings are compact segmented rows; each control keeps its existing state owner.
 */
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_appearance_format_title))
        SingleChoiceRow(
            title = stringResource(R.string.settings_theme_mode_title),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            tagPrefix = "theme-mode",
            label = { mode ->
                when (mode) {
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_mode_light)
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_mode_dark)
                    ThemeMode.AMOLED -> stringResource(R.string.settings_theme_mode_amoled)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_mode_system)
                }
            },
            onSelect = onThemeModeChange
        )
        SettingsColorSchemeSection(
            source = settings.themeColorSource,
            preset = settings.themePreset,
            onSelectDynamicSource = onSelectDynamicSource,
            onSelectPresetSource = onSelectPresetSource,
            onPresetPaletteChange = onPresetPaletteChange
        )
        SingleChoiceRow(
            title = stringResource(R.string.settings_time_format_title),
            options = TimeFormat.entries,
            selected = settings.timeFormat,
            tagPrefix = "time-format",
            label = { format ->
                when (format) {
                    TimeFormat.SYSTEM -> stringResource(R.string.settings_time_format_system)
                    TimeFormat.HOUR_12 -> stringResource(R.string.settings_time_format_12h)
                    TimeFormat.HOUR_24 -> stringResource(R.string.settings_time_format_24h)
                }
            },
            onSelect = onTimeFormatChange
        )
    }
}

/** A titled single-choice segmented row; each segment is tagged `<tagPrefix>-<enum name>`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> SingleChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    tagPrefix: String,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSubsectionTitle(title = title)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val suffix = option.name.lowercase()
                SegmentedButton(
                    modifier = Modifier.testTag("$tagPrefix-$suffix"),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {}
                ) {
                    Text(
                        text = label(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("$tagPrefix-label-$suffix")
                    )
                }
            }
        }
    }
}
