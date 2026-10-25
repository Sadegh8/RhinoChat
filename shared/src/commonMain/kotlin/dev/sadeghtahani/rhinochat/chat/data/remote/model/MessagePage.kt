package dev.sadeghtahani.rhinochat.chat.data.remote.model

data class MessagePage(
    val messages: List<RemoteMessage>,
    val nextCursor: String?,
)
