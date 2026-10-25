package dev.sadeghtahani.rhinochat.chat.data.remote

import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.ChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.MessageDto
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.MessagePageDto
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.demo.data.remote.DemoNetworkRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AndroidChatRemoteDataSourceTest {
    @Test fun defaultFailureThresholdAndReliableOverrideUseTheSameSuppliedServer() = runTest {
        val controls = DemoNetworkControls()
        val draws = ArrayDeque(listOf(0.19, 0.2, 0.19))
        val random = DemoNetworkRandom(controls, object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(): Double = draws.removeFirst()
        })
        // Deliberately omit failureRate: this checks the supplied default, not a replacement.
        val remote = AndroidChatRemoteDataSource(FakeChatApi(random = random))
        assertTrue(controls.settings.value.randomFailures)
        assertEquals(ChatError.NETWORK, assertFailsWith<ChatDataException> {
            remote.getMessages("chat", null, 20)
        }.error)
        assertEquals(20, remote.getMessages("chat", null, 20).messages.size)

        controls.update(controls.settings.value.copy(randomFailures = false))
        val sent = remote.sendMessage("chat", "same-client", "Keep this server")
        controls.update(controls.settings.value.copy(randomFailures = true))
        assertFailsWith<ChatDataException> { remote.sendMessage("chat", "same-client", sent.text) }
        controls.update(controls.settings.value.copy(randomFailures = false))
        assertEquals(sent, remote.sendMessage("chat", "same-client", sent.text))
        assertEquals(1, remote.getMessages("chat", null, 20).messages.count { it.clientId == "same-client" })
        assertTrue(draws.isEmpty())
    }

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
