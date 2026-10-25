package dev.sadeghtahani.rhinochat.testing

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import dev.sadeghtahani.rhinochat.chat.presentation.ChatViewModel
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.remote.model.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.remote.model.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.createChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.remote.AndroidChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.RestartSafeFakeChatRemoteDataSource
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.domain.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.data.repository.OfflineFirstChatRepository
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.rules.ExternalResource
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.random.Random

/** Real repository/Room/ViewModel; each test controls only transport completion. */
internal class ChatUiFixture : ExternalResource() {
    private lateinit var context: Context
    private val databaseName = "chat-ui-${UUID.randomUUID()}.db"
    lateinit var store: ChatLocalStore
        private set
    val remote = ControlledChatRemote()
    val controls = dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls()

    override fun before() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChatUiTestApplication
        application.finishBootstrap()
        start(application)
    }

    internal fun start(context: Context) {
        this.context = context
        store = createChatLocalStore(context, databaseName)
        val repository = OfflineFirstChatRepository(
            store, RestartSafeFakeChatRemoteDataSource(
                dev.sadeghtahani.rhinochat.demo.data.remote.DemoChatRemoteDataSource(remote, controls), store,
            ),
        )
        startKoin {
            androidContext(context)
            modules(module {
                single { controls }
                single<ChatLocalStore> { store }
                single<ChatRepository> { repository }
                viewModel { ChatViewModel(get(), CONVERSATION) }
            })
        }
    }

    // Rule order closes MainActivity (cancelling its ViewModel) before closing the store.
    override fun after() = close()

    internal fun close() {
        try {
            stopKoin()
            store.close()
        } finally {
            context.deleteDatabase(databaseName)
            // Room 3's external lock sidecar is not removed by Context.deleteDatabase.
            context.getDatabasePath("$databaseName.lck").delete()
        }
    }

    fun outgoing(): List<ChatMessage> = runBlocking {
        withTimeout(5_000) { store.observeMessages(CONVERSATION).first().filter { it.clientId != null } }
    }

    companion object {
        const val CONVERSATION = "rhino-team"
    }
}

internal enum class SendReply { ACKNOWLEDGE, COMMIT_THEN_LOSE_REPLY }

internal data class SendAttempt(
    val clientId: String,
    val text: String,
    val reply: CompletableDeferred<SendReply> = CompletableDeferred(),
)

/** Uses the supplied server's actual idempotency; gates and lost replies live only in tests. */
internal class ControlledChatRemote : ChatRemoteDataSource {
    private val delegate = AndroidChatRemoteDataSource(FakeChatApi(failureRate = 0.0, random = Random(42)))
    val historyReply = CompletableDeferred<Boolean>()
    val historyCalls = AtomicInteger()
    val attempts = CopyOnWriteArrayList<SendAttempt>()
    val sendStarted = Channel<SendAttempt>(Channel.UNLIMITED)

    override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
        if (historyCalls.incrementAndGet() == 1 && !historyReply.await()) {
            throw ChatDataException(ChatError.NETWORK)
        }
        return delegate.getMessages(conversationId, cursor, limit)
    }

    override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
        val attempt = SendAttempt(clientId, text)
        attempts += attempt
        sendStarted.send(attempt)
        val reply = attempt.reply.await()
        val committed = delegate.sendMessage(conversationId, clientId, text)
        if (reply == SendReply.COMMIT_THEN_LOSE_REPLY) throw ChatDataException(ChatError.NETWORK)
        return committed
    }

    fun serverCopies(clientId: String): Int = runBlocking {
        withTimeout(5_000) {
            delegate.getMessages(ChatUiFixture.CONVERSATION, null, 20).messages.count { it.clientId == clientId }
        }
    }
}
