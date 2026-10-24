package dev.sadeghtahani.rhinochat.chat.data

data class MessagePage(
    val messages: List<RemoteMessage>,
    val nextCursor: String?,
)
