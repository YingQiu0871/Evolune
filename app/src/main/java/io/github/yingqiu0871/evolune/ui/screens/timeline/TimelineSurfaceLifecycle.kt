package io.github.yingqiu0871.evolune.ui.screens.timeline

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
import io.github.yingqiu0871.evolune.history.timeline.TimelineViewModel

/**
 * V17-D-04 §6 — lifecycle plumbing for the Timeline surface. It renders nothing and defines no
 * layout, colour or copy: the screen hosts this bridge inside it.
 *
 * Contract (identical shape to the A-04 History / B-02 Insights / C-04 Retrospective wiring, so
 * there is no third "screen visible" mechanism):
 * - the keyed effect fires once per composition entry, i.e. on a return from another destination;
 *   the first entry is owned by the ViewModel's initial D-03 load, so a cold start performs one
 *   read;
 * - a real `ON_STOP` → `ON_START` transition while this surface is active triggers one foreground
 *   refresh/load with a fresh capture; the "was stopped" bit keeps the cold-start `ON_START` from
 *   refreshing twice;
 * - the observer only exists while this composable is composed, so the surface never polls, never
 *   runs a timer and never holds a live subscription.
 */
@Composable
fun TimelineSurfaceLifecycle(viewModel: TimelineViewModel) {
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
