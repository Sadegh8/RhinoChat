package dev.sadeghtahani.rhinochat.chat.data.remote

import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.ChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.MessageDto
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.MessagePageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidChatRemoteDataSourceTest {
    @Test fun suppliedApiPaginatesNewestFirstAndIsIdempotentByClientId() = runTest {
        val remote = AndroidChatRemoteDataSource(FakeChatApi(failureRate = 0.0, random = Random(42)))
        val first = remote.getMessages("chat", null, 20)
        assertEquals((119 downTo 100).map { "srv-$it" }, first.messages.map { it.id })
        assertEquals("20", first.nextCursor)
        val sent = remote.sendMessage("chat", "persisted-client", "Hello")
        val retried = remote.sendMessage("chat", "persisted-client", "Hello")
        assertEquals(sent, retried)
        assertEquals("srv-120", sent.id)
        assertEquals("persisted-client", sent.clientId)
        val shifted = remote.getMessages("chat", first.nextCursor, 20)
        assertEquals("srv-100", shifted.messages.first().id)
        assertEquals("40", shifted.nextCursor)
        val refreshed = remote.getMessages("chat", null, 20)
        assertEquals(1, refreshed.messages.count { it.clientId == "persisted-client" })
    }

    @Test fun suppliedFailuresAreMappedToNetworkErrors() = runTest {
        val remote = AndroidChatRemoteDataSource(FakeChatApi(failureRate = 1.0, random = Random(42)))
        assertEquals(ChatError.NETWORK, assertFailsWith<ChatDataException> {
            remote.getMessages("chat", null, 20)
        }.error)
        assertEquals(ChatError.NETWORK, assertFailsWith<ChatDataException> {
            remote.sendMessage("chat", "client", "Hello")
        }.error)
    }

    @Test fun cancellationPropagatesWithoutBeingMappedToANetworkFailure() = runTest {
        val cancelled = CancellationException("Owner ended")
        val api = object : ChatApi {
            override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePageDto = throw cancelled
            override suspend fun sendMessage(conversationId: String, clientId: String, text: String): MessageDto = throw cancelled
        }
        val remote = AndroidChatRemoteDataSource(api)
        assertTrue(assertFailsWith<CancellationException> { remote.getMessages("chat", null, 20) } === cancelled)
        assertTrue(assertFailsWith<CancellationException> { remote.sendMessage("chat", "client", "Hello") } === cancelled)
    }
}
