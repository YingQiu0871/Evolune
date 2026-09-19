package io.github.yingqiu0871.evolune.ui.motion

import io.github.yingqiu0871.evolune.navigation.ABOUT_ROUTE
import io.github.yingqiu0871.evolune.navigation.DISCLOSURES_ROUTE
import io.github.yingqiu0871.evolune.navigation.GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE
import io.github.yingqiu0871.evolune.navigation.INSIGHTS_ROUTE
import io.github.yingqiu0871.evolune.navigation.RETROSPECTIVE_ROUTE
import io.github.yingqiu0871.evolune.navigation.Screen
import io.github.yingqiu0871.evolune.navigation.TIMELINE_ROUTE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.4 — pure policy for the settled-geometry entrance motion.
 *
 * Chrome-changing edges must run exactly one local entrance inside the final
 * geometry; same-geometry edges must stay inert (the NavHost page transition is
 * authoritative there); startup/null edges must not fabricate a motion event.
 */
class SettledGeometryPageMotionTest {

    private fun edge(from: String?, to: String?, id: Long = 0L) =
        NavigationEdgeMotion(fromRoute = from, toRoute = to, id = id)

    @Test
    fun `chrome-changing edges require the settled entrance in both directions`() {
        val boundaries = listOf(
            Screen.HISTORY.route to INSIGHTS_ROUTE,
            Screen.HISTORY.route to RETROSPECTIVE_ROUTE,
            Screen.HISTORY.route to TIMELINE_ROUTE,
            Screen.SETTINGS.route to ABOUT_ROUTE,
            Screen.SETTINGS.route to GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE
        )
        boundaries.forEach { (from, to) ->
            assertTrue("$from -> $to", edge(from, to).crossesPrimaryChromeBoundary())
            assertTrue("$to -> $from (Back)", edge(to, from).crossesPrimaryChromeBoundary())
        }
    }

    @Test
    fun `same-geometry edges stay inert`() {
        val sameGeometry = listOf(
            Screen.HOME.route to Screen.RECORDS.route,
            Screen.RECORDS.route to Screen.HISTORY.route,
            Screen.HISTORY.route to Screen.MEDICATION_PLANS.route,
            Screen.MEDICATION_PLANS.route to Screen.SETTINGS.route,
            INSIGHTS_ROUTE to RETROSPECTIVE_ROUTE,
            RETROSPECTIVE_ROUTE to TIMELINE_ROUTE,
            ABOUT_ROUTE to DISCLOSURES_ROUTE
        )
        sameGeometry.forEach { (from, to) ->
            assertFalse("$from -> $to", edge(from, to).crossesPrimaryChromeBoundary())
            assertFalse("$to -> $from", edge(to, from).crossesPrimaryChromeBoundary())
        }
    }

    @Test
    fun `startup and null edges do not fabricate motion`() {
        assertFalse(edge(null, null).crossesPrimaryChromeBoundary())
        assertFalse(edge(null, Screen.HOME.route).crossesPrimaryChromeBoundary())
        assertFalse(edge(Screen.HOME.route, null).crossesPrimaryChromeBoundary())
        assertFalse(edge(Screen.HISTORY.route, Screen.HISTORY.route).crossesPrimaryChromeBoundary())
    }

    @Test
    fun `route edge tracker emits exactly one edge per committed route change`() {
        val tracker = RouteEdgeTracker()

        val startup = tracker.onRouteComposed(Screen.HOME.route)
        assertEquals(null, startup.fromRoute)
        assertEquals(Screen.HOME.route, startup.toRoute)
        assertFalse(startup.crossesPrimaryChromeBoundary())

        val forward = tracker.onRouteComposed(INSIGHTS_ROUTE)
        assertEquals(Screen.HOME.route, forward.fromRoute)
        assertEquals(INSIGHTS_ROUTE, forward.toRoute)
        assertTrue(forward.crossesPrimaryChromeBoundary())

        val back = tracker.onRouteComposed(Screen.HOME.route)
        assertEquals(INSIGHTS_ROUTE, back.fromRoute)
        assertEquals(Screen.HOME.route, back.toRoute)
        assertTrue(back.crossesPrimaryChromeBoundary())

        // Distinct edges -> distinct motion keys; one event per committed change.
        assertEquals(listOf(0L, 1L, 2L), listOf(startup.id, forward.id, back.id))
    }

    @Test
    fun `repeated same-route composition is not a new edge in the tracker contract`() {
        val tracker = RouteEdgeTracker()
        tracker.onRouteComposed(Screen.HISTORY.route)
        // The UI only calls onRouteComposed from a remember(route) scope, so a
        // same-route recomposition never reaches the tracker; this documents the
        // resulting edge shape if it ever did: same route -> no chrome change.
        val sameRoute = tracker.onRouteComposed(Screen.HISTORY.route)
        assertEquals(Screen.HISTORY.route, sameRoute.fromRoute)
        assertEquals(Screen.HISTORY.route, sameRoute.toRoute)
        assertFalse(sameRoute.crossesPrimaryChromeBoundary())
    }
}
