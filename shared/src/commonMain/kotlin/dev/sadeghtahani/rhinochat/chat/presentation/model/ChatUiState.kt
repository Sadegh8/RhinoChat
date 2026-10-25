package dev.sadeghtahani.rhinochat.chat.presentation.model

import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val initialLoad: HistoryLoadState = HistoryLoadState.Loading,
    val olderLoad: HistoryLoadState = HistoryLoadState.Idle,
    val hasMoreHistory: Boolean = false,
    val nextHistoryCursor: String? = null,
    val isEnqueueing: Boolean = false,
    val actionError: ChatError? = null,
)

sealed interface HistoryLoadState {
    data object Idle : HistoryLoadState
    data object Loading : HistoryLoadState
    data class Failed(val error: ChatError) : HistoryLoadState
}

internal data class ChatOperationState(
    val initialLoad: HistoryLoadState = HistoryLoadState.Idle,
    val olderLoad: HistoryLoadState = HistoryLoadState.Idle,
    val isEnqueueing: Boolean = false,
    val actionError: ChatError? = null,
)
