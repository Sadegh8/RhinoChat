package dev.sadeghtahani.rhinochat.chat.presentation

import dev.sadeghtahani.rhinochat.chat.domain.scheduler.OutgoingMessageRetryScheduler

import androidx.lifecycle.ViewModelStore
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatDataException
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatMessage
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.domain.model.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.presentation.model.HistoryLoadState
import dev.sadeghtahani.rhinochat.chat.domain.model.PaginationState
import dev.sadeghtahani.rhinochat.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test fun activeLoadAndSendSurviveUiRecollectionWithoutRepeatingCommands() {
        val historyResponse = CompletableDeferred<Unit>()
        val sendResponse = CompletableDeferred<Unit>()
        withViewModel(collect = false, prepare = { repository ->
            repository.beforeInitial = { historyResponse.await() }
            repository.beforeSend = { sendResponse.await() }
        }) { vm, repository ->
            val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            vm.onDraftChanged("Send while history waits")
            vm.sendMessage()
            runCurrent()
            vm.onDraftChanged("Keep the next draft")
            firstCollector.cancel()
            advanceTimeBy(5_001)
            runCurrent()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            runCurrent()
            assertEquals(1, repository.initialCalls)
            assertEquals(1, repository.sendCalls)
            assertEquals(HistoryLoadState.Loading, vm.uiState.value.initialLoad)
            assertEquals(DeliveryStatus.SENDING, vm.uiState.value.messages.single().deliveryStatus)
            assertEquals("Keep the next draft", vm.uiState.value.draft)
            historyResponse.complete(Unit)
            sendResponse.complete(Unit)
            runCurrent()
            assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
            assertEquals(DeliveryStatus.SENT, vm.uiState.value.messages.single().deliveryStatus)
            assertEquals("Keep the next draft", vm.uiState.value.draft)
        }
    }

    @Test fun initialAndOlderFailuresPreserveCachedMessagesAndHaveSeparateRetryState() = withViewModel { vm, repository ->
        repository.messages.value = listOf(message())
        repository.initialResult = ChatOperationResult.Failed(ChatError.NETWORK)
        vm.loadInitialMessages()
        runCurrent()
        assertEquals(HistoryLoadState.Failed(ChatError.NETWORK), vm.uiState.value.initialLoad)
        assertEquals(listOf(message()), vm.uiState.value.messages)
        repository.initialResult = ChatOperationResult.Completed
        vm.loadInitialMessages()
        runCurrent()
        repository.pagination.value = PaginationState(true, "20")
        repository.olderResult = ChatOperationResult.Failed(ChatError.NETWORK)
        vm.loadOlderMessages()
        vm.loadOlderMessages()
        runCurrent()
        assertEquals(1, repository.olderCalls)
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
        assertEquals(HistoryLoadState.Failed(ChatError.NETWORK), vm.uiState.value.olderLoad)
        assertEquals(listOf(message()), vm.uiState.value.messages)
        assertTrue(vm.uiState.value.hasMoreHistory)
        repository.olderResult = ChatOperationResult.Completed
        vm.loadOlderMessages()
        runCurrent()
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.olderLoad)
    }

    @Test fun enqueueIsCoalescedAndDoesNotClearANewerDraftEvenWithIdenticalText() = withViewModel { vm, repository ->
        val localWrite = CompletableDeferred<Unit>()
        repository.beforeEnqueue = { localWrite.await() }
        vm.onDraftChanged("Hello")
        vm.sendMessage()
        vm.sendMessage()
        runCurrent()
        assertTrue(vm.uiState.value.isEnqueueing)
        assertEquals(1, repository.enqueueCalls)
        assertEquals(0, repository.sendCalls)
        vm.onDraftChanged("")
        vm.onDraftChanged("Hello") // Same value, but a new edit made after the submission.
        localWrite.complete(Unit)
        runCurrent()
        assertEquals("Hello", vm.uiState.value.draft)
        assertFalse(vm.uiState.value.isEnqueueing)
        assertEquals(1, repository.sendCalls)
        assertEquals(1, vm.uiState.value.messages.size)
    }

    @Test fun successfulLocalWriteClearsSubmittedDraftAndAllowsAnotherSendWhileNetworkWaits() = withViewModel { vm, repository ->
        val response = CompletableDeferred<Unit>()
        repository.beforeSend = { response.await() }
        vm.onDraftChanged("First")
        vm.sendMessage()
        runCurrent()
        assertEquals("", vm.uiState.value.draft)
        assertFalse(vm.uiState.value.isEnqueueing)
        vm.onDraftChanged("Second")
        vm.sendMessage()
        runCurrent()
        assertEquals(2, repository.sendCalls)
        assertEquals(listOf("First", "Second"), vm.uiState.value.messages.map { it.text })
        response.complete(Unit)
        runCurrent()
    }

    @Test fun failedLocalWriteKeepsDraftAndNeverCallsNetwork() = withViewModel { vm, repository ->
        repository.beforeEnqueue = { throw ChatDataException(ChatError.STORAGE) }
        vm.onDraftChanged("Do not lose this")
        vm.sendMessage()
        runCurrent()
        assertEquals("Do not lose this", vm.uiState.value.draft)
        assertEquals(ChatError.STORAGE, vm.uiState.value.actionError)
        assertEquals(0, repository.sendCalls)
        assertFalse(vm.uiState.value.isEnqueueing)
    }

    @Test fun durableOutboxRecoveryIsInstalledBeforeTheMessageIsPersisted() = withViewModel { vm, repository ->
        vm.onDraftChanged("Recover me after process death")
        vm.sendMessage()
        runCurrent()

        assertEquals(listOf("recovery", "enqueue", "schedule", "send"), repository.commandOrder)
    }

    @Test fun retryUsesExistingRowAndDatabaseDeliveryStateWithoutCreatingAnotherMessage() = withViewModel { vm, repository ->
        repository.messages.value = listOf(message().copy(deliveryStatus = DeliveryStatus.FAILED))
        vm.retryMessage(1)
        runCurrent()
        assertEquals(listOf(1L), repository.retriedIds)
        assertEquals(0, repository.enqueueCalls)
        assertEquals(DeliveryStatus.SENT, vm.uiState.value.messages.single().deliveryStatus)
    }

    @Test fun blankSendIsIgnoredAndNetworkFailureIsRepresentedOnThePersistedBubble() = withViewModel { vm, repository ->
        vm.onDraftChanged("  \n")
        vm.sendMessage()
        runCurrent()
        assertEquals(0, repository.enqueueCalls)
        repository.sendResult = ChatOperationResult.Failed(ChatError.NETWORK)
        vm.onDraftChanged("Hello")
        vm.sendMessage()
        runCurrent()
        assertEquals(DeliveryStatus.FAILED, vm.uiState.value.messages.single().deliveryStatus)
        assertEquals(null, vm.uiState.value.actionError)
    }

    @Test fun observationFailureRetainsCacheAndKeepsDraftAndActionErrorsLiveUntilExplicitRetry() = withViewModel { vm, repository ->
        val cached = message()
        repository.messages.value = listOf(cached)
        repository.pagination.value = PaginationState(true, "20")
        vm.onDraftChanged("Before failure")
        runCurrent()
        repository.messageObservationError.value = ChatError.STORAGE
        runCurrent()
        assertEquals(listOf(cached), vm.uiState.value.messages)
        assertEquals(HistoryLoadState.Failed(ChatError.STORAGE), vm.uiState.value.initialLoad)
        assertFalse(vm.uiState.value.hasMoreHistory)

        vm.onDraftChanged("Still editable")
        repository.beforeEnqueue = { throw ChatDataException(ChatError.STORAGE) }
        vm.sendMessage()
        runCurrent()
        assertEquals("Still editable", vm.uiState.value.draft)
        assertEquals(ChatError.STORAGE, vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.isEnqueueing)
        vm.dismissActionError()
        runCurrent()
        assertEquals(null, vm.uiState.value.actionError)

        repository.messageObservationError.value = null
        val updated = cached.copy(text = "Cache is readable again")
        repository.messages.value = listOf(updated)
        runCurrent()
        assertEquals(listOf(cached), vm.uiState.value.messages)
        assertEquals(1, repository.messageSubscriptions)
        vm.loadInitialMessages()
        runCurrent()
        assertEquals(2, repository.messageSubscriptions)
        assertEquals(listOf(updated), vm.uiState.value.messages)
        assertEquals("Still editable", vm.uiState.value.draft)
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
        assertTrue(vm.uiState.value.hasMoreHistory)
        repository.beforeEnqueue = {}
        vm.sendMessage()
        runCurrent()
        assertEquals("", vm.uiState.value.draft)
        assertEquals("Still editable", vm.uiState.value.messages.last().text)
        assertEquals(DeliveryStatus.SENT, vm.uiState.value.messages.last().deliveryStatus)
    }

    @Test fun firstObservationFailureCanRecoverWithoutRecreatingTheViewModel() = withViewModel(
        prepare = { it.messageObservationError.value = ChatError.STORAGE },
    ) { vm, repository ->
        assertEquals(HistoryLoadState.Failed(ChatError.STORAGE), vm.uiState.value.initialLoad)
        vm.onDraftChanged("Keep this draft")
        repository.messageObservationError.value = null
        repository.messages.value = listOf(message())
        vm.loadInitialMessages()
        runCurrent()
        assertEquals("Keep this draft", vm.uiState.value.draft)
        assertEquals(listOf(message()), vm.uiState.value.messages)
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
    }

    @Test fun paginationObservationFailureRetainsMessagesAndRestartsBothObservations() = withViewModel { vm, repository ->
        repository.messages.value = listOf(message())
        runCurrent()
        repository.paginationObservationError.value = ChatError.STORAGE
        runCurrent()
        assertEquals(listOf(message()), vm.uiState.value.messages)
        assertEquals(HistoryLoadState.Failed(ChatError.STORAGE), vm.uiState.value.initialLoad)
        repository.paginationObservationError.value = null
        repository.pagination.value = PaginationState(true, "40")
        vm.loadInitialMessages()
        runCurrent()
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
        assertEquals("40", vm.uiState.value.nextHistoryCursor)
        assertEquals(2, repository.messageSubscriptions)
        assertEquals(2, repository.paginationSubscriptions)
    }

    @Test fun observationRetryDuringAnActiveHistoryRequestDoesNotRepeatTheRequest() = withViewModel { vm, repository ->
        val response = CompletableDeferred<Unit>()
        repository.beforeInitial = { response.await() }
        vm.loadInitialMessages()
        runCurrent()
        repository.messageObservationError.value = ChatError.STORAGE
        runCurrent()
        repository.messageObservationError.value = null
        vm.loadInitialMessages()
        runCurrent()
        assertEquals(2, repository.initialCalls) // Startup plus the one active request.
        assertEquals(2, repository.messageSubscriptions)
        assertEquals(HistoryLoadState.Loading, vm.uiState.value.initialLoad)
        response.complete(Unit)
        runCurrent()
        assertEquals(HistoryLoadState.Idle, vm.uiState.value.initialLoad)
    }

    private fun withViewModel(
        collect: Boolean = true,
        prepare: (FakeRepository) -> Unit = {},
        block: suspend TestScope.(ChatViewModel, FakeRepository) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val repository = FakeRepository().also(prepare)
            val retryScheduler = object : OutgoingMessageRetryScheduler {
                override suspend fun ensureOutboxRecoveryScheduled() {
                    repository.commandOrder += "recovery"
                }

                override suspend fun schedule(conversationId: String, localId: Long) {
                    repository.commandOrder += "schedule"
                }
            }
            val vm = ChatViewModel(repository, "chat", retryScheduler)
            owner.put("chat", vm)
            if (collect) {
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
            }
            runCurrent()
            block(vm, repository)
        } finally {
            owner.clear()
            // Finish StateFlow's cancellation before restoring the platform Main dispatcher.
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository : ChatRepository {
        override suspend fun resumePendingMessage(conversationId: String, localId: Long): ChatOperationResult =
            error("Background recovery is exercised by repository and worker tests")
        override suspend fun resetHistoryCache(conversationId: String): ChatOperationResult =
            error("Cache reset is exercised by repository and device tests")
        val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val pagination = MutableStateFlow(PaginationState())
        val messageObservationError = MutableStateFlow<ChatError?>(null)
        val paginationObservationError = MutableStateFlow<ChatError?>(null)
        var messageSubscriptions = 0
        var paginationSubscriptions = 0
        var initialCalls = 0
        var olderCalls = 0
        var enqueueCalls = 0
        var sendCalls = 0
        val commandOrder = mutableListOf<String>()
        val retriedIds = mutableListOf<Long>()
        var initialResult: ChatOperationResult = ChatOperationResult.Completed
        var olderResult: ChatOperationResult = ChatOperationResult.Completed
        var sendResult: ChatOperationResult = ChatOperationResult.Completed
        var beforeEnqueue: suspend () -> Unit = {}
        var beforeSend: suspend () -> Unit = {}
        var beforeInitial: suspend () -> Unit = {}

        override fun observeMessages(conversationId: String) = combine(messages, messageObservationError) { value, error ->
            error?.let { throw ChatDataException(it) }
            value
        }.onStart { messageSubscriptions++ }

        override fun observePagination(conversationId: String) = combine(pagination, paginationObservationError) { value, error ->
            error?.let { throw ChatDataException(it) }
            value
        }.onStart { paginationSubscriptions++ }

        override suspend fun enqueueMessage(conversationId: String, text: String): ChatMessage {
            commandOrder += "enqueue"
            enqueueCalls++
            beforeEnqueue()
            return message(enqueueCalls.toLong(), text).also { messages.value += it }
        }

        override suspend fun sendPendingMessage(conversationId: String, localId: Long): ChatOperationResult {
            commandOrder += "send"
            sendCalls++
            beforeSend()
            val status = if (sendResult is ChatOperationResult.Failed) DeliveryStatus.FAILED else DeliveryStatus.SENT
            messages.value = messages.value.map { if (it.localId == localId) it.copy(deliveryStatus = status) else it }
            return sendResult
        }

        override suspend fun retryMessage(conversationId: String, localId: Long): ChatOperationResult {
            retriedIds += localId
            return sendPendingMessage(conversationId, localId)
        }

        override suspend fun loadInitialMessages(conversationId: String): ChatOperationResult {
            initialCalls++
            beforeInitial()
            return initialResult
        }

        override suspend fun loadOlderMessages(conversationId: String): ChatOperationResult {
            olderCalls++
            return olderResult
        }
    }

    private companion object {
        fun message(id: Long = 1, text: String = "Hello") =
            ChatMessage(id, "chat", "client-$id", null, "me", text, 100, DeliveryStatus.SENDING)
    }
}
