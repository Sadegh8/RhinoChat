package dev.sadeghtahani.rhinochat.chat.data

import androidx.compose.ui.geometry.Rect

/** Presentation geometry in pixels, after system/IME insets have been removed. */
internal data class ChatLayout(
    val canvasBoundsPx: Rect,
    val compactHeader: Boolean,
    val showHeader: Boolean,
    val floatingCanvas: Boolean,
    val composerMaxLines: Int,
    val historyBottomPx: Float? = null,
    val inputMaxHeightPx: Float? = null,
)
