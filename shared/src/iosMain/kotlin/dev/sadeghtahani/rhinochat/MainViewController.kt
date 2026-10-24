package dev.sadeghtahani.rhinochat

import androidx.compose.ui.window.ComposeUIViewController
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme
import dev.sadeghtahani.rhinochat.welcome.WelcomeRoute

// The optional iOS showcase still needs its own remote adapter; keep the starter explicit.
fun MainViewController() = ComposeUIViewController { RhinoChatTheme { WelcomeRoute() } }
