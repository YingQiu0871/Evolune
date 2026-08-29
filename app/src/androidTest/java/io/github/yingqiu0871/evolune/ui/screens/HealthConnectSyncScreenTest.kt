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
                    onOpenBasicData = {},
                    onOpenAppearanceAndFormat = {},
                    onOpenSyncAndBackup = { opened = true },
                    onOpenUpdate = {},
                    onOpenAbout = {},
                    showTopBar = false
                )
            }
        }
        composeRule.onNodeWithTag("settings-basic-data-entry").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-appearance-format-entry").performScrollTo()
        composeRule.onNodeWithTag("settings-appearance-format-entry").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-sync-backup-entry").performScrollTo()
        composeRule.onNodeWithTag("settings-sync-backup-entry").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-update-entry").performScrollTo()
        composeRule.onNodeWithTag("settings-update-entry").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-about-entry").performScrollTo()
        composeRule.onNodeWithTag("settings-about-entry").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("settings-basic-data-entry")
                .fetchSemanticsNodes().size == 1
        )
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
        assertTrue(
            composeRule.onAllNodesWithTag("settings-weight-input")
                .fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithTag("settings-auto-check-updates")
                .fetchSemanticsNodes().isEmpty()
        )
        listOf(
            "体重 (kg)",
            "自动检查更新",
            "检查更新",
            "版权信息",
            "免责声明",
            "浅色",
            "动态着色",
            "12小时制"
        ).forEach { text ->
            assertTrue(
                "Direct Settings control leaked onto the navigation hub: $text",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
            )
        }
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
