package dev.sadeghtahani.rhinochat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import dev.sadeghtahani.rhinochat.chat.domain.repository.ChatRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** A persisted outbox attempt; WorkManager owns connectivity waits and exponential retries. */
class OutgoingMessageRetryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: ChatRepository by inject()
    private val store: ChatLocalStore by inject()

    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(KEY_CONVERSATION_ID)
            ?.takeIf(String::isNotBlank) ?: return Result.failure()
        val localId = inputData.getLong(KEY_LOCAL_ID, INVALID_LOCAL_ID)
            .takeIf { it >= 0L } ?: return Result.failure()

        val message = try {
            store.findMessage(conversationId, localId)
        } catch (_: ChatDataException) {
            return Result.failure()
        } ?: return Result.success()

        if (message.deliveryStatus == DeliveryStatus.SENT) return Result.success()
        val previousError = try {
            store.findLastDeliveryError(conversationId, localId)
        } catch (_: ChatDataException) {
            return Result.failure()
        }
        if (message.deliveryStatus == DeliveryStatus.FAILED &&
            previousError != null && previousError != ChatError.NETWORK
        ) {
            return Result.failure()
        }
        return when (val result = repository.resumePendingMessage(conversationId, localId)) {
            ChatOperationResult.Completed -> Result.success()
            ChatOperationResult.Ignored -> retryIfStillPending(conversationId, localId)
            is ChatOperationResult.Failed -> when (result.error) {
                ChatError.NETWORK -> Result.retry()
                ChatError.STORAGE, ChatError.IDENTITY_CONFLICT -> Result.failure()
            }
        }
    }

    private suspend fun retryIfStillPending(conversationId: String, localId: Long): Result {
        val current = try {
            store.findMessage(conversationId, localId)
        } catch (_: ChatDataException) {
            return Result.failure()
        } ?: return Result.success()

        val previousError = try {
            store.findLastDeliveryError(conversationId, localId)
        } catch (_: ChatDataException) {
            return Result.failure()
        }
        return when {
            current.deliveryStatus == DeliveryStatus.SENT -> Result.success()
            previousError == null || previousError == ChatError.NETWORK -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_CONVERSATION_ID = "conversation_id"
        const val KEY_LOCAL_ID = "local_id"
        const val INVALID_LOCAL_ID = -1L
    }
}
