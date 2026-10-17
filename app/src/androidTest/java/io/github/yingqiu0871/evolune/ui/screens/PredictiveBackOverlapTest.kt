package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.BackEventCompat
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PredictiveBackOverlapTest {
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
    fun aboutOverSettingsDoesNotShowSettingsThroughDuringPredictiveBack() {
        composeRule.waitForIdle()
        openSettings()
        composeRule.onNodeWithTag("settings-about-entry").scrollToAndClick()
        waitForTag("settings-about-screen")

        assertPoppingPageStaysOpaque("settings-about-screen")
    }

    @Test
    fun helpOverSettingsDoesNotShowSettingsThroughDuringPredictiveBack() {
        composeRule.waitForIdle()
        openSettings()
        composeRule.onNodeWithTag("settings-help-entry").scrollToAndClick()
        waitForTag("settings-help-screen")

        assertPoppingPageStaysOpaque("settings-help-screen")
    }

    // Starts a predictive back gesture, checks the popping page still looks like it did before
    // the gesture (nothing underneath shows through), then cancels the gesture.
    private fun assertPoppingPageStaysOpaque(pageTag: String) {
        composeRule.waitForIdle()
        val baseline = captureContent()

        scenario.onActivity { activity ->
            val dispatcher = activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(backEvent(progress = 0f))
            dispatcher.dispatchOnBackProgressed(backEvent(progress = 0.5f))
        }
        composeRule.waitForIdle()
        val midGesture = captureContent()

        scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        composeRule.waitForIdle()

        val differing = differingPixelFraction(baseline, midGesture)
        assertTrue(
            "Popping page changed on ${"%.1f".format(differing * 100)}% of pixels during " +
                "predictive back; the page underneath is showing through",
            differing < MAX_DIFFERING_FRACTION
        )
        waitForTag(pageTag)
        composeRule.onNodeWithTag(pageTag).assertIsDisplayed()
    }

    private fun backEvent(progress: Float) =
        BackEventCompat(TOUCH_X, TOUCH_Y, progress, BackEventCompat.EDGE_LEFT)

    private fun captureContent(): Bitmap =
        composeRule.onNodeWithTag("app-content").captureToImage().asAndroidBitmap()

    private fun differingPixelFraction(a: Bitmap, b: Bitmap): Float {
        if (a.width != b.width || a.height != b.height) return 1f
        val pixelsA = IntArray(a.width * a.height)
        val pixelsB = IntArray(b.width * b.height)
        a.getPixels(pixelsA, 0, a.width, 0, 0, a.width, a.height)
        b.getPixels(pixelsB, 0, b.width, 0, 0, b.width, b.height)
        var differing = 0
        for (i in pixelsA.indices) {
            val x = pixelsA[i]
            val y = pixelsB[i]
            val maxChannelDiff = maxOf(
                abs(((x shr 16) and 0xFF) - ((y shr 16) and 0xFF)),
                abs(((x shr 8) and 0xFF) - ((y shr 8) and 0xFF)),
                abs((x and 0xFF) - (y and 0xFF))
            )
            if (maxChannelDiff > CHANNEL_TOLERANCE) differing++
        }
        return differing.toFloat() / pixelsA.size
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettings() {
        composeRule.selectPrimaryTab("settings")
        waitForTag("settings-basic-data-section")
    }

    private companion object {
        const val TOUCH_X = 8f
        const val TOUCH_Y = 600f
        const val CHANNEL_TOLERANCE = 16
        const val MAX_DIFFERING_FRACTION = 0.01f
    }
}
