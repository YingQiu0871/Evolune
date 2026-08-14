package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun <T> MedicationOptionGrid(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    optionTag: (T) -> String,
    compactColumns: Int,
    expandedColumns: Int = compactColumns,
    itemHeight: Dp = 56.dp
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = if (maxWidth >= 600.dp) expandedColumns else compactColumns
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(columnCount).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowOptions.forEach { option ->
                        FilterChip(
                            selected = selectedOption == option,
                            onClick = { onOptionSelected(option) },
                            label = {
                                Text(
                                    text = optionLabel(option),
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Clip
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(itemHeight)
                                .testTag(optionTag(option)),
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                    repeat(columnCount - rowOptions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
