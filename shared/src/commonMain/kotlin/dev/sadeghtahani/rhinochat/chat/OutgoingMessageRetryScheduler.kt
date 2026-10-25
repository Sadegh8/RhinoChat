package dev.sadeghtahani.rhinochat.chat

/** Android persists these requests with WorkManager; other hosts can provide their own scheduler. */
fun interface OutgoingMessageRetryScheduler {
    /** Must be durable before a new outgoing row is committed. */
    suspend fun ensureOutboxRecoveryScheduled() = Unit

    suspend fun schedule(conversationId: String, localId: Long)
}

object NoopOutgoingMessageRetryScheduler : OutgoingMessageRetryScheduler {
    override suspend fun schedule(conversationId: String, localId: Long) = Unit
}
