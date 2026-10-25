package dev.sadeghtahani.rhinochat.chat.presentation

import androidx.compose.runtime.snapshotFlow
import dev.sadeghtahani.rhinochat.chat.presentation.model.ChatUiState
import dev.sadeghtahani.rhinochat.chat.presentation.model.HistoryLoadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/** Observes both viewport and cursor progress, including when no gesture is active. */
internal fun historyLoadRequests(
    state: () -> ChatUiState,
    totalItemsCount: () -> Int,
    lastVisibleItemIndex: () -> Int,
): Flow<String> = snapshotFlow {
    val current = state()
    val count = totalItemsCount()
    // Room can publish a page before LazyColumn measures its new items. Its old viewport
    // must not trigger pagination against the new cursor. One extra item is the history row.
    if (count != current.messages.size + 1) return@snapshotFlow null
    val nearHistory = current.hasMoreHistory &&
        count > 0 && lastVisibleItemIndex() >= count - 3
    current.nextHistoryCursor.takeIf { nearHistory } to current.olderLoad
    // Room's cursor and operation completion arrive independently. Loading/failed states must
    // not reset cursor distinctness, otherwise Idle can immediately request the old page again.
}.filterNotNull()
    .filter { (cursor, load) -> cursor == null || load == HistoryLoadState.Idle }
    .map { (cursor, _) -> cursor }
    .distinctUntilChanged()
    .filterNotNull()
