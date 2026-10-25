package dev.sadeghtahani.rhinochat.chat.data.local.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "clientId"], unique = true),
        Index(value = ["conversationId", "serverId"], unique = true),
        Index(value = ["conversationId", "createdAtMillis", "localId"]),
    ],
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val conversationId: String,
    val clientId: String?,
    val serverId: String?,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long,
    val deliveryStatus: DeliveryStatus,
    val lastDeliveryError: ChatError? = null,
)

internal fun MessageEntity.asExternalModel() = ChatMessage(
    localId = localId,
    conversationId = conversationId,
    clientId = clientId,
    serverId = serverId,
    senderId = senderId,
    text = text,
    createdAtMillis = createdAtMillis,
    deliveryStatus = deliveryStatus,
)
