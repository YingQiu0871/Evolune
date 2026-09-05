package io.github.yingqiu0871.evolune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.OnboardingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingFlowScreen(
    state: OnboardingState,
    onAcceptTerms: () -> Unit,
    onAcknowledgeMedicalPkDisclosure: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    beginnerOnboarding: Boolean = true,
    onOpenDisclosures: () -> Unit = {},
    onExit: (() -> Unit)? = null,
    showTopBar: Boolean = true
) {
    val pageCount = if (beginnerOnboarding) 5 else 2
    var page by rememberSaveable(beginnerOnboarding) { mutableIntStateOf(0) }
    val contentPage = if (beginnerOnboarding) page else page + 2
    val termsAccepted = state.hasAcceptedTerms
    val medicalPkAcknowledged = state.hasAcknowledgedMedicalPkDisclosure
    val canProceed = when (contentPage) {
        2 -> termsAccepted
        3 -> medicalPkAcknowledged
        else -> true
    }
    val canFinish = termsAccepted && medicalPkAcknowledged

    BackHandler(enabled = onExit != null) { onExit?.invoke() }

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .testTag("onboarding-flow"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.onboarding_step, page + 1, pageCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            LinearProgressIndicator(
                progress = { (page + 1) / pageCount.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding-progress")
            )

            when (contentPage) {
                0 -> OnboardingWelcomePage()
                1 -> OnboardingBoundariesPage(onOpenDisclosures)
                2 -> OnboardingTermsPage(termsAccepted, onAcceptTerms, onOpenDisclosures)
                3 -> OnboardingMedicalPkPage(
                    acknowledged = medicalPkAcknowledged,
                    onAcknowledge = onAcknowledgeMedicalPkDisclosure,
                    onOpenDisclosures = onOpenDisclosures
                )
                else -> OnboardingCapabilitiesPage()
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { page -= 1 },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
            if (page < pageCount - 1) {
                    Button(
                        onClick = { page += 1 },
                        enabled = canProceed,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding-next")
                    ) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        enabled = canFinish,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("onboarding-finish")
                    ) {
                        Text(stringResource(R.string.onboarding_finish))
                    }
                }
            }
            if (onExit != null) {
                TextButton(
                    onClick = onExit,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("onboarding-close")
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        }
    }

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) })
            },
            modifier = modifier
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) { content() }
        }
    } else {
        Column(modifier = modifier) { content() }
    }
}

@Composable
private fun OnboardingWelcomePage() {
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_content),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun OnboardingBoundariesPage(onOpenDisclosures: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_boundaries_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = stringResource(R.string.onboarding_boundaries_content),
        style = MaterialTheme.typography.bodyLarge
    )
    TextButton(onClick = onOpenDisclosures, modifier = Modifier.testTag("onboarding-open-disclosures")) {
        Text(stringResource(R.string.onboarding_review_disclosures))
    }
}

@Composable
private fun OnboardingTermsPage(
    accepted: Boolean,
    onAccept: () -> Unit,
    onOpenDisclosures: () -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_terms_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = stringResource(R.string.disclosure_terms_content),
        style = MaterialTheme.typography.bodyLarge
    )
    Text(
        text = stringResource(R.string.disclosure_privacy_title),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = stringResource(R.string.disclosure_privacy_content),
        style = MaterialTheme.typography.bodyLarge
    )
    TextButton(onClick = onOpenDisclosures, modifier = Modifier.testTag("onboarding-review-terms")) {
        Text(stringResource(R.string.onboarding_review_full_text))
    }
    AcknowledgementRow(
        checked = accepted,
        onCheckedChange = onAccept,
        label = stringResource(R.string.onboarding_terms_acknowledgement),
        tag = "onboarding-terms-checkbox"
    )
}

@Composable
private fun OnboardingMedicalPkPage(
    acknowledged: Boolean,
    onAcknowledge: () -> Unit,
    onOpenDisclosures: () -> Unit
) {
    Text(
        text = stringResource(R.string.onboarding_medical_pk_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = stringResource(R.string.disclosure_medical_pk_content),
        style = MaterialTheme.typography.bodyLarge
    )
    TextButton(onClick = onOpenDisclosures, modifier = Modifier.testTag("onboarding-review-medical-pk")) {
        Text(stringResource(R.string.onboarding_review_full_text))
    }
    AcknowledgementRow(
        checked = acknowledged,
        onCheckedChange = onAcknowledge,
        label = stringResource(R.string.onboarding_medical_pk_acknowledgement),
        tag = "onboarding-medical-pk-checkbox"
    )
}

@Composable
private fun OnboardingCapabilitiesPage() {
    Text(
        text = stringResource(R.string.onboarding_capabilities_title),
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = stringResource(R.string.onboarding_capabilities_content),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun AcknowledgementRow(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    label: String,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (it) onCheckedChange() },
            modifier = Modifier.testTag("${tag}-control")
        )
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
