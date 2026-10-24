package dev.sadeghtahani.rhinochat

import androidx.compose.runtime.Composable
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.welcome.WelcomeRoute

@Composable
fun App() {
    RhinoChatTheme {
        WelcomeRoute()
    }
}
