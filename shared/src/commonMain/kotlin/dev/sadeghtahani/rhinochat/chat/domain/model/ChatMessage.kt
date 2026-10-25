package dev.sadeghtahani.rhinochat.chat.domain.model

/** [localId] remains the UI identity when a server acknowledgement arrives. */
data class ChatMessage(
    val localId: Long,
    val conversationId: String,
    val clientId: String?,
    val serverId: String?,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long,
    val deliveryStatus: DeliveryStatus,
)
