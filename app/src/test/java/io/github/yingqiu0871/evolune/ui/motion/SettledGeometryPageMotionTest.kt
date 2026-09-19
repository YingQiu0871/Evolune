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
    fun `route edge tracker previews committed route changes`() {
        val tracker = RouteEdgeTracker()

        val startup = tracker.preview(Screen.HOME.route)
        assertEquals(null, startup.fromRoute)
        assertEquals(Screen.HOME.route, startup.toRoute)
        assertFalse(startup.crossesPrimaryChromeBoundary())
        tracker.commit(startup)

        val forward = tracker.preview(INSIGHTS_ROUTE)
        assertEquals(Screen.HOME.route, forward.fromRoute)
        assertEquals(INSIGHTS_ROUTE, forward.toRoute)
        assertTrue(forward.crossesPrimaryChromeBoundary())
        tracker.commit(forward)

        val back = tracker.preview(Screen.HOME.route)
        assertEquals(INSIGHTS_ROUTE, back.fromRoute)
        assertEquals(Screen.HOME.route, back.toRoute)
        assertTrue(back.crossesPrimaryChromeBoundary())
        tracker.commit(back)

        // Distinct edges -> distinct motion keys; one event per committed change.
        assertEquals(listOf(0L, 1L, 2L), listOf(startup.id, forward.id, back.id))
    }

    @Test
    fun `abandoned preview leaves the committed tracker state unchanged`() {
        val tracker = RouteEdgeTracker()
        tracker.commit(tracker.preview(Screen.HISTORY.route))

        val abandoned = tracker.preview(INSIGHTS_ROUTE)
        val laterLegitimatePreview = tracker.preview(INSIGHTS_ROUTE)

        assertEquals(Screen.HISTORY.route, laterLegitimatePreview.fromRoute)
        assertEquals(INSIGHTS_ROUTE, laterLegitimatePreview.toRoute)
        assertEquals(abandoned.id, laterLegitimatePreview.id)

        tracker.commit(laterLegitimatePreview)
        val next = tracker.preview(Screen.HISTORY.route)
        assertEquals(INSIGHTS_ROUTE, next.fromRoute)
        assertEquals(1L + abandoned.id, next.id)
    }

    @Test
    fun `commit is idempotent for repeated SideEffect execution`() {
        val tracker = RouteEdgeTracker()
        tracker.commit(tracker.preview(Screen.HOME.route))

        val edge = tracker.preview(INSIGHTS_ROUTE)
        tracker.commit(edge)
        tracker.commit(edge)
        tracker.commit(edge)

        val next = tracker.preview(Screen.HISTORY.route)
        assertEquals(INSIGHTS_ROUTE, next.fromRoute)
        assertEquals(Screen.HISTORY.route, next.toRoute)
        assertEquals(edge.id + 1L, next.id)
    }

    @Test
    fun `repeated route cycles receive fresh ids and preserve both directions`() {
        val tracker = RouteEdgeTracker()
        tracker.commit(tracker.preview(Screen.HISTORY.route))

        val forward = tracker.preview(INSIGHTS_ROUTE)
        tracker.commit(forward)
        val back = tracker.preview(Screen.HISTORY.route)
        tracker.commit(back)
        val forwardAgain = tracker.preview(INSIGHTS_ROUTE)
        tracker.commit(forwardAgain)
        val edges = listOf(forward, back, forwardAgain)

        assertEquals(
            listOf(
                Screen.HISTORY.route to INSIGHTS_ROUTE,
                INSIGHTS_ROUTE to Screen.HISTORY.route,
                Screen.HISTORY.route to INSIGHTS_ROUTE
            ),
            edges.map { it.fromRoute to it.toRoute }
        )
        assertTrue(edges.zipWithNext().all { (first, second) -> second.id > first.id })
        assertTrue(edges.all(NavigationEdgeMotion::crossesPrimaryChromeBoundary))
    }

    @Test
    fun `same route recomposition reuses the cached edge without fabricating a new one`() {
        val tracker = RouteEdgeTracker()
        val home = tracker.preview(Screen.HOME.route)
        tracker.commit(home)
        tracker.commit(home)

        val records = tracker.preview(Screen.RECORDS.route)
        assertEquals(Screen.HOME.route, records.fromRoute)
        assertEquals(Screen.RECORDS.route, records.toRoute)
        assertEquals(home.id + 1L, records.id)
        assertTrue(records.id > home.id)
        assertFalse(records.crossesPrimaryChromeBoundary())
        tracker.commit(records)

        val next = tracker.preview(Screen.HOME.route)
        assertEquals(Screen.RECORDS.route, next.fromRoute)
        assertEquals(home.id + 2L, next.id)
    }
}
