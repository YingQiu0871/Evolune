package io.github.yingqiu0871.evolune.ui.screens.timeline

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode

/**
 * Scrolls the Timeline body (a lazy list holding the calendar region and the day sections) until
 * [tag] is composed and visible.
 *
 * Selecting a day that has a section scrolls the body to that section. On a short screen this
 * takes the calendar region, and with it the day strip, out of composition, and sections far from
 * the current position are not composed either. Geometry assertions call this first instead of
 * assuming everything fits on a 1080x2400 screen.
 */
internal fun ComposeTestRule.scrollTimelineBodyTo(tag: String) {
    onNodeWithTag("timeline-content-list").performScrollToNode(hasTestTag(tag))
    waitForIdle()
}
