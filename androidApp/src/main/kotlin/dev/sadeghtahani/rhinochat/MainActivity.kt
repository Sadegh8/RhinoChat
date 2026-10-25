package dev.sadeghtahani.rhinochat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import dev.sadeghtahani.rhinochat.chat.ChatViewModel
import dev.sadeghtahani.rhinochat.demo.data.CacheResetStatus
import kotlinx.coroutines.launch
import dev.sadeghtahani.rhinochat.demo.DemoControlsScreen
import dev.sadeghtahani.rhinochat.demo.data.DemoNetworkControls
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val controls = koinInject<DemoNetworkControls>()
            val settings by controls.settings.collectAsState()
            var started by rememberSaveable { mutableStateOf(false) }
            var reloadHistory by rememberSaveable { mutableStateOf(false) }
            var cacheResetStatus by remember { mutableStateOf(CacheResetStatus.Idle) }
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
                                    cacheResetStatus = when (repository.resetHistoryCache("rhino-team")) {
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
                    )
                }
            }
        }
    }
}
