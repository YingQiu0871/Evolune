package io.github.yingqiu0871.evolune.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import io.github.yingqiu0871.evolune.onboarding.OnboardingStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

/**
 * TEST-ONLY color-role conformance: samples the TopAppBar title region in
 * light, dark, and OLED-black themes and reports the measured text/background contrast.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ColorRoleConformanceTest {

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

    private fun luminance(r: Int, g: Int, b: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun contrast(l1: Double, l2: Double): Double =
        (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

    private fun openSettings() {
        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("nav-bar-settings").fetchSemanticsNodes().isNotEmpty()
        }
        val railSettings = composeRule.onAllNodesWithTag("nav-rail-settings").fetchSemanticsNodes()
        if (railSettings.isNotEmpty()) {
            composeRule.onNodeWithTag("nav-rail-settings").performClick()
        } else {
            composeRule.onNodeWithTag("nav-bar-settings").performClick()
        }
        composeRule.onNodeWithTag("settings-appearance-format-entry")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithTag("theme-mode-system").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sampleTitleRegion(mode: String): Double {
        val titleNode = composeRule.onNodeWithTag("app-top-title").fetchSemanticsNode()
        val bounds = titleNode.boundsInWindow
        val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
        val appRootIndex = roots.withIndex()
            .filter { (_, root) ->
                val rootBounds = root.boundsInWindow
                bounds.center.x >= rootBounds.left && bounds.center.x <= rootBounds.right &&
                    bounds.center.y >= rootBounds.top && bounds.center.y <= rootBounds.bottom
            }
            .maxByOrNull { (_, root) ->
                root.boundsInWindow.width * root.boundsInWindow.height
            }
            ?.index
            ?: error("Settings title is not contained by a Compose root: $roots")
        Log.i(
            TAG,
            "$mode composeRoots=${roots.size} selectedAppRoot=${roots[appRootIndex].id} " +
                "rootBounds=${roots[appRootIndex].boundsInWindow}"
        )
        val bitmap = composeRule
            .onAllNodes(isRoot(), useUnmergedTree = true)[appRootIndex]
            .captureToImage()
            .asAndroidBitmap()
        val left = bounds.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = bounds.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = bounds.right.toInt().coerceIn(0, bitmap.width)
        val bottom = bounds.bottom.toInt().coerceIn(0, bitmap.height)
        var minLum = 1.0
        var maxLum = 0.0
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val p = bitmap.getPixel(x, y)
                val l = luminance((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)
                if (l < minLum) minLum = l
                if (l > maxLum) maxLum = l
                x += 2
            }
            y += 2
        }
        val ratio = contrast(maxLum, minLum)
        Log.i(
            TAG,
            "$mode title region bounds=($left,$top,$right,$bottom) minLum=%.3f maxLum=%.3f contrast=%.2f".format(
                minLum, maxLum, ratio
            )
        )
        return ratio
    }

    @Test
    fun topAppBarTitleContrastPassesInLightDarkAndAmoled() {
        val store = SettingsDataStore(context)
        runBlocking { store.updateThemeMode(ThemeMode.LIGHT) }
        composeRule.waitForIdle()
        openSettings()
        val lightRatio = sampleTitleRegion("light")
        runBlocking { store.updateThemeMode(ThemeMode.DARK) }
        composeRule.waitForIdle()
        val darkRatio = sampleTitleRegion("dark")
        runBlocking { store.updateThemeMode(ThemeMode.AMOLED) }
        composeRule.waitForIdle()
        val amoledRatio = sampleTitleRegion("amoled")
        runBlocking { store.updateThemeMode(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()

        Log.i(
            TAG,
            "RESULT light=%.2f dark=%.2f amoled=%.2f".format(
                lightRatio,
                darkRatio,
                amoledRatio
            )
        )
        check(lightRatio >= 2.5f) { "light title contrast too low: $lightRatio" }
        check(darkRatio >= 2.5f) { "dark title contrast too low: $darkRatio" }
        check(amoledRatio >= 2.5f) { "amoled title contrast too low: $amoledRatio" }
        println("ColorRole RESULT light=$lightRatio dark=$darkRatio amoled=$amoledRatio")
    }

    @Test
    fun amoledThemeOptionCanBeSelected() {
        val store = SettingsDataStore(context)
        runBlocking { store.updateThemeMode(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithTag("theme-mode-amoled")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.waitUntil(5_000L) {
            runBlocking { store.userSettings.first().themeMode == ThemeMode.AMOLED }
        }
        runBlocking { store.updateThemeMode(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()
    }

    @Test
    fun themeModeIconsStayVerticallyCentered() {
        val store = SettingsDataStore(context)
        runBlocking { store.updateThemeMode(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()
        openSettings()

        ThemeMode.entries.forEach { mode ->
            val suffix = mode.name.lowercase()
            composeRule.onNodeWithTag("theme-mode-$suffix")
                .performScrollTo()
            val itemBounds = composeRule
                .onNodeWithTag("theme-mode-$suffix")
                .fetchSemanticsNode().boundsInRoot
            val iconBounds = composeRule
                .onNodeWithTag("theme-mode-icon-$suffix", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot

            check(abs(itemBounds.center.y - iconBounds.center.y) <= 1f) {
                "$mode icon is not vertically centered: item=$itemBounds icon=$iconBounds"
            }
        }
    }

    private companion object {
        const val TAG = "ColorRole"
    }
}
