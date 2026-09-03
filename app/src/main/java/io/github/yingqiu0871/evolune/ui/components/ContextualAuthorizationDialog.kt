package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import io.github.yingqiu0871.evolune.R

/** Shared explanation step used immediately before an external authorization UI. */
@Composable
fun ContextualAuthorizationDialog(
    visible: Boolean,
    title: String,
    message: String,
    onContinue: () -> Unit,
    onNotNow: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onNotNow,
        modifier = Modifier.testTag("contextual-authorization-dialog"),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(stringResource(R.string.contextual_authorization_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNow) {
                Text(stringResource(R.string.contextual_authorization_not_now))
            }
        }
    )
}
