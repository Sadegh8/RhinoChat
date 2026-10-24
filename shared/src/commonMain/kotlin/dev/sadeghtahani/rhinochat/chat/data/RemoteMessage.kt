package dev.sadeghtahani.rhinochat.chat.data

/** Platform-neutral input for reconciling server data with local messages. */
data class RemoteMessage(
    val id: String,
    val clientId: String?,
    val senderId: String,
    val text: String,
    val createdAtMillis: Long,
)
