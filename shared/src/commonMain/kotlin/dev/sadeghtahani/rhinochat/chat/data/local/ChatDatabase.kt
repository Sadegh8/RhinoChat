package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationDraftEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationPaginationEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.MessageEntity

@Database(
    entities = [MessageEntity::class, ConversationPaginationEntity::class, ConversationDraftEntity::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
internal abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): ChatMessageDao
}

@Suppress("KotlinNoActualForExpect")
internal expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase> {
    override fun initialize(): ChatDatabase
}

internal fun buildChatDatabase(builder: RoomDatabase.Builder<ChatDatabase>): ChatDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .build()
