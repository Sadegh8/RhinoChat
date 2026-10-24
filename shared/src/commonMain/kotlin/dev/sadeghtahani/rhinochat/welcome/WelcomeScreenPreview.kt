package dev.sadeghtahani.rhinochat.welcome

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.sadeghtahani.rhinochat.theme.RhinoChatTheme

@Preview(name = "Welcome · Light", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun WelcomeLightPreview() {
    RhinoChatTheme(darkTheme = false) {
        WelcomeScreen()
    }
}

@Preview(name = "Welcome · Dark", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun WelcomeDarkPreview() {
    RhinoChatTheme(darkTheme = true) {
        WelcomeScreen()
    }
}
