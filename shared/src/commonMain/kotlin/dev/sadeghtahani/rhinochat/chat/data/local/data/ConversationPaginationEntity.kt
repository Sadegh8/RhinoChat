package dev.sadeghtahani.rhinochat.chat.data.local.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "conversation_pagination")
internal data class ConversationPaginationEntity(
    @PrimaryKey val conversationId: String,
    val nextCursor: String?,
)
