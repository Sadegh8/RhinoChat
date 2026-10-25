package dev.sadeghtahani.rhinochat.chat.domain.model

/** A missing page record differs from a loaded page whose null cursor means the end. */
data class PaginationState(
    val hasLoadedInitialPage: Boolean = false,
    val nextCursor: String? = null,
)
