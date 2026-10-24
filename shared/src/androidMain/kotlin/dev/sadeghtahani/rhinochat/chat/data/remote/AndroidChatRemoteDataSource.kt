package dev.sadeghtahani.rhinochat.chat.data.remote

import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.ChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.MessageDto
import java.io.IOException

class AndroidChatRemoteDataSource(private val api: ChatApi) : ChatRemoteDataSource {
    override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage =
        transport {
            val page = api.getMessages(conversationId, cursor, limit)
            MessagePage(page.messages.map(MessageDto::asRemoteMessage), page.nextCursor)
        }

    override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage =
        transport { api.sendMessage(conversationId, clientId, text).asRemoteMessage() }

    private suspend fun <T> transport(block: suspend () -> T): T = try {
        block()
    } catch (failure: IOException) {
        throw ChatDataException(ChatError.NETWORK, failure)
    }
}

private fun MessageDto.asRemoteMessage() = RemoteMessage(id, clientId, senderId, text, createdAt)
