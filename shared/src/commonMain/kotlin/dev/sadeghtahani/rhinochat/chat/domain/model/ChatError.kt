package dev.sadeghtahani.rhinochat.chat.domain.model

enum class ChatError {
    NETWORK,
    STORAGE,
    IDENTITY_CONFLICT,
}

class ChatDataException(val error: ChatError, cause: Throwable? = null) :
    Exception("Chat data operation failed: $error", cause)
