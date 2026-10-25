package dev.sadeghtahani.rhinochat

import androidx.compose.runtime.Composable
import dev.sadeghtahani.rhinochat.chat.presentation.ChatRoute
import dev.sadeghtahani.rhinochat.chat.presentation.ChatViewModel
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme

@Composable
fun App(chatViewModel: ChatViewModel, isOffline: Boolean = false) {
    RhinoChatTheme {
        ChatRoute(chatViewModel, isOffline)
    }
}
