package io.github.yingqiu0871.evolune.ui.motion

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin

fun evolunePageEnterTransition(): EnterTransition =
    fadeIn(
        animationSpec = tween(
            durationMillis = PAGE_FADE_IN_MILLIS,
            delayMillis = PAGE_FADE_IN_DELAY_MILLIS
        )
    ) + scaleIn(
        initialScale = PAGE_INITIAL_SCALE,
        transformOrigin = TransformOrigin.Center,
        animationSpec = tween(
            durationMillis = PAGE_FADE_IN_MILLIS,
            delayMillis = PAGE_FADE_IN_DELAY_MILLIS
        )
    )

fun evolunePageExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(PAGE_FADE_OUT_MILLIS))

private const val PAGE_FADE_OUT_MILLIS = 90
private const val PAGE_FADE_IN_MILLIS = 220
private const val PAGE_FADE_IN_DELAY_MILLIS = 90
private const val PAGE_INITIAL_SCALE = 0.98f
