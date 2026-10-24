package dev.sadeghtahani.rhinochat.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.theme.RhinoMotion
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
internal fun MessageBubble(
    text: String,
    isOutgoing: Boolean,
    timeLabel: String,
    deliveryStatus: DeliveryStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val failed = isOutgoing && deliveryStatus == DeliveryStatus.FAILED
    val background by animateColorAsState(
        targetValue = when {
            failed -> MaterialTheme.colorScheme.errorContainer
            isOutgoing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = RhinoMotion.effects(),
        label = "Message delivery color",
    )
    val foreground = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        isOutgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        if (isOutgoing) Spacer(Modifier.width(40.dp))
        Surface(
            modifier = Modifier.weight(1f, fill = false).widthIn(max = 480.dp),
            color = background,
            contentColor = foreground,
            shape = RoundedCornerShape(
                topStart = 24.dp, topEnd = 24.dp,
                bottomStart = if (isOutgoing) 24.dp else 8.dp,
                bottomEnd = if (isOutgoing) 8.dp else 24.dp,
            ),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(timeLabel, style = MaterialTheme.typography.labelSmall)
                    if (isOutgoing) {
                        AnimatedContent(
                            targetState = deliveryStatus,
                            transitionSpec = {
                                fadeIn(RhinoMotion.effects()) togetherWith fadeOut(RhinoMotion.effects())
                            },
                            label = "Delivery status",
                        ) { status ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            ) {
                                when (status) {
                                    DeliveryStatus.SENDING -> {
                                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = foreground)
                                        Text(stringResource(Res.string.sending), style = MaterialTheme.typography.labelSmall)
                                    }
                                    DeliveryStatus.SENT -> {
                                        Icon(painterResource(Res.drawable.done_all), null, Modifier.size(16.dp))
                                        Text(stringResource(Res.string.sent), style = MaterialTheme.typography.labelSmall)
                                    }
                                    DeliveryStatus.FAILED -> {
                                        val retryLabel = stringResource(Res.string.retry_message)
                                        TextButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = retryLabel }) {
                                            Icon(painterResource(Res.drawable.refresh), null, Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(Res.string.failed_retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!isOutgoing) Spacer(Modifier.width(40.dp))
    }
}

@Preview(name = "Delivery states", widthDp = 380, showBackground = true)
@Composable
private fun MessageBubblePreview() {
    RhinoChatTheme {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MessageBubble("Welcome to the team!", false, "10:41", DeliveryStatus.SENT, {})
                MessageBubble("Thanks! Happy to be here.", true, "10:42", DeliveryStatus.SENT, {})
                MessageBubble("I’ll share an update shortly.", true, "10:43", DeliveryStatus.SENDING, {})
                MessageBubble("سلام! این پیام برای تلاش دوباره ذخیره شده است.", true, "10:44", DeliveryStatus.FAILED, {})
            }
        }
    }
}
