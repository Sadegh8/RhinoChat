package dev.sadeghtahani.rhinochat.chat.data.repository

import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.PaginationState
import kotlinx.coroutines.flow.Flow

/** Local observations are canonical; suspend commands belong to the caller's coroutine scope. */
interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    fun observePagination(conversationId: String): Flow<PaginationState>
    /** Commits the outgoing identity before returning; expected write failures throw ChatDataException. */
    suspend fun enqueueMessage(conversationId: String, text: String): ChatMessage
    suspend fun sendPendingMessage(conversationId: String, localId: Long): ChatOperationResult
    suspend fun retryMessage(conversationId: String, localId: Long): ChatOperationResult
    /** Fetches the head once per repository session; concurrent/repeated successful loads are ignored. */
    suspend fun loadInitialMessages(conversationId: String): ChatOperationResult
    suspend fun loadOlderMessages(conversationId: String): ChatOperationResult
}
