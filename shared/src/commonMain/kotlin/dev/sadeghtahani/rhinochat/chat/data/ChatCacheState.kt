package dev.sadeghtahani.rhinochat.chat.data

/** Last readable cache remains visible while an explicit retry reconnects its observations. */
internal data class ChatCacheState(
    val messages: List<ChatMessage> = emptyList(),
    val pagination: PaginationState = PaginationState(),
    val error: ChatError? = null,
)
