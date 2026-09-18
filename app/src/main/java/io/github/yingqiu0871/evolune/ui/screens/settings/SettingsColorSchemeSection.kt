package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.theme.palette.PaletteCatalog
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes

/** Single UI metadata mapping from the frozen Slice A palette identities to the widget labels. */
internal fun PresetPalette.paletteLabelRes(): Int = when (this) {
    PresetPalette.MONET_BLUE -> R.string.widget_config_palette_blue
    PresetPalette.MONET_VIOLET -> R.string.widget_config_palette_violet
    PresetPalette.MONET_SAKURA -> R.string.widget_config_palette_sakura
    PresetPalette.MONET_MINT -> R.string.widget_config_palette_mint
    PresetPalette.MONET_TEAL -> R.string.widget_config_palette_teal
    PresetPalette.MONET_AMBER -> R.string.widget_config_palette_amber
    PresetPalette.MONET_NEUTRAL -> R.string.widget_config_palette_neutral
    PresetPalette.MONET_LAVENDER -> R.string.widget_config_palette_lavender
}

/**
 * v1.7.2 Slice C — 配色 section consuming the frozen Slice B canonical state only.
 *
 * - source rows: DYNAMIC (跟随壁纸) / PRESET (预设配色)
 * - exactly the eight [PresetPalette] tiles, previewed from [PaletteCatalog] seeds
 * - LEGACY_BUILTIN is a truthful compatibility row, never a ninth tile
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsColorSchemeSection(
    source: ThemeColorSource,
    preset: ThemePresetSelection?,
    onSelectDynamicSource: () -> Unit,
    onSelectPresetSource: () -> Unit,
    onPresetPaletteChange: (PresetPalette) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-color-scheme-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_color_scheme_title))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                modifier = Modifier.testTag("color-source-dynamic"),
                selected = source == ThemeColorSource.DYNAMIC,
                onClick = onSelectDynamicSource,
                shapes = stableSegmentedShapes(index = 0, count = 2),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.ColorLens, contentDescription = null)
                },
                trailingContent = {
                    RadioButton(selected = source == ThemeColorSource.DYNAMIC, onClick = null)
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_color_theme_dynamic_desc))
                }
            ) { Text(stringResource(R.string.settings_color_source_dynamic)) }

            SegmentedListItem(
                modifier = Modifier.testTag("color-source-preset"),
                selected = source == ThemeColorSource.PRESET,
                onClick = {
                    if (source != ThemeColorSource.PRESET) onSelectPresetSource()
                },
                shapes = stableSegmentedShapes(index = 1, count = 2),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.Palette, contentDescription = null)
                },
                trailingContent = {
                    RadioButton(selected = source == ThemeColorSource.PRESET, onClick = null)
                },
                supportingContent = {
                    Text(stringResource(R.string.settings_color_source_preset_desc))
                }
            ) { Text(stringResource(R.string.settings_color_source_preset)) }
        }

        if (preset == ThemePresetSelection.LegacyBuiltin) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("color-legacy-builtin-current"),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = stringResource(R.string.settings_color_legacy_builtin_current),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        val selectedPalette = (preset as? ThemePresetSelection.Preset)?.palette
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-palette-grid"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetPalette.entries.chunked(4).forEach { rowPalettes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPalettes.forEach { palette ->
                        PaletteTile(
                            palette = palette,
                            selected = selectedPalette == palette,
                            onSelect = { onPresetPaletteChange(palette) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaletteTile(
    palette: PresetPalette,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(palette.paletteLabelRes())
    val seed = PaletteCatalog.seed(palette)
    Surface(
        modifier = modifier
            .testTag("palette-tile-${palette.name.lowercase()}")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(seed.lightPrimary, seed.lightSecondary, seed.lightTertiary).forEach { argb ->
                    Surface(
                        modifier = Modifier.size(12.dp),
                        shape = CircleShape,
                        color = Color(argb)
                    ) {}
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
