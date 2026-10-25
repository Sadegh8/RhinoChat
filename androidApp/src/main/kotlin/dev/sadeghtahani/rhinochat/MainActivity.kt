package dev.sadeghtahani.rhinochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Process
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.sadeghtahani.rhinochat.chat.data.ChatOperationResult
import dev.sadeghtahani.rhinochat.chat.data.ChatDataException
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.ChatViewModel
import dev.sadeghtahani.rhinochat.demo.data.CacheResetStatus
import dev.sadeghtahani.rhinochat.demo.data.PersistenceProbeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.sadeghtahani.rhinochat.demo.DemoControlsScreen
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.koin.core.component.KoinComponent
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val CHAT_CONVERSATION_ID = "rhino-team"
private const val DRAFT_PROBE_TEXT = "Process death draft check"

class MainActivity : ComponentActivity(), KoinComponent {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val retryScheduler = getKoin().getOrNull<WorkManagerOutgoingMessageRetryScheduler>()

        setContent {
            val controls = koinInject<DemoNetworkControls>()
            val settings by controls.settings.collectAsState()
            var started by rememberSaveable { mutableStateOf(false) }
            var reloadHistory by rememberSaveable { mutableStateOf(false) }
            var cacheResetStatus by remember { mutableStateOf(CacheResetStatus.Idle) }
            var persistenceProbeStatus by remember { mutableStateOf(PersistenceProbeStatus.Idle) }
            val repository = koinInject<ChatRepository>()
            val scope = rememberCoroutineScope()
            BackHandler(enabled = started) { started = false }
            RhinoChatTheme {
                if (started) {
                    val viewModel = koinViewModel<ChatViewModel>()
                    LaunchedEffect(reloadHistory) {
                        if (reloadHistory) {
                            viewModel.loadInitialMessages()
                            reloadHistory = false
                        }
                    }
                    App(viewModel, isOffline = settings.offline)
                } else {
                    DemoControlsScreen(
                        settings, controls::update, onStart = { reloadHistory = true; started = true },
                        cacheResetStatus = cacheResetStatus,
                        onResetCache = {
                            if (cacheResetStatus != CacheResetStatus.Working) {
                                cacheResetStatus = CacheResetStatus.Working
                                scope.launch {
                                    cacheResetStatus = when (repository.resetHistoryCache(CHAT_CONVERSATION_ID)) {
                                        ChatOperationResult.Completed -> {
                                            reloadHistory = true
                                            CacheResetStatus.Done
                                        }
                                        ChatOperationResult.Ignored -> CacheResetStatus.Busy
                                        is ChatOperationResult.Failed -> CacheResetStatus.Failed
                                    }
                                }
                            }
                        },
                        persistenceProbeStatus = persistenceProbeStatus,
                        onPrepareDraftProbe = {
                            if (persistenceProbeStatus != PersistenceProbeStatus.Working) {
                                persistenceProbeStatus = PersistenceProbeStatus.Working
                                scope.launch {
                                    persistenceProbeStatus = try {
                                        if (repository.observeDraft(CHAT_CONVERSATION_ID).first().isNotEmpty()) {
                                            PersistenceProbeStatus.DraftAlreadyExists
                                        } else {
                                            repository.saveDraft(CHAT_CONVERSATION_ID, DRAFT_PROBE_TEXT)
                                            PersistenceProbeStatus.DraftPrepared
                                        }
                                    } catch (_: ChatDataException) {
                                        PersistenceProbeStatus.Failed
                                    }
                                }
                            }
                        },
                        onScheduleRetryProbe = {
                            if (persistenceProbeStatus != PersistenceProbeStatus.Working) {
                                if (retryScheduler == null) {
                                    persistenceProbeStatus = PersistenceProbeStatus.Failed
                                } else {
                                    persistenceProbeStatus = PersistenceProbeStatus.Working
                                    scope.launch {
                                        persistenceProbeStatus = try {
                                            if (retryScheduler.scheduleFailedMessagesForTesting() > 0) {
                                                PersistenceProbeStatus.RetriesScheduled
                                            } else {
                                                PersistenceProbeStatus.NoRetries
                                            }
                                        } catch (_: ChatDataException) {
                                            PersistenceProbeStatus.Failed
                                        }
                                    }
                                }
                            }
                        },
                        onStopAppProcess = { Process.killProcess(Process.myPid()) },
                    )
                }
            }
        }
    }
}
