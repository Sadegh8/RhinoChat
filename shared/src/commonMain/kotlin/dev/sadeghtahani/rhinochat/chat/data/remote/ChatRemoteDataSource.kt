package dev.sadeghtahani.rhinochat.chat.data.remote

import dev.sadeghtahani.rhinochat.chat.data.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.RemoteMessage

/** Platform adapters map expected transport failures to ChatDataException. */
interface ChatRemoteDataSource {
    suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage
    suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage
}
