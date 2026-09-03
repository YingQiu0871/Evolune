package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R

/** Canonical, local and re-openable disclosure surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclosuresScreen(
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null
) {
    if (onNavigateBack != null) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.disclosures_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            DisclosureContent(Modifier.padding(padding))
        }
    } else {
        DisclosureContent(modifier)
    }
}

@Composable
private fun DisclosureContent(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("disclosures-screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        DisclosureSection(
            title = stringResource(R.string.disclosure_terms_title),
            body = stringResource(R.string.disclosure_terms_content),
            tag = "disclosure-terms"
        )
        DisclosureSection(
            title = stringResource(R.string.disclosure_privacy_title),
            body = stringResource(R.string.disclosure_privacy_content),
            tag = "disclosure-privacy"
        )
        DisclosureSection(
            title = stringResource(R.string.disclosure_medical_pk_title),
            body = stringResource(R.string.disclosure_medical_pk_content),
            tag = "disclosure-medical-pk"
        )
    }
}

@Composable
private fun DisclosureSection(title: String, body: String, tag: String) {
    Column(
        modifier = Modifier.testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
