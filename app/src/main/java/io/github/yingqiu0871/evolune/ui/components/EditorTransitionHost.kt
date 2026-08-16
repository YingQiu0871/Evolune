package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yingqiu0871.evolune.ui.motion.evolunePageEnterTransition
import io.github.yingqiu0871.evolune.ui.motion.evolunePageExitTransition

@Composable
fun <T : Any> EditorTransitionHost(
    session: T?,
    modifier: Modifier = Modifier,
    content: @Composable (visibleSession: T, isActive: Boolean) -> Unit
) {
    AnimatedContent(
        targetState = session,
        modifier = modifier,
        transitionSpec = {
            val enter = if (targetState != null) {
                evolunePageEnterTransition(startImmediately = true)
            } else {
                EnterTransition.None
            }
            val exit = if (targetState == null) {
                evolunePageExitTransition()
            } else {
                ExitTransition.None
            }
            (enter togetherWith exit).using(
                sizeTransform = null
            )
        },
        contentKey = { it != null },
        label = "editor-transition"
    ) { visibleSession ->
        if (visibleSession != null) {
            content(visibleSession, visibleSession === session)
        }
    }
}
