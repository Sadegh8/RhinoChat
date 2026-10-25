package dev.sadeghtahani.rhinochat.chat.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.presentation.model.ChatUiState
import dev.sadeghtahani.rhinochat.chat.presentation.model.HistoryLoadState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChatHistoryLoadingTest {
    @Test fun completingLoadBeforeRoomPublishesCursorDoesNotRequestTheOldPageAgain() = runTest {
        var state by mutableStateOf(ready("100"))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { 121 }, { 120 }).collect(requests::add)
        }
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Loading) }
        runCurrent()
        // The operation completes before Room's independent observation emits its new cursor.
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Idle) }
        runCurrent()
        assertEquals(listOf("100"), requests)
        Snapshot.withMutableSnapshot { state = ready(null).copy(hasMoreHistory = false) }
        runCurrent()
        assertEquals(listOf("100"), requests)
    }

    @Test fun cursorPublishedDuringLoadIsRequestedOnceAfterCompletion() = runTest {
        var state by mutableStateOf(ready("20"))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { 21 }, { 20 }).collect(requests::add)
        }
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Loading) }
        runCurrent()
        Snapshot.withMutableSnapshot { state = state.copy(nextHistoryCursor = "40") }
        runCurrent()
        assertEquals(listOf("20"), requests)
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Idle) }
        runCurrent()
        assertEquals(listOf("20", "40"), requests)
    }

    @Test fun stationaryBoundaryRequestsNextCursorEvenWhenTheMessageListDoesNotChange() = runTest {
        var state by mutableStateOf(ready("20"))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { 21 }, { 20 }).collect(requests::add)
        }
        assertEquals(listOf("20"), requests)
        Snapshot.withMutableSnapshot { state = ready("40") }
        runCurrent()
        assertEquals(listOf("20", "40"), requests)
        Snapshot.withMutableSnapshot { state = ready("60") }
        runCurrent()
        assertEquals(listOf("20", "40", "60"), requests)
    }

    @Test fun failedPagePausesAutomaticLoadingUntilExplicitRetryCompletes() = runTest {
        var state by mutableStateOf(ready("20"))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { 21 }, { 20 }).collect(requests::add)
        }
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Failed(ChatError.NETWORK)) }
        runCurrent()
        Snapshot.withMutableSnapshot { state = state.copy(draft = "Typing is not a retry") }
        runCurrent()
        assertEquals(listOf("20"), requests)
        Snapshot.withMutableSnapshot { state = state.copy(olderLoad = HistoryLoadState.Loading) }
        runCurrent()
        assertEquals(listOf("20"), requests)
        Snapshot.withMutableSnapshot { state = ready("40") }
        runCurrent()
        assertEquals(listOf("20", "40"), requests)
    }

    @Test fun leavingAndReturningToTheBoundaryCanRequestTheSameCursorAgain() = runTest {
        var lastVisible by mutableIntStateOf(5)
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ ready("20") }, { 21 }, { lastVisible }).collect(requests::add)
        }
        assertEquals(emptyList(), requests)
        Snapshot.withMutableSnapshot { lastVisible = 18 }
        runCurrent()
        assertEquals(listOf("20"), requests)
        Snapshot.withMutableSnapshot { lastVisible = 5 }
        runCurrent()
        Snapshot.withMutableSnapshot { lastVisible = 20 }
        runCurrent()
        assertEquals(listOf("20", "20"), requests)
    }

    @Test fun unrelatedUiChangesDoNotRepeatTheSameBoundaryRequest() = runTest {
        var state by mutableStateOf(ready("20"))
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { 21 }, { 20 }).collect(requests::add)
        }
        Snapshot.withMutableSnapshot { state = state.copy(draft = "A new draft", actionError = ChatError.STORAGE) }
        runCurrent()
        assertEquals(listOf("20"), requests)
    }

    @Test fun emptyUninitializedAndExhaustedHistoryDoNotRequestPages() = runTest {
        var state by mutableStateOf(ChatUiState())
        var count by mutableIntStateOf(0)
        val requests = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            historyLoadRequests({ state }, { count }, { 20 }).collect(requests::add)
        }
        Snapshot.withMutableSnapshot { count = 21 }
        runCurrent()
        Snapshot.withMutableSnapshot { state = ready(null).copy(hasMoreHistory = false) }
        runCurrent()
        Snapshot.withMutableSnapshot { state = ready("20"); count = 0 }
        runCurrent()
        assertEquals(emptyList(), requests)
    }

    private fun ready(cursor: String?) = ChatUiState(
        initialLoad = HistoryLoadState.Idle,
        hasMoreHistory = true,
        nextHistoryCursor = cursor,
    )
}
