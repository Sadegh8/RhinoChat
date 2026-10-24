package dev.sadeghtahani.rhinochat.chat.data

sealed interface ChatOperationResult {
    data object Completed : ChatOperationResult
    data object Ignored : ChatOperationResult
    data class Failed(val error: ChatError) : ChatOperationResult
}
