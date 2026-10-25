package dev.sadeghtahani.rhinochat

import androidx.compose.runtime.Composable
import dev.sadeghtahani.rhinochat.chat.ChatRoute
import dev.sadeghtahani.rhinochat.chat.ChatViewModel
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme

@Composable
fun App(chatViewModel: ChatViewModel) {
    RhinoChatTheme {
        ChatRoute(chatViewModel)
    }
}
