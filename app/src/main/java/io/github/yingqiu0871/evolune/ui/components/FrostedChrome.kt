package io.github.yingqiu0871.evolune.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Space taken by translucent app chrome (top bar / bottom bar) that page content scrolls under.
 * Primary tab screens add it to their scroll content padding so the first and last items can
 * still scroll clear of the bars. Zero when the chrome is opaque and already reserves its space.
 */
val LocalChromeInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

/** Bottom space taken by translucent chrome, e.g. to keep a FAB above the bottom bar. */
@Composable
fun chromeBottomPadding(): Dp =
    LocalChromeInsets.current.asPaddingValues().calculateBottomPadding()

/** Space taken by translucent chrome, to add inside a scroll container that draws under it. */
@Composable
fun chromePadding(): PaddingValues = LocalChromeInsets.current.asPaddingValues()

/**
 * Scroll content padding for a list that draws under the chrome: this Scaffold padding, plus
 * [LocalChromeInsets], plus the list's own spacing. The chrome insets are read directly rather
 * than through the Scaffold, because a Material3 Scaffold excludes insets its ancestors consumed.
 */
@Composable
fun PaddingValues.plusChrome(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val chrome = chromePadding()
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + horizontal,
        top = calculateTopPadding() + chrome.calculateTopPadding() + top,
        end = calculateEndPadding(layoutDirection) + horizontal,
        bottom = calculateBottomPadding() + chrome.calculateBottomPadding() + bottom
    )
}

/**
 * Frosted-glass backdrop: the page content is recorded once into [layer] and drawn normally;
 * each piece of chrome then redraws the part of that recording behind itself, blurred.
 */
@Stable
class FrostedBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var sourceOffset by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFrostedBackdrop(): FrostedBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostedBackdrop(layer) }
}

/** Marks the content that frosted chrome blurs. Draws the content unchanged. */
fun Modifier.frostedSource(backdrop: FrostedBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.sourceOffset = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Draws the blurred backdrop behind this element, then a [tint] scrim, then the element itself.
 * Use a transparent container color on the chrome so the frosted layer shows through.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: FrostedBackdrop,
    tint: Color,
    blurRadius: Dp = 24.dp
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    var ownOffset by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { ownOffset = it.positionInRoot() }
        .drawWithContent {
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            val shift = backdrop.sourceOffset - ownOffset
            blurLayer.record {
                translate(shift.x, shift.y) { drawLayer(backdrop.layer) }
            }
            // A blur spreads past the layer bounds; keep it inside the chrome.
            clipRect { drawLayer(blurLayer) }
            drawRect(tint)
            drawContent()
        }
}
