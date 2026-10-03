package io.github.yingqiu0871.evolune.ui.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction

/**
 * Scrolls to the node and clicks it through its semantics action.
 *
 * Primary tab content scrolls under the translucent top/bottom bars, so after `performScrollTo()`
 * a node can sit behind the bottom bar, where a pointer click would land on a navigation item
 * instead. The semantics click does not depend on what is drawn above the node.
 */
fun SemanticsNodeInteraction.scrollToAndClick(): SemanticsNodeInteraction {
    performScrollTo()
    return clickThroughSemantics()
}

/**
 * Clicks the node through its semantics action. Use after `performScrollToNode()` on a lazy list:
 * on short screens the target can end up at the bottom edge, behind the translucent bottom bar.
 */
fun SemanticsNodeInteraction.clickThroughSemantics(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)
