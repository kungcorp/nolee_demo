package ai.nolee.canvas

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Physical content boundary of the Nolee Devkit Ultra lens.
 *
 * Same calibration the Launcher ships (408 x 502 at a 2 px straight-edge inset and a 112 px
 * rounded corner radius). Backgrounds and decorative art are still allowed to run to the edge;
 * this geometry protects readable text and controls only.
 */
object DisplaySafeZone {
    const val FRAMEBUFFER_WIDTH_PX = 408
    const val FRAMEBUFFER_HEIGHT_PX = 502
    const val EDGE_INSET_PX = 2f
    const val CORNER_RADIUS_PX = 112f

    // Keeps important content just inside the measured visible line instead of sitting on it.
    const val CONTENT_MARGIN_PX = 4f

    fun isCalibratedPanel(widthPx: Int, heightPx: Int): Boolean {
        val shortSide = min(widthPx, heightPx)
        val longSide = max(widthPx, heightPx)
        return shortSide == FRAMEBUFFER_WIDTH_PX && longSide == FRAMEBUFFER_HEIGHT_PX
    }

    /** Boundary for readable/interactive content, offset inward by [CONTENT_MARGIN_PX]. */
    fun contentInsetPx(depthPx: Float): Float {
        val edgePx = EDGE_INSET_PX + CONTENT_MARGIN_PX
        val radiusPx = CORNER_RADIUS_PX - CONTENT_MARGIN_PX
        if (depthPx <= edgePx) return edgePx + radiusPx
        if (depthPx >= edgePx + radiusPx) return edgePx
        val fromCentre = edgePx + radiusPx - depthPx
        return edgePx + radiusPx - sqrt(radiusPx * radiusPx - fromCentre * fromCentre)
    }
}

/** Horizontal protection for content sitting [depthFromNearestEdge] into the top or bottom curve. */
@Composable
fun safeContentInsetAt(depthFromNearestEdge: Dp, minimum: Dp = 0.dp): Dp {
    val metrics = LocalContext.current.resources.displayMetrics
    if (!DisplaySafeZone.isCalibratedPanel(metrics.widthPixels, metrics.heightPixels)) return minimum

    val density = LocalDensity.current
    val measured = with(density) { DisplaySafeZone.contentInsetPx(depthFromNearestEdge.toPx()).toDp() }
    return if (measured > minimum) measured else minimum
}

/**
 * Dense instrument chrome — the sensor stream card — must stay the size it was laid out at.
 *
 * The shipped watches run `font_scale 1.5`, which turned a 133 dp telemetry card into a 335 dp one
 * that clipped off the top of the lens and broke column alignment. Body copy and the scene words
 * still scale with the owner's accessibility setting; only this card is pinned.
 */
@Composable
fun instrumentSp(baseSp: Float, maximumScale: Float = 1f): TextUnit {
    val actualScale = LocalDensity.current.fontScale.coerceAtLeast(0.1f)
    return (baseSp * min(actualScale, maximumScale) / actualScale).sp
}
