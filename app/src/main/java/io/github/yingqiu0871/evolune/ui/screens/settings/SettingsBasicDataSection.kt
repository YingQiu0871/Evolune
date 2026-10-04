package io.github.yingqiu0871.evolune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.data.isValidBodyWeight
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.ui.components.SettingsListItemPosition
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors
import io.github.yingqiu0871.evolune.ui.components.stableSegmentedShapes

/**
 * Concentration-estimate section: everything that feeds or shapes the PK calculation on one
 * place — body weight, its optional Health Connect source, the optional CPA curve and the
 * optional lab calibration of the E2 curve. The
 * persisted authority remains SettingsViewModel/SettingsDataStore; only the text-field
 * interaction state is local.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsBasicDataSection(
    settings: UserSettings,
    healthConnectWeightSyncState: HealthConnectWeightSyncState,
    onBodyWeightChange: (Double) -> Unit,
    onWeightSyncEnabledChange: (Boolean) -> Unit,
    onReauthorize: () -> Unit,
    onManagePermissions: () -> Unit,
    onShowCpaCurveChange: (Boolean) -> Unit,
    onCalibrateE2CurveChange: (Boolean) -> Unit
) {
    val bodyWeight = settings.bodyWeight
    var weightText by remember(bodyWeight) { mutableStateOf(bodyWeight.toString()) }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings-basic-data-section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsSectionHeader(title = stringResource(R.string.settings_basic_data_title))

        OutlinedTextField(
            value = weightText,
            onValueChange = { newValue ->
                weightText = newValue
                val weight = newValue.toDoubleOrNull()
                if (weight != null && isValidBodyWeight(weight)) {
                    isError = false
                    onBodyWeightChange(weight)
                } else {
                    isError = true
                }
            },
            label = { Text(stringResource(R.string.settings_weight_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isError,
            supportingText = {
                Text(
                    stringResource(
                        if (isError) R.string.settings_weight_error else R.string.settings_weight_desc
                    )
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-weight-input")
        )

        SettingsHealthConnectSection(
            settings = settings,
            state = healthConnectWeightSyncState,
            onWeightSyncEnabledChange = onWeightSyncEnabledChange,
            onReauthorize = onReauthorize,
            onManagePermissions = onManagePermissions
        )

        // v1.10 (S1/S8) — optional CPA estimated curve toggle. Default off; copy discloses the
        // estimate/peak-underestimation caveat so the switch is never mistaken for a lab result.
        SegmentedListItem(
            modifier = Modifier.testTag("settings-show-cpa-curve"),
            onClick = { onShowCpaCurveChange(!settings.showCpaCurve) },
            shapes = stableSegmentedShapes(SettingsListItemPosition.SINGLE),
            colors = settingsListItemColors(),
            leadingContent = {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ShowChart, contentDescription = null)
            },
            trailingContent = {
                Switch(checked = settings.showCpaCurve, onCheckedChange = onShowCpaCurveChange)
            },
            supportingContent = {
                Text(stringResource(R.string.settings_show_cpa_curve_desc))
            }
        ) { Text(stringResource(R.string.settings_show_cpa_curve_title)) }

        // PK 2.0 slice 4 — lab-based calibration of the Home E2 curve. Default off; copy says
        // the calibrated curve is still a model estimate.
        SegmentedListItem(
            modifier = Modifier.testTag("settings-calibrate-e2-curve"),
            onClick = { onCalibrateE2CurveChange(!settings.calibrateE2Curve) },
            shapes = stableSegmentedShapes(SettingsListItemPosition.SINGLE),
            colors = settingsListItemColors(),
            leadingContent = {
                Icon(imageVector = Icons.Outlined.Science, contentDescription = null)
            },
            trailingContent = {
                Switch(checked = settings.calibrateE2Curve, onCheckedChange = onCalibrateE2CurveChange)
            },
            supportingContent = {
                Text(stringResource(R.string.settings_calibrate_e2_curve_desc))
            }
        ) { Text(stringResource(R.string.settings_calibrate_e2_curve_title)) }
    }
}
