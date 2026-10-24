package dev.sadeghtahani.rhinochat.chat.data

enum class ChatError {
    NETWORK,
    STORAGE,
    IDENTITY_CONFLICT,
}

class ChatDataException(val error: ChatError, cause: Throwable? = null) :
    Exception("Chat data operation failed: $error", cause)
