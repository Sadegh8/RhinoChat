package dev.sadeghtahani.rhinochat.demo

import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkSettings
import dev.sadeghtahani.rhinochat.demo.data.remote.DemoChatRemoteDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DemoChatRemoteDataSourceTest {
    private val controls = DemoNetworkControls()
    private val server = RecordingServer()
    private val remote = DemoChatRemoteDataSource(server, controls, object : Random() {
        override fun nextBits(bitCount: Int) = 0
    })

    @Test fun offlineBlocksBothOperationsAndTurningItOffAllowsRecovery() = runTest {
        controls.update(DemoNetworkSettings(offline = true))
        assertFailsWith<ChatDataException> { remote.getMessages("chat", null, 20) }
        assertFailsWith<ChatDataException> { remote.sendMessage("chat", "client", "hello") }
        assertEquals(0, server.historyCalls)
        assertEquals(0, server.sendCalls)
        controls.update(DemoNetworkSettings())
        remote.getMessages("chat", null, 20)
        remote.sendMessage("chat", "client", "hello")
        assertEquals(1, server.historyCalls)
        assertEquals(1, server.sendCalls)
    }

    @Test fun sendAndHistoryFailuresAreIndependent() = runTest {
        controls.update(DemoNetworkSettings(failSends = true))
        remote.getMessages("chat", null, 20)
        assertFailsWith<ChatDataException> { remote.sendMessage("chat", "client", "hello") }
        controls.update(DemoNetworkSettings(failHistory = true))
        assertFailsWith<ChatDataException> { remote.getMessages("chat", "20", 20) }
        remote.sendMessage("chat", "client", "hello")
        assertEquals(1, server.historyCalls)
        assertEquals(1, server.sendCalls)
    }

    @Test fun lostReplyCommitsBeforeFailureAndRetryReturnsTheSameMessage() = runTest {
        controls.update(DemoNetworkSettings(loseFirstReply = true))
        assertFailsWith<ChatDataException> { remote.sendMessage("chat", "client", "hello") }
        assertEquals(1, server.messages.size)
        val reply = remote.sendMessage("chat", "client", "hello")
        assertEquals(server.messages.values.single(), reply)
        assertEquals(2, server.sendCalls)
        assertEquals(1, server.messages.size)
        // A separate deliberate send still loses its own first reply.
        assertFailsWith<ChatDataException> { remote.sendMessage("chat", "other", "hello") }
        assertEquals(2, server.messages.size)
    }

    @Test fun randomFailuresAreOptInAndCanBeTurnedOff() = runTest {
        remote.getMessages("chat", null, 20)
        controls.update(DemoNetworkSettings(randomFailures = true))
        assertFailsWith<ChatDataException> { remote.getMessages("chat", null, 20) }
        controls.update(DemoNetworkSettings())
        remote.getMessages("chat", null, 20)
        assertEquals(2, server.historyCalls)
    }

    @Test fun slowConnectionDelaysTransportAndCancellationDoesNotReachServer() = runTest {
        controls.update(DemoNetworkSettings(slowNetwork = true))
        val request = async { remote.getMessages("chat", null, 20) }
        runCurrent()
        advanceTimeBy(2_999)
        assertFalse(request.isCompleted)
        assertEquals(0, server.historyCalls)
        advanceTimeBy(1)
        runCurrent()
        request.await()
        assertEquals(1, server.historyCalls)
        val cancelled = async { remote.sendMessage("chat", "client", "hello") }
        runCurrent()
        cancelled.cancelAndJoin()
        assertEquals(0, server.sendCalls)
    }

    private class RecordingServer : ChatRemoteDataSource {
        var historyCalls = 0
        var sendCalls = 0
        val messages = mutableMapOf<Pair<String, String>, RemoteMessage>()
        override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
            historyCalls++
            return MessagePage(messages.values.toList(), null)
        }
        override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
            sendCalls++
            return messages.getOrPut(conversationId to clientId) { RemoteMessage("server-${messages.size}", clientId, "me", text, 1_000) }
        }
    }
}
