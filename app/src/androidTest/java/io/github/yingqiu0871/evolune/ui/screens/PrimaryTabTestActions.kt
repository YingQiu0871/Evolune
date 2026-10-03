package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** Mirrors `AppNavigation.NAV_CLICK_THROTTLE_MS`. */
internal const val NAV_CLICK_THROTTLE_MS = 200L

/** The bottom-bar tag for [route], or the rail tag on wide layouts. */
internal fun ComposeTestRule.primaryTabTag(route: String): String =
    if (onAllNodesWithTag("nav-bar-$route").fetchSemanticsNodes().isNotEmpty()) {
        "nav-bar-$route"
    } else {
        "nav-rail-$route"
    }

/**
 * Selects a primary tab and waits until it actually reports `Selected`.
 *
 * The bottom bar / rail drops clicks within 200 ms of the previous navigation, so a click can be
 * lost. After each click this waits for the selection itself (not a fixed sleep) for one throttle
 * window, then clicks again, until [timeoutMillis] runs out.
 */
internal fun ComposeTestRule.selectPrimaryTab(route: String, timeoutMillis: Long = 5_000L) {
    val target = primaryTabTag(route)
    val isSelected = {
        onAllNodesWithTag(target)
            .fetchSemanticsNodes()
            .any { it.config.getOrNull(SemanticsProperties.Selected) == true }
    }
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        onNodeWithTag(target).performClick()
        try {
            waitUntil(NAV_CLICK_THROTTLE_MS + 100L, isSelected)
            return
        } catch (_: ComposeTimeoutException) {
            // The click fell inside the throttle window; the window has passed now, so retry.
        }
    }
    error("tab $route did not become selected")
}
