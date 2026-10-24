package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
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

    @Upsert
    suspend fun upsertPagination(pagination: ConversationPaginationEntity)

    @Query("""
        UPDATE messages SET deliveryStatus = 'SENDING'
        WHERE conversationId = :conversationId AND localId = :localId AND deliveryStatus = 'FAILED'
    """)
    suspend fun beginRetry(conversationId: String, localId: Long): Int

    @Query("""
        UPDATE messages SET deliveryStatus = 'FAILED'
        WHERE conversationId = :conversationId AND localId = :localId AND deliveryStatus = 'SENDING'
    """)
    suspend fun markFailed(conversationId: String, localId: Long): Int

    @Query("UPDATE messages SET deliveryStatus = 'FAILED' WHERE deliveryStatus = 'SENDING'")
    suspend fun recoverInterruptedMessages(): Int
}
