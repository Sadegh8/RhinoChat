package dev.sadeghtahani.rhinochat.chat

import androidx.lifecycle.ViewModelStore
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.HistoryLoadState
import dev.sadeghtahani.rhinochat.chat.data.PaginationState
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
    @Test fun initialLoadStartsOnceAndDraftSurvivesStateRecollection() = withViewModel(collect = false) { vm, repository ->
        val firstCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        vm.onDraftChanged("Retain across rotation")
        runCurrent()
        assertEquals(1, repository.initialCalls)
        firstCollector.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        val secondCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.uiState.collect {}
        }
        runCurrent()
        assertEquals("Retain across rotation", vm.uiState.value.draft)
        assertEquals(1, repository.initialCalls)
        secondCollector.cancel()
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

    private fun withViewModel(
        collect: Boolean = true,
        block: suspend TestScope.(ChatViewModel, FakeRepository) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = ViewModelStore()
        try {
            val repository = FakeRepository()
            val vm = ChatViewModel(repository, "chat")
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
        val messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val pagination = MutableStateFlow(PaginationState())
        var initialCalls = 0
        var olderCalls = 0
        var enqueueCalls = 0
        var sendCalls = 0
        val retriedIds = mutableListOf<Long>()
        var initialResult: ChatOperationResult = ChatOperationResult.Completed
        var olderResult: ChatOperationResult = ChatOperationResult.Completed
        var sendResult: ChatOperationResult = ChatOperationResult.Completed
        var beforeEnqueue: suspend () -> Unit = {}
        var beforeSend: suspend () -> Unit = {}

        override fun observeMessages(conversationId: String) = messages
        override fun observePagination(conversationId: String) = pagination

        override suspend fun enqueueMessage(conversationId: String, text: String): ChatMessage {
            enqueueCalls++
            beforeEnqueue()
            return message(enqueueCalls.toLong(), text).also { messages.value += it }
        }

        override suspend fun sendPendingMessage(conversationId: String, localId: Long): ChatOperationResult {
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
