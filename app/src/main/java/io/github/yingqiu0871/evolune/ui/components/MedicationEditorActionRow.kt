package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R

@Composable
internal fun MedicationEditorActionRow(
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    actionsEnabled: Boolean,
    saveEnabled: Boolean,
    deleteTag: String,
    cancelTag: String,
    saveTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onDelete != null) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag(deleteTag),
                enabled = actionsEnabled,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.common_delete),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
            }
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag(cancelTag),
            enabled = actionsEnabled,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.common_cancel),
                maxLines = 1,
                softWrap = false
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .testTag(saveTag),
            enabled = saveEnabled,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.common_save),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
