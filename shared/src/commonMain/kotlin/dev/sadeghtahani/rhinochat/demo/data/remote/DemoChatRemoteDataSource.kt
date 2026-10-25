package dev.sadeghtahani.rhinochat.demo.data.remote

import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.data.remote.model.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.remote.model.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Fault injection around the real transport; the supplied fake itself stays unchanged. */
class DemoChatRemoteDataSource(
    private val delegate: ChatRemoteDataSource,
    private val controls: DemoNetworkControls,
    private val random: Random = Random.Default,
) : ChatRemoteDataSource {
    private val replyMutex = Mutex()
    private val lostReplies = mutableSetOf<Pair<String, String>>()

    override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
        val settings = controls.settings.value
        beforeRequest(settings, settings.failHistory)
        return delegate.getMessages(conversationId, cursor, limit)
    }

    override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
        val settings = controls.settings.value
        beforeRequest(settings, settings.failSends)
        val result = delegate.sendMessage(conversationId, clientId, text)
        // Lose only the first successful reply for this intent. Retry reaches real idempotency.
        if (settings.loseFirstReply && replyMutex.withLock { lostReplies.add(conversationId to clientId) }) {
            throw ChatDataException(ChatError.NETWORK)
        }
        return result
    }

    private suspend fun beforeRequest(settings: DemoNetworkSettings, fail: Boolean) {
        if (settings.slowNetwork) delay(3_000)
        if (settings.offline || fail || (settings.randomFailures && random.nextDouble() < 0.2)) {
            throw ChatDataException(ChatError.NETWORK)
        }
    }
}
