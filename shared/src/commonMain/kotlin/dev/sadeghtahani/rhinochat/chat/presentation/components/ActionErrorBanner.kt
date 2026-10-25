package dev.sadeghtahani.rhinochat.chat.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.chat.domain.model.ChatError
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
internal fun ActionErrorBanner(error: ChatError, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (error) {
        ChatError.NETWORK -> Res.string.network_error
        ChatError.STORAGE -> Res.string.storage_error
        ChatError.IDENTITY_CONFLICT -> Res.string.identity_error
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(label), style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconButton(onClick = onDismiss) {
                Icon(painterResource(Res.drawable.close), stringResource(Res.string.dismiss_error))
            }
        }
    }
}

@Preview(name = "Action error", widthDp = 380, showBackground = true)
@Composable
private fun ActionErrorPreview() {
    RhinoChatTheme { ActionErrorBanner(ChatError.STORAGE, {}) }
}
