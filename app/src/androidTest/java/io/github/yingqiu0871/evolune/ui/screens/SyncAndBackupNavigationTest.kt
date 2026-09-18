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
    fun flattenedSettingsKeepInlineControlsAndRetainedRoutesStable() {
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithTag("settings-basic-data-section").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings-appearance-section").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings-import-export-block").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("health-connect-weight-sync-switch").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings-update-section").performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("google-drive-backup-now")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("google-drive-backup-now").assertIsDisplayed()
        pressBack()
        composeRule.onNodeWithTag("settings-sync-backup-google-drive-entry").assertIsDisplayed()
    }

    @Test
    fun retainedContentRowsOpenAndReturnToTheFlattenedScreen() {
        composeRule.waitForIdle()
        openSettings()

        openRowAndReturn("settings-privacy-entry", assertTitle = R.string.disclosures_title)
        openRowAndReturn("settings-about-entry", assertTag = "settings-about-screen")
        openRowAndReturn("settings-feature-tutorial-entry", assertTag = "feature-tutorial-step-title")
        openRowAndReturn("settings-guide-entry", assertTitle = R.string.onboarding_title)

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
            composeRule.onAllNodesWithTag("settings-basic-data-section")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openRowAndReturn(
        entryTag: String,
        assertTag: String? = null,
        assertTitle: Int? = null
    ) {
        composeRule.onNodeWithTag(entryTag).performScrollTo().performClick()
        if (assertTag != null) {
            composeRule.waitUntil(5_000L) {
                composeRule.onAllNodesWithTag(assertTag).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(assertTag).assertIsDisplayed()
        }
        if (assertTitle != null) {
            composeRule.waitUntil(5_000L) {
                composeRule.onAllNodesWithTag("app-top-title").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("app-top-title").assertTextEquals(
                context.getString(assertTitle)
            )
        }
        pressBack()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("settings-basic-data-section")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pressBack() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.common_back))
            .performClick()
        composeRule.waitForIdle()
    }
}
