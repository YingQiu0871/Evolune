package io.github.yingqiu0871.evolune.navigation

import io.github.yingqiu0871.evolune.navigation.Screen.HISTORY
import io.github.yingqiu0871.evolune.navigation.Screen.HOME
import io.github.yingqiu0871.evolune.navigation.Screen.MEDICATION_PLANS
import io.github.yingqiu0871.evolune.navigation.Screen.RECORDS
import io.github.yingqiu0871.evolune.navigation.Screen.SETTINGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.3 navigation-jank hotfix — pure policy for primary navigation chrome.
 *
 * Chrome-changing navigation must suppress the shared page fade/scale motion
 * (the chrome appearing/disappearing changes the NavHost geometry in the same
 * frame); same-geometry navigation keeps the released animation.
 */
class PrimaryNavigationChromeTest {

    @Test
    fun `full-screen routes are exactly the released child destinations`() {
        assertEquals(
            setOf(
                ABOUT_ROUTE,
                GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE,
                ONBOARDING_ROUTE,
                DISCLOSURES_ROUTE,
                FEATURE_TUTORIAL_ROUTE,
                INSIGHTS_ROUTE,
                RETROSPECTIVE_ROUTE,
                TIMELINE_ROUTE
            ),
            PrimaryNavigationChrome.FULL_SCREEN_ROUTES
        )
    }

    @Test
    fun `top-level tabs and full-screen children classify correctly`() {
        listOf(HOME.route, RECORDS.route, HISTORY.route, MEDICATION_PLANS.route, SETTINGS.route)
            .forEach { route ->
                assertTrue("$route shows chrome", PrimaryNavigationChrome.shows(route))
            }
        PrimaryNavigationChrome.FULL_SCREEN_ROUTES.forEach { route ->
            assertFalse("$route hides chrome", PrimaryNavigationChrome.shows(route))
        }
        assertTrue("unknown/null route keeps chrome", PrimaryNavigationChrome.shows(null))
    }

    @Test
    fun `chrome-changing navigation suppresses page animation`() {
        val chromeChanging = listOf(
            HISTORY.route to INSIGHTS_ROUTE,
            INSIGHTS_ROUTE to HISTORY.route,
            HISTORY.route to RETROSPECTIVE_ROUTE,
            RETROSPECTIVE_ROUTE to HISTORY.route,
            HISTORY.route to TIMELINE_ROUTE,
            TIMELINE_ROUTE to HISTORY.route,
            SETTINGS.route to ABOUT_ROUTE,
            ABOUT_ROUTE to SETTINGS.route,
            SETTINGS.route to GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE,
            GOOGLE_DRIVE_BACKUP_RESTORE_ROUTE to SETTINGS.route
        )
        chromeChanging.forEach { (from, to) ->
            assertTrue("$from -> $to must change chrome", PrimaryNavigationChrome.changes(from, to))
            assertTrue("$to -> $from must change chrome", PrimaryNavigationChrome.changes(to, from))
        }
    }

    @Test
    fun `same-geometry navigation keeps the released page animation`() {
        val sameChrome = listOf(
            HOME.route to RECORDS.route,
            RECORDS.route to HISTORY.route,
            HISTORY.route to MEDICATION_PLANS.route,
            MEDICATION_PLANS.route to SETTINGS.route,
            SETTINGS.route to HOME.route,
            ABOUT_ROUTE to DISCLOSURES_ROUTE,
            DISCLOSURES_ROUTE to ABOUT_ROUTE,
            INSIGHTS_ROUTE to RETROSPECTIVE_ROUTE,
            TIMELINE_ROUTE to INSIGHTS_ROUTE
        )
        sameChrome.forEach { (from, to) ->
            assertFalse("$from -> $to must keep chrome", PrimaryNavigationChrome.changes(from, to))
            assertFalse("$to -> $from must keep chrome", PrimaryNavigationChrome.changes(to, from))
        }
    }

    @Test
    fun `unknown or missing routes do not fabricate a chrome change`() {
        assertFalse(PrimaryNavigationChrome.changes(null, null))
        assertFalse(PrimaryNavigationChrome.changes(HISTORY.route, HISTORY.route))
        assertTrue(PrimaryNavigationChrome.changes(null, ABOUT_ROUTE))
    }
}
