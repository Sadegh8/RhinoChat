package dev.sadeghtahani.rhinochat

import androidx.compose.runtime.Composable
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.welcome.WelcomeScreen

@Composable
fun App() {
    RhinoChatTheme {
        WelcomeScreen()
    }
}
