package dev.sadeghtahani.rhinochat.chat.data.local

import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.remote.model.MessagePage
import dev.sadeghtahani.rhinochat.chat.domain.model.PaginationState
import dev.sadeghtahani.rhinochat.chat.data.remote.model.RemoteMessage
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationDraftEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.ConversationPaginationEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.MessageEntity
import dev.sadeghtahani.rhinochat.chat.data.local.data.asExternalModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Local source of truth. Network orchestration belongs in the repository. */
@OptIn(ExperimentalUuidApi::class)
class ChatLocalStore internal constructor(
    private val database: ChatDatabase,
    private val newClientId: () -> String = { Uuid.random().toString() },
) : AutoCloseable {
    private val dao = database.messageDao()

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        require(conversationId.isNotBlank())
        return dao.observeMessages(conversationId)
            .map { messages -> messages.map(MessageEntity::asExternalModel) }
            .distinctUntilChanged()
    }

    fun observePagination(conversationId: String): Flow<PaginationState> {
        require(conversationId.isNotBlank())
        return dao.observePagination(conversationId)
            .map { PaginationState(hasLoadedInitialPage = it != null, nextCursor = it?.nextCursor) }
            .distinctUntilChanged()
    }

    fun observeDraft(conversationId: String): Flow<String> {
        require(conversationId.isNotBlank())
        return dao.observeDraft(conversationId)
            .map { draft -> draft?.text.orEmpty() }
            .distinctUntilChanged()
    }

    suspend fun findMessage(conversationId: String, localId: Long): ChatMessage? = try {
        dao.findByLocalId(conversationId, localId)?.asExternalModel()
    } catch (failure: SQLiteException) {
        throw ChatDataException(ChatError.STORAGE, failure)
    }

    suspend fun findLastDeliveryError(conversationId: String, localId: Long): ChatError? = try {
        dao.findLastDeliveryError(conversationId, localId)
    } catch (failure: SQLiteException) {
        throw ChatDataException(ChatError.STORAGE, failure)
    }

    suspend fun findRetryableMessages(): List<ChatMessage> = try {
        dao.findRetryableMessages().map(MessageEntity::asExternalModel)
    } catch (failure: SQLiteException) {
        throw ChatDataException(ChatError.STORAGE, failure)
    }

    suspend fun saveDraft(conversationId: String, text: String) {
        require(conversationId.isNotBlank())
        if (text.isEmpty()) {
            dao.deleteDraft(conversationId)
        } else {
            dao.upsertDraft(ConversationDraftEntity(conversationId, text))
        }
    }

    suspend fun clearDraftIfMatches(conversationId: String, expectedText: String) {
        require(conversationId.isNotBlank())
        dao.clearDraftIfMatches(conversationId, expectedText)
    }

    suspend fun enqueue(
        conversationId: String,
        text: String,
        createdAtMillis: Long,
        submittedDraft: String? = null,
    ): ChatMessage {
        require(conversationId.isNotBlank())
        require(text.isNotBlank())
        val entity = MessageEntity(
            conversationId = conversationId,
            clientId = newClientId().also { require(it.isNotBlank()) },
            serverId = null,
            senderId = "me",
            text = text,
            createdAtMillis = createdAtMillis,
            deliveryStatus = DeliveryStatus.SENDING,
        )
        return try {
            val localId = database.withWriteTransaction {
                dao.insert(entity).also {
                    submittedDraft?.let { submitted -> dao.clearDraftIfMatches(conversationId, submitted) }
                }
            }
            entity.copy(localId = localId).asExternalModel()
        } catch (cancelled: CancellationException) {
            // A commit may precede prompt cancellation of the insert's return to its caller.
            withContext(NonCancellable) {
                try {
                    dao.findByClientId(conversationId, requireNotNull(entity.clientId))?.let {
                        dao.markFailed(conversationId, it.localId, ChatError.NETWORK.name)
                    }
                } catch (cleanupFailure: Exception) {
                    cancelled.addSuppressed(cleanupFailure)
                }
            }
            throw cancelled
        }
    }

    /** Writes history and its cursor atomically, including pages containing only duplicates. */
    suspend fun savePage(conversationId: String, page: MessagePage, isInitialRefresh: Boolean = false) {
        require(conversationId.isNotBlank())
        database.withWriteTransaction {
            // In this append-only history, a head page overlapping an already complete cached
            // history connects directly to its known beginning. Do not replay all cached offsets.
            // Client rows alone are insufficient: acknowledgements can exist across a history gap.
            val previous = if (isInitialRefresh) dao.findPagination(conversationId) else null
            val keepsKnownBeginning = previous != null && previous.nextCursor == null &&
                page.messages.any { message ->
                    message.clientId == null && dao.findByServerId(conversationId, message.id) != null
                }
            page.messages.forEach { mergeRemoteMessage(conversationId, it) }
            val nextCursor = if (keepsKnownBeginning) null else page.nextCursor
            dao.upsertPagination(ConversationPaginationEntity(conversationId, nextCursor))
        }
    }

    suspend fun acknowledge(
        conversationId: String,
        localId: Long,
        message: RemoteMessage,
    ) {
        database.withWriteTransaction {
            val pending = requireNotNull(dao.findByLocalId(conversationId, localId))
            if (pending.clientId == null || pending.clientId != message.clientId) {
                throw MessageIdentityConflictException(conversationId, message.id)
            }
            mergeRemoteMessage(conversationId, message)
        }
    }

    /** Reset only downloaded history; retain all user-created outgoing identities and delivery state. */
    suspend fun resetHistoryCache(conversationId: String) {
        require(conversationId.isNotBlank())
        database.withWriteTransaction {
            dao.deleteDownloadedHistory(conversationId)
            dao.deletePagination(conversationId)
        }
    }

    /** A conditional write coalesces simultaneous retry taps without generating another identity. */
    suspend fun beginRetry(conversationId: String, localId: Long): Boolean =
        dao.beginRetry(conversationId, localId) == 1

    suspend fun markFailed(
        conversationId: String,
        localId: Long,
        error: ChatError = ChatError.NETWORK,
    ) {
        dao.markFailed(conversationId, localId, error.name)
    }

    /** Call once at startup, before new sends start; never on screen re-entry. */
    suspend fun recoverInterruptedMessages(): Int = dao.recoverInterruptedMessages()

    /** Adapter-only compatibility for caches written before the fake's ID normalization. */
    internal suspend fun namespaceDemoAcknowledgements() {
        database.withWriteTransaction { dao.namespaceDemoAcknowledgements() }
    }

    override fun close() = database.close()

    private suspend fun mergeRemoteMessage(conversationId: String, message: RemoteMessage) {
        require(message.id.isNotBlank())
        require(message.clientId == null || message.clientId.isNotBlank())
        val byClient = message.clientId?.let { dao.findByClientId(conversationId, it) }
        val byServer = dao.findByServerId(conversationId, message.id)

        listOfNotNull(byClient, byServer).forEach { existing ->
            val conflictingClient = existing.clientId != null && existing.clientId != message.clientId
            val conflictingServer = existing.serverId != null && existing.serverId != message.id
            val conflictingContent = existing.text != message.text || existing.senderId != message.senderId
            if (conflictingClient || conflictingServer || conflictingContent) {
                throw MessageIdentityConflictException(conversationId, message.id)
            }
        }

        // Prefer the optimistic row so acknowledgement/history races retain its stable UI key.
        val existing = byClient ?: byServer
        if (byClient != null && byServer != null && byClient.localId != byServer.localId) {
            dao.delete(conversationId, byServer.localId)
        }

        val merged = MessageEntity(
            localId = existing?.localId ?: 0,
            conversationId = conversationId,
            clientId = message.clientId,
            serverId = message.id,
            senderId = message.senderId,
            text = message.text,
            createdAtMillis = message.createdAtMillis,
            deliveryStatus = DeliveryStatus.SENT,
        )
        if (existing == null) dao.insert(merged) else dao.update(merged)
    }
}
