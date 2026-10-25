package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationDraftEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationPaginationEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChatMessageDao {
    @Query("""
        SELECT * FROM messages
        WHERE conversationId = :conversationId
        ORDER BY createdAtMillis ASC, localId ASC
    """)
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND localId = :localId")
    suspend fun findByLocalId(conversationId: String, localId: Long): MessageEntity?

    @Query("SELECT lastDeliveryError FROM messages WHERE conversationId = :conversationId AND localId = :localId")
    suspend fun findLastDeliveryError(conversationId: String, localId: Long): ChatError?

    @Query("""
        SELECT * FROM messages
        WHERE deliveryStatus = 'SENDING'
           OR (deliveryStatus = 'FAILED' AND (lastDeliveryError IS NULL OR lastDeliveryError = 'NETWORK'))
        ORDER BY localId ASC
    """)
    suspend fun findRetryableMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND clientId = :clientId")
    suspend fun findByClientId(conversationId: String, clientId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND serverId = :serverId")
    suspend fun findByServerId(conversationId: String, serverId: String): MessageEntity?

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("DELETE FROM messages WHERE localId = :localId AND conversationId = :conversationId")
    suspend fun delete(conversationId: String, localId: Long)

    @Query("SELECT * FROM conversation_pagination WHERE conversationId = :conversationId")
    fun observePagination(conversationId: String): Flow<ConversationPaginationEntity?>

    @Query("SELECT * FROM conversation_pagination WHERE conversationId = :conversationId")
    suspend fun findPagination(conversationId: String): ConversationPaginationEntity?

    @Upsert
    suspend fun upsertPagination(pagination: ConversationPaginationEntity)

    @Query("SELECT * FROM conversation_drafts WHERE conversationId = :conversationId")
    fun observeDraft(conversationId: String): Flow<ConversationDraftEntity?>

    @Upsert
    suspend fun upsertDraft(draft: ConversationDraftEntity)

    @Query("DELETE FROM conversation_drafts WHERE conversationId = :conversationId")
    suspend fun deleteDraft(conversationId: String)

    @Query("DELETE FROM conversation_drafts WHERE conversationId = :conversationId AND text = :expectedText")
    suspend fun clearDraftIfMatches(conversationId: String, expectedText: String): Int

    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND clientId IS NULL")
    suspend fun deleteDownloadedHistory(conversationId: String)

    @Query("DELETE FROM conversation_pagination WHERE conversationId = :conversationId")
    suspend fun deletePagination(conversationId: String)

    @Query("""
        UPDATE messages SET deliveryStatus = 'SENDING', lastDeliveryError = NULL
        WHERE conversationId = :conversationId AND localId = :localId AND deliveryStatus = 'FAILED'
    """)
    suspend fun beginRetry(conversationId: String, localId: Long): Int

    @Query("""
        UPDATE messages SET deliveryStatus = 'FAILED', lastDeliveryError = :error
        WHERE conversationId = :conversationId AND localId = :localId AND deliveryStatus = 'SENDING'
    """)
    suspend fun markFailed(conversationId: String, localId: Long, error: String): Int

    @Query("UPDATE messages SET deliveryStatus = 'FAILED', lastDeliveryError = 'NETWORK' WHERE deliveryStatus = 'SENDING'")
    suspend fun recoverInterruptedMessages(): Int

    // The supplied demo server reuses srv-N after restart. Only its acknowledged client rows
    // need conversion; seed history and pending rows retain their existing identities.
    @Query("""
        UPDATE messages SET serverId = 'fake-client:' || clientId
        WHERE clientId IS NOT NULL AND serverId LIKE 'srv-%'
    """)
    suspend fun namespaceDemoAcknowledgements(): Int
}
