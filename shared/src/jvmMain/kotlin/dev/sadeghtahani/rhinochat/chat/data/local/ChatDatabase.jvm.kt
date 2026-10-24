package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.Room

internal fun buildChatDatabase(path: String): ChatDatabase = buildChatDatabase(
    Room.databaseBuilder<ChatDatabase>(
        name = path,
        factory = ChatDatabaseConstructor::initialize,
    ),
)
