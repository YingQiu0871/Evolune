package io.github.yingqiu0871.evolune.diagnostics

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val COMPOSE_DIAGNOSTICS_TAG = "EvoluneCompose"

private object ComposeDiagnostics {
    private val compositionCounts = ConcurrentHashMap<String, AtomicLong>()

    fun record(surface: String, state: String) {
        val count = compositionCounts
            .getOrPut(surface) { AtomicLong() }
            .incrementAndGet()
        Log.i(
            COMPOSE_DIAGNOSTICS_TAG,
            "surface=$surface count=$count state=$state"
        )
    }
}

/**
 * Debug-only evidence for successful Compose recompositions. Release builds are a no-op.
 */
@Composable
internal fun RecordComposeRecomposition(
    surface: String,
    state: String,
    recompositionToken: Any? = null
) {
    val isDebuggable = LocalContext.current.applicationInfo.flags and
        ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!isDebuggable) return
    // Capture the opaque token as a Compose parameter dependency without invoking
    // its equality/hash contract. The call sites pass unstable state objects, so
    // a parent invalidation still re-runs this diagnostic when the label is stable.
    val observedToken = recompositionToken
    SideEffect {
        @Suppress("UNUSED_EXPRESSION")
        observedToken
        ComposeDiagnostics.record(surface, state)
    }
}
