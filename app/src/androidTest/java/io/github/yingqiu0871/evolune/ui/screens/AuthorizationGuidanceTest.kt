package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.res.stringResource
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.backup.BackupRestoreUiState
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.ui.components.ContextualAuthorizationDialog
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthorizationGuidanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun healthConnectActionWaitsForExplanationDecision() {
        var guidanceVisible by mutableStateOf(false)
        var providerInvoked by mutableStateOf(false)
        composeRule.setContent {
            EvoluneTheme {
                HealthConnectSyncScreen(
                    settings = UserSettings(),
                    state = HealthConnectWeightSyncState(),
                    onWeightSyncEnabledChange = { enabled ->
                        if (enabled) guidanceVisible = true
                    },
                    onReauthorize = {},
                    onManagePermissions = {}
                )
                ContextualAuthorizationDialog(
                    visible = guidanceVisible,
                    title = stringResource(R.string.contextual_health_connect_title),
                    message = stringResource(R.string.contextual_health_connect_message),
                    onContinue = {
                        guidanceVisible = false
                        providerInvoked = true
                    },
                    onNotNow = { guidanceVisible = false }
                )
            }
        }

        composeRule.onNodeWithTag("health-connect-weight-sync-switch").performClick()
        composeRule.onNodeWithText(stringResourceForTest(R.string.contextual_health_connect_title))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(providerInvoked) }
        composeRule.onNodeWithText("暂不").performClick()
        composeRule.runOnIdle { assertFalse(providerInvoked) }

        composeRule.onNodeWithTag("health-connect-weight-sync-switch").performClick()
        composeRule.onNodeWithText("继续").performClick()
        composeRule.runOnIdle { assertTrue(providerInvoked) }
    }

    @Test
    fun disconnectedGoogleDriveActionWaitsForExplanationDecision() {
        var guidanceVisible by mutableStateOf(false)
        var providerInvoked by mutableStateOf(false)
        composeRule.setContent {
            EvoluneTheme {
                GoogleDriveBackupRestoreScreen(
                    connected = false,
                    state = BackupRestoreUiState.Idle,
                    onBackupNow = { guidanceVisible = true },
                    onRestoreFromBackup = {},
                    onDisconnect = {},
                    onSelectGeneration = {},
                    onSubmitBackupPassphrase = { _, _ -> },
                    onSubmitRestorePassphrase = {},
                    onConfirmRestore = {},
                    onCancel = {},
                    onDismissMessage = {}
                )
                ContextualAuthorizationDialog(
                    visible = guidanceVisible,
                    title = stringResource(R.string.contextual_google_drive_title),
                    message = stringResource(R.string.contextual_google_drive_message),
                    onContinue = {
                        guidanceVisible = false
                        providerInvoked = true
                    },
                    onNotNow = { guidanceVisible = false }
                )
            }
        }

        composeRule.onNodeWithTag("google-drive-backup-now").performClick()
        composeRule.onNodeWithText(stringResourceForTest(R.string.contextual_google_drive_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText("暂不").performClick()
        composeRule.runOnIdle { assertFalse(providerInvoked) }

        composeRule.onNodeWithTag("google-drive-backup-now").performClick()
        composeRule.onNodeWithText("继续").performClick()
        composeRule.runOnIdle { assertTrue(providerInvoked) }
    }

    private fun stringResourceForTest(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
