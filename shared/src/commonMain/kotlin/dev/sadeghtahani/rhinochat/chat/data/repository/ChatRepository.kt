package dev.sadeghtahani.rhinochat.chat.data.repository

import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.PaginationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Local observations are canonical; suspend commands belong to the caller's coroutine scope. */
interface ChatRepository {
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    fun observePagination(conversationId: String): Flow<PaginationState>
    /** Draft methods have defaults so platform-neutral repository fakes can omit persistence. */
    fun observeDraft(conversationId: String): Flow<String> = flowOf("")
    suspend fun saveDraft(conversationId: String, text: String) = Unit
    suspend fun clearDraftIfMatches(conversationId: String, expectedText: String) = Unit
    /** Commits the outgoing identity before returning; expected write failures throw ChatDataException. */
    suspend fun enqueueMessage(conversationId: String, text: String): ChatMessage
    /** Production stores the message and removes only this submitted draft in one transaction. */
    suspend fun enqueueMessage(
        conversationId: String,
        text: String,
        submittedDraft: String,
    ): ChatMessage {
        val message = enqueueMessage(conversationId, text)
        clearDraftIfMatches(conversationId, submittedDraft)
        return message
    }
    suspend fun sendPendingMessage(conversationId: String, localId: Long): ChatOperationResult
    suspend fun retryMessage(conversationId: String, localId: Long): ChatOperationResult
    /** Resumes unsent work, coalescing with active sends and excluding non-network failures. */
    suspend fun resumePendingMessage(conversationId: String, localId: Long): ChatOperationResult
    /** Fetches the head once per repository session; concurrent/repeated successful loads are ignored. */
    suspend fun loadInitialMessages(conversationId: String): ChatOperationResult
    suspend fun loadOlderMessages(conversationId: String): ChatOperationResult
    /** Clears downloaded history and pagination, preserving user-created messages; ignored during a load. */
    suspend fun resetHistoryCache(conversationId: String): ChatOperationResult
}
