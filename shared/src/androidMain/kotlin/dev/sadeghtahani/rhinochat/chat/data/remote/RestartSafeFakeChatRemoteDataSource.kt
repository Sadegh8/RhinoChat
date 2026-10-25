package dev.sadeghtahani.rhinochat.chat.data.remote

import dev.sadeghtahani.rhinochat.chat.data.remote.model.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.remote.model.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Demo-only identity adapter. The supplied fake resets srv-N and idempotency memory on restart.
 * Its client IDs remain stable in Room, so outgoing acknowledgements/history use that namespace.
 * Seed history retains srv-N. Real backends should retain their own durable server identities.
 */
class RestartSafeFakeChatRemoteDataSource(
    private val delegate: ChatRemoteDataSource,
    private val store: ChatLocalStore,
) : ChatRemoteDataSource {
    private val preparationMutex = Mutex()
    private var prepared = false

    override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
        prepareCache()
        val page = delegate.getMessages(conversationId, cursor, limit)
        return page.copy(messages = page.messages.map { it.withDemoIdentity() })
    }

    override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
        prepareCache()
        return delegate.sendMessage(conversationId, clientId, text).withDemoIdentity()
    }

    private suspend fun prepareCache() = preparationMutex.withLock {
        if (!prepared) {
            // Complete the atomic conversion before any normalized page or acknowledgement merges.
            store.namespaceDemoAcknowledgements()
            prepared = true
        }
    }

    private fun RemoteMessage.withDemoIdentity(): RemoteMessage =
        clientId?.let { copy(id = "fake-client:$it") } ?: this
}
