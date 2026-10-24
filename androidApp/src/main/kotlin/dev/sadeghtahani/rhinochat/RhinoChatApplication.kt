package dev.sadeghtahani.rhinochat

import android.app.Application
import dev.sadeghtahani.rhinochat.chat.data.local.createChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.remote.AndroidChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.data.repository.OfflineFirstChatRepository

class RhinoChatApplication : Application() {
    val chatRepository: ChatRepository by lazy {
        OfflineFirstChatRepository(
            store = createChatLocalStore(this),
            remote = AndroidChatRemoteDataSource(FakeChatApi()),
        )
    }
}
