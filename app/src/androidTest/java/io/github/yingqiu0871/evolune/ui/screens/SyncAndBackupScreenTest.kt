package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.yingqiu0871.evolune.backup.BackupRestoreUiState
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupGeneration
import io.github.yingqiu0871.evolune.backup.cloud.CloudBackupId
import io.github.yingqiu0871.evolune.data.UserSettings
import io.github.yingqiu0871.evolune.healthconnect.HealthConnectWeightSyncState
import io.github.yingqiu0871.evolune.ui.screens.settings.SettingsSyncBackupSection
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncAndBackupScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun syncAndBackupInlineSectionExposesLocalHealthAndDriveControls() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            EvoluneTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                TestScrollHost {
                    SettingsSyncBackupSection(
                        settings = UserSettings(),
                        healthConnectWeightSyncState = HealthConnectWeightSyncState(),
                        backupRestoreConnected = true,
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
                        onWeightSyncEnabledChange = { opened += "health" },
                        onReauthorize = {},
                        onManagePermissions = {},
                        onOpenGoogleDrive = { opened += "drive" },
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }

        composeRule.onNodeWithTag("settings-import-export-block").assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-weight-sync-switch").performScrollTo()
        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("已连接（当前会话）").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry").performClick()

        assertEquals(listOf("drive"), opened)
    }

    @Test
    fun importExportPageKeepsExistingMahiroActions() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = { opened += "import-file" },
                    onImportFromClipboard = { opened += "import-clipboard" },
                    onExportClick = { opened += "export-file" },
                    onExportToClipboard = { opened += "export-clipboard" }
                )
            }
        }

        composeRule.onNodeWithTag("settings-import-json").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("settings-import-clipboard").performClick()
        composeRule.onNodeWithTag("settings-export-json").performClick()
        composeRule.onNodeWithTag("settings-export-clipboard").performClick()
        // Phase E: the legacy clipboard export now requires an explicit privacy confirmation.
        composeRule.onNodeWithTag("legacy-clipboard-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("legacy-clipboard-confirm").performClick()

        assertEquals(
            listOf("import-file", "import-clipboard", "export-file", "export-clipboard"),
            opened
        )
    }

    @Test
    fun googleDrivePageKeepsExistingBackupRestoreActions() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            EvoluneTheme {
                GoogleDriveBackupRestoreScreen(
                    connected = true,
                    state = BackupRestoreUiState.Idle,
                    onBackupNow = { opened += "backup" },
                    onRestoreFromBackup = { opened += "restore" },
                    onDisconnect = { opened += "disconnect" },
                    onSelectGeneration = {},
                    onSubmitBackupPassphrase = { _, _ -> },
                    onSubmitRestorePassphrase = {},
                    onConfirmRestore = {},
                    onCancel = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithText("已连接（当前会话）").assertIsDisplayed()
        composeRule.onNodeWithTag("google-drive-backup-now").performClick()
        composeRule.onNodeWithTag("google-drive-restore-from-backup").performClick()
        composeRule.onNodeWithTag("google-drive-disconnect").performClick()

        assertEquals(listOf("backup", "restore", "disconnect"), opened)
    }

    @Test
    fun backupPickerUsesNumberedRowsWithReadableTimesAndPreservesSelection() {
        val generations = (1..3).map { index ->
            CloudBackupGeneration(
                id = CloudBackupId("generation-$index"),
                name = "evolune-backup-$index.evbackup",
                createdAt = "2026-08-23T00:00:00Z",
                sizeBytes = null,
                contentSha256 = "0".repeat(64)
            )
        }
        var selected: CloudBackupGeneration? = null

        composeRule.setContent {
            EvoluneTheme {
                GoogleDriveBackupRestoreScreen(
                    connected = true,
                    state = BackupRestoreUiState.SelectingBackup(generations),
                    onBackupNow = {},
                    onRestoreFromBackup = {},
                    onDisconnect = {},
                    onSelectGeneration = { selected = it },
                    onSubmitBackupPassphrase = { _, _ -> },
                    onSubmitRestorePassphrase = {},
                    onConfirmRestore = {},
                    onCancel = {},
                    onDismissMessage = {}
                )
            }
        }

        composeRule.onNodeWithText("备份 1").assertIsDisplayed()
        composeRule.onNodeWithText("备份 2").assertIsDisplayed()
        composeRule.onNodeWithText("备份 3").assertIsDisplayed()
        composeRule.onNodeWithTag("backup-generation-1").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(generations[1], selected) }
    }
}
