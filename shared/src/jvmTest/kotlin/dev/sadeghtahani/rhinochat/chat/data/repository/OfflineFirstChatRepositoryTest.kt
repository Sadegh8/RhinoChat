package dev.sadeghtahani.rhinochat.chat.data.repository

import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.PaginationState
import dev.sadeghtahani.rhinochat.chat.data.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.buildChatDatabase
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Real Room plus a scripted transport: the lost-response cases extend the supplied fake. */
class OfflineFirstChatRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val stores = mutableListOf<ChatLocalStore>()
    private val remote = ScriptedRemote()
    private val clock = object : Clock {
        override fun now() = Instant.fromEpochMilliseconds(1_000)
    }

    @After fun closeStores() = stores.forEach(ChatLocalStore::close)

    @Test fun optimisticRowExistsBeforeNetworkAndRetainsItsKeyAfterAcknowledgement() = runTest {
        val store = openStore()
        val repository = repository(store)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.onSend = { clientId, text ->
            started.complete(Unit)
            release.await()
            remote.commit(clientId, text)
        }
        val pending = repository.enqueueMessage("chat", "Hello")
        val send = async { repository.sendPendingMessage("chat", pending.localId) }
        started.await()
        assertEquals(listOf(pending), store.observeMessages("chat").first())
        release.complete(Unit)
        assertEquals(ChatOperationResult.Completed, send.await())
        val delivered = store.observeMessages("chat").first().single()
        assertEquals(pending.localId, delivered.localId)
        assertEquals(pending.clientId, delivered.clientId)
        assertEquals(DeliveryStatus.SENT, delivered.deliveryStatus)
    }

    @Test fun failedRetryIsCoalescedAndReusesIdentityEvenAfterLostAcknowledgement() = runTest {
        val store = openStore()
        val repository = repository(store)
        val pending = repository.enqueueMessage("chat", "Hello")
        remote.onSend = { clientId, text ->
            remote.commit(clientId, text)
            throw networkFailure()
        }
        assertEquals(networkResult, repository.sendPendingMessage("chat", pending.localId))
        assertEquals(DeliveryStatus.FAILED, store.findMessage("chat", pending.localId)?.deliveryStatus)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.onSend = { clientId, text ->
            started.complete(Unit)
            release.await()
            remote.commit(clientId, text)
        }
        val firstRetry = async { repository.retryMessage("chat", pending.localId) }
        started.await()
        val repeats = List(8) { async { repository.retryMessage("chat", pending.localId) } }.awaitAll()
        assertTrue(repeats.all { it == ChatOperationResult.Ignored })
        release.complete(Unit)
        assertEquals(ChatOperationResult.Completed, firstRetry.await())
        assertEquals(listOf(pending.clientId, pending.clientId), remote.sentClientIds)
        assertEquals(1, remote.messages.size)
        assertEquals(1, store.observeMessages("chat").first().size)
    }

    @Test fun historyConfirmationWinsOverLateSendFailure() = runTest {
        val store = openStore()
        val repository = repository(store)
        val pending = repository.enqueueMessage("chat", "Hello")
        val committed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.onSend = { clientId, text ->
            remote.commit(clientId, text)
            committed.complete(Unit)
            release.await()
            throw networkFailure()
        }
        val send = async { repository.sendPendingMessage("chat", pending.localId) }
        committed.await()
        assertEquals(ChatOperationResult.Completed, repository.loadInitialMessages("chat"))
        release.complete(Unit)
        assertEquals(ChatOperationResult.Completed, send.await())
        assertEquals(DeliveryStatus.SENT, store.findMessage("chat", pending.localId)?.deliveryStatus)
    }

    @Test fun cancellationMakesTheInFlightMessageRetryableAndReleasesItsClaim() = runTest {
        val store = openStore()
        val repository = repository(store)
        val pending = repository.enqueueMessage("chat", "Hello")
        val started = CompletableDeferred<Unit>()
        remote.onSend = { _, _ -> started.complete(Unit); awaitCancellation() }
        val job = launch { repository.sendPendingMessage("chat", pending.localId) }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(DeliveryStatus.FAILED, store.findMessage("chat", pending.localId)?.deliveryStatus)
        remote.onSend = remote::commit
        assertEquals(ChatOperationResult.Completed, repository.retryMessage("chat", pending.localId))
    }

    @Test fun cancellationDuringEnqueueNeverLeavesAnOwnerlessSendingRow() = runTest {
        val store = openStore()
        val repository = repository(store)
        // Finish startup first so the cancellation races the insert, not startup recovery.
        repository.loadInitialMessages("chat")
        repeat(20) { attempt ->
            val enqueue = launch(start = CoroutineStart.UNDISPATCHED) {
                repository.enqueueMessage("chat", "Interrupted $attempt")
            }
            enqueue.cancelAndJoin()
        }
        // Room may roll back, or finish a commit; any committed intent must be retryable.
        assertTrue(store.observeMessages("chat").first().all { it.deliveryStatus == DeliveryStatus.FAILED })
    }

    @Test fun initializationRunsOnceAndDoesNotInterruptAnotherActiveSend() = runTest {
        val store = openStore()
        val repository = repository(store)
        val first = repository.enqueueMessage("chat", "First")
        val started = CompletableDeferred<Unit>()
        remote.onSend = { _, _ -> started.complete(Unit); awaitCancellation() }
        val send = launch { repository.sendPendingMessage("chat", first.localId) }
        started.await()
        repository.enqueueMessage("chat", "Second")
        repository.loadInitialMessages("chat")
        assertEquals(DeliveryStatus.SENDING, store.findMessage("chat", first.localId)?.deliveryStatus)
        send.cancelAndJoin()
    }

    @Test fun interruptedSendCanBeRetriedAfterReopeningWithItsOriginalClientId() = runTest {
        val path = temporaryFolder.root.resolve("restart.db").absolutePath
        val firstStore = openStore(path)
        val pending = repository(firstStore).enqueueMessage("chat", "Keep me")
        // Model process termination after local commit, before any response is processed.
        firstStore.close()
        stores.remove(firstStore)
        val reopened = openStore(path)
        assertEquals(ChatOperationResult.Completed, repository(reopened).retryMessage("chat", pending.localId))
        assertEquals(listOf(pending.clientId), remote.sentClientIds)
        assertEquals(DeliveryStatus.SENT, reopened.findMessage("chat", pending.localId)?.deliveryStatus)
    }

    @Test fun concurrentHistoryTriggersFetchOnceAndOlderCannotSkipTheHead() = runTest {
        val repository = repository(openStore())
        assertEquals(ChatOperationResult.Ignored, repository.loadOlderMessages("chat"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.onGet = { _, _ -> started.complete(Unit); release.await(); MessagePage(emptyList(), null) }
        val initial = async { repository.loadInitialMessages("chat") }
        started.await()
        val repeats = List(8) { async { repository.loadInitialMessages("chat") } }.awaitAll()
        assertTrue(repeats.all { it == ChatOperationResult.Ignored })
        assertEquals(ChatOperationResult.Ignored, repository.loadOlderMessages("chat"))
        release.complete(Unit)
        assertEquals(ChatOperationResult.Completed, initial.await())
        assertEquals(listOf<String?>(null), remote.cursors)
        assertEquals(listOf(20), remote.limits)
        assertEquals(ChatOperationResult.Ignored, repository.loadOlderMessages("chat"))
    }

    @Test fun failedPagesKeepTheCacheAndCursorAndRetryTheSameRequest() = runTest {
        val store = openStore()
        remote.seed(45)
        val repository = repository(store)
        remote.onGet = { _, _ -> throw networkFailure() }
        assertEquals(networkResult, repository.loadInitialMessages("chat"))
        assertEquals(PaginationState(), store.observePagination("chat").first())
        remote.onGet = remote::page
        repository.loadInitialMessages("chat")
        val before = store.observeMessages("chat").first()
        remote.onGet = { _, _ -> throw networkFailure() }
        assertEquals(networkResult, repository.loadOlderMessages("chat"))
        assertEquals(before, store.observeMessages("chat").first())
        assertEquals(PaginationState(true, "20"), store.observePagination("chat").first())
        remote.onGet = remote::page
        repository.loadOlderMessages("chat")
        assertEquals(listOf(null, null, "20", "20"), remote.cursors)
        assertEquals(40, store.observeMessages("chat").first().size)
    }

    @Test fun rapidOlderTriggersFetchTheCurrentCursorOnlyOnce() = runTest {
        val store = openStore()
        val repository = repository(store)
        remote.seed(60)
        repository.loadInitialMessages("chat")
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        remote.onGet = { cursor, limit ->
            started.complete(Unit)
            release.await()
            remote.page(cursor, limit)
        }
        val load = async { repository.loadOlderMessages("chat") }
        started.await()
        val repeats = List(8) { async { repository.loadOlderMessages("chat") } }.awaitAll()
        assertTrue(repeats.all { it == ChatOperationResult.Ignored })
        release.complete(Unit)
        assertEquals(ChatOperationResult.Completed, load.await())
        assertEquals(listOf(null, "20"), remote.cursors)
        assertEquals("40", store.observePagination("chat").first().nextCursor)
    }

    @Test fun insertsBetweenOffsetPagesProduceNoDuplicateOrMissingMessages() = runTest {
        val store = openStore()
        remote.seed(120)
        val repository = repository(store)
        repository.loadInitialMessages("chat")
        val outgoing = repository.enqueueMessage("chat", "Inserted at the head")
        repository.sendPendingMessage("chat", outgoing.localId)
        do {
            assertEquals(ChatOperationResult.Completed, repository.loadOlderMessages("chat"))
        } while (store.observePagination("chat").first().nextCursor != null)
        val all = store.observeMessages("chat").first()
        assertEquals(121, all.size)
        assertEquals(remote.messages.map { it.id }.toSet(), all.map { it.serverId }.toSet())
        assertEquals(all.size, all.map { it.localId }.toSet().size)
        assertEquals(ChatOperationResult.Ignored, repository.loadOlderMessages("chat"))
        assertEquals(listOf(null, "20", "40", "60", "80", "100", "120"), remote.cursors)
    }

    @Test fun duplicateOnlyPageAdvancesCursorInsteadOfEndingHistory() = runTest {
        val store = openStore()
        remote.seed(1)
        val repository = repository(store)
        remote.onGet = { cursor, _ -> MessagePage(remote.messages, if (cursor == null) "20" else "40") }
        repository.loadInitialMessages("chat")
        repository.loadOlderMessages("chat")
        assertEquals(1, store.observeMessages("chat").first().size)
        assertEquals("40", store.observePagination("chat").first().nextCursor)
    }

    @Test fun freshRepositoryRefreshesHeadBeforeUsingPersistedOffsets() = runTest {
        val store = openStore()
        remote.seed(60)
        repository(store).apply {
            loadInitialMessages("chat")
            loadOlderMessages("chat")
        }
        assertEquals("40", store.observePagination("chat").first().nextCursor)
        repository(store).apply {
            assertEquals(ChatOperationResult.Ignored, loadOlderMessages("chat"))
            loadInitialMessages("chat")
        }
        assertEquals(listOf(null, "20", null), remote.cursors)
        assertEquals("20", store.observePagination("chat").first().nextCursor)
        assertEquals(40, store.observeMessages("chat").first().size)
    }

    @Test fun cancelledPageDoesNotAdvanceCursorOrBlockAnotherAttempt() = runTest {
        val store = openStore()
        val repository = repository(store)
        remote.seed(40)
        repository.loadInitialMessages("chat")
        val started = CompletableDeferred<Unit>()
        remote.onGet = { _, _ -> started.complete(Unit); awaitCancellation() }
        val load = launch { repository.loadOlderMessages("chat") }
        started.await()
        load.cancelAndJoin()
        assertEquals("20", store.observePagination("chat").first().nextCursor)
        remote.onGet = remote::page
        assertEquals(ChatOperationResult.Completed, repository.loadOlderMessages("chat"))
        assertEquals(40, store.observeMessages("chat").first().size)
    }

    @Test fun resetServerIdentityConflictPreservesCachedOutgoingMessages() = runTest {
        val store = openStore()
        val repository = repository(store)
        val first = repository.enqueueMessage("chat", "Earlier session")
        repository.sendPendingMessage("chat", first.localId)
        remote.messages.clear() // The supplied in-memory fake loses idempotency after process death.
        val second = repository.enqueueMessage("chat", "New session")
        assertEquals(
            ChatOperationResult.Failed(ChatError.IDENTITY_CONFLICT),
            repository.sendPendingMessage("chat", second.localId),
        )
        val rows = store.observeMessages("chat").first()
        assertEquals(listOf("Earlier session", "New session"), rows.map { it.text })
        assertEquals(DeliveryStatus.SENT, rows.first().deliveryStatus)
        assertEquals(DeliveryStatus.FAILED, rows.last().deliveryStatus)
        assertFalse(rows.first().clientId == rows.last().clientId)
    }

    private fun openStore(path: String = temporaryFolder.newFile().absolutePath) =
        ChatLocalStore(buildChatDatabase(path)).also(stores::add)

    private fun repository(store: ChatLocalStore) = OfflineFirstChatRepository(store, remote, clock)

    private class ScriptedRemote : ChatRemoteDataSource {
        val messages = mutableListOf<RemoteMessage>()
        val sentClientIds = mutableListOf<String>()
        val cursors = mutableListOf<String?>()
        val limits = mutableListOf<Int>()
        var onSend: suspend (String, String) -> RemoteMessage = ::commit
        var onGet: suspend (String?, Int) -> MessagePage = ::page

        fun seed(count: Int) {
            repeat(count) { messages += RemoteMessage("srv-$it", null, "colleague", "Message $it", it.toLong()) }
        }

        fun commit(clientId: String, text: String): RemoteMessage =
            messages.find { it.clientId == clientId } ?: RemoteMessage(
                "srv-${messages.size}", clientId, "me", text, 1_000,
            ).also(messages::add)

        fun page(cursor: String?, limit: Int): MessagePage {
            val start = cursor?.toInt() ?: 0
            val page = messages.sortedByDescending { it.createdAtMillis }.drop(start).take(limit)
            return MessagePage(page, (start + page.size).takeIf { it < messages.size }?.toString())
        }

        override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
            cursors += cursor
            limits += limit
            return onGet(cursor, limit)
        }

        override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
            sentClientIds += clientId
            return onSend(clientId, text)
        }
    }

    private companion object {
        val networkResult = ChatOperationResult.Failed(ChatError.NETWORK)
        fun networkFailure() = ChatDataException(ChatError.NETWORK)
    }
}
