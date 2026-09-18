package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.yingqiu0871.evolune.data.ThemeColorSource
import io.github.yingqiu0871.evolune.data.ThemePresetSelection
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.theme.palette.PresetPalette
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import io.github.yingqiu0871.evolune.viewmodel.UpdateCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** v1.7.2 Slice C — canonical 配色 selector behavior (source rows + eight preset tiles). */
class SettingsPaletteSelectorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dynamicStateShowsDynamicSelectedAndNoPresetTileSelected() {
        var presetEntries = 0
        setSettingsContent(
            UserSettings(),
            onSelectPresetSource = { presetEntries += 1 }
        )

        composeRule.onNodeWithTag("color-source-dynamic").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("color-source-preset").assertIsNotSelected()
        PresetPalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("palette-tile-${palette.name.lowercase()}")
                .assertIsNotSelected()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("color-legacy-builtin-current")
                .fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithTag("color-source-preset").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals("dynamic -> preset entry must be requested once", 1, presetEntries)
        }
    }

    @Test
    fun presetStateMarksExactlyTheCanonicalTile() {
        var presetEntries = 0
        setSettingsContent(
            UserSettings(
                themeColorSource = ThemeColorSource.PRESET,
                themePreset = ThemePresetSelection.Preset(PresetPalette.MONET_SAKURA)
            ),
            onSelectPresetSource = { presetEntries += 1 }
        )

        composeRule.onNodeWithTag("color-source-preset").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("palette-tile-monet_sakura").performScrollTo().assertIsSelected()
        PresetPalette.entries.filter { it != PresetPalette.MONET_SAKURA }.forEach { palette ->
            composeRule.onNodeWithTag("palette-tile-${palette.name.lowercase()}")
                .assertIsNotSelected()
        }
        assertTrue(
            composeRule.onAllNodesWithTag("color-legacy-builtin-current")
                .fetchSemanticsNodes().isEmpty()
        )

        composeRule.onNodeWithTag("color-source-preset").performClick()
        composeRule.runOnIdle {
            assertEquals("an already-active PRESET source must not re-default", 0, presetEntries)
        }
    }

    @Test
    fun legacyBuiltinStateShowsCompatibilityRowAndNoNormalTileSelected() {
        var selected: PresetPalette? = null
        setSettingsContent(
            UserSettings(
                themeColorSource = ThemeColorSource.PRESET,
                themePreset = ThemePresetSelection.LegacyBuiltin
            ),
            onPresetPaletteChange = { selected = it }
        )

        composeRule.onNodeWithTag("color-legacy-builtin-current")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("color-source-preset").assertIsSelected()
        PresetPalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("palette-tile-${palette.name.lowercase()}")
                .assertIsNotSelected()
        }

        composeRule.onNodeWithTag("palette-tile-monet_blue").performScrollTo().performClick()
        composeRule.runOnIdle {
            assertEquals(PresetPalette.MONET_BLUE, selected)
        }
    }

    @Test
    fun paletteInventoryIsExactlyTheEightApprovedPresets() {
        setSettingsContent(UserSettings())

        assertEquals(8, PresetPalette.entries.size)
        PresetPalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("palette-tile-${palette.name.lowercase()}").assertExists()
        }
        listOf(
            "palette-tile-material_you_auto",
            "palette-tile-legacy_builtin",
            "palette-tile-auto"
        ).forEach { forbidden ->
            assertTrue(
                "Forbidden tile must not exist: $forbidden",
                composeRule.onAllNodesWithTag(forbidden).fetchSemanticsNodes().isEmpty()
            )
        }
    }

    private fun setSettingsContent(
        userSettings: UserSettings,
        onPresetPaletteChange: (PresetPalette) -> Unit = {},
        onSelectPresetSource: () -> Unit = {}
    ) {
        composeRule.setContent {
            EvoluneTheme {
                SettingsScreen(
                    userSettings = userSettings,
                    healthConnectWeightSyncState = HealthConnectWeightSyncState(),
                    backupRestoreConnected = false,
                    updateCheckResult = UpdateCheckResult.Idle,
                    onBodyWeightChange = {},
                    onThemeModeChange = {},
                    onSelectDynamicSource = {},
                    onSelectPresetSource = onSelectPresetSource,
                    onPresetPaletteChange = onPresetPaletteChange,
                    onTimeFormatChange = {},
                    onAutoCheckUpdatesChange = {},
                    onCheckForUpdates = {},
                    onHealthConnectWeightSyncEnabledChange = {},
                    onHealthConnectReauthorize = {},
                    onHealthConnectManagePermissions = {},
                    importResult = ImportResult.Idle,
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
        }
        composeRule.waitForIdle()
    }
}
