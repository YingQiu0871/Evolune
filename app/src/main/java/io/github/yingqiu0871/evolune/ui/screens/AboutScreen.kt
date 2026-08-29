package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.ui.components.settingsListItemColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    onOpenWebsite: (() -> Unit)? = null,
    onContactDeveloper: (() -> Unit)? = null
) {
    var showCopyrightDialog by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val websiteUrl = stringResource(R.string.about_website_url)
    val developerContactUri = stringResource(R.string.about_developer_contact_uri)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings-about-screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.about_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SegmentedListItem(
                modifier = Modifier.testTag("settings-about-website"),
                onClick = { onOpenWebsite?.invoke() ?: uriHandler.openUri(websiteUrl) },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 4),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.Language, contentDescription = null)
                },
                supportingContent = { Text(websiteUrl) }
            ) { Text(stringResource(R.string.about_website_button)) }

            SegmentedListItem(
                modifier = Modifier.testTag("settings-about-developer-contact"),
                onClick = {
                    onContactDeveloper?.invoke() ?: uriHandler.openUri(developerContactUri)
                },
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 4),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.Email, contentDescription = null)
                },
                supportingContent = {
                    Text(stringResource(R.string.about_developer_contact_email))
                }
            ) { Text(stringResource(R.string.about_developer_contact_button)) }

            SegmentedListItem(
                modifier = Modifier.testTag("settings-about-copyright"),
                onClick = { showCopyrightDialog = true },
                shapes = ListItemDefaults.segmentedShapes(index = 2, count = 4),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.Info, contentDescription = null)
                }
            ) { Text(stringResource(R.string.about_copyright_button)) }

            SegmentedListItem(
                modifier = Modifier.testTag("settings-about-disclaimer"),
                onClick = { showDisclaimerDialog = true },
                shapes = ListItemDefaults.segmentedShapes(index = 3, count = 4),
                colors = settingsListItemColors(),
                leadingContent = {
                    Icon(imageVector = Icons.Outlined.WarningAmber, contentDescription = null)
                }
            ) { Text(stringResource(R.string.about_disclaimer_button)) }
        }
    }

    if (showCopyrightDialog) {
        AlertDialog(
            onDismissRequest = { showCopyrightDialog = false },
            title = { Text(stringResource(R.string.about_copyright_title)) },
            text = { Text(stringResource(R.string.about_copyright_content)) },
            confirmButton = {
                TextButton(onClick = { showCopyrightDialog = false }) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        )
    }

    if (showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { showDisclaimerDialog = false },
            title = { Text(stringResource(R.string.about_disclaimer_title)) },
            text = { Text(stringResource(R.string.about_disclaimer_content)) },
            confirmButton = {
                TextButton(onClick = { showDisclaimerDialog = false }) {
                    Text(stringResource(R.string.dialog_close))
                }
            }
        )
    }
}
