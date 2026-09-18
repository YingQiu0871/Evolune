package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.data.TimeFormat
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsCategoryScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun flattenedSettingsExposeAllInlineSectionsAndRetainedRows() {
        setSettingsContent()

        listOf(
            "settings-basic-data-section",
            "settings-appearance-section",
            "settings-color-scheme-section",
            "settings-import-export-block",
            "settings-health-connect-section",
            "settings-update-section",
            "settings-navigation-rows"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertExists()
        }
        listOf(
            "settings-basic-data-entry",
            "settings-appearance-format-entry",
            "settings-sync-backup-entry",
            "settings-update-entry"
        ).forEach { oldTag ->
            assertTrue(
                "Former hub entry must not exist: $oldTag",
                composeRule.onAllNodesWithTag(oldTag).fetchSemanticsNodes().isEmpty()
            )
        }

        composeRule.onNodeWithTag("settings-about-entry").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-guide-entry").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-privacy-entry").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-feature-tutorial-entry")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun basicDataInlineKeepsBodyWeightInputAndValidationCallback() {
        val weights = mutableListOf<Double>()
        setSettingsContent(onBodyWeightChange = { weights += it })

        composeRule.onNodeWithTag("settings-basic-data-section").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-weight-input").performTextClearance()
        composeRule.onNodeWithTag("settings-weight-input").performTextInput("72.5")
        composeRule.runOnIdle {
            assertEquals(72.5, weights.last(), 0.0)
        }
    }

    @Test
    fun appearanceInlineKeepsThemeModeTimeAndCanonicalColorCallbacks() {
        val selectedModes = mutableListOf<ThemeMode>()
        val selectedFormats = mutableListOf<TimeFormat>()
        val dynamicSelections = mutableListOf<Unit>()
        val presetEntries = mutableListOf<Unit>()
        val paletteSelections = mutableListOf<PresetPalette>()
        setSettingsContent(
            onThemeModeChange = { selectedModes += it },
            onSelectDynamicSource = { dynamicSelections += Unit },
            onSelectPresetSource = { presetEntries += Unit },
            onPresetPaletteChange = { paletteSelections += it },
            onTimeFormatChange = { selectedFormats += it }
        )

        composeRule.onNodeWithTag("theme-mode-dark").performScrollTo().performClick()
        composeRule.onNodeWithTag("time-format-hour_24").performScrollTo().performClick()
        composeRule.onNodeWithTag("color-source-preset").performScrollTo().performClick()
        composeRule.onNodeWithTag("color-source-dynamic").performScrollTo().performClick()
        composeRule.onNodeWithTag("palette-tile-monet_blue").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(ThemeMode.DARK), selectedModes)
            assertEquals(listOf(TimeFormat.HOUR_24), selectedFormats)
            assertEquals(1, presetEntries.size)
            assertEquals(1, dynamicSelections.size)
            assertEquals(listOf(PresetPalette.MONET_BLUE), paletteSelections)
        }
    }

    @Test
    fun updateInlineKeepsToggleCheckAndVersionActions() {
        val autoCheckValues = mutableListOf<Boolean>()
        var checkCount = 0
        setSettingsContent(
            userSettings = UserSettings(autoCheckUpdates = false),
            onAutoCheckUpdatesChange = { autoCheckValues += it },
            onCheckForUpdates = { checkCount += 1 }
        )

        composeRule.onNodeWithTag("settings-update-section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-auto-check-updates").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-check-updates-now").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-current-version").performScrollTo()
            .assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(true), autoCheckValues)
            assertEquals(1, checkCount)
        }
    }

    @Test
    fun syncBackupInlineKeepsHealthConnectAndDriveWiring() {
        val weightToggles = mutableListOf<Boolean>()
        var driveOpened = 0
        setSettingsContent(
            onHealthConnectWeightSyncEnabledChange = { weightToggles += it },
            onOpenGoogleDrive = { driveOpened += 1 }
        )

        composeRule.onNodeWithTag("settings-import-export-block").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-weight-sync-switch").performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry")
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(true), weightToggles)
            assertEquals(1, driveOpened)
        }
    }

    @Test
    fun retainedNavigationRowsOpenTheirDestinations() {
        val opened = mutableListOf<String>()
        setSettingsContent(
            onOpenGuide = { opened += "guide" },
            onOpenPrivacy = { opened += "privacy" },
            onOpenFeatureTutorial = { opened += "tutorial" },
            onOpenAbout = { opened += "about" }
        )

        composeRule.onNodeWithTag("settings-guide-entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-privacy-entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-feature-tutorial-entry")
            .performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-about-entry").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("guide", "privacy", "tutorial", "about"), opened)
        }
    }

    @Test
    fun largeFontScaleKeepsCoreControlsReachable() {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = 2f)
            ) {
                EvoluneTheme {
                    SettingsScreenTestHost()
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings-palette-grid").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-weight-input").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("settings-about-entry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutPageKeepsContactLinksCopyrightAndDisclaimerDialogs() {
        var websiteOpenCount = 0
        var developerContactCount = 0
        composeRule.setContent {
            EvoluneTheme {
                AboutScreen(
                    onOpenWebsite = { websiteOpenCount += 1 },
                    onContactDeveloper = { developerContactCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("settings-about-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-about-website").performClick()
        composeRule.onNodeWithTag("settings-about-developer-contact").performClick()
        composeRule.runOnIdle {
            assertEquals(1, websiteOpenCount)
            assertEquals(1, developerContactCount)
        }
        composeRule.onNodeWithTag("settings-about-copyright").performScrollTo().performClick()
        composeRule.onAllNodesWithText("版权信息").get(1).assertIsDisplayed()
        composeRule.onNodeWithText("关闭").performClick()
        composeRule.onNodeWithTag("settings-about-disclaimer").performScrollTo().performClick()
        composeRule.onAllNodesWithText("免责声明").get(1).assertIsDisplayed()
    }

    private fun setSettingsContent(
        userSettings: UserSettings = UserSettings(),
        onBodyWeightChange: (Double) -> Unit = {},
        onThemeModeChange: (ThemeMode) -> Unit = {},
        onSelectDynamicSource: () -> Unit = {},
        onSelectPresetSource: () -> Unit = {},
        onPresetPaletteChange: (PresetPalette) -> Unit = {},
        onTimeFormatChange: (TimeFormat) -> Unit = {},
        onAutoCheckUpdatesChange: (Boolean) -> Unit = {},
        onCheckForUpdates: () -> Unit = {},
        onHealthConnectWeightSyncEnabledChange: (Boolean) -> Unit = {},
        onOpenGoogleDrive: () -> Unit = {},
        onOpenGuide: () -> Unit = {},
        onOpenPrivacy: () -> Unit = {},
        onOpenFeatureTutorial: () -> Unit = {},
        onOpenAbout: () -> Unit = {}
    ) {
        composeRule.setContent {
            EvoluneTheme {
                SettingsScreen(
                    userSettings = userSettings,
                    healthConnectWeightSyncState = HealthConnectWeightSyncState(),
                    backupRestoreConnected = true,
                    updateCheckResult = UpdateCheckResult.Idle,
                    onBodyWeightChange = onBodyWeightChange,
                    onThemeModeChange = onThemeModeChange,
                    onSelectDynamicSource = onSelectDynamicSource,
                    onSelectPresetSource = onSelectPresetSource,
                    onPresetPaletteChange = onPresetPaletteChange,
                    onTimeFormatChange = onTimeFormatChange,
                    onAutoCheckUpdatesChange = onAutoCheckUpdatesChange,
                    onCheckForUpdates = onCheckForUpdates,
                    onHealthConnectWeightSyncEnabledChange =
                        onHealthConnectWeightSyncEnabledChange,
                    onHealthConnectReauthorize = {},
                    onHealthConnectManagePermissions = {},
                    importResult = io.github.yingqiu0871.evolune.viewmodel.ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {},
                    portableBusy = false,
                    onExportPortableJson = {},
                    onExportPortableCsv = {},
                    onImportPortableJson = {},
                    portableDialog = null,
                    onDismissPortableDialog = {},
                    onOpenGoogleDrive = onOpenGoogleDrive,
                    onOpenGuide = onOpenGuide,
                    onOpenPrivacy = onOpenPrivacy,
                    onOpenFeatureTutorial = onOpenFeatureTutorial,
                    onOpenAbout = onOpenAbout,
                    showTopBar = false
                )
            }
        }
        composeRule.waitForIdle()
    }
}

@androidx.compose.runtime.Composable
private fun SettingsScreenTestHost() {
    SettingsScreen(
        userSettings = UserSettings(),
        healthConnectWeightSyncState = HealthConnectWeightSyncState(),
        backupRestoreConnected = false,
        updateCheckResult = UpdateCheckResult.Idle,
        onBodyWeightChange = {},
        onThemeModeChange = {},
        onSelectDynamicSource = {},
        onSelectPresetSource = {},
        onPresetPaletteChange = {},
        onTimeFormatChange = {},
        onAutoCheckUpdatesChange = {},
        onCheckForUpdates = {},
        onHealthConnectWeightSyncEnabledChange = {},
        onHealthConnectReauthorize = {},
        onHealthConnectManagePermissions = {},
        importResult = io.github.yingqiu0871.evolune.viewmodel.ImportResult.Idle,
        onDismissImportResult = {},
        clipboardExportMessage = null,
        onClipboardExportMessageShown = {},
        onImportClick = {},
        onImportFromClipboard = {},
        onExportClick = {},
        onExportToClipboard = {},
        portableBusy = false,
        onExportPortableJson = {},
        onExportPortableCsv = {},
        onImportPortableJson = {},
        portableDialog = null,
        onDismissPortableDialog = {},
        onOpenGoogleDrive = {},
        onOpenGuide = {},
        onOpenPrivacy = {},
        onOpenFeatureTutorial = {},
        onOpenAbout = {},
        showTopBar = false
    )
}
