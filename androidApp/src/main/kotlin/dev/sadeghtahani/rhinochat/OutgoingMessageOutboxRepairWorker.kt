package dev.sadeghtahani.rhinochat

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Periodically repairs any persisted outgoing row missed by its one-time work enqueue. */
class OutgoingMessageOutboxRepairWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val retryScheduler: WorkManagerOutgoingMessageRetryScheduler by inject()

    override suspend fun doWork(): Result = try {
        retryScheduler.schedulePendingMessages()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: ChatDataException) {
        Result.retry()
    }
}
