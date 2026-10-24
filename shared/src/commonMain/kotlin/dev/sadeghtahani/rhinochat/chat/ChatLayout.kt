package dev.sadeghtahani.rhinochat.chat

import androidx.compose.material3.adaptive.Posture
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import dev.sadeghtahani.rhinochat.chat.data.ChatLayout
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Single-conversation layout policy; dimensions describe the available app region, not a device. */
internal fun calculateChatLayout(
    sizePx: IntSize,
    density: Float,
    fontScale: Float,
    posture: Posture,
    windowOriginPx: Offset,
    direction: LayoutDirection,
): ChatLayout {
    val viewport = Rect(0f, 0f, sizePx.width.toFloat(), sizePx.height.toFloat())
    val gutter = 8f * density
    val hinges = posture.hingeList.filter { it.isSeparating || it.isOccluding }
        .map { it to it.bounds.translate(-windowOriginPx) }
    var regions = listOf(viewport)
    hinges.filter { it.first.isVertical }.forEach { (_, hinge) ->
        regions = regions.flatMap { region ->
            if (hinge.left >= region.right || hinge.right <= region.left ||
                hinge.top >= region.bottom || hinge.bottom <= region.top
            ) listOf(region) else listOf(
                Rect(region.left, region.top, max(region.left, hinge.left - gutter), region.bottom),
                Rect(min(region.right, hinge.right + gutter), region.top, region.right, region.bottom),
            ).filter { it.width > 0 && it.height > 0 }
        }
    }
    // Prefer a larger unobstructed pane; an equal split follows logical reading order.
    var region = regions.maxWithOrNull(compareBy<Rect> { it.width * it.height }.thenBy {
        if (direction == LayoutDirection.Ltr) -it.left else it.right
    }) ?: viewport
    val horizontal = hinges.filter { !it.first.isVertical }.map { it.second }.filter {
        it.top > region.top && it.bottom < region.bottom && it.left < region.right && it.right > region.left
    }
    var historyBottom: Float? = null
    var inputHeight: Float? = null
    if (horizontal.size == 1) {
        val hinge = horizontal.single()
        val above = Rect(region.left, region.top, region.right, hinge.top - gutter)
        val below = Rect(region.left, hinge.bottom + gutter, region.right, region.bottom)
        val minimumInputHeight = (max(56f, 24f * fontScale + 32f) + 48f) * density
        if (above.height >= 160f * density && below.height >= minimumInputHeight) {
            historyBottom = above.bottom
            inputHeight = below.height
        } else {
            // A keyboard may cover the lower half. Keep the complete chat above the fold then.
            region = if (above.height >= below.height) above else below
        }
    } else if (horizontal.size > 1) {
        // Multi-fold displays: use one clear region rather than span another horizontal hinge.
        var horizontalRegions = listOf(region)
        horizontal.forEach { hinge ->
            horizontalRegions = horizontalRegions.flatMap { area ->
                if (hinge.top >= area.bottom || hinge.bottom <= area.top) listOf(area) else listOf(
                    Rect(area.left, area.top, area.right, max(area.top, hinge.top - gutter)),
                    Rect(area.left, min(area.bottom, hinge.bottom + gutter), area.right, area.bottom),
                ).filter { it.height > 0 }
            }
        }
        region = horizontalRegions.maxBy { it.width * it.height }
    }

    val sizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(region.width / density, region.height / density)
    val compactHeight = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    val wide = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val floating = wide && !compactHeight
    val margin = if (wide) (if (compactHeight) 16f else 24f) * density else 0f
    val width = min(840f * density, (region.width - margin * 2).coerceAtLeast(0f))
    val top = region.top + if (floating) margin else 0f
    val bottom = region.bottom - if (floating) margin else 0f
    val canvas = Rect(region.center.x - width / 2, top, region.center.x + width / 2, bottom)
    val showHeader = canvas.height / density >= 240f
    val availableInputDp = inputHeight?.let { (it - (region.bottom - bottom)) / density }
        ?: (canvas.height / density - (if (!showHeader) 0f else if (compactHeight) 52f else 84f) - 72f)
    val outerInputPadding = if (compactHeight) 16f else 24f
    val lines = floor((availableInputDp - outerInputPadding - 32f) / (24f * fontScale))
        .toInt().coerceIn(1, if (compactHeight) 2 else 5)
    return ChatLayout(
        canvasBoundsPx = canvas,
        compactHeader = compactHeight,
        showHeader = showHeader,
        floatingCanvas = floating,
        composerMaxLines = lines,
        historyBottomPx = historyBottom?.minus(canvas.top),
        inputMaxHeightPx = inputHeight?.minus(region.bottom - bottom),
    )
}
