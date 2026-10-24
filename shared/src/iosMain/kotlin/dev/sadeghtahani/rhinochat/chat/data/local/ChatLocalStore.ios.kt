package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
fun createChatLocalStore(): ChatLocalStore {
    val directory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    val path = requireNotNull(directory?.path) + "/rhino-chat.db"
    val builder = Room.databaseBuilder<ChatDatabase>(
        name = path,
        factory = ChatDatabaseConstructor::initialize,
    )
    return ChatLocalStore(buildChatDatabase(builder))
}
