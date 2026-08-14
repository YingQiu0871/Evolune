package io.github.yingqiu0871.evolune.ui.screens

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.MainActivity
import io.github.yingqiu0871.evolune.data.SettingsDataStore
import io.github.yingqiu0871.evolune.data.ThemeMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
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
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun luminance(r: Int, g: Int, b: Int): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun contrast(l1: Double, l2: Double): Double =
        (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

    private fun waitForNav(label: String) {
        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openSettings() {
        waitForNav("设置")
        composeRule.onAllNodesWithText("设置")[0].performClick()
        composeRule.waitForIdle()
    }

    private fun sampleTitleRegion(mode: String): Double {
        val titleNode = composeRule.onAllNodesWithText("设置").fetchSemanticsNodes()
            .first { it.boundsInWindow.top < 500 }
        val bounds = titleNode.boundsInWindow
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsDataStore(context)
        runBlocking { store.updateThemeMode(ThemeMode.SYSTEM) }
        composeRule.waitForIdle()
        openSettings()

        composeRule.onNodeWithText("OLED 全黑")
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
