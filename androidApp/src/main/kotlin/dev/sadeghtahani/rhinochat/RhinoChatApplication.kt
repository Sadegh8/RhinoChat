package dev.sadeghtahani.rhinochat

import android.app.Application
import dev.sadeghtahani.rhinochat.chat.ChatViewModel
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.createChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.remote.AndroidChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.RestartSafeFakeChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.ChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.data.repository.OfflineFirstChatRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class RhinoChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RhinoChatApplication)
            modules(chatModule)
        }
    }
}

private val chatModule = module {
    single<ChatLocalStore> { createChatLocalStore(androidContext()) }
    single<ChatApi> { FakeChatApi() }
    single<ChatRemoteDataSource> {
        RestartSafeFakeChatRemoteDataSource(AndroidChatRemoteDataSource(get()), get())
    }
    single<ChatRepository> { OfflineFirstChatRepository(get(), get()) }
    viewModel { ChatViewModel(get(), conversationId = "rhino-team") }
}
