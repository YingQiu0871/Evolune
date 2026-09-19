package io.github.yingqiu0871.evolune.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class MotionListUxBoundaryTest {
    @Test
    fun `record list keeps newest-first stable identity with item animation`() {
        val source = source("ui/screens/MedicationRecordsScreen.kt")

        assertTrue(source.contains("events.sortedByDescending { it.occurredAt }"))
        assertTrue(source.contains("key = { it.id }"))
        assertTrue(source.contains("modifier = Modifier.animateItem()"))
    }

    @Test
    fun `record and plan overlays share session-driven transition host`() {
        val source = source("navigation/AppNavigation.kt")
        val hostSource = source("ui/components/EditorTransitionHost.kt")
        val motionSource = source("ui/motion/EvolunePageMotion.kt")

        assertTrue(source.contains("session = recordEditSession"))
        assertTrue(source.contains("session = planEditSession"))
        assertTrue(source.split("EditorTransitionHost(").size - 1 >= 2)
        assertFalse(source.contains("if (recordEditSession != null)"))
        assertFalse(source.contains("if (planEditSession != null)"))
        assertTrue(hostSource.contains("evolunePageEnterTransition(startImmediately = true)"))
        assertTrue(hostSource.contains("evolunePageExitTransition()"))
        assertTrue(hostSource.contains("sizeTransform = null"))
        assertFalse(hostSource.contains("slideIn"))
        assertFalse(hostSource.contains("slideOut"))
        assertFalse(hostSource.contains("BottomEnd"))
        assertFalse(hostSource.contains("scaleOut"))
        assertTrue(source.split("evolunePageEnterTransition()").size - 1 >= 2)
        assertTrue(source.split("evolunePageExitTransition()").size - 1 >= 2)
        assertTrue(motionSource.contains("initialScale = PAGE_INITIAL_SCALE"))
        assertTrue(motionSource.contains("TransformOrigin.Center"))
        assertTrue(motionSource.contains("PAGE_INITIAL_SCALE = 0.98f"))
        assertTrue(motionSource.contains("NO_PAGE_ENTER_DELAY_MILLIS = 0"))
        assertFalse(motionSource.contains("slideIn"))
        assertFalse(motionSource.contains("slideOut"))
        assertFalse(motionSource.contains("BottomEnd"))
    }

    @Test
    fun `navhost suppresses page motion only when primary chrome changes`() {
        val source = source("navigation/AppNavigation.kt")
        val policySource = source("navigation/PrimaryNavigationChrome.kt")

        // The chrome predicate is the single source of truth (no duplicated route list).
        assertTrue(policySource.contains("fun shows(route: String?)"))
        assertTrue(policySource.contains("fun changes(fromRoute: String?, toRoute: String?)"))
        assertTrue(source.contains("val isFullScreenSubroute = !PrimaryNavigationChrome.shows(currentRoute)"))

        // All four transition boundaries consult the same chrome-change policy.
        val chromeRule =
            "PrimaryNavigationChrome.changes(initialState.destination.route, targetState.destination.route)"
        assertTrue(source.split(chromeRule).size - 1 == 4)

        // Chrome-changing navigation uses None on both sides; the same-geometry path
        // keeps the released page motion.
        assertTrue(source.split("EnterTransition.None").size - 1 == 2)
        assertTrue(source.split("ExitTransition.None").size - 1 == 2)
        assertTrue(source.contains("popEnterTransition = {"))
        assertTrue(source.contains("popExitTransition = {"))

        // No new motion vocabulary anywhere in the navigation layer.
        assertFalse(source.contains("slideIn"))
        assertFalse(source.contains("slideOut"))
        assertFalse(source.contains("AnimatedVisibility"))
        assertFalse(source.contains("MutableTransitionState"))
    }

    @Test
    fun `settled geometry motion refines chrome boundaries without double animation`() {
        val source = source("navigation/AppNavigation.kt")
        val settled = source("ui/motion/SettledGeometryPageMotion.kt")
        val motion = source("ui/motion/EvolunePageMotion.kt")

        // v1.7.3 anti-jank rule intact: one edge tracker, chrome-aware modifier.
        assertTrue(source.contains("RouteEdgeTracker()"))
        assertTrue(source.contains("import androidx.compose.runtime.SideEffect"))
        assertTrue(source.contains("val navigationEdge = remember(currentRoute)"))
        assertTrue(source.contains("routeEdgeTracker.preview(currentRoute)"))
        assertTrue(source.contains("SideEffect {"))
        assertTrue(source.contains("routeEdgeTracker.commit(navigationEdge)"))
        assertFalse(source.contains("onRouteComposed"))
        assertTrue(source.contains(".settledGeometryPageMotion(navigationEdge)"))

        val trackerSource = settled.substringAfter("internal class RouteEdgeTracker")
            .substringBefore("@Composable")
        val previewSource = trackerSource.substringAfter("fun preview")
            .substringBefore("fun commit")
        assertTrue(trackerSource.contains("fun preview"))
        assertTrue(trackerSource.contains("fun commit"))
        assertFalse(previewSource.contains("previousRoute ="))
        assertFalse(previewSource.contains("nextId +="))

        // v1.7.4 refinement: the entrance is gated by the chrome-boundary policy and
        // runs inside the FINAL geometry after the first committed frame.
        assertTrue(settled.contains("PrimaryNavigationChrome.changes(fromRoute, toRoute)"))
        assertTrue(settled.contains("if (entranceRequired)"))
        assertTrue(settled.contains("withFrameNanos"))
        assertTrue(settled.contains("graphicsLayer"))
        assertTrue(settled.contains("PAGE_INITIAL_SCALE"))
        assertTrue(settled.contains("PAGE_FADE_IN_MILLIS"))
        assertTrue(settled.contains("snapTo"))
        assertTrue(settled.contains("animateTo"))

        // Shared motion language: the existing constants are reused, not duplicated.
        assertTrue(motion.contains("internal const val PAGE_FADE_IN_MILLIS = 220"))
        assertTrue(motion.contains("internal const val PAGE_INITIAL_SCALE = 0.98f"))

        // No new motion vocabulary, no chrome animation, no scale-out, no spring.
        listOf("slideIn", "slideOut", "scaleOut", "spring(", "bounce", "animateContentSize")
            .forEach { term ->
                assertFalse("settled motion must not contain '$term'", settled.contains(term))
            }
        assertFalse(settled.contains("BottomNavigationBar"))
        assertFalse(settled.contains("NavigationRailBar"))

        // Same-geometry path still uses the original NavHost page transitions.
        assertTrue(source.split("evolunePageEnterTransition()").size - 1 >= 2)
        assertTrue(source.split("evolunePageExitTransition()").size - 1 >= 2)
    }

    @Test
    fun `editor layers cover a persistent top-level scaffold`() {
        val source = source("navigation/AppNavigation.kt")

        assertTrue(source.contains("Box(modifier = Modifier.fillMaxSize())"))
        assertTrue(source.contains("The top-level Scaffold stays intact"))
        assertTrue(source.contains("BottomNavigationBar(navController = navController)"))
        assertTrue(source.contains("NavigationRailBar(navController = navController)"))
        assertTrue(source.split("modifier = Modifier.fillMaxSize()").size - 1 >= 2)
        assertFalse(source.contains("navigationChromeState"))
        assertFalse(source.contains("MutableTransitionState"))
        assertFalse(source.contains("AnimatedVisibility"))
        assertFalse(source.contains("delay("))
    }

    private fun source(relativePath: String): String = Files.readString(
        Path.of("src/main/java/io/github/yingqiu0871/evolune/$relativePath")
    )
}
