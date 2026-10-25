package dev.sadeghtahani.rhinochat.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.chat.presentation.model.HistoryLoadState
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
internal fun HistoryStatus(
    state: HistoryLoadState,
    hasMore: Boolean,
    isInitial: Boolean,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var reservedHeightPx by remember { mutableIntStateOf(0) }
    Column(
        // Keep a visible error/retry slot from collapsing into a small spinner. At the oldest
        // edge, a shorter list would clamp the scroll offset before the page can extend it.
        modifier = modifier.onSizeChanged { reservedHeightPx = maxOf(reservedHeightPx, it.height) }
            .heightIn(min = with(density) { reservedHeightPx.toDp() }).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            HistoryLoadState.Loading -> Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(if (isInitial) Res.string.loading_conversation else Res.string.loading_earlier),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            is HistoryLoadState.Failed -> {
                val label = when (state.error) {
                    ChatError.IDENTITY_CONFLICT -> Res.string.identity_error
                    ChatError.STORAGE -> Res.string.history_storage_error
                    ChatError.NETWORK -> if (isInitial) Res.string.load_failed else Res.string.older_failed
                }
                Text(
                    stringResource(label), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                TextButton(onClick = onLoad) { Text(stringResource(Res.string.try_again)) }
            }
            HistoryLoadState.Idle -> if (hasMore) {
                OutlinedButton(onClick = onLoad) { Text(stringResource(Res.string.load_earlier)) }
            } else {
                Text(
                    stringResource(Res.string.history_start), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "History · Error", widthDp = 360, showBackground = true)
@Composable
private fun HistoryStatusPreview() {
    RhinoChatTheme {
        Surface { HistoryStatus(HistoryLoadState.Failed(ChatError.NETWORK), true, false, {}) }
    }
}
