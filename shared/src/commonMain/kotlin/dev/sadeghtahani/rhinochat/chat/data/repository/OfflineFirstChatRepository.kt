package dev.sadeghtahani.rhinochat.chat.data.repository

import androidx.sqlite.SQLiteException
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.PaginationState
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.data.local.MessageIdentityConflictException
import dev.sadeghtahani.rhinochat.chat.data.remote.ChatRemoteDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** One application-owned instance shares in-flight work and the fake server's session. */
class OfflineFirstChatRepository(
    private val store: ChatLocalStore,
    private val remote: ChatRemoteDataSource,
    private val clock: Clock = Clock.System,
) : ChatRepository {
    private val initializationMutex = Mutex()
    private var initialized = false
    private val requestMutex = Mutex()
    private val activeSends = mutableSetOf<Pair<String, Long>>()
    private val activeLoads = mutableSetOf<String>()
    private val initializedConversations = mutableSetOf<String>()

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        store.observeMessages(conversationId).translateStorageErrors()

    override fun observePagination(conversationId: String): Flow<PaginationState> =
        store.observePagination(conversationId).translateStorageErrors()

    override fun observeDraft(conversationId: String): Flow<String> =
        store.observeDraft(conversationId).translateStorageErrors()

    override suspend fun saveDraft(conversationId: String, text: String) {
        try {
            store.saveDraft(conversationId, text)
        } catch (failure: SQLiteException) {
            throw ChatDataException(ChatError.STORAGE, failure)
        }
    }

    override suspend fun clearDraftIfMatches(conversationId: String, expectedText: String) {
        try {
            store.clearDraftIfMatches(conversationId, expectedText)
        } catch (failure: SQLiteException) {
            throw ChatDataException(ChatError.STORAGE, failure)
        }
    }

    override suspend fun enqueueMessage(conversationId: String, text: String): ChatMessage {
        return enqueueMessage(conversationId, text, submittedDraft = "")
    }

    override suspend fun enqueueMessage(
        conversationId: String,
        text: String,
        submittedDraft: String,
    ): ChatMessage {
        try {
            ensureInitialized()
            return store.enqueue(
                conversationId,
                text,
                clock.now().toEpochMilliseconds(),
                submittedDraft.takeIf { it.isNotEmpty() },
            )
        } catch (failure: SQLiteException) {
            throw ChatDataException(ChatError.STORAGE, failure)
        }
    }

    override suspend fun sendPendingMessage(conversationId: String, localId: Long) =
        deliver(conversationId, localId, SendMode.NEW)

    override suspend fun retryMessage(conversationId: String, localId: Long) =
        deliver(conversationId, localId, SendMode.MANUAL_RETRY)

    override suspend fun resumePendingMessage(conversationId: String, localId: Long) =
        deliver(conversationId, localId, SendMode.AUTOMATIC)

    override suspend fun loadInitialMessages(conversationId: String) =
        loadPage(conversationId, initial = true)

    override suspend fun loadOlderMessages(conversationId: String) =
        loadPage(conversationId, initial = false)

    override suspend fun resetHistoryCache(conversationId: String): ChatOperationResult = operation {
        require(conversationId.isNotBlank())
        if (!requestMutex.withLock { activeLoads.add(conversationId) }) return@operation ChatOperationResult.Ignored
        try {
            store.resetHistoryCache(conversationId)
            ChatOperationResult.Completed
        } finally {
            // Even cancellation just after the transaction commits must allow a fresh head.
            withContext(NonCancellable) {
                requestMutex.withLock {
                    initializedConversations.remove(conversationId)
                    activeLoads.remove(conversationId)
                }
            }
        }
    }

    private suspend fun deliver(
        conversationId: String,
        localId: Long,
        mode: SendMode,
    ): ChatOperationResult = operation {
        ensureInitialized()
        val key = conversationId to localId
        if (!requestMutex.withLock { activeSends.add(key) }) return@operation ChatOperationResult.Ignored
        try {
            val message = store.findMessage(conversationId, localId)
                ?: return@operation ChatOperationResult.Ignored
            val clientId = message.clientId ?: return@operation ChatOperationResult.Ignored
            when (message.deliveryStatus) {
                DeliveryStatus.SENT -> return@operation ChatOperationResult.Ignored
                DeliveryStatus.SENDING -> {
                    if (mode == SendMode.MANUAL_RETRY) return@operation ChatOperationResult.Ignored
                }
                DeliveryStatus.FAILED -> {
                    if (mode == SendMode.NEW) return@operation ChatOperationResult.Ignored
                    if (mode == SendMode.AUTOMATIC) {
                        val error = store.findLastDeliveryError(conversationId, localId)
                        if (error != null && error != ChatError.NETWORK) return@operation ChatOperationResult.Ignored
                    }
                    if (!store.beginRetry(conversationId, localId)) return@operation ChatOperationResult.Ignored
                }
            }

            try {
                val acknowledgement = remote.sendMessage(conversationId, clientId, message.text)
                store.acknowledge(conversationId, localId, acknowledgement)
                ChatOperationResult.Completed
            } catch (failure: ChatDataException) {
                failPending(conversationId, localId, failure.error)
            } catch (_: MessageIdentityConflictException) {
                failPending(conversationId, localId, ChatError.IDENTITY_CONFLICT)
            } catch (failure: SQLiteException) {
                failPending(conversationId, localId, ChatError.STORAGE)
            }
        } catch (cancelled: CancellationException) {
            // Includes cancellation just after a retry's local state transition commits.
            withContext(NonCancellable) {
                try {
                    store.markFailed(conversationId, localId, ChatError.NETWORK)
                } catch (cleanupFailure: Exception) {
                    cancelled.addSuppressed(cleanupFailure)
                }
            }
            throw cancelled
        } finally {
            withContext(NonCancellable) {
                requestMutex.withLock { activeSends.remove(key) }
            }
        }
    }

    private enum class SendMode { NEW, MANUAL_RETRY, AUTOMATIC }

    private suspend fun loadPage(conversationId: String, initial: Boolean): ChatOperationResult = operation {
        require(conversationId.isNotBlank())
        ensureInitialized()
        val claimed = requestMutex.withLock {
            val headIsLoaded = conversationId in initializedConversations
            (if (initial) !headIsLoaded else headIsLoaded) && activeLoads.add(conversationId)
        }
        if (!claimed) return@operation ChatOperationResult.Ignored
        try {
            val cursor = if (initial) null else {
                store.observePagination(conversationId).first().nextCursor
                    ?: return@operation ChatOperationResult.Ignored
            }
            val page = remote.getMessages(conversationId, cursor, limit = 20)
            store.savePage(conversationId, page, isInitialRefresh = initial)
            if (initial) requestMutex.withLock { initializedConversations.add(conversationId) }
            ChatOperationResult.Completed
        } finally {
            withContext(NonCancellable) {
                requestMutex.withLock { activeLoads.remove(conversationId) }
            }
        }
    }

    private suspend fun failPending(
        conversationId: String,
        localId: Long,
        error: ChatError,
    ): ChatOperationResult {
        store.markFailed(conversationId, localId, error)
        // History may have confirmed delivery while a send response was being lost.
        return if (store.findMessage(conversationId, localId)?.deliveryStatus == DeliveryStatus.SENT) {
            ChatOperationResult.Completed
        } else {
            ChatOperationResult.Failed(error)
        }
    }

    private suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (!initialized) {
                store.recoverInterruptedMessages()
                initialized = true
            }
        }
    }

    private suspend fun operation(block: suspend () -> ChatOperationResult): ChatOperationResult = try {
        block()
    } catch (failure: ChatDataException) {
        ChatOperationResult.Failed(failure.error)
    } catch (failure: MessageIdentityConflictException) {
        ChatOperationResult.Failed(ChatError.IDENTITY_CONFLICT)
    } catch (failure: SQLiteException) {
        ChatOperationResult.Failed(ChatError.STORAGE)
    }

    private fun <T> Flow<T>.translateStorageErrors(): Flow<T> = catch { failure ->
        if (failure is SQLiteException) throw ChatDataException(ChatError.STORAGE, failure)
        throw failure
    }
}
