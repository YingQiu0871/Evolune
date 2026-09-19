package io.github.yingqiu0871.evolune.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.github.yingqiu0871.evolune.navigation.PrimaryNavigationChrome
import kotlinx.coroutines.launch

/**
 * v1.7.4 — settled-geometry page motion.
 *
 * The v1.7.3 anti-jank rule stays: when a navigation changes the primary navigation
 * chrome (bottom bar / rail), the NavHost boundary transition is None, so the
 * outgoing page disappears instantly and the incoming page composes directly in its
 * FINAL geometry. This modifier then animates ONLY the new destination content inside
 * that settled geometry with the same Evolune language (alpha 0 -> 1, scale
 * 0.98 -> 1.0, 220 ms, Center origin, no artificial delay).
 *
 * Same-geometry navigation is untouched: the modifier is inert and the existing
 * NavHost page transitions remain authoritative (no double animation).
 */
internal data class NavigationEdgeMotion(
    val fromRoute: String?,
    val toRoute: String?,
    val id: Long
)

/** Pure policy: does this edge cross the primary-chrome boundary? */
internal fun NavigationEdgeMotion.crossesPrimaryChromeBoundary(): Boolean =
    PrimaryNavigationChrome.changes(fromRoute, toRoute)

/**
 * Tracks committed route edges. [onRouteComposed] is called exactly once per route
 * change (from a `remember(route)` scope), so unrelated recompositions or ViewModel
 * state changes can never fabricate or retrigger an edge.
 */
internal class RouteEdgeTracker {
    private var previousRoute: String? = null
    private var nextId: Long = 0L

    fun onRouteComposed(route: String?): NavigationEdgeMotion {
        val edge = NavigationEdgeMotion(
            fromRoute = previousRoute,
            toRoute = route,
            id = nextId
        )
        nextId += 1L
        previousRoute = route
        return edge
    }
}

@Composable
internal fun Modifier.settledGeometryPageMotion(edge: NavigationEdgeMotion): Modifier {
    val entranceRequired = edge.crossesPrimaryChromeBoundary()
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    var appliedEdgeId by remember { mutableStateOf(-1L) }
    val pendingEntrance = entranceRequired && edge.id != appliedEdgeId

    LaunchedEffect(edge.id, entranceRequired) {
        if (entranceRequired) {
            // Apply the initial motion state before the first animated target frame.
            scale.snapTo(PAGE_INITIAL_SCALE)
            alpha.snapTo(0f)
            appliedEdgeId = edge.id
            // Let the chrome switch and the destination's final layout commit first.
            withFrameNanos { }
            launch { scale.animateTo(1f, tween(durationMillis = PAGE_FADE_IN_MILLIS)) }
            alpha.animateTo(1f, tween(durationMillis = PAGE_FADE_IN_MILLIS))
        } else {
            scale.snapTo(1f)
            alpha.snapTo(1f)
            appliedEdgeId = edge.id
        }
    }

    return graphicsLayer {
        val appliedScale = if (pendingEntrance) PAGE_INITIAL_SCALE else scale.value
        val appliedAlpha = if (pendingEntrance) 0f else alpha.value
        scaleX = appliedScale
        scaleY = appliedScale
        this.alpha = appliedAlpha
    }
}
