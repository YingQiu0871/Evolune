package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.R
import io.github.yingqiu0871.evolune.onboarding.CURRENT_MEDICAL_PK_DISCLOSURE_VERSION
import io.github.yingqiu0871.evolune.onboarding.CURRENT_ONBOARDING_VERSION
import io.github.yingqiu0871.evolune.onboarding.CURRENT_TERMS_VERSION
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class SyncAndBackupNavigationTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchFromDeterministicOnboardingState() {
        runBlocking {
            val store = OnboardingStateStore(context, isExistingInstallation = true)
            store.initializeIfNeeded()
            store.acceptTerms()
            store.acknowledgeMedicalPkDisclosure()
            store.completeOnboarding()
            store.markFeatureTutorialHandled()
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    @Test
    fun settingsCategoryPagesNavigateBackToSettings() {
        composeRule.waitForIdle()
        openSettings()

        openCategory("settings-basic-data-entry", "settings-basic-data-screen")
        pressBackToSettings()

        openCategory("settings-appearance-format-entry", "settings-appearance-format-screen")
        pressBackToSettings()

        openCategory("settings-sync-backup-entry", "settings-sync-backup-data-entry")
        pressBackToSettings()

        openCategory("settings-update-entry", "settings-update-screen")
        pressBackToSettings()

        openCategory("settings-about-entry", "settings-about-screen")
        pressBackToSettings()
    }

    @Test
    fun settingsSyncBackupHealthConnectAndDriveBackNavigationIsStable() {
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithTag("settings-sync-backup-entry")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-sync-backup-data-entry")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("settings-sync-backup-health-connect-entry").performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("health-connect-sync-title")
                .fetchSemanticsNodes().isNotEmpty()
        }
        pressBack()
        composeRule.onNodeWithTag("settings-sync-backup-data-entry").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry").performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("google-drive-backup-now")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("google-drive-backup-now").assertIsDisplayed()
        composeRule.onNodeWithTag("google-drive-restore-from-backup").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("settings-sync-backup-data-entry").assertIsDisplayed()

        composeRule.onNodeWithTag("settings-sync-backup-data-entry").performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-import-json")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings-import-json").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("settings-sync-backup-data-entry").assertIsDisplayed()

        pressBack()
        composeRule.onNodeWithTag("app-top-title").assertTextEquals(
            context.getString(R.string.settings_title)
        )
    }

    private fun openSettings() {
        val railSettings = composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes()
        if (railSettings.isNotEmpty()) {
            composeRule.onNodeWithTag("nav-rail-settings").performClick()
        } else {
            composeRule.onNodeWithTag("nav-bar-settings").performClick()
        }
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-basic-data-entry")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openCategory(entryTag: String, destinationTag: String) {
        composeRule.onNodeWithTag(entryTag)
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(destinationTag)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(destinationTag).assertIsDisplayed()
    }

    private fun pressBackToSettings() {
        pressBack()
        composeRule.onNodeWithTag("settings-basic-data-entry").assertIsDisplayed()
    }

    private fun pressBack() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.common_back))
            .performClick()
        composeRule.waitForIdle()
    }
}
