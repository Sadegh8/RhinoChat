package dev.sadeghtahani.rhinochat

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.sadeghtahani.rhinochat.chat.presentation.ChatViewModel
import dev.sadeghtahani.rhinochat.chat.domain.scheduler.OutgoingMessageRetryScheduler
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.createChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.remote.AndroidChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.RestartSafeFakeChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.ChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.domain.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.data.repository.OfflineFirstChatRepository
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkSettings
import dev.sadeghtahani.rhinochat.demo.data.remote.DemoChatRemoteDataSource
import dev.sadeghtahani.rhinochat.demo.data.remote.DemoNetworkRandom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class RhinoChatApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val koinApplication = startKoin {
            androidContext(this@RhinoChatApplication)
            modules(chatModule)
        }
        // Initialize only after the production graph exists. Instrumentation substitutes its
        // own Application and an in-memory WorkManager instead of opening production work.
        WorkManager.initialize(this, Configuration.Builder().build())
        val retryScheduler = koinApplication.koin.get<WorkManagerOutgoingMessageRetryScheduler>()
        applicationScope.launch {
            try {
                retryScheduler.schedulePendingMessages()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ChatDataException) {
                Log.w(TAG, "Could not repair the persisted outgoing queue at startup", failure)
            }
        }
    }
}

private val chatModule = module {
    single { createDemoNetworkControls(androidContext()) }
    single<ChatLocalStore> { createChatLocalStore(androidContext()) }
    single { WorkManagerOutgoingMessageRetryScheduler(androidContext(), get()) }
    single<OutgoingMessageRetryScheduler> { get<WorkManagerOutgoingMessageRetryScheduler>() }
    // Keep one fake/server and its default 20% policy. The controls only override its random
    // draw for reliable demos; toggling never creates a second server or loses its messages.
    single<ChatApi> { FakeChatApi(random = DemoNetworkRandom(get())) }
    single<ChatRemoteDataSource> {
        RestartSafeFakeChatRemoteDataSource(
            DemoChatRemoteDataSource(AndroidChatRemoteDataSource(get()), get()), get(),
        )
    }
    single<ChatRepository> { OfflineFirstChatRepository(get(), get()) }
    viewModel { ChatViewModel(get(), conversationId = "rhino-team", retryScheduler = get()) }
}

private fun createDemoNetworkControls(context: Context): DemoNetworkControls {
    val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    val initial = DemoNetworkSettings(
        offline = preferences.getBoolean(KEY_OFFLINE, false),
        failSends = preferences.getBoolean(KEY_FAIL_SENDS, false),
        failHistory = preferences.getBoolean(KEY_FAIL_HISTORY, false),
        slowNetwork = preferences.getBoolean(KEY_SLOW_NETWORK, false),
        loseFirstReply = preferences.getBoolean(KEY_LOSE_FIRST_REPLY, false),
        randomFailures = preferences.getBoolean(KEY_RANDOM_FAILURES, true),
    )
    return DemoNetworkControls(initial) { settings ->
        preferences.edit()
            .putBoolean(KEY_OFFLINE, settings.offline)
            .putBoolean(KEY_FAIL_SENDS, settings.failSends)
            .putBoolean(KEY_FAIL_HISTORY, settings.failHistory)
            .putBoolean(KEY_SLOW_NETWORK, settings.slowNetwork)
            .putBoolean(KEY_LOSE_FIRST_REPLY, settings.loseFirstReply)
            .putBoolean(KEY_RANDOM_FAILURES, settings.randomFailures)
            .apply()
    }
}

private const val PREFERENCES_NAME = "demo_network_controls"
private const val KEY_OFFLINE = "offline"
private const val KEY_FAIL_SENDS = "fail_sends"
private const val KEY_FAIL_HISTORY = "fail_history"
private const val KEY_SLOW_NETWORK = "slow_network"
private const val KEY_LOSE_FIRST_REPLY = "lose_first_reply"
private const val KEY_RANDOM_FAILURES = "random_failures"
private const val TAG = "RhinoChatApplication"
