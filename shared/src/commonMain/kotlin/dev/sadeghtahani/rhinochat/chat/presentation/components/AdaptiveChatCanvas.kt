package dev.sadeghtahani.rhinochat.chat.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.Posture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.chat.presentation.calculateChatLayout
import dev.sadeghtahani.rhinochat.chat.presentation.model.ChatLayout

@Composable
internal fun AdaptiveChatCanvas(
    posture: Posture,
    modifier: Modifier = Modifier,
    content: @Composable (ChatLayout) -> Unit,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .onGloballyPositioned { windowOrigin = it.positionInWindow() },
            contentAlignment = AbsoluteAlignment.TopLeft,
        ) {
            val size = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
            val layout = remember(size, density, posture, windowOrigin, direction) {
                calculateChatLayout(size, density.density, density.fontScale, posture, windowOrigin, direction)
            }
            val bounds = layout.canvasBoundsPx
            Box(
                Modifier.absoluteOffset(with(density) { bounds.left.toDp() }, with(density) { bounds.top.toDp() })
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .clip(RoundedCornerShape(if (layout.floatingCanvas) 28.dp else 0.dp)),
            ) { content(layout) }
        }
    }
}
