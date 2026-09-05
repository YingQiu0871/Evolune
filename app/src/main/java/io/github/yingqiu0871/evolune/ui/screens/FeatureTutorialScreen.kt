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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * A presentation-only tour of Evolune's main features.
 *
 * The optional actions delegate to the existing application entry points. The
 * tour never creates domain data, requests permissions, or changes settings by
 * itself.
 */
@Composable
fun FeatureTutorialScreen(
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onCreatePlan: () -> Unit = {},
    onRecordDose: () -> Unit = {},
    onOpenPkChart: () -> Unit = {},
    onOpenBackup: () -> Unit = {}
) {
    val steps = listOf(
        FeatureTutorialStep(
            R.string.feature_tutorial_plan_title,
            R.string.feature_tutorial_plan_content
        ),
        FeatureTutorialStep(
            R.string.feature_tutorial_dose_title,
            R.string.feature_tutorial_dose_content
        ),
        FeatureTutorialStep(
            R.string.feature_tutorial_pk_title,
            R.string.feature_tutorial_pk_content
        ),
        FeatureTutorialStep(
            R.string.feature_tutorial_widget_title,
            R.string.feature_tutorial_widget_content
        ),
        FeatureTutorialStep(
            R.string.feature_tutorial_wear_title,
            R.string.feature_tutorial_wear_content
        ),
        FeatureTutorialStep(
            R.string.feature_tutorial_backup_title,
            R.string.feature_tutorial_backup_content
        )
    )
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val isLastStep = stepIndex == steps.lastIndex

    BackHandler { onSkip() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .testTag("feature-tutorial"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(
                R.string.feature_tutorial_step,
                stepIndex + 1,
                steps.size
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("feature-tutorial-step")
        )
        LinearProgressIndicator(
            progress = { (stepIndex + 1) / steps.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("feature-tutorial-progress")
        )
        Text(
            text = stringResource(steps[stepIndex].title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("feature-tutorial-step-title")
        )
        Text(
            text = stringResource(steps[stepIndex].content),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("feature-tutorial-step-content")
        )

        when (stepIndex) {
            0 -> FeatureTutorialAction(
                text = stringResource(R.string.feature_tutorial_create_plan),
                tag = "feature-tutorial-create-plan",
                onClick = onCreatePlan
            )
            1 -> FeatureTutorialAction(
                text = stringResource(R.string.feature_tutorial_record_dose),
                tag = "feature-tutorial-record-dose",
                onClick = onRecordDose
            )
            2 -> FeatureTutorialAction(
                text = stringResource(R.string.feature_tutorial_open_pk),
                tag = "feature-tutorial-open-pk",
                onClick = onOpenPkChart
            )
            5 -> FeatureTutorialAction(
                text = stringResource(R.string.feature_tutorial_open_backup),
                tag = "feature-tutorial-open-backup",
                onClick = onOpenBackup
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepIndex > 0) {
                OutlinedButton(
                    onClick = { stepIndex -= 1 },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("feature-tutorial-back")
                ) {
                    Text(stringResource(R.string.feature_tutorial_back))
                }
            }
            Button(
                onClick = { if (isLastStep) onFinish() else stepIndex += 1 },
                modifier = Modifier
                    .weight(1f)
                    .testTag(
                        if (isLastStep) "feature-tutorial-finish" else "feature-tutorial-next"
                    )
            ) {
                Text(
                    stringResource(
                        if (isLastStep) {
                            R.string.feature_tutorial_finish
                        } else {
                            R.string.feature_tutorial_next
                        }
                    )
                )
            }
        }
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag("feature-tutorial-skip")
        ) {
            Text(stringResource(R.string.feature_tutorial_skip))
        }
    }
}

private data class FeatureTutorialStep(
    val title: Int,
    val content: Int
)

@Composable
private fun FeatureTutorialAction(
    text: String,
    tag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Text(text)
    }
}
