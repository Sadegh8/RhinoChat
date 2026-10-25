package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.sqlite.SQLiteException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.remote.model.MessagePage
import dev.sadeghtahani.rhinochat.chat.domain.model.PaginationState
import dev.sadeghtahani.rhinochat.chat.data.remote.model.RemoteMessage
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Uses the production Room builder and bundled SQLite with a real file, not a fake DAO. */
class ChatLocalStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var database: ChatDatabase

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun cachedHistoryUnsentIdentityAndCursorSurviveReopening() = runTest {
        val path = databaseFile()
        var store = openStore(path)
        store.savePage("chat", MessagePage(listOf(remote("srv-1")), nextCursor = "20"))
        val outgoing = store.enqueue("chat", "Keep this message", 200)
        store.markFailed("chat", outgoing.localId)
        store.close()

        store = openStore(path)
        val messages = store.observeMessages("chat").first()
        assertEquals(2, messages.size)
        assertEquals("srv-1", messages.first().serverId)
        assertEquals(outgoing.copy(deliveryStatus = DeliveryStatus.FAILED), messages.last())
        assertEquals(PaginationState(true, "20"), store.observePagination("chat").first())
    }

    @Test
    fun draftAndOutgoingMessageSurviveReopenWithoutClearingNewerDraft() = runTest {
        val path = databaseFile()
        var store = openStore(path)
        store.saveDraft("chat", "Submitted draft")
        val first = store.enqueue("chat", "Submitted draft", 100, submittedDraft = "Submitted draft")
        assertEquals("", store.observeDraft("chat").first())

        store.saveDraft("chat", "Typed afterward")
        val second = store.enqueue("chat", "Another message", 200, submittedDraft = "Submitted draft")
        store.close()

        store = openStore(path)
        assertEquals("Typed afterward", store.observeDraft("chat").first())
        assertEquals(first, store.findMessage("chat", first.localId))
        assertEquals(second, store.findMessage("chat", second.localId))
    }

    @Test
    fun versionOneDatabaseMigratesMessagesAndPaginationIntoVersionTwo() = runTest {
        val path = databaseFile()
        createVersionOneDatabase(path.absolutePath)

        val store = openStore(path)
        val messages = store.observeMessages("chat").first()
        assertEquals(1, messages.size)
        assertEquals("client-1", messages.single().clientId)
        assertEquals("Keep this failed message", messages.single().text)
        assertEquals(DeliveryStatus.FAILED, messages.single().deliveryStatus)
        assertNull(store.findLastDeliveryError("chat", messages.single().localId))
        assertEquals(PaginationState(true, "20"), store.observePagination("chat").first())
        assertEquals("", store.observeDraft("chat").first())

        store.saveDraft("chat", "Draft after migration")
        assertEquals("Draft after migration", store.observeDraft("chat").first())
    }

    @Test
    fun identicalTextStillCreatesTwoDistinctMessagesInStableOrder() = runTest {
        val store = openStore()
        val first = store.enqueue("chat", "Same text", 100)
        val second = store.enqueue("chat", "Same text", 100)

        assertNotEquals(first.localId, second.localId)
        assertNotEquals(first.clientId, second.clientId)
        assertNotNull(first.clientId)
        assertNull(first.serverId)
        assertEquals(listOf(first, second), store.observeMessages("chat").first())
    }

    @Test
    fun retryIsClaimedOnceAndRetainsThePersistedIdentity() = runTest {
        val store = openStore()
        val outgoing = store.enqueue("chat", "Retry me", 100)
        store.markFailed("chat", outgoing.localId)

        val claims = coroutineScope {
            List(8) {
                async(Dispatchers.Default) { store.beginRetry("chat", outgoing.localId) }
            }.awaitAll()
        }

        assertEquals(1, claims.count { it })
        assertEquals(outgoing, store.findMessage("chat", outgoing.localId))
        assertEquals(1, store.observeMessages("chat").first().size)
    }

    @Test
    fun acknowledgementAndRepeatedHistoryKeepTheOptimisticRow() = runTest {
        val store = openStore()
        val outgoing = store.enqueue("chat", "Hello", 100)
        val acknowledgement = remote("srv-120", outgoing.clientId, "Hello", "me")

        store.acknowledge("chat", outgoing.localId, acknowledgement)
        repeat(2) {
            store.savePage("chat", MessagePage(listOf(acknowledgement), nextCursor = "20"))
        }
        store.markFailed("chat", outgoing.localId)

        val stored = store.observeMessages("chat").first().single()
        assertEquals(outgoing.localId, stored.localId)
        assertEquals(outgoing.clientId, stored.clientId)
        assertEquals("srv-120", stored.serverId)
        assertEquals(DeliveryStatus.SENT, stored.deliveryStatus)
        assertFalse(store.beginRetry("chat", outgoing.localId))
    }

    @Test
    fun serverOnlyHistoryRowIsReconciledWithTheOptimisticRow() = runTest {
        val store = openStore()
        val outgoing = store.enqueue("chat", "Hello", 100)
        // Robustness beyond the supplied fake: history has not supplied the client identity yet.
        store.savePage("chat", MessagePage(listOf(remote("srv-120", text = "Hello", senderId = "me")), "20"))
        assertEquals(2, store.observeMessages("chat").first().size)

        store.acknowledge("chat", outgoing.localId, remote("srv-120", outgoing.clientId, "Hello", "me"))

        val stored = store.observeMessages("chat").first().single()
        assertEquals(outgoing.localId, stored.localId)
        assertEquals(outgoing.clientId, stored.clientId)
        assertEquals(DeliveryStatus.SENT, stored.deliveryStatus)
    }

    @Test
    fun duplicateOnlyPagesStillAdvanceTheServerCursor() = runTest {
        val store = openStore()
        val messages = listOf(remote("srv-1"), remote("srv-2"))
        store.savePage("chat", MessagePage(messages, "20"))
        store.savePage("chat", MessagePage(messages, "40"))

        assertEquals(2, store.observeMessages("chat").first().size)
        assertEquals(PaginationState(true, "40"), store.observePagination("chat").first())
    }

    @Test
    fun emptyLoadedHistoryIsDifferentFromHistoryThatHasNotLoaded() = runTest {
        val store = openStore()
        assertEquals(PaginationState(), store.observePagination("chat").first())

        store.savePage("chat", MessagePage(emptyList(), nextCursor = null))

        assertEquals(PaginationState(hasLoadedInitialPage = true), store.observePagination("chat").first())
        assertTrue(store.observeMessages("chat").first().isEmpty())
    }

    @Test
    fun conflictingReusedServerIdentityRollsBackTheWholePageAndCursor() = runTest {
        val store = openStore()
        val original = remote("srv-120", "previous-client", "Previous send", "me")
        store.savePage("chat", MessagePage(listOf(original), "20"))
        val before = store.observeMessages("chat").first()

        assertFailsWith<MessageIdentityConflictException> {
            store.savePage(
                "chat",
                MessagePage(
                    listOf(remote("srv-121"), remote("srv-120", "new-client", "New send", "me")),
                    "40",
                ),
            )
        }

        assertEquals(before, store.observeMessages("chat").first())
        assertEquals(PaginationState(true, "20"), store.observePagination("chat").first())
    }

    @Test
    fun identityConstraintsAreEnforcedAndScopedToTheConversation() = runTest {
        val store = openStore()
        val message = remote("srv-1", "client-1")
        store.savePage("first", MessagePage(listOf(message), null))
        store.savePage("second", MessagePage(listOf(message), null))
        val first = store.observeMessages("first").first().single()
        val second = store.observeMessages("second").first().single()
        assertNotEquals(first.localId, second.localId)
        assertNull(store.findMessage("first", second.localId))

        val dao = database.messageDao()
        val entity = assertNotNull(dao.findByLocalId("first", first.localId))
        assertFailsWith<SQLiteException> {
            dao.insert(entity.copy(localId = 0, serverId = "another-server"))
        }
        assertFailsWith<SQLiteException> {
            dao.insert(entity.copy(localId = 0, clientId = "another-client"))
        }
        assertEquals(listOf(first), store.observeMessages("first").first())
        assertEquals(listOf(second), store.observeMessages("second").first())
    }

    @Test
    fun interruptedSendingRowsRecoverWithoutRegressingSentMessages() = runTest {
        val path = databaseFile()
        var store = openStore(path)
        val outgoing = store.enqueue("chat", "Interrupted", 200)
        store.savePage("chat", MessagePage(listOf(remote("srv-1")), null))
        store.close()

        store = openStore(path)
        assertEquals(1, store.recoverInterruptedMessages())
        assertEquals(0, store.recoverInterruptedMessages())
        assertEquals(outgoing.copy(deliveryStatus = DeliveryStatus.FAILED), store.findMessage("chat", outgoing.localId))
        assertEquals(DeliveryStatus.SENT, store.observeMessages("chat").first().first().deliveryStatus)
    }

    @Test
    fun observersSeePersistedMessages() = runTest {
        val store = openStore()
        assertTrue(store.observeMessages("chat").first().isEmpty())
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            store.observeMessages("chat").first { it.isNotEmpty() }
        }

        val outgoing = store.enqueue("chat", "Observe me", 100)

        assertEquals(listOf(outgoing), observed.await())
    }

    private fun databaseFile() = File(temporaryFolder.newFolder(), "chat.db")

    private fun createVersionOneDatabase(path: String) {
        BundledSQLiteDriver().open(path).use { connection ->
            listOf(
                """CREATE TABLE IF NOT EXISTS `messages` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversationId` TEXT NOT NULL, `clientId` TEXT, `serverId` TEXT, `senderId` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL, `deliveryStatus` TEXT NOT NULL)""",
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_conversationId_clientId` ON `messages` (`conversationId`, `clientId`)""",
                """CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_conversationId_serverId` ON `messages` (`conversationId`, `serverId`)""",
                """CREATE INDEX IF NOT EXISTS `index_messages_conversationId_createdAtMillis_localId` ON `messages` (`conversationId`, `createdAtMillis`, `localId`)""",
                """CREATE TABLE IF NOT EXISTS `conversation_pagination` (`conversationId` TEXT NOT NULL, `nextCursor` TEXT, PRIMARY KEY(`conversationId`))""",
                """CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)""",
            ).forEach { connection.execute(it) }

            connection.execute(
                """INSERT INTO `messages` (`localId`, `conversationId`, `clientId`, `serverId`, `senderId`, `text`, `createdAtMillis`, `deliveryStatus`) VALUES (1, 'chat', 'client-1', NULL, 'me', 'Keep this failed message', 100, 'FAILED')""",
            )
            connection.execute("INSERT INTO `conversation_pagination` (`conversationId`, `nextCursor`) VALUES ('chat', '20')")
            connection.execute(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '2bb5a6e8e659b7627412ad9054ca7177')",
            )
            connection.execute("PRAGMA user_version = 1")
        }
    }

    private fun SQLiteConnection.execute(sql: String) {
        prepare(sql).use { it.step() }
    }

    private fun openStore(file: File = databaseFile()): ChatLocalStore {
        database = buildChatDatabase(file.absolutePath)
        return ChatLocalStore(database)
    }

    private fun remote(
        id: String,
        clientId: String? = null,
        text: String = "A message",
        senderId: String = "colleague",
    ) = RemoteMessage(id, clientId, senderId, text, createdAtMillis = 100)
}
