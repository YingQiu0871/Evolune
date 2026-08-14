package io.github.yingqiu0871.evolune.testime

import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout

/**
 * TEST-ONLY input method with a fixed, realistic keyboard height.
 *
 * The emulator's bundled Gboard is unconfigured and renders as a floating pill,
 * which reports a ~63px inset and therefore never occludes any field. That makes
 * IME-scroll behaviour unobservable. This IME instead presents a plain opaque
 * view of [KEYBOARD_HEIGHT_DP], so the platform emits a real, full-size
 * WindowInsets.Type.ime() animation. The app under test sees an ordinary
 * keyboard; nothing about the production inset/scroll path is stubbed.
 *
 * Lives in the androidTest APK only and is never present in a release build.
 */
class ProbeTestIme : InputMethodService() {

    override fun onCreateInputView(): View {
        val heightPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            KEYBOARD_HEIGHT_DP,
            resources.displayMetrics
        ).toInt()
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heightPx
            )
            minimumHeight = heightPx
            setBackgroundColor(0xFF202124.toInt())
        }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    companion object {
        /** Comparable to Gboard with its suggestion strip on a modern phone. */
        const val KEYBOARD_HEIGHT_DP = 450f
        const val ID = "io.github.yingqiu0871.evolune.debug.test/io.github.yingqiu0871.evolune.testime.ProbeTestIme"
    }
}
