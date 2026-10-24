package dev.sadeghtahani.rhinochat.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sadeghtahani.rhinochat.chat.components.ActionErrorBanner
import dev.sadeghtahani.rhinochat.chat.components.AdaptiveChatCanvas
import dev.sadeghtahani.rhinochat.chat.components.ChatHeader
import dev.sadeghtahani.rhinochat.chat.components.HistoryStatus
import dev.sadeghtahani.rhinochat.chat.components.MessageBubble
import dev.sadeghtahani.rhinochat.chat.components.MessageComposer
import dev.sadeghtahani.rhinochat.chat.data.ChatError
import dev.sadeghtahani.rhinochat.chat.data.ChatMessage
import dev.sadeghtahani.rhinochat.chat.data.ChatUiState
import dev.sadeghtahani.rhinochat.chat.data.DeliveryStatus
import dev.sadeghtahani.rhinochat.chat.data.HistoryLoadState
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.theme.RhinoMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import rhinochat.shared.generated.resources.*

@Composable
fun ChatScreen(
    state: ChatUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onRetryMessage: (Long) -> Unit,
    onLoadInitial: () -> Unit,
    onLoadOlder: () -> Unit,
    onDismissError: () -> Unit,
    posture: Posture = Posture(),
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val awayFromLatest by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val nearLatestOffset = with(LocalDensity.current) { 32.dp.roundToPx() }
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()
    val latestState by rememberUpdatedState(state)
    val loadOlder by rememberUpdatedState(onLoadOlder)
    val newestId = state.messages.lastOrNull()?.localId
    val newestFirst = remember(state.messages) { state.messages.asReversed() }
    var previousNewestId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(newestId) {
        val firstVisibleKey = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == listState.firstVisibleItemIndex }?.key
        // Keys preserve the anchor even when a new item shifts the old newest row to index 1.
        val wasAtLatest = listState.firstVisibleItemIndex == 0 || firstVisibleKey == previousNewestId
        if (previousNewestId != null && newestId != previousNewestId && wasAtLatest &&
            listState.firstVisibleItemScrollOffset < nearLatestOffset && !listState.isScrollInProgress
        ) {
            listState.animateScrollToItem(0)
        }
        previousNewestId = newestId
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            latestState.hasMoreHistory && latestState.olderLoad == HistoryLoadState.Idle &&
                listState.isScrollInProgress && layout.totalItemsCount > 0 &&
                (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 3
        }.distinctUntilChanged().collect { nearHistory ->
            if (nearHistory) {
                loadOlder()
            }
        }
    }

    AdaptiveChatCanvas(posture, modifier) { layout ->
        val density = LocalDensity.current
        val historyHeight = layout.historyBottomPx?.let { with(density) { it.toDp() } }
        val inputMaxHeight = layout.inputMaxHeightPx?.let { with(density) { it.toDp() } }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (layout.showHeader) ChatHeader(compact = layout.compactHeader)
                        if (layout.showHeader && state.messages.isNotEmpty() && state.initialLoad != HistoryLoadState.Idle) {
                            HistoryStatus(state.initialLoad, false, true, onLoadInitial, Modifier.widthIn(max = 840.dp))
                        }
                    }
                }
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.extraLarge) {
                    Box(
                        Modifier.fillMaxWidth().then(
                            if (inputMaxHeight != null) Modifier.heightIn(max = inputMaxHeight).verticalScroll(rememberScrollState())
                            else Modifier,
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                            AnimatedVisibility(visible = state.actionError != null, enter = fadeIn(), exit = fadeOut()) {
                                state.actionError?.let { ActionErrorBanner(it, onDismissError) }
                            }
                            MessageComposer(
                                state.draft, state.isEnqueueing, onDraftChanged, onSend,
                                maxLines = layout.composerMaxLines, compact = layout.compactHeader,
                            )
                        }
                    }
                }
            },
        ) { insets ->
            val bottomPadding = if (historyHeight == null) insets.calculateBottomPadding() else 0.dp
            Box(
                Modifier.fillMaxWidth().then(if (historyHeight != null) Modifier.height(historyHeight) else Modifier.fillMaxHeight())
                    .consumeWindowInsets(insets),
                contentAlignment = Alignment.Center,
            ) {
                if (state.messages.isEmpty()) {
                    Column(
                        Modifier.padding(top = insets.calculateTopPadding(), bottom = bottomPadding).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.initialLoad == HistoryLoadState.Idle) {
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                                    Icon(painterResource(Res.drawable.chat_bubble), null, Modifier.size(36.dp))
                                }
                            }
                            Text(stringResource(Res.string.empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Text(
                                stringResource(Res.string.empty_body), style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                        } else {
                            HistoryStatus(state.initialLoad, false, true, onLoadInitial)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                        state = listState,
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
                        contentPadding = PaddingValues(
                            start = insets.calculateStartPadding(layoutDirection) + 16.dp,
                            end = insets.calculateEndPadding(layoutDirection) + 16.dp,
                            top = insets.calculateTopPadding() + 12.dp,
                            bottom = bottomPadding + 12.dp,
                        ),
                    ) {
                        itemsIndexed(newestFirst, key = { _, message -> message.localId }, contentType = { _, _ -> "message" }) { index, message ->
                            val timestamp = remember(message.createdAtMillis) { formatMessageTimestamp(message.createdAtMillis) }
                            val olderMessage = newestFirst.getOrNull(index + 1)
                            val olderDate = remember(olderMessage?.createdAtMillis) {
                                olderMessage?.let { formatMessageTimestamp(it.createdAtMillis).date }
                            }
                            Column(Modifier.animateItem(
                                fadeInSpec = RhinoMotion.effects(), placementSpec = RhinoMotion.spatial(), fadeOutSpec = RhinoMotion.effects(),
                            )) {
                                if (timestamp.date != olderDate) {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
                                            Text(
                                                timestamp.date, style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).semantics { heading() },
                                            )
                                        }
                                    }
                                }
                                MessageBubble(
                                    text = message.text, isOutgoing = message.senderId == "me",
                                    timeLabel = timestamp.time, deliveryStatus = message.deliveryStatus,
                                    onRetry = { onRetryMessage(message.localId) },
                                )
                            }
                        }
                        item(key = "history", contentType = "history") {
                            if (state.initialLoad == HistoryLoadState.Idle || !layout.showHeader) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                HistoryStatus(
                                    state = if (state.initialLoad == HistoryLoadState.Idle) state.olderLoad else state.initialLoad,
                                    hasMore = state.hasMoreHistory, isInitial = state.initialLoad != HistoryLoadState.Idle,
                                    onLoad = if (state.initialLoad == HistoryLoadState.Idle) onLoadOlder else onLoadInitial,
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = awayFromLatest && state.messages.isNotEmpty(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding + 12.dp),
                    enter = fadeIn(RhinoMotion.effects()) + slideInVertically(RhinoMotion.spatial()) { it / 2 },
                    exit = fadeOut(RhinoMotion.effects()) + slideOutVertically(RhinoMotion.spatial()) { it / 2 },
                ) {
                    FilledTonalButton(onClick = { scope.launch { listState.animateScrollToItem(0) } }) {
                        Icon(painterResource(Res.drawable.arrow_downward), null, Modifier.size(18.dp))
                        Text(stringResource(Res.string.latest_messages), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatRoute(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    ChatScreen(
        state = state, onDraftChanged = viewModel::onDraftChanged, onSend = viewModel::sendMessage,
        onRetryMessage = viewModel::retryMessage, onLoadInitial = viewModel::loadInitialMessages,
        onLoadOlder = viewModel::loadOlderMessages, onDismissError = viewModel::dismissActionError,
        posture = adaptiveInfo.windowPosture,
    )
}

@Preview(name = "Chat · Light", widthDp = 393, heightDp = 820, showBackground = true)
@Composable
private fun ChatLightPreview() = ChatPreview(previewState())

@Preview(name = "Chat · Dark", widthDp = 393, heightDp = 820, showBackground = true)
@Composable
private fun ChatDarkPreview() = ChatPreview(previewState(), dark = true)

@Preview(name = "Chat · Cached error", widthDp = 393, heightDp = 820, showBackground = true)
@Composable
private fun ChatErrorPreview() = ChatPreview(previewState().copy(
    initialLoad = HistoryLoadState.Failed(ChatError.NETWORK), hasMoreHistory = false,
    actionError = ChatError.STORAGE,
))

@Preview(name = "Chat · Loading", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun ChatLoadingPreview() = ChatPreview(ChatUiState())

@Preview(name = "Chat · Empty", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
private fun ChatEmptyPreview() = ChatPreview(ChatUiState(initialLoad = HistoryLoadState.Idle))

@Preview(name = "Chat · RTL · Large text", widthDp = 393, heightDp = 820, locale = "fa", showBackground = true)
@Composable
private fun ChatRtlPreview() {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.4f),
    ) { ChatPreview(previewState()) }
}

@Preview(name = "Chat · Tablet landscape", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun ChatWidePreview() = ChatPreview(previewState())

@Preview(name = "Chat · Phone landscape", widthDp = 840, heightDp = 393, showBackground = true)
@Composable
private fun ChatLandscapePreview() = ChatPreview(previewState())

@Preview(name = "Chat · Short keyboard viewport", widthDp = 840, heightDp = 180, showBackground = true)
@Composable
private fun ChatShortViewportPreview() = ChatPreview(previewState())

@Preview(name = "Chat · Tablet portrait", widthDp = 800, heightDp = 1280, showBackground = true)
@Composable
private fun ChatTabletPreview() = ChatPreview(previewState())

@Preview(name = "Chat · Book fold", widthDp = 840, heightDp = 720, showBackground = true)
@Composable
private fun ChatBookPreview() {
    val density = LocalDensity.current.density
    ChatPreview(previewState(), posture = Posture(hingeList = listOf(HingeInfo(
        Rect(414f * density, 0f, 426f * density, 720f * density),
        isFlat = false, isVertical = true, isSeparating = true, isOccluding = true,
    ))))
}

@Preview(name = "Chat · Tabletop fold", widthDp = 840, heightDp = 900, showBackground = true)
@Composable
private fun ChatTabletopPreview() {
    val density = LocalDensity.current.density
    ChatPreview(previewState(), posture = Posture(isTabletop = true, hingeList = listOf(HingeInfo(
        Rect(0f, 444f * density, 840f * density, 456f * density),
        isFlat = false, isVertical = false, isSeparating = true, isOccluding = true,
    ))))
}

@Composable
private fun ChatPreview(state: ChatUiState, dark: Boolean = false, posture: Posture = Posture()) {
    RhinoChatTheme(darkTheme = dark) { ChatScreen(state, {}, {}, {}, {}, {}, {}, posture) }
}

private fun previewState(): ChatUiState {
    val texts = listOf(
        "Welcome to the team! Glad you’re here.", "Thanks! It’s great to meet everyone.",
        "سلام! خوش آمدی. اینجا می‌تونیم ایده‌هامون رو با هم به اشتراک بذاریم.",
        "I’ve put together a few ideas for tomorrow. I’d love to hear what you think.",
        "Sounds good. Looking forward to seeing it!", "I’ll share a quick update shortly.",
    )
    val messages = texts.mapIndexed { index, text ->
        ChatMessage(
            localId = index + 1L, conversationId = "preview", clientId = "client-$index", serverId = null,
            senderId = if (index in listOf(1, 3, 5)) "me" else "colleague", text = text,
            createdAtMillis = 1_760_000_000_000L + index * 60_000L,
            deliveryStatus = when (index) { 3 -> DeliveryStatus.FAILED; 5 -> DeliveryStatus.SENDING; else -> DeliveryStatus.SENT },
        )
    }
    return ChatUiState(messages = messages, initialLoad = HistoryLoadState.Idle, hasMoreHistory = true)
}
