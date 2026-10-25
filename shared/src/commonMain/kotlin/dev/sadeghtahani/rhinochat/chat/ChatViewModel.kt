package dev.sadeghtahani.rhinochat.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.sadeghtahani.rhinochat.chat.data.ChatCacheState
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationState
import dev.sadeghtahani.rhinochat.chat.data.ChatUiState
import dev.sadeghtahani.rhinochat.chat.data.HistoryLoadState
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val repository: ChatRepository,
    private val conversationId: String,
) : ViewModel() {
    private val draft = MutableStateFlow("")
    private var draftVersion = 0L
    private val operations = MutableStateFlow(ChatOperationState())
    private val observationAttempt = MutableStateFlow(0L)

    private val cache: StateFlow<ChatCacheState> = observationAttempt.flatMapLatest {
        combine(
            repository.observeMessages(conversationId),
            repository.observePagination(conversationId),
        ) { messages, pagination -> ChatCacheState(messages, pagination) }
            .onStart { emit(lastKnownCache().copy(error = null)) }
            .catch { failure ->
                if (failure is ChatDataException) emit(lastKnownCache().copy(error = failure.error))
                else throw failure
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ChatCacheState())

    val uiState: StateFlow<ChatUiState> = combine(
        cache,
        draft,
        operations,
    ) { cached, text, operation ->
        val initialLoad = cached.error?.let { HistoryLoadState.Failed(it) } ?: operation.initialLoad
        ChatUiState(
            messages = cached.messages,
            draft = text,
            initialLoad = initialLoad,
            olderLoad = operation.olderLoad,
            hasMoreHistory = (initialLoad == HistoryLoadState.Idle) &&
                cached.pagination.hasLoadedInitialPage && cached.pagination.nextCursor != null,
            nextHistoryCursor = cached.pagination.nextCursor,
            isEnqueueing = operation.isEnqueueing,
            actionError = operation.actionError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        require(conversationId.isNotBlank())
        loadInitialMessages()
    }

    fun onDraftChanged(text: String) {
        draftVersion++
        draft.value = text
        dismissActionError()
    }

    fun sendMessage() {
        val text = draft.value
        if (text.isBlank() || operations.value.isEnqueueing) return
        val submittedVersion = draftVersion
        operations.update { it.copy(isEnqueueing = true, actionError = null) }
        viewModelScope.launch {
            val message = try {
                repository.enqueueMessage(conversationId, text).also {
                    if (draftVersion == submittedVersion) draft.value = ""
                }
            } catch (failure: ChatDataException) {
                operations.update { it.copy(actionError = failure.error) }
                return@launch
            } finally {
                operations.update { it.copy(isEnqueueing = false) }
            }
            recordSendResult(repository.sendPendingMessage(conversationId, message.localId))
        }
    }

    fun retryMessage(localId: Long) {
        dismissActionError()
        viewModelScope.launch { recordSendResult(repository.retryMessage(conversationId, localId)) }
    }

    fun loadInitialMessages() {
        // Reconnect only on a user retry; never loop automatically on a persistent storage error.
        if (cache.value.error != null) observationAttempt.update { it + 1 }
        if (operations.value.initialLoad == HistoryLoadState.Loading) return
        operations.update { it.copy(initialLoad = HistoryLoadState.Loading) }
        viewModelScope.launch {
            val result = repository.loadInitialMessages(conversationId)
            operations.update { it.copy(initialLoad = result.asLoadState()) }
        }
    }

    fun loadOlderMessages() {
        val current = operations.value
        if (current.initialLoad != HistoryLoadState.Idle || current.olderLoad == HistoryLoadState.Loading) return
        operations.update { it.copy(olderLoad = HistoryLoadState.Loading) }
        viewModelScope.launch {
            val result = repository.loadOlderMessages(conversationId)
            operations.update { it.copy(olderLoad = result.asLoadState()) }
        }
    }

    fun dismissActionError() {
        operations.update { it.copy(actionError = null) }
    }

    private fun recordSendResult(result: ChatOperationResult) {
        if (result is ChatOperationResult.Failed && result.error != ChatError.NETWORK) {
            operations.update { it.copy(actionError = result.error) }
        }
    }

    private fun ChatOperationResult.asLoadState(): HistoryLoadState = when (this) {
        is ChatOperationResult.Failed -> HistoryLoadState.Failed(error)
        ChatOperationResult.Completed, ChatOperationResult.Ignored -> HistoryLoadState.Idle
    }

    private fun lastKnownCache() = cache.value
}
