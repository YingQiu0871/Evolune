package io.github.yingqiu0871.evolune.history.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Lifecycle plumbing for the Insights surface. It renders nothing and defines no layout, colour or
 * copy: B-03 supplies the actual screen and hosts this bridge inside it.
 *
 * Contract (same shape as the A-04 History wiring, so there is no third "screen visible"
 * mechanism):
 * - the keyed effect fires once per composition entry, i.e. on a return from another destination;
 *   the first entry is owned by the ViewModel's initial load, so a cold start performs one read;
 * - a real `ON_STOP` → `ON_START` transition while Insights is active triggers one foreground
 *   refresh. The "was stopped" bit keeps the cold-start `ON_START` from loading twice;
 * - the observer only exists while this composable is composed, so Insights never polls in the
 *   background.
 */
@Composable
fun InsightsSurfaceLifecycle(viewModel: InsightsViewModel) {
    LaunchedEffect(Unit) { viewModel.onSurfaceShown() }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wentToBackground by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wentToBackground = true
                Lifecycle.Event.ON_START -> if (wentToBackground) {
                    wentToBackground = false
                    viewModel.onAppForegrounded()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
