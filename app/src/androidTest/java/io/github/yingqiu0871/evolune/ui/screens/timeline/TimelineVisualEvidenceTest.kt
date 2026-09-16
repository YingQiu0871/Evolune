package io.github.yingqiu0871.evolune.ui.screens.timeline

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.ui.theme.EvoluneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * V17-D-04 §32/§40 — supplementary captured-device visual evidence for the centering rules and the
 * state surfaces. The deterministic assertions live in [TimelineGeometryTest] / [TimelineScreenTest];
 * these PNGs only add human-reviewable captures on the Pixel 7 AVD.
 */
@RunWith(AndroidJUnit4::class)
class TimelineVisualEvidenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun captureCalendarContentNavigationAndStateSurfaces() {
        var state by mutableStateOf(TimelineTestStates.contentState())
        composeRule.setContent {
            EvoluneTheme {
                TimelineScreenContent(state = state)
            }
        }
        composeRule.waitForIdle()
        val outputs = mutableListOf<String>()
        outputs += capture("d-04-01-calendar-centered-content")

        state = TimelineTestStates.pastMonthState()
        composeRule.waitForIdle()
        outputs += capture("d-04-02-month-navigation-enabled")

        state = TimelineTestStates.emptyDayState()
        composeRule.waitForIdle()
        outputs += capture("d-04-03-empty-day-with-other-sections")

        state = TimelineTestStates.errorState()
        composeRule.waitForIdle()
        outputs += capture("d-04-04-error-retry")

        outputs.forEach { path ->
            val file = File(path)
            assertTrue("screenshot must exist: $path", file.exists() && file.length() > 0L)
        }
    }

    private fun capture(name: String): String {
        val directory = File(context.getExternalFilesDir(null), "d-04").apply { mkdirs() }
        val file = File(directory, "$name.png")
        file.outputStream().use { stream ->
            composeRule.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return file.absolutePath
    }
}
