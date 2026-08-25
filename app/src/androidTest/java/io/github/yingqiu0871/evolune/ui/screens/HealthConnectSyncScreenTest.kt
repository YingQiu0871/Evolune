package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasNoClickAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncStatus
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class HealthConnectSyncScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pageShowsWeightToggleMedicationPlaceholderAndPermissionEntry() {
        setScreen()

        composeRule.onNodeWithTag("health-connect-sync-title").assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-weight-sync-switch").assertIsOff()
        composeRule.onNodeWithTag("health-connect-medication-sync-row").performScrollTo()
        composeRule.onNodeWithTag(
            "health-connect-medication-sync-switch",
            useUnmergedTree = true
        ).assert(hasNoClickAction())
        composeRule.onNodeWithText("暂未开放，计划在 v1.8 评估").assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-manage-permissions").performScrollTo()
        composeRule.onNodeWithTag("health-connect-manage-permissions").assertIsDisplayed()
    }

    @Test
    fun settingsHomeRoutesSyncAndBackupWithoutDirectFeatureControls() {
        var opened = false
        composeRule.setContent {
            EvoluneTheme {
                SettingsScreen(
                    settings = UserSettings(),
                    onBodyWeightChange = {},
                    onThemeModeChange = {},
                    onColorThemeChange = {},
                    onTimeFormatChange = {},
                    onAutoCheckUpdatesChange = {},
                    onCheckForUpdates = {},
                    updateCheckResult = UpdateCheckResult.Idle,
                    onOpenSyncAndBackup = { opened = true },
                    showTopBar = false
                )
            }
        }
        composeRule.onNodeWithTag("settings-sync-backup-entry").performScrollTo()
        composeRule.onNodeWithTag("settings-sync-backup-entry").assertIsDisplayed()
        composeRule.onNodeWithText("同步与备份").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("settings-health-connect-sync-entry")
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("立即备份").fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("从备份恢复").fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("settings-sync-backup-entry").performClick()
        composeRule.runOnIdle { check(opened) }
    }

    @Test
    fun permissionRequiredEnabledStateOffersReauthorization() {
        setScreen(
            settings = UserSettings(healthConnectWeightSyncEnabled = true),
            state = HealthConnectWeightSyncState(
                status = HealthConnectWeightSyncStatus.PERMISSION_REQUIRED
            )
        )

        composeRule.onNodeWithTag("health-connect-weight-sync-switch").assertIsOn()
        composeRule.onNodeWithTag("health-connect-reauthorize").performScrollTo()
        composeRule.onNodeWithTag("health-connect-reauthorize").assertIsDisplayed()
    }

    @Test
    fun connectedStateShowsAdoptedMetadataAndNoDataStateIsExplicit() {
        setScreen(
            state = HealthConnectWeightSyncState(
                status = HealthConnectWeightSyncStatus.NO_DATA,
                lastWeightKg = 62.5,
                lastAdoptedAt = Instant.parse("2026-08-23T00:00:00Z")
            )
        )

        composeRule.onNodeWithTag("health-connect-sync-last").performScrollTo()
        composeRule.onNodeWithTag("health-connect-sync-last").assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-sync-no-data").assertIsDisplayed()
    }

    @Test
    fun unavailableStateIsDisplayed() {
        setScreen(
            state = HealthConnectWeightSyncState(
                status = HealthConnectWeightSyncStatus.UNAVAILABLE
            )
        )
        composeRule.onNodeWithText("不可用").assertIsDisplayed()
    }

    @Test
    fun updateRequiredStateIsDisplayed() {
        setScreen(
            state = HealthConnectWeightSyncState(
                status = HealthConnectWeightSyncStatus.UPDATE_REQUIRED
            )
        )
        composeRule.onNodeWithText("需要更新").assertIsDisplayed()
    }

    private fun setScreen(
        settings: UserSettings = UserSettings(),
        state: HealthConnectWeightSyncState = HealthConnectWeightSyncState()
    ) {
        composeRule.setContent {
            EvoluneTheme {
                HealthConnectSyncScreen(
                    settings = settings,
                    state = state,
                    onWeightSyncEnabledChange = {},
                    onReauthorize = {},
                    onManagePermissions = {}
                )
            }
        }
        composeRule.waitForIdle()
    }
}
