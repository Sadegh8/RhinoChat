package dev.sadeghtahani.rhinochat.chat.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.Res
import rhinochat.shared.generated.resources.conversation_subtitle
import rhinochat.shared.generated.resources.conversation_title
import rhinochat.shared.generated.resources.team_initial

@Composable
internal fun ChatHeader(modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(if (compact) 36.dp else 52.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.team_initial), style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(Res.string.conversation_title),
                        style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (!compact) Text(
                        stringResource(Res.string.conversation_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(name = "Conversation header", widthDp = 380, showBackground = true)
@Composable
private fun ChatHeaderPreview() {
    RhinoChatTheme { ChatHeader() }
}
