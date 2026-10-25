package dev.sadeghtahani.rhinochat.chat.presentation.model

import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.PaginationState
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError

/** Last readable cache remains visible while an explicit retry reconnects its observations. */
internal data class ChatCacheState(
    val messages: List<ChatMessage> = emptyList(),
    val pagination: PaginationState = PaginationState(),
    val error: ChatError? = null,
)
