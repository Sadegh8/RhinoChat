package dev.sadeghtahani.rhinochat.chat.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.theme.RhinoMotion
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
internal fun MessageComposer(
    text: String,
    isEnqueueing: Boolean,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    maxLines: Int = 5,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, RhinoMotion.fastSpatial(), label = "Send press")
    val corner by animateDpAsState(if (pressed) 18.dp else 28.dp, RhinoMotion.fastSpatial(), label = "Send shape")
    val fieldLabel = stringResource(Res.string.message_hint)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = text,
            onValueChange = onTextChanged,
            modifier = Modifier.weight(1f).semantics { contentDescription = fieldLabel },
            placeholder = { Text(fieldLabel) },
            shape = RoundedCornerShape(28.dp),
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isEnqueueing,
            modifier = Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(corner),
            interactionSource = interaction,
        ) {
            if (isEnqueueing) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(painterResource(Res.drawable.arrow_upward), stringResource(Res.string.send_message))
            }
        }
    }
}

@Preview(name = "Composer · Ready", widthDp = 380, showBackground = true)
@Composable
private fun MessageComposerPreview() {
    RhinoChatTheme { Surface { MessageComposer("Looking forward to it!", false, {}, {}) } }
}

@Preview(name = "Composer · Empty · Dark", widthDp = 380, showBackground = true)
@Composable
private fun EmptyComposerPreview() {
    RhinoChatTheme(darkTheme = true) { Surface { MessageComposer("", false, {}, {}) } }
}
