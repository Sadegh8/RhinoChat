package dev.sadeghtahani.rhinochat.chat

import androidx.compose.runtime.snapshotFlow
import dev.sadeghtahani.rhinochat.chat.data.ChatUiState
import dev.sadeghtahani.rhinochat.chat.data.HistoryLoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

/** Observes both viewport and cursor progress, including when no gesture is active. */
internal fun historyLoadRequests(
    state: () -> ChatUiState,
    totalItemsCount: () -> Int,
    lastVisibleItemIndex: () -> Int,
): Flow<String> = snapshotFlow {
    val current = state()
    val count = totalItemsCount()
    val nearHistory = current.hasMoreHistory && current.olderLoad == HistoryLoadState.Idle &&
        count > 0 && lastVisibleItemIndex() >= count - 3
    // Null participates in distinctness so completing a load can request the next boundary.
    current.nextHistoryCursor.takeIf { nearHistory }
}.distinctUntilChanged().filterNotNull()
