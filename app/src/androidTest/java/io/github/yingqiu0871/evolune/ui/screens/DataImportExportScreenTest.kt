package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.yingqiu0871.evolune.export.PortableExportFormat
import io.github.yingqiu0871.evolune.ui.screens.settings.PortableDialogMessage
import io.github.yingqiu0871.evolune.export.PortableExportRange
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import io.github.yingqiu0871.evolune.viewmodel.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * V17 Phase E E6.5/E6.6 — affected instrumentation gate for the canonical
 * export/import surface (frozen contract §35): explicit range choice, canonical actions,
 * typed dialogs, busy blocking, legacy labeling and clipboard privacy confirmation.
 */
class DataImportExportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun canonicalActionsAreExposedWithExplicitRangeSelection() {
        val exportRequests = mutableListOf<Pair<PortableExportFormat, PortableExportRange>>()
        var imports = 0
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {},
                    onExportPortableJson = { range ->
                        exportRequests += PortableExportFormat.JSON to range
                    },
                    onExportPortableCsv = { range ->
                        exportRequests += PortableExportFormat.CSV to range
                    },
                    onImportPortableJson = { imports += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("portable-export-json").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("portable-range-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("最近 30 天").assertIsDisplayed()
        composeRule.onNodeWithText("最近 90 天").assertIsDisplayed()
        composeRule.onNodeWithText("全部历史").assertIsDisplayed()
        composeRule.onNodeWithTag("portable-range-30d").performClick()
        composeRule.runOnIdle {
            assertEquals(
                listOf(PortableExportFormat.JSON to PortableExportRange.LAST_30_DAYS),
                exportRequests
            )
        }

        composeRule.onNodeWithTag("portable-export-csv").performClick()
        composeRule.onNodeWithTag("portable-range-all").performClick()
        composeRule.onNodeWithTag("portable-import-json").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PortableExportFormat.JSON to PortableExportRange.LAST_30_DAYS,
                    PortableExportFormat.CSV to PortableExportRange.ALL
                ),
                exportRequests
            )
            assertEquals(1, imports)
        }
    }

    @Test
    fun rangeDialogCanBeCancelledWithoutAnyExport() {
        var exportRequests = 0
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {},
                    onExportPortableJson = { exportRequests += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("portable-export-json").performClick()
        composeRule.onNodeWithTag("portable-range-dialog").assertIsDisplayed()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.runOnIdle { assertEquals(0, exportRequests) }
        composeRule.onNodeWithTag("portable-range-dialog").assertDoesNotExist()
    }

    @Test
    fun busyStateBlocksRepeatedTriggers() {
        var busy by mutableStateOf(true)
        var exportRequests = 0
        var imports = 0
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {},
                    portableBusy = busy,
                    onExportPortableJson = { exportRequests += 1 },
                    onExportPortableCsv = { exportRequests += 1 },
                    onImportPortableJson = { imports += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("portable-busy").assertIsDisplayed()
        composeRule.onNodeWithTag("portable-export-json").performClick()
        composeRule.onNodeWithTag("portable-export-csv").performClick()
        composeRule.onNodeWithTag("portable-import-json").performClick()
        composeRule.runOnIdle {
            assertEquals(0, exportRequests)
            assertEquals(0, imports)
        }

        busy = false
        composeRule.onNodeWithTag("portable-export-json").performClick()
        composeRule.onNodeWithTag("portable-range-all").performClick()
        composeRule.runOnIdle { assertEquals(1, exportRequests) }
    }

    @Test
    fun typedDialogShowsAndDismissesOutcome() {
        var dismissals = 0
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {},
                    portableDialog = PortableDialogMessage(
                        title = "导入 Evolune JSON",
                        body = "不支持的导出文件版本。"
                    ),
                    onDismissPortableDialog = { dismissals += 1 }
                )
            }
        }

        composeRule.onNodeWithText("不支持的导出文件版本。").assertIsDisplayed()
        composeRule.onNodeWithText("确定").performClick()
        composeRule.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test
    fun oversizedLegacyImportShowsLocalizedTooLargeMessage() {
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Error(message = "Import failed", tooLarge = true),
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = {}
                )
            }
        }

        composeRule.onNodeWithText("文件过大，无法导入。").assertIsDisplayed()
    }

    @Test
    fun legacyClipboardExportRequiresExplicitPrivacyConfirmation() {
        var clipboardExports = 0
        composeRule.setContent {
            EvoluneTheme {
                TestDataImportExportHost(
                    importResult = ImportResult.Idle,
                    onDismissImportResult = {},
                    clipboardExportMessage = null,
                    onClipboardExportMessageShown = {},
                    onImportClick = {},
                    onImportFromClipboard = {},
                    onExportClick = {},
                    onExportToClipboard = { clipboardExports += 1 }
                )
            }
        }

        composeRule.onNodeWithText("Legacy / Mahiro JSON v1 兼容").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-export-clipboard").performClick()
        composeRule.onNodeWithTag("legacy-clipboard-warning").assertIsDisplayed()
        composeRule.onNodeWithTag("legacy-clipboard-cancel").performClick()
        composeRule.runOnIdle { assertEquals(0, clipboardExports) }

        composeRule.onNodeWithTag("settings-export-clipboard").performClick()
        composeRule.onNodeWithTag("legacy-clipboard-confirm").performClick()
        composeRule.runOnIdle { assertTrue(clipboardExports == 1) }
    }
}
