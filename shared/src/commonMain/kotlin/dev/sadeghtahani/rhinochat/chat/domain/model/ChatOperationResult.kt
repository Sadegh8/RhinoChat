package dev.sadeghtahani.rhinochat.chat.domain.model

sealed interface ChatOperationResult {
    data object Completed : ChatOperationResult
    data object Ignored : ChatOperationResult
    data class Failed(val error: ChatError) : ChatOperationResult
}
