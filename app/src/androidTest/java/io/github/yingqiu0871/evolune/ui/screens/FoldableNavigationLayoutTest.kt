package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.yingqiu0871.evolune.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FoldableNavigationLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun adaptiveNavigationMatchesCurrentWindowWidth() {
        composeRule.waitForIdle()
        val widthDp = composeRule.activity.resources.configuration.screenWidthDp
        val barNodes = composeRule.onAllNodesWithTag("navigation-bar").fetchSemanticsNodes()
        val railNodes = composeRule.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes()

        if (widthDp < 600) {
            assertTrue("$widthDp dp must use NavigationBar", barNodes.size == 1)
            assertTrue("$widthDp dp must not use NavigationRail", railNodes.isEmpty())
            composeRule.onNodeWithTag("nav-bar-home").assertIsSelected()
        } else {
            assertTrue("$widthDp dp must use NavigationRail", railNodes.size == 1)
            assertTrue("$widthDp dp must not use NavigationBar", barNodes.isEmpty())
            composeRule.onNodeWithTag("nav-rail-home").assertIsSelected()
        }
    }

    @Test
    fun expandedRailAndSharedTopBarKeepStableGeometry() {
        composeRule.waitForIdle()
        val railNodes = composeRule.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes()
        assumeTrue("Navigation rail requires a non-compact window", railNodes.isNotEmpty())

        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val topBarBounds = composeRule.onNodeWithTag("app-top-bar").fetchSemanticsNode().boundsInRoot
        val railBounds = composeRule.onNodeWithTag("navigation-rail").fetchSemanticsNode().boundsInRoot
        val contentBounds = composeRule.onNodeWithTag("app-content").fetchSemanticsNode().boundsInRoot

        assertTrue(abs(topBarBounds.left - rootBounds.left) <= 1f)
        assertTrue(abs(topBarBounds.right - rootBounds.right) <= 1f)
        assertTrue(railBounds.top >= topBarBounds.bottom - 1f)

        val itemBounds = listOf(
            "home",
            "records",
            "medication_plans",
            "settings"
        ).map { route ->
            composeRule.onNodeWithTag("nav-rail-$route").fetchSemanticsNode().boundsInRoot
        }
        val centers = itemBounds.map { it.center.y }
        val gaps = centers.zipWithNext { first, second -> second - first }
        assertTrue(gaps.max() - gaps.min() <= 2f)

        val itemGroupCenter = (itemBounds.first().top + itemBounds.last().bottom) / 2f
        assertTrue(abs(itemGroupCenter - railBounds.center.y) <= 2f)

        val initialTitleBounds = composeRule
            .onNodeWithTag("app-top-title")
            .fetchSemanticsNode().boundsInRoot
        assertTrue(abs(initialTitleBounds.center.x - contentBounds.center.x) <= 1f)
        composeRule.onNodeWithTag("nav-rail-records").performClick()
        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("用药记录").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        val recordsTitleBounds = composeRule
            .onNodeWithTag("app-top-title")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(abs(initialTitleBounds.center.x - recordsTitleBounds.center.x) <= 1f)
        assertTrue(abs(recordsTitleBounds.center.x - contentBounds.center.x) <= 1f)
        assertTrue(abs(initialTitleBounds.top - recordsTitleBounds.top) <= 1f)
    }

    @Test
    fun topLevelTitleRemainsCenteredThroughoutPageTransition() {
        composeRule.waitForIdle()
        val railNodes = composeRule.onAllNodesWithTag("navigation-rail").fetchSemanticsNodes()
        assumeTrue("Navigation rail requires a non-compact window", railNodes.isNotEmpty())

        val contentCenterX = composeRule
            .onNodeWithTag("app-content")
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .x

        composeRule.mainClock.autoAdvance = false
        try {
            composeRule.onNodeWithTag("nav-rail-settings").performClick()
            repeat(24) {
                composeRule.mainClock.advanceTimeByFrame()
                val titleNodes = composeRule
                    .onAllNodesWithTag("app-top-title")
                    .fetchSemanticsNodes()
                assertTrue("Exactly one title layer must exist", titleNodes.size == 1)
                assertTrue(
                    "Title moved away from the content center",
                    abs(titleNodes.single().boundsInRoot.center.x - contentCenterX) <= 1f
                )
            }
        } finally {
            composeRule.mainClock.autoAdvance = true
        }

        composeRule.waitUntil(5_000L) {
            composeRule.onAllNodesWithText("设置").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }
}
