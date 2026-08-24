package io.github.yingqiu0871.evolune.ui.screens

import android.os.Build
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yingqiu0871.evolune.testime.ProbeTestIme
import org.junit.After
import org.junit.Before
import io.github.yingqiu0871.evolune.MainActivity
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val inputMethodManager = composeRule.activity.getSystemService(InputMethodManager::class.java)
            inputMethodManager?.hideSoftInputFromWindow(
                composeRule.activity.window.decorView.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
        shell("ime reset")
        shell("ime enable ${ProbeTestIme.ID}")
        shell("ime set ${ProbeTestIme.ID}")
        SystemClock.sleep(1_500)
        Log.i(
            TAG,
            "IME switched from=$originalIme to=${ProbeTestIme.ID} " +
                "windowFocus=${composeRule.activity.window.decorView.hasWindowFocus()}"
        )
    }

    @After
    fun restoreIme() {
        originalIme?.takeIf { it.isNotBlank() && it != "null" }?.let { shell("ime set $it") }
    }

    private data class Frame(
        val tMs: Long,
        val appRootWidth: Int,
        val appRootHeight: Int,
        val imeVisible: Boolean,
        val imeBottom: Int,
        val fieldTop: Float,
        val fieldBottom: Float,
        val scroll: Float,
        val scrollMax: Float
    )

    private data class ApplicationWindowMetrics(
        val width: Int,
        val height: Int,
        val imeVisible: Boolean,
        val imeBottom: Int
    )

    /**
     * Returns the stable application window that hosts the Compose semantics.
     *
     * The focused WindowManager root is not an application-root identity: when
     * the test IME is shown, Android can focus the IME's auxiliary window. Its
     * 840x555 geometry must never be combined with the app's full-window
     * semantics bounds. The Activity decor view is the explicit owner of the
     * content under test and receives the corresponding app WindowInsets.
     */
    private fun applicationContentRoot(): View {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        return composeRule.activity.window.decorView
    }

    private fun readApplicationWindowMetrics(): ApplicationWindowMetrics {
        var metrics: ApplicationWindowMetrics? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val root = applicationContentRoot()
            val insets = root.rootWindowInsets
            val imeInsets = insets?.getInsets(WindowInsets.Type.ime())
            metrics = ApplicationWindowMetrics(
                width = root.width,
                height = root.height,
                imeVisible = insets?.isVisible(WindowInsets.Type.ime()) == true,
                imeBottom = imeInsets?.bottom ?: 0
            )
        }
        val result = requireNotNull(metrics) { "Application window metrics were not read" }
        check(result.imeBottom in 0..result.height) {
            "Coordinate-space mismatch: IME bottom=${result.imeBottom} exceeds " +
                "application content root height=${result.height}; " +
                "imeVisible=${result.imeVisible}"
        }
        return result
    }

    /**
     * Drives the real platform IME animation. Each visible cycle re-activates
     * the editor field through Compose semantics, then asks the app's actual
     * IME control root to issue the platform show/hide command. That control
     * root is never used for geometry: the app window's scroll response to the
     * resulting inset change is what this probe measures; the trigger is real
     * user-facing focus, only made deterministic for repetition.
     */
    private fun setImeVisible(visible: Boolean) {
        if (visible) {
            // Re-activate the real editor target after the previous hide. This
            // restores the app window's input connection without selecting an
            // auxiliary IME root or bypassing the user-facing focus path.
            composeRule.onNodeWithTag("record-dose").performClick()
            composeRule.waitForIdle()
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val appRoot = applicationContentRoot()
            val controlRoot = imeControlRoot()
            val focused = controlRoot.findFocus() ?: appRoot.findFocus() ?: appRoot
            Log.i(
                TAG,
                "setImeVisible visible=$visible appRoot=${appRoot.javaClass.name} " +
                    "controlRoot=${controlRoot.javaClass.name} focus=$focused " +
                    "controlRootFocus=${controlRoot.hasWindowFocus()}"
            )
            if (Build.VERSION.SDK_INT >= 30) {
                controlRoot.windowInsetsController?.let { controller ->
                    if (visible) controller.show(WindowInsets.Type.ime())
                    else controller.hide(WindowInsets.Type.ime())
                }
            }
            val inputMethodManager = controlRoot.context
                .getSystemService(InputMethodManager::class.java)
            if (visible) {
                inputMethodManager?.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT)
            } else {
                inputMethodManager?.hideSoftInputFromWindow(
                    controlRoot.windowToken,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
        }
    }

    /**
     * Finds a root only for sending show/hide commands to the test IME. This
     * helper is intentionally not used for any geometry or insets measurement.
     * If Android exposes more than one non-application focused root, failing is
     * safer than guessing which auxiliary window owns IME control.
     */
    private fun imeControlRoot(): View {
        check(Looper.myLooper() == Looper.getMainLooper())
        val appRoot = applicationContentRoot()
        val auxiliaryRoots = windowRootsOnMainThread().filter {
            it !== appRoot && it.hasWindowFocus() && it.isShown
        }
        return when (auxiliaryRoots.size) {
            0 -> appRoot
            1 -> auxiliaryRoots.single()
            else -> error(
                "Ambiguous IME control roots: ${auxiliaryRoots.joinToString { root ->
                    "${root.javaClass.simpleName}:${root.width}x${root.height}"
                }}"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowRootsOnMainThread(): List<View> {
        check(Looper.myLooper() == Looper.getMainLooper())
        return runCatching {
            val type = Class.forName("android.view.WindowManagerGlobal")
            val global = type.getMethod("getInstance").invoke(null)
            val method = type.getDeclaredMethod("getWindowViews").apply { isAccessible = true }
            (method.invoke(global) as? List<*>)?.filterIsInstance<View>().orEmpty()
        }.getOrElse {
            runCatching {
                val type = Class.forName("android.view.WindowManagerGlobal")
                val global = type.getMethod("getInstance").invoke(null)
                val field = type.getDeclaredField("mViews").apply { isAccessible = true }
                (field.get(global) as? List<*>)?.filterIsInstance<View>().orEmpty()
            }.getOrDefault(emptyList())
        }
    }

    private fun readFrame(): Frame {
        val window = readApplicationWindowMetrics()
        val now = SystemClock.elapsedRealtime()
        val field = composeRule.onNodeWithTag("record-dose").fetchSemanticsNode()
        val scroll = composeRule.onNodeWithTag("record-editor-scroll").fetchSemanticsNode()
        requireSameApplicationWindow(
            label = "record-editor-scroll",
            bounds = scroll.boundsInWindow,
            window = window,
            requireFullyWithinWindow = true
        )
        requireSameApplicationWindow(
            label = "record-dose",
            bounds = field.boundsInWindow,
            window = window,
            requireFullyWithinWindow = false
        )
        val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        return Frame(
            now,
            window.width,
            window.height,
            window.imeVisible,
            window.imeBottom,
            field.boundsInWindow.top,
            field.boundsInWindow.bottom,
            range.value(),
            range.maxValue()
        )
    }

    private fun requireSameApplicationWindow(
        label: String,
        bounds: androidx.compose.ui.geometry.Rect,
        window: ApplicationWindowMetrics,
        requireFullyWithinWindow: Boolean
    ) {
        check(window.width > 0 && window.height > 0) {
            "Application content root is not laid out: ${window.width}x${window.height}; " +
                "$label bounds=$bounds"
        }
        val outsideLeft = -bounds.right
        val outsideTop = -bounds.bottom
        val outsideRight = bounds.left - window.width
        val outsideBottom = bounds.top - window.height
        val isOutside = outsideLeft > 0f || outsideTop > 0f ||
            outsideRight > 0f || outsideBottom > 0f
        if (requireFullyWithinWindow && isOutside) {
            error(
                "Coordinate-space mismatch: $label boundsInWindow=$bounds are outside " +
                    "application content root ${window.width}x${window.height}; " +
                    "imeVisible=${window.imeVisible} imeBottom=${window.imeBottom}"
            )
        }
        if (!requireFullyWithinWindow && isOutside) {
            val outsideDistance = maxOf(
                outsideLeft.coerceAtLeast(0f),
                outsideTop.coerceAtLeast(0f),
                outsideRight.coerceAtLeast(0f),
                outsideBottom.coerceAtLeast(0f)
            )
            val nodeExtent = maxOf(bounds.width, bounds.height)
            if (outsideDistance > nodeExtent) {
                error(
                    "Coordinate-space mismatch: $label boundsInWindow=$bounds are not " +
                        "in application content root ${window.width}x${window.height}; " +
                        "imeVisible=${window.imeVisible} imeBottom=${window.imeBottom}"
                )
            }
        }
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
                stateReached = if (requireImeState) f.imeVisible else !f.imeVisible
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

    private fun assertImeSettled(
        label: String,
        frames: List<Frame>,
        expectedVisible: Boolean
    ) {
        assertTrue(
            "$label did not settle with imeVisible=$expectedVisible; " +
                "lastFrame=${frames.lastOrNull()}",
            frames.lastOrNull()?.imeVisible == expectedVisible
        )
    }

    private data class Metrics(
        val appRootWidth: Int,
        val appRootHeight: Int,
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

            if (imeSettledMs < 0 && f.imeVisible && index > 0 && kotlin.math.abs(imeVel) <= 0.05f) {
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
                    "F${index.toString().padStart(3, '0')} t=${f.tMs - t0} " +
                        "appRoot=${f.appRootWidth}x${f.appRootHeight} " +
                        "imeVisible=${f.imeVisible} ime=${f.imeBottom}($imeVel) " +
                        "field=${f.fieldTop}..${f.fieldBottom}($fieldVel) " +
                        "viewport=${f.appRootHeight - f.imeBottom} " +
                        "scroll=${f.scroll}/${f.scrollMax}"
                )
            }
            prev = f
        }
        val imeOpen = frames.firstOrNull { it.imeVisible }?.let { it.tMs - t0 } ?: -1L
        val imeMax = frames.maxOf { it.imeBottom }
        // Would the keyboard cover where the field rests when unscrolled?
        val occlusionFrame = frames.maxByOrNull { it.imeBottom } ?: frames.last()
        val occluded = baselineFieldBottom > 0f &&
            (occlusionFrame.appRootHeight - imeMax) < baselineFieldBottom
        val last = frames.last()
        val m = Metrics(
            last.appRootWidth,
            last.appRootHeight,
            imeOpen, imeSettledMs, dirChanges, motionPhases, lateScrollMs, travel,
            maxFieldSpeed, imeMax, occluded, last.fieldBottom, last.imeBottom,
            last.appRootHeight - last.imeBottom
        )
        Log.i(
            TAG,
            "SUMMARY $label appRoot=${m.appRootWidth}x${m.appRootHeight} " +
                "imeFirstOpen=${m.imeFirstOpenMs}ms imeSettled=${m.imeSettledMs}ms " +
                "dirChanges=${m.dirChanges} motionPhases=${m.motionPhases} " +
                "lateScrollAt=${m.lateScrollMs}ms fieldTravel=${m.fieldTravel}px " +
                "maxFieldSpeed=${m.maxFieldSpeed}px/ms imeMax=$imeMax " +
                "occludesField=$occluded endFieldBottom=${m.endFieldBottom} " +
                "endViewportBottom=${m.endViewportBottom}"
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
        composeRule.onNodeWithTag("record-dose")
            .performScrollTo()
            .performClick()
            .performTextInput("2")
        composeRule.waitForIdle()
        // Focusing already opened the keyboard, so close it before measuring:
        // the baseline must be the field's resting position with no IME on screen.
        setImeVisible(false)
        val baselineFrames = sampleUntilStill(
            maxMs = 6_000L,
            stableMs = 600L,
            requireImeState = false
        )
        assertImeSettled("baseline hide", baselineFrames, expectedVisible = false)
        val baseline = readFrame()
        val baselineFieldBottom = baseline.fieldBottom
        Log.i(
            TAG,
            "BASELINE appRoot=${baseline.appRootWidth}x${baseline.appRootHeight} " +
                "fieldBounds=${baseline.fieldTop}..${baseline.fieldBottom} " +
                "imeVisible=${baseline.imeVisible} imeBottom=${baseline.imeBottom}"
        )
        repeat(CYCLES) { cycle ->
            setImeVisible(true)
            val up = sampleUntilStill(maxMs = 8_000L, stableMs = 600L, requireImeState = true)
            assertImeSettled("cycle-${cycle + 1}-focus", up, expectedVisible = true)
            focusMetrics += analyse("cycle-${cycle + 1}-focus", up, baselineFieldBottom)
            setImeVisible(false)
            val down = sampleUntilStill(
                maxMs = 8_000L,
                stableMs = 600L,
                requireImeState = false
            )
            assertImeSettled("cycle-${cycle + 1}-hide", down, expectedVisible = false)
            analyse(
                "cycle-${cycle + 1}-hide",
                down,
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
                "maxImeInset=${maxIme}px appRoot=" +
                "${focusMetrics.firstOrNull()?.appRootWidth}x${focusMetrics.firstOrNull()?.appRootHeight} " +
                "baselineFieldBottom=$baselineFieldBottom"
        )

        assertTrue(
            "INCONCLUSIVE: IME was stably shown in only $opened/$CYCLES cycles; " +
                "maxImeInset=${maxIme}px. Every cycle must observe the real app " +
                "IME before motion analysis is considered evidence.",
            opened == CYCLES
        )

        // A suggestion strip (~63px on an AVD with hw.keyboard=yes) satisfies
        // "ime > 0" without ever reaching the field. Such a run cannot observe
        // bounce at all, so it must fail as INCONCLUSIVE rather than pass green.
        assertTrue(
            "INCONCLUSIVE: IME never occluded the dose field (maxImeInset=${maxIme}px, " +
                "appRoot=${focusMetrics.firstOrNull()?.appRootWidth}x" +
                "${focusMetrics.firstOrNull()?.appRootHeight}, " +
                "baselineFieldBottom=$baselineFieldBottom). " +
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
