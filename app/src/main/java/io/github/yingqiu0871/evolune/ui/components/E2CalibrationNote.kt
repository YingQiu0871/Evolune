package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import java.util.Locale

/**
 * PK 2.0 — states whether the E2 curve above is lab-calibrated, by how much, and that it is
 * still a model estimate. Shared by Home (slice 4) and the retrospective surface (slice 5a);
 * only shown while the setting is on. [labCount] 0 means no lab could be compared.
 */
@Composable
fun E2CalibrationNote(
    labCount: Int,
    scale: Double,
    fitErrorPct: Double?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("e2-calibration-note"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (labCount == 0) {
                Text(
                    text = stringResource(R.string.e2_calibration_unavailable_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.e2_calibration_unavailable_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = stringResource(R.string.e2_calibration_applied_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(
                        R.string.e2_calibration_applied_body,
                        labCount,
                        String.format(Locale.ROOT, "%.2f", scale)
                    ),
                    modifier = Modifier.testTag("e2-calibration-scale"),
                    style = MaterialTheme.typography.bodyMedium
                )
                fitErrorPct?.let { errorPct ->
                    Text(
                        text = stringResource(
                            R.string.e2_calibration_fit_error,
                            String.format(Locale.ROOT, "%.0f", errorPct)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = stringResource(R.string.e2_calibration_markers),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = stringResource(R.string.e2_calibration_disclaimer),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
