package dev.sadeghtahani.rhinochat

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sadeghtahani.rhinochat.chat.ChatRoute
import dev.sadeghtahani.rhinochat.chat.ChatViewModel
import dev.sadeghtahani.rhinochat.chat.data.repository.ChatRepository
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme

@Composable
fun App(repository: ChatRepository) {
    RhinoChatTheme {
        val chatViewModel = viewModel { ChatViewModel(repository, conversationId = "rhino-team") }
        ChatRoute(chatViewModel)
    }
}
