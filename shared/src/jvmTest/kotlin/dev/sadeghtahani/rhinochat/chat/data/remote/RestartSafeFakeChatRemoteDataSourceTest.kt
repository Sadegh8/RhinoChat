package dev.sadeghtahani.rhinochat.chat.data.remote

import androidx.sqlite.SQLiteException
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.MessagePage
import dev.sadeghtahani.rhinochat.chat.data.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.buildChatDatabase
import dev.sadeghtahani.rhinochat.chat.data.remote.supplied.FakeChatApi
import dev.sadeghtahani.rhinochat.chat.data.repository.OfflineFirstChatRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Actual supplied fake + production adapters + file-backed Room, including fake resets. */
class RestartSafeFakeChatRemoteDataSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val stores = mutableListOf<ChatLocalStore>()

    @After fun closeStores() = stores.forEach(ChatLocalStore::close)

    @Test fun legacyCacheSurvivesReopenAndNewSendDespiteTheFakeReusingItsServerId() = runTest {
        val path = temporaryFolder.newFile().absolutePath
        val original = openStore(path)
        val old = original.enqueue("chat", "Earlier process", 100)
        original.acknowledge("chat", old.localId, RemoteMessage("srv-120", old.clientId, "me", old.text, 150))
        val pending = original.enqueue("chat", "Retry after restart", 200)
        original.markFailed("chat", pending.localId)
        original.savePage("chat", MessagePage(listOf(RemoteMessage("srv-119", null, "colleague", "پیام شماره 119", 1_760_000_000_000L + 119 * 60_000L)), "20"))
        val before = original.observeMessages("chat").first()
        original.close()
        stores.remove(original)

        val reopened = openStore(path)
        val remote = freshRemote(reopened)
        val repository = OfflineFirstChatRepository(reopened, remote)
        assertEquals(ChatOperationResult.Completed, repository.loadInitialMessages("chat"))
        before.forEach { expected ->
            val normalized = if (expected.serverId == "srv-120") expected.copy(serverId = "fake-client:${expected.clientId}") else expected
            assertEquals(normalized, reopened.findMessage("chat", expected.localId))
        }
        assertEquals(ChatOperationResult.Completed, repository.retryMessage("chat", pending.localId))
        val next = repository.enqueueMessage("chat", "New process")
        assertEquals(ChatOperationResult.Completed, repository.sendPendingMessage("chat", next.localId))
        assertEquals("fake-client:${next.clientId}", reopened.findMessage("chat", next.localId)?.serverId)
        assertEquals(DeliveryStatus.SENT, reopened.findMessage("chat", pending.localId)?.deliveryStatus)

        // Another fresh fake/repository models another process and reuses srv-120 again.
        val restarted = OfflineFirstChatRepository(reopened, freshRemote(reopened))
        val third = restarted.enqueueMessage("chat", "Another process")
        assertEquals(ChatOperationResult.Completed, restarted.sendPendingMessage("chat", third.localId))
        assertEquals(ChatOperationResult.Completed, restarted.loadInitialMessages("chat"))
        val rows = reopened.observeMessages("chat").first()
        assertEquals(1, rows.count { it.localId == old.localId && it.text == old.text })
        assertEquals(1, rows.count { it.clientId == pending.clientId })
        assertEquals(1, rows.count { it.clientId == next.clientId })
        assertEquals(1, rows.count { it.clientId == third.clientId })
    }

    @Test fun lostAcknowledgementRetryAndAllHistoryPagesKeepOneCanonicalOutgoingRow() = runTest {
        val store = openStore()
        val transport = AndroidChatRemoteDataSource(FakeChatApi(failureRate = 0.0, random = Random(42)))
        var loseResponse = true
        val lossy = object : ChatRemoteDataSource by transport {
            override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage {
                val result = transport.sendMessage(conversationId, clientId, text)
                if (loseResponse) {
                    loseResponse = false
                    throw ChatDataException(ChatError.NETWORK)
                }
                return result
            }
        }
        val remote = RestartSafeFakeChatRemoteDataSource(lossy, store)
        val repository = OfflineFirstChatRepository(store, remote)
        assertEquals(ChatOperationResult.Completed, repository.loadInitialMessages("chat"))
        val pending = repository.enqueueMessage("chat", "One intent")
        assertEquals(ChatOperationResult.Failed(ChatError.NETWORK), repository.sendPendingMessage("chat", pending.localId))
        assertEquals(ChatOperationResult.Completed, repository.retryMessage("chat", pending.localId))
        val acknowledged = store.findMessage("chat", pending.localId)
        assertEquals("fake-client:${pending.clientId}", acknowledged?.serverId)
        assertEquals(DeliveryStatus.SENT, acknowledged?.deliveryStatus)

        // Refresh includes the outgoing row, then offset pages include overlaps.
        val refreshed = OfflineFirstChatRepository(store, remote)
        assertEquals(ChatOperationResult.Completed, refreshed.loadInitialMessages("chat"))
        while (store.observePagination("chat").first().nextCursor != null) {
            assertEquals(ChatOperationResult.Completed, refreshed.loadOlderMessages("chat"))
        }
        val rows = store.observeMessages("chat").first()
        assertEquals(121, rows.size)
        assertEquals(acknowledged, rows.single { it.clientId == pending.clientId })
        assertEquals((0..119).map { "srv-$it" }.toSet(), rows.filter { it.clientId == null }.map { it.serverId }.toSet())
        assertEquals(1, transport.getMessages("chat", null, 20).messages.count { it.clientId == pending.clientId })
    }

    @Test fun cacheConversionRollsBackAndDoesNotCallTransportWhenAnIdentityConflicts() = runTest {
        val store = openStore()
        val old = store.enqueue("chat", "Legacy acknowledgement", 100)
        store.acknowledge("chat", old.localId, RemoteMessage("srv-120", old.clientId, "me", old.text, 100))
        val other = store.enqueue("chat", "Conflicting namespace", 200)
        store.acknowledge("chat", other.localId, RemoteMessage("fake-client:${old.clientId}", other.clientId, "me", other.text, 200))
        store.savePage("chat", MessagePage(emptyList(), "40"))
        val before = store.observeMessages("chat").first()
        var transportCalled = false
        val delegate = object : ChatRemoteDataSource {
            override suspend fun getMessages(conversationId: String, cursor: String?, limit: Int): MessagePage {
                transportCalled = true
                return MessagePage(emptyList(), null)
            }
            override suspend fun sendMessage(conversationId: String, clientId: String, text: String): RemoteMessage = error("Unused")
        }
        val remote = RestartSafeFakeChatRemoteDataSource(delegate, store)
        repeat(2) { assertFailsWith<SQLiteException> { remote.getMessages("chat", null, 20) } }
        assertTrue(!transportCalled)
        assertEquals(before, store.observeMessages("chat").first())
        assertEquals("40", store.observePagination("chat").first().nextCursor)
    }

    @Test fun cachedHistoryAndUnsentIdentitySurviveReopenWhenEveryRemoteRequestFails() = runTest {
        val path = temporaryFolder.newFile().absolutePath
        val original = openStore(path)
        val first = OfflineFirstChatRepository(original, freshRemote(original))
        assertEquals(ChatOperationResult.Completed, first.loadInitialMessages("chat"))
        val pending = first.enqueueMessage("chat", "Keep through offline reopen")
        original.markFailed("chat", pending.localId)
        val before = original.observeMessages("chat").first()
        val cursor = original.observePagination("chat").first()
        original.close()
        stores.remove(original)

        val reopened = openStore(path)
        val failing = RestartSafeFakeChatRemoteDataSource(
            AndroidChatRemoteDataSource(FakeChatApi(failureRate = 1.0, random = Random(42))), reopened,
        )
        val repository = OfflineFirstChatRepository(reopened, failing)
        assertEquals(ChatOperationResult.Failed(ChatError.NETWORK), repository.loadInitialMessages("chat"))
        assertEquals(before, repository.observeMessages("chat").first())
        assertEquals(cursor, repository.observePagination("chat").first())
        assertEquals(ChatOperationResult.Failed(ChatError.NETWORK), repository.retryMessage("chat", pending.localId))
        assertEquals(pending.copy(deliveryStatus = DeliveryStatus.FAILED), reopened.findMessage("chat", pending.localId))
    }

    private fun freshRemote(store: ChatLocalStore) = RestartSafeFakeChatRemoteDataSource(
        AndroidChatRemoteDataSource(FakeChatApi(failureRate = 0.0, random = Random(42))), store,
    )

    private fun openStore(path: String = temporaryFolder.newFile().absolutePath) =
        ChatLocalStore(buildChatDatabase(path)).also(stores::add)
}
