package io.github.yuninggu.evolune.ui.screens

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuninggu.evolune.testime.ProbeTestIme
import org.junit.After
import org.junit.Before
import io.github.yuninggu.evolune.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TEST-ONLY frame-level IME probe on the real app.
 *
 * Samples ime inset bottom, dose field window Y and scroll offset as fast as the
 * compose semantics layer allows (~40-70ms/sample; NOT true 60fps). That is still
 * fine-grained enough to expose a second easing stage or a late bringIntoView
 * correction, which each last 200-400ms.
 *
 * Fails loudly when the IME never opens: a run where the keyboard stayed closed
 * proves nothing about scroll behaviour and must never be reported as a pass.
 */
@RunWith(AndroidJUnit4::class)
class RealAppImeFrameProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var originalIme: String? = null

    private fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }

    /**
     * Swaps in a fixed-height test IME. Without this the emulator's unconfigured
     * Gboard renders a floating pill reporting a ~63px inset, which never reaches
     * the field under test.
     */
    @Before
    fun useDeterministicIme() {
        originalIme = shell("settings get secure default_input_method").trim()
        shell("ime enable ${ProbeTestIme.ID}")
        shell("ime set ${ProbeTestIme.ID}")
        SystemClock.sleep(1_500)
        Log.i(TAG, "IME switched from=$originalIme to=${ProbeTestIme.ID}")
    }

    @After
    fun restoreIme() {
        originalIme?.takeIf { it.isNotBlank() && it != "null" }?.let { shell("ime set $it") }
    }

    private data class Frame(
        val tMs: Long,
        val imeBottom: Int,
        val fieldTop: Float,
        val fieldBottom: Float,
        val scroll: Float,
        val scrollMax: Float
    )

    private fun readViewHeight(): Int {
        var h = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync { h = composeRule.activity.window.decorView.height }
        return h
    }

    private fun readImeInset(): Int {
        var imeBottom = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            imeBottom = composeRule.activity.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.ime())
                ?.bottom
                ?: 0
        }
        return imeBottom
    }

    /**
     * Drives the real platform IME animation. Clicking an already-focused field
     * does not re-show a hidden keyboard, so each cycle asks the window insets
     * controller directly. The app's scroll response to the resulting inset
     * change is what this probe measures - the trigger is not faked, only made
     * deterministic.
     */
    private fun setImeVisible(visible: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val window = composeRule.activity.window
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            if (visible) {
                controller.show(WindowInsetsCompat.Type.ime())
            } else {
                controller.hide(WindowInsetsCompat.Type.ime())
            }
        }
    }

    private fun readFrame(): Frame {
        val imeBottom = readImeInset()
        val now = SystemClock.elapsedRealtime()
        val field = composeRule.onNodeWithTag("record-dose").fetchSemanticsNode()
        val scroll = composeRule.onNodeWithTag("record-editor-scroll").fetchSemanticsNode()
        val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        return Frame(
            now,
            imeBottom,
            field.boundsInWindow.top,
            field.boundsInWindow.bottom,
            range.value(),
            range.maxValue()
        )
    }

    /**
     * Samples until both ime inset and field position have held still for
     * [stableMs], or [maxMs] elapses. Unlike a plain "ime unchanged" check this
     * also requires the field to stop moving, so a queued second scroll cannot
     * end the sampling window early.
     */
    private fun sampleUntilStill(
        maxMs: Long,
        stableMs: Long,
        requireImeState: Boolean? = null
    ): List<Frame> {
        val frames = mutableListOf<Frame>()
        val start = SystemClock.elapsedRealtime()
        var lastChange = start
        var lastIme = Int.MIN_VALUE
        var lastField = Float.NaN
        var lastScroll = Float.NaN
        var stateReached = requireImeState == null
        while (SystemClock.elapsedRealtime() - start < maxMs) {
            val f = readFrame()
            frames += f
            if (!stateReached && requireImeState != null) {
                stateReached = if (requireImeState) f.imeBottom > 0 else f.imeBottom == 0
            }
            val moved = f.imeBottom != lastIme ||
                lastField.isNaN() || kotlin.math.abs(f.fieldTop - lastField) > 0.5f ||
                lastScroll.isNaN() || kotlin.math.abs(f.scroll - lastScroll) > 0.5f
            if (moved) lastChange = f.tMs
            lastIme = f.imeBottom
            lastField = f.fieldTop
            lastScroll = f.scroll
            if (frames.size > 4 && stateReached && f.tMs - lastChange >= stableMs) break
        }
        return frames
    }

    private data class Metrics(
        val imeFirstOpenMs: Long,
        val imeSettledMs: Long,
        val dirChanges: Int,
        val motionPhases: Int,
        val lateScrollMs: Long,
        val fieldTravel: Float,
        val maxFieldSpeed: Float,
        val imeMax: Int,
        val imeOccludedField: Boolean,
        val endFieldBottom: Float,
        val endIme: Int,
        val endViewportBottom: Int
    )

    /**
     * Splits field motion into phases separated by >=[QUIET_MS] of stillness. One
     * phase = single ease (healthy). Two or more phases, or a direction reversal,
     * means the field was scrolled twice -> visible bounce.
     */
    private fun analyse(
        label: String,
        frames: List<Frame>,
        viewHeight: Int,
        baselineFieldBottom: Float
    ): Metrics {
        Log.i(TAG, "=== $label frames=${frames.size} ===")
        val t0 = frames.first().tMs
        var dirChanges = 0
        var lastDir = 0
        var maxFieldSpeed = 0f
        var motionPhases = 0
        var inMotion = false
        var stillSince = t0
        var imeSettledMs = -1L
        var lateScrollMs = -1L
        var travel = 0f
        var prev = frames.first()
        frames.forEachIndexed { index, f ->
            val dtMs = (f.tMs - prev.tMs).coerceAtLeast(1)
            val fieldVel = if (index == 0) 0f else (f.fieldTop - prev.fieldTop) / dtMs
            val imeVel = if (index == 0) 0f else (f.imeBottom - prev.imeBottom).toFloat() / dtMs
            val scrollDelta = if (index == 0) 0f else f.scroll - prev.scroll
            travel += kotlin.math.abs(f.fieldTop - prev.fieldTop)
            if (kotlin.math.abs(fieldVel) > maxFieldSpeed) maxFieldSpeed = kotlin.math.abs(fieldVel)

            val moving = kotlin.math.abs(fieldVel) > MOVE_EPS_PX_PER_MS ||
                kotlin.math.abs(scrollDelta) > 0.5f
            if (moving) {
                if (!inMotion && f.tMs - stillSince >= QUIET_MS) motionPhases += 1
                if (!inMotion && motionPhases == 0) motionPhases = 1
                inMotion = true
            } else {
                if (inMotion) stillSince = f.tMs
                inMotion = false
            }

            val dir = when {
                fieldVel > MOVE_EPS_PX_PER_MS -> 1
                fieldVel < -MOVE_EPS_PX_PER_MS -> -1
                else -> 0
            }
            if (dir != 0 && lastDir != 0 && dir != lastDir) dirChanges += 1
            if (dir != 0) lastDir = dir

            if (imeSettledMs < 0 && f.imeBottom > 0 && index > 0 && kotlin.math.abs(imeVel) <= 0.05f) {
                imeSettledMs = f.tMs - t0
            }
            if (imeSettledMs >= 0 && lateScrollMs < 0 &&
                (f.tMs - t0) > imeSettledMs + LATE_GRACE_MS &&
                kotlin.math.abs(scrollDelta) > 0.5f
            ) {
                lateScrollMs = f.tMs - t0
            }
            if (index == 0 || index == frames.lastIndex || moving || imeVel != 0f) {
                Log.i(
                    TAG,
                    "F%03d t=%d ime=%d(%+.2f) field=%.0f(%+.2f) scroll=%.0f/%.0f".format(
                        index, f.tMs - t0, f.imeBottom, imeVel, f.fieldTop, fieldVel,
                        f.scroll, f.scrollMax
                    )
                )
            }
            prev = f
        }
        val imeOpen = frames.firstOrNull { it.imeBottom > 0 }?.let { it.tMs - t0 } ?: -1L
        val imeMax = frames.maxOf { it.imeBottom }
        // Would the keyboard cover where the field rests when unscrolled?
        val occluded = viewHeight > 0 && baselineFieldBottom > 0f &&
            (viewHeight - imeMax) < baselineFieldBottom
        val last = frames.last()
        val m = Metrics(
            imeOpen, imeSettledMs, dirChanges, motionPhases, lateScrollMs, travel,
            maxFieldSpeed, imeMax, occluded, last.fieldBottom, last.imeBottom,
            viewHeight - last.imeBottom
        )
        Log.i(
            TAG,
            ("SUMMARY $label imeFirstOpen=%dms imeSettled=%dms dirChanges=%d motionPhases=%d " +
                "lateScrollAt=%dms fieldTravel=%.0fpx maxFieldSpeed=%.2fpx/ms imeMax=%d " +
                "occludesField=%b endFieldBottom=%.0f endViewportBottom=%d").format(
                m.imeFirstOpenMs, m.imeSettledMs, m.dirChanges, m.motionPhases,
                m.lateScrollMs, m.fieldTravel, m.maxFieldSpeed, imeMax, occluded,
                m.endFieldBottom, m.endViewportBottom
            )
        )
        Log.i(TAG, "=== end $label ===")
        return m
    }

    private fun openRecordEditor() {
        composeRule.waitUntil(15_000L) {
            composeRule.onAllNodesWithText("记录").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("记录").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithContentDescription("打开添加菜单").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("打开添加菜单").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithText("手动添加").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("手动添加").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodesWithTag("record-dose").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun frameLevelImeMotionAnalysis() {
        openRecordEditor()
        val focusMetrics = mutableListOf<Metrics>()
        composeRule.onNodeWithTag("record-dose").performScrollTo().performClick()
        composeRule.waitForIdle()
        // Focusing already opened the keyboard, so close it before measuring:
        // the baseline must be the field's resting position with no IME on screen.
        setImeVisible(false)
        sampleUntilStill(maxMs = 6_000L, stableMs = 600L, requireImeState = false)
        val viewHeight = readViewHeight()
        val baselineFieldBottom = readFrame().fieldBottom
        Log.i(TAG, "BASELINE viewHeight=$viewHeight fieldBottom=$baselineFieldBottom imeClosed")
        repeat(CYCLES) { cycle ->
            setImeVisible(true)
            val up = sampleUntilStill(maxMs = 8_000L, stableMs = 600L, requireImeState = true)
            focusMetrics += analyse("cycle-${cycle + 1}-focus", up, viewHeight, baselineFieldBottom)
            setImeVisible(false)
            analyse(
                "cycle-${cycle + 1}-hide",
                sampleUntilStill(maxMs = 8_000L, stableMs = 600L, requireImeState = false),
                viewHeight,
                baselineFieldBottom
            )
        }

        // Guard against a vacuous pass: if the keyboard never opened, this run
        // measured a static screen and says nothing about bounce.
        val opened = focusMetrics.count { it.imeFirstOpenMs >= 0 }
        val maxIme = focusMetrics.maxOf { it.imeMax }
        val occluding = focusMetrics.count { it.imeOccludedField }
        Log.i(
            TAG,
            "VERDICT imeOpenedCycles=$opened/$CYCLES occludingCycles=$occluding/$CYCLES " +
                "maxImeInset=${maxIme}px viewHeight=${viewHeight}px baselineFieldBottom=$baselineFieldBottom"
        )

        // A suggestion strip (~63px on an AVD with hw.keyboard=yes) satisfies
        // "ime > 0" without ever reaching the field. Such a run cannot observe
        // bounce at all, so it must fail as INCONCLUSIVE rather than pass green.
        assertTrue(
            "INCONCLUSIVE: IME never occluded the dose field (maxImeInset=${maxIme}px, " +
                "viewHeight=${viewHeight}px, baselineFieldBottom=$baselineFieldBottom). " +
                "The app had no reason to scroll, so dirChanges=0 is not evidence of a fix. " +
                "Use a device/AVD where a full soft keyboard renders (hw.keyboard=no).",
            occluding > 0
        )

        // Only cycles where the keyboard actually covered the field can testify.
        val withIme = focusMetrics.filter { it.imeFirstOpenMs >= 0 && it.imeOccludedField }
        val bouncing = withIme.filter { it.dirChanges > 0 || it.motionPhases > 1 || it.lateScrollMs >= 0 }
        Log.i(
            TAG,
            "VERDICT bouncingCycles=${bouncing.size}/${withIme.size} " +
                "dirChanges=${withIme.map { it.dirChanges }} " +
                "motionPhases=${withIme.map { it.motionPhases }} " +
                "lateScroll=${withIme.map { it.lateScrollMs }}"
        )
        assertTrue(
            "Field bounced in ${bouncing.size}/${withIme.size} cycles: " +
                "dirChanges=${withIme.map { it.dirChanges }} " +
                "motionPhases=${withIme.map { it.motionPhases }} " +
                "lateScrollAt=${withIme.map { it.lateScrollMs }}",
            bouncing.isEmpty()
        )

        // Absence of bounce is worthless if the field ends up under the keyboard.
        val hidden = withIme.filter { it.endFieldBottom > it.endViewportBottom + END_TOLERANCE_PX }
        assertTrue(
            "Field ended occluded by the keyboard in ${hidden.size}/${withIme.size} cycles: " +
                hidden.joinToString { "bottom=${it.endFieldBottom} viewport=${it.endViewportBottom}" },
            hidden.isEmpty()
        )
    }

    private companion object {
        const val TAG = "RealAppImeFrame"
        const val CYCLES = 5
        const val MOVE_EPS_PX_PER_MS = 0.05f
        const val QUIET_MS = 120L
        const val LATE_GRACE_MS = 80L
        const val END_TOLERANCE_PX = 4f
    }
}
