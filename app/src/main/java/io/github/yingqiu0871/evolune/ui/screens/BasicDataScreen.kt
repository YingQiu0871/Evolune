package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import io.github.yingqiu0871.evolune.data.isValidBodyWeight

@Composable
fun BasicDataScreen(
    bodyWeight: Double,
    onBodyWeightChange: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-basic-data-screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        BodyWeightSection(
            bodyWeight = bodyWeight,
            onBodyWeightChange = onBodyWeightChange
        )
    }
}

@Composable
private fun BodyWeightSection(
    bodyWeight: Double,
    onBodyWeightChange: (Double) -> Unit
) {
    var weightText by remember(bodyWeight) { mutableStateOf(bodyWeight.toString()) }
    var isError by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.settings_weight_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_weight_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

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
                if (isError) {
                    Text(stringResource(R.string.settings_weight_error))
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings-weight-input")
        )
    }
}
