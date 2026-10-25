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
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.demo.data.remote.DemoChatRemoteDataSource
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
    single { DemoNetworkControls() }
    single<ChatLocalStore> { createChatLocalStore(androidContext()) }
    // Failures are controlled explicitly by the launch screen, outside the unchanged fake.
    single<ChatApi> { FakeChatApi(failureRate = 0.0) }
    single<ChatRemoteDataSource> {
        RestartSafeFakeChatRemoteDataSource(
            DemoChatRemoteDataSource(AndroidChatRemoteDataSource(get()), get()), get(),
        )
    }
    single<ChatRepository> { OfflineFirstChatRepository(get(), get()) }
    viewModel { ChatViewModel(get(), conversationId = "rhino-team") }
}
