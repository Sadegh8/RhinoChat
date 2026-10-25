package dev.sadeghtahani.rhinochat.chat.data.local.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "conversation_drafts")
internal data class ConversationDraftEntity(
    @PrimaryKey val conversationId: String,
    val text: String,
)
