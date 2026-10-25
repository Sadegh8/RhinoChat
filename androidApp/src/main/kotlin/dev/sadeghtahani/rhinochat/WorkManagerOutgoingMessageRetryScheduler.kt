package dev.sadeghtahani.rhinochat

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.sadeghtahani.rhinochat.chat.domain.scheduler.OutgoingMessageRetryScheduler
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.local.ChatLocalStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WorkManagerOutgoingMessageRetryScheduler(
    context: Context,
    private val store: ChatLocalStore,
) : OutgoingMessageRetryScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override suspend fun ensureOutboxRecoveryScheduled() {
        val request = PeriodicWorkRequestBuilder<OutgoingMessageOutboxRepairWorker>(
            OUTBOX_REPAIR_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        val operation = workManager.enqueueUniquePeriodicWork(
            OUTBOX_REPAIR_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
        awaitEnqueue(operation, OUTBOX_REPAIR_WORK_NAME, INVALID_LOCAL_ID, required = true)
    }

    override suspend fun schedule(conversationId: String, localId: Long) {
        enqueue(conversationId, localId, foregroundAttemptDelaySeconds = 10)
    }

    /** Repairs Room rows whose per-message work was missed during a process-death gap. */
    suspend fun schedulePendingMessages() {
        ensureOutboxRecoveryScheduled()
        val pending = store.findRetryableMessages()
        pending.forEach { message -> enqueue(message.conversationId, message.localId, 0) }
    }

    /** Replaces retryable failed sends with a one-minute delay for the hidden process-death probe. */
    suspend fun scheduleFailedMessagesForTesting(): Int {
        val pending = store.findRetryableMessages().filter { it.deliveryStatus == DeliveryStatus.FAILED }
        pending.forEach { message ->
            enqueue(
                message.conversationId,
                message.localId,
                TEST_RETRY_DELAY_SECONDS,
                ExistingWorkPolicy.REPLACE,
            )
        }
        return pending.size
    }

    private suspend fun enqueue(
        conversationId: String,
        localId: Long,
        foregroundAttemptDelaySeconds: Long,
        existingWorkPolicy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val request = OneTimeWorkRequestBuilder<OutgoingMessageRetryWorker>()
            .setInputData(
                workDataOf(
                    OutgoingMessageRetryWorker.KEY_CONVERSATION_ID to conversationId,
                    OutgoingMessageRetryWorker.KEY_LOCAL_ID to localId,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            // Keep the ViewModel's immediate attempt in front; the worker covers retries and
            // process death if that attempt cannot finish successfully.
            .setInitialDelay(foregroundAttemptDelaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        val operation = workManager.enqueueUniqueWork(
            uniqueWorkName(conversationId, localId),
            existingWorkPolicy,
            request,
        )
        awaitEnqueue(operation, conversationId, localId)
    }

    private suspend fun awaitEnqueue(
        operation: Operation,
        conversationId: String,
        localId: Long,
        required: Boolean = false,
    ) {
        try {
            withContext(Dispatchers.IO) { operation.result.get() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw CancellationException("Waiting for retry work scheduling was interrupted", interrupted)
        } catch (failure: Exception) {
            if (required) throw ChatDataException(ChatError.STORAGE, failure)
            Log.w(TAG, "Could not schedule retry for $conversationId/$localId", failure)
        }
    }

    companion object {
        const val OUTBOX_REPAIR_WORK_NAME = "outgoing-message-outbox-repair"
        private const val OUTBOX_REPAIR_INTERVAL_MINUTES = 15L
        private const val TAG = "OutgoingRetry"
        private const val BACKOFF_MILLIS = 30_000L
        private const val TEST_RETRY_DELAY_SECONDS = 60L
        private const val INVALID_LOCAL_ID = -1L
    }

    private fun uniqueWorkName(conversationId: String, localId: Long) =
        "outgoing-message:$conversationId:$localId"
}
