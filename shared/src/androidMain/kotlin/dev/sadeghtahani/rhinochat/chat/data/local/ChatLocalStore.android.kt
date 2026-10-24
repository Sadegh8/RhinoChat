package dev.sadeghtahani.rhinochat.chat.data.local

import android.content.Context
import androidx.room3.Room

/** The application composition root should own and reuse one store. */
fun createChatLocalStore(context: Context): ChatLocalStore {
    val path = context.applicationContext.getDatabasePath("rhino-chat.db")
    check(path.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
    val builder = Room.databaseBuilder<ChatDatabase>(
        context = context.applicationContext,
        name = path.absolutePath,
        factory = ChatDatabaseConstructor::initialize,
    )
    return ChatLocalStore(buildChatDatabase(builder))
}
