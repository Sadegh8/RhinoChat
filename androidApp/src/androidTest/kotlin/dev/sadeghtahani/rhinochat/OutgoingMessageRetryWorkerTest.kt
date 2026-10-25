package dev.sadeghtahani.rhinochat

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.domain.repository.ChatRepository
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkSettings
import dev.sadeghtahani.rhinochat.testing.ChatUiFixture
import dev.sadeghtahani.rhinochat.testing.SendReply
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class OutgoingMessageRetryWorkerTest {
    private val fixture = ChatUiFixture()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture)

    @Test fun initializedRepositoryRecoversOrphanSendingRowAndDeliversIt() = runBlocking {
        val repository = GlobalContext.get().get<ChatRepository>()
        val message = repository.enqueueMessage(ChatUiFixture.CONVERSATION, "Owner left before transport")
        assertEquals(DeliveryStatus.SENDING, message.deliveryStatus)
        val delivery = async { worker(message).doWork() }
        val attempt = withTimeout(10_000) { fixture.remote.sendStarted.receive() }
        assertEquals(message.clientId, attempt.clientId)
        attempt.reply.complete(SendReply.ACKNOWLEDGE)
        assertTrue(delivery.await() is ListenableWorker.Result.Success)
        assertEquals(DeliveryStatus.SENT, fixture.store.findMessage(message.conversationId, message.localId)?.deliveryStatus)
        assertEquals(1, fixture.remote.attempts.size)
        assertEquals(1, fixture.remote.serverCopies(requireNotNull(message.clientId)))
    }

    @Test fun initializedRepositoryRecordsOfflineFailureAndThenDeliversSameIdentity() = runBlocking {
        val repository = GlobalContext.get().get<ChatRepository>()
        val message = repository.enqueueMessage(ChatUiFixture.CONVERSATION, "Recover interrupted send")
        fixture.controls.update(DemoNetworkSettings(offline = true))
        assertTrue(worker(message).doWork() is ListenableWorker.Result.Retry)
        assertEquals(DeliveryStatus.FAILED, fixture.store.findMessage(message.conversationId, message.localId)?.deliveryStatus)
        assertEquals(ChatError.NETWORK, fixture.store.findLastDeliveryError(message.conversationId, message.localId))
        assertEquals(0, fixture.remote.attempts.size)
        fixture.controls.update(DemoNetworkSettings())
        val delivery = async { worker(message).doWork() }
        val attempt = withTimeout(10_000) { fixture.remote.sendStarted.receive() }
        assertEquals(message.clientId, attempt.clientId)
        attempt.reply.complete(SendReply.ACKNOWLEDGE)
        assertTrue(delivery.await() is ListenableWorker.Result.Success)
        val sent = requireNotNull(fixture.store.findMessage(message.conversationId, message.localId))
        assertEquals(message.localId, sent.localId)
        assertEquals(message.clientId, sent.clientId)
        assertEquals(DeliveryStatus.SENT, sent.deliveryStatus)
    }

    private fun worker(message: ChatMessage) = TestListenableWorkerBuilder<OutgoingMessageRetryWorker>(
        InstrumentationRegistry.getInstrumentation().targetContext,
    ).setInputData(workDataOf(
        OutgoingMessageRetryWorker.KEY_CONVERSATION_ID to message.conversationId,
        OutgoingMessageRetryWorker.KEY_LOCAL_ID to message.localId,
    )).build()
}
